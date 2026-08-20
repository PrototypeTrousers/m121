package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.Direction;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;

import java.util.List;

/**
 * Pure simulation logic for belt networks.
 *
 * <p>Shared between server tick ({@link BeltNetworkTick}) and client visual prediction
 * ({@link proto.mechanicalarmory.client.flywheel.instances.belt.BeltSubnetworkVisual})
 * to ensure 100% deterministic and synchronized behavior without code duplication.
 */
public final class BeltSimulation {

    private BeltSimulation() {}

    /**
     * Executes one simulation tick for an entire subnetwork in topological order (downstream first).
     */
    public static void tickSubnetwork(BeltSubnetwork subnet) {
        List<List<BeltNode>> layers = subnet.layerOrder();
        if (layers.isEmpty()) return;

        // Downstream layers (Layer 0) execute first, clearing space for upstream layers.
        for (List<BeltNode> layer : layers) {
            for (BeltNode node : layer) {
                tickNode(node, subnet);
            }
        }
    }

    /**
     * Advance a single node's lanes and handle transfers to downstream neighbors.
     */
    public static void tickNode(BeltNode node, BeltSubnetwork subnet) {
        if (node.isStopped()) return;

        BeltNode outNode = null;
        if (node.outputPos() != null) {
            BeltNode candidate = subnet.node(node.outputPos());
            if (candidate != null && !candidate.isStopped()) {
                outNode = candidate;
            }
        }

        for (int l = 0; l < 2; l++) {
            BeltLane lane = node.lane(l);
            BeltLane outLane = null;

            if (outNode != null) {
                int destLaneIdx = getDestinationLane(node, outNode, l);
                outLane = outNode.lane(destLaneIdx);
            }

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

            // 2. Transfer into output lane if room is available
            if (outLane != null) {
                lane.transferOut(outLane);
            }
        }
    }

    /**
     * Resolves which downstream lane a source lane feeds into.
     *
     * <p>A perpendicular connection is a continuous 90-degree curve when {@code toNode}
     * has only a single input (preserving lane 0 -> 0 and lane 1 -> 1). It is a side-load
     * merge only when {@code toNode} has multiple inputs (e.g. straight line + side belt).
     *
     * @param fromNode   source belt node
     * @param toNode     destination belt node
     * @param sourceLane lane index (0=left, 1=right) on the source belt
     * @return destination lane index (0=left, 1=right)
     */
    public static int getDestinationLane(BeltNode fromNode, BeltNode toNode, int sourceLane) {
        Direction fromFacing = fromNode.facing();
        Direction toFacing = toNode.facing();

        if (fromFacing == toFacing) {
            // Straight continuation preserves lane parity (left -> left, right -> right)
            return sourceLane;
        }

        // Perpendicular connection: if toNode has only this input, it is a pure curve
        if (toNode.inputCount() <= 1) {
            // Curve maintains lane parity (outer -> outer, inner -> inner)
            return sourceLane;
        }

        // True side-load (T-junction merge point):
        if (fromFacing == toFacing.getClockWise()) {
            // Feeds from the left side of the destination belt -> drops onto left lane (0)
            return 0;
        } else if (fromFacing == toFacing.getCounterClockWise()) {
            // Feeds from the right side of the destination belt -> drops onto right lane (1)
            return 1;
        }

        return sourceLane;
    }
}
