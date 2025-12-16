package proto.mechanicalarmory.client.gltf;

import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.nio.FloatBuffer;

public class GltfMesh implements Mesh {

    private final int vertexCount;
    private final int indexCount;
    private final TriIndexSequence indices;
    private MeshPrimitiveModel meshPrimitiveModel;

    public GltfMesh(MeshPrimitiveModel meshPrimitiveModel) {
        this.meshPrimitiveModel = meshPrimitiveModel;
        AccessorModel posAccessor = meshPrimitiveModel.getAttributes().get("POSITION");
        vertexCount = posAccessor.getCount();
        indexCount = meshPrimitiveModel.getIndices().getCount();
        indices = new TriIndexSequence(meshPrimitiveModel.getIndices().getAccessorData().createByteBuffer(), indexCount);
    }
    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public void write(MutableVertexList vertexList) {
        AccessorModel posAccessor = meshPrimitiveModel.getAttributes().get("POSITION");
        AccessorModel normalAccessor = meshPrimitiveModel.getAttributes().get("NORMAL");
        AccessorModel texAccessor = meshPrimitiveModel.getAttributes().get("TEXCOORD_0");

        FloatBuffer positionBuffer =
                posAccessor.getAccessorData().createByteBuffer().asFloatBuffer();
        FloatBuffer normalBuffer =
                normalAccessor.getAccessorData().createByteBuffer().asFloatBuffer();
        FloatBuffer texBuffer =
                texAccessor.getAccessorData().createByteBuffer().asFloatBuffer();
        //float[] colorFactor = material.pbrMetallicRoughness.baseColorFactor;
        float[] colorFactor = new float[]{1f, 1f, 1f, 0};

        for (int i = 0; i < vertexCount; i++) {
            vertexList.x(i, positionBuffer.get(i * 3));
            vertexList.y(i, positionBuffer.get(i * 3 + 1));
            vertexList.z(i, positionBuffer.get(i * 3 + 2));
            vertexList.normalX(i, normalBuffer.get(i * 3));
            vertexList.normalY(i, normalBuffer.get(i * 3 + 1));
            vertexList.normalZ(i, normalBuffer.get(i * 3 + 2));
            vertexList.u(i, texBuffer.get(i * 2));
            vertexList.v(i, texBuffer.get(i * 2 + 1));
            vertexList.r(i, colorFactor[0]);
            vertexList.g(i, colorFactor[1]);
            vertexList.b(i, colorFactor[2]);
            vertexList.a(i, colorFactor[3]);
        }
    }

    @Override
    public IndexSequence indexSequence() {
        return indices;
    }

    @Override
    public int indexCount() {
        return indexCount;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1,1,1,1);
    }
}
