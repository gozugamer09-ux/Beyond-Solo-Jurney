#!/usr/bin/env python3
"""
Depth for held weapons: a weapon sprite plus a height map become a solid mesh.

A sprite turned into a slab (what item/generated does, and what tools/gen_weapon_models.py used to build) is as
thick everywhere as a sheet of plywood, and every diagonal edge is a staircase of little walls that light up in
stripes. Here each texel has its own half thickness instead (the height map: a blade is a thin wedge with a ridge,
a shaft is as deep as it is wide, a hammer head is a block), the front and back follow those heights, and the
normals are smooth, so a round shaft shades like a pole and a diagonal edge shades evenly.

- `fields(body)`: distance to the outline and local half width for every texel (numpy only, no scipy)
- `generic_heights(body)`: a height map for any sprite: round where it is narrow, a plate with rounded edges where
  it is wide. The texture generators write better ones for the parts they know (see tools/gen_hd_gear.py).
- `Mesh` / `build(...)`: the mesh. Vertices sit on texel corners, so the outline stays exactly the sprite's.
  Front and back quads carry the sprite ("face"), the rim walls carry the side texture ("side").
- `write_obj(mesh, path)`: NeoForge's OBJ loader reads it (models/item/held/<name>.obj + weapon.mtl).

Heights are in texels. Needs: numpy, pillow.
"""
import math
import os

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
HEIGHT_DIR = os.path.join(ROOT, "tools", "weapon_relief")       # <name>.png: half thickness per texel, 1/16 texel
HEIGHT_STEP = 16.0

RIGHT_TURN = {("R", "D"), ("D", "L"), ("L", "U"), ("U", "R")}
OUTWARD = {"R": (0, -1), "D": (1, 0), "L": (0, 1), "U": (-1, 0)}     # pixel coordinates, y down


# ------------------------------------------------------------------------------------------------ height maps

def save_heights(name, h, show=None):
    """`show`: for texels that must not show their own colour on the solid (a bow's painted arrow where it crosses
    the grip: the held bow has a real arrow), the texel (x, y) whose colour they show instead; -1 elsewhere. It is
    kept in the green and blue channels as an offset."""
    os.makedirs(HEIGHT_DIR, exist_ok=True)
    img = np.clip(np.round(h * HEIGHT_STEP), 0, 255).astype(np.uint8)
    path = os.path.join(HEIGHT_DIR, name + ".png")
    if show is None or not (show[..., 0] >= 0).any():
        Image.fromarray(img, "L").save(path, optimize=True)
        return
    S = img.shape[0]
    ys, xs = np.mgrid[0:S, 0:S]
    moved = show[..., 0] >= 0
    rgb = np.zeros((S, S, 3), np.uint8)
    rgb[..., 0] = img
    rgb[..., 1] = np.where(moved, show[..., 0] - xs + 128, 128)
    rgb[..., 2] = np.where(moved, show[..., 1] - ys + 128, 128)
    Image.fromarray(rgb, "RGB").save(path, optimize=True)


def load_heights(name, with_show=False):
    path = os.path.join(HEIGHT_DIR, name + ".png")
    if not os.path.exists(path):
        return (None, None) if with_show else None
    img = Image.open(path)
    if img.mode == "RGB":
        a = np.asarray(img, int)
        h = a[..., 0] / HEIGHT_STEP
        S = h.shape[0]
        ys, xs = np.mgrid[0:S, 0:S]
        show = np.stack([xs + a[..., 1] - 128, ys + a[..., 2] - 128], axis=-1)
    else:
        h = np.asarray(img.convert("L"), float) / HEIGHT_STEP
        show = None
    return (h, show) if with_show else h


def fields(body):
    """(d, R) per texel: d = distance from the texel's middle to the outline, R = the local half width (the radius
    of the widest disc inside the shape that covers the texel). Both in texels, 0 outside the body."""
    S = body.shape[0]
    pad = np.zeros((S + 2, S + 2), bool)
    pad[1:-1, 1:-1] = body
    # outside texels that touch the body: the nearest outside point is always in one of them
    near = np.zeros_like(pad)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            near |= np.roll(np.roll(pad, dy, 0), dx, 1)
    oy, ox = np.nonzero(near & ~pad)
    ox, oy = ox - 1.0, oy - 1.0                                  # texel coordinates (may be -1 or S)
    # the half-step lattice: index (2y+1, 2x+1) is the middle of texel (x, y), even indices are corners
    N = 2 * S + 1
    gy, gx = np.mgrid[0:N, 0:N] / 2.0
    inside = np.zeros((N, N), bool)
    inside[1::2, 1::2] = body
    inside[2:-1:2, 1::2] = body[1:, :] & body[:-1, :]
    inside[1::2, 2:-1:2] = body[:, 1:] & body[:, :-1]
    inside[2:-1:2, 2:-1:2] = body[1:, 1:] & body[:-1, :-1] & body[1:, :-1] & body[:-1, 1:]
    py, px = gy[inside], gx[inside]
    d = np.zeros(len(px))
    if len(ox) == 0:
        d[:] = S
    else:
        for s in range(0, len(px), 2048):
            x, y = px[s:s + 2048, None], py[s:s + 2048, None]
            dx = np.maximum(np.maximum(ox[None, :] - x, x - (ox[None, :] + 1)), 0)
            dy = np.maximum(np.maximum(oy[None, :] - y, y - (oy[None, :] + 1)), 0)
            d[s:s + 2048] = np.sqrt(dx * dx + dy * dy).min(axis=1)
    # local half width: the largest d among the lattice points whose own disc reaches this point
    R = d.copy()
    order = np.argsort(-d)
    qx, qy, qd = px[order], py[order], d[order]
    for s in range(0, len(px), 1024):
        x, y = px[s:s + 1024, None], py[s:s + 1024, None]
        reach = (qx[None, :] - x) ** 2 + (qy[None, :] - y) ** 2 <= (qd[None, :] + 1e-6) ** 2
        first = reach.argmax(axis=1)                              # sorted by d: the first that reaches is the widest
        R[s:s + 1024] = np.maximum(R[s:s + 1024], qd[first])
    dl = np.zeros((N, N))
    Rl = np.zeros((N, N))
    dl[inside], Rl[inside] = d, R
    return dl[1::2, 1::2], Rl[1::2, 1::2]


def round_profile(d, R):
    """Half thickness of a round bar of radius R at distance d inside its edge."""
    d = np.minimum(d, R)
    return np.sqrt(np.maximum(0.0, d * (2 * R - d)))


def generic_heights(body, cap=2.4, cap_max=4.0, fields_=None):
    """Round where the shape is narrow (a shaft is as deep as it is wide), a plate with rounded edges where it is
    wider than `cap`: a little thicker the wider it gets, up to `cap_max`."""
    d, R = fields_ or fields(body)
    limit = np.where(R <= cap, R, np.minimum(cap + (R - cap) * 0.25, cap_max))
    return np.where(body, np.minimum(round_profile(d, R), limit), 0.0)


# ------------------------------------------------------------------------------------------------ designs
#
# The weapon painters (tools/gen_combat_assets.py, tools/gen_arsenal_assets.py) build a weapon from parts along its
# axis: u runs from the pommel to the tip, v across, both in design units (half a texel each way at 64x64, see
# `sc`). A part knows its own half width w(u), so its depth can be exact instead of guessed from the picture.
# `part.relief` names what it is:
#   round  a bar or ball as deep as it is wide (shafts, grips, pommels, collars, spikes)
#   blade  a thin wedge: a honed edge, a ridge down the middle, a groove where the painter draws a fuller
#   bar    a round bar lying across the weapon (cross guards)
#   block  a slab with bevelled edges (hammer heads, knuckle plates)
#   gem    a crystal: two pyramids base to base
#   limb   a bow limb: an oval bar
#   flat   a thin plate (feathers, fins)
# or is a function (u, v, grow) -> half thickness for anything else (axe heads, orbs, mace flanges).
# A part painted wholly on top of another (an inlay, a glowing core) adds no depth of its own.

BLADE_EDGE, BLADE_RATIO, BLADE_MIN, BLADE_CAP = 0.14, 0.28, 0.22, 1.0
FULLER_DEPTH, FULLER_WIDTH = 0.34, 0.26
ROUND_CAP = 3.2
BAR_DEPTH = 0.85
BLOCK_DEPTH, BLOCK_WALL, BLOCK_BEVEL = 0.7, 0.82, 0.7
GEM_DEPTH, GEM_EDGE = 0.8, 0.15
LIMB_DEPTH = 0.55
RISER_DEPTH = 0.62
FLAT_DEPTH = 0.16


def part_kind(p):
    kind = getattr(p, "relief", None)
    if kind:
        return kind
    if getattr(p, "kind", "") == "grip":
        return "round"
    ws = [p.width(p.u0 + (p.u1 - p.u0) * i / 8.0) for i in range(9)]
    if max(ws) - min(ws) < 1e-6:
        return "block" if ws[0] > 3.0 and p.u1 - p.u0 >= 3.0 else "round"
    return "blade" if getattr(p, "fuller", False) else "round"


def part_height(p, u, v, g, kind=None):
    """Half thickness (design units) of a part at (u, v), the part taken g wider all round (the painter's outline),
    or None outside it."""
    kind = kind or part_kind(p)
    if callable(kind):
        return kind(u, v, g)
    if u < p.u0 - g or u > p.u1 + g:
        return None
    uc = min(max(u, p.u0), p.u1)
    w = max(0.0, p.width(uc))
    vv = v - p.bend(uc)
    we = w + g
    if abs(vv) > we:
        return None
    um, a = (p.u0 + p.u1) / 2.0, (p.u1 - p.u0) / 2.0 + g
    if kind == "blade":
        ridge = min(BLADE_CAP, BLADE_RATIO * w + BLADE_MIN)
        h = BLADE_EDGE + (ridge - BLADE_EDGE) * (1 - abs(vv) / we)
        if getattr(p, "fuller", False) and w > 1.4 and u < p.u1 - 3:
            t = abs(vv) / w
            if t < FULLER_WIDTH:
                h -= FULLER_DEPTH * (ridge - BLADE_EDGE) * (1 - (t / FULLER_WIDTH) ** 2)
        return h
    if kind == "bar":
        x = min(1.0, abs(u - um) / a)
        return BAR_DEPTH * a * math.sqrt(max(0.0, 1 - x * x))
    if kind == "block":
        depth = BLOCK_DEPTH * min(a, we)
        e = min(a - abs(u - um), we - abs(vv))
        return depth * (BLOCK_WALL + (1 - BLOCK_WALL) * min(1.0, max(0.0, e) / BLOCK_BEVEL))
    if kind == "gem":
        return GEM_EDGE + GEM_DEPTH * (we - abs(vv))
    if kind == "limb":
        # a flat strip, the same depth right across (a bow's back and belly are its wide sides): the painted outline
        # lies flat on the faces, and seen edge on the limb is the wall round it, which shows the wood. On a rounded
        # limb the outline sat on the slope and showed from the front as two jagged black lines.
        return LIMB_DEPTH * we
    if kind == "riser":
        return RISER_DEPTH * we                      # a bow's handle: a flat strip like the limbs, a little deeper
    if kind == "flat":
        return FLAT_DEPTH
    return min(ROUND_CAP, math.sqrt(max(0.0, we * we - vv * vv)))          # round


def design_heights(parts, uv, S, sc):
    """Half thickness per texel (texels, NaN where no part reaches) for a weapon built from parts. `uv(x, y)`: the
    design coordinates of a texel's middle; `sc`: texels per design unit."""
    g = 1.0 / sc
    coords = [[uv(x, y) for x in range(S)] for y in range(S)]
    cover = []
    for p in parts:
        m = np.zeros((S, S), bool)
        for y in range(S):
            for x in range(S):
                u, v = coords[y][x]
                m[y, x] = p.color_at(u, v) is not None
        cover.append(m)
    solid = []                                       # parts with a depth of their own
    under = np.zeros((S, S), bool)
    for p, m in zip(parts, cover):
        n = int(m.sum())
        inlay = n > 0 and int((m & under).sum()) >= 0.9 * n and not getattr(p, "proud", False)
        has_shape = hasattr(p, "width") or callable(getattr(p, "relief", None))
        if has_shape and not inlay:
            solid.append(p)
        under |= m
    H = np.full((S, S), np.nan)
    for y in range(S):
        for x in range(S):
            u, v = coords[y][x]
            best = None
            for p in solid:
                h = part_height(p, u, v, g) if hasattr(p, "width") else p.relief(u, v, g)
                if h is not None and (best is None or h > best):
                    best = h
            if best is not None:
                H[y, x] = best * sc
    return H


def finalize(body, H=None, min_h=0.3):
    """A full height map for the finished sprite: texels the design did not reach (outline, ornaments) take a little
    less than their neighbours, anything still without a depth gets the generic one."""
    S = body.shape[0]
    out = np.full((S, S), np.nan) if H is None else np.array(H, float)
    out[~body] = np.nan
    for _ in range(2):
        missing = body & np.isnan(out)
        if not missing.any():
            break
        pad = np.full((S + 2, S + 2), np.nan)
        pad[1:-1, 1:-1] = out
        stack = np.stack([pad[1 + dy:S + 1 + dy, 1 + dx:S + 1 + dx] for dy in (-1, 0, 1) for dx in (-1, 0, 1)])
        got = ~np.isnan(stack)
        best = np.where(got, stack, -1.0).max(axis=0)
        fill = missing & got.any(axis=0)
        out[fill] = best[fill] * 0.85
    missing = body & np.isnan(out)
    if missing.any():
        out[missing] = generic_heights(body)[missing]
    out = np.where(body, np.maximum(out, min_h), 0.0)
    return out


# ------------------------------------------------------------------------------------------------ outline

def boundary_loops(body):
    """The body's outline as closed loops of unit edges (pixel corners, y down), walked with the body on the right.
    Each edge: (start, end, direction, pixel it belongs to)."""
    h, w = body.shape
    pad = np.zeros((h + 2, w + 2), bool)
    pad[1:-1, 1:-1] = body
    out = {}
    for y in range(h):
        for x in range(w):
            if not body[y, x]:
                continue
            if not pad[y, x + 1]:
                out.setdefault((x, y), []).append(((x + 1, y), "R", (x, y)))
            if not pad[y + 1, x + 2]:
                out.setdefault((x + 1, y), []).append(((x + 1, y + 1), "D", (x, y)))
            if not pad[y + 2, x + 1]:
                out.setdefault((x + 1, y + 1), []).append(((x, y + 1), "L", (x, y)))
            if not pad[y + 1, x]:
                out.setdefault((x, y + 1), []).append(((x, y), "U", (x, y)))
    loops = []
    while out:
        p0 = next(iter(out))
        loop, p, prev = [], p0, None
        while True:
            cands = out.get(p)
            if not cands:
                break
            pick = 0
            if prev is not None and len(cands) > 1:     # a pinch: keep hugging the same pixels (turn right)
                for i, c in enumerate(cands):
                    if (prev, c[1]) in RIGHT_TURN:
                        pick = i
            end, d, px = cands.pop(pick)
            if not cands:
                del out[p]
            loop.append((p, end, d, px))
            prev, p = d, end
            if p == p0 and (p0 not in out):
                break
        if loop:
            loops.append(loop)
    return loops


def smooth_normals(loop, reach=4):
    """An outward direction (image coordinates) for every corner of a loop, averaged over the edges around it, so a
    staircase gets the direction of the slope it stands for instead of flipping between up and sideways."""
    n = len(loop)
    out_n = np.array([OUTWARD[e[2]] for e in loop], float)
    res = np.zeros((n, 2))
    r = min(reach, max(1, n // 2))
    for k in range(n):                                   # corner k: between edge k-1 and edge k
        acc = np.zeros(2)
        for j in range(r):
            wgt = r - j
            acc += wgt * (out_n[(k + j) % n] + out_n[(k - 1 - j) % n])
        ln = np.linalg.norm(acc)
        res[k] = acc / ln if ln > 1e-9 else out_n[k]
    return res


# ------------------------------------------------------------------------------------------------ mesh

class Mesh:
    """Quads with their own corners: pos (n, 4, 3) in model units (16 to the block), nrm (n, 4, 3), uv (n, 4, 2) in
    0..1 with v down, mat (n,) index into `materials`."""

    def __init__(self, materials=("face", "side")):
        self.materials = list(materials)
        self.pos, self.nrm, self.uv, self.mat = [], [], [], []

    def quad(self, pos, nrm, uv, mat):
        self.pos.append(pos)
        self.nrm.append(nrm)
        self.uv.append(uv)
        self.mat.append(self.materials.index(mat))

    def arrays(self):
        return (np.array(self.pos, float).reshape(-1, 4, 3), np.array(self.nrm, float).reshape(-1, 4, 3),
                np.array(self.uv, float).reshape(-1, 4, 2), np.array(self.mat, int))

    def extend(self, other):
        for m in other.materials:
            if m not in self.materials:
                self.materials.append(m)
        for p, n, u, m in zip(other.pos, other.nrm, other.uv, other.mat):
            self.quad(p, n, u, other.materials[m])
        return self

    def transformed(self, M, t=(0, 0, 0)):
        """A copy with every point p -> M p + t (M: 3x3, a rotation times a scale)."""
        out = Mesh(self.materials)
        M = np.asarray(M, float)
        Rn = np.linalg.inv(M).T
        flip = np.linalg.det(M) < 0
        for p, n, u, m in zip(self.pos, self.nrm, self.uv, self.mat):
            p2 = np.asarray(p) @ M.T + np.asarray(t, float)
            n2 = np.asarray(n) @ Rn.T
            n2 /= np.maximum(1e-9, np.linalg.norm(n2, axis=1, keepdims=True))
            if flip:
                p2, n2, u = p2[::-1], n2[::-1], np.asarray(u)[::-1]
            out.pos.append(p2)
            out.nrm.append(n2)
            out.uv.append(u)
            out.mat.append(m)
        return out


def corner_heights(body, heights):
    """Heights on texel corners: the mean of the body texels around each corner. Returns (hc, used)."""
    S = body.shape[0]
    acc = np.zeros((S + 1, S + 1))
    cnt = np.zeros((S + 1, S + 1))
    hb = np.where(body, heights, 0.0)
    for dy in (0, 1):
        for dx in (0, 1):
            acc[dy:dy + S, dx:dx + S] += hb
            cnt[dy:dy + S, dx:dx + S] += body
    return np.where(cnt > 0, acc / np.maximum(cnt, 1), 0.0), cnt > 0


def build(body, heights, z_mid=8.0, edge_round=0.9, min_h=0.3, show=None):
    """The solid: front and back over every body texel, a rim wall round the outline. `heights`: half thickness per
    texel (texels). The sprite fills 16 model units; z_mid is the plane it lies in. `show`: see save_heights."""
    S = body.shape[0]
    k = 16.0 / S
    hc, used = corner_heights(body, np.maximum(heights, min_h))
    # surface normals from the corner heights (one-sided at the rim)
    gx = np.zeros_like(hc)
    gy = np.zeros_like(hc)
    for j in range(S + 1):
        for i in range(S + 1):
            if not used[j, i]:
                continue
            l = used[j, i - 1] if i > 0 else False
            r = used[j, i + 1] if i < S else False
            u = used[j - 1, i] if j > 0 else False
            dn = used[j + 1, i] if j < S else False
            if l and r:
                gx[j, i] = (hc[j, i + 1] - hc[j, i - 1]) / 2
            elif r:
                gx[j, i] = hc[j, i + 1] - hc[j, i]
            elif l:
                gx[j, i] = hc[j, i] - hc[j, i - 1]
            if u and dn:
                gy[j, i] = (hc[j + 1, i] - hc[j - 1, i]) / 2
            elif dn:
                gy[j, i] = hc[j + 1, i] - hc[j, i]
            elif u:
                gy[j, i] = hc[j, i] - hc[j - 1, i]
    # rim: a smooth outward direction per outline corner (model x right, y up)
    rim = np.zeros((S + 1, S + 1, 2))
    loops = boundary_loops(body)
    loop_normals = []
    for loop in loops:
        sn = smooth_normals(loop)
        loop_normals.append(sn)
        for kk, e in enumerate(loop):
            x, y = e[0]
            rim[y, x] += (sn[kk][0], -sn[kk][1])
    rl = np.linalg.norm(rim, axis=2)
    on_rim = rl > 1e-9
    rim[on_rim] /= rl[on_rim][:, None]

    def surf_normal(i, j, sign):
        n = np.array([-gx[j, i], gy[j, i], 1.0])
        n /= np.linalg.norm(n)
        if on_rim[j, i] and edge_round > 0:
            n = n + edge_round * np.array([rim[j, i, 0], rim[j, i, 1], 0.0])
            n /= np.linalg.norm(n)
        n[2] *= sign
        return n

    def P(i, j, sign):
        return (i * k, 16.0 - j * k, z_mid + sign * hc[j, i] * k)

    mesh = Mesh()
    ys, xs = np.nonzero(body)
    for y, x in zip(ys, xs):
        cs = [(x, y + 1), (x + 1, y + 1), (x + 1, y), (x, y)]          # counter-clockwise seen from the front
        sx, sy = (x, y) if show is None else (int(show[y, x, 0]), int(show[y, x, 1]))
        uv = [((i - x + sx) / S, (j - y + sy) / S) for i, j in cs]
        mesh.quad([P(i, j, 1) for i, j in cs], [surf_normal(i, j, 1) for i, j in cs], uv, "face")
        cs = cs[::-1]
        uv = uv[::-1]
        mesh.quad([P(i, j, -1) for i, j in cs], [surf_normal(i, j, -1) for i, j in cs], uv, "face")
    for loop, sn in zip(loops, loop_normals):
        n = len(loop)
        for kk, (p, q, _d, px) in enumerate(loop):
            n0 = np.array([sn[kk][0], -sn[kk][1], 0.0])
            n1 = np.array([sn[(kk + 1) % n][0], -sn[(kk + 1) % n][1], 0.0])
            a, b = P(p[0], p[1], 1), P(q[0], q[1], 1)
            c, d = P(q[0], q[1], -1), P(p[0], p[1], -1)
            pos, nrm = [a, b, c, d], [n0, n1, n1, n0]
            g = np.cross(np.subtract(b, a), np.subtract(d, a))
            if float(g @ (n0 + n1)) < 0:
                pos, nrm = pos[::-1], nrm[::-1]
            uvc = ((px[0] + 0.5) / S, (px[1] + 0.5) / S)
            mesh.quad(pos, nrm, [uvc] * 4, "side")
    return mesh


def _perp(d):
    """Two unit vectors across d, the first as near the sprite's plane as it can be."""
    d = np.asarray(d, float)
    d = d / np.linalg.norm(d)
    z = np.array([0.0, 0.0, 1.0])
    e1 = np.cross(z, d)
    if np.linalg.norm(e1) < 1e-6:
        e1 = np.array([1.0, 0.0, 0.0])
    e1 /= np.linalg.norm(e1)
    return e1, np.cross(d, e1)


def facet(mesh, pts, uv, material="face", normal=None, both=False):
    """A flat face of three or four corners, counter-clockwise seen from where `normal` points (default: the
    corners' own turn); every corner shows the one texel `uv`. both: seen from behind too."""
    pts = [np.asarray(p, float) for p in pts]
    n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
    ln = np.linalg.norm(n)
    if ln < 1e-12:
        return
    n /= ln
    if normal is not None and float(n @ np.asarray(normal, float)) < 0:
        pts, n = pts[::-1], -n
    if len(pts) == 3:
        pts = pts + [pts[2]]
    mesh.quad(pts, [n] * 4, [uv] * 4, material)
    if both:
        mesh.quad(pts[::-1], [-n] * 4, [uv] * 4, material)


def tube(mesh, a, b, r, uv, sides=6, material="face", r_end=None, caps=(True, True), turn=0.0):
    """A round bar from a to b (model units) with smooth sides; r_end: its radius at b (0: a point)."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    r_end = r if r_end is None else r_end
    d = b - a
    e1, e2 = _perp(d)
    ring = []
    for k in range(sides):
        t = turn + 2 * math.pi * k / sides
        ring.append(math.cos(t) * e1 + math.sin(t) * e2)
    slope = (r - r_end) / max(1e-9, float(np.linalg.norm(d)))
    axis = d / max(1e-9, float(np.linalg.norm(d)))
    for k in range(sides):
        n0, n1 = ring[k], ring[(k + 1) % sides]
        pos = [a + r * n0, a + r * n1, b + r_end * n1, b + r_end * n0]
        nr = [n0 + slope * axis, n1 + slope * axis, n1 + slope * axis, n0 + slope * axis]
        nr = [v / np.linalg.norm(v) for v in nr]
        g = np.cross(pos[1] - pos[0], pos[3] - pos[0])
        if float(g @ (n0 + n1)) < 0:
            pos, nr = pos[::-1], nr[::-1]
        mesh.quad(pos, nr, [uv] * 4, material)
    for end, centre, rad, out in ((0, a, r, -axis), (1, b, r_end, axis)):
        if not caps[end] or rad <= 1e-6:
            continue
        for k in range(sides):
            facet(mesh, [centre, centre + rad * ring[k], centre + rad * ring[(k + 1) % sides]], uv, material, normal=out)


def mask_runs(mask):
    """A mask as runs of texels along its rows: (y, x0, x1), x1 past the last one."""
    out = []
    for y in range(mask.shape[0]):
        row = mask[y]
        x = 0
        while x < row.shape[0]:
            if row[x]:
                x0 = x
                while x < row.shape[0] and row[x]:
                    x += 1
                out.append((y, x0, x))
            else:
                x += 1
    return out


def flat_runs(runs, S, z_mid=8.0, eps=0.02, material="glow"):
    """flat_plane for a few runs of texels only."""
    mesh = Mesh((material,))
    k = 16.0 / S
    for y, x0, x1 in runs:
        for sign in (1, -1):
            z = z_mid + sign * eps
            cs = [(x0, y + 1), (x1, y + 1), (x1, y), (x0, y)]
            pos = [(i * k, 16 - j * k, z) for i, j in cs]
            uv = [(i / S, j / S) for i, j in cs]
            if sign < 0:
                pos, uv = pos[::-1], uv[::-1]
            mesh.quad(pos, [(0, 0, sign)] * 4, uv, material)
    return mesh


def flat_plane(S, z_mid=8.0, eps=0.02, material="glow"):
    """The whole sprite on a plane through the middle of the solid, both ways: what shows of it is the glow around
    the body (the body's own texels are inside the solid)."""
    mesh = Mesh((material,))
    for sign in (1, -1):
        z = z_mid + sign * eps
        cs = [(0, 16), (16, 16), (16, 0), (0, 0)]
        uv = [(0, 1), (1, 1), (1, 0), (0, 0)]
        pos = [(x, 16 - y, z) for x, y in cs]
        if sign < 0:
            pos, uv = pos[::-1], uv[::-1]
        mesh.quad(pos, [(0, 0, sign)] * 4, uv, material)
    return mesh


# ------------------------------------------------------------------------------------------------ files

def _fmt(v, places):
    s = f"{v:.{places}f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def write_obj(mesh, path, mtllib="weapon.mtl", scale=1 / 16.0):
    """Wavefront OBJ as NeoForge's loader reads it: positions in blocks, v texture coordinates counted from the top
    (the model json sets flip_v false), one usemtl group per material."""
    pos, nrm, uv, mat = mesh.arrays()
    vi, ti, ni = {}, {}, {}
    lines = [f"mtllib {mtllib}"]
    faces = {m: [] for m in range(len(mesh.materials))}

    def index(table, key, kind, text):
        i = table.get(key)
        if i is None:
            i = table[key] = len(table) + 1
            lines.append(f"{kind} {text}")
        return i

    for q in range(len(pos)):
        refs = []
        for c in range(4):
            p = tuple(_fmt(v * scale, 5) for v in pos[q, c])
            t = tuple(_fmt(v, 5) for v in uv[q, c])
            n = tuple(_fmt(v, 2) for v in nrm[q, c])
            refs.append(f"{index(vi, p, 'v', ' '.join(p))}/{index(ti, t, 'vt', ' '.join(t))}/{index(ni, n, 'vn', ' '.join(n))}")
        faces[int(mat[q])].append("f " + " ".join(refs))
    for m, fl in faces.items():
        if fl:
            lines.append(f"usemtl {mesh.materials[m]}")
            lines.extend(fl)
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")
    return len(pos)
