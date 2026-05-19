package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

public class InterpolatingInstancetype {
    public static final InstanceType<InterpolatedInstance> INTERPOLATED = SimpleInstanceType.builder(InterpolatedInstance::new)
            .layout(LayoutBuilder.create()
                    .vector("color", FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4) // 4 bytes
                    .vector("overlay", IntegerRepr.SHORT, 2)                // 4 bytes
                    .vector("light", FloatRepr.UNSIGNED_SHORT, 2)           // 4 bytes

                    // Previous Keyframe Layout (Tick N)
                    .vector("posFrom", FloatRepr.FLOAT, 3)                  // 12 bytes
                    .vector("rotFrom", FloatRepr.FLOAT, 4)                  // 16 bytes (Quaternion)
                    .vector("scaleFrom", FloatRepr.FLOAT, 3)                // 12 bytes

                    // Current Keyframe Layout (Tick N+1 / Goal)
                    .vector("posGoal", FloatRepr.FLOAT, 3)                  // 12 bytes
                    .vector("rotGoal", FloatRepr.FLOAT, 4)                  // 16 bytes (Quaternion)
                    .vector("scaleGoal", FloatRepr.FLOAT, 3)                // 12 bytes
                    .build()) // Total struct window size = 80 bytes
            .writer((ptr, instance) -> {
                // Common Lit Overlay attributes (0 - 11)
                MemoryUtil.memPutByte(ptr, instance.red);
                MemoryUtil.memPutByte(ptr + 1, instance.green);
                MemoryUtil.memPutByte(ptr + 2, instance.blue);
                MemoryUtil.memPutByte(ptr + 3, instance.alpha);
                ExtraMemoryOps.put2x16(ptr + 4, instance.overlay);
                ExtraMemoryOps.put2x16(ptr + 8, instance.light);

                // Track 'From' Keyframe writes (12 - 51)
                ExtraMemoryOps.putVector3f(ptr + 12, instance.posFrom);
                ExtraMemoryOps.putQuaternionf(ptr + 24, instance.rotFrom);
                ExtraMemoryOps.putVector3f(ptr + 40, instance.scaleFrom);

                // Track 'Goal' Keyframe writes (52 - 79)
                ExtraMemoryOps.putVector3f(ptr + 52, instance.posGoal);
                ExtraMemoryOps.putQuaternionf(ptr + 64, instance.rotGoal);
                ExtraMemoryOps.putVector3f(ptr + 80, instance.scaleGoal);
            })
            // Maps to your mod's local asset directory path
            .vertexShader(ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "interpolated/interpolatedtransformed.vert"))
            // Points to your custom hierarchy compute culling logic
            //.cullShader(ResourceLocation.fromNamespaceAndPath("mechanicalarmory", "instance/cull/interpolated_arm.glsl"))
            .build();
}
