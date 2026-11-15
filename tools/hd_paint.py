"""
Shared painting toolkit for the high-detail (64x64) gear textures (tools/gen_hd_gear.py).

Everything is numpy on RGBA float arrays. The idea:

- `ramp(base)` turns one colour into a nine-step palette, with shadows shifted towards cool violet and highlights
  towards warm cream, so nothing is shaded by plain darkening.
- `shade_mask` gives any shape volume: a height field from the distance to the shape's edge, lit from the upper
  left, plus a material pattern (brushed metal, leather grain, rock, scales, ice facets, cloth folds, wood grain),
  then posterized to the ramp so it still reads as pixel art.
- small decorations (rivets, engraved lines, stitches, gems, glowing runes and cracks) and finishing passes
  (part seams, tinted outline, grade aura and sparkles).
"""
import math

import numpy as np
from PIL import Image


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3)) + (255,)


def scale(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), 255)


COOL = (26, 18, 52)
WARM = (255, 248, 226)


def ramp(base):
    """Anchors at tone -1, -0.5, 0, 0.5, 1."""
    base = tuple(base[:3]) + (255,)
    deep = mix(scale(base, 0.26), COOL, 0.45)
    dark = mix(scale(base, 0.58), COOL, 0.2)
    light = mix(base, WARM, 0.33)
    hi = mix(base, WARM, 0.74)
    return [deep, dark, base, light, hi]


def tone_color(r, x):
    """r: ramp; x in [-1, 1] -> colour (array op friendly for scalars)."""
    x = max(-1.0, min(1.0, x))
    f = (x + 1) * 2
    i = min(3, int(f))
    return mix(r[i], r[i + 1], f - i)


def tone_array(r, x):
    """Vectorized: x array -> (H, W, 3) float colours."""
    anchors = np.array([c[:3] for c in r], float)
    f = (np.clip(x, -1, 1) + 1) * 2
    i = np.clip(np.floor(f).astype(int), 0, 3)
    t = (f - i)[..., None]
    return anchors[i] * (1 - t) + anchors[i + 1] * t


def posterize(x, step=0.25):
    return np.round(x / step) * step


# ------------------------------------------------------------------------------------------------- noise

def value_noise(shape, cell, seed, octaves=2):
    rng = np.random.default_rng(seed)
    h, w = shape
    out = np.zeros(shape)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = max(1, cell // (2 ** o))
        gh, gw = h // c + 2, w // c + 2
        g = rng.random((gh, gw)).astype(np.float32)
        img = Image.fromarray((g * 255).astype(np.uint8)).resize((gw * c, gh * c), Image.BICUBIC)
        out += amp * (np.asarray(img, float)[:h, :w] / 255.0 - 0.5)
        total += amp
        amp *= 0.5
    return out / total * 2  # about [-1, 1]


def hash2(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ------------------------------------------------------------------------------------------------- masks

def grow(m, n=1):
    for _ in range(n):
        g = m.copy()
        g[1:, :] |= m[:-1, :]
        g[:-1, :] |= m[1:, :]
        g[:, 1:] |= m[:, :-1]
        g[:, :-1] |= m[:, 1:]
        m = g
    return m


def shrink(m, n=1):
    return ~grow(~m, n)


def distance_inside(mask, maxd=10):
    d = np.zeros(mask.shape, float)
    cur = mask.copy()
    for i in range(maxd):
        d += cur
        cur = shrink(cur)
        if not cur.any():
            break
    return d


def poly(points, size, ss=4):
    """Polygon (pixel coordinates, y down) rasterized with coverage > 0.5."""
    h, w = size if isinstance(size, tuple) else (size, size)
    ys, xs = np.mgrid[0:h * ss, 0:w * ss]
    px = (xs + 0.5) / ss
    py = (ys + 0.5) / ss
    inside = np.zeros(px.shape, bool)
    j = len(points) - 1
    for i in range(len(points)):
        xi, yi = points[i]
        xj, yj = points[j]
        inside ^= ((yi > py) != (yj > py)) & (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi)
        j = i
    return inside.reshape(h, ss, w, ss).mean(axis=(1, 3)) > 0.5


def ellipse(cx, cy, rx, ry, size):
    h, w = size if isinstance(size, tuple) else (size, size)
    yy, xx = np.mgrid[0:h, 0:w]
    return ((xx + 0.5 - cx) / rx) ** 2 + ((yy + 0.5 - cy) / ry) ** 2 <= 1


def thick_line(p0, p1, width, size):
    h, w = size if isinstance(size, tuple) else (size, size)
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy + 1e-9
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / L2, 0, 1)
    d = np.hypot(xx - (x0 + t * dx), yy - (y0 + t * dy))
    return d <= width / 2


def curve(points, width, size, steps=40):
    """A thick polyline through Catmull-Rom smoothed points."""
    pts = [points[0]] + list(points) + [points[-1]]
    m = np.zeros(size if isinstance(size, tuple) else (size, size), bool)
    prev = None
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for s in range(steps + 1):
            t = s / steps
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            wv = width(i - 1 + t) if callable(width) else width
            if prev is not None:
                m |= thick_line(prev, (x, y), wv, m.shape)
            prev = (x, y)
    return m


# ------------------------------------------------------------------------------------------------- shading

LIGHT = (0.62, 0.78)  # weights for +gx, +gy (light from the upper left)

MATERIALS = {
    # gain (volume), radius (px of rounding), noise amp, noise cell, specular
    "metal": (1.9, 5, 0.10, 6, 0.55),
    "gold": (1.9, 4, 0.08, 5, 0.65),
    "dark_metal": (1.7, 5, 0.10, 6, 0.45),
    "leather": (1.2, 4, 0.22, 3, 0.0),
    "cloth": (0.9, 6, 0.18, 7, 0.0),
    "fur": (1.0, 3, 0.45, 2, 0.0),
    "stone": (1.5, 5, 0.40, 4, 0.0),
    "bone": (1.3, 4, 0.14, 4, 0.15),
    "scale": (1.3, 4, 0.10, 4, 0.25),
    "ice": (1.4, 5, 0.10, 6, 0.8),
    "crystal": (1.4, 5, 0.05, 6, 0.9),
    "wood": (1.1, 3, 0.15, 3, 0.0),
    "obsidian": (1.8, 5, 0.08, 6, 0.7),
}


def height_field(mask, radius):
    d = distance_inside(mask, radius + 2)
    h = np.clip(d / radius, 0, 1)
    h = 1 - (1 - h) ** 2
    # soften
    p = np.pad(h, 1, mode="edge")
    h = (p[:-2, 1:-1] + p[2:, 1:-1] + p[1:-1, :-2] + p[1:-1, 2:] + 2 * h) / 6
    return h


def shade_mask(mask, base, material="metal", seed=1, extra=None, light_bias=0.0, step=0.25):
    """Returns (rgba uint8 HxWx4, tone array). `extra`: a tone offset array added before posterizing."""
    gain, radius, namp, ncell, spec = MATERIALS[material]
    h, w = mask.shape
    if not mask.any():
        return np.zeros((h, w, 4), np.uint8), np.zeros((h, w))
    hf = height_field(mask, radius)
    gy, gx = np.gradient(hf)
    lit = (gx * LIGHT[0] + gy * LIGHT[1]) * gain * 2.2
    ys, xs = np.nonzero(mask)
    y0, y1 = ys.min(), ys.max()
    x0, x1 = xs.min(), xs.max()
    yy, xx = np.mgrid[0:h, 0:w]
    yrel = (yy - y0) / max(1, y1 - y0)
    xrel = (xx - x0) / max(1, x1 - x0)
    x = lit + 0.32 * (0.45 - yrel) + 0.12 * (0.5 - xrel) + light_bias
    if namp:
        n = value_noise((h, w), ncell, seed)
        if material == "metal" or material == "dark_metal" or material == "gold":
            # brushed: stretch the noise horizontally
            n = value_noise((h, w // 3 + 1), ncell, seed)
            n = np.asarray(Image.fromarray(((n + 1) * 127).astype(np.uint8)).resize((w, h), Image.BILINEAR), float) / 127 - 1
        x = x + n * namp
    if spec:
        # a bright reflection band across the upper part and hot spots where the surface faces the light
        band = np.exp(-((yrel - 0.22 + 0.25 * (xrel - 0.5)) / 0.09) ** 2) * spec * 0.6
        hot = np.clip(lit - 0.35, 0, 1) * spec * 1.6
        x = x + band * (hf > 0.35) + hot
    if material == "ice" or material == "crystal":
        x = x + facets(mask, seed) * 0.55
    if material == "scale":
        x = x + scale_pattern(h, w, 4 if h >= 48 else 3) * 0.7
    if material == "stone":
        x = x + (value_noise((h, w), 2, seed + 7, 1) > 0.55) * -0.35
    if material == "wood":
        grain = np.sin(xx * 0.25 + yy * 1.6 + value_noise((h, w), 6, seed) * 4)
        x = x + grain * 0.18
    if material == "cloth":
        folds = np.sin(xx * 0.55 + value_noise((h, w), 8, seed) * 2.5)
        x = x + folds * 0.2
    if material == "fur":
        strands = np.sin(xx * 1.7 + yy * 0.6 + value_noise((h, w), 3, seed) * 5)
        x = x + strands * 0.25
    if extra is not None:
        x = x + extra
    x = posterize(np.clip(x, -1, 1), step)
    col = tone_array(ramp(base), x)
    out = np.zeros((h, w, 4), np.uint8)
    out[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
    out[..., 3] = np.where(mask, 255, 0)
    out[~mask] = 0
    return out, np.where(mask, x, 0)


def facets(mask, seed):
    """Flat facets: each Voronoi cell gets one tilt, like cut crystal or ice."""
    h, w = mask.shape
    rng = np.random.default_rng(seed + 99)
    ys, xs = np.nonzero(mask)
    n = max(4, len(ys) // 70)
    pts = np.stack([rng.uniform(xs.min(), xs.max() + 1, n), rng.uniform(ys.min(), ys.max() + 1, n)], 1)
    tilt = rng.uniform(-1, 1, n)
    yy, xx = np.mgrid[0:h, 0:w]
    d = (xx[..., None] - pts[:, 0]) ** 2 + (yy[..., None] - pts[:, 1]) ** 2
    cell = np.argmin(d, axis=-1)
    srt = np.sort(d, axis=-1)
    edge = (np.sqrt(srt[..., 1]) - np.sqrt(srt[..., 0])) < 1.0
    out = tilt[cell]
    out = np.where(edge, out + 0.6, out)
    return out


def scale_pattern(h, w, r):
    """Overlapping scales pointing down: each one lit at its top, a dark rim along its lower edge."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    out = np.full((h, w), -0.7)
    rows = int(h / r) + 2
    for row in range(-1, rows):
        cy = row * r * 0.9
        xo = (row % 2) * r
        cx = (np.floor((xx + xo) / (2 * r)) + 0.5) * 2 * r - xo
        d = np.hypot(xx - cx, (yy - cy) * 1.15)
        inside = (d < r) & (yy >= cy - 1)
        v = 0.55 - (d / r) * 1.0
        v = np.where(d > r * 0.78, -0.75, v)
        out = np.where(inside, v, out)  # later (lower) rows overlap the ones above
    return out


# ------------------------------------------------------------------------------------------------- composing

class Canvas:
    """Layers painted in order; remembers which layer owns each pixel for seams."""

    def __init__(self, w, h=None):
        h = h or w
        self.a = np.zeros((h, w, 4), np.uint8)
        self.owner = np.full((h, w), -1)
        self.n = 0
        self.w, self.h = w, h

    def layer(self, mask, base, material="metal", seed=None, extra=None, light_bias=0.0):
        seed = self.n * 31 + 7 if seed is None else seed
        img, _ = shade_mask(mask, base, material, seed, extra, light_bias)
        m = img[..., 3] > 0
        self.a[m] = img[m]
        self.owner[m] = self.n
        self.n += 1
        return m

    def paint(self, mask, color):
        self.a[mask] = color
        self.owner[mask] = self.n
        self.n += 1

    def seams(self, amount=0.32):
        """Darkens the pixels of a lower layer where an upper layer meets it: a contact shadow line."""
        o = self.owner
        solid = self.a[..., 3] > 0
        f = np.ones(o.shape)
        for dy, dx in ((-1, 0), (0, -1), (1, 0), (0, 1)):
            nb = np.full(o.shape, -1)
            ys = slice(max(0, -dy), o.shape[0] - max(0, dy))
            yd = slice(max(0, dy), o.shape[0] - max(0, -dy))
            xs = slice(max(0, -dx), o.shape[1] - max(0, dx))
            xd = slice(max(0, dx), o.shape[1] - max(0, -dx))
            nb[ys, xs] = o[yd, xd]
            f = np.where(solid & (nb > o) & (nb >= 0), 1 - amount, f)
        self.a[..., :3] = (self.a[..., :3] * f[..., None]).astype(np.uint8)

    def image(self):
        return Image.fromarray(self.a)


def rivet(a, x, y, base, size=2):
    r = ramp(base)
    x, y = int(x), int(y)
    H, W = a.shape[:2]

    def put(px, py, c):
        if 0 <= px < W and 0 <= py < H and a[py, px, 3] > 0:
            a[py, px] = c
    if size >= 2:
        put(x, y, r[4]); put(x + 1, y, r[3]); put(x, y + 1, r[3]); put(x + 1, y + 1, r[1])
        put(x + 2, y + 1, scale(r[1], 0.7)); put(x + 1, y + 2, scale(r[1], 0.7))
    else:
        put(x, y, r[4]); put(x + 1, y + 1, r[1])


def engrave(a, mask, depth=0.35):
    """An engraved line: dark in the groove, a lit lip below it."""
    m = mask & (a[..., 3] > 0)
    below = np.zeros_like(m)
    below[1:, :] = m[:-1, :]
    below &= ~m & (a[..., 3] > 0)
    a[m, :3] = (a[m, :3] * (1 - depth)).astype(np.uint8)
    a[below, :3] = np.minimum(255, a[below, :3].astype(int) + 28).astype(np.uint8)


def glow_paint(a, mask, color, halo=0.45, halo_px=1, core=True):
    """Glowing marks (runes, molten cracks): the mark in a hot colour, its neighbours tinted."""
    c = np.array(color[:3], float)
    solid = a[..., 3] > 0
    m = mask & solid
    ring = grow(m, halo_px) & ~m & solid
    a[ring, :3] = (a[ring, :3] * (1 - halo) + c * halo).astype(np.uint8)
    hot = np.minimum(255, c * 0.55 + 255 * 0.45) if core else c
    a[m, :3] = c.astype(np.uint8)
    inner = shrink(m, 1) if m.sum() > 6 else np.zeros_like(m)
    a[inner, :3] = hot.astype(np.uint8)


def gem(a, cx, cy, r, color, mask_into=None):
    H, W = a.shape[:2]
    yy, xx = np.mgrid[0:H, 0:W] + 0.5
    du, dv = (xx - cx) / r, (yy - cy) / r
    d = np.hypot(du, dv)
    m = d <= 1
    nz = np.sqrt(np.clip(1 - d * d, 0, 1))
    lam = (-du * 0.55 - dv * 0.6 + nz * 0.6)
    x = (lam - 0.45) * 1.6
    x = x + np.where((d > 0.6) & (dv > 0.1), 0.55, 0)  # light coming through the bottom
    x = posterize(np.clip(x, -1, 1), 0.25)
    col = tone_array(ramp(color), x)
    a[m, :3] = col[m].astype(np.uint8)
    a[m, 3] = 255
    # specular
    sx, sy = int(cx - r * 0.4), int(cy - r * 0.45)
    if 0 <= sx < W and 0 <= sy < H:
        a[sy, sx] = (255, 255, 255, 255)
    # rim (setting)
    rim = grow(m, 1) & ~m
    a[rim] = (*scale(color, 0.25)[:3], 255)
    return m


def stitches(a, path_mask, color, every=3):
    ys, xs = np.nonzero(path_mask)
    for y, x in zip(ys, xs):
        if (x + y) % every == 0 and a[y, x, 3] > 0:
            a[y, x, :3] = color[:3]


def cracks(mask, seed, n=6, length=10, size=None):
    """Random-walk crack lines inside a mask."""
    rng = np.random.default_rng(seed)
    h, w = mask.shape
    out = np.zeros_like(mask)
    ys, xs = np.nonzero(shrink(mask, 2))
    if len(ys) == 0:
        return out
    for _ in range(n):
        i = rng.integers(len(ys))
        y, x = float(ys[i]), float(xs[i])
        ang = rng.uniform(0, 2 * math.pi)
        for _ in range(length):
            yi, xi = int(y), int(x)
            if 0 <= yi < h and 0 <= xi < w and mask[yi, xi]:
                out[yi, xi] = True
            ang += rng.uniform(-0.8, 0.8)
            x += math.cos(ang)
            y += math.sin(ang)
            if rng.random() < 0.12:
                ang += rng.choice([-1.2, 1.2])
    return out


INK = (18, 12, 28)


def outline(a, ink=INK, mix_amount=0.72):
    """1px outline tinted by the colour it touches (darker and inked, never flat black)."""
    solid = a[..., 3] >= 200
    ring = grow(solid) & ~solid & (a[..., 3] < 200)
    acc = np.zeros(a.shape[:2] + (3,), float)
    cnt = np.zeros(a.shape[:2])
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        sh = np.roll(np.roll(a[..., :3].astype(float), dy, 0), dx, 1)
        sm = np.roll(np.roll(solid, dy, 0), dx, 1)
        acc += sh * sm[..., None]
        cnt += sm
    avg = acc / np.maximum(cnt, 1)[..., None]
    col = avg * (1 - mix_amount) * 0.55 + np.array(ink[:3]) * mix_amount
    a[ring, :3] = col[ring].astype(np.uint8)
    a[ring, 3] = 255


def bevel(a, light=0.16, dark=0.24):
    """Crisp rim light on the upper-left edges of the silhouette and a darker lower-right edge."""
    solid = a[..., 3] >= 200
    up = np.zeros_like(solid); up[1:, :] = solid[:-1, :]
    left = np.zeros_like(solid); left[:, 1:] = solid[:, :-1]
    down = np.zeros_like(solid); down[:-1, :] = solid[1:, :]
    right = np.zeros_like(solid); right[:, :-1] = solid[:, 1:]
    lit = solid & (~up | ~left)
    shd = solid & (~down | ~right) & ~lit
    c = a[..., :3].astype(float)
    c[lit] = c[lit] + (255 - c[lit]) * light
    c[shd] = c[shd] * (1 - dark)
    a[..., :3] = c.astype(np.uint8)


def grain(a, amount=0.035, seed=3):
    solid = a[..., 3] >= 200
    rng = np.random.default_rng(seed)
    n = 1 + rng.uniform(-amount, amount, solid.shape)
    c = a[..., :3].astype(float) * n[..., None]
    a[solid, :3] = np.clip(c[solid], 0, 255).astype(np.uint8)


GRADE_FX = {
    # aura alphas outward, sparkles
    "common": ((), 0),
    "rare": ((), 1),
    "epic": ((70, 30), 2),
    "legendary": ((95, 55, 22), 3),
    "mythic": ((110, 65, 30), 4),
    "divine": ((130, 85, 45, 18), 6),
}


def aura(a, color, alphas, color2=None):
    solid = a[..., 3] > 0
    cur = solid
    for i, al in enumerate(alphas):
        nxt = grow(cur)
        ring = nxt & ~cur
        c = color if color2 is None or i % 2 == 0 else color2
        a[ring, :3] = c[:3]
        a[ring, 3] = al
        cur = nxt


def sparkles(a, n, color, seed=5):
    """4-point stars just off the silhouette and on its brightest pixels."""
    if n <= 0:
        return
    rng = np.random.default_rng(seed)
    H, W = a.shape[:2]
    solid = a[..., 3] >= 200
    lum = a[..., :3].astype(float).sum(-1)
    cand = np.argwhere(solid & (lum >= np.percentile(lum[solid], 85)))
    edge = np.argwhere(grow(solid, 3) & ~grow(solid, 1))
    picks = []
    for i in range(n):
        pool = cand if i % 2 == 0 or len(edge) == 0 else edge
        if len(pool) == 0:
            continue
        for _ in range(20):
            y, x = pool[rng.integers(len(pool))]
            if all(abs(y - py) + abs(x - px) > 10 for py, px in picks):
                break
        picks.append((y, x))
        arm = 2 if i % 3 == 0 else 1
        a[y, x] = (255, 255, 255, 255)
        for k in range(1, arm + 1):
            al = 230 if k == 1 else 140
            for dy, dx in ((k, 0), (-k, 0), (0, k), (0, -k)):
                yy, xx = y + dy, x + dx
                if 0 <= yy < H and 0 <= xx < W:
                    if a[yy, xx, 3] >= 200:
                        a[yy, xx, :3] = np.minimum(255, a[yy, xx, :3].astype(int) // 2 + np.array(color[:3]) // 2 + 60)
                    else:
                        a[yy, xx] = (*color[:3], al)


def finish(img_or_arr, grade="common", accent=(255, 255, 255, 255), accent2=None, seed=5, rim=True, do_outline=True):
    a = np.array(img_or_arr) if not isinstance(img_or_arr, np.ndarray) else img_or_arr
    a = a.copy()
    if rim:
        bevel(a)
    grain(a, seed=seed)
    if do_outline:
        outline(a)
    alphas, n = GRADE_FX[grade]
    if alphas:
        aura(a, accent, alphas, accent2)
    sparkles(a, n, accent if grade != "divine" else (255, 244, 200), seed)
    return Image.fromarray(a)
