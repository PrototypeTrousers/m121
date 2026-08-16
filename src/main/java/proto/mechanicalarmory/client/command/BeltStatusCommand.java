package proto.mechanicalarmory.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import proto.mechanicalarmory.client.belt.ClientBeltNetwork;
import proto.mechanicalarmory.common.belt.data.BeltLane;
import proto.mechanicalarmory.common.belt.data.BeltNode;
import proto.mechanicalarmory.common.belt.data.ItemGroup;
import proto.mechanicalarmory.common.entities.block.BeltEntity;

public final class BeltStatusCommand {

    private BeltStatusCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("beltstatus")
                .executes(BeltStatusCommand::inspectLookedAt)
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                    .executes(ctx -> inspectPos(BlockPosArgument.getLoadedBlockPos(ctx, "pos")))
                )
        );
    }

    private static int inspectLookedAt(CommandContext<CommandSourceStack> ctx) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            return inspectPos(blockHit.getBlockPos());
        }
        sendChat("§cYou are not looking at any block.");
        return 0;
    }

    private static int inspectPos(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            sendChat("§cClient level is null.");
            return 0;
        }

        sendChat("§6================ Belt Status at " + pos.toShortString() + " ================");
        ClientBeltNetwork net = ClientBeltNetwork.get();
        sendChat("§7[ClientNet global] subnets=" + net.totalSubnetCount()
                + ", totalNodes=" + net.totalNodeCount());

        // 1. Server BeltNetworkData (singleplayer)
        net.minecraft.server.MinecraftServer srv = mc.getSingleplayerServer();
        if (srv != null) {
            for (net.minecraft.server.level.ServerLevel srvLevel : srv.getAllLevels()) {
                if (srvLevel.dimension() == level.dimension()) {
                    proto.mechanicalarmory.common.belt.network.BeltNetworkData srvData =
                            proto.mechanicalarmory.common.belt.network.BeltNetworkData.get(srvLevel);
                    proto.mechanicalarmory.common.belt.data.BeltNode srvNode = srvData.nodeAt(pos);
                    if (srvNode == null) {
                        sendChat("§c[Server] No node in BeltNetworkData!");
                    } else {
                        sendChat("§a[Server] BeltNode: stopped=" + srvNode.isStopped()
                                + ", outputId=" + short8(srvNode.outputId())
                                + ", inputs=" + srvNode.inputIds().size()
                                + ", isWrap=" + srvNode.isWrapPoint());
                        dumpLane("  Server Lane 0 (Left)", srvNode.lane(0));
                        dumpLane("  Server Lane 1 (Right)", srvNode.lane(1));
                    }
                }
            }
        }

        // 2. Block state / geometry
        BlockState state = level.getBlockState(pos);
        Direction facing = state.getBlock() instanceof proto.mechanicalarmory.common.blocks.BlockBelt
                ? state.getValue(proto.mechanicalarmory.common.blocks.BlockBelt.FACING)
                : Direction.NORTH;
        BlockPos outPos = pos.relative(facing);
        BlockState outState = level.getBlockState(outPos);
        boolean outIsBelt = outState.getBlock() instanceof proto.mechanicalarmory.common.blocks.BlockBelt;

        sendChat("§d[BlockState] facing=" + facing + " -> outPos=" + outPos.toShortString()
                + " (isBelt=" + outIsBelt + ")");

        // Expected outputId purely from position (posToId)
        java.util.UUID expectedOutputId = BeltNode.posToId(outPos);
        sendChat("§d[Expected outputId from posToId(outPos)] " + short8(expectedOutputId));

        // 3. ClientBeltNetwork
        BeltNode clientNode = net.getNode(pos);
        java.util.UUID thisSubnetId = net.subnetworkIdForPos(pos);

        if (clientNode == null) {
            sendChat("§c[Client Net] No node! posToSubnet=" + short8(thisSubnetId));
        } else {
            // Resolve outputId inside subnet
            java.util.UUID outputId = clientNode.outputId();
            boolean outputIdMatchesExpected = expectedOutputId.equals(outputId);
            BeltNode resolvedOutNode = net.getNodeById(thisSubnetId, outputId);

            sendChat("§a[Client Net] BeltNode: id=" + short8(clientNode.nodeId())
                    + ", stopped=" + clientNode.isStopped()
                    + ", outputId=" + short8(outputId)
                    + (outputIdMatchesExpected ? " §2(matches posToId)" : " §c(MISMATCH with posToId!)")
                    + ", inputs=" + clientNode.inputIds().size()
                    + ", isWrap=" + clientNode.isWrapPoint());
            sendChat("§a[Client Net] subnet=" + short8(thisSubnetId)
                    + ", subnetNodeCount=" + net.subnetNodeCount(thisSubnetId)
                    + ", outputResolvesInSubnet=" + (resolvedOutNode != null));

            // Downstream node info
            java.util.UUID downSubnetId = net.subnetworkIdForPos(outPos);
            BeltNode downNode = net.getNode(outPos);
            if (downNode != null) {
                sendChat("§a[Client Net] downstream(" + outPos.toShortString() + "): id="
                        + short8(downNode.nodeId())
                        + ", sameSubnet=" + java.util.Objects.equals(thisSubnetId, downSubnetId)
                        + ", subnet=" + short8(downSubnetId));
            } else {
                sendChat("§c[Client Net] downstream(" + outPos.toShortString() + "): NOT REGISTERED"
                        + ", subnet=" + short8(downSubnetId));
            }

            dumpLane("  ClientNet Lane 0 (Left)", clientNode.lane(0));
            dumpLane("  ClientNet Lane 1 (Right)", clientNode.lane(1));
        }

        return 1;
    }

    private static String short8(java.util.UUID id) {
        return id != null ? id.toString().substring(0, 8) : "null";
    }

    private static void dumpLane(String label, BeltLane lane) {
        if (lane == null) {
            sendChat("§8" + label + ": null");
            return;
        }
        int count = lane.groupCount();
        sendChat("§e" + label + ": §7speed=" + lane.speed()
                + ", spacing=" + lane.itemSpacing()
                + ", groups=" + count
                + (lane.isEmpty() ? " §8(empty)" : ""));

        ItemGroup[] groups = lane.groupArray();
        for (int i = 0; i < count; i++) {
            ItemGroup g = groups[i];
            if (g != null) {
                float head = g.headPos();
                float tail = g.tailPos(lane.itemSpacing());
                String itemName = g.item().getItem().toString();
                sendChat("    §f[" + i + "] " + itemName + " x" + g.count()
                        + " §7head=§a" + String.format("%.3f", head)
                        + " §7tail=§e" + String.format("%.3f", tail));
            }
        }
    }

    private static void sendChat(String text) {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
}
