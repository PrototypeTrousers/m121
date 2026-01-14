package proto.mechanicalarmory.client.gltf.glb;

import com.google.common.base.Stopwatch;
import org.simdjson.SimdJsonParser;
import proto.mechanicalarmory.client.gltf.data.GlbSchema;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class GlbReader {

    byte[] read(byte[] glbData) {
        int jsonChunkLength = ByteBuffer.wrap(glbData, 12, 4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        return Arrays.copyOfRange(glbData, 20, 20 + jsonChunkLength);
    }


    public static void main(String... args) {

        GlbReader reader = new GlbReader();
        // 1. Read the file into a ByteBuffer or byte array
        byte[] glbData;
        try {
            glbData = Files.readAllBytes(Paths.get("/mnt/dados/git/m121/src/main/resources/assets/mechanicalarmory/models/fullarm.glb"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ByteBuffer buffer = getBinaryBuffer(glbData);

        SimdJsonParser parser = new SimdJsonParser();

        byte[] jsonBytes = reader.read(glbData);
        Stopwatch w = Stopwatch.createStarted();

        parser.parse(jsonBytes, jsonBytes.length, GlbSchema.class);

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