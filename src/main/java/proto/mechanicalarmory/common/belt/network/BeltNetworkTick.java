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
 * When processing layer {@code L}, we write to each node's own lanes and to
 * lanes of its (already-finished, layer-(L−1)) output node. When two nodes in
 * the same layer share an output node (a merge point), each is side-loaded
 * onto exactly one of that node's two lanes (Factorio-style — see
 * {@link proto.mechanicalarmory.common.belt.data.BeltNode#addInput}) and only
 * ever reads/writes that lane, so the two workers touch disjoint
 * {@link BeltLane} instances even when writing into the same node. Workers
 * never touch world state; speeds are cached on
 * {@link proto.mechanicalarmory.common.belt.data.BeltNode} by
 * {@link BeltNetworkData#updateCurveSpeeds} and are updated only on topology
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
        List<List<BeltNode>> layers = subnet.layerOrder();
        if (layers.isEmpty()) return;

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
     * Advance a single node's lanes and handle transfers.
     *
     * <h4>Side-loading and thread safety</h4>
     * A node's two source lanes both feed forward into the <em>single</em>
     * lane of the downstream node that this node is side-loaded onto (see
     * {@link BeltNode#addInput}) — not into "the same-numbered lane" of the
     * output. Each upstream node owns exactly one lane of its output node and
     * never touches the other, so when two nodes in the same topo layer merge
     * into a shared output node, they write to disjoint {@link BeltLane}
     * instances. That disjointness — not just "output is a finished layer" —
     * is what makes the layer-parallel tick race-free at merge points.
     */
    private static void tickNode(BeltNode node, BeltSubnetwork subnet) {
        if (node.isStopped()) return;

        BeltNode output = null;
        int mergedInputLane = -1;   // this node's assigned lane on `output`, if it is one of >1 inputs
        boolean isSoleInput = true; // true when `node` is output's only input (no side-loading collapse)
        if (node.outputId() != null) {
            BeltNode candidate = subnet.node(node.outputId());
            if (candidate != null && !candidate.isStopped()) {
                mergedInputLane = candidate.laneForInput(node.nodeId());
                if (mergedInputLane >= 0) {
                    output = candidate;
                    isSoleInput = candidate.inputIds().size() <= 1;
                }
                // mergedInputLane < 0 means the topology says we point at this
                // node but we're not registered on either of its lanes (stale
                // edge, e.g. mid-relink) — treat as no output this tick.
            }
        }

        for (int l = 0; l < 2; l++) {
            BeltLane lane = node.lane(l);

            // Straight-through (no merge at the output): preserve left/right
            // identity, lane[l] -> output.lane[l]. Actual merge (output has 2
            // inputs feeding it): Factorio-style side-loading collapses both
            // of this node's lanes onto the single lane it's assigned to.
            BeltLane outLane = output == null ? null
                    : output.lane(isSoleInput ? l : mergedInputLane);

            float maxExitPos = 1.0f;
            if (outLane != null) {
                if (outLane.isEmpty()) {
                    maxExitPos = Float.MAX_VALUE;
                } else {
                    float outRoom = outLane.peekLast().tailPos(outLane.itemSpacing());
                    maxExitPos = 1.0f + outRoom * (lane.itemSpacing() / outLane.itemSpacing());
                }
            }

            // 1. Advance items (clamped to maxExitPos)
            lane.advance(1.0f, maxExitPos);

            // 2. Handle output
            if (outLane != null) {
                lane.transferOut(outLane);
            }
        }
    }
}
