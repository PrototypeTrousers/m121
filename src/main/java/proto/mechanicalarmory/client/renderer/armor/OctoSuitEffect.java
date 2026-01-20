package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.Effect;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import net.minecraft.world.level.LevelAccessor;

public class OctoSuitEffect implements Effect {
    LevelAccessor level;

    public  OctoSuitEffect(LevelAccessor level) {
        this.level = level;
    }

    @Override
    public LevelAccessor level() {
        return level;
    }

    @Override
    public EffectVisual<?> visualize(VisualizationContext ctx, float partialTick) {
        return new OctoSuitVisual(ctx, partialTick);
    }
}
