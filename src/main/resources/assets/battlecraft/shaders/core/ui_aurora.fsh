#version 150

uniform vec2 ScreenSize;
uniform float Time;
uniform vec2 Pointer;
uniform vec4 TintA;
uniform vec4 TintB;
uniform vec4 TintC;
uniform vec4 Backdrop;
uniform float Zoom;
uniform float Haze;
uniform float Veil;

in vec2 texCoord0;

out vec4 fragColor;

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

float layered(vec2 point, float detail) {
    float sum = 0.0;
    float weight = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 5; octave++) {
        float reach = clamp(detail - float(octave), 0.0, 1.0);
        sum += amplitude * reach * valueNoise(point);
        weight += amplitude * reach;
        point = point * 2.03 + vec2(17.3, 9.1);
        amplitude *= 0.5;
    }
    return weight > 0.0001 ? sum * (0.9765 / weight) : 0.5;
}

float fbm(vec2 point) {
    return layered(point, 5.0);
}

float bloom(vec2 point, vec2 center, vec2 radius, float angle) {
    vec2 offset = point - center;
    vec2 turned = vec2(offset.x * cos(angle) + offset.y * sin(angle),
                       offset.y * cos(angle) - offset.x * sin(angle));
    vec2 scaled = turned / radius;
    return exp(-dot(scaled, scaled));
}

void main() {
    float aspect = ScreenSize.x / max(1.0, ScreenSize.y);
    vec2 point = (texCoord0 - 0.5) * vec2(aspect, 1.0) / max(Zoom, 0.001);
    vec2 pointer = (Pointer - 0.5) * vec2(aspect, 1.0);

    // WHY: мягкость снимает октавы только у облачного слоя. Если её пустить и в warp со swirl,
    // WHY: те смещают точку сэмпла, и на переходе всё поле уезжает, будто шейдер разогнали
    vec2 drift = vec2(Time * 0.017, Time * -0.011);
    vec2 warp = vec2(fbm(point * 1.4 + drift), fbm(point * 1.4 + drift + 4.7));
    vec2 swirl = vec2(fbm(point * 2.1 + warp * 1.3 + drift * 1.7 + 2.8),
                      fbm(point * 2.1 + warp * 1.3 - drift * 1.2 + 8.3));

    float clouds = layered(point * 1.8 + swirl * 1.9, (1.0 - Haze) * 5.0);
    float filament = pow(1.0 - abs(clouds * 2.0 - 1.0), 3.4);
    float shade = 0.34 + 1.05 * clouds;
    float breath = 0.88 + 0.12 * sin(Time * 0.19);

    vec2 centerA = vec2(0.28 * sin(Time * 0.047), 0.20 * cos(Time * 0.033)) + (swirl - 0.5) * 0.42;
    vec2 centerB = vec2(0.30 * sin(Time * 0.029 + 2.1), 0.24 * cos(Time * 0.043 + 1.3)) + (warp - 0.5) * 0.50;
    vec2 centerC = vec2(0.34 * sin(Time * 0.037 + 4.3), 0.18 * cos(Time * 0.025 + 3.7)) - (swirl - 0.5) * 0.46;

    float coreA = bloom(point, centerA, vec2(0.74, 0.40) * breath, Time * 0.043) * shade;
    float coreB = bloom(point, centerB, vec2(0.38, 0.62), 2.4 - Time * 0.031) * shade;
    float coreC = bloom(point, centerC, vec2(0.55, 0.30) * breath, 1.1 + Time * 0.021) * (0.42 + 1.1 * filament);
    float halo = bloom(point, pointer, vec2(0.22, 0.22), 0.0);

    vec3 light = TintA.rgb * TintA.a * coreA + TintB.rgb * TintB.a * coreB + TintC.rgb * TintC.a * coreC;
    light += mix(TintB.rgb, TintA.rgb, filament) * filament * 0.085;
    light += TintA.rgb * halo * 0.075;

    float falloff = 1.0 - 0.38 * dot(point, point);
    vec3 color = Backdrop.rgb + light * falloff;

    vec2 corner = (texCoord0 - 0.5) * vec2(aspect, 1.0);
    float edge = smoothstep(0.16, 0.78, dot(corner, corner));
    color *= 1.0 - Veil * edge;

    float grain = (hash(gl_FragCoord.xy + fract(Time) * 61.7) - 0.5) / 180.0;

    fragColor = vec4(color + grain, 1.0);
}
