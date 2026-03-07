package proto.mechanicalarmory.client.screens;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.NotNull;

public class FilterLogicScreen extends BaseOwoScreen<FlowLayout> {

    Screen parent;
    FlowLayout overlay;

    public FilterLogicScreen(ArmScreen armScreen) {
        this.parent = armScreen;
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

        overlay = Containers.verticalFlow(Sizing.fixed(64), Sizing.fixed(64));
        overlay.horizontalAlignment(HorizontalAlignment.CENTER);
        overlay.surface(Surface.PANEL);


        rootComponent.child(overlay);
    }


    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.overlay.isInBoundingBox(mouseX, mouseY)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
