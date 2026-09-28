#version 150

uniform vec4 IconColor;
uniform vec4 IconShape;

in vec2 texCoord0;

out vec4 fragColor;

const float PI = 3.14159265;
const float MARGIN = 1.15;
// WHY: у ноты головки слева, а штиль справа: без сдвига на центр тяжести она стоит не по центру
const vec2 NOTE_CENTRE = vec2(0.21, 0.04);

float circle(vec2 point, vec2 centre, float radius) {
    return length(point - centre) - radius;
}

float ring(vec2 point, vec2 centre, float radius, float stroke) {
    return abs(length(point - centre) - radius) - stroke;
}

float segment(vec2 point, vec2 from, vec2 to, float stroke) {
    vec2 along = to - from;
    float share = clamp(dot(point - from, along) / dot(along, along), 0.0, 1.0);
    return length(point - from - along * share) - stroke;
}

float box(vec2 point, vec2 centre, vec2 halfSize, float radius) {
    vec2 outside = abs(point - centre) - halfSize + radius;
    return length(max(outside, 0.0)) + min(max(outside.x, outside.y), 0.0) - radius;
}

float arc(vec2 point, vec2 centre, float radius, float stroke, float middle, float spread) {
    vec2 away = point - centre;
    float angle = atan(away.y, away.x);
    float offset = abs(mod(angle - middle + PI, 2.0 * PI) - PI);
    if (offset <= spread) return abs(length(away) - radius) - stroke;
    vec2 first = centre + radius * vec2(cos(middle - spread), sin(middle - spread));
    vec2 second = centre + radius * vec2(cos(middle + spread), sin(middle + spread));
    return min(length(point - first), length(point - second)) - stroke;
}

float triangle(vec2 point, vec2 first, vec2 second, vec2 third) {
    vec2 edgeA = second - first;
    vec2 edgeB = third - second;
    vec2 edgeC = first - third;
    vec2 awayA = point - first;
    vec2 awayB = point - second;
    vec2 awayC = point - third;
    vec2 nearA = awayA - edgeA * clamp(dot(awayA, edgeA) / dot(edgeA, edgeA), 0.0, 1.0);
    vec2 nearB = awayB - edgeB * clamp(dot(awayB, edgeB) / dot(edgeB, edgeB), 0.0, 1.0);
    vec2 nearC = awayC - edgeC * clamp(dot(awayC, edgeC) / dot(edgeC, edgeC), 0.0, 1.0);
    float turn = sign(edgeA.x * edgeC.y - edgeA.y * edgeC.x);
    vec2 closest = min(min(vec2(dot(nearA, nearA), turn * (awayA.x * edgeA.y - awayA.y * edgeA.x)),
            vec2(dot(nearB, nearB), turn * (awayB.x * edgeB.y - awayB.y * edgeB.x))),
            vec2(dot(nearC, nearC), turn * (awayC.x * edgeC.y - awayC.y * edgeC.x)));
    return -sqrt(closest.x) * sign(closest.y);
}

// WHY: у эллипса нет расстояния в замкнутом виде, а деление координат на полуоси даёт кривую
// WHY: толщину штриха: у вершин линия худела, у боков толстела. Ближайшая точка ищется
// WHY: методом Ньютона по углу (sdEllipse Inigo Quilez), пять шагов сходятся до 1e-4
float ellipse(vec2 point, vec2 axes) {
    vec2 folded = abs(point);
    vec2 start = axes * (folded - axes);
    vec2 turn = normalize(start.x < start.y ? vec2(0.01, 1.0) : vec2(1.0, 0.01));
    for (int iteration = 0; iteration < 5; iteration++) {
        vec2 along = axes * turn;
        vec2 across = axes * vec2(-turn.y, turn.x);
        float lean = dot(folded - along, across);
        float reach = dot(folded - along, along) + dot(across, across);
        float rest = sqrt(max(reach * reach - lean * lean, 0.0));
        turn = vec2(turn.x * rest - turn.y * lean, turn.y * rest + turn.x * lean) / reach;
    }
    return length(folded - axes * turn);
}

float person(vec2 point) {
    return min(circle(point, vec2(0.0, 0.46), 0.3), box(point, vec2(0.0, -0.58), vec2(0.64, 0.36), 0.34));
}

// WHY: внутренние линии идут до самой окружности со скруглёнными концами, и на вершинах конец
// WHY: вылезал за контур отростком. Они режутся кругом по средней линии контура и сливаются с ним
float globe(vec2 point, float stroke) {
    float outline = ring(point, vec2(0.0), 0.82, stroke);
    float meridian = ellipse(point, vec2(0.4, 0.82)) - stroke;
    float lines = min(segment(point, vec2(0.0, -0.82), vec2(0.0, 0.82), stroke),
            segment(point, vec2(-0.82, 0.0), vec2(0.82, 0.0), stroke));
    float chords = min(segment(point, vec2(-0.82, 0.42), vec2(0.82, 0.42), stroke),
            segment(point, vec2(-0.82, -0.42), vec2(0.82, -0.42), stroke));
    float inside = max(min(meridian, min(lines, chords)), length(point) - 0.82);
    return min(outline, inside);
}

float gear(vec2 point) {
    float angle = atan(point.y, point.x);
    float reach = 0.64 + 0.2 * smoothstep(-0.25, 0.25, cos(angle * 8.0));
    return max(length(point) - reach, -(length(point) - 0.26));
}

float sliders(vec2 point, float stroke) {
    float shape = 1.0e3;
    vec3 rows = vec3(0.55, 0.0, -0.55);
    vec3 knobs = vec3(-0.35, 0.4, -0.05);
    for (int row = 0; row < 3; row++) {
        shape = min(shape, segment(point, vec2(-0.8, rows[row]), vec2(0.8, rows[row]), stroke * 0.8));
        shape = min(shape, circle(point, vec2(knobs[row], rows[row]), 0.2));
    }
    return shape;
}

float photo(vec2 point, float stroke) {
    float frame = abs(box(point, vec2(0.0), vec2(0.86, 0.68), 0.18)) - stroke;
    float hills = min(segment(point, vec2(-0.6, -0.42), vec2(-0.18, 0.06), stroke),
            segment(point, vec2(-0.18, 0.06), vec2(0.14, -0.28), stroke));
    hills = min(hills, min(segment(point, vec2(0.14, -0.28), vec2(0.36, -0.08), stroke),
            segment(point, vec2(0.36, -0.08), vec2(0.62, -0.42), stroke)));
    return min(min(frame, hills), circle(point, vec2(0.36, 0.3), 0.12));
}

float power(vec2 point, float stroke) {
    float bow = arc(point, vec2(0.0, -0.06), 0.66, stroke, -PI * 0.5, PI * 0.78);
    return min(bow, segment(point, vec2(0.0, 0.1), vec2(0.0, 0.86), stroke));
}

float search(vec2 point, float stroke) {
    return min(ring(point, vec2(-0.14, 0.14), 0.5, stroke), segment(point, vec2(0.24, -0.24), vec2(0.78, -0.78), stroke * 1.3));
}

float toggles(vec2 point, float stroke) {
    float upper = min(abs(box(point, vec2(0.0, 0.42), vec2(0.8, 0.28), 0.28)) - stroke * 0.8,
            circle(point, vec2(-0.52, 0.42), 0.19));
    float lower = min(abs(box(point, vec2(0.0, -0.42), vec2(0.8, 0.28), 0.28)) - stroke * 0.8,
            circle(point, vec2(0.52, -0.42), 0.19));
    return min(upper, lower);
}

// WHY: волны динамика проявляются по уровню громкости по одной, как в центре управления iOS,
// WHY: а на нуле вместо волн крестик; уровень приходит числом, поэтому смена плавная
float speaker(vec2 point, float stroke, float level) {
    float body = box(point, vec2(-0.62, 0.0), vec2(0.16, 0.22), 0.06);
    float taper = 0.22 + (point.x + 0.46) * 1.05;
    float cone = max(abs(point.y) - taper, max(-0.46 - point.x, point.x + 0.02));
    float shape = min(body, cone);
    if (level <= 0.001) {
        float cross = min(segment(point, vec2(0.28, -0.22), vec2(0.72, 0.22), stroke),
                segment(point, vec2(0.28, 0.22), vec2(0.72, -0.22), stroke));
        return min(shape, cross);
    }
    for (int wave = 0; wave < 3; wave++) {
        float shown = smoothstep(float(wave) / 3.0 - 0.05, float(wave) / 3.0 + 0.15, level);
        if (shown <= 0.01) continue;
        float radius = 0.34 + 0.24 * float(wave);
        shape = min(shape, arc(point, vec2(-0.08, 0.0), radius, stroke * shown, 0.0, PI * 0.26) + (1.0 - shown) * 0.2);
    }
    return shape;
}

float sun(vec2 point, float stroke, float level) {
    float core = circle(point, vec2(0.0), 0.2 + 0.1 * level);
    float angle = atan(point.y, point.x);
    float snapped = floor(angle / (PI * 0.25) + 0.5) * PI * 0.25;
    vec2 ray = vec2(cos(snapped), sin(snapped));
    float inner = 0.4 + 0.06 * level;
    return min(core, segment(point, ray * inner, ray * (inner + 0.16 + 0.22 * level), stroke * 0.9));
}

float note(vec2 point, float stroke) {
    float stems = min(segment(point, vec2(0.02, -0.46), vec2(0.02, 0.66), stroke),
            segment(point, vec2(0.62, -0.3), vec2(0.62, 0.82), stroke));
    float beam = segment(point, vec2(0.02, 0.66), vec2(0.62, 0.82), stroke * 1.7);
    float heads = min(length((point - vec2(-0.2, -0.5)) / vec2(1.25, 1.0)) - 0.24,
            length((point - vec2(0.4, -0.34)) / vec2(1.25, 1.0)) - 0.24);
    return min(min(stems, beam), heads);
}

float fullscreen(vec2 point, float stroke) {
    vec2 folded = abs(point);
    float across = segment(folded, vec2(0.36, 0.8), vec2(0.8, 0.8), stroke);
    float down = segment(folded, vec2(0.8, 0.36), vec2(0.8, 0.8), stroke);
    return min(across, down);
}

float sparkle(vec2 point) {
    vec2 folded = abs(point);
    return pow(pow(folded.x, 0.55) + pow(folded.y, 0.55), 1.0 / 0.55) - 0.82;
}

float drop(vec2 point) {
    float bulb = circle(point, vec2(0.0, -0.26), 0.52);
    float spire = max(abs(point.x) - 0.52 * (0.94 - point.y) / 1.2, max(-0.26 - point.y, point.y - 0.94));
    return min(bulb, spire);
}

float plus(vec2 point, float stroke) {
    return min(segment(point, vec2(-0.7, 0.0), vec2(0.7, 0.0), stroke), segment(point, vec2(0.0, -0.7), vec2(0.0, 0.7), stroke));
}

float merged(float first, float second, float reach) {
    float share = clamp(0.5 + 0.5 * (second - first) / reach, 0.0, 1.0);
    return mix(second, first, share) - reach * share * (1.0 - share);
}

// WHY: треугольник воспроизведения стоит по центру рамки, а не по центру тяжести: SF Symbols
// WHY: так же держит play.fill, и в ряду кнопок он не уезжает влево от паузы
float play(vec2 point) {
    return triangle(point, vec2(-0.5, 0.64), vec2(-0.5, -0.64), vec2(0.64, 0.0)) - 0.14;
}

float pause(vec2 point) {
    vec2 folded = vec2(abs(point.x), point.y);
    return box(folded, vec2(0.33, 0.0), vec2(0.17, 0.72), 0.1);
}

float forward(vec2 point) {
    float first = triangle(point, vec2(-0.8, 0.5), vec2(-0.8, -0.5), vec2(-0.02, 0.0));
    float second = triangle(point, vec2(-0.02, 0.5), vec2(-0.02, -0.5), vec2(0.76, 0.0));
    return min(first, second) - 0.08;
}

float chevron(vec2 point, float stroke) {
    float upper = segment(point, vec2(-0.2, 0.68), vec2(0.3, 0.0), stroke * 1.3);
    float lower = segment(point, vec2(0.3, 0.0), vec2(-0.2, -0.68), stroke * 1.3);
    return min(upper, lower);
}

float calendar(vec2 point, float stroke) {
    float sheet = box(point, vec2(0.0, -0.04), vec2(0.8, 0.74), 0.2);
    float frame = abs(sheet) - stroke;
    float header = max(sheet, 0.36 - point.y);
    float days = 1.0e3;
    for (int row = 0; row < 2; row++) {
        for (int column = 0; column < 3; column++) {
            vec2 centre = vec2(-0.4 + 0.4 * float(column), 0.02 - 0.38 * float(row));
            days = min(days, circle(point, centre, 0.09));
        }
    }
    return min(min(frame, header), days);
}

// WHY: пузырь с многоточием читается как «статус в чате» без логотипа: хвост сливается с телом
// WHY: мягким минимумом, иначе на стыке остаётся угол
float chat(vec2 point) {
    float body = box(point, vec2(0.0, 0.1), vec2(0.84, 0.62), 0.56);
    float tail = triangle(point, vec2(-0.62, -0.2), vec2(-0.78, -0.86), vec2(-0.12, -0.44));
    float bubble = merged(body, tail, 0.12);
    float dots = min(circle(point, vec2(-0.36, 0.1), 0.1),
            min(circle(point, vec2(0.0, 0.1), 0.1), circle(point, vec2(0.36, 0.1), 0.1)));
    return max(bubble, -dots);
}

float motion(vec2 point, float stroke) {
    float ball = circle(point, vec2(0.36, 0.0), 0.44);
    float middle = segment(point, vec2(-0.88, 0.0), vec2(-0.24, 0.0), stroke);
    float upper = segment(point, vec2(-0.6, 0.38), vec2(-0.1, 0.38), stroke);
    float lower = segment(point, vec2(-0.6, -0.38), vec2(-0.1, -0.38), stroke);
    return min(ball, min(middle, min(upper, lower)));
}

float shapeOf(vec2 point, int kind, float stroke, float level) {
    if (kind == 0) return person(point);
    if (kind == 1) return globe(point, stroke);
    if (kind == 2) return gear(point);
    if (kind == 3) return sliders(point, stroke);
    if (kind == 4) return photo(point, stroke);
    if (kind == 5) return power(point, stroke);
    if (kind == 6) return search(point, stroke);
    if (kind == 7) return toggles(point, stroke);
    if (kind == 8) return speaker(point, stroke, level);
    if (kind == 9) return sun(point, stroke, level);
    if (kind == 10) return note(point + NOTE_CENTRE, stroke);
    if (kind == 11) return fullscreen(point, stroke);
    if (kind == 12) return sparkle(point);
    if (kind == 13) return drop(point);
    if (kind == 14) return plus(point, stroke);
    if (kind == 15) return play(point);
    if (kind == 16) return pause(point);
    if (kind == 17) return forward(point);
    if (kind == 18) return forward(point * vec2(-1.0, 1.0));
    if (kind == 19) return chevron(point * vec2(-1.0, 1.0), stroke);
    if (kind == 20) return chevron(point, stroke);
    if (kind == 21) return calendar(point, stroke);
    if (kind == 22) return chat(point);
    return motion(point, stroke);
}

// WHY: значки собраны из неявных форм и сглажены по fwidth, поэтому на любом размере у кромки
// WHY: ровно один пиксель мягкости и нет ни лесенки, ни пикселей точечной матрицы
void main() {
    vec2 point = (texCoord0 * 2.0 - 1.0) * vec2(1.0, -1.0) * MARGIN;
    float shape = shapeOf(point, int(IconShape.x + 0.5), IconShape.z, IconShape.y);
    float cover = clamp(0.5 - shape / max(1.0e-4, fwidth(shape)), 0.0, 1.0);
    if (cover <= 0.0) discard;
    fragColor = vec4(IconColor.rgb, IconColor.a * cover);
}
