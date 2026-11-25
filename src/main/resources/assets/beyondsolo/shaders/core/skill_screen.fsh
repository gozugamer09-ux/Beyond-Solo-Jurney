#version 150

// The screen's answer to a skill's heavy blows and ultimates (see SkillScreen): shock ripples, colour fringes, a zoom
// blur, the ink frame, manhwa focus lines and a vignette, over a copy of what the world drew this frame.

uniform sampler2D Sampler0;
uniform vec2 ScreenSize;
// xy: the blow on the screen (0..1, y up), z: how far the ring has run (a share of the screen's height), w: strength
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
// how far apart red and blue are pulled at the edges
uniform float Fringe;
// xy: where it rushes toward, z: how far
uniform vec3 Zoom;
// x: how much of the ink frame shows, y: 1 while it is inverted
uniform vec2 Ink;
uniform vec3 InkTint;
// x: how much of the focus lines show, y: time in seconds, z: the clear middle (a share of the screen's height)
uniform vec3 Lines;
// x: how dark the edges close in, yzw: their colour
uniform vec4 Vignette;

in vec2 texCoord;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

// how far a ripple pushes this point: out ahead of its ring, back behind it, nothing beyond
vec2 ripple(vec2 uv, vec4 r, float aspect) {
    if (r.w <= 0.0) return vec2(0.0);
    vec2 d = (uv - r.xy) * vec2(aspect, 1.0);
    float dist = length(d);
    float width = 0.05 + r.z * 0.35;
    float x = (dist - r.z) / width;
    if (abs(x) >= 1.0 || dist < 1e-4) return vec2(0.0);
    float push = sin(x * PI) * (1.0 - x * x);
    return (d / dist) * vec2(1.0 / aspect, 1.0) * push * r.w;
}

vec3 sampleAt(vec2 uv) {
    return texture(Sampler0, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    vec2 uv = texCoord;
    float aspect = ScreenSize.x / max(ScreenSize.y, 1.0);
    vec2 push = ripple(uv, Ripple0, aspect) + ripple(uv, Ripple1, aspect) + ripple(uv, Ripple2, aspect) + ripple(uv, Ripple3, aspect);
    vec2 at = uv - push;
    vec2 fromMiddle = uv - vec2(0.5);

    // the picture, its colours pulled apart toward the edges and along a ripple's ring
    float split = Fringe * (0.3 + length(fromMiddle) * 1.6) + length(push) * 0.5;
    vec3 col;
    if (split > 0.0) {
        vec2 dir = normalize(fromMiddle + vec2(1e-5)) * split;
        col = vec3(sampleAt(at + dir).r, sampleAt(at).g, sampleAt(at - dir).b);
    } else {
        col = sampleAt(at);
    }

    // a rush toward the blow
    if (Zoom.z > 0.0) {
        vec3 sum = col;
        float total = 1.0;
        vec2 toward = Zoom.xy - at;
        for (int i = 1; i <= 8; i++) {
            float k = float(i) / 8.0;
            float w = 1.0 - k * 0.85;
            sum += sampleAt(at + toward * k * Zoom.z) * w;
            total += w;
        }
        col = sum / total;
    }

    // the leading edge of a ripple catches the light
    col += vec3(length(push)) * 4.0;

    // the edges close in
    if (Vignette.x > 0.0) {
        float edge = smoothstep(0.25, 0.85, length(fromMiddle * vec2(1.15, 1.0)) * 1.25);
        col = mix(col, Vignette.yzw, clamp(edge * Vignette.x * 1.6, 0.0, 0.92));
    }

    // focus lines: black strokes closing in from the edges, redrawn a dozen times a second as if inked by hand
    if (Lines.x > 0.0) {
        vec2 d = fromMiddle * vec2(aspect, 1.0);
        float r = length(d);
        float turn = (atan(d.y, d.x) + PI) / (2.0 * PI) * 180.0;
        float slot = floor(turn);
        float frame = floor(Lines.y * 12.0);
        float pick = hash(slot + frame * 31.0);
        float inner = Lines.z + pick * 0.3;
        float across = abs(fract(turn) - 0.5) * 2.0;
        // thick at the edge of the screen, thinning to a point as it reaches in
        float reach = clamp((r - inner) / 0.35, 0.0, 1.0);
        float stroke = step(0.55, pick) * step(across, reach * 0.85) * smoothstep(0.0, 0.15, reach);
        col = mix(col, vec3(0.015, 0.01, 0.02), stroke * Lines.x * 0.9);
    }

    // the ink frame: stark black and white (the light side tinted by the element), the first frames inverted
    if (Ink.x > 0.0) {
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        float light = smoothstep(0.30, 0.38, lum);
        vec3 inked = mix(vec3(0.02, 0.015, 0.03), InkTint, light);
        if (Ink.y > 0.5) inked = mix(InkTint, vec3(0.02, 0.015, 0.03), light);
        col = mix(col, inked, Ink.x);
    }

    fragColor = vec4(col, 1.0);
}
