#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 CacheOrigin;
uniform vec3 CacheScale;
uniform float Mode;
uniform float Spin;

out vec2 shapeCoord;
out vec3 surfaceNormal;
out vec3 cameraRelativePos;

vec3 faceNormal(vec3 point) {
    vec3 weight = abs(point);
    if (weight.x >= weight.y && weight.x >= weight.z) return vec3(sign(point.x), 0.0, 0.0);
    if (weight.y >= weight.z) return vec3(0.0, sign(point.y), 0.0);
    return vec3(0.0, 0.0, sign(point.z));
}

vec3 spinAround(vec3 point, float angle) {
    float cosine = cos(angle);
    float sine = sin(angle);
    return vec3(point.x * cosine - point.z * sine, point.y, point.x * sine + point.z * cosine);
}

vec3 restingNormal() {
    if (Mode < 0.5) return faceNormal(Position);
    if (Mode < 2.5) return vec3(0.0, 1.0, 0.0);
    return length(Position) > 0.0001 ? normalize(Position) : vec3(0.0, 1.0, 0.0);
}

void main() {
    vec3 shaped = spinAround(Position, Spin);

    surfaceNormal = spinAround(restingNormal(), Spin);
    shapeCoord = UV0;
    cameraRelativePos = CacheOrigin + shaped * CacheScale;

    gl_Position = ProjMat * ModelViewMat * vec4(cameraRelativePos, 1.0);
}
