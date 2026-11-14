"""Builds the System UI icon and effect textures.

Run from the repo root: python3 tools/gen_ui_assets.py [--preview out.png]
Needs: pip install pillow numpy scipy cairosvg

Icons are stored as signed distance fields (SDF): every pixel holds the distance to the edge of the
shape instead of a colour. The "beyondsolo:shape" shader turns that back into a sharp, smooth edge at any
size and can add outlines and glows for free. That is why one 128px texture works for a 10px stat icon
and a 90px class emblem.

Sources:
- tools/icons-src/*.svg: Phosphor Icons (fill weight), MIT License, see LICENSE_PhosphorIcons_MIT.txt there.
  The licence text ships in the jar as licenses/phosphor_icons_mit.txt.
- Rank letters: Exo 2 ExtraBold Italic, SIL Open Font License 1.1, see tools/fonts-src/OFL.txt.
"""
import io
import json
import math
import os
import shutil
import sys

import cairosvg
import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont
from scipy import ndimage

ICON_SRC = "tools/icons-src"
FONT_SRC = "tools/fonts-src"
TEX = "src/main/resources/assets/beyondsolo/textures"
ICON_OUT = f"{TEX}/gui/icon"
EFFECT_OUT = f"{TEX}/effect"
LICENSES = "src/main/resources/licenses"

# Keep in sync with Icons.java.
SIZE = 128      # texture size
CONTENT = 96    # the source icon's 256x256 view box is mapped onto this many pixels in the middle
SPREAD = 16     # distance (in texture pixels) that maps to the 0..0.5 / 0.5..1 value range
SS = 8          # supersampling factor while rasterising

# texture name -> source svg
ICONS = {
    "stat_str": "hand-fist",
    "stat_agi": "lightning",
    "stat_vit": "heart",
    "stat_int": "brain",
    "stat_sense": "eye",
    "class_warrior": "sword",
    "class_assassin": "knife",
    "class_mage": "magic-wand",
    "class_guardian": "shield-chevron",
    "class_healer": "first-aid",
    "class_ranger": "target",
    "kind_info": "info",
    "kind_quest": "scroll",
    "kind_alert": "warning",
    "kind_level_up": "caret-double-up",
    "kind_reward": "gift",
    "pin": "map-pin",
    "compass": "compass",
    "lock": "lock",
    "check": "check-fat",
    "drop": "drop",
    "moon": "moon",
    "star": "star-four",
    "diamond": "diamond",
    "eye_off": "eye-slash",
    "skull": "skull",
    "hourglass": "hourglass",
    "crown": "crown",
    "medal": "medal",
    "bell": "bell",
    "sparkle": "sparkle",
    "seal": "seal",
    "hexagon": "hexagon",
    "flame": "flame",
    "flask": "flask",
    "spiral": "spiral",
    "heart_break": "heart-break",
    "snowflake": "snowflake",
    "run": "person-simple-run",
    "shield": "shield",
    "weight": "barbell",
}

RANKS = "EDCBAS"


def sdf_from_mask(mask_hi: np.ndarray) -> Image.Image:
    """mask_hi: bool array of (SIZE*SS)^2, True inside the shape. Returns the 8-bit SDF texture."""
    outside = ndimage.distance_transform_edt(~mask_hi)
    inside = ndimage.distance_transform_edt(mask_hi)
    signed = (outside - inside) / SS  # in texture pixels, positive outside
    small = signed.reshape(SIZE, SS, SIZE, SS).mean(axis=(1, 3))
    value = np.clip(0.5 - small / (2 * SPREAD), 0, 1)
    return Image.fromarray(np.round(value * 255).astype(np.uint8), "L")


def icon_mask(svg_name: str) -> np.ndarray:
    hi = SIZE * SS
    content = CONTENT * SS
    png = cairosvg.svg2png(url=os.path.join(ICON_SRC, svg_name + ".svg"), output_width=content, output_height=content)
    alpha = Image.open(io.BytesIO(png)).convert("RGBA").split()[3]
    canvas = Image.new("L", (hi, hi), 0)
    canvas.paste(alpha, ((hi - content) // 2, (hi - content) // 2))
    return np.asarray(canvas) >= 128


def letter_mask(letter: str) -> np.ndarray:
    hi = SIZE * SS
    font = ImageFont.truetype(os.path.join(FONT_SRC, "Exo2-ExtraBoldItalic.ttf"), int(CONTENT * SS * 1.12))
    canvas = Image.new("L", (hi, hi), 0)
    draw = ImageDraw.Draw(canvas)
    left, top, right, bottom = draw.textbbox((0, 0), letter, font=font)
    x = (hi - (right - left)) / 2 - left
    y = (hi - (bottom - top)) / 2 - top
    draw.text((x, y), letter, font=font, fill=255)
    return np.asarray(canvas) >= 128


def write_sdf(img: Image.Image, name: str):
    path = os.path.join(ICON_OUT, name + ".png")
    img.save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


# ---------------------------------------------------------------- magic circle

def magic_circle(size=512, ss=4) -> Image.Image:
    """White-on-transparent summoning circle that the crystal projects on the floor. Drawn additively in game."""
    n = size * ss
    c = n / 2
    img = Image.new("L", (n, n), 0)
    d = ImageDraw.Draw(img)

    def ring(radius, width):
        r = radius * n / 2
        d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=max(1, int(width * n / 2)))

    def poly(points, width):
        pts = [(c + math.cos(a) * r * n / 2, c + math.sin(a) * r * n / 2) for a, r in points]
        d.line(pts + [pts[0]], fill=255, width=max(1, int(width * n / 2)), joint="curve")

    ring(0.93, 0.012)
    ring(0.86, 0.005)
    ring(0.60, 0.008)
    ring(0.555, 0.004)
    ring(0.24, 0.008)
    # tick marks between the two outer rings
    for i in range(72):
        a = i / 72 * math.tau
        r1 = 0.865
        r2 = 0.925 if i % 6 else 0.80
        d.line([(c + math.cos(a) * r1 * n / 2, c + math.sin(a) * r1 * n / 2),
                (c + math.cos(a) * r2 * n / 2, c + math.sin(a) * r2 * n / 2)], fill=255, width=max(1, int(0.004 * n / 2)))
    # two overlapping squares (eight-pointed star) and an octagon touching the 0.6 ring
    for offset in (0.0, math.pi / 4):
        poly([(offset + k * math.tau / 4, 0.60) for k in range(4)], 0.007)
    poly([(math.pi / 8 + k * math.tau / 8, 0.60) for k in range(8)], 0.004)
    # small circles on the outer band and rune-like glyph strokes between them
    for k in range(8):
        a = k * math.tau / 8 + math.pi / 8
        r = 0.73 * n / 2
        x, y = c + math.cos(a) * r, c + math.sin(a) * r
        rr = 0.05 * n / 2
        d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=255, width=max(1, int(0.006 * n / 2)))
        rr2 = 0.017 * n / 2
        d.ellipse([x - rr2, y - rr2, x + rr2, y + rr2], fill=255)
    rng = np.random.default_rng(7)
    for k in range(40):
        if k % 5 == 0:
            continue  # leave room for the small circles
        a = k / 40 * math.tau + math.pi / 8
        # each "rune" is 2-3 short strokes in a little local frame
        base_r = 0.73
        ux, uy = math.cos(a), math.sin(a)
        vx, vy = -uy, ux
        for _ in range(int(rng.integers(2, 4))):
            p = rng.uniform(-0.035, 0.035, size=4)
            x1 = c + (ux * (base_r + p[0]) + vx * p[1]) * n / 2
            y1 = c + (uy * (base_r + p[0]) + vy * p[1]) * n / 2
            x2 = c + (ux * (base_r + p[2]) + vx * p[3]) * n / 2
            y2 = c + (uy * (base_r + p[2]) + vy * p[3]) * n / 2
            d.line([(x1, y1), (x2, y2)], fill=255, width=max(1, int(0.005 * n / 2)))
    # inner star
    poly([(-math.pi / 2 + k * math.tau / 8, 0.24 if k % 2 == 0 else 0.11) for k in range(8)], 0.005)
    for k in range(8):
        a = k * math.tau / 8
        d.line([(c + math.cos(a) * 0.24 * n / 2, c + math.sin(a) * 0.24 * n / 2),
                (c + math.cos(a) * 0.555 * n / 2, c + math.sin(a) * 0.555 * n / 2)], fill=255, width=max(1, int(0.003 * n / 2)))

    sharp = img.resize((size, size), Image.LANCZOS)
    glow = sharp.filter(ImageFilter.GaussianBlur(size / 90))
    wide = sharp.filter(ImageFilter.GaussianBlur(size / 28))
    a = np.asarray(sharp, dtype=np.float32) / 255
    g = np.asarray(glow, dtype=np.float32) / 255
    w = np.asarray(wide, dtype=np.float32) / 255
    alpha = np.clip(a + g * 0.9 + w * 0.8, 0, 1)
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def soft_glow(size=64) -> Image.Image:
    """Round soft dot for sparks and flares."""
    y, x = np.mgrid[0:size, 0:size]
    r = np.hypot(x - (size - 1) / 2, y - (size - 1) / 2) / (size / 2)
    alpha = np.clip(1 - r, 0, 1) ** 2.2
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def shock_ring(size=128) -> Image.Image:
    """A ring of light with a crisp leading edge and a soft trail inside it: a shockwave seen from above."""
    y, x = np.mgrid[0:size, 0:size]
    r = np.hypot(x - (size - 1) / 2, y - (size - 1) / 2) / (size / 2)
    edge = np.exp(-((r - 0.86) / 0.035) ** 2)
    trail = np.clip((r - 0.35) / 0.51, 0, 1) ** 3 * (r < 0.86) * 0.45
    alpha = np.clip(edge + trail, 0, 1)
    rgba = np.zeros((size, size, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(alpha * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


# ---------------------------------------------------------------- preview

def preview(path):
    """Contact sheet that renders the SDFs the way the shader does (edge at 0.5, plus a glow)."""
    names = sorted(n[:-4] for n in os.listdir(ICON_OUT) if n.endswith(".png"))
    cols, cell, draw = 9, 132, 96
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * cell), (10, 18, 32))
    for i, name in enumerate(names):
        sdf = np.asarray(Image.open(os.path.join(ICON_OUT, name + ".png")).convert("L").resize((draw, draw), Image.BILINEAR), dtype=np.float32) / 255
        dist = (0.5 - sdf) * 2 * SPREAD * draw / SIZE
        inside = np.clip(0.5 - dist, 0, 1)
        glow = np.clip(1 - np.maximum(dist, 0) / (SPREAD * draw / SIZE), 0, 1) ** 2 * 0.55 * (1 - inside)
        col = np.array([129, 212, 250], dtype=np.float32)
        bg = np.array([10, 18, 32], dtype=np.float32)
        a = (inside + glow)[..., None]
        out = bg * (1 - a) + col * a
        tile = Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGB")
        sheet.paste(tile, ((i % cols) * cell + 18, (i // cols) * cell + 8))
        ImageDraw.Draw(sheet).text(((i % cols) * cell + 8, (i // cols) * cell + 112), name, fill=(190, 200, 210))
    sheet.save(path)


def main():
    os.makedirs(ICON_OUT, exist_ok=True)
    os.makedirs(EFFECT_OUT, exist_ok=True)
    for name, src in ICONS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), name)
    for letter in RANKS:
        write_sdf(sdf_from_mask(letter_mask(letter)), "rank_" + letter.lower())
    os.makedirs(LICENSES, exist_ok=True)
    shutil.copyfile(os.path.join(ICON_SRC, "LICENSE_PhosphorIcons_MIT.txt"), os.path.join(LICENSES, "phosphor_icons_mit.txt"))

    magic_circle().save(os.path.join(EFFECT_OUT, "magic_circle.png"), optimize=True)
    soft_glow().save(os.path.join(EFFECT_OUT, "glow.png"), optimize=True)
    shock_ring().save(os.path.join(EFFECT_OUT, "ring.png"), optimize=True)
    for name in ("magic_circle", "glow", "ring"):
        with open(os.path.join(EFFECT_OUT, name + ".png.mcmeta"), "w") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)

    total = sum(os.path.getsize(os.path.join(ICON_OUT, n)) for n in os.listdir(ICON_OUT))
    print(len(ICONS) + len(RANKS), "icons,", total // 1024, "KB")
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
