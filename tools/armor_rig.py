"""
The armory's armor model, the way the game builds it (client/armor/ArmorModels.java reads what this writes):

- the vanilla armor boxes (HumanoidArmorModel): head, hat, body, arms and legs, outer model (helmet, chestplate,
  boots: layer_1, inflated 1.0, legs 0.9) and inner model (leggings: layer_2, inflated 0.5, legs 0.4);
- plus each set's own extra parts: pauldrons, crests, horns, wings, capes, tassets, knee guards, toe caps... each a
  small box hung on one of those parts (and mirrored onto the left arm or leg), so armor is not only skin-tight boxes.

Textures are 128x128: the model's UV space is 64x64 at twice the resolution. The vanilla boxes keep their usual
places in the top half; the extra parts are packed into the bottom half.

Model space is the game's: x towards the model's left (the player's right is -x), y down, z towards the back.
Rotations are degrees, applied X then Y then Z about the part's pivot (ModelPart.translateAndRotate).
"""
import math

import numpy as np

TEX = 128          # texture size in pixels
UVS = 64           # UV space in model units
K = TEX // UVS     # pixels per model unit

PART_PIVOT = {
    "head": (0.0, 0.0, 0.0), "body": (0.0, 0.0, 0.0),
    "right_arm": (-5.0, 2.0, 0.0), "left_arm": (5.0, 2.0, 0.0),
    "right_leg": (-1.9, 12.0, 0.0), "left_leg": (1.9, 12.0, 0.0),
}
MIRROR_OF = {"right_arm": "left_arm", "right_leg": "left_leg"}
SLOT_PARTS = {"head": ("head", "hat"), "chest": ("body", "right_arm", "left_arm"), "legs": ("body", "right_leg", "left_leg"),
              "feet": ("right_leg", "left_leg")}
LAYER_OF_SLOT = {"head": 1, "chest": 1, "feet": 1, "legs": 2}


class Box:
    """One cube with its texture rectangle. u, v: texture offset in model units; w, h, d: size in model units.
    Pixel layout (K pixels per unit): top (U+D, V, W, D), bottom (U+D+W, V, W, D), and the strip of the four sides
    (U, V+D, 2(W+D), H): right side, front, left side, back - continuous all the way round."""

    def __init__(self, name, part, origin, size, uv, inflate=0.0, pivot=None, rotation=(0, 0, 0), mirror=False,
                 slot="chest", layer=1, kind="", base=False):
        self.name, self.part = name, part
        self.origin, self.size = tuple(origin), tuple(size)
        self.uv = tuple(uv)
        self.inflate = inflate
        self.pivot = tuple(pivot) if pivot is not None else None
        self.rotation = tuple(rotation)
        self.mirror = mirror
        self.slot, self.layer, self.kind, self.base = slot, layer, kind, base
        w, h, d = size
        self.W, self.H, self.D = int(round(w * K)), int(round(h * K)), int(round(d * K))
        if uv[0] is None:
            self.U = self.V = 0
        else:
            self.U, self.V = int(round(uv[0] * K)), int(round(uv[1] * K))
        self.SW = 2 * (self.W + self.D)

    # ---- face ranges along the strip
    def face_x(self, face):
        W, D = self.W, self.D
        return {"right": (0, D), "front": (D, D + W), "left": (D + W, 2 * D + W), "back": (2 * D + W, 2 * D + 2 * W)}[face]

    def face_at(self, sx):
        W, D = self.W, self.D
        if sx < D:
            return "right"
        if sx < D + W:
            return "front"
        if sx < 2 * D + W:
            return "left"
        return "back"

    # ---- texture coordinates of each region
    def strip_coords(self):
        ys, xs = np.mgrid[0:self.H, 0:self.SW]
        return self.V + self.D + ys, self.U + xs

    def top_coords(self):
        ys, xs = np.mgrid[0:self.D, 0:self.W]
        return self.V + ys, self.U + self.D + xs

    def bottom_coords(self):
        ys, xs = np.mgrid[0:self.D, 0:self.W]
        return self.V + ys, self.U + self.D + self.W + xs

    def place(self, full, strip=None, top=None, bottom=None):
        """Write region arrays into a full texture-sized array (in place) and return it."""
        if strip is not None and self.H and self.SW:
            ty, tx = self.strip_coords()
            full[ty, tx] = strip
        if top is not None and self.D and self.W:
            ty, tx = self.top_coords()
            full[ty, tx] = top
        if bottom is not None and self.D and self.W:
            ty, tx = self.bottom_coords()
            full[ty, tx] = bottom
        return full

    def take(self, full, region="strip"):
        ty, tx = {"strip": self.strip_coords, "top": self.top_coords, "bottom": self.bottom_coords}[region]()
        return full[ty, tx]

    def footprint(self):
        """Width and height of the texture rectangle, in model units."""
        w, h, d = self.size
        return 2 * (w + d), h + d

    def to_json(self):
        return dict(name=self.name, part=self.part, origin=list(self.origin), size=list(self.size), uv=list(self.uv),
                    inflate=self.inflate, pivot=list(self.pivot) if self.pivot else None, rotation=list(self.rotation),
                    mirror=self.mirror)


# ------------------------------------------------------------------------------------------------- the vanilla boxes

def base_boxes():
    """The vanilla armor boxes for both layers: {layer: [Box]}."""
    out = {1: [], 2: []}
    for layer, d in ((1, 1.0), (2, 0.5)):
        out[layer].append(Box("head", "head", (-4, -8, -4), (8, 8, 8), (0, 0), d, slot="head", layer=layer, base=True))
        out[layer].append(Box("hat", "head", (-4, -8, -4), (8, 8, 8), (32, 0), d + 0.5, slot="head", layer=layer, base=True))
        out[layer].append(Box("body", "body", (-4, 0, -2), (8, 12, 4), (16, 16), d, slot="chest" if layer == 1 else "legs",
                              layer=layer, base=True))
        # arms a hair bigger than the body so their fronts win where the two overlap at the shoulder (no z-fighting)
        out[layer].append(Box("arm", "right_arm", (-3, -2, -2), (4, 12, 4), (40, 16), d + 0.02, slot="chest", layer=layer,
                              base=True))
        out[layer].append(Box("leg", "right_leg", (-2, 0, -2), (4, 12, 4), (0, 16), d - 0.1, slot="feet" if layer == 1 else "legs",
                              layer=layer, base=True))
    return out


# ------------------------------------------------------------------------------------------------- packing extras

def pack(extras, top=32, width=UVS, height=UVS):
    """Pack the extra boxes below the vanilla layout (integer offsets; the texture rectangles are whole pixels at 2x as
    long as sizes are multiples of 0.5): tallest first, each into the first shelf with room, else a new shelf.
    Mirrored twins share one rectangle. Returns the UV height used, rounded up to a multiple of 32 (the texture grows
    downwards when a set has many parts)."""
    shelves = []   # [y, h, x]
    bottom = top
    order = sorted([b for b in extras if getattr(b, "twin_of", None) is None], key=lambda b: (-b.footprint()[1], -b.footprint()[0]))
    for b in order:
        fw, fh = b.footprint()
        fw, fh = int(math.ceil(fw)), int(math.ceil(fh))
        if fw > width:
            raise ValueError(f"extra part wider than the texture: {b.name} {fw}")
        for sh in shelves:
            if sh[2] + fw <= width and fh <= sh[1]:
                x, y = sh[2], sh[0]
                sh[2] += fw
                break
        else:
            x, y = 0, bottom
            shelves.append([bottom, fh, fw])
            bottom += fh
        b.uv = (x, y)
        b.U, b.V = x * K, y * K
    for b in extras:
        src = getattr(b, "twin_of", None)
        if src is not None:
            b.uv, b.U, b.V = src.uv, src.U, src.V
    used = max(height, int(math.ceil(bottom / 32.0)) * 32)
    return used


def extra(name, part, origin, size, inflate=0.0, pivot=None, rotation=(0, 0, 0), kind="", slot=None):
    """An extra part on a right-hand or central part; arms and legs get a mirrored twin on the left automatically
    when the model is built (see ArmorModels.java)."""
    layer = 2 if slot == "legs" else 1
    b = Box(name, part, origin, size, (None, None), inflate, pivot, rotation, False, slot or {"head": "head", "body": "chest",
            "right_arm": "chest", "right_leg": "feet"}[part], layer, kind)
    b.uv = None
    return b


# ------------------------------------------------------------------------------------------------- topology

def topology(boxes, size=TEX, height=None):
    """Neighbour indices (up, down, left, right) for every texture pixel, following the cube faces: the strip wraps
    around, its top row meets the top face and its bottom row the bottom face, as on the model."""
    n = size * (height or size)
    nb = [np.full(n, -1, np.int64) for _ in range(4)]
    UP, DN, LF, RT = range(4)

    def idx(y, x):
        return y * size + x

    for b in boxes:
        if getattr(b, "twin_of", None) is not None:
            continue
        W, H, D, SW = b.W, b.H, b.D, b.SW
        if H == 0 or SW == 0:
            continue
        sy0, sx0 = b.V + D, b.U
        for y in range(H):
            for x in range(SW):
                i = idx(sy0 + y, sx0 + x)
                nb[LF][i] = idx(sy0 + y, sx0 + (x - 1) % SW)
                nb[RT][i] = idx(sy0 + y, sx0 + (x + 1) % SW)
                nb[UP][i] = idx(sy0 + y - 1, sx0 + x) if y > 0 else _edge_top(b, x, "top")
                nb[DN][i] = idx(sy0 + y + 1, sx0 + x) if y < H - 1 else _edge_top(b, x, "bottom")
        if D == 0 or W == 0:
            continue
        for region, ry0, rx0 in (("top", b.V, b.U + D), ("bottom", b.V, b.U + D + W)):
            for r in range(D):
                for c in range(W):
                    i = idx(ry0 + r, rx0 + c)
                    nb[UP][i] = idx(ry0 + r - 1, rx0 + c) if r > 0 else idx(sy0 if region == "top" else sy0 + H - 1, sx0 + 2 * D + W + (W - 1 - c))
                    nb[DN][i] = idx(ry0 + r + 1, rx0 + c) if r < D - 1 else idx(sy0 if region == "top" else sy0 + H - 1, sx0 + D + c)
                    nb[LF][i] = idx(ry0 + r, rx0 + c - 1) if c > 0 else idx(sy0 if region == "top" else sy0 + H - 1, sx0 + r)
                    nb[RT][i] = idx(ry0 + r, rx0 + c + 1) if c < W - 1 else idx(sy0 if region == "top" else sy0 + H - 1, sx0 + D + W + (D - 1 - r))
    return nb


def _edge_top(b, sx, region):
    """The top- or bottom-face pixel next to strip column sx's first or last row."""
    W, D = b.W, b.D
    if D == 0 or W == 0:
        return -1
    ry0 = b.V
    rx0 = b.U + D if region == "top" else b.U + D + W
    if sx < D:
        r, c = sx, 0
    elif sx < D + W:
        r, c = D - 1, sx - D
    elif sx < 2 * D + W:
        r, c = D - 1 - (sx - D - W), W - 1
    else:
        r, c = 0, W - 1 - (sx - 2 * D - W)
    return (ry0 + r) * TEX + rx0 + c


# ------------------------------------------------------------------------------------------------- geometry (preview)

def rot(rx, ry, rz):
    """Rz * Ry * Rx (degrees), the game's ModelPart rotation."""
    ax, ay, az = math.radians(rx), math.radians(ry), math.radians(rz)
    cx, sx, cy, sy, cz, sz = math.cos(ax), math.sin(ax), math.cos(ay), math.sin(ay), math.cos(az), math.sin(az)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def box_quads(box, part_pose=None, mirror=False, atlas_off=(0, 0), atlas_scale=1.0):
    """The six faces of a box in model space (the game's axes, y down) as (corners 4x3, uv rect in atlas pixels,
    normal). part_pose: (pivot, rotation) of the part (head/body/arm/leg) it hangs on; the box's own pivot/rotation
    come after. mirror: draw the left-hand twin."""
    x0, y0, z0 = box.origin
    w, h, d = box.size
    g = box.inflate
    X0, Y0, Z0, X1, Y1, Z1 = x0 - g, y0 - g, z0 - g, x0 + w + g, y0 + h + g, z0 + d + g
    # corners in texture order (u0,v0) (u1,v0) (u1,v1) (u0,v1); y down: Y0 is the top
    faces = {
        "front": [(X0, Y0, Z0), (X1, Y0, Z0), (X1, Y1, Z0), (X0, Y1, Z0)],
        "right": [(X0, Y0, Z1), (X0, Y0, Z0), (X0, Y1, Z0), (X0, Y1, Z1)],
        "left": [(X1, Y0, Z0), (X1, Y0, Z1), (X1, Y1, Z1), (X1, Y1, Z0)],
        "back": [(X1, Y0, Z1), (X0, Y0, Z1), (X0, Y1, Z1), (X1, Y1, Z1)],
        "top": [(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y0, Z0), (X0, Y0, Z0)],
        "bottom": [(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y1, Z0), (X0, Y1, Z0)],
    }
    normals = {"front": (0, 0, -1), "back": (0, 0, 1), "right": (-1, 0, 0), "left": (1, 0, 0), "top": (0, -1, 0), "bottom": (0, 1, 0)}
    U, V, W, H, D = box.U, box.V, box.W, box.H, box.D
    rects = {"top": (U + D, V, W, D), "bottom": (U + D + W, V, W, D), "right": (U, V + D, D, H), "front": (U + D, V + D, W, H),
             "left": (U + D + W, V + D, D, H), "back": (U + 2 * D + W, V + D, W, H)}
    M = np.eye(4)
    if part_pose is not None:
        piv, r = part_pose
        T = np.eye(4)
        T[:3, :3] = rot(*r)
        T[:3, 3] = piv
        M = M @ T
    if box.pivot is not None:
        T = np.eye(4)
        T[:3, :3] = rot(*box.rotation)
        T[:3, 3] = box.pivot
        M = M @ T
    out = []
    for f, pts in faces.items():
        if (f in ("top", "bottom") and (W == 0 or D == 0)) or (f in ("front", "back") and (W == 0 or H == 0)) or \
                (f in ("left", "right") and (D == 0 or H == 0)):
            continue
        P = np.array(pts, float)
        P = (M[:3, :3] @ P.T).T + M[:3, 3]
        n = M[:3, :3] @ np.array(normals[f], float)
        u0, v0, uw, vh = rects[f]
        rect = (atlas_off[0] + u0 * atlas_scale, atlas_off[1] + v0 * atlas_scale, uw * atlas_scale, vh * atlas_scale)
        if mirror:
            P[:, 0] *= -1
            n[0] *= -1
        out.append((P, rect, n))
    return out


def part_pose(part, pose=None):
    """(pivot, rotation) of a body part, with an optional pose {part: (rx, ry, rz)}."""
    r = (pose or {}).get(part, (0, 0, 0))
    return PART_PIVOT[part], r
