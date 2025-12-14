package proto.mechanicalarmory.client.gltf;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import org.joml.Vector4f;
import org.joml.Vector4fc;

import java.util.ArrayList;
import java.util.List;

public class GltfFlywheelModel implements Model {
    List<Model> children;
    List<ConfiguredMesh> meshes = new ArrayList<>();

    public GltfFlywheelModel(GltfModel gltfModel) {
        List<NodeModel> nodes = gltfModel.getNodeModels();
        for (NodeModel node : nodes) {
            List<MeshModel> meshes2 = node.getMeshModels();
            for (MeshModel meshPrimitiveModel : meshes2) {
                List<MeshPrimitiveModel> meshPrimitiveModels = meshPrimitiveModel.getMeshPrimitiveModels();
                for (MeshPrimitiveModel meshPrimitiveModel2 : meshPrimitiveModels) {
                    meshes.add(new ConfiguredMesh(SimpleMaterial.builder().build(), new GltfMesh(meshPrimitiveModel2)));
                }
            }
        }
    }

    @Override
    public List<ConfiguredMesh> meshes() {
        return meshes;
    }

    @Override
    public Vector4fc boundingSphere() {
        return new Vector4f(1,1,1,1);
    }

    public List<Model> getChildren() {
        return children;
    }
}
