"""
Item icons for the armor sets, made from the worn armor itself: each piece is drawn from its own model and textures
(tools/armor_worn.py), parts and all, from a three-quarter view, so the icon always matches what the player wears.

Drawn four times too big with an orthographic camera and the texture's own pixels, then brought down to 64x64 by
keeping each block's most common colour (no blur, no new colours), given a dark selective outline and the item
grade's aura and sparkles (tools/hd_paint.py, the same as every other item).

    python3 tools/armor_icons.py forgehand        # -> build/preview/icons_forgehand.png (the four icons, big)

Needs: numpy, pillow.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import armor_kit as ak  # noqa: E402
import armor_preview as ap  # noqa: E402
import hd_paint as hp  # noqa: E402

SIZE = 64
SS = 4

# camera per piece: yaw (degrees, positive turns the piece's front towards the viewer's left), pitch (looking down),
# a pose for the limbs, and how much of the icon the piece fills (measured on what is drawn, not on the boxes).
# The chestplate keeps only the shoulders and upper arms, like a chestplate lying on a table; the boots are seen from
# the side, toes to the left, the second one a step behind and higher, as boots are drawn on item icons.
VIEWS = {
    "head": dict(yaw=-28, pitch=16, pose=None, fill=0.88),
    "chest": dict(yaw=-20, pitch=10, pose={"right_arm": (0, 0, 14), "left_arm": (0, 0, -14)}, fill=0.92,
                  cut={"right_arm": 4.0}),
    "legs": dict(yaw=-18, pitch=10, pose={"right_leg": (0, 0, 7), "left_leg": (0, 0, -7)}, fill=0.9),
    "feet": dict(yaw=-72, pitch=8, pose=None, shift={"right_leg": (-1.9, -2.4, -1.7), "left_leg": (1.9, 0, 1.7)}, fill=0.9),
}
FACE_LIGHT = {"top": 1.0, "front": 0.94, "side": 0.8, "back": 0.72, "bottom": 0.62}


def _face_light(n):
    """Flat light by which way a face points after the camera turn (screen space: x right, y up, z towards us)."""
    x, y, z = n
    if y > 0.6:
        return FACE_LIGHT["top"]
    if y < -0.6:
        return FACE_LIGHT["bottom"]
    if z > 0.3:
        return FACE_LIGHT["front"] if x <= 0.2 else FACE_LIGHT["side"]
    return FACE_LIGHT["side"] if z > -0.3 else FACE_LIGHT["back"]


def render_piece(quads, atlas, yaw, pitch, size=SIZE, ss=SS, fill=0.9):
    """The piece drawn big (size*ss square) with an orthographic camera: RGBA. Drawn twice: once to find where its
    visible pixels are (boxes are partly transparent), then framed on those."""
    W = size * ss
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(-pitch)), math.sin(math.radians(-pitch))
    # preview space: x is the model's left (mirrored as in game), y up, z towards the back; the camera looks from -z
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    R = Rx @ Ry
    flip = np.array([-1.0, 1.0, -1.0])        # screen: x right, y up, z towards the viewer
    pts = [((R @ (P * np.array([-1.0, 1.0, 1.0])).T).T) * flip for P, _, _ in quads]
    allp = np.concatenate(pts)
    lo, hi = allp.min(0), allp.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    scale = W * fill / span
    cx, cyy = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    first = _raster(quads, pts, R, flip, atlas, W, scale, cx, cyy)
    ys, xs = np.nonzero(first[..., 3] > 0)
    if len(xs) == 0:
        return first
    x0, x1 = (xs.min() - W / 2) / scale + cx, (xs.max() + 1 - W / 2) / scale + cx
    y0, y1 = cyy - (ys.max() + 1 - W / 2) / scale, cyy - (ys.min() - W / 2) / scale
    span = max(x1 - x0, y1 - y0)
    return _raster(quads, pts, R, flip, atlas, W, W * fill / span, (x0 + x1) / 2, (y0 + y1) / 2)


def _raster(quads, pts, R, flip, atlas, W, scale, cx, cyy):
    img = np.zeros((W, W, 4), float)
    zbuf = np.full((W, W), -np.inf)
    th, tw = atlas.shape[:2]
    for (P, (u0, v0, uw, vh), n), Q in zip(quads, pts):
        nn = (R @ (np.asarray(n, float) * np.array([-1.0, 1.0, 1.0]))) * flip
        light = _face_light(nn)
        if nn[2] < -0.05:
            light *= 0.5          # an inside face seen through an opening: in shadow
        sx = (Q[:, 0] - cx) * scale + W / 2
        syy = W / 2 - (Q[:, 1] - cyy) * scale
        sz = Q[:, 2]
        uvs = np.array([(u0, v0), (u0 + uw, v0), (u0 + uw, v0 + vh), (u0, v0 + vh)], float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            x, y, z, uv = sx[list(tri)], syy[list(tri)], sz[list(tri)], uvs[list(tri)]
            x0, x1 = max(0, int(math.floor(x.min()))), min(W - 1, int(math.ceil(x.max())))
            y0, y1 = max(0, int(math.floor(y.min()))), min(W - 1, int(math.ceil(y.max())))
            if x0 > x1 or y0 > y1:
                continue
            den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if abs(den) < 1e-9:
                continue
            ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1] + 0.5
            b0 = ((y[1] - y[2]) * (xs - x[2]) + (x[2] - x[1]) * (ys - y[2])) / den
            b1 = ((y[2] - y[0]) * (xs - x[2]) + (x[0] - x[2]) * (ys - y[2])) / den
            b2 = 1 - b0 - b1
            inside = (b0 >= -1e-6) & (b1 >= -1e-6) & (b2 >= -1e-6)
            if not inside.any():
                continue
            depth = b0 * z[0] + b1 * z[1] + b2 * z[2]
            u = b0 * uv[0, 0] + b1 * uv[1, 0] + b2 * uv[2, 0]
            v = b0 * uv[0, 1] + b1 * uv[1, 1] + b2 * uv[2, 1]
            umin, umax = min(u0, u0 + uw), max(u0, u0 + uw)
            vmin, vmax = min(v0, v0 + vh), max(v0, v0 + vh)
            ui = np.clip(np.floor(u).astype(int), int(math.floor(umin)), max(int(math.ceil(umax)) - 1, int(math.floor(umin))))
            vi = np.clip(np.floor(v).astype(int), int(math.floor(vmin)), max(int(math.ceil(vmax)) - 1, int(math.floor(vmin))))
            ui, vi = np.clip(ui, 0, tw - 1), np.clip(vi, 0, th - 1)
            texel = atlas[vi, ui]
            zsub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (texel[..., 3] >= 128) & (depth > zsub - 1e-3)
            if not ok.any():
                continue
            sub = img[y0:y1 + 1, x0:x1 + 1]
            col = texel[..., :3].astype(float) * light
            sub[ok, :3] = col[ok]
            sub[ok, 3] = 255
            zsub[ok] = depth[ok]
    return img


def downsample(big, size=SIZE, ss=SS):
    """Each ss x ss block becomes its most common opaque colour (transparent when mostly empty)."""
    a = np.clip(np.round(big), 0, 255).astype(np.uint8)
    blocks = a.reshape(size, ss, size, ss, 4).transpose(0, 2, 1, 3, 4).reshape(size, size, ss * ss, 4)
    code = (blocks[..., 0].astype(np.uint32) << 16) | (blocks[..., 1].astype(np.uint32) << 8) | blocks[..., 2].astype(np.uint32)
    solid = blocks[..., 3] >= 128
    out = np.zeros((size, size, 4), np.uint8)
    need = solid.sum(-1) >= (ss * ss) * 0.4
    for y, x in zip(*np.nonzero(need)):
        c = code[y, x][solid[y, x]]
        vals, counts = np.unique(c, return_counts=True)
        best = vals[np.argmax(counts)]
        out[y, x, :3] = ((best >> 16) & 255, (best >> 8) & 255, best & 255)
        out[y, x, 3] = 255
    return out


def icon(name, res, slot, grade="common", accent=(255, 255, 255), seed=5):
    """One piece's 64x64 icon from a built set (armor_worn.Worn(...).build())."""
    v = VIEWS[slot]
    quads = ap.scene_quads(name, (slot,), v["pose"], skin=False, parts=res["parts"], shift=v.get("shift"), cut=v.get("cut"))
    atlas = ap.atlas(name, tex=(res[1], res[2]))
    big = render_piece(quads, atlas, v["yaw"], v["pitch"], fill=v["fill"])
    small = downsample(big)
    out = ak.sel_out(small, 0.66)
    alphas, n = hp.GRADE_FX[grade]
    if alphas:
        hp.aura(out, tuple(accent[:3]) + (255,), alphas)
    hp.sparkles(out, n, tuple(accent[:3]) if grade != "divine" else (255, 244, 200), seed)
    return out


def icons(name, res, grade="common", accent=(255, 255, 255)):
    return [icon(name, res, s, grade, accent, seed=11 + k) for k, s in enumerate(("head", "chest", "legs", "feet"))]


if __name__ == "__main__":
    import armor_designs as ad
    import armor_worn as aw
    for nm in sys.argv[1:]:
        r = aw.Worn(ad.D[nm]).build()
        ims = icons(nm, r)
        sheet = np.zeros((SIZE, SIZE * 4, 4), np.uint8)
        for k, im in enumerate(ims):
            sheet[:, k * SIZE:(k + 1) * SIZE] = im
        bg = Image.new("RGBA", (SIZE * 4, SIZE), (40, 38, 48, 255))
        bg.alpha_composite(Image.fromarray(sheet))
        os.makedirs(ap.OUT_DIR, exist_ok=True)
        path = os.path.join(ap.OUT_DIR, "icons_%s.png" % nm)
        bg.resize((SIZE * 4 * 4, SIZE * 4), Image.NEAREST).save(path)
        print(path)
