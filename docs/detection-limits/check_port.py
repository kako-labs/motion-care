#!/usr/bin/env python3
"""Compares the Python port with what the app stored, then runs EPA driving schedules through it.

Usage: check_port.py <trips.db from recording_e2e.py> <folder of EPA schedule .txt files>
Schedules come from https://www.epa.gov/vehicle-and-fuel-emissions-testing/dynamometer-drive-schedules
"""
import glob
import os
import re
import sqlite3
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from detector import Detector, LateralDetector

LATERAL = {"FAST_TURN", "REPEATED_SWAY"}
KINDS = ("RAPID_START", "HARD_STOP", "REPEATED_SPEED_CHANGE")


def compare_with_app(db_path):
    db = sqlite3.connect(db_path)
    samples = db.execute("SELECT elapsed, speed FROM speed_samples ORDER BY elapsed").fetchall()
    observed = sorted(db.execute("SELECT pattern, start_ms, end_ms, peak_mps2 FROM observations").fetchall())
    stored = [row for row in observed if row[0] not in LATERAL]
    detector = Detector()
    found = [x for elapsed, speed in samples for x in detector.add(elapsed, speed)] + detector.finish()
    found.sort()
    same = len(found) == len(stored) and all(
        a[:3] == b[:3] and abs(a[3] - b[3]) < 1e-9 for a, b in zip(found, stored)
    )
    print(f"port vs app: stored={len(stored)} port={len(found)} identical={same}")
    return same and compare_lateral(db, samples, observed)


def compare_lateral(db, samples, observed):
    """Steering is timed on arrival, so the order against speed events can differ by a sample."""
    steering = db.execute("SELECT elapsed, degrees FROM steering_samples ORDER BY elapsed").fetchall()
    if not steering:
        print("lateral: no steering samples")
        return True
    detector = LateralDetector()
    found, index, degrees = [], 0, None
    for elapsed, speed in samples:
        while index < len(steering) and steering[index][0] <= elapsed:
            degrees = steering[index][1]
            index += 1
        if speed is not None and degrees is not None:
            found += detector.add(elapsed, speed, degrees)
    found += detector.finish()
    stored = sorted(row for row in observed if row[0] in LATERAL)
    found.sort()
    same = [x[0] for x in found] == [x[0] for x in stored] and all(
        abs(a[1] - b[1]) <= 400 and abs(a[3] - b[3]) < 0.3 for a, b in zip(found, stored)
    )
    print(f"lateral port vs app: stored={len(stored)} port={len(found)} matching={same}")
    return same


def read_schedule(path):
    rows = []
    with open(path, errors="ignore") as f:
        for line in f:
            match = re.match(r"\s*(\d+(?:\.\d+)?)\s+(\d+(?:\.\d+)?)\s*$", line)
            if match:
                rows.append((float(match.group(1)) * 1000, float(match.group(2)) * 0.44704))
    return rows


def run_schedules(folder):
    print("schedule  minutes  max_accel  max_decel  rapid_start  hard_stop  repeated  per_10_min")
    for path in sorted(glob.glob(os.path.join(folder, "*.txt"))):
        rows = read_schedule(path)
        if len(rows) < 10:
            continue
        detector = Detector()
        found = [x for t, v in rows for x in detector.add(t, v)] + detector.finish()
        slopes = [(b[1] - a[1]) / ((b[0] - a[0]) / 1000) for a, b in zip(rows, rows[1:]) if b[0] > a[0]]
        minutes = (rows[-1][0] - rows[0][0]) / 60000
        counts = [sum(1 for x in found if x[0] == kind) for kind in KINDS]
        name = os.path.basename(path)[:-4]
        print(
            f"{name:9} {minutes:7.1f} {max(slopes):10.2f} {-min(slopes):10.2f} "
            f"{counts[0]:12d} {counts[1]:10d} {counts[2]:9d} {sum(counts) / minutes * 10:11.1f}"
        )


if __name__ == "__main__":
    identical = compare_with_app(sys.argv[1])
    run_schedules(sys.argv[2])
    sys.exit(0 if identical else 1)
