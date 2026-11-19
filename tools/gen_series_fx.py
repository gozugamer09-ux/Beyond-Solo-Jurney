#!/usr/bin/env python3
"""Pictures for the series look of shadows, monarchs and rulers (textures/effect/series), drawn by the energy shader
(shaders/core/energy.fsh): the red channel is heat (light running from the effect's colour to white where it is
hottest), the alpha is cover (a body of the vertex colour that hides what is behind it).

The language they follow is /mnt/project-files/art-style/STYLE-GUIDE.md: dark power is black matter with a burning
edge, energy is thin and sharp (flame tongues, slits, threads), never a soft ball, and dark smoke is ink.

  flame.png       not heat and cover: two layers of tiling noise (red, green) that the flame shader
                  (shaders/core/flame.fsh) lifts through a figure's flame curtain and tears into tongues
  seam.png        light through the seams of ink: a thread broken into bright stretches and dark gaps, tiling along u
  wisp.png        4 ink wisps stacked (256 x 64 each, source on the left): thick at the source, tearing into threads
  pool.png        a pool of ink seen from above: a ragged black disc with a thin hot rim and a few drops round it
  splash.png      ink thrown out over the ground: a ragged ring of ink, its front thick, with a faint edge of the hue
  edge.png        the burning edge laid along a silhouette: a hot thread with a short bloom, across v
  trail.png       a comet tail (an eye's streak): hot at the head (u = 0), thinning to nothing at the tail
  spark.png       a hot point with four short glints: an eye, a mote of light
  feather.png     a feather of light (a ruler's wing): a white spine, a blade of light fading to nothing at the tip,
                  with a faint body of the light so it reads by day
  halo.png        a broken halo: segments, spokes and thin rings, to be turned slowly (a ruler's halo, its seals, a
                  rift's rings); its lines have a little body, the space between them none
  vein.png        veins of light running through ink, tiling both ways: a domain's ground
  shaft.png       a column of ink pouring down with a burning edge each side, tiling along v (a monarch's gate)

Run from the repo root:  python3 tools/gen_series_fx.py [--preview <png>]
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/beyondsolo/textures/effect/series"
SAVED = []


def save(name, heat, cover, clamp=True):
    """heat, cover: float arrays (h, w) in 0..1."""
    os.makedirs(OUT, exist_ok=True)
    h, w = heat.shape
    out = np.zeros((h, w, 4), np.float32)
    out[..., 0] = out[..., 1] = out[..., 2] = np.clip(heat, 0, 1)
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
    """Value noise in 0..1 that wraps round both edges."""
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
    n = total / norm
    lo, hi = np.percentile(n, 1), np.percentile(n, 99)
    return np.clip((n - lo) / (hi - lo), 0, 1)


def save_rg(name, red, green, clamp=False):
    """Two greyscale layers in one texture (red, green), for a shader that reads them itself."""
    os.makedirs(OUT, exist_ok=True)
    h, w = red.shape
    out = np.zeros((h, w, 4), np.float32)
    out[..., 0] = np.clip(red, 0, 1)
    out[..., 1] = np.clip(green, 0, 1)
    out[..., 3] = 1
    img = Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")
    img.save(os.path.join(OUT, name + ".png"), optimize=True)
    with open(os.path.join(OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"texture": {"blur": True, "clamp": clamp}}, f)


def flame():
    """Tiling noise, finer across than up, so what the threshold leaves of it is tall tongues and not round blobs."""
    s = 128
    big = fbm(s, s, 8, 4, 4, 701, gain=0.5)
    fine = fbm(s, s, 16, 6, 3, 709, gain=0.55)
    save_rg("flame", big, fine)


def seam():
    """Light showing through a seam: a hot thread that breaks into bright stretches and dark gaps along its length."""
    w, h = 256, 16
    along = fbm(w, 1, 9, 1, 3, 801, gain=0.5)[0]
    lit = smooth(0.35, 0.70, along) * (0.55 + 0.45 * smooth(0.6, 0.95, along))
    ys = (np.arange(h) + 0.5) / h - 0.5
    d = np.abs(ys) * 2
    core = np.exp(-(d / 0.18) ** 2) * 0.70 + np.exp(-(d / 0.55) ** 2) * 0.22
    save("seam", np.clip(core[:, None] * lit[None, :], 0, 1), np.zeros((h, w)), clamp=False)


def wisps():
    w, h, n = 256, 64, 4
    heat = np.zeros((h * n, w))
    cover = np.zeros((h * n, w))
    for i in range(n):
        r = np.random.default_rng(300 + i)
        ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
        u = (xs + 0.5) / w  # 0 at the source, 1 at the tail
        v = (ys + 0.5) / h - 0.5
        wave = 0.10 * np.sin(u * (5 + 3 * r.random()) + r.random() * 6.28) * u
        # thick at the source, splitting into two or three threads that thin to nothing
        threads = 2 + (i % 2)
        body = np.zeros_like(u)
        for t in range(threads):
            off = (t - (threads - 1) / 2) * 0.16 * smooth(0.25, 1.0, u)
            off += 0.05 * np.sin(u * (9 + 4 * r.random()) + r.random() * 6.28) * u
            width = 0.30 * (1 - u) ** 1.3 + 0.012
            width *= (1.0 if t == 0 else 0.8) * (1 - 0.6 * smooth(0.0, 0.5, u) * (t > 0))
            d = np.abs(v - wave - off) / width
            body = np.maximum(body, smooth(1.0, 0.55, d))
        grain = fbm(w, h, 8, 2, 4, 310 + i)
        body *= 0.75 + 0.25 * grain
        fade = smooth(0.0, 0.05, u) * smooth(1.0, 0.70, u)
        cover[i * h:(i + 1) * h] = body * fade
        # a faint edge of the hue where the wisp tears off its body
        edge = smooth(0.3, 0.7, body) * smooth(1.0, 0.7, body)
        heat[i * h:(i + 1) * h] = edge * 0.35 * smooth(0.35, 0.0, u)
    save("wisp", heat, cover)


def pool():
    s = 128
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float64)
    x = (xs + 0.5) / s * 2 - 1
    y = (ys + 0.5) / s * 2 - 1
    rad = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    # a ragged edge: noise round the circle
    edge = (0.74 + 0.05 * np.sin(ang * 3 + 1.3) + 0.035 * np.sin(ang * 7 + 0.4) + 0.02 * np.sin(ang * 13 + 2.1)
            + 0.012 * np.sin(ang * 29 + 4.0))
    d = rad / edge
    body = smooth(1.0, 0.94, d)
    # drops thrown round it
    r = np.random.default_rng(77)
    for _ in range(9):
        a = r.random() * 6.28
        dist = 0.80 + 0.12 * r.random()
        cx, cy = math.cos(a) * dist, math.sin(a) * dist
        rr = 0.03 + 0.04 * r.random()
        dd = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / rr
        body = np.maximum(body, smooth(1.0, 0.7, dd))
    # a thin rim, burning in the hue rather than white, and in arcs rather than a whole ring
    rim = smooth(0.86, 0.975, d) * smooth(1.02, 0.975, d)
    burn = 0.5 + 0.3 * np.sin(ang * 4 + 0.9) + 0.2 * np.sin(ang * 9 + 2.7)
    heat = rim * 0.7 * (0.2 + 0.8 * smooth(0.3, 0.8, burn))
    cover = body * 0.96
    save("pool", heat, cover)


def splash():
    """Ink thrown out over the ground: a thin ragged ring of ink torn into streaks and gaps, drops flung past its front,
    and a broken edge of the hue along the front. Its front stands at 0.86 of the half-width (Aura.splash)."""
    s = 256
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float64)
    x = (xs + 0.5) / s * 2 - 1
    y = (ys + 0.5) / s * 2 - 1
    rad = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    front = 0.86 + 0.04 * np.sin(ang * 5 + 0.7) + 0.025 * np.sin(ang * 11 + 2.2) + 0.015 * np.sin(ang * 23 + 1.1)
    d = rad / front
    # a thin band behind the front, trailing off inward in streaks
    streaks = 0.5 + 0.5 * np.sin(ang * 37 + 3 * np.sin(ang * 5))
    back = 0.80 - 0.10 * streaks
    body = smooth(1.0, 0.96, d) * smooth(back - 0.08, back + 0.06, d)
    # torn: the band thins to almost nothing in places
    tear = 0.5 + 0.35 * np.sin(ang * 13 + 1.7) + 0.15 * np.sin(ang * 29 + 0.3)
    body *= 0.3 + 0.7 * smooth(0.25, 0.6, tear)
    # drops flung past the front
    r = np.random.default_rng(91)
    for _ in range(28):
        a = r.random() * 6.28
        dist = (1.0 + 0.12 * r.random()) * 0.86
        cx, cy = math.cos(a) * dist, math.sin(a) * dist
        rr = 0.012 + 0.022 * r.random()
        dd = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / rr
        body = np.maximum(body, smooth(1.0, 0.6, dd) * 0.9)
    edge = smooth(0.93, 0.985, d) * smooth(1.02, 0.985, d) * (0.3 + 0.7 * smooth(0.35, 0.75, tear))
    save("splash", edge * 0.45, body * 0.95)


def edge():
    w, h = 16, 64
    ys = (np.arange(h) + 0.5) / h - 0.5
    d = np.abs(ys) * 2  # 0 at the middle, 1 at the sides
    # a hot thread (not quite white: the hue still shows in it) with a short bloom of the hue
    core = np.exp(-(d / 0.14) ** 2) * 0.72
    bloom = np.exp(-(d / 0.5) ** 2) * 0.30
    heat = np.repeat((core + bloom)[:, None], w, axis=1)
    save("edge", np.clip(heat, 0, 1), np.zeros((h, w)), clamp=False)


def trail():
    w, h = 128, 32
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    u = (xs + 0.5) / w
    v = np.abs((ys + 0.5) / h - 0.5) * 2
    width = 0.75 * (1 - u) ** 1.2 + 0.04
    core = np.exp(-(v / (width * 0.45)) ** 2)
    heat = core * (1 - u) ** 0.9 * smooth(0.0, 0.03, u + 0.02)
    save("trail", np.clip(heat * 1.05, 0, 1), np.zeros((h, w)))


def spark():
    s = 64
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float64)
    x = (xs + 0.5) / s * 2 - 1
    y = (ys + 0.5) / s * 2 - 1
    rad = np.sqrt(x * x + y * y)
    core = np.exp(-(rad / 0.16) ** 2)
    halo = np.exp(-(rad / 0.45) ** 2) * 0.35
    glint = (np.exp(-(np.abs(x) / 0.035) ** 2) * np.exp(-(np.abs(y) / 0.85) ** 2)
             + np.exp(-(np.abs(y) / 0.035) ** 2) * np.exp(-(np.abs(x) / 0.85) ** 2)) * 0.55
    heat = np.clip(core + halo + glint, 0, 1) * smooth(1.0, 0.8, rad)
    save("spark", heat, np.zeros((s, s)))


def feather():
    w, h = 64, 256
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    along = 1 - (ys + 0.5) / h  # 0 at the quill (bottom), 1 at the tip
    x = (xs + 0.5) / w - 0.5
    # a long blade, widest a quarter of the way, a needle at the tip
    prof = np.where(along < 0.25, 0.55 + 0.45 * along / 0.25, ((1 - along) / 0.75) ** 0.8)
    half = 0.42 * prof + 1e-4
    lean = 0.06 * along ** 2
    d = np.abs(x - lean) / half
    inside = smooth(1.0, 0.8, d)
    spine = np.exp(-(np.abs(x - lean) / 0.035) ** 2) * (1 - 0.4 * along)
    # barbs: thin slanted lines of light across the vane
    barbs = 0.5 + 0.5 * np.sin((along * 46 + np.abs(x - lean) * 30) * 1.0)
    vane = inside * (0.38 + 0.22 * barbs) * (1 - along) ** 0.6
    edge_l = smooth(0.70, 0.95, d) * inside * 0.55
    heat = np.clip(spine + vane + edge_l, 0, 1) * smooth(0.0, 0.04, along) * smooth(1.0, 0.82, along)
    # a body of the light too (the vertex colour), so the feather still reads against a bright sky
    cover = inside * (0.22 + 0.2 * spine) * (1 - along) ** 0.8
    save("feather", heat, cover)


def halo():
    s = 256
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float64)
    x = (xs + 0.5) / s * 2 - 1
    y = (ys + 0.5) / s * 2 - 1
    rad = np.sqrt(x * x + y * y)
    ang = (np.arctan2(y, x) + math.pi) / (2 * math.pi)  # 0..1 round
    heat = np.zeros((s, s))

    def ring(r0, width, strength, segs=0, gap=0.0, phase=0.0):
        band = np.exp(-((rad - r0) / width) ** 2)
        if segs:
            k = (ang * segs + phase) % 1.0
            band *= smooth(0.0, 0.02, k) * smooth(1.0 - gap, 1.0 - gap - 0.02, k)
        return band * strength

    heat += ring(0.92, 0.012, 1.0, segs=3, gap=0.18, phase=0.1)
    heat += ring(0.84, 0.008, 0.75, segs=12, gap=0.35)
    heat += ring(0.70, 0.010, 0.9, segs=5, gap=0.22, phase=0.3)
    heat += ring(0.56, 0.006, 0.5)
    # spokes between the rings
    spokes = 16
    k = (ang * spokes) % 1.0
    sp = np.exp(-((k - 0.5) / 0.012) ** 2) * smooth(0.58, 0.62, rad) * smooth(0.86, 0.82, rad)
    heat += sp * 0.8
    # little diamonds on the outer ring
    for i in range(6):
        a = i / 6 * 2 * math.pi + 0.3
        cx, cy = math.cos(a) * 0.92, math.sin(a) * 0.92
        dd = (np.abs(x - cx) + np.abs(y - cy)) / 0.05
        heat += smooth(1.0, 0.5, dd) * 0.9
    # its lines have a little body of the light (never the space between them), so it reads by day
    save("halo", np.clip(heat, 0, 1), np.clip(heat, 0, 1) * 0.45)


def veins():
    s = 256
    n1 = fbm(s, s, 4, 4, 5, 501)
    n2 = fbm(s, s, 6, 6, 4, 502)
    ridge1 = 1 - np.abs(n1 * 2 - 1)
    ridge2 = 1 - np.abs(n2 * 2 - 1)
    # thin bright lines along the ridges, the thicker ones brighter, nothing in between
    lines = smooth(0.955, 0.995, ridge1) * 1.0 + smooth(0.965, 0.995, ridge2) * 0.55
    glow = smooth(0.85, 1.0, ridge1) * 0.18
    # the ink the veins run through: pooled thicker in places, thinner along the veins where the light shows
    ink = (0.55 + 0.3 * fbm(s, s, 3, 3, 4, 503)) * (1 - 0.6 * np.clip(lines, 0, 1))
    save("vein", np.clip(lines + glow, 0, 1), ink, clamp=False)


def shaft():
    """A column of ink pouring down (a monarch's gate, the beacon over a dark threat): ink in the middle torn into
    streaks that run along it, a thin burning edge down both sides. Tiles along v: scroll it and the ink pours."""
    w, h = 64, 256
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float64)
    u = (xs + 0.5) / w
    d = np.abs(u - 0.5) * 2  # 0 in the middle, 1 at the sides
    streaks = fbm(w, h, 12, 2, 3, 811)
    grain = fbm(w, h, 6, 8, 3, 823)
    width = 0.78 + 0.1 * (grain - 0.5)
    body = smooth(width, width - 0.2, d) * (0.55 + 0.45 * streaks)
    edge = smooth(width - 0.16, width - 0.04, d) * smooth(width + 0.06, width - 0.02, d)
    heat = edge * (0.45 + 0.4 * grain) + body * 0.06 * smooth(0.7, 1.0, streaks)
    save("shaft", heat, body * 0.92, clamp=False)


def preview(path):
    tiles = []
    for name, img in SAVED:
        h, w = img.shape[:2]
        scale = 256 / max(h, w)
        a = img[..., 3:4]
        heat = img[..., 0:1]
        # shown as the shader draws them: violet light over blue-black ink, on a grey ground
        ink = np.array([0.04, 0.045, 0.09])
        hue = np.array([0.61, 0.36, 1.0])
        light = (hue + (1 - hue) * smooth(0.42, 0.92, heat)) * heat * 1.5
        ground = np.full((h, w, 3), 0.55)
        col = ground * (1 - a) + ink * a + light
        im = Image.fromarray((np.clip(col, 0, 1) * 255).astype(np.uint8), "RGB")
        im = im.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)
        tiles.append((name, im))
    W = sum(t.width for _, t in tiles) + 10 * len(tiles)
    sheet = Image.new("RGB", (W, 270), (30, 30, 30))
    x = 0
    for _, t in tiles:
        sheet.paste(t, (x, 5))
        x += t.width + 10
    sheet.save(path)


if __name__ == "__main__":
    flame()
    seam()
    wisps()
    pool()
    splash()
    edge()
    trail()
    spark()
    feather()
    halo()
    veins()
    shaft()
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
