package proto.mechanicalarmory.common.entities.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.network.BeltNetworkData;
import proto.mechanicalarmory.common.blocks.BlockBelt;
import proto.mechanicalarmory.common.entities.MAEntities;

import java.util.UUID;

/**
 * A thin rendering anchor for a conveyor belt block.
 *
 * <p>This block entity holds <em>no authoritative item data</em>.  All item
 * state lives in {@link BeltNetworkData}.
 *
 * <p>On the server side it stores only the {@code nodeId} used to look up the
 * corresponding {@link BeltNode}.
 */
public class BeltEntity extends BlockEntity {
    public BeltEntity(BlockPos pos, BlockState state) {
        super(MAEntities.BELT_ENTITY.get(), pos, state);
    }

    /** Always matches the node UUID used by both server and client. */
    public UUID nodeId() {
        return BeltNode.posToId(worldPosition);
    }
}
