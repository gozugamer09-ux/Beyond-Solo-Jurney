#!/usr/bin/env python3
"""
Offline preview of worn armor: a mannequin in the player's shape wearing a set's two armor layers and its extra parts
(tools/armor_rig.py), drawn with the same software rasteriser as tools/preview_model.py (the entity shader's two
lights, cut-out alpha, no back-face culling like the game's armor render type). A set can be checked from every side
in a second instead of starting the game.

    python3 tools/armor_preview.py forgehand                  # -> build/preview/armor_forgehand.png
    python3 tools/armor_preview.py forgehand --views front,fl,back --walk
    python3 tools/armor_preview.py --lineup warrior           # every set of a class side by side

Needs: pillow, numpy.
"""
import argparse
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_rig as rig  # noqa: E402
import preview_model as pm  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
ARMOR_TEX = os.path.join(ASSETS, "textures", "models", "armor")
GEO_DIR = os.path.join(ASSETS, "models", "armor")
OUT_DIR = os.path.join(ROOT, "build", "preview")


# ------------------------------------------------------------------------------------------------- mannequin skin

def mannequin_skin():
    """A plain 64x64 player skin: tan face with eyes and hair, charcoal shirt, dark trousers, grey shoes."""
    s = np.zeros((64, 64, 4), np.uint8)

    def fill(x, y, w, h, c):
        s[y:y + h, x:x + w, :3] = c
        s[y:y + h, x:x + w, 3] = 255

    skin, skin_d = (198, 150, 116), (170, 122, 92)
    hair, hair_d = (66, 44, 32), (48, 32, 24)
    shirt, shirt_d = (58, 62, 74), (44, 46, 56)
    pants, pants_d = (46, 44, 56), (36, 34, 44)
    shoe = (82, 80, 88)
    # head (0,0) 8x8x8
    fill(8, 0, 8, 8, hair)            # top
    fill(16, 0, 8, 8, skin_d)         # bottom
    for x, w in ((0, 8), (8, 8), (16, 8), (24, 8)):
        fill(x, 8, w, 8, skin)
    fill(0, 8, 8, 3, hair); fill(16, 8, 8, 3, hair); fill(24, 8, 8, 8, hair); fill(8, 8, 8, 2, hair)
    fill(0, 11, 2, 2, hair_d); fill(22, 11, 2, 2, hair_d)
    s[12, 9:11, :3] = (250, 250, 250); s[12, 10, :3] = (40, 60, 120)
    s[12, 13:15, :3] = (250, 250, 250); s[12, 13, :3] = (40, 60, 120)
    s[11, 9:11, :3] = hair_d; s[11, 13:15, :3] = hair_d
    s[14, 11:13, :3] = skin_d
    s[15, 10:14, :3] = (150, 96, 80)
    # body (16,16) 8x12x4
    fill(20, 16, 8, 4, shirt); fill(28, 16, 8, 4, shirt_d)
    fill(16, 20, 24, 12, shirt)
    fill(16, 30, 24, 2, shirt_d)
    # arms: right (40,16), left (32,48): 4x12x4
    for ax, ay in ((40, 16), (32, 48)):
        fill(ax + 4, ay, 4, 4, shirt); fill(ax + 8, ay, 4, 4, skin_d)
        fill(ax, ay + 4, 16, 6, shirt)
        fill(ax, ay + 10, 16, 6, skin)
        fill(ax, ay + 9, 16, 1, shirt_d)
    # legs: right (0,16), left (16,48)
    for lx, ly in ((0, 16), (16, 48)):
        fill(lx + 4, ly, 4, 4, pants); fill(lx + 8, ly, 4, 4, shoe)
        fill(lx, ly + 4, 16, 9, pants)
        fill(lx, ly + 13, 16, 3, shoe)
        fill(lx, ly + 12, 16, 1, pants_d)
    return s


# ------------------------------------------------------------------------------------------------- scene

SKIN_BOXES = [
    # (name, part, origin, size, uv)
    ("head", "head", (-4, -8, -4), (8, 8, 8), (0, 0)),
    ("body", "body", (-4, 0, -2), (8, 12, 4), (16, 16)),
    ("rarm", "right_arm", (-3, -2, -2), (4, 12, 4), (40, 16)),
    ("larm", "left_arm", (-1, -2, -2), (4, 12, 4), (32, 48)),
    ("rleg", "right_leg", (-2, 0, -2), (4, 12, 4), (0, 16)),
    ("lleg", "left_leg", (-2, 0, -2), (4, 12, 4), (16, 48)),
]

WALK = {"right_arm": (-28, 0, 0), "left_arm": (26, 0, 0), "right_leg": (24, 0, 0), "left_leg": (-24, 0, 0), "head": (0, 0, 0)}
GUARD = {"right_arm": (-50, -15, 0), "left_arm": (-20, 20, 0), "right_leg": (8, 0, 0), "left_leg": (-6, 0, 0)}


def load_geo(name, parts=None):
    if parts is None:
        path = os.path.join(GEO_DIR, name + ".json")
        if not os.path.exists(path):
            return []
        parts = json.load(open(path)).get("parts", [])
    out = []
    for e in parts:
        b = rig.Box(e["name"], e["part"], e["origin"], e["size"], e["uv"], e.get("inflate", 0), e.get("pivot"),
                    e.get("rotation", (0, 0, 0)), mirror=e.get("mirror", False), slot=e.get("slot", "chest"),
                    layer=e.get("layer", 1))
        out.append(b)
    return out


def scene_quads(name, slots=("head", "chest", "legs", "feet"), pose=None, skin=True, tex=None, parts=None, shift=None,
                cut=None):
    """Quads for the mannequin wearing set `name`, in the preview's space (y up, feet at 0). shift: {part: (dx, dy, dz)}
    moves a body part (for item icons: the two boots apart). cut: {part: y} ends that part's armor box at y (part
    space, y down) and leaves out the extra parts hung below it (for item icons: a chestplate with short sleeves)."""
    quads = []
    shift = shift or {}
    cut = cut or {}

    def short(b, part):
        if part not in cut:
            return b.size
        return (b.size[0], max(1.0, min(b.size[1], cut[part] - b.origin[1])), b.size[2])

    def below(b):
        if b.part not in cut:
            return False
        top = (b.pivot[1] if b.pivot is not None else 0.0) + b.origin[1]
        return top >= cut[b.part] - 1.0

    def moved(part, pp, mirrored=False):
        d = shift.get(part)
        if not d:
            return pp
        piv, r = pp
        dx = -d[0] if mirrored else d[0]
        return (piv[0] + dx, piv[1] + d[1], piv[2] + d[2]), r
    # atlas: layer 1 at x 0, layer 2 at x 128, skin (scaled 2x) at x 256
    if skin:
        for nm, part, origin, size, uv in SKIN_BOXES:
            b = rig.Box(nm, part, origin, size, uv, 0.0)
            quads += rig.box_quads(b, rig.part_pose(part, pose), atlas_off=(256, 0))
    base = rig.base_boxes()
    for layer, boxes in base.items():
        for b in boxes:
            if b.slot not in slots or (layer == 1 and b.slot == "legs") or (layer == 2 and b.slot != "legs"):
                continue
            if layer == 2 and b.name in ("head", "hat", "arm"):
                continue
            if layer == 1 and b.name == "leg" and "feet" not in slots:
                continue
            if layer == 1 and b.name in ("body", "arm") and "chest" not in slots:
                continue
            off = (0, 0) if layer == 1 else (128, 0)
            on = [b.part] + ([rig.MIRROR_OF[b.part]] if b.part in rig.MIRROR_OF else [])
            for p in on:
                mirrored = p != b.part
                local = rig.Box(b.name, p, b.origin, short(b, b.part), b.uv, b.inflate, slot=b.slot, layer=b.layer)
                if mirrored:
                    quads += rig.box_quads(local, moved(p, (mirror_pivot(rig.PART_PIVOT[b.part]), mirror_rot((pose or {}).get(p, (0, 0, 0)))), True),
                                           mirror=True, atlas_off=off)
                else:
                    quads += rig.box_quads(local, moved(p, rig.part_pose(p, pose)), atlas_off=off)
    for b in load_geo(name, parts):
        if b.slot not in slots or below(b):
            continue
        off = (0, 0) if b.layer == 1 else (128, 0)
        if b.mirror:
            # a mirrored twin on the head or body: the game flips its texture; draw its original mirrored instead
            w = b.size[0]
            orig = rig.Box(b.name, b.part, (-b.origin[0] - w, b.origin[1], b.origin[2]), b.size, b.uv, b.inflate,
                           None if b.pivot is None else (-b.pivot[0], b.pivot[1], b.pivot[2]),
                           (b.rotation[0], -b.rotation[1], -b.rotation[2]), slot=b.slot, layer=b.layer)
            quads += rig.box_quads(orig, rig.part_pose(b.part, pose), mirror=True, atlas_off=off)
            continue
        quads += rig.box_quads(b, moved(b.part, rig.part_pose(b.part, pose)), atlas_off=off)
        if b.part in rig.MIRROR_OF:
            twin = rig.MIRROR_OF[b.part]
            quads += rig.box_quads(b, moved(twin, (mirror_pivot(rig.PART_PIVOT[b.part]), mirror_rot((pose or {}).get(twin, (0, 0, 0)))), True),
                                   mirror=True, atlas_off=off)
    # to the preview's space: y up with the feet at 0
    out = []
    for P, rect, n in quads:
        Q = P.copy()
        Q[:, 1] = 24 - Q[:, 1]
        nn = np.array(n, float)
        nn[1] = -nn[1]
        out.append((Q, rect, nn))
    return out


def mirror_pivot(p):
    # the right-hand part's pivot; box_quads mirrors the result in x, which puts it on the left
    return p


def mirror_rot(r):
    # the left part's pose seen through the mirror: x rotation stays, y and z flip
    return (r[0], -r[1], -r[2])


def atlas(name, tex=None):
    l1 = os.path.join(ARMOR_TEX, name + "_layer_1.png")
    l2 = os.path.join(ARMOR_TEX, name + "_layer_2.png")
    if tex is not None:
        a1, a2 = tex
    else:
        a1 = np.asarray(Image.open(l1).convert("RGBA")) if os.path.exists(l1) else np.zeros((128, 128, 4), np.uint8)
        a2 = np.asarray(Image.open(l2).convert("RGBA")) if os.path.exists(l2) else np.zeros((128, 128, 4), np.uint8)
    H = max(128, a1.shape[0], a2.shape[0])
    A = np.zeros((H, 384, 4), np.uint8)
    for k, a in enumerate((a1, a2)):
        if a.shape[0] == 64:   # an old 128x64 texture: model UVs are 64x32 there
            big = np.zeros((128, 128, 4), np.uint8)
            big[:64] = a
            a = big
        A[:a.shape[0], k * 128:(k + 1) * 128] = a
    skin = np.asarray(Image.fromarray(mannequin_skin()).resize((128, 128), Image.NEAREST))
    A[:128, 256:384] = skin
    return A


VIEWS = {
    "front": dict(az=0, el=6), "fl": dict(az=-36, el=12), "fr": dict(az=36, el=12), "left": dict(az=-90, el=6),
    "right": dict(az=90, el=6), "back": dict(az=180, el=8), "bl": dict(az=-150, el=14), "top": dict(az=-25, el=50),
    "low": dict(az=-25, el=-12),
}


def render_views(name, views=("fl", "front", "back"), size=(300, 420), pose=None, slots=("head", "chest", "legs", "feet"),
                 dark=False, tex=None, skin=True, zoom=1.0, target=(0, 17, 0), parts=None):
    quads = scene_quads(name, slots, pose, skin=skin, parts=parts)
    A = atlas(name, tex)
    glow = np.zeros_like(A)
    tiles = []
    for v in views:
        cam = dict(VIEWS[v])
        tiles.append(pm.render(quads, A, glow, size, target=target, dist=58 / zoom, fov=38, dark=dark, **cam))
    return tiles


def sheet(name, views=("fl", "front", "fr", "back", "bl"), walk=False, out=None, size=(300, 420), dark=False, zoom=1.0):
    tiles = render_views(name, views, size, WALK if walk else None, dark=dark, zoom=zoom)
    h, w = tiles[0].shape[:2]
    canvas = np.zeros((h, w * len(tiles), 3), np.uint8)
    for i, t in enumerate(tiles):
        canvas[:, i * w:(i + 1) * w] = t
    out = out or os.path.join(OUT_DIR, f"armor_{name}{'_walk' if walk else ''}.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(canvas).save(out)
    return out


def lineup(names, views=("fl", "bl"), size=(220, 360), out=None, zoom=0.82):
    """Several sets side by side, one row per view, from the generated files."""
    rows = []
    for v in views:
        tiles = [render_views(n, (v,), size, zoom=zoom, target=(0, 15.5, 0))[0] for n in names]
        rows.append(np.concatenate(tiles, axis=1))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(np.concatenate(rows, axis=0)).save(out)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("name", nargs="?")
    ap.add_argument("--views", default="fl,front,fr,back,bl")
    ap.add_argument("--walk", action="store_true")
    ap.add_argument("--dark", action="store_true")
    ap.add_argument("--zoom", type=float, default=1.0)
    ap.add_argument("--lineup", metavar="CLASS", help="every set of a class (tools/armor_designs.py CLASS)")
    ap.add_argument("--out")
    a = ap.parse_args()
    if a.lineup:
        import armor_designs
        names = armor_designs.CLASS[a.lineup]
        print(lineup(names, out=a.out or os.path.join(OUT_DIR, f"lineup_{a.lineup}.png")))
        return
    if not a.name:
        ap.error("name a set, or --lineup <class>")
    print(sheet(a.name, tuple(a.views.split(",")), a.walk, a.out, dark=a.dark, zoom=a.zoom))


if __name__ == "__main__":
    main()
