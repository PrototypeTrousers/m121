package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.blocks.BlockBelt;

import javax.annotation.Nullable;
import java.util.function.BiConsumer;

/**
 * Shared graph topology, neighbor connectivity, and curve detection routines
 * used uniformly by both server simulation and client visual prediction.
 */
public final class BeltGraphHelper {

    public enum CurveType {
        STRAIGHT,
        CURVE_LEFT,
        CURVE_RIGHT
    }

    private BeltGraphHelper() {}

    /**
     * Determines whether the belt at {@code pos} forms a curve based on connected input belts.
     */
    public static CurveType getCurveType(BlockPos pos, Direction facing, Level level, @Nullable BeltSubnetwork subnet) {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean hasBack = isBeltFacingInto(pos.relative(back), pos, level, subnet);
        boolean hasLeft = isBeltFacingInto(pos.relative(left), pos, level, subnet);
        boolean hasRight = isBeltFacingInto(pos.relative(right), pos, level, subnet);

        if (!hasBack) {
            if (hasRight && !hasLeft) {
                return CurveType.CURVE_RIGHT;
            } else if (hasLeft && !hasRight) {
                return CurveType.CURVE_LEFT;
            }
        }
        return CurveType.STRAIGHT;
    }

    /**
     * Checks whether a belt at {@code fromPos} is facing into {@code toPos}.
     */
    public static boolean isBeltFacingInto(BlockPos fromPos, BlockPos toPos, Level level, @Nullable BeltSubnetwork subnet) {
        if (subnet != null) {
            BeltNode fromNode = subnet.node(fromPos);
            if (fromNode != null) {
                return fromPos.relative(fromNode.facing()).equals(toPos);
            }
        }
        if (level != null) {
            BlockState state = level.getBlockState(fromPos);
            if (state.getBlock() instanceof BlockBelt) {
                return fromPos.relative(state.getValue(BlockBelt.FACING)).equals(toPos);
            }
        }
        return false;
    }

    /**
     * Recalculates and updates lane speeds for a belt node based on its curve geometry.
     */
    public static void updateCurveSpeeds(BeltNode node, Level level, @Nullable BeltSubnetwork subnet) {
        if (node == null) return;
        CurveType curve = getCurveType(node.pos(), node.facing(), level, subnet);
        switch (curve) {
            case CURVE_RIGHT -> {
                node.lane(0).setSpeed(BeltLane.SPEED_OUTER);
                node.lane(1).setSpeed(BeltLane.SPEED_INNER);
            }
            case CURVE_LEFT -> {
                node.lane(0).setSpeed(BeltLane.SPEED_INNER);
                node.lane(1).setSpeed(BeltLane.SPEED_OUTER);
            }
            case STRAIGHT -> {
                node.lane(0).setSpeed(BeltLane.SPEED_DEFAULT);
                node.lane(1).setSpeed(BeltLane.SPEED_DEFAULT);
            }
        }
    }

    /**
     * Scans adjacent horizontal blocks to link forward outputs and incoming inputs.
     */
    public static void linkNeighbours(BlockPos pos, Direction facing, Level level,
                                      BiConsumer<BlockPos, BlockPos> linkAction) {
        if (level == null) return;

        // 1. Forward output neighbor
        BlockPos outPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outPos);
        if (outState.getBlock() instanceof BlockBelt) {
            linkAction.accept(pos, outPos);
        }

        // 2. Incoming horizontal input neighbors
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == facing) continue;
            BlockPos inPos = pos.relative(d);
            BlockState inState = level.getBlockState(inPos);
            if (inState.getBlock() instanceof BlockBelt) {
                Direction inFacing = inState.getValue(BlockBelt.FACING);
                if (inFacing == d.getOpposite()) {
                    linkAction.accept(inPos, pos);
                }
            }
        }
    }

    /**
     * Disconnects any neighbor belts that were feeding into a broken / removed belt block.
     */
    public static void unlinkIncomingNeighbours(BlockPos pos, Level level, BeltSubnetworkRegistry registry) {
        if (level == null) return;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(d);
            if (!registry.isTracked(neighborPos)) continue;
            BlockState nState = level.getBlockState(neighborPos);
            if (nState.getBlock() instanceof BlockBelt) {
                Direction nFacing = nState.getValue(BlockBelt.FACING);
                if (neighborPos.relative(nFacing).equals(pos)) {
                    registry.unlink(neighborPos);
                }
            }
        }
    }
}
