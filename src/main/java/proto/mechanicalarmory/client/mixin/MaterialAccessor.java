package proto.mechanicalarmory.client.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Material.class)
public interface MaterialAccessor {
    @Accessor("renderType")
    RenderType getRenderType();
}
