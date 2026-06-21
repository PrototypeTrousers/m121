package proto.mechanicalarmory.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.nikdo53.tinymultiblocklib.block.AbstractMultiBlock;
import net.nikdo53.tinymultiblocklib.block.IMultiBlock;
import net.nikdo53.tinymultiblocklib.block.IPreviewableMultiblock;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

import java.util.ArrayList;
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
    public boolean hasCustomBE() {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShredderEntity(pos, state);
    }
}
