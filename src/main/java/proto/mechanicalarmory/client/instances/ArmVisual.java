package proto.mechanicalarmory.client.instances;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.task.TaskExecutor;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.FlatLit;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.NestedPlan;
import dev.engine_room.flywheel.lib.task.SimplePlan;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
    private final Matrix4fc initialPose;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY(), visualPos.getZ() + 0.5f);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree);
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        instanceTree.traverse(consumer);
    }

    @Override
    public void updateLight(float partialTick) {
        int packedLight = LevelRenderer.getLightColor(level, pos.above());

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
    public void setSectionCollector(SectionCollector sectionCollector) {
        this.lightSections = sectionCollector;
        lightSections.sections(LongSet.of(SectionPos.asLong(pos)));
    }

    @Override
    public Plan<Context> planFrame() {
        return new Plan<>() {

            @Override
            public void execute(TaskExecutor taskExecutor, Context context, Runnable onCompletion) {
                long currentTimeMillis = System.currentTimeMillis();
                // Angle in radians (2 * PI radians per cycle)

                double meaningfulSin = Math.sin((currentTimeMillis + 100 * visualPos.getX() - 100 * visualPos.getZ())/ 1000.0 % 10.0 * (2 * Math.PI) / 10.0);


                firstArm.xRot((float) meaningfulSin);
                secondArm.xRot((float) (meaningfulSin - Math.PI/4));

                instanceTree.updateInstancesStatic(initialPose);
            }

            @Override
            public Plan<Context> then(Plan<Context> plan) {
                return null;
            }

            @Override
            public Plan<Context> and(Plan<Context> plan) {
                return null;
            }
        };
    }
}
