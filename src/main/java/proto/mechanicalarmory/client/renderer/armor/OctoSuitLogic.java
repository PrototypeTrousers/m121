package proto.mechanicalarmory.client.renderer.armor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import proto.mechanicalarmory.client.renderer.util.SnakeSolver;
import proto.mechanicalarmory.client.renderer.util.SearchPattern;
import proto.mechanicalarmory.common.items.armor.MyAttachments;

import java.util.ArrayList;
import java.util.List;

import static net.neoforged.neoforge.common.NeoForgeMod.CREATIVE_FLIGHT;
import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class OctoSuitLogic {

    public static AttributeModifier flight = new AttributeModifier(ResourceLocation.fromNamespaceAndPath(MODID,"octosuit_flight"), 1, AttributeModifier.Operation.ADD_VALUE);

    // --- Configuration ---
    private static final int TOTAL_ARMS = 4;
    private static final int MAX_MOVING_ARMS = 2;       // Spider Gait: Only 2 move at a time
    private static final int MIN_ANCHORS_TO_FLY = 2;    // Need 2 arms anchored to fly

    private static final float MOVEMENT_SPEED = 5.8f;   // Speed of arm travel
    private static final double MAX_REACH = 9.0;
    private static final double IDEAL_DISTANCE = 5.0;   // How far out the arms float naturally

    private static final double SNAP_DIST_SQR = 0.1 * 0.1;
    private static final double STEP_TRIGGER_DIST_SQR = 16.0; // 4 blocks away -> trigger step

    /**
     * Call this from your Item's inventoryTick method.
     */
    public static void tick(Player player) {
        if (player.level().isClientSide) return;

        List<Vec3> currentPositions = player.getData(MyAttachments.ARM_TARGETS);
        List<Vec3> lockedDestinations = player.getData(MyAttachments.ARM_DESTINATIONS);

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
        for (int i = 0; i < 4; i++) {
            Vec3 currentPos = currentPositions.get(i);
            Vec3 lockedDest = lockedDestinations.get(i);

            // --- B. Trigger Logic ---
            double distToDest = currentPos.distanceToSqr(lockedDest);
            boolean isMoving = distToDest > SNAP_DIST_SQR;

            boolean isBehind = isBehindPlayer(player, currentPos);
            boolean isTooFar = currentPos.distanceToSqr(player.position()) > (MAX_REACH * MAX_REACH);
            boolean needsStep = currentPos == player.position() || isBehind || isTooFar;
            if (player.level().getBlockState(
                    new BlockPos((int) currentPos.x, (int) currentPos.y, (int) currentPos.z)).equals(Blocks.AIR.defaultBlockState())) {
                needsStep = true;
            }

//            // --- C. Step & Target Logic ---
            if (needsStep && !isMoving) {
                // GAIT CHECK: Can we join the moving queue?
                if (currentlyMovingCount < MAX_MOVING_ARMS) {

//                  SEARCH: Find a solid block in that cone
                    lockedDest = findSolidBlock(player, i % 2 == 0); // 0.5 = ~60 degree cone

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
        updateFlightState(player, anchoredCount > 1);
    }

    /**
     * Optimized Block Search: Cone + Line of Sight + Sorted Sphere
     */
    private static Vec3 findSolidBlock(Player player, boolean isLeft) {
        BlockPos center = player.blockPosition();
        Level level = player.level();
        Vec3 eyePos = player.getEyePosition();
        Vec3 viewVec = player.getViewVector(1);

        // Iterate through our pre-sorted onion layers
        for (Vec3i offset : SearchPattern.OUTER_SHELL_7) {

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
            if (cross < 0 && !isLeft) {
                continue; // Skip blocks to the right
            } else if (cross > 0 && isLeft) {
                continue;
            }
            // 2. Solid Check (Memory access)
            ChunkAccess lc = level.getChunkSource().getChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4, ChunkStatus.FULL, false);
            int si = lc.getSectionIndex(targetPos.getY() >> 4);
            LevelChunkSection section = lc.getSection(si);

            int localX = targetPos.getX() & 15;
            int localY = targetPos.getY() & 15;
            int localZ = targetPos.getZ() & 15;

            BlockState state = section.getBlockState(localX, localY, localZ);

            if (!state.isAir() && state.isSolid()) {

                // 3. Line of Sight Check (Raycast, expensive)
                // Prevents grabbing through walls
                Vec3 targetCenter = new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);

                BlockHitResult hit = level.clip(new ClipContext(
                        eyePos, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player
                ));

                // If we hit the block we intended to, it's valid
                //if (hit.getBlockPos().equals(targetPos)) {
                    return targetCenter;
                //}
            }
        }

        // Fallback: Just float in the air
        return null;
    }

    private static void updateFlightState(Player player, boolean canFly) {
        boolean isFlying = player.getAttribute(CREATIVE_FLIGHT).getValue() > 0;
        // Only set if changed to avoid attribute syncing spam
        int i =2;
        if (canFly && !isFlying){
            if (!player.getAttribute(CREATIVE_FLIGHT).hasModifier(flight.id())) {
                player.getAttribute(CREATIVE_FLIGHT).addTransientModifier(flight);
            }
        }
        else if (!canFly && isFlying) player.getAttribute(CREATIVE_FLIGHT).removeModifier(flight);
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

    public record Segment(Vector3f pos, float length, Vector3f direction){}

    static class Arm {
        static Vector3f UP = new Vector3f(0, 1, 0);
        static Vector3f DOWN = new Vector3f(0, -1, 0);
        static Vector3f LEFT = new Vector3f(-1, 0, 0);
        static Vector3f RIGHT = new Vector3f(1, 0, 0);

        public static Arm TOP_LEFT_ARM = new Arm(16, 0.6f, UP);
        public static Arm TOP_RIGHT_ARM = new Arm(16, 0.6f, UP);
        public static Arm BOTTOM_LEFT_ARM = new Arm(16, 0.6f, LEFT);
        public static Arm BOTTOM_RIGHT_ARM = new Arm(16, 0.6f, RIGHT);

        List<Segment> segments = new ArrayList<>();

        Arm(int amount, float length, Vector3f direction) {
            for ( int i = 0; i < amount; i++) {
                segments.add(new Segment(direction.mul(length * amount, new Vector3f()), length, direction));
            }
        }
    }
}