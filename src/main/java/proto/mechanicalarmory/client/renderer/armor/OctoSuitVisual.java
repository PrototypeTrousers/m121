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
import proto.mechanicalarmory.client.renderer.util.FabrikSolver;
import proto.mechanicalarmory.common.items.armor.OctoSuit;

import java.util.List;

import static proto.mechanicalarmory.common.items.armor.MyAttachments.ARM_TARGETS;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    Entity holderEntity;
    Vector3f visualPos;
    ModelTree modelTree = MechanicalArmoryClient.octoArmModelTree;
    private final FabrikSolver ikSolver = new FabrikSolver();

    private final InstanceTree2 instanceTree;
    private final InstanceTree2 back;
    private final InstanceTree2 topRightArm;
    private final InstanceTree2 topLeftArm;
    Matrix4f pose = new Matrix4f();


    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity, float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;

        instanceTree = InstanceTree2.create(ctx.instancerProvider(), modelTree);

        back = instanceTree.child("Cube");
        topRightArm = instanceTree.child("RightArm");
        instanceTree.updateInstancesStatic(pose);

        topLeftArm = InstanceTree2.create(ctx.instancerProvider(), topRightArm.getSource());
        topLeftArm.updateInstancesStatic(pose);

        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });
        topLeftArm.traverse(instance -> {
            instance.light(packedLight).setChanged();
        });
    }

    @Override
    public void update(float partialTick) {

    }

    @Override
    public void delete() {
        instanceTree.delete();
        topLeftArm.delete();
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


        // Run your FABRIK / Inverse Kinematics logic here using 'target'
        ikSolver.solve(
                topLeftArm,
                targets.get(0).toVector3f(),
                pose, // New Parameter: The Root Pose
                10,
                0.001f
        );


        pose.translate(1f, 0, 0);

        // Pass the Root Matrix (pose.last().pose())
        ikSolver.solve(
                topRightArm,
                targets.get(1).toVector3f(),
                pose, // New Parameter: The Root Pose
                10,
                0.001f
        );
    }
}
