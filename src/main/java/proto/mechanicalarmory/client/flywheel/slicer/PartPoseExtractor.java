package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public class PartPoseExtractor {

    public static PartPoseConfig extract(ClassNode classNode) {
        PartPoseConfig config = new PartPoseConfig();

        for (MethodNode mn : classNode.methods) {
            for (int i = 0; i < mn.instructions.size(); i++) {
                AbstractInsnNode insn = mn.instructions.get(i);

                // 1. Check for Axis rotations (e.g. Axis.XP.rotationDegrees(90.0F) or Axis.ZP.rotationDegrees(180.0F))
                if (insn.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if ("com/mojang/math/Axis".equals(fin.owner)) {
                        // Look ahead for LDC float constant
                        Float angle = findNextFloatConstant(mn.instructions, i, 10);
                        if (angle != null) {
                            if ("XP".equals(fin.name)) {
                                config.xRot = angle;
                                config.customPose = true;
                            } else if ("ZP".equals(fin.name)) {
                                config.zRotOffset = angle;
                                config.customPose = true;
                            }
                        }
                    }
                }

                // 2. Check for PoseStack.translate(FFF)V to find Y offset (height)
                if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if ("com/mojang/blaze3d/vertex/PoseStack".equals(min.owner) && "translate".equals(min.name) && "(FFF)V".equals(min.desc)) {
                        // Scan backwards up to 15 instructions before translate to find float constants
                        for (int j = Math.max(0, i - 15); j < i; j++) {
                            AbstractInsnNode prev = mn.instructions.get(j);
                            Float val = getFloatConstant(prev);
                            if (val != null) {
                                // In block entity renderers, height translations are strictly between 0.0 and 1.0 (excluding 0.5 center)
                                if (val > 0.0F && val < 1.0F && val != 0.5F) {
                                    config.yOffset = val;
                                    config.customPose = true;
                                }
                            }
                        }
                    }
                }
            }
        }

        return config;
    }

    private static Float findNextFloatConstant(InsnList insns, int start, int window) {
        int end = Math.min(insns.size(), start + window);
        for (int i = start; i < end; i++) {
            Float val = getFloatConstant(insns.get(i));
            if (val != null) return val;
        }
        return null;
    }

    private static Float getFloatConstant(AbstractInsnNode insn) {
        if (insn.getOpcode() == Opcodes.LDC) {
            LdcInsnNode ldc = (LdcInsnNode) insn;
            if (ldc.cst instanceof Float f) {
                return f;
            }
        } else if (insn.getOpcode() == Opcodes.FCONST_0) {
            return 0.0F;
        } else if (insn.getOpcode() == Opcodes.FCONST_1) {
            return 1.0F;
        } else if (insn.getOpcode() == Opcodes.FCONST_2) {
            return 2.0F;
        }
        return null;
    }
}
