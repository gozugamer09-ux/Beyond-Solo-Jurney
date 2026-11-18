#!/usr/bin/env python3
"""
Offline preview of a held weapon model (tools/weapon_relief.py meshes): a small numpy rasteriser that lights quads
the way the game's entity shader does (two fixed lights and ambient, per-vertex normals, cut-out alpha, see-through
glow on top), so shapes, sizes and holds can be judged in a second instead of starting the game. The game itself
has the last word.

    python3 tools/weapon_preview.py knight_sword starfall_longbow_pulling_4:1.0
                                        # build/preview/weapons.png: a row per weapon, three studio views and the
                                        # first-person view (":x" after a bow's pull frame: drawn that far, 0..1;
                                        # "@L" after a name: in the left hand)

Views:
- studio: the model alone from the front, three quarters and nearly edge-on
- first person: through the game's chain for a held item (ItemInHandRenderer + the model's first-person
  transform, 70 degree view, 16:9)
Needs: numpy, pillow.
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
OUT_DIR = os.path.join(ROOT, "build", "preview")

L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def rot_x(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def rot_xyz(rx, ry, rz):
    """An item transform's rotation (Quaternionf.rotationXYZ)."""
    return rot_x(rx) @ rot_y(ry) @ rot_z(rz)


class Layer:
    """Quads ready to draw: pos (n, 4, 3) in camera space (the camera at the origin looking down -z, y up), nrm
    (n, 4, 3) in world space (where the lights are), uv (n, 4, 2), a texture (h, w, 4) and how it draws:
    "cutout" (lit, alpha tested), "glow" (cutout, full bright) or "translucent" (lit, blended over the rest)."""

    def __init__(self, pos, nrm, uv, tex, mode="cutout", cull=True):
        self.pos, self.nrm, self.uv = np.asarray(pos, float), np.asarray(nrm, float), np.asarray(uv, float)
        self.tex, self.mode, self.cull = tex, mode, cull


def _fragments(layer, W, H, f, near=0.05):
    pos, nrm, uv, tex = layer.pos, layer.nrm, layer.uv, layer.tex
    th, tw = tex.shape[:2]
    out = []
    tri_idx = np.array([[0, 1, 2], [0, 2, 3]])
    P = pos[:, tri_idx].reshape(-1, 3, 3)
    Nn = nrm[:, tri_idx].reshape(-1, 3, 3)
    T = uv[:, tri_idx].reshape(-1, 3, 2)
    z = -P[..., 2]
    ok = (z > near).all(axis=1)
    P, Nn, T, z = P[ok], Nn[ok], T[ok], z[ok]
    if len(P) == 0:
        return None
    sx = W / 2 + f * P[..., 0] / z
    sy = H / 2 - f * P[..., 1] / z
    area = (sx[:, 1] - sx[:, 0]) * (sy[:, 2] - sy[:, 0]) - (sx[:, 2] - sx[:, 0]) * (sy[:, 1] - sy[:, 0])
    keep = np.abs(area) > 1e-9
    if layer.cull:
        keep &= area < 0                                   # counter-clockwise with y up = front
    x0 = np.clip(np.floor(sx.min(axis=1)).astype(int), 0, W - 1)
    x1 = np.clip(np.ceil(sx.max(axis=1)).astype(int), 0, W - 1)
    y0 = np.clip(np.floor(sy.min(axis=1)).astype(int), 0, H - 1)
    y1 = np.clip(np.ceil(sy.max(axis=1)).astype(int), 0, H - 1)
    keep &= (sx.max(axis=1) >= 0) & (sx.min(axis=1) < W) & (sy.max(axis=1) >= 0) & (sy.min(axis=1) < H)
    idx = np.nonzero(keep)[0]
    if len(idx) == 0:
        return None
    bw, bh = (x1 - x0 + 1)[idx], (y1 - y0 + 1)[idx]
    bucket_w = np.maximum(1, 2 ** np.ceil(np.log2(bw))).astype(int)
    bucket_h = np.maximum(1, 2 ** np.ceil(np.log2(bh))).astype(int)
    for gw in np.unique(bucket_w):
        for gh in np.unique(bucket_h[bucket_w == gw]):
            sel = idx[(bucket_w == gw) & (bucket_h == gh)]
            step = max(1, int(2_000_000 // (gw * gh)))
            for s in range(0, len(sel), step):
                t = sel[s:s + step]
                ox = np.arange(gw)[None, None, :]
                oy = np.arange(gh)[None, :, None]
                ix = x0[t][:, None, None] + ox + np.zeros((1, gh, 1), int)
                iy = y0[t][:, None, None] + oy + np.zeros((1, 1, gw), int)
                px, py = ix + 0.5, iy + 0.5
                ax, ay = sx[t, 0][:, None, None], sy[t, 0][:, None, None]
                bx, by = sx[t, 1][:, None, None], sy[t, 1][:, None, None]
                cx, cy = sx[t, 2][:, None, None], sy[t, 2][:, None, None]
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                b0 = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / den
                b1 = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / den
                b2 = 1 - b0 - b1
                inside = (b0 >= -1e-5) & (b1 >= -1e-5) & (b2 >= -1e-5) & (ix < W) & (iy < H)
                if not inside.any():
                    continue
                za, zb, zc = z[t, 0][:, None, None], z[t, 1][:, None, None], z[t, 2][:, None, None]
                iw = b0 / za + b1 / zb + b2 / zc
                depth = 1.0 / np.where(iw > 0, iw, 1)
                w0, w1, w2 = b0 / za * depth, b1 / zb * depth, b2 / zc * depth
                m = inside
                tt = np.broadcast_to(np.arange(len(t))[:, None, None], m.shape)[m]
                w0, w1, w2 = w0[m], w1[m], w2[m]
                tu = w0 * T[t][tt, 0, 0] + w1 * T[t][tt, 1, 0] + w2 * T[t][tt, 2, 0]
                tv = w0 * T[t][tt, 0, 1] + w1 * T[t][tt, 1, 1] + w2 * T[t][tt, 2, 1]
                ui = np.clip(np.floor(tu * tw).astype(int), 0, tw - 1)
                vi = np.clip(np.floor(tv * th).astype(int), 0, th - 1)
                texel = tex[vi, ui]
                nn = w0[:, None] * Nn[t][tt, 0] + w1[:, None] * Nn[t][tt, 1] + w2[:, None] * Nn[t][tt, 2]
                nn /= np.maximum(1e-9, np.linalg.norm(nn, axis=1, keepdims=True))
                lit = np.minimum(1.0, (np.maximum(0, nn @ L0) + np.maximum(0, nn @ L1)) * 0.6 + 0.4)
                out.append((iy[m] * W + ix[m], depth[m], texel, lit))
    if not out:
        return None
    return tuple(np.concatenate([o[i] for o in out]) for i in range(4))


def draw_layers(layers, size=(640, 360), fov=70.0, bg=None, brightness=1.0):
    """The picture (H, W, 3 uint8). `bg`: None for a sky and floor gradient, or an (H, W, 3) array."""
    W, H = size
    f = (H / 2) / math.tan(math.radians(fov) / 2)
    if bg is None:
        t = np.linspace(0, 1, H)[:, None, None]
        img = np.broadcast_to(np.array([150, 176, 214]) * (1 - 0.3 * t) + np.array([40, 44, 52]) * 0.3 * t, (H, W, 3)).copy()
    else:
        img = np.asarray(bg, float).copy()
    zbuf = np.full(H * W, np.inf)
    flat = img.reshape(-1, 3)
    for layer in layers:
        if layer.mode == "translucent":
            continue
        fr = _fragments(layer, W, H, f)
        if fr is None:
            continue
        pix, depth, texel, lit = fr
        ok = texel[:, 3] >= 26
        if not ok.any():
            continue
        pix, depth, texel, lit = pix[ok], depth[ok], texel[ok], lit[ok]
        order = np.lexsort((depth, pix))
        pix, depth, texel, lit = pix[order], depth[order], texel[order], lit[order]
        first = np.r_[True, pix[1:] != pix[:-1]]
        pix, depth, texel, lit = pix[first], depth[first], texel[first], lit[first]
        win = depth < zbuf[pix]
        pix, depth, texel, lit = pix[win], depth[win], texel[win], lit[win]
        k = 1.0 if layer.mode == "glow" else lit[:, None] * brightness
        flat[pix] = texel[:, :3] * k
        zbuf[pix] = depth
    for layer in layers:
        if layer.mode != "translucent":
            continue
        fr = _fragments(layer, W, H, f)
        if fr is None:
            continue
        pix, depth, texel, lit = fr
        ok = (texel[:, 3] > 0) & (depth < zbuf[pix] - 1e-4)
        if not ok.any():
            continue
        pix, depth, texel, lit = pix[ok], depth[ok], texel[ok], lit[ok]
        order = np.lexsort((depth, pix))
        pix, texel, lit = pix[order], texel[order], lit[order]
        first = np.r_[True, pix[1:] != pix[:-1]]
        pix, texel, lit = pix[first], texel[first], lit[first]
        a = texel[:, 3:4] / 255.0
        flat[pix] = flat[pix] * (1 - a) + texel[:, :3] * lit[:, None] * brightness * a
    return np.clip(img, 0, 255).astype(np.uint8)


# ------------------------------------------------------------------------------------------------ scenes

class Model:
    """A weapon model as the game gets it: mesh parts with their textures and draw modes, in model units."""

    def __init__(self):
        self.parts = []                                    # (pos, nrm, uv, tex, mode)

    def add(self, mesh, textures, modes):
        pos, nrm, uv, mat = mesh.arrays()
        for i, name in enumerate(mesh.materials):
            sel = mat == i
            if sel.any():
                self.parts.append((pos[sel], nrm[sel], uv[sel], textures[name], modes.get(name, "cutout")))
        return self

    def layers(self, M, t, world_rot=None):
        """Model units -> camera space by p -> M p + t. Normals go on to world space by `world_rot` (the camera's
        own turn); without it camera space is world space."""
        Rn = np.linalg.inv(M).T
        out = []
        for pos, nrm, uv, tex, mode in self.parts:
            p = pos @ M.T + t
            n = nrm @ Rn.T
            n /= np.maximum(1e-9, np.linalg.norm(n, axis=2, keepdims=True))
            if world_rot is not None:
                n = n @ world_rot.T
            out.append(Layer(p, n, uv, tex, mode))
        return out


def studio(model, az=35.0, el=12.0, dist=30.0, size=(360, 360), fov=38.0, centre=(8, 8, 8), spin=0.0, upright=True):
    """The model alone. `upright`: turn the sprite so the weapon stands with its tip up (sprites lie on the diagonal)."""
    M = np.eye(3)
    if upright:
        M = rot_z(45.0)
    M = rot_y(spin) @ M
    t = -(M @ np.array(centre, float))
    # camera on a sphere around the origin, looking at it
    a, e = math.radians(az), math.radians(el)
    eye = dist * np.array([math.sin(a) * math.cos(e), math.sin(e), math.cos(a) * math.cos(e)])
    fwd = -eye / np.linalg.norm(eye)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    V = np.stack([right, up, -fwd])                       # world -> camera
    layers = []
    for layer in model.layers(M, t):
        p = (layer.pos - eye) @ V.T
        layers.append(Layer(p, layer.nrm, layer.uv, layer.tex, layer.mode))
    return draw_layers(layers, size, fov)


ARM = np.array([0.56, -0.52, -0.72])                       # ItemInHandRenderer.applyItemArmTransform, right hand


def first_person(model, display, size=(640, 360), yaw=0.0, pitch=0.0, left=False, bg=None, equip=0.0, draw=None):
    """First person through the game's chain: the arm offset, then the model's first-person transform about the
    middle of the model. yaw/pitch: where the player looks (the lights stay with the world). draw: how far a bow
    is drawn (0..1) when it is being used: the game moves and turns the hand first (ItemInHandRenderer)."""
    tr = display["firstperson_lefthand" if left else "firstperson_righthand"]
    rx, ry, rz = tr.get("rotation", [0, 0, 0])
    t = np.array(tr.get("translation", [0, 0, 0]), float) / 16.0
    s = tr.get("scale", [1, 1, 1])
    s = s if isinstance(s, (list, tuple)) else [s] * 3
    side = -1 if left else 1
    if left:
        ry, rz, t = -ry, -rz, t * np.array([-1, 1, 1])
    M = rot_xyz(rx, ry, rz) @ np.diag(s) / 16.0
    off = t - M @ np.array([8.0, 8.0, 8.0])
    arm = ARM * np.array([side, 1, 1]) + np.array([0, -0.6 * equip, 0])
    if draw is not None:
        D = rot_xyz(-13.935, side * 35.3, side * -9.785) @ np.diag([1, 1, 1 + 0.2 * draw]) @ rot_y(side * -45.0)
        arm = arm + np.array([side * -0.2785682, 0.18344387, 0.15731531 + 0.04 * draw])
        M, off = D @ M, D @ off
    cam = rot_y(-yaw) @ rot_x(-pitch)                      # camera space -> world space
    return draw_layers(model.layers(M, arm + off, world_rot=cam), size, 70.0, bg)


def tile(images, cols=None, pad=2, bg=(24, 24, 30)):
    cols = cols or len(images)
    rows = (len(images) + cols - 1) // cols
    h = max(i.shape[0] for i in images)
    w = max(i.shape[1] for i in images)
    sheet = np.zeros((rows * (h + pad), cols * (w + pad), 3), np.uint8)
    sheet[:] = bg
    for k, im in enumerate(images):
        y, x = (k // cols) * (h + pad), (k % cols) * (w + pad)
        sheet[y:y + im.shape[0], x:x + im.shape[1]] = im
    return sheet


def save(img, name):
    os.makedirs(OUT_DIR, exist_ok=True)
    path = os.path.join(OUT_DIR, name)
    Image.fromarray(img).save(path)
    return path


def weapon_model(name):
    """The held model of a weapon as tools/gen_weapon_models.py builds it."""
    import gen_weapon_models as gm
    import weapon_relief
    a, mesh, side, glow = gm.held_parts(name)
    model = Model()
    model.add(mesh, {"face": a, "side": side}, {})
    if glow is True:
        model.add(weapon_relief.flat_plane(a.shape[0]), {"glow": a}, {"glow": "translucent"})
    elif glow:
        model.add(weapon_relief.flat_runs(glow, a.shape[0]), {"glow": a}, {"glow": "translucent"})
    return model


def main(argv):
    import gen_weapon_models as gm
    rows = []
    for arg in argv:
        left = arg.endswith("@L")
        name, _, drawn = (arg[:-2] if left else arg).partition(":")
        model = weapon_model(name)
        views = [studio(model, az=az, el=el, dist=34, size=(360, 360)) for az, el in ((0, 0), (35, 12), (78, 6))]
        sky = np.zeros((360, 640, 3), np.uint8)
        sky[:210], sky[210:] = (150, 185, 250), (70, 70, 76)
        display = gm.displays(name, gm.category(name), "_pulling_" in name)
        views.append(first_person(model, display, size=(640, 360), bg=sky, left=left, draw=float(drawn) if drawn else None))
        rows.append(np.hstack([v[..., :3].astype(np.uint8) for v in views]))
    if rows:
        print(save(np.vstack(rows), "weapons.png"))


if __name__ == "__main__":
    main(sys.argv[1:])
