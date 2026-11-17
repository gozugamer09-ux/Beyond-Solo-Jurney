"""
Worn armor for the armory's sets: the two 128x128 layers and the extra parts (pauldrons, crests, horns, wings,
capes, tassets, toe caps...) described by a set's design in tools/gen_armory_assets.py.

Everything is painted "on the model": a box's four sides are one continuous strip (right side, front, left side,
back), so belts, straps and hems run round the body, and a breastplate can wrap onto the sides. Positions use
`ax`, the distance round the box from the middle of the front (negative towards the player's right, or the outside
of an arm or leg), and `y`, rows from the top of the box. Pixels are tone steps on colour ramps (tools/armor_kit.py),
so the shading, edge light and contact shadows of every piece come out of the same rules.
"""
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import armor_kit as ak  # noqa: E402
import armor_rig as rig  # noqa: E402

FACES = ("right", "front", "left", "back")
METALS = ("metal", "gold", "dark", "crystal")

# edge tones (top, left, bottom, right) by material
EDGE = {
    "metal": (1.1, 0.5, -1.3, -0.7), "gold": (1.0, 0.5, -1.2, -0.6), "dark": (1.0, 0.5, -1.1, -0.6),
    "crystal": (1.8, 0.8, -1.2, -0.6), "leather": (0.8, 0.4, -1.1, -0.5), "cloth": (0.6, 0.3, -1.0, -0.4),
    "fur": (0.7, 0.3, -0.9, -0.4), "bone": (1.2, 0.6, -1.2, -0.6), "stone": (1.0, 0.5, -1.3, -0.6),
    "scale": (1.2, 0.5, -1.2, -0.6), "glow": (0.5, 0.3, -0.5, -0.3),
}


class BP:
    """Helpers for painting one box: masks and tone arrays in strip space (H x SW) and on the top/bottom faces
    (D x W), turned into full-texture arrays for the canvas."""

    def __init__(self, cv, box):
        self.cv, self.b = cv, box
        self.H, self.SW, self.W, self.D = box.H, box.SW, box.W, box.D
        ys, xs = np.mgrid[0:self.H, 0:self.SW]
        self.y = ys.astype(float)
        self.sx = xs
        half = self.SW / 2.0
        ax = (xs + 0.5) - (self.D + self.W / 2.0)
        self.ax = (ax + half) % self.SW - half          # 0 = middle of the front; negative = right/outer side
        self.fid = np.zeros((self.H, self.SW), int)
        self.lx = np.zeros((self.H, self.SW), int)
        for i, f in enumerate(FACES):
            x0, x1 = box.face_x(f)
            self.fid[:, x0:x1] = i
            self.lx[:, x0:x1] = np.arange(x1 - x0)[None, :]
        self.fw = np.where((self.fid == 1) | (self.fid == 3), self.W, self.D)
        self.front = self.fid == 1
        self.back = self.fid == 3
        self.sides = (self.fid == 0) | (self.fid == 2)
        self.outer = self.fid == 0
        self.inner = self.fid == 2
        # back-relative coordinate: 0 = middle of the back
        self.bx = (self.ax + self.SW) % self.SW - half
        self.bx = np.where(self.bx < -half, self.bx + self.SW, self.bx)
        ty, tx = np.mgrid[0:max(1, self.D), 0:max(1, self.W)]
        self.ty, self.tx = ty.astype(float), tx.astype(float)

    # ---- masks
    def S(self, fill=False, dtype=bool):
        return np.full((self.H, self.SW), fill, dtype)

    def T(self, fill=False, dtype=bool):
        return np.full((max(1, self.D), max(1, self.W)), fill, dtype)

    def rows(self, y0, y1):
        return (self.y >= y0) & (self.y < y1)

    def around(self, half_width):
        """Columns within half_width of the middle of the front."""
        return np.abs(self.ax) < half_width

    def around_back(self, half_width):
        return np.abs(self.bx) < half_width

    def face(self, *names):
        m = self.S()
        for n in names:
            m |= self.fid == FACES.index(n)
        return m

    def face_poly(self, face, pts):
        """A polygon in one face's own coordinates (x across the face from its strip-left edge, y down)."""
        x0, x1 = self.b.face_x(face)
        m = ak.poly(pts, x1 - x0, self.H)
        out = self.S()
        out[:, x0:x1] = m
        return out

    def front_poly(self, pts):
        """A polygon in front-centred coordinates (x = ax, may run onto the sides), y down."""
        shifted = [(x + self.SW / 2.0, y) for x, y in pts]
        m = ak.poly(shifted, self.SW, self.H)
        # m is indexed by ax + SW/2; map back to strip columns
        out = self.S()
        col = (np.floor(self.ax[0] + self.SW / 2.0)).astype(int) % self.SW
        out[:, :] = m[:, col]
        return out

    def back_poly(self, pts):
        shifted = [(x + self.SW / 2.0, y) for x, y in pts]
        m = ak.poly(shifted, self.SW, self.H)
        out = self.S()
        col = (np.floor(self.bx[0] + self.SW / 2.0)).astype(int) % self.SW
        out[:, :] = m[:, col]
        return out

    def top_circle(self, cx, cy, r):
        return ((self.tx + 0.5 - cx) ** 2 + (self.ty + 0.5 - cy) ** 2) <= r * r

    # ---- to the canvas
    def full(self, strip=None, top=None, bottom=None, dtype=bool, fill=False):
        out = np.full((self.cv.h, self.cv.w), fill, dtype)
        self.b.place(out, strip, top, bottom)
        return out

    def paint(self, strip, color, kind, tone=None, top=None, bottom=None, top_tone=None, bottom_tone=None,
              edge=None, ao=1.0, glow=0.0):
        m = self.full(strip, top, bottom)
        if not m.any():
            return -1
        t = np.zeros((self.cv.h, self.cv.w), np.float32)
        if tone is not None:
            self.b.place(t, strip=np.broadcast_to(tone, (self.H, self.SW)) if strip is not None else None)
        if top_tone is not None and top is not None:
            self.b.place(t, top=np.broadcast_to(top_tone, top.shape))
        if bottom_tone is not None and bottom is not None:
            self.b.place(t, bottom=np.broadcast_to(bottom_tone, bottom.shape))
        return self.cv.piece(m, color, kind if kind != "glow" else "glow", t, edge or EDGE.get(kind, EDGE["metal"]), ao, glow)

    def shade(self, strip, steps, top=None, top_steps=0):
        m = self.full(strip, top)
        t = np.zeros((self.cv.h, self.cv.w), np.float32)
        if strip is not None:
            self.b.place(t, strip=np.broadcast_to(steps, (self.H, self.SW)).astype(np.float32))
        if top is not None:
            self.b.place(t, top=np.broadcast_to(top_steps, top.shape).astype(np.float32))
        self.cv.shade(m, t)

    def erase(self, strip=None, top=None, bottom=None):
        self.cv.erase(self.full(strip, top, bottom))

    def pix(self, strip_mask, tone_value):
        """Fixed tone on pixels that are already painted (details after finish())."""
        m = self.full(strip_mask)
        self.cv.tone[m & (self.cv.rid >= 0)] = tone_value


# ------------------------------------------------------------------------------------------------- tone helpers

def vband(bp, mask, kind, flip=False):
    """Polished-metal banding over the rows the mask covers (per column run)."""
    t = np.zeros(mask.shape, np.float32)
    cols = np.nonzero(mask.any(axis=0))[0]
    for c in cols:
        ys = np.nonzero(mask[:, c])[0]
        if len(ys) == 0:
            continue
        # split into runs
        runs = np.split(ys, np.nonzero(np.diff(ys) > 1)[0] + 1)
        for r in runs:
            prof = ak.band_profile(len(r), kind)
            if flip:
                prof = prof[::-1]
            t[r, c] = prof
    return t


def convex(bp, mask, ridge=True, strength=1.0):
    """Horizontal shading for a plate curving round the body: lit on the player's-right half of each face (the light
    is top left), a bright ridge line down the middle of the front."""
    t = np.zeros(mask.shape, np.float32)
    rel = (bp.lx + 0.5) / bp.fw
    t += np.where(rel < 0.42, 0.35, np.where(rel > 0.72, -0.45, 0.0)) * strength
    if ridge:
        mid = bp.W // 2
        t += np.where(bp.front & (bp.lx == mid - 1), 0.9, 0) + np.where(bp.front & (bp.lx == mid), -0.5, 0)
        t += np.where(bp.back & (bp.lx == mid - 1), 0.6, 0) + np.where(bp.back & (bp.lx == mid), -0.4, 0)
    return t * mask


def run_pos(mask):
    """For every pixel of a mask: where it sits in its column's run, 0 at the run's top to 1 at its bottom, and the
    run's length."""
    pos = np.zeros(mask.shape, np.float32)
    ln = np.zeros(mask.shape, np.float32)
    H = mask.shape[0]
    for c in np.nonzero(mask.any(axis=0))[0]:
        col = mask[:, c]
        y = 0
        while y < H:
            if not col[y]:
                y += 1
                continue
            y1 = y
            while y1 < H and col[y1]:
                y1 += 1
            n = y1 - y
            pos[y:y1, c] = (np.arange(n) + 0.5) / n
            ln[y:y1, c] = n
            y = y1
    return pos, ln


LIGHT = -0.6     # the light's direction round the body (radians from the front, negative = the player's right)


def form(bp, mask, kind="metal", amp=1.0, vert=1.0, spec=1.0, light=LIGHT, roll=None):
    """Shading for a plate that curves round the body: a lit side facing the light (front, player's right), a core
    shadow turning away from it, a top light and bottom shadow along each column run, a bounce light on the lowest
    row, and for polished materials a specular streak. Smooth values: the ramp steps make the contour bands."""
    theta = 2 * np.pi * bp.ax / bp.SW
    hd = np.cos(theta - light)
    hd = np.where(hd > 0, hd, hd * 0.45)            # the back only a little darker: the game lights it too
    v, ln = run_pos(mask)
    prof = np.interp(v, [0.0, 0.12, 0.4, 0.75, 0.93, 1.0], [0.6, 0.4, 0.05, -0.45, -0.65, -0.3])
    prof = prof * np.clip(ln / 10.0, 0.35, 1.0)        # short plates: a gentler top-to-bottom change
    if roll is not None:
        prof = prof * roll
    t = (1.2 * hd - 0.3) * amp + prof * vert
    if spec and kind in METALS:
        sx = np.abs(theta - light * 0.75)
        streak = (sx < (1.6 * np.pi / bp.SW)) & (v < 0.62) & (ln >= 7)
        t = t + np.where(streak, 1.15 * spec, 0) + np.where((sx < (3.2 * np.pi / bp.SW)) & ~streak & (v < 0.5) & (ln >= 7), 0.3 * spec, 0)
    return (t * mask).astype(np.float32)


def top_form(bp, kind="metal", amp=1.0, cx=0.38, cy=0.7, spec=1.0):
    """Shading for a top face seen from above: a highlight towards the front and the player's right."""
    u = (bp.tx + 0.5) / bp.W
    w = (bp.ty + 0.5) / bp.D
    d = np.hypot((u - cx) * 1.1, (w - cy) * 1.0)
    t = (1.25 - 2.0 * d) * amp
    if spec and kind in METALS:
        t = t + np.where(d < 0.12, 1.0 * spec, 0)
    return t


def ridge_tone(bp, mask, back=True):
    """A raised ridge down the middle of the front (and back): a lit line with its shadow beside it."""
    t = np.where(bp.front & (np.abs(bp.ax + 0.5) < 0.5), 1.1, 0) + np.where(bp.front & (np.abs(bp.ax - 0.5) < 0.5), -0.7, 0)
    if back:
        t = t + np.where(bp.back & (np.abs(bp.bx + 0.5) < 0.5), 0.8, 0) + np.where(bp.back & (np.abs(bp.bx - 0.5) < 0.5), -0.5, 0)
    return t * mask


def lames(bp, mask, height, kind="metal", y0=None, curve=0.0):
    """Overlapping horizontal plates (faulds, pauldron lames): each strip lit at its top, dark at its lower lip.
    curve: the plates bow down at the middle of the front (positive) or up (negative), in rows."""
    t = np.zeros(mask.shape, np.float32)
    ys = np.nonzero(mask.any(axis=1))[0]
    if len(ys) == 0:
        return t
    start = ys.min() if y0 is None else y0
    bow = curve * np.cos(2 * np.pi * bp.ax / bp.SW) if curve else 0.0
    k = np.floor(bp.y - start - bow + 1000 * height).astype(int) % int(height)
    t = np.where(k == 0, 1.3, np.where(k == 1, 0.5, np.where(k == height - 1, -1.4, np.where(k == height - 2, -0.5, 0.0))))
    return t * mask


# ------------------------------------------------------------------------------------------------- stamps

GLYPHS = {
    # small emblems, '#' = mark, '+' = highlight, '-' = shadow (drawn over whatever is there)
    "anvil": ["#######", " ##### ", "  ###  ", " ##### "],
    "sword": ["   +   ", "   #   ", "   #   ", "   #   ", " ##### ", "   #   ", "   -   "],
    "shield": [" ##### ", "#+++++#", "#+###+#", "#+###+#", " #+++# ", "  #+#  ", "   #   "],
    "sun": ["#  #  #", " # # # ", "  ###  ", "### ###", "  ###  ", " # # # ", "#  #  #"],
    "star": ["   #   ", "   #   ", "  ###  ", "#######", "  ###  ", " ## ## ", "#     #"],
    "eye": ["  ###  ", " #+++# ", "#+#-#+#", " #+++# ", "  ###  "],
    "fang": ["#     #", "##   ##", " #   # ", " #   # ", "  # #  "],
    "leaf": ["   #   ", "  ###  ", " ##### ", " ##+## ", "  ###  ", "   #   ", "   #   "],
    "crescent": ["  ###  ", " #     ", "#      ", "#      ", " #     ", "  ###  "],
    "flame": ["   #   ", "  ##   ", "  ###  ", " ##### ", " ##+## ", "  ###  "],
    "snow": ["#  #  #", " # # # ", "  ###  ", "#######", "  ###  ", " # # # ", "#  #  #"],
    "skull": [" ##### ", "#######", "#-##-##", "#######", " # # # "],
    "crown": ["#  #  #", "## # ##", "#######", "#+#+#+#", "#######"],
    "wing": ["#      ", "##     ", "###    ", "####   ", " ####  ", "  #### "],
    "spider": ["#  #  #", " # # # ", "  ###  ", "#######", "  ###  ", " # # # ", "#  #  #"],
    "tower": ["# # # #", "#######", " ##### ", " ##-## ", " ##-## ", " ##### "],
    "arrow": ["   #   ", "  ###  ", " # # # ", "   #   ", "   #   ", "  # #  ", " #   # "],
    "chalice": ["#######", " ##### ", "  ###  ", "   #   ", "   #   ", " ##### "],
    "moon": ["  ###  ", " ##    ", "##     ", "##     ", " ##    ", "  ###  "],
    "rune": ["#  #", "## #", "# ##", "#  #"],
}


def stamp(bp, name, cx, y, color, kind="gold", tone=0.5, glow=0.0, scale=1, face="front"):
    """Paint a glyph centred on ax=cx (front) or bx=cx (back) at row y."""
    g = GLYPHS[name]
    h, w = len(g), max(len(r) for r in g)
    m = bp.S()
    hi = bp.S()
    lo = bp.S()
    coord = bp.ax if face == "front" else bp.bx
    colix = np.floor(coord - (cx - (w * scale) / 2.0)).astype(int)
    for r, row in enumerate(g):
        for c, ch in enumerate(row):
            if ch == " ":
                continue
            for sy in range(scale):
                for sxx in range(scale):
                    yy = int(y) + r * scale + sy
                    sel = (colix == c * scale + sxx) & (bp.y == yy)
                    if ch == "#":
                        m |= sel
                    elif ch == "+":
                        m |= sel
                        hi |= sel
                    elif ch == "-":
                        m |= sel
                        lo |= sel
    t = np.where(hi, tone + 1.2, np.where(lo, tone - 1.2, tone))
    return bp.paint(m, color, kind, t, edge=(0.9, 0.4, -0.9, -0.4), ao=0.8, glow=glow)


def rivets(w, bp, mask_pts, color, kind="metal", coord="front"):
    """Rivet heads: a bright pixel with a dark one below it, on top of what is there. coord "back" measures x from
    the middle of the back."""
    c = bp.ax if coord == "front" else bp.bx
    m = bp.S()
    for (cx, y) in mask_pts:
        m |= (np.floor(c) == np.floor(cx)) & (bp.y == y)
    return bp.paint(m, color, kind, 1.8, edge=(0.6, 0.3, -1.6, -1.0), ao=1.6)


def gem(w, bp, cx, y, color, size=2, glow=0.6, coord="front"):
    """A cut gem: a dark setting, a saturated body, a white glint."""
    c = bp.ax if coord == "front" else bp.bx
    m = (np.abs(c - cx) < size / 2.0) & (bp.y >= y) & (bp.y < y + size)
    t = np.where((bp.y == y) & (c < cx), 2.6, np.where(bp.y == y + size - 1, -0.6, 0.6))
    return bp.paint(m, color, "crystal", t, edge=(0.6, 0.5, -0.8, -0.5), ao=1.2, glow=glow)


def stitches(bp, path, color, kind="cloth", every=2, tone=1.4):
    m = path & (((bp.sx + bp.y.astype(int)) % every) == 0)
    return bp.paint(m, color, kind, tone, edge=(0, 0, 0, 0), ao=0.5)


# ================================================================================================= the set builder

class Worn:
    """Paints one set: design = {"pal": {...}, "head": {...}, "chest": {...}, "legs": {...}, "feet": {...}}."""

    def __init__(self, design):
        self.d = design
        self.pal = design["pal"]
        self.extras = {1: [], 2: []}
        self.base = rig.base_boxes()
        self.rng = np.random.default_rng(design.get("seed", 7))

    # ---- palette: (colour, ramp kind)
    def c(self, key, kind=None):
        k0 = key
        while key not in self.pal or self.pal[key] is None:
            key = FALLBACK.get(key)
            if key is None:
                raise KeyError("palette has no %s" % k0)
            if key.startswith("#"):
                return key[1:], (kind or DEFAULT_KIND.get(k0, "metal"))
        v = self.pal[key]
        if key != k0 and kind is None:
            kind = DEFAULT_KIND.get(k0) if k0 in ("mail",) else None
        if isinstance(v, tuple):
            return v[0], (kind or v[1])
        return v, (kind or DEFAULT_KIND.get(key, "metal"))

    def has(self, key):
        return self.pal.get(key) is not None

    def gem_color(self):
        for k in ("gem", "glow", "crystal"):
            if self.has(k):
                return self.c(k)[0]
        return "e03a3a"

    def box(self, layer, name):
        return next(b for b in self.base[layer] if b.name == name)

    def add(self, b, painter, **opts):
        b.painter = painter
        b.opts = opts
        self.extras[b.layer].append(b)
        return b

    # ---- build
    def build(self):
        d = self.d
        # extra parts first (their shapes decide some of the painting)
        for slot in ("head", "chest", "legs", "feet"):
            for name, opts in d.get(slot, {}).get("extras", []):
                EXTRAS[name](self, **opts)
        # every boot gets the toe of a foot (steel caps on plate, a rounded leather toe on soft boots) unless the design
        # has its own or says toes=False: a boot reads as a boot, not as the bottom of a box
        feet = d.get("feet")
        if feet and feet.get("toes", True) and not any(n == "toes" for n, _ in feet.get("extras", [])):
            k = feet["kind"]
            ex_toes(self, key=feet.get("metal", "metal") if k == "plate" else feet.get("leather", "leather"), long=1.25)
        self.uv_h = {}
        for layer in (1, 2):
            self.uv_h[layer] = rig.pack(self.extras[layer])
        self.cv = {}
        for layer in (1, 2):
            boxes = [b for b in self.base[layer] if b.name != "hat" or layer == 1] + self.extras[layer]
            th = self.uv_h[layer] * rig.K
            self.cv[layer] = ak.Canvas(rig.TEX, th, rig.topology(boxes, rig.TEX, th))
        head = d.get("head")
        if head:
            HEADS[head["kind"]](self, BP(self.cv[1], self.box(1, "head")), head)
            hat = BP(self.cv[1], self.box(1, "hat"))
            for fn in head.get("hat", []):
                HAT[fn[0]](self, hat, **fn[1])
        chest = d.get("chest")
        if chest:
            BODIES[chest["kind"]](self, BP(self.cv[1], self.box(1, "body")), chest)
            ARMS[chest.get("arms", chest["kind"])](self, BP(self.cv[1], self.box(1, "arm")), chest)
        legs = d.get("legs")
        if legs:
            WAISTS[legs.get("waist", legs["kind"])](self, BP(self.cv[2], self.box(2, "body")), legs)
            LEGS[legs["kind"]](self, BP(self.cv[2], self.box(2, "leg")), legs)
        feet = d.get("feet")
        if feet:
            BOOTS[feet["kind"]](self, BP(self.cv[1], self.box(1, "leg")), feet)
        for layer in (1, 2):
            for b in self.extras[layer]:
                if getattr(b, "twin_of", None) is None:
                    b.painter(self, BP(self.cv[layer], b), **b.opts)
        out = {}
        for layer in (1, 2):
            self.cv[layer].finish()
            for fn in self.d.get("post", []):
                pass
            out[layer] = self.cv[layer].render()
            out["glow%d" % layer] = self.cv[layer].glow_mask()
        out["parts"] = [dict(b.to_json(), slot=b.slot, layer=b.layer) for layer in (1, 2) for b in self.extras[layer]]
        out["uv_height"] = {1: self.uv_h[1], 2: self.uv_h[2]}
        return out


# a missing palette key borrows another's colour ("#rrggbb": a fixed colour)
FALLBACK = {"mail": "metal", "metal": "trim", "trim": "metal", "scale": "leather", "cloth2": "cloth", "leather2": "leather",
            "leather": "cloth", "cloth": "leather", "lining": "cloth2", "fur": "leather", "bone": "trim", "crystal": "glow",
            "glow": "trim", "stone": "metal", "dark": "#141018", "thread": "#d8c49a", "gem": "glow"}

DEFAULT_KIND = {"metal": "metal", "metal2": "metal", "trim": "gold", "cloth": "cloth", "cloth2": "cloth", "lining": "cloth",
                "leather": "leather", "leather2": "leather", "mail": "metal", "fur": "fur", "bone": "bone", "gem": "crystal",
                "glow": "glow", "dark": "dark", "scale": "scale", "stone": "stone", "crystal": "crystal"}


# ------------------------------------------------------------------------------------------------- shared garment bits

def material_tone(w, bp, mask, kind):
    """What a plate is made of, on top of its shape: rough chipped stone, faceted ice and crystal, layered scales."""
    if not mask.any():
        return 0.0
    if kind == "stone":
        blocks = ak.hash01((bp.sx + (bp.y.astype(int) // 3) % 2 * 2) // 4, bp.y.astype(int) // 3, int(w.rng.integers(1000)))
        t = np.where(blocks > 0.7, 0.5, np.where(blocks < 0.25, -0.5, 0.0))
        grit = ak.hash01(bp.sx, bp.y.astype(int), 17) > 0.9
        return (t - np.where(grit, 0.6, 0)) * mask
    if kind == "crystal":
        return ak.facet_tone(mask, int(w.rng.integers(1000))) * 0.55 * mask
    if kind == "scale":
        return ak.scale_tone(bp.H, bp.SW, 4) * 0.7 * mask
    return 0.0


def plate_finish(w, bp, o, mask, seed=1):
    """The optional finishing passes every plate painter shares: glowing or dark cracks, an etched border, wear."""
    if not mask.any():
        return
    c = o.get("cracks")
    if c:
        cr = ak.cracks(mask, seed + 3, n=o.get("crack_n", 5), length=o.get("crack_len", 9))
        if c == "glow" and w.has("glow"):
            glow_line(w, bp, cr, "glow", 1.0)
            halo = ak.grow(cr, 1) & mask & ~cr
            bp.shade(halo, 0.6)
        else:
            bp.shade(cr, -1.8)
            bp.shade(np.roll(cr, 1, axis=0) & mask & ~cr, 0.8)
    if o.get("etch"):
        etch(w, bp, mask, o.get("etch_inset", 2), o.get("etch_key"), glow=o.get("etch_glow", 0.0))
    if o.get("wear"):
        wear(w, bp, mask, 6, seed)


def plate(w, bp, mask, key="metal", ridge=False, curve=1.0, extra=None, edge=None, ao=1.0, flip=False, glow=0.0, spec=1.0):
    col, kind = w.c(key)
    t = form(bp, mask, kind, amp=curve, spec=spec if kind in METALS else 0.0)
    if ridge:
        t = t + ridge_tone(bp, mask)
    t = t + material_tone(w, bp, mask, kind)
    if extra is not None:
        t = t + extra
    return bp.paint(mask, col, kind, t, edge=edge, ao=ao, glow=glow)


def cloth(w, bp, mask, key="cloth", seed=1, period=5.0, strength=0.9, extra=None, edge=None, ao=1.0, slant=0.0):
    col, kind = w.c(key, "cloth")
    t = ak.cloth_folds(bp.H, bp.SW, seed, period, strength, slant) * 0.85
    t = t + form(bp, mask, "cloth", amp=0.6, vert=0.7, spec=0)
    if extra is not None:
        t = t + extra
    return bp.paint(mask, col, kind, t * 1.0, edge=edge, ao=ao)


def leather(w, bp, mask, key="leather", seed=3, extra=None, edge=None, ao=1.0):
    col, kind = w.c(key, "leather")
    t = ak.leather_tone(bp.H, bp.SW, seed) + form(bp, mask, "leather", amp=0.7, vert=0.8, spec=0)
    if extra is not None:
        t = t + extra
    return bp.paint(mask, col, kind, t, edge=edge, ao=ao)


def mail(w, bp, mask, key="mail"):
    col, kind = w.c(key, "metal")
    t = ak.mail_tone(bp.H, bp.SW) + form(bp, mask, "metal", amp=0.6, vert=0.5, spec=0) - 0.3
    return bp.paint(mask, col, kind, t, edge=(0.6, 0.2, -1.0, -0.3), ao=1.0)


def quilt(w, bp, mask, key="cloth2"):
    col, kind = w.c(key, "cloth")
    t = ak.quilt_tone(bp.H, bp.SW, 4) * 0.8 + convex(bp, mask, False, 0.5)
    return bp.paint(mask, col, kind, t, edge=(0.5, 0.2, -1.0, -0.3), ao=1.0)


def fur(w, bp, mask, key="fur", seed=5, jag=True):
    col, kind = w.c(key, "fur")
    t = ak.fur_tone(bp.H, bp.SW, seed) + vband(bp, mask, "leather") * 0.8
    return bp.paint(mask, col, kind, t, edge=(0.9, 0.3, -1.0, -0.4), ao=1.0)


def scales(w, bp, mask, key="scale", size=4):
    col, kind = w.c(key, "scale")
    t = ak.scale_tone(bp.H, bp.SW, size) + convex(bp, mask, False, 0.5)
    return bp.paint(mask, col, kind, t, edge=(1.0, 0.4, -1.2, -0.5), ao=1.0)


def band(w, bp, y0, y1, key="trim", where=None, tone=0.0, pattern=None, glow=0.0):
    """A band round the box (belt, brow band, hem, rim)."""
    col, kind = w.c(key)
    m = bp.rows(y0, y1) if where is None else (bp.rows(y0, y1) & where)
    h = y1 - y0
    t = np.where(bp.y == y0, 1.0, np.where(bp.y == y1 - 1, -0.9 if h > 1 else 0.2, 0.0)) + tone
    if pattern == "rope":
        t = t + np.where(((bp.sx + bp.y.astype(int)) % 3) == 0, 0.8, np.where(((bp.sx + bp.y.astype(int)) % 3) == 2, -0.6, 0))
    elif pattern == "studs":
        t = t + np.where((bp.sx % 4 == 1) & (bp.y == y0 + h // 2), 1.6, 0)
    elif pattern == "zig":
        t = t + np.where(((bp.sx + (bp.y - y0).astype(int) * 2) % 4) == 0, 0.9, 0)
    elif pattern == "inlay":
        t = t + np.where((bp.sx % 6 == 2) & (bp.y == y0 + h // 2), 1.8, 0) + np.where((bp.sx % 6 == 3) & (bp.y == y0 + h // 2), 1.2, 0)
    return bp.paint(m, col, kind, t, edge=EDGE.get(kind), ao=1.0, glow=glow)


def buckle(w, bp, cx, y, h=3, key="trim"):
    col, kind = w.c(key)
    outer = (np.abs(bp.ax - cx) < 2.0) & bp.rows(y, y + h)
    hole = (np.abs(bp.ax - cx) < 0.6) & bp.rows(y + 1, y + h - 1)
    t = np.where(bp.y == y, 1.4, np.where(bp.y == y + h - 1, -0.8, 0.3)) + np.where(bp.ax < cx - 0.5, 0.4, 0)
    bp.paint(outer & ~hole, col, kind, t, edge=(0.8, 0.4, -1.0, -0.6), ao=1.4)


def emblem(w, bp, name, y, key="trim", cx=0.0, face="front", glow=0.0, scale=1):
    col, kind = w.c(key)
    return stamp(bp, name, cx, y, col, kind, 0.6, glow, scale, face)


def glow_line(w, bp, mask, key="glow", strength=1.0):
    col, kind = w.c(key, "glow")
    t = np.full(mask.shape, 1.0)
    return bp.paint(mask, col, "glow", t, edge=(0.6, 0.3, -0.6, -0.3), ao=0.3, glow=strength)


def wear(w, bp, mask, n=6, seed=1):
    """Scratches and nicks on worn steel: a few light pixels with a dark one beside."""
    rng = np.random.default_rng(seed)
    ys, xs = np.nonzero(mask)
    if len(ys) == 0:
        return
    s = bp.S()
    d = bp.S()
    for _ in range(n):
        i = rng.integers(len(ys))
        y, x = ys[i], xs[i]
        L = rng.integers(1, 3)
        for k in range(L):
            if y + k < bp.H and x + k < bp.SW and mask[y + k, x + k]:
                s[y + k, x + k] = True
        if y + 1 < bp.H and mask[y + 1, x]:
            d[y + 1, x] = True
    bp.shade(s & mask, 1.4)
    bp.shade(d & mask & ~s, -1.0)


# ------------------------------------------------------------------------------------------------- helmets

def dome(bp, cr=10.0, cc=5.5, k=0.2, base=1.2):
    """Tone for a top face seen from above: brightest towards the front and the player's right (the light)."""
    d = np.hypot(bp.ty + 0.5 - cr, bp.tx + 0.5 - cc)
    return base - k * d


def head_rim_bottom(w, bp, key="metal", width=2):
    col, kind = w.c(key)
    ring = ~((bp.tx >= width) & (bp.tx < bp.W - width) & (bp.ty >= width) & (bp.ty < bp.D - width))
    bp.paint(None, col, kind, bottom=ring, bottom_tone=-1.6, edge=(0, 0, 0, 0))


FACE_OF_STYLE = {"sallet": "slit", "greathelm": "cross", "tower": "cross", "bascinet": "snout", "kettle": "open", "barbute": "y",
                 "nasal": "nasal", "open": "open", "bars": "bars", "cheek": "cheek", "mask": "eyes", "cap": "open", "horned": "cheek"}


def face_hole(bp, face):
    """The transparent opening of a helmet's face (the wearer's face shows through), or None for a closed helm."""
    if face == "y":
        return bp.front_poly([(-6, 6.5), (6, 6.5), (6, 9.5), (2.5, 10.5), (2.5, 16), (-2.5, 16), (-2.5, 10.5), (-6, 9.5)])
    if face == "cheek":
        return bp.front_poly([(-6, 6), (6, 6), (6, 9.5), (3.5, 12), (3.5, 16), (-3.5, 16), (-3.5, 12), (-6, 9.5)])
    if face == "open":
        return bp.front_poly([(-5, 5.5), (5, 5.5), (6, 6.5), (6, 16), (-6, 16), (-6, 6.5)])
    if face == "nasal":
        return bp.front_poly([(-5, 6), (5, 6), (6, 7), (6, 16), (-6, 16), (-6, 7)]) & ~(bp.around(1.01) & bp.rows(0, 12))
    if face == "bars":
        o = bp.front_poly([(-6, 6), (6, 6), (6, 14), (-6, 14)])
        bars = (np.mod(bp.ax + 6.0, 3.0) < 1.0) | bp.rows(9, 10)
        return o & ~bars
    return None


def etch(w, bp, mask, inset=2, key=None, glow=0.0):
    """An engraved line following a plate's outline a few pixels in: a dark groove with a lit lower lip."""
    inner = ak.shrink(mask, inset) & ~ak.shrink(mask, inset + 1)
    if key:
        col, kind = w.c(key)
        bp.paint(inner, col, kind, 0.4, edge=(0.6, 0.3, -0.8, -0.3), ao=0.5, glow=glow)
    else:
        bp.shade(inner, -1.1)
        lip = np.roll(inner, 1, axis=0) & mask & ~inner
        bp.shade(lip, 0.5)


def head_helm(w, bp, o):
    """A metal helm. style picks the shape and the default face: sallet (eye slit), greathelm / tower (cross slit),
    bascinet (snouted visor), barbute (Y opening), cheek (arched opening with cheek guards), nasal, open, bars (a grille
    over the face), mask (closed, two eye holes), cap (a skull cap over an open face), kettle (open, wear with a brim)."""
    style = o.get("style", "sallet")
    face = o.get("face", FACE_OF_STYLE.get(style, "slit"))
    key = o.get("metal", "metal")
    trim = o.get("trim", "trim")
    col, kind = w.c(key)
    hole = face_hole(bp, face)
    if hole is None:
        hole = bp.S()
    shell = ~hole
    if style == "cap":
        shell &= bp.rows(0, 6) | bp.back | (bp.sides & bp.rows(0, 12 if o.get("cheeks", True) else 7))
    if style == "horned":
        pass
    t = form(bp, shell, kind)
    if o.get("fluted"):
        fl = shell & bp.rows(0, 7) & ~bp.back
        t = t + np.where(fl & (np.mod(bp.ax, 3.0) < 1.0), 0.9, 0) + np.where(fl & (np.mod(bp.ax - 1.0, 3.0) < 1.0), -0.6, 0)
    bp.paint(shell, col, kind, t, top=bp.T(True), top_tone=top_form(bp, kind, cy=0.75))
    head_rim_bottom(w, bp, key)
    # the rim round the face opening: a rolled edge, lit along the brow
    if hole.any():
        rim = ak.grow(hole, 1) & ~hole & shell
        above = np.roll(hole, -1, axis=0) & rim
        bp.shade(above, 1.3)
        bp.shade(rim & ~above & (bp.ax < 0), 0.5)
        bp.shade(rim & ~above & (bp.ax >= 0), -0.7)
    # raised comb / ridge over the crown
    if o.get("ridge", style in ("sallet", "bascinet", "kettle", "nasal", "barbute", "cheek", "cap")):
        ridge = (bp.around(1.01) | bp.around_back(1.01)) & bp.rows(0, o.get("ridge_rows", 5)) & shell
        rtop = np.abs(bp.tx + 0.5 - bp.W / 2) < 1.01
        tr, tk = w.c(o.get("ridge_key", key))
        bp.paint(ridge, tr, tk, np.where(bp.ax < 0, 1.2, -0.3) + np.where(bp.back, -0.4, 0), top=rtop,
                 top_tone=np.where(bp.tx < bp.W / 2, 1.8, 0.4), edge=(1.2, 0.8, -1.2, -0.9))
    # brow band
    if o.get("band", True):
        y0 = o.get("band_y", 3 if style in ("greathelm", "tower") else 4)
        band(w, bp, y0, y0 + 2, trim, where=shell & ~ak.grow(hole, 1), pattern=o.get("band_pattern"))
    void = w.c("dark")[0] if w.has("dark") else "100e16"
    slit = bp.S()
    if face == "slit":
        slit = bp.front_poly([(-6.5, 7.5), (6.5, 7.5), (6.5, 9.4), (-6.5, 9.4)])
    elif face == "cross":
        slit = (bp.around(6.5) & bp.rows(7, 9) & ~bp.around(0.99)) | (bp.around(1.01) & bp.rows(10, 14))
    elif face == "snout":
        slit = bp.front_poly([(-6, 7), (-1, 8.5), (1, 8.5), (6, 7), (6, 8.6), (1, 10), (-1, 10), (-6, 8.6)])
    elif face == "eyes":
        slit = bp.front_poly([(-5, 7), (-1.5, 8), (-1.5, 9.5), (-4.5, 9.5)]) | bp.front_poly([(5, 7), (1.5, 8), (1.5, 9.5), (4.5, 9.5)])
    if slit.any():
        bp.paint(slit, void, "dark", -2.2, edge=(-0.6, 0, 0.4, 0), ao=0)
        lip = np.roll(slit, 1, axis=0) & ~slit
        bp.shade(lip, 1.5)
        brow = np.roll(slit, -1, axis=0) & ~slit
        bp.shade(brow, -0.8)
        if o.get("eyes"):
            gcol = w.pal.get("glow") or "ffffff"
            em = slit & (bp.y == bp.y[slit].max()) & ((np.abs(np.abs(bp.ax) - 3.0) < 1.01))
            bp.paint(em, gcol, "glow", 1.6, edge=(0, 0, 0, 0), ao=0, glow=1.0)
        if face in ("slit", "cross", "snout"):
            holes = bp.S()
            for x, y in [(x, y) for y in (11, 13) for x in (-4.5, -2.5, 2.5, 4.5)]:
                holes |= (np.abs(bp.ax - x) < 0.5) & (bp.y == y)
            bp.paint(holes, void, "dark", -2.0, edge=(0, 0, 0, 0), ao=0)
            bp.shade(np.roll(holes, 1, axis=0) & ~holes, 1.2)
    if face == "cross":
        cross = (bp.around(1.01) & bp.rows(2, 16)) & ~bp.rows(7, 9) & ~slit
        plate(w, bp, cross, o.get("ridge_key", trim), extra=np.where(bp.ax < 0, 0.6, -0.3), edge=(1.2, 0.8, -1.2, -0.8))
    if face == "bars":
        bars = bp.front_poly([(-6, 6), (6, 6), (6, 14), (-6, 14)]) & ~hole
        bp.shade(bars & (np.mod(bp.ax + 6.0, 3.0) < 0.5), 0.8)
    # hinge plates / cheek seam on the sides
    seam = bp.sides & (np.abs(np.abs(bp.ax) - 13.5) < 0.5) & bp.rows(6, 15) & shell
    bp.shade(seam, -1.0)
    bp.shade(np.roll(seam, 1, axis=1) & bp.sides & shell & bp.rows(6, 15), 0.7)
    if o.get("etch"):
        etch(w, bp, shell & bp.rows(0, 16) & ~bp.back, 2, o.get("etch_key"), glow=o.get("etch_glow", 0.0))
    if o.get("rivets", True):
        y0 = o.get("band_y", 3 if style in ("greathelm", "tower") else 4)
        pts = [(x, y0) for x in (-6.5, 6.5, -13, 13, 20, -20, 26, -26)]
        rivets(w, bp, pts, w.c(trim)[0], w.c(trim)[1])
    if o.get("cheek_rivets") and hole.any():
        rivets(w, bp, [(-7.0, 12), (7.0, 12), (-7.0, 14), (7.0, 14)], w.c(trim)[0], w.c(trim)[1])
    if o.get("aventail"):
        av = bp.rows(12, 16) & ~bp.front
        mail(w, bp, av, o.get("aventail_key", "mail"))
    if o.get("gem"):
        gem(w, bp, 0, o.get("gem_y", 2), w.gem_color(), 2)
    if o.get("emblem"):
        emblem(w, bp, o["emblem"], o.get("emblem_y", 0), o.get("emblem_key", trim), glow=o.get("emblem_glow", 0.0))
    helm_back(w, bp, o, shell, key, trim)
    plate_finish(w, bp, {k: v for k, v in o.items() if k != "etch"}, shell & ~bp.rows(3, 7), 3)


def helm_back(w, bp, o, shell, key, trim):
    """The back of a helm, so it is never a bare slab: a raised spine down the middle, a nape guard of overlapping
    lames that wraps round onto the sides, rivets on the lames and a small crest plate under the band."""
    col, kind = w.c(key)
    if not (shell & bp.back).any():
        return
    y_n = o.get("nape_y", 10)
    wrap = np.abs(bp.bx) < o.get("nape_wrap", 13)
    nape = shell & bp.rows(y_n, 16) & wrap & ~bp.front
    if nape.any():
        h = o.get("nape_lame", 3)
        t = 0.55 * form(bp, nape, kind, spec=0) + lames(bp, nape, h, kind, y0=y_n)
        # the lames flare out: their outer ends a step darker as they turn away
        t = t + np.where(np.abs(bp.bx) > 9, -0.4, 0)
        bp.paint(nape, col, kind, t, edge=(0.9, 0.4, -1.2, -0.6), ao=1.2)
        # the plate above casts a shadow onto the first lame
        bp.shade(shell & (bp.y == y_n - 1) & wrap & ~bp.front, -0.9)
        rows_ = [y for y in range(y_n + 1, 16, h)]
        pts = [(x, y) for y in rows_ for x in (-5.5, 5.5)]
        tc, tk = w.c(trim)
        rivets(w, bp, pts, tc, tk, coord="back")
    # the spine: a raised comb from the crown down to the nape guard
    spine = shell & bp.back & (np.abs(bp.bx) < 1.01) & bp.rows(0, y_n)
    if spine.any():
        sc, sk = w.c(o.get("ridge_key", key))
        bp.paint(spine, sc, sk, np.where(bp.bx < 0, 1.3, -0.5) + np.where(bp.y < 3, 0.4, 0),
                 edge=(1.0, 0.6, -1.0, -0.8), ao=1.0)
        bp.shade(shell & bp.back & (np.abs(bp.bx - 1.5) < 0.5) & bp.rows(0, y_n), -0.6)
    # a keystone plate on the back of the brow band
    y0 = o.get("band_y", 4)
    if o.get("band", True):
        tc, tk = w.c(trim)
        plate_ = shell & bp.back & bp.back_poly([(-2.5, y0 + 2), (2.5, y0 + 2), (2.5, y0 + 4), (0.5, y0 + 5.5), (-0.5, y0 + 5.5), (-2.5, y0 + 4)])
        bp.paint(plate_, tc, tk, np.where(bp.y == y0 + 2, 1.0, 0.2) + np.where(bp.bx < 0, 0.4, -0.2), edge=(0.9, 0.5, -1.1, -0.6), ao=1.3)


def head_hood(w, bp, o):
    """A cloth or leather hood. style: hood (face open), cowl (a mask over the mouth), reaper (the face lost in
    shadow, eyes glowing), veil (a long cloth hood)."""
    style = o.get("style", "hood")
    key = o.get("cloth", "cloth")
    col, kind = w.c(key, "cloth")
    opening = bp.front_poly([(-6, 16), (-6, 8), (-5, 5.5), (-3, 4.2), (3, 4.2), (5, 5.5), (6, 8), (6, 16)])
    shell = ~opening
    t = ak.cloth_folds(bp.H, bp.SW, 11, 6.0, 0.8) + np.where(bp.rows(0, 3), 0.5, 0) + convex(bp, shell, False, 0.6)
    seam = bp.around_back(0.99) | (np.abs(bp.ax) > 30)
    top_seam = np.abs(bp.tx + 0.5 - bp.W / 2) < 1.0
    tt = dome(bp, 10, 6, 0.16, 0.9) + np.where(top_seam, -1.2, 0)
    bp.paint(shell, col, kind, t + np.where(seam & bp.back, -0.8, 0), top=bp.T(True), top_tone=tt)
    # bottom of the hood: open, a hem ring
    ring = ~((bp.tx >= 2) & (bp.tx < bp.W - 2) & (bp.ty >= 2) & (bp.ty < bp.D - 2))
    bp.paint(None, col, kind, bottom=ring, bottom_tone=-1.2, edge=(0, 0, 0, 0))
    # rolled rim round the face
    rim = ak.grow(opening, 2) & ~opening & bp.front
    lcol, lk = w.c(o.get("rim", "lining" if w.has("lining") else key), "cloth")
    rt = np.where(ak.grow(opening, 1) & rim, -0.6, 0.9)
    bp.paint(rim, lcol, lk, rt, edge=(0.8, 0.4, -0.6, -0.3), ao=0.8)
    if style == "cowl":
        mcol, mk = w.c(o.get("mask", "cloth2"), "cloth")
        mask_ = opening & bp.rows(10, 16)
        mask_ |= opening & bp.rows(9, 10) & ~bp.around(1.5)
        mt = np.where(bp.y < 11, 0.6, 0) + ak.cloth_folds(bp.H, bp.SW, 4, 3.0, 0.6, 0.3)
        bp.paint(mask_, mcol, mk, mt, edge=(1.0, 0.3, -0.8, -0.3), ao=1.0)
        if o.get("mask_trim"):
            band(w, bp, 10, 11, o["mask_trim"], where=opening)
    elif style == "reaper":
        void, vk = ("0b0910", "dark")
        bp.paint(opening, void, "dark", np.where(bp.rows(0, 7), -2.6, -2.0), edge=(0, 0, 0, 0), ao=0)
        gcol = w.pal.get("glow") or "ffffff"
        eyes = opening & (bp.y == 9) & (np.abs(np.abs(bp.ax) - 2.5) < 1.01)
        bp.paint(eyes, gcol, "glow", 1.8, edge=(0, 0, 0, 0), ao=0, glow=1.0)
        under = np.roll(eyes, 1, axis=0)
        bp.paint(under & opening, gcol, "glow", -1.0, edge=(0, 0, 0, 0), ao=0, glow=0.5)
    if o.get("circlet"):
        band(w, bp, 3, 5, o["circlet"], where=~opening, pattern="inlay")
        if w.has("gem"):
            gem(w, bp, 0, 2, w.gem_color(), 2)
    if o.get("trim"):
        band(w, bp, 14, 16, o["trim"], where=~bp.front)
    hood_back(w, bp, o, shell, col, kind)
    if o.get("stitch", True):
        st_col = w.pal.get("thread") or "d8c49a"
        path = (bp.around_back(1.5) & ~bp.around_back(0.5)) & bp.rows(1, 15)
        stitches(bp, path, st_col, "cloth", 3)


def hood_back(w, bp, o, shell, col, kind):
    """The back of a hood: the cloth hangs from the peak in folds that fan out and deepen towards the hem, a darker
    gather at the nape, and an embroidered mark when the design has one."""
    back = shell & ~bp.front & (np.abs(bp.bx) < 15)
    if not back.any():
        return
    ang = np.arctan2(bp.bx, bp.y + 7.0)
    ph = ang * o.get("back_folds", 9.0)
    deep = np.clip((bp.y - 2) / 13.0, 0, 1)
    t = (0.9 * np.sin(ph) + 0.3 * np.sin(2 * ph + 1.0)) * (0.35 + 0.65 * deep)
    # the folds' creases: a dark line with a lit ridge beside it, growing from below the crown towards the hem
    m2 = np.mod(ph, 2 * np.pi) - np.pi
    grown = bp.y >= 3 + 2.0 * (1.0 - np.cos(ph * 0.37) ** 2)
    t = t + np.where(grown & (np.abs(m2) < 0.32), -1.5 * (0.5 + 0.5 * deep), 0)
    t = t + np.where(grown & (m2 >= -1.0) & (m2 < -0.32), 0.7, 0)
    t = t + np.where(bp.rows(0, 3), 0.6, 0) - 1.0 * deep * deep
    # rounded: the middle of the back catches the light, the sides turn away
    t = t + 0.7 * np.cos(np.clip(bp.bx / 12.0, -1, 1) * np.pi / 2) - 0.35 + np.where(bp.bx < 0, 0.25, 0)
    bp.paint(back, col, kind, t + np.where(bp.around_back(0.99), -0.7, 0), edge=(0.6, 0.2, -0.9, -0.3), ao=0.9)
    if o.get("back_emblem"):
        ec, ek = w.c(o.get("back_emblem_key", "trim"))
        stamp(bp, o["back_emblem"], 0, o.get("back_emblem_y", 5), ec, ek, 0.4, o.get("back_emblem_glow", 0.0), 1, "back")
    if o.get("trim") is None:
        hem = shell & ~bp.front & bp.rows(14, 16)
        bp.shade(hem & (bp.y == 14), 0.6)
        bp.shade(hem & (bp.y == 15), -0.8)


def head_bone_mask(w, bp, o):
    """A hood (or helm) with a bone mask: skull (the whole face: hollow eyes, cheek ridges, teeth), jaw (only the lower
    face: a fanged jaw over the mouth, eyes free), visage (a smooth mask with narrow eyes). eyes=6 gives a spider's
    six glowing eyes."""
    under = o.get("under", "hood")
    if under == "helm":
        head_helm(w, bp, dict(o, style=o.get("helm_style", "cap"), face="open"))
    else:
        head_hood(w, bp, dict(o, style="hood"))
    bcol, bk = w.c(o.get("bone", "bone"), "bone")
    kind = o.get("mask", "skull")
    void = w.c("dark")[0] if w.has("dark") else "0b0910"
    if kind == "jaw":
        face = bp.front_poly([(-6, 10), (6, 10), (6, 12.5), (3.5, 15.5), (-3.5, 15.5), (-6, 12.5)])
        t = form(bp, face, "bone", amp=0.6, spec=0) + np.where(bp.y == 10, 0.8, 0)
        bp.paint(face, bcol, bk, t, edge=(1.2, 0.6, -1.3, -0.6), ao=1.4)
        fangs = bp.front_poly([(-4.5, 9), (-3.5, 9), (-4, 7.5)]) | bp.front_poly([(3.5, 9), (4.5, 9), (4, 7.5)])
        fangs |= (bp.front & (bp.y == 10) & (np.mod(bp.ax, 2.0) < 1.0) & bp.around(3.0))
        bp.paint(fangs, bcol, bk, 1.6, edge=(0.6, 0.3, -0.8, -0.3), ao=1.0)
        bp.shade(face & bp.rows(12, 13) & bp.around(4.0), -1.2)
        bp.shade(face & bp.rows(12, 13) & bp.around(4.0) & (np.mod(bp.ax, 2.0) < 1.0), 0.8)
    else:
        if kind == "visage":
            face = bp.front_poly([(-6, 4.5), (6, 4.5), (6, 12), (2.5, 15.5), (-2.5, 15.5), (-6, 12)])
        else:
            face = bp.front_poly([(-5.5, 5), (5.5, 5), (5.5, 11), (3.5, 14.5), (-3.5, 14.5), (-5.5, 11)])
        t = form(bp, face, "bone", amp=0.6, spec=0) + np.where(bp.ax < 0, 0.3, -0.2)
        bp.paint(face, bcol, bk, t, edge=(1.2, 0.6, -1.3, -0.6), ao=1.4)
        if kind == "visage":
            eye = bp.front_poly([(-5, 7.5), (-1.5, 8.5), (-1.5, 9.5), (-5, 9)]) | bp.front_poly([(5, 7.5), (1.5, 8.5), (1.5, 9.5), (5, 9)])
        else:
            eye = bp.front_poly([(-4.5, 7), (-1.5, 7.5), (-1.5, 9.5), (-4, 9.5)]) | bp.front_poly([(4.5, 7), (1.5, 7.5), (1.5, 9.5), (4, 9.5)])
        if o.get("eyes") == 6:
            eye = bp.S()
            for ex_, ey in ((-3.5, 7), (3.5, 7), (-1.5, 9), (1.5, 9), (-4.5, 10), (4.5, 10)):
                eye |= (np.floor(bp.ax) == np.floor(ex_)) & bp.rows(ey, ey + 2) | (np.floor(bp.ax) == np.floor(ex_ + 1)) & bp.rows(ey, ey + 2)
            eye &= face
        bp.paint(eye, void, "dark", -2.4, edge=(0, 0, 0, 0), ao=0)
        if w.pal.get("glow"):
            if o.get("eyes") == 6:
                g = eye & np.roll(eye, -1, axis=0)
            else:
                g = eye & (bp.y == 8) & (np.abs(np.abs(bp.ax) - 2.5) < 0.6)
            bp.paint(g, w.pal["glow"], "glow", 1.8, edge=(0, 0, 0, 0), ao=0, glow=1.0)
        if kind == "skull":
            teeth = bp.front_poly([(-3, 12), (3, 12), (3, 13.5), (-3, 13.5)])
            bp.shade(teeth & (np.mod(bp.ax, 2.0) < 1.0), -1.6)
            bp.shade(face & bp.around(0.99) & bp.rows(9, 11), -1.4)
        else:
            bp.shade(face & bp.around(0.99) & bp.rows(5, 12), 0.7)
            stripe = face & bp.rows(12, 15) & (np.abs(np.abs(bp.ax) - 2.5) < 0.6)
            if w.has("cloth2"):
                c2, k2 = w.c("cloth2", "cloth")
                bp.paint(stripe, c2, k2, 0.4, edge=(0, 0, 0, 0), ao=0.4)
    if o.get("crown"):
        tips = bp.rows(0, 4) & (np.abs(np.mod(bp.ax + 2.0, 4.0) - 2.0) < (2.0 - (4 - bp.y) * 0.0)) & bp.rows(0, 4)
        band(w, bp, 3, 5, o.get("bone", "bone"))


def head_bare(w, bp, o):
    """No helmet over the face: only what sits on the head (a hat's crown, a circlet, a headband), the face and hair
    show."""
    key = o.get("cloth", "cloth")
    col, kind = w.c(key)
    rows = o.get("rows", 4)
    m = bp.rows(0, rows)
    if kind in METALS:
        plate(w, bp, m, key)
    else:
        cloth(w, bp, m, key, seed=5, period=4.0, strength=0.7)
    bp.paint(None, col, kind, top=bp.T(True), top_tone=top_form(bp, kind, cy=0.75, amp=0.7))
    if o.get("band"):
        band(w, bp, rows - 2, rows, o["band"], pattern=o.get("band_pattern"))
    if o.get("gem"):
        gem(w, bp, 0, rows - 2, w.gem_color(), 2, glow=o.get("gem_glow", 0.6))


HEADS = {"helm": head_helm, "hood": head_hood, "bone_mask": head_bone_mask, "bare": head_bare}


# hat layer (half a pixel out from the helmet): raised trims
def hat_crown(w, bp, key="trim", points=5, glow=0.0):
    """A crown ring round the top of the head with points standing up."""
    col, kind = w.c(key)
    m = bp.rows(3, 6)
    pts = bp.rows(0, 3) & ((np.abs(((bp.sx + 2) % 8) - 3.5) < 1.6 - bp.y * 0.45))
    t = np.where(bp.y < 3, 1.0, np.where(bp.y == 5, -0.8, 0.3))
    bp.paint(m | pts, col, kind, t, edge=(1.4, 0.6, -1.4, -0.7), ao=1.0)
    if w.has("gem"):
        for cx in (0, -16, 16, 32):
            gem(w, bp, cx, 3, w.gem_color(), 2, glow=glow)


def hat_brim(w, bp, key="metal", y=10):
    col, kind = w.c(key)
    m = bp.rows(y, y + 2)
    bp.paint(m, col, kind, np.where(bp.y == y, 1.2, -0.6), edge=(1.3, 0.5, -1.4, -0.6))


HAT = {"crown": hat_crown, "brim": hat_brim}


# ------------------------------------------------------------------------------------------------- chest: body

def under_layer(w, bp, kind, key=None, rows=None):
    """The layer under the armor that shows between plates: mail, quilted padding, cloth or leather."""
    m = bp.S(True) if rows is None else bp.rows(*rows)
    if kind == "mail":
        mail(w, bp, m, key or "mail")
    elif kind == "gambeson":
        quilt(w, bp, m, key or "cloth2")
    elif kind == "leather":
        leather(w, bp, m, key or "leather", seed=21)
    elif kind == "scale":
        scales(w, bp, m, key or "scale")
    elif kind == "fur":
        fur(w, bp, m, key or "fur")
    else:
        cloth(w, bp, m, key or "cloth", seed=17)
    return m


def body_top(w, bp, key, kind_tone=0.6):
    col, kind = w.c(key)
    bp.paint(None, col, kind, top=bp.T(True), top_tone=dome(bp, 6, 4, 0.2, kind_tone))


def body_plate(w, bp, o):
    """Breastplate and backplate over an under layer, with gorget, belt and faulds or a skirt."""
    under_layer(w, bp, o.get("under", "mail"), o.get("under_key"))
    key = o.get("metal", "metal")
    trim = o.get("trim", "trim")
    shape = o.get("shape", "cuirass")
    low = o.get("plate_bottom", 15)
    wrap = o.get("wrap", 12.5)
    if shape == "pointed":
        bp_front = bp.front_poly([(-wrap, 1), (wrap, 1), (wrap, low - 3), (8, low - 2), (0, low + 1.5), (-8, low - 2), (-wrap, low - 3)])
    elif shape == "round":
        bp_front = bp.front_poly([(-wrap, 1), (wrap, 1), (wrap, low - 3), (8, low - 2), (5, low), (-5, low), (-8, low - 2), (-wrap, low - 3)])
    else:
        bp_front = bp.front_poly([(-wrap, 1), (wrap, 1), (wrap, low), (-wrap, low)])
    bwrap = 24 - wrap
    bp_back = bp.back_poly([(-bwrap, 1), (bwrap, 1), (bwrap, low), (-bwrap, low)])
    plate(w, bp, bp_front, key, ridge=o.get("ridge", True))
    plate(w, bp, bp_back, key, ridge=True)
    body_top(w, bp, key)
    if shape == "plackart":
        lower = bp.front_poly([(-8, 9), (8, 9), (8, low), (-8, low)]) & bp_front
        plate(w, bp, lower & bp.front_poly([(-8, 8), (0, 6), (8, 8), (8, low + 1), (-8, low + 1)]), key, ridge=True)
    # gorget: two lames round the neck
    if o.get("gorget", True):
        g = bp.rows(0, 3)
        t = np.where(bp.y == 0, 1.4, np.where(bp.y == 1, 0.3, -1.0))
        col, kind = w.c(o.get("gorget_key", key))
        bp.paint(g, col, kind, t, edge=EDGE.get(kind))
    if o.get("trim_edge", True):
        # a rolled trim along the breastplate's neckline
        edge_ = bp_front & bp.rows(1, 3) | bp_back & bp.rows(1, 3)
        col, kind = w.c(trim)
        bp.paint(edge_, col, kind, np.where(bp.y == 1, 1.2, -0.5), edge=(1.2, 0.5, -1.2, -0.6))
    # side straps holding the plates
    seam = bp.sides & (np.abs(np.abs(bp.ax) - wrap) < 0.5) & bp.rows(1, low)
    bp.shade(seam, -1.6)
    for yy in (4, 9):
        st = bp.sides & bp.rows(yy, yy + 2) & (np.abs(np.abs(bp.ax) - wrap) < 2.6)
        leather(w, bp, st, o.get("strap_key", "leather"), edge=(0.8, 0.4, -1.2, -0.6))
        buckle(w, bp, wrap, yy, 2, trim)
        buckle(w, bp, -wrap, yy, 2, trim)
    # belt
    by = o.get("belt_y", low)
    if o.get("belt", True):
        band(w, bp, by, by + 3, o.get("belt_key", "leather"), pattern=o.get("belt_pattern"))
        buckle(w, bp, 0, by, 3, trim)
    # below the belt
    skirt = o.get("skirt", "lames")
    below = bp.rows(by + 3, 24)
    if skirt == "lames":
        col, kind = w.c(key)
        bp.paint(below, col, kind, lames(bp, below, 3) + convex(bp, below, False, 0.5), edge=EDGE.get(kind))
    elif skirt == "mail":
        mail(w, bp, below, "mail")
    elif skirt == "scale":
        scales(w, bp, below, "scale" if w.has("scale") else key)
    elif skirt == "cloth":
        cloth(w, bp, below, o.get("skirt_key", "cloth"), seed=8, period=4.0)
    if o.get("tabard"):
        tk = o.get("tabard_key", "cloth")
        tab = bp.front_poly([(-4.5, by + 2.5), (4.5, by + 2.5), (4.5, 24), (-4.5, 24)])
        if o["tabard"] == "long":
            tab = bp.front_poly([(-4.5, 3), (4.5, 3), (4.5, 24), (-4.5, 24)]) & ~bp.rows(by, by + 3)
            tab |= bp.back_poly([(-4.5, 3), (4.5, 3), (4.5, 24), (-4.5, 24)]) & ~bp.rows(by, by + 3)
        if o["tabard"] == "full":
            tab = bp.front_poly([(-5, 0), (-2, 3), (2, 3), (5, 0), (5, 24), (-5, 24)]) & ~bp.rows(by, by + 3)
            tab |= bp.back_poly([(-5, 0), (5, 0), (5, 24), (-5, 24)]) & ~bp.rows(by, by + 3)
        cloth(w, bp, tab, tk, seed=31, period=4.0, strength=0.7, edge=(0.9, 0.4, -1.1, -0.4), ao=1.2)
        if o.get("tabard_trim"):
            edge_ = tab & ~ak.shrink(tab, 1) & ~bp.rows(0, 1)
            col, kind = w.c(o["tabard_trim"])
            bp.paint(edge_ & ~(bp.y == bp.y[tab].min()), col, kind, 0.6, edge=(0.6, 0.3, -0.6, -0.3), ao=0.4)
    if o.get("baldric"):
        bal = bp.S()
        # from the right shoulder (front) down to the left hip, and up the back
        for k in range(24):
            bal |= (np.abs(bp.ax - (-9 + k * 0.75)) < 1.6) & (bp.y == k) & ~bp.back
            bal |= (np.abs(bp.bx - (9 - k * 0.75)) < 1.6) & (bp.y == k) & bp.back
        leather(w, bp, bal & bp.rows(0, by), o.get("baldric_key", "leather"), edge=(0.9, 0.5, -1.2, -0.6))
        stitches(bp, bal & bp.rows(0, by) & ~ak.shrink(bal, 1), w.pal.get("thread") or "d8c49a", "cloth", 2)
    em = o.get("emblem")
    if em:
        ey = o.get("emblem_y", 5)
        emblem(w, bp, em, ey, o.get("emblem_key", trim), glow=o.get("emblem_glow", 0.0))
    if o.get("gem"):
        gem(w, bp, 0, o.get("gem_y", 4), w.gem_color(), 2)
    if o.get("runes"):
        rr = bp.S()
        for k in range(o.get("runes", 3)):
            rr |= (np.abs(bp.ax - (-5 + k * 5)) < 0.5) & bp.rows(6, 11)
            rr |= (np.abs(bp.ax - (-5 + k * 5) - 0.5 * ((bp.y.astype(int) % 2) * 2 - 1)) < 0.5) & bp.rows(8, 9)
        glow_line(w, bp, rr & bp_front, "glow")
    plate_finish(w, bp, o, bp_front | bp_back, 5)


def body_robe(w, bp, o):
    """A long robe: open front with trims, a high collar, a mantle over the shoulders, a sash."""
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.S(True), key, seed=13, period=5.0, strength=0.9)
    body_top(w, bp, o.get("mantle_key", key))
    trim = o.get("trim", "trim")
    # front opening with trim bands
    opening = bp.around(0.99) & bp.rows(4, 24)
    col, kind = w.c(o.get("inner", "cloth2" if w.has("cloth2") else key), "cloth")
    bp.paint(opening, col, kind, -1.4, edge=(0, 0, 0, 0), ao=0)
    edges = (bp.around(3.01) & ~bp.around(0.99)) & bp.rows(3, 24)
    tc, tk = w.c(trim)
    tt = np.where(np.abs(bp.ax) < 2, 0.8, -0.4) + np.where((bp.y.astype(int) % 4) == 0, 1.0, 0)
    bp.paint(edges, tc, tk, tt, edge=(1.0, 0.5, -1.0, -0.5), ao=1.2)
    # mantle / capelet
    mk = o.get("mantle_key", "cloth2" if w.has("cloth2") else key)
    mrow = o.get("mantle_rows", 8)
    mant = bp.rows(0, mrow) | (bp.rows(mrow, mrow + 2) & ((np.abs(bp.ax) % 4) < 2))
    mc, mkk = w.c(mk, "cloth")
    bp.paint(mant, mc, mkk, ak.cloth_folds(bp.H, bp.SW, 41, 3.0, 0.6) + vband(bp, mant, "cloth") * 0.8,
             edge=(0.8, 0.4, -1.2, -0.5), ao=1.3)
    hem = mant & ~ak.shrink(mant, 1) & (bp.y >= 4)
    bp.paint(hem & (bp.y >= mrow - 1), tc, tk, 0.6, edge=(0.6, 0.3, -0.8, -0.3), ao=0.6)
    # high collar
    if o.get("collar", True):
        coll = bp.rows(0, 3) & ~bp.around(2.0)
        bp.paint(coll, tc, tk, np.where(bp.y == 0, 1.3, -0.3), edge=(1.2, 0.5, -1.2, -0.6))
    # sash
    sy = o.get("sash_y", 14)
    sk = o.get("sash_key", "cloth2" if w.has("cloth2") else trim)
    sc, skk = w.c(sk, "cloth")
    sash = bp.rows(sy, sy + 3)
    bp.paint(sash, sc, skk, np.where(bp.y == sy, 0.8, np.where(bp.y == sy + 2, -0.8, 0)) + ak.cloth_folds(bp.H, bp.SW, 3, 3, 0.4, 0.6),
             edge=(0.9, 0.4, -1.1, -0.5), ao=1.2)
    knot = bp.front_poly([(-6, sy - 0.5), (-3, sy - 0.5), (-3.5, sy + 8), (-4.5, sy + 9), (-6, sy + 8)])
    bp.paint(knot, sc, skk, np.where(bp.y > sy + 3, -0.4, 0.6) + np.where(bp.ax < -4.5, 0.5, -0.2), edge=(0.9, 0.5, -1.1, -0.6), ao=1.3)
    if o.get("gem"):
        gem(w, bp, 0, sy, w.gem_color(), 3)
    em = o.get("emblem")
    if em:
        emblem(w, bp, em, o.get("emblem_y", 9), trim, cx=o.get("emblem_x", 5.0), glow=o.get("emblem_glow", 0.0))
        emblem(w, bp, em, o.get("emblem_y", 9) + 1, trim, cx=0.0, face="back", glow=o.get("emblem_glow", 0.0))
    if o.get("runes"):
        rr = bp.S()
        for k in range(6):
            rr |= (np.abs(bp.ax - (-6.5 + k * 2.6)) < 0.5) & (bp.y == 21 + (k % 2))
        glow_line(w, bp, rr, "glow")
    if o.get("hem", True):
        band(w, bp, 22, 24, trim, pattern="zig" if o.get("ornate") else None)


def body_leather(w, bp, o):
    """A leather jerkin: wrap-over front, stitched edges, straps and buckles, a scarf or collar, a belt."""
    under_layer(w, bp, o.get("under", "cloth"), o.get("under_key"))
    key = o.get("leather", "leather")
    vest = bp.S(True) & ~(bp.sides & bp.rows(0, 6) & (np.abs(np.abs(bp.ax) - 12) < 2.5))
    leather(w, bp, vest, key, seed=29)
    body_top(w, bp, key, 0.4)
    # the front flap: diagonal overlap from the right shoulder to the left side
    flap = bp.front_poly([(-8, 0), (-3, 0), (6, 13), (8, 13), (8, 24), (-8, 24)])
    lc, lk = w.c(o.get("flap_key", key), "leather")
    bp.paint(flap & bp.front, lc, lk, ak.leather_tone(bp.H, bp.SW, 4) + 0.3 + convex(bp, flap, False, 0.6), edge=(0.9, 0.5, -1.2, -0.6), ao=1.4)
    edge_ = flap & ~ak.shrink(flap, 1) & bp.front & ~bp.rows(23, 24)
    stitches(bp, ak.shrink(flap, 1) & ~ak.shrink(flap, 2) & bp.front, w.pal.get("thread") or "d8c49a", "cloth", 2)
    # straps across
    for (x0, y0, x1, y1) in o.get("straps", [(-8, 4, 8, 9)]):
        st = bp.S()
        n = 24
        for k in range(n + 1):
            xx = x0 + (x1 - x0) * k / n
            yy = y0 + (y1 - y0) * k / n
            st |= (np.abs(bp.ax - xx) < 0.6) & (np.abs(bp.y + 0.5 - yy) < 1.1) & bp.front
        sc, sk = w.c(o.get("strap_key", "leather2" if w.has("leather2") else key), "leather")
        bp.paint(st, sc, sk, 0.0, edge=(0.9, 0.4, -1.2, -0.5), ao=1.4)
    # belt and buckle, pouches
    by = o.get("belt_y", 15)
    band(w, bp, by, by + 3, o.get("belt_key", "leather2" if w.has("leather2") else key), pattern="studs" if o.get("studs") else None)
    buckle(w, bp, 0, by, 3, o.get("trim", "trim"))
    for cx in (-11.5, 13.0):
        pouch = (np.abs(bp.ax - cx) < 2.2) & bp.rows(by + 1, by + 6) & bp.sides
        leather(w, bp, pouch, o.get("pouch_key", key), seed=7, edge=(1.0, 0.5, -1.2, -0.6))
        bp.shade(pouch & (bp.y == by + 2), -1.0)
    # collar or scarf
    if o.get("scarf"):
        sc, sk = w.c(o["scarf"], "cloth")
        scarf = bp.rows(0, 4) | bp.front_poly([(1, 3), (4, 3), (3.5, 10), (2, 11), (1.5, 9)])
        bp.paint(scarf, sc, sk, ak.cloth_folds(bp.H, bp.SW, 9, 3, 0.7, 0.5) + np.where(bp.y == 0, 0.8, 0), edge=(0.9, 0.4, -1.2, -0.5), ao=1.4)
    # sheath on the chest strap
    if o.get("knife"):
        kn = bp.front_poly([(-6.5, 3), (-4.5, 3), (-4, 10), (-5.5, 11), (-7, 10)])
        dc, dk = w.c("dark") if w.has("dark") else ("1c1820", "dark")
        bp.paint(kn, dc, dk, np.where(bp.ax < -5.5, 0.6, -0.4), edge=(1.0, 0.5, -1.0, -0.5), ao=1.4)
        pom = bp.front_poly([(-6.3, 1), (-4.7, 1), (-4.7, 3), (-6.3, 3)])
        tc, tk = w.c(o.get("trim", "trim"))
        bp.paint(pom, tc, tk, 1.0, edge=(1.0, 0.5, -1.0, -0.5), ao=1.0)
    # lacing on the sides
    lace = bp.sides & (np.abs(np.abs(bp.ax) - 12) < 0.6) & bp.rows(1, 14) & ((bp.y.astype(int) % 3) != 2)
    bp.paint(lace, w.pal.get("thread") or "d8c49a", "cloth", 0.8, edge=(0, 0, 0, 0), ao=0.6)
    em = o.get("emblem")
    if em:
        emblem(w, bp, em, o.get("emblem_y", 2), o.get("emblem_key", "trim"), cx=0.0, face="back", glow=o.get("emblem_glow", 0.0))
    if o.get("glow_seams"):
        gs = edge_ & bp.rows(2, 13)
        glow_line(w, bp, gs, "glow", 0.9)


def body_vestment(w, bp, o):
    """A healer's vestment: layered robe, a stole down both sides of the front, a rope belt, gold trims."""
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.S(True), key, seed=19, period=6.0, strength=0.7)
    body_top(w, bp, key, 0.7)
    trim = o.get("trim", "trim")
    tc, tk = w.c(trim)
    # tabard panel front and back
    pk = o.get("panel_key", "cloth2" if w.has("cloth2") else key)
    pc, pkk = w.c(pk, "cloth")
    panel = bp.front_poly([(-4.5, 3), (4.5, 3), (4.5, 24), (-4.5, 24)]) | bp.back_poly([(-4.5, 1), (4.5, 1), (4.5, 24), (-4.5, 24)])
    bp.paint(panel, pc, pkk, ak.cloth_folds(bp.H, bp.SW, 23, 4, 0.5) + convex(bp, panel, False, 0.5), edge=(0.9, 0.4, -1.1, -0.5), ao=1.3)
    pe = panel & ~ak.shrink(panel, 1) & ~bp.rows(0, 4)
    bp.paint(pe, tc, tk, 0.5, edge=(0.6, 0.3, -0.6, -0.3), ao=0.5)
    # stole: two bands from the shoulders down the front
    sk = o.get("stole_key", "cloth2" if w.has("cloth2") else trim)
    sc, skk = w.c(sk, "cloth")
    stole = (np.abs(np.abs(bp.ax) - 6.5) < 1.6) & bp.rows(0, 24) & bp.front
    stole |= (np.abs(np.abs(bp.bx) - 6.5) < 1.6) & bp.rows(0, 8) & bp.back
    bp.paint(stole, sc, skk, np.where(np.abs(bp.ax) < 6.5, 0.5, -0.3) + np.where(bp.y > 21, -0.6, 0), edge=(0.9, 0.5, -1.1, -0.6), ao=1.3)
    marks = stole & bp.front & (bp.y.astype(int) % 6 == 3) & (np.abs(np.abs(bp.ax) - 6.5) < 0.6)
    bp.paint(marks, tc, tk, 1.2, edge=(0, 0, 0, 0), ao=0.5)
    # neckline and rope belt
    neck = bp.rows(0, 2)
    bp.paint(neck, tc, tk, np.where(bp.y == 0, 1.3, -0.4), edge=(1.2, 0.5, -1.2, -0.6))
    by = o.get("belt_y", 14)
    rope = bp.rows(by, by + 2)
    bp.paint(rope, tc, tk, np.where(((bp.sx + bp.y.astype(int)) % 3) == 0, 1.2, np.where(((bp.sx + bp.y.astype(int)) % 3) == 2, -0.8, 0.2)),
             edge=(0.9, 0.4, -1.1, -0.5), ao=1.2)
    tassel = bp.front_poly([(-7.5, by + 1), (-6, by + 1), (-6, by + 6), (-7.5, by + 6)]) | bp.front_poly([(6, by + 1), (7.5, by + 1), (7.5, by + 5), (6, by + 5)])
    bp.paint(tassel, tc, tk, np.where(bp.y > by + 3, -0.6, 0.6), edge=(0.8, 0.4, -1.0, -0.5), ao=1.0)
    em = o.get("emblem", "sun")
    if em:
        emblem(w, bp, em, o.get("emblem_y", 5), trim, glow=o.get("emblem_glow", 0.0))
    if o.get("gem"):
        gem(w, bp, 0, o.get("gem_y", 8), w.gem_color(), 2)
    band(w, bp, 22, 24, trim, pattern="inlay" if o.get("ornate") else None)


BODIES = {"plate": body_plate, "robe": body_robe, "leather": body_leather, "vestment": body_vestment}


# ------------------------------------------------------------------------------------------------- chest: arms

def arm_top(w, bp, key, base=0.8):
    col, kind = w.c(key)
    bp.paint(None, col, kind, top=bp.T(True), top_tone=dome(bp, 4.5, 3.0, 0.25, base))


def arm_plate(w, bp, o):
    """Plate arm: a pauldron of lames on the box itself (pauldron_rows; 0 when an extra pauldron sits on top, then a
    rerebrace covers the upper arm), a couter at the elbow, a vambrace and a cuff. The under layer shows on the inner
    side and between the plates."""
    under_layer(w, bp, o.get("sleeve", o.get("under", "mail")), o.get("sleeve_key", o.get("under_key")))
    key = o.get("metal", "metal")
    trim = o.get("trim", "trim")
    col, kind = w.c(key)
    tc, tk = w.c(trim)
    pr = o.get("pauldron_rows", 9) or 0
    if pr:
        pa = bp.rows(0, pr) & ~(bp.inner & bp.rows(4, 24))
        pa &= ~(bp.outer & (bp.y >= pr - 1) & (np.abs(bp.ax + 8) > 2.5))
        bp.paint(pa, col, kind, lames(bp, pa, 3, curve=-1.0) * 0.9 + form(bp, pa, kind, amp=0.7, vert=0.3), edge=EDGE.get(kind))
        rim = pa & ~ak.shrink(pa, 1) & (bp.y >= 2) & ~bp.inner
        bp.paint(rim & (bp.y >= pr - 2), tc, tk, 0.4, edge=(0.6, 0.3, -1.0, -0.4), ao=0.8)
    else:
        pa = bp.rows(1, 11) & ~(bp.inner & bp.rows(3, 24))
        plate(w, bp, pa, key, edge=EDGE.get(kind))
        bp.shade(pa & (bp.y == 6), -1.4)
        bp.shade(pa & (bp.y == 7), 0.8)
    arm_top(w, bp, key)
    # couter: a rounded elbow plate with a fan on the outside
    el = (bp.around_back(5.0) | (bp.outer & (np.abs(bp.bx) < 9.5))) & bp.rows(11, 15)
    el |= bp.outer & bp.rows(10, 16) & (np.abs(bp.ax + 8) < 2.6)
    plate(w, bp, el, key, extra=np.where(bp.y == 11, 0.5, 0), edge=EDGE.get(kind), ao=1.4)
    rivets(w, bp, [(-8.0, 13)], tc, tk)
    # vambrace
    vb = bp.rows(15, 22) & ~bp.inner
    plate(w, bp, vb, key, edge=EDGE.get(kind), ao=1.4)
    straps = bp.inner & (bp.rows(16, 17) | bp.rows(19, 20))
    leather(w, bp, straps, o.get("strap_key", "leather"), edge=(0.6, 0.3, -1.0, -0.4))
    band(w, bp, 21, 24, o.get("cuff_key", trim), where=bp.S(True))
    if o.get("arm_glow"):
        gl = bp.front & (np.abs(bp.ax) < 0.6) & bp.rows(16, 20)
        glow_line(w, bp, gl, "glow")
    plate_finish(w, bp, dict(o, etch=False), vb | pa | el, 9)


def arm_robe(w, bp, o):
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.S(True), key, seed=37, period=4.0, strength=0.8)
    trim = o.get("trim", "trim")
    tc, tk = w.c(trim)
    mk = o.get("mantle_key", "cloth2" if w.has("cloth2") else key)
    mc, mkk = w.c(mk, "cloth")
    mrow = o.get("mantle_rows", 8) - 1
    mant = bp.rows(0, mrow) & ~(bp.inner & bp.rows(3, 24))
    mant |= bp.rows(mrow, mrow + 2) & ((np.abs(bp.ax) % 4) < 2) & ~bp.inner
    bp.paint(mant, mc, mkk, ak.cloth_folds(bp.H, bp.SW, 43, 3.0, 0.6) + vband(bp, mant, "cloth") * 0.8, edge=(0.8, 0.4, -1.2, -0.5), ao=1.3)
    arm_top(w, bp, mk, 0.5)
    hem = mant & ~ak.shrink(mant, 1) & (bp.y >= mrow - 1)
    bp.paint(hem, tc, tk, 0.6, edge=(0.6, 0.3, -0.8, -0.3), ao=0.6)
    # wide cuff
    ck = o.get("cuff_key", "cloth2" if w.has("cloth2") else key)
    cc, ckk = w.c(ck, "cloth")
    cuff = bp.rows(17, 24)
    bp.paint(cuff, cc, ckk, ak.cloth_folds(bp.H, bp.SW, 47, 3.0, 0.5) + np.where(bp.y == 17, 0.8, 0), edge=(1.0, 0.4, -1.1, -0.5), ao=1.4)
    band(w, bp, 17, 18, trim)
    band(w, bp, 22, 24, trim, pattern="zig" if o.get("ornate") else None)
    if o.get("runes"):
        rr = bp.front & (bp.y == 20) & ((bp.sx % 3) == 0)
        glow_line(w, bp, rr, "glow")


def arm_leather(w, bp, o):
    sl = o.get("sleeve", "cloth")
    under_layer(w, bp, sl, o.get("sleeve_key"))
    key = o.get("leather", "leather")
    guard = bp.rows(0, 7) & ~(bp.inner & bp.rows(2, 24))
    guard &= ~(bp.outer & (bp.y == 6) & (np.abs(bp.ax + 8) > 2))
    leather(w, bp, guard, key, seed=51, extra=lames(bp, guard, 4) * 0.5)
    arm_top(w, bp, key, 0.5)
    st = guard & ~ak.shrink(guard, 1) & (bp.y >= 1)
    stitches(bp, ak.shrink(guard, 1) & ~ak.shrink(guard, 2), w.pal.get("thread") or "d8c49a", "cloth", 2)
    if o.get("wraps"):
        wc, wk = w.c(o["wraps"], "cloth")
        wr = bp.rows(9, 14) & (((bp.sx + bp.y.astype(int)) % 4) < 3)
        bp.paint(wr, wc, wk, np.where(((bp.sx + bp.y.astype(int)) % 4) == 0, 0.8, 0), edge=(0.6, 0.3, -1.0, -0.4), ao=1.0)
    br = bp.rows(14, 22)
    bk = o.get("bracer_key", "leather2" if w.has("leather2") else key)
    leather(w, bp, br, bk, seed=53, extra=np.where(bp.y == 14, 0.6, 0))
    for yy in (16, 19):
        s = bp.rows(yy, yy + 1)
        bc, bkk = w.c(o.get("strap_key", "leather"), "leather")
        bp.paint(s, bc, bkk, -0.4, edge=(0.4, 0.2, -1.0, -0.4), ao=1.2)
        buckle(w, bp, -8.0, yy - 1, 3, o.get("trim", "trim"))
    if o.get("studs"):
        rivets(w, bp, [(x, 21) for x in (-10, -6, -2, 2, 14)], w.c(o.get("trim", "trim"))[0], w.c(o.get("trim", "trim"))[1])
    band(w, bp, 22, 24, o.get("cuff_key", key))
    if o.get("arm_glow"):
        gl = bp.outer & (np.abs(bp.ax + 8) < 0.6) & bp.rows(15, 21)
        glow_line(w, bp, gl, "glow")


def arm_vestment(w, bp, o):
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.S(True), key, seed=59, period=4.0, strength=0.7)
    arm_top(w, bp, key, 0.6)
    trim = o.get("trim", "trim")
    band(w, bp, 0, 2, trim)
    ck = o.get("cuff_key", "cloth2" if w.has("cloth2") else key)
    cc, ckk = w.c(ck, "cloth")
    cuff = bp.rows(16, 24)
    bp.paint(cuff, cc, ckk, ak.cloth_folds(bp.H, bp.SW, 61, 3.0, 0.5), edge=(1.0, 0.4, -1.1, -0.5), ao=1.4)
    band(w, bp, 16, 17, trim)
    band(w, bp, 22, 24, trim, pattern="inlay" if o.get("ornate") else None)
    if o.get("bracer"):
        plate(w, bp, bp.rows(18, 22) & ~bp.inner, o["bracer"])


ARMS = {"plate": arm_plate, "robe": arm_robe, "leather": arm_leather, "vestment": arm_vestment}


# ------------------------------------------------------------------------------------------------- leggings

def waist_plate(w, bp, o):
    """The leggings' hips on the inner model's body box: a belt and hip plates (the chestplate covers the rest)."""
    key = o.get("metal", "metal")
    under_layer(w, bp, o.get("under", "mail"), o.get("under_key"), rows=(15, 24))
    band(w, bp, 15, 18, o.get("belt_key", "leather"))
    buckle(w, bp, 0, 15, 3, o.get("trim", "trim"))
    hip = bp.rows(18, 24) & ~bp.around(3.0)
    col, kind = w.c(key)
    bp.paint(hip, col, kind, lames(bp, hip, 3) + convex(bp, hip, False, 0.5), edge=EDGE.get(kind))
    bc, bk = w.c(o.get("under_key") or ("leather" if o.get("under") == "leather" else "mail"))
    bp.paint(None, bc, bk, bottom=bp.T(True), bottom_tone=-1.5, edge=(0, 0, 0, 0))


def waist_cloth(w, bp, o):
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.rows(15, 24), key, seed=71, period=4.0)
    band(w, bp, 15, 18, o.get("belt_key", "cloth2" if w.has("cloth2") else "leather"))
    if o.get("buckle", True):
        buckle(w, bp, 0, 15, 3, o.get("trim", "trim"))
    col, kind = w.c(key, "cloth")
    bp.paint(None, col, kind, bottom=bp.T(True), bottom_tone=-1.5, edge=(0, 0, 0, 0))


def waist_leather(w, bp, o):
    key = o.get("leather", "leather")
    leather(w, bp, bp.rows(15, 24), key, seed=73)
    band(w, bp, 15, 18, o.get("belt_key", "leather2" if w.has("leather2") else key), pattern="studs" if o.get("studs") else None)
    buckle(w, bp, 0, 15, 3, o.get("trim", "trim"))
    col, kind = w.c(key, "leather")
    bp.paint(None, col, kind, bottom=bp.T(True), bottom_tone=-1.5, edge=(0, 0, 0, 0))


WAISTS = {"plate": waist_plate, "cloth": waist_cloth, "leather": waist_leather, "robe": waist_cloth, "vestment": waist_cloth}


def leg_plate(w, bp, o):
    under_layer(w, bp, o.get("under", "mail"), o.get("under_key"))
    key = o.get("metal", "metal")
    col, kind = w.c(key)
    cuisse = bp.rows(0, 10) & (bp.around(9.0) | bp.outer)
    bp.paint(cuisse, col, kind, lames(bp, cuisse, 5) * 0.7 + convex(bp, cuisse, False, 0.7) + vband(bp, cuisse, kind) * 0.5,
             edge=EDGE.get(kind))
    knee = bp.rows(10, 14) & bp.around(5.5)
    plate(w, bp, knee, o.get("knee_key", key), extra=np.where(bp.y == 10, 0.6, 0), edge=EDGE.get(kind), ao=1.6)
    rivets(w, bp, [(0, 12)], w.c(o.get("trim", "trim"))[0], w.c(o.get("trim", "trim"))[1])
    greave = bp.rows(14, 24) & ~bp.inner
    plate(w, bp, greave, key, edge=EDGE.get(kind))
    st = (bp.back | bp.inner) & (bp.rows(3, 4) | bp.rows(7, 8))
    leather(w, bp, st, o.get("strap_key", "leather"), edge=(0.5, 0.2, -1.0, -0.4))
    plate_finish(w, bp, dict(o, etch=False), (cuisse | greave) & ~st, 11)


def leg_leather(w, bp, o):
    key = o.get("leather", "leather")
    leather(w, bp, bp.S(True), key, seed=81)
    k2 = o.get("pad_key", "leather2" if w.has("leather2") else key)
    pad = bp.rows(9, 15) & bp.around(4.5)
    leather(w, bp, pad, k2, seed=83, extra=np.where(bp.y == 9, 0.6, 0), edge=(1.0, 0.5, -1.3, -0.6))
    stitches(bp, pad & ~ak.shrink(pad, 1), w.pal.get("thread") or "d8c49a", "cloth", 2)
    seam = bp.outer & (np.abs(bp.ax + 8) < 0.6)
    bp.shade(seam, -1.0)
    lace = bp.outer & (np.abs(bp.ax + 8) < 1.6) & bp.rows(1, 9) & ((bp.y.astype(int) % 3) == 0)
    bp.paint(lace, w.pal.get("thread") or "d8c49a", "cloth", 0.6, edge=(0, 0, 0, 0), ao=0.6)
    for yy in o.get("straps_y", (3,)):
        s = bp.rows(yy, yy + 2) & ~bp.inner
        sc, sk = w.c(o.get("strap_key", "leather2" if w.has("leather2") else key), "leather")
        bp.paint(s, sc, sk, np.where(bp.y == yy, 0.4, -0.6), edge=(0.6, 0.3, -1.1, -0.5), ao=1.3)
    if o.get("wraps"):
        wc, wk = w.c(o["wraps"], "cloth")
        wr = bp.rows(15, 24) & (((bp.sx + bp.y.astype(int)) % 4) < 3)
        bp.paint(wr, wc, wk, np.where(((bp.sx + bp.y.astype(int)) % 4) == 0, 0.8, 0), edge=(0.6, 0.3, -1.0, -0.4), ao=1.0)


def leg_cloth(w, bp, o):
    key = o.get("cloth", "cloth")
    cloth(w, bp, bp.S(True), key, seed=89, period=3.5, strength=0.8)
    if o.get("panel"):
        pc, pk = w.c(o["panel"], "cloth")
        pnl = bp.around(3.5) & bp.rows(0, 24)
        bp.paint(pnl, pc, pk, ak.cloth_folds(bp.H, bp.SW, 91, 3, 0.4), edge=(0.8, 0.4, -1.0, -0.5), ao=1.2)
        tc, tk = w.c(o.get("trim", "trim"))
        bp.paint(pnl & ~ak.shrink(pnl, 1) & (bp.y > 0), tc, tk, 0.5, edge=(0.5, 0.2, -0.5, -0.2), ao=0.5)
    if o.get("knee_key"):
        plate(w, bp, bp.rows(10, 14) & bp.around(3.5), o["knee_key"])


LEGS = {"plate": leg_plate, "leather": leg_leather, "cloth": leg_cloth}


# ------------------------------------------------------------------------------------------------- boots

def boot_sole(w, bp, key="leather", top=12):
    col, kind = w.c(key, "leather")
    sole = bp.rows(22, 24)
    bp.paint(sole, col, kind, np.where(bp.y == 22, 0.3, -1.6), edge=(0.5, 0.2, -1.0, -0.3), ao=1.4)
    bp.paint(None, col, kind, bottom=bp.T(True), bottom_tone=-1.4 + np.where(bp.ty < 3, -0.6, 0), edge=(0, 0, 0, 0))


def boot_plate(w, bp, o):
    top = o.get("top", 11)
    key = o.get("metal", "metal")
    col, kind = w.c(key)
    under_layer(w, bp, o.get("under", "leather"), o.get("under_key", "leather"), rows=(top, 24))
    greave = bp.rows(top, 20) & ~bp.inner
    plate(w, bp, greave, key, ridge=True)
    foot = bp.rows(19, 22) & (bp.front | bp.sides)
    bp.paint(foot, col, kind, lames(bp, foot, 1.5 if False else 2) * 0.8 + 0.3, edge=EDGE.get(kind))
    band(w, bp, top, top + 2, o.get("cuff_key", o.get("trim", "trim")))
    st = bp.inner & (bp.rows(14, 15) | bp.rows(17, 18))
    leather(w, bp, st, "leather", edge=(0.5, 0.2, -1.0, -0.4))
    boot_sole(w, bp, o.get("sole", "leather"))
    plate_finish(w, bp, dict(o, etch=False), greave, 13)
    if o.get("glow_line"):
        glow_line(w, bp, bp.front & (np.abs(bp.ax) < 0.6) & bp.rows(top + 3, 19), "glow")


def boot_leather(w, bp, o):
    top = o.get("top", 12)
    key = o.get("leather", "leather")
    leather(w, bp, bp.rows(top, 24), key, seed=97, extra=np.where(bp.rows(20, 22), 0.4, 0))
    ck = o.get("cuff_key", "leather2" if w.has("leather2") else key)
    if o.get("fold", True):
        fold = bp.rows(top, top + 3) | (bp.rows(top + 3, top + 4) & ((bp.sx % 4) < 2))
        cc, cki = w.c(ck, "fur" if ck == "fur" else "leather")
        if ck == "fur":
            fur(w, bp, fold, ck)
        else:
            bp.paint(fold, cc, cki, np.where(bp.y == top, 1.0, 0.0) + ak.leather_tone(bp.H, bp.SW, 5), edge=(0.9, 0.4, -1.2, -0.5), ao=1.4)
    if o.get("laces", True):
        lc = w.pal.get("thread") or "d8c49a"
        lm = bp.front & bp.rows(top + 4, 21) & (((np.abs(bp.ax) - 0.5) + (bp.y % 2)) < 1.5) & (np.abs(bp.ax) < 2)
        bp.paint(lm, lc, "cloth", 0.8, edge=(0.3, 0.1, -0.6, -0.2), ao=0.8)
    for yy in o.get("straps_y", (16,)):
        s = bp.rows(yy, yy + 2) & ~bp.front
        sc, sk = w.c(o.get("strap_key", "leather2" if w.has("leather2") else key), "leather")
        bp.paint(s, sc, sk, np.where(bp.y == yy, 0.4, -0.6), edge=(0.6, 0.3, -1.1, -0.5), ao=1.3)
        buckle(w, bp, -8.0, yy - 0.5, 3, o.get("trim", "trim"))
    if o.get("shin"):
        plate(w, bp, bp.rows(top + 3, 20) & bp.around(3.0), o["shin"], ridge=True)
    boot_sole(w, bp, o.get("sole", "leather2" if w.has("leather2") else key))


def boot_wraps(w, bp, o):
    top = o.get("top", 10)
    key = o.get("cloth", "cloth")
    wc, wk = w.c(key, "cloth")
    wr = bp.rows(top, 21)
    t = np.where(((bp.sx + bp.y.astype(int)) % 5) == 0, -1.2, np.where(((bp.sx + bp.y.astype(int)) % 5) == 1, 0.8, 0.0))
    bp.paint(wr, wc, wk, t + vband(bp, wr, "cloth") * 0.5, edge=(0.7, 0.3, -1.0, -0.4))
    if o.get("shin"):
        plate(w, bp, bp.rows(top + 1, 19) & bp.around(3.0), o["shin"], ridge=True)
    lk = o.get("leather", "leather")
    shoe = bp.rows(20, 22)
    leather(w, bp, shoe, lk, seed=101)
    band(w, bp, top, top + 1, o.get("trim", "trim"))
    boot_sole(w, bp, lk)


def boot_fur(w, bp, o):
    boot_leather(w, bp, dict(o, fold=False, laces=False, straps_y=o.get("straps_y", (17,))))
    top = o.get("top", 11)
    fm = bp.rows(top, top + 4) | (bp.rows(top + 4, top + 5) & (ak.hash01(bp.sx, 0, 3) > 0.45))
    fur(w, bp, fm, o.get("fur_key", "fur"))


BOOTS = {"plate": boot_plate, "leather": boot_leather, "wraps": boot_wraps, "fur": boot_fur}


# ------------------------------------------------------------------------------------------------- extra parts

def X(w, name, part, origin, size, pivot=None, rotation=(0, 0, 0), painter=None, twin=False, slot=None, inflate=0.0, **opts):
    """Add an extra part. twin: also add its mirror image on the other side of a central part (head, body)."""
    b = rig.extra(name, part, origin, size, inflate, pivot, rotation, slot=slot)
    w.add(b, painter, **opts)
    if twin:
        t = rig.extra(name + "_l", part, (-origin[0] - size[0], origin[1], origin[2]), size, inflate,
                      None if pivot is None else (-pivot[0], pivot[1], pivot[2]), (rotation[0], -rotation[1], -rotation[2]), slot=slot)
        t.mirror = True
        t.twin_of = b
        w.add(t, None)
    return b


def paint_box_metal(w, bp, key="metal", lame=0, trim=None, rim_rows=0, under=-2.2, top_tone=None, ridge=False, glow=0.0):
    col, kind = w.c(key)
    m = bp.S(True)
    t = form(bp, m, kind, amp=0.9, spec=1.0 if bp.H >= 5 else 0.0)
    if ridge:
        t = t + ridge_tone(bp, m)
    if lame:
        t = t * 0.6 + lames(bp, m, lame, curve=1.0)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=top_tone if top_tone is not None else top_form(bp, kind),
             bottom=bp.T(True), bottom_tone=under, edge=EDGE.get(kind), glow=glow)
    if trim and rim_rows:
        band(w, bp, bp.H - rim_rows, bp.H, trim)


def p_pauldron(w, bp, key="metal", trim="trim", lame=4, rivet=True, gem_=False, spikes=False, fur_key=None, glow=False):
    paint_box_metal(w, bp, key, lame=lame)
    if trim:
        band(w, bp, bp.H - 2, bp.H, trim, pattern="inlay" if gem_ else None)
        col, kind = w.c(trim)
        # a raised rim round the top face's edge
        rim = ~((bp.tx >= 1) & (bp.tx < bp.W - 1) & (bp.ty >= 1) & (bp.ty < bp.D - 1))
        bp.paint(None, col, kind, top=rim, top_tone=np.where(bp.tx < bp.W / 2, 1.2, 0.2), edge=(0.8, 0.4, -0.8, -0.4))
    if rivet:
        rv = (bp.y == bp.H - 4) & ((bp.sx % 5) == 2) & ~bp.inner
        tc, tk = w.c(trim or key)
        bp.paint(rv, tc, tk, 1.8, edge=(0.4, 0.2, -1.4, -0.8), ao=1.4)
    if gem_ and w.has("gem"):
        gem(w, bp, -bp.W / 2 - bp.D / 2, 2, w.gem_color(), 2)
    if glow and w.has("glow"):
        gl = bp.outer & (bp.y == bp.H - 4) & (np.abs(bp.ax + bp.W / 2 + bp.D / 2) < 3)
        glow_line(w, bp, gl, "glow")
    if fur_key:
        fm = bp.rows(bp.H - 3, bp.H) & ~bp.inner
        fur(w, bp, fm | (bp.rows(bp.H - 4, bp.H - 3) & (ak.hash01(bp.sx, 1, 9) > 0.5)), fur_key)


def p_cloth_box(w, bp, key="cloth", trim=None, inside=None, emblem_=None, fold=4.0, hem=True, glow_key=None):
    col, kind = w.c(key, "cloth")
    m = bp.S(True)
    v = (bp.y + 0.5) / max(1, bp.H)
    t = ak.cloth_folds(bp.H, bp.SW, 7, fold * 1.4, 0.75, 0.15) + np.interp(v, [0, 0.15, 0.6, 1], [0.7, 0.3, -0.1, -0.7])
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=0.4, bottom=bp.T(True), bottom_tone=-1.0, edge=(0.6, 0.3, -1.0, -0.4))
    if inside:
        ic, ik = w.c(inside, "cloth")
        bp.paint(bp.front, ic, ik, ak.cloth_folds(bp.H, bp.SW, 9, fold, 0.6) - 0.6, edge=(0.4, 0.2, -0.8, -0.3), ao=0.5)
    if trim and hem:
        band(w, bp, bp.H - 2, bp.H, trim)
    if emblem_:
        emblem(w, bp, emblem_, max(1, bp.H // 3), trim or key, cx=0.0, face="back", glow=0.8 if glow_key else 0.0)


def p_cape(w, bp, key="cloth", trim="trim", lining=None, emblem_=None, ragged=False):
    p_cloth_box(w, bp, key, trim, lining or key, None, 3.5)
    if emblem_:
        emblem(w, bp, emblem_, 6, trim, cx=0.0, face="back", glow=0.0)
    if ragged:
        cut = bp.rows(bp.H - 3, bp.H) & (ak.hash01(bp.sx // 2, 0, 5) > 0.5)
        cut |= bp.rows(bp.H - 1, bp.H) & (ak.hash01(bp.sx, 2, 7) > 0.4)
        bp.erase(cut)


def p_plume(w, bp, key="cloth", base_key=None, fur_=True):
    col, kind = w.c(key, "fur")
    m = bp.S(True)
    t = ak.fur_tone(bp.H, bp.SW, 13) * 0.8 + np.where(bp.y < 2, 0.8, np.where(bp.y > bp.H - 3, -0.8, 0))
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=ak.hash01(bp.tx.astype(int), bp.ty.astype(int), 3) * 1.6 - 0.2,
             bottom=bp.T(True), bottom_tone=-1.5, edge=(0.9, 0.4, -0.9, -0.4))
    notch = (bp.y == 0) & (ak.hash01(bp.sx, 0, 17) > 0.55)
    bp.erase(notch)
    if base_key:
        band(w, bp, bp.H - 1, bp.H, base_key)


def p_horn(w, bp, key="bone", tip=False, rings=True):
    col, kind = w.c(key, "bone")
    m = bp.S(True)
    # base end (the inner, -x side once placed) darker, tip lighter
    t = vband(bp, m, "bone") * 0.7 + (0.5 if tip else -0.2)
    if rings:
        t = t + np.where((bp.sx % 4) == 0, -0.8, 0) if False else t
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=1.0 if tip else 0.5, bottom=bp.T(True), bottom_tone=-1.6, edge=(1.3, 0.6, -1.3, -0.6))
    if rings and not tip:
        ring = bp.S()
        ring[:, :] = (bp.lx % 3 == 0) & (bp.front | bp.back)
        bp.shade(ring, -0.9)


def p_feathers(w, bp, key="trim", key2=None, membrane=False):
    """A wing on a thin box: its two big side faces carry the wing, cut to a wing's outline (the root at the box's
    front edge, the tip at its back). membrane: a drake's leathery wing between finger bones with a scalloped
    trailing edge; otherwise a bird's wing, small covert feathers along the top and long flight feathers below."""
    col, kind = w.c(key)
    D, H = max(1, bp.D), bp.H
    side = bp.outer | bp.inner
    lxf = (bp.lx + 0.5) / D
    r = np.where(bp.outer, 1.0 - lxf, lxf)              # 0 at the root, 1 at the tip
    v = (bp.y + 0.5) / H
    k2c, k2k = w.c(key2 or "trim")
    if membrane:
        third = np.mod(r * 3.0, 1.0)
        edge = 1.0 - 0.78 * r - 0.16 * np.sin(np.pi * third)
        cut = side & (v > edge)
        m = bp.S(True) & ~cut
        # veins fan out from the wrist (at the top, a third of the way out)
        wx, wy = 0.34 * D, 0.5
        px, py = r * D, v * H
        ang = np.arctan2(py - wy, px - wx)
        fingers = np.zeros(bp.H * bp.SW, bool).reshape(bp.H, bp.SW)
        for a in (1.95, 1.35, 0.85, 0.42):
            d = np.abs(np.sin(ang - a)) * np.hypot(px - wx, py - wy)
            fingers |= (d < 0.7) & (np.cos(ang - a) > 0)
        arm = v * H < 1.6
        bone = side & (fingers | arm) & m
        t = -0.4 + 0.9 * (1 - v) - 0.6 * r + np.where(ak.grow(bone, 1) & ~bone, 0.5, 0)
        bp.paint(m, col, "leather", t, top=bp.T(True), top_tone=0.4, bottom=bp.T(True), bottom_tone=-1.0, edge=(0.8, 0.4, -1.0, -0.4))
        bp.paint(bone | (m & ~side), k2c, k2k, np.where(arm, 1.0, 0.4) + np.where(bp.y == 0, 0.6, 0), edge=(0.8, 0.4, -1.0, -0.5), ao=1.2)
        bp.erase(cut | (bp.back & (v > 0.24)), bottom=bp.T(True))
        return
    # bird wing: outline with a toothed trailing edge
    teeth = np.mod(bp.lx, 3) / 3.0
    edge = 1.0 - 0.42 * r - 0.07 * teeth
    cut = side & (v > edge)
    m = bp.S(True) & ~cut
    coverts = side & (v < 0.38 - 0.1 * r)
    # flight feathers: long strips 3px wide, lit on one edge, a dark shaft
    k = np.mod(bp.lx, 3)
    t = np.where(k == 0, 0.9, np.where(k == 1, 0.2, -0.9)) - 0.5 * v
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=0.9, bottom=bp.T(True), bottom_tone=-0.6, edge=(1.0, 0.4, -1.0, -0.4))
    ct = ak.scale_tone(bp.H, bp.SW, 3) * 0.8 + 0.6
    ckey = key2 or key
    cc, ck = w.c(ckey)
    bp.paint(coverts & m, cc, ck, ct, edge=(0.8, 0.4, -1.0, -0.4), ao=1.2)
    bp.erase(cut | (bp.back & (v > 0.6)), bottom=bp.T(True))


def p_fur_box(w, bp, key="fur", jag=True):
    col, kind = w.c(key, "fur")
    m = bp.S(True)
    t = ak.fur_tone(bp.H, bp.SW, 23) + vband(bp, m, "leather") * 0.7
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=ak.fur_tone(max(1, bp.D), max(1, bp.W), 3) * 0.7 + 0.6,
             bottom=bp.T(True), bottom_tone=-1.5, edge=(0.9, 0.3, -0.9, -0.3))
    if jag:
        bp.erase(bp.rows(bp.H - 1, bp.H) & (ak.hash01(bp.sx, 3, 29) > 0.5))


def p_halo(w, bp, key="glow"):
    col, kind = w.c(key, "glow")
    cx, cy = bp.W / 2.0, bp.H / 2.0
    lx = bp.lx + 0.5
    r = np.hypot(lx - cx, (bp.y + 0.5 - cy))
    ring = (bp.front | bp.back) & (r <= bp.W / 2.0) & (r >= bp.W / 2.0 - 2.2)
    t = np.where(r > bp.W / 2.0 - 1.0, 0.4, 1.6)
    bp.paint(ring, col, "glow", t, edge=(0, 0, 0, 0), ao=0, glow=1.0)


def p_snout(w, bp, fur_key="fur", tooth="bone"):
    p_fur_box(w, bp, fur_key, jag=False)
    col, kind = w.c(fur_key, "fur")
    nose = bp.front & bp.rows(0, 3) & bp.around(1.6)
    bp.paint(nose, "1a1418", "dark", np.where(bp.y == 0, 1.0, -0.5), edge=(0.4, 0.2, -0.4, -0.2), ao=0.8)
    eye_ = bp.sides & (bp.y == 1) & (bp.lx > bp.D - 4) & (bp.lx < bp.D - 1) if False else None
    tc, tk = w.c(tooth, "bone")
    teeth = (bp.front | bp.sides) & (bp.y == bp.H - 1) & ((bp.sx % 2) == 0)
    teeth &= (bp.front | (bp.sides & (bp.lx < 4)) | (bp.sides & (bp.lx >= bp.D - 4)))
    bp.paint(teeth, tc, tk, 1.2, edge=(0.4, 0.2, -0.8, -0.2), ao=0.6)
    fang = bp.front & bp.rows(bp.H - 2, bp.H) & (np.abs(np.abs(bp.ax) - bp.W / 2 + 1.5) < 0.6)
    bp.paint(fang, tc, tk, 1.6, edge=(0.4, 0.2, -0.8, -0.2), ao=0.6)


def p_ear(w, bp, fur_key="fur", inner="leather"):
    p_fur_box(w, bp, fur_key, jag=False)
    ic, ik = w.c(inner, "leather")
    bp.paint(bp.front & bp.rows(2, bp.H) & bp.around(bp.W / 2 - 0.9), ic, ik, -0.6, edge=(0.3, 0.1, -0.6, -0.2), ao=1.0)
    bp.erase(bp.rows(0, 1) & ~bp.around(1.0) & ~bp.around_back(1.0))


def p_crystal(w, bp, key="crystal", glow=0.5):
    col, kind = w.c(key, "crystal")
    m = bp.S(True)
    t = ak.facet_tone(m, 31)[:, :] * 0.8 + np.where(bp.y < 2, 0.8, 0)
    bp.paint(m, col, "crystal", t, top=bp.T(True), top_tone=1.8, bottom=bp.T(True), bottom_tone=-0.5, edge=(1.4, 0.6, -0.8, -0.4), glow=glow)


def p_quiver(w, bp, key="leather", feather="cloth2"):
    leather(w, bp, bp.S(True), key, seed=5)
    col, kind = w.c(key, "leather")
    bp.paint(None, col, kind, bottom=bp.T(True), bottom_tone=-1.0, edge=(0, 0, 0, 0))
    band(w, bp, 2, 4, "trim")
    band(w, bp, bp.H - 3, bp.H - 1, "trim")
    fc, fk = w.c(feather, "cloth")
    fl = bp.rows(0, 2) & ((bp.sx % 3) != 2)
    bp.paint(fl, fc, fk, np.where(bp.y == 0, 1.2, 0.0), edge=(0.6, 0.3, -0.6, -0.3), ao=0.6)
    bp.paint(None, fc, fk, top=(bp.tx.astype(int) + bp.ty.astype(int)) % 2 == 0, top_tone=0.8, edge=(0, 0, 0, 0))


def p_spike(w, bp, key="metal"):
    col, kind = w.c(key)
    m = bp.S(True)
    t = np.where(bp.ax < 0, 0.8, -0.6) + np.where(bp.y < 1, 1.0, 0)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=2.0, bottom=bp.T(True), bottom_tone=-2.0, edge=(1.2, 0.6, -1.2, -0.6))


def p_toe(w, bp, key="metal", trim=None, sole=None):
    """A toe cap (also used for knee cops): steel with a ridge, or for soft boots the rounded leather toe of a shoe;
    with a sole, its lowest unit is the boot's sole."""
    col, kind = w.c(key)
    if kind in ("leather", "cloth", "fur"):
        m = bp.S(True)
        leather(w, bp, m, key, seed=5, extra=np.where(bp.y == 0, 0.8, 0) + np.where(bp.front, 0.3, 0))
        bp.paint(None, col, kind, top=bp.T(True), top_tone=dome(bp, bp.D * 0.3, bp.W * 0.35, 0.3, 0.9), edge=(0, 0, 0, 0))
    else:
        paint_box_metal(w, bp, key, ridge=True, top_tone=dome(bp, bp.D * 0.3, bp.W * 0.35, 0.35, 1.2))
    if trim:
        band(w, bp, 0, 1, trim)
    if sole:
        sk_ = ("leather2" if w.has("leather2") else "leather") if sole == "auto" else sole
        sc, sk = w.c(sk_, "leather")
        rows_ = bp.rows(bp.H - 2, bp.H)
        bp.paint(rows_, sc, sk, np.where(bp.y == bp.H - 2, 0.2, -1.6), edge=(0.4, 0.2, -1.0, -0.3), ao=1.2)
        bp.paint(None, sc, sk, bottom=bp.T(True), bottom_tone=-1.8, edge=(0, 0, 0, 0))


def p_tasset(w, bp, key="metal", trim="trim", lame=3, kind_=None):
    if kind_ == "scale":
        scales(w, bp, bp.S(True), "scale" if w.has("scale") else key)
    elif kind_ == "cloth":
        p_cloth_box(w, bp, key, trim)
        return
    elif kind_ == "leather":
        leather(w, bp, bp.S(True), key, extra=lames(bp, bp.S(True), lame) * 0.6)
    else:
        paint_box_metal(w, bp, key, lame=lame)
    col, kind = w.c(key)
    bp.paint(None, col, kind, bottom=bp.T(True), bottom_tone=-2.4, edge=(0, 0, 0, 0))
    bp.erase(None, top=bp.T(True))
    if trim:
        band(w, bp, bp.H - 1, bp.H, trim)


def p_skirt(w, bp, key="cloth", trim="trim", panel=None):
    col, kind = w.c(key, "cloth")
    m = bp.S(True)
    t = ak.cloth_folds(bp.H, bp.SW, 15, 3.0, 0.9) + vband(bp, m, "cloth") * 0.6
    bp.paint(m, col, kind, t, bottom=bp.T(True), bottom_tone=-2.2, edge=(0.6, 0.3, -1.0, -0.4))
    if panel:
        pc, pk = w.c(panel, "cloth")
        pn = bp.around(2.5) & bp.rows(0, bp.H)
        bp.paint(pn, pc, pk, ak.cloth_folds(bp.H, bp.SW, 17, 3.0, 0.4), edge=(0.7, 0.3, -1.0, -0.4), ao=1.2)
    if trim:
        band(w, bp, bp.H - 2, bp.H, trim)


def p_collar(w, bp, key="trim", inner=None, fur_=False):
    if fur_:
        p_fur_box(w, bp, key)
        return
    paint_box_metal(w, bp, key, lame=0)
    bp.erase(None, top=bp.T(True))


def p_brim(w, bp, key="metal", trim=None):
    """A hat's or kettle helm's brim: a wide flat ring seen mostly from above and below (its middle is cut away where
    the head is)."""
    col, kind = w.c(key)
    u = (bp.tx + 0.5) / bp.W - 0.5
    v = (bp.ty + 0.5) / bp.D - 0.5
    r = np.maximum(np.abs(u), np.abs(v))
    inner = r < 0.30
    tt = np.where(r > 0.45, -0.2, 0.6) + np.where((u < 0) & (v > 0), 0.5, 0) + np.where((u > 0) & (v < 0), -0.6, 0)
    tt = tt + np.where((r > 0.30) & (r < 0.34), -1.0, 0)
    bp.paint(bp.S(True), col, kind, np.where(bp.ax < 0, 0.5, -0.3), top=~inner, top_tone=tt, bottom=~inner,
             bottom_tone=-1.6 + np.where(r > 0.44, 0.6, 0), edge=(0.8, 0.4, -0.8, -0.4))
    if trim:
        tc, tk = w.c(trim)
        ring = (r > 0.45) & (r < 0.5)
        bp.paint(None, tc, tk, top=ring, top_tone=np.where(u + v < 0, 1.0, -0.2), edge=(0, 0, 0, 0))


def p_leg_seg(w, bp, key="scale"):
    col, kind = w.c(key)
    m = bp.S(True)
    t = np.where(bp.ax < 0, 0.6, -0.4) + np.where(np.mod(bp.y, 6) < 1, -1.2, 0) + np.where(np.mod(bp.y, 6) == 1, 0.8, 0)
    bp.paint(m, col, kind, t, top=bp.T(True), top_tone=1.2, bottom=bp.T(True), bottom_tone=-1.0, edge=(0.8, 0.4, -0.8, -0.4))


PAINTERS = dict(leg_seg=p_leg_seg, brim=p_brim, pauldron=p_pauldron, cloth=p_cloth_box, cape=p_cape, plume=p_plume, horn=p_horn, feathers=p_feathers,
                fur=p_fur_box, halo=p_halo, snout=p_snout, ear=p_ear, crystal=p_crystal, quiver=p_quiver, spike=p_spike,
                toe=p_toe, tasset=p_tasset, skirt=p_skirt, collar=p_collar, metal=paint_box_metal)


def _ex(painter_name):
    return lambda w, bp, **o: PAINTERS[painter_name](w, bp, **o)


# --- the catalogue of extra parts: each adds boxes with a painter

def ex_pauldrons(w, style="round", key="metal", trim="trim", size=1.0, tilt=-10, gem_=False, spikes=0, fur_key=None, lame=4,
                 glow=False, layers=1):
    s = size
    sz = (round(6.5 * s * 2) / 2, round(5.5 * s * 2) / 2, round(7.5 * s * 2) / 2)
    ox = 1.75 - sz[0]
    X(w, "pauldron", "right_arm", (ox, -3.75 - (sz[1] - 5.5) * 0.5, -sz[2] / 2), sz, (0, -2, 0), (0, 0, tilt),
      _ex("pauldron"), key=key, trim=trim, lame=lame, gem_=gem_, fur_key=fur_key, glow=glow)
    if layers >= 2:
        sz2 = (sz[0] - 0.5, 2.5, sz[2] - 0.5)
        X(w, "pauldron2", "right_arm", (ox - 0.25, 1.25, -sz2[2] / 2), sz2, (0, -2, 0), (0, 0, tilt - 10),
          _ex("pauldron"), key=key, trim=None, lame=0, rivet=False)
    for k in range(spikes):
        zz = (-1.5 + 3 * k / max(1, spikes - 1)) if spikes > 1 else 0
        X(w, "pspike%d" % k, "right_arm", (-1, -4, -0.75), (1.5, 4, 1.5), (ox + 1.5, -3.5, zz), (0, 0, 30 + tilt), _ex("spike"),
          key=trim if trim else key)


def ex_fur_mantle(w, key="fur"):
    X(w, "mantle", "body", (-6, -1.75, -3.75), (12, 4, 7.5), painter=_ex("fur"), key=key)


def ex_collar(w, key="trim", high=3.5):
    X(w, "collar", "body", (-5, -high, -3.5), (10, high, 7), painter=_ex("collar"), key=key)


def ex_cape(w, key="cloth", trim="trim", lining=None, emblem_=None, length=17, ragged=False):
    X(w, "cape", "body", (-4.5, 0, 0), (9, length, 0.5), (0, 0, 3.0), (5, 0, 0), _ex("cape"), key=key, trim=trim,
      lining=lining, emblem_=emblem_, ragged=ragged)


def ex_tabard(w, key="cloth", trim="trim", emblem_=None, length=7, back=True):
    X(w, "tabard", "body", (-3, 0, -0.5), (6, length, 0.5), (0, 11.5, -3.0), (-4, 0, 0), _ex("cloth"), key=key, trim=trim,
      emblem_=None, fold=3.0)
    if back:
        X(w, "tabard_b", "body", (-3, 0, 0), (6, length, 0.5), (0, 11.5, 3.0), (4, 0, 0), _ex("cloth"), key=key, trim=trim, fold=3.0)


def ex_plume(w, key="cloth", base=None, tall=3.0):
    X(w, "plume", "head", (-0.75, -tall, -4.5), (1.5, tall, 9), (0, -9, 0), (6, 0, 0), _ex("plume"), key=key, base_key=base)


def ex_brim(w, key="metal", trim=None, width=2.5, y=-5.0):
    """A wide brim round the head at brow height (kettle helms, hats)."""
    side = 8 + 2 * width
    X(w, "brim", "head", (-side / 2, y, -side / 2), (side, 0.5, side), painter=_ex("brim"), key=key, trim=trim)


def ex_wizard_hat(w, key="cloth", trim="trim", band=None, droop=1.0, brim=2.5):
    """A tall pointed hat: a brim, then a cone of shrinking boxes that droops back."""
    ex_brim(w, key=key, trim=None, width=brim, y=-7.25)
    X(w, "hat1", "head", (-4.25, -11.5, -4.25), (8.5, 4.5, 8.5), painter=_ex("cloth"), key=key, trim=band or trim, hem=bool(band or trim),
      fold=4.0)
    a2, a3, a4 = -14 * droop, -34 * droop, -58 * droop
    p2 = (0.0, -11.5, 0.0)
    X(w, "hat2", "head", (-3.0, -4.0, -3.0), (6, 4, 6), p2, (a2, 0, 0), _ex("cloth"), key=key, hem=False, fold=3.0)
    r2 = math.radians(a2)
    p3 = (0.0, p2[1] - 4 * math.cos(r2), p2[2] + 4 * math.sin(-r2))
    X(w, "hat3", "head", (-2.0, -3.5, -2.0), (4, 3.5, 4), p3, (a3, 0, 0), _ex("cloth"), key=key, hem=False, fold=3.0)
    r3 = math.radians(a3)
    p4 = (0.0, p3[1] - 3.5 * math.cos(r3), p3[2] + 3.5 * math.sin(-r3))
    X(w, "hat4", "head", (-1.0, -3.0, -1.0), (2, 3, 2), p4, (a4, 0, 0), _ex("cloth"), key=key, hem=False, fold=3.0)


def ex_scarf(w, key="cloth", length=9, trim=None):
    """Two scarf tails hanging down the back from the neck."""
    X(w, "scarf1", "body", (-1.0, 0, 0), (2, length, 0.5), (-1.25, 1.0, 2.6), (8, 0, 6), _ex("cloth"), key=key, trim=trim, hem=bool(trim),
      fold=3.0)
    X(w, "scarf2", "body", (-1.0, 0, 0), (2, length - 2.5, 0.5), (1.0, 1.0, 2.7), (12, 0, -10), _ex("cloth"), key=key, trim=trim,
      hem=bool(trim), fold=3.0)


def ex_spider_legs(w, key="scale", n=2, length=9.0):
    """Long jointed legs rising from the back like a spider's."""
    for k in range(n):
        y = 2.5 + k * 3.0
        X(w, "sleg%d" % k, "body", (-0.5, -length, -0.5), (1, length, 1), (-2.0, y, 2.6), (-35 + k * 12, -20, -50 + k * 22),
          _ex("leg_seg"), twin=True, key=key)


def ex_crest(w, key="metal"):
    X(w, "crest", "head", (-0.5, -2.5, -5), (1, 2.5, 10), (0, -9, 0), (0, 0, 0), _ex("metal"), key=key)


def ex_horns(w, key="bone", style="bull", size=1.0):
    s = size
    if style == "bull":
        X(w, "horn1", "head", (-3.5 * s, -1.25, -1.25), (3.5 * s, 2.5, 2.5), (-5, -6.5, -0.5), (0, 10, 18), _ex("horn"), twin=True, key=key)
        X(w, "horn2", "head", (-3, -1, -1), (3, 2, 2), (-8.0 * s, -8.0, -1.0), (0, 18, 55), _ex("horn"), twin=True, key=key)
        X(w, "horn3", "head", (-2, -0.75, -0.75), (2, 1.5, 1.5), (-9.5 * s, -10.5, -1.5), (0, 20, 80), _ex("horn"), twin=True, key=key, tip=True)
    elif style == "ram":
        X(w, "horn1", "head", (-2, -1.5, -1.5), (2, 3, 3), (-5, -6.5, 0.5), (0, 0, 0), _ex("horn"), twin=True, key=key)
        X(w, "horn2", "head", (-1.5, -1, -3.5), (2, 3, 3), (-6.5, -5.5, 0.5), (0, 0, 0), _ex("horn"), twin=True, key=key)
        X(w, "horn3", "head", (-1.5, 0, -4.5), (1.5, 2.5, 2.0), (-6.5, -4.0, -0.5), (0, 0, 0), _ex("horn"), twin=True, key=key, tip=True)
    elif style == "up":
        X(w, "horn1", "head", (-1.25, -3, -1.25), (2.5, 3, 2.5), (-3.5, -9, -1), (-10, 0, -12), _ex("horn"), twin=True, key=key)
        X(w, "horn2", "head", (-1, -3, -1), (2, 3, 2), (-4.2, -11.8, -0.4), (-22, 0, -18), _ex("horn"), twin=True, key=key)
        X(w, "horn3", "head", (-0.75, -2.5, -0.75), (1.5, 2.5, 1.5), (-5.0, -14.2, 0.6), (-35, 0, -22), _ex("horn"), twin=True, key=key, tip=True)


def ex_wings(w, key="trim", key2=None, membrane=False, size=1.0):
    s = size
    X(w, "wing", "head", (-0.5, -7 * s, 0), (0.5, round(8 * s * 2) / 2, round(6 * s * 2) / 2), (-5, -4.5, -1.5), (0, -28, -16),
      _ex("feathers"), twin=True, key=key, key2=key2, membrane=membrane)


def ex_back_wings(w, key="trim", key2=None, membrane=False, size=1.2, spread=68, lift=24):
    """Wings spreading out sideways from the upper back (seen face on from behind), clear of the pauldrons."""
    h, l = round(12 * size * 2) / 2, round(9 * size * 2) / 2
    X(w, "bwing", "body", (-0.25, -h * 0.6, 0), (0.5, h, l), (-1.0, 3.0, 5.0), (0, -spread, lift), _ex("feathers"), twin=True, key=key,
      key2=key2, membrane=membrane)


def ex_wolf(w, fur_key="fur", tooth="bone", inner="leather"):
    X(w, "snout", "head", (-2.5, 0, -4.5), (5, 3, 4.5), (0, -10, -4.0), (8, 0, 0), _ex("snout"), fur_key=fur_key, tooth=tooth)
    X(w, "ear", "head", (-1.25, -3.5, -0.75), (2.5, 3.5, 1.5), (-3.25, -9, 0.5), (-8, 0, -12), _ex("ear"), twin=True,
      fur_key=fur_key, inner=inner)


def ex_ears(w, fur_key="fur", inner="leather"):
    X(w, "ear", "head", (-1.25, -3.5, -0.75), (2.5, 3.5, 1.5), (-3.25, -9, 0.5), (-8, 0, -12), _ex("ear"), twin=True,
      fur_key=fur_key, inner=inner)


def ex_halo(w, key="glow"):
    X(w, "halo", "head", (-5, -10, 0), (10, 10, 0.5), (0, -6.5, 6.0), (-12, 0, 0), _ex("halo"), key=key)


def ex_hood_tip(w, key="cloth"):
    X(w, "hoodtip", "head", (-2.5, -1, 0), (5, 3, 4), (0, -9, 1), (-30, 0, 0), _ex("cloth"), key=key, hem=False)


def ex_crystals(w, key="crystal", n=3, where="head"):
    if where == "head":
        spots = [(-3, -9.5, -1.5, 0, 0, -15), (0, -10.5, 0, 0, 0, 0), (3, -9.5, -1.5, 0, 0, 15)][:n]
        for k, (x, y, z, rx, ry, rz) in enumerate(spots):
            X(w, "cry%d" % k, "head", (-1, -3.5 if k == 1 else -2.5, -1), (2, 3.5 if k == 1 else 2.5, 2), (x, y, z), (rx, ry, rz),
              _ex("crystal"), key=key)
    else:
        X(w, "pcry", "right_arm", (-1, -3.5, -1), (2, 3.5, 2), (-2.5, -3.5, 0), (0, 0, 20), _ex("crystal"), key=key)


def ex_quiver(w, key="leather", feather="cloth2"):
    X(w, "quiver", "body", (-1.25, -5, 0), (2.5, 11, 2.5), (1.5, 5, 3.0), (0, 0, 28), _ex("quiver"), key=key, feather=feather)


def ex_spine(w, key="bone", n=3):
    for k in range(n):
        X(w, "spine%d" % k, "body", (-0.75, -2.5, 0), (1.5, 2.5, 2), (0, 2.5 + k * 3.5, 3.0), (-35, 0, 0), _ex("spike"), key=key)


def ex_tassets(w, key="metal", trim="trim", length=5, kind_=None):
    X(w, "tasset", "right_leg", (-2.75, -0.5, -2.75), (5.5, length, 5.5), slot="legs", painter=_ex("tasset"), key=key, trim=trim,
      kind_=kind_)


def ex_skirt(w, key="cloth", trim="trim", length=9, panel=None):
    X(w, "skirt", "right_leg", (-2.75, -0.5, -2.75), (5.5, length, 5.5), slot="legs", painter=_ex("skirt"), key=key, trim=trim,
      panel=panel)


def ex_knee(w, key="metal"):
    X(w, "knee", "right_leg", (-1.75, 4.75, -3.25), (3.5, 3, 1), slot="legs", painter=_ex("toe"), key=key)


def ex_toes(w, key="metal", trim=None, long=1.5, sole="auto"):
    X(w, "toe", "right_leg", (-2.25, 10.5, -2.9 - long), (4.5, 2.5, long), slot="feet", painter=_ex("toe"), key=key, trim=trim,
      sole=sole)


def ex_boot_cuff(w, key="trim", y=5.5, fur_=False):
    X(w, "bcuff", "right_leg", (-3.25, y, -3.25), (6.5, 2, 6.5), slot="feet", painter=_ex("collar" if not fur_ else "fur"),
      key=key, **({"fur_": True} if False else {}))


def ex_boot_spikes(w, key="metal"):
    X(w, "bspike", "right_leg", (-1, -0.75, -0.75), (2.5, 1.5, 1.5), (-2.9, 8.5, 0), (0, 0, -20), _ex("spike"), slot="feet", key=key)


def ex_heel_wings(w, key="trim", key2=None):
    X(w, "hwing", "right_leg", (-0.25, -3, 0), (0.5, 3.5, 3.5), (-2.9, 9.5, 0.5), (0, -30, -20), _ex("feathers"), slot="feet",
      key=key, key2=key2)


EXTRAS = dict(spider_legs=ex_spider_legs, wizard_hat=ex_wizard_hat, scarf=ex_scarf, brim=ex_brim, pauldrons=ex_pauldrons, fur_mantle=ex_fur_mantle, collar=ex_collar, cape=ex_cape, tabard=ex_tabard, plume=ex_plume,
              crest=ex_crest, horns=ex_horns, wings=ex_wings, back_wings=ex_back_wings, wolf=ex_wolf, ears=ex_ears, halo=ex_halo,
              hood_tip=ex_hood_tip, crystals=ex_crystals, quiver=ex_quiver, spine=ex_spine, tassets=ex_tassets, skirt=ex_skirt,
              knee=ex_knee, toes=ex_toes, boot_cuff=ex_boot_cuff, boot_spikes=ex_boot_spikes, heel_wings=ex_heel_wings)
