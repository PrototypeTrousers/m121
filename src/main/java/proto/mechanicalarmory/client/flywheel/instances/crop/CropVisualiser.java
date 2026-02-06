package proto.mechanicalarmory.client.flywheel.instances.crop;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import proto.mechanicalarmory.client.flywheel.instances.arm.ArmVisual;
import proto.mechanicalarmory.common.blocks.CropBlockEntity;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

public class CropVisualiser implements BlockEntityVisualizer<CropBlockEntity> {

    public static CropVisualiser CROP_VISUAL = new CropVisualiser();

    @Override
    public BlockEntityVisual<? super CropBlockEntity> createVisual(VisualizationContext ctx, CropBlockEntity blockEntity, float partialTick) {
        return new CropVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(CropBlockEntity blockEntity) {
        return true;
    }
}
