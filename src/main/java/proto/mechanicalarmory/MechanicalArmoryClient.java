package proto.mechanicalarmory;

import com.google.common.base.Stopwatch;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import dev.engine_room.flywheel.impl.visual.BandedPrimeLimiter;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.CarrotBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import proto.mechanicalarmory.client.flywheel.gltf.GltfFlywheelModelTree;
import proto.mechanicalarmory.client.flywheel.instances.arm.ArmVisualiser;
import proto.mechanicalarmory.client.flywheel.instances.crop.CropVisualiser;
import proto.mechanicalarmory.client.flywheel.instances.generic.VanillaBlockVisualiser;
import proto.mechanicalarmory.client.flywheel.instances.generic.VanillaEntityVisualiser;
import proto.mechanicalarmory.client.renderer.arm.ArmRenderer;
import proto.mechanicalarmory.client.renderer.arm.MyCustomItemBakedModel;
import proto.mechanicalarmory.client.renderer.arm.MyItemRenderer;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.items.MAItems;

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
    public static ModelResourceLocation octoarm = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MODID, "models/octoarm.glb"));
    public static ModelResourceLocation armItemModel = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MODID, "arm"));
    public static ModelResourceLocation chestplateItemModel = ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MODID, "my_chestplate"));
    public static ModelTree fullArmModelTree;
    public static ModelTree octoArmModelTree;
    public static BandedPrimeLimiter limiter = new BandedPrimeLimiter();

    public MechanicalArmoryClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onClientSetup(FMLClientSetupEvent event) {
        VisualizerRegistry.setVisualizer(MAEntities.ARM_ENTITY.get(), ArmVisualiser.ARM_VISUAL);
        VisualizerRegistry.setVisualizer(MAEntities.CROP_BLOCK_ENTITY.get(), CropVisualiser.CROP_VISUAL);
        BuiltInRegistries.BLOCK_ENTITY_TYPE.forEach(c -> {
            if (VisualizerRegistry.getVisualizer(c) == null) {
                VisualizerRegistry.setVisualizer(c, VanillaBlockVisualiser.VANILLA_BLOCK_VISUALISER);
            }
        });

        BuiltInRegistries.ENTITY_TYPE.forEach(c -> {
            if (c == EntityType.PLAYER) return;
            if (VisualizerRegistry.getVisualizer(c) == null) {
                VisualizerRegistry.setVisualizer(c, VanillaEntityVisualiser.VANILLA_ENTITY_VISUALISER);
            }
        });

        // Some client setup code
        MechanicalArmory.LOGGER.info("HELLO FROM CLIENT SETUP");
        MechanicalArmory.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent // on the mod event bus only on the physical client
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(MAEntities.ARM_ENTITY.get(),
                // Pass the context to an empty (default) constructor call
                ArmRenderer::new
        );
    }

    @SubscribeEvent // on the mod event bus only on the physical client
    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        event.register(arm);
        event.register(octoarm);
        fullArmModelTree = GltfFlywheelModelTree.create(loadglTFModel(arm));
        octoArmModelTree = GltfFlywheelModelTree.create(loadglTFModel(octoarm));
        event.register(armItemModel);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        // We replace whatever Minecraft thinks is there with our custom class
        event.getModels().put(ModelResourceLocation.inventory(armItemModel.id()), new MyCustomItemBakedModel());
        event.getModels().put(ModelResourceLocation.inventory(chestplateItemModel.id()), new MyCustomItemBakedModel());
    }

    @SubscribeEvent
    public static void onClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions itemExtensions = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return MyItemRenderer.INSTANCE; // Your BEWLR class
            }
        };
        event.registerItem(itemExtensions , MAItems.ARM_ITEM);
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        limiter.tick();
    }

    public static GltfModel loadglTFModel(ModelResourceLocation modelResourceLocation) {
        GltfModel g = null;
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(modelResourceLocation.id());
            Resource re = resource.orElseThrow(() -> new RuntimeException("Resource not found"));
            g = (new GltfModelReader()).readWithoutReferences(new BufferedInputStream(re.open()));
        } catch (IOException e) {
            e.printStackTrace();
        }
        stopwatch.stop();
        MechanicalArmory.LOGGER.info("Loaded glTF model in {} ms", stopwatch.elapsed().toMillis());
        return g;
    }
}
