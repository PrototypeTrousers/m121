import proto.mechanicalarmory.client.flywheel.slicer.AutomatedVisualRegistry;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.client.renderer.blockentity.ChestRenderer;

public class TestGen {
    public static void main(String[] args) throws Exception {
        try {
            AutomatedVisualRegistry.generateAndMap(null, ChestRenderer.class, "render", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;FII)V");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
