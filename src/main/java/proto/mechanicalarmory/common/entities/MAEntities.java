package proto.mechanicalarmory.common.entities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import proto.mechanicalarmory.common.blocks.BushBlockEntity;
import proto.mechanicalarmory.common.blocks.MABlocks;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Supplier;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

public class MAEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final Supplier<BlockEntityType<ArmEntity>> ARM_ENTITY = BLOCK_ENTITY_TYPES.register(
            "armentity",
            // The block entity type, created using a builder.
            () -> BlockEntityType.Builder.of(
                            // The supplier to use for constructing the block entity instances.
                            ArmEntity::new,
                            // A vararg of blocks that can have this block entity.
                            // This assumes the existence of the referenced blocks as DeferredBlock<Block>s.
                            MABlocks.ARM.get()
                    )
                    // Build using null; vanilla does some datafixer shenanigans with the parameter that we don't need.
                    .build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BushBlockEntity>> BUSH_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("enhancedcrop",
                    () -> BlockEntityType.Builder.of(BushBlockEntity::new, BuiltInRegistries.BLOCK.stream().filter(
                            block -> block instanceof BushBlock).toList().toArray(new Block[]{})).build(null));
}
