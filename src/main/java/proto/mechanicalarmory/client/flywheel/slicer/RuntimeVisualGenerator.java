package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RuntimeVisualGenerator {

    /**
     * Generates the bytecode for a new Flywheel Visual class based on the sliced vanilla renderer.
     * @param originalClassName The internal name of the original renderer (e.g. "net/minecraft/client/renderer/blockentity/ChestRenderer")
     * @param slices The dual slice result containing the separated instructions.
     * @return The byte array representing the newly generated .class file.
     */
    private static ClassWriter createClassWriter(String generatedName, String superClassName) {
        return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                if (type1.equals(generatedName) || type2.equals(generatedName)) {
                    return superClassName;
                }
                if (type1.startsWith("proto/mechanicalarmory") || type2.startsWith("proto/mechanicalarmory")) {
                    return "java/lang/Object";
                }
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (Exception e) {
                    return "java/lang/Object";
                }
            }
        };
    }

    public static byte[] generateVisualClass(org.objectweb.asm.tree.ClassNode originalClassNode, BytecodeDualSlicer.SliceResult slices) {
        String originalClassName = originalClassNode.name;
        String generatedName = originalClassName.replace('/', '_') + "_FlywheelVisual";
        ClassWriter cw = createClassWriter(generatedName, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual");
        
        String beInternalName = "net/minecraft/world/level/block/entity/BlockEntity";
        for (MethodNode mn : originalClassNode.methods) {
            if (mn.name.equals("render") && (mn.desc.contains("MultiBufferSource") || mn.desc.contains("PoseStack"))) {
                org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(mn.desc);
                if (args.length > 0 && args[0].getSort() == org.objectweb.asm.Type.OBJECT) {
                    beInternalName = args[0].getInternalName();
                    break;
                }
            }
        }
        
        // public class GeneratedVisual extends AbstractBlockEntityVisual implements SimpleTickableVisual, SimpleDynamicVisual
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generatedName, null, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", new String[]{"dev/engine_room/flywheel/lib/visual/SimpleTickableVisual", "dev/engine_room/flywheel/lib/visual/SimpleDynamicVisual"});

        // 1. Generate Fields for Captured State
        for (Map.Entry<AbstractInsnNode, String> entry : slices.captureStateMap.entrySet()) {
            AbstractInsnNode insn = entry.getKey();
            String fieldName = entry.getValue();
            
            String desc = "F";
            if (insn instanceof MethodInsnNode min) {
                desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
            } else if (insn instanceof FieldInsnNode fin) {
                desc = fin.desc;
            }
            cw.visitField(Opcodes.ACC_PRIVATE, fieldName, desc, null, null).visitEnd();
        }

        // 1.5. Find all ModelPart GETFIELDs in the animation slice
        Set<String> dummyParts = new HashSet<>();
        for (AbstractInsnNode insn : slices.animationSlice) {
            if (insn instanceof FieldInsnNode fin && fin.desc.equals("Lnet/minecraft/client/model/geom/ModelPart;")) {
                dummyParts.add(fin.name);
            } else if (insn instanceof MethodInsnNode min && min.desc.endsWith("Lnet/minecraft/client/model/geom/ModelPart;")) {
                String partName = min.name.startsWith("get") && min.name.length() > 3 
                    ? Character.toLowerCase(min.name.charAt(3)) + min.name.substring(4) 
                    : min.name;
                dummyParts.add(partName);
            }
        }
        
        // 1.6 Parse original constructor to find layer and child keys
        MethodNode constructor = null;
        for (MethodNode mn : originalClassNode.methods) {
            if (mn.name.equals("<init>")) {
                constructor = mn;
                break;
            }
        }
        
        Map<String, String> fieldToChildName = new java.util.HashMap<>();
        Map<String, String> fieldToLayerField = new java.util.HashMap<>();
        Map<String, String> fieldToLayerOwner = new java.util.HashMap<>();
        Set<String> uniqueLayerFields = new java.util.LinkedHashSet<>();
        Map<String, String> layerFieldToOwner = new java.util.HashMap<>();

        if (constructor != null) {
            String lastString = null;
            String currentLayerOwner = null;
            String currentLayerField = null;
            for (AbstractInsnNode insn : constructor.instructions) {
                if (insn.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if (fin.desc.equals("Lnet/minecraft/client/model/geom/ModelLayerLocation;")) {
                        currentLayerOwner = fin.owner;
                        currentLayerField = fin.name;
                        uniqueLayerFields.add(currentLayerField);
                        layerFieldToOwner.put(currentLayerField, currentLayerOwner);
                    }
                } else if (insn.getOpcode() == Opcodes.LDC) {
                    org.objectweb.asm.tree.LdcInsnNode ldc = (org.objectweb.asm.tree.LdcInsnNode) insn;
                    if (ldc.cst instanceof String) {
                        lastString = (String) ldc.cst;
                    }
                } else if (insn.getOpcode() == Opcodes.PUTFIELD) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if (fin.desc.equals("Lnet/minecraft/client/model/geom/ModelPart;")) {
                        if (lastString != null && currentLayerField != null) {
                            fieldToChildName.put(fin.name, lastString);
                            fieldToLayerField.put(fin.name, currentLayerField);
                            fieldToLayerOwner.put(fin.name, currentLayerOwner);
                            lastString = null;
                        }
                    }
                }
            }
        }
        
        // Generate dummy ModelPart fields and InstanceTree fields
        for (String partName : dummyParts) {
            cw.visitField(Opcodes.ACC_PUBLIC, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;", null, null).visitEnd();
            cw.visitField(Opcodes.ACC_PRIVATE, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", null, null).visitEnd();
        }

        Set<String> generatedFields = new HashSet<>();
        ClassNode currNode = originalClassNode;
        while (currNode != null && !"java/lang/Object".equals(currNode.name)) {
            for (org.objectweb.asm.tree.FieldNode fn : currNode.fields) {
                if ((fn.access & Opcodes.ACC_STATIC) == 0 && !"Lnet/minecraft/client/model/geom/ModelPart;".equals(fn.desc)) {
                    if (generatedFields.add(fn.name)) {
                        cw.visitField(Opcodes.ACC_PUBLIC, fn.name, fn.desc, null, null).visitEnd();
                    }
                }
            }
            if (currNode.superName == null || "java/lang/Object".equals(currNode.superName)) break;
            try { currNode = RendererAnalyzer.loadClassNode(currNode.superName); } catch (Exception e) { break; }
        }

        // Generate rootTree fields and map
        for (String layerField : uniqueLayerFields) {
            cw.visitField(Opcodes.ACC_PRIVATE, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", null, null).visitEnd();
        }
        cw.visitField(Opcodes.ACC_PRIVATE, "rootTreesMap", "Ljava/util/Map;", null, null).visitEnd();

        // Generate the Constructor
        // public GeneratedVisual(VisualizationContext ctx, BlockEntity blockEntity, float partialTick)
        MethodVisitor mvInit = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)V", null, null);
        mvInit.visitCode();
        mvInit.visitVarInsn(Opcodes.ALOAD, 0); // this
        mvInit.visitVarInsn(Opcodes.ALOAD, 1); // ctx
        mvInit.visitVarInsn(Opcodes.ALOAD, 2); // blockEntity
        mvInit.visitVarInsn(Opcodes.FLOAD, 3); // partialTick
        mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "<init>", "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/level/block/entity/BlockEntity;F)V", false);
        
        for (String partName : dummyParts) {
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitTypeInsn(Opcodes.NEW, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart");
            mvInit.visitInsn(Opcodes.DUP);
            mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "<init>", "()V", false);
            mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
        }

        // Initialize rootTreesMap = new HashMap<>();
        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mvInit.visitInsn(Opcodes.DUP);
        mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");

        for (String layerField : uniqueLayerFields) {
            String owner = layerFieldToOwner.get(layerField);
            
            // Get ModelLayerLocation
            mvInit.visitFieldInsn(Opcodes.GETSTATIC, owner, layerField, "Lnet/minecraft/client/model/geom/ModelLayerLocation;");
            
            // Get blockEntity
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
            
            // generatedName.createModelTree(ModelLayerLocation, BlockEntity)
            mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, generatedName, "createModelTree", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
            
            // InstanceTree.create(instancerProvider, modelTree)
            mvInit.visitVarInsn(Opcodes.ALOAD, 0); 
            mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "instancerProvider", "()Ldev/engine_room/flywheel/api/instance/InstancerProvider;", false);
            mvInit.visitInsn(Opcodes.SWAP);
            mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "create", "(Ldev/engine_room/flywheel/api/instance/InstancerProvider;Ldev/engine_room/flywheel/lib/model/part/ModelTree;)Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", false);
            
            // PUTFIELD rootTree_<layerField>
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitInsn(Opcodes.SWAP);
            mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");

            // rootTreesMap.put(layerField, rootTree_<layerField>)
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");
            mvInit.visitLdcInsn(layerField);
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvInit.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            mvInit.visitInsn(Opcodes.POP);
        }

        // For each ModelPart field found, initialize a DummyModelPart and an InstanceTree
        for (String partName : dummyParts) {
            mvInit.visitVarInsn(Opcodes.ALOAD, 0); // this
            mvInit.visitTypeInsn(Opcodes.NEW, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart");
            mvInit.visitInsn(Opcodes.DUP);
            mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "<init>", "()V", false);
            mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
            
            String childName = fieldToChildName.get(partName);
            if (childName == null) childName = partName;
            String layerField = fieldToLayerField.get(partName);
            if (layerField == null && !uniqueLayerFields.isEmpty()) layerField = uniqueLayerFields.iterator().next();
            if (childName != null && layerField != null) {
                mvInit.visitVarInsn(Opcodes.ALOAD, 0); // this
                
                // rootTree_<layerField>.child(childName)
                mvInit.visitVarInsn(Opcodes.ALOAD, 0);
                mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvInit.visitLdcInsn(childName);
                mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "child", "(Ljava/lang/String;)Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", false);
                
                mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");

                // Copy initial pose from tree_part into dummy_part!
                mvInit.visitVarInsn(Opcodes.ALOAD, 0);
                mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvInit.visitVarInsn(Opcodes.ALOAD, 0);
                mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "copyFrom", "(Ldev/engine_room/flywheel/lib/model/part/InstanceTree;)V", false);
            }
        }
        
        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitVarInsn(Opcodes.FLOAD, 3);
        mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "updateLight", "(F)V", false);

        mvInit.visitInsn(Opcodes.RETURN);
        mvInit.visitMaxs(0, 0);
        mvInit.visitEnd();
        
        // 2. Generate tick() method (The Capture Slice)
        MethodVisitor mvTick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ldev/engine_room/flywheel/api/visual/TickableVisual$Context;)V", null, null);
        mvTick.visitCode();
        
        // Build fresh labels for the capture slice (same reason as beginFrame: stale Label objects)
        Map<LabelNode, Label> tickLabelMap = new HashMap<>();
        for (AbstractInsnNode insn : slices.captureSlice) {
            if (insn instanceof LabelNode ln) tickLabelMap.put(ln, new Label());
            else if (insn instanceof JumpInsnNode jin) tickLabelMap.computeIfAbsent(jin.label, k -> new Label());
            else if (insn instanceof TableSwitchInsnNode tsin) {
                tickLabelMap.computeIfAbsent(tsin.dflt, k -> new Label());
                for (LabelNode l : tsin.labels) tickLabelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LookupSwitchInsnNode lsin) {
                tickLabelMap.computeIfAbsent(lsin.dflt, k -> new Label());
                for (LabelNode l : lsin.labels) tickLabelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LineNumberNode lnn) {
                tickLabelMap.computeIfAbsent(lnn.start, k -> new Label());
            }
        }

        // Write all capture instructions in topological order
        for (AbstractInsnNode insn : slices.captureSlice) {
            if (insn.getType() == AbstractInsnNode.FRAME) continue;
            if (insn instanceof LineNumberNode lnn) {
                Label fresh = tickLabelMap.get(lnn.start);
                if (fresh != null) mvTick.visitLineNumber(lnn.line, fresh);
                continue;
            }
            if (insn instanceof LabelNode ln) {
                Label fresh = tickLabelMap.get(ln);
                if (fresh != null) mvTick.visitLabel(fresh);
                continue;
            }
            if (insn instanceof JumpInsnNode jin) {
                mvTick.visitJumpInsn(jin.getOpcode(), tickLabelMap.get(jin.label));
                continue;
            }
            if (insn instanceof TableSwitchInsnNode tsin) {
                Label[] freshLabels = tsin.labels.stream().map(l -> tickLabelMap.get(l)).toArray(Label[]::new);
                mvTick.visitTableSwitchInsn(tsin.min, tsin.max, tickLabelMap.get(tsin.dflt), freshLabels);
                continue;
            }
            if (insn instanceof LookupSwitchInsnNode lsin) {
                int[] keys = lsin.keys.stream().mapToInt(Integer::intValue).toArray();
                Label[] freshLabels = lsin.labels.stream().map(l -> tickLabelMap.get(l)).toArray(Label[]::new);
                mvTick.visitLookupSwitchInsn(tickLabelMap.get(lsin.dflt), keys, freshLabels);
                continue;
            }
            if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin) {
                if (vin.var == 1 && vin.getOpcode() == Opcodes.ALOAD) {
                    mvTick.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mvTick.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
                    if (!"net/minecraft/world/level/block/entity/BlockEntity".equals(beInternalName)) {
                        mvTick.visitTypeInsn(Opcodes.CHECKCAST, beInternalName);
                    }
                    continue; // Skip the original ALOAD 1
                }
                if (vin.var == 2 && vin.getOpcode() == Opcodes.FLOAD) {
                    mvTick.visitInsn(Opcodes.FCONST_0);
                    continue;
                }
            }

            insn.accept(mvTick);

            if (slices.captureStateMap.containsKey(insn)) {
                String fieldName = slices.captureStateMap.get(insn);
                String desc = "F";
                if (insn instanceof MethodInsnNode min) {
                    desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
                } else if (insn instanceof FieldInsnNode fin) {
                    desc = fin.desc;
                }
                
                boolean consumedInCaptureSlice = false;
                for (AbstractInsnNode consumer : slices.captureSlice) {
                    Set<AbstractInsnNode> deps = slices.dependencies.get(consumer);
                    if (deps != null && deps.contains(insn)) {
                        consumedInCaptureSlice = true;
                        break;
                    }
                }
                
                boolean isCat2 = desc.equals("J") || desc.equals("D");
                
                if (consumedInCaptureSlice) {
                    mvTick.visitInsn(isCat2 ? Opcodes.DUP2 : Opcodes.DUP);
                }
                
                mvTick.visitVarInsn(Opcodes.ALOAD, 0); // this
                if (isCat2) {
                    mvTick.visitInsn(Opcodes.DUP_X2);
                    mvTick.visitInsn(Opcodes.POP);
                } else {
                    mvTick.visitInsn(Opcodes.SWAP);
                }
                mvTick.visitFieldInsn(Opcodes.PUTFIELD, generatedName, fieldName, desc);
            }
        }
        mvTick.visitInsn(Opcodes.RETURN);
        mvTick.visitMaxs(0, 0);
        mvTick.visitEnd();

        // 3. Generate beginFrame() method (The Animation Slice)
        MethodVisitor mvUpdate = cw.visitMethod(Opcodes.ACC_PUBLIC, "beginFrame", "(Ldev/engine_room/flywheel/api/visual/DynamicVisual$Context;)V", null, null);
        mvUpdate.visitCode();
        
        // Update visibility of root trees based on blockState (e.g. single vs double chest)
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockState", "Lnet/minecraft/world/level/block/state/BlockState;");
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");
        mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "updateVisibility", "(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/Map;)V", false);

        for (String partName : dummyParts) {
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "resetPose", "()V", false);
        }

        Map<Integer, Integer> varMap = new HashMap<>();
        int nextVar = 2;
        for (AbstractInsnNode insn : slices.animationSlice) {
            int oldVar = -1;
            int typeSize = 1;
            if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin && vin.var >= 3) {
                oldVar = vin.var;
                if (vin.getOpcode() == Opcodes.LLOAD || vin.getOpcode() == Opcodes.DLOAD || vin.getOpcode() == Opcodes.LSTORE || vin.getOpcode() == Opcodes.DSTORE) {
                    typeSize = 2;
                }
            } else if (insn instanceof org.objectweb.asm.tree.IincInsnNode iin && iin.var >= 3) {
                oldVar = iin.var;
            }
            if (oldVar >= 3 && !varMap.containsKey(oldVar)) {
                varMap.put(oldVar, nextVar);
                nextVar += typeSize;
            }
        }

        // Build a fresh Label mapping so we never pass stale Label objects
        // (with pre-set bytecodeOffset/frame/outgoingEdges from the original ClassNode) into the
        // new MethodWriter. Reusing those stale Label instances corrupts ASM's CFG and triggers
        // Frame.merge ArrayIndexOutOfBoundsException during COMPUTE_FRAMES.
        Map<LabelNode, Label> labelMap = new HashMap<>();
        // sliceEnd: all RETURN opcodes in the animation slice jump here so that our
        // post-slice code (syncDummyToTree, propagateAnimation, updateLight) always runs.
        Label sliceEnd = new Label();

        for (AbstractInsnNode insn : slices.animationSlice) {
            if (insn instanceof LabelNode ln) {
                labelMap.put(ln, new Label());
            } else if (insn instanceof JumpInsnNode jin) {
                labelMap.computeIfAbsent(jin.label, k -> new Label());
            } else if (insn instanceof TableSwitchInsnNode tsin) {
                labelMap.computeIfAbsent(tsin.dflt, k -> new Label());
                for (LabelNode l : tsin.labels) labelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LookupSwitchInsnNode lsin) {
                labelMap.computeIfAbsent(lsin.dflt, k -> new Label());
                for (LabelNode l : lsin.labels) labelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LineNumberNode lnn) {
                labelMap.computeIfAbsent(lnn.start, k -> new Label());
            }
        }

        for (AbstractInsnNode insn : slices.animationSlice) {
            // Skip FrameNode — COMPUTE_FRAMES ignores visitFrame, but skipping avoids passing
            // stale frame data.
            if (insn.getType() == AbstractInsnNode.FRAME) continue;
            // Remap LineNumberNode to use fresh label
            if (insn instanceof LineNumberNode lnn) {
                mvUpdate.visitLineNumber(lnn.line, labelMap.get(lnn.start));
                continue;
            }
            // Remap LabelNode to use fresh label
            if (insn instanceof LabelNode ln) {
                mvUpdate.visitLabel(labelMap.get(ln));
                continue;
            }
            // Remap JumpInsnNode to use fresh target label
            if (insn instanceof JumpInsnNode jin) {
                mvUpdate.visitJumpInsn(jin.getOpcode(), labelMap.get(jin.label));
                continue;
            }
            // Remap TableSwitchInsnNode
            if (insn instanceof TableSwitchInsnNode tsin) {
                Label[] freshLabels = tsin.labels.stream().map(l -> labelMap.get(l)).toArray(Label[]::new);
                mvUpdate.visitTableSwitchInsn(tsin.min, tsin.max, labelMap.get(tsin.dflt), freshLabels);
                continue;
            }
            // Remap LookupSwitchInsnNode
            if (insn instanceof LookupSwitchInsnNode lsin) {
                int[] keys = lsin.keys.stream().mapToInt(Integer::intValue).toArray();
                Label[] freshLabels = lsin.labels.stream().map(l -> labelMap.get(l)).toArray(Label[]::new);
                mvUpdate.visitLookupSwitchInsn(labelMap.get(lsin.dflt), keys, freshLabels);
                continue;
            }

            if (slices.captureStateMap.containsKey(insn)) {
                String fieldName = slices.captureStateMap.get(insn);
                String desc = "F";
                if (insn instanceof MethodInsnNode min) {
                    desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
                } else if (insn instanceof FieldInsnNode fin) {
                    desc = fin.desc;
                }
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0); // this
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, fieldName, desc);
            } else if (insn.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (fin.desc.equals("Lnet/minecraft/client/model/geom/ModelPart;")) {
                    mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                    mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + fin.name, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                    mvUpdate.visitInsn(Opcodes.SWAP);
                    mvUpdate.visitInsn(Opcodes.POP);
                } else if (fin.owner.equals("net/minecraft/client/model/geom/ModelPart")) {
                    mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", fin.name, fin.desc);
                } else {
                    mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, fin.name, fin.desc);
                }
            } else if (insn instanceof MethodInsnNode min && min.desc.endsWith("Lnet/minecraft/client/model/geom/ModelPart;")) {
                String partName = min.name.startsWith("get") && min.name.length() > 3
                    ? Character.toLowerCase(min.name.charAt(3)) + min.name.substring(4)
                    : min.name;
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitInsn(Opcodes.SWAP);
                mvUpdate.visitInsn(Opcodes.POP);
            } else if (insn instanceof MethodInsnNode min && min.owner.equals("net/minecraft/client/model/geom/ModelPart")) {
                mvUpdate.visitMethodInsn(min.getOpcode(), "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", min.name, min.desc, min.itf);
            } else if (insn.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (fin.owner.equals("net/minecraft/client/model/geom/ModelPart")) {
                    mvUpdate.visitFieldInsn(Opcodes.PUTFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", fin.name, fin.desc);
                } else {
                    mvUpdate.visitFieldInsn(Opcodes.PUTFIELD, generatedName, fin.name, fin.desc);
                }
            } else {
                if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin) {
                    if (vin.var == 1 && vin.getOpcode() == Opcodes.ALOAD) {
                        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
                        if (!"net/minecraft/world/level/block/entity/BlockEntity".equals(beInternalName)) {
                            mvUpdate.visitTypeInsn(Opcodes.CHECKCAST, beInternalName);
                        }
                        continue;
                    }
                    if (vin.var == 2 && vin.getOpcode() == Opcodes.FLOAD) {
                        mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
                        mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
                        continue;
                    }
                    if (vin.var >= 3) {
                        mvUpdate.visitVarInsn(vin.getOpcode(), varMap.get(vin.var));
                        continue;
                    }
                } else if (insn instanceof org.objectweb.asm.tree.IincInsnNode iin && iin.var >= 3) {
                    mvUpdate.visitIincInsn(varMap.get(iin.var), iin.incr);
                    continue;
                }
                // Replace any RETURN from the animation slice with a jump to sliceEnd so
                // the post-slice code (syncDummyToTree, propagateAnimation, updateLight) runs.
                int op = insn.getOpcode();
                if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
                    mvUpdate.visitJumpInsn(Opcodes.GOTO, sliceEnd);
                    continue;
                }
                insn.accept(mvUpdate);
            }
        }

        // Place the end-of-slice label here so all RETURN-replaced GOTOs land here.
        mvUpdate.visitLabel(sliceEnd);
        
        // SYNC: Apply dummy transformations to instances
        for (String partName : dummyParts) {
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "syncDummyToTree", "(Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;Ldev/engine_room/flywheel/lib/model/part/InstanceTree;)V", false);
        }
        
        // Propagate animation on ROOT trees only!
        for (String layerField : uniqueLayerFields) {
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "getVisualPosition", "()Lnet/minecraft/core/BlockPos;", false);
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockState", "Lnet/minecraft/world/level/block/state/BlockState;");
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
            mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "createInitialPose", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;)Lorg/joml/Matrix4f;", false);
            
            mvUpdate.visitInsn(Opcodes.ICONST_0); // false
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "propagateAnimation", "(Lorg/joml/Matrix4fc;Z)V", false);
        }
        
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
        mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
        mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "updateLight", "(F)V", false);

        mvUpdate.visitInsn(Opcodes.RETURN);
        mvUpdate.visitMaxs(0, 0);
        mvUpdate.visitEnd();

        // 4. Generate updateLight(float partialTick)
        MethodVisitor mvLight = cw.visitMethod(Opcodes.ACC_PUBLIC, "updateLight", "(F)V", null, null);
        mvLight.visitCode();
        // int packedLight = this.computePackedLight();
        mvLight.visitVarInsn(Opcodes.ALOAD, 0);
        mvLight.visitMethodInsn(Opcodes.INVOKESPECIAL, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "computePackedLight", "()I", false);
        mvLight.visitVarInsn(Opcodes.ISTORE, 2);
        
        for (String layerField : uniqueLayerFields) {
            mvLight.visitVarInsn(Opcodes.ALOAD, 0);
            mvLight.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvLight.visitVarInsn(Opcodes.ILOAD, 2);
            mvLight.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/LightHelper", "light", "(Ldev/engine_room/flywheel/lib/model/part/InstanceTree;I)V", false);
        }
        mvLight.visitInsn(Opcodes.RETURN);
        mvLight.visitMaxs(0, 0);
        mvLight.visitEnd();

        // 5. Generate collectCrumblingInstances
        MethodVisitor mvCollect = cw.visitMethod(Opcodes.ACC_PUBLIC, "collectCrumblingInstances", "(Ljava/util/function/Consumer;)V", "(Ljava/util/function/Consumer<Ldev/engine_room/flywheel/api/instance/Instance;>;)V", null);
        mvCollect.visitCode();
        for (String layerField : uniqueLayerFields) {
            mvCollect.visitVarInsn(Opcodes.ALOAD, 0);
            mvCollect.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvCollect.visitVarInsn(Opcodes.ALOAD, 1);
            mvCollect.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/LightHelper", "crumble", "(Ldev/engine_room/flywheel/lib/model/part/InstanceTree;Ljava/util/function/Consumer;)V", false);
        }
        mvCollect.visitInsn(Opcodes.RETURN);
        mvCollect.visitMaxs(0, 0);
        mvCollect.visitEnd();

        // 6. Generate _delete
        MethodVisitor mvDelete = cw.visitMethod(Opcodes.ACC_PROTECTED, "_delete", "()V", null, null);
        mvDelete.visitCode();
        for (String layerField : uniqueLayerFields) {
            mvDelete.visitVarInsn(Opcodes.ALOAD, 0);
            mvDelete.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvDelete.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "delete", "()V", false);
        }
        mvDelete.visitInsn(Opcodes.RETURN);
        mvDelete.visitMaxs(0, 0);
        mvDelete.visitEnd();

        // 7. Generate createModelTree helper method via ASM
        generateMaterialResolverMethod(cw, detectMaterialStrategy(originalClassNode));

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("DUMP_" + generatedName + ".class"), bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return bytes;
    }

    public static byte[] generateEntityVisualClass(ClassNode modelClassNode, Class<?> rendererClass, BytecodeDualSlicer.SliceResult slices, org.objectweb.asm.tree.FieldInsnNode layerField) {
        String generatedName = rendererClass.getName().replace('.', '_') + "_FlywheelVisual";
        ClassWriter cw = createClassWriter(generatedName, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual");

        String entityInternalName = "net/minecraft/world/entity/Entity";
        for (MethodNode mn : modelClassNode.methods) {
            if (mn.name.equals("setupAnim") && mn.desc.endsWith(";FFFFF)V")) {
                org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(mn.desc);
                if (args.length > 0 && args[0].getSort() == org.objectweb.asm.Type.OBJECT) {
                    entityInternalName = args[0].getInternalName();
                    break;
                }
            }
        }

        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generatedName, null, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", new String[]{"dev/engine_room/flywheel/lib/visual/SimpleTickableVisual", "dev/engine_room/flywheel/lib/visual/SimpleDynamicVisual", "dev/engine_room/flywheel/api/visual/LightUpdatedVisual"});

        for (Map.Entry<AbstractInsnNode, String> entry : slices.captureStateMap.entrySet()) {
            AbstractInsnNode insn = entry.getKey();
            String fieldName = entry.getValue();
            String desc = "F";
            if (insn instanceof MethodInsnNode min) {
                desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
            } else if (insn instanceof FieldInsnNode fin) {
                desc = fin.desc;
            }
            cw.visitField(Opcodes.ACC_PRIVATE, fieldName, desc, null, null).visitEnd();
        }

        Set<String> generatedFields = new HashSet<>();
        ClassNode currModel = modelClassNode;
        while (currModel != null && !"java/lang/Object".equals(currModel.name)) {
            for (org.objectweb.asm.tree.FieldNode fn : currModel.fields) {
                if ((fn.access & Opcodes.ACC_STATIC) == 0 && !"Lnet/minecraft/client/model/geom/ModelPart;".equals(fn.desc)) {
                    if (generatedFields.add(fn.name)) {
                        cw.visitField(Opcodes.ACC_PUBLIC, fn.name, fn.desc, null, null).visitEnd();
                    }
                }
            }
            if (currModel.superName == null || "java/lang/Object".equals(currModel.superName)) break;
            try { currModel = RendererAnalyzer.loadClassNode(currModel.superName); } catch (Exception e) { break; }
        }

        Set<String> uniqueLayerFields = new LinkedHashSet<>();
        Map<String, String> layerFieldToOwner = new HashMap<>();
        if (layerField != null) {
            uniqueLayerFields.add(layerField.name);
            layerFieldToOwner.put(layerField.name, layerField.owner);
        } else {
            uniqueLayerFields.add("PIG");
            layerFieldToOwner.put("PIG", "net/minecraft/client/model/geom/ModelLayers");
        }

        for (String lf : uniqueLayerFields) {
            cw.visitField(Opcodes.ACC_PRIVATE, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", null, null).visitEnd();
        }
        cw.visitField(Opcodes.ACC_PRIVATE, "rootTreesMap", "Ljava/util/Map;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PRIVATE, "poseHelperState", "Ljava/lang/Object;", null, null).visitEnd();

        MethodVisitor mvInit = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/entity/Entity;F)V", null, null);
        mvInit.visitCode();
        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitVarInsn(Opcodes.ALOAD, 1);
        mvInit.visitVarInsn(Opcodes.ALOAD, 2);
        mvInit.visitVarInsn(Opcodes.FLOAD, 3);
        mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "<init>", "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;Lnet/minecraft/world/entity/Entity;F)V", false);

        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
        mvInit.visitInsn(Opcodes.DUP);
        mvInit.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
        mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");

        for (String lf : uniqueLayerFields) {
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "entity", "Lnet/minecraft/world/entity/Entity;");
            mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, generatedName, "createModelTree_" + lf, "(Lnet/minecraft/world/entity/Entity;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);

            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "instancerProvider", "()Ldev/engine_room/flywheel/api/instance/InstancerProvider;", false);
            mvInit.visitInsn(Opcodes.SWAP);
            mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "create", "(Ldev/engine_room/flywheel/api/instance/InstancerProvider;Ldev/engine_room/flywheel/lib/model/part/ModelTree;)Ldev/engine_room/flywheel/lib/model/part/InstanceTree;", false);

            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitInsn(Opcodes.SWAP);
            mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");

            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");
            mvInit.visitLdcInsn(lf);
            mvInit.visitVarInsn(Opcodes.ALOAD, 0);
            mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvInit.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            mvInit.visitInsn(Opcodes.POP);
        }

        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitVarInsn(Opcodes.ALOAD, 2); // entity
        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTreesMap", "Ljava/util/Map;");
        mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "setupEntityVisual", "(Lnet/minecraft/world/entity/Entity;Ljava/util/Map;)Ljava/lang/Object;", false);
        mvInit.visitFieldInsn(Opcodes.PUTFIELD, generatedName, "poseHelperState", "Ljava/lang/Object;");

        mvInit.visitVarInsn(Opcodes.ALOAD, 0);
        mvInit.visitVarInsn(Opcodes.FLOAD, 3);
        mvInit.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "updateLight", "(F)V", false);

        mvInit.visitInsn(Opcodes.RETURN);
        mvInit.visitMaxs(0, 0);
        mvInit.visitEnd();

        MethodVisitor mvTick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ldev/engine_room/flywheel/api/visual/TickableVisual$Context;)V", null, null);
        mvTick.visitCode();
        // Build fresh labels for entity capture slice
        Map<LabelNode, Label> entityTickLabelMap = new HashMap<>();
        for (AbstractInsnNode insn : slices.captureSlice) {
            if (insn instanceof LabelNode ln) entityTickLabelMap.put(ln, new Label());
            else if (insn instanceof JumpInsnNode jin) entityTickLabelMap.computeIfAbsent(jin.label, k -> new Label());
            else if (insn instanceof TableSwitchInsnNode tsin) {
                entityTickLabelMap.computeIfAbsent(tsin.dflt, k -> new Label());
                for (LabelNode l : tsin.labels) entityTickLabelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LookupSwitchInsnNode lsin) {
                entityTickLabelMap.computeIfAbsent(lsin.dflt, k -> new Label());
                for (LabelNode l : lsin.labels) entityTickLabelMap.computeIfAbsent(l, k -> new Label());
            } else if (insn instanceof LineNumberNode lnn) {
                entityTickLabelMap.computeIfAbsent(lnn.start, k -> new Label());
            }
        }
        for (AbstractInsnNode insn : slices.captureSlice) {
            if (insn.getType() == AbstractInsnNode.FRAME) continue;
            if (insn instanceof LineNumberNode lnn) {
                Label fresh = entityTickLabelMap.get(lnn.start);
                if (fresh != null) mvTick.visitLineNumber(lnn.line, fresh);
                continue;
            }
            if (insn instanceof LabelNode ln) {
                Label fresh = entityTickLabelMap.get(ln);
                if (fresh != null) mvTick.visitLabel(fresh);
                continue;
            }
            if (insn instanceof JumpInsnNode jin) {
                mvTick.visitJumpInsn(jin.getOpcode(), entityTickLabelMap.get(jin.label));
                continue;
            }
            if (insn instanceof TableSwitchInsnNode tsin) {
                Label[] freshLabels = tsin.labels.stream().map(l -> entityTickLabelMap.get(l)).toArray(Label[]::new);
                mvTick.visitTableSwitchInsn(tsin.min, tsin.max, entityTickLabelMap.get(tsin.dflt), freshLabels);
                continue;
            }
            if (insn instanceof LookupSwitchInsnNode lsin) {
                int[] keys = lsin.keys.stream().mapToInt(Integer::intValue).toArray();
                Label[] freshLabels = lsin.labels.stream().map(l -> entityTickLabelMap.get(l)).toArray(Label[]::new);
                mvTick.visitLookupSwitchInsn(entityTickLabelMap.get(lsin.dflt), keys, freshLabels);
                continue;
            }
            if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin) {
                if (vin.var == 1 && vin.getOpcode() == Opcodes.ALOAD) {
                    mvTick.visitVarInsn(Opcodes.ALOAD, 0);
                    mvTick.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "entity", "Lnet/minecraft/world/entity/Entity;");
                    if (!"net/minecraft/world/entity/Entity".equals(entityInternalName)) {
                        mvTick.visitTypeInsn(Opcodes.CHECKCAST, entityInternalName);
                    }
                    continue;
                }
                if (vin.var >= 2 && vin.var <= 6 && (vin.getOpcode() == Opcodes.FLOAD || vin.getOpcode() == Opcodes.FSTORE)) {
                    mvTick.visitInsn(Opcodes.FCONST_0);
                    continue;
                }
            }
            insn.accept(mvTick);
            if (slices.captureStateMap.containsKey(insn)) {
                String fieldName = slices.captureStateMap.get(insn);
                String desc = "F";
                if (insn instanceof MethodInsnNode min) desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
                else if (insn instanceof FieldInsnNode fin) desc = fin.desc;
                boolean consumed = false;
                for (AbstractInsnNode consumer : slices.captureSlice) {
                    Set<AbstractInsnNode> deps = slices.dependencies.get(consumer);
                    if (deps != null && deps.contains(insn)) { consumed = true; break; }
                }
                boolean isCat2 = desc.equals("J") || desc.equals("D");
                if (consumed) mvTick.visitInsn(isCat2 ? Opcodes.DUP2 : Opcodes.DUP);
                mvTick.visitVarInsn(Opcodes.ALOAD, 0);
                if (isCat2) { mvTick.visitInsn(Opcodes.DUP_X2); mvTick.visitInsn(Opcodes.POP); }
                else { mvTick.visitInsn(Opcodes.SWAP); }
                mvTick.visitFieldInsn(Opcodes.PUTFIELD, generatedName, fieldName, desc);
            }
        }
        mvTick.visitInsn(Opcodes.RETURN);
        mvTick.visitMaxs(0, 0);
        mvTick.visitEnd();

        MethodVisitor mvUpdate = cw.visitMethod(Opcodes.ACC_PUBLIC, "beginFrame", "(Ldev/engine_room/flywheel/api/visual/DynamicVisual$Context;)V", null, null);
        mvUpdate.visitCode();
        for (String lf : uniqueLayerFields) {
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "entity", "Lnet/minecraft/world/entity/Entity;");
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
            mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "renderOrigin", "()Lnet/minecraft/core/Vec3i;", false);
            mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "createEntityPose", "(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/core/Vec3i;)Lorg/joml/Matrix4f;", false);
            mvUpdate.visitInsn(Opcodes.ICONST_0);
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "propagateAnimation", "(Lorg/joml/Matrix4fc;Z)V", false);
        }

        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "poseHelperState", "Ljava/lang/Object;");
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "entity", "Lnet/minecraft/world/entity/Entity;");
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
        mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
        mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "animateEntityVisual", "(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;F)V", false);

        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
        mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
        mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
        mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "updateLight", "(F)V", false);

        mvUpdate.visitInsn(Opcodes.RETURN);
        mvUpdate.visitMaxs(0, 0);
        mvUpdate.visitEnd();

        MethodVisitor mvLight = cw.visitMethod(Opcodes.ACC_PUBLIC, "updateLight", "(F)V", null, null);
        mvLight.visitCode();
        mvLight.visitVarInsn(Opcodes.ALOAD, 0);
        mvLight.visitVarInsn(Opcodes.FLOAD, 1);
        mvLight.visitMethodInsn(Opcodes.INVOKESPECIAL, "dev/engine_room/flywheel/lib/visual/AbstractEntityVisual", "computePackedLight", "(F)I", false);
        mvLight.visitVarInsn(Opcodes.ISTORE, 2);
        for (String lf : uniqueLayerFields) {
            mvLight.visitVarInsn(Opcodes.ALOAD, 0);
            mvLight.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvLight.visitVarInsn(Opcodes.ILOAD, 2);
            mvLight.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/LightHelper", "light", "(Ldev/engine_room/flywheel/lib/model/part/InstanceTree;I)V", false);
        }
        mvLight.visitInsn(Opcodes.RETURN);
        mvLight.visitMaxs(0, 0);
        mvLight.visitEnd();

        MethodVisitor mvCollect = cw.visitMethod(Opcodes.ACC_PUBLIC, "collectCrumblingInstances", "(Ljava/util/function/Consumer;)V", "(Ljava/util/function/Consumer<Ldev/engine_room/flywheel/api/instance/Instance;>;)V", null);
        mvCollect.visitCode();
        for (String lf : uniqueLayerFields) {
            mvCollect.visitVarInsn(Opcodes.ALOAD, 0);
            mvCollect.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvCollect.visitVarInsn(Opcodes.ALOAD, 1);
            mvCollect.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/LightHelper", "crumble", "(Ldev/engine_room/flywheel/lib/model/part/InstanceTree;Ljava/util/function/Consumer;)V", false);
        }
        mvCollect.visitInsn(Opcodes.RETURN);
        mvCollect.visitMaxs(0, 0);
        mvCollect.visitEnd();

        MethodVisitor mvDelete = cw.visitMethod(Opcodes.ACC_PROTECTED, "_delete", "()V", null, null);
        mvDelete.visitCode();
        for (String lf : uniqueLayerFields) {
            mvDelete.visitVarInsn(Opcodes.ALOAD, 0);
            mvDelete.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + lf, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            mvDelete.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "delete", "()V", false);
        }
        mvDelete.visitInsn(Opcodes.RETURN);
        mvDelete.visitMaxs(0, 0);
        mvDelete.visitEnd();

        for (String lf : uniqueLayerFields) {
            String owner = layerFieldToOwner.getOrDefault(lf, "net/minecraft/client/model/geom/ModelLayers");
            MethodVisitor mvCreate = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "createModelTree_" + lf, "(Lnet/minecraft/world/entity/Entity;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", null, null);
            mvCreate.visitCode();
            mvCreate.visitFieldInsn(Opcodes.GETSTATIC, owner, lf, "Lnet/minecraft/client/model/geom/ModelLayerLocation;");
            mvCreate.visitVarInsn(Opcodes.ASTORE, 1);
            mvCreate.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/client/Minecraft", "getInstance", "()Lnet/minecraft/client/Minecraft;", false);
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/Minecraft", "getEntityRenderDispatcher", "()Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;", false);
            mvCreate.visitVarInsn(Opcodes.ALOAD, 0);
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/entity/EntityRenderDispatcher", "getRenderer", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;", false);
            mvCreate.visitVarInsn(Opcodes.ALOAD, 0);
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/renderer/entity/EntityRenderer", "getTextureLocation", "(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/resources/ResourceLocation;", false);
            mvCreate.visitVarInsn(Opcodes.ASTORE, 2);
            mvCreate.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/material/SimpleMaterial", "builder", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
            mvCreate.visitFieldInsn(Opcodes.GETSTATIC, "dev/engine_room/flywheel/api/material/CardinalLightingMode", "ENTITY", "Ldev/engine_room/flywheel/api/material/CardinalLightingMode;");
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "cardinalLightingMode", "(Ldev/engine_room/flywheel/api/material/CardinalLightingMode;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
            mvCreate.visitVarInsn(Opcodes.ALOAD, 2);
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "texture", "(Lnet/minecraft/resources/ResourceLocation;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
            mvCreate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "build", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial;", false);
            mvCreate.visitVarInsn(Opcodes.ASTORE, 3);
            mvCreate.visitVarInsn(Opcodes.ALOAD, 1);
            mvCreate.visitVarInsn(Opcodes.ALOAD, 3);
            mvCreate.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/ModelTrees", "of", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Ldev/engine_room/flywheel/api/material/Material;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
            mvCreate.visitInsn(Opcodes.ARETURN);
            mvCreate.visitMaxs(0, 0);
            mvCreate.visitEnd();
        }

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("DUMP_" + generatedName + ".class"), bytes);
        } catch (Exception ignored) {}
        return bytes;
    }

    private enum MaterialStrategy {
        CHEST, BED, SHULKER, FALLBACK
    }

    private static MaterialStrategy detectMaterialStrategy(org.objectweb.asm.tree.ClassNode rendererClassNode) {
        for (org.objectweb.asm.tree.MethodNode mn : rendererClassNode.methods) {
            for (AbstractInsnNode insn : mn.instructions) {
                if (insn.getOpcode() == Opcodes.INVOKESTATIC || insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    MethodInsnNode min = (MethodInsnNode) insn;
                    if ("chooseMaterial".equals(min.name) && min.owner.contains("Sheets")) return MaterialStrategy.CHEST;
                } else if (insn.getOpcode() == Opcodes.GETSTATIC) {
                    FieldInsnNode fin = (FieldInsnNode) insn;
                    if ("BED_TEXTURES".equals(fin.name)) return MaterialStrategy.BED;
                    if ("SHULKER_TEXTURE_LOCATION".equals(fin.name) || "DEFAULT_SHULKER_TEXTURE_LOCATION".equals(fin.name)) return MaterialStrategy.SHULKER;
                }
            }
        }
        String name = rendererClassNode.name.toLowerCase();
        if (name.contains("chest")) return MaterialStrategy.CHEST;
        if (name.contains("bed")) return MaterialStrategy.BED;
        if (name.contains("shulker")) return MaterialStrategy.SHULKER;
        return MaterialStrategy.FALLBACK;
    }

    private static void generateMaterialResolverMethod(ClassWriter cw, MaterialStrategy strategy) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "createModelTree", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", null, null);
        mv.visitCode();
        switch (strategy) {
            case CHEST -> generateChestMaterialResolver(mv);
            case BED -> generateBedMaterialResolver(mv);
            case SHULKER -> generateShulkerMaterialResolver(mv);
            case FALLBACK -> generateFallbackMaterialResolver(mv);
        }
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void generateChestMaterialResolver(MethodVisitor mv) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/state/properties/ChestType", "SINGLE", "Lnet/minecraft/world/level/block/state/properties/ChestType;");
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/model/geom/ModelLayerLocation", "getModel", "()Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "getPath", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "()Ljava/lang/String;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        org.objectweb.asm.Label l3 = new org.objectweb.asm.Label();
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitLdcInsn("left");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, l3);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/state/properties/ChestType", "LEFT", "Lnet/minecraft/world/level/block/state/properties/ChestType;");
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        org.objectweb.asm.Label l5 = new org.objectweb.asm.Label();
        mv.visitJumpInsn(Opcodes.GOTO, l5);
        mv.visitLabel(l3);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitLdcInsn("right");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false);
        mv.visitJumpInsn(Opcodes.IFEQ, l5);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/world/level/block/state/properties/ChestType", "RIGHT", "Lnet/minecraft/world/level/block/state/properties/ChestType;");
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitLabel(l5);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/client/renderer/Sheets", "chooseMaterial", "(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/level/block/state/properties/ChestType;Z)Lnet/minecraft/client/resources/model/Material;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/material/SimpleMaterial", "builder", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "dev/engine_room/flywheel/api/material/CardinalLightingMode", "ENTITY", "Ldev/engine_room/flywheel/api/material/CardinalLightingMode;");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "cardinalLightingMode", "(Ldev/engine_room/flywheel/api/material/CardinalLightingMode;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/resources/model/Material", "atlasLocation", "()Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "texture", "(Lnet/minecraft/resources/ResourceLocation;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "build", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 5);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 5);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/ModelTrees", "of", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/client/resources/model/Material;Ldev/engine_room/flywheel/api/material/Material;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
        mv.visitInsn(Opcodes.ARETURN);
    }

    private static void generateBedMaterialResolver(MethodVisitor mv) {
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/client/renderer/Sheets", "BED_TEXTURES", "[Lnet/minecraft/client/resources/model/Material;");
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/world/level/block/entity/BedBlockEntity");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/block/entity/BedBlockEntity", "getColor", "()Lnet/minecraft/world/item/DyeColor;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/DyeColor", "getId", "()I", false);
        mv.visitInsn(Opcodes.AALOAD);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/material/SimpleMaterial", "builder", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "dev/engine_room/flywheel/api/material/CardinalLightingMode", "ENTITY", "Ldev/engine_room/flywheel/api/material/CardinalLightingMode;");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "cardinalLightingMode", "(Ldev/engine_room/flywheel/api/material/CardinalLightingMode;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/resources/model/Material", "atlasLocation", "()Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "texture", "(Lnet/minecraft/resources/ResourceLocation;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "build", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/ModelTrees", "of", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/client/resources/model/Material;Ldev/engine_room/flywheel/api/material/Material;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
        mv.visitInsn(Opcodes.ARETURN);
    }

    private static void generateShulkerMaterialResolver(MethodVisitor mv) {
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/world/level/block/entity/ShulkerBoxBlockEntity");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/level/block/entity/ShulkerBoxBlockEntity", "getColor", "()Lnet/minecraft/world/item/DyeColor;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        org.objectweb.asm.Label l2 = new org.objectweb.asm.Label();
        org.objectweb.asm.Label l5 = new org.objectweb.asm.Label();
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitJumpInsn(Opcodes.IFNONNULL, l2);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/client/renderer/Sheets", "DEFAULT_SHULKER_TEXTURE_LOCATION", "Lnet/minecraft/client/resources/model/Material;");
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitJumpInsn(Opcodes.GOTO, l5);
        mv.visitLabel(l2);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "net/minecraft/client/renderer/Sheets", "SHULKER_TEXTURE_LOCATION", "Ljava/util/List;");
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/world/item/DyeColor", "getId", "()I", false);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "net/minecraft/client/resources/model/Material");
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitLabel(l5);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/material/SimpleMaterial", "builder", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "dev/engine_room/flywheel/api/material/CardinalLightingMode", "ENTITY", "Ldev/engine_room/flywheel/api/material/CardinalLightingMode;");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "cardinalLightingMode", "(Ldev/engine_room/flywheel/api/material/CardinalLightingMode;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/resources/model/Material", "atlasLocation", "()Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "texture", "(Lnet/minecraft/resources/ResourceLocation;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "build", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/ModelTrees", "of", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/client/resources/model/Material;Ldev/engine_room/flywheel/api/material/Material;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
        mv.visitInsn(Opcodes.ARETURN);
    }

    private static void generateFallbackMaterialResolver(MethodVisitor mv) {
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/material/SimpleMaterial", "builder", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitFieldInsn(Opcodes.GETSTATIC, "dev/engine_room/flywheel/api/material/CardinalLightingMode", "ENTITY", "Ldev/engine_room/flywheel/api/material/CardinalLightingMode;");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "cardinalLightingMode", "(Ldev/engine_room/flywheel/api/material/CardinalLightingMode;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitLdcInsn("minecraft");
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn("textures/entity/");
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/model/geom/ModelLayerLocation", "getModel", "()Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/resources/ResourceLocation", "getPath", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitLdcInsn(".png");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath", "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "texture", "(Lnet/minecraft/resources/ResourceLocation;)Ldev/engine_room/flywheel/lib/material/SimpleMaterial$Builder;", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/material/SimpleMaterial$Builder", "build", "()Ldev/engine_room/flywheel/lib/material/SimpleMaterial;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "dev/engine_room/flywheel/lib/model/part/ModelTrees", "of", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Ldev/engine_room/flywheel/api/material/Material;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
        mv.visitInsn(Opcodes.ARETURN);
    }
}
