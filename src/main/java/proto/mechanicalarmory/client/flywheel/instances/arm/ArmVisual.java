package proto.mechanicalarmory.client.flywheel.instances.arm;

import com.google.common.base.Function;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.*;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.NestedPlan;
import dev.engine_room.flywheel.lib.task.PlanMap;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.vanillin.item.ItemModels;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

    protected final List<BlockEntityVisual<?>> children = new ArrayList<>();
    protected final PlanMap<DynamicVisual, DynamicVisual.Context> dynamicVisuals = new PlanMap<>();
    protected final PlanMap<TickableVisual, TickableVisual.Context> tickableVisuals = new PlanMap<>();
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;
    private final Matrix4fc initialPose;
    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private TransformedInstance transformedInstance1;
    private ItemScalingTransforms itemScalingTransforms;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        int packedLight = LevelRenderer.getLightColor(level, pos.above());

        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY(), visualPos.getZ() + 0.5f);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree);
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");

        ItemStack stackOfBlockBelow = new ItemStack(this.level.getBlockState(pos.below()).getBlock());
        if (!stackOfBlockBelow.isEmpty()) {
            BakedModel itemModel = Minecraft.getInstance().getItemRenderer().getModel(stackOfBlockBelow, level, null, 42);
            //if (itemModel.isCustomRenderer()) {
                RenderSystem.recordRenderCall(() -> {
                    if (this.deleted) {
                        return;
                    }
                    CapturingBufferSource cbs = new CapturingBufferSource();
                    PoseStack pose = new PoseStack();

                    Minecraft.getInstance().getItemRenderer().render(stackOfBlockBelow, ItemDisplayContext.FIXED, false, pose, cbs, 0, 0, itemModel);
                    cbs.endLastBatch();

                    CapturedModel capturedModel = new CapturedModel(cbs);

                    itemScalingTransforms = new ItemScalingTransforms(0.375f / capturedModel.boundingSphere().w(), capturedModel.boundingSphere().negate(new Vector4f()));

                    transformedInstance1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, capturedModel).createInstance();
                    updateItemTransforms();

                    transformedInstance1.light(packedLight);
                });
//            } else {
//                transformedInstance1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, ItemModels.get(level, stackOfBlockBelow, ItemDisplayContext.FIXED)).createInstance();
//            }
        }
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        instanceTree.traverse(consumer);
        if (transformedInstance1 != null) {
            consumer.accept(transformedInstance1);
        }
    }

    @Override
    public void updateLight(float partialTick) {
        int packedLight = LevelRenderer.getLightColor(level, pos.above());

        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
        if (transformedInstance1 != null) {
            transformedInstance1.light(packedLight);
        }
        children.forEach(blockEntityVisual -> {
            if (blockEntityVisual instanceof LightUpdatedVisual luv) {
                luv.updateLight(partialTick);
            }
        });
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
        if (transformedInstance1 != null) {
            transformedInstance1.delete();
        }
        children.forEach(Visual::delete);
    }

    @Override
    public void setSectionCollector(SectionCollector sectionCollector) {
        this.lightSections = sectionCollector;
        lightSections.sections(LongSet.of(SectionPos.asLong(pos)));
    }

    void updateItemTransforms() {
        if (transformedInstance1 != null) {
            transformedInstance1.setIdentityTransform();
        }
        if (transformedInstance1 != null) {
            transformedInstance1.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            transformedInstance1.translate(0.5, 0, 0.5f);
        }

        Matrix4f mx = new Matrix4f();
        baseMotor.translateAndRotate(mx);
        firstArm.translateAndRotate(mx);
        secondArm.translateAndRotate(mx);

        if (transformedInstance1 != null) {
            transformedInstance1.mul(mx);
            transformedInstance1.translate(0, secondArm.initialPose().y / 16f + 0.25f, 0);
            transformedInstance1.scale(itemScalingTransforms.scale);
            transformedInstance1.translate(itemScalingTransforms.offset.x, itemScalingTransforms.offset.y, itemScalingTransforms.offset.z);

            transformedInstance1.setChanged();
        }
    }


    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return NestedPlan.of(
                RunnablePlan.of((context) -> {
                    float p = context.partialTick();
                    firstArm.xRot(Mth.lerp(p, blockEntity.getAnimationRotation(0)[0], blockEntity.getRotation(0)[0]));
                    firstArm.yRot(Mth.lerp(p, blockEntity.getAnimationRotation(0)[1], blockEntity.getRotation(0)[1]));

                    secondArm.xRot(Mth.lerp(p, blockEntity.getAnimationRotation(1)[0], blockEntity.getRotation(1)[0]));

                    updateItemTransforms();
                    instanceTree.updateInstancesStatic(initialPose);
                }),
                dynamicVisuals);
    }

    private record ItemScalingTransforms(float scale, Vector4f offset) {
    }
}
