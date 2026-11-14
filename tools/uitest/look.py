#!/usr/bin/env python3
"""usage: look.py px py pz tx ty tz

Prints the command that stands the test player at p (feet position), looking at t.
Example: tools/uitest/rcon.py "$(tools/uitest/look.py 10 64 10 14 65 10)"
"""
import math
import sys

if len(sys.argv) != 7:
    sys.exit(__doc__)
px, py, pz, tx, ty, tz = map(float, sys.argv[1:])
dx, dy, dz = tx - px, ty - (py + 1.62), tz - pz  # 1.62 is the eye height
yaw = math.degrees(math.atan2(-dx, dz))
pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
print(f"tp Dev {px} {py} {pz} {yaw:.1f} {pitch:.1f}")
