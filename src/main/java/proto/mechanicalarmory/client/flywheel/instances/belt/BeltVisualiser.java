package proto.mechanicalarmory.client.flywheel.instances.belt;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

public class BeltVisualiser implements BlockEntityVisualizer<BeltEntity> {

    public static final BeltVisualiser BELT_VISUAL = new BeltVisualiser();

    @Override
    public BlockEntityVisual<? super BeltEntity> createVisual(VisualizationContext ctx,
                                                               BeltEntity blockEntity,
                                                               float partialTick) {
        return new BeltVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(BeltEntity blockEntity) {
        return true;
    }
}
