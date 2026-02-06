package proto.mechanicalarmory.common.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import static proto.mechanicalarmory.common.entities.MAEntities.CROP_BLOCK_ENTITY;

public class CropBlockEntity extends BlockEntity {
    public CropBlockEntity(BlockPos pos, BlockState blockState) {
        super(CROP_BLOCK_ENTITY.get(), pos, blockState);
    }

    @Override
    public void setChanged() {
    }

}