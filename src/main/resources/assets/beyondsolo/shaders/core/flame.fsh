#version 150

// Flame standing on a figure's outline (client.fx.Aura): a curtain whose v runs from its foot (0) to its top (1) and
// whose u runs along the outline, in blocks of noise (flame.vsh brings how tall it is in the same blocks). Two layers of tiling noise (the texture's red and green) rise through it at their
// own speeds, and the noise has to beat a threshold that grows with height: the curtain is whole at its foot and tears
// into tongues as it climbs, the way fire is drawn in the series. The vertex colour is the flame's tint: a dark tint
// makes black flame, an ink body that burns only along its edges; a bright one makes flame of light. The vertex alpha
// is the flame's strength, and a weak flame is a short one: the caller shapes the curtain into separate tongues by
// raising and lowering it along the curtain, and lets it die away at the curtain's ends.
// Drawn with premultiplied alpha, like the skins of skill effects (energy.fsh).

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform float GameTime;

in float fogDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in float noiseHeight;

out vec4 fragColor;

// How far into the fog a fragment is. With no fog (a screen: the start is pushed out of reach and the end left where
// the world had it) nothing is fogged, where smoothstep alone would be undefined and hide everything.
float fog() {
    return fogDistance <= FogStart ? 0.0 : smoothstep(FogStart, max(FogEnd, FogStart + 0.001), fogDistance);
}

void main() {
    // GameTime is the fraction of the day: 1200 seconds. The speeds times 1200 are whole numbers, so the noise does
    // not jump when the day wraps round.
    float seconds = GameTime * 1200.0;
    float strength = vertexColor.a * ColorModulator.a * (1.0 - fog());
    if (strength <= 0.004) discard;
    vec2 uv = texCoord0;
    // up the curtain in blocks of noise, so the tongues keep their size however tall the curtain is
    float up = uv.y * noiseHeight;
    // the noise is warped by a slower noise, so the tongues lick sideways as they climb
    float warp = texture(Sampler0, vec2(uv.x * 0.32 + 0.13, up * 0.50 - seconds * 0.40)).g;
    float n1 = texture(Sampler0, vec2(uv.x * 0.50 + (warp - 0.5) * 0.50, up * 0.58 - seconds * 0.55)).r;
    float n2 = texture(Sampler0, vec2(uv.x * 1.10 + 0.31, up * 1.17 - seconds * 1.15)).g;
    float n = n1 * 0.7 + n2 * 0.3;
    // whole at the foot, nothing left at the top
    float threshold = 0.14 + 0.86 * uv.y / max(strength, 0.05);
    float body = smoothstep(threshold, threshold + 0.09, n) * (1.0 - smoothstep(0.8, 1.0, uv.y));
    if (body <= 0.003) discard;
    // the edge of each tongue: where the noise only just clears the threshold
    float rim = body * (1.0 - smoothstep(threshold + 0.03, threshold + 0.22, n));
    vec3 tint = vertexColor.rgb * ColorModulator.rgb;
    float bright = max(max(tint.r, tint.g), tint.b);
    vec3 hue = tint / max(bright, 0.04);
    // a dark flame hides what is behind it, thinning to smoke toward its tips; a flame of light mostly shines
    float cover = body * mix(1.0, 0.55, smoothstep(0.2, 0.9, bright)) * (1.0 - 0.4 * uv.y);
    // burning along its edges and toward its tips, a little at its foot. A black flame's tint, up to a tenth bright,
    // also says how hot it burns: a darker one burns low (in daylight, where its light would only wash out)
    float hot = bright >= 0.2 ? 1.0 : mix(0.4, 1.0, smoothstep(0.015, 0.1, bright));
    float heat = (rim * 0.80 + body * (0.10 * (1.0 - uv.y) + 0.30 * uv.y)) * hot;
    vec3 light = mix(hue, vec3(1.0), smoothstep(0.60, 1.0, heat)) * heat * 1.45;
    fragColor = vec4(tint * cover + light, cover);
}
