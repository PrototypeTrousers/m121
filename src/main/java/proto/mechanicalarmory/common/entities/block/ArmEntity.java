package proto.mechanicalarmory.common.entities.block;

import brachy.modularui.api.IPanelHandler;
import brachy.modularui.api.IUIHolder;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.SchemaRenderer;
import brachy.modularui.drawable.schema.BlockHighlight;
import brachy.modularui.drawable.schema.SchemaLevel;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.ModularScreen;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Color;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.SchemaWidget;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import proto.mechanicalarmory.MechanicalArmory;
import proto.mechanicalarmory.client.modularui.schemas.BoxSchema2;
import proto.mechanicalarmory.common.entities.MAEntities;
import proto.mechanicalarmory.common.logic.*;
import proto.mechanicalarmory.common.logic.filter.ItemContextFilter;
import proto.mechanicalarmory.common.network.ArmClickPayload;

import java.util.ArrayList;
import java.util.List;

import static proto.mechanicalarmory.client.modularui.Utils.getAllInside;
import static proto.mechanicalarmory.common.logic.Action.DELIVER;
import static proto.mechanicalarmory.common.logic.Action.RETRIEVE;

public class ArmEntity extends BlockEntity implements BlockEntityTicker<ArmEntity>, IUIHolder<PosGuiData> {
    private final Targeting targeting = new Targeting();
    private final MotorCortex motorCortex;
    private final WorkStatus workStatus = new WorkStatus();
    protected ItemStackHandler itemHandler = new ArmItemHandler(1);
    protected ItemContextFilter itemContextFilter = new ItemContextFilter(5);
    private Vector3d armPoint;
    float armSize = 2f;
    InteractionType interactionType = InteractionType.ITEM;

    List<Pair<BlockPos, Direction>> logicSources = new ArrayList<>();

    public ArmEntity(BlockPos pos, BlockState state) {
        super(MAEntities.ARM_ENTITY.get(), pos, state);
        motorCortex = new MotorCortex(this, 1f, interactionType);
    }

    private int progress = 0;

    public float[] getAnimationRotation(int idx) {
        return motorCortex.getAnimationRotation(idx);
    }

    public float[] getRotation(int idx) {
        return motorCortex.getRotation(idx);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, pRegistries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider lookupProvider) {
        super.onDataPacket(net, pkt, lookupProvider);
        handleUpdateTag(pkt.getTag(), lookupProvider);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        armPoint = new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(compound, registries);
        compound.put("rotation", motorCortex.serializeNBT(registries));
        compound.put("targeting", targeting.serializeNBT(registries));
        compound.put("workStatus", workStatus.serializeNBT(registries));
        compound.put("itemHandler", itemHandler.serializeNBT(registries));
        compound.put("logic", serializeLogic(registries));
    }

    protected CompoundTag serializeLogic(HolderLookup.Provider provider) {
        ListTag nbtTagList = new ListTag();
        for (int i = 0; i < logicSources.size(); i++) {
                CompoundTag compound = new CompoundTag();
                compound.put("logicPos" + i, NbtUtils.writeBlockPos(logicSources.get(i).key()));
                compound.putInt("logicFacing" + i, logicSources.get(i).value().ordinal());
                nbtTagList.add(compound);
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put("Logic", nbtTagList);
        return nbt;
    }

    protected void deSerializeLogic(HolderLookup.Provider provider, CompoundTag nbt) {
        ListTag tagList = nbt.getList("Logic", Tag.TAG_COMPOUND);
        logicSources.clear();
        for (int i = 0; i < tagList.size(); i += 2) {
            var pos = NbtUtils.readBlockPos(tagList.getCompound(i), "logicPos" + i);
            if (pos.isPresent()) {
                logicSources.add(Pair.of(pos.get(), Direction.from3DDataValue(tagList.getCompound(i + 1).getInt("logicFacing"))));
            }
        }
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(compound, registries);
        motorCortex.deserializeNBT(registries, compound.getList("rotation", CompoundTag.TAG_FLOAT));
        targeting.deserializeNBT(registries, compound.getCompound("targeting"));
        workStatus.deserializeNBT(registries, compound.getCompound("workStatus"));
        itemHandler.deserializeNBT(registries, compound.getCompound("itemHandler"));
        deSerializeLogic(registries, compound.getCompound("logic"));
    }

    public ItemStackHandler getHandler() {
        return itemHandler;
    }

    public ItemStack getItemStack() {
        return itemHandler.getStackInSlot(0);
    }

    public ItemContextFilter getItemContextFilter() {
        return itemContextFilter;
    }

    public ActionResult interact(Action action, Pair<BlockPos, Direction> blkFace) {
        if (getLevel().isClientSide()) {
            return ActionResult.CONTINUE;
        }
        BlockEntity te = level.getBlockEntity(blkFace.key().offset((this.worldPosition)));
        if (te != null) {
            IItemHandler itemHandler = level.getCapability(Capabilities.ItemHandler.BLOCK, blkFace.key().offset((this.worldPosition)), blkFace.value());
            if (itemHandler != null) {
                if (action == RETRIEVE) {
                    if (this.itemHandler.getStackInSlot(0).isEmpty()) {
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            if (!itemHandler.extractItem(i, 1, true).isEmpty()) {
                                ItemStack itemStack = itemHandler.extractItem(i, 1, true);
                                if (!itemStack.isEmpty()) {
                                    itemStack = itemHandler.extractItem(i, 1, false);
                                    this.itemHandler.insertItem(0, itemStack, false);
                                    return ActionResult.SUCCESS;
                                }
                            }
                        }
                    }
                } else if (action == DELIVER) {
                    ItemStack itemStack = this.itemHandler.extractItem(0, 1, true);
                    if (!itemStack.isEmpty()) {
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            if (itemHandler.insertItem(i, itemStack, true).isEmpty()) {
                                itemStack = this.itemHandler.extractItem(0, 1, false);
                                itemHandler.insertItem(i, itemStack, false);
                                return ActionResult.SUCCESS;
                            }
                        }
                    }
                }
            }
        }
        return ActionResult.CONTINUE;
    }


    @Override
    public void tick(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ArmEntity blockEntity) {
        if (!hasInput() || !hasOutput()) {
            return;
        }
        if (workStatus.getType() == ActionTypes.IDLING) {
            if (hasInput() && hasOutput()) {
                updateWorkStatus(ActionTypes.MOVEMENT, RETRIEVE);
            }
        } else if (workStatus.getType() == ActionTypes.MOVEMENT) {
            if (workStatus.getAction() == RETRIEVE) {
                ActionResult result = motorCortex.move( targeting.getSourceVec() , targeting.getSourceFacing());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.INTERACTION, RETRIEVE);
                }
            } else if (workStatus.getAction() == DELIVER) {
                ActionResult result = motorCortex.move(targeting.getTargetVec(), targeting.getTargetFacing());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.INTERACTION, DELIVER);
                }
            }
        } else if (workStatus.getType() == ActionTypes.INTERACTION) {
            if (workStatus.getAction() == RETRIEVE) {
                ActionResult result = interact(RETRIEVE, targeting.getSource());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.MOVEMENT, DELIVER);
                }
            } else if (workStatus.getAction() == DELIVER) {
                ActionResult result = interact(DELIVER, targeting.getTarget());
                if (result == ActionResult.SUCCESS) {
                    updateWorkStatus(ActionTypes.MOVEMENT, RETRIEVE);
                }
            }
        }
        if (!getLevel().isClientSide && this.progress++ == 100) {
            this.progress = 0;
        }
    }

    public void updateWorkStatus(ActionTypes type, Action action) {
        workStatus.setType(type);
        workStatus.setAction(action);
        level.markAndNotifyBlock(worldPosition, level.getChunkAt(worldPosition), getBlockState(), getBlockState(), 3, 3);
//        markDirty();
    }

    public WorkStatus getWorkStatus() {
        return workStatus;
    }

    public void setSource(BlockPos sourcePos, Direction sourceFacing) {
        targeting.setSource(sourcePos.subtract(this.getBlockPos()) , sourceFacing);
//        markDirty();
    }

    public Pair<BlockPos, Direction> getSource() {
        return targeting.getSource();
    }

    public void setTarget(BlockPos targetPos, Direction targetFacing) {
        targeting.setTarget(targetPos.subtract(this.getBlockPos()), targetFacing);
//        markDirty();
    }

    public Pair<BlockPos, Direction> getTarget() {
        return targeting.getTarget();
    }

    public boolean hasInput() {
        return targeting.hasInput();
    }

    public boolean hasOutput() {
        return targeting.hasOutput();
    }

    public Targeting getTargeting() {
        return targeting;
    }

    public void addLogicSource(BlockPos blockPos, Direction direction) {
        logicSources.add(Pair.of(blockPos, direction));
    }

    public List<Pair<BlockPos, Direction>> getLogicList() {
        return logicSources;
    }

    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel<?> mainPanel) {
        return new ModularScreen(MechanicalArmory.MODID, mainPanel);
    }

    @Override
    public ModularPanel<?> buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        ModularPanel<?> panel = new ModularPanel<>("arm");


        SchemaLevel sl = new SchemaLevel();

        panel.onCloseAction(() -> {
            if (sl.hasFilledBlocks()) {
                sl.getFilledBlocks().forEach(b -> {
                            sl.setBlock(b, Blocks.AIR.defaultBlockState(), 0);
                        }
                );
            }
        });

        int r = 5;
        var blocks = getAllInside(getBlockPos().offset(-r, -r, -r), getBlockPos().offset(r, r, r), false);
        for (BlockPos blockPos : blocks) {
            sl.setBlock(blockPos.subtract(this.getBlockPos()), level.getBlockState(blockPos), 0);
        }

        var schema = BoxSchema2.of(sl, BlockPos.ZERO, 5);

        SchemaRenderer renderer;
        ConfigSchemaWidget configSchemaWidget;

        if (level.isClientSide) {
            renderer = schema.createRenderer();
            renderer.highlightRenderer(new BlockHighlight(Color.withAlpha(Color.RED.main, 0.5f))
                    .allSides(false)
                    .thickness(0.1f));

            configSchemaWidget = new ConfigSchemaWidget(renderer, panel, syncManager);
            configSchemaWidget
                    .full()
                    .enableDragTranslation(false);

            panel.child(configSchemaWidget);


        } else {
            configSchemaWidget = null;
        }

        IntSyncValue slot = new IntSyncValue(() -> level.isClientSide ? 0 : 1);
        syncManager.syncValue("slot", slot);

        syncManager.syncedPanel("clicked", true, (mainPanel, player) ->
                new ModularPanel<>("clicked2")
                        .child(new ListWidget<>()
                                .children(buttons, b -> new ButtonWidget<>()
                                        .overlay(Text.str(b.key))
                                        .onMousePressed((context, button) -> {
                                                    if (level.isClientSide) {
                                                        if (button == 0 || button == 1) {
                                                            PacketDistributor.sendToServer(
                                                                    new ArmClickPayload(
                                                                            this.getBlockPos(),
                                                                            configSchemaWidget.blockHitResult.getBlockPos().offset(this.getBlockPos()),
                                                                            configSchemaWidget.blockHitResult.getDirection(),
                                                                            b.configuration
                                                                    ));
                                                            return true;
                                                        }
                                                    }
                                                    return false;
                                                }
                                        )
                                        .size(3 * 16, 16))
                                .coverChildren())
                        .child(ButtonWidget.panelCloseButton())
                        .draggable(true)
                        .disablePanelsBelow(true)
                        .relative(panel)
                        .top(0)
                        .rightRel(1f)
                        .coverChildren()
                        .closeOnOutOfBoundsClick(true));
        panel.child(ButtonWidget.panelCloseButton());
        return panel;
    }

    static Button IN_BUTTON = new Button("IN", ArmClickPayload.Configuration.SOURCE);
    static Button OUT_BUTTON = new Button("OUT", ArmClickPayload.Configuration.TARGET);

    static List<Button> buttons = List.of(IN_BUTTON, OUT_BUTTON);

    record Button(String key, ArmClickPayload.Configuration configuration){}

    static class ConfigSchemaWidget extends SchemaWidget {
        ModularPanel<?> panel;
        PanelSyncManager syncManager;
        BlockHitResult blockHitResult;
        public ConfigSchemaWidget(SchemaRenderer renderer, ModularPanel<?> panel, PanelSyncManager syncManager) {
            super(renderer);
            this.panel = panel;
            this.syncManager = syncManager;
            this.blockHitResult = null;
        }

        @Override
        public @NotNull Result onMousePressed(int button) {
            if (getSchemaRenderer().lastRayTrace().getType() == HitResult.Type.BLOCK) {
                blockHitResult = getSchemaRenderer().lastRayTrace();
                IPanelHandler colorPicker1 = syncManager.findPanelHandler("clicked");
                colorPicker1.openPanel();

                return Result.SUCCESS;
            }
            return super.onMousePressed(button);
        }

        public BlockHitResult getBlockHitResult() {
            return blockHitResult;
        }
    }

    public class ArmItemHandler extends ItemStackHandler {
        public ArmItemHandler(int i) {
            super(i);
        }

        @Override
        protected void onContentsChanged(int slot) {

            level.markAndNotifyBlock(worldPosition, level.getChunkAt(worldPosition), getBlockState(), getBlockState(), 3, 3);
        }
    }
}