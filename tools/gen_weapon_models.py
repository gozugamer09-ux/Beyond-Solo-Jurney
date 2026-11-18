#!/usr/bin/env python3
"""
Held and dropped 3D models for every weapon (the 46 melee weapons and the 5 bows with their pull frames).

Minecraft's own "item/generated" turns a sprite into a slab one sixteenth of a block deep: a sheet of plywood cut
to the weapon's outline, every diagonal edge a staircase of little walls that light up in stripes. Here every
weapon is a solid instead: tools/weapon_relief.py gives each texel of the sprite its own depth (a blade is a thin
wedge with a ridge, a shaft is round, a hammer head is a block; the depths come from the parts the sprite was
painted from, see tools/gen_hd_gear.py) and builds a mesh with smooth normals from it.

- the solid is a Wavefront OBJ (models/item/held/<name>.obj, read by NeoForge's own OBJ loader): the sprite on its
  front and back, and on the rim a "side" texture painted from the material just inside the outline, so the edge
  of a steel blade is steel and the edge of a leather grip is leather
- the glow around a weapon (the grade's aura, sparkles) is the sprite once more on a flat see-through plane
- the inventory icon is unchanged: the GUI still uses item/generated
- display transforms per weapon: size in the hand from the weapon's real length, the grip kept in the hand

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

# ------------------------------------------------------------------------------------------------ kinds
#
# Every melee weapon has a Better Combat parent (data/beyondsolo/weapon_attributes/<name>.json); its category picks
# the profile:
#   length  how long the weapon is in the hand, in pixels of the player model (the player is 32 tall); LENGTHS
#           sets it for single weapons. The model's scale follows from it and from the sprite's own length, so a
#           dagger whose picture fills its icon is still a dagger in the hand.
#   hold    where the main hand closes: ("grip", f) is f of the way up the weapon's grip wrap, ("u", x) a place
#           along the weapon in design units from the butt (the sprite's diagonal is about 45 long)
#   fp      the first-person hold (FP_HOLDS)
# Gauntlets ("fist") are worn rather than held: in the hand and on the ground they are the 3D gauntlets of
# tools/gen_gauntlets.py, and this model is only what an item frame shows.

PROFILES = {
    "dagger":      dict(length=14.0, hold=("grip", 0.50), fp="short"),
    "claw":        dict(length=14.0, hold=("grip", 0.50), fp="short"),
    "sword":       dict(length=21.5, hold=("grip", 0.55), fp="blade"),
    "rapier":      dict(length=22.5, hold=("grip", 0.55), fp="blade"),
    "mace":        dict(length=18.0, hold=("grip", 0.50), fp="blade"),
    "katana":      dict(length=23.0, hold=("grip", 0.70), fp="blade"),
    "claymore":    dict(length=29.0, hold=("grip", 0.55), fp="great"),
    "hammer":      dict(length=25.0, hold=("grip", 0.15), fp="heavy"),
    "heavy_axe":   dict(length=26.0, hold=("grip", 0.15), fp="heavy"),
    "battlestaff": dict(length=30.5, hold=("u", 10.5), fp="staff"),
    "spear":       dict(length=34.5, hold=("u", 4.25), fp="pole"),
    "glaive":      dict(length=33.0, hold=("u", 4.25), fp="pole"),
    "halberd":     dict(length=35.0, hold=("u", 4.25), fp="pole"),
    "scythe":      dict(length=30.0, hold=("u", 10.5), fp="scythe"),
    "bow":         dict(length=25.0, hold=("grip", 0.50), fp="bow"),
    "fist":        dict(length=12.0, hold=("u", 16.0), fp="short"),
}
LENGTHS = {
    "goblin_shiv": 12.0, "lycan_fang_knife": 13.0, "nightglass_fang": 15.0, "hellsovereign_fang": 15.5, "calamity_fang": 16.0,
    "knight_sword": 21.0, "thunderfang_longsword": 23.0, "stormlord_longsword": 23.5, "ashen_oath": 22.5,
    "steel_greatsword": 28.0, "sunderveil": 30.0, "ruinbringer": 31.0,
    "stormcaller_edict": 26.0, "colossus_aegis": 27.5,
    "hunters_shortbow": 20.0, "windrunner_longbow": 27.0, "serpentsting_bow": 24.0, "galebreaker_warbow": 27.0,
    "starfall_longbow": 28.0,
}
GROUND_SIZE = 0.62                     # a dropped weapon next to the same weapon in the hand

# First-person holds, in the view's own space: x to the right, y up, z towards the player. Straight ahead is
# (0, 0, -1); at that distance the screen ends at about x = +-1.25 and y = +-0.7. "at": where the hold point is;
# "tip": where the weapon points; "face": where the side the inventory icon shows looks (then the edge of an axe or
# scythe, the sprite's upper-left side, is to the left) or "back": where that upper-left side points (bows: the
# way the arrow flies); "size": next to the third-person size; "hold": a hold point further up the weapon than the
# third-person one, as a share of its length (a spear held at its butt would show only shaft).
FP_HOLDS = {
    "short":  dict(at=(0.58, -0.42, -0.66), tip=(-0.22, 0.82, -0.53), face=(-0.60, 0.05, 0.80), size=0.80),
    "blade":  dict(at=(0.64, -0.52, -0.76), tip=(-0.10, 0.85, -0.52), face=(-0.62, 0.05, 0.78), size=0.84),
    "great":  dict(at=(0.66, -0.62, -0.85), tip=(-0.10, 0.84, -0.54), face=(-0.62, 0.05, 0.78), size=0.74),
    "heavy":  dict(at=(0.66, -0.66, -0.80), tip=(-0.12, 0.80, -0.59), face=(-0.55, 0.05, 0.83), size=0.74),
    "pole":   dict(at=(0.62, -0.54, -0.80), tip=(-0.14, 0.62, -0.77), face=(-0.50, 0.10, 0.86), size=0.62, hold=0.40),
    "staff":  dict(at=(0.66, -0.50, -0.85), tip=(-0.06, 0.93, -0.36), face=(-0.50, 0.05, 0.86), size=0.64, hold=0.42),
    "scythe": dict(at=(0.70, -0.52, -0.88), tip=(-0.04, 0.88, -0.47), face=(-0.35, 0.10, 0.93), size=0.62, hold=0.40),
    "bow":    dict(at=(0.76, -0.50, -1.0), tip=(-0.22, 0.90, -0.38), face=(-0.66, 0.05, 0.75), size=0.74),
    # drawing: the arrow from beside the cheek to just under the crosshair, the bow canted a little
    "bow_drawn": dict(at=(0.36, -0.10, -1.25), tip=(0.14, 0.98, -0.10), back=(-0.26, 0.03, -0.96), size=0.60),
}

VANILLA_HAND = [0, -90, 55]            # item/handheld's rotation in the hand: Better Combat's swings are made for it
GUI_FIXED = {"rotation": [0, 180, 0], "scale": [1, 1, 1]}


def category(name):
    if name.split("_pulling_")[0] in BOWS:
        return "bow"
    with open(os.path.join(ATTRS, name + ".json")) as f:
        parent = json.load(f).get("parent", "")
    return parent.split(":")[-1]


def weapon_names():
    names = sorted(f[:-5] for f in os.listdir(ATTRS) if f.endswith(".json"))
    return names + BOWS


_meta = {}


def meta(name):
    """What tools/gen_hd_gear.py noted about the sprite: its length and its grip (pull frames: their bow's)."""
    if not _meta:
        with open(os.path.join(weapon_relief.HEIGHT_DIR, "meta.json")) as f:
            _meta.update(json.load(f))
    return _meta[name.split("_pulling_")[0]]


def model_point(u, v=0.0):
    """Design coordinates (along the weapon from the butt, across it) -> item model units."""
    k = 1 / math.sqrt(2)
    return np.array([((u - v) * k + 3.0) / 2.0, ((u + v) * k + 3.0) / 2.0, 8.0])


def model_length(name):
    u0, u1 = meta(name)["extent"]
    return (u1 - u0) / 2.0                      # model units


def hand_scale(name, cat):
    return LENGTHS.get(name.split("_pulling_")[0], PROFILES[cat]["length"]) / model_length(name)


def hold_point(name, cat):
    """Where the main hand holds the weapon: design (u, v)."""
    kind, x = PROFILES[cat]["hold"]
    if kind == "grip":
        g = meta(name).get("grip")
        if g:
            return g[0] + x * (g[1] - g[0]), g[2]
        u0, u1 = meta(name)["extent"]
        return u0 + 0.2 * (u1 - u0), 0.0
    return x, 0.0


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


def euler(R):
    """Degrees (x, y, z) with _rot(x, y, z) == R (item display rotations are applied X, then Y, then Z)."""
    y = math.asin(max(-1.0, min(1.0, R[0, 2])))
    x = math.atan2(-R[1, 2], R[2, 2])
    z = math.atan2(-R[0, 1], R[0, 0])
    return [round(math.degrees(v), 2) for v in (x, y, z)]


def transform(R, t, s):
    rot = euler(R)
    return {"rotation": rot, "translation": [round(float(v), 3) for v in np.clip(t, -80, 80)], "scale": [round(s, 4)] * 3}


def mirrored(tr):
    """The other hand's transform: the same hold seen in a mirror. The game mirrors a left-hand transform itself
    (it negates the y and z angles and the x offset), and a weapon looks the same from both sides, so the mirror
    image of a hold is a turn: that turn is written here with y and z negated, as vanilla's own models do."""
    R = _rot(*tr["rotation"])
    M, P = np.diag([-1.0, 1.0, 1.0]), np.diag([1.0, 1.0, -1.0])
    e = euler(M @ R @ P)
    return {"rotation": [e[0], -e[1], -e[2]], "translation": tr["translation"], "scale": tr["scale"]}


L_ITEM = np.array([1.0, 1.0, 0.0]) / math.sqrt(2)     # butt -> tip
E_ITEM = np.array([-1.0, 1.0, 0.0]) / math.sqrt(2)    # the sprite's upper-left side
N_ITEM = np.array([0.0, 0.0, 1.0])                    # its face


def frame(tip, face=None, back=None):
    """The rotation that points the weapon along `tip` with its face looking along `face` (or its upper-left side
    along `back`)."""
    L = np.array(tip, float)
    L /= np.linalg.norm(L)
    if back is not None:
        E = np.array(back, float)
        E -= (E @ L) * L
        E /= np.linalg.norm(E)
        N = np.cross(L, E)
    else:
        N = np.array(face, float)
        N -= (N @ L) * L
        N /= np.linalg.norm(N)
        E = np.cross(N, L)
    return np.column_stack([L, E, N]) @ np.column_stack([L_ITEM, E_ITEM, N_ITEM]).T


FP_ARM = np.array([0.56, -0.52, -0.72])                # ItemInHandRenderer.applyItemArmTransform
# while a bow is drawn the game moves and turns the whole hand first (ItemInHandRenderer, case BOW)
FP_DRAW_SHIFT = np.array([-0.2785682, 0.18344387, 0.15731531])
FP_DRAW_PULL = 0.6                                     # the draw the hold is laid out for (the game stretches z with it)


def fp_draw_matrix(pull=FP_DRAW_PULL):
    return _rot(-13.935, 35.3, -9.785) @ np.diag([1.0, 1.0, 1.0 + 0.2 * pull]) @ _rot(0, -45, 0)


def first_person(name, cat, drawn=False):
    hold = FP_HOLDS["bow_drawn" if drawn else PROFILES[cat]["fp"]]
    s = hand_scale(name, cat) * hold["size"]
    if "hold" in hold:
        u0, u1 = meta(name)["extent"]
        p = model_point(u0 + hold["hold"] * (u1 - u0))
    else:
        p = model_point(*hold_point(name, cat))
    R = frame(hold["tip"], hold.get("face"), hold.get("back"))
    at = np.array(hold["at"], float)
    if drawn:
        # view = arm + shift + D (t / 16 + R s (p - 8) / 16): pick R and t so that D R is the frame asked for
        D = fp_draw_matrix()
        Dn = fp_draw_matrix(0.0)                       # directions: without the stretch
        R = np.linalg.inv(Dn) @ R
        t = 16 * (np.linalg.inv(D) @ (at - FP_ARM - FP_DRAW_SHIFT - np.array([0, 0, 0.04 * FP_DRAW_PULL]))) - R @ ((p - 8.0) * s)
    else:
        t = 16 * (at - FP_ARM) - R @ ((p - 8.0) * s)
    return transform(R, t, s)


def displays(name, cat, drawn=False):
    ws = weapon_stances
    s = hand_scale(name, cat)
    p = model_point(*hold_point(name, cat))
    if cat == "bow" and drawn:
        # drawing: vanilla raises the bow arm straight ahead; the bow stands in the fist, canted a little, the
        # arrow along the arm
        third = ws.held_transform(ws.body(0.12, 0.99, 0.0), ws.body(0, 0, 1), p, s, arm_pitch=-90.0)
    elif cat == "bow":
        # at rest: carried at the side, upper limb up and forward, turned so the bow shows from behind the player
        third = ws.held_transform(ws.body(0.10, 0.90, 0.42), ws.body(0.55, 0.0, 0.83), p, s)
    else:
        R = _rot(*VANILLA_HAND)
        third = transform(R, ws.FIST_IN_DISPLAY - R @ ((p - 8.0) * s), s)
    first = first_person(name, cat, drawn)
    gs = s * GROUND_SIZE
    # The dropped-item renderer lifts every item by a quarter block (this model reports no transforms of its own),
    # so sink it by what that lift adds over half the model's height: the lowest pixel then rests on the ground.
    ground = {"rotation": [0, 0, 0], "translation": [0, round(8 * gs - 4, 3), 0], "scale": [round(gs, 4)] * 3}
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": mirrored(third),
        "firstperson_righthand": first,
        "firstperson_lefthand": mirrored(first),
        "ground": ground,
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": GUI_FIXED,
    }


# ------------------------------------------------------------------------------------------------ geometry

def first_frame(img):
    w, h = img.size
    return img.crop((0, 0, w, w)) if h > w else img


def body_mask(a, min_part=10):
    """Solid pixels that get walls: opaque, minus tiny detached bits (sparkles drawn off the silhouette)."""
    return without_specks(a[..., 3] >= 200, min_part)


def without_specks(solid, min_part=10):
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


# ------------------------------------------------------------------------------------------------ models

def tex_id(name):
    return "beyondsolo:item/" + name


def side_id(name):
    return f"beyondsolo:item/{SIDE_DIR}/{name}_side"


HELD_DIR = "held"                      # models/item/held/<name>.obj
MTL = """# materials of every held weapon (tools/gen_weapon_models.py); the textures come from the model json
newmtl face
Kd 1 1 1
map_Kd #layer0
newmtl side
Kd 1 1 1
map_Kd #side
"""


def design_point(x, y):
    """Item model units -> design coordinates: model_point the other way."""
    return (x + y - 3.0) * math.sqrt(2), (y - x) * math.sqrt(2)


def texel_uv(a, u, v, reach=1.5):
    """The middle of the opaque texel of picture `a` nearest design point (u, v), as texture coordinates."""
    S = a.shape[0]
    X, Y, _ = model_point(u, v)
    fx, fy = X * S / 16.0 - 0.5, (16.0 - Y) * S / 16.0 - 0.5
    best = None
    for y in range(max(0, int(fy - reach)), min(S, int(fy + reach) + 2)):
        for x in range(max(0, int(fx - reach)), min(S, int(fx + reach) + 2)):
            if a[y, x, 3] >= 200:
                d = (x - fx) ** 2 + (y - fy) ** 2
                if best is None or d < best[0]:
                    best = (d, x, y)
    x, y = (best[1], best[2]) if best else (min(S - 1, max(0, round(fx))), min(S - 1, max(0, round(fy))))
    return (x + 0.5) / S, (y + 0.5) / S


STRING_R = 0.065                       # model units (a texel is 0.25)
SHAFT_R = 0.105


def bow_extras(mesh, name, a):
    """A held bow's string and, drawn, its arrow as real shapes: a thin cord and a round shaft look right from
    behind and along the arrow, which a painted line with a depth cannot. Their colours are texels of the picture."""
    ga = gen_arsenal_assets
    base, _, frame = name.partition("_pulling_")
    draw = ga.DRAWS[int(frame)] if frame else 0.0
    geo = ga.bow_geometry(base, draw)
    line = geo["string"]
    (u0, v0), (u1, v1) = line[0], line[1]
    cord = texel_uv(a, u0 + (u1 - u0) * 0.35, v0 + (v1 - v0) * 0.35)
    pts = [model_point(u, v) for u, v in line]
    for p, q in zip(pts, pts[1:]):
        weapon_relief.tube(mesh, p, q, STRING_R, cord, sides=6)
    if geo["nock"] is None:
        return
    um, nv = geo["nock"]
    A = E_ITEM                                  # the arrow flies to the sprite's upper left
    W = L_ITEM                                  # across it, in the sprite's plane
    Z = N_ITEM
    k = 0.5                                     # model units to the design unit
    O = model_point(um, nv)

    def at(along, across=0.0, up=0.0):
        return O + A * along * k + W * across * k + Z * up * k

    def colour(du, dv):
        return texel_uv(a, um + du, nv + dv)

    length, head = ga.ARROW_LEN, ga.ARROW_HEAD
    neck = length - head
    r = SHAFT_R / k
    # shaft: the nock, then three lengths that each take the painted shaft's colour there
    weapon_relief.tube(mesh, at(-0.5), at(0.4), SHAFT_R * 1.15, colour(0.0, -0.2), sides=6, caps=(True, False))
    cuts = [0.4, neck * 0.35, neck * 0.7, neck + 0.4]
    for c0, c1 in zip(cuts, cuts[1:]):
        weapon_relief.tube(mesh, at(c0), at(c1), SHAFT_R, colour(0.17, (c0 + c1) / 2), sides=6, caps=(False, False))
    # head: a broadhead with a ridge down the middle and swept barbs
    half, barb = ga.ARROW_HEAD_HALF, ga.ARROW_BARB
    tip = at(length)
    for side in (1, -1):
        c = colour(side * 0.75, neck + 1.0)
        wing = at(neck, side * half)
        for face in (1, -1):
            ridge = at(neck - 0.1, 0.0, face * 0.42)
            weapon_relief.facet(mesh, [tip, wing, ridge], c, normal=Z * face + W * side * 0.3)
        weapon_relief.facet(mesh, [wing, at(neck - 0.1, 0.0, 0.42), at(neck - 0.1, 0.0, -0.42)], c, normal=-A + W * side * 0.5)
        weapon_relief.facet(mesh, [wing, at(neck - barb, side * (half - 0.1)), at(neck - barb, side * (half - 0.3)),
                                   at(neck, side * 0.5)], colour(side * 1.3, neck - 0.5), both=True)
    # three feathers: one standing off the bow's side, two leaning the other way
    f0, f1, fh = ga.ARROW_VANES
    span = f1 - f0
    for turn, c in ((90.0, colour(0.9, f0 + 1.2)), (210.0, colour(-0.9, f0 + 1.2)), (330.0, colour(0.9, f0 + 1.2))):
        t = math.radians(turn)
        ca, sa = math.cos(t), math.sin(t)
        weapon_relief.facet(mesh, [at(f0, ca * r, sa * r), at(f0 + 0.1 * span, ca * (r + fh), sa * (r + fh)),
                                   at(f0 + 0.55 * span, ca * (r + 0.6 * fh), sa * (r + 0.6 * fh)), at(f1, ca * r, sa * r)],
                            c, both=True)


def held_parts(name, cat=None):
    """(picture, solid mesh, side texture, halo runs or True or None) of a held weapon. The halo is what glows
    around the body: True when the whole sprite can go on one plane behind the solid, runs of texels when some of
    the picture must not show (a bow's painted string)."""
    img = first_frame(Image.open(os.path.join(TEX, name + ".png")).convert("RGBA"))
    a = np.array(img)
    body = body_mask(a)
    heights, show = weapon_relief.load_heights(name, with_show=True)
    if heights is None or heights.shape != body.shape:
        heights, show = weapon_relief.generic_heights(body), None
        solid = body
    else:
        # height 0: built as a real shape instead (bow_extras); what only hung on to it goes too
        solid = without_specks(body & (heights > 0))
    mesh = weapon_relief.build(solid, heights, show=show)
    if (cat or category(name)) == "bow":
        bow_extras(mesh, name, a)
    halo = (a[..., 3] > 0) & ~body
    glow = None
    if halo.any():
        left_out = body & ~solid
        if left_out.any():
            # the glow painted round what is left out goes with it: keep what is nearer the solid
            near, far = _spread(solid, 4), _spread(left_out, 4)
            halo &= near <= far
            glow = weapon_relief.mask_runs(halo)
        else:
            glow = True
    return a, mesh, side_texture(a, body), glow


def _spread(mask, reach):
    """How many texels (diagonals count as one) each texel is from the mask, up to reach + 1."""
    d = np.where(mask, 0, reach + 1)
    cur = mask.copy()
    for i in range(1, reach + 1):
        grown = cur.copy()
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                grown[max(0, dy):cur.shape[0] + min(0, dy), max(0, dx):cur.shape[1] + min(0, dx)] |= \
                    cur[max(0, -dy):cur.shape[0] + min(0, -dy), max(0, -dx):cur.shape[1] + min(0, -dx)]
        d[grown & ~cur] = i
        cur = grown
    return d


def held_model(name, cat, drawn=False):
    """The solid weapon: the sprite with a depth of its own under every texel (tools/weapon_relief.py), read by
    NeoForge's OBJ loader, and over it the sprite once more on a flat see-through plane for the glow around it."""
    a, mesh, side_tex, halo = held_parts(name, cat)
    S = a.shape[0]
    os.makedirs(os.path.join(MODELS, HELD_DIR), exist_ok=True)
    weapon_relief.write_obj(mesh, os.path.join(MODELS, HELD_DIR, name + ".obj"))
    with open(os.path.join(MODELS, HELD_DIR, "weapon.mtl"), "w") as f:
        f.write(MTL)
    os.makedirs(os.path.join(TEX, SIDE_DIR), exist_ok=True)
    Image.fromarray(side_tex).save(os.path.join(TEX, SIDE_DIR, name + "_side.png"), optimize=True)
    layer0, side = tex_id(name), side_id(name)
    children = {
        "body": {"loader": "neoforge:obj", "model": f"beyondsolo:models/item/{HELD_DIR}/{name}.obj",
                 "automatic_culling": False, "render_type": "minecraft:cutout",
                 "textures": {"layer0": layer0, "side": side, "particle": layer0}},
    }
    order = ["body"]
    if halo:
        z, k = 8.0, 16.0 / S
        runs = [(0, 0, S, S)] if halo is True else [(y, x0, x1, y + 1) for y, x0, x1 in halo]
        elements = []
        for y0, x0, x1, y1 in runs:
            u0, u1, v0, v1 = (round(v * k, 4) for v in (x0, x1, y0, y1))
            elements.append({"from": [u0, round(16 - v1, 4), z - 0.02], "to": [u1, round(16 - v0, 4), z + 0.02], "faces": {
                "north": {"uv": [u1, v0, u0, v1], "texture": "#layer0"},
                "south": {"uv": [u0, v0, u1, v1], "texture": "#layer0"}}})
        children["glow"] = {"render_type": "minecraft:translucent", "textures": {"layer0": layer0, "particle": layer0},
                            "elements": elements}
        order.append("glow")
    return {
        "loader": "neoforge:composite",
        "textures": {"particle": layer0},
        "children": children,
        "item_render_order": order,
        "display": displays(name, cat, drawn),
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
        "base": held_model(sprite, cat, "_pulling_" in name),
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
            pulls = gen_arsenal_assets.PULLS
            overrides = [{"predicate": {"pulling": 1, **({"pull": p} if p else {})}, "model": f"beyondsolo:item/{name}_pulling_{i}"}
                         for i, p in enumerate(pulls)]
            write(name, item_model(name, cat, overrides))
            for i in range(len(pulls)):
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


import gen_arsenal_assets  # noqa: E402
import gen_gauntlets  # noqa: E402
import weapon_relief  # noqa: E402
import weapon_stances  # noqa: E402  (imports this module back for the held transforms)

if __name__ == "__main__":
    n = build(set(sys.argv[1:]) or None)
    print(f"weapon models written: {n}")
