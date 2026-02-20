package proto.mechanicalarmory.client.screens;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import org.jetbrains.annotations.NotNull;
import proto.mechanicalarmory.common.entities.block.ArmEntity;
import proto.mechanicalarmory.common.menu.ArmMenu;

public class ArmScreen extends BaseOwoHandledScreen<FlowLayout, ArmMenu> {

    private final ArmEntity entity;

    public ArmScreen(ArmEntity armEntity) {
        super();
        this.entity = armEntity;
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
        rootComponent.child(Components.item())
        rootComponent.childById(SlotComponent.class, "item").
    }
}
