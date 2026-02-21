package proto.mechanicalarmory.client.screens;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.SlimSliderComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
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
                .verticalAlignment(VerticalAlignment.CENTER);

        var overlay = Containers.verticalFlow(Sizing.content(), Sizing.content());
        overlay.horizontalAlignment(HorizontalAlignment.CENTER);
        overlay.surface(Surface.PANEL);

        var handSlot = Containers.stack(Sizing.content(), Sizing.content());
        handSlot.child(Components.texture(ITEM_SLOT, 0, 0, 18, 18, 18, 18));
        handSlot.child(slotAsComponent(36).positioning(Positioning.absolute(1,1)));
        handSlot.padding(Insets.vertical(3));

        var filterSettings = Containers.grid(Sizing.content(), Sizing.content(), 2,2);

        filterSettings.child(Components.smallCheckbox(Component.literal("Use filters")), 0,0);
        filterSettings.child(Components.wrapVanillaWidget(
                (Components.button(
                        Component.literal("W/B"), (onPress) -> {})))
                        .sizing(Sizing.fixed(18))
                , 0, 1);

        int as = 0;


        var playerInventoryContainer = Containers.verticalFlow(Sizing.fixed(176), Sizing.fixed(90));
        NonNullList<Slot> slotNonNullList = menu.slots;
        for (int i = 0; i < 36; i++) {
            var slot = slotNonNullList.get(i);
            playerInventoryContainer.child(getItemFrame(slot.x, slot.y));
            playerInventoryContainer.child(this.slotAsComponent(slot.index).positioning(Positioning.absolute(slot.x, slot.y)));
        }
        overlay.child(handSlot);
        overlay.child(filterSettings);
        overlay.child(playerInventoryContainer);

        rootComponent.child(overlay);
    }
}
