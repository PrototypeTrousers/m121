package proto.mechanicalarmory.client.renderer.util;

import com.google.common.collect.ImmutableList;
import net.minecraft.core.Vec3i;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SearchPattern {
    // A pre-calculated list of coordinates (x,y,z) sorted by distance from (0,0,0)
    public static final List<Vec3i> SORTED_OFFSETS = createOnionPattern(5);
    public static final List<Vec3i> OUTER_SHELL_7 = createSolidShell(7);


    static List<Vec3i> createOnionPattern(int radius) {
        List<Vec3i> onions = new ArrayList<>();
        int mxDistSq = radius * radius;
        // Generate offsets
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x*x + y*y + z*z;
                    // 3. Skip if too far (Sphere Check)
                    if (distSq > mxDistSq) {
                        continue;
                    }
                    onions.add(new Vec3i(x, y, z));
                }
            }
        }

        // Sort by physical distance (Closest blocks first)
        onions.sort(Comparator.comparingDouble(vec ->
                vec.getX()*vec.getX() + vec.getY()*vec.getY() + vec.getZ()*vec.getZ()
        ));
        return ImmutableList.copyOf(onions);
    }

    static List<Vec3i> createSolidShell(int radius) {
        List<Vec3i> shell = new ArrayList<>();

        // Outer boundary (Radius)
        int maxDistSq = radius * radius;
        // Inner boundary (Radius - 1)
        // We want blocks LESS than Radius, but GREATER than (Radius - 1)
        int minDistSq = (radius - 1) * (radius - 1);

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x*x + y*y + z*z;

                    // LOGIC: Must be inside the outer sphere...
                    // ...but OUTSIDE the inner sphere.
                    if (distSq <= maxDistSq && distSq > minDistSq) {
                        shell.add(new Vec3i(x, y, z));
                    }
                }
            }
        }
        // No sort needed - usually you just iterate the shell
        return shell;
    }
}