#!/usr/bin/env python3
"""Measures how often the in-drive guidance card would appear on the public VED data.

Usage: measure_guidance.py <folder of VED_*_week.csv> <output.json>

VED has no steering, so only forward and backward motion is used and left and right patterns are left out.
Quick starts are overstated in this data, as explained in measure_ved.py, so counts lean high.
"""
import glob
import json
import os
import sys
from collections import deque
from dataclasses import dataclass
from multiprocessing import Pool

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from detector import Detector
from measure_ved import MAX_POLL_MS, percentile, poll_period, read_trips, resample

MINUTE = 60000.0
SINGLE = {"RAPID_START", "HARD_STOP", "FAST_TURN"}
ALL_PATTERNS = SINGLE | {"REPEATED_SPEED_CHANGE", "REPEATED_SWAY"}


@dataclass(frozen=True)
class Rule:
    repeat_count: int = 3
    repeat_window_ms: float = 10 * MINUTE
    min_interval_ms: float = 15 * MINUTE
    calm_mps2: float = 0.9
    calm_for_ms: float = 3000
    expire_ms: float = 60000
    counted: frozenset = frozenset(ALL_PATTERNS)


class GuidancePolicy:
    """Port of data/trip/.../guidance/GuidancePolicy.kt without the settings and presenter."""

    def __init__(self, rule=Rule()):
        self.rule = rule
        self.times = {}
        self.pending = None
        self.last_shown = None
        self.calm_since = None
        self.speeds = deque()
        self.outcomes = []

    def observation(self, pattern, now):
        rule = self.rule
        if pattern in rule.counted:
            times = self.times.setdefault(pattern, deque())
            times.append(now)
            while now - times[0] > rule.repeat_window_ms:
                times.popleft()
            if len(times) < rule.repeat_count:
                return
            times.clear()
        too_soon = self.last_shown is not None and now - self.last_shown < rule.min_interval_ms
        if too_soon or self.pending is not None:
            self.outcomes.append("SKIPPED_INTERVAL")
            return
        self.pending = now

    def sample(self, now, speed):
        rule = self.rule
        self.speeds.append((now, speed))
        while now - self.speeds[0][0] > 1000:
            self.speeds.popleft()
        span = now - self.speeds[0][0]
        if span < 500:
            self.calm_since = None
        else:
            accel = (speed - self.speeds[0][1]) / (span / 1000)
            if abs(accel) >= rule.calm_mps2:
                self.calm_since = None
            elif self.calm_since is None:
                self.calm_since = now
        if self.pending is None:
            return
        if now - self.pending > rule.expire_ms:
            self.outcomes.append("SKIPPED_EXPIRED")
            self.pending = None
        elif self.calm_since is not None and now - self.calm_since >= rule.calm_for_ms:
            self.outcomes.append("SHOWN")
            self.last_shown = now
            self.pending = None

    def gap(self):
        self.speeds.clear()
        self.calm_since = None

    def finish(self):
        if self.pending is not None:
            self.outcomes.append("SKIPPED_EXPIRED")
            self.pending = None


RULES = {
    "3 times, 15 min gap": Rule(),
    "3 times, 5 min gap": Rule(min_interval_ms=5 * MINUTE),
    "3 times, 10 min gap": Rule(min_interval_ms=10 * MINUTE),
    "3 times, 20 min gap": Rule(min_interval_ms=20 * MINUTE),
    "2 times, 15 min gap": Rule(repeat_count=2),
    "4 times, 15 min gap": Rule(repeat_count=4),
    "repeated patterns at once, 15 min gap": Rule(counted=frozenset(SINGLE)),
    "every pattern at once, 15 min gap": Rule(repeat_count=1),
}


def measure_file(path):
    rows_out = {name: [] for name in RULES}
    for key, rows in read_trips(path).items():
        if len(rows) <= 30:
            continue
        period = poll_period(rows)
        if period is None or period > MAX_POLL_MS:
            continue
        events, minutes, timeline = [], 0.0, []
        detector = Detector()
        for samples in resample(rows, period):
            minutes += (len(samples) - 1) / 60
            timeline.append(samples)
            for t, v in samples:
                events += [(x[2], x[0]) for x in detector.add(t, v)]
            events += [(x[2], x[0]) for x in detector.finish()]
        if minutes < 5:
            continue
        events.sort()
        for name, rule in RULES.items():
            policy = GuidancePolicy(rule)
            index = 0
            for samples in timeline:
                policy.gap()
                for t, v in samples:
                    while index < len(events) and events[index][0] <= t:
                        policy.observation(events[index][1], events[index][0])
                        index += 1
                    policy.sample(t, v)
            policy.finish()
            rows_out[name].append((minutes, policy.outcomes.count("SHOWN"), policy.outcomes.count("SKIPPED_INTERVAL"),
                                   policy.outcomes.count("SKIPPED_EXPIRED")))
    return rows_out


def main():
    files = sorted(glob.glob(os.path.join(sys.argv[1], "*.csv")))
    with Pool(max(1, (os.cpu_count() or 2) - 2)) as pool:
        parts = pool.map(measure_file, files, chunksize=1)
    result = {}
    for name in RULES:
        rows = [row for part in parts for row in part[name]]
        total_minutes = sum(r[0] for r in rows)
        rates = sorted(10 * r[1] / r[0] for r in rows)
        long_trips = [r for r in rows if r[0] >= 20]
        shown_long = sorted(r[1] for r in long_trips)
        result[name] = {
            "trips": len(rows),
            "hours": round(total_minutes / 60, 1),
            "shown_per_10_min": round(10 * sum(r[1] for r in rows) / total_minutes, 2),
            "minutes_between_cards": round(total_minutes / max(sum(r[1] for r in rows), 1), 1),
            "trips_without_card_percent": round(100 * sum(1 for r in rows if r[1] == 0) / len(rows), 1),
            "per_trip_over_20_min": {
                "n": len(long_trips),
                "median": percentile(shown_long, 50),
                "p90": percentile(shown_long, 90),
                "max": shown_long[-1] if shown_long else None,
            },
            "skipped_interval": sum(r[2] for r in rows),
            "skipped_expired": sum(r[3] for r in rows),
            "rate_p90": round(percentile(rates, 90), 2),
        }
        print(name, json.dumps(result[name]), flush=True)
    with open(sys.argv[2], "w") as f:
        json.dump(result, f, indent=1)


if __name__ == "__main__":
    main()
