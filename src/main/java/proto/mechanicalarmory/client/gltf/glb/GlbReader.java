package proto.mechanicalarmory.client.gltf.glb;

import com.google.common.base.Stopwatch;
import org.simdjson.JsonValue;
import org.simdjson.SimdJsonParser;
import proto.mechanicalarmory.client.gltf.data.GltfContext;
import proto.mechanicalarmory.client.gltf.data.GltfMesh;
import proto.mechanicalarmory.client.gltf.data.GltfNode;
import proto.mechanicalarmory.client.gltf.data.GltfScene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class GlbReader {

    List<GltfScene> scenes = new ArrayList<>();
    List<GltfNode> nodes = new ArrayList<>();
    List<GltfMesh > meshes = new ArrayList<>();










    byte[] read(byte[] glbData) {

// 2. Validate Magic Number (Bytes 0-3 should be 'glTF')
// 3. Get JSON Chunk Length (Bytes 12-15)
        int jsonChunkLength = ByteBuffer.wrap(glbData, 12, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

// 4. Extract the JSON bytes (Starting at offset 20)
        return Arrays.copyOfRange(glbData, 20, 20 + jsonChunkLength);
    }

    public void parse(byte[] jsonBytes, ByteBuffer binBuffer) {
        SimdJsonParser parser = new SimdJsonParser();
        JsonValue root = parser.parse(jsonBytes, jsonBytes.length);

        JsonValue scenes = root.get("scenes");
        if (scenes == null || scenes.isNull()) return;

        GltfContext ctx = new GltfContext(root);

        // Start with the first scene's nodes
        JsonValue firstScene = scenes.arrayIterator().next(); // Using the same iterator logic internally
        JsonValue sceneNodes = firstScene.get("nodes");

        if (sceneNodes != null) {
            Iterator<JsonValue> it = sceneNodes.arrayIterator();
            while (it.hasNext()) {
                long nodeIdx = it.next().asLong();
                processNode(ctx, nodeIdx, binBuffer);
            }
        }
    }

    private void processNode(GltfContext ctx, long nodeIdx, ByteBuffer binBuffer) {
        // O(1) Access! No more manual loops to find the node index.
        JsonValue node = ctx.nodes.get((int) nodeIdx);

        JsonValue meshIdxVal = node.get("mesh");
        if (meshIdxVal != null && !meshIdxVal.isNull()) {
            processMesh(ctx, meshIdxVal.asLong(), binBuffer);
        }

        // Handle children recursion
        JsonValue children = node.get("children");
        if (children != null && !children.isNull()) {
            Iterator<JsonValue> childIt = children.arrayIterator();
            while (childIt.hasNext()) {
                processNode(ctx, childIt.next().asLong(), binBuffer);
            }
        }
    }

    private void processMesh(GltfContext ctx, long meshIdx, ByteBuffer binBuffer) {
        JsonValue mesh = ctx.meshes.get((int) meshIdx);
        Iterator<JsonValue> primIt = mesh.get("primitives").arrayIterator();

        while (primIt.hasNext()) {
            JsonValue primitive = primIt.next();
            long accessorIdx = primitive.get("attributes").get("POSITION").asLong();

            // Accessor lookup
            JsonValue accessor = ctx.accessors.get((int) accessorIdx);
            long bvIdx = accessor.get("bufferView").asLong();

            // BufferView lookup
            JsonValue bv = ctx.bufferViews.get((int) bvIdx);
            long offset = bv.get("byteOffset").asLong();
            long length = bv.get("byteLength").asLong();

            // Extract slice from binary buffer
            ByteBuffer data = binBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            data.position((int) offset);
            data.limit((int) (offset + length));

            // You now have the raw floats for this primitive!
        }
    }


    public static void main(String... args) {
        Stopwatch w = Stopwatch.createStarted();

        GlbReader reader = new GlbReader();
        // 1. Read the file into a ByteBuffer or byte array
        byte[] glbData;
        try {
            glbData = Files.readAllBytes(Paths.get("/mnt/dados/git/m121/src/main/resources/assets/mechanicalarmory/models/fullarm.glb"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ByteBuffer buffer = getBinaryBuffer(glbData);

        reader.parse(reader.read(glbData), buffer);
        w.stop();
        System.out.println(w.elapsed().toMillis());

    }

    public static ByteBuffer getBinaryBuffer(byte[] fullGlbData) {
        ByteBuffer wrapper = ByteBuffer.wrap(fullGlbData).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Skip Header (12 bytes)
        // 2. Read JSON Chunk Header
        int jsonLength = wrapper.getInt(12);

        // 3. The BIN chunk starts immediately after the JSON chunk
        // Header (12) + JSON Header (8) + JSON Content (jsonLength)
        int binHeaderOffset = 12 + 8 + jsonLength;

        // 4. Read BIN Chunk Header
        int binLength = wrapper.getInt(binHeaderOffset);
        int binType = wrapper.getInt(binHeaderOffset + 4); // Should be 0x004E4942 ('BIN')

        return wrapper.slice(binHeaderOffset + 8, binLength).order(ByteOrder.LITTLE_ENDIAN);
    }
}