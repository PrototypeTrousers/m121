package proto.mechanicalarmory.client.modularui;

import net.minecraft.core.BlockPos;

public class Utils {
    public static Iterable<BlockPos> getAllInside(BlockPos p1, BlockPos p2, boolean includeBorder) {
        int x0 = Math.min(p1.getX(), p2.getX());
        int y0 = Math.min(p1.getY(), p2.getY());
        int z0 = Math.min(p1.getZ(), p2.getZ());
        int x1 = Math.max(p1.getX(), p2.getX());
        int y1 = Math.max(p1.getY(), p2.getY());
        int z1 = Math.max(p1.getZ(), p2.getZ());

        if (includeBorder) {
            x0--;
            y0--;
            z0--;

            x1++;
            y1++;
            z1++;
        }
        return BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1);
    }
}
