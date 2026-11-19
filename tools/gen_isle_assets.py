#!/usr/bin/env python3
"""
The Halcyon raid's creatures (see com.beyondsolo.isle): the hive's queen and king, and the hearts of its nests.

- Vessa, Mother of the Hive (hive_queen): a queen of the swarm the size of a cottage. Her front half rears up like a
  mantis's in black and amber chitin: a crowned head with great compound eyes burning amber, jaws dripping acid, long
  feelers, a frill of chitin behind her head edged in spines, two raptorial forelegs folded like scythes and four
  legs splayed under her. Behind her lies her abdomen, bloated with eggs: pale stretched membrane in fat rings, dark
  plates floating on it, veins under it and the eggs glowing through, an ovipositor at its end. Her wings are torn
  stubs: she has not flown since she took the island.
- Azhrak, the Hive King (hive_king): her last child, a lean hunter of black chitin that stands like a man and is a
  head taller than one. A narrow waist and a chest of ridged plates with seams of venom light, a long crested skull
  with compound eyes like green coals and split jaws, feelers swept back, plated arms ending in three scythe claws
  each, a blade along each forearm, legs of plated chitin on taloned feet, and two pairs of smoky iridescent wings
  folded down his back.
- A nest's heart (nest_heart): a great sac of living flesh the swarm grows in every nest, gripped by ribs of chitin
  rising out of the ground, its veins glowing amber, a seam down its front that splits when it broods.

All three are built with the creature kit (tools/gen_creatures.py, one skin each painted on the model by
tools/creature_skins.py) at a scale of their design (MODEL_SCALE), like the world bosses (tools/gen_worldboss_assets.py,
whose motion helpers this uses). Their animations' times match the moves in QueenBoss, KingBoss and NestHeartEntity
(see each one's notes).

Run from the repo root:   python3 tools/gen_isle_assets.py                    (everything)
                          python3 tools/gen_isle_assets.py queen heart        (only some)
Look at one without the game:   python3 tools/preview_model.py hive_queen_main --anim slam --time 0.8
Needs: pillow, numpy.

Conventions (as the creature kit): 16 units are one block, the front of a model is -z, +x is the model's left. A
positive x rotation tilts a bone's top towards the front; a positive z rotation tilts its top towards +x.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature_skins as CS  # noqa: E402
import gen_creatures as GC  # noqa: E402
import gen_enemy_assets as G  # noqa: E402
import gen_worldboss_assets as W  # noqa: E402
from gen_gate_assets import ease_in, ease_in_out, ease_out_back, window  # noqa: E402

anim, wave = G.anim, G.wave
pose, timeline, lin, shake, bump, OneSkin = W.pose, W.timeline, W.lin, W.shake, W.bump, W.OneSkin
mx, limb = GC.mirror_x, GC.limb


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


# ============================================================================================= paint

def egg_sac(f, p, s):
    """The queen's abdomen where her plates leave it bare: amber membrane stretched tight over her eggs, each egg a
    swelling lighter at its middle with a dark rim where it presses on the next, a net of dark veins over them, and
    some of the eggs lit from inside."""
    X, Y, Z = f.X, f.Y, f.Z
    r = s.get("r", "sac")
    sd = s.get("seed", 300)
    n = CS.fbm3(X * 0.32, Y * 0.32, Z * 0.32, sd, 3)
    # a lighter amber than the plates over it, so the swollen abdomen still reads in the dark of her chamber
    t = 0.58 + (n - 0.5) * 0.14
    if f.wall:
        hn = (Y - f.bottom) / max(f.top - f.bottom, 1e-6)
        t = t + 0.08 * (hn - 0.5) - 0.2 * CS.sstep(0.3, 0.0, hn)
    elif f.face == "down":
        t = t - 0.24
    p.fill(r, t)
    size = s.get("egg", 2.2)
    d1, d2, idv = CS.worley3(X, Y, Z, size, sd + 1)
    rim = CS.sstep(size * 0.16, size * 0.04, d2 - d1)
    mid = np.clip(1 - d1 / (size * 0.6), 0, 1)
    p.shade(0.2 * (mid - 0.35))
    p.over(r + "_d", 0.18, 0.75 * rim)
    lit = (idv > s.get("lit", 0.55)) & (f.face != "down")
    # the lit eggs glow from their whole middle out (full-bright: they show in any dark)
    p.glow("glow", 0.35 + 0.55 * mid, lit * (1 - rim) * (0.2 + 0.8 * mid ** 0.9))
    p.over(r + "_l", 0.92, 0.5 * CS.sstep(0.78, 0.92, mid) * ~lit)
    # veins: thin dark lines over the eggs, thicker where they meet
    v1 = np.abs(CS.fbm3(X * 0.24 + 7, Y * 0.3, Z * 0.24, sd + 5, 3) - 0.5)
    v2 = np.abs(CS.fbm3(X * 0.5, Y * 0.6 + 2, Z * 0.5, sd + 6, 2) - 0.5)
    p.over("vein", 0.3, 0.85 * CS.sstep(0.026, 0.01, v1))
    p.over("vein", 0.38, 0.55 * CS.sstep(0.02, 0.008, v2) * (v1 < 0.08))


def heart_flesh(f, p, s):
    """A nest's heart: wet raw flesh in knotted masses, glossy, its veins swollen and glowing amber where the light of
    the brood runs through them, darker in the folds."""
    X, Y, Z = f.X, f.Y, f.Z
    r = s.get("r", "meat")
    sd = s.get("seed", 400)
    n = CS.fbm3(X * 0.4, Y * 0.4, Z * 0.4, sd, 3)
    m = CS.fbm3(X * 0.18, Y * 0.22, Z * 0.18, sd + 1, 2)
    t = 0.46 + (n - 0.5) * 0.3 + (m - 0.5) * 0.2
    if f.face == "down":
        t = t - 0.2
    p.fill(r, t)
    # folds: dark creases between the masses
    fold = np.abs(CS.fbm3(X * 0.3 + 3, Y * 0.3, Z * 0.3, sd + 2, 2) - 0.5)
    p.over(r + "_d", 0.24, 0.8 * CS.sstep(0.05, 0.015, fold))
    # gloss: wet highlights on the tops of the masses
    p.over(r + "_l", 0.82, CS.sstep(0.66, 0.74, n) * (0.7 if f.face != "down" else 0.0))
    # veins: glowing along their cores, dark rims round them
    v = np.abs(CS.fbm3(X * 0.28 + 9, Y * 0.2, Z * 0.28, sd + 4, 3) - 0.5)
    v2 = np.abs(CS.fbm3(X * 0.6, Y * 0.5 + 4, Z * 0.6, sd + 5, 2) - 0.5)
    p.over("vein", 0.3, CS.sstep(0.04, 0.022, v))
    p.glow("glow", 0.55 + 0.4 * CS.sstep(0.012, 0.0, v), CS.sstep(0.019, 0.007, v) * s.get("veins", 1.0))
    p.glow("glow", 0.5, 0.7 * CS.sstep(0.01, 0.004, v2) * (v < 0.1) * s.get("veins", 1.0))


def seam(f, p, s):
    """The brood seam down the front of a nest's heart: a split of light between swollen lips."""
    heart_flesh(f, p, s)
    if f.face != "north":
        return
    a = f.X - s.get("seam_x", 0.0)
    y0, y1 = s.get("seam_y", (f.bottom + 2, f.top - 2))
    hn = np.clip((f.Y - y0) / max(y1 - y0, 1e-6), 0, 1)
    inside = (f.Y > y0) & (f.Y < y1)
    w = s.get("seam_w", 1.0) * np.sin(hn * math.pi) ** 0.6
    p.over("meat_d", 0.18, inside & (np.abs(a) < w * 1.9))
    p.over("meat_l", 0.7, inside & (np.abs(np.abs(a) - w * 1.3) < f.texel * 1.2))
    p.glow("glow", 0.95, inside & (np.abs(a) < w * 0.55))
    p.glow("glow", 0.6, inside & (np.abs(a) < w))


# ============================================================================================= Vessa, the queen

QUEEN = "hive_queen"
G.MODEL_SCALE[QUEEN] = 2.0

QUEEN_RAMPS = {
    "chitin": [(5, 3, 2), (20, 12, 7), (46, 28, 15), (100, 68, 36), (196, 156, 98)],
    "stripe": (226, 150, 44),
    "face": (196, 116, 38),
    "sac": [(30, 12, 4), (96, 44, 12), (176, 98, 30), (228, 156, 64), (252, 214, 140)],
    "vein": [(30, 8, 6), (70, 22, 14), (120, 46, 26), (164, 84, 50), (204, 140, 100)],
    "eye": [(4, 2, 1), (28, 12, 4), (82, 36, 8), (160, 92, 30), (240, 192, 120)],
    "mandible": [(6, 3, 2), (34, 14, 6), (100, 50, 18), (176, 120, 60), (238, 214, 170)],
    "spine": [(4, 3, 2), (24, 16, 10), (70, 50, 30), (150, 120, 80), (226, 210, 170)],
    "edge": [(40, 30, 20), (110, 90, 60), (186, 164, 120), (226, 212, 180), (252, 246, 230)],
    "wingskin": (110, 92, 62),
    "crown": [(8, 4, 2), (54, 30, 10), (146, 88, 26), (224, 160, 62), (255, 228, 156)],
    "sheen": (124, 92, 166),
}
AMBER_LIGHT = (255, 168, 46)

CK = dict(r="chitin", hair=0.0)


def queen():
    """Vessa, built 24 units tall to her eyes (her crown to 36) and drawn twice as big: her head 2.4 blocks up and 2
    blocks before her middle (where she spits from, QueenBoss.mouth), her abdomen lying 4 blocks back behind her.
    Bones: root; body (the thorax she stands on, its four legs: right_leg1/right_shin1 the middle pair, right_leg2/
    right_shin2 the hind; the same on the left), right_wing/left_wing (torn stubs), neck (the long prothorax rearing up
    forward, carrying the frill), right_fore/right_fore2 and left_fore/left_fore2 (the raptorial forelegs: the femur
    standing up before her chest and the scythe folded down in front of it), head, right_mandible/left_mandible,
    right_antenna/left_antenna; abdomen and abdomen2 (its rear half), ovipositor."""
    B = OneSkin(QUEEN, 9100)
    box, col, bar, c = B.box, B.col, B.bar, B.c
    B.bone("root", None, [0, 0, 0])

    # ---------------------------------------------------------------- the thorax she stands on
    B.bone("body", "root", [0, 9.0, 2.0], [
        box(0, 10.2, 0.6, 11.0, 7.6, 10.4, "segment", axis="z", r2="stripe", band=(0.72, 0.92), notch=0.3, seed=10, **CK, gloss=0.9),
        box(0, 14.5, 0.8, 8.6, 1.4, 8.4, "segment", axis="z", r2="stripe", band=(0.0, 0.16), seed=11, **CK),
        # the plates over her shoulders, lips of amber along them
        *[c(mx([3.4, 12.0, -4.2], [3.0, 4.0, 8.8], sx), [3.0, 4.0, 8.8], "segment", axis="z", seed=12 + (sx > 0), r2="stripe", band=(0.84, 1.0),
            pivot=[sx * 4.4, 14.0, 0.0], rot=[0, 0, sx * 18], **CK) for sx in (-1, 1)],
        box(0, 6.6, 0.6, 8.4, 2.0, 9.0, "segment", axis="z", seed=14, **CK, gloss=0.3),
        # a keel of spines down her back
        *[c([-0.35, 14.8, -2.8 + i * 2.2], [0.7, 1.8 - 0.2 * i, 1.0], "horn", r="spine", tip="+y", pivot=[0, 15.0, -2.4 + i * 2.2], rot=[-22, 0, 0])
          for i in range(4)],
    ])
    # the wings she shed: torn stubs folded on her back
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_wing", "body", [sx * 1.8, 15.0, 2.6], [
            c(mx([0.0, 14.9, 2.6], [3.6, 0.3, 9.6], sx), [3.6, 0.3, 9.6], "skin", r="wingskin", marks=0.0, seed=20 + (sx > 0), fx=GC.insect_wing,
              wing=dict(span="z", lead="out", sheen="sheen", tip_dark=0.3), skip=("north", "south", "east", "west")),
        ], rot=[-8, sx * 16, sx * -12])

    # ---------------------------------------------------------------- the legs she stands on: long and spurred, the
    # knees high over her back like a spider's, the shins reaching out and down to clawed feet
    legs = {1: (-1.4, 28), 2: (3.6, -24)}
    for j, (z, yaw) in legs.items():
        for side, sx in (("right", -1), ("left", 1)):
            hip, knee, ankle, foot = [sx * 5.0, 9.8, z], [sx * 12.0, 17.4, z], [sx * 17.0, 2.4, z], [sx * 18.6, 0.6, z]
            B.bone(f"{side}_leg{j}", "body", hip, [
                box(sx * 5.6, 9.8, z, 3.4, 3.6, 3.6, "segment", seed=30 + j, **CK, gloss=0.8),
                *limb(B, hip, [((hip[0] + knee[0]) / 2, (hip[1] + knee[1]) / 2, z, 3.0, dict(seed=31 + j)),
                               (knee[0], knee[1], z, 2.4, dict(seed=33 + j))], "segment", axis="y", r2="stripe", band=(0.88, 1.0), **CK),
                box(knee[0], knee[1], z, 2.6, 2.6, 2.6, "segment", seed=35 + j, **CK, gloss=0.9),
                *limb(B, [knee[0], knee[1] + 0.8, z], [(knee[0] + sx * 0.6, knee[1] + 4.4, z + 0.9, 1.0)], "horn", r="spine", tip="+y"),
            ], rot=[0, sx * yaw, 0])
            mid = [knee[0] * 0.55 + ankle[0] * 0.45, knee[1] * 0.55 + ankle[1] * 0.45, z]
            B.bone(f"{side}_shin{j}", f"{side}_leg{j}", knee, [
                *limb(B, knee, [(mid[0], mid[1], z, 2.1, dict(seed=37 + j)), (ankle[0], ankle[1], z, 1.5, dict(seed=39 + j))],
                      "segment", axis="y", r2="stripe", band=(0.0, 0.08), **CK),
                *[cc for k in range(3) for cc in limb(B, [knee[0] + (ankle[0] - knee[0]) * (0.22 + 0.24 * k),
                                                           knee[1] + (ankle[1] - knee[1]) * (0.22 + 0.24 * k), z],
                                                       [(knee[0] + (ankle[0] - knee[0]) * (0.22 + 0.24 * k) + sx * 1.4,
                                                         knee[1] + (ankle[1] - knee[1]) * (0.22 + 0.24 * k) + 0.7, z, 0.5)],
                                                       "horn", r="spine", tip="+x" if sx > 0 else "-x")],
                *limb(B, ankle, [(foot[0], foot[1], z, 1.1)], "segment", r="chitin", hair=0.0, gloss=0.6),
                *limb(B, foot, [(foot[0] + sx * 1.0, 0.0, z - 1.0, 0.8)], "horn", r="spine", tip="-y"),
            ])

    # ---------------------------------------------------------------- the prothorax rearing up, its frill
    frill = []
    for i, a in enumerate((-68, -45, -22, 0, 22, 45, 68)):
        # the frill: plates of chitin fanned out behind her head like a crown's back, a vein of light down each, a spine
        # at the end of each
        ln = 8.6 - abs(a) * 0.03
        pv = dict(pivot=[0, 23.0, -1.0], rot=[-24, 0, a])
        frill += [c([-1.5, 23.0, -1.4], [3.0, ln, 0.7], "segment", axis="y", r2="stripe", band=(0.0, 0.12), seed=43 + i, **CK, gloss=0.6,
                    lines=[dict(pts=[(0.0, 23.8), (0.0, 23.0 + ln - 0.8)], w=0.32, faces=("north",), halo=0.6, bright=0.85)], **pv),
                  c([-0.45, 23.0 + ln - 0.2, -1.3], [0.9, 2.6, 0.5], "horn", r="spine", tip="+y", **pv)]
    B.bone("neck", "body", [0, 13.4, -3.4], [
        col(0, -3.4, 12.8, 18.6, 7.6, 7.0, "segment", axis="y", r2="stripe", band=(0.82, 1.0), seed=40, **CK),
        col(0, -3.4, 18.2, 22.6, 6.6, 6.0, "segment", axis="y", r2="stripe", band=(0.84, 1.0), seed=41, **CK),
        col(0, -3.4, 22.2, 24.8, 5.6, 5.2, "segment", axis="y", seed=42, **CK),
        *frill,
        *[c(mx([3.5, 13.4, -6.8], [2.8, 5.6, 2.8], sx), [2.8, 5.6, 2.8], "segment", axis="y", r2="stripe", band=(0.0, 0.12), seed=48 + (sx > 0),
            pivot=[sx * 4.9, 19.0, -5.4], rot=[-45, 0, 0], **CK, gloss=0.8) for sx in (-1, 1)],
    ], rot=[30, 0, 0])

    # ---------------------------------------------------------------- the raptorial forelegs
    # Each hangs from her shoulder on a heavy coxa (part of the neck), the femur standing up from it before her face
    # with a double row of spines down its front, the scythe (the tibia) folded down along those spines, its pale
    # saw edge locked against them and a hook at its end. At rest (with the neck's tilt) the femur leans 40 degrees
    # forward, the scythes held up either side of her jaws like a mantis's. Anim: fore > 0 swings the femur forward
    # and down, < 0 back and up; fore2 < 0 opens the scythe away from the femur.
    for side, sx in (("right", -1), ("left", 1)):
        inner = ("east",) if sx > 0 else ("west",)
        B.bone(f"{side}_fore", "neck", [sx * 4.9, 15.0, -9.2], [
            box(sx * 4.9, 15.2, -9.2, 3.2, 3.2, 3.2, "segment", seed=50, **CK, gloss=0.9),
            c(mx([3.8, 15.4, -10.6], [2.2, 14.0, 2.8], sx), [2.2, 14.0, 2.8], "segment", axis="y", seed=51 + (sx > 0), r2="stripe",
              band=(0.9, 1.0), paint_faces=[dict(faces=inner, ramp="stripe", tone=0.4, seed=52)], **CK),
            *[c(mx([3.9 + 1.3 * (i % 2), 16.8 + i * 1.35, -11.8 - 0.5 * (i % 2)], [0.7, 0.6, 1.2 + 0.6 * (i % 2)], sx), [0.7, 0.6, 1.2 + 0.6 * (i % 2)],
                "horn", r="spine", tip="-z") for i in range(9)],
        ], rot=[-2, 0, sx * 14])
        B.bone(f"{side}_fore2", f"{side}_fore", [sx * 4.9, 29.2, -10.4], [
            box(sx * 4.9, 29.2, -10.6, 2.6, 2.6, 2.6, "segment", seed=53, **CK, gloss=0.9),
            c(mx([4.2, 17.0, -13.0], [1.4, 12.0, 2.2], sx), [1.4, 12.0, 2.2], "segment", axis="y", seed=54 + (sx > 0), r2="edge", band=(0.0, 0.06),
              paint_faces=[dict(faces=("south",), ramp="edge", tone=0.62, seed=55)], **CK),
            *[c(mx([4.35, 17.8 + i * 1.5, -11.6], [0.7, 0.55, 1.0], sx), [0.7, 0.55, 1.0], "horn", r="edge", tip="+z") for i in range(7)],
            c(mx([4.3, 13.2, -12.6], [1.2, 4.2, 1.6], sx), [1.2, 4.2, 1.6], "horn", r="edge", tip="-y", pivot=[sx * 4.9, 17.4, -11.8], rot=[35, 0, 0]),
        ])

    # ---------------------------------------------------------------- the head and its crown
    crown = []
    for dx, h, w in ((0.0, 7.4, 1.5), (2.2, 5.6, 1.15), (-2.2, 5.6, 1.15), (3.8, 3.8, 0.85), (-3.8, 3.8, 0.85)):
        sx = 1 if dx >= 0 else -1
        x0, y0, z0 = dx, 29.2 - abs(dx) * 0.2, -5.8 + abs(dx) * 0.3
        crown += limb(B, [x0, y0, z0], [(x0 + sx * abs(dx) * 0.25, y0 + h * 0.55, z0 + h * 0.18, w, dict(kr=(0.0, 0.55))),
                                         (x0 + sx * abs(dx) * 0.45, y0 + h, z0 + h * 0.55, w * 0.55, dict(kr=(0.55, 1.0)))],
                      "horn", r="crown", tip="+y", ridged=4.0)
    B.bone("head", "neck", [0, 24.6, -3.4], [
        box(0, 26.0, -5.4, 9.0, 4.8, 6.4, "segment", axis="y", r="chitin", r2="face", band=(0.66, 1.0), seed=60, hair=0.0, gloss=0.8,
            facepaint=[dict(pts=[(0.0, 28.0), (0.0, 25.6)], w=1.0, ramp="chitin", tone=0.16),
                       dict(pts=[(-1.6, 27.8), (0.0, 26.0), (1.6, 27.8)], w=0.5, ramp="chitin", tone=0.16)]),
        box(0, 28.9, -5.0, 6.8, 1.2, 5.0, "segment", axis="z", seed=61, **CK, gloss=0.9, fx=GC.ocelli, ocelli=(0.0, -6.0, 0.34, 0.9)),
        # the brow, heavy over her eyes
        box(0, 27.6, -8.4, 9.4, 1.2, 1.8, "segment", axis="z", seed=62, **CK, gloss=0.9),
        *[c(mx([3.0, 23.6, -9.6], [2.8, 4.4, 3.8], sx), [2.8, 4.4, 3.8], "compound", r="eye", seed=63, facet=0.42,
            pivot=[sx * 3.8, 25.6, -7.6], rot=[0, sx * -12, sx * -6]) for sx in (-1, 1)],
        # her face: a plate narrowing down to the mouth, the mouth's light between her jaws, acid dripping from it
        box(0, 23.6, -8.9, 4.0, 3.6, 2.2, "segment", axis="y", r="face", seed=64, hair=0.0, gloss=0.6,
            cuts=[dict(pts=[(-1.3, 22.6), (0.0, 23.2), (1.3, 22.6)], w=0.24, faces=("north",))]),
        box(0, 21.6, -9.0, 2.8, 1.4, 1.8, "segment", axis="y", r="face", seed=65, hair=0.0, gloss=0.4),
        box(0, 20.8, -9.8, 2.2, 0.8, 0.6, "glow"),
        box(0, 19.8, -9.7, 0.6, 1.4, 0.5, "glow"),
        *crown,
    ], rot=[-28, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # jaws: heavy curved pincers, toothed on their inner edge, crossing at the tips
        B.bone(f"{side}_mandible", "head", [sx * 1.8, 21.6, -9.4], [
            c(mx([0.9, 20.2, -12.6], [1.7, 1.8, 3.8], sx), [1.7, 1.8, 3.8], "horn", r="mandible", tip="-z", kr=(0.0, 0.55),
              pivot=[sx * 1.8, 21.1, -9.4], rot=[12, sx * 16, 0]),
            c(mx([0.2, 19.7, -15.4], [1.2, 1.3, 3.0], sx), [1.2, 1.3, 3.0], "horn", r="mandible", tip="-z", kr=(0.55, 1.0),
              pivot=[sx * 1.0, 20.5, -12.8], rot=[14, sx * 50, 0]),
            *[c(mx([0.7 - k * 0.2, 19.9, -11.6 - k * 1.0], [0.5, 0.5, 0.5], sx), [0.5, 0.5, 0.5], "horn", r="edge", tip="-x" if sx > 0 else "+x",
                pivot=[sx * 1.8, 21.1, -9.4], rot=[12, sx * 16, 0]) for k in range(3)],
            c(mx([0.1, 18.9, -15.2], [0.6, 1.0, 0.6], sx), [0.6, 1.0, 0.6], "glow", pivot=[sx * 1.0, 20.5, -12.8], rot=[14, sx * 50, 0]),
        ])
        # feelers: elbowed, long, curling up and back over her crown
        B.bone(f"{side}_antenna", "head", [sx * 1.6, 27.4, -8.8], limb(B, [sx * 1.6, 27.4, -8.8], [
            (sx * 2.1, 30.6, -10.0, 0.7, dict(r="face")),
            (sx * 3.0, 34.0, -9.4, 0.56), (sx * 4.4, 36.8, -7.2, 0.46), (sx * 6.0, 37.8, -4.0, 0.38), (sx * 7.2, 37.2, -1.0, 0.32)],
            "segment", r="chitin", hair=0.0, gloss=0.0))

    # ---------------------------------------------------------------- the abdomen, swollen with eggs
    # rings (z0, z1, width, height, bottom), each rounded (a wide core and a taller, narrower dome over it): amber
    # membrane over the eggs, a plate of black chitin over its back and upper sides, shorter than the ring so the
    # membrane shows between one plate and the next
    rings = [(5.0, 9.8, 11.6, 9.6, 1.2), (8.8, 13.8, 15.0, 12.0, 0.7), (12.8, 17.6, 17.6, 13.8, 0.4), (16.6, 21.2, 18.6, 14.6, 0.3)]
    rings2 = [(20.2, 24.8, 18.0, 14.2, 0.3), (23.8, 28.2, 16.0, 12.6, 0.4), (27.2, 31.2, 12.8, 10.2, 0.6), (30.2, 33.6, 9.0, 7.4, 0.9)]

    def ring_cubes(rs, seed):
        out = []
        for i, (z0, z1, w, h, y0) in enumerate(rs):
            ln = z1 - z0
            sac = dict(r="sac", marks=0.0, fx=egg_sac, seed=seed + i, egg=2.2 + 0.2 * (i % 2), lit=0.32 + 0.05 * (i % 3))
            out.append(c([-w / 2, y0 + 1.0, z0], [w, h - 2.2, ln], "skin", **sac))
            out.append(c([-(w - 3.4) / 2, y0, z0 + 0.25], [w - 3.4, h, ln - 0.5], "skin", **{**sac, "seed": seed + 10 + i}))
            pz0, pl, ch = z0 + 0.5, ln - 2.4, h - 2.2
            out.append(c([-(w / 2 + 0.35), y0 + 1.0 + ch * 0.45, pz0], [w + 0.7, ch * 0.55 + 0.35, pl], "segment", axis="z", r2="stripe",
                         band=(0.86, 1.0), seed=seed + 20 + i, **CK, gloss=0.8))
            out.append(c([-(w - 3.4) / 2 - 0.35, y0 + h - 1.6, pz0 + 0.2], [w - 2.7, 1.95, pl - 0.4], "segment", axis="z", r2="stripe",
                         band=(0.86, 1.0), seed=seed + 30 + i, **CK, gloss=0.9))
        return out

    B.bone("abdomen", "body", [0, 7.0, 5.4], ring_cubes(rings, 70))
    B.bone("abdomen2", "abdomen", [0, 7.0, 20.4], ring_cubes(rings2, 110))
    B.bone("ovipositor", "abdomen2", [0, 4.8, 33.0], [
        bar(0, 4.6, 33.0, 36.0, 3.8, 3.2, "segment", axis="z", r2="stripe", band=(0.7, 1.0), seed=150, **CK),
        c([-1.0, 3.6, 35.8], [2.0, 2.0, 2.4], "horn", r="spine", tip="+z", kr=(0.0, 0.7)),
        c([-0.6, 4.0, 38.0], [1.2, 1.2, 0.8], "glow"),
    ], rot=[-10, 0, 0])

    GC.register(QUEEN, lambda aff: GC.pal("plain", AMBER_LIGHT, QUEEN_RAMPS, tinted=()), density=3.6)
    B.write(6.0, 3.2, 1.5)
    queen_anims()


def queen_anims():
    """Times match QueenBoss and HiveBoss: intro 100 ticks (she wakes and rears; the roar at 50), phase changes 50,
    death 110 (held). Moves: spit 40 (the glob at 8, a second at 24 in her last phase), slam 30 (the forelegs land at
    16), brood 50 (the eggs hatch at 22), shriek 44 (the wave at 14), guard 56 (the guard out of the walls at 26),
    acid_rain 64 (acid from the vault every 6 ticks from 6 to 54), crush 50 (the drag across the chamber 20 to 30)."""
    A = {}
    bones = ("root", "body", "right_wing", "left_wing", "right_leg1", "right_shin1", "left_leg1", "left_shin1", "right_leg2", "right_shin2",
             "left_leg2", "left_shin2", "neck", "right_fore", "right_fore2", "left_fore", "left_fore2", "head", "right_mandible", "left_mandible",
             "right_antenna", "left_antenna", "abdomen", "abdomen2", "ovipositor")
    STAND = pose(**{b: [0, 0, 0] for b in bones})

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def jaws(k):
        """Her jaws opened by k (1: wide). (A positive y turn swings a part pointing forward towards -x: so the right
        jaw, on -x, opens with a positive one.)"""
        return dict(right_mandible=[0, 26 * k, 0], left_mandible=[0, -26 * k, 0])

    def feelers(up=0.0, out=0.0):
        return dict(right_antenna=[-up, 0, -out], left_antenna=[-up, 0, out])

    def forelegs(fore, fore2, spread=0.0):
        return dict(right_fore=[fore, 0, -spread], left_fore=[fore, 0, spread], right_fore2=[fore2, 0, 0], left_fore2=[fore2, 0, 0])

    def breathe(L, k=1.0):
        # (extras add to the pose, so these are how far from it)
        return {"abdomen_scale": lambda t: [0.025 * k * wave(t, L), 0.035 * k * wave(t, L), 0],
                "abdomen2_scale": lambda t: [0.03 * k * wave(t, L, 0.12), 0.04 * k * wave(t, L, 0.12), 0]}

    # idle (4 s): her abdomen heaving slowly, her feelers searching, her jaws working, her forelegs twitching
    L = 4.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        **breathe(L),
        "body": lambda t: [0.8 * wave(t, L), 0, 0],
        "neck": lambda t: [1.5 * wave(t, L, 0.2), 3 * wave(t, L * 2), 0],
        "head": lambda t: [2 * wave(t, L, 0.4), 6 * wave(t, L * 2, 0.3), 0],
        "right_mandible": lambda t: [0, 6 * max(0.0, wave(t, L / 2)), 0], "left_mandible": lambda t: [0, -6 * max(0.0, wave(t, L / 2)), 0],
        "right_antenna": lambda t: [8 * wave(t, L / 2, 0.1), 6 * wave(t, L, 0.3), -5 * wave(t, L / 2, 0.5)],
        "left_antenna": lambda t: [8 * wave(t, L / 2, 0.6), -6 * wave(t, L, 0.8), 5 * wave(t, L / 2)],
        "right_fore2": lambda t: [4 * max(0.0, wave(t, L, 0.1)) ** 4, 0, 0], "left_fore2": lambda t: [4 * max(0.0, wave(t, L, 0.6)) ** 4, 0, 0],
        "ovipositor": lambda t: [4 * wave(t, L, 0.3), 0, 0],
    }), 0.05)

    # walk (2.2 s): a heavy creep, her legs stepping in two sets of three, her abdomen dragged swaying behind her
    L = 2.2
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        **breathe(L, 0.6),
        **{f"{s}_leg{j}": (lambda t, sx=sx, j=j: [0, sx * 16 * wave(t, L, ((j + (sx > 0)) % 2) * 0.5), 0])
           for s, sx in (("right", -1), ("left", 1)) for j in (1, 2)},
        **{f"{s}_shin{j}": (lambda t, sx=sx, j=j: [0, 0, sx * -12 * max(0.0, wave(t, L, ((j + (sx > 0)) % 2) * 0.5 + 0.25))])
           for s, sx in (("right", -1), ("left", 1)) for j in (1, 2)},
        "body": lambda t: [1.5 * abs(wave(t, L)), 3 * wave(t, L), 1.5 * wave(t, L)], "body_pos": lambda t: [0, 0.3 * abs(wave(t, L)), 0],
        "neck": lambda t: [2 * wave(t, L / 2), -4 * wave(t, L), 0],
        "head": lambda t: [-1.5 * wave(t, L / 2), 4 * wave(t, L), 0],
        "abdomen": lambda t: [0, -4 * wave(t, L, 0.15), 0], "abdomen2": lambda t: [0, -6 * wave(t, L, 0.3), 0],
        "right_fore": lambda t: [5 * wave(t, L), 0, 0], "left_fore": lambda t: [-5 * wave(t, L), 0, 0],
    }), 0.05)

    # dormant (6 s): asleep on her dais: her front half sunk low over her folded forelegs, her head down, feelers laid
    # back along her crown, only her abdomen moving
    L = 6.0
    SLEEP = P(body=[10, 0, 0], body_pos=[0, -2.4, 0], neck=[34, 0, 0], head=[22, 0, 0], **forelegs(40, 15), **feelers(-30, 10),
              right_leg1=[0, 0, 18], left_leg1=[0, 0, -18], right_shin1=[0, 0, -14], left_shin1=[0, 0, 14],
              right_leg2=[0, 0, 14], left_leg2=[0, 0, -14], right_shin2=[0, 0, -10], left_shin2=[0, 0, 10], abdomen=[2, 0, 0])
    A["dormant"] = anim(L, True, timeline([(0, SLEEP, lin), (L, SLEEP, lin)], extra={**breathe(L / 2, 1.4), "head": lambda t: [1.0 * wave(t, L / 2), 0, 0]}),
                        0.1)

    # intro (5 s): she wakes: a twitch of her feelers (0.5), her front half rising off the dais (1.0-2.2), and rearing up
    # with her forelegs spread and her jaws wide she shrieks (2.5); she settles at 4.2
    L = 5.0
    STIR = P(SLEEP, **feelers(10, 0), head=[14, 0, 0], **jaws(0.3))
    RISE = P(body=[-8, 0, 0], body_pos=[0, 1.0, 0], neck=[-14, 0, 0], head=[8, 0, 0], **forelegs(-15, -15, 8), **feelers(14, 8), **jaws(0.4))
    REAR = P(body=[-16, 0, 0], body_pos=[0, 1.8, 0.8], neck=[-26, 0, 0], head=[-20, 0, 0], **forelegs(-40, -50, 24), **feelers(30, 18), **jaws(1.0),
             right_wing=[0, 30, -24], left_wing=[0, -30, 24], abdomen=[6, 0, 0], abdomen2=[6, 0, 0], ovipositor=[12, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, SLEEP, lin), (0.6, STIR, ease_in_out), (1.0, STIR, lin), (2.2, RISE, ease_in_out), (2.5, REAR, ease_out_back),
                                          (3.6, REAR, lin), (4.4, STAND, ease_in_out), (L, STAND, lin)],
                                         extra={**breathe(0.6, 1.5), "head": lambda t: [0, 0, shake(t, 2.5, 3.6, 4, 38)],
                                                "right_mandible": lambda t: [0, shake(t, 2.5, 3.6, 5, 41), 0],
                                                "left_mandible": lambda t: [0, -shake(t, 2.5, 3.6, 5, 41), 0],
                                                "right_antenna": lambda t: [shake(t, 0.5, 1.0, 6, 35), 0, 0],
                                                "left_antenna": lambda t: [shake(t, 0.5, 1.0, 6, 31), 0, 0]}), 0.025)

    # phase2 (2.5 s): The Royal Guard: she rears and shrieks for her guard, her forelegs spread wide
    L = 2.5
    CALL = P(REAR, **forelegs(-35, -70, 34))
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.5, RISE, ease_in_out), (0.7, CALL, ease_out_back), (1.9, CALL, lin), (L, STAND, ease_in_out)],
                                          extra={"head": lambda t: [shake(t, 0.7, 1.9, 3, 33), 0, shake(t, 0.7, 1.9, 4, 41)],
                                                 "neck": lambda t: [0, shake(t, 0.7, 1.9, 2, 29), 0], **breathe(0.5, 1.2)}), 0.025)

    # phase3 (2.5 s): The Last Brood: her abdomen convulses, she hammers both forelegs down twice and rears in fury
    L = 2.5
    HAM_UP = P(body=[-10, 0, 0], body_pos=[0, 1.2, 0], neck=[-20, 0, 0], head=[-10, 0, 0], **forelegs(-45, -30, 12), **jaws(0.8), **feelers(20, 14))
    HAM_DN = P(body=[8, 0, 0], body_pos=[0, -1.0, -0.6], neck=[18, 0, 0], head=[10, 0, 0], **forelegs(45, -70, 6), **jaws(0.5), **feelers(-10, 14))
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.35, HAM_UP, ease_in_out), (0.5, HAM_DN, ease_in), (0.8, HAM_UP, ease_in_out),
                                           (0.95, HAM_DN, ease_in), (1.4, REAR, ease_out_back), (2.0, REAR, lin), (L, STAND, ease_in_out)],
                                          extra={"abdomen_scale": lambda t: [0.06 * bump(t, 0.1, 1.2) * (1 + 0.5 * math.sin(t * 30)),
                                                                             0.08 * bump(t, 0.1, 1.2) * (1 + 0.5 * math.sin(t * 30)), 0],
                                                 "abdomen2_scale": lambda t: [0.07 * bump(t, 0.2, 1.4), 0.09 * bump(t, 0.2, 1.4), 0],
                                                 "head": lambda t: [0, 0, shake(t, 1.4, 2.0, 4, 40)]}), 0.025)

    # death (5.5 s): she rears up screaming (0.2-1.0), shudders, and comes down; her legs fold, her head drops onto
    # her forelegs, her abdomen sags and is still
    L = 5.5
    SCREAM = P(REAR, **forelegs(-50, -70, 36), head=[-34, 0, 0])
    DOWN = P(body=[16, 0, 0], body_pos=[0, -5.0, -0.4], neck=[40, 0, 14], head=[36, 0, 18], **forelegs(55, 10, 26), **feelers(-40, 30), **jaws(0.6),
             right_leg1=[0, 0, 40], left_leg1=[0, 0, -40], right_shin1=[0, 0, -40], left_shin1=[0, 0, 40],
             right_leg2=[0, 0, 36], left_leg2=[0, 0, -36], right_shin2=[0, 0, -36], left_shin2=[0, 0, 36],
             right_wing=[10, 0, -20], left_wing=[10, 0, 20], abdomen=[4, -6, 0], abdomen2=[0, -10, 0], ovipositor=[-20, 0, 0],
             abdomen_scale=[0.96, 0.84, 1], abdomen2_scale=[0.95, 0.8, 1])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.8, SCREAM, ease_out_back), (1.8, SCREAM, lin), (3.0, DOWN, ease_in), (L, DOWN, lin)],
                                          extra={"body": lambda t: [shake(t, 1.0, 2.6, 2.5, 33), 0, shake(t, 1.0, 2.6, 2.0, 27)],
                                                 "right_fore2": lambda t: [shake(t, 3.0, 4.6, 6, 22), 0, 0],
                                                 "left_fore2": lambda t: [shake(t, 3.0, 4.4, 6, 19), 0, 0],
                                                 "right_shin1": lambda t: [0, 0, shake(t, 3.0, 4.8, 8, 25)],
                                                 "left_shin2": lambda t: [0, 0, shake(t, 3.0, 4.8, 8, 23)]}), 0.025)

    # spit (2.0 s): her head drawn back, her jaws open, and the glob spat at 0.4 s; in her last phase a second at 1.2 s
    L = 2.0
    DRAW = P(neck=[-12, 0, 0], head=[-22, 0, 0], body=[-4, 0, 0], **jaws(0.9), **feelers(16, 10), **forelegs(-8, -6))
    SPAT = P(neck=[16, 0, 0], head=[18, 0, 0], body=[4, 0, 0], body_pos=[0, 0, -0.6], **jaws(1.0), **feelers(-6, 14))
    A["spit"] = anim(L, False, timeline([(0, STAND, lin), (0.3, DRAW, ease_in_out), (0.42, SPAT, ease_in), (0.7, SPAT, lin), (0.95, DRAW, ease_in_out),
                                         (1.15, DRAW, lin), (1.24, SPAT, ease_in), (1.5, SPAT, lin), (L, STAND, ease_in_out)],
                                        extra={"head": lambda t: [shake(t, 0.42, 0.7, 2.5, 45), 0, 0]}), 0.025)

    # slam (1.5 s): both forelegs swung high over her (0.55), and brought down on the ground before her at 0.8
    L = 1.5
    LIFT = P(body=[-12, 0, 0], body_pos=[0, 1.2, 0.6], neck=[-22, 0, 0], head=[-8, 0, 0], **forelegs(-45, -60, 14), **jaws(0.7), **feelers(20, 8))
    SLAM = P(body=[10, 0, 0], body_pos=[0, -1.2, -1.0], neck=[22, 0, 0], head=[8, 0, 0], **forelegs(50, -80, 8), **jaws(0.4), **feelers(-10, 16),
             abdomen=[-4, 0, 0])
    A["slam"] = anim(L, False, timeline([(0, STAND, lin), (0.55, LIFT, ease_in_out), (0.68, LIFT, lin), (0.8, SLAM, ease_in), (1.05, SLAM, lin),
                                         (L, STAND, ease_in_out)],
                                        extra={"body": lambda t: [shake(t, 0.8, 1.05, 1.5, 50, 0.08), 0, 0]}), 0.025)

    # brood (2.5 s): her abdomen swells and heaves, the ovipositor lifting; the eggs hatch at 1.1 s as it contracts
    L = 2.5
    SWELL = P(body=[-6, 0, 0], neck=[-8, 0, 0], head=[-14, 0, 0], **jaws(0.6), **feelers(10, 12), abdomen=[-4, 0, 0], abdomen2=[-8, 0, 0],
              ovipositor=[-26, 0, 0], abdomen_scale=[1.1, 1.14, 1.04], abdomen2_scale=[1.12, 1.16, 1.05])
    LAY = P(body=[4, 0, 0], neck=[6, 0, 0], head=[6, 0, 0], **jaws(0.3), abdomen=[2, 0, 0], abdomen2=[6, 0, 0], ovipositor=[16, 0, 0],
            abdomen_scale=[0.95, 0.9, 0.98], abdomen2_scale=[0.94, 0.88, 0.98])
    A["brood"] = anim(L, False, timeline([(0, STAND, lin), (0.9, SWELL, ease_in_out), (1.12, LAY, ease_in), (1.6, LAY, lin), (L, STAND, ease_in_out)],
                                         extra={"abdomen": lambda t: [0, shake(t, 0.3, 0.9, 2, 31), 0], "abdomen2": lambda t: [0, shake(t, 0.3, 0.9, 3, 27), 0]}),
                      0.025)

    # shriek (2.2 s): she rears back (0.6) and shrieks at 0.7 s, her whole body shaking with it to 1.6 s
    L = 2.2
    BACK = P(body=[-10, 0, 0], body_pos=[0, 0.8, 0.6], neck=[-20, 0, 0], head=[-26, 0, 0], **forelegs(-25, -40, 28), **jaws(0.5), **feelers(20, 20),
             right_wing=[0, 20, -16], left_wing=[0, -20, 16])
    CRY = P(BACK, neck=[4, 0, 0], head=[-4, 0, 0], body=[2, 0, 0], **jaws(1.0), **feelers(36, 24), **forelegs(-40, -55, 38))
    A["shriek"] = anim(L, False, timeline([(0, STAND, lin), (0.55, BACK, ease_in_out), (0.72, CRY, ease_out_back), (1.6, CRY, lin), (L, STAND, ease_in_out)],
                                          extra={"head": lambda t: [shake(t, 0.72, 1.6, 3, 47), 0, shake(t, 0.72, 1.6, 4, 39)],
                                                 "body": lambda t: [0, shake(t, 0.72, 1.6, 1.5, 35), 0],
                                                 "right_wing": lambda t: [0, 0, shake(t, 0.72, 1.6, 10, 60)],
                                                 "left_wing": lambda t: [0, 0, shake(t, 0.72, 1.6, 10, 57)]}), 0.025)

    # guard (2.8 s): she rears tall, forelegs spread, and calls her guard out of the walls (1.3 s), stamping them in
    L = 2.8
    SUMMON = P(REAR, **forelegs(-45, -70, 44), head=[-30, 0, 0])
    STAMP = P(body=[6, 0, 0], body_pos=[0, -0.8, 0], neck=[10, 0, 0], head=[0, 0, 0], **forelegs(30, -50, 18), **jaws(0.8), **feelers(10, 20))
    A["guard"] = anim(L, False, timeline([(0, STAND, lin), (0.8, SUMMON, ease_in_out), (1.2, SUMMON, lin), (1.32, STAMP, ease_in), (1.7, STAMP, lin),
                                          (L, STAND, ease_in_out)],
                                         extra={"head": lambda t: [0, 0, shake(t, 0.6, 1.2, 4, 37)],
                                                "right_mandible": lambda t: [0, shake(t, 0.6, 1.2, 6, 43), 0],
                                                "left_mandible": lambda t: [0, -shake(t, 0.6, 1.2, 6, 43), 0]}), 0.025)

    # acid_rain (3.2 s): her head thrown up at the vault, she spits again and again from 0.3 s to 2.7 s
    L = 3.2
    UP = P(body=[-12, 0, 0], body_pos=[0, 0.8, 0.8], neck=[-30, 0, 0], head=[-40, 0, 0], **jaws(0.9), **feelers(30, 18), **forelegs(-20, -20, 18))
    A["acid_rain"] = anim(L, False, timeline([(0, STAND, lin), (0.3, UP, ease_in_out), (2.7, UP, lin), (L, STAND, ease_in_out)],
                                             extra={"head": lambda t: [10 * max(0.0, math.sin(t * math.pi / 0.3)) ** 3 * window(t, 0.3, 0.4) * (1 - window(t, 2.6, 2.7)),
                                                                       12 * math.sin(t * 2.1) * window(t, 0.3, 0.6) * (1 - window(t, 2.5, 2.8)), 0],
                                                    "neck": lambda t: [4 * max(0.0, math.sin(t * math.pi / 0.3)) ** 3 * window(t, 0.3, 0.4) * (1 - window(t, 2.6, 2.7)), 0, 0]}),
                              0.025)

    # crush (2.5 s): she sinks and gathers herself (to 1.0 s), then throws her whole weight forward down the line
    # (1.0-1.5 s, the boss itself moving), forelegs reaching and legs scrabbling; she rises after
    L = 2.5
    COIL = P(body=[12, 0, 0], body_pos=[0, -2.2, 1.0], neck=[24, 0, 0], head=[10, 0, 0], **forelegs(10, 10, 16), **jaws(0.6), **feelers(-20, 20),
             right_leg1=[0, -14, 10], left_leg1=[0, 14, -10], right_leg2=[0, -10, 12], left_leg2=[0, 10, -12], abdomen=[-6, 0, 0])
    THROW = P(body=[18, 0, 0], body_pos=[0, -1.6, -2.0], neck=[30, 0, 0], head=[-6, 0, 0], **forelegs(55, -90, 10), **jaws(1.0), **feelers(-30, 30),
              right_leg1=[0, 20, -6], left_leg1=[0, -20, 6], right_leg2=[0, 18, -8], left_leg2=[0, -18, 8], abdomen=[6, 0, 0], abdomen2=[8, 0, 0])
    A["crush"] = anim(L, False, timeline([(0, STAND, lin), (0.9, COIL, ease_in_out), (1.05, THROW, ease_in), (1.5, THROW, lin), (L, STAND, ease_in_out)],
                                         extra={**{f"{s}_shin{j}": (lambda t, sx=sx, j=j: [0, 0, sx * 14 * math.sin(t * 40 + j) * window(t, 1.0, 1.1) *
                                                                                          (1 - window(t, 1.4, 1.5))])
                                                   for s, sx in (("right", -1), ("left", 1)) for j in (1, 2)},
                                                "body": lambda t: [shake(t, 1.0, 1.5, 1.5, 44), 0, 0]}), 0.025)
    GC.write_anims(QUEEN, A)


# ============================================================================================= Azhrak, the king

KING = "hive_king"
G.MODEL_SCALE[KING] = 1.75
VENOM_LIGHT = (200, 255, 90)

KING_RAMPS = {
    "chitin": [(2, 4, 3), (9, 14, 9), (24, 32, 22), (58, 74, 48), (132, 156, 104)],
    "venom": (96, 168, 30),
    "eye": [(2, 6, 2), (8, 34, 6), (34, 100, 16), (120, 210, 50), (226, 255, 160)],
    "claw": [(2, 3, 2), (10, 14, 9), (30, 38, 26), (84, 100, 66), (176, 196, 146)],
    "crest": [(2, 4, 3), (10, 16, 10), (28, 40, 24), (66, 92, 46), (140, 186, 92)],
    "edge": [(30, 40, 24), (86, 110, 64), (160, 190, 130), (214, 236, 190), (246, 255, 236)],
    "wingskin": (52, 58, 56),
    "sheen": (104, 88, 170),
}
KK = dict(r="chitin", hair=0.0)
# his wings folded down his back: (tilt, spread) for the upper pair (0) and the lower (1)
KING_WINGS = {0: (-78, 14), 1: (-84, 24)}


def scythe_claw(B, x, y, z, rz, length=8.0, w=1.1, d=1.8, curve=34, seed=0):
    """One of the king's claws: a blade of chitin out of the wrist running down (fanned out by rz) and bending forward
    at its end, black at the root and paler toward the point, a fleck of venom on the point."""
    a, b = length * 0.55, length * 0.45
    jx, jy = x - a * math.sin(math.radians(rz)), y - a * math.cos(math.radians(rz))
    return [B.c([x - w / 2, y - a, z - d / 2], [w, a + 0.2, d], "horn", r="claw", tip="-y", kr=(0.0, 0.55), pivot=[x, y, z], rot=[0, 0, rz],
                seed=seed),
            B.c([jx - w * 0.4, jy - b, z - d * 0.4], [w * 0.8, b, d * 0.8], "horn", r="claw", tip="-y", kr=(0.55, 1.0), pivot=[jx, jy, z],
                rot=[-curve, 0, rz], seed=seed + 1),
            B.c([jx - 0.2, jy - b - 0.1, z - d * 0.4 - 0.5], [0.4, 0.6, 0.4], "glow", pivot=[jx, jy, z], rot=[-curve, 0, rz])]


def king():
    """Azhrak, built 35 units tall to the top of his crest and drawn 1.75 times bigger (3.8 blocks, his hitbox).
    Bones: root; hips (the waist's plates, plates hanging over his hips), gaster (the small abdomen behind them);
    right_leg/right_shin/right_foot and the left (plated legs, a spur at the knee, three talons forward and one
    back); body (a narrow waist, a chest of ridged plates with seams of venom light); head (a long skull swept back
    into a crest, compound eyes like green coals), right_mandible/left_mandible (split jaws), right_antenna/
    left_antenna; right_arm/right_hand and the left (spiked pauldrons; a blade along each forearm, three scythe claws
    from each wrist); right_wing0/right_wing1 and the left (two pairs of smoky iridescent wings folded down his
    back; KING_WINGS has their rest turn)."""
    B = OneSkin(KING, 9300)
    box, col, bar, c = B.box, B.col, B.bar, B.c
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15.0, 0.6], [
        box(0, 15.4, 0.6, 5.6, 2.8, 3.8, "segment", axis="y", r2="venom", band=(0.0, 0.14), seed=10, **KK, gloss=0.8),
        # plates of chitin hanging over his hips, like a warrior's tassets
        *[c(mx([1.2, 11.2, -1.7], [2.8, 4.4, 4.6], sx), [2.8, 4.4, 4.6], "segment", axis="y", r2="venom", band=(0.0, 0.08), seed=11 + (sx > 0), **KK,
            gloss=0.9, pivot=[sx * 2.4, 15.6, 0.6], rot=[0, 0, sx * 12]) for sx in (-1, 1)],
        box(0, 13.4, -1.3, 2.6, 3.6, 0.8, "segment", axis="y", seed=13, **KK, gloss=0.9),
    ])
    # his abdomen: small, three plated rings behind his hips, a sting at its end
    B.bone("gaster", "hips", [0, 15.6, 2.4], [
        bar(0, 15.4, 2.2, 5.6, 4.2, 3.8, "segment", axis="z", r2="venom", band=(0.82, 1.0), seed=14, **KK, gloss=0.9),
        bar(0, 15.2, 5.2, 8.4, 3.6, 3.2, "segment", axis="z", r2="venom", band=(0.82, 1.0), seed=15, **KK, gloss=0.9),
        bar(0, 15.0, 8.0, 10.2, 2.4, 2.2, "segment", axis="z", seed=16, **KK),
        c([-0.4, 14.6, 10.0], [0.8, 0.8, 1.8], "horn", r="claw", tip="+z"),
    ], rot=[-34, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "hips", [sx * 2.4, 14.4, 0.6], [
            col(sx * 2.6, 0.6, 8.0, 14.8, 3.2, 3.6, "segment", axis="y", r2="venom", band=(0.88, 1.0), seed=20 + (sx > 0), **KK, gloss=0.8,
                lines=[dict(pts=[(sx * 3.9, 14.0), (sx * 3.9, 9.4)], w=0.22, faces=("west",) if sx > 0 else ("east",), halo=0.5, bright=0.8)]),
            # a plate over the front of the thigh, a spur off the knee
            c(mx([1.4, 9.0, -1.65], [2.4, 5.2, 0.6], sx), [2.4, 5.2, 0.6], "segment", axis="y", seed=22, **KK, gloss=1.0),
            *limb(B, [sx * 2.6, 8.6, -1.2], [(sx * 2.9, 9.8, -3.4, 0.9)], "horn", r="claw", tip="-z"),
        ], rot=[-14, 0, sx * -3])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.6, 8.2, 0.6], [
            col(sx * 2.6, 0.6, 2.0, 8.4, 2.4, 2.8, "segment", axis="y", r2="venom", band=(0.0, 0.1), seed=24 + (sx > 0), **KK),
            c(mx([1.6, 3.0, -0.95], [2.0, 5.0, 0.5], sx), [2.0, 5.0, 0.5], "segment", axis="y", seed=23, **KK, gloss=1.0),
            # spurs down the back of the shin
            *[cc for k in range(2) for cc in limb(B, [sx * 2.6, 3.6 + k * 2.4, 1.8], [(sx * 2.8, 4.6 + k * 2.4, 3.4, 0.6)], "horn", r="claw",
                                                     tip="+z")],
        ], rot=[24, 0, 0])
        B.bone(f"{side}_foot", f"{side}_shin", [sx * 2.6, 2.0, 0.6], [
            box(sx * 2.6, 1.0, 0.0, 2.6, 2.0, 3.4, "segment", axis="z", seed=26 + (sx > 0), **KK),
            # three talons forward, one back
            *[cc for i in range(3) for cc in limb(B, [sx * 2.6 + (i - 1) * 0.9, 0.7, -1.6], [(sx * 2.6 + (i - 1) * 1.4, 0.0, -4.4, 0.7)], "horn",
                                                   r="claw", tip="-z")],
            *limb(B, [sx * 2.6, 0.8, 1.6], [(sx * 2.6, 0.0, 3.4, 0.6)], "horn", r="claw", tip="+z"),
        ], rot=[-10, 0, 0])
    B.bone("body", "hips", [0, 16.4, 0.6], [
        # the waist: two narrow rings
        col(0, 0.6, 16.0, 18.6, 3.6, 3.0, "segment", axis="y", r2="venom", band=(0.0, 0.2), seed=30, **KK),
        col(0, 0.6, 18.2, 20.8, 4.8, 3.6, "segment", axis="y", r2="venom", band=(0.0, 0.18), seed=31, **KK),
        box(0, 20.6, 0.6, 6.0, 1.4, 4.2, "segment", axis="y", r2="venom", band=(0.0, 0.2), seed=37, **KK, gloss=0.9),
        # the chest: ridged plates, wide at the shoulders, a keel down its middle and seams of venom light between
        box(0, 23.8, 0.6, 8.4, 6.6, 5.0, "segment", axis="y", r2="venom", band=(0.0, 0.08), seed=32, **KK, gloss=0.9,
            lines=[dict(pts=[(-3.6, 22.0), (-0.6, 21.0), (0.6, 21.0), (3.6, 22.0)], w=0.3, faces=("north",), halo=0.6, bright=0.9),
                   dict(pts=[(0.0, 21.0), (0.0, 26.8)], w=0.26, faces=("north",), halo=0.6, bright=0.9)]),
        *[c(mx([0.3, 22.4, -2.4], [3.8, 4.6, 0.8], sx), [3.8, 4.6, 0.8], "segment", axis="y", seed=33 + (sx > 0), **KK, gloss=1.0,
            pivot=[0, 24.8, -1.9], rot=[0, sx * -14, 0]) for sx in (-1, 1)],
        box(0, 27.6, 0.8, 6.0, 1.2, 4.2, "segment", axis="z", seed=35, **KK),
        # the plates of his back, where his wings are rooted
        box(0, 24.0, 3.4, 6.6, 5.6, 0.8, "segment", axis="y", r2="venom", band=(0.0, 0.1), seed=36, **KK, gloss=0.8),
        *[c([-0.3, 21.0 + i * 1.6, 3.6], [0.6, 0.8, 1.2], "horn", r="claw", tip="+z") for i in range(4)],
    ], rot=[6, 0, 0])
    crest = []
    for i, (dx, h, w) in enumerate(((0.0, 7.4, 1.8), (1.7, 5.4, 1.3), (-1.7, 5.4, 1.3), (2.8, 3.6, 0.9), (-2.8, 3.6, 0.9))):
        # the crest: horns swept back off the back of his skull, ridged, the middle one the longest
        sxx = 1 if dx >= 0 else -1
        crest += limb(B, [dx, 32.6 - abs(dx) * 0.3, 2.2], [(dx + sxx * abs(dx) * 0.3, 32.6 + h * 0.42, 2.2 + h * 0.62, w, dict(kr=(0.0, 0.55))),
                                                             (dx + sxx * abs(dx) * 0.5, 32.6 + h * 0.55, 2.2 + h * 1.3, w * 0.55, dict(kr=(0.55, 1.0)))],
                      "horn", r="crest", tip="+z", ridged=3.0)
    B.bone("head", "body", [0, 28.4, 0.4], [
        # a long skull, the face narrowing down to his jaws, the back of it swept up into the crest
        box(0, 30.7, 0.2, 4.6, 3.8, 5.4, "segment", axis="z", r2="venom", band=(0.0, 0.05), seed=40, **KK, gloss=0.9,
            facepaint=[dict(pts=[(-1.6, 32.0), (0.0, 30.4), (1.6, 32.0)], w=0.42, ramp="venom", tone=0.55)]),
        c([-1.9, 30.6, 2.4], [3.8, 2.8, 4.4], "segment", axis="z", seed=46, **KK, gloss=1.0, pivot=[0, 32.0, 2.4], rot=[-26, 0, 0]),
        box(0, 32.7, 0.6, 3.0, 0.9, 5.0, "segment", axis="z", seed=41, **KK, gloss=1.0),
        # the brow: two heavy plates meeting in a V over the eyes
        *[c(mx([0.0, 31.7, -3.0], [2.8, 1.0, 1.4], sx), [2.8, 1.0, 1.4], "segment", axis="z", seed=42, **KK, gloss=1.0, pivot=[0, 31.4, -2.3],
            rot=[0, 0, sx * -16]) for sx in (-1, 1)],
        box(0, 29.0, -2.8, 3.2, 2.8, 1.8, "segment", axis="y", seed=43, **KK, gloss=0.6,
            cuts=[dict(pts=[(-1.0, 28.4), (0.0, 28.0), (1.0, 28.4)], w=0.2, faces=("north",))]),
        box(0, 27.5, -3.1, 2.0, 0.9, 1.2, "segment", axis="y", seed=44, **KK),
        box(0, 27.4, -3.75, 1.2, 0.5, 0.2, "glow"),
        *[c(mx([1.4, 29.4, -3.1], [1.9, 3.0, 3.4], sx), [1.9, 3.0, 3.4], "compound", r="eye", seed=45, facet=0.4,
            pivot=[sx * 2.2, 30.9, -1.6], rot=[0, sx * -18, sx * -22]) for sx in (-1, 1)],
        *crest,
    ], rot=[-4, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # split jaws: two sickles reaching forward and down, crossing at the points
        B.bone(f"{side}_mandible", "head", [sx * 0.9, 28.4, -3.0], [
            c(mx([0.35, 27.5, -5.8], [1.0, 1.0, 3.0], sx), [1.0, 1.0, 3.0], "segment", axis="z", r="chitin", seed=47, hair=0.0, gloss=0.8,
              pivot=[sx * 0.9, 28.0, -3.0], rot=[22, sx * 10, 0]),
            c(mx([0.0, 26.9, -7.6], [0.8, 0.8, 2.0], sx), [0.8, 0.8, 2.0], "horn", r="claw", tip="-z", kr=(0.6, 1.0), pivot=[sx * 0.5, 27.3, -5.6],
              rot=[36, sx * 42, 0]),
        ])
        B.bone(f"{side}_antenna", "head", [sx * 1.0, 31.8, -2.4], limb(B, [sx * 1.0, 31.8, -2.4], [
            (sx * 1.4, 33.6, -3.6, 0.5), (sx * 2.2, 35.4, -2.4, 0.42), (sx * 3.0, 36.2, 0.8, 0.34), (sx * 3.6, 35.6, 4.0, 0.28)],
            "segment", r="chitin", hair=0.0, gloss=0.0))
        B.bone(f"{side}_arm", "body", [sx * 5.0, 26.4, 0.6], [
            col(sx * 5.4, 0.6, 20.2, 26.6, 2.8, 3.0, "segment", axis="y", r2="venom", band=(0.0, 0.1), seed=50 + (sx > 0), **KK,
                lines=[dict(pts=[(sx * 5.4, 25.4), (sx * 5.4, 21.0)], w=0.24, faces=("north",), halo=0.5, bright=0.85)]),
            # the pauldron: plates overlapping down over his shoulder, spikes rising off it
            *[c(mx([3.6 + j * 0.3, 25.6 - j * 1.4, -1.7], [3.8, 1.8, 4.6], sx), [3.8, 1.8, 4.6], "segment", axis="z", r2="venom", band=(0.86, 1.0),
                seed=52 + j, **KK, gloss=0.9, pivot=[sx * 5.0, 27.0, 0.6], rot=[0, 0, sx * -(18 + j * 12)]) for j in range(3)],
            *[cc for j in range(3) for cc in limb(B, [sx * (5.2 + j * 0.7), 27.6 - j * 1.2, -0.6 + j * 1.0],
                                                   [(sx * (6.4 + j * 0.9), 30.8 - j * 1.4, 0.2 + j * 1.3, 0.9 - 0.12 * j)], "horn", r="claw", tip="+y")],
        ], rot=[-4, 0, sx * -8])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.4, 20.4, 0.6], [
            col(sx * 5.4, 0.6, 14.2, 20.6, 2.4, 2.6, "segment", axis="y", r2="venom", band=(0.0, 0.1), seed=56 + (sx > 0), **KK),
            # the blade along the outside of his forearm, from the elbow down past the wrist
            c(mx([6.5, 11.4, -0.4], [0.6, 10.0, 1.8], sx), [0.6, 10.0, 1.8], "segment", axis="y", r="chitin", seed=57, hair=0.0, gloss=1.0,
              paint_faces=[dict(faces=("south",), ramp="edge", tone=0.6, seed=59)], pivot=[sx * 6.5, 21.0, 0.6], rot=[-8, 0, 0]),
            box(sx * 5.4, 13.6, 0.5, 2.6, 1.8, 2.8, "segment", seed=58, **KK, gloss=0.9),
            *[cc for i in range(3) for cc in scythe_claw(B, sx * 5.4 + (i - 1) * 1.05, 13.0, 0.2 - abs(i - 1) * 0.3, (i - 1) * 11,
                                                          length=13.0 if i == 1 else 10.6, w=1.25, d=2.1, curve=40, seed=60 + 2 * i)],
        ], rot=[-16, 0, sx * 4])
        for j, (tilt, spread) in KING_WINGS.items():
            ln, wd, y = (18.0, 5.4, 26.4) if j == 0 else (15.0, 4.4, 24.4)
            B.bone(f"{side}_wing{j}", "body", [sx * 1.4, y, 3.8], [
                c(mx([0.8, y - 0.15, 3.8], [wd, 0.3, ln], sx), [wd, 0.3, ln], "skin", r="wingskin", marks=0.0, seed=70 + 2 * j + (sx > 0),
                  fx=GC.insect_wing, wing=dict(span="z", lead="out", sheen="sheen", tip_dark=0.2), skip=("north", "south", "east", "west")),
            ], rot=[tilt, 0, -sx * spread])

    GC.register(KING, lambda aff: GC.pal("plain", VENOM_LIGHT, KING_RAMPS, tinted=()), density=4.4)
    B.write(3.0, 3.8, 1.5)
    king_anims()


def king_anims():
    """Times match KingBoss and HiveBoss: intro 90 ticks (the cocoon cracks at 0, his wings open at 30, he roars at
    55), phase changes 50, death 100 (held). Moves: claws 26 (cuts landing at 8 and 20), rush 30 (he is through his
    prey at 10), dive 40 (in the air from 4 to 26, landing at 26), spray 34 (acid from 10 to 18), roar 40 (the roar at
    16), devour 50 (a bite at 0, 16 and 32), frenzy 70 (blows landing at 7, 16, 25, 34 and 43)."""
    A = {}
    bones = ("root", "hips", "gaster", "right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot", "body", "head",
             "right_mandible", "left_mandible", "right_antenna", "left_antenna", "right_arm", "right_hand", "left_arm", "left_hand",
             "right_wing0", "right_wing1", "left_wing0", "left_wing1")
    STAND = pose(**{b: [0, 0, 0] for b in bones})

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def jaws(k):
        return dict(right_mandible=[0, 30 * k, 0], left_mandible=[0, -30 * k, 0])

    def wings(k=1.0, lift=0.0, fan=1.0):
        """His wings from folded (k 0) to spread (k 1), lift degrees above level, fan how far out (1: wide)."""
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            for j, (tilt, spread) in KING_WINGS.items():
                tgt = [lift - 6 * j, sx * (74 - 18 * j) * fan, 0]
                rest = [tilt, 0, -sx * spread]
                out[f"{side}_wing{j}"] = [k * (tgt[i] - rest[i]) for i in range(3)]
        return out

    def beat(amp, freq, a=0.0, b=99.0):
        """Wing beats on top of a pose: the tips up and down (only between a and b)."""
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            for j in KING_WINGS:
                out[f"{side}_wing{j}"] = (lambda t, sx=sx, j=j: [0, 0, -sx * amp * math.sin(t * freq - j * 0.6) * window(t, a, a + 0.1) *
                                                                  (1 - window(t, b - 0.1, b))])
        return out

    def arms(r=(0, 0, 0), l=(0, 0, 0), rh=(0, 0, 0), lh=(0, 0, 0)):
        return dict(right_arm=list(r), left_arm=list(l), right_hand=list(rh), left_hand=list(lh))

    CROUCH = dict(hips_pos=[0, -3.2, 0], right_leg=[-40, 0, 0], left_leg=[-40, 0, 0], right_shin=[66, 0, 0], left_shin=[66, 0, 0],
                  right_foot=[-26, 0, 0], left_foot=[-26, 0, 0])
    # on his knees: the thighs straight down (their rest lean undone), the shins flat behind on the ground
    KNEEL_LEGS = dict(hips_pos=[0, -7.0, 0], right_leg=[14, 0, 2], left_leg=[14, 0, -2], right_shin=[66, 0, 0], left_shin=[66, 0, 0],
                      right_foot=[10, 0, 0], left_foot=[10, 0, 0])

    # idle (3 s): breathing, his claws flexing, jaws clicking, feelers searching, a shiver through his wings
    L = 3.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [1.5 * wave(t, L), 0, 0], "head": lambda t: [2 * wave(t, L, 0.3), 8 * wave(t, L * 2, 0.1), 0],
        "right_hand": lambda t: [-4 * max(0.0, wave(t, L, 0.2)) ** 3, 0, 0], "left_hand": lambda t: [-4 * max(0.0, wave(t, L, 0.7)) ** 3, 0, 0],
        "right_mandible": lambda t: [0, 10 * max(0.0, math.sin(t * 2 * math.pi * 2)) ** 6, 0],
        "left_mandible": lambda t: [0, -10 * max(0.0, math.sin(t * 2 * math.pi * 2)) ** 6, 0],
        "right_antenna": lambda t: [10 * wave(t, L / 2, 0.1), 0, 6 * wave(t, L, 0.4)], "left_antenna": lambda t: [10 * wave(t, L / 2, 0.6), 0, -6 * wave(t, L)],
        **{f"{s}_wing{j}": (lambda t, sx=sx, j=j: [0, 0, -sx * 3 * max(0.0, wave(t, L, 0.5 + 0.1 * j)) ** 8]) for s, sx in (("right", -1), ("left", 1))
           for j in KING_WINGS},
        "gaster": lambda t: [2 * wave(t, L, 0.2), 0, 0],
    }), 0.05)

    # walk (1 s): a stalking stride, his claws low, his wings tight
    L = 1.0
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [26 * wave(t, L), 0, 0], "left_leg": lambda t: [-26 * wave(t, L), 0, 0],
        "right_shin": lambda t: [24 * max(0.0, wave(t, L, 0.25)), 0, 0], "left_shin": lambda t: [24 * max(0.0, wave(t, L, 0.75)), 0, 0],
        "right_foot": lambda t: [-10 * wave(t, L, 0.1), 0, 0], "left_foot": lambda t: [10 * wave(t, L, 0.1), 0, 0],
        "right_arm": lambda t: [-14 * wave(t, L), 0, 0], "left_arm": lambda t: [14 * wave(t, L), 0, 0],
        "body": lambda t: [6, 5 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.4 * abs(wave(t, L)), 0],
        "head": lambda t: [-4, -4 * wave(t, L), 0], "gaster": lambda t: [3 * abs(wave(t, L)), 0, 0],
    }), 0.05)

    # dormant (4 s): curled in the cocoon: on his knees, folded over, his arms crossed over his chest, his head down
    L = 4.0
    CURL = P(**KNEEL_LEGS, body=[40, 0, 0], head=[44, 0, 0], gaster=[30, 0, 0],
             **arms(r=(-34, 0, -6), l=(-34, 0, 6), rh=(-24, 0, 0), lh=(-24, 0, 0)), right_antenna=[50, 0, 0], left_antenna=[50, 0, 0])
    A["dormant"] = anim(L, True, timeline([(0, CURL, lin), (L, CURL, lin)], extra={"body": lambda t: [1.0 * wave(t, L / 2), 0, 0]}), 0.1)

    # intro (4.5 s): the cocoon cracks (0) and he uncurls (0.4-1.4), his wings snap open at 1.5 and beat; he spreads
    # his arms, throws his head back and roars at 2.75; he folds his wings and settles by 4.2
    L = 4.5
    UNCURL = P(CROUCH, body=[24, 0, 0], head=[10, 0, 0], **arms(r=(-20, 0, 20), l=(-20, 0, -20), rh=(-30, 0, 0), lh=(-30, 0, 0)), **jaws(0.3))
    SPREAD = P(UNCURL, body=[10, 0, 0], head=[-6, 0, 0], **wings(1.0, 18), **jaws(0.5))
    ROAR = P(body=[-14, 0, 0], head=[-34, 0, 0], **arms(r=(14, 0, 38), l=(14, 0, -38), rh=(-24, 0, 30), lh=(-24, 0, -30)), **wings(1.0, 40),
             **jaws(1.2), right_antenna=[-30, 0, 20], left_antenna=[-30, 0, -20], gaster=[-12, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, CURL, lin), (0.4, CURL, lin), (1.4, UNCURL, ease_in_out), (1.55, SPREAD, ease_out_back),
                                          (2.55, SPREAD, lin), (2.75, ROAR, ease_out_back), (3.6, ROAR, lin), (4.2, STAND, ease_in_out), (L, STAND, lin)],
                                         extra={"body": lambda t: [shake(t, 0.0, 0.4, 2, 47), 0, shake(t, 0.0, 0.4, 2, 39)],
                                                "head": lambda t: [0, 0, shake(t, 2.75, 3.6, 4, 41)],
                                                **beat(18, 22, 1.55, 2.55)}), 0.025)

    # phase2 (2.5 s): The Hunger: he hunches over, claws flexing, and roars low
    L = 2.5
    HUNCH = P(CROUCH, body=[26, 0, 0], head=[16, 0, 0], **arms(r=(-34, 0, 30), l=(-34, 0, -30), rh=(-50, 0, 0), lh=(-50, 0, 0)), **jaws(0.6),
              **wings(0.4, -10, 0.6))
    GROWL = P(HUNCH, body=[10, 0, 0], head=[-14, 0, 0], **jaws(1.2), **wings(0.8, 10))
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.4, HUNCH, ease_in_out), (0.6, HUNCH, lin), (0.8, GROWL, ease_out_back),
                                           (1.8, GROWL, lin), (L, STAND, ease_in_out)],
                                          extra={"head": lambda t: [0, 0, shake(t, 0.8, 1.8, 3, 43)],
                                                 "right_hand": lambda t: [10 * math.sin(t * 18) * window(t, 0.3, 0.5) * (1 - window(t, 1.6, 1.8)), 0, 0],
                                                 "left_hand": lambda t: [10 * math.sin(t * 18 + 1) * window(t, 0.3, 0.5) * (1 - window(t, 1.6, 1.8)), 0, 0]}),
                       0.025)

    # phase3 (2.5 s): The King: his wings flare high, his arms spread, and he roars at the vault
    L = 2.5
    FLARE = P(ROAR, body=[-18, 0, 0], **wings(1.0, 55), **arms(r=(4, 0, 100), l=(4, 0, -100), rh=(0, 0, 36), lh=(0, 0, -36)))
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.35, P(CROUCH, body=[20, 0, 0], **wings(0.3)), ease_in_out),
                                           (0.6, FLARE, ease_out_back), (1.9, FLARE, lin), (L, STAND, ease_in_out)],
                                          extra={"head": lambda t: [0, 0, shake(t, 0.6, 1.9, 4, 39)], **beat(14, 26, 0.6, 1.9)}), 0.025)

    # death (5 s): he staggers back roaring (0-0.6), shudders, drops to his knees (1.6-2.4) with a last cry, and bows
    # over dead where he kneels (3.4), his claws on the ground and his wings drooping
    L = 5.0
    STAGGER = P(body=[-18, 0, 0], head=[-30, 0, 0], **arms(r=(-20, 0, 50), l=(-20, 0, -50)), **jaws(1.2), **wings(0.7, 20))
    KNEEL = P(**KNEEL_LEGS, body=[16, 0, 0], head=[-20, 0, 0], **arms(r=(10, 0, 20), l=(10, 0, -20), rh=(-20, 0, 0), lh=(-20, 0, 0)),
              **jaws(0.9), **wings(0.6, -10, 0.8))
    FALL = P(KNEEL, body=[52, 0, 4], head=[34, 0, 16], **arms(r=(-14, 0, 14), l=(-10, 0, -18), rh=(-8, 0, 0), lh=(-14, 0, 0)),
             **jaws(0.3), **wings(0.55, -42, 0.8), right_antenna=[40, 0, -20], left_antenna=[50, 0, 20], gaster=[20, 0, 0])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.6, STAGGER, ease_out_back), (1.6, STAGGER, lin), (2.4, KNEEL, ease_in),
                                           (2.9, KNEEL, lin), (3.5, FALL, ease_in), (L, FALL, lin)],
                                          extra={"body": lambda t: [shake(t, 0.6, 1.6, 3, 45), 0, shake(t, 0.6, 1.6, 2, 37)],
                                                 "right_hand": lambda t: [shake(t, 3.4, 4.6, 8, 23), 0, 0],
                                                 **beat(6, 30, 3.4, 4.4)}), 0.025)

    def slash(side, t_hit):
        """A cut of one claw landing at t_hit: wound up 0.25 s before, swept across his body."""
        sx = -1 if side == "right" else 1
        wind = P(body=[-4, -sx * 26, 0], **{f"{side}_arm": [-96, 0, -sx * 54], f"{side}_hand": [-34, 0, 0]}, head=[0, -sx * 10, 0], **jaws(0.4))
        cut = P(body=[14, sx * 32, 0], **{f"{side}_arm": [-44, 0, sx * 58], f"{side}_hand": [-10, 0, 0]}, head=[6, sx * 12, 0], **jaws(0.8),
                hips_pos=[0, -0.8, -0.6], **{f"{side}_leg": [-24, 0, 0], f"{side}_shin": [20, 0, 0]})
        return [(t_hit - 0.25, wind, ease_in_out), (t_hit, cut, ease_in)]

    # claws (1.3 s): the right claw (landing at 0.4) and the left (1.0)
    L = 1.3
    A["claws"] = anim(L, False, timeline([(0, STAND, lin), *slash("right", 0.4), *slash("left", 1.0), (L, STAND, ease_in_out)]), 0.025)

    # rush (1.5 s): low and coiled, wings half open (to 0.4), through his prey at 0.5, a skid, and up again
    L = 1.5
    COIL = P(CROUCH, body=[30, 0, 0], head=[-16, 0, 0], **arms(r=(30, 0, 20), l=(30, 0, -20), rh=(-20, 0, 0), lh=(-20, 0, 0)), **wings(0.5, 0, 0.6))
    DASH = P(COIL, body=[48, 0, 0], head=[-30, 0, 0], **arms(r=(60, 0, 14), l=(60, 0, -14)), **wings(0.8, 6, 0.4), hips_pos=[0, -2.0, -1.0], **jaws(0.6))
    A["rush"] = anim(L, False, timeline([(0, STAND, lin), (0.4, COIL, ease_in_out), (0.5, DASH, ease_in), (0.75, DASH, lin),
                                         (1.0, P(CROUCH, body=[14, 0, 0], **arms(r=(-60, 0, 40), l=(-60, 0, -40))), ease_out),
                                         (L, STAND, ease_in_out)]), 0.025)

    # dive (2 s): up on his wings (0.2), beating hard with his legs drawn up and claws raised (to 1.15), and down
    # on his prey at 1.3, claws first; up again by 2.0
    L = 2.0
    AIR = P(right_leg=[16, 0, 6], left_leg=[22, 0, -6], right_shin=[50, 0, 0], left_shin=[40, 0, 0], body=[24, 0, 0], head=[-24, 0, 0],
            **arms(r=(34, 0, 34), l=(34, 0, -34), rh=(-36, 0, 0), lh=(-36, 0, 0)), **wings(1.0, 10), **jaws(0.6))
    STRIKE = P(CROUCH, hips_pos=[0, -4.0, 0], body=[34, 0, 0], head=[16, 0, 0], **arms(r=(-40, 0, 16), l=(-40, 0, -16), rh=(-50, 0, 0), lh=(-50, 0, 0)),
               **wings(1.0, 34), **jaws(1.0))
    A["dive"] = anim(L, False, timeline([(0, STAND, lin), (0.2, P(CROUCH, **wings(1.0, 10)), ease_in_out), (0.35, AIR, ease_out), (1.15, AIR, lin),
                                         (1.3, STRIKE, ease_in), (1.6, STRIKE, lin), (L, STAND, ease_in_out)],
                                        extra={**beat(26, 34, 0.25, 1.2)}), 0.025)

    # spray (1.7 s): he rears back (to 0.45), then thrusts his head forward, jaws wide, and sprays (0.5-0.9)
    L = 1.7
    REAR = P(body=[-14, 0, 0], head=[-30, 0, 0], **jaws(0.6), **arms(r=(-20, 0, 30), l=(-20, 0, -30)), **wings(0.3, 0, 0.5))
    THRUST = P(body=[18, 0, 0], head=[6, 0, 0], **jaws(1.3), **arms(r=(-30, 0, 40), l=(-30, 0, -40), rh=(-30, 0, 0), lh=(-30, 0, 0)),
               **wings(0.5, 10, 0.6))
    A["spray"] = anim(L, False, timeline([(0, STAND, lin), (0.45, REAR, ease_in_out), (0.55, THRUST, ease_in), (0.95, THRUST, lin), (L, STAND, ease_in_out)],
                                         extra={"head": lambda t: [shake(t, 0.55, 0.95, 3, 50), shake(t, 0.55, 0.95, 6, 21), 0]}), 0.025)

    # roar (2 s): gathered low (to 0.7), then he throws himself up, wings flared, arms wide, and roars (0.8-1.6)
    L = 2.0
    GATHER = P(CROUCH, body=[30, 0, 0], head=[24, 0, 0], **arms(r=(-30, 0, -20), l=(-30, 0, 20), rh=(-80, 0, 0), lh=(-80, 0, 0)))
    A["roar"] = anim(L, False, timeline([(0, STAND, lin), (0.7, GATHER, ease_in_out), (0.82, ROAR, ease_out_back), (1.6, ROAR, lin), (L, STAND, ease_in_out)],
                                        extra={"head": lambda t: [0, 0, shake(t, 0.82, 1.6, 4, 44)], "body": lambda t: [shake(t, 0.82, 1.6, 2, 31), 0, 0]}),
                     0.025)

    # devour (2.5 s): three bites (0, 0.8, 1.6), each down on his meal with his claws holding it, jaws snapping shut
    L = 2.5
    GRAB = P(CROUCH, body=[46, 0, 0], head=[30, 0, 0], **arms(r=(-70, 0, 10), l=(-70, 0, -10), rh=(-50, 0, 0), lh=(-50, 0, 0)), **jaws(1.2))
    BITE = P(GRAB, head=[44, 0, 0], body=[52, 0, 0], **jaws(-0.1))
    CHEW = P(GRAB, body=[30, 0, 0], head=[10, 0, 0], **jaws(0.3), **arms(r=(-50, 0, 16), l=(-50, 0, -16), rh=(-70, 0, 0), lh=(-70, 0, 0)))
    steps = [(0, STAND, lin)]
    for b in (0.0, 0.8, 1.6):
        steps += [(b + 0.08, GRAB, ease_in_out), (b + 0.2, BITE, ease_in), (b + 0.5, CHEW, ease_in_out)]
    steps += [(L, STAND, ease_in_out)]
    A["devour"] = anim(L, False, timeline(steps, extra={"head": lambda t: [0, 0, 6 * math.sin(t * 30) * window(t, 0.2, 0.3) * (1 - window(t, 2.0, 2.2))]}),
                       0.025)

    # frenzy (3.5 s): five cuts, right and left by turns, landing at 0.35, 0.8, 1.25, 1.7 and 2.15; then he
    # straightens, breathing hard
    L = 3.5
    steps = [(0, STAND, lin)]
    for i, th in enumerate((0.35, 0.8, 1.25, 1.7, 2.15)):
        steps += slash("right" if i % 2 == 0 else "left", th)
    steps += [(2.6, P(CROUCH, body=[20, 0, 0], **jaws(0.6), **wings(0.4, 0, 0.5)), ease_in_out), (L, STAND, ease_in_out)]
    A["frenzy"] = anim(L, False, timeline([(t0, pz, e) if t0 > 0 else (0, pz, e) for t0, pz, e in steps],
                                          extra={"body": lambda t: [3 * math.sin(t * 9) * window(t, 2.4, 2.6), 0, 0]}), 0.025)
    GC.write_anims(KING, A)


# ============================================================================================= a nest's heart

HEART = "nest_heart"
G.MODEL_SCALE[HEART] = 1.25

HEART_RAMPS = {
    "meat": [(30, 6, 4), (86, 22, 12), (150, 52, 22), (206, 104, 44), (244, 170, 96)],
    "vein": [(20, 4, 4), (50, 12, 8), (90, 26, 14), (140, 56, 30), (190, 100, 60)],
    "chitin": [(5, 3, 2), (20, 12, 7), (46, 28, 15), (100, 68, 36), (196, 156, 98)],
    "stripe": (226, 150, 44),
    "resin": [(14, 7, 2), (46, 24, 6), (98, 56, 14), (160, 100, 32), (220, 166, 84)],
    "spine": [(4, 3, 2), (24, 16, 10), (70, 50, 30), (150, 120, 80), (226, 210, 170)],
}


def heart():
    """A nest's heart, 24 units tall and drawn 1.25 times bigger (2 blocks, its hitbox). Bones: root (a lumpy mound
    of amber resin it grows out of, roots of flesh running out over the ground), heart (the sac: knotted lumps of flesh,
    swollen veins glowing amber, the brood seam down its front), right_ribs/left_ribs (ribs of chitin rising out of the
    mound and curling over it like fingers gripping it, spines along them; one more behind on the right)."""
    B = OneSkin(HEART, 9500)
    box, c = B.box, B.c
    resin = dict(r="resin", mottle=0.34, fat=0.24, blotch=0.6, veins=0.4, marks=0.0)
    roots = []
    for i, a in enumerate(range(0, 360, 45)):
        # roots of flesh running out over the ground from the mound, curling
        r0, r1 = 6.5, 11.5 + 2.5 * (i % 2)
        x0, z0 = math.sin(math.radians(a)) * r0, math.cos(math.radians(a)) * r0
        xm, zm = math.sin(math.radians(a + 8)) * (r0 + r1) / 2, math.cos(math.radians(a + 8)) * (r0 + r1) / 2
        x1, z1 = math.sin(math.radians(a + 20)) * r1, math.cos(math.radians(a + 20)) * r1
        roots += limb(B, [x0, 1.6, z0], [(xm, 1.0, zm, 2.2, dict(seed=20 + i)), (x1, 0.5, z1, 1.4, dict(seed=30 + i))], "skin",
                      r="vein", fx=heart_flesh, veins=0.8, marks=0.0)
    B.bone("root", None, [0, 0, 0], [
        # the mound: lumps of resin heaped round its foot
        box(0, 1.1, 0.4, 15.0, 2.2, 14.0, "skin", seed=10, **resin),
        c([-9.0, 0.0, -5.0], [8.0, 2.8, 7.0], "skin", seed=11, **resin, pivot=[-5.0, 0, -1.5], rot=[0, 24, 0]),
        c([1.6, 0.0, -8.4], [7.0, 3.2, 6.0], "skin", seed=12, **resin, pivot=[5.0, 0, -5.4], rot=[0, -18, 0]),
        c([2.0, 0.0, 2.6], [8.0, 3.6, 7.0], "skin", seed=13, **resin, pivot=[6.0, 0, 6.0], rot=[0, 30, 0]),
        c([-8.6, 0.0, 1.6], [6.0, 2.6, 7.0], "skin", seed=14, **resin, pivot=[-5.6, 0, 5.0], rot=[0, -14, 0]),
        *roots,
    ])
    B.bone("heart", "root", [0, 3.0, 0], [
        # the sac: a body of knotted flesh, rounded, with lumps swelling out of it, the seam down the front one
        box(0, 11.0, 0.2, 12.4, 14.4, 11.4, "skin", r="meat", fx=heart_flesh, seed=40, marks=0.0),
        box(0, 11.6, 0.2, 9.0, 18.0, 8.4, "skin", r="meat", fx=heart_flesh, seed=41, marks=0.0),
        box(0, 9.6, 0.2, 14.6, 8.4, 9.0, "skin", r="meat", fx=heart_flesh, seed=42, marks=0.0),
        box(0, 9.6, 1.0, 9.6, 8.4, 13.0, "skin", r="meat", fx=heart_flesh, seed=43, marks=0.0),
        # the front lump, split by the brood seam
        box(0.4, 11.4, -5.2, 8.0, 12.0, 3.2, "skin", r="meat", fx=seam, seed=44, seam_x=0.4, seam_y=(6.0, 16.8), seam_w=0.75, marks=0.0),
        # lumps swelling out of its sides and top
        c([-8.4, 11.6, -4.0], [5.0, 6.4, 6.0], "skin", r="meat", fx=heart_flesh, seed=45, veins=1.3, marks=0.0, pivot=[-6.0, 14.0, -1.0],
          rot=[10, 0, 14]),
        c([4.6, 5.4, 2.0], [5.6, 6.0, 5.8], "skin", r="meat", fx=heart_flesh, seed=46, veins=1.2, marks=0.0, pivot=[6.0, 8.0, 4.0], rot=[-8, 0, -10]),
        c([1.0, 19.4, -2.6], [5.4, 4.6, 5.6], "skin", r="meat", fx=heart_flesh, seed=47, veins=1.2, marks=0.0, pivot=[3.0, 20.0, 0.0], rot=[0, 0, -16]),
        c([-5.0, 18.6, 0.4], [4.4, 3.8, 4.8], "skin", r="meat", fx=heart_flesh, seed=48, veins=1.0, marks=0.0, pivot=[-3.0, 19.0, 2.0], rot=[0, 0, 20]),
        # a vessel looping over its top
        *limb(B, [-2.6, 20.4, 3.2], [(-0.6, 23.4, 3.6, 1.4, dict(seed=49)), (2.4, 22.6, 3.2, 1.2, dict(seed=50)), (3.6, 19.8, 2.4, 1.0, dict(seed=51))],
              "skin", r="meat", fx=heart_flesh, veins=1.6, marks=0.0),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        ribs = []
        zs = (-3.6, 3.4, 7.6) if sx < 0 else (-2.4, 4.6)
        for k, z in enumerate(zs):
            # each rib: out of the mound, up the sac's side and curling in over its shoulder like a finger, narrowing
            ribs += limb(B, [sx * 8.2, 1.6, z], [(sx * 10.0, 7.0, z * 1.05, 1.8, dict(seed=52 + k)), (sx * 10.0, 13.2, z, 1.5, dict(seed=55 + k)),
                                                   (sx * 7.8, 18.6, z * 0.85, 1.2, dict(seed=58 + k)), (sx * 4.6, 21.0, z * 0.7, 0.9, dict(seed=61 + k)),
                                                   (sx * 2.6, 20.2, z * 0.6, 0.6, dict(seed=64 + k))],
                         "segment", axis="y", r="chitin", r2="stripe", band=(0.0, 0.08), hair=0.0, gloss=0.8)
            ribs += limb(B, [sx * 10.6, 9.0 + k * 1.4, z * 1.03], [(sx * 12.4, 10.4 + k * 1.4, z * 1.03 - 0.5, 0.6)], "horn", r="spine",
                         tip="+x" if sx > 0 else "-x")
        B.bone(f"{side}_ribs", "root", [sx * 8.2, 1.6, 0], ribs)

    GC.register(HEART, lambda aff: GC.pal("plain", AMBER_LIGHT, HEART_RAMPS, tinted=()), density=4.0)
    B.write(1.8, 2.2, 0.0)
    heart_anims()


def heart_anims():
    """NestHeartEntity: "beat" loops on its main controller; "struck" (a blow) and "brood" (its young crawling out)
    are triggered on "act"."""
    A = {}
    REST = pose(heart=[0, 0, 0], right_ribs=[0, 0, 0], left_ribs=[0, 0, 0])

    def P(base=None, **kw):
        return pose(base or REST, **kw)

    # beat (1.2 s): lub-dub, the ribs giving with it (a positive z turn tips a rib's top towards +x: the right ribs, on -x,
    # open with a negative one)
    L = 1.2
    LUB = P(heart_scale=[1.06, 1.05, 1.06], right_ribs=[0, 0, -3], left_ribs=[0, 0, 3])
    DUB = P(heart_scale=[1.035, 1.03, 1.035], right_ribs=[0, 0, -1.5], left_ribs=[0, 0, 1.5])
    A["beat"] = anim(L, True, timeline([(0, REST, lin), (0.1, LUB, ease_out), (0.22, REST, ease_in_out), (0.32, DUB, ease_out), (0.55, REST, ease_in_out),
                                        (L, REST, lin)]), 0.02)
    # struck (0.5 s): squashed by the blow, then quivering
    L = 0.5
    SQUASH = P(heart_scale=[1.08, 0.9, 1.08], right_ribs=[0, 0, -5], left_ribs=[0, 0, 5])
    A["struck"] = anim(L, False, timeline([(0, REST, lin), (0.06, SQUASH, ease_out), (0.2, P(heart_scale=[0.97, 1.04, 0.97]), ease_in_out),
                                           (L, REST, ease_in_out)],
                                          extra={"heart": lambda t: [3 * math.sin(t * 60) * (1 - window(t, 0.1, 0.5)), 0, 3 * math.cos(t * 53) * (1 - window(t, 0.1, 0.5))]}),
                       0.02)
    # brood (1.6 s): it swells and its ribs open (to 0.8), the seam splits as it heaves its young out (0.9), and closes
    L = 1.6
    SWELL = P(heart_scale=[1.14, 1.12, 1.14], right_ribs=[0, 0, -14], left_ribs=[0, 0, 14])
    HEAVE = P(heart_scale=[0.9, 0.94, 0.9], right_ribs=[0, 0, -4], left_ribs=[0, 0, 4])
    A["brood"] = anim(L, False, timeline([(0, REST, lin), (0.8, SWELL, ease_in_out), (0.92, HEAVE, ease_in), (1.2, HEAVE, lin), (L, REST, ease_in_out)],
                                         extra={"heart": lambda t: [2 * math.sin(t * 40) * window(t, 0.4, 0.8) * (1 - window(t, 0.8, 0.9)), 0, 0]}),
                      0.02)
    GC.write_anims(HEART, A)


# ============================================================================================= main

def main():
    want = set(sys.argv[1:])
    makers = {"queen": queen, "king": king, "heart": heart}
    for key, fn in makers.items():
        if not want or key in want:
            print(key)
            fn()


if __name__ == "__main__":
    main()
