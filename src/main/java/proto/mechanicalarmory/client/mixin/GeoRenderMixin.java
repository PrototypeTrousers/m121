package proto.mechanicalarmory.client.mixin;

import com.google.common.collect.HashBiMap;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.compat.Constants;
import proto.mechanicalarmory.client.compat.ImmediatelyFast;
import proto.mechanicalarmory.client.compat.IrisCompat;
import proto.mechanicalarmory.client.flywheel.instances.generic.FrameExtractionAnimatedVisual;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaModel;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.WrappingPoseStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(GeoRenderer.class)
public interface GeoRenderMixin {

    @Inject(method = "renderCubesOfBone", at = @At(value = "HEAD"))
    default void addGeckoModel(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour, CallbackInfo ci){
        if (poseStack instanceof WrappingPoseStack pv) {
            ExtendedRecyclingPoseStack eps = pv.getWrappedPoseStack();
            FrameExtractionAnimatedVisual v = pv.getVisual();
            if (bone.getCubes().isEmpty()) return;
            if (!pv.isRendered()) {
                TextureAtlasSprite tas = null;
                Material m;
                RenderType r = null;

                MultiBufferSource mbs = v.getBufferSource();

                if (buffer instanceof SpriteCoordinateExpanderAccessor sceb) {
                    tas = sceb.getSprite();
                    buffer = sceb.getDelegate();
                }
                
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
                FrameExtractionAnimatedVisual.InstanceMaterialKey key = new FrameExtractionAnimatedVisual.InstanceMaterialKey(m, tas);

                v.updateTransforms(eps.getDepth(), eps.last());
                v.addInterpolatedTransformedInstance(eps.getDepth(), bone, key);
            } else {
                v.updateTransforms(eps.getDepth(), eps.last());
            }

        }
    }

    @Inject(method = "renderCubesOfBone", at = @At("HEAD"), cancellable = true)
    default void renderCubes(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        if (poseStack instanceof WrappingPoseStack pv) {
            ci.cancel();
        }
    }
}
