package proto.mechanicalarmory.client.flywheel.instances.arm;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.*;
import dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer;
import dev.engine_room.flywheel.api.visualization.VisualEmbedding;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.ForEachPlan;
import dev.engine_room.flywheel.lib.task.NestedPlan;
import dev.engine_room.flywheel.lib.task.PlanMap;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.vanillin.item.ItemModels;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.SectionPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, TickableVisual, LightUpdatedVisual {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;
    private TransformedInstance transformedInstance1;

    protected final List<BlockEntityVisual<?>> children = new ArrayList<>();
    protected final PlanMap<DynamicVisual, DynamicVisual.Context> dynamicVisuals = new PlanMap<>();
    protected final PlanMap<TickableVisual, TickableVisual.Context> tickableVisuals = new PlanMap<>();



    private VisualEmbedding embedding;
    private BlockEntity embeddedEntity;

    private static final Material MATERIAL = SimpleMaterial.builder()
            .mipmap(false)
            .build();

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
    private final Matrix4fc initialPose;

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
            if (itemModel.isCustomRenderer()) {
                RenderSystem.recordRenderCall(() -> {
                    CapturingBufferSource cbs = new CapturingBufferSource();
                    PoseStack pose = new PoseStack();
                    Minecraft.getInstance().getItemRenderer().render(stackOfBlockBelow, ItemDisplayContext.FIXED, false, pose, cbs, 0, 0, itemModel);
                    cbs.endLastBatch();
                    transformedInstance1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, new CapturedModel(cbs)).createInstance();
                    transformedInstance1.light(packedLight);
                });
            } else {
                transformedInstance1 = instancerProvider().instancer(InstanceTypes.TRANSFORMED, ItemModels.get(level, stackOfBlockBelow, ItemDisplayContext.FIXED)).createInstance();
            }
        }

//        embedding = ctx.createEmbedding(renderOrigin());
//
//        SinglePosVirtualBlockGetter lv = SinglePosVirtualBlockGetter.createFullBright();
//        lv.blockState(Blocks.ENCHANTING_TABLE.defaultBlockState());
//        embeddedEntity = new EnchantingTableBlockEntity(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
//
//
//        lv.blockEntity(embeddedEntity);
//        lv.pos(pos);
//        embeddedEntity.setLevel(level);
//
//        setupVisualizer(embeddedEntity, partialTick);


    }

    @SuppressWarnings("unchecked")
    protected <T extends BlockEntity> void setupVisualizer(T be, float partialTicks) {
        BlockEntityVisualizer<? super T> visualizer = (BlockEntityVisualizer<? super T>) VisualizerRegistry.getVisualizer(be.getType());
        if (visualizer == null) {
            return;
        }

        BlockEntityVisual<? super T> visual = visualizer.createVisual(this.embedding, be, partialTicks);
        children.add(visual);

        if (visual instanceof DynamicVisual dynamic) {
            dynamicVisuals.add(dynamic, dynamic.planFrame());
        }

        if (visual instanceof TickableVisual tickable) {
            tickableVisuals.add(tickable, tickable.planTick());
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


    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return NestedPlan.of(
                RunnablePlan.of(() -> {
                    firstArm.xRot((float) -Math.PI / 4);
                    secondArm.xRot((float) Math.PI / 2);

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

                    //mx.scale(0.5f);
                    if (transformedInstance1 != null) {
                        transformedInstance1.mul(mx);
                        transformedInstance1.translate(0, secondArm.initialPose().y / 16f + 0.25f, 0);
                        transformedInstance1.setChanged();
                    }
                    if (embedding != null) {
                        embedding.transforms(mx, new Matrix3f());
                    }
                    instanceTree.updateInstancesStatic(initialPose);
                }),
                dynamicVisuals);
    }

    @Override
    public Plan<TickableVisual.Context> planTick() {

        return NestedPlan.of(
                tickableVisuals);
    }
}
