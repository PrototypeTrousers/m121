package proto.mechanicalarmory.client.instances;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
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
    private final TransformedInstance cactus;

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
    private final Matrix4fc initialPose;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY(), visualPos.getZ() + 0.5f);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree);
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");

        cactus = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.CACTUS.defaultBlockState()))
                .createInstance();
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
        cactus.light(packedLight);
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
        return RunnablePlan.of(() -> {
            long currentTimeMillis = System.currentTimeMillis();
            // Angle in radians (2 * PI radians per cycle)

            double meaningfulSin = Math.sin((currentTimeMillis + 100 * visualPos.getX() - 100 * visualPos.getZ())/ 1000.0 % 10.0 * (2 * Math.PI) / 10.0);

            firstArm.xRot((float) meaningfulSin);
            secondArm.xRot((float) (meaningfulSin - Math.PI/4));

            cactus.setIdentityTransform();

            cactus.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            cactus.translate(.5f, 0, 0.5f);

            Matrix4f mx = new Matrix4f();
            baseMotor.translateAndRotate(mx);
            firstArm.translateAndRotate(mx);
            secondArm.translateAndRotate(mx);

            mx.scale(0.5f);
            cactus.mul(mx);

            cactus.translate(-0.5f, secondArm.initialPose().y/16f + 0.75f, - 0.5f);
            cactus.setChanged();

            instanceTree.updateInstancesStatic(initialPose);
        });
    }
}
