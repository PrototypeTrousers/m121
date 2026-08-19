package proto.mechanicalarmory.common.belt.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A node in the belt graph.  Holds two independent {@link BeltLane}s (left/right
 * relative to the belt's facing direction) and connectivity metadata.
 *
 * <p>All item simulation happens in the lanes.  The node itself is a pure data
 * container; logic lives in {@code BeltNetworkTick}.
 */
public final class BeltNode {

    // ── Identity ──────────────────────────────────────────────────────────────

    private final BlockPos pos;

    // ── Lanes ─────────────────────────────────────────────────────────────────

    /** lane[0] = left, lane[1] = right (relative to belt facing). */
    private final BeltLane[] lanes = new BeltLane[]{
            BeltLane.standard(),
            BeltLane.standard()
    };

    // ── Connectivity ──────────────────────────────────────────────────────────

    /** BlockPos of the output neighbour node, or null if this is a terminal. */
    @Nullable
    private BlockPos outputPos;

    /**
     * Input neighbour positions, keyed to the lane they feed. Up to 2 inputs — one
     * per side, Factorio-style side-loading: each upstream belt is assigned
     * exclusively to lane 0 (left) or lane 1 (right) of this node and only
     * ever reads/writes that lane. A lane with no assigned input is simply
     * never fed by a merge (it can still receive items directly if this node
     * itself is an input side of a straight belt, i.e. lane parity from the
     * belt's own two lanes).
     *
     * <p>Insertion order is preserved for iteration but is no longer load-bearing
     * for lane assignment — see {@link #laneForInput(BlockPos)}.
     */
    private final Map<BlockPos, Integer> inputLanes = new LinkedHashMap<>(2);

    /**
     * True when this node is the wrap-point of a loop.  Its "output" back into
     * the loop is handled by {@link BeltLane#applyWrap()} instead of a real
     * transfer, so it has no {@code outputPos}.  It is treated as topological
     * layer 0 (processed first in the tick).
     */
    private boolean isWrapPoint;

    /** Whether the belt is stopped (e.g. powered by redstone). */
    private boolean stopped;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltNode(BlockPos pos) {
        this.pos = pos;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public BlockPos pos()        { return pos; }
    public BeltLane lane(int i)  { return lanes[i]; }
    public BeltLane[] lanes()    { return lanes; }

    @Nullable public BlockPos outputPos() { return outputPos; }

    /** Snapshot of currently-connected input positions (order not meaningful). */
    public List<BlockPos> inputPositions() { return new ArrayList<>(inputLanes.keySet()); }

    /**
     * Which lane (0 or 1) the given input position is side-loaded onto, or -1 if
     * {@code inputPos} is not a registered input of this node.
     */
    public int laneForInput(BlockPos inputPos) {
        return inputLanes.getOrDefault(inputPos, -1);
    }

    /** True if the given lane already has an input assigned to it. */
    public boolean laneOccupied(int lane) {
        return inputLanes.containsValue(lane);
    }

    public boolean isWrapPoint() { return isWrapPoint; }
    public boolean isStopped()   { return stopped; }

    public void setOutputPos(@Nullable BlockPos pos) { outputPos = pos; }
    public void setWrapPoint(boolean wp)             { isWrapPoint = wp; }
    public void setStopped(boolean s)                { stopped = s; }

    /**
     * Register {@code inputPos} as an input of this node, side-loaded onto the
     * first free lane (0 then 1). Factorio-style merging: each input is
     * exclusively assigned to one lane and only that lane's contents are ever
     * visible to or written by that upstream node during a tick — this is what
     * keeps concurrent ticking of same-layer nodes race-free at merge points.
     *
     * @return the lane index (0 or 1) the input was assigned to, or -1 if this
     *         node already has an input on {@code inputPos} (idempotent, existing
     *         lane returned) or if both lanes are already occupied by other
     *         inputs (merge point full — caller should not create the edge).
     */
    public int addInput(BlockPos inputPos) {
        Integer existing = inputLanes.get(inputPos);
        if (existing != null) return existing;
        if (!laneOccupied(0)) {
            inputLanes.put(inputPos, 0);
            return 0;
        }
        if (!laneOccupied(1)) {
            inputLanes.put(inputPos, 1);
            return 1;
        }
        return -1; // both lanes taken
    }

    public void removeInput(BlockPos inputPos) { inputLanes.remove(inputPos); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.put("lane0", lanes[0].save(registries));
        tag.put("lane1", lanes[1].save(registries));
        if (outputPos != null) tag.put("outputPos", NbtUtils.writeBlockPos(outputPos));
        tag.putBoolean("wrapPoint", isWrapPoint);
        tag.putBoolean("stopped", stopped);

        ListTag inputs = new ListTag();
        for (Map.Entry<BlockPos, Integer> e : inputLanes.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.put("v", NbtUtils.writeBlockPos(e.getKey()));
            c.putInt("lane", e.getValue());
            inputs.add(c);
        }
        tag.put("inputs", inputs);
        return tag;
    }

    public static BeltNode load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO);
        BeltNode node = new BeltNode(pos);
        BeltLane l0 = BeltLane.load(tag.getCompound("lane0"), registries);
        BeltLane l1 = BeltLane.load(tag.getCompound("lane1"), registries);
        // Replace default lanes
        node.lanes[0] = l0;
        node.lanes[1] = l1;

        if (tag.contains("outputPos")) {
            node.outputPos = NbtUtils.readBlockPos(tag, "outputPos").orElse(null);
        }
        node.isWrapPoint = tag.getBoolean("wrapPoint");
        node.stopped = tag.getBoolean("stopped");

        ListTag inputs = tag.getList("inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputs.size(); i++) {
            CompoundTag c = inputs.getCompound(i);
            BlockPos inputPos = NbtUtils.readBlockPos(c, "v").orElse(null);
            if (inputPos != null) {
                node.inputLanes.put(inputPos, c.getInt("lane"));
            }
        }
        return node;
    }
}
