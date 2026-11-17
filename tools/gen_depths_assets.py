#!/usr/bin/env python3
"""
Builds the depths assets: hidden chambers, trap rooms, elemental weaknesses and gate weather.

Run from the repo root:  python3 tools/gen_depths_assets.py [--preview out.png]
Needs: pillow, numpy, scipy, cairosvg (the same as tools/gen_ui_assets.py).

- Distance-field icons (see tools/gen_ui_assets.py): one for each element, gate weather, trap and hidden chamber, a
  warning sign, a keyhole and the weak-spot mark (DepthsIcons.java).
- Light textures for the world effects (white, tinted when drawn): the frame that outlines a hidden wall through the
  rock when Pulse Scan finds it, and the soft wisp the weather's air is made of (mist, spores, haze).
- Block art. The hidden wall passes for the cave's own rock: each look's model points at that rock's vanilla
  textures (nothing of Minecraft's is copied), with faint cracks in a darker shade of that rock laid over some blocks
  and, deep in a crack, a violet speck that glows as if light seeps through. The trap core: a red crystal on a plinth
  of polished blackstone ringed with burning runes. The lockdown's wall of red light, animated.
- Block models and blockstates for the three.
- The English text: assets/beyondsolo_depths/lang/en_us.json. Spanish lives by hand in es_es.json next to it: add a
  line there for every new key (TranslationGameTests checks), see docs/TRANSLATING.md.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ui_assets as ui  # noqa: E402

ASSETS = "src/main/resources/assets/beyondsolo"
FX = f"{ASSETS}/textures/effect"
BLOCK_TEX = f"{ASSETS}/textures/block"
BLOCK_MODELS = f"{ASSETS}/models/block"
STATES = f"{ASSETS}/blockstates"
LANG = "src/main/resources/assets/beyondsolo_depths/lang/en_us.json"

# ================================================================================================ icons

ICONS = {
    # elements (SkillElement)
    "element_physical": "sword",
    "element_fire": "flame",
    "element_water": "drop",
    "element_lightning": "lightning",
    "element_air": "wind",
    "element_earth": "mountains",
    "element_shadow": "moon-stars",
    "element_light": "sun",
    "element_nature": "leaf",
    "element_arcane": "star-four",
    # gate weather (GateWeather)
    "weather_clear": "sun-dim",
    "weather_mist": "cloud-fog",
    "weather_scorching": "thermometer-hot",
    "weather_blizzard": "snowflake",
    "weather_storm": "cloud-lightning",
    "weather_miasma": "biohazard",
    "weather_crimson": "drop-half-bottom",
    "weather_darkness": "eye-closed",
    "weather_tremors": "waveform",
    # traps (TrapKind)
    "trap_lockdown": "lock",
    "trap_spikes": "caret-double-up",
    "trap_vents": "fire-simple",
    "trap_rockfall": "meteor",
    # hidden chambers (HiddenKind)
    "hidden_vault": "vault",
    "hidden_shrine": "hands-praying",
    "hidden_lair": "skull",
    # marks
    "depths_warning": "warning",
    "depths_keyhole": "keyhole",
    "depths_weak": "crosshair",
}


def write_icons():
    os.makedirs(ui.ICON_OUT, exist_ok=True)
    for name, src in ICONS.items():
        ui.write_sdf(ui.sdf_from_mask(ui.icon_mask(src)), name)


# ================================================================================================ light textures

def white_alpha(alpha: np.ndarray) -> Image.Image:
    rgba = np.zeros(alpha.shape + (4,), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(np.clip(alpha, 0, 1) * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def save_fx(img: Image.Image, name: str):
    os.makedirs(FX, exist_ok=True)
    path = os.path.join(FX, name + ".png")
    img.save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)


def frame(size=64) -> Image.Image:
    """The outline of a block face: a crisp line just inside the edge, a glow falling inwards, a faint fill, and
    brighter corners, so a wall of faces drawn through the rock reads as a lit shape."""
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    c = (size - 1) / 2
    edge = np.maximum(np.abs(x - c), np.abs(y - c)) / (size / 2)   # 0 in the middle, 1 at the edge
    inset = 1 - edge                                                # distance in from the edge, 0..1
    line = np.exp(-((inset - 0.06) / 0.025) ** 2)
    glow = np.exp(-inset / 0.12) * 0.45
    fill = 0.07
    corner = np.minimum(np.abs(x - c), np.abs(y - c)) / (size / 2)
    corners = np.exp(-((1 - corner) / 0.16) ** 2) * np.exp(-((inset - 0.06) / 0.05) ** 2) * 0.6
    return white_alpha(np.clip(line + glow + fill + corners, 0, 1))


def wisp(size=128, seed=11) -> Image.Image:
    """A soft, lumpy puff of air: a few overlapping blobs broken up by noise, fading to nothing at the edge."""
    rng = np.random.default_rng(seed)
    y, x = np.mgrid[0:size, 0:size].astype(np.float32) / size
    alpha = np.zeros((size, size), dtype=np.float32)
    for _ in range(7):
        cx, cy = rng.uniform(0.32, 0.68, 2)
        r = rng.uniform(0.14, 0.24)
        alpha += np.exp(-(((x - cx) ** 2 + (y - cy) ** 2) / (2 * r * r))) * rng.uniform(0.35, 0.6)
    # fractal noise, smooth at every scale
    noise = np.zeros_like(alpha)
    amp, total = 1.0, 0.0
    for octave in (4, 8, 16, 32):
        grid = rng.random((octave + 1, octave + 1)).astype(np.float32)
        layer = np.asarray(Image.fromarray((grid * 255).astype(np.uint8), "L").resize((size, size), Image.BICUBIC), dtype=np.float32) / 255
        noise += layer * amp
        total += amp
        amp *= 0.55
    noise /= total
    r = np.hypot(x - 0.5, y - 0.5) / 0.5
    falloff = np.clip(1 - r, 0, 1) ** 1.6
    out = np.clip(alpha, 0, 1) * (0.45 + 0.75 * noise) * falloff
    out /= max(1e-6, out.max())
    return white_alpha(out * 0.9)


# ================================================================================================ the hidden wall

# HiddenSealBlock.Look -> (side texture, end texture, the rock's average colour). The averages are measured from the
# vanilla textures once and written down here, so the script needs nothing of Minecraft's to run.
LOOKS = {
    "stone": ("stone", "stone", 0x7D7D7D),
    "andesite": ("andesite", "andesite", 0x888888),
    "cobblestone": ("cobblestone", "cobblestone", 0x7F7F7F),
    "mossy_cobblestone": ("mossy_cobblestone", "mossy_cobblestone", 0x6E765E),
    "moss_block": ("moss_block", "moss_block", 0x596D2D),
    "deepslate": ("deepslate", "deepslate_top", 0x505052),
    "cobbled_deepslate": ("cobbled_deepslate", "cobbled_deepslate", 0x4D4D50),
    "tuff": ("tuff", "tuff", 0x6C6D66),
    "blackstone": ("blackstone", "blackstone_top", 0x2A2328),
    "polished_blackstone": ("polished_blackstone", "polished_blackstone", 0x353038),
    "basalt": ("basalt_side", "basalt_top", 0x49484D),
    "netherrack": ("netherrack", "netherrack", 0x612626),
    "obsidian": ("obsidian", "obsidian", 0x0F0A18),
    "red_nether_bricks": ("red_nether_bricks", "red_nether_bricks", 0x450709),
    "calcite": ("calcite", "calcite", 0xDFE0DC),
    "polished_diorite": ("polished_diorite", "polished_diorite", 0xC0C1C2),
    # the realms' own walls (GateRealm): dens, ruins, tombs, catacombs, hives, sanctums, ice, keeps, spires, citadels
    "rooted_dirt": ("rooted_dirt", "rooted_dirt", 0x90674C),
    "packed_mud": ("packed_mud", "packed_mud", 0x8E6A4F),
    "dirt": ("dirt", "dirt", 0x866043),
    "coarse_dirt": ("coarse_dirt", "coarse_dirt", 0x77553B),
    "mossy_stone_bricks": ("mossy_stone_bricks", "mossy_stone_bricks", 0x737969),
    "stone_bricks": ("stone_bricks", "stone_bricks", 0x7A797A),
    "cracked_stone_bricks": ("cracked_stone_bricks", "cracked_stone_bricks", 0x767576),
    "chiseled_stone_bricks": ("chiseled_stone_bricks", "chiseled_stone_bricks", 0x777677),
    "sandstone": ("sandstone", "sandstone_top", 0xD8CB9B),
    "cut_sandstone": ("cut_sandstone", "sandstone_top", 0xD9CE9F),
    "smooth_sandstone": ("sandstone_top", "sandstone_top", 0xDFD6AA),
    "chiseled_sandstone": ("chiseled_sandstone", "sandstone_top", 0xD8CA9B),
    "deepslate_bricks": ("deepslate_bricks", "deepslate_bricks", 0x464647),
    "cracked_deepslate_bricks": ("cracked_deepslate_bricks", "cracked_deepslate_bricks", 0x404041),
    "deepslate_tiles": ("deepslate_tiles", "deepslate_tiles", 0x363637),
    "polished_deepslate": ("polished_deepslate", "polished_deepslate", 0x484849),
    "brown_terracotta": ("brown_terracotta", "brown_terracotta", 0x4D3323),
    "terracotta": ("terracotta", "terracotta", 0x985E43),
    "prismarine_bricks": ("prismarine_bricks", "prismarine_bricks", 0x63AB9E),
    "prismarine": ("prismarine", "prismarine", 0x62A291),
    "dark_prismarine": ("dark_prismarine", "dark_prismarine", 0x335B4B),
    "packed_ice": ("packed_ice", "packed_ice", 0x8DB4FA),
    "blue_ice": ("blue_ice", "blue_ice", 0x74A7FD),
    "snow_block": ("snow", "snow", 0xF9FEFE),
    "polished_andesite": ("polished_andesite", "polished_andesite", 0x848685),
    "tuff_bricks": ("tuff_bricks", "tuff_bricks", 0x62665F),
    "polished_tuff": ("polished_tuff", "polished_tuff", 0x616863),
    "waxed_weathered_cut_copper": ("weathered_cut_copper", "weathered_cut_copper", 0x6D916B),
    "waxed_oxidized_cut_copper": ("oxidized_cut_copper", "oxidized_cut_copper", 0x4F997E),
    "chiseled_polished_blackstone": ("chiseled_polished_blackstone", "chiseled_polished_blackstone", 0x353038),
    "nether_bricks": ("nether_bricks", "nether_bricks", 0x2C151A),
    "polished_blackstone_bricks": ("polished_blackstone_bricks", "polished_blackstone_bricks", 0x302A31),
}
CRACK_PATTERNS = 3
SPECK = (0x5D, 0x45, 0x90)   # the violet that seeps through a crack (drawn full-bright, so it stays dim on purpose)


def crack_pattern(seed):
    """A hairline crack across a 16x16 face: a wandering line from one edge with a short branch, and one or two
    pixels deep in it where light seeps through. Returns (mask, specks)."""
    rng = np.random.default_rng(seed)
    mask = np.zeros((16, 16), dtype=bool)
    side = rng.integers(4)
    t = int(rng.integers(4, 12))
    x, y = [(t, 0), (15, t), (t, 15), (0, t)][side]
    dx, dy = [(0, 1), (-1, 0), (0, -1), (1, 0)][side]
    path = []
    for _ in range(int(rng.integers(9, 13))):
        if 0 <= x < 16 and 0 <= y < 16:
            mask[y, x] = True
            path.append((x, y))
        # mostly onwards, often a step aside, never back
        r = rng.random()
        if r < 0.45:
            x, y = x + dx, y + dy
        elif r < 0.75:
            x, y = x + dx + dy, y + dy + dx
        else:
            x, y = x + dx - dy, y + dy - dx
    # a short branch off the middle
    if len(path) > 5:
        bx, by = path[len(path) // 2]
        bdx, bdy = (dy, dx) if rng.random() < 0.5 else (-dy, -dx)
        for _ in range(int(rng.integers(2, 4))):
            bx, by = bx + bdx + (dx if rng.random() < 0.5 else 0), by + bdy + (dy if rng.random() < 0.5 else 0)
            if 0 <= bx < 16 and 0 <= by < 16:
                mask[by, bx] = True
    inner = [p for p in path if 2 <= p[0] <= 13 and 2 <= p[1] <= 13]
    pick = inner if inner else path
    specks = [pick[len(pick) // 2]]
    if len(pick) > 6 and rng.random() < 0.6:
        specks.append(pick[len(pick) // 2 + 3])
    return mask, specks


def crack_color(avg):
    r, g, b = (avg >> 16) & 0xFF, (avg >> 8) & 0xFF, avg & 0xFF
    lum = 0.299 * r + 0.587 * g + 0.114 * b
    if lum > 60:   # a darker shade of the rock
        return int(r * 0.55), int(g * 0.55), int(b * 0.55)
    # on near-black rock a dark line vanishes: a faint lighter seam with a touch of violet instead
    return min(255, int(r * 1.15) + 16), min(255, int(g * 1.15) + 12), min(255, int(b * 1.15) + 26)


def write_hidden_seal():
    out = os.path.join(BLOCK_TEX, "hidden_seal")
    os.makedirs(out, exist_ok=True)
    patterns = [crack_pattern(91 + 17 * k) for k in range(CRACK_PATTERNS)]
    for k, (mask, specks) in enumerate(patterns):
        glint = np.zeros((16, 16, 4), dtype=np.uint8)
        for (x, y) in specks:
            glint[y, x] = (*SPECK, 255)
        Image.fromarray(glint, "RGBA").save(os.path.join(out, f"glint_{k}.png"), optimize=True)
    for look, (side, end, avg) in LOOKS.items():
        col = crack_color(avg)
        for k, (mask, specks) in enumerate(patterns):
            tex = np.zeros((16, 16, 4), dtype=np.uint8)
            tex[mask] = (*col, 255)
            for (x, y) in specks:
                tex[y, x] = (0, 0, 0, 0)   # the speck's own element draws there
            Image.fromarray(tex, "RGBA").save(os.path.join(out, f"cracks_{look}_{k}.png"), optimize=True)

    models = os.path.join(BLOCK_MODELS, "hidden_seal")
    os.makedirs(models, exist_ok=True)
    dirs = ("north", "south", "east", "west", "up", "down")
    variants = {}
    for look, (side, end, avg) in LOOKS.items():
        rock = {d: {"texture": "#end" if d in ("up", "down") else "#side", "cullface": d} for d in dirs}
        base = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": rock}
        textures = {"particle": f"minecraft:block/{side}", "side": f"minecraft:block/{side}", "end": f"minecraft:block/{end}"}
        write_json(os.path.join(models, f"{look}.json"),
                   {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": textures, "elements": [base]})
        entries = [{"model": f"beyondsolo:block/hidden_seal/{look}", "weight": 5}]
        for k in range(CRACK_PATTERNS):
            t = dict(textures, cracks=f"beyondsolo:block/hidden_seal/cracks_{look}_{k}", glint=f"beyondsolo:block/hidden_seal/glint_{k}")
            elements = [
                base,
                {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {d: {"texture": "#cracks", "cullface": d} for d in dirs}},
                {"from": [0, 0, 0], "to": [16, 16, 16], "shade": False, "neoforge_data": {"block_light": 15, "sky_light": 15},
                 "faces": {d: {"texture": "#glint", "cullface": d} for d in dirs}},
            ]
            write_json(os.path.join(models, f"{look}_{k}.json"),
                       {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": t, "elements": elements})
            entries.append({"model": f"beyondsolo:block/hidden_seal/{look}_{k}"})
        variants[f"look={look}"] = entries
    write_json(os.path.join(STATES, "hidden_seal.json"), {"variants": variants})


# ================================================================================================ the trap core

def trap_core_crystal() -> Image.Image:
    """A face of the red crystal: dark at the foot, burning towards the tip, a bright vein up the middle and dark
    edges, laid out so each stacked part of the crystal (whose uv sits in the middle of the texture) gets its own edges."""
    img = np.zeros((16, 16, 4), dtype=np.float32)
    deep = np.array([0x5E, 0x08, 0x12], dtype=np.float32)
    hot = np.array([0xFF, 0x4A, 0x40], dtype=np.float32)
    pale = np.array([0xFF, 0xD2, 0xC4], dtype=np.float32)
    for y in range(16):
        up = 1 - y / 15                      # 1 at the tip (row 0), 0 at the foot
        for x in range(16):
            across = abs(x - 7.5) / 7.5      # 0 in the middle, 1 at the sides
            base = deep + (hot - deep) * (0.25 + 0.75 * up ** 0.8)
            shade = 1 - 0.55 * across ** 1.5
            col = base * shade
            if abs(x - 7.5) < 1 and y % 5 != 4:
                col = col + (pale - col) * (0.55 + 0.35 * up)
            if (x + 2 * y) % 11 == 0 and 2 < x < 13:
                col = col + (pale - col) * 0.3    # a facet's glint
            img[y, x, :3] = col
            img[y, x, 3] = 255
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGBA")


def trap_core_runes() -> Image.Image:
    """Burning runes in a ring around the crystal's foot, seen from above (laid over the plinth's top): a thin ring,
    a little glyph at each side and an ember at each corner, all pixel by pixel."""
    hot, ember = (0xFF, 0x5A, 0x4A, 255), (0xFF, 0xA0, 0x88, 255)
    rgba = np.zeros((16, 16, 4), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            if 6.0 <= r < 6.9:
                rgba[y, x] = hot
    # one glyph (a fork: two prongs, a glowing joint, a stem), drawn on the north side and turned for the others
    prongs, joint, stem = [(6, 2), (9, 2)], [(7, 3), (8, 3)], [(7, 4), (8, 4)]
    for turn in range(4):
        for group, col in ((prongs, hot), (joint, ember), (stem, hot)):
            for x, y in group:
                for _ in range(turn):
                    x, y = 15 - y, x   # a quarter turn about the middle of the texture
                rgba[y, x] = col
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        rgba[cy, cx] = ember
    return Image.fromarray(rgba, "RGBA")


def write_trap_core():
    os.makedirs(BLOCK_TEX, exist_ok=True)
    trap_core_crystal().save(os.path.join(BLOCK_TEX, "trap_core_crystal.png"), optimize=True)
    trap_core_runes().save(os.path.join(BLOCK_TEX, "trap_core_runes.png"), optimize=True)
    lit = {"block_light": 15, "sky_light": 15}
    turn = {"origin": [8, 8, 8], "axis": "y", "angle": 45}

    def crystal(x0, y0, x1, y1, top):
        """One stacked part of the crystal, turned 45 degrees, its uv in the middle of the crystal texture."""
        side = {"texture": "#crystal", "uv": [x0, 16 - y1, x1, 16 - y0]}
        faces = {d: dict(side) for d in ("north", "south", "east", "west")}
        if top:
            faces["up"] = {"texture": "#crystal", "uv": [x0, x0, x1, x1]}
        return {"from": [x0, y0, x0], "to": [x1, y1, x1], "rotation": turn, "shade": False, "neoforge_data": lit, "faces": faces}

    plinth_side = {"texture": "#plinth", "uv": [1, 12, 15, 16]}
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:cutout",
        "ambientocclusion": False,
        "textures": {
            "particle": "beyondsolo:block/trap_core_crystal",
            "plinth": "minecraft:block/polished_blackstone",
            "plinth_top": "minecraft:block/chiseled_polished_blackstone",
            "runes": "beyondsolo:block/trap_core_runes",
            "crystal": "beyondsolo:block/trap_core_crystal",
        },
        "elements": [
            {"from": [1, 0, 1], "to": [15, 4, 15], "faces": {
                "north": dict(plinth_side), "south": dict(plinth_side), "east": dict(plinth_side), "west": dict(plinth_side),
                "up": {"texture": "#plinth_top", "uv": [1, 1, 15, 15]},
                "down": {"texture": "#plinth", "uv": [1, 1, 15, 15], "cullface": "down"}}},
            {"from": [1, 4.02, 1], "to": [15, 4.02, 15], "shade": False, "neoforge_data": lit,
             "faces": {"up": {"texture": "#runes", "uv": [1, 1, 15, 15]}}},
            crystal(5, 4, 11, 11, False),
            crystal(6, 11, 10, 13, False),
            crystal(7, 13, 9, 15, True),
        ],
    }
    write_json(os.path.join(BLOCK_MODELS, "trap_core.json"), model)
    write_json(os.path.join(STATES, "trap_core.json"), {"variants": {"": {"model": "beyondsolo:block/trap_core"}}})


# ================================================================================================ the lockdown's wall

WARD_FRAMES = 8


def trap_ward() -> Image.Image:
    """The wall of light: a see-through red with a diamond lattice that tiles across a wall of blocks, and a bright
    band sweeping up through it (one frame per step)."""
    frames = []
    y, x = np.mgrid[0:16, 0:16]
    lattice = ((x + y) % 8 == 0) | ((x - y) % 8 == 0)
    nodes = ((x + y) % 8 == 0) & ((x - y) % 8 == 0)
    for f in range(WARD_FRAMES):
        band_row = (15 - 2 * f) % 16
        dist = np.minimum(np.abs(y - band_row), 16 - np.abs(y - band_row))
        band = np.exp(-(dist / 1.4) ** 2)
        alpha = 0.30 + 0.28 * lattice + 0.25 * nodes + 0.30 * band
        mix = np.clip(0.25 * lattice + 0.45 * nodes + 0.5 * band, 0, 1)[..., None]
        base = np.array([0xE0, 0x24, 0x2E], dtype=np.float32)
        light = np.array([0xFF, 0xB0, 0xA0], dtype=np.float32)
        col = base * (1 - mix) + light * mix
        frame_rgba = np.zeros((16, 16, 4), dtype=np.uint8)
        frame_rgba[..., :3] = np.clip(col, 0, 255).astype(np.uint8)
        frame_rgba[..., 3] = np.round(np.clip(alpha, 0, 0.95) * 255).astype(np.uint8)
        frames.append(frame_rgba)
    return Image.fromarray(np.concatenate(frames, axis=0), "RGBA")


def write_trap_ward():
    os.makedirs(BLOCK_TEX, exist_ok=True)
    path = os.path.join(BLOCK_TEX, "trap_ward.png")
    trap_ward().save(path, optimize=True)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, f)
    dirs = ("north", "south", "east", "west", "up", "down")
    model = {
        "parent": "minecraft:block/block",
        "render_type": "minecraft:translucent",
        "ambientocclusion": False,
        "textures": {"particle": "beyondsolo:block/trap_ward", "ward": "beyondsolo:block/trap_ward"},
        "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "shade": False,
                      "neoforge_data": {"block_light": 15, "sky_light": 15},
                      "faces": {d: {"texture": "#ward", "cullface": d} for d in dirs}}],
    }
    write_json(os.path.join(BLOCK_MODELS, "trap_ward.json"), model)
    write_json(os.path.join(STATES, "trap_ward.json"), {"variants": {"": {"model": "beyondsolo:block/trap_ward"}}})


# ================================================================================================ text

WEATHER = {
    "clear": ("Still Air", "The air in this gate is calm."),
    "mist": ("Veil of Mist", "A cold mist fills the cave. You can see only a few steps ahead."),
    "scorching": ("Searing Heat", "The heat builds burns while you stay out of water."),
    "blizzard": ("Frost Gale", "An icy wind cuts through the cave and the cold slows you down."),
    "storm": ("Mana Storm", "Bolts of mana strike wherever the floor lights up. Keep moving."),
    "miasma": ("Spore Miasma", "Poison spores hang in the air. Venom builds up the longer you breathe them."),
    "crimson": ("Crimson Haze", "The creatures here hit harder and heal from every blow they land."),
    "darkness": ("Devouring Dark", "A darkness eats the light. You can barely see, and the creatures are quicker."),
    "tremors": ("Tremors", "The cave shakes and rocks come down wherever the ground is marked."),
}

HIDDEN = {
    "vault": ("Hidden Vault", "A sealed treasure room, untouched since the gate formed.",
              "Gold glints in the dark. The vault's cache is yours."),
    "shrine": ("Forgotten Shrine", "A quiet shrine. Step inside to receive its blessing.",
               "A calm settles over you as you step inside."),
    "lair": ("Hidden Lair", "Something guards this chamber. Defeat its guardians to claim their hoard.",
             "Its guardians wake. Defeat them all to claim the hoard."),
}

TRAPS = {
    "lockdown": ("Lockdown", "The exits are sealed. Survive both waves."),
    "spikes": ("Spike Floor", "Spikes burst from the marked floor. Break the core to stop them."),
    "vents": ("Vent Trap", "Vents blast the marked floor. Break the core to stop them."),
    "rockfall": ("Rockfall", "Rocks fall on the marked spots. Break the core to stop them."),
}


def lang() -> dict:
    t = {
        "block.beyondsolo.hidden_seal": "Cave Wall",
        "block.beyondsolo.trap_core": "Trap Core",
        "block.beyondsolo.trap_ward": "Lockdown Barrier",
    }
    for wid, (title, desc) in WEATHER.items():
        t[f"depths.beyondsolo.weather.{wid}"] = title
        t[f"depths.beyondsolo.weather.{wid}.desc"] = desc
    for hid, (title, desc, enter) in HIDDEN.items():
        t[f"depths.beyondsolo.hidden.{hid}"] = title
        t[f"depths.beyondsolo.hidden.{hid}.desc"] = desc
        t[f"depths.beyondsolo.hidden.enter.{hid}"] = enter
    t.update({
        "depths.beyondsolo.hidden.found": "Hidden Dungeon",
        "depths.beyondsolo.hidden.found.title": "Hidden dungeon found: %s",
        "depths.beyondsolo.hidden.hollow": "The wall sounds hollow. Knock again.",
        "depths.beyondsolo.hidden.lair_cleared.title": "Lair Cleared",
        "depths.beyondsolo.hidden.lair_cleared.body": "Its guardians have fallen. Their hoard is yours.",
        "depths.beyondsolo.hidden.blessing.title": "Shrine's Blessing",
        "depths.beyondsolo.hidden.blessing.body": "Strength, regeneration and resistance for 5 minutes.",
        "depths.beyondsolo.scan.found.title": "Something Hidden",
        "depths.beyondsolo.scan.found": "Your pulse found what the rock hides. It glows violet for a few moments.",
    })
    for tid, (title, hint) in TRAPS.items():
        t[f"depths.beyondsolo.trap.{tid}"] = title
        t[f"depths.beyondsolo.trap.{tid}.hint"] = hint
    t.update({
        "depths.beyondsolo.trap.wave": "Wave %s / %s",
        "depths.beyondsolo.trap.done": "Trap cleared",
        "depths.beyondsolo.trap.done.title": "%s cleared",
        "depths.beyondsolo.trap.defused": "Trap disarmed",
        "depths.beyondsolo.trap.defused.title": "%s disarmed",
        "depths.beyondsolo.trap.reward": "+%s XP",
        "depths.beyondsolo.weakness.found.title": "Weakness Found",
        "depths.beyondsolo.weakness.found.body": "%s is weak to %s",
        "depths.beyondsolo.weakness.found.body_severe": "%s is very weak to %s",
        "depths.beyondsolo.hud.condition": "GATE CONDITION",
        "depths.beyondsolo.hud.chamber": "HIDDEN CHAMBER",
        "depths.beyondsolo.hud.trap": "TRAP",
        "depths.beyondsolo.hud.core": "Break its core",
        "depths.beyondsolo.hud.sealed": "Sealed in",
        "depths.beyondsolo.hud.wave": "Wave %s of %s",
        "depths.beyondsolo.hud.reward": "Rewards +%s%%",
        "depths.beyondsolo.hud.weak": "WEAK",
        "depths.beyondsolo.gaze.weak": "Weak",
        "depths.beyondsolo.gaze.resists": "Resists",
        "notify.beyondsolo.depths.cache.title": "Hidden Rewards",
        "element_rating.beyondsolo.neutral": "",
        "element_rating.beyondsolo.weak": "WEAK",
        "element_rating.beyondsolo.weak_severe": "WEAK+",
        "element_rating.beyondsolo.resist": "RESIST",
        "element_rating.beyondsolo.resist_strong": "RESIST+",
        "element_rating.beyondsolo.immune": "IMMUNE",
    })
    return t


# ================================================================================================ output

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def preview(path):
    """Icons as the shader draws them, then the light textures and the block art blown up."""
    cols, cell = 10, 120
    names = list(ICONS)
    rows = (len(names) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * cell, rows * cell + 300), (10, 18, 32))
    for i, name in enumerate(names):
        sdf = np.asarray(Image.open(os.path.join(ui.ICON_OUT, name + ".png")).convert("L").resize((88, 88), Image.BILINEAR), dtype=np.float32) / 255
        dist = (0.5 - sdf) * 2 * ui.SPREAD * 88 / ui.SIZE
        inside = np.clip(0.5 - dist, 0, 1)[..., None]
        out = np.array([10, 18, 32], dtype=np.float32) * (1 - inside) + np.array([200, 225, 255], dtype=np.float32) * inside
        sheet.paste(Image.fromarray(out.astype(np.uint8), "RGB"), ((i % cols) * cell + 16, (i // cols) * cell + 6))
        ImageDraw.Draw(sheet).text(((i % cols) * cell + 6, (i // cols) * cell + 100), name, fill=(170, 185, 200))
    y0 = rows * cell + 10
    x = 10
    for name in ("depths_frame", "depths_wisp"):
        im = Image.open(os.path.join(FX, name + ".png")).resize((128, 128))
        sheet.paste(im, (x, y0), im)
        x += 140
    for rel in ("trap_core_crystal", "trap_core_runes", "hidden_seal/cracks_stone_0", "hidden_seal/cracks_stone_1",
                "hidden_seal/cracks_obsidian_2", "hidden_seal/glint_0"):
        im = Image.open(os.path.join(BLOCK_TEX, rel + ".png")).resize((128, 128), Image.NEAREST)
        sheet.paste(im, (x, y0), im)
        x += 140
    ward = Image.open(os.path.join(BLOCK_TEX, "trap_ward.png"))
    for f in range(4):
        im = ward.crop((0, 16 * f * 2, 16, 16 * f * 2 + 16)).resize((64, 64), Image.NEAREST)
        sheet.paste(im, (10 + 70 * f, y0 + 150), im)
    sheet.save(path)


def main():
    write_icons()
    save_fx(frame(), "depths_frame")
    save_fx(wisp(), "depths_wisp")
    write_hidden_seal()
    write_trap_core()
    write_trap_ward()
    write_json(LANG, lang())
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
