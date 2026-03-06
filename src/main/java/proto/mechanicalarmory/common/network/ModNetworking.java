package proto.mechanicalarmory.common.network;

import net.minecraft.core.Direction;
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
                ArmClickPayload::handle
        );

        registrar.playToServer(
                FilterPayload.TYPE,
                FilterPayload.STREAM_CODEC,
                FilterPayload::handle
        );
    }
}