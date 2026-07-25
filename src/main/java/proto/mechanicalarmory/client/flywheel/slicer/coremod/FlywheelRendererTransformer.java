package proto.mechanicalarmory.client.flywheel.slicer.coremod;

import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import proto.mechanicalarmory.client.flywheel.slicer.BytecodeDualSlicer;
import proto.mechanicalarmory.client.flywheel.slicer.RendererAnalyzer;

import java.util.Set;

public class FlywheelRendererTransformer implements ITransformer<ClassNode> {

    @Override
    public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
        System.out.println("[Flywheel Slicer] Transforming vanilla renderer: " + input.name);

        try {
            // Find the render method. For ChestRenderer, we look for the private helper
            MethodNode renderMethod = RendererAnalyzer.findMethod(input, "render", "(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;FII)V").orElse(null);

            if (renderMethod != null) {
                // Run the Dual-Slicer
                BytecodeDualSlicer.SliceResult slices = BytecodeDualSlicer.slice(input.name, renderMethod);

                // Inject the DynamicVisual interface
                input.interfaces.add("dev/engine_room/flywheel/api/visual/DynamicVisual");

                // Generate the thread-safe update() method using the extracted Animation Slice!
                MethodNode generatedUpdateMethod = new MethodNode(Opcodes.ACC_PUBLIC, "update", "(Ldev/engine_room/flywheel/api/visual/DynamicVisual$Context;)V", null, null);
                generatedUpdateMethod.visitCode();
                
                // (In a full implementation, we use RuntimeVisualGenerator logic here to insert the slice instructions)
                // For now, just prove the injection works
                generatedUpdateMethod.visitInsn(Opcodes.RETURN);
                generatedUpdateMethod.visitMaxs(0, 0);
                generatedUpdateMethod.visitEnd();

                input.methods.add(generatedUpdateMethod);
                
                System.out.println("[Flywheel Slicer] Successfully sliced and injected Flywheel interface into " + input.name);
            }
        } catch (Exception e) {
            System.err.println("[Flywheel Slicer] Failed to transform " + input.name);
            e.printStackTrace();
        }

        return input;
    }

    @Override
    public TransformerVoteResult castVote(ITransformerVotingContext context) {
        return TransformerVoteResult.YES; // Force ModLauncher to apply our transform
    }

    @Override
    public Set<Target<ClassNode>> targets() {
        // Target specific vanilla renderers we want to hijack
        return Set.of(
            Target.targetClass("net.minecraft.client.renderer.blockentity.ChestRenderer")
        );
    }
    
    @Override
    public TargetType<ClassNode> getTargetType() {
        return TargetType.CLASS;
    }
}
