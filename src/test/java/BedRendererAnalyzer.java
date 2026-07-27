import sootup.core.inputlocation.AnalysisInputLocation;
import sootup.java.bytecode.frontend.inputlocation.JavaClassPathAnalysisInputLocation;
import sootup.java.core.views.JavaView;
import sootup.core.model.SootClass;
import sootup.core.model.SootMethod;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.jimple.common.stmt.JAssignStmt;
import sootup.core.jimple.common.stmt.JInvokeStmt;
import sootup.core.jimple.common.expr.*;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ClassType;
import sootup.core.graph.StmtGraph;

import java.io.File;
import java.util.*;

/**
 * Proof-of-concept SootUp analysis over BedRenderer.
 *
 * Three passes:
 *   1. Constructor scan -> locate the LayerDefinition factory call (geometry source).
 *   2. render() scan -> locate ModelPart.render/renderToBuffer cut points (pose/draw boundary).
 *   3. Classification -> decide if the renderer is a SIMPLE bake-once-pose-per-instance
 *      candidate for auto-generating a Flywheel Visual, or whether it needs manual review.
 *
 * NOTE: SootUp's API surface has shifted across releases; method/class names below reflect
 * a recent SootUp version. Verify import paths (sootup.java.bytecode.frontend.* vs older
 * sootup.java.bytecode.inputlocation.*) against whatever version is on your classpath.
 *
 * NOTE: This expects a DEOBFUSCATED / remapped Minecraft jar (Mojang mappings) on the
 * classpath. Obfuscated names will break the string-based heuristics used here.
 */
public class BedRendererAnalyzer {

    public static void main(String[] args) {
        String mcJar = args.length > 0 ? args[0] : "neoforge-21.1.216.jar";
        String classPath = mcJar + File.pathSeparator + System.getProperty("java.class.path");

        AnalysisInputLocation inputLocation = new JavaClassPathAnalysisInputLocation(classPath);
        JavaView view = new JavaView(Collections.singletonList(inputLocation));

        ClassType bedRendererType = view.getIdentifierFactory()
                .getClassType("net.minecraft.client.renderer.blockentity.BedRenderer");

        SootClass bedRenderer = view.getClass(bedRendererType).orElseThrow(
                () -> new RuntimeException("BedRenderer not found on classpath"));

        SootMethod ctor = findConstructor(bedRenderer);
        SootMethod renderMethod = findRenderMethod(bedRenderer);

        System.out.println("=== Constructor scan ===");
        findLayerDefinitionFactoryCall(ctor);

        System.out.println("\n=== render() scan ===");
        List<Stmt> cutPoints = findModelPartDrawCalls(renderMethod);

        System.out.println("\n=== Classification ===");
        classify(renderMethod, cutPoints);
    }

    // ------------------------------------------------------------------
    // Lookups
    // ------------------------------------------------------------------

    private static SootMethod findConstructor(SootClass clazz) {
        return clazz.getMethods().stream()
                .filter(m -> m.getName().equals("<init>"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No constructor found"));
    }

    private static SootMethod findRenderMethod(SootClass clazz) {
        // BedRenderer#render has a fixed vanilla signature:
        // void render(BedBlockEntity, float, PoseStack, MultiBufferSource, int, int)
        return clazz.getMethods().stream()
                .filter(m -> m.getName().equals("render") && m.getParameterCount() == 6)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("render() not found"));
    }

    private static Optional<AbstractInvokeExpr> extractInvoke(Stmt stmt) {
        if (stmt instanceof JAssignStmt assign && assign.getRightOp() instanceof AbstractInvokeExpr inv) {
            return Optional.of(inv);
        }
        if (stmt instanceof JInvokeStmt invStmt) {
            return invStmt.getInvokeExpr();
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------
    // Pass 1: geometry factory call in the constructor
    // ------------------------------------------------------------------

    private static void findLayerDefinitionFactoryCall(SootMethod ctor) {
        var body = ctor.getBody(); // Jimple body, lazily built from bytecode by SootUp

        for (Stmt stmt : body.getStmts()) {
            Optional<AbstractInvokeExpr> invokeOpt = extractInvoke(stmt);
            if (invokeOpt.isEmpty()) continue;

            AbstractInvokeExpr invoke = invokeOpt.get();
            if (!(invoke instanceof JStaticInvokeExpr)) continue;

            MethodSignature sig = invoke.getMethodSignature();
            String retType = sig.getType().toString();

            boolean looksLikeLayerFactory =
                    retType.contains("LayerDefinition")
                    || (sig.getName().startsWith("create") && sig.getName().contains("Layer"));

            if (looksLikeLayerFactory) {
                System.out.printf("Factory call found: %s.%s(%s) -> %s%n",
                        sig.getDeclClassType(), sig.getName(), sig.getParameterTypes(), retType);
            }
        }
    }

    // ------------------------------------------------------------------
    // Pass 2: draw / cut-point calls in render()
    // ------------------------------------------------------------------

    private static List<Stmt> findModelPartDrawCalls(SootMethod renderMethod) {
        var body = renderMethod.getBody();
        List<Stmt> cutPoints = new ArrayList<>();

        for (Stmt stmt : body.getStmts()) {
            Optional<AbstractInvokeExpr> invokeOpt = extractInvoke(stmt);
            if (invokeOpt.isEmpty()) continue;

            AbstractInvokeExpr invoke = invokeOpt.get();
            if (!(invoke instanceof JVirtualInvokeExpr || invoke instanceof JInterfaceInvokeExpr)) continue;

            MethodSignature sig = invoke.getMethodSignature();
            String declClass = sig.getDeclClassType().toString();
            String name = sig.getName();

            boolean isDrawCall = declClass.endsWith("ModelPart")
                    && (name.equals("render") || name.equals("renderToBuffer"));

            if (isDrawCall) {
                System.out.printf("Cut point: %s @ %s%n", stmt, sig);
                cutPoints.add(stmt);
            }
        }
        return cutPoints;
    }

    // ------------------------------------------------------------------
    // Pass 3: classification (SIMPLE / MODEL_SWAP / COMPLEX)
    // ------------------------------------------------------------------

    private static void classify(SootMethod renderMethod, List<Stmt> cutPoints) {
        StmtGraph<?> cfg = renderMethod.getBody().getStmtGraph();

        boolean hasBranchBeforeDraw = false;

        for (Stmt cutPoint : cutPoints) {
            // Walk predecessors back toward method entry, looking for any statement
            // that has more than one successor (i.e. a conditional branch dominating
            // this draw call).
            Deque<Stmt> worklist = new ArrayDeque<>(cfg.predecessors(cutPoint));
            Set<Stmt> visited = new HashSet<>();

            while (!worklist.isEmpty()) {
                Stmt s = worklist.poll();
                if (!visited.add(s)) continue;

                if (cfg.successors(s).size() > 1) {
                    hasBranchBeforeDraw = true;
                }
                worklist.addAll(cfg.predecessors(s));
            }
        }

        // Separately: scan for direct VertexConsumer.vertex(...) calls anywhere in the
        // body. Presence means procedural geometry emission rather than a fixed baked
        // mesh, which disqualifies the renderer from simple auto-generation.
        boolean rawVertexEmission = false;
        for (Stmt stmt : renderMethod.getBody().getStmts()) {
            Optional<AbstractInvokeExpr> invokeOpt = extractInvoke(stmt);
            if (invokeOpt.isEmpty()) continue;

            MethodSignature sig = invokeOpt.get().getMethodSignature();
            if (sig.getDeclClassType().toString().endsWith("VertexConsumer")
                    && sig.getName().equals("vertex")) {
                rawVertexEmission = true;
            }
        }

        String verdict;
        if (rawVertexEmission) {
            verdict = "COMPLEX (procedural vertex emission) — needs manual Visual";
        } else if (hasBranchBeforeDraw) {
            verdict = "MODEL_SWAP (or conditional draw) — needs manual review";
        } else {
            verdict = "SIMPLE — eligible for auto-generation";
        }

        System.out.println("Verdict: " + verdict);
    }
}