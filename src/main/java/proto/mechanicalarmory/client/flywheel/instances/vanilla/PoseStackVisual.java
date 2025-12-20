package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.PoseStack;

public class PoseStackVisual extends PoseStack {

    private final VanillaBlockEntityVisual visual;
    public PoseStackVisual(VanillaBlockEntityVisual visual) {
        super();
        this.visual = visual;
    }

    public VanillaBlockEntityVisual getVisual() {
        return visual;
    }
}
