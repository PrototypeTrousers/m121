package proto.mechanicalarmory.client.mixin;

import com.google.common.collect.HashBiMap;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.compat.Constants;
import proto.mechanicalarmory.client.compat.ImmediatelyFast;
import proto.mechanicalarmory.client.compat.IrisCompat;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaModel;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.WrappingPoseStack;


@Mixin(targets = "net.minecraft.client.model.geom.ModelPart", priority = 1001)
public abstract class ModelPartMixin {

    @Shadow
    public boolean skipDraw;

    @Shadow
    public boolean visible;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER))
    private void injected(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof WrappingPoseStack pv) {
            ExtendedRecyclingPoseStack eps = pv.getWrappedPoseStack();
            SinkBufferSourceVisual v = pv.getVisual();
            if (((ModelPart) (Object) this).isEmpty()) return;
            if (!pv.isRendered()) {
                TextureAtlasSprite tas = null;
                Material m;
                RenderType r = null;

                if (buffer instanceof SpriteCoordinateExpanderAccessor sceb) {
                    tas = sceb.getSprite();
                    buffer = sceb.getDelegate();
                }

                MultiBufferSource mbs = v.getBufferSource();

                if (Constants.isIrisLoaded) {
                    mbs = IrisCompat.getMultiBufferSourceFromIris(mbs);
                }

                if (mbs instanceof MultiBufferAccessor accessor) {
                    HashBiMap<RenderType, BufferBuilder> map = (HashBiMap<RenderType, BufferBuilder>) accessor.getStartedBuilders();
                    r = map.inverse().get(buffer);

                    if (Constants.isImmediatelyfastLoaded) {
                        r = ImmediatelyFast.getRenderType(mbs, buffer);
                    }
                    if (Constants.isIrisLoaded) {
                        r = IrisCompat.getRenderType(mbs, buffer);
                    }
                }

                if (r == null) {
                    return;
                }

                m = VanillaModel.makeFlywheelMaterial(r);
                SinkBufferSourceVisual.InstanceMaterialKey key = new SinkBufferSourceVisual.InstanceMaterialKey(m, tas);

                if (!visible || skipDraw) {
                    eps.last().pose().zero();
                }
                v.updateTransforms(eps.getDepth(), eps.last());
                v.addInterpolatedTransformedInstance(eps.getDepth(), (ModelPart) (Object) this, key);
            } else {
                if (!visible || skipDraw) {
                    eps.last().pose().zero();
                }
                v.updateTransforms(eps.getDepth(), eps.last());
            }
        }
    }

    @WrapOperation(method = "translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/api/math/MatrixHelper;rotateZYX(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;FFF)V", opcode = Opcodes.INVOKESTATIC), require = 0)
    void sodiumCompat(PoseStack.Pose matrices, float angleZ, float angleY, float angleX, Operation<Void> original, PoseStack matrixStack){
        if (matrixStack instanceof WrappingPoseStack pv) {
            ExtendedRecyclingPoseStack eps = pv.getWrappedPoseStack();
            original.call(eps.last(), angleZ, angleY, angleX);
        }
        original.call(matrices, angleZ, angleY, angleX);
    }

    @WrapOperation(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
    at = @At(value = "FIELD", target = "Lnet/minecraft/client/model/geom/ModelPart;visible:Z", opcode = Opcodes.GETFIELD))
    private boolean forceRender(ModelPart instance, Operation<Boolean> original, PoseStack poseStack){
        return (poseStack instanceof WrappingPoseStack) || original.call(instance);
    }

    @WrapWithCondition(
            method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V")
    )
    private boolean onlyRenderIfAllowed(ModelPart instance, PoseStack.Pose pose, VertexConsumer vertexConsumer, int buffer, int packedLight, int packedOverlay, PoseStack poseStack) {
        if (poseStack instanceof WrappingPoseStack wps) {
            if (wps.getVisual().getBufferSource() instanceof CapturingBufferSource) {
                return true;
            }
        }
        return !(poseStack instanceof WrappingPoseStack);
    }
}
