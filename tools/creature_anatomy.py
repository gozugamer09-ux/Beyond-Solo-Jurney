#!/usr/bin/env python3
"""
Anatomy painted on a creature's cubes (used by gen_creatures.py): chests and bellies, ribs, backs, shoulders, arms
and legs with their muscles, kneecaps and knuckles, and a face's folds. A box only becomes a body when its light says
what is under the skin: a pectoral is lit along its top and throws a shadow on the belly under it, a kneecap sits on
a crease, a ribcage is ridges and grooves.

Everything here moves tones (Paint.shade) and leaves the colours to the material, so the same muscles work on green
hide, grey dead flesh or blue scales, in every affinity. Shapes are placed on a face in its own frame: a from -1 to 1
across it (x on the front and back, z on the sides with the front at -1) and h from 0 at the cube's bottom to 1 at
its top, so they follow the cube whatever its size.

A cube asks for its anatomy in its paint spec:   anat=dict(part="chest", kind="muscle")
  chest   kind muscle | belly | ribs | lean      (an upright torso: front, back and sides)
  upper   an upper arm  (deltoid cap, biceps, triceps)
  fore    a forearm     (a swelling under the elbow, tendons, the wrist)
  thigh   a thigh       (the quadriceps' teardrop, the hamstring's groove)
  shin    a shin        (kneecap, the shin bone's ridge, the calf)
  fist    a hand        (knuckles and the lines between the fingers)
  neck    a thick neck  (the cords from ear to collarbone)
k scales all of it (0.6 for a subtle one, 1.4 for a creature that is all muscle).
"""
import math

import numpy as np

from solid_skins import fbm3, seg_dist, sstep


def frame(f):
    """(a, h, half, height): a across the face from -1 to 1, h up the cube from 0 to 1, and the face's half width and
    the cube's height in units."""
    height = max(f.top - f.bottom, 1e-6)
    if f.face in ("north", "south", "up", "down"):
        half = max(f.s[0] / 2, 1e-6)
        a = (f.X - f.cx) / half
    else:
        half = max(f.s[2] / 2, 1e-6)
        a = (f.Z - f.cz) / half
    if f.face in ("up", "down"):
        h = (f.Z - (f.o[2])) / max(f.s[2], 1e-6)
    else:
        h = (f.Y - f.bottom) / height
    return a, h, half, height


def inward(f):
    """+1 when the cube's -x side faces the body's middle line (a part on the +x side), else -1."""
    return 1.0 if f.cx > 0 else -1.0


def lobe(p, f, ca, ch, ra, rh, k=0.10, rim=0.14, rim_w=1.6, top=0.45):
    """A rounded mass (a muscle, a kneecap, a belly): lit, most of all over its upper half, with a shadow along its
    lower rim where it overhangs what is under it."""
    a, h, half, height = frame(f)
    da, dh = (a - ca) / ra, (h - ch) / rh
    d = np.sqrt(da * da + dh * dh)
    inside = d < 1.0
    lift = np.clip(dh, -1, 1)
    p.shade(k * np.sqrt(np.clip(1 - d * d, 0, 1)) * (0.7 + top * lift), inside)
    if rim:
        w = rim_w * f.texel / max(min(ra * half, rh * height), 1e-6)
        band = (d >= 1.0) & (d < 1.0 + w) & (dh < -0.25)
        p.shade(-rim * (1 - (d - 1.0) / w) * np.clip(-dh * 1.4, 0, 1), band)
    return inside


def groove(p, f, pts, w=1.0, k=0.14, lip=0.06):
    """A crease (between two muscles, under a fold): dark along a line through pts [(a, h)], with a lit lip just under
    it where the lower mass's top edge catches the light."""
    a, h, half, height = frame(f)
    A, H = a * half, h * height
    tex = f.texel
    dmin = np.full(f.X.shape, 99.0)
    dlip = np.full(f.X.shape, 99.0)
    for (a0, h0), (a1, h1) in zip(pts, pts[1:]):
        d, _ = seg_dist(A, H, a0 * half, h0 * height, a1 * half, h1 * height)
        dmin = np.minimum(dmin, d)
        d2, _ = seg_dist(A, H + tex * 1.6 * w, a0 * half, h0 * height, a1 * half, h1 * height)
        dlip = np.minimum(dlip, d2)
    line = dmin < tex * 0.75 * w
    p.shade(-k * (1 - 0.4 * dmin / (tex * 0.75 * w)), line)
    if lip:
        p.shade(lip, (dlip < tex * 0.7 * w) & ~line)
    return line


def groove_u(p, f, pts, w=1.0, k=0.14, lip=0.06):
    """groove() with its points in model units: (x, y) on the front and back, (z, y) on the sides, (x, z) on top."""
    if f.face in ("north", "south"):
        A, H = f.X, f.Y
    elif f.face in ("east", "west"):
        A, H = f.Z, f.Y
    else:
        A, H = f.X, f.Z
    tex = f.texel
    dmin = np.full(f.X.shape, 99.0)
    dlip = np.full(f.X.shape, 99.0)
    for (a0, h0), (a1, h1) in zip(pts, pts[1:]):
        d, _ = seg_dist(A, H, a0, h0, a1, h1)
        dmin = np.minimum(dmin, d)
        d2, _ = seg_dist(A, H + tex * 1.6 * w, a0, h0, a1, h1)
        dlip = np.minimum(dlip, d2)
    line = dmin < tex * 0.75 * w
    p.shade(-k * (1 - 0.4 * dmin / (tex * 0.75 * w)), line)
    if lip:
        p.shade(lip, (dlip < tex * 0.7 * w) & ~line)
    return line


def lobe_u(p, f, cx, cy, rx, ry, k=0.1, rim=0.12, top=0.45):
    """lobe() with its middle and radii in model units on a front face."""
    da, dh = (f.X - cx) / rx, (f.Y - cy) / ry
    d = np.sqrt(da * da + dh * dh)
    inside = d < 1.0
    p.shade(k * np.sqrt(np.clip(1 - d * d, 0, 1)) * (0.7 + top * np.clip(dh, -1, 1)), inside)
    if rim:
        w = 1.6 * f.texel / max(min(rx, ry), 1e-6)
        band = (d >= 1.0) & (d < 1.0 + w) & (dh < -0.25)
        p.shade(-rim * (1 - (d - 1.0) / w) * np.clip(-dh * 1.4, 0, 1), band)
    return inside


def face_folds(p, f, eyes=(), eye_r=(1.0, 0.6), nose=None, mouth=None, brow=None, k=1.0, face="north", frown=None, cheeks=True):
    """The folds of a face, in model units on its front: bags under the eyes and lines at their outer corners, the
    folds from the nose's wings down to the corners of the mouth, lines across the brow, the cheekbones' light."""
    if f.face != face:
        return
    rx, ry = eye_r
    if cheeks:
        for (x, y) in eyes:
            lobe_u(p, f, x * 1.12, y - ry * 2.5, rx * 1.0, ry * 1.05, k=0.06 * k, rim=0.05 * k)
    for (x, y) in eyes:
        s = 1 if x > 0 else -1
        # a bag: a crease curving under the eye, lit below it
        pts = [(x - rx * 0.9, y - ry * 1.15), (x - rx * 0.3, y - ry * 1.55), (x + rx * 0.4, y - ry * 1.5), (x + rx * 1.0, y - ry * 1.05)]
        groove_u(p, f, pts, w=0.9, k=0.12 * k, lip=0.07 * k)
        # crow's feet
        for i, dy in enumerate((-0.35, 0.0, 0.35)):
            x0 = x + s * rx * 1.25
            groove_u(p, f, [(x0, y + dy * ry), (x0 + s * rx * 0.55, y + dy * ry * 1.8 - 0.1 * ry)], w=0.6, k=0.08 * k, lip=0)
    if nose and mouth:
        nx, ny = nose
        x0, x1, my = mouth
        for s, xm in ((-1, x0), (1, x1)):
            pts = [(nx + s * 0.75, ny + 0.1), (nx + s * 1.25, ny - 0.5), (xm - s * 0.1, my + 0.25)]
            groove_u(p, f, pts, w=1.0, k=0.13 * k, lip=0.06 * k)
    if frown:
        # the lines of a scowl between the brows
        x, y0, y1 = frown
        for s in (-1, 1):
            groove_u(p, f, [(s * x, y0), (s * x * 1.3, y1)], w=0.9, k=0.14 * k, lip=0.05 * k)
    if brow:
        x0, x1, y0, n = brow
        for i in range(n):
            yy = y0 + i * 0.42
            pts = [(x0 + (x1 - x0) * t, yy + 0.12 * math.sin(t * math.pi * 2 + i)) for t in np.linspace(0, 1, 7)]
            groove_u(p, f, pts, w=0.7, k=0.08 * k, lip=0.05 * k)


def hollow(p, f, ca, ch, ra, rh, k=0.08):
    """A soft dip (a navel, a sunken belly, the hollow of a cheek): darker in the middle, its lower edge lit."""
    a, h, _, _ = frame(f)
    da, dh = (a - ca) / ra, (h - ch) / rh
    d = np.sqrt(da * da + dh * dh)
    inside = d < 1.0
    p.shade(-k * np.clip(1 - d * d, 0, 1) * (1.0 + 0.5 * np.clip(dh, 0, 1)), inside)
    p.shade(k * 0.5, inside & (dh < -0.55))
    return inside


# --------------------------------------------------------------------------------------------- the parts

def chest(p, f, kind="muscle", k=1.0):
    if not f.wall:
        return
    if f.face == "north":
        if kind == "ribs":
            _ribs_front(p, f, k)
            return
        heavy = kind == "belly"
        lean = kind == "lean"
        # pectorals: two slabs under the collarbones, the sternum between them
        pr = (0.44, 0.15 if not heavy else 0.17)
        for s in (-1, 1):
            lobe(p, f, s * 0.46, 0.80 if not heavy else 0.79, *pr, k=0.10 * k, rim=(0.20 if heavy else 0.16) * k)
        groove(p, f, [(0, 0.64 if not heavy else 0.66), (0, 0.97)], k=0.12 * k, lip=0)
        # collarbones
        for s in (-1, 1):
            groove(p, f, [(s * 0.1, 0.95), (s * 0.85, 0.99)], w=0.8, k=0.08 * k, lip=0.05 * k)
        if heavy:
            # a gut: one great rounded mass that hangs over the belt, the navel deep in it
            lobe(p, f, 0, 0.34, 0.74, 0.33, k=0.12 * k, rim=0.22 * k, rim_w=2.2)
            hollow(p, f, 0, 0.30, 0.06, 0.04, k=0.3 * k)
            groove(p, f, [(-0.5, 0.60), (-0.2, 0.575), (0.2, 0.575), (0.5, 0.60)], w=0.9, k=0.07 * k, lip=0.04 * k)
        else:
            # the six blocks of the stomach, the line down its middle, the navel, the obliques' V
            for row in range(3):
                ch = 0.565 - row * 0.125
                for s in (-1, 1):
                    lobe(p, f, s * 0.17, ch, 0.16, 0.058, k=(0.08 if not lean else 0.05) * k, rim=0.12 * k, rim_w=1.2)
            groove(p, f, [(0, 0.17), (0, 0.63)], w=0.9, k=0.12 * k, lip=0)
            hollow(p, f, 0, 0.16, 0.05, 0.035, k=0.3 * k)
            for s in (-1, 1):
                groove(p, f, [(s * 0.66, 0.46), (s * 0.48, 0.2), (s * 0.24, 0.02)], w=1.0, k=0.11 * k, lip=0.04 * k)
                # serratus: short slanting fingers on the ribs at the sides
                for i in range(3):
                    hh = 0.66 - 0.065 * i
                    groove(p, f, [(s * 0.6, hh - 0.025), (s * 0.84, hh + 0.02)], w=0.7, k=0.09 * k, lip=0.04 * k)
    elif f.face == "south":
        # the back: the spine in its groove, the shoulder blades, the long muscles beside the spine
        groove(p, f, [(0, 0.06), (0, 0.97)], w=1.2, k=0.14 * k, lip=0)
        for s in (-1, 1):
            lobe(p, f, s * 0.44, 0.76, 0.30, 0.15, k=0.09 * k, rim=0.14 * k)
            lobe(p, f, s * 0.15, 0.32, 0.13, 0.30, k=0.07 * k, rim=0.06 * k)
            groove(p, f, [(s * 0.95, 0.74), (s * 0.62, 0.42), (s * 0.34, 0.12)], w=0.9, k=0.08 * k, lip=0.04 * k)
        if kind == "ribs":
            for i in range(4):
                hh = 0.62 - 0.1 * i
                for s in (-1, 1):
                    groove(p, f, [(s * 0.18, hh), (s * 0.55, hh - 0.04), (s * 0.95, hh - 0.11)], w=0.9, k=0.1 * k, lip=0.05 * k)
    else:
        if kind == "ribs":
            for i in range(5):
                hh = 0.86 - 0.09 * i
                groove(p, f, [(-1, hh - 0.08), (-0.3, hh), (0.6, hh + 0.02), (1, hh)], w=0.9, k=0.13 * k, lip=0.07 * k)
            return
        # the flank: the edge of the back's broad muscle, the ribs' fingers under the arm
        groove(p, f, [(0.35, 0.92), (0.15, 0.55), (0.25, 0.18)], w=1.0, k=0.09 * k, lip=0.04 * k)
        lobe(p, f, 0.62, 0.62, 0.32, 0.28, k=0.06 * k, rim=0.06 * k)
        if kind != "belly":
            for i in range(3):
                hh = 0.72 - 0.08 * i
                groove(p, f, [(-0.75, hh - 0.03), (-0.35, hh + 0.02)], w=0.7, k=0.08 * k, lip=0.04 * k)
        else:
            lobe(p, f, -0.35, 0.32, 0.6, 0.3, k=0.08 * k, rim=0.16 * k)


def _ribs_front(p, f, k):
    """A starved chest: ribs arching down and out from the breastbone, the hollow under the ribcage."""
    for i in range(5):
        h0 = 0.88 - 0.085 * i
        for s in (-1, 1):
            pts = [(s * (0.1 + 0.85 * t), h0 - 0.16 * t ** 1.5 - 0.02 * i * t) for t in np.linspace(0, 1, 6)]
            groove(p, f, pts, w=1.0, k=0.14 * k, lip=0.08 * k)
    lobe(p, f, 0, 0.74, 0.08, 0.2, k=0.08 * k, rim=0)
    hollow(p, f, 0, 0.3, 0.55, 0.13, k=0.1 * k)
    hollow(p, f, 0, 0.18, 0.05, 0.035, k=0.3 * k)


def upper(p, f, k=1.0):
    """An upper arm hanging from its shoulder: the deltoid's cap, the biceps in front, the triceps behind."""
    if not f.wall:
        if f.face == "up":
            p.shade(0.05 * k)
        return
    outer = f.outer or (f.face in ("east", "west") and not f.inner)
    groove(p, f, [(-1, 0.76), (-0.3, 0.66 if outer else 0.72), (0, 0.6 if outer else 0.7), (0.3, 0.66 if outer else 0.72), (1, 0.76)],
           w=1.0, k=0.11 * k, lip=0.05 * k)
    lobe(p, f, 0, 0.9, 0.95, 0.14, k=0.07 * k, rim=0)
    if f.face == "north":
        lobe(p, f, 0, 0.4, 0.62, 0.27, k=0.10 * k, rim=0.10 * k)
    elif f.face == "south":
        lobe(p, f, 0, 0.5, 0.62, 0.3, k=0.08 * k, rim=0.08 * k)
        groove(p, f, [(-0.4, 0.06), (0.4, 0.06)], w=0.7, k=0.08 * k, lip=0.04 * k)
    elif outer:
        groove(p, f, [(-0.05, 0.58), (0.0, 0.1)], w=0.8, k=0.07 * k, lip=0)


def fore(p, f, k=1.0, wrist=True):
    """A forearm: thick under the elbow, narrowing to the wrist, the tendons showing near it."""
    if not f.wall:
        return
    lobe(p, f, 0.1 if f.face == "north" else 0.0, 0.74, 0.8, 0.24, k=0.09 * k, rim=0.07 * k)
    if f.face == "north":
        for s in (-1, 1):
            groove(p, f, [(s * 0.28, 0.08), (s * 0.22, 0.42)], w=0.6, k=-0.06 * k, lip=0)
    if wrist:
        groove(p, f, [(-1, 0.07), (1, 0.07)], w=0.8, k=0.08 * k, lip=0.04 * k)


def thigh(p, f, k=1.0):
    if not f.wall:
        return
    if f.face == "north":
        lobe(p, f, 0, 0.56, 0.62, 0.36, k=0.09 * k, rim=0.05 * k)
        ins = -inward(f)
        lobe(p, f, ins * 0.42, 0.2, 0.3, 0.14, k=0.08 * k, rim=0.12 * k)
        groove(p, f, [(ins * 0.1, 0.95), (ins * 0.45, 0.4)], w=0.8, k=0.06 * k, lip=0.03 * k)
    elif f.face == "south":
        groove(p, f, [(0, 0.15), (0, 0.85)], w=0.9, k=0.08 * k, lip=0)
        lobe(p, f, 0, 0.9, 0.9, 0.12, k=0.06 * k, rim=0.1 * k)
    elif f.outer:
        groove(p, f, [(0, 0.95), (0.05, 0.12)], w=0.8, k=0.06 * k, lip=0)


def shin(p, f, k=1.0):
    if not f.wall:
        return
    if f.face == "north":
        lobe(p, f, 0, 0.88, 0.42, 0.1, k=0.12 * k, rim=0.16 * k, rim_w=1.4)
        ins = -inward(f)
        groove(p, f, [(ins * 0.12, 0.12), (ins * 0.08, 0.74)], w=0.6, k=-0.07 * k, lip=0)
    elif f.face == "south":
        lobe(p, f, 0, 0.66, 0.68, 0.27, k=0.10 * k, rim=0.13 * k)
    groove(p, f, [(-1, 0.05), (1, 0.05)], w=0.7, k=0.06 * k, lip=0.03 * k)


def fist(p, f, k=1.0, fingers=4):
    """A hand: a row of knuckles across its front, the fingers' lines under them, a crease across the palm."""
    if f.face == "north":
        n = fingers
        for i in range(n):
            ca = -0.75 + 1.5 * i / max(n - 1, 1)
            lobe(p, f, ca, 0.42, 0.75 / n + 0.06, 0.13, k=0.12 * k, rim=0.12 * k, rim_w=1.0)
        for i in range(n - 1):
            ca = -0.75 + 1.5 * (i + 0.5) / max(n - 1, 1)
            groove(p, f, [(ca, 0.0), (ca, 0.3)], w=0.7, k=0.14 * k, lip=0)
    elif f.face == "down":
        n = fingers
        for i in range(n - 1):
            ca = -0.75 + 1.5 * (i + 0.5) / max(n - 1, 1)
            groove(p, f, [(ca, 0.0), (ca, 0.55)], w=0.7, k=0.12 * k, lip=0)
    elif f.wall:
        groove(p, f, [(-1, 0.35), (1, 0.32)], w=0.7, k=0.06 * k, lip=0.03 * k)


def neck(p, f, k=1.0):
    if f.face == "north":
        for s in (-1, 1):
            groove(p, f, [(s * 0.8, 0.95), (s * 0.18, 0.08)], w=0.9, k=0.08 * k, lip=0.05 * k)
        lobe(p, f, 0, 0.45, 0.16, 0.12, k=0.06 * k, rim=0.08 * k)
    elif f.face == "south":
        groove(p, f, [(0, 0.05), (0, 0.95)], w=1.0, k=0.08 * k, lip=0)


def slab(p, f, k=1.0):
    """A muscle that is its own cube (a pectoral, a deltoid's cap, a calf): rounded and lit over its upper part."""
    if f.wall:
        lobe(p, f, 0, 0.56, 0.98, 0.62, k=0.12 * k, rim=0, top=0.6)
        p.shade(-0.08 * k, (frame(f)[1] < 0.18))
    elif f.face == "up":
        p.shade(0.04 * k)


PARTS = {"slab": slab, "chest": chest, "upper": upper, "fore": fore, "thigh": thigh, "shin": shin, "fist": fist, "neck": neck}


def anatomy(p, f, spec):
    """What a cube's paint spec asks for under anat=dict(part=..., kind=..., k=...)."""
    spec = dict(spec)
    part = spec.pop("part")
    PARTS[part](p, f, **spec)


# --------------------------------------------------------------------------------------------- skin stories

def grime(p, f, ramp, height, ground=0.0, tone=0.3, seed=5, weight=0.85):
    """Dirt worked up from the ground: feet and shins stained, its upper edge ragged, with splashes above it."""
    if f.face == "up" and f.Y.mean() > ground + height * 1.2:
        return
    n = fbm3(f.X * 0.6, f.Y * 0.25, f.Z * 0.6, seed, 3)
    top = ground + height * (0.75 + 0.5 * n)
    w = weight * sstep(top, top - 0.8, f.Y)
    splash = fbm3(f.X * 1.1 + 3, f.Y * 1.1, f.Z * 1.1, seed + 3, 2)
    w = np.maximum(w, weight * 0.8 * ((splash > 0.68) & (f.Y < top + height * 0.5)))
    p.over(ramp, tone + (n - 0.5) * 0.3, w)


def flush(p, f, ramp, where, tone=0.5, weight=0.55):
    """Blood close under the skin (ear tips, a nose, knuckles): a soft wash of a warmer ramp. where is a weight 0..1
    per texel."""
    w = np.clip(where, 0, 1) * weight
    if np.any(w > 0.02):
        p.over(ramp, tone, w)


def bands(p, f, ramp, coord, period, width, tone=0.3, wobble=0.25, seed=3, mask=None, soft=0.0, broken=0.0):
    """Rings or stripes of a second colour across a body or a limb, their edges wavering a little (a tiger's stripes,
    a wasp's bands, a snake's rings): coord runs across the bands. broken thins them in places until they break,
    as a big cat's stripes do."""
    wob = (fbm3(f.X * 0.5, f.Y * 0.5, f.Z * 0.5, seed, 2) - 0.5) * wobble * period
    k = ((coord + wob) / period) % 1.0
    if broken:
        width = width * np.clip(1 - broken + broken * 2.2 * (fbm3(f.X * 0.45 + 7, f.Y * 0.45, f.Z * 0.45, seed + 1, 2) - 0.25), 0, 1.4)
        k = np.abs(k - 0.5) * 2
        k = 1 - k
        k = np.where(k < width, k, 1.0)
    on = k < width
    if mask is not None:
        on = on & mask
    if soft:
        edge = np.minimum(k, width - k) / max(soft, 1e-6)
        p.over(ramp, tone, np.where(on, np.clip(edge, 0.4, 1.0), 0.0))
    else:
        p.over(ramp, tone, on)
    return on


def stripe_marks(p, f, ramp, seed, count, length, width, axis="y", tone=0.22, mask=None, taper=True):
    """Tapering brush strokes (a tiger's or a hyena's markings) running across a face: count strokes per face, each
    a crescent thinning to both ends."""
    a, h, half, height = frame(f)
    rng = np.random.default_rng(seed)
    out = np.zeros(f.X.shape, bool)
    A, H = a * half, h * height
    for i in range(count):
        if axis == "y":
            c = -half + (i + 0.5 + rng.uniform(-0.25, 0.25)) * (2 * half / count)
            y0 = rng.uniform(0.0, 0.25) * height
            y1 = y0 + length * height * rng.uniform(0.7, 1.0)
            bend = rng.uniform(-0.3, 0.3) * half / count
            t = np.clip((H - y0) / max(y1 - y0, 1e-6), 0, 1)
            centre = c + bend * np.sin(t * math.pi)
            wid = width * (np.sin(t * math.pi) if taper else 1.0)
            on = (H >= y0) & (H <= y1) & (np.abs(A - centre) < wid)
        else:
            c = (i + 0.5 + rng.uniform(-0.25, 0.25)) * (height / count)
            x0 = rng.uniform(-1.0, -0.6) * half
            x1 = x0 + length * 2 * half * rng.uniform(0.6, 1.0)
            bend = rng.uniform(-0.3, 0.3) * height / count
            t = np.clip((A - x0) / max(x1 - x0, 1e-6), 0, 1)
            centre = c + bend * np.sin(t * math.pi)
            wid = width * (np.sin(t * math.pi) if taper else 1.0)
            on = (A >= x0) & (A <= x1) & (np.abs(H - centre) < wid)
        out |= on
    if mask is not None:
        out &= mask
    p.over(ramp, tone, out)
    return out


def wraps(p, f, ramp, y0, y1, period=0.75, slant=0.22, tone=0.55, dirt=None, seed=7):
    """Strips of cloth wound round a limb from y0 to y1 (a wrapped forearm, a bandaged shin): every turn lit along
    its top and shaded where the next turn laps over it, the turns slanting round the limb, grubby towards one end."""
    if not f.wall:
        return
    a = f.Z if f.side else f.X
    inside = (f.Y > y0) & (f.Y < y1)
    if not inside.any():
        return
    coord = f.Y + slant * a + (fbm3(f.X * 0.8, f.Y * 0.8, f.Z * 0.8, seed, 2) - 0.5) * 0.25
    k = (coord / period) % 1.0                                   # 0 at the bottom of a turn .. 1 at its top
    t = tone - 0.16 + 0.3 * k
    t = t + (fbm3(f.X * 2.5, f.Y * 6.0, f.Z * 2.5, seed + 1, 2) - 0.5) * 0.12   # the weave and the grime in it
    p.over(ramp, t, inside)
    p.over(ramp, tone - 0.38, inside & (k < 0.1))               # the shadow under the turn above
    # the ends of the wrapping are ragged
    edge = (np.abs(f.Y - y0) < f.texel * 1.2) | (np.abs(f.Y - y1) < f.texel * 1.2)
    p.over(ramp, tone - 0.28, inside & edge)
    if dirt:
        r2, toward = dirt
        w = sstep(y1 if toward == "top" else y0, (y0 + y1) / 2, f.Y) * 0.7
        p.over(r2, 0.36, inside & (fbm3(f.X * 1.2, f.Y * 1.2, f.Z * 1.2, seed + 2, 2) < 0.35 + 0.4 * w))


def ear(p, f, ramp="flush", face="north", weight=0.6, k=1.0):
    """The hollow of an ear on its front: a rim of skin round a cupped, warmer inside that is shaded where the rim
    overhangs it, with a ridge of cartilage curling through it."""
    if f.face != face:
        return
    a, h, half, height = frame(f)
    d = np.sqrt((a / 0.72) ** 2 + ((h - 0.5) / 0.34) ** 2)
    inside = d < 1.0
    p.over(ramp, 0.42 + 0.18 * (1 - d), weight * sstep(1.0, 0.75, d))
    p.shade(-0.1 * k, inside & (h > 0.55))
    p.shade(0.06 * k, inside & (h < 0.32))
    groove(p, f, [(-0.55, 0.62), (-0.1, 0.48), (0.45, 0.42)], w=0.8, k=0.1 * k, lip=0.06 * k)


def nostrils(p, f, x, z, r=(0.28, 0.2), face="down"):
    """Two dark nostrils on the underside of a nose, flared a little."""
    if f.face != face:
        return
    for s in (-1, 1):
        d = ((f.X - s * x) / r[0]) ** 2 + ((f.Z - z) / r[1]) ** 2
        p.shade(-0.4, d < 1.0)
        p.shade(-0.12, (d >= 1.0) & (d < 1.8))


def claw_marks(p, f, ramp, start, end, n=3, gap=0.9, width=0.55, tone=0.42, seed=5, face="north"):
    """Paint dragged across a body by three or four fingers: parallel strokes from start to end (model units on the
    face), each thickest in its middle and dry and broken at its tail."""
    if f.face != face:
        return
    A, H = (f.X, f.Y) if face in ("north", "south") else (f.Z, f.Y)
    (x0, y0), (x1, y1) = start, end
    dx, dy = x1 - x0, y1 - y0
    ln = math.hypot(dx, dy)
    nx, ny = -dy / ln, dx / ln
    rough = fbm3(f.X * 1.6, f.Y * 1.6, f.Z * 1.6, seed, 2)
    for i in range(n):
        o = (i - (n - 1) / 2) * gap
        d, t = seg_dist(A, H, x0 + nx * o, y0 + ny * o, x1 + nx * o, y1 + ny * o)
        w = width * (0.35 + 0.9 * np.sin(np.clip(t, 0, 1) * math.pi) ** 0.6) * (1 - 0.35 * t)
        dry = t > 0.55 + 0.4 * rough
        on = (d < w * (0.75 + 0.5 * rough)) & ~(dry & (rough > 0.5))
        p.over(ramp, tone + (rough - 0.5) * 0.2, on)


def rings(p, f, ramp, ys, tone=0.42, jag_=0.25, seed=9):
    """Painted rings round a limb or a body between the heights in ys [(y0, y1)], their edges ragged."""
    if not f.wall:
        return
    a = f.Z if f.side else f.X
    n = (fbm3(a * 1.3, f.Y * 0.4, f.X * 0.3 + f.Z * 0.3, seed, 2) - 0.5) * 2 * jag_
    for y0, y1 in ys:
        p.over(ramp, tone + n * 0.2, (f.Y > y0 + n) & (f.Y < y1 - n))


def zigzag(p, f, ramp, y, amp=0.45, period=1.2, width=0.32, tone=0.6, faces=("north", "south", "east", "west")):
    """A painted zigzag band running round a body at height y (a shaman's robe, a tribal shield)."""
    if f.face not in faces:
        return
    a = f.Z if f.side else f.X
    k = (a / period) % 1.0
    tri = np.abs(k - 0.5) * 4 - 1                        # -1 .. 1 .. -1
    centre = y + amp * tri
    rough = fbm3(f.X * 2.0, f.Y * 2.0, f.Z * 2.0, 17, 2)
    p.over(ramp, tone + (rough - 0.5) * 0.25, np.abs(f.Y - centre) < width / 2 * (0.75 + 0.5 * rough))


def stitched(p, f, pts, step=0.55, tick=0.32, face=None, thread="black"):
    """A wound sewn shut: a dark seam along pts (model units on the face, as groove_u), puckered lighter at its lips,
    crossed by stitches of black thread."""
    if face and f.face != face:
        return
    if f.face in ("north", "south"):
        A, H = f.X, f.Y
    elif f.face in ("east", "west"):
        A, H = f.Z, f.Y
    else:
        A, H = f.X, f.Z
    tex = f.texel
    seam = np.full(f.X.shape, 99.0)
    stitch = np.zeros(f.X.shape, bool)
    for (a0, h0), (a1, h1) in zip(pts, pts[1:]):
        d, t = seg_dist(A, H, a0, h0, a1, h1)
        seam = np.minimum(seam, d)
        ln = math.hypot(a1 - a0, h1 - h0)
        n = max(int(ln / step), 1)
        dx, dy = (a1 - a0) / ln, (h1 - h0) / ln
        for i in range(n + 1):
            cx, cy = a0 + dx * ln * i / n, h0 + dy * ln * i / n
            # a stitch: a short bar across the seam
            px, py = A - cx, H - cy
            along = px * dx + py * dy
            acr = -px * dy + py * dx
            stitch |= (np.abs(along) < tex * 0.55) & (np.abs(acr) < tick)
    p.shade(0.08, (seam < tex * 2.2) & (seam >= tex * 0.7))
    p.shade(-0.3, seam < tex * 0.7)
    p.over(thread, 0.16, stitch)


def photophores(p, f, rows, r=0.32, faces=("west", "east")):
    """Lights in the skin set in rows, as a deep fish's are: rows [(y, a0, a1, n)] along a face (a is z on the
    sides, x on the front), each light a glowing oval in a dark ring."""
    if f.face not in faces:
        return
    A = f.Z if f.side else f.X
    for (y, a0, a1, n) in rows:
        for i in range(n):
            ca = a0 + (a1 - a0) * (i + 0.5) / n
            d = np.hypot((A - ca) / (r * 1.3), (f.Y - y) / r)
            p.over("black", 0.12, (d >= 1.0) & (d < 1.45))
            p.glow("glow", 0.55 + 0.4 * (1 - d), d < 1.0)
