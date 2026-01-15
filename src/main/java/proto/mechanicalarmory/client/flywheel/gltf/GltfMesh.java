package proto.mechanicalarmory.client.flywheel.gltf;

import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.api.vertex.VertexList;
import dev.engine_room.flywheel.lib.math.DataPacker;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class GltfMesh implements Mesh {

    private final int vertexCount;
    private final int indexCount;
    private final TriIndexSequence indices;
    Vector4fc boundingSphere;
    VertexList vertexList;


    public GltfMesh(MeshPrimitiveModel meshPrimitiveModel) {
        // 1. Get Accessors
        AccessorModel posAccessor = meshPrimitiveModel.getAttributes().get("POSITION");
        AccessorModel normalAccessor = meshPrimitiveModel.getAttributes().get("NORMAL");
        AccessorModel texAccessor = meshPrimitiveModel.getAttributes().get("TEXCOORD_0");

        vertexCount = posAccessor.getCount();
        indexCount = meshPrimitiveModel.getIndices().getCount();
        indices = new TriIndexSequence(meshPrimitiveModel.getIndices().getAccessorData().createByteBuffer(), indexCount);

        // 2. Setup Destination Memory
        FullVertexView vertexView = new FullVertexView();
        long stride = vertexView.stride(); // 36L
        MemoryBlock dst = MemoryBlock.mallocTracked((long) vertexCount * stride);
        long dstPtr = dst.ptr();

        ByteBuffer posBuf = posAccessor.getAccessorData().createByteBuffer();
        ByteBuffer normBuf = normalAccessor.getAccessorData().createByteBuffer();
        ByteBuffer texBuf = texAccessor.getAccessorData().createByteBuffer();

        long posSrcPtr = MemoryUtil.memAddress(posBuf);
        long normSrcPtr = MemoryUtil.memAddress(normBuf);
        long texSrcPtr = MemoryUtil.memAddress(texBuf);

        // Default Color
        float[] color = ((MaterialModelV2)meshPrimitiveModel.getMaterialModel()).getBaseColorFactor();

        byte r = DataPacker.packNormU8(color[0]);
        byte g = DataPacker.packNormU8(color[1]);
        byte b = DataPacker.packNormU8(color[2]);
        byte a = DataPacker.packNormU8(color[3]);

        // 4. Manual Interleaving Loop
        for (int i = 0; i < vertexCount; i++) {
            long vPtr = dstPtr + (i * stride);

            // POSITION: Copy 3 floats (12 bytes)
            MemoryUtil.memCopy(posSrcPtr + (i * 12L), vPtr, 12L);

            // COLOR: Put packed bytes (offsets 12, 13, 14, 15)
            MemoryUtil.memPutByte(vPtr + 12L, r);
            MemoryUtil.memPutByte(vPtr + 13L, g);
            MemoryUtil.memPutByte(vPtr + 14L, b);
            MemoryUtil.memPutByte(vPtr + 15L, a);

            // TEXCOORD: Copy 2 floats (8 bytes) to offset 16
            if (texSrcPtr != 0) {
                MemoryUtil.memCopy(texSrcPtr + (i * 8L), vPtr + 16L, 8L);
            }

            // OVERLAY & LIGHT: Initialize to 0 (offsets 24, 28)
            MemoryUtil.memPutInt(vPtr + 24L, 0);
            MemoryUtil.memPutInt(vPtr + 28L, 0);

            // NORMALS: Convert Float -> Packed Byte (offsets 32, 33, 34)
            if (normSrcPtr != 0) {
                float nx = MemoryUtil.memGetFloat(normSrcPtr + (i * 12L));
                float ny = MemoryUtil.memGetFloat(normSrcPtr + (i * 12L) + 4L);
                float nz = MemoryUtil.memGetFloat(normSrcPtr + (i * 12L) + 8L);

                MemoryUtil.memPutByte(vPtr + 32L, DataPacker.packNormI8(nx));
                MemoryUtil.memPutByte(vPtr + 33L, DataPacker.packNormI8(ny));
                MemoryUtil.memPutByte(vPtr + 34L, DataPacker.packNormI8(nz));
            }
        }

        // 5. Finalize View
        vertexView.ptr(dstPtr);
        vertexView.vertexCount(vertexCount);
        vertexView.nativeMemoryOwner(dst);
        this.vertexList = vertexView;
        this.boundingSphere = ModelUtil.computeBoundingSphere(vertexList);
    }
    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public void write(MutableVertexList vertexList) {
        this.vertexList.writeAll(vertexList);
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
        return boundingSphere;
    }
}
