package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.blocks.BlockBelt;
import proto.mechanicalarmory.common.entities.block.BeltEntity;
import proto.mechanicalarmory.common.network.BeltCorrectionPayload;
import proto.mechanicalarmory.common.network.BeltInitPayload;

import java.util.*;

/**
 * {@link SavedData} that owns the entire belt network for a level.
 *
 * <p>All item positions live here.  {@link BeltEntity} is a thin rendering
 * anchor — it holds no authoritative item data.
 *
 * <h3>Lifecycle</h3>
 * <ul>
 *   <li>Retrieved via {@link #get(ServerLevel)} — created lazily.</li>
 *   <li>Ticked by {@code LevelTickEvent} every server tick.</li>
 *   <li>Belts added/removed via {@link #onBeltPlaced} / {@link #onBeltRemoved}.</li>
 *   <li>Chunk load: {@link #onChunkLoaded} sends {@link BeltInitPayload} to
 *       nearby clients and calls {@link BeltEntity#syncFromNetwork}.</li>
 * </ul>
 */
public final class BeltNetworkData extends SavedData {

    public static final String KEY = "mechanicalarmory_belt_network";

    /** All connected components, keyed by their UUID. */
    private final Map<UUID, BeltSubnetwork> subnetworks = new LinkedHashMap<>();

    /** Fast lookup: BlockPos → which subnetwork owns it. */
    private final Map<BlockPos, UUID> posToSubnet = new HashMap<>();

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
        BeltNode node = new BeltNode(pos);
        boolean powered = level.getBlockState(pos).getValue(proto.mechanicalarmory.common.blocks.BlockBelt.POWERED);
        node.setStopped(powered);

        // Create a fresh subnetwork for this node
        BeltSubnetwork solo = new BeltSubnetwork(UUID.randomUUID());
        solo.addNode(node);
        subnetworks.put(solo.subnetId(), solo);
        posToSubnet.put(pos, solo.subnetId());

        if (level.getBlockEntity(pos) instanceof BeltEntity be) {
            be.syncFromNetwork(node);
        }

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
        UUID subnetId = posToSubnet.remove(pos);
        if (subnetId == null) return;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        if (subnet == null) return;

        BeltNode node = subnet.nodeAt(pos);
        List<BlockPos> affected = new ArrayList<>();
        if (node != null) {
            if (node.outputId() != null) {
                BeltNode out = subnet.node(node.outputId());
                if (out != null) affected.add(out.pos());
            }
            for (UUID inId : node.inputIds()) {
                BeltNode in = subnet.node(inId);
                if (in != null) affected.add(in.pos());
            }
            subnet.removeNode(node.nodeId());
        }

        if (subnet.isEmpty()) {
            subnetworks.remove(subnetId);
        } else {
            // Check if the removal split the subnetwork
            List<BeltSubnetwork> parts = subnet.splitIfNeeded();
            if (parts.size() > 1) {
                subnetworks.remove(subnetId);
                for (BeltSubnetwork part : parts) {
                    subnetworks.put(part.subnetId(), part);
                    for (BeltNode n : part.allNodes()) {
                        posToSubnet.put(n.pos(), part.subnetId());
                    }
                }
            }
        }

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
     * current lane state to nearby clients so they can seed their simulations,
     * and sync the {@link BeltEntity}.
     */
    public void onChunkLoaded(net.minecraft.world.level.ChunkPos chunkPos, ServerLevel level) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        List<BeltInitPayload.NodeSnapshot> snapshots = new ArrayList<>();

        for (Map.Entry<BlockPos, UUID> entry : posToSubnet.entrySet()) {
            BlockPos bpos = entry.getKey();
            if (bpos.getX() < minX || bpos.getX() > maxX) continue;
            if (bpos.getZ() < minZ || bpos.getZ() > maxZ) continue;

            BeltSubnetwork subnet = subnetworks.get(entry.getValue());
            if (subnet == null) continue;
            BeltNode node = subnet.nodeAt(bpos);
            if (node == null) continue;

            snapshots.add(new BeltInitPayload.NodeSnapshot(
                    bpos,
                    node.nodeId(),
                    node.outputId(),
                    node.lane(0).deepCopy(),
                    node.lane(1).deepCopy(),
                    node.isWrapPoint(),
                    node.outputId() != null,
                    node.isStopped()
            ));

            // Sync the BeltEntity's clientLanes reference (server-side BE)
            if (level.isLoaded(bpos)) {
                if (level.getBlockEntity(bpos) instanceof BeltEntity be) {
                    be.syncFromNetwork(node);
                }
            }
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
    public void sendChunkInitToPlayer(net.minecraft.world.level.ChunkPos chunkPos, net.minecraft.server.level.ServerPlayer player) {
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        List<BeltInitPayload.NodeSnapshot> snapshots = new ArrayList<>();

        for (Map.Entry<BlockPos, UUID> entry : posToSubnet.entrySet()) {
            BlockPos bpos = entry.getKey();
            if (bpos.getX() < minX || bpos.getX() > maxX) continue;
            if (bpos.getZ() < minZ || bpos.getZ() > maxZ) continue;

            BeltSubnetwork subnet = subnetworks.get(entry.getValue());
            if (subnet == null) continue;
            BeltNode node = subnet.nodeAt(bpos);
            if (node == null) continue;

            snapshots.add(new BeltInitPayload.NodeSnapshot(
                    bpos,
                    node.nodeId(),
                    node.outputId(),
                    node.lane(0).deepCopy(),
                    node.lane(1).deepCopy(),
                    node.isWrapPoint(),
                    node.outputId() != null,
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
    public BeltNode nodeAt(BlockPos pos) {
        UUID subnetId = posToSubnet.get(pos);
        if (subnetId == null) return null;
        BeltSubnetwork subnet = subnetworks.get(subnetId);
        return subnet == null ? null : subnet.nodeAt(pos);
    }

    public Collection<BeltSubnetwork> allSubnetworks() { return subnetworks.values(); }

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
     */
    private void mergeAndLink(BlockPos fromPos, BlockPos toPos, ServerLevel level) {
        UUID fromSubId = posToSubnet.get(fromPos);
        UUID toSubId = posToSubnet.get(toPos);
        if (fromSubId == null || toSubId == null) return;

        BeltSubnetwork fromSub = subnetworks.get(fromSubId);
        BeltSubnetwork toSub = subnetworks.get(toSubId);
        if (fromSub == null || toSub == null) return;

        // Merge toSub into fromSub if they are distinct subnetworks
        if (!fromSubId.equals(toSubId)) {
            for (BeltNode n : toSub.allNodes()) {
                fromSub.addNode(n);
                posToSubnet.put(n.pos(), fromSub.subnetId());
            }
            subnetworks.remove(toSubId);
        }

        fromSub.link(fromPos, toPos);

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

    @Nullable
    private BeltSubnetwork subnetworkAt(BlockPos pos) {
        UUID id = posToSubnet.get(pos);
        return id == null ? null : subnetworks.get(id);
    }

    /** Send a full lane correction to all nearby clients for one belt position. */
    public void sendCorrection(BlockPos pos, ServerLevel level) {
        BeltNode node = nodeAt(pos);
        if (node == null) return;
        BeltCorrectionPayload pkt = new BeltCorrectionPayload(
                pos,
                node.nodeId(),
                node.outputId(),
                node.lane(0).deepCopy(),
                node.lane(1).deepCopy(),
                level.getGameTime(),
                node.isWrapPoint(),
                node.outputId() != null,
                node.isStopped()
        );
        PacketDistributor.sendToPlayersNear(level, null,
                pos.getX(), pos.getY(), pos.getZ(), 128, pkt);
    }

    // ── SavedData ─────────────────────────────────────────────────────────────

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        ListTag subnetList = new ListTag();
        for (BeltSubnetwork sub : subnetworks.values()) {
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
            data.subnetworks.put(sub.subnetId(), sub);
            for (BeltNode n : sub.allNodes()) {
                data.posToSubnet.put(n.pos(), sub.subnetId());
            }
            sub.layerOrder(); // Ensure topo/layer order is pre-built
        }
        // NOTE: lane speeds are persisted in NBT, so they survive a clean
        // save/load cycle without needing a ServerLevel here.  updateCurveSpeeds
        // is called on every topology change at runtime (place / remove / link),
        // which is the only time speeds ever need to change.
        return data;
    }
}
