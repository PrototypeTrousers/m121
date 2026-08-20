package proto.mechanicalarmory.common.belt.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A node in the belt graph. Holds two independent {@link BeltLane}s (left/right
 * relative to the belt's facing direction) and connectivity metadata.
 *
 * <p>All item simulation happens in the lanes. The node itself is a pure data
 * container; logic lives in {@code BeltSimulation}.
 */
public final class BeltNode {

    // ── Identity ──────────────────────────────────────────────────────────────

    private final BlockPos pos;
    private Direction facing = Direction.NORTH;

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
     * Connected input neighbour positions.
     */
    private final List<BlockPos> inputPositions = new ObjectArrayList<>(3);
    private boolean stopped;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltNode(BlockPos pos) {
        this.pos = pos;
    }

    public BeltNode(BlockPos pos, Direction facing) {
        this.pos = pos;
        this.facing = facing != null ? facing : Direction.NORTH;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public BlockPos pos()        { return pos; }
    public Direction facing()    { return facing; }
    public void setFacing(Direction facing) { this.facing = facing != null ? facing : Direction.NORTH; }

    public BeltLane lane(int i)  { return lanes[i]; }
    public BeltLane[] lanes()    { return lanes; }

    @Nullable public BlockPos outputPos() { return outputPos; }

    /** Snapshot of currently-connected input positions. */
    public List<BlockPos> inputPositions() { return inputPositions; }

    public int inputCount() { return inputPositions.size(); }

    public boolean isStopped()   { return stopped; }

    public void setOutputPos(@Nullable BlockPos pos) { outputPos = pos; }
    public void setStopped(boolean s)                { stopped = s; }

    public boolean addInput(BlockPos inputPos) {
        if (!inputPositions.contains(inputPos)) {
            inputPositions.add(inputPos);
            return true;
        }
        return false;
    }

    public void removeInput(BlockPos inputPos) {
        inputPositions.remove(inputPos);
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BeltNode beltNode = (BeltNode) o;
        return Objects.equals(pos, beltNode.pos);
    }

    @Override
    public int hashCode() {
        return pos != null ? pos.hashCode() : 0;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.putByte("facing", (byte) facing.get3DDataValue());
        tag.put("lane0", lanes[0].save(registries));
        tag.put("lane1", lanes[1].save(registries));
        if (outputPos != null) tag.put("outputPos", NbtUtils.writeBlockPos(outputPos));
        tag.putBoolean("stopped", stopped);

        ListTag inputs = new ListTag();
        for (BlockPos inPos : inputPositions) {
            CompoundTag c = new CompoundTag();
            c.put("v", NbtUtils.writeBlockPos(inPos));
            inputs.add(c);
        }
        tag.put("inputs", inputs);
        return tag;
    }

    public static BeltNode load(CompoundTag tag, HolderLookup.Provider registries) {
        BlockPos pos = NbtUtils.readBlockPos(tag, "pos").orElse(BlockPos.ZERO);
        BeltNode node = new BeltNode(pos);
        if (tag.contains("facing", Tag.TAG_BYTE) || tag.contains("facing", Tag.TAG_INT)) {
            node.facing = Direction.from3DDataValue(tag.getByte("facing"));
            if (node.facing.getAxis().isVertical()) {
                node.facing = Direction.NORTH;
            }
        }
        BeltLane l0 = BeltLane.load(tag.getCompound("lane0"), registries);
        BeltLane l1 = BeltLane.load(tag.getCompound("lane1"), registries);
        node.lanes[0] = l0;
        node.lanes[1] = l1;

        if (tag.contains("outputPos")) {
            node.outputPos = NbtUtils.readBlockPos(tag, "outputPos").orElse(null);
        }
        node.stopped = tag.getBoolean("stopped");

        ListTag inputs = tag.getList("inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputs.size(); i++) {
            CompoundTag c = inputs.getCompound(i);
            BlockPos inputPos = NbtUtils.readBlockPos(c, "v").orElse(null);
            if (inputPos != null && !node.inputPositions.contains(inputPos)) {
                node.inputPositions.add(inputPos);
            }
        }
        return node;
    }
}
