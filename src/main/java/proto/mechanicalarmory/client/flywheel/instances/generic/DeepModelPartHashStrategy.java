package proto.mechanicalarmory.client.flywheel.instances.generic;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.client.model.geom.ModelPart;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DeepModelPartHashStrategy implements Hash.Strategy<ModelPart> {
    public static final DeepModelPartHashStrategy INSTANCE = new DeepModelPartHashStrategy();

    @Override
    public int hashCode(ModelPart part) {
        if (part == null) return 0;

        // 1. Transform Hash
        int result = Objects.hash(
                part.x, part.y, part.z,
                part.xRot, part.yRot, part.zRot,
                part.xScale, part.yScale, part.zScale
        );

        // 2. Cubes Hash (Geometry)
        // We access the private 'cubes' list via the provided class structure
        for (ModelPart.Cube cube : part.cubes) {
            result = 31 * result + hashCube(cube);
        }

        // 3. Children Hash (Recursion)
        for (ModelPart child : part.children.values()) {
            result = 31 * result + hashCode(child);
        }

        return result;
    }

    private int hashCube(ModelPart.Cube cube) {
        int res = Objects.hash(cube.minX, cube.minY, cube.minZ, cube.maxX, cube.maxY, cube.maxZ);
        for (ModelPart.Polygon poly : cube.polygons) {
            res = 31 * res + Objects.hash(poly.normal.x(), poly.normal.y(), poly.normal.z());
            for (net.minecraft.client.model.geom.ModelPart.Vertex v : poly.vertices) {
                res = 31 * res + Objects.hash(v.pos.x(), v.pos.y(), v.pos.z(), v.u, v.v);
            }
        }
        return res;
    }

    @Override
    public boolean equals(ModelPart a, ModelPart b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        // Compare Transformations
        if (Float.compare(a.x, b.x) != 0 || Float.compare(a.y, b.y) != 0 || Float.compare(a.z, b.z) != 0) return false;
        if (Float.compare(a.xRot, b.xRot) != 0 || Float.compare(a.yRot, b.yRot) != 0 || Float.compare(a.zRot, b.zRot) != 0) return false;
        if (Float.compare(a.xScale, b.xScale) != 0 || Float.compare(a.yScale, b.yScale) != 0 || Float.compare(a.zScale, b.zScale) != 0) return false;

        // Compare Cubes
        List<ModelPart.Cube> cubesA = a.cubes;
        List<ModelPart.Cube> cubesB = b.cubes;
        if (cubesA.size() != cubesB.size()) return false;
        for (int i = 0; i < cubesA.size(); i++) {
            if (!compareCubes(cubesA.get(i), cubesB.get(i))) return false;
        }
        // Compare Children
        Map<String, ModelPart> childrenA = a.children;
        Map<String, ModelPart> childrenB = b.children;
        if (childrenA.size() != childrenB.size()) return false;
        for (Map.Entry<String, ModelPart> entry : childrenA.entrySet()) {
            ModelPart childB = childrenB.get(entry.getKey());
            if (!equals(entry.getValue(), childB)) return false;
        }

        return true;
    }

    private boolean compareCubes(ModelPart.Cube a, ModelPart.Cube b) {
        if (Float.compare(a.minX, b.minX) != 0 || Float.compare(a.maxY, b.maxY) != 0) return false; // etc for all bounds

        ModelPart.Polygon[] polyA = a.polygons;
        ModelPart.Polygon[] polyB = b.polygons;
        if (polyA.length != polyB.length) return false;

        for (int i = 0; i < polyA.length; i++) {
            if (!polyA[i].normal.equals(polyB[i].normal)) return false;
            if (polyA[i].vertices.length != polyB[i].vertices.length) return false;
            for (int j = 0; j < polyA[i].vertices.length; j++) {
                ModelPart.Vertex vA = polyA[i].vertices[j];
                ModelPart.Vertex vB = polyB[i].vertices[j];
                if (!vA.pos.equals(vB.pos) || vA.u != vB.u || vA.v != vB.v) return false;
            }
        }
        return true;
    }
}
