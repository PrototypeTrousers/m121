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

    @Nullable
    public synchronized UUID subnetworkIdForPos(BlockPos pos) {
        return posToSubnet.get(pos);
    }

    /** Look up a node by its UUID within a specific subnet (used by diagnostics). */
    @Nullable
    public synchronized BeltNode getNodeById(@Nullable UUID subnetId, @Nullable UUID nodeId) {
        if (subnetId == null || nodeId == null) return null;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        return subnet == null ? null : subnet.node(nodeId);
    }

    /** How many nodes are registered in a given subnet (used by diagnostics). */
    public synchronized int subnetNodeCount(@Nullable UUID subnetId) {
        if (subnetId == null) return 0;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        return subnet == null ? 0 : subnet.allNodes().size();
    }

    public synchronized int totalSubnetCount() { return subnetworks.size(); }

    public synchronized int totalNodeCount() {
        int total = 0;
        for (BeltSubnetwork s : subnetworks.values()) total += s.allNodes().size();
        return total;
    }

    /**
     * Called when a BeltInitPayload or BeltCorrectionPayload arrives from the server,
     * or when chunk NBT data is loaded.
     */
    public synchronized void updateNode(BlockPos pos, @Nullable UUID serverOutputId,
                                        BeltLane lane0, BeltLane lane1,
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
            node = new BeltNode(pos); // UUID always == BeltNode.posToId(pos)
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
                node = new BeltNode(pos);
                subnet.addNode(node);
            }
        }

        // serverOutputId is now just BeltNode.posToId(outPos) – keep it for
        // the case where outPos hasn't loaded yet and relink can't merge.
        if (serverOutputId != null) {
            node.setOutputId(serverOutputId);
        }

        // Copy lane contents from the server snapshot
        node.lane(0).clearGroups();
        for (int i = 0; i < lane0.groupCount(); i++) {
            proto.mechanicalarmory.common.belt.data.ItemGroup g = lane0.groupArray()[i];
            if (g != null) {
                node.lane(0).addLast(new proto.mechanicalarmory.common.belt.data.ItemGroup(g.item(), g.count(), g.headPos()));
            }
        }
        float s0 = lane0.speed() > 0.0f ? lane0.speed() : BeltLane.SPEED_DEFAULT;
        node.lane(0).setSpeed(s0);

        node.lane(1).clearGroups();
        for (int i = 0; i < lane1.groupCount(); i++) {
            proto.mechanicalarmory.common.belt.data.ItemGroup g = lane1.groupArray()[i];
            if (g != null) {
                node.lane(1).addLast(new proto.mechanicalarmory.common.belt.data.ItemGroup(g.item(), g.count(), g.headPos()));
            }
        }
        float s1 = lane1.speed() > 0.0f ? lane1.speed() : BeltLane.SPEED_DEFAULT;
        node.lane(1).setSpeed(s1);

        node.setStopped(stopped);

        // Relink neighbours. Pass hasOutput so relink won't unlink when the forward
        // block isn't visible yet (server told us there IS an output – trust it).
        relink(pos, facing, level, hasOutput);

        // Sync with Flywheel
        syncToFlywheel();
    }

    public synchronized void updateNode(BlockPos pos, BeltLane lane0, BeltLane lane1,
                                        boolean stopped, boolean wrapPoint, boolean hasOutput) {
        updateNode(pos, null, lane0, lane1, stopped, wrapPoint, hasOutput);
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

        // Before registering with flywheel, reconcile any cross-subnet links that
        // couldn't be resolved during updateNode() due to chunk loading order.
        reconcileLinks(level);

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

    /**
     * Scans every registered node. If a node's {@code outputId} is not resolved
     * inside its own subnet, but the target position IS registered in another
     * subnet, the two subnets are merged and properly linked.
     *
     * <p>This corrects chunk-boundary timing issues where the upstream belt loaded
     * before its downstream neighbor was registered in {@link #posToSubnet}.
     */
    private void reconcileLinks(Level level) {
        // Snapshot the entry set to avoid ConcurrentModificationException
        // (mergeAndLink can modify posToSubnet while we iterate).
        List<BlockPos> positions = new ArrayList<>(posToSubnet.keySet());

        for (BlockPos pos : positions) {
            UUID subId = posToSubnet.get(pos);
            if (subId == null) continue;
            BeltSubnetwork sub = subnetworks.get(subId);
            if (sub == null) continue;
            BeltNode node = sub.nodeAt(pos);
            if (node == null || node.outputId() == null) continue;

            // Already resolved in this subnet → nothing to do.
            if (sub.node(node.outputId()) != null) continue;

            // The outputId isn't in our subnet. Derive the downstream position
            // from the block world and check if it's registered somewhere.
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof proto.mechanicalarmory.common.blocks.BlockBelt)) continue;
            Direction facing = state.getValue(proto.mechanicalarmory.common.blocks.BlockBelt.FACING);
            BlockPos outPos = pos.relative(facing);

            if (posToSubnet.containsKey(outPos)) {
                // Downstream is registered – merge the two subnets together.
                proto.mechanicalarmory.MechanicalArmory.LOGGER.info(
                        "[ClientBeltNetwork] reconcile: merging {} -> {}", pos.toShortString(), outPos.toShortString());
                mergeAndLink(pos, outPos, level);
            }
        }
    }

    /**
     * @param hasOutput true when the server confirmed this node has a downstream belt.
     *                  When true, we never call unlink() even if outPos isn't visible yet,
     *                  because the block may still be loading on the client.
     */
    private void relink(BlockPos pos, Direction facing, Level level, boolean hasOutput) {
        UUID subId = posToSubnet.get(pos);
        if (subId == null) return;
        BeltSubnetwork subnet = subnetworks.get(subId);
        if (subnet == null) return;

        BlockPos outPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outPos);
        if (outState.getBlock() instanceof BlockBelt) {
            // Forward neighbour is loaded and is a belt – merge + link.
            mergeAndLink(pos, outPos, level);
        } else if (!hasOutput) {
            // Server says no output and no belt block found – truly unlinked.
            subnet.unlink(pos);
        }
        // else: server says hasOutput but the block isn't visible yet; keep existing outputId.

        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos inPos = pos.relative(d);
            BlockState inState = level.getBlockState(inPos);
            if (inState.getBlock() instanceof BlockBelt) {
                Direction inFacing = inState.getValue(BlockBelt.FACING);
                if (inFacing == d.getOpposite()) {
                    mergeAndLink(inPos, pos, level);
                }
            }
        }
    }

    /** Overload used when block-break / explicit neighbour update relinks without a server hint. */
    private void relink(BlockPos pos, Direction facing, Level level) {
        relink(pos, facing, level, false);
    }

    private BeltNode getOrCreateNode(BlockPos pos, Level level) {
        UUID subId = posToSubnet.get(pos);
        if (subId != null) {
            BeltSubnetwork sub = subnetworks.get(subId);
            if (sub != null) {
                BeltNode n = sub.nodeAt(pos);
                if (n != null) return n;
            }
        }
        UUID newSubId = UUID.randomUUID();
        BeltSubnetwork subnet = new BeltSubnetwork(newSubId);
        subnet.setLevel(level);
        BeltNode node = new BeltNode(pos); // always posToId(pos)
        subnet.addNode(node);
        subnetworks.put(newSubId, subnet);
        posToSubnet.put(pos, newSubId);
        return node;
    }

    private void mergeAndLink(BlockPos fromPos, BlockPos toPos, Level level) {
        BeltNode fromNode = getOrCreateNode(fromPos, level);
        BeltNode toNode = getOrCreateNode(toPos, level);

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
        proto.mechanicalarmory.MechanicalArmory.LOGGER.info("[ClientBeltNetwork] Linked {} -> {}, from.outId={}, to.inCount={}",
                fromPos.toShortString(), toPos.toShortString(),
                fromNode.outputId() != null ? fromNode.outputId().toString().substring(0, 8) : "null",
                toNode.inputIds().size());
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


