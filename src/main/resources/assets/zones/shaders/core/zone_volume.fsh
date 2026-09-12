#version 150

in vec2 shapeCoord;
in vec3 surfaceNormal;
in vec3 cameraRelativePos;

uniform vec4 ZoneColor;
uniform float FresnelPower;
uniform float PulsePhase;
uniform float CaptureProgress;
uniform float SurfaceMode;
uniform float AnimationTime;

out vec4 fragColor;

const float TAU = 6.2831853;
const float CAPTURE_EDGE = 0.02;
const float CAPTURE_GLOW = 0.6;
const float RING_WAVE_SPEED = 0.083;
const float RING_WAVE_COUNT = 2.0;
const float RING_WAVE_DEPTH = 0.28;
const float WALL_BAND_SPEED = 0.11;
const float WALL_BAND_COUNT = 2.5;
const float WALL_BAND_DEPTH = 0.22;
const float GROUND_GLOW_POWER = 3.0;
const float GROUND_GLOW_DEPTH = 0.35;
const float RIBBON_SHARPNESS = 0.55;

float travellingWave(float coord, float count, float speed) {
    return 0.5 + 0.5 * sin((coord * count - AnimationTime * speed) * TAU);
}

void main() {
    vec3 viewDirection = normalize(-cameraRelativePos);
    float facing = abs(dot(normalize(surfaceNormal), viewDirection));
    float rim = pow(1.0 - facing, FresnelPower);

    float heightFade = smoothstep(1.0, 0.0, shapeCoord.x);
    float groundGlow = 1.0 + GROUND_GLOW_DEPTH * pow(1.0 - shapeCoord.x, GROUND_GLOW_POWER);
    float pulse = 0.85 + 0.15 * sin(PulsePhase);

    float filled = smoothstep(CaptureProgress + CAPTURE_EDGE, CaptureProgress - CAPTURE_EDGE, shapeCoord.y);
    float captureBoost = filled * CAPTURE_GLOW * step(0.001, CaptureProgress);

    float bands = 1.0 + WALL_BAND_DEPTH * (travellingWave(shapeCoord.x, WALL_BAND_COUNT, WALL_BAND_SPEED) - 0.5);
    float wave = 1.0 + RING_WAVE_DEPTH * (travellingWave(shapeCoord.y, RING_WAVE_COUNT, RING_WAVE_SPEED) - 0.5);
    float ribbonProfile = pow(sin(shapeCoord.x * 3.14159265), RIBBON_SHARPNESS);

    float wallAlpha = rim * heightFade * pulse * groundGlow * bands;
    float ringAlpha = ribbonProfile * pulse * wave * (1.0 + captureBoost);
    float alpha = mix(wallAlpha, ringAlpha, SurfaceMode);

    fragColor = vec4(ZoneColor.rgb * (1.0 + captureBoost), ZoneColor.a * alpha);

    if (fragColor.a < 0.004) {
        discard;
    }
}
