package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.coralblocks.coralpool.ArrayObjectPool;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visual.SimpleTickableVisual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VanillaEntityVisual extends AbstractEntityVisual<Entity> implements SimpleTickableVisual, SimpleDynamicVisual, SinkBufferSourceVisual {
    final public VisualBufferSource visualBufferSource;
    final public List<List<InterpolatedTransformedInstance>> transformedInstances = new ArrayList<>();
    private final PoseStackVisual poseStackVisual = new PoseStackVisual(this);
    private final EntityRendererBuilder<Entity> builder;
    private final ArrayObjectPool<EntityRenderer<Entity>> rendererPool;
    private final Matrix4f mutableInterpolationMatrix4f = new Matrix4f();
    Vector3f[] interpolationVecs = new Vector3f[]{new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
    Quaternionf[] interpolationQuats = new Quaternionf[]{new Quaternionf(), new Quaternionf()};
    private final int light;

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
    public LevelAccessor getLevel() {
        return this.level;
    }

    @Override
    public Matrix4f getMutableInterpolationMatrix4f() {
        return mutableInterpolationMatrix4f;
    }

    public Vector3f[] getInterpolationVecs() {
        return interpolationVecs;
    }

    public Quaternionf[] getInterpolationQuats() {
        return interpolationQuats;
    }

    @Override
    public int getLight() {
        return light;
    }

    @Override
    public VisualBufferSource getBufferSource() {
        return visualBufferSource;
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
        berenderer.render(entity, entity.getYHeadRot(), Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false), poseStackVisual, visualBufferSource, light);
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

    @Override
    public List<List<InterpolatedTransformedInstance>> getTransformedInstances() {
        return transformedInstances;
    }

    @Override
    public InstancerProvider getInstanceProvider() {
        return instancerProvider();
    }
}
