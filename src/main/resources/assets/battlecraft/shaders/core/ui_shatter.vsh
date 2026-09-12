#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 stagePoint;
out vec4 shardTint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    stagePoint = UV0;
    shardTint = Color;
}
