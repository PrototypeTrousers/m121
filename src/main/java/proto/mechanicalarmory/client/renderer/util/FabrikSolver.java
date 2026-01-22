package proto.mechanicalarmory.client.renderer.util;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.client.renderer.armor.InstanceTree2;

import java.util.ArrayList;
import java.util.List;

public class FabrikSolver {

    // --- Constants ---
    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);
    private static final float LIMIT_ANGLE = 12f; // Default limit
    private static final float LIMIT_COS = (float) Math.cos(Math.toRadians(LIMIT_ANGLE));
    private static final float LIMIT_RAD = (float) Math.toRadians(LIMIT_ANGLE);
    private final Vector3f localTarget = new Vector3f();

    /**
     * Main entry point for the solver.
     */
    public void solve(InstanceTree2 rootNode, Vector3f inWorldTarget, Matrix4f rootPose, int iterations, float tolerance) {
        // 1. Gather the chain of bone instances
        List<InstanceTree2> chain = gatherChain(rootNode);
        if (chain.size() < 2) return;

        // 2. Prepare Data (World positions and Bone lengths)
        ChainData data = extractWorldState(chain, rootPose);

        localTarget.set(inWorldTarget);
        //rootPose.transformPosition(localTarget, worldTarget);

        // 4. Run the Algorithm
        runIterativeSolve(data, localTarget, rootPose, iterations, tolerance);

        // 5. Apply results back to the visual instances
        applyToInstances(chain, data.joints);
    }

    // --- Helper Step 1: Gather Chain ---
    private List<InstanceTree2> gatherChain(InstanceTree2 root) {
        List<InstanceTree2> chain = new ArrayList<>();
        InstanceTree2 current = root;

        while (current != null) {
            chain.add(current);
            int nextId = chain.size();
            // Naming convention: RightArm.001, RightArm.002, etc.
            String nextName = "RightArm.0" + (nextId < 10 ? "0" + nextId : nextId);
            current = current.child(nextName);
        }
        return chain;
    }

    // --- Helper Step 2: Extract State ---
    private ChainData extractWorldState(List<InstanceTree2> chain, Matrix4f rootPose) {
        Vector3f[] joints = new Vector3f[chain.size()];
        float[] lengths = new float[chain.size() - 1];

        // A. Read ALL joints from the previous frame first
        for (int i = 0; i < chain.size(); i++) {
            joints[i] = new Vector3f();
            chain.get(i).getPoseMatrix().getTranslation(joints[i]);
            rootPose.transformPosition(joints[i]);
        }

        // B. Calculate lengths based on those stable positions
        for (int i = 0; i < chain.size() - 1; i++) {
            lengths[i] = joints[i].distance(joints[i + 1]);
            // Safety: prevents div/0 errors if model is initialized at 0,0,0
            if (lengths[i] < 0.0001f) lengths[i] = 0.5f;
        }
        return new ChainData(joints, lengths);
    }

    // --- Helper Step 3: The Algorithm ---
    private void runIterativeSolve(ChainData data, Vector3f target, Matrix4f rootPose, int iterations, float tolerance) {
        Vector3f[] joints = data.joints;
        float[] lengths = data.lengths;
        Vector3f origin = new Vector3f(joints[0]);

        // Check reachability
        float totalLen = 0;
        for (float l : lengths) totalLen += l;

        if (origin.distance(target) > totalLen) {
            // Unreachable: Straighten towards target
            Vector3f dir = new Vector3f(target).sub(origin).normalize();
            for (int i = 1; i < joints.length; i++) {
                joints[i].set(joints[i - 1]).add(new Vector3f(dir).mul(lengths[i - 1]));
            }
        } else {
            // Reachable: Iterate
            for (int iter = 0; iter < iterations; iter++) {
                if (joints[joints.length - 1].distance(target) < tolerance) break;

                // Backward Pass (Tip -> Root)
                backwardPass(joints, lengths, target);

                // Forward Pass (Root -> Tip) WITH Constraints
                forwardPass(joints, lengths, origin, rootPose);
            }
        }
    }

    private void backwardPass(Vector3f[] joints, float[] lengths, Vector3f target) {
        joints[joints.length - 1].set(target);
        for (int i = joints.length - 2; i >= 0; i--) {
            Vector3f dir = new Vector3f(joints[i]).sub(joints[i + 1]).normalize();
            joints[i].set(joints[i + 1]).add(dir.mul(lengths[i]));
        }
    }

    private void forwardPass(Vector3f[] joints, float[] lengths, Vector3f origin, Matrix4f rootPose) {
        joints[0].set(origin);

        for (int i = 1; i < joints.length; i++) {
            Vector3f dir = new Vector3f(joints[i]).sub(joints[i - 1]).normalize();
            Vector3f referenceDir;

            if (i == 1) {
                // Shoulder Constraint: Relative to Body Down (-Y)
                referenceDir = new Vector3f(0, 1, 0);
                rootPose.transformDirection(referenceDir);
            } else {
                // Joint Constraint: Relative to previous bone
                referenceDir = new Vector3f(joints[i - 1]).sub(joints[i - 2]).normalize();
            }
            dir = constrainCone(dir, referenceDir);

            // Apply Length
            joints[i].set(joints[i - 1]).add(dir.mul(lengths[i - 1]));
        }
    }

    // --- Helper Step 4: Constraints ---
    private Vector3f constrainCone(Vector3f currentDir, Vector3f referenceDir) {
        // Fast Check
        if (currentDir.dot(referenceDir) >= LIMIT_COS) {
            return currentDir;
        }

        // Correction
        Vector3f axis = new Vector3f(referenceDir).cross(currentDir);
        if (axis.lengthSquared() < 0.00001f) {
            // Handle 180 degree flip edge case
            if (Math.abs(referenceDir.x) > 0.9f) axis.set(0, 0, 1);
            else axis.set(1, 0, 0);
        } else {
            axis.normalize();
        }

        Quaternionf correction = new Quaternionf().setAngleAxis(LIMIT_RAD, axis.x, axis.y, axis.z);
        return new Vector3f(referenceDir).rotate(correction).normalize();
    }

    // --- Helper Step 5: Update Instances ---
    private void applyToInstances(List<InstanceTree2> chain, Vector3f[] joints) {
        for (int i = 0; i < chain.size() - 1; i++) {
            Vector3f start = joints[i];
            Vector3f end = joints[i + 1];

            Vector3f dir = new Vector3f(end).sub(start).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(BONE_AXIS, dir);

            Matrix4f newMatrix = new Matrix4f()
                    .translate(start)
                    .rotate(rotation);

            // Assuming you have a bridge method or access to the TransformedInstance
            // If this is inside the same package, you might need a getter on InstanceTree2
            updateInstanceMatrix(chain.get(i), newMatrix);
        }
    }

    private void updateInstanceMatrix(InstanceTree2 treeNode, Matrix4f mat) {
        // Your existing update logic
        for (int child = 0; child < treeNode.childCount(); child++) {
            var instance = treeNode.child(child).instance();
            if (instance != null) {
                instance.setTransform(mat).setChanged();
            }
        }
    }

    // Simple Data Holder
    private record ChainData(Vector3f[] joints, float[] lengths) {
    }
}