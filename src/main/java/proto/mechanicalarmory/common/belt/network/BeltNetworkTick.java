package proto.mechanicalarmory.common.belt.network;

import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// NOTE: This class deliberately has no ServerLevel access.
// Lane speeds (curve/straight) are cached on BeltNode by
// BeltNetworkData.updateCurveSpeeds(), which is called on every
// topology change (place, remove, link).  The tick is pure data.

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
 * finished</em> for this tick, so there are no write–write conflicts. Workers never touch world state; speeds are cached on {@link proto.mechanicalarmory.common.belt.data.BeltNode}
 * by {@link BeltNetworkData#updateCurveSpeeds} and are updated only on topology
 * changes (place / remove / link), not during the tick.
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
    public static void tickAll(BeltNetworkData data) {
        for (BeltSubnetwork subnet : data.allSubnetworks()) {
            tickSubnetwork(subnet);
        }
    }

    // ── Per-subnetwork tick ───────────────────────────────────────────────────

    private static void tickSubnetwork(BeltSubnetwork subnet) {
        List<BeltNode> topo = subnet.topoOrder();
        if (topo.isEmpty()) return;

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
        java.util.Map<java.util.UUID, Integer> depth = new java.util.HashMap<>();

        for (BeltNode node : topo) {
            int myDepth = 0;
            java.util.UUID outId = node.outputId();
            if (outId != null && depth.containsKey(outId)) {
                myDepth = depth.get(outId) + 1;
            }
            depth.put(node.nodeId(), myDepth);
        }

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
     * Advance a single node's lanes and handle transfers.
     */
    private static void tickNode(BeltNode node, BeltSubnetwork subnet) {
        if (node.isStopped()) return;

        boolean hasOutput = false;
        BeltNode output = null;
        if (node.outputId() != null) {
            output = subnet.node(node.outputId());
            if (output != null && !output.isStopped()) {
                hasOutput = true;
            }
        }

        for (int l = 0; l < 2; l++) {
            BeltLane lane = node.lane(l);

            // 1. Advance items (clamped to 1.0f if terminal or output is stopped)
            lane.advance(1.0f, hasOutput ? Float.MAX_VALUE : 1.0f);

            // 2. Handle output
            if (hasOutput && output != null) {
                lane.transferOut(output.lane(l));
            }
        }
    }
}
