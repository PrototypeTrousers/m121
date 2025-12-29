package proto.mechanicalarmory.client.flywheel.instances.vanilla;


import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.internal.FlwLibLink;
import dev.engine_room.flywheel.lib.util.RecyclingPoseStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PoseStackVisual extends PoseStack {

    private final SinkBufferSourceVisual visual;
    private int depth;
    private boolean rendered;
    private final Deque<Pose> recycleBin = new ArrayDeque<>();
    private final Deque<Pose> stash = new ArrayDeque<>();


    public PoseStackVisual(SinkBufferSourceVisual visual) {
        super();
        this.visual = visual;
    }

    @Override
    public void pushPose() {
        if (depth == 0) {
            recycleBin.addAll(stash);
            stash.clear();
        }
        if (recycleBin.isEmpty()) {
            super.pushPose();
        } else {
            var last = last();
            var recycle = recycleBin.removeLast();
            recycle.pose()
                    .set(last.pose());
            recycle.normal()
                    .set(last.normal());
            FlwLibLink.INSTANCE.getPoseStack(this)
                    .addLast(recycle);
        }
        depth++;
    }

    @Override
    public void popPose() {
        stash.addLast(FlwLibLink.INSTANCE.getPoseStack(this)
                .removeLast());
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
