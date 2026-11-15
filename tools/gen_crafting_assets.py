#!/usr/bin/env python3
"""
Generates the crafting module's textures and models:

- monster cores (one per rank E..S) and boss cores: faceted crystal hearts in the rank's colour
- mana stones (shard, stone, greater stone) and the three mana vein blocks (16x16, rock with glowing seams)
- the six creature materials and the eight System potions
- item models, block models and blockstates

Run from the repo root:  python3 tools/gen_crafting_assets.py
Needs: pillow, numpy.
"""
import json
import math
import os

import numpy as np
from PIL import Image

import gen_combat_assets as cb
from gen_combat_assets import hexc, shade, outline, poly_mask, S

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
ITEM_OUT = os.path.join(ASSETS, "textures/item")
BLOCK_OUT = os.path.join(ASSETS, "textures/block")
MODEL_OUT = os.path.join(ASSETS, "models/item")
BLOCK_MODEL_OUT = os.path.join(ASSETS, "models/block")
STATE_OUT = os.path.join(ASSETS, "blockstates")

RANKS = {"e": "9e9e9e", "d": "7cc576", "c": "4fa3f7", "b": "b57cf7", "a": "f7b84f", "s": "f75a4f"}
MANA = hexc("5fd0ff")


def put(img, mask, color):
    img[mask] = color


def gradient(mask, base, top=0.35, bottom=-0.45):
    img = np.zeros((S, S, 4), np.uint8)
    ys, xs = np.nonzero(mask)
    if len(ys) == 0:
        return img
    y0, y1 = ys.min(), ys.max()
    for y, x in zip(ys, xs):
        t = (y - y0) / max(1, y1 - y0)
        img[y, x] = shade(base, top + (bottom - top) * t)
    return img


def circle(cx, cy, r):
    yy, xx = np.mgrid[0:S, 0:S]
    return np.hypot(xx + 0.5 - cx, yy + 0.5 - cy) < r


def monster_core(color, boss=False):
    base = hexc(color)
    img = np.zeros((S, S, 4), np.uint8)
    if boss:
        # a dark spiked setting behind the gem
        spikes = []
        for i in range(16):
            a = i / 16 * 2 * math.pi - math.pi / 2
            r = 14.5 if i % 2 == 0 else 10.5
            spikes.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
        m = poly_mask(spikes, S)
        put(img, m, shade(base, -0.75))
        put(img, m & ~circle(16, 16, 11.5), shade(base, -0.55))
    r = 9.5 if boss else 8.5
    gem_pts = [(16 + r * math.cos(a), 16 + r * 1.1 * math.sin(a)) for a in np.linspace(-math.pi / 2, 1.5 * math.pi, 7)[:-1]]
    gem = poly_mask(gem_pts, S)
    g = gradient(gem, base, 0.25, -0.5)
    img[gem] = g[gem]
    # facets: a lit upper-left half and a dark lower-right
    lit = poly_mask([gem_pts[0], gem_pts[4], gem_pts[5], (16, 16)], S) & gem
    for y, x in np.argwhere(lit):
        img[y, x] = shade(tuple(img[y, x]), 0.25)
    dark = poly_mask([gem_pts[1], gem_pts[2], gem_pts[3], (16, 16)], S) & gem
    for y, x in np.argwhere(dark):
        img[y, x] = shade(tuple(img[y, x]), -0.25)
    # the glowing heart
    put(img, circle(16, 16.5, 4.2) & gem, shade(base, 0.45))
    put(img, circle(16, 16.5, 2.4), shade(base, 0.8))
    put(img, circle(15, 15.5, 1.0), (255, 255, 255, 255))
    # dark veins in the crystal
    for (x0, y0, x1, y1) in [(10, 13, 13, 15), (22, 19, 19, 18), (14, 23, 15, 20)]:
        for t in np.linspace(0, 1, 6):
            x, y = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
            if gem[y, x]:
                img[y, x] = shade(base, -0.6)
    img[11, 13] = (255, 255, 255, 220)
    return outline(Image.fromarray(img))


def mana_shard():
    img = np.zeros((S, S, 4), np.uint8)
    pts = [(17, 4), (22, 12), (19, 28), (13, 27), (11, 13)]
    m = poly_mask(pts, S)
    img[m] = gradient(m, MANA, 0.4, -0.35)[m]
    facet = poly_mask([(17, 4), (16, 27), (13, 27), (11, 13)], S) & m
    for y, x in np.argwhere(facet):
        img[y, x] = shade(tuple(img[y, x]), 0.3)
    put(img, poly_mask([(16, 9), (17.5, 9), (17, 20), (15.5, 20)], S), (235, 252, 255, 255))
    return outline(Image.fromarray(img))


def crystal_cluster(n, big):
    img = np.zeros((S, S, 4), np.uint8)
    # a little rock base
    rock = poly_mask([(6, 24), (10, 21), (22, 21), (27, 24), (25, 28), (8, 28)], S)
    img[rock] = gradient(rock, hexc("4a4f5c"), 0.2, -0.4)[rock]
    spires = [(16, 3 if big else 6, 5 if big else 4)]
    if n >= 2:
        spires.append((10, 11, 3.5))
    if n >= 3:
        spires.append((22, 10, 3.5))
    for cx, top, half in sorted(spires, key=lambda s: -s[1]):
        pts = [(cx, top), (cx + half, top + half * 1.4), (cx + half * 0.9, 24), (cx - half * 0.9, 24), (cx - half, top + half * 1.4)]
        m = poly_mask(pts, S)
        col = MANA if not big else hexc("7fe4ff")
        img[m] = gradient(m, col, 0.45, -0.3)[m]
        lit = poly_mask([(cx, top), (cx, 24), (cx - half * 0.9, 24), (cx - half, top + half * 1.4)], S) & m
        for y, x in np.argwhere(lit):
            img[y, x] = shade(tuple(img[y, x]), 0.25)
    if big:
        put(img, circle(16, 14, 2.2), (255, 255, 255, 255))
        for (x, y) in [(8, 6), (25, 5), (27, 15)]:
            img[y, x] = (220, 250, 255, 255)
            img[y - 1, x] = img[y + 1, x] = img[y, x - 1] = img[y, x + 1] = (120, 220, 255, 200)
    return outline(Image.fromarray(img))


def fang():
    img = np.zeros((S, S, 4), np.uint8)
    pts = [(9, 6), (16, 5), (22, 9), (24, 16), (22, 23), (17, 28), (19, 21), (18, 14), (14, 10)]
    m = poly_mask(pts, S)
    img[m] = gradient(m, hexc("ece4d0"), 0.3, -0.35)[m]
    root = poly_mask([(8, 5), (16, 4), (15, 9), (9, 9)], S)
    img[root] = gradient(root, hexc("6a3a4a"), 0.1, -0.3)[root]
    return outline(Image.fromarray(img))


def silk():
    img = np.zeros((S, S, 4), np.uint8)
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot((xx - 15.5) / 1.1, yy - 16.5)
    ball = d < 10
    img[ball] = gradient(ball, hexc("e8eef0"), 0.2, -0.3)[ball]
    # wound threads
    for k in range(-9, 10, 3):
        line = (np.abs((xx - 15.5) * 0.6 + (yy - 16.5) - k) < 0.7) & ball
        img[line] = hexc("b8c4cc")
    tail = poly_mask([(23, 22), (28, 27), (27, 28), (22, 23)], S)
    img[tail] = hexc("dfe8ec")
    return outline(Image.fromarray(img))


def carapace():
    img = np.zeros((S, S, 4), np.uint8)
    pts = [(6, 12), (14, 6), (24, 7), (27, 15), (22, 25), (11, 26), (5, 19)]
    m = poly_mask(pts, S)
    img[m] = gradient(m, hexc("5a6ab0"), 0.3, -0.45)[m]
    for pts2 in ([(10, 13), (15, 9), (14, 17)], [(18, 10), (23, 11), (20, 18)], [(13, 20), (20, 19), (17, 24)]):
        cm = poly_mask(pts2, S)
        img[cm] = hexc("a8d8ff")
    return outline(Image.fromarray(img))


def essence():
    img = np.zeros((S, S, 4), np.uint8)
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx - 15.5, yy - 18)
    wisp = (d < 8) | poly_mask([(10, 14), (14, 3), (17, 10), (21, 5), (22, 14)], S)
    img[wisp] = gradient(wisp, hexc("9b6cf0"), 0.4, -0.3)[wisp]
    put(img, d < 4.5, hexc("d8c4ff"))
    put(img, d < 2, (255, 255, 255, 255))
    return outline(Image.fromarray(img))


def ashen_plate():
    img = np.zeros((S, S, 4), np.uint8)
    pts = [(6, 8), (26, 6), (27, 22), (16, 27), (5, 22)]
    m = poly_mask(pts, S)
    img[m] = gradient(m, hexc("8a8e96"), 0.35, -0.45)[m]
    # rivets and an ember crack
    for (x, y) in [(9, 10), (23, 9), (24, 20), (8, 20)]:
        img[y, x] = hexc("d8dce4")
    for t in np.linspace(0, 1, 14):
        x = int(round(11 + 10 * t))
        y = int(round(12 + 9 * t + 2 * math.sin(t * 7)))
        img[y, x] = hexc("ff9a3a")
    return outline(Image.fromarray(img))


def cinder():
    img = np.zeros((S, S, 4), np.uint8)
    pts = [(8, 14), (13, 8), (21, 8), (25, 15), (22, 24), (12, 25), (7, 20)]
    m = poly_mask(pts, S)
    img[m] = gradient(m, hexc("3a2626"), 0.15, -0.4)[m]
    yy, xx = np.mgrid[0:S, 0:S]
    glow = m & ((np.sin(xx * 0.9) + np.cos(yy * 1.1 + xx * 0.3)) > 0.9)
    img[glow] = hexc("ff8a2a")
    put(img, circle(16, 16, 3) & m, hexc("ffd24a"))
    return outline(Image.fromarray(img))


POTIONS = {
    "healing_draught": ("round", "ff5a6e"),
    "greater_healing_draught": ("flask", "ff3050"),
    "supreme_healing_elixir": ("tall", "ffc070"),
    "mana_draught": ("round", "4fa8ff"),
    "greater_mana_draught": ("flask", "7f9cff"),
    "stamina_tonic": ("vial", "9fe870"),
    "draught_of_might": ("vial", "ff8a3a"),
    "ironskin_draught": ("tall", "b8c4d8"),
    "elixir_of_rebirth": ("tall", "d8b8ff"),
}


# --------------------------------------------------------------------------------------------- vein blocks (16x16)

def vein_block(size, seed):
    """Returns (rock, seams): the rock base and the crystal seams on their own (the seams render fully lit)."""
    rng = np.random.default_rng(seed)
    n = 16
    base = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        for x in range(n):
            v = 48 + int(rng.integers(-8, 9))
            base[y, x] = (v, v + 2, v + 8, 255)
    seams = np.zeros((n, n, 4), np.uint8)
    # crystal seams: a few diagonal strokes, more and brighter for bigger veins
    col = [hexc("3fa8d8"), hexc("5fd0ff"), hexc("9fe8ff")][size]
    core = [hexc("8fdcff"), hexc("c8f2ff"), hexc("ffffff")][size]
    count = 3 + size * 2
    for i in range(count):
        x0, y0 = int(rng.integers(1, 15)), int(rng.integers(1, 15))
        length = int(rng.integers(2, 5))
        dx, dy = (1, -1) if rng.random() < 0.5 else (1, 1)
        for k in range(length):
            x, y = x0 + dx * k, y0 + dy * k
            if 0 <= x < n and 0 <= y < n:
                seams[y, x] = core if k == length // 2 else col
                if 0 <= x + 1 < n and seams[y, x + 1][3] == 0:
                    base[y, x + 1] = shade(col, -0.55)
    return Image.fromarray(base), Image.fromarray(seams)


def model(name):
    with open(os.path.join(MODEL_OUT, name + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/" + name}}, f, indent=2)
        f.write("\n")


def block_model(name):
    """A cube of rock with the seams laid over it as a second, fully lit cube (NeoForge per-element light)."""
    faces = lambda tex: {d: {"texture": tex, "uv": [0, 0, 16, 16]} for d in ("north", "south", "east", "west", "up", "down")}
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "textures": {"particle": "beyondsolo:block/" + name, "rock": "beyondsolo:block/" + name, "seams": "beyondsolo:block/" + name + "_seams"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: dict(f, cullface=d) for d, f in faces("#rock").items()}},
            {"from": [0, 0, 0], "to": [16, 16, 16], "neoforge_data": {"block_light": 15, "sky_light": 15},
             "shade": False, "faces": {d: dict(f, cullface=d) for d, f in faces("#seams").items()}},
        ],
    }
    with open(os.path.join(BLOCK_MODEL_OUT, name + ".json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    with open(os.path.join(MODEL_OUT, name + ".json"), "w") as f:
        json.dump({"parent": "beyondsolo:block/" + name}, f, indent=2)
        f.write("\n")
    with open(os.path.join(STATE_OUT, name + ".json"), "w") as f:
        json.dump({"variants": {"": {"model": "beyondsolo:block/" + name}}}, f, indent=2)
        f.write("\n")


def main():
    for d in (ITEM_OUT, BLOCK_OUT, MODEL_OUT, BLOCK_MODEL_OUT, STATE_OUT):
        os.makedirs(d, exist_ok=True)
    for r, color in RANKS.items():
        monster_core(color).save(os.path.join(ITEM_OUT, "monster_core_" + r + ".png"))
        model("monster_core_" + r)
        monster_core(color, boss=True).save(os.path.join(ITEM_OUT, "boss_core_" + r + ".png"))
        model("boss_core_" + r)
    mana_shard().save(os.path.join(ITEM_OUT, "mana_shard.png"))
    crystal_cluster(3, False).save(os.path.join(ITEM_OUT, "mana_stone.png"))
    crystal_cluster(3, True).save(os.path.join(ITEM_OUT, "greater_mana_stone.png"))
    for name in ("mana_shard", "mana_stone", "greater_mana_stone"):
        model(name)
    for name, fn in (("gloomfang_fang", fang), ("brood_silk", silk), ("shard_carapace", carapace),
                     ("hollow_essence", essence), ("ashen_plate", ashen_plate), ("ember_cinder", cinder)):
        fn().save(os.path.join(ITEM_OUT, name + ".png"))
        model(name)
    for name, (shape, color) in POTIONS.items():
        cb.bottle(shape, hexc(color)).save(os.path.join(ITEM_OUT, name + ".png"))
        model(name)
    for size, name in enumerate(("lesser_mana_vein", "mana_vein", "greater_mana_vein")):
        rock, seams = vein_block(size, 7 + size)
        rock.save(os.path.join(BLOCK_OUT, name + ".png"))
        seams.save(os.path.join(BLOCK_OUT, name + "_seams.png"))
        block_model(name)
    print("crafting assets written")


if __name__ == "__main__":
    main()
