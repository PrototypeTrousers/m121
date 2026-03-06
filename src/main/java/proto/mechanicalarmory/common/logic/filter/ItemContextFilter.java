package proto.mechanicalarmory.common.logic.filter;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.ParentComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import proto.mechanicalarmory.client.screens.ArmScreen;
import proto.mechanicalarmory.common.network.FilterPayload;

public class ItemContextFilter {
    protected ItemStackHandler filterHandler;

    public ItemContextFilter(int size) {
        filterHandler = new ItemStackHandler(size);
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }


    public static class ItemContextFilterEmiDragDropHandler implements EmiDragDropHandler<ArmScreen> {

        @Override
        public boolean dropStack(ArmScreen screen, EmiIngredient stack, int x, int y) {
            if (screen.getFilterSlots().isInBoundingBox(x, y)) {
                int slotId = 0;
                Component clicked = null;
                for (Component child : screen.getFilterSlots().children()) {
                    if (child instanceof ParentComponent component) {
                        clicked = getChildAt(component, x, y);
                    }
                    if (clicked != null) {
                        break;
                    }
                    slotId++;
                }

                PacketDistributor.sendToServer(
                        new FilterPayload(screen.getMenu().getBlockEntity().getBlockPos(), slotId, stack.getEmiStacks().getFirst().getItemStack())
                );

                return true;
            }
            return false;
        }

        public Component getChildAt(ParentComponent parentComponent, int x, int y) {
            for (Component child : parentComponent.children()) {
                if (child instanceof ParentComponent component) {
                    return getChildAt(component, x, y);
                }
            }
            if (parentComponent.isInBoundingBox(x, y)) {
                return parentComponent;
            }
            return null;
        }

        @Override
        public void render(ArmScreen screen, EmiIngredient dragged, GuiGraphics draw, int mouseX, int mouseY, float delta) {
            screen.getFilterSlots().children().forEach(child -> {
                draw.fill(child.x(), child.y(), child.x() + child.width(), child.y() + child.height(), 0x8822BB33);
            });
        }
    }
}
