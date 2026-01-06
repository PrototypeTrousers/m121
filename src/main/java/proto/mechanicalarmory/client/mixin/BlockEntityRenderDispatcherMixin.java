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
import proto.mechanicalarmory.client.flywheel.instances.vanilla.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.WrappingPoseStack;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaBlockEntityVisual v;

    @Inject(method = "setupAndRender", at = @At(value = "HEAD"), cancellable = true)
    private static <T extends BlockEntity> void injected(BlockEntityRenderer<T> renderer, T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (v != null) {
            WrappingPoseStack psv = v.getPoseStackVisual();
            psv.getWrappedPoseStack().last().pose().setTranslation(v.getVisualPosition().getX(), v.getVisualPosition().getY(), v.getVisualPosition().getZ());
            psv.setDepth(0);
            psv.last().pose().set(poseStack.last().pose());
            renderer.render(blockEntity,
                    psv.isRendered() ? 1f : partialTick,
                    v.extendedRecyclingPoseStack,
                    bufferSource,
                    15728880,
                    OverlayTexture.NO_OVERLAY);
            ci.cancel();
            if (!psv.isRendered()) {
                psv.setRendered();
            }
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;shouldRender(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/phys/Vec3;)Z"))
    boolean should(BlockEntityRenderer<?> instance, BlockEntity blockEntity, Vec3 cameraPos, Operation<Boolean> original, BlockEntity blockEntity1, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        v = null;
        boolean should = original.call(instance, blockEntity, cameraPos) == true;
        if (should && BackendManagerImpl.isBackendOn()) {
            if (VisualizationHelper.canVisualize(blockEntity)) {
                VisualizationManagerImpl man = VisualizationManagerImpl.get(blockEntity.getLevel());
                if (man != null) {
                    VisualManagerImpl<BlockEntity, BlockEntityStorage> iii = (VisualManagerImpl<BlockEntity, BlockEntityStorage>) man.blockEntities();
                    if (iii.getStorage().visualAtPos(blockEntity.getBlockPos().asLong()) instanceof VanillaBlockEntityVisual vanillaBlockEntityVisual) {
                        vanillaBlockEntityVisual = (VanillaBlockEntityVisual) iii.getStorage().visualAtPos(blockEntity.getBlockPos().asLong());
                        if (vanillaBlockEntityVisual != null) {
                            v = vanillaBlockEntityVisual;
                            if (!vanillaBlockEntityVisual.extendedRecyclingPoseStack.isRendered() || vanillaBlockEntityVisual.extendedRecyclingPoseStack.isLegacyAccessed()) {
                                return true;
                            }
                            return MechanicalArmoryClient.firstFrameOfTick;
                        }
                    }
                    return true;
                }
            }
        }
        return should;
    }
}
