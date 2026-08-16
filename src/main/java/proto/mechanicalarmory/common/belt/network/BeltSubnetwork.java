package proto.mechanicalarmory.common.belt.network;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.flywheel.instances.belt.BeltSubnetworkVisual;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.*;

/**
 * A single connected component of the belt network.
 *
 * <p>Nodes are stored in topological order (output-first / layer-0 first) so
 * that {@link BeltNetworkTick} can walk them in-order without recomputing the
 * sort each tick.
 */
public final class BeltSubnetwork implements Effect {

    private final UUID subnetId;

    /** All nodes keyed by their UUID. */
    private final Map<UUID, BeltNode> nodes = new LinkedHashMap<>();

    /** Fast lookup: BlockPos → node UUID. */
    private final Map<BlockPos, UUID> posIndex = new HashMap<>();

    /**
     * Nodes in topo order (layer-0 first = output-first).
     * Rebuilt whenever {@code topoDirty} is true.
     */
    private List<BeltNode> topoOrder = new ArrayList<>();

    /**
     * Nodes grouped into parallel layers — layer 0 are the terminals/wrap-points,
     * higher layers feed into them.  Nodes within the same layer share no edges
     * and can be ticked in parallel.  Rebuilt together with {@link #topoOrder}.
     */
    private List<List<BeltNode>> cachedLayers = new ArrayList<>();

    private boolean topoDirty = true;

    @Nullable
    private Level level;

    public BeltSubnetwork(UUID subnetId) {
        this.subnetId = subnetId;
    }

    public UUID subnetId() { return subnetId; }

    public void setLevel(@Nullable Level level) {
        this.level = level;
    }

    @Override
    public Level level() {
        if (level != null) return level;
        return Minecraft.getInstance().level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new BeltSubnetworkVisual(ctx, this, partialTick);
    }

    // ── Node management ───────────────────────────────────────────────────────

    public void addNode(BeltNode node) {
        nodes.put(node.nodeId(), node);
        posIndex.put(node.pos(), node.nodeId());
        topoDirty = true;
    }

    public void removeNode(UUID id) {
        BeltNode node = nodes.remove(id);
        if (node != null) {
            posIndex.remove(node.pos());
            // Unlink from neighbours
            for (BeltNode n : nodes.values()) {
                n.removeInput(id);
                if (id.equals(n.outputId())) n.setOutputId(null);
            }
        }
        topoDirty = true;
    }

    @Nullable
    public BeltNode nodeAt(BlockPos pos) {
        UUID id = posIndex.get(pos);
        return id == null ? null : nodes.get(id);
    }

    @Nullable
    public BeltNode node(UUID id) { return nodes.get(id); }

    public Collection<BeltNode> allNodes() { return nodes.values(); }

    public boolean isEmpty() { return nodes.isEmpty(); }

    public boolean containsPos(BlockPos pos) { return posIndex.containsKey(pos); }

    // ── Edge linking ──────────────────────────────────────────────────────────

    /**
     * Connect {@code fromPos} → {@code toPos}.
     *
     * <p>Side-loading (Factorio-style): {@code to} has exactly two lanes, and
     * each upstream node is assigned exclusively to one of them via
     * {@link BeltNode#addInput}. If a third belt tries to merge into a node
     * that already has both lanes claimed, the link is refused — that spot is
     * full. This exclusivity is also what makes same-layer parallel ticking
     * safe: two inputs merging at a node write to disjoint lanes.
     *
     * @return true if the link was created, false if refused (missing nodes
     *         or both of {@code to}'s lanes already occupied by other inputs)
     */
    public boolean link(BlockPos fromPos, BlockPos toPos) {
        BeltNode from = nodeAt(fromPos);
        BeltNode to   = nodeAt(toPos);
        if (from == null || to == null) {
            MechanicalArmory.LOGGER.warn("[BeltSubnetwork] link failed: fromNode({})={}, toNode({})={}",
                    fromPos.toShortString(), from != null, toPos.toShortString(), to != null);
            return false;
        }

        int lane = to.addInput(from.nodeId());
        if (lane < 0) {
            MechanicalArmory.LOGGER.warn(
                    "[BeltSubnetwork] link refused: {} -> {} has both merge lanes occupied",
                    fromPos.toShortString(), toPos.toShortString());
            return false;
        }

        from.setOutputId(to.nodeId());
        topoDirty = true;
        return true;
    }

    public void unlink(BlockPos fromPos) {
        BeltNode from = nodeAt(fromPos);
        if (from == null) return;
        UUID outId = from.outputId();
        from.setOutputId(null);
        if (outId != null) {
            BeltNode out = nodes.get(outId);
            if (out != null) out.removeInput(from.nodeId());
        }
        topoDirty = true;
    }

    // ── Topological sort ──────────────────────────────────────────────────────

    /**
     * Returns nodes sorted output-first (layer 0 first).
     * The result is cached until the topology changes.
     */
    public List<BeltNode> topoOrder() {
        if (topoDirty) rebuildTopo();
        return topoOrder;
    }

    /**
     * Returns nodes partitioned into parallel layers, cached alongside
     * {@link #topoOrder()}.  Layer 0 = terminals / wrap-points; nodes in
     * layer L all have their output in layer L-1 (already finished for this
     * tick), so within each layer nodes can run concurrently.
     */
    public List<List<BeltNode>> layerOrder() {
        if (topoDirty) rebuildTopo();
        return cachedLayers;
    }

    private void rebuildTopo() {
        List<BeltNode> result = new ArrayList<>(nodes.size());
        Set<UUID> visited = new HashSet<>();

        // In output-first sorting, a node is ready when its downstream output has been placed.
        Map<UUID, Integer> outDegree = new HashMap<>();
        for (BeltNode n : nodes.values()) {
            if (n.outputId() != null && nodes.containsKey(n.outputId())) {
                outDegree.put(n.nodeId(), 1);
            } else {
                outDegree.put(n.nodeId(), 0);
            }
        }

        Queue<UUID> queue = new ArrayDeque<>();
        // 1. Initial drains / terminals (outDegree == 0)
        for (Map.Entry<UUID, Integer> e : outDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }

        while (result.size() < nodes.size()) {
            if (queue.isEmpty()) {
                // Graph contains a cycle among unvisited nodes.
                // Pick an unvisited node to break the cycle.
                UUID cycleBreak = null;
                for (UUID id : nodes.keySet()) {
                    if (!visited.contains(id)) {
                        cycleBreak = id;
                        break;
                    }
                }
                if (cycleBreak == null) break;
                queue.add(cycleBreak);
            }

            while (!queue.isEmpty()) {
                UUID id = queue.poll();
                if (!visited.add(id)) continue;
                BeltNode node = nodes.get(id);
                if (node == null) continue;
                result.add(node);

                // Notify inputs that this output node has been placed
                for (UUID inputId : node.inputIds()) {
                    if (!visited.contains(inputId)) {
                        int remaining = outDegree.merge(inputId, -1, Integer::sum);
                        if (remaining <= 0) {
                            queue.add(inputId);
                        }
                    }
                }
            }
        }

        topoOrder = result;

        // ── Build layer partition ─────────────────────────────────────────────
        final int n = result.size();
        final Map<UUID, Integer> topoPos = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            topoPos.put(result.get(i).nodeId(), i);
        }

        final int[] depth = new int[n];
        int maxDepth = 0;
        for (int i = 0; i < n; i++) {
            BeltNode node = result.get(i);
            UUID outId = node.outputId();
            Integer outIdx = (outId != null) ? topoPos.get(outId) : null;
            // Only depend on downstream nodes placed earlier in result (k < i)
            int d = (outIdx != null && outIdx < i) ? depth[outIdx] + 1 : 0;
            depth[i] = d;
            if (d > maxDepth) maxDepth = d;
        }

        List<List<BeltNode>> layers = new ArrayList<>(maxDepth + 1);
        for (int i = 0; i <= maxDepth; i++) layers.add(new ArrayList<>());
        for (int i = 0; i < n; i++) layers.get(depth[i]).add(result.get(i));
        cachedLayers = layers;

        topoDirty = false;
    }

    // ── Subnetwork split check ────────────────────────────────────────────────

    /**
     * After removing a node, split this subnetwork into separate connected
     * components.  Returns all resulting subnetworks (may be just {@code this}
     * if still connected, or multiple if it split).
     */
    public List<BeltSubnetwork> splitIfNeeded() {
        if (nodes.isEmpty()) return List.of();

        List<List<UUID>> components = new ArrayList<>();
        Set<UUID> unvisited = new HashSet<>(nodes.keySet());

        while (!unvisited.isEmpty()) {
            UUID start = unvisited.iterator().next();
            List<UUID> component = new ArrayList<>();
            Deque<UUID> queue = new ArrayDeque<>();
            queue.push(start);
            while (!queue.isEmpty()) {
                UUID id = queue.pop();
                if (!unvisited.remove(id)) continue;
                component.add(id);
                BeltNode n = nodes.get(id);
                if (n == null) continue;
                if (n.outputId() != null && unvisited.contains(n.outputId()))
                    queue.push(n.outputId());
                for (UUID inp : n.inputIds())
                    if (unvisited.contains(inp)) queue.push(inp);
            }
            components.add(component);
        }

        if (components.size() == 1) return List.of(this); // still one piece

        List<BeltSubnetwork> result = new ArrayList<>();
        for (List<UUID> component : components) {
            BeltSubnetwork sub = new BeltSubnetwork(UUID.randomUUID());
            for (UUID id : component) {
                BeltNode n = nodes.get(id);
                if (n != null) sub.addNode(n);
            }
            result.add(sub);
        }
        return result;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("subnetId", subnetId);
        ListTag nodeList = new ListTag();
        for (BeltNode n : nodes.values()) {
            nodeList.add(n.save(registries));
        }
        tag.put("nodes", nodeList);
        return tag;
    }

    public static BeltSubnetwork load(CompoundTag tag, HolderLookup.Provider registries) {
        UUID id = tag.getUUID("subnetId");
        BeltSubnetwork sub = new BeltSubnetwork(id);
        ListTag nodeList = tag.getList("nodes", Tag.TAG_COMPOUND);
        for (int i = 0; i < nodeList.size(); i++) {
            BeltNode n = BeltNode.load(nodeList.getCompound(i), registries);
            sub.addNode(n);
        }
        sub.topoDirty = true;
        return sub;
    }
}
