#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ZoneOrigin;
uniform vec3 ZoneScale;

out vec2 shapeCoord;
out vec3 surfaceNormal;
out vec3 cameraRelativePos;

vec3 outwardNormal(vec3 unitPosition) {
    if (abs(unitPosition.x) > abs(unitPosition.z)) {
        return vec3(sign(unitPosition.x), 0.0, 0.0);
    }
    return vec3(0.0, 0.0, sign(unitPosition.z));
}

void main() {
    vec3 relative = ZoneOrigin + Position * ZoneScale;
    vec3 radial = vec3(Position.x, 0.0, Position.z);

    cameraRelativePos = relative;
    surfaceNormal = length(radial) > 0.0001 ? normalize(radial) : outwardNormal(Position);
    shapeCoord = UV0;

    gl_Position = ProjMat * ModelViewMat * vec4(relative, 1.0);
}
