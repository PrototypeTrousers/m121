package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import proto.mechanicalarmory.client.LastRenderTimeTracker;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaBlockEntityVisual v;

    @ModifyArgs(method = "setupAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;render(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
    private static void injected(Args args) {

        if (v != null) {
            v.poseStackVisual.last().pose().setTranslation(v.getVisualPosition().getX(), v.getVisualPosition().getY(), v.getVisualPosition().getZ());
            v.poseStackVisual.setDepth(0);
            if (v.visualBufferSource.isRendered()) {
                args.set(1, 1f);
            }
            args.set(2, v.poseStackVisual);
            args.set(3, v.visualBufferSource);
        }
    }

    @Inject(method = "setupAndRender", at = @At("TAIL"))
    private static void setRendered(BlockEntityRenderer<?> renderer, BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (v != null) {
            v.poseStackVisual.setRendered();
            v.visualBufferSource.setRendered(true);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    boolean should(BlockEntityRenderer<?> instance, BlockEntity blockEntity, Vec3 cameraPos, Operation<Boolean> original, BlockEntity blockEntity1, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        v = null;
        if (BackendManagerImpl.isBackendOn()) {
            if (original.call(instance, blockEntity, cameraPos) == true) {
                if (VisualizationHelper.canVisualize(blockEntity)) {
                    VisualizationManagerImpl man = VisualizationManagerImpl.get(blockEntity.getLevel());
                    if (man != null) {
                        VisualManagerImpl<BlockEntity, BlockEntityStorage> iii = (VisualManagerImpl<BlockEntity, BlockEntityStorage>) man.blockEntities();
                        v = (VanillaBlockEntityVisual) iii.getStorage().visualAtPos(blockEntity.getBlockPos().asLong());
                        if (v != null) {
                            if (!v.visualBufferSource.isRendered()) {
                                return true;
                            }
                        }
                    }
                    return ((LastRenderTimeTracker) Minecraft.getInstance().levelRenderer).m121$isFirstFrameOfRenderTick();
                }
            }
            return false;
        } else {
            return original.call(instance, blockEntity, cameraPos);
        }
    }
}
