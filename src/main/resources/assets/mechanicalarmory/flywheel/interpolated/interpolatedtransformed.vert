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
    mat4 partWorldMatrix = finalPartMatrices[0];

    // 3. Transform geometry positions
    flw_vertexPos = partWorldMatrix * flw_vertexPos;

    // 4. Transform normals securely
    flw_vertexNormal = normalize(mat3(partWorldMatrix) * flw_vertexNormal);

    // 5. Apply vertex characteristics
    flw_vertexColor *= i.color;
    flw_vertexOverlay = i.overlay;
    flw_vertexLight = max(vec2(i.light) / 256.0, flw_vertexLight);
}