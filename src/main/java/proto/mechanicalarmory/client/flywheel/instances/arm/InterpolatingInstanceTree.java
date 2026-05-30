package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public class InterpolatingInstanceTree {
    /**
     * Global DFS counter — every node across every arm gets a unique partIdx.
     * Never decremented; deleted arms leave gaps (fine for a game mod).
     */
    private static final AtomicInteger GLOBAL_COUNTER = new AtomicInteger(0);

    /**
     * Number of live Flywheel mesh instances across all arms.
     * Mesh-only (instance != null nodes). Used by the dispatch mixin as the
     * compute shader thread count — must equal the number of Flywheel instances.
     */
    public static final AtomicInteger TOTAL_MESH_INSTANCES = new AtomicInteger(0);

    private final ModelTree source;
    @Nullable
    private final InterpolatedInstance instance;
    private final InterpolatingInstanceTree[] children;

    /** Globally unique DFS index for this node. */
    public final int partIdx;

    // ── Local transform keyframes (set by caller each tick) ──
    // These are LOCAL (relative to parent). cascadeToBuffer() chains them into
    // world-space before writing to PartTransformBuffer.
    public final Vector3f    posFrom   = new Vector3f();
    public final Quaternionf rotFrom   = new Quaternionf();
    public final Vector3f    scaleFrom = new Vector3f(1.0f, 1.0f, 1.0f);

    public final Vector3f    posGoal   = new Vector3f();
    public final Quaternionf rotGoal   = new Quaternionf();
    public final Vector3f    scaleGoal = new Vector3f(1.0f, 1.0f, 1.0f);

    private boolean visible  = true;
    private boolean skipDraw = false;

    private InterpolatingInstanceTree(ModelTree source, @Nullable InterpolatedInstance instance,
                                      InterpolatingInstanceTree[] children, int partIdx) {
        this.source   = source;
        this.instance = instance;
        this.children = children;
        this.partIdx  = partIdx;
    }

    /**
     * Builds the full tree with globally-unique partIdx values.
     * Returns the root; call {@link #totalParts()} on root for buffer sizing.
     */
    public static InterpolatingInstanceTree create(InstancerProvider provider, ModelTree meshTree) {
        return create(provider, meshTree, -1);
    }

    private static InterpolatingInstanceTree create(InstancerProvider provider, ModelTree meshTree,
                                                    int parentPartIdx) {
        int currentIdx = GLOBAL_COUNTER.getAndIncrement();

        Model model = meshTree.model();
        InterpolatedInstance instance;
        if (model != null) {
            instance = provider.instancer(InterpolatingInstancetype.INTERPOLATED, model)
                    .createInstance();
            instance.parentIdx = parentPartIdx;
            instance.partIdx   = currentIdx;
            instance.model     = model;
            TOTAL_MESH_INSTANCES.incrementAndGet();
        } else {
            instance = null;
        }

        InterpolatingInstanceTree[] children = new InterpolatingInstanceTree[meshTree.childCount()];
        for (int i = 0; i < meshTree.childCount(); i++) {
            children[i] = create(provider, meshTree.child(i), currentIdx);
        }
        return new InterpolatingInstanceTree(meshTree, instance, children, currentIdx);
    }

    /**
     * Total node count in this subtree (including non-mesh nodes).
     * On root: equals the number of partIdx slots this arm occupies.
     */
    public int totalParts() {
        int n = 1;
        for (InterpolatingInstanceTree c : children) n += c.totalParts();
        return n;
    }

    // ── Transform upload ─────────────────────────────────────────────────────

    /**
     * Recursively computes world-space transforms by chaining parent TRS,
     * then writes each node's two keyframes into {@code buf} at {@link #partIdx}.
     * Does NOT modify the local posFrom/posGoal fields.
     * Safe on any thread (only touches the CPU-side ByteBuffer in buf).
     */
    public void cascadeToBuffer(PartTransformBuffer buf) {
        cascadeToBuffer(buf,
                new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1),
                new Vector3f(), new Quaternionf(), new Vector3f(1, 1, 1));
    }

    private void cascadeToBuffer(PartTransformBuffer buf,
                                  Vector3f pFromPos, Quaternionf pFromRot, Vector3f pFromScale,
                                  Vector3f pGoalPos, Quaternionf pGoalRot, Vector3f pGoalScale) {
        // World 'from' = parent * local
        Vector3f    wFromPos   = pFromRot.transform(new Vector3f(posFrom).mul(pFromScale)).add(pFromPos);
        Quaternionf wFromRot   = new Quaternionf(pFromRot).mul(rotFrom);
        Vector3f    wFromScale = new Vector3f(pFromScale).mul(scaleFrom);

        // World 'goal' = parent * local
        Vector3f    wGoalPos   = pGoalRot.transform(new Vector3f(posGoal).mul(pGoalScale)).add(pGoalPos);
        Quaternionf wGoalRot   = new Quaternionf(pGoalRot).mul(rotGoal);
        Vector3f    wGoalScale = new Vector3f(pGoalScale).mul(scaleGoal);

        // Write world-space keyframes for every node — mesh and non-mesh alike.
        // The compute shader processes all partIdx slots (gid == partIdx).
        // Non-mesh slots produce a finalPartMatrices entry that no vertex ever reads.
        buf.writeAt(partIdx, wFromPos, wFromRot, wFromScale, wGoalPos, wGoalRot, wGoalScale);

        for (InterpolatingInstanceTree child : children) {
            child.cascadeToBuffer(buf,
                    wFromPos, wFromRot, wFromScale,
                    wGoalPos, wGoalRot, wGoalScale);
        }
    }

    // ── Instance lifecycle ───────────────────────────────────────────────────

    @Nullable
    public InterpolatedInstance instance() { return instance; }

    public void setChanged() {
        if (instance != null) instance.setChanged();
        for (InterpolatingInstanceTree c : children) c.setChanged();
    }

    /** Marks all GPU instances changed so Flywheel re-uploads the small struct. */
    public void cascadeWorldTransforms() {
        if (instance != null) instance.setChanged();
        for (InterpolatingInstanceTree child : children) child.cascadeWorldTransforms();
    }

    // ── Tree navigation ──────────────────────────────────────────────────────

    public PartPose initialPose()                      { return source.initialPose(); }
    public int      childCount()                       { return children.length; }
    public InterpolatingInstanceTree child(int index)  { return children[index]; }
    public String   childName(int index)               { return source.childName(index); }
    public int      childIndex(String name)            { return source.childIndex(name); }
    public boolean  hasChild(String name)              { return childIndex(name) >= 0; }

    @Nullable
    public InterpolatingInstanceTree child(String name) {
        int i = childIndex(name);
        return i < 0 ? null : child(i);
    }

    public InterpolatingInstanceTree childOrThrow(String name) {
        InterpolatingInstanceTree c = child(name);
        if (c == null) throw new NoSuchElementException("Can't find part " + name);
        return c;
    }

    // ── Visibility ───────────────────────────────────────────────────────────

    public void visible(boolean visible) {
        this.visible = visible;
        updateVisible();
        for (InterpolatingInstanceTree c : children) c.visible(visible);
    }

    public void skipDraw(boolean skipDraw) {
        this.skipDraw = skipDraw;
        updateVisible();
    }

    private void updateVisible() {
        if (instance != null) instance.setVisible(visible && !skipDraw);
    }

    public boolean visible()  { return visible; }
    public boolean skipDraw() { return skipDraw; }

    // ── Deletion ─────────────────────────────────────────────────────────────

    public void delete() {
        if (instance != null) {
            instance.delete();
            TOTAL_MESH_INSTANCES.decrementAndGet();
        }
        for (InterpolatingInstanceTree c : children) c.delete();
    }

    // ── Traversal ────────────────────────────────────────────────────────────

    public void traverse(Consumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance);
        for (InterpolatingInstanceTree c : children) c.traverse(consumer);
    }

    @ApiStatus.Experimental
    public void traverse(int i, ObjIntConsumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i);
        for (InterpolatingInstanceTree c : children) c.traverse(i, consumer);
    }

    @ApiStatus.Experimental
    public void traverse(int i, int j, ObjIntIntConsumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i, j);
        for (InterpolatingInstanceTree c : children) c.traverse(i, j, consumer);
    }

    @ApiStatus.Experimental
    @FunctionalInterface
    public interface ObjIntIntConsumer<T> {
        void accept(T t, int i, int j);
    }
}