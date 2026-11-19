#!/usr/bin/env python3
"""
The light path's assets (see com.beyondsolo.light, the hunter who swore to the light instead of the System's road):

- The Envoy of Dawn (light_envoy): the angel waiting in the awakening trial's hidden chapel. A tall figure in white robes
  that dissolve into light above the ground, a stole of gold, a smooth mask of white gold with two pure white glows
  for eyes, a crown of three blades of light, a broken ring of light turning behind its head and six wings of light
  (a great pair raised, a pair spread, a pair hanging low).
- The seraph form (light_seraph): two great wings of light and a halo, drawn on a hunter's back (SeraphLayer).
- The light given a shape (LightConstruct, one model per form): the lance of judgement (light_spear), the Blade of
  Heaven (light_blade, a sword of light the size of a tower), the Hand of Heaven (light_hand, a hand of glass and
  light as big as a house), the sun of Daybreak (light_sun), a feather of the Choir of Wings (light_feather) and the
  choir itself (light_choir, six wings and a halo standing at the ruler's back).
- Icons: the light's powers (skill_light_*) and its four mantles (light_mantle_*), the Envoy's feather (an item).

Painted to the series style guide (/mnt/project-files/art-style/STYLE-GUIDE.md, "rulers and heralds"): bodies of light
are bright matter that never goes darker than warm grey; every glow is a white core, a saturated edge and a short
bloom (the glow mask burns only along edges and spines, never as a slab); wings are long blade-like feathers of
light, white at the spine, gold at the edge, fading to nothing at the tips; halos are broken rings, spokes and thin
lines turning slowly; the unseen hand is glass with burning edges.

The constructs and the seraph are white: the game tints them with the ruler's colour (Mantle.color()). The Envoy is
painted in its own white and gold.

Built with the creature kit (tools/gen_creatures.py, painted on the model by tools/creature_skins.py) at a scale of
their design (MODEL_SCALE), plus one material of its own ("lightm": matter made of light) and see-through paint
(alpha that fades along a part, or glass), which the kit's painter has no way to say.

Run from the repo root:   python3 tools/gen_light_assets.py                (everything)
                          python3 tools/gen_light_assets.py envoy icons    (only some)
Look at one without the game:   python3 tools/preview_model.py light_envoy_main --anim idle --time 1.0
Needs: pillow, numpy, scipy, cairosvg.

Conventions (as the creature kit): 16 units are one block, the front of a model is -z, +x is the model's left. A
positive x rotation tilts a bone's top towards the front; a positive z rotation tilts its top towards +x (so it lowers
a part pointing out to +x); a positive y rotation turns a part pointing out to +x towards the front.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature_skins as CS  # noqa: E402
import gen_creatures as GC  # noqa: E402
import gen_enemy_assets as G  # noqa: E402
import gen_worldboss_assets as W  # noqa: E402
import solid_skins as S  # noqa: E402
from gen_gate_assets import ease_in, ease_in_out, ease_out_back, window  # noqa: E402

anim, wave = G.anim, G.wave
pose, timeline, lin, shake, bump, OneSkin = W.pose, W.timeline, W.lin, W.shake, W.bump, W.OneSkin

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "beyondsolo")


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 3


# ============================================================================================= the light's colours

# the style guide's light (dark, mid, edge, core): never darker than warm grey
GOLD = (255, 215, 122)
# the constructs' and the seraph's own light: white, so the ruler's colour shows
WHITE_LIGHT = (255, 250, 238)

ENVOY_RAMPS = {
    "plate": [(138, 127, 106), (196, 184, 158), (232, 224, 204), (248, 244, 232), (255, 255, 250)],
    "trim": [(150, 108, 40), (198, 148, 58), (228, 176, 70), (255, 215, 122), (255, 246, 214)],
    "cloth": [(138, 127, 106), (202, 192, 172), (234, 228, 214), (248, 245, 236), (255, 254, 248)],
    "inner": [(138, 127, 106), (158, 146, 124), (182, 170, 146), (206, 196, 174), (226, 218, 198)],
    "mask": [(176, 132, 58), (214, 170, 86), (240, 204, 124), (255, 230, 160), (255, 250, 228)],
    "feather": [(160, 146, 118), (222, 206, 172), (244, 236, 216), (252, 249, 240), (255, 255, 252)],
    "leather": (150, 128, 96),
}
WHITE_RAMPS = {
    "body": [(150, 148, 144), (210, 208, 204), (238, 237, 234), (250, 249, 247), (255, 255, 255)],
    "feather": [(156, 152, 146), (214, 212, 206), (240, 239, 236), (251, 250, 248), (255, 255, 255)],
    "trim": [(150, 140, 124), (192, 182, 166), (222, 214, 200), (242, 236, 226), (255, 252, 244)],
    "plate": [(150, 146, 140), (204, 200, 194), (232, 230, 226), (248, 247, 244), (255, 255, 255)],
    "leather": (150, 140, 128),
}


def light_palette(name, glow, ramps, density=5.0):
    GC.register(name, lambda aff: GC.pal("plain", glow, ramps, tinted=()), density=density, levels={"": 0})
    LIGHT_MODELS.add(name)


# ============================================================================================= matter made of light

def m_light(f, p, s):
    """Light given a shape (a feather, a blade, a lance, a ray, a ring): a body lit from inside, brightest down its
    middle; its long edges burning (rim, in texels), its spine white-hot (core, a share of the width) and, if asked,
    fading to nothing along its length (fade: from, to in design units along `long`) or see-through (glass)."""
    r, g = s.get("r", "body"), s.get("g", "glow")
    long_ = s.get("long", "y")
    face = f.face
    if long_ == "y":
        along_u, along_v = False, f.wall
    elif long_ == "z":
        along_u, along_v = face in ("east", "west"), face in ("up", "down")
    else:
        along_u, along_v = face in ("north", "south", "up", "down"), False
    if along_u:
        across, n = f.V, f.h
    elif along_v:
        across, n = f.U, f.w
    else:
        across, n = None, None
    if across is not None:
        d = np.minimum(across, 1 - across) * n
        mid = 1 - np.abs(across - 0.5) * 2
    else:
        d = np.minimum(np.minimum(f.U * f.w, (1 - f.U) * f.w), np.minimum(f.V * f.h, (1 - f.V) * f.h))
        mid = np.clip(d / max(1.0, min(f.w, f.h) / 2), 0, 1)
    along = {"x": f.X, "y": f.Y, "z": f.Z}[long_]
    nz = CS.vnoise3(f.X * 1.7, f.Y * 1.7, f.Z * 1.7, s.get("seed", 7))
    tone = s.get("tone", 0.5) + 0.38 * mid ** 1.4 + (nz - 0.5) * 0.08
    if s.get("facets", True):
        # long slanting planes catching the light, as cut crystal does
        ac = across if across is not None else f.U
        tone = tone + 0.06 * (((along * 0.31 + ac * 1.7) % 1.0) < 0.42) - 0.03
    tone = tone + {"up": 0.06, "down": -0.08}.get(face, 0.0)
    p.fill(r, np.clip(tone, 0, 1))
    rim = s.get("rim", 1.2)
    if rim:
        p.glow(g, s.get("rim_tone", 0.74), d < rim)
        p.glow(g, 0.6, np.clip(1 - (d - rim) / 1.8, 0, 1) * 0.42 * (d >= rim))
    core = s.get("core", 0.0)
    if core and across is not None:
        dc = np.abs(across - 0.5) * n
        p.glow(g, 0.98, dc < max(core * n / 2, 0.55))
    hot = s.get("hot")
    if hot:
        # the white-hot stretch where it is born (the root of a feather, the point of a lance)
        a0, a1 = hot
        k = np.clip((along - a1) / (a0 - a1), 0, 1) if a0 != a1 else np.zeros(f.X.shape)
        p.glow(g, 0.95, (k > 0.5) & (mid > 0.25))
    glass = s.get("glass")
    if glass is not None and hasattr(p, "glass_by"):
        p.glass_by(glass)
    fade = s.get("fade")
    if fade and hasattr(p, "fade_by"):
        a0, a1 = fade
        k = np.clip((along - a1) / (a0 - a1), 0, 1)
        p.fade_by(k * k * (3 - 2 * k))


CS.MATS["lightm"] = m_light
CS.SELF_LIT.add("lightm")
CS.NO_MARKS.add("lightm")


def fade_spec(s, f, p):
    """Any material can dissolve into light along y (fade_y: from, to), as the Envoy's robes do above the ground."""
    fy = s.get("fade_y")
    if fy and hasattr(p, "fade_by"):
        a0, a1 = fy
        k = np.clip((f.Y - a1) / (a0 - a1), 0, 1)
        p.fade_by(k * k * (3 - 2 * k))


# the kit's painter has no way to say "see-through": these models are painted with alpha that can fade
LIGHT_MODELS = set()


class FadePaint(S.Paint):
    """A face's paint that can also fade (alpha and light together) and be glass (alpha of the body only)."""

    def __init__(self, face):
        super().__init__(face)
        self.fade = np.ones(self.shape)
        self.glass = np.ones(self.shape)

    def fade_by(self, k):
        self.fade = self.fade * np.broadcast_to(np.asarray(k, float), self.shape)

    def glass_by(self, k):
        self.glass = self.glass * np.broadcast_to(np.asarray(k, float), self.shape)


_kit_paint = S.paint


def _paint(name, bones, deferred, tex_dir, unit, max_atlas=2048):
    """solid_skins.paint with see-through paint (for the light's models only)."""
    if name not in LIGHT_MODELS:
        return _kit_paint(name, bones, deferred, tex_dir, unit, max_atlas)
    spec = S.PAINTERS[name]
    cubes = [(b, c) for b in bones for c in b.get("cubes", []) if "_mat" in c]
    density = spec["density"]
    while True:
        rects, keys = [], []
        for ci, (b, c) in enumerate(cubes):
            for f in S.FACES:
                a, d = S.face_dims(c["size"], f)
                if f in c.get("_skip", ()) or a <= 0 or d <= 0:
                    continue
                rects.append((max(1, int(round(a * density))), max(1, int(round(d * density)))))
                keys.append((ci, f))
        fit = None
        for w, h in S.SIZES:
            if w > max_atlas or max(r[0] for r in rects) > w:
                continue
            pos, used = S._pack(rects, w)
            if used <= h:
                fit = (w, h, pos)
                break
        if fit or density <= 1.0:
            break
        density -= 0.25
    aw, ah, pos = fit
    for b, c in cubes:
        c["uv"] = {}
    if spec.get("prepare"):
        spec["prepare"](cubes, unit)
    faces = []
    for (ci, f), (w, h), (x, y) in zip(keys, rects, pos):
        b, c = cubes[ci]
        c["uv"][f] = {"uv": [x, y], "uv_size": [w, h]}
        face = S.Face(c, b["name"], f, w, h, unit)
        p = FadePaint(face)
        spec["painter"](face, p)
        fade_spec(c.get("_paint") or {}, face, p)
        faces.append((p, x, y, w, h))
    for vi, (suffix, *args) in enumerate(deferred.variants):
        pal = spec["palette"](deferred.make_palette(np.random.default_rng(deferred.seed + vi), *args))
        img = np.zeros((ah, aw, 4))
        glow = np.zeros((ah, aw, 4))
        for p, x, y, w, h in faces:
            rgb, em, alpha = p.render(pal, spec.get("levels", {}))
            img[y:y + h, x:x + w, :3] = rgb
            img[y:y + h, x:x + w, 3] = np.round(255 * alpha * p.fade * p.glass)
            em = em & alpha & (p.fade > 0.03)
            gl = np.zeros((h, w, 4))
            gl[em, :3] = rgb[em] * p.fade[em][:, None]
            gl[em, 3] = np.round(255 * p.fade[em])
            glow[y:y + h, x:x + w] = gl
        Image.fromarray(img.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}.png"))
        Image.fromarray(glow.astype(np.uint8)).save(os.path.join(tex_dir, f"{name}_{suffix}_glowmask.png"))
    for b, c in cubes:
        for k in ("_mat", "_tag", "_skip", "_part", "_paint"):
            c.pop(k, None)
    return aw, ah, density


S.paint = _paint


# ============================================================================================= shapes of light

def shard(B, at, theta, length, w, t, **spec):
    """A straight piece of light from `at` towards theta (degrees in the x-y plane: 0 is +x, 90 straight up, -90
    straight down), w wide and t thick (along z)."""
    x, y, z = at
    spec.setdefault("long", "y")
    return B.c([x - w / 2, y, z - t / 2], [w, length, t], "lightm", pivot=[x, y, z], rot=[0, 0, 90 - theta], **spec)


def feather(B, at, theta, length, w, t=0.3, seed=0, r="feather", rim=1.1, core=0.16, fade=(0.5, 1.02), lean=0.0):
    """A feather of light: a long blade, white down its spine and burning at its edges, narrowing to a point and fading
    to nothing towards it (fade: where it starts to fade and where it is gone, as shares of its length)."""
    x, y, z = at
    main = 0.76 * length
    fd = (y + length * fade[0], y + length * fade[1])
    rot = [lean, 0, 90 - theta]
    return [B.c([x - w / 2, y, z - t / 2], [w, main, t], "lightm", pivot=[x, y, z], rot=rot, long="y", r=r, rim=rim, core=core, fade=fd,
                hot=(y, y + 0.12 * length), seed=seed),
            B.c([x - w * 0.27, y + main - 0.05, z - t * 0.4], [w * 0.54, length * 0.3, t * 0.8], "lightm", pivot=[x, y, z], rot=rot, long="y", r=r,
                rim=rim, core=core * 1.8, fade=fd, seed=seed + 1)]


def light_wing(B, side, sx, name, parent, root, elev, arm, hand_len, prim, sec, width=1.5, t=0.3, z_step=0.11, rest=(0.0, 0.0, 0.0), seed=0,
               r="feather", bar_r="feather", coverts=5):
    """A wing of light, built spread in the x-y plane behind the back (on the +x side, mirrored by sx). Its arm rises
    from the shoulder blade at elev degrees; the feathers along it (sec: [(where along the arm 0-1, angle, length)])
    hang down and out; its hand runs on from the wrist (hand_len long, 8 degrees higher) and carries the long
    feathers (prim: [(angle, length)], the outermost first, their roots spread along the hand from its tip back to the
    wrist), so together they make one sweep from the leading edge round to the body. A row of short coverts lies over
    the roots. Angles are in the wing's plane, 0 straight out and 90 straight up. Every feather lies a little behind the
    one before, so none of them share a plane. Bones: {side}_{name} (the arm, at the shoulder) and {side}_{name}2 (the
    hand, at the wrist). rest: the arm's resting rotation [x, sweep back, raise] (mirrored for the other side)."""
    x0, y0, z0 = root
    x0 *= sx
    e = math.radians(elev)
    wx, wy = x0 + sx * arm * math.cos(e), y0 + arm * math.sin(e)

    def th(a):
        return a if sx > 0 else 180 - a

    cubes = [shard(B, (x0, y0, z0), th(elev), arm + 0.4, 0.9, 0.7, r=bar_r, rim=1.0, core=0.3, seed=seed)]
    for i, (k, a, ln) in enumerate(sec):
        px, py = x0 + sx * arm * k * math.cos(e), y0 + arm * k * math.sin(e)
        cubes += feather(B, (px, py, z0 + 0.14 + i * z_step), th(a), ln, width, t, seed=seed + 2 + 2 * i, r=r)
    for j in range(coverts):
        # short wide feathers over the roots, lying back along the arm
        k = 0.12 + 0.86 * j / max(1, coverts - 1)
        px, py = x0 + sx * arm * k * math.cos(e), y0 + arm * k * math.sin(e)
        cubes += feather(B, (px, py, z0 - 0.12 - j * 0.03), th(elev - 62 + 8 * k), 2.6 + 2.2 * k, width * 1.15, t * 0.9, seed=seed + 20 + 2 * j, r=r,
                         rim=1.0, core=0.12, fade=(0.62, 1.05))
    B.bone(f"{side}_{name}", parent, [x0, y0, z0], cubes, rot=[rest[0], -sx * rest[1], -sx * rest[2]])
    he = math.radians(elev + 8)
    hand = [shard(B, (wx, wy, z0), th(elev + 8), hand_len + 0.3, 0.8, 0.6, r=bar_r, rim=1.0, core=0.3, seed=seed + 40)]
    zb = z0 + 0.14 + len(sec) * z_step
    n = len(prim)
    for i, (a, ln) in enumerate(prim):
        k = (1 - i / max(1, n - 1)) * 0.94
        px, py = wx + sx * hand_len * k * math.cos(he), wy + hand_len * k * math.sin(he)
        hand += feather(B, (px, py, zb + i * z_step), th(a), ln, width, t, seed=seed + 50 + 2 * i, r=r)
    for j in range(3):
        # coverts over the hand's roots
        k = 0.15 + 0.4 * j
        px, py = wx + sx * hand_len * k * math.cos(he), wy + hand_len * k * math.sin(he)
        hand += feather(B, (px, py, z0 - 0.15 - j * 0.03), th(elev + 8 - 70), 3.0, width * 1.1, t * 0.9, seed=seed + 80 + 2 * j, r=r, rim=1.0,
                        core=0.12, fade=(0.62, 1.05))
    B.bone(f"{side}_{name}2", f"{side}_{name}", [wx, wy, z0], hand)


def ring_upright(B, c, radius, n, frac=0.62, w=0.5, t=0.3, phase=0.0, **spec):
    """A broken ring standing upright (in the x-y plane, facing forward) round c: n segments, each frac of its arc."""
    cx, cy, cz = c
    arc = 2 * math.pi * radius / n * frac
    spec.setdefault("rim", 0.8)
    spec.setdefault("core", 0.25)
    return [B.c([cx + radius - w / 2, cy - arc / 2, cz - t / 2], [w, arc, t], "lightm", pivot=[cx, cy, cz], rot=[0, 0, -(phase + 360.0 * i / n)],
                long="y", **spec) for i in range(n)]


def ring_level(B, c, radius, n, frac=0.62, w=0.4, t=0.4, phase=0.0, **spec):
    """A broken ring lying level round c: n segments, each frac of its arc."""
    cx, cy, cz = c
    arc = 2 * math.pi * radius / n * frac
    spec.setdefault("rim", 0.8)
    spec.setdefault("core", 0.25)
    return [B.c([cx + radius - t / 2, cy - w / 2, cz - arc / 2], [t, w, arc], "lightm", pivot=[cx, cy, cz], rot=[0, -(phase + 360.0 * i / n), 0],
                long="z", **spec) for i in range(n)]


def spokes(B, c, r0, r1, n, w=0.25, t=0.25, phase=0.0, **spec):
    """Spokes of light from r0 to r1 round c, upright (in the x-y plane)."""
    cx, cy, cz = c
    spec.setdefault("rim", 0.6)
    return [B.c([cx + r0, cy - w / 2, cz - t / 2], [r1 - r0, w, t], "lightm", pivot=[cx, cy, cz], rot=[0, 0, -(phase + 360.0 * i / n)], long="x",
                **spec) for i in range(n)]


def nodes(B, c, radius, n, size=0.7, phase=0.0, upright=True, mat="glow", **spec):
    """Small diamonds of light set round a ring (where its segments break)."""
    cx, cy, cz = c
    out = []
    for i in range(n):
        a = math.radians(phase + 360.0 * i / n)
        if upright:
            x, y, z = cx + radius * math.cos(a), cy + radius * math.sin(a), cz
            out.append(B.box(x, y, z, size, size, size * 0.6, mat, pivot=[x, y, z], rot=[0, 0, 45], **spec))
        else:
            x, y, z = cx + radius * math.cos(a), cy, cz + radius * math.sin(a)
            out.append(B.box(x, y, z, size * 0.6, size, size, mat, pivot=[x, y, z], rot=[45, 0, 0], **spec))
    return out


def raise_wings(names, up=0.0, sweep=0.0, tip=0.0):
    """A pose for wings: the arms raised by up and swept back by sweep, the hands raised by tip (both sides)."""
    out = {}
    for n in names:
        for side, sx in (("right", -1), ("left", 1)):
            out[f"{side}_{n}"] = [0, -sx * sweep, -sx * up]
            out[f"{side}_{n}2"] = [0, 0, -sx * tip]
    return out


def beat(L, names, amp=10.0, sweep=4.0, tip=8.0, phase=0.0, k=None):
    """Slow wingbeats (period L) on top of a pose: arms up and down, the hands following behind; k(t) scales them."""
    out = {}
    for j, n in enumerate(names):
        ph = phase + j * 0.12
        for side, sx in (("right", -1), ("left", 1)):
            out[f"{side}_{n}"] = (lambda t, sx=sx, ph=ph: [0, -sx * sweep * wave(t, L, ph + 0.25) * (k(t) if k else 1),
                                                          -sx * amp * wave(t, L, ph) * (k(t) if k else 1)])
            out[f"{side}_{n}2"] = (lambda t, sx=sx, ph=ph: [0, 0, -sx * tip * wave(t, L, ph - 0.15) * (k(t) if k else 1)])
    return out


def merge(*ds):
    out = {}
    for d in ds:
        for key, fn in d.items():
            if key in out:
                a, b = out[key], fn
                out[key] = (lambda t, a=a, b=b: [x + y for x, y in zip(a(t), b(t))])
            else:
                out[key] = fn
    return out


def spin(axis, degrees, L):
    """A steady turn of `degrees` over L seconds about axis (0 x, 1 y, 2 z)."""
    def fn(t):
        v = [0.0, 0.0, 0.0]
        v[axis] = degrees * t / L
        return v
    return fn


def write_anims(name, A):
    GC.write_anims(name, A)


# ============================================================================================= the Envoy of Dawn

ENVOY = "light_envoy"
G.MODEL_SCALE[ENVOY] = 1.45
ENVOY_WINGS = ("wing", "wingb", "wingc")


def envoy():
    """The Envoy, built 36 units tall to the top of its head (its crown to 40, its raised wings to 50) and drawn 1.45
    times bigger (3.3 blocks, its hitbox 3.2): it floats, its robes dissolving into light a hand above the ground.
    Bones: root, body (the waist and the under-robe), robe_front, robe_back, chest, head (turned to look) with halo,
    arms and hands (in wide sleeves), and six wings: right_wing/right_wing2 (raised), right_wingb (spread), right_wingc
    (hanging low), the same on the left."""
    B = OneSkin(ENVOY, 8100)
    box, col, c = B.box, B.col, B.c
    mx = GC.mirror_x
    robe = dict(r="cloth", folds=1.4, marks=0.0)
    TM = dict(r="trim", marks=0.0)
    LP = dict(r="plate", rivets=False, marks=0.0, dents=False, grime=False)
    B.bone("root", None, [0, 0, 0])
    B.bone("body", "root", [0, 20.0, 0], [
        # a sash of gold at the waist, a sun of light at its knot
        box(0, 20.4, 0.0, 5.0, 1.6, 3.8, "trim", **TM),
        box(0, 20.4, -2.0, 1.2, 1.2, 0.4, "glow", pivot=[0, 20.4, -2.0], rot=[0, 0, 45]),
        # the robe's sides, falling and flaring out, dissolving into light above the ground
        *[c(mx([2.2, 1.6, -2.1], [0.6, 18.4, 4.4], sx), [0.6, 18.4, 4.4], "cloth", **robe, hem=1.4, glow_hem=1.8, seed=12 + (sx > 0),
            skip=("up", "down"), pivot=[sx * 2.5, 20.0, 0.0], rot=[0, 0, -sx * 6], fade_y=(7.0, 1.6)) for sx in (-1, 1)],
        # the warm white of the under-robe between its panels
        box(0, 11.0, 0.0, 4.4, 17.6, 3.2, "cloth", r="inner", folds=1.0, marks=0.0, seed=14, skip=("up",), fade_y=(8.0, 2.4)),
    ])
    B.bone("robe_front", "body", [0, 19.6, -2.0], [
        c([-2.5, 1.4, -2.45], [5.0, 18.2, 0.5], "cloth", **robe, hem=1.8, glow_hem=2.2, band=(5.6, 6.2), seed=16, skip=("up", "down"),
          fade_y=(6.4, 1.4),
          lines=[dict(pts=[(-1.5, 17.6), (-1.5, 4.0)], w=0.16, faces=("north",), halo=0.4, bright=0.7),
                 dict(pts=[(1.5, 17.6), (1.5, 4.0)], w=0.16, faces=("north",), halo=0.4, bright=0.7)]),
    ], rot=[-3, 0, 0])
    B.bone("robe_back", "body", [0, 19.6, 1.8], [
        c([-2.7, 1.0, 1.6], [5.4, 18.6, 0.5], "cloth", **robe, hem=1.8, glow_hem=2.2, band=(4.8, 5.4), seed=17, skip=("up", "down"), fade_y=(6.4, 1.0)),
    ], rot=[5, 0, 0])
    B.bone("chest", "body", [0, 21.0, 0.0], [
        box(0, 23.0, 0.0, 4.6, 4.0, 3.4, "cloth", **robe, seed=30),
        box(0, 26.4, -0.1, 6.0, 3.4, 3.8, "cloth", **robe, seed=31),
        # the stole: two bands of gold over the shoulders and down the front, glyphs of light on them
        *[box(sx * 1.25, 23.8, -1.95, 1.1, 7.4, 0.3, "trim", **TM,
              runes=dict(region=(-0.5 + sx * 1.25, 20.6, 0.5 + sx * 1.25, 27.0), faces=("north",), seed=33 + (sx > 0), cell=0.9, density=0.55))
          for sx in (-1, 1)],
        # a sun of gold at its heart, with a heart of light
        box(0, 25.6, -2.05, 1.8, 1.8, 0.3, "trim", **TM, pivot=[0, 25.6, -2.05], rot=[0, 0, 45]),
        box(0, 25.6, -2.25, 0.8, 0.8, 0.3, "glow", pivot=[0, 25.6, -2.25], rot=[0, 0, 45]),
        # a mantle of plates on its shoulders, rimmed in gold
        *[c(mx([0.8 + j * 1.5, 26.6 - j * 0.5, -2.2], [2.0, 2.2, 4.4], sx), [2.0, 2.2, 4.4], "plate", **LP, seed=36 + j * 2 + (sx > 0),
            pivot=[sx * (1.4 + j * 1.5), 28.2, 0.0], rot=[0, 0, sx * (12 + j * 14)]) for sx in (-1, 1) for j in range(2)],
        *[c(mx([0.8 + j * 1.5, 28.6 - j * 0.5, -2.3], [2.0, 0.3, 4.6], sx), [2.0, 0.3, 4.6], "trim", **TM,
            pivot=[sx * (1.4 + j * 1.5), 28.2, 0.0], rot=[0, 0, sx * (12 + j * 14)]) for sx in (-1, 1) for j in range(2)],
        box(0, 28.4, 0.0, 4.2, 0.8, 3.4, "cloth", **robe, seed=34),
    ])
    B.bone("head", "chest", [0, 28.6, 0.0], [
        col(0, 0.0, 28.4, 29.8, 1.5, 1.5, "plate", **LP, seed=40),
        # the mask: smooth white gold with no mouth; for eyes, two slits of pure white light
        box(0, 31.8, -0.2, 3.2, 4.0, 3.4, "plate", r="mask", rivets=False, marks=0.0, dents=False, grime=False, seed=41,
            lines=[dict(pts=[(-1.15, 32.2), (-0.45, 32.0)], w=0.34, faces=("north",), halo=0.8, bright=1.0),
                   dict(pts=[(0.45, 32.0), (1.15, 32.2)], w=0.34, faces=("north",), halo=0.8, bright=1.0),
                   dict(pts=[(0.0, 31.4), (0.0, 30.2)], w=0.14, faces=("north",), halo=0.4, bright=0.6)]),
        # a veil of white falling behind it from the circlet
        box(0, 30.4, 1.75, 3.6, 5.8, 0.4, "cloth", **robe, hem=1.0, glow_hem=1.2, seed=42, fade_y=(29.0, 26.6)),
        *[box(sx * 1.75, 31.0, 0.5, 0.3, 4.4, 2.6, "cloth", **robe, seed=43 + (sx > 0)) for sx in (-1, 1)],
        # a circlet of gold, and a crown of three blades of light standing out of it
        box(0, 33.4, -0.2, 3.5, 0.6, 3.7, "trim", **TM),
        box(0, 33.5, -2.05, 0.7, 0.7, 0.3, "glow", pivot=[0, 33.5, -2.05], rot=[0, 0, 45]),
        *feather(B, (0, 33.8, -1.6), 90, 5.6, 0.9, 0.3, seed=45, r="feather", rim=1.0, core=0.22, fade=(0.55, 1.02), lean=-10),
        *feather(B, (-1.1, 33.7, -1.4), 108, 4.0, 0.7, 0.3, seed=47, r="feather", rim=1.0, core=0.22, fade=(0.5, 1.02), lean=-8),
        *feather(B, (1.1, 33.7, -1.4), 72, 4.0, 0.7, 0.3, seed=49, r="feather", rim=1.0, core=0.22, fade=(0.5, 1.02), lean=-8),
    ])
    hc = (0, 33.0, 3.0)
    B.bone("halo", "head", list(hc), [
        # a broken ring of light behind its head, a ring of fine lines inside it, spokes and diamonds of light
        *ring_upright(B, hc, 5.2, 12, 0.6, w=0.45, t=0.3, r="feather", seed=60),
        *ring_upright(B, hc, 3.9, 24, 0.45, w=0.18, t=0.2, r="feather", rim=0.6, core=0.0, phase=7.5, seed=61),
        *spokes(B, hc, 2.4, 3.7, 6, w=0.2, t=0.2, r="feather", seed=62),
        *nodes(B, hc, 5.2, 6, 0.6, phase=15.0),
    ])
    for side, sx in (("right", -1), ("left", 1)):
        piv = [sx * 3.3, 27.4, 0.0]
        B.bone(f"{side}_arm", "chest", piv, [
            col(sx * 3.6, 0.0, 22.2, 27.6, 1.6, 1.8, "cloth", **robe, seed=60 + (sx > 0)),
        ])
        B.bone(f"{side}_hand", f"{side}_arm", [sx * 3.6, 22.4, 0.0], [
            # a wide sleeve falling from the elbow, hemmed in light, and a slender hand in a glove of gold
            c(mx([2.3, 16.8, -1.5], [2.6, 6.2, 3.0], sx), [2.6, 6.2, 3.0], "cloth", **robe, hem=1.0, glow_hem=1.3, seed=62 + (sx > 0), skip=("up",)),
            box(sx * 3.6, 15.9, -0.2, 1.0, 1.8, 1.3, "trim", **TM),
            box(sx * 3.6, 14.6, -0.3, 0.8, 1.0, 1.1, "trim", **TM),
        ])
    # six wings: a great pair raised, a pair spread, a pair hanging low; the raised pair in front of the others
    wings = (
        ("wing", (1.3, 27.4, 2.3), 48, 8.5, 5.0, [(80, 14.5), (68, 15.6), (56, 16.0), (44, 15.4), (32, 14.2), (20, 12.8), (8, 11.6)],
         [(0.92, -8, 10.8), (0.74, -22, 10.4), (0.56, -36, 9.8), (0.38, -50, 9.0), (0.2, -62, 8.0)], (0, 18, 0)),
        ("wingb", (1.5, 25.2, 2.9), 6, 8.0, 4.4, [(36, 12.2), (24, 13.2), (12, 13.6), (0, 13.2), (-12, 12.4), (-24, 11.4)],
         [(0.9, -38, 10.2), (0.68, -50, 9.4), (0.46, -62, 8.4), (0.24, -72, 7.4)], (0, 28, 0)),
        ("wingc", (1.3, 22.8, 3.4), -36, 6.4, 3.8, [(-18, 10.2), (-30, 11.2), (-42, 11.6), (-54, 11.2), (-66, 10.4)],
         [(0.85, -78, 8.6), (0.6, -86, 7.6), (0.35, -94, 6.6)], (0, 24, 0)),
    )
    for wi, (n, root, elev, arm, hand_len, prim, sec, rest) in enumerate(wings):
        for side, sx in (("right", -1), ("left", 1)):
            light_wing(B, side, sx, n, "chest", root, elev, arm, hand_len, prim, sec, width=2.2, rest=rest,
                       seed=200 + wi * 100 + (50 if sx > 0 else 0))
    light_palette(ENVOY, GOLD, ENVOY_RAMPS, density=4.5)
    B.write(7.0, 4.4, 1.6)
    envoy_anims()


def envoy_anims():
    """Times match EnvoyEntity: idle (loop), leave 40 ticks (rising into the light, held; the renderer fades it over the
    last 14), and on the "act" controller talk and bless (which move only the bones they need)."""
    A = {}
    bones = ("root", "body", "robe_front", "robe_back", "chest", "head", "halo", "right_arm", "right_hand", "left_arm", "left_hand",
             *[f"{s}_{n}{k}" for n in ENVOY_WINGS for s in ("right", "left") for k in ("", "2")])
    # floating, its hands loosely together before it
    STAND = pose(pose(**{b: [0, 0, 0] for b in bones}), right_arm=[-10, 0, 8], right_hand=[-50, 0, -24], left_arm=[-10, 0, -8], left_hand=[-50, 0, 24])

    def P(base=None, **kw):
        return pose(base or STAND, **kw)

    L = 6.0
    A["idle"] = anim(L, True, timeline([(0, STAND, lin), (L, STAND, lin)], extra=merge({
        "root_pos": lambda t: [0, 0.55 * wave(t, L / 2), 0],
        "chest": lambda t: [0.8 * wave(t, L / 2, 0.2), 0, 0],
        "head": lambda t: [1.5 * wave(t, L / 2, 0.35), 4 * wave(t, L), 0],
        "halo": spin(2, -60.0, L),
        "robe_front": lambda t: [1.6 * wave(t, L / 2, 0.3), 0, 0], "robe_back": lambda t: [2.0 * wave(t, L / 2, 0.1), 0, 0],
        "right_hand": lambda t: [2.5 * wave(t, L / 2, 0.5), 0, 0], "left_hand": lambda t: [2.5 * wave(t, L / 2, 0.55), 0, 0],
    }, beat(L / 2, ENVOY_WINGS, amp=6.0, sweep=3.0, tip=6.0))), 0.05)

    # talk (2.4 s): its right hand lifted, open towards the one it speaks to, then lowered
    L = 2.4
    OPEN = dict(right_arm=[-52, -14, 16], right_hand=[-34, -10, 0], head=[6, -6, 4])
    a = anim(L, False, timeline([(0, STAND, lin), (0.55, P(**OPEN), ease_in_out), (1.7, pose(P(**OPEN), head=[2, -2, -2]), ease_in_out),
                                 (L, STAND, ease_in_out)], extra={"right_hand": lambda t: [5 * bump(t, 0.6, 1.6) * wave(t, 0.55), 0, 0]}), 0.05)
    a["bones"] = {k: v for k, v in a["bones"].items() if k in ("right_arm", "right_hand", "head")}
    A["talk"] = a

    # bless (2.8 s): its arms rising and opening wide, all six wings spread, its face lifted, then still again
    L = 2.8
    WIDE = P(right_arm=[-96, 0, 66], right_hand=[-14, 0, 0], left_arm=[-96, 0, -66], left_hand=[-14, 0, 0], head=[-14, 0, 0], chest=[-6, 0, 0],
             **raise_wings(("wing",), up=18, sweep=-10, tip=10), **raise_wings(("wingb",), up=12, sweep=-14, tip=8),
             **raise_wings(("wingc",), up=10, sweep=-10, tip=6))
    a = anim(L, False, timeline([(0, STAND, lin), (0.8, WIDE, ease_in_out), (2.0, WIDE, lin), (L, STAND, ease_in_out)],
                                extra=beat(1.2, ENVOY_WINGS, amp=5.0, sweep=2.0, tip=5.0, k=lambda t: bump(t, 0.6, 2.2))), 0.05)
    keep = ("right_arm", "right_hand", "left_arm", "left_hand", "head", "chest", *[f"{s}_{n}{k}" for n in ENVOY_WINGS for s in ("right", "left")
                                                                                   for k in ("", "2")])
    a["bones"] = {k: v for k, v in a["bones"].items() if k in keep}
    A["bless"] = a

    # leave (2.0 s, held): it opens its arms, beats its wings twice and rises into the light
    L = 2.0
    RISE = P(right_arm=[-60, 0, 42], right_hand=[-10, 0, 0], left_arm=[-60, 0, -42], left_hand=[-10, 0, 0], head=[-18, 0, 0],
             robe_front=[8, 0, 0], robe_back=[-6, 0, 0])
    A["leave"] = anim(L, "hold", timeline([(0, STAND, lin), (0.5, RISE, ease_in_out), (L, RISE, lin)], extra=merge({
        "root_pos": lambda t: [0, 26.0 * ease_in(window(t, 0.35, L)), 0],
        "halo": spin(2, -90.0, L),
    }, beat(0.9, ENVOY_WINGS, amp=24.0, sweep=8.0, tip=16.0, k=lambda t: window(t, 0.1, 0.5)))), 0.05)
    write_anims(ENVOY, A)


# ============================================================================================= the seraph form

SERAPH = "light_seraph"


def seraph():
    """Two great wings of light and a halo for a hunter's back, in the player model's own units: its origin is the
    neck (where SeraphLayer puts it: the top of the body, +y up, the front -z), the back at z = 2. The wings rise from
    the shoulder blades, the halo lies level over the head. Bones: root, right_wing/right_wing2, left_wing/left_wing2,
    halo."""
    B = OneSkin(SERAPH, 8300)
    B.bone("root", None, [0, 0, 0])
    for side, sx in (("right", -1), ("left", 1)):
        light_wing(B, side, sx, "wing", "root", (1.2, -3.2, 2.5), 36, 6.6, 3.8,
                   [(74, 9.4), (62, 10.2), (50, 10.6), (38, 10.2), (26, 9.4), (14, 8.6)],
                   [(0.9, -4, 8.0), (0.7, -20, 7.6), (0.5, -36, 7.0), (0.3, -52, 6.2)], width=1.7, t=0.25, z_step=0.09, rest=(0, 24, 0),
                   seed=300 + (50 if sx > 0 else 0), r="feather")
    hc = (0, 10.6, 0)
    B.bone("halo", "root", list(hc), [
        *ring_level(B, hc, 4.4, 10, 0.6, w=0.35, t=0.4, r="feather", seed=320),
        *ring_level(B, hc, 3.3, 20, 0.45, w=0.16, t=0.18, r="feather", rim=0.6, core=0.0, phase=9.0, seed=321),
        *nodes(B, hc, 4.4, 5, 0.5, phase=18.0, upright=False),
    ])
    light_palette(SERAPH, WHITE_LIGHT, WHITE_RAMPS, density=6.0)
    B.write(3.0, 2.0, 0.0)
    L = 3.0
    write_anims(SERAPH, {"idle": anim(L, True, timeline([(0, {}, lin), (L, {}, lin)], extra=merge({
        "halo": spin(1, 36.0, L),
        "halo_pos": lambda t: [0, 0.25 * wave(t, L), 0],
    }, beat(L, ("wing",), amp=9.0, sweep=4.0, tip=7.0))), 0.05)})


# ============================================================================================= the constructs

SPEAR = "light_spear"


def spear():
    """The lance of judgement: 3.2 blocks of light hurled point first out of the sky, its point driven a block into
    the ground at its foot (the model's origin), a broken ring of light round its head and its shaft fading to
    nothing above. Bones: root, lance, ring. Animations as LightConstruct: fall (loop), land, stand (loop)."""
    B = OneSkin(SPEAR, 8500)
    c = B.c
    B.bone("root", None, [0, 0, 0])
    head = []
    # the head: a long leaf of light, two blades crossed so it reads from any side
    for (y0, y1, w) in ((-6.0, -2.6, 0.8), (-2.7, 0.6, 1.7), (0.5, 4.4, 2.7), (4.3, 6.8, 2.2), (6.7, 8.2, 1.3)):
        for ry in (0, 90):
            head.append(c([-w / 2, y0, -0.3], [w, y1 - y0, 0.6], "lightm", pivot=[0, 0, 0], rot=[0, ry, 0], long="y", r="body", rim=1.1, core=0.2,
                          hot=(-6.0, -2.0), seed=int(10 + y0 * 3 + ry)))
    # fins of light near its foot, as a javelin is fletched
    for k in range(4):
        head.append(c([-0.15, 30.0, -1.6], [0.3, 7.0, 1.6], "lightm", pivot=[0, 30.0, 0], rot=[0, 45 + k * 90, 0], long="y", r="body", rim=0.9, core=0.0,
                      fade=(33.0, 37.2), seed=60 + k))
    head += [
        # a collar of gold where the head meets the shaft, a diamond of light in it
        B.box(0, 8.6, 0, 1.6, 1.4, 1.6, "trim", r="trim", marks=0.0, pivot=[0, 8.6, 0], rot=[0, 45, 0]),
        B.box(0, 8.6, 0, 1.0, 1.0, 1.9, "glow", pivot=[0, 8.6, 0], rot=[0, 45, 45]),
        # the shaft, eight-sided, burning down its edges and fading out above
        c([-0.4, 9.2, -0.4], [0.8, 37.0, 0.8], "lightm", long="y", r="body", rim=0.9, core=0.0, fade=(26.0, 46.0), seed=21),
        c([-0.4, 9.2, -0.4], [0.8, 37.0, 0.8], "lightm", pivot=[0, 9.2, 0], rot=[0, 45, 0], long="y", r="body", rim=0.9, core=0.0, fade=(26.0, 46.0),
          seed=22),
    ]
    # streaks of light trailing it, thin as threads
    for i in range(4):
        a = math.radians(45 + i * 90)
        x, z = math.cos(a) * 1.5, math.sin(a) * 1.5
        head.append(c([x - 0.1, 10.0 + i * 1.6, z - 0.1], [0.2, 20.0 - i * 2.0, 0.2], "lightm", long="y", r="body", rim=0.6, core=0.0,
                      fade=(13.0 + i * 1.6, 30.0 + i * 0.4), seed=30 + i))
    B.bone("lance", "root", [0, 0, 0], head)
    B.bone("ring", "lance", [0, 5.0, 0], [
        *ring_level(B, (0, 5.0, 0), 2.9, 6, 0.55, w=0.3, t=0.3, r="body", seed=40),
        *nodes(B, (0, 5.0, 0), 2.9, 6, 0.45, phase=30.0, upright=False),
    ])
    light_palette(SPEAR, WHITE_LIGHT, WHITE_RAMPS, density=6.0)
    B.write(1.0, 3.4, 0.0)
    A = {}
    A["fall"] = anim(0.5, True, timeline([(0, {}, lin), (0.5, {}, lin)], extra={"lance": spin(1, 360.0, 0.5), "ring": spin(1, -120.0, 0.5)}), 0.025)
    A["land"] = anim(0.4, False, timeline([(0, {}, lin), (0.4, {}, lin)], extra={
        "lance": lambda t: [shake(t, 0.0, 0.4, 3.0, 70, 0.05), 0, shake(t, 0.0, 0.4, 2.0, 55, 0.05)],
        "ring_scale": lambda t: [0.9 * bump(t, 0.0, 0.4)] * 3,
        "ring": spin(1, -60.0, 0.4),
    }), 0.025)
    A["stand"] = anim(1.0, True, timeline([(0, {}, lin), (1.0, {}, lin)], extra={
        "ring": spin(1, -60.0, 1.0),
        "lance": lambda t: [0.6 * wave(t, 0.5), 0, 0.4 * wave(t, 0.5, 0.25)],
    }), 0.025)
    write_anims(SPEAR, A)


FEATHER = "light_feather"


def feather_model():
    """A feather of the choir: 1.9 blocks, a blade of light falling point first and standing in the ground, quivering.
    Bones: root, feather. Animations as LightConstruct: fall (loop), land, stand (loop)."""
    B = OneSkin(FEATHER, 8600)
    c = B.c
    B.bone("root", None, [0, 0, 0])
    parts = []
    # its vane, widest two thirds of the way up, narrowing to the point in the ground; a thinner blade across it
    for (y0, y1, w) in ((-4.0, 0.2, 0.7), (0.0, 6.0, 1.6), (5.8, 16.0, 2.5), (15.8, 22.0, 2.0), (21.8, 26.0, 1.1)):
        parts.append(c([-w / 2, y0, -0.18], [w, y1 - y0, 0.36], "lightm", long="y", r="feather", rim=1.1, core=0.16, fade=(17.0, 26.5),
                       hot=(-4.0, -0.5), seed=int(50 + y0)))
        parts.append(c([-0.15, y0, -w * 0.25], [0.3, y1 - y0, w * 0.5], "lightm", long="y", r="feather", rim=0.8, core=0.0, fade=(17.0, 26.5),
                       seed=int(70 + y0)))
    B.bone("feather", "root", [0, 0, 0], parts)
    light_palette(FEATHER, WHITE_LIGHT, WHITE_RAMPS, density=7.0)
    B.write(0.6, 1.8, 0.0)
    A = {}
    A["fall"] = anim(0.4, True, timeline([(0, {}, lin), (0.4, {}, lin)], extra={"feather": spin(1, 360.0, 0.4)}), 0.025)
    A["land"] = anim(0.5, False, timeline([(0, {}, lin), (0.5, {}, lin)], extra={
        "feather": lambda t: [9 * math.exp(-t * 7) * math.sin(t * 46), 0, 6 * math.exp(-t * 7) * math.sin(t * 38 + 1)],
    }), 0.025)
    A["stand"] = anim(2.0, True, timeline([(0, {}, lin), (2.0, {}, lin)], extra={
        "feather": lambda t: [1.2 * wave(t, 1.0), 0, 0.8 * wave(t, 2.0, 0.25)],
    }), 0.025)
    write_anims(FEATHER, A)


BLADE = "light_blade"
G.MODEL_SCALE[BLADE] = 3.5


def blade():
    """The Blade of Heaven: a sword of light built 61 units tall and drawn 3.5 times bigger (13 blocks), driven point
    first a block into the ground at its foot (the model's origin). Its blade is light, burning at its edges, a spine of
    white fire and glyphs down its fuller; its guard a pair of wings of light over a bar of gold; a ring of gold and
    light for a pommel; a broken ring of light turning round it at its guard. Bones: root, sword, halo, crown (the
    pommel's ring). Animations as LightConstruct: fall (loop), land, stand (loop)."""
    B = OneSkin(BLADE, 8700)
    box, c = B.box, B.c
    TM = dict(r="trim", marks=0.0)
    B.bone("root", None, [0, 0, 0])
    parts = []
    # the blade, from its point up to the guard: stepped so it narrows to the point
    steps = ((-4.5, -1.0, 1.4), (-1.1, 3.0, 2.8), (2.9, 8.0, 4.4), (7.9, 15.0, 5.6), (14.9, 24.0, 6.4), (23.9, 33.0, 6.9), (32.9, 41.2, 7.2))
    for i, (y0, y1, w) in enumerate(steps):
        parts.append(c([-w / 2, y0, -0.7], [w, y1 - y0, 1.4], "lightm", long="y", r="body", rim=1.3, core=0.07, seed=100 + i,
                       hot=(-4.5, 0.0) if i < 2 else None,
                       runes=dict(region=(-1.0, 1.0, max(y0, 6.0), y1 - 0.3), faces=("north", "south"), seed=120 + i, cell=1.4, density=0.62)
                       if y1 > 8 else None))
        # the bevels: thin edges standing proud of the flat, white along the cut
        for sx in (-1, 1):
            parts.append(c([sx * (w / 2 + 0.05) - 0.25, y0, -0.35], [0.5, y1 - y0, 0.7], "lightm", long="y", r="body", rim=0.9, core=0.4,
                           seed=130 + i * 2 + (sx > 0)))
    # the guard: a bar of gold, a sun of light at its heart, and wings of light spread up from it
    parts += [
        box(0, 42.4, 0, 9.0, 2.4, 2.6, "trim", **TM),
        box(0, 42.4, 0, 3.6, 3.6, 3.0, "trim", **TM, pivot=[0, 42.4, 0], rot=[0, 0, 45]),
        box(0, 42.4, -1.5, 2.0, 2.0, 0.4, "glow", pivot=[0, 42.4, -1.5], rot=[0, 0, 45]),
        box(0, 42.4, 1.5, 2.0, 2.0, 0.4, "glow", pivot=[0, 42.4, 1.5], rot=[0, 0, 45]),
    ]
    for sx in (-1, 1):
        for j, (a, ln, w) in enumerate(((14, 14.0, 1.8), (32, 12.5, 1.7), (50, 10.5, 1.6), (68, 8.0, 1.4))):
            th = a if sx > 0 else 180 - a
            parts += feather(B, (sx * 3.6, 42.6, -0.3 + j * 0.18), th, ln, w, 0.5, seed=140 + j * 4 + (sx > 0) * 2, r="body", rim=1.2, core=0.18,
                             fade=(0.62, 1.02))
    # the grip, bound in white and gold, and the pommel's heart
    parts += [
        B.col(0, 0, 43.6, 53.4, 1.8, 1.8, "trim", r="plate", marks=0.0),
        *[box(0, 44.6 + k * 2.2, 0, 2.1, 0.5, 2.1, "trim", **TM) for k in range(5)],
        box(0, 54.4, 0, 2.6, 2.0, 2.6, "trim", **TM, pivot=[0, 54.4, 0], rot=[0, 45, 0]),
    ]
    B.bone("sword", "root", [0, 0, 0], parts)
    pc = (0, 57.6, 0)
    B.bone("crown", "sword", list(pc), [
        *ring_upright(B, pc, 3.2, 8, 0.62, w=0.6, t=0.6, r="body", seed=160),
        *spokes(B, pc, 0.9, 2.8, 4, w=0.35, t=0.35, phase=45.0, r="body", seed=161),
        box(0, 57.6, 0, 1.6, 1.6, 1.6, "glow", pivot=list(pc), rot=[45, 0, 45]),
    ])
    hc = (0, 42.4, 0)
    B.bone("halo", "sword", list(hc), [
        *ring_level(B, hc, 10.5, 10, 0.55, w=0.5, t=0.5, r="body", seed=170),
        *ring_level(B, hc, 8.6, 20, 0.4, w=0.25, t=0.25, r="body", rim=0.7, core=0.0, phase=9.0, seed=171),
        *nodes(B, hc, 10.5, 10, 0.9, phase=18.0, upright=False),
    ])
    light_palette(BLADE, WHITE_LIGHT, dict(WHITE_RAMPS, trim=ENVOY_RAMPS["trim"]), density=2.0)
    B.write(1.5, 4.0, 0.0)
    A = {}
    A["fall"] = anim(0.6, True, timeline([(0, {}, lin), (0.6, {}, lin)], extra={
        "halo": spin(1, 72.0, 0.6),
        "crown": spin(2, 90.0, 0.6),
        "sword": lambda t: [0.4 * wave(t, 0.3), 0, 0.3 * wave(t, 0.3, 0.25)],
    }), 0.025)
    A["land"] = anim(0.8, False, timeline([(0, {}, lin), (0.8, {}, lin)], extra={
        "sword": lambda t: [shake(t, 0.0, 0.7, 1.4, 60, 0.05), 0, shake(t, 0.0, 0.7, 1.0, 47, 0.05)],
        "halo_scale": lambda t: [0.7 * bump(t, 0.0, 0.8)] * 3,
        "halo": spin(1, 36.0, 0.8),
    }), 0.025)
    A["stand"] = anim(4.0, True, timeline([(0, {}, lin), (4.0, {}, lin)], extra={
        "halo": spin(1, 36.0, 4.0),
        "crown": spin(2, 90.0, 4.0),
        "crown_scale": lambda t: [1 + 0.06 * wave(t, 2.0)] * 3,
    }), 0.05)
    write_anims(BLADE, A)


HAND = "light_hand"
G.MODEL_SCALE[HAND] = 9.0


def hand_model():
    """The Hand of Heaven: a right hand of glass and light built 24 units long and drawn 9 times bigger (13 blocks from
    its fingertips to the wrist), palm down, coming out of the sky on an arm of light that fades into it. Its body is
    see-through; its edges, knuckles and the lines of its palm burn; a sun of light on its back. Over its spot (the
    origin) its palm hangs 3.5 blocks up, its fingertips touching the ground. Bones: root, hand (at the wrist), arm,
    palm, thumb/thumb2, finger1-4 and finger1b-4b (index to little finger, each in two). Animations as LightConstruct:
    open (loop, coming down), close (closing on what is under it, lifting it, slamming it down 1.5 s after landing),
    fist (loop)."""
    B = OneSkin(HAND, 8800)
    c = B.c
    glass = 0.42
    G_ = dict(r="body", glass=glass, rim=1.3, core=0.0)
    B.bone("root", None, [0, 0, 0])
    B.bone("hand", "root", [0, 6.4, 6.6])
    # the forearm: rising back from the wrist into the sky, fading out
    B.bone("arm", "hand", [0, 6.4, 6.6], [
        c([-3.6, 6.4, 5.0], [7.2, 22.0, 4.4], "lightm", pivot=[0, 6.4, 6.6], rot=[-58, 0, 0], long="y", **dict(G_, glass=0.36), fade=(10.0, 27.0),
          seed=10),
        c([-2.6, 6.4, 5.4], [5.2, 22.0, 3.6], "lightm", pivot=[0, 6.4, 6.6], rot=[-58, 0, 0], long="y", r="body", glass=0.0, rim=0.0, core=0.12,
          fade=(9.0, 22.0), seed=11),
    ])
    palm = [
        c([-4.6, 5.6, -3.8], [9.2, 2.4, 10.6], "lightm", long="z", **G_, seed=20,
          lines=[dict(pts=[(-3.6, 2.0), (3.4, -1.0)], w=0.3, faces=("down",), halo=0.6, bright=0.9),
                 dict(pts=[(-3.8, -1.6), (2.8, -3.0)], w=0.3, faces=("down",), halo=0.6, bright=0.9),
                 dict(pts=[(2.6, 6.0), (0.4, 0.0), (1.2, -3.4)], w=0.3, faces=("down",), halo=0.6, bright=0.9)]),
        # the knuckles: a ridge of light across the back
        c([-4.5, 7.6, -3.9], [9.0, 0.9, 1.4], "lightm", long="x", r="body", glass=glass, rim=1.0, core=0.35, seed=21),
        # a sun of light on its back: a diamond, its rays and a ring
        B.box(0, 8.1, 1.6, 2.2, 0.4, 2.2, "glow", pivot=[0, 8.1, 1.6], rot=[0, 45, 0]),
        *ring_level(B, (0, 8.15, 1.6), 2.6, 8, 0.6, w=0.2, t=0.3, r="body", seed=22),
        *[c([-0.15, 8.0, 1.6 - 0.15], [0.3, 0.3, 2.6], "lightm", pivot=[0, 8.0, 1.6], rot=[0, k * 45 + 22.5, 0], long="z", r="body", rim=0.6, core=0.5,
            seed=23 + k) for k in range(8)],
    ]
    B.bone("palm", "hand", [0, 6.8, 2.0], palm)
    # four fingers from the knuckles forward (-z), the index on the thumb's side (+x)
    fingers = ((3.5, 5.8, 5.0, 1.75, 9), (1.2, 6.6, 5.8, 1.8, 2), (-1.1, 6.2, 5.4, 1.75, -5), (-3.3, 5.0, 4.4, 1.55, -13))
    for i, (x, l1, l2, w, splay) in enumerate(fingers):
        n = f"finger{i + 1}"
        y, z = 6.8, -3.8
        B.bone(n, "palm", [x, y, z], [
            c([x - w / 2, y - 1.05, z - l1], [w, 2.1, l1], "lightm", long="z", **G_, seed=30 + i * 4),
            c([x - w / 2 - 0.05, y - 0.4, z - l1 - 0.5], [w + 0.1, 1.5, 1.0], "lightm", long="x", r="body", glass=glass, rim=0.8, core=0.4, seed=31 + i * 4),
        ], rot=[8, splay, 0])
        B.bone(n + "b", n, [x, y, z - l1], [
            c([x - w * 0.45, y - 0.95, z - l1 - l2], [w * 0.9, 1.9, l2], "lightm", long="z", **G_, seed=32 + i * 4),
            c([x - w * 0.3, y - 0.7, z - l1 - l2 - 0.9], [w * 0.6, 1.3, 1.0], "lightm", long="z", r="body", glass=glass, rim=0.8, core=0.5, seed=33 + i * 4),
        ], rot=[14, 0, 0])
    # the thumb, from the side of the palm near the wrist, reaching forward and out
    B.bone("thumb", "palm", [4.2, 6.4, 3.4], [
        c([3.15, 5.25, -2.6], [2.1, 2.3, 6.0], "lightm", long="z", **G_, seed=50),
    ], rot=[14, 42, -18])
    B.bone("thumb2", "thumb", [4.2, 6.4, -2.6], [
        c([3.25, 5.4, -7.0], [1.9, 2.0, 4.4], "lightm", long="z", **G_, seed=51),
        c([3.55, 5.6, -7.9], [1.3, 1.5, 1.0], "lightm", long="z", r="body", glass=glass, rim=0.8, core=0.5, seed=52),
    ], rot=[16, -10, 0])
    light_palette(HAND, WHITE_LIGHT, WHITE_RAMPS, density=1.0)
    B.write(2.0, 3.0, 0.0)
    hand_anims()


def hand_anims():
    A = {}
    fingers = [f"finger{i}" for i in range(1, 5)]
    OPEN = pose(**{f: [0, 0, 0] for f in fingers}, **{f + "b": [0, 0, 0] for f in fingers}, thumb=[0, 0, 0], thumb2=[0, 0, 0], hand=[0, 0, 0],
                palm=[0, 0, 0])
    SPREAD = pose(OPEN, **{f: [-6, 0, 0] for f in fingers}, **{f + "b": [-8, 0, 0] for f in fingers}, thumb=[0, 8, 6])
    FIST = pose(OPEN, **{f: [84, 0, 0] for f in fingers}, **{f + "b": [96, 0, 0] for f in fingers}, thumb=[22, 38, 6], thumb2=[30, 20, 0],
                palm=[6, 0, 0])
    L = 1.6
    A["open"] = anim(L, True, timeline([(0, SPREAD, lin), (L, SPREAD, lin)], extra={
        **{f: (lambda t, i=i: [4 * wave(t, L, i * 0.12), 0, 0]) for i, f in enumerate(fingers)},
        **{f + "b": (lambda t, i=i: [5 * wave(t, L, i * 0.12 - 0.1), 0, 0]) for i, f in enumerate(fingers)},
        "hand": lambda t: [1.5 * wave(t, L), 0, 1.0 * wave(t, L, 0.25)],
    }), 0.05)
    # close (2.4 s): the fingers close on what is under it (0-0.35 s), the fist lifts it (to 1.15 s), trembles, and
    # slams it down at 1.5 s (when the server does), then settles
    L = 2.4
    A["close"] = anim(L, False, timeline([(0, SPREAD, lin), (0.35, FIST, ease_in), (1.5, FIST, lin), (L, FIST, lin)], extra={
        "root_pos": lambda t: [0, 7.0 * ease_out(window(t, 0.35, 1.15)) * (1 - ease_in(window(t, 1.32, 1.5))) - 0.7 * bump(t, 1.5, 1.7), 0],
        "hand": lambda t: [-10 * ease_in_out(window(t, 0.35, 1.15)) * (1 - ease_in(window(t, 1.32, 1.5))) + 6 * bump(t, 1.42, 1.62)
                           + shake(t, 1.1, 1.36, 1.5, 70, 0.05), 0, shake(t, 1.5, 2.1, 1.2, 50, 0.08)],
    }), 0.025)
    L = 1.0
    A["fist"] = anim(L, True, timeline([(0, FIST, lin), (L, FIST, lin)], extra={
        "hand": lambda t: [0.6 * wave(t, L / 2), 0, 0.4 * wave(t, L / 2, 0.25)],
    }), 0.05)
    write_anims(HAND, A)


SUN = "light_sun"
G.MODEL_SCALE[SUN] = 2.0


def sun():
    """The sun of Daybreak, built round its heart (the model's origin; the renderer lifts it 9 blocks over the fight)
    and drawn twice as big (4 blocks across its rays): a heart of faceted light, rays standing out of it every way,
    and three broken rings turning on their own axes, one lying level with glyph tiles on it. Bones: root, heart,
    rays, ring_a (level), ring_b and ring_c (tilted; spun inside ring_b_spin and ring_c_spin)."""
    B = OneSkin(SUN, 8900)
    c = B.c
    B.bone("root", None, [0, 0, 0])
    B.bone("heart", "root", [0, 0, 0], [
        # three cubes of light turned on each other: a faceted star of a heart
        B.box(0, 0, 0, 6.0, 6.0, 6.0, "lightm", r="body", rim=1.6, core=0.0, long="y", seed=10),
        B.box(0, 0, 0, 6.0, 6.0, 6.0, "lightm", pivot=[0, 0, 0], rot=[45, 45, 0], r="body", rim=1.6, core=0.0, long="y", seed=11),
        B.box(0, 0, 0, 5.0, 5.0, 5.0, "lightm", pivot=[0, 0, 0], rot=[0, 45, 45], r="body", rim=1.4, core=0.0, long="y", seed=12),
        B.box(0, 0, 0, 3.4, 3.4, 3.4, "glow", pivot=[0, 0, 0], rot=[30, 30, 30]),
    ])
    rays = []
    # rays every way: the six straight ones long, the eight between them shorter, and a ring of thin ones level
    dirs = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
    dirs += [(sx, sy, sz) for sx in (-1, 1) for sy in (-1, 1) for sz in (-1, 1)]
    for i, d in enumerate(dirs):
        ln = 14.0 if i < 6 else 9.0
        x, y, z = d
        n = math.sqrt(x * x + y * y + z * z)
        x, y, z = x / n, y / n, z / n
        # a ray built along +y, turned to point along d: tilt (x rot) then turn (y rot)
        pitch = math.degrees(math.acos(max(-1.0, min(1.0, y))))
        yaw = math.degrees(math.atan2(x, -z)) if abs(y) < 0.999 else 0.0
        w = 0.75 if i < 6 else 0.5
        rays.append(c([-w / 2, 3.2, -w / 2], [w, ln, w], "lightm", pivot=[0, 0, 0], rot=[pitch, -yaw, 0], long="y", r="body", rim=0.9, core=0.35,
                      fade=(3.2 + ln * 0.45, 3.2 + ln * 1.02), seed=20 + i))
    for k in range(12):
        a = k * 30.0 + 15.0
        rays.append(c([-0.18, 3.0, -0.18], [0.36, 8.0 if k % 2 else 5.5, 0.36], "lightm", pivot=[0, 0, 0], rot=[90, a, 0], long="y", r="body", rim=0.6,
                      core=0.4, fade=(5.0, 11.2 if k % 2 else 8.6), seed=40 + k))
    for k in range(12):
        # and a crown of thin ones standing up and hanging down
        a = k * 30.0
        for tilt in (28, 152):
            rays.append(c([-0.16, 3.0, -0.16], [0.32, 6.0, 0.32], "lightm", pivot=[0, 0, 0], rot=[tilt, a, 0], long="y", r="body", rim=0.6, core=0.4,
                          fade=(4.5, 9.2), seed=90 + k + tilt))
    B.bone("rays", "root", [0, 0, 0], rays)
    # ring a: level, with glyph tiles standing on it
    tiles = []
    for k in range(6):
        a = math.radians(k * 60.0)
        x, z = math.cos(a) * 11.0, math.sin(a) * 11.0
        tiles.append(B.box(x, 0, z, 0.4, 2.2, 2.2, "lightm", pivot=[x, 0, z], rot=[0, -k * 60.0, 0], r="body", rim=0.8, core=0.0, long="y", seed=60 + k,
                           runes=dict(region=(-99, 99, -99, 99), faces=("west", "east"), seed=61 + k, cell=1.0, density=0.8)))
    B.bone("ring_a", "root", [0, 0, 0], [*ring_level(B, (0, 0, 0), 11.0, 12, 0.5, w=0.4, t=0.4, phase=15.0, r="body", seed=70), *tiles])
    for n, tilt, rad, segs in (("ring_b", (62, 0, 20), 14.0, 10), ("ring_c", (-58, 0, -24), 16.5, 14)):
        B.bone(n, "root", [0, 0, 0], rot=list(tilt))
        B.bone(n + "_spin", n, [0, 0, 0], [*ring_level(B, (0, 0, 0), rad, segs, 0.55, w=0.35, t=0.35, r="body", seed=80 + segs),
                                            *nodes(B, (0, 0, 0), rad, segs // 2, 0.7, phase=360.0 / segs / 2, upright=False)])
    light_palette(SUN, WHITE_LIGHT, WHITE_RAMPS, density=2.5)
    B.write(2.4, 2.4, -1.2)
    A = {}
    L = 1.2
    A["rise"] = anim(L, False, timeline([(0, {}, lin), (L, {}, lin)], extra={
        "root_scale": lambda t: [0.15 + 0.85 * ease_out_back(window(t, 0.0, 0.9))] * 3,
        "rays_scale": lambda t: [0.2 + 0.8 * ease_out(window(t, 0.25, 1.1))] * 3,
        "ring_a": spin(1, 120.0, L), "ring_b_spin": spin(1, -150.0, L), "ring_c_spin": spin(1, 100.0, L),
        "heart": spin(1, 90.0, L),
    }), 0.025)
    L = 6.0
    A["idle"] = anim(L, True, timeline([(0, {}, lin), (L, {}, lin)], extra={
        "ring_a": spin(1, 60.0, L), "ring_b_spin": spin(1, -72.0, L), "ring_c_spin": spin(1, 51.428571, L),
        "heart": spin(1, 90.0, L),
        "rays": spin(1, -30.0, L),
        "rays_scale": lambda t: [1 + 0.06 * wave(t, L / 4)] * 3,
        "heart_scale": lambda t: [1 + 0.05 * wave(t, L / 6)] * 3,
    }), 0.05)
    write_anims(SUN, A)


CHOIR = "light_choir"
G.MODEL_SCALE[CHOIR] = 1.3
CHOIR_WINGS = ("wing", "wingb", "wingc")


def choir():
    """The Choir of Wings: six wings of light and a broken ring standing at a ruler's back, built round their feet (the
    model's origin, turned with them) and drawn 1.3 times bigger: the wings rise from behind their shoulders (1.4
    blocks up), the ring stands behind their head. Bones: root, right_wing/right_wing2 (raised), right_wingb (spread),
    right_wingc (hanging), the same on the left, halo. Animations: rise (once, the wings opening), idle (loop)."""
    B = OneSkin(CHOIR, 9000)
    B.bone("root", None, [0, 0, 0])
    wings = (
        ("wing", (1.4, 18.4, 2.6), 50, 9.0, 5.2, [(82, 15.2), (70, 16.4), (58, 16.8), (46, 16.2), (34, 15.0), (22, 13.6), (10, 12.2)],
         [(0.92, -6, 11.4), (0.74, -20, 11.0), (0.56, -34, 10.4), (0.38, -48, 9.6), (0.2, -60, 8.6)], (0, 16, 0)),
        ("wingb", (1.6, 16.6, 3.2), 6, 8.6, 4.6, [(36, 13.0), (24, 14.0), (12, 14.4), (0, 14.0), (-12, 13.2), (-24, 12.2)],
         [(0.9, -38, 10.8), (0.68, -50, 10.0), (0.46, -62, 9.0), (0.24, -72, 8.0)], (0, 26, 0)),
        ("wingc", (1.4, 14.2, 3.8), -38, 7.0, 4.0, [(-18, 10.8), (-30, 11.8), (-42, 12.2), (-54, 11.8), (-66, 11.0)],
         [(0.85, -78, 9.0), (0.6, -86, 8.0), (0.35, -94, 7.0)], (0, 22, 0)),
    )
    for wi, (n, root, elev, arm, hand_len, prim, sec, rest) in enumerate(wings):
        for side, sx in (("right", -1), ("left", 1)):
            light_wing(B, side, sx, n, "root", root, elev, arm, hand_len, prim, sec, width=2.3, rest=rest,
                       seed=400 + wi * 100 + (50 if sx > 0 else 0))
    hc = (0, 21.8, 3.6)
    B.bone("halo", "root", list(hc), [
        *ring_upright(B, hc, 6.4, 12, 0.6, w=0.5, t=0.3, r="feather", seed=470),
        *ring_upright(B, hc, 5.0, 24, 0.45, w=0.2, t=0.2, r="feather", rim=0.6, core=0.0, phase=7.5, seed=471),
        *spokes(B, hc, 3.2, 4.8, 6, w=0.22, t=0.22, r="feather", seed=472),
        *nodes(B, hc, 6.4, 6, 0.7, phase=15.0),
    ])
    light_palette(CHOIR, WHITE_LIGHT, WHITE_RAMPS, density=5.0)
    B.write(4.0, 3.0, 0.0)
    A = {}
    FOLD = pose(**raise_wings(("wing",), up=-40, sweep=60, tip=-50), **raise_wings(("wingb",), up=-30, sweep=70, tip=-40),
                **raise_wings(("wingc",), up=-10, sweep=60, tip=-30), root_scale=[0.6, 0.6, 0.6], halo_scale=[0.0, 0.0, 0.0])
    OPENED = pose(**raise_wings(CHOIR_WINGS, 0, 0, 0), root_scale=[1, 1, 1], halo_scale=[1, 1, 1])
    L = 1.0
    A["rise"] = anim(L, False, timeline([(0, FOLD, lin), (0.75, OPENED, ease_out_back), (L, OPENED, lin)], extra={"halo": spin(2, -30.0, L)}), 0.025)
    L = 3.0
    A["idle"] = anim(L, True, timeline([(0, OPENED, lin), (L, OPENED, lin)], extra=merge({
        "halo": spin(2, -30.0, L),
        "root_pos": lambda t: [0, 0.3 * wave(t, L), 0],
    }, beat(L, CHOIR_WINGS, amp=8.0, sweep=4.0, tip=7.0))), 0.05)
    write_anims(CHOIR, A)


# ============================================================================================= icons

# the light's powers (keep in sync with RulerPowers) and mantles (keep in sync with Mantle)
POWER_ICONS = {
    "light_judgement": "eye",
    "light_blade_of_heaven": "sword",
    "light_choir_of_wings": "feather",
    "light_hand_of_heaven": "hand-grabbing",
    "light_daybreak": "sun",
}
MANTLE_ICONS = {
    "verdict": "scales",
    "hand": "hand-palm",
    "dawn": "sun-horizon",
}


def _canvas():
    from gen_ui_assets import SIZE, SS
    n = SIZE * SS
    return n, Image.new("L", (n, n), 0)


def _wing_polys(cx, cy, s, sx, feathers=5, lift=0.0):
    """A wing of blades for an icon: feathers fanned out from the shoulder at (cx, cy), on side sx."""
    polys = []
    for i in range(feathers):
        a = math.radians(lift + 62 - i * 22)
        ln = s * (1.0 - 0.1 * abs(i - 1.2))
        w = s * 0.085
        ux, uy = math.cos(a) * sx, -math.sin(a)
        px, py = -uy, ux
        x0, y0 = cx + ux * s * 0.12, cy + uy * s * 0.12
        x1, y1 = cx + ux * ln, cy + uy * ln
        polys.append([(x0 + px * w, y0 + py * w), (x0 + ux * ln * 0.75 + px * w * 1.1, y0 + uy * ln * 0.75 + py * w * 1.1), (x1, y1),
                      (x0 + ux * ln * 0.75 - px * w * 1.1, y0 + uy * ln * 0.75 - py * w * 1.1), (x0 - px * w, y0 - py * w)])
    return polys


def icon_seraph():
    """Two wings of blades and a broken halo over them."""
    n, im = _canvas()
    d = ImageDraw.Draw(im)
    s = n * 0.36
    cx, cy = n / 2, n * 0.58
    for sx in (-1, 1):
        for poly in _wing_polys(cx + sx * n * 0.04, cy, s, sx):
            d.polygon(poly, fill=255)
    # the halo: a level ring seen from the front, broken into segments
    hx, hy, rx, ry = n / 2, n * 0.27, n * 0.17, n * 0.06
    for k in range(10):
        a0, a1 = k * 36 + 6, k * 36 + 28
        d.arc([hx - rx, hy - ry, hx + rx, hy + ry], a0, a1, fill=255, width=int(n * 0.028))
    d.ellipse([n / 2 - n * 0.05, cy - n * 0.05, n / 2 + n * 0.05, cy + n * 0.05], fill=255)
    return np.asarray(im) >= 128


def icon_sanctum():
    """A dome of light over ground: a broken arc, rays inside it, and the ground line."""
    n, im = _canvas()
    d = ImageDraw.Draw(im)
    cx, cy, r = n / 2, n * 0.66, n * 0.36
    w = int(n * 0.05)
    for k in range(6):
        a0 = 180 + k * 30 + 3
        d.arc([cx - r, cy - r, cx + r, cy + r], a0, a0 + 24, fill=255, width=w)
    for k in range(5):
        a = math.radians(180 + 30 + k * 30)
        r0, r1 = r * 0.22, r * (0.72 if k % 2 == 0 else 0.55)
        x0, y0 = cx + math.cos(a) * r0, cy + math.sin(a) * r0
        x1, y1 = cx + math.cos(a) * r1, cy + math.sin(a) * r1
        d.line([(x0, y0), (x1, y1)], fill=255, width=int(n * 0.032))
    d.rectangle([cx - r * 1.12, cy + n * 0.03, cx + r * 1.12, cy + n * 0.03 + w], fill=255)
    d.ellipse([cx - n * 0.06, cy - n * 0.06, cx + n * 0.06, cy + n * 0.06], fill=255)
    return np.asarray(im) >= 128


def icon_mantle_wings():
    """The Mantle of a Thousand Wings: three pairs of wings of blades."""
    n, im = _canvas()
    d = ImageDraw.Draw(im)
    cx, cy = n / 2, n * 0.52
    for sx in (-1, 1):
        for lift, s, f in ((22, n * 0.36, 4), (-18, n * 0.3, 4), (-58, n * 0.24, 3)):
            for poly in _wing_polys(cx + sx * n * 0.035, cy, s, sx, feathers=f, lift=lift):
                d.polygon(poly, fill=255)
    d.polygon([(cx, cy - n * 0.1), (cx + n * 0.045, cy), (cx, cy + n * 0.1), (cx - n * 0.045, cy)], fill=255)
    return np.asarray(im) >= 128


def icons():
    from gen_ui_assets import icon_mask, sdf_from_mask, write_sdf
    for path, src in POWER_ICONS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "skill_" + path)
    write_sdf(sdf_from_mask(icon_sanctum()), "skill_light_sanctum")
    write_sdf(sdf_from_mask(icon_seraph()), "skill_light_seraph")
    for key, src in MANTLE_ICONS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "light_mantle_" + key)
    write_sdf(sdf_from_mask(icon_mantle_wings()), "light_mantle_wings")
    print(f"  icons: {len(POWER_ICONS) + 2} powers, {len(MANTLE_ICONS) + 1} mantles")


def feather_icon(n=64):
    """The Envoy's feather: a long blade of light with a white spine and a gold edge, lying corner to corner. Two
    layers: the feather (lit by the world) and its light (full-bright)."""
    ss = 4
    w = n * ss
    base = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    db, dg = ImageDraw.Draw(base), ImageDraw.Draw(glow)
    # along the diagonal from the quill (bottom left) to the tip (top right)
    ux, uy = math.cos(math.radians(-45)), math.sin(math.radians(-45))
    px, py = -uy, ux
    x0, y0 = w * 0.16, w * 0.84
    ln = w * 0.95

    def at(t, s):
        return (x0 + ux * ln * t + px * s, y0 + uy * ln * t + py * s)

    def vane(width, k=1.0):
        pts = []
        for t in np.linspace(0.08, 1.0, 24):
            pts.append(at(t, width * math.sin(math.pi * min(1.0, t * 1.15)) ** 0.7 * k))
        for t in np.linspace(1.0, 0.08, 24):
            pts.append(at(t, -width * 0.8 * math.sin(math.pi * min(1.0, t * 1.15)) ** 0.7 * k))
        return pts

    db.polygon(vane(w * 0.15), fill=(176, 132, 58, 255))
    db.polygon(vane(w * 0.13), fill=(236, 220, 186, 255))
    db.polygon(vane(w * 0.09), fill=(250, 244, 230, 255))
    # the barbs: thin cuts across the vane, kept inside it
    barbs = Image.new("RGBA", (w, w), (0, 0, 0, 0))
    dbb = ImageDraw.Draw(barbs)
    for t in np.linspace(0.2, 0.86, 9):
        a, b = at(t, w * 0.02), at(t + 0.07, w * 0.12)
        dbb.line([a, b], fill=(214, 186, 128, 255), width=int(0.8 * ss))
        a, b = at(t, -w * 0.02), at(t + 0.07, -w * 0.09)
        dbb.line([a, b], fill=(214, 186, 128, 255), width=int(0.8 * ss))
    inside = Image.new("L", (w, w), 0)
    ImageDraw.Draw(inside).polygon(vane(w * 0.12), fill=255)
    base.paste(barbs, (0, 0), Image.fromarray(np.minimum(np.asarray(barbs.split()[3]), np.asarray(inside))))
    # the quill, in gold
    db.line([at(-0.02, 0), at(0.12, 0)], fill=(198, 148, 58, 255), width=int(2.4 * ss))
    # its light: the spine white-hot, the edge gold
    dg.line([at(0.08, 0), at(0.96, 0)], fill=(255, 252, 240, 255), width=int(1.6 * ss))
    # its edge burning gold, and a spark at its point
    edge = Image.new("L", (w, w), 0)
    ImageDraw.Draw(edge).polygon(vane(w * 0.15), fill=255)
    inner = Image.new("L", (w, w), 0)
    ImageDraw.Draw(inner).polygon(vane(w * 0.125), fill=255)
    rim = np.clip(np.asarray(edge).astype(int) - np.asarray(inner).astype(int), 0, 255).astype(np.uint8)
    glow.paste(Image.new("RGBA", (w, w), (255, 214, 120, 255)), (0, 0), Image.fromarray(rim))
    tx, ty = at(0.97, 0)
    dg.ellipse([tx - 2 * ss, ty - 2 * ss, tx + 2 * ss, ty + 2 * ss], fill=(255, 255, 255, 255))
    base = base.resize((n, n), Image.LANCZOS)
    glow = glow.resize((n, n), Image.LANCZOS)
    a = base.split()[3].point(lambda v: 255 if v > 40 else 0)
    grown = a.filter(ImageFilter.MaxFilter(3))
    out = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    out.paste(Image.new("RGBA", (n, n), (60, 40, 10, 255)), (0, 0), grown)
    out.alpha_composite(base)
    return out, glow


def items():
    item_tex = os.path.join(ASSETS, "textures", "item")
    item_models = os.path.join(ASSETS, "models", "item")
    for d in (item_tex, item_models):
        os.makedirs(d, exist_ok=True)
    base, glow = feather_icon()
    base.save(os.path.join(item_tex, "envoy_feather.png"), optimize=True)
    glow.save(os.path.join(item_tex, "envoy_feather_light.png"), optimize=True)
    model = {"parent": "minecraft:item/generated", "loader": "neoforge:item_layers",
             "textures": {"layer0": "beyondsolo:item/envoy_feather", "layer1": "beyondsolo:item/envoy_feather_light"},
             "neoforge_data": {"layers": {"1": {"block_light": 15, "sky_light": 15}}}}
    with open(os.path.join(item_models, "envoy_feather.json"), "w") as f:
        json.dump(model, f, indent=2)
        f.write("\n")
    print("  items: envoy_feather")


# ============================================================================================= main

def main():
    want = set(sys.argv[1:])
    makers = {"envoy": envoy, "seraph": seraph, "spear": spear, "feather": feather_model, "blade": blade, "hand": hand_model, "sun": sun,
              "choir": choir, "icons": icons, "items": items}
    for key, fn in makers.items():
        if not want or key in want:
            print(key)
            fn()


if __name__ == "__main__":
    main()
