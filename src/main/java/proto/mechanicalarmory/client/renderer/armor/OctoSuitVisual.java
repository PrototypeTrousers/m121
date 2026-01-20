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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    TransformedInstance transformedInstance;
    Entity holderEntity;
    Vector3f visualPos;

    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity , float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;
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
        transformedInstance.translate(-0.5f, 0.5f, -0.5f);
                transformedInstance.translate(holderEntity.getPosition(ctx.partialTick()));
        transformedInstance.translate(
                Vec3.directionFromRotation(
                        0, (float) ( 180 + holderEntity.getPreciseBodyRotation(ctx.partialTick()))));

        transformedInstance.rotateYCentered(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));

        transformedInstance.setChanged();
    }
}
