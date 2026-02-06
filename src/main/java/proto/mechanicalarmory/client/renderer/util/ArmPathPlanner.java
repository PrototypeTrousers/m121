package proto.mechanicalarmory.client.renderer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class ArmPathPlanner {

    // The current active path for the solver to render
    public final List<Vector3f> currentPath = new ArrayList<>();
    public final List<Vector3f> previousPath = new ArrayList<>();

    // State tracking to prevent running A* every frame
    private BlockPos lastTargetBlock = BlockPos.ZERO;

    public void update(Vector3f currentPos, Vector3f targetPos, Vector3f anchorPos, Level level) {
        BlockPos targetBlock = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
        BlockPos current = new BlockPos((int)currentPos.x, (int)currentPos.y, (int)currentPos.z);

        // 1. Check if we need to re-calculate the path
        // We only run A* if the target BLOCK has changed
        //if (!targetBlock.equals(lastTargetBlock) || currentPath.isEmpty() || shoulderBlock) {
            lastTargetBlock = targetBlock;
            recalculatePath(level, current, targetBlock, currentPos, targetPos, anchorPos);
       // }

        // 2. Dynamic Update (Pinning)
        // Even if the path is cached, the entity (shoulder) and the exact sub-block target
        // might have moved slightly. We must update the endpoints every frame.
        if (!currentPath.isEmpty()) {
            currentPath.set(0, currentPos); // Pin start to moving player
            
            // Optional: If you want the arm to stick to the exact hit vector (e.g. hitting the side of a block)
            // update the end point too.
            currentPath.set(currentPath.size() - 1, targetPos);
        }
    }

    private void recalculatePath(Level level, BlockPos startBlock, BlockPos endBlock, Vector3f exactStart, Vector3f exactEnd, Vector3f anchorPos) {
        // Run the heavy A* logic
        List<BlockPos> blockPath = ArmPathfinder.findPath(level, startBlock, endBlock, anchorPos, 200);
        previousPath.clear();
        previousPath.addAll(currentPath);
        currentPath.clear();

        if (blockPath.isEmpty() || blockPath.size() < 2) {
            // Fallback: Direct line
            currentPath.add(exactStart);
            currentPath.add(exactEnd);
        } else {
            // Convert blocks to vectors (centered)
            for (BlockPos p : blockPath) {
                Vector3f v = new Vector3f(p.getX() + 0.5f, p.getY() + 0.5f, p.getZ() + 0.5f);
                if (!previousPath.isEmpty()) {
                    if (previousPath.removeLast().equals(v)) {
                        continue;
                    }
                }
                currentPath.add(v);
            }
            // Overwrite endpoints with precise positions
            currentPath.set(0, exactStart);
            currentPath.set(currentPath.size() - 1, exactEnd);
        }
    }
}