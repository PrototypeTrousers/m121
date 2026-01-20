package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    TransformedInstance transformedInstance;

    public OctoSuitVisual(VisualizationContext ctx, float partialTick) {

        transformedInstance = ctx.instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.DIRT.defaultBlockState())).createInstance();
        transformedInstance.light(15,15);
    }

    @Override
    public void update(float partialTick) {

    }

    @Override
    public void delete() {
        transformedInstance.delete();
    }

    @Override
    public void beginFrame(Context ctx) {
        transformedInstance.setIdentityTransform();
        transformedInstance.rotateY(Minecraft.getInstance().cameraEntity.getYRot());
        transformedInstance.translate(Minecraft.getInstance().cameraEntity.getPosition(ctx.partialTick()));
        transformedInstance.translate(-0.5f, 0, -0.5f);


        transformedInstance.translate(Minecraft.getInstance().cameraEntity.getForward());

        transformedInstance.setChanged();
    }
}
