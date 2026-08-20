package proto.mechanicalarmory.client.flywheel.instances.belt;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractVisual;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.joml.Matrix4f;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.belt.network.BeltGraphHelper.CurveType;
import proto.mechanicalarmory.common.belt.network.BeltSimulation;
import proto.mechanicalarmory.common.belt.network.BeltSubnetwork;

import java.util.*;

/**
 * Flywheel EffectVisual representing an entire connected conveyor belt subnetwork.
 *
 * <p>Manages all item visual instances across all nodes in the subnetwork in topological order,
 * providing deterministic client prediction and smooth sub-tick rendering.
 */
public class BeltSubnetworkVisual extends AbstractVisual
        implements EffectVisual<BeltSubnetwork>, DynamicVisual, TickableVisual, LightUpdatedVisual {

    private final BeltSubnetwork subnet;

    /** Active Flywheel instances: Node BlockPos -> [Lane 0 instances, Lane 1 instances]. */
    private final Map<BlockPos, List<List<TransformedInstance>>> nodeInstances = new HashMap<>();

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

    public static void clearModelCache() {
        BeltItemModelCapture.clearModelCache();
    }

    // ── DynamicVisual (Frame update) ──────────────────────────────────────────

    @Override
    public Plan<DynamicVisual.Context> planFrame() {
        return RunnablePlan.of(ctx -> {
            if (deleted) return;
            renderSubnetwork(ctx.partialTick());
        });
    }

    @Override
    public Plan<TickableVisual.Context> planTick() {
        return RunnablePlan.of(ctx -> {
            if (deleted) return;
            advanceSimulationIfNeeded();
        });
    }

    private void advanceSimulationIfNeeded() {
        if (level == null) return;
        long gameTime = level.getGameTime();
        if (lastTickedGameTime == 0) {
            lastTickedGameTime = gameTime;
            return;
        }

        long ticksToAdvance = gameTime - lastTickedGameTime;
        if (ticksToAdvance > 0) {
            int ticks = (int) Math.min(ticksToAdvance, 10);
            for (int t = 0; t < ticks; t++) {
                BeltSimulation.tickSubnetwork(subnet);
            }
            lastTickedGameTime = gameTime;
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderSubnetwork(float partialTick) {
        if (level == null) return;

        Set<BlockPos> currentNodes = new HashSet<>();
        for (BeltNode node : subnet.allNodes()) {
            currentNodes.add(node.pos());
            renderNode(node, partialTick);
        }

        // Clean up visual instances for nodes that were removed from this subnetwork
        Iterator<Map.Entry<BlockPos, List<List<TransformedInstance>>>> it = nodeInstances.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, List<List<TransformedInstance>>> entry = it.next();
            if (!currentNodes.contains(entry.getKey())) {
                for (List<TransformedInstance> laneList : entry.getValue()) {
                    laneList.forEach(Instance::delete);
                }
                it.remove();
            }
        }
    }

    private void renderNode(BeltNode node, float partialTick) {
        BlockPos pos = node.pos();
        Direction facing = node.facing();
        CurveType curve = BeltCurveGeometry.getCurveType(pos, facing, level, subnet);
        int packedLight = LevelRenderer.getLightColor(level, pos);

        List<List<TransformedInstance>> lanes = nodeInstances.computeIfAbsent(pos, p -> {
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

        int destLaneIdx = laneIdx;
        if (node.outputPos() != null) {
            BeltNode outNode = subnet.node(node.outputPos());
            if (outNode != null) {
                destLaneIdx = BeltSimulation.getDestinationLane(node, outNode, laneIdx);
            }
        }

        // Compute smooth advance delta for this lane (clamped to physical room ahead)
        float laneAdvance = 0.0f;
        if (!node.isStopped()) {
            float maxExitPos = 1.0f;
            boolean hasOutput = node.outputPos() != null && subnet.node(node.outputPos()) != null
                    && !subnet.node(node.outputPos()).isStopped();
            if (hasOutput) {
                BeltNode outNode = subnet.node(node.outputPos());
                BeltLane outLane = outNode.lane(destLaneIdx);
                if (outLane.isEmpty()) {
                    maxExitPos = Float.MAX_VALUE;
                } else {
                    float outRoom = outLane.peekLast().tailPos(outLane.spacing());
                    maxExitPos = 1.0f + outRoom * (lane.spacing() / outLane.spacing());
                }
            }

            float frontHead = lane.peekFirst().headPos();
            float maxAvailableDelta = Math.max(0.0f, maxExitPos - frontHead);
            float actualSpeed = Math.min(lane.speed(), maxAvailableDelta);
            laneAdvance = actualSpeed * partialTick;
        }

        float spacing = lane.spacing();
        int idx = 0;
        final ItemGroup[] laneArr = lane.groupArray();
        final int laneSize = lane.groupCount();

        float prevTail = Float.MAX_VALUE;
        for (int gi = 0; gi < laneSize; gi++) {
            ItemGroup group = laneArr[gi];
            CapturedModel model = BeltItemModelCapture.getOrCaptureModel(group.item(), level, deleted);
            if (model == null) continue;

            float groupAdvance;
            if (gi == 0) {
                groupAdvance = laneAdvance;
            } else {
                float roomToPrev = Math.max(0.0f, prevTail - group.headPos());
                groupAdvance = Math.min(laneAdvance, roomToPrev);
            }
            float groupRenderHead = group.headPos() + groupAdvance;
            prevTail = groupRenderHead - group.count() * spacing;

            for (int i = 0; i < group.count(); i++) {
                float renderPos = groupRenderHead - i * spacing;
                float[] pos3d = BeltCurveGeometry.getRenderWorldPos(node.pos(), facing, renderPos, laneIdx, destLaneIdx, curve, level);

                if (idx >= instances.size()) {
                    instances.add(instancerProvider()
                            .instancer(InstanceTypes.TRANSFORMED, model)
                            .createInstance());
                }

                TransformedInstance inst = instances.get(idx++);
                inst.setTransform(new Matrix4f().translate(pos3d[0], pos3d[1], pos3d[2]).scale(0.5f))
                        .light(packedLight)
                        .setChanged();
            }
        }

        while (instances.size() > idx) {
            instances.remove(instances.size() - 1).delete();
        }
    }

    // ── LightUpdatedVisual ────────────────────────────────────────────────────

    @Override
    public void updateLight(float partialTick) {
        if (level == null) return;
        for (Map.Entry<BlockPos, List<List<TransformedInstance>>> entry : nodeInstances.entrySet()) {
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
            LongSet set = new LongOpenHashSet();
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
        ClientBeltNetwork.get().onVisualDeleted(subnet.subnetId());
        for (List<List<TransformedInstance>> lanes : nodeInstances.values()) {
            for (List<TransformedInstance> list : lanes) {
                list.forEach(Instance::delete);
                list.clear();
            }
        }
        nodeInstances.clear();
    }
}
