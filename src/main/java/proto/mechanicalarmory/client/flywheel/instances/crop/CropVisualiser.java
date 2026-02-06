package proto.mechanicalarmory.client.flywheel.instances.crop;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import proto.mechanicalarmory.common.blocks.BushBlockEntity;

public class CropVisualiser implements BlockEntityVisualizer<BushBlockEntity> {

    public static CropVisualiser CROP_VISUAL = new CropVisualiser();

    @Override
    public BlockEntityVisual<? super BushBlockEntity> createVisual(VisualizationContext ctx, BushBlockEntity blockEntity, float partialTick) {
        return new BushBlockVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(BushBlockEntity blockEntity) {
        return true;
    }
}
