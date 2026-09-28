#version 150

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;
uniform vec4 ClipShape;
uniform vec2 ClipCorner;

in vec2 texCoord0;

out vec4 fragColor;

float squircleDistance(vec2 point, vec2 halfSize, float radius, float power) {
    vec2 outside = abs(point) - halfSize + radius;
    vec2 corner = max(outside, 0.0);
    float reach = power <= 2.01
            ? length(corner)
            : pow(pow(corner.x, power) + pow(corner.y, power), 1.0 / power);
    return reach + min(max(outside.x, outside.y), 0.0) - radius;
}

// WHY: маска по точному расстоянию до той же формы, что у панели: буфер глубины режет только
// WHY: целыми пикселями, и угол карты шёл ступенькой, а здесь кромка сглажена на один пиксель
void main() {
    float edge = squircleDistance(gl_FragCoord.xy - ClipShape.xy, ClipShape.zw, ClipCorner.x, ClipCorner.y);
    float cover = clamp(0.5 - edge / max(1.0, fwidth(edge)), 0.0, 1.0);
    if (cover <= 0.0) discard;
    fragColor = vec4(texture(Sampler0, gl_FragCoord.xy / ScreenSize).rgb, cover);
}
