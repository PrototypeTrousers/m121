package proto.mechanicalarmory.client.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.irisshaders.iris.layer.BufferSourceWrapper;
import net.irisshaders.iris.layer.OuterWrappedRenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import proto.mechanicalarmory.client.mixin.FullyBufferedMultiBufferSourceAccessor;
import proto.mechanicalarmory.client.mixin.SegmentedBufferBuilderAccessor;

import java.util.Map;

public class IrisCompat {
    public static MultiBufferSource getMultiBufferSourceFromIris(MultiBufferSource multiBufferSource) {
        if (multiBufferSource instanceof BufferSourceWrapper bsw) {
            if (bsw.getOriginal() instanceof MultiBufferSource.BufferSource) {
                return bsw.getOriginal();
            }
        }
        return multiBufferSource;
    }

    public static RenderType getRenderType(MultiBufferSource multiBufferSource, VertexConsumer vertexConsumer) {
        if (multiBufferSource instanceof FullyBufferedMultiBufferSourceAccessor bbs) {
            SegmentedBufferBuilder[] builders = bbs.getBuilders();
            for (SegmentedBufferBuilder builder : builders) {
                for (Map.Entry<RenderType, BufferBuilder> entry : ((SegmentedBufferBuilderAccessor) builder).getBuilders().entrySet()) {
                    RenderType key = entry.getKey();
                    BufferBuilder value = entry.getValue();
                    if (value == vertexConsumer) {
                        return key;
                    }
                }
            }
        }
        return null;
    }

    public static RenderType unwrap(RenderType renderType) {
        if (renderType instanceof OuterWrappedRenderType outerWrappedRenderType) {
            renderType = outerWrappedRenderType.unwrap();
        }
        return renderType;
    }
}
