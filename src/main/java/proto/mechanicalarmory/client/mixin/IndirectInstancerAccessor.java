package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.backend.engine.indirect.IndirectInstancer;
import dev.engine_room.flywheel.backend.engine.indirect.ObjectStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IndirectInstancer.class, remap = false)
public interface IndirectInstancerAccessor {
    @Accessor("mapping")
    ObjectStorage.Mapping mechanicalArmory$getMapping();
}
