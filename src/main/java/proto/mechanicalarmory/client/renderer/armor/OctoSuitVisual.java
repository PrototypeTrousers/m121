package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;

import java.util.ArrayList;
import java.util.List;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    TransformedInstance transformedInstance;
    Entity holderEntity;
    Vector3f visualPos;
    ModelTree modelTree = MechanicalArmoryClient.octoArmModelTree;

    private final InstanceTree2 instanceTree;
//    private final @Nullable InstanceTree firstArm;
//    private final @Nullable InstanceTree secondArm;
//    private final @Nullable InstanceTree baseMotor;
//    private final @Nullable InstanceTree itemAttachment;
    Matrix4f pose = new Matrix4f();


    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity , float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;

        instanceTree = InstanceTree2.create(ctx.instancerProvider(), modelTree);
        instanceTree.updateInstancesStatic(pose);

    }

    @Override
    public void update(float partialTick) {

    }

    @Override
    public void delete() {
        transformedInstance.delete();
    }

    @Override
    public void beginFrame(Context ctx) {
        pose.identity();
        pose.translate(0, 1 , 0);

        Vec3 ePos = holderEntity.getPosition(ctx.partialTick());
        //pose.translate((float) ePos.x(), (float) ePos.y(), (float) ePos.z());
                Vec3 behind = Vec3.directionFromRotation(
                0, (float) ( 180 + holderEntity.getPreciseBodyRotation(ctx.partialTick())));
        //pose.translate((float) behind.x(), (float) behind.y(), (float) behind.z());

        //pose.rotateY(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));



        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });

        solveFABRIKDirect(instanceTree.child("RightArm"), new Vector3f(
                4 * (float) Math.sin(System.currentTimeMillis() / 100d) + 3,
                4 * (float) Math.sin(System.currentTimeMillis() / 100d),
                7), 32 , 0.001f);
    }

    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);



    public void solveFABRIKDirect(InstanceTree2 root, Vector3f targetPos, int iterations, float tolerance) {
        // --- 1. Gather Chain (Same as before) ---
        List<InstanceTree2> chain = new ArrayList<>();
        InstanceTree2 current = root;
        while (current != null) {
            chain.add(current);
            int nextId = chain.size();
            String nextName = "RightArm.0" + (nextId < 10 ? "0" + nextId : nextId);
            current = current.child(nextName);
        }
        if (chain.size() < 2) return;

        // --- 2. Extract World Positions (Same as before) ---
        Vector3f[] joints = new Vector3f[chain.size()];
        float[] lengths = new float[chain.size() - 1];

        for (int i = 0; i < chain.size(); i++) {
            joints[i] = new Vector3f();
            // Get the current render-space position
            chain.get(i).getPoseMatrix().getTranslation(joints[i]);
            if (i > 0) lengths[i - 1] = joints[i].distance(joints[i - 1]);
        }

        // --- 3. Run FABRIK Algorithm (Same as before) ---
        Vector3f origin = new Vector3f(joints[0]);
        float totalLen = 0;
        for(float l : lengths) totalLen += l;

        // unreachable target optimization
        if (origin.distance(targetPos) > totalLen) {
            // Just stretch out in a line towards target
            Vector3f dir = new Vector3f(targetPos).sub(origin).normalize();
            for (int i = 1; i < joints.length; i++) {
                joints[i].set(joints[i-1]).add(new Vector3f(dir).mul(lengths[i-1]));
            }
        } else {
            // 3. FABRIK Iteration
            for (int iter = 0; iter < iterations; iter++) {
                if (joints[joints.length - 1].distance(targetPos) < tolerance) break;

                // BACKWARD: Tip -> Root
                joints[joints.length - 1].set(targetPos);
                for (int i = joints.length - 2; i >= 0; i--) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i + 1]).normalize();
                    joints[i].set(joints[i + 1]).add(dir.mul(lengths[i]));
                }

                // FORWARD: Root -> Tip
                joints[0].set(origin);
                for (int i = 1; i < joints.length; i++) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i - 1]).normalize();
                    joints[i].set(joints[i - 1]).add(dir.mul(lengths[i - 1]));
                }
            }
        }

        // --- 4. DIRECT MATRIX UPDATE (The New Part) ---
        // We iterate through the chain and manually construct the matrix for each segment.

        for (int i = 0; i < chain.size() - 1; i++) {
            Vector3f start = joints[i];
            Vector3f end = joints[i+1];

            // A. Calculate Direction
            Vector3f dir = new Vector3f(end).sub(start).normalize();

            // B. Calculate Rotation
            // We want to rotate our default BONE_AXIS (0,1,0) to face the 'dir'.
            // This Quaternion represents the Absolute World Rotation needed.
            Quaternionf rotation = new Quaternionf().rotationTo(BONE_AXIS, dir);

            // C. Construct the Matrix (Translation * Rotation)
            // Note: We do NOT include the parent's matrix. We are working in absolute space now.
            Matrix4f newMatrix = new Matrix4f()
                    .translate(start)
                    .rotate(rotation);

            // D. Force-apply to the Instance
            // You need to access the underlying TransformedInstance here.
            // Assuming InstanceTree2 has a method to get it, or you can add one.
            updateInstanceMatrix(chain.get(i), newMatrix);
        }
    }

    // Helper to bridge InstanceTree2 and Flywheel Instance
    private void updateInstanceMatrix(InstanceTree2 treeNode, Matrix4f mat) {
        for (int child = 0 ; child < treeNode.childCount() ; child++) {
            TransformedInstance instance = treeNode.child(child).instance();
            if (instance != null) {
                instance.setTransform(mat)
                        .setChanged();
            }
        }
    }
}
