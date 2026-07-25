package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

import java.util.*;

public class BytecodeDualSlicer {

    public static class SliceResult {
        public final Set<AbstractInsnNode> captureSlice = new LinkedHashSet<>();
        public final Set<AbstractInsnNode> animationSlice = new LinkedHashSet<>();
        
        // Maps the instruction in the capture slice that produces a value, to the field name it will be saved as in our state object
        public final Map<AbstractInsnNode, String> captureStateMap = new HashMap<>();
        
        // Expose the dependency graph (Consumer -> Producers)
        public Map<AbstractInsnNode, Set<AbstractInsnNode>> dependencies;
    }

    // Custom Interpreter to build a true Producer -> Consumer dependency graph
    private static class DependencyGraphInterpreter extends SourceInterpreter {
        public final Map<AbstractInsnNode, Set<AbstractInsnNode>> dependencies = new HashMap<>();

        public DependencyGraphInterpreter() {
            super(Opcodes.ASM9);
        }

        private void recordDependencies(AbstractInsnNode consumer, Collection<? extends SourceValue> producers) {
            Set<AbstractInsnNode> deps = dependencies.computeIfAbsent(consumer, k -> new HashSet<>());
            for (SourceValue producer : producers) {
                if (producer != null && producer.insns != null) {
                    deps.addAll(producer.insns);
                }
            }
        }

        @Override
        public SourceValue copyOperation(AbstractInsnNode insn, SourceValue value) {
            recordDependencies(insn, Collections.singleton(value));
            return super.copyOperation(insn, value);
        }

        @Override
        public SourceValue unaryOperation(AbstractInsnNode insn, SourceValue value) {
            recordDependencies(insn, Collections.singleton(value));
            return super.unaryOperation(insn, value);
        }

        @Override
        public SourceValue binaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2) {
            recordDependencies(insn, Arrays.asList(value1, value2));
            return super.binaryOperation(insn, value1, value2);
        }

        @Override
        public SourceValue ternaryOperation(AbstractInsnNode insn, SourceValue value1, SourceValue value2, SourceValue value3) {
            recordDependencies(insn, Arrays.asList(value1, value2, value3));
            return super.ternaryOperation(insn, value1, value2, value3);
        }

        @Override
        public SourceValue naryOperation(AbstractInsnNode insn, List<? extends SourceValue> values) {
            recordDependencies(insn, values);
            return super.naryOperation(insn, values);
        }

        @Override
        public void returnOperation(AbstractInsnNode insn, SourceValue value, SourceValue expected) {
            recordDependencies(insn, Collections.singleton(value));
            super.returnOperation(insn, value, expected);
        }
    }

    public static SliceResult slice(String owner, MethodNode method) throws AnalyzerException {
        // Ensure maxStack is sufficiently large for ASM Analyzer after inlining and transformations
        method.maxStack = Math.max(method.maxStack, 128);
        DependencyGraphInterpreter interpreter = new DependencyGraphInterpreter();
        Analyzer<SourceValue> analyzer = new Analyzer<>(interpreter);
        Frame<SourceValue>[] frames = analyzer.analyze(owner, method);

        SliceResult result = new SliceResult();
        result.dependencies = interpreter.dependencies;
        Set<AbstractInsnNode> worklist = new HashSet<>();

        // 1. Find all PUTFIELDs to ModelPart
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode insn = method.instructions.get(i);
            if (insn.getOpcode() == Opcodes.PUTFIELD) {
                FieldInsnNode fin = (FieldInsnNode) insn;
                if ("net/minecraft/client/model/geom/ModelPart".equals(fin.owner)) {
                    worklist.add(insn);
                    
                    // Manually link dependencies for PUTFIELD since it consumes from the stack but doesn't produce.
                    Frame<SourceValue> frame = frames[i];
                    if (frame != null && frame.getStackSize() >= 2) {
                        SourceValue value = frame.getStack(frame.getStackSize() - 1);
                        SourceValue object = frame.getStack(frame.getStackSize() - 2);
                        Set<AbstractInsnNode> deps = interpreter.dependencies.computeIfAbsent(insn, k -> new HashSet<>());
                        deps.addAll(value.insns);
                        deps.addAll(object.insns);
                    }
                }
            }
        }

        Set<AbstractInsnNode> animationWorklist = new HashSet<>(worklist);
        Set<AbstractInsnNode> captureWorklist = new HashSet<>();
        Set<AbstractInsnNode> animationVisited = new HashSet<>();
        Set<AbstractInsnNode> captureVisited = new HashSet<>();
        int stateCounter = 0;

        // Trace animation slice
        while (!animationWorklist.isEmpty()) {
            AbstractInsnNode insn = animationWorklist.iterator().next();
            animationWorklist.remove(insn);

            if (!animationVisited.add(insn)) {
                continue;
            }

            if (isCaptureBoundary(insn)) {
                captureWorklist.add(insn);
                result.captureStateMap.put(insn, "capturedState_" + (stateCounter++));
                
                // Add the boundary instruction to animationSlice so it can be replaced by GETFIELD,
                // but DO NOT trace its dependencies in animationWorklist!
                result.animationSlice.add(insn);
                continue;
            }
            
            result.animationSlice.add(insn);
            addDependenciesToWorklist(insn, interpreter.dependencies, animationWorklist);
        }
        
        // Trace capture slice
        while (!captureWorklist.isEmpty()) {
            AbstractInsnNode insn = captureWorklist.iterator().next();
            captureWorklist.remove(insn);

            if (!captureVisited.add(insn)) {
                continue;
            }

            result.captureSlice.add(insn);
            addDependenciesToWorklist(insn, interpreter.dependencies, captureWorklist);
        }

        // Add instructions in proper execution order (naive sort based on original method index)
        List<AbstractInsnNode> sortedAnimation = new ArrayList<>(result.animationSlice);
        sortedAnimation.sort(Comparator.comparingInt(insn -> method.instructions.indexOf(insn)));
        result.animationSlice.clear();
        result.animationSlice.addAll(sortedAnimation);

        List<AbstractInsnNode> sortedCapture = new ArrayList<>(result.captureSlice);
        sortedCapture.sort(Comparator.comparingInt(insn -> method.instructions.indexOf(insn)));
        result.captureSlice.clear();
        result.captureSlice.addAll(sortedCapture);

        return result;
    }

    private static void addDependenciesToWorklist(AbstractInsnNode insn, Map<AbstractInsnNode, Set<AbstractInsnNode>> dependencies, Set<AbstractInsnNode> worklist) {
        Set<AbstractInsnNode> deps = dependencies.get(insn);
        if (deps != null) {
            worklist.addAll(deps);
        }
        
        // Handle local variable reads: if this is an ILOAD, FLOAD, ALOAD, we also need to trace 
        // what ISTORE, FSTORE, ASTORE originally wrote to that local variable.
        // The SourceInterpreter natively handles this by tracking local variable sources in Frames!
        // But since we only overrode stack operations, we might need to handle frame locals if needed.
        // Actually, SourceInterpreter tracks locals too via copyOperation / merge!
    }

    private static boolean isCaptureBoundary(AbstractInsnNode insn) {
        if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
            MethodInsnNode min = (MethodInsnNode) insn;
            // Anything invoking on Level, BlockEntity, BlockState is a boundary
            if (min.owner.contains("Level") || min.owner.contains("BlockEntity") || min.owner.contains("BlockState")) {
                return true;
            }
        }
        return false;
    }
}
