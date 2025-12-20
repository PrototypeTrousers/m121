package proto.mechanicalarmory.client.flywheel.instances.vanilla;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import proto.mechanicalarmory.client.mixin.BufferSourceAccessor;

import java.util.Map;

public class CapturedModelTreeBuilder {
    Map<RenderType, BufferBuilder> startedBuildersMap;
    CaptureModelTreeNode root = new CaptureModelTreeNode();
    CaptureModelTreeNode currentNode = root;

    public CapturedModelTreeBuilder(MultiBufferSource.BufferSource bufferSource) {
        this.startedBuildersMap = ((BufferSourceAccessor) bufferSource).getStartedBuilders();
    }

    public void addNode(PoseStack.Pose pose) {
        CaptureModelTreeNode newNode = new CaptureModelTreeNode(currentNode);
        currentNode.children.put(pose, newNode);
    }

    public CaptureModelTreeNode getCurrentNode() {
        return currentNode;
    }

    public void recordBufferIndex() {
        currentNode.recordBufferIndex(startedBuildersMap);
    }
}
