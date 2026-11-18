#version 150

// One shader for every System UI shape: rounded boxes, rings/arcs and SDF icons,
// each with an optional border, outer glow and moving sheen. Edges are anti-aliased.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;

// The shape's settings, the same in all four corners (see shape.vsh for what each one holds).
// rect.w is the mode alone here. grad.xy: gradient direction scaled so dot(local, grad.xy) + 0.5 runs 0..1;
// grad.z > 0.5 (ring only): the gradient runs around the ring instead.
flat in vec4 rect;
flat in vec4 grad;
flat in vec4 params;
flat in vec4 extra;
flat in vec4 fillA;
flat in vec4 fillB;
flat in vec4 borderA;
flat in vec4 borderB;
flat in vec4 glowColor;

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
    int mode = int(rect.w + 0.5);

    if (mode == 3) {
        // Transparent in the middle, fillA towards the edges.
        float r = length(local / rect.xy);
        float v = smoothstep(extra.x, extra.y, r);
        vec4 c = vec4(fillA.rgb, fillA.a * v) * vertexColor * ColorModulator;
        if (c.a < 0.003) {
            discard;
        }
        fragColor = c;
        return;
    }
    if (mode == 4) {
        // Soft rays of light fanning out from the centre.
        float r = length(local) / rect.x;
        float ang = atan(local.y, local.x) / TAU;
        float n = max(extra.z, 1.0);
        float a1 = sin((ang + extra.y) * TAU * n) * 0.5 + 0.5;
        float a2 = sin((ang - extra.y * 1.7) * TAU * (floor(n * 0.5) + 1.0) + 1.3) * 0.5 + 0.5;
        float rays = pow(a1, 3.0) * 0.65 + pow(a2, 5.0) * 0.55;
        float fall = pow(clamp(1.0 - r, 0.0, 1.0), 1.6) * smoothstep(0.0, 0.18, r);
        vec4 c = mix(fillA, fillB, clamp(r, 0.0, 1.0));
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
    float gradT = clamp(dot(local, grad.xy) + 0.5, 0.0, 1.0);

    if (mode == 1) {
        float r = length(local);
        float halfT = params.x * 0.5;
        float aaR = max(fwidth(r), 0.0001);
        // angle in turns, 0 at the start of the arc, clockwise
        float t = fract(atan(local.x, -local.y) / TAU - extra.y);
        float aaT = aaR / (TAU * max(r, 0.5));
        if (extra.x < 0.9995) {
            // Arc with round ends: turn the point so the middle of the arc points up, then measure to the arc.
            float mid = (extra.y + extra.x * 0.5) * TAU;
            vec2 m = vec2(sin(mid), -cos(mid));
            vec2 n = vec2(cos(mid), sin(mid));
            vec2 p = vec2(abs(dot(local, n)), dot(local, m));
            float a = extra.x * 3.14159265;
            vec2 sc = vec2(sin(a), cos(a));
            d = ((sc.y * p.x > sc.x * p.y) ? length(p - sc * rect.x) : abs(r - rect.x)) - halfT;
        } else {
            d = abs(r - rect.x) - halfT;
        }
        if (extra.z > 0.5) {
            float dt = fract(t * extra.z);
            float aaD = aaT * extra.z;
            float dash = clamp(0.5 + (extra.w - dt) / aaD, 0.0, 1.0) * clamp(0.5 + dt / aaD, 0.0, 1.0)
                    + clamp(0.5 + (dt - 1.0) / aaD, 0.0, 1.0);
            mask *= clamp(dash, 0.0, 1.0);
        }
        if (grad.z > 0.5) {
            gradT = extra.x > 0.001 ? clamp(t > extra.x + (1.0 - extra.x) * 0.5 ? 0.0 : t / extra.x, 0.0, 1.0) : 0.0;
        }
    } else if (mode == 2) {
        vec2 uv = local / (2.0 * rect.xy) + 0.5;
        d = (0.5 - texture(Sampler0, uv).r) * rect.z;
    } else {
        float rad = min(rect.z, min(rect.x, rect.y));
        vec2 q = abs(local) - rect.xy + vec2(rad);
        d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - rad;
    }

    float aa = max(fwidth(d), 0.0001);
    float inside = clamp(0.5 - d / aa, 0.0, 1.0);

    vec4 col = mix(fillA, fillB, gradT);
    if (mode != 1 && params.x > 0.0) {
        float border = clamp(0.5 + (d + params.x) / aa, 0.0, 1.0);
        vec4 borderColor = mix(borderA, borderB, clamp(local.y / (2.0 * rect.y) + 0.5, 0.0, 1.0));
        col = over(vec4(borderColor.rgb, borderColor.a * border), col);
    }
    if (params.z >= 0.0 && params.w > 0.0) {
        float u = (local.x + local.y * 0.6) / (2.0 * rect.x + 1.2 * rect.y) + 0.5;
        float band = exp(-pow((u - params.z) / 0.09, 2.0));
        col.rgb += vec3(params.w * band);
    }

    vec4 shape = vec4(col.rgb, col.a * inside * mask);
    float g = 0.0;
    // An icon texture only knows distances up to its border, so its glow cannot reach further than that.
    float glowSize = mode == 2 ? min(params.y, rect.z * 0.46) : params.y;
    if (glowSize > 0.0) {
        float falloff = clamp(1.0 - max(d, 0.0) / glowSize, 0.0, 1.0);
        g = glowColor.a * falloff * falloff * mask * (1.0 - inside);
    }
    vec4 result = over(shape, vec4(glowColor.rgb, g)) * vertexColor * ColorModulator;
    if (result.a < 0.003) {
        discard;
    }
    fragColor = result;
}
