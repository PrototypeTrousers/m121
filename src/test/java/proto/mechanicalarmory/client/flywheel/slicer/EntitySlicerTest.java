package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class EntitySlicerTest {

    public static void main(String[] args) {
        try {
            Class<?>[] testClasses = new Class<?>[] {
                net.minecraft.client.renderer.entity.PigRenderer.class,
                net.minecraft.client.renderer.entity.CowRenderer.class,
                net.minecraft.client.renderer.entity.SheepRenderer.class,
                net.minecraft.client.renderer.entity.ChickenRenderer.class,
                net.minecraft.client.renderer.entity.CreeperRenderer.class,
                net.minecraft.client.renderer.entity.ZombieRenderer.class,
                net.minecraft.client.renderer.entity.SkeletonRenderer.class,
                net.minecraft.client.renderer.entity.SpiderRenderer.class,
                net.minecraft.client.renderer.entity.EndermanRenderer.class,
                net.minecraft.client.renderer.entity.SlimeRenderer.class,
                net.minecraft.client.renderer.entity.IronGolemRenderer.class,
                net.minecraft.client.renderer.entity.VillagerRenderer.class,
                net.minecraft.client.renderer.entity.ArmadilloRenderer.class,
                net.minecraft.client.renderer.entity.WardenRenderer.class,
                net.minecraft.client.renderer.entity.FrogRenderer.class,
                net.minecraft.client.renderer.entity.AxolotlRenderer.class,
                net.minecraft.client.renderer.entity.WolfRenderer.class,
                net.minecraft.client.renderer.entity.HorseRenderer.class,
                net.minecraft.client.renderer.entity.BreezeRenderer.class,
                net.minecraft.client.renderer.entity.BoggedRenderer.class
            };

            for (Class<?> rendererClass : testClasses) {
                System.out.println("\n==================================================");
                System.out.println("Starting Entity Bytecode Slicer Test on: " + rendererClass.getSimpleName());
                System.out.println("==================================================");

                ClassNode cn = RendererAnalyzer.loadClassNode(rendererClass);
                System.out.println("Loaded Renderer ClassNode for: " + cn.name);

                String modelClassName = RendererAnalyzer.findModelClassName(cn).orElse(null);
                if (modelClassName == null) {
                    System.out.println("Could not find EntityModel class in " + rendererClass.getSimpleName());
                    continue;
                }
                System.out.println("Found EntityModel class: " + modelClassName);

                ClassNode modelNode = RendererAnalyzer.loadClassNode(modelClassName);
                MethodNode setupAnimMethod = RendererAnalyzer.findSetupAnimMethod(modelNode).orElse(null);
                if (setupAnimMethod == null) {
                    System.out.println("Could not find setupAnim method in " + modelClassName);
                    continue;
                }
                System.out.println("Found setupAnim method: " + setupAnimMethod.name + setupAnimMethod.desc);

                MethodInliner.inlineLocalMethods(modelNode, setupAnimMethod);

                FieldInsnNode layerField = RendererAnalyzer.findModelLayerLocationField(cn).orElse(null);
                System.out.println("Found ModelLayerLocation field: " + (layerField != null ? layerField.owner + "." + layerField.name : "null"));

                BytecodeDualSlicer.SliceResult result = BytecodeDualSlicer.slice(modelNode.name, setupAnimMethod);

                System.out.println("Capture slice size: " + result.captureSlice.size() + " instructions");
                System.out.println("Animation slice size: " + result.animationSlice.size() + " instructions");

                if (rendererClass.getSimpleName().equals("SheepRenderer")) {
                    System.out.println("--- SheepModel Animation Slice ---");
                    printInstructions(result.animationSlice);
                }

                byte[] bytes = RuntimeVisualGenerator.generateEntityVisualClass(modelNode, rendererClass, result, layerField);
                System.out.println("Generated entity visual class bytes: " + bytes.length);

                class TestClassLoader extends ClassLoader {
                    TestClassLoader() { super(EntitySlicerTest.class.getClassLoader()); }
                    public Class<?> load(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }
                TestClassLoader loader = new TestClassLoader();
                String generatedName = rendererClass.getName().replace('.', '_') + "_FlywheelVisual";
                Class<?> clazz = loader.load(generatedName, bytes);
                clazz.getDeclaredConstructors();
                clazz.getDeclaredMethods();
                System.out.println("Successfully loaded and verified generated entity visual class in JVM: " + clazz.getName());
                java.lang.reflect.Method m = clazz.getDeclaredMethod("createModelTree_" + layerField.name, net.minecraft.world.entity.Entity.class);
                System.out.println("Successfully verified createModelTree method on Entity visual: " + m.getName());
            }

            System.out.println("\nAll entity renderer tests completed successfully!");

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printInstructions(Iterable<AbstractInsnNode> insns) {
        Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        for (AbstractInsnNode insn : insns) {
            insn.accept(mp);
            StringWriter sw = new StringWriter();
            printer.print(new PrintWriter(sw));
            printer.getText().clear();
            System.out.print(sw.toString());
        }
    }
}
