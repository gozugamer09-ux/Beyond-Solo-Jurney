#!/usr/bin/env python3
"""
Builds the art for the random boxes and mana poisoning.

Run from the repo root:  python3 tools/gen_reward_assets.py
Needs: pillow, numpy (the same as tools/gen_combat_assets.py).

- The three random boxes as small 3D models (they are items, drawn like blocks): a body framed by metal corner posts,
  a lid that overhangs it, a seam of light between the two, a lock plate with a glowing keyhole on every side and a
  rune on the lid that glows. Plain: dark steel with cyan light. Blessed: ivory and gold with warm light and a gold
  crest on the lid. Cursed: black wood with bone-iron trim, crimson light and two chains crossed over the lid.
- The purifying crystal: a cluster of pale mana-glass shards rising out of a pool of light on the floor, fully lit.
- The purifying draught's bottle, the Hex of Frailty's icon (a cracked violet heart) and Clear Breath's (a pale
  swirl of wind).

English and Spanish text live by hand in assets/beyondsolo_boxes/lang and assets/beyondsolo_mana/lang.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_combat_assets as cb  # noqa: E402
from gen_combat_assets import hexc  # noqa: E402

ASSETS = "src/main/resources/assets/beyondsolo"
ITEM_TEX = f"{ASSETS}/textures/item"
BOX_TEX = f"{ASSETS}/textures/item/box"
BLOCK_TEX = f"{ASSETS}/textures/block"
ITEM_MODELS = f"{ASSETS}/models/item"
BLOCK_MODELS = f"{ASSETS}/models/block"
STATES = f"{ASSETS}/blockstates"
EFFECTS = f"{ASSETS}/textures/mob_effect"

LIT = {"block_light": 15, "sky_light": 15}

# body base, body dark, trim, trim dark, light, light core
BOXES = {
    "random_box": dict(body="2a3c5e", dark="141d31", trim="a9bdd8", trim_dark="4c5e7a", light="5fd0ff", core="d8f6ff", wood=False),
    "blessed_random_box": dict(body="e9e1cb", dark="b5a782", trim="e3b452", trim_dark="8a6224", light="ffd27a", core="fff7dc", wood=False),
    "cursed_random_box": dict(body="2a1520", dark="12080d", trim="8c7a74", trim_dark="3c3034", light="e0405a", core="ffc4cc", wood=True),
}


def rgba(h, a=255):
    return np.array(hexc(h, a), dtype=np.float32)


def mix(a, b, t):
    return a + (b - a) * t


def save(arr, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path, optimize=True)


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


# ================================================================================================ box textures

def box_side(p, seed):
    """A side panel: planks (cursed) or a riveted plate, darker towards the foot and the edges."""
    rng = np.random.default_rng(seed)
    img = np.zeros((16, 16, 4), np.float32)
    body, dark = rgba(p["body"]), rgba(p["dark"])
    for y in range(16):
        for x in range(16):
            down = y / 15
            edge = min(x - 3, 12 - x) if 3 <= x <= 12 else 0
            c = mix(body, dark, 0.15 + 0.45 * down ** 1.4)
            if edge <= 0:
                c = mix(c, dark, 0.45)
            if p["wood"]:
                if x in (5, 8, 11):
                    c = mix(c, dark, 0.55)            # the gaps between planks
                c = c * (0.92 + 0.12 * rng.random())  # grain
            else:
                if (x, y) in ((4, 9), (11, 9), (4, 14), (11, 14)):
                    c = mix(c, rgba(p["trim"]), 0.8)  # rivets
                if y == 11:
                    c = mix(c, dark, 0.35)            # a pressed line across the plate
                c = c * (0.96 + 0.05 * rng.random())
            c[3] = 255
            img[y, x] = c
    return img


def box_lid(p, seed):
    """The lid's sides (rows 4..8) and underside: a band a shade lighter than the body, a dark lip at the foot."""
    rng = np.random.default_rng(seed)
    img = np.zeros((16, 16, 4), np.float32)
    body, dark = rgba(p["body"]), rgba(p["dark"])
    for y in range(16):
        for x in range(16):
            c = mix(body, rgba("ffffff"), 0.08) if y < 7 else mix(body, dark, 0.5)
            if y == 4:
                c = mix(c, rgba("ffffff"), 0.15)      # a catch of light along the top edge
            if p["wood"]:
                c = c * (0.9 + 0.14 * rng.random())
            c[3] = 255
            img[y, x] = c
    return img


def box_top(p, seed):
    """The lid's top: a raised border and a sunken field; the rune that glows lies over it (box_rune)."""
    rng = np.random.default_rng(seed)
    img = np.zeros((16, 16, 4), np.float32)
    body, dark, trim = rgba(p["body"]), rgba(p["dark"]), rgba(p["trim"])
    for y in range(16):
        for x in range(16):
            d = min(x - 2, 13 - x, y - 2, 13 - y)
            c = mix(body, rgba("ffffff"), 0.1)
            if d <= 0:
                c = mix(trim, rgba(p["trim_dark"]), 0.3)
            elif d == 1:
                c = mix(c, dark, 0.35)               # the sunken field's shadowed edge
            else:
                c = mix(c, dark, 0.15)
            if p["wood"] and d > 0:
                c = c * (0.9 + 0.14 * rng.random())
            c[3] = 255
            img[y, x] = c
    return img


def box_trim(p):
    """Metal for the corner posts, lock plates and the crest: bright on one side, dark on the other."""
    img = np.zeros((16, 16, 4), np.float32)
    trim, td = rgba(p["trim"]), rgba(p["trim_dark"])
    for y in range(16):
        for x in range(16):
            t = (x % 4) / 3
            c = mix(mix(trim, rgba("ffffff"), 0.25), td, t * 0.8)
            if y % 8 == 0:
                c = mix(c, td, 0.4)
            c[3] = 255
            img[y, x] = c
    return img


def box_glow(p):
    """The light (fully lit): bright in the middle rows, the core colour at the heart."""
    img = np.zeros((16, 16, 4), np.float32)
    light, core = rgba(p["light"]), rgba(p["core"])
    for y in range(16):
        for x in range(16):
            k = 1 - abs(y - 7.5) / 8
            c = mix(light, core, max(0.0, k) ** 2)
            c[3] = 255
            img[y, x] = c
    return img


def box_rune(p, kind):
    """The rune on the lid (fully lit, see-through around it): a ring with a mark of the box's kind inside."""
    n = 16
    img = np.zeros((n, n, 4), np.float32)
    light, core = rgba(p["light"]), rgba(p["core"])
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 7.5, y - 7.5)
            if 4.2 <= r < 5.1:
                img[y, x] = light
    if kind == "random_box":
        # a question mark of light: what is inside is anyone's guess
        for x, y in ((6, 5), (7, 5), (8, 5), (9, 6), (9, 7), (8, 8), (7, 8), (7, 9)):
            img[y, x] = core
        img[11, 7] = core
    elif kind == "blessed_random_box":
        # a four-pointed star
        for k in range(-3, 4):
            img[7 + k if abs(k) < 3 else 7 + k, 7] = core if abs(k) < 2 else light
            img[7, 7 + k] = core if abs(k) < 2 else light
        img[8, 8] = img[6, 6] = img[6, 8] = img[8, 6] = light
    else:
        # an eye that does not close
        for x in range(5, 11):
            img[6 if 6 <= x <= 9 else 7, x] = light
            img[9 if 6 <= x <= 9 else 8, x] = light
        img[7, 7] = img[7, 8] = img[8, 7] = img[8, 8] = core
    return img


def chain_tex():
    """A chain's links for the cursed box's straps (see-through between them)."""
    img = np.zeros((16, 16, 4), np.float32)
    iron, dark = rgba("9a8c88"), rgba("3a3034")
    for y in range(16):
        for x in range(16):
            link = y % 4
            if link in (0, 3) and 1 <= x <= 2 or link in (1, 2) and x in (0, 3):
                img[y, x] = mix(iron, dark, 0.3 if x < 2 else 0.6)
    return img


# ================================================================================================ box models

def face(tex, uv, **extra):
    d = {"texture": tex, "uv": uv}
    d.update(extra)
    return d


def cube(fr, to, faces, lit=False, rotation=None):
    e = {"from": fr, "to": to, "faces": faces}
    if lit:
        e["shade"] = False
        e["neoforge_data"] = LIT
    if rotation:
        e["rotation"] = rotation
    return e


def box_model(name):
    sides = ("north", "south", "east", "west")
    body_side = face("#side", [3, 8, 13, 16])
    elements = [
        # the body
        cube([3, 0, 3], [13, 8, 13], {**{s: dict(body_side) for s in sides}, "down": face("#side", [3, 3, 13, 13], cullface="down")}),
        # the seam of light between body and lid
        cube([3.25, 8, 3.25], [12.75, 8.75, 12.75], {s: face("#glow", [3, 7, 13, 8]) for s in sides}, lit=True),
        # the lid
        cube([2.5, 8.75, 2.5], [13.5, 11.5, 13.5], {**{s: face("#lid", [2.5, 4, 13.5, 6.75]) for s in sides},
                                                    "up": face("#top", [2.5, 2.5, 13.5, 13.5]),
                                                    "down": face("#lid", [2.5, 8, 13.5, 16])}),
        # the rune on the lid
        cube([3, 11.52, 3], [13, 11.52, 13], {"up": face("#rune", [3, 3, 13, 13])}, lit=True),
    ]
    # corner posts
    for x0, z0 in ((2.6, 2.6), (12.2, 2.6), (2.6, 12.2), (12.2, 12.2)):
        elements.append(cube([x0, 0, z0], [x0 + 1.2, 8.75, z0 + 1.2], {s: face("#trim", [0, 0, 1.2, 8.75]) for s in sides + ("up",)}))
    # a lock plate with a glowing keyhole on every side
    plates = {
        "north": ([6.5, 3.5, 2.55], [9.5, 9.5, 3], [7.4, 4.6, 2.5], [8.6, 7.4, 2.55]),
        "south": ([6.5, 3.5, 13], [9.5, 9.5, 13.45], [7.4, 4.6, 13.45], [8.6, 7.4, 13.5]),
        "west": ([2.55, 3.5, 6.5], [3, 9.5, 9.5], [2.5, 4.6, 7.4], [2.55, 7.4, 8.6]),
        "east": ([13, 3.5, 6.5], [13.45, 9.5, 9.5], [13.45, 4.6, 7.4], [13.5, 7.4, 8.6]),
    }
    for side, (pf, pt, kf, kt) in plates.items():
        elements.append(cube(pf, pt, {s: face("#trim", [4, 2, 7, 8]) for s in sides + ("up", "down")}))
        elements.append(cube(kf, kt, {side: face("#glow", [7, 5, 8.2, 8])}, lit=True))
    textures = {
        "particle": f"beyondsolo:item/box/{name}_side",
        "side": f"beyondsolo:item/box/{name}_side",
        "lid": f"beyondsolo:item/box/{name}_lid",
        "top": f"beyondsolo:item/box/{name}_top",
        "trim": f"beyondsolo:item/box/{name}_trim",
        "glow": f"beyondsolo:item/box/{name}_glow",
        "rune": f"beyondsolo:item/box/{name}_rune",
    }
    if name == "blessed_random_box":
        # a gold crest on the lid, a gem of light at its heart
        elements.append(cube([6.5, 11.5, 6.5], [9.5, 12.5, 9.5], {s: face("#trim", [0, 0, 3, 1]) for s in sides}
                             | {"up": face("#trim", [4, 4, 7, 7])}))
        elements.append(cube([7.25, 12.5, 7.25], [8.75, 13.5, 8.75], {s: face("#glow", [7, 6, 8.5, 7]) for s in sides + ("up",)}, lit=True))
    if name == "cursed_random_box":
        # two chains crossed over the lid
        textures["chain"] = "beyondsolo:item/box/chain"
        for angle in (45, -45):
            elements.append(cube([7, 11.55, 0.4], [9, 11.85, 15.6],
                                 {"up": face("#chain", [0, 0, 4, 16]), "down": face("#chain", [0, 0, 4, 16])},
                                 rotation={"origin": [8, 11.7, 8], "axis": "y", "angle": angle}))
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": textures,
        "elements": elements,
        "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, 1, 0], "scale": [0.78, 0.78, 0.78]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.7, 0.7, 0.7]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.45, 0.45, 0.45]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
            "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [0.55, 0.55, 0.55]},
        },
    }
    write_json(os.path.join(ITEM_MODELS, name + ".json"), model)


def write_boxes():
    for i, (name, p) in enumerate(BOXES.items()):
        save(box_side(p, 11 + i), os.path.join(BOX_TEX, f"{name}_side.png"))
        save(box_lid(p, 21 + i), os.path.join(BOX_TEX, f"{name}_lid.png"))
        save(box_top(p, 31 + i), os.path.join(BOX_TEX, f"{name}_top.png"))
        save(box_trim(p), os.path.join(BOX_TEX, f"{name}_trim.png"))
        save(box_glow(p), os.path.join(BOX_TEX, f"{name}_glow.png"))
        save(box_rune(p, name), os.path.join(BOX_TEX, f"{name}_rune.png"))
        box_model(name)
    save(chain_tex(), os.path.join(BOX_TEX, "chain.png"))


# ================================================================================================ the purifying crystal

def crystal_tex():
    """A face of the pale crystal: deep teal at the foot, near white at the tip, a bright vein up the middle."""
    img = np.zeros((16, 16, 4), np.float32)
    deep, mid, pale = rgba("1f7f86"), rgba("67e3d6"), rgba("f0fffc")
    for y in range(16):
        up = 1 - y / 15
        for x in range(16):
            across = abs(x - 7.5) / 7.5
            c = mix(deep, mid, 0.3 + 0.7 * up ** 0.9)
            c = mix(c, pale, max(0.0, up - 0.55) * 1.4)
            c = c * (1 - 0.4 * across ** 1.6)
            if abs(x - 7.5) < 1 and y % 6 != 5:
                c = mix(c, pale, 0.5 + 0.3 * up)
            if (x + 2 * y) % 9 == 0 and 2 < x < 13:
                c = mix(c, pale, 0.35)
            c[3] = 255
            img[y, x] = c
    return img


def pool_tex():
    """The pool of light on the floor around the crystal (see-through at the edge)."""
    n = 16
    img = np.zeros((n, n, 4), np.float32)
    col = rgba("8ff5e6")
    for y in range(n):
        for x in range(n):
            r = math.hypot(x - 7.5, y - 7.5) / 7.5
            if r < 1:
                a = (1 - r) ** 1.5
                img[y, x] = [col[0], col[1], col[2], 220 * a]
    return img


def write_crystal():
    save(crystal_tex(), os.path.join(BLOCK_TEX, "purifying_crystal.png"))
    save(pool_tex(), os.path.join(BLOCK_TEX, "purifying_crystal_pool.png"))

    def shard(x0, z0, size, y1, angle_axis=None, angle=0, origin=None, turn=True):
        """One shard: a square column, turned 45 degrees about y, maybe tipped; its uv is the crystal texture's middle."""
        half = size / 2
        fr = [x0 - half, 0, z0 - half]
        to = [x0 + half, y1, z0 + half]
        side = {"texture": "#crystal", "uv": [8 - half, 16 - min(16, y1), 8 + half, 16]}
        faces = {d: dict(side) for d in ("north", "south", "east", "west")}
        faces["up"] = {"texture": "#crystal", "uv": [8 - half, 0, 8 + half, size]}
        e = {"from": fr, "to": to, "shade": False, "neoforge_data": LIT, "faces": faces}
        if angle_axis:
            e["rotation"] = {"origin": origin or [x0, 0, z0], "axis": angle_axis, "angle": angle}
        elif turn:
            e["rotation"] = {"origin": [x0, 0, z0], "axis": "y", "angle": 45}
        return e

    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"particle": "beyondsolo:block/purifying_crystal", "crystal": "beyondsolo:block/purifying_crystal",
                     "pool": "beyondsolo:block/purifying_crystal_pool"},
        "elements": [
            {"from": [-4, 0.05, -4], "to": [20, 0.05, 20], "shade": False, "neoforge_data": LIT,
             "faces": {"up": {"texture": "#pool", "uv": [0, 0, 16, 16]}}},
            shard(8, 8, 4, 15),
            shard(5, 9.5, 2.5, 9, "z", 22.5),
            shard(11, 6.5, 2.5, 10, "x", -22.5),
            shard(10.5, 11, 2, 7, "z", -22.5),
            shard(5.5, 5.5, 2, 6, "x", 22.5),
        ],
    }
    write_json(os.path.join(BLOCK_MODELS, "purifying_crystal.json"), model)
    write_json(os.path.join(STATES, "purifying_crystal.json"), {"variants": {"": {"model": "beyondsolo:block/purifying_crystal"}}})
    write_json(os.path.join(ITEM_MODELS, "purifying_crystal.json"), {"parent": "beyondsolo:block/purifying_crystal"})


# ================================================================================================ the draught and the icons

def write_draught():
    os.makedirs(ITEM_TEX, exist_ok=True)
    cb.bottle("tall", hexc("7fe8da")).save(os.path.join(ITEM_TEX, "purifying_draught.png"))
    write_json(os.path.join(ITEM_MODELS, "purifying_draught.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/purifying_draught"}})


def finish_icon(im, n, edge):
    """Down to size, with a dark 1px rim so it reads on any background."""
    im = im.resize((n, n), Image.LANCZOS)
    a = im.split()[3].point(lambda v: 255 if v > 50 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (n, n), edge), (0, 0), grown)
    out.alpha_composite(im)
    return out


def frailty_icon(n=18):
    """The Hex of Frailty: a violet heart split by a crack."""
    ss = 8
    w = n * ss
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = w / 2
    pts = []
    for i in range(80):
        t = i / 80 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((c + x * ss * 0.44, c - y * ss * 0.44 - 0.5 * ss))
    d.polygon(pts, fill=(138, 43, 226, 255))
    inner = [(c + (x - c) * 0.72, c + (y - c) * 0.72 - ss * 0.6) for x, y in pts]
    d.polygon(inner, fill=(190, 120, 255, 255))
    crack = [(c - 0.5 * ss, c - 6 * ss), (c + 1.2 * ss, c - 2.5 * ss), (c - 1.2 * ss, c + 0.5 * ss), (c + 0.8 * ss, c + 3.5 * ss), (c, c + 6.5 * ss)]
    d.line(crack, fill=(30, 8, 46, 255), width=int(1.6 * ss))
    return finish_icon(im, n, (34, 10, 52, 255))


def clear_breath_icon(n=18):
    """Clear Breath: a pale swirl of wind."""
    ss = 8
    w = n * ss
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = w / 2
    for k, (r0, col) in enumerate(((6.5, (127, 232, 218, 255)), (4.2, (200, 255, 246, 255)))):
        pts = []
        for i in range(60):
            t = i / 60 * 1.6 * math.pi + k * math.pi
            r = (r0 - i / 60 * (r0 - 1.5)) * ss
            pts.append((c + math.cos(t) * r, c + math.sin(t) * r))
        d.line(pts, fill=col, width=int(1.7 * ss), joint="curve")
    d.ellipse([c - 1.4 * ss, c - 1.4 * ss, c + 1.4 * ss, c + 1.4 * ss], fill=(240, 255, 252, 255))
    return finish_icon(im, n, (14, 60, 64, 255))


def write_icons():
    os.makedirs(EFFECTS, exist_ok=True)
    frailty_icon().save(os.path.join(EFFECTS, "hex_of_frailty.png"), optimize=True)
    clear_breath_icon().save(os.path.join(EFFECTS, "clear_breath.png"), optimize=True)


def main():
    write_boxes()
    write_crystal()
    write_draught()
    write_icons()
    print("  boxes: random_box, blessed_random_box, cursed_random_box; block: purifying_crystal;"
          " item: purifying_draught; effects: hex_of_frailty, clear_breath")


if __name__ == "__main__":
    main()
