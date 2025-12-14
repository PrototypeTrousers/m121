package proto.mechanicalarmory;

import com.google.common.base.Stopwatch;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import proto.mechanicalarmory.client.gltf.GltfFlywheelModel;
import proto.mechanicalarmory.client.instances.ArmVisual;
import proto.mechanicalarmory.client.instances.ArmVisualiser;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Optional;

import static proto.mechanicalarmory.MechanicalArmory.MODID;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
public class MechanicalArmoryClient {
    public static ModelResourceLocation arm = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MODID, "models/fullarm.glb"));
    public static GltfFlywheelModel gltfFlywheelModel;
    public MechanicalArmoryClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VisualizerRegistry.setVisualizer(MAEntities.ARM_ENTITY.get(), ArmVisualiser.ARM_VISUAL);
        // Some client setup code
        MechanicalArmory.LOGGER.info("HELLO FROM CLIENT SETUP");
        MechanicalArmory.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent // on the mod event bus only on the physical client
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(arm);
        loadglTFModel(arm);
        gltfFlywheelModel = new GltfFlywheelModel(loadglTFModel(arm));
    }

    public static GltfModel loadglTFModel(ModelResourceLocation modelResourceLocation) {
        GltfModel g = null;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(modelResourceLocation.id());
            Resource re = resource.orElseThrow(() -> new RuntimeException("Resource not found"));
            g = (new GltfModelReader()).readWithoutReferences(new BufferedInputStream(re.open()));
            g.getSkinModels();
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopwatch.stop();
        MechanicalArmory.LOGGER.info("Loaded glTF model in {} ms", stopwatch.elapsed().toMillis());
        return g;
    }
}
