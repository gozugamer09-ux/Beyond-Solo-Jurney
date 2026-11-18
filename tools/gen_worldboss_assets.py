#!/usr/bin/env python3
"""
The great threats' assets (world bosses and monarchs, see com.beyondsolo.worldboss).

- Ghorrath, the Walking Mountain (titan): a giant of basalt eleven blocks tall, hunched under a ridge of peaks that
  grows out of its back, its arms long enough to drag its fists, magma glowing in every crack of it and pouring from
  a heart caged by its ribs. A second skin, "molten", for its last phase: the rock run red-hot and glowing seams
  all over it.
- Thurvok, the Horned Calamity (behemoth): a beast the size of a house, a hump of shoulders, a back of bone plates,
  a head under four great horns (two forward like a bull's, two curling back), hooves like boulders.
- Veyrith, Monarch of Stillness: a queen of ice in pale plate, a crown of crystal, a mantle of frozen light and a
  spear of crystal taller than she is.
- Khorvane, Monarch of Hunger: a feral king hunched like a wolf on its hind legs, wearing the skull of a giant wolf,
  a mane of black fur, blades for claws.
- Thyrax, Monarch of Storms: a lord in storm-dark plate on great wings of steel feathers, a glaive of lightning.

All five are built with the creature kit (tools/gen_creatures.py: one skin each, painted on the model by
tools/creature_skins.py) at a scale of their design (MODEL_SCALE). Their animations' times match the moves in the
boss classes (see each boss's notes below).

Run from the repo root:   python3 tools/gen_worldboss_assets.py                   (everything)
                          python3 tools/gen_worldboss_assets.py titan storms      (only some)
Look at one without the game:   python3 tools/preview_model.py titan_main --anim stomp --time 1.1
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
from gen_gate_assets import ease_in, ease_in_out, ease_out_back, window  # noqa: E402

anim, wave = G.anim, G.wave
sstep, fbm3 = CS.sstep, CS.fbm3


class OneSkin(GC.Body):
    """The creature kit's Body with its own skins (by default one, "main") instead of one per affinity: variants
    [(suffix, palette key)]."""

    def __init__(self, name, seed, variants=(("main", "main"),)):
        self.name = name
        self.uvs, self.size = G.save_textures(name, list(variants), lambda rng, a: {"aff": a}, seed)
        self.m = G.Model(self.uvs)


# ============================================================================================= motion

def lin(t):
    return max(0.0, min(1.0, t))


def pose(base=None, **kw):
    """A pose: {bone: rotation, bone_pos: position, bone_scale: scale}; kw replaces entries of base."""
    p = {k: list(v) for k, v in (base or {}).items()}
    for k, v in kw.items():
        p[k] = list(v)
    return p


def _channel(key):
    if key.endswith("_pos"):
        return key[:-4], "position", (0.0, 0.0, 0.0)
    if key.endswith("_scale"):
        return key[:-6], "scale", (1.0, 1.0, 1.0)
    return key, "rotation", (0.0, 0.0, 0.0)


def timeline(steps, extra=None):
    """steps: [(time, pose, ease into it)] -> GeckoLib bone channels; extra: {key: fn(t) -> [x, y, z]} added on top
    (keys as in a pose). A key a pose leaves out is at rest there."""
    keys = sorted({k for _, p, _ in steps for k in p} | set(extra or {}))

    def at(key, t):
        rest = list(_channel(key)[2])
        v = steps[0][1].get(key, rest)
        for (t0, p0, _), (t1, p1, ease) in zip(steps, steps[1:]):
            if t >= t0:
                k = ease(window(t, t0, t1)) if t1 > t0 else 1.0
                a, b = p0.get(key, rest), p1.get(key, rest)
                v = [a[i] + (b[i] - a[i]) * k for i in range(3)]
        return v

    out = {}
    for key in keys:
        bone, ch, _ = _channel(key)
        add = (extra or {}).get(key)
        if add:
            fn = (lambda t, key=key, add=add: [x + y for x, y in zip(at(key, t), add(t))])
        else:
            fn = (lambda t, key=key: at(key, t))
        out.setdefault(bone, {})[ch] = fn
    return out


def shake(t, a, b, amp, freq=40.0, fade=0.3):
    """A tremble over [a, b], easing in and out."""
    return amp * math.sin(t * freq) * window(t, a, a + fade) * (1 - window(t, b - fade, b))


def bump(t, a, b):
    """0 -> 1 -> 0 over [a, b]."""
    return math.sin(math.pi * window(t, a, b))


def hide(A, bone, keep=()):
    """Every animation but those in keep holds bone at scale 0 (a prop shown only by a move)."""
    for name, a in A.items():
        if name in keep:
            continue
        b = a["bones"].setdefault(bone, {})
        b["scale"] = {"0.00": [0, 0, 0], f"{a['animation_length']:.2f}": [0, 0, 0]}


# ============================================================================================= shared pieces

def spire(B, x, y, z, w, h, rx=0.0, rz=0.0, ry=0.0, seed=0, top="ash", rock="rock", tiers=3, lava=None, crater=False, **spec):
    """A jagged peak growing from (x, y, z): tiers of rock narrowing to a point, each turned a little from the last
    (about the peak's own axis, so the tiers stay stacked: give the turn only with rz, never with rx), its tip in
    another ramp (ash on the peaks); crater: a glowing mouth on top instead of a point."""
    piv = [x, y, z]
    shapes = ((1.0, 0.44, 0), (0.72, 0.3, 22), (0.46, 0.2, -16), (0.24, 0.14, 38))[:tiers]
    out = []
    yy = y
    for i, (kw_, kh, turn) in enumerate(shapes):
        ww, hh = w * kw_, h * kh
        r = top if i >= tiers - 1 else rock
        sp = dict(spec)
        if lava and i < tiers - 1:
            sp["lava"] = lava
        t = ry + (turn if rx == 0 else 0)
        out.append(B.c([x - ww / 2, yy - 0.3, z - ww / 2], [ww, hh + 0.6, ww], "boulder", pivot=piv, rot=[rx, t, rz], r=r, seed=seed + i,
                       skip=("down",) if i else None, **sp))
        yy += hh
    if crater:
        cw = w * shapes[-1][0] * 0.7
        out.append(B.c([x - cw / 2, yy, z - cw / 2], [cw, 0.5, cw], "glow", pivot=piv, rot=[rx, ry, rz]))
    return out


def spike(B, x, y, z, w, h, rx=0.0, rz=0.0, ry=0.0, mat="ice", **spec):
    """A shard standing out of a body: a shaft and a narrower point, leaning (rx forward, rz towards +x)."""
    piv = [x, y, z]
    return [B.c([x - w / 2, y, z - w / 2], [w, h, w], mat, pivot=piv, rot=[rx, ry, rz], skip=("down",), **spec),
            B.c([x - w * 0.31, y + h, z - w * 0.31], [w * 0.62, h * 0.55, w * 0.62], mat, pivot=piv, rot=[rx, ry, rz], skip=("down",), **spec),
            B.c([x - w * 0.13, y + h * 1.55, z - w * 0.13], [w * 0.26, h * 0.3, w * 0.26], mat, pivot=piv, rot=[rx, ry, rz], skip=("down",), **spec)]


def molten(f, p, s):
    """The titan's last phase: seams of magma all through its rock (a ramp only the molten skin has)."""
    n = np.abs(fbm3(f.X * 0.2, f.Y * 0.2, f.Z * 0.2, s.get("seed", 1) + 500, 3) - 0.5)
    p.over("mk_char", 0.08, n < 0.022)
    p.glow("mk_molten", 0.55 + 0.4 * sstep(0.008, 0.0, n), n < 0.008)


# ============================================================================================= the titan

TITAN = "titan"
G.MODEL_SCALE[TITAN] = 4.0
EMBER = (255, 128, 48)


def titan_palette(aff):
    hot = aff == "molten"
    ramps = {
        "rock": [(14, 12, 13), (44, 39, 38), (92, 83, 77), (148, 134, 120), (204, 188, 166)],
        "rock_d": [(8, 7, 8), (26, 22, 22), (56, 49, 46), (102, 91, 82), (150, 136, 120)],
        "ash": [(30, 27, 26), (78, 72, 68), (136, 128, 120), (190, 182, 170), (236, 230, 216)],
        "obsidian": [(3, 2, 4), (10, 8, 12), (26, 22, 30), (62, 56, 70), (130, 122, 140)],
        "horn": [(10, 8, 8), (32, 27, 25), (70, 61, 55), (128, 116, 102), (198, 186, 164)],
    }
    extra = None
    if hot:
        # the rock run red-hot: darker and redder in its shadows, glowing seams all over it, its cracks white-hot
        ramps["rock"] = [(10, 5, 5), (32, 16, 13), (66, 36, 28), (118, 70, 50), (176, 118, 84)]
        ramps["rock_d"] = [(6, 3, 3), (20, 10, 8), (44, 22, 16), (84, 46, 32), (130, 82, 58)]
        ramps["ash"] = [(26, 14, 12), (70, 40, 30), (122, 78, 56), (176, 124, 90), (226, 178, 134)]
        extra = {"glow": CS.glow_ramp((255, 176, 80)), "mk_molten": CS.glow_ramp((255, 112, 36)),
                 "mk_char": [(6, 3, 3), (20, 10, 8), (40, 22, 16), (70, 40, 28), (110, 70, 50)]}
    return GC.pal("plain", EMBER, ramps, tinted=(), extra=extra)


def titan():
    """Ghorrath, built about 40 units tall to the top of its head (its peaks rise to 60) and drawn four times bigger
    (its hitbox is 11.5 blocks tall). Bones: root, hips, legs and shins, body (bent forward), ridge (the peaks, kept
    upright), head (turned to look) with skull, horns and jaw, arms and hands (forearms and fists), held_rock (in its
    left fist only while it hurls)."""
    B = OneSkin(TITAN, 5100, (("main", "main"), ("molten", "molten")))
    box, col, c = B.box, B.col, B.c
    lava = dict(area=34.0, chance=0.45, w=0.8)
    rk = dict(r="rock", cell=9.0, fx=molten)
    rd = dict(r="rock_d", cell=5.0, fx=molten)
    obs = dict(r="obsidian", glint=True, marks=0.0)
    mx = GC.mirror_x
    TILT = 32

    def vein(*pts, w=0.7, faces=("north",), halo=1.0):
        return dict(pts=list(pts), w=w, halo=halo, faces=faces)

    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15.0, 3.0], [
        box(0, 16.6, 3.0, 16.0, 6.6, 12.0, "boulder", **rk, seed=10),
        # slabs hanging over the loins like a broken belt of stone
        *[c(mx([0.8, 11.2, -3.8], [6.8, 6.2, 2.0], sx), [6.8, 6.2, 2.0], "boulder", **rd, seed=11 + sx,
            pivot=[sx * 4.0, 17.0, -3.0], rot=[-10, sx * 8, sx * 7]) for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "hips", [sx * 7.2, 15.0, 3.0], [
            col(sx * 7.8, 3.0, 8.0, 16.6, 10.6, 11.0, "boulder", **rk, seed=20 + sx,
                lines=[vein((sx * 7.8 - 1.8, 15.4), (sx * 7.8 + 0.6, 12.6), (sx * 7.8 - 0.4, 9.6))]),
            box(sx * 7.8, 8.8, -1.0, 8.6, 4.4, 5.6, "boulder", **rd, seed=22 + sx),
        ], rot=[-10, 0, -sx * 6])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 7.8, 8.6, 3.0], [
            col(sx * 8.0, 3.4, 2.4, 9.2, 9.8, 10.2, "boulder", **rk, seed=24 + sx),
            # a foot like a fallen slab, three blunt toes, a spur of obsidian at the heel
            box(sx * 8.2, 1.6, 1.2, 12.4, 3.2, 15.4, "boulder", **rk, seed=26 + sx),
            *[box(sx * 8.2 + dx, 1.3, -7.2, 3.2, 2.6, 3.0, "boulder", **rd, seed=28 + i) for i, dx in enumerate((-4.0, 0.0, 4.0))],
            *spike(B, sx * 8.2, 2.6, 8.8, 2.2, 2.6, rx=-60, **obs),
        ], rot=[11, 0, sx * 6])
    chest = [vein((0.0, 29.4), (-2.6, 26.8), (-5.2, 26.0), (-8.0, 23.0)), vein((0.0, 29.4), (3.0, 26.4), (5.8, 25.8), (8.4, 22.6)),
             vein((-2.6, 26.8), (-2.0, 23.6)), vein((3.0, 26.4), (2.2, 23.2))]
    back = [vein((0.0, 37.0), (-1.2, 32.0), (0.8, 27.0), (-0.4, 21.0), w=0.9, faces=("south",)),
            *[vein((0.0, y), (sx * 4.0, y - 1.8), (sx * 9.0, y - 0.8), w=0.55, faces=("south",), halo=0.7) for y in (33.0, 28.0) for sx in (-1, 1)]]
    B.bone("body", "hips", [0, 18.0, 3.0], [
        box(0, 21.6, 3.0, 15.4, 7.4, 12.0, "boulder", **rk, seed=30, lines=back),
        *[c(mx([0.3, 19.4, -3.6], [6.8, 6.6, 2.0], sx), [6.8, 6.6, 2.0], "boulder", **rd, seed=31 + sx,
            pivot=[0, 22.6, -2.6], rot=[-4, -sx * 6, 0]) for sx in (-1, 1)],
        box(0, 28.6, 2.8, 24.0, 10.0, 16.0, "boulder", **rk, seed=33, lines=chest + back, lava=lava),
        # the breast: two slabs angled out, a cage of rock ribs over a heart of magma between them
        *[c(mx([1.6, 26.2, -7.8], [11.0, 8.4, 3.2], sx), [11.0, 8.4, 3.2], "boulder", **rk, seed=34 + sx,
            pivot=[sx * 1.6, 30.4, -6.2], rot=[-6, -sx * 12, 0]) for sx in (-1, 1)],
        box(0, 28.2, -6.8, 5.6, 7.6, 1.2, "glow"),
        box(0, 28.2, -6.3, 7.6, 9.6, 0.6, "boulder", r="rock_d", cell=1.8, seed=36, skip=("south",)),
        *[c([-4.0, y, -8.5 + 0.1 * i], [8.0, 1.3, 1.6], "boulder", r="rock_d", cell=1.6, seed=37 + i,
            pivot=[0, y + 0.6, -7.8], rot=[0, 0, (i - 1) * 8]) for i, y in enumerate((25.0, 27.5, 30.0))],
        # the flanks, the great hump of its shoulders, slabs sloping from its neck down to them
        *[c(mx([10.6, 22.4, -2.0], [3.8, 10.6, 11.6], sx), [3.8, 10.6, 11.6], "boulder", **rk, seed=40 + sx,
            pivot=[sx * 11.6, 28.0, 3.0], rot=[0, 0, sx * 8]) for sx in (-1, 1)],
        box(0, 36.4, 4.4, 30.0, 8.0, 16.0, "boulder", **rk, seed=42, lines=back, lava=lava),
        *[c(mx([0.6, 38.6, -3.0], [14.4, 4.4, 14.0], sx), [14.4, 4.4, 14.0], "boulder", **rk, seed=43 + sx,
            pivot=[sx * 0.6, 41.0, 4.0], rot=[0, 0, -sx * 13]) for sx in (-1, 1)],
    ], rot=[TILT, 0, 0])
    # the ridge: a mountain range grown out of its back, standing upright whatever its stoop, magma welling between
    # the peaks and running down the tallest from its crater
    pk = dict(lava=dict(area=30.0, chance=0.5, w=0.8), fx=molten, cell=6.0)
    streams = [vein((-1.4, 63.0), (-1.8, 58.0), (-0.6, 53.0), (-1.6, 47.0), (-0.8, 42.0), w=0.7, faces=("north", "south", "east", "west")),
               vein((1.8, 62.0), (2.4, 57.0), (1.4, 51.0), (2.2, 46.0), w=0.55, faces=("north", "south", "east", "west"))]
    ridge = [
        *spire(B, 0.0, 39.0, 8.0, 13.0, 25.0, seed=50, tiers=4, crater=True, lines=[dict(s) for s in streams], **pk),
        *spire(B, -8.4, 38.6, 6.8, 10.4, 18.0, rz=-10, seed=54, tiers=4, **pk),
        *spire(B, 8.6, 38.4, 7.0, 10.6, 19.4, rz=9, seed=58, tiers=4, **pk),
        *spire(B, -15.4, 38.0, 4.8, 8.0, 12.6, rz=-20, seed=62, tiers=4, **pk),
        *spire(B, 15.6, 37.8, 5.0, 8.2, 13.6, rz=18, seed=66, tiers=4, **pk),
        *spire(B, -20.6, 36.6, 3.4, 5.2, 7.0, rz=-34, seed=86, **pk),
        *spire(B, 20.8, 36.4, 3.6, 5.4, 7.6, rz=32, seed=88, **pk),
        *spire(B, 0.0, 35.0, 11.6, 10.0, 14.0, rx=30, seed=70, tiers=4, **pk),
        *spire(B, -5.6, 30.0, 11.2, 7.0, 9.0, rx=46, rz=-8, seed=74, **pk),
        *spire(B, 5.6, 29.6, 11.2, 6.8, 8.4, rx=48, rz=10, seed=78, **pk),
        *spire(B, 0.0, 24.8, 10.2, 5.6, 6.6, rx=56, seed=82, **pk),
        # the vents: magma welling between the peaks
        *[box(x, y, z, w, 0.9, w * 0.9, "glow") for x, y, z, w in ((-3.8, 40.6, 6.2, 2.6), (4.0, 40.4, 6.4, 2.4), (-11.0, 39.6, 5.0, 2.0),
                                                                   (11.2, 39.4, 5.2, 2.0), (0.2, 37.6, 10.0, 2.2), (-2.6, 32.6, 11.2, 1.6), (2.8, 32.2, 11.2, 1.6))],
        *spike(B, -3.4, 40.8, 2.6, 2.0, 4.2, rx=-10, rz=-20, **obs),
        *spike(B, 3.8, 40.6, 2.8, 1.8, 3.6, rx=-8, rz=24, **obs),
        *spike(B, -11.0, 39.4, 1.4, 1.6, 2.8, rx=-14, rz=-40, **obs),
        *spike(B, 11.2, 39.2, 1.6, 1.6, 2.6, rx=-12, rz=42, **obs),
    ]
    B.bone("ridge", "body", [0, 39.0, 6.0], ridge, rot=[-TILT, 0, 0])
    B.bone("head", "body", [0, 33.0, -6.0], [], rot=[-TILT, 0, 0])
    B.bone("skull", "head", [0, 33.0, -6.0], [
        box(0, 34.6, -10.6, 9.6, 8.0, 9.4, "boulder", r="rock", cell=4.0, seed=86, fx=molten),
        # a heavy brow in a scowl over eyes of fire, cheekbones, a ridge of a nose, a crown of rock
        *[c(mx([0.0, 36.6, -16.2], [5.8, 2.4, 2.8], sx), [5.8, 2.4, 2.8], "boulder", **rd, seed=87 + sx,
            pivot=[0, 37.8, -14.8], rot=[0, 0, -sx * 12]) for sx in (-1, 1)],
        *[box(sx * 3.9, 33.6, -15.4, 2.8, 2.6, 1.8, "boulder", **rd, seed=89 + sx) for sx in (-1, 1)],
        box(0, 34.2, -15.8, 2.0, 3.2, 1.4, "boulder", **rd, seed=91),
        *[c([min(sx * 1.0, sx * 3.3), 35.4, -15.7], [2.3, 1.0, 0.6], "glow", pivot=[sx * 2.1, 35.9, -15.5], rot=[0, 0, -sx * 16]) for sx in (-1, 1)],
        box(0, 39.4, -10.2, 7.2, 1.8, 7.0, "boulder", **rd, seed=92),
        *spike(B, 0.0, 40.0, -11.0, 1.8, 2.4, rx=8, **obs),
    ])
    horns = []
    for sx in (-1, 1):
        horns += GC.limb(B, [sx * 4.4, 37.0, -11.6], [(sx * 8.0, 38.4, -13.4, 3.2, dict(kr=(0.0, 0.3))), (sx * 10.8, 41.4, -14.4, 2.6, dict(kr=(0.3, 0.6))),
                                                        (sx * 11.6, 45.0, -13.4, 1.9, dict(kr=(0.6, 0.85))), (sx * 10.8, 47.6, -11.6, 1.1, dict(kr=(0.85, 1.0)))],
                         "horn", r="horn", tip="+y", ridged=4)
    B.bone("horns", "skull", [0, 37.0, -11.6], horns)
    B.bone("jaw", "skull", [0, 32.0, -7.6], [
        box(0, 30.8, -11.8, 9.8, 3.2, 8.6, "boulder", **rd, seed=93),
        box(0, 32.6, -11.6, 7.6, 1.2, 7.2, "glow"),
        *GC.limb(B, [-3.3, 32.0, -15.6], [(-3.8, 34.4, -16.4, 1.0)], "horn", r="horn", tip="+y"),
        *GC.limb(B, [3.3, 32.0, -15.6], [(3.8, 34.4, -16.4, 1.0)], "horn", r="horn", tip="+y"),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        arm_veins = [vein((-1.8, 33.0), (0.4, 29.4), (-0.6, 25.6), faces=("west", "east"))]
        B.bone(f"{side}_arm", "body", [sx * 17.4, 36.0, 3.0], [
            box(sx * 19.8, 38.0, 3.0, 14.0, 12.0, 15.0, "boulder", **rk, seed=100 + sx),
            c(mx([13.6, 43.6, -4.6], [12.6, 2.8, 15.2], sx), [12.6, 2.8, 15.2], "boulder", **rd, seed=102 + sx,
              pivot=[sx * 13.8, 44.0, 3.0], rot=[0, 0, -sx * 18]),
            col(sx * 20.2, 3.0, 23.6, 33.0, 10.0, 11.0, "boulder", **rk, seed=104 + sx, lines=arm_veins),
            *spike(B, sx * 22.4, 44.6, 4.0, 3.0, 5.2, rx=-10, rz=sx * 24, **obs),
            *spike(B, sx * 18.4, 45.4, 7.6, 2.4, 4.0, rx=-30, rz=sx * 12, **obs),
            *spike(B, sx * 25.8, 41.4, 1.6, 2.0, 3.2, rx=-6, rz=sx * 56, **obs),
        ], rot=[-TILT, 0, -sx * 7])
        fore_veins = [vein((-2.2, 23.0), (-0.4, 19.0), (-1.4, 15.4), (0.6, 12.0), faces=("west", "east")),
                      vein((-0.4, 19.0), (2.2, 17.4), faces=("west", "east"), w=0.5, halo=0.7)]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 20.2, 24.6, 3.0], [
            box(sx * 20.2, 24.0, 4.0, 10.8, 5.4, 10.6, "boulder", **rk, seed=106 + sx),
            col(sx * 20.4, 2.4, 12.4, 23.6, 12.0, 12.4, "boulder", **rk, seed=108 + sx, lines=fore_veins),
            box(sx * 20.6, 14.4, 2.0, 13.4, 5.0, 13.6, "boulder", **rd, seed=110 + sx),
            # the fist: a boulder, knuckles of basalt, magma glowing between them
            box(sx * 20.6, 6.8, 1.4, 14.4, 11.0, 14.4, "boulder", **rk, seed=112 + sx,
                lines=[vein((sx * 20.6 + x, 5.6), (sx * 20.6 + x, 2.0), w=0.6, halo=0.8) for x in (-3.4, 0.0, 3.4)]),
            *[box(sx * 20.6 + dx, 3.4, -6.2, 3.2, 3.4, 2.2, "boulder", **rd, seed=114 + i) for i, dx in enumerate((-5.1, -1.7, 1.7, 5.1))],
            *spike(B, sx * 26.2, 17.6, 4.0, 2.2, 4.2, rx=-34, rz=sx * 68, **obs),
            *spike(B, sx * 26.6, 12.0, 3.0, 2.0, 3.2, rx=-22, rz=sx * 76, **obs),
        ], rot=[14, 0, 0])
    # the boulder it tears out of the ground to throw (shown only while it hurls)
    B.bone("held_rock", "left_hand", [20.6, 0.0, 1.4], [
        box(20.6, -4.6, 1.4, 11.0, 9.6, 11.0, "boulder", **rk, seed=118),
        box(19.8, -9.0, 1.8, 7.2, 3.0, 7.6, "boulder", **rd, seed=119),
        box(22.0, -4.2, -4.4, 4.8, 5.0, 2.0, "boulder", **rd, seed=120),
    ])
    GC.register(TITAN, titan_palette, density=1.5)
    B.write(4.0, 4.0, 1.8)
    titan_anims()


def titan_anims():
    """Times match TitanBoss: intro 90 ticks (it climbs out of the earth over the first 60, roars at 62), phase
    changes 60, death 110 (on the ground at 42, falling forward), leaving 80 (it sinks back into the earth). Moves:
    stomp 40 (its right foot down at 22), sweep 44 (24), slam 56 (both fists down at 26), hurl 50 (a boulder torn out
    of the ground ahead, thrown from its left fist at 18), magma_rain 70 (its ridge erupts; stones fall from 10),
    fissure 50 (its fist in the ground at 12), earthbreaker 96 (three stamps at 12, 30, 48 and both fists down at 62)."""
    A = {}
    STAND = pose(body=[0, 0, 0], head=[0, 0, 0], jaw=[0, 0, 0], right_arm=[0, 0, 4], left_arm=[0, 0, -4], right_hand=[0, 0, 0],
                 left_hand=[0, 0, 0], right_leg=[0, 0, 0], left_leg=[0, 0, 0], right_shin=[0, 0, 0], left_shin=[0, 0, 0], hips_pos=[0, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    # idle (4 s): slow, heavy breathing; the head turning a little
    L = 4.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [1.4 * wave(t, L), 0, 0],
        "head": lambda t: [-1.0 * wave(t, L, 0.2), 6 * wave(t, L * 2), 0],
        "jaw": lambda t: [3 * max(0.0, wave(t, L, 0.1)), 0, 0],
        "right_arm": lambda t: [-1.5 * wave(t, L), 0, 0], "left_arm": lambda t: [1.5 * wave(t, L, 0.1), 0, 0],
        "hips_pos": lambda t: [0, -0.3 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0],
    }), 0.1)

    # walk (2.4 s): slow, ground-shaking strides; the body rolling over each, the fists swinging
    L = 2.4
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [-20 * wave(t, L), 0, 0], "left_leg": lambda t: [20 * wave(t, L), 0, 0],
        "right_shin": lambda t: [22 * max(0.0, wave(t, L, 0.25)), 0, 0], "left_shin": lambda t: [22 * max(0.0, wave(t, L, 0.75)), 0, 0],
        "body": lambda t: [2 + 1.5 * abs(wave(t, L)), 5 * wave(t, L), 3 * wave(t, L)],
        "head": lambda t: [0, -3 * wave(t, L), -2 * wave(t, L)],
        "right_arm": lambda t: [14 * wave(t, L), 0, 0], "left_arm": lambda t: [-14 * wave(t, L), 0, 0],
        "right_hand": lambda t: [-8 * max(0.0, wave(t, L)), 0, 0], "left_hand": lambda t: [-8 * max(0.0, -wave(t, L)), 0, 0],
        "hips_pos": lambda t: [0.6 * wave(t, L), -1.0 * abs(wave(t, L)), 0],
    }), 0.05)

    # dormant (5 s): crouched like a hill, fists on the ground, head bowed
    L = 5.0
    DORMANT = P(body=[24, 0, 0], head=[22, 0, 0], right_arm=[-20, 0, 10], left_arm=[-20, 0, -10], right_hand=[-14, 0, 0], left_hand=[-14, 0, 0],
                right_leg=[-38, 0, 4], left_leg=[-38, 0, -4], right_shin=[62, 0, 0], left_shin=[62, 0, 0], hips_pos=[0, -6.4, 2.0])
    A["dormant"] = anim(L, True, timeline([(0, DORMANT, lin), (L, DORMANT, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0],
        "hips_pos": lambda t: [0, -0.2 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0],
    }), 0.1)

    # intro (4.5 s): climbing out of the earth hand over hand as it rises (0 to 3 s), standing, then the roar (3.1 s)
    L = 4.5
    REACH_R = P(body=[30, 0, 0], head=[-20, 0, 0], right_arm=[-150, 0, 12], right_hand=[-10, 0, 0], left_arm=[-60, 0, -10], left_hand=[-40, 0, 0],
                right_leg=[-40, 0, 0], right_shin=[60, 0, 0], left_leg=[-20, 0, 0], left_shin=[40, 0, 0])
    PULL_R = P(body=[34, 0, 0], head=[-12, 0, 0], right_arm=[-70, 0, 14], right_hand=[-46, 0, 0], left_arm=[-150, 0, -12], left_hand=[-10, 0, 0],
               right_leg=[-20, 0, 0], right_shin=[40, 0, 0], left_leg=[-40, 0, 0], left_shin=[60, 0, 0])
    RISEN = P(body=[12, 0, 0], head=[-6, 0, 0], right_arm=[-20, 0, 10], left_arm=[-20, 0, -10], right_hand=[-20, 0, 0], left_hand=[-20, 0, 0])
    ROAR = P(body=[-14, 0, 0], head=[-34, 0, 0], jaw=[34, 0, 0], right_arm=[-40, 0, 52], right_hand=[-30, 0, 0], left_arm=[-40, 0, -52],
             left_hand=[-30, 0, 0], right_leg=[-6, 0, 4], left_leg=[6, 0, -4], hips_pos=[0, -0.8, 0])
    A["intro"] = anim(L, False, timeline([
        (0.0, REACH_R, lin), (0.6, PULL_R, ease_in_out), (1.2, REACH_R, ease_in_out), (1.8, PULL_R, ease_in_out), (2.4, REACH_R, ease_in_out),
        (3.0, RISEN, ease_in_out), (3.25, ROAR, ease_out_back), (4.0, ROAR, lin), (4.5, STAND, ease_in_out),
    ], extra={
        "head": lambda t: [0, 0, shake(t, 3.2, 4.0, 3, 47)],
        "body": lambda t: [shake(t, 3.2, 4.0, 1.0, 53), 0, 0],
    }), 0.05)

    # stomp (2 s): the right foot raised high, brought down at 1.1 s; a step after it with the left (1.7 s)
    L = 2.0
    LIFT = P(body=[-6, -10, 6], head=[6, 8, 0], right_leg=[-78, 0, 8], right_shin=[70, 0, 0], left_leg=[8, 0, -2], left_shin=[6, 0, 0],
             right_arm=[24, 0, 26], left_arm=[-30, 0, -24], hips_pos=[0.8, 1.2, 0.4])
    DOWN = P(body=[18, -6, 0], head=[-8, 4, 0], right_leg=[-30, 0, 4], right_shin=[18, 0, 0], left_leg=[22, 0, -2], left_shin=[24, 0, 0],
             right_arm=[-10, 0, 20], left_arm=[-26, 0, -18], hips_pos=[0, -2.4, -1.2])
    STEP = P(body=[10, 0, 0], left_leg=[-30, 0, -2], left_shin=[20, 0, 0], right_leg=[10, 0, 2], hips_pos=[0, -1.0, -1.6])
    A["stomp"] = anim(L, False, timeline([(0, STAND, lin), (0.8, LIFT, ease_in_out), (1.1, DOWN, ease_in), (1.4, DOWN, lin), (1.7, STEP, ease_in_out),
                                          (2.0, STAND, ease_in_out)], extra={"body": lambda t: [shake(t, 1.1, 1.5, 1.4, 60, 0.1), 0, 0]}), 0.025)

    # sweep (2.2 s): the right arm drawn back across, swept round in front at 1.2 s
    L = 2.2
    WIND = P(body=[6, -46, -6], head=[0, 30, 0], right_arm=[-30, 0, 70], right_hand=[-30, 0, 0], left_arm=[-10, 0, -20],
             right_leg=[-10, 0, 4], left_leg=[10, 0, -4], hips_pos=[0, -1.0, 0])
    SWING = P(body=[14, 48, 4], head=[0, -26, 0], right_arm=[-84, 0, -30], right_hand=[-12, 0, 0], left_arm=[10, 0, -40],
              right_leg=[8, 0, 4], left_leg=[-14, 0, -4], hips_pos=[0, -1.6, -0.6])
    THROUGH = P(SWING, body=[16, 62, 4], right_arm=[-70, 0, -50])
    A["sweep"] = anim(L, False, timeline([(0, STAND, lin), (0.85, WIND, ease_in_out), (1.2, SWING, ease_in), (1.45, THROUGH, ease_out_back),
                                          (1.7, THROUGH, lin), (2.2, STAND, ease_in_out)]), 0.025)

    # slam (2.8 s): both fists raised over its head, brought down together at 1.3 s, buried, wrenched free
    L = 2.8
    HIGH = P(body=[-18, 0, 0], head=[-20, 0, 0], jaw=[18, 0, 0], right_arm=[-172, 0, -10], left_arm=[-172, 0, 10], right_hand=[-20, 0, 0],
             left_hand=[-20, 0, 0], right_leg=[-8, 0, 4], left_leg=[-8, 0, -4], hips_pos=[0, 0.6, 0.6])
    SMASH = P(body=[40, 0, 0], head=[-24, 0, 0], jaw=[10, 0, 0], right_arm=[-64, 0, -12], left_arm=[-64, 0, 12], right_hand=[-6, 0, 0],
              left_hand=[-6, 0, 0], right_leg=[-30, 0, 4], right_shin=[34, 0, 0], left_leg=[-30, 0, -4], left_shin=[34, 0, 0],
              hips_pos=[0, -4.0, -0.8])
    A["slam"] = anim(L, False, timeline([(0, STAND, lin), (1.0, HIGH, ease_in_out), (1.3, SMASH, ease_in), (1.9, SMASH, lin), (2.8, STAND, ease_in_out)],
                                        extra={"body": lambda t: [shake(t, 1.3, 1.8, 1.6, 55, 0.1), 0, 0]}), 0.025)

    # hurl (2.5 s): the left fist driven into the ground ahead (0.3 s), a boulder torn up over its shoulder, thrown at 0.9 s
    L = 2.5
    DIG = P(body=[36, 14, 0], head=[-20, -8, 0], left_arm=[-56, 0, 6], left_hand=[-20, 0, 0], right_arm=[-14, 0, 14],
            right_leg=[-24, 0, 4], right_shin=[30, 0, 0], left_leg=[-30, 0, -4], left_shin=[36, 0, 0], hips_pos=[0, -3.0, 0], held_rock_scale=[0, 0, 0])
    TEAR = P(DIG, held_rock_scale=[1, 1, 1])
    BACK = P(body=[-12, 30, 0], head=[-10, -20, 0], left_arm=[-176, 20, -20], left_hand=[-50, 0, 0], right_arm=[-60, 0, 30],
             right_leg=[-16, 0, 4], left_leg=[14, 0, -4], hips_pos=[0, -0.8, 1.0], held_rock_scale=[1, 1, 1])
    THROW = P(body=[26, -30, 0], head=[-14, 14, 0], jaw=[20, 0, 0], left_arm=[-92, -20, 10], left_hand=[-6, 0, 0], right_arm=[10, 0, 30],
              right_leg=[16, 0, 4], left_leg=[-26, 0, -4], left_shin=[20, 0, 0], hips_pos=[0, -1.6, -1.0], held_rock_scale=[1, 1, 1])
    GONE = P(THROW, held_rock_scale=[0, 0, 0])
    A["hurl"] = anim(L, False, timeline([(0, P(held_rock_scale=[0, 0, 0]), lin), (0.3, DIG, ease_in), (0.32, TEAR, lin), (0.65, BACK, ease_in_out),
                                         (0.9, THROW, ease_in), (0.92, GONE, lin), (1.4, GONE, lin), (2.5, P(held_rock_scale=[0, 0, 0]), ease_in_out)]), 0.02)

    # magma_rain (3.5 s): it rears back with its arms wide and roars at the sky while the ridge erupts (0.5 to 2.6 s)
    L = 3.5
    BRACE = P(body=[30, 0, 0], head=[10, 0, 0], right_arm=[-30, 0, 20], left_arm=[-30, 0, -20], right_hand=[-30, 0, 0], left_hand=[-30, 0, 0],
              right_leg=[-24, 0, 4], right_shin=[30, 0, 0], left_leg=[-24, 0, -4], left_shin=[30, 0, 0], hips_pos=[0, -2.6, 0])
    ERUPT = P(body=[-22, 0, 0], head=[-40, 0, 0], jaw=[36, 0, 0], right_arm=[-120, 0, 60], left_arm=[-120, 0, -60], right_hand=[-30, 0, 0],
              left_hand=[-30, 0, 0], right_leg=[-4, 0, 6], left_leg=[-4, 0, -6], hips_pos=[0, 0.4, 0])
    A["magma_rain"] = anim(L, False, timeline([(0, STAND, lin), (0.35, BRACE, ease_in_out), (0.55, ERUPT, ease_out_back), (2.6, ERUPT, lin),
                                               (3.5, STAND, ease_in_out)], extra={
        "body": lambda t: [shake(t, 0.55, 2.6, 1.2, 45), 0, shake(t, 0.55, 2.6, 0.8, 38)],
        "ridge": lambda t: [shake(t, 0.5, 2.7, 1.4, 61), 0, shake(t, 0.5, 2.7, 1.0, 57)],
        "head": lambda t: [0, 0, shake(t, 0.6, 2.6, 2.4, 41)],
    }), 0.025)

    # fissure (2.5 s): the right fist raised and driven into the ground ahead at 0.6 s, held there as the earth splits
    L = 2.5
    RAISE = P(body=[-4, -12, 0], head=[-8, 6, 0], right_arm=[-160, 0, 6], right_hand=[-30, 0, 0], left_arm=[-20, 0, -24],
              right_leg=[-8, 0, 4], left_leg=[8, 0, -4], hips_pos=[0, 0.4, 0])
    PUNCH = P(body=[44, 8, 0], head=[-28, -6, 0], jaw=[16, 0, 0], right_arm=[-68, 0, 2], right_hand=[-6, 0, 0], left_arm=[-30, 0, -26],
              left_hand=[-30, 0, 0], right_leg=[-34, 0, 4], right_shin=[40, 0, 0], left_leg=[-20, 0, -4], left_shin=[30, 0, 0], hips_pos=[0, -4.4, -0.6])
    A["fissure"] = anim(L, False, timeline([(0, STAND, lin), (0.45, RAISE, ease_in_out), (0.6, PUNCH, ease_in), (2.0, PUNCH, lin), (2.5, STAND, ease_in_out)],
                                           extra={"right_arm": lambda t: [shake(t, 0.6, 2.0, 1.4, 70), 0, 0],
                                                  "body": lambda t: [shake(t, 0.6, 2.0, 0.8, 50), 0, 0]}), 0.025)

    # earthbreaker (4.8 s): stamps with the right foot (0.6 s), the left (1.5 s), the right (2.4 s), its arms rising with
    # each; then both fists high and down on the ground at 3.1 s, crouched over the blow
    L = 4.8

    def stamp(right, k):
        leg, shin, other = ("right_leg", "right_shin", "left_leg") if right else ("left_leg", "left_shin", "right_leg")
        up = P(body=[4, (-8 if right else 8), (6 if right else -6)], right_arm=[-40 - 40 * k, 0, 30], left_arm=[-40 - 40 * k, 0, -30],
               right_hand=[-30, 0, 0], left_hand=[-30, 0, 0], jaw=[10 * k, 0, 0], hips_pos=[0.8 if right else -0.8, 1.0, 0])
        up[leg], up[shin] = [-70, 0, 6 if right else -6], [64, 0, 0]
        down = P(body=[16, 0, 0], right_arm=[-50 - 40 * k, 0, 36], left_arm=[-50 - 40 * k, 0, -36], right_hand=[-30, 0, 0], left_hand=[-30, 0, 0],
                 jaw=[16 * k, 0, 0], hips_pos=[0, -2.0, 0])
        down[leg], down[shin], down[other] = [-26, 0, 0], [20, 0, 0], [12, 0, 0]
        return up, down

    U1, D1 = stamp(True, 0.0)
    U2, D2 = stamp(False, 0.5)
    U3, D3 = stamp(True, 1.0)
    SKY = P(body=[-24, 0, 0], head=[-36, 0, 0], jaw=[30, 0, 0], right_arm=[-178, 0, -14], left_arm=[-178, 0, 14], right_hand=[-24, 0, 0],
            left_hand=[-24, 0, 0], right_leg=[-10, 0, 6], left_leg=[-10, 0, -6], hips_pos=[0, 1.2, 0.8])
    BREAK = P(body=[50, 0, 0], head=[-34, 0, 0], jaw=[24, 0, 0], right_arm=[-60, 0, -8], left_arm=[-60, 0, 8], right_hand=[-4, 0, 0],
              left_hand=[-4, 0, 0], right_leg=[-44, 0, 6], right_shin=[56, 0, 0], left_leg=[-44, 0, -6], left_shin=[56, 0, 0], hips_pos=[0, -6.0, -1.0])
    A["earthbreaker"] = anim(L, False, timeline([
        (0, STAND, lin), (0.4, U1, ease_in_out), (0.6, D1, ease_in), (1.2, U2, ease_in_out), (1.5, D2, ease_in), (2.1, U3, ease_in_out),
        (2.4, D3, ease_in), (2.85, SKY, ease_in_out), (3.1, BREAK, ease_in), (3.9, BREAK, lin), (4.8, STAND, ease_in_out),
    ], extra={"body": lambda t: [shake(t, 3.1, 3.8, 2.0, 55, 0.1), 0, 0], "ridge": lambda t: [shake(t, 3.1, 3.9, 2.0, 63, 0.1), 0, 0]}), 0.025)

    # the phase changes (3 s): phase II doubles over, its heart breaking open, and roars; phase III beats the ground
    # with both fists and rises roaring, burning
    L = 3.0
    CLUTCH = P(body=[38, 0, 0], head=[20, 0, 0], right_arm=[-70, -30, -30], right_hand=[-70, 0, 0], left_arm=[-70, 30, 30], left_hand=[-70, 0, 0],
               right_leg=[-20, 0, 4], right_shin=[26, 0, 0], left_leg=[-20, 0, -4], left_shin=[26, 0, 0], hips_pos=[0, -2.4, 0])
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.4, CLUTCH, ease_in_out), (1.2, CLUTCH, lin), (1.5, ROAR, ease_out_back), (2.4, ROAR, lin),
                                           (3.0, STAND, ease_in_out)], extra={
        "body": lambda t: [shake(t, 0.4, 1.2, 1.6, 50) + shake(t, 1.5, 2.4, 1.0, 53), 0, 0],
        "head": lambda t: [0, 0, shake(t, 1.5, 2.4, 3, 47)],
    }), 0.025)
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.3, HIGH, ease_in_out), (0.5, SMASH, ease_in), (0.9, SMASH, lin), (1.3, ROAR, ease_out_back),
                                           (2.4, ROAR, lin), (3.0, STAND, ease_in_out)], extra={
        "body": lambda t: [shake(t, 0.5, 0.9, 1.6, 55, 0.1) + shake(t, 1.3, 2.4, 1.2, 53), 0, 0],
        "ridge": lambda t: [shake(t, 0.5, 2.4, 1.6, 61), 0, 0],
        "head": lambda t: [0, 0, shake(t, 1.3, 2.4, 3, 47)],
    }), 0.025)

    # death (5.5 s, held): it reels back, drops to its knees (1.4 s) and falls forward onto its face (2.1 s)
    L = 5.5
    REEL = P(body=[-16, 10, 0], head=[-30, 0, 10], jaw=[30, 0, 0], right_arm=[-30, 0, 40], left_arm=[-40, 0, -50], right_leg=[10, 0, 4],
             left_leg=[-10, 0, -4], hips_pos=[0, 0.4, 1.0])
    KNEES = P(body=[20, 0, 0], head=[20, 0, 0], jaw=[10, 0, 0], right_arm=[-10, 0, 20], left_arm=[-10, 0, -20], right_leg=[-80, 0, 4],
              right_shin=[90, 0, 0], left_leg=[-80, 0, -4], left_shin=[90, 0, 0], hips_pos=[0, -9.0, -1.0])
    FALLEN = P(KNEES, root=[84, 0, 0], body=[6, 0, 0], head=[10, 0, 20], right_arm=[-150, 0, -30], left_arm=[-110, 0, 30],
               right_hand=[0, 0, 0], left_hand=[0, 0, 0], right_leg=[-10, 0, 4], right_shin=[30, 0, 0], left_leg=[-6, 0, -4],
               left_shin=[20, 0, 0], hips_pos=[0, -1.0, 0], root_pos=[0, 0, 0])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.45, REEL, ease_out_back), (0.9, REEL, lin), (1.4, KNEES, ease_in), (1.55, KNEES, lin),
                                           (2.1, FALLEN, ease_in), (2.4, P(FALLEN, root=[80, 0, 0]), ease_out_back), (5.5, P(FALLEN, root=[80, 0, 0]), lin)],
                                          extra={"head": lambda t: [0, 0, shake(t, 0.45, 0.9, 3, 37)]}), 0.05)

    # leave (4 s): it hunches, arms drawn in, head bowed, as the earth takes it back
    L = 4.0
    SINK = P(body=[30, 0, 0], head=[26, 0, 0], right_arm=[-30, 0, 10], left_arm=[-30, 0, -10], right_hand=[-30, 0, 0], left_hand=[-30, 0, 0],
             right_leg=[-20, 0, 4], right_shin=[24, 0, 0], left_leg=[-20, 0, -4], left_shin=[24, 0, 0], hips_pos=[0, -2.0, 0])
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (0.8, SINK, ease_in_out), (L, SINK, lin)]), 0.05)
    hide(A, "held_rock", keep=("hurl",))
    GC.write_anims(TITAN, A)


# ============================================================================================= the behemoth

BEHEMOTH = "behemoth"
G.MODEL_SCALE[BEHEMOTH] = 3.0
CRIMSON = (255, 46, 60)


def bone_plate(B, x, y, z, w, h, d, rx=0.0, rz=0.0, seed=0, **spec):
    """A plate of bone standing out of a back: a blade, a narrower step and a point, tilted (rx back, rz out)."""
    piv = [x, y, z]
    sp = dict(r="bone", cracks=True, marks=0.5, **spec)
    return [B.c([x - w / 2, y, z - d / 2], [w, h * 0.55, d], "bone", pivot=piv, rot=[rx, 0, rz], skip=("down",), seed=seed, **sp),
            B.c([x - w * 0.4, y + h * 0.55, z - d * 0.34], [w * 0.8, h * 0.3, d * 0.68], "bone", pivot=piv, rot=[rx, 0, rz], skip=("down",), seed=seed + 1, **sp),
            B.c([x - w * 0.3, y + h * 0.85, z - d * 0.15], [w * 0.6, h * 0.2, d * 0.3], "bone", pivot=piv, rot=[rx, 0, rz], skip=("down",), seed=seed + 2, **sp)]


def behemoth():
    """Thurvok, built 28 units tall to its hump and drawn three times bigger (5.3 blocks, its hitbox 5.4). Bones: root,
    rear (its pivot under the hind feet: it rears on it), body, head, jaw, the four legs (upper and lower), tail and
    tail2 (the club)."""
    B = OneSkin(BEHEMOTH, 5300)
    box, col, c = B.box, B.col, B.c
    sk = dict(seed=70, mottle=0.16, fat=0.12, blotch=0.32, scars=2)
    hoof = dict(r="hoof", tip="-y", grooves=[dict(faces=("north",), pts=[(0.0, 0.0), (0.0, 0.95)], w=1.1, k=0.3, lip=0.0)])
    tuft = dict(ragged=0.9, len=2.0, width=0.9, skip=("up", "down"))
    B.bone("root", None, [0, 0, 0])
    B.bone("rear", "root", [0, 0, 15])
    plates = []
    for i, (z, h, d) in enumerate(((-11.0, 8.6, 5.0), (-6.4, 9.6, 5.4), (-1.6, 8.6, 5.2), (3.0, 7.2, 4.8), (7.4, 6.0, 4.4), (11.6, 4.8, 3.8), (15.4, 3.6, 3.2))):
        y = 26.2 if z < 1 else 25.4 - (z - 1) * 0.12
        plates += bone_plate(B, 0.0, y - 0.6, z, 1.9, h, d, rx=-16, seed=200 + i * 3)
    for i, (z, h) in enumerate(((-9.0, 5.6), (-4.0, 6.0), (1.0, 5.0), (5.6, 4.0))):
        for sx in (-1, 1):
            plates += bone_plate(B, sx * 4.6, 25.0 - i * 0.3, z, 1.6, h, 3.6, rx=-12, rz=sx * 34, seed=230 + i * 6 + (sx > 0) * 3)
    B.bone("body", "rear", [0, 17, 0], [
        # the shoulders and the great hump over them, the barrel, the hindquarters
        box(0, 18.6, -7.0, 17.0, 15.0, 14.0, "skin", zone="back", split=(0.55, 0.25), **sk,
            lobes=[dict(a=-0.42, h=0.58, ra=0.46, rh=0.36, k=0.14, rim=0.14), dict(faces=("north",), a=0.5, h=0.42, ra=0.38, rh=0.3, k=0.12, sym=True)],
            grooves=[dict(pts=[(0.0, 0.64), (0.14, 0.42), (0.16, 0.2)], w=1.3, k=0.09, lip=0.05),
                     dict(pts=[(0.3, 0.62), (0.42, 0.42), (0.44, 0.22)], w=1.3, k=0.08, lip=0.04),
                     dict(faces=("north",), pts=[(0.0, 0.85), (0.0, 0.1)], w=1.3, k=0.1, lip=0.0)]),
        box(0, 26.6, -6.4, 13.0, 3.2, 11.0, "skin", r="skin_d", **sk),
        box(0, 18.2, 6.4, 15.0, 12.4, 12.0, "skin", zone="back", split=(0.55, 0.25), **sk,
            lobes=[dict(a=0.1, h=0.5, ra=0.6, rh=0.4, k=0.1, rim=0.12)]),
        box(0, 18.8, 15.4, 14.0, 11.2, 8.4, "skin", zone="back", split=(0.55, 0.25), **sk,
            lobes=[dict(a=0.25, h=0.55, ra=0.55, rh=0.4, k=0.14, rim=0.14)]),
        # a shaggy black mane over the hump, the shoulders and the chest
        box(0, 20.6, -7.2, 18.6, 10.6, 14.8, "mane", seed=84, ragged=1.6),
        box(0, 25.8, -7.6, 14.6, 4.4, 11.6, "mane", seed=88, ragged=1.2, pivot=[0, 24.0, -7.6], rot=[8, 0, 0]),
        box(0, 12.6, -14.15, 7.0, 7.0, 0.5, "mane", seed=85, ragged=1.3, skip=("up", "down")),
        *plates,
    ])
    horns = []
    for sx in (-1, 1):
        # the bull's horns, out from the brow and sweeping forward and up
        horns += GC.limb(B, [sx * 4.6, 21.8, -21.0], [(sx * 7.8, 22.6, -22.6, 3.2, dict(kr=(0.0, 0.3))), (sx * 9.8, 24.8, -25.6, 2.6, dict(kr=(0.3, 0.6))),
                                                        (sx * 9.8, 27.8, -28.0, 1.9, dict(kr=(0.6, 0.85))), (sx * 8.6, 30.2, -28.8, 1.1, dict(kr=(0.85, 1.0)))],
                         "horn", r="horn", tip="+y", ridged=6)
        # the ram's horns, curling back from behind its ears
        horns += GC.curl(B, [sx * 5.0, 21.6, -16.6], sx, [(60, 4.0, 3.6), (95, 3.6, 3.1), (135, 3.2, 2.6), (175, 2.8, 2.1), (215, 2.4, 1.6), (250, 1.8, 1.0)],
                         "horn", r="horn", tip="+y", z_step=1.0, overlap=0.4, ridged=12.0)
    B.bone("head", "body", [0, 19.0, -13.0], [
        box(0, 18.0, -19.0, 11.0, 9.6, 10.0, "skin", zone="back", split=(0.6, 0.25), seed=71, mottle=0.12, fat=0.08, blotch=0.25,
            eyes=GC.eyes(3.3, 19.8, (1.2, 0.5), slant=0.5, pupil=None, lid=1.4)),
        # a mask of bone over its brow joining its horns, as a buffalo's boss, and down the ridge of its nose
        box(0, 22.6, -18.6, 11.4, 2.4, 6.4, "bone", r="bone", seed=72, marks=0.5, skip=("down",)),
        box(0, 21.4, -23.6, 9.2, 2.2, 1.8, "bone", r="bone", seed=73, marks=0.5),
        box(0, 18.6, -27.2, 3.2, 1.2, 6.4, "bone", r="bone", seed=74, marks=0.5, skip=("down",)),
        box(0, 15.4, -26.4, 8.2, 6.6, 5.2, "skin", r="skin_d", zone="back", seed=75, mottle=0.12, fat=0.08, blotch=0.2,
            sockets=[dict(x=sx * 1.7, y=16.6, r=(0.85, 0.55), core=0.42, slant=sx * -0.3) for sx in (-1, 1)],
            cuts=[dict(pts=[(-4.1, 12.6), (4.1, 12.6)], w=0.5, faces=("north",))],
            paint_faces=[dict(faces=("down",), ramp="mouth", tone=0.32)]),
        box(0, 12.85, -28.8, 7.4, 1.3, 0.5, "teeth", n=7, fangs=1, small=0.55, up=True, skip=("up", "down", "south")),
        *horns,
    ], rot=[12, 0, 0])
    B.bone("jaw", "head", [0, 14.6, -17.0], [
        box(0, 12.4, -22.6, 8.6, 2.4, 11.0, "skin", r="skin_d", seed=76, mottle=0.12, fat=0.08, blotch=0.2,
            paint_faces=[dict(faces=("up",), ramp="mouth", tone=0.3)], cuts=[dict(pts=[(-4.3, 13.4), (4.3, 13.4)], w=0.4, faces=("north",))]),
        box(0, 13.9, -27.9, 7.2, 0.8, 0.4, "teeth", n=6, fangs=1, small=0.6, up=False, skip=("up", "down", "south")),
        box(0, 10.0, -21.0, 5.6, 3.6, 5.4, "mane", seed=86, ragged=1.0, len=2.2, width=0.9),
        *[cc for sx in (-1, 1) for cc in (
            c([sx * 4.6 - 0.75, 12.0, -27.6], [1.5, 3.8, 1.5], "horn", r="tusk", tip="+y", kr=(0.0, 0.6), pivot=[sx * 4.6, 12.4, -27.0],
              rot=[-10, 0, sx * 16], skip=("down",)),
            c([sx * 4.6 - 0.45, 15.6, -27.6 + 0.3], [0.9, 2.6, 0.9], "horn", r="tusk", tip="+y", kr=(0.6, 1.0), pivot=[sx * 4.6, 12.4, -27.0],
              rot=[-24, 0, sx * 26], skip=("down",)))],
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_front", "body", [sx * 7.2, 16.0, -7.0], [
            col(sx * 7.4, -7.0, 7.6, 17.0, 7.2, 8.0, "skin", zone="limb", **sk,
                lobes=[dict(faces=("west", "east", "north"), a=0.0, h=0.72, ra=0.85, rh=0.26, k=0.12, rim=0.14)]),
            box(sx * 7.4, 13.0, -11.05, 7.2, 7.0, 0.5, "mane", seed=87 + (sx > 0), ragged=1.2, skip=("up", "down")),
        ])
        B.bone(f"{side}_front_low", f"{side}_front", [sx * 7.4, 8.0, -7.0], [
            col(sx * 7.4, -7.0, 2.4, 8.4, 5.6, 6.2, "skin", zone="limb", **sk),
            col(sx * 7.4, -7.1, 1.8, 5.0, 6.4, 6.8, "mane", seed=80 + (sx > 0), **tuft),
            col(sx * 7.4, -7.5, 0.0, 2.8, 6.6, 7.0, "horn", **hoof),
        ])
        B.bone(f"{side}_hind", "body", [sx * 6.6, 17.0, 14.0], [
            box(sx * 6.8, 13.4, 14.4, 7.2, 10.4, 9.4, "skin", zone="limb", **sk,
                lobes=[dict(faces=("west", "east"), a=0.1, h=0.66, ra=0.8, rh=0.32, k=0.12, rim=0.14)])], rot=[-14, 0, 0])
        B.bone(f"{side}_hind_low", f"{side}_hind", [sx * 6.8, 8.8, 16.0], [
            col(sx * 6.8, 16.2, 2.4, 9.4, 5.0, 5.6, "skin", zone="limb", **sk),
            col(sx * 6.8, 16.1, 1.8, 4.6, 5.8, 6.2, "mane", seed=82 + (sx > 0), **tuft),
            col(sx * 6.8, 16.0, 0.0, 2.8, 6.0, 6.4, "horn", **hoof),
        ], rot=[14, 0, 0])
    B.bone("tail", "body", [0, 21.0, 19.4], [B.bar(0, 21.0, 19.4, 25.6, 3.2, 3.2, "skin", r="skin_d", seed=77, mottle=0.12, fat=0.08)], rot=[-24, 0, 0])
    B.bone("tail2", "tail", [0, 21.0, 25.6], [
        B.bar(0, 21.0, 25.6, 30.4, 2.4, 2.4, "skin", r="skin_d", seed=78, mottle=0.12, fat=0.08),
        # a club of bone at its end, set with spikes
        box(0, 21.0, 32.2, 4.6, 4.2, 4.4, "bone", r="bone", seed=79, marks=0.5),
        *[cc for sx in (-1, 1) for cc in GC.limb(B, [sx * 2.0, 21.0, 32.2], [(sx * 4.4, 21.6, 32.6, 1.2)], "horn", r="horn", tip="+y")],
        *GC.limb(B, [0, 22.8, 32.4], [(0, 25.2, 33.0, 1.2)], "horn", r="horn", tip="+y"),
        *GC.limb(B, [0, 21.0, 34.2], [(0, 21.4, 36.6, 1.2)], "horn", r="horn", tip="+y"),
    ], rot=[30, 0, 0])
    GC.register(BEHEMOTH, lambda aff: GC.pal("blood", CRIMSON, {
        "skin": (92, 28, 30), "skin_d": (30, 10, 14), "skin_l": (146, 70, 56),
        "hair": (30, 18, 20), "hoof": (26, 20, 22), "tusk": (212, 196, 164),
        "bone": [(44, 30, 26), (108, 86, 70), (182, 164, 138), (222, 210, 184), (248, 242, 224)],
        "horn": [(8, 5, 5), (28, 19, 18), (74, 56, 48), (156, 132, 108), (230, 214, 186)],
    }, tinted=()), density=3.0)
    B.write(4.0, 2.8, 1.4)
    behemoth_anims()


def behemoth_anims():
    """Times match BehemothBoss: intro 80 ticks (out of the earth over the first 53, the roar at 56), phase changes 50,
    death 100 (on its side at 30), leaving 80. Moves: gore 30 (its horns through the prey at 14), trample 46 (rears,
    its forehooves down at 18), charge 64 (pawing and its horns lowered over the first 18, the charge to 48), roar 40
    (20), bloodrage 50 (its roar calls the pack at 20), gore_combo 52 (at 12, 28, 44), leap 60 (in the air from 8 to
    32), triple_charge 150 (three charges of 50)."""
    A = {}
    legs = ("right_front", "left_front", "right_hind", "left_hind")
    STAND = pose(**{b: [0, 0, 0] for b in ("body", "head", "jaw", "tail", "tail2", "rear") + legs + tuple(l + "_low" for l in legs)})

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def gait(L, swing=30, knee=34, phase=0.0):
        """A four-legged walk or gallop's legs, added on top of a pose."""
        ph = {"right_front": 0.0, "left_front": 0.5, "right_hind": 0.5, "left_hind": 0.0}
        out = {}
        for leg, p0 in ph.items():
            out[leg] = (lambda t, p0=p0: [swing * wave(t, L, p0 + phase), 0, 0])
            out[leg + "_low"] = (lambda t, p0=p0: [-knee * max(0.0, wave(t, L, p0 + phase + 0.25)), 0, 0])
        return out

    # idle (3 s): heavy breathing, its head swinging low, its tail switching
    L = 3.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.35 * wave(t, L), 0],
        "head": lambda t: [3 * wave(t, L, 0.2), 8 * wave(t, L * 2), 0], "jaw": lambda t: [4 + 4 * max(0.0, wave(t, L / 2)), 0, 0],
        "tail": lambda t: [0, 12 * wave(t, L), 0], "tail2": lambda t: [0, 16 * wave(t, L, 0.2), 0],
    }), 0.05)

    # walk (1.6 s): a heavy, rolling four-legged walk
    L = 1.6
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        **gait(L, 26, 30),
        "body": lambda t: [1.5 * wave(t, L / 2), 3 * wave(t, L), 2 * wave(t, L)], "body_pos": lambda t: [0, -0.8 * abs(wave(t, L)), 0],
        "head": lambda t: [-3 * wave(t, L / 2), -3 * wave(t, L), 0], "tail": lambda t: [-6, 10 * wave(t, L), 0],
    }), 0.05)

    # dormant (5 s): lying down, legs folded under it, its head on the ground
    L = 5.0
    LIE = P(body_pos=[0, -9.0, 0], head=[16, 0, 0], jaw=[0, 0, 0], tail=[30, 0, 0], tail2=[20, 0, 0],
            right_front=[-80, 0, 0], right_front_low=[150, 0, 0], left_front=[-80, 0, 0], left_front_low=[150, 0, 0],
            right_hind=[70, 0, 0], right_hind_low=[-140, 0, 0], left_hind=[70, 0, 0], left_hind_low=[-140, 0, 0])
    A["dormant"] = anim(L, True, timeline([(0, LIE, lin), (L, LIE, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.2 * wave(t, L), 0],
    }), 0.1)

    # gore (1.5 s): the head swept low, tossed up through its prey at 0.7 s
    L = 1.5
    LOW = P(head=[34, 10, 0], body=[8, 0, 0], body_pos=[0, -1.4, 1.4], right_front=[10, 0, 0], left_front=[-14, 0, 0], left_front_low=[16, 0, 0],
            tail=[-20, 0, 0])
    TOSS = P(head=[-40, -12, -14], jaw=[18, 0, 0], body=[-10, 0, 0], body_pos=[0, 0.8, -3.2], right_front=[-24, 0, 0], left_front=[-30, 0, 0],
             right_hind=[18, 0, 0], left_hind=[14, 0, 0], tail=[-30, 0, 0])
    A["gore"] = anim(L, False, timeline([(0, STAND, lin), (0.5, LOW, ease_in_out), (0.7, TOSS, ease_in), (0.95, TOSS, lin), (1.5, STAND, ease_in_out)]), 0.025)

    # gore_combo (2.6 s): to its right at 0.6 s, its left at 1.4 s, straight up at 2.2 s
    L = 2.6
    LOW_R, TOSS_R = P(LOW, head=[34, 24, 8]), P(TOSS, head=[-34, -26, -20])
    LOW_L, TOSS_L = P(LOW, head=[34, -24, -8]), P(TOSS, head=[-34, 26, 20])
    A["gore_combo"] = anim(L, False, timeline([(0, STAND, lin), (0.4, LOW_L, ease_in_out), (0.6, TOSS_R, ease_in), (1.15, LOW_R, ease_in_out),
                                               (1.4, TOSS_L, ease_in), (1.95, LOW, ease_in_out), (2.2, TOSS, ease_in), (2.6, STAND, ease_in_out)]), 0.025)

    # trample (2.3 s): it rears on its hind legs and brings its forehooves down at 0.9 s
    L = 2.3
    REAR = P(rear=[-34, 0, 0], body_pos=[0, 2.0, 0], head=[-20, 0, 0], jaw=[24, 0, 0], right_front=[-70, 0, 0], right_front_low=[80, 0, 0], left_front=[-50, 0, 0],
             left_front_low=[70, 0, 0], right_hind=[30, 0, 0], left_hind=[30, 0, 0], right_hind_low=[-8, 0, 0], left_hind_low=[-8, 0, 0], tail=[-30, 0, 0])
    CRASH = P(rear=[4, 0, 0], head=[26, 0, 0], jaw=[10, 0, 0], body_pos=[0, -2.2, -1.0], right_front=[-24, 0, 0], left_front=[-20, 0, 0],
              right_front_low=[10, 0, 0], left_front_low=[10, 0, 0], tail=[20, 0, 0])
    A["trample"] = anim(L, False, timeline([(0, STAND, lin), (0.65, REAR, ease_out_back), (0.9, CRASH, ease_in), (1.4, CRASH, lin), (2.3, STAND, ease_in_out)],
                                           extra={"body": lambda t: [shake(t, 0.9, 1.3, 1.5, 55, 0.1), 0, 0]}), 0.025)

    # charge (3.2 s): pawing the ground and lowering its horns (to 0.9 s), the charge (0.9 to 2.4 s), pulling up
    def charge_steps(t0, settle=True):
        PAW_UP = P(head=[30, 0, 0], body=[6, 0, 0], body_pos=[0, -1.2, 1.2], right_front=[-36, 0, 0], right_front_low=[40, 0, 0], tail=[-30, 0, 0])
        PAW_DOWN = P(PAW_UP, right_front=[16, 0, 0], right_front_low=[0, 0, 0])
        RUN = P(head=[38, 0, 0], body=[10, 0, 0], body_pos=[0, -1.6, -1.0], tail=[-40, 0, 0], jaw=[10, 0, 0])
        steps = [(t0 + 0.15, PAW_UP, ease_in_out), (t0 + 0.3, PAW_DOWN, ease_in), (t0 + 0.45, PAW_UP, ease_in_out), (t0 + 0.6, PAW_DOWN, ease_in),
                 (t0 + 0.9, RUN, ease_in_out), (t0 + 2.4, RUN, lin)]
        if settle:
            steps.append((t0 + 2.5, P(RUN, body_pos=[0, -0.6, 0]), ease_in_out))
        return steps

    def gallop_extra(windows):
        g = gait(0.5, 44, 50)

        def on(t):
            return max((window(t, a + 0.0, a + 0.15) * (1 - window(t, b - 0.1, b)) for a, b in windows), default=0.0)
        out = {k: (lambda t, fn=fn: [v * on(t) for v in fn(t)]) for k, fn in g.items()}
        out["body"] = lambda t: [4 * wave(t, 0.25) * on(t), 0, 0]
        out["body_pos"] = lambda t: [0, 1.4 * abs(wave(t, 0.5)) * on(t), 0]
        return out

    L = 3.2
    A["charge"] = anim(L, False, timeline([(0, STAND, lin)] + charge_steps(0.0) + [(3.2, STAND, ease_in_out)], extra=gallop_extra([(0.9, 2.4)])), 0.025)
    L = 7.5
    steps = [(0, STAND, lin)]
    for i in range(3):
        steps += charge_steps(i * 2.5, settle=i < 2)
    steps.append((7.5, STAND, ease_in_out))
    A["triple_charge"] = anim(L, False, timeline(steps, extra=gallop_extra([(0.9, 2.4), (3.4, 4.9), (5.9, 7.4)])), 0.025)

    # roar (2 s): its head thrown up and back, the roar from 0.8 to 1.6 s
    L = 2.0
    ROAR = P(head=[-44, 0, 0], jaw=[40, 0, 0], body=[-8, 0, 0], body_pos=[0, 0.6, 0.6], right_front=[-10, 0, 0], left_front=[-10, 0, 0],
             tail=[-40, 0, 0], tail2=[-20, 0, 0])
    A["roar"] = anim(L, False, timeline([(0, STAND, lin), (0.6, P(head=[20, 0, 0], body=[4, 0, 0]), ease_in_out), (0.85, ROAR, ease_out_back),
                                         (1.6, ROAR, lin), (2.0, STAND, ease_in_out)],
                                        extra={"head": lambda t: [0, 0, shake(t, 0.85, 1.6, 5, 45)], "body": lambda t: [shake(t, 0.85, 1.6, 1.0, 51), 0, 0]}), 0.025)

    # bloodrage (2.5 s): it shakes its head, rears, roars (1.0 s) to call the pack, and crashes down
    L = 2.5
    REAR_ROAR = P(REAR, head=[-50, 0, 0], jaw=[44, 0, 0])
    A["bloodrage"] = anim(L, False, timeline([(0, STAND, lin), (0.2, P(head=[10, 20, 10]), ease_in_out), (0.4, P(head=[10, -20, -10]), ease_in_out),
                                              (0.6, P(head=[10, 16, 8]), ease_in_out), (1.0, REAR_ROAR, ease_out_back), (1.7, REAR_ROAR, lin),
                                              (1.95, CRASH, ease_in), (2.5, STAND, ease_in_out)],
                                             extra={"head": lambda t: [0, 0, shake(t, 1.0, 1.7, 5, 45)]}), 0.025)

    # leap (3 s): a crouch, off the ground at 0.4 s, legs tucked in the air, forehooves reaching, down at 1.6 s
    L = 3.0
    CROUCH = P(body_pos=[0, -3.4, 1.0], head=[16, 0, 0], body=[6, 0, 0], right_front=[-30, 0, 0], left_front=[-30, 0, 0], right_front_low=[50, 0, 0],
               left_front_low=[50, 0, 0], right_hind=[30, 0, 0], left_hind=[30, 0, 0], right_hind_low=[-40, 0, 0], left_hind_low=[-40, 0, 0], tail=[-10, 0, 0])
    LAUNCH = P(rear=[-14, 0, 0], head=[-10, 0, 0], right_front=[-60, 0, 0], left_front=[-60, 0, 0], right_hind=[40, 0, 0], left_hind=[40, 0, 0],
               right_hind_low=[10, 0, 0], left_hind_low=[10, 0, 0], tail=[-30, 0, 0])
    TUCK = P(head=[10, 0, 0], right_front=[-70, 0, 0], left_front=[-70, 0, 0], right_front_low=[100, 0, 0], left_front_low=[100, 0, 0],
             right_hind=[60, 0, 0], left_hind=[60, 0, 0], right_hind_low=[-90, 0, 0], left_hind_low=[-90, 0, 0], tail=[-20, 0, 0])
    REACH = P(rear=[10, 0, 0], head=[20, 0, 0], right_front=[-40, 0, 0], left_front=[-40, 0, 0], right_hind=[20, 0, 0], left_hind=[20, 0, 0], tail=[-30, 0, 0])
    LAND = P(CROUCH, body_pos=[0, -4.0, -1.0], head=[30, 0, 0], jaw=[20, 0, 0])
    A["leap"] = anim(L, False, timeline([(0, STAND, lin), (0.35, CROUCH, ease_in_out), (0.5, LAUNCH, ease_out_back), (0.8, TUCK, ease_in_out),
                                         (1.4, TUCK, lin), (1.55, REACH, ease_in_out), (1.65, LAND, ease_in), (2.2, LAND, lin), (3.0, STAND, ease_in_out)]), 0.025)

    # intro (4 s): clawing its way out of the earth (to 2.7 s), standing, rearing and roaring at 2.8 s
    L = 4.0
    CLAW_R = P(rear=[-20, 0, 0], head=[-16, 0, 0], right_front=[-90, 0, 0], right_front_low=[40, 0, 0], left_front=[-20, 0, 0], left_front_low=[60, 0, 0],
               right_hind=[10, 0, 0], left_hind=[10, 0, 0])
    CLAW_L = P(CLAW_R, right_front=[-20, 0, 0], right_front_low=[60, 0, 0], left_front=[-90, 0, 0], left_front_low=[40, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, CLAW_R, lin), (0.55, CLAW_L, ease_in_out), (1.1, CLAW_R, ease_in_out), (1.65, CLAW_L, ease_in_out),
                                          (2.2, CLAW_R, ease_in_out), (2.7, STAND, ease_in_out), (2.95, REAR_ROAR, ease_out_back), (3.6, REAR_ROAR, lin),
                                          (4.0, STAND, ease_in)],
                                         extra={"head": lambda t: [0, 0, shake(t, 2.95, 3.6, 5, 45)]}), 0.025)

    # the phase changes (2.5 s): phase II thrashes its head and rears roaring; phase III rears and crashes down, roaring
    L = 2.5
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.25, P(head=[14, 26, 12]), ease_in_out), (0.5, P(head=[14, -26, -12]), ease_in_out),
                                           (0.75, P(head=[14, 22, 10]), ease_in_out), (1.0, P(head=[14, -18, -8]), ease_in_out),
                                           (1.3, REAR_ROAR, ease_out_back), (2.0, REAR_ROAR, lin), (2.5, STAND, ease_in_out)],
                                          extra={"head": lambda t: [0, 0, shake(t, 1.3, 2.0, 5, 45)]}), 0.025)
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.4, REAR, ease_out_back), (0.6, CRASH, ease_in), (0.9, CRASH, lin), (1.2, ROAR, ease_out_back),
                                           (2.0, ROAR, lin), (2.5, STAND, ease_in_out)],
                                          extra={"head": lambda t: [0, 0, shake(t, 1.2, 2.0, 5, 45)], "body": lambda t: [shake(t, 0.6, 0.9, 1.5, 55, 0.1), 0, 0]}), 0.025)

    # death (5 s, held): it staggers, its legs buckle, and it falls on its side (1.5 s)
    L = 5.0
    STAGGER = P(head=[-20, 0, 20], jaw=[30, 0, 0], body=[0, 0, 6], right_front=[-20, 0, 0], left_front_low=[30, 0, 0], tail=[-20, 0, 0])
    BUCKLE = P(body_pos=[0, -4.0, 0], head=[20, 0, 10], jaw=[20, 0, 0], right_front=[-40, 0, 0], right_front_low=[70, 0, 0], left_front=[-30, 0, 0],
               left_front_low=[60, 0, 0], right_hind=[30, 0, 0], right_hind_low=[-40, 0, 0])
    FALLEN = P(root=[0, 0, -86], root_pos=[0, -2.0, 0], head=[10, 0, -20], jaw=[26, 0, 0], right_front=[-30, 0, 10], left_front=[-10, 0, -20],
               right_hind=[20, 0, 10], left_hind=[30, 0, -20], tail=[10, 30, 0], tail2=[0, 20, 0])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.4, STAGGER, ease_out_back), (0.8, BUCKLE, ease_in_out), (1.5, FALLEN, ease_in),
                                           (1.7, P(FALLEN, root=[0, 0, -82]), ease_out_back), (5.0, P(FALLEN, root=[0, 0, -82]), lin)]), 0.05)

    # leave (4 s): its head lowered, legs folding as the earth takes it
    L = 4.0
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.2, P(LIE, body_pos=[0, -4.0, 0]), ease_in_out), (L, P(LIE, body_pos=[0, -4.0, 0]), lin)]), 0.05)
    GC.write_anims(BEHEMOTH, A)


# ============================================================================================= Veyrith, Monarch of Stillness

STILLNESS = "monarch_stillness"
G.MODEL_SCALE[STILLNESS] = 1.7
ICE = (155, 231, 255)


def crystal_spike(B, x, y, z, w, h, rx=0.0, rz=0.0, ry=0.0, d=None, mat="crystal", **spec):
    """A shard of glowing crystal: a shaft and a point, leaning (rx forward, rz towards +x)."""
    d = d or w
    piv = [x, y, z]
    return [B.c([x - w / 2, y, z - d / 2], [w, h * 0.68, d], mat, pivot=piv, rot=[rx, ry, rz], skip=("down",), **spec),
            B.c([x - w * 0.3, y + h * 0.68, z - d * 0.3], [w * 0.6, h * 0.32, d * 0.6], mat, pivot=piv, rot=[rx, ry, rz], skip=("down",), **spec)]


def stillness():
    """Veyrith, built 34 units tall to the top of her head (her crown to 38) and drawn 1.7 times bigger (3.9 blocks,
    her hitbox). Bones: root, hips, skirt_front, skirt_back (the gown's panels, swinging as she walks), legs and shins
    (under the gown), body, mantle, head (turned to look) with hair and crown, arms and hands, spear (in her right
    hand)."""
    B = OneSkin(STILLNESS, 5500)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    pl = dict(r="plate", engrave="trim", rivets=False, marks=0.5, grime=False)
    lp = dict(r="plate", rivets=False, marks=0.5, dents=False, grime=False)
    gown = dict(r="cloth", folds=1.8, marks=0.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17.6, 0], [
        box(0, 18.4, 0.0, 6.0, 2.6, 4.2, "plate", **lp, seed=10),
        box(0, 19.6, 0.0, 6.4, 1.0, 4.6, "trim", marks=0.0),
        box(0, 19.6, -2.35, 1.4, 1.4, 0.4, "glow", pivot=[0, 19.6, -2.35], rot=[0, 0, 45]),
        # the gown's sides, flaring out to the ground, and lames of pale plate over the hips
        *[c(mx([2.6, 0.6, -2.4], [0.7, 18.8, 5.6], sx), [0.7, 18.8, 5.6], "cloth", **gown, hem=1.6, glow_hem=2.4, seed=12 + sx,
            skip=("up", "down"), pivot=[sx * 2.9, 19.2, 0.4], rot=[0, 0, -sx * 9]) for sx in (-1, 1)],
        *[c(mx([1.0, 15.4, -2.6], [3.0, 3.6, 0.6], sx), [3.0, 3.6, 0.6], "plate", **pl, seed=14 + sx,
            pivot=[sx * 2.4, 19.0, -2.2], rot=[-6, 0, -sx * 10]) for sx in (-1, 1)],
    ])
    B.bone("skirt_front", "hips", [0, 19.0, -2.0], [
        c([-3.4, 0.6, -2.5], [6.8, 18.4, 0.6], "cloth", **gown, hem=1.8, glow_hem=2.6, seed=16, skip=("up", "down"), band=(4.2, 5.2)),
        # shards of ice hanging along the hem like jewels
        *[c([x - 0.25, 5.0, -2.75], [0.5, 1.6, 0.3], "crystal", pivot=[x, 6.6, -2.6], rot=[0, 0, 180]) for x in (-2.4, -0.8, 0.8, 2.4)],
    ], rot=[-5, 0, 0])
    B.bone("skirt_back", "hips", [0, 19.0, 2.0], [
        c([-3.8, 0.2, 2.0], [7.6, 18.8, 0.6], "cloth", **gown, hem=2.2, glow_hem=2.6, seed=18, skip=("up", "down"), band=(4.4, 5.4)),
    ], rot=[9, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "hips", [sx * 1.6, 17.6, 0.0], [
            col(sx * 1.6, 0.0, 9.2, 17.8, 2.6, 2.8, "plate", **lp, seed=20 + sx),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 1.6, 9.2, 0.0], [
            col(sx * 1.6, 0.0, 1.3, 9.4, 2.4, 2.6, "plate", **pl, seed=22 + sx),
            box(sx * 1.6, 0.65, -0.5, 2.4, 1.3, 3.6, "plate", **lp, seed=24 + sx),
            box(sx * 1.6, 0.55, -2.5, 1.4, 0.9, 1.0, "plate", **lp, seed=26 + sx),
        ])
    B.bone("body", "hips", [0, 19.8, 0.0], [
        # a corset of plate, a breastplate of two plates over a heart of ice, a gorget
        box(0, 21.6, 0.0, 5.2, 3.6, 3.6, "plate", **pl, seed=30),
        box(0, 25.4, -0.1, 7.0, 5.0, 4.2, "plate", **pl, seed=31),
        *[c(mx([0.1, 23.6, -2.55], [3.4, 4.2, 0.6], sx), [3.4, 4.2, 0.6], "plate", **lp, seed=32 + sx,
            pivot=[0, 25.6, -2.3], rot=[0, -sx * 12, 0]) for sx in (-1, 1)],
        box(0, 25.4, -2.95, 1.6, 1.6, 0.5, "trim", marks=0.0, pivot=[0, 25.4, -2.95], rot=[0, 0, 45]),
        box(0, 25.4, -3.2, 1.0, 1.0, 0.4, "crystal", pivot=[0, 25.4, -3.2], rot=[0, 0, 45]),
        box(0, 28.4, -0.1, 4.6, 1.2, 3.8, "plate", **lp, seed=34),
        # a fan of ice standing behind her head, as a high collar
        *[cc for i, (a, h) in enumerate(((-56, 3.4), (-30, 4.6), (0, 5.6), (30, 4.6), (56, 3.4)))
          for cc in crystal_spike(B, math.sin(math.radians(a)) * 1.6, 27.8, 2.2, 0.9, h, rx=14, rz=a, d=0.5)],
    ])
    B.bone("mantle", "body", [0, 28.4, 2.2], [
        c([-4.2, 1.0, 2.1], [8.4, 27.6, 0.6], "cloth", r="mantle", folds=1.2, hem=3.0, glow_hem=3.4, seed=36, skip=("up",), band=(5.0, 6.0),
          paint_faces=[dict(faces=("north",), ramp="cloth", tone=0.3)], marks=0.0),
    ], rot=[6, 0, 0])
    B.bone("head", "body", [0, 28.8, 0.0], [
        box(0, 31.2, -0.1, 4.0, 4.6, 4.2, "skin", r="skin", seed=40, mottle=0.06, fat=0.04, blotch=0.0, marks=0.0,
            eyes=GC.eyes(0.95, 31.45, (0.6, 0.2), slant=0.3, pupil=None, lid=1.0, glint=False),
            facepaint=[dict(pts=[(-0.42, 29.8), (0.42, 29.8)], w=0.2, ramp="lips", tone=0.4),
                       dict(pts=[(0.4, 32.05), (1.0, 32.2), (1.6, 32.35)], w=0.2, ramp="brow", tone=0.4),
                       dict(pts=[(-0.4, 32.05), (-1.0, 32.2), (-1.6, 32.35)], w=0.2, ramp="brow", tone=0.4),
                       dict(pts=[(0.0, 31.2), (0.0, 30.5), (0.2, 30.4)], w=0.16, ramp="skin", tone=0.25)],
            lines=[dict(pts=[(0.0, 33.0), (0.0, 33.01)], w=0.3, faces=("north",), halo=0.4, bright=0.9)]),
        # her hair: silver-white, falling down her back and framing her face
        box(0, 33.8, 0.3, 4.4, 0.9, 4.6, "mane", r="hair", seed=41, ragged=0.0, ends=False, marks=0.0),
        box(0, 28.0, 2.5, 4.6, 6.8, 1.2, "mane", r="hair", seed=42, ragged=1.4, len=3.2, width=0.8, marks=0.0),
        *[box(sx * 2.2, 30.4, 0.2, 0.6, 4.4, 4.0, "mane", r="hair", seed=43 + (sx > 0), ragged=1.0, marks=0.0, skip=("up",)) for sx in (-1, 1)],
        box(0, 32.6, 2.3, 4.6, 2.6, 0.8, "mane", r="hair", seed=45, ragged=0.0, ends=False, marks=0.0),
        # her crown: a circlet of silver, spikes of ice rising from it, a stone of ice on her brow
        box(0, 33.5, -2.25, 4.6, 0.6, 0.3, "trim", marks=0.0),
        box(0, 33.5, 2.55, 4.6, 0.6, 0.3, "trim", marks=0.0),
        *[box(sx * 2.25, 33.5, 0.15, 0.3, 0.6, 4.8, "trim", marks=0.0) for sx in (-1, 1)],
        *crystal_spike(B, 0.0, 33.6, -2.2, 0.9, 4.6, rx=-6),
        *[cc for sx in (-1, 1) for cc in crystal_spike(B, sx * 1.2, 33.6, -2.1, 0.7, 3.2, rx=-6, rz=sx * 12)],
        *[cc for sx in (-1, 1) for cc in crystal_spike(B, sx * 2.2, 33.6, -1.0, 0.6, 2.4, rx=-4, rz=sx * 22)],
        *[cc for sx in (-1, 1) for cc in crystal_spike(B, sx * 2.3, 33.6, 1.2, 0.5, 1.8, rz=sx * 28)],
        box(0, 32.95, -2.35, 0.6, 0.6, 0.3, "crystal", pivot=[0, 32.95, -2.35], rot=[0, 0, 45]),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 4.4, 27.6, 0.0]
        B.bone(f"{side}_arm", "body", [sx * 4.4, 27.2, 0.0], [
            col(sx * 4.8, 0.0, 21.8, 27.6, 2.3, 2.5, "plate", **lp, seed=50 + sx),
            # a pauldron of two plates rimmed in silver, shards of ice standing out of it
            c(mx([3.2, 26.4, -2.1], [3.4, 1.6, 4.2], sx), [3.4, 1.6, 4.2], "plate", **pl, seed=52 + sx, pivot=piv, rot=[0, 0, sx * 18]),
            c(mx([3.7, 25.2, -1.9], [3.0, 1.2, 3.8], sx), [3.0, 1.2, 3.8], "plate", **lp, seed=54 + sx, pivot=piv, rot=[0, 0, sx * 26]),
            c(mx([3.1, 26.2, -2.2], [3.6, 0.3, 4.4], sx), [3.6, 0.3, 4.4], "trim", marks=0.0, pivot=piv, rot=[0, 0, sx * 18]),
            *crystal_spike(B, sx * 5.4, 27.6, -0.6, 0.8, 3.2, rx=-6, rz=sx * 24),
            *crystal_spike(B, sx * 5.0, 27.8, 1.0, 0.6, 2.2, rx=10, rz=sx * 14),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.8, 22.0, 0.0], [
            col(sx * 4.8, 0.0, 16.6, 22.2, 2.1, 2.3, "plate", **pl, seed=56 + sx),
            box(sx * 4.8, 17.0, 0.0, 2.7, 1.3, 2.9, "plate", **lp, seed=58 + sx),
            c(mx([5.7, 18.6, -0.7], [0.4, 2.6, 1.4], sx), [0.4, 2.6, 1.4], "crystal", pivot=[sx * 5.9, 18.6, 0.0], rot=[0, 0, sx * 12]),
            box(sx * 4.8, 15.4, -0.1, 1.8, 2.2, 2.0, "plate", **lp, seed=60 + sx),
        ])
    # the spear: a haft of pale steel bound in silver, a long blade of ice, a guard of ice shards, an ice stone at its butt
    hx, hz = -4.8, -0.2
    B.bone("spear", "right_hand", [hx, 15.6, hz], [
        col(hx, hz, 1.2, 37.6, 0.7, 0.7, "metal", r="haft", long="y", fuller=False, seed=70),
        *[col(hx, hz, y, y + 0.5, 0.95, 0.95, "trim", marks=0.0) for y in (13.0, 18.6, 30.0, 36.6)],
        col(hx, hz, 13.5, 18.6, 0.85, 0.85, "leather", r="grip", stitch=False, seed=71),
        c([hx - 0.3, 37.4, hz - 1.0], [0.6, 6.6, 2.0], "crystal"),
        c([hx - 0.22, 44.0, hz - 0.6], [0.44, 2.4, 1.2], "crystal"),
        c([hx - 0.15, 46.4, hz - 0.25], [0.3, 1.2, 0.5], "crystal"),
        *[cc for sz in (-1, 1) for cc in crystal_spike(B, hx, 36.8, hz + sz * 0.5, 0.6, 2.4, rx=sz * -50, d=0.4)],
        c([hx - 0.4, -0.2, hz - 0.4], [0.8, 1.6, 0.8], "crystal"),
    ])
    # a javelin of ice she makes in her left hand and throws (shown only by ice_spears): lying forward in her fist
    jx, jy = 4.8, 16.2
    B.bone("javelin", "left_hand", [jx, jy, 0.0], [
        c([jx - 0.35, jy - 0.35, -9.0], [0.7, 0.7, 16.0], "crystal"),
        c([jx - 0.25, jy - 0.25, -12.0], [0.5, 0.5, 3.0], "crystal"),
        c([jx - 0.12, jy - 0.12, -13.6], [0.24, 0.24, 1.6], "crystal"),
        *[c([jx - 0.2, jy - 0.2, z], [0.4, 0.4, 1.4], "crystal", pivot=[jx, jy, z + 0.7], rot=[0, 0, 45]) for z in (5.6, 6.6)],
    ])
    GC.register(STILLNESS, lambda aff: GC.pal("frost", ICE, {
        "plate": [(10, 16, 30), (42, 60, 88), (108, 136, 168), (178, 204, 228), (238, 248, 255)],
        "trim": [(30, 40, 58), (88, 106, 132), (162, 182, 206), (216, 230, 242), (252, 254, 255)],
        "cloth": [(3, 6, 16), (12, 22, 48), (30, 50, 96), (64, 94, 148), (124, 156, 204)],
        "mantle": [(16, 30, 52), (54, 86, 122), (116, 158, 196), (182, 214, 236), (236, 248, 255)],
        "skin": [(44, 56, 76), (114, 130, 154), (180, 196, 214), (218, 230, 242), (246, 251, 255)],
        "hair": [(28, 38, 54), (88, 106, 130), (168, 186, 208), (220, 232, 246), (252, 254, 255)],
        "lips": [(30, 40, 70), (60, 80, 120), (96, 120, 164), (140, 160, 200), (190, 206, 232)],
        "brow": [(14, 20, 32), (40, 52, 72), (80, 96, 120), (130, 146, 170), (190, 204, 222)],
        "haft": [(12, 16, 26), (40, 50, 68), (90, 104, 126), (152, 166, 186), (214, 224, 236)],
        "grip": [(6, 10, 22), (16, 26, 50), (32, 48, 86), (62, 84, 128), (110, 134, 176)],
        "leather": (40, 56, 90),
    }, tinted=()), density=4.0)
    B.write(3.6, 3.0, 1.4)
    stillness_anims()


def stillness_anims():
    """Times match StillnessMonarch and MonarchBoss: intro 110 ticks (down out of her gate over the first 80, touching
    down at 79), phase changes 46, death 100 (held), leaving 90 (rising back into the sky). Moves: thrust 26 (the blow
    lands at 12), sweep 30 (14), blink 30 (gone at 6, her thrust from behind lands at 18), ice_spears 44 (thrown at 10,
    18 and 26), glacial_field 56 (the squares freeze at 30 and 54), frozen_lance 56 (26), absolute_zero 130 (100)."""
    A = {}
    bones = ("root", "hips", "skirt_front", "skirt_back", "right_leg", "right_shin", "left_leg", "left_shin", "body", "mantle", "head",
             "right_arm", "right_hand", "left_arm", "left_hand", "spear")
    # standing: her spear upright at her side, clear of her shoulder, her left hand loose
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-14, 0, 10], right_hand=[-24, 0, 0], spear=[38, 0, -16],
                 left_arm=[-6, 0, -8], left_hand=[-12, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def flutter(t, amp=4.0, f=9.0):
        return amp * (math.sin(t * f) + 0.5 * math.sin(t * f * 2.3 + 1.0))

    # idle (4 s): breathing, her mantle stirring, her gaze moving
    L = 4.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.12 * wave(t, L), 0],
        "head": lambda t: [1.5 * wave(t, L, 0.3), 4 * wave(t, L), 0], "mantle": lambda t: [2.5 * wave(t, L, 0.2), 0, 0],
        "skirt_front": lambda t: [1.2 * wave(t, L, 0.4), 0, 0], "skirt_back": lambda t: [1.5 * wave(t, L, 0.1), 0, 0],
        "left_hand": lambda t: [3 * wave(t, L, 0.5), 0, 0],
    }), 0.05)

    # walk (1.4 s): a slow, gliding stride under the gown
    L = 1.4
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [22 * wave(t, L), 0, 0], "left_leg": lambda t: [-22 * wave(t, L), 0, 0],
        "right_shin": lambda t: [26 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [26 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips": lambda t: [0, 4 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.35 * abs(wave(t, L)), 0],
        "body": lambda t: [2, -5 * wave(t, L), 0], "head": lambda t: [-1, 3 * wave(t, L), 0],
        "skirt_front": lambda t: [-7 * wave(t, L), 0, 0], "skirt_back": lambda t: [4 + 3 * abs(wave(t, L)), 0, 0],
        "mantle": lambda t: [5 + 3 * abs(wave(t, L)), 0, 0],
        "left_arm": lambda t: [12 * wave(t, L), 0, 0], "right_arm": lambda t: [-3 * wave(t, L), 0, 0],
    }), 0.05)

    # kneeling on her right knee, her spear planted, her head bowed (asleep, and how she dies)
    KNEEL = P(hips_pos=[0, -8.0, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0],
              skirt_front=[-34, 0, 0], skirt_front_scale=[1, 0.62, 1], skirt_back=[40, 0, 0], skirt_back_scale=[1, 0.62, 1],
              body=[14, 0, 0], head=[26, 0, 0], mantle=[-6, 0, 0], right_arm=[-40, 0, 10], right_hand=[-30, 0, 0], spear=[56, 0, -14],
              left_arm=[-18, 0, -6], left_hand=[-30, 0, 0])
    L = 5.0
    A["dormant"] = anim(L, True, timeline([(0, KNEEL, lin), (L, KNEEL, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "mantle": lambda t: [1.5 * wave(t, L, 0.2), 0, 0],
    }), 0.1)

    # thrust (1.3 s): the spear drawn back level at her hip, driven forward at 0.6 s
    L = 1.3
    DRAW = P(body=[4, 22, 0], head=[0, -18, 0], hips=[0, 10, 0], hips_pos=[0, -0.6, 1.0], right_arm=[30, -22, 6], right_hand=[-62, 0, 0],
             spear=[122, 0, -6], left_arm=[-60, 0, -10], left_hand=[-20, 0, 0], left_leg=[-18, 0, 0], left_shin=[14, 0, 0],
             right_leg=[10, 0, 0], skirt_front=[-8, 0, 0], mantle=[8, 0, 0])
    THRUST = P(body=[10, -22, 0], head=[-6, 20, 0], hips=[0, -10, 0], hips_pos=[0, -1.4, -2.4], right_arm=[-76, 30, 4], right_hand=[-6, 0, 0],
               spear=[172, 0, -4], left_arm=[30, 0, -16], left_hand=[-10, 0, 0], left_leg=[-34, 0, 0], left_shin=[30, 0, 0],
               right_leg=[24, 0, 0], right_shin=[6, 0, 0], skirt_front=[-14, 0, 0], skirt_back=[16, 0, 0], mantle=[18, 0, 0])
    A["thrust"] = anim(L, False, timeline([(0, STAND, lin), (0.42, DRAW, ease_in_out), (0.6, THRUST, ease_in), (0.85, THRUST, lin),
                                           (1.3, STAND, ease_in_out)]), 0.025)

    # sweep (1.5 s): the spear held level and swung across her front from right to left, through 0.7 s
    L = 1.5
    LEVEL = dict(right_arm=[-36, 0, 8], right_hand=[-40, 0, 0], spear=[178, 0, -4], left_arm=[-50, 0, -6], left_hand=[-30, 0, 0])
    SW_A = P(**LEVEL, body=[2, 52, 0], hips=[0, 16, 0], head=[0, -40, 0], hips_pos=[0, -1.0, 0], left_leg=[-14, 0, 0], right_leg=[8, 0, 0],
             skirt_front=[-6, 0, 0], mantle=[6, 0, 0])
    SW_B = P(**LEVEL, body=[8, -56, 0], hips=[0, -18, 0], head=[0, 34, 0], hips_pos=[0, -1.6, -0.6], left_leg=[-22, 0, 0], left_shin=[16, 0, 0],
             right_leg=[16, 0, 0], skirt_front=[-12, 0, 0], skirt_back=[14, 0, 0], mantle=[20, 0, -8])
    A["sweep"] = anim(L, False, timeline([(0, STAND, lin), (0.5, SW_A, ease_in_out), (0.78, SW_B, ease_in_out), (1.0, SW_B, lin),
                                          (1.5, STAND, ease_in_out)]), 0.025)

    # blink (1.5 s): she thins into the snow (gone at 0.3 s), out of it behind her prey already drawn back, the thrust
    # lands at 0.9 s
    L = 1.5
    GONE = [0.15, 1.5, 0.15]
    A["blink"] = anim(L, False, timeline([(0, STAND, lin), (0.24, P(STAND, root_scale=GONE, mantle=[30, 0, 0], skirt_back=[30, 0, 0]), ease_in),
                                          (0.3, P(DRAW, root_scale=GONE), lin), (0.45, DRAW, ease_out_back), (0.72, DRAW, lin),
                                          (0.9, THRUST, ease_in), (1.1, THRUST, lin), (1.5, STAND, ease_in_out)]), 0.025)

    # ice_spears (2.2 s): with her left hand she makes a javelin of ice over her head and throws it, three times (0.5,
    # 0.9, 1.3 s); her own spear stays at her side
    L = 2.2
    WIND = P(body=[-6, -18, 0], head=[-4, 14, 0], left_arm=[-160, 0, -10], left_hand=[-58, 0, 0], hips_pos=[0, -0.4, 0.6],
             left_leg=[-10, 0, 0], right_leg=[6, 0, 0], mantle=[4, 0, 0])
    THROW = P(body=[12, 16, 0], head=[-8, -12, 0], left_arm=[-84, 0, -6], left_hand=[-8, 0, 0], hips_pos=[0, -1.0, -1.0],
              left_leg=[-20, 0, 0], left_shin=[12, 0, 0], right_leg=[12, 0, 0], skirt_front=[-10, 0, 0], mantle=[14, 0, 0])
    steps = [(0, STAND, lin)]
    for rel in (0.5, 0.9, 1.3):
        steps += [(rel - 0.2, WIND, ease_in_out), (rel + 0.06, THROW, ease_in)]
    steps.append((2.2, STAND, ease_in_out))
    a = anim(L, False, timeline(steps, extra={"javelin": lambda t: [200, 0, 0]}), 0.025)

    def jav(t):
        on = max((window(t, rel - 0.36, rel - 0.22) if rel - 0.36 <= t < rel + 0.02 else 0.0) for rel in (0.5, 0.9, 1.3))
        return [on] * 3
    a["bones"]["javelin"]["scale"] = G.track(jav, L, 0.025)
    A["ice_spears"] = a

    # glacial_field (2.8 s): she lifts her spear and drives its butt into the ground (1.5 s, the first squares freeze),
    # holds it there with her left hand raised, and again (2.7 s, the rest)
    L = 2.8
    RAISE = P(right_arm=[-112, 0, 8], right_hand=[-20, 0, 0], spear=[132, 0, -8], left_arm=[-40, 0, -14], left_hand=[-30, 0, 0],
              body=[-6, 8, 0], head=[-10, 0, 0], hips_pos=[0, 0.4, 0], mantle=[-2, 0, 0])
    SLAM = P(right_arm=[-36, 0, 12], right_hand=[-30, 0, 0], spear=[66, 0, -12], left_arm=[-96, 0, -24], left_hand=[-10, 0, 0],
             body=[12, 4, 0], head=[6, 0, 0], hips_pos=[0, -2.2, -0.6], left_leg=[-16, 0, 0], left_shin=[18, 0, 0], right_leg=[10, 0, 0],
             right_shin=[14, 0, 0], skirt_front=[-8, 0, 0], skirt_back=[16, 0, 0], mantle=[22, 0, 0])
    A["glacial_field"] = anim(L, False, timeline([(0, STAND, lin), (0.6, RAISE, ease_in_out), (1.32, RAISE, lin), (1.5, SLAM, ease_in),
                                                  (2.3, SLAM, lin), (2.52, RAISE, ease_in_out), (2.7, SLAM, ease_in), (2.8, SLAM, lin)],
                                                 extra={"left_hand": lambda t: [shake(t, 1.5, 2.3, 4, 50), 0, 0],
                                                        "mantle": lambda t: [shake(t, 1.5, 2.3, 3, 23), 0, 0]}), 0.025)

    # frozen_lance (2.8 s): drawn far back aiming down the line with her left hand, a lunge that drives the spear at the
    # ground ahead (1.3 s), held while the ice runs out
    L = 2.8
    AIM = P(DRAW, body=[0, 34, 0], head=[0, -30, 0], hips=[0, 14, 0], hips_pos=[0, -1.2, 1.6], right_arm=[44, -34, 8], right_hand=[-62, 0, 0],
            spear=[118, 0, -6], left_arm=[-88, 0, -30], left_hand=[0, 0, 0], left_leg=[-26, 0, 0], left_shin=[24, 0, 0], right_leg=[18, 0, 0])
    LUNGE = P(THRUST, body=[22, -26, 0], head=[-14, 22, 0], hips_pos=[0, -3.6, -4.0], right_arm=[-62, 30, 4], right_hand=[-4, 0, 0],
              spear=[176, 0, -4], left_arm=[40, 0, -22], left_leg=[-58, 0, 0], left_shin=[62, 0, 0], right_leg=[40, 0, 0], right_shin=[10, 0, 0],
              skirt_front=[-30, 0, 0], skirt_back=[26, 0, 0], mantle=[16, 0, 0])
    A["frozen_lance"] = anim(L, False, timeline([(0, STAND, lin), (0.9, AIM, ease_in_out), (1.12, AIM, lin), (1.3, LUNGE, ease_in),
                                                 (1.9, LUNGE, lin), (2.8, STAND, ease_in_out)],
                                                extra={"right_hand": lambda t: [shake(t, 0.6, 1.12, 2.5, 47), 0, 0]}), 0.025)

    # absolute_zero (6.5 s), her authority: she rises (to 1.6 s) with her spear held high, hangs there turning while the
    # circle closes, turns the spear point down and falls on it, driving it into the ground (5.0 s), and rises
    L = 6.5
    RISEN = P(root_pos=[0, 15, 0], right_arm=[-176, 0, 6], right_hand=[-4, 0, 0], spear=[180, 0, -6], left_arm=[-10, 0, -62],
              left_hand=[-14, 0, 0], head=[-22, 0, 0], body=[-6, 0, 0], right_leg=[8, 0, 0], left_leg=[14, 0, 0], left_shin=[20, 0, 0],
              skirt_front=[6, 0, 0], skirt_back=[2, 0, 0], mantle=[-4, 0, 0])
    POINT_DOWN = P(RISEN, root_pos=[0, 18, 0], right_arm=[-160, 0, 4], right_hand=[-30, 0, 0], spear=[10, 0, 0], left_arm=[-150, 0, -10],
                   left_hand=[-30, 0, 0], head=[14, 0, 0], body=[8, 0, 0], left_leg=[-30, 0, 0], left_shin=[50, 0, 0], right_leg=[10, 0, 0],
                   right_shin=[30, 0, 0])
    IMPALE = P(KNEEL, right_arm=[-120, 0, 4], right_hand=[-24, 0, 0], spear=[-74, 0, 0], left_arm=[-110, 0, -8], left_hand=[-30, 0, 0],
               head=[18, 0, 0], body=[18, 0, 0], mantle=[30, 0, 0], skirt_back=[54, 0, 0])
    A["absolute_zero"] = anim(L, False, timeline([(0, STAND, lin), (0.5, P(STAND, hips_pos=[0, -1.4, 0], head=[10, 0, 0]), ease_in_out),
                                                  (1.6, RISEN, ease_in_out), (4.3, RISEN, lin), (4.75, POINT_DOWN, ease_in_out),
                                                  (5.0, IMPALE, ease_in), (5.7, IMPALE, lin), (6.5, STAND, ease_in_out)],
                                                 extra={"root_pos": lambda t: [0, 0.8 * wave(t, 1.6) * window(t, 1.6, 2.0) * (1 - window(t, 4.0, 4.3)), 0],
                                                        "root": lambda t: [0, 360 * ease_in_out(window(t, 1.9, 4.2)), 0],
                                                        "mantle": lambda t: [flutter(t, 3) * window(t, 1.0, 1.6) * (1 - window(t, 4.6, 5.0)), 0, 0],
                                                        "skirt_back": lambda t: [flutter(t + 0.4, 3) * window(t, 1.0, 1.6) * (1 - window(t, 4.6, 5.0)), 0, 0],
                                                        "body": lambda t: [shake(t, 5.0, 5.7, 1.2, 50, 0.1), 0, 0]}), 0.025)

    # intro (5.5 s): down out of her gate (to 3.95 s) upright, her gown streaming up; her spear's butt strikes the ground
    # as she touches down; she rises and looks at them
    L = 5.5
    DESCEND = P(head=[16, 0, 0], left_arm=[-14, 0, -40], left_hand=[-10, 0, 0], right_arm=[-14, 0, 22], right_hand=[-24, 0, 0], spear=[38, 0, -26],
                right_leg=[4, 0, 0], left_leg=[10, 0, 0], left_shin=[14, 0, 0], skirt_front=[-26, 0, 0], skirt_back=[34, 0, 0], mantle=[40, 0, 0])
    LIFT = P(DESCEND, right_arm=[-60, 0, 14], right_hand=[-20, 0, 0], spear=[80, 0, -14], head=[24, 0, 0], left_leg=[-6, 0, 0], left_shin=[20, 0, 0],
             right_shin=[10, 0, 0], hips_pos=[0, 1.0, 0])
    LAND = P(SLAM, head=[20, 0, 0], left_arm=[-30, 0, -50], hips_pos=[0, -3.0, -0.4], body=[16, 0, 0], skirt_front=[-4, 0, 0], skirt_back=[12, 0, 0],
             mantle=[-10, 0, 0])
    PRESENT = P(STAND, head=[-8, 0, 0], left_arm=[-50, 24, -30], left_hand=[-14, 0, 0], body=[-4, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, DESCEND, lin), (3.55, DESCEND, lin), (3.85, LIFT, ease_in_out), (3.95, LAND, ease_in), (4.4, LAND, lin),
                                          (4.95, PRESENT, ease_in_out), (5.2, PRESENT, lin), (5.5, STAND, ease_in_out)],
                                         extra={"mantle": lambda t: [flutter(t, 5) * (1 - window(t, 3.7, 3.95)), 0, 0],
                                                "skirt_back": lambda t: [flutter(t + 0.3, 4) * (1 - window(t, 3.7, 3.95)), 0, 0],
                                                "skirt_front": lambda t: [-flutter(t + 0.7, 3) * (1 - window(t, 3.7, 3.95)), 0, 0]}), 0.025)

    # the phase changes (2.3 s): II, her spear's butt strikes the ground and her left hand sweeps the cold out round her;
    # III, she gathers herself and throws her arms wide, her head back
    L = 2.3
    SPREAD = P(SLAM, left_arm=[-90, 50, -30], head=[-4, 30, 0], body=[4, 18, 0], mantle=[30, 0, 0])
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.45, RAISE, ease_in_out), (0.62, SLAM, ease_in), (0.9, SLAM, lin), (1.6, SPREAD, ease_in_out),
                                           (1.8, SPREAD, lin), (2.3, STAND, ease_in_out)],
                                          extra={"mantle": lambda t: [flutter(t, 3) * window(t, 0.62, 0.8) * (1 - window(t, 1.8, 2.2)), 0, 0]}), 0.025)
    GATHER = P(STAND, body=[22, 0, 0], head=[30, 0, 0], left_arm=[-60, -30, 20], left_hand=[-50, 0, 0], right_arm=[-30, 0, 4], hips_pos=[0, -1.6, 0.4],
               right_leg=[10, 0, 0], left_leg=[-8, 0, 0], left_shin=[14, 0, 0], mantle=[-8, 0, 0])
    WIDE = P(STAND, body=[-14, 0, 0], head=[-34, 0, 0], left_arm=[-20, 0, -86], left_hand=[0, 0, 0], right_arm=[-24, 0, 50], right_hand=[-10, 0, 0],
             spear=[30, 0, -52], mantle=[34, 0, 0], skirt_back=[20, 0, 0], skirt_front=[-14, 0, 0], hips_pos=[0, 0.4, 0])
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.6, GATHER, ease_in_out), (0.85, WIDE, ease_out_back), (1.8, WIDE, lin), (2.3, STAND, ease_in_out)],
                                          extra={"body": lambda t: [shake(t, 0.85, 1.8, 1.0, 45), 0, 0],
                                                 "mantle": lambda t: [flutter(t, 4) * window(t, 0.85, 1.0) * (1 - window(t, 1.6, 1.8)), 0, 0]}), 0.025)

    # death (5 s, held): struck, she staggers, sinks to her knee leaning on her spear, and her head bows: still
    L = 5.0
    STAGGER = P(STAND, body=[-14, -8, 0], head=[-24, 0, 6], left_arm=[-50, 20, -10], left_hand=[-60, 0, 0], hips_pos=[0, 0, 1.0], right_leg=[-10, 0, 0],
                left_leg=[14, 0, 0], left_shin=[10, 0, 0], mantle=[-10, 0, 0])
    SLUMP = P(KNEEL, head=[40, 0, 8], body=[22, 0, 4], left_arm=[-4, 0, -4], left_hand=[-6, 0, 0], right_arm=[-46, 0, 14], spear=[62, 0, -18])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.35, STAGGER, ease_out_back), (1.1, KNEEL, ease_in), (1.3, P(KNEEL, hips_pos=[0, -7.6, 0.6]), ease_in_out),
                                           (2.4, SLUMP, ease_in_out), (5.0, SLUMP, lin)]), 0.05)

    # leave (4.5 s): she lifts her face and rises back into the sky, her gown hanging
    L = 4.5
    ASCEND = P(head=[-26, 0, 0], body=[-4, 0, 0], left_arm=[-10, 0, -30], left_hand=[-10, 0, 0], right_arm=[-10, 0, 16], right_hand=[-24, 0, 0],
               spear=[34, 0, -22], right_leg=[6, 0, 0], left_leg=[10, 0, 0], left_shin=[12, 0, 0], skirt_front=[2, 0, 0], skirt_back=[-6, 0, 0],
               mantle=[-4, 0, 0])
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.0, ASCEND, ease_in_out), (L, ASCEND, lin)],
                                         extra={"mantle": lambda t: [flutter(t, 2) * window(t, 0.8, 1.4), 0, 0]}), 0.05)
    hide(A, "javelin", keep=("ice_spears",))
    GC.write_anims(STILLNESS, A)


# ============================================================================================= Khorvane, Monarch of Hunger

HUNGER = "monarch_hunger"
G.MODEL_SCALE[HUNGER] = 1.7
BLOODRED = (255, 46, 70)


def claw_blade(B, x, y, z, rz, length=8.6, w=1.1, t=0.34, curve=24, seed=0):
    """A blade out of the knuckles: flat, running down from (x, y, z) fanned out by rz, its last part bent forward
    (curve), its edge to the front."""
    a, b = length * 0.6, length * 0.4
    sp = dict(r="blade", long="y", edge="front", fuller=False, blood=0.6, seed=seed, marks=0.0)
    jx, jy = x - a * math.sin(math.radians(rz)), y - a * math.cos(math.radians(rz))
    return [B.c([x - t / 2, y - a, z - w / 2], [t, a + 0.2, w], "metal", pivot=[x, y, z], rot=[0, 0, rz], **sp),
            B.c([jx - t / 2, jy - b, z - w * 0.4], [t, b, w * 0.72], "metal", pivot=[jx, jy, z], rot=[-curve, 0, rz], **sp),
            B.c([jx - t * 0.35, jy - b - 0.9, z - w * 0.2], [t * 0.7, 1.0, w * 0.4], "metal", pivot=[jx, jy, z], rot=[-curve * 1.5, 0, rz], **sp)]


def hunger():
    """Khorvane, built 32 units tall to the top of his skull (hunched) and drawn 1.7 times bigger (3.4 blocks, his
    hitbox). Bones: root, hips (a belt of bone and rags of crimson), legs, shins and feet (a wolf's, on his toes), tail
    and tail2, body (hunched forward; ribs of bone over his chest, a mane of black fur), cape (the wolf's pelt and a
    torn crimson cloak), head (in the wolf's skull) and jaw, arms and hands (bracers of bone, three blades from each
    fist)."""
    B = OneSkin(HUNGER, 5700)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    sk = dict(r="skin", seed=60, mottle=0.14, fat=0.16, blotch=0.2, scars=2, marks=0.0)
    fur = dict(r="fur", len=2.2, width=1.2, marks=0.0)
    bn = dict(r="bone", marks=0.0)
    rag = dict(r="cloth", folds=1.6, hem=2.6, tears=2, marks=0.0, skip=("up", "down"))
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15.0, 1.0], [
        box(0, 15.6, 1.0, 6.4, 3.0, 4.2, "skin", **sk),
        # a belt of leather set with plates of bone, a jaw hung from it as a trophy
        box(0, 16.6, 1.0, 6.9, 1.4, 4.7, "leather", r="leather", seed=61, marks=0.0),
        *[box(sx * 2.0, 16.6, -1.4, 1.6, 1.8, 0.5, "bone", seed=62 + i, **bn) for i, sx in enumerate((-1, 0, 1))],
        c([-2.9, 14.3, -1.55], [1.4, 1.6, 0.4], "bone", seed=65, **bn, pivot=[-2.2, 15.9, -1.4], rot=[0, 0, -10]),
        c([-3.0, 13.6, -1.6], [1.6, 0.5, 0.4], "teeth", n=4, fangs=1, small=0.6, up=False, pivot=[-2.2, 15.9, -1.4], rot=[0, 0, -10],
          skip=("up", "down", "south")),
        # rags of crimson hanging front and back, torn
        c([-2.4, 6.6, -1.35], [4.8, 9.6, 0.5], "cloth", seed=66, **rag),
        c([-2.8, 7.4, 3.2], [5.6, 8.8, 0.5], "cloth", seed=67, **rag),
        *[c(mx([2.9, 9.4, -0.6], [0.5, 6.6, 3.4], sx), [0.5, 6.6, 3.4], "cloth", seed=68 + (sx > 0), **rag, pivot=[sx * 3.2, 16.0, 1.0],
            rot=[0, 0, -sx * 10]) for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        # a wolf's legs: a heavy thigh forward, the shin back to a high heel, the foot down onto three clawed toes
        B.bone(f"{side}_leg", "hips", [sx * 2.4, 14.6, 1.0], [
            col(sx * 2.5, 1.0, 7.4, 15.2, 3.4, 3.8, "fur", seed=70 + (sx > 0), **fur),
            c(mx([1.4, 10.4, -0.9], [2.2, 3.8, 0.5], sx), [2.2, 3.8, 0.5], "bone", seed=72 + (sx > 0), **bn),
        ], rot=[-30, 0, -sx * 4])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.5, 7.6, 1.0], [
            col(sx * 2.5, 1.0, 1.4, 7.8, 2.4, 2.8, "fur", seed=74 + (sx > 0), **fur),
            col(sx * 2.5, 1.0, 4.4, 6.4, 2.8, 3.2, "leather", r="leather", seed=76, marks=0.0),
        ], rot=[70, 0, 0])
        toes = [c([sx * 2.5 + (i - 1) * 0.75 - 0.3, -2.6, -2.1], [0.6, 0.8, 2.6], "skin", r="skin_d", seed=78 + i, marks=0.0,
                  pivot=[sx * 2.5, -2.2, 1.0], rot=[40, (i - 1) * -12, 0]) for i in range(3)]
        claws = [c([sx * 2.5 + (i - 1) * 0.75 - 0.18, -2.5, -3.5], [0.36, 0.6, 1.5], "horn", r="claw", tip="-z", pivot=[sx * 2.5, -2.2, 1.0],
                   rot=[40, (i - 1) * -12, 0]) for i in range(3)]
        B.bone(f"{side}_foot", f"{side}_shin", [sx * 2.5, 1.4, 1.0], [
            col(sx * 2.5, 1.0, -2.4, 1.6, 2.0, 2.4, "skin", r="skin_d", seed=77, marks=0.0),
            *toes, *claws,
        ], rot=[-60, 0, 0])
    B.bone("tail", "hips", [0, 15.0, 3.0], [B.bar(0, 15.0, 3.0, 8.6, 2.6, 2.6, "mane", r="fur", seed=80, ragged=0.8, len=2.0, width=0.9, marks=0.0)],
           rot=[-40, 0, 0])
    B.bone("tail2", "tail", [0, 15.0, 8.4], [B.bar(0, 15.0, 8.4, 14.2, 2.0, 2.0, "mane", r="fur", seed=81, ragged=1.0, len=2.2, width=0.9, marks=0.0)],
           rot=[-22, 0, 0])
    ribs = []
    for i in range(4):
        y = 25.0 - i * 1.5
        for sx2 in (-1, 1):
            # a ribcage taken from a beast, worn over his chest: ribs curving round from a keel of bone
            ribs.append(c(mx([0.5, y, -3.15], [3.4 - i * 0.2, 0.6, 0.5], sx2), [3.4 - i * 0.2, 0.6, 0.5], "bone", seed=90 + i * 2 + (sx2 > 0), **bn,
                          pivot=[0, y + 0.3, -2.9], rot=[0, -sx2 * 14, -sx2 * (8 + i * 4)]))
    B.bone("body", "hips", [0, 16.0, 1.0], [
        box(0, 18.2, 1.0, 5.8, 4.4, 4.0, "skin", **sk, anat=dict(part="chest", kind="muscle", k=0.8)),
        box(0, 23.6, 0.9, 8.6, 6.8, 5.4, "skin", **sk, anat=dict(part="chest", kind="muscle", k=1.2)),
        box(0, 23.4, -2.85, 1.0, 6.2, 0.6, "bone", seed=88, **bn),
        *ribs,
        # the mane: black fur heaped over his shoulders and the back of his neck, hanging down his back
        box(0, 26.6, 1.6, 11.2, 4.0, 7.0, "mane", r="fur", seed=96, ragged=1.4, len=2.6, width=1.0, marks=0.0),
        box(0, 22.6, 3.8, 8.4, 8.0, 1.8, "mane", r="fur", seed=97, ragged=1.6, len=3.0, width=0.9, marks=0.0),
        box(0, 28.2, 0.6, 8.0, 1.6, 5.4, "mane", r="fur", seed=98, ragged=1.0, len=1.8, marks=0.0),
    ], rot=[26, 0, 0])
    # the cape: the wolf's pelt from its skull down his back, and a torn crimson cloak under it
    B.bone("cape", "body", [0, 27.0, 3.6], [
        c([-4.6, 13.0, 3.4], [9.2, 14.2, 0.5], "cloth", seed=100, r="cloth", folds=1.4, hem=4.0, tears=3, marks=0.0, skip=("up",)),
        c([-3.4, 18.0, 3.95], [6.8, 9.4, 0.7], "mane", r="fur", seed=101, ragged=2.0, len=3.2, width=1.0, marks=0.0, skip=("up",)),
    ], rot=[-30, 0, 0])
    spikes = []
    for dx, h, rz in ((0.0, 2.6, 0), (1.3, 2.2, 14), (-1.3, 2.2, -14), (2.3, 1.6, 30), (-2.3, 1.6, -30)):
        # a crown of fangs set round the skull's crown
        spikes += GC.limb(B, [dx, 33.0, 0.6 + abs(dx) * 0.3], [(dx + math.sin(math.radians(rz)) * h * 0.5, 33.0 + h * 0.6, 1.6 + abs(dx) * 0.3, 0.7, dict(kr=(0.0, 0.55))),
                                                           (dx + math.sin(math.radians(rz)) * h, 33.0 + h, 2.6 + abs(dx) * 0.2, 0.4, dict(kr=(0.55, 1.0)))],
                          "horn", r="fang", tip="+y")
    B.bone("head", "body", [0, 27.4, -0.4], [
        # his own face under the skull: grey, in its shadow, a snarl of fangs
        box(0, 29.3, -0.9, 3.8, 3.4, 3.8, "skin", r="skin", seed=110, mottle=0.12, fat=0.06, blotch=0.0, marks=0.0,
            facepaint=[dict(pts=[(-1.9, 30.6), (1.9, 30.6)], w=1.3, ramp="black", tone=0.12),
                       dict(pts=[(-1.2, 29.2), (0.0, 28.9), (1.2, 29.2)], w=0.3, ramp="skin_d", tone=0.25)]),
        box(0, 28.45, -2.9, 3.0, 0.5, 0.3, "teeth", n=6, fangs=1, small=0.5, up=True, skip=("up", "down", "south")),
        # the wolf's skull over his head: its crown, a ridge along it, the brow and its burning eye holes
        box(0, 32.1, -0.2, 4.8, 2.2, 5.6, "bone", r="bone", seed=111, marks=0.0),
        box(0, 33.4, 0.4, 1.0, 0.5, 4.4, "bone", r="bone", seed=112, marks=0.0),
        box(0, 31.3, -3.15, 5.0, 1.7, 0.9, "bone", r="bone", seed=113, marks=0.0,
            sockets=[dict(x=sx * 1.45, y=31.35, r=(0.72, 0.48), core=0.42, slant=-sx * 0.3) for sx in (-1, 1)]),
        # its muzzle, long and tapering, tipped down, and the ridge of its nose
        box(0, 30.85, -4.7, 3.0, 1.8, 3.2, "bone", r="bone", seed=114, marks=0.0),
        c([-1.1, 30.0, -8.0], [2.2, 1.5, 2.2], "bone", r="bone", seed=115, marks=0.0, pivot=[0, 30.7, -5.9], rot=[8, 0, 0]),
        c([-0.4, 30.95, -8.12], [0.8, 0.6, 0.14], "skin", r="black", marks=0.0, pivot=[0, 30.7, -5.9], rot=[8, 0, 0]),
        box(0, 31.85, -5.0, 1.0, 0.4, 3.8, "bone", r="bone", seed=116, marks=0.0),
        # cheekbones flaring back to the crown, rows of teeth down the muzzle, the long fangs at its front
        *[c(mx([1.3, 30.0, -4.8], [0.7, 0.9, 3.8], sx), [0.7, 0.9, 3.8], "bone", seed=117 + (sx > 0), **bn, pivot=[sx * 1.6, 30.4, -1.0],
            rot=[0, sx * 12, 0]) for sx in (-1, 1)],
        *[c(mx([1.0, 29.55, -7.6], [0.25, 0.5, 4.4], sx), [0.25, 0.5, 4.4], "teeth", n=7, fangs=1, small=0.55, up=True,
            skip=("up", "down", "north", "south")) for sx in (-1, 1)],
        *[c(mx([0.7, 28.4, -7.7], [0.5, 1.7, 0.5], sx), [0.5, 1.7, 0.5], "horn", r="fang", tip="-y", pivot=[sx * 0.95, 30.0, -7.45], rot=[-8, 0, 0])
          for sx in (-1, 1)],
        *spikes,
        # the wolf's pelt, hanging from the back of its skull
        box(0, 30.6, 3.0, 5.6, 4.6, 1.2, "mane", r="fur", seed=120, ragged=1.6, len=2.8, width=0.9, marks=0.0),
    ], rot=[-26, 0, 0])
    B.bone("jaw", "head", [0, 28.4, -0.4], [
        box(0, 27.7, -1.5, 3.6, 1.3, 3.6, "skin", r="skin", seed=121, mottle=0.1, fat=0.04, blotch=0.0, marks=0.0,
            paint_faces=[dict(faces=("up",), ramp="mouth", tone=0.3)]),
        box(0, 28.55, -3.1, 3.0, 0.4, 0.3, "teeth", n=6, fangs=1, small=0.5, up=False, skip=("up", "down", "south")),
        box(0, 27.0, -0.4, 3.8, 1.6, 2.6, "mane", r="fur", seed=122, ragged=1.2, len=1.6, width=0.8, marks=0.0),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 5.0, 25.8, 0.8]
        pauldron = ([
            # his left shoulder: the skull of a lesser beast, horns and all
            box(sx * 6.0, 26.6, 0.8, 3.4, 2.6, 3.8, "bone", seed=130, **bn,
                sockets=[dict(x=sx * 6.0 + d * 0.8, y=26.4, r=(0.4, 0.3), core=0.3, slant=0.2 * d) for d in (-1, 1)]),
            *[cc for dx, w in ((7.0, 0.9), (5.4, 0.7)) for cc in GC.limb(B, [sx * dx, 27.6, 1.2], [(sx * (dx + 0.4), 28.8, 2.8, w, dict(kr=(0.0, 0.6))),
                                                                                         (sx * (dx + 0.2), 29.2, 4.2, w * 0.55, dict(kr=(0.6, 1.0)))],
                                                                  "horn", r="horn", tip="+y")],
        ] if sx > 0 else [
            # his right: three plates of bone, overlapping, a row of spikes along them
            *[c(mx([4.4 + j * 0.5, 26.0 - j * 1.1, -1.4], [3.2, 1.6, 4.4], sx), [3.2, 1.6, 4.4], "bone", seed=132 + j, **bn, pivot=piv,
                rot=[0, 0, sx * (16 + j * 12)]) for j in range(3)],
            *[cc for j in range(3) for cc in GC.limb(B, [sx * (5.8 + j * 0.4), 27.4 - j * 1.1, -0.6 + j * 1.0],
                                                      [(sx * (7.2 + j * 0.4), 29.6 - j * 1.0, -0.2 + j * 1.0, 0.7)], "horn", r="horn", tip="+y")],
        ])
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 5.4, 0.8, 19.0, 26.4, 3.2, 3.2, "skin", **sk, anat=dict(part="upper", k=1.2),
                lines=[dict(pts=[(sx * 5.4 - 1.1 + d * 0.7, 24.2 - d * 0.3), (sx * 5.4 + 0.3 + d * 0.7, 21.8 - d * 0.3)], w=0.22, faces=("north",),
                            halo=0.5, bright=0.85) for d in range(3)]),
            col(sx * 5.4, 0.8, 23.4, 25.6, 3.5, 3.5, "mane", r="fur", seed=128 + (sx > 0), ragged=0.8, len=1.6, marks=0.0),
            *pauldron,
        ], rot=[-24, 0, -sx * 8])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.4, 19.2, 0.8], [
            col(sx * 5.4, 0.8, 12.6, 19.4, 2.8, 2.8, "skin", **sk, anat=dict(part="fore", k=1.2)),
            # a bracer of leather straps, spurs of bone along its outside, the fist wrapped, three blades out of the knuckles
            col(sx * 5.4, 0.8, 13.6, 17.8, 3.2, 3.2, "leather", r="leather", seed=136 + (sx > 0), marks=0.0),
            *[col(sx * 5.4, 0.8, y, y + 0.4, 3.4, 3.4, "leather", r="leather", seed=137, marks=0.0) for y in (14.4, 16.8)],
            *[cc for j in range(3) for cc in GC.limb(B, [sx * 6.9, 14.4 + j * 1.3, 1.2], [(sx * 8.2, 15.4 + j * 1.3, 2.6, 0.55)], "horn", r="fang", tip="+y")],
            box(sx * 5.4, 11.6, 0.6, 3.0, 2.2, 3.2, "leather", r="leather", seed=139, marks=0.0),
            *[cc for i in range(3) for cc in claw_blade(B, sx * 5.4 + (i - 1) * 1.0, 10.8, -0.3, (i - 1) * 6, length=10.4, w=1.5, t=0.42, seed=140 + i)],
        ], rot=[-18, 0, sx * 4])
    GC.register(HUNGER, lambda aff: GC.pal("blood", BLOODRED, {
        "skin": (70, 58, 60), "fur": (22, 16, 17), "leather": (50, 30, 24),
        "bone": [(40, 30, 24), (104, 86, 68), (172, 154, 126), (216, 204, 178), (244, 238, 220)],
        "horn": [(10, 8, 8), (30, 24, 22), (70, 60, 54), (138, 124, 108), (212, 200, 180)],
        "claw": [(6, 5, 5), (20, 16, 16), (50, 42, 40), (100, 90, 84), (170, 160, 150)],
        "fang": [(60, 50, 40), (130, 116, 96), (196, 184, 160), (232, 224, 204), (252, 248, 236)],
        "cloth": [(10, 2, 4), (44, 6, 12), (100, 14, 24), (150, 32, 40), (200, 80, 80)],
        "blade": [(14, 14, 20), (58, 56, 66), (128, 124, 136), (200, 198, 210), (250, 250, 255)],
    }, tinted=()), density=4.0)
    B.write(3.4, 2.4, 1.4)
    hunger_anims()


def hunger_anims():
    """Times match HungerMonarch and MonarchBoss: intro 110 ticks (down out of his gate over the first 80, landing at
    79), phase changes 46, death 100 (held), leaving 90. Moves: claw 18 (the blow lands at 8), rend 32 (9 and 21),
    pounce 36 (in the air from 4 to 18, landing at 18), howl 40 (the howl at 14, its ring at 18), blood_frenzy 76 (four
    dashes, each aimed 10 ticks and through at 16, 32, 48, 64), maw 44 (the maw closes at 26), the_feast 150 (a howl
    over the first 40, then six dashes through at 50, 66, 82, 98, 114, 130)."""
    A = {}
    bones = ("root", "hips", "right_leg", "right_shin", "right_foot", "left_leg", "left_shin", "left_foot", "tail", "tail2", "body", "cape",
             "head", "jaw", "right_arm", "right_hand", "left_arm", "left_hand")
    STAND = pose(**{b: [0, 0, 0] for b in bones})

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def legs(k=1.0, side=None):
        """Knees bent by k (a crouch): thighs forward, shins back, feet flatter, hips down."""
        out = dict(hips_pos=[0, -2.6 * k, 0.6 * k])
        for s_ in ("right", "left"):
            out[s_ + "_leg"] = [-22 * k, 0, 0]
            out[s_ + "_shin"] = [30 * k, 0, 0]
            out[s_ + "_foot"] = [-12 * k, 0, 0]
        return out

    # idle (2.4 s): heavy breaths through his teeth, his head jerking from one scent to the next, his tail switching
    L = 2.4
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [1.6 * wave(t, L / 2), 0, 0], "body_pos": lambda t: [0, 0.25 * wave(t, L / 2), 0],
        "head": lambda t: [3 * wave(t, L / 2, 0.2), 14 * ease_in_out(window(t, 0.3, 0.45)) * (1 - ease_in_out(window(t, 1.2, 1.35)))
                           - 10 * ease_in_out(window(t, 1.5, 1.6)) * (1 - ease_in_out(window(t, 2.1, 2.3))), 0],
        "jaw": lambda t: [5 + 3 * wave(t, L / 2), 0, 0], "tail": lambda t: [0, 14 * wave(t, L), 0], "tail2": lambda t: [0, 18 * wave(t, L, 0.2), 0],
        "right_arm": lambda t: [3 * wave(t, L / 2, 0.1), 0, 0], "left_arm": lambda t: [3 * wave(t, L / 2, 0.35), 0, 0],
        "right_hand": lambda t: [0, 0, 3 * wave(t, L, 0.3)], "left_hand": lambda t: [0, 0, -3 * wave(t, L, 0.6)],
        "cape": lambda t: [2 * wave(t, L, 0.3), 0, 0],
    }), 0.05)

    # walk (1.0 s): a prowling, loping stride on his toes, his arms swinging their blades
    L = 1.0
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [26 * wave(t, L), 0, 0], "left_leg": lambda t: [-26 * wave(t, L), 0, 0],
        "right_shin": lambda t: [-24 * max(0.0, wave(t, L, 0.25)), 0, 0], "left_shin": lambda t: [-24 * max(0.0, wave(t, L, 0.75)), 0, 0],
        "right_foot": lambda t: [18 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_foot": lambda t: [18 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips": lambda t: [0, 6 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.7 * abs(wave(t, L)), 0],
        "body": lambda t: [3, -8 * wave(t, L), 0], "head": lambda t: [-3, 7 * wave(t, L), 0],
        "right_arm": lambda t: [-24 * wave(t, L), 0, 0], "left_arm": lambda t: [24 * wave(t, L), 0, 0],
        "right_hand": lambda t: [-12 * max(0.0, -wave(t, L)), 0, 0], "left_hand": lambda t: [-12 * max(0.0, wave(t, L)), 0, 0],
        "tail": lambda t: [6, 12 * wave(t, L), 0], "cape": lambda t: [8 + 4 * abs(wave(t, L)), 0, 0],
    }), 0.05)

    # dormant (5 s): crouched low over his kill, his blades on the ground
    L = 5.0
    LOW = P(legs(1.6), body=[30, 0, 0], head=[-20, 0, 0], right_arm=[-30, 0, 6], left_arm=[-30, 0, -6], right_hand=[-10, 0, 0], left_hand=[-10, 0, 0],
            tail=[30, 0, 0], cape=[-10, 0, 0])
    A["dormant"] = anim(L, True, timeline([(0, LOW, lin), (L, LOW, lin)], extra={
        "body": lambda t: [1.2 * wave(t, L / 2), 0, 0], "jaw": lambda t: [6 + 4 * wave(t, L / 2), 0, 0],
    }), 0.1)

    # claw (0.9 s): his right blades up behind his shoulder and torn down across his front (0.4 s)
    L = 0.9
    WIND_R = P(legs(0.6), body=[-6, 26, 0], head=[0, -22, 0], right_arm=[-150, 0, 34], right_hand=[-30, 0, 0], left_arm=[-30, 0, -20],
               left_hand=[-20, 0, 0], jaw=[18, 0, 0], tail=[0, 20, 0])
    RAKE_R = P(legs(1.0), body=[18, -30, 0], head=[-14, 24, 0], hips_pos=[0, -2.6, -1.8], right_arm=[-40, -30, -40], right_hand=[-20, 0, 0],
               left_arm=[10, 0, -24], left_hand=[-10, 0, 0], jaw=[24, 0, 0], tail=[0, -20, 0], cape=[16, 0, 0])
    A["claw"] = anim(L, False, timeline([(0, STAND, lin), (0.26, WIND_R, ease_in_out), (0.42, RAKE_R, ease_in), (0.56, RAKE_R, lin),
                                         (0.9, STAND, ease_in_out)]), 0.025)

    # rend (1.6 s): right blades (0.45 s) and back across with the left (1.05 s)
    L = 1.6
    WIND_L = P(legs(0.6), body=[-6, -26, 0], head=[0, 22, 0], left_arm=[-150, 0, -34], left_hand=[-30, 0, 0], right_arm=[-30, 0, 20],
               right_hand=[-20, 0, 0], jaw=[18, 0, 0], tail=[0, -20, 0])
    RAKE_L = P(legs(1.0), body=[18, 30, 0], head=[-14, -24, 0], hips_pos=[0, -2.6, -1.8], left_arm=[-40, 30, 40], left_hand=[-20, 0, 0],
               right_arm=[10, 0, 24], right_hand=[-10, 0, 0], jaw=[24, 0, 0], tail=[0, 20, 0], cape=[16, 0, 0])
    A["rend"] = anim(L, False, timeline([(0, STAND, lin), (0.3, WIND_R, ease_in_out), (0.47, RAKE_R, ease_in), (0.6, RAKE_R, lin),
                                         (0.88, WIND_L, ease_in_out), (1.07, RAKE_L, ease_in), (1.22, RAKE_L, lin), (1.6, STAND, ease_in_out)]), 0.025)

    # pounce (1.8 s): down in a crouch, off the ground at 0.2 s, his blades raised over his head in the air, driven into
    # the ground as he lands (0.9 s)
    L = 1.8
    CROUCH = P(legs(2.0), body=[24, 0, 0], head=[-24, 0, 0], right_arm=[40, 0, 10], left_arm=[40, 0, -10], right_hand=[-20, 0, 0],
               left_hand=[-20, 0, 0], tail=[30, 0, 0], jaw=[14, 0, 0])
    SPRING = P(body=[-10, 0, 0], head=[0, 0, 0], right_leg=[30, 0, 0], left_leg=[24, 0, 0], right_shin=[-30, 0, 0], left_shin=[-26, 0, 0],
               right_foot=[30, 0, 0], left_foot=[30, 0, 0], right_arm=[-120, 0, 20], left_arm=[-120, 0, -20], right_hand=[-20, 0, 0],
               left_hand=[-20, 0, 0], tail=[-20, 0, 0], jaw=[24, 0, 0], cape=[30, 0, 0])
    AIR = P(SPRING, body=[-16, 0, 0], right_leg=[-50, 0, 0], left_leg=[-40, 0, 0], right_shin=[40, 0, 0], left_shin=[40, 0, 0],
            right_arm=[-142, 0, 24], left_arm=[-142, 0, -24], right_hand=[-30, 0, 0], left_hand=[-30, 0, 0], head=[-10, 0, 0], cape=[40, 0, 0])
    SMASH = P(legs(2.2), body=[50, 0, 0], head=[-40, 0, 0], right_arm=[-70, 0, 6], left_arm=[-70, 0, -6], right_hand=[-30, 0, 0],
              left_hand=[-30, 0, 0], jaw=[30, 0, 0], tail=[40, 0, 0], cape=[-20, 0, 0])
    A["pounce"] = anim(L, False, timeline([(0, STAND, lin), (0.18, CROUCH, ease_in_out), (0.3, SPRING, ease_out_back), (0.55, AIR, ease_in_out),
                                           (0.8, AIR, lin), (0.9, SMASH, ease_in), (1.25, SMASH, lin), (1.8, STAND, ease_in_out)]), 0.025)

    # howl (2 s): he gathers himself down, then rears up tall, head back, arms flung wide, and howls (0.7 to 1.6 s)
    L = 2.0
    GATHER = P(legs(1.4), body=[34, 0, 0], head=[20, 0, 0], right_arm=[-20, -30, -30], left_arm=[-20, 30, 30], right_hand=[-40, 0, 0],
               left_hand=[-40, 0, 0], tail=[30, 0, 0])
    HOWL = P(body=[-30, 0, 0], head=[-40, 0, 0], jaw=[40, 0, 0], hips_pos=[0, 0.6, 0], right_arm=[-60, 0, 60], left_arm=[-60, 0, -60],
             right_hand=[-10, 0, 0], left_hand=[-10, 0, 0], tail=[-20, 0, 0], cape=[20, 0, 0], right_leg=[10, 0, 0], left_leg=[10, 0, 0])
    A["howl"] = anim(L, False, timeline([(0, STAND, lin), (0.5, GATHER, ease_in_out), (0.72, HOWL, ease_out_back), (1.6, HOWL, lin),
                                         (2.0, STAND, ease_in_out)],
                                        extra={"head": lambda t: [0, 0, shake(t, 0.72, 1.6, 4, 40)], "jaw": lambda t: [shake(t, 0.72, 1.6, 3, 31), 0, 0]}), 0.025)

    # the dashes: aimed low and coiled (half a second), then through his prey and out the far side, blades swept out
    # behind him
    READY = P(legs(1.8), body=[40, 0, 0], head=[-34, 0, 0], right_arm=[50, 0, 20], left_arm=[50, 0, -20], right_hand=[-30, 0, 0],
              left_hand=[-30, 0, 0], jaw=[22, 0, 0], tail=[10, 0, 0], cape=[-10, 0, 0])
    THRU_R = P(legs(1.2), body=[30, -20, 0], head=[-30, 16, 0], hips_pos=[0, -2.0, -1.6], right_arm=[40, 30, 70], right_hand=[-10, 0, 0],
               left_arm=[-60, 0, -30], left_hand=[-20, 0, 0], jaw=[30, 0, 0], tail=[-30, -20, 0], cape=[40, 0, 0])
    THRU_L = P(THRU_R, body=[30, 20, 0], head=[-30, -16, 0], right_arm=[-60, 0, 30], right_hand=[-20, 0, 0], left_arm=[40, -30, -70],
               left_hand=[-10, 0, 0], tail=[-30, 20, 0])

    def dash_steps(t0, n, each=0.8, aim=0.5):
        out = []
        for i in range(n):
            a = t0 + i * each
            thru = THRU_R if i % 2 == 0 else THRU_L
            out += [(a + 0.12, READY, ease_in_out), (a + aim - 0.04, P(READY, body=[46, 0, 0], hips_pos=[0, -5.2, 1.6]), ease_in_out),
                    (a + aim, thru, ease_out_back), (a + aim + 0.26, thru, lin)]
        return out

    L = 3.8
    A["blood_frenzy"] = anim(L, False, timeline([(0, STAND, lin)] + dash_steps(0.3, 4) + [(3.8, STAND, ease_in_out)],
                                                extra={"head": lambda t: [0, 0, shake(t, 0.3, 3.2, 1.5, 37)]}), 0.025)

    # maw (2.2 s): his arms raised wide as jaws, his own jaw gaping, and brought crashing together as the maw closes
    # (1.3 s)
    L = 2.2
    OPEN = P(legs(0.8), body=[-16, 0, 0], head=[-30, 0, 0], jaw=[40, 0, 0], right_arm=[-160, 0, 50], left_arm=[-160, 0, -50],
             right_hand=[-20, 0, 0], left_hand=[-20, 0, 0], tail=[-20, 0, 0], cape=[10, 0, 0])
    CLOSE = P(legs(1.8), body=[44, 0, 0], head=[-30, 0, 0], jaw=[0, 0, 0], right_arm=[-70, -40, -20], left_arm=[-70, 40, 20],
              right_hand=[-30, 0, 0], left_hand=[-30, 0, 0], tail=[30, 0, 0], cape=[-10, 0, 0])
    A["maw"] = anim(L, False, timeline([(0, STAND, lin), (0.6, OPEN, ease_in_out), (1.12, OPEN, lin), (1.3, CLOSE, ease_in), (1.7, CLOSE, lin),
                                        (2.2, STAND, ease_in_out)],
                                       extra={"jaw": lambda t: [shake(t, 0.6, 1.12, 3, 33), 0, 0], "body": lambda t: [shake(t, 1.3, 1.6, 1.5, 50, 0.1), 0, 0]}), 0.025)

    # the_feast (7.5 s), his authority: a howl that marks them all (to 1.8 s), then six dashes through them
    L = 7.5
    A["the_feast"] = anim(L, False, timeline([(0, STAND, lin), (0.4, GATHER, ease_in_out), (0.6, HOWL, ease_out_back), (1.7, HOWL, lin)]
                                             + dash_steps(2.0, 6) + [(7.5, STAND, ease_in_out)],
                                             extra={"head": lambda t: [0, 0, shake(t, 0.6, 1.7, 4, 40)]}), 0.025)

    # intro (5.5 s): down out of his gate arms wide, landing on one knee with a fist of blades driven into the ground
    # (3.95 s); he rises and howls
    L = 5.5
    FALL = P(body=[-14, 0, 0], head=[20, 0, 0], right_arm=[-30, 0, 70], left_arm=[-30, 0, -70], right_hand=[-10, 0, 0], left_hand=[-10, 0, 0],
             right_leg=[20, 0, 0], left_leg=[14, 0, 0], right_shin=[-20, 0, 0], left_shin=[-14, 0, 0], right_foot=[30, 0, 0], left_foot=[30, 0, 0],
             tail=[-30, 0, 0], cape=[50, 0, 0])
    TUCK = P(FALL, body=[10, 0, 0], right_arm=[-120, 0, 30], left_arm=[-60, 0, -40], right_leg=[-40, 0, 0], left_leg=[-30, 0, 0],
             right_shin=[40, 0, 0], left_shin=[40, 0, 0])
    LANDING = P(hips_pos=[0, -6.4, 0.6], right_leg=[-60, 0, 0], right_shin=[60, 0, 0], right_foot=[-10, 0, 0], left_leg=[10, 0, 0],
                left_shin=[80, 0, 0], left_foot=[40, 0, 0], body=[40, 0, 0], head=[-30, 0, 0], right_arm=[-70, 0, 10], right_hand=[-40, 0, 0],
                left_arm=[50, 0, -40], left_hand=[-10, 0, 0], tail=[30, 0, 0], cape=[-20, 0, 0], jaw=[20, 0, 0])
    A["intro"] = anim(L, False, timeline([(0, FALL, lin), (3.6, FALL, lin), (3.85, TUCK, ease_in_out), (3.95, LANDING, ease_in), (4.5, LANDING, lin),
                                          (4.85, GATHER, ease_in_out), (5.0, HOWL, ease_out_back), (5.4, HOWL, lin), (5.5, STAND, ease_in_out)],
                                         extra={"cape": lambda t: [6 * math.sin(t * 9) * (1 - window(t, 3.6, 3.9)), 0, 0],
                                                "tail2": lambda t: [10 * math.sin(t * 7) * (1 - window(t, 3.6, 3.9)), 0, 0],
                                                "body": lambda t: [shake(t, 3.95, 4.3, 1.5, 50, 0.1), 0, 0]}), 0.025)

    # the phase changes (2.3 s): II, he drags his blades across each other before his face and roars; III, down on all
    # fours, snarling, and up into a howl
    L = 2.3
    SCRAPE_A = P(legs(0.8), body=[10, 0, 0], head=[-10, 0, 0], jaw=[20, 0, 0], right_arm=[-90, -40, -10], left_arm=[-90, 40, 10],
                 right_hand=[-50, 0, 0], left_hand=[-50, 0, 0])
    SCRAPE_B = P(SCRAPE_A, right_arm=[-90, 30, 30], left_arm=[-90, -30, -30])
    ROAR = P(body=[-10, 0, 0], head=[-24, 0, 0], jaw=[44, 0, 0], right_arm=[-30, 0, 40], left_arm=[-30, 0, -40], right_hand=[-30, 0, 0],
             left_hand=[-30, 0, 0], tail=[-20, 0, 0], cape=[20, 0, 0])
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.35, SCRAPE_A, ease_in_out), (0.6, SCRAPE_B, ease_in), (0.8, SCRAPE_A, ease_in_out),
                                           (1.0, SCRAPE_B, ease_in), (1.25, ROAR, ease_out_back), (1.9, ROAR, lin), (2.3, STAND, ease_in_out)],
                                          extra={"head": lambda t: [0, 0, shake(t, 1.25, 1.9, 4, 40)]}), 0.025)
    FOURS = P(legs(1.8), body=[56, 0, 0], head=[-50, 0, 0], jaw=[26, 0, 0], right_arm=[-30, 0, 10], left_arm=[-30, 0, -10],
              right_hand=[-20, 0, 0], left_hand=[-20, 0, 0], tail=[20, 0, 0], cape=[-20, 0, 0])
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.4, FOURS, ease_in_out), (1.0, FOURS, lin), (1.25, HOWL, ease_out_back), (1.9, HOWL, lin),
                                           (2.3, STAND, ease_in_out)],
                                          extra={"head": lambda t: [shake(t, 0.4, 1.0, 2, 35), 0, shake(t, 1.25, 1.9, 4, 40)]}), 0.025)

    # death (5 s, held): he reels back, drops to his knees, and slumps forward over them, his blades in the dirt
    L = 5.0
    REEL = P(body=[-20, 0, 8], head=[-30, 0, 10], jaw=[30, 0, 0], right_arm=[20, 0, 30], left_arm=[10, 0, -20], hips_pos=[0, 0, 1.4],
             right_leg=[-10, 0, 0], tail=[-20, 0, 0])
    KNEES = P(hips_pos=[0, -6.2, 1.0], right_leg=[30, 0, 4], left_leg=[30, 0, -4], right_shin=[20, 0, 0], left_shin=[20, 0, 0], right_foot=[60, 0, 0],
              left_foot=[60, 0, 0], body=[-6, 0, 0], head=[-20, 0, 0], jaw=[24, 0, 0], right_arm=[10, 0, 10], left_arm=[10, 0, -10],
              right_hand=[0, 0, 0], left_hand=[0, 0, 0], tail=[40, 0, 0])
    SLUMPED = P(KNEES, body=[50, 0, 4], head=[20, 0, 10], jaw=[16, 0, 0], right_arm=[-20, 0, 6], left_arm=[-26, 0, -4], right_hand=[-20, 0, 0],
                left_hand=[-10, 0, 0], cape=[-30, 0, 0])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.4, REEL, ease_out_back), (1.1, KNEES, ease_in), (1.3, P(KNEES, hips_pos=[0, -5.8, 1.0]), ease_in_out),
                                           (2.4, SLUMPED, ease_in_out), (5.0, SLUMPED, lin)]), 0.05)

    # leave (4.5 s): pulled back up into the sky, limbs hanging, his head thrown back
    L = 4.5
    HANG = P(body=[-20, 0, 0], head=[-30, 0, 0], jaw=[16, 0, 0], right_arm=[10, 0, 20], left_arm=[10, 0, -20], right_hand=[0, 0, 0], left_hand=[0, 0, 0],
             right_leg=[24, 0, 0], left_leg=[30, 0, 0], right_shin=[-30, 0, 0], left_shin=[-40, 0, 0], right_foot=[40, 0, 0], left_foot=[40, 0, 0],
             tail=[-30, 0, 0], cape=[-10, 0, 0])
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (1.0, HANG, ease_in_out), (L, HANG, lin)],
                                         extra={"tail2": lambda t: [8 * math.sin(t * 5), 0, 0]}), 0.05)
    GC.write_anims(HUNGER, A)


# ============================================================================================= Thyrax, Monarch of Storms

STORMS = "monarch_storms"
G.MODEL_SCALE[STORMS] = 1.9
VIOLET = (185, 166, 255)


def storm_wing(B, side, sx, seed):
    """A great wing of steel feathers, built folded the way it rests (its arm running back from his shoulder blade,
    the feathers hanging from it; the hand of the wing turned down behind him with its long feathers fanned from the
    wrist). Spread it by turning the arm out to the side (y) and up (z), the hand straight out (x)."""
    x0, y0, z0 = sx * 1.7, 27.6, 2.9
    fe = dict(r="feather", fuller=False, marks=0.0, edge="front")
    arm = [B.c([x0 - 0.75, y0 - 0.75, z0 - 0.4], [1.5, 1.5, 10.0], "plate", r="plate", rivets=False, dents=False, grime=False, marks=0.0, seed=seed),
           B.c([x0 - 0.9, y0 - 0.5, z0 + 7.6], [1.8, 1.8, 2.0], "plate", r="trim", rivets=False, dents=False, grime=False, marks=0.0, seed=seed + 1)]
    for i in range(6):
        z = z0 + 0.9 + i * 1.45
        ln = 7.2 + i * 0.7
        rach = [dict(pts=[(z, y0 - 1.0), (z, y0 - ln + 0.4)], w=0.14, faces=("west", "east"), halo=0.35, bright=0.55)]
        arm += [B.c([x0 - 0.15, y0 - 0.5 - ln, z - 0.85], [0.3, ln, 1.7], "metal", long="y", seed=seed + 2 + i, pivot=[x0, y0 - 0.5, z],
                    rot=[-18 + i * 2, 0, 0], lines=rach, **fe),
                B.c([x0 - 0.12, y0 - 0.5 - ln - 1.5, z - 0.5], [0.24, 1.6, 1.0], "metal", long="y", seed=seed + 2 + i, pivot=[x0, y0 - 0.5, z],
                    rot=[-18 + i * 2, 0, 0], **fe)]
    B.bone(f"{side}_wing", "body", [x0, y0, z0], arm, rot=[35, sx * 10, 0])
    wz = z0 + 9.2
    hand = [B.c([x0 - 0.6, y0 - 0.6, wz - 0.2], [1.2, 1.2, 3.4], "plate", r="plate", rivets=False, dents=False, grime=False, marks=0.0, seed=seed + 10),
            *GC.limb(B, [x0, y0 + 0.4, wz + 0.4], [(x0, y0 + 2.2, wz - 0.4, 0.6)], "metal", r="trim", long="y", fuller=False, marks=0.0)]
    for i in range(6):
        a = 4 + i * 7.0
        ln = 18.0 - i * 1.0
        rach = [dict(pts=[(wz + 0.6, y0), (wz + ln - 0.8, y0)], w=0.14, faces=("west", "east"), halo=0.35, bright=0.6)]
        hand += [B.c([x0 - 0.15, y0 - 0.85, wz], [0.3, 1.7, ln], "metal", long="z", seed=seed + 11 + i, pivot=[x0, y0, wz], rot=[-a, 0, 0],
                     lines=rach, **fe),
                 B.c([x0 - 0.12, y0 - 0.5, wz + ln], [0.24, 1.0, 1.7], "metal", long="z", seed=seed + 11 + i, pivot=[x0, y0, wz], rot=[-a, 0, 0], **fe)]
    B.bone(f"{side}_wing2", f"{side}_wing", [x0, y0, wz], hand, rot=[-110, 0, 0])


def storms():
    """Thyrax, built 33.5 units tall to the top of his helm and drawn 1.9 times bigger (4 blocks, his hitbox). Bones:
    root, hips (faulds and a violet tabard), legs and shins, body, head (a closed helm, a crest of steel feathers),
    arms and hands, glaive (in his right hand), the wings (right_wing and right_wing2, the hand of the wing; the same
    on the left)."""
    B = OneSkin(STORMS, 5900)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    pl = dict(r="plate", engrave="trim", rivets=False, marks=0.0, grime=False)
    lp = dict(r="plate", rivets=False, marks=0.0, dents=False, grime=False)
    tm = dict(r="trim", marks=0.0)
    fe = dict(r="feather", fuller=False, marks=0.0, edge="front")
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17.4, 0], [
        box(0, 18.2, 0.0, 6.2, 2.4, 4.2, "plate", **lp, seed=10),
        box(0, 19.3, 0.0, 6.6, 0.9, 4.6, "trim", **tm),
        box(0, 19.3, -2.35, 1.4, 1.4, 0.4, "glow", pivot=[0, 19.3, -2.35], rot=[0, 0, 45]),
        # faulds: lames of plate over the hips, front and sides
        *[c(mx([0.4, 15.0 - j * 1.6, -2.55 + j * 0.15], [2.8, 2.0, 0.6], sx), [2.8, 2.0, 0.6], "plate", **pl, seed=12 + j * 2 + (sx > 0),
            pivot=[sx * 1.8, 17.0, -2.2], rot=[-8 - j * 4, 0, -sx * 8]) for sx in (-1, 1) for j in range(2)],
        *[c(mx([2.9, 13.2, -1.8], [0.6, 4.6, 3.8], sx), [0.6, 4.6, 3.8], "plate", **pl, seed=16 + (sx > 0), pivot=[sx * 3.1, 17.6, 0.0],
            rot=[0, 0, -sx * 12]) for sx in (-1, 1)],
        # the tabard, violet, hemmed in a thread of light
        c([-1.7, 5.4, -2.6], [3.4, 12.8, 0.5], "cloth", seed=18, folds=1.6, hem=1.4, glow_hem=1.6, band=(6.2, 7.0), marks=0.0, skip=("up", "down")),
        c([-2.6, 6.0, 2.1], [5.2, 12.4, 0.5], "cloth", seed=19, folds=1.6, hem=1.6, glow_hem=1.8, band=(6.8, 7.6), marks=0.0, skip=("up", "down")),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 17.0, 0.0], [
            col(sx * 2.0, 0.0, 9.6, 17.6, 3.0, 3.2, "plate", **lp, seed=20 + (sx > 0)),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.0, 9.6, 0.0], [
            box(sx * 2.0, 9.6, -1.5, 2.6, 2.2, 1.0, "plate", **pl, seed=22 + (sx > 0)),
            c([sx * 2.0 - 0.3, 9.8, -2.5], [0.6, 1.6, 0.6], "metal", r="feather", long="y", fuller=False, marks=0.0, pivot=[sx * 2.0, 10.2, -2.2], rot=[-30, 0, 0]),
            col(sx * 2.0, 0.0, 1.5, 9.8, 2.8, 3.0, "plate", **pl, seed=24 + (sx > 0)),
            box(sx * 2.0, 0.8, -0.6, 3.0, 1.6, 4.4, "plate", **lp, seed=26 + (sx > 0)),
            box(sx * 2.0, 0.6, -2.9, 2.4, 1.2, 1.0, "plate", **lp, seed=28 + (sx > 0)),
        ])
    B.bone("body", "hips", [0, 19.6, 0.0], [
        box(0, 21.4, 0.0, 5.6, 3.6, 3.8, "plate", **pl, seed=30),
        box(0, 25.6, -0.1, 8.4, 5.4, 4.8, "plate", **pl, seed=31),
        *[c(mx([0.1, 23.0, -2.75], [4.0, 5.0, 0.7], sx), [4.0, 5.0, 0.7], "plate", **lp, seed=32 + (sx > 0), pivot=[0, 25.5, -2.4],
            rot=[0, -sx * 14, 0]) for sx in (-1, 1)],
        # his sigil: a bolt of light on a plate of silver at his heart
        box(0, 25.4, -3.25, 1.8, 3.8, 0.4, "trim", **tm,
            lines=[dict(pts=[(0.35, 27.0), (-0.35, 25.7), (0.4, 25.4), (-0.3, 23.8)], w=0.3, faces=("north",), halo=0.6, bright=1.0)]),
        box(0, 28.9, -0.1, 5.2, 1.4, 4.4, "plate", **lp, seed=34),
        box(0, 26.2, 2.6, 6.0, 4.4, 0.8, "plate", **lp, seed=35),
    ])
    crest = []
    for i in range(6):
        # a crest of steel feathers along the helm, sweeping back
        z = -1.9 + i * 0.85
        h = 3.0 + 2.2 * math.sin(math.pi * (i + 0.5) / 6)
        crest.append(c([-0.2, 33.6, z - 0.6], [0.4, h, 1.2], "metal", r="feather", long="y", fuller=False, marks=0.0, edge="front",
                       pivot=[0, 33.9, z], rot=[-(28 + i * 9), 0, 0]))
    B.bone("head", "body", [0, 29.4, 0.0], [
        box(0, 31.8, -0.1, 4.4, 4.6, 4.6, "plate", **pl, seed=40,
            lines=[dict(pts=[(-1.6, 32.1), (1.6, 32.1)], w=0.34, faces=("north",), halo=0.6, bright=1.0),
                   dict(pts=[(0.0, 31.9), (0.0, 30.3)], w=0.22, faces=("north",), halo=0.4, bright=0.8)]),
        box(0, 32.95, -2.45, 4.6, 0.6, 0.5, "trim", **tm),
        box(0, 30.6, -2.5, 1.0, 2.6, 0.5, "plate", **lp, seed=41),
        *[c(mx([1.4, 29.6, -2.6], [0.9, 2.4, 1.3], sx), [0.9, 2.4, 1.3], "plate", **lp, seed=42 + (sx > 0), pivot=[sx * 1.8, 31.0, -2.0],
            rot=[0, sx * 16, 0]) for sx in (-1, 1)],
        box(0, 34.25, 0.0, 3.0, 0.5, 3.6, "plate", **lp, seed=44),
        *crest,
        # wings of steel at his temples
        *[cc for sx in (-1, 1) for j, (h, rx) in enumerate(((3.6, -58), (2.6, -78)))
          for cc in [c([sx * 2.3 - 0.15, 32.0, -0.6 + j * 0.9], [0.3, h, 1.0], "metal", **fe, long="y", pivot=[sx * 2.3, 32.2, -0.1 + j * 0.9],
                       rot=[rx, 0, -sx * 18])]],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 4.6, 28.4, 0.0]
        B.bone(f"{side}_arm", "body", piv, [
            col(sx * 5.0, 0.0, 22.4, 28.4, 2.6, 2.8, "plate", **lp, seed=50 + (sx > 0)),
            # a great pauldron of three plates rimmed in silver, steel feathers standing up from it
            c(mx([3.0, 27.6, -2.4], [3.9, 1.8, 4.8], sx), [3.9, 1.8, 4.8], "plate", **pl, seed=52 + (sx > 0), pivot=piv, rot=[0, 0, sx * 20]),
            c(mx([3.5, 26.3, -2.2], [3.5, 1.5, 4.4], sx), [3.5, 1.5, 4.4], "plate", **lp, seed=54 + (sx > 0), pivot=piv, rot=[0, 0, sx * 28]),
            c(mx([3.9, 25.2, -2.0], [3.0, 1.3, 4.0], sx), [3.0, 1.3, 4.0], "plate", **lp, seed=56 + (sx > 0), pivot=piv, rot=[0, 0, sx * 36]),
            c(mx([2.9, 27.4, -2.5], [4.1, 0.3, 5.0], sx), [4.1, 0.3, 5.0], "trim", **tm, pivot=piv, rot=[0, 0, sx * 20]),
            *[c([sx * (4.6 + j * 0.9) - 0.15, 29.0, -1.2 + j * 1.2], [0.3, 3.4 - j * 0.6, 1.0], "metal", **fe, long="y",
                pivot=[sx * (4.6 + j * 0.9), 29.0, -0.7 + j * 1.2], rot=[-24 - j * 8, 0, sx * 30]) for j in range(3)],
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.0, 22.6, 0.0], [
            box(sx * 5.0, 22.4, 0.9, 2.4, 2.0, 1.2, "plate", **lp, seed=58 + (sx > 0)),
            col(sx * 5.0, 0.0, 17.4, 22.6, 2.4, 2.6, "plate", **pl, seed=60 + (sx > 0)),
            box(sx * 5.0, 18.6, 0.0, 3.0, 1.6, 3.2, "plate", **lp, seed=62 + (sx > 0)),
            box(sx * 5.0, 16.4, -0.1, 2.6, 2.4, 2.8, "plate", **lp, seed=64 + (sx > 0)),
            box(sx * 5.0, 16.6, -1.6, 2.7, 0.6, 0.4, "trim", **tm),
        ])
    # the glaive: a long haft of dark steel bound in silver, a curved blade of lightning, a spike behind it
    hx, hz = -5.0, -0.1
    B.bone("glaive", "right_hand", [hx, 16.4, hz], [
        col(hx, hz, -2.0, 34.0, 0.8, 0.8, "metal", r="haft", long="y", fuller=False, seed=70, marks=0.0),
        *[col(hx, hz, y, y + 0.6, 1.05, 1.05, "trim", **tm) for y in (8.0, 20.0, 32.8)],
        col(hx, hz, 13.4, 19.6, 0.95, 0.95, "leather", r="grip", stitch=False, seed=71, marks=0.0),
        box(hx, 33.6, hz, 1.4, 0.8, 2.2, "trim", **tm),
        c([hx - 0.2, 34.0, hz - 2.0], [0.4, 6.2, 2.6], "crystal"),
        c([hx - 0.18, 39.6, hz - 1.6], [0.36, 4.6, 1.9], "crystal", pivot=[hx, 39.8, hz - 0.6], rot=[-16, 0, 0]),
        c([hx - 0.14, 43.6, hz - 0.9], [0.28, 2.2, 1.0], "crystal", pivot=[hx, 39.8, hz - 0.6], rot=[-26, 0, 0]),
        c([hx - 0.15, 34.2, hz + 0.7], [0.3, 0.9, 2.6], "crystal", pivot=[hx, 34.6, hz + 0.7], rot=[24, 0, 0]),
        c([hx - 0.3, -3.8, hz - 0.3], [0.6, 1.9, 0.6], "trim", **tm),
    ])
    storm_wing(B, "right", -1, 100)
    storm_wing(B, "left", 1, 130)
    GC.register(STORMS, lambda aff: GC.pal("storm", VIOLET, {
        "plate": [(6, 8, 16), (24, 28, 46), (60, 66, 96), (116, 124, 160), (190, 196, 226)],
        "trim": [(30, 32, 44), (86, 90, 108), (156, 160, 182), (212, 216, 232), (250, 250, 255)],
        "cloth": [(6, 4, 14), (22, 14, 44), (48, 32, 90), (84, 62, 140), (140, 118, 196)],
        "feather": [(8, 8, 18), (34, 34, 58), (84, 84, 122), (156, 156, 198), (228, 228, 252)],
        "haft": [(6, 6, 12), (22, 22, 34), (54, 54, 74), (104, 104, 132), (170, 170, 200)],
        "grip": [(6, 4, 12), (18, 12, 32), (40, 28, 66), (72, 56, 110), (120, 104, 160)],
        "leather": (40, 30, 64),
    }, tinted=()), density=4.0)
    B.write(6.0, 4.4, 1.6)
    storms_anims()


def storms_anims():
    """Times match StormMonarch and MonarchBoss: intro 110 ticks (down out of his gate on his wings over the first 80,
    landing at 79), phase changes 46, death 100 (held), leaving 90. Moves: glaive 28 (the sweep lands at 14), skewer 26
    (12), bolt_call 36 (the bolts fall at 20), wing_gust 30 (16), storm_orbs 50 (thrown at 8, 16 and 24), skyfall 64 (up
    from 2 to 20, hanging there to 34, the dive to 44), judgment 150 (the squares fall at 50 and 90, the last blow round
    him at 120)."""
    A = {}
    bones = ("root", "hips", "right_leg", "right_shin", "left_leg", "left_shin", "body", "head", "right_arm", "right_hand", "left_arm", "left_hand",
             "glaive", "right_wing", "right_wing2", "left_wing", "left_wing2")
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-14, 0, 10], right_hand=[-24, 0, 0], glaive=[38, 0, -16],
                 left_arm=[-6, 0, -8], left_hand=[-12, 0, 0])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    def wings(spread=1.0, up=25.0, sweep=0.0, tip=105.0):
        """Both wings: spread 0 (folded) to 1 (straight out), raised by up, swept forward by sweep, the hand of the wing
        opened by tip (105 straight)."""
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            out[side + "_wing"] = [-35 * spread, sx * (70 * spread + sweep), -sx * up * spread]
            out[side + "_wing2"] = [tip * spread, 0, 0]
        return out

    def flap(L, amp=32.0, tip=18.0, phase=0.0, on=None):
        """Wingbeats on top of a pose (period L): the arms of the wings up and down, the hands following behind."""
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            out[side + "_wing"] = (lambda t, sx=sx: [0, sx * 6 * wave(t, L, phase + 0.25) * (on(t) if on else 1), -sx * amp * wave(t, L, phase) * (on(t) if on else 1)])
            out[side + "_wing2"] = (lambda t: [-tip * wave(t, L, phase - 0.15) * (on(t) if on else 1), 0, 0])
        return out

    def merge(*ds):
        out = {}
        for d in ds:
            for k, fn in d.items():
                if k in out:
                    a, b = out[k], fn
                    out[k] = (lambda t, a=a, b=b: [x + y for x, y in zip(a(t), b(t))])
                else:
                    out[k] = fn
        return out

    SPREAD = wings(1.0)
    HALF = wings(0.45, up=30, tip=50)

    # idle (3.2 s): breathing under his plate, the folded wings stirring
    L = 3.2
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "body": lambda t: [0.8 * wave(t, L), 0, 0], "body_pos": lambda t: [0, 0.12 * wave(t, L), 0],
        "head": lambda t: [1.5 * wave(t, L, 0.3), 5 * wave(t, L * 2), 0],
        "right_wing": lambda t: [2 * wave(t, L, 0.2), 0, 1.5 * wave(t, L)], "left_wing": lambda t: [2 * wave(t, L, 0.2), 0, -1.5 * wave(t, L)],
        "right_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0], "left_wing2": lambda t: [-2 * wave(t, L, 0.4), 0, 0],
        "left_hand": lambda t: [3 * wave(t, L, 0.5), 0, 0],
    }), 0.05)

    # walk (1.3 s): a heavy armoured stride, the wings riding with it
    L = 1.3
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra={
        "right_leg": lambda t: [24 * wave(t, L), 0, 0], "left_leg": lambda t: [-24 * wave(t, L), 0, 0],
        "right_shin": lambda t: [28 * max(0.0, wave(t, L, 0.3)), 0, 0], "left_shin": lambda t: [28 * max(0.0, wave(t, L, 0.8)), 0, 0],
        "hips": lambda t: [0, 4 * wave(t, L), 0], "hips_pos": lambda t: [0, -0.45 * abs(wave(t, L)), 0],
        "body": lambda t: [2, -5 * wave(t, L), 0], "head": lambda t: [-1, 3 * wave(t, L), 0],
        "left_arm": lambda t: [14 * wave(t, L), 0, 0], "right_arm": lambda t: [-3 * wave(t, L), 0, 0],
        "right_wing": lambda t: [3 * abs(wave(t, L)), 0, 2 * wave(t, L)], "left_wing": lambda t: [3 * abs(wave(t, L)), 0, 2 * wave(t, L)],
    }), 0.05)

    # kneeling on his right knee, his glaive planted, his wings drooping round him (asleep, and how he dies)
    KNEEL = P(hips_pos=[0, -8.0, 0.6], right_leg=[4, 0, 0], right_shin=[88, 0, 0], left_leg=[-84, 0, -4], left_shin=[86, 0, 0],
              body=[14, 0, 0], head=[26, 0, 0], right_arm=[-40, 0, 10], right_hand=[-30, 0, 0], glaive=[56, 0, -14],
              left_arm=[-18, 0, -6], left_hand=[-30, 0, 0], **wings(0.35, up=-30, tip=40))
    L = 5.0
    A["dormant"] = anim(L, True, timeline([(0, KNEEL, lin), (L, KNEEL, lin)], extra={"body": lambda t: [0.8 * wave(t, L), 0, 0]}), 0.1)

    # the glaive held level at his hip and swept across his front from right to left (0.7 s), his wings flaring
    L = 1.4
    LEVEL = dict(right_arm=[-36, 0, 8], right_hand=[-40, 0, 0], glaive=[178, 0, -4], left_arm=[-50, 0, -6], left_hand=[-30, 0, 0])
    SW_A = P(**LEVEL, **HALF, body=[2, 50, 0], hips=[0, 16, 0], head=[0, -40, 0], hips_pos=[0, -1.0, 0], left_leg=[-14, 0, 0], right_leg=[8, 0, 0])
    SW_B = P(**LEVEL, **wings(0.6, up=10, tip=60), body=[8, -56, 0], hips=[0, -18, 0], head=[0, 34, 0], hips_pos=[0, -1.6, -0.6], left_leg=[-22, 0, 0],
             left_shin=[16, 0, 0], right_leg=[16, 0, 0])
    A["glaive"] = anim(L, False, timeline([(0, STAND, lin), (0.48, SW_A, ease_in_out), (0.76, SW_B, ease_in_out), (0.98, SW_B, lin),
                                           (1.4, STAND, ease_in_out)]), 0.025)

    # skewer (1.3 s): drawn back level and driven forward (0.6 s)
    L = 1.3
    DRAW = P(body=[4, 22, 0], head=[0, -18, 0], hips=[0, 10, 0], hips_pos=[0, -0.6, 1.0], right_arm=[30, -22, 6], right_hand=[-62, 0, 0],
             glaive=[122, 0, -6], left_arm=[-60, 0, -10], left_hand=[-20, 0, 0], left_leg=[-18, 0, 0], left_shin=[14, 0, 0], right_leg=[10, 0, 0],
             **wings(0.25, up=20, tip=30))
    THRUST = P(body=[10, -22, 0], head=[-6, 20, 0], hips=[0, -10, 0], hips_pos=[0, -1.4, -2.4], right_arm=[-76, 30, 4], right_hand=[-6, 0, 0],
               glaive=[172, 0, -4], left_arm=[30, 0, -16], left_hand=[-10, 0, 0], left_leg=[-34, 0, 0], left_shin=[30, 0, 0], right_leg=[24, 0, 0],
               right_shin=[6, 0, 0], **wings(0.5, up=0, sweep=-20, tip=50))
    A["skewer"] = anim(L, False, timeline([(0, STAND, lin), (0.42, DRAW, ease_in_out), (0.6, THRUST, ease_in), (0.85, THRUST, lin),
                                           (1.3, STAND, ease_in_out)]), 0.025)

    # bolt_call (1.8 s): the glaive raised to the sky, wings open, and brought down at them as the bolts fall (1.0 s)
    L = 1.8
    SKY = P(right_arm=[-172, 0, 6], right_hand=[-6, 0, 0], glaive=[178, 0, -6], left_arm=[-30, 0, -40], left_hand=[-10, 0, 0], head=[-24, 0, 0],
            body=[-8, 0, 0], **wings(0.8, up=40, tip=80))
    CALL = P(right_arm=[-80, 0, 6], right_hand=[-20, 0, 0], glaive=[165, 0, -6], left_arm=[-20, 0, -30], head=[6, 0, 0], body=[10, 0, 0],
             hips_pos=[0, -1.4, -0.6], left_leg=[-16, 0, 0], left_shin=[14, 0, 0], **wings(0.9, up=10, tip=90))
    A["bolt_call"] = anim(L, False, timeline([(0, STAND, lin), (0.65, SKY, ease_in_out), (0.85, SKY, lin), (1.0, CALL, ease_in), (1.3, CALL, lin),
                                              (1.8, STAND, ease_in_out)],
                                             extra={"right_hand": lambda t: [shake(t, 0.6, 0.85, 2, 50), 0, 0]}), 0.025)

    # wing_gust (1.5 s): wings raised high and back, then one great beat forward (0.65 to 0.8 s)
    L = 1.5
    GUST_UP = P(body=[-10, 0, 0], head=[-10, 0, 0], left_arm=[-30, 0, -30], hips_pos=[0, 0.4, 0], **wings(1.0, up=55, sweep=-30, tip=90))
    GUST = P(body=[14, 0, 0], head=[4, 0, 0], left_arm=[-60, 0, -10], hips_pos=[0, -1.6, -0.8], left_leg=[-18, 0, 0], left_shin=[16, 0, 0],
             right_leg=[12, 0, 0], **wings(1.0, up=-10, sweep=60, tip=130))
    A["wing_gust"] = anim(L, False, timeline([(0, STAND, lin), (0.55, GUST_UP, ease_in_out), (0.8, GUST, ease_in), (1.0, GUST, lin), (1.5, STAND, ease_in_out)]), 0.025)

    # storm_orbs (2.5 s): his left hand raised, an orb flung from over his head at 0.4, 0.8 and 1.2 s; wings half open
    L = 2.5
    RAISE_L = P(left_arm=[-170, 0, -10], left_hand=[-10, 0, 0], head=[-14, 0, 0], body=[-4, -8, 0], **HALF)
    FLING = P(left_arm=[-110, -20, -10], left_hand=[-4, 0, 0], head=[-4, 10, 0], body=[6, 10, 0], hips_pos=[0, -0.6, -0.4], **HALF)
    steps = [(0, STAND, lin), (0.25, RAISE_L, ease_in_out)]
    for rel in (0.4, 0.8, 1.2):
        steps += [(rel, FLING, ease_in), (rel + 0.22, RAISE_L, ease_in_out)]
    steps += [(1.7, RAISE_L, lin), (2.5, STAND, ease_in_out)]
    A["storm_orbs"] = anim(L, False, timeline(steps), 0.025)

    # skyfall (3.2 s): up on beating wings (to 1.0 s), hanging there with the glaive raised (to 1.7 s), the dive with his
    # wings swept back (1.7 to 2.2 s), the glaive driven into the ground
    L = 3.2
    LIFT = P(right_leg=[16, 0, 0], left_leg=[24, 0, 0], right_shin=[20, 0, 0], left_shin=[30, 0, 0], head=[14, 0, 0], **SPREAD)
    HOVER = P(LIFT, right_arm=[-170, 0, 6], right_hand=[-6, 0, 0], glaive=[178, 0, -6], left_arm=[-40, 0, -30], head=[24, 0, 0])
    # the dive: pitched forward, both hands on the haft, the blade leading forward and down at the ground (the glaive
    # turned on past the hands, so 239 here and 286 driven in, not the short way back through his body)
    DIVE = P(body=[46, 0, 0], head=[-28, 0, 0], right_arm=[-120, 0, 8], right_hand=[-24, 0, 0], glaive=[239, 0, 0], left_arm=[-112, -26, -4],
             left_hand=[-30, 0, 0], right_leg=[30, 0, 0], left_leg=[40, 0, 0], right_shin=[20, 0, 0], left_shin=[30, 0, 0],
             **wings(0.7, up=45, sweep=-70, tip=60))
    IMPACT = P(KNEEL, body=[24, 0, 0], head=[-10, 0, 0], right_arm=[-120, 0, 4], right_hand=[-24, 0, 0], glaive=[-74, 0, 0], left_arm=[-60, 0, -40],
               **wings(1.0, up=-15, sweep=10, tip=100))
    IMPACT_DIVE = P(IMPACT, glaive=[286, 0, 0])
    on_rise = (lambda t: window(t, 0.05, 0.2) * (1 - window(t, 1.6, 1.75)))
    A["skyfall"] = anim(L, False, timeline([(0, STAND, lin), (0.15, LIFT, ease_in_out), (1.0, LIFT, lin), (1.3, HOVER, ease_in_out), (1.65, HOVER, lin),
                                            (1.8, DIVE, ease_in_out), (2.15, DIVE, lin), (2.22, IMPACT_DIVE, ease_in), (2.6, IMPACT_DIVE, lin), (3.2, STAND, ease_in_out)],
                                           extra=merge(flap(0.5, 38, 20, on=on_rise), {"body": lambda t: [shake(t, 2.22, 2.5, 1.5, 50, 0.1), 0, 0]})), 0.025)

    # judgment (7.5 s), his authority: he rises (to 1.2 s), the glaive brought down as each half of the squares falls
    # (2.5 and 4.5 s), then higher, the glaive turned point down, and the dive that ends it round him (6.0 s)
    L = 7.5
    UP = P(HOVER, root_pos=[0, 14, 0])
    DOWN = P(CALL, root_pos=[0, 14, 0], **SPREAD, right_leg=[16, 0, 0], left_leg=[24, 0, 0], right_shin=[20, 0, 0], left_shin=[30, 0, 0])
    HIGH = P(UP, root_pos=[0, 20, 0], body=[20, 0, 0], right_arm=[-150, 0, 6], right_hand=[-20, 0, 0], glaive=[300, 0, 0], left_arm=[-140, -24, -6],
             left_hand=[-26, 0, 0], head=[6, 0, 0], **wings(1.0, up=50, tip=100))
    on_air = (lambda t: window(t, 0.1, 0.4) * (1 - window(t, 5.4, 5.7)))
    A["judgment"] = anim(L, False, timeline([(0, STAND, lin), (1.2, UP, ease_in_out), (2.3, UP, lin), (2.5, DOWN, ease_in), (2.8, DOWN, lin),
                                             (3.4, UP, ease_in_out), (4.3, UP, lin), (4.5, DOWN, ease_in), (4.8, DOWN, lin), (5.5, HIGH, ease_in_out),
                                             (5.75, P(HIGH, root_pos=[0, 21, 0]), lin), (6.0, IMPACT_DIVE, ease_in), (6.6, IMPACT_DIVE, lin), (7.5, STAND, ease_in_out)],
                                            extra=merge(flap(0.8, 26, 16, on=on_air), {"body": lambda t: [shake(t, 6.0, 6.4, 1.5, 50, 0.1), 0, 0]})), 0.025)

    # intro (5.5 s): down out of his gate on slow wingbeats, the wings flared to land (3.95 s), and spread wide over the
    # hunters
    L = 5.5
    GLIDE = P(head=[16, 0, 0], left_arm=[-20, 0, -30], right_leg=[6, 0, 0], left_leg=[12, 0, 0], left_shin=[14, 0, 0], **SPREAD)
    FLARE = P(GLIDE, body=[-14, 0, 0], head=[20, 0, 0], right_leg=[-20, 0, 0], left_leg=[-10, 0, 0], **wings(1.0, up=50, sweep=-20, tip=110))
    LANDED = P(KNEEL, body=[20, 0, 0], head=[0, 0, 0], right_arm=[-60, 0, 14], right_hand=[-20, 0, 0], glaive=[80, 0, -14], left_arm=[-30, 0, -40],
               **wings(1.0, up=20, sweep=20, tip=100))
    DISPLAY = P(STAND, head=[-8, 0, 0], body=[-4, 0, 0], left_arm=[-40, 30, -40], right_arm=[-70, 0, 20], right_hand=[-20, 0, 0], glaive=[100, 0, -20],
                **wings(1.0, up=40, tip=105))
    on_glide = (lambda t: 1 - window(t, 3.4, 3.7))
    A["intro"] = anim(L, False, timeline([(0, GLIDE, lin), (3.5, GLIDE, lin), (3.85, FLARE, ease_in_out), (3.95, LANDED, ease_in), (4.4, LANDED, lin),
                                          (4.9, DISPLAY, ease_in_out), (5.2, DISPLAY, lin), (5.5, STAND, ease_in_out)],
                                         extra=merge(flap(1.2, 30, 18, on=on_glide), {"body": lambda t: [shake(t, 3.95, 4.3, 1.5, 50, 0.1), 0, 0]})), 0.025)

    # the phase changes (2.3 s): II, wings thrown open and the glaive lifted into the storm; III, wings wrapped round him,
    # then flung wide, his head back
    L = 2.3
    A["phase2"] = anim(L, False, timeline([(0, STAND, lin), (0.5, P(SKY, **SPREAD), ease_out_back), (1.8, P(SKY, **SPREAD), lin), (2.3, STAND, ease_in_out)],
                                          extra={"right_hand": lambda t: [shake(t, 0.5, 1.8, 2, 50), 0, 0], "head": lambda t: [0, 0, shake(t, 0.5, 1.8, 3, 40)]}), 0.025)
    WRAP = P(STAND, body=[18, 0, 0], head=[24, 0, 0], left_arm=[-60, -30, 20], left_hand=[-40, 0, 0], hips_pos=[0, -1.4, 0.4],
             **wings(0.9, up=-20, sweep=50, tip=120))
    WIDE = P(STAND, body=[-14, 0, 0], head=[-34, 0, 0], left_arm=[-20, 0, -80], left_hand=[0, 0, 0], right_arm=[-24, 0, 50], right_hand=[-10, 0, 0],
             glaive=[30, 0, -52], hips_pos=[0, 0.4, 0], **wings(1.0, up=45, sweep=-20, tip=105))
    A["phase3"] = anim(L, False, timeline([(0, STAND, lin), (0.6, WRAP, ease_in_out), (0.85, WIDE, ease_out_back), (1.8, WIDE, lin), (2.3, STAND, ease_in_out)],
                                          extra={"body": lambda t: [shake(t, 0.85, 1.8, 1.0, 45), 0, 0]}), 0.025)

    # death (5 s, held): struck, he reels, sinks to his knee leaning on his glaive, his wings fall open on the ground
    L = 5.0
    REEL = P(STAND, body=[-14, -8, 0], head=[-24, 0, 6], left_arm=[-50, 20, -10], left_hand=[-60, 0, 0], hips_pos=[0, 0, 1.0], right_leg=[-10, 0, 0],
             left_leg=[14, 0, 0], left_shin=[10, 0, 0], **wings(0.6, up=30, tip=70))
    FALLEN = P(KNEEL, head=[40, 0, 8], body=[22, 0, 4], left_arm=[-4, 0, -4], left_hand=[-6, 0, 0], right_arm=[-46, 0, 14], glaive=[62, 0, -18],
               **wings(0.9, up=-40, sweep=-10, tip=70))
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.35, REEL, ease_out_back), (1.1, KNEEL, ease_in), (1.3, P(KNEEL, hips_pos=[0, -7.6, 0.6]), ease_in_out),
                                           (2.4, FALLEN, ease_in_out), (5.0, FALLEN, lin)]), 0.05)

    # leave (4.5 s): wings open, beating, he rises back into the sky
    L = 4.5
    A["leave"] = anim(L, False, timeline([(0, STAND, lin), (0.8, GLIDE, ease_in_out), (L, P(GLIDE, head=[-20, 0, 0]), lin)],
                                         extra=flap(0.9, 34, 20, on=lambda t: window(t, 0.5, 0.9))), 0.05)
    GC.write_anims(STORMS, A)


# ============================================================================================= main

def main():
    want = set(sys.argv[1:])
    makers = {"titan": titan, "behemoth": behemoth, "stillness": stillness, "hunger": hunger, "storms": storms}
    for key, fn in makers.items():
        if not want or key in want:
            print(key)
            fn()


if __name__ == "__main__":
    main()
