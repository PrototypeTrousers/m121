package proto.mechanicalarmory.client.instances;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.BlockModelBuilder;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.model.baked.SinglePosVirtualBlockGetter;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.vanillin.item.ItemModels;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BellRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.AtlasSet;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.model.pipeline.RemappingVertexPipeline;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.BERDynamicBakedModel;
import proto.mechanicalarmory.client.CapturedModel;
import proto.mechanicalarmory.client.mixin.BufferSourceAccessor;
import proto.mechanicalarmory.client.mixin.RenderTypeAccessor;
import proto.mechanicalarmory.client.mixin.TextureStateShardAccessor;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.function.Consumer;

import static net.minecraft.client.renderer.Sheets.BED_SHEET;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements DynamicVisual, LightUpdatedVisual {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    private final InstanceTree instanceTree;
    private final @Nullable InstanceTree firstArm;
    private final @Nullable InstanceTree secondArm;
    private final @Nullable InstanceTree baseMotor;
    private final TransformedInstance cactus;

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
        lv.blockState(Blocks.BLACK_BED.defaultBlockState());
        BedBlockEntity bbe = new BedBlockEntity(pos, Blocks.BLACK_BED.defaultBlockState());
        lv.blockEntity(bbe);
        lv.pos(pos);
        PoseStack pose = new PoseStack();
        pose.pushPose();
        pose.setIdentity();
        //pose.translate(pos.getX(), pos.getY(), pos.getZ());
//        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.DIRT.defaultBlockState(), pose, br.bufferSource(), computePackedLight(), 0);
        Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(bbe, new PoseStack(), br.bufferSource(), computePackedLight(), 0);

        MultiBufferSource.BufferSource bs = br.bufferSource();
        Map<RenderType, BufferBuilder> mp = ((BufferSourceAccessor) bs).getStartedBuilders();

        CapturedModel model = new CapturedModel(mp);

        cactus = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, model)
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
        cactus.delete();
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

            firstArm.xRot((float) meaningfulSin /2);
            secondArm.xRot((float) (meaningfulSin - Math.PI/4)/2);

            cactus.setIdentityTransform();

            cactus.translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            cactus.translate(0.5, 0, 0.5f);

            Matrix4f mx = new Matrix4f();
            baseMotor.translateAndRotate(mx);
            firstArm.translateAndRotate(mx);
            secondArm.translateAndRotate(mx);

            //mx.scale(0.5f);
            cactus.mul(mx);

            cactus.translate(0, secondArm.initialPose().y/16f, 0);

            cactus.setChanged();

            instanceTree.updateInstancesStatic(initialPose);
        });
    }
}
