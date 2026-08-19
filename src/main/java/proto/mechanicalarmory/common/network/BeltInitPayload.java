package proto.mechanicalarmory.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltLane;

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
            buf.writeBoolean(snap.outputId() != null);
            if (snap.outputId() != null) buf.writeBlockPos(snap.outputId());
            buf.writeNbt(snap.lane0().save(regs));
            buf.writeNbt(snap.lane1().save(regs));
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
            BlockPos outputPos     = buf.readBoolean() ? buf.readBlockPos() : null;
            BeltLane l0       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
            BeltLane l1       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
            boolean hasOut    = buf.readBoolean();
            boolean stopped   = buf.readBoolean();
            snaps.add(new NodeSnapshot(pos, outputPos, l0, l1, hasOut, stopped));
        }
        return new BeltInitPayload(snaps, tick);
    }

    // ── Handler (CLIENT) ──────────────────────────────────────────────────────

    public static void handle(BeltInitPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            MechanicalArmory.LOGGER.info("[BeltInitPayload] Received {} snapshots at serverTick={}",
                    pkt.snapshots().size(), pkt.serverTick());
            for (NodeSnapshot snap : pkt.snapshots()) {
                BeltLane l0 = snap.lane0();
                BeltLane l1 = snap.lane1();

                ClientBeltNetwork.get().updateNode(
                        snap.pos(), snap.outputId(), l0, l1, snap.stopped(), snap.hasOutput());
            }
        });
    }

    // ── Inner type ────────────────────────────────────────────────────────────

    public record NodeSnapshot(BlockPos pos, BlockPos outputId, BeltLane lane0, BeltLane lane1,
                               boolean hasOutput, boolean stopped) {}
}
