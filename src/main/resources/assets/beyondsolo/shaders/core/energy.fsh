#version 150

// The skins of skill effects. The texture holds two things: heat in its red channel (light, which runs from the
// colour to white where it is hottest) and cover in its alpha (the body, which hides what is behind it). Drawn with
// premultiplied alpha, so one picture is both a solid shape and the light on top of it: a blade or a beam reads as a
// thing even against a bright sky, and a dark tint makes dark energy with bright edges (the colour of the light is
// the tint's hue at full brightness). Fog takes the whole effect away rather than greying it.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float fogDistance;
in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    float strength = vertexColor.a * ColorModulator.a * (1.0 - smoothstep(FogStart, FogEnd, fogDistance));
    vec3 tint = vertexColor.rgb * ColorModulator.rgb;
    vec3 hue = tint / max(max(tint.r, tint.g), max(tint.b, 0.04));
    float heat = tex.r * strength;
    float cover = clamp(tex.a * strength, 0.0, 1.0);
    vec3 light = mix(hue, vec3(1.0), smoothstep(0.42, 0.92, heat)) * heat * 1.5;
    fragColor = vec4(tint * cover + light, cover);
}
