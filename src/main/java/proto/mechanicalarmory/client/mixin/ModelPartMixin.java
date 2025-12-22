package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.model.Material;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

import java.util.ArrayList;
import java.util.LinkedList;


@Mixin(targets = "net.minecraft.client.model.geom.ModelPart")
public abstract class ModelPartMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER), cancellable = true)
    private void injected(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual pv) {
            VanillaBlockEntityVisual v = pv.getVisual();
            if (!pv.isRendered()) {
                v.posedParts.compute(pv.getDepth(), (integer, value) -> {
                    if (value == null) {
                        value = new ArrayList<>();
                    }
                    Material mat = pv.getVisual().getBufferSource().getMaterialMap().get(buffer);
                    value.add(new VanillaBlockEntityVisual.M(pv.getDepth(), (ModelPart) (Object) this, mat));
                    return value;
                });
            }
            v.depthPoseMap.compute(pv.getDepth(), (integer, value) -> {
                if (value == null) {
                    value = new LinkedList<>();
                }
                value.add(poseStack.last());
                return value;
            });
        }
    }

    @WrapWithCondition(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    )
    private boolean onlyRenderIfAllowed(ModelPart instance, PoseStack.Pose pose, VertexConsumer vertexConsumer, int buffer, int packedLight, int packedOverlay, PoseStack poseStack) {
        return !(poseStack instanceof PoseStackVisual);
    }
}
