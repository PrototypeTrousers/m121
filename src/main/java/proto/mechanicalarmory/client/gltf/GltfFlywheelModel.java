package proto.mechanicalarmory.client.gltf;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.mojang.blaze3d.platform.NativeImage;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.TextureModel;
import de.javagl.jgltf.model.v2.MaterialModelV2;
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
    public GltfFlywheelModel(NodeModel nm, MeshPrimitiveModel meshPrimitiveModel) {
        TextureManager manager = Minecraft.getInstance().getTextureManager();
        TextureModel tm;
        MaterialModelV2 m = ((MaterialModelV2) meshPrimitiveModel.getMaterialModel());
        tm = m.getBaseColorTexture();

        if (tm != null) {
            NativeImage embeddedTexture;
            try {
                embeddedTexture = NativeImage.read(new ByteBufferBackedInputStream(tm.getImageModel().getImageData()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MODID, "dynamic/" + tm.getImageModel().getName());
            manager.register(rl, new DynamicTexture(embeddedTexture));
            meshes.add(new ConfiguredMesh(SimpleMaterial.builder().texture(rl).build(), new GltfMesh(meshPrimitiveModel)));
        }
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
