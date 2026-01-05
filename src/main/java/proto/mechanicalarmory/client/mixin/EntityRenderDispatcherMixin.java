package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.impl.BackendManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualManagerImpl;
import dev.engine_room.flywheel.impl.visualization.VisualizationManagerImpl;
import dev.engine_room.flywheel.impl.visualization.storage.EntityStorage;
import dev.engine_room.flywheel.lib.visual.component.ShadowComponent;
import dev.engine_room.flywheel.lib.visualization.VisualizationHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.ExtendedRecyclingPoseStack;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.VanillaEntityVisual;
import proto.mechanicalarmory.client.flywheel.instances.vanilla.WrappingPoseStack;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaEntityVisual visual;

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private static void injected(Args args) {
        if (visual != null) {
            WrappingPoseStack psv = visual.getPoseStackVisual();
            psv.getWrappedPoseStack().last().pose().setTranslation(
                    visual.getVisualPosition().x(),
                    visual.getVisualPosition().y(),
                    visual.getVisualPosition().z());
            psv.setDepth(0);
            if (psv.isRendered()) {
                args.set(2, 1f);
            }
            PoseStack poseStack = args.get(3);
            psv.last().pose().set(poseStack.last().pose());
            args.set(3, psv);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void setRendered(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (visual != null) {
            visual.getPoseStackVisual().setRendered();
        }
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void setupShadow(PoseStack poseStack, MultiBufferSource buffer, Entity entity, float weight, float partialTicks, LevelReader level, float size, CallbackInfo ci) {
        if (visual != null) {
            ShadowComponent sc = visual.getShadowComponent();
            sc.radius(size);
            sc.strength(weight);
            ci.cancel();
        }
    }

    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    boolean should(EntityRenderer<?> instance, Entity leashable, Frustum aabb, double v, double livingEntity, double camera, Operation<Boolean> original) {
        visual = null;
        if (BackendManagerImpl.isBackendOn()) {
            if (original.call(instance, leashable, aabb, v, livingEntity, camera) == true) {
                if (VisualizationHelper.canVisualize(leashable)) {
                    VisualizationManagerImpl man = VisualizationManagerImpl.get(leashable.level());
                    if (man != null) {
                        VisualManagerImpl<Entity, EntityStorage> iii = (VisualManagerImpl<Entity, EntityStorage>) man.entities();
                        visual = (VanillaEntityVisual) ((StorageMixinAccessor) iii.getStorage()).getVisualsFor().get(leashable);
                        if (visual != null) {
                            if (!visual.getPoseStackVisual().isRendered()) {
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
            return original.call(instance, leashable, aabb, v, livingEntity, camera);
        }
    }
}
