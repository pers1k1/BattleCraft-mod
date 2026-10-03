#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TextWeight;

uniform vec4 RevealBand;
uniform vec4 RevealShade;

in float bandX;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// WHY: шрифт острова Glass MediaPlayer Island: цвет буквы постоянный, покрытие идёт только в
// WHY: прозрачность, без порогов и усиления, поэтому сглаженный край не темнеет в кайму и не режется.
// WHY: Атлас запечён с запасом, и покрытие экранного пикселя считается честным средним по его
// WHY: площади (сетка GRID x GRID внутри следа пикселя): это та же доля, что даёт растр один к
// WHY: одному с пикселями. Степень чуть меньше единицы возвращает тонким штрихам плотность
const int GRID = 4;
const float COVERAGE_POWER = 0.85;
const float WEIGHT_GAIN = 0.20;
const float REVEAL_OPAQUE_AT = 0.7;

float areaCoverage(vec2 uv) {
    vec2 stepX = dFdx(uv);
    vec2 stepY = dFdy(uv);
    float sum = 0.0;
    for (int row = 0; row < GRID; row++) {
        for (int column = 0; column < GRID; column++) {
            vec2 offset = (vec2(float(column), float(row)) + 0.5) / float(GRID) - 0.5;
            sum += texture(Sampler0, uv + stepX * offset.x + stepY * offset.y).a;
        }
    }
    float coverage = clamp(sum / float(GRID * GRID) + TextWeight * WEIGHT_GAIN * sum / float(GRID * GRID), 0.0, 1.0);
    return pow(coverage, COVERAGE_POWER);
}

vec4 revealed(vec4 color) {
    float fromLeft = (bandX - RevealBand.x) / max(RevealBand.z, 0.0001);
    float fromRight = (RevealBand.y - bandX) / max(RevealBand.w, 0.0001);
    float reach = clamp(min(fromLeft, fromRight), 0.0, 1.0);
    float light = smoothstep(0.0, REVEAL_OPAQUE_AT, reach);
    float shade = (1.0 - reach) * RevealShade.a;
    return vec4(mix(color.rgb, RevealShade.rgb, shade), color.a * light);
}

void main() {
    vec4 color = vertexColor * ColorModulator;
    fragColor = revealed(vec4(color.rgb, color.a * areaCoverage(texCoord0)));

    if (fragColor.a < 0.002) {
        discard;
    }
}
