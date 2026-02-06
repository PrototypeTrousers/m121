package proto.mechanicalarmory.common.items.armor;

import dev.engine_room.flywheel.api.event.EndClientResourceReloadEvent;
import dev.engine_room.flywheel.api.event.ReloadLevelRendererEvent;
import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.Holder;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitEffect;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitLogic;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitRenderer;
import proto.mechanicalarmory.common.items.MAItems;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class OctoSuit extends ArmorItem {

    OctoSuitLogic logic = new OctoSuitLogic();

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
    public static void onLivingEquipmentChangeEvent(LivingEquipmentChangeEvent event) {
        if (event.getFrom().getItem() instanceof OctoSuit) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                    event.getEntity(),
                    new RemoveFlywheelEffectPayload(event.getEntity().getId())
            );
        }
    }

    @SubscribeEvent
    public static void onEndClientResourceReloadEvent(EndClientResourceReloadEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            Minecraft.getInstance().player.removeData(MyAttachments.ARM_EFFECT_VISUAL);
        }
    }

    @SubscribeEvent
    public static void onReloadLevelRendererEvent(ReloadLevelRendererEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            Minecraft.getInstance().player.removeData(MyAttachments.ARM_EFFECT_VISUAL);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (entity instanceof Player player) {
            if (player.getItemBySlot(EquipmentSlot.CHEST) == stack) {
                if (!level.isClientSide) {
                    logic.tick(player);
                } else {
                    OctoSuitEffect effect = player.getData(MyAttachments.ARM_EFFECT_VISUAL);
                }
            }
        }
    }

    public record RemoveFlywheelEffectPayload(int entityId) implements CustomPacketPayload {

        // 1. Define the unique location for this packet
        public static final Type<RemoveFlywheelEffectPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "remove_flywheel_effect"));

        // 2. Define how to encode/decode the packet
        public static final StreamCodec<ByteBuf, RemoveFlywheelEffectPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, RemoveFlywheelEffectPayload::entityId,
                        RemoveFlywheelEffectPayload::new
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // 3. Handle the packet (Client Side)
        public static void handle(RemoveFlywheelEffectPayload payload, IPayloadContext context) {
            context.enqueueWork(() -> {
                Entity e = Minecraft.getInstance().level.getEntity(payload.entityId());
                Effect effect = e.removeData(MyAttachments.ARM_EFFECT_VISUAL);
                VisualizationManager.get(Minecraft.getInstance().level).effects().queueRemove(effect);
            });
        }
    }
}
