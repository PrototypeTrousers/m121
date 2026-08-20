package proto.mechanicalarmory.common.belt.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * A contiguous run of identical items on a belt lane.
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

    // ── Object Pooling ────────────────────────────────────────────────────────

    public static ItemGroup obtain(ItemStack item, int count, float headPos) {
        return ItemGroupPool.obtain(item, count, headPos);
    }

    public static void release(ItemGroup group) {
        ItemGroupPool.release(group);
    }

    public void set(ItemStack item, int count, float headPos) {
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

    // ── Network ByteBuf Codec ─────────────────────────────────────────────────

    public void encode(RegistryFriendlyByteBuf buf) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
        buf.writeVarInt(count);
        buf.writeFloat(headPos);
    }

    public static ItemGroup decode(RegistryFriendlyByteBuf buf) {
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int count = buf.readVarInt();
        float headPos = buf.readFloat();
        return obtain(item, count, headPos);
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
        return obtain(item, count, headPos);
    }

    /** Returns a copy of this group with an independently-owned ItemStack. */
    public ItemGroup copy() {
        return obtain(item.copy(), count, headPos);
    }

    @Override
    public String toString() {
        return "ItemGroup{item=" + item + ", count=" + count + ", headPos=" + headPos + "}";
    }
}
