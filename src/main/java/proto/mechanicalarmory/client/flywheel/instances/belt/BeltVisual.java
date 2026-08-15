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

    // ── Lane rendering ────────────────────────────────────────────────────────

    private void renderLane(int laneIdx, float partialTick) {
        BeltLane lane = blockEntity.clientLane(laneIdx);
        List<TransformedInstance> instances = laneInstances.get(laneIdx);

        int idx = 0;
        for (ItemGroup group : lane.groups()) {
            CapturedModel model = getOrCaptureModel(group.item());
            if (model == null) continue;
            for (int i = 0; i < group.count(); i++) {
                float itemHead = group.headPos() - i * BeltLane.ITEM_SPACING;
                float renderPos = itemHead + (blockEntity.isClientStopped() ? 0f : lane.speed() * partialTick);
                if (!blockEntity.clientHasOutput()) {
                    renderPos = Math.min(renderPos, 1.0f - i * BeltLane.ITEM_SPACING);
                }

                if (renderPos < 0f || renderPos > 1f) continue;

                // Lazily grow the instance list
                if (idx >= instances.size()) {
                    instances.add(instancerProvider()
                            .instancer(InstanceTypes.TRANSFORMED, model)
                            .createInstance());
                }

                TransformedInstance inst = instances.get(idx++);

                float beltT = renderPos; // 0 = input, 1 = output
                float laneOffset = (laneIdx == 0 ? -0.15f : 0.15f);
                float wx = visualPos.getX() + 0.5f
                        + facing.getStepX() * (beltT - 0.5f)
                        + laneOffset * facing.getClockWise().getStepX();
                float wy = visualPos.getY() + 0.1f;
                float wz = visualPos.getZ() + 0.5f
                        + facing.getStepZ() * (beltT - 0.5f)
                        + laneOffset * facing.getClockWise().getStepZ();

                inst.setTransform(new Matrix4f().translate(wx, wy, wz).scale(0.25f))
                        .light(packedLight)
                        .setChanged();
            }
        }

        // Delete excess instances (item count dropped)
        while (instances.size() > idx) {
            instances.remove(instances.size() - 1).delete();
        }
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
