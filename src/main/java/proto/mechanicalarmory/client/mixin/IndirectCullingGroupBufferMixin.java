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

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupBufferMixin implements IMechanicalArmoryCullGroup {
    @Shadow @Final private InstanceType<?> instanceType;
    @Shadow private int instanceCountThisFrame;

    @Unique private int mechanicalArmory$matrixSsboId = 0;
    @Unique private int mechanicalArmory$allocatedCount = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Only allocate a GPU buffer if this culling group is for your mechanical arm instance layout
        if (instanceType.vertexShader().getPath().contains("interpolated")) {
            mechanicalArmory$matrixSsboId = GL43C.glGenBuffers();
        }
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void onUploadTail(StagingBuffer stagingBuffer, CallbackInfo ci) {
        if (mechanicalArmory$matrixSsboId == 0 || instanceCountThisFrame <= 0) return;

        // Dynamically resize your matrix palette buffer if the instance count grows
        if (instanceCountThisFrame > mechanicalArmory$allocatedCount) {
            mechanicalArmory$allocatedCount = instanceCountThisFrame + 64; // Allocation padding
            long totalByteSize = (long) mechanicalArmory$allocatedCount * 64; // 64 bytes per mat4 matrix

            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, mechanicalArmory$matrixSsboId);
            GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, totalByteSize, GL43C.GL_DYNAMIC_COPY);
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void onDelete(CallbackInfo ci) {
        if (mechanicalArmory$matrixSsboId != 0) {
            GL43C.glDeleteBuffers(mechanicalArmory$matrixSsboId);
            mechanicalArmory$matrixSsboId = 0;
        }
    }

    // Accessors so the dispatch pipeline below can read these internal properties cleanly
    @Unique public int mechanicalArmory$getMatrixSsboId() { return mechanicalArmory$matrixSsboId; }
    @Unique public int mechanicalArmory$getInstanceCount() { return instanceCountThisFrame; }
}