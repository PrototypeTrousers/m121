package proto.mechanicalarmory.client.instances;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

public class ArmVisualiser implements BlockEntityVisualizer<ArmEntity> {

    public static ArmVisualiser ARM_VISUAL = new ArmVisualiser();

    @Override
    public BlockEntityVisual<? super ArmEntity> createVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        return new ArmVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(ArmEntity blockEntity) {
        return true;
    }
}
