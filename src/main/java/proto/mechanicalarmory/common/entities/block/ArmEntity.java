package proto.mechanicalarmory.common.entities.block;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.logic.*;

import static proto.mechanicalarmory.common.logic.Action.DELIVER;
import static proto.mechanicalarmory.common.logic.Action.RETRIEVE;

public class ArmEntity extends BlockEntity implements BlockEntityTicker<ArmEntity> {
    private final Targeting targeting = new Targeting();
    private final MotorCortex motorCortex;
    private final WorkStatus workStatus = new WorkStatus();
    protected ItemStackHandler itemHandler = new ArmItemHandler(1);
    private Vector3d armPoint;
    float armSize = 2f;
    InteractionType interactionType = InteractionType.ITEM;

    public ArmEntity(BlockPos pos, BlockState state) {
        super(MAEntities.ARM_ENTITY.get(), pos, state);
        motorCortex = new MotorCortex(this, 1f, interactionType);
        targeting.setSource(new BlockPos(2,0,0), Direction.UP);
        targeting.setTarget(new BlockPos(-2,0,0), Direction.UP);
    }

    private int progress = 0;

    public float[] getAnimationRotation(int idx) {
        return motorCortex.getAnimationRotation(idx);
    }

    public float[] getRotation(int idx) {
        return motorCortex.getRotation(idx);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, pRegistries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        armPoint = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    @Override
    protected void saveAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.saveAdditional(compound, registries);
        compound.put("rotation", motorCortex.serializeNBT(registries));
        compound.put("targeting", targeting.serializeNBT(registries));
        compound.put("workStatus", workStatus.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag compound, HolderLookup.Provider registries) {
        super.loadAdditional(compound, registries);
        motorCortex.deserializeNBT(registries, compound.getList("rotation", CompoundTag.TAG_FLOAT));
        targeting.deserializeNBT(registries, compound.getCompound("targeting"));
        workStatus.deserializeNBT(registries, compound.getCompound("workStatus"));
    }

    public ActionResult interact(Action action, Pair<BlockPos, Direction> blkFace) {
        if (getLevel().isClientSide()) {
            return ActionResult.CONTINUE;
        }
        BlockEntity te = level.getBlockEntity(blkFace.key().offset((this.worldPosition)));
        if (te != null) {
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, blkFace.key().offset((this.worldPosition)), blkFace.value());
            if (itemHandler != null) {
                if (action == Action.RETRIEVE) {
                    if (this.itemHandler.getStackInSlot(0).isEmpty()) {
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            if (!itemHandler.extractItem(i, 1, true).isEmpty()) {
                                ItemStack itemStack = itemHandler.extractItem(i, 1, true);
                                if (!itemStack.isEmpty()) {
                                    itemStack = itemHandler.extractItem(i, 1, false);
                                    this.itemHandler.insertItem(0, itemStack, false);
                                    return ActionResult.SUCCESS;
                                }
                            }
                        }
                    }
                } else if (action == Action.DELIVER) {
                    ItemStack itemStack = this.itemHandler.extractItem(0, 1, true);
                    if (!itemStack.isEmpty()) {
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            if (itemHandler.insertItem(i, itemStack, true).isEmpty()) {
                                itemStack = this.itemHandler.extractItem(0, 1, false);
                                itemHandler.insertItem(i, itemStack, false);
                                return ActionResult.SUCCESS;
                            }
                        }
                    }
                }
            }
        }
        return ActionResult.CONTINUE;
    }


    @Override
    public void tick(Level level, BlockPos pos, BlockState state, ArmEntity blockEntity) {
        if (workStatus.getType() == ActionTypes.IDLING) {
            if (hasInput() && hasOutput()) {
                updateWorkStatus(ActionTypes.MOVEMENT, RETRIEVE);
            }
        } else if (workStatus.getType() == ActionTypes.MOVEMENT) {
            if (workStatus.getAction() == RETRIEVE) {
                ActionResult result = motorCortex.move( targeting.getSourceVec() , targeting.getSourceFacing());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.INTERACTION, RETRIEVE);
                }
            } else if (workStatus.getAction() == DELIVER) {
                ActionResult result = motorCortex.move(targeting.getTargetVec(), targeting.getTargetFacing());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.INTERACTION, DELIVER);
                }
            }
        } else if (workStatus.getType() == ActionTypes.INTERACTION) {
            if (workStatus.getAction() == RETRIEVE) {
                ActionResult result = interact(RETRIEVE, targeting.getSource());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.MOVEMENT, DELIVER);
                }
            } else if (workStatus.getAction() == DELIVER) {
                ActionResult result = interact(DELIVER, targeting.getTarget());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.MOVEMENT, RETRIEVE);
                }
            }
        }
        if (!getLevel().isClientSide && this.progress++ == 100) {
            this.progress = 0;
        }
    }

    private void updateWorkStatus(ActionTypes type, Action action) {
        workStatus.setType(type);
        workStatus.setAction(action);
        level.markAndNotifyBlock(worldPosition, level.getChunkAt(worldPosition), getBlockState(), getBlockState(), 3, 3);
//        markDirty();
    }

    public WorkStatus getWorkStatus() {
        return workStatus;
    }

    public void setSource(BlockPos sourcePos, Direction sourceFacing) {
        targeting.setSource(sourcePos.subtract(this.getBlockPos()) , sourceFacing);
//        markDirty();
    }

    public void setTarget(BlockPos targetPos, Direction targetFacing) {
        targeting.setTarget(targetPos.subtract(this.getBlockPos()), targetFacing);
//        markDirty();
    }

    public boolean hasInput() {
        return targeting.hasInput();
    }

    public boolean hasOutput() {
        return targeting.hasOutput();
    }

    public class ArmItemHandler extends ItemStackHandler {
        public ArmItemHandler(int i) {
            super(i);
        }

        @Override
        protected void onContentsChanged(int slot) {

            level.markAndNotifyBlock(worldPosition, level.getChunkAt(worldPosition), getBlockState(), getBlockState(), 3, 3);
        }
    }

    
}