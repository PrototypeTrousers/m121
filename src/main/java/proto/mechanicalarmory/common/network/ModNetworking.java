package proto.mechanicalarmory.common.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import proto.mechanicalarmory.common.items.armor.OctoSuit.RemoveFlywheelEffectPayload;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD)
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

        // Belt network: server → client
        registrar.playToClient(
                BeltInitPayload.TYPE,
                BeltInitPayload.STREAM_CODEC,
                BeltInitPayload::handle
        );

        registrar.playToClient(
                BeltCorrectionPayload.TYPE,
                BeltCorrectionPayload.STREAM_CODEC,
                BeltCorrectionPayload::handle
        );

        registrar.playToClient(
                BeltDeltaPayload.TYPE,
                BeltDeltaPayload.STREAM_CODEC,
                BeltDeltaPayload::handle
        );

        registrar.playToClient(
                RemoveFlywheelEffectPayload.TYPE,
                RemoveFlywheelEffectPayload.STREAM_CODEC,
                RemoveFlywheelEffectPayload::handle
        );
    }
}