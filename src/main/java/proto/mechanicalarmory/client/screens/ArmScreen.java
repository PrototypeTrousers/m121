package proto.mechanicalarmory.client.screens;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.engine_room.flywheel.api.material.LightShader;
import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.DropdownComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import proto.mechanicalarmory.client.ui.owo.component.KnobButton;
import proto.mechanicalarmory.client.ui.owo.component.WorldSceneComponent;
import proto.mechanicalarmory.common.logic.Action;
import proto.mechanicalarmory.common.menu.ArmScreenHandler;
import proto.mechanicalarmory.common.network.ArmClickPayload;

import java.util.ArrayList;
import java.util.List;

import static rearth.oritech.client.ui.BasicMachineScreen.ITEM_SLOT;
import static rearth.oritech.client.ui.BasicMachineScreen.getItemFrame;

public class ArmScreen extends BaseOwoHandledScreen<FlowLayout, ArmScreenHandler> implements EmiDragDropHandler<ArmScreen> {

    List<Slot> slotList =  new ArrayList<>();

    public FlowLayout getFilterSlots() {
        return filterSlots;
    }

    private FlowLayout filterSlots;

    public ArmScreen(ArmScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {

        //TODO add a hovering widget to select if a block is a source or a target with the mouse wheel
        rootComponent
                .surface(Surface.VANILLA_TRANSLUCENT)
                .horizontalAlignment(HorizontalAlignment.CENTER)
                .verticalAlignment(VerticalAlignment.BOTTOM);

        var fakeWorld = Containers.verticalFlow(Sizing.content(), Sizing.content());
        WorldSceneComponent worldSceneComponent = new WorldSceneComponent(menu.getBlockEntity().getBlockPos(),
                menu.getPlayerInventory().player.getViewXRot(1),
                menu.getPlayerInventory().player.getViewYRot(1), 1)
                .onBlockClicked((hit, button) -> {
                    if (button == 1) {
                        BlockPos armPos = menu.getBlockEntity().getBlockPos();
                        BlockPos clickedPos = hit.left();
                        Direction clickedFace = hit.right();

                        DropdownComponent inoutSelector = Components.dropdown(Sizing.content());
                        inoutSelector.closeWhenNotHovered(true);

                        inoutSelector
                                .zIndex(200)
                                .positioning(Positioning.absolute(getAbsoluteMouseX() - fakeWorld.x(), getAbsoluteMouseY() - fakeWorld.y()));
                        inoutSelector.button(Component.literal("IN"), (c) -> {
                            PacketDistributor.sendToServer(
                                    new ArmClickPayload(armPos, clickedPos, clickedFace, Action.RETRIEVE)
                            );
                        });
                        inoutSelector.button(Component.literal("NONE"), (c) -> {
                            PacketDistributor.sendToServer(
                                    new ArmClickPayload(armPos, clickedPos, clickedFace, Action.IDLING)
                            );
                        });
                        inoutSelector.button(Component.literal("OUT"), (c) -> {
                            PacketDistributor.sendToServer(
                                    new ArmClickPayload(armPos, clickedPos, clickedFace, Action.DELIVER)
                            );
                        });

                        fakeWorld.child(inoutSelector
                        );
                    }
                });

        fakeWorld.child(worldSceneComponent
                        .targeting(menu.getBlockEntity().getTargeting())
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

        this.filterSlots = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        filterSlots.margins(Insets.horizontal(4));

        for (int s = 37; s < menu.getFilterHandler().getSlots() + 36; s++) {
            var filterSlot = Containers.stack(Sizing.content(), Sizing.content());
            filterSlot.child(Components.texture(ITEM_SLOT, 0, 0, 18, 18, 18, 18));
            SlotComponent slotComponent = slotAsComponent(s);
            slotComponent.positioning(Positioning.absolute(1,1));
            filterSlot.child(slotComponent);
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

    private int getAbsoluteMouseX() {
        return (int) (this.minecraft.mouseHandler.xpos()
                * (double) this.minecraft.getWindow().getGuiScaledWidth()
                / (double) this.minecraft.getWindow().getScreenWidth());
    }

    private int getAbsoluteMouseY() {
        return (int) (this.minecraft.mouseHandler.ypos()
                * (double) this.minecraft.getWindow().getGuiScaledHeight()
                / (double) this.minecraft.getWindow().getScreenHeight());
    }

    @Override
    public boolean dropStack(ArmScreen screen, EmiIngredient stack, int x, int y) {
        return false;
    }

    @Override
    public void render(ArmScreen screen, EmiIngredient dragged, GuiGraphics draw, int mouseX, int mouseY, float delta) {
        EmiDragDropHandler.super.render(screen, dragged, draw, mouseX, mouseY, delta);
    }
}
