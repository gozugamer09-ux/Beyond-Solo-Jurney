#!/usr/bin/env python3
"""
The hunter's residence (see com.beyondsolo.home): the Training Hall's dummy, the trophy hall's pedestals and the Well
Rested icon.

- The training dummy (training_dummy): a figure of sacking stuffed with straw, bound to a post on a heavy wooden foot.
  A barrel of a body tied round the waist and the chest with rope, a red and cream target painted on its chest, a
  crossbar through its shoulders with stuffed sleeves on it tied off at the ends (straw sticking out of them), a sack
  for a head tied off at the neck with a stitched face (cross eyes, a sewn mouth) and a tuft of straw out of its top,
  and straw hanging out under its body. The armoured one (bones armour_body and armour_head, hidden on the plain one by
  client.home.TrainingDummyRenderer) wears an iron breastplate and backplate on leather straps, iron pauldrons on its
  crossbar and a kettle hat. Built with the creature kit (tools/gen_creatures.py, painted on the model by
  tools/creature_skins.py). Animations: "idle" (loops: a slow sway on its post) and "struck" (a blow rocks it back on
  its post and it springs to and fro, its head and arms lagging), as TrainingDummy plays them.
- The trophy pedestal (block trophy_pedestal): a plinth of polished black stone banded with gold, a gold plaque on its
  front; its blockstate turns it to face the hall's walk. Textures trophy_pedestal_side/_top/_plaque (16x16).
- Well Rested (mob_effect/well_rested, 18x18): a pale blue crescent moon with a small star.

Run from the repo root:   python3 tools/gen_home_assets.py               (everything)
                          python3 tools/gen_home_assets.py dummy         (only some: dummy, pedestal, icon)
Look at the dummy without the game:   python3 tools/preview_model.py training_dummy_main --anim struck --time 0.15
Needs: pillow, numpy.

Conventions (as the creature kit): 16 units are one block, the front of a model is -z, +x is the model's left. A
positive x rotation tilts a bone's top towards the front; a positive z rotation tilts its top towards +x.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature_skins as CS  # noqa: E402
import solid_skins as S  # noqa: E402
import gen_creatures as GC  # noqa: E402
import gen_enemy_assets as G  # noqa: E402
import gen_worldboss_assets as W  # noqa: E402
from gen_gate_assets import ease_in_out, window  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
BLOCK_TEX = os.path.join(ASSETS, "textures", "block")
EFFECTS = os.path.join(ASSETS, "textures", "mob_effect")

anim = G.anim
pose, timeline, lin, OneSkin = W.pose, W.timeline, W.lin, W.OneSkin
limb = GC.limb

DUMMY = "training_dummy"


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


# ============================================================================================= paint

def burlap(f, p, s):
    """Sacking stretched over straw: a coarse weave of crossing threads (each lit along its middle, dark holes where
    they cross), the straw pressing lumps into it, old stains, and on request a seam down the sides sewn with big
    stitches, a target painted on its front (s['target'] = (x, y, radius) on the north face) or a face sewn on
    (s['face'] = (x, y) of its middle, on the north face)."""
    r = s.get("r", "burlap")
    sd = s.get("seed", 400)
    tex = f.texel
    k = s.get("weave", 1.15)
    a, b = f.P * k, f.Q * k
    wx = np.abs(np.sin(a * math.pi))
    wy = np.abs(np.sin(b * math.pi))
    over_x = (np.floor(a) + np.floor(b)) % 2 == 0
    thread = np.where(over_x, wy, wx)
    n = CS.fbm3(f.X * 0.3, f.Y * 0.3, f.Z * 0.3, sd, 3)
    lumps = CS.fbm3(f.X * 0.55 + 7, f.Y * 0.55, f.Z * 0.55, sd + 1, 2)
    t = 0.46 + 0.16 * (thread - 0.5) + (n - 0.5) * 0.16 + (lumps - 0.5) * 0.12
    if f.wall:
        t = t + 0.05 * (1 - f.V) - 0.04
    elif f.face == "down":
        t = t - 0.14
    p.fill(r, t)
    p.over(r, 0.1, 0.55 * ((wx < 0.22) & (wy < 0.22)))
    # stains: darker blotches, most of them low down
    st = CS.fbm3(f.X * 0.4 + 3, f.Y * 0.4, f.Z * 0.4, sd + 2, 3)
    low = CS.sstep(f.bottom + (f.top - f.bottom) * 0.6, f.bottom, f.Y) if f.wall else 0.0
    p.shade(-0.12 * CS.sstep(0.58 - 0.12 * low, 0.72, st))
    if s.get("seam") and f.side and f.fw > 2.0:
        # the seam down the middle of each side, sewn over with long cross stitches
        mid = np.abs(f.P - f.fw / 2)
        p.shade(-0.14, mid < tex * 0.8)
        step = 0.9
        q = (f.Q % step) / step
        diag = np.abs((f.P - f.fw / 2) / 0.7 - (q * 2 - 1)) < 0.3
        p.over("thread", 0.42, diag & (mid < 0.7))
    tg = s.get("target")
    if tg and f.face == "north":
        cx, cy, rad = tg
        d = np.hypot(f.X - cx, f.Y - cy)
        ring = d / rad
        worn = CS.fbm3(f.X * 0.9 + 11, f.Y * 0.9, f.Z * 0.9, sd + 4, 3)
        paint_k = np.clip(0.85 - CS.sstep(0.55, 0.78, worn) * 0.7, 0, 1)
        band = np.floor(ring * 3.0)
        red = (band == 0) | (band == 2)
        cream = band == 1
        tone = 0.42 + 0.14 * (thread - 0.5)
        p.over("paint", tone + 0.06, red * (ring < 1.0) * paint_k)
        p.over("paint2", tone + 0.12, cream * paint_k)
        # the painted rim between the rings, a little darker
        edge = np.abs(ring * 3.0 - np.round(ring * 3.0)) < 0.09
        p.over("paint", 0.22, edge * (ring < 1.05) * (ring > 0.2) * paint_k * 0.8)
        # slashes and holes from old blows, a bit of straw showing in them
        cuts, al = CS.polylines(f, CS.face_seed(f, sd + 5), 4, length=(0.6, 1.4), steps=(1, 2), wander=0.4)
        hit = (cuts < tex * 0.7) & (d < rad * 1.15)
        p.over("straw", 0.5, hit)
        p.over(r, 0.12, (cuts >= tex * 0.7) & (cuts < tex * 1.4) & (d < rad * 1.15))
    fc = s.get("face")
    if fc and f.face == "north":
        cx, cy = fc
        # cross eyes, sewn in dark thread
        for ex in (cx - 1.35, cx + 1.35):
            dx, dy = f.X - ex, f.Y - (cy + 0.6)
            x1 = np.abs(dx - dy) < 0.24
            x2 = np.abs(dx + dy) < 0.24
            inside = (np.abs(dx) < 0.62) & (np.abs(dy) < 0.62)
            p.shade(-0.1, inside & ~(x1 | x2) & (np.hypot(dx, dy) < 0.75))
            p.over("thread", 0.2, (x1 | x2) & inside)
        # the sewn mouth: a line with stitches across it
        my = cy - 1.3
        mouth = (np.abs(f.Y - my) < 0.16) & (np.abs(f.X - cx) < 1.7)
        p.over("thread", 0.25, mouth)
        ticks = (np.abs(((f.X - cx + 1.7) % 0.68) - 0.34) < 0.12) & (np.abs(f.Y - my) < 0.42) & (np.abs(f.X - cx) < 1.6)
        p.over("thread", 0.3, ticks)
    if s.get("knot") and f.wall:
        # gathered and tied off: folds running into the tie
        across = f.P / max(f.fw, 1e-6)
        p.shade(-0.12 * (0.5 + 0.5 * np.sin(across * math.pi * 7)))


def rope(f, p, s):
    """A rope bound round: two strands twisting along it, each lit on its crown and dark in the groove between."""
    r = s.get("r", "rope")
    sd = s.get("seed", 420)
    along = f.P if f.wall else (f.X if s.get("long", "x") == "x" else f.Z)
    across = f.Q if f.wall else (f.Z if s.get("long", "x") == "x" else f.X)
    k = s.get("lay", 1.5)
    q = (along * k + across * k * 1.6) % 1.0
    crown = np.sin(q * math.pi)
    t = 0.3 + 0.38 * crown + (CS.vnoise3(f.X * 2, f.Y * 2, f.Z * 2, sd) - 0.5) * 0.1
    p.fill(r, t)
    p.over(r, 0.08, crown < 0.18)


def straw(f, p, s):
    """Straw: stalks of gold and pale tan lying along s['long'] (y: hanging down), a darker one here and there, the
    ends cut ragged (s['ragged']: which way the stalks end, 'down', 'up' or 'out' for every edge)."""
    sd = s.get("seed", 440)
    long = s.get("long", "y")
    tex = f.texel
    if f.wall:
        across = f.P if long == "y" else f.Q
        along = f.Q if long == "y" else f.P
    else:
        across, along = (f.X, f.Z) if long == "z" else (f.Z, f.X)
    stalk = np.floor(across / max(tex * 1.4, 0.18))
    h = CS.hash01(sd, stalk.astype(np.int64), 3)
    t = 0.42 + 0.3 * h + 0.12 * np.sin(along * 0.8 + h * 6)
    p.fill("straw", t)
    p.over("straw", 0.12, (h > 0.88) * 0.8)
    # the groove between stalks
    p.shade(-0.1, ((across / max(tex * 1.4, 0.18)) % 1.0) < 0.25)
    rag = s.get("ragged")
    if rag and f.wall:
        depth = s.get("depth", 1.2)
        ends = CS.hash01(sd + 7, stalk.astype(np.int64), 5) * depth
        if rag == "down":
            p.cut(f.Q < f.fh - ends)
        elif rag == "up":
            p.cut(f.Q > ends)
        elif rag == "out":
            p.cut((f.Q > ends * 0.6) & (f.Q < f.fh - ends * 0.6))


DUMMY_RAMPS = {
    "burlap": (164, 136, 92),
    "thread": (60, 40, 26),
    "straw": [(70, 52, 18), (142, 108, 40), (206, 166, 72), (232, 204, 120), (252, 236, 176)],
    "paint": (176, 44, 36),
    "paint2": (226, 212, 180),
    "post": (104, 74, 46),
    "plank": (120, 88, 56),
    "plate": (104, 108, 116),
    "trim": (168, 160, 140),
    "strap": (96, 64, 40),
}


# ============================================================================================= the dummy

def dummy():
    """The training dummy, 33 units tall (its hitbox is 0.8 x 2 blocks). Bones: root (the foot and the post), body (on
    the post: the stuffed body, its rope, the target, the straw under it), arms (the crossbar and its sleeves), head (the
    sack head, its tie and its tuft), armour_body (on body: breastplate, backplate, straps, pauldrons) and armour_head
    (on head: the kettle hat)."""
    B = OneSkin(DUMMY, 9700)
    box, c, col = B.box, B.c, B.col
    wood = dict(r="post", seed=12)
    sack = dict(fx=burlap, r="burlap", seed=20)
    tie = dict(fx=rope, r="rope", seed=30)
    iron = dict(r="plate", rivet="trim", seed=40, grime=True, armour=True)
    B.bone("root", None, [0, 0, 0], [
        # the foot: two heavy planks crossed, a block in the middle where the post stands, iron bands round it
        box(0, 1.0, 0, 15.0, 2.0, 3.2, "wood", r="plank", long="x", seed=10),
        box(0, 1.0, 0, 3.2, 2.0, 15.0, "wood", r="plank", long="z", seed=11),
        box(0, 3.0, 0, 5.4, 2.4, 5.4, "wood", **wood),
        box(0, 3.6, 0, 5.8, 0.7, 5.8, "plate", **dict(iron, rivets=False, armour=False)),
        # the post: a pole rising out of it into the body
        col(0, 0, 4.2, 14.0, 2.6, 2.6, "wood", **wood),
    ])
    straw_hang = []
    for i, (x, z, w, d) in enumerate([(-2.6, -1.4, 3.2, 1.4), (1.8, -1.6, 3.0, 1.2), (-0.4, 1.6, 4.4, 1.2), (3.4, 0.8, 1.4, 2.4),
                                      (-3.6, 0.6, 1.2, 2.4)]):
        # straw hanging out under the body in ragged bunches
        straw_hang.append(c([x - w / 2, 11.6, z - d / 2], [w, 2.0, d], "skin", fx=straw, ragged="down", depth=1.4, seed=60 + i,
                            skip=("up",)))
    B.bone("body", "root", [0, 12.0, 0], [
        # the post inside the body, showing between the straw
        col(0, 0, 12.0, 14.0, 2.6, 2.6, "wood", **wood),
        # the body: a barrel of sacking stuffed full, three boxes round its swell
        box(0, 18.6, 0, 9.0, 11.0, 5.6, "skin", seam=True, target=(0.0, 19.6, 3.3), **sack),
        box(0, 18.6, 0, 7.6, 10.2, 6.6, "skin", seam=True, target=(0.0, 19.6, 3.3), **dict(sack, seed=21)),
        box(0, 18.4, 0, 9.6, 8.6, 4.8, "skin", **dict(sack, seed=22)),
        # gathered at the bottom where the straw comes out
        box(0, 13.4, 0, 8.2, 0.9, 5.0, "skin", knot=True, **dict(sack, seed=23)),
        # rope bound round its waist and round its chest
        box(0, 15.2, 0, 9.4, 1.0, 6.0, "skin", **tie),
        box(0, 15.2, 0, 8.0, 1.0, 7.0, "skin", **dict(tie, seed=31)),
        box(0, 22.4, 0, 9.4, 0.9, 6.0, "skin", **dict(tie, seed=32)),
        box(0, 22.4, 0, 8.0, 0.9, 7.0, "skin", **dict(tie, seed=33)),
        *straw_hang,
    ])
    sleeves = []
    for sx in (-1, 1):
        sleeves += [
            # a stuffed sleeve on each end of the bar, tied off at its end, straw out of it
            box(sx * 7.0, 21.6, 0, 5.4, 3.4, 3.4, "skin", **dict(sack, seed=24 + sx)),
            box(sx * 9.3, 21.6, 0, 0.8, 3.8, 3.8, "skin", **dict(tie, seed=34 + sx)),
            box(sx * 10.5, 21.6, 0, 1.6, 2.8, 2.8, "skin", fx=straw, long="x", seed=64 + sx, skip=("east" if sx > 0 else "west",)),
        ]
    B.bone("arms", "body", [0, 21.6, 0], [
        # the crossbar through its shoulders
        box(0, 21.6, 0, 18.0, 1.8, 1.8, "wood", r="post", long="x", seed=13),
        *sleeves,
    ])
    B.bone("head", "body", [0, 24.0, 0], [
        # the neck, gathered and tied
        box(0, 24.7, 0, 3.4, 1.6, 3.4, "skin", knot=True, **dict(sack, seed=26)),
        box(0, 24.8, 0, 3.9, 0.8, 3.9, "skin", **dict(tie, seed=36)),
        # the head: a sack stuffed round, a face sewn on its front
        box(0, 28.6, 0, 6.6, 6.2, 6.0, "skin", **dict(sack, seed=27)),
        box(0, 28.6, 0, 5.8, 5.4, 6.8, "skin", face=(0.0, 28.6), **dict(sack, seed=28)),
        box(0, 28.6, 0, 7.2, 5.0, 5.2, "skin", **dict(sack, seed=29)),
        # tied off at the top, a tuft of straw out of it
        box(0, 32.1, 0, 3.0, 0.9, 3.0, "skin", knot=True, **dict(sack, seed=19)),
        box(0, 33.0, 0.2, 2.2, 1.4, 2.2, "skin", fx=straw, ragged="up", depth=0.8, seed=70, skip=("down",)),
    ])
    pauldrons = []
    for sx in (-1, 1):
        pauldrons += [
            c([sx * 6.8 - 2.4, 22.8, -2.4], [4.8, 1.4, 4.8], "plate", **dict(iron, seed=41 + sx), pivot=[sx * 6.8, 23.4, 0], rot=[0, 0, sx * 14]),
            c([sx * 6.8 - 2.2, 21.4, -2.6], [4.4, 1.6, 0.6], "plate", **dict(iron, seed=43 + sx, rivets=False), pivot=[sx * 6.8, 23.4, 0],
              rot=[0, 0, sx * 14]),
        ]
    B.bone("armour_body", "body", [0, 12.0, 0], [
        # a breastplate and a backplate on leather straps over its shoulders and round its sides
        box(0, 19.4, -3.75, 8.6, 7.6, 1.0, "plate", **dict(iron, engrave="trim")),
        box(0, 16.4, -3.6, 7.6, 1.8, 0.8, "plate", **dict(iron, seed=45, rivets=False)),
        box(0, 19.4, 3.75, 8.6, 7.6, 1.0, "plate", **dict(iron, seed=46)),
        box(-4.75, 19.0, 0, 0.6, 1.4, 7.0, "leather", r="strap", seed=47, armour=True),
        box(4.75, 19.0, 0, 0.6, 1.4, 7.0, "leather", r="strap", seed=48, armour=True),
        box(-2.8, 23.6, 0, 1.2, 0.6, 8.2, "leather", r="strap", seed=49, armour=True),
        box(2.8, 23.6, 0, 1.2, 0.6, 8.2, "leather", r="strap", seed=50, armour=True),
        *pauldrons,
    ])
    B.bone("armour_head", "head", [0, 24.0, 0], [
        # a kettle hat: a broad brim and a round crown
        box(0, 31.0, 0, 10.4, 0.5, 9.8, "plate", **dict(iron, seed=51, rivets=False)),
        box(0, 32.1, 0, 7.4, 1.8, 7.2, "plate", **dict(iron, seed=52)),
        box(0, 33.2, 0, 6.2, 0.8, 6.0, "plate", **dict(iron, seed=53, rivets=False)),
    ])
    GC.register(DUMMY, lambda aff: GC.pal("plain", (255, 220, 160), DUMMY_RAMPS, tinted=()), density=6.0)
    # the plain dummy wears no armour: its plates cast no shadow on the sacking under them
    S.PAINTERS[DUMMY]["prepare"] = lambda cubes, unit: CS.prepare_boxes(
        [(b, c) for b, c in cubes if not (c.get("_paint") or {}).get("armour")], unit)
    B.write(1.6, 2.4, 0.0)
    dummy_anims()


def dummy_anims():
    """TrainingDummy: "idle" loops on its main controller; "struck" (a blow) is triggered on "act"."""
    A = {}
    REST = pose(body=[0, 0, 0], arms=[0, 0, 0], head=[0, 0, 0])

    def P(base=None, **kw):
        return pose(base or REST, **kw)

    # idle (4 s): a slow sway on its post, the head nodding a little after it
    L = 4.0
    A["idle"] = anim(L, True, {
        "body": {"rotation": lambda t: [0.7 * math.sin(2 * math.pi * t / L), 0, 0.9 * math.sin(2 * math.pi * (t / L + 0.25))]},
        "head": {"rotation": lambda t: [0.8 * math.sin(2 * math.pi * (t / L - 0.12)), 0, 1.2 * math.sin(2 * math.pi * (t / L + 0.1))]},
        "arms": {"rotation": lambda t: [0, 0, 0.6 * math.sin(2 * math.pi * (t / L + 0.4))]},
    }, 0.05)

    # struck (0.9 s): rocked back on its post by the blow, it springs to and fro, dying away; the head and the arms lag
    L = 0.9

    def spring(t, amp, lag=0.0, freq=2.6):
        u = max(0.0, t - lag)
        return amp * math.exp(-u * 4.2) * math.sin(2 * math.pi * freq * u + math.pi / 2) * ease_out(window(t, lag, lag + 0.05))

    A["struck"] = anim(L, False, {
        "body": {"rotation": lambda t: [-12 * (spring(t, 1.0)), 0, 3 * spring(t, 1.0, freq=1.9)]},
        "head": {"rotation": lambda t: [-9 * spring(t, 1.0, lag=0.06), 0, -5 * spring(t, 1.0, lag=0.05, freq=2.2)]},
        "arms": {"rotation": lambda t: [0, 0, 7 * spring(t, 1.0, lag=0.04, freq=2.3)]},
    }, 0.02)
    GC.write_anims(DUMMY, A)


# ============================================================================================= the pedestal

def noise(n, seed, scale):
    rng = np.random.default_rng(seed)
    base = rng.random((n // scale + 2, n // scale + 2))
    img = Image.fromarray((base * 255).astype(np.uint8)).resize((n, n), Image.BICUBIC)
    return np.asarray(img, float) / 255.0


def stone(n, seed):
    """Polished black stone: near black with a faint grey veining and a fine grain."""
    v = 0.6 * noise(n, seed, 4) + 0.4 * noise(n, seed + 1, 2)
    vein = np.abs(np.sin((np.arange(n)[None, :] * 0.9 + np.arange(n)[:, None] * 0.45 + v * 7.0)))
    t = 0.16 + 0.07 * v + 0.06 * (vein < 0.12)
    rgb = np.stack([t * 120 + 6, t * 118 + 6, t * 132 + 10], axis=-1)
    return rgb


GOLD = [(70, 40, 8), (150, 104, 30), (214, 168, 70), (246, 214, 128), (255, 244, 200)]


def gold(t):
    t = float(max(0.0, min(1.0, t)))
    i = min(int(t * 4), 3)
    k = t * 4 - i
    a, b = GOLD[i], GOLD[i + 1]
    return tuple(int(a[j] + (b[j] - a[j]) * k) for j in range(3))


def pedestal():
    """The pedestal's three textures, its block model and its blockstate."""
    n = 16
    os.makedirs(BLOCK_TEX, exist_ok=True)
    # the side: rows 0-2 the cap's side (a gold rim on top, a gold line under it), 3-12 the column (fluted), 13-15 the
    # foot's side (a gold band on its top)
    side = stone(n, 3)
    for x in range(n):
        side[0, x] = gold(0.78 if x % 4 else 0.92)
        side[2, x] = gold(0.45)
        side[13, x] = gold(0.7 if x % 3 else 0.86)
        for y in range(3, 13):
            # flutes: a lit edge and a dark hollow every 2.5 texels
            f = (x % 3)
            side[y, x] = side[y, x] * (1.18 if f == 0 else 0.82 if f == 2 else 1.0)
    side[15, :] = side[15, :] * 0.7
    Image.fromarray(np.clip(side, 0, 255).astype(np.uint8)).save(os.path.join(BLOCK_TEX, "trophy_pedestal_side.png"))
    # the top: black, a gold border just inside its edge and a ring where the trophy stands
    top = stone(n, 7)
    for y in range(n):
        for x in range(n):
            if (x in (1, 14) and 1 <= y <= 14) or (y in (1, 14) and 1 <= x <= 14):
                top[y, x] = gold(0.62)
            d = math.hypot(x - 7.5, y - 7.5)
            if 4.6 < d < 5.4:
                top[y, x] = gold(0.5)
    Image.fromarray(np.clip(top, 0, 255).astype(np.uint8)).save(os.path.join(BLOCK_TEX, "trophy_pedestal_top.png"))
    # the plaque: gold, a raised rim, lines of an engraving in its middle
    pl = np.zeros((n, n, 3))
    for y in range(n):
        for x in range(n):
            t = 0.58 + 0.12 * math.sin(x * 0.7 + y * 0.3)
            if x in (0, 15) or y in (0, 15):
                t = 0.86 if (x == 0 or y == 0) else 0.32
            elif x in (1, 14) or y in (1, 14):
                t = 0.45
            elif y in (5, 7, 9, 11) and 3 <= x <= 12 and (x + y) % 5 != 0:
                t = 0.34
            pl[y, x] = gold(t)
    Image.fromarray(pl.astype(np.uint8)).save(os.path.join(BLOCK_TEX, "trophy_pedestal_plaque.png"))

    def faces(x0, y0, z0, x1, y1, z1, side_tex="#side", up="#top", down="#top"):
        f = {}
        for d in ("north", "south", "east", "west"):
            if d in ("north", "south"):
                u0, u1 = x0, x1
            else:
                u0, u1 = z0, z1
            f[d] = {"uv": [u0, 16 - y1, u1, 16 - y0], "texture": side_tex, "cullface": None}
            f[d].pop("cullface")
        f["up"] = {"uv": [x0, z0, x1, z1], "texture": up}
        f["down"] = {"uv": [x0, z0, x1, z1], "texture": down}
        if y0 == 0:
            f["down"]["cullface"] = "down"
        return f

    model = {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": "beyondsolo:block/trophy_pedestal_side",
            "side": "beyondsolo:block/trophy_pedestal_side",
            "top": "beyondsolo:block/trophy_pedestal_top",
            "plaque": "beyondsolo:block/trophy_pedestal_plaque",
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 3, 15], "faces": faces(1, 0, 1, 15, 3, 15)},
            {"from": [3, 3, 3], "to": [13, 12, 13], "faces": faces(3, 3, 3, 13, 12, 13)},
            {"from": [1.5, 12, 1.5], "to": [14.5, 15, 14.5], "faces": faces(1.5, 12, 1.5, 14.5, 15, 14.5)},
            # the plaque on its front
            {"from": [5, 5.5, 2.4], "to": [11, 9.5, 3], "faces": {
                "north": {"uv": [2, 4, 14, 12], "texture": "#plaque"},
                "east": {"uv": [0, 4, 1, 12], "texture": "#plaque"},
                "west": {"uv": [15, 4, 16, 12], "texture": "#plaque"},
                "up": {"uv": [2, 0, 14, 1], "texture": "#plaque"},
                "down": {"uv": [2, 15, 14, 16], "texture": "#plaque"},
            }},
        ],
    }
    models = os.path.join(ASSETS, "models", "block")
    os.makedirs(models, exist_ok=True)
    with open(os.path.join(models, "trophy_pedestal.json"), "w") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")
    states = {"variants": {f"facing={d}": ({"model": "beyondsolo:block/trophy_pedestal", "y": y} if y else
                                            {"model": "beyondsolo:block/trophy_pedestal"})
                           for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}
    bs = os.path.join(ASSETS, "blockstates")
    with open(os.path.join(bs, "trophy_pedestal.json"), "w") as fh:
        json.dump(states, fh, indent=2)
        fh.write("\n")


# ============================================================================================= the icon

def rested_icon(n=18):
    """Well Rested: a pale blue crescent moon with a small four-pointed star by it, on a dark outline."""
    ss = 8
    w = n * ss
    im = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # the crescent: a disc with a second disc taken out of it
    cx, cy, r = 7.6 * ss, 9.6 * ss, 6.4 * ss
    moon = Image.new("L", (w, w), 0)
    md = ImageDraw.Draw(moon)
    md.ellipse([cx - r, cy - r, cx + r, cy + r], fill=255)
    md.ellipse([cx - r + 3.6 * ss, cy - r - 2.2 * ss, cx + r + 3.6 * ss, cy + r - 2.2 * ss], fill=0)
    body = Image.new("RGBA", (w, w), (176, 222, 255, 255))
    im.paste(body, (0, 0), moon)
    # its lit edge
    edge = moon.filter(ImageFilter.MinFilter(9))
    rim = Image.eval(moon, lambda v: v) .point(lambda v: v)
    inner = Image.new("RGBA", (w, w), (122, 178, 236, 255))
    im.paste(inner, (0, 0), edge)
    # a star
    sx, sy = 13.4 * ss, 4.4 * ss
    s1, s2 = 3.4 * ss, 1.0 * ss
    d.polygon([(sx, sy - s1), (sx + s2, sy - s2), (sx + s1, sy), (sx + s2, sy + s2), (sx, sy + s1), (sx - s2, sy + s2), (sx - s1, sy),
               (sx - s2, sy - s2)], fill=(236, 248, 255, 255))
    del rim
    im = im.resize((n, n), Image.LANCZOS)
    a = im.split()[3].point(lambda v: 255 if v > 50 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (n, n), (14, 26, 52, 255)), (0, 0), grown)
    out.alpha_composite(im)
    os.makedirs(EFFECTS, exist_ok=True)
    out.save(os.path.join(EFFECTS, "well_rested.png"))
    return out


# ============================================================================================= main

def main():
    want = set(sys.argv[1:])
    makers = {"dummy": dummy, "pedestal": pedestal, "icon": rested_icon}
    for key, fn in makers.items():
        if not want or key in want:
            print(key)
            fn()


if __name__ == "__main__":
    main()
