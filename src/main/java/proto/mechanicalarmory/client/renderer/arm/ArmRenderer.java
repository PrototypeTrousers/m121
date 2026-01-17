package proto.mechanicalarmory.client.renderer.arm;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import dev.engine_room.flywheel.api.vertex.VertexList;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.gltf.GltfFlywheelModel;
import proto.mechanicalarmory.client.flywheel.gltf.GltfMesh;
import proto.mechanicalarmory.client.flywheel.gltf.TriIndexSequence;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.nio.ByteBuffer;

import static net.minecraft.client.renderer.RenderStateShard.*;

public class ArmRenderer implements BlockEntityRenderer<ArmEntity> {

    ModelTree modelTree = MechanicalArmoryClient.gltfFlywheelModelTree;


    public ArmRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ArmEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        renderModelRecursive(modelTree, blockEntity, poseStack, bufferSource, packedLight, packedOverlay);
    }

    public void renderModelRecursive(ModelTree node, ArmEntity blockEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        final int packedLight2 = LevelRenderer.getLightColor(blockEntity.getLevel(), blockEntity.getBlockPos().above());
        int six = 21;

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

                    RenderType r = RenderType.create(
                            "arm",
                            DefaultVertexFormat.BLOCK,
                            VertexFormat.Mode.TRIANGLES,
                            512,
                            true,
                            false,
                            RenderType.CompositeState.builder()
                                    .setLightmapState(LIGHTMAP)
                                    .setShaderState(RENDERTYPE_SOLID_SHADER)
                                    .setTextureState(new TextureStateShard(configuredMesh.material().texture(), false, false))
                                    .createCompositeState(true));

                    VertexConsumer consumer = bufferSource.getBuffer(r);
                    VertexConsumerMutableWrapper wrapper = new VertexConsumerMutableWrapper(
                            poseStack, consumer, gltfMesh.vertexCount(), packedLight2, packedOverlay
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
            renderModelRecursive(node.child(i), blockEntity, poseStack, bufferSource, packedLight2, packedOverlay);
            poseStack.popPose();
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

    public class VertexConsumerMutableWrapper implements MutableVertexList {
        private final VertexConsumer consumer;
        private final int vertexCount;

        // Temporary storage for the "current" vertex attributes
        private float x, y, z;
        private float r, g, b, a;
        private float u, v;
        private int overlay, light;
        private float nx, ny, nz;
        private PoseStack poseStack;

        public VertexConsumerMutableWrapper(PoseStack poseStack, VertexConsumer consumer, int vertexCount, int packedLight, int packedOverlay) {
            this.consumer = consumer;
            this.vertexCount = vertexCount;
            this.poseStack = poseStack;
            this.overlay = packedOverlay;
            this.light = packedLight;
        }

        @Override public void x(int index, float x) { this.x = x; }
        @Override public void y(int index, float y) { this.y = y; }
        @Override public void z(int index, float z) { this.z = z; }

        @Override public void r(int index, float r) { this.r = r; }
        @Override public void g(int index, float g) { this.g = g; }
        @Override public void b(int index, float b) { this.b = b; }
        @Override public void a(int index, float a) { this.a = a; }

        @Override public void u(int index, float u) { this.u = u; }
        @Override public void v(int index, float v) { this.v = v; }

        @Override public void overlay(int index, int overlay) { }
        @Override public void light(int index, int light) { }

        @Override public void normalX(int index, float normalX) { this.nx = normalX; }
        @Override public void normalY(int index, float normalY) { this.ny = normalY; }

        @Override
        public void normalZ(int index, float normalZ) {
            this.nz = normalZ;

            // 1. Transform Position and Normal as you already do
            Vector3f transformPosition = poseStack.last().pose().transformPosition(x, y, z, new Vector3f());
            Vector3f transformNormal = poseStack.last().transformNormal(nx, ny, nz, new Vector3f());

            // 2. Define Light Directions
            Vector3f lightWest = new Vector3f(0.2F, 1.0F, -0.7F);
            Vector3f lightEast = new Vector3f(-0.2F, 1.0F, 0.7F);

            // 3. Calculate Diffusion (Dot Product)
            // We use max(0, dot) to ensure surfaces facing away from the light are dark
            float dotWest = Math.max(0.0f, transformNormal.dot(lightWest));
            float dotEast = Math.max(0.0f, transformNormal.dot(lightEast));

            // 4. Combine and apply to color
            // You can adjust '0.5f' to change the intensity of these specific lights
            float diffuse = (dotWest + dotEast) * 0.6f;

            // Ambient light: prevent the model from being pitch black in shadows
            float ambient = 0.4f;
            float totalLight = Math.min(1.0f, diffuse + ambient);

            // Apply the light to the existing vertex colors (r, g, b)
            float finalR = r * totalLight;
            float finalG = g * totalLight;
            float finalB = b * totalLight;

            consumer.addVertex(transformPosition.x, transformPosition.y, transformPosition.z)
                    .setColor(finalR, finalG, finalB, a) // Multiplied by our custom diffusion
                    .setUv(u, v)
                    .setOverlay(overlay)
                    .setLight(light) // Keep the environment's packedLight for vanilla lightmap compatibility
                    .setNormal(transformNormal.x, transformNormal.y, transformNormal.z);
        }

        @Override
        public int vertexCount() {
            return vertexCount;
        }

        // --- VertexList Getters ---
        // Since VertexConsumer is write-only, we cannot retrieve data.
        // Throwing an exception is safer than returning 0, as it alerts you
        // if a function tries to read-modify-write.

        @Override public float x(int index) { throw new UnsupportedOperationException(); }
        @Override public float y(int index) { throw new UnsupportedOperationException(); }
        @Override public float z(int index) { throw new UnsupportedOperationException(); }
        @Override public float r(int index) { throw new UnsupportedOperationException(); }
        @Override public float g(int index) { throw new UnsupportedOperationException(); }
        @Override public float b(int index) { throw new UnsupportedOperationException(); }
        @Override public float a(int index) { throw new UnsupportedOperationException(); }
        @Override public float u(int index) { throw new UnsupportedOperationException(); }
        @Override public float v(int index) { throw new UnsupportedOperationException(); }
        @Override public int overlay(int index) { throw new UnsupportedOperationException(); }
        @Override public int light(int index) { throw new UnsupportedOperationException(); }
        @Override public float normalX(int index) { throw new UnsupportedOperationException(); }
        @Override public float normalY(int index) { throw new UnsupportedOperationException(); }
        @Override public float normalZ(int index) { throw new UnsupportedOperationException(); }
    }

    private record xx (Material material, RenderType renderType) {}

}
