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
    "inferno_pillar": "fire", "kindled_soul": "fire-simple", "storm_lash": "lightning", "static_charge": "atom",
    "tidal_crash": "waves", "deep_current": "drop", "soul_harvest": "skull", "grave_pact": "ghost",
    "astral_cataclysm": "shooting-star", "singularity": "spiral", "celestial_core": "planet",
    "holy_judgment": "sun", "devotion": "hands-praying", "fortress_stance": "castle-turret", "unyielding": "anchor",
    "mana_barrier": "shield-star", "arcane_ward": "hexagon", "retribution": "shield-warning",
    "burning_spite": "fire-simple", "colossus_form": "mountains", "earthen_domain": "globe-hemisphere-west",
    "undying": "heart-half",
    "radiant_burst": "sun-horizon", "blessed_aura": "sparkle", "verdant_bloom": "flower-lotus",
    "photosynthesis": "leaf", "cleansing_tide": "drop-half", "foresight": "eye", "blood_siphon": "heartbeat",
    "crimson_vigor": "heart", "miracle_of_dawn": "sun-dim", "judgment_of_dawn": "sword", "eternal_grace": "infinity",
    "deadeye_shot": "target", "hawk_focus": "crosshair-simple", "call_of_the_pack": "paw-print",
    "beast_bond": "dog", "snare_field": "graph", "field_craft": "wrench", "tempest_volley": "cloud-lightning",
    "charged_quiver": "lightning", "heavens_arrow": "arrow-up", "storm_of_a_thousand": "arrows-out",
    "eagle_sovereign": "bird",
}

# the Warrior subclasses' own skill sets (keep in sync with WarriorPath.java)
SKILLS.update({
    "crimson_cleave": "axe",
    "gore_rush": "arrow-fat-line-right",
    "thick_blood": "heart",
    "blood_geyser": "drop-simple",
    "hemorrhage": "bandaids",
    "bloodscent": "heartbeat",
    "reckless_slam": "hammer",
    "blood_nova": "asterisk",
    "pain_tolerance": "first-aid",
    "last_stand": "skull",
    "rallying_cry": "megaphone",
    "spear_of_command": "arrow-up-right",
    "iron_discipline": "medal-military",
    "vanguard_charge": "horse",
    "banner_of_ruin": "flag",
    "war_drums": "music-notes",
    "shield_wall": "shield-check",
    "execution_order": "gavel",
    "spoils_of_war": "coins",
    "conquerors_verdict": "crown-simple",
    "iaido": "lightning",
    "crescent_wave": "moon",
    "keen_edge": "diamond",
    "rising_moon": "arrow-bend-right-up",
    "blade_ward": "circles-three",
    "riposte": "arrows-left-right",
    "falling_petal": "flower",
    "sword_rain": "cloud-rain",
    "wind_step": "wind",
    "severing_line": "scissors",
    "seismic_stomp": "boot",
    "boulder_toss": "cube",
    "immovable": "anchor-simple",
    "stone_skin": "wall",
    "grapple_slam": "hand-grabbing",
    "momentum": "fast-forward",
    "living_avalanche": "mountains",
    "bulwark_taunt": "megaphone-simple",
    "second_wind": "heart-straight",
    "mountainfall": "arrow-fat-lines-down",
    "tectonic_uppercut": "arrow-fat-up",
    "meteor_hurl": "meteor",
    "earthen_might": "barbell",
    "gravity_well": "magnet",
    "titan_stride": "footprints",
    "aftershock": "pulse",
    "continental_split": "arrows-split",
    "worldshaker_roar": "speaker-high",
    "unbroken": "shield-plus",
    "end_of_ages": "hourglass",
})

# the Assassin subclasses' own skill sets (keep in sync with AssassinPath.java)
SKILLS.update({
    "shadow_fang": "knife",
    "dusk_shroud": "eye-closed",
    "killer_instinct": "eye",
    "throat_slit": "scissors",
    "umbral_decoy": "ghost",
    "nightfall_step": "sneaker-move",
    "black_lotus": "flower-lotus",
    "night_terror": "mask-sad",
    "finisher": "x",
    "endless_night": "moon-stars",
    "venom_spit": "drop",
    "caustic_slash": "test-tube",
    "toxic_blood": "biohazard",
    "spore_burst": "flower-tulip",
    "paralytic_dart": "syringe",
    "potency": "flask",
    "plague_trail": "footprints",
    "neurotoxin": "brain",
    "antibodies": "shield-plus",
    "pandemic": "virus",
    "phantom_step": "person-simple-run",
    "gale_kick": "wind",
    "featherfall": "feather",
    "mirror_image": "users-three",
    "cyclone_dance": "tornado",
    "tailwind": "fast-forward",
    "updraft": "arrow-fat-lines-up",
    "afterimage_barrage": "dots-three-outline",
    "slipstream": "waves",
    "tempest_waltz": "spiral",
    "chain_hook": "link",
    "reaping_arc": "moon",
    "bounty_ledger": "scroll",
    "crippling_shot": "crosshair-simple",
    "pursuit": "person-simple-run",
    "appraise_weakness": "magnifying-glass",
    "bola_snare": "circles-three",
    "collect_dues": "coins",
    "dead_or_alive": "skull",
    "final_contract": "seal-check",
    "umbral_lance": "arrow-up-right",
    "nightfall": "moon",
    "void_touched": "hand",
    "black_sun": "sun",
    "moonless_veil": "circle-half",
    "penumbra": "circle-dashed",
    "crescent_reaver": "moon-stars",
    "starless_prison": "lock",
    "lord_of_night": "crown",
    "world_without_light": "globe-hemisphere-west",
})

SKILLS.update({
    "fire_lance": "fire-simple", "flame_wall": "wall", "pyromania": "flame", "combustion": "fire", "phoenix_dive": "bird",
    "molten_skin": "thermometer-hot", "meteor_shower": "meteor", "living_bomb": "bomb", "ember_heart": "heart", "sun_fall": "sun-horizon",
    "thunder_spear": "lightning", "ball_lightning": "globe-simple", "overcharge": "battery-charging", "thunderclap": "hands-clapping",
    "lightning_dash": "lightning-slash", "conductivity": "plugs-connected", "storm_cage": "bounding-box", "tempest_call": "cloud-lightning",
    "storm_surge": "wave-sine", "judgment_of_thunder": "gavel",
    "water_whip": "waves", "bubble_prison": "circle", "undertow": "arrow-bend-down-left", "riptide": "arrow-fat-lines-right",
    "healing_rain": "cloud-rain", "high_tide": "drop", "maelstrom": "spiral", "tsunami": "waveform", "ebb_and_flow": "arrows-left-right",
    "abyssal_deluge": "drop-half-bottom",
    "bone_spear": "bone", "withering_curse": "leaf", "necrotic_might": "hand-fist", "grave_burst": "cross", "life_drain": "heartbeat",
    "bone_armor": "shield", "spirit_swarm": "ghost", "curse_of_frailty": "heart-break", "deaths_embrace": "skull", "requiem": "music-notes",
    "star_bolt": "star", "comet": "shooting-star", "stellar_mind": "brain", "event_horizon": "circle-half-tilt", "constellation": "graph",
    "cosmic_flow": "infinity", "supernova": "star-four", "astral_aegis": "shield-star", "zenith": "sun", "genesis_ray": "sparkle",
    # guardian subclass sets
    "hammer_of_dawn": "hammer", "consecrate": "hands-praying", "zeal": "heartbeat", "shield_of_faith": "shield-plus", "radiant_charge": "sun-horizon",
    "aura_of_resolve": "users-three", "lay_on_hands": "hand-palm", "divine_verdict": "gavel", "martyr": "cross", "dawnbreaker": "sun",
    "shield_slam": "shield-chevron", "rampart": "wall", "heavy_plating": "shield-checkered", "iron_tether": "link", "brace": "shield-warning",
    "garrison": "castle-turret", "tremor_march": "footprints", "shield_toss": "arrows-split", "spiked_bulwark": "asterisk", "citadel": "vault",
    "rune_strike": "pentagram", "null_field": "circle-dashed", "mana_weave": "graph", "mirror_ward": "arrows-left-right", "warding_bolt": "lightning",
    "runic_armor": "seal", "binding_glyph": "keyhole", "purge": "eraser", "spell_eater": "shield-slash", "arcanum": "hexagon",
    "searing_brand": "fire-simple", "vengeful_leap": "arrow-bend-right-up", "grudge": "eye", "cinder_chain": "link-break", "wrath": "flame",
    "ashen_hide": "campfire", "eye_for_an_eye": "scales", "funeral_pyre": "fire", "blood_debt": "drop", "day_of_reckoning": "meteor",
    "stone_fist": "boxing-glove", "bedrock_skin": "cube", "titanic_frame": "barbell", "landslide": "mountains", "monolith": "columns",
    "deep_roots": "tree", "grasp_of_the_earth": "hand-grabbing", "stonewake": "arrows-out", "mountain_heart": "heart", "pillars_of_creation": "triangle",
})

# the Healer subclasses' own skill sets (keep in sync with HealerPath.java)
SKILLS.update({
    "sunbeam": "sun", "binding_light": "link", "inner_radiance": "lightbulb", "motes_of_mercy": "circles-three", "lantern_of_mercy": "lamp",
    "afterglow": "sun-dim", "solar_flare": "asterisk", "lightstep": "person-simple-run", "last_light": "first-aid", "solstice": "sun-horizon",
    "thorn_volley": "cactus", "healing_sprout": "plant", "barkskin": "tree-evergreen", "bramble_rush": "grains", "second_spring": "flower",
    "briar_coat": "virus", "spore_cloud": "biohazard", "grasping_vines": "spiral", "toxic_bloom": "flower-lotus", "elderwood": "tree",
    "spring_bolt": "drop", "bubble_ward": "circle", "clear_mind": "brain", "fated_blow": "hourglass", "premonition": "drop-half",
    "second_sight": "eyeglasses", "morning_mist": "cloud-fog", "gaze_of_fate": "eye", "foretold": "scroll", "eternal_spring": "waves",
    "crimson_lance": "syringe", "transfusion": "hand-heart", "sanguine_pact": "handshake", "open_wounds": "heart-break", "exsanguinate": "drop-half-bottom",
    "red_thirst": "drop-simple", "crimson_thorns": "arrow-fat-lines-up", "blood_rite": "pentagram", "coagulate": "bandaids", "blood_tempest": "cloud-rain",
    "first_light": "sparkle", "benison": "hands-praying", "sanctity": "seal-check", "seraph_wings": "feather", "chakram_of_dawn": "disc",
    "gilded_soul": "crown", "rapture": "caret-double-up", "sanctum": "columns", "radiant_soul": "star-four", "avatar_of_dawn": "crown-simple",
})


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
