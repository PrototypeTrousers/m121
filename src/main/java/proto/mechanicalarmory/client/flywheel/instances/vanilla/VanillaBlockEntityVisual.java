package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity> implements SimpleTickableVisual, SimpleDynamicVisual, SinkBufferSourceVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();

    public PoseStackVisual getPoseStackVisual() {
        return poseStackVisual;
    }

    public final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f[] interpolationVecs = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    Quaternionf[] interpolationQuats = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    boolean hasPoseToInterpolate;

    @Override
    public List<PoseStack.Pose> getPoses() {
        return poses;
    }

    List<PoseStack.Pose> poses = new ArrayList<>();
    private final int light;

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        light = LevelRenderer.getLightColor(level, pos.above());
        visualBufferSource = new VisualBufferSource(this);
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
        if(!hasPoseToInterpolate) {
            return;
        }
        if (doDistanceLimitThisFrame(ctx) || !isVisible(ctx.frustum())) {
            return;
        }

        float pt = ctx.partialTick();
        hasPoseToInterpolate = false;
        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (InterpolatedTransformedInstance ti : list) {
                // 1. Check if an update is even needed
                // Only update if the transformation has actually changed between ticks
                if (!ti.previous.equals(ti.current)) {
                    hasPoseToInterpolate = true;
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
        List<List<InterpolatedTransformedInstance>> transformedInstances = getTransformedInstances();
        if (poses.size() == transformedInstances.size()) {
            for (int depth = 0; depth < transformedInstances.size(); depth++) {
                PoseStack.Pose p = poses.get(depth);
                List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);
                for (int i = 0; i < get.size(); i++) {
                    InterpolatedTransformedInstance ti = get.get(i);
                    ti.previous.set(ti.current);
                    if (ti.current.equals(p.pose())) break;
                    hasPoseToInterpolate = true;
                    ti.current.set(p.pose());
                    ti.instance.setTransform(ti.current).setChanged();
                    ti.lastTick = ((ClientLevel) getLevel()).getGameTime();
                }
            }
        }

//        long lastTick = level.getGameTime();
//        poseStackVisual.setDepth(0);
//        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
//        berenderer.render(blockEntity, 1, poseStackVisual, visualBufferSource, light, 0);
//        rendererPool.release(berenderer);
//        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
//            for (Iterator<InterpolatedTransformedInstance> iterator = list.iterator(); iterator.hasNext(); ) {
//                InterpolatedTransformedInstance ti = iterator.next();
//                if (ti.lastTick != lastTick) {
//                    ti.instance.delete();
//                    iterator.remove();
//                }
//            }
//        }
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
