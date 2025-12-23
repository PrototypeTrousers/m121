package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity>  implements SimpleTickableVisual, SimpleDynamicVisual {
    private final Matrix4f initialPose;
    final public VisualBufferSource visualBufferSource;

    BlockRendererBuilder<BlockEntity> builder;
    ArrayObjectPool<BlockEntityRenderer<BlockEntity>> rendererPool;
    Matrix4f mutableMatrix4f = new Matrix4f();

    public void updateTransforms(int depth, Matrix4f p) {
        p.setTranslation(p.m30() + visualPos.getX(), p.m31() + visualPos.getY(), p.m32() + visualPos.getZ());
        transformedInstances.get(depth).forEach((ti) -> {
            Matrix4f last = lastTransform.computeIfAbsent(ti, k -> new Matrix4f());
            Matrix4f current = currentTransform.computeIfAbsent(ti, k -> new Matrix4f());
            last.set(current);
            current.set(p);
        });
        p.setTranslation(p.m30() - visualPos.getX(), p.m31() - visualPos.getY(), p.m32() - visualPos.getZ());
    }



    public Matrix4f interpolate(Matrix4f m1, Matrix4f m2, float t) {
        // 1. Decompose Matrix 1
        Vector3f translation1 = new Vector3f();
        m1.getTranslation(translation1);

        Quaternionf rotation1 = new Quaternionf();
        m1.getUnnormalizedRotation(rotation1);

        Vector3f scale1 = new Vector3f();
        m1.getScale(scale1);

        // 2. Decompose Matrix 2
        Vector3f translation2 = new Vector3f();
        m2.getTranslation(translation2);

        Quaternionf rotation2 = new Quaternionf();
        m2.getUnnormalizedRotation(rotation2);

        Vector3f scale2 = new Vector3f();
        m2.getScale(scale2);

        // 3. Interpolate components
        // Translation: Linear Interpolation (Lerp)
        Vector3f lerpTranslation = new Vector3f(translation1).lerp(translation2, t);

        // Scale: Linear Interpolation (Lerp)
        Vector3f lerpScale = new Vector3f(scale1).lerp(scale2, t);

        // Rotation: Spherical Linear Interpolation (Slerp)
        Quaternionf slerpRotation = new Quaternionf(rotation1).slerp(rotation2, t);

        // 4. Recompose into a new Matrix
        mutableMatrix4f.translationRotateScale(lerpTranslation, slerpRotation, lerpScale);

        return mutableMatrix4f;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        transformedInstances.forEach((tii, list) -> {
            list.forEach((ti) -> {
                Matrix4f last = lastTransform.computeIfAbsent(ti, k -> new Matrix4f());
                Matrix4f current = currentTransform.computeIfAbsent(ti, k -> new Matrix4f());

                if (!last.equals(current)) {
                    Matrix4f p = interpolate(last, current, ctx.partialTick());
                    ti.setTransform(p);
                    ti.setChanged();
                    last.set(p);
                }
            });
        });
    }

    @Override
    public void tick(TickableVisual.Context context) {
        poseStackVisual.setDepth(0);
        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        berenderer.render(blockEntity, 0, poseStackVisual, visualBufferSource,LevelRenderer.getLightColor(level, pos.above()), 0);
        rendererPool.release(berenderer);
    }

    public record M(int poseDepth, ModelPart part, Material material) {
    }

    final public Int2ObjectLinkedOpenHashMap<List<M>> posedParts = new Int2ObjectLinkedOpenHashMap<>();
    final public Int2ObjectLinkedOpenHashMap<List<TransformedInstance>> transformedInstances = new Int2ObjectLinkedOpenHashMap<>();
    final private Object2ObjectArrayMap<TransformedInstance, Matrix4f> lastTransform = new Object2ObjectArrayMap<>();
    final private Object2ObjectArrayMap<TransformedInstance, Matrix4f> currentTransform = new Object2ObjectArrayMap<>();
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
        posedParts.forEach((poseDepth, v) -> {
            for (int partIdx = 0; partIdx < v.size(); partIdx++) {
                M m = v.get(partIdx);
                int finalPartIdx = partIdx;
                transformedInstances.compute(poseDepth, (key, value) -> {
                    if (value == null) value = new ArrayList<>();
                    value.add(instancerProvider().instancer(
                                    InstanceTypes.TRANSFORMED, VanillaModel.cachedOf(blockEntity.getType(), poseDepth, finalPartIdx, new VanillaModel(m.part, m.material)))
                            .createInstance());
                    return value;
                });
            }
        });

        poseStackVisual.setRendered();

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

    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }
}
