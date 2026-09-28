#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec2 ScreenSize;
uniform vec4 Atlas;
uniform vec4 Relief;
uniform vec4 Frost;
uniform vec4 Solid;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

const int BLUR_TAPS = 16;
const float GOLDEN_ANGLE = 2.3999632;
const float AMBIENT_LEVEL = 8.0;
// WHY: цифры «Стекло» экрана блокировки iOS 26. Внутри глифа фон матовый: сильно размыт, пропущен
// WHY: на TRANSMISSION и высветлен белым (Frost.y) и общим цветом кадра (Frost.w), поэтому над
// WHY: тёмным стекло светло-серое с оттенком обоев. Числа сняты с рефа lockscreen_08: цвет цифры
// WHY: против размытого фона под ней по методу наименьших квадратов даёт 0.74 фона + 0.24 белого +
// WHY: 0.31 среднего цвета кадра. По контуру тонкий блик, сильнее со стороны света сверху слева,
// WHY: на фаске с теневой стороны лёгкая тень
const float TRANSMISSION = 0.74;
const vec2 LIGHT = vec2(-0.6, 0.8);
const float RIM_FLOOR = 0.14;
const float RIM_LIT = 0.5;
const float RIM_BACK = 0.24;
const float SHEEN = 0.08;
const float INNER_SHADE = 0.12;
const float EDGE_HALF_PIXEL = 0.5;
const float GLYPH_SOFTEN = 0.06;
const float LEAST_COVER = 1.0e-4;

float depthAt(vec2 uv) {
    return (texture(Sampler0, uv).r - 0.5) * 2.0 * Atlas.y;
}

// WHY: у атласа v растёт вниз по экрану, а у gl_FragCoord y растёт вверх, поэтому вертикальная
// WHY: разность берётся сверху минус снизу: наклон сразу в осях кадра
vec2 inwardAt(vec2 uv, vec2 texel) {
    return vec2(depthAt(uv + vec2(texel.x, 0.0)) - depthAt(uv - vec2(texel.x, 0.0)),
            depthAt(uv - vec2(0.0, texel.y)) - depthAt(uv + vec2(0.0, texel.y)));
}

// WHY: доля пикселя внутри контура - интеграл ступени по квадрату пикселя. Полуширина ската
// WHY: больше половины пикселя - это размытие самой формы по полю расстояний: на переходах
// WHY: цифра расплывается и собирается обратно в чёткий контур
float coverAt(float depthPixels, float halfRamp) {
    return clamp(depthPixels / (2.0 * halfRamp) + 0.5, 0.0, 1.0);
}

// WHY: размытие - диск выборок по спирали Фогеля (шаг золотого угла, радиус как корень номера)
// WHY: по мипмапам копии кадра; уровень мипмапа близок к шагу между выборками, и диск сглаживает
// WHY: ступени крупных текселей. Равномерный диск радиуса R даёт сигму около R / 2
vec3 frostedAt(vec2 center, float radius) {
    float level = max(0.0, log2(max(radius, 1.0)) - Frost.z);
    vec3 sum = vec3(0.0);
    for (int tap = 0; tap < BLUR_TAPS; tap++) {
        float angle = float(tap) * GOLDEN_ANGLE;
        float reach = sqrt((float(tap) + 0.5) / float(BLUR_TAPS)) * radius;
        sum += textureLod(Sampler1, center + vec2(cos(angle), sin(angle)) * reach / ScreenSize, level).rgb;
    }
    return sum / float(BLUR_TAPS);
}

// WHY: у кромки стекло скруглено: наклон плавно спадает от контура до Relief.x высоты цифры
// WHY: и дальше грань плоская, без складки
float bevelAt(float depthPixels, float figurePixels) {
    float width = max(1.0, Relief.x * figurePixels);
    return 1.0 - smoothstep(0.0, width, depthPixels);
}

vec3 frostedGlass(vec2 bend, float figurePixels) {
    vec2 center = (gl_FragCoord.xy + bend) / ScreenSize;
    vec3 seen = frostedAt(center, Frost.x * figurePixels);
    vec3 ambient = textureLod(Sampler1, center, AMBIENT_LEVEL).rgb;
    return seen * TRANSMISSION + vec3(Frost.y) + ambient * Frost.w;
}

// WHY: блик уже пикселя, и выборка в центре пикселя давала лесенку на изгибах: свет блика гулял
// WHY: 0.41..0.63 от того, где контур режет пиксель. Поэтому блик интегрируется по отрезку пикселя
// WHY: вдоль нормали, как покрытие: первообразная 1 - smoothstep(0, w, t) равна w (x - x^3 + x^4 / 2)
float rimIntegral(float depthPixels, float rimPixels) {
    float x = clamp(depthPixels / rimPixels, 0.0, 1.0);
    return rimPixels * (x - x * x * x + 0.5 * x * x * x * x);
}

float rimShare(float depthPixels, float halfRamp, float rimPixels, float cover) {
    float spanned = rimIntegral(depthPixels + halfRamp, rimPixels) - rimIntegral(depthPixels - halfRamp, rimPixels);
    return spanned / (2.0 * halfRamp * max(cover, LEAST_COVER));
}

// WHY: блик контура расплывается вместе с кромкой, когда форма размыта на переходе
vec3 glassAt(float depthPixels, float halfRamp, float figurePixels, float cover) {
    vec2 inward = inwardAt(texCoord0, 1.0 / vec2(textureSize(Sampler0, 0)));
    vec2 outward = length(inward) > 1.0e-4 ? -normalize(inward) : vec2(0.0);
    float edge = bevelAt(depthPixels, figurePixels);
    vec3 glass = frostedGlass(outward * Relief.y * figurePixels * edge, figurePixels);
    float facing = dot(outward, LIGHT);
    float rimPixels = max(1.0, Relief.z * figurePixels) + 2.0 * (halfRamp - EDGE_HALF_PIXEL);
    float rim = min(rimShare(depthPixels, halfRamp, rimPixels, cover), 1.0);
    glass *= 1.0 - INNER_SHADE * edge * (0.25 + 0.75 * max(-facing, 0.0));
    glass += SHEEN * edge * max(facing, 0.0);
    glass += rim * (RIM_FLOOR + RIM_LIT * max(facing, 0.0) + RIM_BACK * max(-facing, 0.0));
    return min(glass, vec3(1.0));
}

void main() {
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    float texelsAcross = length(vec2(dFdx(texCoord0.x), dFdy(texCoord0.x))) * atlasSize.x;
    float pixelsPerTexel = 1.0 / max(texelsAcross, 1.0e-4);
    float figurePixels = Atlas.x * pixelsPerTexel;
    float depthPixels = depthAt(texCoord0) * pixelsPerTexel + Atlas.z * figurePixels;
    float halfRamp = EDGE_HALF_PIXEL + (Atlas.w + vertexColor.r * GLYPH_SOFTEN) * figurePixels;
    float cover = coverAt(depthPixels, halfRamp);
    if (cover * vertexColor.a <= 0.002) discard;

    vec3 color = Solid.rgb;
    if (Solid.a < 0.999) color = mix(glassAt(depthPixels, halfRamp, figurePixels, cover), Solid.rgb, Solid.a);
    fragColor = vec4(color, cover * vertexColor.a);
}
