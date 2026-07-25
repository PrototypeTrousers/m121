package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import proto.mechanicalarmory.client.mixin.BlockEntityRenderDispatcherAccessor;
import proto.mechanicalarmory.client.mixin.BlockEntityRenderersAccessor;

import java.util.HashMap;
import java.util.Map;

public class AutomatedVisualRegistry {

    public static final Map<BlockEntityType<?>, Class<?>> GENERATED_VISUALS = new HashMap<>();
    private static final Map<Class<?>, Class<?>> RENDERER_CLASS_TO_VISUAL = new HashMap<>();
    private static VisualLoader loader;

    public static Class<?> getRendererClass(BlockEntityType<?> type) {
        try {
            BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            if (dispatcher != null) {
                Map<BlockEntityType<?>, BlockEntityRenderer<?>> renderers = ((BlockEntityRenderDispatcherAccessor) dispatcher).getRenderers();
                if (renderers != null && renderers.containsKey(type)) {
                    BlockEntityRenderer<?> r = renderers.get(type);
                    if (r != null) return r.getClass();
                }
            }
        } catch (Exception ignored) {}

        try {
            Map<BlockEntityType<?>, BlockEntityRendererProvider<?>> providers = BlockEntityRenderersAccessor.getProviders();
            if (providers != null && providers.containsKey(type)) {
                BlockEntityRendererProvider<?> provider = providers.get(type);
                if (provider == null) return null;

                BlockEntityRenderDispatcher dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
                if (dispatcher == null) return null;
                BlockEntityRenderDispatcherAccessor acc = (BlockEntityRenderDispatcherAccessor) dispatcher;

                BlockEntityRendererProvider.Context context = new BlockEntityRendererProvider.Context(
                        dispatcher,
                        acc.getBlockRenderDispatcher().get(),
                        acc.getItemRenderer().get(),
                        acc.getEntityRenderer().get(),
                        acc.getEntityModelSet(),
                        acc.getFont()
                );
                BlockEntityRenderer<?> r = provider.create(context);
                if (r != null) return r.getClass();
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static void generateAndMapAll() {
        BuiltInRegistries.BLOCK_ENTITY_TYPE.forEach(AutomatedVisualRegistry::generateAndMap);
    }

    public static void generateAndMap(BlockEntityType<?> type) {
        Class<?> rendererClass = getRendererClass(type);
        if (rendererClass != null) {
            generateAndMap(type, rendererClass);
        } else {
            System.out.println("[Flywheel Slicer] Skipping " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type) + " (no custom 3D renderer registered)");
        }
    }

    public static void generateAndMap(BlockEntityType<?> type, Class<?> rendererClass) {
        try {
            ClassNode classNode = RendererAnalyzer.loadClassNode(rendererClass);
            MethodNode renderMethod = RendererAnalyzer.findRenderMethod(classNode)
                    .orElseThrow(() -> new NoSuchMethodException("Could not find non-synthetic render method in " + rendererClass.getName()));
            generateAndMap(type, rendererClass, renderMethod.name, renderMethod.desc);
        } catch (Exception e) {
            System.err.println("[Flywheel Slicer] Failed to generate visual for " + rendererClass.getSimpleName());
            e.printStackTrace();
        }
    }

    /**
     * Call this during client setup to generate and map a vanilla renderer to Flywheel.
     */
    public static void generateAndMap(BlockEntityType<?> type, Class<?> rendererClass, String targetMethodName, String targetMethodDesc) {
        try {
            if (loader == null) {
                loader = new VisualLoader(Thread.currentThread().getContextClassLoader());
            }

            // If we have already generated and loaded a Flywheel visual for this exact renderer class, reuse it immediately!
            if (RENDERER_CLASS_TO_VISUAL.containsKey(rendererClass)) {
                Class<?> existingClass = RENDERER_CLASS_TO_VISUAL.get(rendererClass);
                GENERATED_VISUALS.put(type, existingClass);
                System.out.println("[Flywheel Slicer] Reusing already generated visual for " + rendererClass.getSimpleName() + " on " + BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type));
                return;
            }

            ClassNode classNode = RendererAnalyzer.loadClassNode(rendererClass);
            MethodNode renderMethod = RendererAnalyzer.findMethod(classNode, targetMethodName, targetMethodDesc).orElseThrow(() -> new NoSuchMethodException(targetMethodName));

            // Inline all private helper methods so the slicer sees the full graph
            MethodInliner.inlineLocalMethods(classNode, renderMethod);

            PartPoseConfig poseConfig = PartPoseExtractor.extract(classNode);
            PoseHelper.registerPoseConfig(type, poseConfig);
            System.out.println("[Flywheel Slicer] Extracted pose config for " + rendererClass.getSimpleName() + ": " + poseConfig);

            BytecodeDualSlicer.SliceResult slices = BytecodeDualSlicer.slice(classNode.name, renderMethod);

            String generatedName = classNode.name.replace('/', '_') + "_FlywheelVisual";
            byte[] classBytes = RuntimeVisualGenerator.generateVisualClass(classNode, slices);

            // DUMP THE CLASS TO DISK FOR USER INSPECTION
            try {
                java.nio.file.Path dumpPath = java.nio.file.Paths.get("run", generatedName + ".class");
                java.nio.file.Files.createDirectories(dumpPath.getParent());
                java.nio.file.Files.write(dumpPath, classBytes);
                System.out.println("[Flywheel Slicer] Dumped generated class to " + dumpPath.toAbsolutePath());
            } catch (Exception e) {
                System.err.println("[Flywheel Slicer] Failed to dump class to disk: " + e.getMessage());
            }

            Class<?> generatedClass = loader.loadGeneratedClass(generatedName, classBytes);
            RENDERER_CLASS_TO_VISUAL.put(rendererClass, generatedClass);
            GENERATED_VISUALS.put(type, generatedClass);
            
            System.out.println("[Flywheel Slicer] Successfully mapped " + rendererClass.getSimpleName() + " for Flywheel integration.");
        } catch (Exception e) {
            System.err.println("[Flywheel Slicer] Failed to generate visual for " + rendererClass.getSimpleName());
            e.printStackTrace();
        }
    }

    /**
     * Loops through the generated map and binds them to Flywheel.
     * Flywheel automatically skips the vanilla renderer when it detects a visual is present (in most configs).
     */
    @SuppressWarnings("unchecked")
    public static void registerAllToFlywheel() {
        for (Map.Entry<BlockEntityType<?>, Class<?>> entry : GENERATED_VISUALS.entrySet()) {
            BlockEntityType<?> type = entry.getKey();
            Class<?> visualClass = entry.getValue();

            VisualizerRegistry.setVisualizer((BlockEntityType) type, new dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer<BlockEntity>() {
                @Override
                public BlockEntityVisual<? super BlockEntity> createVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick) {
                    try {
                        return (BlockEntityVisual<? super BlockEntity>) visualClass
                                .getConstructor(VisualizationContext.class, BlockEntity.class, float.class)
                                .newInstance(ctx, blockEntity, partialTick);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to instantiate generated visual for " + type, e);
                    }
                }
                
                @Override
                public boolean skipVanillaRender(BlockEntity blockEntity) {
                    return true;
                }
            });
        }
        System.out.println("[Flywheel Slicer] Registered " + GENERATED_VISUALS.size() + " automated visuals to Flywheel.");
    }
}
