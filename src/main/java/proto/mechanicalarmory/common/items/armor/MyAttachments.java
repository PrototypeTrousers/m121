package proto.mechanicalarmory.common.items.armor;

import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class MyAttachments {
    // 1. Create the DeferredRegister for Attachments
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
        DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "mymod");

    // 2. Register the attachment. 
    // We use a simple Vec3. Default value is Vec3.ZERO to avoid null checks.
    public static final Supplier<AttachmentType<Vec3>> ARM_TARGET =
        ATTACHMENT_TYPES.register("arm_target", () -> AttachmentType.builder(() -> Vec3.ZERO)
            .serialize(Vec3.CODEC) // Optional: strictly if you want it to save on logout/login
            .copyOnDeath()         // Optional: keep target after respawning
            .build());

    public static void init(){};
}