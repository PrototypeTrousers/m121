package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.StagingBuffer;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.IMechanicalArmoryCullGroup;
import proto.mechanicalarmory.client.flywheel.instances.arm.ArmVisual;
import proto.mechanicalarmory.client.flywheel.instances.arm.PartTransformBuffer;

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupBufferMixin implements IMechanicalArmoryCullGroup {
    @Shadow @Final private InstanceType<?> instanceType;

    @Unique private int mechanicalArmory$matrixSsboId   = 0;
    @Unique private int mechanicalArmory$allocatedSlots = 0; // in mat4 slots

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (instanceType.vertexShader().getPath().contains("interpolated")) {
            mechanicalArmory$matrixSsboId = GL43C.glGenBuffers();
        }
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void onUploadTail(StagingBuffer stagingBuffer, CallbackInfo ci) {
        if (mechanicalArmory$matrixSsboId == 0 || ArmVisual.visuals <= 0) return;

        // Size finalPartMatrices to cover every partIdx that has been written.
        // highWaterMark() is the highest partIdx written; +1 for count.
        int required = PartTransformBuffer.get().highWaterMark() + 1;

        if (required > mechanicalArmory$allocatedSlots) {
            // Pad to avoid re-allocating on every arm addition.
            mechanicalArmory$allocatedSlots = required + 32;

            long bytes = (long) mechanicalArmory$allocatedSlots * 64L; // 64 bytes per mat4

            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, mechanicalArmory$matrixSsboId);
            GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, bytes, GL43C.GL_DYNAMIC_COPY);
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void onDelete(CallbackInfo ci) {
        if (mechanicalArmory$matrixSsboId != 0) {
            GL43C.glDeleteBuffers(mechanicalArmory$matrixSsboId);
            mechanicalArmory$matrixSsboId = 0;
        }
        PartTransformBuffer.destroyGlobal();
    }

    @Unique public int mechanicalArmory$getMatrixSsboId() { return mechanicalArmory$matrixSsboId; }
}
