package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectBuffers;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.flywheel.IMechanicalArmoryCullGroup;
import proto.mechanicalarmory.client.flywheel.instances.arm.ArmVisual;
import proto.mechanicalarmory.client.flywheel.instances.arm.PartTransformBuffer;

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupDispatchMixin {
    @Shadow @Final private IndirectBuffers buffers;

    @Inject(
            method = "dispatchCull",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;bind()V")
    )
    private void onBeforeDispatchCull(CallbackInfo ci) {
        var self = (IMechanicalArmoryCullGroup) this;

        int targetSsboId = self.mechanicalArmory$getMatrixSsboId();
        if (targetSsboId == 0) return;
        if (ArmVisual.visuals <= 0) return;

        // Upload CPU-side transforms to GPU and bind TransformBuffer (binding 13).
        // This is the render thread — safe for all GL operations.
        PartTransformBuffer transformBuffer = PartTransformBuffer.get();
        transformBuffer.uploadIfDirty();
        transformBuffer.bindTo(13);

        // Dispatch one thread per partIdx slot (gid == partIdx in the compute shader).
        // highWaterMark is the max partIdx written — covers all nodes, mesh and non-mesh.
        // This sidesteps Flywheel's page layout entirely: no _flw_unpackInstance needed.
        int partCount = transformBuffer.highWaterMark() + 1;
        if (partCount <= 0) return;

        GL43C.glUseProgram(MechanicalArmory.computeShaderId);
        GL43C.glUniform1ui(50, (int) partCount);

        buffers.bindForCull();
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);

        int workgroups = Math.max(1, (partCount + 31) / 32);
        GL43C.glDispatchCompute(workgroups, 1, 1);

        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        GL43C.glUseProgram(0);
    }

    @Inject(
            method = "submitSolid",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectBuffers;bindForDraw()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterBindForDrawSolid(CallbackInfo ci) {
        mechanicalArmory$bindOutputMatrices();
    }

    @Inject(
            method = "submitTransparent",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectBuffers;bindForDraw()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onAfterBindForDrawTransparent(PipelineCompiler.OitMode oit, CallbackInfo ci) {
        mechanicalArmory$bindOutputMatrices();
    }

    @Unique
    private void mechanicalArmory$bindOutputMatrices() {
        int ssboId = ((IMechanicalArmoryCullGroup) this).mechanicalArmory$getMatrixSsboId();
        if (ssboId != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, ssboId);
        }
        // Also re-bind TransformBuffer (binding 13) for vertex shader access if needed.
        PartTransformBuffer.get().bindTo(13);
    }
}
