#!/usr/bin/env python3
"""
The great guilds' scouts (see com.beyondsolo.scouting): skins in the player's shape (64x64, the game's player layout
with both layers) for the people a guild sends to a hunter who has just ranked up, offer in hand. They dress to
impress, each in their own sharp suit; their guild's band goes on the left arm on top of the skin, tinted with the
guild's colour (client/association/ScoutRenderer, the rival parties' band from tools/gen_rival_assets.py), so the
skins carry no guild colour of their own.

    scout_0  a sleek black bob with a straight fringe, a charcoal pinstripe suit over an open white collar and a thin
             silver chain, dark red lips, black heeled shoes;
    scout_1  dark brown hair combed back, thin silver-framed glasses, a light grey suit with a navy tie and a cream
             pocket square, brown brogues;
    scout_2  a blond undercut (the sides shaved close), a black suit over a charcoal turtleneck, a gold watch, an
             earpiece in the right ear, black boots.

Every face is drawn pixel by pixel from the maps below (one letter a colour, '.' clear), on the association's painter
(tools/gen_association_assets.py): cloth gets a weave of noise and a light from above.

Run from the repo root:   python3 tools/gen_scout_assets.py             (writes the skins)
                          python3 tools/gen_scout_assets.py --preview   (and build/preview/scouts.png)
Needs: pillow, numpy.
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_association_assets as ga  # noqa: E402
from gen_association_assets import (HEAD, HAT, BODY, R_ARM, L_ARM, R_LEG, L_LEG, Skin, fill, mirror, rows,  # noqa: E402
                                    skin_pal, hair_pal, eye_pal)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo", "textures", "entity", "scouting")
PREVIEW = os.path.join(ROOT, "build", "preview", "scouts.png")


def suit_body(sk, front, back_row, side=None, top=None):
    """The jacket: a front map, a back row repeated down to the hem, plain sides."""
    side = side or rows(("J", 4, 11), ("j", 4, 1))
    back = rows([back_row] * 11, ["jjjjcjjj"])
    top = top or ["JJJJJJJJ", "JJWWWWJJ", "JJWwwWJJ", "JLWWWWLJ"]
    sk.box(BODY, side, front, side, back, top, fill("j", 8, 4), light=0.06)


def suit_arms(sk, cuff="W", watch=None):
    """Sleeves to the wrist, a shirt cuff (or none), the hand; a watch on the left wrist if given."""
    for box, outer_right in ((R_ARM, True), (L_ARM, False)):
        wrist = (watch if watch and not outer_right else cuff) or "J"
        sleeve = rows(("J", 4, 9), (wrist, 4, 1), ("S", 4, 1), ("s", 4, 1))
        inner = rows(("j", 4, 9), ("w" if wrist == "W" else "j" if wrist == "J" else wrist, 4, 1), ("s", 4, 2))
        back_ = rows(("J", 4, 6), ["JcJJ"], ("J", 4, 2), (wrist, 4, 1), ("s", 4, 2))
        r, l = (sleeve, inner) if outer_right else (inner, sleeve)
        sk.box(box, r, sleeve, l, back_, fill("J", 4, 4), fill("s", 4, 4), light=0.06)


def suit_legs(sk, shoe, shoe_light, crease="K"):
    """Trousers with a crease down the front, the shoes."""
    for box, outer_right in ((R_LEG, True), (L_LEG, False)):
        outer = rows(("N", 4, 10), (shoe, 4, 1), ("Q", 4, 1))
        inner = rows(("n", 4, 10), (shoe, 4, 1), ("Q", 4, 1))
        front = rows([("N" + crease + "NN") if outer_right else ("NN" + crease + "N")] * 10, [shoe_light * 4, shoe * 4])
        back_ = rows(("N", 4, 10), (shoe, 4, 1), ("Q", 4, 1))
        r, l = (outer, inner) if outer_right else (inner, outer)
        sk.box(box, r, front, l, back_, fill("N", 4, 4), fill("Q", 4, 4), light=0.04)


# ------------------------------------------------------------------------------------------------- scout_0

def scout_bob():
    pal = {
        # a charcoal pinstripe suit
        "J": ("3a3d45", 0.05), "j": ("2a2c33", 0.05), "i": ("5a5e69", 0.03), "L": ("4a4e58", 0.03), "c": ("202228", 0.03),
        "p": ("24262c", 0.03), "b": ("101114", 0.0),
        "W": ("eef0f4", 0.015), "w": ("c6ccd6", 0.02), "V": ("dfe4ec", 0.0), "v": ("9aa2b0", 0.0),
        "N": ("33363e", 0.05), "n": ("26282e", 0.04), "K": ("454953", 0.03),
        "O": ("141418", 0.02), "o": ("34343c", 0.02), "Q": ("0a0a0c", 0.0),
        "r": ("9c3a46", 0.0),
    }
    pal.update(skin_pal("light"))
    pal.update(hair_pal("16161a", "0c0c0f", "34343e"))
    pal.update(eye_pal("5a4636"))
    pal["B"] = ("0c0c0f", 0.0)
    sk = Skin(300, pal)
    front = [
        "JLWWWWLJ",
        "JLWvVWLJ",
        "JiLWVLiJ",
        "JJiLLiJJ",
        "JiJJbJiJ",
        "JiJJcJiJ",
        "JiJJbJiJ",
        "JppJcppJ",
        "JiJJcJiJ",
        "JiJJcJiJ",
        "JiJJcJiJ",
        "jjjjcjjj",
    ]
    suit_body(sk, front, "JiJJjJiJ", side=rows(["JJiJ"] * 11, ["jjjj"]))
    suit_arms(sk)
    suit_legs(sk, "O", "o")
    # the bob: straight across the brow, down past the ears to the jaw
    head_front = ["HHHHHHHH", "HHkHHHkH", "HSSSSSSH", "HBBSSBBH", "HEISSIEH", "hSSssSSh", "hSSrrSSh", "hdSSSSdh"]
    head_side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHS", "HHHHHHHS", "HHHHHHhS", "hhhhhhhs"]
    head_back = ["HHHHHHHH", "HHkHHHHH", "HHHHHkHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hhhhhhhh"]
    head_top = ["HHHHHHHH", "HHHkHHHH", "HHHhHHHH", "HkHhHHkH", "HHHhHHHH", "HHHhHkHH", "HkHhHHHH", "HHHhHHHH"]
    sk.box(HEAD, head_side, head_front, mirror(head_side), head_back, head_top, fill("d", 8, 8))
    hat_front = ["HHHHHHHH", "HHkHHHkH", "H......H", "H......H", "H......H", "H......H", "h......h", "h......h"]
    hat_side = ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHH.", "HHHHHHH.", "HHHHHHH.", "HHHHHHh.", "hhhhhhh."]
    hat_back = ["HHHHHHHH", "HHkHHHHH", "HHHHHkHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "hhhhhhhh"]
    hat_top = ["HHHHHHHH", "HHHkHHHH", "HHHhHHHH", "HkHhHHkH", "HHHhHHHH", "HHHhHkHH", "HkHhHHHH", "HHHhHHHH"]
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    return sk


# ------------------------------------------------------------------------------------------------- scout_1

def scout_glasses():
    pal = {
        # a light grey suit, a navy tie, a cream pocket square
        "J": ("8a909b", 0.04), "j": ("6c727d", 0.04), "L": ("9ea4ae", 0.03), "c": ("5a606a", 0.03), "p": ("737984", 0.03),
        "b": ("2e3036", 0.0),
        "W": ("f0f2f6", 0.015), "w": ("c8cdd6", 0.02), "T": ("263a66", 0.02), "t": ("1a2a4c", 0.02),
        "X": ("ece2c8", 0.0), "x": ("c8bc9e", 0.0),
        "N": ("7e848f", 0.04), "n": ("666c76", 0.04), "K": ("969ca6", 0.03),
        "O": ("5a3a24", 0.04), "o": ("7a5236", 0.03), "Q": ("2a1a10", 0.0),
        "F": ("b4bac6", 0.0), "f": ("dfe6ee", 0.0),
    }
    pal.update(skin_pal("tan"))
    pal.update(hair_pal("3a2618", "28190f", "5a3e28"))
    pal.update(eye_pal("3e2c20"))
    pal["B"] = ("28190f", 0.0)
    sk = Skin(301, pal)
    front = [
        "JLWTTWLJ",
        "JLWTTWLJ",
        "JjLTTLjJ",
        "JJLTtLXJ",
        "JJjLLjxJ",
        "JJJJbJJJ",
        "JJJJcJJJ",
        "JJJJbJJJ",
        "JppJcppJ",
        "JJJJcJJJ",
        "JJJJcJJJ",
        "jjjjcjjj",
    ]
    suit_body(sk, front, "JJJJjJJJ", top=["JJJJJJJJ", "JJWWWWJJ", "JJWTTWJJ", "JLWTTWLJ"])
    suit_arms(sk)
    suit_legs(sk, "O", "o")
    # combed straight back from a high brow; thin dark frames
    head_front = ["HHkHHkHH", "HkHHkHHk", "hSSSSSSh", "SBBSSBBS", "FEIFFIEF", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
    head_side = ["HHHHHHHH", "HkHHHkHH", "HHHHHHHS", "HHHHHSSS", "HHHFFFFF", "HHHheSSS", "hHHsSSSs", "hhssssss"]
    head_back = ["HHHHHHHH", "HkHHkHHk", "HHHHHHHH", "HkHHkHHk", "HHHHHHHH", "hHHHHHHh", "hhhhhhhh", "ssssssss"]
    head_top = ["HkHHkHHk", "HkHHkHHk", "HkHhkHHk", "HkHhkHhk", "HkHhkHhk", "HkHHkHhk", "HkHHkHHk", "HkHHkHHk"]
    sk.box(HEAD, head_side, head_front, mirror(head_side), head_back, head_top, fill("d", 8, 8))
    hat_front = ["HHkHHkHH"] + ["........"] * 7
    hat_side = ["HHHHHHHH", "HHHHHHH."] + ["........"] * 6
    hat_back = ["HHHHHHHH", "HkHHkHHk", "hHHHHHHh"] + ["........"] * 5
    hat_top = ["HkHHkHHk", "HkHHkHHk", "HkHhkHHk", "HkHhkHhk", "HkHhkHhk", "HkHHkHhk", "HkHHkHHk", "HkHHkHHk"]
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    return sk


# ------------------------------------------------------------------------------------------------- scout_2

def scout_undercut():
    pal = {
        # a black suit over a charcoal turtleneck
        "J": ("1e1f24", 0.05), "j": ("141519", 0.04), "L": ("2c2e35", 0.03), "c": ("0e0f12", 0.02), "p": ("17181c", 0.03),
        "b": ("3c3e46", 0.0),
        "R": ("383a42", 0.03), "r": ("2a2c33", 0.03), "W": ("383a42", 0.03), "w": ("2a2c33", 0.03),
        "G": ("e8be55", 0.0), "g": ("a8822e", 0.0),
        "N": ("1a1b20", 0.04), "n": ("131418", 0.03), "K": ("26282e", 0.03),
        "O": ("0f0f12", 0.02), "o": ("2a2a30", 0.02), "Q": ("08080a", 0.0),
        "Z": ("2e3036", 0.0), "z": ("5a5e68", 0.0),
        "u": ("a88c5a", 0.08),
    }
    pal.update(skin_pal("light"))
    pal.update(hair_pal("d8b46a", "b8944e", "f2d690"))
    pal.update(eye_pal("587a8a"))
    pal["B"] = ("8a6c3a", 0.0)
    sk = Skin(302, pal)
    front = [
        "JLRRRRLJ",
        "JLRrrRLJ",
        "JjLRRLjJ",
        "JJLRrLJJ",
        "JJjLLjJJ",
        "JJJJbJJJ",
        "JJJJcJJJ",
        "JJJJbJJJ",
        "JppJcppJ",
        "JJJJcJJJ",
        "JJJJcJJJ",
        "jjjjcjjj",
    ]
    suit_body(sk, front, "JJJJjJJJ", top=["JJJJJJJJ", "JJRRRRJJ", "JJRrrRJJ", "JLRRRRLJ"])
    suit_arms(sk, cuff=None, watch="G")
    suit_legs(sk, "O", "o")
    # long on top and swept back, the sides and the back shaved close; an earpiece in the right ear
    head_front = ["HHkHHkHH", "HkHHHkHH", "uSSSSSSu", "SBBSSBBS", "SEISSIES", "sSSssSSs", "sSSmmSSs", "dsSSSSsd"]
    side_r = ["HHHHHHHH", "hHHHHHHH", "uuuuuuuS", "uuuuuSSS", "uuuuZSSS", "uuuhzSSS", "uuusSSSs", "ssssssss"]
    side_l = mirror(["HHHHHHHH", "hHHHHHHH", "uuuuuuuS", "uuuuuSSS", "uuuueSSS", "uuuheSSS", "uuusSSSs", "ssssssss"])
    head_back = ["HHHHHHHH", "hHHkHHHh", "uuuuuuuu", "uuuuuuuu", "uuuuuuuu", "uuuuuuuu", "suuuuuus", "ssssssss"]
    head_top = ["HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "hhhhhhhh"]
    sk.box(HEAD, side_r, head_front, side_l, head_back, head_top, fill("d", 8, 8))
    hat_front = ["HHkHHkHH"] + ["........"] * 7
    hat_side = ["HHHHHHHH"] + ["........"] * 7
    hat_back = ["HHHHHHHH", "hhhhhhhh"] + ["........"] * 6
    hat_top = ["HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "HHkHHkHH", "HkHHkHHk", "hhhhhhhh"]
    sk.box(HAT, hat_side, hat_front, mirror(hat_side), hat_back, hat_top, None)
    return sk


SKINS = {"scout_0": scout_bob, "scout_1": scout_glasses, "scout_2": scout_undercut}


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
        print("preview", ga.preview(images, PREVIEW))


if __name__ == "__main__":
    main()
