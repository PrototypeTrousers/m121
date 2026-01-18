package proto.mechanicalarmory.client.renderer.arm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public class MyCustomItemBakedModel implements BakedModel {
    @Override
    public boolean isCustomRenderer() {
        // THIS IS THE KEY: Returning true tells Minecraft to 
        // ignore the voxel/quad data and call your BEWLR instead.
        return true; 
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return Collections.emptyList(); // No static quads needed
    }

    @Override
    public boolean useAmbientOcclusion() { return true; }
    @Override
    public boolean isGui3d() { return true; }
    @Override
    public boolean usesBlockLight() { return true; }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        // You still need a fallback particle (e.g., missing_no or a specific texture)
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ResourceLocation.fromNamespaceAndPath("minecraft","block/dirt"));
    }

    @Override
    public ItemTransforms getTransforms() {
        // You can define how the item is held/scaled in code here
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}