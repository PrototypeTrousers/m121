package proto.mechanicalarmory.client.mixin;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Map;

@Mixin(BlockEntityRenderDispatcher.class)
public interface BlockEntityRenderDispatcherAccessor {
    @Accessor("renderers")
    Map<BlockEntityType<?>, BlockEntityRenderer<?>> getRenderers();

    @Accessor("blockRenderDispatcher")
    java.util.function.Supplier<net.minecraft.client.renderer.block.BlockRenderDispatcher> getBlockRenderDispatcher();

    @Accessor("itemRenderer")
    java.util.function.Supplier<net.minecraft.client.renderer.entity.ItemRenderer> getItemRenderer();

    @Accessor("entityRenderer")
    java.util.function.Supplier<net.minecraft.client.renderer.entity.EntityRenderDispatcher> getEntityRenderer();

    @Accessor("entityModelSet")
    net.minecraft.client.model.geom.EntityModelSet getEntityModelSet();

    @Accessor("font")
    net.minecraft.client.gui.Font getFont();
}
