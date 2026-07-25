package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

public class RendererInspectorTest {

    public static void main(String[] args) throws Exception {
        ClassNode cn = RendererAnalyzer.loadClassNode(RendererInspectorTest.class);
        for (MethodNode mn : cn.methods) {
            if (mn.name.equals("template_chest")) {
                System.out.println("==================================================");
                System.out.println("METHOD: " + mn.name);
                printInstructions(mn.instructions);
            }
        }
    }

    public static ModelTree template_chest(ModelLayerLocation layer, BlockEntity blockEntity) {
        ChestType type = ChestType.SINGLE;
        String path = layer.getModel().getPath().toLowerCase();
        if (path.contains("left")) {
            type = ChestType.LEFT;
        } else if (path.contains("right")) {
            type = ChestType.RIGHT;
        }
        net.minecraft.client.resources.model.Material vanillaMaterial = Sheets.chooseMaterial(blockEntity, type, false);
        dev.engine_room.flywheel.api.material.Material flywheelMaterial = SimpleMaterial.builder()
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .texture(vanillaMaterial.atlasLocation())
                .build();
        return ModelTrees.of(layer, vanillaMaterial, flywheelMaterial);
    }

    public static ModelTree template_bed(ModelLayerLocation layer, BlockEntity blockEntity) {
        net.minecraft.client.resources.model.Material vanillaMaterial = Sheets.BED_TEXTURES[((BedBlockEntity) blockEntity).getColor().getId()];
        dev.engine_room.flywheel.api.material.Material flywheelMaterial = SimpleMaterial.builder()
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .texture(vanillaMaterial.atlasLocation())
                .build();
        return ModelTrees.of(layer, vanillaMaterial, flywheelMaterial);
    }

    public static ModelTree template_shulker(ModelLayerLocation layer, BlockEntity blockEntity) {
        net.minecraft.world.item.DyeColor color = ((ShulkerBoxBlockEntity) blockEntity).getColor();
        net.minecraft.client.resources.model.Material vanillaMaterial;
        if (color == null) {
            vanillaMaterial = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION;
        } else {
            vanillaMaterial = Sheets.SHULKER_TEXTURE_LOCATION.get(color.getId());
        }
        dev.engine_room.flywheel.api.material.Material flywheelMaterial = SimpleMaterial.builder()
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .texture(vanillaMaterial.atlasLocation())
                .build();
        return ModelTrees.of(layer, vanillaMaterial, flywheelMaterial);
    }

    public static ModelTree template_fallback(ModelLayerLocation layer, BlockEntity blockEntity) {
        dev.engine_room.flywheel.api.material.Material fallbackMat = SimpleMaterial.builder()
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .texture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/" + layer.getModel().getPath() + ".png"))
                .build();
        return ModelTrees.of(layer, fallbackMat);
    }

    private static void printInstructions(Iterable<AbstractInsnNode> insns) {
        Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        for (AbstractInsnNode insn : insns) {
            insn.accept(mp);
            StringWriter sw = new StringWriter();
            printer.print(new PrintWriter(sw));
            printer.getText().clear();
            System.out.print(sw.toString());
        }
    }
}
