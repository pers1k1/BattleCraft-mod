#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform vec4 Glow;
uniform vec4 Accent;

in vec2 texCoord0;

out vec4 fragColor;

const float TAU = 6.2831853;
// WHY: оттенки свечения Apple Intelligence: персик, розовый, сирень и голубой, идут по кругу
const vec3 HUES[4] = vec3[4](vec3(1.00, 0.52, 0.36), vec3(0.97, 0.38, 0.70),
        vec3(0.66, 0.45, 1.00), vec3(0.30, 0.66, 1.00));
const float WAVES = 3.0;
const float DRIFT = 0.07;

vec3 hueAt(float turn) {
    float along = fract(turn) * 4.0;
    int index = int(floor(along));
    return mix(HUES[index], HUES[(index + 1) % 4], smoothstep(0.0, 1.0, fract(along)));
}

// WHY: жёсткий минимум из четырёх расстояний до кромок даёт излом по диагонали угла, мягкий
// WHY: минимум через сумму экспонент скругляет угол, и у свечения нет видимого шва
float edgeAt(vec2 point, float softness) {
    vec4 away = vec4(point.x, ScreenSize.x - point.x, point.y, ScreenSize.y - point.y);
    vec4 weights = exp(-away / softness);
    return -softness * log(max(1.0e-6, weights.x + weights.y + weights.z + weights.w));
}

void main() {
    vec2 point = gl_FragCoord.xy;
    float edge = max(0.0, edgeAt(point, Glow.z * 0.6));
    vec2 fromCentre = point - ScreenSize * 0.5;
    float turn = atan(fromCentre.y, fromCentre.x) / TAU + 0.5;

    float swell = sin(turn * TAU * WAVES + Time * 1.3);
    float wobble = 0.5 + 0.5 * swell * sin(turn * TAU * (WAVES + 2.0) - Time * 0.9);
    float width = Glow.z * (0.65 + 0.7 * wobble) * (1.0 + Glow.y * 0.9);
    float haze = exp(-edge / max(1.0, width));
    float core = exp(-edge / max(1.0, width * 0.22));

    vec3 color = hueAt(turn + Time * DRIFT + 0.04 * sin(Time * 0.7));
    color = mix(color, Accent.rgb, Glow.w);
    float strength = (haze * 0.7 + core * 0.7) * Glow.x;
    fragColor = vec4((color + core * 0.15) * strength, clamp(strength, 0.0, 1.0));
}
