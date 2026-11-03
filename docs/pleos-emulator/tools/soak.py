#!/usr/bin/env python3
"""Summarize probe logcat output from a background soak run."""
import re
import statistics
import sys

speed, intervals, ticks, stamps = [], [], [], []
other = {"gear": 0, "steering": 0, "brake": 0, "prop": 0}
for line in open(sys.argv[1], errors="replace"):
    stamp = re.match(r"\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d+)", line)
    if stamp:
        h, m, s, ms = map(int, stamp.groups())
        stamps.append(h * 3600 + m * 60 + s + ms / 1000)
    found = re.search(r"speed ([\d.E-]+) status=\d+ dtMs=(\d+)", line)
    if found:
        speed.append(float(found.group(1)))
        intervals.append(int(found.group(2)))
    found = re.search(r"fg tick (\d+)", line)
    if found:
        ticks.append(int(found.group(1)))
    for key, marker in (("gear", "sdk gear"), ("steering", "sdk steering"), ("brake", "sdk brake"), ("prop", "prop GEAR")):
        if marker in line:
            other[key] += 1

span = stamps[-1] - stamps[0] if len(stamps) > 1 else 0
print(f"log span: {span:.0f}s  lines: {len(stamps)}")
if ticks:
    gaps = [b - a for a, b in zip(ticks, ticks[1:])]
    print(f"fg ticks: {ticks[0]}..{ticks[-1]}  entries={len(ticks)}  non-5 step gaps={sum(1 for g in gaps if g != 5)}")
if intervals:
    body = intervals[1:]
    q = statistics.quantiles(body, n=100)
    print(f"speed samples: {len(speed)}  rate={len(speed) / span:.1f}/s  interval median={statistics.median(body)}ms p95={q[94]:.0f}ms max={max(body)}ms")
    kph = [v * 3.6 for v in speed]
    print(f"speed range: {min(kph):.1f}..{max(kph):.1f} km/h  distinct values={len(set(round(v, 3) for v in speed))}")
    steps = sorted({round(abs(b - a) * 3.6, 2) for a, b in zip(speed, speed[1:]) if abs(b - a) > 1e-4})
    print(f"smallest nonzero steps (km/h): {steps[:8]}")
    print(f"longest run of repeated value: {max(sum(1 for _ in g) for g in __import__('itertools').groupby(speed) for g in [g[1]])} samples")
print("other events:", other)
