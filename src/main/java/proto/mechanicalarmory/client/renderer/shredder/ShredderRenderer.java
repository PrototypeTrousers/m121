package proto.mechanicalarmory.client.renderer.shredder;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import proto.mechanicalarmory.common.entities.block.ShredderEntity;

public class ShredderRenderer implements BlockEntityRenderer<ShredderEntity> {

    public ShredderRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ShredderEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

    }
}
