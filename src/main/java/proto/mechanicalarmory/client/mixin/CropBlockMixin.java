package proto.mechanicalarmory.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import proto.mechanicalarmory.common.blocks.CropBlockEntity;

@Mixin(CropBlock.class)
public abstract class CropBlockMixin extends Block implements EntityBlock {
    public CropBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropBlockEntity(pos, state);
    }
}
