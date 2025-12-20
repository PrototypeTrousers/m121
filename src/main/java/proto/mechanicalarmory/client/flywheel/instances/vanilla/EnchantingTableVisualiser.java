package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;

public class EnchantingTableVisualiser implements BlockEntityVisualizer<EnchantingTableBlockEntity> {
    public static EnchantingTableVisualiser ENCHANTING_TABLE_VISUAL = new EnchantingTableVisualiser();

    @Override
    public BlockEntityVisual<? super EnchantingTableBlockEntity> createVisual(VisualizationContext ctx, EnchantingTableBlockEntity blockEntity, float partialTick) {
        return new EnchantingTableVisual(ctx, blockEntity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(EnchantingTableBlockEntity blockEntity) {
        return true;
    }
}
