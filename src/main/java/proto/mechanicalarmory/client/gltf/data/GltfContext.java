package proto.mechanicalarmory.client.gltf.data;

import org.simdjson.JsonValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GltfContext {
    public final List<JsonValue> nodes = new ArrayList<>();
    public final List<JsonValue> meshes = new ArrayList<>();
    public final List<JsonValue> accessors = new ArrayList<>();
    public final List<JsonValue> bufferViews = new ArrayList<>();

    public GltfContext(JsonValue root) {
        // Fill lookups using arrayIterator
        fillLookup(root.get("nodes"), nodes);
        fillLookup(root.get("meshes"), meshes);
        fillLookup(root.get("accessors"), accessors);
        fillLookup(root.get("bufferViews"), bufferViews);
    }

    private void fillLookup(JsonValue array, List<JsonValue> list) {
        if (array != null && !array.isNull()) {
            Iterator<JsonValue> it = array.arrayIterator();
            while (it.hasNext()) {
                list.add(it.next());
            }
        }
    }
}