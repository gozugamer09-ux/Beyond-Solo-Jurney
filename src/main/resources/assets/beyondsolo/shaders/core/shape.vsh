#version 150

// Every shape carries its own settings in its four corners, so many shapes can be drawn in one go
// (see Shapes and ShapeFormat in client/gfx).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
// x: half width, y: half height, z: corner radius (box) / full value range of the texture in GUI pixels (icon)
// w: mode (0 = rounded box, 1 = ring, 2 = SDF icon texture, 3 = vignette, 4 = light rays) + 8 * gradient
//    (0 = none, 1 = top to bottom, 2 = left to right, 3 = around the ring)
in vec4 Rect;
// x: border width (box, icon) or ring thickness, y: glow size, z: sheen position 0..1 (< 0 = off), w: sheen strength
in vec4 Params;
// Ring: x: progress 0..1, y: rotation in turns, z: dash count (0 = solid), w: dash fill 0..1
// Vignette: x: inner radius, y: outer radius (both as a fraction of the half size)
// Rays: y: rotation in turns, z: ray count
in vec4 Extra;
in vec4 FillA;
in vec4 FillB;
in vec4 BorderA;
in vec4 BorderB;
in vec4 GlowColor;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 local;
out vec4 vertexColor;
flat out vec4 rect;
flat out vec4 grad;
flat out vec4 params;
flat out vec4 extra;
flat out vec4 fillA;
flat out vec4 fillB;
flat out vec4 borderA;
flat out vec4 borderB;
flat out vec4 glowColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    local = UV0;
    vertexColor = Color;

    int code = int(Rect.w + 0.5);
    int mode = code - (code / 8) * 8;
    int gradient = code / 8;
    rect = vec4(Rect.xyz, float(mode));
    // gradient direction scaled so dot(local, grad.xy) + 0.5 runs 0..1 across the shape (a ring's line included)
    float extent = mode == 1 ? Params.x * 0.5 : 0.0;
    grad = vec4(0.0);
    if (gradient == 1) {
        grad.y = 1.0 / (2.0 * (Rect.y + extent));
    } else if (gradient == 2) {
        grad.x = 1.0 / (2.0 * (Rect.x + extent));
    } else if (gradient == 3) {
        grad.z = 1.0;
    }
    params = Params;
    extra = Extra;
    fillA = FillA;
    fillB = FillB;
    borderA = BorderA;
    borderB = BorderB;
    glowColor = GlowColor;
}
