package proto.mechanicalarmory.client.mixin;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(LegacyRandomSource.class)
public class LegacyRandomSourceMixin {
    List<String> threadsNames = new ArrayList<>();
    @Final
    @Shadow
    private final AtomicLong seed = new AtomicLong();;


    @Inject(method = "next", at = @At("HEAD"))
    void addThreads(int size, CallbackInfoReturnable<Integer> cir) {
        if (!threadsNames.contains(Thread.currentThread().getName())) {
            threadsNames.add(Thread.currentThread().getName());
        }
    }

    @Overwrite
    public int next(int size) {
        long nextSeed = this.seed.updateAndGet(i -> (i * 25214903917L + 11L) & 281474976710655L);
        return (int)(nextSeed >> (48 - size));
    }
}
