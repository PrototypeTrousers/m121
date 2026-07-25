package proto.mechanicalarmory.client.flywheel.slicer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.analysis.BasicValue;

public class ModelPartValue extends BasicValue {
    
    private final String partName;

    public ModelPartValue(Type type, String partName) {
        super(type);
        this.partName = partName;
    }

    public String getPartName() {
        return partName;
    }

    @Override
    public boolean equals(Object value) {
        if (value == this) return true;
        if (value instanceof ModelPartValue other) {
            return super.equals(value) && this.partName.equals(other.partName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + partName.hashCode();
    }
}
