package proto.mechanicalarmory.client.renderer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import java.util.*;

public class ArmPathfinder {

    // Simple Node class for A*
    private record Node(BlockPos pos, Node parent, double gCost, double hCost) implements Comparable<Node> {
        public double fCost() { return gCost + hCost; }
        
        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fCost(), other.fCost());
        }
    }

    /**
     * Finds a path of AIR blocks from Start to End.
     * Returns a list of center points (Vec3) for the spline to follow.
     */
    public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos end, int maxNodes) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Long> closedSet = new HashSet<>();
        
        // Initial Node
        openSet.add(new Node(start, null, 0, start.distSqr(end)));
        
        // Track best node in case we don't reach the target (fail-safe)
        Node bestNode = openSet.peek();
        int processed = 0;

        while (!openSet.isEmpty() && processed < maxNodes) {
            Node current = openSet.poll();
            processed++;

            // Update "Best" (closest to target) just in case we timeout
            if (current.hCost < bestNode.hCost) {
                bestNode = current;
            }

            // SUCCESS: Reached target
            if (current.pos.equals(end) || current.pos.distSqr(end) < 2.0) {
                return retracePath(current);
            }

            closedSet.add(current.pos.asLong());

            // Check Neighbors (6 directions)
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        // Skip center and diagonals (Manhattan movement is safer for arms)
                        if (Math.abs(x) + Math.abs(y) + Math.abs(z) != 1) continue;

                        BlockPos neighborPos = current.pos.offset(x, y, z);

                        if (closedSet.contains(neighborPos.asLong())) continue;

                        // COLLISION CHECK: Only allow movement through Air or Replaceable blocks
                        // We also allow the 'end' block even if it's solid (so we can latch onto it)
                        if (!neighborPos.equals(end) && !level.getBlockState(neighborPos).canBeReplaced()) {
                            continue;
                        }

                        double newCost = current.gCost + 1; // Distance is always 1 for neighbors
                        Node neighborNode = new Node(neighborPos, current, newCost, neighborPos.distSqr(end));
                        openSet.add(neighborNode);
                    }
                }
            }
        }

        // Failure or Timeout: Return path to the closest point we found
        return retracePath(bestNode);
    }

    private static List<BlockPos> retracePath(Node endNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = endNode;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }
}