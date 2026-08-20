package proto.mechanicalarmory.client.belt;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.belt.network.BeltGraphHelper;
import proto.mechanicalarmory.common.belt.network.BeltSubnetwork;
import proto.mechanicalarmory.common.belt.network.BeltSubnetworkRegistry;
import proto.mechanicalarmory.common.blocks.BlockBelt;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Client-side belt network manager.
 *
 * <p>Tracks all active {@link BeltSubnetwork}s on the client and registers them
 * with Flywheel's {@link VisualizationManager#effects()} so they are rendered
 * by {@link proto.mechanicalarmory.client.flywheel.instances.belt.BeltSubnetworkVisual}.
 *
 * <p>All graph bookkeeping (BlockPos/UUID maps, create/merge/link/split rules)
 * is delegated to {@link BeltSubnetworkRegistry}, the same class the
 * server-side {@code BeltNetworkData} uses — those rules were previously
 * reimplemented independently on each side and had to be kept in sync by
 * hand. This class now only owns what's genuinely client-specific: reading
 * neighbour block state to decide *when* to (re)link, and telling Flywheel
 * about subnetwork add/update/remove.
 */
public final class ClientBeltNetwork {

    private static final ClientBeltNetwork INSTANCE = new ClientBeltNetwork();

    public static ClientBeltNetwork get() {
        return INSTANCE;
    }

    private final BeltSubnetworkRegistry registry = new BeltSubnetworkRegistry();
    private final Set<UUID> registeredWithFlywheel = new HashSet<>();
    private VisualizationManager lastVm = null;

    public BeltSubnetworkRegistry registry() {
        return registry;
    }

    private ClientBeltNetwork() {}

    // ── Updates from the server ──────────────────────────────────────────────

    /**
     * Called when a BeltInitPayload or BeltCorrectionPayload arrives from the server,
     * or when chunk NBT data is loaded.
     */
    public void updateNode(BlockPos pos, @Nullable Direction facing, @Nullable BlockPos serverOutputPos,
                           BeltLane lane0, BeltLane lane1,
                           boolean stopped, boolean hasOutput) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BlockBelt) {
            facing = state.getValue(BlockBelt.FACING);
        }

        BeltNode node = registry.getOrCreateNode(pos, facing);
        node.setFacing(facing);
        BeltSubnetwork subnet = registry.subnetworkAt(pos);
        if (subnet != null) subnet.setLevel(level);

        if (serverOutputPos != null) {
            node.setOutputPos(serverOutputPos);
        }

        // Copy lane contents from the server snapshot
        copyLane(lane0, node.lane(0));
        copyLane(lane1, node.lane(1));

        node.setStopped(stopped);

        // Relink neighbours. Pass hasOutput so relink won't unlink when the forward
        // block isn't visible yet (server told us there IS an output – trust it).
        relink(pos, facing, level, hasOutput);

        // Sync with Flywheel
        syncToFlywheel();
    }

    private static void copyLane(BeltLane source, BeltLane dest) {
        dest.clearGroups();
        for (int i = 0; i < source.groupCount(); i++) {
            ItemGroup g = source.groupArray()[i];
            if (g != null) {
                dest.addLast(new ItemGroup(g.item(), g.count(), g.headPos()));
            }
        }
        float speed = source.speed() > 0.0f ? source.speed() : BeltLane.SPEED_DEFAULT;
        dest.setSpeed(speed);
    }

    // ── Flywheel sync ────────────────────────────────────────────────────────

    public void onVisualDeleted(UUID subnetId) {
        registeredWithFlywheel.remove(subnetId);
    }

    /**
     * Ensures all active client subnetworks are registered with Flywheel.
     */
    public void syncToFlywheel() {
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

        for (BeltSubnetwork subnet : registry.allSubnetworks()) {
            if (!registeredWithFlywheel.contains(subnet.subnetId())) {
                subnet.setLevel(level);
                vm.effects().queueAdd(subnet);
                registeredWithFlywheel.add(subnet.subnetId());
            } else {
                vm.effects().queueUpdate(subnet);
            }
        }
    }

    /** Tell Flywheel to stop rendering a subnetwork that was absorbed into another. */
    private void unregisterFromFlywheel(UUID subnetId, BeltSubnetwork subnet, Level level) {
        if (registeredWithFlywheel.remove(subnetId)) {
            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) vm.effects().queueRemove(subnet);
        }
    }

    /**
     * Scans every registered node. If a node's {@code outputId} is not resolved
     * inside its own subnet, but the target position IS registered in another
     * subnet, the two subnets are merged and properly linked.
     *
     * <p>This corrects chunk-boundary timing issues where the upstream belt loaded
     * before its downstream neighbor was registered.
     */
    private void reconcileLinks(Level level) {
        // Snapshot to avoid ConcurrentModificationException (mergeAndLink can
        // mutate the registry's position map while we iterate).
        for (BlockPos pos : registry.trackedPositions()) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockBelt)) {
                removeNode(pos);
                continue;
            }

            BeltSubnetwork sub = registry.subnetworkAt(pos);
            if (sub == null) continue;
            BeltNode node = sub.nodeAt(pos);
            if (node == null || node.outputPos() == null) continue;

            // Already resolved in this subnet → nothing to do.
            if (sub.node(node.outputPos()) != null) continue;

            // The outputId isn't in our subnet. Derive the downstream position
            // from the block world and check if it's registered somewhere.
            Direction facing = state.getValue(BlockBelt.FACING);
            BlockPos outPos = pos.relative(facing);

            if (registry.isTracked(outPos)) {
                MechanicalArmory.LOGGER.info(
                        "[ClientBeltNetwork] reconcile: merging {} -> {}", pos.toShortString(), outPos.toShortString());
                mergeAndLink(pos, outPos, level);
            }
        }
    }

    // ── Relink / merge ───────────────────────────────────────────────────────

    /**
     * @param hasOutput true when the server confirmed this node has a downstream belt.
     *                  When true, we never call unlink() even if outPos isn't visible yet,
     *                  because the block may still be loading on the client.
     */
    private void relink(BlockPos pos, Direction facing, Level level, boolean hasOutput) {
        if (!registry.isTracked(pos)) return;

        BlockPos outPos = pos.relative(facing);
        BlockState outState = level != null ? level.getBlockState(outPos) : null;
        if (outState == null || !(outState.getBlock() instanceof BlockBelt)) {
            if (!hasOutput) {
                // Server says no output and no belt block found – truly unlinked.
                registry.unlink(pos);
            }
        }

        BeltGraphHelper.linkNeighbours(pos, facing, level, (from, to) -> mergeAndLink(from, to, level));
    }

    /**
     * Merge the subnetwork owning {@code toPos} into the one owning
     * {@code fromPos}, then link the edge and notify Flywheel if a
     * subnetwork was absorbed. Side-loading (Factorio-style) is enforced by
     * the registry: if {@code toPos}'s two merge lanes are already both
     * occupied by other inputs, the link is refused and a warning is logged
     * — see {@link BeltNode#addInput}.
     */
    private void mergeAndLink(BlockPos fromPos, BlockPos toPos, Level level) {
        BeltNode fromNode = registry.getOrCreateNode(fromPos, level.getBlockState(fromPos).getValue(BlockBelt.FACING));
        BeltNode toNode = registry.getOrCreateNode(toPos, level.getBlockState(toPos).getValue(BlockBelt.FACING));
        BeltSubnetwork toSubBefore = registry.subnetworkAt(toPos);

        BeltSubnetworkRegistry.MergeResult result = registry.mergeAndLink(fromPos, toPos);
        if (result.removedSubnetId() != null && toSubBefore != null) {
            unregisterFromFlywheel(result.removedSubnetId(), toSubBefore, level);
        }

        MechanicalArmory.LOGGER.info(
                "[ClientBeltNetwork] Linked {} -> {} ({}), from.outId={}, to.inCount={}",
                fromPos.toShortString(), toPos.toShortString(), result.linked() ? "ok" : "refused",
                fromNode.outputPos() != null ? fromNode.outputPos().toString().substring(0, 8) : "null",
                toNode.inputPositions().size());
    }

    // ── Removal ───────────────────────────────────────────────────────────────

    /**
     * Remove a node when its block is broken or chunk unloaded.
     */
    public void removeNode(BlockPos pos) {
        Level level = Minecraft.getInstance().level;

        // Unlink any neighbor belts that were facing into the removed pos
        BeltGraphHelper.unlinkIncomingNeighbours(pos, level, registry);

        BeltSubnetworkRegistry.RemovalResult result = registry.removeNode(pos);
        if (result == null) return;

        if (result.emptied() || result.split()) {
            // Subnetwork had only this node or was split into parts. Tell Flywheel to stop
            // rendering the old subnetwork so its instances get destroyed.
            if (level != null && result.oldSubnetwork() != null) {
                unregisterFromFlywheel(result.originalSubnetId(), result.oldSubnetwork(), level);
            } else {
                registeredWithFlywheel.remove(result.originalSubnetId());
            }
        }

        if (level == null) return;

        for (BeltSubnetwork part : result.newParts()) {
            part.setLevel(level);
        }
        syncToFlywheel();
    }

    /**
     * Clear on level unload or disconnect.
     */
    public void clear() {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            VisualizationManager vm = VisualizationManager.get(level);
            if (vm != null) {
                for (UUID subId : registeredWithFlywheel) {
                    BeltSubnetwork s = registry.subnetwork(subId);
                    if (s != null) {
                        vm.effects().queueRemove(s);
                    }
                }
            }
        }
        registeredWithFlywheel.clear();
        registry.clear();
    }
}
