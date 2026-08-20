package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.blocks.BlockBelt;
import proto.mechanicalarmory.common.network.BeltCorrectionPayload;
import proto.mechanicalarmory.common.network.BeltInitPayload;

import java.util.*;

/**
 * {@link SavedData} that owns the entire belt network for a level.

 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Retrieved via {@link #get(ServerLevel)} — created lazily.</li>
 *   <li>Ticked by {@code LevelTickEvent} every server tick.</li>
 *   <li>Belts added/removed via {@link #onBeltPlaced} / {@link #onBeltRemoved}.</li>
 *   <li>Chunk load: {@link #onChunkLoaded} sends {@link BeltInitPayload} to
 *       nearby clients.</li>
 * </ul>
 */
public final class BeltNetworkData extends SavedData {

    public static final String KEY = "mechanicalarmory_belt_network";

    /**
     * Owns all subnetworks and the BlockPos/UUID lookup maps, plus the
     * create/merge/link/split mutation rules shared with the client-side
     * manager. See {@link BeltSubnetworkRegistry} for why this is a shared
     * class rather than logic duplicated on each side.
     */
    private final BeltSubnetworkRegistry registry = new BeltSubnetworkRegistry();

    // ── Factory ───────────────────────────────────────────────────────────────

    private static final SavedData.Factory<BeltNetworkData> FACTORY =
            new SavedData.Factory<>(BeltNetworkData::new, BeltNetworkData::load);

    public static BeltNetworkData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, KEY);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    /**
     * Called once per server level tick.  Advances all belt lanes.
     * Marks dirty so NeoForge persists it when the world saves.
     */
    public void tick(ServerLevel level) {
        BeltNetworkTick.tickAll(this);
        setDirty();
    }

    // ── Belt placement / removal ───────────────────────────────────────────────

    /**
     * Register a newly placed belt block.  Links it to any adjacent belt that
     * it faces or that faces it, merging subnetworks as needed.
     */
    public void onBeltPlaced(BlockPos pos, Direction facing, ServerLevel level) {
        BeltNode node = new BeltNode(pos, facing);
        boolean powered = level.getBlockState(pos).getValue(BlockBelt.POWERED);
        node.setStopped(powered);

        // Create a fresh subnetwork for this node
        registry.registerSolo(node);

        // Merge with neighbours and link edges
        relinkNeighbours(pos, facing, level);
        updateCurveSpeeds(pos, level);
        sendCorrection(pos, level);
        setDirty();
    }

    /**
     * Unregister a belt block that was removed.  Splits the owning subnetwork
     * if needed.
     */
    public void onBeltRemoved(BlockPos pos, ServerLevel level) {
        BeltNode node = registry.nodeAt(pos);
        BeltSubnetwork owning = registry.subnetworkAt(pos);

        List<BlockPos> affected = new ArrayList<>();
        if (node != null && owning != null) {
            if (node.outputPos() != null) {
                BeltNode out = owning.node(node.outputPos());
                if (out != null) affected.add(out.pos());
            }
            for (BlockPos inPos : node.inputPositions()) {
                BeltNode in = owning.node(inPos);
                if (in != null) affected.add(in.pos());
            }
        }

        registry.removeNode(pos);

        for (BlockPos affPos : affected) {
            updateCurveSpeeds(affPos, level);
            sendCorrection(affPos, level);
        }

        setDirty();
    }

    /**
     * Called when a belt's redstone state changes.
     * Sends a correction payload so the client can freeze/resume simulation.
     */
    public void onBeltStopped(BlockPos pos, boolean stopped, ServerLevel level) {
        BeltNode node = nodeAt(pos);
        if (node == null) return;
        node.setStopped(stopped);
        sendCorrection(pos, level);
        setDirty();
    }

    // ── Chunk load ────────────────────────────────────────────────────────────

    /**
     * Called when a chunk loads.  For every belt in that chunk, send the
     * current lane state to nearby clients so they can seed their simulations
     */
    public void onChunkLoaded(ChunkPos chunkPos, ServerLevel level) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        List<BeltInitPayload.NodeSnapshot> snapshots = new ArrayList<>();

        for (BlockPos bpos : registry.trackedPositions()) {
            if (bpos.getX() < minX || bpos.getX() > maxX) continue;
            if (bpos.getZ() < minZ || bpos.getZ() > maxZ) continue;

            BeltNode node = registry.nodeAt(bpos);
            if (node == null) continue;

            snapshots.add(new BeltInitPayload.NodeSnapshot(
                    bpos,
                    node.facing(),
                    node.outputPos(),
                    node.lane(0).deepCopy(),
                    node.lane(1).deepCopy(),
                    node.outputPos() != null,
                    node.isStopped()
            ));
        }

        if (!snapshots.isEmpty()) {
            long tick = level.getGameTime();
            BeltInitPayload pkt = new BeltInitPayload(snapshots, tick);
            PacketDistributor.sendToPlayersNear(level,
                    null,
                    (minX + maxX) / 2.0, 64, (minZ + maxZ) / 2.0,
                    128,
                    pkt);
        }
    }

    /**
     * Called when a player starts watching a chunk (ChunkWatchEvent.Watch).
     * Sends the current state of all belts in the chunk directly to that player.
     */
    public void sendChunkInitToPlayer(ChunkPos chunkPos, ServerPlayer player) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        List<BeltInitPayload.NodeSnapshot> snapshots = new ArrayList<>();

        for (BlockPos bpos : registry.trackedPositions()) {
            if (bpos.getX() < minX || bpos.getX() > maxX) continue;
            if (bpos.getZ() < minZ || bpos.getZ() > maxZ) continue;

            BeltNode node = registry.nodeAt(bpos);
            if (node == null) continue;

            snapshots.add(new BeltInitPayload.NodeSnapshot(
                    bpos,
                    node.facing(),
                    node.outputPos(),
                    node.lane(0).deepCopy(),
                    node.lane(1).deepCopy(),
                    node.outputPos() != null,
                    node.isStopped()
            ));
        }

        if (!snapshots.isEmpty()) {
            long tick = player.serverLevel().getGameTime();
            BeltInitPayload pkt = new BeltInitPayload(snapshots, tick);
            PacketDistributor.sendToPlayer(player, pkt);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @Nullable
    public BeltNode nodeAt(BlockPos pos) { return registry.nodeAt(pos); }

    public Collection<BeltSubnetwork> allSubnetworks() { return registry.allSubnetworks(); }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * After placing a belt at {@code pos} facing {@code facing}, scan the four
     * horizontal neighbours to create edges and merge subnetworks.
     */
    private void relinkNeighbours(BlockPos pos, Direction facing, ServerLevel level) {
        // 1. The belt at pos outputs in the direction it faces
        BlockPos outputPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outputPos);
        if (outState.getBlock() instanceof BlockBelt) {
            mergeAndLink(pos, outputPos, level);
        }

        // 2. Belts that face toward pos feed into it
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d == facing) continue;
            BlockPos neighbourPos = pos.relative(d);
            BlockState state = level.getBlockState(neighbourPos);
            if (state.getBlock() instanceof BlockBelt) {
                Direction neighbourFacing = state.getValue(BlockBelt.FACING);
                if (neighbourFacing == d.getOpposite()) {
                    mergeAndLink(neighbourPos, pos, level);
                }
            }
        }
    }

    /**
     * Merge the subnetwork owning {@code toPos} into the one owning {@code fromPos},
     * then link the edge fromPos→toPos and notify clients.
     *
     * <p>The actual graph mutation is delegated to {@link BeltSubnetworkRegistry},
     * shared with the client-side manager; this method just adds the
     * server-specific side effects (curve-speed recompute, correction packets).
     * If the link is refused (the target node's two merge lanes — see
     * {@link BeltNode#addInput} — are both already occupied by other inputs,
     * i.e. a third belt trying to merge into an already-full junction), no
     * edge is created but the subnetwork merge (if any) still stands.
     */
    private void mergeAndLink(BlockPos fromPos, BlockPos toPos, ServerLevel level) {
        registry.mergeAndLink(fromPos, toPos);

        updateCurveSpeeds(fromPos, level);
        updateCurveSpeeds(toPos, level);

        // Notify client simulation on both belts about the connection change
        sendCorrection(fromPos, level);
        sendCorrection(toPos, level);
    }

    public void updateCurveSpeeds(BlockPos pos, ServerLevel level) {
        BeltNode node = nodeAt(pos);
        if (node == null) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) return;

        Direction facing = state.getValue(BlockBelt.FACING);
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean hasBack = isBeltFacing(level, pos.relative(back), pos);
        boolean hasLeft = isBeltFacing(level, pos.relative(left), pos);
        boolean hasRight = isBeltFacing(level, pos.relative(right), pos);

        if (!hasBack) {
            if (hasRight && !hasLeft) {
                node.lane(1).setSpeed(BeltLane.SPEED_INNER);
                node.lane(0).setSpeed(BeltLane.SPEED_OUTER);
                return;
            } else if (hasLeft && !hasRight) {
                node.lane(0).setSpeed(BeltLane.SPEED_INNER);
                node.lane(1).setSpeed(BeltLane.SPEED_OUTER);
                return;
            }
        }
        node.lane(0).setSpeed(BeltLane.SPEED_DEFAULT);
        node.lane(1).setSpeed(BeltLane.SPEED_DEFAULT);
    }

    private static boolean isBeltFacing(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
        BlockState state = level.getBlockState(fromPos);
        if (state.getBlock() instanceof BlockBelt) {
            Direction f = state.getValue(BlockBelt.FACING);
            return fromPos.relative(f).equals(toPos);
        }
        return false;
    }

    /** Send a full lane correction to all nearby clients for one belt position. */
    public void sendCorrection(BlockPos pos, ServerLevel level) {
        BeltNode node = nodeAt(pos);
        if (node == null) return;
        BeltCorrectionPayload pkt = new BeltCorrectionPayload(
                pos,
                node.facing(),
                node.outputPos(),
                node.lane(0).deepCopy(),
                node.lane(1).deepCopy(),
                level.getGameTime(),
                node.outputPos() != null,
                node.isStopped()
        );
        PacketDistributor.sendToPlayersNear(level, null,
                pos.getX(), pos.getY(), pos.getZ(), 128, pkt);
    }

    // ── SavedData ─────────────────────────────────────────────────────────────

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag subnetList = new ListTag();
        for (BeltSubnetwork sub : registry.allSubnetworks()) {
            subnetList.add(sub.save(registries));
        }
        tag.put("subnetworks", subnetList);
        return tag;
    }

    public static BeltNetworkData load(CompoundTag tag, HolderLookup.Provider registries) {
        BeltNetworkData data = new BeltNetworkData();
        ListTag subnetList = tag.getList("subnetworks", Tag.TAG_COMPOUND);
        for (int i = 0; i < subnetList.size(); i++) {
            BeltSubnetwork sub = BeltSubnetwork.load(subnetList.getCompound(i), registries);
            data.registry.adoptSubnetwork(sub);
            sub.layerOrder(); // Ensure topo/layer order is pre-built
        }
        // NOTE: lane speeds are persisted in NBT, so they survive a clean
        // save/load cycle without needing a ServerLevel here.  updateCurveSpeeds
        // is called on every topology change at runtime (place / remove / link),
        // which is the only time speeds ever need to change.
        return data;
    }
}
