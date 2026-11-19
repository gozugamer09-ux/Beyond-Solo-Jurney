#!/usr/bin/env python3
"""
Art for the shadow army's promoted ranks (run from the repository root):

  textures/entity/shadow/shell.png  the energy that flows over a knight's (and higher) shadow: thin veins of light
                                    with a soft halo on black, tiling both ways, drawn additively and tinted by the
                                    rank's colour (RenderType.energySwirl scrolls it). Mostly dark, so the shadow's
                                    ink body still shows between the veins.

  python3 tools/gen_shadow_assets.py [--preview out.png]
"""
import math
import random
import sys

from PIL import Image

OUT = "src/main/resources/assets/beyondsolo/textures/entity/shadow/shell.png"
N = 256


def periodic_noise(seed, cells):
    """Value noise that tiles over N pixels with `cells` cells a side (smooth, cosine-blended)."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / N * cells, y / N * cells
        x0, y0 = int(math.floor(fx)) % cells, int(math.floor(fy)) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        tx, ty = fx - math.floor(fx), fy - math.floor(fy)
        sx, sy = (1 - math.cos(tx * math.pi)) / 2, (1 - math.cos(ty * math.pi)) / 2
        a = grid[y0][x0] * (1 - sx) + grid[y0][x1] * sx
        b = grid[y1][x0] * (1 - sx) + grid[y1][x1] * sx
        return a * (1 - sy) + b * sy

    return at


def smooth(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def main():
    # two vein networks: wide-spaced main veins and a finer, fainter web between them
    main_oct = [(periodic_noise(11, 4), 0.55), (periodic_noise(23, 8), 0.3), (periodic_noise(37, 16), 0.15)]
    fine_oct = [(periodic_noise(61, 8), 0.6), (periodic_noise(73, 16), 0.4)]
    warp_a = periodic_noise(51, 4)
    warp_b = periodic_noise(83, 8)

    def main_field(x, y):
        w = warp_a(x % N, y % N) * 2 * math.pi
        return sum(f((x + 18 * math.cos(w)) % N, (y + 18 * math.sin(w)) % N) * a for f, a in main_oct)

    def fine_field(x, y):
        w = warp_b(x % N, y % N) * 2 * math.pi
        return sum(f((x + 9 * math.cos(w)) % N, (y + 9 * math.sin(w)) % N) * a for f, a in fine_oct)

    def distance(field, x, y):
        """How far (in pixels) this point is from the field's 0.5 line: veins of the same width everywhere."""
        v = field(x, y)
        gx = (field(x + 1, y) - field(x - 1, y)) / 2
        gy = (field(x, y + 1) - field(x, y - 1)) / 2
        return abs(v - 0.5) / max(1e-4, math.hypot(gx, gy))

    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            d = distance(main_field, x, y)
            core = 1 - smooth(0.7, 1.7, d)
            halo = (1 - smooth(1.2, 7.0, d)) ** 2 * 0.30
            d2 = distance(fine_field, x, y)
            web = (1 - smooth(0.25, 0.85, d2)) * 0.40 + (1 - smooth(0.6, 3.0, d2)) ** 2 * 0.08
            c = min(1.0, 0.015 + core + halo + web * (1 - core))
            g = int(round(255 * c))
            img.putpixel((x, y), (g, g, g, 255))
    img.save(OUT)
    print("wrote", OUT)
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        sheet = Image.new("RGBA", (N * 3, N * 3))
        for i in range(3):
            for j in range(3):
                sheet.paste(img, (i * N, j * N))
        sheet.save(sys.argv[2])
        print("preview", sys.argv[2])


if __name__ == "__main__":
    main()
