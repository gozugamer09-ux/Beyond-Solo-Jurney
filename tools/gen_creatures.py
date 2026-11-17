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

import creature_anatomy as CA  # noqa: E402
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


def curl(B, base, sx, segs, mat, z_step=0.0, overlap=0.0, **kw):
    """A horn curling out from base on the side sx: segs [(angle from upright in degrees, length, thickness)], each
    segment starting where the last one ended (overlap: how far each runs on past the next one's start, to close the
    gaps at the joints)."""
    x, y, z = base
    out = []
    total = sum(ln for _, ln, _ in segs)
    done = 0.0
    for i, (ang, ln, w) in enumerate(segs):
        th = math.radians(ang)
        extra = dict(kr=(done / total, (done + ln) / total)) if mat == "horn" and "kr" not in kw else {}
        ext = overlap if i < len(segs) - 1 else 0.0
        out.append(B.c([x - w / 2, y, z - w / 2], [w, ln + ext, w], mat, pivot=[x, y, z], rot=[0, 0, sx * ang], **kw, **extra))
        x, y, z = x + sx * math.sin(th) * ln, y + math.cos(th) * ln, z + z_step
        done += ln
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
    """The features a cube's paint spec asks for: anatomy, eyes, sockets, a mouth, lines and runes of light, war paint."""
    if "anat" in s:
        CA.anatomy(p, f, s["anat"])
    for pf in s.get("paint_faces", ()):
        # a whole face in another ramp (the roof of a mouth, a tongue, a sole)
        if f.face in pf["faces"]:
            n = CS.fbm3(f.X * 1.2, f.Y * 1.2, f.Z * 1.2, pf.get("seed", 3), 2)
            p.over(pf["ramp"], pf.get("tone", 0.4) + (n - 0.5) * pf.get("var", 0.16), 1.0)
    for lb in s.get("lobes", ()):
        # masses under the skin placed by hand (a's front is -1 on the sides): a shoulder, a haunch, a jowl
        if f.face in lb.get("faces", ("west", "east")):
            for sg in ((-1, 1) if lb.get("sym") and not f.side else (1,)):
                CA.lobe(p, f, sg * lb["a"], lb["h"], lb["ra"], lb["rh"], k=lb.get("k", 0.1), rim=lb.get("rim", 0.14),
                        top=lb.get("top", 0.45))
    for gr in s.get("grooves", ()):
        if f.face in gr.get("faces", ("west", "east")):
            for sg in ((-1, 1) if gr.get("sym") and not f.side else (1,)):
                CA.groove(p, f, [(sg * a, h) for a, h in gr["pts"]], w=gr.get("w", 1.0), k=gr.get("k", 0.14), lip=gr.get("lip", 0.06))
    for ln in s.get("facepaint", ()):
        # paint laid on before the eyes, so they shine through a mask of it
        if f.face not in ln.get("faces", ("north",)):
            continue
        a = CS.across(f) if f.wall else f.X
        b = f.Y if f.wall else f.Z
        dmin = np.full(f.X.shape, 99.0)
        for (a0, b0), (a1, b1) in zip(ln["pts"], ln["pts"][1:]):
            d, _ = CS.seg_dist(a, b, a0, b0, a1, b1)
            dmin = np.minimum(dmin, d)
        rough = CS.fbm3(f.X * 1.7, f.Y * 1.7, f.Z * 1.7, ln.get("seed", 4), 2)
        p.over(ln.get("ramp", "warpaint"), ln.get("tone", 0.4) + (rough - 0.5) * 0.2, dmin < ln.get("w", 0.6) / 2 * (0.8 + 0.4 * rough))
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
    for wt in ([s["spotted"]] if isinstance(s.get("spotted"), dict) else s.get("spotted", ())):
        # raised warts or pustules scattered over the faces, lit from above
        if f.face in wt.get("faces", ("north", "south", "east", "west", "up")):
            CS.bumps(p, f, wt.get("ramp", "skin_l"), wt.get("seed", 13), wt.get("cell", 1.0), wt.get("radius", 0.3), wt.get("density", 0.4),
                     tone=wt.get("tone", 0.5), relief=wt.get("relief", 0.24), rim=wt.get("rim", 0.08))
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
    if "tiger" in s:
        # stripes of a darker tone across the body, thinning and breaking as a big cat's do
        tg = s["tiger"]
        if f.face in tg.get("faces", ("north", "south", "east", "west", "up")):
            m = np.ones(f.X.shape, bool)
            if f.side and tg.get("back_half"):
                m = f.Z > f.cz + tg.get("shift", 0.0)
            coord = tg.get("ky", 1.0) * f.Y + tg.get("lean", 0.5) * np.abs(f.X - tg.get("cx", 0.0)) + tg.get("kz", 0.0) * f.Z
            CA.bands(p, f, tg.get("ramp", "skin_d"), coord, tg.get("period", 1.8), tg.get("width", 0.3), tone=tg.get("tone", 0.32),
                     wobble=tg.get("wobble", 0.6), seed=tg.get("seed", 3), mask=m, broken=tg.get("broken", 0.6))
    if isinstance(s.get("grime"), dict):
        g = s["grime"]
        CA.grime(p, f, g.get("ramp", "mud"), g.get("height", 3.0), s.get("ground", 0.0), g.get("tone", 0.3), g.get("seed", 5),
                 g.get("weight", 0.85))
    if "flush" in s:
        fl = s["flush"]
        if f.face in fl.get("faces", ("north", "south", "east", "west", "up", "down")):
            hn = (f.Y - f.bottom) / max(f.top - f.bottom, 1e-6)
            w = np.ones(f.X.shape)
            if "low" in fl:
                w = w * CS.sstep(fl["low"], 0.0, hn)
            if "high" in fl:
                w = w * CS.sstep(fl["high"], 1.0, hn)
            if fl.get("inner") and f.wall:
                w = w * (1.0 if f.inner else 0.35)
            CA.flush(p, f, fl.get("ramp", "flush"), w, fl.get("tone", 0.5), fl.get("weight", 0.55))
    if "wrinkles" in s:
        CA.face_folds(p, f, **s["wrinkles"])
    if "ear" in s:
        CA.ear(p, f, **s["ear"])
    if "nostrils" in s:
        CA.nostrils(p, f, **s["nostrils"])
    for cm in s.get("claws", ()):
        CA.claw_marks(p, f, **cm)
    if "rings" in s:
        CA.rings(p, f, **s["rings"])
    for zz in s.get("zigzag", ()):
        CA.zigzag(p, f, **zz)
    for st in s.get("stitches", ()):
        CA.stitched(p, f, **st)
    if "lights" in s:
        CA.photophores(p, f, **s["lights"])
    if "lava" in s and min(f.fw, f.fh) > 1.2:
        # fire under a cracked hide: a few long cracks per face, glowing, with charred rims
        lv = s["lava"]
        fs = CS.face_seed(f, lv.get("seed", 41))
        n = max(int(f.fw * f.fh / lv.get("area", 10.0)), 1) * (1 if CS.hash01(fs, 3) < lv.get("chance", 0.7) else 0)
        d, al = CS.polylines(f, fs, n, length=(1.2, 3.2), steps=(3, 5), wander=1.3)
        w = f.texel * lv.get("w", 0.6) * (0.45 + 0.9 * np.sin(np.clip(al, 0, 1) * math.pi))
        p.over(lv.get("rim", "black"), 0.12, (d < w * lv.get("rim_w", 2.4)) & (d >= w))
        p.glow("glow", lv.get("tone", 0.5) + 0.35 * np.sin(np.clip(al, 0, 1) * math.pi), d < w)
    if "wraps" in s:
        wr = dict(s["wraps"])
        CA.wraps(p, f, wr.pop("ramp", "wrap"), *wr.pop("y"), **wr)
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
    """A goblin-like raider no taller than a child: bow legs, a hunched back, a big domed head with long ragged ears,
    a long hooked nose with a ring through it, a wide grin of crooked teeth, yellow eyes in a band of red war paint and
    a scar down one of them, a top-knot tied with a ring of bone. A notched cleaver, a scrap of rusted iron strapped
    over one shoulder, a mangy fur collar, a rag at the hips, pouches and bone trinkets."""
    name = "gutterkin"
    B = Body(name, 1500)
    c = B.c
    stripes = dict(faces=("south", "up", "east", "west"), period=2.1, width=0.3, lean=0.35, broken=0.85, wobble=1.0, tone=0.3, back_half=True)
    skin = dict(seed=3, mottle=0.12, fat=0.08, blotch=0.2, tiger=stripes)
    plain = dict(seed=3, mottle=0.12, fat=0.08, blotch=0.2)
    limb = dict(seed=3, mottle=0.12, fat=0.08, blotch=0.2, tiger=dict(stripes, lean=0.0, period=1.3, back_half=False, faces=("south", "up", "west", "east")))
    dirt = dict(ramp="mud", height=3.4)
    gold = dict(r="gold", fuller=False, scratches=0)
    rusty = dict(r="scrap", fuller=False, rust=0.5, scratches=2, nicks=True)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 8, 0.5], [
        c([-3, 7, -1.6], [6, 3, 4], "skin", zone="back", **skin),
        c([-3.3, 8.6, -1.9], [6.6, 1.2, 4.6], "leather", stitch=False),
        # the rag: a strip in front and one behind, torn at the hem
        c([-2.2, 3.4, -2.1], [4.4, 5.4, 0.4], "cloth", hem=1.4, seed=61, skip=("up", "down")),
        c([-2.6, 3.8, 2.3], [5.2, 5.0, 0.4], "cloth", hem=1.6, seed=62, skip=("up", "down")),
        # trinkets on the belt: a little skull and two bones; a pouch on either hip
        c([2.5, 7.3, -2.5], [1.5, 1.5, 1.3], "bone", seed=83, cracks=False,
          holes=[dict(x=2.9, y=8.15, r=(0.24, 0.26)), dict(x=3.6, y=8.15, r=(0.24, 0.26))]),
        c([-3.2, 6.6, -2.2], [0.6, 2.2, 0.6], "bone", seed=84),
        c([-2.5, 6.9, -2.3], [0.6, 1.8, 0.6], "bone", seed=85),
        c([-4.1, 6.4, -0.9], [1.2, 2.2, 2.0], "leather", seed=86, stitch=True),
        c([3.0, 6.7, 0.5], [1.1, 1.8, 1.7], "leather", seed=87, stitch=True),
    ])
    B.bone("chest", "hips", [0, 9.5, 0.5], [
        c([-3.6, 9.5, -2.4], [7.2, 6.5, 5.2], "skin", zone="front", bib_w=0.26, anat=dict(part="chest", kind="ribs"), **skin),
        # a lumpy sack of loot slung on the back, its neck tied off with rope
        c([-2.5, 10.2, 2.5], [4.6, 3.8, 2.8], "cloth", r="sack", seed=63, folds=2.2, pivot=[0, 12.0, 3.9], rot=[0, 0, 12]),
        c([-1.9, 9.6, 2.9], [3.4, 1.0, 2.2], "cloth", r="sack", seed=64, folds=2.2, pivot=[0, 12.0, 3.9], rot=[0, 0, 12]),
        c([-0.8, 13.9, 3.3], [1.5, 1.2, 1.3], "cloth", r="sack", seed=65, folds=3.0, pivot=[0, 12.0, 3.9], rot=[0, 0, 12]),
        c([-0.95, 13.8, 3.15], [1.8, 0.45, 1.6], "leather", r="rope", stitch=False, pivot=[0, 12.0, 3.9], rot=[0, 0, 12]),
        # a strap across the chest
        c([-3.8, 11.6, -2.7], [7.6, 1.0, 0.4], "leather", pivot=[0, 12.1, -2.5], rot=[0, 0, 32], stitch=False),
        # a mangy collar of fur around the neck, a necklace of teeth hanging from under it
        c([-4.0, 14.2, -2.9], [8.0, 2.2, 6.0], "mane", r="fur", seed=88, len=1.4, width=0.8, ragged=0.7, lock_k=0.7),
        c([-3.4, 15.9, -2.4], [6.8, 0.9, 5.2], "mane", r="fur", seed=89, len=1.2, width=0.7, ragged=0.4, lock_k=0.7),
        *[c([-2.2 + i * 1.1, 13.0 + (0.3 if i in (1, 3) else 0.0), -2.95], [0.5, 1.3 - (0.3 if i in (1, 3) else 0.0), 0.4], "bone", seed=86 + i)
          for i in range(5)],
    ], rot=[24, 0, 0])
    # the face: war paint across the eyes and running down from them, a scar down the left eye
    paint = [dict(pts=[(-3.6, 18.85), (3.6, 18.85)], w=1.75, ramp="paint", tone=0.3),
             dict(pts=[(-1.45, 18.0), (-1.6, 16.3)], w=0.42, ramp="paint", tone=0.26),
             dict(pts=[(2.55, 20.3), (1.15, 16.6)], w=0.48, ramp="scar", tone=0.6)]
    head = [
        c([-3.5, 15.6, -7.2], [7.0, 4.8, 5.6], "skin", zone="back", split=(0.74, -1.0), facepaint=paint,
          eyes=eyes(1.7, 18.85, (1.05, 0.6), slant=0.42, pupil="slit"),
          wrinkles=dict(eyes=[(1.7, 18.85), (-1.7, 18.85)], eye_r=(1.05, 0.6), nose=(0, 17.3), mouth=(-2.7, 2.7, 15.9), k=2.2,
                     frown=(0.45, 19.0, 20.2)),
          cuts=[dict(pts=[(2.5, 20.1), (2.25, 19.55)], w=0.16, faces=("north",), ramp="skin_d", tone=0.12),
                dict(pts=[(1.45, 17.5), (1.2, 16.9)], w=0.16, faces=("north",), ramp="skin_d", tone=0.12)],
          **skin),
        # the skull: a low forehead sloping back from the brows into a dome that swells over the nape
        c([-3.25, 18.2, -6.9], [6.5, 3.2, 7.0], "skin", zone="back", pivot=[0, 19.8, -3.4], rot=[12, 0, 0], **dict(skin, tiger=dict(stripes, faces=("up",)))),
        c([-2.9, 16.4, -1.9], [5.8, 3.8, 2.0], "skin", zone="limb", **plain),
        # a long hooked nose drooping at its tip, a ring of gold through it
        c([-0.75, 16.6, -8.6], [1.5, 2.6, 1.6], "skin", pivot=[0, 18, -7.4], rot=[-14, 0, 0], seed=5, mottle=0.1, blotch=0.2,
          flush=dict(low=0.6, weight=0.45)),
        c([-0.6, 15.9, -9.5], [1.2, 1.3, 1.4], "skin", pivot=[0, 16.6, -8.8], rot=[16, 0, 0], seed=5, mottle=0.1, blotch=0.2,
          flush=dict(weight=0.55), nostrils=dict(x=0.32, z=-8.9)),
        c([-0.4, 15.2, -9.15], [0.8, 0.7, 0.3], "metal", **gold),
        # upper teeth under the lip
        c([-2.8, 15.0, -7.35], [5.6, 0.9, 0.4], "teeth", n=6, fangs=1, up=True, skip=("up", "down", "south")),
        # a top-knot tied with a ring of bone, its ends bristling up
        c([-0.95, 21.6, -3.2], [1.9, 2.6, 1.9], "hair", r="hair", seed=7, ragged=0.9, pivot=[0, 22.9, -2.25], rot=[-8, 0, 180], skip=("down",)),
        c([-1.1, 21.7, -3.35], [2.2, 0.6, 2.2], "bone", seed=9, cracks=False),
    ]
    for sx in (-1, 1):
        # brows that slant down to the nose, cheekbones jutting under the eyes
        head.append(c(mirror_x([0.2, 19.45, -7.6], [3.3, 0.85, 1.2], sx), [3.3, 0.85, 1.2], "skin", r="skin_d", pivot=[sx * 0.3, 19.85, -7.0],
                      rot=[0, 0, -sx * 14], seed=4, skip=("south",)))
        head.append(c(mirror_x([2.7, 16.4, -7.4], [1.4, 1.15, 3.2], sx), [1.4, 1.15, 3.2], "skin", pivot=[sx * 3.0, 16.9, -6.0],
                      rot=[0, -sx * 24, sx * -10], **plain))
        # long ears: out, back and up, narrowing to a drooping point; the right one torn short, two rings of gold in the left
        piv, turn = [sx * 3.5, 18.4, -4.2], [8, sx * -38, sx * 16]
        head += [
            c(mirror_x([3.3, 17.3, -4.7], [3.0, 2.6, 0.9], sx), [3.0, 2.6, 0.9], "skin", pivot=piv, rot=turn, zone="limb",
              ear=dict(weight=0.55), **skin),
            c(mirror_x([6.2, 17.6, -4.6], [2.6, 2.0, 0.7], sx), [2.6, 2.0, 0.7], "skin", pivot=piv, rot=turn, zone="limb",
              ear=dict(weight=0.5), **skin),
            c(mirror_x([8.7, 17.6, -4.5], [2.4 if sx > 0 else 1.2, 1.2, 0.5], sx), [2.4 if sx > 0 else 1.2, 1.2, 0.5], "skin", pivot=piv,
              rot=turn, zone="limb", flush=dict(weight=0.25), **skin),
        ]
        if sx > 0:
            head += [c([x, 16.9, -4.45], [0.45, 0.8, 0.35], "metal", pivot=piv, rot=turn, **gold) for x in (6.7, 7.8)]
    B.bone("head", "chest", [0, 15.4, -1.8], head, rot=[-22, 0, 0])
    B.bone("jaw", "head", [0, 16.0, -3.0], [
        c([-2.9, 14.0, -7.0], [5.8, 1.7, 4.4], "skin", zone="back", split=(0.8, 0.5), **skin),
        # a narrow, pointed chin
        c([-1.3, 13.3, -7.15], [2.6, 1.2, 2.8], "skin", zone="back", **plain),
        c([-2.6, 15.55, -6.9], [5.2, 0.8, 0.4], "teeth", n=5, fangs=1, up=False, skip=("up", "down", "south")),
        c([-2.6, 15.0, -6.6], [5.2, 0.6, 3.6], "mouth"),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        # arms long enough to reach the knees; a scrap of rusted iron lashed over the right shoulder with rope
        arm = [c(mirror_x([3.4, 9.8, -0.6], [2.4, 5.6, 2.4], sx), [2.4, 5.6, 2.4], "skin", zone="limb", anat=dict(part="upper", k=1.3), **limb)]
        if sx < 0:
            arm += [
                c([-6.0, 12.6, -0.95], [2.9, 2.2, 3.1], "leather", stitch=False),
                c([-6.5, 14.4, -1.5], [3.6, 0.7, 4.2], "metal", pivot=[-4.6, 14.7, 0.6], rot=[0, 0, -20], **rusty),
                c([-6.9, 11.9, -1.3], [0.7, 2.8, 3.8], "metal", pivot=[-6.5, 13.4, 0.6], rot=[0, 0, -6], **rusty),
                c([-6.05, 12.9, -0.8], [2.8, 0.45, 2.9], "leather", r="rope", stitch=False),
                c([-5.5, 15.0, 0.1], [0.7, 1.4, 0.7], "metal", pivot=[-5.15, 15.0, 0.45], rot=[0, 0, 22], **rusty),
            ]
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 14.6, 0.4], arm, rot=[-30, 0, sx * -6])
        hand = [
            c(mirror_x([3.5, 5.2, -0.4], [2.1, 5.0, 2.1], sx), [2.1, 5.0, 2.1], "skin", zone="limb", anat=dict(part="fore", k=1.3),
              wraps=dict(y=(5.3, 7.4), ramp="wrap", dirt=("mud", "bottom")), grime=dirt, **limb),
            c(mirror_x([3.3, 3.4, -0.7], [2.5, 2.0, 2.6], sx), [2.5, 2.0, 2.6], "skin", anat=dict(part="fist", fingers=3, k=1.3),
              flush=dict(faces=("north", "down"), weight=0.22), grime=dirt, **limb),
            *[c([sx * 3.5 + (0.0 if sx > 0 else -0.5) + i * 0.8 * sx, 2.6, -0.7], [0.5, 1.0, 0.5], "horn", tip="-y", r="horn") for i in range(3)],
        ]
        if sx < 0:
            # the cleaver: a notched slab of iron on a short haft, held blade forward and down
            hand += [
                c([-5.1, 2.9, -3.6], [0.9, 0.9, 4.4], "wood", long="z"),
                c([-5.2, 0.0, -9.6], [0.6, 4.4, 6.4], "metal", r="blade", edge="down", nicks=True, skip=(), rust=0.35, blood=0.45, scratches=3),
                c([-5.25, 3.6, -9.2], [0.7, 0.8, 5.8], "metal", r="iron"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 10.0, 0.6], hand, rot=[-34, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 8.0, 0.4], [
            c(mirror_x([0.9, 4.2, -1.0], [2.6, 4.2, 2.8], sx), [2.6, 4.2, 2.8], "skin", zone="limb", anat=dict(part="thigh", k=1.3), **limb),
        ], rot=[-16, 0, sx * -8])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.2, 4.6, 0.4], [
            c(mirror_x([1.2, 0.9, -0.4], [2.1, 4.0, 2.1], sx), [2.1, 4.0, 2.1], "skin", zone="limb", anat=dict(part="shin", k=1.3),
              flush=dict(faces=("north",), high=0.75, weight=0.35), grime=dirt, **limb),
            c(mirror_x([0.9, 0.0, -3.4], [2.7, 1.1, 4.2], sx), [2.7, 1.1, 4.2], "skin", seed=6, mottle=0.1, blotch=0.2, grime=dirt,
              anat=dict(part="fist", fingers=3, k=1.1)),
            *[c([sx * (1.0 + i * 0.85) - (0.5 if sx < 0 else 0), 0.0, -4.0], [0.5, 0.7, 0.8], "horn", tip="-z", r="horn") for i in range(3)],
        ], rot=[22, 0, sx * 6])
    register(name, lambda aff: pal(aff, (255, 196, 58), {
        "skin": (116, 128, 70), "skin_d": (40, 56, 36), "skin_l": (180, 172, 108),
        "flush": (150, 84, 64), "mud": (62, 48, 34), "wrap": (166, 150, 114),
        "cloth": (104, 54, 40), "hair": (36, 30, 26), "horn": (86, 74, 56), "fur": (66, 54, 44),
        "blade": [(16, 12, 10), (52, 40, 34), (98, 84, 74), (156, 146, 136), (214, 210, 204)],
        "bone": (204, 190, 160), "paint": (150, 26, 24), "scar": (186, 150, 120), "sack": (122, 100, 70), "rust": (100, 60, 40),
        "scrap": [(14, 12, 12), (40, 36, 34), (72, 66, 60), (114, 106, 96), (168, 160, 148)],
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
    """A dead soldier that will not stay down: grey rotting flesh torn open over its ribs, a dented helm with a nasal
    guard low over sunken sockets with a cold light in them, the nose rotted away and one cheek torn through to the
    teeth, a slack jaw, rusted mail under a torn tabard, broken arrows still in it, a broken sword in one hand and
    only bones left of the other."""
    name = "grave_thrall"
    B = Body(name, 1600)
    box, col = B.box, B.col
    rot_ = dict(seed=21, mottle=0.22)
    dirt = dict(ramp="mud", height=3.6)
    rust = dict(ramp="rust", cut=0.73, k=1.3, tone=0.34)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 13, 0], [
        box(0, 12.6, 0, 7.2, 3.0, 4.0, "flesh", zone="back", **rot_),
        box(0, 9.4, 0, 7.8, 5.6, 4.6, "mail", r="mail", rust=0.7, skip=("up",)),
        # the tabard of a fallen order, its sword sigil faded to a dull gold
        B.c([-2.6, 3.4, -2.75], [5.2, 9.8, 0.4], "cloth", hem=2.4, seed=63, skip=("up", "down"), tears=2,
            facepaint=[dict(pts=[(0.0, 11.2), (0.0, 6.0)], w=0.55, ramp="gold", tone=0.42), dict(pts=[(-1.15, 10.1), (1.15, 10.1)], w=0.45, ramp="gold", tone=0.42),
                       dict(pts=[(-0.2, 11.5), (0.2, 11.5)], w=0.6, ramp="gold", tone=0.42)]),
        B.c([-2.8, 4.2, 2.35], [5.6, 9.0, 0.4], "cloth", hem=2.6, seed=64, skip=("up", "down")),
        box(0, 13.3, 0, 8.0, 1.0, 4.8, "leather", stitch=False),
        box(-1.6, 13.3, -2.45, 1.2, 1.3, 0.3, "trim", r="rust_trim"),
    ])
    ribs = []
    for i in range(4):
        y = 15.9 + i * 1.45
        for sx in (-1, 1):
            if i == 1 and sx > 0:
                continue   # one rib broken away
            ln = (2.1 - abs(i - 1.5) * 0.2) * (0.62 if (i == 0 and sx < 0) else 1.0)
            ribs.append(B.c(mirror_x([0.2, y - 0.22, -1.8], [ln, 0.45, 0.45], sx), [ln, 0.45, 0.45], "bone",
                            pivot=[sx * 0.2, y, -1.6], rot=[0, 0, sx * (10 + 5 * (i % 2))], seed=90 + i, cracks=False))
    B.bone("chest", "hips", [0, 13.5, 0], [
        # the torso, its front torn open from the breast down: the dark of the body cavity, ribs and a broken breastbone
        box(0, 18.6, 0.6, 8.0, 10.0, 3.4, "flesh", zone="back", dark_faces=("north",), **rot_, anat=dict(part="chest", kind="lean", k=1.0),
            stitches=[dict(pts=[(-1.6, 21.0), (0.2, 19.2), (1.5, 17.2)], face="west"), dict(pts=[(1.4, 22.0), (-0.6, 20.4)], face="east")]),
        *[box(sx * 3.0, 18.4, -1.45, 2.0, 9.4, 0.7, "flesh", seed=22 + (sx > 0), mottle=0.3) for sx in (-1, 1)],
        box(0, 22.3, -1.45, 8.0, 2.4, 0.7, "flesh", seed=24, mottle=0.3, anat=dict(part="chest", kind="muscle", k=0.6)),
        *ribs,
        box(0, 19.6, -1.95, 0.8, 4.4, 0.45, "bone", seed=95, cracks=False),
        box(0, 23.2, 0.2, 6.0, 0.8, 4.0, "flesh", **rot_),
        col(0, 0.6, 23.2, 25.4, 2.4, 2.2, "flesh", **rot_),
        # a pauldron of rusted plate over the left shoulder, two lames
        B.c([3.2, 23.0, -2.4], [3.6, 1.0, 4.8], "plate", pivot=[3.2, 23.5, 0], rot=[0, 0, 20], rivets=False, seed=51, spots=rust),
        B.c([6.0, 19.8, -2.3], [0.9, 3.2, 4.6], "plate", pivot=[6.0, 23.0, 0], rot=[0, 0, 10], rivets=False, seed=52, spots=rust),
        # broken arrows still in it, one through the back, one in the shoulder
        *limb(B, (-1.2, 18.4, 1.6), [(-2.4, 20.6, 6.0, 0.32)], "wood"),
        *limb(B, (-2.4, 20.6, 6.0), [(-2.7, 21.2, 7.3, 0.55, dict(m="cloth", r="fletch"))], "wood"),
        *limb(B, (2.2, 21.0, 1.4), [(3.0, 23.4, 4.6, 0.32)], "wood"),
        *limb(B, (-2.6, 21.6, -1.6), [(-3.0, 22.4, -3.8, 0.3)], "wood"),
    ], rot=[12, 0, 0])
    B.bone("head", "chest", [0, 23.6, 0], [
        # the face: sunken sockets with a cold light in each under a heavy brow, the nose rotted to a hole, cheekbones
        # pushing through, the upper teeth, and on its left the cheek torn away to the back teeth
        box(0, 28.50, -0.30, 5.0, 3.8, 5.0, "flesh", zone="back", **rot_),
        box(0, 26.00, 1.20, 4.4, 1.4, 2.0, "flesh", seed=30, mottle=0.3),
        box(0, 28.10, -2.90, 4.2, 1.6, 0.16, "flesh", seed=25, mottle=0.3, sockets=sockets(1.05, 28.15, (0.8, 0.62), slant=0.3, core=0.36),
            wrinkles=dict(eyes=[(1.05, 28.15), (-1.05, 28.15)], eye_r=(0.8, 0.62), k=1.4, cheeks=False)),
        box(0, 29.15, -3.15, 4.6, 0.6, 0.6, "flesh", seed=26, mottle=0.3, shadow_top=(28.85, 29.1, 0.2)),
        box(0, 27.00, -3.00, 1.2, 1.0, 0.4, "flesh", seed=27, holes=[dict(x=0.0, y=26.95, r=(0.34, 0.4))]),
        *[box(sx * 1.6, 27.1, -2.95, 1.4, 0.8, 0.6, "flesh", seed=28, mottle=0.3) for sx in (-1, 1)],
        box(-0.4, 26.05, -1.40, 3.4, 1.1, 3.0, "flesh", seed=29, mottle=0.3),
        box(-0.2, 25.30, -3.15, 3.6, 0.6, 0.3, "teeth", n=7, fangs=0, up=True, skip=("up", "down", "south")),
        box(2.25, 25.70, -1.70, 0.3, 0.8, 2.2, "teeth", n=4, fangs=0, up=True, skip=("up", "down")),
        box(2.25, 25.00, -1.70, 0.3, 0.6, 2.2, "teeth", n=4, fangs=0, up=False, skip=("up", "down")),
        # a dented helm, rounded, with a rusted rim, a nasal guard and one cheek guard left
        box(0, 31.05, -0.30, 6.0, 1.6, 5.6, "plate", rivets=False, seed=53, spots=rust),
        box(0, 31.25, -0.30, 5.4, 2.0, 6.2, "plate", rivets=False, seed=54, spots=rust),
        box(0, 31.9, -0.30, 4.4, 1.2, 4.8, "plate", rivets=False, seed=55),
        box(0, 32.3, -0.30, 0.6, 0.9, 5.0, "plate", rivets=False, seed=59),
        box(0, 30.15, -0.30, 6.5, 0.6, 6.7, "trim", r="rust_trim"),
        box(0, 28.90, -3.75, 0.8, 2.6, 0.4, "plate", rivets=False, seed=56),
        box(-2.85, 28.20, -1.30, 0.5, 2.8, 3.0, "plate", rivets=False, seed=57, spots=rust),
    ], rot=[8, 0, 12])
    B.bone("jaw", "head", [0, 26.0, 1.1], [
        box(0, 24.60, -1.30, 3.8, 1.2, 3.0, "flesh", **rot_),
        box(0, 25.45, -2.65, 3.4, 0.5, 0.3, "teeth", n=6, fangs=0, up=False, skip=("up", "down", "south")),
    ], rot=[18, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 5.0, 22.5, 0], [
            col(sx * 5.2, 0, 16.6, 23.0, 2.6, 2.6, "flesh", zone="limb", **rot_, anat=dict(part="upper", k=0.9)),
            # a sleeve of rusted mail to the elbow
            col(sx * 5.2, 0, 19.0, 23.0, 2.9, 2.9, "mail", r="mail", rust=0.7, skip=("up",)),
        ], rot=[-6 if sx < 0 else 4, 0, sx * -4])
        if sx < 0:
            fore = [
                col(-5.2, 0, 10.8, 16.8, 2.3, 2.3, "flesh", zone="limb", **rot_, anat=dict(part="fore", k=0.9),
                    wraps=dict(y=(11.0, 12.8), ramp="wrap", dirt=("mud", "bottom"))),
                box(-5.2, 10.0, -0.2, 2.5, 1.8, 2.7, "flesh", **rot_, anat=dict(part="fist")),
                # a broken, rusted sword, dragged point down
                box(-5.2, 10.1, -0.4, 0.9, 1.0, 2.2, "leather", stitch=False),
                box(-5.2, 10.1, -1.8, 3.2, 0.8, 0.8, "metal", r="rust_iron"),
                B.c([-5.55, 8.9, -11.6], [0.7, 2.4, 9.6], "metal", r="blade", edge="down", nicks=True, pivot=[-5.2, 10.1, -1.8], rot=[38, 0, 0],
                    rust=0.35, blood=0.3, scratches=3),
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
            col(sx * 2.0, 0, 6.4, 12.4, 3.0, 3.0, "flesh", zone="limb", **rot_, anat=dict(part="thigh", k=0.9))])
        shin = [col(sx * 2.0, 0, 1.0, 6.6, 2.6, 2.6, "flesh", zone="limb", **rot_, anat=dict(part="shin", k=0.9), grime=dirt),
                box(sx * 2.0, 0.8, -0.5, 3.0, 1.6, 4.0, "leather", stitch=False, grime=dirt),
                # a knee cop of dented plate
                box(sx * 2.0, 6.4, -1.4, 2.4, 1.8, 0.6, "plate", rivets=False, seed=58 + (sx > 0), spots=rust)]
        if sx < 0:
            shin.append(col(-2.0, 0, 2.2, 5.6, 2.9, 2.9, "cloth", r="wrap", seed=65))
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.0, 6.5, 0], shin)
    register(name, lambda aff: pal(aff, (150, 255, 225), {
        "rot": (112, 124, 120), "rot_d": (52, 60, 62), "rot_l": (164, 168, 150),
        "cloth": (92, 34, 34), "wrap": (118, 106, 88), "bone": (184, 170, 140), "gold": (150, 118, 58), "mud": (52, 46, 38),
        "mail": [(8, 8, 10), (34, 34, 40), (78, 78, 86), (132, 132, 140), (190, 190, 198)],
        "plate": (70, 66, 66), "trim": (112, 84, 60), "rust_trim": (96, 72, 54), "rust": (104, 70, 50), "rust_iron": (80, 66, 60),
        "fletch": (60, 56, 50), "wood": (88, 66, 44),
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
    """A skeleton archer: a bare skull with deep sockets and a point of cold light in each, a ribcage open to the air
    round its spine, a moss-dark cloak with its hood fallen back on the shoulders, a rusted pauldron, a quiver of
    red-fletched arrows, and a horn-tipped bow strung with light."""
    name = "bone_archer"
    B = Body(name, 1700)
    box, col = B.box, B.col
    cloak = dict(r="cloak", folds=1.5)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12.5, 0], [
        # the pelvis: a sacrum at the back, two wings of hip bone flaring forward round the sides, the pubic arch
        box(0, 12.3, 0.9, 1.8, 2.4, 1.2, "bone", seed=81),
        *[B.c(mirror_x([0.6, 11.0, 0.5], [2.4, 2.6, 0.6], sx), [2.4, 2.6, 0.6], "bone", pivot=[sx * 0.6, 12.3, 0.8], rot=[0, sx * 32, 0], seed=82)
          for sx in (-1, 1)],
        box(0, 10.6, -1.0, 2.2, 0.8, 0.8, "bone", seed=83),
        # a belt with a ragged loincloth hanging front and back, a buckle and a pouch
        box(0, 12.2, 0.1, 5.4, 0.9, 3.6, "leather", seed=72, stitch=False),
        box(-1.2, 12.2, -1.75, 1.0, 1.1, 0.3, "trim", r="clasp"),
        B.c([-1.8, 5.8, -1.95], [3.6, 6.0, 0.35], "cloth", hem=2.4, seed=66, skip=("up", "down"), **cloak),
        B.c([-2.2, 6.4, 1.9], [4.4, 5.4, 0.35], "cloth", hem=2.0, seed=68, skip=("up", "down"), **cloak),
        box(2.75, 11.0, -0.6, 1.2, 1.7, 1.5, "leather", seed=73),
    ])
    spine = []
    for i in range(7):
        y = 13.6 + i * 1.4
        spine += [box(0, y + 0.55, 1.0, 1.2, 1.0, 1.2, "bone", seed=84 + i, cracks=False),
                  B.c([-0.25, y + 0.15, 1.5], [0.5, 0.7, 1.0], "bone", pivot=[0, y + 0.5, 1.5], rot=[-25, 0, 0], seed=91, cracks=False)]
    ribs = []
    for i, (y, hw, true_) in enumerate(((21.4, 2.5, True), (20.2, 2.95, True), (19.0, 3.15, True), (17.8, 3.1, False), (16.7, 2.8, False))):
        for sx in (-1, 1):
            pts = [(sx * hw, y - 0.25, 0.55, 0.55), (sx * hw * 1.02, y - 0.65, -0.9, 0.55)]
            pts += [(sx * hw * 0.62, y - 1.0, -1.95, 0.55), (sx * 0.45, y - 1.15, -2.15, 0.5)] if true_ else [(sx * hw * 0.8, y - 0.95, -1.7, 0.5)]
            ribs += limb(B, (sx * 0.55, y, 1.0), pts, "bone", seed=100 + i, cracks=False, ends=0.3)
    B.bone("chest", "hips", [0, 13.0, 0], [
        # the spine, its ribs hooping forward to the breastbone (the lowest two float free), the collarbones
        *spine, *ribs,
        box(0, 19.5, -2.2, 0.9, 4.4, 0.5, "bone", seed=80, cracks=False),
        box(0, 21.45, -2.1, 1.6, 0.9, 0.6, "bone", seed=79, cracks=False),
        *[limb(B, (sx * 0.6, 21.9, -2.0), [(sx * 3.5, 22.6, -0.6, 0.5)], "bone", seed=78, cracks=False)[0] for sx in (-1, 1)],
        col(0, 0.6, 22.6, 25.6, 0.9, 0.9, "bone", seed=77, cracks=False),
        # the cloak, its hood fallen back and bunched behind the neck, fastened at the shoulders with bronze
        B.c([-4.4, 21.3, -0.6], [8.8, 1.8, 3.6], "cloth", hem=0.8, seed=69, skip=("down",), **cloak),
        box(0, 22.9, 2.3, 5.0, 2.4, 2.4, "cloth", seed=70, **cloak),
        B.c([-1.9, 20.2, 3.0], [3.8, 3.2, 1.0], "cloth", pivot=[0, 23.0, 3.0], rot=[18, 0, 0], hem=0.8, seed=71, **cloak),
        B.c([-4.4, 5.2, 2.7], [8.8, 16.4, 0.45], "cloth", hem=3.2, seed=74, skip=("up",), **cloak),
        *[box(sx * 3.3, 22.0, -0.75, 1.0, 1.0, 0.4, "trim", r="clasp") for sx in (-1, 1)],
        # a quiver over the cloak, red fletchings over the shoulder, its strap across the ribs with a buckle
        B.c([0.6, 14.8, 3.2], [2.6, 7.8, 2.6], "leather", pivot=[1.9, 18.7, 4.5], rot=[0, 0, -24], studs=True, stud_step=1.0, stud_r=0.25),
        *[B.c([0.8 + i * 0.6, 22.5, 3.5 + (i % 2) * 0.9], [0.5, 2.6, 0.5], "cloth", r="fletch", seed=70 + i, pivot=[1.9, 18.7, 4.5], rot=[0, 0, -24])
          for i in range(4)],
        B.c([-4.6, 18.55, -2.6], [9.2, 0.9, 0.35], "leather", pivot=[0, 19.0, -2.4], rot=[0, 0, 40], stitch=False),
        B.c([-0.5, 18.5, -2.72], [1.0, 1.0, 0.25], "trim", r="clasp", pivot=[0, 19.0, -2.4], rot=[0, 0, 40]),
    ], rot=[6, 0, 0])
    B.bone("head", "chest", [0, 24.0, 0], [
        # the skull: a rounded vault, brows arching over deep sockets with a point of cold light in each, cheekbones and
        # their arches, the nose's hole, the upper teeth
        box(0, 28.4, 0.6, 5.0, 3.4, 4.9, "bone", seed=88),
        box(0, 28.5, 0.6, 4.4, 4.0, 5.4, "bone", seed=87),
        box(0, 28.75, 0.8, 3.6, 4.1, 4.6, "bone", seed=86),
        box(0, 28.2, 3.2, 3.2, 2.6, 0.5, "bone", seed=79, cracks=False),
        *[B.c(mirror_x([0.0, 28.2, -2.75], [2.35, 0.75, 0.7], sx), [2.35, 0.75, 0.7], "bone", pivot=[0, 28.6, -2.4], rot=[0, 0, sx * 7], seed=85,
              cracks=False, shadow_top=(28.2, 28.5, 0.22)) for sx in (-1, 1)],
        box(0, 27.4, -2.18, 3.8, 1.6, 0.16, "bone", seed=84, cracks=False, sockets=sockets(0.95, 27.45, (0.72, 0.6), slant=0.3, core=0.38)),
        box(0, 26.65, -2.15, 0.9, 0.9, 0.4, "bone", seed=83, cracks=False, holes=[dict(x=0.0, y=26.55, r=(0.3, 0.36))]),
        *[box(sx * 1.7, 26.75, -2.05, 1.1, 0.7, 0.7, "bone", seed=82, cracks=False) for sx in (-1, 1)],
        *[box(sx * 2.45, 26.7, -0.6, 0.5, 0.6, 2.6, "bone", seed=81, cracks=False) for sx in (-1, 1)],
        box(0, 25.8, -1.7, 3.2, 1.0, 1.4, "bone", seed=80, cracks=False),
        box(0, 25.05, -2.15, 3.0, 0.6, 0.3, "teeth", n=8, fangs=0, up=True, skip=("up", "down", "south")),
    ])
    B.bone("jaw", "head", [0, 25.2, 1.0], [
        box(0, 23.9, -1.4, 2.8, 0.9, 1.4, "bone", seed=91, cracks=False),
        box(0, 24.6, -1.85, 2.6, 0.5, 0.3, "teeth", n=7, fangs=0, up=False, skip=("up", "down", "south")),
        *[box(sx * 1.5, 24.0, -0.2, 0.5, 0.9, 2.2, "bone", seed=90, cracks=False) for sx in (-1, 1)],
        *[box(sx * 1.55, 25.0, 0.8, 0.5, 1.8, 0.8, "bone", seed=89, cracks=False) for sx in (-1, 1)],
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        arm = [box(sx * 4.3, 22.5, 0, 1.5, 1.5, 1.5, "bone", seed=92, cracks=False),
               col(sx * 4.3, 0, 17.6, 22.0, 0.85, 0.85, "bone", seed=93),
               box(sx * 4.3, 17.4, 0, 1.4, 0.9, 1.2, "bone", seed=94, cracks=False)]
        if sx < 0:
            # a rusted iron pauldron capping the right shoulder, a lame hanging from it, strapped round the arm
            arm += [B.c(mirror_x([3.1, 23.0, -1.6], [2.9, 0.8, 3.2], sx), [2.9, 0.8, 3.2], "plate", r="rustiron", pivot=[sx * 3.1, 23.4, 0],
                        rot=[0, 0, sx * 16], seed=61, spots=dict(ramp="rust", cut=0.66, k=1.3, tone=0.42)),
                    B.c(mirror_x([5.3, 20.6, -1.5], [0.7, 2.6, 3.0], sx), [0.7, 2.6, 3.0], "plate", r="rustiron", pivot=[sx * 5.3, 23.0, 0],
                        rot=[0, 0, sx * 8], seed=62, spots=dict(ramp="rust", cut=0.66, k=1.3, tone=0.42)),
                    B.c(mirror_x([5.45, 19.2, -1.4], [0.6, 1.6, 2.8], sx), [0.6, 1.6, 2.8], "plate", r="rustiron", pivot=[sx * 5.45, 20.8, 0],
                        rot=[0, 0, sx * 12], seed=63, spots=dict(ramp="rust", cut=0.66, k=1.3, tone=0.42)),
                    col(sx * 4.3, 0, 19.4, 20.0, 1.3, 1.3, "leather", stitch=False)]
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 22.8, 0], arm, rot=[0, 0, sx * -6])
        fore = [col(sx * 4.1, 0.05, 11.6, 17.0, 0.5, 0.5, "bone", seed=95), col(sx * 4.75, -0.1, 11.6, 17.0, 0.55, 0.55, "bone", seed=96),
                box(sx * 4.45, 11.1, -0.05, 1.4, 0.9, 1.2, "bone", seed=97, cracks=False)]
        for i in range(4):
            fx_ = sx * 4.45 + (i - 1.5) * 0.33
            fore += limb(B, (fx_, 10.7, -0.2), [(fx_, 9.3, -0.55, 0.28), (fx_, 8.3, -0.35, 0.26)], "bone", seed=98 + i, cracks=False)
        fore += limb(B, (sx * 4.0, 10.9, -0.5), [(sx * 3.85, 9.9, -1.0, 0.3)], "bone", seed=102, cracks=False)
        if sx > 0:
            # an archer's bracer laced over the bones of the bow arm, and the bow in that hand: a grip, two limbs bending
            # back to the string, tips curled forward
            fore += [
                col(sx * 4.45, 0, 12.4, 15.2, 1.9, 1.6, "leather", stitch=True),
                box(4.6, 10.6, -1.2, 0.9, 2.6, 0.9, "leather", stitch=False),
                B.c([4.25, 11.8, -1.6], [0.7, 7.2, 0.8], "wood", pivot=[4.6, 11.8, -1.2], rot=[-14, 0, 0]),
                B.c([4.3, 18.6, -0.4], [0.6, 2.6, 0.6], "horn", r="horn", tip="+y", pivot=[4.6, 18.8, -0.1], rot=[22, 0, 0]),
                B.c([4.25, 2.2, -1.6], [0.7, 7.2, 0.8], "wood", pivot=[4.6, 9.4, -1.2], rot=[14, 0, 0]),
                B.c([4.3, 0.2, -0.4], [0.6, 2.6, 0.6], "horn", r="horn", tip="-y", pivot=[4.6, 2.6, -0.1], rot=[-22, 0, 0]),
                B.c([4.5, 2.4, 0.4], [0.2, 16.6, 0.2], "glow"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.4, 17.2, 0], fore, rot=[-8 if sx > 0 else 0, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.0, 12.0, 0], [
            box(sx * 1.9, 11.4, 0, 1.4, 1.2, 1.4, "bone", seed=103, cracks=False),
            col(sx * 2.0, 0, 7.0, 11.0, 0.9, 0.9, "bone", seed=98),
            box(sx * 2.0, 6.75, 0, 1.6, 0.9, 1.4, "bone", seed=99, cracks=False)])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.0, 6.4, 0], [
            box(sx * 2.0, 6.0, -0.8, 0.8, 0.9, 0.5, "bone", seed=104, cracks=False),
            col(sx * 2.0, -0.1, 1.4, 6.1, 0.8, 0.8, "bone", seed=105), col(sx * 2.6, 0.25, 1.4, 5.8, 0.45, 0.45, "bone", seed=106),
            box(sx * 2.0, 1.2, 0.1, 1.3, 0.8, 1.3, "bone", seed=107, cracks=False),
            box(sx * 2.0, 0.45, -0.6, 1.6, 0.9, 2.0, "bone", seed=100, cracks=False),
            *[B.bar(sx * 2.0 + (i - 1) * 0.5, 0.25, -2.6, -1.5, 0.36, 0.4, "bone", seed=108 + i, cracks=False) for i in range(3)],
            col(sx * 2.0, 0.05, 2.6, 4.4, 1.3, 1.3, "cloth", r="wrap", seed=71),
        ])
    # the arrow it nocks: only seen while it shoots
    B.bone("arrow", "right_hand", [-4.5, 10.8, 0], [
        B.bar(-4.5, 10.8, -11.0, 1.0, 0.4, 0.4, "wood", long="z"),
        B.bar(-4.5, 10.8, -12.4, -11.0, 0.8, 0.8, "metal", r="steel"),
        B.bar(-4.5, 11.1, 0.0, 1.6, 0.2, 0.9, "cloth", r="fletch"),
    ])
    register(name, lambda aff: pal(aff, (130, 190, 255), {
        "bone": (198, 186, 158), "cloak": (56, 68, 52), "wrap": (110, 98, 84), "fletch": (150, 40, 38), "horn": (70, 60, 50),
        "rustiron": [(8, 8, 10), (30, 29, 31), (62, 58, 58), (104, 94, 86), (156, 138, 118)],
        "clasp": [(30, 20, 10), (78, 56, 30), (132, 100, 56), (184, 150, 92), (226, 202, 150)],
    }, tinted=("cloak", "cloak_d", "cloak_l", "bone", "bone_d", "bone_l")), density=5.5)
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
    """A tall, thin hunter of the deep cold: a body of living shadow under faceted plates of ice, a wedge of an ice
    mask with a hard brow and two slits of light, long icicles sweeping back from its crown, a frozen heart glowing in
    a gap of its breastplate, shards along its spine and shoulders, and arms that end in long blades of ice."""
    name = "rime_stalker"
    B = Body(name, 1800)
    box, col = B.box, B.col
    ice = dict(seed=112, glint=False)
    shadow = dict(veins=1.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 15, 0], [
        box(0, 15.2, 0, 5.0, 3.0, 3.2, "void", **shadow),
        # a belt of ice plates, rags of frozen cloth hanging front and back
        *[B.c(mirror_x([0.0, 15.8, -2.0], [3.0, 1.4, 0.8], sx), [3.0, 1.4, 0.8], "ice", pivot=[0, 16.5, -1.6], rot=[0, -sx * 18, 0], **ice) for sx in (-1, 1)],
        box(0, 16.5, 0.9, 5.4, 1.2, 1.8, "ice", **ice),
        B.c([-2.2, 4.0, -2.15], [4.4, 11.6, 0.4], "cloth", hem=3.2, seed=72, skip=("up", "down"), grime=dict(ramp="ice", height=8.0, tone=0.62, weight=0.7)),
        B.c([-2.6, 5.0, 1.7], [5.2, 10.6, 0.4], "cloth", hem=3.4, seed=73, skip=("up", "down"), grime=dict(ramp="ice", height=8.0, tone=0.62, weight=0.7)),
    ])
    shards = []
    for sx in (-1, 1):
        # shoulders: a faceted block of ice with long shards sweeping up and back from it
        shards += [B.c(mirror_x([2.8, 24.4, -1.6], [3.0, 2.0, 3.6], sx), [3.0, 2.0, 3.6], "ice", pivot=[sx * 2.8, 25.4, 0.2], rot=[0, 0, sx * -16], **ice),
                   B.c(mirror_x([3.8, 26.2, -0.9], [1.4, 5.0, 1.4], sx), [1.4, 5.0, 1.4], "crystal", pivot=[sx * 4.5, 26.2, -0.2], rot=[-34, 0, sx * -24], skip=("down",)),
                   B.c(mirror_x([2.9, 26.0, 0.5], [1.0, 3.6, 1.0], sx), [1.0, 3.6, 1.0], "crystal", pivot=[sx * 3.4, 26.0, 1.0], rot=[-48, 0, sx * -14], skip=("down",)),
                   B.c(mirror_x([4.6, 25.6, 0.6], [0.8, 2.6, 0.8], sx), [0.8, 2.6, 0.8], "crystal", pivot=[sx * 5.0, 25.6, 1.0], rot=[-58, 0, sx * -40], skip=("down",))]
    spine = [B.c([-0.45, 26.4 - i * 2.6, 2.0], [0.9, 3.0 - i * 0.35, 0.9], "crystal", pivot=[0, 26.4 - i * 2.6, 2.4], rot=[-55, 0, 0], skip=("down",))
             for i in range(4)]
    B.bone("chest", "hips", [0, 16.5, 0], [
        box(0, 21.8, 0.2, 5.6, 10.4, 3.4, "void", **shadow),
        # the breastplate: two faceted plates meeting at a ridge, a gap under them where the frozen heart glows
        *[B.c(mirror_x([0.0, 21.4, -2.45], [3.3, 4.6, 0.9], sx), [3.3, 4.6, 0.9], "ice", pivot=[0, 23.7, -2.0], rot=[-6, -sx * 22, 0], **ice) for sx in (-1, 1)],
        box(0, 20.3, -1.9, 1.3, 1.3, 1.3, "glow", pivot=[0, 20.3, -1.9], rot=[0, 0, 45]),
        box(0, 20.3, -1.9, 2.0, 2.0, 0.5, "crystal", pivot=[0, 20.3, -1.9], rot=[0, 0, 45]),
        *[B.c(mirror_x([0.0, 18.4 - j * 1.35, -2.1], [2.3 - j * 0.3, 1.0, 0.7], sx), [2.3 - j * 0.3, 1.0, 0.7], "ice", pivot=[0, 18.9 - j * 1.35, -1.8],
              rot=[0, -sx * 16, 0], **ice) for sx in (-1, 1) for j in range(2)],
        box(0, 22.2, 2.0, 5.0, 6.6, 0.8, "ice", **ice),
        *shards, *spine,
    ], rot=[10, 0, 0])
    crest = []
    for dx, ln, rx, rz, z0 in ((0.0, 6.0, -62, 0, 2.4), (1.1, 5.6, -50, -30, 2.0), (-1.1, 5.6, -50, 30, 2.0), (1.9, 4.2, -40, -52, 1.2),
                               (-1.9, 4.2, -40, 52, 1.2), (0.0, 4.4, -82, 0, 3.4)):
        crest.append(B.c([dx - 0.5, 31.8, z0 - 0.5], [1.0, ln, 1.0], "crystal", pivot=[dx, 31.8, z0], rot=[rx, 0, rz], skip=("down",)))
    B.bone("head", "chest", [0, 27.0, 0], [
        col(0, 0.2, 26.8, 28.6, 2.0, 2.0, "void", **shadow),
        # a long skull of ice sweeping back and up into a crest of icicles
        box(0, 30.5, 0.6, 4.2, 4.0, 4.6, "ice", **ice),
        B.c([-1.8, 30.2, 1.4], [3.6, 3.0, 3.8], "ice", pivot=[0, 32.2, 1.4], rot=[-24, 0, 0], **ice),
        # the mask: narrow, a slit of light slanting up across each side, cheekbones swept back, a hard V of a brow, a
        # narrow chin
        box(0, 30.0, -2.0, 3.4, 3.8, 1.0, "ice", **ice,
            lines=[dict(pts=[(sx * 0.35, 30.4), (sx * 1.45, 30.95)], w=0.4, faces=("north",), halo=0.7, bright=1.0) for sx in (-1, 1)]),
        *[B.c(mirror_x([1.4, 28.4, -2.45], [1.5, 3.2, 0.7], sx), [1.5, 3.2, 0.7], "ice", pivot=[sx * 1.4, 30.0, -2.45], rot=[0, -sx * 42, 0], **ice)
          for sx in (-1, 1)],
        *[B.c(mirror_x([0.0, 31.25, -2.85], [2.1, 0.7, 0.8], sx), [2.1, 0.7, 0.8], "ice", pivot=[0, 31.25, -2.45], rot=[0, 0, -sx * 16], **ice)
          for sx in (-1, 1)],
        box(0, 27.9, -1.7, 1.9, 1.4, 1.4, "ice", **ice),
        *crest,
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 4.2, 25.6, 0.2], [
            col(sx * 4.6, 0.2, 18.0, 25.8, 1.6, 1.6, "void", **shadow),
            col(sx * 4.6, 0.2, 20.4, 23.6, 2.1, 2.1, "ice", **ice),
        ], rot=[-4, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 18.2, 0.2], [
            col(sx * 4.6, 0.2, 11.0, 18.2, 1.5, 1.5, "void", **shadow),
            # a vambrace of ice cut with facets, a spur at the elbow
            B.c([sx * 4.6 - 1.25, 12.0, 0.2 - 1.25], [2.5, 5.0, 2.5], "ice", pivot=[sx * 4.6, 14.5, 0.2], rot=[0, 45, 0], **ice),
            B.c([sx * 4.6 - 0.4, 17.4, 0.9], [0.8, 3.0, 0.8], "crystal", pivot=[sx * 4.6, 17.4, 1.3], rot=[-70, 0, 0], skip=("down",)),
            box(sx * 4.6, 10.4, 0.0, 1.6, 1.6, 1.8, "void", **shadow),
            # the blade: from the wrist, past the hand, nearly to the knee
            B.c([sx * 4.6 - 0.35, -0.8, -1.2], [0.7, 12.0, 2.0], "crystal", pivot=[sx * 4.6, 11.0, 0.0], rot=[-8, 0, 0], skip=("up",)),
            B.c([sx * 4.6 - 0.25, -3.2, -0.9], [0.5, 2.6, 1.2], "crystal", pivot=[sx * 4.6, 11.0, 0.0], rot=[-8, 0, 0], skip=("up",)),
        ], rot=[-14, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 1.8, 14.4, 0], [
            col(sx * 1.9, 0, 7.4, 14.6, 1.8, 1.8, "void", **shadow),
            B.c([sx * 1.9 - 1.2, 8.8, -1.4], [2.4, 4.6, 1.2], "ice", pivot=[sx * 1.9, 11.0, -0.8], rot=[0, -sx * 10, 0], **ice),
        ], rot=[-6, 0, 0])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 1.9, 7.6, 0], [
            col(sx * 1.9, 0, 0.8, 7.8, 1.5, 1.5, "void", **shadow),
            # a greave cut with facets, a spike at the knee, a foot of three ice claws and a heel spur
            B.c([sx * 1.9 - 1.15, 1.8, -1.15], [2.3, 4.6, 2.3], "ice", pivot=[sx * 1.9, 4.1, 0.0], rot=[0, 45, 0], **ice),
            B.c([sx * 1.9 - 0.4, 6.8, -1.8], [0.8, 2.4, 0.8], "crystal", pivot=[sx * 1.9, 6.8, -1.4], rot=[50, 0, 0], skip=("down",)),
            box(sx * 1.9, 0.5, -0.5, 1.8, 1.0, 2.2, "ice", **ice),
            *[B.c([sx * 1.9 + (i - 1) * 0.6 - 0.25, 0.0, -3.2], [0.5, 0.6, 1.7], "crystal", pivot=[sx * 1.9 + (i - 1) * 0.6, 0.3, -1.6],
                  rot=[0, (i - 1) * -14, 0]) for i in range(3)],
            B.c([sx * 1.9 - 0.25, 0.1, 0.6], [0.5, 0.6, 1.4], "crystal", pivot=[sx * 1.9, 0.4, 0.6], rot=[-20, 0, 0]),
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

def hatchet(B, a, b, side):
    """A throwing hatchet: a haft from a to b (in a plane of constant z) and an iron head at b, its blade to the side
    given (+1: towards +x before the haft is turned onto its line)."""
    rz = math.degrees(math.atan2(b[0] - a[0], b[1] - a[1]))
    ln = math.hypot(b[0] - a[0], b[1] - a[1])
    x, y, z = a
    out = [B.c([x - 0.3, y, z - 0.3], [0.6, ln + 0.4, 0.6], "wood", long="y", pivot=[x, y, z], rot=[0, 0, rz])]
    hx, hy = x, y + ln - 1.5
    o = [hx + 0.2, hy, z - 0.22] if side > 0 else [hx - 2.4, hy, z - 0.22]
    out += [B.c(o, [2.2, 1.7, 0.44], "metal", r="blade", fuller=False, nicks=True, rust=0.4, scratches=1,
                pivot=[x, y, z], rot=[0, 0, rz]),
            B.c([hx - 0.45, hy + 0.1, z - 0.4], [0.9, 1.5, 0.8], "metal", r="iron", pivot=[x, y, z], rot=[0, 0, rz])]
    return out


def tuskbrute():
    """A hulking brute: a back like a hill, a small head sunk between its shoulders under a heavy scowling brow, a
    riveted iron patch over one eye, a heavy underslung jaw with two great curved tusks, a black band of war paint and
    a red stripe down the face, a crest of black hair and a braid, a pauldron of three spiked iron lames, a skull
    hung from its bandolier, a fur kilt, and a notched axe the size of a man."""
    name = "tuskbrute"
    B = Body(name, 1900)
    box, col = B.box, B.col
    sk = dict(seed=13, mottle=0.12, fat=0.08, blotch=0.22)
    dirt = dict(ramp="mud", height=4.5)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 13, 0], [
        box(0, 13.6, 0, 10.4, 4.0, 6.8, "skin", zone="back", **sk),
        box(0, 10.6, 0, 11.4, 5.0, 7.6, "fur", r="fur", zone="back", seed=24),
        box(0, 13.4, 0, 11.6, 1.4, 7.8, "leather", stitch=True),
        # a skull for a buckle, a cold light in its sockets
        box(0, 13.4, -4.0, 2.6, 2.4, 1.0, "bone", seed=101, cracks=False,
            sockets=sockets(0.55, 13.75, (0.4, 0.32), slant=0.25, core=0.35),
            holes=[dict(x=0.0, y=13.05, r=(0.16, 0.22))],
            cuts=[dict(pts=[(-0.7, 12.55), (0.7, 12.55)], w=0.18, faces=("north",)), *[dict(pts=[(x, 12.3), (x, 12.8)], w=0.12, faces=("north",)) for x in (-0.45, -0.15, 0.15, 0.45)]]),
    ])
    B.bone("chest", "hips", [0, 15, 0], [
        box(0, 22.0, -0.4, 14.0, 13.0, 9.0, "skin", zone="front", bib_w=0.28, **sk, scars=2,
            anat=dict(part="chest", kind="muscle", k=1.5),
            claws=[dict(ramp="paint", start=(5.4, 27.4), end=(-2.2, 18.6), n=3, gap=1.25, width=0.62, seed=5)],
            tiger=dict(faces=("south",), ramp="warpaint", period=3.4, width=0.3, lean=0.75, broken=0.75, wobble=0.7, tone=0.35)),
        box(0, 28.6, 0.6, 15.6, 3.4, 7.6, "skin", zone="back", **sk, scars=2),
        # slabs of chest muscle standing out from the ribcage
        *[box(sx * 3.5, 25.1, -5.15, 6.6, 4.4, 0.5, "skin", zone="front", bib_w=0.4, bib_cx=sx * 3.5, **sk, anat=dict(part="slab", k=1.4))
          for sx in (-1, 1)],
        # a bandolier from the right shoulder down to the left hip
        box(0, 23.0, -5.55, 16.4, 1.3, 0.3, "leather", pivot=[0, 23, -5.5], rot=[0, 0, -40], stitch=False, studs=True, stud_step=1.1, stud_r=0.22),
        # a pauldron on the left shoulder: three lames of iron, each lapping over the one below like tiles on a roof,
        # a row of spikes along the top one
        box(8.9, 26.4, 0.4, 5.0, 2.4, 9.2, "plate", r="plate", pivot=[6.4, 28.4, 0], rot=[0, 0, 30], engrave="trim", seed=51),
        box(8.4, 28.0, 0.4, 5.6, 2.6, 9.6, "plate", r="plate", pivot=[6.4, 28.4, 0], rot=[0, 0, 22], engrave="trim", seed=52),
        box(7.7, 29.8, 0.4, 6.2, 2.4, 10.0, "plate", r="plate", pivot=[6.4, 28.4, 0], rot=[0, 0, 14], engrave="trim", seed=53),
        *[c_ for z_, h_ in ((-3.0, 3.0), (0.4, 3.8), (3.6, 2.8)) for c_ in limb(B, (7.6, 30.5, z_), [
            (7.9, 30.5 + h_ * 0.42, z_ + 0.25, 1.3), (8.3, 30.5 + h_ * 0.76, z_ + 0.55, 0.8), (8.6, 30.5 + h_, z_ + 0.8, 0.4, dict(tip="+y"))],
            "horn", r="iron_spike", skip=("down",))],
        # the bandolier round the back, two throwing hatchets crossed under it
        box(0, 23.0, 4.25, 16.4, 1.3, 0.3, "leather", pivot=[0, 23, 4.25], rot=[0, 0, -40], stitch=False, studs=True, stud_step=1.1, stud_r=0.22),
        *[c_ for sx in (-1, 1) for c_ in hatchet(B, (-sx * 2.6, 19.4, 4.75), (sx * 1.9, 27.2, 4.75), sx)],
        # a skull hung from the bandolier by a thong
        B.c([-4.75, 17.2, -6.0], [0.3, 1.9, 0.3], "leather", stitch=False),
        box(-4.6, 16.0, -6.15, 2.2, 2.2, 1.9, "bone", seed=102, cracks=True,
            sockets=[dict(x=-5.1, y=16.2, r=(0.36, 0.3), slant=-0.25, core=0.35), dict(x=-4.1, y=16.2, r=(0.36, 0.3), slant=0.25, core=0.35)],
            holes=[dict(x=-4.6, y=15.55, r=(0.14, 0.2))]),
        box(-4.6, 14.6, -6.0, 1.6, 0.8, 1.5, "bone", seed=103, cracks=False,
            cuts=[dict(pts=[(x, 14.6), (x, 15.0)], w=0.12, faces=("north",)) for x in (-5.0, -4.6, -4.2)]),
    ], rot=[14, 0, 0])
    # the face: a black band of war paint across the eyes, a red stripe down the brow and nose
    paint = [dict(pts=[(-3.6, 31.1), (-0.5, 30.85)], w=1.9), dict(pts=[(0.5, 30.85), (3.6, 31.1)], w=1.9),
             dict(pts=[(-0.3, 33.0), (0.0, 29.6)], w=0.55, ramp="paint", tone=0.45)]
    head = [
        box(0, 30.5, -5.05, 7.0, 5.0, 6.5, "skin", zone="back", split=(0.7, -1.0), **sk, facepaint=paint,
            eyes=eyes(1.6, 30.9, (0.72, 0.44), slant=0.42, pupil="dot"),
            wrinkles=dict(eyes=[(1.6, 30.9), (-1.6, 30.9)], eye_r=(0.72, 0.44), nose=(0, 29.9), mouth=(-2.9, 2.9, 28.4), k=2.4,
                          frown=(0.5, 31.6, 32.6), cheeks=True),
            shadow_top=(30.4, 31.8, 0.16)),
        # a dome to the skull, a heavy brow in a scowl over the eyes
        box(0, 33.3, -5.0, 6.0, 1.4, 5.6, "skin", zone="back", **sk),
        *[B.c(mirror_x([0.0, 31.6, -9.3], [3.9, 1.35, 2.0], sx), [3.9, 1.35, 2.0], "skin", r="skin_d", pivot=[sx * 0.2, 32.0, -8.3],
              rot=[0, 0, -sx * 12], seed=14, mottle=0.1, blotch=0.2, anat=dict(part="slab", k=1.0), skip=("south",)) for sx in (-1, 1)],
        # a broad flat snout with a ring through it
        box(0, 30.1, -8.85, 2.5, 1.8, 1.6, "skin", seed=15, mottle=0.1, blotch=0.2, flush=dict(low=0.7, weight=0.3),
            holes=[dict(x=0.55, y=29.65, r=(0.3, 0.26)), dict(x=-0.55, y=29.65, r=(0.3, 0.26))]),
        box(0, 29.0, -9.75, 1.2, 1.0, 0.3, "trim", seed=16),
        # a riveted patch of iron over the right eye, its strap round the head
        box(-1.65, 30.95, -8.45, 2.3, 1.8, 0.35, "plate", r="plate", seed=54, rivets=False, dents=False,
            cuts=[dict(pts=[(-2.55, 31.6), (-2.5, 31.6)], w=0.3, faces=("north",), ramp="trim", tone=0.75),
                  dict(pts=[(-0.75, 30.3), (-0.8, 30.3)], w=0.3, faces=("north",), ramp="trim", tone=0.75)]),
        box(0, 31.25, -4.55, 7.15, 0.55, 7.5, "leather", stitch=False, skip=("north",)),
        # a crest of black hair from brow to nape, a braid down the back bound with rings of bone
        B.c([-0.75, 33.4, -7.2], [1.5, 2.0, 6.0], "hair", r="hair", seed=132, ragged=1.0, pivot=[0, 34.4, -4.2], rot=[0, 0, 180], skip=("down",)),
        B.c([-0.6, 25.4, -2.2], [1.2, 8.0, 1.0], "hair", r="hair", seed=133, ends=True, ragged=0.5, pivot=[0, 33.4, -1.7], rot=[-18, 0, 0], skip=("up",)),
        *[B.c([-0.75, y_, -2.35], [1.5, 0.5, 1.3], "bone", seed=134, cracks=False, pivot=[0, 33.4, -1.7], rot=[-18, 0, 0]) for y_ in (30.2, 28.0)],
    ]
    for sx in (-1, 1):
        ear = [sx * 3.6, 31, -4.0]
        head += [B.c(mirror_x([3.4, 30.2, -4.4], [2.2, 1.8, 0.8], sx), [2.2, 1.8, 0.8], "skin", pivot=ear, rot=[0, sx * -30, sx * 18], zone="limb",
                     ear=dict(weight=0.45), **sk),
                 B.c(mirror_x([5.5, 30.6, -4.3], [1.4, 1.0, 0.6], sx), [1.4, 1.0, 0.6], "skin", pivot=ear, rot=[0, sx * -30, sx * 18], zone="limb", **sk)]
        if sx > 0:
            head += [B.c([x_, 29.6, -4.3], [0.35, 0.8, 0.35], "trim", pivot=ear, rot=[0, -30, 18]) for x_ in (4.3, 5.1)]
    B.bone("head", "chest", [0, 28.0, -3.0], head, rot=[-10, 0, 0])
    # the jaw: wider than the skull and slung forward of it, the lower teeth standing over the lip, jowls at its sides,
    # two great tusks curving up and out from its corners
    tusks = []
    for sx in (-1, 1):
        tusks += limb(B, (sx * 2.85, 27.9, -8.75), [(sx * 3.2, 30.0, -9.3, 1.5), (sx * 4.05, 31.6, -9.15, 1.0, dict(tip="+y"))],
                      "horn", r="tusk", skip=("down",))
    B.bone("jaw", "head", [0, 28.6, -3.0], [
        box(0, 27.25, -5.9, 7.6, 2.7, 6.6, "skin", zone="back", split=(0.85, 0.25), **sk,
            cuts=[dict(pts=[(-0.25, 28.5), (0.0, 26.0)], w=0.55, faces=("north",), ramp="paint", tone=0.45)]),
        *[box(sx * 3.75, 27.3, -6.0, 1.4, 2.2, 3.6, "skin", zone="limb", **sk) for sx in (-1, 1)],
        box(0, 28.75, -9.05, 4.8, 0.8, 0.4, "teeth", n=5, fangs=0, up=False, skip=("up", "down", "south")),
        *tusks,
    ], rot=[4, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 8.6, 27.5, 0], [
            # the shoulder's cap of muscle, broader than the arm under it
            box(sx * 9.8, 27.2, 0, 5.9, 3.4, 6.1, "skin", zone="limb", **sk, anat=dict(part="slab", k=1.3)),
            col(sx * 9.8, 0, 18.4, 28.4, 5.2, 5.4, "skin", zone="limb", **sk, anat=dict(part="upper", k=1.6),
                rings=dict(ramp="paint", ys=[(22.6, 23.5), (24.2, 24.7)]) if sx < 0 else dict(ramp="warpaint", ys=[(22.8, 24.2)])),
        ], rot=[-6, 0, sx * -6])
        hand = [
            col(sx * 10.0, 0, 10.6, 19.4, 5.6, 5.8, "skin", zone="limb", **sk, veins=True, anat=dict(part="fore", k=1.6)),
            col(sx * 10.0, 0, 12.2, 17.2, 6.2, 6.4, "leather", stitch=True, studs=True, stud_step=1.7, stud_r=0.45),
            box(sx * 10.0, 9.0, -0.2, 5.8, 4.0, 6.0, "skin", **sk, anat=dict(part="fist", k=1.5), flush=dict(faces=("north", "down"), weight=0.25)),
        ]
        if sx < 0:
            # a great crude axe: a bone-wrapped haft and a notched iron blade, its head by the knee
            hand += [
                col(-10.2, -0.6, 1.2, 11.6, 1.6, 1.6, "wood", seed=122),
                col(-10.2, -0.6, 7.0, 9.6, 2.0, 2.0, "leather", stitch=False),
                col(-10.2, -0.6, 0.2, 1.4, 2.2, 2.2, "trim"),
                B.c([-10.65, 1.8, -8.6], [0.9, 7.6, 7.6], "metal", r="blade", edge="front", nicks=True, long="y", rust=0.4, blood=0.5, scratches=4),
                B.c([-10.75, 3.2, -2.2], [1.1, 4.8, 1.8], "metal", r="iron"),
                B.c([-10.6, 4.6, 0.4], [0.8, 2.2, 2.4], "horn", r="iron_spike", tip="+z"),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 9.9, 19.0, 0], hand)
        B.bone(f"{side}_leg", "hips", [sx * 3.4, 12.0, 0], [
            col(sx * 3.6, 0, 6.4, 12.6, 5.0, 5.4, "skin", zone="limb", **sk, anat=dict(part="thigh", k=1.5))], rot=[0, 0, sx * -3])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 3.6, 6.6, 0], [
            col(sx * 3.7, 0, 1.4, 6.8, 4.4, 4.6, "skin", zone="limb", **sk, anat=dict(part="shin", k=1.4), grime=dirt),
            col(sx * 3.7, 0, 2.2, 5.6, 5.0, 5.2, "fur", r="fur", seed=25, grime=dirt),
            box(sx * 3.7, 0.8, -0.9, 5.0, 1.6, 6.6, "skin", seed=16, mottle=0.1, blotch=0.2, grime=dirt, anat=dict(part="fist", fingers=4, k=1.2)),
        ], rot=[0, 0, sx * 3])
    register(name, lambda aff: pal(aff, (255, 96, 48), {
        "skin": (88, 102, 74), "skin_d": (34, 42, 36), "skin_l": (150, 146, 110),
        "flush": (138, 80, 66), "mud": (58, 46, 36), "warpaint": [(4, 4, 6), (12, 11, 14), (26, 23, 28), (46, 42, 48), (74, 70, 78)],
        "fur": (90, 70, 54), "hair": (30, 26, 24), "tusk": (210, 196, 160), "paint": (168, 28, 24),
        "plate": (70, 70, 78), "trim": (128, 108, 78), "iron_spike": (96, 96, 104), "bone": (204, 190, 160),
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
    """An old shaman of the tusked brutes: hunched under a fur mantle and a robe of hides, the skull of a horned beast
    for a mask (a long muzzle with its teeth, cheekbones, a heavy brow, eyes burning in its sockets and running down it
    like tears), a pelt falling from it to the shoulders, its own tusks showing under it, beads and teeth, and a staff
    crowned with a smaller skull, two horns cradling a crystal over it and charms hanging from it."""
    name = "tusk_shaman"
    B = Body(name, 2000)
    box, col = B.box, B.col
    sk = dict(seed=17, mottle=0.12, fat=0.08, blotch=0.2)
    dirt = dict(ramp="mud", height=3.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12, 0], [
        box(0, 12.6, 0, 8.6, 3.0, 6.0, "cloth", r="hide", zone="back", seed=74),
        B.c([-4.6, 2.2, -3.2], [9.2, 9.6, 6.6], "cloth", r="hide", hem=2.6, seed=75, skip=("up", "down"), band=(4.4, 5.6), band_r="trim",
            zigzag=[dict(ramp="ochre", y=7.4, amp=0.42, period=1.3, width=0.34), dict(ramp="ochre", y=9.6, amp=0.3, period=0.9, width=0.22, tone=0.4)],
            grime=dirt, patches=1, patch_r="hide_d"),
        box(0, 13.8, 0, 9.4, 1.2, 6.8, "leather", stitch=False),
        *[box(-3.0 + i * 1.5, 12.4, -3.6, 0.6, 1.6, 0.6, "bone", seed=102 + i) for i in range(5)],
    ])
    B.bone("chest", "hips", [0, 14, 0], [
        box(0, 19.4, 0.2, 9.0, 10.0, 6.0, "cloth", r="hide", zone="front", seed=76,
            zigzag=[dict(ramp="ochre", y=16.2, amp=0.36, period=1.1, width=0.3)],
            claws=[dict(ramp="ochre", start=(-3.2, 23.0), end=(-1.2, 17.6), n=3, gap=0.75, width=0.32, tone=0.55, seed=9)]),
        box(0, 24.0, 0.8, 12.0, 3.4, 8.4, "fur", r="fur", zone="back", seed=26),
        box(0, 21.6, 3.6, 10.4, 6.0, 2.0, "fur", r="fur", seed=27),
        # strings of teeth and beads
        *[box(-2.6 + i * 1.3, 21.4 - abs(i - 2) * 0.5, -2.95, 0.6, 1.3, 0.5, "bone" if i % 2 else "glow", seed=106 + i) for i in range(5)],
    ], rot=[22, 0, 0])
    horn = []
    for sx in (-1, 1):
        horn += curl(B, [sx * 2.4, 29.6, -6.4], sx, [(60, 2.2, 1.9), (95, 2.0, 1.7), (140, 1.8, 1.5), (185, 1.6, 1.25), (230, 1.3, 1.0)],
                     "horn", r="horn", tip="+y", z_step=0.5)
    tears = dict(faces=("north",), halo=0.5, bright=0.8)
    mask = [
        # the beast's skull: a broad brow plate with the sockets in it, a long muzzle, cheekbones, a heavy brow ridge
        box(0, 29.0, -6.95, 5.8, 2.8, 1.0, "bone", seed=108,
            sockets=sockets(1.4, 28.25, (0.95, 0.62), slant=0.4, core=0.34),
            cuts=[dict(pts=[(-0.2, 30.4), (0.5, 29.5), (0.1, 28.9)], w=0.26, faces=("north",))],
            facepaint=[dict(pts=[(-2.9, 29.95), (2.9, 29.95)], w=0.5, ramp="paint", tone=0.42)],
            lines=[dict(pts=[(0.0, 29.45), (0.0, 29.6)], w=0.22, **tears)]),
        # the muzzle, long and sloping down, the nasal hollow at its tip, a row of worn teeth under it
        B.c([-1.25, 24.7, -10.4], [2.5, 2.5, 3.8], "bone", seed=110, pivot=[0, 27.2, -6.6], rot=[12, 0, 0],
            holes=[dict(x=0.0, y=26.35, r=(0.55, 0.36))],
            cuts=[dict(pts=[(0.0, 26.75), (0.0, 27.2)], w=0.16, faces=("north",)), dict(pts=[(0.0, -10.4), (0.0, -6.6)], w=0.14, faces=("up",))]),
        B.c([-0.7, 27.0, -10.1], [1.4, 0.55, 3.4], "bone", seed=111, cracks=False, pivot=[0, 27.2, -6.6], rot=[12, 0, 0]),
        B.c([-1.05, 24.15, -10.25], [2.1, 0.6, 0.35], "teeth", n=4, fangs=0, up=True, skip=("up", "down", "south"), pivot=[0, 27.2, -6.6], rot=[12, 0, 0]),
        *[box(sx * 2.25, 27.1, -7.25, 1.5, 1.0, 1.3, "bone", seed=112, cracks=False,
              lines=[dict(pts=[(sx * 1.85, 27.5), (sx * 1.95, 26.8)], w=0.24, **tears)]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.3, 29.0, -7.85], [2.8, 0.7, 1.0], sx), [2.8, 0.7, 1.0], "bone", seed=113, cracks=False, pivot=[sx * 0.3, 29.35, -7.35],
              rot=[0, 0, -sx * 10]) for sx in (-1, 1)],
    ]
    B.bone("head", "chest", [0, 24.2, -2.0], [
        box(0, 27.4, -3.8, 5.6, 5.4, 5.4, "skin", zone="back", **sk),
        *mask,
        box(0, 30.6, -4.6, 6.4, 1.2, 4.8, "fur", r="fur", seed=28),
        # the beast's pelt falling from the skull over the sides of the head and down the back
        *[box(sx * 3.15, 27.4, -3.6, 0.8, 6.2, 5.6, "mane", r="fur", seed=30 + (sx > 0), len=1.6, width=0.8, ragged=1.0, skip=("up",))
          for sx in (-1, 1)],
        box(0, 26.6, -0.6, 6.6, 7.6, 0.8, "mane", r="fur", seed=32, len=1.8, width=0.9, ragged=1.2, skip=("up",)),
        *horn,
        # its own tusks, curving up out of the jaw under the mask
        *[c_ for sx in (-1, 1) for c_ in limb(B, (sx * 2.2, 23.4, -6.6), [(sx * 2.45, 25.0, -7.1, 1.0), (sx * 2.95, 26.1, -7.25, 0.65, dict(tip="+y"))],
                                                  "horn", r="tusk", skip=("down",))],
    ], rot=[-14, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 5.4, 23.0, 0.4], [
            col(sx * 5.8, 0.4, 16.6, 23.4, 2.6, 2.8, "cloth", r="hide", seed=77),
        ], rot=[-10, 0, sx * -6])
        hand = [col(sx * 5.8, 0.2, 10.6, 16.8, 2.0, 2.0, "skin", zone="limb", **sk, anat=dict(part="fore", k=1.1), veins=True),
                col(sx * 5.8, 0.2, 12.0, 13.2, 2.4, 2.4, "bone", seed=113, cracks=False),
                box(sx * 5.8, 9.8, 0, 2.2, 1.8, 2.4, "skin", **sk, anat=dict(part="fist", k=1.2), flush=dict(faces=("north", "down"), weight=0.25))]
        if sx > 0:
            # the staff: a long shaft wrapped under its head, a small beast skull on it looking ahead, two horns
            # rising from the skull to cradle a crystal, charms of bone and feathers hanging from it
            hand += [
                col(6.0, -1.6, -1.0, 30.0, 1.0, 1.0, "wood", seed=123),
                col(6.0, -1.6, 27.2, 29.6, 1.3, 1.3, "leather", stitch=False, wraps=dict(y=(27.2, 29.6), ramp="wrap")),
                box(6.0, 31.0, -1.2, 2.4, 2.2, 2.2, "bone", seed=109,
                    sockets=[dict(x=6.6, y=31.3, r=(0.42, 0.32), slant=0.3, core=0.4), dict(x=5.4, y=31.3, r=(0.42, 0.32), slant=-0.3, core=0.4)]),
                box(6.0, 30.45, -2.95, 1.4, 1.3, 1.6, "bone", seed=114, cracks=False, holes=[dict(x=6.25, y=30.75, r=(0.16, 0.26)), dict(x=5.75, y=30.75, r=(0.16, 0.26))]),
                box(6.0, 29.6, -2.75, 1.2, 0.45, 1.3, "bone", seed=115, cracks=False),
                *[c_ for sx in (-1, 1) for c_ in limb(B, (6.0 + sx * 0.9, 31.9, -1.0), [(6.0 + sx * 1.35, 33.3, -1.15, 0.6), (6.0 + sx * 1.0, 34.6, -1.25, 0.42),
                                                                                       (6.0 + sx * 0.45, 35.4, -1.3, 0.3, dict(tip="+y"))], "horn", r="horn", skip=("down",))],
                B.c([5.6, 32.6, -1.65], [0.8, 2.6, 0.8], "crystal", pivot=[6.0, 33.9, -1.25], rot=[0, 45, 0]),
                B.c([5.1, 27.2, -1.4], [0.3, 3.4, 1.2], "cloth", r="feather", seed=78, hem=0.8, pivot=[6.0, 30.0, -1.6], rot=[0, 0, 18]),
                B.c([6.6, 27.6, -1.8], [0.3, 3.0, 1.2], "cloth", r="feather", seed=79, hem=0.8, pivot=[6.0, 30.0, -1.6], rot=[0, 0, -14]),
                col(4.75, -0.6, 27.6, 30.2, 0.18, 0.18, "leather", r="rope", stitch=False),
                box(4.75, 27.3, -0.6, 0.55, 0.8, 0.55, "bone", seed=116, cracks=False),
            ]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.8, 17.0, 0.4], hand, rot=[-26 if sx > 0 else -10, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 2.2, 11.0, 0], [
            col(sx * 2.2, 0, 4.6, 11.4, 3.0, 3.0, "skin", zone="limb", **sk, anat=dict(part="thigh"))])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 2.2, 4.8, 0], [
            col(sx * 2.2, 0, 1.0, 5.0, 2.6, 2.6, "skin", zone="limb", **sk, anat=dict(part="shin"), grime=dirt,
                wraps=dict(y=(1.2, 4.6), ramp="wrap", dirt=("mud", "bottom"))),
            col(sx * 2.2, 0, 1.6, 4.4, 3.0, 3.0, "fur", r="fur", seed=29, grime=dirt),
            box(sx * 2.2, 0.6, -0.8, 3.0, 1.2, 4.0, "skin", seed=18, mottle=0.1, blotch=0.2, grime=dirt, anat=dict(part="fist", fingers=3)),
        ])
    register(name, lambda aff: pal(aff, (90, 240, 200), {
        "skin": (96, 110, 84), "skin_d": (44, 52, 42), "skin_l": (150, 148, 116),
        "flush": (136, 82, 66), "mud": (58, 46, 36), "wrap": (150, 132, 100), "ochre": (196, 150, 70), "paint": (160, 34, 30),
        "hide": (118, 90, 64), "hide_d": (74, 54, 40), "fur": (96, 84, 72), "horn": (60, 52, 44), "tusk": (204, 190, 156),
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
    tapering to a blunt snout, round glowing eyes on its sides under bony ridges, gill slits, a lure of light on a
    stalk from its brow, a heavy underslung jaw that hangs open on a mouth of needle fangs, barbels from its chin,
    fins on its head, back and arms, glowing spots along its flanks, a shell on one shoulder, coral growing out of
    the other, a necklace of shark's teeth, and a three-pronged spear of coral and bone."""
    name = "deepkin"
    B = Body(name, 2100)
    box, col = B.box, B.col
    sc = dict(size=0.85, seed=33, scale_k=0.75)
    hs = dict(size=0.7, seed=34, scale_k=0.6)
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 12.5, 0.5], [
        box(0, 12.8, 0.6, 6.6, 3.2, 4.4, "scales", zone="back", **sc),
        B.c([-2.4, 5.6, -1.8], [4.8, 7.4, 0.4], "cloth", r="kelp", hem=2.4, seed=80, skip=("up", "down")),
        B.c([-2.6, 6.2, 2.7], [5.2, 6.8, 0.4], "cloth", r="kelp", hem=2.2, seed=81, skip=("up", "down")),
    ])
    bars = dict(tiger=dict(faces=("east", "west", "up", "south"), ky=0.15, lean=0.0, kz=1.0, period=2.0, width=0.3, broken=0.7, tone=0.28,
                           ramp="scale_d", back_half=False))
    B.bone("chest", "hips", [0, 14.0, 0.6], [
        box(0, 19.6, 0.4, 7.8, 10.2, 5.4, "scales", zone="front", bib_w=0.3, **sc, **bars,
            lights=dict(rows=[(17.2, -2.0, 2.4, 4), (19.4, -1.6, 2.0, 3)])),
        B.c([-0.2, 15.0, 3.0], [0.4, 9.6, 3.4], "skin", r="fin", rays=dict(y=15.0, z=3.0, n=8), skip=("up", "down", "north", "south")),
        *[box(sx * 4.4, 23.2, 0.6, 2.2, 1.6, 4.8, "scales", **sc) for sx in (-1, 1)],
        # a necklace of shark's teeth on a cord
        box(0, 23.4, -2.5, 6.4, 0.4, 0.3, "leather", r="rope", stitch=False),
        *[B.c([-2.55 + i * 0.85 - 0.25, 21.8 + abs(i - 3) * 0.25, -2.75], [0.5, 1.5 - abs(i - 3) * 0.25, 0.3], "teeth", r="ivory", n=1, fangs=1,
              up=True, skip=("up", "down", "south")) for i in range(7)],
        # coral grown out of the right shoulder
        *[c_ for (x0, z0, pts) in ((-4.0, 1.6, [(-4.6, 26.0, 1.9, 0.7), (-5.4, 27.2, 2.4, 0.5), (-5.6, 28.2, 2.2, 0.35, dict(tip="+y"))]),
                                   (-3.2, 2.4, [(-3.0, 25.8, 3.0, 0.6), (-2.4, 26.9, 3.6, 0.42, dict(tip="+y"))]),
                                   (-4.6, 0.4, [(-5.4, 25.2, 0.3, 0.5), (-6.0, 26.0, 0.0, 0.35, dict(tip="+y"))]))
          for c_ in limb(B, (x0, 23.8, z0), pts, "horn", r="coral", skip=("down",))],
    ], rot=[24, 0, 0])
    gills = [dict(pts=[(-2.0 + k * 0.75, 26.8), (-1.75 + k * 0.75, 29.6)], w=0.28, faces=("west", "east"), tone=0.06) for k in range(3)]
    head = [
        # the skull: broad behind the eyes, tapering to a blunt snout
        B.c([-3.3, 26.4, -7.4], [6.6, 4.0, 7.8], "scales", zone="back", split=(0.6, 0.3), **hs,
            lights=dict(rows=[(27.1, -7.0, -2.8, 4)], r=0.24),
            eyes=[dict(x=-5.3, y=28.75, r=(1.3, 1.3), face="west", pupil="dot", lid=0.5),
                  dict(x=-5.3, y=28.75, r=(1.3, 1.3), face="east", pupil="dot", lid=0.5)],
            cuts=gills),
        B.c([-2.5, 26.6, -10.0], [5.0, 2.8, 2.8], "scales", zone="back", split=(0.6, 0.3), **hs,
            holes=[dict(x=0.9, y=28.7, r=(0.24, 0.18)), dict(x=-0.9, y=28.7, r=(0.24, 0.18))]),
        # bony ridges over the eyes
        *[B.c(mirror_x([2.95, 29.9, -7.0], [0.7, 0.7, 3.4], sx), [0.7, 0.7, 3.4], "scales", r="scale_d", size=0.5, seed=35) for sx in (-1, 1)],
        # needle teeth hanging from the snout, at its front and down its sides
        B.c([-2.35, 25.7, -10.1], [4.7, 1.0, 0.4], "teeth", n=7, fangs=1, up=True, skip=("up", "down", "south")),
        *[B.c(mirror_x([2.2, 25.8, -9.9], [0.4, 0.9, 5.8], sx), [0.4, 0.9, 5.8], "teeth", n=6, fangs=1, up=True,
              skip=("up", "down", "north", "south", "east" if sx > 0 else "west")) for sx in (-1, 1)],
        # a crest fin from the brow down the nape, small fins behind the gills
        B.c([-0.2, 30.2, -6.8], [0.4, 2.8, 7.4], "skin", r="fin", rays=dict(y=30.2, z=-6.8, n=7, dir=-1), skip=("up", "down", "north", "south")),
        *[B.c(mirror_x([3.2, 26.6, -1.4], [0.4, 3.0, 2.6], sx), [0.4, 3.0, 2.6], "skin", r="fin", rays=dict(y=26.6, z=-1.4, n=5),
              pivot=[sx * 3.3, 28.0, -1.4], rot=[0, sx * -28, 0], skip=("up", "down", "north", "south")) for sx in (-1, 1)],
        # the lure: a stalk curling forward from the brow, a bulb of light at its end
        *limb(B, (0.0, 30.2, -6.4), [(0.0, 32.0, -7.4, 0.4), (0.0, 33.0, -9.0, 0.35), (0.0, 32.7, -10.4, 0.3)], "skin", r="fin", seed=22),
        box(0, 32.1, -10.6, 1.0, 1.2, 1.0, "glow"),
    ]
    B.bone("head", "chest", [0, 24.4, -1.2], head, rot=[-22, 0, 0])
    # the jaw: heavy and underslung, past the snout, hanging open on a mouth of needle fangs; barbels from the chin
    B.bone("jaw", "head", [0, 26.4, -1.6], [
        B.c([-3.4, 24.0, -10.9], [6.8, 2.2, 9.6], "scales", zone="back", split=(0.85, 0.2), **hs),
        B.c([-2.8, 25.6, -10.4], [5.6, 0.8, 8.6], "mouth"),
        B.c([-3.0, 26.15, -10.95], [6.0, 1.5, 0.4], "teeth", n=7, fangs=2, up=False, skip=("up", "down", "south")),
        *[B.c(mirror_x([2.95, 26.15, -10.4], [0.4, 1.1, 6.4], sx), [0.4, 1.1, 6.4], "teeth", n=6, fangs=1, up=False,
              skip=("up", "down", "north", "south", "east" if sx > 0 else "west")) for sx in (-1, 1)],
        *[c_ for sx in (-1, 1) for c_ in limb(B, (sx * 1.3, 24.1, -10.2), [(sx * 1.5, 22.6, -10.1, 0.45), (sx * 1.25, 21.2, -9.7, 0.32)],
                                                  "skin", r="fin", seed=24)],
    ], rot=[12, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        arm = [col(sx * 4.9, 0.6, 16.0, 23.2, 2.4, 2.6, "scales", zone="limb", **sc)]
        if sx > 0:
            # a great ribbed shell lashed over the left shoulder, barnacles crusting it
            arm += [B.c([3.6, 22.6, -1.9], [3.6, 1.3, 5.0], "horn", r="shell", pivot=[4.4, 23.2, 0.6], rot=[0, 0, 24],
                        tiger=dict(faces=("up", "north", "south", "west"), ramp="shell_d", ky=0.0, lean=0.0, kz=1.0, cx=0.0, period=0.9,
                                   width=0.28, tone=0.3, back_half=False),
                        spotted=dict(ramp="shell_l", cell=0.9, radius=0.26, density=0.25, faces=("up", "west"))),
                    B.c([5.6, 20.8, -1.6], [0.9, 2.4, 4.4], "horn", r="shell", pivot=[4.4, 23.2, 0.6], rot=[0, 0, 12],
                        tiger=dict(faces=("west", "north", "south"), ramp="shell_d", ky=0.0, lean=0.0, kz=1.0, period=0.9, width=0.28,
                                   tone=0.3, back_half=False)),
                    B.c([4.4, 20.4, -0.9], [2.6, 0.4, 3.0], "leather", r="rope", stitch=False)]
        B.bone(f"{side}_arm", "chest", [sx * 4.4, 22.6, 0.6], arm, rot=[-26, 0, sx * -6])
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
        "bone": (214, 204, 180), "coral": (190, 80, 74), "shell": (60, 66, 84), "shell_d": (26, 28, 40), "shell_l": (176, 190, 200),
    }), density=5.0)
    B.write(1.6, 2.2, 1.0)

    A = {}
    L = 2.6
    A["idle"] = anim(L, True, {
        "chest": rot(lambda t: [2 * wave(t, L), 0, 0]),
        "head": rot(lambda t: [3 * wave(t, L / 2), 12 * wave(t, L * 2), 6 * wave(t, L, 0.3)]),
        "jaw": rot(lambda t: [4 * wave(t, L / 2, 0.2), 0, 0]),
        "left_arm": rot(lambda t: [4 * wave(t, L), 0, 0]), "right_arm": rot(lambda t: [-3 * wave(t, L), 0, 0]),
    }, 0.1)
    L = 1.0
    A["walk"] = anim(L, True, {
        **biped_walk(L, shins=("right_shin", "left_shin"), swing=26, arm_swing=14, knee=24, bob=0.6, body="hips"),
        "hips": {"rotation": lambda t: [0, 0, 6 * wave(t, L)], "position": lambda t: [0, -0.6 * abs(wave(t, L)), 0]},
        "chest": rot(lambda t: [6, 0, -4 * wave(t, L)]),
        "head": rot(lambda t: [-4, 4 * wave(t, L), 0]),
        "jaw": rot(lambda t: [3 * wave(t, L / 2), 0, 0]),
    }, 0.05)
    # thrust (0.8 s): the spear drawn back, then driven forward at 0.4 s
    L = 0.8
    A["thrust"] = anim(L, False, {
        "right_arm": rot(lambda t: [-30 * hold(t, 0, 0.3, 0.32, 0.42) - 40 * snap(t, 0.32, 0.42, 0.5, 0.8), 0, 0]),
        "right_hand": {"position": lambda t: [0, 0, 3 * hold(t, 0, 0.3, 0.32, 0.4) - 7 * snap(t, 0.32, 0.42, 0.5, 0.8)]},
        "chest": rot(lambda t: [-10 * hold(t, 0, 0.3, 0.32, 0.4) + 16 * snap(t, 0.32, 0.44, 0.5, 0.8), 20 * hold(t, 0, 0.3, 0.32, 0.42), 0]),
        "hips": {"position": lambda t: [0, 0, -3 * snap(t, 0.32, 0.44, 0.5, 0.8)]},
        "head": rot(lambda t: [-10 * sin01(t, 0.2, 0.7), 0, 0]),
        "jaw": rot(lambda t: [20 * sin01(t, 0.22, 0.7), 0, 0]),
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "root": {"rotation": lambda t: [-80 * ease_in(window(t, 0.15, 0.7)), 0, 0], "position": lambda t: [0, -1 * window(t, 0.15, 0.7), 4 * window(t, 0.15, 0.7)]},
        "head": rot(lambda t: [-20 * window(t, 0.1, 0.5) + 6 * math.sin(t * 40) * window(t, 0.7, 0.8) * (1 - window(t, 0.9, 1.2)), 0, 0]),
        "right_arm": rot(lambda t: [-60 * window(t, 0.2, 0.7), 0, 30 * window(t, 0.2, 0.7)]),
        "left_arm": rot(lambda t: [-60 * window(t, 0.2, 0.7), 0, -30 * window(t, 0.2, 0.7)]),
        "jaw": rot(lambda t: [22 * window(t, 0.1, 0.5), 0, 0]),
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
    # thinner towards the trailing edge, where the light comes through it
    p.shade(0.1 * CS.sstep(depth * 0.45, depth, qq))
    if w.get("fingers"):
        # the finger bones under the skin, fanning from the wrist to the points of the trailing edge, each a dark
        # ridge with a lit side
        dmin = np.full(f.X.shape, 99.0)
        for i in range(1, n + 1):
            kk = i / n
            tip_p = span * kk
            tip_q = depth * (1 - w.get("taper", 0.0) * kk)
            d, _ = CS.seg_dist(pp, qq, 0.0, 0.0, tip_p, tip_q)
            dmin = np.minimum(dmin, d)
        p.over(w.get("finger_r", "armbone"), 0.32, dmin < f.texel * 0.9)
        p.shade(0.08, (dmin >= f.texel * 0.9) & (dmin < f.texel * 1.8))
    p.cut((qq < edge) & (pp > -0.1) & (pp < span + 0.1))


def wing_pair(B, side, sx, parent, shoulder, inner, outer, mat="skin", spec=None, bones_r="bone", fingers=3, arm=(1.0, 0.8), finger_bones=False,
              bone_mat="bone", rest=None):
    """A bat wing on side sx: an inner part from the shoulder to the elbow and an outer part folding at the elbow.
    inner/outer: (span, depth) in units; the membranes lie flat (in x-z) behind a bony leading edge. arm: the
    thickness of the arm and of the hand along that edge; finger_bones paints the fingers fanning through the outer
    membrane."""
    spec = dict(spec or {})
    x0, y0, z0 = shoulder
    span1, depth1 = inner
    span2, depth2 = outer
    a1, a2 = arm
    th = 0.3
    B.bone(f"{side}_wing", parent, [x0, y0, z0], [
        B.c(mirror_x([abs(x0), y0 - th / 2, z0], [span1, th, depth1], sx), [span1, th, depth1], mat, r="membrane",
            wing=dict(root=abs(x0), lead=z0, depth=depth1, span=span1, n=2), fx=membrane_fx, skip=("north", "south", "east", "west"), marks=0.5, **spec),
        B.c(mirror_x([abs(x0), y0 - a1 / 2, z0 - a1 / 2], [span1, a1, a1], sx), [span1, a1, a1], bone_mat, r=bones_r),
    ], rot=rest)
    ex = abs(x0) + span1
    outer_cubes = [
        B.c(mirror_x([ex, y0 - th / 2, z0], [span2, th, depth2], sx), [span2, th, depth2], mat, r="membrane",
            wing=dict(root=ex, lead=z0, depth=depth2, span=span2, n=fingers, taper=0.7, fingers=finger_bones, finger_r=bones_r),
            fx=membrane_fx, skip=("north", "south", "east", "west"), marks=0.5, **spec),
        B.c(mirror_x([ex, y0 - a2 / 2, z0 - a2 / 2], [span2 * 0.95, a2, a2], sx), [span2 * 0.95, a2, a2], bone_mat, r=bones_r),
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
    """A great bat of the dark gates: a wingspan wider than a man is tall, a shaggy ruff round its neck, a wrinkled pug
    face under a spear of a nose-leaf, tall ribbed ears, small burning red eyes and long fangs, fur dark above and
    dusky below, wings of thin dark skin over long finger bones."""
    name = "duskwing"
    B = Body(name, 2200)
    box, col = B.box, B.col
    fur = dict(seed=30, len=1.6, width=1.0, lock_k=0.6)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 7, 0], [
        box(0, 7.0, 0.6, 5.0, 4.6, 7.2, "fur", r="fur", zone="back", **fur),
        # a shaggy ruff round the neck and over the chest
        box(0, 6.8, -2.8, 6.4, 5.6, 2.8, "mane", r="fur_l", seed=31, len=1.6, width=0.8, ragged=0.9),
        box(0, 6.2, 4.6, 3.2, 2.6, 2.4, "fur", r="fur", **fur),
    ])
    face = dict(eyes=[dict(x=1.6, y=-1.6, r=(0.5, 0.3), face="north")], nose=(0.0, 7.6), mouth=(-1.0, 1.0, 6.6), k=1.6, frown=(0.4, 8.8, 9.7))
    B.bone("head", "body", [0, 7.8, -3.4], [
        box(0, 8.2, -5.6, 4.6, 4.0, 3.8, "fur", r="fur", zone="back", split=(0.55, 0.2), **fur,
            eyes=eyes(1.15, 8.9, (0.7, 0.42), slant=0.5, pupil=None, lid=1.5),
            wrinkles=dict(eyes=[(1.15, 8.9), (-1.15, 8.9)], eye_r=(0.7, 0.42), k=1.6, frown=(0.35, 9.3, 10.0), cheeks=False)),
        # the pug snout, wrinkled, and the spear of the nose-leaf standing up from it
        box(0, 7.0, -8.0, 2.6, 2.2, 1.4, "skin", zone="back", seed=40, mottle=0.1, fat=0.08, blotch=0.2,
            holes=[dict(x=0.5, y=7.3, r=(0.32, 0.26)), dict(x=-0.5, y=7.3, r=(0.32, 0.26))],
            grooves=[dict(faces=("north",), pts=[(-0.95, 0.75), (-0.5, 0.3), (-0.75, 0.0)], w=0.8, k=0.14, lip=0.05),
                     dict(faces=("north",), pts=[(0.95, 0.75), (0.5, 0.3), (0.75, 0.0)], w=0.8, k=0.14, lip=0.05)]),
        B.c([-0.7, 8.0, -8.5], [1.4, 2.4, 0.5], "skin", r="skin_d", seed=41, skip=("south",), pivot=[0, 8.0, -8.3], rot=[-8, 0, 0],
            grooves=[dict(faces=("north",), pts=[(0.0, 0.05), (0.0, 0.9)], w=0.8, k=0.18, lip=0.0),
                     dict(faces=("north",), pts=[(-0.8, 0.35), (0.0, 0.6), (0.8, 0.35)], w=0.7, k=0.12, lip=0.05)]),
        B.c([-0.4, 10.4, -8.45], [0.8, 0.9, 0.4], "skin", r="skin_d", seed=41, skip=("south", "down"), pivot=[0, 8.0, -8.3], rot=[-8, 0, 0]),
        box(0, 6.1, -8.2, 2.2, 0.6, 0.4, "teeth", n=2, fangs=1, up=True, small=0.3, skip=("up", "down", "south")),
        # tall ears, broad at the root and pointed, the hollow ribbed with folds across it
        *[c for sx in (-1, 1) for c in (
            B.c(mirror_x([0.6, 9.6, -5.4], [2.3, 3.2, 0.6], sx), [2.3, 3.2, 0.6], "skin", r="ear", pivot=[sx * 1.6, 9.8, -5.1], rot=[-10, 0, sx * 16],
                skip=("down",), zone="limb", seed=42, mottle=0.1, fat=0.06, blotch=0.15,
                flush=dict(faces=("north",), ramp="ear_l", tone=0.45, weight=0.5),
                grooves=[dict(faces=("north",), pts=[(-0.75, 0.2 + 0.26 * i), (0.65, 0.32 + 0.26 * i)], w=1.3, k=0.3, lip=0.12) for i in range(3)]),
            B.c(mirror_x([1.0, 12.3, -5.35], [1.4, 3.1, 0.5], sx), [1.4, 3.1, 0.5], "skin", r="ear", pivot=[sx * 1.6, 9.8, -5.1], rot=[-10, 0, sx * 16],
                zone="limb", seed=43, mottle=0.1, fat=0.06, blotch=0.15,
                flush=dict(faces=("north",), ramp="ear_l", tone=0.42, weight=0.4, high=0.7),
                grooves=[dict(faces=("north",), pts=[(-0.75, 0.15 + 0.3 * i), (0.65, 0.3 + 0.3 * i)], w=1.3, k=0.28, lip=0.1) for i in range(2)]))],
    ])
    B.bone("jaw", "head", [0, 6.6, -6.0], [
        box(0, 5.9, -7.4, 2.4, 0.9, 2.4, "skin", seed=43, mottle=0.1, fat=0.08, blotch=0.2, paint_faces=[dict(faces=("up",), ramp="mouth", tone=0.32)]),
        box(0, 6.5, -8.45, 2.0, 0.5, 0.3, "teeth", n=2, fangs=1, up=False, skip=("up", "down", "south")),
    ], rot=[10, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        wing_pair(B, side, sx, "body", [sx * 2.4, 8.4, -2.0], (7.6, 7.6), (10.4, 7.0), bones_r="armbone", arm=(0.8, 0.6), fingers=4,
                  finger_bones=True, bone_mat="skin")
        B.bone(f"{side}_foot", "body", [sx * 1.6, 5.0, 3.8], [
            col(sx * 1.6, 3.8, 2.6, 5.2, 0.9, 0.9, "skin", seed=44),
            *[B.c([sx * 1.6 - 0.75 + i * 0.6, 2.0, 3.0], [0.4, 0.8, 0.9], "horn", r="claw", tip="-y") for i in range(3)],
        ], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 56, 56), {
        "fur": (52, 44, 52), "fur_d": (22, 18, 26), "fur_l": (112, 94, 92), "skin": (70, 50, 54), "ear": (76, 52, 56), "ear_l": (140, 90, 92),
        "membrane": (58, 36, 44), "bone": (104, 88, 82), "armbone": (44, 32, 36), "claw": (30, 26, 26),
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

def limb(B, base, pts, mat, **kw):
    """A chain of thin cubes from base through pts [(x, y, z, thickness, {spec})], each turned to run from one point
    to the next (antennae, an insect's legs, whiskers): the cube stands on its start point and is tilted onto the
    line."""
    out = []
    x0, y0, z0 = base
    for pt in pts:
        x1, y1, z1, w = pt[:4]
        sp = {**kw, **(pt[4] if len(pt) > 4 else {})}
        m = sp.pop("m", mat)
        dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
        ln = math.sqrt(dx * dx + dy * dy + dz * dz)
        rx = -math.degrees(math.asin(max(-1.0, min(1.0, dz / ln))))
        rz = math.degrees(math.atan2(dx, dy))
        out.append(B.c([x0 - w / 2, y0, z0 - w / 2], [w, ln, w], m, pivot=[x0, y0, z0], rot=[rx, 0, rz], **sp))
        x0, y0, z0 = x1, y1, z1
    return out


def aff_mix(aff, c, k):
    """A colour pulled towards the affinity's light: a variant's markings in its own colour (the plain one keeps c)."""
    return tuple(c) if aff == "plain" else CS.mix(c, tuple(v * 0.86 for v in CS.AFF_GLOW[aff]), k)


def insect_wing(f, p, s):
    """An insect's wing seen from above or below: a smoky membrane, lighter in its middle and darker at the root and
    the tip, a thick dark vein down the leading edge with a dark spot two thirds out (the stigma), veins running out
    along it and across it into cells, a dark rim, and its outline cut round: a straight leading edge, a curved
    trailing edge, a rounded tip. s['wing'] = dict(span='x' (sideways) or 'z' (folded back), lead='-z'/'+z' for a
    sideways wing, 'out'/'in' for a folded one, sheen=ramp for a sheen of another colour)."""
    if f.face not in ("up", "down"):
        p.cut(np.zeros(f.X.shape, bool))
        return
    w = s.get("wing", {})
    r = s.get("r", "wingskin")
    out = 1 if f.cx > 0 else -1
    if w.get("span", "x") == "x":
        L, C = f.s[0], f.s[2]
        u = (f.X - f.o[0]) / L if out > 0 else (f.o[0] + L - f.X) / L
        v = (f.Z - f.o[2]) / C if w.get("lead", "-z") == "-z" else (f.o[2] + C - f.Z) / C
    else:
        L, C = f.s[2], f.s[0]
        u = (f.Z - f.o[2]) / L
        xo = (f.X - f.o[0]) / C
        v = 1 - xo if (w.get("lead", "out") == "out") == (out > 0) else xo
    u = np.clip(u, 0, 1)
    tex = f.texel
    le = 0.03 + 0.55 * np.clip((u - 0.78) / 0.22, 0, 1) ** 2
    te = np.where(u < 0.62, 0.4 + 0.58 * np.sin(np.clip(u / 0.62, 0, 1) * math.pi / 2) ** 0.7, 0.98 - 0.4 * ((u - 0.62) / 0.38) ** 2)
    keep = (v >= le - 0.03) & (v <= te)
    n = CS.fbm3(f.X * 0.9, f.Y * 0.9, f.Z * 0.9, s.get("seed", 45), 2)
    tone = 0.5 + 0.1 * np.sin(u * math.pi) - 0.2 * CS.sstep(0.14, 0.0, u) + (n - 0.5) * 0.1
    tone = tone - w.get("tip_dark", 0.14) * CS.sstep(0.72, 0.95, u)
    p.fill(r, tone)
    if w.get("sheen"):
        sh = np.exp(-((u + v * 0.5 - 0.72) / 0.14) ** 2) * 0.4
        p.over(w["sheen"], 0.62, sh)
    vw = tex * 0.55
    vein = (u < 0.8) & ((v - le) * C < tex * 1.3)
    for v0, u0, u1, bend in ((0.2, 0.04, 0.62, -0.06), (0.4, 0.04, 0.9, -0.1), (0.6, 0.06, 0.82, -0.02), (0.8, 0.08, 0.5, 0.04)):
        vc = v0 + bend * u
        vein |= (np.abs((v - vc) * C) < vw) & (u > u0) & (u < u1)
    for u0, va, vb in ((0.24, 0.2, 0.62), (0.4, 0.38, 0.8), (0.5, 0.18, 0.4), (0.62, 0.37, 0.6), (0.72, 0.56, 0.8), (0.33, 0.6, 0.8)):
        vein |= (np.abs((u - u0) * L) < vw) & (v > va) & (v < vb)
    p.over(r, 0.12, vein)
    p.over(r, 0.08, (u > 0.56) & (u < 0.7) & ((v - le) * C < tex * 3.0))
    p.over(r, 0.2, keep & (((v - (le - 0.03)) * C < tex) | ((te - v) * C < tex)))
    p.cut(keep)


def ocelli(f, p, s):
    """Three small simple eyes in a triangle on top of an insect's head, glowing: s['ocelli'] = (x, z, r, spread)."""
    if f.face != "up" or "ocelli" not in s:
        return
    x, z, r, sp = s["ocelli"]
    for dx, dz in ((0.0, -sp * 0.6), (-sp, sp * 0.5), (sp, sp * 0.5)):
        d = np.hypot(f.X - (x + dx), f.Z - (z + dz))
        p.over("black", 0.04, d < r * 1.7)
        p.glow("glow", 0.62, d < r)


def hive_drone():
    """A drone of the hive gates, the size of a dog: a giant hornet. An orange face with a dark mask between huge
    compound eyes that glow from inside like coals, three small eyes glowing on its crown, black jaws toothed along
    their edges, elbowed feelers; a glossy black thorax with a fuzzy collar and yellow marks; an abdomen of glossy
    plates banded black and yellow (in its element's colour), a glowing sting; four smoky veined wings; six
    jointed legs, black above and orange below, hanging as it hovers."""
    name = "hive_drone"
    B = Body(name, 2300)
    box, bar = B.box, B.bar
    gl = dict(r="chitin", hair=0.4)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 8, 0], [
        box(0, 8.9, -0.6, 4.2, 3.8, 4.4, "segment", axis="z", seed=41, **gl,
            facepaint=[dict(pts=[(1.1, -2.5), (1.5, -1.4)], w=0.6, ramp="stripe", tone=0.55, faces=("up",)),
                       dict(pts=[(-1.1, -2.5), (-1.5, -1.4)], w=0.6, ramp="stripe", tone=0.55, faces=("up",)),
                       dict(pts=[(-1.8, 8.2), (0.2, 7.7)], w=0.5, ramp="stripe", tone=0.5, faces=("west", "east"))]),
        box(0, 8.9, -2.95, 4.6, 4.2, 0.9, "fur", r="fuzz", len=0.8, width=0.55, seed=42),
        box(0, 10.95, 0.9, 2.4, 0.5, 1.3, "segment", r="stripe", r2="stripe", seed=43, hair=0),
        *[box(sx * 2.2, 10.2, -1.4, 0.5, 0.8, 1.2, "segment", r="stripe", seed=47, hair=0) for sx in (-1, 1)],
        bar(0, 8.0, 1.4, 3.0, 1.3, 1.2, "segment", r="chitin", seed=44, hair=0),
    ])
    segs = [(2.4, 4.6, 3.4, 3.2, 7.8, (0.5, 0.8)), (4.2, 6.8, 4.2, 3.9, 7.7, (0.48, 0.94)), (6.4, 8.8, 3.9, 3.6, 7.55, (0.5, 0.94)),
            (8.4, 10.4, 3.4, 3.1, 7.4, (0.5, 0.94)), (10.0, 11.6, 2.7, 2.5, 7.25, (0.45, 0.94)), (11.3, 12.6, 1.9, 1.8, 7.1, (0.2, 1.0))]
    B.bone("abdomen", "body", [0, 7.8, 2.6], [
        *[bar(0, y, z0, z1, w, h, "segment", r="chitin", r2="stripe", band=bd, notch=0.22 if 0 < i < 5 else 0.0, seed=48 + i)
          for i, (z0, z1, w, h, y, bd) in enumerate(segs)],
        B.c([-0.2, 6.9, 12.4], [0.4, 0.4, 2.2], "crystal", skip=("north",)),
    ], rot=[-20, 0, 0])
    jaw = dict(r="mandible", hair=0)
    B.bone("head", "body", [0, 8.6, -2.6], [
        box(0, 9.1, -4.4, 3.8, 3.6, 2.2, "segment", r="face", axis="y", seed=45, hair=0, gloss=0.8,
            facepaint=[dict(pts=[(0.0, 10.8), (0.0, 9.0)], w=1.0, ramp="chitin", tone=0.18),
                       dict(pts=[(-1.0, 10.7), (0.0, 9.3), (1.0, 10.7)], w=0.55, ramp="chitin", tone=0.18)],
            cuts=[dict(pts=[(-1.3, 8.3), (-0.7, 8.7), (0.7, 8.7), (1.3, 8.3)], w=0.22, faces=("north",))],
            paint_faces=[dict(faces=("up",), ramp="face_d", tone=0.4, seed=52)],
            fx=ocelli, ocelli=(0.0, -4.6, 0.12, 0.36)),
        *[B.c(mirror_x([1.25, 7.5, -5.75], [1.45, 3.3, 2.5], sx), [1.45, 3.3, 2.5], "compound", r="eye", seed=46) for sx in (-1, 1)],
        # jaws: a pincer each side, curving in so their hooked tips cross
        *[B.c(mirror_x([0.9, 7.1, -7.0], [0.62, 0.62, 1.6], sx), [0.62, 0.62, 1.6], "horn", r="mandible", tip="-z", kr=(0.0, 0.66),
              pivot=[sx * 1.2, 7.4, -5.4], rot=[18, sx * 16, 0]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.53, 6.66, -7.76], [0.5, 0.5, 0.9], sx), [0.5, 0.5, 0.9], "horn", r="mandible", tip="-z", kr=(0.66, 1.0),
              pivot=[sx * 0.78, 6.906, -6.863], rot=[18, sx * 70, 0]) for sx in (-1, 1)],
        box(0, 7.55, -5.6, 1.4, 0.5, 0.4, "segment", r="face", axis="y", seed=53, hair=0, gloss=0.0),
        *[c for sx in (-1, 1) for c in limb(B, [sx * 0.55, 10.2, -5.5], [
            (sx * 0.85, 11.5, -6.3, 0.42, dict(r="face")),
            (sx * 1.6, 12.2, -7.9, 0.34), (sx * 2.4, 11.7, -9.2, 0.3)], "segment", r="chitin", hair=0, gloss=0.0)],
    ])
    legs = {0: [(3.0, 6.6, -1.0, 0.7), (3.4, 3.6, -1.9, 0.55), (3.3, 1.6, -2.7, 0.42)],
            1: [(3.3, 6.5, 0.0, 0.7), (4.0, 3.4, 0.3, 0.55), (4.2, 1.2, 0.5, 0.42)],
            2: [(3.2, 6.4, 1.2, 0.72), (4.0, 3.0, 2.6, 0.58), (4.0, 0.6, 4.0, 0.44)]}
    for side, sx in (("right", -1), ("left", 1)):
        for i, (z, sweep, ln, ch) in enumerate(((-1.6, -30, 9.6, 2.6), (0.2, -8, 6.8, 2.0))):
            B.bone(f"{side}_wing{i}", "body", [sx * 1.4, 10.1, z], [
                B.c(mirror_x([1.4, 9.95, z - ch * 0.4], [ln, 0.3, ch], sx), [ln, 0.3, ch], "skin", r="wingskin", marks=0.0, seed=45 + i,
                    fx=insect_wing, wing=dict(span="x", lead="-z", sheen="sheen"), skip=("north", "south", "east", "west")),
            ], rot=[0, sx * sweep, sx * -12])
        for j, z in enumerate((-1.8, -0.6, 0.6)):
            pts = [(sx * x, y, z + dz, w, dict(r=("chitin" if k == 0 else "leg"), seed=60 + j * 3 + k)) for k, (x, y, dz, w) in enumerate(legs[j])]
            B.bone(f"{side}_leg{j}", "body", [sx * 1.5, 6.8, z], limb(B, [sx * 1.6, 7.2, z], pts, "segment", hair=0))
    register(name, lambda aff: pal(aff, (255, 150, 30), {
        "chitin": [(4, 3, 3), (16, 11, 9), (40, 30, 26), (90, 74, 62), (176, 160, 146)],
        "stripe": aff_mix(aff, (236, 164, 36), 0.85),
        "face": aff_mix(aff, (214, 124, 36), 0.45),
        "leg": aff_mix(aff, (216, 136, 30), 0.4),
        "fuzz": (124, 84, 44),
        "eye": [(3, 2, 2), (20, 8, 6), (54, 20, 12), (120, 54, 30), (220, 160, 110)],
        "mandible": [(6, 4, 3), (30, 16, 8), (96, 52, 18), (168, 112, 52), (236, 214, 170)],
        "wingskin": (128, 104, 72),
        "sheen": (110, 90, 170),
    }, tinted=("chitin", "fuzz", "wingskin", "mandible", "eye")), density=6.0)
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
        "abdomen": rot(lambda t: [-80 * hold(t, 0.0, 0.25, 0.3, 0.55) - 22 * snap(t, 0.25, 0.32, 0.36, 0.55), 0, 0]),
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


# --------------------------------------------------------------------------------------------- brimling

def brimling():
    """A hovering imp of the burning gates: skinny limbs, a big head with ram's horns sweeping back from it, a scowl
    over slit eyes, a hooked nose, long ears, a wide grin of teeth with fire inside it and a jaw that drops open on it,
    a pointed chin with a wisp of beard, small bat wings held up behind it, a long tail with a spade at its end, an
    iron cuff with a broken chain on one ankle, skin black-red above and ember-orange below, cracked with fire."""
    name = "brimling"
    B = Body(name, 2400)
    box, col = B.box, B.col
    sk = dict(seed=51, mottle=0.12, fat=0.08, blotch=0.25, lava=dict(area=9.0, chance=0.75))
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 7, 0], [
        box(0, 9.0, 0.2, 4.4, 5.0, 3.0, "skin", zone="front", bib_w=0.3, **sk, anat=dict(part="chest", kind="lean", k=1.2)),
        box(0, 6.0, 0.4, 3.6, 1.8, 2.6, "skin", zone="back", **sk),
        col(0, 1.8, 7.0, 11.2, 0.8, 0.6, "horn", r="horn", tip="+y"),
    ], rot=[10, 0, 0])
    horns = []
    for sx in (-1, 1):
        horns += curl(B, [sx * 1.6, 15.9, -1.4], sx, [(10, 1.3, 1.4), (28, 1.2, 1.15), (52, 1.1, 0.9), (80, 1.0, 0.7), (110, 0.8, 0.5)],
                      "horn", r="horn", tip="+y", z_step=0.6, overlap=0.3)
    head = [
        box(0, 14.3, -1.0, 5.2, 3.4, 4.4, "skin", zone="back", split=(0.6, 0.15), **sk,
            eyes=eyes(1.2, 14.15, (0.85, 0.4), slant=0.62, pupil="slit", lid=1.0),
            wrinkles=dict(eyes=[(1.2, 14.15), (-1.2, 14.15)], eye_r=(0.85, 0.4), k=1.8, frown=(0.35, 14.5, 15.4), cheeks=True)),
        box(0, 16.2, -1.0, 4.4, 0.8, 3.6, "skin", zone="back", **sk),
        # a scowl over the eyes, a hooked nose, sharp cheekbones
        *[B.c(mirror_x([0.15, 14.75, -3.55], [2.5, 0.6, 0.8], sx), [2.5, 0.6, 0.8], "skin", r="skin_d", seed=52, pivot=[sx * 0.15, 15.05, -3.2],
              rot=[0, 0, -sx * 16], skip=("south",)) for sx in (-1, 1)],
        B.c([-0.45, 12.9, -3.95], [0.9, 1.4, 0.9], "skin", seed=53, mottle=0.1, blotch=0.2, pivot=[0, 14.0, -3.3], rot=[-14, 0, 0]),
        *[B.c(mirror_x([2.15, 12.5, -3.05], [0.8, 0.9, 2.2], sx), [0.8, 0.9, 2.2], "skin", **sk) for sx in (-1, 1)],
        # the grin: a row of teeth over a mouth full of fire
        B.c([-2.0, 12.0, -3.35], [4.0, 0.7, 0.3], "teeth", n=8, fangs=1, up=True, skip=("up", "down", "south")),
        box(0, 11.85, -1.8, 3.8, 1.1, 2.4, "mouth", skip=("north",)),
        box(0, 11.8, -1.0, 2.4, 0.7, 0.6, "glow"),
        *horns,
    ]
    for sx in (-1, 1):
        # long ears swept back
        head += limb(B, (sx * 2.4, 14.2, -1.2), [(sx * 3.7, 14.9, -0.4, 0.95), (sx * 4.8, 15.5, 0.5, 0.55, dict(tip="+y"))], "skin", r="skin", **sk)
    B.bone("head", "body", [0, 11.4, -0.4], head)
    B.bone("jaw", "head", [0, 12.0, 0.4], [
        box(0, 11.2, -1.35, 4.6, 1.2, 3.9, "skin", zone="back", split=(0.85, 0.3), **sk),
        B.c([-1.9, 11.65, -3.4], [3.8, 0.6, 0.3], "teeth", n=7, fangs=1, up=False, skip=("up", "down", "south")),
        box(0, 10.4, -2.3, 2.2, 0.8, 1.8, "skin", zone="back", **sk),
        B.c([-0.45, 9.0, -3.1], [0.9, 1.4, 0.6], "hair", r="horn", seed=54, ragged=0.8),
    ], rot=[8, 0, 0])
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
            # an iron cuff on the right ankle, a few links of a broken chain hanging from it
            *([col(-1.4, 0.4, -0.1, 0.9, 1.5, 1.5, "metal", r="iron", fuller=False, rust=0.4, scratches=1),
               *[col(-1.4 + (0.1 if i % 2 else -0.1), 0.4 - i * 0.15, -0.65 - i * 0.6, -0.05 - i * 0.6, 0.35 if i % 2 else 0.5, 0.5 if i % 2 else 0.35,
                     "chain", r="iron") for i in range(3)]] if sx < 0 else []),
        ], rot=[50, 0, 0])
        wing_pair(B, side, sx, "body", [sx * 1.2, 11.2, 1.6], (3.6, 4.2), (5.4, 4.6), fingers=2, rest=[45, -sx * 12, -sx * 24])
    B.bone("tail", "body", [0, 5.6, 1.6], [
        B.bar(0, 5.6, 1.6, 6.0, 0.9, 0.9, "skin", r="skin_d", **sk)], rot=[30, 0, 0])
    B.bone("tail2", "tail", [0, 5.6, 6.0], [
        B.bar(0, 5.6, 6.0, 10.0, 0.7, 0.7, "skin", r="skin_d", **sk)], rot=[-40, 0, 0])
    B.bone("tail3", "tail2", [0, 5.6, 10.0], [
        B.bar(0, 5.6, 10.0, 12.0, 0.6, 0.6, "skin", r="skin_d", **sk),
        B.c([-1.2, 5.3, 11.6], [2.4, 0.6, 2.6], "horn", r="claw", tip="+z", skip=()),
    ], rot=[-30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 150, 40), {
        "skin": (96, 26, 26), "skin_d": (30, 10, 12), "skin_l": (196, 88, 48),
        "horn": (58, 42, 40), "membrane": (72, 20, 22), "bone": (50, 24, 24), "claw": (26, 18, 18),
    }), density=6.0)
    B.write(2.0, 1.5, 0.5)

    A = {}
    L = 0.8
    A["idle"] = anim(L, True, {
        **flap(L, 34, 18),
        "body": {"position": lambda t: [0, 1.0 * wave(t, L, 0.25), 0]},
        "head": rot(lambda t: [3 * wave(t, L), 8 * wave(t, L * 3), 6 * wave(t, L * 2)]),
        "jaw": rot(lambda t: [5 * max(0.0, wave(t, L, 0.1)), 0, 0]),
        "tail": rot(lambda t: [0, 14 * wave(t, L * 2), 0]), "tail2": rot(lambda t: [0, 20 * wave(t, L * 2, 0.2), 0]),
        "tail3": rot(lambda t: [0, 26 * wave(t, L * 2, 0.4), 0]),
        "right_leg": rot(lambda t: [6 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [-6 * wave(t, L), 0, 0]),
    }, 0.025)
    L = 0.6
    A["walk"] = anim(L, True, {
        **flap(L, 42, 24),
        "body": {"position": lambda t: [0, 0.8 * wave(t, L, 0.25), 0], "rotation": lambda t: [18, 0, 0]},
        "head": rot(lambda t: [-16, 0, 0]),
        "jaw": rot(lambda t: [4 + 3 * wave(t, L / 2), 0, 0]),
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
        # a cackle: the jaw drops as the hands gather and snaps shut after the throw
        "jaw": rot(lambda t: [24 * hold(t, 0.05, 0.4, 0.55, 0.75), 0, 0]),
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
        "jaw": rot(lambda t: [30 * window(t, 0.2, 0.6), 0, 0]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- wailing wraith

def throat_fx(f, p, s):
    """The inside of a mouth stretched open: black, with a cold light far down the throat."""
    if f.face != "north":
        return
    a = (f.X - f.cx) / max(f.s[0] / 2, 1e-6)
    h = (f.Y - f.bottom) / max(f.top - f.bottom, 1e-6)
    w = np.clip(1.0 - np.sqrt((a / 0.62) ** 2 + ((h - s.get("throat_h", 0.42)) / 0.42) ** 2), 0, 1)
    p.glow("glow", 0.04 + 0.62 * w ** 2.2, w > 0.02)


def wailing_wraith():
    """A drifting ghost in a deep cowl, its robes tearing away into light below the waist: a gaunt grey face sunk in
    the dark of the hood, a point of cold light in each socket and a jaw hanging long and open in a wail; long starved
    arms with black claws, rusted chains wound round it, a padlock, and a lantern of trapped light at its hip."""
    name = "wailing_wraith"
    B = Body(name, 2500)
    box, col = B.box, B.col
    robe = dict(r="cloth", folds=1.6)
    hood = dict(r="hood", folds=1.2)
    gaunt = dict(r="gaunt", mottle=0.2, blotch=0.35, fat=0.1)
    B.bone("root", None, [0, 0, 0])
    # long rags hanging from the waist all round, each torn away at its end into light
    rags = []
    for i, (x, z, w, bot, lean) in enumerate(((-2.2, -2.4, 2.0, 2.2, 8), (0.1, -2.5, 2.3, 0.4, 5), (2.3, -2.4, 1.7, 3.2, 10),
                                               (-2.4, 2.7, 2.2, 1.2, -8), (0.1, 2.8, 2.0, 3.4, -5), (2.4, 2.7, 2.1, 0.8, -10))):
        rags.append(B.c([x - w / 2, bot, z - 0.18], [w, 14.6 - bot, 0.36], "cloth", pivot=[x, 14.6, z], rot=[-lean, 0, 0],
                        hem=3.2 + (i % 3) * 0.8, glow_hem=2.8, skip=("up", "down"), seed=85 + i, **robe))
    for i, (sx, z, w, bot, lean) in enumerate(((-1, 0.4, 2.6, 2.6, 9), (1, 0.8, 2.4, 1.2, 10))):
        x = sx * 2.95
        rags.append(B.c([x - 0.18, bot, z - w / 2], [0.36, 14.6 - bot, w], "cloth", pivot=[x, 14.6, z], rot=[0, 0, -sx * lean],
                        hem=3.6, glow_hem=2.8, skip=("up", "down"), seed=92 + i, **robe))
    lx, lz = 3.9, -1.55   # the lantern at the left hip
    B.bone("body", "root", [0, 16, 0], [
        # a narrow chest under a ragged mantle, a waist bound with rope, a skirt that falls apart into rags
        box(0, 19.9, 0.4, 6.0, 5.8, 4.0, "cloth", seed=83, **robe),
        box(0, 15.6, 0.4, 4.8, 2.8, 3.4, "cloth", seed=84, **robe),
        box(0, 11.3, 0.4, 5.8, 6.0, 4.4, "cloth", seed=81, hem=3.4, glow_hem=1.8, skip=("down",), **robe),
        *rags,
        # the rope belt, knotted at the front, its ends hanging
        box(0, 15.5, 0.4, 5.2, 0.8, 3.8, "cloth", r="rope", folds=3.0, seed=96),
        box(-1.0, 15.3, -1.65, 1.0, 1.0, 0.7, "cloth", r="rope", seed=97),
        B.c([-1.35, 11.4, -1.8], [0.45, 3.8, 0.45], "cloth", r="rope", seed=98, pivot=[-1.1, 15.2, -1.6], rot=[-6, 0, -8]),
        B.c([-0.85, 12.1, -1.85], [0.45, 3.1, 0.45], "cloth", r="rope", seed=99, pivot=[-0.6, 15.2, -1.6], rot=[-8, 0, 6]),
        # the mantle: a ragged capelet over the shoulders, a tarnished brooch with a cold gem at the throat
        B.c([-3.9, 17.6, -2.3], [7.8, 5.3, 5.4], "cloth", hem=2.4, seed=93, skip=("down",), band=(19.9, 20.7), band_r="silver",
            **hood),
        box(0, 23.6, 1.2, 4.6, 1.8, 3.8, "cloth", zone="dark", seed=94, **hood),
        box(0, 21.7, -2.55, 1.5, 1.5, 0.5, "trim", r="silver"),
        box(0, 21.7, -2.86, 0.6, 0.6, 0.14, "glow"),
        # rusted chains wound round it, a padlock hanging from the front one
        B.c([-4.2, 17.4, -2.75], [8.4, 0.7, 0.5], "chain", r="rust", pivot=[0, 17.8, -2.5], rot=[0, 0, 22]),
        B.c([-4.2, 14.2, 2.3], [8.4, 0.7, 0.5], "chain", r="rust", pivot=[0, 14.6, 2.55], rot=[0, 0, -14]),
        col(-2.6, -2.55, 12.4, 16.6, 0.45, 0.45, "chain", r="rust"),
        box(-2.6, 11.7, -2.55, 1.4, 1.5, 0.8, "plate", r="iron", rivets=False),
        B.c([-3.0, 12.4, -2.65], [0.8, 0.9, 0.25], "metal", r="iron"),
        # a lantern of trapped light at the left hip, on a short chain from the belt
        col(lx, lz, 12.7, 15.4, 0.4, 0.4, "chain", r="rust"),
        box(lx, 12.7, lz, 1.9, 0.5, 1.9, "plate", r="iron", rivets=False),
        box(lx, 13.15, lz, 1.0, 0.4, 1.0, "plate", r="iron", rivets=False),
        box(lx, 11.2, lz, 1.3, 2.5, 1.3, "glow"),
        *[col(lx + dx, lz + dz, 9.95, 12.45, 0.32, 0.32, "metal", r="iron") for dx in (-0.78, 0.78) for dz in (-0.78, 0.78)],
        box(lx, 9.75, lz, 1.9, 0.5, 1.9, "plate", r="iron", rivets=False),
    ], rot=[10, 0, 0])
    B.bone("head", "body", [0, 24.0, 0], [
        # the face, sunk deep in the cowl: brows drawn up in anguish and gaunt cheekbones round two holes of dark with a
        # point of light in each, and the upper teeth bared over the open mouth
        box(0, 29.4, 0.7, 4.2, 4.2, 4.4, "skin", seed=95, **gaunt),
        box(0, 29.15, -1.62, 3.6, 1.7, 0.24, "void", sockets=sockets(0.88, 29.15, (0.7, 0.55), slant=0.22, core=0.4)),
        *[B.c(mirror_x([0.1, 29.95, -2.4], [1.9, 0.8, 0.9], sx), [1.9, 0.8, 0.9], "skin", pivot=[sx * 0.1, 30.35, -1.95], rot=[0, 0, sx * 12],
              seed=96, shadow_top=(29.9, 30.3, 0.2), **gaunt) for sx in (-1, 1)],
        box(0, 29.05, -1.85, 0.7, 1.5, 0.7, "skin", seed=97, **gaunt),
        *[B.c(mirror_x([0.8, 27.75, -2.2], [1.1, 0.6, 0.8], sx), [1.1, 0.6, 0.8], "skin", pivot=[sx * 0.8, 28.05, -1.8], rot=[0, -sx * 22, 0],
              seed=98, **gaunt) for sx in (-1, 1)],
        box(0, 27.35, -1.75, 2.6, 0.9, 1.0, "skin", seed=99, **gaunt),
        box(0, 26.65, -2.0, 2.4, 0.6, 0.3, "teeth", n=7, fangs=1, up=True, skip=("up", "down", "south")),
        # the inside of the mouth, lit from far down the throat
        box(0, 25.2, -0.9, 2.3, 3.4, 1.4, "void", fx=throat_fx, throat_h=0.35),
        # the cowl: a crown and sides dark inside, a rounded back, a peak drooping back, a heavy rolled rim round the
        # opening
        box(0, 32.15, 0.7, 6.4, 1.0, 6.6, "cloth", dark_faces=("down",), seed=86, **hood),
        # a ridge along the crown, so the cowl rises to a point, and a long tail hanging from it down the back
        B.c([-1.8, 30.6, -1.6], [3.6, 3.6, 5.8], "cloth", pivot=[0, 32.4, 1.3], rot=[0, 0, 45], seed=87, **hood),
        B.c([-0.75, 24.6, 3.9], [1.5, 8.6, 0.7], "cloth", pivot=[0, 33.2, 4.2], rot=[8, 0, 0], hem=1.4, seed=88, **hood),
        # the top corners of the opening drawn in, so it closes to a point like a cowl's
        *[B.c(mirror_x([1.5, 30.2, -3.55], [1.9, 1.3, 1.25], sx), [1.9, 1.3, 1.25], "cloth", r="hood_l", pivot=[sx * 2.45, 30.85, -2.95],
              rot=[0, 0, sx * 40], dark_faces=("down",), seed=84, folds=2.0) for sx in (-1, 1)],
        *[B.c(mirror_x([2.5, 23.4, -2.6], [0.9, 9.2, 6.6], sx), [0.9, 9.2, 6.6], "cloth", pivot=[sx * 2.95, 32.2, 0.7], rot=[0, 0, -sx * 5],
              dark_faces=(("east",) if sx > 0 else ("west",)), hem=1.2, seed=89 + (sx > 0), **hood) for sx in (-1, 1)],
        box(0, 28.0, 3.6, 5.2, 8.6, 0.9, "cloth", dark_faces=("north",), seed=91, **hood),
        B.c([-3.05, 30.3, 2.3], [6.1, 2.2, 2.2], "cloth", pivot=[0, 31.4, 3.4], rot=[45, 0, 0], seed=85, **hood),
        box(0, 31.55, -2.95, 6.6, 1.3, 1.3, "cloth", r="hood_l", dark_faces=("down",), seed=92, folds=2.0),
        *[box(sx * 2.75, 27.8, -2.75, 1.2, 7.4, 1.3, "cloth", r="hood_l", dark_faces=(("east",) if sx > 0 else ("west",)), seed=93,
              folds=2.0) for sx in (-1, 1)],
    ], rot=[-6, 0, 0])
    B.bone("jaw", "head", [0, 26.8, 1.6], [
        # the jaw hangs long and open: a chin with the lower teeth, and the bones running back up to the hinge
        box(0, 23.25, -1.35, 2.4, 0.9, 1.3, "skin", seed=100, **gaunt),
        box(0, 23.95, -1.75, 2.0, 0.55, 0.3, "teeth", n=6, fangs=1, up=False, skip=("up", "down", "south")),
        *[limb(B, (sx * 1.35, 26.6, 0.9), [(sx * 1.2, 23.3, -1.0, 0.55, dict(seed=101))], "skin", **gaunt)[0] for sx in (-1, 1)],
    ])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 3.8, 22.4, 0.3], [
            # a sleeve that hangs open into a torn bell at the elbow
            B.c(mirror_x([3.3, 18.4, -1.2], [2.6, 4.8, 2.9], sx), [2.6, 4.8, 2.9], "cloth", seed=89, **robe),
            B.c(mirror_x([3.0, 14.6, -1.6], [3.2, 4.4, 3.6], sx), [3.2, 4.4, 3.6], "cloth", hem=2.4, glow_hem=1.1, seed=90,
                skip=("up", "down"), **robe),
        ], rot=[-30, 0, sx * -16])
        hand = [
            # a starved forearm, knobbed at the wrist, a long hand and four long black claws
            col(sx * 4.6, 0, 10.6, 17.0, 1.2, 1.2, "skin", seed=102, anat=dict(part="fore", k=0.7), **gaunt),
            box(sx * 4.6, 10.4, 0, 1.6, 0.8, 1.5, "skin", seed=103, **gaunt),
            box(sx * 4.6, 9.3, -0.15, 1.5, 1.6, 1.1, "skin", seed=104, anat=dict(part="fist", k=0.8), **gaunt),
        ]
        for i in range(4):
            fx_ = sx * 4.6 + (i - 1.5) * 0.36
            hand += limb(B, (fx_, 8.6, -0.25), [(fx_ + (i - 1.5) * 0.12, 6.6, -0.65, 0.34, dict(seed=105 + i)),
                                               (fx_ + (i - 1.5) * 0.18, 4.6, -0.55, 0.3, dict(m="horn", r="claw", tip="+y", kr=(0.0, 1.0)))],
                         "skin", **gaunt)
        if sx < 0:
            # a shackle still locked on the right wrist, a few links of its chain hanging from it
            hand += [col(sx * 4.6, 0, 11.0, 12.4, 1.9, 1.9, "plate", r="iron", rivets=False),
                     col(sx * 4.6 - 0.6, 0.95, 7.0, 11.2, 0.45, 0.45, "chain", r="rust")]
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.6, 16.6, 0], hand, rot=[-20, 0, 0])
    register(name, lambda aff: pal(aff, (200, 255, 225), {
        "cloth": (40, 44, 58), "cloth_d": (14, 15, 24), "cloth_l": (92, 100, 122),
        "hood": (32, 35, 48), "hood_d": (8, 9, 16), "hood_l": (70, 78, 98),
        "gaunt": (176, 180, 178), "claw": (24, 22, 26), "rust": (92, 66, 50),
        "silver": [(16, 18, 22), (54, 58, 66), (110, 116, 126), (170, 176, 188), (226, 230, 238)],
    }), density=5.0)
    B.write(1.6, 2.4, 1.0)

    A = {}
    L = 3.0
    A["idle"] = anim(L, True, {
        "root": {"position": lambda t: [0, 1.2 * wave(t, L), 0]},
        "body": rot(lambda t: [10 + 2 * wave(t, L, 0.2), 0, 2 * wave(t, L * 2)]),
        "head": rot(lambda t: [3 * wave(t, L, 0.3), 12 * wave(t, L * 2, 0.1), 0]),
        "jaw": rot(lambda t: [3 * wave(t, L / 2, 0.1), 0, 0]),
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
        "jaw": rot(lambda t: [8 * hold(t, 0.1, 0.36, 0.5, 0.8), 0, 0]),
    }, 0.025)
    # wail (1.4 s): arms flung wide, head thrown back, the jaw dropping wide for the scream at 0.7 s
    L = 1.4
    A["wail"] = anim(L, False, {
        "right_arm": rot(lambda t: [-40 * hold(t, 0.1, 0.6, 1.0, 1.4), 0, -70 * hold(t, 0.1, 0.6, 1.0, 1.4)]),
        "left_arm": rot(lambda t: [-40 * hold(t, 0.1, 0.6, 1.0, 1.4), 0, 70 * hold(t, 0.1, 0.6, 1.0, 1.4)]),
        "head": rot(lambda t: [-35 * hold(t, 0.2, 0.65, 1.0, 1.4) + 3 * math.sin(t * 60) * window(t, 0.65, 0.7) * (1 - window(t, 0.95, 1.0)), 0, 0]),
        "jaw": {"rotation": lambda t: [8 * hold(t, 0.3, 0.65, 1.0, 1.3) + 2 * math.sin(t * 70) * window(t, 0.65, 0.7) * (1 - window(t, 0.95, 1.0)), 0, 0],
                "position": lambda t: [0, -1.1 * hold(t, 0.3, 0.65, 1.0, 1.3), 0]},
        "body": rot(lambda t: [-16 * hold(t, 0.2, 0.65, 1.0, 1.4), 0, 0]),
        "root": {"position": lambda t: [0, 2 * hold(t, 0.1, 0.65, 1.0, 1.4), 0]},
    }, 0.025)
    L = 1.2
    fade = lambda t: max(0.01, 1 - ease_in(window(t, 0.4, 1.15)))  # noqa: E731
    A["death"] = anim(L, "hold", {
        "root": {"position": lambda t: [0, 6 * ease_in(window(t, 0.2, 1.1)), 0], "scale": lambda t: [fade(t), fade(t) * (1 + 0.4 * window(t, 0.3, 1.0)), fade(t)]},
        "head": rot(lambda t: [-40 * window(t, 0.0, 0.4), 0, 0]),
        "jaw": {"rotation": lambda t: [10 * window(t, 0.0, 0.4), 0, 0], "position": lambda t: [0, -1.0 * window(t, 0.0, 0.4), 0]},
        "right_arm": rot(lambda t: [-60 * window(t, 0.0, 0.4), 0, -50 * window(t, 0.0, 0.4)]),
        "left_arm": rot(lambda t: [-60 * window(t, 0.0, 0.4), 0, 50 * window(t, 0.0, 0.4)]),
    }, 0.025)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- static drifter

def jelly_bell(f, p, s):
    """A storm jellyfish's bell: dark and glassy, lit from inside so it glows faintly towards its crown, glowing
    canals running out from the crown and straight down to the rim, four glowing horseshoes round the crown (its
    organs, seen through the bell), and a row of small lights round the rim."""
    X, Y, Z = f.X, f.Y, f.Z
    tex = f.texel
    r = s.get("r", "jelly")
    top, rim = s.get("crown", 24.0), s.get("rim_y", 14.2)
    hn = np.clip((Y - rim) / (top - rim), 0, 1)
    n = CS.fbm3(X * 0.5, Y * 0.5, Z * 0.5, s.get("seed", 91), 2)
    p.fill(r, 0.3 + 0.2 * hn + (n - 0.5) * 0.08)
    p.glow("glow", 0.05 + 0.2 * hn, 0.04 + 0.16 * hn ** 2)            # the light inside, tinting it towards the crown
    ang = np.arctan2(Z, X)
    rad = np.hypot(X, Z)
    if f.face == "up":
        k = (ang / (2 * np.pi) * 8) % 1.0
        canal = np.abs(k - 0.5) * 2 > 1 - (tex * 0.8) / np.maximum(rad * 2 * np.pi / 8, 1e-3) * 2
        p.glow("glow", 0.5, canal & (rad > 1.4))
        for q in range(4):
            a = q * np.pi / 2 + np.pi / 4
            cx, cz = math.cos(a) * 1.7, math.sin(a) * 1.7
            d = np.hypot(X - cx, Z - cz)
            horse = (np.abs(d - 0.75) < 0.22) & (((X - cx) * math.cos(a) + (Z - cz) * math.sin(a)) > -0.35)
            p.glow("glow", 0.72, horse)
            p.over(r, 0.5, (d < 0.53) & ~horse)
    elif f.wall:
        a = CS.across(f)
        half = (f.s[2] if f.side else f.s[0]) / 2
        c = f.cz if f.side else f.cx
        for t in (-0.6, 0.0, 0.6):
            p.glow("glow", 0.46, np.abs(a - (c + t * half)) < tex * 0.6)
        if s.get("rim_lights"):
            k = ((a - c) / 1.6) % 1.0
            dot = (np.abs(k - 0.5) < 0.1) & (Y < f.bottom + 0.42) & (Y > f.bottom + 0.15)
            p.glow("glow", 0.9, dot)
    else:
        k = (ang / (2 * np.pi) * 8) % 1.0
        p.over(r, 0.18, 1.0)
        p.glow("glow", 0.42, (np.abs(k - 0.5) * 2 > 0.86) & (rad > 1.0))


def thread_beads(f, p, s):
    """A stinging thread: thin and dark, beaded here and there with small points of light, its end glowing."""
    if not f.wall:
        return
    yr = f.Y - f.bottom
    p.glow("glow", 0.7, ((yr % 1.7) < f.texel * 1.05) & (np.abs(f.U - 0.5) < 0.3))
    if s.get("tip"):
        p.glow("glow", 0.6 + 0.35 * np.clip(1 - yr / 0.9, 0, 1), yr < 0.9)


def static_drifter():
    """A drifting jellyfish of the storm gates: a dark glassy bell lit from inside, glowing canals running out from
    its crown, four glowing horseshoes seen through it, lights round its rim and lightning crawling over it; a
    crackling storm-crystal hanging under it, frilled ribbon arms twisting down, and long beaded stinging threads."""
    name = "static_drifter"
    B = Body(name, 2600)
    box = B.box
    jl = dict(r="jelly", seed=91, fx=jelly_bell, marks=0.4)
    B.bone("root", None, [0, 0, 0])
    dome = []
    for (y, h, w, cw, ch) in ((15.4, 2.4, 12.8, 14.0, 9.6), (18.15, 3.2, 11.6, 12.6, 8.4), (20.65, 2.2, 9.0, 9.8, 6.4),
                              (22.35, 1.6, 6.0, 6.6, 4.0)):
        dome.append(box(0, y, 0, w, h, w, "skin", **jl, rim_lights=(y < 16)))
        dome.append(box(0, y - 0.1, 0, cw, h - 0.4, ch, "skin", **{**jl, "seed": 95}, rim_lights=(y < 16)))
        dome.append(box(0, y - 0.1, 0, ch, h - 0.4, cw, "skin", **{**jl, "seed": 96}, rim_lights=(y < 16)))
    B.bone("bell", "root", [0, 18, 0], [
        *dome,
        box(0, 23.5, 0, 3.4, 0.9, 3.4, "skin", **jl),
        B.c([-6.5, 13.4, -6.5], [13.0, 1.4, 13.0], "cloth", r="jelly_d", hem=1.0, seed=92, skip=("up",), spots=dict(glow=True, cut=0.86, k=1.4, seed=5)),
        B.c([-1.6, 11.4, -1.6], [3.2, 3.2, 3.2], "crystal", pivot=[0, 13.0, 0], rot=[35, 45, 0]),
        box(0, 13.6, 0, 5.0, 0.8, 5.0, "glow"),
    ])
    arms = []
    for i, (x, z) in enumerate(((-1.6, -1.6), (1.6, -1.6), (-1.6, 1.6), (1.6, 1.6))):
        a0 = 20 + 90 * i
        for j, (y0, y1, w) in enumerate(((10.2, 13.8, 2.2), (6.0, 10.4, 1.8), (1.6, 6.2, 1.4))):
            arms.append(B.c([x - w / 2, y0, z - 0.35], [w, y1 - y0, 0.7], "cloth", r="frill", hem=(1.6 if j == 2 else 0.0), folds=2.2,
                            seed=93 + i * 3 + j, pivot=[x, y1, z], rot=[0, a0 + 40 * j, 0], skip=("up",),
                            spots=dict(glow=True, cut=0.84, k=1.8, seed=11 + i)))
    B.bone("arms", "bell", [0, 14, 0], arms)
    corners = ((-5.2, -5.2), (5.2, -5.2), (-5.2, 5.2), (5.2, 5.2))
    mids = ((0.0, -6.2), (6.2, 0.0), (-6.2, 0.0), (0.0, 6.2))
    for k, (x, z) in enumerate(corners):
        th = []
        for j, (bx, bz, ln) in enumerate(((x, z, 13.4), (x * 0.82 + 0.6, z * 0.82 - 0.4, 11.6), (mids[k][0], mids[k][1], 12.4))):
            sway = 0.6 if j % 2 else -0.6
            pts = [(bx + sway, 14.0 - ln * 0.35, bz - sway * 0.5, 0.42, {}), (bx - sway * 0.6, 14.0 - ln * 0.7, bz + sway * 0.4, 0.38, {}),
                   (bx + sway * 0.3, 14.0 - ln, bz, 0.34, dict(tip=True))]
            th += limb(B, [bx, 14.0, bz], pts, "skin", r="thread", seed=94, marks=0.0, fx=thread_beads)
        B.bone(f"threads{k}", "bell", [x, 14, z], th)
    register(name, lambda aff: pal(aff, (120, 255, 255), {
        "jelly": [(8, 8, 22), (24, 24, 56), (54, 52, 104), (100, 96, 164), (168, 164, 220)],
        "jelly_d": [(6, 6, 16), (20, 20, 44), (44, 42, 84), (84, 80, 134), (140, 136, 190)],
        "frill": [(20, 14, 34), (54, 40, 84), (100, 80, 140), (150, 130, 190), (210, 196, 236)],
        "thread": (118, 112, 168),
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
    """A great bear of the frozen gates: shaggy fur slate-dark on the back and frost-pale beneath, rimed white on the
    tips, glowing ice growing from its shoulders and spine, a heavy brow over small fierce eyes, an old claw scar
    across one of them, a snarling dark muzzle full of fangs with icicles hanging from its chin, long fur hanging from
    its chest, belly, cheeks and forelegs, and claws like hooks of black ice."""
    name = "rimeclaw"
    B = Body(name, 2700)
    box, col = B.box, B.col
    fur = dict(seed=34, len=3.0, width=1.9, lock_k=0.7, frost=dict(k=0.45, low=0.74, cut=0.07))
    lfur = dict(seed=35, len=2.2, width=1.4, lock_k=0.8, frost=dict(k=0.35, low=0.8, cut=0.07))
    hang = dict(r="fur", ragged=1.2, skip=("up", "down"))
    B.bone("root", None, [0, 0, 0])
    spikes = (shard(B, 0, 21.6, -9.0, 3.0, 5.6, -24, 0) + shard(B, -3.6, 21.0, -7.0, 2.4, 4.4, -30, -26) + shard(B, 3.6, 21.0, -7.0, 2.4, 4.4, -30, 26)
              + shard(B, 0, 21.4, -3.6, 2.4, 4.2, -36, 0) + shard(B, -2.8, 20.6, 0.2, 2.0, 3.2, -40, -22) + shard(B, 2.8, 20.6, 0.2, 2.0, 3.2, -40, 22)
              + shard(B, 0, 20.0, 4.4, 2.0, 3.0, -44, 0))
    # the shoulders bulge over the forelegs and the haunches over the hind legs; the ribs show faintly between them
    fore_body = [dict(a=-0.42, h=0.62, ra=0.46, rh=0.34, k=0.12, rim=0.12), dict(a=0.35, h=0.42, ra=0.6, rh=0.3, k=0.05, rim=0.0)]
    rib_grooves = [dict(pts=[(0.0, 0.62), (0.15, 0.42), (0.18, 0.22)], w=1.2, k=0.07, lip=0.04),
                   dict(pts=[(0.35, 0.6), (0.48, 0.4), (0.5, 0.22)], w=1.2, k=0.06, lip=0.03)]
    scar = [dict(ramp="fur_d", start=(-6.0, 17.6), end=(-1.0, 12.4), n=3, gap=1.2, width=0.62, tone=0.12, seed=31, face="east"),
            dict(ramp="scar", start=(-6.0, 17.6), end=(-1.0, 12.4), n=3, gap=1.2, width=0.34, tone=0.5, seed=31, face="east")]
    B.bone("body", "root", [0, 14, 0], [
        box(0, 15.0, -5.0, 14.0, 13.0, 13.0, "fur", r="fur", zone="back", split=(0.5, 0.28), **fur,
            lobes=fore_body, grooves=rib_grooves, claws=scar),
        box(0, 14.0, 6.0, 12.0, 11.0, 11.0, "fur", r="fur", zone="back", split=(0.5, 0.28), **fur,
            lobes=[dict(a=0.25, h=0.58, ra=0.55, rh=0.38, k=0.12, rim=0.12)]),
        box(0, 21.4, -6.0, 12.0, 2.4, 9.0, "fur", r="fur", zone="dark", **fur),
        *spikes,
        # long fur hanging from the chest between the forelegs and from the belly behind them
        box(0, 9.0, -11.75, 5.2, 5.2, 0.5, "hair", seed=36, r="fur_l", ragged=1.2, skip=("up", "down")),
        *[box(sx * 7.1, 9.0, -0.8, 0.5, 4.6, 4.4, "hair", seed=37 + i, **hang) for i, sx in enumerate((-1, 1))],
        *[box(sx * 6.1, 9.4, 3.0, 0.5, 3.6, 3.2, "hair", seed=39 + i, **hang) for i, sx in enumerate((-1, 1))],
    ])
    face_mask = [dict(pts=[(-3.6, 19.3), (-2.3, 18.7), (-1.0, 19.3)], w=1.5, ramp="fur_d", tone=0.18),
                 dict(pts=[(1.0, 19.3), (2.3, 18.7), (3.6, 19.3)], w=1.5, ramp="fur_d", tone=0.18),
                 # the old claw scar across its right eye, from brow to cheek
                 dict(pts=[(-1.4, 21.6), (-2.2, 19.6), (-3.0, 16.4)], w=0.75, ramp="fur_d", tone=0.1, seed=6),
                 dict(pts=[(-1.4, 21.6), (-2.2, 19.6), (-3.0, 16.4)], w=0.4, ramp="scar", tone=0.5, seed=6)]
    B.bone("head", "body", [0, 17, -11], [
        box(0, 17.6, -15.0, 9.0, 8.0, 8.0, "fur", r="fur", zone="front", bib_w=0.12, **lfur, facepaint=face_mask,
            eyes=eyes(2.3, 19.45, (0.95, 0.45), slant=0.5, pupil="slit"),
            lobes=[dict(faces=("north",), a=0.62, h=0.3, ra=0.3, rh=0.22, k=0.1, rim=0.1, sym=True)]),
        box(0, 20.5, -19.0, 9.4, 1.6, 1.4, "fur", r="fur", zone="front", bib_w=0.1, **lfur,
            grooves=[dict(faces=("north",), pts=[(-0.12, 0.1), (-0.05, 0.9)], w=0.9, k=0.16, lip=0.0, sym=True)],
            facepaint=[dict(pts=[(-1.4, 21.6), (-1.6, 19.8)], w=0.4, ramp="scar", tone=0.5, seed=6)]),
        # the muzzle: a pale blaze down its bridge, the skin wrinkled back in a snarl, black lips pulled off the gums
        box(0, 16.0, -20.6, 6.0, 4.0, 5.0, "fur", r="muzzle", seed=60, len=1.0, width=0.8, lock_k=0.7,
            cuts=[dict(pts=[(-23.0, 14.25), (-21.0, 14.35), (-18.4, 15.3)], w=0.5, faces=("west", "east")),
                  dict(pts=[(-3.0, 14.2), (3.0, 14.2)], w=0.4, faces=("north",))],
            facepaint=[dict(pts=[(-23.0, 14.7), (-21.0, 14.8), (-18.6, 15.75)], w=0.45, ramp="gum", tone=0.42, faces=("west", "east")),
                       dict(pts=[(-2.6, 14.65), (2.6, 14.65)], w=0.4, ramp="gum", tone=0.38, faces=("north",)),
                       dict(pts=[(0.0, -18.1), (0.0, -22.6)], w=1.2, ramp="muzzle_l", tone=0.42, faces=("up",))],
            grooves=[dict(faces=("up",), pts=[(-0.7, 0.3 + 0.13 * i), (0.0, 0.26 + 0.13 * i), (0.7, 0.3 + 0.13 * i)], w=1.0, k=0.16, lip=0.07)
                     for i in range(3)] + [dict(faces=("west", "east"), pts=[(-0.7, 0.62 + 0.12 * i), (0.1, 0.55 + 0.12 * i)], w=0.9, k=0.12, lip=0.05)
                                           for i in range(2)]
                    + [dict(faces=("north",), pts=[(0.42, 0.3 + 0.16 * i), (0.85, 0.42 + 0.2 * i)], w=0.9, k=0.13, lip=0.05, sym=True) for i in range(2)],
            paint_faces=[dict(faces=("down",), ramp="mouth", tone=0.3)],
            flush=dict(faces=("north",), low=0.5, ramp="muzzle_d", tone=0.3, weight=0.6)),
        box(0, 17.6, -23.3, 3.0, 1.8, 0.8, "dark", t=0.25, holes=[dict(x=0.65, y=17.3, r=(0.42, 0.34)), dict(x=-0.65, y=17.3, r=(0.42, 0.34))],
            facepaint=[dict(pts=[(-0.9, 18.15), (0.5, 18.2)], w=0.35, ramp="black", tone=0.62, faces=("north",)),
                       dict(pts=[(-0.9, -23.0), (0.6, -23.5)], w=0.4, ramp="black", tone=0.7, faces=("up",))]),
        box(0, 13.55, -22.75, 5.2, 1.3, 0.5, "teeth", n=4, fangs=1, small=0.55, up=True, skip=("up", "down", "south")),
        *[box(sx * 3.4, 22.2, -13.6, 2.4, 2.4, 1.4, "fur", r="fur_d", **lfur, ear=dict(ramp="muzzle", weight=0.7)) for sx in (-1, 1)],
        *shard(B, -2.6, 21.2, -14.0, 1.4, 2.6, -30, -20), *shard(B, 2.6, 21.2, -14.0, 1.4, 2.6, -30, 20),
        # a ruff of long fur standing off each cheek
        *[box(sx * 4.75, 15.0, -14.8, 0.5, 4.6, 5.4, "hair", seed=41 + i, r="fur", ragged=1.0, skip=("up", "down"))
          for i, sx in enumerate((-1, 1))],
    ])
    B.bone("jaw", "head", [0, 14.4, -15], [
        box(0, 12.5, -19.4, 5.6, 1.8, 6.0, "fur", r="muzzle", seed=61, len=1.0, width=0.8, lock_k=0.7,
            paint_faces=[dict(faces=("up",), ramp="mouth", tone=0.22)],
            cuts=[dict(pts=[(-2.8, 13.25), (2.8, 13.25)], w=0.4, faces=("north",)),
                  dict(pts=[(-22.4, 13.25), (-16.6, 13.3)], w=0.4, faces=("west", "east"))]),
        box(0, 13.7, -22.1, 4.6, 0.6, 0.4, "teeth", n=4, fangs=1, small=0.6, up=False, skip=("up", "down", "south")),
        box(0, 12.2, -17.0, 7.2, 2.0, 4.0, "fur", r="fur_l", **lfur),
        # icicles frozen in the fur of its chin
        *[c for x, h in ((-1.6, 1.3), (-0.2, 1.8), (1.3, 1.1)) for c in (
            B.c([x - 0.3, 11.2 - h, -18.2], [0.6, h, 0.6], "ice", glint=False),
            B.c([x - 0.15, 11.2 - h - 0.8, -18.05], [0.3, 0.8, 0.3], "ice", glint=False))],
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        # the forelegs: heavy shoulders, thick arms feathered with long fur behind, paws with long black claws
        B.bone(f"{side}_front", "body", [sx * 5.2, 15, -8], [
            col(sx * 5.4, -8.2, 6.0, 17.0, 5.4, 6.4, "fur", r="fur", zone="limb", **lfur,
                lobes=[dict(faces=("west", "east", "north"), a=0.0, h=0.78, ra=0.8, rh=0.2, k=0.08, rim=0.1)]),
            box(sx * 5.4, 10.4, -4.75, 4.4, 5.6, 0.5, "hair", seed=43 + (sx > 0), **hang)])
        B.bone(f"{side}_front_low", f"{side}_front", [sx * 5.4, 7.0, -8], [
            col(sx * 5.4, -8.2, 1.6, 7.4, 4.8, 5.2, "fur", r="fur", zone="limb", **lfur),
            box(sx * 5.4, 1.2, -9.4, 5.6, 2.4, 6.6, "skin", r="paw", seed=62,
                grooves=[dict(faces=("north",), pts=[(x, 0.0), (x, 0.75)], w=0.9, k=0.2, lip=0.05) for x in (-0.33, 0.33)]),
            *[B.c([sx * 5.4 - 2.4 + i * 1.7, 0.0, -14.0], [0.9, 1.6, 2.6], "horn", r="claw", tip="-z") for i in range(3)],
        ])
        B.bone(f"{side}_hind", "body", [sx * 4.6, 14, 8], [
            col(sx * 4.8, 8.6, 6.0, 15.6, 5.4, 7.0, "fur", r="fur", zone="limb", **lfur)])
        B.bone(f"{side}_hind_low", f"{side}_hind", [sx * 4.8, 6.6, 8.4], [
            col(sx * 4.8, 8.6, 1.4, 7.0, 4.4, 5.0, "fur", r="fur", zone="limb", **lfur),
            box(sx * 4.8, 1.1, 7.6, 5.2, 2.2, 6.4, "skin", r="paw", seed=63,
                grooves=[dict(faces=("north",), pts=[(x, 0.0), (x, 0.75)], w=0.9, k=0.2, lip=0.05) for x in (-0.33, 0.33)]),
            *[B.c([sx * 4.8 - 2.2 + i * 1.6, 0.0, 3.4], [0.8, 1.2, 1.8], "horn", r="claw", tip="-z") for i in range(3)],
        ])
    B.bone("tail", "body", [0, 16.6, 11.5], [box(0, 16.4, 12.6, 3.6, 3.2, 2.6, "fur", r="fur", **lfur)], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (130, 220, 255), {
        "fur": (78, 92, 112), "fur_d": (28, 34, 50), "fur_l": (196, 212, 224), "frost": (214, 234, 248),
        "muzzle": (108, 116, 130), "muzzle_l": (170, 178, 190), "paw": (34, 38, 48), "scar": (176, 150, 152), "gum": (100, 36, 46),
        "ice": (150, 196, 226),
        "claw": [(2, 4, 8), (10, 16, 28), (30, 44, 70), (90, 120, 160), (180, 210, 240)],
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
    """A demon bull from the pits: a hulking slab of charred black-red hide split by long molten cracks, plates of
    obsidian over its shoulders and back glowing at their seams, a low armoured head under great ridged horns, burning
    eyes leaking fire, glowing nostrils over a torn iron nose ring, boar's tusks and a mouth full of fire, broken
    shackles still locked on its forelegs, shaggy fetlocks over cloven hooves, a spiked tail."""
    name = "pit_ravager"
    B = Body(name, 2800)
    box, col = B.box, B.col
    sk = dict(seed=70, mottle=0.14, fat=0.1, blotch=0.3, lava=dict(area=26.0, chance=0.75))
    obs = dict(r="obsidian", rivets=False, dents=False, grime=False, embers=0.6)
    hoof = dict(r="hoof", tip="-y", grooves=[dict(faces=("north",), pts=[(0.0, 0.0), (0.0, 0.95)], w=1.1, k=0.3, lip=0.0)])
    tuft = dict(ragged=0.8, len=1.8, width=0.9, skip=("up", "down"))
    B.bone("root", None, [0, 0, 0])
    spines = [B.c([-0.8, 23.4 - i * 0.4, -10.0 + i * 3.6], [1.6, 3.6 - i * 0.3, 1.8], "horn", r="horn", tip="+y", ridged=3.0,
                  pivot=[0, 23.4, -9.2 + i * 3.6], rot=[-30, 0, 0], skip=("down",)) for i in range(3, 6)]
    B.bone("body", "root", [0, 16, 0], [
        box(0, 17.6, -6.0, 15.0, 13.6, 13.0, "skin", zone="back", split=(0.55, 0.25), **sk,
            lobes=[dict(a=-0.42, h=0.6, ra=0.46, rh=0.36, k=0.14, rim=0.14), dict(faces=("north",), a=0.5, h=0.45, ra=0.38, rh=0.3, k=0.12, sym=True)],
            grooves=[dict(pts=[(0.0, 0.64), (0.14, 0.42), (0.16, 0.2)], w=1.2, k=0.09, lip=0.05),
                     dict(pts=[(0.3, 0.62), (0.42, 0.42), (0.44, 0.22)], w=1.2, k=0.08, lip=0.04),
                     dict(pts=[(0.6, 0.6), (0.68, 0.42), (0.7, 0.24)], w=1.2, k=0.07, lip=0.04),
                     dict(faces=("north",), pts=[(0.0, 0.85), (0.0, 0.1)], w=1.2, k=0.1, lip=0.0)]),
        box(0, 17.0, 7.0, 13.0, 11.6, 12.0, "skin", zone="back", split=(0.55, 0.25), **sk,
            lobes=[dict(a=0.25, h=0.55, ra=0.55, rh=0.4, k=0.14, rim=0.14)]),
        box(0, 23.8, -7.0, 13.0, 2.0, 9.0, "skin", r="skin_d", **sk),
        # a shaggy black mane over the hump and the shoulders, as a bison's
        box(0, 19.5, -6.8, 15.8, 8.0, 12.2, "mane", seed=84, ragged=1.5),
        box(0, 23.7, -7.4, 12.2, 4.6, 9.8, "mane", seed=88, ragged=1.2, pivot=[0, 22.0, -7.4], rot=[8, 0, 0]),
        # plates of obsidian over the back and the haunches, fire showing at their seams
        *[box(sx * 4.1, 23.5, 4.2, 6.4, 1.4, 6.6, "plate", seed=78 + (sx > 0), **obs, pivot=[sx * 0.9, 23.5, 4.2], rot=[0, 0, sx * 12])
          for sx in (-1, 1)],
        # long hair hanging from the chest between the forelegs
        box(0, 12.6, -12.85, 5.6, 6.0, 0.5, "mane", seed=85, ragged=1.2, skip=("up", "down")),
        *spines,
    ])
    horns = []
    for sx in (-1, 1):
        horns += curl(B, [sx * 3.8, 21.8, -14.6], sx, [(70, 3.4, 3.2), (95, 3.0, 2.7), (130, 2.6, 2.2), (170, 2.2, 1.7), (210, 2.0, 1.3), (240, 1.6, 0.9)],
                      "horn", r="horn", tip="+y", z_step=-0.9, ridged=11.0)
    B.bone("head", "body", [0, 19, -12], [
        box(0, 18.4, -17.0, 9.6, 8.6, 9.0, "skin", zone="back", split=(0.6, 0.25), seed=71, mottle=0.12, fat=0.08, blotch=0.25,
            eyes=eyes(2.4, 20.0, (1.15, 0.45), slant=0.5, pupil=None, lid=1.4),
            # fire leaking from the eyes and running back along the face
            lines=[dict(pts=[(-21.4, 20.2), (-19.8, 20.9), (-18.2, 21.2), (-16.6, 21.9)], w=0.32, halo=0.7, faces=("west", "east")),
                   dict(pts=[(-21.4, 19.7), (-20.0, 19.6), (-18.6, 19.9)], w=0.22, halo=0.5, faces=("west", "east"))],
            lava=dict(area=14.0, chance=0.6, seed=43)),
        box(0, 21.4, -21.0, 10.0, 1.8, 1.6, "skin", r="skin_d", seed=71, mottle=0.1, fat=0.06, blotch=0.2,
            grooves=[dict(faces=("north",), pts=[(-0.14, 0.05), (-0.04, 0.95)], w=1.0, k=0.18, lip=0.0, sym=True)]),
        # the boss of horn across its brow that joins its horns, as a buffalo's
        box(0, 22.5, -15.4, 9.2, 2.2, 4.6, "horn", r="horn", tip="+y", kr=(0.0, 0.3), ridged=4.0, skip=("down",)),
        box(0, 16.2, -23.0, 7.4, 5.4, 4.0, "skin", r="skin_d", zone="back", seed=72, mottle=0.12, fat=0.08, blotch=0.2,
            sockets=[dict(x=sx * 1.45, y=17.3, r=(0.75, 0.5), core=0.42, slant=sx * -0.3) for sx in (-1, 1)],
            grooves=[dict(faces=("north",), pts=[(0.42, 0.42), (0.75, 0.2), (0.9, 0.05)], w=1.0, k=0.14, lip=0.06, sym=True),
                     dict(faces=("up",), pts=[(-0.6, 0.35), (0.0, 0.3), (0.6, 0.35)], w=1.0, k=0.14, lip=0.06),
                     dict(faces=("up",), pts=[(-0.6, 0.55), (0.0, 0.5), (0.6, 0.55)], w=1.0, k=0.12, lip=0.05)],
            cuts=[dict(pts=[(-3.7, 13.9), (3.7, 13.9)], w=0.45, faces=("north",)),
                  dict(pts=[(-25.0, 13.9), (-21.0, 14.3)], w=0.45, faces=("west", "east"))],
            paint_faces=[dict(faces=("down",), ramp="hot", tone=0.32)]),
        # a torn iron ring through its nose
        col(-1.0, -25.25, 14.4, 16.9, 0.45, 0.45, "plate", r="iron", rivets=False, dents=False, grime=False),
        col(1.0, -25.25, 14.4, 16.9, 0.45, 0.45, "plate", r="iron", rivets=False, dents=False, grime=False),
        box(0, 14.2, -25.25, 2.45, 0.45, 0.45, "plate", r="iron", rivets=False, dents=False, grime=False),
        box(0, 12.85, -24.6, 6.8, 1.3, 0.5, "teeth", n=7, fangs=1, small=0.55, up=True, skip=("up", "down", "south")),
        *horns,
    ], rot=[14, 0, 0])
    B.bone("jaw", "head", [0, 14.6, -16], [
        box(0, 12.4, -20.7, 7.0, 2.0, 7.0, "skin", r="skin_d", seed=73, mottle=0.12, fat=0.08, blotch=0.2,
            paint_faces=[dict(faces=("up",), ramp="hot", tone=0.3)],
            cuts=[dict(pts=[(-3.5, 13.2), (3.5, 13.2)], w=0.4, faces=("north",))]),
        box(0, 13.7, -23.9, 6.0, 0.7, 0.4, "teeth", n=6, fangs=1, small=0.6, up=False, skip=("up", "down", "south")),
        # a beard of black hair hanging from its chin
        box(0, 10.2, -20.4, 4.6, 3.2, 4.4, "mane", seed=86, ragged=1.0, len=2.2, width=0.9),
        # boar's tusks curving up and out from the corners of the jaw
        *[c for sx in (-1, 1) for c in (
            B.c([sx * 4.1 - 0.65, 12.0, -24.0], [1.3, 3.4, 1.3], "horn", r="tusk", tip="+y", kr=(0.0, 0.6), pivot=[sx * 4.1, 12.4, -23.4],
                rot=[-10, 0, sx * 16], skip=("down",)),
            B.c([sx * 4.1 - 0.4, 15.2, -24.0 + 0.25], [0.8, 2.2, 0.8], "horn", r="tusk", tip="+y", kr=(0.6, 1.0), pivot=[sx * 4.1, 12.4, -23.4],
                rot=[-24, 0, sx * 26], skip=("down",)))],
    ], rot=[6, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_front", "body", [sx * 5.6, 16, -9], [
            col(sx * 5.8, -9.0, 7.6, 18.0, 5.6, 6.6, "skin", zone="limb", **sk,
                lobes=[dict(faces=("west", "east", "north"), a=0.0, h=0.72, ra=0.85, rh=0.26, k=0.12, rim=0.14),
                       dict(faces=("west", "east"), a=-0.2, h=0.32, ra=0.5, rh=0.2, k=0.08, rim=0.1)]),
            # the mane runs down the front of the forelegs
            box(sx * 5.8, 13.2, -12.55, 5.8, 6.2, 0.5, "mane", seed=87 + (sx > 0), ragged=1.2, skip=("up", "down"))])
        B.bone(f"{side}_front_low", f"{side}_front", [sx * 5.8, 8.0, -9], [
            col(sx * 5.8, -9.0, 2.4, 8.4, 4.0, 4.4, "skin", zone="limb", **sk),
            # a broken shackle still locked above the hoof, a few links of its chain hanging from it
            col(sx * 5.8, -9.0, 5.0, 6.6, 4.6, 5.0, "plate", r="iron", rivets=False, dents=False),
            col(sx * 8.2, -9.0, 2.2, 5.4, 0.5, 0.5, "chain", r="rust"),
            col(sx * 5.8, -9.1, 1.8, 4.6, 4.6, 5.0, "mane", seed=80 + (sx > 0), **tuft),
            col(sx * 5.8, -9.4, 0.0, 2.6, 4.6, 5.0, "horn", **hoof),
        ])
        B.bone(f"{side}_hind", "body", [sx * 5.0, 16, 8], [
            box(sx * 5.2, 12.6, 8.4, 5.4, 9.0, 7.4, "skin", zone="limb", **sk,
                lobes=[dict(faces=("west", "east"), a=0.1, h=0.66, ra=0.8, rh=0.32, k=0.12, rim=0.14)])], rot=[-14, 0, 0])
        B.bone(f"{side}_hind_low", f"{side}_hind", [sx * 5.2, 8.6, 9.6], [
            col(sx * 5.2, 9.8, 2.4, 9.0, 3.6, 4.0, "skin", zone="limb", **sk),
            col(sx * 5.2, 9.7, 1.8, 4.4, 4.2, 4.6, "mane", seed=82 + (sx > 0), **tuft),
            col(sx * 5.2, 9.6, 0.0, 2.6, 4.4, 4.8, "horn", **hoof),
        ], rot=[14, 0, 0])
    B.bone("tail", "body", [0, 20, 12.6], [B.bar(0, 20, 12.6, 18.0, 2.4, 2.4, "skin", r="skin_d", seed=74, mottle=0.12, fat=0.08)], rot=[-20, 0, 0])
    B.bone("tail2", "tail", [0, 20, 18.0], [
        B.bar(0, 20, 18.0, 23.0, 1.6, 1.6, "skin", r="skin_d", seed=75, mottle=0.12, fat=0.08),
        B.c([-0.5, 21.0, 19.0], [1.0, 2.2, 1.2], "horn", r="horn", tip="+y", skip=("down",)),
        B.c([-0.5, 21.0, 21.2], [1.0, 1.8, 1.0], "horn", r="horn", tip="+y", skip=("down",)),
        B.c([-1.6, 19.4, 22.6], [3.2, 1.2, 3.2], "horn", r="horn", tip="+z"),
    ], rot=[30, 0, 0])
    register(name, lambda aff: pal(aff, (255, 110, 30), {
        "skin": (74, 26, 26), "skin_d": (24, 11, 13), "skin_l": (132, 62, 46),
        "horn": (44, 34, 32), "tusk": (206, 190, 158), "hoof": (24, 20, 22), "hair": (36, 22, 20),
        "obsidian": [(2, 2, 4), (8, 6, 10), (22, 18, 24), (58, 46, 58), (126, 108, 122)],
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
    """A great serpent that rears to strike: coils of black-green scales banded with gold chevrons edged in black over
    a pale belly of broad plates, a wide hood pale in front with dark bars across it and a glowing spectacle on its
    back, a wedge of a head under heavy brow scales, gold slit eyes in a black mask, two long fangs and a forked
    tongue."""
    name = "coilfang"
    B = Body(name, 2900)
    box = B.box
    sc = dict(size=1.0, seed=36, scale_k=0.55, seam_k=0.75, ventral=True)
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
    # the hood, spread behind the upright upper neck (whose rest +y faces back and -z points up): a broad middle
    # with a spectacle of light on its back, and a flap to each side angled forward, all of it pale in front with
    # dark bars across it
    bars = [dict(pts=[(-7.0, zz), (7.0, zz)], w=0.75, ramp="hood_d", tone=0.3, faces=("down",)) for zz in (-9.6, -7.2)]
    hsc = {**sc, "ventral": False}
    # the spectacle: two rings of light joined by a sagging bridge (on the hood's back x runs across, -z up)
    ring = [[(sx * 1.6 + 0.95 * math.cos(t * math.pi / 6), -9.2 + 0.8 * math.sin(t * math.pi / 6)) for t in range(13)] for sx in (-1, 1)]
    spectacle = [dict(pts=r_, w=0.34, halo=0.55, faces=("up",)) for r_ in ring] + [
        dict(pts=[(-0.66, -9.0), (-0.3, -8.62), (0.3, -8.62), (0.66, -9.0)], w=0.3, halo=0.5, faces=("up",))]
    hood = [B.c([-3.3, 3.4, -11.8], [6.6, 0.6, 8.0], "scales", r="hood", zone="back", **hsc, facepaint=bars, lines=spectacle,
                holes=[])]
    for sx in (-1, 1):
        hood.append(B.c(mirror_x([2.9, 3.35, -11.0], [4.0, 0.5, 6.6], sx), [4.0, 0.5, 6.6], "scales", r="hood", zone="back", **hsc,
                        facepaint=bars, pivot=[sx * 3.0, 3.6, -7.6], rot=[0, 0, sx * 16]))
    B.bone("neck2", "neck", [0, 2.2, -4.4], [
        B.bar(0, 2.2, -9.4, -4.0, 3.8, 3.4, "scales", zone="back", split=(0.55, 0.32), dir=-1, **sc, **bands),
        *hood,
    ], rot=[-32, 0, 0])
    mask = [dict(pts=[(-16.8, 2.7), (-12.6, 3.0), (-9.2, 2.1)], w=1.15, ramp="black", tone=0.14, faces=("west", "east"))]
    eye_s = [dict(x=-12.5, y=3.0, r=(0.8, 0.52), face=fc, slant=0.32, pupil="slit") for fc in ("west", "east")]
    mouth = [dict(faces=("down",), ramp="mouth", tone=0.3)]
    hs = dict(zone="back", split=(0.42, 0.28), **sc)
    B.bone("head", "neck2", [0, 2.2, -9.4], [
        # a wedge: broad over the hinge of its jaws, narrowing to the snout
        box(0, 2.6, -11.4, 5.4, 2.8, 4.4, "scales", **hs, facepaint=mask, eyes=eye_s, paint_faces=mouth,
            grooves=[dict(faces=("up",), pts=[(0.0, 0.0), (0.0, 0.62)], w=0.9, k=0.16, lip=0.05),
                     dict(faces=("up",), pts=[(-0.55, 0.32), (0.55, 0.32)], w=0.9, k=0.14, lip=0.05)]),
        box(0, 2.45, -14.4, 4.2, 2.5, 1.8, "scales", **hs, facepaint=mask, paint_faces=mouth,
            grooves=[dict(faces=("up",), pts=[(0.0, 0.0), (0.0, 1.0)], w=0.9, k=0.14, lip=0.05)]),
        box(0, 2.25, -16.0, 3.0, 2.1, 1.6, "scales", **hs, facepaint=mask, paint_faces=mouth,
            holes=[dict(x=0.65, y=2.75, r=(0.28, 0.2)), dict(x=-0.65, y=2.75, r=(0.28, 0.2))]),
        # heavy brow scales over the eyes, low at the front and inside: a scowl
        *[box(sx * 1.75, 4.05, -12.8, 1.9, 0.7, 3.6, "scales", r="scale_d", **sc, pivot=[sx * 1.75, 4.05, -11.0], rot=[7, 0, sx * -10])
          for sx in (-1, 1)],
        *[B.c([sx * 1.0 - 0.25, -0.7, -16.5], [0.5, 2.1, 0.5], "horn", r="fang", tip="-y", pivot=[sx * 1.0, 1.2, -16.3], rot=[14, 0, 0])
          for sx in (-1, 1)],
    ], rot=[88, 0, 0])
    B.bone("jaw", "head", [0, 1.2, -10.0], [
        box(0, 0.75, -12.8, 3.8, 0.9, 6.4, "scales", r="scale_l", **{**sc, "ventral": False}, paint_faces=[dict(faces=("up",), ramp="mouth", tone=0.36)]),
        B.c([-0.2, 0.6, -18.4], [0.4, 0.3, 2.4], "skin", r="tongue", marks=0.0, seed=81),
        *[B.c([sx * 0.3 - 0.12, 0.62, -19.3], [0.24, 0.26, 1.0], "skin", r="tongue", marks=0.0, seed=82, pivot=[sx * 0.1, 0.7, -18.4], rot=[0, sx * 18, 0])
          for sx in (-1, 1)],
    ], rot=[4, 0, 0])
    register(name, lambda aff: pal(aff, (255, 206, 60), {
        "scale": (36, 44, 32), "scale_d": (12, 16, 12), "scale_l": (206, 194, 148),
        "hood": (30, 36, 26), "hood_d": (14, 16, 12), "hood_l": (198, 184, 136), "band": (204, 156, 52),
        "horn": (40, 36, 28), "fang": (236, 230, 210), "tongue": (150, 30, 44),
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
    """Gold chevrons across a serpent's back every few scales, edged in black, ragged at their edges, the scales still
    showing through them, fading out down the sides before the belly."""
    if f.face == "down":
        return
    coord = f.Z + 0.8 * np.abs(f.X)
    k = np.floor(coord / 3.2)
    c = (k + 0.5) * 3.2
    d = np.abs(coord - c)
    half = 0.5 + 0.2 * CS.jag(f.X * 2 + f.Y, 9, 0.6)
    on = d < half
    rim = (d >= half) & (d < half + 0.32)
    if f.wall:
        low = f.Y > f.bottom + 0.35 * (f.top - f.bottom)
        on &= low
        rim &= low
    n = CS.fbm3(f.X * 2.2, f.Y * 2.2, f.Z * 2.2, 13, 2)
    p.over("black", 0.12, rim)
    p.over("band", 0.4 + (n - 0.5) * 0.3 + 0.12 * (d < half * 0.45), on)


# --------------------------------------------------------------------------------------------- cragcrawler

def cragcrawler():
    """A crab the size of a boulder: a shell of stone with moss on it and crystals growing out of it, eyes of light on
    stalks, six jointed legs, and two claws, one far bigger than the other."""
    name = "cragcrawler"
    B = Body(name, 3000)
    box, col = B.box, B.col
    rk = dict(seed=101, moss=0.5, cell=4.4)
    cs = dict(r="claw", gloss=0.22, stria=0, sheen_r="claw_l", sheen_k=0.3)
    # crab spines along the front rim of the shell and down its sides
    spines = [B.c([x - 0.4, 10.2, -7.2], [0.8, 0.9, 1.6], "horn", r="tip", tip="-z", pivot=[x, 10.6, -6.4], rot=[-14, 0, 0]) for x in (-6.0, -3.0, 3.0, 6.0)]
    spines += [B.c(mirror_x([9.0, 9.8, z - 0.4], [1.4, 0.8, 0.8], sx), [1.4, 0.8, 0.8], "horn", r="tip", tip="+x" if sx > 0 else "-x")
               for sx in (-1, 1) for z in (-4.6, -1.0)]
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
        *spines,
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
            B.c(mirror_x([6.0, 6.6, -9.6], [2.4 * k, 2.4 * k, 4.0], sx), [2.4 * k, 2.4 * k, 4.0], "elytra", **cs, seed=105),
            B.c(mirror_x([6.0 + 0.8 * k, 6.6 + 2.4 * k, -8.6], [0.7, 0.9, 0.7], sx), [0.7, 0.9, 0.7], "horn", r="tip", tip="+y"),
        ], rot=[0, sx * 30, 0])
        B.bone(f"{side}_pincer", f"{side}_claw", [sx * 7.0, 7.8, -9.6], [
            B.c(mirror_x([5.6, 5.8, -15.0], [3.4 * k, 3.4 * k, 5.6 * k], sx), [3.4 * k, 3.4 * k, 5.6 * k], "elytra", **cs, seed=106),
            # knobs along the top of the claw
            *[B.c(mirror_x([5.6 + 1.0 * k, 5.8 + 3.4 * k, -15.0 + (1.0 + 1.6 * i) * k], [0.8 * k, 0.6 * k, 0.8 * k], sx), [0.8 * k, 0.6 * k, 0.8 * k],
                  "horn", r="tip", tip="+y") for i in range(3)],
            B.c(mirror_x([5.9, 5.8 + 0.6 * k, -15.0 - 3.6 * k], [1.2 * k, 1.6 * k, 3.8 * k], sx), [1.2 * k, 1.6 * k, 3.8 * k], "horn", r="tip", tip="-z"),
        ])
        B.bone(f"{side}_nipper", f"{side}_pincer", [sx * 7.0, 6.0, -14.6], [
            B.c(mirror_x([7.4 if not big else 7.9, 5.4, -15.4 - 3.8 * k], [1.0 * k, 1.2 * k, 4.2 * k], sx), [1.0 * k, 1.2 * k, 4.2 * k], "horn", r="tip", tip="-z"),
        ], rot=[0, sx * -16, 0])
        for j, z in enumerate((-3.4, 0.8, 5.0)):
            B.bone(f"{side}_leg{j}", "body", [sx * 8.6, 7.4, z], [
                B.c(mirror_x([8.4, 6.6, z - 0.9], [5.4, 1.6, 1.8], sx), [5.4, 1.6, 1.8], "elytra", **cs, seed=107 + j),
                B.c(mirror_x([13.0, 0.0, z - 0.7], [1.4, 7.0, 1.4], sx), [1.4, 7.0, 1.4], "elytra", **cs, seed=110 + j),
                B.c(mirror_x([12.6, 6.6, z - 0.3], [0.6, 1.0, 0.6], sx), [0.6, 1.0, 0.6], "horn", r="tip", tip="+y"),
                B.c(mirror_x([13.2, -0.6, z - 0.4], [1.0, 1.0, 0.8], sx), [1.0, 1.0, 0.8], "horn", r="tip", tip="-y"),
            ], rot=[0, sx * (j - 1) * -18, sx * -18])
    register(name, lambda aff: pal(aff, (255, 150, 60), {
        "rock": (98, 92, 86), "shell": [(10, 8, 8), (36, 30, 30), (78, 66, 62), (130, 112, 102), (190, 172, 156)],
        "belly": (156, 120, 98), "tip": (30, 26, 26), "claw": (104, 50, 40),
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

def saw_edge(f, p, s):
    """A mantis's scythe: the side facing the other half of the leg is a pale, hard cutting edge, saw-toothed where it
    meets the dark chitin, glossy down its length."""
    if not f.wall:
        return
    r = s.get("edge_r", "edge")
    per = s.get("saw", 0.9)
    tri = np.abs((f.Y / per) % 1.0 - 0.5) * 2.0                         # 0..1 sawtooth along the blade
    if f.side:
        fu = f.U if f.face == "west" else 1 - f.U                        # 0 at the -z side on both sides
        e = fu * f.s[2]
        p.over(r, 0.62 + 0.25 * (e < f.texel * 1.2), e < 0.35 + 0.35 * tri)
        p.over("black", 0.1, (e >= 0.35 + 0.35 * tri) & (e < 0.35 + 0.35 * tri + f.texel))
    elif f.face == "north":
        p.over(r, 0.7 - 0.25 * np.abs(f.U - 0.5), 1.0)
        p.over(r, 0.2, (tri < 0.22) & (np.abs(f.U - 0.5) < 0.3))


def eyespot(f, p, s):
    """A false eye on a folded wing, glowing: a dark ring, an iris of light and a black pupil (s['spot'] = u, v, r in
    the wing's own terms), drawn after the wing's veins."""
    insect_wing(f, p, s)
    if f.face != "up" or "spot" not in s:
        return
    su, sv, sr = s["spot"]
    L, C = f.s[2], f.s[0]
    u = (f.Z - f.o[2]) / L
    out = 1 if f.cx > 0 else -1
    xo = (f.X - f.o[0]) / C
    v = 1 - xo if out > 0 else xo
    d = np.hypot((u - su) * L, (v - sv) * C) / sr
    p.over("black", 0.06, d < 1.25)
    p.glow("glow", 0.42 + 0.4 * (1 - d), (d < 1.0) & (d >= 0.42))
    p.over("black", 0.0, d < 0.42)
    p.glow("glow", 0.98, (np.abs((u - su) * L + sr * 0.22) < f.texel * 0.7) & (np.abs((v - sv) * C - sr * 0.22) < f.texel * 0.7))


def hive_reaver():
    """The hive's warrior: a mantis as tall as a man. A triangular head with two great compound eyes at its corners
    glowing like coals, long feelers and small hooked jaws; a long neck plate with a spined keel; forelegs folded into
    scythes, the femur's row of spines locked against the pale saw-toothed blade; four long jointed legs; leathery
    wings folded over its abdomen, each with a false eye that glows when you see it from behind; dark green-black
    glossy chitin with lime bands."""
    name = "hive_reaver"
    B = Body(name, 3100)
    box, col, bar = B.box, B.col, B.bar
    ck = dict(r="chitin", hair=0.3)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 13, 0], [
        box(0, 13.3, -0.4, 4.4, 3.8, 3.8, "segment", axis="z", seed=120, **ck),
        box(0, 13.1, 2.0, 4.8, 3.6, 2.6, "segment", axis="z", r2="lime", band=(0.6, 0.95), seed=121, **{**ck, "hair": 0}),
        # the leathery forewings folded over the abdomen, a false eye on each
        *[B.c(mirror_x([0.0, 15.0, 1.6], [3.0, 0.4, 12.6], sx), [3.0, 0.4, 12.6], "skin", r="tegmen", marks=0.0, seed=122 + (sx > 0),
              pivot=[0, 15.2, 1.6], rot=[-15, 0, sx * -10], fx=eyespot, wing=dict(span="z", lead="out", tip_dark=0.1),
              spot=(0.62, 0.5, 0.95), skip=("north", "south", "east", "west")) for sx in (-1, 1)],
    ])
    segs = [(2.0, 5.0, 5.0, 4.4, 12.8), (4.6, 7.8, 5.4, 4.6, 12.7), (7.4, 10.4, 5.0, 4.2, 12.6), (10.0, 12.8, 4.4, 3.6, 12.5),
            (12.4, 14.8, 3.6, 3.0, 12.4), (14.4, 16.4, 2.6, 2.2, 12.3)]
    B.bone("abdomen", "body", [0, 13, 2.4], [
        *[bar(0, y, z0, z1, w, h, "segment", r="chitin", r2="lime", band=(0.72, 0.95), wave=0.03, seed=124 + i, hair=0.2)
          for i, (z0, z1, w, h, y) in enumerate(segs)],
        *[B.c(mirror_x([0.5, 11.8, 16.2], [0.4, 0.4, 2.2], sx), [0.4, 0.4, 2.2], "horn", r="spine", tip="+z",
              pivot=[sx * 0.7, 12.0, 16.2], rot=[-10, sx * -16, 0]) for sx in (-1, 1)],
    ], rot=[-18, 0, 0])
    keel = [B.c([-0.2, y, 0.2], [0.4, 0.5, 0.7 + 0.3 * (i % 2)], "horn", r="chitin", tip="+z", pivot=[0, y + 0.25, 0.2], rot=[-24, 0, 0])
            for i, y in enumerate((16.8, 19.0, 21.2, 23.2))]
    B.bone("thorax", "body", [0, 14.6, -1.0], [
        col(0, -1.0, 14.6, 25.0, 2.6, 2.8, "segment", axis="y", seed=127, **ck),
        col(0, -1.0, 21.4, 25.4, 3.8, 3.6, "segment", axis="y", r2="lime", band=(0.0, 0.18), seed=128, **ck),
        *keel,
    ], rot=[14, 0, 0])
    B.bone("head", "thorax", [0, 26.0, -1.0], [
        # a triangle: wide across the eyes, narrowing to the jaws
        box(0, 28.6, -1.8, 5.2, 2.2, 2.6, "segment", axis="y", seed=130, hair=0, gloss=0.7, r="chitin",
            facepaint=[dict(pts=[(-0.9, 29.6), (0.0, 28.4), (0.9, 29.6)], w=0.45, ramp="lime", tone=0.55)]),
        box(0, 26.8, -2.0, 3.6, 1.6, 2.2, "segment", axis="y", seed=131, hair=0, gloss=0.6, r="chitin",
            facepaint=[dict(pts=[(-1.2, 27.6), (-0.6, 26.2)], w=0.4, ramp="lime", tone=0.4),
                       dict(pts=[(1.2, 27.6), (0.6, 26.2)], w=0.4, ramp="lime", tone=0.4)]),
        box(0, 25.4, -2.3, 2.2, 1.4, 1.8, "segment", axis="y", r="lime", seed=132, hair=0, gloss=0.5),
        *[B.c(mirror_x([1.7, 26.9, -3.6], [2.1, 2.8, 2.8], sx), [2.1, 2.8, 2.8], "compound", r="eye", seed=133,
              pivot=[sx * 2.4, 28.2, -2.2], rot=[0, sx * -8, sx * -12]) for sx in (-1, 1)],
        *[B.c(mirror_x([0.3, 23.9, -3.0], [0.6, 1.6, 0.6], sx), [0.6, 1.6, 0.6], "horn", r="spine", tip="-y",
              pivot=[sx * 0.6, 24.9, -2.7], rot=[-14, 0, sx * 20]) for sx in (-1, 1)],
        *[c for sx in (-1, 1) for c in limb(B, [sx * 0.5, 29.6, -2.6], [
            (sx * 1.0, 31.8, -4.0, 0.34), (sx * 2.2, 33.8, -4.6, 0.3), (sx * 4.0, 34.8, -3.8, 0.26), (sx * 5.6, 34.6, -2.0, 0.24)],
            "segment", r="chitin", hair=0, gloss=0.0)],
    ], rot=[-14, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        inner = ("east",) if sx > 0 else ("west",)
        # the scythes: the femur held forward and down with its row of spines underneath, the blade folded up
        # against it, its spines locked between them
        B.bone(f"{side}_arm", "thorax", [sx * 1.8, 23.6, -1.4], [
            B.c(mirror_x([1.1, 20.6, -2.6], [1.6, 3.4, 2.2], sx), [1.6, 3.4, 2.2], "segment", axis="y", seed=134, **ck),
            B.c(mirror_x([1.4, 14.6, -2.5], [1.4, 7.0, 2.0], sx), [1.4, 7.0, 2.0], "segment", axis="y", seed=135, r2="lime",
                band=(0.0, 0.14), paint_faces=[dict(faces=inner, ramp="lime", tone=0.42, seed=136)], **ck),
            *[B.c(mirror_x([1.65 + 0.15 * (i % 2), 15.2 + i * 1.2, -0.6], [0.5, 0.5, 1.0 + 0.5 * (i % 2)], sx), [0.5, 0.5, 1.0 + 0.5 * (i % 2)],
                  "horn", r="spine", tip="+z") for i in range(6)],
        ], rot=[-62, 0, sx * -6])
        B.bone(f"{side}_scythe", f"{side}_arm", [sx * 2.2, 15.6, -1.4], [
            B.c(mirror_x([1.75, 15.4, -0.4], [0.9, 8.4, 1.8], sx), [0.9, 8.4, 1.8], "segment", axis="y", seed=137, fx=saw_edge, **ck),
            *[B.c(mirror_x([1.95, 16.6 + i * 1.3, -1.0], [0.5, 0.45, 0.8], sx), [0.5, 0.45, 0.8], "horn", r="edge", tip="-z") for i in range(5)],
            B.c(mirror_x([1.8, 23.6, -0.9], [0.8, 2.8, 1.2], sx), [0.8, 2.8, 1.2], "horn", r="spine", tip="+y",
                pivot=[sx * 2.2, 23.8, -0.3], rot=[-34, 0, 0]),
        ], rot=[-10, 0, 0])
        for j, (z, spread) in enumerate(((-1.2, -26), (1.6, 20))):
            B.bone(f"{side}_leg{j}", "body", [sx * 2.2, 12.6, z], [
                B.c(mirror_x([2.0, 12.2, z - 0.45], [6.0, 0.9, 0.9], sx), [6.0, 0.9, 0.9], "segment", axis="z", seed=138 + j, r2="lime",
                    band=None, **ck),
                B.c(mirror_x([7.4, 12.0, z - 0.6], [1.0, 1.2, 1.2], sx), [1.0, 1.2, 1.2], "segment", r="lime", seed=140 + j, hair=0),
            ], rot=[0, sx * spread, sx * 34])
            B.bone(f"{side}_shin{j}", f"{side}_leg{j}", [sx * 7.8, 12.6, z], [
                B.c(mirror_x([7.45, 1.4, z - 0.35], [0.7, 11.4, 0.7], sx), [0.7, 11.4, 0.7], "segment", axis="y", seed=142 + j, **ck),
                *[B.c(mirror_x([7.8, 3.0 + i * 3.0, z - 0.2], [0.6, 0.35, 0.35], sx), [0.6, 0.35, 0.35], "horn", r="spine",
                      tip="+x" if sx > 0 else "-x") for i in range(3)],
                B.c(mirror_x([7.3, 0.0, z - 0.4], [1.0, 1.4, 0.8], sx), [1.0, 1.4, 0.8], "segment", axis="y", r="lime", seed=144 + j, hair=0),
            ], rot=[0, 0, sx * -34])
    register(name, lambda aff: pal(aff, (255, 60, 110), {
        "chitin": [(3, 6, 4), (10, 20, 12), (26, 44, 26), (66, 96, 52), (150, 186, 110)],
        "lime": aff_mix(aff, (150, 196, 62), 0.7),
        "tegmen": (60, 82, 44),
        "eye": [(4, 1, 2), (24, 4, 10), (64, 10, 26), (130, 40, 60), (220, 140, 160)],
        "edge": [(40, 34, 26), (110, 100, 80), (186, 176, 150), (226, 220, 200), (252, 250, 240)],
        "spine": [(4, 6, 4), (20, 26, 16), (60, 70, 44), (150, 160, 110), (226, 230, 196)],
        "wingskin": (60, 82, 44),
    }, tinted=("chitin", "tegmen", "eye", "spine", "wingskin")), density=5.0)
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
    """A colossus of rune-cut stone three blocks tall: a hunched mountain of a body in great mossy slabs, channels of
    light carved through it (a ring round the crystal heart in its chest and lines running out from it to the
    shoulders and hips, down the arms and across the fists), bands of old gold rune-cut round its arms and brow, a
    small head sunk between boulder shoulders with one eye, and arms so long its fists rest on the ground."""
    name = "rune_colossus"
    B = Body(name, 3200)
    box, col = B.box, B.col
    rk = dict(seed=130, cell=5.2, moss=0.45)
    gold = dict(r="gold", runes=dict(region=(-12.0, 12.0, -60.0, 60.0), faces=("north", "south", "east", "west"), cell=1.4, density=0.5, seed=33))
    ring = [(4.7 * math.cos(t * math.pi / 10), 28.0 + 4.7 * math.sin(t * math.pi / 10)) for t in range(21)]
    heart = [dict(pts=ring, w=0.55, halo=1.0, faces=("north",)),
             dict(pts=[(3.3, 31.3), (6.4, 33.6), (9.6, 34.2)], w=0.45, halo=0.8, faces=("north",)),
             dict(pts=[(-3.3, 31.3), (-6.4, 33.6), (-9.6, 34.2)], w=0.45, halo=0.8, faces=("north",)),
             dict(pts=[(3.3, 24.7), (6.0, 22.6), (6.0, 20.2)], w=0.45, halo=0.8, faces=("north",)),
             dict(pts=[(-3.3, 24.7), (-6.0, 22.6), (-6.0, 20.2)], w=0.45, halo=0.8, faces=("north",)),
             dict(pts=[(0.0, 32.7), (0.0, 37.8)], w=0.45, halo=0.8, faces=("north",)),
             dict(pts=[(6.4, 33.6), (6.8, 36.4)], w=0.35, halo=0.6, faces=("north",)),
             dict(pts=[(-6.4, 33.6), (-6.8, 36.4)], w=0.35, halo=0.6, faces=("north",)),
             # down the sides of the body
             dict(pts=[(-5.0, 37.0), (-2.0, 33.0), (-2.6, 27.0), (0.6, 22.0)], w=0.4, halo=0.7, faces=("west", "east")),
             # a spine of light down its back, ribs branching off it
             dict(pts=[(0.0, 37.8), (0.0, 21.0)], w=0.5, halo=0.9, faces=("south",)),
             *[dict(pts=[(0.0, y), (sx * 3.6, y - 1.6), (sx * 6.8, y - 1.0)], w=0.38, halo=0.6, faces=("south",))
               for y in (35.0, 30.4, 25.8) for sx in (-1, 1)]]
    arm_lines = [dict(pts=[(0.0, 35.0), (0.0, 30.6), (-1.6, 28.4), (-1.6, 23.0)], w=0.45, halo=0.8, faces=("west", "east")),
                 dict(pts=[(0.0, 30.6), (1.8, 28.0)], w=0.35, halo=0.6, faces=("west", "east"))]
    fore_lines = [dict(pts=[(-2.0, 21.6), (-0.6, 18.0), (-1.6, 14.4), (0.4, 10.6)], w=0.45, halo=0.8, faces=("west", "east")),
                  dict(pts=[(-0.6, 18.0), (1.8, 16.4)], w=0.35, halo=0.6, faces=("west", "east"))]
    B.bone("root", None, [0, 0, 0])
    B.bone("hips", "root", [0, 17, 0], [box(0, 17.0, 0.6, 14.0, 5.0, 9.0, "boulder", r="rock", **rk,
                                             lines=[dict(pts=[(-6.6, 18.0), (-3.0, 16.4), (3.0, 16.4), (6.6, 18.0)], w=0.4, halo=0.7, faces=("north",))])])
    B.bone("chest", "hips", [0, 19, 0], [
        box(0, 29.0, -0.6, 22.0, 18.0, 13.0, "boulder", r="rock", **rk, lines=heart),
        box(0, 28.0, -7.2, 5.0, 5.0, 0.6, "crystal"),
        box(0, 28.0, -7.0, 7.0, 7.0, 0.4, "boulder", r="rock_d", seed=131, cell=1.8, skip=("south",)),
        *[box(sx * 11.6, 36.0, 0.0, 8.0, 7.0, 10.0, "boulder", r="rock", **{**rk, "seed": 132 + sx}) for sx in (-1, 1)],
        *shard(B, -8.0, 38.6, 2.0, 2.6, 4.4, -18, -20), *shard(B, 9.0, 38.8, 3.6, 2.2, 3.4, -24, 22), *shard(B, 2.0, 37.8, 5.0, 2.0, 3.0, -40, 10),
        *shard(B, -10.8, 39.4, -1.4, 1.6, 2.6, -10, -34), *shard(B, 12.6, 39.2, 1.0, 1.4, 2.2, -14, 40),
    ], rot=[12, 0, 0])
    B.bone("head", "chest", [0, 37.0, -3.4], [
        box(0, 39.4, -5.2, 9.0, 6.4, 7.4, "boulder", r="rock", **{**rk, "seed": 136},
            sockets=[dict(x=0.0, y=38.8, r=(2.0, 1.0), core=0.46)],
            cuts=[dict(pts=[(-3.0, 37.0), (-1.2, 36.6), (1.2, 36.6), (3.0, 37.0)], w=0.5, faces=("north",))],
            lines=[dict(pts=[(-3.6, 36.2), (-2.6, 37.4)], w=0.3, halo=0.5, faces=("north",)), dict(pts=[(3.6, 36.2), (2.6, 37.4)], w=0.3, halo=0.5, faces=("north",))]),
        box(0, 40.7, -9.0, 9.6, 1.6, 1.8, "boulder", r="rock_d", seed=133),
        # a band of old gold round its brow, cut with runes
        box(0, 41.95, -5.2, 9.4, 1.1, 7.8, "trim", **gold),
        box(0, 43.0, -4.6, 6.0, 1.4, 5.0, "boulder", r="rock", **{**rk, "seed": 134}),
    ], rot=[-12, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "chest", [sx * 13.4, 36.0, 0.0], [
            col(sx * 14.4, 0.0, 22.0, 36.0, 7.0, 8.0, "boulder", r="rock", **{**rk, "seed": 137 + sx}, lines=arm_lines),
            col(sx * 14.4, 0.0, 31.4, 33.2, 7.6, 8.6, "trim", **gold)], rot=[-12, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 14.4, 23.0, 0.0], [
            col(sx * 14.8, -0.4, 9.0, 23.4, 8.4, 9.0, "boulder", r="rock", **{**rk, "seed": 139 + sx}, lines=fore_lines),
            col(sx * 14.8, -0.4, 18.6, 20.6, 9.0, 9.6, "trim", **gold),
            box(sx * 14.8, 5.2, -0.6, 10.0, 7.4, 10.4, "boulder", r="rock", **{**rk, "seed": 141 + sx},
                lines=[dict(pts=[(x, 8.6), (x, 6.4)], w=0.38, halo=0.6, faces=("north",)) for x in (sx * 14.8 - 2.6, sx * 14.8, sx * 14.8 + 2.6)]),
        ], rot=[12, 0, 0])
        B.bone(f"{side}_leg", "hips", [sx * 5.6, 16.0, 0.6], [
            col(sx * 5.8, 0.6, 8.6, 16.4, 7.0, 7.4, "boulder", r="rock", **{**rk, "seed": 143 + sx},
                lines=[dict(pts=[(sx * 5.8 - 2.6, 13.6), (sx * 5.8, 12.0), (sx * 5.8 + 2.6, 13.6)], w=0.38, halo=0.6, faces=("north",))])])
        B.bone(f"{side}_shin", f"{side}_leg", [sx * 5.8, 9.0, 0.6], [
            col(sx * 5.9, 0.4, 0.0, 9.2, 8.0, 9.0, "boulder", r="rock", **{**rk, "seed": 145 + sx}),
            col(sx * 5.9, 0.4, 6.4, 7.8, 8.6, 9.6, "trim", **gold)])
    register(name, lambda aff: pal(aff, (255, 196, 80), {
        "rock": (98, 96, 92), "rock_d": (40, 38, 38),
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
    """A rooted plant that kills: gnarled roots gripping the ground and broad toothed leaves rotting at their edges, a
    thick barked stalk wound with a thorny vine and set with long hooked thorns, and a swollen flower-head: a round
    mouth ringed with thorn-teeth around a throat glowing with poison, five fleshy petals around it (crimson, warted
    pale, ringed inside with teeth) curling back at their tips, and a venom sac behind it webbed with glowing veins."""
    name = "thorn_spitter"
    B = Body(name, 3300)
    box, col = B.box, B.col
    bk = dict(r="bark", seed=140, knots=0.6)

    def thorn(x, y, z, ox, oz, ln=2.0, w=0.62, up=0.7):
        """A hooked thorn growing out of a stalk at (x, y, z) towards (ox, oz), curving up at its tip."""
        n = math.hypot(ox, oz) or 1.0
        ox, oz = ox / n, oz / n
        return limb(B, [x, y, z], [(x + ox * ln * 0.55, y + ln * 0.18, z + oz * ln * 0.55, w, dict(kr=(0.0, 0.5))),
                                   (x + ox * ln * 0.95, y + ln * up * 0.6, z + oz * ln * 0.95, w * 0.7, dict(kr=(0.5, 1.0)))],
                    "horn", r="thorn", tip="+y")

    def vine(y0, y1, r, a0, turns, cx=0.0, cz=0.0):
        """A thorny vine winding round a stalk from y0 to y1."""
        n = int(turns * 6)
        pts = []
        for i in range(1, n + 1):
            a = a0 + i * 2 * math.pi * turns / n
            pts.append((cx + math.cos(a) * r, y0 + (y1 - y0) * i / n, cz + math.sin(a) * r, 0.5))
        a = a0
        return limb(B, [cx + math.cos(a) * r, y0, cz + math.sin(a) * r], pts, "skin", r="vine", zone="limb", seed=149)

    B.bone("root", None, [0, 0, 0])
    roots = []
    for k, a in enumerate((18, 92, 166, 240, 314)):
        ca, sa = math.cos(math.radians(a)), math.sin(math.radians(a))
        r1, r2, r3 = 1.6, 3.6 + 0.4 * (k % 2), 5.6 + 0.6 * (k % 3)
        roots += limb(B, [ca * r1, 1.8, sa * r1], [(ca * r2, 1.0, sa * r2, 1.5), (ca * r3, -0.3, sa * r3, 1.0), (ca * (r3 + 1.4), -0.6, sa * (r3 + 1.4), 0.6)],
                      "bark", r="root_w", seed=150 + k, fib=3.0, knots=0.0)
    leaves = [B.c([0.4, 0.5, -1.6], [8.6, 0.4, 3.2], "skin", r="leaf", zone="dark", seed=141 + i, pivot=[0, 1.0, 0], rot=[0, a, 20 + 6 * (i % 2)],
                  fx=leaf_rot, skip=("north", "south", "east", "west")) for i, a in enumerate((52, 128, 212, 300))]
    B.bone("base", "root", [0, 0, 0], [*roots, *leaves,
                                       col(0, 0, 0.0, 2.4, 4.6, 4.6, "bark", **{**bk, "moss": 0.6}),
                                       col(0, 0, 2.2, 3.4, 3.8, 3.8, "bark", **{**bk, "seed": 141})])
    B.bone("stem", "base", [0, 2.6, 0], [
        col(0, 0, 2.6, 8.8, 3.0, 3.0, "bark", **bk),
        *vine(3.0, 8.4, 1.75, 0.4, 1.3),
        *thorn(1.5, 4.4, 0.0, 1, 0.2), *thorn(-1.5, 5.6, 0.4, -1, 0.3), *thorn(0.2, 6.8, 1.5, 0.2, 1), *thorn(-0.3, 7.6, -1.5, -0.2, -1),
    ], rot=[-6, 0, 0])
    B.bone("stem2", "stem", [0, 8.4, 0], [
        col(0, 0, 8.4, 13.2, 2.6, 2.6, "bark", **{**bk, "seed": 143}),
        *vine(8.6, 12.6, 1.55, 2.2, 0.9),
        *thorn(1.3, 9.6, 0.3, 1, 0.3, 1.7), *thorn(-1.3, 11.0, -0.2, -1, -0.2, 1.7), *thorn(0.0, 12.0, 1.3, 0.1, 1, 1.6),
    ], rot=[14, 0, 0])
    # the head: a round pod (three crossed boxes) with the mouth on its front, a venom sac behind
    pod = dict(r="petal", zone="back", split=(0.55, 0.2), seed=144, mottle=0.14, blotch=0.3,
               spotted=dict(ramp="petal_l", cell=1.0, radius=0.3, density=0.42, tone=0.48, seed=17))
    teeth = []
    for i in range(9):
        a = i * 2 * math.pi / 9 + 0.2
        x, y = math.cos(a) * 2.35, 15.0 + math.sin(a) * 2.35
        teeth += limb(B, [x, y, -2.3], [(x - math.cos(a) * 0.5, y - math.sin(a) * 0.5, -3.3, 0.45)], "horn", r="tooth", tip="+y")
    B.bone("head", "stem2", [0, 12.8, 0], [
        box(0, 15.0, 0.2, 5.6, 5.6, 4.8, "skin", **pod, maw=dict(x=0.0, y=15.0, r=2.5, n=11, glow=True)),
        box(0, 15.0, 0.6, 6.4, 4.2, 4.0, "skin", **{**pod, "seed": 145}),
        box(0, 15.0, 0.6, 4.2, 6.4, 4.0, "skin", **{**pod, "seed": 146}),
        box(0, 15.4, 3.6, 4.6, 4.6, 2.6, "skin", r="sac", zone="back", split=(0.6, 0.2), seed=147, mottle=0.2, blotch=0.4,
            spotted=dict(ramp="sac_l", cell=1.1, radius=0.34, density=0.35, tone=0.5, seed=18),
            lava=dict(area=4.0, chance=1.0, w=0.45, rim_w=2.0, tone=0.45, seed=148)),
        box(0, 15.6, 5.0, 3.0, 3.0, 1.0, "skin", r="sac", seed=149, lava=dict(area=2.5, chance=1.0, w=0.45, rim_w=2.0, tone=0.45, seed=150)),
        *teeth,
        *thorn(0.0, 18.0, 1.6, 0.0, 1, 1.6, 0.55, 0.9), *thorn(2.2, 17.2, 2.0, 1, 0.6, 1.4, 0.5, 0.8), *thorn(-2.2, 17.2, 2.0, -1, 0.6, 1.4, 0.5, 0.8),
    ], rot=[-8, 0, 0])
    for k in range(5):
        th = 90 + k * 72
        # each petal: a fleshy base flaring out from the mouth's rim, its tip curling back
        y1, z1 = 17.0 + 3.0 * math.sin(math.radians(30)), -2.4 - 3.0 * math.cos(math.radians(30))
        B.bone(f"petal{k}", "head", [0, 15.0, -2.4], [
            B.c([-2.1, 16.6, -5.4], [4.2, 0.8, 3.0], "skin", r="petal", zone="back", seed=151 + k, pivot=[0, 17.0, -2.4], rot=[-30, 0, 0],
                fx=petal_lip, part=0, light=0.45, skip=("north",)),
            B.c([-1.7, y1 - 0.3, z1 - 2.6], [3.4, 0.6, 2.6], "skin", r="petal", zone="back", seed=156 + k, pivot=[0, y1, z1], rot=[-68, 0, 0],
                fx=petal_lip, part=1, light=0.45, skip=("north", "south", "east", "west")),
        ], rot=[0, 0, 90 - th])
    register(name, lambda aff: pal(aff, (200, 255, 70), {
        "bark": (66, 70, 42), "root_w": (64, 50, 36), "vine": (60, 92, 38),
        "leaf": (48, 82, 36), "leaf_rot": (96, 64, 30),
        "petal": (128, 30, 46), "petal_d": (46, 10, 22), "petal_l": (222, 166, 146), "flesh_in": (176, 70, 84),
        "sac": (92, 70, 40),
        "thorn": [(20, 10, 8), (60, 30, 20), (120, 80, 56), (200, 176, 140), (240, 230, 210)],
        "tooth": [(40, 30, 22), (110, 96, 74), (190, 178, 150), (232, 226, 206), (252, 250, 240)],
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


def petal_lip(f, p, s):
    """A petal of the thorn spitter's mouth: crimson with dark veins running out along it and pale warts on it, the
    inner side (towards the mouth) fleshier and set with a row of thorn-teeth near its root, a dark rim, and the
    outer part narrowing to a rounded point."""
    if f.face not in ("up", "down"):
        return
    part = s.get("part", 0)
    # k: 0 at the petal's root (by the mouth) .. 1 at its tip, over both parts (each part's root is its +z end)
    rt = f.V if f.face == "up" else 1 - f.V
    k = np.clip(rt * 0.55 + 0.45 * part, 0, 1)
    across_ = (f.X - f.cx) / max(f.s[0] / 2, 1e-6)
    inner = f.face == "down"
    n = CS.fbm3(f.X * 0.9, f.Y * 0.9, f.Z * 0.9, s.get("seed", 151), 2)
    if inner:
        # the inside: pale flesh by the throat deepening to crimson at the tips, veins fanning out from the root
        p.over("flesh_in", 0.66 - 0.36 * k + (n - 0.5) * 0.12, 1.0)
        fan = across_ / (0.3 + 0.7 * k)
        vein = np.abs(((fan * 1.6 + 0.5) % 1.0) - 0.5) < 0.09 / (0.3 + 0.7 * k) * f.texel * 2.2
        p.over("petal_d", 0.3, 0.75 * (vein & (k > 0.06)))
        p.over(s.get("r", "petal"), 0.32, 0.8 * CS.sstep(0.55, 0.95, k))
    else:
        # the outside: dark crimson, darker at the root, warted pale
        p.over(s.get("r", "petal"), 0.3 + 0.2 * k + (n - 0.5) * 0.12, 1.0)
        CS.bumps(p, f, "petal_l", s.get("seed", 151) + 7, 0.9, 0.3, 0.55, tone=0.5, relief=0.22, rim=0.08)
    # a row of thorn-teeth inside, near the root
    if inner and part == 0:
        tq = (across_ * 3.0) % 1.0
        tooth = (k < 0.34 - 0.3 * np.abs(tq - 0.5) * 2) & (k > 0.04)
        p.over("tooth", 0.45 + 0.4 * (1 - k / 0.34), tooth)
        p.over("black", 0.08, (k <= 0.04))
    # the rim and the shape: straight sides at the root, rounding to a point at the tip
    width = np.where(k < 0.55, 1.0, np.clip(1.0 - (np.clip(k - 0.55, 0, None) / 0.45) ** 1.6, 0, 1) ** 0.5)
    keep = np.abs(across_) < width
    p.over("petal_d", 0.12, keep & (np.abs(across_) > width - 0.16))
    p.cut(keep)


def leaf_rot(f, p, s):
    """A broad toothed leaf, rotting: dark at its root and lighter out along it, a pale midrib and side veins running
    to the teeth of its edge, blotches of rot browning it from the edge in, and bites out of it."""
    if f.face not in ("up", "down"):
        return
    u, v = f.U, f.V
    mid = np.abs(v - 0.5)
    r = s.get("r", "leaf")
    n = CS.fbm3(f.X * 0.7, f.Y * 0.7, f.Z * 0.7, s.get("seed", 141), 3)
    p.over(r, 0.3 + 0.24 * u + (n - 0.5) * 0.12, 1.0)
    p.over(r + "_l", 0.42, mid < 0.045)
    side = np.abs(((u * 5.5 - mid * 2.6) % 1.0) - 0.5) < 0.07
    p.over(r + "_l", 0.3, side & (mid < 0.42) & (mid > 0.05))
    width = 0.5 * np.clip(1.12 - u, 0, 1) ** 0.6 * (1 - 0.12 * (((u * 11.0) % 1.0) > 0.5))
    edge = mid / np.maximum(width, 1e-3)
    rot = CS.sstep(0.55, 0.8, n + 0.35 * edge)
    p.over("leaf_rot", 0.3 + (n - 0.5) * 0.3, rot)
    p.over("black", 0.12, (edge > 0.86) & (edge < 1.0))
    bite = CS.vnoise2(u * 6.0, v * 3.0, s.get("seed", 141) + 3) > 0.84
    p.cut((mid < width) & ~(bite & (edge > 0.45)))


# ============================================================================================= the first five, redone

# --------------------------------------------------------------------------------------------- broodling

def brood_sigil(f, p, s):
    """A broodling's abdomen: an hourglass of light on top inside a black diamond, bone-pale chevrons edged in black
    stepping down its back behind it and round its sides."""
    if f.face == "up":
        zc, h = s.get("sigil_z", 6.0), s.get("sigil_h", 2.6)
        dz = np.abs(f.Z - zc)
        hour = (dz < h) & (np.abs(f.X) < 0.35 + 0.75 * dz / h)
        p.over("black", 0.06, (dz < h + 0.4) & (np.abs(f.X) < 0.75 + 0.75 * dz / h) & ~hour)
        p.glow("glow", 0.5 + 0.4 * (1 - dz / h), hour)
        # chevrons behind the hourglass, pointing forward
        k = ((f.Z - zc - h - 0.4 - 0.5 * np.abs(f.X)) / 1.5)
        band = (k > 0) & (k % 1.0 < 0.3) & (np.abs(f.X) < 3.6)
        rim = (k > 0) & ((k % 1.0 >= 0.3) & (k % 1.0 < 0.42)) & (np.abs(f.X) < 3.8)
        p.over("black", 0.1, rim)
        p.over("fur_l", 0.46, band)
    elif f.wall and f.face != "north":
        a = CS.across(f)
        c = ((f.Y + 0.6 * np.abs(a if not f.side else f.X)) / 1.6) % 1.0
        hi = f.Y > f.bottom + 0.45 * (f.top - f.bottom)
        p.over("black", 0.1, (np.abs(c - 0.5) < 0.16) & hi)
        p.over("fur_l", 0.44, (np.abs(c - 0.5) < 0.09) & hi)


def broodling():
    """A spider the size of a dog, a broodmother's young: a hairy dark body, a swollen abdomen with an hourglass of
    light on it, eight eyes of light in a cluster, hooked fangs, and eight jointed legs whose knees rise above its
    back."""
    name = "broodling"
    B = Body(name, 3400)
    box = B.box
    hair = dict(seed=150, len=1.6, width=1.0, lock_k=0.38, sheen=0.08)
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
        # heavy hairy jaws, and under them hooked fangs, glossy black with a drop of venom light at their points
        B.bone(f"{side}_fang", "head", [sx * 0.9, 4.8, -6.4], [
            B.c(mirror_x([0.2, 3.3, -7.6], [1.6, 2.0, 1.6], sx), [1.6, 2.0, 1.6], "fur", r="fur_d", **hair),
            B.c(mirror_x([0.5, 0.9, -7.4], [0.8, 2.6, 0.8], sx), [0.8, 2.6, 0.8], "horn", r="fang", tip="-y", pivot=[sx * 0.9, 3.4, -7.0], rot=[12, 0, sx * -16],
                lines=[dict(pts=[(sx * 0.9 - 0.2, 1.0), (sx * 0.9 - 0.2, 1.3)], w=0.3, halo=0.3, faces=("north",))]),
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
                B.c([knee[0] - 0.55, knee[1] - 9.8, z - 0.55], [1.1, 9.8, 1.1], "fur", r="fur", zone="dark", **hair, pivot=knee, rot=[0, 0, sx * -24],
                    rings=dict(ramp="fur_l", ys=[(knee[1] - 2.2, knee[1] - 1.3), (knee[1] - 7.4, knee[1] - 6.6)], tone=0.42)),
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
    ely = dict(r="shell", sheen_r="sheen", sheen_k=0.28, seed=160)
    B.bone("root", None, [0, 0, 0])
    crystals = (shard(B, 0.0, 16.4, 1.0, 3.4, 6.4, -12, 0) + shard(B, -3.2, 15.8, 4.4, 2.6, 4.6, 8, -24) + shard(B, 3.0, 16.0, -2.8, 2.6, 5.0, -24, 22)
                + shard(B, -2.4, 16.2, -3.6, 2.0, 3.6, -30, -16) + shard(B, 2.6, 15.6, 5.8, 2.0, 3.4, 20, 26) + shard(B, 0.4, 15.2, 8.0, 1.6, 2.6, 36, 6)
                # smaller crystal breaking out through the sides of the shell
                + shard(B, -7.2, 12.4, -3.0, 1.3, 2.2, -10, -62) + shard(B, -7.0, 12.0, 4.8, 1.0, 1.6, 14, -58)
                + shard(B, 7.2, 12.6, 1.6, 1.4, 2.4, 6, 60) + shard(B, 7.0, 11.8, 7.6, 0.9, 1.4, 20, 64)
                + shard(B, -5.8, 15.0, 8.6, 1.0, 1.6, 40, -30))
    B.bone("body", "root", [0, 9, 0], [
        box(0, 10.0, 0.6, 16.0, 7.0, 18.0, "elytra", **ely, fx=elytra_seam,
            cuts=[dict(pts=[(-8.4, 13.0), (9.6, 13.0)], w=0.5, faces=("west", "east"), ramp="black", tone=0.08)]),
        box(0, 14.6, 1.2, 14.0, 2.4, 15.6, "elytra", **ely, fx=elytra_seam),
        box(0, 16.0, 1.6, 9.0, 0.8, 11.0, "elytra", **ely, fx=elytra_seam),
        box(0, 6.6, 0.6, 13.0, 1.0, 15.0, "chitin", r="belly", axis="z", band=1.8, seed=161),
        *crystals,
    ])
    B.bone("head", "body", [0, 10, -8.4], [
        box(0, 9.6, -10.8, 10.4, 6.0, 5.4, "elytra", **{**ely, "seed": 162},
            eyes=eyes(3.6, 10.6, (0.75, 0.45), slant=0.45, pupil=None, lid=1.2)),
        box(0, 12.9, -10.0, 11.4, 1.4, 4.4, "elytra", **{**ely, "r": "shell_d", "seed": 163}),
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
        "belly": (92, 74, 70), "horn": (40, 34, 38), "mandible": (30, 26, 28), "sheen": (52, 92, 150),
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
    total = sum(ln for _, ln, _ in segs)
    done = 0.0
    for ang, ln, w in segs:
        th = math.radians(ang)
        extra = dict(kr=(done / total, (done + ln) / total)) if mat == "horn" and "kr" not in kw else {}
        out.append(B.c([x - w / 2, y, z - w / 2], [w, ln, w], mat, pivot=[x, y, z], rot=[ang, 0, 0], **kw, **extra))
        y, z = y + math.cos(th) * ln, z - math.sin(th) * ln
        done += ln
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
    """A cult priest in a bone mask: tall and thin in layered violet robes, a crimson stole sewn with runes of light
    over a tabard with the cult's sigil, a high collar and a tall stepped mitre of a hood trimmed in tarnished gold,
    a long mask of bone with slanted empty eyes, a crack of light and a stitched mouth, a ring of runes turning
    behind its head, a tome chained at its hip, long fingers of bone and an orb of its element over its right hand."""
    name = "hollow_acolyte"
    B = Body(name, 3600)
    box, col = B.box, B.col
    robe = dict(r="cloth", folds=1.4)
    B.bone("root", None, [0, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "root", [sx * 1.6, 10, 0], [
            col(sx * 1.6, 0.2, 0.0, 10.0, 2.6, 2.8, "cloth", r="cloth_d", seed=171),
            box(sx * 1.6, 0.6, -0.6, 2.8, 1.2, 3.6, "leather", stitch=False, seed=172),
        ])
    B.bone("body", "root", [0, 11, 0], [
        # the robe: a long skirt with a gold band and a torn hem, a narrow chest; a crimson tabard down the front with
        # the cult's sigil, a stole either side of it with a thread of light down it
        B.c([-4.2, 0.6, -2.8], [8.4, 11.2, 5.8], "cloth", hem=3.0, seed=173, band=(3.6, 4.6), skip=("down",), **robe),
        box(0, 16.2, 0.0, 7.0, 9.4, 4.6, "cloth", seed=170, tears=1, **robe),
        box(0, 9.9, -2.7, 2.2, 17.8, 0.3, "cloth", r="inner", hem=1.6, seed=174, skip=("south",), folds=2.4,
            # the sigil sewn in gold: an eye in a downturned triangle
            facepaint=[dict(pts=[(-0.95, 17.4), (0.95, 17.4), (0.0, 15.4), (-0.95, 17.4)], w=0.22, ramp="trim", tone=0.6),
                       dict(pts=[(-0.42, 16.85), (0.0, 17.05), (0.42, 16.85), (0.0, 16.65), (-0.42, 16.85)], w=0.16, ramp="trim", tone=0.65)],
            lines=[dict(pts=[(0.0, 16.85), (0.0, 16.86)], w=0.18, faces=("north",), halo=0.3, bright=0.9)]),
        *[box(sx * 1.75, 12.6, -2.95, 1.3, 18.0, 0.4, "cloth", r="stole", seed=175 + sx, skip=("south",), hem=1.2, folds=2.0,
              lines=[dict(pts=[(sx * 1.75, 5.0), (sx * 1.75, 20.8)], w=0.2, faces=("north",), halo=0.4, bright=0.7)])
          for sx in (-1, 1)],
        *[box(sx * e, 12.75, -3.05, 0.18, 17.7, 0.3, "trim", r="trim") for sx in (-1, 1) for e in (1.19, 2.31)],
        # a sash of crimson with a buckle of bone, chains of bone charms hanging from it, a tome chained at the left hip
        box(0, 12.0, 0.05, 8.6, 1.1, 6.0, "cloth", r="inner", seed=176, folds=3.0),
        box(0, 12.0, -3.05, 1.6, 1.6, 0.4, "bone", seed=177),
        *[col(sx * 2.85, -3.1, 8.6, 11.6, 0.35, 0.35, "chain", r="trim") for sx in (-1, 1)],
        *[box(sx * 2.85, 8.2, -3.1, 0.8, 0.9, 0.4, "bone", seed=178 + sx) for sx in (-1, 1)],
        col(4.6, -0.4, 9.9, 11.6, 0.35, 0.35, "chain", r="trim"),
        box(4.8, 8.5, -0.4, 1.0, 2.8, 2.2, "leather", seed=179, stitch=True),
        box(4.8, 8.5, -0.4, 0.7, 2.5, 2.35, "cloth", r="page", folds=6.0, seed=180),
        *[box(4.8, 8.5 + dy, -0.4 + dz, 1.1, 0.5, 0.5, "trim", r="trim") for dy in (-1.2, 1.2) for dz in (-0.95, 0.95)],
        # a capelet over the shoulders trimmed in gold
        B.c([-4.4, 18.4, -2.9], [8.8, 4.0, 6.0], "cloth", r="cloth_d", hem=1.0, band=(19.5, 20.1), runes=False, seed=181, skip=("down",)),
    ])
    hood = dict(r="cloth", folds=1.3)
    B.bone("head", "body", [0, 22.0, 0], [
        box(0, 25.4, -0.2, 5.0, 5.4, 5.0, "void"),
        # the mask: a long face of bone with a brow ridge and cheekbones, empty slanted eyes with points of light, a crack
        # of light down it and the mouth stitched shut
        box(0, 25.2, -2.9, 4.2, 5.0, 0.6, "bone", seed=184, ends=0.0,
            sockets=sockets(1.0, 26.0, (0.95, 0.42), slant=0.42, core=0.3),
            lines=[dict(pts=[(0.2, 27.6), (-0.3, 26.6), (0.2, 25.5), (-0.1, 24.4)], w=0.2, faces=("north",), halo=0.5, bright=0.8)],
            cuts=[dict(pts=[(-1.1, 23.5), (1.1, 23.5)], w=0.25, faces=("north",)),
                  *[dict(pts=[(x, 23.1), (x, 23.9)], w=0.18, faces=("north",)) for x in (-0.75, -0.25, 0.25, 0.75)]]),
        box(0, 27.15, -3.3, 4.2, 0.7, 0.5, "bone", seed=185, cracks=False, shadow_top=(26.8, 27.1, 0.2)),
        *[box(sx * 1.5, 25.0, -3.25, 1.1, 0.6, 0.4, "bone", seed=186, cracks=False) for sx in (-1, 1)],
        box(0, 22.2, -2.85, 2.6, 1.2, 0.5, "bone", seed=187, cracks=False),
        # the hood, dark inside, its rim trimmed in gold, rising into a tall stepped mitre with the sigil's eye on it
        box(0, 28.6, -0.2, 6.6, 0.8, 6.8, "cloth", dark_faces=("down",), seed=188, **hood),
        box(3.0, 25.6, 0.0, 0.8, 6.4, 6.6, "cloth", dark_faces=("east",), hem=0.8, seed=189, **hood),
        box(-3.0, 25.6, 0.0, 0.8, 6.4, 6.6, "cloth", dark_faces=("west",), hem=0.8, seed=190, **hood),
        box(0, 25.6, 3.0, 5.2, 6.4, 0.8, "cloth", seed=191, **hood),
        box(0, 28.75, -3.25, 6.6, 1.3, 0.8, "cloth", dark_faces=("down", "south"), seed=192, band=(28.2, 28.7), runes=False, **hood),
        *[box(sx * 2.95, 25.4, -3.25, 0.7, 5.8, 0.8, "cloth", dark_faces=("south",), seed=193, **hood) for sx in (-1, 1)],
        box(0, 30.1, 0.0, 5.0, 2.2, 5.6, "cloth", seed=194, band=(29.0, 29.5), runes=False, **hood),
        box(0, 32.0, 0.3, 3.8, 1.8, 4.4, "cloth", seed=195, band=(31.1, 31.45), runes=False, **hood,
            eyes=eyes(0.0, 31.95, (0.42, 0.24), pupil="dot", lid=0.0, glint=False)[:1]),
        box(0, 33.5, 0.6, 2.4, 1.4, 3.0, "cloth", seed=196, band=(32.8, 33.1), runes=False, **hood),
        box(0, 34.5, 0.8, 1.0, 0.8, 1.6, "trim", r="trim"),
    ], rot=[-4, 0, 0])
    # a ring of runes turning behind the head
    hx, hy, hz, hr = 0.0, 28.6, 4.1, 5.2
    ring = []
    for i in range(16):
        a = i * math.pi / 8
        cx_, cy_ = hx + hr * math.cos(a), hy + hr * math.sin(a)
        ring.append(B.c([cx_ - 1.08, cy_ - 0.17, hz - 0.17], [2.16, 0.34, 0.34], "glow", pivot=[cx_, cy_, hz], rot=[0, 0, -math.degrees(a + math.pi / 2)]))
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        ring.append(B.c([hx + (hr + 0.7) * math.cos(a) - 0.45, hy + (hr + 0.7) * math.sin(a) - 0.45, hz - 0.12], [0.9, 0.9, 0.24], "glow",
                        pivot=[hx + (hr + 0.7) * math.cos(a), hy + (hr + 0.7) * math.sin(a), hz], rot=[0, 0, 45]))
    B.bone("halo", "head", [hx, hy, hz], ring)
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_arm", "body", [sx * 3.6, 20.6, 0], [
            B.c(mirror_x([3.0, 16.6, -1.5], [2.6, 4.6, 3.0], sx), [2.6, 4.6, 3.0], "cloth", seed=197 + sx, **robe),
            B.c(mirror_x([2.7, 12.6, -1.9], [3.2, 4.4, 3.8], sx), [3.2, 4.4, 3.8], "cloth", hem=1.2, band=(14.3, 14.85), runes=False, seed=199 + sx,
                skip=("up", "down"), **robe),
        ], rot=[-8, 0, sx * -8])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 4.3, 14.2, 0], [
            col(sx * 4.0, 0, 8.4, 14.6, 0.6, 0.6, "bone"), col(sx * 4.6, 0, 8.4, 14.6, 0.6, 0.6, "bone"),
            box(sx * 4.3, 8.0, -0.2, 1.6, 1.0, 1.6, "bone"),
            *[B.c([sx * 4.3 - 0.8 + i * 0.55, 4.6, -0.6 + (i % 2) * 0.25], [0.32, 3.6, 0.32], "bone", seed=183 + i, pivot=[sx * 4.3, 7.6, 0], rot=[-16, 0, (i - 1.5) * -7])
              for i in range(4)],
            # a gold ring on one long finger
            box(sx * 4.3 - 0.25, 6.4, -0.55, 0.5, 0.35, 0.5, "trim", r="trim") if sx > 0 else None,
        ], rot=[-14, 0, 0])
    # the orb, floating above the right hand: a glowing core inside a cage of dark bands
    B.bone("orb", "right_hand", [-4.3, 2.6, -2.6], [
        box(-4.3, 2.6, -2.6, 2.0, 2.0, 2.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[45, 45, 0]),
        box(-4.3, 2.6, -2.6, 1.0, 3.4, 1.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[0, 45, 0]),
        box(-4.3, 2.6, -2.6, 3.4, 1.0, 1.0, "glow", pivot=[-4.3, 2.6, -2.6], rot=[0, 0, 0]),
    ])
    register(name, lambda aff: pal(aff, (170, 120, 255), {
        "cloth": (60, 42, 80), "cloth_d": (24, 16, 34), "cloth_l": (124, 98, 144),
        "inner": (98, 24, 36), "stole": (46, 14, 22), "page": (196, 182, 150),
        "trim": [(34, 22, 8), (96, 66, 24), (164, 124, 56), (210, 172, 92), (246, 224, 160)],
        "bone": (214, 204, 180), "leather": (60, 44, 34),
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
        "halo": rot(lambda t: [0, 0, -30 * t]),
    }, 0.05)
    L = 1.4
    A["walk"] = anim(L, True, {
        "right_leg": rot(lambda t: [-22 * wave(t, L), 0, 0]), "left_leg": rot(lambda t: [22 * wave(t, L), 0, 0]),
        "body": {"position": lambda t: [0, -0.3 * abs(wave(t, L)), 0], "rotation": lambda t: [3, 2 * wave(t, L), 0]},
        "right_arm": rot(lambda t: [-22, 0, 0]), "right_hand": rot(lambda t: [-30, 0, 0]),
        "left_arm": rot(lambda t: [10 * wave(t, L), 0, 0]),
        "orb": {"position": lambda t: [0, 0.5 * wave(t, L / 2), 0], "rotation": lambda t: [0, 160 * t, 0]},
        "halo": rot(lambda t: [0, 0, -90 / L * t]),
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
        "halo": {"rotation": lambda t: [0, 0, -270 * ease_in_out(window(t, 0.0, 0.6))], "scale": lambda t: [1 + 0.25 * draw(t) * (1 - window(t, 0.55, 0.7))] * 3},
    }, 0.025)
    L = 1.2
    A["death"] = anim(L, "hold", {
        "body": {"position": lambda t: [0, -7 * ease_in(window(t, 0.2, 0.8)), 0], "rotation": lambda t: [30 * ease_in(window(t, 0.3, 0.9)), 0, 0],
                 "scale": lambda t: [1, 1 - 0.3 * window(t, 0.2, 0.8), 1]},
        "head": rot(lambda t: [40 * window(t, 0.3, 0.9), 0, 16 * window(t, 0.5, 1.0)]),
        "right_arm": rot(lambda t: [-22 + 40 * window(t, 0.1, 0.6), 0, 0]), "left_arm": rot(lambda t: [20 * window(t, 0.1, 0.6), 0, 0]),
        "right_leg": rot(lambda t: [-60 * window(t, 0.2, 0.8), 0, 0]), "left_leg": rot(lambda t: [-60 * window(t, 0.2, 0.8), 0, 0]),
        "orb": {"scale": lambda t: [max(0.01, 1 - window(t, 0.0, 0.4))] * 3},
        "halo": {"scale": lambda t: [max(0.01, 1 - window(t, 0.0, 0.5))] * 3},
    }, 0.05)
    write_anims(name, A)


# --------------------------------------------------------------------------------------------- ashen sentinel

def ashen_sentinel():
    """A soldier of ash in full plate: a great helm with a visor standing out from it, fire showing through the slit
    between its brow and its face plate, breathing holes and a ridge down the face, a long ragged plume falling down
    its back; a gorget, pauldrons of three lames with a guard standing up at the neck, a ridged breastplate, a skirt of
    lames over a tabard gone to rags, couters and poleyns, embers glowing in every joint of its armour, a long sword
    with a burning fuller, and a tower shield bearing a sigil that holds off anything from the front."""
    name = "ashen_sentinel"
    B = Body(name, 3700)
    box, col = B.box, B.col
    pl = dict(r="plate", engrave="trim", embers=1.0)
    # the plates laid over the armour's main pieces: their seams stay dark, so the fire shows only in its joints
    lp = dict(r="plate", engrave="trim")
    B.bone("root", None, [0, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        B.bone(f"{side}_leg", "root", [sx * 2.2, 12.6, 0], [
            col(sx * 2.2, 0, 6.6, 12.8, 3.6, 3.8, "plate", **pl, seed=190),
            box(sx * 2.2, 6.6, -1.8, 3.0, 2.2, 0.8, "plate", **pl, seed=191, rivets=False),
            box(sx * 2.2, 6.6, -2.35, 1.3, 1.3, 0.5, "trim", pivot=[sx * 2.2, 6.6, -2.35], rot=[0, 0, 45]),
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
        # a ridge down the breastplate, a gorget round the neck, a skirt of two lames over the hips
        box(0, 19.4, -3.2, 1.0, 8.8, 0.5, "plate", r="plate", seed=214, rivets=False),
        box(0, 24.7, -0.2, 6.2, 1.4, 6.2, "plate", **lp, seed=215, rivets=False),
        box(0, 12.7, -0.1, 9.2, 1.3, 5.9, "plate", **lp, seed=216, rivets=False),
        box(0, 11.5, -0.1, 9.6, 1.3, 6.2, "plate", **lp, seed=217, rivets=False),
        # the tabard: a rag hanging front and back from the belt
        B.c([-2.4, 4.0, -2.75], [4.8, 10.6, 0.4], "cloth", r="tabard", hem=3.2, seed=197, skip=("up", "down"),
            band=(10.4, 11.6), runes=False),
        B.c([-3.0, 3.0, 2.45], [6.0, 11.6, 0.4], "cloth", r="tabard", hem=3.6, seed=198, skip=("up", "down")),
        box(0, 14.4, 0, 9.0, 1.2, 5.6, "leather", stitch=False, seed=199),
        box(0, 14.4, -2.85, 1.8, 1.6, 0.3, "trim"),
    ])
    B.bone("head", "body", [0, 24.6, 0], [
        # the great helm: its shell, a low dome on it
        box(0, 27.9, 0.0, 6.4, 6.6, 6.4, "plate", **pl, seed=200, rivets=False),
        box(0, 31.5, 0.1, 5.4, 0.8, 5.6, "plate", **lp, seed=218, rivets=False),
        # the visor standing out from it: a brow plate, a face plate with breathing holes and a ridge down it, and
        # between them the slit, with the fire inside showing through
        box(0, 29.85, -3.45, 6.0, 1.7, 0.7, "plate", **lp, seed=219, rivets=False),
        box(0, 26.6, -3.5, 6.0, 3.8, 0.8, "plate", **lp, seed=220, rivets=False,
            holes=[dict(x=x, y=y, r=(0.2, 0.2)) for x in (-2.2, -1.4, 1.4, 2.2) for y in (27.2, 26.3, 25.4)]),
        box(0, 26.8, -4.0, 0.8, 4.2, 0.5, "plate", r="plate", seed=221, rivets=False),
        box(0, 28.75, -3.3, 5.6, 0.55, 0.3, "glow"),
        box(0, 28.75, -3.0, 5.8, 0.9, 0.3, "void"),
        # the plume: a ridge of horsehair along the top of the helm, falling long and ragged down the back
        B.c([-0.7, 31.8, -2.4], [1.4, 1.1, 5.4], "hair", r="crest", seed=203, ragged=0.3),
        B.c([-0.75, 23.0, 2.6], [1.5, 8.8, 1.0], "hair", r="crest", seed=222, ragged=1.0, pivot=[0, 31.8, 3.0], rot=[16, 0, 0], skip=("up",)),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 4.6, 23.6, 0]
        B.bone(f"{side}_arm", "body", [sx * 5.0, 23.2, 0], [
            # a pauldron of three lames, each lapping over the one under it, a guard standing up at the neck
            B.c(mirror_x([4.5, 20.4, -3.0], [3.4, 1.5, 6.0], sx), [3.4, 1.5, 6.0], "plate", **lp, seed=223 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 34]),
            B.c(mirror_x([4.0, 21.7, -3.2], [3.9, 1.5, 6.4], sx), [3.9, 1.5, 6.4], "plate", **lp, seed=225 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 24]),
            B.c(mirror_x([3.4, 23.0, -3.4], [4.3, 1.5, 6.8], sx), [4.3, 1.5, 6.8], "plate", **lp, seed=204 + sx, pivot=piv, rot=[0, 0, sx * 14]),
            B.c(mirror_x([3.3, 24.2, -3.0], [0.6, 1.8, 6.0], sx), [0.6, 1.8, 6.0], "plate", **lp, seed=227 + sx, rivets=False, pivot=piv, rot=[0, 0, sx * 14]),
            col(sx * 5.8, 0, 16.4, 22.6, 3.2, 3.4, "plate", **pl, seed=206 + sx),
        ], rot=[0, 0, sx * -4])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 5.8, 16.6, 0], [
            col(sx * 5.8, 0, 10.6, 16.8, 3.0, 3.2, "plate", **pl, seed=208 + sx),
            box(sx * 5.8, 10.0, -0.2, 3.4, 2.4, 3.6, "plate", **pl, seed=210 + sx, rivets=False),
            # a couter over the elbow with a fan plate on its outside, a flared cuff to the gauntlet
            box(sx * 5.8, 16.5, 0.2, 3.6, 1.8, 3.8, "plate", **lp, seed=229 + sx, rivets=False),
            B.c(mirror_x([7.45, 15.2, -0.9], [0.5, 2.6, 2.6], sx), [0.5, 2.6, 2.6], "plate", r="plate", seed=231 + sx, rivets=False,
                pivot=[sx * 7.7, 16.5, 0.4], rot=[0, 0, sx * 10]),
            box(sx * 5.8, 11.8, -0.2, 3.7, 1.2, 3.9, "plate", **lp, seed=233 + sx, rivets=False),
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
    """A burning skull that flies: old bone gone brown with soot where the fire licks it, a heavy brow and cheekbones,
    the seams of the skull wandering over its crown, cracks with fire showing through, deep sockets with coals in
    them and flame leaking up out of them, a jaw that chatters, flames streaming up and back from its crown, sparks
    circling it. It swells and bursts when it reaches you."""
    name = "emberwisp"
    B = Body(name, 3800)
    box = B.box
    soot = dict(ramp="soot", tone=0.4, weight=0.75)
    bn = dict(seed=210, lava=dict(area=13.0, chance=0.75, seed=47, rim="soot", w=0.5, rim_w=1.8, tone=0.42), flush=dict(**soot, high=0.55))
    # the seams of the skull: across the crown and down its middle to the back
    zig = lambda a0, a1, b, n, amp: [(a0 + (a1 - a0) * i / n, b + (amp if i % 2 else -amp)) for i in range(n + 1)]
    sutures = [dict(pts=[(x, z) for x, z in zig(-3.3, 3.3, -0.8, 12, 0.18)], w=0.22, faces=("up",), ramp="soot", tone=0.12),
               dict(pts=[(x, z) for z, x in zig(-0.8, 3.6, 0.0, 9, 0.16)], w=0.22, faces=("up",), ramp="soot", tone=0.12),
               dict(pts=[(z, y) for z, y in zig(-0.8, 2.4, 9.2, 7, 0.16)], w=0.22, faces=("west", "east"), ramp="soot", tone=0.12)]
    B.bone("root", None, [0, 0, 0])
    B.bone("head", "root", [0, 6.5, 0], [
        # the cranium, its seams, its soot, and the deep sockets under the brow with coals and flame in them
        box(0, 7.6, 0.2, 6.6, 5.6, 6.8, "bone", **bn, cuts=sutures,
            sockets=sockets(1.68, 7.3, (1.05, 0.82), slant=0.42, core=0.3),
            # the temples are hollow behind the eyes
            lobes=[dict(faces=("west", "east"), a=-0.55, h=0.42, ra=0.3, rh=0.25, k=-0.12, rim=0.0)]),
        # a heavy brow over the sockets, the cheekbones under them
        box(0, 8.4, -3.4, 6.2, 0.9, 0.8, "bone", seed=213, flush=dict(ramp="soot", tone=0.4, weight=0.5),
            grooves=[dict(faces=("north",), pts=[(-0.06, 0.0), (0.0, 0.5), (0.06, 0.0)], w=1.0, k=0.16, lip=0.0)]),
        *[box(sx * 2.75, 6.25, -2.5, 1.3, 1.0, 2.6, "bone", seed=214 + (sx > 0)) for sx in (-1, 1)],
        # the upper jaw, narrower, with the nose's hole in it
        box(0, 5.0, -1.6, 4.6, 1.8, 3.6, "bone", seed=215, lava=dict(area=10.0, chance=0.6, seed=49, rim="soot"),
            paint_faces=[dict(faces=("down",), ramp="hot", tone=0.34)],
            holes=[dict(x=0.32, y=5.75, r=(0.3, 0.5)), dict(x=-0.32, y=5.75, r=(0.3, 0.5)), dict(x=0.0, y=5.25, r=(0.36, 0.3))]),
        box(0, 3.9, -3.55, 4.2, 0.9, 0.4, "teeth", n=7, fangs=0, small=0.8, up=True, skip=("up", "down", "south")),
        *[box(sx * 3.45, 6.6, -0.6, 0.6, 2.2, 3.0, "bone", seed=211) for sx in (-1, 1)],
    ])
    B.bone("jaw", "head", [0, 4.6, 0.6], [
        box(0, 3.2, -1.3, 4.6, 1.2, 3.8, "bone", seed=212, flush=dict(ramp="soot", tone=0.4, weight=0.45, low=0.5),
            paint_faces=[dict(faces=("up",), ramp="hot", tone=0.3)]),
        box(0, 4.05, -3.0, 3.8, 0.75, 0.4, "teeth", n=6, fangs=0, small=0.8, up=False, skip=("up", "down", "south")),
    ], rot=[10, 0, 0])
    # the flames: tongues of fire leaning back from the crown, longest in the middle
    flames = []
    for i, (x, h, lean, w) in enumerate(((0.0, 6.0, -26, 2.6), (-2.0, 4.4, -34, 2.0), (2.0, 4.6, -30, 2.0), (-1.0, 3.2, -48, 1.6), (1.2, 3.4, -44, 1.6))):
        z = 0.6 + (i % 2) * 1.2
        flames.append(B.c([x - w / 2, 9.6, z - w / 2], [w, h * 1.4, w], "glow", r="flame", pivot=[x, 10.4, z], rot=[lean, 0, x * -8], fx=flame_fx, seed=220 + i))
    B.bone("flames", "head", [0, 10.4, 0.6], flames)
    # sparks circling it
    B.bone("embers", "root", [0, 6, 0], [box(4.4 * math.cos(a), 5.0 + 2 * math.sin(a * 2), 4.4 * math.sin(a), sz, sz, sz, "glow", r="flame")
                                         for a, sz in ((0, 0.55), (1.6, 0.4), (3.1, 0.5), (4.6, 0.35))])
    register(name, lambda aff: pal(aff, (255, 150, 50), {"bone": (198, 182, 152), "soot": (54, 40, 32)}, extra=flame_ramp(aff)), density=7.0)
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
