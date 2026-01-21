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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

    private static Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> modelCache = new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;
    private final @Nullable InstanceTree itemAttachment;
    private final @Nullable TransformedInstance itemAttachmentInstance;
    private final Matrix4fc initialPose;
    ModelTree modelTree = MechanicalArmoryClient.fullArmModelTree;
    int packedLight;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        packedLight = LevelRenderer.getLightColor(level, pos.above());

        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY(), visualPos.getZ() + 0.5f);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree);
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");
        itemAttachment = secondArm.child("ItemAttach");
        itemAttachmentInstance = itemAttachment.instance();
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
            if (!isVisible(context.frustum())) {
                return;
            }

            if (doDistanceLimitThisFrame(context)) {
                return;
            }

            ItemStack holdingItem = blockEntity.getItemStack();

            CapturedModel capturedModel = modelCache.get(holdingItem);
            if (capturedModel != null) {
                instancerProvider().instancer(InstanceTypes.TRANSFORMED, capturedModel).stealInstance(itemAttachment.instance());

                Vector4fc boundSphere = capturedModel.boundingSphere();
                updateItemTransforms(0.375f / boundSphere.w(), -boundSphere.x(), -boundSphere.y(), -boundSphere.z());
            } else {
                RenderSystem.recordRenderCall(() -> {
                    BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(holdingItem, level, null, 42);

                    if (this.deleted) {
                        return;
                    }
                    CapturingBufferSource cbs = new CapturingBufferSource();
                    PoseStack pose = new PoseStack();

                    Minecraft.getInstance().getItemRenderer().render(holdingItem, ItemDisplayContext.FIXED, false, pose, cbs, 0, 0, itemModel);
                    cbs.endLastBatch();

                    CapturedModel newCapturedModel = new CapturedModel(cbs);
                    modelCache.put(holdingItem, newCapturedModel);
                });
            }

            float p = context.partialTick();
            firstArm.xRot(Mth.lerp(p, blockEntity.getAnimationRotation(0)[0], blockEntity.getRotation(0)[0]));
            firstArm.yRot(Mth.lerp(p, blockEntity.getAnimationRotation(0)[1], blockEntity.getRotation(0)[1]));

            secondArm.xRot(Mth.lerp(p, blockEntity.getAnimationRotation(1)[0], blockEntity.getRotation(1)[0]));

            Vector4fc boundSphere = capturedModel.boundingSphere();
            updateItemTransforms(0.375f / boundSphere.w(), -boundSphere.x(), -boundSphere.y(), -boundSphere.z());

            instanceTree.updateInstancesStatic(initialPose);
        });
    }
}
