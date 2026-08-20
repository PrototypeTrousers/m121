package proto.mechanicalarmory.client.flywheel.instances.belt;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import proto.mechanicalarmory.client.flywheel.CapturedModel;
import proto.mechanicalarmory.client.flywheel.instances.capturing.CapturingBufferSource;

/**
 * Handles capturing, baking, and caching of item models for Flywheel belt visuals.
 */
public final class BeltItemModelCapture {

    private static final Object2ObjectOpenCustomHashMap<ItemStack, CapturedModel> MODEL_CACHE =
            new Object2ObjectOpenCustomHashMap<>(new ItemStackHasher());

    private BeltItemModelCapture() {}

    public static void clearModelCache() {
        MODEL_CACHE.clear();
    }

    /**
     * Retrieves the cached Flywheel {@link CapturedModel} for an item stack, or queues a capture render call.
     */
    public static CapturedModel getOrCaptureModel(ItemStack item, Level level, boolean isDeleted) {
        CapturedModel cached = MODEL_CACHE.get(item);
        if (cached != null) return cached;

        RenderSystem.recordRenderCall(() -> {
            if (isDeleted || level == null) return;
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            var model = itemRenderer.getModel(item, level, null, 0);
            CapturingBufferSource cbs = new CapturingBufferSource();
            PoseStack pose = new PoseStack();
            itemRenderer.render(item, ItemDisplayContext.FIXED, false, pose, cbs, 0, 0, model);
            cbs.endLastBatch();
            MODEL_CACHE.put(item, new CapturedModel(cbs));
        });
        return null;
    }

    /**
     * FastUtil Hash Strategy comparing ItemStacks by item identity and data components.
     */
    private static final class ItemStackHasher implements Hash.Strategy<ItemStack> {
        @Override
        public int hashCode(ItemStack o) {
            if (o == null || o.isEmpty()) return 0;
            int h = System.identityHashCode(o.getItem());
            h = 31 * h + o.getComponents().hashCode();
            return h;
        }

        @Override
        public boolean equals(ItemStack a, ItemStack b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            return ItemStack.isSameItemSameComponents(a, b);
        }
    }
}
