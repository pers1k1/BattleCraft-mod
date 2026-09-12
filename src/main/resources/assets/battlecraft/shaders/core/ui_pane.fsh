#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec2 ScreenSize;

uniform vec4 PaneTop;
uniform vec4 PaneBottom;
uniform vec4 PaneShape;
uniform vec4 PaneLens;
uniform vec4 PaneEdge;
uniform vec4 PaneGrade;
uniform vec4 PaneFlow;
uniform vec4 PaneBend;
uniform vec2 PaneCentre;
uniform mat3 PaneWarp;

in vec2 localPoint;
in vec4 vertexColor;

out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const float RED_INDEX = 0.985;
const float BLUE_INDEX = 1.015;
const float PROBE_TRAVEL = 200.0;

float squircleDistance(vec2 point, vec2 halfSize, float radius, float power) {
    vec2 outside = abs(point) - halfSize + radius;
    vec2 corner = max(outside, 0.0);
    float reach = power <= 2.01
            ? length(corner)
            : pow(pow(corner.x, power) + pow(corner.y, power), 1.0 / power);
    return reach + min(max(outside.x, outside.y), 0.0) - radius;
}

vec4 backdrop(vec2 uv) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 coord = uv * size - 0.5;
    vec2 base = floor(coord);
    vec2 f = coord - base;

    vec2 square = f * f;
    vec2 cube = square * f;
    vec2 w0 = (1.0 - 3.0 * f + 3.0 * square - cube) / 6.0;
    vec2 w1 = (4.0 - 6.0 * square + 3.0 * cube) / 6.0;
    vec2 w2 = (1.0 + 3.0 * f + 3.0 * square - 3.0 * cube) / 6.0;
    vec2 w3 = cube / 6.0;

    vec2 near = w0 + w1;
    vec2 far = w2 + w3;
    vec2 lower = (base + w1 / near - 1.0 + 0.5) / size;
    vec2 upper = (base + w3 / far + 1.0 + 0.5) / size;

    vec4 top = mix(texture(Sampler0, vec2(upper.x, lower.y)),
            texture(Sampler0, vec2(lower.x, lower.y)), near.x);
    vec4 bottom = mix(texture(Sampler0, vec2(upper.x, upper.y)),
            texture(Sampler0, vec2(lower.x, upper.y)), near.x);
    return mix(bottom, top, near.y);
}

// WHY: панель догорает в мировой позе, а фрагмент знает только координату снятия; без переноса
// WHY: стекло читает кусок кадра, прибитый к экрану, и рука игрока из него никуда не девается
vec2 warp(vec2 uv) {
    vec3 carried = PaneWarp * vec3(uv, 1.0);
    return carried.z <= 1.0e-5 ? uv : carried.xy / carried.z;
}

float chaosAt(vec2 point, float amount, float drift) {
    if (amount <= 0.001) {
        return 1.0;
    }
    float turn = atan(point.y, point.x);
    float wobble = sin(turn * 3.0 + drift) * 0.62 + sin(turn * 7.0 - drift * 0.7) * 0.38;
    return 1.0 - amount * 0.5 + amount * 0.5 * wobble;
}

float smoothFade(float travel) {
    float clamped = clamp(travel, 0.0, 1.0);
    return clamped * clamped * (3.0 - 2.0 * clamped);
}

float bendAt(float depth, float thickness, float index, float profile) {
    if (depth >= thickness) {
        return 0.0;
    }
    float share = 1.0 - depth / max(thickness, 1.0e-4);
    float incoming = asin(clamp(pow(share, max(profile, 0.05)), 0.0, 1.0));
    float leaving = asin(clamp(sin(incoming) / max(index, 1.0e-4), -1.0, 1.0));
    return -tan(leaving - incoming);
}

vec3 probeColor(float mode, float depth, float thickness, float sweep,
        float reveal, float shown, vec2 spot) {
    float band = clamp(depth / thickness, 0.0, 1.0);
    float travel = clamp(sweep / PROBE_TRAVEL, 0.0, 1.0);
    if (mode < 1.5) {
        return vec3(band, travel, reveal);
    }
    if (mode < 2.5) {
        return vec3(band);
    }
    if (mode < 3.5) {
        return vec3(travel);
    }
    if (mode < 4.5) {
        return backdrop(spot).rgb;
    }
    return vec3(shown);
}

float rimGlow(float outside, float range, float hardness) {
    float reach = max(range, 1.0e-4);
    float base = 1.0 + outside / 1500.0 * pow(500.0 / reach, 2.0) + hardness;
    return clamp(pow(max(base, 0.0), 5.0), 0.0, 1.0);
}

void main() {
    vec2 halfSize = PaneShape.xy;
    float edge = squircleDistance(localPoint, halfSize, PaneShape.z, PaneEdge.w);
    float skirt = max(0.0, PaneEdge.x * chaosAt(localPoint, PaneEdge.y, PaneEdge.z));
    float feather = max(1.0, fwidth(edge));
    vec2 slope = vec2(dFdx(edge), dFdy(edge));
    float steep = length(slope);
    vec2 normal = steep < 1.0e-5 ? vec2(0.0, 0.0) : slope / steep;

    if (edge > skirt + feather) {
        discard;
    }

    float depth = -edge;
    float span = PaneLens.w + skirt;
    float reveal = span <= 0.01 ? 1.0 : smoothFade((depth + skirt) / span);
    reveal *= clamp((skirt - edge) / feather + 0.5, 0.0, 1.0);

    float thickness = max(1.0, PaneLens.x);
    float carry = bendAt(max(0.0, depth), thickness, PaneLens.y, PaneGrade.w);
    vec2 offset = normal * carry * PaneBend.x * PaneBend.y * thickness / ScreenSize;

    float zoom = PaneLens.z * smoothFade(max(0.0, depth) / max(1.0, halfSize.x));
    vec2 straight = gl_FragCoord.xy / ScreenSize;
    vec2 spot = mix(straight, PaneCentre / ScreenSize, zoom);

    float spread = PaneGrade.x * 20.0;
    vec4 glass;
    glass.r = backdrop(warp(spot + offset * (1.0 - (RED_INDEX - 1.0) * spread))).r;
    glass.g = backdrop(warp(spot + offset)).g;
    glass.b = backdrop(warp(spot + offset * (1.0 - (BLUE_INDEX - 1.0) * spread))).b;
    glass.a = 1.0;

    float grey = dot(glass.rgb, LUMA);
    glass.rgb = clamp(mix(vec3(grey), glass.rgb, PaneGrade.y) * PaneGrade.z, 0.0, 1.0);

    float rise = clamp((localPoint.y + halfSize.y) / max(1.0, halfSize.y * 2.0), 0.0, 1.0);
    vec4 body = mix(PaneTop, PaneBottom, rise);
    float bodyAlpha = clamp(body.a * PaneShape.w * reveal, 0.0, 1.0);
    vec3 blended = mix(glass.rgb, body.rgb, bodyAlpha);

    float sheen = edge >= 0.0
            ? 0.0
            : rimGlow(edge, PaneFlow.x, PaneFlow.y) * PaneFlow.z * 0.7 * min(1.0, steep) * reveal;
    vec3 sheenTint = mix(vec3(1.0), body.rgb, bodyAlpha * 0.5);
    blended = mix(blended, sheenTint, clamp(sheen, 0.0, 1.0));

    float shown = reveal * vertexColor.a * ColorModulator.a;
    if (PaneFlow.w > 0.5) {
        fragColor = vec4(probeColor(PaneFlow.w, max(0.0, depth), thickness,
                length(offset * ScreenSize), reveal, shown, warp(straight)), 1.0);
        return;
    }
    fragColor = vec4(blended * vertexColor.rgb * ColorModulator.rgb, shown);
}
