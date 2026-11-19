#!/usr/bin/env python3
"""
Monarch armor's art (com.beyondsolo.worldboss.MonarchArmor): the regalia a slain monarch leaves, one set for each,
shaped like that monarch's own armor, black as a night without stars, every seam, edge, crest and crack of it a
channel that the wearer's own aura burns in.

- Stillness (Veyrith's): a queen's plate, a crown of three crystals over a cheeked helm, crystal at the shoulders, a
  long cape and a skirt of plates.
- Hunger (Khorvane's): a wolf's skull over a closed helm, its teeth alight, a black fur mantle, spikes down the back
  and on the pauldrons, veins of light breaking through the plate.
- Storms (Thyrax's): a great helm winged with steel feathers and crested, steel wings on the back, double pauldrons,
  a bolt on the breast, winged heels.

For each set this writes (the armor rig, painters and icon renderer are the armory's: tools/armor_worn.py,
tools/armor_icons.py):
- textures/models/armor/<set>_layer_1.png and _layer_2.png: the plate, its channels dark;
- textures/models/armor/<set>_aura_1.png and _aura_2.png: the channels as white light, drawn over the plate in the
  wearer's colour by client/worldboss/MonarchAuraLayer (breathing; the outline of the wearer burns there too);
- models/armor/<set>.json: the extra parts, read by client/armor/ArmorModels;
- each piece's icon in two layers, textures/item/<item>.png (the plate) and <item>_aura.png (its light, the halo and
  the sparkles in white: the item colour handler tints that layer with the aura of the piece's last wearer), and the
  item models.
And the Monarch's Seal's icon (textures/item/monarch_seal.png).

Run from the repo root:  python3 tools/gen_monarch_armor.py [set ...]
                         python3 tools/gen_monarch_armor.py --preview monarch_storms [rrggbb]   (worn, lit by an aura)
Needs: pillow, numpy.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_icons  # noqa: E402
import armor_kit as ak  # noqa: E402
import armor_preview as ap  # noqa: E402
import armor_worn  # noqa: E402
import hd_paint as hp  # noqa: E402
import preview_model as pm  # noqa: E402
from hd_paint import hexc  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
ARMOR_OUT = os.path.join(ASSETS, "textures", "models", "armor")
GEO_OUT = os.path.join(ASSETS, "models", "armor")
ITEM_OUT = os.path.join(ASSETS, "textures", "item")
MODEL_OUT = os.path.join(ASSETS, "models", "item")
PREVIEW_OUT = os.path.join(ROOT, "build", "preview")

# the channels: painted in this one colour, found again by it, and turned into the light layer
AURA = "f6f4ff"
PIECES = ("helm", "chestplate", "leggings", "boots")

armor_worn.GLYPHS.setdefault("bolt", ["    ## ", "   ##  ", "  ##   ", " ##### ", "   ##  ", "  ##   ", " ##    "])


def pal(**extra):
    """Night-black plate; every key that can be a channel (trim, glow, gems, crystal, bone) is the aura's."""
    p = {"metal": ("1e1d26", "metal"), "trim": (AURA, "glow"), "glow": AURA, "gem": (AURA, "glow"), "crystal": (AURA, "crystal"),
         "bone": (AURA, "bone"), "mail": "131219", "leather": "17151d", "leather2": "0e0d12", "cloth": "15141b", "cloth2": "1f1d27",
         "lining": "0b0a0f", "dark": "040306", "fur": "1b1920", "thread": "2c2a36"}
    p.update(extra)
    return p


DESIGNS = {
    "monarch_stillness": {
        "pal": pal(),
        "head": {"kind": "helm", "style": "cheek", "face": "eyes", "eyes": True, "trim": "trim", "ridge_key": "trim", "gem": True,
                 "gem_y": 1, "hat": [("crown", dict(key="metal"))], "extras": [("crystals", dict(key="crystal", n=3))]},
        "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "snow", "emblem_key": "trim",
                  "emblem_glow": 1.0, "gem": True, "gem_y": 3, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
                  "cracks": "glow", "crack_n": 2,
                  "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, size=1.15, gem_=True)),
                             ("crystals", dict(key="crystal", where="shoulder")),
                             ("cape", dict(key="cloth", trim="trim", lining="lining", length=19))]},
        "legs": {"kind": "plate", "under": "mail", "trim": "trim", "etch": True,
                 "extras": [("skirt", dict(key="metal", trim="trim", length=8))]},
        "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "extras": [("toes", dict(key="metal", trim="trim"))]},
    },
    "monarch_hunger": {
        "pal": pal(fur="2e2a35", stone=("34303c", "bone")),
        "head": {"kind": "bone_mask", "under": "helm", "helm_style": "sallet", "mask": "skull", "bone": "stone", "trim": "trim",
                 "ridge_key": "trim",
                 "extras": [("wolf", dict(fur_key="fur", tooth="bone", inner="leather")), ("horns", dict(key="crystal", style="up", size=0.9))]},
        "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "fang", "emblem_key": "trim",
                  "emblem_glow": 1.0, "pauldron_rows": 0, "cracks": "glow", "crack_n": 3,
                  "extras": [("fur_mantle", dict(key="fur")), ("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=3, size=1.15)),
                             ("spine", dict(key="crystal", n=3))]},
        "legs": {"kind": "plate", "under": "mail", "trim": "trim", "cracks": "glow", "crack_n": 2,
                 "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
        "feet": {"kind": "plate", "trim": "trim", "glow_line": True, "cracks": "glow",
                 "extras": [("boot_spikes", dict(key="crystal")), ("toes", dict(key="metal", trim="trim"))]},
    },
    "monarch_storms": {
        "pal": pal(),
        "head": {"kind": "helm", "style": "greathelm", "trim": "trim", "ridge_key": "trim", "eyes": True,
                 "extras": [("wings", dict(key="metal", key2="trim", size=1.1)), ("crest", dict(key="metal"))]},
        "chest": {"kind": "plate", "under": "mail", "shape": "pointed", "trim": "trim", "emblem": "bolt", "emblem_key": "trim",
                  "emblem_glow": 1.0, "gem": True, "gem_y": 1, "pauldron_rows": 0, "etch": True, "etch_key": "trim",
                  "extras": [("pauldrons", dict(key="metal", trim="trim", lame=3, spikes=2, size=1.15, layers=2)),
                             ("back_wings", dict(key="metal", key2="trim", size=1.3))]},
        "legs": {"kind": "plate", "under": "mail", "trim": "trim", "etch": True,
                 "extras": [("tassets", dict(key="metal", trim="trim", length=5))]},
        "feet": {"kind": "plate", "trim": "trim", "glow_line": True,
                 "extras": [("heel_wings", dict(key="metal", key2="trim")), ("toes", dict(key="metal", trim="trim"))]},
    },
}


# ------------------------------------------------------------------------------------------------- the worn set

def build(name):
    """The set's worn layers with its light taken out of them: {1, 2: plate RGBA, "aura1", "aura2": light RGBA,
    "parts", "uv_height"}."""
    d = DESIGNS[name]
    w = armor_worn.Worn(d)
    res = w.build()
    aura = tuple(hexc(AURA)[:3])
    metal = d["pal"]["metal"]
    for layer in (1, 2):
        cv = w.cv[layer]
        ids = [ix for (c, _kind), ix in cv._ramp_ix.items() if tuple(c[:3]) == aura]
        solid = [ix for (c, kind), ix in cv._ramp_ix.items() if tuple(c[:3]) == aura and kind != "glow"]
        mask = (np.isin(cv.rid, ids) | (cv.glow > 0)) & (res[layer][..., 3] > 0)
        plate = res[layer].copy()
        # wide stretches of light (a wing's coverts, a broad rim) burn only round their edges: the plate shows in
        # their middle. Crystals, horns, teeth, gems, eyes and glowing marks stay whole.
        keep = np.isin(cv.rid, solid) | (cv.glow > 0)
        inner = _inside(cv, mask, HOLLOW_DEPTH) & ~keep
        if inner.any():
            ramp = cv.ramps[cv.r(metal[0] if isinstance(metal, tuple) else metal, metal[1] if isinstance(metal, tuple) else "metal")]
            t = np.clip(np.round(cv.tone), -3, 3).astype(int) + 3
            plate[inner, 0:3] = ramp[t[inner]]
            mask &= ~inner
        lum = plate[..., :3].astype(np.float32).mean(-1) / 255.0
        light = np.zeros_like(plate)
        # the light keeps the painter's shading (its tone, in steps of a ramp: the light ramp itself is nearly white
        # all the way): hot on lit faces and edges, dimmer in the hollows and on the faces turned away
        inten = np.clip(0.64 + 0.13 * cv.tone, 0.3, 1.0)
        light[mask, 0:3] = np.round(inten[mask, None] * 255).astype(np.uint8)
        light[mask, 3] = 255
        # under the light the channel is dark: the aura's colour shows true, not washed out by a pale plate
        ch = 14 + 40 * lum
        plate[mask, 0] = np.round(ch[mask] * 0.96).astype(np.uint8)
        plate[mask, 1] = np.round(ch[mask] * 0.94).astype(np.uint8)
        plate[mask, 2] = np.round(ch[mask] * 1.1).astype(np.uint8)
        res[layer] = night_plate(plate, mask)
        res["aura%d" % layer] = light
    return res


HOLLOW_DEPTH = 2


def night_plate(img, channels):
    """Presses the plate down to the series' blue-black (/mnt/project-files/art-style/STYLE-GUIDE.md, rule 1: darks
    #07080F to #1A1D29, never grey): its sheen kept low and cold, a brightest edge no lighter than about #3A4268. The
    channels under the light are left as they are."""
    out = img.copy()
    rgb = out[..., :3].astype(np.float32)
    lum = rgb.mean(-1)
    target = 8 + 0.42 * np.maximum(lum - 8, 0)
    k = np.where(lum > 1, target / np.maximum(lum, 1), 1.0)
    rgb = rgb * k[..., None] * np.array([0.9, 0.96, 1.18], np.float32)
    sel = (out[..., 3] > 0) & ~channels
    out[sel, 0:3] = np.clip(np.round(rgb[sel]), 0, 255).astype(np.uint8)
    return out


def _inside(cv, mask, depth):
    """Pixels of mask with all four neighbours (across the seams between faces too) inside it, `depth` times over."""
    cur = mask.copy()
    for _ in range(depth):
        m = cur.astype(np.int8)
        nxt = cur.copy()
        for dd in range(4):
            nxt &= cv.nbr(m, dd, fill=0) > 0
        cur = nxt
    return cur


# ------------------------------------------------------------------------------------------------- icons

HALO = (150, 100, 58, 26)
SPARKLES = 7


def icons(name, res):
    """Four (plate, light) icon pairs, 64x64: the plate drawn as every armor icon is (tools/armor_icons.py), the light
    from the same camera on the light layers, then the halo and sparkles added to the light."""
    out = []
    lit = tuple(_lit_layer(res, layer) for layer in (1, 2))
    for k, slot in enumerate(("head", "chest", "legs", "feet")):
        v = armor_icons.VIEWS[slot]
        quads = ap.scene_quads(name, (slot,), v["pose"], skin=False, parts=res["parts"], shift=v.get("shift"), cut=v.get("cut"))
        big = armor_icons.render_piece(quads, ap.atlas(name, tex=(res[1], res[2])), v["yaw"], v["pitch"], fill=v["fill"])
        big_l = armor_icons.render_piece(quads, ap.atlas(name, tex=lit), v["yaw"], v["pitch"], fill=v["fill"])
        plate = ak.sel_out(armor_icons.downsample(big), 0.66)
        lum = armor_icons.downsample(big_l)[..., :3].astype(np.float32).max(-1) / 255.0
        light = np.zeros_like(plate)
        on = (lum > 0.2) & (plate[..., 3] > 0)
        light[on, 0:3] = np.round(np.clip(lum[on] * 1.15, 0, 1)[:, None] * 255).astype(np.uint8)
        light[on, 3] = 255
        # the halo round the piece and the sparkles: drawn on a copy, kept only where they land
        halo = plate.copy()
        hp.aura(halo, (255, 255, 255, 255), HALO)
        ring = (plate[..., 3] == 0) & (halo[..., 3] > 0)
        light[ring] = halo[ring]
        both = plate.copy()
        both[on] = light[on]
        spark = both.copy()
        hp.sparkles(spark, SPARKLES, (255, 255, 255), seed=11 + k)
        changed = np.any(spark != both, axis=-1)
        light[changed] = spark[changed]
        light[changed, 0:3] = 255
        out.append((plate, light))
    return out


def _lit_layer(res, layer):
    """A worn layer for drawing the light: white light on the channels, black on the plate, the plate's own alpha (so
    the light is drawn and framed exactly as the plate is)."""
    a = np.zeros_like(res[layer])
    a[..., 3] = res[layer][..., 3]
    g = res["aura%d" % layer]
    on = g[..., 3] > 0
    a[on, 0:3] = g[on, 0:3]
    return a


# ------------------------------------------------------------------------------------------------- the seal

def seal_icon(size=64, ss=4):
    """The Monarch's Seal: a disc of black stone sealed with a violet crown of light, cracked across, its light
    leaking from the crack."""
    S = size * ss
    ys, xs = (np.mgrid[0:S, 0:S] + 0.5) / ss
    c = size / 2
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy)
    R = 25.0
    img = np.zeros((S, S, 4), np.float32)
    disc = r <= R
    # stone: domed light from the top left, a raised rim, fine grain
    rng = np.random.default_rng(7)
    grain = np.asarray(Image.fromarray((rng.random((size, size)) * 255).astype(np.uint8)).resize((S, S), Image.BILINEAR)) / 255.0
    dome = np.clip(1 - (r / R) ** 2, 0, 1) ** 0.5
    light = 0.55 + 0.45 * np.clip((-dx * 0.6 - dy * 0.8) / R, -1, 1)
    rim = (r > R - 3.2) & disc
    base = np.array([34, 30, 44], np.float32)
    shade = (0.55 + 0.35 * dome * light + 0.12 * (grain - 0.5))
    shade = np.where(rim, 0.75 + 0.45 * np.clip((-dx * 0.6 - dy * 0.8) / R, -1, 1), shade)
    groove = disc & (np.abs(r - (R - 3.6)) < 0.55)
    shade = np.where(groove, 0.35, shade)
    img[disc, 0:3] = base * shade[disc, None]
    img[disc, 3] = 255
    # the crown sigil: three points over a band, in violet light
    violet = np.array([199, 125, 255], np.float32)
    band = (np.abs(dy - 7.0) < 2.6) & (np.abs(dx) < 14.0)
    points = np.zeros_like(disc)
    for px, h in ((-11.0, 12.0), (0.0, 17.0), (11.0, 12.0)):
        top = 4.6 - h
        t = (dy - top) / (4.6 - top)
        points |= (dy >= top) & (dy <= 4.6) & (np.abs(dx - px) <= 0.5 + 4.2 * np.clip(t, 0, 1))
    gem = np.hypot(dx, dy - 12.6) < 2.4
    sigil = (band | points | gem) & disc
    edge = sigil & ~(np.roll(sigil, ss, 0) & np.roll(sigil, -ss, 0) & np.roll(sigil, ss, 1) & np.roll(sigil, -ss, 1))
    img[sigil, 0:3] = violet * 0.85
    img[edge, 0:3] = np.minimum(violet * 1.25, 255)
    core = sigil & (np.abs(dy - 7.0) < 0.8) & (np.abs(dx) < 12.5)
    img[core, 0:3] = (245, 225, 255)
    # the crack: a jagged line across the disc, light pouring out of it
    dist = np.full(disc.shape, 1e9, np.float32)
    pts = [(-R, 11.0), (-15, 8.0), (-9, 11.0), (-2, 3.0), (4, 6.0), (11, -4.0), (16, -2.0), (R, -10.0)]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        t = np.clip(((xs - c - x0) * (x1 - x0) + (ys - c - y0) * (y1 - y0)) / ((x1 - x0) ** 2 + (y1 - y0) ** 2), 0, 1)
        dist = np.minimum(dist, np.hypot(xs - c - (x0 + t * (x1 - x0)), ys - c - (y0 + t * (y1 - y0))))
    crack = disc & (dist < 0.62)
    glow_near = disc & ~crack
    img[crack, 0:3] = (255, 240, 255)
    # the light leaking from the crack tints the stone round it
    leak = np.clip(1 - dist / 3.5, 0, 1) ** 1.5 * glow_near
    img[..., 0:3] = img[..., 0:3] * (1 - leak[..., None] * 0.6) + violet * leak[..., None] * 0.6
    small = armor_icons.downsample(img)
    out = ak.sel_out(small, 0.66)
    hp.aura(out, (199, 125, 255, 255), (130, 80, 40, 16))
    hp.sparkles(out, 5, (230, 200, 255), 3)
    return out


# ------------------------------------------------------------------------------------------------- writing

def write_set(name):
    res = build(name)
    for d in (ARMOR_OUT, GEO_OUT, ITEM_OUT, MODEL_OUT):
        os.makedirs(d, exist_ok=True)
    for layer in (1, 2):
        Image.fromarray(res[layer], "RGBA").save(os.path.join(ARMOR_OUT, "%s_layer_%d.png" % (name, layer)), optimize=True)
        Image.fromarray(res["aura%d" % layer], "RGBA").save(os.path.join(ARMOR_OUT, "%s_aura_%d.png" % (name, layer)), optimize=True)
    with open(os.path.join(GEO_OUT, name + ".json"), "w") as f:
        json.dump({"texture_height": {"1": res["uv_height"][1], "2": res["uv_height"][2]}, "parts": res["parts"]}, f, indent=1)
        f.write("\n")
    for piece, (plate, light) in zip(PIECES, icons(name, res)):
        item = name + "_" + piece
        Image.fromarray(plate, "RGBA").save(os.path.join(ITEM_OUT, item + ".png"), optimize=True)
        Image.fromarray(light, "RGBA").save(os.path.join(ITEM_OUT, item + "_aura.png"), optimize=True)
        with open(os.path.join(MODEL_OUT, item + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": "beyondsolo:item/" + item, "layer1": "beyondsolo:item/" + item + "_aura"}}, f, indent=2)
            f.write("\n")
    return res


def write_seal():
    Image.fromarray(seal_icon(), "RGBA").save(os.path.join(ITEM_OUT, "monarch_seal.png"), optimize=True)
    with open(os.path.join(MODEL_OUT, "monarch_seal.json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/monarch_seal"}}, f, indent=2)
        f.write("\n")


# ------------------------------------------------------------------------------------------------- previews

def preview(name, rgb="c77dff", out=None, views=("fl", "front", "back", "bl"), size=(300, 420), dark=True):
    """The set worn and lit as the game lights it: the light layers added in the aura's colour."""
    res = build(name)
    col = np.array(hexc(rgb)[:3], np.float32) / 255.0
    A = ap.atlas(name, tex=(res[1], res[2]))
    G = ap.atlas(name, tex=tuple(_tinted(res["aura%d" % layer], col) for layer in (1, 2)))
    G[:, 256:] = 0
    quads = ap.scene_quads(name, ("head", "chest", "legs", "feet"), None, skin=True, parts=res["parts"])
    tiles = [pm.render(quads, A, G, size, target=(0, 17, 0), dist=58, fov=38, dark=dark, **ap.VIEWS[v]) for v in views]
    canvas = np.concatenate(tiles, axis=1)
    out = out or os.path.join(PREVIEW_OUT, "monarch_%s_%s.png" % (name, rgb))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(canvas).save(out)
    return out


def _tinted(light, col):
    t = light.copy()
    t[..., 0:3] = np.round(light[..., 0:3].astype(np.float32) * col).astype(np.uint8)
    return t


def icon_sheet(names, out, rgb="c77dff", scale=3):
    """Every icon of the sets as the game shows them: the light layer tinted and laid over the plate."""
    col = np.array(hexc(rgb)[:3], np.float32) / 255.0
    rows = []
    for name in names:
        res = build(name)
        row = []
        for plate, light in icons(name, res):
            a = plate.astype(np.float32)
            lt = light.astype(np.float32)
            la = lt[..., 3:4] / 255.0
            rgbv = a[..., :3] * (1 - la) + lt[..., :3] * col * la
            alpha = np.maximum(a[..., 3:4], lt[..., 3:4])
            row.append(np.concatenate([rgbv, alpha], -1))
        rows.append(np.concatenate(row, axis=1))
    img = np.concatenate(rows, axis=0)
    bg = np.zeros(img.shape[:2] + (3,), np.float32)
    bg[:] = (40, 38, 48)
    al = img[..., 3:4] / 255.0
    comp = bg * (1 - al) + img[..., :3] * al
    big = Image.fromarray(np.clip(comp, 0, 255).astype(np.uint8)).resize((img.shape[1] * scale, img.shape[0] * scale), Image.NEAREST)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    big.save(out)
    return out


def main(args):
    if args and args[0] == "--preview":
        print(preview(args[1], args[2] if len(args) > 2 else "c77dff"))
        return
    if args and args[0] == "--icons":
        print(icon_sheet(list(DESIGNS), os.path.join(PREVIEW_OUT, "monarch_icons.png"), args[1] if len(args) > 1 else "c77dff"))
        return
    names = [n for n in DESIGNS if not args or n in args]
    for name in names:
        write_set(name)
    if not args or "seal" in args:
        write_seal()
    print("monarch armor written:", ", ".join(names))


if __name__ == "__main__":
    main(sys.argv[1:])
