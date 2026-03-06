package proto.mechanicalarmory.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.common.entities.block.ArmEntity;
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

    public static void handle(ArmClickPayload payload, IPayloadContext context) {
        // enqueueWork ensures this runs safely on the main game thread, not the network thread!
        context.enqueueWork(() -> {
            var player = context.player();
            var level = player.level();

            // Make sure the chunk is actually loaded
            if (level.isLoaded(payload.armPos())) {
                BlockEntity be = level.getBlockEntity(payload.armPos());

                if (be instanceof ArmEntity arm) {
                    if (payload.action() == Action.RETRIEVE) {
                        arm.setSource(payload.clickedPos(), payload.clickedFace());
                    } else if (payload.action() == Action.DELIVER) {
                        arm.setTarget(payload.clickedPos(), payload.clickedFace());
                    } else {
                        var currentSource = arm.getSource();
                        var currentTarget = arm.getTarget();

                        if (currentSource != null && currentSource.key().equals(payload.clickedPos().subtract(arm.getBlockPos())) && currentSource.value().equals(payload.clickedFace())) {
                            arm.setSource(arm.getBlockPos(), Direction.UP);
                        } else if (currentTarget != null && currentTarget.key().equals(payload.clickedPos().subtract(arm.getBlockPos())) && currentTarget.value().equals(payload.clickedFace())) {
                            arm.setTarget(payload.armPos(), Direction.UP);
                        }
                    }
                    arm.updateWorkStatus(ActionTypes.IDLING, Action.IDLING);

                    // Mark for saving and update tracking clients
                    arm.setChanged();
                    level.sendBlockUpdated(payload.armPos(), arm.getBlockState(), arm.getBlockState(), 3);
                }
            }
        });
    }
}