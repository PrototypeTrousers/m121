package proto.mechanicalarmory.client.gltf;

import de.javagl.jgltf.impl.v2.MeshPrimitive;
import de.javagl.jgltf.model.*;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4fc;

public class GltfMesh implements Mesh {

    private final int vertexCount;

    public GltfMesh(MeshPrimitiveModel meshPrimitiveModel) {
        AccessorModel posAccessor = meshPrimitiveModel.getAttributes().get("POSITION");
        vertexCount = posAccessor.getCount();
    }
    @Override
    public int vertexCount() {
        return vertexCount;
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
