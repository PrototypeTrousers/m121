package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

public class VanillaBlockVisualiser implements BlockEntityVisualizer<BlockEntity> {
    public static VanillaBlockVisualiser VANILLA_BLOCK_VISUALISER = new VanillaBlockVisualiser();

    @Override
    public BlockEntityVisual<? super BlockEntity> createVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        return new VanillaBlockEntityVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(BlockEntity blockEntity) {
        return true;
    }
}
