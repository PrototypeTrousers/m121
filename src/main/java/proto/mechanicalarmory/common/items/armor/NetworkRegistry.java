package proto.mechanicalarmory.common.items.armor;

import net.minecraft.world.phys.Vec3;
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

        // Register for Server -> Client communication
        registrar.playToClient(
                OctoSuit.RemoveFlywheelEffectPayload.TYPE,
                OctoSuit.RemoveFlywheelEffectPayload.STREAM_CODEC,
                OctoSuit.RemoveFlywheelEffectPayload::handle
        );
    }
}