package proto.mechanicalarmory.client.renderer.armor;

import dev.engine_room.flywheel.api.visual.EffectVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.AbstractInstance;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import proto.mechanicalarmory.MechanicalArmoryClient;

import java.util.ArrayList;
import java.util.List;

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
        instanceTree.updateInstancesStatic(pose);

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
        //pose.translate((float) ePos.x(), (float) ePos.y(), (float) ePos.z());
                Vec3 behind = Vec3.directionFromRotation(
                0, (float) ( 180 + holderEntity.getPreciseBodyRotation(ctx.partialTick())));
        //pose.translate((float) behind.x(), (float) behind.y(), (float) behind.z());

        //pose.rotateY(-holderEntity.getPreciseBodyRotation(ctx.partialTick()) * (float) (Math.PI / 180.0));



        var packedLight = LevelRenderer.getLightColor(holderEntity.level(), holderEntity.getOnPos().above());
        instanceTree.traverse(instance -> {
            instance.light(packedLight)
                    .setChanged();
        });

        solveFABRIK(instanceTree.child("RightArm"), new Vector3f(4,4,7), 32 , 0.001f);
        instanceTree.updateInstancesStatic(pose);

    }

    private static final Vector3f BONE_AXIS = new Vector3f(0, 1, 0);

    public void solveFABRIK(InstanceTree2 root, Vector3f targetPos, int iterations, float tolerance) {
        // 1. Gather the chain
        List<InstanceTree2> chain = new ArrayList<>();
        InstanceTree2 current = root;
        while (current != null) {
            chain.add(current);
            int nextId = chain.size();
            String nextName = "RightArm.0" + (nextId < 10 ? "0" + nextId : nextId);
            current = current.child(nextName);
        }

        if (chain.size() < 2) return;

        // 2. Extract World Positions & Lengths
        Vector3f[] joints = new Vector3f[chain.size()];
        float[] lengths = new float[chain.size() - 1];

        for (int i = 0; i < chain.size(); i++) {
            joints[i] = new Vector3f();
            // This gets the absolute world position of the joint
            chain.get(i).getPoseMatrix().getTranslation(joints[i]);
            if (i > 0) {
                lengths[i - 1] = joints[i].distance(joints[i - 1]);
            }
        }

        // Root is fixed
        Vector3f origin = new Vector3f(joints[0]);
        float totalLen = 0;
        for(float l : lengths) totalLen += l;

        // unreachable target optimization
        if (origin.distance(targetPos) > totalLen) {
            // Just stretch out in a line towards target
            Vector3f dir = new Vector3f(targetPos).sub(origin).normalize();
            for (int i = 1; i < joints.length; i++) {
                joints[i].set(joints[i-1]).add(new Vector3f(dir).mul(lengths[i-1]));
            }
        } else {
            // 3. FABRIK Iteration
            for (int iter = 0; iter < iterations; iter++) {
                if (joints[joints.length - 1].distance(targetPos) < tolerance) break;

                // BACKWARD: Tip -> Root
                joints[joints.length - 1].set(targetPos);
                for (int i = joints.length - 2; i >= 0; i--) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i + 1]).normalize();
                    joints[i].set(joints[i + 1]).add(dir.mul(lengths[i]));
                }

                // FORWARD: Root -> Tip
                joints[0].set(origin);
                for (int i = 1; i < joints.length; i++) {
                    Vector3f dir = new Vector3f(joints[i]).sub(joints[i - 1]).normalize();
                    joints[i].set(joints[i - 1]).add(dir.mul(lengths[i - 1]));
                }
            }
        }

        // 4. Apply Rotations (World -> Local conversion)
        // We need to track the accumulated world rotation of the parent to figure out local rotation.
        Quaternionf accumulatedWorldRot = new Quaternionf();

        // If the whole suit is rotated (e.g. by entity body yaw), initialize accumulatedWorldRot with that.
        // For now, we assume Identity, or extract it from the root's current state if needed.
        // accumulatedWorldRot.set(initialEntityRotation);

        for (int i = 0; i < chain.size() - 1; i++) {
            InstanceTree2 segment = chain.get(i);
            Vector3f currentPos = joints[i];
            Vector3f nextPos = joints[i + 1];

            // A. Calculate the direction we WANT to point in World Space
            Vector3f targetWorldDir = new Vector3f(nextPos).sub(currentPos).normalize();

            // B. Convert that World Direction into Local Direction relative to parent
            // We do this by applying the INVERSE of the accumulated parent rotation
            Vector3f targetLocalDir = new Vector3f(targetWorldDir);
            Quaternionf invParentRot = new Quaternionf(accumulatedWorldRot).invert();
            targetLocalDir.rotate(invParentRot);

            // C. Calculate the rotation needed to turn our BONE_AXIS to face targetLocalDir
            Quaternionf localRot = new Quaternionf().rotationTo(BONE_AXIS, targetLocalDir);

            // D. Apply to the visual instance
            // Assuming InstanceTree2 has a method to set rotation from Quaternion
            // If it only has Euler (xRot, yRot), we convert:
            applyQuaternionToInstance(segment, localRot);

            // E. Update the accumulated world rotation for the next child
            accumulatedWorldRot.mul(localRot);
        }
    }

    private void applyQuaternionToInstance(InstanceTree2 segment, Quaternionf q) {
        // JOML method to get Euler angles YXZ is common for Minecraft
        Vector3f euler = new Vector3f();
        q.getEulerAnglesZYX(euler);

        // Apply (Note: JOML returns radians, Minecraft usually expects radians or degrees depending on method)
        // Check if your InstanceTree2 expects Radians or Degrees.
        // Assuming Radians here:
        segment.yRot(euler.y); // Yaw
        segment.xRot(euler.x); // Pitch
        segment.zRot(euler.z); // Roll
    }
}
