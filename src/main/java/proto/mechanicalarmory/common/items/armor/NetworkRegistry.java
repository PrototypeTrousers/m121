package proto.mechanicalarmory.common.items.armor;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class NetworkRegistry {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
            OctoSuit.ArmTargetPayload.TYPE,
            OctoSuit.ArmTargetPayload.STREAM_CODEC,
            (payload, context) -> {
                // This runs on the network thread, so we must enqueue work to the main thread
                context.enqueueWork(() -> {
                    // Update our client-side cache
                    OctoSuit.ClientArmTargetCache.updateTarget(
                            payload.entityId(),
                            new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z())
                    );
                });
            }
        );
    }
}