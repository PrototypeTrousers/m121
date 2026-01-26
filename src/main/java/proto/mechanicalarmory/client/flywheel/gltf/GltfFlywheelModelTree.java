package proto.mechanicalarmory.client.flywheel.gltf;

import de.javagl.jgltf.model.*;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.model.geom.PartPose;

import java.util.Collections;
import java.util.Map;

public class GltfFlywheelModelTree {
    static private int childidx;

    public static ModelTree create(GltfModel gltfModel) {
        childidx = 0;
        Map<String, ModelTree> children = new Object2ObjectArrayMap<>();
        for (SceneModel sm : gltfModel.getSceneModels()) {
            for (NodeModel nm : sm.getNodeModels()) {
                Map<String, ModelTree> newChildren = new Object2ObjectArrayMap<>();
                float[] translation;
                translation = nm.getTranslation();
                PartPose pp;
                if (translation != null) {
                    pp = PartPose.offset(translation[0] * 16, translation[1] * 16, translation[2] * 16);
                } else {
                    pp = PartPose.ZERO;
                }
                addNodeChildren(nm, newChildren);
                ModelTree modelTree = new ModelTree(null, pp, newChildren);

                String nmName = nm.getName();
                children.put(nmName != null ? nmName : String.valueOf(childidx++), modelTree);
            }
        }
        return new ModelTree(null, PartPose.ZERO, children);
    }

    static void addNodeChildren(NodeModel nm, Map<String, ModelTree> children) {
        for (MeshModel mm : nm.getMeshModels()) {
            for (MeshPrimitiveModel pm : mm.getMeshPrimitiveModels()) {
                ModelTree modelTree = new ModelTree(new GltfFlywheelModel(nm, pm), PartPose.ZERO, Collections.EMPTY_MAP);
                String mmName = mm.getName();
                children.put(mmName != null ? mmName : String.valueOf(childidx++), modelTree);
            }
        }

        for (NodeModel mm : nm.getChildren()) {
            Map<String, ModelTree> newChildren = new Object2ObjectArrayMap<>();
            PartPose pp = PartPose.offset(mm.getTranslation()[0] * 16, mm.getTranslation()[1] * 16, mm.getTranslation()[2] * 16);
            addNodeChildren(mm, newChildren);
            ModelTree modelTree;
            if (mm.getName().equals("ItemAttach")) {
                modelTree = new ModelTree(new GltfFlywheelModel(nm, null), pp, newChildren);
            } else {
                modelTree = new ModelTree(null, pp, newChildren);
            }
            String mmName = mm.getName();
            children.put(mmName != null ? mmName : String.valueOf(childidx++), modelTree);
        }
    }
}
