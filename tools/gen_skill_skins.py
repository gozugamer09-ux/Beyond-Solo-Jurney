#!/usr/bin/env python3
"""The skins of skill effects (textures/effect/skin), drawn by the energy shader (shaders/core/energy.fsh).

Each picture holds two things. Its grey (the red channel) is heat: light, which the shader runs from the effect's
colour to white where it is hottest. Its alpha is cover: the body of the effect in its own colour, which hides what is
behind it. So a blade has a white-hot edge, a body of its colour and a soft inner side that lets the world through.

Pictures meant to be scrolled (beams, flames, water, walls) tile along the axis they scroll on and are saved with
clamp off. Run from the repo root:  python3 tools/gen_skill_skins.py [--preview <png>]
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/beyondsolo/textures/effect/skin"
SAVED = []


def save(name, heat, cover, clamp=True, rgb=None):
    """heat, cover: float arrays (h, w) in 0..1. rgb: an own colour for blended (not energy) pictures."""
    os.makedirs(OUT, exist_ok=True)
    h, w = heat.shape
    out = np.zeros((h, w, 4), np.float32)
    if rgb is None:
        out[..., 0] = out[..., 1] = out[..., 2] = np.clip(heat, 0, 1)
    else:
        out[..., :3] = np.clip(rgb, 0, 1)
    out[..., 3] = np.clip(cover, 0, 1)
    img = Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"texture": {"blur": True, "clamp": clamp}}, f)
    SAVED.append((name, out))


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


def pnoise(w, h, cx, cy, seed):
    """Value noise in 0..1 that wraps around both edges: a cx * cy grid of random values, smoothly blended."""
    r = np.random.default_rng(seed)
    g = r.random((cy, cx))
    xs = np.arange(w) / w * cx
    ys = np.arange(h) / h * cy
    x0 = np.floor(xs).astype(int)
    y0 = np.floor(ys).astype(int)
    fx, fy = xs - x0, ys - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    x1, y1 = (x0 + 1) % cx, (y0 + 1) % cy
    x0, y0 = x0 % cx, y0 % cy
    a = g[y0[:, None], x0[None, :]]
    b = g[y0[:, None], x1[None, :]]
    c = g[y1[:, None], x0[None, :]]
    d = g[y1[:, None], x1[None, :]]
    top = a + (b - a) * sx[None, :]
    bot = c + (d - c) * sx[None, :]
    return top + (bot - top) * sy[:, None]


def fbm(w, h, cx, cy, octaves, seed, gain=0.55):
    total = np.zeros((h, w), np.float64)
    amp, norm = 1.0, 0.0
    for o in range(octaves):
        total += amp * pnoise(w, h, cx * 2 ** o, cy * 2 ** o, seed + o * 101)
        norm += amp
        amp *= gain
    return stretch(total / norm)


def stretch(n, lo=1, hi=99):
    """Spreads noise over 0..1 (it bunches round the middle)."""
    a, b = np.percentile(n, lo), np.percentile(n, hi)
    return np.clip((n - a) / (b - a), 0, 1)


def ridged(n):
    """Thin bright filaments where the noise crosses its middle."""
    return 1 - np.abs(2 * n - 1)


def uv(w, h):
    """u along x and v down y, both 0..1 at pixel centres."""
    return np.meshgrid((np.arange(w) + 0.5) / w, (np.arange(h) + 0.5) / h)


# ---------------------------------------------------------------------------------------------------------------

def blade():
    """A sword's cut: u along it (0 the tail, 1 the head), v across it (0 the cutting edge, 1 the inner side)."""
    w, h = 256, 64
    u, v = uv(w, h)
    lines = fbm(w, h, 3, 40, 3, 11)  # speed lines along the cut
    edge = np.exp(-((v - 0.045) / 0.035) ** 2)
    body = (1 - v) ** 1.7
    # two fainter lines inside the band, broken here and there
    inner = 0.0
    for at, width, seed in ((0.22, 0.02, 3), (0.4, 0.016, 5)):
        broken = smooth(0.35, 0.65, fbm(w, h, 5, 1, 2, seed))
        inner = inner + np.exp(-((v - at) / width) ** 2) * broken
    heat = edge * 1.05 + body * (0.32 + 0.4 * lines) + inner * 0.45 * (1 - v)
    # solid near the edge, thinning fast towards the inner side so the cut stays a sharp crescent
    cover = (1 - v) ** 2.4 * (0.62 + 0.38 * lines) + edge * 0.35
    save("blade", heat, cover)


def beam():
    """Across a beam (v), bright in the middle; streaks running along it (u), which tiles."""
    w, h = 256, 64
    u, v = uv(w, h)
    d = np.abs(v - 0.5) * 2
    streak = fbm(w, h, 6, 12, 4, 21)
    core = np.exp(-(d / 0.13) ** 2)
    body = np.exp(-(d / 0.5) ** 2)
    heat = core * 1.05 + body * (0.18 + 0.55 * streak) * (1 - d) ** 0.5
    cover = body * (0.7 + 0.45 * streak) * smooth(1.0, 0.6, d)
    save("beam", heat, cover, clamp=False)


def lightning():
    """Across a bolt: a thin white-hot core, its colour close round it."""
    w, h = 64, 32
    u, v = uv(w, h)
    d = np.abs(v - 0.5) * 2
    heat = np.exp(-(d / 0.11) ** 2) * 1.1 + 0.3 * np.exp(-(d / 0.4) ** 2)
    cover = np.exp(-(d / 0.32) ** 2) * 0.85
    save("lightning", heat, cover, clamp=False)


def plasma():
    """Raw energy that tiles both ways: bright filaments over a body that is thicker in places."""
    w = h = 256
    n1 = fbm(w, h, 4, 4, 5, 31)
    n2 = fbm(w, h, 3, 3, 4, 47)
    fil = ridged(n1) ** 5
    heat = fil * 1.1 + 0.18 * n2
    cover = 0.25 + 0.6 * n2 ** 1.2 + 0.25 * fil
    save("plasma", heat, cover, clamp=False)


def fire():
    """Flames that tile both ways, stretched upwards (v runs down the picture, so scroll v to make them rise)."""
    w = h = 256
    n = fbm(w, h, 6, 3, 5, 61)
    t = fbm(w, h, 12, 6, 3, 67)
    f = np.clip(n * 0.8 + t * 0.35 - 0.1, 0, 1)
    heat = smooth(0.35, 1.0, f) ** 1.2 * 1.1
    cover = smooth(0.12, 0.6, f)
    save("fire", heat, cover, clamp=False)


def water():
    """Moving water that tiles both ways: a body of its colour, bright where it foams and catches the light."""
    w = h = 256
    n1 = fbm(w, h, 5, 5, 4, 71)
    n2 = fbm(w, h, 8, 8, 3, 73)
    foam = ridged(n1) ** 7 + 0.6 * ridged(n2) ** 9
    heat = foam * 1.1 + 0.12 * n2
    cover = 0.55 + 0.35 * n1
    save("water", heat, cover, clamp=False)


def wind():
    """Lines of moving air along u (tiles), soft and mostly see-through."""
    w, h = 256, 64
    u, v = uv(w, h)
    heat = np.zeros((h, w))
    r = np.random.default_rng(81)
    for i in range(9):
        at = r.uniform(0.12, 0.88)
        width = r.uniform(0.015, 0.045)
        runs = smooth(0.45, 0.75, fbm(w, h, int(r.integers(2, 5)), 1, 2, 83 + i))
        heat += np.exp(-((v - at) / width) ** 2) * runs * r.uniform(0.5, 1.0)
    d = np.abs(v - 0.5) * 2
    heat *= smooth(1.0, 0.7, d)
    cover = np.clip(heat * 0.55, 0, 1)
    save("wind", heat, cover, clamp=False)


def hexes():
    """
    A hexagon grid that tiles both ways: lines of light with a faint fill, for barriers. The picture is four hexes
    across and two rows of two down (x 0..4, y 0..2 * sqrt 3), so it is taller than wide: 256 x 222.
    """
    s = np.array([1.0, np.sqrt(3)])
    w, h = 256, 222
    xs = (np.arange(w) + 0.5) / w * 4
    ys = (np.arange(h) + 0.5) / h * 2 * s[1]
    x, y = np.meshgrid(xs, ys)
    p = np.stack([x, y], -1)
    # the offset from the nearest hex centre, from the two lattices that make up a hex grid
    a = np.mod(p, s) - s * 0.5
    b = np.mod(p - s * 0.5, s) - s * 0.5
    g = np.where((np.sum(a * a, -1) < np.sum(b * b, -1))[..., None], a, b)
    gx, gy = np.abs(g[..., 0]), np.abs(g[..., 1])
    n = np.array([1.0, np.sqrt(3)]) / 2
    e = 0.5 - np.maximum(gx * n[0] + gy * n[1], gx)  # distance to the hex's edge
    line = np.exp(-(e / 0.03) ** 2)
    glint = fbm(w, h, 4, 4, 3, 91)
    heat = line * (0.5 + 0.65 * glint)
    cover = 0.1 + line * 0.55 + 0.08 * glint
    save("hex", heat, cover, clamp=False)


def wall():
    """A band of light standing up from the ground: u round it (tiles), v down the picture (0 the top, 1 the foot)."""
    w, h = 256, 64
    u, v = uv(w, h)
    streak = fbm(w, h, 16, 2, 4, 101)
    rise = v ** 1.6
    foot = np.exp(-((v - 0.96) / 0.05) ** 2)
    heat = rise * (0.3 + 0.75 * streak) + foot * 0.9
    cover = v ** 1.2 * (0.45 + 0.5 * streak)
    save("wall", heat, cover, clamp=False)


def fissure():
    """Across a crack in the ground (v), molten in the middle; u along it tiles."""
    w, h = 128, 32
    u, v = uv(w, h)
    d = np.abs(v - 0.5) * 2
    n = fbm(w, h, 8, 1, 3, 111)
    heat = np.exp(-(d / (0.16 + 0.1 * n)) ** 2) * 1.1
    cover = np.exp(-(d / 0.5) ** 2) * (0.6 + 0.3 * n)
    save("fissure", heat, cover, clamp=False)


def ray():
    """One spike of a burst: u from its root (0) to its tip (1), v across."""
    w, h = 128, 32
    u, v = uv(w, h)
    d = np.abs(v - 0.5) * 2
    heat = (1 - u) ** 1.1 * np.exp(-(d / 0.3) ** 2) * 1.1
    cover = (1 - u) ** 0.8 * np.exp(-(d / 0.55) ** 2) * 0.75
    save("ray", heat, cover)


def aura():
    """Tongues of power rising off a body: u up one tongue (tiles, scroll it to make them rise), v across it."""
    w, h = 256, 64
    u, v = uv(w, h)
    d = np.abs(v - 0.5) * 2
    n = fbm(w, h, 8, 3, 4, 121)
    wisp = smooth(0.3, 0.85, n)
    heat = wisp * np.exp(-(d / 0.35) ** 2) * 0.9 + np.exp(-(d / 0.12) ** 2) * 0.25 * n
    cover = np.exp(-(d / 0.55) ** 2) * (0.25 + 0.6 * wisp)
    save("aura", heat, cover, clamp=False)


def orb():
    """A ball of energy seen from any side: a hot core, a body, a bright rim and swirls inside."""
    w = h = 128
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    x, y = np.meshgrid(xs, xs)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    swirl = np.zeros_like(r)
    rng = np.random.default_rng(131)
    for i in range(3):
        k = rng.uniform(2.5, 4.5)
        swirl += 0.5 + 0.5 * np.sin(ang * (i + 2) + r * k * 3 + rng.uniform(0, 6.28))
    swirl /= 3
    disc = smooth(1.0, 0.9, r)
    rim = np.exp(-((r - 0.86) / 0.08) ** 2)
    core = np.exp(-(r / 0.35) ** 2)
    heat = (core * 1.1 + rim * 0.7 + swirl * 0.35 * (1 - r)) * disc
    cover = disc * (0.75 + 0.2 * swirl)
    save("orb", heat, cover)


def flare():
    """A hard star of light: four long points and four short, a hot middle."""
    w = h = 128
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    x, y = np.meshgrid(xs, xs)
    r = np.sqrt(x * x + y * y) + 1e-6
    ang = np.arctan2(y, x)
    long = np.abs(np.cos(ang * 2)) ** 60
    short = np.abs(np.cos(ang * 2 + np.pi / 2)) ** 60
    spikes = long * np.clip(1 - r, 0, 1) ** 1.5 + short * np.clip(1 - r * 1.8, 0, 1) ** 1.5
    core = np.exp(-(r / 0.12) ** 2)
    halo = np.exp(-(r / 0.4) ** 2) * 0.3
    heat = spikes * 1.1 + core * 1.1 + halo
    cover = np.clip(spikes * 0.6 + core * 0.8 + halo * 0.8, 0, 1)
    save("flare", heat, cover)


def scorch():
    """A burnt patch of ground, blended over it (its own dark colour, not light)."""
    w = h = 128
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    x, y = np.meshgrid(xs, xs)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    n = fbm(w, h, 6, 6, 4, 141)
    edge = 0.72 + 0.22 * np.sin(ang * 5 + n * 3) * 0.5 + (n - 0.5) * 0.3
    a = smooth(edge, edge - 0.35, r) * (0.75 + 0.25 * n)
    grey = 0.06 + 0.1 * n
    rgb = np.stack([grey * 1.1, grey * 0.95, grey * 0.85], -1)
    save("scorch", np.zeros_like(a), a, rgb=rgb)


def smoke():
    """A puff of smoke or dust, blended over the world in its own grey (tinted by the vertex colour)."""
    w = h = 128
    xs = (np.arange(w) + 0.5) / w * 2 - 1
    x, y = np.meshgrid(xs, xs)
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    n = fbm(w, h, 5, 5, 5, 151)
    lumps = 0.78 + 0.12 * np.sin(ang * 3 + n * 4) + (n - 0.5) * 0.35
    a = smooth(lumps, lumps - 0.55, r) * (0.55 + 0.45 * n)
    grey = 0.62 + 0.3 * n
    rgb = np.stack([grey, grey * 0.97, grey * 0.93], -1)
    save("smoke", np.zeros_like(a), a, rgb=rgb)


def preview(path):
    """All the skins on one sheet: each as light (heat) over its cover, on dark and on light ground."""
    tiles = []
    for name, rgba in SAVED:
        img = rgba
        h, w = img.shape[:2]
        scale = 128 / max(h, w)
        pic = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA").resize(
            (max(1, int(w * scale)), max(1, int(h * scale))), Image.BILINEAR)
        arr = np.asarray(pic, np.float32) / 255
        rows = []
        for ground in (0.12, 0.75):
            heat, cover = arr[..., 0], arr[..., 3]
            tint = np.array([1.0, 0.45, 0.15])
            body = tint[None, None, :] * cover[..., None]
            light = (tint[None, None, :] ** (1 - smooth(0.42, 0.92, heat))[..., None]) * heat[..., None] * 1.5
            col = np.clip(ground * (1 - cover[..., None]) + body + light, 0, 1)
            canvas = np.full((128, 128, 3), ground, np.float32)
            ph, pw = col.shape[:2]
            canvas[:ph, :pw] = col
            rows.append(canvas)
        tiles.append(np.concatenate(rows, 0))
    sheet = np.concatenate(tiles, 1)
    Image.fromarray((sheet * 255).astype(np.uint8), "RGB").save(path)


if __name__ == "__main__":
    blade()
    beam()
    lightning()
    plasma()
    fire()
    water()
    wind()
    hexes()
    wall()
    fissure()
    ray()
    aura()
    orb()
    flare()
    scorch()
    smoke()
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("wrote", len(SAVED), "skins to", OUT)
