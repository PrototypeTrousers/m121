package proto.mechanicalarmory.client.gltf;

import de.javagl.jgltf.model.MeshPrimitiveModel;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;

public class GltfFlywheelModel implements Model {
    List<ConfiguredMesh> meshes = new ArrayList<>();

    public GltfFlywheelModel(MeshPrimitiveModel meshPrimitiveModel) {
        meshes.add(new ConfiguredMesh(SimpleMaterial.builder().build(), new GltfMesh(meshPrimitiveModel)));
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1,1,1,1);
    }
}
