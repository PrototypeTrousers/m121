package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.BlockEntityStorage;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.instances.generic.VanillaBlockEntityVisual;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.WrappingPoseStack;

@Mixin(value = BlockEntityRenderDispatcher.class, priority = 1001)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaBlockEntityVisual blockEntityVisual;

    @Inject(method = "setupAndRender", at = @At(value = "HEAD"), cancellable = true)
    private static <T extends BlockEntity> void injected(BlockEntityRenderer<T> renderer, T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (blockEntityVisual != null) {
            WrappingPoseStack psv = blockEntityVisual.getPoseStackVisual();
            psv.getWrappedPoseStack().last().pose().setTranslation(blockEntityVisual.getVisualPosition().getX(), blockEntityVisual.getVisualPosition().getY(), blockEntityVisual.getVisualPosition().getZ());
            psv.setDepth(0);
            psv.last().pose().set(poseStack.last().pose());
            blockEntityVisual.setBufferSource(bufferSource);
            renderer.render(blockEntity,
                    partialTick,
                    blockEntityVisual.extendedRecyclingPoseStack,
                    bufferSource,
                    15728880,
                    OverlayTexture.NO_OVERLAY);
            ci.cancel();
            if (!psv.isRendered()) {
                psv.setRendered();
            }
            blockEntityVisual.setBufferSource(null);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    boolean should(BlockEntityRenderer<?> instance, BlockEntity blockEntity, Vec3 cameraPos, Operation<Boolean> original, BlockEntity blockEntity1, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        blockEntityVisual = null;
        boolean should = original.call(instance, blockEntity, cameraPos) == true;
        if (should && BackendManagerImpl.isBackendOn()) {
            if (VisualizationHelper.canVisualize(blockEntity)) {
                VisualizationManagerImpl man = VisualizationManagerImpl.get(blockEntity.getLevel());
                if (man != null) {
                    VisualManagerImpl<BlockEntity, BlockEntityStorage> iii = (VisualManagerImpl<BlockEntity, BlockEntityStorage>) man.blockEntities();
                    if (iii.getStorage().visualAtPos(blockEntity.getBlockPos().asLong()) instanceof VanillaBlockEntityVisual visual) {
                        blockEntityVisual = visual;
                        if (!visual.extendedRecyclingPoseStack.isRendered()) {
                            return true;
                        }
                        return MechanicalArmoryClient.limiter.shouldUpdate((blockEntity.getBlockPos().distToCenterSqr(cameraPos)));
                    }
                }
            }
        }
        return should;
    }
}
