package proto.mechanicalarmory.client.flywheel.instances.arm;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.ColoredLitOverlayInstance;

public class InterpolatedInstance extends ColoredLitOverlayInstance {
    // Previous Keyframe (Tick N)
    public final Vector3f posFrom = new Vector3f();
    public final Quaternionf rotFrom = new Quaternionf();
    public final Vector3f scaleFrom = new Vector3f(1.0f, 1.0f, 1.0f);

    // Current Keyframe (Tick N+1 / Goal)
    public final Vector3f posGoal = new Vector3f();
    public final Quaternionf rotGoal = new Quaternionf();
    public final Vector3f scaleGoal = new Vector3f(1.0f, 1.0f, 1.0f);
    public int partIdx;

    public InterpolatedInstance(InstanceType<? extends InterpolatedInstance> type, InstanceHandle handle) {
       super(type, handle);
    }

    /**
     * Set the entire transformation baseline state for the past frame interval.
     */
    public InterpolatedInstance setFromTransform(Vector3fc position, Quaternionfc rotation, Vector3fc scale) {
        this.posFrom.set(position);
        this.rotFrom.set(rotation);
        this.scaleFrom.set(scale);
        return this;
    }

    /**
     * Set the target objective configuration for the upcoming frame state.
     */
    public InterpolatedInstance setGoalTransform(Vector3fc position, Quaternionfc rotation, Vector3fc scale) {
        this.posGoal.set(position);
        this.rotGoal.set(rotation);
        this.scaleGoal.set(scale);
        return this;
    }

    /**
     * Utility method to completely wipe out scale properties across both frames.
     * This forces the GPU to drop all geometry for this model part instantly.
     */
    public InterpolatedInstance setZeroTransform() {
        this.posFrom.zero();
        this.rotFrom.identity();
        this.scaleFrom.zero();

        this.posGoal.zero();
        this.rotGoal.identity();
        this.scaleGoal.zero();
        return this;
    }
}