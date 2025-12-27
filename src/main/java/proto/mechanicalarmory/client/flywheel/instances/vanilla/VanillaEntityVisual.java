package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import dev.engine_room.vanillin.item.ItemModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class VanillaEntityVisual extends AbstractEntityVisual<Entity> implements SimpleTickableVisual, SimpleDynamicVisual, SinkBufferSourceVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final EntityRendererBuilder<Entity> builder;
    private final ArrayObjectPool<EntityRenderer<Entity>> rendererPool;
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f translation1 = new Vector3f();
    Quaternionf rotation1 = new Quaternionf();
    Vector3f scale1 = new Vector3f();
    Vector3f translation2 = new Vector3f();
    Quaternionf rotation2 = new Quaternionf();
    Vector3f scale2 = new Vector3f();
    private int light;

    public VanillaEntityVisual(VisualizationContext ctx, Entity entity, float partialTick) {
        super(ctx, entity, partialTick);
        light = computePackedLight(partialTick);
        visualBufferSource = new VisualBufferSource(this);

        EntityRendererProvider.Context entityrendererprovider$context = new EntityRendererProvider.Context(
                Minecraft.getInstance().getEntityRenderDispatcher(),
                Minecraft.getInstance().getItemRenderer(),
                Minecraft.getInstance().getBlockRenderer(),
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer(),
                Minecraft.getInstance().getResourceManager(),
                Minecraft.getInstance().getEntityModels(),
                Minecraft.getInstance().font);

        builder = new EntityRendererBuilder(entity.getType(), entityrendererprovider$context);
        rendererPool = new ArrayObjectPool<>(1, builder);

        EntityRenderer<Entity> berenderer = rendererPool.get();
        poseStackVisual.last().pose().setTranslation(getVisualPosition().x(), getVisualPosition().y(), getVisualPosition().z());
        berenderer.render(entity, entity.getYHeadRot(), partialTick, poseStackVisual, visualBufferSource, light);
        rendererPool.release(berenderer);
        visualBufferSource.setRendered(true);
        poseStackVisual.setRendered();

        for (List<InterpolatedTransformedInstance> key : transformedInstances) {
            for (InterpolatedTransformedInstance ti : key) {
                ti.instance.light(light);
                ti.current.setTranslation(ti.current.m30() + getVisualPosition().x, ti.current.m31() + getVisualPosition().y, ti.current.m32() + getVisualPosition().z);
                ti.instance.setTransform(ti.current).setChanged();
                ti.current.setTranslation(ti.current.m30() - getVisualPosition().x, ti.current.m31() - getVisualPosition().y, ti.current.m32() - getVisualPosition().z);
            }
        }
    }

    @Override
    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
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
    }

    public void addInterpolatedItemTransformedInstance(int depth, ItemStack itemStack) {
        while (transformedInstances.size() <= depth) {
            transformedInstances.add(Collections.EMPTY_LIST);
        }
        if (transformedInstances.get(depth) == Collections.EMPTY_LIST) {
            transformedInstances.set(depth, new ArrayList<>());
        }

        if (!transformedInstances.get(depth).isEmpty()) {
            return;
        }

        InterpolatedTransformedInstance newInstance = new InterpolatedTransformedInstance(instancerProvider().instancer(
                        InstanceTypes.TRANSFORMED, ItemModels.get(level, itemStack, ItemDisplayContext.FIXED))
                .createInstance(), new Matrix4f(), new Matrix4f());
        newInstance.instance.light(light);
        transformedInstances.get(depth).add(newInstance);
    }

    public void updateTransforms(int depth, Matrix4f p) {
        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);

        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
            ti.lastTick = level.getGameTime();
        }
    }

    public void updateItemTransforms(int depth, Matrix4f p) {
        //WHY????????
        p.translate(+0.5f, +0.5f, +0.5f);
        p.rotateZ((float)-Math.PI/2);
        //???????????
        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);

        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
            ti.lastTick = level.getGameTime();
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
        if (!isVisible(ctx.frustum())) {
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
        long lastTick = level.getGameTime();
        poseStackVisual.setDepth(0);
        EntityRenderer<Entity> berenderer = rendererPool.get();
        poseStackVisual.last().pose().setTranslation(getVisualPosition().x(), getVisualPosition().y(), getVisualPosition().z());
        berenderer.render(entity, entity.getYHeadRot(), Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false) , poseStackVisual, visualBufferSource, light);
        rendererPool.release(berenderer);
        for (List<InterpolatedTransformedInstance> list : transformedInstances) {
            for (Iterator<InterpolatedTransformedInstance> iterator = list.iterator(); iterator.hasNext(); ) {
                InterpolatedTransformedInstance ti = iterator.next();
                if (ti.lastTick != lastTick) {
                    ti.instance.delete();
                    iterator.remove();
                }
            }
        }
    }

    @Override
    protected void _delete() {
        for (List<InterpolatedTransformedInstance> v : transformedInstances) {
            v.forEach(ti -> ti.instance.delete());
        }
    }
}
