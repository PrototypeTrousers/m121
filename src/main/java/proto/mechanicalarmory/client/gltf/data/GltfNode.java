package proto.mechanicalarmory.client.gltf.data;

import java.util.ArrayList;
import java.util.List;

public class GltfNode {
    public String name;
    public long meshIndex = -1; // Index of the mesh this node points to
    public List<Long> children = new ArrayList<Long>();

    // Transformation data
    public float[] matrix = null;
    public float[] translation = {0, 0, 0};
    public float[] rotation = {0, 0, 0, 1}; // Quaternion
    public float[] scale = {1, 1, 1};

    public GltfNode(String name) {
        this.name = name;
    }
}
