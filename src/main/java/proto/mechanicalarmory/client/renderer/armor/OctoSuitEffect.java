package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

public class OctoSuitEffect implements Effect {
    private final Entity entity;
    LevelAccessor level;

    public  OctoSuitEffect(LevelAccessor level, Entity holderEntity) {
        this.level = level;
        this.entity = holderEntity;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new OctoSuitVisual(ctx, entity, partialTick);
    }
}
