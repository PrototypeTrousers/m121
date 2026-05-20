package proto.mechanicalarmory.client.mixin;

import dev.engine_room.flywheel.backend.compile.IndirectPrograms;
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
        // Compile the compute shader when the backend engine starts up or reloads
        MechanicalArmory.computeShaderId = mechanicalArmory$compileComputeShader(
                ResourceLocation.fromNamespaceAndPath(MODID, "flywheel/interpolated/interpolcompute")
        );
    }

    @Unique
    private static int mechanicalArmory$compileComputeShader(ResourceLocation location) {
        // Load raw GLSL text from assets folder
        String source;
        try (InputStream stream = Minecraft.getInstance().getResourceManager().open(location)) {
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load compute shader: " + location, e);
        }

        // 1. Create and compile shader stage
        int shaderId = GL43C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        GL43C.glShaderSource(shaderId, source);
        GL43C.glCompileShader(shaderId);

        if (GL43C.glGetShaderi(shaderId, GL43C.GL_COMPILE_STATUS) == GL43C.GL_FALSE) {
            String log = GL43C.glGetShaderInfoLog(shaderId);
            GL43C.glDeleteShader(shaderId);
            throw new RuntimeException("Compute shader compilation failed (" + location + "):\n" + log);
        }

        // 2. Link shader into executable program
        int programId = GL20C.glCreateProgram();
        GL20C.glAttachShader(programId, shaderId);
        GL20C.glLinkProgram(programId);

        if (GL20C.glGetProgrami(programId, GL20C.GL_LINK_STATUS) == GL20C.GL_FALSE) {
            String log = GL20C.glGetProgramInfoLog(programId);
            GL20C.glDeleteProgram(programId);
            GL43C.glDeleteShader(shaderId);
            throw new RuntimeException("Compute shader linking failed (" + location + "):\n" + log);
        }

        // Flag shader object for deletion once detached from program context
        GL43C.glDeleteShader(shaderId);
        return programId;
    }
}