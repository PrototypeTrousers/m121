package proto.mechanicalarmory.client.flywheel.slicer;

import dev.engine_room.flywheel.lib.model.part.InstanceTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Matrix4f;

import java.util.Map;

public class PoseHelper {

    public static Matrix4f createInitialPose(BlockPos visualPos, BlockState blockState) {
        Matrix4f matrix = new Matrix4f().translate(visualPos.getX(), visualPos.getY(), visualPos.getZ());

        if (blockState != null) {
            for (Property<?> prop : blockState.getProperties()) {
                if (prop.getName().equals("facing") || prop.getValueClass() == Direction.class) {
                    Object val = blockState.getValue(prop);
                    if (val instanceof Direction dir && dir.getAxis().isHorizontal()) {
                        float horizontalAngle = dir.toYRot();
                        matrix.translate(0.5F, 0.5F, 0.5F)
                              .rotateY(-horizontalAngle * Mth.DEG_TO_RAD)
                              .translate(-0.5F, -0.5F, -0.5F);
                        break;
                    }
                }
            }
        }
        return matrix;
    }

    public static void updateVisibility(BlockState blockState, Map<String, InstanceTree> rootTrees) {
        if (rootTrees == null || rootTrees.size() <= 1 || blockState == null) {
            return;
        }

        for (Property<?> prop : blockState.getProperties()) {
            String propName = prop.getName();
            String valName = blockState.getValue(prop).toString().toLowerCase();

            if (propName.equals("type")) { // Chests (single, left, right)
                for (Map.Entry<String, InstanceTree> entry : rootTrees.entrySet()) {
                    String layerName = entry.getKey().toLowerCase();
                    boolean match = false;
                    if (valName.equals("single") && !layerName.contains("double") && !layerName.contains("left") && !layerName.contains("right")) {
                        match = true;
                    } else if (valName.equals("left") && layerName.contains("left")) {
                        match = true;
                    } else if (valName.equals("right") && layerName.contains("right")) {
                        match = true;
                    }
                    if (entry.getValue() != null) {
                        entry.getValue().visible(match);
                    }
                }
                return;
            } else if (propName.equals("part")) { // Beds (head, foot)
                for (Map.Entry<String, InstanceTree> entry : rootTrees.entrySet()) {
                    String layerName = entry.getKey().toLowerCase();
                    boolean match = layerName.contains(valName);
                    if (entry.getValue() != null) {
                        entry.getValue().visible(match);
                    }
                }
                return;
            }
        }
    }
}
