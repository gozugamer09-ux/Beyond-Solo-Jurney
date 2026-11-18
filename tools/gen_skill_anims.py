#!/usr/bin/env python3
"""Body animations for skill casts (playerAnimator emote JSON): one gesture per way of casting.

A skill's effect starts the moment it is cast, so every gesture strikes fast: the pose it fades in from is the
wind-up, the strike lands by tick 2 to 4, then a short follow-through and the ease back. Which gesture a skill plays
is decided in client/skill/SkillGestures (from the first effect the cast sends and the caster's class).

Same conventions as tools/gen_signature_anims.py (degrees here, radians in the files):
  arms/legs pitch: negative raises the limb forward (-90 straight ahead, -180 straight up), positive swings it back
  arm roll lifts it out to the side (right arm +, left arm -) while it hangs down; raised overhead the sign flips
  arm yaw: + swings the arm towards the body's right
  torso: the whole player, turned about the hips (this emote format has no version, and playerAnimator reads its
    "torso" as "body"): pitch negative leans forward, yaw positive turns to the left. There is no "y": it would lift
    the whole player off the ground by that many blocks. The legs are turned back against every lean (see plant), so
    write leg pitches as they should look in the world and the feet stay on the ground.
  head pitch: negative looks up
  body yaw: turns the whole player (a spin)

Run from the repo root:  python3 tools/gen_skill_anims.py
"""
import json
import math
import os

OUT = "src/main/resources/assets/beyondsolo/player_animations"

PIVOTS = {
    "head": (0, 0, 0),
    "torso": (0, 0, 0),
    "body": (0, 0, 0),
    "rightArm": (-5, 2, 0),
    "leftArm": (5, 2, 0),
    "rightLeg": (-1.9, 12, 0.1),
    "leftLeg": (1.9, 12, 0.1),
}
ROT = ("pitch", "yaw", "roll")


def interp(keys, axis, tick):
    """The value of {@code axis} at {@code tick}, straight between the keyframes that set it (0 if none do)."""
    pts = sorted((k[0], k[1][axis]) for k in keys if axis in k[1])
    if not pts:
        return 0.0
    if tick <= pts[0][0]:
        return pts[0][1]
    for (t0, v0), (t1, v1) in zip(pts, pts[1:]):
        if t0 <= tick <= t1:
            return v1 if t1 == t0 else v0 + (v1 - v0) * (tick - t0) / (t1 - t0)
    return pts[-1][1]


def plant(frames):
    """Keeps the feet on the ground: the torso (the whole player) is never lifted, and each leg is turned forward by
    as much as the body leans forward at every keyframe of either, so a lean bends the player at the hips."""
    out = dict(frames)
    torso = [(k[0], {a: v for a, v in k[1].items() if a != "y"}) + tuple(k[2:]) for k in frames.get("torso", [])]
    if torso:
        out["torso"] = [k for k in torso if k[1]]
    leans = {k[0] for k in torso if "pitch" in k[1]}
    if not leans:
        return out
    for leg in ("leftLeg", "rightLeg"):
        keys = frames.get(leg, [])
        planted = []
        for t in sorted({k[0] for k in keys} | leans):
            pose = {"pitch": round(interp(keys, "pitch", t) + interp(torso, "pitch", t), 2)}
            for axis in ("yaw", "roll"):
                if any(axis in k[1] for k in keys):
                    pose[axis] = interp(keys, axis, t)
            ease = [k[2] for k in keys + torso if k[0] == t and len(k) > 2]
            planted.append((t, pose, ease[0]) if ease else (t, pose))
        out[leg] = planted
    return out


def emote(name, description, end, stop, frames, easing="EASEINOUTQUAD"):
    """frames: {part: [(tick, {axis: value}, easing?), ...]}."""
    frames = plant(frames)
    moves = []
    for part, keys in frames.items():
        px, py, pz = PIVOTS[part]
        for key in keys:
            tick, pose = key[0], key[1]
            ease = key[2] if len(key) > 2 else easing
            for axis, value in pose.items():
                v = math.radians(value) if axis in ROT else value + {"x": px, "y": py, "z": pz}[axis]
                moves.append({"tick": tick, "easing": ease, "turn": 0, part: {axis: round(v, 4)}})
            if part not in ("head", "torso", "body"):
                for axis, base in (("x", px), ("y", py), ("z", pz)):
                    if axis not in pose:
                        moves.append({"tick": tick, "easing": ease, "turn": 0, part: {axis: base}})
    return {
        "name": name,
        "author": "Beyond Solo",
        "description": description,
        "emote": {
            "isLoop": "false",
            "returnTick": 0,
            "beginTick": 0,
            "endTick": end,
            "stopTick": stop,
            "degrees": False,
            "easeBeforeKeyframe": True,
            "moves": moves,
        },
    }


OUTQ, INQ, INC, BACK, LIN = "EASEOUTQUAD", "EASEINQUAD", "EASEINCUBIC", "EASEOUTBACK", "LINEAR"
G = {}


def legs(front=-18, back=14, spread=0):
    """A stance held through the whole move: left foot forward, right foot back."""
    return {
        "leftLeg": [(0, {"pitch": front, "roll": -spread}), (99, {"pitch": front, "roll": -spread})],
        "rightLeg": [(0, {"pitch": back, "roll": spread}), (99, {"pitch": back, "roll": spread})],
    }


def gesture(name, description, end, stop, frames, stance=None):
    parts = dict(frames)
    if stance:
        for part, keys in stance.items():
            parts.setdefault(part, [(t, p) for t, p in keys if t <= end])
    G[name] = emote("skill_" + name, description, end, stop, parts)


# ---- blades

gesture("slash", "A wide cut from right to left", 12, 18, {
    "rightArm": [(0, {"pitch": -105, "yaw": 65}), (3, {"pitch": -88, "yaw": -60}, OUTQ), (8, {"pitch": -70, "yaw": -65}),
                 (12, {"pitch": -40, "yaw": -25})],
    "leftArm": [(0, {"pitch": -25, "roll": -20}), (3, {"pitch": 18, "roll": -28}, OUTQ), (12, {"pitch": 5, "roll": -15})],
    "torso": [(0, {"yaw": -28, "pitch": -4}), (3, {"yaw": 32, "pitch": -10}, OUTQ), (8, {"yaw": 28, "pitch": -8}), (12, {"yaw": 10})],
    "head": [(0, {"yaw": 15}), (3, {"yaw": -18}, OUTQ), (12, {"yaw": -6})],
}, legs(-20, 14, 4))

gesture("slash_back", "A backhand cut from left to right", 12, 18, {
    "rightArm": [(0, {"pitch": -95, "yaw": -60}), (3, {"pitch": -92, "yaw": 70}, OUTQ), (8, {"pitch": -75, "yaw": 72}),
                 (12, {"pitch": -45, "yaw": 30})],
    "leftArm": [(0, {"pitch": 10, "roll": -25}), (3, {"pitch": -30, "roll": -20}, OUTQ), (12, {"pitch": -5, "roll": -12})],
    "torso": [(0, {"yaw": 30, "pitch": -6}), (3, {"yaw": -32, "pitch": -10}, OUTQ), (8, {"yaw": -28}), (12, {"yaw": -10})],
    "head": [(0, {"yaw": -15}), (3, {"yaw": 16}, OUTQ), (12, {"yaw": 5})],
}, legs(-14, 18, 4))

gesture("chop", "Both hands overhead, then one cut straight down", 14, 20, {
    "rightArm": [(0, {"pitch": -168, "yaw": -5}), (4, {"pitch": -58, "yaw": -8}, INC), (10, {"pitch": -55}), (14, {"pitch": -35})],
    "leftArm": [(0, {"pitch": -165, "yaw": 15}), (4, {"pitch": -62, "yaw": 25}, INC), (10, {"pitch": -58, "yaw": 22}), (14, {"pitch": -30})],
    "torso": [(0, {"pitch": 10}), (4, {"pitch": -24}, INC), (10, {"pitch": -20}), (14, {"pitch": -6})],
    "head": [(0, {"pitch": -14}), (4, {"pitch": 16}, INC), (14, {"pitch": 4})],
}, legs(-32, 22))

gesture("claw", "Both hands rake down across each other", 10, 16, {
    "rightArm": [(0, {"pitch": -150, "yaw": 25}), (2, {"pitch": -45, "yaw": -45}, OUTQ), (7, {"pitch": -40, "yaw": -48}), (10, {"pitch": -25, "yaw": -20})],
    "leftArm": [(0, {"pitch": -150, "yaw": -25}), (2, {"pitch": -45, "yaw": 45}, OUTQ), (7, {"pitch": -40, "yaw": 48}), (10, {"pitch": -25, "yaw": 20})],
    "torso": [(0, {"pitch": 6}), (2, {"pitch": -22}, OUTQ), (10, {"pitch": -10})],
    "head": [(0, {"pitch": -8}), (2, {"pitch": 10}), (10, {"pitch": 0})],
}, legs(-22, 18))

gesture("lunge", "Step in low and drive the point forward", 12, 18, {
    "rightArm": [(0, {"pitch": 18, "roll": 18}), (2, {"pitch": -95, "yaw": -8}, OUTQ), (8, {"pitch": -92, "yaw": -6}), (12, {"pitch": -55})],
    "leftArm": [(0, {"pitch": -55, "roll": -12}), (2, {"pitch": 28, "roll": -18}, OUTQ), (12, {"pitch": 8, "roll": -10})],
    "torso": [(0, {"pitch": -8, "yaw": -18}), (2, {"pitch": -26, "yaw": 12}, OUTQ), (8, {"pitch": -24, "yaw": 10}), (12, {"pitch": -8})],
    "head": [(0, {"pitch": -6}), (2, {"pitch": -18}), (12, {"pitch": -4})],
    "leftLeg": [(0, {"pitch": -10}), (2, {"pitch": -42}, OUTQ), (8, {"pitch": -40}), (12, {"pitch": -20})],
    "rightLeg": [(0, {"pitch": 8}), (2, {"pitch": 34}, OUTQ), (8, {"pitch": 32}), (12, {"pitch": 15})],
})

# a whirl: the whole body turns once (it starts a full turn back, so nothing jumps when it ends)
gesture("spin", "Arms out and one full turn", 10, 14, {
    "body": [(0, {"yaw": 360}), (3, {"yaw": 240}, LIN), (6, {"yaw": 110}, LIN), (9, {"yaw": 0}, OUTQ)],
    "rightArm": [(0, {"pitch": -88, "yaw": 55}), (9, {"pitch": -85, "yaw": 50}), (10, {"pitch": -60, "yaw": 20})],
    "leftArm": [(0, {"pitch": -20, "roll": -70}), (9, {"pitch": -20, "roll": -65}), (10, {"pitch": -10, "roll": -30})],
    "torso": [(0, {"pitch": -12}), (9, {"pitch": -12}), (10, {"pitch": -5})],
}, legs(-14, 14, 8))

# ---- weight and ground

gesture("slam", "Arms up, then the whole body crashes down into the ground", 16, 24, {
    "rightArm": [(0, {"pitch": -172}), (4, {"pitch": -32}, INC), (11, {"pitch": -30}), (16, {"pitch": -25})],
    "leftArm": [(0, {"pitch": -172}), (4, {"pitch": -36}, INC), (11, {"pitch": -34}), (16, {"pitch": -25})],
    "torso": [(0, {"pitch": 10}), (4, {"pitch": -38}, INC), (11, {"pitch": -34}), (16, {"pitch": -10})],
    "head": [(0, {"pitch": -18}), (4, {"pitch": 28}, INC), (11, {"pitch": 22}), (16, {"pitch": 5})],
    "leftLeg": [(0, {"pitch": -12, "roll": -4}), (4, {"pitch": -34, "roll": -10}, INC), (11, {"pitch": -32, "roll": -10}), (16, {"pitch": -12})],
    "rightLeg": [(0, {"pitch": 8, "roll": 4}), (4, {"pitch": 26, "roll": 10}, INC), (11, {"pitch": 24, "roll": 10}), (16, {"pitch": 8})],
})

gesture("stomp", "Knee up, then one heavy stamp", 10, 16, {
    "rightLeg": [(0, {"pitch": -62}), (3, {"pitch": 4}, INC), (10, {"pitch": 2})],
    "leftLeg": [(0, {"pitch": 6}), (3, {"pitch": -8}), (10, {"pitch": -4})],
    "rightArm": [(0, {"pitch": -30, "roll": 32}), (3, {"pitch": -18, "roll": 42}, INC), (10, {"pitch": -10, "roll": 20})],
    "leftArm": [(0, {"pitch": -30, "roll": -32}), (3, {"pitch": -18, "roll": -42}, INC), (10, {"pitch": -10, "roll": -20})],
    "torso": [(0, {"pitch": 6}), (3, {"pitch": -12}, INC), (10, {"pitch": -4})],
    "head": [(0, {"pitch": -6}), (3, {"pitch": 12}), (10, {"pitch": 2})],
})

gesture("ground", "Crouch and strike the ground with an open hand", 14, 20, {
    "rightArm": [(0, {"pitch": -160}), (3, {"pitch": -28, "yaw": -6}, INC), (10, {"pitch": -26}), (14, {"pitch": -20})],
    "leftArm": [(0, {"pitch": -40, "roll": -25}), (3, {"pitch": 15, "roll": -35}, INC), (14, {"pitch": 5, "roll": -15})],
    "torso": [(0, {"pitch": 4}), (3, {"pitch": -46}, INC), (10, {"pitch": -42}), (14, {"pitch": -12})],
    "head": [(0, {"pitch": -10}), (3, {"pitch": 30}), (14, {"pitch": 8})],
}, legs(-34, 26, 6))

gesture("roar", "Hunch, then throw the chest out and the head back", 18, 26, {
    "rightArm": [(0, {"pitch": -45, "yaw": -15}), (3, {"pitch": 28, "roll": 34}, BACK), (14, {"pitch": 24, "roll": 30}), (18, {"pitch": 8, "roll": 12})],
    "leftArm": [(0, {"pitch": -45, "yaw": 15}), (3, {"pitch": 28, "roll": -34}, BACK), (14, {"pitch": 24, "roll": -30}), (18, {"pitch": 8, "roll": -12})],
    "torso": [(0, {"pitch": -18}), (3, {"pitch": 14}, BACK), (14, {"pitch": 12}), (18, {"pitch": 4})],
    "head": [(0, {"pitch": 18}), (3, {"pitch": -38}, BACK), (14, {"pitch": -32}), (18, {"pitch": -8})],
}, legs(-10, 10, 10))

gesture("power", "Fists clenched low, the body tightening as power floods it", 14, 20, {
    "rightArm": [(0, {"pitch": -25, "roll": 8}), (3, {"pitch": 12, "roll": 26}, BACK), (11, {"pitch": 10, "roll": 24}), (14, {"pitch": 4, "roll": 10})],
    "leftArm": [(0, {"pitch": -25, "roll": -8}), (3, {"pitch": 12, "roll": -26}, BACK), (11, {"pitch": 10, "roll": -24}), (14, {"pitch": 4, "roll": -10})],
    "torso": [(0, {"pitch": -12}), (3, {"pitch": 8}, BACK), (14, {"pitch": 3})],
    "head": [(0, {"pitch": 14}), (3, {"pitch": -12}), (14, {"pitch": -2})],
}, legs(-6, 6, 12))

gesture("leap", "Arms swing up as the body rises", 12, 18, {
    "rightArm": [(0, {"pitch": 35}), (3, {"pitch": -172, "roll": -10}, OUTQ), (12, {"pitch": -150})],
    "leftArm": [(0, {"pitch": 35}), (3, {"pitch": -172, "roll": 10}, OUTQ), (12, {"pitch": -150})],
    "torso": [(0, {"pitch": -16}), (3, {"pitch": 8}, OUTQ), (12, {"pitch": 4})],
    "head": [(0, {"pitch": 10}), (3, {"pitch": -30}), (12, {"pitch": -20})],
    "leftLeg": [(0, {"pitch": -14}), (3, {"pitch": 10}), (12, {"pitch": 6})],
    "rightLeg": [(0, {"pitch": 10}), (3, {"pitch": 18}), (12, {"pitch": 10})],
})

gesture("vanish", "A low crouch behind a sweep of the arm", 10, 16, {
    "rightArm": [(0, {"pitch": -100, "yaw": -55}), (3, {"pitch": -35, "yaw": 45}, OUTQ), (10, {"pitch": -20, "yaw": 20})],
    "leftArm": [(0, {"pitch": -20, "roll": -15}), (3, {"pitch": 20, "roll": -25}), (10, {"pitch": 5})],
    "torso": [(0, {"pitch": -18}), (3, {"pitch": -32}, OUTQ), (10, {"pitch": -14})],
    "head": [(0, {"pitch": 16}), (3, {"pitch": 24}), (10, {"pitch": 6})],
}, legs(-28, 22))

gesture("place", "Kneel and set something on the ground", 12, 18, {
    "rightArm": [(0, {"pitch": -60}), (3, {"pitch": -38, "yaw": -8}, OUTQ), (9, {"pitch": -36}), (12, {"pitch": -25})],
    "leftArm": [(0, {"pitch": -10, "roll": -10}), (3, {"pitch": 12, "roll": -20}), (12, {"pitch": 4})],
    "torso": [(0, {"pitch": -14}), (3, {"pitch": -50}, OUTQ), (9, {"pitch": -48}), (12, {"pitch": -14})],
    "head": [(0, {"pitch": 10}), (3, {"pitch": 34}), (12, {"pitch": 8})],
}, legs(-36, 22))

# ---- throws and bows

gesture("throw", "Overarm throw", 12, 18, {
    "rightArm": [(0, {"pitch": -195, "roll": -12}), (3, {"pitch": -58, "yaw": -12}, OUTQ), (8, {"pitch": -45, "yaw": -15}), (12, {"pitch": -25})],
    "leftArm": [(0, {"pitch": -85, "yaw": -10}), (3, {"pitch": 12, "roll": -20}, OUTQ), (12, {"pitch": 5})],
    "torso": [(0, {"pitch": 10, "yaw": -26}), (3, {"pitch": -16, "yaw": 20}, OUTQ), (12, {"pitch": -5, "yaw": 6})],
    "head": [(0, {"pitch": -6}), (3, {"pitch": 4}), (12, {"pitch": 0})],
}, legs(-26, 18))

gesture("throw_side", "A flat throw from the hip, sweeping across", 10, 16, {
    "rightArm": [(0, {"pitch": -65, "yaw": 72}), (2, {"pitch": -88, "yaw": -45}, OUTQ), (7, {"pitch": -80, "yaw": -50}), (10, {"pitch": -40, "yaw": -15})],
    "leftArm": [(0, {"pitch": -40, "roll": -20}), (2, {"pitch": 15, "roll": -30}), (10, {"pitch": 5})],
    "torso": [(0, {"yaw": -36, "pitch": -6}), (2, {"yaw": 30, "pitch": -10}, OUTQ), (10, {"yaw": 8})],
}, legs(-18, 16))

gesture("bow", "Draw to the cheek and loose", 12, 18, {
    "rightArm": [(0, {"pitch": -92, "yaw": -6}), (5, {"pitch": -92, "yaw": -6}), (6, {"pitch": -96, "yaw": -8}, OUTQ), (12, {"pitch": -70})],
    "leftArm": [(0, {"pitch": -92, "yaw": 25}), (4, {"pitch": -88, "yaw": 62}, OUTQ), (5, {"pitch": -88, "yaw": 64}),
                (6, {"pitch": -62, "yaw": 85, "roll": -10}, OUTQ), (12, {"pitch": -30, "yaw": 30})],
    "torso": [(0, {"yaw": 10}), (4, {"yaw": 18, "pitch": 3}), (6, {"yaw": 22, "pitch": 6}, OUTQ), (12, {"yaw": 6})],
    "head": [(0, {"yaw": -8}), (4, {"yaw": -16}), (12, {"yaw": -4})],
}, legs(-16, 12, 6))

gesture("bow_sky", "Draw high and loose into the sky", 12, 18, {
    "rightArm": [(0, {"pitch": -150, "yaw": -4}), (5, {"pitch": -152}), (6, {"pitch": -158}, OUTQ), (12, {"pitch": -110})],
    "leftArm": [(0, {"pitch": -148, "yaw": 22}), (4, {"pitch": -140, "yaw": 48}, OUTQ), (5, {"pitch": -140, "yaw": 50}),
                (6, {"pitch": -110, "yaw": 60}, OUTQ), (12, {"pitch": -50, "yaw": 20})],
    "torso": [(0, {"pitch": 8, "yaw": 10}), (4, {"pitch": 12, "yaw": 16}), (6, {"pitch": 14, "yaw": 18}), (12, {"pitch": 4})],
    "head": [(0, {"pitch": -38}), (6, {"pitch": -42}), (12, {"pitch": -20})],
}, legs(-14, 12, 6))

# ---- spells

gesture("cast", "One hand thrust forward, palm out", 13, 19, {
    "rightArm": [(0, {"pitch": -55, "roll": 12}), (2, {"pitch": -96, "yaw": -6}, OUTQ), (9, {"pitch": -92, "yaw": -5}), (13, {"pitch": -55})],
    "leftArm": [(0, {"pitch": -30, "roll": -12}), (2, {"pitch": 12, "roll": -18}, OUTQ), (13, {"pitch": 4, "roll": -10})],
    "torso": [(0, {"yaw": -16, "pitch": 2}), (2, {"yaw": 12, "pitch": -6}, OUTQ), (9, {"yaw": 10}), (13, {"yaw": 3})],
    "head": [(0, {"yaw": 8}), (2, {"yaw": -6}), (13, {"yaw": 0})],
}, legs(-18, 12))

gesture("cast_both", "Both hands pushed forward together", 14, 20, {
    "rightArm": [(0, {"pitch": -48, "yaw": 22}), (2, {"pitch": -94, "yaw": -10}, OUTQ), (10, {"pitch": -90, "yaw": -8}), (14, {"pitch": -55})],
    "leftArm": [(0, {"pitch": -48, "yaw": -22}), (2, {"pitch": -94, "yaw": 10}, OUTQ), (10, {"pitch": -90, "yaw": 8}), (14, {"pitch": -55})],
    "torso": [(0, {"pitch": 6}), (2, {"pitch": -10}, OUTQ), (10, {"pitch": -8}), (14, {"pitch": -2})],
    "head": [(0, {"pitch": 4}), (2, {"pitch": -4}), (14, {"pitch": 0})],
}, legs(-22, 16))

gesture("raise", "An arm to the sky to call it down, then pointing at the mark", 16, 22, {
    "rightArm": [(0, {"pitch": -110}), (3, {"pitch": -176, "roll": -4}, OUTQ), (8, {"pitch": -172}), (11, {"pitch": -118}, INQ), (16, {"pitch": -100})],
    "leftArm": [(0, {"pitch": -15, "roll": -18}), (3, {"pitch": -30, "roll": -32}), (16, {"pitch": -12, "roll": -15})],
    "torso": [(0, {"pitch": 0}), (3, {"pitch": 8}, OUTQ), (8, {"pitch": 9}), (11, {"pitch": -4}), (16, {"pitch": 0})],
    "head": [(0, {"pitch": -12}), (3, {"pitch": -38}, OUTQ), (8, {"pitch": -40}), (11, {"pitch": -6}), (16, {"pitch": -4})],
}, legs(-12, 10, 6))

gesture("channel", "Both arms raised and open, held while the power gathers", 20, 28, {
    "rightArm": [(0, {"pitch": -60, "roll": 6}), (5, {"pitch": -132, "roll": -26}, OUTQ), (17, {"pitch": -128, "roll": -30}), (20, {"pitch": -110, "roll": -20})],
    "leftArm": [(0, {"pitch": -60, "roll": -6}), (5, {"pitch": -132, "roll": 26}, OUTQ), (17, {"pitch": -128, "roll": 30}), (20, {"pitch": -110, "roll": 20})],
    "torso": [(0, {"pitch": -4}), (5, {"pitch": 9}, OUTQ), (17, {"pitch": 10}), (20, {"pitch": 5})],
    "head": [(0, {"pitch": 0}), (5, {"pitch": -24}), (17, {"pitch": -26}), (20, {"pitch": -12})],
}, legs(-10, 10, 10))

gesture("burst", "Arms crossed over the chest, then flung wide", 12, 18, {
    "rightArm": [(0, {"pitch": -82, "yaw": -38}), (2, {"pitch": -18, "roll": 98}, BACK), (9, {"pitch": -15, "roll": 92}), (12, {"pitch": -8, "roll": 45})],
    "leftArm": [(0, {"pitch": -82, "yaw": 38}), (2, {"pitch": -18, "roll": -98}, BACK), (9, {"pitch": -15, "roll": -92}), (12, {"pitch": -8, "roll": -45})],
    "torso": [(0, {"pitch": -16}), (2, {"pitch": 10}, BACK), (9, {"pitch": 8}), (12, {"pitch": 2})],
    "head": [(0, {"pitch": 12}), (2, {"pitch": -18}), (12, {"pitch": -4})],
}, legs(-8, 8, 12))

gesture("pray", "Hands together at the chest, then opened to the sky", 18, 26, {
    "rightArm": [(0, {"pitch": -72, "yaw": -32}), (4, {"pitch": -78, "yaw": -34}), (8, {"pitch": -148, "roll": -38}, OUTQ),
                 (15, {"pitch": -145, "roll": -40}), (18, {"pitch": -110, "roll": -20})],
    "leftArm": [(0, {"pitch": -72, "yaw": 32}), (4, {"pitch": -78, "yaw": 34}), (8, {"pitch": -148, "roll": 38}, OUTQ),
                (15, {"pitch": -145, "roll": 40}), (18, {"pitch": -110, "roll": 20})],
    "torso": [(0, {"pitch": -6}), (4, {"pitch": -8}), (8, {"pitch": 8}, OUTQ), (18, {"pitch": 4})],
    "head": [(0, {"pitch": 18}), (4, {"pitch": 20}), (8, {"pitch": -30}, OUTQ), (15, {"pitch": -32}), (18, {"pitch": -12})],
})

gesture("guard", "The shield arm up in front, braced", 14, 20, {
    "leftArm": [(0, {"pitch": -55}), (2, {"pitch": -86, "yaw": 28}, OUTQ), (11, {"pitch": -84, "yaw": 26}), (14, {"pitch": -60, "yaw": 10})],
    "rightArm": [(0, {"pitch": -20}), (2, {"pitch": -34, "yaw": 12}, OUTQ), (14, {"pitch": -20})],
    "torso": [(0, {"pitch": -4}), (2, {"pitch": -14}, OUTQ), (11, {"pitch": -13}), (14, {"pitch": -4})],
    "head": [(0, {"pitch": 0}), (2, {"pitch": 6}), (14, {"pitch": 0})],
}, legs(-24, 18, 6))

gesture("point", "Point the arm at the mark", 14, 20, {
    "rightArm": [(0, {"pitch": -60, "roll": 10}), (2, {"pitch": -96, "yaw": -4}, OUTQ), (10, {"pitch": -94}), (14, {"pitch": -60})],
    "leftArm": [(0, {"pitch": -10, "roll": -8}), (2, {"pitch": 8, "roll": -14}), (14, {"pitch": 2})],
    "torso": [(0, {"yaw": -6}), (2, {"yaw": 14}, OUTQ), (14, {"yaw": 4})],
    "head": [(0, {"pitch": 2}), (2, {"pitch": -4}), (14, {"pitch": 0})],
}, legs(-10, 8))

gesture("summon", "Arms rise from low to high, palms up, calling something forth", 16, 24, {
    "rightArm": [(0, {"pitch": -28, "roll": 14}), (5, {"pitch": -138, "roll": -28}, OUTQ), (13, {"pitch": -135, "roll": -30}), (16, {"pitch": -100, "roll": -15})],
    "leftArm": [(0, {"pitch": -28, "roll": -14}), (5, {"pitch": -138, "roll": 28}, OUTQ), (13, {"pitch": -135, "roll": 30}), (16, {"pitch": -100, "roll": 15})],
    "torso": [(0, {"pitch": -14}), (5, {"pitch": 10}, OUTQ), (13, {"pitch": 9}), (16, {"pitch": 3})],
    "head": [(0, {"pitch": 10}), (5, {"pitch": -26}), (16, {"pitch": -8})],
}, legs(-10, 10, 8))

gesture("sweep", "An open hand swept across, left to right", 12, 18, {
    "rightArm": [(0, {"pitch": -82, "yaw": -62}), (4, {"pitch": -86, "yaw": 70}, OUTQ), (9, {"pitch": -80, "yaw": 72}), (12, {"pitch": -50, "yaw": 30})],
    "leftArm": [(0, {"pitch": -10, "roll": -15}), (4, {"pitch": 10, "roll": -25}), (12, {"pitch": 2})],
    "torso": [(0, {"yaw": 24}), (4, {"yaw": -24}, OUTQ), (12, {"yaw": -8})],
}, legs(-14, 12))


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, data in G.items():
        with open(os.path.join(OUT, "skill_" + name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
    print("wrote", len(G), "skill gestures to", OUT)


if __name__ == "__main__":
    main()
