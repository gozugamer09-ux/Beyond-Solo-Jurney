#!/usr/bin/env python3
"""
Writes the enemy roster's English texts to src/main/resources/assets/beyondsolo_enemies/lang/en_us.json.
(Minecraft loads lang files from every namespace, so the roster keeps its texts apart from the shared file.)

Run from the repo root:  python3 tools/enemy_lang.py
Spanish lives by hand in es_es.json next to it: add a line there for every new key (TranslationGameTests checks).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/beyondsolo_enemies/lang/en_us.json")

MONSTERS = {
    "gloomfang": "Gloomfang",
    "broodling": "Broodling",
    "shardback": "Shardback",
    "hollow_acolyte": "Hollow Acolyte",
    "ashen_sentinel": "Ashen Sentinel",
    "emberwisp": "Emberwisp",
    "gutterkin": "Gutterkin",
    "grave_thrall": "Grave Thrall",
    "bone_archer": "Bone Archer",
    "rime_stalker": "Rime Stalker",
    "tuskbrute": "Tuskbrute",
    "tusk_shaman": "Tusk Shaman",
    "deepkin": "Deepkin",
    "duskwing": "Duskwing",
    "hive_drone": "Hive Drone",
    "brimling": "Brimling",
    "wailing_wraith": "Wailing Wraith",
    "static_drifter": "Static Drifter",
    "rimeclaw": "Rimeclaw",
    "pit_ravager": "Pit Ravager",
    "coilfang": "Coilfang",
    "cragcrawler": "Cragcrawler",
    "hive_reaver": "Hive Reaver",
    "rune_colossus": "Rune Colossus",
    "thorn_spitter": "Thorn Spitter",
}

AFFINITIES = {"none": "", "flame": "Emberborn", "frost": "Frostbound", "venom": "Venomous", "storm": "Stormcharged",
              "shade": "Shadeborn", "blood": "Bloodsworn"}
VARIANTS = {"normal": "", "veteran": "Veteran", "elite": "Elite", "champion": "Champion"}
AFFIXES = {"swift": "Swift", "armored": "Armoured", "vampiric": "Vampiric", "frenzied": "Frenzied", "volatile": "Volatile",
           "splitting": "Splitting", "warded": "Warded", "thorned": "Thorned"}
# the colour a creature's name is shown in, by how it stands against you (enemy.HiddenLevel.Threat)
THREATS = {"weaker": "white", "even": "orange", "far_stronger": "red"}

# one name per rank, E to S
BOSSES = {
    "broodmother": ["Silkhollow Matron", "Mirefang Broodqueen", "Venomveil Mother", "Thousand-Eye Weaver", "Plaguecrown Matriarch", "-"],
    "alpha": ["Rimefang Alpha", "Shardmane Packlord", "Glacier Howler", "Winterhowl Sovereign", "-", "-"],
    "knight": ["-", "Hollow Squire", "Gravewarden Knight", "Duskblade Champion", "Oathless Paladin", "The Last Knight of Ash"],
    "magus": ["-", "-", "Veiled Stormcaller", "Masked Tempest", "Thunder Hierophant", "The Faceless Sky-King"],
    "wyrm": ["-", "-", "-", "Cinder Drake", "Ashwing Wyrm", "Emberheart Calamity"],
}
RANKS = ["e", "d", "c", "b", "a", "s"]


def main():
    out = {}
    for k, v in MONSTERS.items():
        out["entity.beyondsolo." + k] = v
        out["item.beyondsolo." + k + "_spawn_egg"] = v + " Spawn Egg"
    out["entity.beyondsolo.element_orb"] = "Element Bolt"
    for k, v in AFFINITIES.items():
        out["enemy.beyondsolo.affinity." + k] = v
    for k, v in VARIANTS.items():
        out["enemy.beyondsolo.variant." + k] = v
    for k, v in AFFIXES.items():
        out["enemy.beyondsolo.affix." + k] = v
    # affinity + creature on the nameplate; Spanish turns it round ("%2$s %1$s")
    out["enemy.beyondsolo.named"] = "%s %s"
    for k, v in THREATS.items():
        out["enemy.beyondsolo.threat." + k] = v
    # the System's word the first time a hunter sees creatures' names; the three colours come in coloured
    out["notify.beyondsolo.creature_names.title"] = "Creature Names"
    out["notify.beyondsolo.creature_names.body"] = ("The System reads every creature you meet and colours its name by how it stands against you: "
                                                   "%s if it is weaker, %s if it is about your level or a little above, %s if it is far stronger.")
    for kind, names in BOSSES.items():
        for rank, name in zip(RANKS, names):
            # bosses outside their rank range never appear there; give them the nearest name anyway for commands
            if name == "-":
                name = next(n for n in (names if RANKS.index(rank) < 3 else reversed(names)) if n != "-")
            out["gate.beyondsolo.boss." + kind + "." + rank] = name
    out["gate.beyondsolo.boss_ascended"] = "Ascended %s"
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {len(out)} keys to {os.path.normpath(OUT)}")


if __name__ == "__main__":
    main()
