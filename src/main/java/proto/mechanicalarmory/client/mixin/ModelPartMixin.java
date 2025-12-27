package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VisualBufferSource;

import java.util.ArrayList;
import java.util.Collections;


@Mixin(targets = "net.minecraft.client.model.geom.ModelPart")
public abstract class ModelPartMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;translateAndRotate(Lcom/mojang/blaze3d/vertex/PoseStack;)V", shift = At.Shift.AFTER), cancellable = true)
    private void injected(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (poseStack instanceof PoseStackVisual pv) {
            SinkBufferSourceVisual v = pv.getVisual();
            if (((ModelPart) (Object) this).isEmpty()) return;
            if (!pv.isRendered()) {
                Material mat;
                if (buffer instanceof SpriteCoordinateExpander sceb) {
                    TextureAtlasSprite tas = ((SpriteCoordinateExpanderAccessor) sceb).getSprite();
                    mat = new Material(tas.atlasLocation(), tas.contents().name());
                } else {
                    mat = pv.getVisual().getBufferSource().getMaterialMap().get(buffer);
                }

                if (mat == null) {
                    CompositeRenderTypeAccessor c = ((CompositeRenderTypeAccessor) ((VisualBufferSource.DummyBuffer) buffer).getRenderType());
                    RenderStateShard.EmptyTextureStateShard r = ((RenderTypeAccessor) (Object) c.getState()).getTextureState();
                    ResourceLocation atlas = ((TextureStateShardAccessor) r).getTexture().get();
                    mat = new Material(atlas, atlas);
                }
                v.addInterpolatedTransformedInstance(pv.getDepth(), (ModelPart) (Object) this, mat);

            } else {
                v.updateTransforms(pv.getDepth(), poseStack.last().pose());
            }
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
