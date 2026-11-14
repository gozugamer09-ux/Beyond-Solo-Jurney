#!/usr/bin/env python3
"""
Generates the combat module's textures (no hand-drawn files needed):

- weapon sprites (32x32 pixel art, built from parts along the item's diagonal)
- armor item icons and the armor model layers (64x32, per the vanilla humanoid UV layout)
- remedy bottles
- status effect icons (18x18, from Phosphor icons in tools/icons-src)
- light textures for world effects (brand sigil, burst)

Run from the repo root:  python3 tools/gen_combat_assets.py
Needs: pillow, numpy, cairosvg.
"""
import io
import math
import os

import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
ITEM_OUT = os.path.join(ASSETS, "textures/item")
ARMOR_OUT = os.path.join(ASSETS, "textures/models/armor")
EFFECT_ICON_OUT = os.path.join(ASSETS, "textures/mob_effect")
FX_OUT = os.path.join(ASSETS, "textures/effect")
ICON_SRC = os.path.join(os.path.dirname(__file__), "icons-src")


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    """f > 0 lightens towards white, f < 0 darkens."""
    r, g, b, a = c
    if f >= 0:
        return (round(r + (255 - r) * f), round(g + (255 - g) * f), round(b + (255 - b) * f), a)
    f = -f
    return (round(r * (1 - f)), round(g * (1 - f)), round(b * (1 - f)), a)


def outline(img, color=(20, 14, 24, 255)):
    """Adds a 1px dark outline around the opaque pixels (4-neighbourhood)."""
    a = np.array(img)
    solid = a[:, :, 3] > 0
    grown = solid.copy()
    grown[1:, :] |= solid[:-1, :]
    grown[:-1, :] |= solid[1:, :]
    grown[:, 1:] |= solid[:, :-1]
    grown[:, :-1] |= solid[:, 1:]
    ring = grown & ~solid
    a[ring] = color
    return Image.fromarray(a)


# --------------------------------------------------------------------------------------------- weapons
#
# A weapon lies on the diagonal from the bottom-left (pommel) to the top-right (tip). Each part covers a range
# along that axis (u, in pixels) and a half width w(u) across it (v). Shading: the upper-left side of a blade is
# lit, the lower-right side is in shadow, and a darker fuller line runs down the middle.

S = 32
ORIGIN = (3.0, 3.0)
DIAG = 1 / math.sqrt(2)


def uv(x, y):
    px = x + 0.5 - ORIGIN[0]
    py = (S - 1 - y) + 0.5 - ORIGIN[1]
    u = (px + py) * DIAG
    v = (py - px) * DIAG  # positive = upper-left side
    return u, v


class Part:
    def __init__(self, u0, u1, width, colors, kind="metal", bend=None, fuller=False, bands=0):
        self.u0, self.u1 = u0, u1
        self.width = width          # function u -> half width
        self.colors = colors        # (light, mid, dark)
        self.kind = kind
        self.bend = bend or (lambda u: 0.0)
        self.fuller = fuller
        self.bands = bands

    def color_at(self, u, v):
        if u < self.u0 or u > self.u1:
            return None
        w = self.width(u)
        v = v - self.bend(u)
        if w <= 0 or abs(v) > w:
            return None
        light, mid, dark = self.colors
        t = v / w
        if self.kind == "grip":
            band = int((u - self.u0) / 1.6) % 2
            c = mid if band == 0 else dark
            return shade(c, 0.25) if t > 0.3 else c
        if self.fuller and abs(t) < 0.22 and u < self.u1 - 3:
            return shade(mid, -0.18)
        if t > 0.45:
            return light
        if t < -0.35:
            return dark
        return mid


def blade(u0, u1, base, tip_len, colors, bend=None, fuller=True):
    def width(u):
        if u > u1 - tip_len:
            return base * max(0.0, (u1 - u) / tip_len)
        return base
    return Part(u0, u1, width, colors, bend=bend, fuller=fuller)


def const(w):
    return lambda u: w


def render_weapon(parts, glow=None):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            u, v = uv(x, y)
            for p in parts:  # later parts draw on top
                c = p.color_at(u, v)
                if c is not None:
                    px[x, y] = c
    img = outline(img)
    if glow:
        # a faint coloured rim for the elemental weapons
        a = np.array(img)
        solid = a[:, :, 3] > 0
        grown = solid.copy()
        grown[1:, :] |= solid[:-1, :]
        grown[:-1, :] |= solid[1:, :]
        grown[:, 1:] |= solid[:, :-1]
        grown[:, :-1] |= solid[:, 1:]
        rim = grown & ~solid
        a[rim] = glow
        img = Image.fromarray(a)
    return img


STEEL = (hexc("eef3f7"), hexc("b9c4cf"), hexc("6f7d8c"))
DARK_STEEL = (hexc("9aa6b4"), hexc("5c6676"), hexc("323848"))
LEATHER = (hexc("a7744a"), hexc("6e4428"), hexc("442818"))
WOOD = (hexc("b98a56"), hexc("8a5f36"), hexc("5a3a20"))
GOLD = (hexc("ffe9a0"), hexc("e0b44c"), hexc("9a7422"))
EMBER = (hexc("ffd27a"), hexc("ff7a2e"), hexc("b8360e"))
VENOM = (hexc("d6ff9a"), hexc("7ccf3a"), hexc("3f7a18"))
CRIMSON = (hexc("ff8ab4"), hexc("d02a68"), hexc("6e0c32"))
BLOOD = (hexc("ff6a6a"), hexc("c0162a"), hexc("5e0812"))
DUSK = (hexc("8c86b4"), hexc("4a4470"), hexc("221e38"))


def guard(u, half, colors, thick=1.6):
    return Part(u - thick, u + thick, const(half), colors)


def pommel(u, r, colors):
    return Part(u - r, u + r, lambda x: math.sqrt(max(0.0, r * r - (x - u) ** 2)) + 0.2, colors)


def grip(u0, u1, colors, w=1.3):
    return Part(u0, u1, const(w), colors, kind="grip")


def weapons():
    out = {}
    # Hunter's Dagger: short, wide blade, leather grip
    out["hunter_dagger"] = render_weapon([
        pommel(4.5, 1.8, DARK_STEEL), grip(5, 12, LEATHER), guard(12.8, 4.2, DARK_STEEL, 1.2),
        blade(13.5, 30, 2.6, 7, STEEL)])
    # Knight's Blade: classic arming sword with a gold cross guard
    out["knight_sword"] = render_weapon([
        pommel(3.5, 1.9, GOLD), grip(4, 10.5, LEATHER), guard(11.5, 5.5, GOLD, 1.3),
        blade(12.5, 38.5, 2.3, 6, STEEL)])
    # Steel Greatsword: long and broad, two-hand grip
    out["steel_greatsword"] = render_weapon([
        pommel(2.2, 2.0, DARK_STEEL), grip(2.5, 11, LEATHER), guard(12, 6.5, DARK_STEEL, 1.6),
        blade(13, 40, 3.4, 7, STEEL)])
    # Hunting Spear: wooden shaft with a leaf head
    out["hunting_spear"] = render_weapon([
        Part(0, 30, const(1.0), WOOD), Part(14, 16, const(1.6), LEATHER, kind="grip"),
        Part(29, 31, const(1.7), DARK_STEEL),
        Part(30, 41, lambda u: 2.8 * math.sin(math.pi * min(1.0, (u - 30) / 11) ** 0.8), STEEL, fuller=True)])
    # Emberbrand: dark sword with a glowing ember edge
    out["emberbrand"] = render_weapon([
        pommel(3.5, 1.9, EMBER), grip(4, 10.5, DUSK), guard(11.5, 5.0, DUSK, 1.4),
        blade(12.5, 38.5, 2.6, 6, (EMBER[0], EMBER[1], hexc("7a1e08"))),
        blade(13.5, 36, 1.1, 5, (hexc("fff3c4"), hexc("ffb347"), hexc("ffb347")), fuller=False)],
        glow=(255, 122, 46, 70))
    # Venom Fang: curved green-tinted dagger
    out["venom_fang"] = render_weapon([
        pommel(4.5, 1.7, VENOM), grip(5, 12, DUSK), guard(12.8, 3.8, VENOM, 1.1),
        blade(13.5, 30, 2.4, 9, (hexc("e9ffd0"), hexc("9fd86a"), hexc("3f7a18")), bend=lambda u: -0.012 * (u - 13.5) ** 2)],
        glow=(124, 207, 58, 70))
    # Bloodletter: thin curved blade, red edge
    out["bloodletter"] = render_weapon([
        pommel(3, 1.5, BLOOD), grip(3.5, 11.5, (hexc("3a2a30"), hexc("2a1c22"), hexc("120a0e"))), guard(12.2, 2.6, GOLD, 1.0),
        blade(13, 40, 1.9, 6, (hexc("ffffff"), hexc("d8dde4"), BLOOD[1]), bend=lambda u: 0.012 * (u - 13) ** 2, fuller=False),
        blade(14, 38, 0.6, 5, (BLOOD[0], BLOOD[1], BLOOD[1]), bend=lambda u: 0.012 * (u - 13) ** 2 - 1.3, fuller=False)],
        glow=(211, 23, 42, 60))
    # Crimson Reaper: long shaft and a hooked crimson blade
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    shaft = Part(0, 33, const(1.0), DUSK)
    wrap = Part(9, 15, const(1.4), CRIMSON, kind="grip")
    collar = Part(27, 29.5, const(2.0), GOLD)
    for y in range(S):
        for x in range(S):
            u, v = uv(x, y)
            c = None
            for p in (shaft, wrap, collar):
                c = p.color_at(u, v) or c
            # the blade: a crescent hanging from the top of the shaft towards the upper-left
            bu, bv = u - 29.5, v - 0.5
            if 0 <= bv <= 19:
                k = bv / 19.0
                centre = 2.5 - 11 * k * k         # curves back down the shaft
                half = 3.6 * (1 - k) ** 0.6 + 0.3
                d = bu - centre
                if abs(d) <= half:
                    t = d / half
                    c = CRIMSON[0] if t > 0.4 else CRIMSON[2] if t < -0.4 else CRIMSON[1]
            if c is not None:
                px[x, y] = c
    img = outline(img)
    out["crimson_reaper"] = img
    return out


# --------------------------------------------------------------------------------------------- armor

SETS = {
    # name: (light, mid, dark, trim, accent)
    "hunter": (hexc("b48a5e"), hexc("7a5434"), hexc("4a301c"), hexc("9aa6b4"), hexc("3e5a3a")),
    "knight": (hexc("e8eef4"), hexc("aab6c2"), hexc("5c6878"), hexc("e0b44c"), hexc("2f6fd0")),
    "dusk": (hexc("7a74a4"), hexc("3e3860"), hexc("1c1830"), hexc("b48cff"), hexc("ff5d8a")),
}


def poly_mask(points, size, ss=4):
    """Rasterizes a polygon (pixel coordinates, y down) with coverage > 0.5."""
    n = size * ss
    ys, xs = np.mgrid[0:n, 0:n]
    px = (xs + 0.5) / ss
    py = (ys + 0.5) / ss
    inside = np.zeros((n, n), bool)
    pts = points
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]
        xj, yj = pts[j]
        cond = ((yi > py) != (yj > py)) & (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi)
        inside ^= cond
        j = i
    cov = inside.reshape(size, ss, size, ss).mean(axis=(1, 3))
    return cov > 0.5


def paint_icon(masks, palette):
    """masks: list of (mask, role). Shading: vertical gradient, lit left edge, dark right edge."""
    light, mid, dark, trim, accent = palette
    img = np.zeros((S, S, 4), np.uint8)
    for mask, role in masks:
        base = {"main": mid, "trim": trim, "accent": accent, "dark": dark}[role]
        ys, xs = np.nonzero(mask)
        if len(ys) == 0:
            continue
        y0, y1 = ys.min(), ys.max()
        for y, x in zip(ys, xs):
            t = (y - y0) / max(1, y1 - y0)
            c = shade(base, 0.25 - 0.55 * t)
            left = x == 0 or not mask[y, x - 1]
            right = x == S - 1 or not mask[y, x + 1]
            top = y == 0 or not mask[y - 1, x]
            if left or top:
                c = shade(base, 0.45)
            elif right:
                c = shade(base, -0.45)
            img[y, x] = c
    return outline(Image.fromarray(img))


def armor_icons(name, palette):
    out = {}
    # helmet / hood
    if name == "hunter":
        hood = poly_mask([(9, 6), (16, 3), (23, 6), (26, 14), (26, 24), (21, 27), (11, 27), (6, 24), (6, 14)], S)
        face = poly_mask([(11, 12), (21, 12), (21, 22), (16, 25), (11, 22)], S)
        trim = poly_mask([(10, 11), (22, 11), (22, 13), (10, 13)], S)
        m = paint_icon([(hood & ~face, "main"), (trim & ~face, "accent")], palette)
        a = np.array(m)
        a[face[:, :] & (a[:, :, 3] == 0)] = (12, 10, 16, 255)
        out[name + "_hood"] = Image.fromarray(a)
    else:
        helm = poly_mask([(8, 10), (12, 5), (16, 4), (20, 5), (24, 10), (25, 22), (21, 27), (11, 27), (7, 22)], S)
        visor = poly_mask([(9, 14), (23, 14), (23, 17), (9, 17)], S)
        crest = poly_mask([(15, 3), (17, 3), (17, 26), (15, 26)], S)
        parts = [(helm, "main"), (crest & helm, "trim"), (visor, "dark")]
        out[name + "_helm"] = paint_icon(parts, palette)
    # chest
    chest = poly_mask([(5, 7), (11, 5), (16, 8), (21, 5), (27, 7), (27, 14), (24, 14), (24, 27), (8, 27), (8, 14), (5, 14)], S)
    belt = poly_mask([(8, 22), (24, 22), (24, 24), (8, 24)], S)
    plate = poly_mask([(12, 10), (20, 10), (20, 18), (16, 21), (12, 18)], S)
    chest_name = {"hunter": "_coat", "knight": "_cuirass", "dusk": "_chestplate"}[name]
    out[name + chest_name] = paint_icon([(chest, "main"), (plate, "trim" if name != "hunter" else "accent"), (belt, "dark")], palette)
    # legs
    legs = poly_mask([(8, 5), (24, 5), (25, 27), (19, 27), (16, 13), (13, 27), (7, 27)], S)
    waist = poly_mask([(8, 5), (24, 5), (24, 9), (8, 9)], S)
    knees = poly_mask([(8, 17), (13, 17), (13, 20), (8, 20)], S) | poly_mask([(19, 17), (24, 17), (24, 20), (19, 20)], S)
    legs_name = {"hunter": "_leggings", "knight": "_greaves", "dusk": "_leggings"}[name]
    out[name + legs_name] = paint_icon([(legs, "main"), (waist & legs, "dark"), (knees & legs, "trim")], palette)
    # boots
    boots = poly_mask([(6, 12), (13, 12), (13, 22), (15, 26), (4, 26), (5, 22)], S) | \
        poly_mask([(19, 12), (26, 12), (27, 22), (28, 26), (17, 26), (19, 22)], S)
    cuffs = poly_mask([(5, 12), (14, 12), (14, 15), (5, 15)], S) | poly_mask([(18, 12), (27, 12), (27, 15), (18, 15)], S)
    boots_name = {"hunter": "_boots", "knight": "_sabatons", "dusk": "_boots"}[name]
    out[name + boots_name] = paint_icon([(boots, "main"), (cuffs & boots, "trim"), ], palette)
    return out


# Vanilla humanoid armor UV boxes: (u, v, w, h, d) with the standard cube unwrap.
def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def fill_face(a, rect, palette, style, rows=None, name="", face=""):
    x, y, w, h = rect
    light, mid, dark, trim, accent = palette
    r0, r1 = (0, h) if rows is None else rows
    for j in range(r0, r1):
        for i in range(w):
            t = j / max(1, h - 1)
            c = shade(mid, 0.18 - 0.4 * t)
            edge = i == 0 or i == w - 1 or j == r0 or j == r1 - 1
            if edge:
                c = shade(mid, -0.35)
            if style == "plate" and (j - r0) % 4 == 3 and not edge:
                c = shade(mid, -0.2)
            if style == "cloth" and (i + j) % 5 == 0 and not edge:
                c = shade(mid, -0.12)
            a[y + j, x + i] = c


def armor_layers(name, palette):
    light, mid, dark, trim, accent = palette
    style = {"hunter": "cloth", "knight": "plate", "dusk": "plate"}[name]
    l1 = np.zeros((32, 64, 4), np.uint8)
    l2 = np.zeros((32, 64, 4), np.uint8)
    # layer 1: helmet (head), chestplate (body + arms), boots (lower legs)
    for f, rect in HEAD.items():
        fill_face(l1, rect, palette, style)
    x, y, w, h = HEAD["front"]
    if name == "hunter":
        l1[y + 2:y + 8, x + 1:x + 7] = 0  # open hood: the face shows
        l1[y + 1, x:x + 8] = trim
    else:
        l1[y + 3:y + 5, x + 1:x + 7] = (14, 12, 20, 255)  # visor slit
        l1[y:y + 8, x + 3:x + 5] = np.where(l1[y:y + 8, x + 3:x + 5, 3:4] > 0, np.array(trim), 0)
        l1[y + 3:y + 5, x + 1:x + 7] = (14, 12, 20, 255)
    for f, rect in BODY.items():
        fill_face(l1, rect, palette, style)
    x, y, w, h = BODY["front"]
    l1[y + 2:y + 7, x + 2:x + 6] = trim if name != "hunter" else accent  # chest emblem
    l1[y + 9:y + 11, x:x + w] = dark  # belt
    for f, rect in ARM.items():
        fill_face(l1, rect, palette, style)
        rx, ry, rw, rh = rect
        if f not in ("top", "bottom"):
            l1[ry:ry + 2, rx:rx + rw] = trim  # pauldron rim
    for f, rect in LEG.items():
        rx, ry, rw, rh = rect
        if f in ("top", "bottom"):
            continue
        fill_face(l1, rect, palette, style, rows=(7, rh))
        l1[ry + 7, rx:rx + rw] = trim
    fill_face(l1, LEG["bottom"], palette, style)
    # layer 2: leggings (waist + legs)
    for f, rect in BODY.items():
        rx, ry, rw, rh = rect
        if f in ("top", "bottom"):
            continue
        fill_face(l2, rect, palette, style, rows=(8, rh))
    for f, rect in LEG.items():
        rx, ry, rw, rh = rect
        if f == "bottom":
            continue
        if f == "top":
            fill_face(l2, rect, palette, style)
            continue
        fill_face(l2, rect, palette, style, rows=(0, 10))
        l2[ry + 5:ry + 7, rx:rx + rw] = trim  # knee guard
    return Image.fromarray(l1), Image.fromarray(l2)


# --------------------------------------------------------------------------------------------- remedies

def bottle(shape, liquid, glass=hexc("dfefff", 150), cork=hexc("9a6a3a")):
    img = np.zeros((S, S, 4), np.uint8)
    if shape == "round":
        body = poly_mask([(16 + 9 * math.cos(a), 19 + 9 * math.sin(a)) for a in np.linspace(0, 2 * math.pi, 40)], S)
        neck = poly_mask([(13, 5), (19, 5), (19, 11), (13, 11)], S)
        fill_top = 15
    elif shape == "tall":
        body = poly_mask([(10, 12), (22, 12), (23, 27), (9, 27)], S)
        neck = poly_mask([(13, 4), (19, 4), (19, 12), (13, 12)], S)
        fill_top = 15
    elif shape == "flask":
        body = poly_mask([(13, 11), (19, 11), (26, 26), (24, 28), (8, 28), (6, 26)], S)
        neck = poly_mask([(13, 4), (19, 4), (19, 11), (13, 11)], S)
        fill_top = 17
    else:  # vial
        body = poly_mask([(12, 9), (20, 9), (20, 25), (16, 28), (12, 25)], S)
        neck = poly_mask([(13, 5), (19, 5), (19, 9), (13, 9)], S)
        fill_top = 13
    shell = body | neck
    for y in range(S):
        for x in range(S):
            if not shell[y, x]:
                continue
            c = glass
            if body[y, x] and y >= fill_top:
                t = (y - fill_top) / 14
                c = shade(liquid, 0.2 - 0.45 * t)
            img[y, x] = c
    # highlight streak
    for y in range(fill_top - 3, fill_top + 8):
        x = 11 if shape != "flask" else 12
        if 0 <= y < S and shell[y, x]:
            img[y, x] = (255, 255, 255, 230)
    # cork
    img[3:6, 14:18] = cork
    img[3, 14:18] = shade(cork, 0.3)
    return outline(Image.fromarray(img), (24, 20, 34, 255))


# --------------------------------------------------------------------------------------------- effect icons

EFFECT_ICONS = {
    "deep_burn": ("flame", "ff7a2e", "ffd27a"),
    "venom": ("flask", "7ccf3a", "d6ff9a"),
    "heavy_venom": ("skull", "3f7a18", "a8e06a"),
    "crimson_brand": ("spiral", "d02a68", "ff8ab4"),
    "fire_ward": ("shield", "ff9a4a", "ffe0b0"),
    "poison_ward": ("shield", "7ccf3a", "e0ffc0"),
    "blood_ward": ("shield", "d3172a", "ffb0b8"),
}


def effect_icon(svg_name, dark, light):
    import cairosvg
    with open(os.path.join(ICON_SRC, svg_name + ".svg"), "rb") as f:
        png = cairosvg.svg2png(file_obj=f, output_width=64, output_height=64)
    src = Image.open(io.BytesIO(png)).convert("RGBA")
    alpha = np.array(src.resize((14, 14), Image.LANCZOS))[:, :, 3].astype(float) / 255
    mask = alpha > 0.45
    d = np.array(hexc(dark)[:3], float)
    l = np.array(hexc(light)[:3], float)
    img = np.zeros((18, 18, 4), np.uint8)
    for y in range(14):
        for x in range(14):
            if mask[y, x]:
                t = y / 13
                c = l * (1 - t) + d * t
                img[y + 2, x + 2] = (*c.round().astype(int), 255)
    return outline(Image.fromarray(img), (16, 10, 20, 255))


# --------------------------------------------------------------------------------------------- light textures

def brand_sigil(size=256):
    """A thorned ring with runes: white on transparent, for the crimson brand under a target."""
    yy, xx = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2
    x = (xx - c) / c
    y = (yy - c) / c
    r = np.sqrt(x * x + y * y)
    ang = np.arctan2(y, x)
    a = np.zeros((size, size))
    ring = lambda rad, w: np.clip(1 - np.abs(r - rad) / w, 0, 1)
    a = np.maximum(a, ring(0.92, 0.025))
    a = np.maximum(a, ring(0.78, 0.018) * 0.85)
    a = np.maximum(a, ring(0.40, 0.02) * 0.8)
    # thorns between the outer rings, pointing inward
    thorns = 12
    k = (ang / (2 * np.pi) * thorns) % 1.0
    thorn = (np.abs(k - 0.5) < (r - 0.78) / 0.14 * 0.5) & (r > 0.78) & (r < 0.92)
    a = np.maximum(a, thorn * 0.9)
    # a six-point star inside
    for i in range(6):
        t = i * np.pi / 3
        p1 = np.array([np.cos(t), np.sin(t)]) * 0.76
        p2 = np.array([np.cos(t + 2 * np.pi / 3), np.sin(t + 2 * np.pi / 3)]) * 0.76
        d = p2 - p1
        l2 = d.dot(d)
        tt = np.clip(((x - p1[0]) * d[0] + (y - p1[1]) * d[1]) / l2, 0, 1)
        dist = np.sqrt((x - p1[0] - tt * d[0]) ** 2 + (y - p1[1] - tt * d[1]) ** 2)
        a = np.maximum(a, np.clip(1 - dist / 0.016, 0, 1) * 0.7)
    # rune ticks on the middle band
    ticks = (np.abs(((ang / (2 * np.pi) * 36) % 1.0) - 0.5) < 0.12) & (r > 0.83) & (r < 0.88)
    a = np.maximum(a, ticks * 0.75)
    a = np.maximum(a, np.clip(1 - r / 0.18, 0, 1) * 0.5)
    img = Image.fromarray((np.dstack([np.full_like(a, 255)] * 3 + [a * 255])).astype(np.uint8), "RGBA")
    return img.filter(ImageFilter.GaussianBlur(0.6))


def burst(size=128):
    """A spiky star of light: hit sparks and pops."""
    yy, xx = np.mgrid[0:size, 0:size]
    c = (size - 1) / 2
    x = (xx - c) / c
    y = (yy - c) / c
    r = np.sqrt(x * x + y * y) + 1e-6
    ang = np.arctan2(y, x)
    rng = np.random.default_rng(7)
    spikes = 11
    lengths = 0.55 + 0.45 * rng.random(spikes)
    k = ang / (2 * np.pi) * spikes
    idx = np.floor(k).astype(int) % spikes
    frac = k - np.floor(k)
    width = np.abs(frac - 0.5) * 2  # 0 at the spike's centre line
    reach = lengths[idx] * (1 - width) ** 2.2
    a = np.clip(1 - r / np.maximum(reach, 1e-3), 0, 1) ** 0.8
    a = np.maximum(a, np.clip(1 - r / 0.35, 0, 1) ** 1.5)
    img = Image.fromarray((np.dstack([np.full_like(a, 255)] * 3 + [a * 255])).astype(np.uint8), "RGBA")
    return img.filter(ImageFilter.GaussianBlur(0.8))


def main():
    for d in (ITEM_OUT, ARMOR_OUT, EFFECT_ICON_OUT, FX_OUT):
        os.makedirs(d, exist_ok=True)
    for name, img in weapons().items():
        img.save(os.path.join(ITEM_OUT, name + ".png"))
    for name, palette in SETS.items():
        for item, img in armor_icons(name, palette).items():
            img.save(os.path.join(ITEM_OUT, item + ".png"))
        l1, l2 = armor_layers(name, palette)
        l1.save(os.path.join(ARMOR_OUT, name + "_layer_1.png"))
        l2.save(os.path.join(ARMOR_OUT, name + "_layer_2.png"))
    bottle("round", hexc("6ec8ff")).save(os.path.join(ITEM_OUT, "quenching_elixir.png"))
    bottle("vial", hexc("8ee05a")).save(os.path.join(ITEM_OUT, "antidote.png"))
    bottle("flask", hexc("3fa65a")).save(os.path.join(ITEM_OUT, "greater_antidote.png"))
    bottle("tall", hexc("d3172a")).save(os.path.join(ITEM_OUT, "clotting_tonic.png"))
    for name, (svg, dark, light) in EFFECT_ICONS.items():
        effect_icon(svg, dark, light).save(os.path.join(EFFECT_ICON_OUT, name + ".png"))
    brand_sigil().save(os.path.join(FX_OUT, "brand_sigil.png"), optimize=True)
    burst().save(os.path.join(FX_OUT, "burst.png"), optimize=True)
    for name in ("brand_sigil", "burst"):
        with open(os.path.join(FX_OUT, name + ".png.mcmeta"), "w") as f:
            f.write('{"texture": {"blur": true, "clamp": true}}\n')
    print("combat assets written")


if __name__ == "__main__":
    main()
