package proto.mechanicalarmory.client.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import proto.mechanicalarmory.common.blocks.BushBlockEntity;

@Mixin(BushBlock.class)
public abstract class BushBlockMixin extends Block implements EntityBlock {
    public BushBlockMixin(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BushBlockEntity(pos, state);
    }
}
