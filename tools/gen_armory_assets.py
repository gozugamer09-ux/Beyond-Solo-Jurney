#!/usr/bin/env python3
"""
Art for the armory's 39 class sets (com.beyondsolo.armory.ArmorCatalog), at the PR #20 detail level of
tools/hd_armor.py: four 64x64 item icons per set, its two 128x64 worn layers and the item models.

Every set has its own palette and its own mix of helmet, chest and boots shapes, chosen so no two sets of a class
look alike; the grade (common to divine, the same as ArmorCatalog.gradeFor) decides the aura and the sparkles.

Run from the repo root:  python3 tools/gen_armory_assets.py [set ...]
                         python3 tools/gen_armory_assets.py --sheet out.png   (a contact sheet of every icon)
Needs: pillow, numpy.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import hd_armor as ha  # noqa: E402
from hd_paint import hexc  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
MODEL_OUT = os.path.join(ASSETS, "models/item")


def kit(cls, grade, main, mat, trim, trim_mat, accent, helm, chest, boots, glow=None, **extra):
    cfg = dict(cls=cls, grade=grade, main=hexc(main), mat=mat, trim=hexc(trim), trim_mat=trim_mat, accent=hexc(accent),
               accent_mat="cloth", helm=helm, chest=chest, boots=boots, glow=hexc(glow) if glow else None)
    for k, v in extra.items():
        cfg[k] = hexc(v) if isinstance(v, str) and k in ("fur", "tooth", "cape", "gem") else v
    return cfg


# class, grade, main, material, trim, trim material, accent, helm, chest, boots, glow, extras
ARMORY = {
    # ---- village requests (E, common)
    "forgehand": kit("warrior", "common", "8a8f96", "metal", "b07a3a", "gold", "c84a2a", "sallet", "cuirass", "sabatons"),
    "hearthguard": kit("guardian", "common", "7d848c", "metal", "c8a050", "gold", "3e6a9a", "tower", "cuirass", "sabatons"),
    "nightstitch": kit("assassin", "common", "3a3440", "leather", "8a8a9a", "metal", "6a2a3a", "hood", "coat", "laced"),
    "inkweave": kit("mage", "common", "2e3a6a", "cloth", "c8b070", "gold", "e8e0c8", "hood", "robe", "wraps", "9ab8ff"),
    "chapelward": kit("healer", "common", "e6dcc8", "cloth", "c8a050", "gold", "6a9a5a", "hood", "vestment", "wraps", "fff0b0"),
    "fletchwood": kit("ranger", "common", "5a6a3a", "leather", "a07a4a", "wood", "c8a060", "hood", "pelt", "laced",
                      fur="d8c8a8", tooth="e8dcc0"),
    # ---- gate creatures
    "broodsilk": kit("assassin", "common", "4a4a5a", "cloth", "d8d8e0", "bone", "8ee05a", "mask", "coat", "wraps", "9cff5a", eyes=6),
    "gloomstalker": kit("ranger", "rare", "3a3a4e", "leather", "d0d0e0", "bone", "6a5aa8", "wolf", "pelt", "laced", "9a8aff",
                        fur="4a4660"),
    "shardshell": kit("guardian", "rare", "5a6a7a", "stone", "8ad8ff", "crystal", "8ad8ff", "icecrown", "rock", "rock", "8ad8ff",
                      gem="5ac8ff"),
    "cinderwisp": kit("mage", "rare", "3a2a2a", "cloth", "a8643a", "dark_metal", "ff8a2a", "mask", "robe", "wraps", "ffa030"),
    "duskfang": kit("assassin", "rare", "2a2436", "leather", "c8c8d8", "bone", "8a6ad0", "fanged", "coat", "spiked", "b88aff"),
    "silkstrider": kit("ranger", "rare", "c8c8d0", "cloth", "5a6a4a", "leather", "8ee05a", "hood", "scaled", "wraps", "9cff5a"),
    "bonechant": kit("healer", "rare", "4a4a52", "cloth", "e8dcc0", "bone", "b8e0a0", "reaper", "vestment", "wraps", "c8ffb0"),
    "prismhorn": kit("warrior", "epic", "3a4a6a", "dark_metal", "c8f0ff", "crystal", "9ad8ff", "horned", "ice", "ice", "9ad8ff"),
    "ashen_watch": kit("guardian", "epic", "5a5652", "metal", "b0782a", "gold", "d8c8b0", "tower", "rock", "rock", "ff7a3a",
                       gem="ff6a2a"),
    "pyre_wraith": kit("mage", "epic", "2a1e24", "cloth", "ff9a3a", "dark_metal", "ffd24a", "reaper", "robe", "molten", "ff8a2a"),
    "hollow_hierarch": kit("mage", "epic", "5a4a78", "cloth", "e8dcc0", "bone", "c8a8ff", "mask", "vestment", "wraps", "c8a8ff"),
    "moonmaw": kit("ranger", "legendary", "2a2e4a", "leather", "d8e0ff", "metal", "a8c0ff", "wolf", "scaled", "scaled", "c8d8ff",
                   fur="c8d0e8", tooth="f0f4ff"),
    "hollow_saint": kit("healer", "legendary", "e8e4f0", "cloth", "ffd88a", "gold", "fff0b0", "halo", "vestment", "royal", "fff0a8"),
    "ashen_bastion": kit("guardian", "legendary", "3e3a38", "dark_metal", "e09a3a", "gold", "ff6a2a", "greathelm", "molten", "molten",
                         "ff8a3a"),
    # ---- bosses
    "silken_cradle": kit("healer", "rare", "d8d0e0", "cloth", "c8b8e0", "bone", "9cff7a", "mask", "vestment", "wraps", "b8ff9a", eyes=6),
    "fangcrest": kit("warrior", "rare", "5a6878", "metal", "dceaf5", "bone", "8ad0ff", "fanged", "pelt", "sabatons", "9ad8ff",
                     fur="9aa4b0"),
    "hollow_vanguard": kit("warrior", "epic", "5c6070", "metal", "2e2c3a", "dark_metal", "6a4ab0", "greathelm", "spiked", "sabatons",
                           "a080ff"),
    "sigilweaver": kit("mage", "epic", "2a3a6a", "cloth", "b8e2ff", "metal", "7f9cff", "circlet", "robe", "wraps", "b8e2ff"),
    "frostmane": kit("ranger", "epic", "c8dce8", "leather", "5a8ab8", "metal", "4fa8ff", "wolf", "coat", "laced", "9ad8ff",
                     fur="eef4fa", tooth="f0f4f8"),
    "rimebloom": kit("healer", "epic", "b8d8f0", "cloth", "ffffff", "ice", "ff9ad0", "halo", "robe", "wraps", "bdebff"),
    "brood_empress": kit("assassin", "legendary", "3a2a40", "scale", "d8c8f0", "bone", "a0ff5a", "crown", "robe", "wraps", "a0ff5a"),
    "oathbound": kit("guardian", "legendary", "c8ccd4", "metal", "e0b44c", "gold", "ffffff", "winged", "cuirass", "sabatons", "fff0c0"),
    "starbinder": kit("healer", "legendary", "2a2a5a", "cloth", "ffe9a0", "gold", "b8e2ff", "circlet", "vestment", "royal", "fff0a8"),
    "ashwing": kit("ranger", "legendary", "4a3a34", "scale", "a8643a", "dark_metal", "ff8a2a", "winged", "coat", "scaled", "ffa030",
                   wing="membrane"),
    "hollowblade": kit("assassin", "mythic", "1a1a24", "leather", "c0c0d8", "dark_metal", "ff3d5a", "mask", "spiked", "wraps", "ff4a6a"),
    "archmagus": kit("mage", "mythic", "3a1a5a", "cloth", "ffd166", "gold", "7f9cff", "circlet", "royal", "royal", "9ab0ff",
                     cape="241040"),
    "wyrmlord": kit("warrior", "mythic", "4a1e1a", "scale", "ffb43a", "gold", "ff5a1e", "winged", "scaled", "molten", "ff7a2e",
                    wing="membrane"),
    # ---- divine
    "worldbreaker": kit("warrior", "divine", "d8d0c0", "metal", "ffd36a", "gold", "ffb040", "horned", "spiked", "spiked", "ffb040"),
    "veilwalker": kit("assassin", "divine", "141420", "cloth", "fff3c0", "gold", "e0306e", "reaper", "coat", "wraps", "ff6aa0"),
    "starweave": kit("mage", "divine", "e6e9f5", "cloth", "ffd36a", "gold", "4a5ad0", "circlet", "robe", "royal", "c8d8ff"),
    "eternal_bastion": kit("guardian", "divine", "f0ead8", "metal", "ffcf5a", "gold", "6ab0ff", "tower", "royal", "royal", "fff3c0",
                           cape="2a4a8a"),
    "lifebinder": kit("healer", "divine", "e8e4d8", "metal", "ffe08a", "gold", "8ce05a", "halo", "royal", "royal", "fff3c0",
                      cape="3a7a4a"),
    "skywarden": kit("ranger", "divine", "dfe8f0", "leather", "ffd36a", "gold", "6ad0ff", "winged", "coat", "laced", "a8f0ff"),
}
PIECES = ("helm", "chestplate", "leggings", "boots")
for _n, _c in ARMORY.items():
    _c["items"] = tuple(_n + "_" + p for p in PIECES)


def model(item):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/" + item}}


def main(only=None):
    os.makedirs(ha.ITEM_OUT, exist_ok=True)
    os.makedirs(ha.ARMOR_OUT, exist_ok=True)
    os.makedirs(MODEL_OUT, exist_ok=True)
    for name, cfg in ARMORY.items():
        if only and name not in only:
            continue
        for item, img in zip(cfg["items"], ha.icons(cfg)):
            img.save(os.path.join(ha.ITEM_OUT, item + ".png"))
            with open(os.path.join(MODEL_OUT, item + ".json"), "w") as f:
                json.dump(model(item), f, indent=2)
                f.write("\n")
        l1, l2 = ha.layers(cfg)
        l1.save(os.path.join(ha.ARMOR_OUT, name + "_layer_1.png"))
        l2.save(os.path.join(ha.ARMOR_OUT, name + "_layer_2.png"))
    print("armory art written:", len(only) if only else len(ARMORY), "sets")


def sheet(path, scale=2):
    """Every set's four icons in a row, grouped by class, on a dark background (for previews)."""
    order = ["warrior", "assassin", "mage", "guardian", "healer", "ranger"]
    names = sorted(ARMORY, key=lambda n: (order.index(ARMORY[n]["cls"]), list(ARMORY).index(n)))
    cols = 3
    rows = (len(names) + cols - 1) // cols
    cell = 64 * scale
    W = cols * (4 * cell + 24) + 24
    H = rows * (cell + 12) + 24
    out = Image.new("RGBA", (W, H), (22, 20, 30, 255))
    for i, name in enumerate(names):
        cx = 24 + (i % cols) * (4 * cell + 24)
        cy = 12 + (i // cols) * (cell + 12)
        for k, img in enumerate(ha.icons(ARMORY[name])):
            img = img.resize((cell, cell), Image.NEAREST)
            out.alpha_composite(img, (cx + k * cell, cy))
    out.save(path)
    print("sheet written:", path)


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--sheet":
        sheet(args[1] if len(args) > 1 else "armory_sheet.png")
    else:
        main(set(args) or None)
