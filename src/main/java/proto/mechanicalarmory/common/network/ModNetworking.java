package proto.mechanicalarmory.common.network;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import proto.mechanicalarmory.common.entities.block.ArmEntity;
import proto.mechanicalarmory.common.logic.Action;
import proto.mechanicalarmory.common.logic.ActionTypes;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

@EventBusSubscriber(modid = MODID)
public class ModNetworking {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        // Register your mod's payloads
        final PayloadRegistrar registrar = event.registrar("1");

        // playToServer means this packet only goes Client -> Server
        registrar.playToServer(
            ArmClickPayload.TYPE,
            ArmClickPayload.STREAM_CODEC,
            (payload, context) -> {
                // enqueueWork ensures this runs safely on the main game thread, not the network thread!
                context.enqueueWork(() -> {
                    var player = context.player();
                    var level = player.level();
                    
                    // Make sure the chunk is actually loaded
                    if (level.isLoaded(payload.armPos())) {
                        BlockEntity be = level.getBlockEntity(payload.armPos());
                        
                        if (be instanceof ArmEntity arm) {
                            var currentSource = arm.getSource();
                            
                            // The server processes the toggle logic
                            if (currentSource != null && currentSource.key().equals(payload.clickedPos().subtract(arm.getBlockPos())) && currentSource.value().equals(payload.clickedFace())) {
                                arm.setTarget(payload.clickedPos(), payload.clickedFace());
                            } else {
                                arm.setSource(payload.clickedPos(), payload.clickedFace());
                            }

                            arm.updateWorkStatus(ActionTypes.IDLING, Action.IDLING);
                            
                            // Mark for saving and update tracking clients
                            arm.setChanged();
                            level.sendBlockUpdated(payload.armPos(), arm.getBlockState(), arm.getBlockState(), 3);
                        }
                    }
                });
            }
        );
    }
}