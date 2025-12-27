package proto.mechanicalarmory.client.mixin;

import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import rearth.oritech.util.AutoPlayingSoundKeyframeHandler;

@Mixin(AutoPlayingSoundKeyframeHandler.class)
public class AutoPlayingSoundKeyframeHandlerMixin {
    @ModifyVariable(method = "handle", at = @At("STORE"), name = "random")
    RandomSource injected(RandomSource x) {
        return x.fork();
    }
}