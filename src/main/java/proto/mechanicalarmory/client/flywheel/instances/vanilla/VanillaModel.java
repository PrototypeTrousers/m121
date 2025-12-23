package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.internal.FlwLibLink;
import dev.engine_room.flywheel.lib.material.CutoutShaders;
import dev.engine_room.flywheel.lib.material.LightShaders;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import proto.mechanicalarmory.client.mixin.MaterialAccessor;

import java.util.ArrayList;
import java.util.List;

public class VanillaModel implements Model {

    private static final RendererReloadCache<ModelVanillaKey, VanillaModel> CACHE = new RendererReloadCache<>(modelVanillaKey ->
            new VanillaModel(modelVanillaKey.part, modelVanillaKey.material));

    private record ModelVanillaKey(ModelPart part, net.minecraft.client.resources.model.Material material) {
    }

    private static final ThreadLocal<ThreadLocalObjects> THREAD_LOCAL_OBJECTS = ThreadLocal.withInitial(ThreadLocalObjects::new);
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    List<ConfiguredMesh> meshes = new ArrayList<>();

    public static VanillaModel of(ModelPart part, net.minecraft.client.resources.model.Material material) {
        return CACHE.get(new ModelVanillaKey(part, material));
    }

    VanillaModel(ModelPart part, net.minecraft.client.resources.model.Material material) {
        Mesh m = fromPart(part, material);
        if (m != null) {
            meshes.add(new ConfiguredMesh(makeFlywheelMaterial(material), m));
        }
    }

    SimpleQuadMesh fromPart(ModelPart part, Material material) {
        if (part.isEmpty()) {
            return null;
        }

        VanillaVertexWriter vertexWriter = THREAD_LOCAL_OBJECTS.get().vertexWriter;
        VertexConsumer v;
        if (material.texture() != null) {
            v = new SpriteCoordinateExpander(vertexWriter, material.sprite());
        } else {
            v = vertexWriter;
        }
        FlwLibLink.INSTANCE.compileModelPart(part, IDENTITY_POSE, v, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        MemoryBlock data = vertexWriter.copyDataAndReset();

        VertexView vertexView = new PosTexNormalVertexView();
        vertexView.load(data);
        return new SimpleQuadMesh(vertexView, "source=VanillaModel");
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1, 1, 1, 1);
    }

    private static class ThreadLocalObjects {
        public final VanillaVertexWriter vertexWriter = new VanillaVertexWriter();
    }

    public dev.engine_room.flywheel.api.material.Material makeFlywheelMaterial(net.minecraft.client.resources.model.Material material) {
        RenderType rt = ((MaterialAccessor) material).getRenderType();

        return SimpleMaterial.builder()
                .texture(material.atlasLocation())
                .cutout(CutoutShaders.EPSILON)
                .light(LightShaders.SMOOTH_WHEN_EMBEDDED)
                .cardinalLightingMode(CardinalLightingMode.OFF)
                .ambientOcclusion(false)
                .build();
    }
}
