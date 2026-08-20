package proto.mechanicalarmory.common.belt.data;

import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe object pool for {@link ItemGroup} instances to eliminate GC churn
 * during simulation, seam transitions, and item insertions.
 */
public final class ItemGroupPool {

    private static final int MAX_POOL_SIZE = 1024;
    private static final ConcurrentLinkedQueue<ItemGroup> POOL = new ConcurrentLinkedQueue<>();

    private ItemGroupPool() {}

    /**
     * Obtains a recycled {@link ItemGroup} instance or allocates a new one if pool is empty.
     */
    public static ItemGroup obtain(ItemStack item, int count, float headPos) {
        ItemGroup pooled = POOL.poll();
        if (pooled != null) {
            pooled.set(item, count, headPos);
            return pooled;
        }
        return new ItemGroup(item, count, headPos);
    }

    /**
     * Releases an {@link ItemGroup} back to the pool for reuse.
     */
    public static void release(ItemGroup group) {
        if (group == null) return;
        group.set(ItemStack.EMPTY, 0, 0.0f);
        if (POOL.size() < MAX_POOL_SIZE) {
            POOL.offer(group);
        }
    }
}
