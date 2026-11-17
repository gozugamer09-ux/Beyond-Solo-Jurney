#!/usr/bin/env python3
"""
Writes the armory's English texts to src/main/resources/assets/beyondsolo_armory/lang/en_us.json: the 39 set names
and their 156 pieces (named after their art in tools/gen_armory_assets.py), the set perks, the divine weapon passives
and the set tooltips. Minecraft loads lang files from every namespace, so the armory keeps its texts apart.

Run from the repo root:  python3 tools/armory_lang.py
Spanish lives by hand in es_es.json next to it: add a line there for every new key (TranslationGameTests checks).
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/beyondsolo_armory/lang/en_us.json")

# set id: (display name, helmet, chestplate, leggings, boots)
SETS = {
    # village requests
    "forgehand": ("Forgehand", "Sallet", "Cuirass", "Greaves", "Sabatons"),
    "hearthguard": ("Hearthguard", "Bucket Helm", "Cuirass", "Greaves", "Sabatons"),
    "nightstitch": ("Nightstitch", "Hood", "Jerkin", "Leggings", "Boots"),
    "inkweave": ("Inkweave", "Hood", "Robe", "Skirts", "Wraps"),
    "chapelward": ("Chapelward", "Hood", "Vestment", "Skirts", "Wraps"),
    "fletchwood": ("Fletchwood", "Hood", "Fleece Mantle", "Leggings", "Boots"),
    # gate creatures
    "broodsilk": ("Broodsilk", "Mask", "Jerkin", "Leggings", "Wraps"),
    "gloomstalker": ("Gloomstalker", "Pelt Hood", "Mantle", "Leggings", "Boots"),
    "shardshell": ("Shardshell", "Crown", "Carapace", "Greaves", "Stompers"),
    "cinderwisp": ("Cinderwisp", "Mask", "Robe", "Skirts", "Wraps"),
    "duskfang": ("Duskfang", "Maw Helm", "Jerkin", "Leggings", "Boots"),
    "silkstrider": ("Silkstrider", "Hood", "Chitin Vest", "Leggings", "Wraps"),
    "bonechant": ("Bonechant", "Cowl", "Vestment", "Skirts", "Wraps"),
    "prismhorn": ("Prismhorn", "Helm", "Chestplate", "Greaves", "Sabatons"),
    "ashen_watch": ("Ashen Watch", "Helm", "Plate", "Greaves", "Stompers"),
    "pyre_wraith": ("Pyre Wraith", "Cowl", "Robe", "Skirts", "Cinderboots"),
    "hollow_hierarch": ("Hollow Hierarch", "Mask", "Vestment", "Skirts", "Wraps"),
    "moonmaw": ("Moonmaw", "Pelt Hood", "Ribbed Vest", "Leggings", "Boots"),
    "hollow_saint": ("Hollow Saint", "Halo", "Vestment", "Skirts", "Gilded Boots"),
    "ashen_bastion": ("Ashen Bastion", "Greathelm", "Breastplate", "Greaves", "Sabatons"),
    # bosses
    "silken_cradle": ("Silken Cradle", "Veil", "Vestment", "Skirts", "Wraps"),
    "fangcrest": ("Fangcrest", "Helm", "Mantle Plate", "Greaves", "Sabatons"),
    "hollow_vanguard": ("Hollow Vanguard", "Greathelm", "Breastplate", "Greaves", "Sabatons"),
    "sigilweaver": ("Sigilweaver", "Circlet Cowl", "Robe", "Skirts", "Wraps"),
    "frostmane": ("Frostmane", "Pelt Hood", "Coat", "Leggings", "Boots"),
    "rimebloom": ("Rimebloom", "Halo", "Robe", "Skirts", "Wraps"),
    "brood_empress": ("Brood Empress", "Crown", "Gown", "Skirts", "Wraps"),
    "oathbound": ("Oathbound", "Winged Helm", "Cuirass", "Greaves", "Sabatons"),
    "starbinder": ("Starbinder", "Circlet Cowl", "Vestment", "Skirts", "Gilded Boots"),
    "ashwing": ("Ashwing", "Drake Helm", "Coat", "Leggings", "Boots"),
    "hollowblade": ("Hollowblade", "Mask", "Harness", "Leggings", "Wraps"),
    "archmagus": ("Archmagus", "Circlet Cowl", "Mantle", "Leggings", "Gilded Boots"),
    "wyrmlord": ("Wyrmlord", "Helm", "Scale Plate", "Greaves", "Sabatons"),
    # divine
    "worldbreaker": ("Worldbreaker", "Helm", "Chestplate", "Greaves", "Sabatons"),
    "veilwalker": ("Veilwalker", "Cowl", "Coat", "Leggings", "Wraps"),
    "starweave": ("Starweave", "Circlet Cowl", "Robe", "Skirts", "Gilded Boots"),
    "eternal_bastion": ("Eternal Bastion", "Helm", "Breastplate", "Greaves", "Sabatons"),
    "lifebinder": ("Lifebinder", "Halo", "Breastplate", "Greaves", "Gilded Boots"),
    "skywarden": ("Skywarden", "Winged Helm", "Coat", "Leggings", "Boots"),
}
PIECES = ("helm", "chestplate", "leggings", "boots")

# four-piece perks (com.beyondsolo.armory.SetPerks): name, what it does
PERKS = {
    "venom_fangs": ("Venom Fangs", "your melee hits build venom"),
    "hunters_mark": ("Hunter's Mark", "your arrows mark foes for 6 s; marked foes take 15% more from your arrows"),
    "crystal_spines": ("Crystal Spines", "melee attackers take a fifth of the damage they deal you back as magic"),
    "cinder_burst": ("Cinder Burst", "your spells set foes burning"),
    "pounce": ("Pounce", "after a combat roll, your next melee strike within 3 s hits 35% harder and slows"),
    "webshot": ("Webshot", "your arrows slow foes; slowed foes take 10% more from your arrows"),
    "bone_ward": ("Bone Ward", "casting a skill shields you and the players around you (at most every 10 s)"),
    "prism_charge": ("Prism Charge", "a melee hit while sprinting strikes 30% harder and knocks back (every 4 s)"),
    "tower_guard": ("Tower Guard", "20% less damage from the front"),
    "pyre_burst": ("Pyre Burst", "foes you kill burst into flame, scorching everything around them"),
    "hollow_echo": ("Hollow Echo", "your spells have a 20% chance to echo for 35% of their damage a moment later"),
    "moonlit_hunt": ("Moonlit Hunt", "your arrows hit 20% harder in the dark; every 5th arrow that lands looses a moon fang (magic and bleeding)"),
    "saints_grace": ("Saint's Grace", "a player near you who falls under 35% health is healed for 30% of it (every 45 s)"),
    "ashen_bulwark": ("Ashen Bulwark", "25% less damage from the front; taking a hit draws the foes around you onto you (every 5 s)"),
    "cradle_silk": ("Cradle Silk", "casting a skill heals the most hurt player near you and hardens them for 3 s (every 8 s)"),
    "alphas_howl": ("Alpha's Howl", "every kill gives you Strength and Speed for 4 s"),
    "vanguard_cleave": ("Vanguard Cleave", "every 3rd melee hit cleaves the foes in front of you for half its damage"),
    "storm_sigil": ("Storm Sigil", "your spells have a 25% chance to arc lightning for 30% of their damage"),
    "frostbite_arrows": ("Frostbite Arrows", "your arrows build frostbite"),
    "rime_bloom": ("Rime Bloom", "casting a skill frostbites the foes within 5 blocks and heals the players around you (every 6 s)"),
    "empress_venom": ("Empress Venom", "your melee hits build heavy venom; a poisoned foe you kill spills it onto everything near it"),
    "oath_of_iron": ("Oath of Iron", "25% less damage while sneaking or blocking; players within 6 blocks of you take 10% less"),
    "starbinding": ("Starbinding", "every 8 s a star thread mends the most hurt player near you (Regeneration II)"),
    "ashfall": ("Ashfall", "your arrows build burning; critical arrows burst into flame around the target"),
    "phantom_blade": ("Phantom Blade", "after a kill or a combat roll, a phantom blade echoes your next melee strike for half its damage and crimson"),
    "arcane_cascade": ("Arcane Cascade", "each skill cast adds a stack for 6 s (+5% spell damage, up to 5); the fifth releases an arcane nova"),
    "dragons_blood": ("Dragon's Blood", "your melee hits build burning; under half health they hit 20% harder and heal you for 5% of the damage"),
    # divine sets
    "warlords_fury": ("Warlord's Fury", "every kill adds a stack of fury for 10 s: +6% damage each, up to 5"),
    "shadow_veil": ("Shadow Veil", "a 15% chance to slip a hit entirely; your next strike within 3 s hits 60% harder and makes the foe bleed"),
    "arcane_overflow": ("Arcane Overflow", "every 4th skill cast within 20 s overflows into a starlight nova around you"),
    "sanctuary": ("Sanctuary", "you and the players within 8 blocks take 15% less damage; under 40% health you shield everyone around you (every 60 s)"),
    "overflowing_grace": ("Overflowing Grace", "every 6 s you heal the players within 10 blocks; healing past full health becomes a shield (up to 3 hearts)"),
    "windwalker": ("Windwalker", "your arrows hit 20% harder beyond 20 blocks; after a combat roll or 2 s of sprinting your next arrow flies 40% harder, always critical"),
}

# divine weapon passives (com.beyondsolo.armory.DivinePassives)
PASSIVES = {
    "ruin": ("Ruin", "Every melee hit marks a foe; the fifth mark detonates in fire around it"),
    "vanishing_edge": ("Vanishing Edge", "A kill hides you for 3 s; your next strike within 5 s hits 80% harder"),
    "insatiable": ("Insatiable", "Each kill feeds it for 30 s, up to 10: +3% spell damage for every kill fed"),
    "unyielding": ("Unyielding", "Once every 90 s a killing blow leaves you at 1 health instead, braced and drawing every foe near you"),
    "rootbound_life": ("Rootbound Life", "Players within 10 blocks who fall under 30% health are caught by roots and healed for a quarter of it (each once every 30 s)"),
    "astral_quiver": ("Astral Quiver", "Every 4th full draw looses a star arrow: +50% damage and a burst of starlight where it lands"),
}

STATS = {
    "generic.armor": "Armor",
    "generic.armor_toughness": "Armor Toughness",
    "generic.attack_damage": "Attack Damage",
    "generic.knockback_resistance": "Knockback Resistance",
    "generic.max_health": "Max Health",
    "generic.movement_speed": "Speed",
    "crit_chance": "Crit Chance",
    "crit_damage": "Crit Damage",
    "dodge_chance": "Dodge Chance",
    "arrow_damage": "Arrow Damage",
    "draw_speed": "Draw Speed",
    "healing_received": "Healing Received",
    "fire_resistance": "Fire Resistance",
    "poison_resistance": "Poison Resistance",
    "crimson_resistance": "Crimson Resistance",
    "blood_resistance": "Blood Resistance",
    "exhaustion_resistance": "Exhaustion Resistance",
    "elemental_power": "Elemental Power",
    "spell_power": "Spell Power",
    "mana_efficiency": "Mana Efficiency",
    "skill_haste": "Skill Haste",
}

VILLAGERS = {
    "forgehand": "Reward for weaponsmiths' and toolsmiths' requests",
    "hearthguard": "Reward for armorers' and masons' requests",
    "nightstitch": "Reward for leatherworkers' and butchers' requests",
    "inkweave": "Reward for librarians' and cartographers' requests",
    "chapelward": "Reward for clerics' and farmers' requests",
    "fletchwood": "Reward for fletchers', fishermen's and shepherds' requests",
}

TEXTS = {
    "itemGroup.beyondsolo.armory": "System Armory",
    "attribute.beyondsolo.mana_efficiency": "Mana Efficiency",
    "attribute.beyondsolo.skill_haste": "Skill Haste",
    "tooltip.beyondsolo.set_of": "%s set",
    "tooltip.beyondsolo.set_class": "For the %s",
    "tooltip.beyondsolo.set_level": "Needs level %s",
    "tooltip.beyondsolo.set_bound": "Bound to the %s: no one else can wear it",
    "tooltip.beyondsolo.set_unattuned": "Only a %s draws out its bonuses",
    "tooltip.beyondsolo.set_two": "2 pieces: %s",
    "tooltip.beyondsolo.set_four": "4 pieces: %s",
    "tooltip.beyondsolo.set_source.creature": "Rare drop: %s, %s-rank gates",
    "tooltip.beyondsolo.set_source.divine": "Divine: a sliver of a chance from S-rank bosses",
    "tooltip.beyondsolo.divine_passive": "Divine passive: %s",
    "message.beyondsolo.armor_bound": "Only a %s can wear this divine armor",
    "message.beyondsolo.armor_level": "You need level %s to wear this (you are level %s)",
    "message.beyondsolo.set_unattuned": "%s is made for the %s: its bonuses stay dormant",
    "message.beyondsolo.set_two": "%s: 2-piece bonus active",
    "notify.beyondsolo.set.title": "%s set complete",
    "perk.beyondsolo.warlords_fury.stacks": "Warlord's Fury ×%s",
    "passive.beyondsolo.insatiable.stacks": "Insatiable ×%s (+%s%% spell damage)",
}


def build():
    out = dict(TEXTS)
    for set_id, (name, *pieces) in SETS.items():
        out["set.beyondsolo." + set_id] = name
        for piece, label in zip(PIECES, pieces):
            out["item.beyondsolo.%s_%s" % (set_id, piece)] = name + " " + label
    for key, (name, desc) in PERKS.items():
        out["perk.beyondsolo." + key] = name
        out["perk.beyondsolo." + key + ".desc"] = desc
    for key, (name, desc) in PASSIVES.items():
        out["passive.beyondsolo." + key] = name
        out["passive.beyondsolo." + key + ".desc"] = desc
    for path, name in STATS.items():
        out["armory.beyondsolo.stat." + path] = name
    for set_id, text in VILLAGERS.items():
        out["tooltip.beyondsolo.set_source.villager." + set_id] = text
    return out


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    texts = build()
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(texts, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("armory texts written:", len(texts))


if __name__ == "__main__":
    main()
