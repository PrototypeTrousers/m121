package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.api.visual.BlockEntityVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizerRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

public class AutomatedVisualRegistry {

    public static final Map<BlockEntityType<?>, Class<?>> GENERATED_VISUALS = new HashMap<>();
    private static VisualLoader loader;

    /**
     * Call this during client setup to generate and map a vanilla renderer to Flywheel.
     */
    public static void generateAndMap(BlockEntityType<?> type, Class<?> rendererClass, String targetMethodName, String targetMethodDesc) {
        try {
            if (loader == null) {
                loader = new VisualLoader(Thread.currentThread().getContextClassLoader());
            }

            ClassNode classNode = RendererAnalyzer.loadClassNode(rendererClass);
            MethodNode renderMethod = RendererAnalyzer.findMethod(classNode, targetMethodName, targetMethodDesc).orElseThrow(() -> new NoSuchMethodException(targetMethodName));

            // Inline all private helper methods so the slicer sees the full graph
            MethodInliner.inlineLocalMethods(classNode, renderMethod);

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
