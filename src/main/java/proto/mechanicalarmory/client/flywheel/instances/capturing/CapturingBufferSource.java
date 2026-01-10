package proto.mechanicalarmory.client.flywheel.instances.capturing;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.*;
    @OnlyIn(Dist.CLIENT)
    public class CapturingBufferSource implements MultiBufferSource {
        protected final ByteBufferBuilder sharedBuffer;
        protected final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
        protected final Map<RenderType, BufferBuilder> startedBuilders = new HashMap<>();
        @Nullable
        protected RenderType lastSharedType;

        public Map<MeshData, RenderType> getDataRenderTypeMap() {
            return dataRenderTypeMap;
        }

        protected Map<MeshData, RenderType> dataRenderTypeMap = new Object2ObjectArrayMap<>();

        public List<MeshData> getMeshDataList() {
            return meshDataList;
        }

        protected List<MeshData> meshDataList = new ArrayList<>();

        public CapturingBufferSource() {
            this.sharedBuffer = new ByteBufferBuilder(1536);
            this.fixedBuffers = new Object2ObjectLinkedOpenHashMap<>();
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            BufferBuilder bufferbuilder = this.startedBuilders.get(renderType);
            if (bufferbuilder != null && !renderType.canConsolidateConsecutiveGeometry()) {
                this.endBatch(renderType, bufferbuilder);
                bufferbuilder = null;
            }

            if (bufferbuilder != null) {
                return bufferbuilder;
            } else {
                ByteBufferBuilder bytebufferbuilder = this.fixedBuffers.get(renderType);
                if (bytebufferbuilder != null) {
                    bufferbuilder = new BufferBuilder(bytebufferbuilder, renderType.mode(), renderType.format());
                } else {
                    if (this.lastSharedType != null) {
                        this.endBatch(this.lastSharedType);
                    }

                    bufferbuilder = new BufferBuilder(this.sharedBuffer, renderType.mode(), renderType.format());
                    this.lastSharedType = renderType;
                }

                this.startedBuilders.put(renderType, bufferbuilder);
                return bufferbuilder;
            }
        }

        public void endLastBatch() {
            if (this.lastSharedType != null) {
                this.endBatch(this.lastSharedType);
                this.lastSharedType = null;
            }
        }

        public void endBatch() {
            this.endLastBatch();

            for (RenderType rendertype : this.fixedBuffers.keySet()) {
                this.endBatch(rendertype);
            }
        }

        public void endBatch(RenderType renderType) {
            BufferBuilder bufferbuilder = this.startedBuilders.remove(renderType);
            if (bufferbuilder != null) {
                this.endBatch(renderType, bufferbuilder);
            }
        }

        private void endBatch(RenderType renderType, BufferBuilder builder) {
            MeshData meshdata = builder.build();
            if (meshdata != null) {
                meshDataList.add(meshdata);
                dataRenderTypeMap.put(meshdata, renderType);
            }

            if (renderType.equals(this.lastSharedType)) {
                this.lastSharedType = null;
            }
        }
    }
