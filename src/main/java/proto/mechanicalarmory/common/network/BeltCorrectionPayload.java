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
public record BeltCorrectionPayload(BlockPos pos, BeltLane lane0, BeltLane lane1,
                                     long serverTick, boolean wrapPoint, boolean hasOutput)
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
        buf.writeLong(pkt.serverTick);
        HolderLookup.Provider regs = buf.registryAccess();
        buf.writeNbt(pkt.lane0.save(regs));
        buf.writeNbt(pkt.lane1.save(regs));
        buf.writeBoolean(pkt.wrapPoint);
        buf.writeBoolean(pkt.hasOutput);
    }

    private static BeltCorrectionPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos  = buf.readBlockPos();
        long tick     = buf.readLong();
        HolderLookup.Provider regs = buf.registryAccess();
        BeltLane l0   = BeltLane.load((CompoundTag) buf.readNbt(), regs);
        BeltLane l1   = BeltLane.load((CompoundTag) buf.readNbt(), regs);
        boolean wrap  = buf.readBoolean();
        boolean hasOut = buf.readBoolean();
        return new BeltCorrectionPayload(pos, l0, l1, tick, wrap, hasOut);
    }

    // ── Handler (CLIENT) ──────────────────────────────────────────────────────

    public static void handle(BeltCorrectionPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null) return;
            if (!(level.getBlockEntity(pkt.pos()) instanceof BeltEntity be)) return;

            long clientTick = level.getGameTime();
            BeltLane l0 = pkt.lane0();
            BeltLane l1 = pkt.lane1();

            boolean stopped = (l0.speed() == 0f && l1.speed() == 0f);
            if (!stopped) {
                float catchUp = (clientTick - pkt.serverTick()) / 20.0f;
                if (catchUp > 0) { l0.advance(catchUp); l1.advance(catchUp); }
            }
            be.applyClientSeed(l0, l1, pkt.serverTick(), stopped,
                    pkt.wrapPoint(), pkt.hasOutput());
        });
    }
}
