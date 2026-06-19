package proto.mechanicalarmory.client.flywheel.gltf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record MyPartPose(float x, float y, float z, float xRot, float yRot, float zRot) {
    public static final MyPartPose ZERO = offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public static MyPartPose offset(float x, float y, float z) {
        return offsetAndRotation(x, y, z, 0.0F, 0.0F, 0.0F);
    }

    public static MyPartPose rotation(float xRot, float yRot, float zRot) {
        return offsetAndRotation(0.0F, 0.0F, 0.0F, xRot, yRot, zRot);
    }

    public static MyPartPose offsetAndRotation(float x, float y, float z, float xRot, float yRot, float zRot) {
        return new MyPartPose(x, y, z, xRot, yRot, zRot);
    }
}
