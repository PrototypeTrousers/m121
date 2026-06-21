package proto.mechanicalarmory.client.flywheel.instances.shredder;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.nikdo53.tinymultiblocklib.block.IMultiBlock;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

public class ShredderVisualiser implements BlockEntityVisualizer<ShredderEntity> {
    public static ShredderVisualiser SHREDDER_VISUAL = new ShredderVisualiser();
    @Override
    public BlockEntityVisual<? super ShredderEntity> createVisual(VisualizationContext ctx, ShredderEntity blockEntity, float partialTick) {
        if ( IMultiBlock.isCenter(blockEntity.getBlockState())) {
            return new ShredderVisual(ctx, blockEntity, partialTick);
        }
        return null;
    }

    @Override
    public boolean skipVanillaRender(ShredderEntity blockEntity) {
        return true;
    }
}
