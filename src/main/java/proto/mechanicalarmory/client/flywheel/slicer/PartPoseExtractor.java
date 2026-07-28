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

                    // 3. Detect poseStack.mulPose(direction.getRotation()) pattern.
                    //    The value pushed onto the stack just before mulPose must come from Direction.getRotation().
                    //    We detect this by looking for an INVOKEVIRTUAL Direction.getRotation()
                    //    or INVOKESTATIC Quaternionf.fromYXZ... appearing within a short window before mulPose.
                    if ("com/mojang/blaze3d/vertex/PoseStack".equals(min.owner) && "mulPose".equals(min.name)) {
                        // Scan backwards for a Direction.getRotation() call
                        for (int j = Math.max(0, i - 12); j < i; j++) {
                            AbstractInsnNode prev = mn.instructions.get(j);
                            if (prev instanceof MethodInsnNode prevMin) {
                                if ("net/minecraft/core/Direction".equals(prevMin.owner) && "getRotation".equals(prevMin.name)) {
                                    config.usesFacingRotation = true;
                                    config.customPose = true;
                                    break;
                                }
                            }
                        }
                    }

                    // 4. Detect poseStack.scale(1, -1, -1) — vertical flip (ShulkerBox-style).
                    //    Check if the three float args are 1.0, -1.0, -1.0.
                    if ("com/mojang/blaze3d/vertex/PoseStack".equals(min.owner) && "scale".equals(min.name) && "(FFF)V".equals(min.desc)) {
                        // Scan at most 6 instructions back for the three float constants
                        Float[] scaleArgs = collectLastThreeFloats(mn.instructions, i, 8);
                        if (scaleArgs != null
                                && scaleArgs[0] != null && scaleArgs[0] == 1.0F
                                && scaleArgs[1] != null && scaleArgs[1] == -1.0F
                                && scaleArgs[2] != null && scaleArgs[2] == -1.0F) {
                            config.verticallyFlipped = true;
                            config.customPose = true;
                        }
                    }
                }

                // 5. Detect the post-rotation translate(0, -Y, 0) pattern that appears after mulPose/scale flip.
                //    In ShulkerBox: translate(0.0F, -1.0F, 0.0F)
                if (config.usesFacingRotation && insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if ("com/mojang/blaze3d/vertex/PoseStack".equals(min.owner) && "translate".equals(min.name) && "(FFF)V".equals(min.desc)) {
                        Float[] tArgs = collectLastThreeFloats(mn.instructions, i, 8);
                        if (tArgs != null && tArgs[0] != null && tArgs[0] == 0.0F
                                && tArgs[1] != null && tArgs[1] < 0.0F
                                && tArgs[2] != null && tArgs[2] == 0.0F) {
                            config.postRotationYOffset = tArgs[1]; // e.g. -1.0F
                        }
                    }
                }
            }
        }

        return config;
    }

    /**
     * Collect the last three float-constant instructions before index {@code before} within {@code window} instructions.
     * Returns a Float[3] where index 0 = first encountered (leftmost in source), or null if fewer than 3 were found.
     */
    private static Float[] collectLastThreeFloats(InsnList insns, int before, int window) {
        java.util.ArrayList<Float> found = new java.util.ArrayList<>();
        for (int i = Math.max(0, before - window); i < before; i++) {
            Float f = getFloatConstant(insns.get(i));
            if (f != null) found.add(f);
        }
        if (found.size() < 3) return null;
        // Take the last 3 in order
        int n = found.size();
        return new Float[]{found.get(n - 3), found.get(n - 2), found.get(n - 1)};
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
