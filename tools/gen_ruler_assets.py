#!/usr/bin/env python3
"""
The rulers of light's assets (see com.beyondsolo.ruler): three rulers and their heralds, the mote of radiance and the
blessing of light's icon.

- Aurenthe, Ruler of the Verdict (ruler_verdict): a knight of white gold under a nimbus of light, a closed helm with a
  slit of light for a face, a greatsword with a blade of light and a winged guard, two great wings of white steel
  feathers folded at his back, a tabard hemmed in gold.
- Caelistor, Ruler of a Thousand Wings (ruler_wings): a slender lancer in white plate and a winged helm, her pale gold
  hair falling down her back, a long lance of light, and four wings: a great pair at her shoulders and a smaller pair
  under them.
- Ithrael, Ruler of the Unseen Hand (ruler_hand): a tall figure in layered robes of white and gold, hooded, a smooth
  mask of gold with one line of light down it, a ring of gold plates turning behind his head, and his hands: two great
  gauntlets of white gold that float beside him and do what his own thin hands only gesture.
- A herald of light (ruler_herald): a soldier of the rulers in white plate, a faceless helm crowned with spikes of
  light, small wings, a spear of light.

All four are built with the creature kit (tools/gen_creatures.py, one skin each painted on the model by
tools/creature_skins.py) at a scale of their design (MODEL_SCALE), like the monarchs (tools/gen_worldboss_assets.py,
whose motion helpers this uses). Their animations' times match the moves in the ruler classes (see each one's notes).

Run from the repo root:   python3 tools/gen_ruler_assets.py                    (everything)
                          python3 tools/gen_ruler_assets.py verdict herald     (only some)
Look at one without the game:   python3 tools/preview_model.py ruler_verdict_main --anim cleave --time 0.7
Needs: pillow, numpy.

Conventions (as the creature kit): 16 units are one block, the front of a model is -z, +x is the model's left. A
positive x rotation tilts a bone's top towards the front; a positive z rotation tilts its top towards +x.
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_creatures as GC  # noqa: E402
import gen_enemy_assets as G  # noqa: E402
import gen_worldboss_assets as W  # noqa: E402
from gen_gate_assets import ease_in, ease_in_out, ease_out_back, window  # noqa: E402

anim, wave = G.anim, G.wave
pose, timeline, lin, shake, bump, hide, OneSkin = W.pose, W.timeline, W.lin, W.shake, W.bump, W.hide, W.OneSkin
crystal_spike = W.crystal_spike

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "beyondsolo")

# ============================================================================================= the light's colours

GOLD_LIGHT = (255, 216, 120)

# white gold plate, gold trim, white cloth hemmed in gold, white steel feathers
RULER_RAMPS = {
    "plate": [(74, 66, 54), (162, 152, 132), (222, 214, 194), (246, 242, 230), (255, 255, 252)],
    "trim": [(48, 28, 6), (128, 86, 24), (204, 158, 62), (242, 208, 118), (255, 246, 206)],
    "cloth": [(30, 26, 22), (102, 92, 78), (180, 170, 150), (226, 220, 204), (252, 250, 242)],
    "mantle": [(40, 30, 14), (120, 96, 52), (196, 168, 104), (234, 214, 160), (255, 248, 222)],
    "feather": [(44, 40, 36), (124, 116, 104), (202, 196, 182), (240, 236, 226), (255, 255, 252)],
    "haft": [(40, 26, 8), (110, 78, 28), (180, 138, 62), (228, 196, 120), (255, 238, 190)],
    "grip": [(26, 22, 18), (76, 66, 54), (140, 126, 104), (196, 184, 160), (240, 232, 214)],
    "hair": [(60, 40, 12), (140, 104, 44), (210, 174, 96), (244, 222, 160), (255, 248, 220)],
    "mask": [(52, 32, 8), (136, 96, 34), (214, 172, 84), (248, 222, 150), (255, 250, 222)],
    "leather": (150, 128, 96),
}


def ruler_palette(name, glow=GOLD_LIGHT, extra=None, density=4.0):
    ramps = dict(RULER_RAMPS)
    ramps.update(extra or {})
    GC.register(name, lambda aff: GC.pal("plain", glow, ramps, tinted=()), density=density)


PL = dict(r="plate", engrave="trim", rivets=False, marks=0.0, grime=False)
LP = dict(r="plate", rivets=False, marks=0.0, dents=False, grime=False)
TM = dict(r="trim", marks=0.0)
FE = dict(r="feather", fuller=False, marks=0.0, edge="front")
GOWN = dict(r="cloth", folds=1.5, marks=0.0)


def light_wing(B, side, sx, seed, x0, y0, z0, arm_len=10.0, feathers=6, inner=(7.2, 0.7), outer=(18.0, -1.0), width=1.7, parent="body",
               name=None, rest=(35, 10, 0), rest2=-110):
    """A wing of white steel feathers (after the Monarch of Storms' wings), built folded the way it rests: its arm running
    back from the shoulder blade, the feathers hanging from it; the hand of the wing turned down behind with its long
    feathers fanned from the wrist, each with a shaft of light. Spread it by turning the arm out to the side (y) and up
    (z), the hand straight out (x). inner/outer: (first feather's length, growth per feather)."""
    name = name or f"{side}_wing"
    arm = [B.c([x0 - 0.75, y0 - 0.75, z0 - 0.4], [1.5, 1.5, arm_len], "plate", **LP, seed=seed),
           B.c([x0 - 0.9, y0 - 0.5, z0 + arm_len - 2.4], [1.8, 1.8, 2.0], "plate", r="trim", rivets=False, dents=False, grime=False, marks=0.0,
               seed=seed + 1)]
    step = (arm_len - 1.4) / feathers
    for i in range(feathers):
        z = z0 + 0.9 + i * step
        ln = inner[0] + i * inner[1]
        rach = [dict(pts=[(z, y0 - 1.0), (z, y0 - ln + 0.4)], w=0.16, faces=("west", "east"), halo=0.45, bright=0.75)]
        arm += [B.c([x0 - 0.15, y0 - 0.5 - ln, z - width / 2], [0.3, ln, width], "metal", long="y", seed=seed + 2 + i, pivot=[x0, y0 - 0.5, z],
                    rot=[-18 + i * 2, 0, 0], lines=rach, **FE),
                B.c([x0 - 0.12, y0 - 0.5 - ln - 1.5, z - width * 0.3], [0.24, 1.6, width * 0.6], "metal", long="y", seed=seed + 2 + i,
                    pivot=[x0, y0 - 0.5, z], rot=[-18 + i * 2, 0, 0], **FE)]
    B.bone(name, parent, [x0, y0, z0], arm, rot=[rest[0], sx * rest[1], rest[2]])
    wz = z0 + arm_len - 0.8
    hand = [B.c([x0 - 0.6, y0 - 0.6, wz - 0.2], [1.2, 1.2, 3.4], "plate", **LP, seed=seed + 10)]
    for i in range(feathers):
        a = 4 + i * 7.0
        ln = outer[0] + i * outer[1]
        rach = [dict(pts=[(wz + 0.6, y0), (wz + ln - 0.8, y0)], w=0.16, faces=("west", "east"), halo=0.45, bright=0.8)]
        hand += [B.c([x0 - 0.15, y0 - width / 2, wz], [0.3, width, ln], "metal", long="z", seed=seed + 11 + i, pivot=[x0, y0, wz], rot=[-a, 0, 0],
                     lines=rach, **FE),
                 B.c([x0 - 0.12, y0 - 0.5, wz + ln], [0.24, 1.0, width], "metal", long="z", seed=seed + 11 + i, pivot=[x0, y0, wz], rot=[-a, 0, 0],
                     **FE)]
    B.bone(name + "2", name, [x0, y0, wz], hand, rot=[rest2, 0, 0])


def wing_pose(spread=1.0, up=25.0, sweep=0.0, tip=105.0, names=("wing",)):
    """Wings spread 0 (folded) to 1 (straight out), raised by up, swept forward by sweep, the hand opened by tip."""
    out = {}
    for n in names:
        for side, sx in (("right", -1), ("left", 1)):
            out[f"{side}_{n}"] = [-35 * spread, sx * (70 * spread + sweep), -sx * up * spread]
            out[f"{side}_{n}2"] = [tip * spread, 0, 0]
    return out


def wingbeat(L, amp=32.0, tip=18.0, phase=0.0, on=None, names=("wing",)):
    """Wingbeats on top of a pose (period L): the arms of the wings up and down, the hands following behind."""
    out = {}
    for n in names:
        for side, sx in (("right", -1), ("left", 1)):
            out[f"{side}_{n}"] = (lambda t, sx=sx: [0, sx * 6 * wave(t, L, phase + 0.25) * (on(t) if on else 1),
                                                    -sx * amp * wave(t, L, phase) * (on(t) if on else 1)])
            out[f"{side}_{n}2"] = (lambda t: [-tip * wave(t, L, phase - 0.15) * (on(t) if on else 1), 0, 0])
    return out


def merge(*ds):
    """Extra channels added together where two of them move the same bone."""
    out = {}
    for d in ds:
        for k, fn in d.items():
            if k in out:
                a, b = out[k], fn
                out[k] = (lambda t, a=a, b=b: [x + y for x, y in zip(a(t), b(t))])
            else:
                out[k] = fn
    return out


def flutter(t, amp=4.0, f=9.0):
    return amp * (math.sin(t * f) + 0.5 * math.sin(t * f * 2.3 + 1.0))


def knight_legs(B, hip_x=1.8, seed=20):
    """Plated legs: thighs (right_leg, left_leg) and shins with knee cops and sabatons."""
    box, col = B.box, B.col
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * hip_x
        B.bone(f"{side}_leg", "hips", [x, 17.2, 0.0], [
            col(x, 0.0, 9.4, 17.6, 2.8, 3.0, "plate", **LP, seed=seed + (sx > 0)),
            box(x, 15.4, -1.45, 2.4, 2.0, 0.5, "plate", **PL, seed=seed + 2 + (sx > 0)),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [x, 9.4, 0.0], [
            box(x, 9.4, -1.5, 2.6, 2.2, 1.0, "plate", **PL, seed=seed + 4 + (sx > 0)),
            box(x, 9.4, -2.05, 1.0, 1.0, 0.3, "trim", **TM, pivot=[x, 9.4, -2.05], rot=[0, 0, 45]),
            col(x, 0.0, 1.5, 9.6, 2.6, 2.8, "plate", **PL, seed=seed + 6 + (sx > 0)),
            box(x, 0.8, -0.6, 2.8, 1.6, 4.2, "plate", **LP, seed=seed + 8 + (sx > 0)),
            box(x, 0.6, -2.8, 2.2, 1.2, 1.0, "trim", **TM),
        ])


# ============================================================================================= Aurenthe, the Verdict

VERDICT = "ruler_verdict"
G.MODEL_SCALE[VERDICT] = 1.95


def verdict():
    """Aurenthe, built 34 units tall to the top of his helm (his nimbus to 41) and drawn 1.95 times bigger (4.1 blocks,
    his hitbox 4.2). Bones: root, hips, skirt_front, skirt_back (the tabard), legs and shins, body, head (turned to
    look) with its nimbus, arms and hands, sword (in his right hand), javelin (a spear of light in his left hand, shown
    only by spears), the wings (right_wing and right_wing2, the hand of the wing; the same on the left)."""
    B = OneSkin(VERDICT, 7100)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17.6, 0], [
        box(0, 18.2, 0.0, 6.2, 2.6, 4.2, "plate", **LP, seed=10),
        box(0, 19.4, 0.0, 6.6, 0.9, 4.6, "trim", **TM),
        box(0, 19.4, -2.35, 1.4, 1.4, 0.4, "glow", pivot=[0, 19.4, -2.35], rot=[0, 0, 45]),
        # faulds: lames of plate over the hips
        *[c(mx([0.4, 15.0 - j * 1.6, -2.55 + j * 0.15], [2.8, 2.0, 0.6], sx), [2.8, 2.0, 0.6], "plate", **PL, seed=12 + j * 2 + (sx > 0),
            pivot=[sx * 1.8, 17.0, -2.2], rot=[-8 - j * 4, 0, -sx * 8]) for sx in (-1, 1) for j in range(2)],
        *[c(mx([2.9, 13.4, -1.8], [0.6, 4.6, 3.8], sx), [0.6, 4.6, 3.8], "plate", **PL, seed=16 + (sx > 0), pivot=[sx * 3.1, 17.6, 0.0],
            rot=[0, 0, -sx * 12]) for sx in (-1, 1)],
    ])
    B.bone("skirt_front", "hips", [0, 18.6, -2.4], [
        # the tabard: white, hemmed in gold, a band of light across it and the scales in gold at its middle
        c([-1.9, 4.4, -2.75], [3.8, 14.2, 0.5], "cloth", **GOWN, hem=1.4, glow_hem=1.8, band=(5.6, 6.4), seed=18, skip=("up", "down"),
          lines=[dict(pts=[(-1.2, 12.4), (1.2, 12.4)], w=0.22, faces=("north",), halo=0.5, bright=0.9),
                 dict(pts=[(0.0, 14.0), (0.0, 10.6)], w=0.22, faces=("north",), halo=0.5, bright=0.9),
                 dict(pts=[(-1.2, 12.4), (-1.5, 11.2), (-0.9, 11.2), (-1.2, 12.4)], w=0.16, faces=("north",), halo=0.4, bright=0.8),
                 dict(pts=[(1.2, 12.4), (1.5, 11.2), (0.9, 11.2), (1.2, 12.4)], w=0.16, faces=("north",), halo=0.4, bright=0.8)]),
    ], rot=[-4, 0, 0])
    B.bone("skirt_back", "hips", [0, 18.6, 2.2], [
        c([-2.8, 2.4, 2.0], [5.6, 16.0, 0.5], "cloth", **GOWN, hem=1.6, glow_hem=1.8, band=(4.4, 5.2), seed=19, skip=("up", "down")),
    ], rot=[6, 0, 0])
    knight_legs(B, 1.8, 20)
    B.bone("body", "hips", [0, 19.8, 0.0], [
        box(0, 21.6, 0.0, 5.6, 3.6, 3.8, "plate", **PL, seed=30),
        box(0, 25.6, -0.1, 8.4, 5.4, 4.8, "plate", **PL, seed=31),
        *[c(mx([0.1, 23.0, -2.75], [4.0, 5.0, 0.7], sx), [4.0, 5.0, 0.7], "plate", **LP, seed=32 + (sx > 0), pivot=[0, 25.5, -2.4],
            rot=[0, -sx * 14, 0]) for sx in (-1, 1)],
        # a sun of gold at his heart, its rays of light
        box(0, 25.4, -3.2, 2.6, 2.6, 0.4, "trim", **TM, pivot=[0, 25.4, -3.2], rot=[0, 0, 45],
            lines=[dict(pts=[(-0.9, 25.4), (0.9, 25.4)], w=0.3, faces=("north",), halo=0.7, bright=1.0),
                   dict(pts=[(0.0, 24.5), (0.0, 26.3)], w=0.3, faces=("north",), halo=0.7, bright=1.0)]),
        box(0, 25.4, -3.45, 0.9, 0.9, 0.3, "glow", pivot=[0, 25.4, -3.45], rot=[0, 0, 45]),
        box(0, 28.9, -0.1, 5.2, 1.4, 4.4, "plate", **LP, seed=34),
        box(0, 26.0, 2.6, 6.4, 4.8, 0.8, "plate", **LP, seed=35),
    ])
    nimbus = []
    for i, a in enumerate(range(-90, 91, 18)):
        # the nimbus: rays of light standing out behind his helm in a half sun
        h = 3.6 + (2.6 if i % 2 == 0 else 0.0) + 1.2 * math.cos(math.radians(a))
        nimbus += crystal_spike(B, math.sin(math.radians(a)) * 1.4, 32.6 + math.cos(math.radians(a)) * 0.8, 2.7, 0.55, h, rx=-8, rz=a, d=0.3)
    B.bone("head", "body", [0, 29.4, 0.0], [
        # a closed helm of white gold, a slit of light across it for a face and one down it
        box(0, 31.8, -0.1, 4.4, 4.6, 4.6, "plate", **PL, seed=40,
            lines=[dict(pts=[(-1.5, 32.0), (1.5, 32.0)], w=0.32, faces=("north",), halo=0.7, bright=1.0),
                   dict(pts=[(0.0, 31.8), (0.0, 30.2)], w=0.22, faces=("north",), halo=0.5, bright=0.85)]),
        box(0, 32.95, -2.45, 4.6, 0.6, 0.5, "trim", **TM),
        box(0, 30.6, -2.5, 1.0, 2.6, 0.5, "plate", **LP, seed=41),
        *[c(mx([1.4, 29.6, -2.6], [0.9, 2.4, 1.3], sx), [0.9, 2.4, 1.3], "plate", **LP, seed=42 + (sx > 0), pivot=[sx * 1.8, 31.0, -2.0],
            rot=[0, sx * 16, 0]) for sx in (-1, 1)],
        box(0, 34.25, 0.0, 3.0, 0.5, 3.6, "plate", **LP, seed=44),
        # a circlet of gold over the helm, three points of it standing up at the front
        box(0, 33.9, -2.5, 4.8, 0.7, 0.3, "trim", **TM),
        *[box(sx * 2.45, 33.9, 0.0, 0.3, 0.7, 5.0, "trim", **TM) for sx in (-1, 1)],
        c([-0.35, 34.2, -2.6], [0.7, 2.2, 0.3], "trim", **TM, pivot=[0, 34.2, -2.45], rot=[-8, 0, 0]),
        *[c([sx * 1.3 - 0.25, 34.2, -2.6], [0.5, 1.5, 0.3], "trim", **TM, pivot=[sx * 1.3, 34.2, -2.45], rot=[-8, 0, -sx * 10]) for sx in (-1, 1)],
        *nimbus,
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 4.6, 28.4, 0.0]
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 5.0, 0.0, 22.4, 28.4, 2.6, 2.8, "plate", **LP, seed=50 + (sx > 0)),
            # a great pauldron of three plates rimmed in gold, rising to a ridge
            c(mx([3.0, 27.6, -2.4], [3.9, 1.8, 4.8], sx), [3.9, 1.8, 4.8], "plate", **PL, seed=52 + (sx > 0), pivot=piv, rot=[0, 0, sx * 20]),
            c(mx([3.5, 26.3, -2.2], [3.5, 1.5, 4.4], sx), [3.5, 1.5, 4.4], "plate", **LP, seed=54 + (sx > 0), pivot=piv, rot=[0, 0, sx * 28]),
            c(mx([3.9, 25.2, -2.0], [3.0, 1.3, 4.0], sx), [3.0, 1.3, 4.0], "plate", **LP, seed=56 + (sx > 0), pivot=piv, rot=[0, 0, sx * 36]),
            c(mx([2.9, 27.4, -2.5], [4.1, 0.3, 5.0], sx), [4.1, 0.3, 5.0], "trim", **TM, pivot=piv, rot=[0, 0, sx * 20]),
            c(mx([3.4, 29.2, -1.8], [0.4, 1.4, 3.6], sx), [0.4, 1.4, 3.6], "trim", **TM, pivot=piv, rot=[0, 0, sx * 20]),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.0, 22.6, 0.0], [
            box(sx * 5.0, 22.4, 0.9, 2.4, 2.0, 1.2, "plate", **LP, seed=58 + (sx > 0)),
            col(sx * 5.0, 0.0, 17.4, 22.6, 2.4, 2.6, "plate", **PL, seed=60 + (sx > 0)),
            box(sx * 5.0, 18.6, 0.0, 3.0, 1.6, 3.2, "plate", **LP, seed=62 + (sx > 0)),
            box(sx * 5.0, 18.6, -1.65, 3.1, 0.5, 0.3, "trim", **TM),
            box(sx * 5.0, 16.4, -0.1, 2.6, 2.4, 2.8, "plate", **LP, seed=64 + (sx > 0)),
        ])
    # the greatsword: a grip bound in white, a guard of gold spread like wings, a long broad blade of light
    hx, hz = -5.0, -0.1
    B.bone("sword", "right_hand", [hx, 16.4, hz], [
        col(hx, hz, 12.4, 19.4, 0.9, 0.9, "leather", r="grip", stitch=False, seed=70, marks=0.0),
        box(hx, 11.9, hz, 1.4, 1.2, 1.4, "trim", **TM, pivot=[hx, 11.9, hz], rot=[0, 45, 0]),
        box(hx, 11.9, hz, 0.7, 0.7, 0.7, "glow"),
        box(hx, 19.8, hz, 1.2, 0.9, 1.6, "trim", **TM),
        *[c([hx - 0.4, 19.6, hz - 0.5 if sz < 0 else hz - 0.5], [0.8, 0.9, 4.2], "trim", **TM, pivot=[hx, 20.0, hz], rot=[sz * -64, 0, 0])
          for sz in (-1, 1)],
        *[c([hx - 0.3, 20.0, hz - 0.4], [0.6, 0.7, 3.2], "trim", **TM, pivot=[hx, 20.3, hz], rot=[sz * -40, 0, 0]) for sz in (-1, 1)],
        c([hx - 0.3, 20.2, hz - 1.0], [0.6, 18.6, 2.0], "crystal"),
        c([hx - 0.22, 38.8, hz - 0.7], [0.44, 2.2, 1.4], "crystal"),
        c([hx - 0.14, 41.0, hz - 0.35], [0.28, 1.2, 0.7], "crystal"),
        c([hx - 0.36, 20.2, hz - 0.3], [0.72, 12.0, 0.6], "glow"),
    ])
    # a spear of light he makes in his left hand and throws (shown only by spears): lying forward in his fist
    jx, jy = 5.0, 16.6
    B.bone("javelin", "left_hand", [jx, jy, 0.0], [
        c([jx - 0.3, jy - 0.3, -10.0], [0.6, 0.6, 17.0], "crystal"),
        c([jx - 0.45, jy - 0.45, -13.4], [0.9, 0.9, 3.4], "crystal"),
        c([jx - 0.2, jy - 0.2, -15.2], [0.4, 0.4, 1.8], "crystal"),
        c([jx - 0.4, jy - 0.4, 6.2], [0.8, 0.8, 1.2], "glow"),
    ])
    light_wing(B, "right", -1, 100, -1.7, 27.6, 3.0, arm_len=10.5, inner=(7.6, 0.8), outer=(19.0, -1.0), width=1.9)
    light_wing(B, "left", 1, 130, 1.7, 27.6, 3.0, arm_len=10.5, inner=(7.6, 0.8), outer=(19.0, -1.0), width=1.9)
    # his tabard is the deep blue of a high sky, so the gold on it reads
    ruler_palette(VERDICT, extra={"cloth": [(6, 10, 26), (16, 28, 64), (34, 56, 118), (70, 100, 168), (130, 160, 214)]})
    B.write(6.0, 4.6, 1.6)
    verdict_anims()


def verdict_anims():
    """Times match VerdictRuler and RulerBoss: intro 110 ticks (down out of his rift over the first 80, touching down at
    79), phase changes 46, death 100 (held), leaving 90 (rising back into the sky). Moves: cleave 30 (the sweep lands at
    14), advance 34 (drawn back to 14, the lunge 14-18), pillars 36 (the pillars fall at 22), scales 52 (the band at 20,
    the ground at his feet at 40), spears 46 (thrown at 10, 18 and 26), descent 60 (up 2-18, hanging to 30, down on his
    prey 30-40), final_verdict 140 (the cross at 50, turned at 80, the ground at his feet at 110)."""
    A = {}
    bones = ("root", "hips", "skirt_front", "skirt_back", "right_leg", "right_shin", "left_leg", "left_shin", "body", "head", "right_arm",
             "right_hand", "left_arm", "left_hand", "sword", "right_wing", "right_wing2", "left_wing", "left_wing2")
    # standing: the greatsword upright at his side, his left hand loose
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-14, 0, 10], right_hand=[-24, 0, 0], sword=[38, 0, -16],
                 left_arm=[-6, 0, -8], left_hand=[-12, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    SPREAD = wing_pose(1.0)
    HALF = wing_pose(0.45, up=30, tip=50)
    HIGH = wing_pose(0.9, up=60, tip=90)

    L = 4.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.12 * wave(t, L), 0],
        "head": lambda t: [1.5 * wave(t, L, 0.3), 5 * wave(t, L * 2), 0],
        "right_wing": lambda t: [2 * wave(t, L, 0.2), 0, 1.5 * wave(t, L)], "left_wing": lambda t: [2 * wave(t, L, 0.2), 0, -1.5 * wave(t, L)],
        "right_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0], "left_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0],
        "skirt_front": lambda t: [1.0 * wave(t, L, 0.4), 0, 0], "skirt_back": lambda t: [1.2 * wave(t, L, 0.1), 0, 0],
        "left_hand": lambda t: [3 * wave(t, L, 0.5), 0, 0],
    }), 0.05)

    L = 1.3
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [24 * wave(t, L), 0, 0], "left_leg": lambda t: [-24 * wave(t, L), 0, 0],
        "right_shin": lambda t: [28 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [28 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips": lambda t: [0, 4 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.45 * abs(wave(t, L)), 0],
        "body": lambda t: [2, -5 * wave(t, L), 0], "head": lambda t: [-1, 3 * wave(t, L), 0],
        "skirt_front": lambda t: [-6 * wave(t, L), 0, 0], "skirt_back": lambda t: [3 + 3 * abs(wave(t, L)), 0, 0],
        "left_arm": lambda t: [14 * wave(t, L), 0, 0], "right_arm": lambda t: [-3 * wave(t, L), 0, 0],
        "right_wing": lambda t: [3 * abs(wave(t, L)), 0, 2 * wave(t, L)], "left_wing": lambda t: [3 * abs(wave(t, L)), 0, 2 * wave(t, L)],
    }), 0.05)

    # kneeling on his right knee, his sword driven point down into the ground before him and both hands on its grip,
    # his wings drooping round him
    KNEEL = P(hips_pos=[0, -8.0, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0],
              skirt_front=[-34, 0, 0], skirt_front_scale=[1, 0.7, 1], skirt_back=[40, 0, 0], skirt_back_scale=[1, 0.7, 1],
              body=[14, 0, 0], head=[26, 0, 0], right_arm=[-58, 0, 14], right_hand=[-34, 0, 0], sword=[222, 0, -12],
              left_arm=[-56, 0, -12], left_hand=[-40, 0, 0], **wing_pose(0.35, up=-30, tip=40))
    L = 5.0
    A["dormant"] = anim(L, True, timeline([(0, KNEEL, lin), (L, KNEEL, lin)], extra={"body": lambda t: [0.8 * wave(t, L), 0, 0]}), 0.1)

    # cleave (1.5 s): the greatsword drawn level across his right and swept through his whole front, landing at 0.7 s
    L = 1.5
    LEVEL = dict(right_arm=[-36, 0, 8], right_hand=[-40, 0, 0], sword=[178, 0, -4], left_arm=[-50, 0, -6], left_hand=[-30, 0, 0])
    SW_A = P(**LEVEL, **HALF, body=[2, 54, 0], hips=[0, 16, 0], head=[0, -40, 0], hips_pos=[0, -1.0, 0], left_leg=[-14, 0, 0], right_leg=[8, 0, 0],
             skirt_front=[-6, 0, 0])
    SW_B = P(**LEVEL, **wing_pose(0.6, up=10, tip=60), body=[8, -60, 0], hips=[0, -18, 0], head=[0, 34, 0], hips_pos=[0, -1.6, -0.6],
             left_leg=[-22, 0, 0], left_shin=[16, 0, 0], right_leg=[16, 0, 0], skirt_front=[-12, 0, 0], skirt_back=[14, 0, 0])
    A["cleave"] = anim(L, False, timeline([(0, STAND, lin), (0.48, SW_A, ease_in_out), (0.72, SW_B, ease_in), (0.98, SW_B, lin),
                                           (1.5, STAND, ease_in_out)]), 0.025)

    # advance (1.7 s): drawn far back, the blade level at his hip and aimed down the strip; a lunge at 0.7 s that carries
    # him its length, held to 0.9 s
    L = 1.7
    DRAW = P(**HALF, body=[4, 26, 0], head=[0, -22, 0], hips=[0, 12, 0], hips_pos=[0, -1.0, 1.4], right_arm=[34, -24, 6], right_hand=[-64, 0, 0],
             sword=[118, 0, -6], left_arm=[-70, 0, -24], left_hand=[-10, 0, 0], left_leg=[-24, 0, 0], left_shin=[20, 0, 0], right_leg=[16, 0, 0],
             skirt_front=[-8, 0, 0])
    LUNGE = P(**wing_pose(0.7, up=-10, sweep=-20, tip=70), body=[18, -24, 0], head=[-12, 22, 0], hips_pos=[0, -3.2, -3.6], right_arm=[-70, 30, 4],
              right_hand=[-4, 0, 0], sword=[176, 0, -4], left_arm=[40, 0, -20], left_hand=[-10, 0, 0], left_leg=[-54, 0, 0], left_shin=[58, 0, 0],
              right_leg=[38, 0, 0], right_shin=[10, 0, 0], skirt_front=[-26, 0, 0], skirt_back=[24, 0, 0])
    A["advance"] = anim(L, False, timeline([(0, STAND, lin), (0.5, DRAW, ease_in_out), (0.68, DRAW, lin), (0.78, LUNGE, ease_in), (0.98, LUNGE, lin),
                                            (1.7, STAND, ease_in_out)],
                                           extra={"right_hand": lambda t: [shake(t, 0.3, 0.68, 2.5, 47), 0, 0]}), 0.025)

    # pillars (1.8 s): the sword lifted to the sky in his right hand, his left hand open over the land; the pillars fall
    # at 1.1 s and the blade flashes down to point at them
    L = 1.8
    SKY = P(**HIGH, right_arm=[-170, 0, 6], right_hand=[-6, 0, 0], sword=[10, 0, -4], left_arm=[-60, -20, -40], left_hand=[-10, 0, 0],
            body=[-8, 0, 0], head=[-24, 0, 0], hips_pos=[0, 0.4, 0])
    POINT = P(**HALF, right_arm=[-90, 10, 6], right_hand=[-10, 0, 0], sword=[110, 0, -4], left_arm=[-30, 0, -50], left_hand=[-10, 0, 0],
              body=[10, 0, 0], head=[8, 0, 0], hips_pos=[0, -1.0, 0])
    A["pillars"] = anim(L, False, timeline([(0, STAND, lin), (0.7, SKY, ease_in_out), (1.0, SKY, lin), (1.12, POINT, ease_in), (1.4, POINT, lin),
                                            (1.8, STAND, ease_in_out)],
                                           extra={"sword": lambda t: [shake(t, 0.7, 1.0, 2.0, 50), 0, 0]}), 0.025)

    # scales (2.6 s): his arms spread like the beam of a balance, the sword level in one hand and the other open (the
    # band falls at 1.0 s); then the sword's point driven into the ground at his feet (2.0 s)
    L = 2.6
    BALANCE = P(**wing_pose(0.8, up=20, tip=80), right_arm=[-14, -10, 84], right_hand=[-6, 0, 0], sword=[96, 0, -10], left_arm=[-14, 10, -84],
                left_hand=[-6, 0, 0], body=[-4, 0, 0], head=[-6, 0, 0])
    LIFT = P(**HALF, right_arm=[-150, 0, 8], right_hand=[-30, 0, 0], sword=[200, 0, -8], left_arm=[-150, 0, -8], left_hand=[-30, 0, 0],
             body=[-6, 0, 0], head=[-14, 0, 0])
    PLANT = P(**HALF, right_arm=[-46, 0, 12], right_hand=[-26, 0, 0], sword=[212, 0, -8], left_arm=[-60, 0, -6], left_hand=[-20, 0, 0],
              body=[16, 0, 0], head=[10, 0, 0], hips_pos=[0, -2.4, -0.4], left_leg=[-18, 0, 0], left_shin=[20, 0, 0], right_leg=[12, 0, 0],
              right_shin=[16, 0, 0], skirt_front=[-10, 0, 0])
    A["scales"] = anim(L, False, timeline([(0, STAND, lin), (0.6, BALANCE, ease_in_out), (1.0, BALANCE, lin), (1.6, LIFT, ease_in_out),
                                           (1.78, LIFT, lin), (2.0, PLANT, ease_in), (2.3, PLANT, lin), (2.6, STAND, ease_in_out)],
                                          extra={"right_arm": lambda t: [0, 0, 6 * bump(t, 0.95, 1.25)], "left_arm": lambda t: [0, 0, -6 * bump(t, 0.95, 1.25)],
                                                 "body": lambda t: [shake(t, 2.0, 2.3, 1.2, 50, 0.1), 0, 0]}), 0.025)

    # spears (2.3 s): with his left hand he makes a spear of light over his head and throws it, three times (0.5, 0.9,
    # 1.3 s); his sword stays at his side
    L = 2.3
    WIND = P(body=[-6, -18, 0], head=[-4, 14, 0], left_arm=[-160, 0, -10], left_hand=[-58, 0, 0], hips_pos=[0, -0.4, 0.6], left_leg=[-10, 0, 0],
             right_leg=[6, 0, 0], **wing_pose(0.3, up=20, tip=40))
    THROW = P(body=[12, 16, 0], head=[-8, -12, 0], left_arm=[-84, 0, -6], left_hand=[-8, 0, 0], hips_pos=[0, -1.0, -1.0], left_leg=[-20, 0, 0],
              left_shin=[12, 0, 0], right_leg=[12, 0, 0], skirt_front=[-10, 0, 0], **wing_pose(0.5, up=10, tip=60))
    steps = [(0, STAND, lin)]
    for rel in (0.5, 0.9, 1.3):
        steps += [(rel - 0.2, WIND, ease_in_out), (rel + 0.06, THROW, ease_in)]
    steps.append((2.3, STAND, ease_in_out))
    a = anim(L, False, timeline(steps, extra={"javelin": lambda t: [200, 0, 0]}), 0.025)

    def jav(t):
        on = max((window(t, rel - 0.36, rel - 0.22) if rel - 0.36 <= t < rel + 0.02 else 0.0) for rel in (0.5, 0.9, 1.3))
        return [on] * 3
    a["bones"]["javelin"]["scale"] = G.track(jav, L, 0.025)
    A["spears"] = a

    # descent (3.0 s): up on his wings (0.1-0.9 s: the boss itself rises), hanging there with the sword raised in both
    # hands, the dive (1.5-2.0 s) and the blade driven into the ground as he lands; he rises
    L = 3.0
    CROUCH = P(**HIGH, hips_pos=[0, -2.0, 0], body=[16, 0, 0], head=[10, 0, 0], left_leg=[-30, 0, 0], left_shin=[40, 0, 0], right_leg=[-30, 0, 0],
               right_shin=[40, 0, 0], right_arm=[10, 0, 14], left_arm=[10, 0, -14])
    RISE = P(**SPREAD, body=[-6, 0, 0], head=[-14, 0, 0], left_leg=[10, 0, 0], left_shin=[20, 0, 0], right_leg=[4, 0, 0], right_shin=[30, 0, 0],
             right_arm=[-30, 0, 30], left_arm=[-30, 0, -30], skirt_front=[10, 0, 0], skirt_back=[-10, 0, 0])
    HANG = P(**wing_pose(1.0, up=40), right_arm=[-176, 0, 4], right_hand=[-30, 0, 0], sword=[150, 0, -4], left_arm=[-176, 0, -4],
             left_hand=[-30, 0, 0], body=[-10, 0, 0], head=[16, 0, 0], left_leg=[-20, 0, 0], left_shin=[40, 0, 0], right_leg=[10, 0, 0],
             right_shin=[50, 0, 0])
    DIVE = P(**wing_pose(0.5, up=60, sweep=-30, tip=40), right_arm=[-120, 0, 4], right_hand=[-20, 0, 0], sword=[240, 0, -4], left_arm=[-120, 0, -4],
             left_hand=[-20, 0, 0], body=[30, 0, 0], head=[20, 0, 0], left_leg=[-40, 0, 0], left_shin=[60, 0, 0], right_leg=[-10, 0, 0],
             right_shin=[40, 0, 0], skirt_back=[30, 0, 0])
    STRIKE = P(KNEEL, **wing_pose(0.9, up=-10, tip=80), right_arm=[-60, 0, 10], right_hand=[-26, 0, 0], sword=[220, 0, -8], left_arm=[-70, 0, -6],
               left_hand=[-26, 0, 0], body=[24, 0, 0], head=[14, 0, 0])
    A["descent"] = anim(L, False, timeline([(0, STAND, lin), (0.1, CROUCH, ease_in_out), (0.3, RISE, ease_out_back), (0.9, P(RISE, body=[-2, 0, 0]), lin),
                                            (1.2, HANG, ease_in_out), (1.5, HANG, lin), (1.75, DIVE, ease_in), (2.0, STRIKE, ease_in),
                                            (2.4, STRIKE, lin), (3.0, STAND, ease_in_out)],
                                           extra=merge(wingbeat(0.4, 26, 16, on=lambda t: window(t, 0.3, 0.4) * (1 - window(t, 1.1, 1.2))),
                                                       {"body": lambda t: [shake(t, 2.0, 2.4, 1.4, 50, 0.1), 0, 0]})), 0.025)

    # final_verdict (7 s), his authority: the sword lifted to the sky (1.0 s), driven point down into the land before him
    # (1.25 s) and held there in both hands, wings spread wide, while the cross is cut (2.5 s) and turned (4.0 s); pulled
    # free, lifted and driven down again at his feet (5.5 s); he rises
    L = 7.0
    RAISE = P(**HIGH, right_arm=[-176, 0, 6], right_hand=[-10, 0, 0], sword=[10, 0, -4], left_arm=[-176, 0, -6], left_hand=[-10, 0, 0],
              body=[-10, 0, 0], head=[-26, 0, 0], hips_pos=[0, 0.6, 0])
    HOLD = P(**wing_pose(1.0, up=30, tip=100), right_arm=[-50, 0, 8], right_hand=[-30, 0, 0], sword=[214, 0, -6], left_arm=[-54, 0, -4],
             left_hand=[-28, 0, 0], body=[14, 0, 0], head=[-6, 0, 0], hips_pos=[0, -2.0, -0.4], left_leg=[-18, 0, 0], left_shin=[22, 0, 0],
             right_leg=[12, 0, 0], right_shin=[16, 0, 0], skirt_front=[-10, 0, 0])
    A["final_verdict"] = anim(L, False, timeline([(0, STAND, lin), (0.9, RAISE, ease_in_out), (1.05, RAISE, lin), (1.25, HOLD, ease_in),
                                                  (4.6, HOLD, lin), (5.15, RAISE, ease_in_out), (5.3, RAISE, lin), (5.5, STRIKE, ease_in),
                                                  (6.2, STRIKE, lin), (7.0, STAND, ease_in_out)],
                                                 extra={"body": lambda t: [shake(t, 1.25, 1.6, 1.4, 50, 0.1) + shake(t, 2.45, 2.75, 1.0, 50, 0.1)
                                                                           + shake(t, 3.95, 4.25, 1.0, 50, 0.1) + shake(t, 5.5, 5.9, 1.6, 50, 0.1), 0, 0],
                                                        "head": lambda t: [0, 18 * math.sin((t - 1.3) * 1.4) * window(t, 1.4, 1.8) * (1 - window(t, 4.3, 4.6)), 0],
                                                        "right_wing2": lambda t: [-6 * bump(t, 2.4, 2.8) - 6 * bump(t, 3.9, 4.3), 0, 0],
                                                        "left_wing2": lambda t: [-6 * bump(t, 2.4, 2.8) - 6 * bump(t, 3.9, 4.3), 0, 0]}), 0.025)

    # intro (5.5 s): down out of his rift (to 3.95 s) upright on spread wings, the sword point down; he lands on one knee
    # with the blade driven into the ground, then rises and points it at them
    L = 5.5
    DESCEND = P(**wing_pose(0.9, up=40, tip=95), head=[16, 0, 0], left_arm=[-14, 0, -40], left_hand=[-10, 0, 0], right_arm=[-14, 0, 22],
                right_hand=[-24, 0, 0], sword=[200, 0, -26], right_leg=[4, 0, 0], left_leg=[10, 0, 0], left_shin=[14, 0, 0], skirt_front=[-20, 0, 0],
                skirt_back=[28, 0, 0])
    LAND = P(STRIKE, head=[20, 0, 0])
    PRESENT = P(STAND, **HALF, right_arm=[-80, 20, 6], right_hand=[-10, 0, 0], sword=[100, 0, -6], head=[-6, 0, 0], body=[-4, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, DESCEND, lin), (3.6, DESCEND, lin), (3.95, LAND, ease_in), (4.4, LAND, lin), (4.95, PRESENT, ease_in_out),
                                          (5.2, PRESENT, lin), (5.5, STAND, ease_in_out)],
                                         extra=merge(wingbeat(1.2, 10, 8, on=lambda t: 1 - window(t, 3.5, 3.9)),
                                                     {"skirt_back": lambda t: [flutter(t + 0.3, 4) * (1 - window(t, 3.7, 3.95)), 0, 0],
                                                      "skirt_front": lambda t: [-flutter(t + 0.7, 3) * (1 - window(t, 3.7, 3.95)), 0, 0]})), 0.025)

    # phase2 (2.3 s): the sword raised, the wings thrown open; phase3: his head back, arms and wings wide, the light
    # pouring out of him
    L = 2.3
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.5, RAISE, ease_in_out), (0.7, P(RAISE, **SPREAD), ease_out_back), (1.6, P(RAISE, **SPREAD), lin),
                                           (2.3, STAND, ease_in_out)]), 0.025)
    WIDE = P(STAND, **wing_pose(1.0, up=50, tip=100), body=[-14, 0, 0], head=[-34, 0, 0], left_arm=[-20, 0, -86], left_hand=[0, 0, 0],
             right_arm=[-24, 0, 50], right_hand=[-10, 0, 0], sword=[30, 0, -52], skirt_back=[16, 0, 0], skirt_front=[-10, 0, 0], hips_pos=[0, 0.4, 0])
    GATHER = P(STAND, **wing_pose(0.2, up=-20, tip=30), body=[22, 0, 0], head=[30, 0, 0], left_arm=[-60, -30, 20], left_hand=[-50, 0, 0],
               right_arm=[-30, 0, 4], hips_pos=[0, -1.6, 0.4], right_leg=[10, 0, 0], left_leg=[-8, 0, 0], left_shin=[14, 0, 0])
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.6, GATHER, ease_in_out), (0.85, WIDE, ease_out_back), (1.8, WIDE, lin), (2.3, STAND, ease_in_out)],
                                          extra={"body": lambda t: [shake(t, 0.85, 1.8, 1.0, 45), 0, 0]}), 0.025)

    # death (5 s, held): struck, he staggers, sinks to his knee leaning on his sword, his wings fall round him
    L = 5.0
    STAGGER = P(STAND, **HALF, body=[-14, -8, 0], head=[-24, 0, 6], left_arm=[-50, 20, -10], left_hand=[-60, 0, 0], hips_pos=[0, 0, 1.0],
                right_leg=[-10, 0, 0], left_leg=[14, 0, 0], left_shin=[10, 0, 0])
    SLUMP = P(KNEEL, head=[40, 0, 8], body=[22, 0, 4], left_arm=[-4, 0, -4], left_hand=[-6, 0, 0], **wing_pose(0.6, up=-40, tip=30))
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.35, STAGGER, ease_out_back), (1.1, KNEEL, ease_in), (2.4, SLUMP, ease_in_out),
                                           (5.0, SLUMP, lin)]), 0.05)

    # leave (4.5 s): he lifts his face and rises back into the sky on beating wings
    L = 4.5
    ASCEND = P(**wing_pose(0.9, up=40, tip=95), head=[-26, 0, 0], body=[-4, 0, 0], left_arm=[-10, 0, -30], left_hand=[-10, 0, 0],
               right_arm=[-10, 0, 16], right_hand=[-24, 0, 0], sword=[34, 0, -22], right_leg=[6, 0, 0], left_leg=[10, 0, 0], left_shin=[12, 0, 0])
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.0, ASCEND, ease_in_out), (L, ASCEND, lin)],
                                         extra=wingbeat(0.9, 24, 14, on=lambda t: window(t, 0.8, 1.2))), 0.05)
    hide(A, "javelin", keep=("spears",))
    GC.write_anims(VERDICT, A)


# ============================================================================================= Caelistor, the Wings

WINGS = "ruler_wings"
G.MODEL_SCALE[WINGS] = 1.85
WINGS_LIGHT = (255, 240, 184)


def wings():
    """Caelistor, built 34 units tall to the top of her helm and drawn 1.85 times bigger (3.9 blocks, her hitbox 4.0).
    Bones: root, hips, skirt_front, skirt_back, legs and shins, body, head (turned to look) with hair (her hair down her
    back), arms and hands, lance (in her right hand), the great wings (right_wing and right_wing2; the same on the left)
    and the lesser wings under them (right_lwing and right_lwing2; the same on the left)."""
    B = OneSkin(WINGS, 7300)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    sky = dict(r="cloth", folds=1.4, marks=0.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17.8, 0], [
        box(0, 18.4, 0.0, 5.2, 2.4, 3.6, "plate", **LP, seed=10),
        box(0, 19.5, 0.0, 5.6, 0.8, 4.0, "trim", **TM),
        box(0, 19.5, -2.05, 1.1, 1.1, 0.4, "glow", pivot=[0, 19.5, -2.05], rot=[0, 0, 45]),
        # a skirt of white lames, shaped like folded feathers
        *[c(mx([0.2 + j * 1.2, 13.6 - j * 0.4, -2.3 + j * 0.5], [1.6, 4.4, 0.5], sx), [1.6, 4.4, 0.5], "metal", **FE, long="y", seed=12 + j * 2 + (sx > 0),
            pivot=[sx * (1.0 + j * 1.2), 18.0, -2.0 + j * 0.5], rot=[-6 - j * 2, -sx * j * 14, -sx * (6 + j * 6)]) for sx in (-1, 1) for j in range(3)],
    ])
    B.bone("skirt_front", "hips", [0, 18.6, -2.0], [
        c([-1.6, 3.0, -2.4], [3.2, 15.4, 0.5], "cloth", **sky, hem=1.4, glow_hem=2.0, band=(5.2, 6.0), seed=18, skip=("up", "down"),
          lines=[dict(pts=[(0.0, 15.6), (0.0, 9.4)], w=0.2, faces=("north",), halo=0.5, bright=0.85),
                 dict(pts=[(-0.9, 12.6), (0.0, 11.4), (0.9, 12.6)], w=0.18, faces=("north",), halo=0.4, bright=0.8)]),
    ], rot=[-4, 0, 0])
    B.bone("skirt_back", "hips", [0, 18.6, 1.8], [
        c([-2.6, 0.6, 1.6], [5.2, 18.0, 0.5], "cloth", **sky, hem=1.6, glow_hem=2.0, band=(4.0, 4.8), seed=19, skip=("up", "down")),
    ], rot=[8, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * 1.4
        B.bone(f"{side}_leg", "hips", [x, 17.4, 0.0], [
            col(x, 0.0, 9.4, 17.8, 2.3, 2.5, "plate", **LP, seed=20 + (sx > 0)),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [x, 9.4, 0.0], [
            box(x, 9.4, -1.25, 2.2, 1.8, 0.8, "plate", **PL, seed=22 + (sx > 0)),
            c([x - 0.2, 9.6, -1.9], [0.4, 3.4, 0.6], "metal", **FE, long="y", pivot=[x, 10.0, -1.7], rot=[-24, 0, -sx * 18]),
            col(x, 0.0, 1.3, 9.6, 2.2, 2.4, "plate", **PL, seed=24 + (sx > 0)),
            box(x, 0.7, -0.7, 2.3, 1.4, 3.8, "plate", **LP, seed=26 + (sx > 0)),
            box(x, 0.5, -2.6, 1.6, 1.0, 0.8, "trim", **TM),
            # wings of steel at her heels
            c([x + sx * 1.1 - 0.15, 1.2, 0.2], [0.3, 0.9, 2.6], "metal", **FE, long="z", pivot=[x + sx * 1.1, 1.6, 0.4], rot=[-30, 0, 0]),
        ])
    B.bone("body", "hips", [0, 19.8, 0.0], [
        box(0, 21.4, 0.0, 4.4, 3.2, 3.2, "plate", **PL, seed=30),
        box(0, 25.2, -0.1, 6.8, 5.0, 4.2, "plate", **PL, seed=31),
        *[c(mx([0.1, 22.8, -2.45], [3.2, 4.6, 0.6], sx), [3.2, 4.6, 0.6], "plate", **LP, seed=32 + (sx > 0), pivot=[0, 25.1, -2.1],
            rot=[0, -sx * 14, 0]) for sx in (-1, 1)],
        # a feather of gold over her heart, its shaft of light
        box(0, 25.0, -2.75, 1.6, 3.6, 0.3, "trim", **TM, pivot=[0, 25.0, -2.75], rot=[0, 0, 18],
            lines=[dict(pts=[(-0.5, 23.6), (0.5, 26.4)], w=0.24, faces=("north",), halo=0.6, bright=1.0)]),
        box(0, 28.2, -0.1, 4.2, 1.2, 3.6, "plate", **LP, seed=34),
        box(0, 25.6, 2.3, 5.2, 4.2, 0.7, "plate", **LP, seed=35),
    ])
    B.bone("head", "body", [0, 28.6, 0.0], [
        box(0, 31.0, -0.1, 3.8, 4.4, 4.0, "skin", r="skin", seed=40, mottle=0.04, fat=0.03, blotch=0.0, marks=0.0,
            eyes=GC.eyes(0.9, 31.3, (0.55, 0.2), slant=0.2, pupil=None, lid=1.0, glint=False),
            facepaint=[dict(pts=[(-0.4, 29.6), (0.4, 29.6)], w=0.2, ramp="lips", tone=0.45),
                       dict(pts=[(0.4, 31.9), (1.0, 32.05), (1.5, 32.15)], w=0.18, ramp="brow", tone=0.4),
                       dict(pts=[(-0.4, 31.9), (-1.0, 32.05), (-1.5, 32.15)], w=0.18, ramp="brow", tone=0.4)]),
        # a winged half-helm: a cap of white plate over her brow, a band of gold, wings of steel standing back from it
        box(0, 33.5, 0.2, 4.2, 1.0, 4.4, "plate", **PL, seed=41),
        box(0, 32.7, -2.15, 4.2, 0.5, 0.3, "trim", **TM),
        *[box(sx * 2.1, 32.0, 0.2, 0.3, 1.8, 4.4, "plate", **LP, seed=42 + (sx > 0)) for sx in (-1, 1)],
        box(0, 32.7, -2.35, 0.7, 0.7, 0.3, "glow", pivot=[0, 32.7, -2.35], rot=[0, 0, 45]),
        *[c([sx * 2.25 - 0.15, 32.4, -0.6 + j * 0.9], [0.3, h, 1.1], "metal", **FE, long="y", pivot=[sx * 2.25, 32.6, -0.1 + j * 0.9],
            rot=[rx, 0, -sx * 16]) for sx in (-1, 1) for j, (h, rx) in enumerate(((4.4, -54), (3.4, -70), (2.4, -84)))],
        # her hair: pale gold, framing her face
        box(0, 33.0, 2.3, 4.4, 2.6, 0.8, "mane", r="hair", seed=45, ragged=0.0, ends=False, marks=0.0),
        *[box(sx * 2.05, 30.4, 0.6, 0.5, 4.0, 3.2, "mane", r="hair", seed=46 + (sx > 0), ragged=1.0, marks=0.0, skip=("up",)) for sx in (-1, 1)],
    ])
    B.bone("hair", "head", [0, 32.8, 2.4], [
        c([-2.0, 20.6, 2.2], [4.0, 12.4, 1.0], "mane", r="hair", seed=48, ragged=1.6, len=3.0, width=0.8, marks=0.0, skip=("up",)),
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 3.9, 27.6, 0.0]
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 4.2, 0.0, 21.8, 27.6, 2.1, 2.3, "plate", **LP, seed=50 + (sx > 0)),
            # a pauldron of two plates and three steel feathers sweeping back from it
            c(mx([2.8, 26.6, -2.0], [3.0, 1.5, 4.0], sx), [3.0, 1.5, 4.0], "plate", **PL, seed=52 + (sx > 0), pivot=piv, rot=[0, 0, sx * 20]),
            c(mx([3.2, 25.5, -1.8], [2.6, 1.2, 3.6], sx), [2.6, 1.2, 3.6], "plate", **LP, seed=54 + (sx > 0), pivot=piv, rot=[0, 0, sx * 30]),
            c(mx([2.7, 26.4, -2.1], [3.2, 0.3, 4.2], sx), [3.2, 0.3, 4.2], "trim", **TM, pivot=piv, rot=[0, 0, sx * 20]),
            *[c([sx * (4.4 + j * 0.6) - 0.15, 27.6, -0.6 + j * 1.0], [0.3, 1.0, 3.6 - j * 0.6], "metal", **FE, long="z",
                pivot=[sx * (4.4 + j * 0.6), 28.0, -0.6 + j * 1.0], rot=[-30 - j * 8, 0, sx * 24]) for j in range(3)],
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.2, 22.0, 0.0], [
            col(sx * 4.2, 0.0, 16.8, 22.2, 2.0, 2.2, "plate", **PL, seed=56 + (sx > 0)),
            box(sx * 4.2, 18.2, 0.0, 2.5, 1.3, 2.7, "plate", **LP, seed=58 + (sx > 0)),
            box(sx * 4.2, 18.2, -1.42, 2.6, 0.4, 0.3, "trim", **TM),
            box(sx * 4.2, 15.8, -0.1, 1.8, 2.2, 2.0, "plate", **LP, seed=60 + (sx > 0)),
        ])
    # the lance: a long haft of white gold bound in gold, a cone of plate guarding her hand, a long blade of light
    hx, hz = -4.2, -0.1
    B.bone("lance", "right_hand", [hx, 15.8, hz], [
        col(hx, hz, -4.0, 30.0, 0.7, 0.7, "metal", r="haft", long="y", fuller=False, seed=70, marks=0.0),
        *[col(hx, hz, y, y + 0.5, 0.95, 0.95, "trim", **TM) for y in (6.0, 21.0, 29.6)],
        col(hx, hz, 12.6, 18.4, 0.85, 0.85, "leather", r="grip", stitch=False, seed=71, marks=0.0),
        col(hx, hz, 18.4, 19.4, 2.6, 2.6, "plate", **PL, seed=72),
        col(hx, hz, 19.4, 20.4, 1.8, 1.8, "trim", **TM),
        col(hx, hz, 30.0, 31.0, 1.5, 1.5, "trim", **TM),
        c([hx - 0.5, 31.0, hz - 0.5], [1.0, 9.0, 1.0], "crystal"),
        c([hx - 0.32, 40.0, hz - 0.32], [0.64, 3.0, 0.64], "crystal"),
        c([hx - 0.16, 43.0, hz - 0.16], [0.32, 1.6, 0.32], "crystal"),
        *[c([hx - 0.15, 31.0, hz - 0.3], [0.3, 3.2, 0.6], "crystal", pivot=[hx, 31.2, hz], rot=[0, 0, sz * 32]) for sz in (-1, 1)],
        c([hx - 0.4, -5.2, hz - 0.4], [0.8, 1.2, 0.8], "glow"),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        light_wing(B, side, sx, 100 if sx < 0 else 130, sx * 1.5, 27.4, 2.6, arm_len=11.0, inner=(8.0, 0.8), outer=(21.0, -1.2), width=2.0)
        light_wing(B, side, sx, 160 if sx < 0 else 190, sx * 1.3, 23.8, 2.4, arm_len=8.0, feathers=5, inner=(6.0, 0.6), outer=(14.0, -0.8),
                   width=1.6, name=f"{side}_lwing", rest=(22, 18, 0), rest2=-100)
    ruler_palette(WINGS, glow=WINGS_LIGHT, extra={
        "cloth": [(10, 22, 40), (30, 62, 104), (70, 120, 176), (130, 178, 224), (204, 230, 252)],
        "skin": [(70, 56, 46), (150, 126, 108), (214, 192, 172), (240, 226, 210), (255, 248, 238)],
        "lips": [(70, 40, 40), (120, 70, 70), (170, 108, 104), (206, 150, 142), (232, 192, 184)],
        "brow": [(50, 36, 14), (100, 76, 36), (156, 124, 70), (204, 176, 118), (236, 216, 170)],
    })
    B.write(7.0, 4.6, 1.6)
    wings_anims()


def wings_anims():
    """Times match WingsRuler and RulerBoss: intro 110 ticks (down out of her rift on her wings over the first 80,
    landing at 79), phase changes 46, death 100 (held), leaving 90. Moves: lance 26 (two thrusts landing at 12 and 22),
    wing_sweep 30 (her left wings sweep at 12, her right at 24), feather_storm 44 (the feathers thrown at 8, 16 and 24),
    dive 50 (up and back 2-14, the lance aimed down the strip, the swoop 24-32), gale 40 (the beat lands at 16),
    thousand_wings 150 (up 2-20, hanging there, the bands of wings cut at 52, 82 and 112, the volleys between; the
    dive 124-134)."""
    A = {}
    bones = ("root", "hips", "skirt_front", "skirt_back", "right_leg", "right_shin", "left_leg", "left_shin", "body", "head", "hair", "right_arm",
             "right_hand", "left_arm", "left_hand", "lance", "right_wing", "right_wing2", "left_wing", "left_wing2", "right_lwing", "right_lwing2",
             "left_lwing", "left_lwing2")
    BOTH = ("wing", "lwing")
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-14, 0, 10], right_hand=[-24, 0, 0], lance=[38, 0, -16],
                 left_arm=[-6, 0, -8], left_hand=[-12, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def four(spread=1.0, up=25.0, sweep=0.0, tip=105.0, low=None):
        """All four wings: the great pair as asked, the lesser pair a little lower and less open (or as low asks)."""
        out = wing_pose(spread, up, sweep, tip)
        lo = low or dict(spread=spread * 0.9, up=up - 30, sweep=sweep, tip=tip * 0.9)
        out.update(wing_pose(names=("lwing",), **lo))
        return out

    def side(sx_side, spread, up, sweep, tip):
        """One side's two wings (sx_side: -1 her right, 1 her left)."""
        out = {}
        name = "right" if sx_side < 0 else "left"
        for n, k in (("wing", 1.0), ("lwing", 0.85)):
            out[f"{name}_{n}"] = [-35 * spread * k, sx_side * (70 * spread * k + sweep), -sx_side * (up - (0 if n == "wing" else 25)) * spread * k]
            out[f"{name}_{n}2"] = [tip * spread * k, 0, 0]
        return out

    def hairflow(t, amp=4.0):
        return [flutter(t, amp, 7.0), 0, 0]

    OPEN = four(1.0)
    HALF = four(0.45, up=30, tip=50)

    L = 3.6
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.12 * wave(t, L), 0],
        "head": lambda t: [1.5 * wave(t, L, 0.3), 5 * wave(t, L * 2), 0], "hair": lambda t: [2 * wave(t, L, 0.2), 0, 0],
        "right_wing": lambda t: [2 * wave(t, L, 0.2), 0, 1.5 * wave(t, L)], "left_wing": lambda t: [2 * wave(t, L, 0.2), 0, -1.5 * wave(t, L)],
        "right_lwing": lambda t: [2 * wave(t, L, 0.5), 0, 1.5 * wave(t, L, 0.3)], "left_lwing": lambda t: [2 * wave(t, L, 0.5), 0, -1.5 * wave(t, L, 0.3)],
        "right_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0], "left_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0],
        "skirt_back": lambda t: [1.2 * wave(t, L, 0.1), 0, 0], "left_hand": lambda t: [3 * wave(t, L, 0.5), 0, 0],
    }), 0.05)

    # walk (1.2 s): a light stride, her wings half open as if she might lift off
    L = 1.2
    A["walk"] = anim(L, True, timeline([(0, P(**four(0.15, up=10, tip=20)), lin), (L, P(**four(0.15, up=10, tip=20)), lin)], extra={
        "right_leg": lambda t: [26 * wave(t, L), 0, 0], "left_leg": lambda t: [-26 * wave(t, L), 0, 0],
        "right_shin": lambda t: [28 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [28 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips": lambda t: [0, 5 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.35 * abs(wave(t, L)), 0],
        "body": lambda t: [3, -6 * wave(t, L), 0], "head": lambda t: [-2, 4 * wave(t, L), 0], "hair": lambda t: [6 + 3 * abs(wave(t, L)), 0, 0],
        "skirt_front": lambda t: [-6 * wave(t, L), 0, 0], "skirt_back": lambda t: [4 + 3 * abs(wave(t, L)), 0, 0],
        "left_arm": lambda t: [16 * wave(t, L), 0, 0], "right_arm": lambda t: [-4 * wave(t, L), 0, 0],
        "right_wing": lambda t: [3 * abs(wave(t, L)), 0, 3 * wave(t, L)], "left_wing": lambda t: [3 * abs(wave(t, L)), 0, 3 * wave(t, L)],
    }), 0.05)

    # kneeling on her right knee, her lance planted, all four wings folded down round her like a cloak
    KNEEL = P(hips_pos=[0, -8.0, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0],
              skirt_front=[-34, 0, 0], skirt_front_scale=[1, 0.7, 1], skirt_back=[40, 0, 0], skirt_back_scale=[1, 0.7, 1],
              body=[14, 0, 0], head=[26, 0, 0], hair=[-10, 0, 0], right_arm=[-40, 0, 10], right_hand=[-30, 0, 0], lance=[56, 0, -14],
              left_arm=[-18, 0, -6], left_hand=[-30, 0, 0], **four(0.4, up=-40, sweep=30, tip=30))
    L = 5.0
    A["dormant"] = anim(L, True, timeline([(0, KNEEL, lin), (L, KNEEL, lin)], extra={"body": lambda t: [0.8 * wave(t, L), 0, 0]}), 0.1)

    # lance (1.3 s): drawn back level at her hip, driven forward (0.6 s), drawn and driven again (1.1 s)
    L = 1.3
    DRAW = P(**HALF, body=[4, 24, 0], head=[0, -20, 0], hips=[0, 10, 0], hips_pos=[0, -0.6, 1.0], right_arm=[30, -22, 6], right_hand=[-62, 0, 0],
             lance=[122, 0, -6], left_arm=[-60, 0, -12], left_hand=[-20, 0, 0], left_leg=[-18, 0, 0], left_shin=[14, 0, 0], right_leg=[10, 0, 0])
    THRUST = P(**four(0.6, up=0, sweep=-20, tip=60), body=[10, -22, 0], head=[-6, 20, 0], hips=[0, -10, 0], hips_pos=[0, -1.4, -2.4],
               right_arm=[-76, 30, 4], right_hand=[-6, 0, 0], lance=[172, 0, -4], left_arm=[30, 0, -16], left_hand=[-10, 0, 0], left_leg=[-34, 0, 0],
               left_shin=[30, 0, 0], right_leg=[24, 0, 0], right_shin=[6, 0, 0], skirt_front=[-14, 0, 0], skirt_back=[16, 0, 0], hair=[14, 0, 0])
    A["lance"] = anim(L, False, timeline([(0, STAND, lin), (0.42, DRAW, ease_in_out), (0.6, THRUST, ease_in), (0.72, THRUST, lin),
                                          (0.92, DRAW, ease_in_out), (1.1, THRUST, ease_in), (1.18, THRUST, lin), (1.3, STAND, ease_in_out)]), 0.025)

    # wing_sweep (1.5 s): her left wings swept round across her front (0.6 s), then her right (1.2 s), her body turning
    # with each
    L = 1.5
    LEFT_BACK = P(**side(1, 1.0, 30, -40, 100), **side(-1, 0.3, 10, 0, 40), body=[0, 26, 0], head=[0, -20, 0], hips=[0, 8, 0])
    LEFT_SWEPT = P(**side(1, 0.9, 10, 60, 90), **side(-1, 0.3, 10, 0, 40), body=[6, -30, 0], head=[0, 24, 0], hips=[0, -10, 0], hips_pos=[0, -1.0, 0],
                   left_leg=[-16, 0, 0], right_leg=[10, 0, 0], hair=[10, 0, 0])
    RIGHT_BACK = P(**side(-1, 1.0, 30, -40, 100), **side(1, 0.4, 10, 20, 50), body=[0, -26, 0], head=[0, 20, 0], hips=[0, -8, 0])
    RIGHT_SWEPT = P(**side(-1, 0.9, 10, 60, 90), **side(1, 0.4, 10, 20, 50), body=[6, 30, 0], head=[0, -24, 0], hips=[0, 10, 0], hips_pos=[0, -1.0, 0],
                    left_leg=[10, 0, 0], right_leg=[-16, 0, 0], hair=[10, 0, 0])
    A["wing_sweep"] = anim(L, False, timeline([(0, STAND, lin), (0.42, LEFT_BACK, ease_in_out), (0.62, LEFT_SWEPT, ease_in), (0.76, LEFT_SWEPT, lin),
                                               (1.02, RIGHT_BACK, ease_in_out), (1.22, RIGHT_SWEPT, ease_in), (1.34, RIGHT_SWEPT, lin),
                                               (1.5, STAND, ease_in_out)]), 0.025)

    # feather_storm (2.2 s): her wings thrown open and flung forward three times (0.4, 0.8, 1.2 s), loosing feathers
    L = 2.2
    BACK = P(**four(1.0, up=50, sweep=-30, tip=100), body=[-10, 0, 0], head=[-14, 0, 0], left_arm=[-30, 0, -40], hair=[-6, 0, 0])
    FLING = P(**four(0.9, up=10, sweep=50, tip=80), body=[14, 0, 0], head=[6, 0, 0], left_arm=[-80, 0, -10], left_hand=[-10, 0, 0],
              hips_pos=[0, -1.0, -0.6], hair=[16, 0, 0])
    steps = [(0, STAND, lin)]
    for rel in (0.4, 0.8, 1.2):
        steps += [(rel - 0.2, BACK, ease_in_out), (rel, FLING, ease_in)]
    steps.append((2.2, STAND, ease_in_out))
    A["feather_storm"] = anim(L, False, timeline(steps), 0.025)

    # dive (2.5 s): up and back on beating wings (0.1-0.7 s: the boss itself flies), the lance turned point down at
    # her prey, the swoop down the strip (1.2-1.6 s), wings swept back, and a landing on one knee; she rises
    L = 2.5
    CROUCH = P(**four(0.9, up=60, tip=90), hips_pos=[0, -2.0, 0], body=[16, 0, 0], head=[10, 0, 0], left_leg=[-30, 0, 0], left_shin=[40, 0, 0],
               right_leg=[-30, 0, 0], right_shin=[40, 0, 0])
    UP = P(**OPEN, body=[-8, 0, 0], head=[-10, 0, 0], left_leg=[10, 0, 0], left_shin=[24, 0, 0], right_leg=[4, 0, 0], right_shin=[30, 0, 0],
           skirt_front=[10, 0, 0], skirt_back=[-6, 0, 0], hair=[-10, 0, 0])
    AIM = P(UP, **four(1.0, up=40, tip=100), right_arm=[-110, 20, 6], right_hand=[-20, 0, 0], lance=[200, 0, -6], body=[20, 0, 0], head=[16, 0, 0],
            left_arm=[-40, 0, -50])
    SWOOP = P(**four(0.6, up=40, sweep=-60, tip=40), right_arm=[-120, 20, 6], right_hand=[-10, 0, 0], lance=[196, 0, -6], body=[40, 0, 0],
              head=[30, 0, 0], left_arm=[20, 0, -30], left_leg=[20, 0, 0], left_shin=[30, 0, 0], right_leg=[30, 0, 0], right_shin=[40, 0, 0],
              hair=[40, 0, 0], skirt_back=[40, 0, 0], skirt_front=[30, 0, 0])
    LAND = P(KNEEL, **four(0.9, up=-10, tip=80), right_arm=[-70, 10, 8], right_hand=[-20, 0, 0], lance=[190, 0, -8], head=[10, 0, 0], hair=[20, 0, 0])
    A["dive"] = anim(L, False, timeline([(0, STAND, lin), (0.1, CROUCH, ease_in_out), (0.3, UP, ease_out_back), (0.7, UP, lin), (0.95, AIM, ease_in_out),
                                         (1.2, AIM, lin), (1.4, SWOOP, ease_in), (1.6, LAND, ease_in), (1.95, LAND, lin), (2.5, STAND, ease_in_out)],
                                        extra=merge(wingbeat(0.35, 30, 18, on=lambda t: window(t, 0.25, 0.35) * (1 - window(t, 0.8, 0.95)), names=BOTH),
                                                    {"body": lambda t: [shake(t, 1.6, 1.9, 1.4, 50, 0.1), 0, 0]})), 0.025)

    # gale (2 s): all four wings lifted high and back (0.6 s) and brought down in one great beat (0.8 s) that throws the
    # air ahead of her
    L = 2.0
    LIFTED = P(**four(1.0, up=75, sweep=-30, tip=100), body=[-12, 0, 0], head=[-10, 0, 0], left_arm=[-20, 0, -30], hips_pos=[0, 0.6, 0],
               hair=[-8, 0, 0])
    BEAT = P(**four(0.95, up=-25, sweep=40, tip=95), body=[18, 0, 0], head=[8, 0, 0], left_arm=[-60, 0, -20], hips_pos=[0, -1.6, -0.6],
             left_leg=[-18, 0, 0], left_shin=[16, 0, 0], right_leg=[10, 0, 0], hair=[24, 0, 0], skirt_back=[20, 0, 0], skirt_front=[-14, 0, 0])
    A["gale"] = anim(L, False, timeline([(0, STAND, lin), (0.6, LIFTED, ease_in_out), (0.8, BEAT, ease_in), (1.3, BEAT, lin), (2.0, STAND, ease_in_out)],
                                        extra={"body": lambda t: [shake(t, 0.8, 1.2, 1.0, 45), 0, 0]}), 0.025)

    # thousand_wings (7.5 s), her authority: up into the sky on all four wings (0.1-1.0 s: the boss flies), hanging there
    # with her wings beating, flung wide each time a band of wings is cut (1.2, 2.7 and 4.2 s), feathers loosed between;
    # the lance raised (5.6 s), the dive (6.2-6.7 s) and the landing; she rises
    L = 7.5
    HOVER = P(**four(1.0, up=35, tip=100), body=[-4, 0, 0], head=[8, 0, 0], left_arm=[-30, 0, -40], left_hand=[-10, 0, 0], left_leg=[-10, 0, 0],
              left_shin=[30, 0, 0], right_leg=[8, 0, 0], right_shin=[36, 0, 0], skirt_front=[6, 0, 0], skirt_back=[-6, 0, 0])
    CUT = P(HOVER, **four(1.0, up=-10, sweep=50, tip=100), body=[12, 0, 0], left_arm=[-90, 0, -60], hair=[14, 0, 0])
    RAISED = P(HOVER, right_arm=[-176, 0, 6], right_hand=[-10, 0, 0], lance=[10, 0, -6], head=[-20, 0, 0], body=[-8, 0, 0])
    steps = [(0, STAND, lin), (0.1, CROUCH, ease_in_out), (0.4, UP, ease_out_back), (1.0, HOVER, ease_in_out)]
    for at in (1.2, 2.7, 4.2):
        steps += [(at, HOVER, lin), (at + 0.2, CUT, ease_in), (at + 0.5, CUT, lin), (at + 0.9, HOVER, ease_in_out)]
    steps += [(5.3, HOVER, lin), (5.7, RAISED, ease_in_out), (6.1, RAISED, lin), (6.3, AIM, ease_in_out), (6.5, SWOOP, ease_in), (6.7, LAND, ease_in),
              (7.0, LAND, lin), (7.5, STAND, ease_in_out)]
    A["thousand_wings"] = anim(L, False, timeline(steps, extra=merge(
        wingbeat(0.6, 18, 12, on=lambda t: window(t, 0.9, 1.1) * (1 - window(t, 6.0, 6.2)), names=BOTH),
        {"hair": lambda t: hairflow(t, 4) if 1.0 <= t <= 6.2 else [0, 0, 0],
         "body": lambda t: [shake(t, 6.7, 7.0, 1.4, 50, 0.1), 0, 0]})), 0.025)

    # intro (5.5 s): down out of her rift on beating wings (to 3.95 s), a landing on one knee, rising with the lance
    # pointed at them
    L = 5.5
    DESCEND = P(HOVER, head=[16, 0, 0], left_arm=[-14, 0, -40], right_arm=[-14, 0, 22], right_hand=[-24, 0, 0], lance=[38, 0, -26])
    PRESENT = P(STAND, **HALF, right_arm=[-80, 20, 6], right_hand=[-10, 0, 0], lance=[100, 0, -6], head=[-6, 0, 0], body=[-4, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, DESCEND, lin), (3.6, DESCEND, lin), (3.95, LAND, ease_in), (4.4, LAND, lin), (4.95, PRESENT, ease_in_out),
                                          (5.2, PRESENT, lin), (5.5, STAND, ease_in_out)],
                                         extra=merge(wingbeat(0.9, 18, 12, on=lambda t: 1 - window(t, 3.5, 3.9), names=BOTH),
                                                     {"hair": lambda t: hairflow(t, 5) if t < 3.9 else [0, 0, 0],
                                                      "skirt_back": lambda t: [flutter(t + 0.3, 4) * (1 - window(t, 3.7, 3.95)), 0, 0]})), 0.025)

    # phase2 (2.3 s): the lance raised and all four wings thrown open; phase3: her head back, arms and wings wide
    L = 2.3
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.5, RAISED, ease_in_out), (0.7, P(RAISED, **OPEN, hips_pos=[0, 0, 0], body=[0, 0, 0]),
                                                                                           ease_out_back),
                                           (1.6, P(RAISED, **OPEN, hips_pos=[0, 0, 0], body=[0, 0, 0]), lin), (2.3, STAND, ease_in_out)]), 0.025)
    WIDE = P(STAND, **four(1.0, up=55, tip=100), body=[-14, 0, 0], head=[-34, 0, 0], left_arm=[-20, 0, -86], left_hand=[0, 0, 0],
             right_arm=[-24, 0, 50], right_hand=[-10, 0, 0], lance=[30, 0, -52], hair=[-14, 0, 0])
    GATHER = P(STAND, **four(0.25, up=-20, sweep=40, tip=30), body=[22, 0, 0], head=[30, 0, 0], left_arm=[-60, -30, 20], left_hand=[-50, 0, 0],
               right_arm=[-30, 0, 4], hips_pos=[0, -1.6, 0.4])
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.6, GATHER, ease_in_out), (0.85, WIDE, ease_out_back), (1.8, WIDE, lin), (2.3, STAND, ease_in_out)],
                                          extra={"body": lambda t: [shake(t, 0.85, 1.8, 1.0, 45), 0, 0]}), 0.025)

    # death (5 s, held): she falls to her knees, her wings sinking round her
    L = 5.0
    STAGGER = P(STAND, **HALF, body=[-14, -8, 0], head=[-24, 0, 6], left_arm=[-50, 20, -10], left_hand=[-60, 0, 0], hips_pos=[0, 0, 1.0])
    SLUMP = P(KNEEL, head=[40, 0, 8], body=[22, 0, 4], left_arm=[-4, 0, -4], left_hand=[-6, 0, 0], right_arm=[-46, 0, 14], lance=[62, 0, -18],
              **four(0.7, up=-50, sweep=20, tip=20))
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.35, STAGGER, ease_out_back), (1.1, KNEEL, ease_in), (2.4, SLUMP, ease_in_out),
                                           (5.0, SLUMP, lin)]), 0.05)

    # leave (4.5 s): up into the sky on beating wings
    L = 4.5
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.0, P(DESCEND, head=[-26, 0, 0]), ease_in_out), (L, P(DESCEND, head=[-26, 0, 0]), lin)],
                                         extra=merge(wingbeat(0.8, 26, 14, on=lambda t: window(t, 0.8, 1.2), names=BOTH),
                                                     {"hair": lambda t: hairflow(t, 4) if t > 1.0 else [0, 0, 0]})), 0.05)
    GC.write_anims(WINGS, A)


# ============================================================================================= Ithrael, the Unseen Hand

HAND = "ruler_hand"
G.MODEL_SCALE[HAND] = 1.95
HAND_LIGHT = (246, 226, 168)


class _Scaled:
    """Cubes for a part built at k times its size round a point (its pivot): sizes, origins, pivots and paint lines
    scale with it."""

    def __init__(self, B, at, k):
        self.B, self.at, self.k = B, at, k

    def p(self, v):
        return [a + (b - a) * self.k for a, b in zip(self.at, v)]

    def c(self, o, sz, mat, pivot=None, **kw):
        return self.B.c(self.p(o), [v * self.k for v in sz], mat, pivot=self.p(pivot) if pivot else None, **kw)

    def box(self, cx, cy, cz, w, h, d, mat, **kw):
        return self.c([cx - w / 2, cy - h / 2, cz - d / 2], [w, h, d], mat, **kw)


def gauntlet(B, side, sx, seed, k=1.4):
    """One of his hands: a gauntlet of white gold that floats beside him, open, its fingers hanging (one bone for the
    four of them, {side}_fingers, curled to make a fist), a cuff open at the wrist with nothing in it, an eye of light on
    its back. Built at k times a man's hand."""
    x, y, z = sx * 11.0, 21.0, -1.0
    cubes = _Scaled(B, (x, y, z), k)
    B.bone(f"{side}_gauntlet", "root", [x, y, z], [
        # the cuff: four plates round an empty wrist, rimmed in gold
        cubes.box(x, y + 3.2, z, 3.6, 3.0, 0.5, "plate", **PL, seed=seed),
        cubes.box(x, y + 3.2, z + 2.8, 3.6, 3.0, 0.5, "plate", **LP, seed=seed + 1),
        *[cubes.box(x + k * 1.65, y + 3.2, z + 1.4, 0.5, 3.0, 2.8, "plate", **LP, seed=seed + 2 + (k > 0)) for k in (-1, 1)],
        cubes.box(x, y + 4.8, z + 1.4, 4.0, 0.4, 3.6, "trim", **TM),
        cubes.box(x, y + 1.6, z + 1.4, 3.8, 0.4, 3.4, "trim", **TM),
        # the back of the hand and the palm, the eye of light on its back (towards the outside)
        cubes.box(x, y - 0.4, z + 1.4, 3.6, 3.6, 2.4, "plate", **PL, seed=seed + 4,
              lines=[dict(pts=[(z + 0.6 * k, y - 0.4 * k), (z + 2.2 * k, y - 0.4 * k)], w=0.3 * k, faces=("west", "east"), halo=0.7, bright=1.0)]),
        cubes.box(x + sx * 1.25, y - 0.4, z + 1.4, 0.3, 1.2, 1.2, "glow", pivot=[x + sx * 1.25, y - 0.4, z + 1.4], rot=[45, 0, 0]),
        # the thumb, standing forward off the palm
        cubes.c([x - 0.5, y - 2.4, z - 1.2], [1.0, 3.0, 1.0], "plate", **LP, seed=seed + 5, pivot=[x, y - 0.4, z - 0.2], rot=[30, 0, 0]),
        cubes.c([x - 0.4, y - 3.6, z - 2.6], [0.8, 1.8, 0.8], "trim", **TM, pivot=[x, y - 0.4, z - 0.2], rot=[30, 0, 0]),
    ])
    fingers = []
    for i, zz in enumerate((-0.15, 0.75, 1.65, 2.55)):
        ln = (3.4, 3.8, 3.6, 3.0)[i]
        fingers += [cubes.c([x - 0.45, y - 2.2 - ln, z + zz - 0.38], [0.9, ln, 0.76], "plate", **LP, seed=seed + 6 + i),
                    cubes.c([x - 0.35, y - 2.2 - ln - 1.6, z + zz - 0.3], [0.7, 1.6, 0.6], "trim", **TM)]
    B.bone(f"{side}_fingers", f"{side}_gauntlet", cubes.p([x, y - 2.2, z + 1.2]), fingers)


def hand():
    """Ithrael, built 36 units tall to the top of his hood and drawn 1.95 times bigger (4.4 blocks, his hitbox). Bones:
    root, hips (his robes' sides), robe_front and robe_back (their panels, swinging as he glides), legs and shins (under
    the robe), body with mantle (the collar of plates on his shoulders), head (hood and mask), halo (the ring of plates
    turning behind his head), arms and hands (thin, in wide sleeves), and his great hands: right_gauntlet with
    right_fingers, left_gauntlet with left_fingers, floating beside him."""
    B = OneSkin(HAND, 7500)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    robe = dict(r="cloth", folds=1.6, marks=0.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 18.0, 0], [
        box(0, 19.0, 0.0, 5.4, 2.4, 4.0, "cloth", **robe, seed=10),
        box(0, 20.2, 0.0, 5.8, 0.8, 4.4, "trim", **TM),
        box(0, 20.2, -2.25, 1.2, 1.2, 0.4, "glow", pivot=[0, 20.2, -2.25], rot=[0, 0, 45]),
        # the robe's sides, falling to the ground and flaring out
        *[c(mx([2.4, 0.4, -2.2], [0.7, 19.6, 4.6], sx), [0.7, 19.6, 4.6], "cloth", **robe, hem=1.8, glow_hem=2.0, seed=12 + (sx > 0),
            skip=("up", "down"), pivot=[sx * 2.8, 19.8, 0.0], rot=[0, 0, -sx * 7]) for sx in (-1, 1)],
        # the dark of his under-robe between its panels
        box(0, 9.6, 0.0, 4.6, 18.0, 3.4, "cloth", r="inner", folds=1.0, marks=0.0, seed=14, skip=("up",)),
    ])
    B.bone("robe_front", "hips", [0, 19.6, -2.2], [
        c([-2.6, 0.4, -2.6], [5.2, 19.2, 0.5], "cloth", **robe, hem=2.0, glow_hem=2.4, band=(5.0, 5.8), seed=16, skip=("up", "down"),
          lines=[dict(pts=[(-1.4, 17.8), (-1.4, 3.0)], w=0.18, faces=("north",), halo=0.4, bright=0.75),
                 dict(pts=[(1.4, 17.8), (1.4, 3.0)], w=0.18, faces=("north",), halo=0.4, bright=0.75)]),
    ], rot=[-3, 0, 0])
    B.bone("robe_back", "hips", [0, 19.6, 2.0], [
        c([-2.8, 0.2, 1.8], [5.6, 19.4, 0.5], "cloth", **robe, hem=2.0, glow_hem=2.4, band=(4.6, 5.4), seed=17, skip=("up", "down")),
    ], rot=[5, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * 1.3
        B.bone(f"{side}_leg", "hips", [x, 18.0, 0.0], [col(x, 0.0, 9.6, 18.0, 2.0, 2.2, "cloth", r="inner", folds=0.8, marks=0.0, seed=20 + (sx > 0))])
        B.bone(f"{side}_shin", f"{side}_leg", [x, 9.6, 0.0], [
            col(x, 0.0, 1.0, 9.8, 1.8, 2.0, "cloth", r="inner", folds=0.8, marks=0.0, seed=22 + (sx > 0)),
            box(x, 0.5, -0.5, 2.0, 1.0, 3.2, "trim", **TM),
        ])
    B.bone("body", "hips", [0, 20.4, 0.0], [
        box(0, 22.6, 0.0, 4.8, 4.2, 3.6, "cloth", **robe, seed=30),
        box(0, 26.6, -0.1, 6.4, 4.4, 4.0, "cloth", **robe, seed=31),
        # the stole: a band of gold down his front, runes of light on it
        box(0, 24.6, -2.15, 1.6, 8.0, 0.3, "trim", **TM, runes=dict(region=(-0.6, 21.0, 0.6, 28.2), faces=("north",), seed=33, cell=1.0, density=0.6)),
        box(0, 29.2, -0.1, 4.6, 1.0, 3.8, "cloth", **robe, seed=34),
    ])
    B.bone("mantle", "body", [0, 28.6, 0.0], [
        # a collar of overlapping gold-rimmed plates on his shoulders, standing up behind his hood
        *[c(mx([0.6 + j * 1.6, 27.0 - j * 0.6, -2.4], [2.2, 2.8, 4.8], sx), [2.2, 2.8, 4.8], "plate", **PL, seed=36 + j * 2 + (sx > 0),
            pivot=[sx * (1.4 + j * 1.6), 29.2, 0.0], rot=[0, 0, sx * (10 + j * 14)]) for sx in (-1, 1) for j in range(3)],
        box(0, 30.6, 2.2, 6.0, 3.4, 0.6, "plate", **PL, seed=42, pivot=[0, 29.0, 2.2], rot=[-14, 0, 0]),
        box(0, 32.2, 2.1, 6.2, 0.4, 0.8, "trim", **TM, pivot=[0, 29.0, 2.2], rot=[-14, 0, 0]),
    ])
    B.bone("head", "body", [0, 29.6, 0.0], [
        # the mask: smooth gold, one line of light down it and a stone of light on its brow
        box(0, 32.2, -0.6, 3.4, 4.2, 3.4, "plate", r="mask", rivets=False, marks=0.0, dents=False, grime=False, seed=40,
            lines=[dict(pts=[(0.0, 32.9), (0.0, 30.4)], w=0.3, faces=("north",), halo=0.7, bright=1.0)]),
        box(0, 33.5, -2.35, 0.8, 0.8, 0.3, "glow", pivot=[0, 33.5, -2.35], rot=[0, 0, 45]),
        # the hood over it, deep and white, its rim in gold
        box(0, 35.0, 0.2, 4.6, 1.4, 5.0, "cloth", **robe, seed=41),
        *[box(sx * 2.15, 32.0, 0.4, 0.5, 5.0, 4.6, "cloth", **robe, seed=42 + (sx > 0)) for sx in (-1, 1)],
        box(0, 32.4, 2.6, 4.6, 6.0, 0.6, "cloth", **robe, seed=44),
        box(0, 35.0, -2.35, 4.8, 0.5, 0.4, "trim", **TM),
        *[box(sx * 2.45, 32.0, -1.9, 0.3, 5.4, 0.4, "trim", **TM) for sx in (-1, 1)],
        box(0, 36.0, 0.6, 1.0, 0.7, 4.2, "trim", **TM),
    ])
    halo = []
    for i in range(10):
        a = math.radians(i * 36)
        hx, hy = math.sin(a) * 5.0, 33.0 + math.cos(a) * 5.0
        halo += [B.box(hx, hy, 3.6, 1.6, 2.4, 0.4, "plate", **PL, seed=50 + i, pivot=[hx, hy, 3.6], rot=[0, 0, -i * 36]),
                 B.box(hx, hy, 3.35, 0.5, 0.5, 0.2, "glow", pivot=[hx, hy, 3.35], rot=[0, 0, 45 - i * 36])]
    halo += [B.box(0, 33.0, 3.7, 1.2, 1.2, 0.3, "trim", **TM, pivot=[0, 33.0, 3.7], rot=[0, 0, 45])]
    B.bone("halo", "body", [0, 33.0, 3.6], halo)
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 3.6, 28.0, 0.0]
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 3.8, 0.0, 22.6, 28.4, 2.0, 2.2, "cloth", **robe, seed=60 + (sx > 0)),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 3.8, 22.8, 0.0], [
            # a wide sleeve falling from the elbow, and a thin hand in a glove of gold
            c(mx([2.4, 15.6, -1.6], [2.8, 7.4, 3.2], sx), [2.8, 7.4, 3.2], "cloth", **robe, hem=1.0, glow_hem=1.2, seed=62 + (sx > 0), skip=("up",)),
            box(sx * 3.8, 14.6, -0.2, 1.2, 2.2, 1.4, "trim", **TM),
        ])
    gauntlet(B, "right", -1, 80)
    gauntlet(B, "left", 1, 100)
    ruler_palette(HAND, glow=HAND_LIGHT, extra={
        "cloth": [(40, 36, 30), (120, 112, 98), (196, 188, 170), (236, 232, 220), (255, 254, 248)],
        "inner": [(6, 6, 14), (16, 16, 34), (32, 30, 62), (58, 54, 100), (96, 92, 150)],
    })
    B.write(8.0, 5.0, 1.6)
    hand_anims()


def hand_anims():
    """Times match HandRuler and RulerBoss: intro 110 ticks (down out of his rift over the first 80, touching down at
    79), phase changes 46, death 100 (held), leaving 90. Moves: grasp 40 (the hand closes on them at 20, slams them down
    at 32), crush 36 (the square falls at 18), hurl 46 (stones thrown at 12, 20 and 28), repel 30 (the blast at 14), pull
    44 (the far ring at 16, the ground at his feet at 36), unseen_dominion 140 (the ground at 40, the rings at 55, 70,
    85 and 100, the last blow round him at 120). His gauntlets do the work: positions are in model units (x left, y up,
    z back)."""
    A = {}
    bones = ("root", "hips", "robe_front", "robe_back", "right_leg", "right_shin", "left_leg", "left_shin", "body", "mantle", "head", "halo",
             "right_arm", "right_hand", "left_arm", "left_hand", "right_gauntlet", "right_fingers", "left_gauntlet", "left_fingers")
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-8, 0, 6], right_hand=[-30, 0, 0], left_arm=[-8, 0, -6], left_hand=[-30, 0, 0],
                 right_gauntlet=[0, 0, -6], left_gauntlet=[0, 0, 6], right_fingers=[0, 0, 0], left_fingers=[0, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    FIST = 100  # fingers curled into a fist (x)

    def gaunt(side, pos, rot=(0, 0, 0), fist=0.0):
        """A gauntlet moved to pos (model units from where it floats), turned by rot, its fingers curled by fist (0-1)."""
        return {f"{side}_gauntlet_pos": list(pos), f"{side}_gauntlet": list(rot), f"{side}_fingers": [FIST * fist, 0, 0]}

    def bob(t, L, ph=0.0, amp=0.6):
        return [0, amp * wave(t, L, ph), 0]

    def spin(t, rate=24.0):
        return [0, 0, rate * t]

    def float_extra(L, amp=0.6):
        return {"right_gauntlet_pos": lambda t: bob(t, L, 0.0, amp), "left_gauntlet_pos": lambda t: bob(t, L, 0.5, amp)}

    L = 4.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra=merge(float_extra(L), {
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.12 * wave(t, L), 0],
        "head": lambda t: [1.5 * wave(t, L, 0.3), 5 * wave(t, L * 2), 0], "halo": lambda t: [0, 0, 90 * t / L],
        "robe_front": lambda t: [1.0 * wave(t, L, 0.4), 0, 0], "robe_back": lambda t: [1.2 * wave(t, L, 0.1), 0, 0],
        "right_gauntlet": lambda t: [3 * wave(t, L, 0.2), 0, 2 * wave(t, L)], "left_gauntlet": lambda t: [3 * wave(t, L, 0.7), 0, -2 * wave(t, L)],
        "right_fingers": lambda t: [6 + 4 * wave(t, L, 0.3), 0, 0], "left_fingers": lambda t: [6 + 4 * wave(t, L, 0.8), 0, 0],
    })), 0.05)

    # walk (1.6 s): he glides more than walks, his robes swaying, his hands keeping pace beside him
    L = 1.6
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra=merge(float_extra(L, 0.8), {
        "right_leg": lambda t: [14 * wave(t, L), 0, 0], "left_leg": lambda t: [-14 * wave(t, L), 0, 0],
        "right_shin": lambda t: [14 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [14 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips_pos": lambda t: [0, -0.2 * abs(wave(t, L)), 0], "body": lambda t: [3, -3 * wave(t, L), 0],
        "robe_front": lambda t: [-5 * wave(t, L) - 2, 0, 0], "robe_back": lambda t: [5 + 3 * abs(wave(t, L)), 0, 0],
        "halo": lambda t: [0, 0, 90 * t / L], "right_gauntlet": lambda t: [-8, 0, 0], "left_gauntlet": lambda t: [-8, 0, 0],
    })), 0.05)

    # kneeling, his head bowed, his hands lying open on the ground before him
    KNEEL = P(hips_pos=[0, -8.0, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0],
              robe_front=[-36, 0, 0], robe_front_scale=[1, 0.62, 1], robe_back=[40, 0, 0], robe_back_scale=[1, 0.62, 1], body=[16, 0, 0], head=[26, 0, 0],
              right_arm=[-20, 0, 6], left_arm=[-20, 0, -6], **gaunt("right", (3, -14, -8), (-80, 0, -10)), **gaunt("left", (-3, -14, -8), (-80, 0, 10)))
    L = 5.0
    A["dormant"] = anim(L, True, timeline([(0, KNEEL, lin), (L, KNEEL, lin)], extra={"body": lambda t: [0.8 * wave(t, L), 0, 0],
                                                                                    "halo": lambda t: [0, 0, 36 * t / L]}), 0.1)

    # grasp (2 s): his right hand reaches for them (the great one flies out, open, 0-1.0 s), closes (1.0 s), lifts them
    # (to 1.5 s) and slams them down (1.6 s)
    L = 2.0
    REACH = P(right_arm=[-84, 20, 6], right_hand=[-6, 0, 0], body=[6, -14, 0], head=[4, 10, 0], **gaunt("right", (4, 2, -26), (-90, 0, 0), 0.0))
    CLOSED = P(REACH, **gaunt("right", (4, 2, -26), (-90, 0, 0), 1.0))
    LIFTED = P(right_arm=[-150, 20, 6], right_hand=[-30, 0, 0], body=[-8, -10, 0], head=[-14, 8, 0], **gaunt("right", (4, 18, -22), (-120, 0, 0), 1.0))
    SLAMMED = P(right_arm=[-40, 20, 6], right_hand=[-20, 0, 0], body=[16, -10, 0], head=[10, 8, 0], hips_pos=[0, -1.4, 0],
                **gaunt("right", (4, -14, -24), (-10, 0, 0), 1.0))
    A["grasp"] = anim(L, False, timeline([(0, STAND, lin), (0.85, REACH, ease_in_out), (1.0, CLOSED, ease_in), (1.12, CLOSED, lin), (1.45, LIFTED, ease_in_out),
                                          (1.62, SLAMMED, ease_in), (1.75, SLAMMED, lin), (2.0, STAND, ease_in_out)],
                                         extra={"right_hand": lambda t: [0, 0, 0], "halo": lambda t: [0, 0, 60 * t],
                                                "left_gauntlet_pos": lambda t: bob(t, 2.0, 0.5)}), 0.025)

    # crush (1.8 s): both hands lifted high before him (0.7 s) and brought down together on the square (0.9 s)
    L = 1.8
    HIGH = P(right_arm=[-150, 0, 10], right_hand=[-20, 0, 0], left_arm=[-150, 0, -10], left_hand=[-20, 0, 0], body=[-8, 0, 0], head=[-10, 0, 0],
             **gaunt("right", (5, 20, -20), (-150, 0, 0), 1.0), **gaunt("left", (-5, 20, -20), (-150, 0, 0), 1.0))
    DOWN = P(right_arm=[-60, 0, 10], right_hand=[-20, 0, 0], left_arm=[-60, 0, -10], left_hand=[-20, 0, 0], body=[18, 0, 0], head=[10, 0, 0],
             hips_pos=[0, -1.6, 0], **gaunt("right", (6, -14, -28), (-20, 0, 0), 1.0), **gaunt("left", (-6, -14, -28), (-20, 0, 0), 1.0))
    A["crush"] = anim(L, False, timeline([(0, STAND, lin), (0.65, HIGH, ease_in_out), (0.78, HIGH, lin), (0.92, DOWN, ease_in), (1.25, DOWN, lin),
                                          (1.8, STAND, ease_in_out)], extra={"halo": lambda t: [0, 0, 60 * t],
                                                                             "body": lambda t: [shake(t, 0.92, 1.25, 1.2, 50, 0.1), 0, 0]}), 0.025)

    # hurl (2.3 s): stones torn up and thrown, right, left, right (0.6, 1.0, 1.4 s): each hand drawn back high and
    # flung forward
    L = 2.3
    R_BACK = P(right_arm=[-120, -20, 20], right_hand=[-40, 0, 0], body=[-4, 20, 0], head=[0, -14, 0], **gaunt("right", (-2, 14, 10), (-160, 0, 0), 0.7))
    R_THROW = P(right_arm=[-80, 20, 6], right_hand=[-6, 0, 0], body=[8, -18, 0], head=[0, 12, 0], **gaunt("right", (4, 4, -24), (-80, 0, 0), 0.0))
    L_BACK = P(left_arm=[-120, 20, -20], left_hand=[-40, 0, 0], body=[-4, -20, 0], head=[0, 14, 0], **gaunt("left", (2, 14, 10), (-160, 0, 0), 0.7))
    L_THROW = P(left_arm=[-80, -20, -6], left_hand=[-6, 0, 0], body=[8, 18, 0], head=[0, -12, 0], **gaunt("left", (-4, 4, -24), (-80, 0, 0), 0.0))
    A["hurl"] = anim(L, False, timeline([(0, STAND, lin), (0.4, R_BACK, ease_in_out), (0.6, R_THROW, ease_in), (0.8, L_BACK, ease_in_out),
                                         (1.0, L_THROW, ease_in), (1.2, R_BACK, ease_in_out), (1.4, R_THROW, ease_in), (1.7, R_THROW, lin),
                                         (2.3, STAND, ease_in_out)], extra={"halo": lambda t: [0, 0, 60 * t]}), 0.025)

    # repel (1.5 s): his hands drawn in to his chest (0.5 s) and thrown out from him (0.7 s), the air with them
    L = 1.5
    IN = P(right_arm=[-60, 30, 10], right_hand=[-70, 0, 0], left_arm=[-60, -30, -10], left_hand=[-70, 0, 0], body=[10, 0, 0], head=[14, 0, 0],
           hips_pos=[0, -0.8, 0.4], **gaunt("right", (8, 2, -6), (-60, 0, 0), 0.5), **gaunt("left", (-8, 2, -6), (-60, 0, 0), 0.5))
    OUT = P(right_arm=[-70, -20, 50], right_hand=[-6, 0, 0], left_arm=[-70, 20, -50], left_hand=[-6, 0, 0], body=[-8, 0, 0], head=[-14, 0, 0],
            **gaunt("right", (-10, 4, -16), (-90, 0, -40), 0.0), **gaunt("left", (10, 4, -16), (-90, 0, 40), 0.0))
    A["repel"] = anim(L, False, timeline([(0, STAND, lin), (0.5, IN, ease_in_out), (0.7, OUT, ease_out_back), (1.0, OUT, lin), (1.5, STAND, ease_in_out)],
                                         extra={"halo": lambda t: [0, 0, 120 * t], "body": lambda t: [shake(t, 0.7, 1.0, 1.0, 45), 0, 0]}), 0.025)

    # pull (2.2 s): both hands flung far out (0.6 s), clenched on the far ring (0.8 s) and dragged back to him (to 1.3
    # s), lifted (1.6 s) and brought down at his feet (1.8 s)
    L = 2.2
    FAR = P(right_arm=[-90, 10, 30], right_hand=[-4, 0, 0], left_arm=[-90, -10, -30], left_hand=[-4, 0, 0], body=[8, 0, 0],
            **gaunt("right", (-10, 4, -34), (-90, 0, 0), 0.0), **gaunt("left", (10, 4, -34), (-90, 0, 0), 0.0))
    CLENCH = P(FAR, **gaunt("right", (-10, 4, -34), (-90, 0, 0), 1.0), **gaunt("left", (10, 4, -34), (-90, 0, 0), 1.0))
    DRAWN = P(right_arm=[-50, 30, 10], right_hand=[-80, 0, 0], left_arm=[-50, -30, -10], left_hand=[-80, 0, 0], body=[-10, 0, 0], head=[-8, 0, 0],
              hips_pos=[0, 0, 0.8], **gaunt("right", (4, 6, -8), (-60, 0, 0), 1.0), **gaunt("left", (-4, 6, -8), (-60, 0, 0), 1.0))
    UPH = P(HIGH, **gaunt("right", (4, 18, -10), (-150, 0, 0), 1.0), **gaunt("left", (-4, 18, -10), (-150, 0, 0), 1.0))
    FEET = P(DOWN, **gaunt("right", (4, -14, -12), (-20, 0, 0), 1.0), **gaunt("left", (-4, -14, -12), (-20, 0, 0), 1.0))
    A["pull"] = anim(L, False, timeline([(0, STAND, lin), (0.6, FAR, ease_out_back), (0.8, CLENCH, ease_in), (1.3, DRAWN, ease_in_out),
                                         (1.6, UPH, ease_in_out), (1.8, FEET, ease_in), (2.0, FEET, lin), (2.2, STAND, ease_in_out)],
                                        extra={"halo": lambda t: [0, 0, 80 * t], "body": lambda t: [shake(t, 1.8, 2.0, 1.2, 50, 0.1), 0, 0]}), 0.025)

    # unseen_dominion (7 s), his authority: he rises off the ground, arms wide, his hands high over the land (to 1.5 s);
    # they press down as each ring falls (2.0, 2.75, 3.5, 4.25, 5.0 s), lift once more (5.6 s) and come down together
    # round him (6.0 s); he settles
    L = 7.0
    RISEN = P(root_pos=[0, 4, 0], right_arm=[-30, 0, 70], right_hand=[-6, 0, 0], left_arm=[-30, 0, -70], left_hand=[-6, 0, 0], body=[-6, 0, 0],
              head=[-16, 0, 0], robe_front=[8, 0, 0], robe_back=[-4, 0, 0], **gaunt("right", (-6, 22, -6), (-170, 0, 0), 0.0),
              **gaunt("left", (6, 22, -6), (-170, 0, 0), 0.0))
    PRESS = P(RISEN, right_arm=[-30, 0, 40], left_arm=[-30, 0, -40], head=[6, 0, 0], body=[8, 0, 0], **gaunt("right", (-6, 6, -10), (-180, 0, 0), 0.3),
              **gaunt("left", (6, 6, -10), (-180, 0, 0), 0.3))
    FINAL = P(DOWN, root_pos=[0, 0, 0], **gaunt("right", (-2, -14, -6), (0, 0, 0), 1.0), **gaunt("left", (2, -14, -6), (0, 0, 0), 1.0))
    steps = [(0, STAND, lin), (1.5, RISEN, ease_in_out)]
    for at in (2.0, 2.75, 3.5, 4.25, 5.0):
        steps += [(at - 0.25, RISEN, ease_in_out), (at, PRESS, ease_in)]
    steps += [(5.6, P(RISEN, **gaunt("right", (-4, 26, -6), (-170, 0, 0), 1.0), **gaunt("left", (4, 26, -6), (-170, 0, 0), 1.0)), ease_in_out),
              (6.0, FINAL, ease_in), (6.4, FINAL, lin), (7.0, STAND, ease_in_out)]
    A["unseen_dominion"] = anim(L, False, timeline(steps, extra={"halo": lambda t: [0, 0, 40 * t + 120 * window(t, 1.0, 6.0)],
                                                                 "body": lambda t: [shake(t, 6.0, 6.4, 1.4, 50, 0.1), 0, 0]}), 0.025)

    # intro (5.5 s): down out of his rift upright, arms open, his hands drifting beside him; they strike the ground as
    # he touches down (3.95 s); he straightens and turns his mask on them
    L = 5.5
    DESCEND = P(right_arm=[-20, 0, 40], left_arm=[-20, 0, -40], head=[16, 0, 0], robe_front=[-10, 0, 0], robe_back=[20, 0, 0],
                **gaunt("right", (-4, 8, 0), (-30, 0, -20), 0.0), **gaunt("left", (4, 8, 0), (-30, 0, 20), 0.0))
    LAND = P(FINAL, hips_pos=[0, -1.0, 0], head=[16, 0, 0])
    PRESENT = P(STAND, head=[-8, 0, 0], right_arm=[-70, 20, 10], right_hand=[-10, 0, 0], **gaunt("right", (0, 6, -14), (-90, 0, 0), 0.0))
    A["intro"] = anim(L, False, timeline([(0, DESCEND, lin), (3.6, DESCEND, lin), (3.95, LAND, ease_in), (4.4, LAND, lin), (4.95, PRESENT, ease_in_out),
                                          (5.2, PRESENT, lin), (5.5, STAND, ease_in_out)],
                                         extra={"robe_back": lambda t: [flutter(t + 0.3, 4) * (1 - window(t, 3.7, 3.95)), 0, 0],
                                                "robe_front": lambda t: [-flutter(t + 0.7, 3) * (1 - window(t, 3.7, 3.95)), 0, 0],
                                                "halo": lambda t: [0, 0, 30 * t]}), 0.025)

    # phase2 (2.3 s): his hands brought together before him and torn apart; phase3: he rises, arms wide, the halo spinning
    L = 2.3
    TOGETHER = P(right_arm=[-80, 30, 6], left_arm=[-80, -30, -6], **gaunt("right", (8, 6, -12), (-90, 0, 0), 1.0), **gaunt("left", (-8, 6, -12), (-90, 0, 0), 1.0))
    APART = P(OUT, **gaunt("right", (-12, 10, -8), (-110, 0, -50), 0.0), **gaunt("left", (12, 10, -8), (-110, 0, 50), 0.0))
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.5, TOGETHER, ease_in_out), (0.8, TOGETHER, lin), (1.0, APART, ease_out_back), (1.7, APART, lin),
                                           (2.3, STAND, ease_in_out)], extra={"halo": lambda t: [0, 0, 120 * t]}), 0.025)
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.8, RISEN, ease_in_out), (1.8, RISEN, lin), (2.3, STAND, ease_in_out)],
                                          extra={"halo": lambda t: [0, 0, 360 * ease_in_out(window(t, 0.4, 2.0))],
                                                 "body": lambda t: [shake(t, 0.8, 1.8, 1.0, 45), 0, 0]}), 0.025)

    # death (5 s, held): his hands fall to the ground, he sinks to his knees, the halo slows and tilts
    L = 5.0
    STAGGER = P(STAND, body=[-14, -8, 0], head=[-24, 0, 6], hips_pos=[0, 0, 1.0], **gaunt("right", (0, -6, 0), (20, 0, -30), 0.4),
                **gaunt("left", (0, -6, 0), (20, 0, 30), 0.4))
    SLUMP = P(KNEEL, head=[40, 0, 8], body=[22, 0, 4], halo=[20, 0, 0], **gaunt("right", (4, -16, -6), (-90, 0, -40), 0.2),
              **gaunt("left", (-4, -16, -6), (-90, 0, 40), 0.2))
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.35, STAGGER, ease_out_back), (1.1, KNEEL, ease_in), (2.4, SLUMP, ease_in_out),
                                           (5.0, SLUMP, lin)]), 0.05)

    # leave (4.5 s): he rises into the sky, arms open, his hands with him
    L = 4.5
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.0, P(DESCEND, head=[-26, 0, 0]), ease_in_out), (L, P(DESCEND, head=[-26, 0, 0]), lin)],
                                         extra={"halo": lambda t: [0, 0, 60 * t]}), 0.05)
    GC.write_anims(HAND, A)


# ============================================================================================= the heralds

HERALD = "ruler_herald"
G.MODEL_SCALE[HERALD] = 1.38


def herald():
    """A herald, built 30 units tall to the top of its helm (its crown of light to 35) and drawn 1.38 times bigger (2.6
    blocks, its hitbox). Bones: root, hips, legs and shins, body, head (turned to look), arms and hands, spear (in its
    right hand), small wings (right_wing and right_wing2; the same on the left)."""
    B = OneSkin(HERALD, 7700)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15.0, 0], [
        box(0, 15.6, 0.0, 5.0, 2.2, 3.4, "plate", **LP, seed=10),
        box(0, 16.6, 0.0, 5.4, 0.7, 3.8, "trim", **TM),
        c([-1.5, 5.6, -2.0], [3.0, 10.6, 0.4], "cloth", **GOWN, hem=1.0, glow_hem=1.4, seed=12, skip=("up", "down")),
        c([-2.2, 6.0, 1.6], [4.4, 10.2, 0.4], "cloth", **GOWN, hem=1.0, glow_hem=1.4, seed=13, skip=("up", "down")),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * 1.3
        B.bone(f"{side}_leg", "hips", [x, 14.6, 0.0], [col(x, 0.0, 8.0, 15.0, 2.2, 2.4, "plate", **LP, seed=20 + (sx > 0))])
        B.bone(f"{side}_shin", f"{side}_leg", [x, 8.0, 0.0], [
            box(x, 8.0, -1.2, 2.0, 1.8, 0.8, "plate", **PL, seed=22 + (sx > 0)),
            col(x, 0.0, 1.2, 8.2, 2.0, 2.2, "plate", **PL, seed=24 + (sx > 0)),
            box(x, 0.6, -0.6, 2.2, 1.2, 3.4, "plate", **LP, seed=26 + (sx > 0)),
        ])
    B.bone("body", "hips", [0, 16.8, 0.0], [
        box(0, 18.4, 0.0, 4.4, 3.0, 3.0, "plate", **PL, seed=30),
        box(0, 21.6, -0.1, 6.2, 4.2, 3.8, "plate", **PL, seed=31,
            lines=[dict(pts=[(-1.4, 23.0), (0.0, 21.2), (1.4, 23.0)], w=0.26, faces=("north",), halo=0.6, bright=1.0),
                   dict(pts=[(0.0, 20.4), (0.0, 20.41)], w=0.4, faces=("north",), halo=0.6, bright=1.0)]),
        box(0, 24.2, -0.1, 4.0, 1.0, 3.4, "plate", **LP, seed=34),
    ])
    spikes = []
    for i, a in enumerate(range(-60, 61, 20)):
        h = 3.2 + (1.4 if i % 2 == 0 else 0.0)
        spikes += crystal_spike(B, math.sin(math.radians(a)) * 1.5, 29.2 + math.cos(math.radians(a)) * 0.4, 0.6, 0.5, h, rx=-10, rz=a, d=0.4)
    B.bone("head", "body", [0, 24.6, 0.0], [
        # a smooth helm with no face, one line of light across where the eyes would be
        box(0, 27.0, -0.1, 3.8, 4.4, 4.0, "plate", **PL, seed=40,
            lines=[dict(pts=[(-1.3, 27.4), (1.3, 27.4)], w=0.26, faces=("north",), halo=0.7, bright=1.0)]),
        box(0, 29.0, -0.1, 4.0, 0.5, 4.2, "trim", **TM),
        *spikes,
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 3.6, 23.6, 0.0]
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 3.9, 0.0, 18.8, 23.6, 1.9, 2.1, "plate", **LP, seed=50 + (sx > 0)),
            c(mx([2.6, 22.8, -1.8], [2.6, 1.4, 3.6], sx), [2.6, 1.4, 3.6], "plate", **PL, seed=52 + (sx > 0), pivot=piv, rot=[0, 0, sx * 20]),
            c(mx([2.5, 22.6, -1.9], [2.8, 0.3, 3.8], sx), [2.8, 0.3, 3.8], "trim", **TM, pivot=piv, rot=[0, 0, sx * 20]),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 3.9, 19.0, 0.0], [
            col(sx * 3.9, 0.0, 14.6, 19.2, 1.8, 2.0, "plate", **PL, seed=56 + (sx > 0)),
            box(sx * 3.9, 13.8, -0.1, 1.6, 1.8, 1.8, "plate", **LP, seed=58 + (sx > 0)),
        ])
    hx, hz = -3.9, -0.1
    B.bone("spear", "right_hand", [hx, 13.8, hz], [
        col(hx, hz, -2.0, 24.0, 0.6, 0.6, "metal", r="haft", long="y", fuller=False, seed=70, marks=0.0),
        *[col(hx, hz, y, y + 0.5, 0.85, 0.85, "trim", **TM) for y in (6.0, 23.6)],
        c([hx - 0.25, 24.0, hz - 0.8], [0.5, 5.0, 1.6], "crystal"),
        c([hx - 0.18, 29.0, hz - 0.5], [0.36, 1.8, 1.0], "crystal"),
        c([hx - 0.1, 30.8, hz - 0.2], [0.2, 0.8, 0.4], "crystal"),
    ])
    light_wing(B, "right", -1, 100, -1.2, 23.0, 1.9, arm_len=7.0, feathers=5, inner=(5.0, 0.5), outer=(11.0, -0.6), width=1.4)
    light_wing(B, "left", 1, 130, 1.2, 23.0, 1.9, arm_len=7.0, feathers=5, inner=(5.0, 0.5), outer=(11.0, -0.6), width=1.4)
    ruler_palette(HERALD, extra={"cloth": [(6, 10, 26), (16, 28, 64), (34, 56, 118), (70, 100, 168), (130, 160, 214)]})
    B.write(3.6, 2.8, 1.2)
    herald_anims()


def herald_anims():
    """Its loops (idle, walk), its blow (attack: the thrust lands at 0.25 s) and its death (1 s, held: it falls to its
    knees, the light going out of it as the body fades)."""
    A = {}
    bones = ("root", "hips", "right_leg", "right_shin", "left_leg", "left_shin", "body", "head", "right_arm", "right_hand", "left_arm", "left_hand",
             "spear", "right_wing", "right_wing2", "left_wing", "left_wing2")
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-14, 0, 8], right_hand=[-24, 0, 0], spear=[38, 0, -12], left_arm=[-6, 0, -8],
                 left_hand=[-12, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    L = 2.4
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "head": lambda t: [1.5 * wave(t, L, 0.3), 4 * wave(t, L * 2), 0],
        "right_wing": lambda t: [3 * wave(t, L, 0.2), 0, 2 * wave(t, L)], "left_wing": lambda t: [3 * wave(t, L, 0.2), 0, -2 * wave(t, L)],
    }), 0.05)
    L = 1.0
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [28 * wave(t, L), 0, 0], "left_leg": lambda t: [-28 * wave(t, L), 0, 0],
        "right_shin": lambda t: [30 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [30 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips_pos": lambda t: [0, -0.4 * abs(wave(t, L)), 0], "body": lambda t: [4, -5 * wave(t, L), 0], "head": lambda t: [-2, 4 * wave(t, L), 0],
        "left_arm": lambda t: [18 * wave(t, L), 0, 0], "right_wing": lambda t: [4 * abs(wave(t, L)), 0, 3 * wave(t, L)],
        "left_wing": lambda t: [4 * abs(wave(t, L)), 0, 3 * wave(t, L)],
    }), 0.05)
    L = 0.6
    DRAW = P(body=[4, 20, 0], right_arm=[30, -20, 6], right_hand=[-60, 0, 0], spear=[120, 0, -6], left_arm=[-50, 0, -10], **wing_pose(0.4, up=30, tip=40))
    THRUST = P(body=[10, -20, 0], hips_pos=[0, -1.0, -1.6], right_arm=[-76, 28, 4], right_hand=[-6, 0, 0], spear=[172, 0, -4], left_arm=[30, 0, -14],
               left_leg=[-30, 0, 0], left_shin=[26, 0, 0], right_leg=[20, 0, 0], **wing_pose(0.6, up=0, sweep=-20, tip=60))
    A["attack"] = anim(L, False, timeline([(0, STAND, lin), (0.15, DRAW, ease_in_out), (0.25, THRUST, ease_in), (0.36, THRUST, lin),
                                           (0.6, STAND, ease_in_out)]), 0.025)
    L = 1.0
    KNEEL = P(hips_pos=[0, -6.6, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0], body=[30, 0, 0],
              head=[36, 0, 0], right_arm=[-10, 0, 20], right_hand=[-10, 0, 0], spear=[90, 0, -40], left_arm=[-6, 0, -16],
              **wing_pose(0.6, up=-40, tip=20))
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.2, P(STAND, body=[-16, 0, 0], head=[-26, 0, 0], **wing_pose(0.6, up=40, tip=60)), ease_out_back),
                                           (0.6, KNEEL, ease_in), (1.0, KNEEL, lin)]), 0.025)
    GC.write_anims(HERALD, A)


# ============================================================================================= the mote and the blessing

def mote_icon(n=64):
    """The mote of radiance: a drop of white-gold light with a four-pointed star standing out of it and a faint ring
    round it. Two layers: the drop's body (lit by the world) and its light (drawn full-bright)."""
    ss = 4
    w = n * ss
    base = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    db, dg = ImageDraw.Draw(base), ImageDraw.Draw(glow)
    cx, cy = w / 2, w / 2 + 2 * ss

    def star(d, r_long, r_short, color, rot=0.0, points=4):
        pts = []
        for i in range(points * 2):
            a = rot + i * math.pi / points
            r = r_long if i % 2 == 0 else r_short
            pts.append((cx + math.sin(a) * r, cy - math.cos(a) * r))
        d.polygon(pts, fill=color)

    # the ring of gold round it, with notches like the rim of a seal
    db.ellipse([cx - 25 * ss, cy - 25 * ss, cx + 25 * ss, cy + 25 * ss], outline=(150, 108, 34, 255), width=int(3.2 * ss))
    db.ellipse([cx - 24 * ss, cy - 24 * ss, cx + 24 * ss, cy + 24 * ss], outline=(232, 196, 110, 255), width=int(1.4 * ss))
    for i in range(8):
        a = i * math.pi / 4 + math.pi / 8
        x, y = cx + math.sin(a) * 25 * ss, cy - math.cos(a) * 25 * ss
        db.ellipse([x - 2.2 * ss, y - 2.2 * ss, x + 2.2 * ss, y + 2.2 * ss], fill=(246, 222, 150, 255))
    # the star: long points up, down and across, short ones between
    star(db, 27 * ss, 6 * ss, (196, 150, 60, 255))
    star(db, 23 * ss, 4.6 * ss, (250, 230, 170, 255))
    star(db, 15 * ss, 4 * ss, (232, 196, 110, 255), rot=math.pi / 4)
    # the drop itself
    db.ellipse([cx - 11 * ss, cy - 11 * ss, cx + 11 * ss, cy + 11 * ss], fill=(255, 236, 178, 255))
    db.polygon([(cx - 9.5 * ss, cy - 5 * ss), (cx, cy - 22 * ss), (cx + 9.5 * ss, cy - 5 * ss)], fill=(255, 236, 178, 255))
    # its light: the drop's heart and the star's points, bright
    dg.ellipse([cx - 8 * ss, cy - 8 * ss, cx + 8 * ss, cy + 8 * ss], fill=(255, 250, 226, 255))
    dg.polygon([(cx - 6.5 * ss, cy - 4 * ss), (cx, cy - 18 * ss), (cx + 6.5 * ss, cy - 4 * ss)], fill=(255, 250, 226, 255))
    star(dg, 22 * ss, 1.6 * ss, (255, 244, 200, 255))
    dg.ellipse([cx - 3 * ss, cy - 3 * ss, cx + 3 * ss, cy + 3 * ss], fill=(255, 255, 255, 255))
    base = base.resize((n, n), Image.LANCZOS)
    glow = glow.resize((n, n), Image.LANCZOS)
    # a dark outline so it reads on any slot
    a = base.split()[3].point(lambda v: 255 if v > 40 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    edge = Image.new("RGBA", (n, n), (40, 26, 8, 255))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(edge, (0, 0), grown)
    out.alpha_composite(base)
    return out, glow


def blessing_icon(n=18):
    """The Blessing of Light's icon: a small sun of gold with a white heart."""
    ss = 8
    w = n * ss
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = w / 2
    for i in range(8):
        a = i * math.pi / 4
        r0, r1, half = 4.2 * ss, (8.6 if i % 2 == 0 else 7.0) * ss, (1.2 if i % 2 == 0 else 0.9) * ss
        ux, uy = math.sin(a), -math.cos(a)
        px, py = -uy, ux
        d.polygon([(c + ux * r0 + px * half, c + uy * r0 + py * half), (c + ux * r1, c + uy * r1), (c + ux * r0 - px * half, c + uy * r0 - py * half)],
                  fill=(244, 196, 84, 255))
    d.ellipse([c - 4.6 * ss, c - 4.6 * ss, c + 4.6 * ss, c + 4.6 * ss], fill=(222, 162, 52, 255))
    d.ellipse([c - 3.6 * ss, c - 3.6 * ss, c + 3.6 * ss, c + 3.6 * ss], fill=(255, 232, 150, 255))
    d.ellipse([c - 2.0 * ss, c - 2.0 * ss, c + 2.0 * ss, c + 2.0 * ss], fill=(255, 255, 240, 255))
    im = im.resize((n, n), Image.LANCZOS)
    a = im.split()[3].point(lambda v: 255 if v > 50 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (n, n), (60, 36, 6, 255)), (0, 0), grown)
    out.alpha_composite(im)
    return out


def items():
    item_tex = os.path.join(ASSETS, "textures", "item")
    item_models = os.path.join(ASSETS, "models", "item")
    effects = os.path.join(ASSETS, "textures", "mob_effect")
    for d in (item_tex, item_models, effects):
        os.makedirs(d, exist_ok=True)
    base, glow = mote_icon()
    base.save(os.path.join(item_tex, "mote_of_radiance.png"), optimize=True)
    glow.save(os.path.join(item_tex, "mote_of_radiance_light.png"), optimize=True)
    model = {"parent": "minecraft:item/generated", "loader": "neoforge:item_layers",
             "textures": {"layer0": "beyondsolo:item/mote_of_radiance", "layer1": "beyondsolo:item/mote_of_radiance_light"},
             "neoforge_data": {"layers": {"1": {"block_light": 15, "sky_light": 15}}}}
    import json
    with open(os.path.join(item_models, "mote_of_radiance.json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    blessing_icon().save(os.path.join(effects, "blessing_of_light.png"), optimize=True)
    print("  items: mote_of_radiance; effect: blessing_of_light")


# ============================================================================================= main

def main():
    want = set(sys.argv[1:])
    makers = {"verdict": verdict, "wings": wings, "hand": hand, "herald": herald, "items": items}
    for key, fn in makers.items():
        if not want or key in want:
            print(key)
            fn()


if __name__ == "__main__":
    main()
