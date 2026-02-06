package proto.mechanicalarmory.client.renderer.util;

import it.unimi.dsi.fastutil.longs.LongArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

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
    public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos end, Vector3f anchorPos, int maxNodes) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        // Use a Map to track the best gCost found for each position
        Map<Long, Double> visitedGCosts = new HashMap<>();

        // Use Euclidean distance for hCost
        openSet.add(new Node(start, null, 0, Math.sqrt(start.distSqr(end))));
        visitedGCosts.put(start.asLong(), 0.0);

        Node bestNode = openSet.peek();
        int processed = 0;

        while (!openSet.isEmpty() && processed < maxNodes) {
            Node current = openSet.poll();
            processed++;

            if (current.hCost < bestNode.hCost) {
                bestNode = current;
            }

            // SUCCESS
            if (current.pos.equals(end)) {
                return retracePath(current);
            }

            for (Direction dir : Direction.values()) { // cleaner than triple-nested loop
                BlockPos neighborPos = current.pos.relative(dir);
                long neighborLong = neighborPos.asLong();

                // Collision Check
                if (!neighborPos.equals(end) && !level.getBlockState(neighborPos).canBeReplaced()) {
                    continue;
                }

                // G-Cost is distance from start. H-Cost is estimated distance to end.
                double newGCost = current.gCost + 1;

                // If we've seen this block before with a better or equal cost, skip it
                if (visitedGCosts.containsKey(neighborLong) && visitedGCosts.get(neighborLong) <= newGCost) {
                    continue;
                }

                // Heuristic: Straight line distance to goal
                double h = Math.sqrt(neighborPos.distSqr(end));

                // Optional: Soft constraint to keep arm near anchor
                // We add this to gCost so it's a "penalty" for moving far away
                double anchorDist = Math.sqrt(neighborPos.distSqr(new BlockPos((int)anchorPos.x, (int)anchorPos.y, (int)anchorPos.z)));
                double penalty = anchorDist * 0.05;

                Node neighborNode = new Node(neighborPos, current, newGCost + penalty, h);

                visitedGCosts.put(neighborLong, newGCost);
                openSet.add(neighborNode);
            }
        }

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
        return  path;
    }
}