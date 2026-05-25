package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.backend.engine.indirect.IndirectBuffers;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class ComputeDebugger {

    private static final int PAGE_SIZE = 32; // ObjectStorage.PAGE_SIZE

    // ── Step 1: instance buffer (binding 1) ──────────────────────────────────
    public static void checkInstanceBuffer(int instanceBufferHandle, int totalInstances) {
        System.out.println("\n=== [DEBUG] Instance Buffer (binding 1) ===");

        int bytesToRead = totalInstances * 24 * Integer.BYTES;
        ByteBuffer buf = MemoryUtil.memAlloc(bytesToRead);

        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, instanceBufferHandle);
        GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, buf);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

        IntBuffer   ints   = buf.asIntBuffer();
        FloatBuffer floats = buf.asFloatBuffer();

        for (int idx = 0; idx < totalInstances; idx++) {
            int base = idx * 24;

            int   colorPacked = ints.get(base);
            float r = ((colorPacked      ) & 0xFF) / 255f;
            float g = ((colorPacked >>  8) & 0xFF) / 255f;
            float b = ((colorPacked >> 16) & 0xFF) / 255f;
            float a = ((colorPacked >> 24) & 0xFF) / 255f;

            float posFromX = floats.get(base + 3);
            float posFromY = floats.get(base + 4);
            float posFromZ = floats.get(base + 5);

            float rotFromX = floats.get(base + 6);
            float rotFromY = floats.get(base + 7);
            float rotFromZ = floats.get(base + 8);
            float rotFromW = floats.get(base + 9);

            float posGoalX = floats.get(base + 13);
            float posGoalY = floats.get(base + 14);
            float posGoalZ = floats.get(base + 15);

            int partIdx = ints.get(base + 23);

            System.out.printf(
                    "  [%2d] color=(%.2f,%.2f,%.2f,%.2f) posFrom=(%.3f,%.3f,%.3f) " +
                            "rotFrom=(%.3f,%.3f,%.3f,%.3f) posGoal=(%.3f,%.3f,%.3f) partIdx=%d%n",
                    idx, r, g, b, a,
                    posFromX, posFromY, posFromZ,
                    rotFromX, rotFromY, rotFromZ, rotFromW,
                    posGoalX, posGoalY, posGoalZ,
                    partIdx
            );
        }

        MemoryUtil.memFree(buf);
    }

    // ── Step 2: draw-instance index buffer (binding 2) ───────────────────────
    public static void checkDrawInstanceIndexBuffer(int drawInstanceIndexHandle, int expectedCount) {
        System.out.println("\n=== [DEBUG] Draw-Instance Index Buffer (binding 2, post-cull) ===");

        IntBuffer buf = MemoryUtil.memAllocInt(expectedCount);

        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, drawInstanceIndexHandle);
        GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, buf);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

        System.out.printf("  Expected %d surviving instances:%n", expectedCount);
        for (int i = 0; i < expectedCount; i++) {
            System.out.printf("  [%2d] globalIdx = %d%n", i, buf.get(i));
        }

        MemoryUtil.memFree(buf);
    }

    // ── Step 3: finalPartMatrices SSBO (binding 12) ───────────────────────────
    // Reads at the actual globalIdx positions the compute shader wrote to,
    // NOT sequential 0..totalInstances. Each segment group occupies a full page
    // so the stride between segments in globalIdx space is PAGE_SIZE (32).
    public static void checkFinalPartMatrices(int matrixSsboHandle, int armsCount) {
        System.out.println("\n=== [DEBUG] finalPartMatrices (binding 12, post-compute) ===");

        int pagesPerSegment = (armsCount + PAGE_SIZE - 1) / PAGE_SIZE;
        int totalPages      = 4 * pagesPerSegment;
        int totalSlots      = totalPages * PAGE_SIZE; // full addressable range

        FloatBuffer buf = MemoryUtil.memAllocFloat(totalSlots * 16);

        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, matrixSsboHandle);
        GL15C.glGetBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, buf);
        GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, 0);

        String[] segNames = {"base  ", "first ", "second", "item  "};

        for (int seg = 0; seg < 4; seg++) {
            for (int arm = 0; arm < armsCount; arm++) {
                // This mirrors the compute shader's write address: globalIdx = pageIndex * 32 + slotInPage
                // Segment seg occupies pages [seg*pagesPerSegment .. (seg+1)*pagesPerSegment - 1]
                int globalIdx = seg * pagesPerSegment * PAGE_SIZE + arm;
                int base      = globalIdx * 16;

                float m00 = buf.get(base);
                float m11 = buf.get(base + 5);
                float m22 = buf.get(base + 10);
                float tx  = buf.get(base + 12);
                float ty  = buf.get(base + 13);
                float tz  = buf.get(base + 14);
                float tw  = buf.get(base + 15);

                boolean zero = (m00 == 0f && m11 == 0f && m22 == 0f
                        && tx  == 0f && ty  == 0f && tz  == 0f);

                System.out.printf(
                        "  [globalIdx=%3d] arm=%d seg=%s | diag=(%.3f,%.3f,%.3f) translate=(%.3f,%.3f,%.3f,%.3f) %s%n",
                        globalIdx, arm, segNames[seg], m00, m11, m22, tx, ty, tz, tw,
                        zero ? "<-- ZERO: compute never wrote this slot!" : ""
                );
            }
        }

        MemoryUtil.memFree(buf);
    }

    // ── Combined entry point ──────────────────────────────────────────────────
    public static void checkAll(IndirectBuffers buffers, int matrixSsboHandle,
                                int armsCount, boolean verbose) {
        int totalInstances = armsCount * 4;

        System.out.println("\n╔══════════════════════════════════════════════════╗");
        System.out.println("║          ComputeDebugger — frame snapshot        ║");
        System.out.printf ("║  armsCount=%d  totalInstances=%d%n", armsCount, totalInstances);
        System.out.println("╚══════════════════════════════════════════════════╝");

        if (verbose) {
            checkInstanceBuffer(
                    buffers.objectStorage.objectBuffer.handle(),
                    totalInstances
            );
        }

        checkDrawInstanceIndexBuffer(
                buffers.drawInstanceIndex.handle(),
                totalInstances
        );

        checkFinalPartMatrices(matrixSsboHandle, armsCount);

        System.out.println();
    }
}
