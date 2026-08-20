package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the {@code BlockPos -> subnetwork} and {@code UUID -> subnetwork}
 * bookkeeping shared by the server ({@code BeltNetworkData}) and client
 * ({@code proto.mechanicalarmory.client.belt.ClientBeltNetwork}) belt
 * managers, plus the graph-mutation rules (create / merge / link / split)
 * that were previously implemented twice, once per side, and had to be kept
 * in sync by hand.
 *
 * <p>This class is deliberately side-effect free with respect to the world
 * and network: it never touches {@code ServerLevel}/{@code Level} block
 * state and never sends packets or talks to Flywheel. Callers are
 * responsible for:
 * <ul>
 *   <li>deciding *whether* a link should exist (reading block state /
 *       facing on their respective side), and</li>
 *   <li>any notification side effects after a mutation (server: correction
 *       packets; client: Flywheel {@code queueAdd}/{@code queueRemove}).</li>
 * </ul>
 *
 * <h3>Side-loading</h3>
 * {@link #link} enforces the same Factorio-style side-loading rule as
 * {@link BeltSubnetwork#link}: a node accepts at most two inputs, one per
 * lane, and a third attempted merge is refused. See
 * {@link BeltNode#addInput}.
 */
public final class BeltSubnetworkRegistry {

    private final Map<UUID, BeltSubnetwork> subnetworks = new LinkedHashMap<>();
    private final Map<BlockPos, UUID> posToSubnet = new HashMap<>();

    // ── Lookups ───────────────────────────────────────────────────────────────

    @Nullable
    public BeltNode nodeAt(BlockPos pos) {
        UUID subnetId = posToSubnet.get(pos);
        if (subnetId == null) return null;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        return subnet == null ? null : subnet.nodeAt(pos);
    }

    @Nullable
    public UUID subnetIdAt(BlockPos pos) { return posToSubnet.get(pos); }

    @Nullable
    public BeltSubnetwork subnetwork(UUID subnetId) {
        return subnetId == null ? null : subnetworks.get(subnetId);
    }

    @Nullable
    public BeltSubnetwork subnetworkAt(BlockPos pos) {
        UUID id = posToSubnet.get(pos);
        return id == null ? null : subnetworks.get(id);
    }

    public Collection<BeltSubnetwork> allSubnetworks() { return subnetworks.values(); }

    public boolean isTracked(BlockPos pos) { return posToSubnet.containsKey(pos); }

    /** Snapshot of tracked positions — safe to iterate while mutating the registry. */
    public List<BlockPos> trackedPositions() { return new ArrayList<>(posToSubnet.keySet()); }

    // ── Registration ──────────────────────────────────────────────────────────

    /** Register a brand-new node in its own fresh, solo subnetwork. */
    public BeltSubnetwork registerSolo(BeltNode node) {
        BeltSubnetwork solo = new BeltSubnetwork(UUID.randomUUID());
        solo.addNode(node);
        subnetworks.put(solo.subnetId(), solo);
        posToSubnet.put(node.pos(), solo.subnetId());
        return solo;
    }

    /**
     * Get the existing node at {@code pos}, or create one in a fresh solo
     * subnetwork if none is registered yet.
     */
    public BeltNode getOrCreateNode(BlockPos pos, Direction facing) {
        BeltNode existing = nodeAt(pos);
        if (existing != null) {
            if (facing != null) existing.setFacing(facing);
            return existing;
        }
        BeltNode node = new BeltNode(pos, facing);
        registerSolo(node);
        return node;
    }

    public BeltNode getOrCreateNode(BlockPos pos) {
        return getOrCreateNode(pos, Direction.NORTH);
    }

    public void adoptSubnetwork(BeltSubnetwork subnet) {
        subnetworks.put(subnet.subnetId(), subnet);
        for (BeltNode n : subnet.allNodes()) {
            posToSubnet.put(n.pos(), subnet.subnetId());
        }
    }

    // ── Merge + link ──────────────────────────────────────────────────────────

    /** Result of {@link #mergeAndLink}, telling the caller what changed. */
    public record MergeResult(boolean linked, @Nullable UUID removedSubnetId) {
        public static final MergeResult REFUSED = new MergeResult(false, null);
    }

    /**
     * Merge the subnetwork owning {@code toPos} into the one owning
     * {@code fromPos} (if they're distinct), then link the edge
     * {@code fromPos -> toPos}.
     *
     * <p>Both {@code fromPos} and {@code toPos} must already be registered
     * (via {@link #registerSolo} / {@link #getOrCreateNode}) — this method
     * does not create nodes.
     *
     * @return a {@link MergeResult} describing whether the link succeeded and,
     *         if a subnetwork was absorbed and removed, its old id (so the
     *         caller can e.g. tell Flywheel to stop rendering it)
     */
    public MergeResult mergeAndLink(BlockPos fromPos, BlockPos toPos) {
        UUID fromSubId = posToSubnet.get(fromPos);
        UUID toSubId = posToSubnet.get(toPos);
        if (fromSubId == null || toSubId == null) return MergeResult.REFUSED;

        BeltSubnetwork fromSub = subnetworks.get(fromSubId);
        BeltSubnetwork toSub = subnetworks.get(toSubId);
        if (fromSub == null || toSub == null) return MergeResult.REFUSED;

        boolean merged = !fromSubId.equals(toSubId);
        if (merged) {
            for (BeltNode n : toSub.allNodes()) {
                fromSub.addNode(n);
                posToSubnet.put(n.pos(), fromSub.subnetId());
            }
            subnetworks.remove(toSubId);
        }

        boolean linked = fromSub.link(fromPos, toPos);
        if (!linked) {
            // Link refused (merge lanes full). If we already merged the
            // subnetworks together, that merge itself is still valid — the
            // nodes are genuinely in one connected component via other edges,
            // or will simply sit unlinked at this edge until a lane frees up.
            MechanicalArmory.LOGGER.warn(
                    "[BeltSubnetworkRegistry] link refused (merge point full): {} -> {}",
                    fromPos.toShortString(), toPos.toShortString());
        }

        return new MergeResult(linked, merged ? toSubId : null);
    }

    public void unlink(BlockPos fromPos) {
        BeltSubnetwork sub = subnetworkAt(fromPos);
        if (sub != null) sub.unlink(fromPos);
    }

    // ── Removal ───────────────────────────────────────────────────────────────

    /**
     * Result of {@link #removeNode}.
     *
     * <p>Exactly one of three things happened to the original subnetwork
     * (identified by {@code originalSubnetId}):
     * <ul>
     *   <li><b>Emptied</b> — {@code emptied() == true}. It had only the
     *       removed node; {@code emptiedSubnetwork} holds the now-empty
     *       object (still useful as an identity for e.g.
     *       {@code Effect}-based unregistration) and it no longer exists in
     *       this registry.</li>
     *   <li><b>Split</b> — {@code split() == true}. It disconnected into
     *       {@code newParts} (already adopted into this registry under fresh
     *       ids); the original id no longer exists.</li>
     *   <li><b>Unchanged</b> — neither flag set. It still exists under
     *       {@code originalSubnetId}, just missing one node.</li>
     * </ul>
     * In the emptied and split cases, {@code originalSubnetId} is now stale
     * and callers should stop referencing it (e.g. drop it from any
     * render-registration set).
     */
    public record RemovalResult(UUID originalSubnetId, boolean emptied,
                                 @Nullable BeltSubnetwork emptiedSubnetwork,
                                 List<BeltSubnetwork> newParts) {
        public boolean split() { return newParts.size() > 1; }
    }

    /**
     * Remove the node at {@code pos} from its subnetwork, unlinking it from
     * neighbours and splitting the subnetwork if the removal disconnected it.
     *
     * @return null if nothing was registered at {@code pos}; otherwise a
     *         {@link RemovalResult} describing what happened — all bookkeeping
     *         (removal, split-part adoption) is already applied to this
     *         registry by the time this returns
     */
    @Nullable
    public RemovalResult removeNode(BlockPos pos) {
        UUID subnetId = posToSubnet.remove(pos);
        if (subnetId == null) return null;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        if (subnet == null) return null;

        BeltNode node = subnet.nodeAt(pos);
        if (node != null) {
            subnet.removeNode(node.pos());
        }

        if (subnet.isEmpty()) {
            subnetworks.remove(subnetId);
            return new RemovalResult(subnetId, true, subnet, List.of());
        }

        List<BeltSubnetwork> parts = subnet.splitIfNeeded();
        if (parts.size() > 1) {
            subnetworks.remove(subnetId);
            for (BeltSubnetwork part : parts) {
                adoptSubnetwork(part);
            }
            return new RemovalResult(subnetId, false, null, parts);
        }

        return new RemovalResult(subnetId, false, null, List.of(subnet));
    }

    public void clear() {
        subnetworks.clear();
        posToSubnet.clear();
    }
}
