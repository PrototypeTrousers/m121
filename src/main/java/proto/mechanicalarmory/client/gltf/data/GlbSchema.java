package proto.mechanicalarmory.client.gltf.data;

import java.util.List;

public record GlbSchema(
        Asset asset,
        List<Scene> scenes,
        Integer scene, // ID of the default scene
        List<Node> nodes,
        List<Mesh> meshes,
        List<Accessor> accessors,
        List<BufferView> bufferViews,
        List<Buffer> buffers
) {}

record Asset(
        String version,
        String generator
) {}

record Scene(
        String name,
        List<Integer> nodes // Indices of the root nodes in this scene
) {}

record Node(
        String name,
        List<Integer> children,
        Integer camera,
        Integer skin,
        Integer mesh,
        float[] matrix,      // 4x4, Default: Identity
        float[] translation, // [x, y, z], Default: [0, 0, 0]
        float[] rotation,    // [x, y, z, w], Default: [0, 0, 0, 1]
        float[] scale,       // [x, y, z], Default: [1, 1, 1]
        float[] weights      // Morph target weights
) {}

record Mesh(
        String name,
        List<MeshPrimitive> primitives
) {}

record MeshPrimitive(
        List<Attributes> attributes, // Maps "POSITION", "NORMAL" to accessor IDs
        Integer indices,
        Integer material,
        Integer mode
) {}

record Attributes(String attribute, Integer index) {}

record Accessor(
        Integer bufferView,
        Integer byteOffset,
        Integer componentType, // 5126 = FLOAT, 5123 = UNSIGNED_SHORT
        Integer count,
        String type,           // "SCALAR", "VEC3", "MAT4"
        float[] max,
        float[] min
) {}

record BufferView(
        Integer buffer,
        Integer byteOffset,
        Integer byteLength,
        Integer byteStride,
        Integer target
) {}

record Buffer(
        Integer byteLength,
        String uri // In GLB, this is usually null as data is in the binary chunk
) {}