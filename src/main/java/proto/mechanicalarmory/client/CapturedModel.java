package proto.mechanicalarmory.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.QuadIndexSequence;
import dev.engine_room.flywheel.lib.model.SimpleModel;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector4fc;
import proto.mechanicalarmory.client.gltf.GltfMesh;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class CapturedModel extends SimpleModel {
    public CapturedModel(List<ConfiguredMesh> meshes) {
        super(meshes);
        for (Map.Entry<RenderType, BufferBuilder> entry : mp.entrySet()) {
            meshes.add(new ConfiguredMesh(SimpleMaterial.builder()
                    .texture(rl)
                    .cardinalLightingMode(CardinalLightingMode.ENTITY)
                    .ambientOcclusion(false)
                    .build(),
                    new CapturedMesh(entry)));

        }
    }

    static class CapturedMesh implements Mesh {
        MeshData meshData;

        CapturedMesh(Map.Entry<RenderType, BufferBuilder> entry) {
            MeshData meshData = entry.getValue().build();
        }

        Map.Entry<RenderType, BufferBuilder> entry;

        @Override
        public int vertexCount() {
            return meshData.drawState().vertexCount();
        }

        @Override
        public void write(MutableVertexList vertexList) {

            VertexFormat format = meshData.drawState().format();
            ByteBuffer buffer = meshData.vertexBuffer();

            // Calculate how many vertices are in this mesh
            int vertexCount = meshData.drawState().vertexCount();
            int vertexSizeInBytes = format.getVertexSize();

            // BakedQuads MUST have 4 vertices
            for (int vIdx = 0; vIdx < vertexCount; vIdx += 4) {
                for (int i = 0; i < 4; i++) {
                    int currentVertexOffset = (vIdx + i) * vertexSizeInBytes;

                    vertexList.x(vIdx, buffer.getFloat(currentVertexOffset));
                    vertexList.y(vIdx, buffer.getFloat(currentVertexOffset + 4));
                    vertexList.z(vIdx, buffer.getFloat(currentVertexOffset + 8));

                    int packedColor = buffer.getInt(currentVertexOffset + 12);

                    int r = (packedColor) & 0xFF;
                    int g = (packedColor >> 8) & 0xFF;
                    int b = (packedColor >> 16) & 0xFF;
                    int a = (packedColor >> 24) & 0xFF;

                    vertexList.r(vIdx, r);
                    vertexList.g(vIdx, g);
                    vertexList.b(vIdx, b);
                    vertexList.a(vIdx, a);

                    vertexList.u(vIdx, Float.floatToRawIntBits(buffer.getFloat(currentVertexOffset + 16)));
                    vertexList.v(vIdx, Float.floatToRawIntBits(buffer.getFloat(currentVertexOffset + 20)));

                    int packedNormal = buffer.getInt(currentVertexOffset + 28);

                    float x = ((byte) (packedNormal & 0xFF)) / 127.0f;
                    float y = ((byte) ((packedNormal >> 8) & 0xFF)) / 127.0f;
                    float z = ((byte) ((packedNormal >> 16) & 0xFF)) / 127.0f;

                    vertexList.normalX(vIdx, x);
                    vertexList.normalY(vIdx, y);
                    vertexList.normalZ(vIdx, z);

                    //quadData[destPos + 6] = buffer.getInt(currentVertexOffset + 24); // UV2 (Light)
                }

            }
        }

        @Override
        public IndexSequence indexSequence() {
            return QuadIndexSequence.INSTANCE;
        }

        @Override
        public int indexCount() {
            return meshData.drawState().indexCount();
        }

        @Override
        public Vector4fc boundingSphere() {
            return null;
        }
    }
}
