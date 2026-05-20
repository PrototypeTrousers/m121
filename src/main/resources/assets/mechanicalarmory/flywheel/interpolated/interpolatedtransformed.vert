#include "flywheel:internal/indirect/buffer_bindings.glsl"

layout(std430, binding = 12) readonly buffer OutputMatrices {
    mat4 finalPartMatrices[];
};

void flw_instanceVertex(in FlwInstance i) {
    uint absoluteInstanceIndex;

    // Isolate the backends completely using Flywheel's core pipeline definitions
    #if defined(FLW_BACKEND_INDIRECT)
        // INDIRECT PATHWAY: Safe to look up redirection indexes
        absoluteInstanceIndex = _flw_instanceIndices[gl_InstanceID + gl_BaseInstance];
    #else
        // INSTANCING PATHWAY: Direct 1:1 mapping (the indirect array code is never scanned)
        absoluteInstanceIndex = gl_InstanceID;
    #endif

    // 2. Fetch the pre-computed matrix using that precise index marker
    mat4 partWorldMatrix = finalPartMatrices[absoluteInstanceIndex];

    // 3. Transform geometry positions
    flw_vertexPos = partWorldMatrix * flw_vertexPos;

    // 4. Transform normals securely
    flw_vertexNormal = normalize(mat3(partWorldMatrix) * flw_vertexNormal);

    // 5. Apply vertex characteristics
    flw_vertexColor *= i.color;
    flw_vertexOverlay = i.overlay;
    flw_vertexLight = max(vec2(i.light) / 256.0, flw_vertexLight);
}