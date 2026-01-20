package proto.mechanicalarmory.common.items.armor;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitEffect;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitRenderer;

import java.util.Map;
import java.util.WeakHashMap;

import static proto.mechanicalarmory.MechanicalArmory.MODID;
import static proto.mechanicalarmory.common.items.MAItems.ITEMS;
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class OctoSuit extends ArmorItem {

    private static final Map<Entity, OctoSuitEffect> EFFECT_CACHE = new WeakHashMap<>();

    public static OctoSuitEffect getEffect(Entity entity, LevelAccessor l) {
        return EFFECT_CACHE.computeIfAbsent(entity, e -> new OctoSuitEffect(l));
    }

    public static final DeferredItem<ArmorItem> MY_CHESTPLATE = ITEMS.registerItem("my_chestplate",
            properties -> new ArmorItem(ArmorMaterials.MY_MATERIAL, ArmorItem.Type.CHESTPLATE, properties));

    public static void init() {

    }

    public OctoSuit(Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
    }


    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions itemExtensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return OctoSuitRenderer.INSTANCE; // Your BEWLR class
            }
        };
        event.registerItem(itemExtensions , OctoSuit.MY_CHESTPLATE);
    }

    @SubscribeEvent
    public static void onClientExtensions(RenderLivingEvent.Pre event) {
        Level l = event.getEntity().level();
        VisualizationManager vm = VisualizationManager.get(l);
        if (event.getEntity().getItemBySlot(EquipmentSlot.CHEST).getItem() == MY_CHESTPLATE.get()) {
            if (EFFECT_CACHE.get(event.getEntity()) == null) {
                vm.effects().queueAdd(getEffect(event.getEntity(), l));
            }
        } else {
            OctoSuitEffect ose = EFFECT_CACHE.get(event.getEntity());
            if (ose != null) {
                vm.effects().queueRemove(ose);
                EFFECT_CACHE.remove(event.getEntity());
            }
        }
    }
}
