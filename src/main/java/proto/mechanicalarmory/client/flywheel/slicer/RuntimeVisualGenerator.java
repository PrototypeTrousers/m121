package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RuntimeVisualGenerator {

    /**
     * Generates the bytecode for a new Flywheel Visual class based on the sliced vanilla renderer.
     * @param originalClassName The internal name of the original renderer (e.g. "net/minecraft/client/renderer/blockentity/ChestRenderer")
     * @param slices The dual slice result containing the separated instructions.
     * @return The byte array representing the newly generated .class file.
     */
    public static byte[] generateVisualClass(org.objectweb.asm.tree.ClassNode originalClassNode, BytecodeDualSlicer.SliceResult slices) {
        String originalClassName = originalClassNode.name;
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        String generatedName = originalClassName.replace('/', '_') + "_FlywheelVisual";
        
        // public class GeneratedVisual extends AbstractBlockEntityVisual implements SimpleTickableVisual, SimpleDynamicVisual
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generatedName, null, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", new String[]{"dev/engine_room/flywheel/lib/visual/SimpleTickableVisual", "dev/engine_room/flywheel/lib/visual/SimpleDynamicVisual"});

        // 1. Generate Fields for Captured State
        for (Map.Entry<AbstractInsnNode, String> entry : slices.captureStateMap.entrySet()) {
            AbstractInsnNode insn = entry.getKey();
            String fieldName = entry.getValue();
            
            String desc = "F";
            if (insn instanceof MethodInsnNode min) {
                desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
            }
            cw.visitField(Opcodes.ACC_PRIVATE, fieldName, desc, null, null).visitEnd();
        }

        // 1.5. Find all ModelPart GETFIELDs in the animation slice
        Set<String> dummyParts = new HashSet<>();
        for (AbstractInsnNode insn : slices.animationSlice) {
            if (insn.getOpcode() == Opcodes.GETFIELD) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (fin.desc.equals("Lnet/minecraft/client/model/geom/ModelPart;")) {
                    dummyParts.add(fin.name);
                }
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
        
        // Initialize rootTreesMap = new HashMap()
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
            
            // MaterialHelper.createModelTree(ModelLayerLocation, BlockEntity)
            mvInit.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/MaterialHelper", "createModelTree", "(Lnet/minecraft/client/model/geom/ModelLayerLocation;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ldev/engine_room/flywheel/lib/model/part/ModelTree;", false);
            
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
            String layerField = fieldToLayerField.get(partName);
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
        
        mvInit.visitInsn(Opcodes.RETURN);
        mvInit.visitMaxs(0, 0);
        mvInit.visitEnd();
        
        // 2. Generate tick() method (The Capture Slice)
        MethodVisitor mvTick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "(Ldev/engine_room/flywheel/api/visual/TickableVisual$Context;)V", null, null);
        mvTick.visitCode();
        
        // Write all capture instructions in topological order
        for (AbstractInsnNode insn : slices.captureSlice) {
            if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin) {
                if (vin.var == 1 && vin.getOpcode() == Opcodes.ALOAD) {
                    mvTick.visitVarInsn(Opcodes.ALOAD, 0); // this
                    mvTick.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
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
                }
                
                boolean consumedInCaptureSlice = false;
                for (AbstractInsnNode consumer : slices.captureSlice) {
                    Set<AbstractInsnNode> deps = slices.dependencies.get(consumer);
                    if (deps != null && deps.contains(insn)) {
                        consumedInCaptureSlice = true;
                        break;
                    }
                }
                
                if (consumedInCaptureSlice) {
                    mvTick.visitInsn(Opcodes.DUP);
                }
                
                mvTick.visitVarInsn(Opcodes.ALOAD, 0); // this
                mvTick.visitInsn(Opcodes.SWAP); // swap the value and 'this'
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

        for (AbstractInsnNode insn : slices.animationSlice) {
            if (slices.captureStateMap.containsKey(insn)) {
                String fieldName = slices.captureStateMap.get(insn);
                String desc = "F";
                if (insn instanceof MethodInsnNode min) {
                    desc = org.objectweb.asm.Type.getReturnType(min.desc).getDescriptor();
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
                    insn.accept(mvUpdate);
                }
            } else if (insn.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                if (fin.owner.equals("net/minecraft/client/model/geom/ModelPart")) {
                    mvUpdate.visitFieldInsn(Opcodes.PUTFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", fin.name, fin.desc);
                } else {
                    insn.accept(mvUpdate);
                }
            } else {
                if (insn instanceof org.objectweb.asm.tree.VarInsnNode vin) {
                    if (vin.var == 1 && vin.getOpcode() == Opcodes.ALOAD) {
                        mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                        mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockEntity", "Lnet/minecraft/world/level/block/entity/BlockEntity;");
                        continue;
                    }
                    if (vin.var == 2 && vin.getOpcode() == Opcodes.FLOAD) {
                        mvUpdate.visitVarInsn(Opcodes.ALOAD, 1);
                        mvUpdate.visitMethodInsn(Opcodes.INVOKEINTERFACE, "dev/engine_room/flywheel/api/visual/DynamicVisual$Context", "partialTick", "()F", true);
                        continue;
                    }
                }
                insn.accept(mvUpdate);
            }
        }
        
        // SYNC: Apply dummy transformations to instances
        for (String partName : dummyParts) {
            // Check if tree exists
            if (fieldToChildName.containsKey(partName)) {
                // tree.xRot(dummy.xRot)
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "xRot", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "xRot", "(F)V", false);

                // tree.yRot(dummy.yRot)
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "yRot", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "yRot", "(F)V", false);

                // tree.zRot(dummy.zRot)
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "zRot", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "zRot", "(F)V", false);

                // x, y, z translation
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "x", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "xPos", "(F)V", false);
                
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "y", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "yPos", "(F)V", false);
                
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "tree_" + partName, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
                mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "dummy_" + partName, "Lproto/mechanicalarmory/client/flywheel/slicer/DummyModelPart;");
                mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "proto/mechanicalarmory/client/flywheel/slicer/DummyModelPart", "z", "F");
                mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "zPos", "(F)V", false);
            }
        }
        
        // Propagate animation on ROOT trees only!
        for (String layerField : uniqueLayerFields) {
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, generatedName, "rootTree_" + layerField, "Ldev/engine_room/flywheel/lib/model/part/InstanceTree;");
            
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, generatedName, "getVisualPosition", "()Lnet/minecraft/core/BlockPos;", false);
            mvUpdate.visitVarInsn(Opcodes.ALOAD, 0);
            mvUpdate.visitFieldInsn(Opcodes.GETFIELD, "dev/engine_room/flywheel/lib/visual/AbstractBlockEntityVisual", "blockState", "Lnet/minecraft/world/level/block/state/BlockState;");
            mvUpdate.visitMethodInsn(Opcodes.INVOKESTATIC, "proto/mechanicalarmory/client/flywheel/slicer/PoseHelper", "createInitialPose", "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lorg/joml/Matrix4f;", false);
            
            mvUpdate.visitInsn(Opcodes.ICONST_0); // false
            mvUpdate.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "dev/engine_room/flywheel/lib/model/part/InstanceTree", "propagateAnimation", "(Lorg/joml/Matrix4fc;Z)V", false);
        }
        
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

        cw.visitEnd();
        byte[] bytes = cw.toByteArray();
        
        try {
            java.nio.file.Files.write(java.nio.file.Paths.get("DUMP_" + generatedName + ".class"), bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return bytes;
    }
}
