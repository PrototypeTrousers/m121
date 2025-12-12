package proto.mechanicalarmory.common.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import proto.mechanicalarmory.MechanicalArmory;

public class MABlocks {
    // Create a Deferred Register to hold Blocks which will all be registered under the "mechanicalarmory" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MechanicalArmory.MODID);
    // Creates a new Block with the id "mechanicalarmory:example_block", combining the namespace and path
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock("example_block", BlockBehaviour.Properties.of().mapColor(MapColor.STONE));
    public static final DeferredBlock<BlockArm> ARM = BLOCKS.registerBlock("arm", BlockArm::new, BlockBehaviour.Properties.of());
}
