#!/usr/bin/env python3
"""
The hunter's own story (see com.beyondsolo.story): their mother's skins and the Long Sleep's things.

- mother_0..2 (+ _asleep, the same with her eyes shut): a woman of about fifty in the slim player shape (64x64, both
  layers): a long dress with a sash at the waist and a skirt to the ankle (the outer leg layer flares it), a knitted
  shawl over her shoulders, knotted at the chest, slippers. Three people:
    0  fair, dark brown hair going grey, parted in the middle and pinned in a low bun; brown eyes; a dusty rose dress
       and a cream shawl.
    1  tan, black hair with a grey streak, tied back with a fringe; dark eyes; a sage dress and an oatmeal shawl.
    2  dark, short silver curls; amber eyes; a lavender dress and a wine-red shawl.
- item icons (32x32): elder_root_dew (a round vial of pale green light with a root curled round its neck),
  crimson_kings_chalice (a gold goblet of crimson, a ruby on the cup), elixir_of_waking (a tall flask of gold light),
  home_cooked_stew (a wooden bowl of stew, steam rising); their item models.
- the Home Comfort effect icon (18x18): a warm heart under a little roof.

Faces are drawn pixel by pixel from the maps below (one letter a colour, '.' clear), with the association art kit's
weave and light (tools/gen_association_assets.py).

Run from the repo root:   python3 tools/gen_story_assets.py             (writes everything)
                          python3 tools/gen_story_assets.py --preview   (and build/preview/story_*.png)
Needs: pillow, numpy.
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_association_assets import (HAT, HEAD, BODY, JACKET, R_LEG, R_PANTS, L_LEG, L_PANTS,  # noqa: E402
                                    Skin, eye_pal, fill, hair_pal, hexc, mirror, rows, skin_pal)
import gen_combat_assets as cb  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
SKIN_OUT = os.path.join(ASSETS, "textures", "entity", "story")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")
EFFECTS = os.path.join(ASSETS, "textures", "mob_effect")
PREVIEW = os.path.join(ROOT, "build", "preview")

# the slim arms (3 wide) of the player model
R_ARM, R_SLEEVE = (40, 16, 3, 12, 4), (40, 32, 3, 12, 4)
L_ARM, L_SLEEVE = (32, 48, 3, 12, 4), (48, 48, 3, 12, 4)

LOOKS = [
    # tone, hair (base, shade, light), grey strands, iris, dress (base, fold), sash (base, dark), shawl (base, knit, fringe),
    # stockings, slippers (base, light)
    dict(tone="light", hair=("5a3a2a", "3e281c", "7a5440"), grey="aaa49e", iris="6a4a2e", tie="c8b48a",
         dress=("b07a7e", "8c5a60"), sash=("74444e", "56303a"), shawl=("ece0c8", "cbbd9f", "b2a284"),
         stockings="8c7c72", slippers=("5a3a2a", "7a5440")),
    dict(tone="tan", hair=("201c1e", "141114", "3a3438"), grey="9a98a0", iris="3e2c20", tie="7a2e34",
         dress=("7e8e6c", "5e6c50"), sash=("4e5a42", "38422e"), shawl=("d2c0a0", "ad9b7c", "8f7e62"),
         stockings="6a5a50", slippers=("3e2a20", "5e4434")),
    dict(tone="deep", hair=("cdc8c2", "a29c96", "ece8e2"), grey="ffffff", iris="4a2c1a", tie="54466e",
         dress=("8c7cac", "6a5a8a"), sash=("54466e", "3c3054"), shawl=("843a4c", "62283a", "4c1c2c"),
         stockings="3e2e2a", slippers=("2e201a", "4a362c")),
]


def palette(look, asleep=False):
    L = LOOKS[look]
    tone = skin_pal(L["tone"])
    if asleep:
        # years asleep: her colour has gone out of her, a little grey and pale; it comes back when she wakes
        tone = {k: (pale(v[0]), v[1]) for k, v in tone.items()}
    pal = {}
    pal.update(tone)
    pal.update(hair_pal(*L["hair"]))
    pal.update(eye_pal(L["iris"]))
    pal.update({
        "g": (L["grey"], 0.04),
        "b": (L["hair"][1], 0.0),            # brows
        "B": (mixc(tone["d"][0], "24181a", 0.55), 0.0),   # shut lids: a soft dark line
        "r": (tone["S"][0] if asleep else mixc(tone["S"][0], "d27a7a", 0.35), 0.0),   # colour in the cheeks
        "y": (L["tie"], 0.02),               # what holds her hair
        "D": (L["dress"][0], 0.035), "o": (L["dress"][1], 0.03),
        "w": (L["sash"][0], 0.03), "x": (L["sash"][1], 0.03),
        "A": (L["shawl"][0], 0.04), "a": (L["shawl"][1], 0.04), "f": (L["shawl"][2], 0.04),
        "T": (L["stockings"], 0.03),
        "Q": (L["slippers"][0], 0.03), "q": (L["slippers"][1], 0.03),
        "c": ("f4ece0", 0.02),               # the lace at the neckline
    })
    return pal


def pale(h):
    """A skin colour with the life gone out of it: a quarter greyer, a touch lighter."""
    c = hexc(h)
    grey = c @ np.array([0.299, 0.587, 0.114])
    m = (c * 0.72 + grey * 0.28) * 1.04
    return "".join(f"{int(round(min(255, v))):02x}" for v in m)


def curls(w, h, shift=0):
    """Tight curls: light tops and shaded undersides in a staggered weave."""
    out = []
    for y in range(h):
        row = ""
        for x in range(w):
            if y % 2 == 0:
                row += "k" if (x + y + shift) % 4 == 0 else "H"
            else:
                row += "h" if (x + y + shift) % 4 == 2 else "H"
        out.append(row)
    return out


def cut(rows_, keep):
    """Clears what is not kept: keep[y] is the columns (as a string mask, '#' kept) of each row."""
    return ["".join(c if m == "#" else "." for c, m in zip(r, k)) for r, k in zip(rows_, keep)]


def mixc(a, b, t):
    ca, cb_ = hexc(a), hexc(b)
    m = ca * (1 - t) + cb_ * t
    return "".join(f"{int(round(v)):02x}" for v in m)


# ------------------------------------------------------------------------------------------------- the head

def head(sk, look, asleep):
    # eyes: open (white, iris), or shut (a line of lashes under a soft lid)
    eyes = "hEISSIEh" if not asleep else "hBBSSBBh"
    if look == 0:
        front = ["HHHgHHHH", "HHHHhHHH", "HhSSSShH", "HbbSSbbH", eyes, "SSSssSSS", "SrSmmSrS", "dSSSSSSd"]
        side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHS", "HHHHHHSS", "HHHgHeSS", "HHHHheSS", "hHHHHSSS", "hhhHSSSs"]
        back = ["HHHHHHHH", "HHHgHHHH", "HHHHHHHH", "HHHHHHgH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhHHHHhh"]
        top = ["HHHHHHHH", "HHHgHHHH", "HHHhHHHH", "HHHhHHkH", "HkHhHHHH", "HHHhHgHH", "HHHhHHHH", "HHHHHHHH"]
        hat_front = ["HHHkHHHH", "Hh....hH", "H......H"] + ["........"] * 5
        hat_side = ["HHHHHHHH", "HHHHHHH.", "HHHHHH..", "HHHgH...", "HHH.....", "HH......", "........", "........"]
        # the low bun at the back, a pin through it
        hat_back = ["HHHHHHHH", "HHHgHHHH", "hHHHHHHh", "..hHHh..", ".hHHyHh.", ".HHyHgH.", "..hHHh..", "........"]
        hat_top = ["HHHHHHHH", "HHHkHHHH", "HHHhHHHH", "HkHhHHHH", "HHHhHHkH", "HHHhHHHH", "HHHHHHHH", "HHHHHHHH"]
    elif look == 1:
        # tied back, a fringe swept to one side and a streak of grey from the temple
        front = ["HHHHHHHH", "HHHHHHgH", "HHHSSSgH", "HbbSSbbH", eyes, "SSSssSSS", "SrSmmSrS", "dSSSSSSd"]
        side = ["HHHHHHgg", "HHHHHHgH", "HHHHHHHS", "HHHHHHSS", "HHHHHeSS", "HHHHheSS", "hHHHHSSS", "hhHHSSSs"]
        back = ["HHHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hHHHHHHh", "hhhHHhhh"]
        top = ["HHHHHHHH", "HHHHHHkH", "HkHHHHgH", "HHHHHHgH", "HHHkHHHH", "HHHHHHHH", "HkHHHHHH", "HHHHHHHH"]
        hat_front = ["HHHHHHgH", "HHHH...H", "H......H"] + ["........"] * 5
        hat_side = ["HHHHHHgg", "HHHHHHH.", "HHHHHH..", "HHHHH...", "HHH.....", "HH......", "H.......", "........"]
        # the tail tied at the nape, a dark band round it
        hat_back = ["HHHHHHHH", "HHHHHHHH", "HHHkHHHH", "hHHHHHHh", "...yy...", "...HH...", "...kH...", "...hh..."]
        hat_top = ["HHHHHHHH", "HHHHHHkH", "HkHHHHgH", "HHHHHHgH", "HHHkHHHH", "HHHHHHHH", "HkHHHHHH", "HHHHHHHH"]
    else:
        # short silver curls all round
        front = curls(8, 2) + ["hSSSSSSh", "SbbSSbbS", eyes.replace("h", "S"), "SSSssSSS", "SrSmmSrS", "dSSSSSSd"]
        side = [a[:n] + b[n:] for a, b, n in zip(curls(8, 8, 1), ["SSSSSSSS"] * 8, (8, 8, 7, 6, 5, 5, 4, 3))]
        side[4] = side[4][:5] + "eSS"
        side[5] = side[5][:4] + "heSS"
        back = curls(8, 6, 2) + ["hHhhHhhH", "dddddddd"]
        top = curls(8, 8, 3)
        # the curls stand off the head a little, a scalloped edge where they end
        hat_front = cut(curls(8, 8), ["########", "##....##", "#......#"] + ["........"] * 5)
        hat_side = cut(curls(8, 8, 1), ["########", "########", "#######.", "######..", "#####...", "##.#....",
                                         "........", "........"])
        hat_back = cut(curls(8, 8, 2), ["########"] * 5 + ["##.##.##", "#..#..#.", "........"])
        hat_top = curls(8, 8, 3)
    sk.box(HEAD, side, front, mirror(side), back, top, fill("d", 8, 8))
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)


# ------------------------------------------------------------------------------------------------- the dress and shawl

def body(sk):
    # the dress: a round neckline with a little lace, darts at the bodice, the sash knotted at one side, the skirt's
    # folds running on down the legs
    front = ["DDcSScDD", "DDDccDDD", "DDDDDDDD", "DDDDDDDD", "DDDDDDDD", "DoDDDDoD", "DoDDDDoD",
             "wwwwwwww", "xxxxxwqx", "DoDDDDoD", "DoDDDDoD", "DoDDDDoD"]
    side = rows(("D", 4, 7), ("w", 4, 1), ("x", 4, 1), ["DDoD"] * 3)
    back = rows(["DDDDDDDD"] * 3, ["DDDooDDD"] * 4, ["wwwwwwww", "xxxxxxxx"], ["DoDDDDoD"] * 3)
    top = ["DDDDDDDD", "DDcSScDD", "DDcSScDD", "DDDDDDDD"]
    sk.box(BODY, side, front, side, back, top, fill("o", 8, 4), light=0.05)
    # the shawl over it: round the shoulders, one end crossed over the other and knotted at the chest; knitted in ribs,
    # its fringe at the back
    rib = ["AAaAAaAA", "AAaAAaAA"]
    j_front = ["AAa..aAA", "AAAa.aAA", ".AAAaAA.", "..aAAAa.", "...AaA..", "...fAf..",
               "...f.f..", "........", "........", "........", "........", "........"]
    j_side = rows(["AaAA"] * 5, ["fAfA", "f.f."], (".", 4, 5))
    j_back = rows(rib * 3, ["AAaAAaAA"], ["fAfAfAfA", "f.f.f.f."], (".", 8, 3))
    j_top = ["AAaAAaAA", "AaA..AaA", "AA....AA", "aA....Aa"]
    sk.box(JACKET, j_side, j_front, j_side, j_back, j_top, None)


def arms(sk):
    # long sleeves, the hands; the shawl falls over the top of the arm (the outer layer)
    for box, sleeve_box in ((R_ARM, R_SLEEVE), (L_ARM, L_SLEEVE)):
        out = rows(("D", 4, 9), ("o", 4, 1), ("S", 4, 1), ("s", 4, 1))
        face3 = rows(("D", 3, 9), ("o", 3, 1), ("S", 3, 1), ("s", 3, 1))
        inner = rows(("o", 4, 9), ("o", 4, 1), ("s", 4, 2))
        back3 = rows(("D", 3, 9), ("o", 3, 1), ("s", 3, 2))
        sk.box(box, out, face3, inner, back3, fill("D", 3, 4), fill("s", 3, 4), light=0.05)
        drape4 = rows(["AaAA", "AAaA", "aAAf", "f.f."], (".", 4, 8))
        drape3 = rows(["AaA", "AAa", "fAf", ".f."], (".", 3, 8))
        sk.box(sleeve_box, drape4, drape3, drape4, drape3, ["AAA", "AaA", "AAA", "AAA"], None)


def legs(sk):
    # under the skirt: the skirt itself down to the ankle (a dark band at the hem), stockings, slippers. The folds
    # carry on from the body's: column 1 of the right leg, column 2 of the left.
    for box, right in ((R_LEG, True), (L_LEG, False)):
        fold = "DoDD" if right else "DDoD"
        outer = rows(["DDoD"] * 9, ("x", 4, 1), ("T", 4, 1), ("Q", 4, 1))
        inner = rows(("o", 4, 10), ("T", 4, 1), ("Q", 4, 1))
        front = rows([fold] * 9, ("x", 4, 1), ("T", 4, 1), ["QqqQ"])
        back = rows([fold] * 9, ("x", 4, 1), ("T", 4, 1), ("Q", 4, 1))
        r, l = (outer, inner) if right else (inner, outer)
        sk.box(box, r, front, l, back, fill("D", 4, 4), fill("Q", 4, 4), light=0.04)
    # the skirt flares out over the legs (outer layer): the same folds, the hem band at the bottom
    for box, right in ((R_PANTS, True), (L_PANTS, False)):
        fold = "DoDD" if right else "DDoD"
        outer = rows(["DDoD"] * 9, ("x", 4, 1), (".", 4, 2))
        inner = rows((".", 4, 12))
        front = rows([fold] * 9, ("x", 4, 1), (".", 4, 2))
        back = rows([fold] * 9, ("x", 4, 1), (".", 4, 2))
        r, l = (outer, inner) if right else (inner, outer)
        sk.box(box, r, front, l, back)


def mother(look, asleep):
    sk = Skin(300 + look, palette(look, asleep))
    head(sk, look, asleep)
    body(sk)
    arms(sk)
    legs(sk)
    return sk


# ------------------------------------------------------------------------------------------------- items

S = 32


def rgba(h, a=255):
    return cb.hexc(h, a)


def save_item(img, name):
    os.makedirs(ITEM_TEX, exist_ok=True)
    os.makedirs(ITEM_MODELS, exist_ok=True)
    img.save(os.path.join(ITEM_TEX, name + ".png"))
    with open(os.path.join(ITEM_MODELS, name + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/" + name}}, f, indent=2)
        f.write("\n")


def put(a, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        a[y, x] = c


def dew_icon():
    """A round vial of pale green light, an old root curled round its neck."""
    img = np.array(cb.bottle("round", cb.hexc("a6e57e")))
    root, root_dk = rgba("7a5634"), rgba("4e341c")
    for x, y, c in ((12, 7, root), (13, 8, root), (14, 8, root), (15, 9, root), (16, 9, root), (17, 8, root),
                    (18, 8, root), (19, 9, root), (20, 10, root_dk), (20, 11, root_dk), (19, 12, root),
                    (11, 8, root_dk), (10, 9, root_dk), (10, 10, root)):
        put(img, x, y, c)
    # the light inside: a brighter heart and a couple of motes
    for x, y in ((15, 19), (16, 19), (15, 20), (16, 20), (17, 21)):
        put(img, x, y, rgba("e4ffc8"))
    for x, y in ((13, 22), (19, 17)):
        put(img, x, y, rgba("ffffff"))
    return Image.fromarray(img)


def chalice_icon():
    """A gold goblet brimming with crimson, a ruby set in the cup."""
    cup = cb.poly_mask([(7, 6), (25, 6), (24, 10), (21, 15), (17, 17), (15, 17), (11, 15), (8, 10)], S)
    stem = cb.poly_mask([(14.5, 17), (17.5, 17), (17, 24), (15, 24)], S)
    knot = cb.poly_mask([(13.5, 19.5), (18.5, 19.5), (18.5, 21.5), (13.5, 21.5)], S)
    base = cb.poly_mask([(15, 24), (17, 24), (23, 27), (23, 28.5), (9, 28.5), (9, 27)], S)
    shell = cup | stem | knot | base
    gold, gold_lt, gold_dk = rgba("e8be55"), rgba("fff0b0"), rgba("8a6224")
    img = np.zeros((S, S, 4), np.uint8)
    ys, xs = np.nonzero(shell)
    x0, x1 = xs.min(), xs.max()
    for y, x in zip(ys, xs):
        t = (x - x0) / max(1, x1 - x0)
        c = cb.shade(gold, 0.35 - 0.8 * t) if t > 0.15 else gold_lt
        if t > 0.8:
            c = gold_dk
        img[y, x] = c
    # the wine at the brim, darker to the right
    for x in range(8, 25):
        for y in (6, 7):
            if cup[y, x]:
                img[y, x] = cb.shade(rgba("d0284a"), 0.25 - 0.6 * (x - 8) / 16 - (0.1 if y == 7 else 0))
    img[6, 10] = rgba("ff9aac")
    img[6, 11] = rgba("ff9aac")
    # a ruby on the cup, and a band round its rim
    for x in range(8, 25):
        if cup[8, x]:
            img[8, x] = gold_dk
    for x, y, c in ((15, 11, rgba("ff5070")), (16, 11, rgba("c8183a")), (15, 12, rgba("c8183a")), (16, 12, rgba("8a0c22")),
                    (15, 10, rgba("ffb0bc"))):
        img[y, x] = c
    return cb.outline(Image.fromarray(img), (34, 18, 14, 255))


def elixir_icon():
    """A tall flask of gold light, sparkling."""
    img = np.array(cb.bottle("flask", cb.hexc("ffd98a"), cork=cb.hexc("c8a050")))
    for x, y in ((15, 21), (16, 21), (16, 22), (15, 22)):
        put(img, x, y, rgba("fff8e0"))
    for x, y in ((11, 24), (20, 19), (13, 19)):
        put(img, x, y, rgba("ffffff"))
    # a twist of gold thread round the neck
    for x in range(13, 19):
        put(img, x, 8, rgba("e8be55") if x % 2 else rgba("8a6224"))
    # a glint beside it
    for x, y in ((25, 6), (24, 5), (26, 5), (25, 4), (25, 7)):
        put(img, x, y, rgba("fff0b0") if (x, y) != (25, 6) else rgba("ffffff"))
    return Image.fromarray(img)


def stew_icon():
    """A wooden bowl of stew, carrot and potato in it, steam rising."""
    bowl = cb.poly_mask([(4, 15), (28, 15), (27, 19), (23, 24), (18, 26), (14, 26), (9, 24), (5, 19)], S)
    foot = cb.poly_mask([(12, 25.5), (20, 25.5), (21, 28), (11, 28)], S)
    stew = cb.poly_mask([(5, 13), (27, 13), (27.5, 15.5), (4.5, 15.5)], S)
    img = np.zeros((S, S, 4), np.uint8)
    wood, wood_dk, wood_lt = rgba("9a6a3a"), rgba("5e3e20"), rgba("c08a52")
    for mask in (bowl, foot):
        ys, xs = np.nonzero(mask)
        for y, x in zip(ys, xs):
            t = (x - 4) / 24
            c = cb.shade(wood, 0.25 - 0.6 * t)
            if (y + (x // 3)) % 4 == 0:
                c = cb.shade(c, -0.12)   # the grain
            img[y, x] = c
    ys, xs = np.nonzero(stew)
    for y, x in zip(ys, xs):
        img[y, x] = cb.shade(rgba("b8642a"), 0.1 - 0.25 * (x - 5) / 22)
    for x, y, c in ((8, 13, rgba("ff9a3a")), (9, 13, rgba("ff9a3a")), (14, 13, rgba("f0d890")), (15, 13, rgba("f0d890")),
                    (15, 14, rgba("d8bc70")), (20, 13, rgba("ff9a3a")), (21, 14, rgba("e07a20")), (24, 13, rgba("7aa83a")),
                    (11, 14, rgba("7aa83a")), (18, 14, rgba("f0d890"))):
        img[y, x] = c
    for x in range(5, 28):
        if bowl[15, x]:
            img[15, x] = wood_lt
    img = np.array(cb.outline(Image.fromarray(img), (40, 22, 12, 255)))
    # the steam: soft curls, half see-through
    for k, x0 in enumerate((10, 16, 22)):
        for i in range(8):
            y = 11 - i
            x = int(round(x0 + math.sin(i * 0.9 + k) * 1.4))
            if 0 <= y < S:
                img[y, x] = (255, 255, 255, int(150 * (1 - i / 9)))
    return Image.fromarray(img)


def comfort_icon(n=18):
    """Home Comfort: a warm heart under a little roof."""
    ss = 8
    w = n * ss
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = w / 2
    # the roof
    d.polygon([(c, 1.0 * ss), (c + 7.6 * ss, 7.2 * ss), (c + 5.8 * ss, 7.2 * ss), (c, 2.8 * ss), (c - 5.8 * ss, 7.2 * ss),
               (c - 7.6 * ss, 7.2 * ss)], fill=(255, 214, 160, 255))
    # the heart under it
    pts = []
    for i in range(80):
        t = i / 80 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)
        pts.append((c + x * ss * 0.3, c + 2.2 * ss - y * ss * 0.3))
    d.polygon(pts, fill=(242, 140, 72, 255))
    inner = [(c + (x - c) * 0.62 - 0.6 * ss, c + 2.2 * ss + (y - c - 2.2 * ss) * 0.62 - 0.6 * ss) for x, y in pts]
    d.polygon(inner, fill=(255, 190, 120, 255))
    im = im.resize((n, n), Image.LANCZOS)
    a = im.split()[3].point(lambda v: 255 if v > 50 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (n, n), (58, 24, 10, 255)), (0, 0), grown)
    out.alpha_composite(im)
    return out


# ------------------------------------------------------------------------------------------------- preview

def preview(images, out, views=("fl", "front", "back")):
    import armor_rig as rig  # noqa: E402
    import preview_model as pm  # noqa: E402
    boxes = [
        ("head", (-4, -8, -4), (8, 8, 8), (0, 0), 0.0), ("head", (-4, -8, -4), (8, 8, 8), (32, 0), 0.5),
        ("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.0), ("body", (-4, 0, -2), (8, 12, 4), (16, 32), 0.25),
        ("right_arm", (-2, -2, -2), (3, 12, 4), (40, 16), 0.0), ("right_arm", (-2, -2, -2), (3, 12, 4), (40, 32), 0.25),
        ("left_arm", (-1, -2, -2), (3, 12, 4), (32, 48), 0.0), ("left_arm", (-1, -2, -2), (3, 12, 4), (48, 48), 0.25),
        ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.0), ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 32), 0.25),
        ("left_leg", (-2, 0, -2), (4, 12, 4), (16, 48), 0.0), ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 48), 0.25),
    ]
    pose = {"right_arm": (-8, 0, 3), "left_arm": (6, 0, -3), "right_leg": (4, 0, 0), "left_leg": (-4, 0, 0)}
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
        tiles.append(pm.render(scene, atlas, glow, (220, 220), target=(0, 28, 0), dist=20, fov=38, **pm.VIEWS["face"]))
        columns.append(np.concatenate(tiles, axis=0))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(np.concatenate(columns, axis=1)).save(out)
    return out


def items_preview(icons, out):
    tiles = [ic.resize((128, 128), Image.NEAREST) for ic in icons]
    sheet = Image.new("RGBA", (len(tiles) * 144 + 16, 160), (34, 36, 44, 255))
    for i, t in enumerate(tiles):
        sheet.alpha_composite(t, (16 + i * 144, 16))
    sheet.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    os.makedirs(SKIN_OUT, exist_ok=True)
    skins = []
    for look in range(len(LOOKS)):
        for asleep in (False, True):
            name = f"mother_{look}" + ("_asleep" if asleep else "")
            img = mother(look, asleep).image()
            img.save(os.path.join(SKIN_OUT, name + ".png"))
            skins.append(img)
    icons = {"elder_root_dew": dew_icon(), "crimson_kings_chalice": chalice_icon(), "elixir_of_waking": elixir_icon(),
             "home_cooked_stew": stew_icon()}
    for name, img in icons.items():
        save_item(img, name)
    os.makedirs(EFFECTS, exist_ok=True)
    comfort = comfort_icon()
    comfort.save(os.path.join(EFFECTS, "home_comfort.png"))
    print("  skins: mother_0..2 (+ _asleep); items: " + ", ".join(icons) + "; effect: home_comfort")
    if args.preview:
        print("preview", preview(skins[0::2] + skins[1::2][:1], os.path.join(PREVIEW, "story_mothers.png")))
        print("preview", items_preview(list(icons.values()) + [comfort.resize((32, 32), Image.NEAREST)],
                                       os.path.join(PREVIEW, "story_items.png")))


if __name__ == "__main__":
    main()
