package proto.mechanicalarmory.client.ui.owo.component;

import com.mojang.math.Axis;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WorldSceneComponent extends BaseComponent {
    protected BlockPos center;
    protected float rotX;
    protected float rotY;
    protected float zoom;
    private final Matrix4f lastModelViewMatrix = new Matrix4f();
    private boolean isDragging;
    private BiConsumer<Pair<BlockPos, Direction>, Integer> onBlockClicked = (hit, button) -> {};

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

        lastModelViewMatrix.set(poseStack.last().pose());

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
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        this.zoom += amount * 0.1f;
        this.zoom = Math.max(0.1f, this.zoom); // Prevent inverted zoom
        return true;
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Reset the drag flag when the user first clicks down
            this.isDragging = false;
            return true; // Return true to capture the interaction
        }
        return super.onMouseDown(mouseX, mouseY, button);
    }

    @Override
    public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
        if (button == 0) {
            // The user moved the mouse! Flag this as a drag and rotate the scene
            this.isDragging = true;
            this.rotY += deltaX;
            this.rotX += deltaY;
            return true;
        }
        return super.onMouseDrag(mouseX, mouseY, deltaX, deltaY, button);
    }

    @Override
    public boolean onMouseUp(double mouseX, double mouseY, int button) {
        // If they let go of the left mouse button AND they didn't drag to rotate...
        if (button == 1 && !this.isDragging) {
            Pair<BlockPos, Direction> hit = getTargetedHitResult(mouseX, mouseY);

            if (hit != null) {
                // Fire the callback with the hit data!
                this.onBlockClicked.accept(hit, button);
                return true;
            }
            this.isDragging = false;
        }
        return super.onMouseUp(mouseX, mouseY, button);
    }

    public WorldSceneComponent onBlockClicked(BiConsumer<Pair<BlockPos, Direction>, Integer> callback) {
        this.onBlockClicked = callback;
        return this;
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    public Pair<BlockPos, Direction> getTargetedHitResult(double mouseX, double mouseY) {
        Vec3 localRayStart = getMouseRay(mouseX, mouseY, false);
        Vec3 localRayEnd = getMouseRay(mouseX, mouseY, true);

        Vec3 worldOffset = new Vec3(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        Vec3 worldRayStart = localRayStart.add(worldOffset);
        Vec3 worldRayEnd = localRayEnd.add(worldOffset);

        var client = Minecraft.getInstance();
        if (client.level == null) return null;

        Pair<BlockPos, Direction> closestHit = null;
        double minDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 1, 1))) {
            BlockState state = client.level.getBlockState(pos);
            if (state.isAir()) continue;

            var shape = state.getShape(client.level, pos);
            if (shape.isEmpty()) continue;

            // shape.clip() automatically calculates the hit location and the Direction (block face)
            BlockHitResult hitResult = shape.clip(worldRayStart, worldRayEnd, pos);

            if (hitResult != null) {
                double dist = worldRayStart.distanceToSqr(hitResult.getLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    closestHit = Pair.of(hitResult.getBlockPos().immutable(), hitResult.getDirection());
                }
            }
        }

        return closestHit;
    }

    public Vec3 getMouseRay(double mouseX, double mouseY, boolean far) {
        Matrix4f invMatrix = new Matrix4f(lastModelViewMatrix).invert();

        // Convert OwoUI's relative mouse coordinates into absolute screen coordinates
        // so they perfectly match the absolute translations inside your lastModelViewMatrix
        float absoluteX = (float) (this.x + mouseX);
        float absoluteY = (float) (this.y + mouseY);

        // Near plane is 10000, Far plane is -10000 (Minecraft UI Z goes out of the screen)
        float zDepth = far ? -10000.0f : 10000.0f;

        Vector4f pos = new Vector4f(absoluteX, absoluteY, zDepth, 1.0f);
        invMatrix.transform(pos);

        return new Vec3(pos.x() / pos.w(), pos.y() / pos.w(), pos.z() / pos.w());
    }
}