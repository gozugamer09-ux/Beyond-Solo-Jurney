#!/usr/bin/env python3
"""
Generates the hunter economy's item art: the Hunter License sprite (32x32 pixel art), one per rank colour plus a
blank card, and the item model that picks the sprite by the card's rank (item property "beyondsolo:rank",
0 = blank, 0.1 = E ... 0.6 = S; registered in client/economy/EconomyClientEvents).

Run from the repo root:  python3 tools/gen_economy_assets.py
Needs: pillow.
"""
import json
import os

from PIL import Image

ITEM_TEX = "src/main/resources/assets/beyondsolo/textures/item"
ITEM_MODEL = "src/main/resources/assets/beyondsolo/models/item"

# keep in sync with PlayerRank.java
RANKS = {"e": 0x9E9E9E, "d": 0x7CC576, "c": 0x4FA3F7, "b": 0xB57CF7, "a": 0xF7B84F, "s": 0xF75A4F}
BLANK = 0x6F859C


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def card(color):
    """A 32x32 ID card seen at a slight angle: dark glass body, a band in the rank colour, a photo, text lines,
    a rank chip and a holographic corner."""
    S = 32
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = img.load()
    rank = rgb(color)
    body_top, body_bot = (26, 40, 68), (12, 20, 38)
    x0, x1, y0, y1 = 2, 29, 7, 25
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            # rounded corners
            corner = (x in (x0, x1) and y in (y0, y1))
            if corner:
                continue
            t = (y - y0) / (y1 - y0)
            c = mix(body_top, body_bot, t)
            edge = x in (x0, x1) or y in (y0, y1)
            if edge:
                c = mix(rank, (255, 255, 255), 0.15) if y < y0 + 6 else mix(rank, (0, 0, 0), 0.25)
            px[x, y] = c + (255,)
    # the band across the top
    for y in range(y0 + 1, y0 + 5):
        for x in range(x0 + 1, x1):
            t = (x - x0) / (x1 - x0)
            px[x, y] = mix(mix(rank, (255, 255, 255), 0.2), mix(rank, body_top, 0.6), t) + (255,)
    # band highlight
    for x in range(x0 + 1, x1):
        r, g, b, a = px[x, y0 + 1]
        px[x, y0 + 1] = mix((r, g, b), (255, 255, 255), 0.35) + (255,)
    # the photo
    for y in range(13, 22):
        for x in range(5, 12):
            px[x, y] = (40, 58, 92, 255)
    for y in range(14, 21):
        for x in range(6, 11):
            px[x, y] = (54, 76, 116, 255)
    # a head and shoulders
    for (x, y) in [(8, 15), (7, 16), (8, 16), (9, 16), (7, 17), (8, 17), (9, 17), (6, 19), (7, 19), (8, 19), (9, 19), (10, 19), (6, 20), (7, 20), (8, 20), (9, 20), (10, 20), (7, 18), (8, 18), (9, 18)]:
        px[x, y] = (196, 206, 222, 255) if y < 19 else (150, 166, 192, 255)
    # photo frame corners in the rank colour
    for (x, y) in [(5, 13), (11, 13), (5, 21), (11, 21)]:
        px[x, y] = rank + (255,)
    # text lines
    for (y, x_end, c) in [(14, 22, (226, 236, 248)), (16, 19, (150, 170, 196)), (19, 21, (110, 130, 160)), (21, 18, (110, 130, 160))]:
        for x in range(14, x_end):
            px[x, y] = c + (255,)
    # rank chip on the right
    for y in range(15, 21):
        for x in range(23, 28):
            edge = x in (23, 27) or y in (15, 20)
            if edge and (x in (23, 27) and y in (15, 20)):
                continue
            px[x, y] = (mix(rank, (255, 255, 255), 0.25) if edge else mix(rank, (20, 30, 50), 0.35)) + (255,)
    for (x, y) in [(25, 17), (25, 18), (24, 17), (26, 18)]:
        px[x, y] = (255, 255, 255, 255)
    # holographic corner
    holo = [(150, 232, 255), (228, 168, 255), (255, 236, 160)]
    for i, (x, y) in enumerate([(26, 22), (27, 22), (27, 23), (28, 23), (28, 22), (26, 23)]):
        px[x, y] = holo[i % 3] + (255,)
    # a soft shadow under the card
    for x in range(x0 + 1, x1 + 1):
        if px[x, y1 + 1][3] == 0:
            px[x, y1 + 1] = (0, 0, 0, 90)
    return img


def main():
    os.makedirs(ITEM_TEX, exist_ok=True)
    card(BLANK).save(f"{ITEM_TEX}/hunter_license.png")
    overrides = []
    for i, (letter, color) in enumerate(RANKS.items()):
        name = f"hunter_license_{letter}"
        card(color).save(f"{ITEM_TEX}/{name}.png")
        with open(f"{ITEM_MODEL}/{name}.json", "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"beyondsolo:item/{name}"}}, f, indent=2)
            f.write("\n")
        overrides.append({"predicate": {"beyondsolo:rank": round((i + 1) / 10 - 0.001, 3)}, "model": f"beyondsolo:item/{name}"})
    with open(f"{ITEM_MODEL}/hunter_license.json", "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "beyondsolo:item/hunter_license"},
                   "overrides": overrides}, f, indent=2)
        f.write("\n")
    print("hunter license: blank +", len(RANKS), "ranks")


if __name__ == "__main__":
    main()
