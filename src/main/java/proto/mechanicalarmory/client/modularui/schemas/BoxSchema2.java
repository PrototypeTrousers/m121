package proto.mechanicalarmory.client.modularui.schemas;

import brachy.modularui.drawable.schema.PosListSchema;
import brachy.modularui.utils.BlockPosUtil;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import proto.mechanicalarmory.client.modularui.Utils;
import proto.mechanicalarmory.client.modularui.renderer.MASchemaRenderer;

public class BoxSchema2 extends PosListSchema {
        public static BoxSchema2 of(Level level, BlockPos center, int r) {
        return new BoxSchema2(level, center.offset(-r, -r, -r), center.offset(r, r, r));
    }

    @Getter
    private final Level level;
    @Getter
    private final BlockPos min, max;
    private final Vector3f center;

    public BoxSchema2(Level level, BlockPos min, BlockPos max) {
        super(level, Utils.getAllInside(min, max, false));
        this.level = level;
        this.min = BlockPosUtil.getMin(min, max);
        this.max = BlockPosUtil.getMax(min, max);
        this.center = BlockPosUtil.getCenterF(min, max);
    }

    @Override
    public Vector3fc getFocus() {
        return center;
    }

    @Override
    public BlockPos getOrigin() {
        return min;
    }

    /**
     * Creates a Mechanical Armory renderer for this schema.
     * Use this instead of {@link #createRenderer()} anywhere in MA code so that
     * the returned renderer is typed as {@link MASchemaRenderer} and gives access
     * to the full MA fluent API without any casting.
     */
    public MASchemaRenderer createMARenderer() {
        return new MASchemaRenderer(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BoxSchema2 entries)) return false;

        return level.equals(entries.level) && min.equals(entries.min) && max.equals(entries.max) && center.equals(entries.center);
    }

    @Override
    public int hashCode() {
        int result = level.hashCode();
        result = 31 * result + min.hashCode();
        result = 31 * result + max.hashCode();
        result = 31 * result + center.hashCode();
        return result;
    }
    }