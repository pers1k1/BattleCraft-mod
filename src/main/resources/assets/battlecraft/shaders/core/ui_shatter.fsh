#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float Rim;
uniform float Blur;

in vec2 stagePoint;
in vec4 shardTint;

out vec4 fragColor;

// WHY: стадия шестнадцатибитная и альфа стекла доходит до одного кванта, поэтому покрытие берётся
// WHY: корнем со шкалой в один квант источника: интерфейс это сплошная область, а не полупрозрачная
const float COVER_STEPS = 255.0;

const int BLUR_RINGS = 2;
const vec2 BLUR_TAPS[8] = vec2[8](
    vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
    vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071), vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071));

// WHY: размытие идёт только по цвету, а покрытие берётся из резкого отсчёта: осколок это кусок
// WHY: стекла с ровным сколом, расфокус ему положен по картинке, а не по форме
vec3 blurred(vec2 point, float radius) {
    vec2 step = vec2(radius) / ScreenSize;
    vec3 sum = texture(Sampler0, point).rgb;
    float weight = 1.0;
    for (int ring = 1; ring <= BLUR_RINGS; ring++) {
        vec2 reach = step * (float(ring) / float(BLUR_RINGS));
        for (int tap = 0; tap < 8; tap++) {
            sum += texture(Sampler0, point + BLUR_TAPS[tap] * reach).rgb;
            weight += 1.0;
        }
    }
    return sum / weight;
}

void main() {
    vec4 ink = texture(Sampler0, stagePoint);
    float cover = min(1.0, sqrt(ink.a) * COVER_STEPS);
    if (cover <= 0.0) discard;

    // WHY: проход канта помечен юниформом, а не нулевой альфой вершины: альфа осколка на затухании
    // WHY: округляется в ноль, и по такой метке целый осколок уходил бы в аддитивную вспышку
    if (Rim > 0.5) {
        fragColor = vec4(shardTint.rgb * shardTint.a * cover, 0.0);
        return;
    }
    vec3 tone = Blur > 0.05 ? blurred(stagePoint, Blur) : ink.rgb;
    float shown = cover * shardTint.a;
    fragColor = vec4(tone * shardTint.rgb * shown, shown);
}
