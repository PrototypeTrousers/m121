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

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupBufferMixin implements IMechanicalArmoryCullGroup {
    @Shadow @Final private InstanceType<?> instanceType;

    @Unique private int mechanicalArmory$matrixSsboId     = 0;
    @Unique private int mechanicalArmory$allocatedSlots   = 0; // in mat4 slots, not instances

    // ObjectStorage page size — must match ObjectStorage.PAGE_SIZE = 32
    @Unique private static final int PAGE_SIZE = 32;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        if (instanceType.vertexShader().getPath().contains("interpolated")) {
            mechanicalArmory$matrixSsboId = GL43C.glGenBuffers();
        }
    }

    @Inject(method = "upload", at = @At("TAIL"))
    private void onUploadTail(StagingBuffer stagingBuffer, CallbackInfo ci) {
        if (mechanicalArmory$matrixSsboId == 0 || ArmVisual.visuals <= 0) return;

        int armsCount = ArmVisual.visuals;

        // Each segment group (base/first/second/item) gets its own page of PAGE_SIZE slots.
        // The compute shader writes at globalIdx = pageIndex * PAGE_SIZE + slotInPage,
        // so the buffer must cover the full page-scattered address range, not just
        // instanceCountThisFrame sequential slots.
        //
        // Example: 3 arms → pagesPerSegment=1, totalSlots=4*32=128 → 8192 bytes
        // Old (wrong): 12 * 64 = 768 bytes → writes to slots 32-98 were out of bounds
        int pagesPerSegment = (armsCount + PAGE_SIZE - 1) / PAGE_SIZE;
        int requiredSlots   = 4 * pagesPerSegment * PAGE_SIZE;

        if (requiredSlots > mechanicalArmory$allocatedSlots) {
            // Add padding so small arm-count changes don't re-allocate every frame
            mechanicalArmory$allocatedSlots = requiredSlots + PAGE_SIZE;

            long totalByteSize = (long) mechanicalArmory$allocatedSlots * 64L; // 64 bytes per mat4

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

    @Unique public int mechanicalArmory$getMatrixSsboId()  { return mechanicalArmory$matrixSsboId; }
}
