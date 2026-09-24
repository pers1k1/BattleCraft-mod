#version 150

uniform vec4 TopTint;
uniform vec4 UpperTint;
uniform vec4 LowerTint;
uniform vec4 BottomTint;
uniform vec2 Half;
uniform vec2 Shape;
uniform float Radius;
uniform float Soft;

in vec2 texCoord0;

out vec4 fragColor;

// WHY: точное расстояние до скруглённого прямоугольника по Inigo Quilez
// WHY: (https://iquilezles.org/articles/distfunctions2d/): фигура собирается не из дуг, поэтому
// WHY: кромка ровная на любом размере, а на трёх пикселях полигону с растушёвкой места уже не хватает
float rounded(vec2 point) {
    vec2 away = abs(point) - (Shape - Radius);
    return length(max(away, 0.0)) + min(max(away.x, away.y), 0.0) - Radius;
}

vec4 ramp(float share) {
    float along = share * 3.0;
    if (along < 1.0) return mix(TopTint, UpperTint, along);
    if (along < 2.0) return mix(UpperTint, LowerTint, along - 1.0);
    return mix(LowerTint, BottomTint, along - 2.0);
}

void main() {
    vec2 point = (texCoord0 - 0.5) * Half * 2.0;
    float cover = clamp(0.5 - rounded(point) / Soft, 0.0, 1.0);
    float share = clamp((point.y + Shape.y) / max(1.0e-4, Shape.y * 2.0), 0.0, 1.0);
    vec4 tint = ramp(share);

    fragColor = vec4(tint.rgb, tint.a * cover);
}
