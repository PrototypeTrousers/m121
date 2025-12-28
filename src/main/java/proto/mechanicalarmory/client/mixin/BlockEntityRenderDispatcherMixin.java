package proto.mechanicalarmory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualManager;
import dev.engine_room.flywheel.api.visualization.VisualizationLevel;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.engine_room.flywheel.impl.visualization.storage.Storage;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.SinkBufferSourceVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaBlockEntityVisual v;

    @ModifyArgs(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static void injected(Args args) {
        BlockEntity be = args.get(0);
        v = null;
        if (VisualizationHelper.canVisualize(be)) {
            VisualizationManagerImpl man = VisualizationManagerImpl.get(be.getLevel());
            if (man != null) {
                VisualManagerImpl<BlockEntity, BlockEntityStorage> iii = (VisualManagerImpl<BlockEntity, BlockEntityStorage>) man.blockEntities();
                v = (VanillaBlockEntityVisual) iii.getStorage().visualAtPos(be.getBlockPos().asLong());
                if (v != null) {
                    v.poseStackVisual.last().pose().setTranslation(v.getVisualPosition().getX(), v.getVisualPosition().getY(), v.getVisualPosition().getZ());
                    v.poseStackVisual.setDepth(0);
                    args.set(2, v.poseStackVisual);
                    args.set(3, v.visualBufferSource);
                }
            }
        }
    }

    @Inject(method = "setupAndRender", at = @At("TAIL"))
    private static void setRendered(BlockEntityRenderer<?> renderer, BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci){
        if (v != null) {
            v.poseStackVisual.setRendered();
            v.visualBufferSource.setRendered(true);
        }
    }


}
