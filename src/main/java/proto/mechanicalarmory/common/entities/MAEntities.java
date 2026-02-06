package proto.mechanicalarmory.common.entities;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import proto.mechanicalarmory.common.blocks.CropBlockEntity;
import proto.mechanicalarmory.common.blocks.MABlocks;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.function.Supplier;
import java.util.stream.Collectors;

import static net.irisshaders.iris.pipeline.WorldRenderingPhase.BLOCK_ENTITIES;
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

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropBlockEntity>> CROP_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("enhancedcrop",
                    () -> BlockEntityType.Builder.of(CropBlockEntity::new, BuiltInRegistries.BLOCK.stream().filter(
                            block -> block instanceof CropBlock).toList().toArray(new Block[]{})).build(null));
}
