#version 150

// Unlike the vanilla textured shaders this one keeps faint pixels (they cut everything below 10% opacity,
// which turns a soft glow into a hard-edged disc), and in fog the light fades out instead of turning grey.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float fogDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.a *= 1.0 - smoothstep(FogStart, FogEnd, fogDistance);
    fragColor = color;
}
