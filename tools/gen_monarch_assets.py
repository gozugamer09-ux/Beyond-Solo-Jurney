#!/usr/bin/env python3
"""
Generates the monarch path's icons: distance-field icons for the four crowns ("monarch_<dominion>") and the seven
monarch powers ("skill_<path>"), from Phosphor Icons (bold, MIT) in tools/icons-src, with the same pipeline as
tools/gen_ui_assets.py.

Run from the repo root:  python3 tools/gen_monarch_assets.py
Needs: cairosvg, pillow, numpy, scipy.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from gen_ui_assets import icon_mask, sdf_from_mask, write_sdf  # noqa: E402

# keep in sync with Dominion.java
CROWNS = {
    "night": "moon-stars",
    "winter": "snowflake",
    "cinders": "fire",
    "thunder": "lightning",
}

# keep in sync with MonarchPowers.java
POWERS = {
    "monarch_domain": "planet",
    "monarch_dread": "skull",
    "monarch_crown": "crown",
    "monarch_eclipse": "moon",
    "monarch_long_winter": "snowflake",
    "monarch_ashen_heaven": "sun",
    "monarch_thousand_thunders": "cloud-lightning",
}


def main():
    for key, src in CROWNS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "monarch_" + key)
    for path, src in POWERS.items():
        write_sdf(sdf_from_mask(icon_mask(src)), "skill_" + path)
    print(len(CROWNS), "crown icons,", len(POWERS), "power icons")


if __name__ == "__main__":
    main()
