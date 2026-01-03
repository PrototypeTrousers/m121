package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VisualBufferSource;

import java.util.function.Function;

@Mixin(Material.class)
public abstract class MaterialMixin {
    @Inject(method = "buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("RETURN"))
    void a(MultiBufferSource buffer, Function<ResourceLocation, RenderType> renderTypeGetter, CallbackInfoReturnable<VertexConsumer> cir){
        if (buffer instanceof VisualBufferSource vbs) {
            if (!vbs.getVisual().getPoseStackVisual().isRendered()) {
                vbs.getMaterialMap().put(cir.getReturnValue(), (Material) (Object) this);
            }
        }
    }


    @Inject(method = "buffer(Lnet/minecraft/client/renderer/MultiBufferSource;Ljava/util/function/Function;Z)Lcom/mojang/blaze3d/vertex/VertexConsumer;", at = @At("HEAD"))
    void b(MultiBufferSource buffer, Function<ResourceLocation, RenderType> renderTypeGetter, boolean withGlint, CallbackInfoReturnable<VertexConsumer> cir){

    }
}
