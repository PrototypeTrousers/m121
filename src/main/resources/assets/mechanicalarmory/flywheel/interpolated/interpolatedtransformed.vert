#include "flywheel:internal/indirect/buffer_bindings.glsl"

layout(std430, binding = _FLW_DRAW_INSTANCE_INDEX_BUFFER_BINDING) restrict readonly buffer TargetBuffer2 {
    uint instanceIndices[];
};

layout(std430, binding = 12) readonly buffer OutputMatrices {
    mat4 finalPartMatrices[];
};

#if __VERSION__ < 460
#define flw_baseInstance gl_BaseInstanceARB
#define flw_drawId gl_DrawIDARB
#else
#define flw_baseInstance gl_BaseInstance
#define flw_drawId gl_DrawID
#endif

void flw_instanceVertex(in FlwInstance i) {
    uint instanceIndex = instanceIndices[flw_baseInstance + gl_InstanceID];

    // instanceIndex is the logical buffer position, which matches the slot
    // the compute shader wrote the pre-chained matrix into.
    mat4 partWorldMatrix = finalPartMatrices[instanceIndex];

    flw_vertexPos    = partWorldMatrix * flw_vertexPos;
    flw_vertexNormal = normalize(mat3(partWorldMatrix) * flw_vertexNormal);
    flw_vertexColor   *= i.color;
    flw_vertexOverlay  = i.overlay;
    flw_vertexLight    = max(vec2(i.light) / 256.0, flw_vertexLight);
}
