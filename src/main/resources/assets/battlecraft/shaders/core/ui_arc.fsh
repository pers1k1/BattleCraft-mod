#version 150

uniform vec4 ArcColor;
uniform vec2 Half;
uniform vec4 Arc;

in vec2 texCoord0;

out vec4 fragColor;

const float TAU = 6.2831853;

// WHY: дуга считается точным расстоянием, а не полосой треугольников: у полосы торцы срезаны
// WHY: без растушёвки и шли ступенькой, а маленькое кольцо читалось многоугольником.
// WHY: Arc = (радиус середины, толщина, доля дуги, мягкость кромки), угол от верха по часовой
float capDistance(vec2 point, float share, float sweep) {
    if (sweep >= 0.9999) return -1.0e6;
    float around = length(point) * TAU;
    if (share <= sweep) return -min(share, sweep - share) * around;
    return min(share - sweep, 1.0 - share) * around;
}

void main() {
    vec2 point = (texCoord0 - 0.5) * Half * 2.0;
    float turn = atan(point.x, -point.y);
    float share = (turn < 0.0 ? turn + TAU : turn) / TAU;
    float radial = abs(length(point) - Arc.x) - Arc.y * 0.5;
    float edge = max(radial, capDistance(point, share, Arc.z));
    float cover = clamp(0.5 - edge / max(1.0e-4, Arc.w), 0.0, 1.0);
    if (cover <= 0.0) discard;
    fragColor = vec4(ArcColor.rgb, ArcColor.a * cover);
}
