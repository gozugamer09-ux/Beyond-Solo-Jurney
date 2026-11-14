"""Builds the System UI font files.

Run from the repo root: python3 tools/gen_fonts.py   (needs: pip install fonttools)

Why so many JSON files: Minecraft draws TTF glyphs without smoothing when they are scaled, so text only
looks clean when a glyph is rasterised at exactly the size it appears on screen. We therefore register
one font per pixel size ("beyondsolo:text_18", "beyondsolo:title_32", ...) and SysFont picks the one
that matches the current GUI scale.

Font: Exo 2 by the Exo 2 Project Authors, SIL Open Font License 1.1. The licence text ships in the jar as
licenses/exo2_ofl.txt (resource pack folders only allow lowercase names, so it cannot sit next to the fonts).
The fonts are subset to Western European characters to keep the jar and load time small.
"""
import json
import os
import shutil
from fontTools import subset

SRC = "tools/fonts-src"
OUT = "src/main/resources/assets/beyondsolo/font"
LICENSES = "src/main/resources/licenses"

# Pixel sizes we rasterise at. Keep in sync with SysFont.SIZES.
SIZES = [7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 21, 22, 24, 27, 28, 30, 32, 33, 36, 40, 42, 44, 48,
         54, 56, 60, 64, 72, 80, 84, 88, 96, 112, 128]

UNICODES = "U+0020-007E,U+00A0-00FF,U+0131,U+0152,U+0153,U+2013,U+2014,U+2018-201A,U+201C-201E,U+2022,U+2026,U+2122,U+2212"

WEIGHTS = {"text": "Exo2-SemiBold.ttf", "title": "Exo2-Bold.ttf"}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, src in WEIGHTS.items():
        ttf = f"exo2_{name}.ttf"
        subset.main([
            os.path.join(SRC, src),
            f"--unicodes={UNICODES}",
            "--layout-features=",
            "--name-IDs=*",
            "--notdef-outline",
            f"--output-file={os.path.join(OUT, ttf)}",
        ])
        for px in SIZES:
            with open(os.path.join(OUT, f"{name}_{px}.json"), "w") as f:
                json.dump({"providers": [{"type": "ttf", "file": f"beyondsolo:{ttf}", "size": float(px), "oversample": 1.0}]}, f)
        print(name, os.path.getsize(os.path.join(OUT, ttf)), "bytes,", len(SIZES), "sizes")
    os.makedirs(LICENSES, exist_ok=True)
    shutil.copyfile(os.path.join(SRC, "OFL.txt"), os.path.join(LICENSES, "exo2_ofl.txt"))


if __name__ == "__main__":
    main()
