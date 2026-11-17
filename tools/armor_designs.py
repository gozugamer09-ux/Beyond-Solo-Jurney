"""
The look of every armor set: palette, helmet, chest, leggings, boots and the extra 3D parts each one wears.
Painted by tools/armor_worn.py (worn layers + part geometry) and tools/armor_icons.py (item icons); written to the
game by tools/gen_armory_assets.py.

A design: {"pal": {key: "rrggbb" or ("rrggbb", ramp kind)}, "head": {...}, "chest": {...}, "legs": {...}, "feet": {...}}
Each piece names a painter ("kind") and its options; "extras" lists the extra parts (armor_worn.EXTRAS) with theirs.
Palette keys: metal, trim, cloth, cloth2, lining, leather, leather2, mail, fur, bone, gem, glow, dark, scale, stone,
crystal, thread. Kinds default per key (armor_worn.DEFAULT_KIND).

Class looks: warriors are steel and war-red plate with tabards and pauldrons; guardians the heaviest plate, tower
helms, shields and capes; assassins dark leather, hoods, masks and scarves; mages robes, tall hats, circlets and
glowing sigils; healers white and gold vestments, stoles and halos; rangers leather, fur, hoods, wolf pelts and
quivers. Higher grades add glow, gems, wings, horns and capes.
"""

D = {}

# =============================================================================================== warrior

D["forgehand"] = {   # E: a smith's kettle helm and a forge-red surcoat
    "pal": {"metal": "8d939c", "trim": ("b8823e", "gold"), "cloth": "b03a26", "leather": "6a4630", "leather2": "3e2a1e",
            "mail": "50545e", "dark": "18161e", "thread": "d8c49a"},
    "head": {"kind": "helm", "style": "kettle", "face": "open", "wear": True, "band_y": 6,
             "extras": [("brim", dict(key="metal", trim="trim", width=2.0, y=-5.25))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "round", "tabard": "long", "tabard_key": "cloth", "tabard_trim": "trim",
              "wear": True, "emblem": "anvil", "emblem_y": 6, "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3))]},
    "legs": {"kind": "plate", "under": "mail"},
    "feet": {"kind": "leather", "shin": "metal", "extras": [("toes", dict(key="metal"))]},
}

D["fangcrest"] = {   # rare boss: a frost wolf's pelt over blued steel
    "pal": {"metal": "5f6f82", "trim": ("dceaf5", "bone"), "bone": "dceaf5", "fur": "9aa4b0", "cloth": "2c3e58",
            "leather": "3a3440", "mail": "3a4250", "glow": "9ad8ff", "dark": "10141c"},
    "head": {"kind": "helm", "style": "barbute", "trim": "trim", "eyes": False,
             "extras": [("wolf", dict(fur_key="fur", tooth="bone", inner="leather"))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "fang", "emblem_key": "trim",
              "pauldron_rows": 0, "extras": [("fur_mantle", dict(key="fur")),
                                             ("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=2))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "extras": [("tassets", dict(key="metal", trim="trim", length=4))]},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("boot_spikes", dict(key="bone"))]},
}

D["prismhorn"] = {   # epic: dark steel grown through with ice crystal
    "pal": {"metal": ("34425e", "dark"), "trim": ("c8f0ff", "crystal"), "crystal": "9ad8ff", "glow": "9ad8ff", "cloth": "1e2a44",
            "mail": "262e3e", "leather": "2a2a36", "dark": "080a12"},
    "head": {"kind": "helm", "style": "sallet", "eyes": True, "trim": "trim", "ridge_key": "trim",
             "extras": [("horns", dict(key="crystal", style="up")), ("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "gem": True, "runes": 3, "pauldron_rows": 0,
              "cracks": "glow", "crack_n": 3,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=0, size=1.1)), ("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 2},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("toes", dict(key="crystal"))]},
}

D["hollow_vanguard"] = {   # epic boss: a hollow knight's black-violet plate
    "pal": {"metal": "5c6070", "trim": ("2e2c3a", "dark"), "cloth": "4a3484", "glow": "a080ff", "mail": "2a2a36", "leather": "2a2430",
            "dark": "07060c", "gem": "a080ff"},
    "head": {"kind": "helm", "style": "greathelm", "eyes": True, "trim": "trim", "ridge_key": "trim",
             "extras": [("crest", dict(key="trim"))]},
    "chest": {"kind": "plate", "under": "mail", "tabard": "full", "tabard_key": "cloth", "tabard_trim": "trim", "trim": "trim",
              "emblem": "eye", "emblem_key": "trim", "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=3, size=1.1)),
                         ("cape", dict(key="cloth", trim="trim", ragged=True))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim"},
    "feet": {"kind": "plate", "trim": "trim", "extras": [("boot_spikes", dict(key="metal")), ("toes", dict(key="metal"))]},
}

D["wyrmlord"] = {   # mythic: a dragon knight, red scale under gold-edged plate, membrane wings
    "pal": {"metal": ("5a2220", "metal"), "trim": ("ffb43a", "gold"), "scale": "6a2a22", "glow": "ff7a2e", "cloth": "8a1e14",
            "leather": "3a1a16", "bone": "f0dcb0", "gem": "ff5a1e", "dark": "100606", "membrane": "8a2a1e"},
    "head": {"kind": "helm", "style": "barbute", "trim": "trim", "ridge_key": "trim", "gem": True, "gem_y": 1,
             "extras": [("horns", dict(key="bone", style="bull", size=1.1))]},
    "chest": {"kind": "plate", "under": "scale", "shape": "pointed", "trim": "trim", "skirt": "scale", "emblem": "flame",
              "emblem_key": "trim", "emblem_glow": 0.8, "gem": True, "gem_y": 3, "pauldron_rows": 0, "cracks": "glow", "crack_n": 3,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=3, size=1.15, gem_=True)),
                         ("back_wings", dict(key="cloth", key2="trim", membrane=True))]},
    "legs": {"kind": "plate", "under": "scale", "trim": "trim", "extras": [("tassets", dict(key="scale", trim="trim", length=5, kind_="scale"))]},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "cracks": "glow",
             "extras": [("boot_spikes", dict(key="trim")), ("toes", dict(key="metal", trim="trim", long=2.0))]},
}

D["worldbreaker"] = {   # divine: white-gold plate, bull horns, a war banner cape
    "pal": {"metal": "d8d0c0", "trim": ("ffd36a", "gold"), "cloth": "c8641a", "glow": "ffb040", "mail": "8a8478", "leather": "6a4a2a",
            "bone": "f4ecd8", "gem": "ffb040", "dark": "1a1410"},
    "head": {"kind": "helm", "style": "cheek", "trim": "trim", "ridge_key": "trim", "gem": True, "gem_y": 1, "etch": True, "etch_key": "trim",
             "extras": [("horns", dict(key="trim", style="bull", size=1.2))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "sun", "emblem_key": "trim",
              "emblem_glow": 0.6, "gem": True, "gem_y": 3, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=3, size=1.2, layers=2, gem_=True)),
                         ("cape", dict(key="cloth", trim="trim", emblem_="sun"))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "etch": True,
             "extras": [("tassets", dict(key="metal", trim="trim", length=5)), ("knee", dict(key="trim"))]},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True,
             "extras": [("boot_spikes", dict(key="trim")), ("toes", dict(key="metal", trim="trim"))]},
}

# =============================================================================================== guardian

D["hearthguard"] = {   # E: a village shieldbearer, blue surcoat and plume
    "pal": {"metal": "7d848c", "trim": ("c8a050", "gold"), "cloth": "2f5f94", "leather": "5a3e2c", "mail": "4a505a",
            "dark": "14161e"},
    "head": {"kind": "helm", "style": "cheek", "aventail": True, "cheek_rivets": True,
             "extras": [("plume", dict(key="cloth", tall=3.5))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "tabard": "full", "tabard_key": "cloth", "tabard_trim": "trim",
              "emblem": "shield", "emblem_y": 6, "emblem_key": "trim", "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.1, layers=2))]},
    "legs": {"kind": "plate", "under": "mail", "extras": [("tassets", dict(key="metal", trim="trim", length=4))]},
    "feet": {"kind": "plate", "extras": [("toes", dict(key="metal", trim="trim"))]},
}

D["shardshell"] = {   # rare: slabs of stone with ice crystal growing out of them
    "pal": {"metal": ("6a7682", "stone"), "trim": ("8ad8ff", "crystal"), "crystal": "8ad8ff", "glow": "8ad8ff", "gem": "5ac8ff",
            "mail": ("3e4650", "stone"), "leather": "3a3a40", "cloth": "2a3a4a", "dark": "0c1016"},
    "head": {"kind": "helm", "style": "tower", "eyes": True, "trim": "trim", "rivets": False,
             "extras": [("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "plate", "under": "mail", "trim": "trim", "gem": True, "gem_y": 5, "pauldron_rows": 0, "cracks": "glow",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=0, size=1.2)), ("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 2},
    "feet": {"kind": "plate", "trim": "trim", "cracks": "glow", "crack_n": 2, "extras": [("toes", dict(key="metal"))]},
}

D["ashen_watch"] = {   # epic: ash-grey watch plate with ember seams and a pale surcoat
    "pal": {"metal": "6a6560", "trim": ("b0782a", "gold"), "cloth": "d8c8b0", "glow": "ff7a3a", "gem": "ff6a2a", "mail": "3e3a38",
            "leather": "4a3426", "dark": "120c0a"},
    "head": {"kind": "helm", "style": "tower", "eyes": True, "trim": "trim", "gem": True, "gem_y": 1},
    "chest": {"kind": "plate", "under": "mail", "tabard": "long", "tabard_key": "cloth", "tabard_trim": "trim", "trim": "trim",
              "emblem": "tower", "emblem_key": "trim", "pauldron_rows": 0, "cracks": "glow", "crack_n": 3,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.2, layers=2, glow=True))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 2,
             "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
    "feet": {"kind": "plate", "trim": "trim", "extras": [("toes", dict(key="metal", trim="trim"))]},
}

D["ashen_bastion"] = {   # legendary: black iron fortress, molten seams, ember eyes
    "pal": {"metal": ("3e3a38", "dark"), "trim": ("e09a3a", "gold"), "cloth": "5a1e10", "glow": "ff8a3a", "gem": "ff6a2a",
            "mail": "24201e", "leather": "2a1e18", "dark": "0a0605"},
    "head": {"kind": "helm", "style": "greathelm", "eyes": True, "trim": "trim", "ridge_key": "trim", "cracks": "glow", "crack_n": 3,
             "extras": [("horns", dict(key="trim", style="ram"))]},
    "chest": {"kind": "plate", "under": "mail", "trim": "trim", "gem": True, "gem_y": 4, "pauldron_rows": 0, "cracks": "glow",
              "crack_n": 6, "skirt": "lames",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.25, layers=2, spikes=2, glow=True)),
                         ("collar", dict(key="metal", high=3.0))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 3,
             "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
    "feet": {"kind": "plate", "trim": "trim", "cracks": "glow", "glow_line": True, "extras": [("toes", dict(key="metal", trim="trim"))]},
}

D["oathbound"] = {   # legendary boss: a paladin's silver and gold, white wings and cape
    "pal": {"metal": "c8ccd4", "trim": ("e0b44c", "gold"), "cloth": "f0f0f4", "cloth2": "2a4a8a", "glow": "fff0c0", "mail": "6a6e78",
            "leather": "5a4232", "gem": "6ab0ff", "dark": "12141c", "feather": "ffffff"},
    "head": {"kind": "helm", "style": "cheek", "trim": "trim", "ridge_key": "trim", "gem": True, "gem_y": 1, "etch": True,
             "etch_key": "trim", "extras": [("wings", dict(key="cloth", key2="trim"))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "tabard": "long", "tabard_key": "cloth2",
              "tabard_trim": "trim", "emblem": "sun", "emblem_key": "trim", "emblem_glow": 0.5, "pauldron_rows": 0, "etch": True,
              "etch_key": "trim",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.15, layers=2, gem_=True)),
                         ("cape", dict(key="cloth", trim="trim", lining="cloth2"))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
    "feet": {"kind": "plate", "trim": "trim", "extras": [("heel_wings", dict(key="cloth", key2="trim")), ("toes", dict(key="metal", trim="trim"))]},
}

D["eternal_bastion"] = {   # divine: white-gold tower helm, navy war cloak, huge pauldrons
    "pal": {"metal": "f0ead8", "trim": ("ffcf5a", "gold"), "cloth": "2a4a8a", "cloth2": "6ab0ff", "glow": "fff3c0", "gem": "6ab0ff",
            "mail": "8a8676", "leather": "5a4430", "dark": "141a2a"},
    "head": {"kind": "helm", "style": "tower", "eyes": True, "trim": "trim", "ridge_key": "trim", "gem": True, "gem_y": 1,
             "etch": True, "etch_key": "trim", "extras": [("plume", dict(key="cloth", tall=4.0)), ("crest", dict(key="trim"))]},
    "chest": {"kind": "plate", "under": "mail", "trim": "trim", "emblem": "tower", "emblem_key": "trim", "emblem_glow": 0.5,
              "gem": True, "gem_y": 3, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.3, layers=2, gem_=True)),
                         ("cape", dict(key="cloth", trim="trim", emblem_="tower")), ("collar", dict(key="trim", high=2.5))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "etch": True,
             "extras": [("tassets", dict(key="metal", trim="trim", length=5)), ("knee", dict(key="trim"))]},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("toes", dict(key="metal", trim="trim"))]},
}

# =============================================================================================== assassin

D["nightstitch"] = {   # E: a cutpurse's cowl, wine scarf, knife on the chest
    "pal": {"leather": "4a4250", "leather2": "2a2430", "trim": ("9a9aac", "metal"), "cloth": "2c2832", "cloth2": "7a2c40",
            "dark": "100e14", "thread": "a08a98"},
    "head": {"kind": "hood", "style": "cowl", "cloth": "cloth", "mask": "cloth2", "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "leather", "under": "cloth", "scarf": "cloth2", "knife": True, "straps": [(-8, 3, 8, 10)],
              "extras": [("scarf", dict(key="cloth2", length=9))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather2", "shin": "trim"},
}

D["broodsilk"] = {   # E gate set: grey spider silk and a six-eyed bone mask
    "pal": {"cloth": "5a5a6a", "cloth2": "8ee05a", "bone": "d8d8e0", "trim": ("d8d8e0", "bone"), "glow": "9cff5a", "leather": "34343e",
            "leather2": "24242c", "dark": "0a0c08", "scale": "3a3a46", "thread": "c8c8d0"},
    "head": {"kind": "bone_mask", "mask": "visage", "eyes": 6, "cloth": "cloth"},
    "chest": {"kind": "leather", "under": "cloth", "leather": "leather", "scarf": "cloth", "glow_seams": True,
              "extras": [("spider_legs", dict(key="scale", n=2, length=8.0))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather2"},
}

D["duskfang"] = {   # rare: a fanged jaw mask, violet scarf, spiked boots
    "pal": {"leather": "3a3248", "leather2": "221c2c", "cloth": "2a2436", "cloth2": "6a4ab0", "bone": "d0d0e0", "trim": ("c8c8d8", "bone"),
            "glow": "b88aff", "dark": "0a0810", "thread": "9a8ab8", "metal": "8a88a0"},
    "head": {"kind": "bone_mask", "mask": "jaw", "cloth": "cloth", "extras": [("ears", dict(fur_key="cloth", inner="cloth2"))]},
    "chest": {"kind": "leather", "under": "cloth", "scarf": "cloth2", "knife": True, "studs": True, "glow_seams": True,
              "straps": [(-8, 2, 8, 11), (8, 2, -8, 11)], "extras": [("scarf", dict(key="cloth2", length=10))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "leather", "fold": True, "extras": [("boot_spikes", dict(key="bone"))]},
}

D["brood_empress"] = {   # legendary: the spider queen's chitin robe, bone crown and legs
    "pal": {"cloth": "3a2a40", "cloth2": "a0ff5a", "scale": "4a3456", "bone": "d8c8f0", "trim": ("d8c8f0", "bone"), "glow": "a0ff5a",
            "gem": "a0ff5a", "leather": "2a2030", "dark": "0a060c", "lining": "24182a"},
    "head": {"kind": "bone_mask", "mask": "visage", "eyes": 6, "cloth": "cloth", "hat": [("crown", dict(key="trim", glow=0.8))]},
    "chest": {"kind": "robe", "cloth": "scale", "mantle_key": "cloth", "inner": "cloth2", "trim": "trim", "runes": True, "gem": True,
              "extras": [("spider_legs", dict(key="scale", n=2, length=10.0)), ("collar", dict(key="trim", high=3.0))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "scale", "trim": "trim",
             "extras": [("skirt", dict(key="cloth", trim="trim", length=8, panel="scale"))]},
    "feet": {"kind": "wraps", "cloth": "scale", "leather": "leather"},
}

D["hollowblade"] = {   # mythic: a black blade-dancer, steel face mask, crimson eyes and scarf
    "pal": {"leather": "26242e", "leather2": "141218", "metal": ("9a9ab4", "metal"), "trim": ("c0c0d8", "metal"), "cloth": "1a1820",
            "cloth2": "c4203c", "glow": "ff4a6a", "dark": "060508", "thread": "c4203c"},
    "head": {"kind": "hood", "style": "cowl", "cloth": "cloth", "mask": "leather2", "mask_trim": "trim",
             "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "leather", "under": "cloth", "scarf": "cloth2", "knife": True, "studs": True, "glow_seams": True,
              "straps": [(-8, 2, 8, 11)], "extras": [("scarf", dict(key="cloth2", length=12)),
                                                    ("pauldrons", dict(key="metal", trim="cloth2", lame=0, size=0.8, spikes=2))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather2", "shin": "metal", "extras": [("boot_spikes", dict(key="metal"))]},
}

D["veilwalker"] = {   # divine: a void-faced reaper in gold-edged black, a long ragged veil
    "pal": {"cloth": "1a1a28", "cloth2": "e0306e", "trim": ("fff3c0", "gold"), "glow": "ff6aa0", "leather": "1e1a26", "leather2": "120e18",
            "dark": "040306", "lining": "3a1028", "gem": "ff6aa0", "thread": "ffd8a0"},
    "head": {"kind": "hood", "style": "reaper", "cloth": "cloth", "circlet": "trim", "trim": "trim",
             "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "cloth", "inner": "cloth2", "trim": "trim", "runes": True, "ornate": True,
              "extras": [("cape", dict(key="cloth", trim="trim", lining="lining", ragged=True, length=19))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather2", "shin": "trim"},
}

# =============================================================================================== mage

D["inkweave"] = {   # E: the scribe's pointed hat and navy robe
    "pal": {"cloth": "2e3a6a", "cloth2": "e8e0c8", "trim": ("c8b070", "gold"), "glow": "9ab8ff", "leather": "4a3628", "gem": "7f9cff"},
    "head": {"kind": "bare", "cloth": "cloth", "rows": 4, "band": "trim",
             "extras": [("wizard_hat", dict(key="cloth", trim="trim"))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "cloth", "inner": "cloth2", "runes": True},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "cloth2"},
    "feet": {"kind": "wraps", "cloth": "cloth2", "leather": "leather"},
}

D["cinderwisp"] = {   # rare: ember-stitched robes and a bronze half-mask
    "pal": {"cloth": "3a2622", "cloth2": "ff8a2a", "trim": ("b06a3a", "gold"), "bone": ("b06a3a", "metal"), "glow": "ffa030",
            "leather": "2a1a16", "dark": "0c0604", "gem": "ff8a2a", "lining": "5a2a1a"},
    "head": {"kind": "bone_mask", "mask": "visage", "bone": "bone", "cloth": "cloth"},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "lining", "inner": "cloth2", "trim": "trim", "runes": True, "gem": True,
              "extras": [("crystals", dict(key="cloth2", where="shoulder"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["pyre_wraith"] = {   # epic: a burning wraith, hood of shadow, molten wraps
    "pal": {"cloth": "2a1e24", "cloth2": "ff8a2a", "trim": ("ff9a3a", "gold"), "glow": "ff8a2a", "leather": "1a1216", "dark": "060304",
            "lining": "5a1a10", "crystal": "ffb040"},
    "head": {"kind": "hood", "style": "reaper", "cloth": "cloth", "trim": "trim", "extras": [("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "cloth", "inner": "cloth2", "trim": "trim", "runes": True, "ornate": True,
              "extras": [("cape", dict(key="cloth", trim="trim", lining="lining", ragged=True))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather", "shin": "trim"},
}

D["hollow_hierarch"] = {   # epic: violet vestments, a bone visage and a crown of bone
    "pal": {"cloth": "5a4a78", "cloth2": "c8a8ff", "bone": "e8dcc0", "trim": ("e8dcc0", "bone"), "glow": "c8a8ff", "leather": "2e2438",
            "dark": "0a0810", "lining": "3a2e50", "gem": "c8a8ff"},
    "head": {"kind": "bone_mask", "mask": "visage", "cloth": "cloth", "hat": [("crown", dict(key="trim"))]},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "lining", "stole_key": "trim", "trim": "trim", "emblem": "eye",
              "emblem_glow": 0.6, "extras": [("collar", dict(key="trim", high=3.5))]},
    "legs": {"kind": "cloth", "cloth": "lining", "panel": "cloth", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["sigilweaver"] = {   # epic boss: a sigil-covered robe, rune circlet, floating crystals
    "pal": {"cloth": "2a3a6a", "cloth2": "b8e2ff", "trim": ("b8e2ff", "metal"), "glow": "b8e2ff", "crystal": "7f9cff", "gem": "7f9cff",
            "leather": "1e2440", "lining": "1a2448", "dark": "060810"},
    "head": {"kind": "bare", "cloth": "trim", "rows": 3, "gem": True, "extras": [("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "lining", "inner": "cloth2", "trim": "trim", "runes": True, "ornate": True,
              "gem": True, "emblem": "rune", "emblem_glow": 0.9,
              "extras": [("collar", dict(key="trim", high=3.0)), ("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather", "shin": "trim"},
}

D["archmagus"] = {   # mythic: violet and gold, a towering hat, star-blue sigils, deep cape
    "pal": {"cloth": "3a1a5a", "cloth2": "7f9cff", "trim": ("ffd166", "gold"), "glow": "9ab0ff", "gem": "7f9cff", "lining": "241040",
            "leather": "2a1a30", "dark": "08040c", "crystal": "9ab0ff"},
    "head": {"kind": "bare", "cloth": "cloth", "rows": 4, "band": "trim", "gem": True,
             "extras": [("wizard_hat", dict(key="cloth", trim="trim", band="trim", droop=1.2, brim=3.0))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "lining", "inner": "cloth2", "trim": "trim", "runes": True, "ornate": True,
              "gem": True, "emblem": "star", "emblem_glow": 0.8,
              "extras": [("collar", dict(key="trim", high=3.5)), ("cape", dict(key="lining", trim="trim", emblem_="star", length=19)),
                         ("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather", "shin": "trim"},
}

D["starweave"] = {   # divine: white starcloth, gold and a deep-blue star hood
    "pal": {"cloth": "e6e9f5", "cloth2": "3a4ac0", "trim": ("ffd36a", "gold"), "glow": "c8d8ff", "gem": "4a5ad0", "lining": "2a3488",
            "leather": "3a3a5a", "dark": "0a0c1a", "crystal": "c8d8ff"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth2", "circlet": "trim", "rim": "trim",
             "extras": [("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "cloth2", "inner": "lining", "trim": "trim", "runes": True, "ornate": True,
              "gem": True, "emblem": "star", "emblem_glow": 0.9,
              "extras": [("cape", dict(key="cloth2", trim="trim", lining="lining", emblem_="star", length=18))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "cloth2", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather", "shin": "trim", "extras": [("heel_wings", dict(key="cloth", key2="trim"))]},
}

# =============================================================================================== healer

D["chapelward"] = {   # E: a chapel acolyte's linen hood, green stole, gold sun
    "pal": {"cloth": "e6dcc8", "cloth2": "5e8e50", "trim": ("c8a050", "gold"), "glow": "fff0b0", "leather": "8a6a4a", "lining": "c8bca8"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth", "circlet": "trim"},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "cloth", "stole_key": "cloth2", "emblem": "sun"},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "cloth2"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather"},
}

D["bonechant"] = {   # rare: a bone-masked chanter in ash grey, pale green light
    "pal": {"cloth": "4a4a52", "cloth2": "b8e0a0", "bone": "e8dcc0", "trim": ("e8dcc0", "bone"), "glow": "c8ffb0", "leather": "2e2e34",
            "dark": "08080a", "lining": "34343a"},
    "head": {"kind": "bone_mask", "mask": "skull", "cloth": "cloth", "extras": [("horns", dict(key="bone", style="ram", size=0.9))]},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "lining", "stole_key": "trim", "trim": "trim", "emblem": "skull",
              "emblem_glow": 0.4},
    "legs": {"kind": "cloth", "cloth": "lining", "panel": "cloth", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["silken_cradle"] = {   # rare boss: lavender silk, a six-eyed mask, cocooned shoulders
    "pal": {"cloth": "d8d0e0", "cloth2": "9cff7a", "bone": "c8b8e0", "trim": ("c8b8e0", "bone"), "glow": "b8ff9a", "leather": "6a6078",
            "dark": "0c0a10", "lining": "a898b8", "fur": "efe8f4"},
    "head": {"kind": "bone_mask", "mask": "visage", "eyes": 6, "cloth": "cloth"},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "lining", "stole_key": "cloth2", "trim": "trim", "emblem": "spider",
              "extras": [("fur_mantle", dict(key="fur"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["rimebloom"] = {   # epic boss: frost-blue robe, pink blossoms, an ice halo
    "pal": {"cloth": "b8d8f0", "cloth2": "ff9ad0", "trim": ("f4fbff", "crystal"), "glow": "bdebff", "crystal": "bdebff", "gem": "ff9ad0",
            "leather": "6a8aa8", "lining": "8ab4d8", "dark": "0c1420"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth", "circlet": "trim", "extras": [("halo", dict(key="glow"))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "lining", "inner": "cloth2", "trim": "trim", "gem": True, "emblem": "snow",
              "emblem_glow": 0.6, "extras": [("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["hollow_saint"] = {   # legendary: white and gold, a halo, small white wings
    "pal": {"cloth": "ece8f4", "cloth2": "ffd88a", "trim": ("ffd88a", "gold"), "glow": "fff0b0", "gem": "fff0b0", "leather": "8a7a6a",
            "lining": "d0c8e0", "dark": "141018"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth", "circlet": "trim", "rim": "trim",
             "extras": [("halo", dict(key="glow")), ("wings", dict(key="cloth", key2="trim", size=0.8))]},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "lining", "stole_key": "cloth2", "trim": "trim", "emblem": "sun",
              "emblem_glow": 0.7, "gem": True, "ornate": True, "extras": [("back_wings", dict(key="cloth", key2="trim"))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "plate", "metal": "trim", "trim": "trim"},
}

D["starbinder"] = {   # legendary boss: a star priest, navy and gold with starlit runes
    "pal": {"cloth": "2a2a5a", "cloth2": "ffe9a0", "trim": ("ffe9a0", "gold"), "glow": "b8e2ff", "gem": "b8e2ff", "leather": "1e1e3a",
            "lining": "1a1a40", "dark": "06061a", "metal": "c8ccdc"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth", "circlet": "trim", "rim": "trim", "hat": [("crown", dict(key="trim", points=5, glow=0.8))]},
    "chest": {"kind": "vestment", "cloth": "cloth", "panel_key": "lining", "stole_key": "cloth2", "trim": "trim", "emblem": "star",
              "emblem_glow": 0.9, "gem": True, "ornate": True, "extras": [("collar", dict(key="trim", high=3.0))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "plate", "metal": "metal", "trim": "trim"},
}

D["lifebinder"] = {   # divine: white-gold plate over vestments, a leaf crown and halo, a green cape
    "pal": {"metal": "e8e4d8", "trim": ("ffe08a", "gold"), "cloth": "f0ece0", "cloth2": "3a7a4a", "glow": "fff3c0", "gem": "8ce05a",
            "mail": "a8a498", "leather": "6a5a3a", "lining": "2a5a36", "dark": "10140c"},
    "head": {"kind": "bare", "cloth": "trim", "rows": 3, "gem": True, "extras": [("halo", dict(key="glow")), ("wings", dict(key="cloth", key2="trim", size=0.7))]},
    "chest": {"kind": "plate", "under": "gambeson", "under_key": "cloth", "shape": "round", "trim": "trim", "tabard": "long",
              "tabard_key": "cloth2", "tabard_trim": "trim", "emblem": "leaf", "emblem_key": "trim", "emblem_glow": 0.6, "gem": True,
              "gem_y": 3, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.0, gem_=True)),
                         ("cape", dict(key="cloth2", trim="trim", lining="lining", emblem_="leaf"))]},
    "legs": {"kind": "plate", "under": "gambeson", "under_key": "cloth", "trim": "trim",
             "extras": [("skirt", dict(key="cloth2", trim="trim", length=8, panel="cloth"))]},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("heel_wings", dict(key="cloth", key2="trim"))]},
}

# =============================================================================================== ranger

D["fletchwood"] = {   # E: a woods archer, green hood, fur collar, quiver
    "pal": {"leather": "7a6040", "leather2": "4a3a26", "cloth": "4a6a34", "cloth2": "c8a060", "trim": ("a07a4a", "leather"),
            "fur": "d8c8a8", "thread": "e0d0a0"},
    "head": {"kind": "hood", "style": "hood", "cloth": "cloth", "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "leather", "under": "cloth", "leather": "leather", "straps": [(-8, 2, 8, 12)],
              "extras": [("fur_mantle", dict(key="fur")), ("quiver", dict(key="leather2", feather="cloth2"))]},
    "legs": {"kind": "leather", "waist": "leather"},
    "feet": {"kind": "fur", "fur_key": "fur"},
}

D["gloomstalker"] = {   # rare: a shadow wolf's head and pelt, violet eyes
    "pal": {"leather": "3a3a4e", "leather2": "24242e", "fur": "4a4660", "bone": "d0d0e0", "trim": ("d0d0e0", "bone"), "cloth": "2a2838",
            "cloth2": "6a5aa8", "glow": "9a8aff", "dark": "08080c", "thread": "8a7ab8"},
    "head": {"kind": "hood", "style": "hood", "cloth": "fur", "extras": [("wolf", dict(fur_key="fur", tooth="bone", inner="cloth2"))]},
    "chest": {"kind": "leather", "under": "cloth", "straps": [(-8, 2, 8, 12)], "glow_seams": True,
              "extras": [("fur_mantle", dict(key="fur")), ("quiver", dict(key="leather2", feather="cloth2"))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "fur", "fur_key": "fur"},
}

D["silkstrider"] = {   # rare: pale silk hood and wraps over green leather
    "pal": {"cloth": "d0d0d8", "cloth2": "8ee05a", "leather": "5a6a4a", "leather2": "3a4630", "trim": ("5a6a4a", "leather"),
            "scale": "6a7a5a", "glow": "9cff5a", "thread": "e8e8f0", "dark": "0a0c08"},
    "head": {"kind": "hood", "style": "cowl", "cloth": "cloth", "mask": "cloth", "mask_trim": "cloth2"},
    "chest": {"kind": "leather", "under": "scale", "leather": "leather", "scarf": "cloth", "straps": [(-8, 2, 8, 12)],
              "extras": [("quiver", dict(key="leather2", feather="cloth2"))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "wraps", "cloth": "cloth", "leather": "leather2"},
}

D["frostmane"] = {   # epic boss: a white wolf's head and mane, ice-blue leather
    "pal": {"leather": "a8bccc", "leather2": "5a7a98", "fur": "eef4fa", "bone": "f0f4f8", "trim": ("5a8ab8", "metal"), "cloth": "4a6a8a",
            "cloth2": "4fa8ff", "glow": "9ad8ff", "dark": "0a1018", "thread": "dceaf5", "metal": "8aa8c8"},
    "head": {"kind": "hood", "style": "hood", "cloth": "fur", "extras": [("wolf", dict(fur_key="fur", tooth="bone", inner="cloth2"))]},
    "chest": {"kind": "leather", "under": "cloth", "straps": [(-8, 2, 8, 12)], "studs": True,
              "extras": [("fur_mantle", dict(key="fur")), ("quiver", dict(key="leather2", feather="cloth2")),
                         ("pauldrons", dict(key="metal", trim="trim", lame=3, size=0.85))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "fur", "fur_key": "fur"},
}

D["moonmaw"] = {   # legendary: a moon wolf, silver scales, crescent marks
    "pal": {"leather": "2a2e4a", "leather2": "1a1c30", "fur": "c8d0e8", "bone": "f0f4ff", "trim": ("d8e0ff", "metal"), "metal": "c0c8e0",
            "scale": "4a5276", "cloth": "22263e", "cloth2": "a8c0ff", "glow": "c8d8ff", "gem": "a8c0ff", "dark": "06081a", "thread": "c8d0f0"},
    "head": {"kind": "hood", "style": "hood", "cloth": "fur", "extras": [("wolf", dict(fur_key="fur", tooth="bone", inner="cloth2")),
                                                                     ("crystals", dict(key="cloth2", n=1))]},
    "chest": {"kind": "leather", "under": "scale", "straps": [(-8, 2, 8, 12)], "glow_seams": True, "emblem": "moon", "emblem_glow": 0.8,
              "extras": [("fur_mantle", dict(key="fur")), ("quiver", dict(key="leather2", feather="cloth2")),
                         ("cape", dict(key="cloth", trim="trim", lining="leather2", emblem_="moon", length=16))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth2"},
    "feet": {"kind": "fur", "fur_key": "fur"},
}

D["ashwing"] = {   # legendary boss: a drake hunter, scale coat, membrane wings, ember light
    "pal": {"scale": "5a4038", "leather": "4a3a34", "leather2": "2a1e1a", "trim": ("a8643a", "gold"), "cloth": "8a3a1e", "cloth2": "ff8a2a",
            "glow": "ffa030", "bone": "e8d8b8", "dark": "0c0604", "thread": "e8a060"},
    "head": {"kind": "bone_mask", "mask": "jaw", "cloth": "cloth", "extras": [("horns", dict(key="bone", style="up", size=1.0)),
                                                                          ("wings", dict(key="cloth", key2="bone", membrane=True, size=0.8))]},
    "chest": {"kind": "leather", "under": "scale", "leather": "leather", "straps": [(-8, 2, 8, 12)], "glow_seams": True,
              "extras": [("back_wings", dict(key="cloth", key2="bone", membrane=True)), ("quiver", dict(key="leather2", feather="cloth2"))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "leather", "leather": "scale", "extras": [("boot_spikes", dict(key="bone"))]},
}

D["skywarden"] = {   # divine: white and gold, feathered helm and heels, sky-blue cloak
    "pal": {"leather": "dfe8f0", "leather2": "a8b8c8", "trim": ("ffd36a", "gold"), "cloth": "4ab0e0", "cloth2": "6ad0ff", "glow": "a8f0ff",
            "gem": "6ad0ff", "fur": "ffffff", "metal": "e8eef4", "dark": "0a1420", "thread": "ffe8a0"},
    "head": {"kind": "helm", "style": "cap", "metal": "metal", "trim": "trim", "gem": True, "gem_y": 1,
             "extras": [("wings", dict(key="fur", key2="trim"))]},
    "chest": {"kind": "leather", "under": "cloth", "leather": "leather", "straps": [(-8, 2, 8, 12)], "emblem": "wing", "emblem_glow": 0.6,
              "extras": [("cape", dict(key="cloth", trim="trim", lining="cloth2", length=17)), ("quiver", dict(key="trim", feather="fur")),
                         ("pauldrons", dict(key="metal", trim="trim", lame=3, size=0.85, gem_=True))]},
    "legs": {"kind": "leather", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "leather", "shin": "metal", "extras": [("heel_wings", dict(key="fur", key2="trim"))]},
}

# =============================================================================================== the first armor (gear)

D["hunter"] = {   # common: the hunter's long leather coat and green hood
    "pal": {"leather": "8a5a36", "leather2": "5a3a22", "trim": ("9aa6b4", "metal"), "cloth": "3e5a3a", "cloth2": "2a3a28", "thread": "d8c49a",
            "metal": "9aa6b4"},
    "head": {"kind": "hood", "style": "cowl", "cloth": "cloth", "mask": "cloth2", "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "leather", "under": "cloth", "leather": "leather", "straps": [(-8, 2, 8, 12)], "knife": True},
    "legs": {"kind": "leather", "waist": "leather", "extras": [("skirt", dict(key="leather", trim="leather2", length=6))]},
    "feet": {"kind": "leather", "fold": True},
}

D["knight"] = {   # rare: a polished knight with a blue plume and surcoat
    "pal": {"metal": "b9c4cf", "trim": ("e0b44c", "gold"), "cloth": "2f6fd0", "mail": "5a6270", "leather": "5a3e2a", "dark": "10141c"},
    "head": {"kind": "helm", "style": "greathelm", "trim": "trim", "extras": [("plume", dict(key="cloth", tall=4.0))]},
    "chest": {"kind": "plate", "under": "mail", "tabard": "full", "tabard_key": "cloth", "tabard_trim": "trim", "emblem": "crown",
              "emblem_key": "trim", "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.1))]},
    "legs": {"kind": "plate", "under": "mail", "extras": [("tassets", dict(key="metal", trim="trim", length=4))]},
    "feet": {"kind": "plate", "extras": [("toes", dict(key="metal", trim="trim"))]},
}

D["dusk"] = {   # epic: violet dusk plate with a pink-lit slit and spiked shoulders
    "pal": {"metal": ("4a4470", "dark"), "trim": ("b48cff", "metal"), "cloth": "2a2240", "glow": "ff5d8a", "mail": "24203a", "leather": "221c30",
            "dark": "06040c", "gem": "ff5d8a"},
    "head": {"kind": "helm", "style": "sallet", "eyes": True, "trim": "trim", "ridge_key": "trim", "gem": True, "gem_y": 1},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "runes": 3, "gem": True, "gem_y": 4, "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=3, glow=True))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim"},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("boot_spikes", dict(key="trim"))]},
}

D["stonehide"] = {   # rare: slabs of carved stone, ram horns of bone
    "pal": {"metal": ("8a7a62", "stone"), "trim": ("c99a48", "gold"), "bone": "e8dcc0", "leather": "5a4632", "mail": ("5a5040", "stone"),
            "fur": "8a7458", "dark": "120e0a"},
    "head": {"kind": "helm", "style": "cap", "trim": "trim", "rivets": False, "extras": [("horns", dict(key="bone", style="ram"))]},
    "chest": {"kind": "plate", "under": "fur", "trim": "trim", "pauldron_rows": 0, "cracks": "dark",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=0, size=1.2)), ("fur_mantle", dict(key="fur"))]},
    "legs": {"kind": "plate", "under": "leather", "trim": "trim", "cracks": "dark"},
    "feet": {"kind": "plate", "trim": "trim", "cracks": "dark", "under": "fur"},
}

D["mirefang"] = {   # rare: swamp-lizard scale, a fanged bone jaw, venom-green glow
    "pal": {"scale": "4f7a3a", "leather": "3a4a2a", "leather2": "24301a", "bone": "e0d6b4", "trim": ("e0d6b4", "bone"), "cloth": "2e3e22",
            "cloth2": "8ee05a", "glow": "9cff5a", "dark": "080c06", "thread": "c8d0a0"},
    "head": {"kind": "bone_mask", "mask": "jaw", "cloth": "scale", "extras": [("spine", dict(key="bone", n=0))]},
    "chest": {"kind": "leather", "under": "scale", "leather": "scale", "straps": [(-8, 2, 8, 12)], "glow_seams": True,
              "extras": [("spine", dict(key="bone", n=3)), ("pauldrons", dict(key="scale", trim="trim", lame=0, size=0.9, spikes=2))]},
    "legs": {"kind": "leather", "leather": "scale", "waist": "leather", "wraps": "cloth"},
    "feet": {"kind": "leather", "leather": "scale", "extras": [("boot_spikes", dict(key="bone"))]},
}

D["rimeguard"] = {   # epic: ice-blue plate, a crown of ice, crystal shoulders
    "pal": {"metal": "9cc8e8", "trim": ("eaf8ff", "crystal"), "crystal": "7fd0ff", "glow": "7fd0ff", "cloth": "2a5a8a", "mail": "5a7a98",
            "leather": "3a4a5a", "gem": "4fa8ff", "dark": "0a1420"},
    "head": {"kind": "helm", "style": "cheek", "trim": "trim", "ridge_key": "trim", "extras": [("crystals", dict(key="crystal", n=3))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "gem": True, "gem_y": 4, "emblem": "snow",
              "emblem_key": "trim", "emblem_glow": 0.6, "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.1)), ("crystals", dict(key="crystal", where="shoulder"))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim"},
    "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("toes", dict(key="crystal"))]},
}

D["gloomreaver"] = {   # epic: the reaper's hood and robe, violet eyes, a ragged cloak
    "pal": {"cloth": "2e2444", "cloth2": "c8a8ff", "trim": ("8a72c8", "dark"), "glow": "b88aff", "leather": "1e1830", "dark": "06040c",
            "lining": "1a1428", "metal": "6a5a9a"},
    "head": {"kind": "hood", "style": "reaper", "cloth": "cloth", "trim": "trim", "extras": [("hood_tip", dict(key="cloth"))]},
    "chest": {"kind": "robe", "cloth": "cloth", "mantle_key": "lining", "inner": "cloth2", "trim": "trim", "runes": True,
              "extras": [("cape", dict(key="cloth", trim="trim", lining="lining", ragged=True, length=19)),
                         ("pauldrons", dict(key="metal", trim="trim", lame=0, size=0.8, spikes=1))]},
    "legs": {"kind": "cloth", "cloth": "cloth", "panel": "lining", "trim": "trim"},
    "feet": {"kind": "wraps", "cloth": "lining", "leather": "leather"},
}

D["emberwrought"] = {   # legendary: obsidian plate cracked with lava, ember horns
    "pal": {"metal": ("3a2c2a", "dark"), "trim": ("a8643a", "gold"), "glow": "ffa030", "cloth": "4a1a10", "mail": "201614", "leather": "24180e",
            "bone": "2a2220", "gem": "ff8a2a", "dark": "060302"},
    "head": {"kind": "helm", "style": "greathelm", "eyes": True, "trim": "trim", "cracks": "glow", "crack_n": 4,
             "extras": [("horns", dict(key="trim", style="up"))]},
    "chest": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 7, "gem": True, "gem_y": 4, "pauldron_rows": 0,
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=0, size=1.2, spikes=2, glow=True))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 4},
    "feet": {"kind": "plate", "trim": "trim", "cracks": "glow", "glow_line": True, "extras": [("boot_spikes", dict(key="trim"))]},
}

D["sovereign"] = {   # mythic: a crimson king, crown over the helm, gold and rubies, royal cape
    "pal": {"metal": "a01c34", "trim": ("ffcf5a", "gold"), "cloth": "6a0a1a", "cloth2": "f4ecd8", "glow": "ff4a6a", "gem": "ff3d5a",
            "mail": "5a1a24", "leather": "3a1016", "fur": "f4ecd8", "dark": "10040a", "lining": "f4ecd8"},
    "head": {"kind": "helm", "style": "cheek", "trim": "trim", "ridge": False, "gem": True, "gem_y": 1, "etch": True, "etch_key": "trim",
             "hat": [("crown", dict(key="trim", glow=0.8))]},
    "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "crown", "emblem_key": "trim",
              "emblem_glow": 0.5, "gem": True, "gem_y": 3, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
              "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.15, gem_=True, fur_key="fur")),
                         ("cape", dict(key="cloth", trim="trim", lining="lining", emblem_="crown", length=19))]},
    "legs": {"kind": "plate", "under": "mail", "trim": "trim", "etch": True,
             "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
    "feet": {"kind": "plate", "trim": "trim", "extras": [("toes", dict(key="metal", trim="trim"))]},
}

CLASS = {
    "warrior": ["forgehand", "fangcrest", "prismhorn", "hollow_vanguard", "wyrmlord", "worldbreaker"],
    "guardian": ["hearthguard", "shardshell", "ashen_watch", "ashen_bastion", "oathbound", "eternal_bastion"],
    "assassin": ["nightstitch", "broodsilk", "duskfang", "brood_empress", "hollowblade", "veilwalker"],
    "mage": ["inkweave", "cinderwisp", "pyre_wraith", "hollow_hierarch", "sigilweaver", "archmagus", "starweave"],
    "healer": ["chapelward", "bonechant", "silken_cradle", "rimebloom", "hollow_saint", "starbinder", "lifebinder"],
    "ranger": ["fletchwood", "gloomstalker", "silkstrider", "frostmane", "moonmaw", "ashwing", "skywarden"],
    "gear": ["hunter", "knight", "dusk", "stonehide", "mirefang", "rimeguard", "gloomreaver", "emberwrought", "sovereign"],
}
