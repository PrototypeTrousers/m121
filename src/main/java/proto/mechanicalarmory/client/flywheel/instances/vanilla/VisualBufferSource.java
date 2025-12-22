package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.Material;
import proto.mechanicalarmory.client.mixin.BufferSourceAccessor;

public class VisualBufferSource extends MultiBufferSource.BufferSource {
    VanillaBlockEntityVisual visual;
    Object2ObjectArrayMap<VertexConsumer, Material> bufferMaterialMap = new Object2ObjectArrayMap<>();
    boolean rendered;

    public VisualBufferSource(VanillaBlockEntityVisual visual) {
        super(null, ((BufferSourceAccessor) Minecraft.getInstance().renderBuffers().bufferSource()).getFixedBuffers());
        this.visual = visual;
    }

    public VanillaBlockEntityVisual getVisual() {
        return visual;
    }

    public void setRendered(boolean rendered) {
        this.rendered = rendered;
    }

    public boolean isRendered() {
        return rendered;
    }

    public Object2ObjectArrayMap<VertexConsumer, Material> getMaterialMap() {
        return bufferMaterialMap;
    }
}
