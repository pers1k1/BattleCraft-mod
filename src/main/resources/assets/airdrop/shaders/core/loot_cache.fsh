#version 150

in vec2 shapeCoord;
in vec3 surfaceNormal;
in vec3 cameraRelativePos;

uniform vec4 CacheColor;
uniform float Mode;
uniform float Phase;
uniform float Pulse;
uniform float Fade;
uniform float Drift;
uniform float Time;

out vec4 fragColor;

const float TAU = 6.2831853;
const float LOOP_SECONDS = 240.0;
const float WHITE_LIFT = 0.18;
const float RIB_WIDTH = 0.06;
const float RIB_GLOW = 0.30;
const float RIB_SOFT = 0.22;
const float FACE_FILL = 0.025;
const float CORNER_GLINT = 0.18;
const float COMET_TURNS = 48.0;
const float COMET_TAIL = 9.0;
const float COMET_GLOW = 0.55;
const float FRAME_BREATH_TURNS = 30.0;
const float FRAME_BREATH = 0.12;
const float EMPTY_LEVEL = 0.28;
const float HALO_FALLOFF = 3.2;
const float HALO_GLOW = 0.30;
const float HALO_RIPPLES = 3.0;
const float HALO_RIPPLE_TURNS = 60.0;
const float HALO_RIPPLE = 0.18;
const float HALO_BREATH_TURNS = 24.0;
const float RING_TRACK = 0.10;
const float RING_FILL = 0.70;
const float RING_SOFT = 0.22;
const float RING_FILL_EDGE = 0.008;
const float RING_HEAD = 0.035;
const float BLOOM_RIM = 2.2;
const float GRAIN = 0.004;

// WHY: время приходит свёрнутым в петлю LOOP_SECONDS, и любая волна обязана делать за петлю
// WHY: целое число оборотов: дробное даёт рывок в момент сворачивания
float turns(float count) {
    return Time / LOOP_SECONDS * count + Drift;
}

float breathing(float count, float depth) {
    return 1.0 - depth + depth * sin(turns(count) * TAU);
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

// WHY: точка на кромке грани переводится в долю её периметра, чтобы блик бежал по кругу
// WHY: вдоль рёбер: каждая сторона квадрата это четверть пути
float perimeter() {
    vec2 uv = shapeCoord;
    vec2 toEdge = vec2(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    if (toEdge.y <= toEdge.x) return uv.y < 0.5 ? uv.x * 0.25 : 0.5 + (1.0 - uv.x) * 0.25;
    return uv.x > 0.5 ? 0.25 + uv.y * 0.25 : 0.75 + (1.0 - uv.y) * 0.25;
}

float comet() {
    float head = fract(turns(COMET_TURNS));
    float behind = fract(head - perimeter());
    return exp(-behind * COMET_TAIL) * COMET_GLOW;
}

float frame() {
    float distance = edgeDistance();
    float rib = smoothstep(1.0 - RIB_WIDTH, 1.0, distance);
    float halo = smoothstep(1.0 - RIB_SOFT, 1.0, distance) * 0.35;
    vec2 corner = abs(shapeCoord - 0.5) * 2.0;
    float glint = smoothstep(0.82, 1.0, min(corner.x, corner.y)) * CORNER_GLINT;
    float rim = pow(1.0 - facing(), 2.0) * 0.08;
    float lit = (rib + halo) * RIB_GLOW + glint + FACE_FILL + rim;
    float alive = mix(EMPTY_LEVEL, 1.0, Phase);
    float runner = comet() * rib * Phase;
    return (lit * breathing(FRAME_BREATH_TURNS, FRAME_BREATH) + runner) * alive + Pulse * 0.4 * rib;
}

float halo() {
    vec2 centered = shapeCoord * 2.0 - 1.0;
    float radius = length(centered);
    float body = exp(-radius * radius * HALO_FALLOFF) * (1.0 - smoothstep(0.8, 1.0, radius));
    float ripple = 0.5 + 0.5 * sin((radius * HALO_RIPPLES - turns(HALO_RIPPLE_TURNS)) * TAU);
    float breath = breathing(HALO_BREATH_TURNS, 0.2);
    return body * (HALO_GLOW + ripple * HALO_RIPPLE * body) * breath * Phase + Pulse * body * 0.5;
}

float ring() {
    float along = shapeCoord.y;
    float across = abs(shapeCoord.x - 0.5) * 2.0;
    float band = 1.0 - smoothstep(1.0 - RING_SOFT * 2.0, 1.0, across);
    float filled = 1.0 - smoothstep(Phase - RING_FILL_EDGE, Phase + RING_FILL_EDGE, along);
    float head = (1.0 - smoothstep(0.0, RING_HEAD, abs(along - Phase))) * step(0.001, Phase) * 0.35;
    return band * (RING_TRACK + filled * RING_FILL * (0.55 + 0.45 * along) + head);
}

float bloom() {
    float rim = pow(1.0 - facing(), BLOOM_RIM);
    return rim * Pulse * Pulse * 0.6;
}

float shaped() {
    if (Mode < 0.5) return frame();
    if (Mode < 1.5) return halo();
    if (Mode < 2.5) return ring();
    return bloom();
}

void main() {
    float amount = shaped() * Fade * CacheColor.a;
    if (amount < 0.004) discard;

    vec3 lifted = mix(CacheColor.rgb, vec3(1.0), WHITE_LIFT);
    float grey = dot(lifted, vec3(0.299, 0.587, 0.114));
    vec3 tint = mix(vec3(grey), lifted, 0.35 + 0.65 * Phase) * (0.92 + 0.2 * Pulse);
    float speck = (grain(shapeCoord + Time) - 0.5) * GRAIN;
    fragColor = vec4(tint + speck, clamp(amount, 0.0, 1.0));
}
