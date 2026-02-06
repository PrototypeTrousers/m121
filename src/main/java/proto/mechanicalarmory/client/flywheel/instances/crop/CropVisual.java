package proto.mechanicalarmory.client.flywheel.instances.crop;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.blocks.CropBlockEntity;

import java.util.function.Consumer;

public class CropVisual extends AbstractBlockEntityVisual<CropBlockEntity> {
    TransformedInstance cropInstance;

    public CropVisual(VisualizationContext ctx, CropBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        cropInstance = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(blockEntity.getBlockState())).createInstance();
        cropInstance.translate(getVisualPosition().getX(), getVisualPosition().getY(), getVisualPosition().getZ());
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        consumer.accept(cropInstance);
    }

    @Override
    public void updateLight(float partialTick) {
        cropInstance.light(LevelRenderer.getLightColor(level, pos));
    }

    @Override
    protected void _delete() {
        cropInstance.delete();
    }
}
