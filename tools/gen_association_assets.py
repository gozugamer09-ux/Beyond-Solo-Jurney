#!/usr/bin/env python3
"""
The Hunter Association's people (see com.beyondsolo.association): skins in the player's shape (64x64, the game's
player layout with both layers) for

- the association officers who keep an office at every Evaluation Zone (officer_0..2): a navy suit, a white shirt and
  a tie, the association's gold pin on the chest and a steel blue armband with its seal on the left arm. Three people:
  a dark crop and grey-blue eyes; auburn hair tied back, green eyes and a burgundy tie; silver hair swept back with a
  short beard.
- the gate brokers who hang about the offices (broker_0..1): a long coat whose skirt hangs over the legs (the outer
  leg layer), a vest with a belt of pouches, boots. One hides under a hood with a plum scarf over the mouth, gold eyes
  and a violet gate shard on a cord; the other wears a tweed cap, round violet-tinted glasses, a rust scarf and a
  pocket watch on a chain.

Every face is drawn pixel by pixel from the maps below (one letter a colour, '.' clear); cloth gets a weave of noise
and a light from above, so nothing reads as a flat colour.

Run from the repo root:   python3 tools/gen_association_assets.py             (writes the skins)
                          python3 tools/gen_association_assets.py --preview   (and build/preview/association.png)
Needs: pillow, numpy.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo", "textures", "entity", "association")
PREVIEW = os.path.join(ROOT, "build", "preview", "association.png")

# (u, v, w, h, d) of each box in the 64x64 player skin
HEAD, HAT = (0, 0, 8, 8, 8), (32, 0, 8, 8, 8)
BODY, JACKET = (16, 16, 8, 12, 4), (16, 32, 8, 12, 4)
R_ARM, R_SLEEVE = (40, 16, 4, 12, 4), (40, 32, 4, 12, 4)
L_ARM, L_SLEEVE = (32, 48, 4, 12, 4), (48, 48, 4, 12, 4)
R_LEG, R_PANTS = (0, 16, 4, 12, 4), (0, 32, 4, 12, 4)
L_LEG, L_PANTS = (16, 48, 4, 12, 4), (0, 48, 4, 12, 4)


def hexc(s):
    return np.array([int(s[i:i + 2], 16) for i in (0, 2, 4)], float)


class Skin:
    """A 64x64 skin being painted. A palette maps a letter to (colour, noise): noise is how much the weave varies."""

    def __init__(self, seed, pal):
        self.a = np.zeros((64, 64, 4), float)
        self.rng = np.random.default_rng(seed)
        self.pal = {k: (hexc(v[0]), v[1]) for k, v in pal.items()}

    def paint(self, x, y, rows, light=0.0):
        """Paints a map at (x, y). light: how much brighter the top row is than the bottom one (cloth in daylight)."""
        h, w = len(rows), len(rows[0])
        for r in rows:
            assert len(r) == w, (rows, r)
        grad = np.linspace(1 + light, 1 - light, h) if h > 1 else np.ones(1)
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                col, noise = self.pal[ch]
                k = grad[j] * (1 + noise * (self.rng.random() * 2 - 1))
                self.a[y + j, x + i, :3] = np.clip(col * k, 0, 255)
                self.a[y + j, x + i, 3] = 255

    def box(self, box, right=None, front=None, left=None, back=None, top=None, bottom=None, light=0.0):
        """Paints the faces of a box. right/left are the model's own sides (right = the side at -x)."""
        u, v, w, h, d = box
        if top is not None:
            self.paint(u + d, v, top)
        if bottom is not None:
            self.paint(u + d + w, v, bottom)
        for face, x, fw in ((right, u, d), (front, u + d, w), (left, u + d + w, d), (back, u + 2 * d + w, w)):
            if face is not None:
                assert len(face[0]) == fw and len(face) == h, (box, face)
                self.paint(x, v + d, face, light)

    def image(self):
        return Image.fromarray(np.clip(self.a, 0, 255).astype(np.uint8), "RGBA")


def fill(ch, w, h):
    return [ch * w] * h


def mirror(rows):
    return [r[::-1] for r in rows]


def rows(*parts):
    """Stacks maps: rows(("J", 4, 9), ("W", 4, 1)) or literal lists."""
    out = []
    for p in parts:
        if isinstance(p, tuple):
            ch, w, h = p
            out += [ch * w] * h
        else:
            out += list(p)
    return out


# ------------------------------------------------------------------------------------------------- palettes

SKIN_TONES = {
    # skin, shade, deep shade, light, mouth, ear
    "light": ("eac0a0", "d4a684", "b88868", "f6d6bc", "b8746a", "c99474"),
    "tan": ("c8956c", "b07e58", "8e6242", "dcae86", "985c48", "a87252"),
    "deep": ("86593e", "704830", "553522", "9c6c4c", "5e3426", "6a4230"),
}


def skin_pal(tone):
    s, sh, dp, li, mo, ea = SKIN_TONES[tone]
    return {"S": (s, 0.02), "s": (sh, 0.02), "d": (dp, 0.02), "l": (li, 0.015), "m": (mo, 0.0), "e": (ea, 0.02)}


def hair_pal(base, shade, light):
    return {"H": (base, 0.07), "h": (shade, 0.06), "k": (light, 0.05)}


SUIT = {
    "J": ("2c3858", 0.05), "j": ("1e2742", 0.05), "L": ("3e4c76", 0.03), "c": ("1a2139", 0.03), "p": ("171f37", 0.03),
    "W": ("e8ecf2", 0.015), "w": ("c2c9d6", 0.02), "b": ("10131c", 0.0),
    "G": ("e8be55", 0.0), "g": ("a8822e", 0.0), "Y": ("fff0b0", 0.0),
    "N": ("273049", 0.05), "n": ("1b2236", 0.04), "K": ("343f60", 0.03),
    "O": ("1c1a1f", 0.03), "o": ("3c3a42", 0.02), "Q": ("0f0e11", 0.0),
    "A": ("4b7ac2", 0.02), "a": ("365c94", 0.02),
}


def eye_pal(iris):
    return {"E": ("eef0f2", 0.0), "I": (iris, 0.0), "P": ("16161c", 0.0)}


# ------------------------------------------------------------------------------------------------- the officers

def officer_body(sk):
    """The suit: open lapels over a shirt and tie, the gold pin, buttons and pocket flaps, a back seam."""
    side = rows(("J", 4, 11), ("j", 4, 1))
    front = [
        "JLWTTWLJ",
        "JLWTTWLJ",
        "JjLTTLjJ",
        "JJLTtLYJ",
        "JJjLLjGJ",
        "JJJJbJgJ",
        "JJJJcJJJ",
        "JJJJcJJJ",
        "JppJbppJ",
        "JJJJcJJJ",
        "JJJJcJJJ",
        "jjjjcjjj",
    ]
    back = rows(["JJJJjJJJ"] * 11, ["jjjjcjjj"])
    top = ["JJJJJJJJ", "JJWWWWJJ", "JJWwwWJJ", "JLWTTWLJ"]
    sk.box(BODY, side, front, side, back, top, fill("j", 8, 4), light=0.06)
    # the arms: sleeves to the wrist, a shirt cuff, the hand
    for box, outer_right in ((R_ARM, True), (L_ARM, False)):
        sleeve = rows(("J", 4, 9), ("W", 4, 1), ("S", 4, 1), ("s", 4, 1))
        inner = rows(("j", 4, 9), ("w", 4, 1), ("s", 4, 2))
        back_ = rows(("J", 4, 6), ["JcJJ"], ("J", 4, 2), ("W", 4, 1), ("s", 4, 2))
        r, l = (sleeve, inner) if outer_right else (inner, sleeve)
        sk.box(box, r, sleeve, l, back_, fill("J", 4, 4), fill("s", 4, 4), light=0.06)
    # the armband on the left sleeve (outer layer), the seal on its outer face
    band = ["AAAA", "aaaa"]
    seal = ["AGYA", "agga"]
    clear = fill(".", 4, 2)
    sk.box(L_SLEEVE, rows(clear, band, fill(".", 4, 8)), rows(clear, band, fill(".", 4, 8)),
           rows(clear, seal, fill(".", 4, 8)), rows(clear, band, fill(".", 4, 8)))
    # trousers with a crease, polished shoes
    for box, outer_right in ((R_LEG, True), (L_LEG, False)):
        outer = rows(("N", 4, 10), ("O", 4, 1), ("Q", 4, 1))
        inner = rows(("n", 4, 10), ("O", 4, 1), ("Q", 4, 1))
        front = rows(["NKNN" if outer_right else "NNKN"] * 10, ["oooo", "OOOO"])
        back_ = rows(("N", 4, 10), ("O", 4, 1), ("Q", 4, 1))
        r, l = (outer, inner) if outer_right else (inner, outer)
        sk.box(box, r, front, l, back_, fill("N", 4, 4), fill("Q", 4, 4), light=0.04)


def officer(look):
    if look == 0:
        tone, hair, iris, tie = "light", ("2a211e", "1a1413", "463830"), "3d5a8a", ("35609e", "264a7c")
    elif look == 1:
        tone, hair, iris, tie = "light", ("7a3a22", "5a2a18", "a4583a"), "4e7a44", ("7a2433", "5a1825")
    else:
        tone, hair, iris, tie = "deep", ("72747c", "50525a", "a8aab2"), "4a3424", ("2c4470", "1e3054")
    pal = dict(SUIT)
    pal.update(skin_pal(tone))
    pal.update(hair_pal(*hair))
    pal.update(eye_pal(iris))
    pal["T"], pal["t"] = (tie[0], 0.02), (tie[1], 0.02)
    pal["B"] = (hair[1], 0.0)
    pal["z"] = (hair[1], 0.05)   # beard
    sk = Skin(100 + look, pal)
    officer_body(sk)
    if look == 0:
        # a neat dark crop, the fringe swept to one side
        front = ["HHHHHHHH", "HHkHHHHH", "hSSSSSSh", "SBBSSBBS", "SEISSIES", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
        side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHh", "HHHHHhSS", "HHHHeSSS", "HHHheSSS", "hHHsSSSs", "hhssssss"]
        back = ["HHHHHHHH", "HHkHHHHH", "HHHHHkHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
        top = ["HHHHHHHH", "HkHHHHHH", "HHHHHkHH", "HHhHHHHH", "HHhHHHkH", "HkhHHHHH", "HHhHHHHH", "HHhHHHHH"]
        hat_front = ["HHkHHHHH", "HHHHH..."] + ["........"] * 6
        hat_side = ["HHHHHHHH", "HHHHHHHH", "HHHH....", "HHH....."] + ["........"] * 4
        hat_back = ["HHHHHHHH", "HHHHHHHH", "HHkHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "........", "........"]
        hat_top = ["HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HkHHHHHH", "HHHHHHkH", "HHHHHHHH", "HHHHkHHH", "HHHHHHHH"]
    elif look == 1:
        # auburn hair tied back in a tail, a few strands framing the face
        front = ["HHHHHHHH", "HHkHHHHH", "HHSSSSHH", "HBBSSBBH", "hEISSIEh", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
        side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHS", "HHHHHHHS", "HHHHHHSS", "HHHHHhSS", "HHHHhsSs"]
        back = ["HHHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhHHHHhh"]
        top = ["HHHHHHHH", "HHHkHHHH", "HHHhHHHH", "HkHhHHkH", "HHHhHHHH", "HHHhHkHH", "HkHhHHHH", "HHHhHHHH"]
        hat_front = ["HHkHHHHH", "H......H"] + ["........"] * 6
        hat_side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHH.", "HHHHH...", "HHHH....", "HHH.....", "HH......", "........"]
        hat_back = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHxxHHH", "..HkkH..", "..HkHH..", "...HH...", "...hh..."]
        hat_top = ["HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HkHHHHkH", "HHHHHHHH", "HHHHHkHH", "HkHHHHHH", "HHHHHHHH"]
        sk.pal["x"] = (hexc("2a1410"), 0.0)
    else:
        # silver hair swept back, a high hairline and a short beard
        front = ["HkHHkHHk", "HHkHHkHH", "hSSSSSSh", "SBBSSBBS", "SEISSIES", "sSSssSSs", "szzmmzzs", "dzzzzzzd"]
        side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHSS", "HHHHHSSS", "HHHHeSSS", "HHHheSSz", "hHHSSSzz", "hhSSSzzz"]
        back = ["hHHhHHHh", "HkHHhkHH", "HHhHHHhH", "hHHHhHHH", "HHhHHHhH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
        top = ["hHHhHHhH", "HkHHhkHH", "HkHhHkHh", "hHkHHHkH", "HHkhHHkH", "HhkHHhkH", "HHHkHHHk", "HhHkHHhk"]
        # cropped close: no volume on the outer layer
        hat_front = hat_side = hat_back = hat_top = ["........"] * 8
        sk.pal["z"] = (hexc("8e9098"), 0.08)
    sk.box(HEAD, side, front, mirror(side), back, top, fill("d", 8, 8))
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    return sk


# ------------------------------------------------------------------------------------------------- the brokers

def broker_coat(sk, edge, lining):
    """The long coat on the outer layers: open at the front over the vest, its skirt over the legs, turned-back cuffs."""
    side = rows(("C", 4, 11), ("c", 4, 1))
    front = rows(["CE....EC"] * 3, ["C" + lining + "...." + lining + "C"] * 8, ["c" + lining + "...." + lining + "c"])
    back = rows(["CCCCcCCC"] * 11, ["ccccdccc"])
    top = ["CCCCCCCC", "CC....CC", "CE....EC", "CE....EC"]
    sk.box(JACKET, side, front, side, back, top, ["cc....cc"] * 4)
    # the skirt: the outer side and the back of each leg, the outer half of its front, lined at the opening
    clear = "...."
    for box, right_leg in ((R_PANTS, True), (L_PANTS, False)):
        outer = rows(("C", 4, 8), ("c", 4, 1), (".", 4, 3))
        inner = rows((".", 4, 12))
        fr = rows(["CC.." if right_leg else "..CC"] * 8, ["cc.." if right_leg else "..cc"], [clear] * 3)
        bk = rows(("C", 4, 8), ("c", 4, 1), (".", 4, 3))
        r, l = (outer, inner) if right_leg else (inner, outer)
        sk.box(box, r, fr, l, bk)
    cuff = rows((".", 4, 8), ("E", 4, 1), ("C", 4, 1), (".", 4, 2))
    for box in (R_SLEEVE,):
        sk.box(box, cuff, cuff, cuff, cuff)
    # the left sleeve keeps the same cuff (the association's band is the officers')
    sk.box(L_SLEEVE, cuff, cuff, cuff, cuff)


def broker_limbs(sk, hand, hand_dark):
    for box, outer_right in ((R_ARM, True), (L_ARM, False)):
        out = rows(("C", 4, 10), (hand, 4, 1), (hand_dark, 4, 1))
        inner = rows(("c", 4, 10), (hand_dark, 4, 2))
        r, l = (out, inner) if outer_right else (inner, out)
        sk.box(box, r, out, l, out, fill("C", 4, 4), fill(hand_dark, 4, 4), light=0.05)
    for box, outer_right in ((R_LEG, True), (L_LEG, False)):
        outer = rows(("N", 4, 6), ("O", 4, 2), ("u", 4, 1), ("O", 4, 2), ("Q", 4, 1))
        inner = rows(("n", 4, 6), ("O", 4, 2), ("u", 4, 1), ("O", 4, 2), ("Q", 4, 1))
        front = rows(("N", 4, 6), ("o", 4, 1), ("O", 4, 1), ("u", 4, 1), ("O", 4, 1), ("o", 4, 1), ("Q", 4, 1))
        r, l = (outer, inner) if outer_right else (inner, outer)
        sk.box(box, r, front, l, outer, fill("N", 4, 4), fill("Q", 4, 4), light=0.04)


def broker(look):
    if look == 0:
        pal = {
            "C": ("3e2c22", 0.06), "c": ("2a1e17", 0.05), "E": ("5a4230", 0.04), "V": ("3a2550", 0.03), "d": ("1c140f", 0.0),
            "Z": ("34343c", 0.05), "z": ("26262c", 0.04), "U": ("2a1e16", 0.04), "y": ("c8a050", 0.0),
            "X": ("5e4430", 0.05), "x": ("44301f", 0.04), "*": ("d8bcff", 0.0), "+": ("8a5cd6", 0.0), "r": ("1a1418", 0.0),
            "N": ("2e2e36", 0.05), "n": ("222229", 0.04), "O": ("34241c", 0.05), "o": ("4c3629", 0.04), "u": ("1e150e", 0.0),
            "Q": ("141010", 0.0), "K": ("222024", 0.03), "k": ("3a373c", 0.02),
            "R": ("3a3438", 0.06), "q": ("4e474c", 0.04), "w": ("262126", 0.04),
            "M": ("5c2c4e", 0.05), "m": ("40203a", 0.04), "j": ("78426a", 0.03),
            "H": ("1e1a1c", 0.05), "h": ("141114", 0.04),
            "W": ("d8d0c4", 0.0), "I": ("d0a040", 0.0), "B": ("141114", 0.0),
        }
        pal.update({k: v for k, v in skin_pal("tan").items() if k in "Ssdlme"})
        sk = Skin(200, pal)
        # under the hood: a shadowed brow, gold eyes, the scarf over mouth and nose
        front = ["HHHHHHHH", "HHHHHHHH", "hhddddhh", "dBBddBBd", "dWIddIWd", "MMMMMMMM", "MjMMmMjM", "mMMmmMMm"]
        side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHd", "HHHHHHdd", "HHHHedds", "HHHhMMMM", "hHHMMMmM", "hhMMMmMm"]
        back = ["HHHHHHHH"] * 5 + ["hHHHHHHh", "MMMMMMMM", "mmmmmmmm"]
        sk.box(HEAD, side, front, mirror(side), back, fill("H", 8, 8), fill("d", 8, 8))
        hood_front = ["qqqqqqqq", "qwwwwwwq", "q......q", "q......q", "q......q", "MMMMMMMM", "MjMMmMjM", "mMMmmMMm"]
        hood_side = ["RRRRRRRq", "RRRRRRRq", "RRRRRRRq", "wRRRRRRq", "wRRRRRRq", "wRRRRMMM", "wwRRRMMm", "wwwRMMmm"]
        hood_back = ["RRRRRRRR", "RRRRRRRR", "RRRwRRRR", "RRRwRRRR", "RRRRwRRR", "RRRRwRRR", "wRRRRRRw", "wwwwwwww"]
        hood_top = ["wwwwwwww", "RRRwRRRR", "RRRwRRRR", "RRRRwRRR", "RRRRwRRR", "RRRRRRRR", "RRRRRRRR", "qqqqqqqq"]
        sk.box(HAT, hood_side, hood_front, mirror(hood_side), hood_back, hood_top, None)
        # the vest under the coat: the scarf's tail, a gate shard on a cord, a belt of pouches
        side_b = rows(("Z", 4, 7), ("U", 4, 1), ("Z", 4, 2), ("N", 4, 2))
        front_b = ["ZZZMMZZZ", "ZZZMmrZZ", "ZzZmMZrZ", "ZZZMZZ*Z", "ZZZZZZ+Z", "ZzZZZZZZ", "ZZZZZZzZ",
                   "UUUyUUUU", "XXZZZXXZ", "xxZZZxxZ", "NNNNNNNN", "NNNNNNNN"]
        back_b = rows(("Z", 8, 7), ("U", 8, 1), ("Z", 8, 2), ("N", 8, 2))
        sk.box(BODY, side_b, front_b, side_b, back_b, ["ZZZZZZZZ", "ZZMMMMZZ", "ZZMmmMZZ", "ZZZMMZZZ"], fill("N", 8, 4), light=0.05)
        broker_limbs(sk, "K", "k")
        broker_coat(sk, "E", "V")
        return sk
    pal = {
        "C": ("3c464c", 0.06), "c": ("2a3237", 0.05), "E": ("546270", 0.04), "V": ("5e3020", 0.03), "d": ("1d2327", 0.0),
        "Z": ("5a402c", 0.05), "z": ("44301f", 0.04), "U": ("201812", 0.03), "y": ("c8a050", 0.0), "Y": ("e6c46a", 0.0),
        "b": ("2a1e14", 0.0),
        "N": ("2c2c32", 0.05), "n": ("202026", 0.04), "O": ("1c1a1e", 0.03), "o": ("38353c", 0.02), "u": ("101012", 0.0),
        "Q": ("0e0d10", 0.0),
        "P": ("5a5e4c", 0.1), "p": ("454838", 0.06), "T": ("383b2e", 0.03),
        "M": ("8a3c28", 0.05), "m": ("68291a", 0.04),
        "H": ("4a2a1e", 0.06), "h": ("341d14", 0.05),
        "F": ("b08c4c", 0.0), "L": ("5e4a92", 0.0), "f": ("b4a0e8", 0.0), "B": ("341d14", 0.0),
        "t": ("bf9a80", 0.06),
    }
    pal.update(skin_pal("light"))
    sk = Skin(201, pal)
    # a tweed cap, round violet-tinted glasses, stubble
    front = ["HHHHHHHH", "HHHHHHHH", "hSSSSSSh", "SBBSSBBS", "FfLFFfLF", "sSSssSSs", "tSSmmSSt", "dttttttd"]
    side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHS", "HHHHHSSS", "HHHHeFFF", "HHHheSSS", "hHHSSSSt", "hhSSSStt"]
    back = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
    sk.box(HEAD, side, front, mirror(side), back, fill("H", 8, 8), fill("d", 8, 8))
    cap_front = ["PPPPPPPP", "TTTTTTTT"] + ["........"] * 6
    cap_side = ["PPPPPPPP", "pppppppT"] + ["........"] * 6
    cap_back = ["PPPPPPPP", "PPPPPPPP", "pppppppp"] + ["........"] * 5
    cap_top = ["pppppppp", "PPPPPPPP", "PPPpPPPP", "PPPpPPPP", "PPPPpPPP", "PPPPpPPP", "PPPPPPPP", "TTTTTTTT"]
    sk.box(HAT, cap_side, cap_front, mirror(cap_side), cap_back, cap_top, None)
    # a rust scarf at the throat, a brown waistcoat with a watch on a chain
    side_b = rows(("M", 4, 2), ("Z", 4, 6), ("U", 4, 1), ("N", 4, 3))
    front_b = ["MMMMMMMM", "mMMMMMMm", "ZZMmZbZZ", "ZZmMZZZZ", "ZZZmZbZZ", "ZYZZZZYZ", "ZZYYYYZZ",
               "ZZZZZbZZ", "UUUyUUUU", "NNNNNNNN", "NNNNNNNN", "NNNNNNNN"]
    back_b = rows(("M", 8, 2), ("z", 8, 6), ("U", 8, 1), ("N", 8, 3))
    sk.box(BODY, side_b, front_b, side_b, back_b, ["ZZZZZZZZ", "ZMMMMMMZ", "ZMmmmmMZ", "MMMMMMMM"], fill("N", 8, 4), light=0.05)
    broker_limbs(sk, "S", "s")
    broker_coat(sk, "E", "V")
    return sk


SKINS = {f"officer_{i}": (lambda i=i: officer(i)) for i in range(3)}
SKINS.update({f"broker_{i}": (lambda i=i: broker(i)) for i in range(2)})


# ------------------------------------------------------------------------------------------------- preview

def preview(images, out, views=("fl", "front", "back")):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import armor_rig as rig  # noqa: E402
    import preview_model as pm  # noqa: E402
    boxes = [
        ("head", (-4, -8, -4), (8, 8, 8), (0, 0), 0.0), ("head", (-4, -8, -4), (8, 8, 8), (32, 0), 0.5),
        ("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.0), ("body", (-4, 0, -2), (8, 12, 4), (16, 32), 0.25),
        ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), 0.0), ("right_arm", (-3, -2, -2), (4, 12, 4), (40, 32), 0.25),
        ("left_arm", (-1, -2, -2), (4, 12, 4), (32, 48), 0.0), ("left_arm", (-1, -2, -2), (4, 12, 4), (48, 48), 0.25),
        ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.0), ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 32), 0.25),
        ("left_leg", (-2, 0, -2), (4, 12, 4), (16, 48), 0.0), ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 48), 0.25),
    ]
    pose = {"right_arm": (-14, 0, 4), "left_arm": (12, 0, -4), "right_leg": (10, 0, 0), "left_leg": (-10, 0, 0)}
    quads = []
    for part, origin, size, uv, inflate in boxes:
        b = rig.Box(part, part, origin, size, uv, inflate)
        quads += rig.box_quads(b, rig.part_pose(part, pose))
    scene = []
    for P, rect, n in quads:
        Q = P.copy()
        Q[:, 1] = 24 - Q[:, 1]
        nn = np.array(n, float)
        nn[1] = -nn[1]
        scene.append((Q, rect, nn))
    columns = []
    for img in images:
        atlas = np.asarray(img.resize((128, 128), Image.NEAREST))
        glow = np.zeros_like(atlas)
        tiles = [pm.render(scene, atlas, glow, (220, 330), target=(0, 16.5, 0), dist=60, fov=38, **pm.VIEWS[v]) for v in views]
        columns.append(np.concatenate(tiles, axis=0))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(np.concatenate(columns, axis=1)).save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    images = []
    for name, make in SKINS.items():
        img = make().image()
        img.save(os.path.join(OUT, name + ".png"))
        images.append(img)
        print("wrote", name)
    if args.preview:
        print("preview", preview(images, PREVIEW))


if __name__ == "__main__":
    main()
