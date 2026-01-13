package proto.mechanicalarmory.client.gltf.data;

import dev.engine_room.flywheel.api.model.Model;
import org.joml.Vector4fc;

import java.util.List;

public class GltfMesh implements Model {

    ConfiguredMesh makeConfiguredMesh(GltfAccessor position, GltfAccessor texture, GltfAccessor normal) {
        return  new ConfiguredMesh(null, null);
    }
    @Override
    public List<ConfiguredMesh> meshes() {
        return List.of();
    }

    @Override
    public Vector4fc boundingSphere() {
        return null;
    }
}
