package proto.mechanicalarmory.common.logic;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.joml.Vector3d;

public class Targeting implements INBTSerializable<CompoundTag> {

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
        BlockPos vecPos = sourcePos.offset(sourceFacing.getNormal());
        this.sourceVec = new Vector3d(vecPos.getX() + 0.5, vecPos.getY() + 0.5, vecPos.getZ() + 0.5);
    }

    public void setTarget(BlockPos targetPos, Direction targetFacing) {
        this.target = Pair.of(targetPos, targetFacing);
        BlockPos vecPos = targetPos.offset(targetFacing.getNormal());
        this.targetVec = new Vector3d(vecPos.getX() + 0.5, vecPos.getY() + 0.5, vecPos.getZ() + 0.5);
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
        setSource(NbtUtils.readBlockPos(compound, "sourcePos").get(), Direction.from3DDataValue((compound.getInt("sourceFacing"))));
        setTarget(NbtUtils.readBlockPos(compound, "targetPos").get(), Direction.from3DDataValue(compound.getInt("targetFacing")));
    }
}