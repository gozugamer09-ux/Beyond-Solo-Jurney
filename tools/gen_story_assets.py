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
- father_0..2 (+ _glow): the hunter's father, the main story's (com.beyondsolo.story.MainStory), a man of about fifty
  in the wide player shape: the long hunter's coat he walked into a gate in ten years ago, open over a dark shirt with
  his hunter's tag on a chain, its skirt over the legs (the outer leg layer) and torn at the hem, trousers, boots,
  fingerless gloves. The light he carries now shows through the coat's seams (waist, cuffs, hem) and in
  his eyes: the _glow textures hold only those, drawn at full brightness by the game. Each matches the mother of the
  same number:
    0  fair, dark brown hair going grey at the temples, a short beard; a charcoal coat.
    1  tan, black hair, dark stubble, a scar down the left cheek; an olive coat.
    2  dark, a close silver crop and a grey beard; a navy coat.
- king_of_night (+ _glow): Morvath, the King of Night, the vision the story shows: a pale gaunt face framed by long
  black hair under a crown of iron spikes, black plate with a crack across the chest, heavy pauldrons, a ragged cloak
  of shadow down the back to the heels (its tatters see-through). His eyes, the crack, the runes on his gauntlets and
  pauldrons and the crown's jewel burn violet (the _glow texture).

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
import gen_association_assets as assoc  # noqa: E402
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


# ------------------------------------------------------------------------------------------------- lit skins

class LitSkin(Skin):
    """A skin with a glow texture beside it: glow letters (letter -> (colour, strength)) paint their pixels into the
    glow texture too (everything else there stays clear black, which adds nothing); alpha letters (letter -> 0..255)
    are see-through by that much in the skin itself."""

    def __init__(self, seed, pal, glow, alpha=None):
        super().__init__(seed, pal)
        self.glow = {k: hexc(v[0]) * v[1] for k, v in glow.items()}
        self.alpha = alpha or {}
        self.g = np.zeros((64, 64, 4), float)

    def paint(self, x, y, rows_, light=0.0):
        super().paint(x, y, rows_, light)
        for j, row in enumerate(rows_):
            for i, ch in enumerate(row):
                if ch == ".":
                    continue
                if ch in self.alpha:
                    self.a[y + j, x + i, 3] = self.alpha[ch]
                if ch in self.glow:
                    self.g[y + j, x + i, :3] = self.glow[ch]
                    self.g[y + j, x + i, 3] = 255
                else:
                    self.g[y + j, x + i] = 0

    def glow_image(self):
        return Image.fromarray(np.clip(self.g, 0, 255).astype(np.uint8), "RGBA")


# the wide arms (4 wide) of the player model
W_R_ARM, W_R_SLEEVE, W_L_ARM, W_L_SLEEVE = assoc.R_ARM, assoc.R_SLEEVE, assoc.L_ARM, assoc.L_SLEEVE
LIGHT = "ffe7a0"


# ------------------------------------------------------------------------------------------------- their father

FATHER_LOOKS = [
    # tone, hair (base, shade, light), grey strands, beard (base, grey), iris, coat (base, fold, edge), shirt (base,
    # fold), trousers (base, fold), boots (base, light), gloves
    dict(tone="light", hair=("4a3426", "33231a", "6a4c38"), grey="9c968e", beard=("4e3a2c", "8a8078"), iris="d8b058",
         coat=("3c3c42", "2a2a30", "54545e"), shirt=("2e3a4c", "222c3a"), trousers=("2e2c2c", "201e1e"),
         boots=("3a2a20", "5a4232"), glove="4a3628"),
    dict(tone="tan", hair=("1e1a1c", "121012", "38323a"), grey="8e8c94", beard=("3a3234", "6e6a70"), iris="e0b860",
         coat=("454c38", "33392a", "5c664a"), shirt=("4a2e2a", "36201e"), trousers=("2a2826", "1e1c1a"),
         boots=("2e221c", "4a382c"), glove="3a2a20"),
    dict(tone="deep", hair=("b8b2aa", "8e8880", "d8d2ca"), grey="e8e4de", beard=("a8a29a", "d0cac2"), iris="e8c060",
         coat=("2a3249", "1d2337", "3b466c"), shirt=("5e4e3e", "463a2e"), trousers=("26242a", "1a181e"),
         boots=("221a16", "3a2c22"), glove="2e2420"),
]


def father_palette(look):
    L = FATHER_LOOKS[look]
    tone = skin_pal(L["tone"])
    pal = {}
    pal.update(tone)
    pal.update(hair_pal(*L["hair"]))
    pal.update(eye_pal(L["iris"]))
    pal.update({
        "g": (L["grey"], 0.04),
        "W": (L["hair"][1], 0.0),            # brows
        "R": (L["beard"][0], 0.06), "r": (L["beard"][1], 0.05),
        "M": (mixc(tone["m"][0], "1a1012", 0.55), 0.0),   # the mouth, in the beard
        "X": (mixc(tone["S"][0], "f2d8c8", 0.45), 0.0),   # the old scar
        "Z": (L["iris"], 0.0),               # the light in his eyes
        "C": (L["coat"][0], 0.05), "c": (L["coat"][1], 0.05), "K": (L["coat"][2], 0.03),
        "F": ("c8b070", 0.03),               # the seams, where the light shows through
        "T": (L["shirt"][0], 0.04), "t": (L["shirt"][1], 0.04),
        "B": ("2a1e18", 0.03), "b": ("a8a8b2", 0.02),
        "N": (L["trousers"][0], 0.04), "n": (L["trousers"][1], 0.04),
        "O": (L["boots"][0], 0.04), "o": (L["boots"][1], 0.03),
        "G": (L["glove"], 0.04),
    })
    return pal


def father_head(sk, look):
    if look == 0:
        front = ["HHHHHHHH", "gHHHHHHg", "HSSSSSSH", "SWWSSWWS", "SEZSSZES", "SSSssSSS", "sRRMMRRs", "RRRrrRRR"]
        side = ["HHHHHHHH", "HHHHHHHg", "HHHHHHgS", "HHHHHHSS", "HHHHeSSS", "hHHeeSSS", "hhHRRRSS", "hhRRRRRR"]
        back = ["HHHHHHHH", "HHHgHHHH", "HHHHHHHH", "HHHHgHHH", "HHHHHHHH", "hHHHHHHh", "hhhhhhhh", "sSSSSSSs"]
        top = ["HHHHHHHH", "HHgHHHHH", "HHHHHkHH", "HkHHHHHH", "HHHHgHHH", "HHHHHHHH", "HHkHHHgH", "HHHHHHHH"]
        # a little volume to the hair
        hat_front = ["H.HkHH.H"] + ["........"] * 7
        hat_side = ["HHHHHHHg", "HHHHHH..", "hHH.....", "........", "........", "........", "........", "........"]
        hat_back = ["HHHHHHHH", "HHHgHHHH", "hHHHHHHh", "........", "........", "........", "........", "........"]
        hat_top = ["HHHHHHHH", "HHgHHHHH", "HHHHHkHH", "HkHHHHHH", "HHHHgHHH", "HHHHHHHH", "HHkHHHgH", "HHHHHHHH"]
    elif look == 1:
        # dark stubble, and an old scar from the brow down the left cheek
        front = ["HHHHHHHH", "HHHHHHHH", "gSSSSSSg", "SWWSSXWS", "SEZSSZES", "SSSssSXS", "sRRMMRXs", "dRRrrRRd"]
        side = ["HHHHHHHH", "HHHHHHHH", "gHHHHHHS", "gHHHHHSS", "HHHHeSSS", "hHHeeSSS", "hhHSRSSS", "hhSRRRRR"]
        back = ["HHHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhhhhhhh", "sSSSSSSs"]
        top = ["HHHHHHHH", "HHHHHHkH", "HkHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HkHHHHHH", "HHHHHHHH"]
        hat_front = ["HHHkHH.H"] + ["........"] * 7
        hat_side = ["HHHHHHHH", "gHHHHH..", "g.......", "........", "........", "........", "........", "........"]
        hat_back = ["HHHHHHHH", "HHHkHHHH", "hHHHHHHh", "........", "........", "........", "........", "........"]
        hat_top = ["HHHHHHHH", "HHHHHHkH", "HkHHHHHH", "HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HkHHHHHH", "HHHHHHHH"]
    else:
        # a close silver crop and a grey beard
        front = ["HHHHHHHH", "HSSSSSSH", "SSSSSSSS", "SWWSSWWS", "SEZSSZES", "SSSssSSS", "sRRMMRRs", "RRRrrRRR"]
        side = ["HHHHHHHH", "HHHHHHHS", "HHHHHHSS", "HHHHHSSS", "hHHHeSSS", "hHHeeSSS", "hhRRRRSS", "hRRRRRRR"]
        back = ["HHHHHHHH", "HHHkHHHH", "HHHHHHHH", "HHHHHHHH", "hHHHHHHh", "hhHHHHhh", "shhhhhhs", "sSSSSSSs"]
        top = ["HHHHHHHH", "HHkHHHHH", "HHHHHHkH", "HHHHHHHH", "HkHHHHHH", "HHHHkHHH", "HHHHHHHH", "HHHHHHHH"]
        hat_front = hat_side = hat_back = hat_top = None
    sk.box(HEAD, side, front, mirror(side), back, top, fill("s", 8, 8))
    if hat_front is not None:
        sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)


def father_body(sk):
    # the shirt, his hunter's tag on its chain, the belt; trousers below
    front = ["TTTSSTTT", "TTTtbTTT", "TTTbbTTT", "TTTTTTTT", "TTtTTtTT", "TTTTTTTT", "TTtTTtTT", "TTTTTTTT",
             "BBBbbBBB", "NNNNNNNN", "NnNNNNnN", "NNNNNNNN"]
    side = rows(("T", 4, 8), ("B", 4, 1), ("N", 4, 3))
    back = rows(["TTTTTTTT", "TTtTTtTT"] * 4, ("B", 8, 1), ["NNNNNNNN", "NnNNNNnN", "NNNNNNNN"])
    sk.box(BODY, side, front, side, back, fill("T", 8, 4), fill("N", 8, 4), light=0.05)
    # the coat over it, open down the front; the seam round the waist lit
    j_front = ["CK....KC", "CCK..KCC", "CCK..KCC", "CcK..KcC", "CcK..KcC", "CcK..KcC", "CcK..KcC",
               "FFK..KFF", "CcK..KcC", "CcK..KcC", "CcK..KcC", "CcK..KcC"]
    j_side = rows(["CCCC"], ["CCCc"] * 6, ["FFFF"], ["CCCc"] * 4)
    j_back = rows(["CCCCCCCC"], ["CCcCCcCC"] * 6, ["FFFFFFFF"], ["CCCccCCC"] * 4)
    j_top = ["CCKKKKCC", "CK....KC", "CK....KC", "CCKKKKCC"]
    sk.box(JACKET, j_side, j_front, mirror(j_side), j_back, j_top, None, light=0.06)


def father_arms(sk):
    for box, sleeve_box, right in ((W_R_ARM, W_R_SLEEVE, True), (W_L_ARM, W_L_SLEEVE, False)):
        face = rows(("C", 4, 9), ("K", 4, 1), ("G", 4, 1), ("S", 4, 1))
        sk.box(box, face, face, face, face, fill("C", 4, 4), fill("s", 4, 4), light=0.05)
        # the coat's sleeve over it, the cuff lit at its edge
        outer = rows(["CCCC"], ["CcCC"] * 7, ["KKKK", "FFFF"], (".", 4, 2))
        plain = rows(["CCCC"], ["CCcC"] * 7, ["KKKK", "FFFF"], (".", 4, 2))
        r, l = (outer, plain) if right else (plain, mirror(outer))
        sk.box(sleeve_box, r, plain, l, plain, fill("C", 4, 4), None, light=0.06)


def father_legs(sk):
    for box in (R_LEG, L_LEG):
        face = rows(["NNNN", "NnNN"] * 4, ["NNnN"], ["OoOO"], ("O", 4, 2))
        sk.box(box, face, face, face, face, fill("N", 4, 4), fill("O", 4, 4), light=0.04)
    # the coat's skirt over the upper legs, open at the front, a lit hem, torn below it
    r_front = rows(["CcK."] * 6, ["FFK."], ["c.C."], (".", 4, 4))
    l_front = rows([".KcC"] * 6, [".KFF"], [".C.c"], (".", 4, 4))
    r_out = rows(["CCCc"] * 6, ["FFFF"], [".C.c"], (".", 4, 4))
    l_out = mirror(r_out)
    r_in = rows(["cCCc"] * 6, ["FFFF"], ["c.c."], (".", 4, 4))
    l_in = mirror(r_in)
    r_back = rows(["cCCC"] * 6, ["FFFF"], [".C.c"], (".", 4, 4))
    l_back = rows(["CCCc"] * 6, ["FFFF"], ["c.C."], (".", 4, 4))
    sk.box(R_PANTS, r_out, r_front, r_in, r_back, None, None, light=0.05)
    sk.box(L_PANTS, l_in, l_front, l_out, l_back, None, None, light=0.05)


def father(look):
    sk = LitSkin(400 + look, father_palette(look), {"Z": (LIGHT, 1.0), "F": (LIGHT, 0.55)})
    father_head(sk, look)
    father_body(sk)
    father_arms(sk)
    father_legs(sk)
    return sk


# ------------------------------------------------------------------------------------------------- the King of Night

KING_PAL = {
    "A": ("16131e", 0.03), "h": ("2a2240", 0.03), "a": ("44346a", 0.03),   # long black hair, strands with a violet sheen
    "S": ("a8a4b6", 0.02), "s": ("807c92", 0.02), "d": ("4e4a62", 0.02), "m": ("2a2434", 0.0),   # ashen, gaunt
    "v": ("0e0b16", 0.0), "V": ("dcc4ff", 0.0), "t": ("7444c0", 0.0),   # dark sockets, burning eyes, light running down
    "K": ("27242f", 0.025), "k": ("4a465c", 0.02), "L": ("75718c", 0.02), "n": ("0b0a10", 0.02),  # black plate: base,
    # bevelled edges, a glint of light on them, the deep joints
    "R": ("8a50dc", 0.0), "r": ("3a2460", 0.02),                         # the crack and the runes, their scorched rims
    "I": ("2e2c38", 0.04), "i": ("5c5a70", 0.03), "J": ("9894b0", 0.02),  # the iron crown, its edges, the spikes' points
    "U": ("a46cf0", 0.0), "u": ("5c309e", 0.0),                          # its jewel, the jewel's dark facet
    "C": ("120e1a", 0.03), "c": ("221b32", 0.03), "G": ("33275a", 0.03),  # the cloak of shadow, its folds, the lining
    "x": ("0d0b13", 0.0), "w": ("120e1a", 0.0),                          # its tatters, thinning into nothing
    "P": ("2e2b3c", 0.025), "p": ("57536e", 0.02), "Q": ("8a86a4", 0.02), "T": ("9a62ec", 0.0),   # pauldrons, their rims,
    # the light on them, a violet trim
    "B": ("1a1622", 0.04), "b": ("a6a2ba", 0.02),                        # the belt, its buckle
}
KING_GLOW = {"V": ("e2ccff", 1.0), "t": ("8a52e0", 0.55), "R": ("a46cff", 0.9), "U": ("b884ff", 1.0),
             "u": ("7a44d0", 0.5), "T": ("9a62ec", 0.75)}
KING_ALPHA = {"C": 238, "c": 238, "x": 140, "w": 70}


def king():
    sk = LitSkin(500, KING_PAL, KING_GLOW, KING_ALPHA)
    # the head: an ashen, gaunt face framed by long black hair, light running from the burning eyes
    front = ["AAhAAhAA", "AhSSSShA", "ASdSSdSA", "AvVSSVvA", "AdtSStdA", "AsSssSsA", "AdsmmsdA", "AAdssdAA"]
    side = ["AAAAAAAA", "AhAAaAAA", "AhAAaAAS", "AhAAhAsS", "AAhAhAvS", "AAhAAhsS", "AAhAAhds", "AAhAAhAd"]
    back = ["AAAAAAAA", "AAhAAhaA", "AAhAAhaA", "AAhAAhAA", "AhAAAhAA", "AhAAhAAA", "AhAAhAaA", "AAAAhAaA"]
    top = ["AAAAAAAA", "AAhhhhAA", "AhAAAAhA", "AhAaaAhA", "AhAaaAhA", "AhAAAAhA", "AAhhhhAA", "AAAAAAAA"]
    sk.box(HEAD, side, front, mirror(side), back, top, fill("d", 8, 8))
    # the crown of iron spikes round the brow (the points catch the light), a violet stone at the front
    ring = ["IiIiIiIi", "I......I", "i......i", "I......I", "I......I", "i......i", "I......I", "IiIiIiIi"]
    band = rows(["J.iJ.iJ.", "IiIIiIIi"], (".", 8, 6))
    sk.box(HAT, band, rows(["J.iJJi.J", "IiIUUIiI", "...uu..."], (".", 8, 5)), mirror(band), band, ring, None)
    # black plate: a gorget, a crack burning violet from the right shoulder across the chest, a belt, the faulds
    b_front = ["nkkLLkkn", "kKKkLKRk", "KKKkLRrK", "KKKrRKKK", "kkkRrkkk", "KKRrkKKK", "nnnnnnnn", "BBBbbBBB",
               "kkkkkkkk", "KKKnnKKK", "kkknnkkk", "nKKnnKKn"]
    b_side = rows(["kkkk"], ("K", 4, 3), ["kkkk"], ["KKKK"], ["nnnn"], ["BBBB"], ["kkkk"], ("K", 4, 2), ["nKKn"])
    b_back = rows(["nkkkkkkn"], ["KKKkkKKK"] * 3, ["kkkkkkkk"], ["KKKkkKKK"], ("n", 8, 1), ["BBBBBBBB"], ["kkkkkkkk"],
                  ("K", 8, 2), ["nKKKKKKn"])
    sk.box(BODY, b_side, b_front, b_side, b_back, ["kLLLLLLk", "kKKKKKKk", "kKKKKKKk", "nkkkkkkn"], fill("n", 8, 4), light=0.08)
    # the cloak of shadow: a high collar, its violet lining showing at the front edges, down the back in folds,
    # ragged at the end
    c_side = rows(["CC.."] * 11, ["x.x."])
    c_back = rows(["CCCCCCCC"], ["CcCCcCcC"] * 9, ["CxCCxCxC"], ["x.xCx.x."])
    c_front = rows(["CcC..CcC", "GC....CG", "G......G"], (".", 8, 9))
    sk.box(JACKET, c_side, c_front, mirror(c_side), c_back, ["CCCCCCCC", "C......C", "C......C", "c......c"], None, light=0.04)
    # arms in plate (an elbow cop, the gauntlets' cuffs), runes down the backs of the gauntlets; heavy pauldrons with a
    # violet trim over the shoulders
    for box, sleeve_box, outer in ((W_R_ARM, W_R_SLEEVE, "right"), (W_L_ARM, W_L_SLEEVE, "left")):
        plain = ["kkkk", "KKKK", "KKKK", "nnnn", "kLLk", "KKKK", "nnnn", "kkkk", "KKKK", "nnnn", "kkkk", "nKKn"]
        rune = ["kkkk", "KKKK", "KKKK", "nnnn", "kLLk", "KKKK", "nnnn", "kRkk", "KrRK", "nnRn", "kkrk", "nKKn"]
        right, left = (rune, plain) if outer == "right" else (plain, rune)
        sk.box(box, right, rune, left, plain, fill("K", 4, 4), fill("n", 4, 4), light=0.08)
        pad = rows(["QQQQ", "PPPP", "pppp", "TTTT"], (".", 4, 8))
        sk.box(sleeve_box, pad, pad, mirror(pad), pad, ["pQQp", "QPPQ", "QPPQ", "pQQp"], None, light=0.05)
    # greaves with knee cops and sabatons; the cloak goes on down the backs of the legs to the heels, thinning into shadow
    for box in (R_LEG, L_LEG):
        face = ["kkkk", "KKKK", "KKKK", "nnnn", "kLLk", "KKKK", "nnnn", "kkkk", "KKKK", "KKKK", "nnnn", "kkkk"]
        sk.box(box, face, face, mirror(face), face, fill("K", 4, 4), fill("n", 4, 4), light=0.06)
    r_back = rows(["CcCC"] * 7, ["CxCx", "xCx."], ["x.w.", ".w.."], (".", 4, 1))
    l_back = mirror(r_back)
    r_out = rows(["Cc.."] * 7, ["x...", "x..."], ["w..."], (".", 4, 2))
    r_in = mirror(r_out)
    sk.box(R_PANTS, r_out, None, r_in, r_back, None, None)
    sk.box(L_PANTS, mirror(r_in), None, mirror(r_out), l_back, None, None)
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

def preview(images, out, views=("fl", "front", "back"), wide=False, glows=None):
    import armor_rig as rig  # noqa: E402
    import preview_model as pm  # noqa: E402
    aw, ax = (4, -3) if wide else (3, -2)
    boxes = [
        ("head", (-4, -8, -4), (8, 8, 8), (0, 0), 0.0), ("head", (-4, -8, -4), (8, 8, 8), (32, 0), 0.5),
        ("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.0), ("body", (-4, 0, -2), (8, 12, 4), (16, 32), 0.25),
        ("right_arm", (ax, -2, -2), (aw, 12, 4), (40, 16), 0.0), ("right_arm", (ax, -2, -2), (aw, 12, 4), (40, 32), 0.25),
        ("left_arm", (-1, -2, -2), (aw, 12, 4), (32, 48), 0.0), ("left_arm", (-1, -2, -2), (aw, 12, 4), (48, 48), 0.25),
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
    for n, img in enumerate(images):
        atlas = np.asarray(img.resize((128, 128), Image.NEAREST))
        glow = np.asarray(glows[n].resize((128, 128), Image.NEAREST)) if glows else np.zeros_like(atlas)
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
    fathers, father_glows = [], []
    for look in range(len(FATHER_LOOKS)):
        sk = father(look)
        img, glow = sk.image(), sk.glow_image()
        img.save(os.path.join(SKIN_OUT, f"father_{look}.png"))
        glow.save(os.path.join(SKIN_OUT, f"father_{look}_glow.png"))
        fathers.append(img)
        father_glows.append(glow)
    kg = king()
    king_img, king_glow = kg.image(), kg.glow_image()
    king_img.save(os.path.join(SKIN_OUT, "king_of_night.png"))
    king_glow.save(os.path.join(SKIN_OUT, "king_of_night_glow.png"))
    print("  skins: mother_0..2 (+ _asleep), father_0..2 (+ _glow), king_of_night (+ _glow); items: " + ", ".join(icons)
          + "; effect: home_comfort")
    if args.preview:
        print("preview", preview(fathers + [king_img], os.path.join(PREVIEW, "story_fathers.png"),
                                 views=("fl", "front", "back"), wide=True, glows=father_glows + [king_glow]))
        print("preview", preview(skins[0::2] + skins[1::2][:1], os.path.join(PREVIEW, "story_mothers.png")))
        print("preview", items_preview(list(icons.values()) + [comfort.resize((32, 32), Image.NEAREST)],
                                       os.path.join(PREVIEW, "story_items.png")))


if __name__ == "__main__":
    main()
