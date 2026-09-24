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

const float CENTER_WEIGHT = 0.44;
const float AXIS_WEIGHT = 0.14;
const float AXIS_STEP = 0.44;
const float COARSE_TEXELS = 3.0;
const float FINE_TEXELS = 5.5;
const float THRESHOLD = 0.50;
const float SMALL_THRESHOLD = 0.37;
const float EDGE_HALF = 0.27;
const float SMALL_EDGE_HALF = 0.32;
const float WEIGHT_SHIFT = 0.26;
const float SMALL_WEIGHT_SHARE = 0.5;

// WHY: покрытие берётся усреднением по футпринту, иначе на минификации штрих рассыпается,
// WHY: а край режется порогом шириной в долю пикселя: отсюда резкость без утолщения
float sharpCoverage(vec2 uv) {
    vec2 stepX = dFdx(uv);
    vec2 stepY = dFdy(uv);
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    float footprint = max(length(stepX * atlasSize), length(stepY * atlasSize));

    float center = texture(Sampler0, uv).a;
    float axes =
        texture(Sampler0, uv + stepX * AXIS_STEP).a +
        texture(Sampler0, uv - stepX * AXIS_STEP).a +
        texture(Sampler0, uv + stepY * AXIS_STEP).a +
        texture(Sampler0, uv - stepY * AXIS_STEP).a;
    float ink = center * CENTER_WEIGHT + axes * AXIS_WEIGHT;

    float smallText = smoothstep(COARSE_TEXELS, FINE_TEXELS, footprint);
    float shift = TextWeight * WEIGHT_SHIFT * mix(1.0, SMALL_WEIGHT_SHARE, smallText);
    float threshold = mix(THRESHOLD, SMALL_THRESHOLD, smallText) - shift;
    float edge = mix(EDGE_HALF, SMALL_EDGE_HALF, smallText);
    return smoothstep(threshold - edge, threshold + edge, ink);
}

const float REVEAL_OPAQUE_AT = 0.7;

// WHY: бегущая строка гаснет у края коробки по пикселю, а не буквой целиком: край проявляется
// WHY: как свет по надписи, а подмес тёмного тона у самой кромки даёт тень на стыке букв
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
    float coverage = sharpCoverage(texCoord0);
    fragColor = revealed(vec4(color.rgb, color.a * coverage));

    if (fragColor.a < 0.008) {
        discard;
    }
}
