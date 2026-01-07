package proto.mechanicalarmory.client.mixin;

import com.google.common.collect.HashBiMap;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.irisshaders.batchedentityrendering.impl.SegmentedBufferBuilder;
import net.irisshaders.iris.layer.BufferSourceWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
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

import java.util.Map;

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
                RenderType r;
                MultiBufferAccessor accessor;

                MultiBufferSource mbs = v.getBufferSource();

                if (mbs instanceof BufferSourceWrapper bsw) {
                    accessor = (MultiBufferAccessor) bsw.getOriginal();
                } else {
                    accessor = (MultiBufferAccessor) v.getBufferSource();
                }
                HashBiMap<RenderType, BufferBuilder> map = (HashBiMap<RenderType, BufferBuilder>) accessor.getStartedBuilders();

                if (buffer instanceof SpriteCoordinateExpanderAccessor sceb) {
                    tas = sceb.getSprite();
                    buffer = sceb.getDelegate();
                }

                r = map.inverse().get(buffer);

                if (accessor instanceof BatchableBufferSourceAccessor bbs) {
                    for (Map.Entry<RenderType, ReferenceSet<BufferBuilder>> entry : bbs.getPendingBuffers().entrySet()) {
                        RenderType key = entry.getKey();
                        ReferenceSet<BufferBuilder> value = entry.getValue();
                        if (value.contains(buffer)) {
                            r = key;
                        }
                    }
                }

                if (accessor instanceof FullyBufferedMultiBufferSourceAccessor bbs) {
                    SegmentedBufferBuilder[] builders = bbs.getBuilders();
                    for (SegmentedBufferBuilder builder : builders) {
                        for (Map.Entry<RenderType, BufferBuilder> entry : ((SegmentedBufferBuilderAccessor) builder).getBuilders().entrySet()) {
                            RenderType key = entry.getKey();
                            BufferBuilder value = entry.getValue();
                            if (value == buffer) {
                                r = key;
                                break;
                            }
                        }
                    }
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
