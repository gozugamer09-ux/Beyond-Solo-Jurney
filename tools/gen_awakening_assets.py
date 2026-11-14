#!/usr/bin/env python3
"""
Generates the awakening trial's models, animations and textures (no hand-drawn files needed):

- the Hollow Arbiter: a hooded, faceless colossus of cracked white stone with gold seams, kneeling in chains over
  a planted greatsword, a broken halo behind its head (GeckoLib entity model, drawn at three times this size)
- its animations: dormant, rise, idle, walk, cleave, sunder, judgment, verdict, death
- the sealed gate's texture (the gate model in pale stone and gold)
- the four legendary weapons' item textures

Run from the repo root:  python3 tools/gen_awakening_assets.py
Needs: pillow, numpy.

Conventions (same as the gate boss): 16 pixels are one block and the front of the model is -z. In the files a
positive x rotation pitches a bone forward: the top of an upright part goes to the front, the end of a hanging limb
to the back. The poses below are written the way a body moves instead (positive = an arm or leg swings forward, a
knee or the cloth trails back when negative) and timeline() turns the limbs' signs around.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import gen_combat_assets as gc  # noqa: E402
import gen_gate_assets as gg  # noqa: E402
from gen_gate_assets import (ANIM, GEO, T, TEX, atlas, cube, ease_in, ease_in_out, ease_out_back, geometry, keyframes,  # noqa: E402
                             tile_crystal, tile_gold, tile_hide, tile_metal_dark, tile_rock, window, write_json)

STONE = (216, 211, 200)
GOLD = (242, 213, 138)

# tiles
WHITE, SHADE, ROBE, TRIM, VOID, SLIT, CHAIN, BLADE, HALO, CAPE = range(10)


# --------------------------------------------------------------------------------------------- textures

def tile_void():
    rgb = np.zeros((T, T, 4))
    rgb[:, :] = (6, 5, 9, 255)
    return rgb, np.zeros((T, T, 4))


def tile_blade(rng):
    """Pale steel with a glowing gold line of runes down the middle."""
    rgb = np.zeros((T, T, 4))
    glow = np.zeros((T, T, 4))
    for y in range(T):
        k = 1.0 - 0.35 * abs(y - T / 2 + 0.5) / (T / 2)
        rgb[y, :] = (205 * k, 210 * k, 222 * k, 255)
    rgb[0, :, :3] = (245, 248, 255)
    rgb[T - 1, :, :3] = (120, 124, 140)
    for x in range(T):
        if x % 4 == 3:
            continue
        for y in (7, 8):
            rgb[y, x] = (*GOLD, 255)
            glow[y, x] = (*GOLD, 255)
        if x % 8 == 1:
            for y in (5, 6, 9, 10):
                rgb[y, x] = (*GOLD, 255)
                glow[y, x] = (*GOLD, 255)
    return rgb, glow


def tile_cape(rng):
    rgb, glow = tile_hide(rng, (44, 40, 52))
    rgb[T - 2:T, :, :3] = GOLD
    rgb[T - 3, ::2, :3] = [v * 0.7 for v in GOLD]
    glow[T - 1, :] = (*GOLD, 255)
    return rgb, glow


def clipped(tiles):
    """Pale stone runs past white in places; without this those pixels wrap around to blue."""
    return [(np.clip(rgb, 0, 255), np.clip(glow, 0, 255)) for rgb, glow in tiles]


def arbiter_textures():
    rng = np.random.default_rng(900)
    tiles = [
        tile_rock(rng, STONE, GOLD),
        tile_rock(rng, [v * 0.78 for v in STONE], GOLD),
        tile_hide(rng, (62, 58, 68)),
        tile_gold(rng),
        tile_void(),
        tile_crystal(rng, (255, 228, 160)),
        tile_metal_dark(rng),
        tile_blade(rng),
        tile_rock(rng, (196, 176, 128), (255, 236, 180)),
        tile_cape(rng),
    ]
    img, glow, uvs, size = atlas(clipped(tiles), cols=4)
    img.save(os.path.join(TEX, "entity", "arbiter.png"))
    glow.save(os.path.join(TEX, "entity", "arbiter_glowmask.png"))
    return uvs, size


def sealed_gate_textures():
    rng = np.random.default_rng(910)
    tiles = [tile_rock(rng, STONE, GOLD), tile_rock(rng, [v * 0.8 for v in STONE], GOLD, veins=False),
             tile_crystal(rng, GOLD), tile_rock(rng, [v * 0.62 for v in STONE], GOLD, veins=False)]
    img, glow, _, _ = atlas(clipped(tiles), cols=2)
    img.save(os.path.join(TEX, "block", "gate_sealed.png"))
    glow.save(os.path.join(TEX, "block", "gate_sealed_glowmask.png"))


# --------------------------------------------------------------------------------------------- the model

HIP_Y = 24.0       # standing hip height
KNEEL_DROP = 12.0  # how far the hips come down when it kneels (one thigh length)
SHOULDER = (10.5, 40.0)
ELBOW_Y, FIST_Y = 29.0, 15.5
BLADE_LEN = 36.0
HALO_SEGMENTS = [0, 1, 2, 4, 5, 7, 8, 9, 10]  # of 12 slots: a ring with pieces missing
CHAINS = [  # floor anchor -> where it holds the kneeling body (pixels)
    ((-24, 0, 12), (-10, 27, 1)),
    ((24, 0, 12), (10, 27, 1)),
    ((-22, 0, -16), (-12, 25, -8)),
    ((20, 0, -18), (9, 30, -3)),
]


def chain_cubes(c, anchor, to):
    dx, dy, dz = (to[i] - anchor[i] for i in range(3))
    length = math.sqrt(dx * dx + dy * dy + dz * dz)
    dx, dy, dz = dx / length, dy / length, dz / length
    rot = [-math.degrees(math.asin(dz)), 0, math.degrees(math.atan2(dx, dy))]
    out = []
    y, i = 0.0, 0
    while y < length:
        wide = i % 2 == 0
        w, d = (1.8, 0.7) if wide else (0.7, 1.8)
        out.append(c([anchor[0] - w / 2, anchor[1] + y, anchor[2] - d / 2], [w, min(2.6, length - y), d], CHAIN, pivot=list(anchor), rotation=rot))
        y += 2.0
        i += 1
    # the ring it is bolted to
    out.append(c([anchor[0] - 2, anchor[1], anchor[2] - 2], [4, 1.2, 4], CHAIN))
    return out


def arbiter_model(uvs, size):
    def c(origin, sz, tile, **kw):
        made = cube(origin, sz, tile, uvs, **kw)
        # a hair inside the tile, so the neighbouring tiles never bleed in along the edges
        for face in made["uv"].values():
            face["uv"] = [face["uv"][0] + 0.25, face["uv"][1] + 0.25]
            face["uv_size"] = [T - 0.5, T - 0.5]
        return made

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "hips", "parent": "root", "pivot": [0, HIP_Y, 0], "cubes": [
            c([-6, 21.5, -3.5], [12, 5.5, 7], SHADE),
            c([-6.5, 25, -4], [13, 2, 8], TRIM),
        ]},
        {"name": "tabard", "parent": "hips", "pivot": [0, 24, -3.8], "cubes": [
            c([-3.5, 7, -4.4], [7, 17, 0.8], CAPE),
            c([-1, 18, -4.7], [2, 4, 0.5], TRIM),
        ]},
        {"name": "torso", "parent": "hips", "pivot": [0, 26, 0], "cubes": [
            c([-5, 26, -3], [10, 6, 6], SHADE),
            c([-8, 31, -4.5], [16, 11, 9], WHITE),
            c([-8.4, 35, -4.9], [16.8, 1.6, 9.8], TRIM),
            c([-1.2, 31, -5], [2.4, 11, 0.8], TRIM),
            # the cowl over the shoulders
            c([-9.5, 40.5, -5.5], [19, 3, 11], ROBE),
            c([-7, 42.5, -4.5], [14, 2, 10], ROBE),
            # a cracked wound of light in the chest
            c([2.5, 36.5, -4.8], [1, 4, 0.5], SLIT, pivot=[3, 38.5, -4.6], rotation=[0, 0, -24]),
            c([3.6, 33.5, -4.8], [0.8, 3, 0.5], SLIT, pivot=[4, 35, -4.6], rotation=[0, 0, 18]),
        ]},
        {"name": "cape", "parent": "torso", "pivot": [0, 42, 4.8], "cubes": [
            c([-7.5, 10, 4.6], [15, 32, 1], CAPE),
            c([-5.5, 6, 4.7], [4, 5, 0.8], CAPE),
            c([2, 4, 4.7], [3.5, 7, 0.8], CAPE),
        ]},
        {"name": "head", "parent": "torso", "pivot": [0, 43.5, 0], "cubes": [
            c([-4.5, 43.5, -4.5], [9, 10, 9], ROBE),
            c([-5.5, 44.5, -3.4], [11, 10, 9.4], ROBE),
            c([-3.5, 53.5, -3], [7, 2, 7.5], ROBE),
            c([-4.9, 52, -5.2], [9.8, 2, 2], ROBE),
            c([-3.2, 44.5, -4.8], [6.4, 7.5, 0.6], VOID),
            # no face: one seam of light
            c([-0.45, 45.2, -5.05], [0.9, 6.2, 0.5], SLIT),
            c([-2.2, 49.2, -5.0], [4.4, 0.7, 0.4], SLIT),
        ]},
        {"name": "halo", "parent": "head", "pivot": [0, 50, 6.5]},
    ]
    for i in HALO_SEGMENTS:
        a = i / 12 * 360
        r = 9.5
        px, py = r * math.sin(math.radians(a)), 50 + r * math.cos(math.radians(a))
        bones.append({"name": f"halo{i}", "parent": "halo", "pivot": [px, py, 6.5], "rotation": [0, 0, a], "cubes": [
            c([px - 2.2, py - 1, 5.9], [4.4, 2, 1.2], HALO),
            c([px - 0.6, py + 0.6, 6.0], [1.2, 2.2 + (i % 3), 1.0], HALO),
        ]})

    for side, sx in (("right", -1), ("left", 1)):
        x = sx * SHOULDER[0]
        bones.append({"name": f"{side}_arm", "parent": "torso", "pivot": [x, SHOULDER[1], 0], "cubes": [
            c([x - 2.5 + sx * 0.5, ELBOW_Y - 1, -2.5], [5, 12, 5], SHADE),
            c([x - 3.6 + sx * 1.2, 37, -3.6], [7.2, 6, 7.2], WHITE),
            c([x - 3.8 + sx * 1.2, 36.6, -3.8], [7.6, 1.4, 7.6], TRIM),
            c([x - 1 + sx * 3, 42, -1], [2, 4, 2], WHITE, pivot=[x + sx * 3, 43, 0], rotation=[0, 0, -sx * 24]),
        ]})
        bones.append({"name": f"{side}_forearm", "parent": f"{side}_arm", "pivot": [x + sx * 0.5, ELBOW_Y, 0], "cubes": [
            c([x - 2.7 + sx * 0.5, 18, -2.7], [5.4, 11.5, 5.4], WHITE),
            c([x - 3 + sx * 0.5, 13, -3], [6, 5, 6], SHADE),
            # the shackle it never took off, and what is left of its chain
            c([x - 3.2 + sx * 0.5, 18.5, -3.2], [6.4, 2.2, 6.4], CHAIN),
            c([x - 0.6 + sx * 3.6, 12, -0.5], [1.2, 7, 1], CHAIN),
            c([x - 0.4 + sx * 3.6, 8.5, -0.8], [0.8, 4, 1.6], CHAIN),
        ]})
        hx = sx * 3.5
        bones.append({"name": f"{side}_leg", "parent": "hips", "pivot": [hx, HIP_Y, 0], "cubes": [
            c([hx - 3, 11, -3], [6, 13, 6], SHADE),
            c([hx - 3.3, 11, -3.4], [6.6, 3.2, 6.8], WHITE),
        ]})
        bones.append({"name": f"{side}_shin", "parent": f"{side}_leg", "pivot": [hx, 12, 0], "cubes": [
            c([hx - 2.8, 2, -2.8], [5.6, 10, 5.6], WHITE),
            c([hx - 3.1, 6.5, -3.2], [6.2, 1.4, 6.4], TRIM),
            c([hx - 3.2, 0, -5], [6.4, 2.6, 8.2], SHADE),
        ]})

    # the greatsword: held in the right fist; at rest it points straight ahead
    fx = -SHOULDER[0] - 0.5
    bones.append({"name": "sword", "parent": "right_forearm", "pivot": [fx, FIST_Y, 0], "cubes": [
        c([fx - 0.9, FIST_Y - 0.9, -3.5], [1.8, 1.8, 11.5], CHAIN),
        c([fx - 1.5, FIST_Y - 1.5, 7.5], [3, 3, 3], TRIM),
        c([fx - 1.2, FIST_Y - 5.5, -5.5], [2.4, 11, 2], TRIM),
        c([fx - 0.8, FIST_Y - 7, -5.2], [1.6, 2, 1.4], WHITE),
        c([fx - 0.8, FIST_Y + 5, -5.2], [1.6, 2, 1.4], WHITE),
        c([fx - 0.6, FIST_Y - 3, -5.5 - (BLADE_LEN - 5)], [1.2, 6, BLADE_LEN - 5], BLADE),
        c([fx - 0.5, FIST_Y - 2.1, -5.5 - (BLADE_LEN - 2)], [1.0, 4.2, 3.5], BLADE),
        c([fx - 0.4, FIST_Y - 1, -5.5 - BLADE_LEN], [0.8, 2, 2.5], BLADE),
    ]})

    for i, (anchor, to) in enumerate(CHAINS):
        bones.append({"name": f"chain{i}", "parent": "root", "pivot": list(anchor), "cubes": chain_cubes(c, anchor, to)})
    return geometry("geometry.beyondsolo.arbiter", size, bones, 8, 6, 2)


# --------------------------------------------------------------------------------------------- poses
# A pose maps bone -> rotation [x, y, z]; "hips@" is the hips' position. The sword is given as the angle the blade
# should make with the horizon (0 straight ahead, 90 up, -90 down) and turned into the bone's own rotation.

def pose(sword=None, **bones):
    p = {k: list(v) for k, v in bones.items()}
    if sword is not None:
        chain = p.get("torso", [0, 0, 0])[0] - p.get("right_arm", [0, 0, 0])[0] - p.get("right_forearm", [0, 0, 0])[0]
        p["sword"] = [-sword - chain, 0, 0]
    return p


KNEEL = pose(sword=-90, **{
    "hips@": [0, -KNEEL_DROP, 0], "torso": [16, 0, 0], "head": [30, 0, 0],
    "right_leg": [90, 0, 0], "right_shin": [-90, 0, 0], "left_leg": [0, 0, 0], "left_shin": [-90, 0, 0],
    "right_arm": [52, 0, 0], "right_forearm": [38, 0, 0], "left_arm": [38, 0, -6], "left_forearm": [46, 0, 0],
    "cape": [-30, 0, 0], "tabard": [58, 0, 0], "halo": [0, 0, 0]})

STAND = pose(sword=76, **{
    "hips@": [0, 0, 0], "torso": [0, 0, 0], "head": [0, 0, 0],
    "right_leg": [0, 0, 0], "right_shin": [0, 0, 0], "left_leg": [0, 0, 0], "left_shin": [0, 0, 0],
    "right_arm": [14, 0, 5], "right_forearm": [62, 0, 0], "left_arm": [2, 0, -5], "left_forearm": [12, 0, 0],
    "cape": [-4, 0, 0], "tabard": [0, 0, 0], "halo": [0, 0, 0]})

# standing with the sword still in the floor, a moment before it is wrenched free
STAND_PLANTED = dict(STAND, **pose(sword=-84, torso=[10, 0, 0], right_arm=[58, 0, 4], right_forearm=[18, 0, 0]),
                     head=[10, 0, 0])
WRENCH = dict(STAND, **pose(sword=112, torso=[-10, 0, 0], right_arm=[150, 0, 12], right_forearm=[26, 0, 0]),
              head=[-18, 0, 0], left_arm=[-20, 0, -30], left_forearm=[30, 0, 0], cape=[-16, 0, 0])

LIMBS = {"right_arm", "left_arm", "right_forearm", "left_forearm", "right_leg", "left_leg", "right_shin", "left_shin", "cape", "tabard"}


def mix(a, b, k):
    return [a[i] + (b[i] - a[i]) * k for i in range(3)]


def timeline(steps, length, step=0.05, extra=None, only=None):
    """steps: [(time, pose, ease)]; the pose at each time, eased from the one before. Returns GeckoLib bone tracks."""
    names = sorted({b for _, p, _ in steps for b in p}) if only is None else only

    def at(bone, t):
        value = steps[0][1].get(bone, [0, 0, 0])
        for (t0, p0, _), (t1, p1, ease) in zip(steps, steps[1:]):
            if t >= t0:
                value = mix(p0.get(bone, [0, 0, 0]), p1.get(bone, [0, 0, 0]), ease(window(t, t0, t1)))
        return value

    bones = {}
    for name in names:
        channel = "position" if name.endswith("@") else "rotation"
        add = (extra or {}).get(name)
        raw = (lambda t, n=name, add=add: [v + w for v, w in zip(at(n, t), add(t))]) if add else (lambda t, n=name: at(n, t))
        fn = (lambda t, raw=raw: [-raw(t)[0], raw(t)[1], raw(t)[2]]) if name in LIMBS else raw
        bones.setdefault(name.rstrip("@"), {})[channel] = keyframes(fn, length, step)
    return bones


def chains(bones, visible, length):
    for i in range(len(CHAINS)):
        s = 1 if visible else 0.001
        bones[f"chain{i}"] = {"scale": {"0.00": [s, s, s], f"{length:.2f}": [s, s, s]}}
    return bones


def linear(t):
    return max(0.0, min(1.0, t))


def arbiter_animations():
    anims = {}
    A = "animation.beyondsolo.arbiter."

    # dormant: a statue. Only the cloth stirs
    L = 4.0
    anims[A + "dormant"] = {"loop": True, "animation_length": L, "bones": chains(timeline(
        [(0, KNEEL, linear), (L, KNEEL, linear)], L, 0.5,
        extra={"cape": lambda t: [1.2 * math.sin(2 * math.pi * t / L), 0, 0]}), True, L)}

    # rise (6 s = ArbiterEntity.INTRO_TICKS): the head comes up, the chains tear at 0.9 s and 1.3 s, it stands
    # between 2.0 s and 4.5 s, and at 4.8 s it wrenches the sword out of the floor
    L = 6.0
    look = dict(KNEEL, head=[-6, 0, 0], torso=[10, 0, 0])
    strain = dict(look, torso=[4, 0, 0], left_arm=[10, 0, -30], left_forearm=[60, 0, 0], head=[-14, 0, 0])
    jerk = lambda t: math.sin(t * 60) * (window(t, 0.85, 0.9) * (1 - window(t, 0.95, 1.2)) + window(t, 1.25, 1.3) * (1 - window(t, 1.35, 1.6)))
    rise = timeline([(0, KNEEL, linear), (0.3, KNEEL, linear), (0.85, look, ease_in_out), (1.3, strain, ease_in_out),
                     (2.0, strain, linear), (4.5, STAND_PLANTED, ease_in_out), (4.62, STAND_PLANTED, linear),
                     (4.9, WRENCH, ease_in), (5.35, WRENCH, linear), (6.0, STAND, ease_in_out)], L,
                    extra={"torso": lambda t: [2.5 * jerk(t), 0, 2 * jerk(t)],
                           "head": lambda t: [0, 3 * jerk(t), 0]})
    for i in range(len(CHAINS)):
        snap = 0.9 if i < 2 else 1.3
        rise[f"chain{i}"] = {"scale": {"0.00": [1, 1, 1], f"{snap - 0.05:.2f}": [1, 1, 1], f"{snap:.2f}": [0.001] * 3, f"{L:.2f}": [0.001] * 3}}
    anims[A + "rise"] = {"loop": False, "animation_length": L, "bones": rise}

    # idle: slow breath, the halo tilting
    L = 4.0
    s = lambda t: math.sin(2 * math.pi * t / L)
    anims[A + "idle"] = {"loop": True, "animation_length": L, "bones": chains(timeline(
        [(0, STAND, linear), (L, STAND, linear)], L, 0.25,
        extra={"torso": lambda t: [1.5 * s(t), 0, 0], "head": lambda t: [-1.5 * s(t), 2.5 * math.sin(2 * math.pi * t / L + 1), 0],
               "right_forearm": lambda t: [2 * s(t), 0, 0], "cape": lambda t: [2 * s(t), 0, 0],
               "halo": lambda t: [0, 0, 5 * s(t)], "hips@": lambda t: [0, -0.4 + 0.4 * math.cos(2 * math.pi * t / L), 0]}), False, L)}

    # walk: slow and heavy
    L = 2.0
    w = lambda t: math.sin(2 * math.pi * t / L)
    bend = lambda v: max(0.0, v)
    anims[A + "walk"] = {"loop": True, "animation_length": L, "bones": chains(timeline(
        [(0, STAND, linear), (L, STAND, linear)], L, 0.1,
        extra={"right_leg": lambda t: [24 * w(t), 0, 0], "left_leg": lambda t: [-24 * w(t), 0, 0],
               "right_shin": lambda t: [-30 * bend(math.cos(2 * math.pi * t / L)), 0, 0],
               "left_shin": lambda t: [-30 * bend(-math.cos(2 * math.pi * t / L)), 0, 0],
               "hips@": lambda t: [0, -1.4 * abs(w(t)), 0], "torso": lambda t: [5, 5 * w(t), 0],
               "head": lambda t: [-4, -4 * w(t), 0], "left_arm": lambda t: [-14 * w(t), 0, 0],
               "cape": lambda t: [-10 + 4 * w(t), 0, 0], "tabard": lambda t: [10 * abs(w(t)), 0, 0]}), False, L)}

    # verdict: its challenger has fallen. The sword goes up over them and stays there
    L = 2.6
    over = dict(STAND, **pose(sword=118, torso=[-8, 0, 0], right_arm=[168, 0, 10], right_forearm=[14, 0, 0]),
                head=[24, 0, 0], left_arm=[150, 0, -14], left_forearm=[30, 0, 0], cape=[-14, 0, 0])
    anims[A + "verdict"] = {"loop": "hold_on_last_frame", "animation_length": L, "bones": chains(timeline(
        [(0, STAND, linear), (0.5, dict(STAND, head=[22, 0, 0]), ease_in_out), (2.2, over, ease_in_out), (L, over, linear)], L), False, L)}

    # death (5.5 s = DEATH_TICKS): staggers, goes to its knees at 1.7 s, bows at 3.1 s, and at 4.2 s comes apart
    L = 5.5
    reel = dict(STAND, **pose(sword=-30, torso=[-14, 0, 0], right_arm=[30, 0, 20], right_forearm=[10, 0, 0]),
                head=[-26, 0, 0], left_arm=[-10, 0, -40], cape=[-20, 0, 0])
    knees = dict(STAND, **pose(sword=-80, torso=[14, 0, 0], right_arm=[20, 0, 10], right_forearm=[20, 0, 0]),
                 **{"hips@": [0, -KNEEL_DROP, 0], "right_leg": [0, 0, 0], "left_leg": [0, 0, 0], "right_shin": [-90, 0, 0],
                    "left_shin": [-90, 0, 0], "head": [10, 0, 0], "cape": [-26, 0, 0], "tabard": [40, 0, 0]})
    bowed = dict(knees, **pose(sword=-88, torso=[46, 0, 0], right_arm=[10, 0, 14], right_forearm=[6, 0, 0]),
                 head=[38, 0, 0], left_arm=[30, 0, -14], left_forearm=[10, 0, 0])
    shake = lambda t: math.sin(t * 48) * window(t, 3.9, 4.3)
    death = chains(timeline([(0, STAND, linear), (0.5, reel, ease_out_back), (1.2, reel, linear), (1.75, knees, ease_in),
                             (2.6, knees, linear), (3.2, bowed, ease_in_out), (L, bowed, linear)], L,
                            extra={"torso": lambda t: [2 * shake(t), 0, 2 * shake(t)], "head": lambda t: [0, 4 * shake(t), 0]}), False, L)
    fade = lambda t: max(0.001, 1 - ease_in(window(t, 4.9, 5.5)))
    death["root"] = {"scale": keyframes(lambda t: [fade(t)] * 3, L, 0.05)}
    for i in HALO_SEGMENTS:
        a = math.radians(i / 12 * 360)
        fly = lambda t, a=a, i=i: [24 * math.sin(a) * ease_in(window(t, 4.2, 5.5)), 24 * math.cos(a) * ease_in(window(t, 4.2, 5.5)) - 6 * window(t, 0.2, 4.2) * (i % 2),
                                   10 * ease_in(window(t, 4.2, 5.5))]
        death[f"halo{i}"] = {"position": keyframes(fly, L, 0.1), "rotation": keyframes(lambda t, i=i: [0, 0, 200 * (1 if i % 2 else -1) * ease_in(window(t, 4.2, 5.5))], L, 0.1)}
    anims[A + "death"] = {"loop": "hold_on_last_frame", "animation_length": L, "bones": death}

    # ---- attacks: played on their own controller over idle or walk, so only the upper body moves
    upper = ["torso", "head", "right_arm", "right_forearm", "sword", "left_arm", "left_forearm", "cape"]

    # cleave (lands at 1.2 s): the blade is drawn back level at its side, then swept across everything in front
    L = 2.0
    back = dict(STAND, **pose(sword=4, torso=[6, 62, 0], right_arm=[86, 0, 22], right_forearm=[4, 0, 0]),
                head=[4, -46, 0], left_arm=[30, 0, -40], cape=[-10, 20, 0])
    through = dict(STAND, **pose(sword=-6, torso=[16, -70, 0], right_arm=[82, 0, 6], right_forearm=[4, 0, 0]),
                   head=[0, 40, 0], left_arm=[-30, 0, -24], cape=[-22, -30, 0])
    anims[A + "cleave"] = {"loop": False, "animation_length": L, "bones": timeline(
        [(0, STAND, linear), (0.75, back, ease_in_out), (1.02, back, linear), (1.24, through, ease_in),
         (1.5, through, linear), (L, STAND, ease_in_out)], L, only=upper)}

    # sunder (lands at 1.5 s): the sword is turned point down, lifted, and driven into the floor ahead
    L = 2.4
    lifted = dict(STAND, **pose(sword=-96, torso=[-12, 0, 0], right_arm=[150, 0, 8], right_forearm=[34, 0, 0]),
                  head=[-12, 0, 0], left_arm=[150, 0, -8], left_forearm=[34, 0, 0], cape=[-16, 0, 0])
    driven = dict(STAND, **pose(sword=-62, torso=[34, 0, 0], right_arm=[54, 0, 4], right_forearm=[8, 0, 0]),
                  head=[-6, 0, 0], left_arm=[60, 0, -4], left_forearm=[30, 0, 0], cape=[-34, 0, 0])
    anims[A + "sunder"] = {"loop": False, "animation_length": L, "bones": timeline(
        [(0, STAND, linear), (0.9, lifted, ease_in_out), (1.34, lifted, linear), (1.5, driven, ease_in),
         (1.95, driven, linear), (L, STAND, ease_in_out)], L, only=upper)}

    # judgment (called at 1.1 s): the open hand goes up, and comes down pointing
    L = 2.0
    raised = dict(STAND, torso=[-6, 0, 0], head=[-22, 0, 0], left_arm=[172, 0, -12], left_forearm=[8, 0, 0])
    pointing = dict(STAND, torso=[8, 0, 0], head=[6, 0, 0], left_arm=[84, 0, -4], left_forearm=[4, 0, 0])
    anims[A + "judgment"] = {"loop": False, "animation_length": L, "bones": timeline(
        [(0, STAND, linear), (0.6, raised, ease_in_out), (0.98, raised, linear), (1.12, pointing, ease_in),
         (1.6, pointing, linear), (L, STAND, ease_in_out)], L, only=["torso", "head", "left_arm", "left_forearm"])}
    return {"format_version": "1.8.0", "animations": anims}


# --------------------------------------------------------------------------------------------- legendary weapons

def legendary_weapons():
    hexc, Part, const = gc.hexc, gc.Part, gc.const
    bone = (hexc("fffdf4"), hexc("d8d2c2"), hexc("8f8878"))
    night = (hexc("7a6cc8"), hexc("2c2452"), hexc("0c0a1c"))
    ash = (hexc("8a8690"), hexc("45424c"), hexc("1c1a22"))
    out = {}
    # Sunderveil: the Arbiter's greatsword at human size. White stone, a seam of gold light
    out["sunderveil"] = gc.render_weapon([
        gc.pommel(2.4, 2.1, gc.GOLD), gc.grip(2.8, 10.5, ash), gc.guard(11.6, 7.2, gc.GOLD, 1.5),
        Part(12.6, 14.6, const(4.6), bone),
        gc.blade(13, 41, 3.7, 7, bone, fuller=False),
        gc.blade(14, 38, 0.9, 5, (hexc("fff6cf"), hexc("f2d58a"), hexc("f2d58a")), fuller=False)],
        glow=(242, 213, 138, 110))
    # Ashen Oath: a burnt-black blade, still burning down the middle
    out["ashen_oath"] = gc.render_weapon([
        gc.pommel(3.4, 1.9, gc.EMBER), gc.grip(4, 10.5, ash), gc.guard(11.5, 5.6, ash, 1.4),
        Part(10.8, 12.2, const(2.2), gc.EMBER),
        gc.blade(12.5, 39.5, 2.7, 8, ash, fuller=False),
        gc.blade(13.5, 37, 1.2, 6, (hexc("fff3c4"), hexc("ff9a3c"), hexc("e0481a")), fuller=False,
                 bend=lambda u: 0.5 * math.sin(u * 0.9))],
        glow=(255, 110, 40, 110))
    # Nightglass Fang: a sliver of dark glass with a venom-green edge
    out["nightglass_fang"] = gc.render_weapon([
        gc.pommel(4.6, 1.7, gc.VENOM), gc.grip(5, 12, night), gc.guard(12.8, 3.4, night, 1.0),
        gc.blade(13.5, 32, 2.7, 12, night, bend=lambda u: -0.014 * (u - 13.5) ** 2, fuller=False),
        gc.blade(14, 31, 0.6, 9, (hexc("e4ffb8"), hexc("8cf04a"), hexc("8cf04a")), fuller=False,
                 bend=lambda u: -0.014 * (u - 13.5) ** 2 + 1.6)],
        glow=(140, 240, 74, 110))
    # Thorn Requiem: a scythe whose blade has grown thorns
    img = Image.new("RGBA", (gc.S, gc.S), (0, 0, 0, 0))
    px = img.load()
    shaft = Part(0, 33, const(1.0), ash)
    wrap = Part(8, 15, const(1.4), gc.BLOOD, kind="grip")
    collar = Part(26.5, 29.5, const(2.1), gc.GOLD)
    for y in range(gc.S):
        for x in range(gc.S):
            u, v = gc.uv(x, y)
            c = None
            for p in (shaft, wrap, collar):
                c = p.color_at(u, v) or c
            bu, bv = u - 29.5, v - 0.5
            if 0 <= bv <= 20:
                k = bv / 20.0
                centre = 2.8 - 12 * k * k
                thorn = 1.5 if int(bv) % 5 == 2 else 0.0
                half = 3.5 * (1 - k) ** 0.6 + 0.3
                d = bu - centre
                if -half <= d <= half + thorn:
                    t = d / half
                    c = hexc("fff0f0") if t > 1 else gc.BLOOD[0] if t > 0.4 else hexc("2a0a10") if t < -0.3 else gc.BLOOD[1]
            if c is not None:
                px[x, y] = c
    img = gc.outline(img)
    a = np.array(img)
    solid = a[:, :, 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    a[grown & ~solid] = (220, 30, 50, 110)
    out["thorn_requiem"] = Image.fromarray(a)
    return out


def main():
    for d in ("block", "entity"):
        os.makedirs(os.path.join(GEO, d), exist_ok=True)
        os.makedirs(os.path.join(ANIM, d), exist_ok=True)
        os.makedirs(os.path.join(TEX, d), exist_ok=True)
    uvs, size = arbiter_textures()
    write_json(os.path.join(GEO, "entity", "arbiter.geo.json"), arbiter_model(uvs, size))
    write_json(os.path.join(ANIM, "entity", "arbiter.animation.json"), arbiter_animations())
    sealed_gate_textures()
    for name, img in legendary_weapons().items():
        img.save(os.path.join(gc.ITEM_OUT, name + ".png"))
    print("awakening assets written")


if __name__ == "__main__":
    main()
