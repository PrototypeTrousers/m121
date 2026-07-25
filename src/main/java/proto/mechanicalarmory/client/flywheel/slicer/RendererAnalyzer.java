package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import java.io.IOException;
import java.util.Optional;

public class RendererAnalyzer {

    public static ClassNode loadClassNode(Class<?> clazz) throws IOException {
        String resource = "/" + clazz.getName().replace('.', '/') + ".class";
        var is = clazz.getResourceAsStream(resource);
        if (is == null) {
            throw new IOException("Cannot find class resource for " + clazz.getName());
        }
        
        ClassReader cr = new ClassReader(is);
        ClassNode cn = new ClassNode();
        // We need all details for analysis, so we don't skip code
        cr.accept(cn, 0);
        return cn;
    }

    public static Optional<MethodNode> findMethod(ClassNode cn, String methodName, String methodDesc) {
        return cn.methods.stream()
                .filter(m -> m.name.equals(methodName) && m.desc.equals(methodDesc))
                .findFirst();
    }

    public static Optional<MethodNode> findRenderMethod(ClassNode cn) {
        return cn.methods.stream()
                .filter(m -> "render".equals(m.name)
                        && (m.access & (org.objectweb.asm.Opcodes.ACC_BRIDGE | org.objectweb.asm.Opcodes.ACC_SYNTHETIC)) == 0
                        && m.desc.endsWith(";FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"))
                .findFirst();
    }
}
