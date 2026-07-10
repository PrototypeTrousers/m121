package proto.mechanicalarmory.common.integration.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public class MAEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        //registry.addDragDropHandler(ArmScreen.class, new ItemContextFilter.ItemContextFilterEmiDragDropHandler());
    }
}
