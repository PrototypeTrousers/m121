package proto.mechanicalarmory.client.gltf;

import de.javagl.jgltf.model.*;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.model.geom.PartPose;

import java.util.Collections;
import java.util.Map;

public class GltfFlywheelModelTree {

    public static ModelTree create(GltfModel gltfModel) {
        Map<String, ModelTree> children = new Object2ObjectArrayMap<>();
        for (SceneModel sm : gltfModel.getSceneModels()) {
            for (NodeModel nm : sm.getNodeModels()) {
                addNodeChildren(nm,children);
            }
        }
        return new ModelTree(null, PartPose.ZERO, children);
    }

    static void addNodeChildren(NodeModel nm, Map<String, ModelTree> children) {
        //Breadth First
        for (MeshModel mm : nm.getMeshModels()) {
            for (MeshPrimitiveModel pm : mm.getMeshPrimitiveModels()) {
                ModelTree modelTree = new ModelTree(new GltfFlywheelModel(pm), PartPose.ZERO, Collections.EMPTY_MAP);
                children.put(mm.getName(), modelTree);
            }
        }

        for (NodeModel mm : nm.getChildren()) {
            Map<String, ModelTree> newChildren = new Object2ObjectArrayMap<>();
            addNodeChildren(mm, newChildren);
            ModelTree modelTree = new ModelTree(null, PartPose.ZERO, newChildren);
            children.put(mm.getName(), modelTree);
        }
    }

}
