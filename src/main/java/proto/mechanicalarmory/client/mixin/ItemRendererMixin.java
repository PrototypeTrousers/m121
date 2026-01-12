package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.generic.FrameExtractionAnimatedVisual;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V", shift = At.Shift.BEFORE), cancellable = true)
    public void ma$renderStatic(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel p_model, CallbackInfo ci) {
        if (poseStack instanceof ExtendedRecyclingPoseStack psv) {
            FrameExtractionAnimatedVisual v = psv.getVisual();
            v.addInterpolatedItemTransformedInstance(psv.getDepth(), itemStack, displayContext);
            v.updateItemTransforms(psv.getDepth(), poseStack.last().pose());
            //ci.cancel();
        }
    }
}
