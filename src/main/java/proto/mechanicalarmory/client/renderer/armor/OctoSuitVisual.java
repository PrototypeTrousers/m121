package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;

public class OctoSuitVisual implements EffectVisual<OctoSuitEffect>, SimpleDynamicVisual {

    TransformedInstance transformedInstance;
    Entity holderEntity;
    Vector3f visualPos;
    ModelTree modelTree = MechanicalArmoryClient.octoArmModelTree;

    private final InstanceTree instanceTree;
//    private final @Nullable InstanceTree firstArm;
//    private final @Nullable InstanceTree secondArm;
//    private final @Nullable InstanceTree baseMotor;
//    private final @Nullable InstanceTree itemAttachment;
    Matrix4f pose = new Matrix4f();


    public OctoSuitVisual(VisualizationContext ctx, Entity holderEntity , float partialTick) {
        this.visualPos = new Vector3f((float) (holderEntity.getPosition(partialTick).x - ctx.renderOrigin().getX()),
                (float) (holderEntity.getPosition(partialTick).y - ctx.renderOrigin().getY()),
                (float) (holderEntity.getPosition(partialTick).z - ctx.renderOrigin().getZ()));
        this.holderEntity = holderEntity;

        instanceTree = InstanceTree.create(ctx.instancerProvider(), modelTree);
//        baseMotor = instanceTree.child("BaseMotor");
//        firstArm = baseMotor.child("FirstArm");
//        secondArm = firstArm.child("SecondArm");
//        itemAttachment = secondArm.child("ItemAttach");
        var rightArm = instanceTree.child("RightArm");

        float totalSegments = 7f;
// The total amount of "bend" you want in the whole arm (90 degrees)
        float totalRad = (float) (270 * Math.PI / 180);

        InstanceTree parent = rightArm;
        for (int i = 1; i <= 8; i++) {
            // Dynamically get RightArm.001 through RightArm.007
            var segment = parent.child("RightArm.00" + (i));
            parent = segment;

            // Progress is 0.0 at the shoulder, 1.0 at the fingertips
            float progress = (i - 1) / (totalSegments - 1);

            // We split the 90-degree bend across the segments.
            // Early segments only get Z-rotation (bending it Right)
            // Later segments only get X-rotation (bending it Forward)
            float segmentRotation = totalRad / totalSegments;

            // Use 'progress' to shift the rotation from Z to X
            float zAmount = (1.0f - progress) * segmentRotation;
            float xAmount = progress * segmentRotation;

            segment.zRot(zAmount);
            segment.xRot(-xAmount); // Negative X usually bends "forward"
            segment.yRot(0);        // Explicitly kill any inherited twist
        }


//        transformedInstance.light(15,15);
    }

    @Override
    public void update(float partialTick) {

    }

    @Override
    public void delete() {
        transformedInstance.delete();
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



        instanceTree.updateInstances(pose);

        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });



//        transformedInstance.setIdentityTransform();
//        transformedInstance.translate(-0.5f, 0.5f, -0.5f);

//
//        transformedInstance.rotateYCentered(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));
//
//        transformedInstance.setChanged();
    }
}
