package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.Instancer;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.backend.engine.InstanceHandleImpl;
import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements TickableVisual, LightUpdatedVisual {

    private static Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> modelCache = new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());
    private final InterpolatingInstanceTree instanceTree;
    private final @Nullable InterpolatingInstanceTree firstArm;
    private final @Nullable InterpolatingInstanceTree secondArm;
    private final @Nullable InterpolatingInstanceTree baseMotor;
    private final @Nullable InterpolatingInstanceTree itemAttachment;
    private final @Nullable TransformedInstance itemAttachmentInstance;
    private final @Nullable InterpolatingInstanceTree base;

    public static int visuals;
    ModelTree modelTree = MechanicalArmoryClient.fullArmModelTree;
    int packedLight;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        packedLight = LevelRenderer.getLightColor(level, pos.above());

        instanceTree = InterpolatingInstanceTree.create(instancerProvider(), modelTree);
        instanceTree.setChanged();
        baseMotor = instanceTree.child("BaseMotor");
        base = instanceTree.child("Base");
        firstArm = null;
        secondArm = null;
        itemAttachment = null;
        itemAttachmentInstance = null;//itemAttachment.instance();
        visuals++;
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        instanceTree.traverse(consumer);
    }

    @Override
    public void updateLight(float partialTick) {
        packedLight = LevelRenderer.getLightColor(level, pos.above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
        visuals--;
    }

    @Override
    public void setSectionCollector(SectionCollector sectionCollector) {
        this.lightSections = sectionCollector;
        lightSections.sections(LongSet.of(SectionPos.asLong(pos)));
    }

    void updateItemTransforms(float scale, float offsetX, float offsetY, float offsetZ) {
        itemAttachmentInstance.translate(0, secondArm.initialPose().y / 16f + 0.25f, 0);
        itemAttachmentInstance.scale(scale);
        itemAttachmentInstance.translate(offsetX, offsetY, offsetZ);
        itemAttachmentInstance.setChanged();
    }

    @Override
    public Plan<Context> planTick() {
        return RunnablePlan.of((context) -> {
//            if (!isVisible(context.frustum())) return;
//            if (doDistanceLimitThisFrame(context)) return;
            baseMotor.child(0).instance().posGoal.set(0, 0.5f, 0);
            baseMotor.child(0).instance().posFrom.set(0, 0.5f, 0);
            base.child(0).instance().posGoal.set(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            base.child(0).instance().posFrom.set(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            firstArm.child(0).instance().posGoal.set(0, 1, 0);
            firstArm.child(0).instance().posFrom.set(0, 1, 0);
            instanceTree.cascadeWorldTransforms();
        });
    }
}
