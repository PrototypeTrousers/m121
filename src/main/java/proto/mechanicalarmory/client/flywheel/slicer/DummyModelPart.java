package proto.mechanicalarmory.client.flywheel.slicer;

public class DummyModelPart {
    public float x, y, z;
    public float xRot, yRot, zRot;
    public float xScale = 1.0f, yScale = 1.0f, zScale = 1.0f;
    public boolean visible = true;
    public boolean skipDraw = false;

    private float initialX, initialY, initialZ;
    private float initialXRot, initialYRot, initialZRot;
    private float initialXScale = 1.0f, initialYScale = 1.0f, initialZScale = 1.0f;

    public void copyFrom(dev.engine_room.flywheel.lib.model.part.InstanceTree tree) {
        if (tree != null) {
            this.x = this.initialX = tree.xPos();
            this.y = this.initialY = tree.yPos();
            this.z = this.initialZ = tree.zPos();
            this.xRot = this.initialXRot = tree.xRot();
            this.yRot = this.initialYRot = tree.yRot();
            this.zRot = this.initialZRot = tree.zRot();
            this.xScale = this.initialXScale = tree.xScale();
            this.yScale = this.initialYScale = tree.yScale();
            this.zScale = this.initialZScale = tree.zScale();
            this.visible = tree.visible();
            this.skipDraw = tree.skipDraw();
        }
    }

    public void resetPose() {
        this.x = this.initialX;
        this.y = this.initialY;
        this.z = this.initialZ;
        this.xRot = this.initialXRot;
        this.yRot = this.initialYRot;
        this.zRot = this.initialZRot;
        this.xScale = this.initialXScale;
        this.yScale = this.initialYScale;
        this.zScale = this.initialZScale;
        this.visible = true;
        this.skipDraw = false;
    }

    public void copyFrom(DummyModelPart other) {
        if (other != null) {
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
            this.xRot = other.xRot;
            this.yRot = other.yRot;
            this.zRot = other.zRot;
            this.xScale = other.xScale;
            this.yScale = other.yScale;
            this.zScale = other.zScale;
            this.visible = other.visible;
            this.skipDraw = other.skipDraw;
        }
    }

    public void copyFrom(net.minecraft.client.model.geom.ModelPart other) {
        if (other != null) {
            this.x = other.x;
            this.y = other.y;
            this.z = other.z;
            this.xRot = other.xRot;
            this.yRot = other.yRot;
            this.zRot = other.zRot;
            this.xScale = other.xScale;
            this.yScale = other.yScale;
            this.zScale = other.zScale;
            this.visible = other.visible;
            this.skipDraw = other.skipDraw;
        }
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void setRotation(float xRot, float yRot, float zRot) {
        this.xRot = xRot;
        this.yRot = yRot;
        this.zRot = zRot;
    }

    public void setScale(float xScale, float yScale, float zScale) {
        this.xScale = xScale;
        this.yScale = yScale;
        this.zScale = zScale;
    }

    public void offsetPos(org.joml.Vector3f offset) {
        this.x += offset.x();
        this.y += offset.y();
        this.z += offset.z();
    }

    public void offsetRotation(org.joml.Vector3f offset) {
        this.xRot += offset.x();
        this.yRot += offset.y();
        this.zRot += offset.z();
    }

    public void offsetScale(org.joml.Vector3f offset) {
        this.xScale += offset.x();
        this.yScale += offset.y();
        this.zScale += offset.z();
    }

    public void rotate(org.joml.Vector3f offset) {
        this.xRot += offset.x();
        this.yRot += offset.y();
        this.zRot += offset.z();
    }

    public net.minecraft.client.model.geom.PartPose storePose() {
        return net.minecraft.client.model.geom.PartPose.offsetAndRotation(this.x, this.y, this.z, this.xRot, this.yRot, this.zRot);
    }

    public void loadPose(net.minecraft.client.model.geom.PartPose pose) {
        if (pose != null) {
            this.x = pose.x;
            this.y = pose.y;
            this.z = pose.z;
            this.xRot = pose.xRot;
            this.yRot = pose.yRot;
            this.zRot = pose.zRot;
            this.xScale = 1.0f;
            this.yScale = 1.0f;
            this.zScale = 1.0f;
        }
    }

    public boolean isEmpty() {
        return false;
    }
}
