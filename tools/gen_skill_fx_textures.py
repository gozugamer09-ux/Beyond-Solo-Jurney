#!/usr/bin/env python3
"""Textures for the skill effects' soft particles and shapes (client/skill/fx).

Everything is white on transparent: the game tints it with the vertex colour. Additive textures (light) keep their
intensity in alpha; the smoke and dust sheets are alpha-blended puffs with a little shading baked into grey.
Flipbook sheets are 4x4 frames, read row by row.

Run from the repo root:  python3 tools/gen_skill_fx_textures.py [--preview <png>]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/beyondsolo/textures/effect/fx"
RNG = np.random.default_rng(20261118)


def save(name, rgba, clamp=True):
    """rgba: float array (h, w, 4) in 0..1."""
    os.makedirs(OUT, exist_ok=True)
    a = np.clip(rgba, 0, 1)
    img = Image.fromarray((a * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"texture": {"blur": True, "clamp": clamp}}, f)


def white(alpha, grey=None):
    h, w = alpha.shape
    out = np.ones((h, w, 4), np.float32)
    if grey is not None:
        out[..., 0] = out[..., 1] = out[..., 2] = grey
    out[..., 3] = alpha
    return out


def grid(w, h):
    """x, y in -1..1 (pixel centres), y down."""
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    ys = (np.arange(h) + 0.5) / h * 2 - 1
    return np.meshgrid(xs, ys)


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def noise(w, h, cells, seed=None):
    """Value noise in 0..1: a random grid of `cells` blown up smoothly."""
    r = RNG if seed is None else np.random.default_rng(seed)
    cw, ch = max(2, int(cells)), max(2, int(cells * h / w))
    small = (r.random((ch + 1, cw + 1)) * 255).astype(np.uint8)
    big = Image.fromarray(small, "L").resize((w, h), Image.BICUBIC)
    return np.asarray(big, np.float32) / 255


def fbm(w, h, base=4, octaves=4, seed=None):
    total, amp, norm = np.zeros((h, w), np.float32), 1.0, 0.0
    for o in range(octaves):
        total += amp * noise(w, h, base * 2 ** o, None if seed is None else seed + o)
        norm += amp
        amp *= 0.5
    return total / norm


# ---------------------------------------------------------------- light

def soft():
    x, y = grid(64, 64)
    r = np.hypot(x, y)
    a = np.exp(-(r / 0.36) ** 2 * 1.6) * 0.85 + np.exp(-(r / 0.12) ** 2) * 0.25
    save("soft", white(a * smooth(1.0, 0.75, r)))


def hot():
    """A glow with a small white-hot centre: the heart of an impact."""
    x, y = grid(64, 64)
    r = np.hypot(x, y)
    a = np.exp(-(r / 0.5) ** 2 * 2.2) * 0.55 + np.exp(-(r / 0.1) ** 2) * 0.9
    save("hot", white(a * smooth(1.0, 0.8, r)))


def spark():
    """A streak of light with its head on the right: stretched along a spark's flight."""
    w, h = 64, 16
    x, y = grid(w, h)
    u = (x + 1) / 2
    width = 0.22 + 0.4 * u
    core = np.exp(-(y / (width * 0.55)) ** 2 * 2.5)
    along = smooth(0.0, 0.75, u) * smooth(1.0, 0.86, u)
    a = core * along * (0.45 + 0.55 * u)
    save("spark", white(a))


def ember():
    x, y = grid(32, 32)
    r = np.hypot(x, y)
    a = np.exp(-(r / 0.2) ** 2) + 0.35 * np.exp(-(r / 0.55) ** 2)
    save("ember", white(np.clip(a, 0, 1) * smooth(1.0, 0.8, r)))


def flare():
    """Four long rays and four short ones round a hot core: the flash of a hit."""
    x, y = grid(128, 128)
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    a = np.exp(-(r / 0.16) ** 2) * 0.9 + np.exp(-(r / 0.45) ** 2) * 0.25
    for k in range(4):
        th = k * math.pi / 2
        d = np.abs(np.sin(ang - th)) * r          # distance from the ray's line
        along = np.cos(ang - th) * r
        ray = np.exp(-(d / (0.018 + 0.02 * (1 - r))) ** 2) * np.clip(1 - r, 0, 1) ** 1.5 * (along > 0)
        a += ray * 0.9
        th2 = th + math.pi / 4
        d2 = np.abs(np.sin(ang - th2)) * r
        along2 = np.cos(ang - th2) * r
        a += np.exp(-(d2 / 0.016) ** 2) * np.clip(1 - r / 0.55, 0, 1) ** 2 * (along2 > 0) * 0.5
    save("flare", white(np.clip(a, 0, 1) * smooth(1.0, 0.85, r)))


def rays():
    """God rays out of a point, uneven like light through cloud."""
    x, y = grid(128, 128)
    r = np.hypot(x, y)
    ang = (np.arctan2(y, x) + math.pi) / (2 * math.pi)
    n = np.zeros_like(r)
    for k, w in ((24, 0.6), (48, 0.3), (96, 0.15)):
        row = RNG.random(k)
        idx = ang * k
        i0 = np.floor(idx).astype(int) % k
        f = idx - np.floor(idx)
        f = f * f * (3 - 2 * f)
        n += w * (row[i0] * (1 - f) + row[(i0 + 1) % k] * f)
    n /= 1.05
    beams = smooth(0.45, 0.95, n)
    a = beams * np.clip(1 - r, 0, 1) ** 1.3 * smooth(0.0, 0.12, r) + np.exp(-(r / 0.15) ** 2) * 0.6
    save("rays", white(np.clip(a, 0, 1)))


def shock():
    """A shockwave seen from above: a sharp bright front and a soft wake inside it."""
    x, y = grid(256, 256)
    r = np.hypot(x, y)
    ang = np.arctan2(y, x)
    wob = fbm(256, 256, 6, 3)
    front = 0.9 + 0.04 * (wob - 0.5)
    edge = np.exp(-((r - front) / 0.022) ** 2)
    wake = np.clip(r / front, 0, 1) ** 5 * (r < front) * 0.45 * (0.7 + 0.6 * wob)
    a = (edge + wake) * smooth(1.0, 0.96, r)
    save("shock", white(np.clip(a, 0, 1)))


def rim():
    """A sphere's outline as light: clear in the middle, bright towards the rim (a cheap fresnel)."""
    x, y = grid(128, 128)
    r = np.hypot(x, y)
    a = smooth(0.55, 0.985, r) ** 3 * smooth(1.0, 0.975, r)
    save("rim", white(a))


def trail():
    """A ribbon's cross-section (v) with faint streaks running along it (u): dash wakes, projectile tails."""
    w, h = 256, 32
    x, y = grid(w, h)
    core = np.exp(-(y / 0.2) ** 2) * 0.85 + np.exp(-(y / 0.6) ** 2) * 0.35
    streaks = noise(4, h, 2, 11)  # varies across only
    lines = 0.75 + 0.5 * (np.asarray(Image.fromarray((streaks * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255 - 0.5)
    a = core * lines * smooth(1.0, 0.85, np.abs(y))
    save("trail", white(np.clip(a, 0, 1)))


def arc():
    """A blade's trail: the cutting edge bright along the top (v=0), the swept air fading below it."""
    w, h = 256, 64
    xs = np.linspace(0, 1, w)
    vs = np.linspace(0, 1, h)
    u, v = np.meshgrid(xs, vs)
    fine = np.asarray(Image.fromarray((RNG.random((h, 6)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC), np.float32) / 255
    streak = np.clip(0.95 + 0.7 * (fine - 0.5), 0.6, 1.25)
    # a white-hot edge, then the swept air behind it thinning away in streaks
    wake = np.exp(-np.maximum(v - 0.12, 0) * 3.2)
    streak = 1 + (streak - 1) * smooth(0.1, 0.45, v)
    a = np.where(v < 0.12, 1.0, wake * streak)
    a *= smooth(1.0, 0.85, v) * smooth(0.0, 0.03, v)
    save("arc", white(np.clip(a, 0, 1)))


def wisp():
    """A curl of soul-smoke: a soft comma of light."""
    s = 64
    x, y = grid(s, s)
    a = np.zeros((s, s), np.float32)
    for i in range(40):
        t = i / 39
        ang = -0.6 + 3.6 * t
        rad = 0.55 * (1 - 0.6 * t)
        cx, cy = math.cos(ang) * rad * 0.8, math.sin(ang) * rad * 0.8 + 0.1
        size = 0.09 + 0.16 * (1 - t)
        a += np.exp(-((x - cx) ** 2 + (y - cy) ** 2) / (size * size)) * (0.25 + 0.75 * t) * 0.18
    r = np.hypot(x, y)
    a = np.clip(a, 0, 1) * smooth(1.0, 0.8, r)
    save("wisp", white(a))


def drop():
    """A drop of water (or blood) falling: a bead with a tail, and a glint."""
    w, h = 16, 32
    x, y = grid(w, h)
    head = np.exp(-((x / 0.55) ** 2 + ((y - 0.45) / 0.42) ** 2) * 2.2)
    tail = np.exp(-(x / (0.12 + 0.3 * smooth(-1, 0.45, y))) ** 2) * smooth(-1.0, 0.3, y) * 0.6
    glint = np.exp(-(((x + 0.18) / 0.15) ** 2 + ((y - 0.32) / 0.12) ** 2)) * 0.8
    save("drop", white(np.clip(head + tail + glint, 0, 1)))


def shard():
    """A sliver of crystal or stone: a kite with a bright edge."""
    s = 32
    x, y = grid(s, s)
    # kite: |x|/wx + |y|/wy <= 1, longer downwards
    wx, wy = 0.42, np.where(y > 0, 0.95, 0.6)
    d = np.abs(x) / wx + np.abs(y) / wy
    fill = smooth(1.0, 0.85, d)
    edge = np.exp(-((d - 0.88) / 0.08) ** 2) * 0.8
    facet = 0.55 + 0.45 * (x < 0)
    save("shard", white(np.clip(fill * facet * 0.75 + edge, 0, 1)))


def petal():
    s = 32
    x, y = grid(s, s)
    yy = y * 1.05
    wid = 0.62 * np.sqrt(np.clip(1 - ((yy - 0.05) / 0.95) ** 2, 0, 1))
    notch = 0.12 * np.exp(-(x / 0.12) ** 2) * (yy < -0.75)
    inside = smooth(0.0, 0.08, wid - np.abs(x)) * (yy > -0.98 + notch * 4)
    shade = 0.75 + 0.25 * (1 - np.abs(x) / 0.62)
    a = inside * (0.65 + 0.35 * smooth(1, -1, yy))
    save("petal", white(np.clip(a, 0, 1), shade))


def leaf():
    s = 32
    x, y = grid(s, s)
    wid = 0.55 * np.clip(1 - y ** 2, 0, 1) ** 0.8 * (1 - 0.25 * y)
    inside = smooth(0.0, 0.07, wid - np.abs(x)) * (np.abs(y) < 0.97)
    rib = np.exp(-(x / 0.05) ** 2) * 0.25
    stem = np.exp(-(x / 0.05) ** 2) * (y > 0.8) * (y < 1.0)
    a = np.clip(inside + stem, 0, 1)
    shade = np.clip(0.8 - rib + 0.2 * (x > 0), 0, 1)
    save("leaf", white(a, shade))


def feather():
    s = 32
    x, y = grid(s, s)
    yy = -y
    wid = 0.38 * np.sqrt(np.clip(1 - ((yy - 0.1) / 0.9) ** 2, 0, 1))
    vane = smooth(0.0, 0.08, wid - np.abs(x + 0.05 * yy)) * (yy > -0.8)
    shaft = np.exp(-(x / 0.05) ** 2) * (yy > -1.0) * (yy < 0.9)
    barbs = 0.8 + 0.2 * np.sin((yy * 18 + np.abs(x) * 10))
    a = np.clip(vane * barbs * 0.85 + shaft, 0, 1)
    save("feather", white(a))


# ---------------------------------------------------------------- flipbooks

def sheet(frames, size=64):
    out = np.zeros((size * 4, size * 4, 4), np.float32)
    for i, f in enumerate(frames[:16]):
        r, c = divmod(i, 4)
        out[r * size:(r + 1) * size, c * size:(c + 1) * size] = f
    return out


def flame_sheet():
    """Sixteen moments of a lick of flame: born small, flaring, tearing and gone. Light (additive)."""
    s = 64
    x, y = grid(s, s)
    base = fbm(s, s * 4, 3, 5, 101)
    frames = []
    for k in range(16):
        t = k / 15
        off = int(k * 9) % (s * 3)
        n = base[off:off + s, :]
        n2 = fbm(s, s, 5, 3, 200 + k)
        grow = math.sin(min(1.0, t * 2.4) * math.pi / 2)
        height = 0.55 + 0.45 * grow - 0.35 * max(0.0, t - 0.5)
        root = 0.78
        tip = root - 1.65 * height
        cy = (y - tip) / (root - tip)                   # 0 at the tip, 1 at the root (rounded off below 1)
        c = np.clip(cy, 0, 1)
        upper = 0.4 * np.sin(np.clip(c / 0.72, 0, 1) * math.pi / 2) ** 1.1
        lower = 0.4 * np.sqrt(np.clip(1 - ((cy - 0.72) / 0.3) ** 2, 0, 1))
        width = np.where(cy < 0.72, upper, lower)
        xd = x + 0.32 * (n2 - 0.5) * (1 - c) ** 1.4 + 0.08 * math.sin(k * 1.7) * (1 - c)
        body = smooth(0.0, 0.09, width - np.abs(xd)) * (cy > 0)
        tear = smooth(0.12 + 0.5 * t, 0.42 + 0.45 * t, n * 0.55 + c * 0.55 + 0.05)
        core = 1 - 0.5 * np.clip(np.abs(xd) / np.maximum(width, 1e-3), 0, 1) ** 2
        a = body * tear * (0.45 + 0.55 * c) * core * (1 - 0.5 * t)
        frames.append(white(np.clip(a * 1.6, 0, 1)))
    save("flame_sheet", sheet(frames))


def smoke_sheet():
    """Sixteen puffs of smoke, each its own shape, shaded from the top. Alpha-blended."""
    s = 64
    x, y = grid(s, s)
    frames = []
    for k in range(16):
        n = fbm(s, s, 3, 5, 400 + k * 7)
        r = np.hypot(x * (1 + 0.15 * math.sin(k)), y * (1 + 0.15 * math.cos(k * 1.3)))
        blob = np.clip(1 - r / 0.92, 0, 1)
        dens = blob ** 0.8 * (0.35 + 0.95 * n)
        a = smooth(0.18, 0.75, dens) * smooth(1.0, 0.85, r)
        light = np.clip(0.78 + 0.35 * (-y * 0.6 - x * 0.2) + 0.15 * (n - 0.5), 0.45, 1)
        frames.append(white(a * 0.95, light))
    save("smoke_sheet", sheet(frames))


def preview(path):
    names = sorted(f[:-4] for f in os.listdir(OUT) if f.endswith(".png"))
    tiles = []
    for n in names:
        im = Image.open(os.path.join(OUT, n + ".png")).convert("RGBA")
        im.thumbnail((160, 160))
        bg = Image.new("RGBA", (170, 170), (18, 20, 28, 255))
        bg.alpha_composite(im, ((170 - im.width) // 2, (170 - im.height) // 2))
        tiles.append(bg)
    cols = 6
    rows = (len(tiles) + cols - 1) // cols
    out = Image.new("RGBA", (170 * cols, 170 * rows), (0, 0, 0, 255))
    for i, t in enumerate(tiles):
        out.paste(t, ((i % cols) * 170, (i // cols) * 170))
    out.save(path)
    print("preview", path, names)


def main():
    for f in (soft, hot, spark, ember, flare, rays, shock, rim, trail, arc, wisp, drop, shard, petal, leaf, feather,
              flame_sheet, smoke_sheet):
        f()
    print("wrote textures to", OUT)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
