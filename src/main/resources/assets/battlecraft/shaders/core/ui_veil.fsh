#version 150

uniform sampler2D Sampler0;

uniform vec2 ScreenSize;
uniform vec4 VeilShape;
uniform vec2 VeilCorner;
uniform vec2 VeilBlur;

in vec2 texCoord0;

out vec4 fragColor;

const int TAPS = 32;
const float GOLDEN_ANGLE = 2.39996323;
const float FALLOFF = 2.2;
const float SPACING = 0.34;

float squircleDistance(vec2 point, vec2 halfSize, float radius, float power) {
    vec2 outside = abs(point) - halfSize + radius;
    vec2 corner = max(outside, 0.0);
    float reach = power <= 2.01
            ? length(corner)
            : pow(pow(corner.x, power) + pow(corner.y, power), 1.0 / power);
    return reach + min(max(outside.x, outside.y), 0.0) - radius;
}

// WHY: диск Фогеля с гауссовым весом по квадрату радиуса. Выборка идёт с того уровня мипмапов,
// WHY: где шаг между соседними точками диска не больше текселя: иначе на большом радиусе
// WHY: 32 точки дали бы зерно, а усреднённый уровень делает размытие гладким на любом радиусе
vec4 veiled(vec2 pixel, float radius) {
    if (radius < 0.35) return texture(Sampler0, pixel / ScreenSize);

    float level = max(0.0, log2(radius * SPACING));
    vec4 sum = vec4(0.0);
    float weight = 0.0;
    for (int tap = 0; tap < TAPS; tap++) {
        float share = (float(tap) + 0.5) / float(TAPS);
        float angle = float(tap) * GOLDEN_ANGLE;
        vec2 offset = vec2(cos(angle), sin(angle)) * sqrt(share) * radius;
        float tapWeight = exp(-FALLOFF * share);
        sum += textureLod(Sampler0, (pixel + offset) / ScreenSize, level) * tapWeight;
        weight += tapWeight;
    }
    return sum / weight;
}

void main() {
    float edge = squircleDistance(gl_FragCoord.xy - VeilShape.xy, VeilShape.zw, VeilCorner.x, VeilCorner.y);
    float cover = clamp(0.5 - edge / max(1.0, fwidth(edge)), 0.0, 1.0);
    if (cover <= 0.0) discard;
    fragColor = veiled(gl_FragCoord.xy, VeilBlur.x) * (cover * VeilBlur.y);
}
