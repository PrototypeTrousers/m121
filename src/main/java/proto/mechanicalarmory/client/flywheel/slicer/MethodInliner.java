package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MethodInliner {

    public static void inlineLocalMethods(ClassNode classNode, MethodNode targetMethod) {
        java.util.Set<String> callStack = new java.util.HashSet<>();
        callStack.add(classNode.name + "#" + targetMethod.name + targetMethod.desc);
        inlineRecursive(classNode, classNode, targetMethod, callStack, 0);
    }

    private static void inlineRecursive(ClassNode rootClassNode, ClassNode currentClassNode, MethodNode hostMethod, java.util.Set<String> callStack, int depth) {
        if (depth > 5) {
            return; // Prevent runaway inlining depth in deep or cyclic helper chains
        }

        java.util.List<MethodInsnNode> toInline = new java.util.ArrayList<>();
        for (AbstractInsnNode insn : hostMethod.instructions) {
            if (insn instanceof MethodInsnNode min) {
                if (!min.name.equals("<init>") && !min.name.equals("<clinit>") && !min.owner.equals("net/minecraft/client/model/geom/ModelPart")) {
                    if (min.owner.startsWith("net/minecraft/client/model/") || min.owner.startsWith("net/minecraft/client/renderer/") || min.owner.startsWith("proto/") || min.owner.equals(rootClassNode.name) || min.owner.equals(currentClassNode.name)) {
                        String sig = min.owner + "#" + min.name + min.desc;
                        if (!callStack.contains(sig)) {
                            toInline.add(min);
                        }
                    }
                }
            }
        }

        for (MethodInsnNode min : toInline) {
            ClassNode targetOwnerNode;
            try {
                targetOwnerNode = min.owner.equals(rootClassNode.name) ? rootClassNode : (min.owner.equals(currentClassNode.name) ? currentClassNode : RendererAnalyzer.loadClassNode(min.owner));
            } catch (Exception e) {
                continue;
            }
            MethodNode methodToInline = findConcreteMethodToInline(rootClassNode, targetOwnerNode, min.name, min.desc);
            if (methodToInline != null) {
                MethodNode copy = cloneMethod(methodToInline);
                java.util.Set<String> nextStack = new java.util.HashSet<>(callStack);
                nextStack.add(min.owner + "#" + min.name + min.desc);
                inlineRecursive(rootClassNode, targetOwnerNode, copy, nextStack, depth + 1);
                inlineMethodCall(hostMethod, min, copy);
            }
        }
    }

    private static MethodNode findConcreteMethodToInline(ClassNode currentClass, ClassNode targetOwnerClass, String name, String desc) {
        ClassNode curr = currentClass;
        while (curr != null) {
            for (MethodNode m : curr.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && m.instructions.size() > 0) {
                        return m;
                    }
                    break;
                }
            }
            if (curr.superName == null || "java/lang/Object".equals(curr.superName)) {
                break;
            }
            try {
                curr = curr.superName.equals(targetOwnerClass.name) ? targetOwnerClass : RendererAnalyzer.loadClassNode(curr.superName);
            } catch (Exception e) {
                break;
            }
        }

        curr = targetOwnerClass;
        while (curr != null) {
            for (MethodNode m : curr.methods) {
                if (m.name.equals(name) && m.desc.equals(desc)) {
                    if ((m.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0 && m.instructions.size() > 0) {
                        return m;
                    }
                    return null;
                }
            }
            if (curr.superName == null || "java/lang/Object".equals(curr.superName)) {
                break;
            }
            try {
                curr = RendererAnalyzer.loadClassNode(curr.superName);
            } catch (Exception e) {
                break;
            }
        }
        return null;
    }

    private static MethodNode cloneMethod(MethodNode source) {
        MethodNode copy = new MethodNode(source.access, source.name, source.desc, source.signature, source.exceptions != null ? source.exceptions.toArray(new String[0]) : null);
        source.accept(copy);
        return copy;
    }

    private static void inlineMethodCall(MethodNode hostMethod, MethodInsnNode methodCall, MethodNode methodToInline) {
        InsnList inlineBlock = new InsnList();
        Type[] argTypes = Type.getArgumentTypes(methodCall.desc);
        
        int offset = hostMethod.maxLocals;
        
        // 1. Pop arguments from stack into shifted local variables (in reverse order)
        for (int i = argTypes.length - 1; i >= 0; i--) {
            Type argType = argTypes[i];
            int localIndex = offset + getBaseLocalIndex(argTypes, i) + (methodCall.getOpcode() == Opcodes.INVOKESTATIC ? 0 : 1);
            inlineBlock.add(new VarInsnNode(argType.getOpcode(Opcodes.ISTORE), localIndex));
        }
        
        // 2. Pop 'this' if it's not static
        if (methodCall.getOpcode() != Opcodes.INVOKESTATIC) {
            inlineBlock.add(new VarInsnNode(Opcodes.ASTORE, offset));
        }
        
        LabelNode endLabel = new LabelNode();
        
        // 3. Clone instructions and shift local variables
        Map<LabelNode, LabelNode> labelMap = new HashMap<>();
        for (AbstractInsnNode insn : methodToInline.instructions) {
            if (insn instanceof LabelNode) {
                labelMap.put((LabelNode) insn, new LabelNode());
            }
        }
        
        for (AbstractInsnNode insn : methodToInline.instructions) {
            if (insn.getOpcode() >= Opcodes.IRETURN && insn.getOpcode() <= Opcodes.RETURN) {
                // Replace RETURN with GOTO endLabel
                inlineBlock.add(new JumpInsnNode(Opcodes.GOTO, endLabel));
            } else {
                AbstractInsnNode clone = insn.clone(labelMap);
                
                // Shift local variables
                if (clone instanceof VarInsnNode vin) {
                    vin.var += offset;
                } else if (clone instanceof IincInsnNode iin) {
                    iin.var += offset;
                }
                
                inlineBlock.add(clone);
            }
        }
        
        inlineBlock.add(endLabel);
        
        // 4. Insert the inline block and remove the original call
        hostMethod.instructions.insertBefore(methodCall, inlineBlock);
        hostMethod.instructions.remove(methodCall);
        
        // Update max locals and max stack
        hostMethod.maxLocals += methodToInline.maxLocals;
        hostMethod.maxStack += methodToInline.maxStack;
    }
    
    private static int getBaseLocalIndex(Type[] argTypes, int index) {
        int sum = 0;
        for (int i = 0; i < index; i++) {
            sum += argTypes[i].getSize();
        }
        return sum;
    }
}
