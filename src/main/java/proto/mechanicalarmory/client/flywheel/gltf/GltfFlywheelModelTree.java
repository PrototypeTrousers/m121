package proto.mechanicalarmory.client.flywheel.gltf;

import com.kneelawk.krender.model.gltf.impl.GltfFile;
import com.kneelawk.krender.model.gltf.impl.format.*;
import com.kneelawk.krender.model.gltf.impl.format.GltfMesh;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.model.geom.PartPose;

import java.util.Collections;
import java.util.Map;

public class GltfFlywheelModelTree {

    public static ModelTree create(GltfFile gltfFile) {
        Map<String, ModelTree> children = new Object2ObjectArrayMap<>();
        GltfRoot gltfRoot = gltfFile.root();
        for (GltfScene sm : gltfRoot.scenes()) {
            for (int nI : sm.nodes()) {
                GltfNode nm = gltfRoot.nodes().get(nI);
                Map<String, ModelTree> newChildren = new Object2ObjectArrayMap<>();
                PartPose pp = PartPose.offset(nm.translation()[0] * 16, nm.translation()[1] * 16, nm.translation()[2] * 16);
                addNodeChildren(nm, newChildren, gltfFile);
                ModelTree modelTree = new ModelTree(null, pp, newChildren);
                children.put(nm.name().orElse(""), modelTree);
            }
        }
        return new ModelTree(null, PartPose.ZERO, children);
    }

    static void addNodeChildren(GltfNode nm, Map<String, ModelTree> children, GltfFile gltfFile) {
        GltfRoot gltfRoot = gltfFile.root();
        if (nm.mesh().isPresent()) {
            GltfMesh mesh = gltfRoot.meshes().get(nm.mesh().getAsInt());
            for (GltfPrimitive gltfPrimitive : mesh.primitives()) {
                ModelTree modelTree = new ModelTree(new GltfFlywheelModel(nm, gltfPrimitive, gltfFile), PartPose.ZERO, Collections.EMPTY_MAP);
                children.put(nm.name().orElse(""), modelTree);
            }
        }

        int[] cIx =nm.children();

        for (int nI : cIx) {
            GltfNode mm = gltfRoot.nodes().get(nI);
            Map<String, ModelTree> newChildren = new Object2ObjectArrayMap<>();
            PartPose pp = PartPose.offset(mm.translation()[0] * 16, mm.translation()[1] * 16, mm.translation()[2] * 16);
            addNodeChildren(mm, newChildren, gltfFile);
            ModelTree modelTree = new ModelTree(null, pp, newChildren);
            children.put(mm.name().orElse(""), modelTree);
        }
    }
}
