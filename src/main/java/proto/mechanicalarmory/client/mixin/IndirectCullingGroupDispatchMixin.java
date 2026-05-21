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

    @Inject(method = "dispatchCull", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;bind()V"))
    private void onBeforeDispatchCull(CallbackInfo ci) {
        var bufferMixin = (IMechanicalArmoryCullGroup) this;
        int targetSsboId = bufferMixin.mechanicalArmory$getMatrixSsboId();
        if (targetSsboId == 0) return;

        int armsCount = bufferMixin.mechanicalArmory$getInstanceCount() / 4;
        if (armsCount <= 0) return;

        int computeProgramId = MechanicalArmory.computeShaderId;
        GL43C.glUseProgram(computeProgramId);

        // Upload the instance count to location 50 directly to the shader
        GL43C.glUniform1ui(50, armsCount);

        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, buffers.objectStorage.objectBuffer.handle());
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);

        // Dispatch based on absolute instances
        int workGroupsX = (armsCount + 63) / 64;
        GL43C.glDispatchCompute(workGroupsX, 1, 1);

        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        GL43C.glUseProgram(0);
    }

    // --- RE-BIND PALETTE RIGHT BEFORE DRAW CALL SUBMISSIONS ---

    @Inject(method = "submitSolid", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectBuffers;bindForDraw()V", shift = At.Shift.AFTER))
    private void onAfterBindForDrawSolid(CallbackInfo ci) {
        mechanicalArmory$bindPaletteToVertexShader();
    }

    @Inject(method = "submitTransparent", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectBuffers;bindForDraw()V", shift = At.Shift.AFTER))
    private void onAfterBindForDrawTransparent(PipelineCompiler.OitMode oit, CallbackInfo ci) {
        mechanicalArmory$bindPaletteToVertexShader();
    }

    @Unique
    private void mechanicalArmory$bindPaletteToVertexShader() {
        var bufferMixin = (IMechanicalArmoryCullGroup) this;
        int targetSsboId = bufferMixin.mechanicalArmory$getMatrixSsboId();
        
        // Ensure your calculated matrix palette is plugged into binding slot 1 when the vertex shader runs
        if (targetSsboId != 0) {
            GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);
        }
    }
}