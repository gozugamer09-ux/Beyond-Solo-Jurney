#!/usr/bin/env python3
"""
Offline preview of a generated entity model: draws a geo.json with its texture and glow mask the way the game does
(GeckoLib's cube faces and UVs, bone and cube rotations, cut-out alpha, the entity shader's two-light shading, the
glow mask drawn full-bright on top), so a skin can be looked at from every side in a second instead of starting the
game. No game, GPU or window needed: a small numpy rasteriser.

    python3 tools/preview_model.py boss_alpha_c                     # a sheet of views -> build/preview/boss_alpha_c.png
    python3 tools/preview_model.py boss_wyrm_s --anim idle --time 0.5
    python3 tools/preview_model.py gloomfang_frost --views front,left --size 520 --dark

The name is <model>_<variant> as in textures/entity. Needs: pillow, numpy.

How the game maps a cube (worked out from GeckoLib's BakedModelFactory and GeoQuad; u runs right and v runs down in
a face's rectangle on the texture, model units, origin o and size s):
    north (z = oz)        x = ox + u sx        y = oy + sy - v sy
    south (z = oz + sz)   x = ox + sx - u sx   y = oy + sy - v sy
    west  (x = ox + sx)   z = oz + u sz        y = oy + sy - v sy      (the model's left side)
    east  (x = ox)        z = oz + sz - u sz   y = oy + sy - v sy      (the model's right side)
    up    (y = oy + sy)   x = ox + u sx        z = oz + sz - v sz
    down  (y = oy)        x = ox + u sx        z = oz + v sz
A rotation [rx, ry, rz] (degrees) is Rz(-rz) Ry(ry) Rx(-rx) in model space; the game then mirrors x to draw.
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "beyondsolo")
FACES = ("north", "south", "east", "west", "up", "down")


# --------------------------------------------------------------------------------------------- transforms

def rot_matrix(r):
    """Model-space rotation for a JSON rotation [rx, ry, rz] in degrees."""
    ax, ay, az = math.radians(-r[0]), math.radians(r[1]), math.radians(-r[2])
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


def affine(rotation=None, pivot=(0, 0, 0), position=(0, 0, 0), scale=(1, 1, 1)):
    """T(position) T(pivot) R S T(-pivot) as a 4x4."""
    m = np.eye(4)
    r = rot_matrix(rotation or (0, 0, 0)) @ np.diag(scale)
    p = np.asarray(pivot, float)
    m[:3, :3] = r
    m[:3, 3] = p - r @ p + np.asarray(position, float)
    return m


def face_corners(origin, size, face):
    """The four corners of a face in model space, in texture order (u, v) = (0,0) (1,0) (1,1) (0,1)."""
    ox, oy, oz = origin
    sx, sy, sz = size
    out = []
    for u, v in ((0, 0), (1, 0), (1, 1), (0, 1)):
        if face == "north":
            p = (ox + u * sx, oy + sy - v * sy, oz)
        elif face == "south":
            p = (ox + sx - u * sx, oy + sy - v * sy, oz + sz)
        elif face == "west":
            p = (ox + sx, oy + sy - v * sy, oz + u * sz)
        elif face == "east":
            p = (ox, oy + sy - v * sy, oz + sz - u * sz)
        elif face == "up":
            p = (ox + u * sx, oy + sy, oz + sz - v * sz)
        else:
            p = (ox + u * sx, oy, oz + v * sz)
        out.append(p)
    return np.array(out, float)


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (1, 0, 0), "east": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def sample_track(track, t):
    """A keyframe dict {"0.00": [x, y, z], ...} (or one vector) at time t, linear between keys."""
    if isinstance(track, list):
        return [float(v) for v in track]
    keys = sorted((float(k), v) for k, v in track.items())
    if t <= keys[0][0]:
        return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            k = (t - t0) / max(1e-9, t1 - t0)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return keys[-1][1]


def load_pose(model, anim, t):
    if not anim:
        return {}
    path = os.path.join(ASSETS, "animations", "entity", f"{model}.animation.json")
    data = json.load(open(path))["animations"]
    a = data.get(f"animation.beyondsolo.{model}.{anim}")
    if a is None:
        raise SystemExit(f"no animation {anim} in {path}: {sorted(k.rsplit('.', 1)[-1] for k in data)}")
    return {bone: {ch: sample_track(tr, t) for ch, tr in chans.items()} for bone, chans in a["bones"].items()}


def build_quads(bones, pose):
    """All faces as (corners 4x3 in model space, uv rect, normal)."""
    by_name = {b["name"]: b for b in bones}
    cache = {}

    def matrix(name):
        if name in cache:
            return cache[name]
        b = by_name[name]
        p = pose.get(name, {})
        rest = b.get("rotation", [0, 0, 0])
        add = p.get("rotation", [0, 0, 0])
        m = affine([rest[i] + add[i] for i in range(3)], b["pivot"], p.get("position", (0, 0, 0)), p.get("scale", (1, 1, 1)))
        if b.get("parent"):
            m = matrix(b["parent"]) @ m
        cache[name] = m
        return m

    quads = []
    for b in bones:
        m = matrix(b["name"])
        for c in b.get("cubes", []):
            cm = m @ affine(c.get("rotation"), c.get("pivot", (0, 0, 0))) if c.get("rotation") else m
            inf = c.get("inflate", 0) or 0
            origin = [v - inf for v in c["origin"]]
            size = [v + 2 * inf for v in c["size"]]
            for f in FACES:
                uv = c["uv"].get(f)
                if uv is None:
                    continue
                pts = face_corners(origin, size, f)
                pts = (cm[:3, :3] @ pts.T).T + cm[:3, 3]
                n = cm[:3, :3] @ np.array(NORMALS[f], float)
                n /= max(1e-9, np.linalg.norm(n))
                quads.append((pts, (uv["uv"][0], uv["uv"][1], uv["uv_size"][0], uv["uv_size"][1]), n))
    return quads


# --------------------------------------------------------------------------------------------- drawing

L0 = np.array([0.2, 1.0, -0.7]) / np.linalg.norm([0.2, 1.0, -0.7])
L1 = np.array([-0.2, 1.0, 0.7]) / np.linalg.norm([-0.2, 1.0, 0.7])


def shade(n):
    """The entity shader's light for a world normal."""
    return min(1.0, (max(0.0, float(L0 @ n)) + max(0.0, float(L1 @ n))) * 0.6 + 0.4)


def render(quads, tex, glow, size=(640, 520), az=35, el=14, target=(0, 16, 0), dist=90, fov=38, dark=False, yaw=0.0):
    """One picture. az: where the camera stands around the model (0 = in front, positive = towards the model's
    right); the result is what the game would show (x mirrored as GeckoLib does)."""
    W, H = size
    th, tw = tex.shape[:2]
    emissive = glow[:, :, 3] > 0
    base = tex[:, :, :3].astype(float)
    base[emissive] = glow[:, :, :3][emissive]
    alpha = np.where(emissive, 255, tex[:, :, 3])

    # the game's space: x mirrored; then the entity's own turn
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    turn = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])

    def to_world(p):
        return (turn @ (p * np.array([-1.0, 1.0, 1.0])).T).T

    a, e = math.radians(az), math.radians(el)
    tgt = np.array([-target[0], target[1], target[2]], float)
    eye = tgt + dist * np.array([math.sin(a) * math.cos(e), math.sin(e), -math.cos(a) * math.cos(e)])
    fwd = tgt - eye
    fwd /= np.linalg.norm(fwd)
    right = np.cross(fwd, [0, 1, 0])
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    f = (H / 2) / math.tan(math.radians(fov) / 2)

    img = np.zeros((H, W, 3))
    zbuf = np.full((H, W), np.inf)
    lit = 0.34 if dark else 1.0

    # backdrop: sky gradient and a floor at y = 0
    ys, xs = np.mgrid[0:H, 0:W]
    dx, dy = (xs + 0.5 - W / 2) / f, -(ys + 0.5 - H / 2) / f
    ray = fwd[None, None, :] + dx[..., None] * right[None, None, :] + dy[..., None] * up[None, None, :]
    sky_t = np.clip(ys / H, 0, 1)[..., None]
    sky = (np.array([16, 18, 26]) if dark else np.array([150, 176, 214])) * (1 - 0.35 * sky_t)
    img[:] = sky
    hit = ray[..., 1] < -1e-6
    tt = np.where(hit, -eye[1] / np.where(hit, ray[..., 1], -1), 0)
    fx, fz = eye[0] + tt * ray[..., 0], eye[2] + tt * ray[..., 2]
    tile = ((np.floor(fx / 16) + np.floor(fz / 16)) % 2)
    line = (np.abs(fx / 16 - np.round(fx / 16)) < 0.03) | (np.abs(fz / 16 - np.round(fz / 16)) < 0.03)
    floor = np.where(line, 38, 62 + 10 * tile)[..., None] * np.array([1.0, 1.0, 1.06])
    fade = np.clip(1 - tt / 900, 0, 1)[..., None]
    img[hit] = (floor * lit * fade + sky * (1 - fade))[hit]

    for pts, (u0, v0, uw, vh), n in quads:
        wp = to_world(pts)
        wn = turn @ (n * np.array([-1.0, 1.0, 1.0]))
        rel = wp - eye
        cz = rel @ fwd
        if (cz < 1.0).any():
            continue
        sxp = W / 2 + f * (rel @ right) / cz
        syp = H / 2 - f * (rel @ up) / cz
        k = shade(wn) * lit
        uvs = np.array([(u0, v0), (u0 + uw, v0), (u0 + uw, v0 + vh), (u0, v0 + vh)], float)
        for tri in ((0, 1, 2), (0, 2, 3)):
            x, y, z, uv = sxp[list(tri)], syp[list(tri)], cz[list(tri)], uvs[list(tri)]
            x0, x1 = max(0, int(math.floor(x.min()))), min(W - 1, int(math.ceil(x.max())))
            y0, y1 = max(0, int(math.floor(y.min()))), min(H - 1, int(math.ceil(y.max())))
            if x0 > x1 or y0 > y1:
                continue
            gy, gx = np.mgrid[y0:y1 + 1, x0:x1 + 1]
            px, py = gx + 0.5, gy + 0.5
            den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if abs(den) < 1e-9:
                continue
            b0 = ((y[1] - y[2]) * (px - x[2]) + (x[2] - x[1]) * (py - y[2])) / den
            b1 = ((y[2] - y[0]) * (px - x[2]) + (x[0] - x[2]) * (py - y[2])) / den
            b2 = 1 - b0 - b1
            inside = (b0 >= -1e-6) & (b1 >= -1e-6) & (b2 >= -1e-6)
            if not inside.any():
                continue
            iw = b0 / z[0] + b1 / z[1] + b2 / z[2]
            depth = 1.0 / np.where(iw > 0, iw, 1)
            u = (b0 * uv[0, 0] / z[0] + b1 * uv[1, 0] / z[1] + b2 * uv[2, 0] / z[2]) * depth
            v = (b0 * uv[0, 1] / z[0] + b1 * uv[1, 1] / z[1] + b2 * uv[2, 1] / z[2]) * depth
            ui = np.clip(np.floor(u).astype(int), int(math.floor(min(u0, u0 + uw))), max(int(math.ceil(max(u0, u0 + uw))) - 1, int(math.floor(min(u0, u0 + uw)))))
            vi = np.clip(np.floor(v).astype(int), int(math.floor(min(v0, v0 + vh))), max(int(math.ceil(max(v0, v0 + vh))) - 1, int(math.floor(min(v0, v0 + vh)))))
            ui, vi = np.clip(ui, 0, tw - 1), np.clip(vi, 0, th - 1)
            ok = inside & (alpha[vi, ui] >= 26) & (depth < zbuf[y0:y1 + 1, x0:x1 + 1] + 1e-3)
            if not ok.any():
                continue
            col = base[vi, ui]
            em = emissive[vi, ui]
            out = np.where(em[..., None], col, col * k)
            sub = img[y0:y1 + 1, x0:x1 + 1]
            sub[ok] = out[ok]
            zsub = zbuf[y0:y1 + 1, x0:x1 + 1]
            zsub[ok] = depth[ok]
    return np.clip(img, 0, 255).astype(np.uint8)


VIEWS = {
    "front": dict(az=0, el=8), "left": dict(az=-90, el=6), "right": dict(az=90, el=6), "back": dict(az=180, el=8),
    "fl": dict(az=-38, el=14), "fr": dict(az=38, el=14), "bl": dict(az=-142, el=16), "br": dict(az=142, el=16),
    "top": dict(az=-20, el=62), "low": dict(az=-30, el=-8), "face": dict(az=-22, el=6),
}


def load_model(name):
    """<model>_<variant> -> (model id, bones, texture, glow mask)."""
    geo_dir = os.path.join(ASSETS, "geo", "entity")
    model = max((f[:-9] for f in os.listdir(geo_dir) if f.endswith(".geo.json") and name.startswith(f[:-9])), key=len, default=None)
    if model is None:
        raise SystemExit(f"no model for {name}")
    geo = json.load(open(os.path.join(geo_dir, f"{model}.geo.json")))["minecraft:geometry"][0]
    tex = np.asarray(Image.open(os.path.join(ASSETS, "textures", "entity", f"{name}.png")).convert("RGBA"))
    gpath = os.path.join(ASSETS, "textures", "entity", f"{name}_glowmask.png")
    glow = np.asarray(Image.open(gpath).convert("RGBA")) if os.path.exists(gpath) else np.zeros_like(tex)
    return model, geo["bones"], tex, glow


def bounds(quads):
    pts = np.concatenate([q[0] for q in quads])
    return pts.min(axis=0), pts.max(axis=0)


def sheet(name, views=("fl", "left", "front", "br", "top", "face"), size=520, anim=None, time=0.0, dark=False, zoom=1.0, cols=3,
          target=None, out=None):
    model, bones, tex, glow = load_model(name)
    quads = build_quads(bones, load_pose(model, anim, time))
    lo, hi = bounds(quads)
    centre = (lo + hi) / 2
    radius = float(np.linalg.norm(hi - lo)) / 2
    tiles = []
    for v in views:
        cam = dict(VIEWS[v])
        tgt = list(target) if target else [centre[0], centre[1], centre[2]]
        d = radius / math.tan(math.radians(38) / 2) * 1.05 / zoom
        if v == "face" and not target:
            # close on the head: the front third of the model
            tgt = [centre[0], hi[1] - (hi[1] - lo[1]) * 0.3, lo[2] + (hi[2] - lo[2]) * 0.18]
            d *= 0.45
        tiles.append(render(quads, tex, glow, (int(size * 1.25), size), target=tgt, dist=d, dark=dark, **cam))
    rows = (len(tiles) + cols - 1) // cols
    h, w = tiles[0].shape[:2]
    canvas = np.zeros((rows * h, cols * w, 3), np.uint8)
    for i, t in enumerate(tiles):
        canvas[(i // cols) * h:(i // cols + 1) * h, (i % cols) * w:(i % cols + 1) * w] = t
    out = out or os.path.join(ROOT, "build", "preview", f"{name}{'_' + anim if anim else ''}{'_dark' if dark else ''}.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    Image.fromarray(canvas).save(out)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("name", help="<model>_<variant>, e.g. boss_alpha_c")
    ap.add_argument("--views", default="fl,left,front,br,top,face")
    ap.add_argument("--size", type=int, default=520)
    ap.add_argument("--anim")
    ap.add_argument("--time", type=float, default=0.0)
    ap.add_argument("--dark", action="store_true", help="as in a dark cave: only the glow stays bright")
    ap.add_argument("--zoom", type=float, default=1.0)
    ap.add_argument("--cols", type=int, default=3)
    ap.add_argument("--target", help="x,y,z in model units to look at instead of the model's middle (with --zoom, for close-ups)")
    ap.add_argument("--out")
    a = ap.parse_args()
    target = [float(v) for v in a.target.split(",")] if a.target else None
    print(sheet(a.name, tuple(a.views.split(",")), a.size, a.anim, a.time, a.dark, a.zoom, a.cols, target=target, out=a.out))


if __name__ == "__main__":
    main()
