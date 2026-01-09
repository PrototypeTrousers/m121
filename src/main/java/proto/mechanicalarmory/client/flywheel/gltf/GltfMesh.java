package proto.mechanicalarmory.client.flywheel.gltf;

import com.kneelawk.krender.model.gltf.impl.BufferAccess;
import com.kneelawk.krender.model.gltf.impl.GltfFile;
import com.kneelawk.krender.model.gltf.impl.format.*;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class GltfMesh implements Mesh {

    private final int vertexCount;
    private final int indexCount;
    private final TriIndexSequence indices;
    private final GltfRoot gltfRoot;
    private final GltfFile gltfFile;
    private GltfPrimitive meshPrimitiveModel;

    public GltfMesh(GltfPrimitive gltfPrimitive, GltfFile gltfFile) {
        GltfRoot gltfRoot = gltfFile.root();
        this.meshPrimitiveModel = gltfPrimitive;
        this.gltfRoot = gltfRoot;
        this.gltfFile = gltfFile;

        int paIdx = meshPrimitiveModel.attributes().get("POSITION");
        GltfAccessor posAccessor = gltfRoot.accessors().get(paIdx);
        vertexCount = (int) posAccessor.count();
        int indicesBufferIdx = gltfPrimitive.indices().getAsInt();
        GltfBufferView bufferView = gltfRoot.bufferViews().get(indicesBufferIdx);
        GltfAccessor indicesAccessor = gltfRoot.accessors().get(indicesBufferIdx);

        BufferAccess is;
        try {
            is = gltfFile.getRawBufferView(indicesBufferIdx);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        indexCount = Math.toIntExact(indicesAccessor.count());
        indices = new TriIndexSequence(toByteBufferNio(is.createStream()), indexCount);
    }

    public ByteBuffer toByteBufferNio(InputStream is) {
        try (ReadableByteChannel channel = Channels.newChannel(is)) {
            // Create a buffer (adjust size based on expected data)
            ByteBuffer buffer = ByteBuffer.allocate(16384);

            while (channel.read(buffer) != -1) {
                if (!buffer.hasRemaining()) {
                    // Resize the buffer if it gets full
                    ByteBuffer newBuffer = ByteBuffer.allocate(buffer.capacity() * 2);
                    buffer.flip();
                    newBuffer.put(buffer);
                    buffer = newBuffer;
                }
            }
            buffer.flip(); // Prepare for reading
            return buffer;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public void write(MutableVertexList vertexList) {
        int paIdx = meshPrimitiveModel.attributes().get("POSITION");
        GltfAccessor posAccessor = gltfRoot.accessors().get(paIdx);
        int posBufferIdx = posAccessor.bufferView().getAsInt();
        GltfBufferView posBufferView = gltfRoot.bufferViews().get(posBufferIdx);

        BufferAccess posBufferAccess;
        try {
            posBufferAccess = gltfFile.getRawBufferView(posBufferView.buffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int nmIdx = meshPrimitiveModel.attributes().get("NORMAL");
        GltfAccessor normalAccessor = gltfRoot.accessors().get(nmIdx);
        int normalBufferIdx = normalAccessor.bufferView().getAsInt();
        GltfBufferView normalBufferView = gltfRoot.bufferViews().get(normalBufferIdx);

        BufferAccess normalBufferAccess;
        try {
            normalBufferAccess = gltfFile.getRawBufferView(normalBufferView.buffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int texIdx = meshPrimitiveModel.attributes().get("TEXCOORD_0");
        GltfAccessor texAccessor = gltfRoot.accessors().get(texIdx);
        int texBufferIdx = texAccessor.bufferView().getAsInt();
        GltfBufferView texBufferView = gltfRoot.bufferViews().get(texBufferIdx);

        BufferAccess textureBufferAccess;
        try {
            textureBufferAccess = gltfFile.getRawBufferView(texBufferView.buffer());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        float[] colorFactor = new float[]{1f, 1f, 1f, 0};

        for (int i = 0; i < vertexCount; i++) {
            // Each vertex has 3 floats (X, Y, Z).
            // The offset for the start of vertex 'i' is (i * 3 components * 4 bytes).
            int posBase = i * 12;
            vertexList.x(i, posBufferAccess.getFloat(posBase));
            vertexList.y(i, posBufferAccess.getFloat(posBase + 4));
            vertexList.z(i, posBufferAccess.getFloat(posBase + 8));

            // Normals also usually have 3 float components (12 bytes total per vertex)
            int normBase = i * 12;
            vertexList.normalX(i, normalBufferAccess.getFloat(normBase));
            vertexList.normalY(i, normalBufferAccess.getFloat(normBase + 4));
            vertexList.normalZ(i, normalBufferAccess.getFloat(normBase + 8));

            // UVs usually have 2 float components (8 bytes total per vertex)
            int uvBase = i * 8;
            vertexList.u(i, textureBufferAccess.getFloat(uvBase));
            vertexList.v(i, textureBufferAccess.getFloat(uvBase + 4));

            // Color factors (assuming these are constant or handled elsewhere)
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
