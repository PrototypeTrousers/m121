package proto.mechanicalarmory.client.flywheel.compile;

import com.google.common.collect.ImmutableList;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.backend.compile.component.InstanceStructComponent;
import dev.engine_room.flywheel.backend.compile.component.SsboInstanceComponent;
import dev.engine_room.flywheel.backend.compile.core.CompilationHarness;
import dev.engine_room.flywheel.backend.compile.core.Compile;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.backend.gl.GlCompat;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import dev.engine_room.flywheel.backend.glsl.GlslVersion;
import dev.engine_room.flywheel.backend.glsl.ShaderSources;
import dev.engine_room.flywheel.lib.util.ResourceUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class Compute {
    private static final Compile<InstanceType<?>> COMPUTE = new Compile<>();
    private static final List<String> EXTENSIONS = getExtensions(GlCompat.MAX_GLSL_VERSION);

    private static final ResourceLocation CULL_SHADER_API_IMPL = ResourceUtil.rl("internal/indirect/cull_api_impl.glsl");
    private static final ResourceLocation COMPUTE_SHADER_MAIN = ResourceLocation.fromNamespaceAndPath(MODID, "interpolated/interpolcompute.comp");

    private static final List<String> COMPUTE_EXTENSIONS = getComputeExtensions(GlCompat.MAX_GLSL_VERSION);

    public static CompilationHarness<InstanceType<?>> createComputingCompiler(ShaderSources sources) {
        return COMPUTE.program()
                .link(COMPUTE.shader(GlCompat.MAX_GLSL_VERSION, ShaderType.COMPUTE)
                        .nameMapper(instanceType -> "culling/" + ResourceUtil.toDebugFileNameNoExtension(instanceType.cullShader()))
                        .requireExtensions(COMPUTE_EXTENSIONS)
                        .define("_FLW_SUBGROUP_SIZE", GlCompat.SUBGROUP_SIZE)
                        .withResource(CULL_SHADER_API_IMPL)
                        .withComponent(InstanceStructComponent::new)
                        .withComponent(SsboInstanceComponent::new)
                        .withResource(COMPUTE_SHADER_MAIN))
                .postLink((key, program) -> Uniforms.setUniformBlockBindings(program))
                .harness("culling", sources);
    }

    private static List<String> getExtensions(GlslVersion glslVersion) {
        var extensions = ImmutableList.<String>builder();
        if (glslVersion.compareTo(GlslVersion.V400) < 0) {
            extensions.add("GL_ARB_gpu_shader5");
        }
        if (glslVersion.compareTo(GlslVersion.V420) < 0) {
            extensions.add("GL_ARB_shading_language_420pack");
            extensions.add("GL_ARB_shader_image_load_store");
        }
        if (glslVersion.compareTo(GlslVersion.V430) < 0) {
            extensions.add("GL_ARB_shader_storage_buffer_object");
            extensions.add("GL_ARB_shader_image_size");
        }
        if (glslVersion.compareTo(GlslVersion.V460) < 0) {
            extensions.add("GL_ARB_shader_draw_parameters");
        }
        return extensions.build();
    }

    private static List<String> getComputeExtensions(GlslVersion glslVersion) {
        var extensions = ImmutableList.<String>builder();

        extensions.addAll(EXTENSIONS);

        if (glslVersion.compareTo(GlslVersion.V430) < 0) {
            extensions.add("GL_ARB_compute_shader");
        }
        return extensions.build();
    }
}
