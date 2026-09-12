#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec2 ScreenSize;
uniform float Dispersion;
uniform float Brightness;
uniform mat3 LensWarp;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

// WHY: UV сэмпла считаются на процессоре и в горении уже перенесены в мировую позу, поэтому опорная
// WHY: точка обязана быть перенесена тем же переносом, иначе изгиб вберёт в себя весь мировой сдвиг
vec2 warp(vec2 uv) {
    vec3 carried = LensWarp * vec3(uv, 1.0);
    return carried.z <= 1.0e-5 ? uv : carried.xy / carried.z;
}

void main() {
    vec2 straight = warp(gl_FragCoord.xy / ScreenSize);
    vec2 bend = (texCoord0 - straight) * Dispersion;

    vec4 color = texture(Sampler0, texCoord0);
    color.r = texture(Sampler0, texCoord0 + bend).r;
    color.b = texture(Sampler0, texCoord0 - bend).b;

    color.rgb = clamp(color.rgb * Brightness, 0.0, 1.0);
    fragColor = color * vertexColor * ColorModulator;
}
