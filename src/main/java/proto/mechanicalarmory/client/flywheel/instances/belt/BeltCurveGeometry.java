package proto.mechanicalarmory.client.flywheel.instances.belt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.network.BeltSubnetwork;
import proto.mechanicalarmory.common.blocks.BlockBelt;

/**
 * Geometric and trigonometric calculations for conveyor belt rendering along straight runs and 90-degree curves.
 */
public final class BeltCurveGeometry {

    public enum CurveType {
        STRAIGHT,
        CURVE_LEFT,
        CURVE_RIGHT
    }

    private BeltCurveGeometry() {}

    /**
     * Determines whether the belt at {@code pos} forms a curve based on connected inputs.
     */
    public static CurveType getCurveType(BlockPos pos, Direction facing, Level level, BeltSubnetwork subnet) {
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

    public static boolean isBeltFacingInto(BlockPos fromPos, BlockPos toPos, Level level, BeltSubnetwork subnet) {
        BeltNode fromNode = subnet.node(fromPos);
        if (fromNode != null) {
            return fromPos.relative(fromNode.facing()).equals(toPos);
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
     * Maps normalized belt position [0.0, 1.0+] and lane index to 3D world coordinates.
     */
    public static float[] getRenderWorldPos(BlockPos pos, Direction facing, float renderPos, int laneIdx,
                                            int destLaneIdx, CurveType curve, Level level) {
        float cx = pos.getX() + 0.5f;
        float cy = pos.getY() + 0.1f;
        float cz = pos.getZ() + 0.5f;
        float laneOffset = (laneIdx == 0 ? -0.15f : 0.15f);

        // Within this block
        if (renderPos <= 1.0f) {
            if (curve == CurveType.STRAIGHT) {
                float beltT = Math.max(0.0f, renderPos);
                float wx = cx + facing.getStepX() * (beltT - 0.5f) + laneOffset * facing.getClockWise().getStepX();
                float wz = cz + facing.getStepZ() * (beltT - 0.5f) + laneOffset * facing.getClockWise().getStepZ();
                return new float[]{wx, cy, wz};
            } else {
                Direction sideDir = (curve == CurveType.CURVE_RIGHT)
                        ? facing.getClockWise()
                        : facing.getCounterClockWise();

                float radius = (curve == CurveType.CURVE_RIGHT)
                        ? (0.5f - laneOffset)
                        : (0.5f + laneOffset);

                double theta = Math.max(0.0, renderPos) * (Math.PI / 2.0);
                double sinT = Math.sin(theta);
                double cosT = Math.cos(theta);

                float cornerX = cx + 0.5f * facing.getStepX() + 0.5f * sideDir.getStepX();
                float cornerZ = cz + 0.5f * facing.getStepZ() + 0.5f * sideDir.getStepZ();

                float wx = (float) (cornerX - radius * (cosT * facing.getStepX() + sinT * sideDir.getStepX()));
                float wz = (float) (cornerZ - radius * (cosT * facing.getStepZ() + sinT * sideDir.getStepZ()));
                return new float[]{wx, cy, wz};
            }
        }

        // Exceeded exit boundary (renderPos > 1.0f) — project into connected downstream belt
        float excess = renderPos - 1.0f;
        BlockPos nextPos = pos.relative(facing);
        if (level != null) {
            BlockState nextState = level.getBlockState(nextPos);
            if (nextState.getBlock() instanceof BlockBelt) {
                Direction nextFacing = nextState.getValue(BlockBelt.FACING);
                float nextCx = nextPos.getX() + 0.5f;
                float nextCz = nextPos.getZ() + 0.5f;

                if (nextFacing == facing) {
                    // Straight continuation: excess is distance from the junction seam into next block
                    float wx = nextCx + nextFacing.getStepX() * (excess - 0.5f) + laneOffset * nextFacing.getClockWise().getStepX();
                    float wz = nextCz + nextFacing.getStepZ() * (excess - 0.5f) + laneOffset * nextFacing.getClockWise().getStepZ();
                    return new float[]{wx, cy, wz};
                } else {
                    // Next belt is a curve
                    Direction entrySide = facing.getOpposite();
                    CurveType nextCurve = (nextFacing.getClockWise() == entrySide)
                            ? CurveType.CURVE_RIGHT
                            : CurveType.CURVE_LEFT;

                    float nextRadius = (nextCurve == CurveType.CURVE_RIGHT)
                            ? (0.5f - laneOffset)
                            : (0.5f + laneOffset);

                    double theta = Math.min(Math.PI / 2.0, excess / nextRadius);
                    double sinT = Math.sin(theta);
                    double cosT = Math.cos(theta);

                    float cornerX = nextCx + 0.5f * nextFacing.getStepX() + 0.5f * entrySide.getStepX();
                    float cornerZ = nextCz + 0.5f * nextFacing.getStepZ() + 0.5f * entrySide.getStepZ();

                    float wx = (float) (cornerX - nextRadius * (cosT * nextFacing.getStepX() + sinT * entrySide.getStepX()));
                    float wz = (float) (cornerZ - nextRadius * (cosT * nextFacing.getStepZ() + sinT * entrySide.getStepZ()));
                    return new float[]{wx, cy, wz};
                }
            }
        }

        // Default: straight linear projection
        float wx = cx + facing.getStepX() * (renderPos - 0.5f) + laneOffset * facing.getClockWise().getStepX();
        float wz = cz + facing.getStepZ() * (renderPos - 0.5f) + laneOffset * facing.getClockWise().getStepZ();
        return new float[]{wx, cy, wz};
    }
}
