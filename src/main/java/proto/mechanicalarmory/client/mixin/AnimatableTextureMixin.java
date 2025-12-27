package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

@Mixin(AnimatableTexture.class)
public class AnimatableTextureMixin {

    @WrapMethod(method = "setAndUpdate(Lnet/minecraft/resources/ResourceLocation;)V")
    private static void recordRender(ResourceLocation texturePath, Operation<Void> original) {
        RenderSystem.recordRenderCall(() -> original.call(texturePath));
    }
}
