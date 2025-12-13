package proto.mechanicalarmory.common.entities.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import proto.mechanicalarmory.common.entities.MAEntities;

public class ArmEntity extends BlockEntity {
    public ArmEntity(BlockPos pos, BlockState state) {
        super(MAEntities.ARM_ENTITY.get(), pos, state);
    }
}