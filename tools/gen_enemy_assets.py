#!/usr/bin/env python3
"""
Generates the enemy roster's models, animations and textures (no hand-drawn files needed):

Gate bosses (each with idle, walk, intro, death and its own attacks; one texture per gate rank):
- broodmother: a bloated cave spider queen with a glowing egg sac (bite, venom spray, brood, leap)
- knight: a towering hollow knight in a tattered cape with a broken greatsword (cleave, charge, rising blades)
- magus: a floating robed caster in a bone mask with a crown of orbiting shards (orbs, sigils, blink, summon)
- wyrm: a wingfolded drake of ash and ember scales (breath, tail sweep, wing buffet)
- alpha: a huge crystal-maned hound (pounce, bite, howl)
(The rock brute is the original gate boss from gen_gate_assets.py.)

Gate monsters (idle, walk, attack, death; one texture per element affinity, plain included):
- gloomfang (cave hound), broodling (small spider), shardback (crystal-backed beetle), hollow_acolyte (robed
  caster), ashen_sentinel (shield knight), emberwisp (floating burning skull)

Run from the repo root:  python3 tools/gen_enemy_assets.py            (or only some: ... gen_enemy_assets.py alpha wyrm)
Needs: pillow, numpy.

Conventions (as the other generators): 16 pixels are one block and the front of a model is -z. A positive x
rotation tilts a bone's top towards the front (so the end of a hanging limb swings back, and a head or neck that
points forward dips down); a positive z rotation tilts a bone's top towards +x (the model's left).
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from gen_gate_assets import (ANIM, GEO, PALETTES, RANKS, T, TEX, atlas, cube, ease_in, ease_in_out, ease_out_back,  # noqa: E402
                             geometry, keyframes, noise, tile_crystal, tile_eye, window, write_json)

import paint_skins  # noqa: E402
import solid_skins  # noqa: E402

# tiles (every model's atlas has the same eight)
HIDE, DARK, PLATE, TRIM, GLOW, EYE, BONE, CLOTH = range(8)

# every face painted on its own (paint_skins); False goes back to the old shared 16 px tiles
PAINTED = True

# models drawn bigger than they are built
MODEL_SCALE = {"boss_wyrm": 1.3}

# models built at a scale of their design (the hounds): model units per design unit, for the painters in solid_skins
UNITS = {}

AFFINITIES = {
    "plain": (205, 210, 225),
    "flame": (255, 128, 48),
    "frost": (120, 205, 255),
    "venom": (140, 232, 80),
    "storm": (178, 168, 255),
    "shade": (168, 108, 240),
    "blood": (228, 44, 56),
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def mixc(a, b, k):
    return tuple(a[i] + (b[i] - a[i]) * k for i in range(3))


# --------------------------------------------------------------------------------------------- tiles

def t_skin(rng, base, scales=True, spots=None):
    n = noise(rng, T, 4)
    rgb = np.zeros((T, T, 4))
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.72 + 0.5 * n)
    if scales:
        for y in range(0, T, 3):
            for x in range((y // 3) % 2, T, 3):
                rgb[y, x, :3] *= 0.72
    glow = np.zeros((T, T, 4))
    if spots is not None:
        for _ in range(3):
            x, y = rng.integers(1, T - 1), rng.integers(1, T - 1)
            rgb[y, x, :3] = spots
            glow[y, x] = (*spots, 255)
    rgb[:, :, 3] = 255
    return np.clip(rgb, 0, 255), glow


def t_plate(rng, base, trim):
    n = noise(rng, T, 2)
    rgb = np.zeros((T, T, 4))
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.82 + 0.32 * n)
    rgb[0:2, :, :3] = [min(255, v * 1.35) for v in base]
    rgb[T - 2:T, :, :3] = trim
    rgb[T - 1, :, :3] = [v * 0.6 for v in trim]
    for x in (2, T - 3):
        rgb[5, x, :3] = trim
    # scratches
    for _ in range(3):
        x, y = rng.integers(0, T - 4), rng.integers(3, T - 3)
        for k in range(4):
            rgb[y + (k // 2), x + k, :3] *= 1.25
    rgb[:, :, 3] = 255
    return np.clip(rgb, 0, 255), np.zeros((T, T, 4))


def t_trim(rng, trim):
    rgb = np.zeros((T, T, 4))
    for y in range(T):
        k = 0.7 + 0.5 * math.sin(y / T * math.pi)
        rgb[y, :, :3] = [min(255, v * k) for v in trim]
    rgb[::4, ::4, :3] *= 0.7
    rgb[:, :, 3] = 255
    return rgb, np.zeros((T, T, 4))


def t_bone(rng, base=(225, 214, 190)):
    rgb = np.zeros((T, T, 4))
    n = noise(rng, T, 3)
    for y in range(T):
        k = 1 - 0.35 * y / T
        for c in range(3):
            rgb[y, :, c] = base[c] * k * (0.9 + 0.2 * n[y])
    rgb[:, ::5, :3] *= 0.86
    rgb[:, :, 3] = 255
    return np.clip(rgb, 0, 255), np.zeros((T, T, 4))


def t_cloth(rng, base, glow=None):
    rgb = np.zeros((T, T, 4))
    n = noise(rng, T, 3)
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.75 + 0.4 * n)
    for x in range(0, T, 4):
        rgb[:, x, :3] *= 0.8
    gl = np.zeros((T, T, 4))
    if glow is not None:
        # a glowing rune band near the hem
        for x in range(T):
            if (x % 5) in (1, 2):
                rgb[T - 4, x, :3] = glow
                gl[T - 4, x] = (*glow, 255)
    rgb[:, :, 3] = 255
    return np.clip(rgb, 0, 255), gl


def t_dark(rng, base):
    return t_skin(rng, [v * 0.55 for v in base], scales=False)


def tiles_for(rng, hide, plate, trim, glow, bone=(225, 214, 190), cloth=None, spots=False):
    if PAINTED:
        return {"hide": hide, "plate": plate, "trim": trim, "glow": glow, "bone": bone,
                "cloth": cloth or [v * 0.8 for v in hide], "spots": spots}
    return [
        t_skin(rng, hide, spots=glow if spots else None),
        t_dark(rng, hide),
        t_plate(rng, plate, trim),
        t_trim(rng, trim),
        tile_crystal(rng, glow),
        tile_eye(rng, glow),
        t_bone(rng, bone),
        t_cloth(rng, cloth or [v * 0.8 for v in hide], glow),
    ]


def save_textures(name, variants, make_tiles, seed):
    """variants: [(suffix, args...)]; writes textures/entity/<name>_<suffix>.png and its glow mask."""
    if PAINTED:
        # painted per face once the model is built (see paint_skins and write_geo)
        d = paint_skins.Deferred(name, variants, make_tiles, seed)
        return d, d
    uvs, size = None, None
    for i, (suffix, *args) in enumerate(variants):
        rng = np.random.default_rng(seed + i)
        img, glow, uvs, size = atlas(make_tiles(rng, *args), cols=4)
        img.save(os.path.join(TEX, "entity", f"{name}_{suffix}.png"))
        glow.save(os.path.join(TEX, "entity", f"{name}_{suffix}_glowmask.png"))
    return uvs, size


# --------------------------------------------------------------------------------------------- model helpers

class Model:
    def __init__(self, uvs):
        self.uvs = uvs
        self.bones = []

    def c(self, origin, size, tile, tag=None, skip=None, part=None, **kw):
        """A cube. tag, part: what it is, for the painters in solid_skins; skip: faces that are never seen (not
        drawn and given no room on the texture)."""
        made = cube(origin, size, tile, self.uvs, **kw)
        made["_mat"] = tile
        if tag is not None:
            made["_tag"] = tag
        if skip:
            made["_skip"] = tuple(skip)
        if part is not None:
            made["_part"] = part
        # a hair inside the tile, so neighbouring tiles never bleed in along the edges
        for face in made["uv"].values():
            face["uv"] = [face["uv"][0] + 0.25, face["uv"][1] + 0.25]
            face["uv_size"] = [T - 0.5, T - 0.5]
        return made

    def bone(self, name, parent, pivot, cubes=(), rotation=None):
        b = {"name": name, "pivot": [round(v, 3) for v in pivot], "cubes": list(cubes)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [round(v, 3) for v in rotation]
        self.bones.append(b)
        return b

    def geo(self, ident, size, w, h, oy):
        return geometry(f"geometry.beyondsolo.{ident}", size, self.bones, w, h, oy)


def track(fn, length, step=0.05):
    return keyframes(fn, length, step)


def anim(length, loop, bones, step=0.05):
    """bones: {name: {"rotation": fn, "position": fn}} -> a GeckoLib animation."""
    out = {}
    for name, chans in bones.items():
        out[name] = {ch: track(fn, length, step) for ch, fn in chans.items()}
    a = {"animation_length": length, "bones": out}
    a["loop"] = True if loop is True else ("hold_on_last_frame" if loop == "hold" else False)
    return a


def wave(t, period, phase=0.0):
    return math.sin(2 * math.pi * (t / period + phase))


def rot(fn):
    return {"rotation": fn}


def write_anims(name, anims):
    k = MODEL_SCALE.get(name, 1.0)
    if k != 1.0:
        # positions are in model units: they grow with the model
        for a in anims.values():
            for bone in a.get("bones", {}).values():
                pos = bone.get("position")
                if isinstance(pos, dict):
                    for t, v in pos.items():
                        if isinstance(v, list):
                            pos[t] = [round(x * k, 4) for x in v]
                        elif isinstance(v, dict):
                            for kk in ("pre", "post", "vector"):
                                if isinstance(v.get(kk), list):
                                    v[kk] = [round(x * k, 4) for x in v[kk]]
    write_json(os.path.join(ANIM, "entity", f"{name}.animation.json"),
               {"format_version": "1.8.0", "animations": {f"animation.beyondsolo.{name}.{k}": v for k, v in anims.items()}})


def write_geo(name, model):
    geo = model["minecraft:geometry"][0]
    k = MODEL_SCALE.get(name, 1.0)
    if k != 1.0:
        for b in geo["bones"]:
            b["pivot"] = [round(v * k, 3) for v in b["pivot"]]
            for c in b.get("cubes", []):
                for key in ("origin", "size", "pivot"):
                    if key in c:
                        c[key] = [round(v * k, 3) for v in c[key]]
        d = geo["description"]
        d["visible_bounds_width"] *= k
        d["visible_bounds_height"] *= k
    d = geo["description"]
    if isinstance(d["texture_width"], paint_skins.Deferred) and name in solid_skins.PAINTERS:
        w, h, density = solid_skins.paint(name, geo["bones"], d["texture_width"], os.path.join(TEX, "entity"), UNITS.get(name, 1.0) * k)
        d["texture_width"], d["texture_height"] = w, h
        print(f"  {name}: {w}x{h} atlas, {density} texels per unit (painted on the model)")
    elif isinstance(d["texture_width"], paint_skins.Deferred):
        size, density = paint_skins.paint(name, geo["bones"], d["texture_width"], os.path.join(TEX, "entity"))
        d["texture_width"] = d["texture_height"] = size
        print(f"  {name}: {size}px atlas, {density} texels per unit")
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            for key in ("_mat", "_tag", "_skip", "_part"):
                c.pop(key, None)
    write_json(os.path.join(GEO, "entity", f"{name}.geo.json"), model)


# ============================================================================================= BOSSES

def rank_glow(rank):
    return PALETTES[rank]["glow"]


def rank_tint(base, rank, k=0.22):
    return mixc(base, PALETTES[rank]["hide"], k)


# --------------------------------------------------------------------------------------------- broodmother

def broodmother():
    name = "boss_broodmother"
    uvs, size = save_textures(name, [(r, r) for r in RANKS], lambda rng, r: tiles_for(
        rng, rank_tint((44, 34, 48), r), rank_tint((72, 58, 78), r), (130, 110, 96), rank_glow(r),
        bone=(200, 190, 160), cloth=mixc((96, 40, 70), rank_glow(r), 0.25), spots=True), 300)
    m = Model(uvs)
    c = m.c
    m.bone("root", None, [0, 0, 0])
    m.bone("thorax", "root", [0, 14, 0], [
        c([-10, 9, -12], [20, 11, 18], HIDE),
        c([-9, 18, -11], [18, 3, 15], PLATE),
        c([-6, 20, -8], [12, 2, 9], TRIM),
        # spines along the back
        c([-1, 20, -4], [2, 6, 2], BONE, pivot=[0, 21, -3], rotation=[-20, 0, 0]),
        c([-5, 19, -2], [2, 5, 2], BONE, pivot=[-4, 20, -1], rotation=[-25, 0, 20]),
        c([3, 19, -2], [2, 5, 2], BONE, pivot=[4, 20, -1], rotation=[-25, 0, -20]),
    ])
    m.bone("head", "thorax", [0, 14, -12], [
        c([-6, 9, -20], [12, 9, 9], DARK),
        c([-6.5, 16, -20.5], [13, 2.5, 10], PLATE),
        # six eyes
        c([-4.5, 14, -20.6], [2, 2, 0.6], EYE), c([2.5, 14, -20.6], [2, 2, 0.6], EYE),
        c([-2.5, 15.5, -20.6], [1.6, 1.6, 0.6], EYE), c([0.9, 15.5, -20.6], [1.6, 1.6, 0.6], EYE),
        c([-5.5, 11.5, -20.6], [1.3, 1.3, 0.6], EYE), c([4.2, 11.5, -20.6], [1.3, 1.3, 0.6], EYE),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        m.bone(f"{side}_fang", "head", [sx * 3, 10, -19.5], [
            c([sx * 3 - 1.2, 3, -21], [2.4, 7, 2.4], BONE),
            c([sx * 3 - 0.8, 1, -21.4 + 0.6], [1.6, 2.5, 1.6], BONE, pivot=[sx * 3, 3, -20], rotation=[25, 0, 0]),
        ], rotation=[0, 0, -sx * 10])
    m.bone("abdomen", "thorax", [0, 16, 6], [
        c([-13, 10, 6], [26, 20, 24], CLOTH),
        c([-11, 26, 8], [22, 5, 20], PLATE),
        c([-12, 11, 29], [24, 16, 2], DARK),
        # egg pustules
        c([-9, 22, 26], [4, 4, 4], GLOW), c([4, 18, 28], [4, 4, 3], GLOW), c([-3, 14, 28.5], [3, 3, 3], GLOW),
        c([10, 22, 14], [3, 4, 4], GLOW), c([-13.5, 18, 16], [3, 4, 4], GLOW), c([2, 29.5, 16], [5, 2, 5], GLOW),
    ])
    m.bone("sac", "abdomen", [0, 20, 18])
    # eight legs: (z of hip, splay yaw)
    legs = [(-9, -40), (-4, -14), (1, 12), (6, 38)]
    for i, (z, yaw) in enumerate(legs):
        for side, sx in (("r", -1), ("l", 1)):
            hip = [sx * 9, 15, z]
            m.bone(f"leg{i}{side}", "thorax", hip, [
                c([sx * 9 + (0 if sx > 0 else -18), 13.5, z - 1.5], [18, 3, 3], HIDE),
                c([sx * 9 + (0 if sx > 0 else -18), 15.8, z - 1.6], [18, 1.2, 3.2], PLATE),
            ], rotation=[0, -sx * yaw, -sx * 35])
            knee = [sx * 27, 15, z]
            m.bone(f"leg{i}{side}_low", f"leg{i}{side}", knee, [
                c([sx * 27 - 1.2, -11, z - 1.2], [2.4, 26, 2.4], DARK),
                c([sx * 27 - 0.7, -14, z - 0.7], [1.4, 3.5, 1.4], BONE),
            ], rotation=[0, 0, sx * 25])
    write_geo(name, m.geo(name, size, 6, 4, 1.2))

    def legs_track(fn):
        out = {}
        for i in range(4):
            for side, sx in (("r", -1), ("l", 1)):
                out[f"leg{i}{side}"] = rot(lambda t, i=i, sx=sx: fn(t, i, sx)[0])
                out[f"leg{i}{side}_low"] = rot(lambda t, i=i, sx=sx: fn(t, i, sx)[1])
        return out

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "abdomen": {"rotation": lambda t: [2 * wave(t, L), 0, 0],
                    "position": lambda t: [0, 0.4 * wave(t, L), 0]},
        "head": rot(lambda t: [3 * wave(t, L, 0.3), 4 * wave(t, L * 2), 0]),
        "right_fang": rot(lambda t: [0, 0, 6 * max(0, wave(t, 1.0)) * (t > 1.8)]),
        "left_fang": rot(lambda t: [0, 0, -6 * max(0, wave(t, 1.0)) * (t > 1.8)]),
        **legs_track(lambda t, i, sx: ([0, 1.5 * wave(t, L, i * 0.2), 0], [0, 0, 0])),
    }, 0.1)
    L = 1.0

    def gait(t, i, sx):
        ph = (i + (0 if sx < 0 else 1)) % 2 * 0.5
        s = wave(t, L, ph)
        lift = max(0, wave(t, L, ph + 0.25))
        return [0, -sx * 22 * s, -sx * 12 * lift], [0, 0, sx * 10 * lift]
    A["walk"] = anim(L, True, {
        "thorax": {"position": lambda t: [0, 0.6 * abs(wave(t, L / 2)), 0]},
        "abdomen": rot(lambda t: [3 * wave(t, L / 2), 4 * wave(t, L), 0]),
        **legs_track(gait),
    })
    # intro (3.5 s): curled and still, then rears up on the hind legs, front legs high, screeches with fangs wide
    L = 3.5

    def rear(t):
        return ease_out_back(window(t, 0.5, 1.4), 1.4) * (1 - ease_in_out(window(t, 2.9, 3.5)))

    def shake(t):
        return math.sin(t * 50) * window(t, 1.3, 1.5) * (1 - window(t, 2.4, 2.7))

    def intro_legs(t, i, sx):
        r = rear(t)
        if i < 2:
            return [0, -sx * 10 * r, -sx * 50 * r + 3 * shake(t)], [0, 0, sx * 30 * r]
        return [0, 0, sx * 12 * r], [0, 0, -sx * 8 * r]
    A["intro"] = anim(L, False, {
        "root": {"position": lambda t: [0, 2 * rear(t), 4 * rear(t)]},
        "thorax": rot(lambda t: [-38 * rear(t) + shake(t), 0, shake(t)]),
        "abdomen": rot(lambda t: [30 * rear(t), 0, 0]),
        "head": rot(lambda t: [-12 * rear(t) + 3 * shake(t), 0, 0]),
        "right_fang": rot(lambda t: [0, 0, 30 * rear(t)]),
        "left_fang": rot(lambda t: [0, 0, -30 * rear(t)]),
        **legs_track(intro_legs),
    })
    # bite (1.0 s): draws back, lunges at 0.45 s
    L = 1.0

    def back(t):
        return ease_in_out(window(t, 0, 0.35)) * (1 - window(t, 0.38, 0.48))

    def lunge(t):
        return ease_in(window(t, 0.38, 0.5)) * (1 - ease_in_out(window(t, 0.6, 1.0)))
    A["bite"] = anim(L, False, {
        "thorax": {"rotation": lambda t: [-14 * back(t) + 12 * lunge(t), 0, 0],
                   "position": lambda t: [0, 0, 3 * back(t) - 6 * lunge(t)]},
        "head": rot(lambda t: [-18 * back(t) + 20 * lunge(t), 0, 0]),
        "right_fang": rot(lambda t: [0, 0, 28 * back(t) - 10 * lunge(t)]),
        "left_fang": rot(lambda t: [0, 0, -28 * back(t) + 10 * lunge(t)]),
    })
    # spray (1.6 s): rears a little, abdomen curls under and pumps
    L = 1.6

    def up(t):
        return ease_out_back(window(t, 0, 0.4), 1.3) * (1 - ease_in_out(window(t, 1.2, 1.6)))
    A["spray"] = anim(L, False, {
        "thorax": rot(lambda t: [-16 * up(t), 0, 0]),
        "abdomen": rot(lambda t: [-35 * up(t) + 4 * math.sin(t * 30) * window(t, 0.4, 1.2), 0, 0]),
        "head": rot(lambda t: [10 * up(t), 0, 0]),
        "right_fang": rot(lambda t: [0, 0, 26 * up(t)]),
        "left_fang": rot(lambda t: [0, 0, -26 * up(t)]),
    })
    # brood (2.0 s): the sac heaves and splits open
    L = 2.0
    A["brood"] = anim(L, False, {
        "abdomen": {"rotation": lambda t: [10 * math.sin(t * 9) * window(t, 0, 0.3) * (1 - window(t, 1.5, 2.0)), 0, 0],
                    "scale": lambda t: [1 + 0.12 * ease_out_back(window(t, 0.2, 1.0)) * (1 - window(t, 1.3, 1.8))] * 3},
        "thorax": rot(lambda t: [8 * window(t, 0, 0.4) * (1 - window(t, 1.5, 2.0)), 0, 0]),
    })
    # leap (1.4 s): crouch, launch, legs splayed in the air, land at 1.0 s
    L = 1.4

    def crouch(t):
        return ease_in_out(window(t, 0, 0.35)) * (1 - window(t, 0.35, 0.45)) + ease_in(window(t, 0.95, 1.05)) * (1 - ease_in_out(window(t, 1.1, 1.4)))

    def air(t):
        return ease_out_back(window(t, 0.35, 0.55)) * (1 - ease_in(window(t, 0.85, 1.0)))
    A["leap"] = anim(L, False, {
        "root": {"position": lambda t: [0, -3 * crouch(t), 0]},
        "thorax": rot(lambda t: [-15 * air(t) + 8 * crouch(t), 0, 0]),
        **legs_track(lambda t, i, sx: ([0, 0, sx * 20 * crouch(t) - sx * 25 * air(t)], [0, 0, -sx * 15 * crouch(t) - sx * 20 * air(t)])),
    })
    # death (3.5 s): shudders, the legs fold in, it sinks and curls up on its back's side
    L = 3.5

    def fold(t):
        return ease_in_out(window(t, 0.6, 2.0))

    def sink(t):
        return ease_in(window(t, 1.2, 2.2))
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -8 * sink(t), 0]},
        "thorax": rot(lambda t: [10 * math.sin(t * 30) * (1 - window(t, 0, 0.8)) - 10 * sink(t), 0, 22 * sink(t)]),
        "abdomen": rot(lambda t: [-15 * fold(t), 0, -12 * sink(t)]),
        "head": rot(lambda t: [30 * fold(t), 0, 0]),
        **legs_track(lambda t, i, sx: ([0, 0, -sx * 45 * fold(t)], [0, 0, sx * 75 * fold(t)])),
    })
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- knight

def knight():
    name = "boss_knight"
    uvs, size = save_textures(name, [(r, r) for r in RANKS], lambda rng, r: tiles_for(
        rng, rank_tint((44, 44, 52), r), rank_tint((88, 90, 102), r, 0.15), mixc((170, 150, 104), rank_glow(r), 0.2), rank_glow(r),
        bone=(190, 186, 176), cloth=rank_tint((58, 22, 30), r, 0.4)), 400)
    m = Model(uvs)
    c = m.c
    HIP = 26
    m.bone("root", None, [0, 0, 0])
    m.bone("hips", "root", [0, HIP, 0], [
        c([-7, 23, -4], [14, 6, 8], DARK),
        c([-7.5, 22, -4.5], [15, 3, 9], TRIM),
        c([-6.5, 13, -4.6], [5.5, 10, 0.8], PLATE, pivot=[-4, 23, -4.2], rotation=[-6, 0, 4]),
        c([1, 13, -4.6], [5.5, 10, 0.8], PLATE, pivot=[4, 23, -4.2], rotation=[-6, 0, -4]),
    ])
    m.bone("torso", "hips", [0, 29, 0], [
        c([-6, 29, -3.5], [12, 6, 7], DARK),
        c([-9, 34, -5], [18, 12, 10], PLATE),
        c([-9.4, 39, -5.4], [18.8, 1.4, 10.8], TRIM),
        c([-1, 35, -5.6], [2, 10, 0.8], TRIM),
        c([-2, 37.5, -5.7], [4, 1.5, 0.6], GLOW),
    ])
    m.bone("cape", "torso", [0, 45, 5], [
        c([-8, 12, 4.8], [16, 33, 1], CLOTH),
        c([-7, 7, 4.9], [5, 6, 0.8], CLOTH),
        c([1.5, 5, 4.9], [5.5, 8, 0.8], CLOTH),
    ])
    m.bone("head", "torso", [0, 46, 0], [
        c([-4.5, 46, -4.5], [9, 10, 9], PLATE),
        c([-5, 46.5, -5], [10, 2, 10], TRIM),
        c([-3.5, 50.5, -4.9], [7, 1.3, 0.6], GLOW),
        c([-0.5, 47, -4.9], [1, 3.5, 0.6], GLOW),
        # swept horns
        c([-9, 53, -1.5], [5, 2.5, 2.5], BONE, pivot=[-4.5, 54, 0], rotation=[0, 0, -28]),
        c([-12, 55.5, -1.2], [3, 4.5, 2], BONE, pivot=[-10, 56, 0], rotation=[0, 0, -10]),
        c([4, 53, -1.5], [5, 2.5, 2.5], BONE, pivot=[4.5, 54, 0], rotation=[0, 0, 28]),
        c([9, 55.5, -1.2], [3, 4.5, 2], BONE, pivot=[10, 56, 0], rotation=[0, 0, 10]),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * 11
        m.bone(f"{side}_arm", "torso", [x, 44, 0], [
            c([x - 2.5 + sx * 0.5, 33, -2.5], [5, 11, 5], DARK),
            c([x - 4 + sx * 1, 40, -4], [8, 6, 8], PLATE),
            c([x - 4.3 + sx * 1, 45.4, -4.3], [8.6, 1.3, 8.6], TRIM),
            c([x - 1 + sx * 3, 46, -1], [2, 5, 2], BONE, pivot=[x + sx * 3, 46, 0], rotation=[0, 0, -sx * 18]),
        ])
        m.bone(f"{side}_forearm", f"{side}_arm", [x + sx * 0.5, 34, 0], [
            c([x - 2.8 + sx * 0.5, 23, -2.8], [5.6, 11, 5.6], PLATE),
            c([x - 3 + sx * 0.5, 23, -3], [6, 2, 6], TRIM),
            c([x - 2.4 + sx * 0.5, 19, -2.4], [4.8, 4, 4.8], DARK),
        ])
        hx = sx * 3.8
        m.bone(f"{side}_leg", "hips", [hx, HIP, 0], [
            c([hx - 3, 13, -3], [6, 13, 6], DARK),
            c([hx - 3.4, 18, -3.5], [6.8, 6, 7], PLATE),
        ])
        m.bone(f"{side}_shin", f"{side}_leg", [hx, 13, 0], [
            c([hx - 3, 2, -3], [6, 11, 6], PLATE),
            c([hx - 3.3, 9.5, -3.5], [6.6, 1.4, 7], TRIM),
            c([hx - 3.2, 0, -5.5], [6.4, 2.5, 8.5], DARK),
        ])
    # the greatsword, in the right fist; at rest the blade points ahead (-z)
    fx, fy = -11.5, 20.5
    m.bone("sword", "right_forearm", [fx, fy, 0], [
        c([fx - 0.9, fy - 0.9, -2], [1.8, 1.8, 9], DARK),
        c([fx - 1.4, fy - 1.4, 6.5], [2.8, 2.8, 2.8], TRIM),
        c([fx - 1, fy - 5, -4], [2, 10, 2], TRIM),
        c([fx - 0.6, fy - 3.2, -30], [1.2, 6.4, 26], PLATE),
        c([fx - 0.4, fy - 2.2, -34], [0.8, 4.4, 4], PLATE),
        c([fx - 0.65, fy - 0.4, -28], [1.3, 0.8, 22], GLOW),
        # broken notch near the tip
        c([fx - 0.7, fy + 1.6, -27], [1.4, 1.6, 3], DARK),
    ])
    write_geo(name, m.geo(name, size, 5, 5, 1.8))

    STAND = {"right_arm": [-12, 0, 6], "right_forearm": [-40, 0, 0], "sword": [12, 0, 0], "left_arm": [4, 0, -6],
             "left_forearm": [-12, 0, 0], "torso": [0, 0, 0], "head": [0, 0, 0], "hips": [0, 0, 0], "cape": [-4, 0, 0],
             "right_leg": [0, 0, 0], "left_leg": [0, 0, 0], "right_shin": [0, 0, 0], "left_shin": [0, 0, 0]}

    def P(blade=None, base=None, **kw):
        """A pose; blade is the angle the sword makes with the horizon (0 ahead, 90 up, -90 down)."""
        p = dict(base or STAND)
        p.update(kw)
        if blade is not None:
            chain = p["torso"][0] + p["right_arm"][0] + p["right_forearm"][0]
            p["sword"] = [-blade - chain, 0, 0]
        return p

    def timeline(steps, length, extra=None, pos=None):
        names = sorted({b for _, p, _ in steps for b in p})

        def at(bone, t):
            v = steps[0][1].get(bone, [0, 0, 0])
            for (t0, p0, _), (t1, p1, ease) in zip(steps, steps[1:]):
                if t >= t0:
                    k = ease(window(t, t0, t1))
                    a, b = p0.get(bone, [0, 0, 0]), p1.get(bone, [0, 0, 0])
                    v = [a[i] + (b[i] - a[i]) * k for i in range(3)]
            return v
        out = {}
        for n in names:
            add = (extra or {}).get(n)
            out[n] = {"rotation": (lambda t, n=n, add=add: [x + y for x, y in zip(at(n, t), add(t))]) if add else (lambda t, n=n: at(n, t))}
        for n, fn in (pos or {}).items():
            out.setdefault(n, {})["position"] = fn
        return out

    lin = lambda t: max(0.0, min(1.0, t))  # noqa: E731
    A = {}
    L = 3.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], L, extra={
        "torso": lambda t: [1.5 * wave(t, L), 0, 0],
        "head": lambda t: [-1.5 * wave(t, L), 5 * wave(t, L * 2), 0],
        "cape": lambda t: [-2 * wave(t, L, 0.3), 0, 0],
        "right_arm": lambda t: [1.5 * wave(t, L), 0, 0],
    }, pos={"hips": lambda t: [0, -0.4 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0]}), 0.1)
    L = 1.3
    A["walk"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], L, extra={
        "right_leg": lambda t: [-26 * wave(t, L), 0, 0], "left_leg": lambda t: [26 * wave(t, L), 0, 0],
        "right_shin": lambda t: [18 * max(0, wave(t, L, 0.25)), 0, 0], "left_shin": lambda t: [18 * max(0, wave(t, L, 0.75)), 0, 0],
        "left_arm": lambda t: [-14 * wave(t, L), 0, 0],
        "torso": lambda t: [4, 5 * wave(t, L), 0], "cape": lambda t: [-10 - 4 * abs(wave(t, L)), 0, 0],
    }, pos={"hips": lambda t: [0, -1 * abs(wave(t, L)), 0]}))
    # intro (3.5 s): kneels with the sword planted, rises, wrenches it free, levels it at the intruder
    L = 3.5
    KNEEL = P(blade=-90, hips=[0, 0, 0], torso=[18, 0, 0], head=[24, 0, 0], right_leg=[-90, 0, 0], right_shin=[90, 0, 0],
              left_leg=[0, 0, 0], left_shin=[88, 0, 0], right_arm=[-60, 0, 0], right_forearm=[-10, 0, 0],
              left_arm=[-40, 0, -8], left_forearm=[-50, 0, 0], cape=[30, 0, 0])
    RISE = P(blade=-90, torso=[6, 0, 0], head=[-8, 0, 0], right_arm=[-60, 0, 4], right_forearm=[-10, 0, 0])
    WRENCH = P(blade=100, torso=[-12, 0, 0], head=[-16, 0, 0], right_arm=[-160, 0, 14], right_forearm=[-20, 0, 0],
               left_arm=[20, 0, -30], left_forearm=[-30, 0, 0], cape=[-18, 0, 0])
    POINT = P(blade=0, torso=[2, -18, 0], head=[0, 14, 0], right_arm=[-88, -10, 0], right_forearm=[-4, 0, 0],
              left_arm=[6, 0, -14], cape=[-10, 0, 0])
    kneel_drop = lambda t: -9 * (1 - ease_in_out(window(t, 0.6, 1.4)))  # noqa: E731
    A["intro"] = anim(L, False, timeline([(0, KNEEL, lin), (0.6, KNEEL, lin), (1.4, RISE, ease_in_out), (1.9, WRENCH, ease_out_back),
                                          (2.5, POINT, ease_in_out), (3.1, POINT, lin), (3.5, STAND, ease_in_out)], L,
                                         pos={"hips": lambda t: [0, kneel_drop(t), 0]}))
    # cleave (2.0 s): three strikes landing at 0.45, 0.95 and 1.55 s
    L = 2.0
    UP = P(blade=110, torso=[-8, 20, 0], right_arm=[-150, 0, 20], right_forearm=[-30, 0, 0])
    DOWN = P(blade=-35, torso=[18, -10, 0], right_arm=[-40, 0, 0], right_forearm=[-10, 0, 0], head=[10, 0, 0])
    SIDE_A = P(blade=0, torso=[2, 45, 0], right_arm=[-90, -60, 30], right_forearm=[-10, 0, 0])
    SIDE_B = P(blade=0, torso=[6, -50, 0], right_arm=[-90, 70, -10], right_forearm=[-10, 0, 0])
    OVER = P(blade=120, torso=[-14, 0, 0], right_arm=[-175, 0, 0], right_forearm=[-25, 0, 0], left_arm=[-160, 0, 0], left_forearm=[-25, 0, 0])
    SMASH = P(blade=-60, torso=[24, 0, 0], right_arm=[-60, 0, 0], left_arm=[-55, 0, 0], left_forearm=[-10, 0, 0], head=[14, 0, 0])
    A["cleave"] = anim(L, False, timeline([(0, STAND, lin), (0.3, UP, ease_in_out), (0.45, DOWN, ease_in), (0.7, SIDE_A, ease_in_out),
                                           (0.95, SIDE_B, ease_in), (1.25, OVER, ease_in_out), (1.55, SMASH, ease_in), (2.0, STAND, ease_in_out)], L,
                                          pos={"hips": lambda t: [0, -2.5 * window(t, 1.45, 1.55) * (1 - window(t, 1.6, 2.0)), 0]}))
    # charge (1.4 s): the blade lowered ahead like a lance, a lean, and the follow-through
    L = 1.4
    LANCE = P(blade=0, torso=[22, 0, 0], head=[-14, 0, 0], right_arm=[-70, 0, 6], right_forearm=[-10, 0, 0],
              left_arm=[30, 0, -10], right_leg=[-30, 0, 0], left_leg=[30, 0, 0], left_shin=[20, 0, 0], cape=[-30, 0, 0])
    A["charge"] = anim(L, False, timeline([(0, STAND, lin), (0.35, LANCE, ease_in_out), (1.1, LANCE, lin), (1.4, STAND, ease_in_out)], L))
    # rising blades (1.8 s): drives the sword into the floor at 0.8 s, holds, pulls it out
    L = 1.8
    RAISE = P(blade=90, torso=[-10, 0, 0], right_arm=[-170, 0, 10], right_forearm=[-10, 0, 0], left_arm=[-160, 0, -10], left_forearm=[-10, 0, 0])
    PLUNGE = P(blade=-90, torso=[30, 0, 0], head=[10, 0, 0], right_arm=[-75, 0, 4], right_forearm=[-5, 0, 0], left_arm=[-70, 0, -4], left_forearm=[-5, 0, 0],
               right_leg=[-40, 0, 0], right_shin=[50, 0, 0], left_shin=[40, 0, 0])
    A["rising"] = anim(L, False, timeline([(0, STAND, lin), (0.55, RAISE, ease_in_out), (0.8, PLUNGE, ease_in), (1.4, PLUNGE, lin), (1.8, STAND, ease_in_out)], L,
                                          pos={"hips": lambda t: [0, -4 * window(t, 0.7, 0.8) * (1 - window(t, 1.4, 1.8)), 0]}))
    # death (3.5 s): staggers, the sword drops from the hand, falls to its knees and forward; the helm rolls off
    L = 3.5
    REEL = P(blade=-20, torso=[-18, 10, 0], head=[-24, 0, 0], right_arm=[-30, 0, 30], right_forearm=[-20, 0, 0], left_arm=[10, 0, -40])
    KNEES = P(blade=-90, torso=[20, 0, 0], head=[30, 0, 0], right_leg=[-90, 0, 0], right_shin=[90, 0, 0], left_leg=[-90, 0, 0], left_shin=[90, 0, 0],
              right_arm=[0, 0, 10], right_forearm=[0, 0, 0], left_arm=[0, 0, -10], cape=[30, 0, 0])
    SLUMP = P(blade=-90, base=KNEES, torso=[70, 0, 0], head=[20, 0, 25], right_arm=[-50, 0, 10], left_arm=[-50, 0, -10])
    A["death"] = anim(L, "hold", timeline([(0, STAND, lin), (0.4, REEL, ease_out_back), (1.0, REEL, lin), (1.6, KNEES, ease_in),
                                           (2.6, SLUMP, ease_in_out), (3.5, SLUMP, lin)], L,
                                          pos={"hips": lambda t: [0, -11 * ease_in(window(t, 1.0, 1.6)) - 4 * ease_in_out(window(t, 1.6, 2.6)), -3 * window(t, 1.6, 2.6)],
                                               "sword": lambda t: [0, -14 * ease_in(window(t, 0.5, 1.1)), 0],
                                               "head": lambda t: [5 * ease_in(window(t, 2.6, 3.2)), -6 * ease_in(window(t, 2.6, 3.0)), -8 * window(t, 2.6, 3.3)]}))
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- magus

def magus():
    name = "boss_magus"
    uvs, size = save_textures(name, [(r, r) for r in RANKS], lambda rng, r: tiles_for(
        rng, rank_tint((52, 48, 70), r, 0.3), rank_tint((70, 66, 88), r), (196, 170, 110), rank_glow(r),
        bone=(232, 226, 210), cloth=rank_tint((38, 34, 54), r, 0.35)), 500)
    m = Model(uvs)
    c = m.c
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 26, 0], [
        # the robe tapers to nothing: no legs
        c([-3, 6, -2.5], [6, 8, 5], CLOTH),
        c([-5, 13, -4], [10, 12, 8], CLOTH),
        c([-7, 24, -5], [14, 14, 10], CLOTH),
        c([-7.4, 33, -5.4], [14.8, 1.5, 10.8], TRIM),
        c([-1, 22, -5.5], [2, 14, 0.8], TRIM),
        c([-8.5, 37, -5.5], [17, 4, 11], HIDE),
    ])
    m.bone("tatters", "body", [0, 13, 0], [
        c([-4.5, 2, -3.5], [3, 8, 1], CLOTH), c([1, 0, -3.5], [3, 10, 1], CLOTH), c([-2, 3, 2.5], [4, 9, 1], CLOTH),
    ])
    m.bone("head", "body", [0, 41, 0], [
        c([-5, 41, -4.5], [10, 11, 10], CLOTH),
        c([-5.5, 49, -5], [11, 4, 11], CLOTH),
        c([-3.8, 42, -5.2], [7.6, 9, 1], BONE),
        c([-1, 46.5, -5.6], [2, 2, 0.6], EYE),
        c([-0.3, 43, -5.5], [0.6, 3, 0.4], GLOW),
    ])
    m.bone("crown", "head", [0, 56, 0])
    for i in range(6):
        a = i / 6 * 2 * math.pi
        x, z = 8 * math.cos(a), 8 * math.sin(a)
        m.bone(f"shard{i}", "crown", [x, 56, z], [c([x - 1, 54 + (i % 2) * 1.5, z - 1], [2, 5 + (i % 3), 2], GLOW,
                                                    pivot=[x, 56, z], rotation=[15 * math.sin(a), 0, -15 * math.cos(a)])])
    for side, sx in (("right", -1), ("left", 1)):
        x = sx * 9
        m.bone(f"{side}_arm", "body", [x, 38, 0], [
            c([x - 2.5 + sx * 0.5, 26, -2.5], [5, 13, 5], CLOTH),
            c([x - 3 + sx * 0.5, 35, -3], [6, 4, 6], HIDE),
        ])
        m.bone(f"{side}_forearm", f"{side}_arm", [x + sx * 0.5, 27, 0], [
            c([x - 2 + sx * 0.5, 16, -2], [4, 11, 4], DARK),
            c([x - 2.8 + sx * 0.5, 22, -2.8], [5.6, 5, 5.6], CLOTH),
            c([x - 1.2 + sx * 0.5, 12, -1.6], [2.4, 4, 3.2], BONE),
        ])
    # staff in the left hand, upright
    sx, sy = 9.5, 14
    m.bone("staff", "left_forearm", [sx, sy, 0], [
        c([sx - 0.7, sy - 22, -0.7], [1.4, 46, 1.4], DARK),
        c([sx - 1.5, sy + 22, -1.5], [3, 2, 3], TRIM),
        c([sx - 2, sy + 24, -2], [4, 5, 4], GLOW, pivot=[sx, sy + 26, 0], rotation=[0, 45, 0]),
        c([sx - 3, sy + 23, -0.5], [1, 6, 1], TRIM, pivot=[sx - 2.5, sy + 23, 0], rotation=[0, 0, 20]),
        c([sx + 2, sy + 23, -0.5], [1, 6, 1], TRIM, pivot=[sx + 2.5, sy + 23, 0], rotation=[0, 0, -20]),
    ])
    write_geo(name, m.geo(name, size, 5, 6, 2))

    A = {}
    L = 4.0
    hover = lambda t: [0, 1.2 * wave(t, L), 0]  # noqa: E731
    A["idle"] = anim(L, True, {
        "root": {"position": hover},
        "crown": rot(lambda t: [0, 90 * t, 0]),
        "body": rot(lambda t: [2 * wave(t, L, 0.25), 0, 0]),
        "tatters": rot(lambda t: [6 * wave(t, L / 2), 0, 3 * wave(t, L)]),
        "right_arm": rot(lambda t: [-6 + 3 * wave(t, L), 0, 10]),
        "left_arm": rot(lambda t: [-20, 0, -8]), "left_forearm": rot(lambda t: [-20, 0, 0]),
        "right_forearm": rot(lambda t: [-20, 0, 0]),
    }, 0.1)
    L = 2.0
    A["walk"] = anim(L, True, {
        "root": {"position": lambda t: [0, 1.0 * wave(t, L), 0]},
        "body": rot(lambda t: [12, 0, 0]),
        "crown": rot(lambda t: [0, 180 * t, 0]),
        "tatters": rot(lambda t: [-25 + 6 * wave(t, L / 2), 0, 0]),
        "right_arm": rot(lambda t: [20, 0, 14]), "left_arm": rot(lambda t: [-14, 0, -10]), "left_forearm": rot(lambda t: [-24, 0, 0]),
    }, 0.1)
    # intro (3.5 s): hangs folded, a cocoon of robe; rises, the shards fly out, arms open wide
    L = 3.5

    def unfold(t):
        return ease_out_back(window(t, 0.8, 1.8), 1.2)

    def lift(t):
        return ease_in_out(window(t, 0.3, 1.6))
    A["intro"] = anim(L, False, {
        "root": {"position": lambda t: [0, -6 + 10 * lift(t) - 4 * window(t, 3.0, 3.5), 0]},
        "body": rot(lambda t: [30 * (1 - unfold(t)) - 10 * unfold(t) * (1 - window(t, 2.8, 3.5)), 0, 0]),
        "head": rot(lambda t: [40 * (1 - unfold(t)) - 20 * unfold(t) * (1 - window(t, 2.8, 3.5)), 0, 0]),
        "right_arm": rot(lambda t: [-60 * (1 - unfold(t)), 0, 80 * unfold(t) * (1 - window(t, 2.8, 3.5))]),
        "left_arm": rot(lambda t: [-60 * (1 - unfold(t)), 0, -80 * unfold(t) * (1 - window(t, 2.8, 3.5))]),
        "crown": {"rotation": lambda t: [0, 400 * ease_in_out(window(t, 0.8, 3.5)), 0],
                  "scale": lambda t: [0.2 + 0.8 * ease_out_back(window(t, 1.2, 2.2), 2.4)] * 3},
        "tatters": rot(lambda t: [10 * math.sin(t * 6), 0, 0]),
    })
    # cast (1.2 s): the right hand thrust forward at 0.55 s
    L = 1.2

    def draw_back(t):
        return ease_in_out(window(t, 0, 0.45)) * (1 - window(t, 0.45, 0.55))

    def thrust(t):
        return ease_in(window(t, 0.45, 0.58)) * (1 - ease_in_out(window(t, 0.75, 1.2)))
    A["cast"] = anim(L, False, {
        "right_arm": rot(lambda t: [40 * draw_back(t) - 95 * thrust(t), 0, 20 * draw_back(t)]),
        "right_forearm": rot(lambda t: [-40 * draw_back(t), 0, 0]),
        "body": rot(lambda t: [-6 * draw_back(t) + 10 * thrust(t), 20 * draw_back(t) - 10 * thrust(t), 0]),
        "crown": rot(lambda t: [0, 300 * t, 0]),
    })
    # sigil (1.8 s): staff and free hand raised high, held, brought down at 1.2 s
    L = 1.8

    def high(t):
        return ease_out_back(window(t, 0, 0.5), 1.4) * (1 - ease_in(window(t, 1.05, 1.2)))
    A["sigil"] = anim(L, False, {
        "root": {"position": lambda t: [0, 3 * high(t), 0]},
        "right_arm": rot(lambda t: [-160 * high(t) - 40 * window(t, 1.1, 1.2) * (1 - window(t, 1.3, 1.8)), 0, 15 * high(t)]),
        "left_arm": rot(lambda t: [-150 * high(t), 0, -15 * high(t)]),
        "body": rot(lambda t: [-12 * high(t) + 18 * window(t, 1.1, 1.2) * (1 - window(t, 1.3, 1.8)), 0, 0]),
        "head": rot(lambda t: [-20 * high(t), 0, 0]),
        "crown": {"rotation": lambda t: [0, 720 * t / L, 0], "scale": lambda t: [1 + 0.5 * high(t)] * 3},
    })
    # blink (0.8 s): folds into itself and vanishes (scale 0 at 0.35 s), then unfolds where it lands
    L = 0.8

    def gone(t):
        return ease_in(window(t, 0.05, 0.35)) * (1 - ease_out_back(window(t, 0.45, 0.8), 1.6))
    A["blink"] = anim(L, False, {
        "root": {"scale": lambda t: [max(0.01, 1 - gone(t))] * 3, "rotation": lambda t: [0, 360 * gone(t), 0]},
    })
    # summon (1.6 s): the staff is driven down at 0.7 s
    L = 1.6
    A["summon"] = anim(L, False, {
        "left_arm": rot(lambda t: [-100 * ease_in_out(window(t, 0, 0.5)) * (1 - window(t, 0.55, 0.7)) - 30 * window(t, 0.6, 0.7) * (1 - ease_in_out(window(t, 1.0, 1.6))), 0, -10]),
        "body": rot(lambda t: [-10 * window(t, 0, 0.5) + 25 * window(t, 0.6, 0.7) * (1 - window(t, 1.0, 1.6)), 0, 0]),
        "root": {"position": lambda t: [0, 3 * window(t, 0, 0.5) - 5 * window(t, 0.6, 0.7) * (1 - window(t, 1.0, 1.6)), 0]},
        "crown": rot(lambda t: [0, 400 * t, 0]),
    })
    # death (3.5 s): the shards fall away, it shudders, the robe crumples to the floor and the mask drops
    L = 3.5

    def fall(t):
        return ease_in(window(t, 1.2, 2.4))
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -22 * fall(t), 0]},
        "crown": {"position": lambda t: [0, -40 * ease_in(window(t, 0.1, 0.9)), 0], "scale": lambda t: [max(0.01, 1 - window(t, 0.6, 1.0))] * 3},
        "body": rot(lambda t: [8 * math.sin(t * 34) * (1 - window(t, 0, 1.2)) + 30 * fall(t), 0, 10 * fall(t)]),
        "head": {"rotation": lambda t: [-30 * window(t, 0, 0.6) + 80 * fall(t), 0, 30 * fall(t)],
                 "position": lambda t: [0, -6 * ease_in(window(t, 2.4, 2.8)), -4 * window(t, 2.4, 2.8)]},
        "right_arm": rot(lambda t: [-90 * window(t, 0, 0.6) * (1 - fall(t)), 0, 60 * fall(t)]),
        "left_arm": rot(lambda t: [-90 * window(t, 0, 0.6) * (1 - fall(t)), 0, -60 * fall(t)]),
        "staff": rot(lambda t: [80 * ease_in(window(t, 1.6, 2.4)), 0, 0]),
    })
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- wyrm

# the wyrm's wing in its two poses, for the left wing (the right mirrors y and z): (arm, finger) rotations, and how
# far each membrane is gathered along the body when folded
WING_FOLD = ((0, -54, -100), (0, -56, 0))
WING_OPEN = ((0, 6, -24), (0, -20, 6))
WING_GATHER = (0.3, 0.5)


def wyrm():
    """A four-legged drake: a neck held in an S, haunches, a row of spines, a bladed tail and wings that lie folded
    along the back until it rears or beats them. Built in design units (drawn 1.3 times bigger, MODEL_SCALE) and
    painted on the model by solid_skins.paint_wyrm, which goes by the tags and coordinates used here. The bones'
    rest rotations are the standing pose (the game adds an animation's rotation to them), so the animations below
    are all movements away from that pose."""
    name = "boss_wyrm"
    uvs, size = save_textures(name, [(r, r) for r in RANKS], lambda rng, r: tiles_for(
        rng, rank_tint((150, 64, 40), r, 0.3), rank_tint((170, 120, 80), r, 0.2), (210, 170, 110), rank_glow(r),
        bone=(222, 210, 186), cloth=rank_tint((112, 48, 40), r, 0.3), spots=True), 600)
    m = Model(uvs)
    c = m.c

    def spine(y, z, h, lean=-28, w=1.8, d=3.0, x=0.0):
        """A back spine: two steps to a point, raked back."""
        piv = [x, y, z]
        return [c([x - w / 2, y - 0.7, z - d / 2], [w, h * 0.55 + 0.7, d], BONE, pivot=piv, rotation=[lean, 0, 0], tag="spine", part=0, skip=("down",)),
                c([x - w * 0.3, y + h * 0.55, z - d * 0.17], [w * 0.6, h * 0.45, d * 0.6], BONE, pivot=piv, rotation=[lean, 0, 0], tag="spine", part=1,
                  skip=("down",))]

    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 18, 0], [
        c([-8.5, 11, -14], [17, 13, 30], HIDE, tag="torso", skip=("north",)),
        c([-10, 12.5, -15.5], [20, 13.5, 12], HIDE, tag="hump"),
        c([-9.5, 12, 5], [19, 12.5, 11.5], HIDE, tag="hips"),
        # the plated underside, deeper between the forelegs
        c([-6.5, 9.5, -15], [13, 2.5, 31], PLATE, tag="belly", skip=("up",)),
        c([-7.2, 8.6, -15.6], [14.4, 4.4, 11], PLATE, tag="keel", skip=("up",)),
        *spine(26, -12.6, 4.6), *spine(26, -7.4, 5.2), *spine(24, -1.6, 4.6), *spine(24, 3.4, 4.0),
        *spine(24.5, 8.4, 3.6), *spine(24.5, 13.2, 3.2),
    ])
    m.bone("neck", "body", [0, 21, -14], [
        c([-4.5, 16.5, -26], [9, 9, 16], HIDE, tag="neck", skip=("south",)),
        *spine(25.5, -17.6, 3.6), *spine(25.5, -22.4, 3.2),
    ], rotation=[-48, 0, 0])
    m.bone("neck2", "neck", [0, 21, -25], [
        c([-4, 17, -35.5], [8, 8, 12], HIDE, tag="neck", part=1, skip=("south",)),
        *spine(25, -27.6, 2.9), *spine(25, -32.2, 2.5),
    ], rotation=[22, 0, 0])

    head = [
        c([-6, 18, -46.5], [12, 8, 13.5], HIDE, tag="skull"),
        c([-5, 18.5, -53.5], [10, 5.5, 7.5], HIDE, tag="snout", part=0, skip=("south",)),
        c([-4, 18.5, -58], [8, 4.6, 5], HIDE, tag="snout", part=1, skip=("south",)),
        # the horn on the nose
        *spine(23.1, -55.2, 2.8, lean=-20, w=1.5, d=2.2),
        # fangs: boxes whose sides are cut into teeth, overhanging the lower jaw
        c([-4.95, 16.8, -53.45], [9.9, 1.8, 7.4], BONE, tag="teeth_up", part=0, skip=("south", "up", "down")),
        c([-3.95, 16.8, -57.95], [7.9, 1.8, 4.95], BONE, tag="teeth_up", part=1, skip=("south", "up", "down")),
    ]
    for sx in (-1, 1):
        # nostril, eye socket (the eye is painted on its outer and front faces) and the ridge over it
        head.append(c([sx * 2.3 - 1.1, 22.5, -57.8], [2.2, 1.4, 2.8], DARK, tag="nostril", skip=("down",)))
        head.append(c([sx * 6.2 - 0.6, 21.9, -45.9], [1.2, 2.9, 4.4], DARK, tag="eye", skip=("down",)))
        head.append(c([sx * 4.5 - 2.5, 24.9, -47.2], [5, 2, 7.6], DARK, pivot=[sx * 4.5, 25.5, -43], rotation=[9, 0, sx * -9], tag="brow"))
        # a short spike on the cheek, under the horn
        for part, (w, d0, d1) in enumerate(((2.2, -37.5, 4.6), (1.3, -32.9, 3.2))):
            head.append(c([sx * 5.6 - w / 2, 20.3 - w / 2, d0], [w, w, d1], BONE, pivot=[sx * 5.6, 20.3, -37.5], rotation=[6, sx * 26, 0],
                          tag="horn", part=3 + part, skip=("north",)))
    m.bone("head", "neck2", [0, 21, -34.5], head, rotation=[34, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # the horns: swept back and up, in two bones so they curve
        m.bone(f"{side}_horn", "head", [sx * 4.3, 24.6, -36.2], [
            c([sx * 4.3 - 1.6, 23.0, -37.2], [3.2, 3.2, 9], BONE, tag="horn", part=0, skip=("north",)),
        ], rotation=[24, sx * 15, 0])
        m.bone(f"{side}_horn_tip", f"{side}_horn", [sx * 4.3, 24.6, -28.6], [
            c([sx * 4.3 - 1.1, 23.5, -29.4], [2.2, 2.2, 7.6], BONE, tag="horn", part=1, skip=("north",)),
            c([sx * 4.3 - 0.6, 24.0, -21.8], [1.2, 1.2, 3.4], BONE, tag="horn", part=2, skip=("north",)),
        ], rotation=[20, sx * -7, 0])
    m.bone("jaw", "head", [0, 19, -38], [
        c([-4.7, 15.6, -53.3], [9.4, 2.8, 15.8], HIDE, tag="jaw", part=0, skip=("south",)),
        c([-3.7, 15.9, -57.6], [7.4, 2.5, 4.6], HIDE, tag="jaw", part=1, skip=("south",)),
        # the jowl: the jaw is deep at the hinge and thin at the tip, so it reads as a jaw and not a plank when it gapes
        c([-4.3, 14.0, -47.6], [8.6, 1.8, 9.8], HIDE, tag="jaw", part=2, skip=("up",)),
        c([-3.8, 12.9, -43.4], [7.6, 1.3, 5.6], HIDE, tag="jaw", part=3, skip=("up",)),
        c([-4.3, 18.2, -53.0], [8.6, 1.5, 9], BONE, tag="teeth_low", part=0, skip=("south", "up", "down")),
        c([-3.3, 18.2, -57.3], [6.6, 1.5, 4.4], BONE, tag="teeth_low", part=1, skip=("south", "up", "down")),
    ], rotation=[5, 0, 0])

    m.bone("tail", "body", [0, 18, 16], [
        c([-6, 12.5, 14.5], [12, 10.5, 15.5], HIDE, tag="tail", part=0, skip=("north",)),
        *spine(23, 19.5, 3.0), *spine(23, 25.2, 2.7),
    ], rotation=[-14, 0, 0])
    m.bone("tail2", "tail", [0, 18, 30], [
        c([-4.5, 14, 28.5], [9, 8, 15.5], HIDE, tag="tail", part=1, skip=("north",)),
        *spine(22, 33.2, 2.4, w=1.6), *spine(22, 39, 2.1, w=1.6),
    ], rotation=[5, 0, 0])
    m.bone("tail3", "tail2", [0, 18, 44], [
        c([-3, 15.5, 42.5], [6, 5.5, 13.5], HIDE, tag="tail", part=2, skip=("north",)),
        c([-1.8, 16.4, 55.5], [3.6, 3.6, 7], HIDE, tag="tail", part=3, skip=("north",)),
        *spine(21, 46.6, 1.9, w=1.4, d=2.4), *spine(21, 51.4, 1.6, w=1.4, d=2.4),
        # the blade on the end: an upright and a flat sheet, cut to shape in the texture
        c([0, 10.2, 53.5], [0, 16, 15], CLOTH, tag="blade", part=0, skip=("east",)),
        c([-8, 18.2, 53.5], [16, 0, 15], CLOTH, tag="blade", part=1, skip=("down",)),
    ], rotation=[10, 0, 0])

    def foot(cx, y, z, sx):
        out = [c([cx - 3.1, y, z - 5.6], [6.2, 2.6, 8.2], HIDE, tag="foot")]
        for i in range(3):
            tx = cx - 2.9 + i * 2.1
            out.append(c([tx, y, z - 7.9], [1.6, 1.8, 2.6], BONE, tag="claw", part=0, skip=("south",)))
            out.append(c([tx + 0.35, y, z - 9.4], [0.9, 1.1, 1.6], BONE, tag="claw", part=1, skip=("south",)))
        # the dew claw behind
        out.append(c([cx + sx * 1.6 - 0.6, y + 0.2, z + 2.2], [1.2, 1.3, 2.2], BONE, tag="claw", part=2, skip=("north",)))
        return out

    for side, sx in (("right", -1), ("left", 1)):
        # front: the upper arm back to the elbow, the forearm forward to the foot
        hx, z = sx * 9.3, -9.5
        cx = hx + sx * 0.8
        m.bone(f"{side}_front", "body", [hx, 18, z], [
            c([cx - 3.2, 8.2, z - 4.1], [6.4, 12.6, 8.2], HIDE, tag="upper", part=0),
            *spine(9.6, z + 3.6, 2.4, lean=-78, w=1.3, d=2.0, x=cx),          # the spur on the elbow
        ], rotation=[22, 0, sx * -4])
        m.bone(f"{side}_front_low", f"{side}_front", [cx, 9.4, z], [
            c([cx - 2.3, -0.8, z - 2.3], [4.6, 10.8, 4.6], HIDE, tag="lower", part=0, skip=("up", "down")),
        ], rotation=[-48, 0, 0])
        m.bone(f"{side}_front_foot", f"{side}_front_low", [cx, 0, z], foot(cx, -1.55, z, sx), rotation=[26, 0, sx * 4])
        # hind: the thigh forward to the knee, the shank back to the hock
        hx, z = sx * 9.0, 10.5
        cx = hx + sx * 0.9
        m.bone(f"{side}_hind", "body", [hx, 18, z], [
            c([cx - 3.4, 7.2, z - 4.7], [6.8, 14.2, 9.6], HIDE, tag="upper", part=1),
        ], rotation=[-30, 0, sx * -4])
        m.bone(f"{side}_hind_low", f"{side}_hind", [cx, 8.8, z], [
            c([cx - 2.2, -1.8, z - 2.2], [4.4, 11.4, 4.4], HIDE, tag="lower", part=1, skip=("up", "down")),
            *spine(8.4, z + 1.8, 2.2, lean=-70, w=1.2, d=1.8, x=cx),          # the spur on the hock
        ], rotation=[58, 0, 0])
        m.bone(f"{side}_hind_foot", f"{side}_hind_low", [cx, -1.0, z], foot(cx, -2.4, z, sx), rotation=[-28, 0, sx * 4])

        # wings: the arm, then the long finger the membrane hangs from. The membranes are flat sheets (drawn from
        # both sides) on bones of their own, so folding can gather them (their scale along the body).
        wx = sx * 10
        m.bone(f"{side}_wing", "body", [wx, 25.6, -8], [
            c([wx if sx > 0 else wx - 12.6, 24.5, -9.2], [12.6, 2.2, 2.4], BONE, tag="wing_arm", part=0),
        ], rotation=[WING_FOLD[0][0], sx * WING_FOLD[0][1], sx * WING_FOLD[0][2]])
        ex = sx * 22
        m.bone(f"{side}_wing_tip", f"{side}_wing", [ex, 25.6, -8], [
            c([ex if sx > 0 else ex - 30, 24.85, -8.85], [30, 1.5, 1.7], BONE, tag="wing_arm", part=1),
            c([ex - 0.75, 24.85, -11.4], [1.5, 1.5, 2.8], BONE, tag="wing_claw", skip=("south",)),
        ], rotation=[WING_FOLD[1][0], sx * WING_FOLD[1][1], sx * WING_FOLD[1][2]])
        m.bone(f"{side}_membrane", f"{side}_wing", [wx, 25.6, -8], [
            c([wx if sx > 0 else wx - 14, 25.5, -8], [14, 0, 25], CLOTH, tag="membrane", part=0, skip=("down",)),
        ])
        m.bone(f"{side}_membrane_tip", f"{side}_wing_tip", [ex, 25.6, -8], [
            c([ex if sx > 0 else ex - 31, 25.15, -8], [31, 0, 28], CLOTH, tag="membrane", part=1, skip=("down",)),
        ])
    write_geo(name, m.geo(name, size, 9, 6, 1.8))

    def wings(open_k, flap=None):
        """Folded (0, the rest pose) to spread (1); flap lifts (+) or beats down (-) the spread wing."""
        out = {}
        f = flap or (lambda t: 0.0)
        for side, sx in (("right", -1), ("left", 1)):
            def arm(t, sx=sx):
                k = open_k(t)
                return [(WING_OPEN[0][0] - WING_FOLD[0][0]) * k, sx * (WING_OPEN[0][1] - WING_FOLD[0][1]) * k,
                        sx * ((WING_OPEN[0][2] - WING_FOLD[0][2]) * k - 26 * f(t))]

            def tip(t, sx=sx):
                k = open_k(t)
                return [(WING_OPEN[1][0] - WING_FOLD[1][0]) * k, sx * (WING_OPEN[1][1] - WING_FOLD[1][1]) * k,
                        sx * ((WING_OPEN[1][2] - WING_FOLD[1][2]) * k - 10 * f(t))]
            out[f"{side}_wing"] = rot(arm)
            out[f"{side}_wing_tip"] = rot(tip)
            out[f"{side}_membrane"] = {"scale": lambda t: [1, 1, WING_GATHER[0] + (1 - WING_GATHER[0]) * min(1.0, max(0.0, open_k(t)))]}
            out[f"{side}_membrane_tip"] = {"scale": lambda t: [1, 1, WING_GATHER[1] + (1 - WING_GATHER[1]) * min(1.0, max(0.0, open_k(t)))]}
        return out

    def legs(fn):
        """fn(t, phase) -> (swing of the whole leg, how far it is lifted 0..1)."""
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            for leg, ph, bend in (("front", 0.0, -1), ("hind", 0.5, 1)):
                p = ph + (0 if sx < 0 else 0.5)
                out[f"{side}_{leg}"] = rot(lambda t, p=p: [fn(t, p)[0], 0, 0])
                out[f"{side}_{leg}_low"] = rot(lambda t, p=p, bend=bend: [bend * 26 * fn(t, p)[1], 0, 0])
                # the foot stays level
                out[f"{side}_{leg}_foot"] = rot(lambda t, p=p, bend=bend: [-fn(t, p)[0] - bend * 26 * fn(t, p)[1] + 14 * fn(t, p)[1], 0, 0])
        return out

    folded = lambda t: 0.0  # noqa: E731
    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.5 * wave(t, L), 0]},
        "neck": rot(lambda t: [2.5 * wave(t, L), 0, 0]),
        "neck2": rot(lambda t: [-2 * wave(t, L, -0.1), 0, 0]),
        "head": rot(lambda t: [-1.5 * wave(t, L, -0.2), 4 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [2 + 2 * wave(t, L, 0.25), 0, 0]),
        "tail": rot(lambda t: [0, 9 * wave(t, L), 0]), "tail2": rot(lambda t: [0, 12 * wave(t, L, -0.15), 0]),
        "tail3": rot(lambda t: [0, 15 * wave(t, L, -0.3), 0]),
        **wings(lambda t: 0.03 + 0.03 * wave(t, L)),
    }, 0.1)
    L = 1.4
    A["walk"] = anim(L, True, {
        "body": {"position": lambda t: [0, -0.8 * abs(wave(t, L / 2)), 0], "rotation": lambda t: [0, 3 * wave(t, L), 2 * wave(t, L)]},
        "neck": rot(lambda t: [3 * wave(t, L / 2), -4 * wave(t, L), 0]),
        "neck2": rot(lambda t: [-2 * wave(t, L / 2), 0, 0]),
        "head": rot(lambda t: [-2, 3 * wave(t, L), 0]),
        "tail": rot(lambda t: [0, -12 * wave(t, L), 0]), "tail2": rot(lambda t: [0, -16 * wave(t, L, -0.15), 0]),
        "tail3": rot(lambda t: [0, -20 * wave(t, L, -0.3), 0]),
        **legs(lambda t, p: (22 * wave(t, L, p), max(0.0, wave(t, L, p + 0.25)))),
        **wings(lambda t: 0.04 + 0.03 * wave(t, L / 2)),
    })
    # intro (3.5 s): rears up with its neck arched over, wings thrown wide, roars down at whoever woke it, and comes
    # down with a thud at 3.0 s
    L = 3.5

    def rear(t):
        return ease_out_back(window(t, 0.4, 1.3), 1.3) * (1 - ease_in(window(t, 2.7, 3.0)))

    def spread(t):
        return ease_out_back(window(t, 0.7, 1.4), 1.6) * (1 - ease_in_out(window(t, 3.0, 3.5)))

    def roar(t):
        return window(t, 1.3, 1.5) * (1 - window(t, 2.4, 2.7))
    A["intro"] = anim(L, False, {
        "body": {"rotation": lambda t: [-36 * rear(t), 0, 0], "position": lambda t: [0, 4 * rear(t), 7 * rear(t)]},
        "neck": rot(lambda t: [22 * rear(t) + 2 * math.sin(t * 40) * roar(t), 0, 0]),
        "neck2": rot(lambda t: [14 * rear(t) - 5 * roar(t), 0, 0]),
        "head": rot(lambda t: [22 * rear(t) - 12 * roar(t), 3 * math.sin(t * 31) * roar(t), 0]),
        "jaw": rot(lambda t: [25 * roar(t), 0, 0]),
        "right_front": rot(lambda t: [-34 * rear(t), 0, -8 * rear(t)]), "left_front": rot(lambda t: [-34 * rear(t), 0, 8 * rear(t)]),
        "right_front_low": rot(lambda t: [-26 * rear(t), 0, 0]), "left_front_low": rot(lambda t: [-26 * rear(t), 0, 0]),
        "right_front_foot": rot(lambda t: [50 * rear(t), 0, 0]), "left_front_foot": rot(lambda t: [50 * rear(t), 0, 0]),
        "right_hind": rot(lambda t: [36 * rear(t), 0, 0]), "left_hind": rot(lambda t: [36 * rear(t), 0, 0]),
        "tail": rot(lambda t: [26 * rear(t), 0, 0]), "tail2": rot(lambda t: [8 * rear(t), 0, 0]),
        **wings(spread, lambda t: 0.45 * min(1.0, max(0.0, spread(t))) + 0.3 * math.sin(t * 9) * roar(t)),
    })
    # breath (2.6 s): the neck draws back, then thrusts out low, jaw wide, held from 0.8 to 2.2 s
    L = 2.6

    def draw(t):
        return ease_in_out(window(t, 0, 0.6)) * (1 - window(t, 0.6, 0.8))

    def blast(t):
        return ease_out_back(window(t, 0.6, 0.85), 1.2) * (1 - ease_in_out(window(t, 2.2, 2.6)))
    A["breath"] = anim(L, False, {
        "neck": rot(lambda t: [-16 * draw(t) + 34 * blast(t), 6 * math.sin(t * 3) * blast(t), 0]),
        "neck2": rot(lambda t: [10 * draw(t) - 12 * blast(t), 0, 0]),
        "head": rot(lambda t: [8 * draw(t) - 24 * blast(t), 0, 0]),
        "jaw": rot(lambda t: [8 * draw(t) + 23 * blast(t), 0, 0]),
        "body": rot(lambda t: [-5 * draw(t) + 5 * blast(t), 0, 0]),
        **wings(lambda t: 0.4 * blast(t)),
    })
    # tail sweep (1.5 s): the body coils, then the tail whips round at 0.7 s
    L = 1.5

    def coil(t):
        return ease_in_out(window(t, 0, 0.5)) * (1 - window(t, 0.55, 0.7))

    def whip(t):
        return ease_in(window(t, 0.55, 0.75)) * (1 - ease_in_out(window(t, 0.95, 1.5)))
    A["tail_sweep"] = anim(L, False, {
        "body": rot(lambda t: [0, 30 * coil(t) - 45 * whip(t), 0]),
        "tail": rot(lambda t: [6 * whip(t), 35 * coil(t) - 55 * whip(t), 0]),
        "tail2": rot(lambda t: [0, 30 * coil(t) - 50 * whip(t), 0]),
        "tail3": rot(lambda t: [0, 25 * coil(t) - 45 * whip(t), 0]),
        "neck": rot(lambda t: [0, -20 * coil(t) + 30 * whip(t), 0]),
        **wings(lambda t: 0.25 * whip(t)),
    })
    # wing buffet (1.6 s): the wings rise, then sweep forward and down at 0.75 s
    L = 1.6

    def rise(t):
        return ease_out_back(window(t, 0, 0.55)) * (1 - window(t, 0.6, 0.75))

    def beat(t):
        return ease_in(window(t, 0.6, 0.8)) * (1 - ease_in_out(window(t, 1.0, 1.6)))
    A["buffet"] = anim(L, False, {
        "body": {"rotation": lambda t: [-13 * rise(t) + 7 * beat(t), 0, 0], "position": lambda t: [0, 2.5 * rise(t), 0]},
        **wings(lambda t: max(rise(t), beat(t)), lambda t: 1.1 * rise(t) - 1.5 * beat(t)),
        "neck": rot(lambda t: [-8 * rise(t) + 10 * beat(t), 0, 0]),
        "head": rot(lambda t: [10 * rise(t) - 6 * beat(t), 0, 0]),
        "jaw": rot(lambda t: [20 * beat(t), 0, 0]),
        "right_hind": rot(lambda t: [13 * rise(t) - 7 * beat(t), 0, 0]), "left_hind": rot(lambda t: [13 * rise(t) - 7 * beat(t), 0, 0]),
        "right_front": rot(lambda t: [13 * rise(t) - 7 * beat(t), 0, 0]), "left_front": rot(lambda t: [13 * rise(t) - 7 * beat(t), 0, 0]),
    })
    # death (3.5 s): staggers, the legs buckle, it crashes to the floor on its side, wings limp
    L = 3.5

    def buckle(t):
        return ease_in(window(t, 0.8, 1.5))

    def roll(t):
        return ease_in_out(window(t, 1.3, 2.4))
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -7 * buckle(t) + 3 * roll(t), 0], "rotation": lambda t: [0, 0, 78 * roll(t)]},
        "body": rot(lambda t: [-18 * window(t, 0, 0.5) * (1 - buckle(t)), 0, 0]),
        "neck": rot(lambda t: [-14 * window(t, 0, 0.5) * (1 - buckle(t)) + 50 * roll(t), 0, -12 * roll(t)]),
        "neck2": rot(lambda t: [-16 * roll(t), 0, 0]),
        "head": rot(lambda t: [-20 * roll(t), 0, 0]),
        "jaw": rot(lambda t: [30 * window(t, 0, 0.5) * (1 - window(t, 1.0, 1.4)) + 12 * roll(t), 0, 0]),
        "tail": rot(lambda t: [10 * roll(t), 20 * roll(t), 0]), "tail2": rot(lambda t: [0, 20 * roll(t), 0]),
        **{f"{s}_{leg}": rot(lambda t, sgn=sgn: [sgn * 30 * buckle(t), 0, 0]) for s in ("right", "left") for leg, sgn in (("front", -1), ("hind", 1))},
        **{f"{s}_{leg}_low": rot(lambda t, sgn=sgn: [sgn * 34 * buckle(t), 0, 0]) for s in ("right", "left") for leg, sgn in (("front", -1), ("hind", 1))},
        **wings(lambda t: 0.45 * roll(t)),
    })
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- alpha (and gloomfang, its small kin)

def hound(name, uvs, size, scale, mane=True, sizes=None):
    """A lean hound. scale 1 = the alpha (about 2.4 blocks tall at the shoulder crest). Painted on the model by
    solid_skins.paint_hound, which goes by the tags and the design units used here."""
    s = scale
    UNITS[name] = s
    m = Model(uvs)
    c = lambda o, z, tile, **kw: m.c([v * s for v in o], [v * s for v in z], tile, **{k: ([v * s for v in val] if k == "pivot" else val) for k, val in kw.items()})  # noqa: E731
    P = lambda v: [x * s for x in v]  # noqa: E731

    def shard(x, y, z, w, h, rx, rz):
        """A crystal: a shaft and a narrower point, leaning back (and out)."""
        piv = [x, y + 0.8, z]
        return [c([x - w / 2, y, z - w / 2], [w, h, w], GLOW, pivot=piv, rotation=[rx, 0, rz], tag="crystal", part=0, skip=("down",)),
                c([x - w * 0.31, y + h, z - w * 0.31], [w * 0.62, h * 0.55, w * 0.62], GLOW, pivot=piv, rotation=[rx, 0, rz],
                  tag="crystal", part=1, skip=("down",))]

    m.bone("root", None, [0, 0, 0])
    body = [
        c([-6, 14.5, -12], [12, 11.5, 13], HIDE, tag="chest"),
        c([-5, 16, 1], [10, 9.5, 12], HIDE, tag="rear", skip=("north",)),
        # the mane: a collar of long fur over the neck and chest, ragged behind and below
        c([-6.8, 13.6, -13.5], [13.6, 13.2, 5], HIDE, tag="ruff", skip=("south", "down")),
    ]
    for sx in (-1, 1):
        # long belly fur: a thin ragged sheet hanging under each side
        body.append(c([sx * 5.2 - 0.3, 12.2, -11.5], [0.6, 4.2, 24], HIDE, tag="fringe", skip=("up", "down", "north", "south")))
    if mane:
        body += shard(0, 25.2, -10.4, 3.2, 6.2, -30, 0)
        body += shard(-3.3, 25.0, -8.0, 2.6, 5.0, -36, -24) + shard(3.3, 25.0, -8.0, 2.6, 5.0, -36, 24)
        body += shard(0, 25.2, -4.6, 2.6, 4.8, -40, 0)
        body += shard(-2.9, 25.0, -1.8, 2.0, 3.4, -44, -28) + shard(2.9, 25.0, -1.8, 2.0, 3.4, -44, 28)
        body += shard(0, 24.7, 2.2, 2.2, 3.6, -46, 0)
    m.bone("body", "root", P([0, 20, 0]), body)

    head = [
        c([-4.5, 20, -21], [9, 8, 9.5], HIDE, tag="skull"),
        c([-2.8, 20, -28], [5.6, 4.6, 7.5], HIDE, tag="snout", skip=("south",)),
        c([-1.6, 22.7, -28.8], [3.2, 2.2, 1.5], DARK, tag="nose", skip=("south", "down")),
        # fangs: a box whose sides are cut into teeth
        c([-2.95, 18.7, -27.9], [5.9, 1.5, 6.9], BONE, tag="teeth_up", skip=("south", "up", "down")),
    ]
    for sx in (-1, 1):
        # brows that slant down to the nose
        head.append(c([sx * 2.45 - 1.95, 26.5, -21.5], [3.9, 1.7, 3.2], DARK, pivot=[sx * 2.45, 27.3, -20], rotation=[0, 0, sx * -12], tag="brow"))
        # ears: three steps to a point, leaning back and out
        ex, piv, turn = sx * 3.1, [sx * 3.1, 27.5, -14.5], [-20, 0, sx * 16]
        head += [
            c([ex - 1.5, 27.3, -15.6], [3, 2.4, 2.2], DARK, pivot=piv, rotation=turn, tag="ear", part=0, skip=("down",)),
            c([ex - 1.1, 29.7, -15.4], [2.2, 2.0, 1.8], DARK, pivot=piv, rotation=turn, tag="ear", part=1, skip=("down",)),
            c([ex - 0.6, 31.7, -15.2], [1.2, 1.5, 1.4], DARK, pivot=piv, rotation=turn, tag="ear", part=2, skip=("down",)),
        ]
        # cheek ruff: a thin ragged sheet of fur standing off the jaw
        head.append(c([sx * 4.5 - 0.35, 19.4, -17.5], [0.7, 7.2, 6.5], HIDE, pivot=[sx * 4.5, 23, -17.5], rotation=[0, sx * 22, 0],
                      tag="cheek", skip=("south", "down")))
    m.bone("head", "body", P([0, 24, -12]), head)
    m.bone("jaw", "head", P([0, 20, -20]), [
        c([-2.45, 17.6, -27.4], [4.9, 2.2, 7.9], HIDE, tag="jaw", skip=("south",)),
        c([-2.2, 19.4, -27.2], [4.4, 1.3, 5.6], BONE, tag="teeth_low", skip=("south", "up", "down")),
    ])
    m.bone("tail", "body", P([0, 24, 13]), [
        c([-1.7, 22.3, 12.5], [3.4, 3.4, 5], HIDE, tag="tail", skip=("north",)),
        c([-2.3, 21.7, 17], [4.6, 4.6, 7.2], HIDE, tag="tail"),
        c([-1.6, 22.4, 24], [3.2, 3.2, 3.6], HIDE, tag="tail", skip=("north",)),
        # the brush under the tail
        c([-0.3, 19.4, 13.5], [0.6, 3.4, 13], HIDE, tag="brush", skip=("up", "down", "north", "south")),
    ], rotation=[30, 0, 0])

    def paw(hx, y, z):
        return [c([hx - 2.1, y, z - 3.6], [4.2, 2, 5], HIDE, tag="paw")] + [
            c([hx - 1.9 + i * 1.4, y, z - 4.5], [1, 1.1, 1.3], DARK, tag="claw", skip=("south",)) for i in range(3)]

    for side, sx in (("right", -1), ("left", 1)):
        hx = sx * 4.5
        # front: a straight leg under a heavy shoulder
        z = -8
        m.bone(f"{side}_front", "body", P([hx, 18, z]), [
            c([hx - 2.5, 15.2, z - 3.2], [5, 6, 6.4], HIDE, tag="shoulder"),
            c([hx - 2.2, 9, z - 2.3], [4.4, 7, 4.6], HIDE, tag="leg_up", skip=("up",)),
            # feathering down the back of the leg
            c([hx - 0.3, 8.6, z + 1.9], [0.6, 8, 2.6], HIDE, tag="feather", skip=("up", "down", "north", "south")),
        ])
        m.bone(f"{side}_front_low", f"{side}_front", P([hx, 10, z]),
               [c([hx - 1.6, 1.2, z - 1.6], [3.2, 9.3, 3.2], HIDE, tag="leg_low", skip=("up", "down"))] + paw(hx, 0, z))
        # hind: the thigh forward and the shank back, as a dog stands (the walk and the rest add to this)
        z = 9
        m.bone(f"{side}_hind", "body", P([hx, 18, z]), [
            c([hx - 2.7, 9.5, z - 3.6], [5.4, 10.5, 7.4], HIDE, tag="thigh"),
        ], rotation=[-20, 0, 0])
        m.bone(f"{side}_hind_low", f"{side}_hind", P([hx, 10, z]), [
            c([hx - 1.6, -0.9, z - 1.6], [3.2, 11.5, 3.2], HIDE, tag="leg_low", skip=("up", "down")),
        ], rotation=[45, 0, 0])
        m.bone(f"{side}_hind_paw", f"{side}_hind_low", P([hx, -0.3, z]), paw(hx, -1.5, z), rotation=[-25, 0, 0])
    return m


def hound_anims(name, run_period=0.8, intro=True, big=True):
    A = {}
    L = 2.5

    def legs(fn):
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            for leg, ph in (("front", 0.0), ("hind", 0.5)):
                p = ph + (0 if sx < 0 else 0.15)
                out[f"{side}_{leg}"] = rot(lambda t, p=p: [fn(t, p)[0], 0, 0])
                out[f"{side}_{leg}_low"] = rot(lambda t, p=p: [fn(t, p)[1], 0, 0])
        return out
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.3 * wave(t, L), 0]},
        "head": rot(lambda t: [3 * wave(t, L), 6 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [4 + 3 * wave(t, L / 2), 0, 0]),
        "tail": rot(lambda t: [0, 12 * wave(t, L), 0]),
    }, 0.1)
    L = run_period
    A["walk"] = anim(L, True, {
        "body": {"position": lambda t: [0, -0.8 * abs(wave(t, L / 2)), 0], "rotation": lambda t: [3 * wave(t, L), 0, 0]},
        "head": rot(lambda t: [-3 * wave(t, L), 0, 0]),
        "jaw": rot(lambda t: [8, 0, 0]),
        "tail": rot(lambda t: [-16 + 8 * wave(t, L), 6 * wave(t, L / 2), 0]),
        **legs(lambda t, p: (35 * wave(t, L, p), -25 * max(0, wave(t, L, p + 0.25)))),
    })
    # bite (0.8 s)
    L = 0.8

    def back(t):
        return ease_in_out(window(t, 0, 0.3)) * (1 - window(t, 0.32, 0.42))

    def snap(t):
        return ease_in(window(t, 0.32, 0.45)) * (1 - ease_in_out(window(t, 0.5, 0.8)))
    A["bite"] = anim(L, False, {
        "body": {"position": lambda t: [0, 0, 2 * back(t) - 5 * snap(t)], "rotation": lambda t: [-6 * back(t) + 8 * snap(t), 0, 0]},
        "head": rot(lambda t: [-20 * back(t) + 25 * snap(t), 0, 0]),
        "jaw": rot(lambda t: [35 * back(t) - 5 * snap(t), 0, 0]),
    })
    # pounce (1.2 s): crouch, leap stretched out, land at 0.85 s
    L = 1.2

    def crouch(t):
        return ease_in_out(window(t, 0, 0.3)) * (1 - window(t, 0.3, 0.4)) + ease_in(window(t, 0.8, 0.9)) * (1 - ease_in_out(window(t, 0.9, 1.2)))

    def air(t):
        return ease_out_back(window(t, 0.3, 0.5)) * (1 - ease_in(window(t, 0.7, 0.85)))
    A["pounce"] = anim(L, False, {
        "root": {"position": lambda t: [0, -2.5 * crouch(t), 0]},
        "body": rot(lambda t: [8 * crouch(t) - 12 * air(t), 0, 0]),
        "head": rot(lambda t: [-15 * air(t), 0, 0]),
        "jaw": rot(lambda t: [35 * air(t), 0, 0]),
        "right_front": rot(lambda t: [-70 * air(t) + 30 * crouch(t), 0, 0]), "left_front": rot(lambda t: [-70 * air(t) + 30 * crouch(t), 0, 0]),
        "right_hind": rot(lambda t: [60 * air(t) - 30 * crouch(t), 0, 0]), "left_hind": rot(lambda t: [60 * air(t) - 30 * crouch(t), 0, 0]),
        "tail": rot(lambda t: [-25 * air(t), 0, 0]),
    })
    # howl (2.4 s): sits back, muzzle to the ceiling
    L = 2.4

    def sit(t):
        return ease_out_back(window(t, 0, 0.6), 1.3) * (1 - ease_in_out(window(t, 1.9, 2.4)))
    howl = {
        "body": {"rotation": lambda t: [-35 * sit(t), 0, 0], "position": lambda t: [0, -3 * sit(t), 3 * sit(t)]},
        "head": rot(lambda t: [-45 * sit(t) + 2 * math.sin(t * 30) * window(t, 0.6, 0.8) * (1 - window(t, 1.6, 1.9)), 0, 0]),
        "jaw": rot(lambda t: [30 * window(t, 0.5, 0.7) * (1 - window(t, 1.6, 1.9)), 0, 0]),
        "right_hind": rot(lambda t: [-60 * sit(t), 0, 0]), "left_hind": rot(lambda t: [-60 * sit(t), 0, 0]),
        "right_hind_low": rot(lambda t: [70 * sit(t), 0, 0]), "left_hind_low": rot(lambda t: [70 * sit(t), 0, 0]),
        "right_front": rot(lambda t: [35 * sit(t), 0, 0]), "left_front": rot(lambda t: [35 * sit(t), 0, 0]),
        "tail": rot(lambda t: [10, 20 * math.sin(t * 6) * sit(t), 0]),
    }
    A["howl"] = anim(L, False, howl)
    if intro:
        # intro (3.5 s): lying low, rises with a snarl, then the howl
        L = 3.5

        def low(t):
            return 1 - ease_in_out(window(t, 0.3, 1.0))

        def sit2(t):
            return ease_out_back(window(t, 1.1, 1.7), 1.3) * (1 - ease_in_out(window(t, 3.0, 3.5)))
        A["intro"] = anim(L, False, {
            "root": {"position": lambda t: [0, -6 * low(t), 0]},
            "body": {"rotation": lambda t: [-35 * sit2(t), 0, 0], "position": lambda t: [0, -3 * sit2(t), 3 * sit2(t)]},
            "head": rot(lambda t: [10 * low(t) - 48 * sit2(t) + 2 * math.sin(t * 30) * window(t, 1.7, 1.9) * (1 - window(t, 2.6, 2.9)), 0, 0]),
            "jaw": rot(lambda t: [20 * window(t, 0.6, 0.9) * (1 - window(t, 1.0, 1.2)) + 32 * window(t, 1.6, 1.8) * (1 - window(t, 2.6, 2.9)), 0, 0]),
            **{f"{s}_{leg}": rot(lambda t, leg=leg: [(-80 if leg == "front" else 80) * low(t) + (35 if leg == "front" else -60) * sit2(t), 0, 0])
               for s in ("right", "left") for leg in ("front", "hind")},
            **{f"{s}_hind_low": rot(lambda t: [70 * sit2(t), 0, 0]) for s in ("right", "left")},
            "tail": rot(lambda t: [10, 18 * math.sin(t * 5) * sit2(t), 0]),
        })
    # death (big: 3.5 s, small: 1.0 s): stumbles and falls on its side
    L = 3.5 if big else 1.0
    k = L / 3.5

    def fall(t):
        return ease_in(window(t, 0.6 * k, 1.5 * k))
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 85 * fall(t)], "position": lambda t: [0, -2 * fall(t), 0]},
        "body": rot(lambda t: [-15 * window(t, 0, 0.5 * k) * (1 - fall(t)), 0, 0]),
        "head": rot(lambda t: [-25 * window(t, 0, 0.5 * k) * (1 - fall(t)) + 20 * fall(t), 0, 0]),
        "jaw": rot(lambda t: [25 * fall(t), 0, 0]),
        **{f"{s}_{leg}": rot(lambda t, sgn=sgn: [sgn * 25 * fall(t), 0, 0]) for s in ("right", "left") for leg, sgn in (("front", -1), ("hind", 1))},
        "tail": rot(lambda t: [-25 * fall(t), 0, 0]),
    }, 0.05 if big else 0.025)
    write_anims(name, A)


def alpha():
    name = "boss_alpha"
    uvs, size = save_textures(name, [(r, r) for r in RANKS], lambda rng, r: tiles_for(
        rng, rank_tint((112, 122, 140), r, 0.25), rank_tint((90, 96, 112), r), (170, 160, 150), rank_glow(r), spots=False), 700)
    m = hound(name, uvs, size, 1.25)
    write_geo(name, m.geo(name, size, 5, 4, 1.4))
    hound_anims(name)


# ============================================================================================= MONSTERS

def monster_textures(name, hide, plate, trim, seed, bone=(225, 214, 190), cloth=None):
    return save_textures(name, [(a, a, col) for a, col in AFFINITIES.items()], lambda rng, a, col: tiles_for(
        rng, mixc(hide, col, 0.18 if a != "plain" else 0), mixc(plate, col, 0.1 if a != "plain" else 0), trim, col,
        bone=bone, cloth=cloth and mixc(cloth, col, 0.15 if a != "plain" else 0)), seed)


def gloomfang():
    name = "gloomfang"
    uvs, size = monster_textures(name, (60, 58, 66), (84, 82, 94), (150, 140, 130), 800)
    m = hound(name, uvs, size, 0.55, mane=True)
    write_geo(name, m.geo(name, size, 2, 2, 0.6))
    hound_anims(name, 0.55, intro=False, big=False)


def broodling():
    name = "broodling"
    uvs, size = monster_textures(name, (46, 36, 50), (74, 60, 80), (130, 110, 96), 900, cloth=(92, 44, 70))
    m = Model(uvs)
    c = m.c
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 5, 0], [c([-4, 3, -5], [8, 5, 7], HIDE), c([-3.5, 7.5, -4.5], [7, 1.2, 6], PLATE)])
    m.bone("head", "body", [0, 5, -5], [
        c([-3, 2.5, -9], [6, 4.5, 4], DARK),
        c([-2.2, 5, -9.3], [1.3, 1.3, 0.5], EYE), c([0.9, 5, -9.3], [1.3, 1.3, 0.5], EYE),
        c([-2, 0.5, -9.5], [1.2, 2.5, 1.2], BONE), c([0.8, 0.5, -9.5], [1.2, 2.5, 1.2], BONE),
    ])
    m.bone("abdomen", "body", [0, 6, 2], [c([-5, 3, 2], [10, 8, 9], CLOTH), c([-2, 8, 6], [3, 3, 3], GLOW), c([1.5, 6, 9.5], [2, 2, 2], GLOW)])
    for i, (z, yaw) in enumerate(((-4, -30), (-1.5, -10), (1, 10), (3.5, 30))):
        for side, sx in (("r", -1), ("l", 1)):
            m.bone(f"leg{i}{side}", "body", [sx * 3.5, 5, z], [
                c([sx * 3.5 + (0 if sx > 0 else -7), 4.3, z - 0.6], [7, 1.2, 1.2], HIDE),
                c([sx * 10.5 - 0.5, -0.5, z - 0.5], [1, 5.5, 1], DARK),
            ], rotation=[0, -sx * yaw, sx * 15])
    write_geo(name, m.geo(name, size, 2, 1.2, 0.4))
    A = {}

    def legs(fn):
        return {f"leg{i}{s}": rot(lambda t, i=i, sx=sx: fn(t, i, sx)) for i in range(4) for s, sx in (("r", -1), ("l", 1))}
    L = 2.0
    A["idle"] = anim(L, True, {"abdomen": rot(lambda t: [3 * wave(t, L), 0, 0]), "head": rot(lambda t: [0, 8 * wave(t, L), 0])}, 0.1)
    L = 0.4
    A["walk"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.3 * abs(wave(t, L / 2)), 0]},
        **legs(lambda t, i, sx: [0, -sx * 25 * wave(t, L, (i + (sx > 0)) % 2 * 0.5), sx * 10 * max(0, wave(t, L, (i + (sx > 0)) % 2 * 0.5 + 0.25))]),
    }, 0.025)
    L = 0.6
    A["bite"] = anim(L, False, {
        "body": {"position": lambda t: [0, 1.5 * math.sin(math.pi * window(t, 0.1, 0.45)), -3 * math.sin(math.pi * window(t, 0.15, 0.5))],
                 "rotation": lambda t: [-15 * math.sin(math.pi * window(t, 0.05, 0.3)) + 10 * math.sin(math.pi * window(t, 0.3, 0.55)), 0, 0]},
    }, 0.025)
    L = 1.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 180 * ease_in_out(window(t, 0, 0.4))], "position": lambda t: [0, 7 * ease_in_out(window(t, 0, 0.4)), 0]},
        **legs(lambda t, i, sx: [0, 0, sx * (-40 * ease_in_out(window(t, 0.3, 0.9))) + 6 * math.sin(t * 40) * (1 - window(t, 0.4, 1.0))]),
    }, 0.025)
    write_anims(name, A)


def shardback():
    name = "shardback"
    uvs, size = monster_textures(name, (70, 66, 60), (96, 90, 82), (150, 140, 120), 1000)
    m = Model(uvs)
    c = m.c
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 9, 0], [
        c([-8, 4, -9], [16, 10, 18], HIDE),
        c([-8.5, 12, -8], [17, 3, 16], PLATE),
        c([-7, 15, -6], [14, 2, 12], DARK),
        c([-2, 16, -5], [4, 8, 4], GLOW, pivot=[0, 17, -3], rotation=[-15, 0, 10]),
        c([-6, 15, 0], [3, 6, 3], GLOW, pivot=[-4.5, 16, 1.5], rotation=[10, 0, 25]),
        c([3, 15, 1], [3, 7, 3], GLOW, pivot=[4.5, 16, 2.5], rotation=[10, 0, -25]),
        c([-1.5, 15.5, 4], [3, 5, 3], GLOW, pivot=[0, 16, 5.5], rotation=[-25, 0, 0]),
    ])
    m.bone("head", "body", [0, 9, -9], [
        c([-5, 5, -15], [10, 8, 7], PLATE),
        c([-5.5, 11, -15.5], [11, 3, 8], DARK),
        c([-3.5, 9, -15.6], [2, 1.5, 0.6], EYE), c([1.5, 9, -15.6], [2, 1.5, 0.6], EYE),
        c([-1.5, 10, -18], [3, 3, 4], BONE, pivot=[0, 11, -16], rotation=[20, 0, 0]),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        for leg, z in (("front", -6), ("mid", 0), ("hind", 6)):
            m.bone(f"{side}_{leg}", "body", [sx * 8, 7, z], [
                c([sx * 8 + (0 if sx > 0 else -5), 5.5, z - 1.5], [5, 3, 3], DARK),
                c([sx * 12.5 - 1.5, 0, z - 1.5], [3, 6, 3], PLATE),
            ])
    write_geo(name, m.geo(name, size, 3, 2, 0.6))
    A = {}

    def legs(fn):
        out = {}
        for side, sx in (("right", -1), ("left", 1)):
            for j, leg in enumerate(("front", "mid", "hind")):
                out[f"{side}_{leg}"] = rot(lambda t, j=j, sx=sx: fn(t, j, sx))
        return out
    L = 3.0
    A["idle"] = anim(L, True, {"body": {"position": lambda t: [0, 0.3 * wave(t, L), 0]}, "head": rot(lambda t: [3 * wave(t, L), 5 * wave(t, L * 2), 0])}, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {
        "body": rot(lambda t: [0, 3 * wave(t, L), 2 * wave(t, L / 2)]),
        **legs(lambda t, j, sx: [0, -sx * 20 * wave(t, L, ((j + (sx > 0)) % 2) * 0.5), sx * 10 * max(0, wave(t, L, ((j + (sx > 0)) % 2) * 0.5 + 0.25))]),
    })
    L = 1.0
    A["bite"] = anim(L, False, {
        "head": rot(lambda t: [-30 * ease_in_out(window(t, 0, 0.4)) * (1 - window(t, 0.4, 0.5)) + 25 * ease_in(window(t, 0.4, 0.5)) * (1 - window(t, 0.6, 1.0)), 0, 0]),
        "body": {"position": lambda t: [0, 0, -4 * ease_in(window(t, 0.4, 0.5)) * (1 - window(t, 0.6, 1.0))]},
    })
    L = 1.5
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -4 * ease_in(window(t, 0.3, 0.9)), 0]},
        "body": rot(lambda t: [6 * math.sin(t * 40) * (1 - window(t, 0, 0.6)), 0, 12 * ease_in(window(t, 0.4, 1.0))]),
        "head": rot(lambda t: [30 * ease_in(window(t, 0.5, 1.2)), 0, 0]),
        **legs(lambda t, j, sx: [0, 0, sx * 50 * ease_in_out(window(t, 0.3, 1.0))]),
    })
    write_anims(name, A)


def humanoid(m, c, tiles, hood=True):
    """A plain humanoid frame (24 px hip, about two blocks tall). Returns nothing; adds bones to m."""
    m.bone("root", None, [0, 0, 0])
    m.bone("body", "root", [0, 12, 0], [c([-4, 12, -2.5], [8, 12, 5], tiles["chest"]), c([-4.3, 12, -2.8], [8.6, 2, 5.6], TRIM)])
    head = [c([-4, 24, -4], [8, 8, 8], tiles["head"])]
    if hood:
        head += [c([-4.5, 24.5, -4.5], [9, 8.5, 9], CLOTH), c([-3.5, 25, -4.7], [7, 6, 0.5], DARK),
                 c([-2.5, 28, -4.9], [1.6, 1, 0.5], EYE), c([0.9, 28, -4.9], [1.6, 1, 0.5], EYE)]
    m.bone("head", "body", [0, 24, 0], head)
    for side, sx in (("right", -1), ("left", 1)):
        m.bone(f"{side}_arm", "body", [sx * 5, 22, 0], [c([sx * 5 - 2 + sx * 1, 12, -2], [4, 12, 4], tiles["arm"])])
        m.bone(f"{side}_leg", "root", [sx * 2, 12, 0], [c([sx * 2 - 2, 0, -2], [4, 12, 4], tiles["leg"])])


def biped_anims(name, attack, extra_attack=None):
    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "body": rot(lambda t: [1.5 * wave(t, L), 0, 0]),
        "right_arm": rot(lambda t: [-4 + 3 * wave(t, L), 0, 4]), "left_arm": rot(lambda t: [-4 - 3 * wave(t, L), 0, -4]),
        "head": rot(lambda t: [0, 6 * wave(t, L * 2), 0]),
    }, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {
        "right_leg": rot(lambda t: [-30 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [30 * wave(t, L), 0, 0]),
        "right_arm": rot(lambda t: [20 * wave(t, L), 0, 4]), "left_arm": rot(lambda t: [-20 * wave(t, L), 0, -4]),
        "body": {"position": lambda t: [0, -0.4 * abs(wave(t, L)), 0]},
    })
    A.update(attack)
    if extra_attack:
        A.update(extra_attack)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [-85 * ease_in(window(t, 0.2, 0.8)), 0, 0], "position": lambda t: [0, 0, 2 * ease_in(window(t, 0.2, 0.8))]},
        "right_arm": rot(lambda t: [-30 * window(t, 0, 0.3), 0, 30 * window(t, 0.3, 0.8)]),
        "left_arm": rot(lambda t: [-30 * window(t, 0, 0.3), 0, -30 * window(t, 0.3, 0.8)]),
        "head": rot(lambda t: [-20 * window(t, 0, 0.3) + 25 * window(t, 0.7, 1.0), 0, 0]),
    }, 0.025)
    write_anims(name, A)


def hollow_acolyte():
    name = "hollow_acolyte"
    uvs, size = monster_textures(name, (60, 52, 72), (80, 72, 96), (190, 165, 110), 1100, bone=(232, 226, 210), cloth=(48, 42, 64))
    m = Model(uvs)
    c = m.c
    humanoid(m, c, {"chest": CLOTH, "head": BONE, "arm": CLOTH, "leg": CLOTH})
    m.bones[1]["cubes"].append(c([-4.5, 4, -3], [9, 9, 6], CLOTH))  # robe skirt
    m.bone("orb", "right_arm", [-6, 10, -2], [c([-7.2, 8.8, -3.2], [2.4, 2.4, 2.4], GLOW)])
    write_geo(name, m.geo(name, size, 2, 3, 1))
    L = 1.0
    cast = {"cast": anim(L, False, {
        "right_arm": rot(lambda t: [70 * ease_in_out(window(t, 0, 0.4)) * (1 - window(t, 0.4, 0.5)) - 100 * ease_in(window(t, 0.4, 0.55)) * (1 - ease_in_out(window(t, 0.7, 1.0))), 0, 0]),
        "left_arm": rot(lambda t: [-60 * math.sin(math.pi * window(t, 0, 1.0)), 0, -20 * math.sin(math.pi * window(t, 0, 1.0))]),
        "body": rot(lambda t: [-6 * window(t, 0, 0.4) * (1 - window(t, 0.4, 0.55)) + 8 * window(t, 0.45, 0.55) * (1 - window(t, 0.7, 1.0)), 0, 0]),
        "orb": {"scale": lambda t: [1 + 1.2 * math.sin(math.pi * window(t, 0.1, 0.55))] * 3},
    })}
    biped_anims(name, cast)


def ashen_sentinel():
    name = "ashen_sentinel"
    uvs, size = monster_textures(name, (52, 52, 58), (100, 100, 108), (160, 140, 100), 1200, cloth=(60, 30, 30))
    m = Model(uvs)
    c = m.c
    humanoid(m, c, {"chest": PLATE, "head": PLATE, "arm": PLATE, "leg": DARK}, hood=False)
    m.bones[2]["cubes"] += [c([-4.4, 24, -4.4], [8.8, 9, 8.8], PLATE), c([-3, 28, -4.6], [6, 1, 0.5], GLOW),
                           c([-0.5, 33, -3], [1, 4, 7], CLOTH)]
    m.bones[1]["cubes"] += [c([-5.5, 20, -3], [11, 4, 6], PLATE)]
    m.bone("blade", "right_arm", [-6, 13, 0], [c([-6.6, 12, -16], [1.2, 2.4, 16], PLATE), c([-7, 11.5, -2], [2, 3.4, 2], TRIM),
                                              c([-6.4, 12.9, -15], [0.8, 0.6, 13], GLOW)])
    m.bone("shield", "left_arm", [7, 17, -3], [c([5.5, 10, -5], [1.5, 13, 9], PLATE), c([5, 15, -1.5], [1, 3, 2], GLOW),
                                               c([5.2, 10.5, -5.3], [1.6, 12, 0.6], TRIM)], rotation=[0, 0, 0])
    write_geo(name, m.geo(name, size, 2, 3, 1))
    L = 0.9
    swing = {"attack": anim(L, False, {
        "right_arm": rot(lambda t: [70 * ease_in_out(window(t, 0, 0.35)) * (1 - window(t, 0.35, 0.45)) - 60 * ease_in(window(t, 0.35, 0.5)) * (1 - ease_in_out(window(t, 0.6, 0.9))), 30 * window(t, 0, 0.35) * (1 - window(t, 0.35, 0.5)), 0]),
        "body": rot(lambda t: [0, 20 * window(t, 0, 0.35) * (1 - window(t, 0.35, 0.5)) - 25 * window(t, 0.35, 0.5) * (1 - window(t, 0.6, 0.9)), 0]),
    }), "block": anim(1.0, False, {
        "left_arm": rot(lambda t: [-70 * math.sin(math.pi * window(t, 0, 1.0)), 40 * math.sin(math.pi * window(t, 0, 1.0)), 0]),
    })}
    biped_anims(name, swing)


def emberwisp():
    name = "emberwisp"
    uvs, size = monster_textures(name, (70, 60, 56), (110, 100, 92), (200, 170, 120), 1300)
    m = Model(uvs)
    c = m.c
    m.bone("root", None, [0, 0, 0])
    m.bone("head", "root", [0, 6, 0], [
        c([-3.5, 2, -3.5], [7, 6, 7], BONE),
        c([-2.5, 0.5, -3.6], [5, 2, 5], BONE),
        c([-2.6, 4.5, -3.7], [2, 1.6, 0.5], EYE), c([0.6, 4.5, -3.7], [2, 1.6, 0.5], EYE),
        c([-0.5, 2.8, -3.7], [1, 1, 0.5], DARK),
        c([-3, 7.5, -3], [6, 3, 6], GLOW),
        c([-2, 10, -2], [4, 2.5, 4], GLOW, pivot=[0, 10, 0], rotation=[0, 45, 0]),
    ])
    m.bone("embers", "root", [0, 6, 0], [c([4 * math.cos(a), 4 + 2 * math.sin(a * 2), 4 * math.sin(a)] , [1.2, 1.2, 1.2], GLOW)
                                         for a in (0, 2.1, 4.2)])
    write_geo(name, m.geo(name, size, 1.5, 1.5, 0.4))
    A = {}
    L = 2.0
    A["idle"] = anim(L, True, {"root": {"position": lambda t: [0, 1.0 * wave(t, L), 0]}, "embers": rot(lambda t: [0, 180 * t, 0]),
                               "head": rot(lambda t: [4 * wave(t, L), 0, 3 * wave(t, L / 2)])}, 0.1)
    A["walk"] = anim(L, True, {"root": {"position": lambda t: [0, 0.6 * wave(t, L), 0]}, "embers": rot(lambda t: [0, 360 * t, 0]),
                               "head": rot(lambda t: [15, 0, 0])}, 0.1)
    L = 1.0
    A["attack"] = anim(L, False, {"head": {"scale": lambda t: [1 + 0.35 * window(t, 0, 1.0) + 0.08 * math.sin(t * 60)] * 3},
                                  "embers": {"rotation": lambda t: [0, 900 * t, 0], "scale": lambda t: [1 + window(t, 0, 1.0)] * 3}}, 0.025)
    L = 0.5
    A["death"] = anim(L, "hold", {"head": {"scale": lambda t: [max(0.01, 1 + 0.6 * window(t, 0, 0.2) - 1.6 * window(t, 0.2, 0.5))] * 3},
                                  "embers": {"scale": lambda t: [max(0.01, 1 + 2 * window(t, 0, 0.5))] * 3}}, 0.025)
    write_anims(name, A)


def telegraphs_and_eggs():
    """The telegraph textures (a soft fill and a bright rim) and the spawn egg item models."""
    import json
    from PIL import Image
    eff = os.path.join(TEX, "effect")
    os.makedirs(eff, exist_ok=True)
    n = 64
    fill = Image.new("RGBA", (n, n), (255, 255, 255, 255))
    fill.save(os.path.join(eff, "telegraph_fill.png"))
    edge = Image.new("RGBA", (n, n))
    px = edge.load()
    for y in range(n):
        for x in range(n):
            d = min(x, y, n - 1 - x, n - 1 - y)
            v = 255 if d < 3 else (150 if d < 5 else 60)
            px[x, y] = (v, v, v, 255)
    edge.save(os.path.join(eff, "telegraph_edge.png"))
    for t in ("telegraph_fill", "telegraph_edge"):
        with open(os.path.join(eff, t + ".png.mcmeta"), "w") as f:
            f.write('{"texture": {"blur": true, "clamp": true}}')
    items = os.path.join(TEX, "..", "models", "item")
    os.makedirs(items, exist_ok=True)
    for k in ("gloomfang", "broodling", "shardback", "hollow_acolyte", "ashen_sentinel", "emberwisp"):
        with open(os.path.join(items, k + "_spawn_egg.json"), "w") as f:
            json.dump({"parent": "minecraft:item/template_spawn_egg"}, f, indent=2)


def main():
    for d in ("entity",):
        os.makedirs(os.path.join(GEO, d), exist_ok=True)
        os.makedirs(os.path.join(ANIM, d), exist_ok=True)
        os.makedirs(os.path.join(TEX, d), exist_ok=True)
    makers = [broodmother, knight, magus, wyrm, alpha, gloomfang, broodling, shardback, hollow_acolyte, ashen_sentinel, emberwisp,
              telegraphs_and_eggs]
    only = sys.argv[1:]     # e.g. "alpha gloomfang" while working on the hounds
    for make in makers:
        if not only or make.__name__ in only:
            make()
    print("enemy assets written")


if __name__ == "__main__":
    main()
