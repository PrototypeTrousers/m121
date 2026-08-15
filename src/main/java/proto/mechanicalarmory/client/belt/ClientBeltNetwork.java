package proto.mechanicalarmory.client.belt;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.network.BeltSubnetwork;
import proto.mechanicalarmory.common.blocks.BlockBelt;

import java.util.*;

/**
 * Client-side belt network manager.
 *
 * <p>Tracks all active {@link BeltSubnetwork}s on the client and registers them
 * with Flywheel's {@link VisualizationManager#effects()} so they are rendered
 * by {@link proto.mechanicalarmory.client.flywheel.instances.belt.BeltSubnetworkVisual}.
 */
public final class ClientBeltNetwork {

    private static final ClientBeltNetwork INSTANCE = new ClientBeltNetwork();

    public static ClientBeltNetwork get() {
        return INSTANCE;
    }

    private final Map<UUID, BeltSubnetwork> subnetworks = new HashMap<>();
    private final Map<BlockPos, UUID> posToSubnet = new HashMap<>();

    private ClientBeltNetwork() {}

    /**
     * Called when a BeltInitPayload or BeltCorrectionPayload arrives from the server.
     */
    public synchronized void updateNode(BlockPos pos, BeltLane lane0, BeltLane lane1,
                                        boolean stopped, boolean wrapPoint, boolean hasOutput) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) return;
        Direction facing = state.getValue(BlockBelt.FACING);

        UUID existingSubId = posToSubnet.get(pos);
        BeltSubnetwork subnet;
        BeltNode node;

        if (existingSubId == null) {
            UUID subId = UUID.randomUUID();
            subnet = new BeltSubnetwork(subId);
            subnet.setLevel(level);
            UUID nodeId = UUID.nameUUIDFromBytes(pos.toString().getBytes());
            node = new BeltNode(nodeId, pos);
            subnet.addNode(node);
            subnetworks.put(subId, subnet);
            posToSubnet.put(pos, subId);

            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) {
                vm.effects().queueAdd(subnet);
            }
        } else {
            subnet = subnetworks.get(existingSubId);
            if (subnet == null) {
                subnet = new BeltSubnetwork(existingSubId);
                subnet.setLevel(level);
                subnetworks.put(existingSubId, subnet);
                VisualizationManager vm = VisualizationManager.get(level);
                if (vm != null) vm.effects().queueAdd(subnet);
            }
            node = subnet.nodeAt(pos);
            if (node == null) {
                UUID nodeId = UUID.nameUUIDFromBytes(pos.toString().getBytes());
                node = new BeltNode(nodeId, pos);
                subnet.addNode(node);
            }
        }

        // Copy lane contents
        node.lane(0).groups().clear();
        for (proto.mechanicalarmory.common.belt.data.ItemGroup g : lane0.groups()) {
            node.lane(0).insertBack(new proto.mechanicalarmory.common.belt.data.ItemGroup(g.item(), g.count(), g.headPos()));
        }
        node.lane(0).setSpeed(lane0.speed());

        node.lane(1).groups().clear();
        for (proto.mechanicalarmory.common.belt.data.ItemGroup g : lane1.groups()) {
            node.lane(1).insertBack(new proto.mechanicalarmory.common.belt.data.ItemGroup(g.item(), g.count(), g.headPos()));
        }
        node.lane(1).setSpeed(lane1.speed());

        node.setStopped(stopped);

        // Relink horizontal neighbours
        relink(pos, facing, level);

        VisualizationManager vm = VisualizationManager.get(level);
        if (vm != null) {
            vm.effects().queueUpdate(subnet);
        }
    }

    private void relink(BlockPos pos, Direction facing, Level level) {
        BlockPos outPos = pos.relative(facing);
        if (posToSubnet.containsKey(outPos)) {
            mergeAndLink(pos, outPos, level);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == facing) continue;
            BlockPos inPos = pos.relative(d);
            BlockState inState = level.getBlockState(inPos);
            if (inState.getBlock() instanceof BlockBelt) {
                Direction inFacing = inState.getValue(BlockBelt.FACING);
                if (inFacing == d.getOpposite() && posToSubnet.containsKey(inPos)) {
                    mergeAndLink(inPos, pos, level);
                }
            }
        }
    }

    private void mergeAndLink(BlockPos fromPos, BlockPos toPos, Level level) {
        UUID fromSubId = posToSubnet.get(fromPos);
        UUID toSubId = posToSubnet.get(toPos);
        if (fromSubId == null || toSubId == null) return;

        BeltSubnetwork fromSub = subnetworks.get(fromSubId);
        BeltSubnetwork toSub = subnetworks.get(toSubId);
        if (fromSub == null || toSub == null) return;

        if (!fromSubId.equals(toSubId)) {
            // Merge toSub into fromSub
            for (BeltNode n : toSub.allNodes()) {
                fromSub.addNode(n);
                posToSubnet.put(n.pos(), fromSub.subnetId());
            }
            subnetworks.remove(toSubId);
            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) {
                vm.effects().queueRemove(toSub);
            }
        }

        fromSub.link(fromPos, toPos);
        VisualizationManager vm = VisualizationManager.get(level);
        if (vm != null) {
            vm.effects().queueUpdate(fromSub);
        }
    }

    /**
     * Remove a node when its block is broken or chunk unloaded.
     */
    public synchronized void removeNode(BlockPos pos) {
        UUID subnetId = posToSubnet.remove(pos);
        if (subnetId == null) return;

        BeltSubnetwork subnet = subnetworks.get(subnetId);
        if (subnet == null) return;

        BeltNode node = subnet.nodeAt(pos);
        if (node != null) {
            subnet.removeNode(node.nodeId());
        }

        Level level = Minecraft.getInstance().level;
        if (subnet.isEmpty()) {
            subnetworks.remove(subnetId);
            if (level != null) {
                VisualizationManager vm = VisualizationManager.get(level);
                if (vm != null) {
                    vm.effects().queueRemove(subnet);
                }
            }
        } else if (level != null) {
            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) {
                vm.effects().queueUpdate(subnet);
            }
        }
    }

    /**
     * Clear on level unload or disconnect.
     */
    public synchronized void clear() {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) {
                for (BeltSubnetwork s : subnetworks.values()) {
                    vm.effects().queueRemove(s);
                }
            }
        }
        subnetworks.clear();
        posToSubnet.clear();
    }
}

