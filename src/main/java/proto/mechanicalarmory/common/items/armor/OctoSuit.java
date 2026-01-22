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
// 1. Where does the player WANT the arm to go? (The Crosshair)
                    Vec3 desiredTarget = player.pick(10.0D, 0.0F, false).getLocation();

                    // 2. Where is the arm RIGHT NOW?
                    // If null (first time equipping), snap to the target immediately
                    Vec3 currentArmPos = player.getData(MyAttachments.ARM_TARGET);
                    if (currentArmPos.equals(Vec3.ZERO)) currentArmPos = desiredTarget;

                    // 3. Move the arm 10% of the way there (Adjust 0.1F for speed. 0.05 is heavy, 0.3 is fast)
                    // This is the "Smoothing" logic
                    Vec3 nextArmPos = currentArmPos.lerp(desiredTarget, 0.15D);

                    // 4. Save the new position
                    player.setData(MyAttachments.ARM_TARGET, nextArmPos);

                    // 6. Send the DESIRED Target to the client
                    // We send the 'desiredTarget' (Crosshair), not the 'nextArmPos'.
                    // We want the client to run its own smooth simulation to save bandwidth.
                    // Optimization: Only send if desiredTarget changed significantly (>0.1 blocks)
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                            player,
                            new ArmTargetPayload(player.getId(), desiredTarget.x, desiredTarget.y, desiredTarget.z)
                    );
                }
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
        private static final Map<Integer, ArmData> CACHE = new ConcurrentHashMap<>();

        public static class ArmData {
            public Vec3 target = Vec3.ZERO;
            public Vec3 current = Vec3.ZERO;
        }

        // Called when Packet arrives (Server says: "Go here")
        public static void updateTarget(int entityId, Vec3 newTarget) {
            CACHE.computeIfAbsent(entityId, id -> new ArmData()).target = newTarget;
        }

        // Called every Render Frame to calculate smoothing
        public static Vec3 getSmoothedPosition(int entityId, float partialTick) {
            ArmData data = CACHE.get(entityId);
            if (data == null) return null;

            // "Frame-rate Independent Damping"
            // This moves 'current' towards 'target' smoothly every frame.
            // The factor 0.15 must match your Server logic roughly,
            // but since render frames are faster than ticks, we adjust slightly or use delta time.

            double smoothness = 0.15;
            data.current = data.current.lerp(data.target, smoothness);

            return data.current;
        }
    }
}
