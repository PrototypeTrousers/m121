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

import javax.annotation.Nullable;
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
    private final Set<UUID> registeredWithFlywheel = new HashSet<>();

    private ClientBeltNetwork() {}

    @Nullable
    public synchronized BeltNode getNode(BlockPos pos) {
        UUID subId = posToSubnet.get(pos);
        if (subId == null) return null;
        BeltSubnetwork subnet = subnetworks.get(subId);
        return subnet == null ? null : subnet.nodeAt(pos);
    }

    /**
     * Called when a BeltInitPayload or BeltCorrectionPayload arrives from the server,
     * or when chunk NBT data is loaded.
     */
    public synchronized void updateNode(BlockPos pos, BeltLane lane0, BeltLane lane1,
                                        boolean stopped, boolean wrapPoint, boolean hasOutput) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        Direction facing = Direction.NORTH;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockBelt) {
            facing = state.getValue(BlockBelt.FACING);
        }

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
        } else {
            subnet = subnetworks.get(existingSubId);
            if (subnet == null) {
                subnet = new BeltSubnetwork(existingSubId);
                subnet.setLevel(level);
                subnetworks.put(existingSubId, subnet);
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
        float s0 = lane0.speed() > 0.0f ? lane0.speed() : BeltLane.SPEED_DEFAULT;
        node.lane(0).setSpeed(s0);

        node.lane(1).groups().clear();
        for (proto.mechanicalarmory.common.belt.data.ItemGroup g : lane1.groups()) {
            node.lane(1).insertBack(new proto.mechanicalarmory.common.belt.data.ItemGroup(g.item(), g.count(), g.headPos()));
        }
        float s1 = lane1.speed() > 0.0f ? lane1.speed() : BeltLane.SPEED_DEFAULT;
        node.lane(1).setSpeed(s1);

        node.setStopped(stopped);

        // Relink horizontal neighbours
        relink(pos, facing, level);

        // Sync with Flywheel
        syncToFlywheel();
    }

    private VisualizationManager lastVm = null;

    public synchronized void onVisualDeleted(UUID subnetId) {
        registeredWithFlywheel.remove(subnetId);
    }

    /**
     * Ensures all active client subnetworks are registered with Flywheel.
     */
    public synchronized void syncToFlywheel() {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        VisualizationManager vm = VisualizationManager.get(level);
        if (vm == null) return;

        if (vm != lastVm) {
            lastVm = vm;
            registeredWithFlywheel.clear();
        }

        for (BeltSubnetwork subnet : subnetworks.values()) {
            if (!registeredWithFlywheel.contains(subnet.subnetId())) {
                subnet.setLevel(level);
                vm.effects().queueAdd(subnet);
                registeredWithFlywheel.add(subnet.subnetId());
            } else {
                vm.effects().queueUpdate(subnet);
            }
        }
    }

    private void relink(BlockPos pos, Direction facing, Level level) {
        UUID subId = posToSubnet.get(pos);
        if (subId == null) return;
        BeltSubnetwork subnet = subnetworks.get(subId);
        if (subnet == null) return;

        BlockPos outPos = pos.relative(facing);
        if (posToSubnet.containsKey(outPos) && level.getBlockState(outPos).getBlock() instanceof BlockBelt) {
            mergeAndLink(pos, outPos, level);
        } else {
            subnet.unlink(pos);
        }

        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == facing) continue;
            BlockPos inPos = pos.relative(d);
            if (posToSubnet.containsKey(inPos)) {
                BlockState inState = level.getBlockState(inPos);
                if (inState.getBlock() instanceof BlockBelt) {
                    Direction inFacing = inState.getValue(BlockBelt.FACING);
                    if (inFacing == d.getOpposite()) {
                        mergeAndLink(inPos, pos, level);
                    }
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
            if (registeredWithFlywheel.remove(toSubId)) {
                VisualizationManager vm = VisualizationManager.get(level);
                if (vm != null) {
                    vm.effects().queueRemove(toSub);
                }
            }
        }

        fromSub.link(fromPos, toPos);
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
        if (level != null) {
            // Unlink any neighbor belts that were facing into the removed pos
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(d);
                UUID neighborSubId = posToSubnet.get(neighborPos);
                if (neighborSubId != null) {
                    BeltSubnetwork nSub = subnetworks.get(neighborSubId);
                    if (nSub != null) {
                        BlockState nState = level.getBlockState(neighborPos);
                        if (nState.getBlock() instanceof BlockBelt) {
                            Direction nFacing = nState.getValue(BlockBelt.FACING);
                            if (neighborPos.relative(nFacing).equals(pos)) {
                                nSub.unlink(neighborPos);
                            }
                        }
                    }
                }
            }
        }

        if (subnet.isEmpty()) {
            subnetworks.remove(subnetId);
            if (registeredWithFlywheel.remove(subnetId) && level != null) {
                VisualizationManager vm = VisualizationManager.get(level);
                if (vm != null) {
                    vm.effects().queueRemove(subnet);
                }
            }
        } else {
            List<BeltSubnetwork> parts = subnet.splitIfNeeded();
            if (parts.size() > 1) {
                subnetworks.remove(subnetId);
                if (registeredWithFlywheel.remove(subnetId) && level != null) {
                    VisualizationManager vm = VisualizationManager.get(level);
                    if (vm != null) {
                        vm.effects().queueRemove(subnet);
                    }
                }
                for (BeltSubnetwork part : parts) {
                    part.setLevel(level);
                    subnetworks.put(part.subnetId(), part);
                    for (BeltNode n : part.allNodes()) {
                        posToSubnet.put(n.pos(), part.subnetId());
                    }
                }
            }
            syncToFlywheel();
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
                for (UUID subId : registeredWithFlywheel) {
                    BeltSubnetwork s = subnetworks.get(subId);
                    if (s != null) {
                        vm.effects().queueRemove(s);
                    }
                }
            }
        }
        registeredWithFlywheel.clear();
        subnetworks.clear();
        posToSubnet.clear();
    }
}


