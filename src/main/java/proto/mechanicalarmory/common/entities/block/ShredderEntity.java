package proto.mechanicalarmory.common.entities.block;

import brachy.modularui.api.IUIHolder;
import brachy.modularui.drawable.SchemaRenderer;
import brachy.modularui.drawable.schema.BaseSchemaRenderer;
import brachy.modularui.drawable.schema.BlockHighlight;
import brachy.modularui.drawable.schema.BoxSchema;
import brachy.modularui.drawable.schema.ISchema;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Color;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.SchemaWidget;
import brachy.modularui.widgets.SlotGroupWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.nikdo53.tinymultiblocklib.blockentities.AbstractMultiBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.recipes.Recipe;
import proto.mechanicalarmory.common.recipes.RecipeRegistry;

import java.util.List;

public class ShredderEntity extends AbstractMultiBlockEntity implements BlockEntityTicker<ShredderEntity>, MenuProvider, IUIHolder<PosGuiData> {
    private static final List<Recipe> recipes = RecipeRegistry.getInstance().getRecipes("shredder");

    public ShredderEntity(BlockPos pos, BlockState blockState) {
        super(MAEntities.SHREDDER_ENTITY.get(), pos, blockState);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.nullToEmpty("");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return null;
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ShredderEntity blockEntity) {
        if (level.isClientSide) {
            return;
        }
        var l = level.getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(this.getBlockPos().above(), this.getBlockPos().above().north().west()));
        l.forEach(entity -> {
            ItemStack is = entity.getItem();
            if (!is.isEmpty()) {
                recipes.forEach(recipe -> {
                    if (ItemStack.isSameItem(is, recipe.input())) {
                        is.shrink(1);
                        level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5f, pos.getY(), pos.getZ() + 1.5f, recipe.output().copy()));
                    }
                });
            }
        });
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel<?> mainPanel) {
        return new ModularScreen(MechanicalArmory.MODID, mainPanel);
    }

    @Override
    public ModularPanel<?> buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel<?> panel = new ModularPanel<>("shredder");

        var schema  =BoxSchema.of(this.level, this.getCenter(), 5  );
        int i = 0;
        if (level.isClientSide) {
            var renderer = schema.createRenderer();
            renderer.highlightRenderer(new BlockHighlight(Color.withAlpha(Color.RED.main, 0.5f))
                    .allSides(false)
                    .thickness(0.1f));

            panel.child(
                    new ConfigSchemaWidget(renderer, panel)
                            .full()
                            .enableDragTranslation(false));
        }
        return panel;
    }

    static class ConfigSchemaWidget extends SchemaWidget {
        ModularPanel<?> panel;
        public ConfigSchemaWidget(SchemaRenderer renderer, ModularPanel<?> panel) {
            super(renderer);
            this.panel = panel;
        }

        @Override
        public @NotNull Result onMousePressed(int button) {
            if (getSchemaRenderer().lastRayTrace().getType() == HitResult.Type.BLOCK) {
                this.panel.child(SlotGroupWidget.playerInventory(true));
                return Result.SUCCESS;
            }
            return super.onMousePressed(button);
        }
    }
}
