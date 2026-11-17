#!/usr/bin/env python3
"""
The Crimson Citadel's assets.

- The lord of the Citadel (tower.CitadelLordEntity): a king taller than a door in black plate lacquered crimson and
  trimmed in old gold. A closed helm with a scowling visor, eyes of crimson fire and a grille over a mouth with fire in
  it; a gold crown of spikes on the helm and two black horns sweeping out of it; a high collar, a collar of black fur and
  a crimson mantle; pauldrons of a crimson dome and two lames, a horn rising from each; a cuirass with a crimson stone in
  a sun of gold over his heart; a tabard sewn with his sigil (a crown over a blade); embers glowing in every joint of
  his armour; a clawed left gauntlet that closes into a fist; and a halberd taller than a man, a crescent blade with a
  burning edge, a spike on top and a beak behind.
  Built with the creature kit (tools/gen_creatures.py: one skin, "main", painted on the model by
  tools/creature_skins.py) at 1.55 times the size it is designed at, so he stands 3.4 blocks tall.
  Animations (their times match the moves in CitadelLordEntity): dormant, intro (eyes at 0.5 s, the halberd's butt
  on the floor at 2 s, the roar at 2.9 s), idle, walk, cleave (hits at 0.8 s), lance (thrust at 1 s), decree (the fist
  closes at 0.4 s), leap (off the floor at 0.5 s, lands at 1.3 s), moon (arm raised at 1 s), death (on one knee, held).
- The items (64x64, painted with tools/hd_paint.py, each with a light layer drawn full-bright): the Key to the Crimson
  Citadel (a key of crimson obsidian bound in dark gold, its bow a crowned gate) and the Sigil of Dominion (a seal of
  black gold with a crimson crown in it, rays of light behind).

Run from the repo root:   python3 tools/gen_tower_assets.py            (everything)
                          python3 tools/gen_tower_assets.py lord       (only the lord)
                          python3 tools/gen_tower_assets.py items      (only the items)
Look at the lord without the game:   python3 tools/preview_model.py citadel_lord_main --anim idle --time 0.5
Needs: pillow, numpy.

Conventions (as the creature kit): 16 units are one block, the front of a model is -z, +x is the model's left. A
positive x rotation tilts a bone's top towards the front; a positive z rotation tilts its top towards +x.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature_skins as CS  # noqa: E402
import gen_creatures as GC  # noqa: E402
import gen_enemy_assets as G  # noqa: E402
import hd_paint as hp  # noqa: E402
from gen_gate_assets import ease_in, ease_in_out, ease_out_back, window  # noqa: E402

anim, wave = G.anim, G.wave

NAME = "citadel_lord"
SCALE = 1.55
G.MODEL_SCALE[NAME] = SCALE
RED = (255, 46, 63)

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "beyondsolo")
ITEM_TEX = os.path.join(ASSETS, "textures", "item")
ITEM_MODELS = os.path.join(ASSETS, "models", "item")


class LordBody(GC.Body):
    """The creature kit's Body with one skin (main) instead of one per affinity."""

    def __init__(self, name, seed):
        self.name = name
        self.uvs, self.size = G.save_textures(name, [("main", "plain")], lambda rng, a: {"aff": a}, seed)
        self.m = G.Model(self.uvs)


# ============================================================================================= paint

def _strokes(a, b, lines, w):
    d = np.full(a.shape, 99.0)
    for pts in lines:
        for (a0, b0), (a1, b1) in zip(pts, pts[1:]):
            dd, _ = CS.seg_dist(a, b, a0, b0, a1, b1)
            d = np.minimum(d, dd)
    return d, d < w / 2


def crown_sigil(f, p, s):
    """The tabard's front: gold braid down its edges and the king's sigil sewn in gold, a crown of three points over a
    blade pointing down, with a crimson stone in the crown and a dark outline round the thread."""
    if f.face != "north":
        return
    a, b = f.X, f.Y
    crown = [[(-1.45, 12.7), (-1.45, 14.3), (-0.72, 13.45), (0.0, 14.9), (0.72, 13.45), (1.45, 14.3), (1.45, 12.7), (-1.45, 12.7)]]
    blade = [[(0.0, 12.1), (0.0, 7.4)], [(-1.05, 11.5), (1.05, 11.5)], [(-0.3, 7.7), (0.0, 6.7), (0.3, 7.7)]]
    d1, m1 = _strokes(a, b, crown, 0.3)
    d2, m2 = _strokes(a, b, blade, 0.36)
    out = (np.minimum(d1, d2) < 0.36) & ~(m1 | m2)
    p.over("cloth_d", 0.2, out)
    p.over("trim", 0.66 + 0.18 * (b - 12.7) / 2.2, m1)
    p.over("trim", 0.58, m2)
    edge = (np.abs(np.abs(a) - 2.0) < 0.17) & (b > 5.5)
    p.over("trim", 0.5, edge)
    CS.line_glow(p, f, [(0.0, 13.55), (0.0, 13.56)], 0.34, halo=0.4, bright=0.9)


def grille(f, p, s):
    """The visor's mouth: four slots cut through it with the fire inside showing at their hearts."""
    if f.face != "north":
        return
    for x in (-1.5, -0.75, 0.75, 1.5):
        d, _ = CS.seg_dist(f.X, f.Y, x, 28.95, x, 30.15)
        p.over("black", 0.06, d < 0.17)
        p.glow("glow", 0.5 + 0.3 * CS.sstep(28.9, 29.6, f.Y), (d < 0.075) & (f.Y < 29.95))


def mantle_sigil(f, p, s):
    """The back of the mantle: braid of gold down its edges and the king's sigil sewn large between the shoulders."""
    if f.face != "south":
        return
    a, b = f.X, f.Y
    crown = [[(-2.2, 20.0), (-2.2, 22.4), (-1.1, 21.2), (0.0, 23.4), (1.1, 21.2), (2.2, 22.4), (2.2, 20.0), (-2.2, 20.0)]]
    blade = [[(0.0, 19.2), (0.0, 11.6)], [(-1.6, 18.4), (1.6, 18.4)], [(-0.45, 12.0), (0.0, 10.6), (0.45, 12.0)]]
    d1, m1 = _strokes(a, b, crown, 0.42)
    d2, m2 = _strokes(a, b, blade, 0.5)
    out = (np.minimum(d1, d2) < 0.5) & ~(m1 | m2)
    p.over("cloth_d", 0.2, out)
    p.over("trim", 0.66 + 0.16 * (b - 20.0) / 3.4, m1)
    p.over("trim", 0.56, m2)
    p.over("trim", 0.5, (np.abs(np.abs(a) - 4.55) < 0.2) & (b > 9.0))
    CS.line_glow(p, f, [(0.0, 21.0), (0.0, 21.01)], 0.5, halo=0.6, bright=0.9)


def blade_rune(f, p, s):
    """A line of light cut into each face of the halberd's blade, following its edge."""
    if f.face not in ("west", "east"):
        return
    z0 = s["z0"]
    CS.line_glow(p, f, [(z0 - 1.3, 29.0), (z0 - 2.5, 28.3), (z0 - 3.2, 27.1), (z0 - 3.1, 25.8), (z0 - 2.4, 25.0)], 0.2, halo=0.5, bright=0.8)


# ============================================================================================= the lord

def citadel_lord():
    B = LordBody(NAME, 4100)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    pl = dict(r="plate", engrave="trim", embers=1.0)    # the armour's main pieces: fire showing through their joints
    lp = dict(r="plate", engrave="trim")                # plates laid over them
    lq = dict(r="lacquer", engrave="trim")              # crimson-lacquered plates
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15.0, 0], [
        # the fauld, two lames over the hips; a studded belt, its buckle of gold set with a crimson stone
        box(0, 16.4, -0.1, 9.0, 1.4, 5.8, "plate", **lp, seed=301, rivets=False),
        box(0, 15.2, -0.1, 9.6, 1.4, 6.2, "plate", **lp, seed=302, rivets=False),
        box(0, 17.7, -0.1, 8.2, 1.3, 5.4, "leather", studs=True, stitch=False, seed=303),
        box(0, 17.7, -2.95, 2.0, 1.9, 0.45, "trim"),
        box(0, 17.7, -3.25, 0.8, 0.8, 0.3, "glow", pivot=[0, 17.7, -3.25], rot=[0, 0, 45]),
    ])
    # the tabard, hanging from under the belt in front of the legs
    B.bone("tabard", "hips", [0, 16.9, -3.4], [
        c([-2.3, 3.6, -3.65], [4.6, 13.3, 0.4], "cloth", r="cloth", hem=3.0, folds=2.0, seed=304, skip=("up", "down"), fx=crown_sigil),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "hips", [sx * 2.6, 15.0, 0], [
            col(sx * 2.6, -0.1, 9.0, 15.4, 3.6, 3.8, "plate", **pl, seed=310 + sx),
            # a tasset over the thigh, flared at its foot
            c(mx([0.9, 10.8, -2.6], [3.8, 4.4, 0.6], sx), [3.8, 4.4, 0.6], "plate", **lq, seed=312 + sx, rivets=False,
              pivot=[sx * 2.8, 15.2, -2.3], rot=[-9, 0, sx * -3]),
            # the poleyn: a knee cop with a horn standing out of it
            box(sx * 2.6, 8.9, -1.5, 3.4, 2.4, 1.8, "plate", **lp, seed=314 + sx, rivets=False),
            *GC.limb(B, [sx * 2.6, 8.7, -2.2], [(sx * 2.6, 9.7, -3.7, 0.8, dict(kr=(0.0, 0.6))), (sx * 2.6, 10.9, -4.4, 0.45, dict(kr=(0.6, 1.0)))],
                     "horn", r="horn", tip="+y"),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.6, 8.8, 0], [
            col(sx * 2.6, -0.1, 1.4, 8.8, 3.3, 3.5, "plate", **pl, seed=316 + sx),
            box(sx * 2.6, 5.0, -1.95, 0.9, 6.4, 0.5, "plate", r="plate", seed=318 + sx, rivets=False),
            # the sabaton: lames over the foot, a pointed toe
            box(sx * 2.6, 0.8, -0.8, 3.8, 1.6, 5.6, "plate", **lp, seed=320 + sx, rivets=False),
            box(sx * 2.6, 0.6, -4.0, 3.0, 1.2, 1.2, "plate", r="plate", seed=322 + sx, rivets=False),
            box(sx * 2.6, 0.45, -4.9, 1.8, 0.9, 0.8, "plate", r="plate", seed=324 + sx, rivets=False),
        ])
    B.bone("body", "hips", [0, 17.6, 0], [
        box(0, 19.8, 0.0, 7.6, 4.0, 4.8, "plate", **pl, seed=330),
        box(0, 20.1, -0.1, 8.2, 1.2, 5.3, "plate", **lp, seed=331, rivets=False),
        box(0, 21.2, -0.1, 8.8, 1.2, 5.6, "plate", **lp, seed=332, rivets=False),
        box(0, 24.8, -0.1, 10.2, 6.4, 6.4, "plate", **pl, seed=333),
        # the cuirass: two crimson plates over the chest turned like a breast, a ridge down the middle, and over the
        # heart a crimson stone in a sun of gold
        *[c(mx([0.15, 22.4, -3.8], [4.9, 5.4, 0.7], sx), [4.9, 5.4, 0.7], "plate", **lq, seed=334 + sx, rivets=False,
            pivot=[0, 25.2, -3.4], rot=[0, -sx * 10, 0]) for sx in (-1, 1)],
        box(0, 24.8, -4.05, 1.0, 5.8, 0.6, "plate", r="plate", seed=336, rivets=False),
        box(0, 25.8, -4.3, 2.5, 2.5, 0.4, "trim", pivot=[0, 25.8, -4.3], rot=[0, 0, 45]),
        box(0, 25.8, -4.55, 1.3, 1.3, 0.4, "glow", pivot=[0, 25.8, -4.55], rot=[0, 0, 45]),
        # the gorget; a high collar standing up behind the head, flaring at its sides
        box(0, 28.4, -0.2, 6.8, 1.6, 6.0, "plate", **lp, seed=337, rivets=False),
        c([-3.0, 27.6, 3.3], [6.0, 3.6, 0.7], "plate", **lq, seed=338, rivets=False, pivot=[0, 27.6, 3.65], rot=[-24, 0, 0]),
        *[c(mx([3.3, 27.6, -0.6], [0.7, 3.4, 3.8], sx), [0.7, 3.4, 3.8], "plate", **lq, seed=339 + sx, rivets=False,
            pivot=[sx * 3.6, 27.6, 1.0], rot=[0, 0, sx * 16]) for sx in (-1, 1)],
        # a collar of black fur round the shoulders and the back of the neck
        box(0, 28.4, 2.0, 12.4, 2.6, 4.2, "mane", r="fur", seed=341, len=1.8, width=0.7, ragged=0.6),
    ])
    # the mantle: a crimson panel down the back and a side panel either side wrapping forward, black inside, a gold
    # band with a thread of light in it above the torn hem
    inside = dict(ramp="cloth_in", tone=0.36)
    B.bone("mantle", "body", [0, 28.2, 3.6], [
        c([-5.0, 2.2, 3.3], [10.0, 26.0, 0.8], "cloth", r="cloth", hem=4.2, folds=1.0, seed=342, skip=("up",), band=(7.6, 8.7),
          paint_faces=[dict(faces=("north",), **inside)], fx=mantle_sigil),
        *[c(mx([4.6, 3.0, 3.3], [3.4, 25.2, 0.8], sx), [3.4, 25.2, 0.8], "cloth", r="cloth", hem=4.0, folds=1.2, seed=343 + sx,
            skip=("up",), band=(8.3, 9.3), paint_faces=[dict(faces=("north",), **inside)],
            pivot=[sx * 4.9, 28.2, 3.7], rot=[0, sx * 28, sx * 4]) for sx in (-1, 1)],
    ])
    B.bone("head", "body", [0, 28.6, -0.2])       # the model turns this bone to look; the helm under it is animated
    helm = [
        # the shell, and a cap of crimson inside the crown with arches of gold over it and an orb on top
        box(0, 31.1, 0.2, 5.6, 5.0, 5.6, "plate", **pl, seed=350, rivets=False),
        box(0, 34.0, 0.25, 5.0, 0.8, 5.0, "cloth", r="cloth", folds=2.6, seed=351),
        box(0, 34.65, 0.25, 3.8, 0.5, 3.8, "cloth", r="cloth", folds=2.6, seed=359),
        box(0, 35.05, 0.25, 0.45, 0.3, 4.0, "trim"),
        box(0, 35.05, 0.25, 4.0, 0.3, 0.45, "trim"),
        *[box(sx * 2.15, 34.3, 0.25, 0.4, 1.2, 0.45, "trim") for sx in (-1, 1)],
        *[box(0, 34.3, 0.25 + sz * 2.15, 0.45, 1.2, 0.4, "trim") for sz in (-1, 1)],
        box(0, 35.6, 0.25, 0.95, 0.95, 0.95, "trim", pivot=[0, 35.6, 0.25], rot=[0, 45, 0]),
        box(0, 36.3, 0.25, 0.3, 0.8, 0.3, "trim"),
        box(0, 35.65, -0.45, 0.42, 0.42, 0.2, "glow", pivot=[0, 35.65, -0.45], rot=[0, 0, 45]),
        # the visor: a face plate with a grille over a mouth of fire, a ridge down it, brows in a scowl over eyes of
        # crimson fire, cheek guards flaring, a pointed chin
        box(0, 30.2, -2.95, 5.0, 3.0, 0.7, "plate", **lp, seed=352, rivets=False, fx=grille),
        box(0, 30.5, -3.45, 0.7, 2.8, 0.4, "plate", r="plate", seed=353, rivets=False),
        *[c([min(0.0, sx * 2.9), 31.85, -3.65], [2.9, 0.9, 1.0], "plate", **lq, seed=354 + sx, rivets=False,
            pivot=[sx * 0.2, 32.3, -3.15], rot=[0, 0, -sx * 16]) for sx in (-1, 1)],
        *[c([min(sx * 0.45, sx * 2.05), 31.25, -3.5], [1.6, 0.42, 0.2], "glow",
            pivot=[sx * 0.45, 31.45, -3.4], rot=[0, 0, -sx * 16]) for sx in (-1, 1)],
        *[c(mx([2.6, 28.8, -2.9], [0.6, 2.8, 3.0], sx), [0.6, 2.8, 3.0], "plate", **lp, seed=356 + sx, rivets=False,
            pivot=[sx * 2.9, 30.2, -1.4], rot=[0, sx * 14, 0]) for sx in (-1, 1)],
        c([-1.6, 28.2, -3.5], [3.2, 1.0, 0.9], "plate", r="plate", seed=358, rivets=False, pivot=[0, 28.7, -3.05], rot=[-12, 0, 0]),
        # the crown: a circlet of old gold round the top of the helm, five spikes rising from it, a stone in the
        # front ones
        box(0, 33.45, -2.75, 6.0, 0.9, 0.4, "trim"),
        box(0, 33.45, 3.15, 6.0, 0.9, 0.4, "trim"),
        *[box(sx * 3.0, 33.45, 0.2, 0.4, 0.9, 6.3, "trim") for sx in (-1, 1)],
        box(0, 35.3, -2.75, 0.9, 2.8, 0.4, "trim"),
        box(0, 37.1, -2.75, 0.5, 0.9, 0.36, "trim"),
        *[box(sx * 1.8, 34.6, -2.75, 0.7, 1.4, 0.4, "trim") for sx in (-1, 1)],
        *[box(sx * 1.8, 35.55, -2.75, 0.4, 0.6, 0.36, "trim") for sx in (-1, 1)],
        *[box(sx * 1.6, 34.4, 3.15, 0.6, 1.0, 0.4, "trim") for sx in (-1, 1)],
        box(0, 34.25, -3.0, 0.62, 0.62, 0.2, "glow", pivot=[0, 34.25, -3.0], rot=[0, 0, 45]),
        *[box(sx * 1.8, 34.15, -3.0, 0.45, 0.45, 0.2, "glow", pivot=[sx * 1.8, 34.15, -3.0], rot=[0, 0, 45]) for sx in (-1, 1)],
    ]
    # two black horns sweeping out of the helm, up, and in at their tips
    for sx in (-1, 1):
        helm += GC.curl(B, [sx * 2.5, 32.2, 0.7], sx, [(80, 1.4, 1.5), (60, 1.6, 1.35), (36, 1.7, 1.2), (12, 1.7, 1.0), (-14, 1.5, 0.8), (-34, 1.2, 0.6)],
                        "horn", z_step=0.4, overlap=0.3, r="horn", tip="+y", ridged=5)
    B.bone("helm", "head", [0, 28.6, -0.2], helm)
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 5.9, 27.4, 0]
        B.bone(f"{side}_arm", "body", [sx * 5.9, 26.8, 0], [
            col(sx * 6.7, 0, 21.4, 26.6, 3.2, 3.4, "plate", **pl, seed=360 + sx),
            # the pauldron: a stepped crimson dome rimmed in gold over two lames, a horn standing up out of it
            c(mx([4.0, 25.6, -3.7], [5.8, 1.8, 7.4], sx), [5.8, 1.8, 7.4], "plate", **lq, seed=362 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 16]),
            c(mx([4.4, 27.3, -3.1], [4.8, 0.9, 6.2], sx), [4.8, 0.9, 6.2], "plate", **lq, seed=386 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 16]),
            c(mx([5.2, 28.1, -2.0], [3.0, 0.6, 4.0], sx), [3.0, 0.6, 4.0], "plate", **lq, seed=388 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 16]),
            c(mx([3.9, 25.35, -3.8], [6.0, 0.45, 7.6], sx), [6.0, 0.45, 7.6], "trim", pivot=piv, rot=[0, 0, sx * 16]),
            c(mx([5.2, 24.6, -3.4], [4.6, 1.7, 6.8], sx), [4.6, 1.7, 6.8], "plate", **lp, seed=364 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 24]),
            c(mx([5.9, 23.2, -3.1], [3.9, 1.6, 6.2], sx), [3.9, 1.6, 6.2], "plate", **lp, seed=366 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 32]),
            *GC.limb(B, [sx * 7.4, 28.2, -0.4], [(sx * 8.4, 30.6, -0.7, 1.0, dict(kr=(0.0, 0.55))), (sx * 8.9, 32.4, -0.8, 0.55, dict(kr=(0.55, 1.0)))],
                     "horn", r="horn", tip="+y"),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 6.7, 21.6, 0], [
            # the couter with a fan plate on its outside, the vambrace, the gauntlet's flared cuff, the hand, its knuckles
            box(sx * 6.7, 21.3, 0.3, 3.6, 2.0, 3.8, "plate", **lp, seed=368 + sx, rivets=False),
            c(mx([8.4, 20.0, -0.9], [0.5, 2.8, 2.8], sx), [0.5, 2.8, 2.8], "plate", **lq, seed=370 + sx, rivets=False,
              pivot=[sx * 8.6, 21.3, 0.4], rot=[0, 0, sx * 12]),
            col(sx * 6.7, 0, 16.6, 21.2, 3.0, 3.2, "plate", **pl, seed=372 + sx),
            box(sx * 6.7, 17.1, -0.1, 3.7, 1.5, 3.9, "plate", **lq, seed=374 + sx, rivets=False),
            box(sx * 6.7, 15.0, -0.3, 2.8, 2.6, 3.2, "plate", **lp, seed=376 + sx, rivets=False),
            box(sx * 6.7, 14.5, -2.05, 2.9, 1.3, 0.6, "plate", r="plate", seed=378 + sx, rivets=False),
        ])
    # the left hand's fingers, clawed, hanging from the palm (they close into a fist)
    fingers = []
    for i, (z, ln) in enumerate(((-1.45, 2.1), (-0.65, 2.4), (0.15, 2.2), (0.95, 1.8))):
        fingers += GC.limb(B, [6.75, 13.9, z], [(6.6, 13.9 - ln, z - 0.15, 0.62, dict(m="plate", r="plate", rivets=False, engrave=None, seed=380 + i)),
                                                 (6.35, 13.9 - ln - 1.2, z - 0.4, 0.36, dict(m="horn", r="horn", tip="+y"))], "plate")
    fingers += GC.limb(B, [6.0, 14.6, -1.5], [(5.5, 13.3, -2.0, 0.6, dict(m="plate", r="plate", rivets=False, seed=385)),
                                              (5.3, 12.3, -2.2, 0.36, dict(m="horn", r="horn", tip="+y"))], "plate")
    B.bone("left_fingers", "left_hand", [6.7, 13.9, -0.4], fingers)
    # the halberd, in the right fist, upright at rest
    hx, hz = -6.7, -0.3
    bz = hz - 0.6
    B.bone("halberd", "right_hand", [hx, 15.0, hz], [
        col(hx, hz, -0.4, 30.4, 0.9, 0.9, "metal", r="haft", long="y", fuller=False, seed=390),
        col(hx, hz, 11.6, 18.4, 1.1, 1.1, "leather", r="grip", stitch=False, seed=391),
        *[col(hx, hz, y, y + 0.5, 1.25, 1.25, "trim") for y in (11.2, 18.4, 4.0, 22.4)],
        col(hx, hz, -0.9, -0.2, 1.3, 1.3, "trim"),
        *GC.limb(B, [hx, -0.8, hz], [(hx, -2.5, hz, 0.6)], "metal", r="blade", long="y", fuller=False),
        col(hx, hz, 24.4, 30.8, 1.35, 1.35, "plate", r="plate", rivets=False, seed=392),
        col(hx, hz, 24.0, 24.5, 1.5, 1.5, "trim"),
        # the blade: a crescent ahead of the haft, its edge burning, a line of light cut into it
        c([hx - 0.22, 24.8, bz - 4.2], [0.44, 5.6, 4.2], "metal", r="blade", long="y", edge="front", fuller=False, seed=393,
          fx=blade_rune, z0=bz),
        c([hx - 0.22, 29.4, bz - 4.0], [0.44, 2.6, 1.8], "metal", r="blade", long="y", edge="front", fuller=False, seed=394,
          pivot=[hx, 29.4, bz - 3.1], rot=[-28, 0, 0]),
        c([hx - 0.22, 22.0, bz - 4.0], [0.44, 3.2, 1.8], "metal", r="blade", long="y", edge="front", fuller=False, seed=395,
          pivot=[hx, 25.2, bz - 3.1], rot=[30, 0, 0]),
        *GC.limb(B, [hx, 31.8, bz - 3.0], [(hx, 29.6, bz - 4.5, 0.5), (hx, 26.6, bz - 4.75, 0.5), (hx, 23.4, bz - 4.5, 0.5), (hx, 21.3, bz - 3.0, 0.5)],
                 "glow"),
        # the spike on top and the beak behind
        *GC.limb(B, [hx, 30.6, hz], [(hx, 33.6, hz, 0.95), (hx, 36.0, hz, 0.5)], "metal", r="blade", long="y", fuller=False),
        *GC.limb(B, [hx, 28.0, hz + 0.6], [(hx, 29.0, hz + 2.4, 0.7), (hx, 28.2, hz + 3.8, 0.4)], "metal", r="blade", fuller=False),
    ])
    GC.register(NAME, lambda aff: GC.pal(aff, RED, {
        "plate": [(3, 2, 4), (16, 10, 14), (40, 28, 32), (94, 70, 72), (172, 146, 140)],
        "lacquer": [(12, 2, 5), (44, 6, 14), (96, 14, 26), (156, 36, 44), (214, 98, 92)],
        "trim": [(26, 12, 4), (80, 46, 16), (146, 100, 42), (204, 160, 82), (246, 222, 154)],
        "cloth": (110, 14, 26), "cloth_d": (44, 4, 10), "cloth_l": (164, 52, 56),
        "cloth_in": [(4, 1, 2), (14, 4, 6), (30, 8, 12), (52, 16, 20), (80, 30, 32)],
        "fur": [(2, 1, 2), (9, 6, 8), (22, 16, 18), (46, 36, 38), (82, 68, 68)],
        "horn": [(2, 1, 2), (12, 7, 9), (34, 22, 24), (84, 58, 56), (164, 128, 116)],
        "blade": [(6, 2, 4), (28, 14, 18), (74, 50, 56), (146, 118, 122), (224, 208, 206)],
        "haft": [(3, 2, 3), (14, 10, 12), (32, 24, 26), (62, 50, 50), (110, 92, 86)],
        "grip": [(8, 3, 4), (30, 10, 12), (62, 22, 24), (104, 48, 46), (150, 92, 84)],
        "leather": (50, 30, 26),
    }, tinted=()), density=4.4)
    B.write(3.6, 3.2, 2.0)
    write_lord_anims()


# ============================================================================================= the lord's moves

STAND = {
    "hips": [0, 0, 0], "body": [3, 0, 0], "helm": [0, 0, 0], "mantle": [5, 0, 0], "tabard": [-3, 0, 0],
    "right_arm": [-8, 0, 4], "right_hand": [-36, 0, 0], "left_arm": [-4, 0, -8], "left_hand": [-18, 0, 0], "left_fingers": [0, 0, 18],
    "right_leg": [-3, 0, 2], "right_shin": [4, 0, 0], "left_leg": [3, 0, -2], "left_shin": [4, 0, 0], "hips_pos": [0, 0, 0],
}


def P(base=None, pitch=84, spin=0.0, roll=0.0, **kw):
    """A pose: rotations by bone (and hips_pos, where the hips are); pitch is the halberd's angle with the floor ahead
    (90 upright, 0 level and pointing ahead, negative pointing down), worked out back through the arm."""
    p = {k: list(v) for k, v in (base or STAND).items() if k != "halberd"}
    for k, v in kw.items():
        p[k] = list(v)
    chain = p["hips"][0] + p["body"][0] + p["right_arm"][0] + p["right_hand"][0]
    p["halberd"] = [90 - pitch - chain, spin, roll]
    return p


def timeline(steps, extra=None):
    """steps: [(time, pose, ease into it)] -> GeckoLib bone channels; extra: {bone: fn(t) -> [x, y, z]} added on top."""
    names = sorted({b for _, p, _ in steps for b in p})

    def at(key, t):
        v = steps[0][1].get(key, [0, 0, 0])
        for (t0, p0, _), (t1, p1, ease) in zip(steps, steps[1:]):
            if t >= t0:
                k = ease(window(t, t0, t1))
                a, b = p0.get(key, [0, 0, 0]), p1.get(key, [0, 0, 0])
                v = [a[i] + (b[i] - a[i]) * k for i in range(3)]
        return v

    out = {}
    for n in names:
        add = (extra or {}).get(n)
        if n == "hips_pos":
            out.setdefault("hips", {})["position"] = (lambda t, add=add: [x + y for x, y in zip(at("hips_pos", t), add(t))]) if add else (lambda t: at("hips_pos", t))
            continue
        fn = (lambda t, n=n, add=add: [x + y for x, y in zip(at(n, t), add(t))]) if add else (lambda t, n=n: at(n, t))
        out.setdefault(n, {})["rotation"] = fn
    return out


def lin(t):
    return max(0.0, min(1.0, t))


def write_lord_anims():
    A = {}
    stand = P()

    # idle (3 s): breathing, the mantle stirring, the claws flexing
    L = 3.0
    A["idle"] = anim(L, True, timeline([(0, stand, lin), (L, stand, lin)], extra={
        "body": lambda t: [1.2 * wave(t, L), 0, 0],
        "helm": lambda t: [-0.8 * wave(t, L), 0, 0],
        "mantle": lambda t: [1.5 * wave(t, L, 0.3), 0, 0.6 * wave(t, L * 2)],
        "left_fingers": lambda t: [0, 0, 8 * max(0.0, wave(t, L, 0.6))],
        "right_arm": lambda t: [0.8 * wave(t, L), 0, 0],
        "hips_pos": lambda t: [0, -0.25 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0],
    }), 0.1)

    # walk (1.4 s): heavy strides, the halberd carried slanting forward, the mantle swinging
    L = 1.4
    carry = P(pitch=62, right_arm=[-14, 0, 6], right_hand=[-50, 0, 0], body=[6, 0, 0])
    A["walk"] = anim(L, True, timeline([(0, carry, lin), (L, carry, lin)], extra={
        "right_leg": lambda t: [-24 * wave(t, L) + 3, 0, -2], "left_leg": lambda t: [24 * wave(t, L) - 3, 0, 2],
        "right_shin": lambda t: [26 * max(0.0, wave(t, L, 0.25)) - 4, 0, 0], "left_shin": lambda t: [26 * max(0.0, wave(t, L, 0.75)) - 4, 0, 0],
        "body": lambda t: [0, 4 * wave(t, L), 0],
        "left_arm": lambda t: [-16 * wave(t, L), 0, 0],
        "right_arm": lambda t: [4 * wave(t, L), 0, 0],
        "mantle": lambda t: [6 + 5 * abs(wave(t, L)), 0, 2 * wave(t, L)],
        "tabard": lambda t: [-10 * abs(wave(t, L)), 0, 0],
        "hips_pos": lambda t: [0, -0.8 * abs(wave(t, L)), 0],
    }), 0.05)

    # dormant (4 s): bowed over the halberd planted before him, both hands on it, breathing slow
    DORMANT = P(pitch=96, roll=40, body=[10, 0, 0], helm=[28, 0, 0], right_arm=[-30, 0, -42], right_hand=[-36, 0, 0],
                left_arm=[-36, 0, 40], left_hand=[-44, -20, 0], left_fingers=[0, 0, 60], mantle=[-4, 0, 0], tabard=[-8, 0, 0],
                right_leg=[0, 0, 4], left_leg=[0, 0, -4], right_shin=[0, 0, 0], left_shin=[0, 0, 0])
    L = 4.0
    A["dormant"] = anim(L, True, timeline([(0, DORMANT, lin), (L, DORMANT, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0],
        "mantle": lambda t: [0.8 * wave(t, L, 0.3), 0, 0],
        "hips_pos": lambda t: [0, -0.15 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0],
    }), 0.1)

    # intro (4 s): the eyes light (0.5 s), he lifts his head and the halberd, drives its butt into the floor (2.0 s),
    # wrenches it round and roars with his arms flung wide (2.9 s), then settles to fight
    L = 4.0
    WAKE = P(DORMANT, pitch=90, helm=[12, 0, 0], body=[6, 0, 0])
    LIFT = P(pitch=90, right_arm=[-122, 0, 8], right_hand=[-24, 0, 0], left_arm=[-8, 0, -14], left_hand=[-20, 0, 0],
             left_fingers=[0, 0, 24], helm=[-6, 0, 0], body=[-5, 0, 0], mantle=[8, 0, 0])
    LIFT2 = P(LIFT, pitch=90, right_arm=[-134, 0, 8])
    SLAM = P(pitch=90, right_arm=[-30, 0, -4], right_hand=[-40, 0, 0], body=[14, 0, 0], helm=[10, 0, 0], left_arm=[-20, 0, -18],
             left_fingers=[0, 0, 50], mantle=[-6, 0, 0], hips_pos=[0, -1.3, 0])
    ROAR = P(pitch=64, body=[-14, 0, 0], helm=[-30, 0, 0], right_arm=[-56, 0, 48], right_hand=[-12, 0, 0], left_arm=[-48, 0, -68],
             left_hand=[-10, 0, 0], left_fingers=[0, 0, -16], mantle=[26, 0, 0], tabard=[6, 0, 0],
             right_leg=[-6, 0, 6], left_leg=[6, 0, -6])
    A["intro"] = anim(L, False, timeline([
        (0.0, DORMANT, lin), (0.5, DORMANT, lin), (0.9, WAKE, ease_in_out), (1.5, LIFT, ease_in_out), (1.82, LIFT2, ease_in_out),
        (2.0, SLAM, ease_in), (2.4, SLAM, lin), (2.9, ROAR, ease_out_back), (3.35, ROAR, lin), (4.0, stand, ease_in_out),
    ], extra={
        "helm": lambda t: [0, 0, 5 * math.sin(t * 40) * window(t, 2.9, 3.0) * (1 - window(t, 3.0, 3.35))],
        "body": lambda t: [0.8 * math.sin(t * 50) * window(t, 2.9, 2.95) * (1 - window(t, 2.95, 3.35)), 0, 0],
    }), 0.05)

    # cleave (1.5 s): the halberd drawn back over the right shoulder, swept down and across, landing at 0.8 s
    L = 1.5
    WIND = P(pitch=150, body=[-6, 38, 0], right_arm=[-150, 30, 20], right_hand=[-30, 0, 0], left_arm=[-60, 0, 30],
             left_hand=[-30, 0, 0], left_fingers=[0, 0, 40], right_leg=[10, 0, 4], left_leg=[-20, 0, -4], left_shin=[16, 0, 0],
             mantle=[8, 0, 0], hips_pos=[0, -0.6, 0])
    CUT = P(pitch=-20, spin=-10, body=[14, -42, 0], right_arm=[-70, -50, -10], right_hand=[-10, 0, 0], left_arm=[-30, 0, -24],
            left_hand=[-10, 0, 0], left_fingers=[0, 0, 10], left_leg=[-34, 0, -4], left_shin=[30, 0, 0], right_leg=[18, 0, 4],
            right_shin=[8, 0, 0], mantle=[-4, 0, 0], tabard=[-10, 0, 0], hips_pos=[0, -1.4, -0.6])
    FOLLOW = P(CUT, pitch=-40, spin=-14, body=[18, -50, 0], right_arm=[-40, -60, -20])
    A["cleave"] = anim(L, False, timeline([(0, stand, lin), (0.55, WIND, ease_in_out), (0.8, CUT, ease_in), (1.0, FOLLOW, ease_out_back),
                                           (1.15, FOLLOW, lin), (1.5, stand, ease_in_out)]), 0.025)

    # lance (2 s): drawn back level at the hip, thrust forward in a lunge at 1.0 s, held, recovered
    L = 2.0
    DRAW = P(pitch=0, body=[-4, 30, 0], right_arm=[-50, 10, 10], right_hand=[-60, 0, 0], left_arm=[-70, -20, 20],
             left_hand=[-20, 0, 0], left_fingers=[0, 0, 40], right_leg=[14, 0, 4], right_shin=[10, 0, 0], left_leg=[-18, 0, -4],
             left_shin=[20, 0, 0], hips_pos=[0, -1.0, 0.6], mantle=[6, 0, 0])
    THRUST = P(pitch=0, body=[16, -12, 0], right_arm=[-92, -6, 0], right_hand=[-4, 0, 0], left_arm=[26, 0, -30], left_hand=[-10, 0, 0],
               left_fingers=[0, 0, -10], left_leg=[-46, 0, -4], left_shin=[36, 0, 0], right_leg=[30, 0, 4], right_shin=[12, 0, 0],
               mantle=[22, 0, 0], tabard=[-14, 0, 0], hips_pos=[0, -2.0, -1.6])
    A["lance"] = anim(L, False, timeline([(0, stand, lin), (0.75, DRAW, ease_in_out), (1.0, THRUST, ease_in), (1.4, THRUST, lin),
                                          (2.0, stand, ease_in_out)]), 0.025)

    # decree (1.5 s): the left hand raised high, open, and closed into a fist at 0.4 s; the floor marks answer it
    L = 1.5
    RAISE = P(left_arm=[-165, 0, -16], left_hand=[-10, 0, 0], left_fingers=[0, 0, -22], body=[-6, -10, 0], helm=[-14, 0, 0], mantle=[8, 0, 0])
    FIST = P(RAISE, left_arm=[-172, 0, -8], left_fingers=[0, 0, 82], body=[-8, -10, 0], helm=[-16, 0, 0])
    A["decree"] = anim(L, False, timeline([(0, stand, lin), (0.3, RAISE, ease_in_out), (0.4, FIST, ease_in), (1.05, FIST, lin),
                                           (1.5, stand, ease_in_out)], extra={
        "left_arm": lambda t: [1.6 * math.sin(t * 60) * window(t, 0.4, 0.5) * (1 - window(t, 0.8, 1.05)), 0, 0],
    }), 0.025)

    # leap (2.2 s): a deep crouch, off the floor at 0.5 s with the halberd raised over his head, down onto the mark
    # with it at 1.3 s, a crouch on landing
    L = 2.2
    CROUCH = P(pitch=20, body=[26, 0, 0], helm=[-10, 0, 0], right_leg=[-46, 0, 4], right_shin=[72, 0, 0], left_leg=[-46, 0, -4],
               left_shin=[72, 0, 0], right_arm=[24, 0, 10], right_hand=[-30, 0, 0], left_arm=[30, 0, -16], mantle=[-14, 0, 0],
               hips_pos=[0, -3.2, 0.4])
    RISE = P(pitch=120, body=[-12, 0, 0], right_leg=[16, 0, 4], right_shin=[24, 0, 0], left_leg=[16, 0, -4], left_shin=[24, 0, 0],
             right_arm=[-168, 0, 6], right_hand=[-16, 0, 0], left_arm=[-168, 0, -6], left_hand=[-16, 0, 0], left_fingers=[0, 0, 60],
             mantle=[50, 0, 0], tabard=[20, 0, 0], hips_pos=[0, 0.6, 0])
    APEX = P(RISE, pitch=140, body=[-16, 0, 0], right_arm=[-178, 0, 4], right_leg=[-30, 0, 4], right_shin=[70, 0, 0],
             left_leg=[10, 0, -4], left_shin=[40, 0, 0])
    SMASH = P(pitch=-70, body=[34, 0, 0], helm=[10, 0, 0], right_arm=[-60, 0, 0], right_hand=[-10, 0, 0], left_arm=[-56, 0, 0],
              left_hand=[-10, 0, 0], left_fingers=[0, 0, 60], right_leg=[-60, 0, 4], right_shin=[90, 0, 0], left_leg=[-30, 0, -4],
              left_shin=[60, 0, 0], mantle=[-20, 0, 0], tabard=[-16, 0, 0], hips_pos=[0, -4.2, -0.4])
    A["leap"] = anim(L, False, timeline([(0, stand, lin), (0.45, CROUCH, ease_in_out), (0.6, RISE, ease_out_back), (1.05, APEX, ease_in_out),
                                         (1.3, SMASH, ease_in), (1.7, SMASH, lin), (2.2, stand, ease_in_out)]), 0.025)

    # moon (3 s): the halberd planted, the left hand raised to the sky at 1.0 s, the head thrown back, trembling
    L = 3.0
    PLANT = P(pitch=90, right_arm=[-36, 0, -10], right_hand=[-50, 0, 0], body=[6, 0, 0], left_arm=[-10, 0, -20])
    CALL = P(PLANT, pitch=90, body=[-16, 0, 0], helm=[-38, 0, 0], left_arm=[-175, 0, -14], left_hand=[-6, 0, 0], left_fingers=[0, 0, -24],
             mantle=[30, 0, 0], tabard=[10, 0, 0], right_leg=[-6, 0, 6], left_leg=[6, 0, -6])
    A["moon"] = anim(L, False, timeline([(0, stand, lin), (0.45, PLANT, ease_in_out), (1.0, CALL, ease_out_back), (2.4, CALL, lin),
                                         (3.0, stand, ease_in_out)], extra={
        "left_arm": lambda t: [1.4 * math.sin(t * 47) * window(t, 1.0, 1.2) * (1 - window(t, 2.1, 2.4)), 0, 0],
        "body": lambda t: [0.7 * math.sin(t * 39) * window(t, 1.0, 1.2) * (1 - window(t, 2.1, 2.4)), 0, 0],
        "mantle": lambda t: [5 * math.sin(t * 9) * window(t, 1.0, 1.3) * (1 - window(t, 2.1, 2.4)), 0, 0],
    }), 0.05)

    # death (5 s, held): he reels, falls to one knee with the halberd planted before him and his head bowed, then
    # slumps over it as the light takes him
    L = 5.0
    REEL = P(pitch=80, body=[-16, 10, 0], helm=[-24, 0, 0], right_arm=[-20, 0, 10], right_hand=[-20, 0, 0], left_arm=[10, 0, -30],
             left_fingers=[0, 0, -10], mantle=[14, 0, 0])
    KNEEL = P(pitch=90, body=[16, 0, 0], helm=[26, 0, 0], right_arm=[-50, 0, -8], right_hand=[-40, 0, 0], left_arm=[-10, 0, -10],
              left_hand=[-10, 0, 0], left_fingers=[0, 0, 30], right_leg=[-6, 0, 4], right_shin=[92, 0, 0], left_leg=[-84, 0, -4],
              left_shin=[86, 0, 0], mantle=[-10, 0, 0], tabard=[-30, 0, 0], hips_pos=[0, -7.6, 0])
    SLUMP = P(KNEEL, pitch=90, body=[30, 0, 8], helm=[40, 0, 10], left_arm=[0, 0, -4], right_arm=[-40, 0, -6])
    A["death"] = anim(L, "hold", timeline([(0, stand, lin), (0.35, REEL, ease_out_back), (0.8, REEL, lin), (1.4, KNEEL, ease_in),
                                           (3.0, KNEEL, lin), (3.5, SLUMP, ease_in_out), (5.0, SLUMP, lin)], extra={
        "body": lambda t: [1.0 * math.sin(t * 7) * window(t, 1.5, 1.8) * (1 - window(t, 2.6, 3.0)), 0, 0],
    }), 0.05)
    GC.write_anims(NAME, A)


# ============================================================================================= the items

N = 64


def _disc(cx, cy, r):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    return np.hypot(xx - cx, yy - cy) <= r


def _ring(cx, cy, r0, r1):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    d = np.hypot(xx - cx, yy - cy)
    return (d >= r0) & (d <= r1)


def _line(p0, p1, w):
    return hp.thick_line(p0, p1, w, N)


def citadel_key():
    """A key of crimson obsidian bound in dark gold: its bow a crowned gate with a crimson stone where the door would
    be, a long blade with a line of light down it, and bits cut like battlements."""
    # the key's axis runs from the bow (low left) to the tip (high right)
    bow = np.array([18.0, 45.0])
    tip = np.array([55.0, 10.0])
    u = (tip - bow) / np.linalg.norm(tip - bow)
    v = np.array([-u[1], u[0]])
    length = float(np.linalg.norm(tip - bow))

    def at(s, t=0.0):
        p = bow + u * s + v * t
        return float(p[0]), float(p[1])

    def quad(s0, s1, t0, t1):
        return hp.poly([at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)], N)

    bx, by = bow
    # the bow: an arched gate (a round top on a square foot), with a crown of three points on its arch
    gate = (_disc(bx, by - 1.0, 9.6) | hp.poly([(bx - 9.6, by - 1.0), (bx + 9.6, by - 1.0), (bx + 9.6, by + 9.0), (bx - 9.6, by + 9.0)], N))
    hole = _disc(bx, by - 0.5, 4.8) | hp.poly([(bx - 4.8, by - 0.5), (bx + 4.8, by - 0.5), (bx + 4.8, by + 5.4), (bx - 4.8, by + 5.4)], N)
    crown = hp.poly([(bx - 7.5, by - 8.0), (bx - 7.5, by - 13.5), (bx - 4.0, by - 10.0), (bx, by - 16.5), (bx + 4.0, by - 10.0),
                     (bx + 7.5, by - 13.5), (bx + 7.5, by - 8.0)], N)
    rim = gate & ~hp.shrink(gate, 2)
    blade = quad(9.0, length, -3.0, 3.0)
    trim = quad(9.0, length, -3.0, -2.0) | quad(9.0, length, 2.0, 3.0)
    bits = quad(length - 15.5, length - 11.5, 2.0, 9.5) | quad(length - 9.5, length - 5.5, 2.0, 7.0) | quad(length - 3.5, length, 2.0, 9.0)
    battl = quad(length - 15.5, length - 14.0, 9.0, 10.5) | quad(length - 13.0, length - 11.5, 9.0, 10.5)
    collar = quad(9.5, 14.0, -5.0, 5.0)
    cv = hp.Canvas(N)
    cv.layer(blade | bits | battl, hp.hexc("561626"), "obsidian", seed=71)
    cv.layer(trim, hp.hexc("b8862c"), "gold", seed=72)
    cv.layer(gate & ~hole, hp.hexc("5e1a2a"), "obsidian", seed=73)
    cv.layer(rim & ~hole, hp.hexc("b8862c"), "gold", seed=74)
    cv.layer(crown, hp.hexc("c9952f"), "gold", seed=75)
    cv.layer(collar, hp.hexc("a8782a"), "gold", seed=76)
    cv.seams()
    a = cv.a
    sil = a[..., 3] > 0
    # the door of the gate: dark, with a stone set in it
    door = hole & ~_disc(bx, by + 0.8, 3.2)
    a[hole] = (16, 6, 10, 255)
    hp.engrave(a, hp.grow(hole, 1) & ~hole & sil, 0.3)
    groove = quad(16.5, length - 17.0, -0.55, 0.55)
    hp.engrave(a, hp.grow(groove, 1) & sil & ~groove, 0.25)
    a[groove & sil] = (24, 8, 14, 255)
    cracks = hp.cracks(hp.shrink(blade, 1), 37, n=4, length=7)
    hp.glow_paint(a, cracks & blade & ~groove, (255, 70, 70), halo=0.35, halo_px=1)
    for px, py in ((bx - 7.5, by + 7.0), (bx + 6.5, by + 7.0)):
        hp.rivet(a, px, py, hp.hexc("e8c070"), 1)
    del door
    base = hp.finish(a, grade="mythic", accent=(255, 70, 80, 255), seed=17)
    # the light: the stone in the door, the line down the blade, the crown's points catching it
    glow = np.zeros((N, N, 4), np.uint8)
    hp.gem(glow, bx, by + 0.8, 3.2, (255, 50, 70, 255))
    col = np.array([255, 90, 100])
    glow[groove & sil, :3] = col
    glow[groove & sil, 3] = 255
    s = (np.mgrid[0:N, 0:N][1] + np.mgrid[0:N, 0:N][0]) % 5
    dash = groove & sil & (s == 0)
    glow[dash, :3] = 255
    for px, py in ((bx, by - 16.0), (bx - 7.5, by - 13.0), (bx + 7.5, by - 13.0)):
        x, y = int(px), int(py)
        if 0 <= x < N and 0 <= y < N:
            glow[y, x] = (255, 236, 200, 255)
    return base, Image.fromarray(glow)


def sigil_of_dominion():
    """A seal of black gold: a crown of crimson fire in a ring of runes, eight rays of light behind it, a chain loop at
    its top."""
    cx, cy = 32.0, 34.0
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    ang = np.arctan2(yy - cy, xx - cx)
    r = np.hypot(xx - cx, yy - cy)
    # eight blades of a star behind the seal (four long, four short)
    k = np.abs(((ang / (2 * math.pi) * 8) % 1.0) - 0.5) * 2          # 1 on a ray's axis
    long_ray = (np.round(ang / (math.pi / 2)) * (math.pi / 2))
    is_long = np.abs(np.angle(np.exp(1j * (ang - long_ray)))) < math.pi / 8
    reach = np.where(is_long, 29.0, 23.0)
    star = (r < reach * (0.42 + 0.58 * k ** 1.7)) & (r > 14.0)
    seal = _disc(cx, cy, 17.0)
    rim = _ring(cx, cy, 14.6, 17.0)
    inner_ring = _ring(cx, cy, 10.2, 11.2)
    loop = _ring(cx, cy - 19.5, 2.2, 3.6) & ~seal
    crown = hp.poly([(cx - 7.0, cy + 5.0), (cx - 7.0, cy - 3.0), (cx - 3.6, cy + 0.6), (cx, cy - 7.0), (cx + 3.6, cy + 0.6),
                     (cx + 7.0, cy - 3.0), (cx + 7.0, cy + 5.0)], N)
    band = hp.poly([(cx - 7.0, cy + 3.0), (cx + 7.0, cy + 3.0), (cx + 7.0, cy + 5.4), (cx - 7.0, cy + 5.4)], N)
    cv = hp.Canvas(N)
    cv.layer(star, hp.hexc("8c6a2a"), "gold", seed=81)
    cv.layer(loop, hp.hexc("b8862c"), "gold", seed=82)
    cv.layer(seal & ~rim, hp.hexc("2a1418"), "obsidian", seed=83)
    cv.layer(rim, hp.hexc("c9952f"), "gold", seed=84)
    cv.layer(crown, hp.hexc("a01a2c"), "obsidian", seed=85)
    cv.layer(band, hp.hexc("c9952f"), "gold", seed=86)
    cv.seams()
    a = cv.a
    sil = a[..., 3] > 0
    hp.engrave(a, inner_ring & sil, 0.35)
    # runes round the inner ring: short ticks, every third one a glyph
    ticks = np.zeros((N, N), bool)
    for i in range(16):
        t = i * 2 * math.pi / 16
        p0 = (cx + 12.0 * math.cos(t), cy + 12.0 * math.sin(t))
        p1 = (cx + 13.6 * math.cos(t), cy + 13.6 * math.sin(t))
        ticks |= _line(p0, p1, 0.9)
    a[ticks & sil] = (30, 10, 14, 255)
    for px, py in ((cx - 4.5, cy + 4.2), (cx, cy + 4.2), (cx + 4.5, cy + 4.2)):
        hp.rivet(a, px, py, hp.hexc("ff5a6a"), 1)
    base = hp.finish(a, grade="divine", accent=(255, 70, 80, 255), accent2=(255, 200, 120, 255), seed=23)
    glow = np.zeros((N, N, 4), np.uint8)
    lit = ticks & sil
    glow[lit, :3] = (255, 96, 104)
    glow[lit, 3] = 255
    core = crown & hp.shrink(crown, 1)
    glow[core, :3] = (255, 60, 76)
    glow[core, 3] = 255
    hot = core & hp.shrink(crown, 2)
    glow[hot, :3] = (255, 150, 150)
    hp.gem(glow, cx, cy - 1.5, 2.2, (255, 220, 160, 255))
    return base, Image.fromarray(glow)


def write_items():
    os.makedirs(ITEM_TEX, exist_ok=True)
    for name, make in (("citadel_key", citadel_key), ("sigil_of_dominion", sigil_of_dominion)):
        base, glow = make()
        base.save(os.path.join(ITEM_TEX, f"{name}.png"), optimize=True)
        glow.save(os.path.join(ITEM_TEX, f"{name}_light.png"), optimize=True)
        model = {"parent": "minecraft:item/generated", "loader": "neoforge:item_layers",
                 "textures": {"layer0": f"beyondsolo:item/{name}", "layer1": f"beyondsolo:item/{name}_light"},
                 "neoforge_data": {"layers": {"1": {"block_light": 15, "sky_light": 15}}}}
        with open(os.path.join(ITEM_MODELS, f"{name}.json"), "w") as f:
            json.dump(model, f, indent=2)
            f.write("\n")
    print("  items: citadel_key, sigil_of_dominion")


def main():
    only = sys.argv[1:]
    if not only or "lord" in only:
        citadel_lord()
    elif "anims" in only:
        write_lord_anims()
    if not only or "items" in only:
        write_items()
    print("tower assets written")


if __name__ == "__main__":
    main()
