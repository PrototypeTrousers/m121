package proto.mechanicalarmory.client.flywheel.instances.shredder;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.gltf.MyInstanceTree;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

import java.util.function.Consumer;

public class ShredderVisual extends AbstractBlockEntityVisual<ShredderEntity> implements DynamicVisual, TickableVisual {
    private final MyInstanceTree instanceTree;
    private final MyInstanceTree leftBlade;
    private final MyInstanceTree rigthBlade;
    private final Matrix4fc initialPose;
    float rotation;


    public ShredderVisual(VisualizationContext ctx, ShredderEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        instanceTree = MyInstanceTree.create(instancerProvider(), MechanicalArmoryClient.shredderModelTree);
        leftBlade=instanceTree.child("LeftBlade");
        rigthBlade=instanceTree.child("RightBlade");
        initialPose = new Matrix4f().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        instanceTree.updateInstancesStatic(initialPose);
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        var packedLight = LevelRenderer.getLightColor(level, pos.above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
    }

    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return RunnablePlan.of((context) -> {
            if (!isVisible(context.frustum())) {
                return;
            }

            if (doDistanceLimitThisFrame(context)) {
                return;
            }

            float partialTick = context.partialTick();
            float interpolatedRotation = (float) Mth.lerp(partialTick,
                    (((rotation - 1) * 6f) * Math.PI / 180f),
                    ((rotation * 6f) * Math.PI / 180f));

            leftBlade.zRot(-interpolatedRotation);
            rigthBlade.zRot(interpolatedRotation);

            instanceTree.updateInstancesStatic(initialPose);
        });
    }

    @Override
    public Plan<TickableVisual.Context> planTick() {
        return RunnablePlan.of((context) -> {
            rotation = ++rotation % 20;
        });
    }
}
