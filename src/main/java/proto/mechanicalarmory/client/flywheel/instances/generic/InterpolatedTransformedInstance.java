package proto.mechanicalarmory.client.flywheel.instances.generic;

import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import org.joml.Matrix4f;

import java.util.Objects;

public record InterpolatedTransformedInstance(TransformedInstance instance, Matrix4f current, Matrix4f previous) {

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (InterpolatedTransformedInstance) obj;
        return Objects.equals(this.instance, that.instance) &&
                Objects.equals(this.current, that.current) &&
                Objects.equals(this.previous, that.previous);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instance, current, previous);
    }

    @Override
    public String toString() {
        return "InterpolatedTransformedInstance[" +
                "instance=" + instance + ", " +
                "current=" + current + ", " +
                "previous=" + previous + ']';
    }
}