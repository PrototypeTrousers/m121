package proto.mechanicalarmory.common.entities.block;

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
import net.nikdo53.tinymultiblocklib.blockentities.AbstractMultiBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.recipes.Recipe;
import proto.mechanicalarmory.common.recipes.RecipeRegistry;

import java.util.List;

public class ShredderEntity extends AbstractMultiBlockEntity implements BlockEntityTicker<ShredderEntity>, MenuProvider {
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


}
