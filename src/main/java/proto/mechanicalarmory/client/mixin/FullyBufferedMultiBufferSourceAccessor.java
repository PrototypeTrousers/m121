package proto.mechanicalarmory.client.mixin;

import net.irisshaders.batchedentityrendering.impl.FullyBufferedMultiBufferSource;
import net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FullyBufferedMultiBufferSource.class)
public interface FullyBufferedMultiBufferSourceAccessor {
    @Accessor("builders")
    SegmentedBufferBuilder[] getBuilders();
}
