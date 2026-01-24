package proto.mechanicalarmory.client.renderer.util;

import net.minecraft.core.Vec3i;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SearchPattern {
    // A pre-calculated list of coordinates (x,y,z) sorted by distance from (0,0,0)
    public static final List<Vec3i> SORTED_OFFSETS;

    static {
        SORTED_OFFSETS = new ArrayList<>();
        int radius = 5; // Search radius (covers ~9 block reach)

        // Generate offsets
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    SORTED_OFFSETS.add(new Vec3i(x, y, z));
                }
            }
        }

        // Sort by physical distance (Closest blocks first)
        SORTED_OFFSETS.sort(Comparator.comparingDouble(vec -> 
            vec.getX()*vec.getX() + vec.getY()*vec.getY() + vec.getZ()*vec.getZ()
        ));
    }
}