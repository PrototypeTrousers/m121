package proto.mechanicalarmory.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

import java.util.UUID;

/**
 * Sent server → client whenever the lane state diverges from what the client
 * can predict autonomously.
 *
 * <p>Triggers:
 * <ul>
 *   <li>Item inserted (player/machine)</li>
 *   <li>Item removed (player/machine)</li>
 *   <li>Belt stopped (redstone)</li>
 *   <li>Belt restarted (redstone off)</li>
 * </ul>
 *
 * <p>The handler is identical to {@link BeltInitPayload} — re-seed + catch-up,
 * then set the {@code stopped} flag.
 */
public record BeltCorrectionPayload(BlockPos pos, UUID nodeId, UUID outputId, BeltLane lane0, BeltLane lane1,
                                    long serverTick, boolean wrapPoint, boolean hasOutput, boolean stopped)
        implements CustomPacketPayload {

    public static final Type<BeltCorrectionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "belt_correction")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltCorrectionPayload> STREAM_CODEC =
            StreamCodec.of(BeltCorrectionPayload::encode, BeltCorrectionPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static void encode(RegistryFriendlyByteBuf buf, BeltCorrectionPayload pkt) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUUID(pkt.nodeId);
        buf.writeBoolean(pkt.outputId != null);
        if (pkt.outputId != null) buf.writeUUID(pkt.outputId);
        buf.writeLong(pkt.serverTick);
        HolderLookup.Provider regs = buf.registryAccess();
        buf.writeNbt(pkt.lane0.save(regs));
        buf.writeNbt(pkt.lane1.save(regs));
        buf.writeBoolean(pkt.wrapPoint);
        buf.writeBoolean(pkt.hasOutput);
        buf.writeBoolean(pkt.stopped);
    }

    private static BeltCorrectionPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos      = buf.readBlockPos();
        UUID nodeId       = buf.readUUID();
        UUID outputId     = buf.readBoolean() ? buf.readUUID() : null;
        long tick         = buf.readLong();
        HolderLookup.Provider regs = buf.registryAccess();
        BeltLane l0       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
        BeltLane l1       = BeltLane.load((CompoundTag) buf.readNbt(), regs);
        boolean wrap      = buf.readBoolean();
        boolean hasOut    = buf.readBoolean();
        boolean stopped   = buf.readBoolean();
        return new BeltCorrectionPayload(pos, nodeId, outputId, l0, l1, tick, wrap, hasOut, stopped);
    }

    // ── Handler (CLIENT) ──────────────────────────────────────────────────────

    public static void handle(BeltCorrectionPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;

            BeltLane l0 = pkt.lane0();
            BeltLane l1 = pkt.lane1();


            ClientBeltNetwork.get().updateNode(
                    pkt.pos(), pkt.outputId(), l0, l1, pkt.stopped(), pkt.wrapPoint(), pkt.hasOutput());
        });
    }
}
