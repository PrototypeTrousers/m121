package proto.mechanicalarmory.client.flywheel.slicer;

public class PartPoseConfig {
    public boolean customPose = false;
    public float yOffset = 0.0F;
    public float xRot = 0.0F;
    public float zRotOffset = 0.0F;

    /**
     * True when the renderer applies the block's directional facing via poseStack.mulPose(direction.getRotation()).
     * This allows PoseHelper to reconstruct the orientation from the BlockState's facing property without
     * hardcoding specific block types.
     */
    public boolean usesFacingRotation = false;

    /**
     * True when the renderer flips the model vertically (poseStack.scale(1, -1, -1) pattern).
     * Combined with usesFacingRotation this produces the ShulkerBox-style transform.
     */
    public boolean verticallyFlipped = false;

    /** Y translation applied before facing rotation, e.g. translate(0, -1, 0) in ShulkerBox. */
    public float postRotationYOffset = 0.0F;

    @Override
    public String toString() {
        return "PartPoseConfig{customPose=" + customPose +
               ", yOffset=" + yOffset +
               ", xRot=" + xRot +
               ", zRotOffset=" + zRotOffset +
               ", usesFacingRotation=" + usesFacingRotation +
               ", verticallyFlipped=" + verticallyFlipped +
               ", postRotationYOffset=" + postRotationYOffset + '}';
    }
}
