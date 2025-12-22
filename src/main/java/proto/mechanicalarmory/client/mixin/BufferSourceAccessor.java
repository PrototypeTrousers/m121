package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.SequencedMap;

@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    @Accessor("startedBuilders")
    Map<RenderType, BufferBuilder> getStartedBuilders();

    @Accessor("fixedBuffers")
    SequencedMap<RenderType, ByteBufferBuilder> getFixedBuffers();
}
