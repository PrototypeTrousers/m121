package proto.mechanicalarmory.client.renderer.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.vertex.MutableVertexList;
import org.joml.Vector3f;

public class VertexConsumerMutableWrapper implements MutableVertexList {
    private VertexConsumer consumer;
    private int vertexCount;

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

    public VertexConsumerMutableWrapper() {

    }

    public void setPoseStack(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public void setLight(int light) {
        this.light = light;
    }

    public void setOverlay(int overlay) {
        this.overlay = overlay;
    }

    public void setConsumer(VertexConsumer consumer) {
        this.consumer = consumer;
    }

    public void setVertexCount(int vertexCount) {
        this.vertexCount = vertexCount;
    }

    @Override
    public void x(int index, float x) {
        this.x = x;
    }

    @Override
    public void y(int index, float y) {
        this.y = y;
    }

    @Override
    public void z(int index, float z) {
        this.z = z;
    }

    @Override
    public void r(int index, float r) {
        this.r = r;
    }

    @Override
    public void g(int index, float g) {
        this.g = g;
    }

    @Override
    public void b(int index, float b) {
        this.b = b;
    }

    @Override
    public void a(int index, float a) {
        this.a = a;
    }

    @Override
    public void u(int index, float u) {
        this.u = u;
    }

    @Override
    public void v(int index, float v) {
        this.v = v;
    }

    @Override
    public void overlay(int index, int overlay) {
    }

    @Override
    public void light(int index, int light) {
    }

    @Override
    public void normalX(int index, float normalX) {
        this.nx = normalX;
    }

    @Override
    public void normalY(int index, float normalY) {
        this.ny = normalY;
    }

    @Override
    public void normalZ(int index, float normalZ) {
        this.nz = normalZ;

        // 1. Transform Position and Normal as you already do
        Vector3f transformPosition = poseStack.last().pose().transformPosition(x, y, z, new Vector3f());
        Vector3f transformNormal = poseStack.last().transformNormal(nx, ny, nz, new Vector3f());

        consumer.addVertex(transformPosition.x, transformPosition.y, transformPosition.z)
                .setColor(r, g, b, a) // Multiplied by our custom diffusion
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

    @Override
    public float x(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float y(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float z(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float r(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float g(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float b(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float a(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float u(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float v(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int overlay(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int light(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float normalX(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float normalY(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public float normalZ(int index) {
        throw new UnsupportedOperationException();
    }
}
