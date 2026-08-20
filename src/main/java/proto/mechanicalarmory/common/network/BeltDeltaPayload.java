package proto.mechanicalarmory.common.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;

/**
 * Lightweight single-item event delta payload sent from server to client.
 */
public record BeltDeltaPayload(BlockPos pos, int lane, ItemStack item, float headPos, byte action)
        implements CustomPacketPayload {

    public static final byte ACTION_INSERT = 0;
    public static final byte ACTION_EXTRACT = 1;

    public static final Type<BeltDeltaPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "belt_delta")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BeltDeltaPayload> STREAM_CODEC =
            StreamCodec.of(BeltDeltaPayload::encode, BeltDeltaPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    // ── Codec ─────────────────────────────────────────────────────────────────

    private static void encode(RegistryFriendlyByteBuf buf, BeltDeltaPayload pkt) {
        buf.writeBlockPos(pkt.pos);
        buf.writeVarInt(pkt.lane);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, pkt.item);
        buf.writeFloat(pkt.headPos);
        buf.writeByte(pkt.action);
    }

    private static BeltDeltaPayload decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int lane = buf.readVarInt();
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        float headPos = buf.readFloat();
        byte action = buf.readByte();
        return new BeltDeltaPayload(pos, lane, item, headPos, action);
    }

    // ── Handler (CLIENT) ──────────────────────────────────────────────────────

    public static void handle(BeltDeltaPayload pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;

            BeltNode node = ClientBeltNetwork.get().registry().nodeAt(pkt.pos());
            if (node == null) return;

            BeltLane lane = node.lane(pkt.lane() == 1 ? 1 : 0);
            if (pkt.action() == ACTION_INSERT) {
                lane.insertItem(pkt.item());
            } else if (pkt.action() == ACTION_EXTRACT) {
                lane.extractNearest(pkt.headPos());
            }

            ClientBeltNetwork.get().syncToFlywheel();
        });
    }
}
