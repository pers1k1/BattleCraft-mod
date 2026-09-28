#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

uniform vec2 ScreenSize;
uniform float Time;
uniform vec2 Pointer;
uniform vec4 Ink;
uniform vec4 Backdrop;
uniform float Pitch;
uniform vec4 Source;
uniform vec4 CoverA;
uniform vec4 CoverB;
uniform float Dotted;
uniform vec4 Mood;

in vec2 texCoord0;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const float LEVELS = 4.0;
const float DOT_FILL = 0.43;
const float DOT_REST = 0.09;
const float REST_LIGHT = 0.03;
const float GLOW = 0.24;
const float HOT_POWER = 3.0;
const float LENS_REACH = 0.17;
const float LENS_BULGE = 0.32;
const float SPREAD = 0.62;
const float RADIAL_SHARE = 0.55;
const float BACK_OVERSHOOT = 1.9;
const float DITHER_DEPTH = 255.0;
const float PI = 3.14159265;
const float GOLDEN_ANGLE = 2.39996323;
const int BLUR_TAPS = 24;
const float BLUR_FALLOFF = 2.2;
const float HAZE_REACH = 0.02;
const float HAZE_CELLS = 1.6;
const float HAZE_SOFTEN = 0.7;
const float HAZE_ONSET = 0.1;
// WHY: две октавы шума набирают 0.75 против 0.97 у пяти, и туманность под дымкой темнела бы;
// WHY: прибавка растёт с дымкой, поэтому без неё свечение фона остаётся прежним
const float SOFT_GAIN = 0.25;
const float PLANET_BLUR = 0.06;

// WHY: порядок порогов Байера 4x4: соседние точки загораются в разнесённом порядке, поэтому
// WHY: полутон собирается из несоединённых точек, а не из сплошных пятен
const float BAYER[16] = float[16](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0,
        3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

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

// WHY: доля октавы дробная: дымка снимает мелкие октавы плавно, целое число давало скачок детали
float fbm(vec2 point, float detail) {
    float sum = 0.0;
    float amplitude = 0.5;
    for (int octave = 0; octave < 5; octave++) {
        if (float(octave) >= detail) break;
        sum += amplitude * clamp(detail - float(octave), 0.0, 1.0) * valueNoise(point);
        point = point * 2.03 + vec2(17.3, 9.1);
        amplitude *= 0.5;
    }
    return sum;
}

vec2 planeOf(vec2 uv) {
    return (uv - 0.5) * vec2(ScreenSize.x / max(1.0, ScreenSize.y), 1.0);
}

// WHY: приближение настроения меню масштабирует всю картину вокруг центра экрана, вместе с сеткой
// WHY: точек: точки растут и расходятся, как у снимка под камерой, а не перетекают по сетке
vec2 zoomed(vec2 uv) {
    return 0.5 + (uv - 0.5) / max(Mood.x, 0.001);
}

vec2 unzoomed(vec2 uv) {
    return 0.5 + (uv - 0.5) * max(Mood.x, 0.001);
}

// WHY: луч идёт из-за левого верхнего угла и медленно качается, как фонарь на образце pixel deep;
// WHY: ширина растёт с дальностью, а свет гаснет по экспоненте, поэтому конец луча тает в облаках
float beamAt(vec2 plane, float aspect) {
    float angle = -0.42 + 0.10 * sin(Time * 0.13);
    vec2 along = vec2(cos(angle), sin(angle));
    vec2 across = vec2(-along.y, along.x);
    vec2 from = plane - vec2(-aspect * 0.5 - 0.08, 0.46);
    float reach = dot(from, along);
    float width = 0.04 + max(reach, 0.0) * 0.26;
    float side = dot(from, across) / width;
    return exp(-side * side) * smoothstep(0.0, 0.18, reach) * exp(-reach * 0.85);
}

float planetHalo(vec2 offset, float reach, vec3 light) {
    float side = max(dot(normalize(vec3(offset, 0.25)), light), 0.0);
    return exp(-(max(reach, 1.0) - 1.0) * 10.0) * side * 0.55;
}

float planetBody(vec2 offset, float reach, vec3 light) {
    vec3 normal = vec3(offset, sqrt(max(0.0, 1.0 - reach * reach)));
    float lit = max(dot(normal, light), 0.0);
    vec2 surface = vec2(atan(normal.x, normal.z) + Time * 0.035, normal.y * 1.6);
    float bands = fbm(surface * vec2(2.2, 3.4) + vec2(0.0, sin(surface.x * 1.5) * 0.4), 4.0);
    float rim = smoothstep(0.82, 1.0, reach) * lit * 0.5;
    return clamp(lit * (0.35 + 0.75 * bands) + rim, 0.0, 1.0);
}

// WHY: планета освещена со стороны луча, поверхность крутится шумом по сферическим координатам,
// WHY: а ореол атмосферы горит только на освещённой стороне: из точек читается объём, а не пятно.
// WHY: Под дымкой кромка диска расплывается полосой, иначе размытая планета оставалась с резким краем
float planetAt(vec2 plane, float aspect, float soften) {
    vec2 offset = (plane - vec2(aspect * 0.17, -0.06)) / 0.34;
    float reach = length(offset);
    vec3 light = normalize(vec3(-0.62, 0.48, 0.62));
    float blur = soften * PLANET_BLUR;
    if (reach >= 1.0 + blur) return planetHalo(offset, reach, light);
    if (reach < 1.0 - blur) return planetBody(offset, reach, light);
    float inside = min(reach, 0.999);
    float body = planetBody(offset * inside / max(reach, 0.0001), inside, light);
    return mix(body, planetHalo(offset, reach, light), smoothstep(1.0 - blur, 1.0 + blur, reach));
}

float scene(vec2 uv, float detail, float soften) {
    vec2 plane = planeOf(uv);
    float aspect = ScreenSize.x / max(1.0, ScreenSize.y);
    float clock = Time * 0.03;
    vec2 warp = vec2(fbm(plane * 1.3 + clock, 3.0), fbm(plane * 1.3 - clock + 5.2, 3.0));
    float cloud = fbm(plane * 2.0 + warp * 1.2 + vec2(0.0, clock), detail) * (1.0 + SOFT_GAIN * soften);
    float nebula = smoothstep(0.48, 0.86, cloud);
    float beam = beamAt(plane, aspect);
    float planet = planetAt(plane, aspect, soften);
    float value = max(planet, nebula * 0.34 + beam * (0.32 + nebula * 0.5));
    float vignette = 1.0 - smoothstep(0.55, 1.15, length(plane * vec2(0.75, 1.0)));
    return clamp(value * vignette, 0.0, 1.0);
}

vec2 coverAt(vec4 cover, vec2 uv) {
    return vec2(uv.x, 1.0 - uv.y) * cover.xy + cover.zw;
}

vec3 pictureAt(float kind, sampler2D picture, vec4 cover, vec2 uv, float detail) {
    if (kind < 0.5) return vec3(scene(uv, detail, 0.0));
    return texture(picture, coverAt(cover, uv)).rgb;
}

// WHY: у своих обоев нет мипмапов, поэтому дымка это диск отсчётов по золотому углу с гауссовым
// WHY: весом: отсчёты ложатся равномерно по площади, и на резких кромках не встают кольца
vec3 blurred(vec4 cover, vec2 uv, float reach) {
    vec2 span = vec2(ScreenSize.y / max(1.0, ScreenSize.x), 1.0) * reach;
    float turn = hash(floor(gl_FragCoord.xy)) * 2.0 * PI;
    vec3 sum = vec3(0.0);
    float weight = 0.0;
    for (int tap = 0; tap < BLUR_TAPS; tap++) {
        float share = (float(tap) + 0.5) / float(BLUR_TAPS);
        float angle = float(tap) * GOLDEN_ANGLE + turn;
        float strength = exp(-share * BLUR_FALLOFF);
        vec2 offset = vec2(cos(angle), sin(angle)) * sqrt(share) * span;
        sum += texture(Sampler0, coverAt(cover, uv + offset)).rgb * strength;
        weight += strength;
    }
    return sum / weight;
}

// WHY: мягкая картина нужна и для свечения фона, и для растворения точек в дымке; у планеты
// WHY: мягкость даёт шум из двух октав, у своих обоев - размытие шире клетки сетки. Радиус
// WHY: входит от нуля по началу дымки: иначе свечение между точками менялось бы скачком
vec3 softPicture(vec2 uv) {
    if (Source.x < 0.5) return vec3(scene(uv, 2.0, Mood.y));
    if (Mood.y <= 0.001) return pictureAt(1.0, Sampler0, CoverA, uv, 2.0);
    float cellReach = Pitch * HAZE_CELLS / max(1.0, ScreenSize.y);
    return blurred(CoverA, uv, max(cellReach, Mood.y * HAZE_REACH) * smoothstep(0.0, HAZE_ONSET, Mood.y));
}

// WHY: под дымкой линза гаснет: мягкое среднее идёт без неё, и под курсором точки разошлись бы
// WHY: с размытием; к тому же в меню курсор ходит по окну, а не по обоям
vec2 lens(vec2 uv) {
    vec2 aspect = vec2(ScreenSize.x / max(1.0, ScreenSize.y), 1.0);
    vec2 aim = zoomed(Pointer);
    vec2 toward = (uv - aim) * aspect;
    float pull = LENS_BULGE * exp(-dot(toward, toward) / (LENS_REACH * LENS_REACH)) * (1.0 - Mood.y);
    return uv - (uv - aim) * pull;
}

float level(float value, vec2 cell) {
    ivec2 slot = ivec2(mod(cell, 4.0));
    float threshold = (BAYER[slot.x + slot.y * 4] + 0.5) / 16.0;
    return clamp(floor(value * LEVELS + threshold) / LEVELS, 0.0, 1.0);
}

float disc(vec2 local, float radius, float blur) {
    float soft = 1.0 / max(1.0, Pitch) + blur;
    return smoothstep(radius + soft, radius - soft, length(local));
}

float backOut(float t) {
    float shifted = t - 1.0;
    return 1.0 + shifted * shifted * ((BACK_OVERSHOOT + 1.0) * shifted + BACK_OVERSHOOT);
}

// WHY: точка выходит со своей задержкой: шум плюс удаление от центра, поэтому картина собирается
// WHY: из середины наружу россыпью, а не волной с ровным фронтом
float arrival(float progress, vec2 cell, vec2 uv) {
    float spread = mix(hash(cell + 3.7), length(planeOf(uv)) * 1.2, RADIAL_SHARE);
    return clamp((progress - clamp(spread, 0.0, 1.0) * SPREAD) / (1.0 - SPREAD), 0.0, 1.0);
}

vec3 inkFor(float value) {
    vec3 hot = mix(Ink.rgb, vec3(1.0), pow(value, HOT_POWER) * Ink.a);
    return hot * (0.32 + 0.68 * value);
}

float dotRadius(float lit, float grown) {
    return (DOT_REST + (DOT_FILL - DOT_REST) * lit) * grown;
}

vec3 inked(vec3 ground, float lit, float grown) {
    if (lit <= 0.0) return vec3(0.0);
    float radius = dotRadius(lit, grown);
    return (inkFor(lit) - ground) * min(1.0, PI * radius * radius);
}

// WHY: размытая сетка точек сходится к средней яркости клетки: доля площади диска, залитая
// WHY: чернилами. Порог Байера даёт соседние уровни в пропорции дробной части, поэтому среднее
// WHY: берётся по двум уровням, и дымка не темнит полутона. Дымка ведёт картину к этому среднему
vec3 settled(vec3 ground, vec3 rest, float value, float grown) {
    float scaled = clamp(value, 0.0, 1.0) * LEVELS;
    float lower = min(floor(scaled), LEVELS - 1.0);
    vec3 below = inked(ground, lower / LEVELS, grown);
    vec3 above = inked(ground, (lower + 1.0) / LEVELS, grown);
    return ground + rest * PI * DOT_REST * DOT_REST + mix(below, above, scaled - lower);
}

// WHY: дымка сначала смягчает кромку точки, но не дальше границы клетки: диск шире клетки
// WHY: обрезался бы её краем, и сквозь размытие проступила бы квадратная сетка
vec3 lay(vec3 color, vec2 local, float lit, float grown) {
    if (lit <= 0.0 || grown <= 0.0) return color;
    float radius = dotRadius(lit, grown);
    float blur = max(0.0, 0.5 - radius - 1.0 / max(1.0, Pitch)) * Mood.y * HAZE_SOFTEN;
    return mix(color, inkFor(lit), disc(local, radius, blur));
}

// WHY: прошлые обои приходят снимком кадра, поэтому их точка не пересчитывается, а сжимается:
// WHY: клетка снимка ужимается к своему центру, и старая точка гаснет ровно той формой, какой была.
// WHY: Снимок уже несёт приближение, дымку и виньетку своего кадра, поэтому читается по экрану
vec3 shrinking(vec3 color, vec2 centre, vec2 local, float swapped) {
    float shrink = 1.0 - smoothstep(0.0, 0.5, swapped);
    if (shrink <= 0.001 || length(local) > 0.5 * shrink) return color;
    vec2 at = centre + local * Pitch / shrink / ScreenSize;
    return pictureAt(1.0, Sampler1, CoverB, unzoomed(at), 1.0);
}

// WHY: у среднего своя задержка без шума клетки: шум дал бы каждой клетке свою яркость,
// WHY: и сквозь дымку на смене обоев проступила бы мозаика из квадратов
float arrivalSmooth(float progress, vec2 uv) {
    float spread = mix(0.5, length(planeOf(uv)) * 1.2, RADIAL_SHARE);
    return clamp((progress - clamp(spread, 0.0, 1.0) * SPREAD) / (1.0 - SPREAD), 0.0, 1.0);
}

float grownAt(float swapped, float assembled) {
    return backOut(clamp(swapped * 2.0 - 1.0, 0.0, 1.0)) * backOut(assembled);
}

vec3 dotted(vec2 frag, vec2 uv, float shade) {
    vec2 cell = floor(frag / Pitch);
    vec2 centre = (cell + 0.5) * Pitch / ScreenSize;
    vec2 local = frag / Pitch - cell - 0.5;
    float swapped = arrival(Source.z, cell, centre);
    float soft = dot(softPicture(uv), LUMA);
    vec3 ground = Backdrop.rgb + Ink.rgb * pow(soft, 2.0) * GLOW * Source.w;
    vec3 rest = Ink.rgb * REST_LIGHT * smoothstep(0.0, 0.25, Source.w);

    float fresh = level(dot(pictureAt(Source.x, Sampler0, CoverA, lens(centre), 5.0), LUMA), cell);
    vec3 sharp = lay(ground + rest * disc(local, DOT_REST, 0.0), local, fresh,
            grownAt(swapped, arrival(Source.w, cell, centre)));
    float calm = grownAt(arrivalSmooth(Source.z, uv), arrivalSmooth(Source.w, uv));
    vec3 image = mix(sharp, settled(ground, rest, soft, calm), Mood.y) * shade;
    return swapped < 1.0 ? shrinking(image, centre, local, swapped) : image;
}

vec3 freshPlain(vec2 uv) {
    if (Source.x < 0.5) return vec3(scene(uv, mix(5.0, 2.0, Mood.y), Mood.y));
    if (Mood.y <= 0.001) return pictureAt(1.0, Sampler0, CoverA, uv, 5.0);
    return blurred(CoverA, uv, Mood.y * HAZE_REACH);
}

vec3 plain(vec2 uv, vec2 screen, float shade) {
    vec3 shown = mix(Backdrop.rgb, freshPlain(uv), smoothstep(0.0, 1.0, Source.w)) * shade;
    if (Source.z >= 1.0) return shown;
    vec3 stale = pictureAt(Source.y, Sampler1, CoverB, screen, 5.0);
    return mix(stale, shown, smoothstep(0.0, 1.0, Source.z));
}

// WHY: виньетка и затемнение ложатся по экрану, а не по картине: это свойство взгляда,
// WHY: поэтому приближение их не растягивает; кривая краёв та же, что у авроры
float shadeAt(vec2 screen) {
    vec2 corner = (screen - 0.5) * vec2(ScreenSize.x / max(1.0, ScreenSize.y), 1.0);
    float edge = smoothstep(0.16, 0.78, dot(corner, corner));
    return (1.0 - Mood.z * edge) * (1.0 - Mood.w);
}

void main() {
    vec2 screen = gl_FragCoord.xy / ScreenSize;
    vec2 uv = zoomed(screen);
    float shade = shadeAt(screen);
    vec3 color = Dotted > 0.5 ? dotted(uv * ScreenSize, uv, shade) : plain(uv, screen, shade);
    float dither = (hash(gl_FragCoord.xy + fract(Time) * 61.7) - 0.5) / DITHER_DEPTH;
    fragColor = vec4(color + dither, 1.0);
}
