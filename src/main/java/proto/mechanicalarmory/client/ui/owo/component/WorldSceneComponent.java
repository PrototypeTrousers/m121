package proto.mechanicalarmory.client.ui.owo.component;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import proto.mechanicalarmory.common.logic.Targeting;

import java.util.function.BiConsumer;

public class WorldSceneComponent extends BaseComponent {
    protected BlockPos center;
    protected float rotX;
    protected float rotY;
    protected float zoom;
    private final Matrix4f lastModelViewMatrix = new Matrix4f();
    private boolean isDragging;
    private BiConsumer<Pair<BlockPos, Direction>, Integer> onBlockClicked = (hit, button) -> {};

    public WorldSceneComponent(BlockPos center, float rotX, float rotY, float zoom) {
        this.center = center;
        this.rotX = rotX;
        this.rotY = rotY;
        this.zoom = zoom;
    }

    private Targeting targeting;

    public WorldSceneComponent targeting(Targeting targeting) {
        this.targeting = targeting;
        return this;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;

        var poseStack = context.pose();
        var bufferSource = client.renderBuffers().bufferSource();

        poseStack.pushPose();

        // 1. Center in component space
        poseStack.translate(x + width / 2f, y + height / 2f, 100);

        // 2. Apply Scale/Zoom
        float scaleValue = 20f * zoom;
        poseStack.scale(scaleValue, -scaleValue, scaleValue);

        // 3. Apply Rotations
        poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY - 180));

        lastModelViewMatrix.set(poseStack.last().pose());

        // 4. Render Blocks
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - center.getX() - 0.5f, 
                               pos.getY() - center.getY() - 0.5f, 
                               pos.getZ() - center.getZ() - 0.5f);

            BlockState bs = client.level.getBlockState(pos);
            if (bs.isAir()) {
                poseStack.popPose();
                continue;
            }

            if (bs.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                BlockEntity be = client.level.getBlockEntity(pos);
                if (be != null) {
                    client.getBlockEntityRenderDispatcher().render(be, partialTicks, poseStack, bufferSource);
                }
            } else {
                client.getBlockRenderer().renderSingleBlock(bs, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
            poseStack.popPose();
        }

        if (this.targeting != null) {
            if (this.targeting.hasInput()) {
                renderIndicatorBlock(poseStack, bufferSource, client,
                        this.targeting.getSourceVec(), this.targeting.getSourceFacing(),
                        Blocks.BLUE_CONCRETE.defaultBlockState());
            }
            if (this.targeting.hasOutput()) {
                renderIndicatorBlock(poseStack, bufferSource, client,
                        this.targeting.getTargetVec(), this.targeting.getTargetFacing(),
                        Blocks.GREEN_CONCRETE.defaultBlockState());
            }
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    private void renderIndicatorBlock(PoseStack poseStack, MultiBufferSource bufferSource, Minecraft client,
                                      Vector3d vec, Direction facing, BlockState state) {
        poseStack.pushPose();
        poseStack.translate(vec.x, vec.y, vec.z);

        // 3. Scale down to a tiny cube (20% of a standard block)
        float scale = 0.2f;
        poseStack.scale(scale, scale, scale);

        // 4. Minecraft renders blocks from 0 to 1. Translate by -0.5 to center the model on our offset coordinate.
        poseStack.translate(-0.5, -0.5, -0.5);

        // 5. Render as a solid block at full brightness (0xF000F0)
        client.getBlockRenderer().renderSingleBlock(state, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        this.zoom += amount * 0.1f;
        this.zoom = Math.max(0.1f, this.zoom); // Prevent inverted zoom
        return true;
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Reset the drag flag when the user first clicks down
            this.isDragging = false;
            return true; // Return true to capture the interaction
        }
        return super.onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        if (button == 0) {
            // The user moved the mouse! Flag this as a drag and rotate the scene
            this.isDragging = true;
            this.rotY += deltaX;
            this.rotX += deltaY;
            return true;
        }
        return super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
    }

    @Override
    public boolean onMouseUp(double mouseX, double mouseY, int button) {
        // If they let go of the left mouse button AND they didn't drag to rotate...
        if (button == 1 && !this.isDragging) {
            Pair<BlockPos, Direction> hit = getTargetedHitResult(mouseX, mouseY);

            if (hit != null) {
                // Fire the callback with the hit data!
                this.onBlockClicked.accept(hit, button);
                return true;
            }
        }
        this.isDragging = false;

        return super.onMouseUp(mouseX, mouseY, button);
    }

    public WorldSceneComponent onBlockClicked(BiConsumer<Pair<BlockPos, Direction>, Integer> callback) {
        this.onBlockClicked = callback;
        return this;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    public Pair<BlockPos, Direction> getTargetedHitResult(double mouseX, double mouseY) {
        Vec3 localRayStart = getMouseRay(mouseX, mouseY, false);
        Vec3 localRayEnd = getMouseRay(mouseX, mouseY, true);

        Vec3 worldOffset = new Vec3(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        Vec3 worldRayStart = localRayStart.add(worldOffset);
        Vec3 worldRayEnd = localRayEnd.add(worldOffset);

        var client = Minecraft.getInstance();
        if (client.level == null) return null;

        Pair<BlockPos, Direction> closestHit = null;

        ClipContext context = new ClipContext(
                worldRayStart,
                worldRayEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        );

        // Raycast but ignore everything further than 2 blocks away from the center
        BlockHitResult hit = clipWithinRadius(client.level, context, this.center, 2.0);

        if (hit.getType() != BlockHitResult.Type.MISS) {
            closestHit = Pair.of(hit.getBlockPos().immutable(), hit.getDirection());
        }

        return closestHit;
    }

    public BlockHitResult clipWithinRadius(BlockGetter level, ClipContext context, BlockPos center, double maxRadius) {
        // Square the radius once for faster distance checking later
        double maxRadiusSqr = maxRadius * maxRadius;

        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context,
                (ctx, pos) -> {
                    if (pos.distSqr(center) > maxRadiusSqr) {
                        return null;
                    }

                    // Standard Minecraft Clip Logic
                    BlockState blockstate = level.getBlockState(pos);
                    FluidState fluidstate = level.getFluidState(pos);
                    Vec3 rayStart = ctx.getFrom();
                    Vec3 rayEnd = ctx.getTo();

                    VoxelShape blockShape = ctx.getBlockShape(blockstate, level, pos);
                    BlockHitResult blockHit = level.clipWithInteractionOverride(rayStart, rayEnd, pos, blockShape, blockstate);

                    VoxelShape fluidShape = ctx.getFluidShape(fluidstate, level, pos);
                    BlockHitResult fluidHit = fluidShape.clip(rayStart, rayEnd, pos);

                    double blockDist = blockHit == null ? Double.MAX_VALUE : rayStart.distanceToSqr(blockHit.getLocation());
                    double fluidDist = fluidHit == null ? Double.MAX_VALUE : rayStart.distanceToSqr(fluidHit.getLocation());

                    return blockDist <= fluidDist ? blockHit : fluidHit;
                },
                // 2. The On-Fail Function (Runs if the ray hits nothing)
                (ctx) -> {
                    Vec3 vec3 = ctx.getFrom().subtract(ctx.getTo());
                    return BlockHitResult.miss(ctx.getTo(), Direction.getNearest(vec3.x, vec3.y, vec3.z), BlockPos.containing(ctx.getTo()));
                }
        );
    }

    public Vec3 getMouseRay(double mouseX, double mouseY, boolean far) {
        Matrix4f invMatrix = new Matrix4f(lastModelViewMatrix).invert();

        // Convert OwoUI's relative mouse coordinates into absolute screen coordinates
        // so they perfectly match the absolute translations inside your lastModelViewMatrix
        float absoluteX = (float) (this.x + mouseX);
        float absoluteY = (float) (this.y + mouseY);

        // Near plane is 10000, Far plane is -10000 (Minecraft UI Z goes out of the screen)
        float zDepth = far ? -10000.0f : 10000.0f;

        Vector4f pos = new Vector4f(absoluteX, absoluteY, zDepth, 1.0f);
        invMatrix.transform(pos);

        return new Vec3(pos.x() / pos.w(), pos.y() / pos.w(), pos.z() / pos.w());
    }
}