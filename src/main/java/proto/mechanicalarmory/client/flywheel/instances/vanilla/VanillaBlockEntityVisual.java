package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import proto.mechanicalarmory.client.mixin.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity>  implements SimpleDynamicVisual {
    private final Matrix4f initialPose;
    public RenderBuffers rb = new RenderBuffers(1);
    public Map<ModelPart, PoseStack.Pose> modelPartPoseMap = new HashMap<>();
    public Map<TransformedInstance, ModelPart> instanceModelPartMap = new HashMap<>();
    public Map<ModelPart, Material> flwmaterialMap = new HashMap<>();
    public Map<ModelPart, TextureAtlasSprite> atlasSpriteMap = new HashMap<>();

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY() + 1.5f, visualPos.getZ() + 0.5f);

        RenderSystem.recordRenderCall(() -> {
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, partialTick, new PoseStackVisual(this), rb.bufferSource());
            modelPartPoseMap.forEach((key, value) ->
                    instanceModelPartMap.put(instancerProvider().instancer(
                                    InstanceTypes.TRANSFORMED, VanillaModel.of(key, flwmaterialMap.get(key), atlasSpriteMap.get(key)))
                            .createInstance(), key));
        });
        instanceModelPartMap.forEach((key, value) -> {
            key.light(LevelRenderer.getLightColor(level, pos.above()));
        });
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        instanceModelPartMap.forEach((key, value) -> {
            key.light(LevelRenderer.getLightColor(level, pos.above()));
        });
    }

    @Override
    protected void _delete() {
//        book.delete();
        instanceModelPartMap.forEach((key, value) -> {
            key.delete();
        });
    }

    @Override
    public void beginFrame(Context ctx) {
        RenderSystem.recordRenderCall(() -> {
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, ctx.partialTick(), new PoseStackVisual(this), rb.bufferSource());
            rb.bufferSource().endBatch();
        });
        instanceModelPartMap.forEach((key, value) -> {
            Matrix4f p = modelPartPoseMap.get(value).pose();
            p.setTranslation(p.m30() + visualPos.getX(), p.m31() + visualPos.getY(), p.m32() + visualPos.getZ());
            key.setTransform(p);

            key.setChanged();
        });
    }

    public void makeMaterialForPart(ModelPart modelPart, VertexConsumer buffer) {
        if (buffer instanceof SpriteCoordinateExpanderAccessor spriteCoordinateExpanderAccessor) {
            TextureAtlasSprite sprite = spriteCoordinateExpanderAccessor.getSprite();
            atlasSpriteMap.put(modelPart, sprite);
        }
        MultiBufferSource.BufferSource bs = rb.bufferSource();
        Map<RenderType, BufferBuilder> mp = ((BufferSourceAccessor) bs).getStartedBuilders();
        for (Map.Entry<RenderType, BufferBuilder> entry : mp.entrySet()) {
            CompositeRenderTypeAccessor c = ((CompositeRenderTypeAccessor) entry.getKey());
            RenderStateShard.EmptyTextureStateShard r = ((RenderTypeAccessor) (Object) c.getState()).getTextureState();
            ResourceLocation atlas = ((TextureStateShardAccessor) r).getTexture().get();

            flwmaterialMap.put(modelPart, SimpleMaterial.builder()
                    .texture(atlas)
                    .cutout(CutoutShaders.EPSILON)
                    .light(LightShaders.SMOOTH_WHEN_EMBEDDED)
                    .cardinalLightingMode(CardinalLightingMode.OFF)
                    .ambientOcclusion(false)
                    .build());
        }
    }
}
