#!/usr/bin/env python3
"""
Generates the skill module's textures:

- light textures for skill effects: a streak (bolts, shots, light columns) and a crescent (slashes)
- the skill tome item sprite (32x32 pixel art)

Skill icons are made by tools/gen_ui_assets.py (entries "skill_<path>" in ICONS, Phosphor SVGs in tools/icons-src).

Run from the repo root:  python3 tools/gen_skill_assets.py
Needs: pillow, numpy.
"""
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
FX_OUT = os.path.join(ASSETS, "textures/effect")
ITEM_OUT = os.path.join(ASSETS, "textures/item")


def save_light(img, name):
    img.save(os.path.join(FX_OUT, name + ".png"), optimize=True)
    with open(os.path.join(FX_OUT, name + ".png.mcmeta"), "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


def streak(w=256, h=32):
    """Bright core along the length (u), soft falloff across (v), soft ends."""
    u = np.linspace(0, 1, w)[None, :]
    v = np.linspace(-1, 1, h)[:, None]
    across = np.exp(-(v / 0.32) ** 2) * 0.75 + np.exp(-(v / 0.09) ** 2) * 0.6
    ends = np.clip(np.minimum(u, 1 - u) / 0.08, 0, 1)
    a = np.clip(across * ends, 0, 1)
    rgba = np.zeros((h, w, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba)


def crescent(size=256):
    """A slash: an arc of about 150 degrees around the top of the texture, thick in the middle, sharp at the tips."""
    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32)
    cx = cy = (size - 1) / 2
    dx = (xs - cx) / cx
    dy = (cy - ys) / cy  # up = +
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.degrees(np.arctan2(dx, dy))  # 0 at the top
    span = 75.0
    t = np.clip(1 - np.abs(ang) / span, 0, 1)  # 1 in the middle, 0 at the tips
    width = 0.03 + 0.16 * t ** 0.7
    edge = 0.86
    d = np.abs(r - (edge - width / 2)) / (width / 2)
    core = np.clip(1 - d, 0, 1) ** 1.5
    glow = np.exp(-((r - edge + width / 2) / (width + 0.05)) ** 2) * 0.35 * t
    # the leading edge (outside) is brighter, the inside trails off
    lead = np.clip((r - (edge - width)) / width, 0, 1) * 0.5 + 0.5
    a = np.clip(core * lead + glow, 0, 1) * (t > 0)
    rgba = np.zeros((size, size, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(rgba)


def tome():
    """A dark blue tome with gold corners and a glowing rune."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    cover = (28, 44, 92, 255)
    cover_d = (16, 26, 58, 255)
    cover_l = (52, 76, 140, 255)
    pages = (232, 224, 200, 255)
    pages_d = (190, 178, 150, 255)
    gold = (238, 196, 92, 255)
    gold_d = (170, 124, 48, 255)
    rune = (120, 220, 255, 255)
    rune_l = (210, 248, 255, 255)
    x0, x1, y0, y1 = 6, 25, 4, 27
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = cover
            if x == x0 or y == y0:
                c = cover_l
            if x == x1 or y == y1:
                c = cover_d
            p[x, y] = c
    # pages on the right and bottom edges
    for y in range(y0 + 1, y1 + 2):
        p[x1 + 1, y] = pages if y % 2 else pages_d
    for x in range(x0 + 1, x1 + 2):
        p[x, y1 + 1] = pages if x % 2 else pages_d
    # spine
    for y in range(y0, y1 + 1):
        p[x0 + 1, y] = cover_d
        p[x0 + 2, y] = (40, 60, 118, 255)
    # gold corners
    for (cx, cy, sx, sy) in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        for i in range(3):
            p[cx + sx * i, cy] = gold if i < 2 else gold_d
            p[cx, cy + sy * i] = gold if i < 2 else gold_d
    # rune: a diamond with a cross
    mx, my = 16, 15
    for i in range(-5, 6):
        for j in range(-5, 6):
            if abs(i) + abs(j) == 5:
                p[mx + i, my + j] = rune
    for i in range(-3, 4):
        p[mx + i, my] = rune_l if i == 0 else rune
        p[mx, my + i] = rune_l if i == 0 else rune
    p[mx, my] = (255, 255, 255, 255)
    # a little glow around the rune
    for i in range(-6, 7):
        for j in range(-6, 7):
            if abs(i) + abs(j) == 6 and p[mx + i, my + j][3] > 0 and p[mx + i, my + j] == cover:
                p[mx + i, my + j] = (44, 84, 150, 255)
    return img


def main():
    os.makedirs(FX_OUT, exist_ok=True)
    os.makedirs(ITEM_OUT, exist_ok=True)
    save_light(streak(), "streak")
    save_light(crescent(), "slash")
    tome().save(os.path.join(ITEM_OUT, "skill_tome.png"))
    print("skill assets written")


if __name__ == "__main__":
    main()
