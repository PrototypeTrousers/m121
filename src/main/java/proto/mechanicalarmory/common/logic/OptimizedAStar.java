package proto.mechanicalarmory.common.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import java.util.*;

public class OptimizedAStar {

    public static List<BlockPos> findPath(Level level, BlockPos start, BlockPos end) {
        long target = end.asLong();
        
        // PriorityQueue still needs a wrapper, but we only create it for valid candidates
        PriorityQueue<LongNode> openSet = new PriorityQueue<>();
        
        // Use Long-to-Double maps to store costs to avoid Object wrappers for every coordinate
        Map<Long, Double> gCosts = new HashMap<>(); 
        Map<Long, Long> cameFrom = new HashMap<>();

        long startLong = start.asLong();
        openSet.add(new LongNode(startLong, 0, heuristic(startLong, end.asLong())));
        gCosts.put(startLong, 0.0);

        while (!openSet.isEmpty()) {
            LongNode current = openSet.poll();
            long currLong = current.posLong;

            if (currLong == target) {
                return retracePath(cameFrom, currLong);
            }

            // Standard cardinal directions + Up/Down
            for (Direction dir : Direction.values()) {
                // Use the internal long-shifting logic to get neighbors without allocating a new BlockPos
                long neighborLong = BlockPos.offset(currLong, dir);

               //if (!isWalkable(level, neighborLong)) continue;

                double tentativeGCost = gCosts.get(currLong) + 1.0;

                if (tentativeGCost < gCosts.getOrDefault(neighborLong, Double.MAX_VALUE)) {
                    cameFrom.put(neighborLong, currLong);
                    gCosts.put(neighborLong, tentativeGCost);
                    
                    // We only allocate the wrapper here for the PriorityQueue
                    double h = heuristic(neighborLong, target);
                    openSet.add(new LongNode(neighborLong, tentativeGCost, h));
                }
            }
        }
        return Collections.emptyList();
    }

    private static boolean isWalkable(Level level, long posLong) {
        // Use the pooled/mutable BlockPos internally provided by many versions 
        // or just temporary BlockPos.of(long) which is faster than full instantiation
        BlockPos pos = BlockPos.of(posLong);
        return level.getBlockState(pos).isAir() && 
               level.getBlockState(pos.above()).isAir() && 
               level.getBlockState(pos.below()).isSolid();
    }

    private static double heuristic(long packedA, long packedB) {
        // Unpack coordinates manually to avoid BlockPos allocation
        int ax = BlockPos.getX(packedA);
        int ay = BlockPos.getY(packedA);
        int az = BlockPos.getZ(packedA);
        int bx = BlockPos.getX(packedB);
        int by = BlockPos.getY(packedB);
        int bz = BlockPos.getZ(packedB);
        return Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz);
    }

    private static List<BlockPos> retracePath(Map<Long, Long> cameFrom, long current) {
        List<BlockPos> path = new ArrayList<>();
        path.add(BlockPos.of(current));
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(BlockPos.of(current));
        }
        Collections.reverse(path);
        return path;
    }

    private static class LongNode implements Comparable<LongNode> {
        long posLong;
        double fCost;

        LongNode(long posLong, double g, double h) {
            this.posLong = posLong;
            this.fCost = g + h;
        }

        @Override
        public int compareTo(LongNode o) {
            return Double.compare(this.fCost, o.fCost);
        }
    }
}