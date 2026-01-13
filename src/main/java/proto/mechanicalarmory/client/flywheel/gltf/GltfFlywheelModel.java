package proto.mechanicalarmory.client.flywheel.gltf;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class GltfFlywheelModel implements Model {
    List<ConfiguredMesh> meshes = new ArrayList<>();
    NativeImage embeddedTexture;
    public GltfFlywheelModel(NodeModel nm, MeshPrimitiveModel meshPrimitiveModel) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        MaterialModelV2 m = ((MaterialModelV2) meshPrimitiveModel.getMaterialModel());
        TextureModel tm = m.getBaseColorTexture();
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MODID, "dynamic/" + tm.getImageModel().getName());

        // DynamicTexture needs to be created on the Render thread otherwise it crashes with pixels being null.. somehow
        RenderSystem.recordRenderCall(() -> {
            try {
                embeddedTexture = NativeImage.read(new ByteBufferBackedInputStream(tm.getImageModel().getImageData()));
                DynamicTexture d = new DynamicTexture(embeddedTexture);
                manager.register(rl, d);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        meshes.add(new ConfiguredMesh(SimpleMaterial.builder()
                .texture(rl)
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .ambientOcclusion(false)
                .build(),
                new GltfFlywheelMesh(meshPrimitiveModel)));
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1, 1, 1, 1);
    }
}
