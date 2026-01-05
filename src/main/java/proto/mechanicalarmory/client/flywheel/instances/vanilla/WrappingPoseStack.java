package proto.mechanicalarmory.client.flywheel.instances.vanilla;


import com.mojang.math.Transformation;
import dev.engine_room.flywheel.lib.internal.FlwLibLink;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayDeque;
import java.util.Deque;

public class WrappingPoseStack extends RecyclingPoseStack {

    private final SinkBufferSourceVisual visual;
    private boolean rendered;
    public final ExtendedRecyclingPoseStack wrappedPoseStack;

    public WrappingPoseStack(SinkBufferSourceVisual visual) {
        super();
        this.wrappedPoseStack = new ExtendedRecyclingPoseStack(visual);
        this.visual = visual;
    }

    public ExtendedRecyclingPoseStack getWrappedPoseStack() {
        return wrappedPoseStack;
    }

    @Override
    public void pushPose() {
        super.pushPose();
        if (wrappedPoseStack != null) {
            wrappedPoseStack.pushPose();
            wrappedPoseStack.depth++;
        }
    }

    @Override
    public void popPose() {
        super.popPose();
        if (wrappedPoseStack != null) {
            wrappedPoseStack.popPose();
        }
    }

    @Override
    public void translate(float x, float y, float z) {
        super.translate(x, y, z);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.translate(x, y, z);
        }
    }

    @Override
    public void scale(float x, float y, float z) {
        super.scale(x, y, z);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.scale(x, y, z);
        }
    }

    @Override
    public void mulPose(Quaternionf quaternion) {
        super.mulPose(quaternion);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.mulPose(quaternion);
        }
    }

    @Override
    public void rotateAround(Quaternionf quaternion, float x, float y, float z) {
        super.rotateAround(quaternion, x, y, z);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.rotateAround(quaternion, x, y, z);
        }
    }

    @Override
    public void setIdentity() {
        super.setIdentity();
        if (wrappedPoseStack != null) {
            wrappedPoseStack.setIdentity();
        }
    }

    @Override
    public void mulPose(Matrix4f pose) {
        super.mulPose(pose);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.mulPose(pose);
        }
    }

    @Override
    public void pushTransformation(Transformation transformation) {
        super.pushTransformation(transformation);
        if (wrappedPoseStack != null) {
            wrappedPoseStack.pushTransformation(transformation);
        }
    }

    public boolean isRendered() {
        return rendered;
    }

    public void setRendered() {
        this.rendered = true;
    }

    public SinkBufferSourceVisual getVisual() {
        return visual;
    }

    public void setDepth(int depth) {
        wrappedPoseStack.depth = depth;
    }
}
