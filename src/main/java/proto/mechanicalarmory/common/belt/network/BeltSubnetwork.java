package proto.mechanicalarmory.common.belt.network;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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

    /** All nodes keyed by their BlockPos. */
    private final Map<BlockPos, BeltNode> nodes = new LinkedHashMap<>();

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
        nodes.put(node.pos(), node);
        topoDirty = true;
    }

    public void removeNode(BlockPos pos) {
        BeltNode node = nodes.remove(pos);
        if (node != null) {
            // Unlink from neighbours
            for (BeltNode n : nodes.values()) {
                n.removeInput(pos);
                if (pos.equals(n.outputPos())) n.setOutputPos(null);
            }
        }
        topoDirty = true;
    }

    @Nullable
    public BeltNode nodeAt(BlockPos pos) {
        return nodes.get(pos);
    }

    /** Alias for {@link #nodeAt} — look up a node by its BlockPos. */
    @Nullable
    public BeltNode node(BlockPos pos) { return nodes.get(pos); }

    public Collection<BeltNode> allNodes() { return nodes.values(); }

    public boolean isEmpty() { return nodes.isEmpty(); }

    public boolean containsPos(BlockPos pos) { return nodes.containsKey(pos); }

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
        BeltNode from = nodes.get(fromPos);
        BeltNode to   = nodes.get(toPos);
        if (from == null || to == null) {
            MechanicalArmory.LOGGER.warn("[BeltSubnetwork] link failed: fromNode({})={}, toNode({})={}",
                    fromPos.toShortString(), from != null, toPos.toShortString(), to != null);
            return false;
        }

        to.addInput(fromPos);
        from.setOutputPos(toPos);
        topoDirty = true;
        return true;
    }

    public void unlink(BlockPos fromPos) {
        BeltNode from = nodes.get(fromPos);
        if (from == null) return;
        BlockPos outPos = from.outputPos();
        from.setOutputPos(null);
        if (outPos != null) {
            BeltNode out = nodes.get(outPos);
            if (out != null) out.removeInput(fromPos);
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
        ObjectArrayList<BeltNode> result = new ObjectArrayList<>(nodes.size());
        ObjectOpenHashSet<BlockPos> visited = new ObjectOpenHashSet<>(nodes.size());

        // In output-first sorting, a node is ready when its downstream output has been placed.
        Object2IntOpenHashMap<BlockPos> outDegree = new Object2IntOpenHashMap<>(nodes.size());
        outDegree.defaultReturnValue(0);
        for (BeltNode n : nodes.values()) {
            if (n.outputPos() != null && nodes.containsKey(n.outputPos())) {
                outDegree.put(n.pos(), 1);
            } else {
                outDegree.put(n.pos(), 0);
            }
        }

        ObjectArrayFIFOQueue<BlockPos> queue = new ObjectArrayFIFOQueue<>(nodes.size());
        // 1. Initial drains / terminals (outDegree == 0)
        for (Object2IntOpenHashMap.Entry<BlockPos> e : outDegree.object2IntEntrySet()) {
            if (e.getIntValue() == 0) {
                queue.enqueue(e.getKey());
            }
        }

        while (result.size() < nodes.size()) {
            if (queue.isEmpty()) {
                // Graph contains a cycle among unvisited nodes.
                // Pick an unvisited node to break the cycle.
                BlockPos cycleBreak = null;
                for (BlockPos pos : nodes.keySet()) {
                    if (!visited.contains(pos)) {
                        cycleBreak = pos;
                        break;
                    }
                }
                if (cycleBreak == null) break;
                queue.enqueue(cycleBreak);
            }

            while (!queue.isEmpty()) {
                BlockPos pos = queue.dequeue();
                if (!visited.add(pos)) continue;
                BeltNode node = nodes.get(pos);
                if (node == null) continue;
                result.add(node);

                // Notify inputs that this output node has been placed
                for (BlockPos inputPos : node.inputPositions()) {
                    if (!visited.contains(inputPos)) {
                        int remaining = outDegree.addTo(inputPos, -1);
                        if (remaining <= 0) {
                            queue.enqueue(inputPos);
                        }
                    }
                }
            }
        }

        topoOrder = result;

        // ── Build layer partition ─────────────────────────────────────────────
        final int n = result.size();
        final Object2IntOpenHashMap<BlockPos> topoPos = new Object2IntOpenHashMap<>(n * 2);
        topoPos.defaultReturnValue(-1);
        for (int i = 0; i < n; i++) {
            topoPos.put(result.get(i).pos(), i);
        }

        final int[] depth = new int[n];
        int maxDepth = 0;
        for (int i = 0; i < n; i++) {
            BeltNode node = result.get(i);
            BlockPos outPos = node.outputPos();
            int outIdx = (outPos != null) ? topoPos.getInt(outPos) : -1;
            // Only depend on downstream nodes placed earlier in result (k < i)
            int d = (outIdx >= 0 && outIdx < i) ? depth[outIdx] + 1 : 0;
            depth[i] = d;
            if (d > maxDepth) maxDepth = d;
        }

        List<List<BeltNode>> layers = new ObjectArrayList<>(maxDepth + 1);
        for (int i = 0; i <= maxDepth; i++) layers.add(new ObjectArrayList<>());
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

        List<List<BlockPos>> components = new ObjectArrayList<>();
        ObjectOpenHashSet<BlockPos> unvisited = new ObjectOpenHashSet<>(nodes.keySet());

        while (!unvisited.isEmpty()) {
            BlockPos start = unvisited.iterator().next();
            List<BlockPos> component = new ObjectArrayList<>();
            ObjectArrayFIFOQueue<BlockPos> queue = new ObjectArrayFIFOQueue<>();
            queue.enqueue(start);
            while (!queue.isEmpty()) {
                BlockPos pos = queue.dequeue();
                if (!unvisited.remove(pos)) continue;
                component.add(pos);
                BeltNode n = nodes.get(pos);
                if (n == null) continue;
                if (n.outputPos() != null && unvisited.contains(n.outputPos()))
                    queue.enqueue(n.outputPos());
                for (BlockPos inp : n.inputPositions())
                    if (unvisited.contains(inp)) queue.enqueue(inp);
            }
            components.add(component);
        }

        if (components.size() == 1) return List.of(this); // still one piece

        List<BeltSubnetwork> result = new ObjectArrayList<>(components.size());
        for (List<BlockPos> component : components) {
            BeltSubnetwork sub = new BeltSubnetwork(UUID.randomUUID());
            for (BlockPos pos : component) {
                BeltNode n = nodes.get(pos);
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
