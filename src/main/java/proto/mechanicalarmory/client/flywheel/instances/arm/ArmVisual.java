package proto.mechanicalarmory.client.flywheel.instances.arm;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.*;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
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
    ModelTree modelTree = MechanicalArmoryClient.fullArmModelTree;
    int packedLight;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        packedLight = LevelRenderer.getLightColor(level, pos.above());

        instanceTree = InterpolatingInstanceTree.create(instancerProvider(), modelTree);
        InterpolatingInstanceTree.idx = 0;
        instanceTree.setChanged();
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");
        itemAttachment = secondArm.child("ItemAttach");
        itemAttachmentInstance = null;//itemAttachment.instance();
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
            baseMotor.child(0).instance().posGoal.set(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            baseMotor.child(0).instance().posFrom.set(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            firstArm.child(0).instance().posGoal.set(visualPos.getX(), visualPos.getY() + 1, visualPos.getZ());
            firstArm.child(0).instance().posFrom.set(visualPos.getX(), visualPos.getY()+ 1, visualPos.getZ());
            instanceTree.cascadeWorldTransforms();
        });
    }
}
