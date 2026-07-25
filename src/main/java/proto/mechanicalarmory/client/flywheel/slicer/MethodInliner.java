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
        boolean modified = true;
        
        while (modified) {
            modified = false;
            
            for (AbstractInsnNode insn : targetMethod.instructions) {
                if (insn instanceof MethodInsnNode min) {
                    // Only inline private/helper methods from the same class.
                    // Make sure we don't infinitely recurse by skipping the exact same method (name + desc),
                    // but allow overloaded methods with the same name!
                    if (min.owner.equals(classNode.name) && !(min.name.equals(targetMethod.name) && min.desc.equals(targetMethod.desc))) {
                        MethodNode methodToInline = RendererAnalyzer.findMethod(classNode, min.name, min.desc).orElse(null);
                        
                        if (methodToInline != null) {
                            inlineMethodCall(targetMethod, min, methodToInline);
                            modified = true;
                            break; // Restart loop after modification because the instruction list changed
                        }
                    }
                }
            }
        }
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
