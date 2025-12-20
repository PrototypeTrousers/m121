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
import proto.mechanicalarmory.client.flywheel.instances.vanilla.EnchantingTableVisual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static proto.mechanicalarmory.client.flywheel.instances.vanilla.EnchantingTableVisual.CURRENT_VISUAL;

@Mixin(targets = "net.minecraft.client.model.geom.ModelPart")
public abstract class ModelPartMixin {
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    public void a(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (CURRENT_VISUAL instanceof EnchantingTableVisual tableVisual) {
            tableVisual.modelPartPoseMap.put((ModelPart) (Object) this, pose);
            tableVisual.makeMaterialForPart((ModelPart) (Object) this, buffer);

        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("TAIL"))
    public void b(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color, CallbackInfo ci) {
        if (CURRENT_VISUAL instanceof EnchantingTableVisual tableVisual) {
            Map<RenderType, BufferBuilder> sb = ((BufferSourceAccessor) tableVisual.rb.bufferSource()).getStartedBuilders();

            List<RenderType> toRemove = new ArrayList<>(sb.keySet());
            for (RenderType k : toRemove) {
                //sb.remove(k);
            }
        }
    }
}
