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

    private boolean visible = true;
    private boolean skipDraw = false;
    private boolean changed;

    private InterpolatingInstanceTree(ModelTree source, @Nullable InterpolatedInstance instance, InterpolatingInstanceTree[] children) {
        this.source = source;
        this.instance = instance;
        this.children = children;

        resetPose();
    }

    public static InterpolatingInstanceTree create(InstancerProvider provider, ModelTree meshTree) {
        InterpolatingInstanceTree[] children = new InterpolatingInstanceTree[meshTree.childCount()];
        for (int i = 0; i < meshTree.childCount(); i++) {
            children[i] = create(provider, meshTree.child(i));
        }

        Model model = meshTree.model();
        InterpolatedInstance instance;
        if (model != null) {
            // Instantiates your custom InterpolatingInstancetype layout instead of standard Transformed
            instance = provider.instancer(InterpolatingInstancetype.INTERPOLATED, model)
                    .createInstance();
        } else {
            instance = null;
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
     * Shifts old goal data into 'From' variables and updates 'Goal' references.
     * This should be executed once every 50ms game tick.
     */
    public void pushKeyframes(Vector3fc nextPos, Quaternionfc nextRot, Vector3fc nextScale) {
        // 1. Shift current states back to the old keyframe slot
        this.posFrom.set(this.posGoal);
        this.rotFrom.set(this.rotGoal);
        this.scaleFrom.set(this.scaleGoal);

        // 2. Assign the fresh simulation ticks to the target goals
        this.posGoal.set(nextPos);
        this.rotGoal.set(nextRot);
        this.scaleGoal.set(nextScale);

        setChanged();

        // If your custom Instance class has explicit vectors, map them directly here:
        if (instance != null) {
            instance.posFrom.set(this.posFrom);
            instance.rotFrom.set(this.rotFrom);
            instance.scaleFrom.set(this.scaleFrom);

            instance.posGoal.set(this.posGoal);
            instance.rotGoal.set(this.rotGoal);
            instance.scaleGoal.set(this.scaleGoal);
            instance.setChanged();
        }
    }

    /**
     * Propagates changes down the hierarchy tree structure.
     * Matrix math calculations are omitted completely; the GPU processes transformations.
     */
    public void propagateAnimation(boolean forceUpdate) {
        if (!visible) {
            return;
        }

        if (changed || forceUpdate) {
            if (instance != null && !skipDraw) {
                instance.setChanged();
            }
            forceUpdate = true;
            changed = false;
        }

        for (InterpolatingInstanceTree child : children) {
            child.propagateAnimation(forceUpdate);
        }
    }

    public void resetPose() {
        PartPose initial = source.initialPose();

        // Zero out From
        this.posFrom.set(initial.x, initial.y, initial.z);
        this.rotFrom.rotationXYZ(initial.xRot, initial.yRot, initial.zRot);
        this.scaleFrom.set(1.0f, 1.0f, 1.0f);

        // Zero out Goal
        this.posGoal.set(this.posFrom);
        this.rotGoal.set(this.rotFrom);
        this.scaleGoal.set(this.scaleFrom);

        setChanged();
    }

    /**
     * Walks down the hierarchy, calculating the absolute world matrix for every part,
     * and applies the final absolute coordinates directly to the GPU instance.
     */
    public void cascadeWorldTransforms(Matrix4f parentWorldMatrix) {
        // 1. Shift the old goals to 'From' for interpolation
        this.posFrom.set(this.posGoal);
        this.rotFrom.set(this.rotGoal);
        this.scaleFrom.set(this.scaleGoal);

        // 2. Build THIS part's local transformation matrix
        Matrix4f localMatrix = new Matrix4f()
                .translate(this.posGoal)
                .rotate(this.rotGoal)
                .scale(this.scaleGoal);

        // 3. Multiply by parent to get the Absolute World Matrix
        Matrix4f absoluteWorldMatrix = new Matrix4f(parentWorldMatrix).mul(localMatrix);

        // 4. Extract the absolute vectors to push to the GPU
        Vector3f absolutePos = new Vector3f();
        Quaternionf absoluteRot = new Quaternionf();
        Vector3f absoluteScale = new Vector3f();

        absoluteWorldMatrix.getTranslation(absolutePos);
        absoluteWorldMatrix.getUnnormalizedRotation(absoluteRot);
        absoluteWorldMatrix.getScale(absoluteScale);

        // 5. Upload absolute coordinates to the GPU instance memory
        if (this.instance != null) {
            // Shift old state
            this.instance.posFrom.set(this.instance.posGoal);
            this.instance.rotFrom.set(this.instance.rotGoal);
            this.instance.scaleFrom.set(this.instance.scaleGoal);

            // Apply new absolute world state
            this.instance.posGoal.set(absolutePos);
            this.instance.rotGoal.set(absoluteRot);
            this.instance.scaleGoal.set(absoluteScale);
            this.instance.setChanged();
        }

        // 6. Recursively cascade down to children
        for (InterpolatingInstanceTree child : children) {
            child.cascadeWorldTransforms(absoluteWorldMatrix);
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