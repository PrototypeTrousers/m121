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
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import proto.mechanicalarmory.client.mixin.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class EnchantingTableVisual extends AbstractBlockEntityVisual<EnchantingTableBlockEntity>  implements SimpleDynamicVisual {
    private final Matrix4f initialPose;
    public static EnchantingTableVisual CURRENT_VISUAL;
    public RenderBuffers rb = new RenderBuffers(1);
    public Map<ModelPart, PoseStack.Pose> modelPartPoseMap = new HashMap<>();
    public Map<TransformedInstance, ModelPart> instanceModelPartMap = new HashMap<>();
    public Map<ModelPart, Material> flwmaterialMap = new HashMap<>();
    public Map<ModelPart, TextureAtlasSprite> atlasSpriteMap = new HashMap<>();

    Material material = SimpleMaterial.builder()
            .texture(TextureAtlas.LOCATION_BLOCKS)
            .mipmap(false)
            .build();

    public EnchantingTableVisual(VisualizationContext ctx, EnchantingTableBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY() + 1.5f, visualPos.getZ() + 0.5f);

        RenderSystem.recordRenderCall(() -> {
            CURRENT_VISUAL = this;
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, partialTick, new PoseStack(), rb.bufferSource());
            modelPartPoseMap.forEach((key, value) ->
                    instanceModelPartMap.put(instancerProvider().instancer(
                                    InstanceTypes.TRANSFORMED, new VanillaModel(key, material, atlasSpriteMap.get(key)))
                            .createInstance(), key));
            CURRENT_VISUAL = null;
        });


//        book = InstanceTree.create(instancerProvider(), ModelTrees.of(
//                ModelLayers.BOOK, EnchantTableRenderer.BOOK_LOCATION, MATERIAL));
//        book.traverse(transformedInstance ->
//                transformedInstance.light(15, 15));
//
//        book.updateInstancesStatic(initialPose);
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        instanceModelPartMap.forEach((key, value) -> {
            key.light(255);
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
            CURRENT_VISUAL = this;
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, ctx.partialTick(), new CapturingPoseStack(new CapturedModelTreeBuilder(rb.bufferSource())), rb.bufferSource());
            CURRENT_VISUAL = null;
            rb.bufferSource().endBatch();
        });
        instanceModelPartMap.forEach((key, value) -> {
            key.setTransform(modelPartPoseMap.get(value));
            key.translate(pos.getX(), pos.getY(), pos.getZ());
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
