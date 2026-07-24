package proto.mechanicalarmory.client.modularui.renderer;

import brachy.modularui.ModularUI;
import brachy.modularui.api.drawable.IDrawable;
import brachy.modularui.drawable.GuiDraw;
import brachy.modularui.drawable.Icon;
import brachy.modularui.drawable.schema.*;
import brachy.modularui.integration.embeddium.SodiumCompat;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import brachy.modularui.utils.Color;
import brachy.modularui.utils.FluidTextureType;
import brachy.modularui.utils.MatrixUtils;
import brachy.modularui.widget.sizer.Area;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import proto.mechanicalarmory.client.modularui.widgets.MASchemaWidget;

import java.util.*;

/**
 * Mechanical Armory's own copy of the upstream {@code BaseSchemaRenderer}.
 * Does not extend any upstream renderer class — fully owned by this mod.
 * <p>
 * World rendering is based on Applied Energistics 2's
 * <a href="https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/643dfe2e7e16dac48192d85305d35e2e74a64fb0/src/main/java/appeng/client/guidebook/scene/GuidebookLevelRenderer.java">GuidebookLevelRenderer</a>
 * (LGPLv3)
 * <p>
 * Unlike the upstream, the chunk compilation step runs <em>synchronously</em> on the render
 * thread the first time a dirty frame is drawn, using plain vanilla
 * {@link SectionBufferBuilderPack} / {@link BufferBuilder} / {@link VertexBuffer} calls —
 * no custom {@code CompileStatus} state-machine, no background {@link java.util.concurrent.CompletableFuture}
 * pipeline, and no custom task/result inner classes.
 */
@Accessors(fluent = true)
public class MABaseSchemaRenderer implements IDrawable {

    // Shared across all renderers — no per-instance allocation needed
    private static final brachy.modularui.drawable.schema.DummyLightTexture LIGHT_TEXTURE =
            new brachy.modularui.drawable.schema.DummyLightTexture();

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------

    @Getter private final ISchema schema;
    private final RenderLevel renderLevel;
    private final Viewport viewport = new Viewport();
    @Getter private final Camera camera = new Camera();
    @Getter private BlockHitResult lastRayTrace = null;
    @Getter private RenderFilter renderFilter = RenderFilter.ALL;

    /** Set to {@code true} to force a synchronous recompile on the next rendered frame. */
    private volatile boolean dirty = true;
    private boolean disposed = false;

    // Compiled GPU buffers — keyed only for render types that actually have geometry.
    private final Map<RenderType, VertexBuffer> vertexBuffers = new Reference2ObjectLinkedOpenHashMap<>();
    private final List<BlockEntity> compiledBlockEntities = new ArrayList<>();
    private final Set<TextureAtlasSprite> activeFluidSprites = new HashSet<>();

    // Reused across recompile calls to avoid repeated large allocations.
    private final SectionBufferBuilderPack sectionBufferBuilders = new SectionBufferBuilderPack();

    // projection * model view matrix — rebuilt each frame in setupCamera()
    @Getter private final Matrix4f projection = new Matrix4f();
    @Getter @Setter private boolean captureDebugInfo;
    @Getter private final Vector3f openGLMousePos = new Vector3f();
    private final List<Vector3f> pos = new ArrayList<>();

    public MABaseSchemaRenderer(ISchema schema) {
        this.schema = schema;
        this.renderLevel = new RenderLevel(schema, (p, state) -> this.renderFilter.shouldRender(p, state));
    }

    /** Mark the compiled scene as stale; it will be rebuilt synchronously before the next draw. */
    public void notifyRecompile() {
        this.dirty = true;
    }

    public void dispose() {
        disposed = true;
        clearVertexBuffers();
        sectionBufferBuilders.discardAll();
    }

    @Override
    public MASchemaWidget asWidget() {
        return new MASchemaWidget(this);
    }

    @Override
    public Icon asIcon() {
        return IDrawable.super.asIcon().size(50);
    }

    // -----------------------------------------------------------------------
    // IDrawable entry-point
    // -----------------------------------------------------------------------

    @Override
    public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
        if (disposed) return;

        int mouseX = context.getMouseX();
        int mouseY = context.getMouseY();

        context.getGraphics().flush();
        context.graphicsPose().pushPose();

        Area area = context.getScreenArea();
        int transformX = context.transformX(x, y) + area.x();
        int transformY = context.transformY(x, y) + area.y();
        this.viewport.calculateOpenGLViewportFromRectangle(transformX, transformY, width, height);
        this.viewport.applyViewport();

        onSetupCamera();
        setupCamera(width, height);
        renderWorld(context.getGraphics().bufferSource(), context.getRenderPartialTicks());

        if (doRayTrace() || captureDebugInfo()) {
            BlockHitResult result = null;
            if (Area.isInside(x, y, width, height, mouseX, mouseY)) {
                result = rayTrace(mouseX, mouseY, width, height);
            }
            if (result == null || result.getType() != HitResult.Type.BLOCK) {
                if (this.lastRayTrace != null) onRayTraceFailed();
            } else {
                onSuccessfulRayTrace(createWorldRenderPose(), result);
            }
            this.lastRayTrace = result;
        }

        resetCamera();
        context.graphicsPose().popPose();

        if (this.captureDebugInfo) {
            drawProjectedBlockPos(context.getGraphics(), width, height);
        }
    }

    // -----------------------------------------------------------------------
    // Synchronous compilation — runs on the render thread, called from renderWorld()
    // -----------------------------------------------------------------------

    /**
     * Rebuilds all {@link VertexBuffer}s from the schema blocks.
     * <p>
     * This is called synchronously from the render thread inside {@link #renderWorld} whenever
     * {@link #dirty} is {@code true}. It uses vanilla's {@link SectionBufferBuilderPack} and
     * {@link BufferBuilder} to build per-{@link RenderType} geometry, then uploads each one
     * to a {@link VertexBuffer} directly — no background tasks, no custom state machine.
     */
    private void recompileNow() {
        clearVertexBuffers();
        compiledBlockEntities.clear();
        activeFluidSprites.clear();

        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        var random = RandomSource.create();
        var poseStack = new PoseStack();
        Map<RenderType, BufferBuilder> builders = new Reference2ObjectArrayMap<>(RenderType.chunkBufferLayers().size());

        ModelBlockRenderer.enableCaching();
        for (var entry : this.schema) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();

            if (!this.renderFilter.shouldRender(pos, state)) continue;

            FluidState fluid = state.getFluidState();

            // ---- block entities ----
            if (state.hasBlockEntity()) {
                BlockEntity be = this.renderLevel.getBlockEntity(pos);
                if (be != null) compiledBlockEntities.add(be);
            }

            // ---- fluids ----
            if (!fluid.isEmpty()) {
                RenderType rt = ItemBlockRenderTypes.getRenderLayer(fluid);
                BufferBuilder builder = getOrBeginLayer(builders, rt);
                dispatcher.renderLiquid(pos, this.renderLevel, new LiquidVertexConsumer(builder, SectionPos.of(pos)), state, fluid);
                // track sprites for Sodium compat
                var props = IClientFluidTypeExtensions.of(fluid);
                activeFluidSprites.add(FluidTextureType.STILL.map(props));
                activeFluidSprites.add(FluidTextureType.FLOWING.map(props));
            }

            // ---- solid / transparent blocks ----
            if (state.getRenderShape() == RenderShape.MODEL) {
                BakedModel model = dispatcher.getBlockModel(state);
                BlockEntity be = this.renderLevel.getBlockEntity(pos);
                ModelData modelData = be != null ? be.getModelData() : ModelData.EMPTY;
                modelData = model.getModelData(this.renderLevel, pos, state, modelData);
                random.setSeed(state.getSeed(pos));

                for (RenderType rt : model.getRenderTypes(state, random, modelData)) {
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    dispatcher.renderBatched(state, pos, this.renderLevel, poseStack, getOrBeginLayer(builders, rt), true, random, modelData, rt);
                    poseStack.popPose();
                }
            }
        }
        ModelBlockRenderer.clearCache();

        // Build MeshData and upload to VertexBuffer — all on the render thread, so no
        // RenderSystem.recordRenderCall indirection is needed.
        builders.forEach((rt, builder) -> {
            MeshData mesh = builder.build();
            if (mesh == null) return;
            if (rt == RenderType.translucent()) {
                mesh.sortQuads(sectionBufferBuilders.buffer(RenderType.translucent()), VertexSorting.byDistance(camera.pos()));
            }
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(mesh);
            VertexBuffer.unbind();
            mesh.close();
            vertexBuffers.put(rt, vb);
        });

        sectionBufferBuilders.clearAll(); // reset byte buffers for next recompile
        onRendered();
    }

    private BufferBuilder getOrBeginLayer(Map<RenderType, BufferBuilder> builders, RenderType rt) {
        return builders.computeIfAbsent(rt, type ->
                new BufferBuilder(sectionBufferBuilders.buffer(type), VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK));
    }

    private void clearVertexBuffers() {
        vertexBuffers.values().forEach(VertexBuffer::close);
        vertexBuffers.clear();
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public void renderWorld(MultiBufferSource.BufferSource bufferSource, float partialTick) {
        if (disposed) return;

        // Synchronous recompile if the scene is stale — safe because we are on the render thread.
        if (dirty) {
            dirty = false;
            recompileNow();
        }

        if (vertexBuffers.isEmpty() && compiledBlockEntities.isEmpty()) return;

        RenderSystem.setShaderFogColor(1, 1, 1, 0);
        RenderSystem.setShaderFogStart(0);
        RenderSystem.setShaderFogEnd(1000);
        RenderSystem.setShaderFogShape(FogShape.SPHERE);

        LIGHT_TEXTURE.update(this.renderLevel);
        LevelLightEngine lightEngine = this.renderLevel.getLightEngine();
        while (lightEngine.hasLightWork()) lightEngine.runLightUpdates();

        Lighting.setupLevel();
        RenderSystem.disableDepthTest();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);

        RenderSystem.runAsFancy(() -> {
            renderLayer(RenderType.solid());
            Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .setBlurMipmap(false, Minecraft.getInstance().options.mipmapLevels().get() > 0);
            renderLayer(RenderType.cutoutMipped());
            Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).restoreLastBlurMipmap();
            renderLayer(RenderType.cutout());

            bufferSource.endBatch(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
            bufferSource.endBatch(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
            bufferSource.endBatch(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
            bufferSource.endBatch(RenderType.entitySmoothCutout(TextureAtlas.LOCATION_BLOCKS));

            if (isBEREnabled()) renderBlockEntities(bufferSource, partialTick);

            bufferSource.endBatch(RenderType.solid());
            bufferSource.endBatch(RenderType.endPortal());
            bufferSource.endBatch(RenderType.endGateway());
            bufferSource.endBatch(Sheets.solidBlockSheet());
            bufferSource.endBatch(Sheets.cutoutBlockSheet());
            bufferSource.endBatch(Sheets.bedSheet());
            bufferSource.endBatch(Sheets.shulkerBoxSheet());
            bufferSource.endBatch(Sheets.signSheet());
            bufferSource.endBatch(Sheets.hangingSignSheet());
            bufferSource.endBatch(Sheets.chestSheet());
            bufferSource.endBatch(Sheets.translucentCullBlockSheet());
            bufferSource.endBatch(Sheets.bannerSheet());
            bufferSource.endBatch(Sheets.shieldSheet());
            bufferSource.endLastBatch();

            renderLayer(RenderType.translucent());
            renderLayer(RenderType.tripwire());

            if (this.captureDebugInfo) drawBlockOutlines(bufferSource);
        });

        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
    }

    /**
     * Sets up shader uniforms (matrices, fog, lighting, chunk offset) and draws the
     * pre-compiled {@link VertexBuffer} for {@code renderType}, if one exists.
     */
    protected void renderLayer(RenderType renderType) {
        VertexBuffer vb = vertexBuffers.get(renderType);
        if (vb != null && !vb.isInvalid() && vb.getFormat() != null) {

            renderType.setupRenderState();
            ModelBlockRenderer.enableCaching();

            ShaderInstance shader = RenderSystem.getShader();
            assert shader != null;

            if (ModularUI.Mods.isSodiumLikeLoaded()) {
                SodiumCompat.markSpritesAsActive(activeFluidSprites);
            }
            vb.bind();
            vb.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            shader.clear();
            VertexBuffer.unbind();
            renderType.clearRenderState();
        }
    }

    protected void renderBlockEntities(MultiBufferSource bufferSource, float partialTick) {
        PoseStack poseStack = new PoseStack();
        for (BlockEntity be : compiledBlockEntities) {
            if (be != null) handleBlockEntity(poseStack, bufferSource, partialTick, be);
        }
    }

    protected <E extends BlockEntity> void handleBlockEntity(PoseStack poseStack, MultiBufferSource bufferSource,
                                                             float partialTick, E be) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(be);
        if (renderer == null) return;
        BlockPos blockPos = be.getBlockPos();
        poseStack.pushPose();
        poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        //noinspection DataFlowIssue
        int packedLight = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos());
        renderer.render(be, partialTick, poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    // -----------------------------------------------------------------------
    // Camera / projection
    // -----------------------------------------------------------------------

    protected void setupCamera(int width, int height) {
        int clearColor = getClearColor();
        RenderSystem.clearColor(Color.getRedF(clearColor), Color.getGreenF(clearColor),
                Color.getBlueF(clearColor), Color.getAlphaF(clearColor));
        RenderSystem.backupProjectionMatrix();

        float near = 0.05f, far = 10000.0f, fovY = 60.0f * Mth.DEG_TO_RAD;
        float aspect = (float) width / height;
        float top    = -near * (float) Math.tan(fovY * 0.5);
        float bottom = -top, left = aspect * bottom, right = aspect * top;

        Matrix4f proj = new Matrix4f();
        if (isIsometric()) {
            proj.setOrtho(left, right, bottom, top, near, far);
            RenderSystem.setProjectionMatrix(proj, VertexSorting.ORTHOGRAPHIC_Z);
        } else {
            proj.setPerspective(fovY, aspect, near, far);
            RenderSystem.setProjectionMatrix(proj, VertexSorting.byDistance(camera.pos()));
        }

        Matrix4fStack mv = RenderSystem.getModelViewStack();
        mv.pushMatrix();
        mv.identity();
        if (isIsometric()) mv.translate(0f, 0f, -2000f);
        MatrixUtils.lookAt(mv, this.camera.pos(), this.camera.lookAt());
        RenderSystem.applyModelViewMatrix();

        rebuildProjection();
    }

    protected void resetCamera() {
        Window window = Minecraft.getInstance().getWindow();
        RenderSystem.viewport(0, 0, window.getWidth(), window.getHeight());
        RenderSystem.restoreProjectionMatrix();
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    private void rebuildProjection() {
        this.projection.set(RenderSystem.getModelViewMatrix());
        RenderSystem.getProjectionMatrix().mul(this.projection, this.projection);
    }

    // -----------------------------------------------------------------------
    // Ray-tracing / debug
    // -----------------------------------------------------------------------

    protected BlockHitResult rayTrace(int mouseX, int mouseY, int width, int height) {
        var m = projection();
        if (this.captureDebugInfo) {
            int i = 0;
            for (var e : this.schema) {
                if (!e.getValue().isAir()) {
                    var p = e.getKey();
                    boolean reuse = this.pos.size() > i;
                    var vec = reuse ? this.pos.get(i) : new Vector3f();
                    vec = m.project(p.getX() + 0.5f, p.getY() + 0.5f, p.getZ() + 0.5f, this.viewport.getViewport(), vec);
                    if (reuse) this.pos.set(i, vec); else this.pos.add(vec);
                    i++;
                }
            }
            while (i < this.pos.size()) this.pos.remove(i);
        }

        screenToOpenGLPos(mouseX, mouseY, width, height, 1, this.captureDebugInfo, this.openGLMousePos);
        float d = openGLMousePos.z;

        this.openGLMousePos.z = 0;
        Vector3f worldPos = screenToWorldPos(m, this.openGLMousePos);
        this.openGLMousePos.z = 1;
        Vector3f target = screenToWorldPos(m, this.openGLMousePos);
        this.openGLMousePos.z = d;

        return this.renderLevel.clip(new ClipContext(
                new Vec3(worldPos), new Vec3(target),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, CollisionContext.empty()));
    }

    public void drawProjectedBlockPos(GuiGraphics graphics, int width, int height) {
        for (Vector3f s : this.pos) {
            GuiDraw.drawRect(graphics, this.viewport.unscaleXFromViewport(s.x, width) - 1,
                    this.viewport.unscaleYFromViewport(s.y, height) - 1, 2, 2, Color.withAlpha(Color.BLUE.main, 1f));
        }
    }

    public void drawBlockOutlines(MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer vc = bufferSource.getBuffer(RenderType.lines());
        var ps = createWorldRenderPose();
        for (var e : this.schema) {
            if (!e.getValue().isAir()) {
                var p = e.getKey();
                LevelRenderer.renderLineBox(ps, vc, p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1, p.getY() + 1, p.getZ() + 1, 1f, 0f, 0f, 1f);
            }
        }
        bufferSource.endBatch(RenderType.lines());
    }

    public PoseStack createWorldRenderPose() {
        var ps = new PoseStack();
        return ps;
    }

    public Vector3f screenToOpenGLPos(int x, int y, int width, int height, Vector3f dest) {
        return screenToOpenGLPos(x, y, width, height, 0, true, dest);
    }

    public Vector3f screenToOpenGLPos(int x, int y, int width, int height, float depth, Vector3f dest) {
        return screenToOpenGLPos(x, y, width, height, depth, false, dest);
    }

    private Vector3f screenToOpenGLPos(int x, int y, int width, int height, float depth, boolean readDepth, Vector3f dest) {
        this.viewport.rescaleToViewport(x, y, width, height, dest);
        if (readDepth) depth = MatrixUtils.readDepth((int) dest.x, (int) dest.y);
        dest.z = depth;
        return dest;
    }

    public Vector3f screenToWorldPos(int x, int y, int screenWidth, int screenHeight) {
        return screenToWorldPos(projection(), screenToOpenGLPos(x, y, screenWidth, screenHeight, new Vector3f()), new Vector3f());
    }

    private Vector3f screenToWorldPos(Matrix4f proj, Vector3f openGLPos) {
        return screenToWorldPos(proj, openGLPos, new Vector3f());
    }

    private Vector3f screenToWorldPos(Matrix4f proj, Vector3f openGLPos, Vector3f dest) {
        return proj.unproject(openGLPos, this.viewport.getViewport(), dest);
    }

    // -----------------------------------------------------------------------
    // Override hooks
    // -----------------------------------------------------------------------

    @ApiStatus.OverrideOnly protected void onSetupCamera() {}
    @ApiStatus.OverrideOnly protected void onRendered() {}
    @ApiStatus.OverrideOnly protected void onSuccessfulRayTrace(PoseStack poseStack, @NotNull BlockHitResult result) {}
    @ApiStatus.OverrideOnly protected void onRayTraceFailed() {}

    public boolean doRayTrace()  { return false; }
    public boolean isIsometric() { return false; }
    public boolean isBEREnabled() { return true; }

    public int getClearColor() {
        return Color.withAlpha(Color.WHITE.main, 0.5f);
    }

    /** Sets a render filter and triggers a recompile on the next rendered frame. */
    public void updateRenderFilter(RenderFilter renderFilter) {
        this.renderFilter = renderFilter != null ? renderFilter : RenderFilter.ALL;
        notifyRecompile();
    }

    // -----------------------------------------------------------------------
    // equals / hashCode
    // -----------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MABaseSchemaRenderer that)) return false;
        return this.schema.equals(that.schema) && this.renderLevel.equals(that.renderLevel)
                && this.camera.equals(that.camera)
                && Objects.equals(this.viewport, that.viewport)
                && Objects.equals(this.renderFilter, that.renderFilter);
    }

    @Override
    public int hashCode() {
        int result = this.schema.hashCode();
        result = 31 * result + this.renderLevel.hashCode();
        result = 31 * result + this.camera.hashCode();
        result = 31 * result + this.viewport.hashCode();
        result = 31 * result + Objects.hashCode(this.renderFilter);
        return result;
    }
}
