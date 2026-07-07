package proto.mechanicalarmory.common.blocks;

import brachy.modularui.factory.UIFactories;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.nikdo53.tinymultiblocklib.block.AbstractMultiBlock;
import net.nikdo53.tinymultiblocklib.block.IMultiBlock;
import net.nikdo53.tinymultiblocklib.block.IPreviewableMultiblock;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

import java.util.List;

public class BlockShredder extends AbstractMultiBlock implements IPreviewableMultiblock {

    public BlockShredder(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getMultiblockRenderShape(BlockState state, boolean isCenter) {
        return isCenter ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.INVISIBLE;
    }

    @Override
    public List<BlockPos> makeFullBlockShape(Level level, BlockPos center, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction direction) {
        return IMultiBlock.posStreamToList(BlockPos.betweenClosedStream(center, center.north().west()));
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }
        if (IMultiBlock.isCenter(state)) {
            return (world1, pos, state1, blockEntity) -> {
                if (blockEntity instanceof BlockEntityTicker ticker)
                    ticker.tick(world1, pos, state1, blockEntity);
            };
        }
        return null;
    }

    @Override
    public boolean hasCustomBE() {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShredderEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            UIFactories.blockEntity().open(player, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
