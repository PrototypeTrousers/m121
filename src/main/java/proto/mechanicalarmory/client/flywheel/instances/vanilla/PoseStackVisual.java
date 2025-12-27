package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;

public class PoseStackVisual extends RecyclingPoseStack {

    private final SinkBufferSourceVisual visual;
    private int depth;
    private boolean rendered;

    public PoseStackVisual(SinkBufferSourceVisual visual) {
        super();
        this.visual = visual;
    }

    @Override
    public void pushPose() {
        super.pushPose();
        depth++;
    }

    @Override
    public void popPose() {
        super.popPose();
    }

    public boolean isRendered() {
        return rendered;
    }

    public void setRendered() {
        this.rendered = true;
    }

    public SinkBufferSourceVisual getVisual() {
        return visual;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
