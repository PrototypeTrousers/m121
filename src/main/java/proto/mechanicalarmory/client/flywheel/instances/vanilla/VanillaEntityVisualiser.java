package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.api.visual.EntityVisual;
import dev.engine_room.flywheel.api.visualization.EntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.entity.Entity;

public class VanillaEntityVisualiser implements EntityVisualizer<Entity> {
    public static VanillaEntityVisualiser VANILLA_ENTITY_VISUALISER = new VanillaEntityVisualiser();

    @Override
    public EntityVisual<? super Entity> createVisual(VisualizationContext ctx, Entity entity, float partialTick) {
        return new VanillaEntityVisual(ctx, entity, partialTick);
    }

    @Override
    public boolean skipVanillaRender(Entity entity) {
        return false;
    }
}
