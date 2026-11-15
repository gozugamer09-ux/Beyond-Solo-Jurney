#!/usr/bin/env python3
"""Body animations for the weapon signature arts (playerAnimator emote JSON).

Poses are written in degrees and saved in radians. Positions are absolute (the part's pivot), like Better Combat's
files. Sign conventions, as playerAnimator applies them:
  arms/legs pitch: negative raises the limb forward (-90 straight ahead, -180 straight up), positive swings it back
  rightArm roll: positive lifts it out to the side; leftArm roll: negative lifts it out
  torso pitch: negative leans forward; torso yaw: positive turns the upper body to the left
  head pitch: negative looks up

Each move lists keyframes per part: (tick, {axis: value}). The strike frame matches the impact tick in
ArsenalAbilities, so the hit lands as the weapon comes down.

Run from the repo root:  python3 tools/gen_signature_anims.py
"""
import json
import math
import os

OUT = "src/main/resources/assets/beyondsolo/player_animations"

PIVOTS = {
    "head": (0, 0, 0),
    "torso": (0, 0, 0),
    "rightArm": (-5, 2, 0),
    "leftArm": (5, 2, 0),
    "rightLeg": (-1.9, 12, 0.1),
    "leftLeg": (1.9, 12, 0.1),
}
ROT = ("pitch", "yaw", "roll")


def emote(name, description, end, stop, frames, easing="EASEINOUTQUAD"):
    """frames: {part: [(tick, {axis: value}, easing?), ...]}."""
    moves = []
    for part, keys in frames.items():
        px, py, pz = PIVOTS[part]
        for key in keys:
            tick, pose = key[0], key[1]
            ease = key[2] if len(key) > 2 else easing
            for axis, value in pose.items():
                if axis in ROT:
                    v = math.radians(value)
                else:
                    v = value + {"x": px, "y": py, "z": pz}[axis]
                moves.append({"tick": tick, "easing": ease, "turn": 0, part: {axis: round(v, 4)}})
            # keep the pivot where it belongs on every keyframe, as Better Combat's files do
            for axis, base in (("x", px), ("y", py), ("z", pz)):
                if axis not in pose and part not in ("head", "torso"):
                    moves.append({"tick": tick, "easing": ease, "turn": 0, part: {axis: base}})
    first = min(m["tick"] for m in moves)
    return {
        "name": name,
        "author": "Beyond Solo",
        "description": description,
        "emote": {
            "isLoop": "false",
            "returnTick": 0,
            "beginTick": first,
            "endTick": end,
            "stopTick": stop,
            "degrees": False,
            # a keyframe's easing shapes the move that arrives at it (a wind-up eases in, a strike snaps)
            "easeBeforeKeyframe": True,
            "moves": moves,
        },
    }


ANIMS = {}

# Hellsovereign's Fang: hunch with the arms crossed over the chest, then fling them up and out as the fire wakes.
ANIMS["infernal_rend"] = emote("infernal_rend", "Cross the arms, then fling them wide as hellfire wakes", 20, 28, {
    "rightArm": [(1, {"pitch": -75, "yaw": -35, "roll": 0}), (6, {"pitch": -85, "yaw": -50, "roll": 0}),
                 (8, {"pitch": -150, "yaw": 0, "roll": -50}, "EASEOUTBACK"), (18, {"pitch": -145, "yaw": 0, "roll": -45})],
    "leftArm": [(1, {"pitch": -75, "yaw": 35, "roll": 0}), (6, {"pitch": -85, "yaw": 50, "roll": 0}),
                (8, {"pitch": -150, "yaw": 0, "roll": 50}, "EASEOUTBACK"), (18, {"pitch": -145, "yaw": 0, "roll": 45})],
    "torso": [(1, {"pitch": -12}), (6, {"pitch": -18}), (8, {"pitch": 10}, "EASEOUTQUAD"), (18, {"pitch": 6})],
    "head": [(1, {"pitch": 18}), (6, {"pitch": 25}), (8, {"pitch": -30}, "EASEOUTQUAD"), (18, {"pitch": -20})],
    "rightLeg": [(1, {"roll": 4}), (8, {"roll": 14}), (18, {"roll": 12})],
    "leftLeg": [(1, {"roll": -4}), (8, {"roll": -14}), (18, {"roll": -12})],
})

# Winterborne Requiem: raise the lance point-down overhead, then drive it into the ground with a long step.
ANIMS["absolute_zero"] = emote("absolute_zero", "Raise the lance overhead, then drive it into the ground", 22, 30, {
    "rightArm": [(2, {"pitch": -120, "roll": 10}), (8, {"pitch": -175, "roll": 5}),
                 (10, {"pitch": -35, "roll": -5}, "EASEINCUBIC"), (20, {"pitch": -40, "roll": -5})],
    "leftArm": [(2, {"pitch": -60, "roll": -30}), (8, {"pitch": -80, "roll": -50}),
                (10, {"pitch": -55, "roll": -85}, "EASEOUTQUAD"), (20, {"pitch": -50, "roll": -80})],
    "torso": [(2, {"pitch": 6}), (8, {"pitch": 12}), (10, {"pitch": -22}, "EASEINCUBIC"), (20, {"pitch": -18})],
    "head": [(2, {"pitch": -15}), (8, {"pitch": -25}), (10, {"pitch": 25}, "EASEOUTQUAD"), (20, {"pitch": 15})],
    "leftLeg": [(2, {"pitch": -10}), (10, {"pitch": -45}, "EASEOUTQUAD"), (20, {"pitch": -40})],
    "rightLeg": [(2, {"pitch": 5}), (10, {"pitch": 30}, "EASEOUTQUAD"), (20, {"pitch": 28})],
})

# Stormcaller's Edict: the sword slowly rises to the sky, the free hand opens, the gaze follows; then the sky answers.
ANIMS["heavens_verdict"] = emote("heavens_verdict", "Raise the blade to the sky and call the storm down", 24, 34, {
    "rightArm": [(2, {"pitch": -60, "roll": 5}), (11, {"pitch": -178, "roll": 0}, "EASEOUTQUAD"),
                 (14, {"pitch": -170, "roll": 6}, "EASEOUTBACK"), (22, {"pitch": -172, "roll": 4})],
    "leftArm": [(2, {"pitch": -20, "roll": -25}), (11, {"pitch": -40, "roll": -70}),
                (14, {"pitch": -30, "roll": -80}), (22, {"pitch": -30, "roll": -75})],
    "torso": [(2, {"pitch": 4}), (11, {"pitch": 14}), (14, {"pitch": 8}, "EASEOUTBACK"), (22, {"pitch": 10})],
    "head": [(2, {"pitch": -10}), (11, {"pitch": -45}), (14, {"pitch": -35}), (22, {"pitch": -40})],
    "rightLeg": [(2, {"roll": 6}), (14, {"roll": 9}), (22, {"roll": 9})],
    "leftLeg": [(2, {"roll": -6}), (14, {"roll": -9}), (22, {"roll": -9})],
})

# Ruinbringer: coil the whole body to the right, then unleash a huge sweep to the left.
ANIMS["cataclysm_cleave"] = emote("cataclysm_cleave", "Coil to the side, then a ground-tearing horizontal sweep", 20, 30, {
    "rightArm": [(1, {"pitch": -90, "yaw": 45}), (7, {"pitch": -100, "yaw": 75}),
                 (10, {"pitch": -90, "yaw": -50}, "EASEINCUBIC"), (18, {"pitch": -75, "yaw": -60})],
    "leftArm": [(1, {"pitch": -80, "yaw": 55}), (7, {"pitch": -90, "yaw": 80}),
                (10, {"pitch": -85, "yaw": -35}, "EASEINCUBIC"), (18, {"pitch": -65, "yaw": -45})],
    "torso": [(1, {"yaw": -30, "pitch": -5}), (7, {"yaw": -55, "pitch": -8}),
              (10, {"yaw": 55, "pitch": -15}, "EASEINCUBIC"), (18, {"yaw": 60, "pitch": -12})],
    "head": [(1, {"yaw": -10}), (7, {"yaw": -20}), (10, {"yaw": 20}, "EASEINCUBIC"), (18, {"yaw": 15})],
    "leftLeg": [(1, {"pitch": -15}), (10, {"pitch": -30}), (18, {"pitch": -28})],
    "rightLeg": [(1, {"pitch": 10}), (10, {"pitch": 25}), (18, {"pitch": 22})],
})

# Calamity Fang: drop into a low lunge with the blade trailing, then flash forward in one cut.
ANIMS["dragons_wrath"] = emote("dragons_wrath", "A low lunge with the blade trailing, then one flashing cut", 14, 22, {
    "rightArm": [(1, {"pitch": 20, "roll": 25}), (3, {"pitch": 35, "roll": 30}),
                 (5, {"pitch": -95, "yaw": -45}, "EASEOUTQUAD"), (12, {"pitch": -80, "yaw": -55})],
    "leftArm": [(1, {"pitch": -55, "roll": -10}), (3, {"pitch": -65, "roll": -15}),
                (5, {"pitch": 25, "roll": -20}, "EASEOUTQUAD"), (12, {"pitch": 15, "roll": -15})],
    "torso": [(1, {"pitch": -25, "yaw": -15}), (3, {"pitch": -35, "yaw": -20}),
              (5, {"pitch": -15, "yaw": 35}, "EASEOUTQUAD"), (12, {"pitch": -10, "yaw": 25})],
    "head": [(1, {"pitch": -15}), (3, {"pitch": -20}), (5, {"pitch": -5, "yaw": -25}), (12, {"pitch": 0, "yaw": -15})],
    "leftLeg": [(1, {"pitch": -35}), (3, {"pitch": -45}), (5, {"pitch": -25}), (12, {"pitch": -15})],
    "rightLeg": [(1, {"pitch": 30}), (3, {"pitch": 40}), (5, {"pitch": 25}), (12, {"pitch": 15})],
})

# Eye of the Insatiable: aim the staff, pull it back as the star gathers, thrust, and rock back from the recoil.
ANIMS["devouring_star"] = emote("devouring_star", "Aim, gather the star, thrust and take the recoil", 18, 28, {
    "rightArm": [(2, {"pitch": -90, "roll": -10}), (7, {"pitch": -70, "roll": -15}),
                 (9, {"pitch": -100, "roll": -8}, "EASEOUTQUAD"), (11, {"pitch": -125, "roll": -5}, "EASEOUTQUAD"),
                 (16, {"pitch": -95, "roll": -8})],
    "leftArm": [(2, {"pitch": -90, "roll": 10}), (7, {"pitch": -70, "roll": 15}),
                (9, {"pitch": -100, "roll": 8}, "EASEOUTQUAD"), (11, {"pitch": -125, "roll": 5}, "EASEOUTQUAD"),
                (16, {"pitch": -95, "roll": 8})],
    "torso": [(2, {"pitch": -4}), (7, {"pitch": 6}), (9, {"pitch": -14}, "EASEOUTQUAD"),
              (11, {"pitch": 12}, "EASEOUTQUAD"), (16, {"pitch": 0})],
    "head": [(2, {"pitch": 0}), (9, {"pitch": 5}), (11, {"pitch": -12}), (16, {"pitch": 0})],
    "leftLeg": [(2, {"pitch": -20}), (9, {"pitch": -25}), (16, {"pitch": -20})],
    "rightLeg": [(2, {"pitch": 15}), (9, {"pitch": 22}), (16, {"pitch": 18})],
})

# Colossus Aegis: heave the hammer overhead, slam it down on a wide stance, then rise into a guard.
ANIMS["colossus_stance"] = emote("colossus_stance", "Heave the hammer up, slam it down, rise into a guard", 26, 36, {
    "rightArm": [(2, {"pitch": -130}), (7, {"pitch": -175}), (9, {"pitch": -45}, "EASEINCUBIC"),
                 (14, {"pitch": -40}), (20, {"pitch": -70, "roll": 20}), (24, {"pitch": -70, "roll": 20})],
    "leftArm": [(2, {"pitch": -130}), (7, {"pitch": -175}), (9, {"pitch": -45}, "EASEINCUBIC"),
                (14, {"pitch": -40}), (20, {"pitch": -95, "roll": 25}), (24, {"pitch": -95, "roll": 25})],
    "torso": [(2, {"pitch": 6}), (7, {"pitch": 14}), (9, {"pitch": -28, "y": 1.5}, "EASEINCUBIC"),
              (14, {"pitch": -24, "y": 1.5}), (20, {"pitch": -4, "y": 0}), (24, {"pitch": -4, "y": 0})],
    "head": [(2, {"pitch": -15}), (7, {"pitch": -25}), (9, {"pitch": 20}), (14, {"pitch": 15}), (20, {"pitch": 0})],
    "rightLeg": [(2, {"roll": 8}), (9, {"roll": 22}, "EASEOUTQUAD"), (24, {"roll": 18})],
    "leftLeg": [(2, {"roll": -8}), (9, {"roll": -22}, "EASEOUTQUAD"), (24, {"roll": -18})],
})

# Branch of the World Tree: lift the branch skyward, palm open, then open both arms wide as the blessing blooms.
ANIMS["verdant_blessing"] = emote("verdant_blessing", "Lift the branch to the sky, then open the arms as it blooms", 24, 34, {
    "rightArm": [(2, {"pitch": -90, "roll": 0}), (10, {"pitch": -150, "roll": 5}),
                 (12, {"pitch": -130, "roll": -60}, "EASEOUTBACK"), (22, {"pitch": -125, "roll": -55})],
    "leftArm": [(2, {"pitch": -60, "roll": -20}), (10, {"pitch": -90, "roll": -30}),
                (12, {"pitch": -130, "roll": 60}, "EASEOUTBACK"), (22, {"pitch": -125, "roll": 55})],
    "torso": [(2, {"pitch": 2}), (10, {"pitch": 8}), (12, {"pitch": 12}), (22, {"pitch": 10})],
    "head": [(2, {"pitch": -10}), (10, {"pitch": -30}), (12, {"pitch": -40}), (22, {"pitch": -35})],
})


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, data in ANIMS.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
    print("wrote", len(ANIMS), "animations to", OUT)


if __name__ == "__main__":
    main()
