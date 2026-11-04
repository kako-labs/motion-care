#!/usr/bin/env python3
"""Measures the detector's limits on real driving from the public Vehicle Energy Dataset (VED).

Usage: measure_ved.py <folder of VED_*_week.csv> <output.json>
Data: https://github.com/gsoh/VED (extract VED_DynamicData_Part1.7z and Part2.7z into one folder)

VED rows repeat the last polled speed, so a jump after a hold looks instantaneous. Each trip's poll
period is estimated from speed changes during ramps, and speed is rebuilt as straight lines between
polls before being resampled at 1 Hz. Only trips polled about once per second are used.

Starts are still affected because the first report after zero can be far above walking speed, which
no car reaches in one poll. So starts are measured twice: with the app's logic as an upper estimate, and
only for starts first reported at 10 km/h or less as a lower estimate.
"""
import csv
import glob
import json
import os
import statistics
import sys
from collections import defaultdict
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from detector import Detector, Limits

SPLIT_GAP_MS = 3000
MAX_POLL_MS = 1250
PLAUSIBLE_MPS2 = 6.0
MIN_DRIVER_MINUTES = 60
SHARE_THRESHOLDS = [1.23, 1.47, 1.67, 1.89, 2.0, 2.22, 2.5, 2.78, 3.0, 3.43, 3.89, 4.41]
RATE_THRESHOLDS = [1.67, 1.89, 2.0, 2.22, 2.5, 2.78, 3.43, 3.89]
PERCENTILES = [10, 25, 50, 75, 90, 95, 99]
REPEATED_VARIANTS = [(0.8, 30000, 4), (0.9, 30000, 4), (1.0, 30000, 4), (0.9, 20000, 4), (0.9, 30000, 3), (0.9, 20000, 3)]
OFF = dict(rapid_start=0.0, hard_stop=0.0, swing=99.0)
QUIET = dict(rapid_start=99.0, hard_stop=99.0)


def percentile(sorted_values, p):
    if not sorted_values:
        return None
    k = (len(sorted_values) - 1) * p / 100
    low = int(k)
    high = min(low + 1, len(sorted_values) - 1)
    return sorted_values[low] + (sorted_values[high] - sorted_values[low]) * (k - low)


def read_trips(path):
    trips = defaultdict(list)
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            speed = row["Vehicle Speed[km/h]"]
            if speed in ("", "NaN"):
                continue
            key = (int(float(row["VehId"])), int(float(row["Trip"])))
            trips[key].append((float(row["Timestamp(ms)"]), float(speed) / 3.6))
    for key, rows in trips.items():
        rows.sort()
        trips[key] = [row for i, row in enumerate(rows) if i == 0 or row[0] != rows[i - 1][0]]
    return trips


def poll_period(rows):
    knots = [rows[0]]
    for row in rows[1:]:
        if row[1] != knots[-1][1]:
            knots.append(row)
    gaps = []
    for a, b, c in zip(knots, knots[1:], knots[2:]):
        if (b[1] - a[1]) * (c[1] - b[1]) > 0 and c[0] - b[0] <= SPLIT_GAP_MS:
            gaps.append(c[0] - b[0])
    return statistics.median(gaps) if len(gaps) >= 20 else None


def resample(rows, period):
    """Yields 1 Hz samples for each unbroken segment of a trip."""
    segments, current = [], [rows[0]]
    for row in rows[1:]:
        if row[0] - current[-1][0] > SPLIT_GAP_MS:
            segments.append(current)
            current = []
        current.append(row)
    segments.append(current)
    for segment in segments:
        points = [segment[0]]
        for row in segment[1:]:
            if row[1] != points[-1][1]:
                hold_end = row[0] - period
                if hold_end > points[-1][0]:
                    points.append((hold_end, points[-1][1]))
                points.append(row)
        if segment[-1][0] > points[-1][0]:
            points.append(segment[-1])
        if len(points) < 2:
            continue
        samples, j, t = [], 0, points[0][0]
        while t <= points[-1][0]:
            while points[j + 1][0] < t:
                j += 1
            (t0, v0), (t1, v1) = points[j], points[j + 1]
            samples.append((t, v0 + (v1 - v0) * (t - t0) / (t1 - t0)))
            t += 1000
        yield samples


def speed_at(rows, start, t):
    for i in range(start, len(rows) - 1):
        (t0, v0), (t1, v1) = rows[i], rows[i + 1]
        if t0 <= t <= t1:
            if t1 - t0 > SPLIT_GAP_MS:
                return None
            return v0 + (v1 - v0) * (t - t0) / (t1 - t0) if t1 > t0 else v1
    return None


def seen_rising_starts(rows):
    """Peak 1 s acceleration over 3 s, for starts first reported at 10 km/h or less."""
    peaks, total = [], 0
    for i in range(1, len(rows)):
        before, now = rows[i - 1], rows[i]
        if now[0] - before[0] > SPLIT_GAP_MS or before[1] != 0 or now[1] <= 0:
            continue
        total += 1
        if now[1] * 3.6 > 10:
            continue
        peak = None
        for offset in range(0, 3000, 1000):
            a, b = speed_at(rows, i, now[0] + offset), speed_at(rows, i, now[0] + offset + 1000)
            if a is None or b is None:
                peak = None
                break
            peak = b - a if peak is None else max(peak, b - a)
        if peak is not None and peak >= 0:
            peaks.append(peak)
    return peaks, total


def measure_file(path):
    result = {
        "minutes": defaultdict(float), "starts": defaultdict(list), "stops_3s": defaultdict(list),
        "stops_5s": defaultdict(list), "seen_rising": [], "all_starts": 0, "trips": 0, "dropped": 0,
        "repeated": {v: defaultdict(int) for v in REPEATED_VARIANTS},
    }
    for key, rows in read_trips(path).items():
        if len(rows) <= 30:
            continue
        period = poll_period(rows)
        if period is None or period > MAX_POLL_MS:
            continue
        vehicle = key[0]
        result["trips"] += 1
        peaks, total = seen_rising_starts(rows)
        result["seen_rising"] += peaks
        result["all_starts"] += total
        narrow = Detector(Limits(hard_window_ms=3000, **OFF))
        wide = Detector(Limits(hard_window_ms=5000, **OFF))
        swings = {
            v: Detector(Limits(swing=v[0], swing_window_ms=v[1], swing_count=v[2], **QUIET))
            for v in REPEATED_VARIANTS
        }
        for samples in resample(rows, period):
            result["minutes"][vehicle] += (len(samples) - 1) / 60
            for t, v in samples:
                for kind, _start, _end, peak in narrow.add(t, v):
                    if peak > PLAUSIBLE_MPS2:
                        result["dropped"] += 1
                    elif kind == "RAPID_START":
                        result["starts"][vehicle].append(peak)
                    elif kind == "HARD_STOP":
                        result["stops_3s"][vehicle].append(peak)
                for kind, _start, _end, peak in wide.add(t, v):
                    if kind == "HARD_STOP" and peak <= PLAUSIBLE_MPS2:
                        result["stops_5s"][vehicle].append(peak)
                for variant, detector in swings.items():
                    for found in detector.add(t, v):
                        if found[0] == "REPEATED_SPEED_CHANGE" and found[3] <= PLAUSIBLE_MPS2:
                            result["repeated"][variant][vehicle] += 1
            for kind, _start, _end, peak in narrow.finish():
                if kind == "RAPID_START" and peak <= PLAUSIBLE_MPS2:
                    result["starts"][vehicle].append(peak)
            wide.finish()
            for detector in swings.values():
                detector.finish()
    return result


def distribution(values):
    data = sorted(values)
    return {
        "n": len(data),
        "percentiles": {str(p): round(percentile(data, p), 2) for p in PERCENTILES},
        "share_at_or_above": {
            str(t): round(100 * sum(1 for v in data if v >= t) / len(data), 1) for t in SHARE_THRESHOLDS
        },
    }


def spread(values):
    data = sorted(values)
    return {"p25": round(percentile(data, 25), 2), "median": round(percentile(data, 50), 2), "p90": round(percentile(data, 90), 2)}


def main():
    folder, out_path = sys.argv[1], sys.argv[2]
    files = sorted(glob.glob(os.path.join(folder, "*.csv")))
    with Pool(max(1, (os.cpu_count() or 2) - 2)) as pool:
        parts = pool.map(measure_file, files, chunksize=1)
    minutes, trips = defaultdict(float), 0
    merged = {name: defaultdict(list) for name in ("starts", "stops_3s", "stops_5s")}
    repeated = {v: defaultdict(int) for v in REPEATED_VARIANTS}
    seen_rising, all_starts, dropped = [], 0, 0
    for part in parts:
        trips += part["trips"]
        seen_rising += part["seen_rising"]
        all_starts += part["all_starts"]
        dropped += part["dropped"]
        for vehicle, value in part["minutes"].items():
            minutes[vehicle] += value
        for name in merged:
            for vehicle, values in part[name].items():
                merged[name][vehicle] += values
        for variant in REPEATED_VARIANTS:
            for vehicle, count in part["repeated"][variant].items():
                repeated[variant][vehicle] += count
    drivers = [v for v, m in minutes.items() if m >= MIN_DRIVER_MINUTES]
    total = sum(minutes.values())

    def rates(by_vehicle):
        return {
            str(t): spread([10 * sum(1 for p in by_vehicle.get(v, []) if p >= t) / minutes[v] for v in drivers])
            for t in RATE_THRESHOLDS
        }

    flat = {name: [p for values in merged[name].values() for p in values] for name in merged}
    result = {
        "trips": trips,
        "hours": round(total / 60, 1),
        "vehicles": len(minutes),
        "drivers_over_60_min": len(drivers),
        "dropped_over_6_mps2": dropped,
        "starts_per_10_min": round(10 * len(flat["starts"]) / total, 2),
        "stops_per_10_min": round(10 * len(flat["stops_3s"]) / total, 2),
        "starts_upper_estimate": distribution(flat["starts"]),
        "starts_seen_rising": {**distribution(seen_rising), "of_all_starts": all_starts},
        "stops_3s": distribution(flat["stops_3s"]),
        "stops_5s": distribution(flat["stops_5s"]),
        "start_rate_per_10_min_upper": rates(merged["starts"]),
        "stop_rate_per_10_min_3s": rates(merged["stops_3s"]),
        "stop_rate_per_10_min_5s": rates(merged["stops_5s"]),
        "repeated_per_10_min": {
            f"{v[0]}mps2_{int(v[1] / 1000)}s_{v[2]}changes": spread([10 * repeated[v].get(d, 0) / minutes[d] for d in drivers])
            for v in REPEATED_VARIANTS
        },
    }
    with open(out_path, "w") as f:
        json.dump(result, f, indent=1)
    print(json.dumps({k: result[k] for k in ("trips", "hours", "vehicles", "drivers_over_60_min", "dropped_over_6_mps2")}))


if __name__ == "__main__":
    main()
