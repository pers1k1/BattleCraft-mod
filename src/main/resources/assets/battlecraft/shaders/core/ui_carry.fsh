#version 150

uniform sampler2D Sampler0;
uniform mat3 CarryWarp;

in vec2 texCoord0;

out vec4 fragColor;

// WHY: сцену горения композит переносит на мировую плоскость целиком, поэтому копия кадра в ней
// WHY: обязана лежать уже перенесённой, иначе стекло поверх пустого места читает мир по экрану
void main() {
    vec3 carried = CarryWarp * vec3(texCoord0, 1.0);
    vec2 spot = carried.z <= 1.0e-5 ? texCoord0 : carried.xy / carried.z;
    fragColor = vec4(texture(Sampler0, spot).rgb, 1.0);
}
