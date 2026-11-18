# Usage: drawcalls.py <GLCOUNT_FILE> <client log>
# For every "UITEST perf <label>: <n> frames, frame avg <ms> ms" line in the client log, prints the OpenGL draw calls
# a frame during that perf run, from the counts glcount.c wrote. See tools/uitest/README.md.
import bisect
import datetime
import re
import sys

counts = [tuple(map(int, line.split())) for line in open(sys.argv[1]) if line.strip()]
times = [c[0] for c in counts]


def draws_at(t):
    i = bisect.bisect_left(times, t)
    if i <= 0:
        return counts[0][1]
    if i >= len(times):
        return counts[-1][1]
    (t0, c0), (t1, c1) = counts[i - 1], counts[i]
    return c0 + (c1 - c0) * (t - t0) / max(1, t1 - t0)


perf = re.compile(r'\[(\d+\w{3}\d{4} [\d:.]+)\].*UITEST perf (\S+): (\d+) frames, frame avg ([\d.]+) ms')
for line in open(sys.argv[2], errors='replace'):
    m = perf.search(line)
    if not m:
        continue
    end = datetime.datetime.strptime(m.group(1), '%d%b%Y %H:%M:%S.%f').timestamp() * 1000
    frames, avg = int(m.group(3)), float(m.group(4))
    start = end - frames * avg
    if start < times[0] or end > times[-1]:
        continue
    print(f'{m.group(2):22s} {(draws_at(end) - draws_at(start)) / frames:7.1f} draw calls a frame')
