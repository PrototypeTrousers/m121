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

    private final InstanceTree2 instanceTree;
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

        instanceTree = InstanceTree2.create(ctx.instancerProvider(), modelTree);
//        baseMotor = instanceTree.child("BaseMotor");
//        firstArm = baseMotor.child("FirstArm");
//        secondArm = firstArm.child("SecondArm");
//        itemAttachment = secondArm.child("ItemAttach");
        var rightArm = instanceTree.child("RightArm");
        rightArm.yRot((float) (Math.PI));
        rightArm.xRot(0.5f);
        rightArm.zRot(-0.5f);

        var rightArm001 = rightArm.child("RightArm.001");
        rightArm001.xRot(0.5f);
        rightArm001.zRot(-0.5f);

        var rightArm002 = rightArm001.child("RightArm.002");
        rightArm002.xRot(0.5f);
        rightArm002.zRot(-0.5f);

        var rightArm003 = rightArm002.child("RightArm.003");
        rightArm003.xRot(0.5f);
        rightArm003.zRot(-0.5f);

        var rightArm004 = rightArm003.child("RightArm.004");
        rightArm004.xRot(0.5f);
        rightArm004.zRot(-0.5f);

        var rightArm005 = rightArm004.child("RightArm.005");
        rightArm005.xRot(0.5f);
        rightArm005.zRot(-0.5f);

        var rightArm006 = rightArm005.child("RightArm.006");
        rightArm006.xRot(0.5f);
        rightArm006.zRot(-0.5f);

//        var rightArm007 = rightArm006.child("RightArm.007");
//        rightArm007.xRot(-0.5f);








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
        int i =0;

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
