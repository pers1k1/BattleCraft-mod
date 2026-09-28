#version 150

uniform vec4 Shape;
uniform vec4 Form;
uniform vec4 PriorBox0;
uniform vec4 PriorBox1;
uniform vec4 PriorBox2;
uniform vec4 PriorBox3;
uniform vec4 PriorForm0;
uniform vec4 PriorForm1;
uniform vec4 PriorForm2;
uniform vec4 PriorForm3;

out vec4 fragColor;

const float BODY_SOFT = 1.0;
const float SOLID = 0.999;

// WHY: точное расстояние до скруглённого прямоугольника по Inigo Quilez
// WHY: (https://iquilezles.org/articles/distfunctions2d/), в пикселях кадра: так сравнимы ореолы
// WHY: всплывающих с разной позой и масштабом
float rounded(vec2 point, vec4 box, float radius) {
    vec2 away = abs(point - box.xy) - (box.zw - radius);
    return length(max(away, 0.0)) + min(max(away.x, away.y), 0.0) - radius;
}

// WHY: спад квадратом остатка: у панели он крутой, как у размытой тени, а к краю дальности
// WHY: производная обнуляется, и конца ореола не видно
float spread(float away, float reach) {
    float left = 1.0 - clamp(away / max(reach, 1.0e-3), 0.0, 1.0);
    return left * left;
}

// WHY: ореол в пять процентов спадает на светлом небе всего на десяток уровней из 255, и в восьми
// WHY: битах это ступени шириной в несколько пикселей. Неподвижный шум Хименеса
// WHY: (interleaved gradient noise) в полуровня разбивает ступени и не мерцает
float dither(vec2 point) {
    float noise = fract(52.9829189 * fract(dot(point, vec2(0.06711056, 0.00583715))));
    return (noise - 0.5) / 255.0;
}

float halo(vec2 point, vec4 box, vec4 form) {
    return form.z * spread(rounded(point, box, form.x), form.y);
}

// WHY: уже нарисованное всплывающее закрывает своё место целиком, поэтому чужой ореол по его телу не идёт
float laid(vec2 point, vec4 box, vec4 form) {
    float away = rounded(point, box, form.x);
    float body = form.w * clamp(-away / BODY_SOFT, 0.0, 1.0);
    return max(halo(point, box, form), body);
}

void main() {
    vec2 point = gl_FragCoord.xy;
    float wanted = halo(point, Shape, Form);
    float before = max(max(laid(point, PriorBox0, PriorForm0), laid(point, PriorBox1, PriorForm1)),
            max(laid(point, PriorBox2, PriorForm2), laid(point, PriorBox3, PriorForm3)));
    // WHY: чёрный с альфой b поверх уже погашенного на a_prev даёт 1 - (1 - a_prev)(1 - b), и при
    // WHY: b = (a_new - a_prev) / (1 - a_prev) это ровно max(a_prev, a_new): ореолы не складываются,
    // WHY: в каждом пикселе остаётся самый сильный из них
    float added = before >= SOLID ? 0.0 : max(0.0, wanted - before) / (1.0 - before);
    if (added <= 0.0) discard;
    fragColor = vec4(0.0, 0.0, 0.0, max(0.0, added + dither(point)));
}
