#!/usr/bin/env python3
"""
Builds the rune stone assets.

Run from the repo root:  python3 tools/gen_rune_assets.py [--preview out_dir]
Needs: pillow, numpy, scipy, cairosvg (the same as tools/gen_ui_assets.py).

- Item textures (64x64, painted with tools/hd_paint.py) in textures/item/rune: a stone for each grade (E fieldstone,
  D mossy slate, C carved basalt, B violet obsidian, A gold-bound stone, S crimson monolith), the eleven glyphs (the
  ten elements, then the seal) as a soft halo and a hot core, three stages of cracks, and the three engraving runes.
- Item models: the parts client.rune.RuneStoneModel puts together when a stone is drawn (the glyph and crack layers are
  full-bright and tinted in the rune's colour; the empty layers in front of them only move them to the tint index the
  colour handler expects), the stone item's own model and the engraving runes.
- Light textures for the world effects (white, tinted when drawn): the glyphs, Widow's Snare's web and Ashen
  Bulwark's tower shield.
- Distance-field icons (see tools/gen_ui_assets.py): the glyphs, the engravings and the fourteen rune arts (Phosphor
  icons where one fits, drawn here where none does).
- The two body animations of absorbing a stone (playerAnimator, see tools/gen_signature_anims.py).
- The English text: assets/beyondsolo_runes/lang/en_us.json.

The glyphs are a small alphabet of straight carved strokes, drawn from the same strokes at every size.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import hd_paint as hp  # noqa: E402
import gen_ui_assets as ui  # noqa: E402
from gen_signature_anims import emote  # noqa: E402

ASSETS = "src/main/resources/assets/beyondsolo"
ITEM_TEX = f"{ASSETS}/textures/item/rune"
MODELS = f"{ASSETS}/models/item"
FX = f"{ASSETS}/textures/effect"
ICONS = f"{ASSETS}/textures/gui/icon"
ANIMS = f"{ASSETS}/player_animations"
LANG = "src/main/resources/assets/beyondsolo_runes/lang/en_us.json"

N = 64                 # item texture size
CENTER = (32.0, 33.0)  # where the glyph sits on every stone
RECESS = 13.5          # radius of the carved disc the glyph sits in
GRADES = "EDCBAS"
# the light of a sealed stone by grade (Runes.gradeColor)
GRADE_COLOR = ["c8d4e0", "8fe0a0", "7fc4ff", "c79bff", "ffd27a", "ff6a5a"]
# how much sparkle and aura each grade gets (tools/hd_paint.py GRADE_FX)
GRADE_FX = ["common", "rare", "epic", "legendary", "mythic", "divine"]
ELEMENTS = ["physical", "fire", "water", "lightning", "air", "earth", "shadow", "light", "nature", "arcane"]
ELEMENT_COLOR = ["d8d8d8", "ff7a2e", "4fa8ff", "b8e2ff", "a8f0e0", "c9a35a", "9b7be0", "fff0a8", "8ce05a", "7f9cff"]
ENGRAVINGS = {"haste": "7fe3ff", "thrift": "8cf0a8", "might": "ff9a5a"}


# ================================================================================================ the glyphs

def arc(cx, cy, r, a0, a1, n=8):
    return [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * k / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * k / n)))
            for k in range(n + 1)]


def ngon(cx, cy, r, n, start=-90.0):
    return [(cx + r * math.cos(math.radians(start + 360 * k / n)), cy + r * math.sin(math.radians(start + 360 * k / n)))
            for k in range(n)]


def rays(n, r0, r1, r0b=None, r1b=None, start=-90.0):
    out = []
    for k in range(n):
        a = math.radians(start + 360 * k / n)
        a0, a1 = (r0, r1) if k % 2 == 0 or r0b is None else (r0b, r1b)
        out.append(("open", [(a0 * math.cos(a), a0 * math.sin(a)), (a1 * math.cos(a), a1 * math.sin(a))]))
    return out


# Each glyph is a list of strokes in a box from -1 to 1 (y down): ("open", points), ("closed", points) or
# ("dot", (x, y, r)). Order: the ten elements as in SkillElement, then the seal.
GLYPHS = [
    # physical: a sword, point up
    [("closed", [(0, -0.95), (0.17, -0.62), (0.13, 0.2), (-0.13, 0.2), (-0.17, -0.62)]),
     ("open", [(-0.62, 0.48), (-0.52, 0.3), (0.52, 0.3), (0.62, 0.48)]), ("open", [(0, 0.3), (0, 0.7)]), ("dot", (0, 0.86, 0.13))],
    # fire: a flame with a burning heart
    [("closed", [(0, -0.95), (0.45, -0.28), (0.62, 0.32), (0.32, 0.86), (-0.32, 0.86), (-0.62, 0.32), (-0.45, -0.28)]),
     ("closed", [(0, 0.04), (0.22, 0.47), (0, 0.72), (-0.22, 0.47)])],
    # water: a drop over two waves
    [("closed", [(0, -0.97), (0.28, -0.58), (0, -0.38), (-0.28, -0.58)]),
     ("open", [(-0.9, 0.24), (-0.45, -0.02), (0, 0.24), (0.45, -0.02), (0.9, 0.24)]),
     ("open", [(-0.9, 0.74), (-0.45, 0.48), (0, 0.74), (0.45, 0.48), (0.9, 0.74)])],
    # lightning: a bolt
    [("open", [(0.42, -0.95), (-0.3, 0.02), (0.3, 0.02), (-0.42, 0.95)])],
    # air: three gusts
    [("open", [(-0.92, -0.52), (0.3, -0.52), (0.62, -0.84)]),
     ("open", [(-0.62, 0.0), (0.6, 0.0), (0.92, -0.32)]),
     ("open", [(-0.92, 0.52), (0.22, 0.52), (0.54, 0.84)])],
    # earth: two peaks on solid ground
    [("open", [(-0.92, 0.72), (-0.3, -0.64), (0.12, 0.2), (0.44, -0.24), (0.92, 0.72)]),
     ("open", [(-0.96, 0.72), (0.96, 0.72)])],
    # shadow: a crescent moon and the stars it hides
    [("closed", arc(-0.08, 0, 0.92, -58, -302, 8) + arc(0.39, 0, 0.8, 91, 269, 6)[1:-1]),
     ("dot", (0.62, -0.3, 0.12)), ("dot", (0.52, 0.38, 0.09))],
    # light: a sun
    [("closed", ngon(0, 0, 0.4, 8, -90 + 22.5))] + rays(8, 0.64, 0.97, 0.64, 0.82),
    # nature: a tree, branching and rooted
    [("open", [(0, 0.95), (0, -0.95)]), ("open", [(-0.56, -0.42), (0, 0.08), (0.56, -0.42)]),
     ("open", [(-0.32, -0.76), (0, -0.44), (0.32, -0.76)]), ("open", [(-0.46, 0.95), (0, 0.6), (0.46, 0.95)])],
    # arcane: a star-eye
    [("closed", [(0, -0.95), (0.6, 0), (0, 0.95), (-0.6, 0)]), ("open", [(-0.96, 0), (-0.6, 0)]),
     ("open", [(0.6, 0), (0.96, 0)]), ("dot", (0, 0, 0.17))],
    # the seal: a ring, locked
    [("closed", ngon(0, 0, 0.92, 8, -90 + 22.5)), ("dot", (0, -0.2, 0.27)), ("fill", [(-0.13, -0.1), (0.13, -0.1), (0.24, 0.52), (-0.24, 0.52)])],
]

# the engraving runes' marks, in the same hand
MARKS = {
    # haste: running forward
    "haste": [("open", [(-0.72, -0.72), (-0.04, 0), (-0.72, 0.72)]), ("open", [(0.0, -0.72), (0.68, 0), (0.0, 0.72)])],
    # thrift: a drop, held back by a bar
    "thrift": [("closed", [(0, -0.95), (0.46, -0.12), (0.34, 0.42), (0, 0.6), (-0.34, 0.42), (-0.46, -0.12)]),
               ("open", [(-0.66, 0.92), (0.66, 0.92)])],
    # might: a burst
    "might": [("closed", [(0, -0.95), (0.24, -0.24), (0.95, 0), (0.24, 0.24), (0, 0.95), (-0.24, 0.24), (-0.95, 0), (-0.24, -0.24)]),
              ("dot", (0, 0, 0.12))],
}


def draw_strokes(strokes, size, cx, cy, scale, width, ss=8, hi=False):
    """Coverage (0..1) of the strokes on a size x size grid; (cx, cy) the centre and scale the pixels per unit.
    hi=True returns the supersampled mask instead."""
    n = size * ss
    img = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(img)
    w = width * ss

    def at(x, y):
        return (cx + x * scale) * ss, (cy + y * scale) * ss

    for kind, data in strokes:
        if kind == "dot":
            x, y, r = data
            px, py = at(x, y)
            rr = r * scale * ss
            d.ellipse([px - rr, py - rr, px + rr, py + rr], fill=255)
            continue
        pts = [at(x, y) for x, y in data]
        if kind == "fill":
            d.polygon(pts, fill=255)
            continue
        if kind == "closed":
            pts = pts + [pts[0]]
        d.line(pts, fill=255, width=max(1, int(round(w))))
        for px, py in pts:
            d.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=255)
    if hi:
        return np.asarray(img) >= 128
    return np.asarray(img.resize((size, size), Image.BOX), float) / 255


def glyph_layers(strokes, scale=11.6, width=2.3, cx=CENTER[0], cy=CENTER[1], threshold=0.42):
    """The two glyph layers of a 64px item: a halo (tinted with the colour) and a white-hot core."""
    cov = draw_strokes(strokes, N, cx, cy, scale, width)
    core = cov >= threshold
    ring1 = hp.grow(core) & ~core
    ring2 = hp.grow(core, 2) & ~core & ~ring1
    halo = np.zeros((N, N, 4), np.uint8)
    halo[ring1] = (255, 255, 255, 190)
    halo[ring2] = (255, 255, 255, 80)
    hot = np.zeros((N, N, 4), np.uint8)
    hot[core] = (255, 255, 255, 255)
    return Image.fromarray(halo), Image.fromarray(hot), core


# ================================================================================================ the stones

def recess_extra(depth=0.34, cx=CENTER[0], cy=CENTER[1], r=RECESS):
    """Tone offsets that carve a disc into the stone: a darker floor, the inner wall in shadow at the upper left and
    lit at the lower right, and a raised lip around it lit the other way."""
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    dx, dy = xx - cx, yy - cy
    d = np.hypot(dx, dy)
    lit = (dx * -0.62 + dy * -0.78) / np.maximum(d, 1e-6)  # > 0 on the side facing the light
    extra = np.zeros((N, N))
    inside = d <= r
    extra[inside] -= depth
    wall = inside & (d > r - 2.4)
    extra[wall] -= lit[wall] * 0.6
    lip = (d > r) & (d <= r + 1.7)
    extra[lip] += lit[lip] * 0.5 + 0.12
    return extra


def disc(r, cx=CENTER[0], cy=CENTER[1]):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    return np.hypot(xx - cx, yy - cy) <= r


def ring_mask(r0, r1, cx=CENTER[0], cy=CENTER[1]):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    d = np.hypot(xx - cx, yy - cy)
    return (d > r0) & (d <= r1)


def lit_side(cx=CENTER[0], cy=CENTER[1]):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    dx, dy = xx - cx, yy - cy
    return (dx * -0.62 + dy * -0.78) / np.maximum(np.hypot(dx, dy), 1e-6)


def inlay(a, mask, base, cx=CENTER[0], cy=CENTER[1]):
    """A metal ring set into the stone: lit on its upper left, shaded on its lower right."""
    r = hp.ramp(hp.hexc(base))
    lit = lit_side(cx, cy)
    for y, x in zip(*np.nonzero(mask)):
        t = lit[y, x]
        a[y, x, :3] = r[4][:3] if t > 0.55 else r[3][:3] if t > 0.0 else r[2][:3] if t > -0.55 else r[1][:3]


def shape_pebble():
    pts = []
    for k in range(56):
        a = k / 56 * math.tau
        w = 1 + 0.055 * math.sin(3 * a + 0.7) + 0.04 * math.sin(5 * a + 2.1) + 0.025 * math.sin(9 * a + 0.4)
        pts.append((32 + 24.5 * w * math.cos(a), 34 + 21.5 * w * math.sin(a)))
    m = hp.poly(pts, N)
    chip = hp.poly([(45, 5), (62, 5), (62, 24), (55, 19), (49, 13)], N)
    return m & ~chip


def shape_tablet():
    pts = [(32 + 20 * math.cos(math.pi + math.pi * k / 24), 23 + 18 * math.sin(math.pi + math.pi * k / 24)) for k in range(25)]
    pts += [(52, 30), (51, 49)]
    pts += [(45 + 6 * math.cos(math.pi / 2 * k / 6), 52 + 6 * math.sin(math.pi / 2 * k / 6)) for k in range(7)]
    pts += [(19 + 6 * math.cos(math.pi / 2 + math.pi / 2 * k / 6), 52 + 6 * math.sin(math.pi / 2 + math.pi / 2 * k / 6)) for k in range(7)]
    pts += [(13, 49), (12, 30)]
    return hp.poly(pts, N)


def chamfer(points, cut):
    out = []
    n = len(points)
    for i in range(n):
        p, prev, nxt = np.array(points[i]), np.array(points[i - 1]), np.array(points[(i + 1) % n])
        a = p + (prev - p) / np.linalg.norm(prev - p) * cut
        b = p + (nxt - p) / np.linalg.norm(nxt - p) * cut
        out += [tuple(a), tuple(b)]
    return out


def shape_hex():
    pts = [(32 + 22 * math.cos(math.radians(a)), 33 + 26 * math.sin(math.radians(a))) for a in (-90, -30, 30, 90, 150, 210)]
    return hp.poly(chamfer(pts, 3.2), N), pts


def shape_gem():
    pts = [(32, 7), (44, 11), (51, 23), (51, 41), (44, 53), (32, 58), (20, 53), (13, 41), (13, 23), (20, 11)]
    return hp.poly(pts, N), pts


def shape_shield():
    pts = [(19, 7), (45, 7)]
    pts += [(45 + 8 * math.cos(-math.pi / 2 + math.pi / 2 * k / 6), 15 + 8 * math.sin(-math.pi / 2 + math.pi / 2 * k / 6)) for k in range(7)]
    pts += [(53, 40), (50, 48), (32, 59), (14, 48), (11, 40)]
    pts += [(19 + 8 * math.cos(math.pi + math.pi / 2 * k / 6), 15 + 8 * math.sin(math.pi + math.pi / 2 * k / 6)) for k in range(7)]
    return hp.poly(pts, N)


def shape_monolith():
    # a pointed arch over the top, the foot cut square
    body = [(14, 55), (14, 23), (16, 15), (21, 9), (27, 5.5), (32, 4), (37, 5.5), (43, 9), (48, 15), (50, 23), (50, 55),
            (46, 58), (18, 58)]
    m = hp.poly(body, N)
    horn_l = hp.poly([(15, 26), (6, 9), (21, 16)], N)
    horn_r = hp.poly([(49, 26), (58, 9), (43, 16)], N)
    return m, horn_l | horn_r


def lichen(a, m, seed, colors, n=7, top=30):
    rng = np.random.default_rng(seed)
    cand = np.argwhere(m & hp.shrink(m, 1) & ~hp.shrink(m, 6) & (np.mgrid[0:N, 0:N][0] < top))
    for _ in range(n):
        y, x = cand[rng.integers(len(cand))]
        for dy, dx in ((0, 0), (0, 1), (1, 0), (1, 1), (-1, 0), (0, -1))[: rng.integers(3, 7)]:
            yy, xx = y + dy, x + dx
            if 0 <= yy < N and 0 <= xx < N and m[yy, xx]:
                a[yy, xx, :3] = colors[(dy + dx) % len(colors)]


def seams_from_edge(body, seed, plan):
    """Veins (or glowing cracks) that run in from the edge of a stone towards the glyph: (angle, length) each, seen
    from the centre; each starts where that ray leaves the body and wanders inwards, with a short branch."""
    rng = np.random.default_rng(seed)
    out = np.zeros((N, N), bool)
    cx, cy = CENTER
    for ang, length in plan:
        a = math.radians(ang)
        r = RECESS + 2
        while r < 40 and body[int(cy + r * math.sin(a)) % N, int(cx + r * math.cos(a)) % N]:
            r += 0.5
        r -= 1.5
        pts = walk(rng, cx + r * math.cos(a), cy + r * math.sin(a), ang + 180, length)
        k = len(pts) // 2
        branch = walk(rng, pts[k][0], pts[k][1], ang + 180 + rng.choice([-55, 55]), max(3, length // 2))
        for y, x in rasterize(pts) | rasterize(branch):
            if 0 <= y < N and 0 <= x < N:
                out[y, x] = True
    return out & body


def stone(tier):
    """One grade's stone (RGBA array, before the finishing pass) and its silhouette."""
    cv = hp.Canvas(N)
    yy, xx = np.mgrid[0:N, 0:N]
    if tier == 0:
        # E: a weathered fieldstone, the glyph scratched into a shallow hollow; lichen on the top
        m = shape_pebble()
        cv.layer(m, hp.hexc("8e8779"), "stone", seed=11, extra=recess_extra(0.26))
        a = cv.a
        lichen(a, m, 4, [(176, 186, 104), (134, 148, 76), (196, 204, 140)])
    elif tier == 1:
        # D: a slate tablet, moss on its crown
        m = shape_tablet()
        cv.layer(m, hp.hexc("6d877b"), "stone", seed=12, extra=recess_extra(0.32))
        a = cv.a
        n = hp.value_noise((N, N), 3, 41)
        moss = m & ~hp.shrink(m, 3) & (yy < 24) & (n > -0.25)
        drip = hp.grow(moss, 1) & m & (n > 0.35) & (yy < 27)
        moss |= drip
        lit = (xx < 30)
        for y, x in zip(*np.nonzero(moss)):
            a[y, x, :3] = (128, 170, 86) if lit[y, x] and n[y, x] > 0.1 else (92, 132, 62) if n[y, x] > -0.1 else (62, 96, 48)
        lichen(a, m, 9, [(150, 190, 100), (110, 150, 70)], n=4, top=40)
    elif tier == 2:
        # C: a cut basalt hexagon with a carved border and studs at its corners
        m, corners = shape_hex()
        cv.layer(m, hp.hexc("57709a"), "stone", seed=13, extra=recess_extra(0.36))
        a = cv.a
        groove = hp.shrink(m, 3) & ~hp.shrink(m, 4)
        hp.engrave(a, groove, 0.42)
        for (x, y) in corners:
            sx, sy = 32 + (x - 32) * 0.72, 33 + (y - 33) * 0.72
            stud = hp.ellipse(sx, sy, 1.6, 1.6, N) & m
            hp.engrave(a, stud, 0.3)
        hp.engrave(a, ring_mask(RECESS + 2.2, RECESS + 3.0) & m, 0.3)
    elif tier == 3:
        # B: violet obsidian cut like a gem, a silver ring around the glyph
        m, pts = shape_gem()
        extra = recess_extra(0.42) + hp.facets(m, 21) * 0.38 * ~disc(RECESS + 1.5)
        cv.layer(m, hp.hexc("5c3d8e"), "obsidian", seed=14, extra=extra)
        a = cv.a
        inlay(a, ring_mask(RECESS + 0.2, RECESS + 1.6) & m, "c8cfe0")
        # glints along the cut edges
        for (x0, y0), (x1, y1) in zip(pts, pts[1:] + pts[:1]):
            if y0 + y1 < 64:
                g = hp.thick_line((x0 * 0.82 + 32 * 0.18, y0 * 0.82 + 33 * 0.18), (x1 * 0.82 + 32 * 0.18, y1 * 0.82 + 33 * 0.18), 0.9, N)
                a[g & m & ~disc(RECESS + 2), :3] = np.minimum(255, a[g & m & ~disc(RECESS + 2), :3].astype(int) + 40)
    elif tier == 4:
        # A: dark stone bound in gold, gold veins running through it
        m = shape_shield()
        bezel = m & ~hp.shrink(m, 3)
        body = m & ~bezel
        cv.layer(body, hp.hexc("4c4757"), "stone", seed=15, extra=recess_extra(0.38))
        cv.layer(bezel, hp.hexc("d9a93c"), "gold", seed=16)
        cv.seams()
        a = cv.a
        inlay(a, ring_mask(RECESS + 0.2, RECESS + 1.7) & body, "e0b448")
        veins = seams_from_edge(body, 77, [(-140, 9), (-35, 8), (25, 10), (150, 9), (95, 6)]) & hp.shrink(body, 1) & ~disc(RECESS + 2.5)
        hp.glow_paint(a, veins, (240, 200, 96), halo=0.3, halo_px=1, core=False)
        for (x, y) in ((15.5, 11), (47.5, 11), (13, 42), (50, 42), (31, 56)):
            hp.rivet(a, x - 1, y - 1, hp.hexc("f0cc6a"))
    else:
        # S: a crimson-black monolith in a dark gold frame, horned, a gem over the glyph and embers in its cracks
        m, horns = shape_monolith()
        frame = m & ~hp.shrink(m, 3)
        body = m & ~frame
        cv.layer(body, hp.hexc("561824"), "obsidian", seed=17, extra=recess_extra(0.42) + hp.facets(m, 5) * 0.22 * ~disc(RECESS + 1.5))
        cv.layer(frame, hp.hexc("b8862c"), "gold", seed=18)
        cv.layer(horns & ~m, hp.hexc("3a1620"), "obsidian", seed=19)
        cv.seams()
        m = m | horns
        a = cv.a
        line = hp.shrink(m & ~horns, 3) & ~hp.shrink(m & ~horns, 4)
        a[line & body, :3] = (236, 190, 92)
        inlay(a, ring_mask(RECESS + 0.2, RECESS + 1.7) & body, "e8b84e")
        embers = seams_from_edge(body, 31, [(-150, 8), (-28, 8), (40, 9), (120, 9), (75, 7), (200, 6)]) & hp.shrink(body, 1) & ~disc(RECESS + 2.5)
        hp.glow_paint(a, embers, (255, 120, 44), halo=0.45, halo_px=1)
        hp.gem(a, 32, 12.5, 2.6, hp.hexc("e8283c"))
    return cv.a, m


def stone_image(tier):
    a, m = stone(tier)
    return hp.finish(a, grade=GRADE_FX[tier], accent=hp.hexc(GRADE_COLOR[tier]), seed=tier + 3)


def all_masks():
    return [stone(t)[1] for t in range(6)]


# ================================================================================================ the cracks

def walk(rng, x, y, heading, length):
    pts = [(x, y)]
    ang = heading
    for _ in range(length):
        ang += rng.uniform(-30, 30)
        ang = ang * 0.7 + heading * 0.3
        if rng.random() < 0.16:
            ang += rng.choice([-48, 48])
        x += math.cos(math.radians(ang))
        y += math.sin(math.radians(ang))
        pts.append((x, y))
    return pts


def rasterize(pts):
    out = set()
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
        for s in range(steps + 1):
            t = s / steps
            out.add((int(math.floor(y0 + (y1 - y0) * t)), int(math.floor(x0 + (x1 - x0) * t))))
    return out


def crack_stages():
    """Three masks, each holding the one before: hairlines from the carved disc, spreading, about to burst."""
    rng = np.random.default_rng(23)
    inner = np.logical_and.reduce(all_masks())
    inner = hp.shrink(inner, 2)
    cx, cy = CENTER
    # (angle, the stage it appears at, full length); the first ones grow over the three stages
    plan = [(-118, 1, 15), (-20, 1, 16), (72, 1, 14), (160, 2, 12), (-66, 2, 11), (28, 2, 12), (118, 3, 9),
            (204, 3, 10), (-160, 3, 8), (250, 3, 7)]
    paths = []
    for ang, born, length in plan:
        a = math.radians(ang)
        pts = walk(rng, cx + (RECESS + 0.6) * math.cos(a), cy + (RECESS + 0.6) * math.sin(a), ang, length)
        paths.append((born, pts))
        if born == 1:
            # a branch late on
            k = int(len(pts) * 0.55)
            paths.append((3, walk(rng, pts[k][0], pts[k][1], ang + rng.choice([-50, 50]), 5)))
    # hairlines into the disc itself: the glyph splitting
    for ang, born in ((-40, 2), (140, 3), (95, 3)):
        a = math.radians(ang)
        paths.append((born, walk(rng, cx + (RECESS + 0.5) * math.cos(a), cy + (RECESS + 0.5) * math.sin(a), ang + 180, 6)))
    stages = []
    for s in (1, 2, 3):
        m = np.zeros((N, N), bool)
        for born, pts in paths:
            if s < born:
                continue
            frac = min(1.0, (s - born + 1) / (4 - born))
            keep = pts[: max(2, int(round(len(pts) * frac)))]
            for y, x in rasterize(keep):
                if 0 <= y < N and 0 <= x < N:
                    m[y, x] = True
        stages.append(m & inner)
    return stages


def crack_image(m):
    a = np.zeros((N, N, 4), np.uint8)
    halo = hp.grow(m) & ~m
    a[halo] = (255, 255, 255, 80)
    a[m] = (255, 255, 255, 255)
    return Image.fromarray(a)


# ================================================================================================ engraving runes

def engraving_tablet(color):
    pts = [(32, 4), (43, 13), (46, 45), (32, 60), (18, 45), (21, 13)]
    m = hp.poly(pts, N)
    base = hp.mix(hp.hexc("c3c9d6"), hp.hexc(color), 0.28)
    cv = hp.Canvas(N)
    cv.layer(m, base, "crystal", seed=8, extra=recess_extra(0.3, r=11.0))
    a = cv.a
    inlay(a, ring_mask(11.2, 12.4) & m, "e8eef8")
    return hp.finish(a, grade="epic", accent=hp.hexc(color), seed=8)


# ================================================================================================ models

def item_layers(textures, emissive=(), parent="neoforge:item/default"):
    model = {"parent": parent, "loader": "neoforge:item_layers",
             "textures": {f"layer{i}": t for i, t in enumerate(textures)}}
    if emissive:
        model["neoforge_data"] = {"layers": {str(i): {"block_light": 15, "sky_light": 15} for i in emissive}}
    return model


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=indent, ensure_ascii=False)
        f.write("\n")


def write_models():
    blank = "beyondsolo:item/rune/blank"
    for t in range(6):
        write_json(f"{MODELS}/rune/stone_{t}.json", item_layers([f"beyondsolo:item/rune/stone_{t}"]))
    for g in range(len(GLYPHS)):
        write_json(f"{MODELS}/rune/glyph_{g}.json", item_layers(
            [blank, f"beyondsolo:item/rune/glyph_{g}_halo", f"beyondsolo:item/rune/glyph_{g}_core"], emissive=(1, 2)))
    for c in (1, 2, 3):
        write_json(f"{MODELS}/rune/crack_{c}.json", item_layers([blank, blank, blank, f"beyondsolo:item/rune/crack_{c}"], emissive=(3,)))
    # the item's own model (RuneStoneModel draws over it): a sealed E stone
    write_json(f"{MODELS}/rune_stone.json", item_layers(
        ["beyondsolo:item/rune/stone_0", f"beyondsolo:item/rune/glyph_{len(GLYPHS) - 1}_halo",
         f"beyondsolo:item/rune/glyph_{len(GLYPHS) - 1}_core"], emissive=(1, 2)))
    for e in ENGRAVINGS:
        write_json(f"{MODELS}/rune_of_{e}.json", item_layers(
            [f"beyondsolo:item/rune/engraving_{e}", f"beyondsolo:item/rune/mark_{e}_halo", f"beyondsolo:item/rune/mark_{e}_core"],
            emissive=(1, 2)))


# ================================================================================================ textures

def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def write_item_textures():
    os.makedirs(ITEM_TEX, exist_ok=True)
    save_png(Image.new("RGBA", (16, 16), (0, 0, 0, 0)), f"{ITEM_TEX}/blank.png")
    for t in range(6):
        save_png(stone_image(t), f"{ITEM_TEX}/stone_{t}.png")
    for g, strokes in enumerate(GLYPHS):
        halo, core, _ = glyph_layers(strokes)
        save_png(halo, f"{ITEM_TEX}/glyph_{g}_halo.png")
        save_png(core, f"{ITEM_TEX}/glyph_{g}_core.png")
    for c, m in enumerate(crack_stages(), start=1):
        save_png(crack_image(m), f"{ITEM_TEX}/crack_{c}.png")
    for e, color in ENGRAVINGS.items():
        save_png(engraving_tablet(color), f"{ITEM_TEX}/engraving_{e}.png")
        halo, core, _ = glyph_layers(MARKS[e], scale=9.2, width=2.4)
        save_png(halo, f"{ITEM_TEX}/mark_{e}_halo.png")
        save_png(core, f"{ITEM_TEX}/mark_{e}_core.png")


# ================================================================================================ light textures

def light_rgba(alpha):
    rgba = np.zeros(alpha.shape + (4,), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def glow_alpha(sharp, size, tight=0.85, wide=0.7):
    """A bright line with a tight glow and a wide one (sharp: an L image at the final size)."""
    g = np.asarray(sharp.filter(ImageFilter.GaussianBlur(size / 64)), np.float32) / 255
    w = np.asarray(sharp.filter(ImageFilter.GaussianBlur(size / 20)), np.float32) / 255
    a = np.asarray(sharp, np.float32) / 255
    return np.clip(a + g * tight + w * wide, 0, 1)


def fx_glyph(strokes, size=128):
    cov = draw_strokes(strokes, size, size / 2, size / 2, size * 0.36, size * 0.055, ss=4)
    return light_rgba(glow_alpha(Image.fromarray((cov * 255).astype(np.uint8), "L"), size))


def quad(p0, c, p1, n=12):
    return [((1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * c[0] + t * t * p1[0], (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * c[1] + t * t * p1[1])
            for t in np.linspace(0, 1, n)]


def sag(p0, p1, toward=(0.0, 0.0), amount=0.16):
    """The control point of a strand between two spokes, pulled towards the hub."""
    mx, my = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
    return mx + (toward[0] - mx) * amount * 2, my + (toward[1] - my) * amount * 2


def fx_web(size=256, ss=4):
    """Widow's Snare: a web laid flat on the ground, strands sagging between the spokes, a few broken at the rim."""
    n = size * ss
    img = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(img)
    c = n / 2
    rng = np.random.default_rng(12)
    spokes = 12
    angles = [k * 360 / spokes + rng.uniform(-7, 7) for k in range(spokes)]

    def pt(a, r):
        return c + r * math.cos(math.radians(a)), c + r * math.sin(math.radians(a))

    for a in angles:
        d.line([pt(a, 0.02 * n), pt(a, 0.47 * n * rng.uniform(0.9, 1.0))], fill=255, width=int(2.4 * ss))
    rings = [0.08, 0.15, 0.22, 0.29, 0.36, 0.43]
    for ri, rr in enumerate(rings):
        radii = [rr * n * rng.uniform(0.94, 1.05) for _ in angles]
        for k in range(spokes):
            if ri >= len(rings) - 2 and rng.random() < 0.2:
                continue
            p0, p1 = pt(angles[k], radii[k]), pt(angles[(k + 1) % spokes], radii[(k + 1) % spokes])
            d.line(quad(p0, sag(p0, p1, (c, c), 0.1), p1), fill=255, width=int(1.7 * ss), joint="curve")
    d.ellipse([c - 0.025 * n, c - 0.025 * n, c + 0.025 * n, c + 0.025 * n], fill=255)
    a = glow_alpha(img.resize((size, size), Image.LANCZOS), size, 0.7, 0.45)
    yy, xx = np.mgrid[0:size, 0:size] + 0.5
    r = np.hypot(xx - size / 2, yy - size / 2) / (size / 2)
    return light_rgba(a * np.clip((1.0 - r) / 0.1, 0, 1))


def shield_outline(x0, x1, top, shoulder, bottom, steps=24):
    arch = [(x0 + (x1 - x0) * t, shoulder - (shoulder - top) * math.sin(math.pi * t)) for t in np.linspace(0, 1, steps)]
    return [(x0, bottom)] + arch + [(x1, bottom)]


def fx_ward(w=128, h=192, ss=4):
    """Ashen Bulwark: a tower shield of light, standing on the ground (the bottom edge), a honeycomb inside."""
    W, H = w * ss, h * ss
    lines = Image.new("L", (W, H), 0)
    body = Image.new("L", (W, H), 0)
    lattice = Image.new("L", (W, H), 0)
    d, b, l = ImageDraw.Draw(lines), ImageDraw.Draw(body), ImageDraw.Draw(lattice)
    outer = shield_outline(10 * ss, (w - 10) * ss, 12 * ss, 40 * ss, H + 4 * ss)
    inner = shield_outline(21 * ss, (w - 21) * ss, 24 * ss, 50 * ss, H + 4 * ss)
    b.polygon(outer, fill=255)
    d.line(outer, fill=255, width=6 * ss, joint="curve")
    d.line(inner, fill=255, width=2 * ss, joint="curve")
    d.line([(W / 2, 34 * ss), (W / 2, H)], fill=255, width=3 * ss)
    for y in (84, 140):
        d.line([(24 * ss, y * ss), ((w - 24) * ss, y * ss)], fill=255, width=2 * ss)
    r = 9 * ss
    for row in range(-1, int(H / (r * 1.5)) + 2):
        for col in range(-1, int(W / (r * math.sqrt(3))) + 2):
            cx = col * r * math.sqrt(3) + (row % 2) * r * math.sqrt(3) / 2
            cy = row * r * 1.5
            hexagon = [(cx + r * math.cos(math.radians(30 + 60 * k)), cy + r * math.sin(math.radians(30 + 60 * k))) for k in range(7)]
            l.line(hexagon, fill=255, width=max(1, ss))
    inside = Image.new("L", (W, H), 0)
    ImageDraw.Draw(inside).polygon(inner, fill=255)
    lat = np.asarray(lattice.resize((w, h), Image.LANCZOS), np.float32) / 255 * (np.asarray(inside.resize((w, h), Image.LANCZOS), np.float32) / 255)
    fill = np.asarray(body.resize((w, h), Image.LANCZOS), np.float32) / 255
    a = glow_alpha(lines.resize((w, h), Image.LANCZOS), w, 0.8, 0.5) + fill * 0.14 + lat * 0.3
    # brighter at the foot, where the ash rises from
    yy = np.mgrid[0:h, 0:w][0] / h
    return light_rgba(np.clip(a, 0, 1) * (0.6 + 0.4 * yy))


def write_light_textures():
    def save(img, name):
        save_png(img, f"{FX}/{name}.png")
        with open(f"{FX}/{name}.png.mcmeta", "w") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)

    for g, strokes in enumerate(GLYPHS):
        save(fx_glyph(strokes), f"rune_glyph_{g}")
    save(fx_web(), "rune_web")
    save(fx_ward(), "rune_ward")


# ================================================================================================ icons

class Pen:
    """Draws a mask for a distance-field icon in the units of the glyphs: -1..1 fills the icon's content box."""

    def __init__(self):
        self.n = ui.SIZE * ui.SS
        self.img = Image.new("L", (self.n, self.n), 0)
        self.d = ImageDraw.Draw(self.img)
        self.s = ui.CONTENT * ui.SS / 2

    def at(self, x, y):
        return self.n / 2 + x * self.s, self.n / 2 + y * self.s

    def poly(self, pts, fill=255):
        self.d.polygon([self.at(*q) for q in pts], fill=fill)

    def line(self, pts, w, fill=255):
        pp = [self.at(*q) for q in pts]
        ww = w * self.s
        self.d.line(pp, fill=fill, width=int(ww), joint="curve")
        for x, y in pp:
            self.d.ellipse([x - ww / 2, y - ww / 2, x + ww / 2, y + ww / 2], fill=fill)

    def circle(self, x, y, r, fill=255):
        cx, cy = self.at(x, y)
        rr = r * self.s
        self.d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=fill)

    def svg(self, name, x, y, size):
        """A Phosphor icon, its view box {@code size} units wide, centred on (x, y)."""
        import io
        import cairosvg
        px = int(size * self.s)
        png = cairosvg.svg2png(url=os.path.join(ui.ICON_SRC, name + ".svg"), output_width=px, output_height=px)
        alpha = Image.open(io.BytesIO(png)).convert("RGBA").split()[3].point(lambda v: 255 if v >= 128 else 0)
        cx, cy = self.at(x, y)
        layer = Image.new("L", self.img.size, 0)
        layer.paste(alpha, (int(cx - px / 2), int(cy - px / 2)))
        self.img = Image.fromarray(np.maximum(np.asarray(self.img), np.asarray(layer)))
        self.d = ImageDraw.Draw(self.img)

    def ring(self, x, y, r, w):
        self.circle(x, y, r + w / 2)
        self.circle(x, y, r - w / 2, fill=0)

    def mask(self):
        return np.asarray(self.img) >= 128


def icon_lance(p):
    u = np.array([math.cos(math.radians(-45)), math.sin(math.radians(-45))])
    v = np.array([-u[1], u[0]])

    def at(a, b):
        q = (a - 0.04) * u + b * v
        return float(q[0]), float(q[1])

    p.line([at(-1.2, 0), at(0.22, 0)], 0.15)
    p.line([at(0.2, -0.28), at(0.2, 0.28)], 0.13)
    p.poly([at(0.24, 0), at(0.5, 0.26), at(0.86, 0.21), at(1.3, 0), at(0.86, -0.21), at(0.5, -0.26)])
    p.poly([at(0.48, 0), at(0.6, 0.1), at(0.84, 0.08), at(1.04, 0), at(0.84, -0.08), at(0.6, -0.1)], fill=0)


def icon_tower_shield(p):
    p.poly(shield_outline(-0.68, 0.68, -0.94, -0.58, 0.96))
    p.poly(shield_outline(-0.52, 0.52, -0.77, -0.45, 0.8), fill=0)
    p.line([(0, -0.62), (0, 0.84)], 0.13)
    p.circle(0, 0.06, 0.27)
    p.circle(0, 0.06, 0.12, fill=0)


def icon_wisp(p):
    cx, cy, r = 0.0, 0.3, 0.5
    lower = [(cx + r * math.cos(t), cy + r * math.sin(t)) for t in np.linspace(math.radians(-15), math.radians(195), 28)]
    tip = (0.16, -0.96)
    left = quad(lower[-1], (-0.5, -0.45), tip, 14)
    right = quad(tip, (0.42, -0.42), lower[0], 14)
    p.poly(lower + left[1:] + right[1:])
    p.circle(0.0, 0.32, 0.2, fill=0)
    for x, y, rr in ((-0.66, -0.3, 0.09), (0.6, -0.6, 0.07), (0.72, 0.02, 0.06)):
        p.circle(x, y, rr)


def icon_web(p):
    angles = [-90 + 60 * k for k in range(6)]

    def pt(a, rr):
        return rr * math.cos(math.radians(a)), rr * math.sin(math.radians(a))

    for a in angles:
        p.line([(0, 0), pt(a, 0.96)], 0.12)
    for rr in (0.4, 0.76):
        for k in range(6):
            p0, p1 = pt(angles[k], rr), pt(angles[(k + 1) % 6], rr)
            p.line(quad(p0, sag(p0, p1, (0, 0), 0.14), p1), 0.11)
    p.circle(0, 0, 0.12)


def icon_slashes(p):
    for off, length in ((-0.4, 0.78), (0.0, 1.0), (0.4, 0.78)):
        p0, p1, c = np.array([0.66, -0.84]), np.array([-0.84, 0.66]), np.array([-0.2, -0.2])
        mid = (p0 + p1) / 2
        p0, p1 = mid + (p0 - mid) * length, mid + (p1 - mid) * length
        shift = np.array([1, 1]) / math.sqrt(2) * off
        ts = np.linspace(0, 1, 24)
        pts = np.array([(1 - t) ** 2 * p0 + 2 * (1 - t) * t * c + t * t * p1 for t in ts]) + shift
        tang = np.gradient(pts, axis=0)
        nrm = np.stack([-tang[:, 1], tang[:, 0]], 1)
        nrm /= np.linalg.norm(nrm, axis=1, keepdims=True)
        w = 0.21 * np.sin(np.pi * ts) ** 0.8 + 0.005
        left = pts + nrm * w[:, None] / 2
        right = pts - nrm * w[:, None] / 2
        p.poly([tuple(q) for q in left] + [tuple(q) for q in right[::-1]])


def icon_sigils(p):
    for x, y, r, w in ((0.34, 0.4, 0.46, 0.11), (-0.46, -0.12, 0.31, 0.1), (0.2, -0.66, 0.22, 0.09)):
        p.ring(x, y, r, w)
        k = r * 0.55
        p.poly([(x, y - k), (x + k * 0.62, y), (x, y + k), (x - k * 0.62, y)])


def icon_breath(p):
    """A flame and the roar rolling out of it."""
    p.svg("fire", -0.3, 0.0, 1.25)
    for r, w in ((0.78, 0.12), (1.12, 0.12)):
        pts = [(-0.3 + r * math.cos(math.radians(a)), r * math.sin(math.radians(a))) for a in np.linspace(-42, 42, 16)]
        p.line(pts, w)


def icon_svg(name):
    return ui.icon_mask(name)


ART_ICONS = {
    "hunters_howl": lambda: icon_svg("paw-print"),
    "skittering_brood": lambda: icon_svg("bug"),
    "crystal_carapace": lambda: icon_svg("diamonds-four"),
    "hollow_lance": icon_lance,
    "ashen_bulwark": icon_tower_shield,
    "cinder_wisp": icon_wisp,
    "titans_fall": lambda: icon_svg("arrow-line-down"),
    "widows_snare": icon_web,
    "dread_presence": lambda: icon_svg("eyes"),
    "phantom_flurry": icon_slashes,
    "sigil_cascade": icon_sigils,
    "wyrmfire_roar": icon_breath,
    "quickened_pulse": lambda: icon_svg("gauge"),
    "iron_resolve": lambda: icon_svg("shield-checkered"),
}


def art_icon_mask(path):
    make = ART_ICONS[path]
    if make.__code__.co_argcount == 0:
        return make()
    pen = Pen()
    make(pen)
    return pen.mask()


def glyph_icon_mask(strokes):
    return draw_strokes(strokes, ui.SIZE, ui.SIZE / 2, ui.SIZE / 2, ui.CONTENT / 2 * 0.98, ui.CONTENT * 0.085, ss=ui.SS, hi=True)


def write_icons():
    for g, strokes in enumerate(GLYPHS):
        ui.write_sdf(ui.sdf_from_mask(glyph_icon_mask(strokes)), f"rune_glyph_{g}")
    for e, strokes in MARKS.items():
        ui.write_sdf(ui.sdf_from_mask(glyph_icon_mask(strokes)), f"engrave_{e}")
    for path in ART_ICONS:
        ui.write_sdf(ui.sdf_from_mask(art_icon_mask(path)), f"skill_{path}")


# ================================================================================================ body animations

ANIMATIONS = {
    # the stone raised to the chest in both hands, the body tensing over it as it cracks (Runes.USE_TICKS = 40)
    "rune_channel": emote("rune_channel", "Hold a rune stone to the chest as it cracks", 46, 60, {
        "rightArm": [(4, {"pitch": -50, "yaw": -38}), (18, {"pitch": -55, "yaw": -41}), (32, {"pitch": -60, "yaw": -44}),
                     (35, {"pitch": -57, "yaw": -43}), (37, {"pitch": -63, "yaw": -45}), (39, {"pitch": -58, "yaw": -43}),
                     (41, {"pitch": -64, "yaw": -45}), (43, {"pitch": -59, "yaw": -44}), (46, {"pitch": -62, "yaw": -44})],
        "leftArm": [(4, {"pitch": -50, "yaw": 38}), (18, {"pitch": -55, "yaw": 41}), (32, {"pitch": -60, "yaw": 44}),
                    (35, {"pitch": -63, "yaw": 45}), (37, {"pitch": -57, "yaw": 43}), (39, {"pitch": -64, "yaw": 45}),
                    (41, {"pitch": -58, "yaw": 43}), (43, {"pitch": -63, "yaw": 45}), (46, {"pitch": -60, "yaw": 44})],
        "torso": [(4, {"pitch": -4}), (18, {"pitch": -7}), (32, {"pitch": -11}), (46, {"pitch": -12})],
        "head": [(4, {"pitch": 20}), (18, {"pitch": 16}), (32, {"pitch": 10}), (46, {"pitch": 8})],
        "rightLeg": [(4, {"roll": 4}), (32, {"roll": 7}), (46, {"roll": 7})],
        "leftLeg": [(4, {"roll": -4}), (32, {"roll": -7}), (46, {"roll": -7})],
    }),
    # the stone shatters: the arms are thrown open, the chest lifts and the head goes back as the light pours in
    "rune_release": emote("rune_release", "Thrown open as the rune's light pours in", 24, 36, {
        "rightArm": [(3, {"pitch": -112, "yaw": 68}, "EASEOUTBACK"), (11, {"pitch": -118, "yaw": 74}), (24, {"pitch": -55, "yaw": 30})],
        "leftArm": [(3, {"pitch": -112, "yaw": -68}, "EASEOUTBACK"), (11, {"pitch": -118, "yaw": -74}), (24, {"pitch": -55, "yaw": -30})],
        "torso": [(3, {"pitch": 10}, "EASEOUTQUAD"), (11, {"pitch": 13}), (24, {"pitch": 3})],
        "head": [(3, {"pitch": -34}, "EASEOUTQUAD"), (11, {"pitch": -42}), (24, {"pitch": -8})],
        "rightLeg": [(3, {"roll": 8}), (11, {"roll": 9}), (24, {"roll": 4})],
        "leftLeg": [(3, {"roll": -8}), (11, {"roll": -9}), (24, {"roll": -4})],
    }),
}


def write_animations():
    os.makedirs(ANIMS, exist_ok=True)
    for name, data in ANIMATIONS.items():
        with open(f"{ANIMS}/{name}.json", "w") as f:
            json.dump(data, f, indent=1)


# ================================================================================================ text

ARTS = {
    # path: (name, flavour, description); the description's %s are the skill's numbers, in RuneArts' order
    "hunters_howl": ("Hunter's Howl", "The Gloomfang's call. The pack answers; the prey freezes.",
                     "Howl: you and allies within 10 blocks run faster and hit harder for %s seconds, and monsters within 7 blocks are slowed and marked."),
    "skittering_brood": ("Skittering Brood", "A Broodling's last gift: a nest that never stops hatching.",
                         "Loose a brood of spectral spiderlings ahead of you. Each monster they reach takes %s damage and is poisoned, then %s more from bites each second for 3 seconds."),
    "crystal_carapace": ("Crystal Carapace", "A Shardback's crystal, grown under your skin.",
                         "+%s armour and some knockback resistance. A monster that strikes you in melee has a %s%% chance to take a shard for %s damage."),
    "hollow_lance": ("Hollow Lance", "The Hollow Acolytes' rite: what it takes from them, it gives to you.",
                     "Hurl a lance of hollow light up to 16 blocks. Every monster it passes through takes %s magic damage and is weakened, and gives you %s mana back."),
    "ashen_bulwark": ("Ashen Bulwark", "The Ashen Sentinel's tower shield, raised from the ground.",
                      "For %s seconds, blows from the front deal %s%% less damage and %s%% of a melee blow is thrown back. Nothing knocks you back."),
    "cinder_wisp": ("Cinder Wisp", "An Emberwisp, caught and thrown.",
                    "Throw a burning wisp in an arc up to 18 blocks. It bursts where it lands for %s damage and sets everything near it on fire."),
    "titans_fall": ("Titan's Fall", "The Brute never stayed down. Neither does the ground it hits.",
                    "Leap up to 12 blocks and land like the Brute: %s damage and a launch to monsters near the landing, and the ground keeps quaking for 3 seconds."),
    "widows_snare": ("Widow's Snare", "The Broodmother's web. Nothing it catches leaves.",
                     "Cast a web over the ground up to 16 blocks away. Monsters caught in it are held fast for %s seconds, poisoned and bitten for %s each second."),
    "dread_presence": ("Dread Presence", "The Alpha's killing intent, now yours to let loose.",
                       "For %s seconds, monsters within 12 blocks are weakened and slowed and the lesser ones freeze in terror. You take less damage."),
    "phantom_flurry": ("Phantom Flurry", "The Fallen Knight's last duel, fought again in a heartbeat.",
                       "Blink to the monster in your sights (12 blocks) and cut it %s times, %s damage each. The last cut throws it back."),
    "sigil_cascade": ("Sigil Cascade", "The Magus's spell: a sigil for every foe in sight.",
                      "Sigils burn under up to %s of the nearest monsters you can see and burst a moment later for %s magic damage each."),
    "wyrmfire_roar": ("Wyrmfire Roar", "The Wyrm's voice, and its fire with it.",
                      "Roar a cone of flame 10 blocks ahead for %s damage that sets monsters ablaze. Everything within 16 blocks freezes in terror, and you are kept from burning."),
    "quickened_pulse": ("Quickened Pulse", "A rune from the deepest gates. Your heart beats faster than it should.",
                        "+%s%% movement speed and +%s%% attack speed."),
    "iron_resolve": ("Iron Resolve", "A rune from the deepest gates. It holds when nothing else does.",
                     "Below 30%% health, you take %s%% less damage."),
}

TEXT = {
    "itemGroup.beyondsolo.runes": "Beyond Solo: Runes",
    "item.beyondsolo.rune_stone": "Rune Stone",
    "item.beyondsolo.rune_stone.sealed": "Sealed %s-rank Rune Stone",
    "item.beyondsolo.rune_stone.named": "Rune Stone: %s",
    "item.beyondsolo.rune_of_haste": "Rune of Haste",
    "item.beyondsolo.rune_of_thrift": "Rune of Thrift",
    "item.beyondsolo.rune_of_might": "Rune of Might",
    "craft.beyondsolo.cat.runes": "RUNES",
    "skills.beyondsolo.rune_art": "Rune art",

    "rune.beyondsolo.tip.grade": "%s-rank rune",
    "rune.beyondsolo.tip.sealed": "The skill inside shows itself when the seal breaks.",
    "rune.beyondsolo.tip.rune_art": "Rune art: any class",
    "rune.beyondsolo.tip.class_rune": "%s only",
    "rune.beyondsolo.tip.use": "Hold use to absorb it. A skill you already know rises %s levels.",
    "rune.beyondsolo.tip.engraving": "Carves itself into one of your active skills, for good.",
    "rune.beyondsolo.tip.engrave_use": "Use to choose the skill",

    "rune.beyondsolo.refuse.asleep": "The stone stays cold. Only an awakened hunter can absorb a rune.",
    "rune.beyondsolo.refuse.rank": "The rune is too strong for you yet: rank %s needed.",
    "rune.beyondsolo.refuse.class": "The rune will not answer you: it is for a %s.",
    "rune.beyondsolo.refuse.mastered": "%s is already mastered.",
    "rune.beyondsolo.refuse.nothing": "The seal holds nothing left for you.",

    "rune.beyondsolo.overlay.absorbing": "ABSORBING",
    "rune.beyondsolo.overlay.sealed": "Sealed %s-rank rune",

    "rune.beyondsolo.notify.raised.title": "Rune absorbed",
    "rune.beyondsolo.notify.raised.body": "%s rose to Lv. %s",
    "rune.beyondsolo.notify.engraved.title": "Skill engraved",
    "rune.beyondsolo.notify.engraved.body": "The rune of %1$s is carved into %2$s: %3$s",
    "rune.beyondsolo.notify.engraved.replaced": "The rune of %1$s is carved into %2$s: %3$s. %4$s was worn away.",

    "rune.beyondsolo.engraving.haste": "Haste",
    "rune.beyondsolo.engraving.haste.effect": "%s%% shorter cooldown",
    "rune.beyondsolo.engraving.thrift": "Thrift",
    "rune.beyondsolo.engraving.thrift.effect": "%s%% less mana",
    "rune.beyondsolo.engraving.might": "Might",
    "rune.beyondsolo.engraving.might.effect": "%s%% more damage",

    "rune.beyondsolo.engrave.title": "ENGRAVE A SKILL",
    "rune.beyondsolo.engrave.hint": "One engraving per skill. A new one wears the old away.",
    "rune.beyondsolo.engrave.none": "You know no active skill to carve it into.",
    "rune.beyondsolo.engrave.pick": "Pick a skill",
    "rune.beyondsolo.engrave.replaces": "Replaces %s",
    "rune.beyondsolo.engrave.same": "Already carved",
    "rune.beyondsolo.engrave.carve": "CARVE",
    "rune.beyondsolo.engrave.carved": "CARVED",
    "rune.beyondsolo.engrave.cancel": "CANCEL",
    "rune.beyondsolo.engrave.held": "%s in your bags",
    "rune.beyondsolo.engrave.unknown": "You don't know that skill.",
    "rune.beyondsolo.engrave.passive": "Only active skills take an engraving.",
}


def write_lang():
    out = dict(TEXT)
    for path, (name, flavour, desc) in ARTS.items():
        out[f"skill.beyondsolo.{path}"] = name
        out[f"skill.beyondsolo.{path}.flavour"] = flavour
        out[f"skill.beyondsolo.{path}.desc"] = desc
    write_json(LANG, out)


# ------------------------------------------------------------------------------------------------ previews

def tint(img, color, white=0.0):
    a = np.asarray(img, float).copy()
    c = np.array(hp.hexc(color)[:3], float)
    c = c * (1 - white) + 255 * white
    a[..., :3] = a[..., :3] * c / 255
    return Image.fromarray(a.astype(np.uint8))


def composite(tier, glyph, color, crack=0):
    base = stone_image(tier).copy()
    halo, core, _ = glyph_layers(GLYPHS[glyph])
    base.alpha_composite(tint(halo, color))
    base.alpha_composite(tint(core, color, 0.5))
    if crack:
        base.alpha_composite(tint(crack_image(crack_stages()[crack - 1]), color))
    return base


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    scale, cell = 4, 64 * 4 + 12
    bg = (34, 36, 44, 255)
    # every grade with the seal, then a skill glyph, then the three crack stages
    rows = []
    for t in range(6):
        col = GRADE_COLOR[t]
        e = (t * 3 + 1) % 10
        rows.append([composite(t, 10, col), composite(t, e, ELEMENT_COLOR[e])] +
                    [composite(t, e, ELEMENT_COLOR[e], c) for c in (1, 2, 3)])
    sheet = Image.new("RGBA", (cell * 5, cell * 6), bg)
    for r, row in enumerate(rows):
        for c, img in enumerate(row):
            sheet.alpha_composite(img.resize((256, 256), Image.NEAREST), (c * cell + 6, r * cell + 6))
    sheet.save(os.path.join(out_dir, "rune_stones.png"))
    # all glyphs on the C stone, at 4x and at the size they show in a slot
    sheet = Image.new("RGBA", (cell * 6, cell * 2 + 60), bg)
    for g in range(len(GLYPHS)):
        col = ELEMENT_COLOR[g] if g < 10 else GRADE_COLOR[2]
        img = composite(2 if g % 2 else 3, g, col)
        sheet.alpha_composite(img.resize((256, 256), Image.NEAREST), ((g % 6) * cell + 6, (g // 6) * cell + 6))
    for g in range(len(GLYPHS)):
        col = ELEMENT_COLOR[g] if g < 10 else GRADE_COLOR[2]
        sheet.alpha_composite(composite(1, g, col).resize((32, 32), Image.BOX), (g * 40 + 6, cell * 2 + 14))
    sheet.save(os.path.join(out_dir, "rune_glyphs.png"))


def main():
    write_item_textures()
    write_models()
    write_light_textures()
    write_icons()
    write_animations()
    write_lang()
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
    print("rune assets written")


if __name__ == "__main__":
    main()
