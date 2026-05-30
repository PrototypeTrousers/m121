package proto.mechanicalarmory.client.flywheel.instances.arm;

import dev.engine_room.flywheel.api.model.Model;
import dev.engine_room.flywheel.api.instance.InstanceHandle;
import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.lib.instance.ColoredLitOverlayInstance;

public class InterpolatedInstance extends ColoredLitOverlayInstance {
    public int partIdx;
    public int parentIdx;
    public Model model;

    public InterpolatedInstance(InstanceType<? extends InterpolatedInstance> type, InstanceHandle handle) {
       super(type, handle);
    }
}