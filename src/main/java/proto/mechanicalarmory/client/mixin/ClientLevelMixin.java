package proto.mechanicalarmory.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    void sbu(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci){
        if (oldState.getBlock() == newState.getBlock() && oldState.getBlock() instanceof CropBlock) {
            ci.cancel();
        }
    }
}
