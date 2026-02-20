package proto.mechanicalarmory.client.screens;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.GridLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;
import proto.mechanicalarmory.common.entities.block.ArmEntity;
import proto.mechanicalarmory.common.menu.ArmScreenHandler;
import rearth.oritech.client.ui.ItemFilterScreenHandler;

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
        overlay.surface(Surface.PANEL);

        var slots = Containers.verticalFlow(Sizing.fixed(176), Sizing.fixed(90));
        for (var slot : menu.getPlayerInventory().slots) {
            slots.child(this.slotAsComponent(slot.index).positioning(Positioning.absolute(slot.x, slot.y)));
            slots.child(getItemFrame(slot.x, slot.y));
        }
        overlay.child(slots);

        rootComponent.child(overlay);
    }
}
