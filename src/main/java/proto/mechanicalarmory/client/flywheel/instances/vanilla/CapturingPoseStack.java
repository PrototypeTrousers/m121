package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class CapturingPoseStack extends PoseStack {
    CapturedModelTreeBuilder modelTreeBuilder;
    public CapturingPoseStack(CapturedModelTreeBuilder modelTreeBuilder) {
        super();
        this.modelTreeBuilder = modelTreeBuilder;
    }

    @Override
    public void translate(double x, double y, double z) {
        super.translate(x, y, z);
    }

    @Override
    public void translate(float x, float y, float z) {
        super.translate(x, y, z);
    }

    @Override
    public void scale(float x, float y, float z) {
        super.scale(x, y, z);
    }

    @Override
    public void mulPose(Quaternionf quaternion) {
        super.mulPose(quaternion);
    }

    @Override
    public void rotateAround(Quaternionf quaternion, float x, float y, float z) {
        super.rotateAround(quaternion, x, y, z);
    }

    @Override
    public void pushPose() {
        super.pushPose();
        modelTreeBuilder.addNode(this.last());
        modelTreeBuilder.recordBufferIndex();
    }

    @Override
    public void popPose() {
        super.popPose();
    }

    @Override
    public Pose last() {
        return super.last();
    }

    @Override
    public boolean clear() {
        return super.clear();
    }

    @Override
    public void setIdentity() {
        super.setIdentity();
    }

    @Override
    public void mulPose(Matrix4f pose) {
        super.mulPose(pose);
    }

    @Override
    public void pushTransformation(Transformation transformation) {
        super.pushTransformation(transformation);
    }
}
