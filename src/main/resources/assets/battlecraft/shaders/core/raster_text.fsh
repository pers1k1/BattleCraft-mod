#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TextWeight;
uniform vec4 RestMove;

uniform vec4 RevealBand;
uniform vec4 RevealShade;

in float bandX;
in vec4 vertexColor;
in vec2 texCoord0;
in vec2 livePixel;
in vec2 restPixel;

out vec4 fragColor;

const float CENTER_WEIGHT = 0.36;
const float AXIS_WEIGHT = 0.11;
const float DIAGONAL_WEIGHT = 0.05;
const float AXIS_STEP = 0.42;
const float DIAGONAL_STEP = 0.34;
const float COARSE_TEXELS = 3.0;
const float FINE_TEXELS = 5.0;
const float SMALL_GAIN = 1.32;
const float SMALL_LIFT = 0.055;
const float FLOOR = 0.025;
const float WEIGHT_GAIN = 0.20;

float sampledCoverage(vec2 uv, vec2 stepX, vec2 stepY) {
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
    coverage = smoothstep(FLOOR, mix(0.88, 0.68, smallText), coverage);
    return pow(clamp(coverage, 0.0, 1.0), mix(0.95, 0.78, smallText));
}

const vec4 AT_REST = vec4(1.0, 1.0, 0.0, 0.0);

// WHY: порог прилипает к пиксельной сетке, и строка в ходу шла бы ступенями. В ходу покрытие
// WHY: считается на пикселях сетки покоя с футпринтом пикселя покоя и переносится на живые пиксели
// WHY: билинейно: чернила не меняются, строка едет ровно, а к концу хода веса сходятся к покою
float restCoverage(vec2 uv) {
    vec2 stepX = dFdx(uv);
    vec2 stepY = dFdy(uv);
    if (RestMove == AT_REST) return sampledCoverage(uv, stepX, stepY);

    mat2 toTexture = mat2(stepX, stepY) * inverse(mat2(dFdx(livePixel), dFdy(livePixel)));
    vec2 restStepX = toTexture * vec2(RestMove.x, 0.0);
    vec2 restStepY = toTexture * vec2(0.0, RestMove.y);
    vec2 base = floor(restPixel - 0.5) + 0.5;
    vec2 blend = restPixel - base;
    vec2 anchor = uv + toTexture * ((base - restPixel) * RestMove.xy);
    float nearTop = sampledCoverage(anchor, restStepX, restStepY);
    float farTop = sampledCoverage(anchor + restStepX, restStepX, restStepY);
    float nearBottom = sampledCoverage(anchor + restStepY, restStepX, restStepY);
    float farBottom = sampledCoverage(anchor + restStepX + restStepY, restStepX, restStepY);
    return mix(mix(nearTop, farTop, blend.x), mix(nearBottom, farBottom, blend.x), blend.y);
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
    float coverage = restCoverage(texCoord0);
    fragColor = revealed(vec4(color.rgb, color.a * coverage));

    if (fragColor.a < 0.008) {
        discard;
    }
}
