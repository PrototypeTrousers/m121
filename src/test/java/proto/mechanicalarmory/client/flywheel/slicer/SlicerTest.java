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
            System.out.println("Starting Bytecode Slicer Test on ChestRenderer...");
            
            // We use the class name to load it from the classpath
            // Note: In a dev environment, the class name might be different depending on mappings, 
            // but we'll try the standard mapped name.
            ClassNode cn = RendererAnalyzer.loadClassNode(net.minecraft.client.renderer.blockentity.ChestRenderer.class);
            System.out.println("Loaded ClassNode for: " + cn.name);
            
            // Find the private render helper method
            // Signature: (Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;FII)V
            MethodNode targetMethod = null;
            for (MethodNode mn : cn.methods) {
                if (mn.name.equals("render") && mn.desc.contains("FII)V")) {
                    targetMethod = mn;
                    break;
                }
            }
            
            if (targetMethod == null) {
                System.out.println("Could not find the private render method in ChestRenderer.");
                return;
            }
            
            System.out.println("Found target method: " + targetMethod.name + " " + targetMethod.desc);
            
            // Run the dual slicer
            BytecodeDualSlicer.SliceResult result = BytecodeDualSlicer.slice(cn.name, targetMethod);
            
            System.out.println("\n--- CAPTURE SLICE (" + result.captureSlice.size() + " instructions) ---");
            printInstructions(result.captureSlice);
            
            System.out.println("\n--- ANIMATION SLICE (" + result.animationSlice.size() + " instructions) ---");
            printInstructions(result.animationSlice);
            
            System.out.println("\nTest completed successfully!");
            
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
