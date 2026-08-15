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

    // ── Simulation ────────────────────────────────────────────────────────────

    /**
     * Advance all groups by {@code dt} belt-lengths.
     * <p>This does NOT pop groups that exceed 1.0 — callers are responsible for
     * that so they can decide whether to transfer or wrap.
     */
    public void advance(float dt) {
        float delta = speed * dt;
        for (ItemGroup g : groups) {
            g.advanceHead(delta);
        }
    }

    /**
     * Called by the network tick on a <em>wrap-point</em> node after
     * {@link #advance}.  Any group whose <em>entire body</em> has passed 1.0
     * (i.e. {@code tailPos >= 1.0}) is popped from the front and re-queued at
     * the back with a negative headPos such that the group immediately follows
     * the current last group.
     */
    public void applyWrap() {
        while (!groups.isEmpty()) {
            ItemGroup front = groups.peekFirst();
            if (front.tailPos() < 1.0f) {
                // Part of the group is still on the belt — stop here.
                break;
            }
            groups.pollFirst();

            // Calculate headPos so the wrapping group's tail is just behind the
            // current last group (or at 0 minus one item-length if empty).
            float newHeadPos;
            if (groups.isEmpty()) {
                // Re-enter just behind position 0
                newHeadPos = -(front.count() * ITEM_SPACING) + speed;
            } else {
                ItemGroup last = groups.peekLast();
                float lastTail = last.tailPos();
                newHeadPos = lastTail - GAP_MERGE_THRESHOLD - (front.count() * ITEM_SPACING)
                        + front.count() * ITEM_SPACING; // = lastTail - GAP_MERGE_THRESHOLD
                newHeadPos = lastTail - GAP_MERGE_THRESHOLD;
            }
            front.setHeadPos(newHeadPos);
            groups.addLast(front);
        }
    }

    /**
     * Attempt to transfer items to the given output lane.
     * <p>Groups whose {@code headPos >= 1.0} are pushed into the output lane's
     * back. This is called for non-wrap terminals during the tick.
     *
     * @return true if any item was transferred
     */
    public boolean transferOut(BeltLane output) {
        boolean transferred = false;
        while (!groups.isEmpty()) {
            ItemGroup front = groups.peekFirst();
            if (front.headPos() < 1.0f) break;

            groups.pollFirst();

            // Re-centre headPos at the output belt's output-most entry point
            float newHead = front.headPos() - 1.0f;
            front.setHeadPos(Math.min(newHead, ITEM_SPACING * front.count()));
            output.insertBack(front);
            transferred = true;
        }
        return transferred;
    }

    /**
     * Insert an item group at the back (input side) of this lane.
     * Merges with the current last group if same type and gap is small.
     */
    public void insertBack(ItemGroup incoming) {
        if (!groups.isEmpty()) {
            ItemGroup last = groups.peekLast();
            float gap = last.tailPos() - incoming.headPos();
            if (gap <= GAP_MERGE_THRESHOLD && incoming.sameType(last)) {
                // Merge: extend the existing last group
                int extra = incoming.count();
                last.setCount(last.count() + extra);
                // Tail expands backward automatically via tailPos() = headPos - count*spacing
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
        float entryHead = groups.isEmpty()
                ? ITEM_SPACING
                : groups.peekLast().tailPos() - GAP_MERGE_THRESHOLD;

        if (entryHead <= 0 && !groups.isEmpty()) {
            return false; // Lane is backed up to the input
        }
        insertBack(new ItemGroup(item, 1, Math.max(entryHead, ITEM_SPACING)));
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
        BeltLane lane = new BeltLane(tag.getFloat("speed"));
        ListTag list = tag.getList("groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            lane.groups.addLast(ItemGroup.load(list.getCompound(i), registries));
        }
        return lane;
    }
}
