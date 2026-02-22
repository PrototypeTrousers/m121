package proto.mechanicalarmory.common.logic;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.joml.Vector3d;

import java.util.Optional;

public class  Targeting implements INBTSerializable<CompoundTag> {

    Pair<BlockPos, Direction> source;
    Pair<BlockPos, Direction> target;
    private Vector3d sourceVec;
    private Vector3d targetVec;

    public Targeting() {

    }

    public Vector3d getSourceVec() {
        return sourceVec;
    }

    public Vector3d getTargetVec() {
        return targetVec;
    }

    public Pair<BlockPos, Direction> getSource() {
        return source;
    }

    public Pair<BlockPos, Direction> getTarget() {
        return target;
    }

    public Direction getTargetFacing() {
        return target.right();
    }

    public Direction getSourceFacing() {
        return source.right();
    }

    public void setSource(BlockPos sourcePos, Direction sourceFacing) {
        this.source = Pair.of(sourcePos, sourceFacing);

        this.sourceVec = new Vector3d(sourcePos.getX() + sourceFacing.getStepX() / 2f,
                sourcePos.getY() + sourceFacing.getStepY() / 2f,
                sourcePos.getZ() + sourceFacing.getStepZ() / 2f);
    }

    public void setTarget(BlockPos targetPos, Direction targetFacing) {
        this.target = Pair.of(targetPos, targetFacing);
        this.targetVec = new Vector3d(targetPos.getX() + targetFacing.getStepX() / 2f,
                targetPos.getY() + targetFacing.getStepY() / 2f,
                targetPos.getZ() + targetFacing.getStepZ() / 2f);
    }

    public boolean hasInput() {
        return source != null;
    }

    public boolean hasOutput() {
        return target != null;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag compound = new CompoundTag();
        if (source != null) {
            compound.put("sourcePos", NbtUtils.writeBlockPos(source.key()));
            compound.putInt("sourceFacing", source.value().ordinal());
        }
        if (target != null) {
            compound.put("targetPos", NbtUtils.writeBlockPos(target.key()));
            compound.putInt("targetFacing", target.value().ordinal());
        }
        return compound;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag compound) {
        Optional<BlockPos> src = NbtUtils.readBlockPos(compound, "sourcePos");
        src.ifPresent(blockPos -> this.setSource(blockPos, Direction.from3DDataValue((compound.getInt("sourceFacing")))));
        Optional<BlockPos> target = NbtUtils.readBlockPos(compound, "targetPos");
        target.ifPresent(blockPos -> this.setTarget(blockPos, Direction.from3DDataValue((compound.getInt("targetFacing")))));
    }
}