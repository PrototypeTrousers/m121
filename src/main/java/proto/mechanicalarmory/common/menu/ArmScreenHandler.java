package proto.mechanicalarmory.common.menu;

import io.wispforest.owo.client.screens.SlotGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;
import proto.mechanicalarmory.common.entities.block.ArmEntity;

import java.util.Objects;

public class ArmScreenHandler extends AbstractContainerMenu {

    @NotNull
    protected final BlockPos blockPos;
    @NotNull
    protected final ArmEntity blockEntity;
    @NotNull
    protected final Inventory playerInventory;
    @NotNull
    protected final ItemStackHandler filterHandler;

    protected int handSlotIdx;
    protected int playerInventorySlotIdx;
    protected int filterSlotIdx;

    public ArmScreenHandler(int syncId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(syncId, playerInventory, Objects.requireNonNull(playerInventory.player.level().getBlockEntity(buf.readBlockPos())));
    }

    public ArmScreenHandler(int syncId, @NotNull Inventory playerInventory, BlockEntity blockEntity) {
        super(MenuTypes.ARM_ENTITY_MENU.get(), syncId);
        this.playerInventory = playerInventory;
        this.blockPos = blockEntity.getBlockPos();
        this.blockEntity = (ArmEntity) blockEntity;

        playerInventorySlotIdx = 0;

        SlotGenerator.begin(this::addSlot, 8, 8)
                .playerInventory(playerInventory);

        handSlotIdx = 36;
        addSlot(new SlotItemHandler(this.blockEntity.getHandler(), 0, 64, 64));

        filterSlotIdx = 37;
        this.filterHandler = this.blockEntity.getItemContextFilter().getFilterHandler();
        for (int i = 0; i< filterHandler.getSlots(); i++) {
            addSlot(new SlotItemHandler(filterHandler, i, 64 + i * 18, 18));
        }

    }

    public @NotNull Inventory getPlayerInventory() {
        return playerInventory;
    }

    public @NotNull ArmEntity getBlockEntity() {
        return blockEntity;
    }

    public @NotNull ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
