package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.layout.IntegerRepr;
import dev.engine_room.flywheel.api.layout.FloatRepr;
import dev.engine_room.flywheel.api.layout.LayoutBuilder;
import dev.engine_room.flywheel.lib.instance.SimpleInstanceType;
import dev.engine_room.flywheel.lib.util.ExtraMemoryOps;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.system.MemoryUtil;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class InterpolatingInstancetype {
    // Instance struct: color(4) + overlay(4) + light(4) + partIdx(4) = 16 bytes
    public static final InstanceType<InterpolatedInstance> INTERPOLATED = SimpleInstanceType.builder(InterpolatedInstance::new)
            .layout(LayoutBuilder.create()
                    .vector("color",   FloatRepr.NORMALIZED_UNSIGNED_BYTE, 4) // 4 bytes
                    .vector("overlay", IntegerRepr.SHORT,                  2) // 4 bytes
                    .vector("light",   FloatRepr.UNSIGNED_SHORT,           2) // 4 bytes
                    .scalar("partIdx", IntegerRepr.INT)                        // 4 bytes
                    .build())
            .writer((ptr, instance) -> {
                MemoryUtil.memPutByte(ptr,     instance.red);
                MemoryUtil.memPutByte(ptr + 1, instance.green);
                MemoryUtil.memPutByte(ptr + 2, instance.blue);
                MemoryUtil.memPutByte(ptr + 3, instance.alpha);
                ExtraMemoryOps.put2x16(ptr + 4, instance.overlay);
                ExtraMemoryOps.put2x16(ptr + 8, instance.light);
                MemoryUtil.memPutInt(ptr + 12, instance.partIdx);
            })
            .vertexShader(ResourceLocation.fromNamespaceAndPath(MODID, "interpolated/interpolatedtransformed.vert"))
            .cullShader(ResourceLocation.fromNamespaceAndPath(MODID,   "interpolated/cull/transformed.glsl"))
            .build();
}
