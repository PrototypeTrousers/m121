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

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            proto.mechanicalarmory.common.belt.data.BeltNode existing =
                    proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().getNode(worldPosition);
            if (existing != null) {
                // Restore clientLanes from existing simulated network node
                this.clientLanes[0] = existing.lane(0).deepCopy();
                this.clientLanes[1] = existing.lane(1).deepCopy();
                this.clientStopped = existing.isStopped();
            } else {
                proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().updateNode(
                        worldPosition, clientLanes[0], clientLanes[1], clientStopped, clientWrapPoint, clientHasOutput);
            }
        }
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

    private boolean isAdvancing = false;

    /**
     * Advance the client-side simulation when the game tick advances.
     * Evaluates downstream belts first so space is freed up before upstream transfers.
     */
    public void advanceClientSimulation(net.minecraft.world.level.Level lvl, net.minecraft.core.Direction facing) {
        long gameTime = lvl.getGameTime();
        if (clientLastTickedGameTime == 0) {
            clientLastTickedGameTime = gameTime;
            return;
        }
        long elapsed = gameTime - clientLastTickedGameTime;
        if (elapsed <= 0) return;

        if (isAdvancing) return;
        isAdvancing = true;

        try {
            clientLastTickedGameTime = gameTime;
            if (clientStopped) return;

            updateClientCurveSpeeds(lvl, facing);

            // Ensure downstream belt ticks first so space is freed up
            if (clientHasOutput) {
                net.minecraft.core.BlockPos nextPos = worldPosition.relative(facing);
                BlockEntity next = lvl.getBlockEntity(nextPos);
                if (next instanceof BeltEntity outBe) {
                    net.minecraft.world.level.block.state.BlockState outState = lvl.getBlockState(nextPos);
                    if (outState.getBlock() instanceof proto.mechanicalarmory.common.blocks.BlockBelt) {
                        net.minecraft.core.Direction outFacing = outState.getValue(proto.mechanicalarmory.common.blocks.BlockBelt.FACING);
                        outBe.advanceClientSimulation(lvl, outFacing);
                    }
                }
            }

            long ticks = Math.min(elapsed, 20);
            for (int t = 0; t < ticks; t++) {
                for (int l = 0; l < 2; l++) {
                    BeltLane lane = clientLanes[l];
                    BlockEntity next = clientHasOutput ? lvl.getBlockEntity(worldPosition.relative(facing)) : null;
                    BeltEntity outBe = (next instanceof BeltEntity be) ? be : null;
                    BeltLane outLane = (outBe != null) ? outBe.clientLane(l) : null;

                    float maxExitPos = 1.0f;
                    if (clientHasOutput) {
                        if (outLane != null) {
                            if (outLane.isEmpty()) {
                                maxExitPos = Float.MAX_VALUE;
                            } else {
                                float outRoom = outLane.peekLast().tailPos(outLane.itemSpacing());
                                maxExitPos = 1.0f + outRoom * (lane.itemSpacing() / outLane.itemSpacing());
                            }
                        } else {
                            maxExitPos = Float.MAX_VALUE;
                        }
                    }

                    lane.advance(1.0f, maxExitPos);

                    if (clientHasOutput) {
                        if (outLane != null) {
                            lane.transferOut(outLane);
                        } else {
                            while (!lane.isEmpty() && lane.peekFirst().headPos() >= 1.0f) {
                                lane.pollFirst();
                            }
                        }
                    }
                }
            }
        } finally {
            isAdvancing = false;
        }
    }

    private void updateClientCurveSpeeds(net.minecraft.world.level.Level lvl, net.minecraft.core.Direction facing) {
        net.minecraft.core.Direction back = facing.getOpposite();
        net.minecraft.core.Direction left = facing.getCounterClockWise();
        net.minecraft.core.Direction right = facing.getClockWise();

        boolean hasBack = isBeltFacingInto(lvl, worldPosition.relative(back), worldPosition);
        boolean hasLeft = isBeltFacingInto(lvl, worldPosition.relative(left), worldPosition);
        boolean hasRight = isBeltFacingInto(lvl, worldPosition.relative(right), worldPosition);

        if (!hasBack) {
            if (hasRight && !hasLeft) {
                clientLanes[1].setSpeed(BeltLane.SPEED_INNER);
                clientLanes[0].setSpeed(BeltLane.SPEED_OUTER);
                return;
            } else if (hasLeft && !hasRight) {
                clientLanes[0].setSpeed(BeltLane.SPEED_INNER);
                clientLanes[1].setSpeed(BeltLane.SPEED_OUTER);
                return;
            }
        }
        clientLanes[0].setSpeed(BeltLane.SPEED_DEFAULT);
        clientLanes[1].setSpeed(BeltLane.SPEED_DEFAULT);
    }

    private static boolean isBeltFacingInto(net.minecraft.world.level.Level lvl, BlockPos fromPos, BlockPos toPos) {
        net.minecraft.world.level.block.state.BlockState state = lvl.getBlockState(fromPos);
        if (state.getBlock() instanceof proto.mechanicalarmory.common.blocks.BlockBelt) {
            net.minecraft.core.Direction f = state.getValue(proto.mechanicalarmory.common.blocks.BlockBelt.FACING);
            return fromPos.relative(f).equals(toPos);
        }
        return false;
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
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("nodeId")) nodeId = tag.getUUID("nodeId");
        applySyncTag(tag, registries);
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
        applySyncTag(tag, lookupProvider);
    }

    private void applySyncTag(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        if (tag.contains("lane0") && tag.contains("lane1")) {
            BeltLane l0 = BeltLane.load(tag.getCompound("lane0"), lookupProvider);
            BeltLane l1 = BeltLane.load(tag.getCompound("lane1"), lookupProvider);
            boolean wrap = tag.getBoolean("wrapPoint");
            boolean hasOut = tag.getBoolean("hasOutput");
            boolean stopped = tag.getBoolean("stopped");
            long serverTick = tag.getLong("serverTick");
            applyClientSeed(l0, l1, serverTick, stopped, wrap, hasOut);

            if (level != null && level.isClientSide()) {
                proto.mechanicalarmory.client.belt.ClientBeltNetwork.get().updateNode(
                        worldPosition, l0, l1, stopped, wrap, hasOut);
            }
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
