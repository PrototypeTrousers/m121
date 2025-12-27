package proto.mechanicalarmory.client.mixin;

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
                v.addInterpolatedTransformedInstance(pv.getDepth(), bone, mat);

            } else {
                v.updateTransforms(pv.getDepth(), poseStack.last().pose());
            }
        }
    }

}
