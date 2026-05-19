// Inside interpolatedtransformed.vert
layout(std430, binding = 1) readonly buffer OutputMatrices {
    mat4 finalPartMatrices[];
};

void flw_instanceVertex(in FlwInstance i) {
    // gl_InstanceID extracts the continuous sequence index of this specific component drawing pass
    mat4 partWorldMatrix = finalPartMatrices[gl_InstanceID];

    flw_vertexPos = partWorldMatrix * flw_vertexPos;
}