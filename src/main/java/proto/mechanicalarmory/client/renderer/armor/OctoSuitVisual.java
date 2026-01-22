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

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    Entity holderEntity;
    Vector3f visualPos;
    ModelTree modelTree = MechanicalArmoryClient.octoArmModelTree;
    private final FabrikSolver ikSolver = new FabrikSolver();

    private final InstanceTree2 instanceTree;
    private final InstanceTree2 back;
    private final InstanceTree2 topRightArm;
    Matrix4f pose = new Matrix4f();


    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity , float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;

        instanceTree = InstanceTree2.create(ctx.instancerProvider(), modelTree);

        back = instanceTree.child("Cube");
        topRightArm = instanceTree.child("RightArm");
        instanceTree.updateInstancesStatic(pose);

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
        pose.translate(0, 1 , 0);

        Vec3 ePos = holderEntity.getPosition(ctx.partialTick());
        pose.translate((float) ePos.x(), (float) ePos.y(), (float) ePos.z());
                Vec3 behind = Vec3.directionFromRotation(
                0, (float) ( 180 + holderEntity.getPreciseBodyRotation(ctx.partialTick())));
        pose.translate((float) behind.x(), (float) behind.y(), (float) behind.z());

        pose.rotateY(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));

        back.child(0).instance().setTransform(pose);

        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });

        Vector3f worldTarget = new Vector3f(
                0,
                0,
                6
        );

        // Pass the Root Matrix (pose.last().pose())
        ikSolver.solve(
                topRightArm,
                worldTarget,
                pose, // New Parameter: The Root Pose
                10,
                0.001f
        );
    }
}
