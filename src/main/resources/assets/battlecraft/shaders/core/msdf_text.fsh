#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float DistanceRange;
uniform float TextWeight;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

const float THIN_STROKE_PIXELS = 6.0;
const float THICKEN_LIMIT = 0.09;
const float GAMMA = 0.78;
const float EDGE_SOFTNESS = 1.3;
const float WEIGHT_SHIFT = 0.26;

float median(vec3 msd) {
    return max(min(msd.r, msd.g), min(max(msd.r, msd.g), msd.b));
}

float screenPixelRange() {
    vec2 unitRange = vec2(DistanceRange) / vec2(textureSize(Sampler0, 0));
    vec2 screenTexSize = vec2(1.0) / fwidth(texCoord0);
    return max(0.5 * dot(unitRange, screenTexSize), 1.0);
}

void main() {
    float pixelRange = screenPixelRange();
    float signedDistance = median(texture(Sampler0, texCoord0).rgb) - 0.5;

    float smallText = clamp(1.0 - pixelRange / THIN_STROKE_PIXELS, 0.0, 1.0);
    float thicken = THICKEN_LIMIT * smallText + TextWeight * WEIGHT_SHIFT;

    float softness = EDGE_SOFTNESS * 0.5;
    float coverage = smoothstep(-softness, softness, pixelRange * (signedDistance + thicken));
    float emptyCoverage = smoothstep(-softness, softness, pixelRange * (thicken - 0.5));
    coverage = clamp((coverage - emptyCoverage) / max(1.0 - emptyCoverage, 0.001), 0.0, 1.0);
    coverage = pow(coverage, GAMMA);

    vec4 color = vertexColor * ColorModulator;
    fragColor = vec4(color.rgb, color.a * coverage);

    if (fragColor.a < 0.008) {
        discard;
    }
}
