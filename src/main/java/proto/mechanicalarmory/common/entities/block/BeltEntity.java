package proto.mechanicalarmory.common.entities.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.entities.MAEntities;

/**
 * A thin rendering anchor for a conveyor belt block.
 *
 * <p>This block entity holds <em>no authoritative item data</em>.  All item
 * state lives in {@link proto.mechanicalarmory.common.belt.network.BeltNetworkData}.
 *
 * <p>On the server side it stores only the {@code nodeId} used to look up the
 * corresponding {@link BeltNode}.
 *
 * <p>On the client side it holds a deep-copy of the two {@link BeltLane}s that
 * the {@link proto.mechanicalarmory.client.flywheel.instances.belt.BeltVisual}
 * simulates autonomously every frame.
 */
public class BeltEntity extends BlockEntity {

    // ── Shared ────────────────────────────────────────────────────────────────

    /** Server: UUID of the BeltNode in BeltNetworkData. Persisted in NBT. */
    @Nullable
    private java.util.UUID nodeId;

    // ── Client-only simulation state ──────────────────────────────────────────

    /**
     * Deep copies of the two belt lanes, seeded from the server on chunk load
     * and advanced autonomously every frame by {@code BeltVisual}.
     * These lanes are only meaningful on the logical client.
     */
    private BeltLane[] clientLanes = new BeltLane[]{
            BeltLane.standard(),
            BeltLane.standard()
    };

    /** The server game-tick at which the snapshot was taken (used for catch-up). */
    private long clientSeedTick = 0L;

    /** Last game time when client simulation was advanced. */
    private long clientLastTickedGameTime = 0L;

    /** Whether this belt is a loop wrap-point (client copy of BeltNode.isWrapPoint). */
    private boolean clientWrapPoint = false;

    /**
     * Whether this belt has a real output connection (another belt or machine).
     * False means it is a terminal — items stall at the output end.
     * Synced from server via payload.
     */
    private boolean clientHasOutput = false;

    /** Whether the belt is stopped (mirrors server-side stop state). */
    private boolean clientStopped = false;

    // ── Construction ──────────────────────────────────────────────────────────

    public BeltEntity(BlockPos pos, BlockState state) {
        super(MAEntities.BELT_ENTITY.get(), pos, state);
    }

    // ── Server sync ───────────────────────────────────────────────────────────

    /** Called by BeltNetworkData when the chunk this BE is in loads. */
    public void syncFromNetwork(BeltNode node) {
        this.nodeId = node.nodeId();
    }

    // ── Client sync ───────────────────────────────────────────────────────────

    /**
     * Called on the client when a {@code BeltInitPayload} or
     * {@code BeltCorrectionPayload} arrives for this position.
     */
    public void applyClientSeed(BeltLane lane0, BeltLane lane1, long serverTick,
                                  boolean stopped, boolean wrapPoint, boolean hasOutput) {
        this.clientLanes[0] = lane0.deepCopy();
        this.clientLanes[1] = lane1.deepCopy();
        this.clientSeedTick  = serverTick;
        this.clientStopped   = stopped;
        this.clientWrapPoint = wrapPoint;
        this.clientHasOutput = hasOutput;
        if (this.level != null) {
            this.clientLastTickedGameTime = this.level.getGameTime();
        }
    }

    /**
     * Advance the client-side simulation when the game tick advances.
     */
    public void advanceClientSimulation(net.minecraft.world.level.Level lvl, net.minecraft.core.Direction facing) {
        long gameTime = lvl.getGameTime();
        if (clientLastTickedGameTime == 0) {
            clientLastTickedGameTime = gameTime;
            return;
        }
        long elapsed = gameTime - clientLastTickedGameTime;
        if (elapsed <= 0) return;
        clientLastTickedGameTime = gameTime;

        if (clientStopped) return;

        long ticks = Math.min(elapsed, 20);
        for (int t = 0; t < ticks; t++) {
            for (int l = 0; l < 2; l++) {
                BeltLane lane = clientLanes[l];
                lane.advance(1.0f, clientHasOutput ? Float.MAX_VALUE : 1.0f);

                if (clientHasOutput) {
                    BlockEntity next = lvl.getBlockEntity(worldPosition.relative(facing));
                    if (next instanceof BeltEntity outBe) {
                        lane.transferOut(outBe.clientLane(l));
                    } else {
                        while (!lane.groups().isEmpty() && lane.groups().peekFirst().headPos() >= 1.0f) {
                            lane.groups().pollFirst();
                        }
                    }
                }
            }
        }
    }

    public BeltLane clientLane(int i)   { return clientLanes[i]; }
    public long     clientSeedTick()    { return clientSeedTick; }
    public boolean  isClientStopped()   { return clientStopped; }
    public boolean  isClientWrapPoint() { return clientWrapPoint; }
    public boolean  clientHasOutput()   { return clientHasOutput; }

    // ── NBT (server-side: only store nodeId) ─────────────────────────────────

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        if (nodeId != null) tag.putUUID("nodeId", nodeId);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("nodeId")) nodeId = tag.getUUID("nodeId");
    }

    // ── Update packet (vanilla BE sync on chunk load & client join) ─────────

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        if (level instanceof net.minecraft.server.level.ServerLevel srv) {
            proto.mechanicalarmory.common.belt.network.BeltNetworkData data =
                    proto.mechanicalarmory.common.belt.network.BeltNetworkData.get(srv);
            BeltNode node = data.nodeAt(worldPosition);
            if (node != null) {
                tag.put("lane0", node.lane(0).save(registries));
                tag.put("lane1", node.lane(1).save(registries));
                tag.putBoolean("wrapPoint", node.isWrapPoint());
                tag.putBoolean("hasOutput", node.outputId() != null);
                tag.putBoolean("stopped", node.isStopped());
                tag.putLong("serverTick", srv.getGameTime());
            }
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        super.handleUpdateTag(tag, lookupProvider);
        if (tag.contains("lane0") && tag.contains("lane1")) {
            BeltLane l0 = BeltLane.load(tag.getCompound("lane0"), lookupProvider);
            BeltLane l1 = BeltLane.load(tag.getCompound("lane1"), lookupProvider);
            boolean wrap = tag.getBoolean("wrapPoint");
            boolean hasOut = tag.getBoolean("hasOutput");
            boolean stopped = tag.getBoolean("stopped");
            long serverTick = tag.getLong("serverTick");
            applyClientSeed(l0, l1, serverTick, stopped, wrap, hasOut);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt,
                              HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag, lookupProvider);
        }
    }

    @Nullable
    public java.util.UUID nodeId() { return nodeId; }
}
