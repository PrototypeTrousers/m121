package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.Material;
import proto.mechanicalarmory.client.mixin.BufferSourceAccessor;

public class VisualBufferSource extends MultiBufferSource.BufferSource {
    VanillaBlockEntityVisual visual;
    Object2ObjectArrayMap<VertexConsumer, Material> bufferMaterialMap = new Object2ObjectArrayMap<>();
    Object2ObjectArrayMap<RenderType,VertexConsumer> DUMMY_BUFFER_MAP = new Object2ObjectArrayMap<>();
    boolean rendered;

    public VisualBufferSource(VanillaBlockEntityVisual visual) {
        super(null, ((BufferSourceAccessor) Minecraft.getInstance().renderBuffers().bufferSource()).getFixedBuffers());
        this.visual = visual;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return DUMMY_BUFFER_MAP.computeIfAbsent(renderType, buffer -> new DummyBuffer(renderType));
    }

    public static class DummyBuffer implements VertexConsumer {
        RenderType renderType;
        DummyBuffer(RenderType renderType) {
            this.renderType = renderType;
        }

        public RenderType getRenderType() {
            return renderType;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }
    }

    public VanillaBlockEntityVisual getVisual() {
        return visual;
    }

    public void setRendered(boolean rendered) {
        this.rendered = rendered;
    }

    public boolean isRendered() {
        return rendered;
    }

    public Object2ObjectArrayMap<VertexConsumer, Material> getMaterialMap() {
        return bufferMaterialMap;
    }
}
