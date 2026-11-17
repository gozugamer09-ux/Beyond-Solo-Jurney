#!/usr/bin/env python3
"""
The gate's creatures (MonsterKind): models, animations and skins painted on the model, in the dark-fantasy comic look
(dark backs, pale bellies, lit tops, cold shadows, glowing eyes and marks). Every creature has a texture per element
affinity (plain, flame, frost, venom, storm, shade, blood): the same pattern in that affinity's colours plus its own
marks (rime, ember cracks, venom blotches, storm veins, shade smoke, blood veins). See creature_skins.py for the
materials and the marks.

The gloomfang (a hound) is still made by gen_enemy_assets.py, as are the bosses.

Run from the repo root:   python3 tools/gen_creatures.py                    (all of them)
                          python3 tools/gen_creatures.py gutterkin coilfang  (only some)
Look at one without the game:  python3 tools/preview_model.py gutterkin_plain

Conventions (as gen_enemy_assets.py): 16 units are one block, the front of a model is -z, +x is the creature's left.
A positive x rotation tilts a bone's top towards the front (a hanging arm swings back, a jaw or a head that points
forward dips down); a positive z rotation tilts its top towards +x. Animated rotations add to a bone's rest rotation;
positions and scales replace.
"""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import gen_enemy_assets as G  # noqa: E402
from gen_gate_assets import TEX, ease_in, ease_in_out, ease_out_back, window  # noqa: E402

import creature_skins as CS  # noqa: E402
import numpy as np  # noqa: E402
from creature_skins import AFFS  # noqa: E402

anim, rot, wave = G.anim, G.rot, G.wave


def sin01(t, a, b):
    """0 -> 1 -> 0 over [a, b] (a smooth bump)."""
    return math.sin(math.pi * window(t, a, b))


def hold(t, a, b, c, d):
    """Eases in over [a, b], holds, eases out over [c, d]."""
    return ease_in_out(window(t, a, b)) * (1 - ease_in_out(window(t, c, d)))


def snap(t, a, b, c, d):
    """A fast strike: in over [a, b] (accelerating), held, back over [c, d]."""
    return ease_in(window(t, a, b)) * (1 - ease_in_out(window(t, c, d)))


# --------------------------------------------------------------------------------------------- building

class Body:
    """A creature's model while it is being built. Every cube says what it is made of and how it is painted (the
    material and its parameters, read by creature_skins.painter)."""

    def __init__(self, name, seed):
        self.name = name
        self.uvs, self.size = G.save_textures(name, [(a, a) for a in AFFS], lambda rng, a: {"aff": a}, seed)
        self.m = G.Model(self.uvs)

    def c(self, o, s, mat, pivot=None, rot=None, skip=None, inflate=None, **spec):
        made = self.m.c(list(o), list(s), mat, skip=skip, pivot=pivot, rotation=rot, inflate=inflate)
        made["_paint"] = dict(m=mat, **spec)
        return made

    def box(self, cx, cy, cz, w, h, d, mat, **kw):
        """A cube centred on (cx, cy, cz)."""
        return self.c([cx - w / 2, cy - h / 2, cz - d / 2], [w, h, d], mat, **kw)

    def col(self, cx, cz, y0, y1, w, d, mat, **kw):
        """An upright cube from y0 to y1, centred on (cx, cz)."""
        return self.c([cx - w / 2, y0, cz - d / 2], [w, y1 - y0, d], mat, **kw)

    def bar(self, cx, cy, z0, z1, w, h, mat, **kw):
        """A cube along z from z0 to z1, centred on (cx, cy)."""
        return self.c([cx - w / 2, cy - h / 2, min(z0, z1)], [w, h, abs(z1 - z0)], mat, **kw)

    def bone(self, name, parent, pivot, cubes=(), rot=None):
        return self.m.bone(name, parent, pivot, [c for c in cubes if c], rot)

    def write(self, bounds_w, bounds_h, offset_y):
        G.write_geo(self.name, self.m.geo(self.name, self.size, bounds_w, bounds_h, offset_y))


def mirror_x(o, s, sx):
    """The origin of a cube mirrored to the side sx (a cube designed on the +x side)."""
    return [o[0] if sx > 0 else -o[0] - s[0], o[1], o[2]]


# --------------------------------------------------------------------------------------------- palettes

GREY = (128, 128, 128)


def shade_of(c, k, toward=None, amt=0.0):
    c = tuple(v * k for v in c)
    return CS.mix(c, toward, amt) if toward else c


def pal(aff, glow, ramps, tinted=None, tint_k=0.24, extra=None):
    """A creature's palette for one affinity. ramps: {name: mid colour}; a name with no '_d' (dark back) or '_l'
    (pale belly) companion gets them made from it. Every body ramp is pulled towards the affinity unless tinted says
    which ones."""
    full = {}
    for k, v in ramps.items():
        full[k] = v
    for k, v in list(ramps.items()):
        if isinstance(v, list) or k.endswith("_d") or k.endswith("_l"):
            continue
        full.setdefault(k + "_d", shade_of(v, 0.58, (30, 34, 52), 0.12))
        full.setdefault(k + "_l", CS.mix(v, (226, 214, 190), 0.34))
    if tinted is None:
        tinted = [k for k in full if not k.startswith(("bone", "ivory", "steel", "iron", "gold", "wood", "leather", "rope"))]
    return CS.palette(aff, glow, full, tinted=tuple(tinted), tint_k=tint_k, extra=extra)


# --------------------------------------------------------------------------------------------- features

def _face_coords(f, face):
    """(a, b): across and up on a face, in model units, as the face is seen from outside (a grows to the viewer's
    right on the front, so a design's +x is on the left of the picture)."""
    if face == "north":
        return f.X, f.Y
    if face == "south":
        return -f.X, f.Y
    if face == "west":
        return f.Z, f.Y
    if face == "east":
        return -f.Z, f.Y
    if face == "up":
        return f.X, -f.Z
    return f.X, f.Z


def eye(p, f, x, y, r=(0.8, 0.5), face="north", slant=0.0, pupil="slit", lid=1.0, glint=True, ramp="glow"):
    """A glowing eye centred at (x, y) on a face (x is z on the side faces): an almond of light in a dark socket, a
    pupil, a glint. slant lifts the eye's +x corner (angry eyes lift the outer corners: give the +x eye a positive
    slant and the -x eye a negative one)."""
    if f.face != face:
        return
    if face in ("up", "down"):
        a, b = f.X - x, (-f.Z if face == "up" else f.Z) - y
    else:
        a = (f.X if face in ("north", "south") else f.Z) - x
        b = f.Y - y
    c, s_ = math.cos(slant), math.sin(slant)
    xa, xb = a * c + b * s_, -a * s_ + b * c
    ra, rb = r
    d = (abs(xa) / ra) ** 2 + (abs(xb) / rb) ** 2
    if lid > 0:
        p.over("black", 0.04, (d < 1.0 + 1.3 * lid) & (xb > -rb * 1.2))
        p.over("black", 0.16, (d < 1.0 + 0.6 * lid) & (xb <= -rb * 1.2))
    p.glow(ramp, 0.34 + 0.36 * np.clip(1 - d, 0, 1) ** 0.6 if pupil != "white" else 0.85, d < 1.0)
    tex = f.texel
    if pupil == "slit":
        p.over("black", 0.0, (d < 1.0) & (abs(xa) < max(ra * 0.15, tex * 0.55)) & (abs(xb) < rb * 0.86))
    elif pupil == "dot":
        p.over("black", 0.0, (d < 1.0) & (((xa / ra) ** 2 + (xb / rb) ** 2) < 0.16))
    if glint:
        p.glow(ramp, 0.98, (abs(xa + ra * 0.38) < tex * 0.7) & (abs(xb - rb * 0.3) < tex * 0.7) & (d < 1.0))


def eyes(x, y, r, slant=0.0, **kw):
    """A pair of eyes at +-x."""
    return [dict(x=x, y=y, r=r, slant=slant, **kw), dict(x=-x, y=y, r=r, slant=-slant, **kw)]


def socket(p, f, x, y, r, face="north", core=0.34, flame=False, slant=0.0):
    """An empty eye socket, an almond of dark slanted like an eye (give the +x one a positive slant), with a point of
    cold light deep in it."""
    if f.face != face:
        return
    a = (f.X if face in ("north", "south") else f.Z) - x
    b = f.Y - y
    c, s_ = math.cos(slant), math.sin(slant)
    xa, xb = a * c + b * s_, -a * s_ + b * c
    d = ((xa / r[0]) ** 2 + (xb / r[1]) ** 2) ** 0.5
    p.over("black", 0.0 + 0.12 * d, d < 1.0)
    p.over("black", 0.24, (d >= 1.0) & (d < 1.3))
    cd = ((xa / (r[0] * core)) ** 2 + ((xb + r[1] * 0.1) / (r[1] * core)) ** 2) ** 0.5
    p.glow("glow", 0.36, (cd < 1.7) & (cd >= 1.0) & (d < 1.0))
    p.glow("glow", 0.8, cd < 1.0)
    if flame:
        up = (b > 0) & (abs(a) < r[0] * 0.45 * (1 - b / (r[1] * 2.4))) & (b < r[1] * 2.4) & (d >= 0.95)
        p.glow("glow", 0.62, up)


def sockets(x, y, r, slant=0.3, **kw):
    return [dict(x=x, y=y, r=r, slant=slant, **kw), dict(x=-x, y=y, r=r, slant=-slant, **kw)]


def curl(B, base, sx, segs, mat, z_step=0.0, **kw):
    """A horn curling out from base on the side sx: segs [(angle from upright in degrees, length, thickness)], each
    segment starting where the last one ended."""
    x, y, z = base
    out = []
    for ang, ln, w in segs:
        th = math.radians(ang)
        out.append(B.c([x - w / 2, y, z - w / 2], [w, ln, w], mat, pivot=[x, y, z], rot=[0, 0, sx * ang], **kw))
        x, y, z = x + sx * math.sin(th) * ln, y + math.cos(th) * ln, z + z_step
    return out


def grin(p, f, x0, x1, y, h, face="north", curve=0.0, n=6, teeth=True, glow=False, fangs=True):
    """A mouth across a face from x0 to x1 at height y, h tall, its corners lifted by curve: a dark gap with rows of
    teeth from both lips (or a glowing maw)."""
    if f.face != face:
        return
    a = f.X if face in ("north", "south") else f.Z
    k = (a - x0) / (x1 - x0)
    inside_x = (k > 0) & (k < 1)
    e = 2 * k - 1
    yc = y + curve * e ** 2
    half = h / 2 * (1 - 0.55 * e ** 6)
    inside = inside_x & (abs(f.Y - yc) < half)
    p.over("mouth", 0.18, inside)
    p.over("black", 0.1, inside_x & (abs(f.Y - yc) < half + f.texel) & ~inside)
    if glow:
        p.glow("glow", 0.55 + 0.4 * (1 - abs(f.Y - yc) / max(h / 2, 1e-6)), inside & (abs(f.Y - yc) < half * 0.55))
    if teeth:
        span = (x1 - x0) / n
        for i in range(n):
            ci = x0 + (i + 0.5) * span
            big = fangs and (i == 1 or i == n - 2)
            tl = half * (1.25 if big else 0.8)
            w = span * (0.62 if big else 0.5)
            dx = abs(a - ci) / w
            top = inside & (f.Y > yc + half - tl * (1 - dx)) & (dx < 1)
            bot = inside & (f.Y < yc - half + tl * 0.85 * (1 - dx)) & (dx < 1) & ((i % 2) == (0 if not big else 1) or big)
            p.over("ivory", 0.62 + 0.3 * (1 - dx), top)
            p.over("ivory", 0.5 + 0.3 * (1 - dx), bot)


def maw(p, f, x, y, r, n=8, face="north", glow=True):
    """A round mouth on a face: a ring of teeth pointing in from the rim around a dark throat that glows deep down."""
    if f.face != face:
        return
    a, b = _face_coords(f, face)
    if face == "south":
        x = -x
    d = np.hypot(a - x, b - y) / r
    ang = np.arctan2(b - y, a - x)
    inside = d < 1.0
    p.over("mouth", 0.1 + 0.26 * d, inside)
    p.over("black", 0.06, (d >= 1.0) & (d < 1.0 + 1.2 * f.texel / r))
    if glow:
        p.glow("glow", np.clip(0.98 - 0.9 * d, 0.3, 1.0), d < 0.5)
    k = (ang / (2 * np.pi) * n) % 1.0
    tri = np.abs(k - 0.5) * 2                               # 0 in the middle of a tooth's root, 1 between teeth
    tooth = inside & (tri < 0.72 * (d - 0.42) / 0.58) & (d < 0.94)
    p.over("mouth", 0.42, inside & (d >= 0.88))
    p.over("ivory", 0.4 + 0.36 * (1 - tri) + 0.12 * (1 - d), tooth)


def features(f, p, s):
    """The features a cube's paint spec asks for: eyes, sockets, a mouth, lines and runes of light, war paint."""
    for e in s.get("eyes", ()):
        eye(p, f, **e)
    for k in s.get("sockets", ()):
        socket(p, f, **k)
    if "grin" in s:
        grin(p, f, **s["grin"])
    if "maw" in s:
        maw(p, f, **s["maw"])
    for ln in s.get("lines", ()):
        faces = ln.get("faces")
        if faces and f.face not in faces:
            continue
        CS.line_glow(p, f, ln["pts"], ln.get("w", 0.35), halo=ln.get("halo", 0.8), bright=ln.get("bright", 1.0))
    if isinstance(s.get("runes"), dict):
        r = s["runes"]
        if f.face in r.get("faces", ("north", "south", "east", "west")):
            CS.runes(p, f, r["region"], r.get("seed", 5), r.get("cell", 1.2), r.get("density", 0.55))
    if "warpaint" in s:
        w = s["warpaint"]
        if f.face in w.get("faces", ("north", "south", "east", "west")):
            coord = f.Y + (f.Z if f.side else f.X) * w.get("lean", 0.6)
            CS.stripes(p, f, w.get("ramp", "paint"), coord, w.get("period", 2.2), w.get("width", 0.7), w.get("seed", 7), w.get("tone", 0.45))
    if "spots" in s:
        sp = s["spots"]
        n = CS.vnoise3(f.X * sp.get("k", 1.6), f.Y * sp.get("k", 1.6), f.Z * sp.get("k", 1.6), sp.get("seed", 9))
        if sp.get("glow", False):
            p.glow("glow", 0.7, n > sp.get("cut", 0.8))
        else:
            p.over(sp.get("ramp", "black"), sp.get("tone", 0.3), n > sp.get("cut", 0.78))
    if "shadow_top" in s and f.wall:
        y0, y1, k = s["shadow_top"]
        p.shade(-k * CS.sstep(y0, y1, f.Y))
    if f.face in s.get("dark_faces", ()):
        p.over("void", 0.3 + 0.25 * f.V if f.wall else 0.3, 1.0)
    for h in s.get("holes", ()):
        if f.face == h.get("face", "north"):
            a = (f.X if f.face in ("north", "south") else f.Z) - h["x"]
            d = (a / h["r"][0]) ** 2 + ((f.Y - h["y"]) / h["r"][1]) ** 2
            p.over("black", 0.04, d < 1.0)
    for ln in s.get("cuts", ()):
        if f.face not in ln.get("faces", ("north", "south", "east", "west")):
            continue
        a = CS.across(f) if f.wall else f.X
        b = f.Y if f.wall else f.Z
        dmin = np.full(f.X.shape, 99.0)
        for (a0, b0), (a1, b1) in zip(ln["pts"], ln["pts"][1:]):
            d, _ = CS.seg_dist(a, b, a0, b0, a1, b1)
            dmin = np.minimum(dmin, d)
        p.over(ln.get("ramp", "black"), ln.get("tone", 0.08), dmin < ln.get("w", 0.3) / 2)
    if "rays" in s and f.face in ("west", "east"):
        # a fin: dark rays fanning from its root
        r = s["rays"]
        ang = np.arctan2(f.Y - r["y"], (f.Z - r["z"]) * r.get("dir", 1))
        k = (ang * r.get("n", 7) / math.pi) % 1.0
        p.over(r.get("ramp", "black"), 0.2, (k < 0.16))
    if "fx" in s:
        s["fx"](f, p, s)


def register(name, palette_fn, density=5.0, ground=0.0, levels=None):
    CS.register(name, palette_fn, features=features, density=density, ground=ground, levels=levels)


# --------------------------------------------------------------------------------------------- shared motion

def biped_walk(L, legs=("right_leg", "left_leg"), shins=None, arms=("right_arm", "left_arm"), swing=30, arm_swing=22,
               knee=28, bob=0.5, body="body", lean=0.0, arm_rest=(0, 0)):
    """A walk for two legs (each with an optional shin that bends as the foot lifts) and two swinging arms."""
    out = {}
    for i, (leg, sx) in enumerate(zip(legs, (-1, 1))):
        ph = 0.0 if i == 0 else 0.5
        out[leg] = rot(lambda t, ph=ph: [-swing * wave(t, L, ph), 0, 0])
        if shins:
            out[shins[i]] = rot(lambda t, ph=ph: [knee * max(0.0, wave(t, L, ph + 0.25)), 0, 0])
    for i, arm in enumerate(arms or ()):
        ph = 0.5 if i == 0 else 0.0
        out[arm] = rot(lambda t, ph=ph, i=i: [-arm_swing * wave(t, L, ph) + arm_rest[i], 0, 0])
    if body:
        out[body] = {"position": lambda t: [0, -bob * abs(wave(t, L)), 0], "rotation": lambda t: [lean, 2.5 * wave(t, L), 0]}
    return out


def fall_over(L, k=1.0, side=1, root="root", drop=0.0):
    """A body toppling onto its side, the usual death."""
    return {root: {"rotation": lambda t: [0, 0, side * 88 * ease_in(window(t, 0.15 * k, 0.6 * k))],
                   "position": lambda t: [0, -drop * ease_in(window(t, 0.15 * k, 0.6 * k)), 0]}}


def write_anims(name, A):
    G.write_anims(name, A)


# ============================================================================================= the creatures

# --------------------------------------------------------------------------------------------- gutterkin

def gutterkin():
    """A goblin-like raider no taller than a child: bow legs, a hunched back, a big head with long ears, a hooked
    nose, a wide grin of crooked teeth, yellow eyes. A notched cleaver, a rag at the hips, bone trinkets."""
    name = "gutterkin"
    B = Body(name, 1500)
    c = B.c
    skin = dict(seed=3)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 8, 0.5], [
        c([-3, 7, -1.6], [6, 3, 4], "skin", zone="back", **skin),
        c([-3.3, 8.6, -1.9], [6.6, 1.2, 4.6], "leather", stitch=False),
        # the rag: a strip in front and one behind, torn at the hem
        c([-2.2, 3.4, -2.1], [4.4, 5.4, 0.4], "cloth", hem=1.4, seed=61, skip=("up", "down")),
        c([-2.6, 3.8, 2.3], [5.2, 5.0, 0.4], "cloth", hem=1.6, seed=62, skip=("up", "down")),
        # trinkets on the belt: a little skull and two bones
        c([2.6, 7.4, -2.4], [1.4, 1.4, 1.2], "bone", seed=83),
        c([-3.2, 6.6, -2.2], [0.6, 2.2, 0.6], "bone", seed=84),
        c([-2.5, 6.9, -2.3], [0.6, 1.8, 0.6], "bone", seed=85),
    ])
    B.bone("chest", "hips", [0, 9.5, 0.5], [
        c([-3.6, 9.5, -2.4], [7.2, 6.5, 5.2], "skin", zone="front", bib_w=0.26, **skin),
        # a ridge of knobs down the spine
        c([-0.6, 14.4, 2.4], [1.2, 1.2, 1.0], "horn", tip="+z", r="horn"),
        c([-0.5, 12.2, 2.6], [1.0, 1.1, 0.9], "horn", tip="+z", r="horn"),
        # a strap across the chest
        c([-3.8, 12.0, -2.7], [7.6, 1.0, 0.4], "leather", pivot=[0, 12.5, -2.5], rot=[0, 0, 32], stitch=False),
        # a necklace of teeth
        c([-2.6, 14.8, -2.75], [5.2, 0.5, 0.35], "leather", stitch=False),
        *[c([-2.2 + i * 1.1, 13.6 + (0.3 if i in (1, 3) else 0.0), -2.85], [0.5, 1.2 - (0.3 if i in (1, 3) else 0.0), 0.4], "bone", seed=86 + i)
          for i in range(5)],
    ], rot=[24, 0, 0])
    head = [
        c([-3.6, 15.6, -7.2], [7.2, 5.4, 6.0], "skin", zone="back", split=(0.7, 0.18),
          eyes=eyes(1.7, 18.9, (1.05, 0.6), slant=0.42, pupil="slit"),
          **skin),
        # a hooked nose
        c([-0.75, 16.6, -8.6], [1.5, 2.6, 1.6], "skin", pivot=[0, 18, -7.4], rot=[-14, 0, 0], seed=5),
        # upper teeth under the lip
        c([-2.8, 15.0, -7.35], [5.6, 0.9, 0.4], "teeth", n=6, fangs=1, up=True, skip=("up", "down", "south")),
    ]
    for sx in (-1, 1):
        # brows that slant down to the nose
        head.append(c(mirror_x([0.3, 19.7, -7.7], [3.4, 1.0, 1.4], sx), [3.4, 1.0, 1.4], "skin", r="skin_d", pivot=[sx * 0.4, 20.1, -7.0],
                      rot=[0, 0, sx * 14], seed=4, skip=("south",)))
        # long ears: out, back and up, narrowing to a drooping point
        piv, turn = [sx * 3.5, 18.4, -4.2], [8, sx * -38, sx * 16]
        head += [
            c(mirror_x([3.3, 17.3, -4.7], [3.0, 2.6, 0.9], sx), [3.0, 2.6, 0.9], "skin", pivot=piv, rot=turn, zone="limb", **skin),
            c(mirror_x([6.2, 17.6, -4.6], [2.6, 2.0, 0.7], sx), [2.6, 2.0, 0.7], "skin", pivot=piv, rot=turn, zone="limb", **skin),
            c(mirror_x([8.7, 17.6, -4.5], [2.4, 1.2, 0.5], sx), [2.4, 1.2, 0.5], "skin", pivot=piv, rot=turn, zone="limb", **skin),
            # warts
            c([sx * 2.6 - 0.35, 17.4, -7.45], [0.7, 0.7, 0.4], "skin", r="skin_d", skip=("south",)),
        ]
    B.bone("head", "chest", [0, 15.4, -1.8], head, rot=[-22, 0, 0])
    B.bone("jaw", "head", [0, 16.0, -3.0], [
        c([-3.0, 13.8, -7.0], [6.0, 1.8, 4.6], "skin", zone="back", split=(0.8, 0.5), **skin),
        c([-2.6, 15.55, -6.9], [5.2, 0.8, 0.4], "teeth", n=5, fangs=1, up=False, skip=("up", "down", "south")),
        c([-2.6, 15.0, -6.6], [5.2, 0.6, 3.6], "mouth"),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        # arms long enough to reach the knees
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 14.6, 0.4], [
            c(mirror_x([3.4, 9.8, -0.6], [2.4, 5.6, 2.4], sx), [2.4, 5.6, 2.4], "skin", zone="limb", **skin),
            c(mirror_x([3.2, 13.2, -0.9], [2.9, 2.2, 3.0], sx), [2.9, 2.2, 3.0], "leather", stitch=False) if sx < 0 else None,
        ], rot=[-30, 0, sx * -6])
        hand = [
            c(mirror_x([3.5, 5.2, -0.4], [2.1, 5.0, 2.1], sx), [2.1, 5.0, 2.1], "skin", zone="limb", **skin),
            c(mirror_x([3.3, 3.4, -0.7], [2.5, 2.0, 2.6], sx), [2.5, 2.0, 2.6], "skin", **skin),
            *[c([sx * 3.5 + (0.0 if sx > 0 else -0.5) + i * 0.8 * sx, 2.6, -0.7], [0.5, 1.0, 0.5], "horn", tip="-y", r="horn") for i in range(3)],
        ]
        if sx < 0:
            # the cleaver: a notched slab of iron on a short haft, held blade forward and down
            hand += [
                c([-5.1, 2.9, -3.6], [0.9, 0.9, 4.4], "wood", long="z"),
                c([-5.2, 0.0, -9.6], [0.6, 4.4, 6.4], "metal", r="blade", edge="down", nicks=True, skip=()),
                c([-5.25, 3.6, -9.2], [0.7, 0.8, 5.8], "metal", r="iron"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 10.0, 0.6], hand, rot=[-34, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 8.0, 0.4], [
            c(mirror_x([0.9, 4.2, -1.0], [2.6, 4.2, 2.8], sx), [2.6, 4.2, 2.8], "skin", zone="limb", **skin),
        ], rot=[-16, 0, sx * -8])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.2, 4.6, 0.4], [
            c(mirror_x([1.2, 0.9, -0.4], [2.1, 4.0, 2.1], sx), [2.1, 4.0, 2.1], "skin", zone="limb", **skin),
            c(mirror_x([0.9, 0.0, -3.4], [2.7, 1.1, 4.2], sx), [2.7, 1.1, 4.2], "skin", r="skin_d", seed=6),
            *[c([sx * (1.0 + i * 0.85) - (0.5 if sx < 0 else 0), 0.0, -4.0], [0.5, 0.7, 0.8], "horn", tip="-z", r="horn") for i in range(3)],
        ], rot=[22, 0, sx * 6])
    register(name, lambda aff: pal(aff, (255, 196, 58), {
        "skin": (104, 114, 78), "skin_d": (52, 62, 44), "skin_l": (156, 152, 108),
        "cloth": (104, 54, 40), "hair": (36, 30, 26), "horn": (86, 74, 56),
        "blade": [(16, 12, 10), (52, 40, 34), (98, 84, 74), (156, 146, 136), (214, 210, 204)],
        "bone": (204, 190, 160), "paint": (176, 36, 30),
    }), density=6.0)
    B.write(1.2, 1.6, 0.6)

    A = {}
    L = 2.2
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [3 * wave(t, L, 0.2), 14 * (1 if (t % 2.2) > 1.1 else -1) * window(t % 1.1, 0, 0.12), 0]),
        "jaw": rot(lambda t: [4 + 4 * max(0, wave(t, L / 2)), 0, 0]),
        "right_arm": rot(lambda t: [3 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-3 * wave(t, L), 0, 0]),
        "left_hand": rot(lambda t: [6 * wave(t, L, 0.3), 0, 0]),
    }, 0.05)
    L = 0.6
    A["walk"] = anim(L, True, {
        **biped_walk(L, legs=("right_leg", "left_leg"), shins=("right_shin", "left_shin"), arms=("right_arm", "left_arm"),
                     swing=34, arm_swing=26, knee=34, bob=0.6, body="hips"),
        "chest": rot(lambda t: [6, 4 * wave(t, L), 0]),
        "head": rot(lambda t: [-5 + 3 * wave(t, L / 2), -4 * wave(t, L), 0]),
        "jaw": rot(lambda t: [10, 0, 0]),
    }, 0.025)
    # slash (0.7 s): the cleaver up and over, hacking down at 0.35 s
    L = 0.7
    A["slash"] = anim(L, False, {
        "right_arm": rot(lambda t: [-130 * hold(t, 0, 0.25, 0.25, 0.32) + 40 * snap(t, 0.25, 0.36, 0.45, 0.7), 0, -10 * hold(t, 0, 0.25, 0.36, 0.7)]),
        "right_hand": rot(lambda t: [-30 * hold(t, 0, 0.25, 0.25, 0.36) + 20 * snap(t, 0.28, 0.38, 0.45, 0.7), 0, 0]),
        "chest": rot(lambda t: [-12 * hold(t, 0, 0.25, 0.25, 0.34) + 20 * snap(t, 0.25, 0.36, 0.45, 0.7), 18 * hold(t, 0, 0.25, 0.3, 0.7), 0]),
        "jaw": rot(lambda t: [26 * sin01(t, 0.1, 0.5), 0, 0]),
        "hips": {"position": lambda t: [0, 0, -1.5 * snap(t, 0.25, 0.36, 0.45, 0.7)]},
    }, 0.025)
    # dash (0.5 s): drops low and springs forward
    L = 0.5
    A["dash"] = anim(L, False, {
        "root": {"position": lambda t: [0, -1.4 * sin01(t, 0, 0.2) + 1.0 * sin01(t, 0.2, 0.45), 0]},
        "chest": rot(lambda t: [22 * sin01(t, 0, 0.45), 0, 0]),
        "right_leg": rot(lambda t: [-40 * sin01(t, 0.15, 0.45), 0, 0]), "left_leg": rot(lambda t: [35 * sin01(t, 0.15, 0.45), 0, 0]),
        "right_arm": rot(lambda t: [50 * sin01(t, 0.1, 0.45), 0, 0]), "left_arm": rot(lambda t: [50 * sin01(t, 0.1, 0.45), 0, 0]),
        "jaw": rot(lambda t: [20 * sin01(t, 0.0, 0.5), 0, 0]),
    }, 0.025)
    L = 1.1
    A["death"] = anim(L, "hold", {
        **fall_over(L, side=-1),
        "chest": rot(lambda t: [-20 * sin01(t, 0, 0.4), 0, 0]),
        "head": rot(lambda t: [-25 * sin01(t, 0, 0.4) + 20 * window(t, 0.6, 0.9), 0, 0]),
        "jaw": rot(lambda t: [30 * window(t, 0.2, 0.6), 0, 0]),
        "right_arm": rot(lambda t: [-60 * window(t, 0.1, 0.5), 0, 0]), "left_arm": rot(lambda t: [-40 * window(t, 0.1, 0.5), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- grave thrall

def grave_thrall():
    """A dead soldier that will not stay down: grey rotting flesh, its ribs open to the dark, a dented nasal helm over
    empty sockets with a cold light in them, a slack jaw of bare teeth, a torn tabard over rusted mail, a broken sword
    in one hand and only bones left of the other."""
    name = "grave_thrall"
    B = Body(name, 1600)
    box, col = B.box, B.col
    rot_ = dict(seed=21)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 13, 0], [
        box(0, 12.6, 0, 7.2, 3.0, 4.0, "flesh", zone="back", **rot_),
        box(0, 9.4, 0, 7.8, 5.6, 4.6, "chain", r="rust", skip=("up",)),
        B.c([-2.6, 3.4, -2.75], [5.2, 9.8, 0.4], "cloth", hem=2.4, seed=63, skip=("up", "down")),
        B.c([-2.8, 4.2, 2.35], [5.6, 9.0, 0.4], "cloth", hem=2.6, seed=64, skip=("up", "down")),
        box(0, 13.3, 0, 8.0, 1.0, 4.8, "leather", stitch=False),
    ])
    B.bone("chest", "hips", [0, 13.5, 0], [
        box(0, 18.6, 0, 8.0, 10.0, 4.6, "flesh", zone="front", bib_w=0.3, **rot_),
        box(0, 18.7, -2.36, 5.2, 6.6, 0.2, "void", skip=("south",)),
        *[box(0, 16.3 + i * 1.5, -2.55, 6.0 - abs(i - 1.5) * 0.5, 0.7, 0.5, "bone", seed=90 + i) for i in range(4)],
        box(0, 18.9, -2.62, 0.8, 7.0, 0.5, "bone", seed=95),
        box(5.3, 22.9, 0, 3.4, 2.8, 5.4, "plate", pivot=[4, 23, 0], rot=[0, 0, -20], rivets=True),
        box(0, 23.4, 0, 6.0, 1.2, 4.2, "flesh", **rot_),
    ], rot=[12, 0, 0])
    B.bone("head", "chest", [0, 23.6, 0], [
        box(0, 27.2, -0.2, 6.2, 6.0, 6.2, "flesh", zone="back", split=(0.75, 0.2),
            sockets=sockets(1.4, 27.3, (1.0, 0.72), slant=0.32), shadow_top=(27.0, 29.2, 0.16),
            holes=[dict(x=0, y=25.9, r=(0.45, 0.55))], **rot_),
        box(0, 24.7, -3.35, 4.6, 0.9, 0.4, "teeth", n=7, fangs=0, up=True, skip=("up", "down", "south")),
        box(0, 30.3, -0.2, 6.8, 2.2, 6.8, "plate", rivets=False),
        box(0, 29.1, -0.2, 7.3, 0.8, 7.3, "trim", r="rust_trim"),
        box(0, 27.8, -3.55, 0.8, 3.2, 0.5, "plate", rivets=False),
    ], rot=[8, 0, 12])
    B.bone("jaw", "head", [0, 25, 1.5], [
        box(0, 24.0, -1.3, 4.8, 1.6, 4.0, "flesh", **rot_),
        box(0, 25.05, -3.2, 4.2, 0.8, 0.4, "teeth", n=6, fangs=0, up=False, skip=("up", "down", "south")),
    ], rot=[18, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 5.0, 22.5, 0], [
            col(sx * 5.2, 0, 16.6, 23.0, 2.6, 2.6, "flesh", zone="limb", **rot_),
        ], rot=[-6 if sx < 0 else 4, 0, sx * -4])
        if sx < 0:
            fore = [
                col(-5.2, 0, 10.8, 16.8, 2.3, 2.3, "flesh", zone="limb", **rot_),
                box(-5.2, 10.0, -0.2, 2.5, 1.8, 2.7, "flesh", **rot_),
                # a broken, rusted sword, dragged point down
                box(-5.2, 10.1, -0.4, 0.9, 1.0, 2.2, "leather", stitch=False),
                box(-5.2, 10.1, -1.8, 3.2, 0.8, 0.8, "metal", r="rust_iron"),
                B.c([-5.55, 8.9, -11.6], [0.7, 2.4, 9.6], "metal", r="blade", edge="down", nicks=True, pivot=[-5.2, 10.1, -1.8], rot=[38, 0, 0]),
            ]
        else:
            fore = [
                col(4.7, 0, 10.8, 16.8, 0.8, 0.8, "bone"), col(5.6, 0, 10.8, 16.8, 0.8, 0.8, "bone"),
                box(5.2, 16.3, 0, 2.4, 1.2, 2.4, "flesh", **rot_),
                box(5.2, 10.2, -0.2, 2.0, 1.2, 2.0, "bone"),
                *[col(4.5 + i * 0.7, -0.6, 7.6, 9.8, 0.45, 0.45, "bone", seed=96 + i) for i in range(3)],
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.2, 16.8, 0], fore, rot=[-10, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 12.0, 0], [
            col(sx * 2.0, 0, 6.4, 12.4, 3.0, 3.0, "flesh", zone="limb", **rot_)])
        shin = [col(sx * 2.0, 0, 1.0, 6.6, 2.6, 2.6, "flesh", zone="limb", **rot_),
                box(sx * 2.0, 0.8, -0.5, 3.0, 1.6, 4.0, "leather", stitch=False)]
        if sx < 0:
            shin.append(col(-2.0, 0, 2.2, 5.6, 2.9, 2.9, "cloth", r="wrap", seed=65))
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.0, 6.5, 0], shin)
    register(name, lambda aff: pal(aff, (150, 255, 225), {
        "rot": (112, 124, 120), "rot_d": (58, 66, 70), "rot_l": (156, 160, 146),
        "cloth": (92, 34, 34), "wrap": (118, 106, 88), "bone": (184, 170, 140),
        "plate": (70, 66, 66), "trim": (112, 84, 60), "rust_trim": (104, 70, 48), "rust": (104, 70, 50), "rust_iron": (80, 66, 60),
        "blade": [(18, 12, 10), (58, 40, 32), (110, 84, 70), (160, 140, 124), (206, 196, 186)],
    }), density=5.0)
    B.write(1.4, 2.2, 1.0)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 0, 3 * wave(t, L, 0.25)]),
        "head": rot(lambda t: [3 * wave(t, L), 0, 10 * window(t % 1.5, 0.0, 0.08) * (1 - window(t % 1.5, 0.08, 0.3))]),
        "jaw": rot(lambda t: [4 * wave(t, L / 2), 0, 0]),
        "right_arm": rot(lambda t: [2 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-3 * wave(t, L, 0.3), 0, 0]),
    }, 0.05)
    L = 1.6
    A["walk"] = anim(L, True, {
        "right_leg": rot(lambda t: [-14 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [26 * wave(t, L, 0.5) * -1, 0, 0]),
        "right_shin": rot(lambda t: [10 * max(0, wave(t, L, 0.25)), 0, 0]), "left_shin": rot(lambda t: [30 * max(0, wave(t, L, 0.75)), 0, 0]),
        "hips": {"position": lambda t: [0, -0.5 * abs(wave(t, L)), 0], "rotation": lambda t: [0, 4 * wave(t, L), 6 * wave(t, L)]},
        "chest": rot(lambda t: [4, 0, -5 * wave(t, L)]),
        "head": rot(lambda t: [4 * wave(t, L / 2), 0, -6 * wave(t, L, 0.1)]),
        "right_arm": rot(lambda t: [8 * wave(t, L, 0.5), 0, 0]), "left_arm": rot(lambda t: [-16 * wave(t, L), 0, 0]),
    }, 0.05)
    L = 0.9
    A["attack"] = anim(L, False, {
        "right_arm": rot(lambda t: [-150 * hold(t, 0.0, 0.38, 0.38, 0.5) + 30 * snap(t, 0.4, 0.5, 0.6, 0.9), 0, 0]),
        "right_hand": rot(lambda t: [-30 * hold(t, 0.0, 0.38, 0.4, 0.6), 0, 0]),
        "chest": rot(lambda t: [-10 * hold(t, 0, 0.38, 0.38, 0.5) + 18 * snap(t, 0.38, 0.5, 0.6, 0.9), -15 * hold(t, 0, 0.38, 0.5, 0.9), 0]),
        "jaw": rot(lambda t: [20 * sin01(t, 0.2, 0.8), 0, 0]),
    }, 0.025)
    # revive (3.0 s): drops in a heap, lies still, then drags itself up again
    L = 3.0

    def down(t):
        return ease_in(window(t, 0.0, 0.45)) * (1 - ease_in_out(window(t, 1.9, 2.8)))
    A["revive"] = anim(L, False, {
        "root": {"rotation": lambda t: [86 * down(t), 0, 0], "position": lambda t: [0, -1.5 * down(t), -3 * down(t)]},
        "head": rot(lambda t: [-30 * down(t) + 25 * sin01(t, 2.0, 2.6), 0, 0]),
        "jaw": rot(lambda t: [26 * down(t), 0, 0]),
        "right_arm": rot(lambda t: [-70 * down(t) - 80 * sin01(t, 1.9, 2.6), 0, 0]),
        "left_arm": rot(lambda t: [-70 * down(t) - 80 * sin01(t, 1.95, 2.65), 0, 0]),
        "right_leg": rot(lambda t: [10 * down(t), 0, 0]), "left_leg": rot(lambda t: [-6 * down(t), 0, 0]),
    }, 0.05)
    L = 1.4
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [86 * ease_in(window(t, 0.2, 0.75)), 0, 0], "position": lambda t: [0, -1.5 * window(t, 0.2, 0.75), -3 * window(t, 0.2, 0.75)]},
        "head": rot(lambda t: [-30 * window(t, 0.3, 0.8), 0, 20 * window(t, 0.6, 1.0)]),
        "jaw": rot(lambda t: [30 * window(t, 0.2, 0.8), 0, 0]),
        "right_arm": rot(lambda t: [-80 * window(t, 0.2, 0.7), 0, 0]), "left_arm": rot(lambda t: [-60 * window(t, 0.2, 0.7), 0, 10]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- bone archer

def bone_archer():
    """A skeleton in a ragged hood, a quiver on its back and a bow strung with light: dark sockets with a point of
    cold fire in each, a ribcage open to the dark."""
    name = "bone_archer"
    B = Body(name, 1700)
    box, col = B.box, B.col
    hood = dict(r="cloth", seed=67)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12.5, 0], [
        box(0, 12.0, 0, 5.4, 2.0, 3.0, "bone", seed=81),
        col(0, 0.6, 12.0, 14.0, 1.2, 1.2, "bone", seed=82),
        B.c([-2.4, 5.2, -1.9], [4.8, 7.6, 0.4], "cloth", hem=2.2, seed=66, skip=("up", "down")),
        B.c([-2.6, 6.0, 1.4], [5.2, 6.8, 0.4], "cloth", hem=2.0, seed=68, skip=("up", "down")),
    ])
    B.bone("chest", "hips", [0, 13.0, 0], [
        col(0, 0.8, 13.0, 23.8, 1.2, 1.2, "bone", seed=83),
        box(0, 19.8, -0.2, 6.4, 6.4, 3.6, "void"),
        *[box(0, 17.0 + i * 1.3, -0.2, 6.9 - (1.0 if i == 0 else 0.0), 0.7, 4.0, "bone", seed=84 + i) for i in range(5)],
        box(0, 23.4, 0.0, 8.4, 1.1, 2.2, "bone", seed=89),
        box(0, 23.9, 0.7, 9.4, 1.8, 5.2, "cloth", zone="dark", **hood),
        # the cloak, down the back
        B.c([-4.4, 8.6, 2.8], [8.8, 14.6, 0.5], "cloth", hem=3.0, seed=69, skip=("up",)),
        # a quiver over the shoulder, with fletchings
        B.c([0.4, 15.5, 3.3], [2.6, 7.6, 2.6], "leather", pivot=[1.7, 19, 4.6], rot=[0, 0, -24]),
        *[B.c([0.6 + i * 0.7, 22.9, 3.6 + (i % 2) * 0.9], [0.5, 2.4, 0.5], "cloth", r="fletch", seed=70 + i, pivot=[1.7, 19, 4.6], rot=[0, 0, -24])
          for i in range(3)],
    ], rot=[6, 0, 0])
    B.bone("head", "chest", [0, 24.0, 0], [
        box(0, 27.3, -0.4, 5.6, 5.2, 5.6, "bone", seed=88, ends=0.0,
            sockets=sockets(1.3, 27.8, (1.05, 0.72), slant=0.36, core=0.3), shadow_top=(27.0, 29.9, 0.3),
            holes=[dict(x=0.3, y=26.2, r=(0.26, 0.5)), dict(x=-0.3, y=26.2, r=(0.26, 0.5))],
            cuts=[dict(pts=[(1.6, 29.9), (2.2, 28.9), (1.9, 28.3)], w=0.25, faces=("north",))]),
        box(0, 25.0, -3.3, 4.0, 0.8, 0.4, "teeth", n=7, fangs=0, up=True, skip=("up", "down", "south")),
        # the hood: four slabs, dark inside, and a peak over the face
        box(0, 31.1, 0.1, 7.6, 0.8, 7.8, "cloth", dark_faces=("down",), **hood),
        box(3.4, 27.6, 0.3, 0.8, 7.2, 7.4, "cloth", dark_faces=("east",), **hood),
        box(-3.4, 27.6, 0.3, 0.8, 7.2, 7.4, "cloth", dark_faces=("west",), **hood),
        box(0, 27.6, 3.6, 6.0, 7.2, 0.8, "cloth", dark_faces=("north",), **hood),
        box(0, 30.6, -3.6, 7.6, 1.4, 0.8, "cloth", dark_faces=("down", "south"), **hood),
    ])
    B.bone("jaw", "head", [0, 25.2, 1.0], [
        box(0, 24.3, -1.0, 4.4, 1.4, 4.4, "bone", seed=91),
        box(0, 25.2, -3.0, 3.8, 0.7, 0.4, "teeth", n=6, fangs=0, up=False, skip=("up", "down", "south")),
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 22.8, 0], [
            col(sx * 4.4, 0, 17.0, 23.2, 1.2, 1.2, "bone", seed=92),
            box(sx * 4.4, 23.0, 0, 2.2, 1.8, 2.4, "bone", seed=93),
        ], rot=[0, 0, sx * -6])
        fore = [col(sx * 4.2, 0, 11.4, 17.0, 0.7, 0.7, "bone"), col(sx * 4.8, 0, 11.4, 17.0, 0.7, 0.7, "bone"),
                box(sx * 4.5, 17.0, 0, 1.6, 1.2, 1.6, "bone"),
                box(sx * 4.5, 10.8, -0.1, 1.6, 1.2, 1.8, "bone"),
                *[col(sx * 4.0 + i * 0.5 * sx, -0.6, 8.8, 10.4, 0.4, 0.4, "bone", seed=97 + i) for i in range(3)]]
        if sx > 0:
            # the bow, upright in the left hand: a grip, two limbs bending back to the string, tips curled forward
            fore += [
                box(4.6, 10.6, -1.2, 0.9, 2.6, 0.9, "leather", stitch=False),
                B.c([4.25, 11.8, -1.6], [0.7, 7.2, 0.8], "wood", pivot=[4.6, 11.8, -1.2], rot=[-14, 0, 0]),
                B.c([4.3, 18.6, -0.4], [0.6, 2.6, 0.6], "horn", r="horn", tip="+y", pivot=[4.6, 18.8, -0.1], rot=[22, 0, 0]),
                B.c([4.25, 2.2, -1.6], [0.7, 7.2, 0.8], "wood", pivot=[4.6, 9.4, -1.2], rot=[14, 0, 0]),
                B.c([4.3, 0.2, -0.4], [0.6, 2.6, 0.6], "horn", r="horn", tip="-y", pivot=[4.6, 2.6, -0.1], rot=[-22, 0, 0]),
                B.c([4.5, 2.4, 0.4], [0.2, 16.6, 0.2], "glow"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.4, 17.2, 0], fore, rot=[-8 if sx > 0 else 0, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 12.0, 0], [
            col(sx * 2.0, 0, 6.4, 12.4, 1.2, 1.2, "bone", seed=98), box(sx * 2.0, 6.4, 0, 1.8, 1.4, 1.8, "bone", seed=99)])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.0, 6.4, 0], [
            col(sx * 2.0, 0, 1.0, 6.0, 0.9, 0.9, "bone"), col(sx * 2.0 + 0.7 * sx, 0, 1.0, 6.0, 0.6, 0.6, "bone"),
            box(sx * 2.0, 0.5, -0.8, 2.0, 1.0, 3.4, "bone", seed=100),
            col(sx * 2.0, 0, 2.6, 5.4, 2.0, 2.0, "cloth", r="wrap", seed=71),
        ])
    # the arrow it nocks: only seen while it shoots
    B.bone("arrow", "right_hand", [-4.5, 10.8, 0], [
        B.bar(-4.5, 10.8, -11.0, 1.0, 0.4, 0.4, "wood", long="z"),
        B.bar(-4.5, 10.8, -12.4, -11.0, 0.8, 0.8, "metal", r="steel"),
        B.bar(-4.5, 11.1, 0.0, 1.6, 0.2, 0.9, "cloth", r="fletch"),
    ])
    register(name, lambda aff: pal(aff, (130, 190, 255), {
        "bone": (190, 178, 150), "cloth": (50, 44, 50), "wrap": (110, 98, 84), "fletch": (150, 40, 38), "horn": (70, 60, 50),
    }, tinted=("cloth", "cloth_d", "cloth_l", "bone", "bone_d", "bone_l")), density=5.5)
    B.write(1.4, 2.2, 1.0)

    hidden = {"arrow": {"scale": lambda t: [0.01, 0.01, 0.01]}}
    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [6 + 1.5 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [2 * wave(t, L), 18 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [6 + 3 * wave(t, L / 3), 0, 0]),
        "right_arm": rot(lambda t: [3 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-3 * wave(t, L), 0, 0]),
        **hidden,
    }, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {**biped_walk(L, shins=("right_shin", "left_shin"), arm_swing=16, swing=28, knee=26, body="hips"), **hidden})
    # shoot (1.0 s): bow arm up, draw to the jaw, release at 0.62 s
    L = 1.0

    def aim(t):
        return hold(t, 0.0, 0.2, 0.8, 1.0)

    def draw(t):
        return ease_in_out(window(t, 0.2, 0.58)) * (1 - window(t, 0.62, 0.66))
    A["shoot"] = anim(L, False, {
        "left_arm": rot(lambda t: [-82 * aim(t), -10 * aim(t), 0]),
        "left_hand": rot(lambda t: [8 * aim(t), 0, 0]),
        "right_arm": rot(lambda t: [-82 * aim(t) + 6 * draw(t), 30 * aim(t) + 18 * draw(t), 0]),
        "right_hand": rot(lambda t: [-70 * draw(t) - 20 * aim(t), 0, 0]),
        "chest": rot(lambda t: [0, -18 * aim(t), 0]),
        "head": rot(lambda t: [0, 18 * aim(t), 0]),
        "arrow": {"scale": lambda t: [1.0 if 0.12 < t < 0.63 else 0.01] * 3},
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -7 * ease_in(window(t, 0.2, 0.7)), 0], "rotation": lambda t: [12 * window(t, 0.2, 0.7), 0, 0]},
        "head": {"position": lambda t: [3 * window(t, 0.5, 0.9), -9 * ease_in(window(t, 0.45, 0.9)), -2 * window(t, 0.5, 0.9)],
                 "rotation": lambda t: [0, 40 * window(t, 0.5, 1.0), 70 * window(t, 0.5, 1.0)]},
        "chest": rot(lambda t: [35 * window(t, 0.3, 0.8), 0, 0]),
        "right_arm": rot(lambda t: [-20 * window(t, 0.2, 0.6), 0, 60 * window(t, 0.4, 0.9)]),
        "left_arm": rot(lambda t: [-20 * window(t, 0.2, 0.6), 0, -60 * window(t, 0.4, 0.9)]),
        "right_leg": rot(lambda t: [-70 * window(t, 0.2, 0.7), 0, 0]), "left_leg": rot(lambda t: [-70 * window(t, 0.2, 0.7), 0, 0]),
        "right_shin": rot(lambda t: [110 * window(t, 0.2, 0.7), 0, 0]), "left_shin": rot(lambda t: [110 * window(t, 0.2, 0.7), 0, 0]),
        **hidden,
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- rime stalker

def rime_stalker():
    """A tall, thin hunter of the deep cold: a body of living shadow under plates of ice, a smooth ice mask with two
    slits of light, a crown of glowing shards, and arms that end in long blades of ice."""
    name = "rime_stalker"
    B = Body(name, 1800)
    box, col = B.box, B.col
    ice = dict(seed=112)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15, 0], [
        box(0, 15.2, 0, 5.6, 3.0, 3.6, "void"),
        box(0, 16.4, 0, 6.2, 1.0, 4.2, "ice", **ice),
        B.c([-2.4, 4.0, -2.2], [4.8, 11.6, 0.4], "cloth", hem=3.2, seed=72, skip=("up", "down")),
        B.c([-2.8, 5.0, 1.8], [5.6, 10.6, 0.4], "cloth", hem=3.4, seed=73, skip=("up", "down")),
    ])
    shards = []
    for sx in (-1, 1):
        shards += [B.c(mirror_x([3.4, 27.0, -1.0], [1.6, 4.4, 1.6], sx), [1.6, 4.4, 1.6], "crystal", pivot=[sx * 4.2, 27.0, -0.2], rot=[-12, 0, sx * -28], skip=("down",)),
                   B.c(mirror_x([2.6, 26.4, 0.6], [1.2, 3.2, 1.2], sx), [1.2, 3.2, 1.2], "crystal", pivot=[sx * 3.2, 26.4, 1.2], rot=[-30, 0, sx * -18], skip=("down",))]
    B.bone("chest", "hips", [0, 16.5, 0], [
        box(0, 21.8, 0.2, 6.4, 10.4, 3.8, "void"),
        box(0, 23.4, -1.8, 6.6, 5.6, 1.2, "ice", pivot=[0, 26, -1.6], rot=[-6, 0, 0], **ice),
        box(0, 19.0, -1.6, 4.4, 3.0, 0.8, "ice", **ice),
        box(0, 22.0, 2.1, 5.6, 7.0, 0.8, "ice", **ice),
        *[box(sx * 4.2, 26.2, 0.2, 3.6, 2.4, 4.6, "ice", pivot=[sx * 3.0, 26.0, 0], rot=[0, 0, sx * -14], **ice) for sx in (-1, 1)],
        *shards,
    ], rot=[10, 0, 0])
    crown = [B.c([-0.6 + 2.0 * k - 0.4, 33.4, -0.6], [1.2, 4.2 - abs(k) * 1.2, 1.2], "crystal", pivot=[2.0 * k, 33.4, 0], rot=[-10, 0, -k * 22], skip=("down",))
             for k in (-2, -1, 0, 1, 2)]
    B.bone("head", "chest", [0, 27.0, 0], [
        col(0, 0.2, 26.8, 28.6, 2.2, 2.2, "void"),
        box(0, 31.0, -0.4, 4.8, 5.4, 4.8, "ice", eyes=eyes(1.1, 31.2, (1.05, 0.28), slant=0.46, pupil=None, lid=1.4), **ice),
        box(0, 29.0, -2.5, 2.8, 1.4, 0.6, "ice", **ice),
        *crown,
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 25.6, 0.2], [
            col(sx * 4.6, 0.2, 18.0, 25.8, 1.6, 1.6, "void"),
        ], rot=[-4, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 18.2, 0.2], [
            col(sx * 4.6, 0.2, 11.0, 18.2, 1.5, 1.5, "void"),
            col(sx * 4.6, 0.2, 12.0, 17.0, 2.2, 2.2, "ice", **ice),
            box(sx * 4.6, 10.4, 0.0, 1.6, 1.6, 1.8, "void"),
            # the blade: from the wrist, past the hand, nearly to the knee
            B.c([sx * 4.6 - 0.35, -0.8, -1.2], [0.7, 12.0, 2.0], "crystal", pivot=[sx * 4.6, 11.0, 0.0], rot=[-8, 0, 0], skip=("up",)),
            B.c([sx * 4.6 - 0.25, -3.2, -0.9], [0.5, 2.6, 1.2], "crystal", pivot=[sx * 4.6, 11.0, 0.0], rot=[-8, 0, 0], skip=("up",)),
        ], rot=[-14, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 1.8, 14.4, 0], [
            col(sx * 1.9, 0, 7.4, 14.6, 1.8, 1.8, "void"),
            col(sx * 1.9, -0.2, 8.6, 13.6, 2.4, 2.4, "ice", **ice),
        ], rot=[-6, 0, 0])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 1.9, 7.6, 0], [
            col(sx * 1.9, 0, 0.8, 7.8, 1.5, 1.5, "void"),
            col(sx * 1.9, -0.3, 1.6, 6.6, 2.2, 2.2, "ice", **ice),
            B.c([sx * 1.9 - 0.9, 0.0, -3.4], [1.8, 1.0, 4.2], "ice", **ice),
        ], rot=[8, 0, 0])
    register(name, lambda aff: pal(aff, (140, 220, 255), {
        "ice": [(20, 34, 58), (64, 104, 144), (130, 176, 214), (196, 228, 248), (246, 252, 255)],
        "cloth": (34, 36, 56), "void": [(1, 1, 4), (5, 6, 14), (14, 16, 32), (30, 34, 60), (56, 62, 100)],
    }, tinted=("cloth", "cloth_d", "cloth_l", "ice")), density=5.0)
    B.write(1.4, 2.5, 1.1)

    A = {}
    L = 3.2
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 4 * wave(t, L * 2), 0]),
        "head": rot(lambda t: [0, 10 * wave(t, L * 2, 0.3), 8 * wave(t, L, 0.1)]),
        "right_arm": rot(lambda t: [4 * wave(t, L), 0, -3]), "left_arm": rot(lambda t: [-4 * wave(t, L), 0, 3]),
    }, 0.1)
    L = 1.1
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=34, arm_swing=10, knee=36, bob=0.8, body="hips"),
        "chest": rot(lambda t: [10, 5 * wave(t, L), 0]),
        "head": rot(lambda t: [-8, -5 * wave(t, L), 0]),
    }, 0.05)
    # slash (0.7 s): both blades up and crossing down through the target
    L = 0.7
    A["slash"] = anim(L, False, {
        "right_arm": rot(lambda t: [-120 * hold(t, 0, 0.22, 0.26, 0.34) + 20 * snap(t, 0.26, 0.36, 0.45, 0.7), -30 * hold(t, 0, 0.22, 0.3, 0.7), 40 * snap(t, 0.26, 0.36, 0.45, 0.7)]),
        "left_arm": rot(lambda t: [-120 * hold(t, 0.04, 0.26, 0.3, 0.38) + 20 * snap(t, 0.3, 0.4, 0.48, 0.7), 30 * hold(t, 0.04, 0.26, 0.34, 0.7), -40 * snap(t, 0.3, 0.4, 0.48, 0.7)]),
        "chest": rot(lambda t: [-14 * hold(t, 0, 0.22, 0.26, 0.34) + 22 * snap(t, 0.26, 0.38, 0.5, 0.7), 0, 0]),
        "hips": {"position": lambda t: [0, 0, -2.5 * snap(t, 0.26, 0.38, 0.5, 0.7)]},
    }, 0.025)
    L = 0.5
    A["dash"] = anim(L, False, {
        "root": {"position": lambda t: [0, -2 * sin01(t, 0, 0.5), 0]},
        "chest": rot(lambda t: [30 * sin01(t, 0, 0.5), 0, 0]),
        "right_arm": rot(lambda t: [60 * sin01(t, 0.05, 0.5), 0, -20 * sin01(t, 0.05, 0.5)]),
        "left_arm": rot(lambda t: [60 * sin01(t, 0.05, 0.5), 0, 20 * sin01(t, 0.05, 0.5)]),
        "right_leg": rot(lambda t: [-50 * sin01(t, 0.05, 0.5), 0, 0]), "left_leg": rot(lambda t: [40 * sin01(t, 0.05, 0.5), 0, 0]),
    }, 0.025)
    # death (1.2 s): to its knees, then it shatters away
    L = 1.2
    gone = lambda t: max(0.01, 1 - ease_in(window(t, 0.75, 1.15)))  # noqa: E731
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -6 * ease_out_back(window(t, 0.1, 0.45)), 0], "scale": lambda t: [gone(t), gone(t), gone(t)]},
        "right_leg": rot(lambda t: [-80 * window(t, 0.1, 0.45), 0, 0]), "left_leg": rot(lambda t: [-80 * window(t, 0.1, 0.45), 0, 0]),
        "right_shin": rot(lambda t: [90 * window(t, 0.1, 0.45), 0, 0]), "left_shin": rot(lambda t: [90 * window(t, 0.1, 0.45), 0, 0]),
        "chest": rot(lambda t: [30 * window(t, 0.2, 0.6), 0, 0]),
        "head": rot(lambda t: [-30 * window(t, 0.3, 0.7), 0, 0]),
        "right_arm": rot(lambda t: [20, 0, 30 * window(t, 0.2, 0.6)]), "left_arm": rot(lambda t: [20, 0, -30 * window(t, 0.2, 0.6)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- tuskbrute

def brute_paint(f, p, s):
    if f.face != "north":
        return
    a, b = f.X, f.Y
    for (a0, b0), (a1, b1) in (((-5.6, 26.6), (-1.6, 23.0)), ((5.6, 26.6), (1.6, 23.0)), ((-4.6, 19.6), (4.6, 19.6))):
        d, _ = CS.seg_dist(a, b, a0, b0, a1, b1)
        p.over("paint", 0.42, d < 0.75 + 0.25 * CS.jag(a * 1.3 + b, 5, 0.9))


def tuskbrute():
    """A hulking brute: a back like a hill, a small head sunk between its shoulders under a heavy brow, a jutting jaw
    with two great tusks, red war paint, an iron pauldron with spikes, a fur kilt, and a club the size of a man."""
    name = "tuskbrute"
    B = Body(name, 1900)
    box, col = B.box, B.col
    sk = dict(seed=13)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 13, 0], [
        box(0, 13.6, 0, 10.4, 4.0, 6.8, "skin", zone="back", **sk),
        box(0, 10.6, 0, 11.4, 5.0, 7.6, "fur", r="fur", zone="back", seed=24),
        box(0, 13.4, 0, 11.6, 1.4, 7.8, "leather", stitch=True),
        box(0, 13.4, -4.0, 2.4, 2.2, 0.8, "bone", seed=101),
    ])
    paint = dict(fx=brute_paint)
    B.bone("chest", "hips", [0, 15, 0], [
        box(0, 22.0, -0.4, 14.0, 13.0, 9.0, "skin", zone="front", bib_w=0.28, **sk, **paint),
        box(0, 28.6, 0.6, 15.6, 3.4, 7.6, "skin", zone="back", **sk),
        # the harness: two straps crossing the chest
        box(0, 23.0, -4.95, 14.4, 1.2, 0.3, "leather", pivot=[0, 23, -4.9], rot=[0, 0, 38], stitch=False),
        box(0, 23.0, -5.0, 14.4, 1.2, 0.3, "leather", pivot=[0, 23, -5.0], rot=[0, 0, -38], stitch=False),
        box(0, 23.0, -5.15, 2.2, 2.2, 0.4, "trim"),
        # an iron pauldron on the left shoulder, spiked
        box(8.0, 28.2, 0.4, 6.2, 4.6, 9.0, "plate", pivot=[6.0, 28, 0], rot=[0, 0, -16], engrave="trim"),
        B.c([8.6, 30.6, -1.2], [1.6, 3.4, 1.6], "horn", r="iron_spike", tip="+y", pivot=[6.0, 28, 0], rot=[0, 0, -16], skip=("down",)),
        B.c([8.6, 30.6, 2.0], [1.6, 3.0, 1.6], "horn", r="iron_spike", tip="+y", pivot=[6.0, 28, 0], rot=[0, 0, -16], skip=("down",)),
    ], rot=[14, 0, 0])
    head = [
        box(0, 30.0, -5.0, 7.2, 6.4, 6.8, "skin", zone="back", split=(0.68, 0.2),
            eyes=eyes(1.65, 31.1, (1.05, 0.5), slant=0.42, pupil="dot"), **sk,
            cuts=[dict(pts=[(-3.4, 30.4), (-0.9, 30.1)], w=0.7, faces=("north",), ramp="paint", tone=0.45),
                  dict(pts=[(0.9, 30.1), (3.4, 30.4)], w=0.7, faces=("north",), ramp="paint", tone=0.45)]),
        box(0, 32.3, -8.5, 7.6, 1.4, 1.8, "skin", r="skin_d", seed=14),
        box(0, 30.4, -8.7, 2.4, 2.2, 1.6, "skin", r="skin_d", seed=15, holes=[dict(x=0.5, y=29.7, r=(0.3, 0.25)), dict(x=-0.5, y=29.7, r=(0.3, 0.25))]),
        box(0, 34.4, -2.6, 2.4, 2.0, 2.4, "hair", r="hair", seed=132, ends=False),
        B.c([-0.9, 26.0, -1.6], [1.8, 8.6, 1.0], "hair", r="hair", seed=133, pivot=[0, 34, -1.6], rot=[-20, 0, 0], skip=("up",)),
    ]
    for sx in (-1, 1):
        head.append(B.c(mirror_x([3.4, 30.2, -4.4], [2.2, 1.8, 0.8], sx), [2.2, 1.8, 0.8], "skin", pivot=[sx * 3.6, 31, -4.0], rot=[0, sx * -30, sx * 18], zone="limb", **sk))
    B.bone("head", "chest", [0, 28.0, -3.0], head, rot=[-10, 0, 0])
    B.bone("jaw", "head", [0, 28.6, -3.0], [
        box(0, 27.4, -5.8, 7.0, 2.6, 6.2, "skin", zone="back", split=(0.8, 0.3), **sk),
        box(0, 28.9, -8.65, 5.4, 0.8, 0.4, "teeth", n=5, fangs=0, up=False, skip=("up", "down", "south")),
        *[B.c([sx * 2.6 - 0.65, 27.8, -9.0], [1.3, 4.2, 1.3], "horn", r="tusk", tip="+y", pivot=[sx * 2.6, 28.2, -8.4], rot=[-8, 0, sx * -10], skip=("down",))
          for sx in (-1, 1)],
    ], rot=[4, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 8.6, 27.5, 0], [
            col(sx * 9.8, 0, 18.4, 28.4, 5.2, 5.4, "skin", zone="limb", **sk),
        ], rot=[-6, 0, sx * -6])
        hand = [
            col(sx * 10.0, 0, 10.6, 19.4, 5.0, 5.2, "skin", zone="limb", **sk),
            col(sx * 10.0, 0, 12.2, 17.2, 5.6, 5.8, "leather", stitch=True),
            box(sx * 10.0, 9.0, -0.2, 5.0, 3.6, 5.6, "skin", **sk),
        ]
        if sx < 0:
            # a great crude axe: a bone-wrapped haft and a notched iron blade, its head by the knee
            hand += [
                col(-10.2, -0.6, 1.2, 11.6, 1.6, 1.6, "wood", seed=122),
                col(-10.2, -0.6, 7.0, 9.6, 2.0, 2.0, "leather", stitch=False),
                col(-10.2, -0.6, 0.2, 1.4, 2.2, 2.2, "trim"),
                B.c([-10.65, 1.8, -8.6], [0.9, 7.6, 7.6], "metal", r="blade", edge="front", nicks=True),
                B.c([-10.75, 3.2, -2.2], [1.1, 4.8, 1.8], "metal", r="iron"),
                B.c([-10.6, 4.6, 0.4], [0.8, 2.2, 2.4], "horn", r="iron_spike", tip="+z"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 9.9, 19.0, 0], hand)
        B.bone(f"{side}_leg", "hips", [sx * 3.4, 12.0, 0], [
            col(sx * 3.6, 0, 6.4, 12.6, 5.0, 5.4, "skin", zone="limb", **sk)], rot=[0, 0, sx * -3])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 3.6, 6.6, 0], [
            col(sx * 3.7, 0, 1.4, 6.8, 4.4, 4.6, "skin", zone="limb", **sk),
            col(sx * 3.7, 0, 2.2, 5.6, 5.0, 5.2, "fur", r="fur", seed=25),
            box(sx * 3.7, 0.8, -0.9, 5.0, 1.6, 6.6, "skin", r="skin_d", seed=16),
        ], rot=[0, 0, sx * 3])
    register(name, lambda aff: pal(aff, (255, 96, 48), {
        "skin": (94, 104, 86), "skin_d": (46, 54, 46), "skin_l": (146, 144, 118),
        "fur": (90, 70, 54), "hair": (30, 26, 24), "tusk": (210, 196, 160), "paint": (162, 30, 26),
        "plate": (70, 70, 78), "trim": (128, 108, 78), "iron_spike": (96, 96, 104),
        "blade": [(14, 12, 12), (46, 42, 42), (96, 92, 92), (158, 154, 150), (220, 218, 212)],
    }), density=4.5)
    B.write(2.6, 3.0, 1.2)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2.5 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [-2 * wave(t, L), 8 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [3 + 3 * wave(t, L), 0, 0]),
        "right_arm": rot(lambda t: [2 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-2 * wave(t, L), 0, 2 * wave(t, L)]),
    }, 0.1)
    L = 1.3
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=24, arm_swing=14, knee=22, bob=1.0, body="hips"),
        "chest": rot(lambda t: [4, 6 * wave(t, L), 3 * wave(t, L)]),
        "head": rot(lambda t: [0, -5 * wave(t, L), 0]),
    }, 0.05)
    # smash (1.0 s): the club up behind the head, down hard at 0.55 s
    L = 1.0
    A["smash"] = anim(L, False, {
        "right_arm": rot(lambda t: [-170 * hold(t, 0, 0.42, 0.45, 0.56) + 10 * snap(t, 0.45, 0.56, 0.7, 1.0), 0, 10 * hold(t, 0, 0.42, 0.56, 1.0)]),
        "right_hand": rot(lambda t: [-40 * hold(t, 0, 0.42, 0.45, 0.56), 0, 0]),
        "chest": rot(lambda t: [-16 * hold(t, 0, 0.42, 0.45, 0.56) + 22 * snap(t, 0.45, 0.58, 0.7, 1.0), -14 * hold(t, 0, 0.42, 0.56, 1.0), 0]),
        "jaw": rot(lambda t: [22 * sin01(t, 0.3, 0.8), 0, 0]),
        "hips": {"position": lambda t: [0, -1.5 * snap(t, 0.45, 0.58, 0.7, 1.0), 0]},
    }, 0.025)
    # slam (1.4 s): both arms high, then down onto the ground at 0.8 s
    L = 1.4
    A["slam"] = anim(L, False, {
        "right_arm": rot(lambda t: [-160 * hold(t, 0, 0.6, 0.66, 0.8) - 40 * snap(t, 0.66, 0.8, 1.0, 1.4), 0, 0]),
        "left_arm": rot(lambda t: [-160 * hold(t, 0, 0.6, 0.66, 0.8) - 40 * snap(t, 0.66, 0.8, 1.0, 1.4), 0, 0]),
        "chest": rot(lambda t: [-18 * hold(t, 0, 0.6, 0.66, 0.8) + 34 * snap(t, 0.66, 0.82, 1.0, 1.4), 0, 0]),
        "head": rot(lambda t: [-20 * hold(t, 0.1, 0.6, 0.66, 0.8), 0, 0]),
        "jaw": rot(lambda t: [30 * sin01(t, 0.3, 1.1), 0, 0]),
        "hips": {"position": lambda t: [0, -2.5 * snap(t, 0.66, 0.82, 1.0, 1.4), -1.5 * snap(t, 0.66, 0.82, 1.0, 1.4)]},
        "right_leg": rot(lambda t: [-20 * snap(t, 0.66, 0.82, 1.0, 1.4), 0, 0]), "left_leg": rot(lambda t: [-20 * snap(t, 0.66, 0.82, 1.0, 1.4), 0, 0]),
        "right_shin": rot(lambda t: [30 * snap(t, 0.66, 0.82, 1.0, 1.4), 0, 0]), "left_shin": rot(lambda t: [30 * snap(t, 0.66, 0.82, 1.0, 1.4), 0, 0]),
    }, 0.025)
    L = 1.6
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [84 * ease_in(window(t, 0.45, 1.1)), 0, 0], "position": lambda t: [0, -1 * window(t, 0.45, 1.1), -4 * window(t, 0.45, 1.1)]},
        "right_leg": rot(lambda t: [-40 * window(t, 0.1, 0.5), 0, 0]), "right_shin": rot(lambda t: [70 * window(t, 0.1, 0.5), 0, 0]),
        "chest": rot(lambda t: [-12 * sin01(t, 0, 0.5), 0, 0]),
        "head": rot(lambda t: [-25 * sin01(t, 0, 0.6) - 20 * window(t, 0.9, 1.3), 0, 0]),
        "jaw": rot(lambda t: [26 * window(t, 0.0, 0.4), 0, 0]),
        "right_arm": rot(lambda t: [-90 * window(t, 0.5, 1.0), 0, 0]), "left_arm": rot(lambda t: [-90 * window(t, 0.5, 1.0), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- tusk shaman

def tusk_shaman():
    """An old shaman of the tusked brutes: hunched under a fur mantle and a robe of hides, a beast's skull for a mask
    with curling horns and eyes burning through it, beads and teeth, and a staff crowned with a skull and a crystal."""
    name = "tusk_shaman"
    B = Body(name, 2000)
    box, col = B.box, B.col
    sk = dict(seed=17)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12, 0], [
        box(0, 12.6, 0, 8.6, 3.0, 6.0, "cloth", r="hide", zone="back", seed=74),
        B.c([-4.6, 2.2, -3.2], [9.2, 9.6, 6.6], "cloth", r="hide", hem=2.6, seed=75, skip=("up", "down"), band=(4.4, 5.6), band_r="trim"),
        box(0, 13.8, 0, 9.4, 1.2, 6.8, "leather", stitch=False),
        *[box(-3.0 + i * 1.5, 12.4, -3.6, 0.6, 1.6, 0.6, "bone", seed=102 + i) for i in range(5)],
    ])
    B.bone("chest", "hips", [0, 14, 0], [
        box(0, 19.4, 0.2, 9.0, 10.0, 6.0, "cloth", r="hide", zone="front", seed=76),
        box(0, 24.0, 0.8, 12.0, 3.4, 8.4, "fur", r="fur", zone="back", seed=26),
        box(0, 21.6, 3.6, 10.4, 6.0, 2.0, "fur", r="fur", seed=27),
        # strings of teeth and beads
        *[box(-2.6 + i * 1.3, 21.4 - abs(i - 2) * 0.5, -2.95, 0.6, 1.3, 0.5, "bone" if i % 2 else "glow", seed=106 + i) for i in range(5)],
    ], rot=[22, 0, 0])
    horn = []
    for sx in (-1, 1):
        horn += curl(B, [sx * 2.4, 29.6, -6.4], sx, [(60, 2.2, 1.9), (95, 2.0, 1.7), (140, 1.8, 1.5), (185, 1.6, 1.25), (230, 1.3, 1.0)],
                     "horn", r="horn", tip="+y", z_step=0.5)
    B.bone("head", "chest", [0, 24.2, -2.0], [
        box(0, 27.4, -3.8, 5.6, 5.4, 5.4, "skin", zone="back", **sk),
        box(0, 27.6, -6.9, 6.0, 5.2, 0.9, "bone", seed=108,
            sockets=sockets(1.4, 28.3, (1.0, 0.68), slant=0.4, core=0.34),
            holes=[dict(x=0.4, y=26.4, r=(0.28, 0.5)), dict(x=-0.4, y=26.4, r=(0.28, 0.5))],
            cuts=[dict(pts=[(-0.2, 30.0), (0.6, 28.8), (0.1, 27.6)], w=0.3, faces=("north",))]),
        box(0, 30.6, -4.6, 6.4, 1.2, 4.8, "fur", r="fur", seed=28),
        *horn,
        *[B.c([sx * 2.4 - 0.5, 24.0, -6.4], [1.0, 2.8, 1.0], "horn", r="tusk", tip="+y", pivot=[sx * 2.4, 24.6, -6.0], rot=[-8, 0, sx * -12]) for sx in (-1, 1)],
    ], rot=[-14, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 5.4, 23.0, 0.4], [
            col(sx * 5.8, 0.4, 16.6, 23.4, 2.6, 2.8, "cloth", r="hide", seed=77),
        ], rot=[-10, 0, sx * -6])
        hand = [col(sx * 5.8, 0.2, 10.6, 16.8, 2.0, 2.0, "skin", zone="limb", **sk),
                col(sx * 5.8, 0.2, 12.0, 13.2, 2.4, 2.4, "bone", seed=113),
                box(sx * 5.8, 9.8, 0, 2.2, 1.8, 2.4, "skin", **sk)]
        if sx > 0:
            # the staff: a long shaft, a beast skull, a crystal held in its jaws, feathers
            hand += [
                col(6.0, -1.6, -1.0, 30.0, 1.0, 1.0, "wood", seed=123),
                box(6.0, 31.0, -1.8, 2.6, 2.4, 3.4, "bone", seed=109, sockets=[dict(x=6.6, y=31.4, r=(0.35, 0.3), face="west", core=0.5), dict(x=-1.0, y=31.4, r=(0.35, 0.3), face="east", core=0.5)]),
                B.c([5.5, 32.0, -2.6], [1.0, 3.4, 1.0], "crystal", skip=("down",)),
                B.c([5.1, 27.2, -1.4], [0.3, 3.4, 1.2], "cloth", r="feather", seed=78, hem=0.8, pivot=[6.0, 30.0, -1.6], rot=[0, 0, 18]),
                B.c([6.6, 27.6, -1.8], [0.3, 3.0, 1.2], "cloth", r="feather", seed=79, hem=0.8, pivot=[6.0, 30.0, -1.6], rot=[0, 0, -14]),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.8, 17.0, 0.4], hand, rot=[-26 if sx > 0 else -10, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.2, 11.0, 0], [
            col(sx * 2.2, 0, 4.6, 11.4, 3.0, 3.0, "skin", zone="limb", **sk)])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.2, 4.8, 0], [
            col(sx * 2.2, 0, 1.0, 5.0, 2.6, 2.6, "skin", zone="limb", **sk),
            col(sx * 2.2, 0, 1.6, 4.4, 3.0, 3.0, "fur", r="fur", seed=29),
            box(sx * 2.2, 0.6, -0.8, 3.0, 1.2, 4.0, "skin", r="skin_d", seed=18),
        ])
    register(name, lambda aff: pal(aff, (90, 240, 200), {
        "skin": (108, 116, 96), "skin_d": (56, 62, 52), "skin_l": (156, 152, 126),
        "hide": (118, 90, 64), "fur": (96, 84, 72), "horn": (60, 52, 44), "tusk": (204, 190, 156),
        "bone": (212, 202, 176), "feather": (40, 36, 34), "trim": (150, 120, 70),
    }), density=5.0)
    B.write(1.8, 2.4, 1.1)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [3 * wave(t, L, 0.2), 10 * wave(t, L * 2), 0]),
        "left_arm": rot(lambda t: [3 * wave(t, L), 0, 0]),
        "right_arm": rot(lambda t: [-6 * wave(t, L), 0, 6 * wave(t, L, 0.25)]),
    }, 0.1)
    L = 1.3
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=22, arm_swing=10, knee=20, bob=0.5, body="hips"),
        "left_arm": rot(lambda t: [-12 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [3 * wave(t, L / 2), 0, 0]),
    }, 0.05)
    # cast (1.0 s): the staff raised high, the bolt let go at 0.55 s
    L = 1.0
    A["cast"] = anim(L, False, {
        "left_arm": rot(lambda t: [-110 * hold(t, 0, 0.45, 0.6, 1.0) - 20 * snap(t, 0.45, 0.55, 0.6, 1.0), 0, -10 * hold(t, 0, 0.45, 0.6, 1.0)]),
        "right_arm": rot(lambda t: [-70 * sin01(t, 0.2, 0.8), 30 * sin01(t, 0.2, 0.8), 0]),
        "chest": rot(lambda t: [-16 * hold(t, 0, 0.45, 0.55, 0.9) + 10 * snap(t, 0.45, 0.58, 0.6, 1.0), 0, 0]),
        "head": rot(lambda t: [-20 * hold(t, 0, 0.45, 0.55, 0.9), 0, 0]),
    }, 0.025)
    # mend (1.2 s): the staff driven into the ground at 0.6 s, a pulse that heals its kin
    L = 1.2
    A["mend"] = anim(L, False, {
        "left_arm": rot(lambda t: [-70 * hold(t, 0, 0.45, 0.5, 0.62) + 20 * snap(t, 0.5, 0.62, 0.8, 1.2), 0, 0]),
        "left_hand": rot(lambda t: [20 * hold(t, 0, 0.45, 0.5, 0.62), 0, 0]),
        "chest": rot(lambda t: [-20 * hold(t, 0, 0.45, 0.5, 0.62) + 18 * snap(t, 0.5, 0.64, 0.8, 1.2), 0, 0]),
        "head": rot(lambda t: [-30 * hold(t, 0, 0.45, 0.5, 0.62), 0, 0]),
        "right_arm": rot(lambda t: [-90 * sin01(t, 0.4, 1.1), -30 * sin01(t, 0.4, 1.1), 0]),
        "hips": {"position": lambda t: [0, -1.2 * snap(t, 0.5, 0.64, 0.8, 1.2), 0]},
    }, 0.025)
    L = 1.4
    A["death"] = anim(L, "hold", {
        **fall_over(L, side=1, drop=0.0),
        "chest": rot(lambda t: [-15 * sin01(t, 0, 0.5), 0, 0]),
        "head": rot(lambda t: [-20 * sin01(t, 0, 0.5), 0, 15 * window(t, 0.6, 1.0)]),
        "left_arm": rot(lambda t: [-30 * window(t, 0.1, 0.6), 0, -40 * window(t, 0.3, 0.8)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- deepkin

def deepkin():
    """A fish-man of the drowned gates: hunched and scaled, dark blue-green above and pale below, a broad fish head
    with round glowing eyes on its sides and a wide mouth of needle teeth, fins on its head, back and arms, glowing
    spots along its flanks, and a three-pronged spear of coral and bone."""
    name = "deepkin"
    B = Body(name, 2100)
    box, col = B.box, B.col
    sc = dict(size=1.1, seed=33)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12.5, 0.5], [
        box(0, 12.8, 0.6, 6.6, 3.2, 4.4, "scales", zone="back", **sc),
        B.c([-2.4, 5.6, -1.8], [4.8, 7.4, 0.4], "cloth", r="kelp", hem=2.4, seed=80, skip=("up", "down")),
        B.c([-2.6, 6.2, 2.7], [5.2, 6.8, 0.4], "cloth", r="kelp", hem=2.2, seed=81, skip=("up", "down")),
    ])
    spots = dict(spots=dict(glow=True, cut=0.86, k=1.4, seed=13))
    B.bone("chest", "hips", [0, 14.0, 0.6], [
        box(0, 19.6, 0.4, 7.8, 10.2, 5.4, "scales", zone="front", bib_w=0.3, **sc, **spots),
        B.c([-0.2, 15.0, 3.0], [0.4, 9.6, 3.4], "skin", r="fin", rays=dict(y=15.0, z=3.0, n=8), skip=("up", "down", "north", "south")),
        *[box(sx * 4.4, 23.2, 0.6, 2.2, 1.6, 4.8, "scales", **sc) for sx in (-1, 1)],
    ], rot=[24, 0, 0])
    gills = [dict(pts=[(-3.0 + k * 0.8, 24.6), (-2.7 + k * 0.8, 26.6)], w=0.28, faces=("west", "east"), tone=0.1) for k in range(3)]
    head = [
        box(0, 27.2, -4.0, 7.2, 6.0, 8.0, "scales", zone="back", split=(0.6, 0.3), **sc,
            eyes=[dict(x=-6.0, y=28.4, r=(1.4, 1.4), face="west", pupil="dot", lid=0.5),
                  dict(x=-6.0, y=28.4, r=(1.4, 1.4), face="east", pupil="dot", lid=0.5)],
            grin=dict(x0=-3.4, x1=3.4, y=25.4, h=1.7, curve=-0.35, n=11, fangs=True),
            cuts=gills),
        B.c([-0.2, 29.8, -7.0], [0.4, 3.0, 7.6], "skin", r="fin", rays=dict(y=29.8, z=-7.0, n=7, dir=-1), skip=("up", "down", "north", "south")),
        *[B.c([sx * 3.6 - 0.25, 23.2, -7.8], [0.5, 2.4, 0.5], "skin", r="fin", seed=19) for sx in (-1, 1)],
    ]
    B.bone("head", "chest", [0, 24.4, -1.2], head, rot=[-22, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 4.4, 22.6, 0.6], [
            col(sx * 4.9, 0.6, 16.0, 23.2, 2.4, 2.6, "scales", zone="limb", **sc)], rot=[-26, 0, sx * -6])
        hand = [
            col(sx * 5.0, 0.6, 9.6, 16.4, 2.2, 2.4, "scales", zone="limb", **sc),
            B.c([sx * 5.0 - 0.2, 10.4, 1.8], [0.4, 5.6, 2.6], "skin", r="fin", rays=dict(y=16.0, z=1.8, n=6), skip=("up", "down", "north", "south")),
            box(sx * 5.0, 8.8, 0.2, 2.6, 1.8, 2.8, "skin", r="web", seed=20),
            *[col(sx * 5.0 + (i - 1) * 0.8, -0.6, 6.8, 8.2, 0.5, 0.5, "horn", r="claw", tip="-y") for i in range(3)],
        ]
        if sx < 0:
            # the spear, held low and pointing ahead: a shaft, a binding, three prongs of coral and bone
            hand += [
                B.bar(-5.0, 9.0, -11.0, 7.0, 0.9, 0.9, "wood", long="z"),
                B.bar(-5.0, 9.0, -12.2, -10.6, 1.4, 1.4, "leather", stitch=False),
                B.bar(-5.0, 9.0, -16.4, -12.2, 0.7, 0.7, "bone", seed=110),
                B.bar(-5.0, 10.3, -15.2, -12.2, 0.6, 0.6, "bone", seed=111),
                B.bar(-5.0, 7.7, -15.2, -12.2, 0.6, 0.6, "bone", seed=112),
                B.bar(-5.0, 10.3, -12.6, -11.8, 0.6, 3.2, "horn", r="coral", tip="-z"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.0, 16.4, 0.6], hand, rot=[-30, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.2, 12.0, 0.6], [
            col(sx * 2.3, 0.6, 6.4, 12.4, 2.8, 3.0, "scales", zone="limb", **sc)], rot=[-24, 0, sx * -6])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.3, 6.6, 0.6], [
            col(sx * 2.3, 0.6, 1.0, 6.8, 2.3, 2.4, "scales", zone="limb", **sc),
            B.c([sx * 2.3 - 0.2, 1.6, 1.8], [0.4, 4.4, 2.0], "skin", r="fin", rays=dict(y=6.0, z=1.8, n=5), skip=("up", "down", "north", "south")),
            box(sx * 2.3, 0.5, -1.4, 3.2, 1.0, 5.0, "skin", r="web", seed=21),
        ], rot=[30, 0, sx * 6])
    register(name, lambda aff: pal(aff, (90, 255, 230), {
        "scale": (52, 90, 98), "scale_d": (22, 44, 58), "scale_l": (178, 196, 170),
        "fin": (66, 124, 128), "web": (60, 96, 100), "kelp": (44, 70, 40), "claw": (40, 40, 40),
        "bone": (214, 204, 180), "coral": (190, 80, 74),
    }), density=5.0)
    B.write(1.6, 2.2, 1.0)

    A = {}
    L = 2.6
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [3 * wave(t, L / 2), 12 * wave(t, L * 2), 6 * wave(t, L, 0.3)]),
        "left_arm": rot(lambda t: [4 * wave(t, L), 0, 0]), "right_arm": rot(lambda t: [-3 * wave(t, L), 0, 0]),
    }, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=26, arm_swing=14, knee=24, bob=0.6, body="hips"),
        "hips": {"rotation": lambda t: [0, 0, 6 * wave(t, L)], "position": lambda t: [0, -0.6 * abs(wave(t, L)), 0]},
        "chest": rot(lambda t: [6, 0, -4 * wave(t, L)]),
        "head": rot(lambda t: [-4, 4 * wave(t, L), 0]),
    }, 0.05)
    # thrust (0.8 s): the spear drawn back, then driven forward at 0.4 s
    L = 0.8
    A["thrust"] = anim(L, False, {
        "right_arm": rot(lambda t: [-30 * hold(t, 0, 0.3, 0.32, 0.42) - 40 * snap(t, 0.32, 0.42, 0.5, 0.8), 0, 0]),
        "right_hand": {"position": lambda t: [0, 0, 3 * hold(t, 0, 0.3, 0.32, 0.4) - 7 * snap(t, 0.32, 0.42, 0.5, 0.8)]},
        "chest": rot(lambda t: [-10 * hold(t, 0, 0.3, 0.32, 0.4) + 16 * snap(t, 0.32, 0.44, 0.5, 0.8), 20 * hold(t, 0, 0.3, 0.32, 0.42), 0]),
        "hips": {"position": lambda t: [0, 0, -3 * snap(t, 0.32, 0.44, 0.5, 0.8)]},
        "head": rot(lambda t: [-10 * sin01(t, 0.2, 0.7), 0, 0]),
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [-80 * ease_in(window(t, 0.15, 0.7)), 0, 0], "position": lambda t: [0, -1 * window(t, 0.15, 0.7), 4 * window(t, 0.15, 0.7)]},
        "head": rot(lambda t: [-20 * window(t, 0.1, 0.5) + 6 * math.sin(t * 40) * window(t, 0.7, 0.8) * (1 - window(t, 0.9, 1.2)), 0, 0]),
        "right_arm": rot(lambda t: [-60 * window(t, 0.2, 0.7), 0, 30 * window(t, 0.2, 0.7)]),
        "left_arm": rot(lambda t: [-60 * window(t, 0.2, 0.7), 0, -30 * window(t, 0.2, 0.7)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- wings

def membrane_fx(f, p, s):
    """A wing membrane (a flat cube lying in x-z): veins, darker near the bones, a scalloped trailing edge cut out,
    and the far end tapered."""
    w = s["wing"]
    root, lead, depth, span, n = w["root"], w["lead"], w["depth"], w["span"], w.get("n", 3)
    if f.face not in ("up", "down"):
        return
    pp = abs(f.X) - root
    qq = f.Z - lead
    k = np.clip(pp / span, 0, 1)
    edge = depth * (1 - w.get("taper", 0.0) * k) * (1 - 0.32 * np.abs(np.sin(math.pi * k * n)))
    vein = np.abs(CS.fbm2(pp * 0.35 + 3, qq * 0.35, 140, 2) - 0.5)
    p.over(s.get("r", "membrane"), 0.18, vein < 0.016)
    p.shade(-0.14 * CS.sstep(1.6, 0.0, qq))
    p.cut((qq < edge) & (pp > -0.1) & (pp < span + 0.1))


def wing_pair(B, side, sx, parent, shoulder, inner, outer, mat="skin", spec=None, bones_r="bone", fingers=3):
    """A bat wing on side sx: an inner part from the shoulder to the elbow and an outer part folding at the elbow.
    inner/outer: (span, depth) in units; the membranes lie flat (in x-z) behind a bony leading edge."""
    spec = dict(spec or {})
    x0, y0, z0 = shoulder
    span1, depth1 = inner
    span2, depth2 = outer
    th = 0.3
    B.bone(f"{side}_wing", parent, [x0, y0, z0], [
        B.c(mirror_x([abs(x0), y0 - th / 2, z0], [span1, th, depth1], sx), [span1, th, depth1], mat, r="membrane",
            wing=dict(root=abs(x0), lead=z0, depth=depth1, span=span1, n=2), fx=membrane_fx, skip=("north", "south", "east", "west"), marks=0.5, **spec),
        B.c(mirror_x([abs(x0), y0 - 0.5, z0 - 0.5], [span1, 1.0, 1.0], sx), [span1, 1.0, 1.0], "bone", r=bones_r),
    ])
    ex = abs(x0) + span1
    outer_cubes = [
        B.c(mirror_x([ex, y0 - th / 2, z0], [span2, th, depth2], sx), [span2, th, depth2], mat, r="membrane",
            wing=dict(root=ex, lead=z0, depth=depth2, span=span2, n=fingers, taper=0.7), fx=membrane_fx, skip=("north", "south", "east", "west"), marks=0.5, **spec),
        B.c(mirror_x([ex, y0 - 0.4, z0 - 0.4], [span2 * 0.95, 0.8, 0.8], sx), [span2 * 0.95, 0.8, 0.8], "bone", r=bones_r),
        B.c(mirror_x([ex - 0.6, y0 - 0.7, z0 - 1.6], [1.0, 1.4, 1.6], sx), [1.0, 1.4, 1.6], "horn", r="claw", tip="-z"),
    ]
    B.bone(f"{side}_wing2", f"{side}_wing", [sx * ex, y0, z0], outer_cubes)


def flap(L, amp=40, fold=20, phase=0.0, bias=0.0, sides=(("right", -1), ("left", 1))):
    out = {}
    for side, sx in sides:
        out[f"{side}_wing"] = rot(lambda t, sx=sx: [0, 0, -sx * (amp * wave(t, L, phase) + bias)])
        out[f"{side}_wing2"] = rot(lambda t, sx=sx: [0, 0, -sx * (fold * wave(t, L, phase - 0.12))])
    return out


# --------------------------------------------------------------------------------------------- duskwing

def duskwing():
    """A great bat of the dark gates: a wingspan wider than a man is tall, a pug face with a leaf of a nose, tall ears,
    small red eyes and long fangs, fur dark above and dusky below, wings of thin skin over long finger bones."""
    name = "duskwing"
    B = Body(name, 2200)
    box, col = B.box, B.col
    fur = dict(seed=30, len=1.6, width=1.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 7, 0], [
        box(0, 7.0, 0.6, 5.0, 4.6, 7.2, "fur", r="fur", zone="back", **fur),
        box(0, 6.6, -2.8, 5.8, 4.8, 2.6, "fur", r="fur_l", **fur),
        box(0, 6.2, 4.6, 3.2, 2.6, 2.4, "fur", r="fur", **fur),
    ])
    B.bone("head", "body", [0, 7.8, -3.4], [
        box(0, 8.2, -5.6, 4.6, 4.0, 3.8, "fur", r="fur", zone="back", split=(0.55, 0.2), **fur,
            eyes=eyes(1.15, 8.9, (0.7, 0.45), slant=0.45, pupil=None, lid=1.4)),
        box(0, 7.0, -8.0, 2.6, 2.2, 1.4, "skin", zone="back", seed=40, holes=[dict(x=0.5, y=7.4, r=(0.3, 0.3)), dict(x=-0.5, y=7.4, r=(0.3, 0.3))]),
        B.c([-0.7, 8.0, -8.5], [1.4, 2.0, 0.6], "skin", r="skin_d", seed=41, skip=("south",)),
        box(0, 6.1, -8.2, 2.2, 0.6, 0.4, "teeth", n=2, fangs=1, up=True, small=0.3, skip=("up", "down", "south")),
        *[B.c(mirror_x([0.9, 9.6, -5.4], [1.8, 5.2, 0.6], sx), [1.8, 5.2, 0.6], "skin", r="ear", pivot=[sx * 1.6, 9.8, -5.1], rot=[-10, 0, sx * -20],
              skip=("down",), zone="limb", seed=42) for sx in (-1, 1)],
    ])
    B.bone("jaw", "head", [0, 6.6, -6.0], [
        box(0, 5.9, -7.4, 2.4, 0.9, 2.4, "skin", seed=43),
        box(0, 6.5, -8.45, 2.0, 0.5, 0.3, "teeth", n=2, fangs=1, up=False, skip=("up", "down", "south")),
    ], rot=[10, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        wing_pair(B, side, sx, "body", [sx * 2.4, 8.4, -2.0], (7.6, 7.6), (10.4, 7.0))
        B.bone(f"{side}_foot", "body", [sx * 1.6, 5.0, 3.8], [
            col(sx * 1.6, 3.8, 2.6, 5.2, 0.9, 0.9, "skin", seed=44),
            *[B.c([sx * 1.6 - 0.75 + i * 0.6, 2.0, 3.0], [0.4, 0.8, 0.9], "horn", r="claw", tip="-y") for i in range(3)],
        ], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 56, 56), {
        "fur": (60, 52, 60), "fur_d": (26, 22, 30), "fur_l": (122, 104, 102), "skin": (78, 56, 60), "ear": (84, 58, 62),
        "membrane": (70, 44, 52), "bone": (104, 88, 82), "claw": (30, 26, 26),
    }), density=6.0)
    B.write(3.0, 1.2, 0.4)

    A = {}
    L = 0.7
    A["idle"] = anim(L, True, {
        **flap(L, 38, 26),
        "body": {"position": lambda t: [0, 1.2 * wave(t, L, 0.25), 0], "rotation": lambda t: [6 * wave(t, L, 0.1), 0, 0]},
        "head": rot(lambda t: [-6 * wave(t, L, 0.1), 0, 0]),
        "right_foot": rot(lambda t: [8 * wave(t, L), 0, 0]), "left_foot": rot(lambda t: [8 * wave(t, L), 0, 0]),
    }, 0.025)
    L = 0.5
    A["walk"] = anim(L, True, {
        **flap(L, 44, 30),
        "body": {"position": lambda t: [0, 1.0 * wave(t, L, 0.25), 0], "rotation": lambda t: [14 + 5 * wave(t, L, 0.1), 0, 0]},
        "head": rot(lambda t: [-14, 0, 0]),
        "right_foot": rot(lambda t: [30, 0, 0]), "left_foot": rot(lambda t: [30, 0, 0]),
    }, 0.025)
    # bite (0.6 s): wings thrown back, a dive at the face, jaws snapping at 0.3 s
    L = 0.6
    A["bite"] = anim(L, False, {
        **{f"{s}_wing": rot(lambda t, sx=sx: [0, -30 * sin01(t, 0.0, 0.5), -sx * 30 * sin01(t, 0.0, 0.5)]) for s, sx in (("right", -1), ("left", 1))},
        "body": {"rotation": lambda t: [30 * sin01(t, 0.1, 0.5), 0, 0], "position": lambda t: [0, -2 * sin01(t, 0.1, 0.5), -3 * sin01(t, 0.15, 0.45)]},
        "jaw": rot(lambda t: [40 * hold(t, 0.05, 0.22, 0.28, 0.34), 0, 0]),
        "head": rot(lambda t: [-20 * hold(t, 0.05, 0.22, 0.28, 0.4), 0, 0]),
    }, 0.025)
    L = 1.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 160 * ease_in(window(t, 0.1, 0.6))], "position": lambda t: [0, 6 * ease_in(window(t, 0.1, 0.6)), 0]},
        **{f"{s}_wing": rot(lambda t, sx=sx: [0, -60 * window(t, 0.1, 0.6), sx * 50 * window(t, 0.1, 0.6)]) for s, sx in (("right", -1), ("left", 1))},
        **{f"{s}_wing2": rot(lambda t, sx=sx: [0, 0, sx * 90 * window(t, 0.2, 0.7)]) for s, sx in (("right", -1), ("left", 1))},
        "jaw": rot(lambda t: [30 * window(t, 0.1, 0.4), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- hive drone

def hive_drone():
    """A drone of the hive gates, the size of a dog: a black-and-amber banded body, a glowing sting, compound eyes
    that glow, jagged mandibles, four buzzing wings and six thin legs."""
    name = "hive_drone"
    B = Body(name, 2300)
    box, col = B.box, B.col
    ch = dict(seed=41, band=1.4)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 8, 0], [
        box(0, 8.2, -0.6, 3.6, 3.4, 4.0, "chitin", r="chitin", axis="z", **ch),
        box(0, 10.0, -0.6, 2.6, 0.6, 3.0, "chitin", r="stripe", axis="z", seed=43),
        box(0, 7.8, 1.9, 1.2, 1.2, 1.6, "chitin", r="chitin", axis="z", seed=44),
    ])
    B.bone("abdomen", "body", [0, 7.8, 2.6], [
        B.bar(0, 7.6, 2.6, 5.4, 3.4, 3.2, "chitin", r="chitin", axis="z", **ch),
        B.bar(0, 7.4, 5.4, 6.2, 3.2, 3.0, "chitin", r="stripe", axis="z", seed=48),
        B.bar(0, 7.2, 6.2, 8.8, 3.0, 2.8, "chitin", r="chitin", axis="z", **ch),
        B.bar(0, 7.0, 8.8, 9.4, 2.6, 2.4, "chitin", r="stripe", axis="z", seed=49),
        B.bar(0, 6.8, 9.4, 11.4, 2.0, 1.9, "chitin", r="chitin", axis="z", **ch),
        B.c([-0.3, 6.5, 11.2], [0.6, 0.6, 3.2], "crystal", skip=("north",)),
    ], rot=[-20, 0, 0])
    B.bone("head", "body", [0, 8.6, -2.6], [
        box(0, 8.6, -3.8, 3.0, 2.8, 2.4, "chitin", r="chitin", axis="y", **ch),
        *[B.c(mirror_x([1.3, 7.4, -5.0], [1.0, 2.6, 2.4], sx), [1.0, 2.6, 2.4], "crystal") for sx in (-1, 1)],
        *[B.c(mirror_x([0.1, 6.4, -6.8], [1.6, 0.8, 2.2], sx), [1.6, 0.8, 2.2], "horn", r="mandible", tip="-z", pivot=[sx * 0.9, 6.8, -5.0],
              rot=[0, sx * 28, 0]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.3, 6.2, -7.6], [0.6, 0.8, 0.9], sx), [0.6, 0.8, 0.9], "horn", r="mandible", tip="-x" if sx > 0 else "+x", pivot=[sx * 0.9, 6.8, -5.0],
              rot=[0, sx * 28, 0]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.5, 9.6, -5.6], [0.3, 0.3, 4.8], sx), [0.3, 0.3, 4.8], "chitin", r="chitin", pivot=[sx * 0.6, 9.8, -4.8],
              rot=[-38, sx * -18, 0]) for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        for i, (z, sweep, ln) in enumerate(((-1.6, -30, 10.0), (0.2, -8, 7.6))):
            B.bone(f"{side}_wing{i}", "body", [sx * 1.4, 10.1, z], [
                B.c(mirror_x([1.4, 9.95, z - 1.0], [ln, 0.3, 2.2], sx), [ln, 0.3, 2.2], "skin", r="wingskin", marks=0.0, seed=45,
                    fx=wing_veins, skip=("north", "south", "east", "west")),
            ], rot=[0, sx * sweep, sx * -12])
        for j, z in enumerate((-1.8, -0.6, 0.6)):
            B.bone(f"{side}_leg{j}", "body", [sx * 1.5, 6.8, z], [
                B.c(mirror_x([1.3, 6.4, z - 0.3], [2.6, 0.6, 0.6], sx), [2.6, 0.6, 0.6], "chitin", r="chitin", axis="x", seed=46),
                B.c(mirror_x([3.5, 1.4, z - 0.25], [0.5, 5.2, 0.5], sx), [0.5, 5.2, 0.5], "chitin", r="chitin", axis="y", seed=47),
                B.c(mirror_x([3.55, 0.2, z - 0.2], [0.4, 1.4, 0.4], sx), [0.4, 1.4, 0.4], "chitin", r="stripe", axis="y", seed=50),
            ], rot=[(j - 1) * -14 + 26, 0, sx * -14])
    register(name, lambda aff: pal(aff, (255, 150, 30), {
        "chitin": [(3, 3, 5), (12, 12, 18), (30, 30, 40), (70, 72, 90), (150, 156, 180)],
        "stripe": [(46, 14, 2), (120, 46, 8), (204, 104, 26), (240, 160, 70), (255, 226, 160)],
        "wingskin": (92, 98, 114), "mandible": (30, 24, 22),
    }, tinted=("stripe", "wingskin")), density=6.0)
    B.write(2.0, 1.2, 0.5)

    buzz = {}
    for side, sx in (("right", -1), ("left", 1)):
        for i in range(2):
            buzz[f"{side}_wing{i}"] = rot(lambda t, sx=sx, i=i: [0, 0, -sx * (36 if (round(t / 0.025) + i) % 2 else -24)])
    A = {}
    L = 1.2
    A["idle"] = anim(L, True, {
        **buzz,
        "body": {"position": lambda t: [0, 0.8 * wave(t, L), 0]},
        "abdomen": rot(lambda t: [4 * wave(t, L / 2), 0, 0]),
        "head": rot(lambda t: [0, 10 * wave(t, L, 0.3), 0]),
    }, 0.025)
    L = 0.6
    A["walk"] = anim(L, True, {
        **buzz,
        "body": {"position": lambda t: [0, 0.6 * wave(t, L), 0], "rotation": lambda t: [12, 0, 4 * wave(t, L)]},
        "abdomen": rot(lambda t: [6, 0, 0]),
        **{f"{s}_leg{j}": rot(lambda t: [30, 0, 0]) for s in ("right", "left") for j in range(3)},
    }, 0.025)
    # sting (0.6 s): the abdomen swings under and forward, the sting stabbing at 0.32 s
    L = 0.6
    A["sting"] = anim(L, False, {
        **buzz,
        "abdomen": rot(lambda t: [70 * hold(t, 0.0, 0.25, 0.3, 0.55) + 20 * snap(t, 0.25, 0.32, 0.36, 0.55), 0, 0]),
        "body": {"rotation": lambda t: [-25 * hold(t, 0.0, 0.25, 0.3, 0.55), 0, 0], "position": lambda t: [0, 0, -2 * snap(t, 0.25, 0.34, 0.4, 0.6)]},
    }, 0.025)
    L = 1.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 170 * ease_in(window(t, 0.1, 0.5))], "position": lambda t: [0, 9 * ease_in(window(t, 0.1, 0.5)), 0]},
        **{f"{s}_leg{j}": rot(lambda t, sx=sx: [0, 0, sx * 50 * window(t, 0.3, 1.0) + 8 * math.sin(t * 50) * (1 - window(t, 0.5, 1.0))])
           for s, sx in (("right", -1), ("left", 1)) for j in range(3)},
        "abdomen": rot(lambda t: [30 * window(t, 0.2, 0.7), 0, 0]),
    }, 0.025)
    write_anims(name, A)


def wing_veins(f, p, s):
    """An insect's wing: dark veins along it and across, a dark spot near the tip."""
    if f.face not in ("up", "down"):
        return
    a = np.abs(f.X)
    p.over(s.get("r", "wingskin"), 0.2, (np.abs((a * 0.9 + f.Z * 0.4) % 2.2 - 1.1) < 0.1) | (np.abs(f.Z - f.cz + 0.25 * np.sin(a)) < 0.12))
    p.over(s.get("r", "wingskin"), 0.85, (f.U > 0.2) & (f.U < 0.8) & (np.abs(f.Z - f.cz) < 0.5) & (np.abs(a - (abs(f.cx) + f.s[0] * 0.35)) < 0.6))
    p.cut(((f.V - 0.5) ** 2 / 0.25 + ((np.abs(f.X - f.cx) / (f.s[0] / 2)) ** 2) * 0.9) < 1.05)


# --------------------------------------------------------------------------------------------- brimling

def brimling():
    """A hovering imp of the burning gates: skinny limbs, a big horned head with a wide glowing grin and slit eyes,
    small bat wings, a long tail with a spade at its end, skin black-red above and ember-orange below."""
    name = "brimling"
    B = Body(name, 2400)
    box, col = B.box, B.col
    sk = dict(seed=51)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 7, 0], [
        box(0, 9.0, 0.2, 4.4, 5.0, 3.0, "skin", zone="front", bib_w=0.3, **sk),
        box(0, 6.0, 0.4, 3.6, 1.8, 2.6, "skin", zone="back", **sk),
        col(0, 1.8, 7.0, 11.2, 0.8, 0.6, "horn", r="horn", tip="+y"),
    ], rot=[10, 0, 0])
    horns = []
    for sx in (-1, 1):
        horns += curl(B, [sx * 1.7, 15.6, -1.0], sx, [(14, 1.8, 1.2), (34, 1.6, 1.0), (62, 1.4, 0.8), (92, 1.2, 0.6)], "horn", r="horn", tip="+y", z_step=0.4)
    B.bone("head", "body", [0, 11.4, -0.4], [
        box(0, 13.6, -1.0, 5.2, 4.6, 4.4, "skin", zone="back", split=(0.6, 0.15), **sk,
            eyes=eyes(1.25, 14.3, (0.95, 0.42), slant=0.55, pupil="slit", lid=1.2),
            grin=dict(x0=-2.2, x1=2.2, y=12.4, h=1.2, curve=0.55, n=8, glow=True, fangs=True)),
        *horns,
        *[B.c(mirror_x([2.4, 13.4, -1.6], [2.2, 1.4, 0.6], sx), [2.2, 1.4, 0.6], "skin", pivot=[sx * 2.5, 14.0, -1.3], rot=[0, sx * -24, sx * 14],
              zone="limb", **sk) for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 2.6, 10.8, 0], [
            col(sx * 2.9, 0, 7.0, 11.0, 1.2, 1.2, "skin", zone="limb", **sk)], rot=[-20, 0, sx * -10])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 2.9, 7.2, 0], [
            col(sx * 2.9, 0, 4.0, 7.2, 1.1, 1.1, "skin", zone="limb", **sk),
            box(sx * 2.9, 3.6, -0.2, 1.4, 1.0, 1.4, "skin", r="skin_d", **sk),
            *[B.c([sx * 2.9 - 0.65 + i * 0.5, 2.0, -0.6], [0.35, 1.6, 0.35], "horn", r="horn", tip="-y") for i in range(3)],
        ], rot=[-30, 0, 0])
        B.bone(f"{side}_leg", "body", [sx * 1.4, 5.6, 0.4], [
            col(sx * 1.4, 0.4, 2.4, 5.8, 1.3, 1.4, "skin", zone="limb", **sk)], rot=[-30, 0, sx * -6])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 1.4, 2.6, 0.4], [
            col(sx * 1.4, 0.4, -0.6, 2.8, 1.0, 1.0, "skin", zone="limb", **sk),
            box(sx * 1.4, -0.8, -0.4, 1.2, 0.8, 1.8, "horn", r="horn", tip="-z"),
        ], rot=[50, 0, 0])
        wing_pair(B, side, sx, "body", [sx * 1.2, 11.2, 1.6], (3.6, 4.2), (5.4, 4.6), fingers=2)
    B.bone("tail", "body", [0, 5.6, 1.6], [
        B.bar(0, 5.6, 1.6, 6.0, 0.9, 0.9, "skin", r="skin_d", **sk)], rot=[30, 0, 0])
    B.bone("tail2", "tail", [0, 5.6, 6.0], [
        B.bar(0, 5.6, 6.0, 10.0, 0.7, 0.7, "skin", r="skin_d", **sk)], rot=[-40, 0, 0])
    B.bone("tail3", "tail2", [0, 5.6, 10.0], [
        B.bar(0, 5.6, 10.0, 12.0, 0.6, 0.6, "skin", r="skin_d", **sk),
        B.c([-1.2, 5.3, 11.6], [2.4, 0.6, 2.6], "horn", r="horn", tip="+z", skip=()),
    ], rot=[-30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 150, 40), {
        "skin": (96, 26, 26), "skin_d": (30, 10, 12), "skin_l": (196, 88, 48),
        "horn": (30, 24, 24), "membrane": (72, 20, 22), "bone": (50, 24, 24), "claw": (26, 18, 18),
    }), density=6.0)
    B.write(2.0, 1.5, 0.5)

    A = {}
    L = 0.8
    A["idle"] = anim(L, True, {
        **flap(L, 34, 18),
        "body": {"position": lambda t: [0, 1.0 * wave(t, L, 0.25), 0]},
        "head": rot(lambda t: [3 * wave(t, L), 8 * wave(t, L * 3), 6 * wave(t, L * 2)]),
        "tail": rot(lambda t: [0, 14 * wave(t, L * 2), 0]), "tail2": rot(lambda t: [0, 20 * wave(t, L * 2, 0.2), 0]),
        "tail3": rot(lambda t: [0, 26 * wave(t, L * 2, 0.4), 0]),
        "right_leg": rot(lambda t: [6 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [-6 * wave(t, L), 0, 0]),
    }, 0.025)
    L = 0.6
    A["walk"] = anim(L, True, {
        **flap(L, 42, 24),
        "body": {"position": lambda t: [0, 0.8 * wave(t, L, 0.25), 0], "rotation": lambda t: [18, 0, 0]},
        "head": rot(lambda t: [-16, 0, 0]),
        "tail": rot(lambda t: [10, 10 * wave(t, L), 0]), "tail2": rot(lambda t: [0, 14 * wave(t, L, 0.2), 0]),
        "right_leg": rot(lambda t: [20, 0, 0]), "left_leg": rot(lambda t: [20, 0, 0]),
    }, 0.025)
    # cast (1.0 s): both hands brought together over the head, flung forward at 0.55 s
    L = 1.0
    A["cast"] = anim(L, False, {
        **flap(L / 2, 30, 14),
        "right_arm": rot(lambda t: [-150 * hold(t, 0, 0.42, 0.48, 0.6) - 70 * snap(t, 0.48, 0.58, 0.7, 1.0), 0, 20 * hold(t, 0, 0.42, 0.48, 0.6)]),
        "left_arm": rot(lambda t: [-150 * hold(t, 0, 0.42, 0.48, 0.6) - 70 * snap(t, 0.48, 0.58, 0.7, 1.0), 0, -20 * hold(t, 0, 0.42, 0.48, 0.6)]),
        "body": rot(lambda t: [-14 * hold(t, 0, 0.42, 0.48, 0.6) + 18 * snap(t, 0.48, 0.6, 0.7, 1.0), 0, 0]),
        "head": rot(lambda t: [-12 * hold(t, 0, 0.42, 0.5, 0.8), 0, 0]),
    }, 0.025)
    # blink (0.5 s): it folds into itself and is gone, then unfolds where it lands
    L = 0.5
    sq = lambda t: max(0.02, 1 - sin01(t, 0.0, 0.5))  # noqa: E731
    A["blink"] = anim(L, False, {"root": {"scale": lambda t: [sq(t), sq(t) * 1.4 if sq(t) < 0.9 else sq(t), sq(t)]},
                                 "body": rot(lambda t: [0, 720 * window(t, 0.0, 0.5), 0])}, 0.025)
    L = 1.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 360 * ease_in(window(t, 0.0, 0.6)), 80 * window(t, 0.3, 0.7)], "position": lambda t: [0, -2 * window(t, 0.3, 0.7), 0]},
        **{f"{s}_wing": rot(lambda t, sx=sx: [0, 0, sx * 60 * window(t, 0.0, 0.5)]) for s, sx in (("right", -1), ("left", 1))},
        "head": rot(lambda t: [25 * window(t, 0.2, 0.6), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- wailing wraith

def wailing_wraith():
    """A drifting ghost in a hood and robes that tear away into nothing below the waist: a void for a face with two
    slits of light and a mouth stretched in a wail, long bony arms with longer claws, rusted chains around it."""
    name = "wailing_wraith"
    B = Body(name, 2500)
    box, col = B.box, B.col
    robe = dict(r="cloth", seed=84)
    B.bone("root", None, [0, 0, 0])
    tatters = []
    for i, (x, z, w, top, bot) in enumerate(((-2.6, -2.9, 2.2, 16, 1.0), (0.2, -3.0, 2.6, 16, 3.0), (2.8, -2.8, 1.8, 16, 0.0),
                                             (-3.4, 0.4, 0.4, 16, 2.0), (3.4, 0.6, 0.4, 16, 1.0), (-1.6, 3.2, 2.6, 16, 0.4), (1.8, 3.1, 2.4, 16, 2.4))):
        size = [w, top - bot, 0.4] if abs(z) > 2 else [0.4, top - bot, 2.6]
        tatters.append(B.c([x - size[0] / 2, bot, z - size[2] / 2], size, "cloth", hem=4.0 + (i % 3), seed=85 + i, skip=("up", "down"), **{k: v for k, v in robe.items() if k != "seed"}))
    B.bone("body", "root", [0, 16, 0], [
        B.c([-3.8, 9.0, -2.8], [7.6, 13.6, 5.8], "cloth", hem=5.0, seed=83, zone="dark", skip=("down",), **{k: v for k, v in robe.items() if k != "seed"}),
        box(0, 23.0, 0.3, 7.0, 3.0, 5.2, "cloth", zone="dark", **robe),
        *tatters,
        # rusted chains wound around it
        B.c([-4.0, 17.0, -3.0], [8.0, 0.8, 0.6], "chain", r="rust", pivot=[0, 17, -3], rot=[0, 0, 16]),
        B.c([-4.0, 13.6, 2.6], [8.0, 0.8, 0.6], "chain", r="rust", pivot=[0, 13.6, 2.6], rot=[0, 0, -12]),
    ], rot=[10, 0, 0])
    hood = dict(r="cloth", seed=86)
    B.bone("head", "body", [0, 24.0, 0], [
        box(0, 27.4, -0.4, 5.4, 5.4, 5.4, "void",
            eyes=eyes(1.2, 28.4, (1.0, 0.3), slant=0.5, pupil=None, lid=0.0, glint=False),
            grin=dict(x0=-1.4, x1=1.4, y=25.9, h=1.9, curve=-0.2, n=1, teeth=False, glow=True)),
        box(0, 31.0, 0.0, 7.4, 0.8, 7.6, "cloth", dark_faces=("down",), **hood),
        box(3.3, 27.6, 0.2, 0.8, 7.0, 7.2, "cloth", dark_faces=("east",), hem=1.0, **{**hood, "seed": 87}),
        box(-3.3, 27.6, 0.2, 0.8, 7.0, 7.2, "cloth", dark_faces=("west",), hem=1.0, **{**hood, "seed": 88}),
        box(0, 27.6, 3.4, 5.8, 7.0, 0.8, "cloth", dark_faces=("north",), **hood),
        box(0, 30.8, -3.6, 7.4, 1.6, 0.8, "cloth", dark_faces=("down", "south"), **hood),
        B.c([-0.4, 31.4, -4.2], [0.8, 1.8, 0.8], "cloth", **hood, pivot=[0, 31.4, -3.6], rot=[30, 0, 0]),
    ], rot=[-6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 3.8, 22.4, 0.3], [
            B.c(mirror_x([3.2, 15.8, -1.6], [2.8, 7.4, 3.4], sx), [2.8, 7.4, 3.4], "cloth", hem=2.6, seed=89, r="cloth"),
        ], rot=[-30, 0, sx * -16])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 16.6, 0], [
            col(sx * 4.3, 0, 9.0, 17.0, 0.7, 0.7, "bone"), col(sx * 4.9, 0, 9.0, 17.0, 0.7, 0.7, "bone"),
            box(sx * 4.6, 8.6, -0.2, 1.8, 1.0, 1.8, "bone"),
            *[B.c([sx * 4.6 - 0.95 + i * 0.7, 3.6, -0.7 + (i % 2) * 0.3], [0.4, 4.6, 0.4], "horn", r="claw", tip="-y", pivot=[sx * 4.6, 8.2, 0], rot=[-12, 0, (i - 1) * -8])
              for i in range(3)],
        ], rot=[-20, 0, 0])
    register(name, lambda aff: pal(aff, (200, 255, 225), {
        "cloth": (46, 48, 58), "cloth_d": (18, 18, 26), "cloth_l": (96, 98, 110),
        "bone": (176, 170, 150), "claw": (26, 24, 28), "rust": (92, 66, 50),
    }), density=5.0)
    B.write(1.6, 2.4, 1.0)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "root": {"position": lambda t: [0, 1.2 * wave(t, L), 0]},
        "body": rot(lambda t: [10 + 2 * wave(t, L, 0.2), 0, 2 * wave(t, L * 2)]),
        "head": rot(lambda t: [3 * wave(t, L, 0.3), 12 * wave(t, L * 2, 0.1), 0]),
        "right_arm": rot(lambda t: [6 * wave(t, L, 0.1), 0, 4 * wave(t, L)]), "left_arm": rot(lambda t: [6 * wave(t, L, 0.6), 0, -4 * wave(t, L)]),
        "right_hand": rot(lambda t: [8 * wave(t, L, 0.3), 0, 0]), "left_hand": rot(lambda t: [8 * wave(t, L, 0.8), 0, 0]),
    }, 0.1)
    L = 2.0
    A["walk"] = anim(L, True, {
        "root": {"position": lambda t: [0, 0.8 * wave(t, L), 0]},
        "body": rot(lambda t: [22 + 2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [-12, 0, 0]),
        "right_arm": rot(lambda t: [26 + 6 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [26 + 6 * wave(t, L, 0.5), 0, 0]),
    }, 0.1)
    # claw (0.8 s): the right claw raised and raked down at 0.42 s
    L = 0.8
    A["claw"] = anim(L, False, {
        "right_arm": rot(lambda t: [-130 * hold(t, 0, 0.32, 0.34, 0.44) + 40 * snap(t, 0.34, 0.46, 0.55, 0.8), 0, 0]),
        "right_hand": rot(lambda t: [-40 * hold(t, 0, 0.32, 0.34, 0.44), 0, 0]),
        "body": rot(lambda t: [-12 * hold(t, 0, 0.32, 0.34, 0.44) + 16 * snap(t, 0.34, 0.48, 0.55, 0.8), 0, 0]),
    }, 0.025)
    # wail (1.4 s): arms flung wide, head thrown back, the scream at 0.7 s
    L = 1.4
    A["wail"] = anim(L, False, {
        "right_arm": rot(lambda t: [-40 * hold(t, 0.1, 0.6, 1.0, 1.4), 0, -70 * hold(t, 0.1, 0.6, 1.0, 1.4)]),
        "left_arm": rot(lambda t: [-40 * hold(t, 0.1, 0.6, 1.0, 1.4), 0, 70 * hold(t, 0.1, 0.6, 1.0, 1.4)]),
        "head": rot(lambda t: [-35 * hold(t, 0.2, 0.65, 1.0, 1.4) + 3 * math.sin(t * 60) * window(t, 0.65, 0.7) * (1 - window(t, 0.95, 1.0)), 0, 0]),
        "body": rot(lambda t: [-16 * hold(t, 0.2, 0.65, 1.0, 1.4), 0, 0]),
        "root": {"position": lambda t: [0, 2 * hold(t, 0.1, 0.65, 1.0, 1.4), 0]},
    }, 0.025)
    L = 1.2
    fade = lambda t: max(0.01, 1 - ease_in(window(t, 0.4, 1.15)))  # noqa: E731
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, 6 * ease_in(window(t, 0.2, 1.1)), 0], "scale": lambda t: [fade(t), fade(t) * (1 + 0.4 * window(t, 0.3, 1.0)), fade(t)]},
        "head": rot(lambda t: [-40 * window(t, 0.0, 0.4), 0, 0]),
        "right_arm": rot(lambda t: [-60 * window(t, 0.0, 0.4), 0, -50 * window(t, 0.0, 0.4)]),
        "left_arm": rot(lambda t: [-60 * window(t, 0.0, 0.4), 0, 50 * window(t, 0.0, 0.4)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- static drifter

def static_drifter():
    """A drifting jellyfish of the storm gates: a dark bell lit from within by a crackling core, glowing veins
    running down it, frilled arms and long thin stinging threads with bright tips."""
    name = "static_drifter"
    B = Body(name, 2600)
    box, col = B.box, B.col
    jl = dict(seed=91)
    veins = [dict(pts=[(-5.8 + i * 2.9, 15.2), (-4.0 + i * 2.0, 18.8), (-2.2 + i * 1.1, 21.6)], w=0.3, faces=("north", "south", "east", "west"), halo=0.5)
             for i in range(5)]
    B.bone("root", None, [0, 0, 0])
    B.bone("bell", "root", [0, 18, 0], [
        box(0, 17.6, 0, 12.0, 4.0, 12.0, "skin", r="jelly", zone="back", split=(0.5, 0.2), lines=veins, **jl),
        box(0, 20.8, 0, 9.0, 2.6, 9.0, "skin", r="jelly", zone="back", lines=veins, **jl),
        box(0, 22.8, 0, 5.0, 1.6, 5.0, "skin", r="jelly", **jl),
        B.c([-6.4, 14.2, -6.4], [12.8, 1.6, 12.8], "cloth", r="jelly_d", hem=1.2, seed=92, skip=("up",), spots=dict(glow=True, cut=0.88, k=1.2, seed=5)),
        box(0, 14.4, 0, 6.4, 3.6, 6.4, "glow"),
    ])
    arms = []
    for i, (x, z) in enumerate(((-1.8, -1.8), (1.8, -1.8), (-1.8, 1.8), (1.8, 1.8))):
        arms.append(B.c([x - 0.9, 3.0, z - 0.9], [1.8, 11.0, 1.8], "cloth", r="frill", hem=2.6, seed=93 + i, skip=("up",)))
    B.bone("arms", "bell", [0, 14, 0], arms)
    for k, (x, z) in enumerate(((-5.2, -5.2), (5.2, -5.2), (-5.2, 5.2), (5.2, 5.2))):
        threads = [B.c([x + dx - 0.2, 1.0 + (j % 3) * 1.6, z + dz - 0.2], [0.4, 13.0 - (j % 3) * 1.6, 0.4], "skin", r="thread", seed=94, marks=0.0,
                       fx=thread_tip, skip=("up",)) for j, (dx, dz) in enumerate(((0, 0), (1.2, 0.4), (0.4, 1.2)))]
        B.bone(f"threads{k}", "bell", [x, 14, z], threads)
    register(name, lambda aff: pal(aff, (120, 255, 255), {
        "jelly": [(10, 10, 24), (30, 30, 64), (66, 62, 118), (118, 112, 176), (180, 176, 226)],
        "jelly_d": [(6, 6, 16), (20, 20, 44), (44, 42, 84), (84, 80, 134), (140, 136, 190)],
        "frill": [(20, 14, 34), (54, 40, 84), (100, 80, 140), (150, 130, 190), (210, 196, 236)],
        "thread": (120, 110, 160),
    }, tinted=("jelly", "jelly_d", "frill", "thread")), density=5.0)
    B.write(1.8, 2.0, 0.8)

    A = {}

    def pulse(L, k=1.0):
        return {"bell": {"scale": lambda t: [1 + 0.06 * k * wave(t, L), 1 - 0.1 * k * wave(t, L), 1 + 0.06 * k * wave(t, L)],
                         "position": lambda t: [0, 1.0 * wave(t, L, 0.3), 0]},
                "arms": rot(lambda t: [5 * wave(t, L, 0.2), 0, 4 * wave(t, L * 2)]),
                **{f"threads{k_}": rot(lambda t, k_=k_: [8 * wave(t, L, 0.1 * k_ + 0.3), 0, 8 * wave(t, L * 1.5, 0.2 * k_)]) for k_ in range(4)}}
    L = 2.0
    A["idle"] = anim(L, True, pulse(L), 0.05)
    L = 1.2
    A["walk"] = anim(L, True, {**pulse(L, 1.4), "root": rot(lambda t: [10, 0, 0])}, 0.05)
    # zap (0.8 s): the threads flare out and the bell clenches at 0.4 s
    L = 0.8
    A["zap"] = anim(L, False, {
        "bell": {"scale": lambda t: [1 - 0.15 * snap(t, 0.3, 0.4, 0.45, 0.8), 1 + 0.12 * snap(t, 0.3, 0.4, 0.45, 0.8), 1 - 0.15 * snap(t, 0.3, 0.4, 0.45, 0.8)]},
        **{f"threads{k}": rot(lambda t, k=k: [(-40 if k < 2 else 40) * hold(t, 0.1, 0.38, 0.45, 0.8), 0, (-40 if k % 2 == 0 else 40) * hold(t, 0.1, 0.38, 0.45, 0.8)]) for k in range(4)},
        "arms": {"scale": lambda t: [1 + 0.4 * hold(t, 0.1, 0.38, 0.45, 0.8), 1, 1 + 0.4 * hold(t, 0.1, 0.38, 0.45, 0.8)]},
    }, 0.025)
    # pulse (1.2 s): its storm discharge, held a breath and then let go at 0.7 s
    L = 1.2
    A["pulse"] = anim(L, False, {
        "bell": {"scale": lambda t: [1 - 0.12 * hold(t, 0.1, 0.6, 0.7, 0.8) + 0.2 * snap(t, 0.68, 0.74, 0.8, 1.2)] * 3},
        **{f"threads{k}": rot(lambda t, k=k: [(-60 if k < 2 else 60) * snap(t, 0.68, 0.76, 0.85, 1.2), 0, (-60 if k % 2 == 0 else 60) * snap(t, 0.68, 0.76, 0.85, 1.2)]) for k in range(4)},
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "bell": {"scale": lambda t: [1 + 0.3 * window(t, 0.0, 0.8), max(0.25, 1 - 0.75 * window(t, 0.0, 0.8)), 1 + 0.3 * window(t, 0.0, 0.8)]},
        "root": {"position": lambda t: [0, -10 * ease_in(window(t, 0.2, 1.1)), 0]},
        **{f"threads{k}": rot(lambda t, k=k: [(-30 if k < 2 else 30) * window(t, 0.2, 1.0), 0, 0]) for k in range(4)},
    }, 0.025)
    write_anims(name, A)


def thread_tip(f, p, s):
    """A stinging thread: thin and dark, its last stretch glowing."""
    p.glow("glow", 0.75, f.Y < f.bottom + 1.6)


def cracks_fx(f, p, s):
    """Glowing cracks through the body (molten veins, rune-light seeping through stone)."""
    c = s["cracks"]
    k, w, sd = c.get("k", 0.4), c.get("w", 0.02), c.get("seed", 7)
    ridge = np.abs(CS.fbm3(f.X * k + 3, f.Y * k, f.Z * k, sd, 3) - 0.5)
    mask = CS.fbm3(f.X * 0.2, f.Y * 0.2, f.Z * 0.2, sd + 1, 2) > c.get("cover", 0.4)
    if f.face == "down":
        return
    p.over("black", 0.1, (ridge < w * 1.8) & mask)
    p.glow("glow", 0.55 + 0.4 * CS.sstep(w, 0.0, ridge), (ridge < w) & mask)


def quad_walk(L, legs, swing=30, knee=26, front_phase=0.0):
    """A four-legged walk: legs {name: (low bone or None, phase)}."""
    out = {}
    for leg, (low, ph) in legs.items():
        out[leg] = rot(lambda t, ph=ph: [swing * wave(t, L, ph + front_phase), 0, 0])
        if low:
            out[low] = rot(lambda t, ph=ph: [-knee * max(0.0, wave(t, L, ph + front_phase + 0.25)), 0, 0])
    return out


def shard(B, x, y, z, w, h, rx, rz, mat="crystal"):
    """A crystal growing out of a back: a shaft and a narrower point, leaning."""
    piv = [x, y + 0.6, z]
    return [B.c([x - w / 2, y, z - w / 2], [w, h, w], mat, pivot=piv, rot=[rx, 0, rz], skip=("down",)),
            B.c([x - w * 0.31, y + h, z - w * 0.31], [w * 0.62, h * 0.55, w * 0.62], mat, pivot=piv, rot=[rx, 0, rz], skip=("down",))]


# --------------------------------------------------------------------------------------------- rimeclaw

def rimeclaw():
    """A great bear of the frozen gates: shaggy fur slate-dark on the back and frost-pale beneath, glowing ice growing
    from its shoulders and spine, a heavy brow over small fierce eyes, a dark muzzle full of fangs, and claws like
    hooks of black ice."""
    name = "rimeclaw"
    B = Body(name, 2700)
    box, col = B.box, B.col
    fur = dict(seed=34, len=2.2, width=1.4)
    B.bone("root", None, [0, 0, 0])
    spikes = (shard(B, 0, 21.6, -9.0, 3.0, 5.6, -24, 0) + shard(B, -3.6, 21.0, -7.0, 2.4, 4.4, -30, -26) + shard(B, 3.6, 21.0, -7.0, 2.4, 4.4, -30, 26)
              + shard(B, 0, 21.4, -3.6, 2.4, 4.2, -36, 0) + shard(B, -2.8, 20.6, 0.2, 2.0, 3.2, -40, -22) + shard(B, 2.8, 20.6, 0.2, 2.0, 3.2, -40, 22)
              + shard(B, 0, 20.0, 4.4, 2.0, 3.0, -44, 0))
    B.bone("body", "root", [0, 14, 0], [
        box(0, 15.0, -5.0, 14.0, 13.0, 13.0, "fur", r="fur", zone="back", split=(0.5, 0.28), **fur),
        box(0, 14.0, 6.0, 12.0, 11.0, 11.0, "fur", r="fur", zone="back", split=(0.5, 0.28), **fur),
        box(0, 21.4, -6.0, 12.0, 2.4, 9.0, "fur", r="fur", zone="dark", **fur),
        *spikes,
    ])
    B.bone("head", "body", [0, 17, -11], [
        box(0, 17.6, -15.0, 9.0, 8.0, 8.0, "fur", r="fur", zone="back", split=(0.58, 0.25), **fur,
            eyes=eyes(2.3, 19.6, (1.0, 0.5), slant=0.42, pupil="slit")),
        box(0, 20.6, -19.0, 9.4, 1.6, 1.4, "fur", r="fur_d", **fur),
        box(0, 15.6, -20.6, 6.0, 4.6, 5.0, "fur", r="muzzle", seed=60, len=1.0, width=0.8,
            cuts=[dict(pts=[(-23.0, 13.9), (-18.4, 14.6)], w=0.4, faces=("west", "east"))]),
        box(0, 17.4, -23.2, 2.8, 1.8, 0.8, "dark", t=0.25, holes=[dict(x=0.6, y=17.2, r=(0.4, 0.35)), dict(x=-0.6, y=17.2, r=(0.4, 0.35))]),
        box(0, 13.9, -22.9, 5.4, 1.0, 0.4, "teeth", n=6, fangs=1, up=True, skip=("up", "down", "south")),
        *[box(sx * 3.4, 22.2, -13.6, 2.4, 2.4, 1.4, "fur", r="fur_d", **fur) for sx in (-1, 1)],
        *shard(B, -2.6, 21.2, -14.0, 1.4, 2.6, -30, -20), *shard(B, 2.6, 21.2, -14.0, 1.4, 2.6, -30, 20),
    ])
    B.bone("jaw", "head", [0, 14.4, -15], [
        box(0, 13.0, -19.6, 5.6, 2.2, 6.0, "skin", r="muzzle", seed=61),
        box(0, 14.45, -22.4, 5.0, 0.9, 0.4, "teeth", n=5, fangs=1, up=False, skip=("up", "down", "south")),
        box(0, 12.6, -17.0, 7.2, 2.0, 4.0, "fur", r="fur_l", **fur),
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # the forelegs: heavy shoulders, thick arms, paws with long black claws
        B.bone(f"{side}_front", "body", [sx * 5.2, 15, -8], [
            col(sx * 5.4, -8.2, 6.0, 17.0, 5.4, 6.4, "fur", r="fur", zone="limb", **fur)])
        B.bone(f"{side}_front_low", f"{side}_front", [sx * 5.4, 7.0, -8], [
            col(sx * 5.4, -8.2, 1.6, 7.4, 4.8, 5.2, "fur", r="fur", zone="limb", **fur),
            box(sx * 5.4, 1.2, -9.4, 5.6, 2.4, 6.6, "skin", r="paw", seed=62),
            *[B.c([sx * 5.4 - 2.4 + i * 1.7, 0.0, -14.0], [0.9, 1.6, 2.6], "horn", r="claw", tip="-z") for i in range(3)],
        ])
        B.bone(f"{side}_hind", "body", [sx * 4.6, 14, 8], [
            col(sx * 4.8, 8.6, 6.0, 15.6, 5.4, 7.0, "fur", r="fur", zone="limb", **fur)])
        B.bone(f"{side}_hind_low", f"{side}_hind", [sx * 4.8, 6.6, 8.4], [
            col(sx * 4.8, 8.6, 1.4, 7.0, 4.4, 5.0, "fur", r="fur", zone="limb", **fur),
            box(sx * 4.8, 1.1, 7.6, 5.2, 2.2, 6.4, "skin", r="paw", seed=63),
            *[B.c([sx * 4.8 - 2.2 + i * 1.6, 0.0, 3.4], [0.8, 1.2, 1.8], "horn", r="claw", tip="-z") for i in range(3)],
        ])
    B.bone("tail", "body", [0, 16.6, 11.5], [box(0, 16.4, 12.6, 3.6, 3.2, 2.6, "fur", r="fur", **fur)], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (130, 220, 255), {
        "fur": (88, 102, 120), "fur_d": (34, 42, 58), "fur_l": (206, 220, 230),
        "muzzle": (118, 128, 142), "muzzle_l": (176, 184, 194), "paw": (36, 40, 50), "claw": [(2, 4, 8), (10, 16, 28), (30, 44, 70), (90, 120, 160), (180, 210, 240)],
    }), density=4.0)
    B.write(3.0, 2.4, 1.0)

    A = {}
    legs = {"right_front": ("right_front_low", 0.0), "left_front": ("left_front_low", 0.5),
            "right_hind": ("right_hind_low", 0.5), "left_hind": ("left_hind_low", 0.0)}
    L = 3.2
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.4 * wave(t, L), 0], "rotation": lambda t: [1.5 * wave(t, L), 0, 0]},
        "head": rot(lambda t: [3 * wave(t, L, 0.2), 10 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [4 * max(0, wave(t, L / 2)), 0, 0]),
        "tail": rot(lambda t: [0, 10 * wave(t, L), 0]),
    }, 0.1)
    L = 1.3
    A["walk"] = anim(L, True, {
        **quad_walk(L, legs, swing=26, knee=30),
        "body": {"position": lambda t: [0, -0.6 * abs(wave(t, L)), 0], "rotation": lambda t: [0, 3 * wave(t, L), 3 * wave(t, L)]},
        "head": rot(lambda t: [4 * wave(t, L / 2), -5 * wave(t, L), 0]),
    }, 0.05)
    # swipe (0.9 s): up on the right forepaw, a raking blow at 0.45 s
    L = 0.9
    A["swipe"] = anim(L, False, {
        "right_front": rot(lambda t: [-80 * hold(t, 0, 0.35, 0.38, 0.48) + 30 * snap(t, 0.38, 0.5, 0.6, 0.9), 0, 20 * hold(t, 0, 0.35, 0.5, 0.9)]),
        "right_front_low": rot(lambda t: [-30 * hold(t, 0, 0.35, 0.38, 0.5), 0, 0]),
        "body": rot(lambda t: [-10 * hold(t, 0, 0.35, 0.38, 0.5) + 8 * snap(t, 0.38, 0.5, 0.6, 0.9), -12 * hold(t, 0, 0.35, 0.5, 0.9), 0]),
        "head": rot(lambda t: [-10 * sin01(t, 0.1, 0.8), 0, 0]),
        "jaw": rot(lambda t: [30 * sin01(t, 0.15, 0.7), 0, 0]),
    }, 0.025)
    # slam (1.4 s): it rears up on its hind legs and brings both forepaws down at 0.85 s
    L = 1.4

    def up(t):
        return hold(t, 0.0, 0.6, 0.72, 0.86)
    A["slam"] = anim(L, False, {
        "body": {"rotation": lambda t: [-42 * up(t), 0, 0], "position": lambda t: [0, 3 * up(t) - 1.5 * snap(t, 0.8, 0.88, 1.0, 1.4), 2 * up(t)]},
        "right_front": rot(lambda t: [-60 * up(t) + 20 * snap(t, 0.8, 0.88, 1.0, 1.4), 0, 0]),
        "left_front": rot(lambda t: [-60 * up(t) + 20 * snap(t, 0.8, 0.88, 1.0, 1.4), 0, 0]),
        "right_hind": rot(lambda t: [42 * up(t), 0, 0]), "left_hind": rot(lambda t: [42 * up(t), 0, 0]),
        "head": rot(lambda t: [20 * up(t), 0, 0]),
        "jaw": rot(lambda t: [35 * sin01(t, 0.2, 1.1), 0, 0]),
    }, 0.025)
    L = 2.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 82 * ease_in(window(t, 0.4, 1.1))], "position": lambda t: [0, -2 * window(t, 0.4, 1.1), 0]},
        "head": rot(lambda t: [-25 * sin01(t, 0, 0.6) + 20 * window(t, 0.9, 1.5), 0, 0]),
        "jaw": rot(lambda t: [28 * window(t, 0.1, 0.6), 0, 0]),
        "right_front": rot(lambda t: [-20 * window(t, 0.6, 1.2), 0, 0]), "left_front": rot(lambda t: [-30 * window(t, 0.6, 1.2), 0, 0]),
        "right_hind": rot(lambda t: [25 * window(t, 0.6, 1.2), 0, 0]), "left_hind": rot(lambda t: [15 * window(t, 0.6, 1.2), 0, 0]),
    }, 0.05)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- pit ravager

def pit_ravager():
    """A demon beast from the pits: a slab of muscle in black-red hide split by molten cracks, a low head under great
    curling horns, burning eyes and nostrils, spikes down its spine and tail, cloven hooves."""
    name = "pit_ravager"
    B = Body(name, 2800)
    box, col = B.box, B.col
    sk = dict(seed=70, mottle=0.24, cracks=dict(k=0.3, w=0.011, cover=0.55), fx=cracks_fx)
    B.bone("root", None, [0, 0, 0])
    spines = [B.c([-0.8, 23.4 - i * 0.4, -10.0 + i * 3.6], [1.6, 3.6 - i * 0.3, 1.8], "horn", r="horn", tip="+y", pivot=[0, 23.4, -9.2 + i * 3.6], rot=[-30, 0, 0], skip=("down",))
              for i in range(6)]
    B.bone("body", "root", [0, 16, 0], [
        box(0, 17.6, -6.0, 15.0, 13.6, 13.0, "skin", zone="back", split=(0.55, 0.25), **sk),
        box(0, 17.0, 7.0, 13.0, 11.6, 12.0, "skin", zone="back", split=(0.55, 0.25), **sk),
        box(0, 23.8, -7.0, 13.0, 2.0, 9.0, "skin", r="skin_d", **sk),
        *spines,
    ])
    horns = []
    for sx in (-1, 1):
        horns += curl(B, [sx * 3.8, 21.8, -14.6], sx, [(70, 3.4, 2.8), (95, 3.0, 2.4), (130, 2.6, 2.0), (170, 2.2, 1.6), (210, 2.0, 1.2), (240, 1.6, 0.9)],
                      "horn", r="horn", tip="+y", z_step=-0.9)
    B.bone("head", "body", [0, 19, -12], [
        box(0, 18.4, -17.0, 9.6, 8.6, 9.0, "skin", zone="back", split=(0.6, 0.25), **{**sk, "cracks": dict(k=0.45, w=0.012, cover=0.6)},
            eyes=eyes(2.4, 20.0, (1.15, 0.45), slant=0.5, pupil=None, lid=1.4)),
        box(0, 21.4, -21.0, 10.0, 1.8, 1.6, "skin", r="skin_d", seed=71),
        box(0, 16.2, -23.0, 7.4, 5.4, 4.0, "skin", r="skin_d", zone="back", seed=72,
            holes=[dict(x=1.4, y=17.4, r=(0.7, 0.45)), dict(x=-1.4, y=17.4, r=(0.7, 0.45))],
            cuts=[dict(pts=[(-3.7, 14.6), (3.7, 14.6)], w=0.4, faces=("north",))]),
        box(0, 14.0, -24.7, 6.8, 1.0, 0.4, "teeth", n=7, fangs=1, up=True, skip=("up", "down", "south")),
        *horns,
    ], rot=[14, 0, 0])
    B.bone("jaw", "head", [0, 14.6, -16], [
        box(0, 13.0, -21.0, 7.0, 2.4, 7.4, "skin", r="skin_d", seed=73),
        box(0, 14.6, -24.4, 6.4, 0.9, 0.4, "teeth", n=6, fangs=1, up=False, skip=("up", "down", "south")),
        *[B.c([sx * 3.0 - 0.6, 13.4, -24.6], [1.2, 3.6, 1.2], "horn", r="tusk", tip="+y", pivot=[sx * 3.0, 13.8, -24.0], rot=[-12, 0, sx * -14], skip=("down",))
          for sx in (-1, 1)],
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_front", "body", [sx * 5.6, 16, -9], [
            col(sx * 5.8, -9.0, 7.6, 18.0, 5.6, 6.6, "skin", zone="limb", **sk)])
        B.bone(f"{side}_front_low", f"{side}_front", [sx * 5.8, 8.0, -9], [
            col(sx * 5.8, -9.0, 2.4, 8.4, 4.0, 4.4, "skin", zone="limb", **sk),
            col(sx * 5.8, -9.4, 0.0, 2.6, 4.6, 5.0, "horn", r="hoof", tip="-y"),
        ])
        B.bone(f"{side}_hind", "body", [sx * 5.0, 16, 8], [
            box(sx * 5.2, 12.6, 8.4, 5.4, 9.0, 7.4, "skin", zone="limb", **sk)], rot=[-14, 0, 0])
        B.bone(f"{side}_hind_low", f"{side}_hind", [sx * 5.2, 8.6, 9.6], [
            col(sx * 5.2, 9.8, 2.4, 9.0, 3.6, 4.0, "skin", zone="limb", **sk),
            col(sx * 5.2, 9.6, 0.0, 2.6, 4.4, 4.8, "horn", r="hoof", tip="-y"),
        ], rot=[14, 0, 0])
    B.bone("tail", "body", [0, 20, 12.6], [B.bar(0, 20, 12.6, 18.0, 2.4, 2.4, "skin", r="skin_d", seed=74)], rot=[-20, 0, 0])
    B.bone("tail2", "tail", [0, 20, 18.0], [
        B.bar(0, 20, 18.0, 23.0, 1.6, 1.6, "skin", r="skin_d", seed=75),
        B.c([-0.5, 21.0, 19.0], [1.0, 2.2, 1.2], "horn", r="horn", tip="+y", skip=("down",)),
        B.c([-1.6, 19.4, 22.6], [3.2, 1.2, 3.2], "horn", r="horn", tip="+z"),
    ], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 110, 30), {
        "skin": (88, 30, 28), "skin_d": (30, 12, 14), "skin_l": (150, 80, 60),
        "horn": (36, 30, 30), "tusk": (200, 186, 156), "hoof": (24, 20, 20),
    }), density=4.0)
    B.write(3.2, 2.6, 1.0)

    A = {}
    legs = {"right_front": ("right_front_low", 0.0), "left_front": ("left_front_low", 0.5),
            "right_hind": ("right_hind_low", 0.5), "left_hind": ("left_hind_low", 0.0)}
    L = 2.4
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.4 * wave(t, L), 0]},
        "head": rot(lambda t: [3 * wave(t, L), 6 * wave(t, L * 2), 0]),
        "jaw": rot(lambda t: [3 + 3 * wave(t, L / 2), 0, 0]),
        "tail": rot(lambda t: [0, 14 * wave(t, L), 0]), "tail2": rot(lambda t: [0, 18 * wave(t, L, 0.2), 0]),
        "right_front_low": rot(lambda t: [-14 * max(0, wave(t, L, 0.1)) * (1 if (t % (2 * L)) < L else 0), 0, 0]),
    }, 0.05)
    L = 1.0
    A["walk"] = anim(L, True, {
        **quad_walk(L, legs, swing=32, knee=32),
        "body": {"position": lambda t: [0, -0.8 * abs(wave(t, L)), 0], "rotation": lambda t: [2 * wave(t, L / 2), 3 * wave(t, L), 0]},
        "head": rot(lambda t: [-3 * wave(t, L / 2), 0, 0]),
        "tail": rot(lambda t: [-6, 10 * wave(t, L), 0]),
    }, 0.05)
    # gore (1.0 s): the head swept low and tossed up through the target at 0.5 s
    L = 1.0
    A["gore"] = anim(L, False, {
        "head": rot(lambda t: [30 * hold(t, 0, 0.38, 0.4, 0.5) - 50 * snap(t, 0.4, 0.52, 0.6, 1.0), 0, -10 * snap(t, 0.4, 0.52, 0.6, 1.0)]),
        "body": {"rotation": lambda t: [8 * hold(t, 0, 0.38, 0.4, 0.5) - 10 * snap(t, 0.4, 0.52, 0.6, 1.0), 0, 0],
                 "position": lambda t: [0, 0, -3 * snap(t, 0.4, 0.52, 0.6, 1.0)]},
        "jaw": rot(lambda t: [20 * sin01(t, 0.3, 0.8), 0, 0]),
    }, 0.025)
    # charge (0.7 s): the wind-up: head down, a hoof scraping the ground twice
    L = 0.7
    A["charge"] = anim(L, False, {
        "head": rot(lambda t: [28 * hold(t, 0, 0.2, 0.6, 0.7), 0, 0]),
        "body": {"rotation": lambda t: [8 * hold(t, 0, 0.2, 0.6, 0.7), 0, 0], "position": lambda t: [0, -1.4 * hold(t, 0, 0.2, 0.6, 0.7), 1.2 * hold(t, 0, 0.2, 0.6, 0.7)]},
        "right_front": rot(lambda t: [-30 * sin01(t, 0.1, 0.32) + 26 * sin01(t, 0.32, 0.5) - 30 * sin01(t, 0.5, 0.7), 0, 0]),
        "tail": rot(lambda t: [-30 * hold(t, 0, 0.2, 0.6, 0.7), 0, 0]),
    }, 0.025)
    L = 2.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, -84 * ease_in(window(t, 0.5, 1.2))], "position": lambda t: [0, -2 * window(t, 0.5, 1.2), 0]},
        "head": rot(lambda t: [-30 * sin01(t, 0, 0.6), 0, 0]),
        "jaw": rot(lambda t: [30 * window(t, 0.0, 0.5), 0, 0]),
        "right_front": rot(lambda t: [-40 * sin01(t, 0.0, 0.6), 0, 0]), "left_front": rot(lambda t: [-40 * sin01(t, 0.0, 0.6), 0, 0]),
        "tail": rot(lambda t: [-20 * window(t, 0.5, 1.4), 0, 0]),
    }, 0.05)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- coilfang

def coilfang():
    """A great serpent that rears to strike: a body coiled behind it, dark scales banded with gold over a pale belly, a
    spread hood with two glowing eye-marks, gold eyes and two long fangs."""
    name = "coilfang"
    B = Body(name, 2900)
    box = B.box
    sc = dict(size=1.0, seed=36)
    bands = dict(fx=snake_bands)
    B.bone("root", None, [0, 0, 0])
    # the coils on the ground, running back from the neck
    prev, z = "root", 0.0
    for i, (ln, w, yaw) in enumerate(((5.6, 4.6, 30), (5.0, 4.4, -50), (5.0, 4.0, -45), (4.6, 3.4, 40), (4.4, 2.6, 45), (3.6, 1.8, 30))):
        h = w * 0.86
        cubes = [B.bar(0, h / 2, z, z + ln + 0.4, w, h, "scales", zone="back", split=(0.55, 0.32), dir=-1, **sc, **bands)]
        if i == 5:
            cubes.append(B.bar(0, h / 2, z + ln, z + ln + 2.6, 0.9, 0.9, "horn", r="horn", tip="+z"))
        B.bone(f"coil{i}", prev, [0, h / 2, z], cubes, rot=[0, yaw, 0])
        prev, z = f"coil{i}", z + ln
    # the neck, rearing up from the front of the coils
    B.bone("neck", "root", [0, 2.2, 0.6], [
        B.bar(0, 2.2, -4.6, 1.0, 4.2, 3.8, "scales", zone="back", split=(0.55, 0.32), dir=-1, **sc, **bands)], rot=[-58, 0, 0])
    # the hood: two broad thin flaps along the upper neck, behind it, marked with two eyes of light on the back and
    # pale belly scales in front (the neck is upright, so the neck's rest +y faces back and -y faces forward)
    hood = []
    for sx in (-1, 1):
        hood.append(B.c(mirror_x([0.6, 3.5, -9.6], [4.2, 0.6, 5.0], sx), [4.2, 0.6, 5.0], "scales", r="hood", zone="back", **sc,
                        pivot=[sx * 1.4, 3.8, -7.0], rot=[0, 0, sx * -14],
                        eyes=[dict(x=sx * 2.6, y=7.4, r=(0.9, 0.75), face="up", pupil="dot", lid=0.8)]))
    B.bone("neck2", "neck", [0, 2.2, -4.4], [
        B.bar(0, 2.2, -9.4, -4.0, 3.8, 3.4, "scales", zone="back", split=(0.55, 0.32), dir=-1, **sc, **bands),
        B.c([-3.2, 3.4, -10.0], [6.4, 0.6, 2.8], "scales", r="hood", zone="back", **sc, skip=("north", "south")),
        *hood,
    ], rot=[-32, 0, 0])
    B.bone("head", "neck2", [0, 2.2, -9.4], [
        box(0, 2.6, -11.6, 5.0, 2.8, 4.6, "scales", zone="back", split=(0.5, 0.2), **sc,
            eyes=[dict(x=-12.4, y=3.0, r=(0.75, 0.45), face="west", slant=0.3, pupil="slit"), dict(x=-12.4, y=3.0, r=(0.75, 0.45), face="east", slant=0.3, pupil="slit")]),
        box(0, 2.3, -14.8, 3.4, 2.2, 2.0, "scales", zone="back", **sc,
            holes=[dict(x=0.7, y=2.9, r=(0.3, 0.22)), dict(x=-0.7, y=2.9, r=(0.3, 0.22))]),
        *[box(sx * 1.9, 3.95, -12.6, 1.6, 0.7, 3.2, "scales", r="scale_d", **sc) for sx in (-1, 1)],
        *[B.c([sx * 1.1 - 0.3, -0.4, -15.2], [0.6, 2.4, 0.6], "horn", r="fang", tip="-y", pivot=[sx * 1.1, 1.2, -15.0], rot=[16, 0, 0]) for sx in (-1, 1)],
    ], rot=[88, 0, 0])
    B.bone("jaw", "head", [0, 1.2, -10.0], [
        box(0, 0.8, -12.4, 3.6, 1.0, 5.0, "scales", r="scale_l", **sc),
        B.c([-0.2, 0.6, -16.6], [0.4, 0.3, 2.4], "skin", r="tongue", marks=0.0, seed=81),
    ], rot=[4, 0, 0])
    register(name, lambda aff: pal(aff, (255, 206, 60), {
        "scale": (54, 58, 40), "scale_d": (20, 22, 16), "scale_l": (214, 204, 162),
        "hood": (40, 44, 30), "hood_d": (16, 18, 12), "band": (196, 150, 52), "horn": (40, 36, 28), "fang": (232, 226, 206),
    }), density=6.0)
    B.write(2.0, 1.6, 0.6)

    A = {}
    coils = [f"coil{i}" for i in range(6)]
    L = 2.4
    A["idle"] = anim(L, True, {
        "neck": rot(lambda t: [2 * wave(t, L), 6 * wave(t, L * 2), 0]),
        "neck2": rot(lambda t: [3 * wave(t, L, 0.2), 8 * wave(t, L * 2, 0.15), 0]),
        "head": rot(lambda t: [-3 * wave(t, L, 0.3), -8 * wave(t, L * 2, 0.3), 0]),
        "jaw": rot(lambda t: [6 * max(0, wave(t, L / 3)), 0, 0]),
        "coil5": rot(lambda t: [0, 20 * wave(t, 0.6), 0]),
    }, 0.05)
    L = 1.0
    A["walk"] = anim(L, True, {
        **{c: rot(lambda t, i=i: [0, 22 * wave(t, L, i * 0.16), 0]) for i, c in enumerate(coils)},
        "neck": rot(lambda t: [10, -10 * wave(t, L, -0.16), 0]),
        "neck2": rot(lambda t: [6, -6 * wave(t, L, -0.3), 0]),
        "head": rot(lambda t: [-12, 8 * wave(t, L, -0.4), 0]),
    }, 0.05)
    # strike (0.6 s): it draws back and lashes out low at 0.3 s
    L = 0.6
    A["strike"] = anim(L, False, {
        "neck": rot(lambda t: [-18 * hold(t, 0, 0.2, 0.22, 0.3) + 50 * snap(t, 0.22, 0.32, 0.38, 0.6), 0, 0]),
        "neck2": rot(lambda t: [-14 * hold(t, 0, 0.2, 0.22, 0.3) + 26 * snap(t, 0.22, 0.32, 0.38, 0.6), 0, 0]),
        "head": rot(lambda t: [10 * hold(t, 0, 0.2, 0.22, 0.3) - 40 * snap(t, 0.22, 0.32, 0.38, 0.6), 0, 0]),
        "jaw": rot(lambda t: [44 * hold(t, 0.1, 0.24, 0.3, 0.4), 0, 0]),
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "neck": rot(lambda t: [58 * ease_in(window(t, 0.1, 0.6)), 0, 20 * window(t, 0.1, 0.6)]),
        "neck2": rot(lambda t: [30 * window(t, 0.2, 0.7), 0, 0]),
        "head": rot(lambda t: [-60 * window(t, 0.3, 0.8), 0, 70 * window(t, 0.5, 1.0)]),
        "jaw": rot(lambda t: [30 * window(t, 0.2, 0.6), 0, 0]),
        **{c: rot(lambda t, i=i: [0, 10 * math.sin(t * 30 + i) * (1 - window(t, 0.0, 0.8)), 0]) for i, c in enumerate(coils)},
    }, 0.025)
    write_anims(name, A)


def snake_bands(f, p, s):
    """Gold bands across a serpent's back, ragged at their edges, every few scales."""
    if f.face == "down":
        return
    coord = f.Z + 0.8 * np.abs(f.X)
    k = np.floor(coord / 3.2)
    c = (k + 0.5) * 3.2
    on = np.abs(coord - c) < 0.42 + 0.2 * CS.jag(f.X * 2 + f.Y, 9, 0.6)
    if f.wall:
        on &= f.Y > f.bottom + 0.35 * (f.top - f.bottom)
    p.over("band", 0.45, on)


# --------------------------------------------------------------------------------------------- cragcrawler

def cragcrawler():
    """A crab the size of a boulder: a shell of stone with moss on it and crystals growing out of it, eyes of light on
    stalks, six jointed legs, and two claws, one far bigger than the other."""
    name = "cragcrawler"
    B = Body(name, 3000)
    box, col = B.box, B.col
    rk = dict(seed=101, moss=0.35, cell=2.8)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 7, 0], [
        box(0, 8.2, 0.6, 18.0, 5.6, 14.0, "boulder", r="rock", **rk,
            maw=dict(x=0.0, y=7.4, r=1.5, n=7, glow=True)),
        box(0, 11.9, 1.2, 15.0, 2.2, 11.6, "boulder", r="rock", **rk),
        box(0.4, 13.6, 2.0, 10.6, 1.4, 8.0, "boulder", r="rock", **rk),
        box(-5.0, 12.4, 5.2, 4.6, 2.6, 4.4, "boulder", r="rock", **{**rk, "seed": 109}),
        box(5.4, 12.2, -2.0, 3.6, 2.2, 3.6, "boulder", r="rock", **{**rk, "seed": 108}),
        *shard(B, -2.6, 14.0, 4.6, 2.6, 4.0, 14, -18), *shard(B, 1.4, 14.0, 3.4, 1.8, 2.8, 8, 16), *shard(B, 4.2, 13.2, 6.2, 1.6, 2.4, 22, 30),
        box(0, 5.0, 0.6, 15.0, 1.2, 12.0, "chitin", r="belly", axis="z", seed=102),
        # mouthparts under the shell's lip
        *[B.c(mirror_x([0.4, 4.6, -7.4], [1.6, 2.4, 1.2], sx), [1.6, 2.4, 1.2], "horn", r="tip", tip="-y", pivot=[sx * 1.0, 6.4, -6.8], rot=[-12, 0, sx * -10])
          for sx in (-1, 1)],
    ])
    B.bone("eyes", "body", [0, 10, -6.6], [
        *[B.c(mirror_x([2.0, 9.4, -7.4], [1.0, 4.6, 1.0], sx), [1.0, 4.6, 1.0], "chitin", r="belly", axis="y", seed=104, pivot=[sx * 2.5, 9.6, -6.9], rot=[8, 0, sx * 12])
          for sx in (-1, 1)],
        *[B.c(mirror_x([2.4, 13.4, -8.4], [1.8, 1.8, 1.8], sx), [1.8, 1.8, 1.8], "chitin", r="shell", axis="y", seed=104, pivot=[sx * 2.5, 9.6, -6.9], rot=[8, 0, sx * 12],
              eyes=[dict(x=sx * 3.3, y=14.3, r=(0.8, 0.8), face="north", pupil="dot", lid=0.4, glint=True)])
          for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        big = sx < 0
        k = 1.35 if big else 1.0
        B.bone(f"{side}_claw", "body", [sx * 6.2, 7.6, -6.0], [
            B.c(mirror_x([6.0, 6.6, -9.6], [2.4 * k, 2.4 * k, 4.0], sx), [2.4 * k, 2.4 * k, 4.0], "chitin", r="shell", axis="z", seed=105),
        ], rot=[0, sx * 30, 0])
        B.bone(f"{side}_pincer", f"{side}_claw", [sx * 7.0, 7.8, -9.6], [
            B.c(mirror_x([5.6, 5.8, -15.0], [3.4 * k, 3.4 * k, 5.6 * k], sx), [3.4 * k, 3.4 * k, 5.6 * k], "chitin", r="shell", axis="z", seed=106),
            B.c(mirror_x([5.9, 5.8 + 0.6 * k, -15.0 - 3.6 * k], [1.2 * k, 1.6 * k, 3.8 * k], sx), [1.2 * k, 1.6 * k, 3.8 * k], "horn", r="tip", tip="-z"),
        ])
        B.bone(f"{side}_nipper", f"{side}_pincer", [sx * 7.0, 6.0, -14.6], [
            B.c(mirror_x([7.4 if not big else 7.9, 5.4, -15.4 - 3.8 * k], [1.0 * k, 1.2 * k, 4.2 * k], sx), [1.0 * k, 1.2 * k, 4.2 * k], "horn", r="tip", tip="-z"),
        ], rot=[0, sx * -16, 0])
        for j, z in enumerate((-3.4, 0.8, 5.0)):
            B.bone(f"{side}_leg{j}", "body", [sx * 8.6, 7.4, z], [
                B.c(mirror_x([8.4, 6.6, z - 0.9], [5.4, 1.6, 1.8], sx), [5.4, 1.6, 1.8], "chitin", r="shell", axis="x", seed=107 + j),
                B.c(mirror_x([13.0, 0.0, z - 0.7], [1.4, 7.0, 1.4], sx), [1.4, 7.0, 1.4], "chitin", r="shell", axis="y", seed=110 + j),
                B.c(mirror_x([13.2, -0.6, z - 0.4], [1.0, 1.0, 0.8], sx), [1.0, 1.0, 0.8], "horn", r="tip", tip="-y"),
            ], rot=[0, sx * (j - 1) * -18, sx * -18])
    register(name, lambda aff: pal(aff, (255, 150, 60), {
        "rock": (98, 92, 86), "shell": [(10, 8, 8), (36, 30, 30), (78, 66, 62), (130, 112, 102), (190, 172, 156)],
        "belly": (156, 120, 98), "tip": (30, 26, 26),
    }), density=5.0)
    B.write(2.6, 1.4, 0.5)

    A = {}
    legs = [f"{s}_leg{j}" for s in ("right", "left") for j in range(3)]
    L = 2.4
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.3 * wave(t, L), 0]},
        "eyes": rot(lambda t: [6 * wave(t, L), 10 * wave(t, L * 2), 0]),
        "right_nipper": rot(lambda t: [0, -14 * max(0, wave(t, 0.6)) * (1 if (t % 2.4) < 1.2 else 0), 0]),
        "left_nipper": rot(lambda t: [0, 14 * max(0, wave(t, 0.6, 0.5)) * (1 if (t % 2.4) >= 1.2 else 0), 0]),
    }, 0.05)
    L = 0.8
    A["walk"] = anim(L, True, {
        **{leg: rot(lambda t, i=i: [0, 16 * wave(t, L, (i % 2) * 0.5), (12 if i < 3 else -12) * max(0, wave(t, L, (i % 2) * 0.5 + 0.25))]) for i, leg in enumerate(legs)},
        "body": rot(lambda t: [0, 4 * wave(t, L), 2 * wave(t, L / 2)]),
    }, 0.05)
    # pinch (0.7 s): the big claw snaps forward and closes at 0.35 s
    L = 0.7
    A["pinch"] = anim(L, False, {
        "right_claw": rot(lambda t: [-20 * hold(t, 0, 0.25, 0.28, 0.36) + 10 * snap(t, 0.28, 0.36, 0.45, 0.7), -40 * snap(t, 0.25, 0.36, 0.45, 0.7), 0]),
        "right_nipper": rot(lambda t: [0, -30 * hold(t, 0, 0.2, 0.3, 0.34) + 6 * snap(t, 0.3, 0.36, 0.5, 0.7), 0]),
        "body": {"position": lambda t: [0, 0, -2 * snap(t, 0.28, 0.38, 0.45, 0.7)]},
    }, 0.025)
    # block (1.0 s): both claws raised before its face
    L = 1.0
    A["block"] = anim(L, False, {
        "right_claw": rot(lambda t: [-40 * hold(t, 0, 0.15, 0.7, 1.0), -30 * hold(t, 0, 0.15, 0.7, 1.0), 0]),
        "left_claw": rot(lambda t: [-40 * hold(t, 0, 0.15, 0.7, 1.0), 30 * hold(t, 0, 0.15, 0.7, 1.0), 0]),
        "body": {"position": lambda t: [0, -1.5 * hold(t, 0, 0.15, 0.7, 1.0), 0]},
        "eyes": {"position": lambda t: [0, -2.5 * hold(t, 0, 0.1, 0.7, 1.0), 0]},
    }, 0.025)
    L = 1.4
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 180 * ease_in_out(window(t, 0.0, 0.6))], "position": lambda t: [0, 14 * ease_in_out(window(t, 0.0, 0.6)), 0]},
        **{leg: rot(lambda t, i=i: [0, 0, (-50 if i < 3 else 50) * window(t, 0.4, 1.2) + 10 * math.sin(t * 40 + i) * (1 - window(t, 0.6, 1.4))]) for i, leg in enumerate(legs)},
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- hive reaver

def hive_reaver():
    """The hive's warrior: a mantis as tall as a man, standing on four thin legs with its long body raised, a narrow
    head of jagged mandibles and two great glowing eyes, forelegs that end in serrated scythes, folded wings."""
    name = "hive_reaver"
    B = Body(name, 3100)
    box, col = B.box, B.col
    ch = dict(seed=120, band=1.8)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 13, 0], [
        box(0, 13.0, 0.0, 5.0, 4.0, 5.0, "chitin", r="chitin", axis="y", **ch),
        # wings folded over the abdomen
        *[B.c(mirror_x([0.0, 14.6, 1.0], [2.8, 0.4, 11.0], sx), [2.8, 0.4, 11.0], "skin", r="wingskin", marks=0.0, pivot=[0, 14.8, 1.0], rot=[16, 0, sx * -8],
              fx=wing_veins, skip=("north", "south", "east", "west")) for sx in (-1, 1)],
    ])
    B.bone("abdomen", "body", [0, 13, 2.4], [
        B.bar(0, 12.6, 2.4, 13.6, 5.2, 4.8, "chitin", r="chitin", axis="z", **ch),
        B.bar(0, 12.4, 13.6, 16.0, 3.6, 3.2, "chitin", r="chitin", axis="z", **ch),
    ], rot=[-18, 0, 0])
    B.bone("thorax", "body", [0, 14.6, -1.0], [
        col(0, -1.0, 14.6, 26.0, 3.0, 3.2, "chitin", r="chitin", axis="y", **ch),
        col(0, -1.0, 22.0, 25.6, 3.8, 3.8, "chitin", r="plate_c", axis="y", seed=121),
    ], rot=[14, 0, 0])
    B.bone("head", "thorax", [0, 26.0, -1.0], [
        box(0, 27.8, -1.8, 5.4, 3.0, 3.0, "chitin", r="chitin", axis="x", **ch),
        box(0, 25.8, -2.4, 2.8, 1.8, 2.2, "chitin", r="chitin", axis="y", **ch),
        *[B.c(mirror_x([2.0, 27.4, -3.4], [1.8, 2.6, 2.8], sx), [1.8, 2.6, 2.8], "crystal", pivot=[sx * 2.4, 28.4, -2.0], rot=[0, sx * -10, sx * -14]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.2, 24.0, -4.2], [1.4, 1.8, 0.8], sx), [1.4, 1.8, 0.8], "horn", r="mandible", tip="-y", pivot=[sx * 0.8, 25.0, -3.6], rot=[-10, 0, sx * 18]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.6, 29.0, -3.0], [0.3, 0.3, 6.0], sx), [0.3, 0.3, 6.0], "chitin", r="chitin", pivot=[sx * 0.6, 29.2, -2.8], rot=[-44, sx * -14, 0]) for sx in (-1, 1)],
    ], rot=[-14, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # the scythes: a thick spiked upper part held forward and down, the pale serrated blade folded back up along
        # its underside, ready to snap open
        B.bone(f"{side}_arm", "thorax", [sx * 1.8, 23.6, -1.4], [
            B.c(mirror_x([1.0, 22.6, -2.6], [2.0, 2.0, 2.4], sx), [2.0, 2.0, 2.4], "chitin", r="plate_c", axis="x", seed=122),
            B.c(mirror_x([1.3, 15.2, -2.5], [1.8, 8.0, 2.2], sx), [1.8, 8.0, 2.2], "chitin", r="chitin", axis="y", seed=123),
            *[B.c(mirror_x([1.75, 15.8 + i * 1.7, -3.4], [0.8, 0.5, 1.0], sx), [0.8, 0.5, 1.0], "horn", r="mandible", tip="-z") for i in range(4)],
        ], rot=[-62, 0, sx * -6])
        B.bone(f"{side}_scythe", f"{side}_arm", [sx * 2.2, 15.6, -1.4], [
            B.c(mirror_x([1.8, 15.6, -0.6], [0.8, 9.4, 2.4], sx), [0.8, 9.4, 2.4], "metal", r="scythe", edge="front"),
            *[B.c(mirror_x([1.9, 16.6 + i * 1.8, -1.2], [0.6, 0.7, 0.8], sx), [0.6, 0.7, 0.8], "horn", r="mandible", tip="-z") for i in range(4)],
            B.c(mirror_x([1.85, 24.6, -1.0], [0.7, 2.6, 1.4], sx), [0.7, 2.6, 1.4], "horn", r="mandible", tip="+y", pivot=[sx * 2.2, 24.8, -0.4], rot=[-30, 0, 0]),
        ], rot=[-10, 0, 0])
        for j, (z, spread) in enumerate(((-1.2, -26), (1.6, 20))):
            B.bone(f"{side}_leg{j}", "body", [sx * 2.2, 12.6, z], [
                B.c(mirror_x([2.0, 12.2, z - 0.4], [6.0, 0.8, 0.8], sx), [6.0, 0.8, 0.8], "chitin", r="chitin", axis="x", seed=124 + j),
            ], rot=[0, sx * spread, sx * 34])
            B.bone(f"{side}_shin{j}", f"{side}_leg{j}", [sx * 7.8, 12.6, z], [
                B.c(mirror_x([7.4, 0.0, z - 0.35], [0.7, 12.8, 0.7], sx), [0.7, 12.8, 0.7], "chitin", r="chitin", axis="y", seed=126 + j),
            ], rot=[0, 0, sx * -34])
    register(name, lambda aff: pal(aff, (255, 60, 110), {
        "chitin": [(3, 6, 4), (12, 22, 14), (34, 56, 34), (86, 118, 66), (176, 206, 130)],
        "plate_c": [(10, 10, 6), (40, 40, 18), (96, 92, 40), (156, 150, 80), (214, 210, 150)],
        "scythe": [(22, 18, 14), (74, 68, 54), (156, 148, 118), (214, 208, 182), (252, 250, 238)],
        "wingskin": (70, 82, 64), "mandible": (26, 22, 18),
    }, tinted=("chitin", "plate_c", "wingskin")), density=5.0)
    B.write(2.4, 2.6, 1.0)

    A = {}
    legs = [(f"{s}_leg{j}", f"{s}_shin{j}", sx, j) for s, sx in (("right", -1), ("left", 1)) for j in range(2)]
    L = 2.6
    A["idle"] = anim(L, True, {
        "thorax": rot(lambda t: [3 * wave(t, L), 0, 3 * wave(t, L * 2)]),
        "head": rot(lambda t: [3 * wave(t, L, 0.2), 0, 14 * wave(t, L * 2, 0.4)]),
        "right_scythe": rot(lambda t: [6 * max(0, wave(t, L / 2)), 0, 0]), "left_scythe": rot(lambda t: [6 * max(0, wave(t, L / 2, 0.5)), 0, 0]),
        "abdomen": rot(lambda t: [2 * wave(t, L, 0.5), 0, 0]),
    }, 0.05)
    L = 0.9
    A["walk"] = anim(L, True, {
        **{leg: rot(lambda t, sx=sx, j=j: [0, sx * 20 * wave(t, L, ((j + (sx > 0)) % 2) * 0.5), 0]) for leg, shin, sx, j in legs},
        **{shin: rot(lambda t, sx=sx, j=j: [0, 0, sx * 10 * max(0, wave(t, L, ((j + (sx > 0)) % 2) * 0.5 + 0.25))]) for leg, shin, sx, j in legs},
        "body": {"position": lambda t: [0, -0.5 * abs(wave(t, L)), 0]},
        "thorax": rot(lambda t: [6, 3 * wave(t, L), 0]),
    }, 0.05)
    # slash (0.6 s): both scythes thrown open and slammed down at 0.3 s
    L = 0.6
    A["slash"] = anim(L, False, {
        "right_arm": rot(lambda t: [-56 * hold(t, 0, 0.2, 0.22, 0.3) + 24 * snap(t, 0.22, 0.32, 0.4, 0.6), 0, 0]),
        "left_arm": rot(lambda t: [-56 * hold(t, 0.03, 0.22, 0.25, 0.33) + 24 * snap(t, 0.25, 0.35, 0.42, 0.6), 0, 0]),
        "right_scythe": rot(lambda t: [-120 * hold(t, 0, 0.2, 0.22, 0.3) + 8 * snap(t, 0.22, 0.3, 0.4, 0.6), 0, 0]),
        "left_scythe": rot(lambda t: [-120 * hold(t, 0.03, 0.22, 0.25, 0.33) + 8 * snap(t, 0.25, 0.33, 0.42, 0.6), 0, 0]),
        "thorax": rot(lambda t: [-12 * hold(t, 0, 0.2, 0.22, 0.3) + 18 * snap(t, 0.22, 0.34, 0.4, 0.6), 0, 0]),
    }, 0.025)
    # lunge (0.8 s): low, then a leap with both scythes reaching
    L = 0.8
    A["lunge"] = anim(L, False, {
        "body": {"position": lambda t: [0, -3 * hold(t, 0, 0.25, 0.3, 0.4) + 2 * sin01(t, 0.35, 0.7), -2 * sin01(t, 0.3, 0.7)]},
        "thorax": rot(lambda t: [30 * sin01(t, 0.2, 0.75), 0, 0]),
        "right_arm": rot(lambda t: [-70 * sin01(t, 0.25, 0.75), 0, 0]), "left_arm": rot(lambda t: [-70 * sin01(t, 0.25, 0.75), 0, 0]),
        "right_scythe": rot(lambda t: [-120 * sin01(t, 0.3, 0.75), 0, 0]), "left_scythe": rot(lambda t: [-120 * sin01(t, 0.3, 0.75), 0, 0]),
        **{leg: rot(lambda t, sx=sx: [0, 0, sx * -16 * hold(t, 0, 0.25, 0.3, 0.45)]) for leg, shin, sx, j in legs},
    }, 0.025)
    L = 1.4
    A["death"] = anim(L, "hold", {
        "body": {"position": lambda t: [0, -8 * ease_in(window(t, 0.2, 0.8)), 0], "rotation": lambda t: [0, 0, 30 * window(t, 0.5, 1.0)]},
        "thorax": rot(lambda t: [50 * window(t, 0.3, 0.9), 0, 0]),
        "head": rot(lambda t: [40 * window(t, 0.6, 1.1), 0, 20 * window(t, 0.6, 1.1)]),
        **{leg: rot(lambda t, sx=sx: [0, 0, sx * 40 * window(t, 0.2, 0.8)]) for leg, shin, sx, j in legs},
        "right_scythe": rot(lambda t: [-40 * window(t, 0.3, 0.9), 0, 0]), "left_scythe": rot(lambda t: [-40 * window(t, 0.3, 0.9), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- rune colossus

def rune_colossus():
    """A colossus of rune-cut stone three blocks tall: a hunched mountain of a body with light seeping through its
    cracks and carved runes, a crystal heart in its chest, a small head sunk between boulder shoulders with one eye,
    and arms so long its fists rest on the ground."""
    name = "rune_colossus"
    B = Body(name, 3200)
    box, col = B.box, B.col
    rk = dict(seed=130, cell=3.6, moss=0.3, cracks=dict(k=0.26, w=0.011, cover=0.56), fx=cracks_fx)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17, 0], [box(0, 17.0, 0.6, 14.0, 5.0, 9.0, "boulder", r="rock", **rk)])
    B.bone("chest", "hips", [0, 19, 0], [
        box(0, 29.0, -0.6, 22.0, 18.0, 13.0, "boulder", r="rock", **rk,
            runes=dict(region=(-7.0, 7.0, 22.0, 34.0), faces=("north",), cell=2.6, density=0.3, seed=31)),
        box(0, 28.0, -7.2, 5.0, 5.0, 0.6, "crystal"),
        box(0, 28.0, -7.0, 7.0, 7.0, 0.4, "boulder", r="rock_d", seed=131, cell=1.8, skip=("south",)),
        *[box(sx * 11.6, 36.0, 0.0, 8.0, 7.0, 10.0, "boulder", r="rock", **{**rk, "seed": 132 + sx}) for sx in (-1, 1)],
        *shard(B, -8.0, 38.6, 2.0, 2.6, 4.4, -18, -20), *shard(B, 9.0, 38.8, 3.6, 2.2, 3.4, -24, 22), *shard(B, 2.0, 37.8, 5.0, 2.0, 3.0, -40, 10),
    ], rot=[12, 0, 0])
    B.bone("head", "chest", [0, 37.0, -3.4], [
        box(0, 39.4, -5.2, 9.0, 6.4, 7.4, "boulder", r="rock", **{**rk, "cracks": dict(k=0.5, w=0.014, cover=0.6)},
            sockets=[dict(x=0.0, y=38.8, r=(2.0, 1.0), core=0.46)],
            cuts=[dict(pts=[(-3.0, 37.0), (-1.2, 36.6), (1.2, 36.6), (3.0, 37.0)], w=0.5, faces=("north",))]),
        box(0, 40.7, -9.0, 9.6, 1.6, 1.8, "boulder", r="rock_d", seed=133),
        box(0, 43.0, -4.6, 6.0, 1.4, 5.0, "boulder", r="rock", **{**rk, "seed": 134}),
    ], rot=[-12, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 13.4, 36.0, 0.0], [
            col(sx * 14.4, 0.0, 22.0, 36.0, 7.0, 8.0, "boulder", r="rock", **rk)], rot=[-12, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 14.4, 23.0, 0.0], [
            col(sx * 14.8, -0.4, 9.0, 23.4, 8.4, 9.0, "boulder", r="rock", **rk,
                lines=[dict(pts=[(-3.0, 10.0), (-1.0, 14.0), (-2.0, 19.0), (0.5, 22.0)], w=0.4, faces=("west", "east"))]),
            box(sx * 14.8, 5.2, -0.6, 10.0, 7.4, 10.4, "boulder", r="rock", **rk),
        ], rot=[12, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 5.6, 16.0, 0.6], [
            col(sx * 5.8, 0.6, 8.6, 16.4, 7.0, 7.4, "boulder", r="rock", **rk)])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 5.8, 9.0, 0.6], [
            col(sx * 5.9, 0.4, 0.0, 9.2, 8.0, 9.0, "boulder", r="rock", **rk)])
    register(name, lambda aff: pal(aff, (255, 196, 80), {
        "rock": (104, 100, 96), "rock_d": (40, 38, 38),
    }), density=3.5)
    B.write(4.0, 3.6, 1.6)

    A = {}
    L = 3.6
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [1.5 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [2 * wave(t, L, 0.2), 10 * wave(t, L * 2), 0]),
        "right_arm": rot(lambda t: [2 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-2 * wave(t, L), 0, 0]),
    }, 0.1)
    L = 1.8
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=18, arm_swing=14, knee=18, bob=1.2, body="hips"),
        "chest": rot(lambda t: [4, 5 * wave(t, L), 4 * wave(t, L)]),
    }, 0.05)
    # smash (1.2 s): the right fist raised high, down at 0.7 s
    L = 1.2
    A["smash"] = anim(L, False, {
        "right_arm": rot(lambda t: [-150 * hold(t, 0, 0.55, 0.58, 0.7) + 10 * snap(t, 0.58, 0.7, 0.85, 1.2), 0, 10 * hold(t, 0, 0.55, 0.7, 1.2)]),
        "right_hand": rot(lambda t: [-30 * hold(t, 0, 0.55, 0.58, 0.7), 0, 0]),
        "chest": rot(lambda t: [-10 * hold(t, 0, 0.55, 0.58, 0.7) + 16 * snap(t, 0.58, 0.72, 0.85, 1.2), -12 * hold(t, 0, 0.55, 0.7, 1.2), 0]),
    }, 0.025)
    # slam (1.6 s): both fists high over the head, brought down together at 1.0 s
    L = 1.6
    A["slam"] = anim(L, False, {
        "right_arm": rot(lambda t: [-160 * hold(t, 0, 0.75, 0.85, 1.0) - 10 * snap(t, 0.85, 1.0, 1.2, 1.6), 0, 0]),
        "left_arm": rot(lambda t: [-160 * hold(t, 0, 0.75, 0.85, 1.0) - 10 * snap(t, 0.85, 1.0, 1.2, 1.6), 0, 0]),
        "chest": rot(lambda t: [-14 * hold(t, 0, 0.75, 0.85, 1.0) + 26 * snap(t, 0.85, 1.02, 1.2, 1.6), 0, 0]),
        "head": rot(lambda t: [-16 * hold(t, 0, 0.75, 0.85, 1.0), 0, 0]),
        "hips": {"position": lambda t: [0, -2.5 * snap(t, 0.85, 1.02, 1.2, 1.6), 0]},
    }, 0.025)
    L = 2.2
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -10 * ease_in(window(t, 0.6, 1.6)), 0], "rotation": lambda t: [14 * window(t, 0.4, 1.4), 0, 0]},
        "head": {"position": lambda t: [0, -4 * ease_in(window(t, 0.2, 0.9)), -2 * window(t, 0.2, 0.9)], "rotation": lambda t: [30 * window(t, 0.2, 0.9), 0, 20 * window(t, 0.3, 1.0)]},
        "right_arm": {"position": lambda t: [-2 * window(t, 0.4, 1.2), -8 * ease_in(window(t, 0.4, 1.2)), 0], "rotation": lambda t: [0, 0, -30 * window(t, 0.4, 1.2)]},
        "left_arm": {"position": lambda t: [2 * window(t, 0.5, 1.3), -8 * ease_in(window(t, 0.5, 1.3)), 0], "rotation": lambda t: [0, 0, 30 * window(t, 0.5, 1.3)]},
        "chest": rot(lambda t: [20 * window(t, 0.3, 1.2), 0, 0]),
    }, 0.05)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- thorn spitter

def thorn_spitter():
    """A rooted plant that kills: a thick thorny stem, broad blade-leaves at its foot, and a bulb that opens like a
    mouth, petals ringed with thorn-teeth around a glowing throat it spits poisoned thorns from."""
    name = "thorn_spitter"
    B = Body(name, 3300)
    box, col = B.box, B.col
    st = dict(seed=140)
    thorns = lambda y0, n, r, sd: [B.c([math.cos(a) * r - 0.3, y0 + (i % 3) * 1.4, math.sin(a) * r - 0.3], [0.6, 0.6, 0.6], "horn", r="thorn", tip="+y")  # noqa: E731
                                   for i, a in enumerate(np.linspace(0, 2 * math.pi, n, endpoint=False) + sd)]
    B.bone("root", None, [0, 0, 0])
    roots = [B.c([0, 0, -0.6], [5.6, 1.4, 1.2], "wood", r="root_w", long="x", pivot=[0, 0.7, 0], rot=[0, a, -8]) for a in (20, 95, 170, 245, 315)]
    leaves = [B.c([0.6, 0.6, -1.2], [9.0, 0.4, 2.6], "skin", r="leaf", zone="dark", seed=141, pivot=[0, 1.0, 0], rot=[0, a, 24], fx=leaf_veins, skip=("north", "south", "east", "west"))
              for a in (40, 130, 220, 310)]
    B.bone("base", "root", [0, 0, 0], [*roots, *leaves, col(0, 0, 0.0, 3.0, 4.0, 4.0, "wood", r="root_w", seed=142)])
    B.bone("stem", "base", [0, 2.6, 0], [col(0, 0, 2.6, 8.6, 2.8, 2.8, "skin", r="stem", zone="limb", **st), *thorns(4.0, 5, 1.6, 0.3)], rot=[-6, 0, 0])
    B.bone("stem2", "stem", [0, 8.4, 0], [col(0, 0, 8.4, 13.0, 2.4, 2.4, "skin", r="stem", zone="limb", **st), *thorns(9.4, 4, 1.4, 1.1)], rot=[14, 0, 0])
    # the bulb: a round head whose front is a mouth ringed with thorn-teeth, five petals opening around it like lips
    petals = []
    for k in range(5):
        th = 90 + k * 72
        petals.append(B.c([-2.1, 17.3, -7.8], [4.2, 0.6, 5.2], "skin", r="petal", zone="back", seed=143 + k,
                          pivot=[0, 15.0, -3.2], rot=[-40, 0, 90 - th], fx=petal_fx, skip=("north", "south", "east", "west")))
    B.bone("head", "stem2", [0, 12.8, 0], [
        box(0, 15.0, -0.4, 6.8, 6.6, 5.6, "skin", r="petal", zone="back", split=(0.5, 0.2), seed=144,
            maw=dict(x=0.0, y=15.0, r=2.9, n=9, glow=True)),
        box(0, 15.0, 2.8, 5.2, 5.2, 1.6, "skin", r="petal_d", seed=145),
        box(0, 15.0, 4.0, 3.0, 3.0, 1.2, "skin", r="stem", zone="limb", seed=146),
        *petals,
        *thorns(17.8, 5, 2.6, 0.4),
    ], rot=[-8, 0, 0])
    register(name, lambda aff: pal(aff, (200, 255, 70), {
        "stem": (66, 92, 46), "stem_d": (24, 40, 20), "stem_l": (118, 140, 76),
        "leaf": (52, 86, 40), "petal": (104, 40, 58), "petal_d": (40, 12, 26), "petal_l": (160, 70, 86),
        "root_w": (60, 44, 32), "thorn": (196, 186, 150),
    }), density=6.0)
    B.write(2.0, 1.6, 0.6)

    A = {}
    L = 2.6
    A["idle"] = anim(L, True, {
        "stem": rot(lambda t: [-6 + 3 * wave(t, L), 0, 3 * wave(t, L * 2)]),
        "stem2": rot(lambda t: [14 + 3 * wave(t, L, 0.2), 0, 4 * wave(t, L * 2, 0.2)]),
        "head": {"rotation": lambda t: [-8 + 4 * wave(t, L, 0.4), 10 * wave(t, L * 2, 0.3), 0], "scale": lambda t: [1 + 0.03 * wave(t, L / 2)] * 3},
    }, 0.1)
    A["walk"] = A["idle"]
    # spit (0.8 s): the bulb draws back, then snaps forward and spits at 0.45 s
    L = 0.8
    A["spit"] = anim(L, False, {
        "stem": rot(lambda t: [-6 - 16 * hold(t, 0, 0.35, 0.38, 0.46) + 22 * snap(t, 0.38, 0.47, 0.55, 0.8), 0, 0]),
        "stem2": rot(lambda t: [14 - 10 * hold(t, 0, 0.35, 0.38, 0.46) + 16 * snap(t, 0.38, 0.47, 0.55, 0.8), 0, 0]),
        "head": {"rotation": lambda t: [-8 - 14 * hold(t, 0, 0.35, 0.38, 0.46) + 16 * snap(t, 0.38, 0.47, 0.55, 0.8), 0, 0],
                 "scale": lambda t: [1 + 0.15 * snap(t, 0.4, 0.47, 0.55, 0.8), 1 + 0.15 * snap(t, 0.4, 0.47, 0.55, 0.8), 1 - 0.1 * snap(t, 0.4, 0.47, 0.55, 0.8)]},
    }, 0.025)
    L = 1.6
    A["death"] = anim(L, "hold", {
        "stem": rot(lambda t: [-6 + 40 * ease_in(window(t, 0.1, 1.0)), 0, 20 * window(t, 0.2, 1.0)]),
        "stem2": rot(lambda t: [14 + 40 * window(t, 0.2, 1.1), 0, 0]),
        "head": {"rotation": lambda t: [-8 + 30 * window(t, 0.3, 1.2), 0, 0], "scale": lambda t: [max(0.6, 1 - 0.4 * window(t, 0.3, 1.4))] * 3},
    }, 0.05)
    write_anims(name, A)


def petal_fx(f, p, s):
    """A petal-lip: pale towards the mouth with dark veins running out, its outer end cut into a point."""
    if f.face not in ("up", "down"):
        return
    k = 1 - f.V if f.face == "down" else f.V          # 0 at the root by the mouth .. 1 at the tip
    k = np.clip(k, 0, 1)
    mid = np.abs(f.U - 0.5)
    p.over(s.get("r", "petal") + "_l", 0.34 + 0.3 * (1 - k), (f.face == "down") & (k < 0.55))
    p.over(s.get("r", "petal") + "_d", 0.2, (np.abs(((f.U * 3.0) % 1.0) - 0.5) < 0.08) & (k > 0.2))
    p.cut(mid < 0.5 * np.clip(1.25 - k, 0, 1) ** 0.6)


def leaf_veins(f, p, s):
    """A blade leaf: a pale midrib, side veins, a serrated edge cut out, and a pointed tip."""
    if f.face not in ("up", "down"):
        return
    u, v = f.U, f.V
    mid = np.abs(v - 0.5)
    p.over(s.get("r", "leaf") + "_l", 0.4, mid < 0.06)
    p.over(s.get("r", "leaf") + "_l", 0.3, (np.abs(((u * 6 + mid * 3) % 1.0) - 0.5) < 0.06) & (mid < 0.4))
    width = 0.5 * np.clip(1.15 - u, 0, 1) ** 0.7 * (1 - 0.15 * (np.floor(u * 14) % 2))
    p.cut(mid < width)


# ============================================================================================= the first five, redone

# --------------------------------------------------------------------------------------------- broodling

def brood_sigil(f, p, s):
    """A broodling's abdomen: pale chevrons down its back and an hourglass of light on top."""
    if f.face == "up":
        zc, h = s.get("sigil_z", 6.0), s.get("sigil_h", 2.6)
        dz = np.abs(f.Z - zc)
        hour = (dz < h) & (np.abs(f.X) < 0.35 + 0.75 * dz / h)
        p.over("black", 0.06, (dz < h + 0.4) & (np.abs(f.X) < 0.75 + 0.75 * dz / h) & ~hour)
        p.glow("glow", 0.5 + 0.4 * (1 - dz / h), hour)
    elif f.wall and f.face != "north":
        a = CS.across(f)
        chev = np.abs(((f.Y + 0.6 * np.abs(a if not f.side else f.X)) / 1.6) % 1.0 - 0.5) < 0.09
        p.over("fur_l", 0.42, chev & (f.Y > f.bottom + 0.45 * (f.top - f.bottom)))


def broodling():
    """A spider the size of a dog, a broodmother's young: a hairy dark body, a swollen abdomen with an hourglass of
    light on it, eight eyes of light in a cluster, hooked fangs, and eight jointed legs whose knees rise above its
    back."""
    name = "broodling"
    B = Body(name, 3400)
    box = B.box
    hair = dict(seed=150, len=1.2, width=0.9)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 5.6, 0], [
        box(0, 5.8, -1.0, 6.4, 3.6, 6.2, "fur", r="fur", zone="back", split=(0.5, 0.25), **hair),
        box(0, 7.9, -1.0, 4.8, 0.8, 4.6, "chitin", r="carapace", axis="z", band=1.4, seed=151),
    ])
    eye_spots = [(0.8, 6.0, (0.62, 0.55)), (-0.8, 6.0, (0.62, 0.55)), (1.95, 6.5, (0.42, 0.38)), (-1.95, 6.5, (0.42, 0.38)),
                 (0.6, 7.05, (0.3, 0.28)), (-0.6, 7.05, (0.3, 0.28)), (2.1, 5.3, (0.3, 0.3)), (-2.1, 5.3, (0.3, 0.3))]
    B.bone("head", "body", [0, 5.8, -4.0], [
        box(0, 5.8, -5.4, 5.0, 3.0, 2.8, "fur", r="fur", zone="back", split=(0.7, 0.2), **hair,
            eyes=[dict(x=x, y=y, r=r, pupil=None, lid=0.6, glint=True) for x, y, r in eye_spots]),
        *[B.c(mirror_x([1.6, 3.2, -6.6], [0.6, 2.0, 0.6], sx), [0.6, 2.0, 0.6], "fur", r="fur_d", **hair, pivot=[sx * 1.9, 5.0, -6.3], rot=[-30, 0, sx * -16])
          for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_fang", "head", [sx * 0.9, 4.8, -6.4], [
            B.c(mirror_x([0.35, 3.6, -7.4], [1.3, 1.6, 1.3], sx), [1.3, 1.6, 1.3], "fur", r="fur_d", **hair),
            B.c(mirror_x([0.55, 1.6, -7.2], [0.7, 2.2, 0.7], sx), [0.7, 2.2, 0.7], "horn", r="fang", tip="-y", pivot=[sx * 0.9, 3.6, -6.8], rot=[10, 0, sx * 14]),
        ])
    B.bone("abdomen", "body", [0, 6.6, 2.0], [
        box(0, 7.6, 6.6, 8.8, 7.2, 9.0, "fur", r="fur", zone="back", split=(0.4, 0.2), **hair, fx=brood_sigil, sigil_z=6.4, sigil_h=2.8),
        box(0, 8.0, 11.6, 5.6, 4.8, 1.0, "fur", r="fur_d", **hair),
        box(0, 6.4, 12.2, 1.6, 1.4, 0.8, "horn", r="fang", tip="+z"),
    ], rot=[-16, 0, 0])
    # eight legs: a femur rising to a knee above the back, a long shin down to a hooked foot
    for i, (z, yaw) in enumerate(((-3.0, 42), (-1.6, 14), (-0.2, -14), (1.2, -42))):
        for side, sx in (("r", -1), ("l", 1)):
            hip = [sx * 3.0, 6.0, z]
            knee = [sx * (3.0 + 5.6 * math.cos(math.radians(42))), 6.0 + 5.6 * math.sin(math.radians(42)), z]
            B.bone(f"leg{i}{side}", "body", hip, [
                B.c([hip[0] - (0 if sx > 0 else 5.6), hip[1] - 0.6, z - 0.6], [5.6, 1.2, 1.2], "fur", r="fur", zone="dark", **hair, pivot=hip, rot=[0, 0, sx * -42]),
                B.c([knee[0] - 0.55, knee[1] - 9.8, z - 0.55], [1.1, 9.8, 1.1], "fur", r="fur", zone="dark", **hair, pivot=knee, rot=[0, 0, sx * -24]),
                B.c([knee[0] - 0.75, knee[1] - 1.2, z - 0.75], [1.5, 1.6, 1.5], "chitin", r="carapace", axis="y", seed=152, pivot=knee, rot=[0, 0, sx * -24]),
                B.c([knee[0] - 0.35, knee[1] - 11.0, z - 0.35], [0.7, 1.3, 0.7], "horn", r="fang", tip="-y", pivot=knee, rot=[0, 0, sx * -24]),
            ], rot=[0, sx * yaw, 0])
    register(name, lambda aff: pal(aff, (190, 255, 90), {
        "fur": (48, 40, 52), "fur_d": (18, 14, 22), "fur_l": (150, 128, 112),
        "carapace": [(4, 3, 6), (18, 14, 22), (46, 38, 54), (96, 84, 104), (170, 156, 176)],
        "fang": (40, 30, 30),
    }), density=6.0)
    B.write(1.6, 0.8, 0.4)

    A = {}
    legs = [(f"leg{i}{s}", i, sx) for i in range(4) for s, sx in (("r", -1), ("l", 1))]

    def group(i, sx):
        return ((i + (sx > 0)) % 2) * 0.5
    L = 2.2
    A["idle"] = anim(L, True, {
        "abdomen": {"rotation": lambda t: [3 * wave(t, L), 0, 0], "scale": lambda t: [1 + 0.03 * wave(t, L / 2)] * 3},
        "head": rot(lambda t: [0, 8 * wave(t, L * 2), 0]),
        "right_fang": rot(lambda t: [0, 0, 10 * max(0, wave(t, 0.4)) * (1 if (t % L) < 0.8 else 0)]),
        "left_fang": rot(lambda t: [0, 0, -10 * max(0, wave(t, 0.4)) * (1 if (t % L) < 0.8 else 0)]),
        **{leg: rot(lambda t, i=i, sx=sx: [0, 0, sx * 3 * max(0, wave(t, L, i * 0.2))]) for leg, i, sx in legs},
    }, 0.05)
    L = 0.5
    A["walk"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.3 * abs(wave(t, L / 2)), 0]},
        "abdomen": rot(lambda t: [2 * wave(t, L / 2), 4 * wave(t, L), 0]),
        **{leg: rot(lambda t, i=i, sx=sx: [0, sx * 20 * wave(t, L, group(i, sx)), sx * -14 * max(0, wave(t, L, group(i, sx) + 0.25))]) for leg, i, sx in legs},
    }, 0.025)
    # bite (0.6 s): it rears, fangs wide, and strikes down at 0.32 s
    L = 0.6
    up = lambda t: hold(t, 0.0, 0.22, 0.24, 0.32)  # noqa: E731
    strike = lambda t: snap(t, 0.24, 0.34, 0.4, 0.6)  # noqa: E731
    A["bite"] = anim(L, False, {
        "body": {"rotation": lambda t: [-22 * up(t) + 10 * strike(t), 0, 0], "position": lambda t: [0, 1.2 * up(t), -3 * strike(t)]},
        "right_fang": rot(lambda t: [-20 * up(t) + 10 * strike(t), 0, -30 * up(t)]),
        "left_fang": rot(lambda t: [-20 * up(t) + 10 * strike(t), 0, 30 * up(t)]),
        "leg0r": rot(lambda t: [0, -10 * up(t), 30 * up(t)]), "leg0l": rot(lambda t: [0, 10 * up(t), -30 * up(t)]),
    }, 0.025)
    L = 1.0
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [0, 0, 180 * ease_in_out(window(t, 0, 0.4))], "position": lambda t: [0, 7 * ease_in_out(window(t, 0, 0.4)), 0]},
        **{leg: rot(lambda t, i=i, sx=sx: [0, 0, sx * 46 * ease_in_out(window(t, 0.3, 0.9)) + 6 * math.sin(t * 40 + i) * (1 - window(t, 0.4, 1.0))]) for leg, i, sx in legs},
        "right_fang": rot(lambda t: [0, 0, -20 * window(t, 0.2, 0.6)]), "left_fang": rot(lambda t: [0, 0, 20 * window(t, 0.2, 0.6)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- shardback

def shardback():
    """A squat beetle as wide as a cart: a domed shell split down its back with crystal growing out of the seam, a
    broad low head under a great curved horn, mandibles like shears, small hot eyes, six thick spurred legs."""
    name = "shardback"
    B = Body(name, 3500)
    box = B.box
    sh = dict(axis="z", band=2.6, seed=160)
    B.bone("root", None, [0, 0, 0])
    crystals = (shard(B, 0.0, 16.4, 1.0, 3.4, 6.4, -12, 0) + shard(B, -3.2, 15.8, 4.4, 2.6, 4.6, 8, -24) + shard(B, 3.0, 16.0, -2.8, 2.6, 5.0, -24, 22)
                + shard(B, -2.4, 16.2, -3.6, 2.0, 3.6, -30, -16) + shard(B, 2.6, 15.6, 5.8, 2.0, 3.4, 20, 26) + shard(B, 0.4, 15.2, 8.0, 1.6, 2.6, 36, 6))
    B.bone("body", "root", [0, 9, 0], [
        box(0, 10.0, 0.6, 16.0, 7.0, 18.0, "chitin", r="shell", zone="back", split=(0.45, 0.2), **sh,
            cuts=[dict(pts=[(-8.4, 13.0), (9.6, 13.0)], w=0.5, faces=("west", "east"), ramp="black", tone=0.08)]),
        box(0, 14.6, 1.2, 14.0, 2.4, 15.6, "chitin", r="shell", zone="dark", **sh, fx=elytra_seam),
        box(0, 16.0, 1.6, 9.0, 0.8, 11.0, "chitin", r="shell", zone="dark", **sh, fx=elytra_seam),
        box(0, 6.6, 0.6, 13.0, 1.0, 15.0, "chitin", r="belly", axis="z", band=1.8, seed=161),
        *crystals,
    ])
    B.bone("head", "body", [0, 10, -8.4], [
        box(0, 9.6, -10.8, 10.4, 6.0, 5.4, "chitin", r="shell", zone="back", split=(0.6, 0.25), axis="x", band=3.0, seed=162,
            eyes=eyes(3.6, 10.6, (0.75, 0.45), slant=0.45, pupil=None, lid=1.2)),
        box(0, 12.9, -10.0, 11.4, 1.4, 4.4, "chitin", r="shell_d", axis="x", band=3.0, seed=163),
        # the horn: a thick root curving up and forward into a point
        *horn_curve(B, [0, 11.4, -12.4], [(62, 3.4, 3.2), (48, 3.2, 2.6), (30, 2.8, 2.0), (12, 2.4, 1.4), (-4, 1.8, 0.9)], "horn", r="horn", tip="+y"),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_mandible", "head", [sx * 3.0, 8.0, -13.0], [
            B.c(mirror_x([1.6, 7.0, -17.0], [2.2, 1.6, 4.4], sx), [2.2, 1.6, 4.4], "horn", r="mandible", tip="-z"),
            B.c(mirror_x([0.4, 7.2, -18.2], [1.8, 1.2, 1.4], sx), [1.8, 1.2, 1.4], "horn", r="mandible", tip="-x" if sx > 0 else "+x"),
        ], rot=[0, sx * 14, 0])
        for leg, z, yaw in (("front", -5.6, 30), ("mid", 0.4, 0), ("hind", 6.4, -30)):
            hip = [sx * 7.6, 8.4, z]
            B.bone(f"{side}_{leg}", "body", hip, [
                B.c([hip[0] - (0 if sx > 0 else 5.4), 7.4, z - 1.3], [5.4, 2.4, 2.6], "chitin", r="shell", zone="dark", axis="x", band=2.7, seed=164, pivot=hip, rot=[0, 0, sx * -24]),
            ], rot=[0, sx * yaw, 0])
            knee = [sx * (7.6 + 5.0), 10.4, z]
            B.bone(f"{side}_{leg}_low", f"{side}_{leg}", knee, [
                B.c([knee[0] - 1.1, 0.0, z - 1.1], [2.2, 10.6, 2.2], "chitin", r="shell", axis="y", band=2.6, seed=165, pivot=knee, rot=[0, 0, sx * -14]),
                B.c(mirror_x([abs(knee[0]) + 0.6, 4.4, z - 0.4], [1.8, 0.8, 0.8], sx), [1.8, 0.8, 0.8], "horn", r="mandible", tip="+x" if sx > 0 else "-x", pivot=knee, rot=[0, 0, sx * -14]),
                B.c([knee[0] - 1.4, -0.4, z - 1.6], [2.8, 1.2, 3.2], "horn", r="mandible", tip="-z", pivot=knee, rot=[0, 0, sx * -14]),
            ])
    register(name, lambda aff: pal(aff, (150, 220, 255), {
        "shell": [(4, 4, 8), (18, 18, 30), (48, 50, 72), (104, 110, 140), (182, 190, 218)],
        "shell_d": [(2, 2, 4), (8, 8, 14), (22, 22, 34), (56, 58, 80), (110, 114, 140)],
        "belly": (92, 74, 70), "horn": (40, 34, 38), "mandible": (30, 26, 28),
    }, tinted=("shell", "shell_d", "belly")), density=4.5)
    B.write(3.0, 2.0, 0.6)

    A = {}
    legs = [(f"{s}_{lg}", f"{s}_{lg}_low", j, sx) for s, sx in (("right", -1), ("left", 1)) for j, lg in enumerate(("front", "mid", "hind"))]

    def tri(j, sx):
        return ((j + (sx > 0)) % 2) * 0.5
    L = 3.0
    A["idle"] = anim(L, True, {
        "body": {"position": lambda t: [0, 0.3 * wave(t, L), 0]},
        "head": rot(lambda t: [3 * wave(t, L), 6 * wave(t, L * 2), 0]),
        "right_mandible": rot(lambda t: [0, -8 * max(0, wave(t, 0.5)) * (1 if (t % L) < 1.0 else 0), 0]),
        "left_mandible": rot(lambda t: [0, 8 * max(0, wave(t, 0.5)) * (1 if (t % L) < 1.0 else 0), 0]),
    }, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {
        "body": rot(lambda t: [0, 3 * wave(t, L), 2 * wave(t, L / 2)]),
        **{leg: rot(lambda t, j=j, sx=sx: [0, sx * 18 * wave(t, L, tri(j, sx)), 0]) for leg, low, j, sx in legs},
        **{low: rot(lambda t, j=j, sx=sx: [0, 0, sx * -16 * max(0, wave(t, L, tri(j, sx) + 0.25))]) for leg, low, j, sx in legs},
    }, 0.05)
    # bite (1.0 s): head down, horn levelled, a forefoot scraping; then the thrust and a toss of the horn at 0.55 s
    L = 1.0
    low = lambda t: hold(t, 0.0, 0.3, 0.45, 0.55)  # noqa: E731
    toss = lambda t: snap(t, 0.45, 0.58, 0.7, 1.0)  # noqa: E731
    A["bite"] = anim(L, False, {
        "head": rot(lambda t: [18 * low(t) - 34 * toss(t), 0, 0]),
        "body": {"rotation": lambda t: [5 * low(t) - 6 * toss(t), 0, 0], "position": lambda t: [0, -1.2 * low(t), 1.2 * low(t) - 3.6 * toss(t)]},
        "right_front": rot(lambda t: [0, -30 * sin01(t, 0.05, 0.25) + 26 * sin01(t, 0.25, 0.45), 0]),
        "right_mandible": rot(lambda t: [0, 26 * low(t) - 30 * toss(t), 0]), "left_mandible": rot(lambda t: [0, -26 * low(t) + 30 * toss(t), 0]),
    }, 0.025)
    L = 1.5
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, -3 * ease_in(window(t, 0.3, 0.9)), 0]},
        "body": rot(lambda t: [6 * math.sin(t * 40) * (1 - window(t, 0, 0.6)), 0, 10 * ease_in(window(t, 0.4, 1.0))]),
        "head": rot(lambda t: [26 * ease_in(window(t, 0.5, 1.2)), 0, 0]),
        **{leg: rot(lambda t, sx=sx: [0, 0, sx * 40 * ease_in_out(window(t, 0.3, 1.0))]) for leg, low_, j, sx in legs},
        **{low_: rot(lambda t, sx=sx: [0, 0, sx * -30 * ease_in_out(window(t, 0.4, 1.1))]) for leg, low_, j, sx in legs},
    }, 0.05)
    write_anims(name, A)


def horn_curve(B, base, segs, mat, **kw):
    """A horn rising from base and curving: segs [(lean forward from upright in degrees, length, thickness)], each
    segment starting where the last one ended (a rhinoceros beetle's horn sweeps forward and up)."""
    x, y, z = base
    out = []
    for ang, ln, w in segs:
        th = math.radians(ang)
        out.append(B.c([x - w / 2, y, z - w / 2], [w, ln, w], mat, pivot=[x, y, z], rot=[ang, 0, 0], **kw))
        y, z = y + math.cos(th) * ln, z - math.sin(th) * ln
    return out


def elytra_seam(f, p, s):
    """The split down a beetle's back: a black seam with the lit lip of each wing case beside it."""
    if f.face != "up":
        return
    ax = np.abs(f.X)
    p.over("black", 0.06, ax < 0.3)
    p.over(s.get("r", "shell"), 0.86, (ax >= 0.3) & (ax < 0.65))


# --------------------------------------------------------------------------------------------- hollow acolyte

def hollow_acolyte():
    """A robed caster in a bone mask: tall and thin in a hooded robe that drags on the ground, a mask of bone with
    slanted empty eyes and a crack of light down it, a stole sewn with runes, long fingers of bone, and an orb of its
    element floating over its right hand."""
    name = "hollow_acolyte"
    B = Body(name, 3600)
    box, col = B.box, B.col
    robe = dict(r="cloth", seed=170)
    B.bone("root", None, [0, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "root", [sx * 1.6, 10, 0], [
            col(sx * 1.6, 0.2, 0.0, 10.0, 2.6, 2.8, "cloth", r="cloth_d", seed=171),
            box(sx * 1.6, 0.6, -0.6, 2.8, 1.2, 3.6, "leather", stitch=False, seed=172),
        ])
    B.bone("body", "root", [0, 11, 0], [
        # the robe: a long skirt that hides the legs, a narrow chest, a stole with a band of runes down the front
        B.c([-4.2, 0.6, -2.8], [8.4, 11.2, 5.8], "cloth", hem=3.0, seed=173, zone="front", bib_w=0.18, band=(2.2, 3.6), skip=("down",),
            **{k: v for k, v in robe.items() if k != "seed"}),
        box(0, 16.2, 0.0, 7.0, 9.4, 4.6, "cloth", zone="front", bib_w=0.2, **robe),
        box(0, 21.4, 0.0, 8.0, 1.6, 5.2, "cloth", r="cloth_d", seed=174),
        *[box(sx * 1.6, 13.0, -2.45, 1.4, 16.0, 0.4, "cloth", r="stole", seed=175 + sx, skip=("south",),
              lines=[dict(pts=[(sx * 1.6, 6.0), (sx * 1.6, 20.4)], w=0.22, faces=("north",), halo=0.4, bright=0.7)])
          for sx in (-1, 1)],
        box(0, 12.0, -2.6, 8.6, 1.0, 0.4, "leather", stitch=False, seed=176),
        box(0, 12.0, -2.9, 1.6, 1.6, 0.4, "bone", seed=177),
    ])
    hood = dict(r="cloth", seed=178)
    B.bone("head", "body", [0, 22.0, 0], [
        box(0, 25.4, -0.2, 5.0, 5.4, 5.0, "void"),
        # the mask: a long face of bone, empty slanted eyes with points of light, a crack of light down it
        box(0, 25.0, -2.9, 4.2, 5.4, 0.6, "bone", seed=179, ends=0.0,
            sockets=sockets(1.0, 26.0, (0.95, 0.42), slant=0.42, core=0.3),
            lines=[dict(pts=[(0.2, 28.0), (-0.3, 26.8), (0.2, 25.6), (-0.1, 24.4)], w=0.2, faces=("north",), halo=0.5, bright=0.8)],
            cuts=[dict(pts=[(-1.2, 23.4), (1.2, 23.4)], w=0.25, faces=("north",)),
                  *[dict(pts=[(x, 23.0), (x, 23.8)], w=0.18, faces=("north",)) for x in (-0.8, -0.27, 0.27, 0.8)]]),
        box(0, 28.6, -0.2, 6.6, 0.8, 6.8, "cloth", dark_faces=("down",), **hood),
        box(3.0, 25.6, 0.0, 0.8, 6.4, 6.6, "cloth", dark_faces=("east",), hem=0.8, **{**hood, "seed": 180}),
        box(-3.0, 25.6, 0.0, 0.8, 6.4, 6.6, "cloth", dark_faces=("west",), hem=0.8, **{**hood, "seed": 181}),
        box(0, 25.6, 3.0, 5.2, 6.4, 0.8, "cloth", **hood),
        box(0, 28.8, -3.2, 6.6, 1.4, 0.8, "cloth", dark_faces=("down", "south"), **hood),
    ], rot=[-4, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 3.6, 20.6, 0], [
            B.c(mirror_x([3.0, 13.4, -1.6], [2.6, 7.6, 3.2], sx), [2.6, 7.6, 3.2], "cloth", hem=2.2, seed=182 + sx, r="cloth"),
        ], rot=[-8, 0, sx * -8])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.3, 14.2, 0], [
            col(sx * 4.0, 0, 8.4, 14.6, 0.6, 0.6, "bone"), col(sx * 4.6, 0, 8.4, 14.6, 0.6, 0.6, "bone"),
            box(sx * 4.3, 8.0, -0.2, 1.6, 1.0, 1.6, "bone"),
            *[B.c([sx * 4.3 - 0.8 + i * 0.55, 4.6, -0.6 + (i % 2) * 0.25], [0.32, 3.6, 0.32], "bone", seed=183 + i, pivot=[sx * 4.3, 7.6, 0], rot=[-16, 0, (i - 1.5) * -7])
              for i in range(4)],
        ], rot=[-14, 0, 0])
    # the orb, floating above the right hand: a glowing core inside a cage of dark bands
    B.bone("orb", "right_hand", [-4.3, 2.6, -2.6], [
        box(-4.3, 2.6, -2.6, 2.0, 2.0, 2.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[45, 45, 0]),
        box(-4.3, 2.6, -2.6, 1.0, 3.4, 1.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[0, 45, 0]),
        box(-4.3, 2.6, -2.6, 3.4, 1.0, 1.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[0, 0, 0]),
    ])
    register(name, lambda aff: pal(aff, (170, 120, 255), {
        "cloth": (54, 44, 70), "cloth_d": (20, 16, 30), "cloth_l": (110, 96, 120),
        "stole": (110, 34, 40), "trim": (176, 140, 72), "bone": (214, 204, 180), "leather": (60, 44, 34),
    }), density=6.0)
    B.write(1.4, 2.6, 1.0)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "body": rot(lambda t: [1.5 * wave(t, L), 0, 1.2 * wave(t, L * 2)]),
        "head": rot(lambda t: [0, 6 * wave(t, L * 2), 0]),
        "right_arm": rot(lambda t: [-22 + 3 * wave(t, L), 0, 0]), "right_hand": rot(lambda t: [-30, 0, 0]),
        "left_arm": rot(lambda t: [-3 * wave(t, L), 0, 0]),
        "orb": {"position": lambda t: [0, 0.6 * wave(t, L / 2), 0], "rotation": lambda t: [0, 120 * t, 0]},
    }, 0.05)
    L = 1.4
    A["walk"] = anim(L, True, {
        "right_leg": rot(lambda t: [-22 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [22 * wave(t, L), 0, 0]),
        "body": {"position": lambda t: [0, -0.3 * abs(wave(t, L)), 0], "rotation": lambda t: [3, 2 * wave(t, L), 0]},
        "right_arm": rot(lambda t: [-22, 0, 0]), "right_hand": rot(lambda t: [-30, 0, 0]),
        "left_arm": rot(lambda t: [10 * wave(t, L), 0, 0]),
        "orb": {"position": lambda t: [0, 0.5 * wave(t, L / 2), 0], "rotation": lambda t: [0, 160 * t, 0]},
    }, 0.05)
    # cast (1.0 s): the orb drawn back and swelling, then the arm thrust forward to loose it at 0.55 s
    L = 1.0
    draw = lambda t: hold(t, 0.0, 0.4, 0.45, 0.55)  # noqa: E731
    loose = lambda t: snap(t, 0.45, 0.56, 0.7, 1.0)  # noqa: E731
    A["cast"] = anim(L, False, {
        "right_arm": rot(lambda t: [-22 + 30 * draw(t) - 70 * loose(t), 0, 0]),
        "right_hand": rot(lambda t: [-30 + 10 * draw(t) + 20 * loose(t), 0, 0]),
        "left_arm": rot(lambda t: [-50 * sin01(t, 0.05, 0.9), 0, 18 * sin01(t, 0.05, 0.9)]),
        "body": rot(lambda t: [-6 * draw(t) + 8 * loose(t), -10 * draw(t), 0]),
        "head": rot(lambda t: [-8 * draw(t) + 6 * loose(t), 0, 0]),
        "orb": {"scale": lambda t: [1 + 0.9 * draw(t) * (1 - window(t, 0.55, 0.62))] * 3, "rotation": lambda t: [0, 540 * t, 0]},
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "body": {"position": lambda t: [0, -7 * ease_in(window(t, 0.2, 0.8)), 0], "rotation": lambda t: [30 * ease_in(window(t, 0.3, 0.9)), 0, 0],
                 "scale": lambda t: [1, 1 - 0.3 * window(t, 0.2, 0.8), 1]},
        "head": rot(lambda t: [40 * window(t, 0.3, 0.9), 0, 16 * window(t, 0.5, 1.0)]),
        "right_arm": rot(lambda t: [-22 + 40 * window(t, 0.1, 0.6), 0, 0]), "left_arm": rot(lambda t: [20 * window(t, 0.1, 0.6), 0, 0]),
        "right_leg": rot(lambda t: [-60 * window(t, 0.2, 0.8), 0, 0]), "left_leg": rot(lambda t: [-60 * window(t, 0.2, 0.8), 0, 0]),
        "orb": {"scale": lambda t: [max(0.01, 1 - window(t, 0.0, 0.4))] * 3},
    }, 0.05)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- ashen sentinel

def ashen_sentinel():
    """A soldier of ash in full plate: a great helm with a slit of fire for eyes and a ragged crest, a tabard gone to
    rags, embers glowing in the joints of its armour, a long sword with a burning fuller, and a tower shield bearing
    a sigil that holds off anything from the front."""
    name = "ashen_sentinel"
    B = Body(name, 3700)
    box, col = B.box, B.col
    pl = dict(r="plate", engrave="trim")
    B.bone("root", None, [0, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "root", [sx * 2.2, 12.6, 0], [
            col(sx * 2.2, 0, 6.6, 12.8, 3.6, 3.8, "plate", **pl, seed=190),
            box(sx * 2.2, 6.6, -1.8, 3.0, 2.2, 0.8, "plate", **pl, seed=191, rivets=False),
        ])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.2, 6.6, 0], [
            col(sx * 2.2, 0, 1.2, 6.8, 3.4, 3.6, "plate", **pl, seed=192),
            box(sx * 2.2, 0.7, -0.6, 3.8, 1.4, 5.2, "plate", **pl, seed=193, rivets=False),
        ])
    B.bone("body", "root", [0, 12.6, 0], [
        box(0, 13.4, 0, 8.4, 2.6, 4.8, "plate", **pl, seed=194, rivets=False),
        box(0, 19.6, -0.2, 8.6, 9.8, 5.4, "plate", **pl, seed=195,
            lines=[dict(pts=[(-3.0, 17.4), (3.0, 17.4)], w=0.3, faces=("north",), halo=0.6, bright=0.75)]),
        box(0, 22.4, -2.9, 6.0, 3.6, 0.4, "plate", r="plate", seed=196, rivets=False),
        # the tabard: a rag hanging front and back from the belt
        B.c([-2.4, 4.0, -2.75], [4.8, 10.6, 0.4], "cloth", r="tabard", hem=3.2, seed=197, skip=("up", "down"),
            band=(10.4, 11.6), runes=False),
        B.c([-3.0, 3.0, 2.45], [6.0, 11.6, 0.4], "cloth", r="tabard", hem=3.6, seed=198, skip=("up", "down")),
        box(0, 14.4, 0, 9.0, 1.2, 5.6, "leather", stitch=False, seed=199),
        box(0, 14.4, -2.85, 1.8, 1.6, 0.3, "trim"),
    ])
    B.bone("head", "body", [0, 24.6, 0], [
        # the great helm: a slit of fire for eyes, breathing holes, a ragged crest
        box(0, 28.0, -0.2, 6.6, 6.8, 6.6, "plate", **pl, seed=200, rivets=False,
            lines=[dict(pts=[(-2.4, 28.8), (2.4, 28.8)], w=0.5, faces=("north",), halo=0.9, bright=1.0)],
            holes=[dict(x=x, y=y, r=(0.22, 0.22)) for x in (-1.6, -0.8, 0.8, 1.6) for y in (26.4, 25.6)]),
        box(0, 28.0, -3.6, 1.0, 6.2, 0.6, "plate", **pl, seed=201, rivets=False),
        box(0, 31.8, 0.2, 1.0, 1.6, 6.0, "plate", **pl, seed=202, rivets=False),
        B.c([-0.4, 30.8, 0.0], [0.8, 5.0, 6.0], "hair", r="crest", seed=203, pivot=[0, 32.4, 0.6], rot=[-28, 0, 0], skip=("up",)),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 5.0, 23.2, 0], [
            B.c(mirror_x([3.6, 21.6, -3.2], [4.4, 3.4, 6.4], sx), [4.4, 3.4, 6.4], "plate", **pl, seed=204 + sx, pivot=[sx * 5.0, 23.2, 0], rot=[0, 0, sx * -12]),
            col(sx * 5.8, 0, 16.4, 22.6, 3.2, 3.4, "plate", **pl, seed=206 + sx),
        ], rot=[0, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.8, 16.6, 0], [
            col(sx * 5.8, 0, 10.6, 16.8, 3.0, 3.2, "plate", **pl, seed=208 + sx),
            box(sx * 5.8, 10.0, -0.2, 3.4, 2.4, 3.6, "plate", **pl, seed=210 + sx, rivets=False),
        ], rot=[-10, 0, 0])
    # the long sword, held point-forward low in the right hand, its fuller burning
    B.bone("blade", "right_hand", [-5.8, 10.0, -0.4], [
        B.c([-6.3, 9.4, -2.4], [1.0, 1.2, 4.4], "leather", stitch=False, seed=212),
        B.c([-6.6, 8.8, -3.4], [1.6, 2.4, 1.0], "trim"),
        B.c([-7.0, 9.2, -5.0], [2.4, 1.6, 1.0], "trim"),
        B.c([-6.4, 9.15, -20.4], [0.8, 1.7, 15.6], "metal", r="steel", edge="down", nicks=True),
        B.c([-6.45, 9.75, -19.4], [0.9, 0.5, 14.0], "glow"),
    ], rot=[18, 0, 0])
    # the tower shield on the left arm: iron-bound, a sigil of light in its face
    B.bone("shield", "left_hand", [7.4, 13.0, -0.4], [
        B.c([7.4, 4.2, -5.4], [1.2, 15.6, 10.6], "plate", r="shield", engrave="trim", seed=213,
            fx=shield_sigil),
        B.c([7.2, 3.8, -5.8], [1.6, 0.8, 11.4], "trim"),
        B.c([7.2, 19.4, -5.8], [1.6, 0.8, 11.4], "trim"),
    ])
    register(name, lambda aff: pal(aff, (255, 120, 40), {
        "plate": [(4, 4, 6), (22, 22, 26), (58, 58, 64), (120, 118, 116), (196, 190, 180)],
        "shield": [(4, 4, 6), (18, 18, 22), (46, 46, 52), (104, 100, 98), (176, 168, 156)],
        "trim": [(20, 14, 8), (64, 46, 26), (120, 90, 52), (176, 140, 84), (226, 200, 140)],
        "tabard": (84, 24, 22), "tabard_d": (34, 10, 12), "tabard_l": (130, 52, 42), "crest": (66, 20, 18),
        "steel": [(8, 10, 14), (40, 42, 50), (96, 98, 108), (164, 166, 174), (232, 232, 236)],
        "leather": (56, 40, 30),
    }, tinted=("plate", "shield", "tabard", "tabard_d", "tabard_l", "crest")), density=5.0)
    B.write(1.8, 2.6, 1.0)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "body": rot(lambda t: [1.2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [0, 5 * wave(t, L * 2), 0]),
        "right_arm": rot(lambda t: [-6 + 2 * wave(t, L), 0, 0]), "left_arm": rot(lambda t: [-24 - 2 * wave(t, L), 18, 0]),
        "left_hand": rot(lambda t: [-30, 0, 0]),
    }, 0.1)
    L = 1.1
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), arms=("right_arm",), swing=24, arm_swing=14, knee=24, bob=0.5),
        "left_arm": rot(lambda t: [-24, 18, 0]), "left_hand": rot(lambda t: [-30, 0, 0]),
    }, 0.05)
    # attack (0.9 s): the sword drawn up over the right shoulder, brought down and across at 0.45 s
    L = 0.9
    up = lambda t: hold(t, 0.0, 0.32, 0.35, 0.45)  # noqa: E731
    cut = lambda t: snap(t, 0.35, 0.47, 0.6, 0.9)  # noqa: E731
    A["attack"] = anim(L, False, {
        "right_arm": rot(lambda t: [-150 * up(t) - 40 * cut(t), 20 * up(t) - 30 * cut(t), 0]),
        "right_hand": rot(lambda t: [-20 * up(t), 0, 0]),
        "blade": rot(lambda t: [-30 * up(t) + 10 * cut(t), 0, 0]),
        "body": rot(lambda t: [-6 * up(t) + 10 * cut(t), 18 * up(t) - 24 * cut(t), 0]),
        "left_arm": rot(lambda t: [-24, 18 - 10 * up(t), 0]), "left_hand": rot(lambda t: [-30, 0, 0]),
    }, 0.025)
    # block (1.0 s): the shield brought square across the front, braced
    L = 1.0
    br = lambda t: hold(t, 0.0, 0.15, 0.7, 1.0)  # noqa: E731
    A["block"] = anim(L, False, {
        "left_arm": rot(lambda t: [-24 - 40 * br(t), 18 + 26 * br(t), 0]), "left_hand": rot(lambda t: [-30 + 20 * br(t), 0, 0]),
        "body": {"rotation": lambda t: [6 * br(t), 0, 0], "position": lambda t: [0, -0.8 * br(t), 0]},
        "right_leg": rot(lambda t: [-16 * br(t), 0, 0]), "right_shin": rot(lambda t: [18 * br(t), 0, 0]),
    }, 0.025)
    L = 1.4
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [-80 * ease_in(window(t, 0.6, 1.2)), 0, 0], "position": lambda t: [0, -5 * window(t, 0.1, 0.5), -2 * window(t, 0.6, 1.2)]},
        "right_leg": rot(lambda t: [-80 * window(t, 0.1, 0.5), 0, 0]), "right_shin": rot(lambda t: [90 * window(t, 0.1, 0.5), 0, 0]),
        "left_leg": rot(lambda t: [-20 * window(t, 0.1, 0.5), 0, 0]), "left_shin": rot(lambda t: [70 * window(t, 0.1, 0.5), 0, 0]),
        "head": rot(lambda t: [24 * window(t, 0.3, 0.8), 0, 0]),
        "right_arm": rot(lambda t: [30 * window(t, 0.2, 0.7), 0, 20 * window(t, 0.6, 1.2)]),
        "left_arm": rot(lambda t: [-24 + 20 * window(t, 0.2, 0.7), 18, -20 * window(t, 0.6, 1.2)]),
    }, 0.05)
    write_anims(name, A)


def shield_sigil(f, p, s):
    """A tower shield's face: an iron boss and a ring-and-spike sigil of light."""
    if f.face != "west":
        return
    cz, cy = -0.1, 12.0
    a = f.Z - cz
    b = f.Y - cy
    r = np.hypot(a, b * 0.9)
    p.over("trim", 0.5 + 0.3 * (b > 0), (r > 2.4) & (r < 2.9))
    ring = (r > 3.3) & (r < 3.7)
    spike = (np.abs(a) < 0.35 * (1 - np.abs(b) / 6.4)) & (np.abs(b) < 6.4) & (r > 3.7)
    cross = (np.abs(b) < 0.3 * (1 - np.abs(a) / 4.6)) & (np.abs(a) < 4.6) & (r > 3.7)
    p.glow("glow", 0.8, ring | spike | cross)
    p.over("trim", 0.62, r < 1.2)


# --------------------------------------------------------------------------------------------- emberwisp

def emberwisp():
    """A burning skull that flies: old cracked bone with fire showing through, eyes like coals, a jaw that chatters,
    flames streaming up and back from its crown, sparks circling it. It swells and bursts when it reaches you."""
    name = "emberwisp"
    B = Body(name, 3800)
    box = B.box
    bn = dict(seed=210, cracks=dict(k=0.5, w=0.008, cover=0.6), fx=cracks_fx)
    B.bone("root", None, [0, 0, 0])
    B.bone("head", "root", [0, 6.5, 0], [
        box(0, 7.6, 0.2, 6.6, 5.6, 6.8, "bone", **bn,
            sockets=sockets(1.5, 7.5, (1.3, 0.95), slant=0.38, core=0.26)),
        box(0, 5.0, -1.6, 5.2, 1.8, 3.6, "bone", **bn,
            holes=[dict(x=0.35, y=5.6, r=(0.3, 0.45)), dict(x=-0.35, y=5.6, r=(0.3, 0.45))]),
        box(0, 4.4, -3.25, 4.4, 1.0, 0.4, "teeth", n=6, fangs=1, up=True, skip=("up", "down", "south")),
        *[box(sx * 3.45, 6.6, -0.6, 0.6, 2.2, 3.0, "bone", seed=211) for sx in (-1, 1)],
    ])
    B.bone("jaw", "head", [0, 4.6, 0.6], [
        box(0, 3.4, -1.4, 4.8, 1.4, 3.6, "bone", seed=212),
        box(0, 4.35, -3.05, 4.0, 0.9, 0.4, "teeth", n=5, fangs=1, up=False, skip=("up", "down", "south")),
    ], rot=[10, 0, 0])
    # the flames: tongues of fire leaning back from the crown, longest in the middle
    flames = []
    for i, (x, h, lean, w) in enumerate(((0.0, 6.0, -26, 2.6), (-2.0, 4.4, -34, 2.0), (2.0, 4.6, -30, 2.0), (-1.0, 3.2, -48, 1.6), (1.2, 3.4, -44, 1.6))):
        z = 0.6 + (i % 2) * 1.2
        flames.append(B.c([x - w / 2, 9.6, z - w / 2], [w, h * 1.4, w], "glow", r="flame", pivot=[x, 10.4, z], rot=[lean, 0, x * -8], fx=flame_fx, seed=220 + i))
    B.bone("flames", "head", [0, 10.4, 0.6], flames)
    B.bone("embers", "root", [0, 6, 0], [box(4.4 * math.cos(a), 5.0 + 2 * math.sin(a * 2), 4.4 * math.sin(a), 1.0, 1.0, 1.0, "glow", r="flame")
                                         for a in (0, 2.1, 4.2)])
    register(name, lambda aff: pal(aff, (255, 150, 50), {"bone": (198, 182, 152)}, extra=flame_ramp(aff)), density=7.0)
    B.write(1.4, 1.6, 0.4)

    A = {}
    L = 2.0
    flicker = lambda t, ph=0.0: [1 + 0.08 * math.sin(t * 23 + ph), 1 + 0.18 * math.sin(t * 17 + ph) * math.sin(t * 5.3), 1 + 0.08 * math.sin(t * 19 + ph)]  # noqa: E731
    A["idle"] = anim(L, True, {
        "root": {"position": lambda t: [0, 1.0 * wave(t, L), 0]},
        "head": rot(lambda t: [4 * wave(t, L), 0, 3 * wave(t, L / 2)]),
        "jaw": rot(lambda t: [8 * max(0, wave(t, 0.25)) * (1 if (t % L) < 0.75 else 0), 0, 0]),
        "flames": {"scale": lambda t: flicker(t), "rotation": lambda t: [4 * wave(t, 0.5), 0, 3 * wave(t, 0.7)]},
        "embers": rot(lambda t: [0, 180 * t, 0]),
    }, 0.05)
    A["walk"] = anim(L, True, {
        "root": {"position": lambda t: [0, 0.6 * wave(t, L), 0]},
        "head": rot(lambda t: [14, 0, 4 * wave(t, L / 2)]),
        "jaw": rot(lambda t: [6 * max(0, wave(t, 0.3)), 0, 0]),
        "flames": {"scale": lambda t: [1.05, 1.25 + 0.15 * math.sin(t * 17), 1.05], "rotation": lambda t: [-16 + 4 * wave(t, 0.5), 0, 0]},
        "embers": rot(lambda t: [0, 360 * t, 0]),
    }, 0.05)
    # attack (1.1 s, the fuse): it swells, shakes and its jaw gapes, the flames roaring up
    L = 1.1
    A["attack"] = anim(L, False, {
        "head": {"scale": lambda t: [1 + 0.4 * window(t, 0, 1.0) + 0.08 * math.sin(t * 60)] * 3,
                 "rotation": lambda t: [6 * math.sin(t * 50) * window(t, 0.2, 1.0), 0, 6 * math.sin(t * 43) * window(t, 0.2, 1.0)]},
        "jaw": rot(lambda t: [10 + 30 * window(t, 0, 0.4), 0, 0]),
        "flames": {"scale": lambda t: [1 + 0.5 * window(t, 0, 1.0), 1 + 1.0 * window(t, 0, 1.0) + 0.2 * math.sin(t * 30), 1 + 0.5 * window(t, 0, 1.0)]},
        "embers": {"rotation": lambda t: [0, 900 * t, 0], "scale": lambda t: [1 + window(t, 0, 1.0)] * 3},
    }, 0.025)
    L = 0.5
    A["death"] = anim(L, "hold", {
        "head": {"scale": lambda t: [max(0.01, 1 + 0.6 * window(t, 0, 0.2) - 1.6 * window(t, 0.2, 0.5))] * 3},
        "embers": {"scale": lambda t: [max(0.01, 1 + 2 * window(t, 0, 0.5))] * 3},
    }, 0.025)
    write_anims(name, A)


def flame_fx(f, p, s):
    """A tongue of fire cut out of a glowing cube: wide and white-hot at its root, licking up to a wavering point."""
    if not f.wall:
        p.cut(np.zeros(f.X.shape, bool) if f.face == "up" else np.ones(f.X.shape, bool))
        return
    k = 1 - f.V                                              # 0 at the root .. 1 at the top
    sd = s.get("seed", 220)
    wob = 0.12 * np.sin(k * 7.0 + sd) + 0.06 * np.sin(k * 15.0 + sd * 1.7)
    half = 0.5 * np.clip(1.05 - k, 0, 1) ** 0.75
    u = f.U - 0.5 - wob * k
    p.cut(np.abs(u) < half)
    core = np.abs(u) / np.maximum(half, 1e-6)
    p.glow(s.get("r", "flame"), np.clip(0.95 - 0.55 * k - 0.3 * core, 0.2, 1.0))


def flame_ramp(aff):
    """Fire in the affinity's colour (its own orange when plain): a dark red root up to a white-hot core."""
    g = CS.glow_of(aff, (255, 150, 50))
    return {"flame": [CS.mul(g, 0.36), CS.mul(g, 0.7), g, CS.mix(g, (255, 236, 190), 0.55), (255, 250, 236)]}


# ============================================================================================= main

CREATURES = [gutterkin, grave_thrall, bone_archer, rime_stalker, tuskbrute, tusk_shaman, deepkin, duskwing, hive_drone, brimling,
             wailing_wraith, static_drifter, rimeclaw, pit_ravager, coilfang, cragcrawler, hive_reaver, rune_colossus, thorn_spitter,
             broodling, shardback, hollow_acolyte, ashen_sentinel, emberwisp]


def eggs():
    items = os.path.join(TEX, "..", "models", "item")
    os.makedirs(items, exist_ok=True)
    for make in CREATURES:
        with open(os.path.join(items, make.__name__ + "_spawn_egg.json"), "w") as f:
            json.dump({"parent": "minecraft:item/template_spawn_egg"}, f, indent=2)


def main():
    only = sys.argv[1:]
    for make in CREATURES:
        if not only or make.__name__ in only:
            make()
    if not only:
        eggs()
    print("creatures written")


if __name__ == "__main__":
    main()
