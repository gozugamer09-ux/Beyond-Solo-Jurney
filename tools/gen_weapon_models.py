#!/usr/bin/env python3
"""
Held and dropped 3D models for every weapon (the 46 melee weapons and the 5 bows with their pull frames).

Minecraft's own "item/generated" turns a sprite into a slab one sixteenth of a block deep, with side walls
coloured by the outermost pixels. That works for 16x16 vanilla art, but our weapons are 64x64 with a dark outline
and a soft grade glow around them, so the walls came out as dark stripes four texels deep, and on glowing weapons
as see-through glow. This script builds the slab itself instead:

- the sprite on a front and a back plane (the inventory icon is unchanged: the GUI still uses item/generated)
- side walls only around the solid body (not around the glow or stray sparkles), merged into runs, coloured from
  a "side" texture painted from the material just inside the outline, so the edge of a steel blade is steel and the
  edge of a leather grip is leather
- the walls render first (cutout) and the faces after them (translucent), so the glow blends over the walls
  instead of cutting holes in them
- display transforms per weapon kind: bigger in the hand and on the ground than vanilla, the grip kept in the hand

Run from the repo root after the textures exist (tools/gen_hd_gear.py and tools/gen_awakening_assets.py call it):
    python3 tools/gen_weapon_models.py [names...]
Needs: pillow, numpy.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
TEX = os.path.join(ASSETS, "textures/item")
SIDE_DIR = "held"                      # side textures: textures/item/held/<name>_side.png
MODELS = os.path.join(ASSETS, "models/item")
ATTRS = os.path.join(ROOT, "src/main/resources/data/beyondsolo/weapon_attributes")

BOWS = ["hunters_shortbow", "windrunner_longbow", "serpentsting_bow", "galebreaker_warbow", "starfall_longbow"]
PULL_FRAMES = 3

# ------------------------------------------------------------------------------------------------ kinds
#
# Every melee weapon has a Better Combat parent (data/beyondsolo/weapon_attributes/<name>.json); its category picks
# the profile. "hand": size in the hand compared with a vanilla sword (third person); "fp": the same in first
# person; "ground": size of the dropped item compared with vanilla; "thick": slab depth in model units (vanilla 1).
# "grip": how far along the weapon (from the pommel end, in model units) the hand holds it, compared with vanilla.
# Gauntlets ("fist") are worn rather than held: in the hand and on the ground they are the 3D gauntlets of
# tools/gen_gauntlets.py, and the slab is only what an item frame shows.

PROFILES = {
    "dagger":      dict(hand=1.30, fp=1.00, ground=1.45, thick=0.75, grip=0.0),
    "sword":       dict(hand=1.45, fp=1.05, ground=1.65, thick=0.75, grip=0.0),
    "rapier":      dict(hand=1.45, fp=1.05, ground=1.65, thick=0.70, grip=0.0),
    "katana":      dict(hand=1.55, fp=1.05, ground=1.75, thick=0.70, grip=0.0),
    "claymore":    dict(hand=1.70, fp=1.15, ground=1.95, thick=0.85, grip=0.0),
    "mace":        dict(hand=1.45, fp=1.05, ground=1.65, thick=0.85, grip=0.0),
    "hammer":      dict(hand=1.65, fp=1.15, ground=1.95, thick=0.95, grip=0.0),
    "heavy_axe":   dict(hand=1.65, fp=1.15, ground=1.95, thick=0.85, grip=0.0),
    "claw":        dict(hand=1.30, fp=1.00, ground=1.50, thick=0.80, grip=0.0),
    "fist":        dict(hand=0.85, fp=1.00, ground=1.45, thick=1.00, grip=0.0),
    "battlestaff": dict(hand=1.80, fp=1.15, ground=1.95, thick=0.85, grip=0.0),
    "spear":       dict(hand=2.00, fp=1.15, ground=2.10, thick=0.80, grip=0.0),
    "glaive":      dict(hand=2.00, fp=1.15, ground=2.10, thick=0.80, grip=0.0),
    "halberd":     dict(hand=2.00, fp=1.15, ground=2.10, thick=0.85, grip=0.0),
    "scythe":      dict(hand=2.00, fp=1.15, ground=2.10, thick=0.80, grip=0.0),
    "bow":         dict(hand=1.40, fp=1.05, ground=1.65, thick=0.70, grip=0.0),
}

# vanilla transforms the profiles scale from
HANDHELD_3P = dict(rotation=[0, -90, 55], translation=[0, 4.0, 0.5], scale=0.85)
HANDHELD_1P = dict(rotation=[0, -90, 25], translation=[1.13, 3.2, 1.13], scale=0.68)
BOW_3P = dict(rotation=[-80, 260, -40], translation=[-1, -2, 2.5], scale=0.9)
BOW_1P = HANDHELD_1P
BOW_GRIP = (6.75, 9.25, 8.0)           # model pixels: the middle of our bows' grip wrap
GROUND_SCALE = 0.5


def category(name):
    if name.split("_pulling_")[0] in BOWS:
        return "bow"
    with open(os.path.join(ATTRS, name + ".json")) as f:
        parent = json.load(f).get("parent", "")
    return parent.split(":")[-1]


def weapon_names():
    names = sorted(f[:-5] for f in os.listdir(ATTRS) if f.endswith(".json"))
    return names + BOWS


# ------------------------------------------------------------------------------------------------ transforms

def _rot(rx, ry, rz):
    def X(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])

    def Y(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])

    def Z(a):
        c, s = math.cos(a), math.sin(a)
        return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])
    return X(math.radians(rx)) @ Y(math.radians(ry)) @ Z(math.radians(rz))


def scaled(base, factor, shift=0.0):
    """`base` scaled by `factor`, translated so the sprite point that `base` puts in the hand stays in the hand.
    `shift` slides that point along the weapon (model units, + towards the tip) before scaling."""
    R = _rot(*base["rotation"])
    s0 = base["scale"]
    t0 = np.array(base["translation"], float)
    anchor = 8.0 + (R.T @ -t0) / s0                    # model units: the point at the hand
    anchor = anchor + shift * np.array([1, 1, 0]) / math.sqrt(2)
    s = s0 * factor
    t = -(R @ ((anchor - 8.0) * s))
    t = np.clip(t, -80, 80)
    return {"rotation": base["rotation"], "translation": [round(float(v), 3) for v in t], "scale": [round(s, 4)] * 3}


def mirrored(tr):
    """The left hand's transform from the right hand's, as vanilla's own models write it."""
    r = tr["rotation"]
    return {"rotation": [r[0], -r[1], -r[2]], "translation": tr["translation"], "scale": tr["scale"]}


def displays(cat, drawn=False):
    p = PROFILES[cat]
    if cat == "bow" and not drawn:
        # A bow at rest: vanilla's bow transform holds our long, shallow bows flat at the hip like a stick, so the
        # idle model (the pull frames keep vanilla's drawing hold) carries it upright at the side instead, its back
        # to the front and the string to the rear.
        ws = weapon_stances
        third = ws.held_transform(ws.body(0.08, 0.92, 0.38), ws.body(0, 0, 1), BOW_GRIP, BOW_3P["scale"] * p["hand"])
        third_l = mirrored(third)
        first = scaled(BOW_1P, p["fp"])
    elif cat == "bow":
        third = scaled(BOW_3P, p["hand"])
        third_l = {"rotation": [-80, -280, 40], "translation": third["translation"], "scale": third["scale"]}
        first = scaled(BOW_1P, p["fp"])
    else:
        third = scaled(HANDHELD_3P, p["hand"], p["grip"])
        third_l = mirrored(third)
        first = scaled(HANDHELD_1P, p["fp"], p["grip"])
    gs = GROUND_SCALE * p["ground"]
    # The dropped-item renderer lifts every item by a quarter block (this model reports no transforms of its own),
    # so sink it by what that lift adds over half the model's height: the lowest pixel then rests on the ground.
    ground = {"rotation": [0, 0, 0], "translation": [0, round(8 * gs - 4, 3), 0], "scale": [round(gs, 4)] * 3}
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": third_l,
        "firstperson_righthand": first,
        "firstperson_lefthand": mirrored(first),
        "ground": ground,
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
    }


# ------------------------------------------------------------------------------------------------ geometry

def first_frame(img):
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


def body_mask(a, min_part=10):
    """Solid pixels that get walls: opaque, minus tiny detached bits (sparkles drawn off the silhouette)."""
    solid = a[..., 3] >= 200
    h, w = solid.shape
    seen = np.zeros_like(solid)
    keep = np.zeros_like(solid)
    for y0 in range(h):
        for x0 in range(w):
            if not solid[y0, x0] or seen[y0, x0]:
                continue
            stack, comp = [(y0, x0)], []
            seen[y0, x0] = True
            while stack:
                y, x = stack.pop()
                comp.append((y, x))
                for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    yy, xx = y + dy, x + dx
                    if 0 <= yy < h and 0 <= xx < w and solid[yy, xx] and not seen[yy, xx]:
                        seen[yy, xx] = True
                        stack.append((yy, xx))
            if len(comp) >= min_part:
                for y, x in comp:
                    keep[y, x] = True
    return keep


def side_texture(a, body):
    """What each wall shows: the material just inside the outline, a touch darker, with a little grain."""
    h, w = body.shape
    rgb = a[..., :3].astype(float)
    inner = body.copy()
    inner[1:, :] &= body[:-1, :]
    inner[:-1, :] &= body[1:, :]
    inner[:, 1:] &= body[:, :-1]
    inner[:, :-1] &= body[:, 1:]
    out = np.zeros((h, w, 4), np.uint8)
    rng = np.random.default_rng(7)
    lum = rgb.sum(-1)
    for y in range(h):
        for x in range(w):
            if not body[y, x]:
                continue
            if inner[y, x]:
                c = rgb[y, x]
            else:
                acc, n = np.zeros(3), 0.0
                for r in (1, 2, 3):
                    y0, y1, x0, x1 = max(0, y - r), min(h, y + r + 1), max(0, x - r), min(w, x + r + 1)
                    m = inner[y0:y1, x0:x1]
                    if m.any():
                        wts = m * (lum[y0:y1, x0:x1] + 60)        # lean to the lit side of the material
                        acc = (rgb[y0:y1, x0:x1] * wts[..., None]).sum((0, 1))
                        n = wts.sum()
                        break
                c = acc / n if n else np.minimum(255, rgb[y, x] * 1.6 + 25)
            c = c * (0.86 + 0.06 * rng.random())
            out[y, x, :3] = np.clip(c, 0, 255).astype(np.uint8)
            out[y, x, 3] = 255
    return out


RIGHT_TURN = {("R", "D"), ("D", "L"), ("L", "U"), ("U", "R")}
STEP = {"R": (1, 0), "D": (0, 1), "L": (-1, 0), "U": (0, -1)}
OUTWARD = {"R": (0, -1), "D": (1, 0), "L": (0, 1), "U": (-1, 0)}     # pixel coordinates, y down


def boundary_loops(body):
    """The body's outline as closed loops of unit edges (pixel corners, y down), walked with the body on the right.
    Each edge: (start, end, direction, pixel it belongs to)."""
    h, w = body.shape
    pad = np.zeros((h + 2, w + 2), bool)
    pad[1:-1, 1:-1] = body
    out = {}
    for y in range(h):
        for x in range(w):
            if not body[y, x]:
                continue
            if not pad[y, x + 1]:
                out.setdefault((x, y), []).append(((x + 1, y), "R", (x, y)))
            if not pad[y + 1, x + 2]:
                out.setdefault((x + 1, y), []).append(((x + 1, y + 1), "D", (x, y)))
            if not pad[y + 2, x + 1]:
                out.setdefault((x + 1, y + 1), []).append(((x, y + 1), "L", (x, y)))
            if not pad[y + 1, x]:
                out.setdefault((x, y + 1), []).append(((x, y), "U", (x, y)))
    loops = []
    while out:
        p0 = next(iter(out))
        loop, p, prev = [], p0, None
        while True:
            cands = out.get(p)
            if not cands:
                break
            pick = 0
            if prev is not None and len(cands) > 1:     # a pinch: keep hugging the same pixels (turn right)
                for i, c in enumerate(cands):
                    if (prev, c[1]) in RIGHT_TURN:
                        pick = i
            end, d, px = cands.pop(pick)
            if not cands:
                del out[p]
            loop.append((p, end, d, px))
            prev, p = d, end
            if p == p0 and (p0 not in out):
                break
        if loop:
            loops.append(loop)
    return loops


def split_runs(loop):
    """Splits a loop into pieces: ("diag", edges) for 45-degree staircases of single steps, ("line", edges) for the
    rest. A staircase becomes one smooth slanted wall, which lights evenly instead of in stripes."""
    n = len(loop)
    # rotate the loop to start where the direction changes after a longer straight piece (or anywhere)
    start = 0
    for i in range(n):
        if loop[i][2] != loop[i - 1][2] and loop[i][2] == loop[(i + 1) % n][2]:
            start = i
            break
    edges = loop[start:] + loop[:start]
    # run-length groups
    groups = []
    for e in edges:
        if groups and groups[-1][0] == e[2]:
            groups[-1][1].append(e)
        else:
            groups.append([e[2], [e]])
    pieces, i = [], 0
    while i < len(groups):
        j = i
        if len(groups[i][1]) == 1:
            a = groups[i][0]
            b = groups[i + 1][0] if i + 1 < len(groups) else None
            while (j + 1 < len(groups) and len(groups[j + 1][1]) == 1 and b is not None
                   and groups[j + 1][0] == (b if (j - i) % 2 == 0 else a)):
                j += 1
        if j - i + 1 >= 5:
            pieces.append(("diag", [g[1][0] for g in groups[i:j + 1]]))
            i = j + 1
        else:
            pieces.append(("line", groups[i][1]))
            i += 1
    return pieces


def walls(body, side, thick):
    """Side walls. Straight pieces become merged axis-aligned walls; 45-degree staircases become one slanted wall
    through their outer corners. Returns (elements, side texture with the slanted walls' colour strips packed in
    below the per-pixel colours)."""
    h, w = body.shape
    ku, kv = 16.0 / w, 16.0 / (2 * h)
    k = 16.0 / w
    z0, z1 = round(8 - thick / 2, 4), round(8 + thick / 2, 4)
    tex = np.zeros((2 * h, w, 4), np.uint8)
    tex[:h] = side
    pack = [0, h]                                      # next free texel of the strip area
    els = []

    def r(v):
        return round(float(v), 4)

    def straight(es):
        d = es[0][2]
        xs = [e[3][0] for e in es]
        ys = [e[3][1] for e in es]
        if d in ("R", "L"):
            x0, x1, y = min(xs), max(xs), ys[0]
            face = "up" if d == "R" else "down"
            yy = 16 - y * k if face == "up" else 16 - (y + 1) * k
            els.append({"from": [r(x0 * k), r(yy), z0], "to": [r((x1 + 1) * k), r(yy), z1],
                        "faces": {face: {"uv": [r(x0 * ku), r(y * kv), r((x1 + 1) * ku), r((y + 1) * kv)],
                                         "texture": "#side"}}})
        else:
            y0, y1, x = min(ys), max(ys), xs[0]
            face = "west" if d == "U" else "east"
            xx = x * k if face == "west" else (x + 1) * k
            els.append({"from": [r(xx), r(16 - (y1 + 1) * k), z0], "to": [r(xx), r(16 - y0 * k), z1],
                        "faces": {face: {"uv": [r(x * ku), r(y0 * kv), r((x + 1) * ku), r((y1 + 1) * kv)],
                                         "texture": "#side"}}})

    def lines(es):
        # consecutive edges in one direction on one row/column merge into one wall
        run = []
        for e in es:
            if run and (e[2] != run[-1][2] or e[0] != run[-1][1]):
                straight(run)
                run = []
            run.append(e)
        if run:
            straight(run)

    def slant_piece(mid, p0, p1):
        # one slanted wall from outer corner p0 to outer corner p1 (pixel-corner points, y down) over the edges mid
        a = np.array([p0[0] * k, 16 - p0[1] * k])
        b = np.array([p1[0] * k, 16 - p1[1] * k])
        if a[0] > b[0]:
            a, b = b, a
            mid = mid[::-1]
        m = (a + b) / 2
        length = float(np.linalg.norm(b - a))
        angle = 45 if (b[1] - a[1]) > 0 else -45
        th = math.radians(angle)
        n_up = np.array([-math.sin(th), math.cos(th)])
        out = np.array([OUTWARD[mid[0][2]][0], -OUTWARD[mid[0][2]][1]], float) + \
            np.array([OUTWARD[mid[1][2]][0], -OUTWARD[mid[1][2]][1]], float)
        face = "up" if float(n_up @ out) > 0 else "down"
        cols = [side[e[3][1], e[3][0]] for e in mid]
        nseg = len(cols)
        if pack[0] + nseg > w:
            pack[0], pack[1] = 0, pack[1] + 1
        if pack[1] >= 2 * h:
            lines(mid)
            return
        u0, v0 = pack
        for i, c in enumerate(cols):
            tex[v0, u0 + i] = c
        pack[0] += nseg
        els.append({"from": [r(m[0] - length / 2), r(m[1]), z0], "to": [r(m[0] + length / 2), r(m[1]), z1],
                    "rotation": {"origin": [r(m[0]), r(m[1]), 8], "axis": "z", "angle": angle, "rescale": False},
                    "faces": {face: {"uv": [r(u0 * ku), r(v0 * kv), r((u0 + nseg) * ku), r((v0 + 1) * kv)],
                                     "texture": "#side"}}})

    def slanted(es):
        # convex corners are right turns (the body is on the right of the walk)
        corners = [i for i in range(len(es) - 1) if (es[i][2], es[i + 1][2]) in RIGHT_TURN]
        if len(corners) < 2:
            lines(es)
            return
        lines(es[:corners[0] + 1])
        lines(es[corners[-1] + 1:])
        # the edges between the first and last outer corner become slanted walls, each short enough to fit one
        # row of the strip area; neighbouring pieces share an outer corner
        start = corners[0]
        for j in range(1, len(corners)):
            last = j == len(corners) - 1
            if last or corners[j + 1] - start > w:
                slant_piece(es[start + 1:corners[j] + 1], es[start][1], es[corners[j]][1])
                start = corners[j]

    for loop in boundary_loops(body):
        for kind, es in split_runs(loop):
            (slanted if kind == "diag" else lines)(es)
    return els, tex


def faces(thick):
    z0, z1 = round(8 - thick / 2, 4), round(8 + thick / 2, 4)
    return [{"from": [0, 0, z0], "to": [16, 16, z1], "faces": {
        "north": {"uv": [16, 0, 0, 16], "texture": "#layer0"},
        "south": {"uv": [0, 0, 16, 16], "texture": "#layer0"}}}]


# ------------------------------------------------------------------------------------------------ models

def tex_id(name):
    return "beyondsolo:item/" + name


def side_id(name):
    return f"beyondsolo:item/{SIDE_DIR}/{name}_side"


def held_model(name, cat, thick, drawn=False):
    img = first_frame(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA"))
    a = np.array(img)
    body = body_mask(a)
    els, side_tex = walls(body, side_texture(a, body), thick)
    os.makedirs(os.path.join(TEX, SIDE_DIR), exist_ok=True)
    Image.fromarray(side_tex).save(os.path.join(TEX, SIDE_DIR, name + "_side.png"), optimize=True)
    layer0, side = tex_id(name), side_id(name)
    return {
        "loader": "neoforge:composite",
        "textures": {"particle": layer0},
        "children": {
            "sides": {"render_type": "minecraft:cutout", "textures": {"side": side, "particle": layer0},
                      "elements": els},
            "faces": {"render_type": "minecraft:translucent", "textures": {"layer0": layer0, "particle": layer0},
                      "elements": faces(thick)},
        },
        "item_render_order": ["sides", "faces"],
        "display": displays(cat, drawn),
    }


def item_model(name, cat, overrides=None, sprite=None, worn=None):
    """`sprite`: the item whose picture this model shows (default: itself). `worn`: for a gauntlet, the 3D model
    (tools/gen_gauntlets.py) its two third-person hands show; first person and the ground always show the snug fit
    (client/combat/WornGauntlets.java draws the first-person arm bare)."""
    sprite = sprite or name
    model = {
        "loader": "neoforge:separate_transforms",
        "gui_light": "front",
        "textures": {"particle": tex_id(sprite)},
        "base": held_model(sprite, cat, PROFILES[cat]["thick"], "_pulling_" in name),
        "perspectives": {"gui": {"parent": "minecraft:item/generated", "textures": {"layer0": tex_id(sprite)}}},
    }
    if worn:
        snug = gen_gauntlets.model_id(sprite)
        for hand in ("righthand", "lefthand"):
            model["perspectives"]["thirdperson_" + hand] = {"parent": worn}
            model["perspectives"]["firstperson_" + hand] = {"parent": snug}
        model["perspectives"]["ground"] = {"parent": snug}
    if overrides:
        model["overrides"] = overrides
    return model


def write(name, model):
    path = os.path.join(MODELS, name + ".json")
    with open(path, "w") as f:
        json.dump(model, f, separators=(",", ":"))
        f.write("\n")


def build(names=None):
    todo = weapon_names()
    if names:
        todo = [n for n in todo if n in names]
    count = 0
    for name in todo:
        cat = category(name)
        if cat not in PROFILES:
            raise SystemExit(f"{name}: no model profile for Better Combat category '{cat}'")
        if cat == "bow":
            overrides = [{"predicate": {"pulling": 1}, "model": f"beyondsolo:item/{name}_pulling_0"},
                         {"predicate": {"pulling": 1, "pull": 0.65}, "model": f"beyondsolo:item/{name}_pulling_1"},
                         {"predicate": {"pulling": 1, "pull": 0.9}, "model": f"beyondsolo:item/{name}_pulling_2"}]
            write(name, item_model(name, cat, overrides))
            for i in range(PULL_FRAMES):
                write(f"{name}_pulling_{i}", item_model(f"{name}_pulling_{i}", cat))
                count += 1
        elif cat == "fist":
            # worn: the snug gauntlet, and the wider one when the wearer's chestplate covers the arm
            gen_gauntlets.write(name)
            fits = [f for f in gen_gauntlets.FITS if f]
            overrides = [{"predicate": {"beyondsolo:" + f.strip("_"): 1}, "model": f"beyondsolo:item/{name}{f}"} for f in fits]
            write(name, item_model(name, cat, overrides, worn=gen_gauntlets.model_id(name)))
            for f in fits:
                write(name + f, item_model(name + f, cat, sprite=name, worn=gen_gauntlets.model_id(name, f)))
            set_pose(name, weapon_stances.pose_for(cat))
        else:
            write(name, item_model(name, cat))
            set_pose(name, weapon_stances.pose_for(cat))
        count += 1
    weapon_stances.write_all()
    return count


def set_pose(name, pose):
    """Names the idle stance (tools/weapon_stances.py) in the weapon's Better Combat file, keeping its parent."""
    path = os.path.join(ATTRS, name + ".json")
    with open(path) as f:
        data = json.load(f)
    attrs = data.get("attributes", {})
    if pose:
        attrs["pose"] = pose
    else:
        attrs.pop("pose", None)
    if attrs:
        data["attributes"] = attrs
    else:
        data.pop("attributes", None)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


import gen_gauntlets  # noqa: E402
import weapon_stances  # noqa: E402  (imports this module back for the held transforms)

if __name__ == "__main__":
    n = build(set(sys.argv[1:]) or None)
    print(f"weapon models written: {n}")
