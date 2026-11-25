#version 150

// The flame on a figure's outline (flame.fsh). The normal is not a normal: its x carries how tall the curtain is,
// in blocks of noise (as the square root of a sixteenth of it, to fit the byte the normal is packed into and still
// keep small tongues exact), so that the noise keeps the same size up the curtain whether it is a tongue on a hand or
// a pillar of flame five blocks tall. Aura.flameVertex writes it.

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float fogDistance;
out vec2 texCoord0;
out vec4 vertexColor;
out float noiseHeight;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    fogDistance = FogShape == 0 ? length(Position) : max(length(Position.xz), abs(Position.y));
    texCoord0 = UV0;
    vertexColor = Color;
    noiseHeight = max(16.0 * Normal.x * Normal.x, 0.02);
}
