package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import java.io.PrintWriter;

public class BytecodeDumper {
    public static void dump() {
        try {
            ClassNode cn = RendererAnalyzer.loadClassNode(ChestRenderer.class);
            MethodNode mn = RendererAnalyzer.findMethod(cn, "render", "(Lnet/minecraft/world/level/block/entity/BlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V").orElse(null);
            if (mn == null) {
                System.out.println("Could not find render method!");
                return;
            }
            Printer printer = new Textifier();
            TraceMethodVisitor mp = new TraceMethodVisitor(printer);
            for (AbstractInsnNode insn : mn.instructions) {
                insn.accept(mp);
            }
            java.io.StringWriter sw = new java.io.StringWriter();
            printer.print(new PrintWriter(sw));
            System.out.println("--- CHEST RENDERER BYTECODE ---");
            System.out.println(sw.toString());
            System.out.println("-------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
