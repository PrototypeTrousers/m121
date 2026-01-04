package proto.mechanicalarmory.client.mixin;

import com.google.common.collect.HashBiMap;
import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(MultiBufferSource.BufferSource.class)
public class MultiBufferSourceMixin {

    @Shadow
    protected final Map<RenderType, BufferBuilder> startedBuilders = HashBiMap.create();

}
