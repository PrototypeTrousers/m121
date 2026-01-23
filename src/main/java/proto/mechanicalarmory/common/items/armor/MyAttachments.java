package proto.mechanicalarmory.common.items.armor;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import proto.mechanicalarmory.client.renderer.armor.OctoSuitEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class MyAttachments {
    // 1. Create the DeferredRegister for Attachments
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    public static final Supplier<AttachmentType<List<Vec3>>> ARM_TARGETS =
            ATTACHMENT_TYPES.register("arm_targets", () -> AttachmentType.builder(() -> {
                        List<Vec3> newList = new ArrayList<>();
                        newList.add(Vec3.ZERO);
                        newList.add(Vec3.ZERO);
                        newList.add(Vec3.ZERO);
                        newList.add(Vec3.ZERO);
                        return newList;
                    })
                    // 2. Use .listOf() to handle the List structure
                    .serialize(Vec3.CODEC.listOf().xmap(ArrayList::new, ArrayList::new))
                    .copyOnDeath()
                    .sync(
                            StreamCodec.composite(
                                            ByteBufCodecs.DOUBLE, Vec3::x,
                                            ByteBufCodecs.DOUBLE, Vec3::y,
                                            ByteBufCodecs.DOUBLE, Vec3::z,
                                            Vec3::new)
                                    .apply(ByteBufCodecs.collection(ArrayList::new)))
                    .build());

    public static final Supplier<AttachmentType<OctoSuitEffect>> ARM_EFFECT_VISUAL =
            ATTACHMENT_TYPES.register("arm_effect_visual", () -> AttachmentType.builder((holder) -> new OctoSuitEffect(((Entity) holder).level(), (Entity) holder))
                    .build());
}