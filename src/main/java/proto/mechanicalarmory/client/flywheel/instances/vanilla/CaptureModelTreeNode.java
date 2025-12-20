package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntIntMutablePair;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.client.renderer.RenderType;
import proto.mechanicalarmory.client.mixin.BufferBuilderAccessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CaptureModelTreeNode {
    CaptureModelTreeNode parent = null;
    Map<PoseStack.Pose, CaptureModelTreeNode> children = new Object2ObjectArrayMap<>();
    Map<RenderType, List<IntIntMutablePair>> renderIntervalMap = new Object2ObjectArrayMap<>();

    public CaptureModelTreeNode() {
    }

    public CaptureModelTreeNode(CaptureModelTreeNode parentNode) {
        this.parent = parentNode;
    }

    public void recordBufferIndex(Map<RenderType, BufferBuilder> startedBuildersMap) {
        startedBuildersMap.forEach((type, builder) -> {
            renderIntervalMap.computeIfAbsent(type, k -> new ArrayList<>());
            renderIntervalMap.get(type).add(IntIntMutablePair.of(
                    ((BufferBuilderAccessor) builder).getVertices(),
                    ((BufferBuilderAccessor) builder).getVertices()));
        });
    }
}
