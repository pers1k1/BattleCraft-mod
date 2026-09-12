#version 150

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
uniform float Phase;
uniform float Time;
uniform vec4 Spark;
uniform float Mode;
uniform vec2 Sweep;
uniform vec2 Origin;

in vec2 stagePoint;

out vec4 fragColor;

const float FIELD_SCALE = 2.60;
const float WARP_NEAR = 1.60;
const float WARP_FAR = 2.40;
const float WARP_STRENGTH = 0.55;
const float RAMP_TILT = 0.22;
const float CLOUD_SHARE = 0.26;
const float BLOOM_SHARE = 0.10;
const float RADIAL_REACH = 0.78;
const float EDGE_FROM = -0.20;
const float EDGE_TO = 1.46;
const float FRONT_WIDTH = 0.26;

const float FIELD_LOW = 0.30;
const float FIELD_HIGH = 0.70;

const float BODY_IN = 0.70;
const float EDGE_SHADE = 0.40;
// WHY: стадия шестнадцатибитная, поэтому корень возвращает настоящую альфу, а порог в один квант источника
// WHY: не зависит ни от темы, ни от режима стекла, ни от ползунка непрозрачности
const float COVER_STEPS = 255.0;
const float COVER_REACH = 1.0;
const float BURN_SEAM = 0.05;
const float TINT_STRENGTH = 0.40;
const float FRONT_GLOW = 0.30;
const float SPARK_SCALE = 90.0;
const float SPARK_DRIFT = 0.40;
const float SPARK_POWER = 10.0;
const float SPARK_GLOW = 0.55;
const float EMBER_WHITE = 0.55;
const float EDGE_AT = 0.34;
const float EDGE_SPAN = 0.20;

// WHY: числа входа сняты покадрово с образца: панель не масштабируется, меняются только резкость
// WHY: и непрозрачность, а кривая ложится на ease-out пятой степени за 0.47 секунды
const float FOCUS_BLUR = 14.0;
const float ENTER_BLUR = 11.0;
const float FOCUS_EASE = 5.0;
const int FOCUS_RINGS = 2;
const vec2 FOCUS_TAPS[8] = vec2[8](
    vec2(1.0, 0.0), vec2(-1.0, 0.0), vec2(0.0, 1.0), vec2(0.0, -1.0),
    vec2(0.7071, 0.7071), vec2(-0.7071, 0.7071), vec2(0.7071, -0.7071), vec2(-0.7071, -0.7071));

const float LAND_START = 0.94;
const float LAND_END = 1.00;
const float DITHER_DEPTH = 220.0;

float hash(vec2 seed) {
    return fract(sin(dot(seed, vec2(127.1, 311.7))) * 43758.5453123);
}

float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 inner = fract(point);
    vec2 blend = inner * inner * (3.0 - 2.0 * inner);

    float corner00 = hash(cell);
    float corner10 = hash(cell + vec2(1.0, 0.0));
    float corner01 = hash(cell + vec2(0.0, 1.0));
    float corner11 = hash(cell + vec2(1.0, 1.0));

    return mix(mix(corner00, corner10, blend.x), mix(corner01, corner11, blend.x), blend.y);
}

float fbm(vec2 point) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += amplitude * valueNoise(point);
        point = point * 2.03 + vec2(17.3, 9.1);
        amplitude *= 0.5;
    }
    return sum;
}

float swirl(vec2 point) {
    float sum = 0.5 * valueNoise(point);
    point = point * 2.03 + vec2(17.3, 9.1);
    sum += 0.25 * valueNoise(point);
    point = point * 2.03 + vec2(17.3, 9.1);
    return sum + 0.125 * valueNoise(point);
}

float bloom(vec2 point, vec2 centre, vec2 radius) {
    vec2 scaled = (point - centre) / radius;
    return exp(-dot(scaled, scaled));
}

float sparkle(vec2 point) {
    return 0.5 * valueNoise(point) + 0.25 * valueNoise(point * 2.03 + vec2(17.3, 9.1));
}

float smoother(float t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
}

vec4 inkAt(vec2 point) {
    vec2 uv = point / ScreenSize;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
    return texture(Sampler0, uv);
}

// WHY: интерфейс это сплошная область, поэтому выпавший одиночный пиксель внутри неё это шум выборки,
// WHY: а не край; такая дырка пропускала бы кадр без интерфейса и давала шов, поэтому берём максимум по кресту
// WHY: у горения крест растёт только внутрь: там сцена в стадии перенесена гомографией и вне интерфейса
// WHY: не равна кадру, поэтому лишний пиксель маски вернул бы перенесённый фон каймой по контуру панели
float coverageAt(vec2 point, float reach, float inward) {
    float found = inkAt(point).a;
    if (reach <= 0.0) return found;

    float left = inkAt(point - vec2(reach, 0.0)).a;
    float right = inkAt(point + vec2(reach, 0.0)).a;
    float above = inkAt(point - vec2(0.0, reach)).a;
    float below = inkAt(point + vec2(0.0, reach)).a;
    float grown = max(max(found, left), max(max(right, above), below));
    if (inward < 0.5) return grown;

    float enclosed = max(min(left, right), min(above, below));
    return found > 0.0 || enclosed > 0.0 ? grown : found;
}

float hardCover(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return 0.0;
    return min(1.0, sqrt(texture(Sampler0, uv).a) * COVER_STEPS);
}

// WHY: цвет усредняется с весом покрытия, иначе пустые тапы за кромкой панели тянут её в чёрное
vec4 focusAt(vec2 centre, vec2 step) {
    float cover = hardCover(centre);
    vec3 tint = texture(Sampler0, centre).rgb * cover;
    float weight = 1.0;
    for (int ring = 1; ring <= FOCUS_RINGS; ring++) {
        vec2 reach = step * (float(ring) / float(FOCUS_RINGS));
        for (int tap = 0; tap < 8; tap++) {
            vec2 at = centre + FOCUS_TAPS[tap] * reach;
            float found = hardCover(at);
            tint += texture(Sampler0, at).rgb * found;
            cover += found;
            weight += 1.0;
        }
    }
    float share = cover / weight;
    return vec4(cover > 0.0 ? tint / cover : vec3(0.0), share);
}

void main() {
    // WHY: снимок сэмплится по координате снятия, а не по экранной: на горении квад лежит в мире,
    // WHY: и по gl_FragCoord он читал бы сцену с чужого места
    vec2 uv = stagePoint;
    vec2 pixel = uv * ScreenSize;
    if (Mode > 1.5) {
        float settled = 1.0 - pow(1.0 - clamp(Phase, 0.0, 1.0), FOCUS_EASE);
        vec4 soft = focusAt(uv, FOCUS_BLUR * (1.0 - settled) / ScreenSize);
        // WHY: к концу входа маска обязана прийти к той же, что у входа из света: покрытие по кресту
        // WHY: и полное открытие на посадке. Без креста выпавший пиксель на кромке кнопки оставлял
        // WHY: дырку, сквозь неё проступал кадр без интерфейса и читался как светлый контур
        float exact = min(1.0, sqrt(coverageAt(pixel, COVER_REACH, 0.0)) * COVER_STEPS);
        float land = smoothstep(LAND_START, LAND_END, Phase);
        float shown = mix(mix(soft.a, exact, settled) * settled, 1.0, land);
        vec3 tone = soft.a > 0.0 ? soft.rgb : texture(Sampler0, uv).rgb;
        fragColor = vec4(tone * shown, shown);
        return;
    }
    float aspect = ScreenSize.x / max(1.0, ScreenSize.y);
    vec2 plane = (uv - 0.5) * vec2(aspect, 1.0);
    vec2 local = (uv - Origin) * vec2(aspect, 1.0);

    float clock = Time * 0.08;
    vec2 warpNear = vec2(swirl(plane * WARP_NEAR + clock + 3.1), swirl(plane * WARP_NEAR - clock + 8.7));
    vec2 warpFar = vec2(swirl(plane * WARP_FAR + warpNear * 1.2 + 1.3),
                        swirl(plane * WARP_FAR + warpNear * 1.2 + 6.9));
    float clouds = fbm(plane * FIELD_SCALE + warpFar * WARP_STRENGTH);

    vec2 seedA = vec2(-0.22 + 0.05 * sin(Time * 0.50), 0.20 + 0.04 * cos(Time * 0.40));
    vec2 seedB = vec2(0.26 + 0.05 * cos(Time * 0.37), -0.06 + 0.04 * sin(Time * 0.53));
    vec2 seedC = vec2(0.02 + 0.06 * sin(Time * 0.29), -0.28 + 0.03 * cos(Time * 0.61));
    float seeded = max(max(bloom(plane, seedA, vec2(0.46, 0.30)), bloom(plane, seedB, vec2(0.34, 0.40))),
                       bloom(plane, seedC, vec2(0.52, 0.26)));

    float shaped = smoothstep(FIELD_LOW, FIELD_HIGH, clouds);
    float depth = clamp(((1.0 - uv.y) - Sweep.x) / max(0.02, Sweep.y), 0.0, 1.0);
    float straight = mix(depth, uv.x, RAMP_TILT);
    float radial = clamp(1.0 - length(local) / RADIAL_REACH, 0.0, 1.0);
    float ramp = mix(straight, radial, Mode);
    float field = ramp + (shaped - 0.5) * CLOUD_SHARE + (0.5 - seeded) * BLOOM_SHARE;
    float edge = mix(mix(EDGE_FROM, EDGE_TO, Mode), mix(EDGE_TO, EDGE_FROM, Mode), Phase);
    float ramped = clamp((edge - field) / FRONT_WIDTH, 0.0, 1.0);
    float reached = smoother(ramped);

    vec4 ink = inkAt(pixel);
    // WHY: содержимое доводится до резкости вслед за фронтом: куда свет ещё не дошёл, там оно
    // WHY: расфокусировано, поэтому вход из света получает ту же наводку, что и резкость с расколом.
    // WHY: горение берёт ту же наводку зеркально - резкость уходит перед фронтом, - и это безопасно:
    // WHY: содержимое лежит в стадии плоско, в мировую позу его выносит уже композитный квад,
    // WHY: а вес тапа это его покрытие, поэтому перенесённая сцена из-за кромки в цвет не попадает
    vec4 soft = focusAt(uv, ENTER_BLUR * (1.0 - reached) / ScreenSize);
    vec3 tone = soft.a > 0.0 ? soft.rgb : ink.rgb;
    float body = smoothstep(0.0, BODY_IN, reached);
    float offset = (ramped - EDGE_AT) / EDGE_SPAN;
    float line = exp(-offset * offset);

    float cover = min(1.0, sqrt(coverageAt(pixel, COVER_REACH, Mode)) * COVER_STEPS);
    float charge = Spark.a;
    // WHY: у горения полоса кромки не темнит: тёмный ободок шириной в десятки пикселей читается как
    // WHY: затухание интерфейса, а не как фронт, и вход в свете от него не зависит
    vec3 shaded = tone * (1.0 - line * EDGE_SHADE * (1.0 - charge) * (1.0 - Mode));
    vec3 tinted = mix(shaded, Spark.rgb, line * TINT_STRENGTH * charge);

    float land = smoothstep(LAND_START, LAND_END, Phase) * (1.0 - Mode);
    // WHY: шов старта горения поднимает маску только под покрытием: полный подъём требовал бы, чтобы
    // WHY: сцена совпадала с кадром вне интерфейса, а на мировом кваде это неверно по построению
    float seam = (1.0 - smoothstep(0.0, BURN_SEAM, Phase)) * Mode;
    float settled = max(land, seam);
    float mask = mix(mix(body * cover, cover, seam), 1.0, land);

    vec3 ember = mix(Spark.rgb, mix(Spark.rgb, vec3(1.0), EMBER_WHITE), Mode);
    float glint = charge > 0.0 ? pow(sparkle(plane * SPARK_SCALE + Time * SPARK_DRIFT), SPARK_POWER) : 0.0;
    float lift = cover * body * line * charge * (FRONT_GLOW + glint * SPARK_GLOW) * (1.0 - settled);
    float dither = (hash(pixel + fract(Time) * 61.7) - 0.5) / DITHER_DEPTH * line * mask;

    fragColor = vec4((tinted + ember * lift + dither) * mask, mask);
}
