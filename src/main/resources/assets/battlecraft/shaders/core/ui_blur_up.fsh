#version 150

uniform sampler2D Sampler0;
uniform vec2 Offset;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 gathered = texture(Sampler0, texCoord0 + vec2(-2.0 * Offset.x, 0.0));
    gathered += texture(Sampler0, texCoord0 + vec2(2.0 * Offset.x, 0.0));
    gathered += texture(Sampler0, texCoord0 + vec2(0.0, 2.0 * Offset.y));
    gathered += texture(Sampler0, texCoord0 + vec2(0.0, -2.0 * Offset.y));

    gathered += texture(Sampler0, texCoord0 + vec2(-Offset.x, Offset.y)) * 2.0;
    gathered += texture(Sampler0, texCoord0 + Offset) * 2.0;
    gathered += texture(Sampler0, texCoord0 + vec2(Offset.x, -Offset.y)) * 2.0;
    gathered += texture(Sampler0, texCoord0 - Offset) * 2.0;

    fragColor = gathered / 12.0;
}
