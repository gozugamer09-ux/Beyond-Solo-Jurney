#!/usr/bin/env python3
"""
Builds the dungeon key assets.

Run from the repo root:  python3 tools/gen_key_assets.py [--preview out_dir]
Needs: pillow, numpy, scipy, cairosvg (the same as tools/gen_ui_assets.py).

- Item textures (64x64, painted with tools/hd_paint.py) in textures/item/key: a key for each rank (E plain iron,
  D bronze gone green, C frost steel, B blackened steel set with amethyst, A gold with ember veins, S crimson
  obsidian bound in dark gold, horned), each with a light layer drawn full-bright (the gem in its bow, and from C up
  the runes along its blade), and the two seals a key can carry: a wax-red sigil medallion hung from the bow, and the
  ascendant one in gold.
- Item models: each key, and its sealed and ascendant looks (picked by the item property beyondsolo:seal).
- Light textures for the world effects (white, tinted when drawn): the lock that forms in the air while a key turns
  (a ring of tumblers around a keyhole), its cracks in three stages, the keyhole at the heart of a keyed gate and the
  lock rings turning around it.
- Distance-field icons (see tools/gen_ui_assets.py): a key, and the chain.
- The body animation of turning a key (playerAnimator, see tools/gen_signature_anims.py).
- The English text: assets/beyondsolo_keys/lang/en_us.json.
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
ITEM_TEX = f"{ASSETS}/textures/item/key"
MODELS = f"{ASSETS}/models/item"
FX = f"{ASSETS}/textures/effect"
ANIMS = f"{ASSETS}/player_animations"
LANG = "src/main/resources/assets/beyondsolo_keys/lang/en_us.json"

N = 64
RANKS = "EDCBAS"
# the light of each rank (PlayerRank colours, a little brighter for a gem)
RANK_LIGHT = ["d6e2f0", "8fe6a0", "79c4ff", "c99bff", "ffc45e", "ff5a4a"]
GRADE_FX = ["common", "rare", "epic", "legendary", "mythic", "divine"]
# the key's axis: the middle of the bow, low on the left, and the tip of the blade, high on the right
BOW = np.array([17.0, 46.5])
TIP = np.array([54.5, 9.0])
U = (TIP - BOW) / np.linalg.norm(TIP - BOW)   # along the blade
V = np.array([-U[1], U[0]])                   # across it, towards the teeth (down and to the right)
LENGTH = float(np.linalg.norm(TIP - BOW))


def at(s, t=0.0):
    """A point s along the key from the bow's middle and t across it (towards the teeth)."""
    p = BOW + U * s + V * t
    return float(p[0]), float(p[1])


def quad_mask(s0, s1, t0, t1):
    return hp.poly([at(s0, t0), at(s1, t0), at(s1, t1), at(s0, t1)], N)


def ring(cx, cy, r0, r1):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    d = np.hypot(xx - cx, yy - cy)
    return (d <= r1) & (d > r0)


def disc(cx, cy, r):
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    return np.hypot(xx - cx, yy - cy) <= r


def lobes(cx, cy, n, r_ring, r_lobe, start):
    m = np.zeros((N, N), bool)
    for k in range(n):
        a = math.radians(start + 360 * k / n)
        m |= disc(cx + r_ring * math.cos(a), cy + r_ring * math.sin(a), r_lobe)
    return m


def ngon_mask(cx, cy, r, n, start):
    return hp.poly([(cx + r * math.cos(math.radians(start + 360 * k / n)), cy + r * math.sin(math.radians(start + 360 * k / n)))
                    for k in range(n)], N)


# ================================================================================================ the keys

def teeth(pattern, width=3.0):
    """Bits hanging off the blade near its tip: (position along, length) pairs."""
    m = np.zeros((N, N), bool)
    for s, length in pattern:
        m |= quad_mask(s - width / 2, s + width / 2, 1.5, 2.2 + length)
    return m


def spikes(cx, cy, n, r0, r1, half_width_deg, start, jitter=None):
    """Pointed rays around a centre: n triangles from radius r0 out to r1."""
    m = np.zeros((N, N), bool)
    for k in range(n):
        a = start + 360 * k / n
        r = r1 * (jitter[k % len(jitter)] if jitter else 1)
        p0 = (cx + r0 * math.cos(math.radians(a - half_width_deg)), cy + r0 * math.sin(math.radians(a - half_width_deg)))
        p1 = (cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)))
        p2 = (cx + r0 * math.cos(math.radians(a + half_width_deg)), cy + r0 * math.sin(math.radians(a + half_width_deg)))
        m |= hp.poly([p0, p1, p2], N)
    return m


def key_parts(tier):
    """The key's pieces for one rank: (layers [(mask, colour, material)], light mask, gem radius, silhouette)."""
    bx, by = BOW
    light = np.zeros((N, N), bool)
    if tier == 0:
        # E: plain iron. A round bow, a straight blade, two square bits
        bow = disc(bx, by, 11.0) & ~disc(bx, by, 4.6)
        blade = quad_mask(9, LENGTH, -2.8, 2.8)
        bits = teeth([(LENGTH - 9.5, 6), (LENGTH - 3.5, 4)], width=3.6)
        collar = quad_mask(9.5, 13, -4.4, 4.4)
        layers = [(blade | bits, "8f969e", "metal"), (bow, "9aa1aa", "metal"), (collar, "6f7680", "dark_metal")]
        gem = 3.6
    elif tier == 1:
        # D: bronze gone green, a three-lobed bow, three bits
        bow = (lobes(bx, by, 3, 6.4, 5.6, -135) | disc(bx, by, 9.2)) & ~disc(bx, by, 4.6)
        blade = quad_mask(9, LENGTH, -2.8, 2.8)
        bits = teeth([(LENGTH - 12, 5), (LENGTH - 7, 7), (LENGTH - 2.5, 4)], width=3.4)
        collar = quad_mask(10, 13.5, -4.5, 4.5) | quad_mask(16, 17.8, -3.6, 3.6)
        layers = [(blade | bits, "a9783e", "gold"), (bow, "b5823f", "gold"), (collar, "7d5530", "metal")]
        gem = 3.6
    elif tier == 2:
        # C: frost steel. A six-sided bow with frost points, icicle bits, a rune line down the blade
        bow = (ngon_mask(bx, by, 11.2, 6, 15) | spikes(bx, by, 6, 9.5, 13.2, 9, 45)) & ~disc(bx, by, 4.8)
        blade = quad_mask(9, LENGTH, -2.9, 2.9)
        bits = hp.poly([at(LENGTH - 13, 2), at(LENGTH - 9.5, 2), at(LENGTH - 11.5, 10.0)], N)
        bits |= hp.poly([at(LENGTH - 8, 2), at(LENGTH - 4, 2), at(LENGTH - 6.2, 7.5)], N)
        bits |= hp.poly([at(LENGTH - 3.5, 2), at(LENGTH, 2), at(LENGTH - 1.8, 5.0)], N)
        collar = quad_mask(10, 13.5, -4.6, 4.6)
        layers = [(blade | bits, "a6cfe6", "ice"), (bow, "b8dcf0", "ice"), (collar, "5f86a8", "metal")]
        light |= quad_mask(17, LENGTH - 14, -0.5, 0.5)
        gem = 3.8
    elif tier == 3:
        # B: blackened steel set with amethyst. A four-lobed bow, crystal points on it, stepped bits
        bow = (lobes(bx, by, 4, 6.8, 5.4, 0) | disc(bx, by, 8.8)) & ~disc(bx, by, 4.8)
        crystals = spikes(bx, by, 4, 7.5, 16.0, 16, 45)
        blade = quad_mask(9, LENGTH, -3.0, 3.0)
        bits = teeth([(LENGTH - 12.5, 8), (LENGTH - 8, 5), (LENGTH - 3.5, 7)], width=3.6)
        collar = quad_mask(10, 14, -4.8, 4.8)
        layers = [(blade | bits, "3d3549", "dark_metal"), (crystals & ~bow, "b07cf0", "crystal"), (bow, "443b52", "dark_metal"),
                  (collar, "2a2433", "dark_metal")]
        light |= quad_mask(17, LENGTH - 14, -0.55, 0.55)
        gem = 4.0
    elif tier == 4:
        # A: gold, a bow crowned with rays like a sun, ember light down the blade
        bow = disc(bx, by, 9.8) & ~disc(bx, by, 4.8)
        rays = spikes(bx, by, 9, 8.0, 16.5, 13, 0, jitter=[1.0, 0.8, 0.9])
        blade = quad_mask(9, LENGTH, -3.0, 3.0)
        bits = teeth([(LENGTH - 14, 5), (LENGTH - 9.5, 8), (LENGTH - 5, 5), (LENGTH - 1.5, 3)], width=3.2)
        collar = quad_mask(10, 14.5, -5.0, 5.0)
        layers = [(blade | bits, "e0ae48", "gold"), (rays & ~bow, "c98f2e", "gold"), (bow, "e8b84e", "gold"), (collar, "a36a22", "gold")]
        light |= quad_mask(17, LENGTH - 15, -0.55, 0.55)
        gem = 4.0
    else:
        # S: crimson obsidian bound in dark gold: a bow of jagged thorns, jagged bits, glowing cracks
        bow = (disc(bx, by, 10.4)) & ~disc(bx, by, 5.0)
        thorns = spikes(bx, by, 5, 8.0, 19.5, 15, -100, jitter=[1.0, 0.74, 0.9, 0.8, 0.95])
        blade = quad_mask(9, LENGTH, -3.2, 3.2)
        bits = hp.poly([at(LENGTH - 16, 2), at(LENGTH - 12, 2), at(LENGTH - 13.5, 9.5)], N)
        bits |= hp.poly([at(LENGTH - 10.5, 2), at(LENGTH - 6, 2), at(LENGTH - 9.5, 11.5)], N)
        bits |= hp.poly([at(LENGTH - 5, 2), at(LENGTH - 0.5, 2), at(LENGTH - 2.5, 6.5)], N)
        trim = quad_mask(9, LENGTH, -3.2, -2.1) | quad_mask(9, LENGTH, 2.1, 3.2)
        rim = disc(bx, by, 10.4) & ~disc(bx, by, 8.9)
        collar = quad_mask(10, 15, -5.2, 5.2)
        layers = [(blade | bits, "571826", "obsidian"), (trim, "b8862c", "gold"), (thorns & ~bow, "7a2234", "obsidian"),
                  (bow, "5e1a29", "obsidian"), (rim, "b8862c", "gold"), (collar, "b8862c", "gold")]
        light |= quad_mask(16, LENGTH - 16, -0.6, 0.6)
        gem = 4.4
    sil = np.zeros((N, N), bool)
    for m, _, _ in layers:
        sil |= m
    return layers, light & sil, gem, sil


def key_image(tier):
    """The key itself (what the light layer leaves dark) and its light layer, as two RGBA images."""
    layers, light, gem_r, sil = key_parts(tier)
    cv = hp.Canvas(N)
    for i, (m, color, material) in enumerate(layers):
        cv.layer(m, hp.hexc(color), material, seed=40 + tier * 7 + i)
    cv.seams()
    a = cv.a
    bx, by = BOW
    # patina on the bronze, frost on the steel, a few scratches on the iron
    yy, xx = np.mgrid[0:N, 0:N]
    if tier == 0:
        scratch = hp.cracks(sil, 5, n=4, length=6)
        a[scratch & sil, :3] = np.minimum(255, a[scratch & sil, :3].astype(int) + 22).astype(np.uint8)
    elif tier == 1:
        n = hp.value_noise((N, N), 3, 9)
        patina = sil & (n > 0.28) & ~hp.shrink(sil, 3)
        a[patina, :3] = (np.where((xx[patina] + yy[patina]) % 3 == 0, 1, 0)[:, None] * np.array([40, 30, 20])
                         + np.array([86, 156, 128])).astype(np.uint8)
    elif tier == 2:
        frost = sil & ~hp.shrink(sil, 1) & (yy < xx + 4)
        a[frost, :3] = np.minimum(255, a[frost, :3].astype(int) + 46).astype(np.uint8)
    elif tier == 5:
        cracks = hp.cracks(hp.shrink(sil, 1), 31, n=5, length=7)
        hp.glow_paint(a, cracks & sil, (255, 96, 60), halo=0.4, halo_px=1)
    # the socket in the bow (the gem sits in it on the light layer)
    socket = disc(bx, by, gem_r + 0.9)
    a[socket & ~disc(bx, by, gem_r)] = (*hp.scale(hp.hexc(layers[1][1] if len(layers) > 1 else layers[0][1]), 0.32)[:3], 255)
    a[disc(bx, by, gem_r)] = (20, 16, 30, 255)
    # a groove down the blade for the rune line (lit on the light layer)
    if light.any():
        hp.engrave(a, hp.grow(light, 1) & sil & ~light, 0.25)
        a[light] = (24, 18, 34, 255)
    base = hp.finish(a, grade=GRADE_FX[tier], accent=hp.hexc(RANK_LIGHT[tier]), seed=tier + 11)

    # the light layer: the gem and the rune line, drawn full-bright over the key
    glow = np.zeros((N, N, 4), np.uint8)
    hp.gem(glow, bx, by, gem_r, hp.hexc(RANK_LIGHT[tier]))
    rim = hp.grow(disc(bx, by, gem_r), 1) & ~disc(bx, by, gem_r)
    glow[rim] = 0
    if light.any():
        col = np.array(hp.hexc(RANK_LIGHT[tier])[:3])
        hot = np.minimum(255, col * 0.5 + 128)
        glow[light, :3] = hot.astype(np.uint8)
        glow[light, 3] = 255
        # runes: brighter dashes along the line
        s = (np.mgrid[0:N, 0:N][1] + np.mgrid[0:N, 0:N][0]) % 5
        dash = light & (s == 0)
        glow[dash, :3] = 255
    return base, Image.fromarray(glow)


def seal_image(ascendant):
    """The seal hung from the bow: a medallion on a short chain, wax-red with a sigil, or gold for an ascendant key."""
    a = np.zeros((N, N, 4), np.uint8)
    cx, cy = 6.5, 58.0
    chain = hp.thick_line((BOW[0] - 6.5, BOW[1] + 6.5), (cx + 2.0, cy - 3.5), 1.3, N)
    links = chain & ((np.mgrid[0:N, 0:N][0] % 3) != 0)
    a[links] = (150, 140, 120, 255) if not ascendant else (224, 186, 92, 255)
    med = disc(cx, cy, 4.9)
    img, _ = hp.shade_mask(med, hp.hexc("e0ac3e" if ascendant else "a8232e"), "gold" if ascendant else "leather", seed=7)
    a[med] = img[med]
    # the mark pressed into it: a crown for the ascendant seal, the boss's claw-mark otherwise
    if ascendant:
        mark = hp.poly([(cx - 3, cy + 1.5), (cx - 3, cy - 1.5), (cx - 1.5, cy), (cx, cy - 2.5), (cx + 1.5, cy), (cx + 3, cy - 1.5), (cx + 3, cy + 1.5)], N)
    else:
        mark = hp.thick_line((cx - 2.5, cy - 2.5), (cx + 1.5, cy + 2.5), 0.9, N) | hp.thick_line((cx - 0.5, cy - 3), (cx + 3, cy + 1.5), 0.9, N)
    hp.engrave(a, mark & med, 0.45)
    hp.outline(a)
    return Image.fromarray(a)


def seal_light(ascendant):
    """The seal's glint (full-bright): a gold medallion's crown, or the sigil's ember."""
    a = np.zeros((N, N, 4), np.uint8)
    cx, cy = 6.5, 58.0
    if ascendant:
        mark = hp.poly([(cx - 3, cy + 1.5), (cx - 3, cy - 1.5), (cx - 1.5, cy), (cx, cy - 2.5), (cx + 1.5, cy), (cx + 3, cy - 1.5), (cx + 3, cy + 1.5)], N)
        a[mark] = (255, 236, 160, 255)
    else:
        mark = hp.thick_line((cx - 2.5, cy - 2.5), (cx + 1.5, cy + 2.5), 0.9, N) | hp.thick_line((cx - 0.5, cy - 3), (cx + 3, cy + 1.5), 0.9, N)
        a[mark] = (255, 120, 90, 255)
    return Image.fromarray(a)


# ================================================================================================ models

# How a key sits in the hand in first person (client.key.KeyClientEvents undoes it while a key turns: keep them equal).
# Smaller and more upright than an ordinary item, so the whole key shows, bow in hand and bit up.
HELD_FIRST_PERSON = {"rotation": [0, -90, 35], "translation": [1.28, 5.38, 0.77], "scale": [0.4, 0.4, 0.4]}
HELD = "beyondsolo:item/key/key_held"


def item_layers(textures, emissive=(), parent=HELD, overrides=None):
    model = {"parent": parent, "loader": "neoforge:item_layers",
             "textures": {f"layer{i}": t for i, t in enumerate(textures)}}
    if emissive:
        model["neoforge_data"] = {"layers": {str(i): {"block_light": 15, "sky_light": 15} for i in emissive}}
    if overrides:
        model["overrides"] = overrides
    return model


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=indent, ensure_ascii=False)
        f.write("\n")


def write_models():
    write_json(f"{MODELS}/key/key_held.json", {"parent": "neoforge:item/default",
                                                "display": {"firstperson_righthand": HELD_FIRST_PERSON}})
    for t, r in enumerate(RANKS):
        r = r.lower()
        key = f"beyondsolo:item/key/key_{r}"
        light = f"beyondsolo:item/key/key_{r}_light"
        write_json(f"{MODELS}/key/key_{r}_sealed.json", item_layers(
            [key, "beyondsolo:item/key/seal", light, "beyondsolo:item/key/seal_light"], emissive=(2, 3)))
        write_json(f"{MODELS}/key/key_{r}_ascendant.json", item_layers(
            [key, "beyondsolo:item/key/seal_ascendant", light, "beyondsolo:item/key/seal_ascendant_light"], emissive=(2, 3)))
        write_json(f"{MODELS}/dungeon_key_{r}.json", item_layers([key, light], emissive=(1,), overrides=[
            {"predicate": {"beyondsolo:seal": 1}, "model": f"beyondsolo:item/key/key_{r}_sealed"},
            {"predicate": {"beyondsolo:seal": 2}, "model": f"beyondsolo:item/key/key_{r}_ascendant"},
        ]))


# ================================================================================================ textures

def save_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def write_item_textures():
    for t, r in enumerate(RANKS):
        base, glow = key_image(t)
        save_png(base, f"{ITEM_TEX}/key_{r.lower()}.png")
        save_png(glow, f"{ITEM_TEX}/key_{r.lower()}_light.png")
    save_png(seal_image(False), f"{ITEM_TEX}/seal.png")
    save_png(seal_image(True), f"{ITEM_TEX}/seal_ascendant.png")
    save_png(seal_light(False), f"{ITEM_TEX}/seal_light.png")
    save_png(seal_light(True), f"{ITEM_TEX}/seal_ascendant_light.png")


# ================================================================================================ light textures

def light_rgba(alpha):
    rgba = np.zeros(alpha.shape + (4,), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def glow_alpha(sharp, size, tight=0.85, wide=0.7):
    g = np.asarray(sharp.filter(ImageFilter.GaussianBlur(size / 64)), np.float32) / 255
    w = np.asarray(sharp.filter(ImageFilter.GaussianBlur(size / 20)), np.float32) / 255
    a = np.asarray(sharp, np.float32) / 255
    return np.clip(a + g * tight + w * wide, 0, 1)


def keyhole_poly(cx, cy, s):
    """A keyhole: a round head over a flared slot, s = the head's radius."""
    pts = []
    for k in range(41):
        a = math.radians(125 + 290 * k / 40)
        pts.append((cx + s * math.cos(a), cy - 0.35 * s + s * math.sin(a)))
    pts += [(cx + 0.95 * s, cy + 2.2 * s), (cx - 0.95 * s, cy + 2.2 * s)]
    return pts


def fx_lock(size=256, ss=4):
    """The lock that forms in the air: a ring of tumblers around a keyhole, with a thin outer ring."""
    S = size * ss
    img = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    r_out, r_in = S * 0.44, S * 0.36
    d.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], outline=255, width=int(S * 0.018))
    d.ellipse([c - r_in, c - r_in, c + r_in, c + r_in], outline=255, width=int(S * 0.026))
    # tumblers: short bars between the rings
    for k in range(12):
        a = math.radians(360 * k / 12)
        w = S * 0.022
        p0 = (c + (r_in + S * 0.02) * math.cos(a), c + (r_in + S * 0.02) * math.sin(a))
        p1 = (c + (r_out - S * 0.02) * math.cos(a), c + (r_out - S * 0.02) * math.sin(a))
        d.line([p0, p1], fill=255, width=int(w))
    # little notches on the inner ring
    for k in range(24):
        a = math.radians(360 * k / 24 + 7.5)
        p = (c + (r_in - S * 0.05) * math.cos(a), c + (r_in - S * 0.05) * math.sin(a))
        d.ellipse([p[0] - S * 0.008, p[1] - S * 0.008, p[0] + S * 0.008, p[1] + S * 0.008], fill=255)
    hole = keyhole_poly(c, c - S * 0.04, S * 0.085)
    d.polygon(hole, outline=255, width=int(S * 0.014))
    sharp = img.resize((size, size), Image.LANCZOS)
    return light_rgba(glow_alpha(sharp, size, 0.7, 0.5))


def fx_keyhole(size=128, ss=4):
    """A solid keyhole of light (the heart of a keyed gate)."""
    S = size * ss
    img = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(img)
    d.polygon(keyhole_poly(S / 2, S / 2 - S * 0.12, S * 0.2), fill=255)
    sharp = img.resize((size, size), Image.LANCZOS)
    return light_rgba(glow_alpha(sharp, size, 0.9, 0.6))


def fx_lock_ring(size=256, ss=4):
    """A ring of a lock's tumblers, turning around a keyed gate: a band with teeth and gaps."""
    S = size * ss
    img = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(img)
    c = S / 2
    r = S * 0.42
    d.ellipse([c - r, c - r, c + r, c + r], outline=255, width=int(S * 0.02))
    for k in range(36):
        a0 = 360 * k / 36
        if k % 6 == 5:
            continue
        tooth = S * (0.045 if k % 3 == 0 else 0.028)
        a = math.radians(a0)
        p0 = (c + r * math.cos(a), c + r * math.sin(a))
        p1 = (c + (r - tooth) * math.cos(a), c + (r - tooth) * math.sin(a))
        d.line([p0, p1], fill=255, width=int(S * 0.016))
    sharp = img.resize((size, size), Image.LANCZOS)
    return light_rgba(glow_alpha(sharp, size, 0.6, 0.35))


def crack_paths(seed, n, length, size):
    rng = np.random.default_rng(seed)
    paths = []
    for k in range(n):
        a = 2 * math.pi * k / n + rng.uniform(-0.25, 0.25)
        x, y = size / 2 + math.cos(a) * size * 0.16, size / 2 + math.sin(a) * size * 0.16
        pts = [(x, y)]
        steps = int(length * rng.uniform(0.7, 1.1))
        for _ in range(steps):
            a += rng.uniform(-0.5, 0.5)
            x += math.cos(a) * size * 0.03
            y += math.sin(a) * size * 0.03
            pts.append((x, y))
        paths.append(pts)
    return paths


def fx_cracks(stage, size=256, ss=4):
    """The lock cracking, stage 1..3: lines of light running out from it, more and longer each stage."""
    S = size * ss
    img = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(img)
    paths = crack_paths(77, 9, 11, S)
    for i, pts in enumerate(paths):
        if i % 3 >= stage:
            continue
        keep = pts[:max(2, int(len(pts) * (0.45 + 0.27 * stage)))]
        d.line(keep, fill=255, width=int(S * 0.008), joint="curve")
        # a branch now and then
        if len(keep) > 5 and i % 2 == 0:
            bx, by = keep[len(keep) // 2]
            ex, ey = keep[-1]
            d.line([(bx, by), (bx + (ey - by) * 0.25, by - (ex - bx) * 0.25)], fill=255, width=int(S * 0.006))
    sharp = img.resize((size, size), Image.LANCZOS)
    return light_rgba(glow_alpha(sharp, size, 0.8, 0.3))


def write_light_textures():
    def save(img, name):
        save_png(img, f"{FX}/{name}.png")
        with open(f"{FX}/{name}.png.mcmeta", "w") as f:
            json.dump({"texture": {"blur": True, "clamp": True}}, f)

    save(fx_lock(), "key_lock")
    save(fx_keyhole(), "key_hole")
    save(fx_lock_ring(), "key_ring")
    for s in (1, 2, 3):
        save(fx_cracks(s), f"key_crack_{s}")


# ================================================================================================ icons

def icon_key_mask():
    """A key for the GATES tab: a round bow with a hole, a blade, two bits; diagonal like the item."""
    hi = ui.SIZE * ui.SS
    img = Image.new("L", (hi, hi), 0)
    d = ImageDraw.Draw(img)
    s = ui.CONTENT * ui.SS / 2
    c = hi / 2

    def p(x, y):
        return (c + x * s, c + y * s)

    bow = p(-0.55, 0.55)
    r = 0.36 * s
    d.ellipse([bow[0] - r, bow[1] - r, bow[0] + r, bow[1] + r], fill=255)
    hole = 0.15 * s
    d.ellipse([bow[0] - hole, bow[1] - hole, bow[0] + hole, bow[1] + hole], fill=0)
    d.line([p(-0.35, 0.35), p(0.78, -0.78)], fill=255, width=int(0.2 * s))
    d.line([p(0.42, -0.42), p(0.66, -0.18)], fill=255, width=int(0.17 * s))
    d.line([p(0.64, -0.64), p(0.84, -0.44)], fill=255, width=int(0.17 * s))
    return np.asarray(img) >= 128


def icon_chain_mask():
    """Two links of a chain (the chain bonus)."""
    hi = ui.SIZE * ui.SS
    img = Image.new("L", (hi, hi), 0)
    d = ImageDraw.Draw(img)
    s = ui.CONTENT * ui.SS / 2
    c = hi / 2
    w = int(0.17 * s)
    for (x, y) in ((-0.32, 0.32), (0.32, -0.32)):
        box = [c + (x - 0.42) * s, c + (y - 0.24) * s, c + (x + 0.42) * s, c + (y + 0.24) * s]
        link = Image.new("L", (hi, hi), 0)
        ld = ImageDraw.Draw(link)
        ld.rounded_rectangle(box, radius=0.24 * s, outline=255, width=w)
        link = link.rotate(45, center=(c + x * s, c + y * s), resample=Image.BILINEAR)
        img = Image.fromarray(np.maximum(np.asarray(img), np.asarray(link)))
    return np.asarray(img) >= 128


def write_icons():
    ui.write_sdf(ui.sdf_from_mask(icon_key_mask()), "key")
    ui.write_sdf(ui.sdf_from_mask(icon_chain_mask()), "chain")


# ================================================================================================ body animation

ANIMATIONS = {
    # the key held out at arm's length in the right hand and turned a quarter in the lock (DungeonKeys.USE_TICKS = 30)
    "key_turn": emote("key_turn", "Hold a dungeon key out and turn it in a lock of light", 34, 44, {
        "rightArm": [(4, {"pitch": -82, "yaw": -8, "roll": 0}, "EASEOUTQUAD"), (12, {"pitch": -86, "yaw": -6, "roll": 0}),
                     (20, {"pitch": -86, "yaw": -6, "roll": -40}, "EASEINOUTBACK"), (28, {"pitch": -88, "yaw": -6, "roll": -88}, "EASEINOUTBACK"),
                     (34, {"pitch": -90, "yaw": -5, "roll": -92})],
        "leftArm": [(4, {"pitch": -24, "yaw": 18, "roll": -12}), (20, {"pitch": -30, "yaw": 22, "roll": -14}), (34, {"pitch": -28, "yaw": 20, "roll": -12})],
        "torso": [(4, {"yaw": -7}), (20, {"yaw": -9}), (28, {"yaw": -11}), (34, {"yaw": -10})],
        "head": [(4, {"pitch": 4}), (28, {"pitch": 2}), (34, {"pitch": 0})],
        "rightLeg": [(4, {"pitch": -10}), (34, {"pitch": -12})],
        "leftLeg": [(4, {"pitch": 8}), (34, {"pitch": 9})],
    }),
}


def write_animations():
    os.makedirs(ANIMS, exist_ok=True)
    for name, data in ANIMATIONS.items():
        with open(f"{ANIMS}/{name}.json", "w") as f:
            json.dump(data, f, indent=1)


# ================================================================================================ text

TEXT = {
    "itemGroup.beyondsolo.keys": "Beyond Solo: Dungeon Keys",
    "item.beyondsolo.dungeon_key": "%s-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key.sealed": "%s-Rank Sigil Key",
    "item.beyondsolo.dungeon_key.ascendant": "%s-Rank Ascendant Key",
    "item.beyondsolo.dungeon_key_e": "E-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key_d": "D-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key_c": "C-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key_b": "B-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key_a": "A-Rank Dungeon Key",
    "item.beyondsolo.dungeon_key_s": "S-Rank Dungeon Key",
    "craft.beyondsolo.cat.keys": "KEYS",

    "key.beyondsolo.tip.rank": "Opens a gate of rank %s",
    "key.beyondsolo.tip.sigil": "Sealed: its gate ends with %s",
    "key.beyondsolo.tip.sigil_ascendant": "Ascendant seal: %s waits at its end, ascended",
    "key.beyondsolo.tip.ascendant": "Ascendant: its boss comes ascended",
    "key.beyondsolo.tip.what": "Turn it anywhere and a gate opens a few steps ahead. The way out leads back to where you turned it.",
    "key.beyondsolo.tip.use": "Hold use to turn it. A gate nobody enters gives the key back.",

    "key.beyondsolo.refuse.asleep": "The key won't turn. Only an awakened hunter can open a gate.",
    "key.beyondsolo.refuse.rank": "This %s-Rank key is too heavy for you yet: rank %s needed.",
    "key.beyondsolo.refuse.place": "The System won't open a gate here.",
    "key.beyondsolo.refuse.uncleared": "This gate's boss still stands. Clear it first.",
    "key.beyondsolo.refuse.waiting": "Your last gate is still open, waiting for you.",
    "key.beyondsolo.refuse.room": "No room for a gate here: it needs solid ground and space ahead.",
    "key.beyondsolo.refuse.busy": "You are already turning a key.",
    "key.beyondsolo.sealed": "The gate has sealed behind you.",
    "key.beyondsolo.chain": "Chain x%s: +%s%% XP and gold for this run",
    "key.beyondsolo.no_key": "You have no %s-Rank key to turn.",

    "notify.beyondsolo.key.refund.title": "Key returned",
    "notify.beyondsolo.key.refund.body": "Nobody went through the gate. %s is back in your bag.",

    "key.beyondsolo.hud.run": "%s-RANK RUN",
    "key.beyondsolo.hud.cleared": "CLEARED",
    "key.beyondsolo.hud.new_best": "NEW BEST",
    "key.beyondsolo.hud.best": "BEST %s",
    "key.beyondsolo.hud.chain": "CHAIN x%s",
    "key.beyondsolo.hud.rerun": "RE-RUN",
    "key.beyondsolo.hud.rerun_key": "Turn %s",
    "key.beyondsolo.hud.rerun_chain": "Chain x%s: +%s%%",
    "key.beyondsolo.banner.time": "TIME %s",
    "key.beyondsolo.banner.chain": "CHAIN x%s  +%s%%",

    "screen.beyondsolo.tab.gates": "GATES",
    "key.beyondsolo.tab.ring": "KEY RING",
    "key.beyondsolo.tab.empty": "No keys yet.",
    "key.beyondsolo.tab.empty_hint": "Bosses and elite creatures drop them, the shop sells E to C keys, and the CRAFT tab forges one around a boss core.",
    "key.beyondsolo.tab.opens": "Opens a gate of rank %s",
    "key.beyondsolo.tab.boss": "Ends with %s",
    "key.beyondsolo.tab.boss_ascended": "Ends with %s, ascended",
    "key.beyondsolo.tab.boss_random": "Its boss is the gate's own",
    "key.beyondsolo.tab.how": "A gate opens a few steps ahead of you. It seals 10 seconds after the first one through, and the way out brings you back here.",
    "key.beyondsolo.tab.how_chain": "You are in a cleared cave: the next gate opens right here, as the next run of a chain.",
    "key.beyondsolo.tab.turn": "TURN",
    "key.beyondsolo.tab.turning": "TURNING",
    "key.beyondsolo.tab.held": "x%s",
    "key.beyondsolo.tab.too_heavy": "Needs rank %s to turn",
    "key.beyondsolo.tab.records": "RECORDS",
    "key.beyondsolo.tab.cleared": "%s cleared",
    "key.beyondsolo.tab.best": "Best %s",
    "key.beyondsolo.tab.no_best": "No clear yet",
    "key.beyondsolo.tab.totals": "Keys turned %s  ·  Clears %s  ·  Longest chain x%s",
    "key.beyondsolo.tab.last": "Last run: %s-Rank in %s",
    "key.beyondsolo.tab.in_run": "RUN UNDER WAY",
}


def write_lang():
    write_json(LANG, dict(TEXT))


# ------------------------------------------------------------------------------------------------ previews

def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    scale, cell = 4, 64 * 4 + 12
    bg = (34, 36, 44, 255)
    sheet = Image.new("RGBA", (cell * 6, cell * 3), bg)
    for t in range(6):
        base, glow = key_image(t)
        plain = base.copy()
        plain.alpha_composite(glow)
        sealed = base.copy()
        sealed.alpha_composite(seal_image(False))
        sealed.alpha_composite(glow)
        sealed.alpha_composite(seal_light(False))
        asc = base.copy()
        asc.alpha_composite(seal_image(True))
        asc.alpha_composite(glow)
        asc.alpha_composite(seal_light(True))
        for row, img in enumerate((plain, sealed, asc)):
            sheet.alpha_composite(img.resize((256, 256), Image.NEAREST), (t * cell + 6, row * cell + 6))
    sheet.save(os.path.join(out_dir, "keys.png"))
    fx = Image.new("RGBA", (256 * 6, 256), (12, 14, 22, 255))
    for i, img in enumerate([fx_lock(), fx_keyhole().resize((256, 256)), fx_lock_ring(), fx_cracks(1), fx_cracks(2), fx_cracks(3)]):
        tinted = Image.new("RGBA", img.size, (120, 200, 255, 0))
        tinted.putalpha(img.split()[3])
        fx.alpha_composite(tinted, (i * 256, 0))
    fx.save(os.path.join(out_dir, "key_fx.png"))


def main():
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
        return
    write_item_textures()
    write_models()
    write_light_textures()
    write_icons()
    write_animations()
    write_lang()
    print("key assets written")


if __name__ == "__main__":
    main()
