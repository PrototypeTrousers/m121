package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity>  implements SimpleDynamicVisual {
    private final Matrix4f initialPose;
    public VisualBufferSource visualBufferSource = new VisualBufferSource(this);
    public record M(int poseDepth, ModelPart part, Material material) {}
    public Int2ObjectArrayMap<List<M>> posedParts = new Int2ObjectArrayMap<>();






    public Map<ModelPart, PoseStack.Pose> modelPartPoseMap = new HashMap<>();
    public Map<TransformedInstance, ModelPart> instanceModelPartMap = new HashMap<>();
    public Map<ModelPart, TextureAtlasSprite> atlasSpriteMap = new HashMap<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY() + 1.5f, visualPos.getZ() + 0.5f);

        RenderSystem.recordRenderCall(() -> {
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, partialTick, poseStackVisual, visualBufferSource);
            modelPartPoseMap.forEach((key, value) ->
                    instanceModelPartMap.put(instancerProvider().instancer(
                                    InstanceTypes.TRANSFORMED, VanillaModel.of(key, visualBufferSource.getMaterialMap().get(key)))
                            .createInstance(), key));
            instanceModelPartMap.forEach((key, value) -> {
                key.light(LevelRenderer.getLightColor(level, pos.above()));
            });
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
        Minecraft.getInstance().getBlockEntityRenderDispatcher().render(blockEntity, ctx.partialTick(), poseStackVisual, visualBufferSource);
        instanceModelPartMap.forEach((key, value) -> {
            Matrix4f p = modelPartPoseMap.get(value).pose();
            p.setTranslation(p.m30() + visualPos.getX(), p.m31() + visualPos.getY(), p.m32() + visualPos.getZ());
            key.setTransform(p);
            key.setChanged();
        });
    }

    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }
}
