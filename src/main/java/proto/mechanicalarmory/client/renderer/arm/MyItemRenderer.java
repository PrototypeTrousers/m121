package proto.mechanicalarmory.client.renderer.arm;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.vertex.VertexList;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.gltf.GltfFlywheelModel;
import proto.mechanicalarmory.client.flywheel.gltf.GltfMesh;
import proto.mechanicalarmory.client.flywheel.gltf.TriIndexSequence;
import proto.mechanicalarmory.client.renderer.util.VertexConsumerMutableWrapper;

import java.nio.ByteBuffer;

import static net.minecraft.client.renderer.RenderStateShard.LIGHTMAP;
import static net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_SHADER;

public class MyItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final MyItemRenderer INSTANCE = new MyItemRenderer();
    ModelTree modelTree = MechanicalArmoryClient.fullArmModelTree;
    public static RenderType r;



    public MyItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
              Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transform, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderModelRecursive(modelTree, poseStack, bufferSource, packedLight, packedOverlay);

    }

    public void renderModelRecursive(ModelTree node, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();


        // 1. Apply local transformations
        // Assuming mt.initialPose() provides the local offset/rotation for this node
        var pose = node.initialPose();
        poseStack.translate(pose.x/16f, pose.y/16f, pose.z/16f);

        // If your initialPose includes rotation, apply it here:
        // poseStack.mulPose(pose.rotation());

        // 2. Render meshes attached to this specific node
        GltfFlywheelModel model = (GltfFlywheelModel) node.model();
        if (model != null) {
            model.meshes().forEach(configuredMesh -> {
                if (configuredMesh.mesh() instanceof GltfMesh gltfMesh) {
                    if (r == null) {
                        r = RenderType.create(
                                "arm",
                                DefaultVertexFormat.NEW_ENTITY,
                                VertexFormat.Mode.TRIANGLES,
                                512,
                                true,
                                false,
                                RenderType.CompositeState.builder()
                                        .setLightmapState(LIGHTMAP)
                                        .setShaderState(RENDERTYPE_ENTITY_CUTOUT_SHADER)
                                        .setTextureState(new RenderStateShard.TextureStateShard(configuredMesh.material().texture(), false, false))
                                        .createCompositeState(true));
                    }

                    VertexConsumer consumer = bufferSource.getBuffer(r);
                    VertexConsumerMutableWrapper wrapper = new VertexConsumerMutableWrapper(
                            poseStack, consumer, gltfMesh.vertexCount(), packedLight, packedOverlay
                    );

                    VertexList v = gltfMesh.getVertexList();
                    ByteBuffer elementBuffer = ((TriIndexSequence) gltfMesh.indexSequence()).getBinary();
                    int indexCount = elementBuffer.remaining() / 2;
                    for (int i = 0; i < indexCount; i++) {
                        int vIndex = elementBuffer.getShort(i * 2) & 0xFFFF;
                        v.write(wrapper, vIndex, i);
                    }

                }
            });
        }

        // 3. Recurse into children
        for (int i = 0; i < node.childCount(); i++) {
            poseStack.pushPose();
            poseStack.mulPose(new Quaternionf(0.1, 0, 0, 1));
            renderModelRecursive(node.child(i), poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
        }

        poseStack.popPose();
    }
}