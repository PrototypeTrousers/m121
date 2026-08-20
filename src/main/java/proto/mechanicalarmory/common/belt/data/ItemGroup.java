package proto.mechanicalarmory.common.belt.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A contiguous run of identical items on a belt lane.
 *
 * <p>The lane coordinate space is [0.0, 1.0] where 0 is the belt's input end
 * and 1 is its output end. {@code headPos} is the position of the <em>leading
 * edge</em> of this group; the trailing edge is at
 * {@code headPos - count * BeltLane.ITEM_SPACING}.
 *
 * <p>{@code headPos} may legally exceed [0, 1]:
 * <ul>
 *   <li>Negative – the group is entering from behind the input (wrap case).</li>
 *   <li>&gt;1.0  – the group is exiting past the output (wrap / transfer case).</li>
 * </ul>
 */
public final class ItemGroup {

    /** Mutable: we update headPos in-place rather than allocating per tick. */
    private ItemStack item;
    private int count;
    private float headPos;

    /**
     * @param item  the item stack — the caller must pass an exclusively-owned
     *              copy; {@code ItemGroup} does not copy it defensively.
     */
    public ItemGroup(ItemStack item, int count, float headPos) {
        this.item = item;
        this.count = count;
        this.headPos = headPos;
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public ItemStack item()    { return item; }
    public int       count()   { return count; }
    public float     headPos() { return headPos; }

    /** Position of the trailing edge with custom spacing. */
    public float tailPos(float spacing) {
        return headPos - count * spacing;
    }

    /** Position of the trailing edge with default spacing. */
    public float tailPos() {
        return headPos - count * BeltLane.ITEM_SPACING;
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    public void advanceHead(float delta) { headPos += delta; }
    public void setHeadPos(float pos)    { headPos = pos; }
    public void setCount(int count)      { this.count = count; }
    /** Replaces the item; caller must pass an exclusively-owned copy. */
    public void setItem(ItemStack item)  { this.item = item; }

    /** Returns true if this group is the same item type as {@code other}. */
    public boolean sameType(ItemGroup other) {
        return ItemStack.isSameItemSameComponents(this.item, other.item);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", item.save(registries));
        tag.putInt("count", count);
        tag.putFloat("headPos", headPos);
        return tag;
    }

    public static ItemGroup load(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack item = ItemStack.parseOptional(registries, tag.getCompound("item"));
        int count = tag.getInt("count");
        float headPos = tag.getFloat("headPos");
        return new ItemGroup(item, count, headPos);
    }

    /** Returns a copy of this group with an independently-owned ItemStack. */
    public ItemGroup copy() {
        return new ItemGroup(item.copy(), count, headPos);
    }

    @Override
    public String toString() {
        return "ItemGroup{item=" + item + ", count=" + count + ", headPos=" + headPos + "}";
    }
}
