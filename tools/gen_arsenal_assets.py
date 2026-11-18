#!/usr/bin/env python3
"""
Generates the arsenal's assets (no hand-drawn files needed):

- 39 weapon sprites (32x32 pixel art, built from parts along the item's diagonal like tools/gen_combat_assets.py),
  bows with their three pull frames, gauntlet icons
- a light sweep animation on legendary, mythic and divine sprites (.png strip + .mcmeta)
- Better Combat weapon attribute files, and the English names and lore (the item models come from
  tools/gen_weapon_models.py, which this calls at the end)
- status icons (frostbite, frozen, infernal rend) and the loot beam light texture

Run from the repo root:  python3 tools/gen_arsenal_assets.py
Then run tools/gen_hd_gear.py: it replaces the weapon, armor and forge stone sprites with 64x64 versions.
Needs: pillow, numpy, cairosvg.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.dirname(__file__))
import gen_combat_assets as gc  # noqa: E402
from gen_combat_assets import Part, blade, const, guard, grip, hexc, pommel, shade, uv, outline, poly_mask, paint_icon, S  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
ITEM_OUT = os.path.join(ASSETS, "textures/item")
LANG = os.path.join(ASSETS, "lang/en_us.json")
ATTR_OUT = os.path.join(ROOT, "src/main/resources/data/beyondsolo/weapon_attributes")
EFFECT_ICON_OUT = os.path.join(ASSETS, "textures/mob_effect")
FX_OUT = os.path.join(ASSETS, "textures/effect")

# ------------------------------------------------------------------------------------------------ palettes
STEEL, DARK_STEEL, LEATHER, WOOD, GOLD = gc.STEEL, gc.DARK_STEEL, gc.LEATHER, gc.WOOD, gc.GOLD
EMBER, VENOM, CRIMSON, BLOOD, DUSK = gc.EMBER, gc.VENOM, gc.CRIMSON, gc.BLOOD, gc.DUSK
BONE = (hexc("f4ecd8"), hexc("d6c8a4"), hexc("8e7c58"))
RUST = (hexc("b8906a"), hexc("8a6248"), hexc("4e3426"))
RAGS = (hexc("8a9a5a"), hexc("5e6a3a"), hexc("343c20"))
ICE = (hexc("f2fcff"), hexc("9ed8ff"), hexc("3f86c0"))
FROST_STEEL = (hexc("e8f6ff"), hexc("a9c8de"), hexc("5a7a96"))
STORM = (hexc("e8f4ff"), hexc("8aa6d8"), hexc("3a4a7a"))
BOLT = (hexc("ffffff"), hexc("fff6a8"), hexc("ffd84a"))
OBSIDIAN = (hexc("6a5a7e"), hexc("2e2638"), hexc("120e18"))
VIOLET = (hexc("e0c8ff"), hexc("9b7bff"), hexc("4a2e9a"))
ROSE = (hexc("fff0f6"), hexc("ffb8d4"), hexc("c8608c"))
SILVER = (hexc("ffffff"), hexc("d4dae4"), hexc("8a94a6"))
MOON = (hexc("f2f8ff"), hexc("bcd2f0"), hexc("6a86b4"))
NAGA = (hexc("b8f0d8"), hexc("4ab894"), hexc("1e5a48"))
PALE_WOOD = (hexc("e8dcc0"), hexc("c4b08a"), hexc("8a7452"))
DARK_WOOD = (hexc("8a6248"), hexc("5a3e2c"), hexc("2e1e14"))
LIVING_WOOD = (hexc("a8c27a"), hexc("6e8a48"), hexc("3a4e24"))
LEAF = (hexc("d6ff9a"), hexc("8ce05a"), hexc("3a8a28"))
BRONZE = (hexc("f0c890"), hexc("c08a4a"), hexc("6e4420"))
STONE = (hexc("d8d2c4"), hexc("a09a8a"), hexc("5a5648"))
WHITE_GOLD = (hexc("fffef0"), hexc("ffe9a0"), hexc("c8a040"))
NAVY = (hexc("5a6aa8"), hexc("2a3468"), hexc("12183a"))
MAGMA = (hexc("fff0a0"), hexc("ff8a2a"), hexc("c0300a"))
BLACK_IRON = (hexc("5a5a66"), hexc("2c2c34"), hexc("121216"))
BLUE_ORB = (hexc("e8f6ff"), hexc("5ab0ff"), hexc("1e4a9a"))
EYE = (hexc("ffe6ff"), hexc("c050ff"), hexc("4a0a7a"))


# ------------------------------------------------------------------------------------------------ helpers
class Fn:
    """A free-form layer: f(u, v) -> colour or None."""

    def __init__(self, f, relief=None):
        self.f = f
        self.relief = relief        # optional depth: (u, v, grow) -> half thickness in design units, or None

    def color_at(self, u, v):
        return self.f(u, v)


def ball_relief(uc, vc, r):
    """Depth of an orb: a ball."""
    def h(u, v, g):
        d2 = (u - uc) ** 2 + (v - vc) ** 2
        return math.sqrt((r + g) ** 2 - d2) if d2 <= (r + g) ** 2 else None
    return h


def flanged_relief(uc, r, flanges):
    """Depth of a flanged mace head: a ball in the middle, flange plates that thin towards their tips."""
    def h(u, v, g):
        d = math.hypot(u - uc, v)
        core = r * 0.62 + g
        best = math.sqrt(core * core - d * d) if d <= core else None
        k = (math.atan2(v, u - uc) / (2 * math.pi) * flanges) % 1.0
        if d <= r + g and (k < 0.4 + 0.08 * g or k > 1 - 0.08 * g):
            plate = r * (0.5 - 0.3 * max(0.0, d - r * 0.62) / (r * 0.38))
            best = plate if best is None else max(best, plate)
        return best
    return h


def circle(uc, vc, r, colors):
    light, mid, dark = colors

    def f(u, v):
        du, dv = u - uc, v - vc
        d = math.hypot(du, dv)
        if d > r:
            return None
        # light from the upper-left (+v), dark at the lower-right edge
        if d < r * 0.38 and dv > -0.2:
            return shade(light, 0.35)
        if dv - du * 0.3 > r * 0.25:
            return light
        if d > r * 0.72 and dv < 0:
            return dark
        return mid
    return Fn(f, relief=ball_relief(uc, vc, r))


def diamond(u0, u1, w, colors, peak=0.45):
    """A crystal: width grows to w at `peak` of its length, then narrows to a point."""
    def width(u):
        t = (u - u0) / (u1 - u0)
        return w * (t / peak if t < peak else (1 - t) / (1 - peak))
    return Part(u0, u1, width, colors, relief="gem")


def axe(u0, u1, reach, colors, side=1, inner=0.8):
    """A one-sided axe blade along [u0, u1], reaching `reach` px to one side of the shaft."""
    def width(u):
        t = (u - u0) / (u1 - u0)
        return max(0.0, reach * math.sin(math.pi * t) ** 0.6) / 2

    def relief(u, v, g):
        # thick where it wraps the haft, honed at the cutting edge, thinner towards its two horns
        if u < u0 - g or u > u1 + g:
            return None
        w = width(min(max(u, u0), u1))
        vv = (v - side * (inner + w)) * side          # -w at the haft, +w at the edge
        if abs(vv) > w + g:
            return None
        k = (vv + w + g) / (2 * (w + g))
        root = min(1.05, 0.3 + 0.5 * w)
        return root + (0.14 - root) * k ** 0.8
    return Part(u0, u1, width, colors, bend=lambda u: side * (inner + width(u)), relief=relief)


def flanged(uc, r, colors, flanges=6):
    light, mid, dark = colors

    def f(u, v):
        d = math.hypot(u - uc, v)
        ang = math.atan2(v, u - uc)
        k = (ang / (2 * math.pi) * flanges) % 1.0
        if d <= r * 0.62 or (d <= r and k < 0.4):
            return light if v > 0.8 else dark if v < -1.5 else mid
        return None
    return Fn(f, relief=flanged_relief(uc, r, flanges))


def shaft(u0, u1, colors, w=1.0):
    return Part(u0, u1, const(w), colors, relief="round")


def serrated(u0, u1, base, tip_len, colors, teeth=2.2, depth=0.6, bend=None):
    def width(u):
        w = base if u <= u1 - tip_len else base * max(0.0, (u1 - u) / tip_len)
        return max(0.0, w - depth * (0.5 + 0.5 * math.sin(u * math.pi * 2 / teeth)))
    return Part(u0, u1, width, colors, bend=bend, fuller=False, relief="blade")


def line_glow(color, alpha=70):
    c = hexc(color)
    return (c[0], c[1], c[2], alpha)


def render(parts, glow=None):
    return gc.render_weapon(parts, glow=glow)


# ------------------------------------------------------------------------------------------------ bows
#
# A bow lies along the sprite's diagonal like every other weapon, bent towards the upper-left (its back, the side
# that faces the target). The string runs below it and the arrow lies across it, pointing to the upper-left: the
# layout of vanilla's bow, whose drawing pose every bow uses.
#
# `draw`: how far the string is pulled back, in design units (0: at rest, no arrow). The limbs bend with it: the
# tips come back and in. Every bow has its own build (length, depth, recurve, what its tips and grip are).

BOW_MID = 18.562            # u of the grip and of the arrow: the middle of the diagonal, between two texel diagonals
BOW_STRING = -0.5303        # v of the string at rest: between two texel diagonals, so it is an even two pixels
ARROW_LEN = 22.0
DRAWS = (2.2, 4.2, 6.2, 7.8, 9.0)       # the pull frames...
PULLS = (0.0, 0.2, 0.45, 0.7, 0.9)      # ...and the seconds of drawing from which each one shows

SERPENT = (hexc("a8e07a"), hexc("3f8a34"), hexc("14321c"))
SNAKE_NECK = 0.8            # where along a limb of the serpent bow the snake's head begins
GALE = (hexc("ffffff"), hexc("bfeee4"), hexc("4ab894"))
STAR_BLUE = (hexc("ffffff"), hexc("b8e2ff"), hexc("5a8ad8"))
FEATHER = (hexc("ffffff"), hexc("e4e4ea"), hexc("a8a8b8"))

BOW_SPECS = {
    "hunters_shortbow": dict(wood=WOOD, string="e8e0d0", tips=DARK_STEEL, grip=LEATHER, L=12.6, bulge=4.6, thick=1.35,
                             bands=((0.5, DARK_WOOD), (0.74, DARK_WOOD))),
    "windrunner_longbow": dict(wood=PALE_WOOD, string="ffffff", tips=NAGA, grip=NAGA, L=15.6, bulge=5.6, thick=1.2,
                               recurve=1.1, bands=((0.3, NAGA), (0.56, NAGA)), feather=(hexc("e8fff6"), hexc("9fe6cc"), hexc("3f9a80"))),
    "serpentsting_bow": dict(wood=SERPENT, string="b8f07a", tips=VENOM, grip=DUSK, L=14.6, bulge=5.2, thick=1.45,
                             style="serpent", glow="7ccf3a/70", head=VENOM, feather=(hexc("d6ff9a"), hexc("7ccf3a"), hexc("2e5a1e"))),
    "galebreaker_warbow": dict(wood=GALE, string="a8f0e0", tips=WHITE_GOLD, grip=NAVY, L=15.2, bulge=6.6, thick=1.7,
                               recurve=2.3, style="gale", glow="a8f0e0/100", feather=(hexc("ffffff"), hexc("a8f0e0"), hexc("3f9a80"))),
    "starfall_longbow": dict(wood=WHITE_GOLD, string="fff6c8", tips=STAR_BLUE, grip=NAVY, L=14.0, bulge=6.2, thick=1.6,
                             recurve=1.5, style="star", glow="fff0a8/170",
                             head=(hexc("ffffff"), hexc("fff0a8"), hexc("ffd84a")), shaft=WHITE_GOLD,
                             feather=(hexc("ffffff"), hexc("b8e2ff"), hexc("5a8ad8"))),
}


def tone3(colors, x):
    """Light, mid or dark of a (light, mid, dark) triple; tools/gen_hd_gear.py swaps in its smooth ramp."""
    return colors[0] if x > 0.4 else colors[2] if x < -0.4 else colors[1]


def seg_dist(u, v, a, b):
    du, dv = b[0] - a[0], b[1] - a[1]
    t = max(0.0, min(1.0, ((u - a[0]) * du + (v - a[1]) * dv) / max(1e-9, du * du + dv * dv)))
    return math.hypot(u - a[0] - t * du, v - a[1] - t * dv)


class BowShape:
    """Where everything of a bow is, for one draw length."""

    def __init__(self, spec, draw=0.0):
        self.spec, self.draw = spec, draw
        self.L0, self.bulge, self.thick = spec["L"], spec["bulge"], spec["thick"]
        self.recurve = spec.get("recurve", 0.0)
        self.L = self.L0 * (1 - 0.011 * draw)                    # drawn, a bow is shorter from tip to tip
        self.grip_v = BOW_STRING + 0.3 + self.bulge - self.recurve
        self.tip_v = self._arc(1.0)
        self.nock = (BOW_MID, BOW_STRING - draw)
        self.tips = ((BOW_MID - self.L, self.tip_v - 0.3), (BOW_MID + self.L, self.tip_v - 0.3))

    def k(self, u):
        return abs(u - BOW_MID) / self.L

    def arc(self, u):
        """v of the limb's middle line."""
        k = self.k(u)
        if k > 1:                                                # past the string: straight on, curling forward
            e = 1e-3
            slope = (self._arc(1.0) - self._arc(1.0 - e)) / e
            return self._arc(1.0) + slope * (k - 1) + 14.0 * (k - 1) ** 2
        return self._arc(k)

    def _arc(self, k):
        v = self.grip_v - self.bulge * k * k - 0.33 * self.draw * k ** 1.6
        if self.recurve:
            v += self.recurve * (1 - 0.04 * self.draw) * max(0.0, (k - 0.68) / 0.32) ** 2
        return v

    def width(self, u):
        k = self.k(u)
        w = self.thick * (1 - 0.5 * min(1.0, k) ** 1.5)
        if self.spec.get("style") == "serpent":
            w *= 1 + 0.08 * math.sin(k * 17)                     # a snake's body swells and narrows
            if k > SNAKE_NECK:                                   # and ends in a head
                s = min(1.0, (k - SNAKE_NECK) / (1 - SNAKE_NECK))
                w = self.thick * (0.5 + 1.0 * math.sin(math.pi * s ** 0.7) ** 0.8 * (1 - 0.3 * s))
                if s > 0.75:
                    w = max(0.0, w * math.sqrt(max(0.0, 1 - ((s - 0.75) / 0.25) ** 2)))
        return w

    def grip_point(self):
        """The middle of the grip in item model units (16 to the sprite)."""
        u, v = BOW_MID, self.grip_v
        return ((u - v) * gc.DIAG + gc.ORIGIN[0]) / 2.0, ((u + v) * gc.DIAG + gc.ORIGIN[1]) / 2.0


def bow_parts(spec, draw=0.0):
    sh = BowShape(spec, draw)
    px = 32.0 / S                                               # design units to the texel
    um, L = BOW_MID, sh.L
    string_c = hexc(spec["string"])
    lo, hi = sh.tips

    def on_string(u, v, grow=0.0):
        return min(seg_dist(u, v, lo, sh.nock), seg_dist(u, v, sh.nock, hi)) <= 0.6 * px + grow

    string = Fn(lambda u, v: string_c if on_string(u, v) else None,
                relief=lambda u, v, g: 0.3 if on_string(u, v, g) else None)
    string.soft_outline = True                                  # a cord, not a black-edged bar
    string.geometry = True                                      # in the hand it is a real cord (bow_geometry)
    parts = [string]
    style = spec.get("style")
    end = 1.0 if style != "star" else 0.94
    limb = Part(um - L * end, um + L * end, sh.width, spec["wood"], bend=sh.arc, relief="limb",
                kind="scales" if style == "serpent" else "metal")
    parts.append(limb)
    for k, colors in spec.get("bands", ()):
        for side in (-1, 1):
            c = um + side * k * L
            parts.append(Part(c - 0.55, c + 0.55, lambda u: sh.width(u) + 0.12, colors, bend=sh.arc, relief="limb"))
    if style == "gale":
        # white-gold plates above and below the grip, and a wind-blue line let into the back of each limb
        parts.append(Part(um - 5.8, um + 5.8, lambda u: sh.width(u) + 0.3, WHITE_GOLD, bend=sh.arc, relief="limb"))
        for side in (-1, 1):
            a, b = sorted((um + side * 6.2, um + side * 0.8 * L))
            parts.append(Part(a, b, const(0.3), (hexc("e8fffa"), hexc("6ad8c4"), hexc("2a8a78")),
                              bend=lambda u: sh.arc(u) + 0.25 * sh.width(u)))
    if style == "star":
        # the tips are blades of star crystal that curl forward
        for side in (-1, 1):
            a, b = sorted((um + side * 0.9 * L, um + side * 1.14 * L))
            parts.append(Part(a, b, lambda u: sh.thick * 0.62 * max(0.0, 1 - max(0.0, sh.k(u) - 0.97) / 0.17) ** 0.8,
                              STAR_BLUE, bend=sh.arc, relief="gem"))
        parts.append(Part(um - 5.4, um + 5.4, lambda u: sh.width(u) + 0.25, (hexc("fffef0"), hexc("ffd86a"), hexc("b8862a")),
                          bend=sh.arc, relief="limb"))
    elif spec.get("tips"):
        # tip caps; the serpent bow's are the two snakes' heads
        tip_len = 2.6 if style != "serpent" else (1 - SNAKE_NECK) * L
        for side in (-1, 1):
            a, b = sorted((um + side * (L - tip_len), um + side * L))
            parts.append(Part(a, b, lambda u: sh.width(u) + (0.1 if style != "serpent" else 0.0), spec["tips"],
                              bend=sh.arc, relief="limb"))
    parts.append(Part(um - 2.7, um + 2.7, const(sh.thick + 0.45), spec["grip"], kind="grip", bend=sh.arc))
    if style == "serpent":
        # eyes, and a pale belly line
        for side in (-1, 1):
            eu = um + side * (SNAKE_NECK + (1 - SNAKE_NECK) * 0.45) * L
            ev = sh.arc(eu) + 0.4 * sh.width(eu)
            parts.append(Fn(lambda u, v, eu=eu, ev=ev: hexc("fff27a") if math.hypot(u - eu, v - ev) <= 0.5 * px + 0.3 else None))
            parts.append(Fn(lambda u, v, eu=eu, ev=ev: hexc("1a0a10") if math.hypot(u - eu, v - ev) <= 0.3 * px else None))
    if style == "star":
        gem = circle(um, sh.grip_v + 0.2, 1.55, (hexc("ffffff"), hexc("9fd4ff"), hexc("3a6ad0")))
        gem.proud = True
        parts.append(gem)
        parts.append(Fn(lambda u, v: hexc("ffffff") if (abs(u - um) < 0.3 * px and abs(v - sh.grip_v - 0.2) < 2.3)
                        or (abs(v - sh.grip_v - 0.2) < 0.3 * px and abs(u - um) < 2.3) else None))
    if style == "gale":
        gem = circle(um, sh.grip_v + 0.15, 1.25, (hexc("ffffff"), hexc("7ff0dc"), hexc("1e8a78")))
        gem.proud = True
        parts.append(gem)
    if draw > 0:
        parts.extend(arrow_parts(sh.nock[1], spec))
    return parts, sh


ARROW_HEAD, ARROW_BARB, ARROW_HEAD_HALF = 3.9, 1.2, 1.8             # the head's length, its barbs' sweep, half its width
ARROW_VANES = (0.8, 5.4, 1.65)                                    # the feathers: from, to (off the nock), height


def arrow_parts(nock_v, spec):
    """The nocked arrow: along v, pointing to +v (the upper-left of the picture)."""
    px = 32.0 / S
    um = BOW_MID
    head_c = spec.get("head", STEEL)
    shaft_c = spec.get("shaft", WOOD)
    feather_c = spec.get("feather", FEATHER)
    r = 0.6 * px if S < 64 else 0.3                               # the shaft's half width
    v0, v_tip = nock_v, nock_v + ARROW_LEN
    v_head = v_tip - ARROW_HEAD
    fa, fb = v0 + ARROW_VANES[0], v0 + ARROW_VANES[1]             # the feathers

    def vane(v):
        """How far a feather stands off the shaft at v."""
        if not fa <= v <= fb:
            return 0.0
        s = (v - fa) / (fb - fa)
        return ARROW_VANES[2] * (1 - s) ** 0.75 * min(1.0, (s + 0.08) / 0.16)

    def head_half(v):
        if v_head <= v <= v_tip:
            return ARROW_HEAD_HALF * (1 - (v - v_head) / (v_tip - v_head)) ** 0.9
        return 0.0

    def barb(u, v):
        if v_head - ARROW_BARB <= v < v_head:
            s = (v_head - v) / ARROW_BARB
            return 0.5 + 1.0 * s <= abs(u - um) <= ARROW_HEAD_HALF - 0.1 * s
        return False

    def shaft_f(u, v):
        if v0 - 0.5 <= v <= v_head + 0.4 and abs(u - um) <= r:
            if v < v0 + 0.35:
                return shade(shaft_c[2], -0.2)                    # the nock
            return tone3(shaft_c, 0.55 if u > um else -0.45)
        return None

    def head_f(u, v):
        hw = head_half(v)
        if hw > 0 and abs(u - um) <= hw:
            if abs(u - um) < 0.22 * px:
                return tone3(head_c, 0.05)                        # the ridge down the middle
            return tone3(head_c, 0.9 if u > um else -0.35)
        if barb(u, v):
            return tone3(head_c, 0.5 if u > um else -0.7)
        return None

    def feather_f(u, v):
        d = abs(u - um)
        if r < d <= r + vane(v):
            rib = int((v + d * 0.9) * (1.6 / max(px, 0.5))) % 2
            return tone3(feather_c, (0.75 if u > um else 0.1) - 0.7 * rib)
        return None

    def shaft_h(u, v, g):
        if not (v0 - 0.5 - g <= v <= v_head + 0.4) or abs(u - um) > r + g:
            return None
        h = math.sqrt(max(0.0, (r + g) ** 2 - (u - um) ** 2))
        return max(h, 0.25 + 0.75 * vane(v))                      # the third feather stands up off the shaft

    def head_h(u, v, g):
        hw = head_half(min(max(v, v_head), v_tip)) if v_head - g <= v <= v_tip + g else 0.0
        if hw > 0 and abs(u - um) <= hw + g:
            return 0.14 + 0.34 * (1 - abs(u - um) / (hw + g))
        return 0.16 if barb(u, v) else None

    def feather_h(u, v, g):
        return 0.14 if r < abs(u - um) <= r + vane(v) + g and fa - g <= v <= fb + g else None

    arrow = [Fn(feather_f, relief=feather_h), Fn(shaft_f, relief=shaft_h), Fn(head_f, relief=head_h)]
    for p in arrow:
        p.geometry = True                                         # in the hand it is a real arrow (bow_geometry)
    return arrow



def bow_geometry(name, draw=0.0):
    """What a held bow shows as real shapes instead of painted texels (tools/gen_weapon_models.py): the string's
    line and, on a drawn bow, where the arrow's nock is. Design coordinates (u, v)."""
    sh = BowShape(BOW_SPECS[name], draw)
    lo, hi = sh.tips
    return {"string": [lo, sh.nock, hi] if draw > 0 else [lo, hi], "nock": sh.nock if draw > 0 else None,
            "grip": (BOW_MID, sh.grip_v)}


def bow(name, draw=0.0):
    spec = BOW_SPECS[name]
    parts, _shape = bow_parts(spec, draw)
    glow = None
    if spec.get("glow"):
        colour, alpha = spec["glow"].split("/")
        glow = line_glow(colour, int(alpha))
    return render(parts, glow=glow)


def bow_grip(name):
    """Where the hand holds a bow: item model units (16 to the sprite)."""
    x, y = BowShape(BOW_SPECS[name]).grip_point()
    return (round(x, 3), round(y, 3), 8.0)


def bow_set(name):
    """Builders for a bow and its pull frames."""
    out = {name: lambda: bow(name)}
    for i, d in enumerate(DRAWS):
        out[f"{name}_pulling_{i}"] = (lambda d=d: bow(name, d))
    return out


# ------------------------------------------------------------------------------------------------ gauntlets

def gauntlet(palette, claws=None, spikes=False):
    fist = poly_mask([(8, 9), (12, 6), (22, 6), (25, 9), (25, 19), (22, 22), (10, 22), (7, 18)], S)
    thumb = poly_mask([(6, 13), (10, 12), (12, 17), (8, 19), (5, 17)], S)
    knuckles = poly_mask([(9, 7), (24, 7), (24, 11), (9, 11)], S) & fist
    lines = np.zeros((S, S), bool)
    for x in (13, 17, 21):
        lines[7:12, x] = True
    cuff = poly_mask([(9, 21), (23, 21), (25, 29), (7, 29)], S)
    band = poly_mask([(8, 24), (24, 24), (24, 26), (8, 26)], S) & cuff
    parts = [(fist | thumb, "main"), (knuckles, "trim"), (lines & fist, "dark"), (cuff, "accent"), (band, "dark")]
    if claws:
        for x0 in (10, 15, 20):
            parts.append((poly_mask([(x0, 7), (x0 + 3, 7), (x0 + 2.5, 1.5), (x0 + 1, 0.5)], S), "claw"))
    if spikes:
        for x0 in (10, 15, 20):
            parts.append((poly_mask([(x0, 7), (x0 + 3, 7), (x0 + 1.5, 3)], S), "trim"))
    light, mid, dark, trim, accent = palette
    masks = []
    for m, role in parts:
        if role == "claw":
            masks.append((m, "claw"))
        else:
            masks.append((m, role))
    base = paint_icon([(m, r) for m, r in masks if r != "claw"], palette)
    if claws:
        claw_img = paint_icon([(m, "main") for m, r in masks if r == "claw"], (claws[0], claws[1], claws[2], claws[1], claws[1]))
        base = Image.alpha_composite(claw_img, base) if False else Image.alpha_composite(base, claw_img)
    return base


# ------------------------------------------------------------------------------------------------ the arsenal
#
# id: (grade, kind, sprite builder, english name, lore)

def bow_entry(d, grade, name, title, lore):
    for k, build in bow_set(name).items():
        d[k] = (grade, "bow", build, title if k == name else None, lore if k == name else None)


def designs():
    d = {}

    # ---- common
    d["lycan_fang_knife"] = ("common", "dagger", lambda: render([
        pommel(4.5, 1.6, BONE), grip(5, 12, LEATHER), guard(12.8, 3.0, DARK_STEEL, 1.1),
        blade(13.5, 29.5, 2.5, 9, BONE, bend=lambda u: -0.02 * max(0, u - 20) ** 2),
        blade(15, 27, 0.6, 5, (BLOOD[0], BLOOD[1], BLOOD[1]), bend=lambda u: -0.02 * max(0, u - 20) ** 2 - 1.6, fuller=False)]),
        "Lycan Fang Knife", "A fang pried from a steel-furred wolf in an E-rank gate. It still wants to bite.")
    d["goblin_shiv"] = ("common", "dagger", lambda: render([
        grip(5, 11.5, RAGS, 1.5), Part(11.5, 12.5, const(2.2), RUST),
        serrated(12, 27.5, 2.2, 6, RUST), blade(14, 25, 0.5, 4, (VENOM[0], VENOM[1], VENOM[1]), fuller=False)],
        glow=line_glow("7ccf3a", 55)),
        "Goblin Shiv", "Crude, notched and smeared with something green. Goblins never wash their blades.")
    d["apprentice_staff"] = ("common", "staff", lambda: render([
        shaft(0, 34, WOOD), grip(6, 12, LEATHER, 1.3), Part(32.5, 34.5, const(1.8), GOLD),
        circle(37, 0, 2.7, BLUE_ORB)], glow=line_glow("5ab0ff", 45)),
        "Apprentice's Staff", "Issued to every new mage at the Association. The orb hums when mana is near.")
    d["acolyte_mace"] = ("common", "mace", lambda: render([
        pommel(2.5, 1.5, GOLD), shaft(2.5, 29, WOOD), grip(5, 12, LEATHER, 1.3),
        flanged(32, 3.9, SILVER), pommel(36.6, 1.2, WHITE_GOLD)]),
        "Acolyte's Mace", "A healer's first weapon: heavy enough to end a fight, blessed enough to mend after it.")
    d["ironclad_warhammer"] = ("common", "hammer", lambda: render([
        shaft(0, 27, DARK_STEEL, 1.1), grip(3, 12, LEATHER, 1.4),
        Part(23, 30.5, const(5.4), STEEL), Part(24, 29.5, const(4.4), DARK_STEEL, fuller=True),
        Part(30.5, 34.5, lambda u: 1.6 * (34.5 - u) / 4, STEEL)]),
        "Ironclad Warhammer", "Plain iron, honest weight. Tanks swear by it for their first raids.")
    bow_entry(d, "common", "hunters_shortbow", "Hunter's Shortbow",
              "Light, quick and forgiving. Every ranger starts somewhere.")

    # ---- rare
    d["knightsbane"] = ("rare", "dagger", lambda: render([
        pommel(4.5, 1.7, OBSIDIAN), grip(5, 12, (hexc("4a3a3a"), hexc("2a2026"), hexc("120a0e"))),
        guard(12.8, 4.4, OBSIDIAN, 1.1), serrated(13.5, 30.5, 2.6, 8, OBSIDIAN, teeth=2.0, depth=0.5),
        blade(14, 29.5, 0.6, 6, (SILVER[0], SILVER[0], SILVER[1]), bend=lambda u: 1.6, fuller=False)],
        glow=line_glow("8a94a6", 50)),
        "Knightsbane", "A black blade forged to slip between plates. The heavier the armor, the deeper it goes.")
    d["frostwarden_dagger"] = ("rare", "dagger", lambda: render([
        pommel(4.5, 1.7, ICE), grip(5, 12, FROST_STEEL), guard(12.8, 3.6, SILVER, 1.1),
        blade(13.5, 31, 2.5, 9, ICE, bend=lambda u: 0.012 * (u - 13.5) ** 2)], glow=line_glow("9ed8ff", 80)),
        "Frostwarden's Dagger", "Taken from the queen of a frozen gate's elves. Frost creeps up whatever it cuts.")
    d["warchief_cleaver"] = ("rare", "heavy_axe", lambda: render([
        shaft(0, 35, DARK_WOOD, 1.1), grip(3, 12, LEATHER, 1.4), Part(33, 36, lambda u: 1.4 * (36 - u) / 3, RUST),
        axe(19, 32, 8.0, (hexc("e0d4c8"), hexc("9a8c80"), hexc("4e4440")), side=1), axe(21, 30, 1.8, (BLOOD[0], BLOOD[1], BLOOD[2]), side=1, inner=7.0)],
        glow=line_glow("c0162a", 45)),
        "Warchief's Cleaver", "The high orc chieftain swung it one-handed. Mere humans need both.")
    d["silvermoon_rapier"] = ("rare", "rapier", lambda: render([
        pommel(3.5, 1.7, MOON), grip(4, 10.5, (hexc("5a6a9a"), hexc("3a4670"), hexc("1e2440"))),
        Fn(lambda u, v: SILVER[1] if 10.5 <= u <= 14 and 2.6 <= abs(v - (u - 10.5) * 0.5) <= 3.5 else None),
        guard(11.5, 3.4, SILVER, 0.9), blade(12.5, 40.5, 1.1, 5, SILVER, fuller=False)],
        glow=line_glow("bcd2f0", 50)),
        "Silvermoon Rapier", "A duelist's needle that drinks a little of every wound it opens.")
    d["naga_glaive"] = ("rare", "glaive", lambda: render([
        shaft(0, 27, (hexc("4a7a6a"), hexc("2e5246"), hexc("16302a"))), grip(4, 11, LEATHER, 1.3),
        Part(25.5, 28, const(2.0), GOLD),
        blade(27, 41, 2.8, 7, NAGA, bend=lambda u: 0.035 * (u - 27) ** 2)], glow=line_glow("7ccf3a", 60)),
        "Naga Glaive", "Carved from the fang of a swamp serpent-queen. The venom never quite dried.")
    d["winter_breath_staff"] = ("rare", "staff", lambda: render([
        shaft(0, 32, PALE_WOOD), grip(6, 12, FROST_STEEL, 1.3), Part(30.5, 32.5, const(2.2), SILVER),
        diamond(31, 41, 3.2, ICE)], glow=line_glow("9ed8ff", 70)),
        "Staff of Winter Breath", "The crystal at its tip never melts, even in a fire gate.")
    bow_entry(d, "rare", "windrunner_longbow", "Windrunner Longbow",
              "Strung with wind-spider silk. Arrows from it barely seem to fall.")
    d["mending_scepter"] = ("rare", "mace", lambda: render([
        pommel(2.5, 1.6, WHITE_GOLD), shaft(2.5, 29, GOLD), grip(5, 12, (hexc("fff6e0"), hexc("e8d8b0"), hexc("a89870")), 1.3),
        guard(28.5, 4.4, WHITE_GOLD, 0.9), circle(33.5, 0, 3.0, (hexc("ffffff"), hexc("fff0b0"), hexc("d8b050")))],
        glow=line_glow("fff0a8", 60)),
        "Scepter of Mending", "Every blow it lands sends a little light back to those who stand with you.")

    # ---- epic
    d["thunderfang_longsword"] = ("epic", "sword", lambda: render([
        pommel(3.5, 1.9, BOLT), grip(4, 10.5, NAVY), guard(11.5, 5.2, GOLD, 1.3),
        Fn(lambda u, v: GOLD[1] if 11 <= u <= 15 and 4.0 <= abs(v) <= 5.0 + (u - 11) * 0.4 and (u - 11) < 3 else None),
        blade(12.5, 39, 2.5, 6, STORM, fuller=False),
        Part(13.5, 35, const(0.55), BOLT, bend=lambda u: 0.9 * (1 if int(u / 2.5) % 2 else -1) * ((u / 2.5) % 1 - 0.5) * 2)],
        glow=line_glow("b8e2ff", 85)),
        "Thunderfang Longsword", "Its fuller is a captured bolt. Strike hard enough and the storm jumps free.")
    d["crimson_commander_blade"] = ("epic", "claymore", lambda: render([
        pommel(2.2, 2.0, CRIMSON), grip(2.5, 11, (hexc("3a1420"), hexc("240a14"), hexc("10040a"))),
        guard(12, 7.0, (hexc("8a2a3a"), hexc("5a1224"), hexc("2a0610")), 1.7),
        blade(13, 40.5, 3.5, 8, (hexc("5a4a56"), hexc("2e2430"), hexc("140e16"))),
        blade(14, 39, 1.0, 6, CRIMSON, fuller=False)], glow=line_glow("e0306e", 80)),
        "Crimson Commander's Blade", "The sword of a knight who served a throne of shadow. Red light still runs along its edge.")
    d["swarm_king_talons"] = ("epic", "claw", lambda: render([
        grip(4, 12, (hexc("5a3a6a"), hexc("3a2448"), hexc("1a0e24")), 1.5),
        Part(12, 16, const(4.5), (hexc("6a4a8a"), hexc("3e2a58"), hexc("1e1230"))),
        blade(15, 33, 1.1, 6, VIOLET, bend=lambda u: 2.8 - 0.03 * (u - 15) ** 2, fuller=False, relief="round"),
        blade(15, 37, 1.2, 6, VIOLET, bend=lambda u: -0.004 * (u - 15) ** 2, fuller=False, relief="round"),
        blade(15, 33, 1.1, 6, VIOLET, bend=lambda u: -2.8 - 0.03 * (u - 15) ** 2, fuller=False, relief="round")],
        glow=line_glow("7ccf3a", 70)),
        "Swarm King's Talons", "The claws of an insect king that ate its way through an island. They finish what they start.")
    d["frostbear_gauntlets"] = ("epic", "fist", lambda: gauntlet(
        (hexc("ffffff"), hexc("dceaf4"), hexc("8aa2b8"), hexc("9ed8ff"), hexc("f4f8ff")), claws=ICE),
        "Frostbear Gauntlets", "Fur and claws of a giant ice bear. Punches leave frost behind.")
    d["dawnpetal_rapier"] = ("epic", "rapier", lambda: render([
        pommel(3.5, 1.8, ROSE), grip(4, 10.5, (hexc("fff6fa"), hexc("f0d0e0"), hexc("b88aa0"))),
        Fn(lambda u, v: GOLD[0] if 10 <= u <= 14 and 2.5 <= math.hypot(u - 12, v) <= 3.4 else None),
        guard(11.5, 2.2, GOLD, 0.9), blade(12.5, 40.5, 1.3, 6, (hexc("ffffff"), hexc("ffe0ec"), hexc("e090b4")), fuller=False)],
        glow=line_glow("ffb8d4", 80)),
        "Dawnpetal Rapier", "Light as a falling petal. Its dance builds, step by step, to one perfect cut.")
    d["ember_sage_staff"] = ("epic", "staff", lambda: render([
        shaft(0, 31, OBSIDIAN), grip(6, 12, GOLD, 1.3),
        Fn(lambda u, v: GOLD[1] if 14 <= u <= 30 and abs(v - 1.1 * math.sin(u * 1.3)) < 0.5 else None),
        guard(30.5, 2.6, GOLD, 0.8),
        Part(31, 41, lambda u: 2.8 * math.sin(math.pi * (u - 31) / 10) ** 0.7, MAGMA,
             bend=lambda u: 0.6 * math.sin((u - 31) * 0.9))], glow=line_glow("ff7a2e", 90)),
        "Ember Sage's Staff", "A sage of the fire gates poured his last flame into it.")
    d["lifebloom_staff"] = ("epic", "staff", lambda: render([
        shaft(0, 33, LIVING_WOOD),
        Fn(lambda u, v: LEAF[1] if u in (8.5, 16.5, 24.5) or (abs(u % 8 - 0.5) < 1.2 and 1.0 < v < 2.6 and u < 30) else None),
        circle(36, 0, 3.4, ROSE), circle(36, 0, 1.5, WHITE_GOLD)], glow=line_glow("8ce05a", 70)),
        "Lifebloom Staff", "A sapling that never stopped growing. Its flower opens wider the more it heals.")
    bow_entry(d, "epic", "serpentsting_bow", "Serpentsting Bow",
              "Its limbs are two coiled snakes. Every arrow it looses carries their venom.")

    # ---- legendary
    d["stormlord_longsword"] = ("legendary", "sword", lambda: render([
        pommel(3.5, 2.0, BOLT), grip(4, 10.5, BLACK_IRON), guard(11.5, 6.0, (hexc("2a2a34"), hexc("16161c"), hexc("060608")), 1.5),
        Fn(lambda u, v: GOLD[0] if 10.5 <= u <= 12.5 and 4.6 <= abs(v) <= 6.0 else None),
        blade(12.5, 40, 2.8, 6, BLACK_IRON, fuller=False),
        Part(13, 37, const(0.9), BOLT, bend=lambda u: 1.0 * math.sin(u * 1.1)),
        Part(13, 36, const(0.45), (hexc("ffffff"),) * 3, bend=lambda u: 1.0 * math.sin(u * 1.1))],
        glow=line_glow("b8e2ff", 110)),
        "Stormlord's Longsword", "Once carried by the lord of a tower of demons. Lightning answers its every swing.")
    d["titans_knuckles"] = ("legendary", "fist", lambda: gauntlet(
        (hexc("ffd8a0"), hexc("c08a4a"), hexc("6e4420"), hexc("ffe9a0"), hexc("5a3a20")), spikes=True),
        "Titan's Knuckles", "Made for a giant of a man who held the line alone. Blows bounce off whoever wears them.")
    d["hellhound_halberd"] = ("legendary", "halberd", lambda: render([
        shaft(0, 37, OBSIDIAN, 1.1), grip(3, 12, (hexc("8a2a1a"), hexc("5a1408"), hexc("2a0804")), 1.4),
        axe(21, 31, 7.0, MAGMA, side=1), axe(23, 29, 3.2, OBSIDIAN, side=-1),
        Part(34, 41, lambda u: 1.8 * (41 - u) / 7, MAGMA)], glow=line_glow("ff7a2e", 110)),
        "Hellhound Gatekeeper's Halberd", "The three-headed guardian of a demon tower fell. Its fire stayed in the steel.")
    d["starseer_staff"] = ("legendary", "staff", lambda: render([
        shaft(0, 32, NAVY), grip(6, 12, WHITE_GOLD, 1.3),
        Fn(lambda u, v: WHITE_GOLD[1] if 31 <= u <= 41 and 3.2 <= math.hypot(u - 36, v) <= 4.2 and (v - (u - 36)) > -2 else None),
        circle(36, 0, 2.3, (hexc("ffffff"), hexc("fff6c8"), hexc("c8b0ff"))),
        Fn(lambda u, v: hexc("ffffff") if (abs(u - 36) < 0.5 and abs(v) < 3.6) or (abs(v) < 0.5 and abs(u - 36) < 3.6) else None)],
        glow=line_glow("fff0a8", 110)),
        "Starseer's Staff", "It shows its bearer where the stars will fall, and lets them choose.")
    d["saints_sunstaff"] = ("legendary", "staff", lambda: render([
        shaft(0, 32, WHITE_GOLD), grip(6, 12, (hexc("ffffff"), hexc("f4ecd8"), hexc("c8b890")), 1.3),
        Fn(lambda u, v: WHITE_GOLD[2] if 31 <= u <= 41 and 3.0 <= math.hypot(u - 36, v) <= 4.9
           and (math.atan2(v, u - 36) * 8 / math.pi) % 2 < 0.9 else None),
        circle(36, 0, 2.9, (hexc("ffffff"), hexc("ffe9a0"), hexc("ffb02e")))], glow=line_glow("ffe9a0", 120)),
        "Saint's Sunstaff", "Carried by a healer who walked out of an S-rank gate with every one of her party alive.")
    bow_entry(d, "legendary", "galebreaker_warbow", "Galebreaker Warbow",
              "Draw it fully and the wind itself nocks two more arrows beside yours.")
    d["nightveil_kris"] = ("legendary", "dagger", lambda: render([
        pommel(4.5, 1.8, VIOLET), grip(5, 12, OBSIDIAN), guard(12.8, 3.4, VIOLET, 1.1),
        blade(13.5, 31, 2.3, 7, OBSIDIAN, bend=lambda u: 1.0 * math.sin((u - 13.5) * 0.7), fuller=False),
        blade(14, 30, 0.5, 5, (VIOLET[0], VIOLET[1], VIOLET[1]), bend=lambda u: 1.0 * math.sin((u - 13.5) * 0.7) + 1.4, fuller=False)],
        glow=line_glow("9b7bff", 110)),
        "Nightveil Kris", "A shadow-forged kris. It strikes deepest at the backs of those who never saw it coming.")
    d["ironblood_claymore"] = ("legendary", "claymore", lambda: render([
        pommel(2.2, 2.1, BLOOD), grip(2.5, 11, BLACK_IRON), guard(12, 7.2, BLACK_IRON, 1.8),
        blade(13, 40.5, 3.8, 8, BLACK_IRON),
        Fn(lambda u, v: BLOOD[1] if 15 <= u <= 36 and abs(v) < 0.6 and int(u) % 4 != 0 else None)],
        glow=line_glow("d3172a", 110)),
        "Ironblood Claymore", "Runes of blood run down its spine. The more it takes, the more its wielder keeps.")

    # ---- mythic
    d["hellsovereign_fang"] = ("mythic", "dagger", lambda: render([
        pommel(4.5, 1.9, MAGMA), grip(5, 12, OBSIDIAN),
        Fn(lambda u, v: OBSIDIAN[0] if 11.5 <= u <= 14.5 and 2.5 <= abs(v) <= 3.2 + (u - 11.5) * 0.9 else None),
        guard(12.8, 3.6, OBSIDIAN, 1.2),
        Part(13.5, 32, lambda u: 2.8 * (1 - ((u - 13.5) / 18.5) ** 1.8) + 0.6 * math.sin(u * 1.4), MAGMA, bend=lambda u: 0.4 * math.sin(u),
             relief="blade"),
        blade(14.5, 28, 0.7, 5, (hexc("ffffff"), hexc("fff0a0"), hexc("fff0a0")), fuller=False)],
        glow=line_glow("ff5a1e", 140)),
        "Hellsovereign's Fang", "One of a pair, forged in a sovereign's hellfire. Two in hand burn twice as bright.")
    d["winterborne_requiem"] = ("mythic", "spear", lambda: render([
        shaft(0, 31, (hexc("ffffff"), hexc("d4e8f8"), hexc("8aaac8")), 1.1), grip(12, 18, NAVY, 1.4),
        Part(29.5, 31.5, const(2.4), SILVER),
        Fn(lambda u, v: ICE[1] if 30 <= u <= 35 and 2.0 <= abs(v) <= 2.0 + (35 - u) * 0.7 else None),
        diamond(30.5, 42, 3.0, ICE, peak=0.3), Part(31.5, 40, const(0.5), (hexc("ffffff"),) * 3)],
        glow=line_glow("cff2ff", 140)),
        "Winterborne Requiem", "A lance of eternal ice from a frozen sovereign's court. Its song is the silence after a blizzard.")
    d["stormcaller_edict"] = ("mythic", "hammer", lambda: render([
        shaft(0, 27, BLACK_IRON, 1.2), grip(3, 12, NAVY, 1.5), Part(21.5, 23, const(2.4), GOLD),
        Part(22.5, 32, const(6.0), STORM), Part(23.5, 31, const(5.0), (hexc("4a5a8a"), hexc("2a3460"), hexc("12183a"))),
        Part(26.5, 28, const(5.8), GOLD),
        Fn(lambda u, v: BOLT[0] if 24 <= u <= 30.5 and abs(v - 1.6 * math.sin(u * 2.2)) < 0.5 and abs(v) < 4.8 else None),
        Part(32, 35, lambda u: 1.5 * (35 - u) / 3, STORM)],
        glow=line_glow("b8e2ff", 150)),
        "Stormcaller's Edict", "Its verdict is lightning, and there is no appeal.")

    # ---- divine
    d["ruinbringer"] = ("divine", "claymore", lambda: render([
        pommel(2.2, 2.3, MAGMA), grip(2.5, 11, (hexc("3a2a20"), hexc("1e140e"), hexc("0a0604"))),
        Fn(lambda u, v: OBSIDIAN[0] if 10.5 <= u <= 14 and 4.0 <= abs(v) <= 8.0 - (u - 10.5) else None),
        guard(12, 7.6, OBSIDIAN, 1.8),
        blade(13, 41, 4.0, 9, (hexc("4a4048"), hexc("221a20"), hexc("0c080a")), fuller=False),
        Fn(lambda u, v: MAGMA[(int(u * 3) % 2)] if 14 <= u <= 37 and abs(v - 1.6 * math.sin(u * 0.9)) < 0.45 else None),
        Fn(lambda u, v: MAGMA[1] if 16 <= u <= 34 and abs(v + 1.8 * math.sin(u * 0.6 + 1)) < 0.4 and int(u) % 3 else None)],
        glow=line_glow("ff7a2e", 170)),
        "Ruinbringer", "A greatsword from the age of the first sovereigns. It does not cut so much as end things.")
    d["calamity_fang"] = ("divine", "dagger", lambda: render([
        pommel(4.5, 2.0, CRIMSON), grip(5, 12, (hexc("7a2a3a"), hexc("4a1222"), hexc("22060e"))),
        Fn(lambda u, v: CRIMSON[1] if 11.5 <= u <= 14.5 and 2.6 <= abs(v) <= 3.0 + (u - 11.5) * 1.1 and v > 0 else None),
        guard(12.8, 3.4, (hexc("ff8ab4"), hexc("8a1a3e"), hexc("3a0818")), 1.2),
        blade(13.5, 32, 3.0, 13, BONE, bend=lambda u: -0.016 * (u - 13.5) ** 2),
        blade(15, 30, 0.7, 7, CRIMSON, bend=lambda u: -0.016 * (u - 13.5) ** 2 - 2.0, fuller=False)],
        glow=line_glow("e0306e", 170)),
        "Calamity Fang", "Carved from the fang of the dragon that was calamity itself. It remembers how to burn a city.")
    d["eye_of_the_insatiable"] = ("divine", "staff", lambda: render([
        shaft(0, 31, OBSIDIAN, 1.1), grip(6, 12, VIOLET, 1.3),
        Fn(lambda u, v: OBSIDIAN[0] if 29 <= u <= 39 and 3.3 <= math.hypot(u - 35, v) <= 4.4 and abs(v) > 1.2 else None),
        circle(35, 0, 3.1, EYE),
        Fn(lambda u, v: hexc("10001c") if abs(u - 35) < 2.0 and abs(v) < 0.6 else None),
        Fn(lambda u, v: hexc("ffffff") if abs(u - 36) < 0.6 and abs(v - 1.1) < 0.6 else None)],
        glow=line_glow("c050ff", 170)),
        "Eye of the Insatiable", "A sovereign's eye that never stopped hungering. It feeds its mage everything it devours.")
    d["colossus_aegis"] = ("divine", "hammer", lambda: render([
        shaft(0, 25, STONE, 1.3), grip(3, 12, BRONZE, 1.6), Part(20, 22, const(3.0), WHITE_GOLD),
        Part(21, 32, const(6.6), STONE), Part(22, 31, const(5.6), (hexc("c8c0aa"), hexc("8a8470"), hexc("4a4636"))),
        Fn(lambda u, v: WHITE_GOLD[1] if 21 <= u <= 32 and abs(v) >= 5.8 and abs(v) <= 6.6 else None),
        diamond(23.5, 29.5, 2.4, WHITE_GOLD, peak=0.5), Part(32, 35.5, lambda u: 2.2 * (35.5 - u) / 3.5, WHITE_GOLD)], glow=line_glow("ffc85a", 170)),
        "Colossus Aegis", "Broken from the hand of the stone colossus that guarded the first gate. Nothing moves it.")
    d["world_tree_branch"] = ("divine", "staff", lambda: render([
        Part(0, 34, const(1.2), LIVING_WOOD, bend=lambda u: 0.7 * math.sin(u * 0.45)),
        Fn(lambda u, v: LEAF[0 if v > 1.5 else 1] if (u % 7 < 2.5 and 1.0 < v - 0.7 * math.sin(u * 0.45) < 3.2 and 6 < u < 32) else None),
        Fn(lambda u, v: LEAF[1] if (u % 9 < 2 and -3.0 < v - 0.7 * math.sin(u * 0.45) < -1.0 and 10 < u < 32) else None),
        Fn(lambda u, v: LIVING_WOOD[1] if 32 <= u <= 40 and 2.6 <= math.hypot(u - 36, v) <= 3.6 else None),
        circle(36, 0, 2.0, (hexc("ffffff"), hexc("d8ffb0"), hexc("6ac03a")))], glow=line_glow("8ce05a", 170)),
        "Branch of the World Tree", "Every healer has heard the story. Few believe the branch is real.")
    bow_entry(d, "divine", "starfall_longbow", "Starfall Longbow",
              "Its string was spun from a falling star. Loose it fully and the sky follows the arrow down.")
    return d


# ------------------------------------------------------------------------------------------------ shimmer

def shimmer_strip(img, grade):
    """A light sweep along the blade, then a rest: 16 frames stacked vertically."""
    a = np.array(img).astype(float)
    solid = a[:, :, 3] > 0
    frames = []
    accent = {"legendary": (255, 236, 170), "mythic": (255, 170, 120), "divine": (255, 255, 255)}[grade]
    for f in range(16):
        out = a.copy()
        if f < 10:
            pos = -8 + f * 5.5
            for y in range(S):
                for x in range(S):
                    if not solid[y, x] or out[y, x, 3] < 255:
                        continue
                    u, v = uv(x, y)
                    k = max(0.0, 1 - abs((u + v * 0.3) - pos) / 2.6)
                    if k > 0:
                        out[y, x, :3] = out[y, x, :3] * (1 - 0.65 * k) + np.array(accent) * 0.65 * k
        if grade == "divine" and f in (11, 12):
            ys, xs = np.nonzero(solid & (a[:, :, 3] == 255))
            if len(ys):
                i = (f * 37) % len(ys)
                out[ys[i], xs[i], :3] = 255
        frames.append(out.astype(np.uint8))
    return Image.fromarray(np.vstack(frames))


# ------------------------------------------------------------------------------------------------ extras

def beam(w=32, h=128):
    """A soft vertical light column: brightest in the middle and at the bottom."""
    xx = np.linspace(-1, 1, w)[None, :]
    yy = np.linspace(0, 1, h)[:, None]  # 0 = top
    across = np.clip(1 - np.abs(xx) ** 1.6, 0, 1)
    along = yy ** 1.5
    a = across * along
    img = np.dstack([np.full((h, w), 255.0)] * 3 + [a * 255]).astype(np.uint8)
    return Image.fromarray(img, "RGBA").filter(ImageFilter.GaussianBlur(0.6))


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    for p in (ITEM_OUT, ATTR_OUT, EFFECT_ICON_OUT, FX_OUT):
        os.makedirs(p, exist_ok=True)
    d = designs()
    names = {}
    for item, (grade, kind, build, name, lore) in d.items():
        img = build()
        tex = os.path.join(ITEM_OUT, item + ".png")
        meta = tex + ".mcmeta"
        animate = grade in ("legendary", "mythic", "divine") and "_pulling_" not in item
        if animate:
            shimmer_strip(img, grade).save(tex)
            write_json(meta, {"animation": {"frametime": 2 if grade != "legendary" else 3, "interpolate": False}})
        else:
            img.save(tex)
            if os.path.exists(meta):
                os.remove(meta)
        if name is not None:
            names[item] = (name, lore)
        if kind != "bow":
            preset = {"staff": "battlestaff"}.get(kind, kind)
            path = os.path.join(ATTR_OUT, item + ".json")
            attrs = {}
            if os.path.exists(path):
                with open(path) as f:
                    attrs = json.load(f)          # keeps the idle pose gen_weapon_models.py adds
            attrs["parent"] = "bettercombat:" + preset
            write_json(path, attrs)

    # status icons
    for name, (svg, dark, light) in {"frostbite": ("snowflake", "3f86c0", "e8f6ff"), "frozen": ("cube", "5a9ad0", "ffffff"),
                                     "infernal_rend": ("fire", "c0300a", "ffe08a")}.items():
        gc.effect_icon(svg, dark, light).save(os.path.join(EFFECT_ICON_OUT, name + ".png"))
    beam().save(os.path.join(FX_OUT, "loot_beam.png"), optimize=True)
    write_json(os.path.join(FX_OUT, "loot_beam.png.mcmeta"), {"texture": {"blur": True, "clamp": True}})

    # names and lore into the language file
    with open(LANG) as f:
        lang = json.load(f)
    for item, (name, lore) in names.items():
        lang["item.beyondsolo." + item] = name
        lang["item.beyondsolo." + item + ".lore"] = lore
    with open(LANG, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    # held and dropped 3D models (tools/gen_hd_gear.py rebuilds them again for its 64x64 textures)
    import gen_weapon_models
    gen_weapon_models.build(set(names) | set(gen_weapon_models.BOWS))
    print(f"arsenal assets written: {len(names)} weapons")


if __name__ == "__main__":
    main()
