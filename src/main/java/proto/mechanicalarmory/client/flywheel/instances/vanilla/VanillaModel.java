package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.model.Mesh;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.internal.FlwLibLink;
import dev.engine_room.flywheel.lib.memory.MemoryBlock;
import dev.engine_room.flywheel.lib.model.SimpleQuadMesh;
import dev.engine_room.flywheel.lib.model.part.MeshTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import dev.engine_room.flywheel.lib.util.RendererReloadCache;
import dev.engine_room.flywheel.lib.vertex.PosTexNormalVertexView;
import dev.engine_room.flywheel.lib.vertex.VertexView;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VanillaModel implements Model {

    private static final RendererReloadCache<ModelVanillaKey, VanillaModel> CACHE = new RendererReloadCache<>(modelVanillaKey ->
            new VanillaModel(modelVanillaKey.part, modelVanillaKey.material, modelVanillaKey.textureAtlasSprite));

    private record ModelVanillaKey(ModelPart part, Material material, TextureAtlasSprite textureAtlasSprite) {
    }

    private static final ThreadLocal<ThreadLocalObjects> THREAD_LOCAL_OBJECTS = ThreadLocal.withInitial(ThreadLocalObjects::new);
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    List<ConfiguredMesh> meshes = new ArrayList<>();

    public static VanillaModel of(ModelPart part, Material material, TextureAtlasSprite textureAtlasSprite) {
        return CACHE.get(new ModelVanillaKey(part, material, textureAtlasSprite));
    }

    VanillaModel(ModelPart part, Material material, TextureAtlasSprite textureAtlasSprite) {
        Mesh m = fromPart(part, textureAtlasSprite);
        if (m != null) {
            meshes.add(new ConfiguredMesh(material, m));
        }
    }

    SimpleQuadMesh fromPart(ModelPart part, TextureAtlasSprite textureAtlasSprite) {
        if (part.isEmpty()) {
            return null;
        }

        VanillaVertexWriter vertexWriter = THREAD_LOCAL_OBJECTS.get().vertexWriter;
        VertexConsumer v = new SpriteCoordinateExpander(vertexWriter, textureAtlasSprite);
        FlwLibLink.INSTANCE.compileModelPart(part, IDENTITY_POSE, v, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        MemoryBlock data = vertexWriter.copyDataAndReset();

        VertexView vertexView = new PosTexNormalVertexView();
        vertexView.load(data);
        return new SimpleQuadMesh(vertexView, "source=MeshTree");
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1,1,1,1);
    }

    private static class ThreadLocalObjects {
        public final VanillaVertexWriter vertexWriter = new VanillaVertexWriter();
    }
}
