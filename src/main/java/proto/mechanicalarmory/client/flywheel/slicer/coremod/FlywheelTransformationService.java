package proto.mechanicalarmory.client.flywheel.slicer.coremod;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import java.util.List;
import java.util.Set;

public class FlywheelTransformationService implements ITransformationService {

    @Override
    public String name() {
        return "flywheel_bytecode_slicer";
    }

    @Override
    public void initialize(cpw.mods.modlauncher.api.IEnvironment environment) {
        System.out.println("[Flywheel Slicer] Transformation Service Initialized!");
    }

    @Override
    public void onLoad(cpw.mods.modlauncher.api.IEnvironment env, Set<String> otherServices) {
    }

    @Override
    public List<ITransformer<?>> transformers() {
        // Register our bytecode slicer transformer!
        return List.of(new FlywheelRendererTransformer());
    }
}
