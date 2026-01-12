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
import proto.mechanicalarmory.client.flywheel.instances.generic.VanillaEntityVisual;
import proto.mechanicalarmory.client.flywheel.instances.generic.posestacks.WrappingPoseStack;

@Mixin(value = EntityRenderDispatcher.class, priority = 1001)
public class EntityRenderDispatcherMixin {
    @Shadow
    public Level level;

    @Unique
    private static VanillaEntityVisual entityVisual;

    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private static void injected(Args args) {
        if (entityVisual != null) {
            WrappingPoseStack psv = entityVisual.getPoseStackVisual();
            psv.getWrappedPoseStack().last().pose().setTranslation(
                    entityVisual.getVisualPosition().x(),
                    entityVisual.getVisualPosition().y(),
                    entityVisual.getVisualPosition().z());
            psv.setDepth(0);
            if (psv.isRendered()) {
                args.set(2, 1f);
            }
            PoseStack poseStack = args.get(3);
            psv.last().pose().set(poseStack.last().pose());
            args.set(3, psv);
            entityVisual.setBufferSource(args.get(4));
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private static void setRendered(Entity entity, double x, double y, double z, float rotationYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entityVisual != null) {
            entityVisual.getPoseStackVisual().setRendered();
            entityVisual.setBufferSource(null);
        }
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void setupShadow(PoseStack poseStack, MultiBufferSource buffer, Entity entity, float weight, float partialTicks, LevelReader level, float size, CallbackInfo ci) {
        if (entityVisual != null) {
            ShadowComponent sc = entityVisual.getShadowComponent();
            sc.radius(size);
            sc.strength(weight);
            ci.cancel();
        }
    }

    @WrapOperation(method = "shouldRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z"))
    boolean should(EntityRenderer<?> instance, Entity leashable, Frustum aabb, double v, double livingEntity, double camera, Operation<Boolean> original) {
        entityVisual = null;
        boolean should = original.call(instance, leashable, aabb, v, livingEntity, camera);
        if (should && BackendManagerImpl.isBackendOn()) {
            if (VisualizationHelper.canVisualize(leashable)) {
                VisualizationManagerImpl man = VisualizationManagerImpl.get(leashable.level());
                if (man != null) {
                    VisualManagerImpl<Entity, EntityStorage> iii = (VisualManagerImpl<Entity, EntityStorage>) man.entities();
                    if (((StorageMixinAccessor) iii.getStorage()).getVisualsFor().get(leashable) instanceof VanillaEntityVisual visual) {
                        entityVisual = visual;
                        if (!entityVisual.getPoseStackVisual().isRendered()) {
                            return true;
                        }
                        return MechanicalArmoryClient.firstFrameOfTick;
                    }
                }
            }
        }
        return should;
    }
}
