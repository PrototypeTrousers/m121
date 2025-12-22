package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ObjectBuilder;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import proto.mechanicalarmory.client.mixin.BlockEntityRenderersAccessor;

public class BlockRendererBuilder<T> implements ObjectBuilder<BlockEntityRenderer<BlockEntity>> {
    BlockEntityRendererProvider.Context context;
    BlockEntityType<?>  blockEntityType;

    BlockRendererBuilder(BlockEntityType<?> blockEntityType, BlockEntityRendererProvider.Context context){
        this.context = context;
        this.blockEntityType = blockEntityType;
    }

    @Override
    public BlockEntityRenderer<BlockEntity> newInstance() {
        return (BlockEntityRenderer<BlockEntity>) BlockEntityRenderersAccessor.getProviders().get(this.blockEntityType).create(context);
    }
}
