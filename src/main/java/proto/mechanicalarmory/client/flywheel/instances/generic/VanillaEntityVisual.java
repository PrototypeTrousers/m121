package proto.mechanicalarmory.client.flywheel.instances.generic;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.WrappingPoseStack;

import java.util.ArrayList;
import java.util.List;

public class VanillaEntityVisual extends AbstractEntityVisual<Entity> implements SimpleDynamicVisual, FrameExtractionAnimatedVisual {
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final WrappingPoseStack extendedRecyclingPoseStack = new WrappingPoseStack(this);
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f[] interpolationVecs = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    Quaternionf[] interpolationQuats = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    private final int light;
    boolean hasPoseToInterpolate;
    private boolean updateTransforms;
    private boolean rendered;
    private MultiBufferSource bufferSource;

    public ShadowComponent getShadowComponent() {
        return shadowComponent;
    }

    ShadowComponent shadowComponent;


    @Override
    public List<PoseStack.Pose> getPoses() {
        return poses;
    }

    @Override
    public void setBufferSource(MultiBufferSource bufferSource) {
        this.bufferSource = bufferSource;
    }

    @Override
    public MultiBufferSource getBufferSource() {
        return this.bufferSource;
    }

    List<PoseStack.Pose> poses = new ArrayList<>();


    public VanillaEntityVisual(VisualizationContext ctx, Entity entity, float partialTick) {
        super(ctx, entity, partialTick);
        light = computePackedLight(partialTick);
        shadowComponent = new ShadowComponent(ctx, entity);
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
        if (updateTransforms) {
            if (poses.size() == transformedInstances.size()) {
                for (int depth = 0; depth < transformedInstances.size(); depth++) {
                    PoseStack.Pose p = poses.get(depth);
                    List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);
                    for (int i = 0; i < get.size(); i++) {
                        InterpolatedTransformedInstance ti = get.get(i);
                        ti.previous().set(ti.current());
                        ti.instance().setVisible(!p.pose().equals(ExtendedRecyclingPoseStack.ZERO));
                        hasPoseToInterpolate = true;
                        ti.current().set(p.pose());
                    }
                }
            }
            updateTransforms = false;
        }

        if(!hasPoseToInterpolate) {
            return;
        }
        if (!isVisible(ctx.frustum())) {
            return;
        }

        shadowComponent.beginFrame(ctx);

        float pt = ctx.partialTick();
        hasPoseToInterpolate = false;
        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (InterpolatedTransformedInstance ti : list) {
                // 1. Check if an update is even needed
                // Only update if the transformation has actually changed between ticks
                if (!ti.current().equals(ti.instance().pose,0.0001f)) {
                    hasPoseToInterpolate = true;
                    // 2. Interpolate into a temporary matrix
                    // Do NOT modify ti.current or ti.previous here!
                    Matrix4f interpolated = interpolate(ti.previous(), ti.current(), pt);

                    // 3. Apply to the rendering instance
                    ti.instance().setTransform(interpolated);
                    ti.instance().setChanged();
                }
            }
        }
    }

    public void dirtyTransforms() {
        this.updateTransforms = true;
    }

    @Override
    public boolean isRendered() {
        return this.rendered;
    }

    @Override
    public void setRendered() {
        this.rendered = true;
    }

    @Override
    protected void _delete() {
        for (List<InterpolatedTransformedInstance> v : transformedInstances) {
            v.forEach(ti -> ti.instance().delete());
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
    public WrappingPoseStack getPoseStackVisual() {
        return extendedRecyclingPoseStack;
    }
}
