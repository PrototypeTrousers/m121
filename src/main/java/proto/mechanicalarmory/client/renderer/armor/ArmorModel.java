package proto.mechanicalarmory.client.renderer.armor;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

public class ArmorModel extends HumanoidModel<LivingEntity> {

    public ArmorModel(ModelPart root) {
        super(root);
    }

    public ArmorModel(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
        super(root, renderType);
    }
}