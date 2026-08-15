package proto.mechanicalarmory.client.flywheel.instances.belt;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

/**
 * Dummy BlockEntityVisualizer that skips vanilla rendering.
 * Belt rendering is fully handled by {@link BeltSubnetworkVisual} via Flywheel Effects.
 */
public class BeltVisualiser implements BlockEntityVisualizer<BeltEntity> {

    public static final BeltVisualiser BELT_VISUAL = new BeltVisualiser();

    @Override
    public @Nullable BlockEntityVisual<? super BeltEntity> createVisual(VisualizationContext ctx,
                                                                         BeltEntity blockEntity,
                                                                         float partialTick) {
        return null; // Handled globally by BeltSubnetwork EffectVisual
    }

    @Override
    public boolean skipVanillaRender(BeltEntity blockEntity) {
        return true;
    }
}
