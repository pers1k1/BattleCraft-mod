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

const float CENTER_WEIGHT = 0.36;
const float AXIS_WEIGHT = 0.11;
const float DIAGONAL_WEIGHT = 0.05;
const float AXIS_STEP = 0.42;
const float DIAGONAL_STEP = 0.34;
const float COARSE_TEXELS = 3.0;
const float FINE_TEXELS = 5.5;
const float SMALL_GAIN = 1.14;
const float SMALL_LIFT = 0.026;
const float FLOOR = 0.025;
const float WEIGHT_GAIN = 0.20;

float sampledCoverage(vec2 uv) {
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
    float diagonals =
        texture(Sampler0, uv + (stepX + stepY) * DIAGONAL_STEP).a +
        texture(Sampler0, uv - (stepX + stepY) * DIAGONAL_STEP).a +
        texture(Sampler0, uv + (stepX - stepY) * DIAGONAL_STEP).a +
        texture(Sampler0, uv + (stepY - stepX) * DIAGONAL_STEP).a;

    float coverage = center * CENTER_WEIGHT + axes * AXIS_WEIGHT + diagonals * DIAGONAL_WEIGHT;
    float smallText = smoothstep(COARSE_TEXELS, FINE_TEXELS, footprint);
    coverage = mix(coverage, clamp(coverage * SMALL_GAIN + SMALL_LIFT, 0.0, 1.0), smallText);
    coverage = clamp(coverage + TextWeight * WEIGHT_GAIN, 0.0, 1.0);
    coverage = smoothstep(FLOOR, mix(0.88, 0.78, smallText), coverage);
    return pow(clamp(coverage, 0.0, 1.0), mix(0.95, 0.86, smallText));
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
    float coverage = sampledCoverage(texCoord0);
    fragColor = revealed(vec4(color.rgb, color.a * coverage));

    if (fragColor.a < 0.008) {
        discard;
    }
}
