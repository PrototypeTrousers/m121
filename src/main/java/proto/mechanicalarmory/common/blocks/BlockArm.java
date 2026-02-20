package proto.mechanicalarmory.common.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.screens.ArmScreen;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

public class BlockArm extends Block implements EntityBlock {
    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<BlockArm>> COMPLEX_CODEC = MechanicalArmory.REGISTRAR.register("simple", () -> simpleCodec(BlockArm::new));
    int value;

    public BlockArm(Properties properties) {
        super(properties);
        properties.noOcclusion();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected @NotNull MapCodec<BlockArm> codec() {
        return COMPLEX_CODEC.value();
    }

    public int getValue() {
        return this.value;
    }

    @Override
    public boolean hasDynamicShape() {
        return true;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return super.getOcclusionShape(state, level, pos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArmEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (world1, pos, state1, blockEntity) -> {
            if (blockEntity instanceof BlockEntityTicker ticker)
                ticker.tick(world1, pos, state1, blockEntity);
        };
    }

    @Override
    protected boolean isOcclusionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            // Find the BlockEntity at this position
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ArmEntity arm) {
                // Open the owo screen
                Minecraft.getInstance().setScreen(new ArmScreen(arm));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
