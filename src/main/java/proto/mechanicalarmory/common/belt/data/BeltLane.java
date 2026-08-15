package proto.mechanicalarmory.common.belt.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * A single lane of a conveyor belt.
 *
 * <p>The lane is a 1-D coordinate space where 0.0 is the belt's input end and
 * 1.0 is the output end.  Groups are stored front-first (index 0 = closest to
 * the output). The speed is measured in belt-lengths per tick.
 *
 * <p>Standard belt: {@value #SPEED_DEFAULT} belt-lengths/tick = 4 blocks/s at
 * 20 TPS, since each belt block is 1 block long.
 *
 * <h3>Backing store</h3>
 * Groups are kept in a plain {@code ItemGroup[]} with an explicit {@code size}
 * counter (front = index 0, back = index size-1).  This gives O(1) indexed
 * read in {@link #advance} without any per-tick allocation, at the cost of an
 * O(n) {@code System.arraycopy} when the front group is consumed by
 * {@link #transferOut} — acceptable because n is almost always ≤ 4.
 */
public final class BeltLane {

    /** Distance occupied by one item slot on the lane (4 per block = 0.25). */
    public static final float ITEM_SPACING = 0.25f;

    /** 4 blocks/s / 20 ticks/s = 0.20 belt-lengths per tick. */
    public static final float SPEED_DEFAULT = 0.20f;

    /** Speed on the inner lane of a 90-degree turn (R = 0.35, L = 0.55). */
    public static final float SPEED_INNER = SPEED_DEFAULT * (2.0f / (float) (Math.PI * 0.35));

    /** Speed on the outer lane of a 90-degree turn (R = 0.65, L = 1.02). */
    public static final float SPEED_OUTER = SPEED_DEFAULT * (2.0f / (float) (Math.PI * 0.65));

    /** Groups whose gap is smaller than this are merged (same item type). */
    private static final float GAP_MERGE_THRESHOLD = 0.01f;

    private static final int INITIAL_CAPACITY = 4;

    // ── State ─────────────────────────────────────────────────────────────────

    private float speed;

    /**
     * Cached item spacing — derived from {@link #speed}, updated in
     * {@link #setSpeed}.  Avoids a float division on every hot call site.
     */
    private float spacing;

    /**
     * Groups in front-first order (front = output side = index 0).
     * Invariant: {@code groups[0].headPos >= groups[1].headPos >= …}
     */
    private ItemGroup[] groups = new ItemGroup[INITIAL_CAPACITY];
    private int size = 0;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltLane(float speed) {
        this.speed   = speed;
        this.spacing = computeSpacing(speed);
    }

    public static BeltLane standard() {
        return new BeltLane(SPEED_DEFAULT);
    }

    private static float computeSpacing(float speed) {
        return speed <= 0 ? ITEM_SPACING : (speed / SPEED_DEFAULT) * ITEM_SPACING;
    }

    /** @deprecated prefer the cached {@link #spacing} field directly within this class. */
    public float itemSpacing() { return spacing; }

    // ── Simulation ────────────────────────────────────────────────────────────

    /**
     * Advance all groups by {@code dt} belt-lengths.
     * Groups back up behind preceding groups when blocked.
     *
     * <p>Fully allocation-free: positions are updated in-place in a first
     * forward pass, then adjacent groups that now touch are compacted in a
     * second forward pass using a write pointer — no temporary collection.
     *
     * @param dt         delta time in ticks (1.0f for a full tick)
     * @param maxExitPos maximum position the front group can reach before
     *                   stopping (1.0f for dead-end terminals,
     *                   {@link Float#MAX_VALUE} if it can exit)
     */
    public void advance(float dt, float maxExitPos) {
        if (size == 0) return;
        final float delta = speed * dt;
        if (delta <= 0) return;

        // ── Pass 1: move ──────────────────────────────────────────────────────
        // Front group is clamped to maxExitPos; each subsequent group is clamped
        // behind the tail of the group ahead of it.
        groups[0].setHeadPos(Math.min(groups[0].headPos() + delta, maxExitPos));
        for (int i = 1; i < size; i++) {
            final float cap = groups[i - 1].tailPos(spacing);
            groups[i].setHeadPos(Math.min(groups[i].headPos() + delta, cap));
        }

        // ── Pass 2: compact merges ────────────────────────────────────────────
        // Walk with a write pointer (w). When the current "accumulator" group
        // touches its successor and they share an item type, absorb it.
        // Otherwise flush the accumulator to groups[w] and advance w.
        int w = 0;
        ItemGroup cur = groups[0];
        for (int r = 1; r < size; r++) {
            final ItemGroup next = groups[r];
            if (cur.tailPos(spacing) - next.headPos() <= GAP_MERGE_THRESHOLD
                    && cur.sameType(next)) {
                cur.setCount(cur.count() + next.count());
                groups[r] = null; // release reference
            } else {
                groups[w++] = cur;
                cur = next;
            }
        }
        groups[w++] = cur;

        // Clear stale tail slots so GC can reclaim evicted groups.
        for (int i = w; i < size; i++) {
            groups[i] = null;
        }
        size = w;
    }

    /** Backward-compatible overload — advances with no exit cap. */
    public void advance(float dt) {
        advance(dt, Float.MAX_VALUE);
    }

    /**
     * Attempt to transfer items to the given output lane.
     * Groups whose {@code headPos >= 1.0} are pushed into the output lane's
     * back if the output lane has room.  If the output lane is backed up, items
     * wait at position 1.0 on this belt.
     *
     * @return true if any item was transferred
     */
    public boolean transferOut(BeltLane output) {
        boolean transferred = false;
        while (size > 0) {
            final ItemGroup front = groups[0];
            if (front.headPos() < 1.0f) break;

            // Check space at the back of the output lane
            final float outputSpacing = output.spacing;
            final float outputRoom = output.size == 0
                    ? Float.MAX_VALUE
                    : output.groups[output.size - 1].tailPos(outputSpacing);

            if (outputRoom <= 0.0f) {
                // Output lane is completely backed up past the junction entrance (tailPos <= 0)
                float clampPos = 1.0f + outputRoom * (spacing / outputSpacing);
                front.setHeadPos(Math.min(front.headPos(), clampPos));
                break;
            }

            // excess is in source-lane coordinate space.  Convert to output-lane
            // coordinate space before placing the item.  Both lanes are calibrated
            // to the same physical throughput speed, so the ratio of their spacings
            // equals the ratio of their coordinate scales (speed_out / speed_in).
            // For straight→straight this is 1.0; for straight→inner curve ≈ 1.82.
            float excess = front.headPos() - 1.0f;
            float newHead = Math.min(excess * (outputSpacing / spacing), outputRoom);

            if (front.count() == 1) {
                pollFirst(); // removes groups[0], shifts down
                front.setHeadPos(newHead);
                output.insertBack(front);
            } else {
                // Split 1 item from the front of the group to transfer.
                // Both groups share the same ItemStack reference — safe because
                // we never mutate the ItemStack itself after construction.
                front.setCount(front.count() - 1);
                front.advanceHead(-spacing);

                output.insertBack(new ItemGroup(front.item(), 1, newHead));
            }
            transferred = true;
        }
        return transferred;
    }

    /**
     * Insert an item group at the back (input side) of this lane.
     * Merges with the current last group if same type and gap is small.
     */
    public void insertBack(ItemGroup incoming) {
        if (size > 0) {
            final ItemGroup last    = groups[size - 1];
            final float    lastTail = last.tailPos(spacing); // computed once
            if (incoming.headPos() > lastTail) {
                incoming.setHeadPos(lastTail);
            }
            if (lastTail - incoming.headPos() <= GAP_MERGE_THRESHOLD && incoming.sameType(last)) {
                last.setCount(last.count() + incoming.count());
                return;
            }
        }
        addLast(incoming);
    }

    /**
     * Insert a single item at the back input tip of the lane.
     * Returns {@code false} if there is no room (the last group's tail is <= 0).
     */
    public boolean insertItem(ItemStack item) {
        final float maxAvailableHead = size == 0
                ? spacing
                : groups[size - 1].tailPos(spacing);

        if (maxAvailableHead <= 0.0f && size > 0) {
            return false; // lane is full / backed up to input
        }
        insertBack(new ItemGroup(item, 1, Math.min(spacing, maxAvailableHead)));
        return true;
    }

    /**
     * Remove one item from the group closest to {@code targetPos} (for player
     * extraction / hopper pulls).  Returns the extracted item or
     * {@link ItemStack#EMPTY}.
     */
    public ItemStack extractNearest(float targetPos) {
        int   bestIdx  = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            float dist = Math.abs(groups[i].headPos() - targetPos);
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx  = i;
            }
        }
        if (bestIdx < 0) return ItemStack.EMPTY;

        final ItemGroup best   = groups[bestIdx];
        final ItemStack result = best.item().copyWithCount(1);
        if (best.count() == 1) {
            removeAt(bestIdx);
        } else {
            best.setCount(best.count() - 1);
            best.advanceHead(-ITEM_SPACING);
        }
        return result;
    }

    // ── Speed ─────────────────────────────────────────────────────────────────

    public void setSpeed(float speed) {
        this.speed   = speed;
        this.spacing = computeSpacing(speed);
    }
    public float speed() { return speed; }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isEmpty()  { return size == 0; }
    public int     groupCount() { return size; }

    /** Front (output-side) group, or {@code null} if empty. */
    public ItemGroup peekFirst() { return size > 0 ? groups[0]        : null; }

    /** Back (input-side) group, or {@code null} if empty. */
    public ItemGroup peekLast()  { return size > 0 ? groups[size - 1] : null; }

    /**
     * Returns a read-only view of the backing array.
     * Valid indices are {@code [0, groupCount())}.
     * <p><b>Do not modify the returned array.</b>
     */
    public ItemGroup[] groupArray() { return groups; }

    /**
     * Clears all groups from this lane.
     */
    public void clearGroups() {
        for (int i = 0; i < size; i++) groups[i] = null;
        size = 0;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    public void addLast(ItemGroup g) {
        if (size == groups.length) {
            ItemGroup[] grown = new ItemGroup[groups.length * 2];
            System.arraycopy(groups, 0, grown, 0, size);
            groups = grown;
        }
        groups[size++] = g;
    }

    /** Removes and returns the front group (index 0). */
    public ItemGroup pollFirst() {
        final ItemGroup front = groups[0];
        final int       tail  = size - 1;
        System.arraycopy(groups, 1, groups, 0, tail);
        groups[tail] = null;
        size--;
        return front;
    }

    /** Removes the group at {@code idx}, shifting the tail down. */
    private void removeAt(int idx) {
        final int tail = size - 1;
        if (idx < tail) System.arraycopy(groups, idx + 1, groups, idx, tail - idx);
        groups[tail] = null;
        size--;
    }

    // ── Snapshot (for client sync) ────────────────────────────────────────────

    /** Returns a deep copy of this lane for packet serialisation or client seeding. */
    public BeltLane deepCopy() {
        BeltLane copy = new BeltLane(speed);
        copy.groups = new ItemGroup[Math.max(INITIAL_CAPACITY, size)];
        int valid = 0;
        for (int i = 0; i < size; i++) {
            if (groups[i] != null) {
                copy.groups[valid++] = groups[i].copy();
            }
        }
        copy.size = valid;
        return copy;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("speed", speed);
        ListTag list = new ListTag();
        for (int i = 0; i < size; i++) {
            if (groups[i] != null) list.add(groups[i].save(registries));
        }
        tag.put("groups", list);
        return tag;
    }

    public static BeltLane load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        float spd = tag.contains("speed") ? tag.getFloat("speed") : SPEED_DEFAULT;
        if (spd <= 0.0f) spd = SPEED_DEFAULT;
        BeltLane lane = new BeltLane(spd);
        ListTag list = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            lane.addLast(ItemGroup.load(list.getCompound(i), registries));
        }
        return lane;
    }
}
