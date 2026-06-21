package proto.mechanicalarmory.common.entities.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.nikdo53.tinymultiblocklib.blockentities.AbstractMultiBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.entities.MAEntities;

public class ShredderEntity extends AbstractMultiBlockEntity implements BlockEntityTicker<ArmEntity>, MenuProvider {
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
    public void tick(Level level, BlockPos pos, BlockState state, ArmEntity blockEntity) {

    }
}
