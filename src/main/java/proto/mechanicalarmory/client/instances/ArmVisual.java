package proto.mechanicalarmory.client.instances;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.CapturedModel;
import proto.mechanicalarmory.client.mixin.BufferSourceAccessor;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;
    private TransformedInstance transformedInstance1;
    private final TransformedInstance transformedInstance2;

    private static final Material MATERIAL = SimpleMaterial.builder()
            .mipmap(false)
            .build();

    private final RecyclingPoseStack poseStack = new RecyclingPoseStack();
    private final Matrix4fc initialPose;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY(), visualPos.getZ() + 0.5f);

        instanceTree = InstanceTree.create(instancerProvider(), modelTree);
        baseMotor = instanceTree.child("BaseMotor");
        firstArm = baseMotor.child("FirstArm");
        secondArm = firstArm.child("SecondArm");

        var br = new RenderBuffers(1);
        SinglePosVirtualBlockGetter lv = SinglePosVirtualBlockGetter.createFullBright();
        lv.blockState(Blocks.ENCHANTING_TABLE.defaultBlockState());
        EnchantingTableBlockEntity bbe = new EnchantingTableBlockEntity(pos, Blocks.ENCHANTING_TABLE.defaultBlockState());
        lv.blockEntity(bbe);
        lv.pos(pos);
        bbe.setLevel(level);

        transformedInstance2 = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.AIR.defaultBlockState()))
                .createInstance();


        RenderSystem.recordRenderCall(() -> {
                Block below = level.getBlockState(pos.below()).getBlock();
                int packedLight = LevelRenderer.getLightColor(level, pos.above());
                Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(below.asItem()), ItemDisplayContext.GROUND, packedLight, 0, new PoseStack(), br.bufferSource(), null, 42);
//        render(new ItemStack(Blocks.ENCHANTING_TABLE.asItem()), ItemDisplayContext.GROUND, false, new PoseStack(), br.bufferSource(), LevelRenderer.getLightColor(level, pos.above()), 0 );
                CapturedModel model = new CapturedModel(br);

                transformedInstance1 = instancerProvider()
                        .instancer(InstanceTypes.TRANSFORMED, model)
                        .createInstance();
            transformedInstance1.light(packedLight);

        });

        //pose.translate(pos.getX(), pos.getY(), pos.getZ());
//        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.DIRT.defaultBlockState(), pose, br.bufferSource(), computePackedLight(), 0);
        //Minecraft.getInstance().getBlockEntityRenderDispatcher().render(bbe, partialTick, new PoseStack(), br.bufferSource());
        //Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(bbe, new PoseStack(), br.bufferSource(), LevelRenderer.getLightColor(level, pos.above()), 0);

    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        instanceTree.traverse(consumer);
        if (transformedInstance1 != null) {
            consumer.accept(transformedInstance1);
        }
        consumer.accept(transformedInstance2);
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
        transformedInstance2.light(packedLight);
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
        if (transformedInstance1 != null) {
            transformedInstance1.delete();
        }
        transformedInstance2.delete();
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

//            double meaningfulSin = Math.sin((currentTimeMillis + 100 * visualPos.getX() - 100 * visualPos.getZ()) / 1000.0 % 10.0 * (2 * Math.PI) / 10.0);
//
//            firstArm.xRot((float) meaningfulSin / 2);
//            secondArm.xRot((float) (meaningfulSin - Math.PI / 4) / 2);

            firstArm.xRot((float) -Math.PI /4);
            secondArm.xRot((float) Math.PI/2);

            if (transformedInstance1 != null) {
                transformedInstance1.setIdentityTransform();
            }
            transformedInstance2.setIdentityTransform();
            if (transformedInstance1 != null) {
                transformedInstance1.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
                transformedInstance1.translate(0.5, 0, 0.5f);
            }

            transformedInstance2.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            transformedInstance2.translate(0.5, 0, 0.5f);

            Matrix4f mx = new Matrix4f();
            baseMotor.translateAndRotate(mx);
            firstArm.translateAndRotate(mx);
            secondArm.translateAndRotate(mx);

            //mx.scale(0.5f);
            if (transformedInstance1 != null) {
                transformedInstance1.mul(mx);
                transformedInstance1.translate(0, secondArm.initialPose().y / 16f, 0);
                transformedInstance1.setChanged();
            }

            transformedInstance2.mul(mx);

            transformedInstance2.translate(0, secondArm.initialPose().y / 16f, 0);

            transformedInstance2.setChanged();

            instanceTree.updateInstancesStatic(initialPose);
        });
    }
}
