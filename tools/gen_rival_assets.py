#!/usr/bin/env python3
"""
The world's other hunters and the hands for hire (see com.beyondsolo.rival):

- porter_0..2: the porters a hunter can hire at the association's office, skins in the player's shape (64x64, the
  game's player layout with both layers), in work clothes for carrying a cave's haul out of it:
    0  a young one: a mustard knit beanie over a dark mop, an olive canvas jacket with patched elbows over a grey
       hoodie, khaki cargo trousers with knee pads, fingerless work gloves, brown boots;
    1  a seasoned one: black hair tied under a rust bandana, a charcoal utility vest full of pockets over a cream shirt
       with the sleeves rolled up, tan leather gloves, navy cargo trousers, laced boots;
    2  an old hand: close grey hair and a grey beard, a miner's lamp strapped on, a red scarf, a navy coverall with
       hi-vis orange bands and a reflective stripe, black gloves and boots.
- porter_pack (+ _glow): the great pack every porter carries (client/rival/PorterRenderer): a canvas sack with a
  leather-bound flap and a pocket, a striped wool bedroll strapped on top, a wooden frame's two rails, leather
  shoulder straps, a brass lantern on one rail (its glass glows) and a sooty pot on the other. Drawn at 2 pixels per
  model unit (128x128 for a 64x64 UV space).
- guild_band: the band a rival party's hunters wear on the left arm (client/rival/RivalHunterRenderer), in greys:
  the game tints it with the guild's colour. Stitched edges, a woven body and a mark on the outer face. 2 px a unit.

Cloth gets a weave, leather a grain, wood a grain along its length, metal a sheen; every face is lit from above and
darkens into its edges, so nothing reads as a flat colour.

Run from the repo root:   python3 tools/gen_rival_assets.py             (writes the textures)
                          python3 tools/gen_rival_assets.py --preview   (and build/preview/rivals.png)
Needs: pillow, numpy.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_association_assets as ga  # noqa: E402
from gen_association_assets import (HEAD, HAT, BODY, JACKET, R_ARM, R_SLEEVE, L_ARM, L_SLEEVE, R_LEG, R_PANTS, L_LEG,  # noqa: E402
                                    L_PANTS, Skin, fill, mirror, rows, skin_pal, hair_pal, eye_pal, hexc)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo", "textures", "entity", "rival")
PREVIEW = os.path.join(ROOT, "build", "preview", "rivals.png")


# ================================================================================================= porters

def limbs(sk, arm_out, arm_in, arm_back, arm_top, arm_bottom, leg_front, leg_out, leg_in, leg_back, leg_top, leg_bottom,
          light=0.05):
    """Both arms and both legs; the outer faces (the right limb's right side, the left limb's left side) get *_out."""
    for box, right in ((R_ARM, True), (L_ARM, False)):
        r, l = (arm_out, arm_in) if right else (arm_in, arm_out)
        front = arm_out
        sk.box(box, r, front, l, arm_back, arm_top, arm_bottom, light=light)
    for box, right in ((R_LEG, True), (L_LEG, False)):
        r, l = (leg_out, leg_in) if right else (leg_in, leg_out)
        sk.box(box, r, leg_front, l, leg_back, leg_top, leg_bottom, light=light)


def porter_rookie():
    pal = {
        "J": ("5c6438", 0.06), "j": ("454b2a", 0.05), "L": ("737d48", 0.04), "c": ("353a20", 0.03),
        "F": ("6e5a3c", 0.07), "f": ("54442c", 0.05),
        "W": ("8c9198", 0.05), "w": ("6c7179", 0.04), "V": ("d6d8da", 0.0),
        "U": ("3c2a1c", 0.04), "y": ("b89a50", 0.0),
        "T": ("7c6c50", 0.06), "t": ("5f523c", 0.05), "K": ("3a3a3e", 0.04), "O": ("2a2a2e", 0.03), "x": ("4e4a44", 0.04),
        "G": ("3e3e44", 0.05), "g": ("2c2c30", 0.04),
        "B": ("5a3c26", 0.05), "b": ("44301e", 0.04), "Q": ("1e1612", 0.0), "q": ("c8b48c", 0.0),
        "Y": ("c8962e", 0.07), "R": ("dcab42", 0.05), "Z": ("a67a24", 0.05), "z": ("8c6620", 0.04),
    }
    pal.update(skin_pal("light"))
    pal.update(hair_pal("3a2a20", "281c15", "54402e"))
    pal.update(eye_pal("5a3e28"))
    pal["B"] = ("5a3c26", 0.05)
    pal["n"] = ("281c15", 0.0)  # brows
    sk = Skin(300, pal)
    # a dark mop under the beanie, brown eyes
    front = ["HHHHHHHH", "HhHHHHhH", "hSSSSSSh", "SnnSSnnS", "SEISSIES", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
    side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHh", "HHHHHhSS", "HHHHeSSS", "HHHheSSS", "hHHsSSSs", "hhssssss"]
    back = ["HHHHHHHH", "HHhHHHHH", "HHHHHhHH", "HHHHHHHH", "HhHHHHHH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
    sk.box(HEAD, side, front, mirror(side), back, fill("H", 8, 8), fill("d", 8, 8))
    # the knit beanie: ribbed body, a folded cuff, tufts of hair under it at the back
    hat_front = ["YRYRYRYR", "YYYYYYYY", "ZzZzZzZz", "H......H"] + ["........"] * 4
    hat_side = ["RYRYRYRY", "YYYYYYYY", "zZzZzZzZ", "HHh.....", "Hh......"] + ["........"] * 3
    hat_back = ["YRYRYRYR", "YYYYYYYY", "ZzZzZzZz", "HhHHhHHh", "h.H..h.H"] + ["........"] * 3
    hat_top = ["YYYYYYYY", "YYzzzzYY", "YzYYYYzY", "YzYzzYzY", "YzYzzYzY", "YzYYYYzY", "YYzzzzYY", "YYYYYYYY"]
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    # the jacket, open over the hoodie: its strings, chest pockets, a belt, the top of the trousers
    side_b = rows(("J", 4, 9), ("U", 4, 1), ("T", 4, 1), ("t", 4, 1))
    front_b = ["JLWWWWLJ", "JJLWVWLJ", "JJjWVWjJ", "JcJWWWcJ", "JccWWccJ", "JJJWWWJJ",
               "JJJwWwJJ", "JJjWWWjJ", "jjjwwwjj", "UUUUyUUU", "TTTTTTTT", "TtTTTTtT"]
    back_b = rows(["JJJJjJJJ"] * 8, ["jjjjjjjj"], ("U", 8, 1), ("T", 8, 1), ("t", 8, 1))
    top_b = ["JJJJJJJJ", "JLWWWWLJ", "JLWwwWLJ", "JLWWWWLJ"]
    sk.box(BODY, side_b, front_b, side_b, back_b, top_b, fill("T", 8, 4), light=0.06)
    # the collar standing off the jacket, and its pocket flaps (outer layer)
    clear = "........"
    sk.box(JACKET, rows(("J", 4, 1), (".", 4, 11)), rows(["LL....LL", "L......L", clear, clear, ".cc..cc.", ] + [clear] * 7),
           rows(("J", 4, 1), (".", 4, 11)), rows(["JJJJJJJJ"], [clear] * 11), ["JJJJJJJJ", "J......J", "J......J", "JL....LJ"], None)
    # sleeves with patched elbows, a cuff, fingerless gloves
    arm_out = rows(("J", 4, 5), ["JFFJ", "JFfJ"], ("J", 4, 2), ("c", 4, 1), ("G", 4, 1), ["SgSg"])
    arm_in = rows(("j", 4, 9), ("c", 4, 1), ("g", 4, 1), ["sgsg"])
    arm_back = rows(("J", 4, 5), ["FFFF", "fFFf"], ("J", 4, 2), ("c", 4, 1), ("G", 4, 1), ["gggg"])
    leg_front = ["TTTT", "TTTT", "TtTT", "TTTT", "KKKK", "KOOK", "TTTT", "TtTT", "tttt", "BbBB", "BBBB", "QQQQ"]
    leg_out = ["TTTT", "TTTT", "TxxT", "TttT", "TttT", "TTTT", "TTTT", "TTTT", "tttt", "BBBB", "bBBb", "QQQQ"]
    leg_in = ["tttt"] * 8 + ["tttt", "bbbb", "bBBb", "QQQQ"]
    leg_back = ["TTTT"] * 7 + ["TtTT", "tttt", "BBBB", "bbbb", "QQQQ"]
    limbs(sk, arm_out, arm_in, arm_back, fill("J", 4, 4), ["gggg", "gSSg", "gSSg", "gggg"],
          leg_front, leg_out, leg_in, leg_back, fill("T", 4, 4), fill("Q", 4, 4))
    # the knee pads stand off the trousers (outer layer)
    pad = rows((".", 4, 4), ("K", 4, 1), ("O", 4, 1), (".", 4, 6))
    side_pad = rows((".", 4, 12))
    for box in (R_PANTS, L_PANTS):
        sk.box(box, side_pad, pad, side_pad, side_pad)
    return sk


def porter_seasoned():
    pal = {
        "V": ("3a3d43", 0.05), "v": ("2a2c31", 0.04), "o": ("4b4f56", 0.04), "z": ("9aa0a6", 0.0), "e": ("a87252", 0.02),
        "W": ("e0d4ba", 0.03), "w": ("c2b698", 0.03),
        "G": ("b08850", 0.05), "g": ("8a683a", 0.04),
        "T": ("2f3b54", 0.05), "t": ("232c41", 0.04), "K": ("3b4968", 0.04), "c": ("1b2335", 0.03),
        "B": ("4c3624", 0.05), "b": ("3a281a", 0.04), "y": ("c8b080", 0.0), "Q": ("18120e", 0.0),
        "U": ("2a1f16", 0.03), "Y": ("c8a050", 0.0),
        "R": ("a8452a", 0.06), "r": ("82331d", 0.05), "x": ("c45c38", 0.04),
    }
    pal.update(skin_pal("tan"))
    pal.update(hair_pal("1e1a1a", "141010", "36302e"))
    pal.update(eye_pal("3a2a1a"))
    pal["n"] = ("141010", 0.0)
    sk = Skin(301, pal)
    front = ["HHHHHHHH", "HHHHHHHH", "hSSSSSSh", "SnnSSnnS", "SEISSIES", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
    side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHh", "HHHHHhSS", "HHHHeSSS", "HHHheSSS", "hHHHsSSs", "hhhHssss"]
    back = ["HHHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "hHHHHHHh", "hhHHHHhh"]
    sk.box(HEAD, side, front, mirror(side), back, fill("H", 8, 8), fill("d", 8, 8))
    # the bandana over the hair, knotted at the back over a tail
    hat_front = ["RRRRRRRR", "rRRRRRRr"] + ["........"] * 6
    hat_side = ["RRRRRRRR", "RRRRRRRr", "rrrr....", "........", "........", "........", "........", "........"]
    hat_back = ["RRRRRRRR", "RRRRRRRR", "rRRxxRRr", "..rRRr..", "..RHHR..", "..rHHr..", "...HH...", "...hh..."]
    hat_top = ["RRRRRRRR", "RRRxRRRR", "RRRxRRRR", "RRxRxRRR", "RRxRxRRR", "RxRRRxRR", "RxRRRxRR", "rrrrrrrr"]
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    # the vest: a zip, two rows of pockets, the shirt's collar at the neck
    side_b = rows(("V", 4, 9), ("U", 4, 1), ("T", 4, 1), ("t", 4, 1))
    front_b = ["VWWWWWWV", "VVWWwWVV", "VVVWzVVV", "VooVzooV", "VooVzooV", "VvvVzvvV",
               "VVVVzVVV", "VooVzooV", "VvvVzvvV", "UUUYUUUU", "TTTTTTTT", "TtTTTTtT"]
    back_b = rows(["VVVvVVVV"] * 8, ["vvvvvvvv"], ("U", 8, 1), ("T", 8, 1), ("t", 8, 1))
    sk.box(BODY, side_b, front_b, side_b, back_b, ["VVVVVVVV", "VWWWWWWV", "VWwwwwWV", "VWWWWWWV"], fill("T", 8, 4), light=0.05)
    # the pockets' flaps stand off the vest
    clear = "........"
    sk.box(JACKET, rows((".", 4, 12)), rows([clear] * 3, [".oo..oo."], [clear] * 3, [".oo..oo."], [clear] * 4),
           rows((".", 4, 12)), rows((".", 8, 12)))
    # shirt sleeves rolled to the elbow, bare forearms, leather gloves
    arm_out = rows(("W", 4, 5), ["wWWw", "WWWW"], ("S", 4, 2), ["sSSs"], ("G", 4, 1), ["gGGg"])
    arm_in = rows(("w", 4, 5), ["wwww", "wWWw"], ("s", 4, 3), ("g", 4, 2))
    arm_back = rows(("W", 4, 5), ["wwww", "WWWW"], ("S", 4, 2), ["ssss"], ("G", 4, 1), ["gggg"])
    leg_front = ["TTTT", "TTTT", "TTtT", "TTTT", "KKKK", "KcKK", "TTTT", "TTTT", "tttt", "ByyB", "BybB", "QQQQ"]
    leg_out = ["TTTT", "TccT", "TttT", "TttT", "TTTT", "TTTT", "TTTT", "TTTT", "tttt", "BBBB", "bBBb", "QQQQ"]
    leg_in = ["tttt"] * 9 + ["bbbb", "bBBb", "QQQQ"]
    leg_back = ["TTTT"] * 8 + ["tttt", "BBBB", "bbbb", "QQQQ"]
    limbs(sk, arm_out, arm_in, arm_back, fill("W", 4, 4), fill("g", 4, 4),
          leg_front, leg_out, leg_in, leg_back, fill("T", 4, 4), fill("Q", 4, 4))
    # the rolled cuff stands off the arm
    cuff = rows((".", 4, 5), ("W", 4, 1), ("w", 4, 1), (".", 4, 5))
    for box in (R_SLEEVE, L_SLEEVE):
        sk.box(box, cuff, cuff, cuff, cuff)
    return sk


def porter_old_hand():
    pal = {
        "C": ("2c3e5c", 0.05), "c": ("1f2d44", 0.04), "L": ("3d5274", 0.03),
        "A": ("e08a1c", 0.04), "a": ("b86c12", 0.03), "Z": ("d4d8d4", 0.02),
        "M": ("a83030", 0.05), "r": ("7e2222", 0.04),
        "G": ("26262a", 0.04), "g": ("18181c", 0.03),
        "B": ("201e1e", 0.04), "b": ("151313", 0.03), "Q": ("0c0a0a", 0.0),
        "U": ("1a1410", 0.03), "y": ("a8a8a8", 0.0),
        "X": ("1c1c1e", 0.02), "Y": ("d8a838", 0.03), "O": ("fff2c0", 0.0), "o": ("ffd77a", 0.0),
        "z": ("7c7c80", 0.07),
    }
    pal.update(skin_pal("deep"))
    pal.update(hair_pal("8a8a8e", "68686c", "aaaaae"))
    pal.update(eye_pal("3a2a1c"))
    pal["n"] = ("6a6a6e", 0.0)
    sk = Skin(302, pal)
    # close grey hair, a grey beard and moustache
    front = ["HkHHkHHH", "hHSSSShh", "SSSSSSSS", "SnnSSnnS", "SEISSIES", "sSSssSSs", "szzzzzzs", "dzzmmzzd"]
    side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHSS", "HHHHHSSS", "HHHHeSSS", "HHHheSSz", "hHHSSSzz", "hhSSSzzz"]
    back = ["HHHHHHHH", "HkHHhkHH", "HHhHHHhH", "hHHHhHHH", "HHhHHHhH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
    top = ["HHHHHHHH", "HkHHhkHH", "HkHhHkHh", "hHkHHHkH", "HHkhHHkH", "HhkHHhkH", "HHHkHHHk", "HhHkHHhk"]
    sk.box(HEAD, side, front, mirror(side), back, top, fill("d", 8, 8))
    # the miner's lamp on its strap
    hat_front = ["........", "...YY...", "XXYOoYXX"] + ["........"] * 5
    hat_side = ["........", "........", "XXXXXXXX"] + ["........"] * 5
    hat_back = ["........", "........", "XXXyyXXX"] + ["........"] * 5
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, fill(".", 8, 8), None)
    # the coverall: a scarf at the throat, a zip, a chest pocket, the hi-vis band with its reflective stripe
    side_b = rows(["MMMM", "rrrr"], ("C", 4, 2), ["AAAA", "ZZZZ", "aaaa"], ("C", 4, 2), ("U", 4, 1), ("C", 4, 1), ("c", 4, 1))
    front_b = ["MMMMMMMM", "rMMrMMMr", "CCCLCccC", "CCCLCccC", "AAAAAAAA", "ZZZZZZZZ",
               "aaaaaaaa", "CCCLCCCC", "CCCLCCCC", "UUUyUUUU", "CCCCCCCC", "CcCCCCcC"]
    back_b = rows(["MMMMMMMM", "rrrrrrrr"], ("C", 8, 2), ["AAAAAAAA", "ZZZZZZZZ", "aaaaaaaa"], ("C", 8, 2), ("U", 8, 1),
                  ("C", 8, 1), ("c", 8, 1))
    sk.box(BODY, side_b, front_b, side_b, back_b, ["CCCCCCCC", "CMMMMMMC", "CMrrrrMC", "CMMMMMMC"], fill("C", 8, 4), light=0.05)
    # the scarf's knot stands off the chest
    clear = "........"
    sk.box(JACKET, rows(["MMMM"], (".", 4, 11)), rows(["MMMMMMMM", "..rMMr..", "...Mr..."], [clear] * 9),
           rows(["MMMM"], (".", 4, 11)), rows(["MMMMMMMM"], [clear] * 11))
    arm_out = rows(("C", 4, 3), ["AAAA", "ZZZZ", "aaaa"], ("C", 4, 3), ("c", 4, 1), ("G", 4, 1), ["gGGg"])
    arm_in = rows(("c", 4, 3), ["aaaa", "ZZZZ", "aaaa"], ("c", 4, 3), ("c", 4, 1), ("g", 4, 2))
    leg_front = ["CCCC", "CCCC", "CLCC", "CCCC", "CcCC", "CCCC", "AAAA", "ZZZZ", "cccc", "BBBB", "BbBB", "QQQQ"]
    leg_out = ["CCCC", "CCCC", "CccC", "CccC", "CCCC", "CCCC", "AAAA", "ZZZZ", "cccc", "BBBB", "bBBb", "QQQQ"]
    leg_in = ["cccc"] * 6 + ["aaaa", "ZZZZ", "cccc", "bbbb", "bBBb", "QQQQ"]
    limbs(sk, arm_out, arm_in, arm_out, fill("C", 4, 4), fill("g", 4, 4),
          leg_front, leg_out, leg_in, leg_out, fill("C", 4, 4), fill("Q", 4, 4))
    return sk


PORTERS = [porter_rookie, porter_seasoned, porter_old_hand]


# ================================================================================================= painted parts

K = 2  # pixels per model unit for the pack and the band

# name: (u, v, w, h, d) in UV units; keep in sync with client/rival/PorterRenderer.Pack
PACK_BOXES = {
    "sack": (0, 0, 9, 11, 5),
    "flap": (28, 0, 8, 5, 1),
    "pocket": (28, 6, 6, 4, 2),
    "pot": (28, 12, 3, 2, 3),
    "lantern": (46, 0, 2, 4, 2),
    "rail": (56, 0, 1, 14, 1),
    "bedroll": (0, 16, 11, 3, 3),
    "strap": (0, 24, 1, 8, 1),
    "strap_top": (4, 24, 1, 1, 5),
}
BAND_BOX = (0, 0, 4, 3, 4)


def faces(box):
    """Each face's rectangle (x, y, w, h) in pixels: top, bottom, right (-x), front (-z), left (+x), back (+z)."""
    u, v, w, h, d = (k * K for k in box)
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


class Canvas:
    def __init__(self, w, h, seed):
        self.a = np.zeros((h, w, 4), float)
        self.rng = np.random.default_rng(seed)

    def put(self, x, y, col):
        self.a[y, x, :3] = np.clip(col, 0, 255)
        self.a[y, x, 3] = 255

    def get(self, x, y):
        return self.a[y, x, :3].copy()

    def image(self):
        return Image.fromarray(np.clip(self.a, 0, 255).astype(np.uint8), "RGBA")


def material(c, rect, base, kind, light=0.12, edge=0.22, along="y"):
    """Fills a face with a material. kind: canvas, leather, wool, wood, brass, iron, glass. light: how much brighter the
    top is than the bottom; edge: how much its rim darkens (the seam where faces meet)."""
    x0, y0, w, h = rect
    base = hexc(base) if isinstance(base, str) else np.array(base, float)
    rng = c.rng
    rowk = 1 + rng.normal(0, 0.025, h)
    colk = 1 + rng.normal(0, 0.025, w)
    for j in range(h):
        for i in range(w):
            k = 1 + light * (0.5 - (j + 0.5) / h) * 2
            if kind == "canvas":
                # a plain weave: threads across and along, a little uneven
                k *= rowk[j] * colk[i] * (1 + rng.normal(0, 0.03)) * (0.97 if (i + j) % 2 else 1.02)
            elif kind == "leather":
                k *= 1 + rng.normal(0, 0.035)
            elif kind == "wool":
                k *= rowk[j] * (1 + rng.normal(0, 0.05))
            elif kind == "wood":
                g = colk[i] if along == "y" else rowk[j]
                k *= g * (1 + rng.normal(0, 0.02)) * (0.9 if (i if along == "y" else j) % 3 == 1 else 1.0)
            elif kind == "brass":
                k *= 1 + 0.25 * np.cos((i / max(1, w - 1) - 0.35) * 3.0) - 0.1
            elif kind == "iron":
                k *= 1 + rng.normal(0, 0.06)
            # the rim
            rim = min(i, j, w - 1 - i, h - 1 - j)
            if rim == 0:
                k *= 1 - edge
            elif rim == 1 and kind in ("canvas", "wool"):
                k *= 1 - edge * 0.35
            c.put(x0 + i, y0 + j, base * k)


def stitch(c, x0, y0, x1, y1, col, every=2):
    """A line of stitches (light dashes) from (x0, y0) to (x1, y1), horizontal or vertical."""
    col = hexc(col)
    n = max(abs(x1 - x0), abs(y1 - y0)) + 1
    for s in range(n):
        if s % every:
            continue
        x = x0 + (0 if x1 == x0 else (s if x1 > x0 else -s))
        y = y0 + (0 if y1 == y0 else (s if y1 > y0 else -s))
        c.put(x, y, col * (0.95 + 0.1 * c.rng.random()))


def band_across(c, rect, at, width, base, buckle=None, vertical=True):
    """A leather strap across a face (vertical: a column band at x offset `at`), with an optional buckle row."""
    x0, y0, w, h = rect
    col = hexc(base)
    if vertical:
        for j in range(h):
            for i in range(width):
                edge = i == 0 or i == width - 1
                k = (0.82 if edge else 1.0) * (1 + 0.12 * (0.5 - j / h)) * (1 + c.rng.normal(0, 0.03))
                c.put(x0 + at + i, y0 + j, col * k)
        if buckle is not None:
            bc = hexc("c8a04a")
            for i in range(-1, width + 1):
                for jj in (buckle, buckle + 2):
                    if 0 <= jj < h and 0 <= at + i < w:
                        c.put(x0 + at + i, y0 + jj, bc * (1.1 if jj == buckle else 0.8))
            for jj in range(buckle, min(h, buckle + 3)):
                for i in (-1, width):
                    if 0 <= at + i < w:
                        c.put(x0 + at + i, y0 + jj, bc * 0.95)
            # the tongue through the frame
            if buckle + 1 < h:
                c.put(x0 + at + width // 2, y0 + buckle + 1, hexc("e8d08a"))
    else:
        for i in range(w):
            for j in range(width):
                edge = j == 0 or j == width - 1
                k = (0.82 if edge else 1.0) * (1 + c.rng.normal(0, 0.03))
                c.put(x0 + i, y0 + at + j, col * k)


def patch(c, rect, px, py, pw, ph, base, thread):
    """A sewn-on patch: a square of other cloth with a stitched edge."""
    x0, y0, w, h = rect
    sub = (x0 + px, y0 + py, pw, ph)
    material(c, sub, base, "canvas", light=0.06, edge=0.12)
    stitch(c, x0 + px, y0 + py, x0 + px + pw - 1, y0 + py, thread)
    stitch(c, x0 + px, y0 + py + ph - 1, x0 + px + pw - 1, y0 + py + ph - 1, thread)
    stitch(c, x0 + px, y0 + py, x0 + px, y0 + py + ph - 1, thread)
    stitch(c, x0 + px + pw - 1, y0 + py, x0 + px + pw - 1, y0 + py + ph - 1, thread)


CANVAS, CANVAS_DARK, LEATHER, LEATHER_DARK, THREAD = "8c7c58", "6e6044", "6a4428", "4a2e1a", "d8c9a0"


def paint_pack():
    c = Canvas(64 * K, 64 * K, 400)
    f = faces(PACK_BOXES["sack"])
    for side in ("right", "left", "front", "back"):
        material(c, f[side], CANVAS if side != "front" else CANVAS_DARK, "canvas")
    material(c, f["top"], "988a64", "canvas", light=0.04)
    material(c, f["bottom"], LEATHER_DARK, "leather", light=0.0)
    # the back: two compression straps with buckles, a patch on the canvas (the flap and the pocket cover the rest)
    x, y, w, h = f["back"]
    band_across(c, f["back"], 3, 2, LEATHER, buckle=14)
    band_across(c, f["back"], w - 5, 2, LEATHER, buckle=14)
    # the sides: a sewn seam down the middle, a loop at the top, a patch on one
    for side in ("right", "left"):
        x, y, w, h = f[side]
        stitch(c, x + w // 2, y + 1, x + w // 2, y + h - 2, "5a4c34")
        band_across(c, f[side], 0, 2, LEATHER, vertical=False)
        stitch(c, x + 1, y + 3, x + w - 2, y + 3, THREAD)
    patch(c, f["left"], 2, 9, 5, 6, "7a6a48", THREAD)
    # the top: the cinched mouth of the sack, a drawstring
    x, y, w, h = f["top"]
    for i in range(3, w - 3):
        for j in range(3, h - 3):
            c.put(x + i, y + j, hexc("3c3222") * (0.9 + 0.2 * c.rng.random()))
    stitch(c, x + 2, y + 2, x + w - 3, y + 2, "c8b88c", every=1)
    stitch(c, x + 2, y + h - 3, x + w - 3, y + h - 3, "c8b88c", every=1)

    # the flap: leather-bound canvas, two buckles at its foot
    f = faces(PACK_BOXES["flap"])
    for side in ("back", "front", "right", "left", "top", "bottom"):
        material(c, f[side], "7a6a4a" if side in ("back", "top") else CANVAS_DARK, "canvas", light=0.08)
    x, y, w, h = f["back"]
    for i in range(w):
        for j in (0, h - 1):
            c.put(x + i, y + j, hexc(LEATHER) * (0.85 if j else 1.05) * (1 + c.rng.normal(0, 0.03)))
    for j in range(h):
        for i in (0, w - 1):
            c.put(x + i, y + j, hexc(LEATHER) * 0.9 * (1 + c.rng.normal(0, 0.03)))
    stitch(c, x + 2, y + 1, x + w - 3, y + 1, THREAD)
    for bx in (3, w - 5):
        for i in range(2):
            for j in range(h - 4, h):
                c.put(x + bx + i, y + j, hexc(LEATHER) * (1.0 - 0.06 * (j - (h - 4))))
        for i in range(-1, 3):
            c.put(x + bx + i, y + h - 3, hexc("c8a04a") * 1.1)
        c.put(x + bx - 1, y + h - 2, hexc("c8a04a") * 0.9)
        c.put(x + bx + 2, y + h - 2, hexc("c8a04a") * 0.9)
    # a painted mark on the flap: a porter's guild number (three short bars)
    for k_ in range(3):
        for i in range(5):
            c.put(x + w // 2 - 2 + i, y + 3 + k_ * 2, hexc("d8ccb0") * (0.92 + 0.08 * c.rng.random()))

    # the pocket: canvas, its own flap and a toggle
    f = faces(PACK_BOXES["pocket"])
    for side in f:
        material(c, f[side], CANVAS if side != "bottom" else LEATHER_DARK, "canvas" if side != "bottom" else "leather")
    x, y, w, h = f["back"]
    for i in range(w):
        for j in range(3):
            c.put(x + i, y + j, hexc("7a6a4a") * (1.05 - 0.08 * j) * (1 + c.rng.normal(0, 0.03)))
    stitch(c, x + 1, y + 3, x + w - 2, y + 3, "4a3e2a", every=1)
    for i in range(w // 2 - 1, w // 2 + 1):
        for j in (3, 4):
            c.put(x + i, y + j, hexc("3a2a1a") if j == 4 else hexc("b89a60"))

    # the bedroll: a striped wool blanket rolled tight, two straps round it, the roll's spiral at the ends
    f = faces(PACK_BOXES["bedroll"])
    wool, stripe, stripe2 = "6e2a26", "c8b48a", "2a3a5a"
    for side in ("top", "bottom", "front", "back"):
        x, y, w, h = f[side]
        material(c, f[side], wool, "wool", light=0.1 if side != "bottom" else -0.05)
        for i in range(w):
            for j in range(h):
                s = i % 11
                if s == 5:
                    c.put(x + i, y + j, hexc(stripe) * (0.92 + 0.12 * c.rng.random()) * (0.8 if j in (0, h - 1) else 1))
                elif s in (4, 6):
                    c.put(x + i, y + j, hexc(stripe2) * (0.9 + 0.12 * c.rng.random()) * (0.8 if j in (0, h - 1) else 1))
        for at in (3, w - 6):
            band_across(c, f[side], at, 3, LEATHER, buckle=None)
    for side in ("right", "left"):
        x, y, w, h = f[side]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for j in range(h):
            for i in range(w):
                r = np.hypot(i - cx, j - cy)
                a = np.arctan2(j - cy, i - cx)
                ring = (r + a / np.pi * 0.9) % 2.2
                col = hexc(wool) * (1.15 if ring < 1.1 else 0.72)
                if r > 2.9:
                    col = hexc(wool) * 0.55
                c.put(x + i, y + j, col * (1 + c.rng.normal(0, 0.04)))

    # the frame's rails: wood with the grain along them, cut ends lighter
    f = faces(PACK_BOXES["rail"])
    for side in ("right", "left", "front", "back"):
        material(c, f[side], "7a5434", "wood", light=0.06, edge=0.15)
        x, y, w, h = f[side]
        for i in range(w):
            c.put(x + i, y + 4, hexc("4a2e1a"))  # a lashing under the top
            c.put(x + i, y + 5, hexc("c8b088") * 0.9)
    material(c, f["top"], "b08a5c", "wood", light=0.0, edge=0.1, along="x")
    material(c, f["bottom"], "b08a5c", "wood", light=0.0, edge=0.1, along="x")

    # the shoulder straps: leather, stitched along both edges, a buckle on the front
    f = faces(PACK_BOXES["strap"])
    for side in f:
        material(c, f[side], LEATHER, "leather", light=0.1, edge=0.25)
    x, y, w, h = f["front"]
    for j in range(1, h - 1):
        c.put(x, y + j, hexc(LEATHER) * 1.18)
        c.put(x + w - 1, y + j, hexc(LEATHER) * 0.78)
    # a buckle: the brass frame, the strap's end through it
    for i in range(w):
        c.put(x + i, y + 9, hexc("c8a04a") * 1.1)
        c.put(x + i, y + 10, hexc(LEATHER_DARK))
        c.put(x + i, y + 11, hexc("a07e36"))
    f = faces(PACK_BOXES["strap_top"])
    for side in f:
        material(c, f[side], LEATHER, "leather", light=0.05, edge=0.25)

    # the lantern: brass cap and foot, glass between
    f = faces(PACK_BOXES["lantern"])
    for side in ("right", "left", "front", "back"):
        x, y, w, h = f[side]
        material(c, f[side], "b8883a", "brass", light=0.1, edge=0.2)
        for j in range(2, h - 2):
            for i in range(w):
                bar = i in (0, w - 1)
                c.put(x + i, y + j, hexc("8a6428") if bar else hexc("ffd27a") * (0.9 + 0.1 * c.rng.random()))
    material(c, f["top"], "c89a48", "brass", light=0.0, edge=0.25)
    material(c, f["bottom"], "8a6428", "brass", light=0.0, edge=0.2)
    x, y, w, h = f["top"]
    c.put(x + w // 2 - 1, y + h // 2 - 1, hexc("3a2a14"))

    # the pot: sooty iron, a brighter rim
    f = faces(PACK_BOXES["pot"])
    for side in ("right", "left", "front", "back"):
        material(c, f[side], "3c3c42", "iron", light=0.15, edge=0.15)
        x, y, w, h = f[side]
        for i in range(w):
            c.put(x + i, y, hexc("7a7a82") * (0.95 + 0.1 * c.rng.random()))
            c.put(x + i, y + 1, hexc("55555c"))
    material(c, f["top"], "1e1e22", "iron", light=0.0, edge=0.0)
    x, y, w, h = f["top"]
    for i in range(w):
        for j in range(h):
            if min(i, j, w - 1 - i, h - 1 - j) == 0:
                c.put(x + i, y + j, hexc("6a6a72"))
    material(c, f["bottom"], "141416", "iron", light=0.0, edge=0.0)
    return c


def paint_pack_glow(pack):
    """Only the lantern's glass, for the full-bright pass."""
    g = Canvas(64 * K, 64 * K, 401)
    f = faces(PACK_BOXES["lantern"])
    for side in ("right", "left", "front", "back"):
        x, y, w, h = f[side]
        for j in range(2, h - 2):
            for i in range(1, w - 1):
                g.put(x + i, y + j, hexc("ffc864") * (0.85 + 0.15 * ((j - 2) % 2)))
    return g


def paint_band():
    c = Canvas(32 * K, 16 * K, 500)
    f = faces(BAND_BOX)
    # greys: the guild's colour is multiplied in, so white is its full colour
    for side in ("right", "front", "back"):
        x, y, w, h = f[side]
        material(c, f[side], "e6e6e6", "canvas", light=0.08, edge=0.15)
        for i in range(w):
            c.put(x + i, y, hexc("8c8c8c"))
            c.put(x + i, y + h - 1, hexc("8c8c8c"))
        stitch(c, x, y + 1, x + w - 1, y + 1, "ffffff")
        stitch(c, x + 1, y + h - 2, x + w - 1, y + h - 2, "ffffff")
    # the outer face (the arm's left side) carries the guild's mark: a diamond in a ring
    x, y, w, h = f["left"]
    material(c, f["left"], "d0d0d0", "canvas", light=0.08, edge=0.15)
    for i in range(w):
        c.put(x + i, y, hexc("8c8c8c"))
        c.put(x + i, y + h - 1, hexc("8c8c8c"))
    cx, cy = (w - 1) / 2, (h - 1) / 2
    for j in range(h):
        for i in range(w):
            d = abs(i - cx) + abs(j - cy)
            if d <= 1.6:
                c.put(x + i, y + j, hexc("ffffff"))
            elif d <= 2.6:
                c.put(x + i, y + j, hexc("5a5a5a"))
    for side in ("top", "bottom"):
        material(c, f[side], "a8a8a8", "canvas", light=0.0, edge=0.2)
    return c


# ================================================================================================= preview

def preview(skins, pack, glow, band, out):
    import armor_rig as rig  # noqa: E402
    import preview_model as pm  # noqa: E402
    player = [
        ("head", (-4, -8, -4), (8, 8, 8), (0, 0), 0.0), ("head", (-4, -8, -4), (8, 8, 8), (32, 0), 0.5),
        ("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.0), ("body", (-4, 0, -2), (8, 12, 4), (16, 32), 0.25),
        ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), 0.0), ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 32), 0.25),
        ("left_arm", (-1, -2, -2), (4, 12, 4), (32, 48), 0.0), ("left_arm", (-1, -2, -2), (4, 12, 4), (48, 48), 0.25),
        ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.0), ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 32), 0.25),
        ("left_leg", (-2, 0, -2), (4, 12, 4), (16, 48), 0.0), ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 48), 0.25),
    ]
    # the pack, as PorterRenderer.Pack builds it: (origin, size, uv, pivot)
    pack_parts = [
        ((-4.5, 0.5, 2.2), (9, 11, 5), (0, 0), None), ((-4, 0.3, 7.2), (8, 5, 1), (28, 0), None),
        ((-3, 6.8, 7.2), (6, 4, 2), (28, 6), None), ((-5.5, -2.4, 4.8), (11, 3, 3), (0, 16), None),
        ((-5.4, -1.5, 4.2), (1, 14, 1), (56, 0), None), ((4.4, -1.5, 4.2), (1, 14, 1), (56, 0), None),
        ((-3, -0.4, -2.6), (1, 8, 1), (0, 24), None), ((-3, -0.6, -2.6), (1, 1, 5), (4, 24), None),
        ((2, -0.4, -2.6), (1, 8, 1), (0, 24), None), ((2, -0.6, -2.6), (1, 1, 5), (4, 24), None),
        ((-1, 0, -1), (2, 4, 2), (46, 0), (-6.4, 1.2, 5.2)), ((-1.5, 0, -1.5), (3, 2, 3), (28, 12), (7, 4, 5.2)),
    ]
    pose = {"right_arm": (-14, 0, 4), "left_arm": (12, 0, -4), "right_leg": (10, 0, 0), "left_leg": (-10, 0, 0)}

    def scene_of(with_pack, with_band):
        quads = []
        for part, origin, size, uv, inflate in player:
            b = rig.Box(part, part, origin, size, uv, inflate)
            quads += rig.box_quads(b, rig.part_pose(part, pose))
        if with_pack:
            for origin, size, uv, pivot in pack_parts:
                b = rig.Box("pack", "body", origin, size, uv, 0.0, pivot=pivot)
                quads += rig.box_quads(b, rig.part_pose("body", pose), atlas_off=(128, 0))
        if with_band:
            b = rig.Box("band", "left_arm", (-1, 0.6, -2), (4, 3, 4), (0, 0), 1.15)
            quads += rig.box_quads(b, rig.part_pose("left_arm", pose), atlas_off=(0, 128))
        scene = []
        for P, rect, n in quads:
            Q = P.copy()
            Q[:, 1] = 24 - Q[:, 1]
            nn = np.array(n, float)
            nn[1] = -nn[1]
            scene.append((Q, rect, nn))
        return scene

    def atlas_of(skin, tint=None):
        a = np.zeros((192, 256, 4), np.uint8)
        a[0:128, 0:128] = np.asarray(skin.resize((128, 128), Image.NEAREST))
        a[0:128, 128:256] = np.asarray(pack)
        b = np.asarray(band).astype(float)
        if tint is not None:
            b[:, :, :3] *= np.array(tint, float)[None, None, :] / 255.0
        a[128:160, 0:64] = np.clip(b, 0, 255).astype(np.uint8)
        g = np.zeros_like(a)
        g[0:128, 128:256] = np.asarray(glow)
        return a, g

    columns = []
    views = ("fl", "br", "bl")
    for skin in skins:
        atlas, gl = atlas_of(skin)
        tiles = [pm.render(scene_of(True, False), atlas, gl, (220, 330), target=(0, 16.5, 0), dist=62, fov=38, **pm.VIEWS[v])
                 for v in views]
        columns.append(np.concatenate(tiles, axis=0))
    # a rival hunter's band (on a porter's body here, for scale), in Red Lantern red and Azure Tide blue
    for tint in ((0xFF, 0x5A, 0x4F), (0x4F, 0xA8, 0xFF)):
        atlas, gl = atlas_of(skins[0], tint)
        tiles = [pm.render(scene_of(False, True), atlas, gl, (220, 330), target=(0, 16.5, 0), dist=62, fov=38, **pm.VIEWS[v])
                 for v in ("fr", "right", "fl")]
        columns.append(np.concatenate(tiles, axis=0))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(np.concatenate(columns, axis=1)).save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    skins = []
    for i, make in enumerate(PORTERS):
        img = make().image()
        img.save(os.path.join(OUT, f"porter_{i}.png"))
        skins.append(img)
        print("wrote", f"porter_{i}")
    pack = paint_pack()
    pack_img = pack.image()
    pack_img.save(os.path.join(OUT, "porter_pack.png"))
    glow = paint_pack_glow(pack).image()
    glow.save(os.path.join(OUT, "porter_pack_glow.png"))
    band = paint_band().image()
    band.save(os.path.join(OUT, "guild_band.png"))
    print("wrote porter_pack, porter_pack_glow, guild_band")
    if args.preview:
        print("preview", preview(skins, pack_img, glow, band, PREVIEW))


if __name__ == "__main__":
    main()
