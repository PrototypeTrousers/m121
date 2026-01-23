package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

public class OctoSuitEffect implements Effect {
    private final Entity entity;
    LevelAccessor level;
    private OctoSuitVisual visual;

    public OctoSuitEffect(LevelAccessor level, Entity holderEntity) {
        this.level = level;
        this.entity = holderEntity;
        VisualizationManager.get(level).effects().queueAdd(this);
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        this.visual = new OctoSuitVisual(ctx, entity, partialTick);
        return this.visual;
    }

    public OctoSuitVisual getVisual() {
        return visual;
    }
}
