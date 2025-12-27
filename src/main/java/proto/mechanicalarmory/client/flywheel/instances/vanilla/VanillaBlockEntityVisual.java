package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity> implements SimpleTickableVisual, SimpleDynamicVisual, SinkBufferSourceVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final BlockRendererBuilder<BlockEntity> builder;
    private final ArrayObjectPool<BlockEntityRenderer<BlockEntity>> rendererPool;
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f[] interpolationVecs = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    Quaternionf[] interpolationQuats = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    private final int light;

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        light = LevelRenderer.getLightColor(level, pos.above());
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
        poseStackVisual.last().pose().setTranslation(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        berenderer.render(blockEntity, partialTick, poseStackVisual, visualBufferSource, light, 0);
        rendererPool.release(berenderer);
        visualBufferSource.setRendered(true);
        poseStackVisual.setRendered();

        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            for (InterpolatedTransformedInstance ti : key) {
                ti.instance.light(light);
                ti.current.setTranslation(ti.current.m30() + visualPos.getX(), ti.current.m31() + visualPos.getY(), ti.current.m32() + visualPos.getZ());
                ti.instance.setTransform(ti.current).setChanged();
                ti.current.setTranslation(ti.current.m30() - visualPos.getX(), ti.current.m31() - visualPos.getY(), ti.current.m32() - visualPos.getZ());
            }
        }
    }

    @Override
    public LevelAccessor getLevel() {
        return this.level;
    }

    @Override
    public Matrix4f getMutableInterpolationMatrix4f() {
        return mutableInterpolationMatrix4f;
    }

    public Vector3f[] getInterpolationVecs() {
        return interpolationVecs;
    }

    public Quaternionf[] getInterpolationQuats() {
        return interpolationQuats;
    }

    @Override
    public int getLight() {
        return light;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (doDistanceLimitThisFrame(ctx) || !isVisible(ctx.frustum())) {
            return;
        }

        float pt = ctx.partialTick();

        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (InterpolatedTransformedInstance ti : list) {
                // 1. Check if an update is even needed
                // Only update if the transformation has actually changed between ticks
                if (!ti.previous.equals(ti.current)) {

                    // 2. Interpolate into a temporary matrix
                    // Do NOT modify ti.current or ti.previous here!
                    Matrix4f interpolated = interpolate(ti.previous, ti.current, pt);

                    // 3. Apply to the rendering instance
                    ti.instance.setTransform(interpolated);
                    ti.instance.setChanged();
                }
            }
        }
    }

    @Override
    public void tick(TickableVisual.Context context) {
        long lastTick = level.getGameTime();
        poseStackVisual.setDepth(0);
        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        berenderer.render(blockEntity, 1, poseStackVisual, visualBufferSource, light, 0);
        rendererPool.release(berenderer);
        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (Iterator<InterpolatedTransformedInstance> iterator = list.iterator(); iterator.hasNext(); ) {
                InterpolatedTransformedInstance ti = iterator.next();
                if (ti.lastTick != lastTick) {
                    ti.instance.delete();
                    iterator.remove();
                }
            }
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            key.forEach(ti -> consumer.accept(ti.instance));
        }
    }

    @Override
    public void updateLight(float partialTick) {
        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            key.forEach(ti -> ti.instance.light(light));
        }
    }

    @Override
    protected void _delete() {
        for (List<InterpolatedTransformedInstance> v : transformedInstances) {
            v.forEach(ti -> ti.instance.delete());
        }
    }

    @Override
    public List<List<InterpolatedTransformedInstance>> getTransformedInstances() {
        return transformedInstances;
    }

    @Override
    public InstancerProvider getInstanceProvider() {
        return instancerProvider();
    }

    @Override
    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }
}
