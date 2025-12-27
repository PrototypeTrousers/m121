package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "renderModelLists", at = @At("HEAD"), cancellable = true)
    public void ma$renderStatic(BakedModel model, ItemStack stack, int combinedLight, int combinedOverlay, PoseStack poseStack, VertexConsumer buffer, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual psv) {
            SinkBufferSourceVisual v = psv.getVisual();
            v.addInterpolatedItemTransformedInstance(psv.getDepth(), stack);
            v.updateItemTransforms(psv.getDepth(), poseStack.last().pose());
            ci.cancel();
        }
    }
}
