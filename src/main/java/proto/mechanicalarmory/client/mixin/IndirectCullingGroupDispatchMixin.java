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

        int armsCount = self.mechanicalArmory$getInstanceCount() / 4;
        if (armsCount <= 0) return;

        GL43C.glUseProgram(MechanicalArmory.computeShaderId);
        GL43C.glUniform1ui(50, armsCount);

        // bindForCull() binds:
        //   slot 0 = page frame descriptors
        //   slot 1 = instance buffer  (_flw_unpackInstance reads from here)
        //   slot 2 = draw-instance index
        //   slot 3 = model buffer
        buffers.bindForCull();

        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);

        // One workgroup of 4 threads — one thread per arm.
        GL43C.glDispatchCompute(1, 1, 1);

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
    }
}
