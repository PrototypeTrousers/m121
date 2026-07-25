package proto.mechanicalarmory.client.flywheel.slicer;

public class PartPoseConfig {
    public boolean customPose = false;
    public float yOffset = 0.0F;
    public float xRot = 0.0F;
    public float zRotOffset = 0.0F;

    @Override
    public String toString() {
        return "PartPoseConfig{customPose=" + customPose +
               ", yOffset=" + yOffset +
               ", xRot=" + xRot +
               ", zRotOffset=" + zRotOffset + '}';
    }
}
