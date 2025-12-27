package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public interface SinkBufferSourceVisual {
    VisualBufferSource getBufferSource();

    void addInterpolatedTransformedInstance(int depth, ModelPart modelPart, Material mat);

    void updateTransforms(int depth, Matrix4f pose);

    void addInterpolatedItemTransformedInstance(int depth, ItemStack stack);

    void updateItemTransforms(int depth, Matrix4f pose);
}
