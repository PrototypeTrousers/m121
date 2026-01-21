package proto.mechanicalarmory.client.renderer.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.transform.Affine;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.minecraft.client.model.geom.PartPose;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.ObjIntConsumer;

public final class InstanceTree2 {
    private final ModelTree source;
    @Nullable
    private final TransformedInstance instance;
    private final InstanceTree2[] children;

    private final Matrix4f poseMatrix;

    private float x, y, z;
    private final Quaternionf rotation = new Quaternionf();
    private float xScale = 1.0f, yScale = 1.0f, zScale = 1.0f;

    private boolean visible = true;
    private boolean skipDraw = false;
    private boolean changed;

    private InstanceTree2(ModelTree source, @Nullable TransformedInstance instance, InstanceTree2[] children) {
        this.source = source;
        this.instance = instance;
        this.children = children;
        this.poseMatrix = (instance != null) ? instance.pose : new Matrix4f();
        resetPose();
    }

    public static InstanceTree2 create(InstancerProvider provider, ModelTree meshTree) {
        InstanceTree2[] children = new InstanceTree2[meshTree.childCount()];
        for (int i = 0; i < meshTree.childCount(); i++) {
            children[i] = create(provider, meshTree.child(i));
        }

        Model model = meshTree.model();
        TransformedInstance instance = (model != null)
                ? provider.instancer(InstanceTypes.TRANSFORMED, model).createInstance()
                : null;

        return new InstanceTree2(meshTree, instance, children);
    }

    // --- Update Methods ---

    public void updateInstances(Matrix4fc initialPose) {
        propagateAnimation(initialPose, true);
    }

    public void updateInstancesStatic(Matrix4fc initialPose) {
        propagateAnimation(initialPose, false);
    }

    // --- Propagation Logic ---

    public void propagateAnimation(Matrix4fc initialPose, boolean force) {
        if (!visible) return;

        if (changed || force) {
            poseMatrix.set(initialPose);
            applyLocalTransform(poseMatrix);
            force = true;

            if (instance != null && !skipDraw) {
                instance.setChanged();
            }
        }

        for (InstanceTree2 child : children) {
            child.propagateAnimation(poseMatrix, force);
        }
        changed = false;
    }

    private void applyLocalTransform(Matrix4f matrix) {
        matrix.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        matrix.rotate(rotation);
        if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
            matrix.scale(xScale, yScale, zScale);
        }
    }

    // --- Transform Logic ---

    public void translateAndRotate(Affine<?> affine) {
        affine.translate(x / 16.0F, y / 16.0F, z / 16.0F);
        affine.rotate(rotation);
        if (xScale != 1.0F || yScale != 1.0F || zScale != 1.0F) {
            affine.scale(xScale, yScale, zScale);
        }
    }

    public void translateAndRotate(PoseStack poseStack) {
        translateAndRotate(TransformStack.of(poseStack));
    }

    // --- Getters & Setters ---

    public float xPos() { return x; }
    public void xPos(float x) { this.x = x; setChanged(); }
    public float yPos() { return y; }
    public void yPos(float y) { this.y = y; setChanged(); }
    public float zPos() { return z; }
    public void zPos(float z) { this.z = z; setChanged(); }

    public void pos(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z; setChanged();
    }

    public Quaternionfc rotation() { return rotation; }
    public void rotation(Quaternionfc rotation) { this.rotation.set(rotation); setChanged(); }

    /** Sets rotation using Euler angles (Minecraft order: ZYX) */
    public void rotation(float xRot, float yRot, float zRot) {
        this.rotation.rotationZYX(zRot, yRot, xRot);
        setChanged();
    }

    public float xScale() { return xScale; }
    public void xScale(float xScale) { this.xScale = xScale; setChanged(); }
    public float yScale() { return yScale; }
    public void yScale(float yScale) { this.yScale = yScale; setChanged(); }
    public float zScale() { return zScale; }
    public void zScale(float zScale) { this.zScale = zScale; setChanged(); }

    public void scale(float x, float y, float z) {
        this.xScale = x; this.yScale = y; this.zScale = z; setChanged();
    }

    public void xRot(float xRot) {
        this.rotation.rotateX(xRot);
        setChanged();
    }

    public void yRot(float yRot) {
        this.rotation.rotateY(yRot);
        setChanged();
    }

    public void zRot(float zRot) {
        this.rotation.rotateZ(zRot);
        setChanged();
    }

    // --- Pose Management ---

    public void loadPose(PartPose pose) {
        this.x = pose.x;
        this.y = pose.y;
        this.z = pose.z;
        this.rotation.rotationZYX(pose.zRot, pose.yRot, pose.xRot);
        this.xScale = 1.0F;
        this.yScale = 1.0F;
        this.zScale = 1.0F;
        setChanged();
    }

    public void resetPose() {
        loadPose(source.initialPose());
    }

    public void copyTransform(InstanceTree2 tree) {
        this.x = tree.x;
        this.y = tree.y;
        this.z = tree.z;
        this.rotation.set(tree.rotation);
        this.xScale = tree.xScale;
        this.yScale = tree.yScale;
        this.zScale = tree.zScale;
        setChanged();
    }

    // --- Utility Methods ---

    public void visible(boolean visible) {
        this.visible = visible;
        updateVisible();
        for (InstanceTree2 child : children) child.visible(visible);
    }

    public void skipDraw(boolean skipDraw) {
        this.skipDraw = skipDraw;
        updateVisible();
    }

    private void updateVisible() {
        if (instance != null) instance.setVisible(visible && !skipDraw);
    }

    @Nullable
    public TransformedInstance instance() { return instance; }

    private void setChanged() { this.changed = true; }

    public void delete() {
        if (instance != null) instance.delete();
        for (InstanceTree2 child : children) child.delete();
    }

    // --- Traversal Boilerplate ---

    public void traverse(Consumer<? super TransformedInstance> consumer) {
        if (instance != null) consumer.accept(instance);
        for (InstanceTree2 child : children) child.traverse(consumer);
    }

    @ApiStatus.Experimental
    public void traverse(int i, ObjIntConsumer<? super TransformedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i);
        for (InstanceTree2 child : children) child.traverse(i, consumer);
    }

    @ApiStatus.Experimental
    public interface ObjIntIntConsumer<T> { void accept(T t, int i, int j); }

    @ApiStatus.Experimental
    public void traverse(int i, int j, ObjIntIntConsumer<? super TransformedInstance> consumer) {
        if (instance != null) consumer.accept(instance, i, j);
        for (InstanceTree2 child : children) child.traverse(i, j, consumer);
    }

    public int childCount() { return children.length; }
    public InstanceTree2 child(int index) { return children[index]; }
    public String childName(int index) { return source.childName(index); }
    public int childIndex(String name) { return source.childIndex(name); }

    @Nullable
    public InstanceTree2 child(String name) {
        int index = childIndex(name);
        return index < 0 ? null : child(index);
    }

    public InstanceTree2 childOrThrow(String name) {
        InstanceTree2 child = child(name);
        if (child == null) throw new NoSuchElementException("Can't find part " + name);
        return child;
    }
}