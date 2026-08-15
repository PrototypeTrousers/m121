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
import java.util.List;
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
     * Input neighbour UUIDs.  Up to 2 inputs (one per merge side).
     * Belt lane assignment: inputIds.get(0) feeds lane 0, .get(1) feeds lane 1.
     */
    private final List<UUID> inputIds = new ArrayList<>(2);

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
    public List<UUID>    inputIds()  { return inputIds; }

    public boolean isWrapPoint() { return isWrapPoint; }
    public boolean isStopped()   { return stopped; }

    public void setOutputId(@Nullable UUID id) { outputId = id; }
    public void setWrapPoint(boolean wp)       { isWrapPoint = wp; }
    public void setStopped(boolean s)          { stopped = s; }

    public void addInput(UUID id) {
        if (!inputIds.contains(id) && inputIds.size() < 2) {
            inputIds.add(id);
        }
    }
    public void removeInput(UUID id) { inputIds.remove(id); }

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
        for (UUID id : inputIds) {
            CompoundTag c = new CompoundTag();
            c.putUUID("v", id);
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
            node.inputIds.add(inputs.getCompound(i).getUUID("v"));
        }
        return node;
    }

    // Expose lanes array for load workaround
    BeltLane[] lanesArray() { return lanes; }
}
