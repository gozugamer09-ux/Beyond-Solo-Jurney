"""Textures for the chronicle (titles, rankings, journal).

Run from the repository root:  python3 tools/gen_chronicle_assets.py [pictures-folder]

- The chronicle's icons, signed distance fields like every System icon (see gen_ui_assets.py), from the Phosphor
  icons in tools/icons-src (MIT; trophy.svg is Phosphor's bold trophy).
- The journal's realm pictures: one 256x144 picture a realm, cut from screenshots of the realms taken in game
  (the dungeon, red gate, Citadel, Penalty Zone and awakening threads saved them to the project's files). Skipped when
  the screenshots are not there; the pictures already in the repository stay.
"""

import os
import sys

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_ui_assets as ui  # noqa: E402

ICONS = {
    "chronicle_rankings": "trophy",
    "chronicle_journal": "book-open",
    "chronicle_sword": "sword",
    "chronicle_castle": "castle-turret",
    "chronicle_scroll": "scroll",
    "chronicle_realm": "mountains",
    "family_beast": "paw-print",
    "family_arthropod": "bug",
    "family_undead": "ghost",
    "family_arcane": "magic-wand",
    "perk_might": "hand-fist",
}

REALM_OUT = "src/main/resources/assets/beyondsolo/textures/gui/journal/realm"
W, H = 256, 144

# realm -> (picture, crop box or None for the whole frame)
REALMS = {
    "hollows": ("dungeons/pictures/hollows_labyrinth.png", None),
    "mirewood": ("dungeons/pictures/mirewood_hollow.png", None),
    "azure_grotto": ("dungeons/pictures/azure_grotto_chasm.png", None),
    "gloomshade": ("dungeons/pictures/gloomshade_descent.png", None),
    "emberforge": ("dungeons/pictures/emberforge_descent.png", None),
    "crimson_abyss": ("dungeons/pictures/crimson_abyss_hollow.png", None),
    "rootbound_den": ("dungeons/pictures/rootbound_den_hive.png", None),
    "mosswept_ruins": ("dungeons/pictures/mosswept_ruins_gauntlet.png", None),
    "sunscorched_tomb": ("dungeons/pictures/sunscorched_tomb_descent.png", None),
    "bone_catacombs": ("dungeons/pictures/bone_catacombs_labyrinth.png", None),
    "chitin_hive": ("dungeons/pictures/chitin_hive_hive.png", None),
    "sunken_sanctum": ("dungeons/pictures/sunken_sanctum_chasm.png", None),
    "rimefrost": ("dungeons/pictures/rimefrost_hollow.png", None),
    "crystal_seam": ("dungeons/pictures/crystal_seam_caverns.png", None),
    "sporefall_deep": ("dungeons/pictures/sporefall_deep_caverns.png", None),
    "ironbound_keep": ("dungeons/pictures/ironbound_keep_spire.png", None),
    "stormspire": ("dungeons/pictures/stormspire_gauntlet.png", None),
    "starless_expanse": ("dungeons/pictures/starless_expanse_chasm.png", None),
    "infernal_citadel": ("dungeons/pictures/infernal_citadel_gauntlet.png", None),
    "threshold": ("awakening/2-hollow-arbiter-kneeling.png", None),
    "red_world": ("red-gates/05_keep_on_its_hill.png", (430, 118, 1280, 596)),
    "crimson_citadel": ("tower/05_gatekeeper_hall.png", (266, 114, 1013, 534)),
    "penalty_wastes": ("penalty-zone/2-arrival.png", (0, 0, 1138, 640)),
}


def icons():
    for name, svg in ICONS.items():
        ui.write_sdf(ui.sdf_from_mask(ui.icon_mask(svg)), name)
        print("icon", name)


def fit(img: Image.Image) -> Image.Image:
    """Cuts the middle 16:9 out of the picture and scales it down."""
    w, h = img.size
    target = W / H
    if w / h > target:
        nw = int(round(h * target))
        img = img.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(round(w / target))
        img = img.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    img = img.resize((W, H), Image.LANCZOS)
    img = img.filter(ImageFilter.UnsharpMask(radius=1.2, percent=60, threshold=2))
    return ImageEnhance.Contrast(img).enhance(1.06)


def without_crosshair(img: Image.Image) -> Image.Image:
    """Paints the crosshair in the middle of a screenshot over with what surrounds it."""
    a = np.asarray(img).astype(np.float32)
    h, w = a.shape[:2]
    cy, cx = h // 2, w // 2
    r = 12
    box = a[cy - r:cy + r + 1, cx - r:cx + r + 1]
    bright = box.min(axis=2) > 150
    spread = box.max(axis=2) - box.min(axis=2) < 60
    hole = bright & spread
    hole = np.asarray(Image.fromarray(hole.astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(3))) > 0
    for _ in range(40):
        if not hole.any():
            break
        known = ~hole
        acc = np.zeros_like(box)
        cnt = np.zeros(hole.shape, np.float32)
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                if dx == 0 and dy == 0:
                    continue
                k = np.roll(np.roll(known, dy, 0), dx, 1)
                v = np.roll(np.roll(box, dy, 0), dx, 1)
                acc += v * k[..., None]
                cnt += k
        fill = hole & (cnt > 0)
        box[fill] = acc[fill] / cnt[fill][..., None]
        hole = hole & ~fill
    a[cy - r:cy + r + 1, cx - r:cx + r + 1] = box
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def realms(src: str):
    os.makedirs(REALM_OUT, exist_ok=True)
    for realm, (path, box) in REALMS.items():
        full = os.path.join(src, path)
        if not os.path.exists(full):
            print("missing", full)
            continue
        img = without_crosshair(Image.open(full).convert("RGB"))
        if box:
            img = img.crop(box)
        fit(img).save(os.path.join(REALM_OUT, realm + ".png"), optimize=True)
        print("realm", realm)


def main():
    icons()
    realms(sys.argv[1] if len(sys.argv) > 1 else "/mnt/project-files")


if __name__ == "__main__":
    main()
