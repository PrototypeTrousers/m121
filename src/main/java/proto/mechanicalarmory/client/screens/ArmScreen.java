package proto.mechanicalarmory.client.screens;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.math.Axis;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import proto.mechanicalarmory.client.ui.owo.component.KnobButton;
import proto.mechanicalarmory.client.ui.owo.component.WorldSceneComponent;
import proto.mechanicalarmory.common.menu.ArmScreenHandler;

import static rearth.oritech.client.ui.BasicMachineScreen.ITEM_SLOT;
import static rearth.oritech.client.ui.BasicMachineScreen.getItemFrame;

public class ArmScreen extends BaseOwoHandledScreen<FlowLayout, ArmScreenHandler> {

    public ArmScreen(ArmScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.BOTTOM);

        var fakeWorld = Containers.verticalFlow(Sizing.content(), Sizing.content());
        fakeWorld.child(new WorldSceneComponent(menu.getBlockEntity().getBlockPos(),
                menu.getPlayerInventory().player.getViewXRot(1),
                menu.getPlayerInventory().player.getViewYRot(1), 1)
                .sizing(Sizing.fixed(128)))
                .alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);


        var overlay = Containers.verticalFlow(Sizing.content(), Sizing.content());
        overlay.horizontalAlignment(HorizontalAlignment.CENTER);
        overlay.surface(Surface.PANEL);

        var handSlot = Containers.stack(Sizing.content(), Sizing.content());
        handSlot.child(Components.texture(ITEM_SLOT, 0, 0, 18, 18, 18, 18));
        handSlot.child(slotAsComponent(36).positioning(Positioning.absolute(1, 1)));
        handSlot.padding(Insets.vertical(3));

        var filterSettings = Containers.grid(Sizing.content(), Sizing.content(), 2, 2);

        filterSettings.child(Components.smallCheckbox(Component.literal("Use filters"))
                .margins(Insets.horizontal(4)), 0, 0);
        filterSettings.child(Components.wrapVanillaWidget(
                                (Components.button(Component.literal("Whitelist/Blacklist"), (a) -> {
                                })).renderer(KnobButton.knob(0xffff0000, 0xffff0000, 0xffff0000)))
                        .sizing(Sizing.content(), Sizing.content())
                        .margins(Insets.horizontal(4))
                , 1, 0);

        var filterSlots = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        filterSlots.margins(Insets.horizontal(4));

        for (int s = 0; s < 5; s++) {
            var filterSlot = Containers.stack(Sizing.content(), Sizing.content());
            filterSlot.child(Components.texture(ITEM_SLOT, 0, 0, 18, 18, 18, 18));
            //handSlot.child(slotAsComponent(36).positioning(Positioning.absolute(1,1)));
            filterSlots.child(filterSlot);
        }
        filterSettings.child(filterSlots, 1, 1);


        var playerInventoryContainer = Containers.verticalFlow(Sizing.fixed(176), Sizing.fixed(90));
        NonNullList<Slot> slotNonNullList = menu.slots;
        for (int i = 0; i < 36; i++) {
            var slot = slotNonNullList.get(i);
            playerInventoryContainer.child(getItemFrame(slot.x, slot.y));
            playerInventoryContainer.child(this.slotAsComponent(slot.index).positioning(Positioning.absolute(slot.x, slot.y)));
        }

        overlay.child(fakeWorld);
        overlay.child(handSlot);
        overlay.child(filterSettings);
        overlay.child(playerInventoryContainer);

        rootComponent.child(overlay);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }
}
