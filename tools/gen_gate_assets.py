#!/usr/bin/env python3
"""
Generates the gate module's models, animations and textures (no hand-drawn files needed):

- the gate: a ring of floating rock shards with rank crystals (GeckoLib block model + idle/open/close animations)
- the gate boss: a hulking armoured brute (GeckoLib entity model + idle/walk/slam/swipe/roar/death animations)
- the reward cache: the chest that drops after a boss (GeckoLib block model + open animation)
- per-rank textures and glow masks for all three (glow masks light up crystals, eyes and veins)
- light textures for the world effects: the gate's vortex and a soft beam

Run from the repo root:  python3 tools/gen_gate_assets.py
Needs: pillow, numpy.
"""
import json
import math
import os
import random

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
GEO = os.path.join(ASSETS, "geo")
ANIM = os.path.join(ASSETS, "animations")
TEX = os.path.join(ASSETS, "textures")
FX_OUT = os.path.join(TEX, "effect")

RANKS = ["e", "d", "c", "b", "a", "s"]
# per rank: rock, rock vein / crystal colour, boss hide, boss armour, boss trim
PALETTES = {
    "e": dict(rock=(78, 82, 90), glow=(170, 190, 210), hide=(70, 74, 70), armor=(96, 100, 108), trim=(140, 140, 130)),
    "d": dict(rock=(66, 82, 64), glow=(130, 235, 120), hide=(58, 72, 50), armor=(84, 98, 78), trim=(150, 170, 110)),
    "c": dict(rock=(60, 70, 96), glow=(90, 175, 255), hide=(46, 54, 78), armor=(70, 86, 120), trim=(140, 180, 230)),
    "b": dict(rock=(70, 58, 96), glow=(190, 130, 255), hide=(52, 40, 72), armor=(84, 66, 120), trim=(190, 160, 240)),
    "a": dict(rock=(92, 70, 48), glow=(255, 190, 80), hide=(72, 50, 36), armor=(112, 84, 56), trim=(230, 190, 110)),
    "s": dict(rock=(64, 30, 34), glow=(255, 80, 70), hide=(40, 18, 22), armor=(60, 28, 32), trim=(220, 70, 60)),
}

T = 16  # tile size in the texture atlases


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, separators=(",", ":"))


def clamp(v):
    return max(0, min(255, int(round(v))))


def noise(rng, size, octaves=3):
    """Smooth value noise in 0..1, size x size."""
    out = np.zeros((size, size))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        cells = 2 ** (o + 1)
        grid = rng.random((cells + 1, cells + 1))
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((size, size), Image.BICUBIC)
        out += np.asarray(img) / 255.0 * amp
        total += amp
        amp *= 0.5
    return out / total


# --------------------------------------------------------------------------------------------- tiles
# Each painter returns (rgba tile, glow tile) as numpy arrays T x T x 4.

def tile_rock(rng, base, vein, veins=True):
    n = noise(rng, T, 3)
    rgb = np.zeros((T, T, 4))
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.7 + 0.6 * n)
    rgb[:, :, 3] = 255
    glow = np.zeros((T, T, 4))
    # cracks: a few random walks
    for _ in range(2 if veins else 1):
        x, y = rng.integers(0, T), 0
        while y < T:
            if veins:
                rgb[y, x, :3] = vein
                glow[y, x] = (*vein, 255)
            else:
                rgb[y, x, :3] = [v * 0.45 for v in base]
            x = int(np.clip(x + rng.integers(-1, 2), 0, T - 1))
            y += 1
    # light top edge, dark bottom edge
    rgb[0, :, :3] = np.minimum(255, rgb[0, :, :3] * 1.25)
    rgb[T - 1, :, :3] *= 0.65
    return rgb, glow


def tile_crystal(rng, color):
    rgb = np.zeros((T, T, 4))
    glow = np.zeros((T, T, 4))
    for y in range(T):
        for x in range(T):
            k = 0.55 + 0.45 * (1 - y / T)
            facet = 1.15 if (x + y // 3) % 7 < 2 else 1.0
            c = [min(255, v * k * facet + 60 * (1 - y / T)) for v in color]
            rgb[y, x] = (*c, 255)
            glow[y, x] = (*c, 255)
    return rgb, glow


def tile_hide(rng, base):
    n = noise(rng, T, 4)
    rgb = np.zeros((T, T, 4))
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.75 + 0.45 * n)
    # fine scales
    for y in range(0, T, 3):
        for x in range((y // 3) % 2, T, 3):
            rgb[y, x, :3] *= 0.7
    rgb[:, :, 3] = 255
    return rgb, np.zeros((T, T, 4))


def tile_armor(rng, base, trim):
    n = noise(rng, T, 2)
    rgb = np.zeros((T, T, 4))
    for c in range(3):
        rgb[:, :, c] = base[c] * (0.85 + 0.3 * n)
    rgb[:, :, 3] = 255
    rgb[0:2, :, :3] = [min(255, v * 1.35) for v in base]
    rgb[T - 2:T, :, :3] = trim
    rgb[T - 1, :, :3] = [v * 0.6 for v in trim]
    for x in (2, T - 3):
        rgb[4, x, :3] = trim
        rgb[T - 5, x, :3] = trim
    return rgb, np.zeros((T, T, 4))


def tile_trim(rng, trim):
    rgb = np.zeros((T, T, 4))
    for y in range(T):
        k = 0.7 + 0.5 * math.sin(y / T * math.pi)
        rgb[y, :, :3] = [min(255, v * k) for v in trim]
    rgb[:, :, 3] = 255
    rgb[::4, ::4, :3] *= 0.7
    return rgb, np.zeros((T, T, 4))


def tile_eye(rng, color):
    rgb = np.zeros((T, T, 4))
    rgb[:, :] = (255, 255, 255, 255)
    for y in range(T):
        for x in range(T):
            d = math.hypot(x - T / 2 + 0.5, y - T / 2 + 0.5) / (T / 2)
            c = [min(255, v + (255 - v) * max(0, 1 - d * 1.4)) for v in color]
            rgb[y, x] = (*c, 255)
    return rgb, rgb.copy()


def tile_horn(rng):
    rgb = np.zeros((T, T, 4))
    for y in range(T):
        k = y / T
        rgb[y, :] = (clamp(225 - 120 * k), clamp(212 - 120 * k), clamp(190 - 115 * k), 255)
    rgb[:, ::5, :3] *= 0.85
    return rgb, np.zeros((T, T, 4))


def tile_metal_dark(rng):
    n = noise(rng, T, 3)
    rgb = np.zeros((T, T, 4))
    for c, v in enumerate((34, 40, 58)):
        rgb[:, :, c] = v * (0.8 + 0.4 * n)
    rgb[:, :, 3] = 255
    rgb[0, :, :3] = (70, 86, 120)
    return rgb, np.zeros((T, T, 4))


def tile_gold(rng):
    rgb = np.zeros((T, T, 4))
    for y in range(T):
        k = 0.75 + 0.35 * math.sin(y / T * math.pi)
        rgb[y, :, :3] = (min(255, 235 * k), min(255, 190 * k), min(255, 90 * k))
    rgb[:, :, 3] = 255
    return rgb, np.zeros((T, T, 4))


def tile_system_gem(rng):
    return tile_crystal(rng, (95, 208, 255))


def atlas(tiles, cols=4):
    """Packs (rgb, glow) tiles into one texture and one glow mask; returns them and the tile -> (u, v) map."""
    rows = (len(tiles) + cols - 1) // cols
    size = max(cols, rows) * T
    img = np.zeros((size, size, 4))
    glow = np.zeros((size, size, 4))
    uv = []
    for i, (rgb, gl) in enumerate(tiles):
        u, v = (i % cols) * T, (i // cols) * T
        img[v:v + T, u:u + T] = rgb
        glow[v:v + T, u:u + T] = gl
        uv.append((u, v))
    return Image.fromarray(img.astype(np.uint8)), Image.fromarray(glow.astype(np.uint8)), uv, size


# --------------------------------------------------------------------------------------------- geometry helpers

def face_uv(u, v):
    """All six faces of a cube show the same tile."""
    f = {"uv": [u, v], "uv_size": [T, T]}
    return {k: dict(f) for k in ("north", "east", "south", "west", "up", "down")}


def cube(origin, size, tile, uvs, pivot=None, rotation=None, inflate=None):
    c = {"origin": [round(x, 3) for x in origin], "size": [round(x, 3) for x in size], "uv": face_uv(*uvs[tile])}
    if pivot is not None:
        c["pivot"] = [round(x, 3) for x in pivot]
    if rotation is not None:
        c["rotation"] = [round(x, 3) for x in rotation]
    if inflate is not None:
        c["inflate"] = inflate
    return c


def geometry(identifier, size, bones, bounds_w, bounds_h, offset_y):
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": identifier,
                "texture_width": size,
                "texture_height": size,
                "visible_bounds_width": bounds_w,
                "visible_bounds_height": bounds_h,
                "visible_bounds_offset": [0, offset_y, 0],
            },
            "bones": bones,
        }],
    }


def keyframes(fn, length, step=0.1):
    """Samples fn(t) -> [x, y, z] into a keyframe dict (GeckoLib interpolates linearly between them)."""
    out = {}
    n = int(round(length / step))
    for i in range(n + 1):
        t = round(i * step, 3)
        out[f"{t:.2f}"] = [round(v, 3) for v in fn(min(t, length))]
    return out


def ease_out_back(t, s=1.9):
    t = max(0.0, min(1.0, t)) - 1
    return t * t * ((s + 1) * t + s) + 1


def ease_in_out(t):
    t = max(0.0, min(1.0, t))
    return 3 * t * t - 2 * t * t * t


def ease_in(t):
    t = max(0.0, min(1.0, t))
    return t * t * t


def window(t, a, b):
    return max(0.0, min(1.0, (t - a) / (b - a)))


# --------------------------------------------------------------------------------------------- the gate

GATE_RX, GATE_RY, GATE_CY = 25.0, 33.0, 40.0  # ellipse radii and centre height (pixels)
GATE_SHARDS = 22
GATE_CRYSTALS = 6


def gate_layout():
    rng = random.Random(7)
    shards = []
    for i in range(GATE_SHARDS):
        a = (i + 0.3 * rng.random()) / GATE_SHARDS * 2 * math.pi
        # outward normal of the ellipse at angle a
        x, y = GATE_RX * math.cos(a), GATE_CY + GATE_RY * math.sin(a)
        nx, ny = math.cos(a) / GATE_RX, math.sin(a) / GATE_RY
        nl = math.hypot(nx, ny)
        nx, ny = nx / nl, ny / nl
        length = 9 + rng.random() * 6
        thick = 5 + rng.random() * 4
        depth = 5 + rng.random() * 5
        push = thick / 2 + 0.5 + rng.random() * 2
        shards.append(dict(i=i, angle=a, x=x + nx * push, y=y + ny * push, nx=nx, ny=ny,
                           length=length, thick=thick, depth=depth,
                           tilt=(rng.uniform(-14, 14), rng.uniform(-18, 18)),
                           tangent=math.degrees(math.atan2(-nx, ny)), phase=rng.random() * 2 * math.pi))
    crystals = []
    for i in range(GATE_CRYSTALS):
        a = (i + 0.5) / GATE_CRYSTALS * 2 * math.pi + 0.2
        x, y = GATE_RX * math.cos(a), GATE_CY + GATE_RY * math.sin(a)
        nx, ny = math.cos(a) / GATE_RX, math.sin(a) / GATE_RY
        nl = math.hypot(nx, ny)
        nx, ny = nx / nl, ny / nl
        crystals.append(dict(i=i, x=x + nx * 10, y=y + ny * 10, nx=nx, ny=ny, len=9 + 4 * (i % 2),
                             radial=math.degrees(math.atan2(ny, nx)) - 90, phase=i * 1.3))
    return shards, crystals


def gate_model(uvs, size):
    shards, crystals = gate_layout()
    bones = [{"name": "root", "pivot": [0, 0, 0]}, {"name": "ring", "parent": "root", "pivot": [0, GATE_CY, 0]}]
    for s in shards:
        tile = 0 if s["i"] % 3 else 1
        p = [s["x"], s["y"], 0]
        bones.append({
            "name": f"shard{s['i']}", "parent": "ring", "pivot": p,
            "rotation": [s["tilt"][0], s["tilt"][1], s["tangent"]],
            "cubes": [
                cube([p[0] - s["length"] / 2, p[1] - s["thick"] / 2, -s["depth"] / 2], [s["length"], s["thick"], s["depth"]], tile, uvs),
                # a smaller chip stuck to it, so the edge reads as broken rock instead of a box
                cube([p[0] - s["length"] / 4, p[1] + s["thick"] / 2 - 1, -s["depth"] / 3], [s["length"] / 2, 2.5, s["depth"] * 0.6], 1, uvs,
                     pivot=p, rotation=[0, 0, 9]),
            ],
        })
    for c in crystals:
        p = [c["x"], c["y"], 0]
        bones.append({
            "name": f"crystal{c['i']}", "parent": "ring", "pivot": p,
            "rotation": [0, 0, c["radial"]],
            "cubes": [
                cube([p[0] - 1.5, p[1] - c["len"] / 2, -1.5], [3, c["len"], 3], 2, uvs, pivot=p, rotation=[0, 45, 0]),
                cube([p[0] - 0.8, p[1] + c["len"] / 2 - 0.5, -0.8], [1.6, 3, 1.6], 2, uvs, pivot=p, rotation=[0, 45, 0]),
            ],
        })
    # rubble the gate stands on
    rng = random.Random(3)
    rubble = []
    for i in range(7):
        w = 6 + rng.random() * 7
        h = 3 + rng.random() * 4
        d = 6 + rng.random() * 8
        x = -22 + i * 7 + rng.uniform(-2, 2)
        rubble.append(cube([x - w / 2, 0, -d / 2], [w, h, d], 3 if i % 2 else 0, uvs,
                           pivot=[x, 0, 0], rotation=[0, rng.uniform(-30, 30), rng.uniform(-8, 8)]))
    bones.append({"name": "rubble", "parent": "root", "pivot": [0, 0, 0], "cubes": rubble})
    return geometry("geometry.beyondsolo.gate", size, bones, 6, 7, 2.5), shards, crystals


def gate_animations(shards, crystals):
    anims = {}
    # idle: the shards drift in and out and bob, the crystals turn
    idle = {}
    for s in shards:
        def f(t, s=s):
            w = 2 * math.pi * t / 4.0 + s["phase"]
            r = 0.9 * math.sin(w)
            return [-s["nx"] * r, s["ny"] * r + 0.5 * math.sin(w * 2), 0.6 * math.cos(w)]
        idle[f"shard{s['i']}"] = {"position": keyframes(f, 4.0, 0.2)}
    for c in crystals:
        def rot(t, c=c):
            return [0, 360 * t / 4.0, 0]
        def pos(t, c=c):
            w = 2 * math.pi * t / 4.0 + c["phase"]
            return [-c["nx"] * 1.2 * math.sin(w), c["ny"] * 1.2 * math.sin(w), 0]
        idle[f"crystal{c['i']}"] = {"rotation": keyframes(rot, 4.0, 0.5), "position": keyframes(pos, 4.0, 0.25)}
    idle["ring"] = {"rotation": keyframes(lambda t: [0, 0, 1.2 * math.sin(2 * math.pi * t / 4.0)], 4.0, 0.25)}
    anims["animation.beyondsolo.gate.idle"] = {"loop": True, "animation_length": 4.0, "bones": idle}

    # open: shards fly in from far out, one after another, and snap into place
    length = 2.2
    opening = {}
    for s in shards:
        delay = 0.05 + 0.9 * s["i"] / GATE_SHARDS
        def pos(t, s=s, delay=delay):
            k = ease_out_back(window(t, delay, delay + 0.7), 1.4)
            far = (1 - k) * 34
            return [-s["nx"] * far, s["ny"] * far, (1 - k) * 10]
        def scl(t, s=s, delay=delay):
            k = window(t, delay, delay + 0.35)
            v = max(0.001, ease_out_back(k, 2.2))
            return [v, v, v]
        opening[f"shard{s['i']}"] = {"position": keyframes(pos, length, 0.05), "scale": keyframes(scl, length, 0.05)}
    for c in crystals:
        delay = 1.0 + 0.12 * c["i"]
        def scl(t, delay=delay):
            v = max(0.001, ease_out_back(window(t, delay, delay + 0.5), 2.6))
            return [v, v, v]
        opening[f"crystal{c['i']}"] = {"scale": keyframes(scl, length, 0.05)}
    opening["rubble"] = {"scale": keyframes(lambda t: [max(0.001, ease_out_back(window(t, 0, 0.5)))] * 3, length, 0.05)}
    anims["animation.beyondsolo.gate.open"] = {"loop": False, "animation_length": length, "bones": opening}

    # close: everything is pulled into the middle and vanishes
    length = 1.8
    closing = {}
    for s in shards:
        delay = 0.4 * ((s["i"] * 7) % GATE_SHARDS) / GATE_SHARDS
        def pos(t, s=s, delay=delay):
            k = ease_in(window(t, delay, delay + 1.2))
            dx, dy = -(s["x"]), (GATE_CY - s["y"])
            return [-dx * k, dy * k, 0]
        def scl(t, s=s, delay=delay):
            v = max(0.001, 1 - ease_in(window(t, delay + 0.3, delay + 1.3)))
            return [v, v, v]
        def rot(t, s=s, delay=delay):
            return [0, 0, 220 * ease_in(window(t, delay, delay + 1.3))]
        closing[f"shard{s['i']}"] = {"position": keyframes(pos, length, 0.05), "scale": keyframes(scl, length, 0.05),
                                     "rotation": keyframes(rot, length, 0.05)}
    for c in crystals:
        closing[f"crystal{c['i']}"] = {"scale": keyframes(lambda t: [max(0.001, 1 - ease_in(window(t, 0, 0.8)))] * 3, length, 0.05)}
    closing["rubble"] = {"scale": keyframes(lambda t: [max(0.001, 1 - ease_in(window(t, 1.0, 1.8)))] * 3, length, 0.05)}
    anims["animation.beyondsolo.gate.close"] = {"loop": False, "animation_length": length, "bones": closing}
    return {"format_version": "1.8.0", "animations": anims}


def gate_textures():
    for rank in RANKS:
        p = PALETTES[rank]
        rng = np.random.default_rng(100 + RANKS.index(rank))
        tiles = [tile_rock(rng, p["rock"], p["glow"]), tile_rock(rng, [v * 0.8 for v in p["rock"]], p["glow"], veins=False),
                 tile_crystal(rng, p["glow"]), tile_rock(rng, [v * 0.65 for v in p["rock"]], p["glow"], veins=False)]
        img, glow, uvs, size = atlas(tiles, cols=2)
        img.save(os.path.join(TEX, "block", f"gate_{rank}.png"))
        glow.save(os.path.join(TEX, "block", f"gate_{rank}_glowmask.png"))
    return uvs, size


# --------------------------------------------------------------------------------------------- the boss
# tiles: 0 hide, 1 armour, 2 trim, 3 crystal, 4 eye, 5 horn, 6 dark hide, 7 stone fist

def boss_model(uvs, size):
    def c(origin, sz, tile, **kw):
        return cube(origin, sz, tile, uvs, **kw)

    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "body", "parent": "root", "pivot": [0, 20, 0], "cubes": [
            c([-9, 19, -6], [18, 10, 12], 0),
            c([-10, 18, -6.5], [20, 3, 13], 2),
            c([-11.5, 28, -7.5], [23, 12, 15], 1),
            c([-6, 30, -8.5], [12, 8, 2], 2),
            # crystals growing out of the back
            c([-6, 34, 5], [3, 9, 3], 3, pivot=[-4.5, 36, 6.5], rotation=[-35, 0, 18]),
            c([-1.5, 35, 5.5], [3, 12, 3], 3, pivot=[0, 37, 7], rotation=[-40, 0, 0]),
            c([3, 34, 5], [3, 9, 3], 3, pivot=[4.5, 36, 6.5], rotation=[-35, 0, -18]),
            c([-8, 27, 5], [2.5, 6, 2.5], 3, pivot=[-7, 29, 6], rotation=[-50, 0, 30]),
            c([5.5, 27, 5], [2.5, 6, 2.5], 3, pivot=[7, 29, 6], rotation=[-50, 0, -30]),
        ]},
        {"name": "head", "parent": "body", "pivot": [0, 40, -1], "cubes": [
            c([-5, 39.5, -7], [10, 10, 10], 6),
            c([-5.5, 39, -7.6], [11, 3.5, 4], 1),
            c([-5.5, 47, -7.6], [11, 2.5, 11], 1),
            c([-3.8, 44, -7.9], [2.6, 1.4, 0.6], 4),
            c([1.2, 44, -7.9], [2.6, 1.4, 0.6], 4),
            c([-9, 46, -4], [3, 9, 3], 5, pivot=[-6, 47, -2.5], rotation=[0, 0, 38]),
            c([6, 46, -4], [3, 9, 3], 5, pivot=[6, 47, -2.5], rotation=[0, 0, -38]),
            c([-8.5, 53, -3.5], [2, 4, 2], 5, pivot=[-8, 54, -2.5], rotation=[0, 0, 12]),
            c([6.5, 53, -3.5], [2, 4, 2], 5, pivot=[8, 54, -2.5], rotation=[0, 0, -12]),
        ]},
    ]
    for side, sx in (("right", -1), ("left", 1)):
        bones.append({"name": f"{side}_arm", "parent": "body", "pivot": [sx * 12, 37, 0], "cubes": [
            c([sx * 12 - 3.5 + (sx * 1.5), 25, -4], [7, 13, 8], 0),
            c([sx * 12 - 4.5 + (sx * 2), 33, -5], [9, 7, 10], 1),
            c([sx * 12 - 1.5 + (sx * 3), 39, -1.5], [3, 5, 3], 3, pivot=[sx * 14, 40, 0], rotation=[0, 0, -sx * 20]),
        ]})
        bones.append({"name": f"{side}_forearm", "parent": f"{side}_arm", "pivot": [sx * 13.5, 26, 0], "cubes": [
            c([sx * 13.5 - 4, 14, -4.5], [8, 12, 9], 1),
            c([sx * 13.5 - 4.5, 7, -5], [9, 8, 10], 7),
            c([sx * 13.5 - 4.6, 17, -4.8], [9.2, 2, 9.6], 2),
        ]})
        bones.append({"name": f"{side}_leg", "parent": "root", "pivot": [sx * 5, 20, 0], "cubes": [
            c([sx * 5 - 3.5, 9, -4], [7, 11, 8], 6),
            c([sx * 5 - 4, 0, -5], [8, 10, 10], 1),
            c([sx * 5 - 4.2, 8, -5.3], [8.4, 2, 10.6], 2),
        ]})
    return geometry("geometry.beyondsolo.gate_boss", size, bones, 4, 5, 1.8)


def boss_animations():
    anims = {}
    L = 3.0
    anims["animation.beyondsolo.gate_boss.idle"] = {"loop": True, "animation_length": L, "bones": {
        "body": {"position": keyframes(lambda t: [0, -0.6 * (0.5 - 0.5 * math.cos(2 * math.pi * t / L)), 0], L, 0.25),
                 "rotation": keyframes(lambda t: [2 * math.sin(2 * math.pi * t / L), 0, 0], L, 0.25)},
        "head": {"rotation": keyframes(lambda t: [-3 * math.sin(2 * math.pi * t / L), 4 * math.sin(2 * math.pi * t / (L * 2) * 2), 0], L, 0.25)},
        "right_arm": {"rotation": keyframes(lambda t: [0, 0, 3 + 2 * math.sin(2 * math.pi * t / L)], L, 0.25)},
        "left_arm": {"rotation": keyframes(lambda t: [0, 0, -3 - 2 * math.sin(2 * math.pi * t / L)], L, 0.25)},
        "right_forearm": {"rotation": keyframes(lambda t: [-8, 0, 0], L, 1.0)},
        "left_forearm": {"rotation": keyframes(lambda t: [-8, 0, 0], L, 1.0)},
    }}
    L = 1.4
    sw = lambda t: math.sin(2 * math.pi * t / L)
    anims["animation.beyondsolo.gate_boss.walk"] = {"loop": True, "animation_length": L, "bones": {
        "right_leg": {"rotation": keyframes(lambda t: [28 * sw(t), 0, 0], L, 0.1)},
        "left_leg": {"rotation": keyframes(lambda t: [-28 * sw(t), 0, 0], L, 0.1)},
        "right_arm": {"rotation": keyframes(lambda t: [-20 * sw(t), 0, 4], L, 0.1)},
        "left_arm": {"rotation": keyframes(lambda t: [20 * sw(t), 0, -4], L, 0.1)},
        "body": {"position": keyframes(lambda t: [0, -1.2 * abs(sw(t)), 0], L, 0.1),
                 "rotation": keyframes(lambda t: [5, 4 * sw(t), 0], L, 0.1)},
        "head": {"rotation": keyframes(lambda t: [-5, -3 * sw(t), 0], L, 0.1)},
    }}
    # slam: both fists go up, hang, and come down hard
    L = 1.5
    def raise_k(t):
        return ease_in_out(window(t, 0.0, 0.55)) * (1 - ease_in(window(t, 0.62, 0.78)))
    def slam_k(t):
        return ease_in(window(t, 0.62, 0.78)) * (1 - ease_in_out(window(t, 1.05, 1.5)))
    anims["animation.beyondsolo.gate_boss.slam"] = {"loop": False, "animation_length": L, "bones": {
        "right_arm": {"rotation": keyframes(lambda t: [165 * raise_k(t) - 55 * slam_k(t), 0, 15 * raise_k(t)], L, 0.05)},
        "left_arm": {"rotation": keyframes(lambda t: [165 * raise_k(t) - 55 * slam_k(t), 0, -15 * raise_k(t)], L, 0.05)},
        "right_forearm": {"rotation": keyframes(lambda t: [25 * raise_k(t), 0, 0], L, 0.05)},
        "left_forearm": {"rotation": keyframes(lambda t: [25 * raise_k(t), 0, 0], L, 0.05)},
        "body": {"rotation": keyframes(lambda t: [-12 * raise_k(t) + 22 * slam_k(t), 0, 0], L, 0.05),
                 "position": keyframes(lambda t: [0, -4 * slam_k(t), -2 * slam_k(t)], L, 0.05)},
        "head": {"rotation": keyframes(lambda t: [10 * raise_k(t) - 10 * slam_k(t), 0, 0], L, 0.05)},
        "right_leg": {"rotation": keyframes(lambda t: [-18 * slam_k(t), 0, 0], L, 0.05)},
        "left_leg": {"rotation": keyframes(lambda t: [14 * slam_k(t), 0, 0], L, 0.05)},
    }}
    # swipe: a wide backhand with the right arm
    L = 1.0
    def wind(t):
        return ease_in_out(window(t, 0.0, 0.35)) * (1 - window(t, 0.38, 0.5))
    def cut(t):
        return ease_in(window(t, 0.38, 0.52)) * (1 - ease_in_out(window(t, 0.65, 1.0)))
    anims["animation.beyondsolo.gate_boss.swipe"] = {"loop": False, "animation_length": L, "bones": {
        "right_arm": {"rotation": keyframes(lambda t: [70 * wind(t) + 80 * cut(t), -60 * wind(t) + 70 * cut(t), 40 * wind(t) - 10 * cut(t)], L, 0.05)},
        "body": {"rotation": keyframes(lambda t: [0, 25 * wind(t) - 35 * cut(t), 0], L, 0.05)},
        "head": {"rotation": keyframes(lambda t: [0, -15 * wind(t) + 15 * cut(t), 0], L, 0.05)},
    }}
    # roar: rears up, throws the arms out and shakes
    L = 2.6
    def up(t):
        return ease_out_back(window(t, 0.2, 0.75), 1.6) * (1 - ease_in_out(window(t, 2.1, 2.6)))
    def shake(t):
        return math.sin(t * 55) * window(t, 0.7, 0.9) * (1 - window(t, 1.9, 2.2))
    anims["animation.beyondsolo.gate_boss.roar"] = {"loop": False, "animation_length": L, "bones": {
        "body": {"rotation": keyframes(lambda t: [-16 * up(t) + 1.5 * shake(t), 0, 1.5 * shake(t)], L, 0.05)},
        "head": {"rotation": keyframes(lambda t: [32 * up(t) + 4 * shake(t), 3 * shake(t), 0], L, 0.05)},
        "right_arm": {"rotation": keyframes(lambda t: [25 * up(t), 0, 70 * up(t) + 3 * shake(t)], L, 0.05)},
        "left_arm": {"rotation": keyframes(lambda t: [25 * up(t), 0, -70 * up(t) - 3 * shake(t)], L, 0.05)},
        "right_forearm": {"rotation": keyframes(lambda t: [45 * up(t), 0, 0], L, 0.05)},
        "left_forearm": {"rotation": keyframes(lambda t: [45 * up(t), 0, 0], L, 0.05)},
    }}
    # death: staggers, drops to its knees, slumps forward and stays down
    L = 3.2
    def knees(t):
        return ease_in(window(t, 0.6, 1.2))
    def slump(t):
        return ease_in_out(window(t, 1.6, 2.5))
    def stagger(t):
        return math.sin(t * 9) * (1 - window(t, 0, 0.7)) * window(t, 0, 0.1)
    anims["animation.beyondsolo.gate_boss.death"] = {"loop": "hold_on_last_frame", "animation_length": L, "bones": {
        "root": {"position": keyframes(lambda t: [0, -9 * knees(t) - 6 * slump(t), -4 * slump(t)], L, 0.05)},
        "body": {"rotation": keyframes(lambda t: [-14 * stagger(t) + 10 * knees(t) + 58 * slump(t), 6 * stagger(t), 0], L, 0.05)},
        "head": {"rotation": keyframes(lambda t: [-20 * window(t, 0, 0.5) * (1 - knees(t)) - 25 * slump(t), 0, 10 * slump(t)], L, 0.05)},
        "right_leg": {"rotation": keyframes(lambda t: [-80 * knees(t), 0, 0], L, 0.05)},
        "left_leg": {"rotation": keyframes(lambda t: [-80 * knees(t), 0, 0], L, 0.05)},
        "right_arm": {"rotation": keyframes(lambda t: [-20 * knees(t) + 60 * slump(t), 0, 10 * knees(t)], L, 0.05)},
        "left_arm": {"rotation": keyframes(lambda t: [-20 * knees(t) + 60 * slump(t), 0, -10 * knees(t)], L, 0.05)},
    }}
    return {"format_version": "1.8.0", "animations": anims}


def boss_textures():
    uvs, size = None, None
    for rank in RANKS:
        p = PALETTES[rank]
        rng = np.random.default_rng(200 + RANKS.index(rank))
        tiles = [tile_hide(rng, p["hide"]), tile_armor(rng, p["armor"], p["trim"]), tile_trim(rng, p["trim"]),
                 tile_crystal(rng, p["glow"]), tile_eye(rng, p["glow"]), tile_horn(rng),
                 tile_hide(rng, [v * 0.7 for v in p["hide"]]), tile_rock(rng, p["rock"], p["glow"])]
        img, glow, uvs, size = atlas(tiles, cols=4)
        img.save(os.path.join(TEX, "entity", f"gate_boss_{rank}.png"))
        glow.save(os.path.join(TEX, "entity", f"gate_boss_{rank}_glowmask.png"))
    return uvs, size


# --------------------------------------------------------------------------------------------- reward cache
# tiles: 0 dark metal, 1 gold, 2 system gem

def cache_model(uvs, size):
    def c(origin, sz, tile, **kw):
        return cube(origin, sz, tile, uvs, **kw)
    bones = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "base", "parent": "root", "pivot": [0, 0, 0], "cubes": [
            c([-7.5, 0, -6], [15, 9, 12], 0),
            c([-8, 0, -6.5], [16, 1.5, 13], 1),
            c([-8, 7.5, -6.5], [2, 1.5, 13], 1),
            c([6, 7.5, -6.5], [2, 1.5, 13], 1),
            c([-8, 1.5, -6.5], [1.5, 6, 1.5], 1),
            c([6.5, 1.5, -6.5], [1.5, 6, 1.5], 1),
            c([-8, 1.5, 5], [1.5, 6, 1.5], 1),
            c([6.5, 1.5, 5], [1.5, 6, 1.5], 1),
            c([-1.5, 5, -7], [3, 3, 1], 1),
        ]},
        {"name": "lid", "parent": "root", "pivot": [0, 9, 6], "cubes": [
            c([-7.5, 9, -6], [15, 4, 12], 0),
            c([-8, 12.5, -6.5], [16, 1, 13], 1),
            c([-1, 10, -7], [2, 3, 1], 1),
        ]},
        {"name": "gem", "parent": "lid", "pivot": [0, 13.5, 0], "cubes": [
            c([-1.5, 13, -1.5], [3, 3, 3], 2, pivot=[0, 14.5, 0], rotation=[45, 0, 45]),
        ]},
    ]
    return geometry("geometry.beyondsolo.reward_cache", size, bones, 2, 2, 0.5)


def cache_animations():
    L = 1.6
    def lid(t):
        return -112 * ease_out_back(window(t, 0.35, 1.0), 2.2)
    def shake(t):
        return math.sin(t * 60) * 2.5 * (1 - window(t, 0, 0.35))
    return {"format_version": "1.8.0", "animations": {
        "animation.beyondsolo.reward_cache.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "gem": {"position": keyframes(lambda t: [0, 0.6 * math.sin(math.pi * t), 0], 2.0, 0.2),
                    "rotation": keyframes(lambda t: [0, 180 * t, 0], 2.0, 0.5)},
        }},
        "animation.beyondsolo.reward_cache.open": {"loop": "hold_on_last_frame", "animation_length": L, "bones": {
            "lid": {"rotation": keyframes(lambda t: [lid(t), 0, shake(t) * 0.5], L, 0.05)},
            "base": {"rotation": keyframes(lambda t: [0, 0, shake(t)], L, 0.05)},
            "gem": {"scale": keyframes(lambda t: [1 + 0.6 * math.sin(math.pi * window(t, 0.3, 0.9))] * 3, L, 0.05)},
        }},
    }}


def cache_textures():
    rng = np.random.default_rng(300)
    img, glow, uvs, size = atlas([tile_metal_dark(rng), tile_gold(rng), tile_system_gem(rng)], cols=2)
    img.save(os.path.join(TEX, "block", "reward_cache.png"))
    glow.save(os.path.join(TEX, "block", "reward_cache_glowmask.png"))
    return uvs, size


# --------------------------------------------------------------------------------------------- light textures

def vortex_texture():
    """Spiral arms, white on transparent: the gate's surface (layered and turned by the renderer)."""
    S = 256
    a = np.zeros((S, S, 4), dtype=np.float64)
    ys, xs = np.mgrid[0:S, 0:S]
    dx, dy = (xs - S / 2 + 0.5) / (S / 2), (ys - S / 2 + 0.5) / (S / 2)
    r = np.sqrt(dx * dx + dy * dy)
    ang = np.arctan2(dy, dx)
    arms = 5
    spiral = np.cos(arms * ang + 7.0 * np.log(r + 0.05))
    v = np.clip(spiral, 0, 1) ** 3
    v *= np.clip(1 - r, 0, 1) ** 0.7 * np.clip(r * 3, 0, 1)
    core = np.clip(1 - r * 2.2, 0, 1) ** 2
    v = np.clip(v * 0.9 + core, 0, 1)
    a[:, :, 0:3] = 255
    a[:, :, 3] = v * 255
    Image.fromarray(a.astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.2)).save(os.path.join(FX_OUT, "vortex.png"))
    with open(os.path.join(FX_OUT, "vortex.png.mcmeta"), "w") as f:
        f.write('{"texture": {"blur": true, "clamp": true}}')


def beam_texture():
    """A soft vertical beam: bright in the middle, fading to the sides and the top."""
    W, H = 64, 256
    a = np.zeros((H, W, 4))
    ys, xs = np.mgrid[0:H, 0:W]
    across = 1 - np.abs((xs - W / 2 + 0.5) / (W / 2))
    up = 1 - ys / H
    v = across ** 2.2 * (1 - up) ** 0.6
    a[:, :, 0:3] = 255
    a[:, :, 3] = np.clip(v, 0, 1) * 255
    Image.fromarray(a.astype(np.uint8)).save(os.path.join(FX_OUT, "beam.png"))
    with open(os.path.join(FX_OUT, "beam.png.mcmeta"), "w") as f:
        f.write('{"texture": {"blur": true, "clamp": true}}')


def item_icon():
    """16x16 inventory icon: a dark oval rimmed with rock and a blue swirl."""
    S = 16
    a = np.zeros((S, S, 4), dtype=np.uint8)
    for y in range(S):
        for x in range(S):
            dx, dy = (x - 7.5) / 6.0, (y - 7.5) / 7.5
            r = math.hypot(dx, dy)
            if r > 1.05:
                continue
            if r > 0.78:
                a[y, x] = (70, 74, 86, 255) if (x + y) % 3 else (50, 54, 64, 255)
            else:
                ang = math.atan2(dy, dx)
                v = 0.5 + 0.5 * math.cos(3 * ang + 6 * r)
                a[y, x] = (clamp(20 + 60 * v * (1 - r)), clamp(40 + 150 * v * (1 - r) + 40 * (1 - r)), clamp(90 + 165 * (1 - r)), 255)
    Image.fromarray(a).save(os.path.join(TEX, "item", "gate.png"))


def main():
    for d in ("block", "entity"):
        os.makedirs(os.path.join(GEO, d), exist_ok=True)
        os.makedirs(os.path.join(ANIM, d), exist_ok=True)
        os.makedirs(os.path.join(TEX, d), exist_ok=True)

    uvs, size = gate_textures()
    model, shards, crystals = gate_model(uvs, size)
    write_json(os.path.join(GEO, "block", "gate.geo.json"), model)
    write_json(os.path.join(ANIM, "block", "gate.animation.json"), gate_animations(shards, crystals))

    uvs, size = boss_textures()
    write_json(os.path.join(GEO, "entity", "gate_boss.geo.json"), boss_model(uvs, size))
    write_json(os.path.join(ANIM, "entity", "gate_boss.animation.json"), boss_animations())

    uvs, size = cache_textures()
    write_json(os.path.join(GEO, "block", "reward_cache.geo.json"), cache_model(uvs, size))
    write_json(os.path.join(ANIM, "block", "reward_cache.animation.json"), cache_animations())

    item_icon()
    vortex_texture()
    beam_texture()
    print("gate assets written")


if __name__ == "__main__":
    main()
