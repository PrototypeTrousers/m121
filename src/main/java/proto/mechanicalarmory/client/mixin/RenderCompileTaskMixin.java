package proto.mechanicalarmory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "brachy.modularui.drawable.schema.BaseSchemaRenderer$RenderCompileTask")
public class RenderCompileTaskMixin {
    @WrapOperation(method = "compileBlockBuffers", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    RenderShape shape(BlockState instance, Operation<RenderShape> original) {
        if (instance.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED)
            return RenderShape.INVISIBLE;
        return original.call(instance);
    }
}
