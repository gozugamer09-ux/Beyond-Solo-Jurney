#!/usr/bin/env python3
"""
Worn gauntlets: the Better Combat "fist" weapons are worn round the forearm and fist like a piece of armor, not held
in the hand like a flat picture.

- Each design is a handful of boxes in the right arm's own model space (the game's axes: x towards the model's left,
  so the outside of the right arm is -x; y down the arm from the shoulder pivot, the hand ending at y = 10; z
  backwards, the front of the arm at z = -2). A cuff at the elbow, a vambrace, the plated hand, a knuckle guard over
  the end of the fist and whatever the design adds (claws, spikes, a gem).
- They are painted with the armory's armor kit (tools/armor_kit.py, tools/armor_worn.py) at the armor's resolution,
  two texels per model pixel: ramps with hue-shifted shading, edge light, contact shadows, fur, leather, crystal.
- Every box clears the armory's chestplate arms (the arm box inflated by 1.02), so the gauntlets show over armor.
- The boxes become an item model, models/item/worn/<name>.json, textured by textures/item/worn/<name>.png. Its
  third-person transform undoes what vanilla's ItemInHandLayer does to a held item (move it to the front of the fist
  and stand it up), so the boxes land on the arm exactly where they were drawn and follow it through every animation:
  Better Combat's punches and guards, playerAnimator's elbow bends. The left hand gets the same values: the game
  mirrors left-hand transforms itself, and the designs are symmetric about the middle of the arm, so the left arm
  wears the mirror image.
- tools/gen_weapon_models.py gives a fist weapon's item model this model for the two third-person hands (the
  inventory icon and the dropped item stay the sprite). The client (client/combat/WornGauntlets.java) puts the
  gauntlet on the other arm too when that hand is empty, and draws the arm wearing it in first person.

Run from the repo root (tools/gen_weapon_models.py calls it for the fist weapons):
    python3 tools/gen_gauntlets.py [names...]            # write models and textures
    python3 tools/gen_gauntlets.py --preview [names...]  # also -> build/preview/gauntlet_<name>.png
Needs: numpy, pillow.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_kit as ak  # noqa: E402
import armor_rig as rig  # noqa: E402
import armor_worn as aw  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
TEX_DIR = os.path.join(ASSETS, "textures", "item", "worn")
MODEL_DIR = os.path.join(ASSETS, "models", "item", "worn")
OUT_DIR = os.path.join(ROOT, "build", "preview")

ARM_X = -1.0          # the middle of the right arm (its box runs from x -3 to 1, z -2 to 2)
ARMOR = 3.02          # half the width of a chestplate's arm box (inflated 1.02): every box stays outside it

# ------------------------------------------------------------------------------------------------- model space
#
# Item model pixels p (0..16) and arm pixels q:  q = A p + B.  A turns the model half a turn about z, so model +y runs
# up the arm (the elbow is "up" in the texture) and model +x is the outside of the right arm; B puts the middle of
# the arm on the model's middle (x 8, z 8) and the end of the fist at model y 4.

A = np.diag([-1.0, -1.0, 1.0])
B = np.array([ARM_X + 8.0, 14.0, -8.0])


def to_model(q):
    return A.T @ (np.asarray(q, float) - B)


def _rot_xyz(rx, ry, rz):
    """Euler angles in degrees, X then Y then Z as the item transform applies them (JOML rotationXYZ)."""
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


def _euler_xyz(R):
    """Degrees (rx, ry, rz) with _rot_xyz(rx, ry, rz) == R."""
    ry = math.asin(max(-1.0, min(1.0, R[0, 2])))
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(-R[1, 2], R[2, 2])
        rz = math.atan2(-R[0, 1], R[0, 0])
    else:
        rx = math.atan2(R[2, 1], R[1, 1])
        rz = 0.0
    return [round(math.degrees(a), 4) + 0.0 for a in (rx, ry, rz)]


# vanilla's ItemInHandLayer after the arm: stand the item up and move it to the front of the fist (pixels)
VAN_R = _rot_xyz(-90, 0, 0) @ _rot_xyz(0, 180, 0)
VAN_T = np.array([1.0, 2.0, -10.0])


def worn_display():
    """The third-person transform that puts model space on the arm: arm = VAN_R (VAN_T + D(p)), with
    D(p) = t + R (p - 8) (scale 1) the item transform; we want arm = A p + B."""
    R = VAN_R.T @ A
    t = VAN_R.T @ (A @ np.full(3, 8.0) + B) - VAN_T
    rot = _euler_xyz(R)
    assert np.allclose(_rot_xyz(*rot), R, atol=1e-6)
    tr = {"rotation": rot, "translation": [round(float(v), 4) + 0.0 for v in t], "scale": [1, 1, 1]}
    # The left hand gets the same values: the game negates the y/z rotation and x translation of a left-hand
    # transform by itself, which moves the model across onto the left arm. The first-person hands use the same
    # chain (client/combat/WornGauntlets.java draws the arm and hangs the gauntlet on it the way the layer does).
    out = {k: dict(tr) for k in ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
                                 "firstperson_lefthand")}
    # Dropped: a raised fist, the cuff on the ground. The item model reports no transforms of its own
    # (neoforge:separate_transforms), so the dropped-item renderer lifts it a quarter of a block: the translation
    # takes that back and stands the cuff's rim (model y GROUND_RIM, turned upside down about the middle) on the
    # ground.
    s = GROUND_SCALE
    out["ground"] = {"rotation": [180, 0, 0], "translation": [0, round((GROUND_RIM - 8.0) * s - 4.0 + 0.5, 3), 0],
                     "scale": [s, s, s]}
    return out


GROUND_SCALE = 0.8
GROUND_RIM = 11.5     # model y of the elbow end of the cuff (arm y 2.5)


# ------------------------------------------------------------------------------------------------- designs
#
# A design: its palette (armor_worn keys) and parts(g), the list of (name, painter, box, options) for a fit: g is how
# much wider than the snug fit every ring is (0 over a bare arm, FITS["_armored"] over a chestplate's arm).
# Boxes are given round the middle of the arm: ("ring", y0, y1, half) is a box `half` wide each way in x and z;
# ("ring", y0, y1, half, front, back) sets how far it reaches forward and backward separately. ("at", x, y, z, w, h,
# d) is a box by its corner (x from the middle of the arm). Sizes are multiples of a quarter pixel (whole texels).
# Options "rot": (axis, degrees) turns the element about its own middle in the item model (22.5 degree steps),
# "glow": True makes it full-bright.
#
# Both arms wear the same model, moved across rather than mirrored (a rotation cannot mirror), so everything is
# symmetric about the middle of the arm in x: the outer and inner faces are painted alike, straps go on the back.

K = 4                                  # texels per model pixel (the armor has two; held weapons have four)
FITS = {"": 0.0, "_armored": 0.75}     # model suffix: extra half width


def _claws(g, y, xs, front, sizes, painter, **opts):
    """Tapering segments hanging from the striking face: xs = their middles across the fist, front = the z of
    their middles, sizes = (width, length) of each segment from the base to the point."""
    out = []
    for i, cx in enumerate(xs):
        yy = y
        for k, (wd, ln) in enumerate(sizes):
            out.append((f"{painter}{i}_{k}", painter, ("at", cx - wd / 2, yy, front - wd / 2, wd, ln, wd),
                        dict(opts, seg=k, last=k == len(sizes) - 1)))
            yy += ln
    return out


def frostbear(g):
    spread = 2.0 + g * 0.6
    return [
        ("cuff", "fur_ruff", ("ring", 2.5, 4.5, 3.0 + g), {}),
        ("vambrace", "vambrace", ("ring", 4.25, 7.25, 2.5 + g), dict(key="metal", trim="trim", emblem="frost", side="snow")),
        ("hand", "hand_lames", ("ring", 7.0, 10.5, 2.75 + g), dict(key="metal2", trim="trim", lames=3)),
        ("knuckles", "knuckle_guard", ("ring", 9.75, 11.25, 3.0 + g, 3.25 + g, 2.5 + g), dict(key="metal", trim="trim", bumps=3)),
        ("crest", "gem", ("at", -0.75, 7.75, -(2.75 + g) - 0.5, 1.5, 1.5, 0.5), dict(key="crystal", rot=("z", 45), glow=True)),
    ] + _claws(g, 11.25, (-spread, 0.0, spread), -(3.25 + g) + 1.25, ((1.5, 1.5), (1.0, 1.5), (0.5, 1.0)), "ice_claw",
               key="crystal", glow=True)


def titan(g):
    step = 1.6 + g * 0.4
    return [
        ("cuff", "cuff_plate", ("ring", 2.5, 4.5, 3.0 + g), dict(key="metal", trim="trim")),
        ("vambrace", "vambrace", ("ring", 4.25, 7.25, 2.5 + g), dict(key="metal", trim="trim", emblem="shield", side="flutes",
                                                                    straps="leather")),
        ("hand", "hand_lames", ("ring", 7.0, 10.0, 2.75 + g), dict(key="metal2", trim="trim", lames=2)),
        ("knuckles", "knuckle_guard", ("ring", 9.5, 11.5, 3.25 + g, 3.5 + g, 2.5 + g), dict(key="trim", trim="metal", bumps=4)),
        ("stone", "gem", ("at", -0.75, 7.5, -(2.75 + g) - 0.5, 1.5, 1.5, 0.5), dict(key="gem", rot=("z", 45), glow=True)),
    ] + _claws(g, 11.5, (-1.5 * step, -0.5 * step, 0.5 * step, 1.5 * step), -(3.5 + g) + 1.25,
               ((1.5, 1.0), (1.0, 1.0), (0.5, 0.5)), "spike", key="trim")


DESIGNS = {
    # Epic. Fur and claws of a giant ice bear: a ruff of white fur at the elbow, ice-steel plates, three claws of ice.
    "frostbear_gauntlets": dict(
        pal={"metal": "c0d2e6", "metal2": "7d96b6", "trim": ("86cdfa", "metal"), "fur": "eef3f8", "leather": "4c5c70",
             "crystal": "aeeaff", "dark": "101826"},
        parts=frostbear),
    # Legendary. Made for a giant who held the line alone: heavy bronze, a gold knuckle-duster with four spikes, an
    # amber stone on the back of the hand.
    "titans_knuckles": dict(
        pal={"metal": ("b27a3c", "gold"), "metal2": ("8c5a2a", "gold"), "trim": ("f4c95c", "gold"), "leather": "5e3c22",
             "gem": "ffae36", "dark": "1c120a"},
        parts=titan),
}


class _texels:
    """armor_rig at this file's resolution while a gauntlet is built (the armor itself is drawn at two per pixel)."""

    def __enter__(self):
        self.old = (rig.TEX, rig.UVS, rig.K)
        rig.TEX, rig.UVS, rig.K = 64 * K, 64, K

    def __exit__(self, *exc):
        rig.TEX, rig.UVS, rig.K = self.old


def part_box(name, spec, armored):
    if spec[0] == "ring":
        _, y0, y1, half = spec[:4]
        front = spec[4] if len(spec) > 4 else half
        back = spec[5] if len(spec) > 5 else half
        assert y1 > y0 and (not armored or min(half, front, back) > ARMOR + 0.2), name
        origin = (ARM_X - half, y0, -front)
        size = (2 * half, y1 - y0, front + back)
    else:
        _, x, y, z, w, h, d = spec
        origin, size = (ARM_X + x, y, z), (w, h, d)
    for v in size:
        assert abs(v * K - round(v * K)) < 1e-9, (name, size)
    return rig.extra(name, "right_arm", origin, size)


# ------------------------------------------------------------------------------------------------- painters
#
# All in texels, on the box's strip (rows from the elbow end down to the fist end; faces: outer side, front, inner
# side, back) and its two end faces, through armor_worn's BP. Light comes from the top left of every face.

def _w(design, seed):
    """An armor_worn painter context for one design (its palette lookups and random numbers)."""
    return aw.Worn({"pal": design["pal"], "seed": seed})


def _plate_tone(bp, kind, amp=0.8, rim=True):
    """A plate wrapped round the arm: the turn of the box towards the light, and each face framed like a plate, lit
    along its top and leading edge and shaded along its bottom and trailing edge."""
    m = bp.S(True)
    t = aw.form(bp, m, kind, amp=amp, vert=0.0, spec=0.0)
    if rim:
        t = t + np.where(bp.y == 0, 1.0, 0) + np.where(bp.y == 1, 0.4, 0) + np.where(bp.y == bp.H - 1, -0.7, 0)
        t = t + np.where(bp.lx == 0, 0.6, 0) + np.where(bp.lx == bp.fw - 1, -0.7, 0) + np.where(bp.lx == bp.fw - 2, -0.25, 0)
    return t


def _fc(bp):
    """Texels from the middle of the face a strip texel is on (negative = towards its left edge)."""
    return bp.lx + 0.5 - bp.fw / 2.0


def _rivet(bp, pts, color, kind):
    """Round rivet heads two texels across at (strip x, y): lit top left, their shadow under them."""
    hi, lo = bp.S(), bp.S()
    for x, y in pts:
        x, y = int(x) % bp.SW, int(y)
        hi |= (bp.sx == x) & (bp.y == y)
        lo |= ((bp.sx == (x + 1) % bp.SW) & ((bp.y == y) | (bp.y == y + 1))) | ((bp.sx == x) & (bp.y == y + 1))
    bp.paint(hi | lo, color, kind, np.where(hi, 2.2, 0.2), edge=(0.3, 0.2, -1.2, -0.8), ao=1.5)


def fur_ruff(w, bp, key="fur", lock=4):
    """A thick ruff of fur in locks: each lock lit on its left and shaded on its right, their tips ragged along the
    lower edge so the plate below shows between them."""
    col, kind = w.c(key, "fur")
    m = bp.S(True)
    H = bp.H
    li, lp = bp.sx // lock, bp.sx % lock
    across = np.interp(lp, [0, 1, lock - 2, lock - 1], [0.9, 0.45, -0.15, -1.0])
    jit = (ak.hash01(li, 0, 17) - 0.5) * 0.8
    down = np.interp(bp.y, [0, 1, H * 0.55, H - 1], [0.8, 0.4, 0.0, -0.6])
    t = across + jit + down + aw.form(bp, m, "fur", amp=0.55, vert=0.0, spec=0)
    # the top face: the same locks seen end on round the arm, a dark middle where the arm goes in
    edge_d = np.minimum(np.minimum(bp.tx, bp.W - 1 - bp.tx), np.minimum(bp.ty, bp.D - 1 - bp.ty))
    ring = np.interp((bp.tx + bp.ty) % lock, [0, 1, lock - 2, lock - 1], [0.9, 0.4, 0.0, -0.7]) + np.interp(edge_d, [0, 2, 4], [0.9, 0.2, -0.6])
    top = np.where(edge_d >= max(2, (bp.W - 16) // 2 + 1), -2.6, ring)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=top, bottom=bp.T(True), bottom_tone=-1.8, edge=(0, 0, 0, 0))
    length = H - (ak.hash01(li, 1, 23) > 0.55).astype(int) - (ak.hash01(li, 2, 29) > 0.8).astype(int)
    point = (bp.y == length - 1) & ((lp == 0) | (lp == lock - 1))
    bp.erase((bp.y >= length) | point)


def cuff_plate(w, bp, key="metal", trim="trim"):
    """A flared elbow cuff: plate between two rolled rims, a row of rivets, the opening for the arm on top."""
    col, kind = w.c(key)
    tc, tk = w.c(trim)
    H = bp.H
    bp.paint(bp.S(True), col, kind, _plate_tone(bp, kind, 0.9), top=bp.T(True), top_tone=0.2, bottom=bp.T(True), bottom_tone=-2.0,
             edge=(0, 0, 0, 0))
    roll = np.where(bp.y == 0, 1.6, np.where(bp.y == 1, 0.5, np.where(bp.y == H - 2, 0.6, -1.0)))
    bp.paint(bp.rows(0, 2) | bp.rows(H - 2, H), tc, tk, roll + aw.form(bp, bp.S(True), tk, amp=0.6, vert=0, spec=0),
             edge=(0, 0, 0, 0), ao=1.2)
    step = max(4, int(round(bp.W / 4)))
    _rivet(bp, [(x, H // 2 - 1) for x in range(step // 2, bp.SW, step)], tc, tk)
    # top: a rolled rim round the edge, plate sloping in to the dark opening
    edge_d = np.minimum(np.minimum(bp.tx, bp.W - 1 - bp.tx), np.minimum(bp.ty, bp.D - 1 - bp.ty))
    bp.paint(None, tc, tk, top=edge_d < 2, top_tone=np.where(edge_d == 0, 1.4, 0.2) + np.where(bp.tx + bp.ty < bp.W, 0.4, -0.4),
             edge=(0, 0, 0, 0))
    bp.paint(None, w.c("dark")[0], "dark", top=edge_d >= max(3, (bp.W - 16) // 2 + 1), top_tone=-1.0, edge=(0, 0, 0, 0), ao=0)


def vambrace(w, bp, key="metal", trim="trim", emblem=None, side=None, straps=None):
    """The forearm plate. emblem (front): "frost" = chevrons of inlaid ice running down to the hand, "shield" = a
    raised shield. side (outer and inner faces alike): "snow" = an engraved snowflake, "flutes" = fluting.
    straps: leather straps with buckles across the back (the underside of the forearm in a punch)."""
    col, kind = w.c(key)
    tc, tk = w.c(trim)
    H, W = bp.H, bp.W
    m = bp.S(True)
    t = _plate_tone(bp, kind)
    fc = _fc(bp)
    if side == "flutes":
        n = 3
        per = bp.fw / n
        ph = (bp.lx % per) / per
        fl = np.where(ph < 0.22, 0.9, np.where(ph > 0.78, -1.0, np.where(ph > 0.5, -0.3, 0.1)))
        t = t + np.where(bp.sides & (bp.y >= 2) & (bp.y < H - 3), fl, 0)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=-1.5, bottom=bp.T(True), bottom_tone=-1.5, edge=(0, 0, 0, 0))
    # the wrist band
    band = np.where(bp.y == H - 3, 1.3, np.where(bp.y == H - 2, 0.3, -0.9)) + aw.form(bp, m, tk, amp=0.6, vert=0, spec=0)
    bp.paint(bp.rows(H - 3, H), tc, tk, band, edge=(0, 0, 0, 0), ao=1.4)
    body = bp.rows(2, H - 3)
    if emblem == "frost":
        for apex, half in ((H - 5, 5.5), (H - 8.5, 3.5)):
            v = bp.front & body & (np.abs(bp.y - (apex - np.abs(fc) * 0.8)) < 0.75) & (np.abs(fc) < half)
            bp.paint(v, tc, "crystal", np.where(fc < 0, 1.6, 0.6), edge=(0.5, 0.3, -0.9, -0.5), ao=1.2)
    elif emblem == "shield":
        ax, yy = np.abs(fc), bp.y - 2.0
        hh = H - 6.0
        outline = bp.front & (yy >= 0) & (yy < hh) & (ax < np.where(yy < hh * 0.55, 4.5, 4.5 - (yy - hh * 0.55) * 1.6))
        inner = bp.front & (yy >= 1) & (yy < hh - 1) & (ax < np.where(yy < hh * 0.55, 3.5, 3.5 - (yy - hh * 0.55) * 1.6))
        bp.paint(outline, tc, tk, np.where(fc < 0, 1.2, 0.2) + np.where(yy < 1, 0.8, 0), edge=(0.6, 0.4, -1.0, -0.6), ao=1.6)
        mc, mk = w.c("metal2")
        bp.paint(inner, mc, mk, np.where(fc < 0, 0.3, -0.5), edge=(-0.8, -0.5, 0.6, 0.4), ao=0)
        bar = inner & (ax < 1.0)
        bp.paint(bar, tc, tk, np.where(fc < 0, 1.5, 0.4), edge=(0.3, 0.2, -0.6, -0.3), ao=0.6)
    if side == "snow":
        for face_mid in (-(W + bp.D) / 2.0, (W + bp.D) / 2.0):
            aw.stamp(bp, "snow", face_mid, 2 + max(0, (H - 5 - 7) // 2), tc, "crystal", 1.0)
    if straps:
        lc, lk = w.c(straps, "leather")
        for y0 in (3, H - 7):
            sm = bp.back & bp.rows(y0, y0 + 2)
            bp.paint(sm, lc, lk, np.where(bp.y == y0, 0.6, -0.5), edge=(0.4, 0.0, -1.0, 0.0), ao=1.3)
            bk = bp.back & bp.rows(y0 - 1, y0 + 3) & (np.abs(_fc(bp)) < 2.0)
            hole = bp.back & bp.rows(y0, y0 + 2) & (np.abs(_fc(bp)) < 1.0)
            bp.paint(bk & ~hole, tc, tk, np.where(bp.y == y0 - 1, 1.4, np.where(bp.y == y0 + 2, -0.8, 0.4)),
                     edge=(0.5, 0.3, -1.0, -0.6), ao=1.4)
    # rivets in the corners of the front and of both side faces
    pts = []
    for f in ("right", "front", "left"):
        x0, x1 = bp.b.face_x(f)
        pts += [(x0 + 2, 2), (x1 - 4, 2)]
    _rivet(bp, pts, tc, tk)


def hand_lames(w, bp, key="metal2", trim="trim", lames=3):
    """The back of the hand: overlapping plates that let the fist close, each lit along its upper lip and dark under
    the next, the first one edged in trim."""
    col, kind = w.c(key)
    tc, tk = w.c(trim)
    H = bp.H
    m = bp.S(True)
    lh = H / lames
    k = np.floor(bp.y / lh).astype(int)
    r = bp.y - np.floor(k * lh)                    # row inside its lame
    last = np.floor((k + 1) * lh) - np.floor(k * lh) - 1
    lt = np.where(r == 0, 1.5, np.where(r == 1, 0.6, np.where(r == last, -1.5, np.where(r == last - 1, -0.5, 0.0))))
    t = lt + aw.form(bp, m, kind, amp=0.8, vert=0.0, spec=0.0) + np.where(bp.lx == 0, 0.5, 0) + np.where(bp.lx == bp.fw - 1, -0.6, 0)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=-0.4, bottom=bp.T(True), bottom_tone=-2.0, edge=(0, 0, 0, 0))
    bp.paint(bp.rows(0, 2), tc, tk, np.where(bp.y == 0, 1.4, -0.2) + aw.form(bp, m, tk, amp=0.6, vert=0, spec=0), edge=(0, 0, 0, 0), ao=1.2)
    # a rivet at each end of every lame on the two side faces
    pts = []
    for f in ("right", "left"):
        x0, x1 = bp.b.face_x(f)
        for i in range(1, lames):
            y = int(np.floor(i * lh)) + 2
            if y + 1 < H - 1:
                pts += [(x0 + 2, y), (x1 - 4, y)]
    _rivet(bp, pts, tc, tk)


def knuckle_guard(w, bp, key="metal", trim="trim", bumps=3):
    """A heavy plate over the end of the fist: a raised knuckle for every claw or spike across the front, carried
    over onto the striking face (the fist end), a lip of trim along its upper edge."""
    col, kind = w.c(key)
    tc, tk = w.c(trim)
    H, W, D = bp.H, bp.W, bp.D
    m = bp.S(True)
    per = W / bumps
    ph = ((bp.lx + 0.5) % per) / per
    dome = np.where(ph < 0.12, -0.9, np.where(ph < 0.3, 1.4, np.where(ph < 0.55, 0.6, np.where(ph < 0.85, -0.2, -1.2))))
    t = aw.form(bp, m, kind, amp=0.8, vert=0.0, spec=0.0) + np.where(bp.front, dome, 0)
    t = t + np.where(~bp.front & (bp.lx == 0), 0.5, 0) + np.where(~bp.front & (bp.lx == bp.fw - 1), -0.6, 0)
    t = t + np.where(bp.y == H - 1, -0.8, 0) + np.where(bp.y == 1, 0.5, 0)
    # the striking face: the knuckles along its front edge (texture rows near the front), a plain plate behind
    bph = ((bp.tx + 0.5) % per) / per
    front_d = bp.D - 1 - bp.ty                       # rows from the front edge
    depth = max(4, int(round(D * 0.55)))
    kn = np.where(bph < 0.12, -1.2, np.where(bph < 0.3, 1.3, np.where(bph < 0.6, 0.5, np.where(bph < 0.85, -0.2, -1.2))))
    kn = kn + np.where(front_d == 0, 0.6, np.where(front_d == depth - 1, -0.9, 0))
    plain = np.where(front_d == depth, 0.7, -0.5) + np.where(bp.tx < 2, 0.3, 0) + np.where(bp.ty == 0, -0.5, 0)
    bottom = np.where(front_d < depth, kn, plain)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=0.3, bottom=bp.T(True), bottom_tone=bottom, edge=(0, 0, 0, 0))
    bp.paint(bp.rows(0, 1), tc, tk, 1.2 + aw.form(bp, m, tk, amp=0.6, vert=0, spec=0), edge=(0, 0, 0, 0), ao=1.0)


def ice_claw(w, bp, key="crystal", seg=0, last=False, glow=True):
    """A claw of ice: facets lit on the left, a bright ridge down the front, white at the point."""
    col, kind = w.c(key, "crystal")
    m = bp.S(True)
    t = np.where(bp.lx < bp.fw / 2, 0.9, -0.5) + np.where(bp.front | bp.outer, 0.5, -0.4)
    t = t + np.where((bp.lx == 0) & (bp.front | bp.back), 0.9, 0) + np.where(bp.y == 0, 0.4, 0) + 0.5 * seg
    bp.paint(m, col, "crystal", t, top=bp.T(True), top_tone=0.6, bottom=bp.T(True), bottom_tone=2.6 if last else 1.0,
             edge=(0, 0, 0, 0))


def spike(w, bp, key="trim", seg=0, last=False):
    col, kind = w.c(key)
    m = bp.S(True)
    t = np.where(bp.lx < bp.fw / 2, 1.0, -0.7) + np.where(bp.front | bp.outer, 0.5, -0.5) + np.where(bp.y == 0, 0.5, 0)
    t = t + np.where(bp.y == bp.H - 1, -0.5, 0) + 0.3 * seg
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=0.5, bottom=bp.T(True), bottom_tone=2.4 if last else 0.8,
             edge=(0, 0, 0, 0))


def gem(w, bp, key="gem", glow=True):
    """A cut stone: a table facet with a white glint, darker bevels below and to the right."""
    col, kind = w.c(key, "crystal")
    m = bp.S(True)
    W, H = bp.fw, bp.H
    edge = (bp.lx == 0) | (bp.y == 0)
    far = (bp.lx == W - 1) | (bp.y == H - 1)
    t = np.where(edge, 1.6, np.where(far, -1.4, 0.3))
    t = np.where((bp.lx == 1) & (bp.y == 1), 3.0, t)
    bp.paint(m, col, "crystal", t, top=bp.T(True), top_tone=1.2, bottom=bp.T(True), bottom_tone=-1.0, edge=(0, 0, 0, 0))


PAINTERS = {"fur_ruff": fur_ruff, "cuff_plate": cuff_plate, "vambrace": vambrace, "hand_lames": hand_lames,
            "knuckle_guard": knuckle_guard, "ice_claw": ice_claw, "spike": spike, "gem": gem}
NOT_PAINT_OPTS = ("rot",)


# ------------------------------------------------------------------------------------------------- build

def build(name, fit=""):
    """(boxes, texture) of one design in one fit."""
    design = DESIGNS[name]
    g = FITS[fit]
    with _texels():
        parts = []
        for pname, painter, spec, opts in design["parts"](g):
            b = part_box(pname, spec, g > 0)
            b.painter, b.opts = painter, opts
            parts.append(b)
        h_uv = rig.pack(parts, top=0, width=rig.UVS, height=16)
        th = h_uv * rig.K
        cv = ak.Canvas(rig.TEX, th, rig.topology(parts, rig.TEX, th))
        w = _w(design, sum(map(ord, name)))
        for b in parts:
            opts = {k: v for k, v in b.opts.items() if k not in NOT_PAINT_OPTS}
            PAINTERS[b.painter](w, aw.BP(cv, b), **opts)
        cv.finish()
        tex = cv.render()
        for b in parts:                      # the game reads these outside the scaled rig: keep what it needs
            b.quads = rig.box_quads(b)
    return parts, tex


# block-model faces and the armor box faces they are, in model space (see A)
FACE_OF = {"north": "front", "south": "back", "east": "right", "west": "left", "up": "top", "down": "bottom"}
# FaceInfo: each face's four corners (min/max per axis) in FaceBakery's vertex order
FACE_INFO = {
    "down": [("x0", "y0", "z1"), ("x0", "y0", "z0"), ("x1", "y0", "z0"), ("x1", "y0", "z1")],
    "up": [("x0", "y1", "z0"), ("x0", "y1", "z1"), ("x1", "y1", "z1"), ("x1", "y1", "z0")],
    "north": [("x1", "y1", "z0"), ("x1", "y0", "z0"), ("x0", "y0", "z0"), ("x0", "y1", "z0")],
    "south": [("x0", "y1", "z1"), ("x0", "y0", "z1"), ("x1", "y0", "z1"), ("x1", "y1", "z1")],
    "west": [("x0", "y1", "z0"), ("x0", "y0", "z0"), ("x0", "y0", "z1"), ("x0", "y1", "z1")],
    "east": [("x1", "y1", "z1"), ("x1", "y0", "z1"), ("x1", "y0", "z0"), ("x1", "y1", "z0")],
}


def _face_uv(corners_model, box, face, th):
    """JSON uv + rotation for one element face so each corner gets the texel the armor box layout gives the same
    point of the arm (rig.box_quads: corners in texture order with the face's rectangle)."""
    normal = {"front": (0, 0, -1), "back": (0, 0, 1), "right": (-1, 0, 0), "left": (1, 0, 0), "top": (0, -1, 0),
              "bottom": (0, 1, 0)}[face]
    P, (u0, v0, uw, vh) = next((P, rect) for P, rect, n in box.quads if np.allclose(n, normal))
    tex_uv = [(u0, v0), (u0 + uw, v0), (u0 + uw, v0 + vh), (u0, v0 + vh)]
    want = []
    for c in corners_model:
        q = A @ np.asarray(c, float) + B
        k = int(np.argmin(np.linalg.norm(P - q, axis=1)))
        assert np.linalg.norm(P[k] - q) < 1e-6, (box.name, face)
        want.append(tex_uv[k])
    for rot in (0, 90, 180, 270):
        s = rot // 90
        # FaceBakery: vertex i gets corner j = (i + s) % 4 of (u1,v1) (u1,v2) (u2,v2) (u2,v1)
        j0 = [(i + s) % 4 for i in range(4)]
        u1 = v1 = u2 = v2 = None
        ok = True
        for i, j in enumerate(j0):
            u, v = want[i]
            if j in (0, 1):
                ok &= u1 is None or abs(u1 - u) < 1e-9
                u1 = u
            else:
                ok &= u2 is None or abs(u2 - u) < 1e-9
                u2 = u
            if j in (0, 3):
                ok &= v1 is None or abs(v1 - v) < 1e-9
                v1 = v
            else:
                ok &= v2 is None or abs(v2 - v) < 1e-9
                v2 = v
        if ok:
            sc_u, sc_v = 16.0 / (64 * K), 16.0 / th
            uv = [round(u1 * sc_u, 5), round(v1 * sc_v, 5), round(u2 * sc_u, 5), round(v2 * sc_v, 5)]
            return uv, rot
    raise ValueError(f"no uv rotation fits {box.name} {face}")


def elements(parts, th, tex_key="#worn"):
    out = []
    for b in parts:
        x0, y0, z0 = b.origin
        w, h, d = b.size
        q0, q1 = np.array([x0, y0, z0]), np.array([x0 + w, y0 + h, z0 + d])
        m0, m1 = to_model(q0), to_model(q1)
        lo, hi = np.minimum(m0, m1), np.maximum(m0, m1)
        lim = {"x0": lo[0], "y0": lo[1], "z0": lo[2], "x1": hi[0], "y1": hi[1], "z1": hi[2]}
        faces = {}
        for f, face in FACE_OF.items():
            if face in ("top", "bottom") and (b.W == 0 or b.D == 0):
                continue
            corners = [[lim[c[0]], lim[c[1]], lim[c[2]]] for c in FACE_INFO[f]]
            uv, rot = _face_uv(corners, b, face, th)
            fj = {"uv": uv, "texture": tex_key}
            if rot:
                fj["rotation"] = rot
            faces[f] = fj
        el = {"name": b.name, "from": [round(float(v), 4) for v in lo], "to": [round(float(v), 4) for v in hi], "faces": faces}
        r = b.opts.get("rot")
        if r:
            el["rotation"] = {"origin": [round(float(v), 4) for v in (lo + hi) / 2], "axis": r[0], "angle": r[1]}
        if b.opts.get("glow"):
            el["neoforge_data"] = {"block_light": 15, "sky_light": 15}
        out.append(el)
    assert all(-16 <= v <= 32 for e in out for v in e["from"] + e["to"])
    return out


def tex_id(name):
    return "beyondsolo:item/worn/" + name


def model_id(name, fit=""):
    return "beyondsolo:item/worn/" + name + fit


def write(name, preview=False):
    """Every fit of one design: models/item/worn/<name><fit>.json and its texture."""
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    for fit in FITS:
        parts, tex = build(name, fit)
        th = tex.shape[0]
        Image.fromarray(tex, "RGBA").save(os.path.join(TEX_DIR, name + fit + ".png"), optimize=True)
        model = {
            "render_type": "minecraft:cutout",
            "textures": {"worn": tex_id(name + fit), "particle": "beyondsolo:item/" + name},
            "elements": elements(parts, th),
            "display": worn_display(),
        }
        with open(os.path.join(MODEL_DIR, name + fit + ".json"), "w") as f:
            json.dump(model, f, separators=(",", ":"))
            f.write("\n")
    if preview:
        import gauntlet_preview
        gauntlet_preview.sheet(name)


def names():
    return list(DESIGNS)


if __name__ == "__main__":
    args = sys.argv[1:]
    prev = "--preview" in args
    todo = [a for a in args if not a.startswith("--")] or names()
    for n in todo:
        write(n, prev)
    print(f"worn gauntlets written: {len(todo)}")
