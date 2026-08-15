package proto.mechanicalarmory.common.belt.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A single lane of a conveyor belt.
 *
 * <p>The lane is a 1-D coordinate space where 0.0 is the belt's input end and
 * 1.0 is the output end.  Groups are stored front-first (index 0 = closest to
 * the output). The speed is measured in belt-lengths per tick.
 *
 * <p>Standard belt: {@value #SPEED_DEFAULT} belt-lengths/tick = 4 blocks/s at
 * 20 TPS, since each belt block is 1 block long.
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

    // ── State ─────────────────────────────────────────────────────────────────

    private float speed;

    /**
     * Groups in front-first order (front = output side = highest headPos).
     * <p>Invariant: groups[0].headPos >= groups[1].headPos >= …
     */
    private final ArrayDeque<ItemGroup> groups = new ArrayDeque<>();

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltLane(float speed) {
        this.speed = speed;
    }

    public static BeltLane standard() {
        return new BeltLane(SPEED_DEFAULT);
    }

    public float itemSpacing() {
        if (speed <= 0) return ITEM_SPACING;
        return (speed / SPEED_DEFAULT) * ITEM_SPACING;
    }

    // ── Simulation ────────────────────────────────────────────────────────────

    /**
     * Advance all groups by {@code dt} belt-lengths.
     * Groups back up behind preceding groups when blocked.
     *
     * @param dt delta time in ticks (1.0f for full tick)
     * @param maxExitPos maximum position the front group can reach before stopping
     *                   (1.0f for dead-end terminals, Float.MAX_VALUE if it can exit)
     */
    public void advance(float dt, float maxExitPos) {
        float delta = speed * dt;
        if (delta <= 0 || groups.isEmpty()) return;

        float spacing = itemSpacing();
        List<ItemGroup> list = new ArrayList<>(groups);
        int n = list.size();

        // 1. Advance front group up to maxExitPos
        ItemGroup front = list.get(0);
        float newHead = Math.min(front.headPos() + delta, maxExitPos);
        front.setHeadPos(newHead);

        // 2. Advance subsequent groups, clamping behind previous group's tail
        for (int i = 1; i < n; i++) {
            ItemGroup prev = list.get(i - 1);
            ItemGroup curr = list.get(i);
            float maxHeadForCurr = prev.tailPos(spacing);
            float desiredHead = curr.headPos() + delta;
            curr.setHeadPos(Math.min(desiredHead, maxHeadForCurr));
        }

        // 3. Merge adjacent groups of same item type that have touched
        groups.clear();
        ItemGroup currentMerged = list.get(0);
        for (int i = 1; i < n; i++) {
            ItemGroup next = list.get(i);
            float gap = currentMerged.tailPos(spacing) - next.headPos();
            if (gap <= GAP_MERGE_THRESHOLD && currentMerged.sameType(next)) {
                currentMerged.setCount(currentMerged.count() + next.count());
            } else {
                groups.addLast(currentMerged);
                currentMerged = next;
            }
        }
        groups.addLast(currentMerged);
    }

    /**
     * Backward-compatible overload.
     */
    public void advance(float dt) {
        advance(dt, Float.MAX_VALUE);
    }

    /**
     * Attempt to transfer items to the given output lane.
     * Groups whose {@code headPos >= 1.0} are pushed into the output lane's
     * back if the output lane has room. If the output lane is backed up, items
     * wait at position 1.0 on this belt.
     *
     * @return true if any item was transferred
     */
    public boolean transferOut(BeltLane output) {
        boolean transferred = false;
        while (!groups.isEmpty()) {
            ItemGroup front = groups.peekFirst();
            if (front.headPos() < 1.0f) break;

            // Check space at the back of the output lane
            float outputSpacing = output.itemSpacing();
            float outputRoom = output.groups.isEmpty()
                    ? Float.MAX_VALUE
                    : output.groups.peekLast().tailPos(outputSpacing);

            if (outputRoom <= 0.001f) {
                // Downstream lane is backed up; clamp lead item at 1.0 on this belt
                front.setHeadPos(1.0f);
                break;
            }

            // Excess distance into the output lane
            float excess = front.headPos() - 1.0f;
            float newHead = Math.min(excess, outputRoom);
            if (newHead <= 0f) {
                newHead = Math.min(outputSpacing, outputRoom);
            }

            if (front.count() == 1) {
                groups.pollFirst();
                front.setHeadPos(newHead);
                output.insertBack(front);
            } else {
                // Split 1 item from front of group to transfer
                front.setCount(front.count() - 1);
                front.advanceHead(-itemSpacing());

                ItemGroup single = new ItemGroup(front.item(), 1, newHead);
                output.insertBack(single);
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
        float spacing = itemSpacing();
        if (!groups.isEmpty()) {
            ItemGroup last = groups.peekLast();
            if (incoming.headPos() > last.tailPos(spacing)) {
                incoming.setHeadPos(last.tailPos(spacing));
            }
            float gap = last.tailPos(spacing) - incoming.headPos();
            if (gap <= GAP_MERGE_THRESHOLD && incoming.sameType(last)) {
                last.setCount(last.count() + incoming.count());
                return;
            }
        }
        groups.addLast(incoming);
    }

    /**
     * Insert a single item at the back input tip of the lane.
     * Returns {@code false} if there is no room (the last group's tail is <= 0).
     */
    public boolean insertItem(ItemStack item) {
        float spacing = itemSpacing();
        float maxAvailableHead = groups.isEmpty()
                ? spacing
                : groups.peekLast().tailPos(spacing);

        if (maxAvailableHead <= 0.0f && !groups.isEmpty()) {
            return false; // Lane is full / backed up to input
        }
        float entryHead = Math.min(spacing, maxAvailableHead);
        insertBack(new ItemGroup(item, 1, entryHead));
        return true;
    }

    /**
     * Remove one item from the group closest to {@code targetPos} (for player
     * extraction / hopper pulls).  Returns the extracted item or
     * {@link ItemStack#EMPTY}.
     */
    public ItemStack extractNearest(float targetPos) {
        ItemGroup best = null;
        float bestDist = Float.MAX_VALUE;

        for (ItemGroup g : groups) {
            float dist = Math.abs(g.headPos() - targetPos);
            if (dist < bestDist) {
                bestDist = dist;
                best = g;
            }
        }
        if (best == null) return ItemStack.EMPTY;

        ItemStack result = best.item().copyWithCount(1);
        if (best.count() == 1) {
            groups.remove(best);
        } else {
            best.setCount(best.count() - 1);
            // Shift headPos so one item disappears from the front of the group
            best.advanceHead(-ITEM_SPACING);
        }
        return result;
    }

    // ── Stopped / Running ─────────────────────────────────────────────────────

    public void setSpeed(float speed) { this.speed = speed; }
    public float speed() { return speed; }

    public boolean isEmpty() { return groups.isEmpty(); }

    public Deque<ItemGroup> groups() { return groups; }

    // ── Snapshot (for client sync) ────────────────────────────────────────────

    /** Returns a deep copy of this lane for packet serialisation or client seeding. */
    public BeltLane deepCopy() {
        BeltLane copy = new BeltLane(speed);
        for (ItemGroup g : groups) {
            copy.groups.addLast(g.copy());
        }
        return copy;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public CompoundTag save(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("speed", speed);
        ListTag list = new ListTag();
        for (ItemGroup g : groups) {
            list.add(g.save(registries));
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
            lane.groups.addLast(ItemGroup.load(list.getCompound(i), registries));
        }
        return lane;
    }
}
