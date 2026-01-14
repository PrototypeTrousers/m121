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
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.logic.*;

import static proto.mechanicalarmory.common.logic.Action.DELIVER;
import static proto.mechanicalarmory.common.logic.Action.RETRIEVE;

public class ArmEntity extends BlockEntity {
    public ArmEntity(BlockPos pos, BlockState state) {
        super(MAEntities.ARM_ENTITY.get(), pos, state);
    }

    private final Targeting targeting = new Targeting();
    private final MotorCortex motorCortex;
    private final WorkStatus workStatus = new WorkStatus();
    protected ItemStackHandler itemHandler = new ArmItemHandler(1);
    private Vector3d armPoint;

    private int progress = 0;


    public TileArmBase(float armSize, InteractionType interactionType) {
        super();
        motorCortex = new MotorCortex(this, armSize, interactionType);
    }

    public float[] getAnimationRotation(int idx) {
        return motorCortex.getAnimationRotation(idx);
    }

    public float[] getRotation(int idx) {
        return motorCortex.getRotation(idx);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // getUpdateTag() is called whenever the chunkdata is sent to the
        // client. In contrast getUpdatePacket() is called when the tile entity
        // itself wants to sync to the client. In many cases you want to send
        // over the same information in getUpdateTag() as in getUpdatePacket().
        return writeToNBT(new CompoundTag());
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        // Prepare a packet for syncing our TE to the client. Since we only have to sync the stack
        // and that's all we have we just write our entire NBT here. If you have a complex
        // tile entity that doesn't need to have all information on the client you can write
        // a more optimal NBT here.
        CompoundTag nbtTag = new CompoundTag();
        this.writeToNBT(nbtTag);
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
        armPoint = new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Override
    public void readFromNBT(CompoundTag compound) {
        super.readFromNBT(compound);
        motorCortex.deserializeNBT(compound.getList("rotation", CompoundTag.TAG_FLOAT));
        targeting.deserializeNBT(compound.getCompound("targeting"));
        workStatus.deserializeNBT(compound.getCompound("workStatus"));
    }

    @Override
    public CompoundTag writeToNBT(CompoundTag compound) {
        super.writeToNBT(compound);
        compound.put("rotation", motorCortex.serializeNBT());
        compound.put("targeting", targeting.serializeNBT());
        compound.put("workStatus", workStatus.serializeNBT());
        return compound;
    }


    public abstract ActionResult interact(Action retrieve, Pair<BlockPos, Direction> blkFacePair);

    @Override
    public void update() {
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
                if (itemHandler.getStackInSlot(0).isEmpty()){
                    updateWorkStatus(ActionTypes.MOVEMENT, RETRIEVE);
                } else {
                    ActionResult result = motorCortex.move(targeting.getTargetVec(), targeting.getTargetFacing());
                    if (result == ActionResult.SUCCESS) {
                        updateWorkStatus(ActionTypes.INTERACTION, DELIVER);
                    }
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
        if (!getWorld().isRemote && this.progress++ == 100) {
            this.progress = 0;
        }
    }

    private void updateWorkStatus(ActionTypes type, Action action) {
        workStatus.setType(type);
        workStatus.setAction(action);
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        markDirty();
    }

    public WorkStatus getWorkStatus() {
        return workStatus;
    }

    public void setSource(BlockPos sourcePos, Direction sourceFacing) {
        targeting.setSource(sourcePos.subtract(this.getBlockPos()) , sourceFacing);
        markDirty();
    }

    public void setTarget(BlockPos targetPos, Direction targetFacing) {
        targeting.setTarget(targetPos.subtract(this.getBlockPos()), targetFacing);
        markDirty();
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
            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    
}