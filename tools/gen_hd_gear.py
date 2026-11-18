#!/usr/bin/env python3
"""
High-detail (64x64) textures for System gear. Run AFTER the older generators: it overwrites their sprites.

- all 47 weapons: the designs stay in tools/gen_combat_assets.py and tools/gen_arsenal_assets.py (32-unit
  coordinates); this script renders them at twice the resolution with smooth multi-tone shading, wrapped grips,
  wood grain, part seams and a tinted outline, then dresses them by grade (epic: etched glowing runes and a soft
  aura; legendary and up: brighter aura and sparkles; divine: white-gold radiance). The light sweep animation on
  legendary and up is rebuilt at 64x64.
- all 9 armor sets: item icons, worn layers and their 3D parts, by tools/gen_armory_assets.py (the same as the
  armory's sets)
- the refining stone and the ward seal

Better Combat files and names are untouched (the older generators own them). The weapons' held and dropped
models are rebuilt from the new textures by tools/gen_weapon_models.py at the end.

Run from the repo root:  python3 tools/gen_hd_gear.py      Needs: pillow, numpy (and cairosvg for the imports).
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import gen_combat_assets as gc  # noqa: E402
import gen_arsenal_assets as ga  # noqa: E402
import hd_paint as hp  # noqa: E402
import weapon_relief  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/beyondsolo")
ITEM_OUT = os.path.join(ASSETS, "textures/item")

SC = 2
S = 32 * SC

# ------------------------------------------------------------------------------------------------- patching
# The designs use the 32-unit coordinate space; we sample it at half-pixel steps.

_orig_poly = gc.poly_mask


def uv_hd(x, y):
    px = (x + 0.5) / SC - gc.ORIGIN[0]
    py = ((S - 1 - y) + 0.5) / SC - gc.ORIGIN[1]
    return (px + py) * gc.DIAG, (py - px) * gc.DIAG


def poly_hd(points, size, ss=4):
    if size == S:
        points = [(x * SC, y * SC) for x, y in points]
    return _orig_poly(points, size, ss)


WOODS = [ga.WOOD, ga.DARK_WOOD, ga.PALE_WOOD, ga.LIVING_WOOD]
SOFT = [ga.LEATHER, ga.RAGS]


def _same(a, b):
    return tuple(a) == tuple(b)


def material_of(colors):
    if any(_same(colors, w) for w in WOODS):
        return "wood"
    if any(_same(colors, s) for s in SOFT):
        return "leather"
    return "metal"


def ramp3(colors):
    """A 5-anchor ramp from a design's (light, mid, dark), hue-shifted at both ends."""
    light, mid, dark = colors
    deep = hp.mix(hp.scale(dark, 0.55), hp.COOL, 0.35)
    hi = hp.mix(light, hp.WARM, 0.55)
    return [deep, dark, mid, light, hi]


_ramps = {}


def tone(colors, x):
    key = tuple(map(tuple, colors))
    r = _ramps.get(key)
    if r is None:
        r = _ramps[key] = ramp3(colors)
    x = round(max(-1.0, min(1.0, x)) * 4) / 4
    return hp.tone_color(r, x)


def part_color(self, u, v):
    if u < self.u0 or u > self.u1:
        return None
    w = self.width(u)
    vv = v - self.bend(u)
    if w <= 0 or abs(vv) > w:
        return None
    t = vv / w
    n = hp.hash2(int(u * 4), int(v * 4), 11) - 0.5
    mat = material_of(self.colors)
    if self.kind == "grip":
        phase = (u * 1.0 + vv * 0.75) / 1.35
        band = phase - math.floor(phase)
        x = 0.75 - band * 1.5 + t * 0.35 + n * 0.1
        if band > 0.82:
            x = -0.9
        return tone(self.colors, x)
    if self.kind == "scales":
        # a snake's skin: dark saddles along the back, a pale belly, a bright line where the light catches it
        saddle = math.sin(u * 2.6) + 0.5 * math.sin(u * 5.2 + 1.0)
        x = t * 0.55 + 0.1 + n * 0.14
        if t > -0.15 and saddle > 0.55:
            x -= 0.85
        elif 0.3 < t < 0.62:
            x += 0.5
        if t < -0.5:
            x = 0.75 + n * 0.15 - (0.5 if int(u * 2.2) % 2 else 0)
        return tone(self.colors, x)
    if mat == "wood":
        g = math.sin(u * 0.9 + 2.2 * math.sin(vv * 1.3 + u * 0.15))
        x = t * 0.7 + g * 0.22 + n * 0.12
        return tone(self.colors, x)
    if mat == "leather":
        return tone(self.colors, t * 0.65 + n * 0.25)
    # metal: smooth across the width, a bright reflection band, honed edges, a ridge or a fuller groove
    x = t * 0.75 + 0.55 * math.exp(-((t - 0.45) / 0.16) ** 2) + n * 0.12
    grade = CTX.get("grade", "common")
    if grade in ("legendary", "mythic", "divine") and w > 1.2:
        # pattern-welded steel: flowing light waves through the metal
        if math.sin(u * 1.6 + 2.4 * math.sin(vv * 1.1 + u * 0.3)) > 0.78:
            x += 0.35
    x += 0.1 * math.sin((u - self.u0) / max(1.0, self.u1 - self.u0) * math.pi)
    e = w - abs(vv)
    if w > 1.1:
        if e < 0.5:
            x = 1.0 if t > 0 else 0.3          # the honed edge catches the light
        elif e < 1.0 and t > 0:
            x = min(x, 0.05)                   # the bevel line behind it
        elif e < 1.0:
            x = min(x, -0.75)
    if self.fuller and abs(t) < 0.22 and u < self.u1 - 3 and w > 1.4:
        x = -0.6 if t > 0.02 else 0.15
    elif not self.fuller and w > 1.6 and e >= 1.0:
        if 0 <= t < 0.1:
            x = 0.85                           # the ridge down the middle
        elif -0.12 <= t < 0:
            x = -0.45
    return tone(self.colors, x)


def crescent_color(self, u, v):
    """A scythe blade in full detail: a lit spine, a flat that darkens towards the edge, a bevel and a honed edge."""
    hit = self.at(u, v)
    if hit is None:
        return None
    t, half, k = hit
    if t > 1:
        return tone(self.thorns * 3 if len(self.thorns) == 1 else self.thorns, 0.6)
    n = hp.hash2(int(u * 4), int(v * 4), 17) - 0.5
    e_spine, e_edge = (1 - t) * half, (t + 1) * half
    x = 0.1 + 0.45 * t + 0.4 * math.exp(-((t - 0.35) / 0.2) ** 2) + n * 0.12
    if CTX.get("grade") in ("legendary", "mythic", "divine") and math.sin(k * 26 + t * 2.2) > 0.8:
        x += 0.3                                   # pattern-welded waves
    if e_spine < 0.5:
        x = 0.95                                   # the spine catches the light
    elif e_spine < 1.0:
        x = min(x, 0.1)
    if e_edge < 0.55:
        x = 0.8                                    # the honed edge
    elif e_edge < 1.25:
        x = -0.8                                   # the bevel behind it
    return tone(self.colors, x)


CTX = {}


def render_weapon_hd(parts, glow=None):
    a = np.zeros((S, S, 4), np.uint8)
    owner = np.full((S, S), -1)
    tmap = np.full((S, S), 9.0)
    blade_score = {}
    for y in range(S):
        for x in range(S):
            u, v = uv_hd(x, y)
            for i, p in enumerate(parts):
                c = p.color_at(u, v)
                if c is not None:
                    a[y, x] = c
                    owner[y, x] = i
                    if isinstance(p, gc.Part) and p.kind != "grip" and material_of(p.colors) == "metal":
                        w = p.width(u)
                        tmap[y, x] = (v - p.bend(u)) / w if w > 0 else 9
                        blade_score[i] = blade_score.get(i, 0) + 1
                    elif isinstance(p, gc.Crescent):
                        tmap[y, x] = p.at(u, v)[0]
                        blade_score[i] = blade_score.get(i, 0) + 1
    CTX["glow"] = glow
    CTX["owner"] = owner
    CTX["tmap"] = tmap
    CTX["blade"] = max(blade_score, key=blade_score.get) if blade_score else None
    CTX["parts"] = parts
    return Image.fromarray(a)


def circle_hd(uc, vc, r, colors):
    def f(u, v):
        du, dv = (u - uc) / r, (v - vc) / r
        d = math.hypot(du, dv)
        if d > 1:
            return None
        nz = math.sqrt(max(0.0, 1 - d * d))
        lam = dv * 0.6 - du * 0.25 + nz * 0.7
        x = (lam - 0.55) * 1.8
        if d > 0.62 and dv < -0.15:
            x += 0.7  # light through the far side of the orb
        if d < 0.28 and dv > 0.2:
            x = 1.0
        return tone(colors, x)
    return ga.Fn(f, relief=ga.ball_relief(uc, vc, r))


def flanged_hd(uc, r, colors, flanges=6):
    def f(u, v):
        d = math.hypot(u - uc, v)
        ang = math.atan2(v, u - uc)
        k = (ang / (2 * math.pi) * flanges) % 1.0
        if d <= r * 0.62:
            return tone(colors, v / (r * 0.62) * 0.8)
        if d <= r and k < 0.4:
            face = math.cos(ang - 1.2)
            x = face * 0.8 + (0.5 if k < 0.08 else -0.3 if k > 0.32 else 0)
            return tone(colors, x)
        return None
    return ga.Fn(f, relief=ga.flanged_relief(uc, r, flanges))


def gauntlet_hd(palette, claws=None, spikes=False):
    light, mid, dark, trim, accent = palette
    cv = hp.Canvas(S)
    P = lambda pts: hp.poly([(x * SC, y * SC) for x, y in pts], S)  # noqa: E731
    cuff = P([(9, 20.5), (23, 20.5), (25.5, 30), (6.5, 30)])
    cv.layer(cuff, accent, "fur" if _same((accent,), (accent,)) and sum(accent[:3]) > 600 else "leather", seed=3)
    fist = P([(8, 9), (12, 6), (22, 6), (25, 9), (25, 19.5), (22, 22.5), (10, 22.5), (7, 18.5)])
    cv.layer(fist, mid, "metal", seed=4)
    for i, x0 in enumerate((9.2, 13.2, 17.2, 21.2)):
        cv.layer(P([(x0, 6.4), (x0 + 3.6, 6.4), (x0 + 3.8, 11.2), (x0 - 0.2, 11.2)]), trim, "metal", seed=10 + i)
    thumb = P([(5.5, 13), (10, 11.5), (12.2, 17), (8.5, 19.5), (5, 17.2)])
    cv.layer(thumb, mid, "metal", seed=6)
    plate = P([(10, 13.5), (22, 13.5), (21.5, 18.5), (10.5, 18.5)])
    cv.layer(plate, dark, "dark_metal", seed=7)
    band = P([(7.5, 23.6), (24.5, 23.6), (24.8, 25.8), (7.2, 25.8)])
    cv.layer(band, trim, "gold" if trim[0] > 200 else "metal", seed=8)
    if claws:
        for i, x0 in enumerate((9.5, 14.3, 19.1)):
            cl = P([(x0, 7), (x0 + 3.2, 7), (x0 + 2.6, 2.4), (x0 + 1.2, 0.6)])
            cv.layer(cl, claws[1], "ice", seed=20 + i)
    if spikes:
        for i, x0 in enumerate((9.4, 13.4, 17.4, 21.4)):
            cv.layer(P([(x0 + 0.2, 7), (x0 + 3.2, 7), (x0 + 1.7, 2.6)]), trim, "gold", seed=30 + i)
    cv.seams()
    a = cv.a
    for x0 in (10, 14, 18, 22):
        hp.rivet(a, x0 * SC + 1, 9 * SC, trim)
    hp.engrave(a, P([(10.5, 15.8), (21.5, 15.8), (21.5, 16.3), (10.5, 16.3)]))
    for x in range(int(8 * SC), int(25 * SC), 5):
        hp.rivet(a, x, int(24.2 * SC), hp.mix(trim, (255, 255, 255), 0.3), size=1)
    CTX["owner"] = None
    CTX["blade"] = None
    return Image.fromarray(a)


def outline_noop(img, color=None):
    return img


def apply_patches():
    gc.S = S
    ga.S = S
    gc.uv = uv_hd
    ga.uv = uv_hd
    gc.poly_mask = poly_hd
    ga.poly_mask = poly_hd
    gc.Part.color_at = part_color
    gc.Crescent.color_at = crescent_color
    gc.render_weapon = render_weapon_hd
    gc.outline = outline_noop
    ga.outline = outline_noop
    ga.tone3 = tone
    ga.circle = circle_hd
    ga.flanged = flanged_hd
    ga.gauntlet = gauntlet_hd


# ------------------------------------------------------------------------------------------------- weapon dressing

GRADE_ACCENT = {
    "common": hp.hexc("ffffff"), "rare": hp.hexc("7fc8ff"), "epic": hp.hexc("b47bff"),
    "legendary": hp.hexc("ffb43a"), "mythic": hp.hexc("ff4a6a"), "divine": hp.hexc("fff3c0"),
}
RUNE_GLYPHS = [
    [(0, 0), (1, 0), (1, 1), (1, 2)], [(0, 0), (0, 1), (1, 1), (0, 2)], [(0, 0), (1, 1), (0, 2)],
    [(1, 0), (0, 1), (1, 2)], [(0, 0), (0, 1), (0, 2), (1, 1)], [(0, 1), (1, 0), (1, 2)],
]


def runes(a, owner, tmap, blade, color, seed):
    """Glowing etched glyphs along the blade's centre."""
    if blade is None:
        return
    m = (owner == blade) & (np.abs(tmap) < 0.5)
    ys, xs = np.nonzero(m)
    if len(ys) < 20:
        return
    # walk the blade from hilt (bottom-left) to tip (top-right) and place glyphs every few pixels
    order = np.argsort(xs - ys)
    pts = list(zip(ys[order], xs[order]))
    marks = np.zeros(owner.shape, bool)
    rng = np.random.default_rng(seed)
    step = max(6, len(pts) // 7)
    for k in range(step // 2, len(pts) - step // 2, step):
        y, x = pts[k]
        g = RUNE_GLYPHS[rng.integers(len(RUNE_GLYPHS))]
        for gx, gy in g:
            yy, xx = y - 1 + gy, x - 1 + gx
            if 0 <= yy < S and 0 <= xx < S and owner[yy, xx] == blade:
                marks[yy, xx] = True
    hp.glow_paint(a, marks, color, halo=0.35, core=False)


def dress_weapon(img, grade, seed):
    a = np.array(img).copy()
    owner = CTX.get("owner")
    if owner is not None and owner.shape == a.shape[:2]:
        cv = hp.Canvas(S)
        cv.a, cv.owner = a, owner
        cv.seams(0.28)
        a = cv.a
    glow = CTX.get("glow")
    accent = (glow[0], glow[1], glow[2], 255) if glow else GRADE_ACCENT[grade]
    if grade in ("epic", "legendary", "mythic", "divine") and owner is not None:
        runes(a, owner, CTX["tmap"], CTX["blade"], hp.mix(accent, (255, 255, 255), 0.25), seed)
    if grade == "divine":
        # radiant: the lit edges go white-gold
        solid = a[..., 3] >= 200
        up = np.zeros_like(solid); up[1:, :] = solid[:-1, :]
        left = np.zeros_like(solid); left[:, 1:] = solid[:, :-1]
        edge = solid & (~up | ~left)
        a[edge, :3] = (a[edge, :3] * 0.4 + np.array([255, 246, 210]) * 0.6).astype(np.uint8)
    if glow and grade in ("common", "rare"):
        # elemental weapons below epic keep a thin coloured rim
        hp.bevel(a)
        hp.grain(a, seed=seed)
        hp.outline(a)
        hp.aura(a, accent, (min(110, glow[3] + 30),))
        hp.sparkles(a, hp.GRADE_FX[grade][1], accent, seed)
        return soften(Image.fromarray(a))
    accent2 = {"mythic": hp.hexc("ffb070"), "divine": hp.hexc("ffffff")}.get(grade)
    return soften(hp.finish(a, grade, accent, accent2, seed=seed))


def soften(img):
    """Parts marked `soft_outline` (a bowstring) get an outline in their own colour, darker, instead of ink: a
    one-pixel light line between two black ones reads as a row of beads, not as a cord."""
    owner = CTX.get("owner")
    soft = [i for i, p in enumerate(CTX.get("parts") or []) if getattr(p, "soft_outline", False)]
    if owner is None or not soft:
        return img
    a = np.array(img)
    mine = np.isin(owner, soft)
    if not mine.any():
        return img
    ring = hp.grow(mine) & (owner == -1) & (a[..., 3] >= 200) & ~hp.grow((owner >= 0) & ~mine)
    colour = a[mine][:, :3].astype(float).mean(axis=0)
    a[ring, :3] = (colour * 0.5).astype(np.uint8)
    a[mine, :3] = (a[mine][:, :3] * 0.4 + colour * 0.6).astype(np.uint8)
    return Image.fromarray(a)


COMBAT_GRADES = {
    "hunter_dagger": "common", "knight_sword": "common", "steel_greatsword": "common", "hunting_spear": "common",
    "emberbrand": "rare", "venom_fang": "rare", "bloodletter": "rare", "crimson_reaper": "epic",
}


def write_tex(name, img, grade, animate):
    tex = os.path.join(ITEM_OUT, name + ".png")
    meta = tex + ".mcmeta"
    if animate:
        ga.shimmer_strip(img, grade).save(tex)
        with open(meta, "w") as f:
            json.dump({"animation": {"frametime": 2 if grade != "legendary" else 3, "interpolate": False}}, f, indent=2)
            f.write("\n")
    else:
        img.save(tex)
        if os.path.exists(meta):
            os.remove(meta)


META = os.path.join(weapon_relief.HEIGHT_DIR, "meta.json")


def write_relief(name, dressed):
    """What the held model needs to know about a weapon (tools/gen_weapon_models.py): its depth under every texel
    (tools/weapon_relief.py), from the parts it was painted from with the finished picture's outline and ornaments
    filled in around them; how long it is; where its grip is."""
    import gen_weapon_models as gm
    body = gm.body_mask(np.array(dressed))
    H = None
    parts = CTX.get("parts") or []
    # parts the held model builds as real shapes (a bow's string, its arrow) have no depth here
    real = [i for i, p in enumerate(parts) if getattr(p, "geometry", False)]
    solid_parts = [p for i, p in enumerate(parts) if i not in real]
    if solid_parts:
        H = weapon_relief.design_heights(solid_parts, uv_hd, S, SC)
    reached = None if H is None else ~np.isnan(H)
    H = weapon_relief.finalize(body, H)
    show = None
    owner = CTX.get("owner")
    if real and owner is not None and reached is not None:
        # height 0 leaves their texels, and the outline round them, out of the solid; where they are painted over
        # the bow itself (the arrow across the grip) the solid shows the nearest texel of the bow instead
        mine = np.isin(owner, real)
        ring = hp.grow(mine) & (owner == -1) & ~hp.grow((owner >= 0) & ~mine)
        H[((mine & ~reached) | ring) & body] = 0.0
        show = np.full((S, S, 2), -1, int)
        other = (owner >= 0) & ~mine
        oy, ox = np.nonzero(other)
        for y, x in zip(*np.nonzero(mine & reached & body)):
            if len(ox):
                k = int(np.argmin((ox - x) ** 2 + (oy - y) ** 2))
                show[y, x] = (ox[k], oy[k])
    weapon_relief.save_heights(name, H, show)
    ys, xs = np.nonzero(body)
    us = [uv_hd(x, y)[0] for x, y in zip(xs, ys)]
    info = {"extent": [round(min(us) - 0.25, 2), round(max(us) + 0.25, 2)]}          # along the weapon, design units
    grip = next((p for p in parts if getattr(p, "kind", "") == "grip"), None)
    if grip is not None:
        mid = (grip.u0 + grip.u1) / 2
        info["grip"] = [round(grip.u0, 2), round(grip.u1, 2), round(grip.bend(mid), 3)]
    meta = {}
    if os.path.exists(META):
        with open(META) as f:
            meta = json.load(f)
    meta[name] = info
    with open(META, "w") as f:
        json.dump(dict(sorted(meta.items())), f, indent=1)
        f.write("\n")


def weapons(only=None):
    apply_patches()
    count = 0
    # combat weapons: render_weapon is patched, so their builders run in HD
    for i, (name, img) in enumerate(_combat_weapons().items()):
        if only and name not in only:
            continue
        dressed = dress_weapon(img, COMBAT_GRADES[name], 100 + i)
        write_tex(name, dressed, COMBAT_GRADES[name], False)
        write_relief(name, dressed)
        count += 1
    todo = [(item, grade, build, 200 + i) for i, (item, (grade, _kind, build, _name, _lore)) in enumerate(ga.designs().items())]
    # the awakening trial's four legendary weapons are designed in their own generator
    import gen_awakening_assets
    todo += [(item, "legendary", build, 300 + i) for i, (item, build) in enumerate(gen_awakening_assets.legendary_weapons().items())]
    for item, grade, build, seed in todo:
        if only and item.split("_pulling_")[0] not in only:
            continue
        CTX.clear()
        CTX["grade"] = grade
        img = build()
        img = dress_weapon(img, grade, seed)
        animate = grade in ("legendary", "mythic", "divine") and "_pulling_" not in item
        write_tex(item, img, grade, animate)
        write_relief(item, img)
        count += 1
    return count


def _combat_weapons():
    """gc.weapons() renders every weapon in one go; collect each one's context as it is made."""
    out = {}
    orig = gc.render_weapon

    def capture(parts, glow=None):
        img = orig(parts, glow)
        img.info["ctx"] = dict(CTX)
        return img
    gc.render_weapon = capture
    try:
        imgs = gc.weapons()
    finally:
        gc.render_weapon = orig
    return _CtxDict(imgs)


class _CtxDict(dict):
    """dress_weapon reads CTX, so hand the weapons over one at a time, each with the context it was painted in."""

    def items(self):
        for k, v in super().items():
            CTX.clear()
            CTX.update(v.info.get("ctx") or {})
            yield k, v


# ------------------------------------------------------------------------------------------------- forge materials

def refining_stone():
    """A chunk of raw refining ore: dark slate rock with amber crystals breaking out of it, glowing veins and
    sparks of ore. Used at the forge to hammer gear up."""
    P = lambda pts: hp.poly([(x * SC, y * SC) for x, y in pts], S)  # noqa: E731
    cv = hp.Canvas(S)
    rock = P([(3.5, 20), (6, 13.5), (11, 10.5), (15, 12), (19.5, 10), (26, 13.5), (29, 19.5), (27, 26), (19, 29.5),
              (10, 29), (4.5, 25.5)])
    cv.layer(rock, hp.hexc("4c5670"), "stone", seed=41)
    lit = P([(6, 13.8), (11, 10.8), (15, 12.3), (12.5, 17.5), (5.2, 19)]) & rock
    cv.layer(lit, hp.hexc("7684a0"), "stone", seed=42, light_bias=0.3)
    low = P([(28.8, 19.8), (26.8, 25.8), (19, 29.2), (17, 24), (23, 20.5)]) & rock
    cv.layer(low, hp.hexc("30364c"), "stone", seed=43, light_bias=-0.2)
    amber = hp.hexc("ff9a1a")
    crystals = [
        [(12.5, 17.5), (14.5, 5.5), (17, 2.5), (20, 5), (20, 17), (16.5, 19.5)],  # the big one
        [(18.5, 18), (23.5, 7.5), (26, 7), (27.5, 9.5), (24, 19)],                # leaning right
        [(7.5, 20), (6, 11.5), (8, 9), (10.5, 10.5), (12.5, 19)],                 # leaning left
        [(21.5, 21), (28.5, 16.5), (29.5, 19), (24, 23)],                       # small, low
    ]
    for k, pts in enumerate(crystals):
        m = P(pts)
        # a prism: the plane left of the base-to-tip line is lit, the right one in shade, the core glows
        (bx0, by0), tip = ((pts[0][0] + pts[-1][0]) / 2, (pts[0][1] + pts[-1][1]) / 2), max(pts, key=lambda q: -q[1])
        yy, xx = np.mgrid[0:S, 0:S] / SC
        side = (tip[0] - bx0) * (yy - by0) - (tip[1] - by0) * (xx - bx0)
        extra = np.where(side > 0, 0.45, -0.35)
        dline = np.abs(side) / max(1e-6, math.hypot(tip[0] - bx0, tip[1] - by0))
        extra = extra + np.where(dline < 0.45, 0.6, 0)
        base = hp.mix(amber, hp.hexc("ff6a1a"), 0.3 * (k % 2))
        cv.layer(m, base, "gold", seed=60 + k, extra=extra)
    cv.seams(0.32)
    a = cv.a
    veins = hp.curve([(5 * SC, 23 * SC), (9 * SC, 24 * SC), (13 * SC, 26.5 * SC)], 1.4, S) | \
        hp.curve([(17 * SC, 27.5 * SC), (21 * SC, 25.5 * SC), (25 * SC, 26 * SC)], 1.2, S) | \
        hp.curve([(14 * SC, 20.5 * SC), (15.5 * SC, 23.5 * SC), (14.5 * SC, 26 * SC)], 1.2, S)
    hp.glow_paint(a, veins & rock & (cv.owner < 3), hp.hexc("ffc65a"), halo=0.45)
    rng = np.random.default_rng(7)
    ys, xs = np.nonzero(hp.shrink(rock, 2) & (cv.owner < 3))
    for i in rng.choice(len(ys), 16, replace=False):
        a[ys[i], xs[i], :3] = (255, 226, 140) if i % 2 else (190, 140, 70)
    return hp.finish(a, "rare", hp.hexc("ffb23a"), seed=9)


def ward_seal():
    """A wax-sealed ward talisman: a silver-rimmed medallion with a blue warding gem, engraved runes round the rim
    and two ribbons hanging below."""
    cv = hp.Canvas(S)
    c = 15.5 * SC
    ribbon_l = hp.poly([(c - 9, c + 8), (c - 3, c + 10), (c - 7, 62), (c - 10, 58), (c - 14, 62)], S)
    ribbon_r = hp.poly([(c + 3, c + 10), (c + 9, c + 8), (c + 14, 62), (c + 10, 58), (c + 7, 62)], S)
    cv.layer(ribbon_l | ribbon_r, hp.hexc("3a64c8"), "cloth", seed=50)
    outer = hp.ellipse(c, c - 1, 22, 22, S)
    cv.layer(outer, hp.hexc("c8d2e4"), "metal", seed=51)
    band = hp.ellipse(c, c - 1, 17.5, 17.5, S)
    cv.layer(band, hp.hexc("2b3a6e"), "dark_metal", seed=52)
    inner = hp.ellipse(c, c - 1, 13, 13, S)
    cv.layer(inner, hp.hexc("d9a441"), "gold", seed=53)
    cv.seams(0.3)
    a = cv.a
    # runes round the dark band
    ring_r = 15.3
    for k in range(12):
        ang = k / 12 * 2 * math.pi
        x = c + math.cos(ang) * ring_r
        y = c - 1 + math.sin(ang) * ring_r
        g = RUNE_GLYPHS[k % len(RUNE_GLYPHS)]
        m = np.zeros((S, S), bool)
        for gx, gy in g:
            yy, xx = int(y) - 1 + gy, int(x) - 1 + gx
            if 0 <= yy < S and 0 <= xx < S:
                m[yy, xx] = True
        hp.glow_paint(a, m, hp.hexc("9fdcff"), halo=0.3, core=False)
    # the warding shield inside the gold: a hexagon with a gem
    hexm = hp.poly([(c, c - 11), (c + 9, c - 6), (c + 9, c + 4), (c, c + 9), (c - 9, c + 4), (c - 9, c - 6)], S)
    sub = hp.Canvas(S)
    sub.layer(hexm, hp.hexc("eef4ff"), "metal", seed=54)
    m = sub.a[..., 3] > 0
    a[m] = sub.a[m]
    hp.engrave(a, hp.grow(hexm) & ~hexm)
    hp.gem(a, c, c - 1, 5.5, hp.hexc("3fa0ff"))
    for ang in (0.5, 2.6, 4.7):
        hp.rivet(a, c + math.cos(ang) * 20 - 1, c - 1 + math.sin(ang) * 20 - 1, hp.hexc("e8eef8"))
    return hp.finish(a, "epic", hp.hexc("7fc8ff"), seed=12)


def main():
    os.makedirs(ITEM_OUT, exist_ok=True)
    only = set(sys.argv[1:]) or None
    n = weapons(only)
    # the held and dropped 3D models are built from the finished textures
    import gen_weapon_models
    gen_weapon_models.build(only)
    refining_stone().save(os.path.join(ITEM_OUT, "refining_stone.png"))
    ward_seal().save(os.path.join(ITEM_OUT, "ward_seal.png"))
    # the armor sets' art (icons, worn layers, 3D parts) comes from tools/gen_armory_assets.py now
    import gen_armory_assets
    gen_armory_assets.main(set(gen_armory_assets.GEAR))
    print(f"hd gear written: {n} weapon textures")


if __name__ == "__main__":
    main()
