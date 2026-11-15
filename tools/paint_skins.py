#!/usr/bin/env python3
"""
Painted skins for the enemy models (used by gen_enemy_assets.py).

The first version of the enemy textures gave every material one 16x16 tile and stretched that same tile over every
face of every cube, so big faces turned into a blur of a few fat dots. This paints every face of every cube on its
own instead, at a fixed number of texels per model unit (2 to 3, so faces keep their proportions and the detail
stays fine), with:

- a material per cube (the model's own tile choice: hide, dark hide, armour plate, trim, glow crystal, eye, bone,
  cloth) and a style per model for the hide and the cloth: fur, scales, chitin, rock, leather; robe, wing membrane,
- the same lighting on every model: lit tops, shaded undersides, a soft top-to-bottom gradient on the sides and a
  dark rim around each face so the blocky shapes read clearly,
- the same pattern for every rank or affinity of a model (only the colours change), so they look like one creature.

The faces are packed into one atlas per model and every cube gets per-face UVs; the glow mask follows the emissive
pixels (crystals, eyes, runes, ember cracks).
"""
import math

import numpy as np
from PIL import Image

# hide/cloth style per model (the rest use leather and plain cloth)
STYLES = {
    "boss_wyrm": {"hide": "scales", "cloth": "membrane"},
    "boss_alpha": {"hide": "fur"},
    "gloomfang": {"hide": "fur"},
    "boss_magus": {"hide": "fur", "cloth": "robe"},
    "hollow_acolyte": {"cloth": "robe"},
    "boss_broodmother": {"hide": "chitin"},
    "broodling": {"hide": "chitin"},
    "shardback": {"hide": "rock"},
    "emberwisp": {"hide": "rock"},
    "boss_knight": {"cloth": "robe"},
    "ashen_sentinel": {"cloth": "robe"},
}

# texels per model unit: as many as fit the atlas, at most this
MAX_DENSITY = 3.0
MAX_ATLAS = 1024

HIDE, DARK, PLATE, TRIM, GLOW, EYE, BONE, CLOTH = range(8)
FACES = ("north", "south", "east", "west", "up", "down")


class Deferred:
    """Stands in for a model's tile UVs and texture size until the model is built (see gen_enemy_assets)."""

    def __init__(self, name, variants, make_palette, seed):
        self.name = name
        self.variants = variants
        self.make_palette = make_palette
        self.seed = seed

    def __getitem__(self, tile):
        return 0, 0


# --------------------------------------------------------------------------------------------- small helpers

def _noise(rng, w, h, cells=4, octaves=3):
    """Smooth value noise in 0..1, w x h."""
    out = np.zeros((h, w))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        n = cells * 2 ** o
        grid = rng.random((n + 1, n + 1))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((max(1, w), max(1, h)), Image.BICUBIC)
        out += np.asarray(img, dtype=float) / 255.0 * amp
        total += amp
        amp *= 0.5
    return out / total


def _fill(w, h, color):
    a = np.zeros((h, w, 3))
    a[:, :] = color
    return a


def _mul(a, k):
    return a * np.asarray(k)[..., None] if np.ndim(k) else a * k


def _mix(a, b, k):
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


def _lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


# --------------------------------------------------------------------------------------------- materials
# each returns (rgb h x w x 3 floats, emissive mask h x w bool, rim strength)

def m_fur(rng, w, h, face, color, light=None):
    """Layered fur: a dark undercoat, then many strands that fall downward, lighter at the tips; darker along
    the back, lighter on the belly."""
    n = _noise(rng, w, h, cells=3)
    if face == "up":
        color = _mix(color, (0, 0, 0), 0.22)
    elif face == "down":
        color = _mix(color, (226, 228, 236), 0.3)
    under = _mix(color, (0, 0, 0), 0.45)
    rgb = _fill(w, h, under) * (0.85 + 0.3 * n)[..., None]
    tip = _mix(color, light or (236, 240, 250), 0.38)
    count = int(w * h / 1.4) + 6
    for _ in range(count):
        x, y = int(rng.integers(0, w)), int(rng.integers(-3, h))
        length = int(rng.integers(3, 8))
        slant = rng.choice([-1, 0, 0, 1]) if face not in ("up", "down") else rng.choice([-1, 1])
        k = 0.82 + 0.4 * rng.random()
        for i in range(length):
            yy, xx = y + i, x + (slant * i) // 3
            if 0 <= yy < h and 0 <= xx < w:
                t = i / max(1, length - 1)
                c = _mix(color, tip, t * 0.85)
                rgb[yy, xx] = [v * k for v in c]
    if face not in ("up", "down") and h > 4:
        # the shaggy lower edge: a few longer, lighter locks
        for x in range(0, w, 2):
            for i in range(int(rng.integers(1, 3))):
                yy = h - 1 - i
                rgb[yy, x] = [v * 1.05 for v in tip]
    return rgb, None, 0.7


def m_scales(rng, w, h, face, color, glow=None, spots=False):
    """Overlapping rounded scales (each lit at its crown, shadowed where the next row overlaps it)."""
    s = 7 if face != "down" else 5
    rgb = np.zeros((h, w, 3))
    em = np.zeros((h, w), bool)
    belly = _mix(color, (226, 186, 132), 0.45)
    light = _mix(color, (255, 214, 170), 0.35)
    jitter = rng.random((h // s + 4, w // s + 4))
    if face == "down":
        # belly: wide overlapping plates across the body
        for y in range(h):
            v = (y % s) / s
            k = 1.15 - 0.45 * v - (0.3 if y % s == s - 1 else 0)
            rgb[y, :] = [ch * k for ch in belly]
        return rgb, em, 0.6
    for y in range(h):
        for x in range(w):
            # rows overlap downward: a pixel belongs to the lowest scale whose round lower edge still covers it
            row = y // s
            off = s / 2 if row % 2 else 0
            cx = math.floor((x + off) / s) * s - off + s / 2
            u = (x - cx) / (s / 2)           # -1..1 across the scale
            v = (y - row * s) / s             # 0 top .. 1 bottom
            edge = v + 0.55 * u * u           # rounded lower edge
            j = jitter[row % jitter.shape[0], int((x + off) // s) % jitter.shape[1]]
            c = _mix(color, light, max(0.0, 0.7 - edge) * 0.9)
            k = (1.12 - 0.55 * edge) * (0.86 + 0.28 * j)
            if edge > 1.0:
                k *= 0.5   # the shadow of the scale above
            elif edge > 0.88:
                k *= 0.7
            rgb[y, x] = [ch * k for ch in c]
    if spots and glow is not None:
        # ember cracks: a few scale rims smoulder
        for _ in range(max(1, w * h // 420)):
            row = int(rng.integers(0, max(1, h // s)))
            col = int(rng.integers(0, max(1, w // s + 1)))
            off = s / 2 if row % 2 else 0
            cx = col * s - off + s / 2
            for x in range(int(cx - s / 2), int(cx + s / 2) + 1):
                if 0 <= x < w:
                    u = (x - cx) / (s / 2)
                    y = int(row * s + (0.95 - 0.55 * u * u) * s)
                    if 0 <= y < h:
                        rgb[y, x] = glow
                        em[y, x] = True
    return rgb, em, 0.7


def m_chitin(rng, w, h, face, color):
    n = _noise(rng, w, h, cells=2)
    rgb = _fill(w, h, color) * (0.8 + 0.3 * n)[..., None]
    band = 6
    for y in range(h):
        t = (y % band) / band
        k = 1.0 + 0.25 * max(0, 1 - abs(t - 0.25) * 6) - (0.35 if y % band == band - 1 else 0)
        rgb[y] *= k
    if face not in ("down",):
        # a glossy streak
        for x in range(w):
            y = int(h * 0.25 + 0.15 * h * math.sin(x / max(1, w) * math.pi))
            if 0 <= y < h:
                rgb[y, x] = rgb[y, x] * 0.4 + np.array(_mix(color, (255, 255, 255), 0.55)) * 0.6
    for _ in range(w * h // 30):
        x, y = rng.integers(0, w), rng.integers(0, h)
        rgb[y, x] *= 0.75
    return rgb, None, 0.8


def m_rock(rng, w, h, face, color):
    n = _noise(rng, w, h, cells=4)
    rgb = _fill(w, h, color) * (0.75 + 0.45 * n)[..., None]
    for _ in range(max(1, w * h // 90)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for _ in range(int(rng.integers(4, 12))):
            if 0 <= x < w and 0 <= y < h:
                rgb[y, x] *= 0.55
            x += int(rng.integers(-1, 2))
            y += int(rng.integers(0, 2))
    for _ in range(w * h // 25):
        x, y = rng.integers(0, w), rng.integers(0, h)
        rgb[y, x] *= 1.2
    return rgb, None, 0.75


def m_leather(rng, w, h, face, color):
    n = _noise(rng, w, h, cells=5)
    rgb = _fill(w, h, color) * (0.78 + 0.38 * n)[..., None]
    for _ in range(w * h // 18):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for i in range(int(rng.integers(2, 4))):
            if x + i < w:
                rgb[y, x + i] *= 0.82
    return rgb, None, 0.75


def m_plate(rng, w, h, face, color, trim):
    n = _noise(rng, w, h, cells=2)
    rgb = _fill(w, h, color) * (0.86 + 0.22 * n)[..., None]
    # brushed
    streak = rng.random(h)
    rgb *= (0.94 + 0.12 * streak)[:, None, None]
    # bevel
    if w >= 4 and h >= 4:
        rgb[0, :] *= 1.35
        rgb[:, 0] *= 1.2
        rgb[h - 1, :] *= 0.55
        rgb[:, w - 1] *= 0.7
    # rivets in the corners
    if w >= 8 and h >= 8:
        for (x, y) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
            rgb[y, x] = trim
            rgb[y - 1, x - 1] = [min(255, v * 1.3) for v in trim]
            rgb[y + 1, x + 1] = [v * 0.5 for v in trim]
    # scratches
    for _ in range(max(1, w * h // 60)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for i in range(int(rng.integers(3, 6))):
            if 0 <= x + i < w and 0 <= y + i // 2 < h:
                rgb[y + i // 2, x + i] *= 1.3
    return rgb, None, 0.6


def m_trim(rng, w, h, face, color):
    rgb = np.zeros((h, w, 3))
    for y in range(h):
        k = 0.7 + 0.55 * math.sin((y + 0.5) / max(1, h) * math.pi)
        rgb[y, :] = [min(255, v * k) for v in color]
    if h >= 3:
        rgb[0, :] *= 1.25
        rgb[h - 1, :] *= 0.6
    for x in range(1, w, 4):
        if h >= 4:
            rgb[h // 2, x] *= 0.7
    return rgb, None, 0.5


def m_glow(rng, w, h, face, color):
    rgb = np.zeros((h, w, 3))
    for y in range(h):
        for x in range(w):
            k = 0.6 + 0.5 * (1 - y / max(1, h))
            facet = 1.18 if ((x + y // 2) % 6) < 2 else 1.0
            rgb[y, x] = [min(255, v * k * facet + 70 * (1 - y / max(1, h))) for v in color]
    return rgb, np.ones((h, w), bool), 0.0


def m_eye(rng, w, h, face, color):
    rgb = np.zeros((h, w, 3))
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = max(1.0, min(w, h) / 2)
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - cx) / max(1, w / 2), (y - cy) / max(1, h / 2))
            rgb[y, x] = [min(255, v + (255 - v) * max(0, 1 - d * 1.3)) for v in color]
    return rgb, np.ones((h, w), bool), 0.0


def m_bone(rng, w, h, face, color):
    n = _noise(rng, w, h, cells=3)
    rgb = np.zeros((h, w, 3))
    for y in range(h):
        k = 1.05 - 0.4 * y / max(1, h)
        rgb[y] = np.array(color) * k * (0.9 + 0.2 * n[y])[:, None]
    for y in range(2, h, 5):
        rgb[y] *= 0.82
    for _ in range(max(0, w * h // 120)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        for _ in range(4):
            if 0 <= x < w and 0 <= y < h:
                rgb[y, x] *= 0.7
            y += 1
            x += int(rng.integers(-1, 2))
    return rgb, None, 0.65


def m_cloth(rng, w, h, face, color, glow=None, trim=None, robe=False):
    n = _noise(rng, w, h, cells=3)
    rgb = _fill(w, h, color) * (0.82 + 0.25 * n)[..., None]
    weave = ((np.indices((h, w)).sum(axis=0) % 2) * 0.06 + 0.97)
    rgb *= weave[..., None]
    em = np.zeros((h, w), bool)
    if face not in ("up", "down"):
        # folds
        phase = rng.random() * 6
        folds = 0.86 + 0.2 * np.sin(np.arange(w) / max(3.0, w / 3.2) * math.pi + phase)
        rgb *= folds[None, :, None]
        if robe and h >= 10 and trim is not None:
            # an embroidered hem with glowing runes
            band = max(3, h // 7)
            y0 = h - band - 1
            rgb[y0:y0 + band, :] = np.array(trim) * 0.75
            rgb[y0, :] = np.array(trim) * 1.1
            rgb[y0 + band - 1, :] = np.array(trim) * 0.5
            if glow is not None and band >= 3:
                for x0 in range(1, w - 3, 5):
                    bits = rng.integers(0, 2, (band - 2, 3))
                    for yy in range(band - 2):
                        for xx in range(3):
                            if bits[yy, xx]:
                                rgb[y0 + 1 + yy, x0 + xx] = glow
                                em[y0 + 1 + yy, x0 + xx] = True
            # a trim line down the front
    return rgb, em, 0.7


def m_membrane(rng, w, h, face, color, bone):
    rgb = np.zeros((h, w, 3))
    # veins fan out from the wing arm (the top edge) to the trailing edge
    veins = max(3, w // 12)
    for y in range(h):
        for x in range(w):
            t = x / max(1, w) * veins
            d = abs(t - round(t))
            k = 0.75 + 0.45 * d + 0.15 * (1 - y / max(1, h))
            rgb[y, x] = [v * k for v in color]
            if d < 0.06 + 0.02 * (1 - y / max(1, h)):
                rgb[y, x] = [v * 0.75 for v in bone]
    n = _noise(rng, w, h, cells=4)
    rgb *= (0.88 + 0.2 * n)[..., None]
    return rgb, None, 0.5


# --------------------------------------------------------------------------------------------- painting

def _paint_face(model_name, mat, rng, w, h, face, pal):
    style = STYLES.get(model_name, {})
    hide_style = style.get("hide", "leather")
    cloth_style = style.get("cloth", "cloth")

    def hide(color):
        if hide_style == "fur":
            return m_fur(rng, w, h, face, color)
        if hide_style == "scales":
            return m_scales(rng, w, h, face, color, pal["glow"], pal["spots"])
        if hide_style == "chitin":
            return m_chitin(rng, w, h, face, color)
        if hide_style == "rock":
            return m_rock(rng, w, h, face, color)
        return m_leather(rng, w, h, face, color)

    if mat == HIDE:
        return hide(pal["hide"])
    if mat == DARK:
        return hide([v * 0.55 for v in pal["hide"]])
    if mat == PLATE:
        return m_plate(rng, w, h, face, pal["plate"], pal["trim"])
    if mat == TRIM:
        return m_trim(rng, w, h, face, pal["trim"])
    if mat == GLOW:
        return m_glow(rng, w, h, face, pal["glow"])
    if mat == EYE:
        return m_eye(rng, w, h, face, pal["glow"])
    if mat == BONE:
        return m_bone(rng, w, h, face, pal["bone"])
    if cloth_style == "membrane":
        return m_membrane(rng, w, h, face, pal["cloth"], pal["bone"])
    return m_cloth(rng, w, h, face, pal["cloth"], pal["glow"], pal["trim"], robe=cloth_style == "robe")


def _light(rgb, face, rim):
    h, w = rgb.shape[:2]
    k = {"up": 1.1, "down": 0.62, "north": 0.98, "south": 0.9, "east": 0.86, "west": 0.86}[face]
    out = rgb * k
    if face not in ("up", "down") and h > 2:
        out *= np.linspace(1.08, 0.82, h)[:, None, None]
    if rim > 0 and w >= 4 and h >= 4:
        edge = 1 - (1 - rim) * 0.6
        out[0, :] *= edge if face != "up" else 1.05
        out[h - 1, :] *= edge * 0.9
        out[:, 0] *= edge
        out[:, w - 1] *= edge
    return out


def _face_dims(size, face):
    sx, sy, sz = size
    return {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[face]


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


def paint(name, bones, deferred, tex_dir, max_atlas=MAX_ATLAS):
    """Gives every cube per-face UVs, paints every variant's texture and glow mask; returns the atlas size."""
    cubes = [c for b in bones for c in b.get("cubes", []) if "_mat" in c]
    density = MAX_DENSITY
    while True:
        rects, keys = [], []
        for ci, c in enumerate(cubes):
            for f in FACES:
                a, b = _face_dims(c["size"], f)
                rects.append((max(1, int(round(a * density))), max(1, int(round(b * density)))))
                keys.append((ci, f))
        size = 64
        while size <= max_atlas:
            pos, used = _pack(rects, size)
            if used <= size:
                break
            size *= 2
        if size <= max_atlas or density <= 1.0:
            break
        density -= 0.5
    size = min(size, max_atlas)

    for (ci, f), (w, h), (x, y) in zip(keys, rects, pos):
        c = cubes[ci]
        c["uv"][f] = {"uv": [x, y], "uv_size": [w, h]}

    import os
    for vi, (suffix, *args) in enumerate(deferred.variants):
        pal = deferred.make_palette(np.random.default_rng(deferred.seed + vi), *args)
        img = np.zeros((size, size, 4))
        glow = np.zeros((size, size, 4))
        for fi, ((ci, f), (w, h), (x, y)) in enumerate(zip(keys, rects, pos)):
            # the same pattern for every variant of a model: only the colours change
            rng = np.random.default_rng(deferred.seed * 7919 + fi)
            rgb, em, rim = _paint_face(name, cubes[ci]["_mat"], rng, w, h, f, pal)
            rgb = np.clip(_light(rgb, f, rim) if em is None or not em.all() else rgb, 0, 255)
            if f not in ("up", "down"):
                # GeckoLib maps the side faces bottom-up: paint top-down, then flip
                rgb = rgb[::-1]
                em = None if em is None else em[::-1]
            img[y:y + h, x:x + w, :3] = rgb
            img[y:y + h, x:x + w, 3] = 255
            if em is not None and em.any():
                g = np.zeros((h, w, 4))
                g[em, :3] = rgb[em]
                g[em, 3] = 255
                glow[y:y + h, x:x + w] = g
        Image.fromarray(img.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}.png"))
        Image.fromarray(glow.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}_glowmask.png"))
    for c in cubes:
        del c["_mat"]
    return size, density
