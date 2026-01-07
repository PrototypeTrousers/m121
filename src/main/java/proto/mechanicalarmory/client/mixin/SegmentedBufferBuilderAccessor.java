package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(SegmentedBufferBuilder.class)
public interface SegmentedBufferBuilderAccessor {
    @Accessor("builders")
    Map<RenderType, BufferBuilder> getBuilders();
}
