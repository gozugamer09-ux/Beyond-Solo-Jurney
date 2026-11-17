#!/usr/bin/env python3
"""
Skins painted on the model itself, for the hounds and the wyrm (used by gen_enemy_assets.py).

paint_skins.py gives every cube a material and fills each face with that material's pattern; that is fine for armour
and robes but an animal painted that way has no face, no markings and one colour all over. Here the painter is
handed, for every texel of every face, where that texel sits on the creature (in the model's own units, before any
bone turns), so a coat can be designed like a real one:

- colour regions that run over the whole body whatever cubes it is built from (dark back, mid flanks, pale belly,
  a mask on the face, socks on the legs) with jagged fur or scale-shaped borders,
- features put where they belong (eyes with pupils, nose, lips, fangs cut out of the texture, claws, ear linings),
- glowing marks that run from the back over the edge and down the flank, glow-mask pixels with their own shading
  (the game draws those full-bright, so their light and dark have to be in the colours),
- shading that follows the body (lit upper flank, dark under the belly, shadows under a ruff or a brow), strong
  light-to-dark ramps with the shadows pushed cool and the highlights warm,
- cut-out shapes (fur fringes, teeth, wing membranes) through the texture's alpha.

Faces are laid out the way the game maps them (see preview_model.py, which draws the result without the game).
A painter records layers of (colour ramp, tone, weight); the layers are then coloured once per rank or affinity, so
every variant of a creature has exactly the same pattern in its own colours.
"""
import math
import os

import numpy as np
from PIL import Image

FACES = ("north", "south", "east", "west", "up", "down")
M32 = np.uint64(0xFFFFFFFF)


# --------------------------------------------------------------------------------------------- steady randomness

def hash01(seed, *coords):
    """A steady random number in 0..1 for every integer coordinate (arrays broadcast together)."""
    shape = np.broadcast_shapes(*[np.shape(c) for c in coords])
    h = np.full(shape, (seed * 0x9E3779B1 + 0x7F4A7C15) & 0xFFFFFFFF, dtype=np.uint64)
    for c in coords:
        c = (np.asarray(c).astype(np.int64) & 0xFFFFFFFF).astype(np.uint64)
        h = (h ^ ((c + np.uint64(0x9E3779B9) + ((h << np.uint64(6)) & M32) + (h >> np.uint64(2))) & M32)) & M32
        h = (h * np.uint64(0x85EBCA6B)) & M32
        h ^= h >> np.uint64(13)
        h = (h * np.uint64(0xC2B2AE35)) & M32
        h ^= h >> np.uint64(16)
    return (h & np.uint64(0xFFFFFF)).astype(np.float64) / float(0xFFFFFF)


def _fade(t):
    return t * t * (3 - 2 * t)


def vnoise2(x, y, seed=0):
    """Smooth noise in 0..1 over the plane (one bump per unit)."""
    x, y = np.asarray(x, float), np.asarray(y, float)
    xi, yi = np.floor(x), np.floor(y)
    fx, fy = _fade(x - xi), _fade(y - yi)
    xi, yi = xi.astype(np.int64), yi.astype(np.int64)
    a, b = hash01(seed, xi, yi), hash01(seed, xi + 1, yi)
    c, d = hash01(seed, xi, yi + 1), hash01(seed, xi + 1, yi + 1)
    return (a + (b - a) * fx) * (1 - fy) + (c + (d - c) * fx) * fy


def vnoise3(x, y, z, seed=0):
    x, y, z = np.broadcast_arrays(np.asarray(x, float), np.asarray(y, float), np.asarray(z, float))
    xi, yi, zi = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = _fade(x - xi), _fade(y - yi), _fade(z - zi)
    xi, yi, zi = xi.astype(np.int64), yi.astype(np.int64), zi.astype(np.int64)
    out = 0
    for dz in (0, 1):
        plane = 0
        for dy in (0, 1):
            a, b = hash01(seed, xi, yi + dy, zi + dz), hash01(seed, xi + 1, yi + dy, zi + dz)
            plane = plane + (a + (b - a) * fx) * (fy if dy else 1 - fy)
        out = out + plane * (fz if dz else 1 - fz)
    return out


def fbm2(x, y, seed=0, octaves=3):
    out, amp, total = 0, 1.0, 0.0
    for o in range(octaves):
        out = out + vnoise2(np.asarray(x) * 2 ** o, np.asarray(y) * 2 ** o, seed + 31 * o) * amp
        total += amp
        amp *= 0.5
    return out / total


def fbm3(x, y, z, seed=0, octaves=3):
    out, amp, total = 0, 1.0, 0.0
    for o in range(octaves):
        k = 2 ** o
        out = out + vnoise3(np.asarray(x) * k, np.asarray(y) * k, np.asarray(z) * k, seed + 31 * o) * amp
        total += amp
        amp *= 0.5
    return out / total


def sstep(a, b, x):
    t = np.clip((np.asarray(x, float) - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def jag(coord, seed, period=1.6, lean=1.3):
    """A fur-edge profile in 0..1 along coord: teeth of uneven height that rise fast and trail off."""
    i = np.floor(np.asarray(coord, float) / period)
    f = coord / period - i
    amp = 0.35 + 0.65 * hash01(seed, i.astype(np.int64))
    return amp * np.clip(1 - f, 0, 1) ** lean


def mix(a, b, k):
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


def mul(c, k):
    return tuple(min(255.0, v * k) for v in c)


def cool(c, k):
    """A darker step of a colour, pushed towards blue as shadows are."""
    return (min(255.0, c[0] * k * 0.90), min(255.0, c[1] * k * 0.96), min(255.0, c[2] * k * 1.12))


def warm(c, k):
    """A lighter step of a colour, pushed towards yellow as highlights are."""
    w = (255, 250, 236)
    return tuple(c[i] + (w[i] - c[i]) * k for i in range(3))


def seg_dist(px, py, ax, ay, bx, by):
    """Distance of every point to the segment a-b and how far along it (0..1) the nearest point is."""
    dx, dy = bx - ax, by - ay
    t = np.clip(((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy), 0, 1)
    return np.hypot(px - (ax + t * dx), py - (ay + t * dy)), t


# --------------------------------------------------------------------------------------------- faces and layers

def face_dims(size, face):
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


class Face:
    """One face of one cube with, per texel, where it sits on the model (X, Y, Z in the model's design units)."""

    def __init__(self, cube, bone, face, w, h, unit):
        self.cube, self.bone, self.face, self.w, self.h = cube, bone, face, w, h
        self.tag = cube.get("_tag") or ""
        self.mat = cube.get("_mat")
        self.o = [v / unit for v in cube["origin"]]
        self.s = [v / unit for v in cube["size"]]
        ox, oy, oz = self.o
        sx, sy, sz = self.s
        U, V = np.meshgrid((np.arange(w) + 0.5) / w, (np.arange(h) + 0.5) / h)
        self.U, self.V = U, V
        one = np.ones_like(U)
        if face == "north":
            X, Y, Z = ox + U * sx, oy + sy - V * sy, oz * one
        elif face == "south":
            X, Y, Z = ox + sx - U * sx, oy + sy - V * sy, (oz + sz) * one
        elif face == "west":
            X, Y, Z = (ox + sx) * one, oy + sy - V * sy, oz + U * sz
        elif face == "east":
            X, Y, Z = ox * one, oy + sy - V * sy, oz + sz - U * sz
        elif face == "up":
            X, Y, Z = ox + U * sx, (oy + sy) * one, oz + sz - V * sz
        else:
            X, Y, Z = ox + U * sx, oy * one, oz + V * sz
        self.X, self.Y, self.Z = X, Y, Z
        self.fw, self.fh = face_dims(self.s, face)
        self.P, self.Q = U * self.fw, V * self.fh          # across and down the face, in units
        self.cx, self.cy, self.cz = ox + sx / 2, oy + sy / 2, oz + sz / 2
        self.top, self.bottom = oy + sy, oy
        self.texel = self.fw / w                            # one texel in units
        self.side = face in ("east", "west")
        self.wall = face in ("north", "south", "east", "west")
        # the face that looks away from the body's middle (for limbs and other parts off the centre line)
        self.outer = (face == "west" and self.cx > 0) or (face == "east" and self.cx < 0)
        self.inner = (face == "east" and self.cx > 0) or (face == "west" and self.cx < 0)


class Paint:
    """The layers of one face: (ramp, tone, weight), coloured later once per variant."""

    def __init__(self, face):
        self.shape = (face.h, face.w)
        self.layers = []
        self.alpha = np.ones(self.shape, bool)

    def _a(self, v):
        return np.broadcast_to(np.asarray(v, float), self.shape)

    def fill(self, ramp, tone=0.5):
        self.layers = [(ramp, self._a(tone), None, False)]

    def over(self, ramp, tone, weight=1.0, glow=False):
        w = np.clip(self._a(weight), 0, 1)
        if w.any():
            self.layers.append((ramp, self._a(tone), w, glow))

    def glow(self, ramp, tone, weight=1.0):
        """Light: full-bright where the weight reaches a half, a tint of the colour around it."""
        self.over(ramp, tone, weight, True)

    def shade(self, delta, mask=None):
        """Lighter or darker for everything painted so far (not the light)."""
        d = self._a(delta) if mask is None else self._a(delta) * self._a(mask)
        self.layers = [(r, t if g else t + d, w, g) for r, t, w, g in self.layers]

    def cut(self, keep):
        self.alpha = self.alpha & np.broadcast_to(keep, self.shape)

    def render(self, pal, levels):
        rgb = np.zeros(self.shape + (3,))
        em = np.zeros(self.shape, bool)
        for ramp, tone, w, glow in self.layers:
            col = ramp_colour(pal[ramp], tone, levels.get(ramp, levels.get("", 0)))
            if w is None:
                rgb, em = col.copy(), np.zeros(self.shape, bool)
            elif glow:
                solid = w >= 0.5
                wk = np.where(solid, 1.0, w * 1.5)[..., None]
                rgb = rgb * (1 - wk) + col * wk
                em |= solid
            else:
                rgb = rgb * (1 - w[..., None]) + col * w[..., None]
                em &= ~(w >= 0.5)
        return np.clip(rgb, 0, 255), em, self.alpha


def ramp_colour(anchors, tone, levels=0):
    """A colour ramp (dark to light anchors) at tone 0..1; levels > 0 gives that many flat steps, as pixel art has."""
    a = np.asarray(anchors, float)
    t = np.clip(tone, 0, 1)
    if levels:
        t = np.round(t * (levels - 1)) / (levels - 1)
    pos = t * (len(a) - 1)
    i = np.clip(np.floor(pos).astype(int), 0, len(a) - 2)
    k = (pos - i)[..., None]
    return a[i] * (1 - k) + a[i + 1] * k


# --------------------------------------------------------------------------------------------- patterns

def fur(s, t, seed, length=2.6, width=1.7):
    """Layered locks of fur. s runs along the hair and t across it (units). Returns a tone offset: each lock is
    lighter at its tip, casts a shadow on the lock below, and is made of finer strands."""
    s, t = np.asarray(s, float), np.asarray(t, float)
    # the rows wander, so the locks never line up like a weave
    s = s + (vnoise2(t * 0.36 + 3.1, s * 0.21 + 7.7, seed + 5) - 0.5) * length * 1.1
    t = t + (vnoise2(t * 0.27 + 11.3, s * 0.33 + 2.9, seed + 6) - 0.5) * width * 0.9
    r = np.floor(s / length)
    b = s / length - r

    def lock(rr, bb):
        off = hash01(seed, rr.astype(np.int64)) * width
        col = np.floor((t + off) / width)
        a = ((t + off) / width - col) * 2 - 1
        j = hash01(seed + 1, rr.astype(np.int64), col.astype(np.int64))
        reach = (1.12 + 0.55 * j) * (1 - 0.78 * np.abs(a) ** 1.7)     # how far down the lock reaches at this strand
        return a, j, reach, col

    a1, j1, e1, c1 = lock(r - 1, b + 1)        # the lock rooted one row up lies on top
    a0, j0, e0, c0 = lock(r, b)
    top = (b + 1) < e1
    a = np.where(top, a1, a0)
    j = np.where(top, j1, j0)
    k = np.where(top, (b + 1) / np.maximum(e1, 1e-3), b / np.maximum(e0, 1e-3))
    c = np.where(top, c1, c0)
    tone = (j - 0.5) * 0.20 + (np.clip(k, 0, 1) - 0.5) * 0.30
    gap = (b + 1) - e1
    tone = np.where(~top & (gap < 0.30), tone - 0.30 * (1 - gap / 0.30), tone)     # the shadow under a lock's tip
    tone = np.where(top & (gap > -0.16), tone + 0.10, tone)                          # the lit tip itself
    tone = tone + (vnoise2(t * 2.4 + c * 5.7, s * 0.30, seed + 2) - 0.5) * 0.18     # strands
    tone = tone - 0.12 * np.clip((-a - 0.5) * 2, 0, 1)                               # a parting on one side
    return tone


def scales(p, q, size, seed, squash=0.78, point=0.62):
    """Overlapping scales: rows run along p and overlap towards +q. Returns (tone, row, col, random per scale,
    p and q of the scale's middle): lit crown, dark rim, the shadow of the row above."""
    p, q = np.asarray(p, float), np.asarray(q, float)
    h = size * squash
    row = np.floor(q / h)

    def cell(rr):
        off = (np.mod(rr, 2)) * 0.5 * size
        col = np.floor((p + off) / size)
        u = ((p + off) / size - col) * 2 - 1
        v = (q - rr * h) / h
        return col, u, v, off

    c1, u1, v1, o1 = cell(row - 1)
    lim1 = 1 + point * (1 - np.abs(u1) ** 2.2)
    top = v1 < lim1
    c0, u0, v0, o0 = cell(row)
    col = np.where(top, c1, c0)
    u = np.where(top, u1, u0)
    v = np.where(top, v1, v0)
    rr = np.where(top, row - 1, row)
    off = np.where(top, o1, o0)
    lim = 1 + point * (1 - np.abs(u) ** 2.2)
    rnd = hash01(seed, rr.astype(np.int64), col.astype(np.int64))
    tone = (rnd - 0.5) * 0.22
    tone = tone + 0.26 * np.exp(-(((u + 0.25) * 1.5) ** 2 + ((v - 0.55) * 1.7) ** 2))        # the lit crown
    tone = tone - 0.34 * sstep(lim - 0.34, lim, v)                                            # its dark lower rim
    below = v1 - lim1
    tone = np.where(~top & (below < 0.34), tone - 0.30 * (1 - below / 0.34), tone)           # shadow of the row above
    tone = tone - 0.10 * np.clip(np.abs(u) - 0.72, 0, 1) * 3                                  # sides fall away
    return tone, rr, col, rnd, (col + 0.5) * size - off, (rr + 0.7) * h


# --------------------------------------------------------------------------------------------- atlas

def _pack(rects, width):
    """Shelf packing: rects [(w, h)] -> positions, total height."""
    order = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
    pos = [None] * len(rects)
    x = y = shelf = 0
    for i in order:
        w, h = rects[i]
        if x + w > width:
            x, y = 0, y + shelf
            shelf = 0
        pos[i] = (x, y)
        x += w
        shelf = max(shelf, h)
    return pos, y + shelf


SIZES = ((128, 128), (256, 128), (256, 256), (512, 256), (512, 512), (1024, 512), (1024, 768), (1024, 1024), (2048, 1024), (2048, 2048))

# name -> dict(painter, palette, density, unit, levels); filled in below and by gen_enemy_assets (unit)
PAINTERS = {}


def paint(name, bones, deferred, tex_dir, unit, max_atlas=2048):
    """Gives every painted face its place on the atlas and writes every variant's texture and glow mask.
    Returns (atlas width, atlas height, texels per model unit)."""
    spec = PAINTERS[name]
    cubes = [(b, c) for b in bones for c in b.get("cubes", []) if "_mat" in c]
    density = spec["density"]
    while True:
        rects, keys = [], []
        for ci, (b, c) in enumerate(cubes):
            for f in FACES:
                a, d = face_dims(c["size"], f)
                if f in c.get("_skip", ()) or a <= 0 or d <= 0:
                    continue
                rects.append((max(1, int(round(a * density))), max(1, int(round(d * density)))))
                keys.append((ci, f))
        fit = None
        for w, h in SIZES:
            if w > max_atlas or max(r[0] for r in rects) > w:
                continue
            pos, used = _pack(rects, w)
            if used <= h:
                fit = (w, h, pos)
                break
        if fit or density <= 1.0:
            break
        density -= 0.25
    aw, ah, pos = fit

    for b, c in cubes:
        c["uv"] = {}
    faces = []
    for (ci, f), (w, h), (x, y) in zip(keys, rects, pos):
        b, c = cubes[ci]
        c["uv"][f] = {"uv": [x, y], "uv_size": [w, h]}
        face = Face(c, b["name"], f, w, h, unit)
        p = Paint(face)
        spec["painter"](face, p)
        faces.append((p, x, y, w, h))

    for vi, (suffix, *args) in enumerate(deferred.variants):
        pal = spec["palette"](deferred.make_palette(np.random.default_rng(deferred.seed + vi), *args))
        img = np.zeros((ah, aw, 4))
        glow = np.zeros((ah, aw, 4))
        for p, x, y, w, h in faces:
            rgb, em, alpha = p.render(pal, spec.get("levels", {}))
            img[y:y + h, x:x + w, :3] = rgb
            img[y:y + h, x:x + w, 3] = np.where(alpha, 255, 0)
            em = em & alpha
            g = np.zeros((h, w, 4))
            g[em, :3] = rgb[em]
            g[em, 3] = 255
            glow[y:y + h, x:x + w] = g
        Image.fromarray(img.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}.png"))
        Image.fromarray(glow.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}_glowmask.png"))
    for b, c in cubes:
        for k in ("_mat", "_tag", "_skip", "_part"):
            c.pop(k, None)
    return aw, ah, density


# ============================================================================================= the hounds

def hound_palette(base):
    hide, glow = base["hide"], base["glow"]
    dark = mul(hide, 0.34)
    mid = mul(hide, 1.12)
    pale = mix(hide, (242, 244, 252), 0.78)
    return {
        "back": [cool(dark, 0.2), cool(dark, 0.58), dark, mix(dark, hide, 0.5), mix(hide, (214, 222, 240), 0.25)],
        "flank": [cool(mid, 0.3), cool(mid, 0.62), mid, warm(mid, 0.26), warm(mid, 0.58)],
        "belly": [cool(pale, 0.38), cool(pale, 0.64), cool(pale, 0.87), warm(pale, 0.35), (255, 255, 255)],
        "black": [(4, 4, 8), (16, 16, 24), (38, 40, 52), (78, 82, 100), (140, 146, 168)],
        "flesh": [(26, 14, 22), (56, 32, 42), (92, 58, 66), (136, 96, 100), (188, 148, 146)],
        "tongue": [(70, 16, 30), (128, 34, 48), (184, 62, 74), (222, 106, 110), (246, 168, 164)],
        "mouth": [(18, 4, 10), (52, 10, 20), (96, 22, 32), (150, 44, 52), (200, 84, 84)],
        "ivory": [(96, 80, 66), (164, 148, 122), (218, 206, 178), (244, 238, 220), (255, 255, 250)],
        "glow": [mul(glow, 0.34), mul(glow, 0.72), glow, mix(glow, (255, 255, 255), 0.5), mix(glow, (255, 255, 255), 0.88)],
        "crystal": [mul(glow, 0.16), mul(glow, 0.42), mul(glow, 0.8), mix(glow, (255, 255, 255), 0.22), mix(glow, (255, 255, 255), 0.62),
                    (255, 255, 255)],
    }


# the veins of light that run from the crystals over the back and down the flank:
# z at the spine, length, width at the root, and which box they are on (its top y and half width)
HOUND_VEINS = (
    (-9.6, 11.5, 1.25, 26.0, 6.0), (-5.4, 13.0, 1.35, 26.0, 6.0), (-1.4, 10.5, 1.1, 26.0, 6.0),
    (3.8, 9.5, 1.05, 25.5, 5.0), (7.6, 8.0, 0.95, 25.5, 5.0), (11.0, 5.5, 0.75, 25.5, 5.0),
)


def _light(p, body, core, halo, bright=1.0):
    p.glow("glow", 0.4, np.clip(halo, 0, 1) * 0.36 * bright)
    p.glow("glow", 0.6, body)
    p.glow("glow", 0.97, core)


def _vein(p, f, top, half, veins):
    """Light that runs from the spine over the edge of the back and down the flank, tapering to a point."""
    if f.face == "up":
        s = np.abs(f.X)
    elif f.side:
        s = half + (top - f.Y)
    else:
        return
    core = np.zeros(f.X.shape, bool)
    body = np.zeros(f.X.shape, bool)
    halo = np.zeros(f.X.shape)
    for zc, length, w0, vt, vh in veins:
        if abs(vt - top) > 0.01 or abs(vh - half) > 0.01:
            continue
        k = np.clip(1 - s / length, 0, 1)
        width = w0 * k ** 0.75 * (s > 1.5)
        path = zc + 0.42 * s + 0.55 * np.sin(s * 0.85 + zc * 1.7)
        d = np.abs(f.Z - path)
        # a side branch from half way down
        start = length * 0.42
        path2 = zc + 0.42 * s - 0.6 * (s - start) + 0.55 * math.sin(start * 0.85 + zc * 1.7)
        d2 = np.abs(f.Z - path2)
        w2 = 0.6 * w0 * np.clip(1 - (s - start) / (length * 0.32), 0, 1) * (s > start)
        body |= ((d < width / 2) & (width > 0.12)) | ((d2 < w2 / 2) & (w2 > 0.12))
        core |= (d < width * 0.22) & (width > 0.34)
        halo = np.maximum(halo, np.clip(1 - (d - width / 2) / 1.0, 0, 1) * (width > 0.12) * (d >= width / 2))
    _light(p, body, core, halo)


def _slash(p, f, a, b, w):
    """One tapered glowing mark from a to b in the face's own (z, y) or (x, y)."""
    px = f.Z if f.side else f.X
    d, t = seg_dist(px, f.Y, a[0], a[1], b[0], b[1])
    width = w * np.sin(np.clip(t, 0, 1) * math.pi) ** 0.6
    _light(p, (d < width / 2) & (width > 0.1), (d < width * 0.22) & (width > 0.3), np.clip(1 - (d - width / 2) / 0.9, 0, 1) * (d >= width / 2))


def _eye(p, f, a, b, ca, cb, ra, rb, slant, flip=1.0):
    """An almond eye on the plane (a, b), centred (ca, cb): dark liner, glowing iris, slit pupil, a glint."""
    ea, eb = (a - ca) * flip, b - cb
    c, s = math.cos(slant), math.sin(slant)
    xa, xb = ea * c + eb * s, -ea * s + eb * c
    r = (np.abs(xa) / ra) ** 1.8 + (np.abs(xb) / rb) ** 1.8
    p.over("black", 0.06, r < 2.1)
    p.glow("glow", 0.5 + 0.47 * np.clip(1 - r, 0, 1) ** 0.7, r < 1.0)
    p.over("black", 0.0, (np.abs(xa) < max(ra * 0.14, f.texel * 0.55)) & (np.abs(xb) < rb * 0.86))
    p.glow("glow", 1.0, (np.abs(xa + ra * 0.38) < f.texel * 0.75) & (np.abs(xb - rb * 0.3) < f.texel * 0.75))


def _fur_base(p, f, back, belly, s, t, seed, length=2.6, width=1.7, base=0.5):
    tone = base + fur(s, t, seed, length, width)
    p.fill("flank", tone + 0.02)
    p.over("back", tone + 0.02, back)
    p.over("belly", tone + 0.06, belly)
    return tone


def paint_hound(f, p):
    tag, face, X, Y, Z = f.tag, f.face, f.X, f.Y, f.Z
    ax = np.abs(X)
    none = np.zeros(X.shape, bool)
    full = np.ones(X.shape, bool)
    round_ = 0.10 * (1 - (2 * f.U - 1) ** 2) - 0.06           # a limb's faces fall away at their edges

    # ------------------------------------------------------------------ trunk
    if tag in ("chest", "rear", "ruff"):
        half, top = f.s[0] / 2, f.top
        long = tag == "ruff"
        if f.side:
            yb = 21.4 + 0.9 * np.sin(Z * 0.33 + 0.6) + 1.5 * jag(Z, 11, 1.7)
            yl = 17.9 + 0.5 * np.sin(Z * 0.4 + 2.0) - 1.3 * jag(Z + 0.8, 12, 1.5)
            back, belly = Y > yb, Y < yl
            if long:
                back, belly = Y > yb + 0.6, Y < yl + 2.2 + 0.5 * (Z + 13.5)
            s, t = Z * 0.88 - Y * 0.47, Z * 0.47 + Y * 0.88
        elif face == "up":
            back, belly = full, none
            s, t = Z + 0.4 * ax, X
        elif face == "down":
            back, belly = none, full
            s, t = Z, X
        elif face == "north":
            # the pale bib: narrow at the throat, the whole chest at the bottom
            edge = 2.0 + 0.36 * (26.5 - Y) + 1.0 * jag(Y, 13, 1.6)
            back, belly = (ax > edge + 2.6), ax < edge
            s, t = -Y + 0.3 * ax, X
        else:
            back, belly = Y > 23.5 + jag(X, 14, 1.4), Y < 18.5 - jag(X, 15, 1.4)
            s, t = -Y, X
        _fur_base(p, f, back, belly, s, t, 21 if long else 20, 3.3 if long else 2.6, 1.9 if long else 1.7)
        # the body's own light: a lit upper flank, dark towards the belly and under the mane
        if f.wall:
            p.shade(0.10 * sstep(19.0, 25.0, Y) - 0.18 * sstep(17.5, 14.5, Y))
            p.shade(0.12, Y > top - 1.2 * f.texel)                     # light catching the edge of the back
        if face == "up":
            p.shade(-0.14, ax < 1.3)                                  # the dorsal stripe
            p.shade(0.08 * sstep(2.0, half, ax))
        if face == "down":
            p.shade(-0.12)
        if tag == "chest" and f.side:
            p.shade(-0.28 * sstep(-6.4, -8.4, Z))                     # the mane's shadow
        if tag in ("chest", "rear"):
            _vein(p, f, top, half, HOUND_VEINS)
            if face == "up":
                for cz, r in ((-10.4, 2.6), (-4.6, 2.1), (2.2, 1.8)):   # scorched fur around the crystals' roots
                    p.shade(-0.24 * sstep(r, r * 0.4, np.hypot(X, Z - cz)))
        if long:
            # the mane's ragged ends: behind, and hanging under the chest
            ring = X if face == "up" else (Y if f.side else None)
            if ring is not None:
                p.cut(Z < f.o[2] + f.s[2] - 0.4 - 2.2 * jag(ring + 0.4, 16, 1.5, 1.0))
            if f.wall:
                p.cut(Y > f.bottom + 0.2 + 1.5 * jag(Z if f.side else X, 17, 1.3, 1.0))
        return

    if tag in ("fringe", "brush", "feather"):
        if tag == "fringe":
            edge = np.where(Z < 1.0, 14.5, 16.0)                       # the body's underside above this sheet
            depth = edge - Y
            reach = 0.5 + 2.1 * jag(Z, 18, 1.25, 1.0) * (0.55 + 0.45 * sstep(12.5, 2, Z))
        elif tag == "brush":
            edge = np.where(Z < 17.0, 22.3, np.where(Z < 24.2, 21.7, 22.4))
            depth = edge - Y
            reach = (0.4 + 1.7 * jag(Z, 19, 1.1, 1.0)) * (1 - 0.8 * sstep(21.5, 26.5, Z)) * sstep(12.5, 15.0, Z)
        else:
            edge = f.o[2] + 0.4                                        # the back of the leg
            depth = Z - edge
            reach = (0.3 + 2.0 * jag(-Y, 23, 1.4, 1.0)) * sstep(f.bottom, f.bottom + 2.2, Y)
        tone = 0.5 + fur(-Y if tag != "feather" else Z - Y, Z if tag != "feather" else Z + Y, 24, 2.0, 1.1)
        k = np.clip(depth / np.maximum(reach, 0.2), 0, 1)
        p.fill("flank", tone - 0.1)
        if tag == "brush":
            p.over("belly", tone - 0.12 + 0.2 * k, k > 0.55)          # only the very tips are pale
        else:
            p.over("belly", tone + 0.06 + 0.12 * k, k > 0.18)         # pale towards the tips
        p.cut((depth < reach) & (depth > -0.6))
        return

    # ------------------------------------------------------------------ head
    if tag == "skull":
        if face == "up":
            _fur_base(p, f, full, none, Z, X, 30, 1.6, 1.1)
            p.shade(-0.12, ax < 1.0)
            d = ax / 0.9 + np.abs(Z + 17.4) / 2.4                      # the mark on the brow
            _light(p, d < 1, d < 0.45, 1.6 - d)
        elif face == "down":
            _fur_base(p, f, none, full, Z, X, 31, 1.6, 1.1)
            p.shade(-0.16)
        elif f.side:
            back = Y > 26.2 - 0.10 * (Z + 21) + 0.5 * jag(Z, 32, 1.2)
            belly = Y < 23.4 - 0.34 * (Z + 21) - 0.7 * jag(Z, 33, 1.1)
            _fur_base(p, f, back, belly, Z * 0.94 - Y * 0.34, Z * 0.34 + Y * 0.94, 34, 1.6, 1.1)
            p.shade(0.08 * sstep(22, 27, Y))
            d, t = seg_dist(Z, Y, -18.5, 25.75, -15.4, 27.0)           # a dark line back from the corner of the eye
            p.over("black", 0.14, d < 0.36 * (1 - t * 0.6))
            _eye(p, f, Z, Y, -19.75, 25.45, 1.25, 0.62, 0.2)
        elif face == "north":
            back = (ax < 0.9) | (Y > 27.0)
            belly = (ax > 2.5) & (Y < 24.4 - 0.5 * jag(X, 35, 1.0))
            _fur_base(p, f, back, belly, -Y, X, 36, 1.4, 1.0)
            p.shade(-0.24 * sstep(25.7, 26.7, Y))                      # under the brows
            for k in (-1, 1):
                _eye(p, f, X, Y, k * 2.7, 25.5, 1.45, 0.64, 0.26, k)
        else:
            _fur_base(p, f, full, none, -Y, X, 37, 1.6, 1.1)
        return

    if tag == "snout":
        if face == "up":
            _fur_base(p, f, ax < 0.9, ax > 1.9, Z, X, 40, 1.2, 0.8)
            p.shade(0.06)
            p.over("black", 0.2, Z < -27.2)
        elif f.side:
            belly = Y < 22.3 + 0.6 * jag(Z, 41, 1.0) - 0.5 * sstep(-24.0, -20.6, Z)
            back = Y > 24.0 - 0.3 * jag(Z, 56, 0.9)
            _fur_base(p, f, back, belly, Z, Y, 42, 1.2, 0.8)
            p.shade(0.06 * sstep(21, 24.5, Y) - 0.12 * sstep(-23.5, -20.6, Z))
            p.over("black", 0.12, Y < 20.45)                             # the lip
            p.over("black", 0.2, (Y < 20.45 + 1.0 * sstep(-22.8, -20.6, Z)) & (Z > -22.8))   # ... curling up at the corner
        elif face == "north":
            _fur_base(p, f, (Y > 24.0) & (ax < 1.2), (Y < 23.4) | (ax > 1.9), -Y, X, 43, 1.2, 0.8)
            p.shade(-0.16 * sstep(22.6, 20.0, Y) - 0.10 * sstep(1.4, 2.8, ax))
            p.over("black", 0.12, (ax < 0.2) & (Y < 22.9))            # the cleft under the nose
            p.over("black", 0.12, (Y < 20.45) | ((Y < 20.45 + 0.7 * sstep(1.6, 2.8, ax)) & (ax > 1.6)))
        elif face == "down":
            p.fill("mouth", 0.3 + 0.2 * sstep(-21, -27.5, Z))
            p.over("mouth", 0.55, ((ax % 1.2) < 0.25) & (Z < -22))     # the ridges of the palate
        else:
            _fur_base(p, f, none, none, -Y, X, 44, 1.2, 0.8)
        return

    if tag == "nose":
        tone = 0.3 + (0.3 if face == "up" else 0.0) + 0.12 * (f.V < 0.3)
        p.fill("black", tone)
        if face == "north":
            p.over("black", 0.0, (np.abs(ax - 0.8) < 0.4) & (np.abs(Y - (f.bottom + 0.8)) < 0.4))     # nostrils
            p.over("black", 0.8, (f.V < 0.18) & (ax < 1.0))                                              # wet shine
        return

    if tag == "brow":
        p.fill("back", 0.42 + fur(Z, X, 45, 1.2, 0.8) * 0.7)
        if face == "up":
            p.shade(0.14)
        if face == "north":
            p.shade(0.18 * (f.V < 0.4) - 0.14 * (f.V > 0.7))
        if face == "down":
            p.shade(-0.3)
        return

    if tag == "ear":
        part = f.cube.get("_part")
        p.fill("back", 0.44 + fur(-Y, X + Z, 46, 1.3, 0.8) * 0.8)
        if face == "north" and part != 2:
            inner = (f.U > 0.2) & (f.U < 0.8) & (f.V > (0.3 if part == 1 else 0.0))
            deep = (f.V + (0 if part == 1 else 1)) / 2                 # 0 near the tip .. 1 at the root
            p.over("flesh", 0.5 - 0.34 * deep - 0.08 * (np.abs(f.U - 0.5) < 0.12), inner)
            hair = (f.U - 0.2 if f.cx < 0 else 0.8 - f.U) < 0.16 * jag(Y, 47, 0.6, 1.0)     # hair growing in from the inner edge
            p.over("flank", 0.74, inner & hair)
        if face == "up":
            p.shade(0.14)
        if f.wall:
            p.shade(0.10 * (f.V < 0.2))
        return

    if tag == "cheek":
        tone = 0.56 + fur(Z * 0.9 - Y * 0.44, Z * 0.44 + Y * 0.9, 48, 2.4, 1.2)
        p.fill("belly", tone)
        p.over("flank", tone, Z < f.o[2] + 1.4 + jag(Y, 49, 1.2))
        p.over("back", tone, Y > f.top - 1.2 - jag(Z, 50, 1.2))
        if f.side or face == "up":
            p.cut(Z < f.o[2] + f.s[2] - 0.3 - 2.6 * jag((Y if f.side else X) + 0.3, 51, 1.25, 1.0))
        if f.side:
            p.cut(Y > f.bottom + 0.2 + 1.9 * jag(Z, 52, 1.2, 1.0) * sstep(f.o[2], f.o[2] + 2.5, Z))
        return

    if tag in ("teeth_up", "teeth_low"):
        up = tag == "teeth_up"
        if f.side:
            a, front = Z, f.o[2]
            fangs = [(front + 1.15, 0.85, 1.0)] + [(front + 2.5 + i * 1.05, 0.5, 0.62 - 0.07 * i) for i in range(4)]
        else:
            a = X
            fangs = [(k * (f.s[0] / 2 - 0.62), 0.6, 1.0) for k in (-1, 1)] + [((i - 1.5) * 0.86, 0.4, 0.55) for i in range(4)]
        depth = (f.top - Y) if up else (Y - f.bottom)                   # 0 at the gum
        keep = none
        tone = np.zeros(X.shape)
        for centre, hw, tall in fangs:
            h = f.s[1] * tall
            inside = depth < h * (1 - (np.abs(a - centre) / hw) ** 1.25)
            keep = keep | inside
            tone = np.where(inside, 0.5 + 0.4 * (depth / h) - 0.2 * (np.abs(a - centre) / hw), tone)
        p.fill("ivory", tone)
        p.cut(keep)
        return

    if tag == "jaw":
        if face == "up":
            p.fill("mouth", 0.22)
            tongue = (ax < 1.5) & (Z > f.o[2] + 0.7) & (Z < f.o[2] + 6.8)
            p.over("tongue", 0.6 - 0.2 * (ax / 1.5) ** 2 - 0.2 * (ax < 0.16), tongue)
        elif f.side:
            _fur_base(p, f, none, full, Z, Y, 53, 1.2, 0.8)
            p.shade(-0.12)
            p.over("black", 0.12, Y > f.top - 0.5)
        elif face == "down":
            _fur_base(p, f, none, full, Z, X, 54, 1.2, 0.8)
            p.shade(-0.2)
        else:
            _fur_base(p, f, none, full, -Y, X, 55, 1.2, 0.8)
            p.shade(-0.06)
            p.over("black", 0.12, Y > f.top - 0.5)
        return

    # ------------------------------------------------------------------ tail
    if tag == "tail":
        if face == "up":
            back, belly = full, none
        elif face == "down":
            back, belly = none, full
        else:
            back, belly = Y > f.cy + 0.5 + 0.5 * jag(Z, 60, 1.3), Y < f.cy - 0.7 - 0.5 * jag(Z, 61, 1.3)
        s, t = (Z, X if face in ("up", "down") else Y) if face not in ("north", "south") else (-Y, X)
        tone = _fur_base(p, f, back, belly, s, t, 62, 2.4, 1.3)
        p.over("belly", tone + 0.2, Z > 25.9 - 1.2 * jag(t, 63, 0.9))        # the white tip
        for z0, z1 in ((18.3, 19.1), (21.7, 22.4)):
            ring = (Z > z0) & (Z < z1)
            _light(p, ring, (Z > z0 + 0.25) & (Z < z1 - 0.25), (Z > z0 - 0.7) & (Z < z1 + 0.7) & ~ring)
        if f.wall:
            p.shade(-0.12 * sstep(f.cy, f.bottom, Y))
        return

    # ------------------------------------------------------------------ legs
    if tag in ("shoulder", "thigh", "leg_up", "leg_low", "paw"):
        s, t = -Y, (Z if f.side else X)
        if face in ("up", "down"):
            s, t = Z, X
        if tag == "paw":
            _fur_base(p, f, none, full, s, t, 70, 1.2, 0.8)
            if f.wall:
                p.shade(round_ * 0.6)
            if face == "north":
                p.over("black", 0.2, (np.abs(((X - f.o[0]) / f.s[0] * 3) % 1 - 0.5) > 0.42) & (Y < f.top - 0.5))   # between the toes
            if face == "down":
                p.fill("black", 0.3)
            return
        back, belly = none, none
        if tag == "leg_low":
            belly = Y < 6.6 + 1.8 * jag(t, 71, 1.1)                        # socks
        elif tag in ("shoulder", "thigh") and f.wall:
            back = Y > f.top - 0.24 * f.s[1] - 1.5 * jag(t, 72, 1.5)
            belly = Y < f.bottom + 0.2 * f.s[1] + 2.0 * jag(t + 0.6, 74, 1.4)
        if f.inner:
            back, belly = none, full
        _fur_base(p, f, back, belly, s, t, 73, 2.0 if tag in ("shoulder", "thigh") else 1.6, 1.2)
        if f.wall:
            p.shade(round_)
        if f.inner:
            p.shade(-0.26)
        if face == "south":
            p.shade(-0.14)
        if face == "north":
            p.shade(0.06)
        if tag == "leg_up" and f.wall:
            p.shade(-0.14 * sstep(f.top - 2.5, f.top, Y))                 # under the shoulder
        if tag in ("shoulder", "thigh") and f.outer:
            # two strokes of light across the muscle
            z0, z1, y0, y1 = f.o[2], f.o[2] + f.s[2], f.bottom, f.top
            for i, w in ((0, 1.1), (1, 0.9)):
                _slash(p, f, (z0 + (0.2 + 0.27 * i) * (z1 - z0), y1 - 0.14 * (y1 - y0)),
                       (z0 + (0.56 + 0.27 * i) * (z1 - z0), y0 + (0.26 + 0.1 * i) * (y1 - y0)), w)
        return

    if tag == "claw":
        p.fill("black", 0.36 + 0.3 * (face == "up") + 0.2 * (f.V < 0.35) * f.wall)
        p.over("ivory", 0.7, (Z < f.o[2] + 0.45) & (face != "down"))
        return

    # ------------------------------------------------------------------ crystals (their own light: shaded here)
    if tag == "crystal":
        part = f.cube.get("_part", 0)                 # 0 the shaft, 1 the point
        v = (0.42 + 0.58 * f.V) if part == 0 else 0.42 * f.V          # 0 at the very tip .. 1 at the root
        side_light = {"up": 0.16, "north": 0.08, "west": 0.0, "east": -0.14, "south": -0.2, "down": -0.3}[face]
        tone = 0.72 - 0.56 * v ** 1.3 + side_light
        if f.wall:
            tone = tone + 0.14 * (f.U < 0.16) + 0.08 * (f.U > 0.86)                    # lit edges
            tone = tone + 0.16 * ((f.U + 0.55 * v) % 0.9 < 0.2)                        # a facet running up the shard
            tone = tone - 0.14 * ((f.U - 0.45 * v) % 1.0 > 0.72)
            tone = np.where((np.abs(f.U - 0.3) < 0.07) & (v > 0.15) & (v < 0.7), 0.98, tone)   # the glint
        p.fill("crystal", 0.5)
        p.glow("crystal", tone)
        return

    # anything not named: plain fur
    _fur_base(p, f, none, none, -Y, X + Z, 99)


PAINTERS["boss_alpha"] = dict(painter=paint_hound, palette=hound_palette, density=4.0, levels={"": 12, "glow": 0, "crystal": 9})
PAINTERS["gloomfang"] = dict(painter=paint_hound, palette=hound_palette, density=6.0, levels={"": 12, "glow": 0, "crystal": 9})


# ============================================================================================= the wyrm

def scale_cells(p, q, size, seed, squash=0.78, point=0.62):
    """Overlapping scales, as scales(), with what a painter needs to colour them one by one. Rows run along p and
    every row lies over the next towards +q (the free, rounded ends point to +q). Returns a dict of arrays:
    tone (lit crown, dark end, the shadow a scale casts on the next), seam (0..1, the gap along a scale's free end),
    rnd (a steady random number per scale), cp and cq (the middle of the scale a texel belongs to), v (0 at a
    scale's root .. 1 at its end)."""
    p, q = np.asarray(p, float), np.asarray(q, float)
    h = size * squash
    row = np.floor(q / h)

    def cell(rr):
        off = np.mod(rr, 2) * 0.5 * size
        col = np.floor((p + off) / size)
        u = ((p + off) / size - col) * 2 - 1
        v = (q - rr * h) / h
        return col, u, v, off

    c1, u1, v1, o1 = cell(row - 1)
    lim1 = 1 + point * (1 - np.abs(u1) ** 2.2)
    top = v1 < lim1
    c0, u0, v0, o0 = cell(row)
    col = np.where(top, c1, c0)
    u = np.where(top, u1, u0)
    v = np.where(top, v1, v0)
    rr = np.where(top, row - 1, row)
    off = np.where(top, o1, o0)
    lim = 1 + point * (1 - np.abs(u) ** 2.2)
    rnd = hash01(seed, rr.astype(np.int64), col.astype(np.int64))
    tone = (rnd - 0.5) * 0.20
    tone = tone + 0.24 * np.exp(-(((u + 0.25) * 1.5) ** 2 + ((v / lim - 0.42) * 2.4) ** 2))     # the lit crown
    tone = tone - 0.30 * sstep(lim - 0.36, lim, v)                                              # its dark free end
    below = v1 - lim1
    shadow = ~top & (below < 0.34)
    tone = np.where(shadow, tone - 0.30 * (1 - below / 0.34), tone)                             # the shadow of the row above
    tone = tone - 0.10 * np.clip(np.abs(u) - 0.72, 0, 1) * 3                                    # the sides fall away
    seam = np.maximum(sstep(lim - 0.22, lim, v) * top, np.where(~top, np.clip(1 - below / 0.2, 0, 1), 0.0))
    seam = np.maximum(seam, sstep(lim - 0.2, lim, v) * ~top * 0.0)
    return {"tone": tone, "seam": seam, "rnd": rnd, "cp": (col + 0.5) * size - off, "cq": (rr + 0.75) * h, "v": v / lim, "u": u}


def wyrm_palette(base):
    hide, glow, bone, plate, cloth = base["hide"], base["glow"], base["bone"], base["plate"], base["cloth"]
    red = (hide[0] * 1.02, hide[1] * 0.62, hide[2] * 0.66)                 # the flank: a deep blood red
    ember = mix(hide, (244, 122, 34), 0.62)
    tan = mix(plate, (240, 206, 140), 0.55)
    ash = mix((30, 26, 30), hide, 0.10)
    skin = mix(cloth, (60, 14, 20), 0.35)
    warmth = min(1.0, max(0.0, (glow[0] - glow[2]) / 255.0))
    heart = mix((255, 255, 255), (255, 206, 104), warmth)                  # what the glow turns to where it is hottest
    return {
        "ash": [mul(ash, 0.22), cool(ash, 0.6), ash, mix(ash, (120, 104, 104), 0.5), (156, 140, 136)],
        "red": [cool(red, 0.2), cool(red, 0.5), red, mix(red, ember, 0.45), mix(ember, (255, 214, 150), 0.3)],
        "ember": [cool(red, 0.42), mix(red, ember, 0.45), ember, warm(ember, 0.38), warm(ember, 0.72)],
        "plate": [cool(tan, 0.26), cool(tan, 0.52), mul(tan, 0.86), warm(tan, 0.3), (255, 250, 226)],
        "horn": [(22, 16, 16), (64, 50, 44), mix(bone, (120, 100, 84), 0.6), bone, (255, 250, 236)],
        "black": [(3, 2, 4), (12, 9, 12), (30, 24, 28), (62, 52, 56), (110, 98, 100)],
        "glow": [mul(glow, 0.34), mul(glow, 0.72), glow, mix(glow, (255, 255, 255), 0.5), mix(glow, (255, 255, 255), 0.88)],
        "hot": [mul(glow, 0.3), mul(glow, 0.64), glow, mix(glow, heart, 0.6), mix(heart, (255, 255, 246), 0.55)],
        "mouth": [(20, 4, 6), (64, 10, 12), mix((120, 20, 16), glow, 0.35), mix(glow, (255, 240, 200), 0.3), (255, 250, 230)],
        "skin": [mul(skin, 0.3), mul(skin, 0.62), skin, mix(skin, ember, 0.5), mix(ember, (255, 226, 170), 0.45)],
    }


def _wing_shape(pp, qq, part):
    """The bat-wing outline on a membrane sheet. pp along the arm from the sheet's joint, qq back from the leading
    edge. Returns (inside, 0 at the arm .. 1 at the trailing edge, distance to the nearest finger)."""
    if part == 1:
        th = np.degrees(np.arctan2(qq, np.maximum(pp, 1e-6)))
        r = np.hypot(pp, qq)
        fingers = ((0.0, 30.0), (24.6, 30.5), (48.8, 30.7), (73.4, 28.3), (90.0, 25.2))
        rmax = np.zeros(r.shape)
        for (a0, r0), (a1, r1) in zip(fingers, fingers[1:]):
            s = np.clip((th - a0) / (a1 - a0), 0, 1)
            rmax = np.where((th >= a0) & (th <= a1), (r0 + (r1 - r0) * s) * (1 - 0.22 * np.sin(np.pi * s)), rmax)
        strut = np.full(r.shape, 99.0)
        for a, r0 in fingers[1:-1]:
            d = np.abs(np.sin(np.radians(th - a))) * r
            strut = np.minimum(strut, np.where((r < r0) & (np.abs(th - a) < 60), d, 99.0))
        return (r < rmax) & (pp >= 0) & (qq >= 0), r / np.maximum(rmax, 1e-6), strut, r
    k = np.clip(pp / 12, 0, 1)
    qmax = 19.4 + 5.8 * k - 2.8 * np.sin(np.pi * k)
    return qq < qmax, qq / qmax, np.full(pp.shape, 99.0), qq


def _blade_shape(t, d):
    """The tail blade: t along it from its root, d out from its middle line."""
    half = np.where(t < 2.2, 1.9, np.where(t < 6.0, 1.9 + 6.2 * np.clip((t - 2.2) / 3.8, 0, 1) ** 1.7,
                                           8.1 * np.clip(1 - (t - 6.0) / 9.0, 0, 1) ** 0.8))
    return np.abs(d) < half, np.abs(d) / np.maximum(half, 1e-6), half


def _wyrm_heat(X, Y, Z, low):
    """How hot the hide is (0..1) at a place: the throat and chest burn, the back smoulders in patches.
    low: 0 on the back .. 1 at the belly."""
    n = fbm3(X * 0.15 + 5.3, Y * 0.15 + 9.1, Z * 0.12 + 1.7, 77, 3)
    front = sstep(-2, -20, Z) * (1 - 0.7 * sstep(-38, -46, Z))        # from the chest up the throat, not the face
    return np.clip(n * 0.9 + 0.36 * front * low ** 1.6 + 0.11 * (1 - low) ** 2 - 0.04, 0, 1)


def _wyrm_scales(p, f, a, b, size, seed, zone, heat=None, glow_at=0.60, tone=0.5):
    """Scales over a face: a across the rows, b the way their free ends point. zone(cell) -> (ash, ember, plate)
    weights by where each scale's middle is; the rest is red. The seams of hot scales glow."""
    cells = scale_cells(a, b, size, seed)
    t = tone + cells["tone"]
    ash, ember, plate = zone(cells)
    p.fill("red", t)
    p.over("ember", t + 0.04, ember)
    p.over("ash", t + 0.02, ash)
    if plate is not None:
        p.over("plate", t + 0.06, plate)
    if heat is not None:
        hot = np.clip((heat(cells) - glow_at) / 0.14, 0, 1)
        seam = cells["seam"] * (0.45 + 0.55 * hot) * (hot > 0)
        p.glow("hot", 0.30, np.clip(hot * (0.32 - 0.5 * np.abs(cells["v"] - 0.9)), 0, 0.32))    # a warm tint near the seam
        p.glow("hot", 0.42 + 0.26 * hot + 0.3 * sstep(0.55, 1.0, seam), seam > 0.42)
    return cells


def _scutes(p, f, along, across, half, seed, hot=0.0, length=2.7, mask=None):
    """Belly plates: bands across the body, each lit along its leading edge. hot (0..1): the seams glow, brightest
    along the body's middle line. mask: only there (over what is painted already)."""
    k = np.floor(along / length)
    t = along / length - k
    rnd = hash01(seed, k.astype(np.int64))
    side = np.abs(across) / max(half, 1e-6)
    tone = 0.56 + (rnd - 0.5) * 0.12 + 0.16 * sstep(0.34, 0.0, t) - 0.20 * sstep(0.60, 0.92, t)
    tone = tone - 0.16 * sstep(0.4, 1.0, side) + 0.05 * sstep(0.3, 0.0, side)
    tone = tone + (vnoise2(across * 1.3, along * 0.5, seed + 3) - 0.5) * 0.10
    m = np.ones(along.shape, bool) if mask is None else mask
    if mask is None:
        p.fill("plate", tone)
    else:
        p.over("plate", tone, m)
    seam = (t > 0.90) & m
    p.over("black", 0.2, seam)
    h = np.broadcast_to(np.asarray(hot, float), along.shape) * (0.55 + 0.45 * rnd) * (1 - 0.75 * sstep(0.35, 1.0, side))
    p.glow("hot", 0.34, np.clip(sstep(0.62, 0.9, t) * h * 0.36, 0, 0.36) * m)
    p.glow("hot", 0.46 + 0.5 * h * (t > 0.94), seam & (h > 0.34))
    return t


def _wyrm_fangs(p, f, up, fangs, along):
    depth = (f.top - f.Y) if up else (f.Y - f.bottom)                 # 0 at the gum
    keep = np.zeros(f.X.shape, bool)
    tone = np.zeros(f.X.shape)
    for centre, hw, tall in fangs:
        h = f.s[1] * tall
        inside = depth < h * (1 - (np.abs(along - centre) / hw) ** 1.3)
        keep = keep | inside
        tone = np.where(inside, 0.56 + 0.34 * (depth / h) - 0.24 * (np.abs(along - centre) / hw), tone)
    p.fill("horn", tone)
    p.cut(keep)


def _stripes(z):
    """Dark bands that come down off the back, every few units along the body."""
    k = np.floor((z + 2.0) / 6.4)
    c = (k + 0.5) * 6.4 - 2.0 + (hash01(91, k.astype(np.int64)) - 0.5) * 2.4
    return np.exp(-((z - c) / (1.2 + 0.8 * hash01(92, k.astype(np.int64)))) ** 2)


def paint_wyrm(f, p):
    tag, face, X, Y, Z, part = f.tag, f.face, f.X, f.Y, f.Z, f.cube.get("_part", 0)
    ax = np.abs(X)
    none = np.zeros(X.shape, bool)
    full = np.ones(X.shape, bool)
    span = max(f.top - f.bottom, 1e-6)

    # ------------------------------------------------------------------ the trunk: body, neck, tail
    if tag in ("torso", "hump", "hips", "neck", "tail"):
        plated = tag in ("neck", "tail")              # these carry their own belly plates; the body has a slab of them
        size = {"torso": 2.5, "hump": 2.5, "hips": 2.5, "neck": 2.0, "tail": (2.3, 2.0, 1.7, 1.4)[part] if tag == "tail" else 2.0}[tag]
        hot_tail = sstep(38, 56, Z) * 0.26

        def zone(cells, coord):
            hc = (cells[coord] - f.bottom) / span if coord else None
            return hc

        if f.side:
            hn = (Y - f.bottom) / span

            def zones(cells):
                hc = (cells["cp"] - f.bottom) / span + (cells["rnd"] - 0.5) * 0.14
                ash = hc > 0.80 - 0.46 * _stripes(cells["cq"])
                ember = (hc < 0.40 + 0.08 * np.sin(cells["cq"] * 0.5)) & ~ash
                plate = (hc < 0.2) if plated else None
                return ash, ember, plate

            def heat(cells):
                hc = np.clip((cells["cp"] - f.bottom) / span, 0, 1)
                return _wyrm_heat(np.full(X.shape, f.o[0] if face == "east" else f.o[0] + f.s[0]), cells["cp"], cells["cq"], 1 - hc) + hot_tail

            cells = _wyrm_scales(p, f, Y, Z, size, 300, zones, heat)
            if plated:
                t = Z / 2.7 % 1
                band = hn < 0.2 - 0.07 * (2 * t - 1) ** 4                 # every plate's end is rounded
                tone = 0.5 + 0.12 * sstep(0.34, 0.0, t) - 0.16 * sstep(0.62, 0.92, t)
                p.over("plate", tone - 0.14 * sstep(0.2, 0.0, hn), band)
                seam = band & (t > 0.9)
                p.over("black", 0.26, seam)
                p.over("black", 0.18, (hn < 0.2 + 0.025 - 0.07 * (2 * t - 1) ** 4) & ~band)      # the shadow line over the plates
                throat = sstep(-8, -20, Z) if tag == "neck" else 0.0
                p.glow("hot", 0.5, seam & (np.broadcast_to(throat, X.shape) > 0.5) & (hn < 0.1))
            # the body's own light and shade
            p.shade(0.10 * sstep(0.55, 1.0, hn) - 0.16 * sstep(0.3, 0.0, hn))
            p.shade(0.10, Y > f.top - 1.5 * f.texel)
            if tag == "torso":
                for z0, z1 in ((-14.2, -4.6), (5.2, 16.0)):       # behind and under the haunches
                    p.shade(-0.20 * sstep(z1 + 2.6, z1, Z) * sstep(z0 - 1, z0 + 1, Z) * sstep(23.0, 19.0, Y))
        elif face == "up":
            def zones(cells):
                return full, none, None

            def heat(cells):
                return _wyrm_heat(cells["cp"], np.full(X.shape, f.top), cells["cq"], 0.0) + hot_tail

            _wyrm_scales(p, f, X, Z, size * 1.15, 301, zones, heat, glow_at=0.58)
            p.shade(-0.12 * sstep(1.8, 0.6, ax) + 0.07 * sstep(1.5, f.s[0] / 2, ax))
        elif face == "down":
            _scutes(p, f, Z, X, f.s[0] / 2, 302, sstep(-8, -20, Z) if tag == "neck" else 0.0)
            p.shade(-0.10)
        else:
            hn = (Y - f.bottom) / span

            def zones(cells):
                hc = (-cells["cq"] - f.bottom) / span + (cells["rnd"] - 0.5) * 0.14
                ash = hc > 0.78
                return ash, (hc < 0.40) & ~ash, None

            def heat(cells):
                return _wyrm_heat(cells["cp"], -cells["cq"], np.full(X.shape, f.cz), np.clip(1 - (-cells["cq"] - f.bottom) / span, 0, 1))

            _wyrm_scales(p, f, X, -Y, size, 303, zones, heat)
            p.shade(0.08 * sstep(0.55, 1.0, hn) - 0.16 * sstep(0.3, 0.0, hn) - (0.10 if face == "south" else 0.0))
            if tag == "hump" and face == "north":
                strip = (ax < 4.6 + 0.5 * np.cos((-Y / 2.2 % 1) * 2 * math.pi)) & (Y < 20.5)
                p.shade(-0.2, (ax < 5.6) & (Y < 21.0))
                _scutes(p, f, -Y, X, 4.8, 304, 1.0, length=2.2, mask=strip)
                p.shade(-0.22 * sstep(21.5, 17.5, Y) * (ax < 4.4), strip)                          # the neck's shadow
        return

    if tag in ("belly", "keel"):
        hot = sstep(2, -12, Z)
        if face in ("up", "down"):
            _scutes(p, f, Z, X, f.s[0] / 2, 302, hot)
            p.shade(-0.08)
        elif f.side:
            _scutes(p, f, Z, np.zeros(X.shape), 1.0, 302, hot)
            p.shade(-0.14 * sstep(f.top - 0.2, f.bottom, Y) + 0.06)
        elif face == "north" and tag == "keel":
            strip = ax < 4.6 + 0.5 * np.cos((-Y / 2.2 % 1) * 2 * math.pi)
            _wyrm_scales(p, f, X, -Y, 2.0, 305, lambda cells: (none, full, None))
            p.shade(-0.14)
            p.shade(-0.2, ax < 5.6)
            _scutes(p, f, -Y, X, 4.8, 304, 1.0, length=2.2, mask=strip)
        else:
            _scutes(p, f, -Y * 1.6, X, f.s[0] / 2, 304, 0.0, length=2.2)
        return

    if tag == "spine":
        # dark where it leaves the hide, pale and worn at the point
        v = (0.45 + 0.55 * f.V) if part == 0 else 0.45 * f.V          # 0 at the point .. 1 at the root
        tone = 0.86 - 0.62 * v ** 0.8 + (0.08 if face == "up" else 0.0) - (0.12 if face == "south" else 0.0)
        if f.wall:
            tone = tone + 0.10 * (f.U < 0.26) - 0.10 * (f.U > 0.74) - 0.07 * ((v * 5.0) % 1 > 0.82)
        p.fill("horn", tone)
        if part == 0 and f.wall:
            p.glow("hot", 0.5, (f.V > 0.86) & ((f.U * 3.0) % 1 > 0.35))       # embers where it roots
        return

    # ------------------------------------------------------------------ head
    if tag in ("skull", "snout", "jaw"):
        size = 1.55
        upper = tag != "jaw"

        def lowness(y):
            return np.clip(1 - (y - 18.5) / 7.5, 0, 1)

        if f.side:
            def zones(cells):
                yc = cells["cp"] + (cells["rnd"] - 0.5) * 0.8
                if tag == "jaw":
                    return none, yc > 17.3, yc <= 17.3
                top = 24.2 if tag == "skull" else (22.6 if part == 0 else 21.9)
                ash = yc > top
                return ash, (yc < 20.6) & ~ash, None

            cells = _wyrm_scales(p, f, Y, Z, size, 310, zones)
            if upper:
                p.shade(0.08 * sstep(21, 25, Y) - 0.10 * sstep(20.2, 18.4, Y))
                p.over("black", 0.14, Y < 18.5 + 0.55 + 0.25 * np.sin(Z * 1.9))          # the lip
            else:
                p.shade(-0.06 - 0.05 * (part >= 2))
                if part < 2:
                    p.over("black", 0.14, Y > f.top - 0.5)
            if tag == "skull":
                p.shade(-0.26 * sstep(1.4, 0.0, np.hypot((Z + 43.7) / 1.5, (Y - 23.3) / 1.0) - 1.0) * 0.6)   # the hollow round the eye
                p.shade(-0.14 * sstep(-36.5, -33.2, Z))                                    # under the horn and frill
        elif face == "up":
            if tag == "jaw":
                # the floor of the mouth: it burns at the back of the throat
                glowk = sstep(-56, -40, Z)
                p.fill("mouth", 0.2 + 0.3 * glowk)
                tongue = (ax < 1.9 - 0.8 * sstep(-50, -56.5, Z)) & (Z > -56.5) & (Z < -40)
                p.over("mouth", 0.42 + 0.1 * (ax < 0.25), tongue)
                p.glow("mouth", 0.55 + 0.4 * sstep(-47, -39, Z), (Z > -45 + 2.0 * (ax / 4.7)) & ~tongue)
            else:
                def zones(cells):
                    return full, none, None
                _wyrm_scales(p, f, X, Z, size * (1.3 if tag == "skull" else 1.0), 311, zones,
                             (lambda cells: _wyrm_heat(cells["cp"], np.full(X.shape, 26.0), cells["cq"], 0.0) - 0.06) if tag == "skull" else None)
                p.shade(-0.12 * sstep(1.3, 0.3, ax) + 0.08 * sstep(1.4, f.s[0] / 2, ax))
        elif face == "down":
            if tag == "jaw":
                _scutes(p, f, Z, X, f.s[0] / 2, 312, 0.0, length=2.2)
                p.shade(-0.12)
            elif tag == "snout":
                # the roof of the mouth
                glowk = sstep(-56, -44, Z)
                p.fill("mouth", 0.22 + 0.3 * glowk)
                p.over("mouth", 0.5 + 0.16 * glowk, ((ax % 1.5) < 0.3) & (Z < -46))
                p.glow("mouth", 0.6 + 0.3 * sstep(-50, -46, Z), Z > -49.5 + 1.5 * (ax / 5.0))
            else:
                _scutes(p, f, Z, X, f.s[0] / 2, 312, 0.0, length=2.2)
                p.shade(-0.16)
                p.glow("mouth", 0.75, (Z < -44.5) & (ax < 4.4))                        # the throat, seen through the open jaws
        else:
            def zones(cells):
                yc = -cells["cq"] + (cells["rnd"] - 0.5) * 0.8
                if tag == "jaw":
                    return none, yc > 17.3, yc <= 17.3
                ash = (yc > 22.4) | (np.abs(cells["cp"]) < 1.2)
                return ash, (yc < 20.4) & ~ash, None
            _wyrm_scales(p, f, X, -Y, size, 313, zones)
            if face == "north" and upper:
                p.shade(-0.12 * sstep(21.0, 18.6, Y))
                p.over("black", 0.14, Y < 18.5 + 0.55)
            if face == "north" and tag == "jaw" and part < 2:
                p.over("black", 0.14, Y > f.top - 0.5)
            if face == "south":
                p.shade(-0.18)
        return

    if tag == "nostril":
        p.fill("ash", 0.4 + 0.16 * (face == "up") + 0.14 * (f.V < 0.3) * f.wall)
        if face == "north":
            hole = (np.abs(X - f.cx) < 0.62) & (np.abs(Y - (f.bottom + 0.62)) < 0.45)
            p.over("black", 0.0, hole)
            p.glow("hot", 0.5, hole & (Y < f.bottom + 0.45))                               # a spark deep inside
        if f.outer:
            p.over("black", 0.05, np.hypot(Z - (f.o[2] + 0.9), Y - (f.bottom + 0.6)) < 0.5)
        return

    if tag == "eye":
        p.fill("black", 0.2 + 0.2 * (face == "up"))
        if f.outer:
            p.fill("ash", 0.24 + 0.1 * (f.V < 0.2))
            _eye(p, f, Z, Y, f.cz - 0.1, f.cy - 0.15, 1.7, 0.86, -0.16, 1.0)
        if face == "north":
            core = (np.abs(X - f.cx) < 0.36) & (np.abs(Y - (f.cy - 0.15)) < 0.75)
            p.glow("glow", 0.72, core)
        return

    if tag == "brow":
        t = 0.34 + (vnoise2(Z * 1.3, X * 1.3, 320) - 0.5) * 0.16
        p.fill("ash", t + (0.2 if face == "up" else 0.0))
        if f.wall:
            p.shade(0.2 * (f.V < 0.34) - 0.16 * (f.V > 0.7))
        if face == "down":
            p.shade(-0.3)
        if face == "up":
            cells = scale_cells(X, Z, 1.25, 321)
            p.fill("ash", 0.5 + cells["tone"])
            p.over("horn", 0.34 + 0.2 * f.V, (f.U > 0.84) if f.cx > 0 else (f.U < 0.16))           # the worn outer rim
        return

    if tag == "horn":
        # 0 at the very tip .. 1 where it grows out of the skull
        lengths = {0: (0.54, 1.0), 1: (0.16, 0.54), 2: (0.0, 0.16), 3: (0.42, 1.0), 4: (0.0, 0.42)}[part]
        along = (Z - f.o[2]) / max(f.s[2], 1e-6) if face not in ("north", "south") else (1.0 if face == "south" else 0.0)
        v = lengths[1] - (lengths[1] - lengths[0]) * np.clip(along, 0, 1)
        light = {"up": 0.12, "down": -0.2, "north": 0.0, "south": -0.1}.get(face, 0.04 if f.outer else -0.1)
        tone = 0.9 - 0.7 * v ** 0.75 + light
        tone = tone - 0.09 * ((v * 9.0) % 1 > 0.74)                     # growth rings
        if face not in ("north", "south"):
            tone = tone + 0.10 * (f.V < 0.25) - 0.08 * (f.V > 0.75)
        p.fill("horn", tone)
        return

    if tag in ("teeth_up", "teeth_low"):
        up = tag == "teeth_up"
        if f.side:
            z0, z1 = f.o[2], f.o[2] + f.s[2]
            n = 5 if part == 0 else 3
            fangs = [(z0 + (i + 0.5) * (z1 - z0) / n, (z1 - z0) / n * 0.5, (1.0 if (i % 2 == 0) else 0.62)) for i in range(n)]
            if part == 1:
                fangs[0] = (fangs[0][0], fangs[0][1], 1.0)
            _wyrm_fangs(p, f, up, fangs, Z)
        else:
            half = f.s[0] / 2
            if part == 1:
                fangs = [(k * (half - 0.7), 0.7, 1.0) for k in (-1, 1)] + [((i - 1.5) * 1.15, 0.5, 0.6) for i in range(4)]
            else:
                fangs = [(k * (half - 0.5), 0.5, 0.9) for k in (-1, 1)]
            _wyrm_fangs(p, f, up, fangs, X)
        return

    # ------------------------------------------------------------------ tail blade
    if tag == "blade":
        t = Z - f.o[2]
        d = (Y - 18.2) if part == 0 else X
        inside, k, half = _blade_shape(t, d)
        tone = 0.30 + 0.5 * k ** 2.2                                    # dark along the rib, pale and sharp at the edge
        tone = tone - 0.10 * ((np.abs(d) * 0.9 + t * 0.5) % 1.7 < 0.3)  # ridges
        p.fill("horn", tone)
        p.over("ash", 0.3 + 0.2 * (np.abs(d) < 0.5), np.abs(d) < 1.9 * (1 - t / 15.0) + 0.3)     # the tail running into it
        p.cut(inside)
        return

    # ------------------------------------------------------------------ legs
    if tag in ("upper", "lower", "foot"):
        size = 1.7 if tag != "foot" else 1.3
        a = Z if f.side else X
        if tag == "foot":
            if face == "down":
                p.fill("black", 0.22)
                return

            def zones(cells):
                return full, none, None
            if face == "up":
                _wyrm_scales(p, f, X, -Z, size, 330, zones)
                toes = np.abs(((X - f.o[0]) / f.s[0] * 3) % 1 - 0.5) > 0.40
                p.over("black", 0.1, toes & (Z < f.o[2] + 4.2))
                p.shade(0.08)
            else:
                _wyrm_scales(p, f, a, -Y, size, 331, zones)
                p.shade(-0.1 * sstep(f.top, f.bottom, Y))
                if face == "north":
                    p.over("black", 0.1, np.abs(((X - f.o[0]) / f.s[0] * 3) % 1 - 0.5) > 0.40)
            return
        # the limb: red over the muscle, charred on top, embers down towards the foot, soot on the shank
        top, bottom = f.top, f.bottom

        def zones(cells):
            yc = -cells["cq"] + (cells["rnd"] - 0.5) * 1.2
            if tag == "upper":
                ash = yc > top - 0.2 * (top - bottom)
                ember = (yc < bottom + 0.26 * (top - bottom)) & ~ash
            else:
                ash = yc < bottom + 0.42 * (top - bottom)
                ember = (yc > bottom + 0.62 * (top - bottom)) & ~ash
            return ash, ember, None

        if face in ("up", "down"):
            _wyrm_scales(p, f, X, Z, size, 332, lambda cells: (full, none, None))
            p.shade(0.06 if face == "up" else -0.2)
            return
        _wyrm_scales(p, f, a, -Y, size, 333, zones)
        p.shade(0.10 * (1 - (2 * f.U - 1) ** 2) - 0.06)                                     # round
        if f.inner:
            p.shade(-0.22)
        if face == "south":
            p.shade(-0.12)
        if tag == "upper":
            p.shade(0.08 * sstep(bottom + 3, top - 2, Y))
            if f.outer:
                # the bulge of the muscle
                d = np.hypot((Z - f.cz) / (f.s[2] * 0.42), (Y - (f.cy + 0.6)) / (f.s[1] * 0.40))
                p.shade(0.12 * sstep(1.0, 0.2, d) - 0.10 * sstep(0.85, 1.25, d))
        else:
            p.shade(-0.10 * sstep(top - 2.5, top, Y))
        return

    if tag == "claw":
        tip = part == 1
        tone = (0.78 if tip else 0.52) + 0.14 * (face == "up") - 0.2 * (face == "down")
        if face in ("up", "down") or f.side:
            along = (Z - f.o[2]) / max(f.s[2], 1e-6)
            tone = tone - 0.24 * (along if part != 2 else 1 - along)                         # darker towards the toe
        p.fill("horn", tone + 0.1 * (f.V < 0.3) * f.wall)
        return

    # ------------------------------------------------------------------ wings
    if tag in ("wing_arm", "wing_claw"):
        if tag == "wing_claw":
            along = (Z - f.o[2]) / max(f.s[2], 1e-6) if face not in ("north", "south") else 0.0
            p.fill("horn", 0.84 - 0.5 * along + 0.1 * (face == "up"))
            return
        along = np.abs(X - (f.o[0] if f.cx > 0 else f.o[0] + f.s[0])) / max(f.s[0], 1e-6)     # 0 at the inner joint .. 1 at the far end
        t = 0.36 + (vnoise2(X * 0.9, (Y + Z) * 2.2, 340) - 0.5) * 0.14
        t = t + {"up": 0.16, "down": -0.18, "north": 0.06, "south": -0.08}.get(face, 0.0)
        joint = sstep(0.14, 0.0, along) + sstep(0.86, 1.0, along)
        p.fill("ash", t + 0.10 * joint - 0.05 * ((along * (5 if part == 0 else 11)) % 1 > 0.8))
        if part == 1:
            p.over("horn", 0.36 + 0.4 * sstep(0.7, 1.0, along), along > 0.72)               # the finger ends in horn
        return

    if tag == "membrane":
        pp = ax - (10.0 if part == 0 else 22.0)
        qq = Z + 8.0
        inside, k, strut, r = _wing_shape(pp, qq, part)
        vein = np.abs(fbm2(pp * 0.34 + 3.0, qq * 0.34 + (7.0 if part else 1.0), 350, 2) - 0.5)
        tone = 0.22 + 0.42 * k ** 1.4                                                         # thin and bright towards the edge
        tone = tone + (vnoise2(pp * 0.8, qq * 0.8, 351) - 0.5) * 0.10
        tone = tone - 0.14 * sstep(0.035, 0.012, vein) * sstep(0.2, 0.5, k)                   # veins
        tone = tone - 0.16 * sstep(0.955, 1.0, k)                                             # the hem
        p.fill("skin", tone)
        w = 0.5 + 0.4 * (1 - np.clip(r / 30.0, 0, 1))
        p.over("ash", 0.3 + 0.16 * sstep(w, 0.0, strut), strut < w)                            # the fingers
        p.over("ash", 0.32, qq < 0.9)                                                         # along the arm
        tear = (fbm2(pp * 0.5 + 9.0, qq * 0.5 + 2.0, 352, 2) > 0.70) & (k > 0.72) & (strut > 1.2)
        p.cut(inside & ~tear)
        return

    p.fill("red", 0.5)


PAINTERS["boss_wyrm"] = dict(painter=paint_wyrm, palette=wyrm_palette, density=4.0,
                             levels={"": 12, "glow": 0, "hot": 0, "mouth": 10})
