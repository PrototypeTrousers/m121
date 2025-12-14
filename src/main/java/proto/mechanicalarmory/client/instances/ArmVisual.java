package proto.mechanicalarmory.client.instances;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> {
    private final TransformedInstance base;

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        base = instancerProvider().instancer(InstanceTypes.TRANSFORMED, MechanicalArmoryClient.gltfFlywheelModel)
                .createInstance();
        var msr = TransformStack.of(poseStack);
        msr.translate(getVisualPosition());
        msr.center();
        base.setTransform(poseStack);
        base.light(255);
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
    }

    @Override
    protected void _delete() {
        base.delete();
    }


}
