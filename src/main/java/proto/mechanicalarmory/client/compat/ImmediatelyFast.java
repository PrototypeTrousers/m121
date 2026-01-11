package proto.mechanicalarmory.client.compat;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import proto.mechanicalarmory.client.mixin.BatchableBufferSourceAccessor;

import java.util.Map;

public class ImmediatelyFast {
    public static RenderType getRenderType(MultiBufferSource multiBufferSource, VertexConsumer vertexConsumer) {
        if (multiBufferSource instanceof BatchableBufferSourceAccessor bbs) {
            for (Map.Entry<RenderType, ReferenceSet<BufferBuilder>> entry : bbs.getPendingBuffers().entrySet()) {
                RenderType key = entry.getKey();
                ReferenceSet<BufferBuilder> value = entry.getValue();
                if (value.contains(vertexConsumer)) {
                    return key;
                }
            }
        }
        return null;
    }
}
