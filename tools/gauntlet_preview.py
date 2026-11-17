#!/usr/bin/env python3
"""
Offline preview of a worn gauntlet (tools/gen_gauntlets.py): the item model as written (its elements, uv rotations
and element turns, read the way the game's FaceBakery reads them) hung on a mannequin's arms through the same chain
the game uses for a held item (arm pose, ItemInHandLayer, the model's third-person transform, the left hand mirrored
by the item transform itself), next to the player's skin and optionally a chestplate. Quick to iterate on the art;
the game itself has the last word.

    python3 tools/gauntlet_preview.py titans_knuckles [--armor forgehand]   # -> build/preview/gauntlet_<name>.png
Needs: numpy, pillow.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_preview as ap  # noqa: E402
import armor_rig as rig  # noqa: E402
import gen_gauntlets as gg  # noqa: E402
import preview_model as pm  # noqa: E402

DIRS = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0)}
AXES = {"x": (1, 0, 0), "y": (0, 1, 0), "z": (0, 0, 1)}

POSES = {
    # the vanilla "holding an item" arm (-18 degrees) and the idle sway; the other arm hangs
    "idle": {"right_arm": (-18, 0, 4), "left_arm": (0, 0, -4)},
    "walk": {"right_arm": (-40, 0, 4), "left_arm": (28, 0, -4), "right_leg": (26, 0, 0), "left_leg": (-26, 0, 0)},
    # Better Combat's one-handed punch at its strike frame (tick 12): the right fist out, the left up on guard
    "punch": {"right_arm": (math.degrees(-1.972), math.degrees(0.441), math.degrees(-0.018)),
              "left_arm": (math.degrees(-1.593), math.degrees(0.735), math.degrees(0.226))},
}


def _axis_rot(axis, deg):
    k = np.array(AXES[axis], float)
    a = math.radians(deg)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(a) * K + (1 - math.cos(a)) * (K @ K)


def element_quads(model, tw, th):
    """(corners in model pixels ordered by texture corner (u1,v1) (u2,v1) (u2,v2) (u1,v2), rect in texels, normal,
    glow) for every face of every element."""
    out = []
    for el in model["elements"]:
        lo, hi = np.array(el["from"], float), np.array(el["to"], float)
        lim = {"x0": lo[0], "y0": lo[1], "z0": lo[2], "x1": hi[0], "y1": hi[1], "z1": hi[2]}
        R, org = np.eye(3), np.zeros(3)
        if "rotation" in el:
            R = _axis_rot(el["rotation"]["axis"], el["rotation"]["angle"])
            org = np.array(el["rotation"]["origin"], float)
        glow = "neoforge_data" in el
        for f, fj in el["faces"].items():
            corners = [np.array([lim[c[0]], lim[c[1]], lim[c[2]]]) for c in gg.FACE_INFO[f]]
            corners = [org + R @ (c - org) for c in corners]
            u1, v1, u2, v2 = fj["uv"]
            s = fj.get("rotation", 0) // 90
            table = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
            uv = [table[(i + s) % 4] for i in range(4)]
            order = []
            for want in ((u1, v1), (u2, v1), (u2, v2), (u1, v2)):
                order.append(next(i for i in range(4) if uv[i] == want))
            P = np.array([corners[i] for i in order])
            rect = (u1 * tw / 16, v1 * th / 16, (u2 - u1) * tw / 16, (v2 - v1) * th / 16)
            out.append((P, rect, R @ np.array(DIRS[f], float), glow))
    return out


def on_arm(quads, display, side, pose, atlas_x):
    """Model pixels -> the preview's space through the game's chain for a held item on arm `side`."""
    tr = display["thirdperson_righthand" if side == "right" else "thirdperson_lefthand"]
    rx, ry, rz = tr["rotation"]
    t = np.array(tr["translation"], float)
    if side == "left":                     # ItemTransform.apply(leftHand): y/z rotation and x translation negated
        ry, rz, t = -ry, -rz, t * np.array([-1, 1, 1])
    Rd = gg._rot_xyz(rx, ry, rz) * tr["scale"][0]
    van_t = gg.VAN_T * np.array([1 if side == "right" else -1, 1, 1])
    part = side + "_arm"
    piv, r = rig.part_pose(part, pose)
    Rp = rig.rot(*r)
    out = []
    for P, (u0, v0, uw, vh), n, glow in quads:
        q = (gg.VAN_R @ (van_t[:, None] + t[:, None] + Rd @ (P.T - 8.0))).T
        body = (Rp @ q.T).T + np.array(piv)
        body[:, 1] = 24 - body[:, 1]
        nn = Rp @ gg.VAN_R @ Rd @ n
        nn[1] = -nn[1]
        out.append((body, (u0 + atlas_x, v0, uw, vh), nn / np.linalg.norm(nn), glow))
    return out


def scene(name, pose_name="idle", armor=None, both=True):
    fit = "_armored" if armor else ""
    model = json.load(open(os.path.join(gg.MODEL_DIR, name + fit + ".json")))
    tex = np.asarray(Image.open(os.path.join(gg.TEX_DIR, name + fit + ".png")).convert("RGBA"))
    th, tw = tex.shape[:2]
    pose = POSES[pose_name]
    body = ap.scene_quads(armor or "none", slots=("chest",) if armor else (), pose=pose, skin=True)
    A0 = ap.atlas(armor or "none")
    H = max(A0.shape[0], th)
    atlas = np.zeros((H, A0.shape[1] + tw, 4), np.uint8)
    atlas[:A0.shape[0], :A0.shape[1]] = A0
    atlas[:th, A0.shape[1]:] = tex
    glow = np.zeros_like(atlas)
    quads = element_quads(model, tw, th)
    # check the transform: the right hand must put model pixels exactly where gen_gauntlets drew them
    P = quads[0][0]
    q = on_arm([(P, (0, 0, 0, 0), np.array([0, 0, 1.0]), False)], model["display"], "right", {}, 0)[0][0].copy()
    q[:, 1] = 24 - q[:, 1]
    assert np.allclose(q - np.array(rig.PART_PIVOT["right_arm"]), (gg.A @ P.T).T + gg.B, atol=1e-4)
    gq = []
    for side in (("right", "left") if both else ("right",)):
        for Pq, rect, n, g in on_arm(quads, model["display"], side, pose, A0.shape[1]):
            gq.append((Pq, rect, n))
            if g:
                u0, v0, uw, vh = rect
                x0, x1 = sorted((int(round(u0)), int(round(u0 + uw))))
                y0, y1 = sorted((int(round(v0)), int(round(v0 + vh))))
                glow[y0:y1, x0:x1] = atlas[y0:y1, x0:x1]
    return body + gq, atlas, glow


# (pose, view az, el, target, dist, with armor): a row of whole-body views and a row of close-ups of the right arm
WIDE = [
    ("idle", -35, 12, (0, 15, 0), 62, False),
    ("idle", 145, 14, (0, 15, 0), 62, False),
    ("walk", 60, 10, (0, 15, 0), 62, False),
    ("punch", -40, 16, (0, 18, -4), 62, False),
    ("idle", -35, 12, (0, 15, 0), 62, True),
    ("punch", -40, 16, (0, 18, -4), 62, True),
]
CLOSE = [
    ("idle", 60, 8, (-6, 13.5, -1), 26, False),      # the outside and the front
    ("idle", 5, 6, (-6, 13.5, -1), 26, False),       # the front
    ("idle", 150, 8, (-6, 13.5, 0), 26, False),      # the back
    ("idle", 40, -40, (-6, 11, -2), 26, False),      # the striking face from below
    ("idle", 60, 8, (-6, 13.5, -1), 26, True),
    ("idle", -40, 10, (6, 13.5, -1), 26, False),     # the other arm
]


def sheet(name, armor="forgehand", out=None, size=(380, 460), dark=False):
    rows = []
    for shots in (WIDE, CLOSE):
        tiles = []
        for pose, az, el, target, dist, arm in shots:
            quads, atlas, glow = scene(name, pose, armor if arm else None)
            tiles.append(pm.render(quads, atlas, glow, size, az=az, el=el, target=target, dist=dist, fov=38, dark=dark))
        rows.append(np.concatenate(tiles, axis=1))
    img = np.concatenate(rows, axis=0)
    out = out or os.path.join(gg.OUT_DIR, f"gauntlet_{name}{'_dark' if dark else ''}.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(img).save(out)
    return out


if __name__ == "__main__":
    args = sys.argv[1:]
    armor = "forgehand"
    if "--armor" in args:
        armor = args[args.index("--armor") + 1]
        args = [a for a in args if a not in ("--armor", armor)]
    dark = "--dark" in args
    args = [a for a in args if not a.startswith("--")]
    for n in args or gg.names():
        print(sheet(n, armor, dark=dark))
