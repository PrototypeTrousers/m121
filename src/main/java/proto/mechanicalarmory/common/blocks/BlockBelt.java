package proto.mechanicalarmory.common.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.network.BeltNetworkData;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

public class BlockBelt extends Block implements EntityBlock {

    public static final DeferredHolder<MapCodec<? extends Block>, MapCodec<BlockBelt>> BELT_CODEC =
            MechanicalArmory.REGISTRAR.register("belt", () -> simpleCodec(BlockBelt::new));

    public static final DirectionProperty FACING  = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty   POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public BlockBelt(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    // ── Block state ───────────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection())
                .setValue(POWERED, ctx.getLevel().hasNeighborSignal(ctx.getClickedPos()));
    }

    @Override
    protected @NotNull MapCodec<BlockBelt> codec() {
        return BELT_CODEC.value();
    }

    // ── Shape ─────────────────────────────────────────────────────────────────

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                            @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    // ── Placement & removal ───────────────────────────────────────────────────

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                             @Nullable LivingEntity placer,
                             @NotNull ItemStack stack) {
        if (!level.isClientSide && level instanceof ServerLevel srv) {
            Direction facing = state.getValue(FACING);
            BeltNetworkData.get(srv).onBeltPlaced(pos, facing, srv);
        }
    }

    @Override
    protected void onRemove(@NotNull BlockState state, @NotNull Level level,
                             @NotNull BlockPos pos, @NotNull BlockState newState, boolean movedByPiston) {
        if (!newState.is(this)) {
            if (!level.isClientSide && level instanceof ServerLevel srv) {
                BeltNetworkData.get(srv).onBeltRemoved(pos, srv);
            } else if (level.isClientSide) {
                ClientBeltNetwork.get().removeNode(pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // ── Redstone ──────────────────────────────────────────────────────────────

    @Override
    protected void neighborChanged(@NotNull BlockState state, @NotNull Level level,
                                    @NotNull BlockPos pos, @NotNull Block block,
                                    @NotNull BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level instanceof ServerLevel srv) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) {
                BlockState newState = state.setValue(POWERED, powered);
                level.setBlock(pos, newState, 3);
                BeltNetworkData.get(srv).onBeltStopped(pos, powered, srv);
            }
        }
    }

    // ── Right-click: insert item into belt lane ────────────────────────────────

    @Override
    protected @NotNull ItemInteractionResult useItemOn(@NotNull ItemStack heldStack,
                                                       @NotNull BlockState state,
                                                       @NotNull Level level,
                                                       @NotNull BlockPos pos,
                                                       @NotNull Player player,
                                                       @NotNull InteractionHand hand,
                                                       @NotNull BlockHitResult hit) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (heldStack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level instanceof ServerLevel srv)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        BeltNetworkData data = BeltNetworkData.get(srv);
        BeltNode node = data.nodeAt(pos);
        if (node == null || node.isStopped()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // Determine which lane (0=left, 1=right) based on where the player clicked.
        // We compare the hit location's offset from the block centre along the
        // belt's perpendicular axis.
        Direction facing = state.getValue(FACING);
        Vec3 hitLocal = hit.getLocation().subtract(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Perpendicular axis to facing
        Direction perp = facing.getClockWise();
        double perpOffset = hitLocal.x() * perp.getStepX() + hitLocal.z() * perp.getStepZ();
        int lane = perpOffset >= 0 ? 1 : 0;

        ItemStack toInsert = heldStack.copyWithCount(1);
        boolean inserted = node.lane(lane).insertItem(toInsert);
        if (!inserted) {
            // Try the other lane
            lane = 1 - lane;
            inserted = node.lane(lane).insertItem(toInsert);
        }

        if (inserted) {
            if (!player.isCreative()) heldStack.shrink(1);
            data.setDirty();
            data.sendCorrection(pos, srv);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }


    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BeltEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T>
    getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        // BeltEntity has no server-side ticker; return null.
        return null;
    }
}
