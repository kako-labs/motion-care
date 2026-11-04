#!/usr/bin/env python3
"""Estimates lateral acceleration on the public VED data from GPS heading and speed, as v times yaw rate.

Usage: measure_lateral.py <folder of VED_*_week.csv> <output.json>

VED has no steering or lateral acceleration, so this is an estimate. GPS fixes arrive about every 3 s,
which smooths peaks and cannot show swings faster than about 0.17 Hz. GPS noise can add false motion.
"""
import csv
import glob
import json
import math
import os
import sys
from collections import defaultdict
from multiprocessing import Pool

SPLIT_GAP_MS = 7000  # GPS fixes arrive about every 3 s.
MIN_SPEED = 5.0  # m/s; heading from GPS is unreliable when slow.
MIN_MOVE_M = 3.0
THRESHOLDS = [0.5, 0.75, 0.9, 1.0, 1.23, 1.47, 1.89, 2.0, 2.5, 3.0, 3.5, 4.0]
VARIANTS = [(0.9, 30000, 3), (0.9, 30000, 4), (1.2, 30000, 3), (1.5, 30000, 3), (2.0, 30000, 3)]


def pct(values, p):
    if not values:
        return None
    k = (len(values) - 1) * p / 100
    low = int(k)
    high = min(low + 1, len(values) - 1)
    return values[low] + (values[high] - values[low]) * (k - low)


def read_trips(path):
    trips = defaultdict(list)
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            speed, lat, lon = row["Vehicle Speed[km/h]"], row["Latitude[deg]"], row["Longitude[deg]"]
            if speed in ("", "NaN") or lat in ("", "NaN") or lon in ("", "NaN"):
                continue
            key = (int(float(row["VehId"])), int(float(row["Trip"])))
            trips[key].append((float(row["Timestamp(ms)"]), float(speed) / 3.6, float(lat), float(lon)))
    for rows in trips.values():
        rows.sort()
    return trips


def lateral_series(rows):
    """Yields (t, a_y) from fixes at least MIN_MOVE_M apart, split at gaps."""
    last_fix, last_heading, last_t = None, None, None
    series = []
    for t, v, lat, lon in rows:
        if last_fix is None:
            last_fix = (t, lat, lon)
            continue
        dy = (lat - last_fix[1]) * 111320.0
        dx = (lon - last_fix[2]) * 111320.0 * math.cos(math.radians(lat))
        moved = math.hypot(dx, dy)
        if moved < MIN_MOVE_M:
            continue
        heading = math.atan2(dy, dx)
        if last_heading is None or t - last_t > SPLIT_GAP_MS:
            series.append(None)
        elif t > last_t and v >= MIN_SPEED:
            change = (heading - last_heading + math.pi) % (2 * math.pi) - math.pi
            series.append((t, v * change / ((t - last_t) / 1000)))
        last_heading, last_t = heading, t
        last_fix = (t, lat, lon)
    return series


def count_swings(series, limit, window_ms, needed):
    count, direction, times = 0, 0, []
    for item in series:
        if item is None:
            direction, times = 0, []
            continue
        t, a = item
        now = 1 if a >= limit else -1 if a <= -limit else direction
        if now != direction:
            if direction != 0:
                times.append(t)
            direction = now
        times = [x for x in times if t - x <= window_ms]
        if len(times) >= needed:
            count += 1
            times = []
    return count


def turn_peaks(series):
    """Peak |a_y| of each continuous turn that reaches at least 0.5 m/s2."""
    peaks, current = [], 0.0
    for item in series:
        value = abs(item[1]) if item else 0.0
        if value >= 0.5:
            current = max(current, value)
        elif current:
            peaks.append(current)
            current = 0.0
    if current:
        peaks.append(current)
    return peaks


def measure(path):
    minutes, peaks, values = defaultdict(float), [], []
    swings = {v: defaultdict(int) for v in VARIANTS}
    for key, rows in read_trips(path).items():
        if len(rows) <= 30:
            continue
        series = lateral_series(rows)
        valid = [s for s in series if s]
        if len(valid) < 20:
            continue
        minutes[key[0]] += (rows[-1][0] - rows[0][0]) / 60000
        values += [round(abs(a), 2) for _, a in valid if abs(a) <= 8]
        peaks += [p for p in turn_peaks(series) if p <= 8]
        for v in VARIANTS:
            swings[v][key[0]] += count_swings(series, v[0], v[1], v[2])
    return dict(minutes), peaks, values, {v: dict(d) for v, d in swings.items()}


if __name__ == "__main__":
    files = sorted(glob.glob(os.path.join(sys.argv[1], "*.csv")))
    with Pool(8) as pool:
        parts = pool.map(measure, files, chunksize=1)
    minutes, peaks, values = defaultdict(float), [], []
    swings = {v: defaultdict(int) for v in VARIANTS}
    for m, p, a, s in parts:
        for k, x in m.items():
            minutes[k] += x
        peaks += p
        values += a
        for v in VARIANTS:
            for k, x in s[v].items():
                swings[v][k] += x
    peaks.sort()
    values.sort()
    drivers = [k for k, m in minutes.items() if m >= 60]
    total = sum(minutes.values())
    result = {
        "hours": round(total / 60, 1),
        "vehicles": len(minutes),
        "turns_per_10_min": round(10 * len(peaks) / total, 2),
        "moving_samples": {
            "n": len(values),
            "percentiles": {p: pct(values, p) for p in (50, 90, 95, 99)},
        },
        "turn_peaks": {
            "n": len(peaks),
            "percentiles": {p: round(pct(peaks, p), 2) for p in (10, 25, 50, 75, 90, 95, 99)},
            "share_at_or_above": {t: round(100 * sum(1 for x in peaks if x >= t) / len(peaks), 1) for t in THRESHOLDS},
        },
        "repeated_per_10_min": {
            f"{v[0]}_{int(v[1] / 1000)}s_{v[2]}": {
                "median": round(pct(sorted(10 * swings[v].get(d, 0) / minutes[d] for d in drivers), 50), 2),
                "p90": round(pct(sorted(10 * swings[v].get(d, 0) / minutes[d] for d in drivers), 90), 2),
            }
            for v in VARIANTS
        },
    }
    with open(sys.argv[2], "w") as f:
        json.dump(result, f, indent=1)
    print(json.dumps(result)[:1600])
