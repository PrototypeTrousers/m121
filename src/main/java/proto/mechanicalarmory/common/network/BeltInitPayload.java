package proto.mechanicalarmory.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sent once from the server to a client when a chunk containing belt blocks
 * loads.  The client seeds its autonomous simulation from this snapshot.
 */
public record BeltInitPayload(List<NodeSnapshot> snapshots, long serverTick)
        implements CustomPacketPayload {

    public static final Type<BeltInitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "belt_init")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltInitPayload> STREAM_CODEC =
            StreamCodec.of(BeltInitPayload::encode, BeltInitPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static void encode(RegistryFriendlyByteBuf buf, BeltInitPayload pkt) {
        buf.writeLong(pkt.serverTick);
        buf.writeVarInt(pkt.snapshots.size());
        HolderLookup.Provider regs = buf.registryAccess();
        for (NodeSnapshot snap : pkt.snapshots) {
            buf.writeBlockPos(snap.pos());
            buf.writeUUID(snap.nodeId());
            buf.writeBoolean(snap.outputId() != null);
            if (snap.outputId() != null) buf.writeUUID(snap.outputId());
            buf.writeNbt(snap.lane0().save(regs));
            buf.writeNbt(snap.lane1().save(regs));
            buf.writeBoolean(snap.wrapPoint());
            buf.writeBoolean(snap.hasOutput());
            buf.writeBoolean(snap.stopped());
        }
    }

    private static BeltInitPayload decode(RegistryFriendlyByteBuf buf) {
        long tick = buf.readLong();
        int count = buf.readVarInt();
        HolderLookup.Provider regs = buf.registryAccess();
        List<NodeSnapshot> snaps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos      = buf.readBlockPos();
            UUID nodeId       = buf.readUUID();
            UUID outputId     = buf.readBoolean() ? buf.readUUID() : null;
            BeltLane l0       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
            BeltLane l1       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
            boolean wrap      = buf.readBoolean();
            boolean hasOut    = buf.readBoolean();
            boolean stopped   = buf.readBoolean();
            snaps.add(new NodeSnapshot(pos, nodeId, outputId, l0, l1, wrap, hasOut, stopped));
        }
        return new BeltInitPayload(snaps, tick);
    }

    // ── Handler (CLIENT) ──────────────────────────────────────────────────────

    public static void handle(BeltInitPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            long clientTick = level.getGameTime();
            proto.mechanicalarmory.MechanicalArmory.LOGGER.info("[BeltInitPayload] Received {} snapshots at serverTick={}",
                    pkt.snapshots().size(), pkt.serverTick());
            for (NodeSnapshot snap : pkt.snapshots()) {
                BeltLane l0 = snap.lane0();
                BeltLane l1 = snap.lane1();

                if (level.getBlockEntity(snap.pos()) instanceof BeltEntity be) {
                    be.applyClientSeed(l0, l1, pkt.serverTick(), snap.stopped(),
                            snap.wrapPoint(), snap.hasOutput());
                }

                proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().updateNode(
                        snap.pos(), snap.outputId(), l0, l1, snap.stopped(), snap.wrapPoint(), snap.hasOutput());
            }
        });
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    public record NodeSnapshot(BlockPos pos, UUID nodeId, UUID outputId, BeltLane lane0, BeltLane lane1,
                               boolean wrapPoint, boolean hasOutput, boolean stopped) {}
}
