package proto.mechanicalarmory.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import proto.mechanicalarmory.common.logic.Action;
import proto.mechanicalarmory.common.logic.ActionTypes;

public record ArmClickPayload(BlockPos armPos, BlockPos clickedPos, Direction clickedFace, Action action) implements CustomPacketPayload {

    // Unique ID for your packet
    public static final Type<ArmClickPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "arm_click")
    );

    // Codec to read/write the data. BlockPos and Direction already have built-in codecs!
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmClickPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArmClickPayload::armPos,
            BlockPos.STREAM_CODEC, ArmClickPayload::clickedPos,
            Direction.STREAM_CODEC, ArmClickPayload::clickedFace,
            NeoForgeStreamCodecs.enumCodec(Action.class), ArmClickPayload::action,
            ArmClickPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}