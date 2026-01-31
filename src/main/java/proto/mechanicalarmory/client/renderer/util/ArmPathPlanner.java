package proto.mechanicalarmory.client.renderer.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class ArmPathPlanner {

    // The current active path for the solver to render
    public final List<Vector3f> currentPath = new ArrayList<>();
    
    // State tracking to prevent running A* every frame
    private BlockPos lastTargetBlock = BlockPos.ZERO;

    /**
     * Updates the path logic.
     * @param shoulderPos The exact world position of the arm's base (shoulder).
     * @param targetPos   The exact world position the arm is trying to reach.
     * @param level       The level (for block checks).
     */
    public void update(Vector3f shoulderPos, Vector3f targetPos, Level level) {
        BlockPos targetBlock = new BlockPos((int)targetPos.x, (int)targetPos.y, (int)targetPos.z);
        BlockPos shoulderBlock = new BlockPos((int)shoulderPos.x, (int)shoulderPos.y, (int)shoulderPos.z);

        // 1. Check if we need to re-calculate the path
        // We only run A* if the target BLOCK has changed
        if (!targetBlock.equals(lastTargetBlock) || currentPath.isEmpty()) {
            lastTargetBlock = targetBlock;
            recalculatePath(level, shoulderBlock, targetBlock, shoulderPos, targetPos);
        }

        // 2. Dynamic Update (Pinning)
        // Even if the path is cached, the entity (shoulder) and the exact sub-block target
        // might have moved slightly. We must update the endpoints every frame.
        if (!currentPath.isEmpty()) {
            currentPath.set(0, shoulderPos); // Pin start to moving player
            
            // Optional: If you want the arm to stick to the exact hit vector (e.g. hitting the side of a block)
            // update the end point too.
            currentPath.set(currentPath.size() - 1, targetPos);
        }
    }

    private void recalculatePath(Level level, BlockPos startBlock, BlockPos endBlock, Vector3f exactStart, Vector3f exactEnd) {
        // Run the heavy A* logic
        List<BlockPos> blockPath = ArmPathfinder.findPath(level, startBlock, endBlock, 200);

        currentPath.clear();

        if (blockPath.isEmpty() || blockPath.size() < 2) {
            // Fallback: Direct line
            currentPath.add(exactStart);
            currentPath.add(exactEnd);
        } else {
            // Convert blocks to vectors (centered)
            for (BlockPos p : blockPath) {
                currentPath.add(new Vector3f(p.getX() + 0.5f, p.getY() + 0.5f, p.getZ() + 0.5f));
            }
            // Overwrite endpoints with precise positions
            currentPath.set(0, exactStart);
            currentPath.set(currentPath.size() - 1, exactEnd);
        }
    }
}