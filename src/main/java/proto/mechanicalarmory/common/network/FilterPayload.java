package proto.mechanicalarmory.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

public record FilterPayload(BlockPos armPos, int filterIdx, ItemStack stack) implements CustomPacketPayload {

    // Unique ID for your packet
    public static final Type<FilterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "filter_click")
    );

    // Codec to read/write the data. BlockPos and Direction already have built-in codecs!
    public static final StreamCodec<RegistryFriendlyByteBuf, FilterPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, FilterPayload::armPos,
            ByteBufCodecs.INT, FilterPayload::filterIdx,
            ItemStack.STREAM_CODEC, FilterPayload::stack,
            FilterPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var level = player.level();

            // Make sure the chunk is actually loaded
            if (level.isLoaded(payload.armPos())) {
                BlockEntity be = level.getBlockEntity(payload.armPos());

                if (be instanceof ArmEntity arm) {
                    arm.getItemContextFilter().getFilterHandler().setStackInSlot(payload.filterIdx(), payload.stack());
                }
            }
        });
    }
}

