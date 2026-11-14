#!/usr/bin/env python3
"""
Generates the job change module's textures:

- distance-field icons for the 30 subclasses ("subclass_<key>") and their 66 starter skills ("skill_<path>"),
  from Phosphor Icons (bold, MIT) in tools/icons-src, with the same pipeline as tools/gen_ui_assets.py
- the Ascension Key item sprite (32x32 pixel art)

Run from the repo root:  python3 tools/gen_job_assets.py
Needs: cairosvg, pillow, numpy, scipy. With --fetch <phosphor bold dir> it first copies missing SVGs into icons-src.
"""
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from gen_ui_assets import ICON_SRC, icon_mask, sdf_from_mask, write_sdf  # noqa: E402

ITEM_OUT = "src/main/resources/assets/beyondsolo/textures/item"

# keep in sync with Subclass.java (iconSource)
SUBCLASSES = {
    "bloodrager": "drop", "warlord": "flag-banner", "blademaster": "sword", "juggernaut": "shield-chevron",
    "worldbreaker": "planet",
    "nightblade": "knife", "toxin_weaver": "flask", "mirage_dancer": "wind", "bounty_reaper": "crosshair",
    "eclipse_sovereign": "moon-stars",
    "pyromancer": "fire", "stormcaller": "lightning", "tidecaller": "waves", "necromancer": "skull",
    "starborn_archon": "shooting-star",
    "paladin": "sun", "ironwall_sentinel": "castle-turret", "spellward_warden": "shield-star",
    "vengeance_knight": "shield-warning", "everlasting_colossus": "mountains",
    "lightbinder": "sun-horizon", "grovekeeper": "tree", "spring_oracle": "drop-half", "hemomancer": "heartbeat",
    "dawn_saint": "sun-dim",
    "sharpshooter": "target", "wildcaller": "paw-print", "snare_artisan": "graph", "storm_archer": "cloud-lightning",
    "heavenpiercer": "arrow-up",
}

# keep in sync with SubclassSkills.java
SKILLS = {
    "blood_frenzy": "drop", "rage_engine": "flame", "banner_of_conquest": "flag-banner",
    "commanding_presence": "crown", "thousand_cuts": "sword", "flowing_blade": "wave-sine",
    "unstoppable_charge": "arrow-fat-lines-right", "iron_hide": "shield", "cataclysm_fist": "hand-fist",
    "fault_line": "lightning-slash", "titan_blood": "barbell",
    "umbral_execution": "knife", "night_hunter": "moon", "miasma_cloud": "cloud-fog", "virulence": "biohazard",
    "mirage_flurry": "users-three", "evasive_step": "sneaker-move", "death_sentence": "crosshair",
    "opening_strike": "lightning", "total_eclipse": "circle-half", "void_rend": "spiral",
    "sovereign_of_dusk": "moon-stars",
    "inferno_pillar": "fire", "kindled_soul": "fire-simple", "chain_lightning": "lightning", "static_charge": "atom",
    "tidal_crash": "waves", "deep_current": "drop", "soul_harvest": "skull", "grave_pact": "ghost",
    "astral_cataclysm": "shooting-star", "singularity": "spiral", "celestial_core": "planet",
    "holy_judgment": "sun", "devotion": "hands-praying", "fortress_stance": "castle-turret", "unyielding": "anchor",
    "mana_barrier": "shield-star", "arcane_ward": "hexagon", "retribution": "shield-warning",
    "burning_spite": "fire-simple", "colossus_form": "mountains", "earthen_domain": "globe-hemisphere-west",
    "undying": "heart-half",
    "radiant_burst": "sun-horizon", "blessed_aura": "sparkle", "verdant_bloom": "flower-lotus",
    "photosynthesis": "leaf", "cleansing_tide": "drop-half", "foresight": "eye", "blood_siphon": "heartbeat",
    "crimson_vigor": "heart", "miracle_of_dawn": "sun-dim", "judgment_of_dawn": "sword", "eternal_grace": "infinity",
    "deadeye_shot": "target", "steady_aim": "crosshair-simple", "call_of_the_pack": "paw-print",
    "beast_bond": "dog", "snare_field": "graph", "field_craft": "wrench", "thunder_volley": "cloud-lightning",
    "charged_quiver": "lightning", "heavens_arrow": "arrow-up", "storm_of_a_thousand": "arrows-out",
    "eagle_sovereign": "bird",
}


def fetch(src_dir):
    for name in sorted(set(SUBCLASSES.values()) | set(SKILLS.values())):
        dst = os.path.join(ICON_SRC, name + ".svg")
        if not os.path.exists(dst):
            shutil.copyfile(os.path.join(src_dir, name + "-bold.svg"), dst)


def key_sprite():
    """A violet crystal key: ring bow with a gem, a shaft, and a toothed bit, outlined dark."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    outline = (34, 16, 66, 255)
    body = (150, 112, 240, 255)
    light = (214, 196, 255, 255)
    dark = (96, 60, 190, 255)
    gem = (255, 214, 110, 255)
    gem_l = (255, 246, 210, 255)

    def put(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            p[x, y] = c

    # the key runs diagonally from the bow (top left) to the bit (bottom right)
    cx, cy = 9, 9
    for y in range(32):
        for x in range(32):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= 42:
                put(x, y, outline)
    for y in range(32):
        for x in range(32):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= 30:
                put(x, y, light if x + y < cx + cy - 2 else body if x + y < cx + cy + 3 else dark)
            if d2 <= 6:
                put(x, y, outline)
            if d2 <= 2:
                put(x, y, gem_l if (x, y) == (cx - 1, cy - 1) else gem)
    # shaft and bit: distance to the diagonal line, 2 px core inside a 1 px outline
    for y in range(32):
        for x in range(32):
            along = (x + y) / 2
            across = (x - y) / 1.414
            if 12 <= along <= 27 and abs(across) <= 2.2 and (x - cx) ** 2 + (y - cy) ** 2 > 30:
                put(x, y, outline)
    for y in range(32):
        for x in range(32):
            along = (x + y) / 2
            across = (x - y) / 1.414
            if 12.5 <= along <= 26.5 and abs(across) <= 1.0 and (x - cx) ** 2 + (y - cy) ** 2 > 30:
                put(x, y, light if across > 0.3 else body if across > -0.3 else dark)
    # two teeth hanging off the lower left side of the bit
    for t, ln in ((21.0, 3.6), (25.0, 4.6)):
        for y in range(32):
            for x in range(32):
                along = (x + y) / 2
                across = (x - y) / 1.414
                if t - 1.2 <= along <= t + 1.2 and -ln - 1 <= across < -1.6:
                    put(x, y, outline)
        for y in range(32):
            for x in range(32):
                along = (x + y) / 2
                across = (x - y) / 1.414
                if t - 0.5 <= along <= t + 0.5 and -ln <= across < -1.2:
                    put(x, y, body)
    # little sparkles
    for (x, y) in ((25, 6), (4, 24)):
        put(x, y, gem_l)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(x + dx, y + dy, (200, 170, 255, 200))
    return img


def main():
    if "--fetch" in sys.argv:
        fetch(sys.argv[sys.argv.index("--fetch") + 1])
    for key, src in SUBCLASSES.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "subclass_" + key)
    for path, src in SKILLS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "skill_" + path)
    os.makedirs(ITEM_OUT, exist_ok=True)
    key_sprite().save(os.path.join(ITEM_OUT, "ascension_key.png"), optimize=True)
    print(len(SUBCLASSES), "subclass icons,", len(SKILLS), "skill icons, 1 item sprite")


if __name__ == "__main__":
    main()
