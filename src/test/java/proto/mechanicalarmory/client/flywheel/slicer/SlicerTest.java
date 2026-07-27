package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class SlicerTest {
    
    public static void main(String[] args) {
        try {
            Class<?>[] testClasses = new Class<?>[] {
                net.minecraft.client.renderer.blockentity.ChestRenderer.class,
                net.minecraft.client.renderer.blockentity.BedRenderer.class,
                net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer.class,
                net.minecraft.client.renderer.blockentity.SignRenderer.class,
                net.minecraft.client.renderer.blockentity.HangingSignRenderer.class,
                net.minecraft.client.renderer.blockentity.BannerRenderer.class,
                net.minecraft.client.renderer.blockentity.SkullBlockRenderer.class,
                net.minecraft.client.renderer.blockentity.ConduitRenderer.class,
                net.minecraft.client.renderer.blockentity.BeaconRenderer.class,
                net.minecraft.client.renderer.blockentity.EnchantTableRenderer.class,
                net.minecraft.client.renderer.blockentity.LecternRenderer.class,
                net.minecraft.client.renderer.blockentity.BellRenderer.class,
                net.minecraft.client.renderer.blockentity.CampfireRenderer.class,
                net.minecraft.client.renderer.blockentity.PistonHeadRenderer.class,
                net.minecraft.client.renderer.blockentity.StructureBlockRenderer.class,
                net.minecraft.client.renderer.blockentity.TheEndPortalRenderer.class,
                net.minecraft.client.renderer.blockentity.TheEndGatewayRenderer.class,
                net.minecraft.client.renderer.blockentity.DecoratedPotRenderer.class,
                net.minecraft.client.renderer.blockentity.BrushableBlockRenderer.class,
                net.minecraft.client.renderer.blockentity.TrialSpawnerRenderer.class,
                net.minecraft.client.renderer.blockentity.VaultRenderer.class
            };
            
            for (Class<?> rendererClass : testClasses) {
                System.out.println("\n==================================================");
                System.out.println("Starting Bytecode Slicer Test on: " + rendererClass.getSimpleName());
                System.out.println("==================================================");
                
                ClassNode cn = RendererAnalyzer.loadClassNode(rendererClass);
                System.out.println("Loaded ClassNode for: " + cn.name);
                
                MethodNode targetMethod = null;
                for (MethodNode mn : cn.methods) {
                    if (mn.name.equals("render") && mn.desc.contains("MultiBufferSource")) {
                        targetMethod = mn;
                        break;
                    }
                }
                
                if (targetMethod == null) {
                    System.out.println("Could not find the public render method in " + rendererClass.getSimpleName());
                    continue;
                }
                MethodInliner.inlineLocalMethods(cn, targetMethod);

                PartPoseConfig poseConfig = PartPoseExtractor.extract(cn);
                System.out.println("Extracted Pose Config: " + poseConfig);

                BytecodeDualSlicer.SliceResult result = BytecodeDualSlicer.slice(cn.name, targetMethod);
                
                System.out.println("Capture slice size: " + result.captureSlice.size() + " instructions");
                System.out.println("Animation slice size: " + result.animationSlice.size() + " instructions");

                
                byte[] bytes = RuntimeVisualGenerator.generateVisualClass(cn, result);
                System.out.println("Generated visual class bytes: " + bytes.length);
                
                class TestClassLoader extends ClassLoader {
                    TestClassLoader() { super(SlicerTest.class.getClassLoader()); }
                    public Class<?> load(String name, byte[] b) {
                        return defineClass(name, b, 0, b.length);
                    }
                }
                TestClassLoader loader = new TestClassLoader();
                String generatedName = cn.name.replace('/', '_') + "_FlywheelVisual";
                Class<?> clazz = loader.load(generatedName, bytes);
                clazz.getDeclaredConstructors();
                clazz.getDeclaredMethods();
                System.out.println("Successfully loaded and verified generated class in JVM: " + clazz.getName());
                java.lang.reflect.Method m = clazz.getDeclaredMethod("createModelTree", net.minecraft.client.model.geom.ModelLayerLocation.class, net.minecraft.world.level.block.entity.BlockEntity.class);
                System.out.println("Successfully verified createModelTree method: " + m.getName());
            }
            
            System.out.println("\nAll renderer tests completed successfully!");
            
        } catch (Exception e) {
            e.printStackTrace();
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
