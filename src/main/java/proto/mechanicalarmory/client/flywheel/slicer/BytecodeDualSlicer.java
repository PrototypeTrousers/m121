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
                }
            }
        }

        // 2. Trace backwards using dependency graph to find all instructions needed for those PUTFIELDs (animation slice)
        Set<AbstractInsnNode> animationSlice = new HashSet<>();
        Queue<AbstractInsnNode> animationWorklist = new ArrayDeque<>(worklist);
        while (!animationWorklist.isEmpty()) {
            AbstractInsnNode insn = animationWorklist.poll();
            if (insn == null || !animationSlice.add(insn)) continue;

            Set<AbstractInsnNode> deps = result.dependencies.get(insn);
            if (deps != null) {
                for (AbstractInsnNode dep : deps) {
                    if (isCaptureBoundary(dep)) {
                        if (!result.captureStateMap.containsKey(dep)) {
                            result.captureStateMap.put(dep, "capturedState_" + result.captureStateMap.size());
                        }
                        animationSlice.add(dep);
                    } else if (!animationSlice.contains(dep)) {
                        animationWorklist.add(dep);
                    }
                }
            }
        }
        
        boolean changed = true;
        while (changed) {
            int sizeBefore = animationSlice.size();
            augmentWithControlFlow(method, animationSlice, result.dependencies, result.captureStateMap);
            // ensureLocalVariableCompleteness(method, animationSlice, result.dependencies, result.captureStateMap);
            changed = (animationSlice.size() != sizeBefore);
        }

        // 3. Find all capture boundaries required by the animation slice (capture slice)
        Set<AbstractInsnNode> captureSlice = new HashSet<>();
        Queue<AbstractInsnNode> captureWorklist = new ArrayDeque<>(result.captureStateMap.keySet());
        while (!captureWorklist.isEmpty()) {
            AbstractInsnNode insn = captureWorklist.poll();
            if (insn == null || !captureSlice.add(insn)) continue;

            Set<AbstractInsnNode> deps = result.dependencies.get(insn);
            if (deps != null) {
                for (AbstractInsnNode dep : deps) {
                    if (!captureSlice.contains(dep)) {
                        captureWorklist.add(dep);
                    }
                }
            }
        }
        
        changed = true;
        while (changed) {
            int sizeBefore = captureSlice.size();
            augmentWithControlFlow(method, captureSlice, result.dependencies, null);
            // ensureLocalVariableCompleteness(method, captureSlice, result.dependencies, null);
            changed = (captureSlice.size() != sizeBefore);
        }

        // Sort instructions by original method order for clean output
        List<AbstractInsnNode> sortedAnimation = new ArrayList<>(animationSlice);
        sortedAnimation.sort(Comparator.comparingInt(insn -> method.instructions.indexOf(insn)));
        result.animationSlice.clear();
        result.animationSlice.addAll(sortedAnimation);

        List<AbstractInsnNode> sortedCapture = new ArrayList<>(captureSlice);
        sortedCapture.sort(Comparator.comparingInt(insn -> method.instructions.indexOf(insn)));
        result.captureSlice.clear();
        result.captureSlice.addAll(sortedCapture);

        return result;
    }

    private static void augmentWithControlFlow(MethodNode method, Set<AbstractInsnNode> slice, Map<AbstractInsnNode, Set<AbstractInsnNode>> dependencies, Map<AbstractInsnNode, String> captureStateMap) {
        Set<LabelNode> jumpTargets = new HashSet<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof JumpInsnNode jin) {
                jumpTargets.add(jin.label);
            } else if (insn instanceof TableSwitchInsnNode tsin) {
                jumpTargets.add(tsin.dflt);
                jumpTargets.addAll(tsin.labels);
            } else if (insn instanceof LookupSwitchInsnNode lsin) {
                jumpTargets.add(lsin.dflt);
                jumpTargets.addAll(lsin.labels);
            }
        }
        for (org.objectweb.asm.tree.TryCatchBlockNode tcbn : method.tryCatchBlocks) {
            jumpTargets.add(tcbn.start);
            jumpTargets.add(tcbn.end);
            jumpTargets.add(tcbn.handler);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            // 1. Add terminating GOTOs / switches / returns for any basic block containing a sliced instruction
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode insn = method.instructions.get(i);
                if (slice.contains(insn)) {
                    AbstractInsnNode next = insn.getNext();
                    while (next != null) {
                        if (slice.contains(next) || (next instanceof LabelNode ln && jumpTargets.contains(ln))) {
                            break; // Hit another sliced instruction or end of basic block by fall-through
                        }
                        if (next instanceof JumpInsnNode jin) {
                            if (jin.getOpcode() == Opcodes.GOTO) {
                                if (slice.add(jin)) changed = true;
                            }
                            break; // Hit a jump, end of basic block
                        }
                        if (next instanceof TableSwitchInsnNode || next instanceof LookupSwitchInsnNode) {
                            if (slice.add(next)) changed = true;
                            break;
                        }
                        if (next.getOpcode() >= Opcodes.IRETURN && next.getOpcode() <= Opcodes.RETURN || next.getOpcode() == Opcodes.ATHROW) {
                            if (slice.add(next)) changed = true;
                            break;
                        }
                        next = next.getNext();
                    }
                }
            }
            // 2. Add control-dependent branch instructions
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode insn = method.instructions.get(i);
                if ((insn instanceof JumpInsnNode jin && jin.getOpcode() != Opcodes.GOTO) || insn instanceof TableSwitchInsnNode || insn instanceof LookupSwitchInsnNode) {
                    if (!slice.contains(insn) && isControlDependent(method, insn, slice)) {
                        if (slice.add(insn)) changed = true;
                        Queue<AbstractInsnNode> q = new ArrayDeque<>();
                        q.add(insn);
                        while (!q.isEmpty()) {
                            AbstractInsnNode curr = q.poll();
                            Set<AbstractInsnNode> deps = dependencies.get(curr);
                            if (deps != null) {
                                for (AbstractInsnNode dep : deps) {
                                    if (isCaptureBoundary(dep)) {
                                        if (captureStateMap != null && !captureStateMap.containsKey(dep)) {
                                            captureStateMap.put(dep, "capturedState_" + captureStateMap.size());
                                        }
                                        if (slice.add(dep)) changed = true;
                                    } else if (slice.add(dep)) {
                                        changed = true;
                                        q.add(dep);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (AbstractInsnNode insn : new ArrayList<>(slice)) {
            if (insn instanceof JumpInsnNode jin) {
                slice.add(jin.label);
            } else if (insn instanceof TableSwitchInsnNode tsin) {
                slice.add(tsin.dflt);
                slice.addAll(tsin.labels);
            } else if (insn instanceof LookupSwitchInsnNode lsin) {
                slice.add(lsin.dflt);
                slice.addAll(lsin.labels);
            }
        }
    }

    private static void ensureLocalVariableCompleteness(MethodNode method, Set<AbstractInsnNode> slice, Map<AbstractInsnNode, Set<AbstractInsnNode>> dependencies, Map<AbstractInsnNode, String> captureStateMap) {
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<Integer> usedVars = new HashSet<>();
            for (AbstractInsnNode insn : slice) {
                if (insn instanceof VarInsnNode vin && vin.var >= 3) {
                    usedVars.add(vin.var);
                } else if (insn instanceof IincInsnNode iin && iin.var >= 3) {
                    usedVars.add(iin.var);
                }
            }
            for (int i = 0; i < method.instructions.size(); i++) {
                AbstractInsnNode insn = method.instructions.get(i);
                int varIndex = -1;
                if (insn instanceof VarInsnNode vin) varIndex = vin.var;
                else if (insn instanceof IincInsnNode iin) varIndex = iin.var;
                
                if (varIndex >= 3 && usedVars.contains(varIndex) && !slice.contains(insn)) {
                    Queue<AbstractInsnNode> q = new ArrayDeque<>();
                    q.add(insn);
                    while (!q.isEmpty()) {
                        AbstractInsnNode curr = q.poll();
                        if (curr == null) continue;
                        Set<AbstractInsnNode> deps = dependencies.get(curr);
                        if (deps != null) {
                            for (AbstractInsnNode dep : deps) {
                                if (isCaptureBoundary(dep)) {
                                    if (captureStateMap != null && !captureStateMap.containsKey(dep)) {
                                        captureStateMap.put(dep, "capturedState_" + captureStateMap.size());
                                    }
                                    if (slice.add(dep)) changed = true;
                                } else if (slice.add(dep)) {
                                    changed = true;
                                    q.add(dep);
                                }
                            }
                        }
                    }
                    if (slice.add(insn)) changed = true;
                }
            }
        }
    }

    private static boolean isControlDependent(MethodNode method, AbstractInsnNode branchInsn, Set<AbstractInsnNode> slice) {
        List<AbstractInsnNode> succs = new ArrayList<>();
        if (branchInsn instanceof JumpInsnNode jin) {
            succs.add(jin.label);
            succs.add(jin.getNext());
        } else if (branchInsn instanceof TableSwitchInsnNode tsin) {
            succs.add(tsin.dflt);
            succs.addAll(tsin.labels);
        } else if (branchInsn instanceof LookupSwitchInsnNode lsin) {
            succs.add(lsin.dflt);
            succs.addAll(lsin.labels);
        }
        if (succs.size() < 2) return false;

        List<Set<AbstractInsnNode>> reachSets = new ArrayList<>();
        for (AbstractInsnNode s : succs) {
            reachSets.add(getReachable(s, branchInsn));
        }

        for (AbstractInsnNode insn : slice) {
            if (insn == branchInsn) continue;
            boolean inAny = false;
            boolean inAll = true;
            for (Set<AbstractInsnNode> r : reachSets) {
                if (r.contains(insn)) {
                    inAny = true;
                } else {
                    inAll = false;
                }
            }
            if (inAny && !inAll) {
                return true;
            }
        }
        return false;
    }

    private static Set<AbstractInsnNode> getReachable(AbstractInsnNode start, AbstractInsnNode stop) {
        Set<AbstractInsnNode> visited = new HashSet<>();
        Queue<AbstractInsnNode> q = new ArrayDeque<>();
        if (start != null && start != stop) {
            q.add(start);
            visited.add(start);
        }
        while (!q.isEmpty()) {
            AbstractInsnNode curr = q.poll();
            List<AbstractInsnNode> succs = new ArrayList<>();
            if (curr instanceof JumpInsnNode jin) {
                succs.add(jin.label);
                if (jin.getOpcode() != Opcodes.GOTO) {
                    succs.add(jin.getNext());
                }
            } else if (curr instanceof TableSwitchInsnNode tsin) {
                succs.add(tsin.dflt);
                succs.addAll(tsin.labels);
            } else if (curr instanceof LookupSwitchInsnNode lsin) {
                succs.add(lsin.dflt);
                succs.addAll(lsin.labels);
            } else if (curr.getOpcode() >= Opcodes.IRETURN && curr.getOpcode() <= Opcodes.RETURN || curr.getOpcode() == Opcodes.ATHROW) {
                // No successors
            } else {
                if (curr.getNext() != null) {
                    succs.add(curr.getNext());
                }
            }
            for (AbstractInsnNode s : succs) {
                if (s != null && s != stop && visited.add(s)) {
                    q.add(s);
                }
            }
        }
        return visited;
    }

    private static boolean isCaptureBoundary(AbstractInsnNode insn) {
        if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
            MethodInsnNode min = (MethodInsnNode) insn;
            // Anything invoking on Level, BlockEntity, BlockState, Entity, Mob, Animal is a boundary
            if (min.owner.contains("Level") || min.owner.contains("BlockEntity") || min.owner.contains("BlockState")
                || min.owner.contains("Entity") || min.owner.contains("Mob") || min.owner.contains("Animal")) {
                return true;
            }
        } else if (insn.getOpcode() == Opcodes.GETFIELD) {
            FieldInsnNode fin = (FieldInsnNode) insn;
            // Any field read from BlockEntity, BlockState, Level, Entity is a capture boundary!
            if (fin.owner.contains("Level") || fin.owner.contains("BlockEntity") || fin.owner.contains("BlockState")
                || fin.owner.contains("Entity") || fin.owner.contains("Mob") || fin.owner.contains("Animal")) {
                return true;
            }
        }
        return false;
    }
}
