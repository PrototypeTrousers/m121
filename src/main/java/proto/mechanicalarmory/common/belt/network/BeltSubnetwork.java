package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.*;

/**
 * A single connected component of the belt network.
 *
 * <p>Nodes are stored in topological order (output-first / layer-0 first) so
 * that {@link BeltNetworkTick} can walk them in-order without recomputing the
 * sort each tick.
 *
 * <h3>Topo-sort rules</h3>
 * <ol>
 *   <li>Wrap-point nodes are unconditionally placed in layer 0 (they are the
 *       "drain" for any belts feeding into the loop).</li>
 *   <li>All remaining nodes are sorted with Kahn's algorithm on the graph with
 *       wrap-point back-edges removed.</li>
 * </ol>
 */
public final class BeltSubnetwork {

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
    private boolean topoDirty = true;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltSubnetwork(UUID subnetId) {
        this.subnetId = subnetId;
    }

    public UUID subnetId() { return subnetId; }

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
     * Connect {@code fromPos} → {@code toPos}.  If the connection would close a
     * cycle, a random node in the cycle is designated the wrap-point instead of
     * adding a real output edge.
     */
    public void link(BlockPos fromPos, BlockPos toPos) {
        BeltNode from = nodeAt(fromPos);
        BeltNode to   = nodeAt(toPos);
        if (from == null || to == null) return;

        // Cycle check: would connecting from→to create a cycle?
        if (wouldCycle(from.nodeId(), to.nodeId())) {
            // Pick the wrap-point randomly among all nodes in the cycle
            List<UUID> cycle = findCycle(from.nodeId(), to.nodeId());
            if (!cycle.isEmpty()) {
                UUID wrapId = cycle.get(new Random().nextInt(cycle.size()));
                BeltNode wrapNode = nodes.get(wrapId);
                if (wrapNode != null) {
                    wrapNode.setWrapPoint(true);
                    wrapNode.setOutputId(null); // no real output
                }
            }
        } else {
            from.setOutputId(to.nodeId());
            to.addInput(from.nodeId());
        }
        topoDirty = true;
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

    private void rebuildTopo() {
        // 1. Wrap-points go in layer 0.
        List<BeltNode> result = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();

        for (BeltNode n : nodes.values()) {
            if (n.isWrapPoint()) {
                result.add(n);
                visited.add(n.nodeId());
            }
        }

        // 2. Kahn's algorithm on the remaining graph (back-edges from wrap-points removed).
        // Build in-degree map (excluding back edges from wrap-points).
        Map<UUID, Integer> inDegree = new HashMap<>();
        for (BeltNode n : nodes.values()) {
            inDegree.putIfAbsent(n.nodeId(), 0);
            UUID outId = n.outputId();
            if (outId != null && !n.isWrapPoint()) {
                inDegree.merge(outId, 1, Integer::sum);
            }
        }

        // Terminals (inDegree == 0 from non-wrap edges, not already added)
        Queue<UUID> queue = new ArrayDeque<>();
        for (Map.Entry<UUID, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0 && !visited.contains(e.getKey())) {
                queue.add(e.getKey());
            }
        }

        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (visited.contains(id)) continue;
            BeltNode node = nodes.get(id);
            if (node == null) continue;
            result.add(node);
            visited.add(id);

            // Reduce in-degree of inputs (they come after this in topo)
            for (UUID inputId : node.inputIds()) {
                if (!visited.contains(inputId)) {
                    int deg = inDegree.merge(inputId, -1, Integer::sum);
                    if (deg == 0) queue.add(inputId);
                }
            }
        }

        // Add any remaining nodes not reached (disconnected or in unresolved cycles)
        for (BeltNode n : nodes.values()) {
            if (!visited.contains(n.nodeId())) {
                result.add(n);
            }
        }

        topoOrder = result;
        topoDirty = false;
    }

    // ── Cycle detection helpers ───────────────────────────────────────────────

    private boolean wouldCycle(UUID fromId, UUID toId) {
        // DFS: can we reach fromId starting from toId?
        Set<UUID> seen = new HashSet<>();
        Deque<UUID> stack = new ArrayDeque<>();
        stack.push(toId);
        while (!stack.isEmpty()) {
            UUID cur = stack.pop();
            if (cur.equals(fromId)) return true;
            if (!seen.add(cur)) continue;
            BeltNode n = nodes.get(cur);
            if (n != null && n.outputId() != null) stack.push(n.outputId());
        }
        return false;
    }

    /** Collect all node UUIDs on the cycle that would be formed by fromId→toId. */
    private List<UUID> findCycle(UUID fromId, UUID toId) {
        List<UUID> cycle = new ArrayList<>();
        // Walk from toId following outputs until we hit fromId
        Set<UUID> seen = new HashSet<>();
        UUID cur = toId;
        while (cur != null && !cur.equals(fromId)) {
            if (!seen.add(cur)) break; // safety
            cycle.add(cur);
            BeltNode n = nodes.get(cur);
            cur = (n != null) ? n.outputId() : null;
        }
        if (cur != null) cycle.add(fromId);
        return cycle;
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
