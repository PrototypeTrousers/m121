package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaModel;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VisualBufferSource;


@Mixin(targets = "net.minecraft.client.model.geom.ModelPart")
public abstract class ModelPartMixin {

    @Shadow
    public boolean skipDraw;

    @Shadow
    public boolean visible;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER), cancellable = true)
    private void injected(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual pv) {
            SinkBufferSourceVisual v = pv.getVisual();
            if (((ModelPart) (Object) this).isEmpty()) return;
            if (!pv.isRendered()) {
                TextureAtlasSprite tas = null;
                Material m;
                if (buffer instanceof SpriteCoordinateExpanderAccessor sceb) {
                    tas = sceb.getSprite();
                    m = VanillaModel.makeFlywheelMaterial(((VisualBufferSource.DummyBuffer) sceb.getDelegate()).getRenderType());
                } else  {
                    m = VanillaModel.makeFlywheelMaterial(((VisualBufferSource.DummyBuffer) buffer).getRenderType());
                }
                SinkBufferSourceVisual.InstanceMaterialKey key = new SinkBufferSourceVisual.InstanceMaterialKey(m, tas);

                if (!visible || skipDraw) {
                    poseStack.last().pose().zero();
                }
                v.updateTransforms(pv.getDepth(), poseStack.last());
                v.addInterpolatedTransformedInstance(pv.getDepth(), (ModelPart) (Object) this, key);
            } else {
                if (!visible || skipDraw) {
                    poseStack.last().pose().zero();
                }
                v.updateTransforms(pv.getDepth(), poseStack.last());
            }
        }
    }

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
    at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/geom/ModelPart;visible:Z", opcode = Opcodes.GETFIELD))
    private boolean forceRender(ModelPart instance, Operation<Boolean> original, PoseStack poseStack){
        return (poseStack instanceof PoseStackVisual) || original.call(instance);
    }

    @WrapWithCondition(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    )
    private boolean onlyRenderIfAllowed(ModelPart instance, PoseStack.Pose pose, VertexConsumer vertexConsumer, int buffer, int packedLight, int packedOverlay, PoseStack poseStack) {
        return !(poseStack instanceof PoseStackVisual);
    }
}
