package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.api.material.CardinalLightingMode;
import dev.engine_room.flywheel.lib.material.SimpleMaterial;
import dev.engine_room.flywheel.lib.model.part.ModelTree;
import dev.engine_room.flywheel.lib.model.part.ModelTrees;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;

public class MaterialHelper {
    public static ModelTree createModelTree(ModelLayerLocation layer, BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof EnderChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity) {
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

        // Fallback for other entities
        dev.engine_room.flywheel.api.material.Material fallbackMat = SimpleMaterial.builder()
                .cardinalLightingMode(CardinalLightingMode.ENTITY)
                .texture(ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/" + layer.getModel().getPath() + ".png"))
                .build();
        return ModelTrees.of(layer, fallbackMat);
    }
}
