package proto.mechanicalarmory.client.flywheel.slicer;

public class DummyModelPart {
    public float x, y, z;
    public float xRot, yRot, zRot;
    public float xScale = 1.0f, yScale = 1.0f, zScale = 1.0f;

    public void copyFrom(dev.engine_room.flywheel.lib.model.part.InstanceTree tree) {
        if (tree != null) {
            this.x = tree.xPos();
            this.y = tree.yPos();
            this.z = tree.zPos();
            this.xRot = tree.xRot();
            this.yRot = tree.yRot();
            this.zRot = tree.zRot();
        }
    }
}
