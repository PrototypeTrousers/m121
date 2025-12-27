package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.vanillin.item.ItemModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface SinkBufferSourceVisual {

    int getLight();

    List<List<InterpolatedTransformedInstance>> getTransformedInstances();

    InstancerProvider getInstanceProvider();

    LevelAccessor getLevel();

    Matrix4f getMutableInterpolationMatrix4f();

    Vector3f[] getInterpolationVecs();

    Quaternionf[] getInterpolationQuats();

    default Matrix4f interpolate(Matrix4f m1, Matrix4f m2, float t) {
        Vector3f[] v = getInterpolationVecs();
        Vector3f translation1 = v[0];
        Vector3f translation2 = v[1];

        Vector3f scale1 = v[2];
        Vector3f scale2 = v[3];

        Quaternionf[] quaternionfs = getInterpolationQuats();

        Quaternionf rotation1 = quaternionfs[0];
        Quaternionf rotation2 = quaternionfs[1];

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
        Matrix4f mutableInterpolationMatrix4f = getMutableInterpolationMatrix4f();
        mutableInterpolationMatrix4f.translationRotateScale(lerpTranslation, slerpRotation, lerpScale);

        return mutableInterpolationMatrix4f;
    }

    VisualBufferSource getBufferSource();

    default void addInterpolatedTransformedInstance(int depth, ModelPart modelPart, Material material) {
        List<List<InterpolatedTransformedInstance>> transformedInstances = getTransformedInstances();
        while (transformedInstances.size() <= depth) {
            transformedInstances.add(Collections.EMPTY_LIST);
        }
        if (transformedInstances.get(depth) == Collections.EMPTY_LIST) {
            transformedInstances.set(depth, new ArrayList<>());
        }

        transformedInstances.get(depth).add(new InterpolatedTransformedInstance(getInstanceProvider().instancer(
                        InstanceTypes.TRANSFORMED, VanillaModel.cachedOf(modelPart, material))
                .createInstance(), new Matrix4f(), new Matrix4f()));
    }

    default void updateTransforms(int depth, Matrix4f p) {
        List<List<InterpolatedTransformedInstance>> transformedInstances = getTransformedInstances();

        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);
        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
            ti.lastTick = ((ClientLevel) getLevel()).getGameTime();
        }
    }

    default void addInterpolatedItemTransformedInstance(int depth, ItemStack itemStack, ItemDisplayContext itemDisplayContext) {
        List<List<InterpolatedTransformedInstance>> transformedInstances = getTransformedInstances();

        while (transformedInstances.size() <= depth) {
            transformedInstances.add(Collections.EMPTY_LIST);
        }
        if (transformedInstances.get(depth) == Collections.EMPTY_LIST) {
            transformedInstances.set(depth, new ArrayList<>());
        }

        if (!transformedInstances.get(depth).isEmpty()) {
            return;
        }

        InterpolatedTransformedInstance newInstance = new InterpolatedTransformedInstance(getInstanceProvider().instancer(
                        InstanceTypes.TRANSFORMED, ItemModels.get((Level) getLevel(), itemStack, ItemDisplayContext.NONE))
                .createInstance(), new Matrix4f(), new Matrix4f());
        newInstance.instance.light(getLight());
        transformedInstances.get(depth).add(newInstance);
    }

    default void updateItemTransforms(int depth, Matrix4f p) {
        List<List<InterpolatedTransformedInstance>> transformedInstances = getTransformedInstances();

        //WHY????????
        p.translate(+0.5f, +0.5f, +0.5f);
        //???????????
        List<InterpolatedTransformedInstance> get = transformedInstances.get(depth);

        for (int i = 0; i < get.size(); i++) {
            InterpolatedTransformedInstance ti = get.get(i);
            ti.previous.set(ti.current);
            ti.current.set(p);
            ti.instance.setTransform(ti.current).setChanged();
            ti.lastTick = ((ClientLevel) getLevel()).getGameTime();
        }
    }
}
