#version 150

uniform sampler2D Sampler0;
uniform float Mash;
uniform float Corner;
uniform float Soft;
uniform float Inset;
uniform float Shade;
uniform float Fade;
uniform float Seed;

in vec2 texCoord0;

out vec4 fragColor;

const int TAPS = 8;
const float SPIRAL = 2.399963;
const float BLUR_REACH = 0.12;
const float WARP_REACH = 0.78;
const float SWIRL_REACH = 0.40;
const float SATURATION_LIFT = 0.55;

float hash(vec2 seed) {
    return fract(sin(dot(seed, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 inner = fract(point);
    vec2 blend = inner * inner * (3.0 - 2.0 * inner);

    float corner00 = hash(cell);
    float corner10 = hash(cell + vec2(1.0, 0.0));
    float corner01 = hash(cell + vec2(0.0, 1.0));
    float corner11 = hash(cell + vec2(1.0, 1.0));

    return mix(mix(corner00, corner10, blend.x), mix(corner01, corner11, blend.x), blend.y);
}

float fbm(vec2 point) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 3; octave++) {
        sum += amplitude * valueNoise(point);
        point = point * 2.07 + vec2(11.3, 5.7);
        amplitude *= 0.5;
    }
    return sum / 0.875;
}

// WHY: скругление считается тут, а не берётся из запечённой альфы снимка: замес двигает точку
// WHY: сэмпла, и прозрачность углов расползлась бы вместе с цветом рваной каймой
float coverage(vec2 point) {
    vec2 offset = max(max(vec2(Corner) - point, point - (1.0 - Corner)), vec2(0.0));
    if (offset.x <= 0.0 || offset.y <= 0.0) return 1.0;

    return clamp((Corner - length(offset)) / Soft + 0.5, 0.0, 1.0);
}

// WHY: далеко уведённая точка сэмпла заворачивается зеркалом, а не зажимается краем: зажим
// WHY: растягивает кромочные пиксели лучами от рамки, а зеркало держит замес из цветов самой обложки
vec2 folded(vec2 point) {
    vec2 wrapped = abs(fract((point + 1.0) * 0.5) * 2.0 - 1.0);
    return clamp(wrapped, vec2(Inset), vec2(1.0 - Inset));
}

// WHY: замес это доменное искажение, а не размытие: гауссиан на всю карточку даёт одну серую
// WHY: массу, а искажение растаскивает те же цвета разводами, и обложка в них читается
vec2 stirred(vec2 point) {
    vec2 drift = vec2(Seed, Seed * 1.37 + 4.1);
    vec2 warp = vec2(fbm(point * 3.1 + drift), fbm(point * 3.1 + drift + 7.3)) - 0.5;
    vec2 swirl = vec2(fbm(point * 6.4 + warp * 1.6 + drift), fbm(point * 6.4 + warp * 1.6 + 3.1)) - 0.5;
    return point + (warp * WARP_REACH + swirl * SWIRL_REACH) * Mash;
}

vec3 gathered(vec2 spot) {
    vec3 tone = vec3(0.0);
    for (int tap = 0; tap < TAPS; tap++) {
        float angle = float(tap) * SPIRAL;
        float reach = sqrt((float(tap) + 0.5) / float(TAPS)) * BLUR_REACH * Mash;
        vec2 at = folded(spot + vec2(cos(angle), sin(angle)) * reach);
        tone += texture(Sampler0, at).rgb;
    }
    return tone / float(TAPS);
}

void main() {
    vec3 tone = gathered(stirred(texCoord0));
    float grey = dot(tone, vec3(0.2126, 0.7152, 0.0722));
    tone = clamp(mix(vec3(grey), tone, 1.0 + SATURATION_LIFT * Mash), 0.0, 1.0);

    fragColor = vec4(tone * Shade, coverage(texCoord0) * Fade);
}
