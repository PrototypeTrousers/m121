package proto.mechanicalarmory.client.instances;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> {
    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private final InstanceTree instanceTree;

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree );

        var msr = TransformStack.of(poseStack);
        msr.translate(getVisualPosition());
        msr.center();
        instanceTree.updateInstancesStatic(poseStack.last().pose());
        instanceTree.instance().light(255);

    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
    }

    @Override
    public void updateLight(float partialTick) {
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
    }
}
