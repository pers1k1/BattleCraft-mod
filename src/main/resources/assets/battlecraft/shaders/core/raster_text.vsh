#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 RestMove;
uniform float RestPixels;

out vec4 vertexColor;
out vec2 texCoord0;
out float bandX;
out vec2 livePixel;
out vec2 restPixel;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    bandX = Position.x;
    livePixel = Position.xy * RestPixels;
    restPixel = (Position.xy - RestMove.zw) / RestMove.xy * RestPixels;
}
