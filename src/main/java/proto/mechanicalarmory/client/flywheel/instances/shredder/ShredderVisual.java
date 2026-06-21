package proto.mechanicalarmory.client.flywheel.instances.shredder;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.gltf.MyInstanceTree;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

import java.util.function.Consumer;

public class ShredderVisual extends AbstractBlockEntityVisual<ShredderEntity> {
    private final MyInstanceTree instanceTree;
    private final Matrix4fc initialPose;


    public ShredderVisual(VisualizationContext ctx, ShredderEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        instanceTree = MyInstanceTree.create(instancerProvider(), MechanicalArmoryClient.shredderModelTree);
        initialPose = new Matrix4f().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());
        instanceTree.updateInstancesStatic(initialPose);
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {

    }

    @Override
    public void updateLight(float partialTick) {
        var packedLight = LevelRenderer.getLightColor(level, pos.above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
    }
}
