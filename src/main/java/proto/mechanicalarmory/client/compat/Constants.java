package proto.mechanicalarmory.client.compat;

import net.neoforged.fml.ModList;

public class Constants {
    public static final boolean isIrisLoaded = ModList.get().isLoaded("iris");
    public static final boolean isImmediatelyfastLoaded = ModList.get().isLoaded("immediatelyfast");
}
