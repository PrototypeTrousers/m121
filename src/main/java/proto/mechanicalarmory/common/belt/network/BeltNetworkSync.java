package proto.mechanicalarmory.common.belt.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.network.BeltCorrectionPayload;
import proto.mechanicalarmory.common.network.BeltDeltaPayload;
import proto.mechanicalarmory.common.network.BeltInitPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles server-to-client network synchronization and packet dispatching for belt networks.
 */
public final class BeltNetworkSync {

    private static final double PACKET_DIST_RADIUS = 128.0;

    private BeltNetworkSync() {}

    /**
     * Broadcasts a full lane correction to all nearby clients for one belt node.
     */
    public static void sendCorrection(BeltNode node, ServerLevel level) {
        if (node == null) return;
        BlockPos pos = node.pos();
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
                pos.getX(), pos.getY(), pos.getZ(), PACKET_DIST_RADIUS, pkt);
    }

    /**
     * Broadcasts a lightweight single-item delta to all nearby clients.
     */
    public static void sendDelta(BlockPos pos, int lane, ItemStack item, float headPos, byte action, ServerLevel level) {
        BeltDeltaPayload pkt = new BeltDeltaPayload(pos, lane, item, headPos, action);
        PacketDistributor.sendToPlayersNear(level, null,
                pos.getX(), pos.getY(), pos.getZ(), PACKET_DIST_RADIUS, pkt);
    }

    /**
     * Sends snapshot data for all belts within a chunk to a player who started watching that chunk.
     */
    public static void sendChunkInitToPlayer(ChunkPos chunkPos, BeltSubnetworkRegistry registry,
                                             ServerPlayer player, ServerLevel level) {
        List<BeltInitPayload.NodeSnapshot> snapshots = new ArrayList<>();
        int minX = chunkPos.getMinBlockX(), maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ(), maxZ = chunkPos.getMaxBlockZ();

        for (BlockPos pos : registry.trackedPositions()) {
            if (pos.getX() >= minX && pos.getX() <= maxX && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                BeltNode node = registry.nodeAt(pos);
                if (node != null) {
                    snapshots.add(new BeltInitPayload.NodeSnapshot(
                            node.pos(),
                            node.facing(),
                            node.outputPos(),
                            node.lane(0).deepCopy(),
                            node.lane(1).deepCopy(),
                            node.outputPos() != null,
                            node.isStopped()
                    ));
                }
            }
        }

        if (!snapshots.isEmpty()) {
            PacketDistributor.sendToPlayer(player, new BeltInitPayload(snapshots, level.getGameTime()));
        }
    }
}
