package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.material.Materials;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import dev.engine_room.flywheel.lib.model.baked.BakedModelBuilder;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import dev.engine_room.vanillin.item.ItemModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static dev.engine_room.vanillin.item.ItemModels.getActualBakedModel;

public class VanillaBlockEntityVisual extends AbstractBlockEntityVisual<BlockEntity> implements SimpleTickableVisual, SimpleDynamicVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final Matrix4f initialPose;
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final BlockRendererBuilder<BlockEntity> builder;
    private final ArrayObjectPool<BlockEntityRenderer<BlockEntity>> rendererPool;
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f translation1 = new Vector3f();
    Quaternionf rotation1 = new Quaternionf();
    Vector3f scale1 = new Vector3f();
    Vector3f translation2 = new Vector3f();
    Quaternionf rotation2 = new Quaternionf();
    Vector3f scale2 = new Vector3f();
    int partIdx;

    public VanillaBlockEntityVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        initialPose = new Matrix4f().translate(visualPos.getX() + 0.5f, visualPos.getY() + 1.5f, visualPos.getZ() + 0.5f);
        visualBufferSource = new VisualBufferSource(this);

        BlockEntityRendererProvider.Context blockentityrendererprovider$context = new BlockEntityRendererProvider.Context(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getBlockRenderer(),
                Minecraft.getInstance().getItemRenderer(),
                Minecraft.getInstance().getEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels(),
                Minecraft.getInstance().font);

        builder = new BlockRendererBuilder(blockEntity.getType(), blockentityrendererprovider$context);
        rendererPool = new ArrayObjectPool<>(1, builder);

        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        poseStackVisual.last().pose().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        berenderer.render(blockEntity, partialTick, poseStackVisual, visualBufferSource, LevelRenderer.getLightColor(level, pos.above()), 0);
        rendererPool.release(berenderer);
        visualBufferSource.setRendered(true);
        poseStackVisual.setRendered();

        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            for (InterpolatedTransformedInstance ti : key) {
                ti.instance.light(LevelRenderer.getLightColor(level, pos.above()));
                ti.current.setTranslation(ti.current.m30() + visualPos.getX(), ti.current.m31() + visualPos.getY(), ti.current.m32() + visualPos.getZ());
                ti.instance.setTransform(ti.current).setChanged();
                ti.current.setTranslation(ti.current.m30() - visualPos.getX(), ti.current.m31() - visualPos.getY(), ti.current.m32() - visualPos.getZ());
            }
        }
    }

    public void addInterpolatedTransformedInstance(int depth, ModelPart modelPart, Material material) {
        while (transformedInstances.size() <= depth) {
            transformedInstances.add(Collections.EMPTY_LIST);
        }
        if (transformedInstances.get(depth) == Collections.EMPTY_LIST) {
            transformedInstances.set(depth, new ArrayList<>());
        }

        transformedInstances.get(depth).add(new InterpolatedTransformedInstance(instancerProvider().instancer(
                        InstanceTypes.TRANSFORMED, VanillaModel.cachedOf(modelPart, material))
                .createInstance(), new Matrix4f(), new Matrix4f()));
        partIdx++;
    }

    public void addInterpolatedItemTransformedInstance(int depth, ItemStack itemStack) {
        while (transformedInstances.size() <= depth) {
            transformedInstances.add(Collections.EMPTY_LIST);
        }
        if (transformedInstances.get(depth) == Collections.EMPTY_LIST) {
            transformedInstances.set(depth, new ArrayList<>());
        }

        boolean cull = !(itemStack.getItem() instanceof BlockItem block) || !(block.getBlock() instanceof HalfTransparentBlock) && !(block.getBlock() instanceof StainedGlassPaneBlock);
        var material = ModelUtil.getItemMaterial(ItemBlockRenderTypes.getRenderType(itemStack, cull));

        if (material == null) {
            material = Materials.TRANSLUCENT_ENTITY;
        }

        if (itemStack.getItem() instanceof BlockItem && material.transparency() == Transparency.TRANSLUCENT) {
            material = SimpleMaterial.builderOf(material)
                    .transparency(Transparency.ORDER_INDEPENDENT)
                    .build();
        }

        BakedModel bm = Minecraft.getInstance().getItemRenderer().getModel(itemStack, null, null, 42);
        //BakedModel p_model = ClientHooks.handleCameraTransforms(new PoseStack(), bm, ItemDisplayContext.GUI, false);

        Model model = ItemModels.bakeModel(bm, ItemDisplayContext.FIXED, material,itemStack.hasFoil());

        transformedInstances.get(depth).add(new InterpolatedTransformedInstance(instancerProvider().instancer(
                        InstanceTypes.TRANSFORMED, model)
                .createInstance(), new Matrix4f(), new Matrix4f()));
        partIdx++;
    }

    public void updateTransforms(int depth, Matrix4f p) {
        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);
        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
        }
    }

    public void updateItemTransforms(int depth, Matrix4f p) {
        //WHY????????
        p.rotate((float) (Math.PI/2), new Vector3f(0,0,1));
        p.translate(new Vector3f(0.5f,-0.5f,0));
        p.translate(new Vector3f(0,0,0.5f));
        //???????????
        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);
        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
        }
    }

    public Matrix4f interpolate(Matrix4f m1, Matrix4f m2, float t) {
        // 1. Decompose Matrix 1
        m1.getTranslation(translation1);
        m1.getUnnormalizedRotation(rotation1);
        m1.getScale(scale1);
        // 2. Decompose Matrix 2

        m2.getTranslation(translation2);
        m2.getUnnormalizedRotation(rotation2);
        m2.getScale(scale2);

        // 3. Interpolate components
        // Translation: Linear Interpolation (Lerp)
        Vector3f lerpTranslation = translation1.lerp(translation2, t);
        // Scale: Linear Interpolation (Lerp)
        Vector3f lerpScale = scale1.lerp(scale2, t);
        // Rotation: Spherical Linear Interpolation (Slerp)
        Quaternionf slerpRotation = rotation1.nlerp(rotation2, t);

        // 4. Recompose into a new Matrix
        mutableInterpolationMatrix4f.translationRotateScale(lerpTranslation, slerpRotation, lerpScale);

        return mutableInterpolationMatrix4f;
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        if (doDistanceLimitThisFrame(ctx) || !isVisible(ctx.frustum())) {
            return;
        }

        float pt = ctx.partialTick();

        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (InterpolatedTransformedInstance ti : list) {
                // 1. Check if an update is even needed
                // Only update if the transformation has actually changed between ticks
                if (!ti.previous.equals(ti.current)) {

                    // 2. Interpolate into a temporary matrix
                    // Do NOT modify ti.current or ti.previous here!
                    Matrix4f interpolated = interpolate(ti.previous, ti.current, pt);

                    // 3. Apply to the rendering instance
                    ti.instance.setTransform(interpolated);
                    ti.instance.setChanged();
                }
            }
        }
    }

    @Override
    public void tick(TickableVisual.Context context) {
        poseStackVisual.setDepth(0);
        BlockEntityRenderer<BlockEntity> berenderer = rendererPool.get();
        berenderer.render(blockEntity, 0, poseStackVisual, visualBufferSource, LevelRenderer.getLightColor(level, pos.above()), 0);
        rendererPool.release(berenderer);
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            key.forEach(ti -> ti.instance.light(LevelRenderer.getLightColor(level, pos.above())));
        }
    }

    @Override
    protected void _delete() {
        for (List<InterpolatedTransformedInstance> v : transformedInstances) {
            v.forEach(ti -> ti.instance.delete());
        }
    }

    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
    }

    public record InterpolatedTransformedInstance(TransformedInstance instance, Matrix4f current, Matrix4f previous) {

    }
}
