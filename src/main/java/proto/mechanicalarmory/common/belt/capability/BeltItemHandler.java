package proto.mechanicalarmory.common.belt.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.belt.network.BeltNetworkData;

import javax.annotation.Nullable;

/**
 * Ephemeral IItemHandler wrapper providing sided hopper/automation access to conveyor belts.
 */
public final class BeltItemHandler implements IItemHandler {

    private final ServerLevel level;
    private final BlockPos pos;
    private final Direction facing;
    @Nullable
    private final Direction side;

    public BeltItemHandler(ServerLevel level, BlockPos pos, Direction facing, @Nullable Direction side) {
        this.level = level;
        this.pos = pos;
        this.facing = facing;
        this.side = side;
    }

    private int mapSlotToLane(int slot) {
        if (side == facing.getCounterClockWise()) {
            return 0; // Left side -> Lane 0
        } else if (side == facing.getClockWise()) {
            return 1; // Right side -> Lane 1
        }
        return slot == 1 ? 1 : 0; // Back, Top, Bottom, Front, Null -> 2 slots (0=left, 1=right)
    }

    @Nullable
    private BeltNode getNode() {
        BeltNetworkData data = BeltNetworkData.get(level);
        return data.nodeAt(pos);
    }

    @Override
    public int getSlots() {
        if (side == facing.getCounterClockWise() || side == facing.getClockWise()) {
            return 1; // 1-slot for side faces
        }
        return 2; // 2-slot for Back, Top, Bottom, Null, Front
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        BeltNode node = getNode();
        if (node == null) return ItemStack.EMPTY;
        int laneIdx = mapSlotToLane(slot);
        BeltLane lane = node.lane(laneIdx);
        ItemGroup front = lane.peekFirst();
        return front != null ? front.item().copyWithCount(front.count()) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (side == facing) return stack; // Cannot insert into front output face

        BeltNode node = getNode();
        if (node == null || node.isStopped()) return stack;

        int laneIdx = mapSlotToLane(slot);
        BeltLane lane = node.lane(laneIdx);

        float spacing = lane.spacing();
        float maxAvailableHead = lane.isEmpty() ? spacing : lane.peekLast().tailPos(spacing);
        if (maxAvailableHead <= 0.0f && !lane.isEmpty()) {
            return stack; // Lane is backed up
        }

        if (!simulate) {
            ItemStack toInsert = stack.copyWithCount(1);
            boolean inserted = lane.insertItem(toInsert);
            if (inserted) {
                BeltNetworkData data = BeltNetworkData.get(level);
                data.setDirty();
                data.sendCorrection(pos, level);
                return stack.copyWithCount(stack.getCount() - 1);
            }
            return stack;
        }

        // Simulated insertion
        return stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;

        BeltNode node = getNode();
        if (node == null) return ItemStack.EMPTY;

        int laneIdx = mapSlotToLane(slot);
        BeltLane lane = node.lane(laneIdx);
        if (lane.isEmpty()) return ItemStack.EMPTY;

        ItemGroup front = lane.peekFirst();
        if (front == null || front.item().isEmpty()) return ItemStack.EMPTY;

        if (simulate) {
            return front.item().copyWithCount(1);
        }

        ItemStack extracted = lane.extractNearest(1.0f);
        if (!extracted.isEmpty()) {
            BeltNetworkData data = BeltNetworkData.get(level);
            data.setDirty();
            data.sendCorrection(pos, level);
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 4; // Max 4 items per lane block
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }
}
