package proto.mechanicalarmory.client.renderer.armor;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForgeMod;
import proto.mechanicalarmory.client.renderer.util.SearchPattern;
import proto.mechanicalarmory.common.items.armor.MyAttachments;

import java.util.List;
import java.util.ArrayList;

import static net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT;

public class OctoSuitLogic {

    // --- Configuration ---
    private static final int TOTAL_ARMS = 4;
    private static final int MAX_MOVING_ARMS = 2;       // Spider Gait: Only 2 move at a time
    private static final int MIN_ANCHORS_TO_FLY = 2;    // Need 2 arms anchored to fly

    private static final float MOVEMENT_SPEED = 0.6f;   // Speed of arm travel
    private static final double MAX_REACH = 9.0;
    private static final double IDEAL_DISTANCE = 5.0;   // How far out the arms float naturally

    private static final double SNAP_DIST_SQR = 0.1 * 0.1;
    private static final double STEP_TRIGGER_DIST_SQR = 16.0; // 4 blocks away -> trigger step

    // Offsets: [Right/Left, Forward/Back]
    // 0=TopRight, 1=TopLeft, 2=BotRight, 3=BotLeft
    private static final double[][] ARM_OFFSETS = {
            { 2.5,  2.0 },
            {-2.5,  2.0 },
            { 3.5, -1.5 },
            {-3.5, -1.5 }
    };

    /**
     * Call this from your Item's inventoryTick method.
     */
    public static void tick(Player player) {
        if (player.level().isClientSide) return;

        // 1. Calculate Basis Vectors
        Vec3 forward = new Vec3(player.getDirection().step().x, player.getDirection().step().y, player.getDirection().step().z);
        Vec3 right = new Vec3 (player.getDirection().getClockWise().step().x, player.getDirection().getClockWise().step().y, player.getDirection().getClockWise().step().z);

        // 2. Load Data (Assumes you have a helper to get/set lists)
        List<Vec3> currentPositions = player.getData(MyAttachments.ARM_TARGETS);
        List<Vec3> lockedDestinations = player.getData(MyAttachments.ARM_DESTINATIONS);

        // Initialization Safety
        if (currentPositions.size() != TOTAL_ARMS) {
            currentPositions = new ArrayList<>();
            lockedDestinations = new ArrayList<>();
            for (int i = 0; i < TOTAL_ARMS; i++) {
                currentPositions.add(player.position());
                lockedDestinations.add(player.position());
            }
        }

        // 3. Count Moving Arms (Gait Control)
        int currentlyMovingCount = 0;
        int anchoredCount = 0;

        for (int i = 0; i < TOTAL_ARMS; i++) {
            if (currentPositions.get(i).distanceToSqr(lockedDestinations.get(i)) > SNAP_DIST_SQR) {
                currentlyMovingCount++;
            } else {
                anchoredCount++;
            }
        }

        // 4. Process Each Arm
        for (int i = 1; i < 2; i++) {
            Vec3 currentPos = currentPositions.get(i);
            Vec3 lockedDest = lockedDestinations.get(i);

            // --- A. Calculate Ideal Air Position ---
            double offX = ARM_OFFSETS[i][0];
            double offZ = ARM_OFFSETS[i][1];

            // Normalize vector to where the arm WANTS to be
            Vec3 idealDir = forward.scale(offZ).add(right.scale(offX)).normalize();
            // The point in the air 5 blocks away
            Vec3 idealAirPoint = player.position().add(0, 1.0, 0).add(idealDir.scale(IDEAL_DISTANCE));

            // --- B. Trigger Logic ---
            double distToDest = currentPos.distanceToSqr(lockedDest);
            boolean isMoving = distToDest > SNAP_DIST_SQR;

            boolean isBehind = isBehindPlayer(player, currentPos);
            boolean isTooFar = currentPos.distanceToSqr(player.position()) > (MAX_REACH * MAX_REACH);
            boolean needsStep = currentPos == player.position() || isBehind || isTooFar || currentPos.distanceToSqr(idealAirPoint) > STEP_TRIGGER_DIST_SQR;

            // --- C. Step & Target Logic ---
            if (needsStep && !isMoving) {
                // GAIT CHECK: Can we join the moving queue?
                if (currentlyMovingCount < MAX_MOVING_ARMS) {

                    // CONE LOGIC: Right arms search Right, Left arms search Left
                    // Indices 0, 2 are Right. Indices 1, 3 are Left.
                    // We flip the 'Right' vector for left arms.
                    Vec3 coneDir = (i % 2 == 0) ? right : right.scale(-1);

                    // SEARCH: Find a solid block in that cone
                    lockedDest = findSolidBlock(player, idealAirPoint, coneDir, 0.5); // 0.5 = ~60 degree cone
                    if (lockedDest == null) {
                        lockedDest = player.position();
                    }
                    currentlyMovingCount++;
                }
            }

            // --- D. Movement ---
            // Using moveTowards for consistent linear logic (Server Side)
            Vec3 newPos = moveTowards(currentPos, lockedDest, MOVEMENT_SPEED);

            currentPositions.set(i, newPos);
            lockedDestinations.set(i, lockedDest);
        }

        // 5. Save Data
        player.setData(MyAttachments.ARM_TARGETS, currentPositions);
        player.setData(MyAttachments.ARM_DESTINATIONS, lockedDestinations);

        // 6. Flight Buff
        updateFlightState(player, anchoredCount >= MIN_ANCHORS_TO_FLY);
    }

    /**
     * Optimized Block Search: Cone + Line of Sight + Sorted Sphere
     */
    private static Vec3 findSolidBlock(Player player, Vec3 idealPoint, Vec3 coneDir, double coneWidth) {
        BlockPos center = player.blockPosition();
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1);

        int checks = 0;

        // Iterate through our pre-sorted onion layers
        for (Vec3i offset : SearchPattern.SORTED_OFFSETS) {

            BlockPos targetPos = center.offset(offset);

            // 1. Cone Check (Math only, cheap)
            Vec3 centerToBlock = new Vec3(targetPos.getX()+0.5, targetPos.getY()+0.5, targetPos.getZ()+0.5).subtract(eyePos).normalize();

            double dot = (viewVec.x * centerToBlock.x) + (viewVec.z * centerToBlock.z);

            // 3. Calculate Cross Product (Left vs Right)
            // Formula: (Ax * Bz) - (Az * Bx)
            double cross = (viewVec.x * centerToBlock.z) - (viewVec.z * centerToBlock.x);

            if (dot < 0) {
                continue; // Skip blocks behind
            }
            if (cross > 0) {
                continue; // Skip blocks to the right
            }

            // 2. Solid Check (Memory access)
            BlockState state = level.getBlockState(targetPos);
            if (!state.isAir() && state.isSolid()) {

                // 3. Line of Sight Check (Raycast, expensive)
                // Prevents grabbing through walls
                Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

                BlockHitResult hit = level.clip(new ClipContext(
                        eyePos, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
                ));

                // If we hit the block we intended to, it's valid
                if (hit.getBlockPos().equals(targetPos)) {
                    return targetCenter;
                }
            }
        }

        // Fallback: Just float in the air
        return null;
    }

    // --- Helpers ---

    private static void updateFlightState(Player player, boolean canFly) {
        boolean isFlying = player.getAttribute(CREATIVE_FLIGHT).getBaseValue() > 0;
        // Only set if changed to avoid attribute syncing spam
        if (canFly && !isFlying) player.getAttribute(CREATIVE_FLIGHT).setBaseValue(1);
        else if (!canFly && isFlying) player.getAttribute(CREATIVE_FLIGHT).setBaseValue(0);
    }

    private static Vec3 moveTowards(Vec3 current, Vec3 target, float maxDelta) {
        Vec3 d = target.subtract(current);
        double distSqr = d.lengthSqr();
        if (distSqr < 0.0001 || (maxDelta >= 0 && distSqr <= maxDelta * maxDelta)) return target;
        double dist = Math.sqrt(distSqr);
        return current.add(d.scale(maxDelta / dist));
    }

    private static boolean isBehindPlayer(Player player, Vec3 target) {
        Vec3 toTarget = target.subtract(player.position()).normalize();
        // Dot < 0 means angle > 90 degrees (Behind)
        // -0.2 provides a bit of buffer so it doesn't snap instantly
        return toTarget.dot(player.getLookAngle()) < -0.2;
    }
}