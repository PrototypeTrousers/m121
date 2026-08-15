package proto.mechanicalarmory.client.flywheel.instances.belt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.arm.ItemStackHasher;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.blocks.BlockBelt;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

import java.util.*;
import java.util.function.Consumer;

/**
 * Flywheel visual for a belt block.
 *
 * <p>The visual maintains its own deep-copied {@link BeltLane}s that advance
 * every frame (via {@link #planFrame()}) at sub-tick resolution.  No server
 * sync is needed between corrections — the simulation is autonomous.
 *
 * <p>Items are expanded from groups into per-item {@link TransformedInstance}s
 * every frame.  Instances are pooled and reused; excess instances are deleted
 * when the item count drops.
 */
public class BeltVisual extends AbstractBlockEntityVisual<BeltEntity>
        implements DynamicVisual, LightUpdatedVisual {

    /** Shared item model cache keyed by ItemStack identity+components. */
    private static final Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> MODEL_CACHE =
            new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());

    // ── Rendering ─────────────────────────────────────────────────────────────

    /** Active instances per lane.  Resized lazily. */
    private final List<List<TransformedInstance>> laneInstances = new ArrayList<>(2);

    /** Belt facing direction — needed to orient items correctly. */
    private Direction facing = Direction.NORTH;

    int packedLight;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltVisual(VisualizationContext ctx, BeltEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        laneInstances.add(new ArrayList<>());
        laneInstances.add(new ArrayList<>());

        packedLight = LevelRenderer.getLightColor(level, pos);

        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BlockBelt.FACING)) {
            facing = state.getValue(BlockBelt.FACING);
        }
    }

    // ── DynamicVisual ─────────────────────────────────────────────────────────

    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return RunnablePlan.of(ctx -> {
            if (!isVisible(ctx.frustum())) return;

            BeltEntity be = blockEntity;
            be.advanceClientSimulation(level, facing);

            // Render items in both lanes
            for (int l = 0; l < 2; l++) {
                renderLane(l, ctx.partialTick());
            }
        });
    }

    // ── Curve trajectory calculation ─────────────────────────────────────────

    private enum CurveType {
        STRAIGHT,
        CURVE_LEFT,
        CURVE_RIGHT
    }

    private CurveType getCurveType() {
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean hasBack = isBeltFacingInto(pos.relative(back), pos);
        boolean hasLeft = isBeltFacingInto(pos.relative(left), pos);
        boolean hasRight = isBeltFacingInto(pos.relative(right), pos);

        if (!hasBack) {
            if (hasRight && !hasLeft) {
                return CurveType.CURVE_RIGHT;
            } else if (hasLeft && !hasRight) {
                return CurveType.CURVE_LEFT;
            }
        }
        return CurveType.STRAIGHT;
    }

    private boolean isBeltFacingInto(net.minecraft.core.BlockPos fromPos, net.minecraft.core.BlockPos toPos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(fromPos);
        if (state.getBlock() instanceof BlockBelt) {
            Direction f = state.getValue(BlockBelt.FACING);
            return fromPos.relative(f).equals(toPos);
        }
        return false;
    }

    // ── Lane rendering ────────────────────────────────────────────────────────

    private void renderLane(int laneIdx, float partialTick) {
        BeltLane lane = blockEntity.clientLane(laneIdx);
        List<TransformedInstance> instances = laneInstances.get(laneIdx);
        CurveType curve = getCurveType();
        if (lane.isEmpty()) {
            while (instances.size() > 0) {
                instances.remove(instances.size() - 1).delete();
            }
            return;
        }

        // Calculate maximum forward advance available for the front group this frame
        float maxFrontAdvance = 0.0f;
        if (!blockEntity.isClientStopped()) {
            if (blockEntity.clientHasOutput()) {
                net.minecraft.world.level.block.entity.BlockEntity next =
                        level.getBlockEntity(pos.relative(facing));
                if (next instanceof BeltEntity nextBe) {
                    BeltLane nextLane = nextBe.clientLane(laneIdx);
                    float nextRoom = nextLane.isEmpty()
                            ? Float.MAX_VALUE
                            : nextLane.peekLast().tailPos(nextLane.itemSpacing());
                    if (nextRoom > 0.001f) {
                        maxFrontAdvance = lane.speed();
                    } else {
                        float maxReachPos = 1.0f + nextRoom * (lane.itemSpacing() / nextLane.itemSpacing());
                        maxFrontAdvance = Math.max(0.0f, Math.min(lane.speed(), maxReachPos - lane.peekFirst().headPos()));
                    }
                } else {
                    maxFrontAdvance = lane.speed();
                }
            } else {
                // Dead end
                maxFrontAdvance = Math.max(0.0f, 1.0f - lane.peekFirst().headPos());
            }
        }

        float spacing = lane.itemSpacing();
        float prevTail = Float.MAX_VALUE;
        boolean isFront = true;

        int idx = 0;
        final proto.mechanicalarmory.common.belt.data.ItemGroup[] laneArr = lane.groupArray();
        final int laneSize = lane.groupCount();
        for (int gi = 0; gi < laneSize; gi++) {
            ItemGroup group = laneArr[gi];
            CapturedModel model = getOrCaptureModel(group.item());
            if (model == null) continue;

            float advanceForThisGroup;
            if (isFront) {
                advanceForThisGroup = Math.min(lane.speed() * partialTick, maxFrontAdvance);
                isFront = false;
            } else {
                float roomToPrev = Math.max(0.0f, prevTail - group.headPos());
                advanceForThisGroup = Math.min(lane.speed() * partialTick, roomToPrev);
            }

            float groupRenderHead = group.headPos() + (blockEntity.isClientStopped() ? 0.0f : advanceForThisGroup);
            prevTail = groupRenderHead - group.count() * spacing;

            for (int i = 0; i < group.count(); i++) {
                float renderPos = groupRenderHead - i * spacing;
                float[] pos3d = getRenderWorldPos(renderPos, laneIdx, curve);

                // Lazily grow the instance list
                if (idx >= instances.size()) {
                    instances.add(instancerProvider()
                            .instancer(InstanceTypes.TRANSFORMED, model)
                            .createInstance());
                }

                TransformedInstance inst = instances.get(idx++);
                inst.setTransform(new Matrix4f().translate(pos3d[0], pos3d[1], pos3d[2]).scale(0.25f))
                        .light(packedLight)
                        .setChanged();
            }
        }

        // Delete excess instances (item count dropped)
        while (instances.size() > idx) {
            instances.remove(instances.size() - 1).delete();
        }
    }

    private float[] getRenderWorldPos(float renderPos, int laneIdx, CurveType curve) {
        float cx = visualPos.getX() + 0.5f;
        float cy = visualPos.getY() + 0.1f;
        float cz = visualPos.getZ() + 0.5f;
        float laneOffset = (laneIdx == 0 ? -0.15f : 0.15f);

        // Within this block
        if (renderPos <= 1.0f) {
            if (curve == CurveType.STRAIGHT) {
                float beltT = Math.max(0.0f, renderPos);
                float wx = cx + facing.getStepX() * (beltT - 0.5f) + laneOffset * facing.getClockWise().getStepX();
                float wz = cz + facing.getStepZ() * (beltT - 0.5f) + laneOffset * facing.getClockWise().getStepZ();
                return new float[]{wx, cy, wz};
            } else {
                Direction sideDir = (curve == CurveType.CURVE_RIGHT)
                        ? facing.getClockWise()
                        : facing.getCounterClockWise();

                float radius = (curve == CurveType.CURVE_RIGHT)
                        ? (0.5f - laneOffset)
                        : (0.5f + laneOffset);

                double theta = Math.max(0.0, renderPos) * (Math.PI / 2.0);
                double sinT = Math.sin(theta);
                double cosT = Math.cos(theta);

                float cornerX = cx + 0.5f * facing.getStepX() + 0.5f * sideDir.getStepX();
                float cornerZ = cz + 0.5f * facing.getStepZ() + 0.5f * sideDir.getStepZ();

                float wx = (float) (cornerX - radius * (cosT * facing.getStepX() + sinT * sideDir.getStepX()));
                float wz = (float) (cornerZ - radius * (cosT * facing.getStepZ() + sinT * sideDir.getStepZ()));
                return new float[]{wx, cy, wz};
            }
        }

        // Exceeded exit boundary (renderPos > 1.0f) — project into connected downstream belt
        float excess = renderPos - 1.0f;
        net.minecraft.core.BlockPos nextPos = pos.relative(facing);
        net.minecraft.world.level.block.state.BlockState nextState = level.getBlockState(nextPos);
        if (nextState.getBlock() instanceof BlockBelt) {
            Direction nextFacing = nextState.getValue(BlockBelt.FACING);
            float nextCx = nextPos.getX() + 0.5f;
            float nextCz = nextPos.getZ() + 0.5f;

            if (nextFacing == facing) {
                // Straight continuation
                float beltT = Math.max(0.0f, excess);
                float wx = nextCx + nextFacing.getStepX() * (beltT - 0.5f) + laneOffset * nextFacing.getClockWise().getStepX();
                float wz = nextCz + nextFacing.getStepZ() * (beltT - 0.5f) + laneOffset * nextFacing.getClockWise().getStepZ();
                return new float[]{wx, cy, wz};
            } else {
                // Next belt is a curve
                Direction entrySide = facing.getOpposite();
                CurveType nextCurve = (nextFacing.getClockWise() == entrySide)
                        ? CurveType.CURVE_RIGHT
                        : CurveType.CURVE_LEFT;

                float nextRadius = (nextCurve == CurveType.CURVE_RIGHT)
                        ? (0.5f - laneOffset)
                        : (0.5f + laneOffset);

                double theta = Math.min(Math.PI / 2.0, excess / nextRadius);
                double sinT = Math.sin(theta);
                double cosT = Math.cos(theta);

                float cornerX = nextCx + 0.5f * nextFacing.getStepX() + 0.5f * entrySide.getStepX();
                float cornerZ = nextCz + 0.5f * nextFacing.getStepZ() + 0.5f * entrySide.getStepZ();

                float wx = (float) (cornerX - nextRadius * (cosT * nextFacing.getStepX() + sinT * entrySide.getStepX()));
                float wz = (float) (cornerZ - nextRadius * (cosT * nextFacing.getStepZ() + sinT * entrySide.getStepZ()));
                return new float[]{wx, cy, wz};
            }
        }

        // Default: straight linear projection
        float wx = cx + facing.getStepX() * (renderPos - 0.5f) + laneOffset * facing.getClockWise().getStepX();
        float wz = cz + facing.getStepZ() * (renderPos - 0.5f) + laneOffset * facing.getClockWise().getStepZ();
        return new float[]{wx, cy, wz};
    }

    private CapturedModel getOrCaptureModel(ItemStack item) {
        CapturedModel cached = MODEL_CACHE.get(item);
        if (cached != null) return cached;

        // Capture asynchronously on the render thread
        RenderSystem.recordRenderCall(() -> {
            if (this.deleted) return;
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            var model = itemRenderer.getModel(item, level, null, 0);
            CapturingBufferSource cbs = new CapturingBufferSource();
            PoseStack pose = new PoseStack();
            itemRenderer.render(item, ItemDisplayContext.FIXED, false, pose, cbs, 0, 0, model);
            cbs.endLastBatch();
            MODEL_CACHE.put(item, new CapturedModel(cbs));
        });
        return null;
    }

    // ── LightUpdatedVisual ────────────────────────────────────────────────────

    @Override
    public void updateLight(float partialTick) {
        packedLight = LevelRenderer.getLightColor(level, pos);
        for (List<TransformedInstance> instances : laneInstances) {
            for (TransformedInstance inst : instances) {
                inst.light(packedLight).setChanged();
            }
        }
    }

    @Override
    public void setSectionCollector(SectionCollector collector) {
        this.lightSections = collector;
        lightSections.sections(LongSet.of(SectionPos.asLong(pos)));
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        for (List<TransformedInstance> instances : laneInstances) {
            instances.forEach(consumer);
        }
    }

    @Override
    protected void _delete() {
        for (List<TransformedInstance> instances : laneInstances) {
            instances.forEach(Instance::delete);
            instances.clear();
        }
    }
}
