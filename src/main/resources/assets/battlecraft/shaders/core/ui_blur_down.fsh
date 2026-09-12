#version 150

uniform sampler2D Sampler0;
uniform vec2 Offset;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 gathered = texture(Sampler0, texCoord0) * 4.0;
    gathered += texture(Sampler0, texCoord0 + Offset);
    gathered += texture(Sampler0, texCoord0 - Offset);
    gathered += texture(Sampler0, texCoord0 + vec2(Offset.x, -Offset.y));
    gathered += texture(Sampler0, texCoord0 - vec2(Offset.x, -Offset.y));

    fragColor = gathered / 8.0;
}
