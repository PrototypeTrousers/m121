package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.HashSet;
import java.util.Set;

public class BackwardSlicer {

    public static Set<AbstractInsnNode> slice(MethodNode method, AbstractInsnNode targetInsn) throws AnalyzerException {
        // We use SourceInterpreter because it tracks which instructions produced the values on the stack and in locals.
        Analyzer<SourceValue> analyzer = new Analyzer<>(new SourceInterpreter());
        Frame<SourceValue>[] frames = analyzer.analyze("DummyClass", method);

        Set<AbstractInsnNode> slice = new HashSet<>();
        Set<AbstractInsnNode> worklist = new HashSet<>();
        
        worklist.add(targetInsn);
        
        while (!worklist.isEmpty()) {
            AbstractInsnNode insn = worklist.iterator().next();
            worklist.remove(insn);
            
            if (slice.add(insn)) {
                // Find what instructions produced the values consumed by this instruction
                int index = method.instructions.indexOf(insn);
                if (index >= 0 && index < frames.length) {
                    Frame<SourceValue> frame = frames[index];
                    if (frame != null) {
                        // This is a naive backward slice: it just adds the producers of stack values 
                        // consumed by this instruction. A real slicer needs to know how many stack items
                        // the instruction pops, but SourceInterpreter's naryOperation and others 
                        // give us the producers.
                        
                        // For a robust implementation, we would need to map consumers to producers 
                        // during the analysis phase.
                    }
                }
                
                // If it's not the start, maybe add the previous instruction if it's control flow?
                // Real slicing requires a full Program Dependence Graph (PDG).
            }
        }
        
        return slice;
    }
}
