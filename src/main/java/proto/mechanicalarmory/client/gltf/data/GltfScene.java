package proto.mechanicalarmory.client.gltf.data;

import java.util.ArrayList;
import java.util.List;

public class GltfScene {
    private final String name;
    private final List<Long> nodeIndices = new ArrayList<Long>();

    public GltfScene(String name) {
        this.name = name;
    }

    public void addNode(long index) {
        nodeIndices.add(index);
    }

    @Override
    public String toString() {
        return "GltfScene{name='" + name + "', nodes=" + nodeIndices + "}";
    }
}
