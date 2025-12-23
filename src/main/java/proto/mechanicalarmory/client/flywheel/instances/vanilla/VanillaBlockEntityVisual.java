package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.checkerframework.checker.units.qual.A;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity>  implements SimpleDynamicVisual {
    private final Matrix4f initialPose;
    final public VisualBufferSource visualBufferSource;

    BlockRendererBuilder<BlockEntity> builder;
    ArrayObjectPool<BlockEntityRenderer<BlockEntity>> rendererPool;

    public record M(int poseDepth, ModelPart part, Material material) {
    }

    final public Int2ObjectLinkedOpenHashMap<List<M>> posedParts = new Int2ObjectLinkedOpenHashMap<>();
    final public Int2ObjectLinkedOpenHashMap<List<TransformedInstance>> transformedInstances = new Int2ObjectLinkedOpenHashMap<>();
    final private Object2ObjectArrayMap<TransformedInstance, Matrix4f> lastTransform= new Object2ObjectArrayMap<>();
    final public Int2ObjectLinkedOpenHashMap<LinkedList<PoseStack.Pose>> depthPoseMap = new Int2ObjectLinkedOpenHashMap<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY() + 1.5f, visualPos.getZ() + 0.5f);
        visualBufferSource = new VisualBufferSource(this);

        BlockEntityRendererProvider.Context blockentityrendererprovider$context = new BlockEntityRendererProvider.Context(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getBlockRenderer(),
                Minecraft.getInstance().getItemRenderer(),
                Minecraft.getInstance().getEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels(),
                Minecraft.getInstance().font);

        builder = new BlockRendererBuilder(blockEntity.getType(), blockentityrendererprovider$context);
        rendererPool = new ArrayObjectPool<>(1, builder);

        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        berenderer.render(blockEntity, partialTick, poseStackVisual, visualBufferSource,LevelRenderer.getLightColor(level, pos.above()), 0);
        rendererPool.release(berenderer);

        visualBufferSource.setRendered(true);
        posedParts.forEach((k, v) -> {
            v.forEach(m -> transformedInstances.compute(k, (key, value) -> {
                if (value == null) value = new ArrayList<>();
                value.add(instancerProvider().instancer(
                                InstanceTypes.TRANSFORMED, VanillaModel.of(m.part, m.material))
                        .createInstance());
                return value;
            }));
        });

        transformedInstances.forEach((key, value) -> {
            value.forEach(ti -> ti.light(LevelRenderer.getLightColor(level, pos.above())));
        });
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        transformedInstances.forEach((i, value) -> {
            value.forEach(ti -> ti.light(LevelRenderer.getLightColor(level, pos.above())));
        });
    }

    @Override
    protected void _delete() {
        transformedInstances.forEach((i, v) -> v.forEach(AbstractInstance::delete));
    }

    @Override
    public void beginFrame(Context ctx) {
        poseStackVisual.setDepth(0);

        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        berenderer.render(blockEntity, ctx.partialTick(), poseStackVisual, visualBufferSource,LevelRenderer.getLightColor(level, pos.above()), 0);
        rendererPool.release(berenderer);

        transformedInstances.forEach((i, value) -> {
            Matrix4f p = depthPoseMap.get(i).pollFirst().pose();
            p.setTranslation(p.m30() + visualPos.getX(), p.m31() + visualPos.getY(), p.m32() + visualPos.getZ());
            value.forEach(ti -> {
                Matrix4f last =lastTransform.computeIfAbsent(ti, k -> new Matrix4f());
                if (!lastTransform.get(ti).equals(p)) {
                    ti.setTransform(p);
                    ti.setChanged();
                    last.set(p);
                }
            });
        });
    }

    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }
}
