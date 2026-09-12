#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float TextWeight;

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

void main() {
    vec4 color = vertexColor * ColorModulator;
    float coverage = sharpCoverage(texCoord0);
    fragColor = vec4(color.rgb, color.a * coverage);

    if (fragColor.a < 0.008) {
        discard;
    }
}
