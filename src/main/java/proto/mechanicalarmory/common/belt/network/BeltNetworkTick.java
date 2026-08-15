package proto.mechanicalarmory.common.belt.network;

import net.minecraft.server.level.ServerLevel;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Executes one simulation tick for a {@link BeltSubnetwork}.
 *
 * <h3>Update order</h3>
 * Nodes are processed in topological order (output-first).  Wrap-point nodes
 * are layer 0 and are processed first.  Within each topo layer, nodes that
 * share no edges can be processed in parallel using a cached thread pool.
 *
 * <h3>Thread safety</h3>
 * When processing layer {@code L}, we write to each node's own lanes.  The
 * output of a layer-L node is a layer-(L−1) node that is <em>already
 * finished</em> for this tick, so there are no write–write conflicts.  Workers
 * never touch world state; all block/level access happens before and after the
 * parallel sections on the main server thread.
 */
public final class BeltNetworkTick {

    /** Global pool shared by all subnetworks — sized to available processors. */
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                    r -> {
                        Thread t = new Thread(r, "belt-network-worker");
                        t.setDaemon(true);
                        return t;
                    });

    private BeltNetworkTick() {}

    /**
     * Tick all subnetworks in the given {@link BeltNetworkData}.
     * Called once per server level tick from the main thread.
     */
    public static void tickAll(BeltNetworkData data, ServerLevel level) {
        for (BeltSubnetwork subnet : data.allSubnetworks()) {
            tickSubnetwork(subnet, level);
        }
    }

    // ── Per-subnetwork tick ───────────────────────────────────────────────────

    private static void tickSubnetwork(BeltSubnetwork subnet, ServerLevel level) {
        List<BeltNode> topo = subnet.topoOrder();
        if (topo.isEmpty()) return;

        // Group topo-ordered nodes into layers.
        // Since topoOrder already guarantees correct processing order, and nodes
        // within the same layer share no edges, we can run each layer in parallel.
        List<List<BeltNode>> layers = buildLayers(topo);

        for (List<BeltNode> layer : layers) {
            if (layer.size() == 1) {
                // Avoid thread overhead for single-node layers (common case)
                tickNode(layer.get(0), subnet);
            } else {
                // Submit all nodes in this layer concurrently, then join.
                List<CompletableFuture<Void>> futures = new ArrayList<>(layer.size());
                for (BeltNode node : layer) {
                    futures.add(CompletableFuture.runAsync(() -> tickNode(node, subnet), POOL));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
    }

    /**
     * Build a list of layers from an already-sorted topo list.
     * Layer 0 = wrap-points (they have no outputId in the topo graph).
     * Subsequent layers are determined by walking the outputId chain.
     */
    private static List<List<BeltNode>> buildLayers(List<BeltNode> topo) {
        // Simple approach: assign each node a depth via its outputId chain.
        // Because topo is already in output-first order, a linear pass works.
        java.util.Map<java.util.UUID, Integer> depth = new java.util.HashMap<>();

        for (BeltNode node : topo) {
            int myDepth = 0;
            java.util.UUID outId = node.outputId();
            if (outId != null && depth.containsKey(outId)) {
                myDepth = depth.get(outId) + 1;
            } else if (node.isWrapPoint()) {
                myDepth = 0;
            }
            depth.put(node.nodeId(), myDepth);
        }

        // Find max depth to size the layer list
        int maxDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<List<BeltNode>> layers = new ArrayList<>(maxDepth + 1);
        for (int i = 0; i <= maxDepth; i++) layers.add(new ArrayList<>());

        for (BeltNode node : topo) {
            layers.get(depth.getOrDefault(node.nodeId(), 0)).add(node);
        }
        return layers;
    }

    // ── Per-node tick ─────────────────────────────────────────────────────────

    /**
     * Advance a single node's lanes and handle transfers / wraps.
     * This method is the only thing called from worker threads; it touches
     * only data owned by {@code node} and {@code node.outputId()}'s lane
     * (already finished for this tick).
     */
    private static void tickNode(BeltNode node, BeltSubnetwork subnet) {
        if (node.isStopped()) return;

        for (int l = 0; l < 2; l++) {
            BeltLane lane = node.lane(l);

            // 1. Advance items
            lane.advance(1.0f); // 1 tick

            // 2. Handle output
            if (node.isWrapPoint()) {
                // Wrap: re-queue groups that have fully exited
                lane.applyWrap();
            } else if (node.outputId() != null) {
                // Transfer: push exited groups into the output belt's same lane
                BeltNode output = subnet.node(node.outputId());
                if (output != null && !output.isStopped()) {
                    lane.transferOut(output.lane(l));
                }
                // If output is stopped or full, groups stall past 1.0 — that's fine,
                // they just accumulate at the front until there's room.
            }
            // Null outputId and not a wrap-point = terminal (drops or awaits machine pull)
        }
    }
}
