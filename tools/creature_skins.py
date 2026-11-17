#!/usr/bin/env python3
"""
Materials for skins painted on the model, for the gate's creatures (used by gen_creatures.py).

solid_skins.py paints the hounds and the wyrm with a painter written for each of them. This is the same idea made
reusable: a creature's model says what each cube is made of (skin, fur, scales, chitin, armour plate, cloth, bone,
rock, ice, wood, hair, wing membrane, a void, glowing crystal ...) and how its colours split (a dark back, pale
belly, a lit front), and every material knows how to paint itself at any texel from where that texel sits on the
creature (its X, Y, Z in model units). On top of that a creature adds its own features with the helpers here: eyes
that glow, a mouth, teeth cut out of the texture, war paint, veins and runes of light.

The look aimed for is a dark-fantasy comic's: dark, cold shadows and warm highlights, hard edges between colour
areas, lit top edges and dark crevices so the blocks read as carved shapes, and light (eyes, runes, cracks) in the
creature's affinity colour, drawn full-bright through the glow mask.

Every variant (affinity) of a creature has the same pattern in its own colours, plus marks of its own: rime on the
frost ones, ember cracks on the flame ones, venom blotches, storm veins, shade smoke, blood veins. A layer whose
ramp is missing from a palette is skipped (solid_skins.Paint.render), which is how a variant leaves out the others'
marks.
"""
import math

import numpy as np

import solid_skins as S
from solid_skins import cool, fbm2, fbm3, fur, hash01, jag, mix, mul, scale_cells, seg_dist, sstep, vnoise2, vnoise3, warm

WHITE = (255, 255, 255)
AFFS = ("plain", "flame", "frost", "venom", "storm", "shade", "blood")

AFF_GLOW = {"flame": (255, 128, 48), "frost": (120, 205, 255), "venom": (140, 232, 80), "storm": (178, 168, 255),
            "shade": (168, 108, 240), "blood": (228, 44, 56)}
# what an affinity does to a creature's body colours (a dark tone of its light)
AFF_BODY = {"flame": (92, 34, 22), "frost": (150, 184, 214), "venom": (66, 86, 36), "storm": (58, 62, 104),
            "shade": (46, 30, 66), "blood": (96, 18, 26)}


# --------------------------------------------------------------------------------------------- colours

def ramp(c, deep=0.16, dark=0.5, lit=0.3, hi=0.62):
    """Five anchors from a mid colour: shadows pushed cool, highlights warm."""
    c = tuple(float(v) for v in c)
    return [cool(c, deep), cool(c, dark), c, warm(c, lit), warm(c, hi)]


def glow_ramp(g):
    return [mul(g, 0.34), mul(g, 0.72), tuple(g), mix(g, WHITE, 0.5), mix(g, WHITE, 0.88)]


def tint(c, aff, k=0.22):
    """A body colour pulled towards the affinity's own dark tone."""
    if aff == "plain":
        return tuple(c)
    return mix(c, AFF_BODY[aff], k)


def glow_of(aff, plain):
    return tuple(plain) if aff == "plain" else AFF_GLOW[aff]


BASE_RAMPS = {
    "black": [(3, 3, 6), (12, 12, 18), (30, 30, 40), (64, 66, 82), (120, 124, 146)],
    "ivory": [(70, 58, 48), (140, 126, 104), (206, 194, 168), (238, 232, 214), (255, 255, 248)],
    "mouth": [(16, 4, 8), (48, 10, 18), (92, 22, 30), (146, 46, 52), (196, 88, 86)],
    "tongue": [(70, 16, 30), (128, 34, 48), (184, 62, 74), (222, 106, 110), (246, 168, 164)],
    "flesh": [(26, 14, 22), (56, 32, 42), (92, 58, 66), (136, 96, 100), (188, 148, 146)],
    "steel": [(10, 12, 18), (44, 48, 60), (104, 110, 126), (172, 178, 192), (236, 240, 248)],
    "iron": [(6, 6, 10), (24, 24, 30), (50, 50, 58), (92, 92, 102), (150, 150, 162)],
    "gold": [(40, 22, 6), (110, 70, 20), (186, 140, 60), (232, 196, 110), (255, 240, 190)],
    "bronze": [(30, 16, 8), (84, 50, 24), (142, 94, 50), (196, 146, 92), (240, 206, 156)],
    "leather": [(14, 9, 6), (42, 28, 20), (82, 56, 38), (128, 92, 64), (178, 140, 104)],
    "wood": [(16, 10, 6), (46, 30, 18), (88, 60, 36), (134, 98, 62), (186, 150, 106)],
    "rope": [(30, 22, 12), (70, 54, 32), (120, 98, 64), (170, 146, 104), (214, 196, 156)],
    "moss": [(8, 18, 8), (24, 44, 18), (52, 82, 34), (96, 128, 58), (150, 178, 96)],
    "rust": [(26, 10, 4), (66, 28, 12), (118, 56, 26), (164, 96, 52), (206, 150, 100)],
    "void": [(1, 1, 3), (4, 3, 9), (10, 8, 20), (24, 20, 42), (46, 40, 74)],
    "blood": [(20, 2, 4), (58, 6, 10), (108, 14, 20), (160, 34, 38), (210, 80, 78)],
}


def palette(aff, plain_glow, ramps, tinted=(), tint_k=0.22, extra=None):
    """A creature's palette for one affinity. ramps: {name: mid colour or a ramp (list)}; the names in tinted are pulled
    towards the affinity's tone. The glow ramp is the affinity's colour (the creature's own when plain), and the
    affinity's marks get their ramps."""
    out = dict(BASE_RAMPS)
    g = glow_of(aff, plain_glow)
    for name, c in ramps.items():
        if isinstance(c, list):
            out[name] = [tint(v, aff, tint_k) if name in tinted else v for v in c]
        else:
            out[name] = ramp(tint(c, aff, tint_k) if name in tinted else c)
    out["glow"] = glow_ramp(g)
    out["crystal"] = [mul(g, 0.16), mul(g, 0.42), mul(g, 0.8), mix(g, WHITE, 0.22), mix(g, WHITE, 0.62), WHITE]
    out["hot"] = [mul(g, 0.3), mul(g, 0.64), g, mix(g, (255, 240, 200), 0.6), (255, 252, 240)]
    if aff == "frost":
        out["mk_frost"] = [(70, 96, 128), (130, 166, 200), (196, 222, 244), (228, 242, 255), (255, 255, 255)]
    elif aff == "flame":
        out["mk_char"] = [(6, 3, 3), (20, 10, 8), (40, 22, 16), (70, 40, 28), (110, 70, 50)]
        out["mk_flame"] = out["hot"]
    elif aff == "venom":
        out["mk_venom"] = [(20, 40, 8), (48, 92, 18), (110, 180, 44), (170, 230, 96), (220, 255, 170)]
    elif aff == "storm":
        out["mk_storm"] = [mul(g, 0.4), mul(g, 0.8), g, mix(g, WHITE, 0.6), WHITE]
    elif aff == "shade":
        out["mk_shade"] = [(2, 1, 6), (10, 6, 20), (26, 16, 44), (52, 34, 82), (90, 64, 130)]
        out["mk_wisp"] = glow_ramp(g)
    elif aff == "blood":
        out["mk_blood"] = BASE_RAMPS["blood"]
        out["mk_vein"] = glow_ramp(g)
    if extra:
        out.update(extra)
    return out


# --------------------------------------------------------------------------------------------- helpers

def zeros(f):
    return np.zeros(f.X.shape)


def flat(f, v):
    return np.full(f.X.shape, float(v))


def across(f):
    """The coordinate across a wall face (z on the sides, x on the front and back)."""
    return f.Z if f.side else f.X


def light_pass(p, f, s, k=1.0):
    """The shared light: lit tops and top edges, darker undersides and bottom edges, a cube's walls darker towards
    its bottom, everything darker near the ground."""
    face = f.face
    tex = f.texel
    d = {"up": 0.10, "down": -0.26, "north": 0.02, "south": -0.06, "east": -0.02, "west": -0.02}[face]
    p.shade(d * k)
    if f.wall:
        h = max(f.top - f.bottom, 1e-6)
        p.shade(k * (0.10 * sstep(f.bottom, f.top, f.Y) - 0.06))
        p.shade(k * 0.14, f.Y > f.top - 1.15 * tex)                       # light catching the top edge
        p.shade(-k * 0.10, f.Y < f.bottom + 1.15 * tex)                    # the crease underneath
        edge = (f.U < 1.05 / max(f.w, 1)) | (f.U > 1 - 1.05 / max(f.w, 1))
        p.shade(-k * 0.05, edge & (h > 3 * tex))
    elif face == "up":
        rim = (f.U < 1.05 / max(f.w, 1)) | (f.U > 1 - 1.05 / max(f.w, 1)) | (f.V < 1.05 / max(f.h, 1)) | (f.V > 1 - 1.05 / max(f.h, 1))
        p.shade(k * 0.08, rim)
    ground = s.get("ground", 0.0)
    p.shade(-k * 0.14 * sstep(ground + 5.0, ground, f.Y))


# ----------------------------------------------------------------------------- colour areas

def zone_masks(f, s):
    """(back, belly) weights for a cube by its zone: 'back' splits it by height (dark on top, pale below), 'front' has
    a pale front, 'limb' a pale inner side, 'dark'/'light' are all one. Borders are jagged."""
    z = s.get("zone")
    none = np.zeros(f.X.shape, bool)
    full = np.ones(f.X.shape, bool)
    if not z:
        return none, none
    if z == "dark":
        return full, none
    if z == "light":
        return none, full
    seed = s.get("seed", 3)
    a = across(f) if f.wall else f.X
    h = max(f.top - f.bottom, 1e-6)
    hn = (f.Y - f.bottom) / h
    if z == "back":
        hi, lo = s.get("split", (0.62, 0.30))
        jb = 0.10 * jag(a * 1.3, seed, 1.5) + 0.04 * np.sin(a * 0.7 + seed)
        jl = 0.10 * jag(a * 1.3 + 0.7, seed + 1, 1.3)
        if f.face == "up":
            return full, none
        if f.face == "down":
            return none, full
        if f.face == "north" and s.get("bib", False):
            ax = np.abs(f.X - s.get("cx", 0.0))
            w = s.get("bib_w", 0.3) * f.s[0] * (0.6 + 0.6 * (1 - hn))
            return (hn > hi + 0.15 + jb) & (ax > w), ax < w - 0.4 * jag(f.Y * 1.4, seed + 2, 1.2)
        return hn > hi + jb, hn < lo - jl
    if z == "front":
        # upright bodies: the front is pale in the middle, the back and the tops are dark
        if f.face == "north":
            ax = np.abs(f.X - s.get("cx", 0.0))
            w = s.get("bib_w", 0.32) * f.s[0]
            return ax > w + 0.6 + 0.5 * jag(f.Y, seed, 1.3), ax < w - 0.5 * jag(f.Y + 0.4, seed + 1, 1.2)
        if f.face in ("south", "up"):
            return full, none
        if f.face == "down":
            return none, none
        zf = (f.Z - f.o[2]) / max(f.s[2], 1e-6)
        return zf > 0.55 + 0.1 * jag(f.Y, seed + 2, 1.4), zf < 0.18 - 0.08 * jag(f.Y, seed + 3, 1.2)
    if z == "limb":
        if f.face == "up":
            return full, none
        if f.face == "down":
            return none, full
        if f.inner:
            return none, full
        if f.face == "south":
            return full, none
        return hn > 0.78 + 0.1 * jag(a, seed, 1.2), none
    return none, none


def base(p, f, s, r, t):
    """Fills with ramp r at tone t, then the back and belly ramps over their zones."""
    p.fill(r, t)
    back, belly = zone_masks(f, s)
    rb, rl = s.get("back", r + "_d"), s.get("belly", r + "_l")
    p.over(rb, t + s.get("back_t", 0.0), back)
    p.over(rl, t + s.get("belly_t", 0.04), belly)


# ----------------------------------------------------------------------------- materials

def m_skin(f, p, s):
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 11)
    t = 0.5 + (fbm3(X * 0.42 + 3, Y * 0.42, Z * 0.42, sd, 3) - 0.5) * s.get("mottle", 0.2)
    t = t + (vnoise3(X * 2.4, Y * 2.4, Z * 2.4, sd + 7) - 0.5) * 0.07                 # pores
    if s.get("wrinkle", 0):
        w = np.abs(np.sin((Y + 0.4 * np.sin(across(f) * 1.7)) * s["wrinkle"]))
        t = t - 0.08 * (w < 0.18)
    base(p, f, s, s.get("r", "skin"), t)
    if s.get("veins"):
        n = fbm3(X * 0.5, Y * 0.5, Z * 0.5, sd + 21, 2)
        p.over(s.get("r", "skin") + "_d", 0.32, np.abs(n - 0.5) < 0.018)


def m_fur(f, p, s):
    X, Y, Z = f.X, f.Y, f.Z
    if f.face in ("up", "down"):
        sa, ta = Z + 0.4 * np.abs(X), X
    elif f.side:
        sa, ta = Z * 0.5 - Y * 0.87, Z * 0.87 + Y * 0.5
    else:
        sa, ta = -Y, X
    t = 0.5 + fur(sa, ta, s.get("seed", 20), s.get("len", 2.4), s.get("width", 1.5))
    base(p, f, s, s.get("r", "fur"), t)


def m_scales(f, p, s):
    X, Y, Z = f.X, f.Y, f.Z
    size = s.get("size", 1.6)
    if f.face in ("up", "down"):
        c = scale_cells(X, Z if s.get("dir", 1) > 0 else -Z, size, s.get("seed", 30))
    elif f.side:
        c = scale_cells(Y, Z if s.get("dir", 1) > 0 else -Z, size, s.get("seed", 31))
    else:
        c = scale_cells(X, -Y, size, s.get("seed", 32))
    t = 0.5 + c["tone"]
    base(p, f, s, s.get("r", "scale"), t)
    p.over("black", 0.16, c["seam"] > 0.75)


def m_chitin(f, p, s):
    """Glossy plates in bands along an axis: lit leading edge, a black seam, a sharp highlight, fine pits."""
    X, Y, Z = f.X, f.Y, f.Z
    axis = s.get("axis", "z")
    a = {"z": Z, "y": -Y, "x": X}[axis]
    length = s.get("band", 2.2)
    k = np.floor(a / length)
    t = a / length - k
    rnd = hash01(s.get("seed", 40), k.astype(np.int64))
    tone = 0.46 + (rnd - 0.5) * 0.08 + 0.18 * sstep(0.32, 0.0, t) - 0.20 * sstep(0.55, 0.94, t)
    tone = tone + (vnoise3(X * 3.1, Y * 3.1, Z * 3.1, s.get("seed", 40) + 5) - 0.5) * 0.08
    base(p, f, s, s.get("r", "chitin"), tone)
    seam = t > 0.92
    if axis == "y" and f.face in ("up", "down"):
        seam = np.zeros(X.shape, bool)
    p.over("black", 0.12, seam)
    # the gloss: a hard streak of light across the upper part of each plate
    if f.wall and axis != "y":
        hn = (Y - f.bottom) / max(f.top - f.bottom, 1e-6)
        streak = np.exp(-((hn - 0.72) / 0.07) ** 2) * (t > 0.08) * (t < 0.8)
        p.over(s.get("r", "chitin"), 0.92, streak > 0.55)
    elif f.face == "up":
        across_ = np.abs(X - f.cx) / max(f.s[0] / 2, 1e-6)
        streak = (np.abs(across_ - 0.45) < 0.10) & (t > 0.1) & (t < 0.75)
        p.over(s.get("r", "chitin"), 0.9, streak)


def m_plate(f, p, s):
    """Armour: dark worked metal with a bevel (lit top and left edges), rivets, scratches and grime."""
    X, Y, Z = f.X, f.Y, f.Z
    r = s.get("r", "plate")
    sd = s.get("seed", 50)
    tex = f.texel
    t = 0.46 + (fbm3(X * 0.6, Y * 0.6, Z * 0.6, sd, 2) - 0.5) * 0.14
    if f.wall:
        t = t + 0.08 * sstep(f.bottom, f.top, Y)
    p.fill(r, t)
    bw = max(1.05 / max(f.w, 1), 0.55 * tex / max(f.fw, 1e-6))
    bh = max(1.05 / max(f.h, 1), 0.55 * tex / max(f.fh, 1e-6))
    if min(f.fw, f.fh) > 1.4:
        p.over(r, 0.88, f.V < bh)                       # the lit top bevel
        p.over(r, 0.70, (f.U < bw) & (f.V >= bh))
        p.over(r, 0.12, f.V > 1 - bh)                   # the dark lower bevel
        p.over(r, 0.24, (f.U > 1 - bw) & (f.V <= 1 - bh))
    if s.get("rivets", True) and f.fw > 3.2 and f.fh > 3.2:
        for u, v in ((0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)):
            cx = (u * (f.fw - 2.2 * tex - 0.5) + 1.1 * tex + 0.25)
            cy = (v * (f.fh - 2.2 * tex - 0.5) + 1.1 * tex + 0.25)
            d = np.hypot(f.P - cx, f.Q - cy)
            p.over(s.get("rivet", "trim"), 0.62 - 0.4 * ((f.P - cx) + (f.Q - cy)) / max(tex * 2, 0.3), d < max(tex * 1.1, 0.32))
    # scratches
    sc = np.zeros(X.shape)
    for i in range(int(f.fw * f.fh / 6) + 1):
        ax, ay = hash01(sd + i, 1) * f.fw, hash01(sd + i, 2) * f.fh
        ang = hash01(sd + i, 3) * math.pi
        ln = 0.6 + 1.6 * hash01(sd + i, 4)
        d, tt = seg_dist(f.P, f.Q, ax, ay, ax + math.cos(ang) * ln, ay + math.sin(ang) * ln)
        sc = np.maximum(sc, (d < tex * 0.5) * 1.0)
    p.over(r, 0.78, sc > 0.5)
    if f.wall and s.get("grime", True):
        p.shade(-0.14 * sstep(f.bottom + 0.35 * (f.top - f.bottom), f.bottom, Y))
    if s.get("engrave"):
        # an engraved line just inside the edge, filled with the trim colour
        inset = 1.6 * tex
        ring = ((np.abs(f.P - inset) < tex * 0.5) | (np.abs(f.P - (f.fw - inset)) < tex * 0.5)) & (f.Q > inset - tex * 0.5) & (f.Q < f.fh - inset + tex * 0.5)
        ring |= ((np.abs(f.Q - inset) < tex * 0.5) | (np.abs(f.Q - (f.fh - inset)) < tex * 0.5)) & (f.P > inset - tex * 0.5) & (f.P < f.fw - inset + tex * 0.5)
        if min(f.fw, f.fh) > 3.0:
            p.over(s.get("engrave"), 0.5, ring)


def m_trim(f, p, s):
    t = 0.5 + 0.28 * np.sin(np.clip(f.V, 0, 1) * math.pi) - 0.14 + (vnoise3(f.X * 2, f.Y * 2, f.Z * 2, 55) - 0.5) * 0.12
    p.fill(s.get("r", "trim"), t)
    if f.wall and f.fh > 1.2:
        p.over(s.get("r", "trim"), 0.9, f.V < 1.05 / max(f.h, 1))
        p.over(s.get("r", "trim"), 0.1, f.V > 1 - 1.05 / max(f.h, 1))


def m_cloth(f, p, s):
    """Heavy cloth: folds that hang down, a weave, a darker lining at the hem, and a torn hem cut out of it."""
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 60)
    a = across(f) if f.wall else X
    fold = np.sin(a * s.get("folds", 1.3) + (vnoise2(a * 0.4, Y * 0.2, sd) - 0.5) * 3.0)
    t = 0.48 + 0.12 * fold + (vnoise3(X * 1.8, Y * 0.6, Z * 1.8, sd + 1) - 0.5) * 0.12
    t = t + 0.03 * ((np.floor(f.P / max(f.texel, 1e-6)) + np.floor(f.Q / max(f.texel, 1e-6))) % 2)
    base(p, f, s, s.get("r", "cloth"), t)
    if f.wall:
        p.shade(-0.10 * (fold < -0.6))
    if s.get("band") and f.wall:
        # an embroidered band near the hem, with stitched runes of light
        y0, y1 = s["band"]
        inb = (Y > y0) & (Y < y1)
        p.over(s.get("band_r", "trim"), 0.45 + 0.2 * ((Y - y0) / max(y1 - y0, 1e-6)), inb)
        if s.get("runes", True):
            cell = np.floor(a / 1.1)
            g = hash01(sd + 9, cell.astype(np.int64), np.floor((Y - y0) / max((y1 - y0) / 3, 1e-6)).astype(np.int64))
            lit = inb & (Y > y0 + 0.25 * (y1 - y0)) & (Y < y1 - 0.25 * (y1 - y0)) & (g > 0.45) & ((a / 1.1 - cell) > 0.2) & ((a / 1.1 - cell) < 0.8)
            p.glow("glow", 0.7, lit)
    if s.get("hem") and f.wall:
        depth = s["hem"]
        edge = f.bottom + depth * (0.25 + 0.9 * jag(a * 1.1, sd + 3, 1.0, 1.0))
        p.cut(Y > edge)
        p.shade(-0.12 * sstep(f.bottom + depth * 2.2, f.bottom, Y))
    if s.get("tatter") and f.face in ("up", "down"):
        pass


def m_leather(f, p, s):
    X, Y, Z = f.X, f.Y, f.Z
    t = 0.5 + (fbm3(X * 0.9, Y * 0.9, Z * 0.9, s.get("seed", 70), 2) - 0.5) * 0.24
    p.fill(s.get("r", "leather"), t)
    tex = f.texel
    if f.fw > 2.2 * tex and f.fh > 2.2 * tex and s.get("stitch", True):
        inset = 0.9 * tex
        line = ((np.abs(f.Q - inset) < tex * 0.5) | (np.abs(f.Q - (f.fh - inset)) < tex * 0.5))
        dots = (np.floor(f.P / max(tex, 1e-6)) % 2) == 0
        p.over(s.get("r", "leather"), 0.85, line & dots)
    p.over(s.get("r", "leather"), 0.18, vnoise3(X * 2.6, Y * 2.6, Z * 2.6, 72) > 0.82)


def m_bone(f, p, s):
    """Old bone: pale at the middle of the cube, browner towards its ends and crevices, a few cracks."""
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 80)
    n = fbm3(X * 0.8, Y * 0.8, Z * 0.8, sd, 3)
    t = 0.58 + (n - 0.5) * 0.24
    if f.wall:
        t = t - 0.16 * sstep(0.62, 1.0, np.abs(f.V * 2 - 1)) * s.get("ends", 1.0)
    p.fill(s.get("r", "bone"), t)
    cr = np.abs(fbm3(X * 1.3 + 7, Y * 1.3, Z * 1.3, sd + 3, 2) - 0.5)
    p.over(s.get("r", "bone"), 0.16, (cr < 0.022) & (n > 0.42))
    if s.get("rings"):
        p.over(s.get("r", "bone"), 0.3, (np.abs(np.sin(Y * s["rings"])) < 0.16) & f.wall)


def m_horn(f, p, s):
    """Horn, claw or tusk: dark at the root, pale and worn at the tip, growth rings. s['tip']: the axis and way to
    the tip, e.g. '+y', '-z'."""
    tip = s.get("tip", "+y")
    sign = 1 if tip[0] == "+" else -1
    coord = {"x": f.X, "y": f.Y, "z": f.Z}[tip[1]]
    lo, hi = s.get("span", (None, None))
    if lo is None:
        o = {"x": f.o[0], "y": f.o[1], "z": f.o[2]}[tip[1]]
        sz = {"x": f.s[0], "y": f.s[1], "z": f.s[2]}[tip[1]]
        lo, hi = (o, o + sz) if sign > 0 else (o + sz, o)
    k = np.clip((coord - lo) / (hi - lo) if hi != lo else 0.5, 0, 1)      # 0 at the root .. 1 at the tip
    t = 0.24 + 0.62 * k ** 0.8 + (vnoise3(f.X * 3, f.Y * 3, f.Z * 3, 91) - 0.5) * 0.08
    t = t - 0.07 * ((k * s.get("rings", 7.0)) % 1 > 0.78)
    p.fill(s.get("r", "horn"), t)


def worley3(X, Y, Z, size, seed):
    """Cells around jittered points in space: (distance to the nearest point, to the second nearest, a 0..1 number
    for the nearest point's cell)."""
    px, py, pz = X / size, Y / size, Z / size
    ix, iy, iz = np.floor(px), np.floor(py), np.floor(pz)
    d1 = np.full(X.shape, 9.0)
    d2 = np.full(X.shape, 9.0)
    idv = np.zeros(X.shape)
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = ix + dx, iy + dy, iz + dz
                fx = cx + 0.15 + 0.7 * hash01(seed, cx, cy, cz)
                fy = cy + 0.15 + 0.7 * hash01(seed + 1, cx, cy, cz)
                fz = cz + 0.15 + 0.7 * hash01(seed + 2, cx, cy, cz)
                d = np.sqrt((px - fx) ** 2 + ((py - fy) * 1.25) ** 2 + (pz - fz) ** 2)
                closer = d < d1
                d2 = np.where(closer, d1, np.minimum(d2, d))
                idv = np.where(closer, hash01(seed + 3, cx, cy, cz), idv)
                d1 = np.where(closer, d, d1)
    return d1 * size, d2 * size, idv


def m_boulder(f, p, s):
    """Rough stone in big chipped slabs (not bricks): each slab its own tone, lit along its top, shadowed under it,
    dark seams between them, pits and fine cracks, moss only in patches on what faces up."""
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 100)
    size = s.get("cell", 3.0)
    d1, d2, cid = worley3(X, Y, Z, size, sd)
    edge = d2 - d1                                           # 0 on a seam
    r = s.get("r", "rock")
    t = 0.42 + (cid - 0.5) * 0.22 + (fbm3(X * 0.8, Y * 0.8, Z * 0.8, sd + 2, 3) - 0.5) * 0.2
    t = t + 0.08 * sstep(0.0, 0.8, edge) - 0.05
    p.fill(r, t)
    if f.wall:
        # each slab catches the light along its top and is dark under its lower edge
        dy = max(f.texel * 1.6, 0.4)
        above = worley3(X, Y + dy, Z, size, sd)[2] != cid
        below = worley3(X, Y - dy, Z, size, sd)[2] != cid
        p.over(r, 0.76 + (cid - 0.5) * 0.1, above & (edge > 0.16))
        p.over(r, 0.16, below & ~above & (edge > 0.16))
    p.over("black", 0.14, edge < 0.16)
    pits = vnoise3(X * 2.2, Y * 2.2, Z * 2.2, sd + 4)
    p.over(r, 0.18, pits > 0.86)
    p.over(r, 0.72, (pits < 0.08) & (edge > 0.4))
    cr = np.abs(fbm3(X * 0.6 + 11, Y * 0.6, Z * 0.6, sd + 5, 3) - 0.5)
    p.over("black", 0.2, (cr < 0.014) & (edge > 0.3))
    if s.get("moss", 0) > 0:
        n = fbm3(X * 0.45, Y * 0.45, Z * 0.45, sd + 7, 3)
        if f.face == "up":
            p.over("moss", 0.36 + (n - 0.5) * 0.6, n > 0.66 - 0.2 * s["moss"])
        elif f.wall:
            drip = sstep(f.top - 1.2 - 1.2 * jag(across(f), sd + 8, 0.9), f.top, Y)
            p.over("moss", 0.32 + (n - 0.5) * 0.5, (drip > 0.5) & (n > 0.7 - 0.2 * s["moss"]))
            p.over("moss", 0.28, (edge < 0.3) & (n > 0.74 - 0.2 * s["moss"]))


def m_rock(f, p, s):
    """Stone: blocks split by cracks, chips, moss on what faces up."""
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 100)
    size = s.get("cell", 2.4)
    a = across(f) if f.wall else X
    b = Y if f.wall else Z
    row = np.floor(b / (size * 0.7))
    off = hash01(sd, row.astype(np.int64)) * size
    col = np.floor((a + off) / size)
    rnd = hash01(sd + 1, row.astype(np.int64), col.astype(np.int64))
    u = (a + off) / size - col
    v = b / (size * 0.7) - row
    t = 0.44 + (rnd - 0.5) * 0.18 + (fbm3(X * 0.9, Y * 0.9, Z * 0.9, sd + 2, 3) - 0.5) * 0.22
    t = t + 0.10 * sstep(0.35, 0.95, v) - 0.06 * sstep(0.4, 0.0, v)
    p.fill(s.get("r", "rock"), t)
    seam = (u < 0.06) | (v < 0.08)
    p.over("black", 0.18, seam & (rnd > 0.12))
    cr = np.abs(fbm3(X * 0.7 + 11, Y * 0.7, Z * 0.7, sd + 5, 3) - 0.5)
    p.over(s.get("r", "rock"), 0.14, cr < 0.02)
    if s.get("moss", 0) > 0:
        n = fbm3(X * 0.5, Y * 0.5, Z * 0.5, sd + 7, 3)
        if f.face == "up":
            p.over("moss", 0.45 + (n - 0.5) * 0.6, n > 0.55 - 0.25 * s["moss"])
        elif f.wall:
            drip = sstep(f.top - 1.6 - 1.5 * jag(a, sd + 8, 0.9), f.top, Y)
            p.over("moss", 0.4 + (n - 0.5) * 0.5, (drip > 0.5) & (n > 0.62 - 0.3 * s["moss"]))


def m_crystal(f, p, s):
    """A glowing crystal: facets and a glint, shaded in its own colours (it is drawn full-bright)."""
    v = f.V
    side_light = {"up": 0.16, "north": 0.08, "west": 0.0, "east": -0.14, "south": -0.2, "down": -0.3}[f.face]
    tone = 0.72 - 0.5 * v ** 1.3 + side_light
    if f.wall:
        tone = tone + 0.14 * (f.U < 0.16) + 0.08 * (f.U > 0.86)
        tone = tone + 0.16 * ((f.U + 0.55 * v) % 0.9 < 0.2)
        tone = tone - 0.14 * ((f.U - 0.45 * v) % 1.0 > 0.72)
        tone = np.where((np.abs(f.U - 0.3) < 0.07) & (v > 0.15) & (v < 0.7), 0.98, tone)
    p.fill("crystal", 0.5)
    p.glow(s.get("r", "crystal"), tone)


def m_ice(f, p, s):
    """Ice that is not alight: cold and clear, broad facets of lighter and darker ice, a lit top edge, a darker
    depth below, a few fine cracks and rare glints."""
    X, Y, Z = f.X, f.Y, f.Z
    sd = s.get("seed", 110)
    r = s.get("r", "ice")
    t = 0.5 + (fbm3(X * 0.35, Y * 0.35, Z * 0.35, sd, 2) - 0.5) * 0.16
    if f.wall:
        k = hash01(sd, int(round(f.cx * 7 + f.cz * 3 + f.cy * 5)))
        t = t + 0.12 * (((f.U * 0.9 + f.V * 0.55 + k) % 1.0) < 0.34) - 0.10 * (((f.U - 0.6 * f.V + k) % 1.0) > 0.74)
        t = t + 0.16 * (1 - f.V) - 0.08
        p.fill(r, t)
        p.over(r, 0.92, f.V < 1.05 / max(f.h, 1))
        p.over(r, 0.16, f.V > 1 - 1.05 / max(f.h, 1))
    else:
        p.fill(r, t + (0.18 if f.face == "up" else -0.2))
    cr = np.abs(fbm3(X * 0.9, Y * 0.9, Z * 0.9, sd + 4, 2) - 0.5)
    p.over(r, 0.88, cr < 0.012)
    if s.get("glint", True):
        p.glow("glow", 0.8, (cr < 0.005) & (vnoise3(X * 1.7, Y * 1.7, Z * 1.7, sd + 5) > 0.72))


def m_wood(f, p, s):
    X, Y, Z = f.X, f.Y, f.Z
    long = s.get("long", "y")
    a = {"x": X, "y": Y, "z": Z}[long]
    c1 = {"x": Y, "y": X, "z": X}[long]
    c2 = {"x": Z, "y": Z, "z": Y}[long]
    g = np.sin((c1 + c2) * 6.0 + fbm3(a * 0.3, c1, c2, s.get("seed", 120), 2) * 6.0)
    t = 0.48 + 0.12 * g + (vnoise2(a * 0.5, c1 * 3 + c2 * 3, 121) - 0.5) * 0.12
    p.fill(s.get("r", "wood"), t)


def m_hair(f, p, s):
    """Long hair falling down: strands of slightly different tones, a sheen band, ragged ends cut out."""
    X, Y, Z = f.X, f.Y, f.Z
    a = across(f) if f.wall else X
    col = np.floor(a / max(f.texel * 0.9, 0.12))
    j = hash01(s.get("seed", 130), col.astype(np.int64))
    t = 0.46 + (j - 0.5) * 0.3 + (vnoise2(a * 2.0, Y * 0.25, 131) - 0.5) * 0.2
    t = t + 0.24 * np.exp(-((Y - (f.top - 0.22 * (f.top - f.bottom)) + 0.5 * np.sin(a * 1.3)) / 0.7) ** 2)
    p.fill(s.get("r", "hair"), t)
    if f.wall and s.get("ends", True):
        p.cut(Y > f.bottom + (0.2 + 1.6 * jag(a * 1.4, 133, 0.7, 1.0)) * s.get("ragged", 1.0))


def m_membrane(f, p, s):
    """A wing membrane: thin skin with veins, darker by the bones, cut out along a scalloped trailing edge."""
    X, Y, Z = f.X, f.Y, f.Z
    pp, qq = np.abs(X) - s.get("root", 0.0), Z - s.get("lead", 0.0)
    vein = np.abs(fbm2(pp * 0.3 + 3, qq * 0.3, 140, 2) - 0.5)
    t = 0.3 + 0.35 * sstep(0, s.get("depth", 10.0), qq) + (vnoise2(pp * 0.8, qq * 0.8, 141) - 0.5) * 0.1
    t = t - 0.16 * sstep(0.03, 0.01, vein)
    p.fill(s.get("r", "membrane"), t)
    fingers = s.get("fingers", ())
    if fingers:
        dmin = np.full(X.shape, 99.0)
        for fx, fz in fingers:
            d, tt = seg_dist(pp, qq, 0.0, 0.0, fx, fz)
            dmin = np.minimum(dmin, d)
        p.over(s.get("bone_r", "bone"), 0.36 + 0.2 * sstep(0.45, 0.0, dmin), dmin < 0.42)
        # the scalloped edge between the finger ends
        ang = np.arctan2(qq, np.maximum(pp, 1e-6))
        r = np.hypot(pp, qq)
        ends = sorted(((math.atan2(fz, max(fx, 1e-6)), math.hypot(fx, fz)) for fx, fz in fingers))
        rmax = np.zeros(X.shape)
        for (a0, r0), (a1, r1) in zip(ends, ends[1:]):
            k = np.clip((ang - a0) / max(a1 - a0, 1e-6), 0, 1)
            inside = (ang >= a0) & (ang <= a1)
            rmax = np.where(inside, (r0 + (r1 - r0) * k) * (1 - 0.24 * np.sin(np.pi * k)), rmax)
        p.cut((r < rmax + 0.2) | (dmin < 0.42))


def m_void(f, p, s):
    t = 0.3 + (fbm3(f.X * 0.6, f.Y * 0.6 + 3, f.Z * 0.6, 150, 3) - 0.5) * 0.4
    p.fill("void", t)


def m_flesh(f, p, s):
    """Rotting flesh: grey-green and bruised, dark blotches and stringy sinew."""
    X, Y, Z = f.X, f.Y, f.Z
    n = fbm3(X * 0.5, Y * 0.5, Z * 0.5, s.get("seed", 160), 3)
    t = 0.48 + (n - 0.5) * 0.4
    base(p, f, s, s.get("r", "rot"), t)
    p.over("flesh", 0.3 + 0.2 * n, fbm3(X * 0.35 + 5, Y * 0.35, Z * 0.35, 161, 3) > 0.64)
    sinew = np.abs(np.sin((across(f) if f.wall else X) * 3.1 + fbm3(X, Y * 0.3, Z, 162, 2) * 5))
    p.over("flesh", 0.62, (sinew < 0.12) & (fbm3(X * 0.35 + 5, Y * 0.35, Z * 0.35, 161, 3) > 0.64))


def m_metal(f, p, s):
    """A polished blade: a bright edge along s['edge'] (the side or bottom that cuts), a dark fuller down the middle."""
    r = s.get("r", "steel")
    t = 0.46 + (vnoise3(f.X * 1.5, f.Y * 1.5, f.Z * 1.5, 170) - 0.5) * 0.12
    p.fill(r, t)
    edge = s.get("edge")
    if edge and f.wall:
        if edge in ("front", "back") and f.side:
            fu = f.U if f.face == "west" else 1 - f.U          # 0 at the front (-z) on both sides
            e = fu if edge == "front" else 1 - fu
        elif edge in ("front", "back"):
            e = np.full(f.X.shape, 0.5)
        else:
            e = {"down": 1 - f.V, "up": f.V}.get(edge, f.V)
        p.over(r, 0.95, e < 0.22)
        p.over(r, 0.82, (e >= 0.22) & (e < 0.36))
        p.over(r, 0.18, np.abs(f.V - 0.5) < 0.08)
    if s.get("nicks"):
        n = vnoise2(f.P * 2.0, 3.0, 171)
        p.cut(~((f.V > 0.9) & (n > 0.7)))


def m_glow(f, p, s):
    """A cube that is light: a gem, a flame, an eye, shaded in its own colours."""
    v = f.V if f.wall else 0.5
    t = 0.66 - 0.3 * v + {"up": 0.2, "down": -0.2}.get(f.face, 0.0) + (vnoise3(f.X * 3, f.Y * 3, f.Z * 3, 180) - 0.5) * 0.16
    p.fill(s.get("r", "glow"), 0.5)
    p.glow(s.get("r", "glow"), t)


def m_dark(f, p, s):
    p.fill(s.get("r", "black"), s.get("t", 0.25) + (vnoise3(f.X * 2, f.Y * 2, f.Z * 2, 190) - 0.5) * 0.1)


def m_mouth(f, p, s):
    p.fill("mouth", 0.28 + 0.18 * (f.V if f.wall else 0.5))


def m_chain(f, p, s):
    """Links of a chain along y."""
    a = f.Y
    k = (a / 0.9) % 1.0
    t = 0.42 + 0.3 * np.sin(k * math.pi) - 0.3 * (k < 0.12)
    p.fill(s.get("r", "iron"), t)
    across_ = across(f) if f.wall else f.X
    ring = np.abs((across_ - (f.cz if f.side else f.cx))) < 0.18
    p.over("black", 0.1, ring & (k > 0.25) & (k < 0.75) & ((np.floor(a / 0.9) % 2) == 0))


def m_teeth(f, p, s):
    """A row of teeth cut out of a strip: s['up'] hangs them down from the gum (upper jaw), else they stand up.
    On the sides along z, on the front along x; s['n'] teeth, s['fangs'] how many big ones at the ends."""
    up = s.get("up", True)
    if f.face in ("up", "down"):
        p.fill("ivory", 0.4)
        p.cut(np.zeros(f.X.shape, bool))
        return
    a = f.Z if f.side else f.X
    lo = f.o[2] if f.side else f.o[0]
    span = f.s[2] if f.side else f.s[0]
    n = max(1, int(s.get("n", 5)))
    depth = (f.top - f.Y) if up else (f.Y - f.bottom)
    keep = np.zeros(f.X.shape, bool)
    tone = np.zeros(f.X.shape)
    fangs = s.get("fangs", 1)
    for i in range(n):
        c = lo + (i + 0.5) * span / n
        hw = span / n * 0.55
        big = (i < fangs or i >= n - fangs) if not f.side else (i < fangs) == (s.get("front_at_low", True))
        if f.side and fangs and ((i == 0 and s.get("front_low", True)) or (i == n - 1 and not s.get("front_low", True))):
            big = True
        h = f.s[1] * (1.0 if big else s.get("small", 0.6))
        hwi = hw * (1.25 if big else 1.0)
        inside = depth < h * (1 - (np.abs(a - c) / hwi) ** 1.3)
        keep |= inside
        tone = np.where(inside, 0.5 + 0.38 * (depth / max(h, 1e-6)) - 0.22 * (np.abs(a - c) / hwi), tone)
    p.fill(s.get("r", "ivory"), tone)
    p.cut(keep)


MATS = {
    "skin": m_skin, "fur": m_fur, "scales": m_scales, "chitin": m_chitin, "plate": m_plate, "trim": m_trim,
    "cloth": m_cloth, "leather": m_leather, "bone": m_bone, "horn": m_horn, "rock": m_rock, "boulder": m_boulder, "crystal": m_crystal,
    "ice": m_ice, "wood": m_wood, "hair": m_hair, "membrane": m_membrane, "void": m_void, "flesh": m_flesh,
    "metal": m_metal, "glow": m_glow, "dark": m_dark, "mouth": m_mouth, "chain": m_chain, "teeth": m_teeth,
}
# materials that light their own way (no shared light pass) and take no affinity marks
SELF_LIT = {"crystal", "glow", "teeth", "void"}
NO_MARKS = SELF_LIT | {"mouth", "metal", "chain", "dark", "trim"}


# ----------------------------------------------------------------------------- features

def eye(p, f, centre, r, slant=0.0, pupil="slit", lid=True, axis=None):
    """A glowing eye painted on whatever face lies at centre (x, y, z) facing the way the face does: an almond of
    light with a dark rim, a pupil ('slit', 'dot', None) and a glint. r = (half width, half height); axis picks which
    face the eye is on ('north' for an eye looking ahead, 'west'/'east' for the sides of a head)."""
    cx, cy, cz = centre
    face = axis or "north"
    if f.face != face:
        return
    plane = {"north": f.Z, "south": f.Z, "west": f.X, "east": f.X}[face]
    pc = {"north": cz, "south": cz, "west": cx, "east": cx}[face]
    if np.abs(plane - pc).min() > 0.35:
        return
    a = f.X - cx if face in ("north", "south") else f.Z - cz
    if face == "east" or face == "south":
        a = -a
    b = f.Y - cy
    if face in ("north", "south") and cx < 0:
        a = -a                                  # mirror the slant for the right eye
    c, s_ = math.cos(slant), math.sin(slant)
    xa, xb = a * c + b * s_, -a * s_ + b * c
    ra, rb = r
    d = (np.abs(xa) / ra) ** 1.8 + (np.abs(xb) / rb) ** 1.8
    on = np.abs(plane - pc) < 0.35
    if lid:
        p.over("black", 0.05, on & (d < 2.0))
    p.glow("glow", 0.5 + 0.46 * np.clip(1 - d, 0, 1) ** 0.7, on & (d < 1.0))
    if pupil == "slit":
        p.over("black", 0.0, on & (np.abs(xa) < max(ra * 0.16, f.texel * 0.55)) & (np.abs(xb) < rb * 0.8))
    elif pupil == "dot":
        p.over("black", 0.0, on & (np.hypot(xa / ra, xb / rb) < 0.36))
    p.glow("glow", 1.0, on & (np.abs(xa + ra * 0.36) < f.texel * 0.75) & (np.abs(xb - rb * 0.28) < f.texel * 0.75))


def socket(p, f, centre, r, face="north", depth=0.35, core=0.42, flame=False):
    """An empty eye socket with a point of light deep in it (skulls, wraiths, golems)."""
    cx, cy, cz = centre
    if f.face != face:
        return
    plane = {"north": f.Z, "south": f.Z, "west": f.X, "east": f.X}[face]
    pc = {"north": cz, "south": cz, "west": cx, "east": cx}[face]
    if np.abs(plane - pc).min() > 0.35:
        return
    a = (f.X - cx) if face in ("north", "south") else (f.Z - cz)
    b = f.Y - cy
    d = np.hypot(a / r[0], b / r[1])
    on = np.abs(plane - pc) < 0.35
    p.over("black", 0.02 + 0.08 * d, on & (d < 1.0))
    p.over("black", 0.2, on & (d >= 1.0) & (d < 1.3))
    p.glow("glow", 0.45, on & (d < core * 1.6) & (d >= core))
    p.glow("glow", 0.97, on & (d < core))
    if flame:
        up = on & (b > 0) & (np.abs(a) < r[0] * 0.5 * (1 - b / (r[1] * 2.2))) & (b < r[1] * 2.2)
        p.glow("glow", 0.6, up & (d >= 0.9))


def line_glow(p, f, pts, width, plane_axis=None, core=True, halo=0.9, bright=1.0):
    """A line of light along a polyline pts [(a, b)] in the face's own plane coordinates (across, up) — z or x
    across and y up on walls, x and z on tops. Used for veins, scars, runes and cracks."""
    a = across(f) if f.wall else f.X
    b = f.Y if f.wall else f.Z
    dmin = np.full(f.X.shape, 99.0)
    tmin = np.zeros(f.X.shape)
    for (a0, b0), (a1, b1) in zip(pts, pts[1:]):
        d, t = seg_dist(a, b, a0, b0, a1, b1)
        closer = d < dmin
        dmin = np.where(closer, d, dmin)
        tmin = np.where(closer, t, tmin)
    body = dmin < width / 2
    p.glow("glow", 0.4, np.clip(1 - (dmin - width / 2) / halo, 0, 1) * 0.36 * bright * (dmin >= width / 2))
    p.glow("glow", 0.62, body)
    if core:
        p.glow("glow", 0.97, dmin < width * 0.2)
    return dmin


def runes(p, f, region, seed, cell=1.2, density=0.55, ramp_="glow"):
    """Glyphs of light in a grid on a face: region (a0, a1, b0, b1) in the face's (across, up) units."""
    a = across(f) if f.wall else f.X
    b = f.Y if f.wall else f.Z
    a0, a1, b0, b1 = region
    inside = (a > a0) & (a < a1) & (b > b0) & (b < b1)
    if not inside.any():
        return
    ca = np.floor((a - a0) / cell)
    cb = np.floor((b - b0) / cell)
    u = (a - a0) / cell - ca
    v = (b - b0) / cell - cb
    tex = f.texel / cell
    on = hash01(seed, ca.astype(np.int64), cb.astype(np.int64)) < density
    k = (hash01(seed + 1, ca.astype(np.int64), cb.astype(np.int64)) * 6).astype(int)
    w = max(tex * 0.9, 0.12)
    stroke = np.zeros(f.X.shape, bool)
    vert = np.abs(u - 0.5) < w
    hor_t = np.abs(v - 0.8) < w
    hor_m = np.abs(v - 0.5) < w
    hor_b = np.abs(v - 0.2) < w
    diag = np.abs((u - 0.2) - (v - 0.2)) < w * 1.4
    inner = (u > 0.18) & (u < 0.82) & (v > 0.15) & (v < 0.85)
    shapes = [vert | hor_t, vert | hor_m, hor_t | hor_b | diag, vert | diag, hor_m | (np.abs(u - 0.25) < w), vert | hor_b | hor_t]
    for i, sh in enumerate(shapes):
        stroke |= (k == i) & sh
    p.glow(ramp_, 0.85, inside & on & inner & stroke)


def stripes(p, f, ramp_, coord, period, width, seed, tone=0.4, mask=None):
    """Bands (war paint, tiger stripes) across a coordinate, ragged at their edges."""
    k = np.floor(coord / period)
    c = (k + 0.5) * period + (hash01(seed, k.astype(np.int64)) - 0.5) * period * 0.3
    w = width * (0.7 + 0.6 * hash01(seed + 1, k.astype(np.int64)))
    on = np.abs(coord - c) < w / 2
    if mask is not None:
        on &= mask
    p.over(ramp_, tone, on)


# ----------------------------------------------------------------------------- affinity marks

def marks(p, f, s):
    """Each affinity's own marks over the body: they are layers with ramps only that affinity's palette has. They are
    accents, a tenth or so of the body: the creature stays itself, steeped in its element."""
    X, Y, Z = f.X, f.Y, f.Z
    amt = s.get("marks", 1.0)
    if amt <= 0:
        return
    sd = s.get("seed", 1) * 7 + 300
    g = s.get("ground", 0.0)
    low = sstep(g + s.get("lowband", 9.0), g, Y)          # the lower body (legs, feet, a hem)
    # frost: a crust of rime on everything that faces up, frost creeping over the top edges
    n = fbm3(X * 0.7, Y * 0.7, Z * 0.7, sd, 3)
    if f.face == "up":
        p.over("mk_frost", 0.45 + (n - 0.5) * 0.9, (n > 0.46 - 0.12 * amt))
    elif f.wall:
        creep = sstep(f.top - 0.7 - 1.3 * jag(across(f) * 1.2, sd + 1, 0.9, 1.0), f.top, Y)
        p.over("mk_frost", 0.5 + (n - 0.5) * 0.8, (creep > 0.5) & (n > 0.44))
        p.over("mk_frost", 0.78, (fbm3(X * 1.3, Y * 1.3, Z * 1.3, sd + 2, 2) > 0.78) & (n > 0.55))
    # flame: a few charred patches, split by thin glowing cracks
    c = fbm3(X * 0.3 + 9, Y * 0.3, Z * 0.3, sd + 3, 3)
    ridge = np.abs(fbm3(X * 0.6 + 2, Y * 0.6, Z * 0.6, sd + 4, 3) - 0.5)
    charred = c > 0.64 - 0.05 * amt - 0.08 * low
    p.over("mk_char", 0.32 + (c - 0.62) * 1.5, charred)
    p.glow("mk_flame", 0.5 + 0.45 * sstep(0.02, 0.0, ridge), charred & (ridge < 0.02))
    # venom: a few dark blotches with glowing pustules, streaks running down the walls
    v = fbm3(X * 0.45 + 4, Y * 0.45, Z * 0.45, sd + 5, 3)
    blot = v > 0.66 - 0.04 * amt
    p.over("mk_venom", 0.18 + (v - 0.66) * 2.0, blot)
    pust = blot & (vnoise3(X * 2.2, Y * 2.2, Z * 2.2, sd + 6) > 0.84)
    p.glow("mk_venom", 0.75, pust)
    if f.wall:
        a = across(f)
        col = np.floor(a / 0.8)
        drip_len = 1.0 + 2.4 * hash01(sd + 7, col.astype(np.int64))
        start = f.top - 0.3 - 1.6 * hash01(sd + 8, col.astype(np.int64))
        drip = (hash01(sd + 9, col.astype(np.int64)) > 0.82) & (Y < start) & (Y > start - drip_len) & (np.abs(a / 0.8 - col - 0.5) < 0.2)
        p.over("mk_venom", 0.3, drip)
    # storm: a few branching veins of light
    b1 = np.abs(fbm3(X * 0.42, Y * 0.42 + 5, Z * 0.42, sd + 10, 3) - 0.5)
    b2 = np.abs(fbm3(X * 0.9 + 3, Y * 0.9, Z * 0.9, sd + 11, 2) - 0.5)
    bolt = (b1 < 0.012) | ((b2 < 0.009) & (b1 < 0.06))
    p.glow("mk_storm", 0.72, bolt & (fbm3(X * 0.2, Y * 0.2, Z * 0.2, sd + 12, 2) > 0.52))
    # shade: smoke rising over the lower body, wisps of light at its edge
    sm = fbm3(X * 0.35, Y * 0.5 + 3, Z * 0.35, sd + 13, 3) + 0.34 * low
    p.over("mk_shade", 0.34 + (sm - 0.72), sm > 0.72)
    p.glow("mk_wisp", 0.5, (np.abs(sm - 0.72) < 0.008) & (vnoise3(X, Y, Z, sd + 17) > 0.45))
    # blood: dark veins, a few glowing; old stains on the lower parts
    bv = np.abs(fbm3(X * 0.5 + 6, Y * 0.5, Z * 0.5, sd + 14, 3) - 0.5)
    p.over("mk_blood", 0.3, (bv < 0.016) & (fbm3(X * 0.25, Y * 0.25, Z * 0.25, sd + 18, 2) > 0.45))
    p.glow("mk_vein", 0.55, (bv < 0.007) & (fbm3(X * 0.3, Y * 0.3, Z * 0.3, sd + 15, 2) > 0.58))
    if f.wall:
        stain = fbm3(X * 0.4, Y * 0.25, Z * 0.4, sd + 16, 3) + 0.3 * low
        p.over("mk_blood", 0.22 + (stain - 0.78), stain > 0.78)


# ----------------------------------------------------------------------------- the painter

def painter(features=None, lighting=None, ground=0.0):
    """A painter for solid_skins.paint: every cube paints its material (cube['_paint']), then the creature's own
    features(f, p, spec), the affinity marks and the shared light."""

    def paint(f, p):
        s = dict(f.cube.get("_paint") or {"m": "skin"})
        s.setdefault("ground", ground)
        m = s.get("m", "skin")
        MATS[m](f, p, s)
        if m not in NO_MARKS and s.get("marks", 1.0) > 0:
            marks(p, f, s)
        if features:
            features(f, p, s)
        if m not in SELF_LIT and s.get("light", 1.0) > 0:
            light_pass(p, f, s, s.get("light", 1.0))
        if lighting:
            lighting(f, p, s)
    return paint


LEVELS = {"": 11, "glow": 0, "crystal": 9, "hot": 0, "mk_flame": 0, "mk_storm": 0, "mk_wisp": 0, "mk_vein": 0}


def register(name, palette_fn, features=None, density=5.0, levels=None, ground=0.0):
    """Registers a creature with solid_skins, so gen_enemy_assets.write_geo paints it on the model."""
    S.PAINTERS[name] = dict(painter=painter(features, ground=ground), palette=lambda base: palette_fn(base["aff"]), density=density,
                            levels=levels or LEVELS)
