#!/usr/bin/env python3
"""
Idle stances for the two-handed weapon kinds: Better Combat poses (playerAnimator "emote" files) written to
assets/beyondsolo/player_animations/pose_<stance>.json and named by the weapons' attribute files.

Better Combat's own idle poses are made for its own item transforms, and with ours they held a claymore straight
up in front of the face, spears and hammers sideways across the hips and katanas and scythes pointing backwards.
Each stance here is described by what it should look like instead: where the weapon points, which way its edge
faces, where the main hand holds it and where along the weapon the other hand goes. The script then works out the
arm angles that put both fists on the grip (straight Minecraft arms, so a shoulder may slide a pixel or two) and
the item bone rotation and offset that line the weapon up with them through our held-item transforms.

Only idle and walking use these poses: every Better Combat attack animation sets the arms and the item bone
itself, so attacks look exactly as before.

Body model space, in pixels: x to the player's left, y down, z backwards (the player faces -z). The item is the
64x64 sprite drawn from bottom-left (pommel or butt) to top-right (tip or head), its upper-left side being the edge
of axes, scythes, katanas and glaives. Positions along the weapon are "t", 0 at the sprite's bottom-left corner and
64 at its top-right corner, measured along the diagonal (one t is one pixel across and one up).

Run from the repo root (tools/gen_weapon_models.py calls it):  python3 tools/weapon_stances.py
Needs: numpy.
"""
import json
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_weapon_models as gm  # noqa: E402

ANIM_DIR = os.path.join(gm.ASSETS, "player_animations")
NS = "beyondsolo"

RIGHT_SHOULDER = np.array([-5.0, 2.0, 0.0])
LEFT_SHOULDER = np.array([5.0, 2.0, 0.0])
# where a fist closes, in the arm's own space (the arm hangs down +y from its pivot; the fist is its last 4 px)
RIGHT_FIST = np.array([-1.0, 8.0, 0.0])
LEFT_FIST = np.array([1.0, 8.0, 0.0])
ARM_REACH = float(np.linalg.norm(RIGHT_FIST))


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def part_rot(pitch, yaw, roll):
    """A model part's rotation (ModelPart.translateAndRotate and playerAnimator's item bones: Z, then Y, then X)."""
    return Rz(roll) @ Ry(yaw) @ Rx(pitch)


def euler_zyx(R):
    """(pitch, yaw, roll) with part_rot(pitch, yaw, roll) == R."""
    yaw = math.asin(max(-1.0, min(1.0, -R[2, 0])))
    pitch = math.atan2(R[2, 1], R[2, 2])
    roll = math.atan2(R[1, 0], R[0, 0])
    return pitch, yaw, roll


def body(right=0.0, up=0.0, forward=0.0):
    """A direction given as right/up/forward, in body model space."""
    v = np.array([-right, -up, -forward], float)
    return v / np.linalg.norm(v)


# vanilla's ItemInHandLayer, after the arm: turn the item upright and move it to the front of the fist (pixels)
VAN_R = Rx(math.radians(-90)) @ Ry(math.radians(180))
VAN_T = np.array([1.0, 2.0, -10.0])


def display_affine(tr):
    """Item model pixels (0..16) -> pixels after the display transform (ItemTransform.apply, then -0.5)."""
    R = gm._rot(*tr["rotation"])
    s = tr["scale"][0]
    t = np.array(tr["translation"], float)
    return R * s, t - R @ (np.full(3, 8.0) * s)


def sprite_point(t):
    """The point at position t along the sprite's diagonal, in item model pixels."""
    return np.array([t / 4.0, t / 4.0, 8.0])


L_ITEM = np.array([1.0, 1.0, 0.0]) / math.sqrt(2)     # pommel -> tip
E_ITEM = np.array([-1.0, 1.0, 0.0]) / math.sqrt(2)    # the sprite's upper-left side
N_ITEM = np.array([0.0, 0.0, 1.0])


def swing(u):
    """The rotation that swings a hanging arm (+y) to point along `u` without twisting it about its own length."""
    a = np.array([0.0, 1.0, 0.0])
    u = u / np.linalg.norm(u)
    k = np.cross(a, u)
    sin, cos = np.linalg.norm(k), float(np.dot(a, u))
    if sin < 1e-9:
        return np.eye(3) if cos > 0 else Rx(math.pi)
    k = k / sin
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + sin * K + (1 - cos) * (K @ K)


def solve_arm(shoulder, fist, target):
    """Arm angles that point the fist from the shoulder at `target` with no twist, and the pivot that then puts the
    fist exactly there (the shoulder slides along the arm when the target is nearer or further than the arm)."""
    u = target - shoulder
    for _ in range(8):                      # the fist sits off the arm's pivot axis: aim the arm until it lines up
        R = swing(u)
        got = R @ fist
        want = target - shoulder
        # turn u by the angle between where the fist points and where it should
        corr = swing_between(got, want)
        u = corr @ u
    R = swing(u)
    pitch, yaw, roll = euler_zyx(R)
    pivot = target - R @ fist
    return {"pitch": pitch, "yaw": yaw, "roll": roll, "pivot": pivot, "shift": float(np.linalg.norm(pivot - shoulder))}


def swing_between(a, b):
    """Shortest rotation taking direction a to direction b."""
    a = a / np.linalg.norm(a)
    b = b / np.linalg.norm(b)
    k = np.cross(a, b)
    sin, cos = np.linalg.norm(k), float(np.dot(a, b))
    if sin < 1e-12:
        return np.eye(3)
    k = k / sin
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + sin * K + (1 - cos) * (K @ K)


TORSO = (np.array([-4.0, 0.0, -2.0]), np.array([4.0, 12.0, 2.0]))


def clipping(pivot, fist, half=1.5):
    """How deep an arm from `pivot` to `fist` (about 4 px thick) sinks into the torso, summed along its length."""
    lo, hi = TORSO[0] - half, TORSO[1] + half
    depth = 0.0
    for u in np.linspace(0.25, 1.0, 7):
        q = pivot + u * (fist - pivot)
        if np.all(q > lo) and np.all(q < hi):
            depth += float(np.min(np.concatenate([q - lo, hi - q])))
    return depth


class Stance:
    """
    kinds: weapon kinds (Better Combat parents) that use it; the first one's held transform is used for the maths
    tip/edge: where the weapon points and where its edge (the sprite's upper-left side) faces, as body() directions
    grip: t along the weapon where the main (right) hand holds it; fist: where that fist goes, in body pixels
    other: t where the left hand holds it, or None to leave the left arm free
    """

    def __init__(self, name, kinds, tip, edge, grip, fist, other=None):
        self.name, self.kinds = name, kinds
        L = tip / np.linalg.norm(tip)
        E = edge - np.dot(edge, L) * L
        self.L, self.E = L, E / np.linalg.norm(E)
        self.grip, self.fist, self.other = grip, np.array(fist, float), other

    def arms(self, fist, other):
        right = solve_arm(RIGHT_SHOULDER, RIGHT_FIST, fist)
        left = target = None
        if other is not None:
            s = gm.displays(self.kinds[0])["thirdperson_righthand"]["scale"][0]
            d = (other - self.grip) * math.sqrt(2) / 4.0 * s      # pixels along the weapon
            target = fist + d * self.L
            left = solve_arm(LEFT_SHOULDER, LEFT_FIST, target)
        return right, left, target

    def cost(self, fist, other):
        right, left, target = self.arms(fist, other)
        c = right["shift"] ** 2 + 0.25 * float(np.sum((fist - self.fist) ** 2))
        c += 3 * clipping(right["pivot"], fist)
        if left is not None:
            c += left["shift"] ** 2 + 0.15 * (other - self.other) ** 2 + 3 * clipping(left["pivot"], target)
            gap = float(np.linalg.norm(target - fist))
            c += 4 * max(0.0, 3.0 - gap) ** 2                         # two fists, not one
        return c

    def fit(self):
        """Nudges the main fist (up to 3 px) and the other hand's place on the grip (up to 6 t) so the arms reach
        with the least shoulder sliding and without pushing through the chest."""
        if getattr(self, "_fit", None) is not None:
            return self._fit
        best = (self.cost(self.fist, self.other), self.fist, self.other)
        offs = np.arange(-3.0, 3.01, 1.0)
        others = [None] if self.other is None else list(np.arange(self.other - 6, self.other + 6.01, 1.0))
        for dx in offs:
            for dy in offs:
                for dz in offs:
                    f = self.fist + np.array([dx, dy, dz])
                    for o in others:
                        c = self.cost(f, o)
                        if c < best[0]:
                            best = (c, f, o)
        self._fit = best
        return best

    def solve(self):
        tr = gm.displays(self.kinds[0])["thirdperson_righthand"]
        A, b = display_affine(tr)
        _, fist, other = self.fit()
        right, left, target = self.arms(fist, other)
        Ra = part_rot(right["pitch"], right["yaw"], right["roll"])
        # weapon orientation in body space -> item bone rotation
        Fw = np.column_stack([self.L, self.E, np.cross(self.L, self.E)])
        Fi = np.column_stack([L_ITEM, E_ITEM, N_ITEM])
        Ad = A / tr["scale"][0]                                   # display rotation only
        Rb = (Ra @ VAN_R).T @ Fw @ (Ad @ Fi).T
        # item bone offset: the grip point lands on the fist
        p_disp = A @ sprite_point(self.grip) + b                  # grip after the display transform
        # body = pivot + Ra (VAN_R (VAN_T + bone_t + Rb p_disp)) -> solve for bone_t
        local = VAN_R.T @ (Ra.T @ (fist - right["pivot"]))
        bone_t = local - VAN_T - Rb @ p_disp
        out = {"right": right, "bone": (euler_zyx(Rb), bone_t), "fist": fist, "other": other}
        if left is not None:
            out["left"], out["left_target"] = left, target
        return out

    def emote(self):
        sol = self.solve()
        r, (bone_rot, bone_t) = sol["right"], sol["bone"]
        parts = {
            "rightArm": {"x": r["pivot"][0], "y": r["pivot"][1], "z": r["pivot"][2],
                         "pitch": r["pitch"], "yaw": r["yaw"], "roll": r["roll"]},
            "rightItem": {"x": bone_t[0], "y": bone_t[1], "z": bone_t[2],
                          "pitch": bone_rot[0], "yaw": bone_rot[1], "roll": bone_rot[2]},
        }
        if "left" in sol:
            left = sol["left"]
            parts["leftArm"] = {"x": left["pivot"][0], "y": left["pivot"][1], "z": left["pivot"][2],
                                "pitch": left["pitch"], "yaw": left["yaw"], "roll": left["roll"]}
        moves = []
        for tick in (1, 10):
            for part, axes in parts.items():
                for axis, v in axes.items():
                    moves.append({"tick": tick, "easing": "EASEINOUTQUAD", "turn": 0, part: {axis: round(float(v), 5)}})
        return {
            "name": "pose_" + self.name,
            "author": "Beyond Solo",
            "description": "Idle stance for " + ", ".join(self.kinds) + " (tools/weapon_stances.py)",
            "emote": {"isLoop": "true", "returnTick": 1, "beginTick": 0, "endTick": 1, "stopTick": 11,
                      "degrees": False, "moves": moves},
        }

    def report(self):
        sol = self.solve()
        r = sol["right"]
        deg = lambda a: f"{math.degrees(a['pitch']):6.1f} {math.degrees(a['yaw']):6.1f} {math.degrees(a['roll']):6.1f}"  # noqa
        f = sol["fist"]
        msg = f"{self.name:9s} fist ({f[0]:5.1f} {f[1]:5.1f} {f[2]:5.1f}) R shift {r['shift']:.2f} [{deg(r)}]"
        if "left" in sol:
            left, t = sol["left"], sol["left_target"]
            msg += f" | other t {sol['other']:.1f} at ({t[0]:5.1f} {t[1]:5.1f} {t[2]:5.1f}) L shift {left['shift']:.2f} [{deg(left)}]"
        return msg


# The stances. Directions: body(right, up, forward).
STANCES = [
    # two-handed sword in a middle guard: both fists in front of the belly, blade forward and up a little to the
    # right (so it shows past the body from the usual camera behind the player), edge down
    Stance("claymore", ["claymore"], tip=body(0.28, 0.55, 1.0), edge=body(0, -1, 0), grip=16, fist=(-1.5, 8.0, -7.0),
           other=9),
    # katana in chudan: the same guard with the longer grip and the blade a little lower
    Stance("katana", ["katana"], tip=body(0.25, 0.45, 1.0), edge=body(0, -1, 0), grip=19, fist=(-1.5, 8.5, -6.5),
           other=11),
    # spears, glaives and halberds: rear fist at the right hip, the other hand forward on the shaft, point forward
    # and up towards the enemy's face, blade edge down
    Stance("polearm", ["spear", "glaive", "halberd"], tip=body(-0.20, 0.45, 0.87), edge=body(0, -1, 0), grip=12,
           fist=(-5.0, 11.0, 0.0), other=22),
    # war hammers and great axes at port arms: haft across the body from the right hip, head up by the left shoulder
    # and well forward, striking face or edge to the front
    Stance("heavy", ["hammer", "heavy_axe"], tip=body(-0.55, 0.60, 0.58), edge=body(0, 0, 1), grip=12,
           fist=(-4.0, 10.5, -4.0), other=24),
    # scythes like a reaper: shaft upright at the right side, blade over the head pointing forward, one hand
    Stance("scythe", ["scythe"], tip=body(0.10, 0.95, 0.25), edge=body(0, 0, 1), grip=18, fist=(-6.0, 10.0, -4.0)),
]


def euler_xyz(R):
    """Degrees (x, y, z) with gm._rot(x, y, z) == R (item display rotations are applied X, then Y, then Z)."""
    y = math.asin(max(-1.0, min(1.0, R[0, 2])))
    x = math.atan2(-R[1, 2], R[2, 2])
    z = math.atan2(-R[0, 1], R[0, 0])
    return [round(math.degrees(v), 2) for v in (x, y, z)]


def held_transform(tip, edge, grip, scale, arm_pitch=-18.0, offset=(0.0, 0.0, 0.0)):
    """A third-person display transform (no animation involved) that holds the item in a plain hanging hand (the
    vanilla arm pose for a held item tilts the arm by `arm_pitch`) pointing `tip` with `edge` facing as given,
    `grip` (item model pixels) in the middle of the fist, moved by `offset` (arm pixels, the arm's own axes)."""
    L = tip / np.linalg.norm(tip)
    E = edge - np.dot(edge, L) * L
    E = E / np.linalg.norm(E)
    Ra = Rx(math.radians(arm_pitch))
    Fw = np.column_stack([L, E, np.cross(L, E)])
    Fi = np.column_stack([L_ITEM, E_ITEM, N_ITEM])
    Rd = (Ra @ VAN_R).T @ Fw @ Fi.T
    rot = euler_xyz(Rd)
    Rd = gm._rot(*rot)
    want = VAN_R.T @ (RIGHT_FIST + np.array(offset, float)) - VAN_T   # where the grip ends up after the display
    t = want - Rd @ ((np.array(grip, float) - 8.0) * scale)
    return {"rotation": rot, "translation": [round(float(v), 3) for v in t], "scale": [round(scale, 4)] * 3}


def pose_for(kind):
    for s in STANCES:
        if kind in s.kinds:
            return f"{NS}:pose_{s.name}"
    return None


def write_all():
    os.makedirs(ANIM_DIR, exist_ok=True)
    for s in STANCES:
        with open(os.path.join(ANIM_DIR, f"pose_{s.name}.json"), "w") as f:
            json.dump(s.emote(), f, indent=2)
            f.write("\n")
    return len(STANCES)


if __name__ == "__main__":
    for s in STANCES:
        print(s.report())
    print(f"stances written: {write_all()}")
