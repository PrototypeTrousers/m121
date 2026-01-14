package proto.mechanicalarmory.common.logic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.common.util.INBTSerializable;

public class WorkStatus implements INBTSerializable<CompoundTag> {
    private ActionTypes type;
    private Action action;

    public WorkStatus() {
        this.type = ActionTypes.IDLING;
        this.action = Action.IDLING;
    }

    public void idle() {
        this.type = ActionTypes.IDLING;
        this.action = Action.IDLING;
    }

    public ActionTypes getType() {
        return type;
    }

    public Action getAction() {
        return action;
    }

    public Action setAction(Action value) {
        this.action = value;
        return value;
    }

    public ActionTypes setType(ActionTypes value) {
        this.type = value;
        return type;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag compound = new CompoundTag();
        compound.putString("type", type.name());
        compound.putString("action", action.name());
        return compound;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        this.type = ActionTypes.valueOf(nbt.getString("type"));
        this.action = Action.valueOf(nbt.getString("action"));
    }
}