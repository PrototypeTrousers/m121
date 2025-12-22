package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpriteCoordinateExpander.class)
public interface SpriteCoordinateExpanderAccessor {
    @Accessor("sprite")
    TextureAtlasSprite getSprite();

    @Accessor("delegate")
    VertexConsumer getDelegate();
}
