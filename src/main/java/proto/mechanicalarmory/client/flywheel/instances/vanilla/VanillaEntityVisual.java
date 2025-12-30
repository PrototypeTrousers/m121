package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class VanillaEntityVisual extends AbstractEntityVisual<Entity> implements SimpleTickableVisual, SimpleDynamicVisual, SinkBufferSourceVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f[] interpolationVecs = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    Quaternionf[] interpolationQuats = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    private final int light;
    boolean hasPoseToInterpolate;


    @Override
    public List<PoseStack.Pose> getPoses() {
        return poses;
    }

    List<PoseStack.Pose> poses = new ArrayList<>();


    public VanillaEntityVisual(VisualizationContext ctx, Entity entity, float partialTick) {
        super(ctx, entity, partialTick);
        light = computePackedLight(partialTick);
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
    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if(!hasPoseToInterpolate) {
            return;
        }
        if (!isVisible(ctx.frustum())) {
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
                    ti.instance.setVisible(!p.pose().equals(PoseStackVisual.ZERO));
                    hasPoseToInterpolate = true;
                    ti.current.set(p.pose());
                    ti.instance.setTransform(ti.current).setChanged();
                }
            }
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
    public PoseStackVisual getPoseStackVisual() {
        return poseStackVisual;
    }
}
