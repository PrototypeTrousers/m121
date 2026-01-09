package proto.mechanicalarmory.client.flywheel.gltf;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.kneelawk.krender.model.gltf.impl.GltfFile;
import com.kneelawk.krender.model.gltf.impl.format.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
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
    public GltfFlywheelModel(GltfNode gltfNode, GltfPrimitive gltfPrimitive, GltfFile gltfFile) {

        GltfRoot gltfRoot = gltfFile.root();
        int mIdx = gltfPrimitive.material().getAsInt();

        GltfMaterial material = gltfRoot.materials().get(mIdx);
        GltfImage image = gltfRoot.images().get(mIdx);
        GltfBufferView bufferView = gltfRoot.bufferViews().get(image.bufferView().getAsInt());

        TextureManager manager = Minecraft.getInstance().getTextureManager();

        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(MODID, "dynamic/" + gltfNode.name().get().toLowerCase());

//         DynamicTexture needs to be created on the Render thread otherwise it crashes with pixels being null.. somehow
        RenderSystem.recordRenderCall(() -> {
            try {
                embeddedTexture = NativeImage.read(gltfFile.getImageBuffer(bufferView.buffer()).createStream());
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
                new GltfMesh(gltfPrimitive, gltfFile)));
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
