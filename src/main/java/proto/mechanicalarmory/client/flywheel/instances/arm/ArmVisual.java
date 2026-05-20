package proto.mechanicalarmory.client.flywheel.instances.arm;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
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

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

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
    public Plan<DynamicVisual.Context> planFrame() {
        return RunnablePlan.of((context) -> {
            if (!isVisible(context.frustum())) return;
            if (doDistanceLimitThisFrame(context)) return;

            // --- 1. SET LOCAL ANIMATIONS FIRST ---
            // Set local rotations based on block entity variables here before cascading.
            // (e.g., baseMotor.rotGoal.rotationY(blockEntity.getRotation(0)); )

            // --- 2. DEFINE THE ROOT WORLD POSITION ---
            // Create the starting matrix right at the center of the BlockEntity
            Matrix4f rootWorldMatrix = new Matrix4f()
                    .translate(
                            visualPos.getX() + 0.5f,
                            visualPos.getY(),
                            visualPos.getZ() + 0.5f
                    );

            // --- 3. CASCADE ALL TRANSFORMS ---
            // This calculates every child's world position and pushes it to VRAM!
            instanceTree.cascadeWorldTransforms(rootWorldMatrix);

            instanceTree.propagateAnimation(true);
        });
    }
}
