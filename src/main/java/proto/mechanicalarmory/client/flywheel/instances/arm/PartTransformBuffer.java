package proto.mechanicalarmory.client.flywheel.instances.arm;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.lwjgl.opengl.GL15C.*;
import static org.lwjgl.opengl.GL30C.glBindBufferBase;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;

/**
 * Global flat SSBO — one {@code PartTransform} per globally-unique {@link #partIdx}.
 *
 * <p><b>Thread model</b>:
 * <ul>
 *   <li>{@link #writeAt} — any thread (task threads). Writes into an off-heap
 *       {@link ByteBuffer}. Sets {@link #dirty} flag.</li>
 *   <li>{@link #uploadIfDirty} / {@link #bindTo} — render thread only.</li>
 *   <li>{@link #delete} — render thread only.</li>
 * </ul>
 *
 * <p>Layout per entry (std430, {@value #BYTES_PER_PART} bytes):
 * <pre>
 *   offset  0 : vec3  posFrom    (12 bytes)
 *   offset 12 : float _pad0      ( 4 bytes)
 *   offset 16 : vec4  rotFrom    (16 bytes)
 *   offset 32 : vec3  scaleFrom  (12 bytes)
 *   offset 44 : float _pad1      ( 4 bytes)
 *   offset 48 : vec3  posGoal    (12 bytes)
 *   offset 60 : float _pad2      ( 4 bytes)
 *   offset 64 : vec4  rotGoal    (16 bytes)
 *   offset 80 : vec3  scaleGoal  (12 bytes)
 *   offset 92 : float _pad3      ( 4 bytes)
 * </pre>
 */
public class PartTransformBuffer {

    public static final int BYTES_PER_PART = 96;

    // ── Singleton ────────────────────────────────────────────────────────────

    private static volatile PartTransformBuffer INSTANCE;

    public static PartTransformBuffer get() {
        PartTransformBuffer b = INSTANCE;
        if (b == null) {
            synchronized (PartTransformBuffer.class) {
                b = INSTANCE;
                if (b == null) {
                    b = new PartTransformBuffer(64);
                    INSTANCE = b;
                }
            }
        }
        return b;
    }

    public static void destroyGlobal() {
        RenderSystem.assertOnRenderThread();
        PartTransformBuffer b = INSTANCE;
        if (b != null) {
            INSTANCE = null;
            b.delete();
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    /** Highest partIdx written so far — used by mixin to size finalPartMatrices. */
    private final AtomicInteger highWaterMark = new AtomicInteger(0);

    private volatile ByteBuffer cpuBuffer;
    private volatile int        cpuCapacity; // in parts

    /** GL handle for the TransformBuffer SSBO. 0 until first render-thread use. */
    private int glHandle;

    private final AtomicBoolean dirty     = new AtomicBoolean(false);
    private final AtomicBoolean needsGrow = new AtomicBoolean(false);

    // ── Construction ─────────────────────────────────────────────────────────

    private PartTransformBuffer(int initialPartCount) {
        this.cpuCapacity = initialPartCount;
        this.cpuBuffer   = MemoryUtil.memCalloc(initialPartCount * BYTES_PER_PART);
        this.glHandle    = 0;
    }

    // ── CPU-side writes (any thread) ─────────────────────────────────────────

    /**
     * Writes world-space from/goal keyframes for {@code partIdx} into the CPU buffer.
     * Grows the buffer automatically if needed. Safe on task threads.
     */
    public void writeAt(int partIdx,
                        Vector3f fromPos, Quaternionf fromRot, Vector3f fromScale,
                        Vector3f goalPos, Quaternionf goalRot, Vector3f goalScale) {
        ensureCpuCapacity(partIdx + 1);
        highWaterMark.updateAndGet(cur -> Math.max(cur, partIdx));

        int base = partIdx * BYTES_PER_PART;
        ByteBuffer buf = cpuBuffer;

        // posFrom (offset 0)
        buf.putFloat(base +  0, fromPos.x);
        buf.putFloat(base +  4, fromPos.y);
        buf.putFloat(base +  8, fromPos.z);
        // _pad0 (offset 12)

        // rotFrom (offset 16)
        buf.putFloat(base + 16, fromRot.x);
        buf.putFloat(base + 20, fromRot.y);
        buf.putFloat(base + 24, fromRot.z);
        buf.putFloat(base + 28, fromRot.w);

        // scaleFrom (offset 32)
        buf.putFloat(base + 32, fromScale.x);
        buf.putFloat(base + 36, fromScale.y);
        buf.putFloat(base + 40, fromScale.z);
        // _pad1 (offset 44)

        // posGoal (offset 48)
        buf.putFloat(base + 48, goalPos.x);
        buf.putFloat(base + 52, goalPos.y);
        buf.putFloat(base + 56, goalPos.z);
        // _pad2 (offset 60)

        // rotGoal (offset 64)
        buf.putFloat(base + 64, goalRot.x);
        buf.putFloat(base + 68, goalRot.y);
        buf.putFloat(base + 72, goalRot.z);
        buf.putFloat(base + 76, goalRot.w);

        // scaleGoal (offset 80)
        buf.putFloat(base + 80, goalScale.x);
        buf.putFloat(base + 84, goalScale.y);
        buf.putFloat(base + 88, goalScale.z);
        // _pad3 (offset 92)

        dirty.set(true);
    }

    private synchronized void ensureCpuCapacity(int requiredParts) {
        if (requiredParts <= cpuCapacity) return;
        int newCap = Math.max(requiredParts, cpuCapacity * 2);
        ByteBuffer old = cpuBuffer;
        ByteBuffer next = MemoryUtil.memCalloc(newCap * BYTES_PER_PART);
        old.rewind();
        next.put(old); // copy existing data
        next.rewind();
        cpuBuffer   = next;
        cpuCapacity = newCap;
        MemoryUtil.memFree(old);
        needsGrow.set(true); // tell render thread to re-allocate GL buffer
    }

    // ── Render-thread GL operations ──────────────────────────────────────────

    /**
     * Creates the GL buffer if absent/grown, uploads dirty CPU data.
     * Must be called on the render thread (before dispatch).
     */
    public void uploadIfDirty() {
        RenderSystem.assertOnRenderThread();

        boolean grow = needsGrow.compareAndSet(true, false);

        if (glHandle == 0 || grow) {
            if (glHandle != 0) glDeleteBuffers(glHandle);
            glHandle = glGenBuffers();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, glHandle);
            glBufferData(GL_SHADER_STORAGE_BUFFER, (long) cpuCapacity * BYTES_PER_PART, GL_DYNAMIC_DRAW);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
            dirty.set(true); // force full upload into freshly allocated buffer
        }

        if (dirty.compareAndSet(true, false)) {
            ByteBuffer buf = cpuBuffer;
            buf.rewind();
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, glHandle);
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, buf);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
        }
    }

    /** Binds to the given SSBO binding point. Render thread only. */
    public void bindTo(int binding) {
        if (glHandle != 0) {
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, binding, glHandle);
        }
    }

    /** Highest partIdx that has been written — use to size finalPartMatrices. */
    public int highWaterMark() {
        return highWaterMark.get();
    }

    /** Render thread only. */
    public void delete() {
        RenderSystem.assertOnRenderThread();
        if (glHandle != 0) {
            glDeleteBuffers(glHandle);
            glHandle = 0;
        }
        ByteBuffer buf = cpuBuffer;
        if (buf != null) {
            MemoryUtil.memFree(buf);
            cpuBuffer = null;
        }
    }
}
