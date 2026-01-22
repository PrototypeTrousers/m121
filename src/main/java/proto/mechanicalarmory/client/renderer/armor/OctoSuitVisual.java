package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.model.geom.PartPose;
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
        pose.translate((float) ePos.x(), (float) ePos.y(), (float) ePos.z());
                Vec3 behind = Vec3.directionFromRotation(
                0, (float) ( 180 + holderEntity.getPreciseBodyRotation(ctx.partialTick())));
        pose.translate((float) behind.x(), (float) behind.y(), (float) behind.z());

        pose.rotateY(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));

        instanceTree.child("Cube").child("Cube").instance().setTransform(pose);


        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });

// Define where the Shoulder is physically located on the body model (Local Space)
        // You must tweak this vector to match your specific model's shoulder coordinates!
        PartPose init = instanceTree.child("RightArm").initialPose();
        Vector3f shoulderOffset = new Vector3f(init.x / 16f, init.y / 16f, init.z / 16f);

        Vector3f localTarget = new Vector3f(
                0,
                0,
                6
        );

        // Pass the Root Matrix (pose.last().pose())
        solveFABRIKDirect(
                instanceTree.child("RightArm"),
                localTarget,
                shoulderOffset, // New Parameter
                pose, // New Parameter: The Root Pose
                10,
                0.001f
        );
    }

    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);

    public void solveFABRIKDirect(InstanceTree2 root, Vector3f localTargetPos, Vector3f localShoulderPos, Matrix4f rootPose, int iterations, float tolerance) {
        // --- 1. Gather Chain ---
        List<InstanceTree2> chain = new ArrayList<>();
        InstanceTree2 current = root;
        while (current != null) {
            chain.add(current);
            int nextId = chain.size();
            String nextName = "RightArm.0" + (nextId < 10 ? "0" + nextId : nextId);
            current = current.child(nextName);
        }
        if (chain.size() < 2) return;

        // --- 2. Extract World Positions ---
        Vector3f[] joints = new Vector3f[chain.size()];
        float[] lengths = new float[chain.size() - 1];

        for (int i = 0; i < chain.size(); i++) {
            joints[i] = new Vector3f();
            chain.get(i).getPoseMatrix().getTranslation(joints[i]);
            rootPose.transformPosition(joints[i]);

            // Calculate lengths based on the initial setup (or cache these if the model scales)
            if (i > 0) lengths[i - 1] = joints[i].distance(joints[i - 1]);
        }

        Vector3f worldTarget = new Vector3f(localTargetPos);
        rootPose.transformPosition(worldTarget);

        // --- 3. Run FABRIK Algorithm ---
        Vector3f origin = new Vector3f(joints[0]);
        float totalLen = 0;
        for (float l : lengths) totalLen += l;

        if (origin.distance(worldTarget) > totalLen) {
            Vector3f dir = new Vector3f(worldTarget).sub(origin).normalize();
            for (int i = 1; i < joints.length; i++) {
                joints[i].set(joints[i - 1]).add(new Vector3f(dir).mul(lengths[i - 1]));
            }
        } else {
            for (int iter = 0; iter < iterations; iter++) {
                if (joints[joints.length - 1].distance(worldTarget) < tolerance) break;

                // BACKWARD: Tip -> Root
                joints[joints.length - 1].set(worldTarget);
                for (int i = joints.length - 2; i >= 0; i--) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i + 1]).normalize();
                    joints[i].set(joints[i + 1]).add(dir.mul(lengths[i]));
                }

                // FORWARD: Root -> Tip
                joints[0].set(origin); // Snaps back to the new correct shoulder pos
                for (int i = 1; i < joints.length; i++) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i - 1]).normalize();
                    Vector3f referenceDir;

                    if (i == 1) {
                        // Shoulder Constraint
                        referenceDir = new Vector3f(0, -1, 0);
                        rootPose.transformDirection(referenceDir);

                        // Use optimized check
                        dir = constrainCone(dir, referenceDir, LIMIT_15_COS, LIMIT_15_RAD);
                    } else {
                        // Elbow Constraint
                        referenceDir = new Vector3f(joints[i-1]).sub(joints[i-2]).normalize();

                        // Use optimized check
                        dir = constrainCone(dir, referenceDir, LIMIT_15_COS, LIMIT_15_RAD);
                    }

                    // Apply
                    joints[i].set(joints[i - 1]).add(dir.mul(lengths[i - 1]));
                }
            }
        }

        // --- 4. DIRECT MATRIX UPDATE ---
        for (int i = 0; i < chain.size() - 1; i++) {
            Vector3f start = joints[i];
            Vector3f end = joints[i + 1];

            Vector3f dir = new Vector3f(end).sub(start).normalize();

            // This rotation is global, which is what we want since 'start' is global
            Quaternionf rotation = new Quaternionf().rotationTo(BONE_AXIS, dir);

            Matrix4f newMatrix = new Matrix4f()
                    .translate(start)
                    .rotate(rotation);

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

    // Pre-calculate this once in your class (e.g., for 90 degrees)
    private static final float LIMIT_15_COS = (float) Math.cos(Math.toRadians(15));
    private static final float LIMIT_15_RAD = (float) Math.toRadians(15);

    private Vector3f constrainCone(Vector3f currentDir, Vector3f referenceDir, float minCos, float maxRad) {
        // 1. Fast Check: Dot Product
        // We assume both vectors are already normalized (FABRIK does this anyway)
        float currentDot = currentDir.dot(referenceDir);

        // If dot is larger than the limit cosine, we are inside the cone.
        // (Remember: Cosine 0deg = 1.0, Cosine 90deg = 0.0)
        if (currentDot >= minCos) {
            return currentDir;
        }

        // 2. Correction (Only runs if violated)
        // We need to clamp the vector to the edge of the cone.

        // Find the axis perpendicular to both (the "hinge" of the error)
        Vector3f axis = new Vector3f(referenceDir).cross(currentDir);

        if (axis.lengthSquared() < 0.00001f) {
            // Edge case: vectors are exactly opposite (180 deg apart)
            // Rotate around arbitrary axis (X or Z)
            if (Math.abs(referenceDir.x) > 0.9f) axis.set(0, 0, 1);
            else axis.set(1, 0, 0);
        } else {
            axis.normalize();
        }

        // Rotate the REFERENCE vector by the MAX angle to get the new limit boundary
        // We don't need to know the current angle, we just force it to the max allowed.
        Quaternionf correction = new Quaternionf().setAngleAxis(maxRad, axis.x, axis.y, axis.z);

        return new Vector3f(referenceDir).rotate(correction).normalize();
    }
}
