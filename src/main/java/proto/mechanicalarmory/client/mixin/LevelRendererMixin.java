package proto.mechanicalarmory.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.client.LastRenderTimeTracker;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin implements LastRenderTimeTracker {

    @Unique
    private static long lastRenderTime;
    private static long currentRenderTime;

    @Shadow
    private ClientLevel level;

    @Inject(method = "renderLevel", at = @At("TAIL"))
    void updateLastRenderedTick(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci){

        lastRenderTime = currentRenderTime;
        currentRenderTime = level.getGameTime();
    }

    @Override
    public boolean m121$isFirstFrameOfRenderTick() {
        return lastRenderTime != currentRenderTime;
    }
}