package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaModel;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VisualBufferSource;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(GeoRenderer.class)
public interface GeoRenderMixin {
    @Inject(method = "renderCubesOfBone", at = @At(value = "HEAD"))
    default void addGeckoModel(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour, CallbackInfo ci){
        if (poseStack instanceof PoseStackVisual pv) {
            SinkBufferSourceVisual v = pv.getVisual();
            if (bone.getCubes().isEmpty()) return;
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

                v.updateTransforms(pv.getDepth(), poseStack.last());
                v.addInterpolatedTransformedInstance(pv.getDepth(), bone, key);
            } else {
                v.updateTransforms(pv.getDepth(), poseStack.last());
            }

        }
    }

}
