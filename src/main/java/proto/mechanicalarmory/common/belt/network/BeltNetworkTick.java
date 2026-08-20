package proto.mechanicalarmory.common.belt.network;

import java.util.Collection;

/**
 * Executes simulation ticks for all {@link BeltSubnetwork}s in the level.
 *
 * <h3>Subnetwork-Level Parallelization</h3>
 * Each {@link BeltSubnetwork} is a completely disjoint, connected component of the belt graph.
 * Subnetworks never share nodes, lanes, or mutable state. When multiple subnetworks exist,
 * they are ticked in parallel across the worker pool without any inter-thread contention.
 */
public final class BeltNetworkTick {

    private BeltNetworkTick() {}

    /**
     * Tick all subnetworks in the given {@link BeltNetworkData}.
     * Called once per server level tick from the main thread.
     */
    public static void tickAll(BeltNetworkData data) {
        Collection<BeltSubnetwork> subnets = data.allSubnetworks();
        int size = subnets.size();
        if (size == 0) return;

        if (size == 1) {
            // Single subnetwork: zero thread dispatch overhead, execute directly on caller thread
            for (BeltSubnetwork subnet : subnets) {
                BeltSimulation.tickSubnetwork(subnet);
            }
        } else {
            // Multiple subnetworks: parallelize concurrently across worker threads
            subnets.parallelStream().forEach(BeltSimulation::tickSubnetwork);
        }
    }
}
