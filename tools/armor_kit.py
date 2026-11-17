"""
Pixel-art kit for the armory's armor, shared by the worn layers (tools/armor_worn.py) and the item icons
(tools/armor_icons.py), both driven by the set designs in tools/gen_armory_assets.py.

What it does differently from the first armor art (tools/hd_armor.py), following what human texture artists do
(Faithful 32x, Spryzeen's Knight Armor, Armory Conglomery and the usual pixel-art shading guides):

- Every colour becomes a 7-step ramp built in OKLab: shadows turn cooler and keep their saturation, highlights turn
  warmer and paler, so shading is never plain darkening (hue shifting). Metal ramps run from near black to near white
  (high contrast), cloth and leather ramps stay matte.
- The canvas stores a ramp and a tone step per pixel instead of RGB. Edge light, contact shadows and wear move pixels
  along their own ramp, so every set keeps a small clean palette. No grain noise anywhere.
- Light comes from the top left. Each plate gets a bright top/left rim and a dark bottom/right rim; polished metal
  gets banded reflections (bright band, dark band, bounce light) instead of pillow shading.
- A piece painted over another darkens the pixels just below and right of it (contact shadow), so layers separate.
- Materials are drawn as patterns: chainmail rings, quilted padding, scales, fur tufts, cloth folds, leather creases
  and stitches, crystal facets, molten cracks.

Needs: numpy, pillow.
"""
import math

import numpy as np
from PIL import Image

# ------------------------------------------------------------------------------------------------- colour

def hexc(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def _to_lin(c):
    c = np.asarray(c, float) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _to_srgb(l):
    l = np.clip(l, 0, 1)
    return np.where(l <= 0.0031308, 12.92 * l, 1.055 * np.power(l, 1 / 2.4) - 0.055) * 255.0


def oklab(rgb):
    r, g, b = _to_lin(rgb[:3])
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l_, m_, s_ = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return (0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_,
            1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_,
            0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_)


def _lab_to_lin(L, a, b):
    l_ = L + 0.3963377774 * a + 0.2158037573 * b
    m_ = L - 0.1055613458 * a - 0.0638541728 * b
    s_ = L - 0.0894841775 * a - 1.2914855480 * b
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return np.array([4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                     -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                     -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s])


def oklch(rgb):
    L, a, b = oklab(rgb)
    return float(L), float(math.hypot(a, b)), math.degrees(math.atan2(b, a)) % 360


def from_oklch(L, C, h):
    """sRGB (0-255 ints) for an OKLCH colour, chroma reduced until it fits the gamut."""
    hr = math.radians(h)
    lo, hi = 0.0, C
    lin = _lab_to_lin(L, C * math.cos(hr), C * math.sin(hr))
    if not ((lin >= -1e-4).all() and (lin <= 1 + 1e-4).all()):
        for _ in range(22):
            mid = (lo + hi) / 2
            lin = _lab_to_lin(L, mid * math.cos(hr), mid * math.sin(hr))
            if (lin >= -1e-4).all() and (lin <= 1 + 1e-4).all():
                lo = mid
            else:
                hi = mid
        lin = _lab_to_lin(L, lo * math.cos(hr), lo * math.sin(hr))
    return tuple(int(round(v)) for v in _to_srgb(lin))


def _toward(h, target, amount):
    """Turn hue h towards target by up to `amount` degrees, the short way round."""
    d = (target - h + 540) % 360 - 180
    step = max(-abs(amount), min(abs(amount), d))
    return (h + step) % 360


# How far each material's ramp reaches: shadow fractions of the way to the floor, highlight fractions of the way to
# the ceiling (tones -1..-3 and +1..+3), chroma kept in shadows / highlights, hue turn per step.
KINDS = {
    #            floor ceil  shadow fracs        light fracs         chroma shadows      chroma lights       cool warm
    "metal":    (0.10, 0.985, (0.30, 0.58, 0.82), (0.26, 0.55, 0.86), (1.05, 1.05, 0.90), (0.80, 0.55, 0.25), 7, 7),
    "gold":     (0.12, 0.985, (0.26, 0.52, 0.78), (0.24, 0.50, 0.82), (1.10, 1.05, 0.85), (0.95, 0.75, 0.40), 0, 6),
    "cloth":    (0.09, 0.90, (0.27, 0.52, 0.76), (0.17, 0.34, 0.52), (1.05, 1.00, 0.90), (0.92, 0.82, 0.68), 8, 5),
    "leather":  (0.08, 0.86, (0.28, 0.54, 0.78), (0.15, 0.31, 0.48), (1.05, 1.00, 0.90), (0.95, 0.85, 0.70), 6, 6),
    "fur":      (0.09, 0.95, (0.25, 0.50, 0.75), (0.20, 0.42, 0.66), (1.00, 0.95, 0.85), (0.90, 0.75, 0.55), 7, 6),
    "bone":     (0.12, 0.97, (0.22, 0.46, 0.72), (0.22, 0.46, 0.72), (1.20, 1.15, 1.00), (0.85, 0.65, 0.40), 5, 6),
    "stone":    (0.10, 0.92, (0.25, 0.50, 0.75), (0.20, 0.40, 0.62), (1.00, 1.00, 0.90), (0.85, 0.70, 0.50), 6, 5),
    "crystal":  (0.14, 0.99, (0.25, 0.50, 0.74), (0.30, 0.62, 0.92), (1.10, 1.10, 1.00), (0.80, 0.50, 0.15), 9, -4),
    "scale":    (0.08, 0.95, (0.27, 0.54, 0.80), (0.22, 0.46, 0.74), (1.10, 1.05, 0.95), (0.88, 0.70, 0.45), 8, 7),
    "glow":     (0.20, 0.995, (0.18, 0.40, 0.62), (0.40, 0.72, 0.95), (1.00, 1.00, 1.00), (0.80, 0.50, 0.20), 4, 3),
    "dark":     (0.05, 0.80, (0.28, 0.55, 0.80), (0.22, 0.45, 0.68), (1.00, 1.00, 0.90), (0.90, 0.80, 0.60), 7, 5),
}

COOL_HUE = 285.0   # blue-violet
WARM_HUE = 95.0    # warm yellow
GOLD_SHADOW_HUE = 32.0


def ramp(base, kind="metal"):
    """Seven colours (tones -3..+3) for one base colour and material kind."""
    if isinstance(base, str):
        base = hexc(base)
    floor, ceil, sf, lf, cs, cl, cool, warm = KINDS[kind]
    L, C, h = oklch(base)
    if kind == "metal" and C > 0.05:
        # painted or coloured metal (enamel, red-gold, blued steel): highlights stay coloured, a little paler
        cl = (0.95, 0.80, 0.50)
        lf = (0.22, 0.46, 0.78)
    out = []
    neutral = C < 0.025
    for t in range(-3, 4):
        if t < 0:
            k = -t - 1
            Lt = L - max(sf[k] * (L - floor), 0.035 * -t)
            Lt = max(Lt, floor * (1 - 0.25 * k) + 0.01)
            Ct = C * cs[k]
            if kind == "gold":
                ht = _toward(h, GOLD_SHADOW_HUE if t > -3 else 12.0, 7 * -t)
            else:
                ht = _toward(h, COOL_HUE, cool * -t)
            if neutral:
                Ct = max(Ct, 0.012 + 0.006 * -t)
                ht = COOL_HUE - 20 if C < 0.008 else _toward(h, COOL_HUE, 25 * -t)
        elif t > 0:
            k = t - 1
            Lt = L + max(lf[k] * (ceil - L), 0.03 * t)
            Lt = min(Lt, 0.995)
            Ct = C * cl[k]
            ht = _toward(h, WARM_HUE, warm * t)
            if neutral and kind in ("metal", "gold", "bone", "crystal"):
                Ct = max(Ct, 0.008)
                ht = WARM_HUE if C < 0.008 else ht
        else:
            Lt, Ct, ht = L, C, h
        out.append(from_oklch(Lt, Ct, ht))
    return np.array(out, np.uint8)


def lighten(rgb, f):
    L, C, h = oklch(rgb)
    return from_oklch(min(0.99, L + f), C, h)


def tint(rgb, other, f):
    a, b = np.array(rgb[:3], float), np.array(other[:3], float)
    return tuple(int(round(v)) for v in a + (b - a) * f)


# ------------------------------------------------------------------------------------------------- shapes (pixel masks)

def poly(points, w, h, ss=4):
    """A polygon (pixel coordinates, y down) rasterised: pixels more than half covered."""
    ys, xs = np.mgrid[0:h * ss, 0:w * ss]
    px = (xs + 0.5) / ss
    py = (ys + 0.5) / ss
    inside = np.zeros(px.shape, bool)
    j = len(points) - 1
    for i in range(len(points)):
        xi, yi = points[i]
        xj, yj = points[j]
        inside ^= ((yi > py) != (yj > py)) & (px < (xj - xi) * (py - yi) / (yj - yi + 1e-9) + xi)
        j = i
    return inside.reshape(h, ss, w, ss).mean(axis=(1, 3)) > 0.5


def ellipse(cx, cy, rx, ry, w, h):
    yy, xx = np.mgrid[0:h, 0:w]
    return ((xx + 0.5 - cx) / rx) ** 2 + ((yy + 0.5 - cy) / ry) ** 2 <= 1


def seg(p0, p1, width, w, h):
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    (x0, y0), (x1, y1) = p0, p1
    dx, dy = x1 - x0, y1 - y0
    L2 = dx * dx + dy * dy + 1e-9
    t = np.clip(((xx - x0) * dx + (yy - y0) * dy) / L2, 0, 1)
    return np.hypot(xx - (x0 + t * dx), yy - (y0 + t * dy)) <= width / 2


def curve(points, width, w, h, steps=24):
    """A thick line through Catmull-Rom smoothed points; width may be a function of the point index t."""
    pts = [points[0]] + list(points) + [points[-1]]
    m = np.zeros((h, w), bool)
    prev = None
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        for s in range(steps + 1):
            t = s / steps
            t2, t3 = t * t, t * t * t
            x = 0.5 * ((2 * p1[0]) + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                       + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3)
            y = 0.5 * ((2 * p1[1]) + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                       + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3)
            wv = width(i - 1 + t) if callable(width) else width
            if prev is not None:
                m |= seg(prev, (x, y), wv, w, h)
            prev = (x, y)
    return m


def grow(m, n=1):
    for _ in range(n):
        g = m.copy()
        g[1:, :] |= m[:-1, :]
        g[:-1, :] |= m[1:, :]
        g[:, 1:] |= m[:, :-1]
        g[:, :-1] |= m[:, 1:]
        m = g
    return m


def shrink(m, n=1):
    return ~grow(~m, n)


def hash01(x, y, seed=0):
    n = (np.asarray(x, np.int64) * 374761393 + np.asarray(y, np.int64) * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ------------------------------------------------------------------------------------------------- tone canvas

class Canvas:
    """Pixels hold (ramp, tone) instead of colour. Pieces are painted in order; edges and contact shadows are worked
    out at the end from who owns which pixel, with neighbours taken from a topology (a plain grid for icons, the
    wrapped faces of the model's boxes for worn armor)."""

    def __init__(self, w, h, topology=None):
        self.w, self.h = w, h
        self.rid = np.full((h, w), -1, np.int32)
        self.tone = np.zeros((h, w), np.float32)
        self.pid = np.full((h, w), -1, np.int32)
        self.glow = np.zeros((h, w), np.float32)      # 0..1: drawn emissive in the glow mask
        self.ramps = []
        self._ramp_ix = {}
        self.pieces = []
        self.nb = topology if topology is not None else grid_topology(w, h)
        self.after = []                                # fixed-colour pixels (y, x, rgb) painted last

    # ---- ramps
    def r(self, color, kind):
        key = (tuple(color[:3]) if not isinstance(color, str) else hexc(color), kind)
        if key not in self._ramp_ix:
            self._ramp_ix[key] = len(self.ramps)
            self.ramps.append(ramp(key[0], kind))
        return self._ramp_ix[key]

    # ---- pieces
    def piece(self, mask, color, kind, tone=0.0, edge=(1.0, 0.5, -1.0, -0.5), ao=1.0, glow=0.0, keep_edges=False):
        """Paint a piece. tone: a number or an array (the piece's own shading, in ramp steps). edge: steps added on the
        piece's outer top, left, bottom and right rims. ao: steps taken off pixels of lower pieces just below/right."""
        mask = mask & np.ones((self.h, self.w), bool)
        if not mask.any():
            return -1
        p = len(self.pieces)
        self.pieces.append(dict(edge=edge, ao=ao, keep=keep_edges))
        rid = self.r(color, kind)
        self.rid[mask] = rid
        t = tone if np.isscalar(tone) else np.asarray(tone)[mask]
        self.tone[mask] = t
        self.pid[mask] = p
        self.glow[mask] = glow
        return p

    def shade(self, mask, steps):
        """Move pixels along their ramp (positive = lighter)."""
        m = mask & (self.rid >= 0)
        if np.isscalar(steps):
            self.tone[m] += steps
        else:
            self.tone[m] += np.asarray(steps)[m]

    def set_tone(self, mask, t, color=None, kind=None):
        m = mask & ((self.rid >= 0) | (color is not None))
        if color is not None:
            self.rid[m] = self.r(color, kind)
            if (self.pid[m] < 0).any():
                self.pid[m & (self.pid < 0)] = len(self.pieces) - 1
        self.tone[m] = t

    def erase(self, mask):
        self.rid[mask] = -1
        self.pid[mask] = -1
        self.glow[mask] = 0

    def nbr(self, arr, d, fill=-2):
        """Value of each pixel's neighbour in direction d (0 up, 1 down, 2 left, 3 right)."""
        flat = arr.reshape(-1)
        idx = self.nb[d]
        out = np.where(idx >= 0, flat[np.clip(idx, 0, None)], fill)
        return out.reshape(arr.shape)

    def finish(self):
        """Edge light on every piece's own rims, contact shadows where a later piece overlaps an earlier one."""
        pid = self.pid
        own = pid >= 0
        add = np.zeros_like(self.tone)
        nbs = [self.nbr(pid, d) for d in range(4)]
        edges = np.array([pc["edge"] for pc in self.pieces] + [(0, 0, 0, 0)], np.float32)
        aos = np.array([pc["ao"] for pc in self.pieces] + [0], np.float32)
        pidc = np.where(own, pid, len(self.pieces))
        for d in range(4):
            nb = nbs[d]
            rim = own & (nb != pid) & (nb < pid)          # neighbour is empty or an earlier piece: our own rim
            add += np.where(rim, edges[pidc, d], 0)
        # contact shadow: a later piece just above or left of us
        up_over = own & (nbs[0] > pid)
        left_over = own & (nbs[2] > pid)
        ao_up = np.where(up_over, aos[np.clip(nbs[0], 0, len(self.pieces))], 0)
        ao_left = np.where(left_over, aos[np.clip(nbs[2], 0, len(self.pieces))], 0) * 0.6
        add -= np.maximum(ao_up, ao_left)
        self.tone += add

    def render(self):
        out = np.zeros((self.h, self.w, 4), np.uint8)
        own = self.rid >= 0
        t = np.clip(np.round(self.tone), -3, 3).astype(int) + 3
        allr = np.stack(self.ramps) if self.ramps else np.zeros((1, 7, 3), np.uint8)
        out[own, :3] = allr[self.rid[own], t[own]]
        out[own, 3] = 255
        for y, x, c in self.after:
            out[y, x, :3] = c[:3]
            out[y, x, 3] = 255 if len(c) < 4 else c[3]
        return out

    def glow_mask(self):
        """The emissive pixels in their own colours (black elsewhere is left transparent)."""
        rgba = self.render()
        g = self.glow > 0
        out = np.zeros_like(rgba)
        out[g] = rgba[g]
        out[g, 3] = np.clip(self.glow[g] * 255, 0, 255).astype(np.uint8)
        return out


def grid_topology(w, h):
    idx = np.arange(w * h).reshape(h, w)
    up = np.full((h, w), -1)
    up[1:] = idx[:-1]
    down = np.full((h, w), -1)
    down[:-1] = idx[1:]
    left = np.full((h, w), -1)
    left[:, 1:] = idx[:, :-1]
    right = np.full((h, w), -1)
    right[:, :-1] = idx[:, 1:]
    return [up.reshape(-1), down.reshape(-1), left.reshape(-1), right.reshape(-1)]


# ------------------------------------------------------------------------------------------------- material tones

def band_profile(n, kind):
    """Tone per row for a plate n rows tall: polished metal's bright band / dark band / bounce light."""
    y = (np.arange(n) + 0.5) / max(1, n)
    if kind in ("metal", "gold", "crystal"):
        if n <= 3:
            return np.array([0.6, 0.0, -0.5][:n])
        t = np.where(y < 0.18, 1.0, np.where(y < 0.34, 0.45, np.where(y < 0.62, -0.25, np.where(y < 0.84, -0.9, -0.35))))
        return t
    if kind in ("dark", "scale", "bone", "stone"):
        return np.where(y < 0.3, 0.45, np.where(y < 0.7, 0.0, -0.5))
    # matte: a gentle top light
    return np.where(y < 0.25, 0.3, np.where(y < 0.75, 0.0, -0.35))


def rows_tone(mask, kind, flip=False):
    """Apply band_profile within the vertical extent of each column run of the mask (any shape)."""
    h, w = mask.shape
    out = np.zeros((h, w), np.float32)
    ys, xs = np.nonzero(mask)
    if len(ys) == 0:
        return out
    y0, y1 = ys.min(), ys.max()
    prof = band_profile(y1 - y0 + 1, kind)
    if flip:
        prof = prof[::-1]
    out[y0:y1 + 1, :] = prof[:, None]
    return out * mask


def cloth_folds(h, w, seed, period=5.0, strength=1.0, slant=0.0):
    """Vertical folds: soft light and dark stripes that wander a little."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    rng = np.random.default_rng(seed)
    ph = rng.uniform(0, 6.28)
    wob = np.sin(yy * 0.35 + rng.uniform(0, 6.28)) * 0.6
    v = np.sin((xx + yy * slant + wob) * 2 * math.pi / period + ph)
    return np.where(v > 0.55, 1.0, np.where(v < -0.55, -1.0, 0.0)) * strength


def mail_tone(h, w, x0=0, y0=0):
    """Chainmail rings on a 4x2 repeat: a lit top-left on each ring, a dark gap below."""
    yy, xx = np.mgrid[0:h, 0:w]
    xx = xx - x0 + (((yy - y0) // 2) % 2) * 2
    yy = yy - y0
    cx = xx % 4
    cy = yy % 2
    t = np.full((h, w), 0.0)
    t = np.where((cy == 0) & (cx == 0), 1.0, t)
    t = np.where((cy == 0) & (cx == 1), 0.4, t)
    t = np.where((cy == 0) & (cx == 3), -1.0, t)
    t = np.where((cy == 1) & (cx == 0), -0.2, t)
    t = np.where((cy == 1) & (cx == 1), -0.6, t)
    t = np.where((cy == 1) & ((cx == 2) | (cx == 3)), -1.6, t)
    return t


def scale_tone(h, w, size=4, x0=0, y0=0):
    """Overlapping scales pointing down: lit tops, a dark rim along the lower edge of each scale."""
    yy, xx = np.mgrid[0:h, 0:w].astype(float)
    yy = yy - y0
    xx = xx - x0
    out = np.full((h, w), -1.4)
    rows = int(h / (size * 0.75)) + 3
    r = size / 2.0
    for row in range(-1, rows):
        cy = row * size * 0.75
        xo = (row % 2) * r
        cx = (np.floor((xx + xo) / size) + 0.5) * size - xo
        d = np.hypot((xx + 0.5 - cx) / r, (yy + 0.5 - cy) / (r * 1.25))
        inside = (d <= 1.0) & (yy + 0.5 >= cy - r * 0.6)
        v = np.where(d > 0.78, -1.3, np.where(yy + 0.5 < cy - r * 0.05, 0.9, 0.0))
        v = np.where((d > 0.5) & (xx + 0.5 > cx + r * 0.25) & (d <= 0.78), -0.5, v)
        out = np.where(inside, v, out)
    return out


def quilt_tone(h, w, step=4, x0=0, y0=0):
    """Quilted padding: diamond seams with a soft puff between them."""
    yy, xx = np.mgrid[0:h, 0:w]
    a = (xx - x0 + yy - y0) % step
    b = (xx - x0 - (yy - y0)) % step
    t = np.zeros((h, w))
    t = np.where((a == 0) | (b == 0), -1.2, t)
    t = np.where(((a == 1) & (b != 0)) | ((b == 1) & (a != 0)), 0.6, t)
    return t


def fur_tone(h, w, seed, x0=0, y0=0):
    """Fur: short vertical strands in clumps."""
    yy, xx = np.mgrid[0:h, 0:w]
    hx = hash01(xx - x0, (yy - y0) // 3, seed)
    hy = hash01((xx - x0) // 2, yy - y0, seed + 5)
    t = np.where(hx > 0.62, 1.0, np.where(hx < 0.3, -1.0, 0.0))
    t = np.where(hy > 0.85, t + 0.6, t)
    return t


def leather_tone(h, w, seed, x0=0, y0=0):
    """Leather: mostly flat with a few lighter scuffs and dark creases."""
    yy, xx = np.mgrid[0:h, 0:w]
    hv = hash01((xx - x0) // 3, (yy - y0) // 2, seed)
    hw = hash01(xx - x0, yy - y0, seed + 11)
    t = np.where(hv > 0.86, 0.6, 0.0)
    t = np.where(hw > 0.93, -0.8, t)
    return t


def facet_tone(mask, seed, n=None):
    """Crystal or ice: flat facets at random tilts with a bright line where two facets meet."""
    h, w = mask.shape
    ys, xs = np.nonzero(mask)
    if len(ys) == 0:
        return np.zeros((h, w))
    rng = np.random.default_rng(seed)
    n = n or max(3, len(ys) // 22)
    pts = np.stack([rng.uniform(xs.min(), xs.max() + 1, n), rng.uniform(ys.min(), ys.max() + 1, n)], 1)
    tilt = rng.choice([-1.4, -0.6, 0.2, 0.9, 1.6], n)
    yy, xx = np.mgrid[0:h, 0:w] + 0.5
    d = (xx[..., None] - pts[:, 0]) ** 2 + (yy[..., None] - pts[:, 1]) ** 2
    cell = np.argmin(d, axis=-1)
    srt = np.sort(d, axis=-1)
    edge = (np.sqrt(srt[..., 1]) - np.sqrt(srt[..., 0])) < 0.9
    out = tilt[cell] + np.where(edge, 1.2, 0)
    return out


def cracks(mask, seed, n=6, length=10):
    """Random-walk crack lines inside a mask."""
    rng = np.random.default_rng(seed)
    h, w = mask.shape
    out = np.zeros_like(mask)
    ys, xs = np.nonzero(shrink(mask, 1) if shrink(mask, 1).any() else mask)
    if len(ys) == 0:
        return out
    for _ in range(n):
        i = rng.integers(len(ys))
        y, x = float(ys[i]), float(xs[i])
        ang = rng.uniform(0, 2 * math.pi)
        for _ in range(length):
            yi, xi = int(y), int(x)
            if 0 <= yi < h and 0 <= xi < w and mask[yi, xi]:
                out[yi, xi] = True
            ang += rng.uniform(-0.7, 0.7)
            x += math.cos(ang)
            y += math.sin(ang)
            if rng.random() < 0.15:
                ang += rng.choice([-1.1, 1.1])
    return out


# ------------------------------------------------------------------------------------------------- outlines (icons)

def sel_out(rgba, strength=0.62, ink=(14, 10, 22)):
    """Selective outline for icons: a 1px rim around the silhouette in a dark, hue-kept version of the colour it
    touches - lighter on the lit top-left, darkest on the bottom-right."""
    a = rgba.copy()
    solid = a[..., 3] >= 128
    H, W = solid.shape
    ring = grow(solid) & ~solid
    acc = np.zeros((H, W, 3))
    cnt = np.zeros((H, W))
    for dy, dx in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        sh = np.roll(np.roll(a[..., :3].astype(float), dy, 0), dx, 1)
        sm = np.roll(np.roll(solid, dy, 0), dx, 1)
        acc += sh * sm[..., None]
        cnt += sm
    avg = acc / np.maximum(cnt, 1)[..., None]
    yy, xx = np.mgrid[0:H, 0:W]
    # which side of the shape the rim pixel is on: below/right of solid = shadow side
    below = np.roll(solid, 1, 0) | np.roll(solid, 1, 1)
    k = np.where(below, strength + 0.18, strength - 0.1)[..., None]
    col = avg * (1 - k) * 0.5 + np.array(ink, float) * k
    a[ring, :3] = np.clip(col[ring], 0, 255).astype(np.uint8)
    a[ring, 3] = 255
    return a


def to_image(rgba):
    return Image.fromarray(rgba.astype(np.uint8), "RGBA")
