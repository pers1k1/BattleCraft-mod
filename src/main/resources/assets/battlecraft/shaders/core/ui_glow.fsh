#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec3 light = texture(Sampler0, texCoord0).rgb * ColorModulator.rgb * ColorModulator.a;
    fragColor = vec4(light, 1.0);
}
