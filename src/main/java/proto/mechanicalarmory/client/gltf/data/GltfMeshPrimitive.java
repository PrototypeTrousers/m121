package proto.mechanicalarmory.client.gltf.data;

import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4fc;

public class GltfMeshPrimitive implements Mesh {

    public GltfMeshPrimitive(){

    }

    @Override
    public int vertexCount() {
        return 0;
    }

    @Override
    public void write(MutableVertexList vertexList) {

    }

    @Override
    public IndexSequence indexSequence() {
        return null;
    }

    @Override
    public int indexCount() {
        return 0;
    }

    @Override
    public Vector4fc boundingSphere() {
        return null;
    }
}
