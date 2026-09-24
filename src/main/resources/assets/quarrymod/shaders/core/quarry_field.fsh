#version 150

in vec2 shapeCoord;
in vec3 surfaceNormal;
in vec3 localPos;
in vec3 cameraRelativePos;

uniform vec4 QuarryColor;
uniform float Mode;
uniform float Phase;
uniform float Pulse;
uniform float Fade;
uniform float Time;

out vec4 fragColor;

const float TAU = 6.2831853;
const float LOOP_SECONDS = 240.0;
const float WHITE_LIFT = 0.22;
const float WALL_EDGE = 0.34;
const float WALL_FILL = 0.08;
const float WALL_SURFACE = 0.22;
const float WALL_SURFACE_SHARPNESS = 38.0;
const float CORE_BREATH = 0.05;
const float CORE_BREATH_TURNS = 30.0;
const float RING_TRACK = 0.10;
const float RING_FILL = 0.75;
const float RING_SOFT = 0.22;
const float RING_FILL_EDGE = 0.008;
const float RING_HEAD = 0.035;
const float LACE_EDGE = 0.07;
const float LACE_BREATH = 0.08;
const float LACE_BREATH_TURNS = 20.0;
const float BLOOM_RIM = 2.2;
const float GRAIN = 0.004;

// WHY: время приходит свёрнутым в петлю LOOP_SECONDS, и любая волна обязана делать за петлю
// WHY: целое число оборотов: дробное даёт рывок в момент сворачивания
float breathing(float turns, float depth) {
    return 1.0 - depth + depth * sin(Time / LOOP_SECONDS * turns * TAU);
}

float grain(vec2 coord) {
    return fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
}

float facing() {
    vec3 view = normalize(-cameraRelativePos);
    return abs(dot(normalize(surfaceNormal), view));
}

float edgeDistance() {
    vec2 edge = abs(shapeCoord - 0.5) * 2.0;
    return max(edge.x, edge.y);
}

float walls() {
    float edge = smoothstep(0.55, 1.0, edgeDistance());
    float level = localPos.y + 0.5;
    float filled = (1.0 - smoothstep(Phase - 0.03, Phase + 0.03, level)) * WALL_FILL;
    float rising = step(0.001, Phase) * step(Phase, 0.999);
    float surface = exp(-abs(level - Phase) * WALL_SURFACE_SHARPNESS) * WALL_SURFACE * rising;
    float rim = pow(1.0 - facing(), 2.0) * 0.2;
    return edge * edge * WALL_EDGE + filled + surface + rim + Pulse * 0.35;
}

float core() {
    float rim = pow(1.0 - facing(), 2.5);
    float body = 0.18 + rim * 0.7;
    return body * (0.6 + 0.4 * Phase) * breathing(CORE_BREATH_TURNS, CORE_BREATH);
}

float ring() {
    float along = shapeCoord.y;
    float across = abs(shapeCoord.x - 0.5) * 2.0;
    float band = 1.0 - smoothstep(1.0 - RING_SOFT * 2.0, 1.0, across);
    float filled = 1.0 - smoothstep(Phase - RING_FILL_EDGE, Phase + RING_FILL_EDGE, along);
    float gradient = 0.55 + 0.45 * along;
    float head = (1.0 - smoothstep(0.0, RING_HEAD, abs(along - Phase))) * step(0.001, Phase) * 0.3;
    return band * (RING_TRACK + filled * RING_FILL * gradient + head);
}

float lace() {
    float rib = smoothstep(1.0 - LACE_EDGE, 1.0, edgeDistance());
    float rim = pow(1.0 - facing(), 2.0) * 0.1;
    return (rib * 0.35 + rim) * breathing(LACE_BREATH_TURNS, LACE_BREATH) + Pulse * 0.25 * rib;
}

float bloom() {
    float rim = pow(1.0 - facing(), BLOOM_RIM);
    return rim * Pulse * Pulse * 0.6;
}

float shaped() {
    if (Mode < 0.5) return walls();
    if (Mode < 1.5) return core();
    if (Mode < 2.5) return ring();
    if (Mode < 3.5) return lace();
    return bloom();
}

void main() {
    float amount = shaped() * Fade * QuarryColor.a;
    if (amount < 0.004) discard;

    vec3 tint = mix(QuarryColor.rgb, vec3(1.0), WHITE_LIFT) * (0.9 + 0.2 * Pulse);
    float speck = (grain(shapeCoord + Time) - 0.5) * GRAIN;
    fragColor = vec4(tint + speck, clamp(amount, 0.0, 1.0));
}
