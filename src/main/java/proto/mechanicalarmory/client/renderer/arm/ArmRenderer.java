package proto.mechanicalarmory.client.renderer.arm;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.vertex.VertexList;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.gltf.GltfMesh;
import proto.mechanicalarmory.client.flywheel.gltf.TriIndexSequence;
import proto.mechanicalarmory.client.renderer.util.VertexConsumerMutableWrapper;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.nio.ByteBuffer;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class ArmRenderer implements BlockEntityRenderer<ArmEntity> {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;
    MyModelTree armTree = MyModelTree.create(modelTree);
    MyModelTree baseMotor = armTree.child("BaseMotor");
    MyModelTree firstArmTree = baseMotor.child("FirstArm");
    MyModelTree secondArmTree = firstArmTree.child("SecondArm");

    VertexConsumerMutableWrapper wrapper = new VertexConsumerMutableWrapper();

    public static RenderType r;

    public ArmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArmEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        packedLight = 15728880;
        firstArmTree.resetPose();
        firstArmTree.xRot(Mth.lerp(partialTick, blockEntity.getAnimationRotation(0)[0], blockEntity.getRotation(0)[0]));
        firstArmTree.yRot(Mth.lerp(partialTick, blockEntity.getAnimationRotation(0)[1], blockEntity.getRotation(0)[1]));

        secondArmTree.xRot(Mth.lerp(partialTick, blockEntity.getAnimationRotation(1)[0], blockEntity.getRotation(1)[0]));

        poseStack.pushPose();
        poseStack.translate(0.5f, 0, 0.5f);
        renderModelRecursive(armTree, blockEntity, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

    }

    public void renderModelRecursive(MyModelTree modelTree, ArmEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ModelTree mt = modelTree.getSource();
        Model model = mt.model();
        poseStack.pushPose();
        modelTree.translateAndRotate(poseStack.last().pose());
        modelTree.rotateNormal(poseStack.last().normal());
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
                                        .setTextureState(new TextureStateShard(configuredMesh.material().texture(), false, false))
                                        .createCompositeState(true));
                    }

                    VertexConsumer consumer = bufferSource.getBuffer(r);
                    wrapper.setConsumer(consumer);
                    wrapper.setPoseStack(poseStack);
                    wrapper.setLight(packedLight);
                    wrapper.setOverlay(packedOverlay);
                    wrapper.setVertexCount(gltfMesh.vertexCount());

                    VertexList v = gltfMesh.getVertexList();
                    ByteBuffer elementBuffer = ((TriIndexSequence) gltfMesh.indexSequence()).getBinary();
                    int indexCount = elementBuffer.remaining() / 2;
                    for (int i = 0; i < indexCount; i++) {
                        int vIndex = elementBuffer.getShort(i * 2) & 0xFFFF;
                        v.write(wrapper, vIndex, i);
                    }
                    wrapper.setConsumer(null);

                }
            });
        }

        // 3. Recurse into children
        for (int i = 0; i < modelTree.childCount(); i++) {
            renderModelRecursive(modelTree.child(i), blockEntity, poseStack, bufferSource, packedLight, packedOverlay);
        }
        poseStack.popPose();

    }

    @Override
    public boolean shouldRender(ArmEntity blockEntity, Vec3 cameraPos) {
        return true;
    }

    @Override
    public boolean shouldRenderOffScreen(ArmEntity blockEntity) {
        return true;
    }
}
