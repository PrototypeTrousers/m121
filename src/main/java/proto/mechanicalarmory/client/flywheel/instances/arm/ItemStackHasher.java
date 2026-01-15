package proto.mechanicalarmory.client.flywheel.instances.arm;

import it.unimi.dsi.fastutil.Hash;
import net.minecraft.world.item.ItemStack;

public class ItemStackHasher implements Hash.Strategy<ItemStack> {
    @Override
    public int hashCode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        // ItemStack.hashItemAndComponents() is the standard way to
        // get a hash code based on the Item and all Data Components.
        return ItemStack.hashItemAndComponents(stack);
    }

    @Override
    public boolean equals(ItemStack s1, ItemStack s2) {
        if (s1 == s2) return true;
        if (s1 == null || s2 == null) return false;
        if (s1.isEmpty() && s2.isEmpty()) return true;

        // matches() checks if the Item, count, and Components are identical.
        // If you want to ignore the stack count (stack size), use isSameItemSameComponents().
        return ItemStack.isSameItemSameComponents(s1, s2);
    }
}