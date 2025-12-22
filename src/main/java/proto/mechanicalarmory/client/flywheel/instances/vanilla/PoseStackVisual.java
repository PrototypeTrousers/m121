package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;

public class PoseStackVisual extends RecyclingPoseStack {

    private final VanillaBlockEntityVisual visual;
    private int depth;

    public PoseStackVisual(VanillaBlockEntityVisual visual) {
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
        depth++;
    }

    public VanillaBlockEntityVisual getVisual() {
        return visual;
    }

    public int getDepth() {
        return depth;
    }
}
