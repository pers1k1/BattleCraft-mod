#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 ScreenSize;
uniform vec4 Shape;
uniform vec4 Frame;
uniform vec4 Accent;
uniform vec4 BodyTop;
uniform vec4 BodyBottom;
uniform vec4 Source;

in vec2 cardPoint;

out vec4 fragColor;

const float PULL = 9.0;
const float PULL_LIMIT = 48.0;
const float FROST = 2.4;
const float RIM_LIGHT = 0.10;
const float POINTER_RADIUS = 3.4;
const float TINY = 1.0e-8;

float boxEdge(vec2 point, vec2 extent, float radius) {
    vec2 corner = abs(point) - extent + radius;
    return length(max(corner, 0.0)) + min(max(corner.x, corner.y), 0.0) - radius;
}

// WHY: наклон поля в экранных пикселях это и направление нормали, и мера единиц карточки на пиксель,
// WHY: поэтому сдвиг сэмпла у кромки верен при любом ракурсе без переноса матрицы в шейдер
vec2 bend(float edge, float band) {
    vec2 slope = vec2(dFdx(edge), dFdy(edge));
    float steep = dot(slope, slope);
    if (steep < TINY) return vec2(0.0);

    float depth = clamp(-edge / band, 0.0, 1.0);
    float pull = PULL * pow(1.0 - depth, 2.0);
    vec2 shift = -slope / steep * pull;
    float reach = length(shift);
    return reach > PULL_LIMIT ? shift * (PULL_LIMIT / reach) : shift;
}

vec3 frost(vec2 centre) {
    vec2 step = FROST / ScreenSize;
    vec3 sum = texture(Sampler1, centre).rgb * 4.0;
    sum += texture(Sampler1, centre + vec2(step.x, 0.0)).rgb * 2.0;
    sum += texture(Sampler1, centre - vec2(step.x, 0.0)).rgb * 2.0;
    sum += texture(Sampler1, centre + vec2(0.0, step.y)).rgb * 2.0;
    sum += texture(Sampler1, centre - vec2(0.0, step.y)).rgb * 2.0;
    sum += texture(Sampler1, centre + step).rgb;
    sum += texture(Sampler1, centre - step).rgb;
    sum += texture(Sampler1, centre + vec2(step.x, -step.y)).rgb;
    sum += texture(Sampler1, centre + vec2(-step.x, step.y)).rgb;
    return sum / 16.0;
}

vec3 glass(float edge, vec2 shift) {
    vec3 world = frost((gl_FragCoord.xy + shift) / ScreenSize);
    vec4 tint = mix(BodyTop, BodyBottom, cardPoint.y);
    float rim = pow(1.0 - clamp(-edge / Shape.w, 0.0, 1.0), 3.0);
    return mix(world, tint.rgb, tint.a) + rim * RIM_LIGHT;
}

// WHY: карточка запечена обычным блендингом интерфейса в прозрачную цель: цвет в ней уже умножен
// WHY: на альфу, а сама альфа легла квадратом, поэтому покрытие это корень из неё
vec3 face(vec3 under) {
    float v = mix(cardPoint.y, 1.0 - cardPoint.y, Source.x);
    vec4 card = texture(Sampler0, vec2(cardPoint.x, v));
    float coverage = sqrt(clamp(card.a, 0.0, 1.0));
    return mix(under, card.rgb + under * (1.0 - coverage), Source.y);
}

// WHY: узел blend стандартный, а не premultiplied: кеш BlendMode после окна остаётся тем, что ждёт
// WHY: ванильная виньетка, иначе она переприменяет его поверх своего умножения и красит экран в чёрное
void main() {
    vec2 size = Shape.xy;
    vec2 local = cardPoint * size;
    float edge = boxEdge(local - size * 0.5, size * 0.5, Shape.z);
    vec2 shift = bend(edge, Shape.w);
    float soft = max(fwidth(edge), 1.0e-4);
    float cover = clamp(0.5 - edge / soft, 0.0, 1.0) * Frame.x;
    if (cover <= 0.0) discard;

    vec3 color = face(glass(edge, shift));
    float pointer = 1.0 - smoothstep(POINTER_RADIUS * 0.5, POINTER_RADIUS, length(local - Frame.yz));
    color = mix(color, Accent.rgb, pointer * Frame.w);
    fragColor = vec4(color, cover);
}
