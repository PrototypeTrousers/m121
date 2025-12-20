package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Mixin(targets = "net.minecraft.client.model.geom.ModelPart")
public abstract class ModelPartMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("TAIL"))
    public void b(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual pv) {
            VanillaBlockEntityVisual v = pv.getVisual();
            Map<RenderType, BufferBuilder> sb = ((BufferSourceAccessor) v.rb.bufferSource()).getStartedBuilders();
            List<RenderType> toRemove = new ArrayList<>(sb.keySet());
            for (RenderType k : toRemove) {
                //sb.remove(k);
            }
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void injected(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual pv) {
            VanillaBlockEntityVisual v = pv.getVisual();
            v.modelPartPoseMap.put((ModelPart) (Object) this, poseStack.last());
            v.makeMaterialForPart((ModelPart) (Object) this, buffer);
        }
    }
}
