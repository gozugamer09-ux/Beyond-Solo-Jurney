#!/usr/bin/env python3
"""
The national-level hunters (see com.beyondsolo.duel): skins in the player's shape (64x64, the game's player layout
with both layers; slim arms, three pixels wide, for the two drawn so) for the four S-rank hunters a hunter can duel.

    han_seori       the Tidecaller, Azure Tide's mage (slim): long blue-black hair with a fringe and a band of shine,
                    azure eyes, an azure earring; a navy longcoat with a white stand collar, azure piping down the
                    front and down each sleeve, silver clasps, a sash tied at the waist, its skirt over the legs to
                    the knee, flared cuffs; dark leggings and navy knee boots strapped in azure.
    viktor_kael     the Iron Titan, Iron Bastion's warrior: a grey-blond crew cut, a short beard, a scar through the
                    left brow, steel-grey eyes; a gunmetal cuirass of lit lames with the bastion's brass mark over a
                    dark tunic, a fur mantle with a ragged edge on the shoulders, steel pauldrons, vambraces and
                    gauntlets with lit knuckles, tassets over the thighs, knee guards, greaves and heavy boots.
    amara_okafor    the Sunbow, Sunfall's ranger (slim): cornrows drawn back into a high braided ponytail bound in
                    gold, amber eyes, gold earrings; a cropped sand jacket trimmed in sun orange over a dark top, the
                    sun on the left shoulder, a quiver on the back on a strap across the chest, rolled sleeves, a
                    leather bracer on the bow arm, fingerless gloves, tan trousers with a knife strapped to the right
                    thigh, brown boots with a turned-down cuff.
    ren_takashiro   the Unseen Blade, no guild's: messy black hair over the right eye, the other crimson; a black mask
                    over the lower face with a red stripe, knotted at the back with its tails down the back; a
                    charcoal coat wrapped across the chest with crimson edges, the under panel in shadow, a strap at
                    the waist with a knife tucked in it, the coat's crimson-lined skirt over the legs; black gloves,
                    forearm and leg wraps, a knife on the right thigh, soft black boots.

Every face is drawn pixel by pixel from the maps below (one letter a colour, '.' clear), on the association's painter
(tools/gen_association_assets.py): cloth gets a weave of noise and a light from above; plate gets a lit top lip and
a shaded underside; hair gets a band of shine and strands.

Run from the repo root:   python3 tools/gen_duel_assets.py             (writes the skins)
                          python3 tools/gen_duel_assets.py --preview   (and build/preview/duelists.png)
Needs: pillow, numpy.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_association_assets import (HEAD, HAT, BODY, JACKET, R_ARM, R_SLEEVE, L_ARM, L_SLEEVE, R_LEG, R_PANTS, L_LEG,  # noqa: E402
                                    L_PANTS, Skin, fill, mirror, rows, skin_pal, hair_pal, eye_pal)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo", "textures", "entity", "duel")
PREVIEW = os.path.join(ROOT, "build", "preview", "duelists.png")


def put(maps, pixels):
    """maps with single pixels replaced: pixels = {(row, x): letter}."""
    out = [list(r) for r in maps]
    for (r, x), ch in pixels.items():
        out[r][x] = ch
    return ["".join(r) for r in out]


def over(maps, at, part):
    """maps with the rows from `at` on replaced by part's rows."""
    out = list(maps)
    for i, r in enumerate(part):
        out[at + i] = r
    return out


def clear(w, h):
    return fill(".", w, h)


def limb(sk, box, right, faces, top, bottom, light):
    """One arm or leg. faces are drawn for the right limb as the game lays them out: out (the face away from the body)
    with x=0 at the back, front with x=0 at the outer edge, inner with x=0 at the front, back with x=0 at the inner
    edge; the left limb gets them mirrored, so both sides match."""
    if right:
        sk.box(box, faces["out"], faces["front"], faces["inner"], faces["back"], top, bottom, light=light)
    else:
        sk.box(box, mirror(faces["inner"]), mirror(faces["front"]), mirror(faces["out"]), mirror(faces["back"]),
               mirror(top) if top else None, mirror(bottom) if bottom else None, light=light)


def arms(sk, slim, out, front, inner, back, top=None, bottom=None, sleeve=False, light=0.05, left=None):
    """Both arms (or both sleeves); left: faces to use on the left arm instead (drawn as for the right)."""
    for right in (True, False):
        faces = {"out": out, "front": front, "inner": inner, "back": back}
        if not right and left:
            faces.update(left)
        box = (R_SLEEVE if sleeve else R_ARM) if right else (L_SLEEVE if sleeve else L_ARM)
        if slim:
            box = (box[0], box[1], 3, box[3], box[4])
        limb(sk, box, right, faces, top, bottom, light)


def legs(sk, out, front, inner, back, top=None, bottom=None, pants=False, light=0.04, right_leg=None):
    """Both legs (or both trouser layers); right_leg: faces to use on the right leg instead."""
    for right in (True, False):
        faces = {"out": out, "front": front, "inner": inner, "back": back}
        if right and right_leg:
            faces.update(right_leg)
        box = (R_PANTS if pants else R_LEG) if right else (L_PANTS if pants else L_LEG)
        limb(sk, box, right, faces, top, bottom, light)


def head(sk, front, side, back, top, hat_front, hat_side, hat_back, hat_top, chin="d"):
    sk.box(HEAD, side, front, mirror(side), back, top, fill(chin, 8, 8))
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)


# ================================================================================================= Han Seo-ri

def han_seori():
    pal = {
        # the navy longcoat, its piping, collar and clasps
        "C": ("1f2a4c", 0.04), "c": ("162039", 0.04), "L": ("2f3f6c", 0.03), "D": ("0e1426", 0.02),
        "A": ("4fa8ff", 0.0), "a": ("2c6fbd", 0.0),
        "W": ("eaf0f8", 0.0), "w": ("b6c0d2", 0.0),
        "G": ("e4eaf2", 0.0), "g": ("8f99a8", 0.0),
        "b": ("121a33", 0.02),
        # leggings, boots, the earring
        "N": ("1c1f2d", 0.03), "n": ("13151f", 0.03),
        "O": ("1e2846", 0.03), "o": ("37487a", 0.02), "Q": ("0a0c15", 0.0),
        "Y": ("9ad6ff", 0.0),
    }
    pal.update(skin_pal("light"))
    pal.update(hair_pal("1c2236", "10131f", "3a4a78"))
    pal.update(eye_pal("3f90e6"))
    pal["B"] = ("10131f", 0.0)
    pal["m"] = ("cf8784", 0.0)
    sk = Skin(400, pal)

    # long hair with a fringe, framing the face; a band of shine across it
    head(sk,
         front=["HHHHHHHH", "HHhHHhHH", "HhSSSShH", "HBBSSBBH", "HEISSIEH", "hSSssSSh", "hSSmmSSh", "hdSSSSdh"],
         side=["HHHHHHHH", "HHHHHHHH", "kkHkkHkk", "HHhHHhHh", "HHhHHhhS", "HHhHHhhS", "HHhHHhhS", "hHhHHhhs"],
         back=["HHHHHHHH", "HHHHHHHH", "kkHkkHkk", "HhHHhHHh", "HhHHhHHh", "HhHHhHHh", "HhHHhHHh", "hhHhhHhh"],
         top=["HHHhHHHH", "HHHhHHHH", "HkHhHkHH", "HkHhHkHH", "HHHhHHHH", "HHHhHHHH", "HkHhHHkH", "HHHhHHHH"],
         hat_front=["HHHHHHHH", "HHhHHhHH", "H......H", "H......H", "h......h", "h......h", "h......h", "h......h"],
         hat_side=["HHHHHHHH", "HHHHHHHH", "kkHkkHkk", "HHhHHhH.", "HHhHHhH.", "HHhHHhY.", "HHhHHhH.", "hHhHHhh."],
         hat_back=["HHHHHHHH", "HHHHHHHH", "kkHkkHkk", "HhHHhHHh", "HhHHhHHh", "HhHHhHHh", "HhHHhHHh", "hhHhhHhh"],
         hat_top=["HHHhHHHH", "HHHhHHHH", "HkHhHkHH", "HkHhHkHH", "HHHhHHHH", "HHHhHHHH", "HkHhHHkH", "HHHhHHHH"])

    # the coat: a white stand collar, azure piping either side of the placket, silver clasps, a sash, folds below it
    front = [
        "cLWwwWLc",
        "cCLWWLCc",
        "cCAGGACc",
        "cCACCACc",
        "cLACCALc",
        "cCAGgACc",
        "cCACCACc",
        "bbbAabbb",
        "cCAcCACc",
        "cLACCALc",
        "cCAcCACc",
        "DcaccacD",
    ]
    side = rows(["CCCC"] * 7, ["bbbb"], ["CCCC"] * 3, ["cccc"])
    back = rows(["CCCcCCCC"] * 7, ["bbbAabbb"], ["CCCcCCCC", "CLCcCLCC", "CCCcCCCC", "cccDcccc"])
    sk.box(BODY, side, front, mirror(side), back, ["CCCCCCCC", "CCWWWWCC", "CCWDDWCC", "CLWWWWLC"], fill("c", 8, 4), light=0.07)
    # the outer layer: the collar standing round the throat, the sash, the hair down the back and the sash's tails
    sk.box(JACKET,
           rows(["cCCc"], clear(4, 6), ["bbbb"], clear(4, 4)),
           rows(["cLW..WLc"], clear(8, 6), ["bbbAabbb"], clear(8, 4)),
           rows(["cCCc"], clear(4, 6), ["bbbb"], clear(4, 4)),
           [".HHHHHH.",
            ".HhHHhH.",
            ".kkHkkH.",
            "..hHHh..",
            "..HhhH..",
            "...hh...",
            "........",
            "bbbAabbb",
            "...aA...",
            "...a.A..",
            "..a...A.",
            "........"])

    # sleeves with azure piping down the outside, a white undercuff, the hand; the cuff flares on the outer layer
    arms(sk, True,
         out=rows(["CCAC"] * 9, ["WWWW", "SSSS", "ssss"]),
         front=rows(["CCc"] * 9, ["WWW", "SSS", "sss"]),
         inner=rows(["cccc"] * 9, ["wwww", "ssss", "ssss"]),
         back=rows(["cCC"] * 9, ["WWW", "sss", "sss"]),
         top=fill("C", 3, 4), bottom=fill("s", 3, 4), light=0.07)
    arms(sk, True, sleeve=True,
         out=rows(clear(4, 7), ["CCAC", "aAAa"], clear(4, 3)),
         front=rows(clear(3, 7), ["CCC", "aAa"], clear(3, 3)),
         inner=rows(clear(4, 7), ["cccc", "aaaa"], clear(4, 3)),
         back=rows(clear(3, 7), ["CCC", "aaa"], clear(3, 3)))

    # dark leggings, navy knee boots strapped in azure with a lit shin
    legs(sk,
         out=rows(["NNNN"] * 5, ["aAAa"], ["OOoO"] * 5, ["QQQQ"]),
         front=rows(["NNNN"] * 4, ["NnNN"], ["aAAa"], ["OooO"] * 5, ["QQQQ"]),
         inner=rows(["nnnn"] * 5, ["aaaa"], ["OOOO"] * 5, ["QQQQ"]),
         back=rows(["NNNN"] * 5, ["aaaa"], ["OOOO"] * 5, ["QQQQ"]),
         top=fill("N", 4, 4), bottom=fill("Q", 4, 4))
    # the coat's skirt over the legs to the knee, open at the front, piped at the edge and the hem
    legs(sk, pants=True,
         out=rows(["CCCC"] * 3, ["CcCC", "CcCC", "CcCc"], ["aAAa"], clear(4, 5)),
         front=rows(["CCA."] * 3, ["CcA.", "CcA.", "CcA."], ["aAA."], clear(4, 5)),
         inner=clear(4, 12),
         back=rows(["CCCC"] * 3, ["CcCC", "CcCC", "ccCc"], ["aaaa"], clear(4, 5)))
    return sk


# ================================================================================================= Viktor Kael

def viktor_kael():
    pal = {
        # gunmetal plate (lit lips t, sheen T, shade r), steel (V v), the brass mark and rivets
        "R": ("4c535c", 0.03), "r": ("31363d", 0.03), "T": ("6e7782", 0.02), "t": ("a8b2bd", 0.0),
        "V": ("8b96a3", 0.02), "v": ("5d6773", 0.02),
        "Y": ("caa65c", 0.0), "y": ("8e7238", 0.0),
        # the tunic, leather, the fur mantle
        "U": ("2b2421", 0.04), "u": ("1d1816", 0.03),
        "G": ("4b3526", 0.04), "g": ("31231a", 0.03),
        "F": ("75685a", 0.06), "f": ("4f453b", 0.05),
        # trousers, boots
        "N": ("2f2e33", 0.04), "n": ("222126", 0.03),
        "O": ("2c2621", 0.04), "o": ("463c35", 0.03), "Q": ("121010", 0.0),
        # the scar, the beard
        "x": ("e2b39a", 0.0), "z": ("6f6b65", 0.05), "Z": ("4e4b47", 0.04),
    }
    pal.update(skin_pal("tan"))
    pal.update(hair_pal("8e8b86", "63615d", "b9b6b0"))
    pal.update(eye_pal("5c7c96"))
    pal["B"] = ("4e4b47", 0.0)
    sk = Skin(401, pal)

    # a crew cut, a scar through the left brow, a short beard
    head(sk,
         front=["HHHHHHHH", "hHHHHHHh", "SSSSSSxS", "SBBSSxBS", "SEISSIES", "zSSssSSz", "zzZmmZzz", "ZzzzzzzZ"],
         side=["HHHHHHHH", "HHHHHHHH", "HkkkkkhS", "hHHHhSSS", "hHHHeSSS", "hhHHeSSz", "sshhSSzz", "ssssSzzz"],
         back=["HHHHHHHH", "HHHHHHHH", "kkkkkkkk", "HHHHHHHH", "hHHHHHHh", "hhHHHHhh", "sshhhhss", "ssssssss"],
         top=["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HkkkkkkH", "HkkkkkkH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH"],
         hat_front=clear(8, 8), hat_side=clear(8, 8), hat_back=clear(8, 8), hat_top=clear(8, 8), chin="Z")

    # the cuirass: lames with lit lips over a dark tunic, the bastion's brass mark, a belt, tassets below it
    front = [
        "FFfUUfFF",
        "fTTtRRRf",
        "TTTtRRRr",
        "TTTYYRRr",
        "TTTtRRRr",
        "rrrrrrrr",
        "tttttttt",
        "RRRRRRrr",
        "GgGYyGgG",
        "VtUuuUtV",
        "VvUUUUvV",
        "vvUuuUvv",
    ]
    side = ["FFFF", "rRRT", "rRRR", "rGGR", "rRRR", "rRRr", "tttt", "rGGR", "GGgG", "UUVt", "uUVv", "uuvv"]
    back = ["FFFFFFFF", "fFfFFfFf", "TRRrRRRT", "RRRrRRRR", "RRRrRRRR", "rrrrrrrr", "tttttttt", "RRRrRRRR",
            "GGGgGGGG", "VvUuuUvV", "VvUUUUvV", "vvUuuUvv"]
    sk.box(BODY, side, front, mirror(side), back, ["FFFFFFFF", "FfFFFFfF", "FFfUUfFF", "FFFUUFFF"], fill("u", 8, 4), light=0.05)
    # the fur mantle standing off the shoulders, ragged at its edge down the back
    sk.box(JACKET,
           rows(["FFFF", "fFFf"], clear(4, 10)),
           rows(["FFf..fFF", "f......f"], clear(8, 10)),
           rows(["fFFF", "fFFf"], clear(4, 10)),
           rows(["FFFFFFFF", "FfFFfFFf", "fF.ff.Ff", ".f..f..f"], clear(8, 8)),
           ["FFFFFFFF", "FfFFFFfF", "FF....FF", "Ff....fF"])

    # tunic sleeves under steel pauldrons, a strap, vambraces with lit lips, gauntlets with lit knuckles
    arms(sk, False,
         out=rows(["UUUU"] * 5, ["GGYG", "tttt", "VVVV", "VvVV", "vvvv", "TRTR", "rrrr"]),
         front=rows(["UUUU"] * 5, ["GGGG", "tttt", "VVVV", "VvVV", "vvvv", "TRTR", "rrrr"]),
         inner=rows(["uuuu"] * 5, ["gggg", "vvvv", "vvvv", "vvvv", "vvvv", "rrrr", "rrrr"]),
         back=rows(["UUUU"] * 5, ["GGGG", "VVVV", "VVVV", "VvVV", "vvvv", "RRRR", "rrrr"]),
         top=fill("U", 4, 4), bottom=fill("r", 4, 4))
    pauldron = ["tttt", "VTVV", "VVVV", "vvvv"]
    arms(sk, False, sleeve=True,
         out=rows(pauldron, clear(4, 4), ["tttt", "vvvv"], clear(4, 2)),
         front=rows(pauldron, clear(4, 4), ["tttt", "vvvv"], clear(4, 2)),
         inner=rows(clear(4, 8), ["VVVV", "vvvv"], clear(4, 2)),
         back=rows(pauldron, clear(4, 4), ["tttt", "vvvv"], clear(4, 2)),
         top=["tVVt", "VTTV", "VTTV", "tVVt"])

    # trousers, knee guards, greaves strapped at the back, heavy boots
    legs(sk,
         out=rows(["NNNN"] * 4, ["Vttt", "vVVV", "RRTR", "RRRR", "rrrr", "OOoO", "OOOO", "QQQQ"]),
         front=rows(["NNNN"] * 4, ["tttt", "VvvV", "RTTR", "RRRR", "rrrr", "oooo", "OOOO", "QQQQ"]),
         inner=rows(["nnnn"] * 4, ["vvvv", "vvvv", "rrrr", "rrrr", "rrrr", "OOOO", "OOOO", "QQQQ"]),
         back=rows(["NNNN"] * 6, ["rGGr", "rrrr", "rGGr", "OOOO", "OOOO", "QQQQ"]),
         top=fill("N", 4, 4), bottom=fill("Q", 4, 4))
    # tassets over the thighs, the boots' heavy cuffs
    tasset = ["tttt", "VVVV", "vvvv"]
    legs(sk, pants=True,
         out=rows(tasset, clear(4, 6), ["oooo"], clear(4, 2)),
         front=rows(tasset, clear(4, 6), ["oooo"], clear(4, 2)),
         inner=rows(clear(4, 9), ["OOOO"], clear(4, 2)),
         back=rows(clear(4, 9), ["oooo"], clear(4, 2)))
    return sk


# ================================================================================================= Amara Okafor

def amara_okafor():
    pal = {
        # the sand jacket, its sun-orange trim, the dark top beneath, the belt
        "J": ("dcc9a2", 0.03), "j": ("b6a27c", 0.03), "L": ("efe3c6", 0.02),
        "A": ("ff9d2e", 0.0), "a": ("c46a18", 0.0),
        "U": ("2b211d", 0.03), "b": ("3a2418", 0.02),
        # leather (strap, quiver, bracer: lit q, G, shade g), gold, the fletchings
        "q": ("8d5d3a", 0.03), "G": ("6b4329", 0.03), "g": ("472b1a", 0.03),
        "Y": ("eabb4a", 0.0), "y": ("a8822e", 0.0), "W": ("f4efe4", 0.0),
        # gloves, trousers, boots
        "K": ("3d2b21", 0.03),
        "N": ("a98a63", 0.04), "n": ("8b6e4b", 0.03), "Z": ("c2a47e", 0.02),
        "O": ("4f3525", 0.04), "o": ("74513a", 0.03), "Q": ("1c120c", 0.0),
    }
    pal.update(skin_pal("deep"))
    pal.update(hair_pal("1f1713", "110c09", "3e2e24"))
    pal.update(eye_pal("e0952a"))
    pal["B"] = ("110c09", 0.0)
    sk = Skin(402, pal)

    # cornrows drawn back to a high ponytail bound in gold; gold earrings
    head(sk,
         front=["HhHhHhHh", "hHSSSSHh", "SSSSSSSS", "SBBSSBBS", "SEISSIES", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"],
         side=["HHHHHHHH", "hhhhhhhh", "HHHHHHHS", "hhhhhhSS", "HHHHeSSS", "hhhheSSS", "ssssYSSS", "ssssssss"],
         back=["HhHhHhHh", "HhHhHhHh", "HhHhHhHh", "HhHhHhHh", "hHhHHhHh", "shHhHhHs", "ssSSSSss", "ssssssss"],
         top=["HhHYYhHh"] + ["HhHhHhHh"] * 7,
         hat_front=clear(8, 8), hat_side=clear(8, 8),
         hat_back=["..hHHh..", "...YY...", "...HH...", "...hH...", "...Hh...", "...YY...", "...HH...", "...hH..."],
         hat_top=["..HHHH..", "..HYYH..", "...HH...", "........", "........", "........", "........", "........"])

    # a cropped jacket open over a dark top, trimmed in orange; the quiver's strap across the chest, a gold buckle
    front = [
        "JLAUUALJ",
        "JJAUUAJJ",
        "JJAUUAJJ",
        "jJAUUAJj",
        "jJAUUAJj",
        "JJAUUAJJ",
        "JjAUUAjJ",
        "jjaUUajj",
        "bbbbYbbb",
        "NNNNNNNN",
        "NnNNNNnN",
        "nnnnnnnn",
    ]
    strap = {(0, 6): "G", (1, 5): "G", (2, 4): "G", (3, 3): "G", (4, 3): "Y", (5, 2): "G", (6, 1): "G", (7, 0): "G"}
    front = put(front, strap)
    side = rows(["JJJJ"] * 7, ["jjjj", "bbbb", "NNNN", "NNNN", "nnnn"])
    back = rows(["JJJJJJJJ"] * 2, ["JJJjJJJJ"] * 5, ["jjjjjjjj", "bbbbbbbb", "NNNNNNNN", "NNNNNNNN", "nnnnnnnn"])
    sk.box(BODY, side, front, mirror(side), back, ["JJJJJJJJ", "JJUUUUJJ", "JJUUUUJJ", "JAUUUUAJ"], fill("N", 8, 4), light=0.06)
    # the outer layer: a popped collar; the braid down the back, and the quiver with its fletchings
    sk.box(JACKET,
           clear(4, 12),
           rows(["JA....AJ"], clear(8, 11)),
           clear(4, 12),
           ["...HHAWA",
            "...hHyYy",
            "...YyqGg",
            "...HHqGg",
            "...hHqGg",
            "....hYyy",
            ".....qGg",
            ".....qGg",
            ".....qGg",
            ".....ggg",
            "........",
            "........"])

    # rolled sleeves trimmed in orange, bare forearms, fingerless gloves; the sun on the left shoulder and a bracer
    # on the bow arm
    out = rows(["JJJJ"] * 5, ["LLLL", "AAAA", "SSSS", "sSSs", "KKKK", "KKKK", "sSSs"])
    front_a = rows(["JJj"] * 5, ["LLL", "AAA", "SSS", "SSs", "KKK", "KKK", "SSs"])
    inner = rows(["jjjj"] * 5, ["jjjj", "aaaa", "ssss", "ssss", "KKKK", "KKKK", "ssss"])
    back_a = rows(["jJJ"] * 5, ["LLL", "AAA", "sss", "sss", "KKK", "KKK", "sss"])
    bracer4, bracer3 = ["qGqG", "gggg"], ["qGq", "ggg"]
    arms(sk, True, out=out, front=front_a, inner=inner, back=back_a, top=fill("J", 3, 4), bottom=fill("s", 3, 4),
         left={"out": over(over(out, 1, ["JAAJ", "AYYA", "JAAJ"]), 7, bracer4), "front": over(front_a, 7, bracer3),
               "inner": over(inner, 7, bracer4), "back": over(back_a, 7, bracer3)})
    arms(sk, True, sleeve=True,
         out=rows(clear(4, 5), ["LLLL", "AAAA"], clear(4, 5)),
         front=rows(clear(3, 5), ["LLL", "AAA"], clear(3, 5)),
         inner=rows(clear(4, 5), ["jjjj", "aaaa"], clear(4, 5)),
         back=rows(clear(3, 5), ["LLL", "AAA"], clear(3, 5)),
         left={"out": rows(clear(4, 5), ["LLLL", "AAAA", "qqqq"], clear(4, 4)),
               "front": rows(clear(3, 5), ["LLL", "AAA", "qqq"], clear(3, 4)),
               "inner": rows(clear(4, 5), ["jjjj", "aaaa", "gggg"], clear(4, 4)),
               "back": rows(clear(3, 5), ["LLL", "AAA", "ggg"], clear(3, 4))})

    # tan trousers with a lit crease, brown boots with a turned-down cuff; a knife strapped to the right thigh
    leg_out = rows(["NNNN"] * 7, ["oooo", "OOoO", "OOOO", "OOOO", "QQQQ"])
    leg_front = rows(["NZNN"] * 3, ["NZNn"] * 4, ["oooo", "OoOO", "OoOO", "OOOO", "QQQQ"])
    legs(sk,
         out=leg_out, front=leg_front,
         inner=rows(["nnnn"] * 7, ["oooo", "OOOO", "OOOO", "OOOO", "QQQQ"]),
         back=rows(["NNNN"] * 7, ["oooo", "OOOO", "OOOO", "OOOO", "QQQQ"]),
         top=fill("N", 4, 4), bottom=fill("Q", 4, 4),
         right_leg={"out": over(leg_out, 1, ["NYNN", "GqGG", "NgNN", "NgNN", "NgNN"]),
                    "front": over(leg_front, 2, ["GGGG"])})
    legs(sk, pants=True,
         out=rows(clear(4, 7), ["oooo"], clear(4, 4)),
         front=rows(clear(4, 7), ["oooo"], clear(4, 4)),
         inner=rows(clear(4, 7), ["oooo"], clear(4, 4)),
         back=rows(clear(4, 7), ["oooo"], clear(4, 4)))
    return sk


# ================================================================================================= Ren Takashiro

def ren_takashiro():
    pal = {
        # the charcoal coat, its crimson edges and lining, the black layer beneath
        "C": ("2c2d34", 0.04), "c": ("1d1e24", 0.04), "L": ("40414b", 0.03),
        "A": ("e0465a", 0.0), "a": ("a52a3c", 0.0), "V": ("7a1828", 0.02),
        "U": ("101013", 0.02),
        # the mask and its stripe
        "M": ("18181c", 0.02), "X": ("0c0c0f", 0.0), "R": ("c62c42", 0.0),
        # gloves, straps, silver, trousers, boots
        "G": ("141417", 0.02), "g": ("2a2a30", 0.02),
        "T": ("2c2422", 0.03), "Y": ("b0b6c0", 0.0),
        "N": ("23242a", 0.03), "n": ("18191d", 0.03),
        "O": ("131316", 0.02), "o": ("2b2b31", 0.02), "Q": ("08080a", 0.0),
    }
    pal.update(skin_pal("light"))
    pal.update(hair_pal("16161b", "0b0b0f", "34343f"))
    pal.update(eye_pal("d6324a"))
    pal["B"] = ("0b0b0f", 0.0)
    sk = Skin(403, pal)

    # messy black hair over the right eye; a mask over the lower face with a red stripe, knotted at the back
    head(sk,
         front=["HkkHHHkH", "HHHHHHHH", "HHHHhSSh", "HHHhSBBS", "HHhHSIES", "MMMMMMMM", "XRRRRRRX", "MMMMMMMM"],
         side=["HHHHHHHH", "HkkHHkkH", "HHhHHHHh", "HHhHHhSS", "HHHHeSSS", "hHHHMMMM", "hhhMXRRR", "sssMMMMM"],
         back=["HHHHHHHH", "HkkHHkkH", "HhHHhHHh", "HhHHhHHh", "HHhHHhHH", "MMMMMMMM", "hHhMMhHh", "ssshhsss"],
         top=["HHhHHHhH", "HkHHkHHH", "HHHhHHkH", "HkHHHhHH", "HHkHHHHk", "HhHHkHHH", "HHHkHHhH", "HkHHHkHH"],
         hat_front=["HkkHHHkH", "HHHHHH.H", "HHHH....", "HHH.....", "Hh......", "h.......", "........", "........"],
         hat_side=["HHHHHHHH", "HkkHHkkH", "HHhHHHH.", "HHhHHh..", "hHHHH...", "hhH.....", "........", "........"],
         hat_back=["HHHHHHHH", "HkkHHkkH", "HhHHhHHh", "HhHHhHHh", "hHhHHhHh", "...MM...", "...RM...", "...MR..."],
         hat_top=["HHhHHHhH", "HkHHkHHH", "HHHhHHkH", "HkHHHhHH", "HHkHHHHk", "HhHHkHHH", "HHHkHHhH", "HkHHHkHH"],
         chin="X")

    # the coat wrapped across the chest: crimson collar bands meeting over a black layer, the top panel's edge
    # running down to the waist with the under panel in shadow; a strap at the waist with a silver buckle
    front = [
        "CCAUUACC",
        "CCCAUACC",
        "CCCCAACC",
        "ccccACCC",
        "cccACCCC",
        "ccACCCCC",
        "cACCCCCC",
        "TTTTYTTT",
        "CCCCCCCC",
        "CLCCCCLC",
        "CCCcCCCC",
        "cccccccc",
    ]
    side = rows(["CCCC"] * 7, ["TTTT"], ["CCCC"] * 3, ["cccc"])
    back = rows(["CCCcCCCC"] * 7, ["TTTTTTTT"], ["CCCcCCCC", "CCCcCCCC", "CCCVCCCC", "cccVcccc"])
    sk.box(BODY, side, front, mirror(side), back, ["CCCCCCCC", "CCUUUUCC", "CCUUUUCC", "CAUUUUAC"], fill("c", 8, 4), light=0.06)
    # the outer layer: the strap standing off the coat, a knife tucked in it, the mask's tails down the back
    sk.box(JACKET,
           rows(clear(4, 7), ["TTTT"], clear(4, 4)),
           rows(clear(8, 5), ["......Y.", "......g.", "TTTTYTgT"], clear(8, 4)),
           rows(clear(4, 7), ["TTTT"], clear(4, 4)),
           rows(["...MR...", "...RM...", "....M...", "....X..."], clear(8, 3), ["TTTTTTTT"], clear(8, 4)))

    # coat sleeves, a strap round the forearm, crimson cuffs, black gloves with lit knuckles
    arms(sk, False,
         out=rows(["CCCC"] * 8, ["AAAA", "GGGG", "GgGg", "GGGG"]),
         front=rows(["CCCc"] * 8, ["AAAA", "GGGG", "gGgG", "GGGG"]),
         inner=rows(["cccc"] * 8, ["aaaa", "GGGG", "GGGG", "GGGG"]),
         back=rows(["cCCC"] * 8, ["aaaa", "GGGG", "GGGG", "GGGG"]),
         top=fill("C", 4, 4), bottom=fill("G", 4, 4))
    arms(sk, False, sleeve=True,
         out=rows(clear(4, 6), ["TTTT"], clear(4, 1), ["AAAA"], clear(4, 3)),
         front=rows(clear(4, 6), ["TTTT"], clear(4, 1), ["AAAA"], clear(4, 3)),
         inner=rows(clear(4, 6), ["TTTT"], clear(4, 1), ["aaaa"], clear(4, 3)),
         back=rows(clear(4, 6), ["TTTT"], clear(4, 1), ["aaaa"], clear(4, 3)))

    # trousers bound with wraps, soft black boots; a knife on the right thigh
    leg_out = rows(["NNNN"] * 5, ["TTTT", "NNNN", "TTTT", "NNNN", "oooo", "OOOO", "QQQQ"])
    legs(sk,
         out=leg_out,
         front=rows(["NNNN", "NnNN", "NNNN", "NnNN", "NNNN"], ["TTTT", "NNNN", "TTTT", "NNNN", "oooo", "OOOO", "QQQQ"]),
         inner=rows(["nnnn"] * 5, ["TTTT", "nnnn", "TTTT", "nnnn", "OOOO", "OOOO", "QQQQ"]),
         back=rows(["NNNN"] * 5, ["TTTT", "NNNN", "TTTT", "NNNN", "OOOO", "OOOO", "QQQQ"]),
         top=fill("N", 4, 4), bottom=fill("Q", 4, 4),
         right_leg={"out": over(leg_out, 1, ["NYNN", "TgTT", "NUNN", "NUNN"])})
    # the coat's skirt over the legs, open at the front and the back on its crimson lining, a crimson hem
    legs(sk, pants=True,
         out=rows(["CCCC"] * 8, ["AAAA"], clear(4, 3)),
         front=rows(["CCV."] * 8, ["AAA."], clear(4, 3)),
         inner=clear(4, 12),
         back=rows(["CCCC"] * 4, ["VCCC"] * 4, ["AAAA"], clear(4, 3)))
    return sk


SKINS = {"han_seori": (han_seori, True), "viktor_kael": (viktor_kael, False), "amara_okafor": (amara_okafor, True),
         "ren_takashiro": (ren_takashiro, False)}


# ================================================================================================= preview

def preview(images, slims, out, views=("fl", "front", "back", "fr")):
    """Each skin on the player's shape (slim arms where drawn so), seen from the given sides, one column a skin."""
    import armor_rig as rig  # noqa: E402
    import preview_model as pm  # noqa: E402
    pose = {"right_arm": (-14, 0, 4), "left_arm": (12, 0, -4), "right_leg": (10, 0, 0), "left_leg": (-10, 0, 0)}
    columns = []
    for img, slim in zip(images, slims):
        aw = 3 if slim else 4
        boxes = [
            ("head", (-4, -8, -4), (8, 8, 8), (0, 0), 0.0), ("head", (-4, -8, -4), (8, 8, 8), (32, 0), 0.5),
            ("body", (-4, 0, -2), (8, 12, 4), (16, 16), 0.0), ("body", (-4, 0, -2), (8, 12, 4), (16, 32), 0.25),
            ("right_arm", (1 - aw, -2, -2), (aw, 12, 4), (40, 16), 0.0), ("right_arm", (1 - aw, -2, -2), (aw, 12, 4), (40, 32), 0.25),
            ("left_arm", (-1, -2, -2), (aw, 12, 4), (32, 48), 0.0), ("left_arm", (-1, -2, -2), (aw, 12, 4), (48, 48), 0.25),
            ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), 0.0), ("right_leg", (-2, 0, -2), (4, 12, 4), (0, 32), 0.25),
            ("left_leg", (-2, 0, -2), (4, 12, 4), (16, 48), 0.0), ("left_leg", (-2, 0, -2), (4, 12, 4), (0, 48), 0.25),
        ]
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
        atlas = np.asarray(img.resize((128, 128), Image.NEAREST))
        glow = np.zeros_like(atlas)
        tiles = [pm.render(scene, atlas, glow, (240, 360), target=(0, 16.5, 0), dist=58, fov=38, **pm.VIEWS[v]) for v in views]
        columns.append(np.concatenate(tiles, axis=1))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(np.concatenate(columns, axis=0)).save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    os.makedirs(OUT, exist_ok=True)
    images, slims = [], []
    for name, (make, slim) in SKINS.items():
        img = make().image()
        img.save(os.path.join(OUT, name + ".png"))
        images.append(img)
        slims.append(slim)
        print("wrote", name)
    if args.preview:
        print("preview", preview(images, slims, PREVIEW))


if __name__ == "__main__":
    main()
