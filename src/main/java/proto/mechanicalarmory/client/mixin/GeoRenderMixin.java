package proto.mechanicalarmory.client.mixin;

import com.google.common.collect.HashBiMap;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.checkerframework.checker.units.qual.A;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaModel;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.WrappingPoseStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(GeoRenderer.class)
public interface GeoRenderMixin {
    @Inject(method = "renderCubesOfBone", at = @At(value = "HEAD"))
    default void addGeckoModel(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour, CallbackInfo ci){
        if (poseStack instanceof WrappingPoseStack pv) {
            ExtendedRecyclingPoseStack eps = pv.getWrappedPoseStack();
            SinkBufferSourceVisual v = pv.getVisual();
            if (bone.getCubes().isEmpty()) return;
            if (!pv.isRendered()) {
                TextureAtlasSprite tas = null;
                Material m;

                MultiBufferAccessor accessor = (MultiBufferAccessor) Minecraft.getInstance().renderBuffers().bufferSource();;
                HashBiMap<RenderType, BufferBuilder> map = (HashBiMap<RenderType, BufferBuilder>) accessor.getStartedBuilders();

                RenderType r;
                if (buffer instanceof SpriteCoordinateExpanderAccessor sceb) {
                    tas = sceb.getSprite();
                    r = map.inverse().get(sceb.getDelegate());
                } else  {
                    r = map.inverse().get(buffer);
                }

                m = VanillaModel.makeFlywheelMaterial(r);
                SinkBufferSourceVisual.InstanceMaterialKey key = new SinkBufferSourceVisual.InstanceMaterialKey(m, tas);

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
