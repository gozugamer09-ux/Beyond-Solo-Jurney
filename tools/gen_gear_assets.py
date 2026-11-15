#!/usr/bin/env python3
"""
Generates the gear module's textures (item grades, sets, enhancement, infusions):

- the six boss signature armor sets: item icons and armor model layers, built with the combat generator's armor
  shapes plus a few touches of their own (horns, a crown, ice spikes, a hood, molten seams)
- the forge's materials: refining stone, ward seal and one infusion crystal per element
- item models for all of them

Run from the repo root:  python3 tools/gen_gear_assets.py
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
ARMOR_OUT = os.path.join(ASSETS, "textures/models/armor")
MODEL_OUT = os.path.join(ASSETS, "models/item")

# name: (light, mid, dark, trim, accent), helmet touch
SETS = {
    "stonehide": ((hexc("c9b79a"), hexc("8a7a62"), hexc("4c4234"), hexc("d4a24c"), hexc("7a2a22")), "horns"),
    "mirefang": ((hexc("9cc27a"), hexc("4f7a3a"), hexc("243a1c"), hexc("d8d0b0"), hexc("8ee05a")), "fangs"),
    "rimeguard": ((hexc("e6f6ff"), hexc("8cc4e8"), hexc("3a6a94"), hexc("ffffff"), hexc("4fa8ff")), "spikes"),
    "gloomreaver": ((hexc("6a5a8a"), hexc("2e2444"), hexc("140f20"), hexc("9b7be0"), hexc("c8a8ff")), "hood"),
    "emberwrought": ((hexc("5a4440"), hexc("2c2020"), hexc("140c0c"), hexc("ff9a3a"), hexc("ffd24a")), "horns"),
    "sovereign": ((hexc("e05a6a"), hexc("9a1830"), hexc("4a0816"), hexc("ffd166"), hexc("ff3d5a")), "crown"),
}
PIECES = {"knight_helm": "helm", "knight_cuirass": "chestplate", "knight_greaves": "leggings", "knight_sabatons": "boots"}

ELEMENTS = {
    "fire": "ff7a2e", "water": "4fa8ff", "lightning": "b8e2ff", "air": "a8f0e0", "earth": "c9a35a",
    "shadow": "9b7be0", "light": "fff0a8", "nature": "8ce05a", "arcane": "7f9cff",
}


def touch_helmet(img, palette, touch):
    a = np.array(img)
    light, mid, dark, trim, accent = palette
    layer = np.zeros_like(a)

    def put(points, color):
        m = poly_mask(points, S)
        layer[m] = color

    if touch == "horns":
        put([(9, 10), (4, 3), (6, 2), (11, 8)], trim)
        put([(23, 10), (28, 3), (26, 2), (21, 8)], trim)
    elif touch == "fangs":
        put([(11, 25), (12, 31), (14, 25)], trim)
        put([(18, 25), (20, 31), (21, 25)], trim)
    elif touch == "spikes":
        for x in (10, 16, 22):
            put([(x - 2, 6), (x, 0), (x + 2, 6)], trim)
    elif touch == "hood":
        put([(7, 9), (16, 1), (25, 9), (26, 14), (6, 14)], dark)
    elif touch == "crown":
        put([(8, 7), (9, 1), (12, 5), (16, 0), (20, 5), (23, 1), (24, 7)], trim)
        put([(15, 3), (17, 3), (17, 5), (15, 5)], accent)
    under = layer[:, :, 3] > 0
    keep = a[:, :, 3] > 0
    out = a.copy()
    out[under & ~keep] = layer[under & ~keep]
    if touch == "hood":
        out[under] = layer[under]
    return outline(Image.fromarray(out))


def glow_seams(img, palette, amount):
    """Lights a few pixels in the accent colour: the molten seams and gems of the higher sets."""
    a = np.array(img)
    accent = palette[4]
    rng = np.random.default_rng(len(a.tobytes()) % 9973)
    solid = np.argwhere(a[:, :, 3] > 0)
    for y, x in solid[rng.choice(len(solid), size=min(amount, len(solid)), replace=False)]:
        if int(a[y, x, 0]) + int(a[y, x, 1]) + int(a[y, x, 2]) > 60:
            a[y, x] = accent
    return Image.fromarray(a)


def armor(name, palette, touch):
    icons = cb.armor_icons("knight", palette)
    out = {}
    for key, img in icons.items():
        piece = PIECES[key]
        if piece == "helm":
            img = touch_helmet(img, palette, touch)
        if name in ("emberwrought", "sovereign", "gloomreaver"):
            img = glow_seams(img, palette, 6)
        out[name + "_" + piece] = img
    l1, l2 = cb.armor_layers("knight", palette)
    if name in ("emberwrought", "sovereign"):
        l1 = glow_seams(l1, palette, 40)
        l2 = glow_seams(l2, palette, 20)
    return out, l1, l2


def shaded(mask, base, lit_dir=(-1, -1)):
    img = np.zeros((S, S, 4), np.uint8)
    ys, xs = np.nonzero(mask)
    if len(ys) == 0:
        return img
    y0, y1 = ys.min(), ys.max()
    for y, x in zip(ys, xs):
        t = (y - y0) / max(1, y1 - y0)
        c = shade(base, 0.3 - 0.6 * t)
        if x == 0 or not mask[y, x - 1] or y == 0 or not mask[y - 1, x]:
            c = shade(base, 0.5)
        elif x == S - 1 or not mask[y, x + 1]:
            c = shade(base, -0.45)
        img[y, x] = c
    return img


def refining_stone():
    rock = poly_mask([(7, 12), (12, 6), (21, 5), (27, 11), (26, 22), (19, 27), (9, 26), (5, 19)], S)
    img = shaded(rock, hexc("6c6f7c"))
    # glowing rune lines
    rune = poly_mask([(15, 9), (17, 9), (17, 23), (15, 23)], S) | poly_mask([(11, 15), (21, 15), (21, 17), (11, 17)], S)
    for y, x in np.argwhere(rune & rock):
        img[y, x] = hexc("ffe9a0")
    for y, x in np.argwhere(poly_mask([(14, 14), (18, 14), (18, 18), (14, 18)], S)):
        img[y, x] = hexc("fff8e0")
    return outline(Image.fromarray(img))


def ward_seal():
    yy, xx = np.mgrid[0:S, 0:S]
    d = np.hypot(xx - 15.5, yy - 15.5)
    disc = d < 12
    img = shaded(disc, hexc("2f6fd0"))
    ring = (d > 9) & (d < 11)
    img[ring] = hexc("bfe8ff")
    hexm = poly_mask([(16, 8), (22, 12), (22, 19), (16, 23), (10, 19), (10, 12)], S)
    inner = poly_mask([(16, 11), (19.5, 13.5), (19.5, 18), (16, 20.5), (12.5, 18), (12.5, 13.5)], S)
    img[hexm & ~inner] = hexc("e6f6ff")
    img[inner] = hexc("7fd8ff")
    return outline(Image.fromarray(img))


def crystal(color):
    base = hexc(color)
    body = poly_mask([(16, 3), (22, 10), (20, 26), (16, 29), (12, 26), (10, 10)], S)
    side = poly_mask([(9, 14), (12, 12), (12, 24), (8, 22)], S) | poly_mask([(23, 14), (20, 12), (20, 24), (24, 22)], S)
    img = shaded(side, shade(base, -0.2))
    b = shaded(body, base)
    m = b[:, :, 3] > 0
    img[m] = b[m]
    # a bright facet and core
    facet = poly_mask([(16, 4), (16, 28), (13, 25), (11, 11)], S)
    for y, x in np.argwhere(facet & body):
        img[y, x] = shade(base, 0.35)
    for y, x in np.argwhere(poly_mask([(15, 12), (17, 12), (17, 20), (15, 20)], S)):
        img[y, x] = shade(base, 0.75)
    return outline(Image.fromarray(img))


def model(name):
    with open(os.path.join(MODEL_OUT, name + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/" + name}}, f, indent=2)
        f.write("\n")


def main():
    for d in (ITEM_OUT, ARMOR_OUT, MODEL_OUT):
        os.makedirs(d, exist_ok=True)
    for name, (palette, touch) in SETS.items():
        icons, l1, l2 = armor(name, palette, touch)
        for item, img in icons.items():
            img.save(os.path.join(ITEM_OUT, item + ".png"))
            model(item)
        l1.save(os.path.join(ARMOR_OUT, name + "_layer_1.png"))
        l2.save(os.path.join(ARMOR_OUT, name + "_layer_2.png"))
    refining_stone().save(os.path.join(ITEM_OUT, "refining_stone.png"))
    model("refining_stone")
    ward_seal().save(os.path.join(ITEM_OUT, "ward_seal.png"))
    model("ward_seal")
    for name, color in ELEMENTS.items():
        crystal(color).save(os.path.join(ITEM_OUT, name + "_infusion_crystal.png"))
        model(name + "_infusion_crystal")
    print("gear assets written")


if __name__ == "__main__":
    main()
