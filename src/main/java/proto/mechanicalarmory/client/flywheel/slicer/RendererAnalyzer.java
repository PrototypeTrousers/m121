package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class RendererAnalyzer {

    public static ClassNode loadClassNode(Class<?> clazz) throws IOException {
        return loadClassNode(clazz.getName().replace('.', '/'));
    }

    public static ClassNode loadClassNode(String internalName) throws IOException {
        String cleanName = internalName.replace('.', '/');
        String resource = "/" + cleanName + ".class";
        InputStream is = RendererAnalyzer.class.getResourceAsStream(resource);
        if (is == null) {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(cleanName + ".class");
        }
        if (is == null) {
            throw new IOException("Cannot find class resource for " + cleanName);
        }
        
        ClassReader cr = new ClassReader(is);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        return cn;
    }

    public static Optional<MethodNode> findMethod(ClassNode cn, String methodName, String methodDesc) {
        ClassNode curr = cn;
        while (curr != null && !"java/lang/Object".equals(curr.name)) {
            Optional<MethodNode> res = curr.methods.stream()
                    .filter(m -> m.name.equals(methodName) && m.desc.equals(methodDesc))
                    .findFirst();
            if (res.isPresent()) return res;
            if (curr.superName == null || "java/lang/Object".equals(curr.superName)) break;
            try {
                curr = loadClassNode(curr.superName);
            } catch (Exception e) {
                break;
            }
        }
        return Optional.empty();
    }

    public static Optional<MethodNode> findRenderMethod(ClassNode cn) {
        return cn.methods.stream()
                .filter(m -> "render".equals(m.name)
                        && (m.access & (org.objectweb.asm.Opcodes.ACC_BRIDGE | org.objectweb.asm.Opcodes.ACC_SYNTHETIC)) == 0
                        && m.desc.endsWith(";FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
                .findFirst();
    }

    public static Optional<MethodNode> findSetupAnimMethod(ClassNode cn) {
        ClassNode curr = cn;
        while (curr != null && !"java/lang/Object".equals(curr.name)) {
            Optional<MethodNode> res = curr.methods.stream()
                    .filter(m -> "setupAnim".equals(m.name)
                            && (m.access & (org.objectweb.asm.Opcodes.ACC_BRIDGE | org.objectweb.asm.Opcodes.ACC_SYNTHETIC)) == 0
                            && m.desc.endsWith(";FFFFF)V"))
                    .findFirst();
            if (res.isPresent()) return res;
            if (curr.superName == null || "java/lang/Object".equals(curr.superName)) break;
            try {
                curr = loadClassNode(curr.superName);
            } catch (Exception e) {
                break;
            }
        }
        return Optional.empty();
    }

    public static Optional<String> findModelClassName(ClassNode rendererClassNode) {
        for (MethodNode mn : rendererClassNode.methods) {
            if ("<init>".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == org.objectweb.asm.Opcodes.NEW) {
                        TypeInsnNode tin = (TypeInsnNode) insn;
                        if (tin.desc.endsWith("Model") || tin.desc.contains("/model/")) {
                            return Optional.of(tin.desc);
                        }
                    }
                }
            }
        }
        for (MethodNode mn : rendererClassNode.methods) {
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn.getOpcode() == org.objectweb.asm.Opcodes.NEW) {
                    TypeInsnNode tin = (TypeInsnNode) insn;
                    if (tin.desc.endsWith("Model") || tin.desc.contains("/model/")) {
                        return Optional.of(tin.desc);
                    }
                }
            }
        }
        if (rendererClassNode.superName != null && (rendererClassNode.superName.endsWith("Model") || rendererClassNode.superName.contains("/model/"))) {
            return Optional.of(rendererClassNode.superName);
        }
        return Optional.empty();
    }

    public static Optional<FieldInsnNode> findModelLayerLocationField(ClassNode rendererClassNode) {
        for (MethodNode mn : rendererClassNode.methods) {
            if ("<init>".equals(mn.name)) {
                for (AbstractInsnNode insn : mn.instructions) {
                    if (insn.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC) {
                        FieldInsnNode fin = (FieldInsnNode) insn;
                        if ("Lnet/minecraft/client/model/geom/ModelLayerLocation;".equals(fin.desc)) {
                            return Optional.of(fin);
                        }
                    }
                }
            }
        }
        for (MethodNode mn : rendererClassNode.methods) {
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn.getOpcode() == org.objectweb.asm.Opcodes.GETSTATIC) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if ("Lnet/minecraft/client/model/geom/ModelLayerLocation;".equals(fin.desc)) {
                        return Optional.of(fin);
                    }
                }
            }
        }
        return Optional.empty();
    }
}
