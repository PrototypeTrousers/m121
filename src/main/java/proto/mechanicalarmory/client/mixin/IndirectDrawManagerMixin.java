package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.backend.engine.LightStorage;
import dev.engine_room.flywheel.backend.engine.embed.EnvironmentStorage;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectDrawManager;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = IndirectDrawManager.class, remap = false)
public class IndirectDrawManagerMixin {
    @Unique
    private static int mechanicalArmory$matrixSsboId = 0;
    @Unique
    private static int mechanicalArmory$allocatedMatricesCount = 0;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Generate the native OpenGL storage buffer ID on startup
        mechanicalArmory$matrixSsboId = GL43C.glGenBuffers();
        mechanicalArmory$allocatedMatricesCount = 0;
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void onDelete(CallbackInfo ci) {
        // Safe GPU memory cleanup when the engine reloads or closes
        if (mechanicalArmory$matrixSsboId != 0) {
            GL43C.glDeleteBuffers(mechanicalArmory$matrixSsboId);
            mechanicalArmory$matrixSsboId = 0;
        }
    }

    // A public accessor for your dispatch hook to grab the buffer ID
    @Unique
    private static int mechanicalArmory$getMatrixSsboId(int requiredMatricesCount) {
        if (requiredMatricesCount > mechanicalArmory$allocatedMatricesCount) {
            // Reallocate / expand buffer if the arm count increases
            mechanicalArmory$allocatedMatricesCount = requiredMatricesCount + 64; // pad allocations
            long totalByteSize = (long) mechanicalArmory$allocatedMatricesCount * 64; // 64 bytes per mat4
            
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, mechanicalArmory$matrixSsboId);
            GL43C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, totalByteSize, GL43C.GL_DYNAMIC_COPY);
            GL43C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);
        }
        return mechanicalArmory$matrixSsboId;
    }

    @Unique
    private static int mechanicalArmory$computeProgramId = -1; // Initialize this via a shader compiler utility

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/engine_room/flywheel/backend/engine/indirect/IndirectCullingGroup;dispatchCull()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void onBeforeCullAndDraw(LightStorage lightStorage, EnvironmentStorage environmentStorage, CallbackInfo ci) {
        RenderSystem.assertOnRenderThread();

        // 1. Determine active counts.
        // In your real logic, track how many total instances exist in your layout managers.
        int totalInstancesUploadedByFlywheel = 300; // Example: 100 arms * 3 parts
        int totalArmAssemblies = totalInstancesUploadedByFlywheel / 3;

        if (totalArmAssemblies <= 0) return;

        // 2. Resolve/Resize our Matrix Palette Destination Buffer
        int targetSsboId = mechanicalArmory$getMatrixSsboId(totalInstancesUploadedByFlywheel);

        // 3. Bind the Compute Shader Program
        GL43C.glUseProgram(mechanicalArmory$computeProgramId);

        // 4. Bind the SSBO Pipes
        // Binding 0: Flywheel's Input Buffer Object (This is managed by Flywheel's active context layout)
        // Note: You must bind Flywheel's active instance buffer object id to base index 0 here.

        // Binding 1: Our Write-Only Matrix Palette Buffer
        GL43C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1, targetSsboId);

        // 5. Dispatch threads across hardware warps (64 threads per workgroup)
        int workGroupsX = (totalArmAssemblies + 63) / 64;
        GL43C.glDispatchCompute(workGroupsX, 1, 1);

        // 6. CRITICAL: Enforce memory barrier synchronization stall
        // Forces the vertex shader to wait until the matrix outputs are fully finalized in VRAM.
        GL43C.glMemoryBarrier(GL43C.GL_SHADER_STORAGE_BARRIER_BIT);

        // Clean up state
        GL43C.glUseProgram(0);
    }
}