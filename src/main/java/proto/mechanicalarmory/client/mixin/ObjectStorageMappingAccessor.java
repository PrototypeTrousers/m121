package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.backend.engine.indirect.ObjectStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// ObjectStorage.Mapping is an inner class — target it with the $ separator.
@Mixin(value = ObjectStorage.Mapping.class, remap = false)
public interface ObjectStorageMappingAccessor {
    @Accessor("pages")
    int[] mechanicalArmory$getPages();
}
