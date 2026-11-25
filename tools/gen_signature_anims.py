#!/usr/bin/env python3
"""Body animations for the weapon signature arts (playerAnimator emote JSON).

Every right-click art has one: a wind-up the world can read, the strike frame on the art's impact tick (the first blow
lands then, see Signature.impactTicks) and, for the arts that come in beats, a move on every beat: the Fang's three
cuts, the Titan's six punches and the uppercut, the wielder rising as the fissure erupts, the hand closing as the
star collapses. The bows' drawn arts have none: the draw is the bow's own.

Same conventions and builder as tools/gen_skill_anims.py (degrees here, radians in the files):
  arms/legs pitch: negative raises the limb forward (-90 straight ahead, -180 straight up), positive swings it back
  arm roll lifts it out to the side (right arm +, left arm -) while it hangs down; raised overhead the sign flips
  arm yaw: + swings the arm towards the body's right
  torso: the whole player turned about the hips; pitch negative leans forward, yaw positive turns to the left. The
    legs are planted against every lean, so leg pitches are written as they should look in the world.
  head pitch: negative looks up; head yaw positive turns it to the left
  body yaw: spins the whole player. A whole turn ends at 360 and is held there until the animation stops (360 is 0),
    so the turn never unwinds on the way out.

Run from the repo root:  python3 tools/gen_signature_anims.py
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_skill_anims import emote, OUT, OUTQ, INQ, INC, BACK  # noqa: E402

SIGNATURES = "src/main/java/com/beyondsolo/item/arsenal/Signature.java"

A = {}
STRIKE = {}


def legs(front=-18, back=14, spread=0):
    """A stance held through the whole move: left foot forward, right foot back."""
    return {
        "leftLeg": [(0, {"pitch": front, "roll": -spread})],
        "rightLeg": [(0, {"pitch": back, "roll": spread})],
    }


def art(key, description, strike, end, stop, frames, stance=None):
    parts = dict(frames)
    for part, keys in (stance or {}).items():
        parts.setdefault(part, keys)
    A[key] = emote(key, description, end, stop, parts)
    STRIKE[key] = strike


# ================================================================== mythic

# Hellsovereign's Fang: the blade cocked high on the right, then a cut of hellfire on each beat: down across from the
# right, back up across from the left, and one wide level sweep.
art("infernal_rend", "Three cuts of hellfire: a diagonal, its backhand, then a wide sweep", 8, 24, 32, {
    "rightArm": [(0, {"pitch": -120, "yaw": 45}), (6, {"pitch": -165, "yaw": 40, "roll": -10}),
                 (8, {"pitch": -55, "yaw": -55, "roll": 0}, OUTQ), (10, {"pitch": -150, "yaw": -45}),
                 (11, {"pitch": -55, "yaw": 65}, OUTQ), (13, {"pitch": -95, "yaw": 78}),
                 (14, {"pitch": -90, "yaw": -72}, OUTQ), (20, {"pitch": -78, "yaw": -66}), (24, {"pitch": -55, "yaw": -35})],
    "leftArm": [(0, {"pitch": -45, "roll": -20}), (6, {"pitch": -60, "roll": -25}), (8, {"pitch": 15, "roll": -30}, OUTQ),
                (11, {"pitch": -55, "roll": -12}, OUTQ), (14, {"pitch": 22, "roll": -38}, OUTQ), (24, {"pitch": 8, "roll": -20})],
    "torso": [(0, {"yaw": -25, "pitch": 2}), (6, {"yaw": -35, "pitch": 4}), (8, {"yaw": 26, "pitch": -14}, OUTQ),
              (10, {"yaw": 30, "pitch": -6}), (11, {"yaw": -24, "pitch": -12}, OUTQ), (13, {"yaw": -36, "pitch": -6}),
              (14, {"yaw": 40, "pitch": -12}, OUTQ), (20, {"yaw": 32, "pitch": -9}), (24, {"yaw": 14, "pitch": -4})],
    "head": [(0, {"yaw": 14, "pitch": -4}), (6, {"yaw": 22}), (8, {"yaw": -14, "pitch": 6}, OUTQ), (11, {"yaw": 14}, OUTQ),
             (14, {"yaw": -20, "pitch": 4}, OUTQ), (24, {"yaw": -8, "pitch": 0})],
}, legs(-24, 18, 6))

# Winterborne Requiem: the lance raised point-down in both hands and driven into the ground on a deep step, held
# there while the frost spreads; when the requiem shatters the ice the wielder tears free and throws the free arm wide.
art("absolute_zero", "Drive the lance into the ground, hold, then tear free as the ice shatters", 12, 46, 54, {
    "rightArm": [(0, {"pitch": -110, "roll": 8}), (9, {"pitch": -176, "roll": 2}, OUTQ), (12, {"pitch": -38, "yaw": -4}, INC),
                 (36, {"pitch": -40, "yaw": -4}), (40, {"pitch": -55}), (42, {"pitch": -95, "roll": 25}, OUTQ),
                 (46, {"pitch": -80, "roll": 15})],
    "leftArm": [(0, {"pitch": -100, "roll": -8}), (9, {"pitch": -170, "roll": -2}, OUTQ), (12, {"pitch": -44, "yaw": 10}, INC),
                (36, {"pitch": -46, "yaw": 10}), (40, {"pitch": -50, "yaw": 8}), (42, {"pitch": -25, "roll": -95}, BACK),
                (46, {"pitch": -20, "roll": -80})],
    "torso": [(0, {"pitch": 2}), (9, {"pitch": 12}, OUTQ), (12, {"pitch": -34}, INC), (36, {"pitch": -30}), (40, {"pitch": -20}),
              (42, {"pitch": 6}, BACK), (46, {"pitch": 2})],
    "head": [(0, {"pitch": -10}), (9, {"pitch": -28}), (12, {"pitch": 26}, INC), (36, {"pitch": 20}), (40, {"pitch": 6}),
             (42, {"pitch": -18}, BACK), (46, {"pitch": -10})],
    "leftLeg": [(0, {"pitch": -14}), (12, {"pitch": -44}, INC), (40, {"pitch": -38}), (42, {"pitch": -24}), (46, {"pitch": -20})],
    "rightLeg": [(0, {"pitch": 8}), (12, {"pitch": 30}, INC), (40, {"pitch": 26}), (42, {"pitch": 16}), (46, {"pitch": 14})],
})

# Stormcaller's Edict: the blade rises slowly to the sky, the free hand opens, the gaze follows; the storm answers on
# the strike frame, and on the verdict the blade comes down to point at the one the pillar falls on.
art("heavens_verdict", "Raise the blade to the sky, then bring it down on the verdict", 14, 34, 42, {
    "rightArm": [(0, {"pitch": -60, "roll": 5}), (11, {"pitch": -178, "roll": 0}, OUTQ), (14, {"pitch": -170, "roll": 6}, BACK),
                 (21, {"pitch": -176, "roll": 2}), (24, {"pitch": -78, "yaw": -6, "roll": 0}, INC), (32, {"pitch": -82, "yaw": -6})],
    "leftArm": [(0, {"pitch": -20, "roll": -25}), (11, {"pitch": -40, "roll": -70}), (14, {"pitch": -30, "roll": -82}, BACK),
                (21, {"pitch": -28, "roll": -78}), (24, {"pitch": 12, "roll": -34}, INC), (32, {"pitch": 8, "roll": -28})],
    "torso": [(0, {"pitch": 0}), (11, {"pitch": 8}), (14, {"pitch": 6}, BACK), (21, {"pitch": 9}), (24, {"pitch": -12}, INC),
              (32, {"pitch": -8})],
    "head": [(0, {"pitch": -8}), (11, {"pitch": -44}), (14, {"pitch": -36}, BACK), (21, {"pitch": -40}), (24, {"pitch": 0}, INC),
             (32, {"pitch": -2})],
}, legs(-14, 12, 8))

# ================================================================== divine

# Ruinbringer: the great blade heaved up behind the head on a long breath and brought down in one cleave that splits
# the ground on the strike frame; the wielder stays bent over it until the fissure erupts, then rises out of the fire.
art("cataclysm_cleave", "Heave the blade overhead, cleave the ground, rise as the fissure erupts", 14, 34, 44, {
    "rightArm": [(0, {"pitch": -120, "yaw": 8}), (10, {"pitch": -192, "yaw": 0}, OUTQ), (12, {"pitch": -196}),
                 (14, {"pitch": -34, "yaw": -4}, INC), (20, {"pitch": -36}), (22, {"pitch": -55}, BACK), (30, {"pitch": -50})],
    "leftArm": [(0, {"pitch": -115, "yaw": -8}), (10, {"pitch": -188, "yaw": 0}, OUTQ), (12, {"pitch": -192}),
                (14, {"pitch": -38, "yaw": 12}, INC), (20, {"pitch": -40, "yaw": 12}), (22, {"pitch": -50, "yaw": 8}, BACK),
                (30, {"pitch": -45, "yaw": 6})],
    "torso": [(0, {"pitch": 4, "yaw": -8}), (10, {"pitch": 14, "yaw": -4}, OUTQ), (12, {"pitch": 16}),
              (14, {"pitch": -40, "yaw": 0}, INC), (20, {"pitch": -38}), (22, {"pitch": -18}, BACK), (30, {"pitch": -12})],
    "head": [(0, {"pitch": -8}), (10, {"pitch": -22}), (14, {"pitch": 28}, INC), (20, {"pitch": 22}), (22, {"pitch": -8}, BACK),
             (30, {"pitch": -4})],
    "leftLeg": [(0, {"pitch": -16, "roll": -4}), (10, {"pitch": -18, "roll": -6}), (14, {"pitch": -42, "roll": -8}, INC),
                (22, {"pitch": -36, "roll": -8}), (30, {"pitch": -30, "roll": -6})],
    "rightLeg": [(0, {"pitch": 10, "roll": 4}), (10, {"pitch": 12, "roll": 6}), (14, {"pitch": 30, "roll": 8}, INC),
                 (22, {"pitch": 26, "roll": 8}), (30, {"pitch": 22, "roll": 6})],
})

# Calamity Fang: drop into a low lunge with the blade trailing, flash forward in one cut on the strike frame (the dash),
# hold the finish with the back to the cut foes, and flick the blood off the blade as their wounds open behind.
art("dragons_wrath", "A low lunge, one flashing cut, then the blade flicked clean as the wounds open", 6, 26, 34, {
    "rightArm": [(0, {"pitch": 20, "roll": 25}), (4, {"pitch": 38, "roll": 30}), (6, {"pitch": -96, "yaw": -55, "roll": 0}, OUTQ),
                 (13, {"pitch": -82, "yaw": -60}), (16, {"pitch": -64, "yaw": -40}),
                 (18, {"pitch": -18, "yaw": 30, "roll": 30}, OUTQ), (24, {"pitch": -14, "yaw": 26, "roll": 26})],
    "leftArm": [(0, {"pitch": -55, "roll": -10}), (4, {"pitch": -65, "roll": -15}), (6, {"pitch": 25, "roll": -20}, OUTQ),
                (13, {"pitch": 15, "roll": -15}), (18, {"pitch": 4, "roll": -10}), (24, {"pitch": 2, "roll": -8})],
    "torso": [(0, {"pitch": -20, "yaw": -15}), (4, {"pitch": -30, "yaw": -20}), (6, {"pitch": -18, "yaw": 35}, OUTQ),
              (13, {"pitch": -12, "yaw": 30}), (16, {"pitch": -8, "yaw": 22}), (18, {"pitch": -4, "yaw": 8}, OUTQ),
              (24, {"pitch": -2, "yaw": 6})],
    "head": [(0, {"pitch": -12}), (4, {"pitch": -18}), (6, {"pitch": -5, "yaw": -25}, OUTQ), (13, {"pitch": 0, "yaw": -18}),
             (18, {"pitch": 6, "yaw": -4}, OUTQ), (24, {"pitch": 4, "yaw": -2})],
    "leftLeg": [(0, {"pitch": -36}), (4, {"pitch": -42}), (6, {"pitch": -30}, OUTQ), (13, {"pitch": -24}), (18, {"pitch": -14}),
                (24, {"pitch": -12})],
    "rightLeg": [(0, {"pitch": 28}), (4, {"pitch": 34}), (6, {"pitch": 26}, OUTQ), (13, {"pitch": 20}), (18, {"pitch": 12}),
                 (24, {"pitch": 10})],
})

# Eye of the Insatiable: aim the staff in both hands, draw back as the star is born at its tip, thrust it away on the
# strike frame and rock back from the recoil; then the free hand reaches after it and closes as the star collapses.
art("devouring_star", "Aim, thrust the star away, then close the hand as it collapses", 12, 36, 44, {
    "rightArm": [(0, {"pitch": -90, "roll": -10}), (9, {"pitch": -68, "roll": -15}), (12, {"pitch": -102, "roll": -8}, OUTQ),
                 (14, {"pitch": -126, "roll": -5}, OUTQ), (20, {"pitch": -92, "roll": -8}), (32, {"pitch": -88, "roll": -8})],
    "leftArm": [(0, {"pitch": -90, "roll": 10}), (9, {"pitch": -68, "roll": 15}), (12, {"pitch": -102, "roll": 8}, OUTQ),
                (14, {"pitch": -126, "roll": 5}, OUTQ), (20, {"pitch": -70, "yaw": -10, "roll": 0}), (26, {"pitch": -98, "yaw": 6}),
                (28, {"pitch": -62, "yaw": 42}, INC), (34, {"pitch": -58, "yaw": 40})],
    "torso": [(0, {"pitch": -4}), (9, {"pitch": 8}), (12, {"pitch": -14}, OUTQ), (14, {"pitch": 12}, OUTQ), (20, {"pitch": 0}),
              (26, {"pitch": -6}), (28, {"pitch": 4}, INC), (34, {"pitch": 2})],
    "head": [(0, {"pitch": 0}), (12, {"pitch": 5}), (14, {"pitch": -12}), (20, {"pitch": -4}), (28, {"pitch": 4}, INC),
             (34, {"pitch": 0})],
}, legs(-20, 16, 4))

# Colossus Aegis: heave the hammer high on a wide stance, slam it down on the strike frame (the hammer of light comes
# down with it), stay low over it a breath, then rise into a braced guard: the bulwark stands.
art("colossus_stance", "Heave the hammer up, slam it down, rise into a braced guard", 12, 32, 42, {
    "rightArm": [(0, {"pitch": -120}), (9, {"pitch": -186}, OUTQ), (12, {"pitch": -30}, INC), (18, {"pitch": -32}),
                 (24, {"pitch": -62, "yaw": -18}, OUTQ), (30, {"pitch": -60, "yaw": -18})],
    "leftArm": [(0, {"pitch": -120}), (9, {"pitch": -182}, OUTQ), (12, {"pitch": -34}, INC), (18, {"pitch": -36}),
                (24, {"pitch": -88, "yaw": 30}, OUTQ), (30, {"pitch": -86, "yaw": 28})],
    "torso": [(0, {"pitch": 4}), (9, {"pitch": 16}, OUTQ), (12, {"pitch": -40}, INC), (18, {"pitch": -36}), (24, {"pitch": -10}, OUTQ),
              (30, {"pitch": -8})],
    "head": [(0, {"pitch": -10}), (9, {"pitch": -26}), (12, {"pitch": 26}, INC), (18, {"pitch": 20}), (24, {"pitch": 2}),
             (30, {"pitch": 0})],
    "leftLeg": [(0, {"pitch": -12, "roll": -8}), (12, {"pitch": -30, "roll": -18}, INC), (24, {"pitch": -22, "roll": -14}),
                (30, {"pitch": -20, "roll": -14})],
    "rightLeg": [(0, {"pitch": 8, "roll": 8}), (12, {"pitch": 22, "roll": 18}, INC), (24, {"pitch": 16, "roll": 14}),
                 (30, {"pitch": 14, "roll": 14})],
})

# Branch of the World Tree: kneel and lay the free hand on the ground as the roots run out, rise lifting the branch to
# the sky, and open both arms wide as the tree blooms on the strike frame.
art("verdant_blessing", "Kneel with a hand on the ground, rise with the branch, open the arms as it blooms", 16, 32, 42, {
    "rightArm": [(0, {"pitch": -60, "yaw": -10}), (6, {"pitch": -40, "yaw": -6}, OUTQ), (10, {"pitch": -42, "yaw": -6}),
                 (14, {"pitch": -176, "yaw": 0, "roll": 0}, OUTQ), (16, {"pitch": -142, "roll": -55}, BACK),
                 (28, {"pitch": -136, "roll": -50})],
    "leftArm": [(0, {"pitch": -30, "roll": -10}), (6, {"pitch": -22, "yaw": -6}, OUTQ), (10, {"pitch": -24, "yaw": -6}),
                (14, {"pitch": -120, "yaw": 0, "roll": 20}, OUTQ), (16, {"pitch": -142, "roll": 55}, BACK),
                (28, {"pitch": -136, "roll": 50})],
    "torso": [(0, {"pitch": -10}), (6, {"pitch": -40}, OUTQ), (10, {"pitch": -38}), (14, {"pitch": 6}, OUTQ), (16, {"pitch": 12}, BACK),
              (28, {"pitch": 10})],
    "head": [(0, {"pitch": 10}), (6, {"pitch": 26}), (10, {"pitch": 24}), (14, {"pitch": -34}), (16, {"pitch": -40}, BACK),
             (28, {"pitch": -34})],
    "leftLeg": [(0, {"pitch": -16}), (6, {"pitch": -40}, OUTQ), (10, {"pitch": -40}), (14, {"pitch": -14}, OUTQ), (28, {"pitch": -10})],
    "rightLeg": [(0, {"pitch": 10}), (6, {"pitch": 30}, OUTQ), (10, {"pitch": 30}), (14, {"pitch": 10}, OUTQ), (28, {"pitch": 8})],
})

# ================================================================== legendary

# Stormlord's Longsword: coil to the right with the blade held out wide, then one whole turn of the body throws the
# ring of blades on the strike frame, and the cut finishes across the front.
art("stormbreak", "Coil, then one whole turn of the body with the blade held out", 9, 18, 24, {
    "body": [(0, {"yaw": 0}), (5, {"yaw": -55}, OUTQ), (7, {"yaw": -62}), (9, {"yaw": 150}, INQ), (11, {"yaw": 360}, OUTQ),
             (24, {"yaw": 360})],
    "rightArm": [(0, {"pitch": -70, "yaw": 20}), (5, {"pitch": -96, "yaw": 74}, OUTQ), (9, {"pitch": -92, "yaw": 58}),
                 (11, {"pitch": -86, "yaw": -45}, OUTQ), (16, {"pitch": -70, "yaw": -40})],
    "leftArm": [(0, {"pitch": -30, "roll": -20}), (5, {"pitch": -34, "roll": -62}, OUTQ), (11, {"pitch": -24, "roll": -58}),
                (16, {"pitch": -12, "roll": -26})],
    "torso": [(0, {"pitch": -4}), (5, {"pitch": -12}), (9, {"pitch": -16}), (11, {"pitch": -14}), (16, {"pitch": -6})],
    "head": [(0, {"pitch": 0, "yaw": 0}), (5, {"pitch": 6, "yaw": 20}), (9, {"pitch": 4, "yaw": 0}), (11, {"yaw": -10}),
             (16, {"yaw": -4, "pitch": 0})],
}, legs(-16, 14, 10))

# Titan's Knuckles: a fighter's crouch, six straight punches right, left, right... on the beats, then the right fist
# drops low and comes up in the uppercut that throws everything in front into the air.
barrage = {
    "rightArm": [(0, {"pitch": -58, "yaw": -24})],
    "leftArm": [(0, {"pitch": -58, "yaw": 24})],
    "torso": [(0, {"pitch": -12, "yaw": 0}), (3, {"pitch": -14, "yaw": -8})],
    "head": [(0, {"pitch": 6}), (14, {"pitch": 6})],
}
for i in range(6):
    t = 4 + 2 * i
    hit, guard, s = ("rightArm", "leftArm", 1) if i % 2 == 0 else ("leftArm", "rightArm", -1)
    barrage[hit] += [(t, {"pitch": -96, "yaw": -6 * s}, OUTQ), (t + 1, {"pitch": -70, "yaw": -18 * s})]
    barrage[guard] += [(t, {"pitch": -50, "yaw": 24 * s}, OUTQ)]
    barrage["torso"] += [(t, {"pitch": -14, "yaw": 16 * s}, OUTQ)]
barrage["rightArm"] += [(16, {"pitch": -8, "yaw": -12}), (18, {"pitch": -172, "yaw": -6}, OUTQ), (24, {"pitch": -165, "yaw": -6})]
barrage["leftArm"] += [(16, {"pitch": -60, "yaw": 24}), (18, {"pitch": -24, "yaw": 0, "roll": -32}, OUTQ), (24, {"pitch": -20, "roll": -28})]
barrage["torso"] += [(16, {"pitch": -24, "yaw": -24}), (18, {"pitch": 8, "yaw": 22}, OUTQ), (24, {"pitch": 6, "yaw": 18})]
barrage["head"] += [(16, {"pitch": 10}), (18, {"pitch": -26}, OUTQ), (24, {"pitch": -20})]
barrage["leftLeg"] = [(0, {"pitch": -24, "roll": -6}), (14, {"pitch": -26, "roll": -6}), (16, {"pitch": -34, "roll": -8}),
                      (18, {"pitch": -20, "roll": -6}, OUTQ), (24, {"pitch": -18, "roll": -6})]
barrage["rightLeg"] = [(0, {"pitch": 20, "roll": 6}), (14, {"pitch": 22, "roll": 6}), (16, {"pitch": 30, "roll": 8}),
                       (18, {"pitch": 14, "roll": 6}, OUTQ), (24, {"pitch": 12, "roll": 6})]
art("titans_barrage", "Six straight punches on the beats, then a rising uppercut", 4, 26, 34, barrage)

# Hellhound Gatekeeper's Halberd: draw the halberd far back to the right in both hands, then one great sweep across the
# front looses the three heads down their lanes.
art("three_headed_maw", "Draw the halberd back, then one great sweep that looses the three heads", 10, 20, 28, {
    "rightArm": [(0, {"pitch": -72, "yaw": 30}), (7, {"pitch": -88, "yaw": 72}, OUTQ), (10, {"pitch": -90, "yaw": -55}, OUTQ),
                 (16, {"pitch": -80, "yaw": -60})],
    "leftArm": [(0, {"pitch": -76, "yaw": 44}), (7, {"pitch": -92, "yaw": 82}, OUTQ), (10, {"pitch": -86, "yaw": -40}, OUTQ),
                (16, {"pitch": -76, "yaw": -46})],
    "torso": [(0, {"pitch": -4, "yaw": -20}), (7, {"pitch": -8, "yaw": -50}, OUTQ), (10, {"pitch": -16, "yaw": 42}, OUTQ),
              (16, {"pitch": -12, "yaw": 36})],
    "head": [(0, {"yaw": 14, "pitch": 0}), (7, {"yaw": 34, "pitch": 4}), (10, {"yaw": -26, "pitch": 6}, OUTQ),
             (16, {"yaw": -22, "pitch": 2})],
}, legs(-26, 18, 8))

# Starseer's Staff: the staff rises to the sky while the free hand traces the stars across it (the marks land on the
# strike frame), held while the constellation joins, then driven down as their light falls.
art("constellation", "Raise the staff and trace the stars, then drive it down as their light falls", 10, 32, 40, {
    "rightArm": [(0, {"pitch": -60}), (8, {"pitch": -170, "roll": -8}, OUTQ), (16, {"pitch": -172, "roll": -6}),
                 (22, {"pitch": -180, "roll": 0}), (24, {"pitch": -42, "yaw": -4}, INC), (30, {"pitch": -44, "yaw": -4})],
    "leftArm": [(0, {"pitch": -40, "roll": -15}), (7, {"pitch": -148, "yaw": -45, "roll": 0}, OUTQ),
                (10, {"pitch": -152, "yaw": 40}, OUTQ), (16, {"pitch": -146, "yaw": 56}), (22, {"pitch": -140, "yaw": 50}),
                (24, {"pitch": -24, "yaw": 0, "roll": -40}, INC), (30, {"pitch": -20, "roll": -36})],
    "torso": [(0, {"pitch": 0}), (8, {"pitch": 8}, OUTQ), (16, {"pitch": 9}), (22, {"pitch": 12}), (24, {"pitch": -18}, INC),
              (30, {"pitch": -14})],
    "head": [(0, {"pitch": -6, "yaw": 0}), (8, {"pitch": -38}), (10, {"pitch": -40, "yaw": 12}), (16, {"pitch": -38, "yaw": 4}),
             (24, {"pitch": 10, "yaw": 0}, INC), (30, {"pitch": 6})],
}, legs(-12, 10, 8))

# Saint's Mercy: hands together at the chest, head bowed, then raised and opened to the sky as the sun rises behind.
art("saints_dawn", "Hands together at the chest, then raised and opened as the sun rises", 12, 28, 36, {
    "rightArm": [(0, {"pitch": -70, "yaw": -32}), (9, {"pitch": -76, "yaw": -34}), (12, {"pitch": -152, "yaw": 0, "roll": -42}, BACK),
                 (24, {"pitch": -146, "roll": -44})],
    "leftArm": [(0, {"pitch": -70, "yaw": 32}), (9, {"pitch": -76, "yaw": 34}), (12, {"pitch": -152, "yaw": 0, "roll": 42}, BACK),
                (24, {"pitch": -146, "roll": 44})],
    "torso": [(0, {"pitch": -8}), (9, {"pitch": -12}), (12, {"pitch": 10}, BACK), (24, {"pitch": 8})],
    "head": [(0, {"pitch": 16}), (9, {"pitch": 22}), (12, {"pitch": -34}, BACK), (24, {"pitch": -30})],
}, legs(-8, 8, 10))

# Veilstep: sink low into the dark with the dagger drawn up, then (behind the foe) a reverse-grip stab down into its back.
art("veilstep", "Sink low into the dark, then a reverse-grip stab from behind", 5, 16, 22, {
    "rightArm": [(0, {"pitch": -40, "yaw": -30}), (3, {"pitch": -150, "yaw": 6}, OUTQ), (5, {"pitch": -52, "yaw": -6}, INC),
                 (11, {"pitch": -50, "yaw": -6}), (14, {"pitch": -30, "yaw": 0})],
    "leftArm": [(0, {"pitch": -40, "yaw": 30}), (3, {"pitch": -70, "yaw": 20}), (5, {"pitch": 18, "yaw": 0, "roll": -24}, INC),
                (14, {"pitch": 6, "roll": -12})],
    "torso": [(0, {"pitch": -22}), (3, {"pitch": -10}), (5, {"pitch": -30}, INC), (11, {"pitch": -26}), (14, {"pitch": -10})],
    "head": [(0, {"pitch": 18}), (3, {"pitch": 0}), (5, {"pitch": 22}, INC), (14, {"pitch": 6})],
}, legs(-30, 24, 4))

# Blood Price: the free hand closes on the blade and draws it across the palm (the price), the claymore is coiled far
# back in both hands, then one huge sweep throws the wave of blood.
art("blood_price", "Draw the blade across the palm, coil, then one huge sweep that throws the wave", 10, 20, 28, {
    "rightArm": [(0, {"pitch": -80, "yaw": 10}), (4, {"pitch": -84, "yaw": -22}, OUTQ), (8, {"pitch": -100, "yaw": 76}, OUTQ),
                 (10, {"pitch": -88, "yaw": -62}, OUTQ), (16, {"pitch": -76, "yaw": -66})],
    "leftArm": [(0, {"pitch": -86, "yaw": -32}), (4, {"pitch": -58, "yaw": 12}, OUTQ), (8, {"pitch": -96, "yaw": 62}, OUTQ),
                (10, {"pitch": -84, "yaw": -46}, OUTQ), (16, {"pitch": -72, "yaw": -50})],
    "torso": [(0, {"pitch": -6, "yaw": 0}), (4, {"pitch": -4, "yaw": 6}), (8, {"pitch": -6, "yaw": -46}, OUTQ),
              (10, {"pitch": -14, "yaw": 46}, OUTQ), (16, {"pitch": -10, "yaw": 40})],
    "head": [(0, {"pitch": 14, "yaw": 6}), (4, {"pitch": 8}), (8, {"pitch": 2, "yaw": 28}), (10, {"pitch": 4, "yaw": -22}, OUTQ),
             (16, {"yaw": -18})],
}, legs(-28, 20, 8))

# Sundering Verdict: the blade raised high to call the sword of judgement out of the sky, held while it hangs there,
# then one cut straight down that sends it.
art("sundering_verdict", "Raise the blade to call the sword of judgement, then cut down to send it", 12, 22, 30, {
    "rightArm": [(0, {"pitch": -90}), (7, {"pitch": -176, "roll": -4}, OUTQ), (10, {"pitch": -180, "roll": 0}),
                 (12, {"pitch": -46, "yaw": -6}, INC), (18, {"pitch": -50, "yaw": -6})],
    "leftArm": [(0, {"pitch": -30, "roll": -20}), (7, {"pitch": -44, "roll": -50}), (12, {"pitch": 14, "roll": -30}, INC),
                (18, {"pitch": 8, "roll": -22})],
    "torso": [(0, {"pitch": 0}), (7, {"pitch": 8}, OUTQ), (10, {"pitch": 10}), (12, {"pitch": -22}, INC), (18, {"pitch": -18})],
    "head": [(0, {"pitch": -6}), (7, {"pitch": -40}), (10, {"pitch": -42}), (12, {"pitch": 8}, INC), (18, {"pitch": 4})],
}, legs(-22, 16, 6))

# Ashen Oath: the sword raised point-down before the face, then the wielder drops onto a knee and plants it as the
# pyre rises round them, and stays there, head bowed, while it burns.
art("pyre_of_oaths", "Raise the sword point down, drop to a knee and plant it as the pyre rises", 10, 30, 40, {
    "rightArm": [(0, {"pitch": -96, "yaw": -6}), (7, {"pitch": -150, "yaw": -4}, OUTQ), (10, {"pitch": -48, "yaw": -4}, INC),
                 (26, {"pitch": -50, "yaw": -4})],
    "leftArm": [(0, {"pitch": -96, "yaw": 10}), (7, {"pitch": -146, "yaw": 10}, OUTQ), (10, {"pitch": -52, "yaw": 12}, INC),
                (26, {"pitch": -54, "yaw": 12})],
    "torso": [(0, {"pitch": -2}), (7, {"pitch": 6}), (10, {"pitch": -18}, INC), (26, {"pitch": -16})],
    "head": [(0, {"pitch": 0}), (7, {"pitch": -16}), (10, {"pitch": 24}, INC), (26, {"pitch": 26})],
    "leftLeg": [(0, {"pitch": -14}), (10, {"pitch": -42}, INC), (26, {"pitch": -40})],
    "rightLeg": [(0, {"pitch": 10}), (10, {"pitch": 34}, INC), (26, {"pitch": 32})],
})

# Nightglass Fan: the shards gathered in the hand across the body, then flung out backhand in a fan.
art("nightglass_fan", "Gather the shards across the body, then fling them out in a fan", 6, 14, 20, {
    "rightArm": [(0, {"pitch": -60, "yaw": -48}), (4, {"pitch": -76, "yaw": -82}, OUTQ), (6, {"pitch": -92, "yaw": 56}, OUTQ),
                 (12, {"pitch": -86, "yaw": 60})],
    "leftArm": [(0, {"pitch": -46, "roll": -12}), (4, {"pitch": -60, "yaw": -20, "roll": 0}), (6, {"pitch": -18, "yaw": 0, "roll": -38}, OUTQ),
                (12, {"pitch": -12, "roll": -30})],
    "torso": [(0, {"pitch": -6, "yaw": 16}), (4, {"pitch": -10, "yaw": 30}, OUTQ), (6, {"pitch": -12, "yaw": -26}, OUTQ),
              (12, {"pitch": -8, "yaw": -20})],
    "head": [(0, {"yaw": -8}), (4, {"yaw": -14}), (6, {"yaw": 8}, OUTQ), (12, {"yaw": 6})],
}, legs(-20, 16, 6))

# Requiem of Thorns: the scythe lifted high behind, then one low reaping turn round the front that wakes the thorns.
art("requiem_of_thorns", "Lift the scythe high, then one low reaping turn that wakes the thorns", 12, 24, 34, {
    "body": [(0, {"yaw": 0}), (8, {"yaw": -40}, OUTQ), (12, {"yaw": 70}, OUTQ), (20, {"yaw": 60})],
    "rightArm": [(0, {"pitch": -110, "yaw": 20}), (8, {"pitch": -165, "yaw": 50}, OUTQ), (12, {"pitch": -62, "yaw": -70}, OUTQ),
                 (20, {"pitch": -58, "yaw": -66})],
    "leftArm": [(0, {"pitch": -100, "yaw": 34}), (8, {"pitch": -150, "yaw": 60}, OUTQ), (12, {"pitch": -66, "yaw": -50}, OUTQ),
                (20, {"pitch": -60, "yaw": -48})],
    "torso": [(0, {"pitch": 0}), (8, {"pitch": 8}), (12, {"pitch": -26}, OUTQ), (20, {"pitch": -22})],
    "head": [(0, {"pitch": -6, "yaw": 0}), (8, {"pitch": -14, "yaw": 20}), (12, {"pitch": 14, "yaw": -16}, OUTQ),
             (20, {"pitch": 10, "yaw": -12})],
}, legs(-30, 22, 10))


def check():
    """Every right-click art has a move whose strike frame is its impact tick; the drawn ones have none."""
    src = open(SIGNATURES).read()
    arts = re.findall(r"^\s*([A-Z_]+)\(ItemGrade\.\w+, Trigger\.(USE|DRAW), \d+, 0x[0-9A-Fa-f]+, (\d+)\)", src, re.M)
    assert arts, "no signatures found in " + SIGNATURES
    for name, trigger, ticks in arts:
        key = name.lower()
        if trigger == "DRAW":
            assert key not in A, key + " is drawn: the bow's own draw is its move"
            continue
        assert key in A, "no move for " + key
        assert STRIKE[key] == int(ticks), "%s strikes on %d, the art lands on %s" % (key, STRIKE[key], ticks)
    extra = set(A) - {n.lower() for n, _, _ in arts}
    assert not extra, "moves for arts that don't exist: %s" % extra


def main():
    check()
    os.makedirs(OUT, exist_ok=True)
    for name, data in A.items():
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(data, f, indent=1)
    print("wrote", len(A), "art animations to", OUT)


if __name__ == "__main__":
    main()
