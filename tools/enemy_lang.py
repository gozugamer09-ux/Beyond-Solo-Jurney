#!/usr/bin/env python3
"""
Writes the enemy roster's English texts to src/main/resources/assets/beyondsolo_enemies/lang/en_us.json.
(Minecraft loads lang files from every namespace, so the roster keeps its texts apart from the shared file.)

Run from the repo root:  python3 tools/enemy_lang.py
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
}

AFFINITIES = {"none": "", "flame": "Emberborn", "frost": "Frostbound", "venom": "Venomous", "storm": "Stormcharged",
              "shade": "Shadeborn", "blood": "Bloodsworn"}
VARIANTS = {"normal": "", "veteran": "Veteran", "elite": "Elite", "champion": "Champion"}
AFFIXES = {"swift": "Swift", "armored": "Armoured", "vampiric": "Vampiric", "frenzied": "Frenzied", "volatile": "Volatile",
           "splitting": "Splitting", "warded": "Warded", "thorned": "Thorned"}

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
