package proto.mechanicalarmory.common.belt.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A node in the belt graph.  Holds two independent {@link BeltLane}s (left/right
 * relative to the belt's facing direction) and connectivity metadata.
 *
 * <p>All item simulation happens in the lanes.  The node itself is a pure data
 * container; logic lives in {@code BeltNetworkTick}.
 */
public final class BeltNode {

    // ── Identity ──────────────────────────────────────────────────────────────

    private final UUID nodeId;
    private final BlockPos pos;

    // ── Lanes ─────────────────────────────────────────────────────────────────

    /** lane[0] = left, lane[1] = right (relative to belt facing). */
    private final BeltLane[] lanes = new BeltLane[]{
            BeltLane.standard(),
            BeltLane.standard()
    };

    // ── Connectivity ──────────────────────────────────────────────────────────

    /** UUID of the output neighbour node, or null if this is a terminal. */
    @Nullable
    private UUID outputId;

    /**
     * Input neighbour UUIDs, keyed to the lane they feed. Up to 2 inputs — one
     * per side, Factorio-style side-loading: each upstream belt is assigned
     * exclusively to lane 0 (left) or lane 1 (right) of this node and only
     * ever reads/writes that lane. A lane with no assigned input is simply
     * never fed by a merge (it can still receive items directly if this node
     * itself is an input side of a straight belt, i.e. lane parity from the
     * belt's own two lanes).
     *
     * <p>Insertion order is preserved for iteration but is no longer load-bearing
     * for lane assignment — see {@link #laneForInput(UUID)}.
     */
    private final Map<UUID, Integer> inputLanes = new LinkedHashMap<>(2);

    /**
     * True when this node is the wrap-point of a loop.  Its "output" back into
     * the loop is handled by {@link BeltLane#applyWrap()} instead of a real
     * transfer, so it has no {@code outputId}.  It is treated as topological
     * layer 0 (processed first in the tick).
     */
    private boolean isWrapPoint;

    /** Whether the belt is stopped (e.g. powered by redstone). */
    private boolean stopped;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Deterministic UUID derived from a block position.
     * Because only one belt can ever occupy a given block, this is a stable,
     * collision-free identity shared by both server and client without any sync.
     */
    public static UUID posToId(BlockPos pos) {
        ByteBuffer buf = ByteBuffer.allocate(12);
        buf.putInt(pos.getX());
        buf.putInt(pos.getY());
        buf.putInt(pos.getZ());
        return UUID.nameUUIDFromBytes(buf.array());
    }

    /** Create a node whose UUID is derived purely from its position. */
    public BeltNode(BlockPos pos) {
        this.nodeId = posToId(pos);
        this.pos    = pos;
    }

    /** Full constructor kept for deserialization from older NBT. */
    public BeltNode(UUID nodeId, BlockPos pos) {
        this.nodeId = nodeId;
        this.pos = pos;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public UUID nodeId()         { return nodeId; }
    public BlockPos pos()        { return pos; }
    public BeltLane lane(int i)  { return lanes[i]; }
    public BeltLane[] lanes()    { return lanes; }

    @Nullable public UUID outputId() { return outputId; }

    /** Snapshot of currently-connected input UUIDs (order not meaningful). */
    public List<UUID> inputIds() { return new ArrayList<>(inputLanes.keySet()); }

    /**
     * Which lane (0 or 1) the given input UUID is side-loaded onto, or -1 if
     * {@code inputId} is not a registered input of this node.
     */
    public int laneForInput(UUID inputId) {
        return inputLanes.getOrDefault(inputId, -1);
    }

    /** True if the given lane already has an input assigned to it. */
    public boolean laneOccupied(int lane) {
        return inputLanes.containsValue(lane);
    }

    public boolean isWrapPoint() { return isWrapPoint; }
    public boolean isStopped()   { return stopped; }

    public void setOutputId(@Nullable UUID id) { outputId = id; }
    public void setWrapPoint(boolean wp)       { isWrapPoint = wp; }
    public void setStopped(boolean s)          { stopped = s; }

    /**
     * Register {@code id} as an input of this node, side-loaded onto the first
     * free lane (0 then 1). Factorio-style merging: each input is exclusively
     * assigned to one lane and only that lane's contents are ever visible to
     * or written by that upstream node during a tick — this is what keeps
     * concurrent ticking of same-layer nodes race-free at merge points.
     *
     * @return the lane index (0 or 1) the input was assigned to, or -1 if this
     *         node already has an input on {@code id} (idempotent, existing
     *         lane returned) or if both lanes are already occupied by other
     *         inputs (merge point full — caller should not create the edge).
     */
    public int addInput(UUID id) {
        Integer existing = inputLanes.get(id);
        if (existing != null) return existing;
        if (!laneOccupied(0)) {
            inputLanes.put(id, 0);
            return 0;
        }
        if (!laneOccupied(1)) {
            inputLanes.put(id, 1);
            return 1;
        }
        return -1; // both lanes taken
    }

    public void removeInput(UUID id) { inputLanes.remove(id); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", nodeId);
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.put("lane0", lanes[0].save(registries));
        tag.put("lane1", lanes[1].save(registries));
        if (outputId != null) tag.putUUID("outputId", outputId);
        tag.putBoolean("wrapPoint", isWrapPoint);
        tag.putBoolean("stopped", stopped);

        ListTag inputs = new ListTag();
        for (Map.Entry<UUID, Integer> e : inputLanes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("v", e.getKey());
            c.putInt("lane", e.getValue());
            inputs.add(c);
        }
        tag.put("inputs", inputs);
        return tag;
    }

    public static BeltNode load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO);
        // Derive the UUID from position; ignore any stored "id" so old and new
        // saves are handled uniformly and always agree with the client.
        BeltNode node = new BeltNode(pos);
        BeltLane l0 = BeltLane.load(tag.getCompound("lane0"), registries);
        BeltLane l1 = BeltLane.load(tag.getCompound("lane1"), registries);
        // Replace default lanes
        node.lanes[0] = l0;
        node.lanes[1] = l1;

        if (tag.contains("outputId")) node.outputId = tag.getUUID("outputId");
        node.isWrapPoint = tag.getBoolean("wrapPoint");
        node.stopped = tag.getBoolean("stopped");

        ListTag inputs = tag.getList("inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputs.size(); i++) {
            CompoundTag c = inputs.getCompound(i);
            UUID inputId = c.getUUID("v");
            node.inputLanes.put(inputId, c.getInt("lane"));
        }
        return node;
    }
}
