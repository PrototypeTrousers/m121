package proto.mechanicalarmory.client.mixin;

import brachy.modularui.drawable.schema.BaseSchemaRenderer;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(BaseSchemaRenderer.class)
public class BaseSchemaRendererMixin {
    @ModifyArgs(method = "renderBlocks", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/shaders/Uniform;set(FFF)V"))
    private void modifyArgs(Args args) {
        args.set(0,0f);
        args.set(1,0f);
        args.set(2,0f);
    }

    @ModifyArgs(method = "handleBlockEntity", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void modify2Args(Args args, @Local BlockPos pos) {
        args.set(0,(float)pos.getX());
        args.set(1,(float)pos.getY());
        args.set(2,(float)pos.getZ());
    }
}
