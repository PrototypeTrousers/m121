package proto.mechanicalarmory.common.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import proto.mechanicalarmory.client.screens.ArmScreen;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class MenuTypes {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MODID);

    // Use IForgeMenuType to allow passing BlockPos/Data via FriendlyByteBuf
    public static final DeferredHolder<MenuType<?>, MenuType<ArmScreenHandler>> ARM_ENTITY_MENU =
            MENUS.register("arm_entity_menu",
                    () -> IMenuTypeExtension.create(ArmScreenHandler::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
