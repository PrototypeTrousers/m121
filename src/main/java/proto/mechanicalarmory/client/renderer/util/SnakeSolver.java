package proto.mechanicalarmory.client.renderer.util;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.client.renderer.armor.InstanceTree2;

import java.util.ArrayList;
import java.util.List;

public class SnakeSolver {

    // --- Constants ---
    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);

    // --- Helper Step 1: Gather Chain ---
    private List<InstanceTree2> gatherChain(InstanceTree2 root) {
        List<InstanceTree2> chain = new ArrayList<>();

        for (int i =0; i < root.childCount(); i++) {
            if (root.child(i).instance() == null) {
                addChildren(root.child(i), chain);
            }
        }
        return chain;
    }

    private void addChildren(InstanceTree2 root, List<InstanceTree2> children) {
        if (root.instance() == null) {
            children.add(root);
        } else {
            return;
        }
        for (int i =0; i < root.childCount(); i++) {
            addChildren(root.child(i), children);
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

    /**
     * Solves the arm along a complex path of waypoints.
     * @param pathWaypoints A list of points (from A*) that the arm must thread through.
     */
    public void solveMultiSegment(InstanceTree2 rootNode, List<Vector3f> pathWaypoints, Matrix4f rootPose) {
        List<InstanceTree2> chain = gatherChain(rootNode);
        if (chain.isEmpty() || pathWaypoints.size() < 2) {
            return;
        }

        int bonesCount = chain.size();

        // We need to distribute the bones evenly along the ENTIRE spline length.

        // 1. Calculate the total length of the spline (approximation)
        // This helps us map "Bone Index" to "Spline T"
        // For simplicity, we assume the spline segments are roughly equal length.

        for (int i = 0; i < bonesCount; i++) {
            // Normalized position of this bone in the chain (0.0 to 1.0)
            float tGlobal = (float) i / (float) (bonesCount - 1);

            // 2. Sample the Catmull-Rom Spline at tGlobal
            Vector3f pos = sampleSpline(pathWaypoints, tGlobal);
            Vector3f nextPos = sampleSpline(pathWaypoints, tGlobal + 0.01f);

            // 3. Orient the bone
            Vector3f forward = new Vector3f(nextPos).sub(pos).normalize();
            Quaternionf rotation = new Quaternionf().rotationTo(BONE_AXIS, forward);

            Matrix4f newMatrix = new Matrix4f().translate(pos).rotate(rotation);
            updateInstanceMatrix(chain.get(i), newMatrix);
        }
    }

    /**
     * Samples a position from a list of points using Catmull-Rom interpolation.
     * @param points The full path.
     * @param tGlobal 0.0 (Start of path) to 1.0 (End of path).
     */
    private Vector3f sampleSpline(List<Vector3f> points, float tGlobal) {
        int numSegments = points.size() - 1;
        float tReal = tGlobal * numSegments;
        int index = (int) tReal;
        float tLocal = tReal - index;

        // Clamp index
        if (index >= numSegments) {
            index = numSegments - 1;
            tLocal = 1.0f;
        }

        // Get the 4 points for Catmull-Rom
        // If we are at the ends, clamp to the nearest point
        Vector3f p0 = (index > 0) ? points.get(index - 1) : points.get(0);
        Vector3f p1 = points.get(index);
        Vector3f p2 = points.get(index + 1);
        Vector3f p3 = (index + 2 < points.size()) ? points.get(index + 2) : points.get(index + 1);

        return catmullRom(p0, p1, p2, p3, tLocal);
    }

    // Use the catmullRom method discussed in previous turn
    private Vector3f catmullRom(Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float t) {
// 1. Calculate the polynomial coefficients (A, B, C, D)
        // We compute these manually for x, y, z to avoid creating temporary Vector3f objects.

        // Coefficient A (Cubic term): 0.5 * (-p0 + 3*p1 - 3*p2 + p3)
        float ax = 0.5f * (-p0.x + 3f * p1.x - 3f * p2.x + p3.x);
        float ay = 0.5f * (-p0.y + 3f * p1.y - 3f * p2.y + p3.y);
        float az = 0.5f * (-p0.z + 3f * p1.z - 3f * p2.z + p3.z);

        // Coefficient B (Quadratic term): 0.5 * (2*p0 - 5*p1 + 4*p2 - p3)
        float bx = 0.5f * (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x);
        float by = 0.5f * (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y);
        float bz = 0.5f * (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z);

        // Coefficient C (Linear term): 0.5 * (p2 - p0)
        float cx = 0.5f * (p2.x - p0.x);
        float cy = 0.5f * (p2.y - p0.y);
        float cz = 0.5f * (p2.z - p0.z);

        // Coefficient D (Constant term): p1
        float dx = p1.x;
        float dy = p1.y;
        float dz = p1.z;

        // 2. Evaluate using Horner's Method with FMA
        // Formula: D + t * (C + t * (B + t * A))
        // FMA Form: fma(t, fma(t, fma(t, A, B), C), D)

        float x = Math.fma(t, Math.fma(t, Math.fma(t, ax, bx), cx), dx);
        float y = Math.fma(t, Math.fma(t, Math.fma(t, ay, by), cy), dy);
        float z = Math.fma(t, Math.fma(t, Math.fma(t, az, bz), cz), dz);

        return new Vector3f(x, y, z);
    }
}