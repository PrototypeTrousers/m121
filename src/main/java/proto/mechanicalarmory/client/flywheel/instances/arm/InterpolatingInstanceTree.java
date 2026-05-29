package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public class InterpolatingInstanceTree {
    private final ModelTree source;
    @Nullable
    private final InterpolatedInstance instance; // Changed from TransformedInstance to your custom type
    private final InterpolatingInstanceTree[] children;

    // Previous Keyframe (Tick N)
    public final Vector3f posFrom = new Vector3f();
    public final Quaternionf rotFrom = new Quaternionf();
    public final Vector3f scaleFrom = new Vector3f(1.0f, 1.0f, 1.0f);

    // Current Keyframe (Tick N+1 / Goal)
    public final Vector3f posGoal = new Vector3f();
    public final Quaternionf rotGoal = new Quaternionf();
    public final Vector3f scaleGoal = new Vector3f(1.0f, 1.0f, 1.0f);

    int nodes;

    private boolean visible = true;
    private boolean skipDraw = false;
    private boolean changed;

    private InterpolatingInstanceTree(ModelTree source, @Nullable InterpolatedInstance instance, InterpolatingInstanceTree[] children) {
        this.source = source;
        this.instance = instance;
        this.children = children;
    }

    public static InterpolatingInstanceTree create(InstancerProvider provider, ModelTree meshTree) {
        int[] counter = {0};
        return create(provider, meshTree, 0, counter);
    }

    private static InterpolatingInstanceTree create(InstancerProvider provider, ModelTree meshTree, int parentIdx, int[] counter) {
        Model model = meshTree.model();
        InterpolatedInstance instance;

        int currentIdx = counter[0]++;

        if (model != null) {
            instance = provider.instancer(InterpolatingInstancetype.INTERPOLATED, model)
                    .createInstance();
            instance.parentIdx = parentIdx;
            instance.partIdx = currentIdx;
            instance.model = model;
        } else {
            instance = null;
        }

        int myIdx = currentIdx;
        InterpolatingInstanceTree[] children = new InterpolatingInstanceTree[meshTree.childCount()];
        for (int i = 0; i < meshTree.childCount(); i++) {
            children[i] = create(provider, meshTree.child(i), myIdx, counter);
        }
        return new InterpolatingInstanceTree(meshTree, instance, children);
    }

    @Nullable
    public InterpolatedInstance instance() {
        return instance;
    }

    public void setChanged() {
        this.changed = true;
        if (instance != null) {
            instance.setChanged();
        }
    }

    /**
     * Walks down the hierarchy, calculating the absolute world matrix for every part,
     * and applies the final absolute coordinates directly to the GPU instance.
     */
    public void cascadeWorldTransforms() {

        // 5. Upload absolute coordinates to the GPU instance memory
        if (this.instance != null) {
            // Shift old state

//            this.instance.posFrom.set(this.instance.posGoal);
//            this.instance.rotFrom.set(this.instance.rotGoal);
//            this.instance.scaleFrom.set(this.instance.scaleGoal);

            this.instance.setChanged();
        }

        // 6. Recursively cascade down to children
        for (InterpolatingInstanceTree child : children) {
            child.cascadeWorldTransforms();
        }
    }

    public PartPose initialPose() { return source.initialPose(); }
    public int childCount() { return children.length; }
    public InterpolatingInstanceTree child(int index) { return children[index]; }
    public String childName(int index) { return source.childName(index); }
    public int childIndex(String name) { return source.childIndex(name); }
    public boolean hasChild(String name) { return childIndex(name) >= 0; }

    @Nullable
    public InterpolatingInstanceTree child(String name) {
        int index = childIndex(name);
        return index < 0 ? null : child(index);
    }

    public InterpolatingInstanceTree childOrThrow(String name) {
        InterpolatingInstanceTree child = child(name);
        if (child == null) throw new NoSuchElementException("Can't find part " + name);
        return child;
    }

    public void visible(boolean visible) {
        this.visible = visible;
        updateVisible();
        for (InterpolatingInstanceTree child : children) {
            child.visible(visible);
        }
    }

    public void skipDraw(boolean skipDraw) {
        this.skipDraw = skipDraw;
        updateVisible();
    }

    private void updateVisible() {
        if (instance != null) {
            instance.setVisible(visible && !skipDraw);
        }
    }

    public boolean visible() { return visible; }
    public boolean skipDraw() { return skipDraw; }

    public void delete() {
        if (instance != null) instance.delete();
        for (InterpolatingInstanceTree child : children) child.delete();
    }

    public void traverse(Consumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance);
        for (InterpolatingInstanceTree child : children) child.traverse(consumer);
    }

    @ApiStatus.Experimental
    public void traverse(int i, ObjIntConsumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i);
        for (InterpolatingInstanceTree child : children) child.traverse(i, consumer);
    }

    @ApiStatus.Experimental
    public void traverse(int i, int j, ObjIntIntConsumer<? super InterpolatedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i, j);
        for (InterpolatingInstanceTree child : children) child.traverse(i, j, consumer);
    }

    @ApiStatus.Experimental
    @FunctionalInterface
    public interface ObjIntIntConsumer<T> {
        void accept(T t, int i, int j);
    }
}