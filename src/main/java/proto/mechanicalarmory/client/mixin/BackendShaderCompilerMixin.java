package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.backend.compile.IndirectPrograms;
import dev.engine_room.flywheel.backend.compile.core.CompilationHarness;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL43C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.flywheel.compile.Compute;
import proto.mechanicalarmory.client.flywheel.instances.arm.InterpolatingInstancetype;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

@Mixin(value = IndirectPrograms.class, remap = false)
public class BackendShaderCompilerMixin {

    @Inject(method = "reload", at = @At("HEAD"))
    private static void onBackendShutdown(CallbackInfo ci) {
        // Safe GPU clean up when the engine closes
        if (MechanicalArmory.computeShaderId != -1) {
            GL20C.glDeleteProgram(MechanicalArmory.computeShaderId);
            MechanicalArmory.computeShaderId = -1;
        }

        CompilationHarness<InstanceType<?>> compiler = Compute.createComputingCompiler(new ShaderSources(Minecraft.getInstance().getResourceManager()));
        GlProgram glProgram = compiler.get(InterpolatingInstancetype.INTERPOLATED);

        // Compile the compute shader when the backend engine starts up or reloads
        MechanicalArmory.computeShaderId = glProgram.handle();
    }
}