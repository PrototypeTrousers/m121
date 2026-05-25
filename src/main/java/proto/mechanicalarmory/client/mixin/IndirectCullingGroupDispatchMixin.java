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
import proto.mechanicalarmory.client.flywheel.instances.arm.ComputeDebugger;

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupDispatchMixin {
    @Shadow @Final private IndirectBuffers buffers;

    @Unique private static final int DEBUG_INTERVAL = 60;
    @Unique private int mechanicalArmory$frameCount = 0;

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

        // bindForCull() binds:
        //   slot 0 = page frame descriptors  ← shader reads validBits and modelIndex here
        //   slot 1 = instance buffer          ← _flw_unpackInstance reads here
        //   slot 2 = draw-instance index
        //   slot 3 = model buffer
        buffers.bindForCull();
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);

        // Each segment group (base/first/second/item) gets its own page.
        // pagesPerSegment = ceil(armsCount / PAGE_SIZE), total pages = 4 * pagesPerSegment.
        // For armsCount <= 32, this is always 4 workgroups.
        int pagesPerSegment = (armsCount + 31) / 32;
        int totalPages      = 4 * pagesPerSegment;
        GL43C.glDispatchCompute(totalPages, 1, 1);

        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        GL43C.glUseProgram(0);

        // Debug readback — remove when confirmed working.
        if (mechanicalArmory$frameCount++ % DEBUG_INTERVAL == 0) {
            GL43C.glFinish();
            ComputeDebugger.checkAll(buffers, targetSsboId, armsCount, true);
        }
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
