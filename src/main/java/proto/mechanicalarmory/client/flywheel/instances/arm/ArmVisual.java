package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.task.Plan;
import dev.engine_room.flywheel.api.visual.LightUpdatedVisual;
import dev.engine_room.flywheel.api.visual.TickableVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.task.RunnablePlan;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Consumer;

public class ArmVisual extends AbstractBlockEntityVisual<ArmEntity> implements TickableVisual, LightUpdatedVisual {

    private static Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> modelCache =
            new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());

    // Model hierarchy (mirrors ArmRenderer / GLTF structure):
    //   root
    //   ├── Base       (mesh node — the static base plate)
    //   └── BaseMotor  (pivot group)
    //       └── FirstArm  (first arm segment)
    //           └── SecondArm  (second arm segment)
    private final InterpolatingInstanceTree instanceTree;
    private final @Nullable InterpolatingInstanceTree base;
    private final @Nullable InterpolatingInstanceTree baseMotor;
    private final @Nullable InterpolatingInstanceTree firstArm;
    private final @Nullable InterpolatingInstanceTree secondArm;

    public static int visuals;
    ModelTree modelTree = MechanicalArmoryClient.fullArmModelTree;
    int packedLight;

    public ArmVisual(VisualizationContext ctx, ArmEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick);
        packedLight = LevelRenderer.getLightColor(level, pos.above());

        instanceTree = InterpolatingInstanceTree.create(instancerProvider(), modelTree);
        instanceTree.setChanged();

        // Wire up tree references to match ArmRenderer's child() calls exactly.
        base      = instanceTree.child("Base");
        baseMotor = instanceTree.child("BaseMotor");
        firstArm  = baseMotor  != null ? baseMotor.child("FirstArm")  : null;
        secondArm = firstArm   != null ? firstArm.child("SecondArm")  : null;

        visuals++;
    }

    @Override
    public void collectCrumblingInstances(Consumer<@Nullable Instance> consumer) {
        instanceTree.traverse(consumer);
    }

    @Override
    public void updateLight(float partialTick) {
        packedLight = LevelRenderer.getLightColor(level, pos.above());
        instanceTree.traverse(instance -> instance.light(packedLight).setChanged());
    }

    @Override
    protected void _delete() {
        instanceTree.delete();
        visuals--;
    }

    @Override
    public void setSectionCollector(SectionCollector sectionCollector) {
        this.lightSections = sectionCollector;
        lightSections.sections(LongSet.of(SectionPos.asLong(pos)));
    }

    @Override
    public Plan<Context> planTick() {
        return RunnablePlan.of((context) -> {
            // ── Cascade rule ────────────────────────────────────────────────
            // root.pos = arm block world-space origin  (+ 0.5,0,0.5 centre offset,
            //            matching the poseStack.translate in ArmRenderer)
            // Every other node: pos/rot are LOCAL relative to its parent.
            // cascadeToBuffer() chains parent TRS × local TRS → world-space.
            //
            // Hierarchy: root → Base (sibling)
            //                 → BaseMotor → FirstArm → SecondArm

            instanceTree.posGoal.set(
                    visualPos.getX() + 0.5f,
                    visualPos.getY(),
                    visualPos.getZ() + 0.5f);
            instanceTree.posFrom.set(
                    visualPos.getX() + 0.5f,
                    visualPos.getY(),
                    visualPos.getZ() + 0.5f);

            // Base: no local offset from root.
            // (its initial pose from the GLTF is baked into the mesh geometry)
            if (base != null) {
                base.posGoal.set(0, 0, 0);
                base.posFrom.set(0, 0, 0);
            }

            // BaseMotor: pivot at root origin (local offset = 0).
            // Set rotGoal/rotFrom here to rotate the whole arm chain.
            if (baseMotor != null) {
                baseMotor.posGoal.set(0, 0, 0);
                baseMotor.posFrom.set(0, 0, 0);
                baseMotor.rotFrom.set(new Quaternionf().rotateX(30));
                baseMotor.rotGoal.set(new Quaternionf().rotateX(30));
            }

            // FirstArm: child of BaseMotor — local transform relative to BaseMotor.
            // Set rotGoal/rotFrom to animate the first arm joint.
            if (firstArm != null) {
                firstArm.posGoal.set(0, 1, 0);
                firstArm.posFrom.set(0, 1, 0);
            }

            // SecondArm: child of FirstArm — local transform relative to FirstArm.
            if (secondArm != null) {
                secondArm.posGoal.set(0, 1, 0);
                secondArm.posFrom.set(0, 1, 0);
            }

            instanceTree.cascadeWorldTransforms();
            instanceTree.cascadeToBuffer(PartTransformBuffer.get());
        });
    }
}
