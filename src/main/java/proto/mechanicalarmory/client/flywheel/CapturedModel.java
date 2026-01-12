package proto.mechanicalarmory.client.flywheel;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.material.WriteMask;
import dev.engine_room.flywheel.api.model.IndexSequence;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.api.vertex.VertexList;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.ModelUtil;
import dev.engine_room.flywheel.lib.model.QuadIndexSequence;
import dev.engine_room.flywheel.lib.vertex.FullVertexView;
import dev.engine_room.flywheel.lib.vertex.NoOverlayVertexView;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryUtil;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.client.mixin.CompositeRenderTypeAccessor;
import proto.mechanicalarmory.client.mixin.RenderTypeAccessor;
import proto.mechanicalarmory.client.mixin.TextureStateShardAccessor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CapturedModel implements Model {
    List<ConfiguredMesh> meshes = new ArrayList<>();
    Vector4f boundingSphere;

    public CapturedModel(CapturingBufferSource bufferSource) {
        for (MeshData meshData : bufferSource.getMeshDataList()) {
            RenderType rt = bufferSource.getDataRenderTypeMap().get(meshData);

            CompositeRenderTypeAccessor c = ((CompositeRenderTypeAccessor) rt);
            RenderStateShard.EmptyTextureStateShard r = ((RenderTypeAccessor) (Object) c.getState()).getTextureState();
            ResourceLocation atlas = ((TextureStateShardAccessor) r).getTexture().get();

            CapturedMesh mesh = new CapturedMesh(meshData);

            meshes.add(new Model.ConfiguredMesh(SimpleMaterial.builder()
                    .texture(atlas)
                    .cutout(CutoutShaders.EPSILON)
                    .light(LightShaders.FLAT)
                    .cardinalLightingMode(CardinalLightingMode.OFF)
                    .ambientOcclusion(false)
                    .writeMask(WriteMask.COLOR_DEPTH)
                    .build(),
                    mesh));
        }

        this.boundingSphere = ModelUtil.computeBoundingSphere(meshes);
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return this.boundingSphere;
    }

    static class CapturedMesh implements Mesh {
        int vertexCount;
        int indexCount;
        Vector4fc boundingSphere;
        VertexList vertexList;

        CapturedMesh(MeshData meshData) {
            //copied from MeshHelper
            MeshData.DrawState drawState = meshData.drawState();
            int vertexCount = drawState.vertexCount();
            long srcStride = drawState.format().getVertexSize();

            VertexView vertexView = new FullVertexView();
            long dstStride = vertexView.stride();

            ByteBuffer src = meshData.vertexBuffer();
            MemoryBlock dst = MemoryBlock.mallocTracked((long) vertexCount * dstStride);
            long srcPtr = MemoryUtil.memAddress(src);
            long dstPtr = dst.ptr();
            long bytesToCopy = Math.min(dstStride, srcStride);

            for (int i = 0; i < vertexCount; i++) {
                // It is safe to copy bytes directly since the NoOverlayVertexView uses the same memory layout as the first
                // 31 bytes of the block vertex format, vanilla or otherwise.
                MemoryUtil.memCopy(srcPtr + srcStride * i, dstPtr + dstStride * i, bytesToCopy);
            }

            vertexView.ptr(dstPtr);
            vertexView.vertexCount(vertexCount);
            vertexView.nativeMemoryOwner(dst);

            this.vertexList = vertexView;
            this.boundingSphere = ModelUtil.computeBoundingSphere(vertexList);
            this.vertexCount = meshData.drawState().vertexCount();
            this.indexCount = meshData.drawState().indexCount();
        }

        Map.Entry<RenderType, BufferBuilder> entry;

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
            return QuadIndexSequence.INSTANCE;
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
}
