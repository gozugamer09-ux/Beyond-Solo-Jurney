"""
High-detail armor for all nine System sets (called from tools/gen_hd_gear.py, or run on its own):

- item icons, 64x64: every set has its own helmet shape (hood, great helm, sallet, horned, fanged, ice crown,
  royal crown), its own pauldrons, plates and boots, painted with the hd_paint materials (leather, steel, stone,
  scales, ice, cloth, obsidian) and dressed with rivets, trims, stitches, gems, glowing runes or molten cracks
- worn armor layers, 128x64 (the vanilla 64x32 humanoid layout at twice the resolution)

Run from the repo root:  python3 tools/hd_armor.py
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import hd_paint as hp  # noqa: E402
from hd_paint import hexc  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
ITEM_OUT = os.path.join(ASSETS, "textures/item")
ARMOR_OUT = os.path.join(ASSETS, "textures/models/armor")
S = 64

# main: the set's body colour and material; trim: bands, rims, rivets; accent: cloth, gems, glow
SETS = {
    "hunter": dict(grade="common", main=hexc("8a5a36"), mat="leather", trim=hexc("9aa6b4"), trim_mat="metal",
                   accent=hexc("3e5a3a"), accent_mat="cloth", helm="hood", chest="coat", boots="laced", glow=None,
                   items=("hunter_hood", "hunter_coat", "hunter_leggings", "hunter_boots")),
    "knight": dict(grade="rare", main=hexc("b9c4cf"), mat="metal", trim=hexc("e0b44c"), trim_mat="gold",
                   accent=hexc("2f6fd0"), accent_mat="cloth", helm="greathelm", chest="cuirass", boots="sabatons",
                   glow=None, items=("knight_helm", "knight_cuirass", "knight_greaves", "knight_sabatons")),
    "dusk": dict(grade="epic", main=hexc("4a4470"), mat="dark_metal", trim=hexc("b48cff"), trim_mat="metal",
                 accent=hexc("ff5d8a"), accent_mat="cloth", helm="sallet", chest="spiked", boots="spiked",
                 glow=hexc("ff5d8a"), items=("dusk_helm", "dusk_chestplate", "dusk_leggings", "dusk_boots")),
    "stonehide": dict(grade="rare", main=hexc("8a7a62"), mat="stone", trim=hexc("c99a48"), trim_mat="gold",
                      accent=hexc("e8dcc0"), accent_mat="bone", helm="horned", chest="rock", boots="rock", glow=None),
    "mirefang": dict(grade="rare", main=hexc("4f7a3a"), mat="scale", trim=hexc("e0d6b4"), trim_mat="bone",
                     accent=hexc("8ee05a"), accent_mat="cloth", helm="fanged", chest="scaled", boots="scaled",
                     glow=hexc("9cff5a")),
    "rimeguard": dict(grade="epic", main=hexc("9cc8e8"), mat="metal", trim=hexc("eaf8ff"), trim_mat="ice",
                      accent=hexc("4fa8ff"), accent_mat="crystal", helm="icecrown", chest="ice", boots="ice",
                      glow=hexc("7fd0ff")),
    "gloomreaver": dict(grade="epic", main=hexc("2e2444"), mat="cloth", trim=hexc("8a72c8"), trim_mat="dark_metal",
                        accent=hexc("c8a8ff"), accent_mat="cloth", helm="reaper", chest="robe", boots="wraps",
                        glow=hexc("b88aff")),
    "emberwrought": dict(grade="legendary", main=hexc("3a2c2a"), mat="obsidian", trim=hexc("a8643a"),
                         trim_mat="dark_metal", accent=hexc("ff8a2a"), accent_mat="metal", helm="horned_ember",
                         chest="molten", boots="molten", glow=hexc("ffa030")),
    "sovereign": dict(grade="mythic", main=hexc("a01c34"), mat="metal", trim=hexc("ffcf5a"), trim_mat="gold",
                      accent=hexc("ff3d5a"), accent_mat="crystal", helm="crown", chest="royal", boots="royal",
                      glow=hexc("ff4a6a")),
}
for _n, _c in SETS.items():
    _c.setdefault("items", (_n + "_helm", _n + "_chestplate", _n + "_leggings", _n + "_boots"))

GRADE_ACCENT = {"common": hexc("ffffff"), "rare": hexc("7fc8ff"), "epic": hexc("b47bff"),
                "legendary": hexc("ffb43a"), "mythic": hexc("ff4a6a"), "divine": hexc("fff3c0")}


def P(*pts):
    return hp.poly(list(pts), S)


def mirror(pts):
    return [(S - x, y) for x, y in pts]


def E(cx, cy, rx, ry):
    return hp.ellipse(cx, cy, rx, ry, S)


def dark_of(c, f=0.35):
    return hp.mix(hp.scale(c, f), hp.COOL, 0.3)


# ------------------------------------------------------------------------------------------------- helmets

def helmet(cfg):
    cv = hp.Canvas(S)
    main, trim, acc = cfg["main"], cfg["trim"], cfg["accent"]
    style = cfg["helm"]
    glow = cfg["glow"]
    a = cv.a
    if style == "hood":
        cape = P((14, 46), (50, 46), (56, 60), (8, 60))
        cv.layer(cape, acc, "cloth", seed=1)
        hood = P((32, 3), (43, 7), (51, 18), (54, 34), (52, 50), (44, 57), (20, 57), (12, 50), (10, 34), (13, 18), (21, 7))
        cv.layer(hood, main, cfg["mat"], seed=2)
        opening = E(32, 34, 12.5, 15)
        cv.paint(opening, (14, 10, 18, 255))
        inner = E(32, 31, 10, 11)
        cv.layer(inner & opening, hexc("2a2026"), "cloth", seed=3, light_bias=-0.5)
        mask = P((20, 38), (44, 38), (43, 49), (32, 52), (21, 49)) & opening
        cv.layer(mask, acc, "cloth", seed=4)
        cv.seams()
        a = cv.a
        hp.stitches(a, hood & ~hp.shrink(hood, 3) & hp.shrink(hood, 2), hp.hexc("d8b88a"), 3)
        # brow band with eyes in the shadow
        for ex in (26, 37):
            a[33:35, ex:ex + 2] = (220, 230, 200, 255)
        a[35, 26:28] = (90, 110, 90, 255)
        a[35, 37:39] = (90, 110, 90, 255)
        tie = P((40, 46), (44, 47), (46, 58), (42, 58))
        sub = hp.Canvas(S)
        sub.layer(tie, hp.scale(acc, 0.8), "cloth", seed=5)
        m = sub.a[..., 3] > 0
        a[m] = sub.a[m]
        return a
    if style == "reaper":
        back = P((32, 1), (46, 8), (56, 26), (60, 52), (50, 62), (14, 62), (4, 52), (8, 26), (18, 8))
        cv.layer(back, hp.scale(main, 0.8), "cloth", seed=1)
        hood = P((32, 4), (44, 9), (52, 22), (54, 40), (48, 54), (40, 58), (24, 58), (16, 54), (10, 40), (12, 22), (20, 9))
        cv.layer(hood, main, "cloth", seed=2)
        opening = P((32, 18), (43, 24), (45, 40), (38, 52), (26, 52), (19, 40), (21, 24))
        cv.paint(opening, (8, 6, 14, 255))
        trimm = (hp.grow(opening, 2) & ~opening & hood)
        cv.layer(trimm, trim, "dark_metal", seed=3)
        cv.seams()
        a = cv.a
        # tattered hem: notches cut out of the bottom
        for x in range(6, 58, 5):
            a[60 - (x % 3):64, x:x + 2] = 0
        for ex in (26, 36):
            hp.glow_paint(a, P((ex, 32), (ex + 4, 31), (ex + 3.5, 33.5), (ex, 33.5)), glow, halo=0.5)
        rune = hp.thick_line((32, 8), (32, 15), 1.2, S) | hp.thick_line((29, 11), (35, 11), 1.2, S)
        hp.glow_paint(a, rune & hood, glow, halo=0.35, core=False)
        return a

    # helmets with a shell
    if style in ("greathelm",):
        shell = P((15, 13), (20, 7), (44, 7), (49, 13), (50, 47), (45, 56), (19, 56), (14, 47))
    elif style == "sallet":
        shell = P((32, 2), (41, 9), (48, 20), (50, 36), (55, 50), (44, 56), (20, 56), (9, 50), (14, 36), (16, 20), (23, 9))
    else:
        shell = P((16, 22), (20, 13), (27, 9), (32, 8), (37, 9), (44, 13), (48, 22), (49, 40), (46, 50), (40, 56), (24, 56), (18, 50), (15, 40))

    # behind the shell: horns, plume, spikes, crown back
    if style == "greathelm":
        plume = hp.curve([(32, 8), (40, 2), (50, 3), (57, 10), (58, 20)], lambda t: 7 - t * 1.6, S)
        cv.layer(plume, acc, "cloth", seed=6)
    if style in ("horned", "horned_ember"):
        hc = cfg["accent"] if style == "horned" else hexc("2a1e1c")
        for side in (1, -1):
            pts = [(32 + side * 12, 16), (32 + side * 20, 12), (32 + side * 26, 4), (32 + side * 25, -2)]
            horn = hp.curve(pts, lambda t: 7.5 - t * 2.4, S)
            cv.layer(horn, hc, "bone" if style == "horned" else "obsidian", seed=7 + side)
    if style == "icecrown":
        for i, (x, h) in enumerate(((18, 10), (25, 4), (32, 0), (39, 4), (46, 10))):
            spike = P((x - 4, 18), (x - 1, h + 2), (x, h), (x + 1, h + 2), (x + 4, 18))
            cv.layer(spike, hp.mix(acc, (255, 255, 255), 0.35), "crystal", seed=20 + i)

    cv.layer(shell, main, cfg["mat"], seed=10)

    if style == "greathelm":
        crest = P((30, 7), (34, 7), (34, 55), (30, 55))
        cv.layer(crest & shell, trim, cfg["trim_mat"], seed=11)
        brow = P((15, 20), (49, 20), (49, 24), (15, 24))
        cv.layer(brow & shell, trim, cfg["trim_mat"], seed=12)
        visor = P((17, 27), (47, 27), (47, 31), (17, 31))
        cv.paint(visor & ~(P((30, 27), (34, 27), (34, 31), (30, 31))), (12, 10, 18, 255))
    elif style == "sallet":
        visor = P((16, 28), (32, 33), (48, 28), (48, 32), (32, 38), (16, 32))
        cv.paint(visor, (12, 8, 16, 255))
        ridge = P((31, 3), (33, 3), (33, 27), (31, 27))
        cv.layer(ridge & shell, trim, cfg["trim_mat"], seed=11)
    else:
        face = P((24, 26), (40, 26), (40, 31), (35, 31), (35, 48), (29, 48), (29, 31), (24, 31))
        brow = P((17, 21), (47, 21), (47, 26), (17, 26))
        cv.layer(brow & shell, trim, cfg["trim_mat"], seed=12)
        cv.paint(face & shell, (12, 9, 16, 255))
        if style == "crown":
            mask = P((18, 26), (46, 26), (45, 44), (32, 52), (19, 44)) & ~face
            cv.layer(mask & shell, trim, "gold", seed=13)
            band = P((15, 12), (49, 12), (49, 20), (15, 20))
            cv.layer(band & hp.grow(shell, 2), trim, "gold", seed=14)
            for i, x in enumerate((17, 25, 32, 39, 47)):
                pt = P((x - 3.5, 13), (x, 2 if x == 32 else 5), (x + 3.5, 13))
                cv.layer(pt, trim, "gold", seed=15 + i)
        if style == "fanged":
            for x in (22, 38):
                fang = P((x, 44), (x + 4, 44), (x + 2.5, 56), (x + 1.5, 57))
                cv.layer(fang, cfg["trim"], "bone", seed=16 + x)
            ridge = P((31, 8), (33, 8), (33, 21), (31, 21))
            cv.layer(ridge & shell, cfg["trim"], "bone", seed=18)
        if style == "horned_ember":
            plate = P((19, 33), (27, 33), (28, 48), (20, 46)) | P((37, 33), (45, 33), (44, 46), (36, 48))
            cv.layer(plate & shell, hexc("2a1e1c"), "obsidian", seed=19)
    cv.seams()
    a = cv.a

    # surface details
    if style == "greathelm":
        for y in range(36, 52, 4):
            for x in (21, 25, 39, 43):
                a[y:y + 2, x:x + 2] = (14, 12, 20, 255)
        for x in (17, 45):
            hp.rivet(a, x, 21, trim)
        hp.rivet(a, 31, 44, trim)
    elif style == "sallet":
        hp.glow_paint(a, P((19, 30), (31, 34.2), (32, 34.5), (33, 34.2), (45, 30), (45, 31.5), (32, 36), (19, 31.5)), glow, halo=0.5)
        for x in (20, 44):
            hp.rivet(a, x - 1, 44, trim)
        hp.engrave(a, hp.thick_line((14, 42), (50, 42), 1, S))
    else:
        if style in ("horned", "fanged", "icecrown", "horned_ember", "crown"):
            for x in (19, 44):
                hp.rivet(a, x, 22, hp.mix(trim, (255, 255, 255), 0.2))
        if style == "horned":
            hp.engrave(a, hp.cracks(shell & ~(P((17, 21), (47, 21), (47, 26), (17, 26))), 77, n=5, length=9))
        if style == "fanged":
            hp.glow_paint(a, P((24, 27), (28, 27), (28, 30), (24, 30)) | P((36, 27), (40, 27), (40, 30), (36, 30)), glow, halo=0.6)
        if style == "icecrown":
            hp.glow_paint(a, P((24, 27), (40, 27), (40, 30), (24, 30)), glow, halo=0.6)
            hp.gem(a, 32, 23.5, 2.6, acc)
        if style == "horned_ember":
            hp.glow_paint(a, hp.cracks(shell, 31, n=7, length=12), cfg["accent"], halo=0.45)
            hp.glow_paint(a, P((24, 27.5), (29, 27.5), (29, 30), (24, 30)) | P((35, 27.5), (40, 27.5), (40, 30), (35, 30)), hexc("ffd24a"), halo=0.6)
        if style == "crown":
            hp.glow_paint(a, P((24, 27.5), (29, 28), (29, 30.5), (24, 30)) | P((35, 28), (40, 27.5), (40, 30), (35, 30.5)), glow, halo=0.6)
            for x in (17, 25, 32, 39, 47):
                hp.gem(a, x, 15.5, 1.9 if x != 32 else 2.6, acc)
            hp.engrave(a, hp.curve([(22, 36), (26, 41), (32, 43), (38, 41), (42, 36)], 0.9, S))
    return a


# ------------------------------------------------------------------------------------------------- chest

def chest(cfg):
    cv = hp.Canvas(S)
    main, trim, acc, glow = cfg["main"], cfg["trim"], cfg["accent"], cfg["glow"]
    style = cfg["chest"]
    body = P((17, 13), (25, 10), (32, 15), (39, 10), (47, 13), (48, 34), (46, 57), (18, 57), (16, 34))
    if style == "royal":
        cape = P((12, 18), (52, 18), (58, 62), (6, 62))
        cv.layer(cape, hexc("6a0c1e"), "cloth", seed=1)
    if style in ("robe",):
        body = P((18, 12), (25, 9), (32, 14), (39, 9), (46, 12), (49, 40), (54, 62), (10, 62), (15, 40))
    cv.layer(body, main, cfg["mat"], seed=2)
    # pauldrons
    if style == "coat":
        pl = P((8, 17), (14, 11), (24, 10), (24, 22), (10, 25))
        pmat, pcol = "leather", hp.scale(main, 0.85)
    elif style in ("spiked", "molten"):
        pl = P((5, 20), (8, 12), (16, 8), (25, 10), (25, 22), (13, 27))
        pmat, pcol = cfg["trim_mat"] if style == "spiked" else "obsidian", trim if style == "spiked" else hexc("2a1e1c")
    elif style == "ice":
        pl = P((5, 22), (7, 12), (16, 7), (25, 10), (25, 22), (14, 27))
        pmat, pcol = "metal", main
    elif style == "rock":
        pl = P((4, 21), (7, 11), (16, 7), (25, 11), (24, 23), (12, 28))
        pmat, pcol = "stone", hp.scale(main, 0.9)
    elif style == "robe":
        pl = P((8, 16), (16, 9), (25, 10), (25, 20), (12, 24))
        pmat, pcol = "dark_metal", trim
    elif style == "scaled":
        pl = P((6, 20), (9, 12), (17, 8), (25, 10), (25, 22), (13, 26))
        pmat, pcol = "bone", trim
    else:  # cuirass, royal
        pl = E(15, 17, 10, 9)
        pmat, pcol = cfg["mat"] if style != "royal" else "gold", main if style != "royal" else trim
    pr = np.fliplr(pl)
    # spikes and crystals stick out of the pauldrons
    if style in ("spiked", "molten"):
        for side in (1, -1):
            for k, (dx, h) in enumerate(((10, 0), (16, 2))):
                x = 32 - side * (32 - dx) if side == 1 else 64 - dx
                sp = P((x - 3, 12), (x, h), (x + 3, 12))
                cv.layer(sp, trim if style == "spiked" else hexc("2a1e1c"), "metal" if style == "spiked" else "obsidian", seed=30 + k + side)
    if style == "ice":
        for k, (x, h) in enumerate(((8, 1), (14, 3), (50, 3), (56, 1))):
            sp = P((x - 3.5, 13), (x, h), (x + 3.5, 13))
            cv.layer(sp, hp.mix(acc, (255, 255, 255), 0.35), "crystal", seed=40 + k)
    if style == "rock":
        for k, x in enumerate((9, 55)):
            cv.layer(P((x - 4, 12), (x - 1, 3), (x + 3, 11)), cfg["accent"], "bone", seed=45 + k)
    cv.layer(pl, pcol, pmat, seed=3)
    cv.layer(pr, pcol, pmat, seed=4)
    # chest details by style
    if style == "coat":
        lapel_l = P((25, 10.5), (32, 15), (30, 30), (24, 22))
        cv.layer(lapel_l | np.fliplr(lapel_l), hp.scale(main, 0.75), "leather", seed=5)
        strap = hp.thick_line((22, 14), (44, 46), 4, S)
        cv.layer(strap & body, hexc("4a2e1a"), "leather", seed=6)
        belt = P((17, 44), (47, 44), (47, 49), (17, 49))
        cv.layer(belt & body, hexc("3a2414"), "leather", seed=7)
        buckle = P((29, 43), (35, 43), (35, 50), (29, 50))
        cv.layer(buckle, trim, "metal", seed=8)
        pocket = P((36, 26), (44, 26), (44, 34), (36, 34))
        cv.layer(pocket, hp.scale(main, 0.9), "leather", seed=9)
    elif style == "cuirass":
        tabard = P((24, 34), (40, 34), (42, 58), (22, 58))
        cv.layer(tabard, acc, "cloth", seed=5)
        neck = P((24, 11), (32, 16), (40, 11), (40, 14), (32, 19), (24, 14))
        cv.layer(neck, trim, "gold", seed=6)
        belt = P((17, 32), (47, 32), (47, 36), (17, 36))
        cv.layer(belt & body, trim, "gold", seed=7)
        emblem = P((29, 39), (35, 39), (35, 46), (32, 50), (29, 46))
        cv.layer(emblem, trim, "gold", seed=8)
    elif style == "spiked":
        plate = P((22, 16), (42, 16), (40, 34), (32, 40), (24, 34))
        cv.layer(plate, hp.scale(main, 1.25), "dark_metal", seed=5)
        belt = P((17, 44), (47, 44), (47, 48), (17, 48))
        cv.layer(belt & body, trim, "metal", seed=6)
        sash = P((22, 48), (30, 48), (28, 58), (20, 58))
        cv.layer(sash, acc, "cloth", seed=7)
    elif style == "rock":
        for k, (y0, y1) in enumerate(((15, 27), (28, 40), (41, 56))):
            slab = P((19 + k, y0), (45 - k, y0), (44 - k, y1), (20 + k, y1)) & body
            cv.layer(slab, hp.scale(main, 1.05 - 0.08 * k), "stone", seed=5 + k)
        band = P((17, 40.5), (47, 40.5), (47, 43), (17, 43))
        cv.layer(band & body, trim, "gold", seed=9)
    elif style == "scaled":
        ribs = np.zeros((S, S), bool)
        for y in (20, 26, 32):
            ribs |= hp.curve([(22, y + 3), (27, y), (32, y + 2), (37, y), (42, y + 3)], 2.2, S)
        cv.layer(ribs & body, trim, "bone", seed=5)
        belt = P((17, 44), (47, 44), (47, 48), (17, 48))
        cv.layer(belt & body, hexc("3a2a1c"), "leather", seed=6)
    elif style == "ice":
        plate = P((22, 15), (42, 15), (41, 36), (32, 44), (23, 36))
        cv.layer(plate, hp.mix(main, (255, 255, 255), 0.35), "ice", seed=5)
        belt = P((17, 46), (47, 46), (47, 50), (17, 50))
        cv.layer(belt & body, hexc("3a6a94"), "metal", seed=6)
    elif style == "robe":
        collar = P((22, 9), (32, 18), (42, 9), (42, 14), (32, 24), (22, 14))
        cv.layer(collar, trim, "dark_metal", seed=5)
        sash = P((16, 38), (48, 38), (48, 43), (16, 43))
        cv.layer(sash & body, hexc("1a1428"), "cloth", seed=6)
        tail = P((27, 43), (33, 43), (34, 60), (26, 60))
        cv.layer(tail, trim, "cloth", seed=7)
    elif style == "molten":
        plate = P((21, 15), (43, 15), (42, 38), (32, 44), (22, 38))
        cv.layer(plate, hexc("4a3634"), "obsidian", seed=5)
        belt = P((17, 46), (47, 46), (47, 50), (17, 50))
        cv.layer(belt & body, trim, "dark_metal", seed=6)
    elif style == "royal":
        plate = P((21, 15), (43, 15), (42, 36), (32, 43), (22, 36))
        cv.layer(plate, hp.scale(main, 1.1), "metal", seed=5)
        neck = P((23, 11), (32, 16), (41, 11), (41, 14.5), (32, 19.5), (23, 14.5))
        cv.layer(neck, trim, "gold", seed=6)
        belt = P((17, 45), (47, 45), (47, 49), (17, 49))
        cv.layer(belt & body, trim, "gold", seed=7)
        skirt = P((19, 49), (45, 49), (47, 58), (17, 58))
        cv.layer(skirt, hp.scale(main, 0.8), "metal", seed=8)
    cv.seams()
    a = cv.a

    # decoration
    if style == "coat":
        hp.stitches(a, (hp.shrink(body, 2) & ~hp.shrink(body, 3)), hexc("d8b88a"), 3)
        for y in (20, 28, 36):
            hp.rivet(a, 26 + (y - 20) * 0.6, y, trim, size=1)
        hp.engrave(a, P((30.5, 44.5), (33.5, 44.5), (33.5, 48.5), (30.5, 48.5)), 0.5)
    elif style == "cuirass":
        hp.engrave(a, hp.thick_line((32, 19), (32, 31), 1, S))
        for y in (22, 27):
            hp.engrave(a, hp.curve([(19, y + 3), (25, y), (32, y + 1.5), (39, y), (45, y + 3)], 0.9, S) & body)
        for x in (19, 26, 38, 45):
            hp.rivet(a, x, 33, hexc("fff0c0"))
        for k in range(5):
            ang = 2.5 + k * 0.55
            hp.rivet(a, 15 + math.cos(ang) * 7, 17 + math.sin(ang) * 6, trim, size=1)
            hp.rivet(a, 49 - math.cos(ang) * 7, 17 + math.sin(ang) * 6, trim, size=1)
        hp.engrave(a, hp.thick_line((32, 41), (32, 47), 1, S), 0.45)
        hp.engrave(a, hp.thick_line((30, 43), (34, 43), 1, S), 0.45)
    elif style == "spiked":
        rune = hp.thick_line((32, 20), (32, 33), 1.3, S) | hp.thick_line((27, 22), (32, 28), 1.3, S) | hp.thick_line((37, 22), (32, 28), 1.3, S)
        hp.glow_paint(a, rune, glow, halo=0.4)
        for x in (20, 44):
            hp.rivet(a, x, 45, hp.mix(trim, (255, 255, 255), 0.3))
    elif style == "rock":
        hp.engrave(a, hp.cracks(body, 12, n=8, length=10), 0.4)
        for x in (19, 31, 44):
            hp.rivet(a, x, 41, hexc("ffe0a0"))
        hp.gem(a, 32, 22, 3.2, hexc("c0392b"))
    elif style == "scaled":
        hp.glow_paint(a, hp.curve([(24, 40), (32, 37), (40, 40)], 1.3, S), glow, halo=0.4)
        hp.gem(a, 32, 46, 2.6, hexc("6fe04a"))
    elif style == "ice":
        hp.gem(a, 32, 27, 4.2, acc)
        hp.glow_paint(a, hp.curve([(24, 18), (28, 22), (27, 32)], 1.0, S) | hp.curve([(40, 18), (36, 22), (37, 32)], 1.0, S), glow, halo=0.35)
        for x in (20, 44):
            hp.rivet(a, x, 47, hexc("eaf8ff"))
    elif style == "robe":
        sig = hp.ellipse(32, 29, 6, 6, S) & ~hp.ellipse(32, 29, 4.6, 4.6, S)
        sig |= hp.thick_line((32, 22), (32, 36), 1.1, S) | hp.thick_line((25, 29), (39, 29), 1.1, S)
        hp.glow_paint(a, sig & body, glow, halo=0.35, core=False)
        for x in range(12, 54, 5):
            a[60 + (x % 3):64, x:x + 2] = 0
    elif style == "molten":
        hp.glow_paint(a, hp.cracks(body | pl | pr, 21, n=12, length=12), cfg["accent"], halo=0.45)
        hp.gem(a, 32, 27, 3.4, hexc("ffb030"))
        for x in (20, 44):
            hp.rivet(a, x, 47, hexc("d08048"))
    elif style == "royal":
        fil = hp.curve([(24, 18), (27, 24), (24, 30), (28, 34)], 0.9, S) | hp.curve([(40, 18), (37, 24), (40, 30), (36, 34)], 0.9, S)
        hp.glow_paint(a, fil, trim, halo=0.25, core=False)
        hp.gem(a, 32, 27, 4.0, acc)
        for x in (21, 27, 37, 43):
            hp.gem(a, x, 47, 1.4, acc)
        for k in range(4):
            ang = 2.6 + k * 0.5
            hp.rivet(a, 15 + math.cos(ang) * 7, 17 + math.sin(ang) * 6, trim, size=1)
            hp.rivet(a, 49 - math.cos(ang) * 7, 17 + math.sin(ang) * 6, trim, size=1)
    return a


# ------------------------------------------------------------------------------------------------- legs

def legs(cfg):
    cv = hp.Canvas(S)
    main, trim, acc, glow = cfg["main"], cfg["trim"], cfg["accent"], cfg["glow"]
    style = cfg["chest"]
    left = P((15, 12), (31, 12), (30.5, 58), (18, 58), (15.5, 34))
    right = np.fliplr(left)
    soft = cfg["mat"] in ("leather", "cloth")
    cv.layer(left, main, cfg["mat"], seed=1)
    cv.layer(right, main, cfg["mat"], seed=2)
    waist = P((14, 6), (50, 6), (50, 13), (14, 13))
    wmat = "leather" if soft else cfg["trim_mat"] if style not in ("rock", "scaled") else "leather"
    cv.layer(waist, trim if not soft else hexc("3a2414"), wmat, seed=3)
    if not soft:
        # tassets over the hips
        for k, xs in enumerate(((15, 27), (37, 49))):
            t = P((xs[0], 13), (xs[1], 13), (xs[1] - 0.5, 23), (xs[0] + 0.5, 24))
            cv.layer(t, hp.scale(main, 1.12), cfg["mat"], seed=4 + k)
    if style == "royal" or style == "cuirass":
        tab = P((27, 13), (37, 13), (36, 32), (28, 32))
        cv.layer(tab, acc if style == "cuirass" else hexc("6a0c1e"), "cloth", seed=8)
    if style == "robe":
        skirt = P((13, 13), (51, 13), (54, 46), (10, 46))
        cv.layer(skirt, hp.scale(main, 1.1), "cloth", seed=9)
    # knee guards
    for k, cx in enumerate((23.5, 40.5)):
        if soft:
            patch = E(cx, 37, 5.5, 4.5)
            cv.layer(patch, hp.scale(main, 0.8), "leather", seed=10 + k)
        else:
            kn = E(cx, 37, 6.5, 5.2)
            kmat = {"ice": "crystal", "rock": "stone", "scaled": "bone"}.get(style, cfg["trim_mat"] if style in ("cuirass", "royal") else cfg["mat"])
            kc = {"ice": hp.mix(acc, (255, 255, 255), 0.3), "scaled": trim, "rock": hp.scale(main, 1.1)}.get(style, trim if style in ("cuirass", "royal", "spiked") else hp.scale(main, 1.2))
            cv.layer(kn, kc, kmat, seed=10 + k)
    cv.seams()
    a = cv.a
    # details
    if soft and style != "robe":
        hp.stitches(a, hp.thick_line((23, 14), (24, 56), 1, S) | hp.thick_line((41, 14), (40, 56), 1, S), hexc("d8b88a"), 3)
        hp.engrave(a, P((30, 7.5), (34, 7.5), (34, 11.5), (30, 11.5)))
        a[8:11, 31:33] = (*trim[:3], 255)
    for x in (18, 25, 39, 46):
        if not soft:
            hp.rivet(a, x, 8.5, hp.mix(trim, (255, 255, 255), 0.25))
    if style not in ("coat", "robe"):
        for y in (46, 51):
            hp.engrave(a, (hp.thick_line((16, y), (31, y), 0.9, S) | hp.thick_line((33, y), (48, y), 0.9, S)) & (left | right))
    if glow is not None:
        if style == "molten":
            hp.glow_paint(a, hp.cracks(left | right, 5, n=8, length=11), acc, halo=0.45)
        elif style in ("spiked", "ice", "robe", "scaled"):
            for cx in (23.5, 40.5):
                hp.glow_paint(a, E(cx, 37, 1.6, 1.6), glow, halo=0.5)
        elif style == "royal":
            pass
    if style == "royal":
        for cx in (23.5, 40.5):
            hp.gem(a, cx, 37, 2.2, acc)
    if style == "rock":
        hp.engrave(a, hp.cracks(left | right, 9, n=6, length=9), 0.4)
    return a


# ------------------------------------------------------------------------------------------------- boots

def boots(cfg):
    cv = hp.Canvas(S)
    main, trim, acc, glow = cfg["main"], cfg["trim"], cfg["accent"], cfg["glow"]
    style = cfg["boots"]
    soft = cfg["mat"] in ("leather", "cloth")
    for k, x0 in enumerate((6, 36)):
        x0 += 3
        shaft = P((x0 + 2, 12), (x0 + 16, 12), (x0 + 16.5, 42), (x0 + 1.5, 40))
        foot = P((x0 + 16.5, 36), (x0 + 17, 53), (x0 - 7, 53), (x0 - 8.5, 49.5), (x0 - 6.5, 45.5), (x0 - 1, 42), (x0 + 2, 36))
        if style == "ice":
            for j, h in enumerate((6, 9)):
                sp = P((x0 + 4 + j * 7, 15), (x0 + 6.5 + j * 7, h), (x0 + 9 + j * 7, 15))
                cv.layer(sp, hp.mix(acc, (255, 255, 255), 0.35), "crystal", seed=40 + k * 3 + j)
        if style in ("spiked", "molten"):
            sp = P((x0 + 16, 22), (x0 + 22, 25), (x0 + 16, 28))
            cv.layer(sp, trim if style == "spiked" else hexc("2a1e1c"), "metal" if style == "spiked" else "obsidian", seed=50 + k)
        cv.layer(shaft | foot, main, cfg["mat"], seed=1 + k)
        sole = P((x0 - 8.5, 51), (x0 + 17, 51), (x0 + 17, 55), (x0 - 7.5, 55))
        cv.layer(sole, hexc("2a1c16") if soft else dark_of(main, 0.5), "leather", seed=5 + k)
        cuff = P((x0 + 1, 13), (x0 + 17, 13), (x0 + 17, 19), (x0 + 1, 19))
        if style == "laced":
            cv.layer(cuff, hexc("6a4a2e"), "fur", seed=7 + k)
        elif style == "wraps":
            cv.layer(cuff, trim, "dark_metal", seed=7 + k)
        else:
            cv.layer(cuff, trim, cfg["trim_mat"] if style not in ("rock",) else "gold", seed=7 + k)
        if not soft and style != "wraps":
            toe = P((x0 - 6.5, 45.5), (x0 + 1, 41.5), (x0 + 5, 51), (x0 - 8.5, 51), (x0 - 8.5, 49.5))
            cv.layer(toe, hp.scale(main, 1.15) if style != "royal" else trim, cfg["mat"] if style != "royal" else "gold", seed=9 + k)
            shin = P((x0 + 5, 20), (x0 + 13, 20), (x0 + 12.5, 36), (x0 + 9, 39), (x0 + 5.5, 36))
            smat = {"ice": "ice", "rock": "stone", "scaled": "bone"}.get(style, cfg["mat"])
            sc = {"scaled": trim}.get(style, hp.scale(main, 1.2))
            cv.layer(shin, sc, smat, seed=11 + k)
    cv.seams()
    a = cv.a
    for x0 in (9, 39):
        if style == "laced":
            for y in range(22, 42, 4):
                a[y, x0 + 7:x0 + 11] = (215, 190, 150, 255)
                a[y + 1, x0 + 7] = (90, 60, 40, 255)
            hp.rivet(a, x0 + 3, 46, trim, size=1)
        elif style == "wraps":
            for y in range(20, 44, 5):
                hp.engrave(a, hp.thick_line((x0 + 2, y), (x0 + 16, y + 3), 1, S))
            hp.glow_paint(a, E(x0 + 9, 30, 1.4, 1.4), glow, halo=0.5)
        else:
            hp.rivet(a, x0 + 3, 15, hp.mix(trim, (255, 255, 255), 0.3))
            hp.rivet(a, x0 + 13, 15, hp.mix(trim, (255, 255, 255), 0.3))
            if style == "molten":
                hp.glow_paint(a, hp.cracks(a[..., 3] > 0, 40 + x0, n=4, length=9) & (np.arange(S)[None, :] >= x0 - 5) & (np.arange(S)[None, :] < x0 + 18), acc, halo=0.45)
            if style in ("spiked", "ice", "scaled"):
                hp.glow_paint(a, E(x0 + 9, 29, 1.3, 1.3), glow, halo=0.5)
            if style == "royal":
                hp.gem(a, x0 + 9, 28, 2.0, acc)
            if style == "rock":
                hp.engrave(a, hp.cracks(P((x0 + 2, 20), (x0 + 16, 20), (x0 + 16, 44), (x0 + 2, 44)), 60 + x0, n=3, length=8), 0.4)
            if style == "sabatons":
                for y in (24, 30, 36):
                    hp.engrave(a, hp.thick_line((x0 + 2, y), (x0 + 16, y), 0.9, S))
    return a


# ------------------------------------------------------------------------------------------------- worn layers

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}


K = 2
HEAD = faces(0, 0, 8 * K, 8 * K, 8 * K)
BODY = faces(16 * K, 16 * K, 8 * K, 12 * K, 4 * K)
ARM = faces(40 * K, 16 * K, 4 * K, 12 * K, 4 * K)
LEG = faces(0, 16 * K, 4 * K, 12 * K, 4 * K)
LW, LH = 128, 64


def rect_mask(x, y, w, h, rows=None):
    m = np.zeros((LH, LW), bool)
    r0, r1 = (0, h) if rows is None else rows
    m[y + r0:y + r1, x:x + w] = True
    return m


def panel(cv, rect, color, mat, seed, rows=None, bias=0.0):
    x, y, w, h = rect
    m = rect_mask(x, y, w, h, rows)
    cv.layer(m, color, mat, seed=seed, light_bias=bias)
    return m


def layers(cfg):
    main, trim, acc, glow = cfg["main"], cfg["trim"], cfg["accent"], cfg["glow"]
    mat, tmat = cfg["mat"], cfg["trim_mat"]
    soft = mat in ("leather", "cloth")
    style = cfg["chest"]
    helm = cfg["helm"]
    l1 = hp.Canvas(LW, LH)
    l2 = hp.Canvas(LW, LH)
    seed = 1
    # ---- helmet
    for f, rect in HEAD.items():
        panel(l1, rect, main if helm not in ("crown",) else main, mat if helm not in ("hood", "reaper") else ("cloth" if helm == "reaper" else "leather"), seed)
        seed += 1
    hx, hy, hw, hh = HEAD["front"]
    if helm in ("hood", "reaper"):
        l1.a[hy + 5:hy + 16, hx + 3:hx + 13] = 0
        l1.a[hy + 4:hy + 5, hx + 2:hx + 14] = (*hp.scale(main, 0.5)[:3], 255)
        if helm == "reaper":
            for f in ("front", "left", "right", "back"):
                x, y, w, h = HEAD[f]
                hp.glow_paint(l1.a, rect_mask(x, y + 1, w, 1), trim, halo=0.2, core=False)
    else:
        # brow band all round
        for f in ("front", "left", "right", "back"):
            x, y, w, h = HEAD[f]
            panel(l1, (x, y, w, h), trim, tmat, seed, rows=(3, 6))
            seed += 1
        if helm == "greathelm":
            for f in ("front", "back", "top"):
                x, y, w, h = HEAD[f]
                panel(l1, (x + w // 2 - 1, y, 2, h), trim, tmat, seed)
                seed += 1
            l1.a[hy + 7:hy + 9, hx + 1:hx + 15] = (12, 10, 18, 255)
            for yy in range(hy + 11, hy + 15, 2):
                for xx in (hx + 3, hx + 5, hx + 10, hx + 12):
                    l1.a[yy, xx] = (14, 12, 20, 255)
        elif helm == "sallet":
            l1.a[hy + 7:hy + 9, hx + 1:hx + 15] = (12, 8, 16, 255)
            hp.glow_paint(l1.a, rect_mask(hx + 2, hy + 7, 12, 1), glow, halo=0.4)
        else:
            # T-visor
            l1.a[hy + 7:hy + 9, hx + 3:hx + 13] = (12, 9, 16, 255)
            l1.a[hy + 9:hy + 14, hx + 7:hx + 9] = (12, 9, 16, 255)
            if glow is not None:
                hp.glow_paint(l1.a, rect_mask(hx + 4, hy + 7, 3, 1) | rect_mask(hx + 9, hy + 7, 3, 1), glow, halo=0.4)
            if helm == "crown":
                for f in ("front", "left", "right", "back"):
                    x, y, w, h = HEAD[f]
                    panel(l1, (x, y, w, h), trim, "gold", seed, rows=(0, 3))
                    seed += 1
                    for xx in range(x + 1, x + w - 1, 5):
                        hp.gem(l1.a, xx + 1.5, y + 1.5, 1.2, acc)
            if helm == "fanged":
                for xx in (hx + 4, hx + 11):
                    l1.a[hy + 14:hy + 16, xx] = (*trim[:3], 255)
            if helm == "icecrown":
                hp.gem(l1.a, hx + 8, hy + 4.5, 1.6, acc)
            if helm == "horned_ember":
                hp.glow_paint(l1.a, hp.cracks(rect_mask(*HEAD["front"]) | rect_mask(*HEAD["left"]) | rect_mask(*HEAD["right"]), 3, n=5, length=8), acc, halo=0.4)
    # ---- chestplate: body + arms
    for f, rect in BODY.items():
        panel(l1, rect, main, mat, seed)
        seed += 1
    bx, by, bw, bh = BODY["front"]
    if style == "cuirass":
        panel(l1, (bx + 4, by + 13, 8, 11), acc, "cloth", seed); seed += 1
        panel(l1, (bx, by, bw, 2), trim, "gold", seed); seed += 1
        panel(l1, (bx, by + 11, bw, 2), trim, "gold", seed); seed += 1
        hp.engrave(l1.a, rect_mask(bx + 8, by + 2, 1, 9))
        l1.a[by + 15:by + 19, bx + 7:bx + 9] = (*trim[:3], 255)
    elif style == "coat":
        hp.stitches(l1.a, rect_mask(bx + 1, by, 1, bh) | rect_mask(bx + bw - 2, by, 1, bh), hexc("d8b88a"), 3)
        strap = np.zeros((LH, LW), bool)
        for i in range(bh - 6):
            strap[by + i, bx + 2 + int(i * 0.6):bx + 5 + int(i * 0.6)] = True
        l1.layer(strap & rect_mask(*BODY["front"]), hexc("4a2e1a"), "leather", seed=seed); seed += 1
        panel(l1, (bx, by + 18, bw, 3), hexc("3a2414"), "leather", seed); seed += 1
        l1.a[by + 18:by + 21, bx + 7:bx + 9] = (*trim[:3], 255)
    else:
        plate_c = {"rock": hp.scale(main, 1.05), "ice": hp.mix(main, (255, 255, 255), 0.35), "robe": hp.scale(main, 1.1),
                   "molten": hexc("4a3634"), "royal": hp.scale(main, 1.1), "spiked": hp.scale(main, 1.25), "scaled": main}[style]
        pm = {"rock": "stone", "ice": "ice", "robe": "cloth", "molten": "obsidian", "royal": "metal", "spiked": "dark_metal", "scaled": "scale"}[style]
        panel(l1, (bx + 3, by + 2, 10, 12), plate_c, pm, seed); seed += 1
        belt_c = {"scaled": hexc("3a2a1c"), "ice": hexc("3a6a94"), "robe": hexc("1a1428")}.get(style, trim)
        panel(l1, (bx, by + 17, bw, 3), belt_c, "leather" if style in ("scaled", "robe") else tmat if tmat != "ice" else "metal", seed); seed += 1
        if style == "royal":
            panel(l1, (bx, by, bw, 2), trim, "gold", seed); seed += 1
            hp.gem(l1.a, bx + 8, by + 7, 2.2, acc)
        if style == "ice":
            hp.gem(l1.a, bx + 8, by + 7, 2.2, acc)
        if style == "rock":
            hp.gem(l1.a, bx + 8, by + 6, 1.8, hexc("c0392b"))
        if style == "scaled":
            for yy in (by + 4, by + 7, by + 10):
                l1.layer(rect_mask(bx + 3, yy, 10, 1), trim, "bone", seed=seed); seed += 1
        if glow is not None and style in ("spiked", "robe", "scaled"):
            hp.glow_paint(l1.a, rect_mask(bx + 7, by + 4, 2, 8) | rect_mask(bx + 5, by + 7, 6, 1), glow, halo=0.35)
        if style == "molten":
            hp.glow_paint(l1.a, hp.cracks(rect_mask(*BODY["front"]) | rect_mask(*BODY["back"]), 8, n=8, length=10), acc, halo=0.4)
            hp.gem(l1.a, bx + 8, by + 7, 1.8, hexc("ffb030"))
    # back plate lines
    x, y, w, h = BODY["back"]
    for yy in (y + 5, y + 10, y + 15):
        hp.engrave(l1.a, rect_mask(x + 1, yy, w - 2, 1))
    for f, rect in ARM.items():
        panel(l1, rect, main, mat, seed)
        seed += 1
        if f in ("top", "bottom"):
            continue
        x, y, w, h = rect
        pcol = {"coat": hp.scale(main, 0.85), "spiked": trim, "molten": hexc("2a1e1c"), "robe": trim, "scaled": trim,
                "royal": trim}.get(style, main)
        pmat = {"coat": "leather", "spiked": "metal", "molten": "obsidian", "robe": "dark_metal", "scaled": "bone",
                "royal": "gold", "rock": "stone", "ice": "metal"}.get(style, mat)
        panel(l1, (x, y, w, 8), pcol, pmat, seed); seed += 1
        panel(l1, (x, y + 8, w, 2), trim if style not in ("coat",) else hexc("3a2414"), tmat if style not in ("coat", "robe") else "leather", seed); seed += 1
        panel(l1, (x, y + 18, w, 5), trim if style != "coat" else hexc("4a2e1a"), tmat if style not in ("coat", "robe") else "leather", seed); seed += 1
        if not soft:
            hp.rivet(l1.a, x + 1, y + 3, hp.mix(trim, (255, 255, 255), 0.3), size=1)
            hp.rivet(l1.a, x + w - 3, y + 3, hp.mix(trim, (255, 255, 255), 0.3), size=1)
        if style == "molten":
            hp.glow_paint(l1.a, rect_mask(x + 2, y + 2, 1, 4) | rect_mask(x + 5, y + 12, 1, 5), acc, halo=0.4)
        if style in ("spiked", "ice", "royal") and f in ("front",):
            hp.glow_paint(l1.a, rect_mask(x + 3, y + 3, 2, 2), glow if glow else trim, halo=0.4)
    # ---- boots: lower legs on layer 1
    for f, rect in LEG.items():
        x, y, w, h = rect
        if f == "top":
            continue
        if f == "bottom":
            panel(l1, rect, dark_of(main, 0.5), "leather", seed); seed += 1
            continue
        panel(l1, rect, main, mat, seed, rows=(14, h)); seed += 1
        cuffc = hexc("6a4a2e") if cfg["boots"] == "laced" else trim
        panel(l1, rect, cuffc, "fur" if cfg["boots"] == "laced" else (tmat if tmat != "ice" else "metal"), seed, rows=(14, 16)); seed += 1
        panel(l1, rect, dark_of(main, 0.5), "leather", seed, rows=(h - 2, h)); seed += 1
        if cfg["boots"] == "laced" and f == "front":
            for yy in range(y + 17, y + h - 3, 2):
                l1.a[yy, x + 2:x + 6] = (215, 190, 150, 255)
        if not soft and f == "front":
            hp.rivet(l1.a, x + 3, y + 18, hp.mix(trim, (255, 255, 255), 0.3), size=1)
            if glow is not None:
                hp.glow_paint(l1.a, rect_mask(x + 3, y + 20, 2, 1), glow, halo=0.4)
    # ---- leggings (layer 2): waist + upper legs
    for f, rect in BODY.items():
        x, y, w, h = rect
        if f in ("top", "bottom"):
            continue
        panel(l2, rect, main, mat, seed, rows=(16, h)); seed += 1
        panel(l2, rect, trim if not soft else hexc("3a2414"), tmat if not soft and tmat != "ice" else "leather", seed, rows=(16, 19)); seed += 1
        if f == "front" and style in ("cuirass", "royal"):
            panel(l2, (x + 5, y + 19, 6, 5), acc if style == "cuirass" else hexc("6a0c1e"), "cloth", seed); seed += 1
    for f, rect in LEG.items():
        x, y, w, h = rect
        if f == "bottom":
            continue
        if f == "top":
            panel(l2, rect, main, mat, seed); seed += 1
            continue
        panel(l2, rect, main, mat, seed, rows=(0, 20)); seed += 1
        if soft:
            panel(l2, rect, hp.scale(main, 0.8), "leather", seed, rows=(9, 13)); seed += 1
            hp.stitches(l2.a, rect_mask(x + w // 2, y, 1, 20), hexc("d8b88a"), 3)
        else:
            kc = {"ice": hp.mix(acc, (255, 255, 255), 0.3), "scaled": trim}.get(style, trim if style in ("cuirass", "royal", "spiked") else hp.scale(main, 1.2))
            km = {"ice": "crystal", "rock": "stone", "scaled": "bone"}.get(style, tmat if style in ("cuirass", "royal") else mat)
            panel(l2, rect, kc, km, seed, rows=(9, 13)); seed += 1
            hp.engrave(l2.a, rect_mask(x, y + 4, w, 1))
            hp.engrave(l2.a, rect_mask(x, y + 16, w, 1))
            if f == "front":
                if style == "royal":
                    hp.gem(l2.a, x + 4, y + 11, 1.4, acc)
                elif glow is not None:
                    hp.glow_paint(l2.a, rect_mask(x + 3, y + 10, 2, 2), glow, halo=0.4)
        if style == "molten":
            hp.glow_paint(l2.a, hp.cracks(rect_mask(x, y, w, 20), 70 + x, n=2, length=7), acc, halo=0.4)
    for cv in (l1, l2):
        cv.seams(0.25)
        hp.grain(cv.a, seed=4)
    return Image.fromarray(l1.a), Image.fromarray(l2.a)


# ------------------------------------------------------------------------------------------------- output

def icons(cfg):
    out = []
    accent = cfg["glow"] or GRADE_ACCENT[cfg["grade"]]
    for k, fn in enumerate((helmet, chest, legs, boots)):
        a = fn(cfg)
        out.append(hp.finish(a, cfg["grade"], accent, None, seed=11 + k))
    return out


def main(only=None):
    os.makedirs(ITEM_OUT, exist_ok=True)
    os.makedirs(ARMOR_OUT, exist_ok=True)
    for name, cfg in SETS.items():
        if only and name not in only:
            continue
        for item, img in zip(cfg["items"], icons(cfg)):
            img.save(os.path.join(ITEM_OUT, item + ".png"))
        l1, l2 = layers(cfg)
        l1.save(os.path.join(ARMOR_OUT, name + "_layer_1.png"))
        l2.save(os.path.join(ARMOR_OUT, name + "_layer_2.png"))
    print("hd armor written")


if __name__ == "__main__":
    main(set(sys.argv[1:]) or None)
