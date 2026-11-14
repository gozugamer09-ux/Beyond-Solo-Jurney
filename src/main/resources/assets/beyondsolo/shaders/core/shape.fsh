#version 150

// One shader for every System UI shape: rounded boxes, rings/arcs and SDF icons,
// each with an optional border, outer glow and moving sheen. Edges are anti-aliased.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

// x: half width, y: half height
// z: corner radius (box) / full value range of the texture in GUI pixels (icon)
// w: mode (0 = rounded box, 1 = ring, 2 = SDF icon texture, 3 = vignette, 4 = light rays)
uniform vec4 Rect;
// Fill gradient from FillA to FillB. Grad.xy: gradient direction scaled so dot(local, Grad.xy) + 0.5 runs 0..1.
// Grad.z > 0.5 (ring only): gradient runs around the ring instead.
uniform vec4 FillA;
uniform vec4 FillB;
uniform vec4 Grad;
// Border colour at the top (A) and bottom (B) of the shape.
uniform vec4 BorderA;
uniform vec4 BorderB;
uniform vec4 GlowColor;
// x: border width (box, icon) or ring thickness, y: glow size, z: sheen position 0..1 (< 0 = off), w: sheen strength
uniform vec4 Params;
// Ring: x: progress 0..1, y: rotation in turns, z: dash count (0 = solid), w: dash fill 0..1
// Vignette: x: inner radius, y: outer radius (both as a fraction of the half size)
// Rays: y: rotation in turns, z: ray count
uniform vec4 Extra;

in vec2 local;
in vec4 vertexColor;

out vec4 fragColor;

const float TAU = 6.28318530718;

vec4 over(vec4 top, vec4 bottom) {
    float a = top.a + bottom.a * (1.0 - top.a);
    if (a <= 0.0001) {
        return vec4(0.0);
    }
    vec3 rgb = (top.rgb * top.a + bottom.rgb * bottom.a * (1.0 - top.a)) / a;
    return vec4(rgb, a);
}

void main() {
    int mode = int(Rect.w + 0.5);

    if (mode == 3) {
        // Transparent in the middle, FillA towards the edges.
        float r = length(local / Rect.xy);
        float v = smoothstep(Extra.x, Extra.y, r);
        vec4 c = vec4(FillA.rgb, FillA.a * v) * vertexColor * ColorModulator;
        if (c.a < 0.003) {
            discard;
        }
        fragColor = c;
        return;
    }
    if (mode == 4) {
        // Soft rays of light fanning out from the centre.
        float r = length(local) / Rect.x;
        float ang = atan(local.y, local.x) / TAU;
        float n = max(Extra.z, 1.0);
        float a1 = sin((ang + Extra.y) * TAU * n) * 0.5 + 0.5;
        float a2 = sin((ang - Extra.y * 1.7) * TAU * (floor(n * 0.5) + 1.0) + 1.3) * 0.5 + 0.5;
        float rays = pow(a1, 3.0) * 0.65 + pow(a2, 5.0) * 0.55;
        float fall = pow(clamp(1.0 - r, 0.0, 1.0), 1.6) * smoothstep(0.0, 0.18, r);
        vec4 c = mix(FillA, FillB, clamp(r, 0.0, 1.0));
        c.a *= rays * fall;
        c *= vertexColor * ColorModulator;
        if (c.a < 0.003) {
            discard;
        }
        fragColor = c;
        return;
    }

    float d;
    float mask = 1.0;
    float gradT = clamp(dot(local, Grad.xy) + 0.5, 0.0, 1.0);

    if (mode == 1) {
        float r = length(local);
        float halfT = Params.x * 0.5;
        float aaR = max(fwidth(r), 0.0001);
        // angle in turns, 0 at the start of the arc, clockwise
        float t = fract(atan(local.x, -local.y) / TAU - Extra.y);
        float aaT = aaR / (TAU * max(r, 0.5));
        if (Extra.x < 0.9995) {
            // Arc with round ends: turn the point so the middle of the arc points up, then measure to the arc.
            float mid = (Extra.y + Extra.x * 0.5) * TAU;
            vec2 m = vec2(sin(mid), -cos(mid));
            vec2 n = vec2(cos(mid), sin(mid));
            vec2 p = vec2(abs(dot(local, n)), dot(local, m));
            float a = Extra.x * 3.14159265;
            vec2 sc = vec2(sin(a), cos(a));
            d = ((sc.y * p.x > sc.x * p.y) ? length(p - sc * Rect.x) : abs(r - Rect.x)) - halfT;
        } else {
            d = abs(r - Rect.x) - halfT;
        }
        if (Extra.z > 0.5) {
            float dt = fract(t * Extra.z);
            float aaD = aaT * Extra.z;
            float dash = clamp(0.5 + (Extra.w - dt) / aaD, 0.0, 1.0) * clamp(0.5 + dt / aaD, 0.0, 1.0)
                    + clamp(0.5 + (dt - 1.0) / aaD, 0.0, 1.0);
            mask *= clamp(dash, 0.0, 1.0);
        }
        if (Grad.z > 0.5) {
            gradT = Extra.x > 0.001 ? clamp(t > Extra.x + (1.0 - Extra.x) * 0.5 ? 0.0 : t / Extra.x, 0.0, 1.0) : 0.0;
        }
    } else if (mode == 2) {
        vec2 uv = local / (2.0 * Rect.xy) + 0.5;
        d = (0.5 - texture(Sampler0, uv).r) * Rect.z;
    } else {
        float rad = min(Rect.z, min(Rect.x, Rect.y));
        vec2 q = abs(local) - Rect.xy + vec2(rad);
        d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
    }

    float aa = max(fwidth(d), 0.0001);
    float inside = clamp(0.5 - d / aa, 0.0, 1.0);

    vec4 col = mix(FillA, FillB, gradT);
    if (mode != 1 && Params.x > 0.0) {
        float border = clamp(0.5 + (d + Params.x) / aa, 0.0, 1.0);
        vec4 borderColor = mix(BorderA, BorderB, clamp(local.y / (2.0 * Rect.y) + 0.5, 0.0, 1.0));
        col = over(vec4(borderColor.rgb, borderColor.a * border), col);
    }
    if (Params.z >= 0.0 && Params.w > 0.0) {
        float u = (local.x + local.y * 0.6) / (2.0 * Rect.x + 1.2 * Rect.y) + 0.5;
        float band = exp(-pow((u - Params.z) / 0.09, 2.0));
        col.rgb += vec3(Params.w * band);
    }

    vec4 shape = vec4(col.rgb, col.a * inside * mask);
    float g = 0.0;
    // An icon texture only knows distances up to its border, so its glow cannot reach further than that.
    float glowSize = mode == 2 ? min(Params.y, Rect.z * 0.46) : Params.y;
    if (glowSize > 0.0) {
        float falloff = clamp(1.0 - max(d, 0.0) / glowSize, 0.0, 1.0);
        g = GlowColor.a * falloff * falloff * mask * (1.0 - inside);
    }
    vec4 result = over(shape, vec4(GlowColor.rgb, g)) * vertexColor * ColorModulator;
    if (result.a < 0.003) {
        discard;
    }
    fragColor = result;
}
