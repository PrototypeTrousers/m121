package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.api.instance.Instance;

import java.util.function.Consumer;

public class LightHelper {
    public static void light(InstanceTree tree, int packedLight) {
        if (tree != null) {
            tree.traverse(inst -> inst.light(packedLight));
        }
    }

    public static void crumble(InstanceTree tree, Consumer<Instance> consumer) {
        if (tree != null) {
            tree.traverse(inst -> consumer.accept(inst));
        }
    }
}
