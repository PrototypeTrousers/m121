package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.minecraft.client.renderer.RenderType;
import net.raphimc.immediatelyfast.feature.core.BatchableBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(BatchableBufferSource.class)
public interface BatchableBufferSourceAccessor {
    @Accessor("pendingBuffers")
    Map<RenderType, ReferenceSet<BufferBuilder>> getPendingBuffers();
}
