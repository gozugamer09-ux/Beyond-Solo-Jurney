"""Generates data/beyondsolo/structure/evaluation_zone.nbt (the village building).

Run from the repo root: python3 tools/gen_evaluation_zone.py
Edit the layout below, or build it in game and save it with a structure block instead.
"""
import gzip
import struct

TAG_END, TAG_BYTE, TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 0, 1, 3, 8, 9, 10


class Int(int):
    pass


def w_str(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def tag_type(v):
    if isinstance(v, dict):
        return TAG_COMPOUND
    if isinstance(v, list):
        return TAG_LIST
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, int):
        return TAG_INT
    raise TypeError(v)


def payload(v):
    t = tag_type(v)
    if t == TAG_COMPOUND:
        out = b""
        for k, x in v.items():
            out += bytes([tag_type(x)]) + w_str(k) + payload(x)
        return out + bytes([TAG_END])
    if t == TAG_LIST:
        et = tag_type(v[0]) if v else TAG_END
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(payload(x) for x in v)
    if t == TAG_STRING:
        return w_str(v)
    return struct.pack(">i", v)


W, H, D = 7, 6, 7
blocks = {}


def put(x, y, z, name, props=None, nbt=None):
    blocks[(x, y, z)] = (name, props or {}, nbt)


for x in range(W):
    for y in range(H):
        for z in range(D):
            put(x, y, z, "minecraft:air")

# floor
for x in range(W):
    for z in range(D):
        edge = x in (0, W - 1) or z in (0, D - 1)
        put(x, 0, z, "minecraft:stone_bricks" if edge else "minecraft:polished_andesite")
# dark inlay around the crystal, so its magic circle stands out on the floor
for x in range(2, 5):
    for z in range(2, 5):
        put(x, 0, z, "minecraft:polished_deepslate")
put(3, 0, 3, "minecraft:chiseled_deepslate")
put(3, 1, 3, "beyondsolo:evaluation_crystal")
# corner pillars
for (x, z) in [(0, 0), (W - 1, 0), (0, D - 1), (W - 1, D - 1)]:
    for y in range(1, 5):
        put(x, y, z, "minecraft:chiseled_stone_bricks" if y == 4 else "minecraft:stone_bricks")
# low rails on the sides and back (front stays open)
for z in range(1, D - 1):
    put(0, 1, z, "minecraft:stone_brick_wall", {"up": "true"})
    put(W - 1, 1, z, "minecraft:stone_brick_wall", {"up": "true"})
for x in range(1, W - 1):
    put(x, 1, D - 1, "minecraft:stone_brick_wall", {"up": "true"})
# roof
for x in range(W):
    for z in range(D):
        put(x, 5, z, "minecraft:stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
put(3, 5, 3, "minecraft:light_blue_stained_glass")
# hanging lanterns
for (x, z) in [(1, 1), (W - 2, 1), (1, D - 2), (W - 2, D - 2)]:
    put(x, 4, z, "minecraft:soul_lantern", {"hanging": "true", "waterlogged": "false"})
# jigsaw: front middle of the floor, pointing out to the street
put(3, 0, 0, "minecraft:jigsaw", {"orientation": "north_up"}, {
    "id": "minecraft:jigsaw",
    "name": "minecraft:building_entrance",
    "target": "minecraft:empty",
    "pool": "minecraft:empty",
    "final_state": "minecraft:stone_bricks",
    "joint": "rollable",
    "placement_priority": 0,
    "selection_priority": 0,
})

palette, index, block_list = [], {}, []
for pos, (name, props, nbt) in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
    key = (name, tuple(sorted(props.items())))
    if key not in index:
        index[key] = len(palette)
        entry = {"Name": name}
        if props:
            entry["Properties"] = dict(props)
        palette.append(entry)
    b = {"pos": [pos[0], pos[1], pos[2]], "state": index[key]}
    if nbt:
        b["nbt"] = nbt
    block_list.append(b)

root = {
    "DataVersion": 3955,
    "size": [W, H, D],
    "palette": palette,
    "blocks": block_list,
    "entities": [],
}
data = bytes([TAG_COMPOUND]) + w_str("") + payload(root)
out = "src/main/resources/data/beyondsolo/structure/evaluation_zone.nbt"
with gzip.open(out, "wb") as f:
    f.write(data)
print("wrote", out, len(block_list), "blocks,", len(palette), "palette entries")
