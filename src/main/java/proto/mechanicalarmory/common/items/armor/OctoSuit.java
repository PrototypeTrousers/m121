package proto.mechanicalarmory.common.items.armor;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitEffect;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitRenderer;
import proto.mechanicalarmory.common.items.MAItems;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import static proto.mechanicalarmory.MechanicalArmory.MODID;
import static proto.mechanicalarmory.common.items.MAItems.ITEMS;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class OctoSuit extends ArmorItem {

    private static final Map<Entity, OctoSuitEffect> EFFECT_CACHE = new WeakHashMap<>();

    public static OctoSuitEffect getEffect(Entity entity, LevelAccessor l) {
        return EFFECT_CACHE.computeIfAbsent(entity, e -> new OctoSuitEffect(l, e));
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
        event.registerItem(itemExtensions, MAItems.MY_CHESTPLATE);
    }

    @SubscribeEvent
    public static void onClientExtensions(RenderLivingEvent.Pre event) {
        Level l = event.getEntity().level();
        VisualizationManager vm = VisualizationManager.get(l);
        if (event.getEntity().getItemBySlot(EquipmentSlot.CHEST).getItem() == MAItems.MY_CHESTPLATE.get()) {
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

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (entity instanceof Player player) {
            if (player.getItemBySlot(EquipmentSlot.CHEST) == stack) {

                if (!level.isClientSide) {
                    // 1. Calculate your target (e.g., raycast 10 blocks ahead)
                    Vec3 target = player.pick(10.0D, 0.0F, false).getLocation();

                    // 2. Optimization: Only send if the target has changed significantly
                    // (You might want to store the 'last sent' target in a transient capability or map to compare)

                    // 3. Send the packet to everyone tracking this player (and the player themselves)
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                            player,
                            new ArmTargetPayload(player.getId(), target.x, target.y, target.z)
                    );
                }
                int i =0;
            }
        }
    }

    public record ArmTargetPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {

        // Unique ID for this packet
        public static final Type<ArmTargetPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("mymod", "arm_target"));

        // Codec to encode/decode the data (int + 3 doubles)
        public static final StreamCodec<RegistryFriendlyByteBuf, ArmTargetPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ArmTargetPayload::entityId,
                ByteBufCodecs.DOUBLE, ArmTargetPayload::x,
                ByteBufCodecs.DOUBLE, ArmTargetPayload::y,
                ByteBufCodecs.DOUBLE, ArmTargetPayload::z,
                ArmTargetPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public class ClientArmTargetCache {
        // Maps Entity ID -> Target Position
        private static final Map<Integer, Vec3> TARGETS = new ConcurrentHashMap<>();

        public static void update(int entityId, double x, double y, double z) {
            TARGETS.put(entityId, new Vec3(x, y, z));
        }

        public static Vec3 getTarget(int entityId) {
            return TARGETS.get(entityId);
        }
    }
}
