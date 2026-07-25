package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;

import java.util.List;

public class ModelPartInterpreter extends BasicInterpreter {

    private static final Type MODEL_PART_TYPE = Type.getObjectType("net/minecraft/client/model/geom/ModelPart");

    public ModelPartInterpreter() {
        super(Opcodes.ASM9);
    }

    @Override
    public BasicValue newValue(Type type) {
        if (type != null && type.equals(MODEL_PART_TYPE)) {
            // Unnamed model part value
            return new ModelPartValue(type, "unknown");
        }
        return super.newValue(type);
    }

    @Override
    public BasicValue naryOperation(AbstractInsnNode insn, List<? extends BasicValue> values) throws AnalyzerException {
        if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
            MethodInsnNode min = (MethodInsnNode) insn;
            if (min.owner.equals(MODEL_PART_TYPE.getInternalName()) && min.name.equals("getChild")) {
                // The arguments to getChild are [ModelPart, String]
                BasicValue target = values.get(0);
                BasicValue arg = values.get(1);
                
                String parentName = "unknown";
                if (target instanceof ModelPartValue) {
                    parentName = ((ModelPartValue) target).getPartName();
                }
                
                String childName = "?";
                // We don't have the constant value directly here unless we track LDC in a more advanced way
                // But this demonstrates intercepting the method call.
                return new ModelPartValue(MODEL_PART_TYPE, parentName + "/" + childName);
            }
        }
        return super.naryOperation(insn, values);
    }
}
