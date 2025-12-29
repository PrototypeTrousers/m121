package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.api.visual.Visual;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(Storage.class)
public interface StorageMixinAccessor {
    @Accessor("visuals")
    Map<Entity, Visual> getVisualsFor();

}
