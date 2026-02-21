package proto.mechanicalarmory.client.ui.owo.component;

import com.mojang.math.Axis;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WorldSceneComponent extends BaseComponent {
    protected BlockPos center;
    protected float rotX;
    protected float rotY;
    protected float zoom;

    public WorldSceneComponent(BlockPos center, float rotX, float rotY, float zoom) {
        this.center = center;
        this.rotX = rotX;
        this.rotY = rotY;
        this.zoom = zoom;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        var client = Minecraft.getInstance();
        if (client.level == null) return;

        var poseStack = context.pose();
        var bufferSource = client.renderBuffers().bufferSource();

        poseStack.pushPose();

        // 1. Center in component space
        poseStack.translate(x + width / 2f, y + height / 2f, 100);

        // 2. Apply Scale/Zoom
        float scaleValue = 20f * zoom;
        poseStack.scale(scaleValue, -scaleValue, scaleValue);

        // 3. Apply Rotations
        poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        poseStack.mulPose(Axis.YP.rotationDegrees(rotY - 180));

        // 4. Render Blocks
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            poseStack.pushPose();
            poseStack.translate(pos.getX() - center.getX() - 0.5f, 
                               pos.getY() - center.getY() - 0.5f, 
                               pos.getZ() - center.getZ() - 0.5f);

            BlockState bs = client.level.getBlockState(pos);
            if (bs.isAir()) {
                poseStack.popPose();
                continue;
            }

            if (bs.getRenderShape() == RenderShape.ENTITYBLOCK_ANIMATED) {
                BlockEntity be = client.level.getBlockEntity(pos);
                if (be != null) {
                    client.getBlockEntityRenderDispatcher().render(be, partialTicks, poseStack, bufferSource);
                }
            } else {
                client.getBlockRenderer().renderSingleBlock(bs, poseStack, bufferSource, 0xF000F0, OverlayTexture.NO_OVERLAY);
            }
            poseStack.popPose();
        }

        bufferSource.endBatch();
        poseStack.popPose();
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        if (button == 0) { // Left click drag to rotate
            this.rotY += deltaX;
            this.rotX += deltaY;
            return true;
        }
        return super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        this.zoom += amount * 0.1f;
        this.zoom = Math.max(0.1f, this.zoom); // Prevent inverted zoom
        return true;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }
}