package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;
import proto.mechanicalarmory.client.renderer.util.ArmPathPlanner;
import proto.mechanicalarmory.client.renderer.util.SnakeSolver;

import java.util.List;

import static proto.mechanicalarmory.common.items.armor.MyAttachments.ARM_TARGETS;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    Entity holderEntity;
    Vector3f visualPos;
    ModelTree modelTree = MechanicalArmoryClient.octoArmModelTree;
    private final SnakeSolver ikSolver = new SnakeSolver();

    private final InstanceTree2 instanceTree;
    private final InstanceTree2 back;
    private final InstanceTree2 topRightArm;
    private final InstanceTree2 bottomRightArm;
    private final InstanceTree2 topLeftArm;
    private final InstanceTree2 bottomLeftArm;
    Matrix4f pose = new Matrix4f();

    private final SnakeSolver snakeSolver = new SnakeSolver();

    // Instantiate one planner for each arm
    private final ArmPathPlanner plannerTopLeft = new ArmPathPlanner();
    private final ArmPathPlanner plannerTopRight = new ArmPathPlanner();
    private final ArmPathPlanner plannerBotLeft = new ArmPathPlanner();
    private final ArmPathPlanner plannerBotRight = new ArmPathPlanner();


    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity, float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;

        instanceTree = InstanceTree2.create(ctx.instancerProvider(), modelTree);

        back = instanceTree.child("Cube");
        topLeftArm = instanceTree.child("TopLeftArm");
        bottomLeftArm = instanceTree.child("BottomLeftArm");

        topRightArm = instanceTree.child("TopRightArm");
        bottomRightArm = instanceTree.child("BottomRightArm");


        instanceTree.updateInstancesStatic(pose);

        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
    }

    @Override
    public void update(float partialTick) {

    }

    @Override
    public void delete() {
        instanceTree.delete();
    }

    @Override
    public void beginFrame(Context ctx) {
        pose.identity();
        pose.translate(0, 1, 0);


        Vec3 ePos = holderEntity.getPosition(ctx.partialTick());
        pose.translate((float) ePos.x(), (float) ePos.y(), (float) ePos.z());
        Vec3 behind = Vec3.directionFromRotation(
                0, (float) (180 + holderEntity.getPreciseBodyRotation(ctx.partialTick())));
        pose.translate((float) behind.x(), (float) behind.y(), (float) behind.z());

        pose.rotateY(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));

        back.child(0).instance().setTransform(pose).setChanged();

        List<Vec3> targets = holderEntity.getData(ARM_TARGETS);

        List<Vec3> rawTargets = holderEntity.getData(ARM_TARGETS);

        Vector3f shoulderOrigin = new Vector3f();
        shoulderOrigin.add((float) ePos.x, (float) ePos.y, (float) ePos.z);
        shoulderOrigin.add((float) behind.x, (float) behind.y, (float) behind.z);

        // 1. Update Planners
        // They will handle A* internally and store the result in .currentPath
        plannerTopLeft.update(shoulderOrigin, rawTargets.get(0).toVector3f(), holderEntity.level());
        plannerTopRight.update(shoulderOrigin, rawTargets.get(1).toVector3f(), holderEntity.level());
        plannerBotLeft.update(shoulderOrigin, rawTargets.get(2).toVector3f(), holderEntity.level());
        plannerBotRight.update(shoulderOrigin, rawTargets.get(3).toVector3f(), holderEntity.level());

        // 2. Draw
        // Just pass the planner's path to the solver
        snakeSolver.solveMultiSegment(topLeftArm, plannerTopLeft.currentPath, pose);
        snakeSolver.solveMultiSegment(topRightArm, plannerTopRight.currentPath, pose);
        snakeSolver.solveMultiSegment(bottomLeftArm, plannerBotLeft.currentPath, pose);
        snakeSolver.solveMultiSegment(bottomRightArm, plannerBotRight.currentPath, pose);

        topRightArm.visible(false);
        bottomLeftArm.visible(false);
        bottomRightArm.visible(false);

    }
}
