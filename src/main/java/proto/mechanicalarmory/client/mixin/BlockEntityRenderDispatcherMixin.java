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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.PoseStackVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaBlockEntityVisual;

@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaBlockEntityVisual v;

    @Inject(method = "setupAndRender", at = @At(value = "HEAD"), cancellable = true)
    private static <T extends BlockEntity> void injected(BlockEntityRenderer<T> renderer, T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (v != null) {
            PoseStackVisual psv = v.getPoseStackVisual();
            psv.last().pose().setTranslation(v.getVisualPosition().getX(), v.getVisualPosition().getY(), v.getVisualPosition().getZ());
            psv.setDepth(0);

            renderer.render(blockEntity,
                    psv.isRendered() ? 1f : partialTick,
                    v.poseStackVisual,
                    v.visualBufferSource,
                    0,
                    OverlayTexture.NO_OVERLAY);
            ci.cancel();
            psv.setRendered();
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
                            if (!v.poseStackVisual.isRendered()) {
                                return true;
                            }
                            return MechanicalArmoryClient.firstFrameOfTick;
                        }
                        return true;
                    }
                }
            }
            return false;
        } else {
            return original.call(instance, blockEntity, cameraPos);
        }
    }
}
