package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;

public class PoseStackVisual extends RecyclingPoseStack {

    private final VanillaBlockEntityVisual visual;
    private int depth;
    private boolean rendered;

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
    }

    public boolean isRendered() {
        return rendered;
    }

    public void setRendered() {
        this.rendered = true;
    }

    public VanillaBlockEntityVisual getVisual() {
        return visual;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
