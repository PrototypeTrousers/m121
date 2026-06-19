package proto.mechanicalarmory.client.flywheel.gltf;

import de.javagl.jgltf.model.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

import java.util.Collections;
import java.util.Map;

public class GltfFlywheelModelTree {
    static private int childidx;

    public static MyModelTree create(GltfModel gltfModel) {
        childidx = 0;
        Map<String, MyModelTree> children = new Object2ObjectArrayMap<>();
        for (SceneModel sm : gltfModel.getSceneModels()) {
            for (NodeModel nm : sm.getNodeModels()) {
                Map<String, MyModelTree> newChildren = new Object2ObjectArrayMap<>();
                float[] translation;
                translation = nm.getTranslation();
                MyPartPose pp;
                if (translation != null) {
                    pp = MyPartPose.offset(translation[0], translation[1], translation[2]);
                } else {
                    pp = MyPartPose.ZERO;
                }
                addNodeChildren(nm, newChildren);
                MyModelTree MyModelTree = new MyModelTree(null, pp, newChildren);

                String nmName = nm.getName();
                children.put(nmName != null ? nmName : String.valueOf(childidx++), MyModelTree);
            }
        }
        return new MyModelTree(null, MyPartPose.ZERO, children);
    }

    static void addNodeChildren(NodeModel nm, Map<String, MyModelTree> children) {
        for (MeshModel mm : nm.getMeshModels()) {
            for (MeshPrimitiveModel pm : mm.getMeshPrimitiveModels()) {
                MyModelTree MyModelTree = new MyModelTree(new GltfFlywheelModel(nm, pm), MyPartPose.ZERO, Collections.EMPTY_MAP);
                String mmName = mm.getName();
                children.put(mmName != null ? mmName : String.valueOf(childidx++), MyModelTree);
            }
        }

        for (NodeModel mm : nm.getChildren()) {
            Map<String, MyModelTree> newChildren = new Object2ObjectArrayMap<>();
            MyPartPose pp = MyPartPose.offset(mm.getTranslation()[0], mm.getTranslation()[1], mm.getTranslation()[2]);
            addNodeChildren(mm, newChildren);
            MyModelTree MyModelTree;
            if (mm.getName().equals("ItemAttach")) {
                MyModelTree = new MyModelTree(new GltfFlywheelModel(nm, null), pp, newChildren);
            } else {
                MyModelTree = new MyModelTree(null, pp, newChildren);
            }
            String mmName = mm.getName();
            children.put(mmName != null ? mmName : String.valueOf(childidx++), MyModelTree);
        }
    }
}
