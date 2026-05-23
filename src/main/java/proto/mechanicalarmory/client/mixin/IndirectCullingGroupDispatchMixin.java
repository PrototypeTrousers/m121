package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectCullingGroup;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectBuffers;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import dev.engine_room.flywheel.backend.engine.indirect.ObjectStorage;
import org.lwjgl.opengl.GL15C;
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

import java.util.List;

@Mixin(value = IndirectCullingGroup.class, remap = false)
public class IndirectCullingGroupDispatchMixin {
    @Shadow @Final private IndirectBuffers buffers;

    // Raw type is intentional — generic type is erased at runtime.
    @Shadow private List<IndirectInstancer> instancers;

    /** GL buffer object that holds the virtual→real page mapping each frame. */
    @Unique
    private int mechanicalArmory$pagesSsboId = 0;

    @Inject(
            method = "dispatchCull",
            at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;bind()V")
    )
    private void onBeforeDispatchCull(CallbackInfo ci) {
        var self = (IMechanicalArmoryCullGroup) this;

        int targetSsboId = self.mechanicalArmory$getMatrixSsboId();
        if (targetSsboId == 0) return;

        // armsCount = total instances / 4 segments per arm
        int armsCount = self.mechanicalArmory$getInstanceCount() / 4;
        if (armsCount <= 0) return;

        // ── Get the virtual→real page mapping from the first instancer ────────
        // All arm instances live in a single instancer, so instancers.get(0) is it.
        if (instancers.isEmpty()) return;

        IndirectInstancer instancer = instancers.get(0);
        ObjectStorage.Mapping mapping =
                ((IndirectInstancerAccessor) instancer).mechanicalArmory$getMapping();
        int[] pages =
                ((ObjectStorageMappingAccessor) mapping).mechanicalArmory$getPages();

        // ── Upload pages to a persistent SSBO at binding 13 ──────────────────
        if (mechanicalArmory$pagesSsboId == 0) {
            mechanicalArmory$pagesSsboId = GL15C.glGenBuffers();
        }
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, mechanicalArmory$pagesSsboId);
        GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, pages, GL15C.GL_DYNAMIC_DRAW);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

        // ── Dispatch ──────────────────────────────────────────────────────────
        GL43C.glUseProgram(MechanicalArmory.computeShaderId);

        // Uniform 50 = armsCount (NOT total instances — the shader derives the rest)
        GL43C.glUniform1ui(50, armsCount);

        // bindForCull() populates:
        //   binding 0 = page frame descriptors
        //   binding 1 = instance buffer  (_flw_unpackInstance reads from here)
        //   binding 2 = draw-instance index
        //   binding 3 = model buffer
        buffers.bindForCull();

        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 12, targetSsboId);           // output matrices
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 13, mechanicalArmory$pagesSsboId); // page map

        // One thread per arm, 32 threads per workgroup
        int workGroupsX = (armsCount + 32) / 32;
        GL43C.glDispatchCompute(workGroupsX, 1, 1);

        // Ensure the output SSBO is visible to subsequent vertex shaders
        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);

        GL43C.glUseProgram(0);
    }

    // ── Re-bind the output matrix palette before each draw submission ─────────

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
