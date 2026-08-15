package proto.mechanicalarmory.client.flywheel.instances.belt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
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
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.belt.network.BeltSubnetwork;
import proto.mechanicalarmory.common.blocks.BlockBelt;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;

/**
 * Flywheel EffectVisual representing an entire connected conveyor belt subnetwork.
 *
 * <p>Instead of individual 1-block visualizers, this unified effect manages all
 * items across all nodes in the subnetwork in topological order, guaranteeing
 * deterministic simulation and buttery smooth sub-tick rendering without race
 * conditions or boundary pauses.
 */
public class BeltSubnetworkVisual extends AbstractVisual
        implements EffectVisual<BeltSubnetwork>, DynamicVisual, LightUpdatedVisual {

    /** Shared item model cache keyed by ItemStack identity+components. */
    private static final Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> MODEL_CACHE =
            new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());

    private final BeltSubnetwork subnet;

    /** Active Flywheel instances: Node UUID -> [Lane 0 instances, Lane 1 instances]. */
    private final Map<UUID, List<List<TransformedInstance>>> nodeInstances = new HashMap<>();

    /** Last game time simulation was advanced. */
    private long lastTickedGameTime = 0;

    private boolean deleted = false;
    private SectionCollector lightSections;

    public BeltSubnetworkVisual(VisualizationContext ctx, BeltSubnetwork subnet, float partialTick) {
        super(ctx, Minecraft.getInstance().level, partialTick);
        this.subnet = subnet;
        if (this.level != null) {
            this.lastTickedGameTime = this.level.getGameTime();
        }
    }

    // ── DynamicVisual (Frame update) ──────────────────────────────────────────

    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return RunnablePlan.of(ctx -> {
            if (deleted) return;

            // 1. Advance simulation once per game tick across the whole subnetwork
            advanceSimulationIfNeeded();

            // 2. Render all nodes in the subnetwork with sub-tick interpolation
            renderSubnetwork(ctx.partialTick());
        });
    }

    private void advanceSimulationIfNeeded() {
        if (level == null) return;
        long gameTime = level.getGameTime();
        if (lastTickedGameTime == 0) {
            lastTickedGameTime = gameTime;
            return;
        }
        long elapsed = gameTime - lastTickedGameTime;
        if (elapsed <= 0) return;
        lastTickedGameTime = gameTime;

        long ticks = Math.min(elapsed, 20);
        for (int t = 0; t < ticks; t++) {
            // Topological order evaluates downstream nodes first
            for (BeltNode node : subnet.topoOrder()) {
                if (node.isStopped()) continue;

                updateNodeSpeeds(node);

                boolean hasOutput = hasValidOutput(node);
                BeltNode outNode = hasOutput ? getOutputNode(node) : null;
                for (int l = 0; l < 2; l++) {
                    BeltLane lane = node.lane(l);
                    lane.advance(1.0f, hasOutput ? Float.MAX_VALUE : 1.0f);

                    if (hasOutput && outNode != null && !outNode.isStopped()) {
                        lane.transferOut(outNode.lane(l));
                    }
                }
            }
        }
    }

    private boolean hasValidOutput(BeltNode node) {
        if (level == null) return false;
        BlockPos pos = node.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) return false;

        Direction facing = state.getValue(BlockBelt.FACING);
        BlockPos outPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outPos);
        return outState.getBlock() instanceof BlockBelt;
    }

    @Nullable
    private BeltNode getOutputNode(BeltNode node) {
        if (level == null) return null;
        BlockPos pos = node.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) return null;

        Direction facing = state.getValue(BlockBelt.FACING);
        BlockPos outPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outPos);
        if (!(outState.getBlock() instanceof BlockBelt)) return null;

        BeltNode out = subnet.nodeAt(outPos);
        if (out == null) {
            out = proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().getNode(outPos);
        }
        return out;
    }

    private void updateNodeSpeeds(BeltNode node) {
        BlockPos pos = node.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) return;

        Direction facing = state.getValue(BlockBelt.FACING);
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        boolean hasBack = isBeltFacingInto(pos.relative(back), pos);
        boolean hasLeft = isBeltFacingInto(pos.relative(left), pos);
        boolean hasRight = isBeltFacingInto(pos.relative(right), pos);

        if (!hasBack) {
            if (hasRight && !hasLeft) {
                // CURVE_RIGHT: inner is lane 1, outer is lane 0
                node.lane(1).setSpeed(BeltLane.SPEED_INNER);
                node.lane(0).setSpeed(BeltLane.SPEED_OUTER);
                return;
            } else if (hasLeft && !hasRight) {
                // CURVE_LEFT: inner is lane 0, outer is lane 1
                node.lane(0).setSpeed(BeltLane.SPEED_INNER);
                node.lane(1).setSpeed(BeltLane.SPEED_OUTER);
                return;
            }
        }
        node.lane(0).setSpeed(BeltLane.SPEED_DEFAULT);
        node.lane(1).setSpeed(BeltLane.SPEED_DEFAULT);
    }

    private boolean isBeltFacingInto(BlockPos fromPos, BlockPos toPos) {
        BlockState state = level.getBlockState(fromPos);
        if (state.getBlock() instanceof BlockBelt) {
            Direction f = state.getValue(BlockBelt.FACING);
            return fromPos.relative(f).equals(toPos);
        }
        return false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderSubnetwork(float partialTick) {
        Set<UUID> liveNodeIds = new HashSet<>();

        for (BeltNode node : subnet.allNodes()) {
            liveNodeIds.add(node.nodeId());
            renderNode(node, partialTick);
        }

        // Clean up removed nodes
        Iterator<Map.Entry<UUID, List<List<TransformedInstance>>>> it = nodeInstances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<List<TransformedInstance>>> entry = it.next();
            if (!liveNodeIds.contains(entry.getKey())) {
                for (List<TransformedInstance> laneList : entry.getValue()) {
                    for (TransformedInstance inst : laneList) inst.delete();
                    laneList.clear();
                }
                it.remove();
            }
        }
    }

    private void renderNode(BeltNode node, float partialTick) {
        BlockPos pos = node.pos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockBelt)) {
            List<List<TransformedInstance>> lanes = nodeInstances.remove(node.nodeId());
            if (lanes != null) {
                for (List<TransformedInstance> list : lanes) {
                    list.forEach(Instance::delete);
                    list.clear();
                }
            }
            return;
        }

        Direction facing = state.getValue(BlockBelt.FACING);
        updateNodeSpeeds(node);
        CurveType curve = getCurveType(pos, facing);
        int packedLight = LevelRenderer.getLightColor(level, pos);

        List<List<TransformedInstance>> lanes = nodeInstances.computeIfAbsent(node.nodeId(), k -> {
            List<List<TransformedInstance>> list = new ArrayList<>(2);
            list.add(new ArrayList<>());
            list.add(new ArrayList<>());
            return list;
        });

        for (int l = 0; l < 2; l++) {
            renderLane(node, l, facing, curve, lanes.get(l), packedLight, partialTick);
        }
    }

    private void renderLane(BeltNode node, int laneIdx, Direction facing, CurveType curve,
                            List<TransformedInstance> instances, int packedLight, float partialTick) {
        BeltLane lane = node.lane(laneIdx);
        if (lane.isEmpty()) {
            while (!instances.isEmpty()) {
                instances.remove(instances.size() - 1).delete();
            }
            return;
        }

        // Calculate max forward advance available for the front group
        float maxFrontAdvance = 0.0f;
        if (!node.isStopped()) {
            if (hasValidOutput(node)) {
                BeltNode outNode = getOutputNode(node);
                if (outNode != null && !outNode.isStopped()) {
                    BeltLane nextLane = outNode.lane(laneIdx);
                    float nextRoom = nextLane.isEmpty()
                            ? Float.MAX_VALUE
                            : nextLane.peekLast().tailPos(nextLane.itemSpacing());
                    if (nextRoom > 0.0f) {
                        maxFrontAdvance = lane.speed();
                    } else {
                        maxFrontAdvance = Math.max(0.0f, 1.0f - lane.peekFirst().headPos());
                    }
                } else if (!lane.isEmpty()) {
                    maxFrontAdvance = Math.max(0.0f, 1.0f - lane.peekFirst().headPos());
                }
            } else if (!lane.isEmpty()) {
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

            float groupRenderHead = group.headPos() + (node.isStopped() ? 0.0f : advanceForThisGroup);
            prevTail = groupRenderHead - group.count() * spacing;

            for (int i = 0; i < group.count(); i++) {
                float renderPos = groupRenderHead - i * spacing;
                float[] pos3d = getRenderWorldPos(node.pos(), facing, renderPos, laneIdx, curve);

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

        while (instances.size() > idx) {
            instances.remove(instances.size() - 1).delete();
        }
    }

    private float[] getRenderWorldPos(BlockPos pos, Direction facing, float renderPos, int laneIdx, CurveType curve) {
        float cx = pos.getX() + 0.5f;
        float cy = pos.getY() + 0.1f;
        float cz = pos.getZ() + 0.5f;
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
        BlockPos nextPos = pos.relative(facing);
        BlockState nextState = level.getBlockState(nextPos);
        if (nextState.getBlock() instanceof BlockBelt) {
            Direction nextFacing = nextState.getValue(BlockBelt.FACING);
            float nextCx = nextPos.getX() + 0.5f;
            float nextCz = nextPos.getZ() + 0.5f;

            if (nextFacing == facing) {
                // Straight continuation
                float wx = nextCx + nextFacing.getStepX() * (excess - 0.5f) + laneOffset * nextFacing.getClockWise().getStepX();
                float wz = nextCz + nextFacing.getStepZ() * (excess - 0.5f) + laneOffset * nextFacing.getClockWise().getStepZ();
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

                double theta = Math.min(1.0, excess) * (Math.PI / 2.0);
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

    private enum CurveType {
        STRAIGHT,
        CURVE_LEFT,
        CURVE_RIGHT
    }

    private CurveType getCurveType(BlockPos pos, Direction facing) {
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

    private CapturedModel getOrCaptureModel(ItemStack item) {
        CapturedModel cached = MODEL_CACHE.get(item);
        if (cached != null) return cached;

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
        if (level == null) return;
        for (Map.Entry<UUID, List<List<TransformedInstance>>> entry : nodeInstances.entrySet()) {
            BeltNode node = subnet.node(entry.getKey());
            if (node == null) continue;
            int light = LevelRenderer.getLightColor(level, node.pos());
            for (List<TransformedInstance> laneList : entry.getValue()) {
                for (TransformedInstance inst : laneList) {
                    inst.light(light).setChanged();
                }
            }
        }
    }

    @Override
    public void setSectionCollector(SectionCollector collector) {
        this.lightSections = collector;
        if (lightSections != null) {
            LongSet set = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            for (BeltNode node : subnet.allNodes()) {
                set.add(SectionPos.asLong(node.pos()));
            }
            lightSections.sections(set);
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void _delete() {
        this.deleted = true;
        proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().onVisualDeleted(subnet.subnetId());
        for (List<List<TransformedInstance>> lanes : nodeInstances.values()) {
            for (List<TransformedInstance> list : lanes) {
                list.forEach(Instance::delete);
                list.clear();
            }
        }
        nodeInstances.clear();
    }
}
