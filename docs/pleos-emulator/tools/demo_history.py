#!/usr/bin/env python3
"""Puts three weeks of generated driving history into the app on the Pleos emulator, for filming the demo.

    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/demo_history.py              # about a minute
    python3 docs/pleos-emulator/tools/demo_history.py --dry-run                            # summary only
    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/demo_history.py --check      # rules vs. the app

The trips are generated, not recorded. A road and driver model makes the speed and steering the car would have
sent (city lights and turns, congestion, highway ramps and lane changes, winding roads; each driver brakes and
accelerates harder or softer on each trip), and the script writes them straight into the app's databases in the
columns the app writes. Patterns and in-drive cards are not made up: they come from a copy of the app's rules
(PatternDetector, LateralDetector, GuidancePolicy, EnvironmentAdvisor) run over the generated curves. --check runs
that copy over trips the app recorded itself and compares, so a rule change in the app shows up as a mismatch.
Outdoor temperature and air quality are Open-Meteo values for Seoul at each trip's hour, fetched when the script
runs; cabin values and passengers' answers are generated. The last trip has no answers so they can be given on
camera.

Run demo_data.py once first, or anything else that leaves the four people in the app: this script keeps people,
seats and settings and replaces every trip, environment snapshot and answer.
"""
import argparse
import datetime as dt
import json
import math
import pathlib
import random
import sqlite3
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
import uuid
from collections import deque

PROFILE_DB = "databases/motion-care-profile.db"
TRIPS_DB = "databases/motion-care-trips.db"
ENVIRONMENT_DB = "databases/motion-care-environment.db"
KST = dt.timezone(dt.timedelta(hours=9))
LATITUDE, LONGITUDE = 37.6, 127.0  # rounded the way the app rounds before asking Open-Meteo

PATTERNS = {"RAPID_START", "HARD_STOP", "REPEATED_SPEED_CHANGE", "FAST_TURN", "REPEATED_SWAY"}
ADVICE = {"REDUCE_OUTSIDE_AIR", "TRY_OUTSIDE_AIR"}
DETECTOR_VERSION = "pattern-v1"


# ---------------------------------------------------------------------------------------------------------------
# A copy of the app's rules. Keep in step with data/trip/.../analysis and .../guidance; --check catches drift.
# ---------------------------------------------------------------------------------------------------------------

class Swing:
    """SwingTracker: a signal that changes direction enough times inside a window."""

    def __init__(self, pattern, limit=0.9, window_ms=30_000, count=3):
        self.pattern, self.limit, self.window_ms, self.count = pattern, limit, window_ms, count
        self.times, self.direction, self.peak = deque(), 0, 0.0

    def add(self, now, value):
        nxt = 1 if value >= self.limit else -1 if value <= -self.limit else self.direction
        if nxt != self.direction:
            if self.direction != 0:
                self.times.append(now)
            self.direction = nxt
        while self.times and now - self.times[0] > self.window_ms:
            self.times.popleft()
        self.peak = 0.0 if not self.times else max(self.peak, abs(value))
        if len(self.times) < self.count:
            return None
        found = (self.pattern, self.times[0], now, self.peak)
        self.times.clear()
        self.peak = 0.0
        return found

    def reset(self):
        self.times.clear()
        self.direction, self.peak = 0, 0.0


class Patterns:
    """PatternDetector with its default PatternLimits."""

    def __init__(self):
        self.speeds, self.accelerations = deque(), deque()
        self.swing = Swing("REPEATED_SPEED_CHANGE")
        self.stopped, self.started_at, self.start_peak = None, None, 0.0

    def add(self, now, speed):
        if speed is None:
            return self.finish()
        found = []
        if self.speeds and now - self.speeds[-1][0] > 1_000:
            found += self.finish()
        acceleration = self._acceleration(now, speed)
        if acceleration is not None:
            self.accelerations.append((now, acceleration))
            while now - self.accelerations[0][0] > 5_000:
                self.accelerations.popleft()
            if self.started_at is not None:
                self.start_peak = max(self.start_peak, acceleration)
                if now - self.started_at >= 3_000:
                    found += self._close_start(now)
            swing = self.swing.add(now, acceleration)
            if swing:
                found.append(swing)
        self._stop_state(now, speed, acceleration, found)
        return found

    def finish(self):
        found = self._close_start(self.speeds[-1][0]) if self.speeds else []
        self.speeds.clear()
        self.accelerations.clear()
        self.swing.reset()
        self.stopped, self.started_at, self.start_peak = None, None, 0.0
        return found

    def _acceleration(self, now, speed):
        self.speeds.append((now, speed))
        while now - self.speeds[0][0] > 1_000:
            self.speeds.popleft()
        oldest_ms, oldest = self.speeds[0]
        span = now - oldest_ms
        return None if span < 500 else (speed - oldest) / (span / 1000.0)

    def _close_start(self, end):
        if self.started_at is None:
            return []
        started, self.started_at = self.started_at, None
        return [] if self.start_peak < 2.0 else [("RAPID_START", started, end, self.start_peak)]

    def _stop_state(self, now, speed, acceleration, found):
        was = self.stopped
        stopped = speed <= 0.5 if was is None or was is False else speed < 1.0
        self.stopped = stopped
        if was is True and not stopped:
            self.started_at = now
            self.start_peak = max(acceleration if acceleration is not None else 0.0, 0.0)
        if was is False and stopped:
            found += self._close_start(now)
            strong = [a for a in self.accelerations if -a[1] >= 2.0]
            if strong:
                found.append(("HARD_STOP", strong[0][0], now, max(-a[1] for a in strong)))
            self.accelerations.clear()
            self.speeds.clear()
            self.speeds.append((now, speed))


def lateral_acceleration(speed, degrees):
    """LateralDetector's kinematic bicycle model: 3.0 m wheelbase, 14.26 steering ratio."""
    return speed * speed * math.tan(math.radians(degrees / 14.26)) / 3.0


class Lateral:
    """LateralDetector with its default LateralLimits."""

    def __init__(self):
        self.recent = deque()
        self.swing = Swing("REPEATED_SWAY")
        self.turn_start, self.turn_direction, self.turn_peak = None, 0, 0.0

    def add(self, now, speed, degrees):
        found = []
        if self.recent and now - self.recent[-1][0] > 1_000:
            found += self.finish()
        self.recent.append((now, lateral_acceleration(speed, degrees)))
        while now - self.recent[0][0] > 1_000:
            self.recent.popleft()
        if now - self.recent[0][0] < 500:
            return found
        average = sum(v for _, v in self.recent) / len(self.recent)
        swing = self.swing.add(now, average)
        if swing:
            found.append(swing)
        direction = 1 if average >= 0 else -1
        if self.turn_start is not None and (direction != self.turn_direction or abs(average) < 1.0):
            found += self._close_turn(now)
        if self.turn_start is None and abs(average) >= 2.0:
            self.turn_start, self.turn_direction, self.turn_peak = now, direction, 0.0
        if self.turn_start is not None:
            self.turn_peak = max(self.turn_peak, abs(average))
        return found

    def finish(self):
        found = self._close_turn(self.recent[-1][0]) if self.recent else []
        self.recent.clear()
        self.swing.reset()
        return found

    def _close_turn(self, end):
        if self.turn_start is None:
            return []
        start, self.turn_start = self.turn_start, None
        return [("FAST_TURN", start, end, self.turn_peak)]


class LongDrive:
    """LongDriveTracker: two hours without a 15 minute break."""

    def __init__(self):
        self.last, self.stopped_since, self.due = None, None, None

    def on_sample(self, now, speed):
        last, self.last = self.last, now
        if last is not None and now - last >= 900_000:
            self.due = None
        if speed <= 0.5:
            if self.stopped_since is None:
                self.stopped_since = now
            if now - self.stopped_since >= 900_000:
                self.due = None
            return False
        self.stopped_since = None
        if self.due is None:
            self.due = now + 7_200_000
        return now >= self.due

    def used(self, now):
        if self.due is not None:
            self.due = now + 7_200_000


class Guidance:
    """GuidancePolicy with its default limits, cards on and every card displayed."""

    def __init__(self):
        self.repeats, self.speeds = {}, deque()
        self.waiting, self.last_shown, self.calm_since = None, None, None
        self.decided, self.long_drive = set(), LongDrive()

    def on_observation(self, seen):
        pattern, _, now, _ = seen
        times = self.repeats.setdefault(pattern, deque())
        times.append(now)
        while now - times[0] > 600_000:
            times.popleft()
        if len(times) < 3:
            return []
        times.clear()
        if (self.waiting and self.waiting[0] in PATTERNS) or self._within(now):
            return [(now, now, pattern, "SKIPPED_INTERVAL")]
        self.waiting = (pattern, now)
        return []

    def on_advice(self, advice, now):
        if advice in self.decided or self.waiting is not None or self._within(now):
            return []
        self.waiting = (advice, now)
        return []

    def on_sample(self, now, speed, lateral):
        self._calm(now, speed, lateral)
        offered = []
        if self.long_drive.on_sample(now, speed) and not (self.waiting and self.waiting[0] == "LONG_DRIVE"):
            if self.waiting is None and not self._within(now):
                self.waiting = ("LONG_DRIVE", now)
        card = self.waiting
        if card is None:
            return offered
        if now - card[1] > 60_000:
            return offered + self._settle(card, now, "SKIPPED_EXPIRED")
        if self.calm_since is None or now - self.calm_since < 3_000:
            return offered
        self.last_shown = now
        return offered + self._settle(card, now, "SHOWN")

    def on_signal_lost(self):
        self.speeds.clear()
        self.calm_since = None

    def finish(self, now):
        return self._settle(self.waiting, now, "SKIPPED_EXPIRED") if self.waiting else []

    def _within(self, now):
        return self.last_shown is not None and now - self.last_shown < 900_000

    def _settle(self, card, now, outcome):
        self.waiting = None
        if card[0] in ADVICE:
            self.decided.add(card[0])
        elif card[0] == "LONG_DRIVE":
            self.long_drive.used(now)
        return [(now, card[1], card[0], outcome)]

    def _calm(self, now, speed, lateral):
        if self.speeds and now - self.speeds[-1][0] > 1_000:
            self.on_signal_lost()
        self.speeds.append((now, speed))
        while now - self.speeds[0][0] > 1_000:
            self.speeds.popleft()
        span = now - self.speeds[0][0]
        calm = span >= 500 and abs(speed - self.speeds[0][1]) / (span / 1000.0) < 0.9 and \
            (lateral is None or abs(lateral) < 0.9)
        self.calm_since = (self.calm_since if self.calm_since is not None else now) if calm else None


class Advisor:
    """EnvironmentAdvisor: cabin air advice only when the air intake contradicts the outdoor air."""

    def __init__(self):
        self.recirculating_since = None

    def on_snapshot(self, recirculating, grade, now):
        self.recirculating_since = (self.recirculating_since if self.recirculating_since is not None else now) \
            if recirculating else None
        if grade is None:
            return None
        if recirculating is False and grade in ("BAD", "VERY_BAD"):
            return "REDUCE_OUTSIDE_AIR"
        if self.recirculating_since is not None and grade == "GOOD" and now - self.recirculating_since >= 1_800_000:
            return "TRY_OUTSIDE_AIR"
        return None


def replay(speeds, steering, environment):
    """TripSessionController.accept over one trip: speeds are (elapsed, m/s or None), steering (elapsed, degrees),
    environment (elapsed, recirculating, grade) as the environment flow would emit them."""
    patterns, lateral, guidance, advisor = Patterns(), Lateral(), Guidance(), Advisor()
    observations, events = [], []
    steer, environment = deque(steering), deque(environment)
    degrees, last = None, 0
    for now, speed in speeds:
        while environment and environment[0][0] < now:
            at, recirculating, grade = environment.popleft()
            advice = advisor.on_snapshot(recirculating, grade, at)
            if advice:
                events += guidance.on_advice(advice, at)
        while steer and steer[0][0] < now:
            degrees = steer.popleft()[1]
        found = patterns.add(now, speed)
        found += lateral.add(now, speed, degrees) if speed is not None and degrees is not None else lateral.finish()
        observations += found
        for seen in found:
            events += guidance.on_observation(seen)
        if speed is None:
            guidance.on_signal_lost()
        else:
            lat = lateral_acceleration(speed, degrees) if degrees is not None else None
            events += guidance.on_sample(now, speed, lat)
        last = now
    observations += patterns.finish() + lateral.finish()
    events += guidance.finish(max(last, 0))
    return observations, events


# ---------------------------------------------------------------------------------------------------------------
# Road and driver model
# ---------------------------------------------------------------------------------------------------------------

STEP = 0.05  # seconds of simulated time per integration step


def kmh(value):
    return value / 3.6


def float32(value):
    """The car reports speed as a float, as the app's stored values show."""
    return struct.unpack("f", struct.pack("f", value))[0]


def wheel_for_radius(radius):
    """Steering wheel degrees for a curve of [radius] metres in the same bicycle model the app assumes."""
    return 14.26 * math.degrees(math.atan(3.0 / radius))


class Car:
    """Moves one small step at a time and keeps what the app would have received: a speed event about every
    155 ms (the rate the emulator gave) and a steering event whenever the wheel moves by half a degree."""

    def __init__(self, rng, style):
        self.rng, self.style = rng, style
        self.t = self.v = self.a = 0.0
        self.wheel = self.wheel_goal = 0.0
        self.wobble = 0.0
        self.speeds, self.steering = [], []
        self.next_event = rng.uniform(0.03, 0.2)
        self.sent_wheel = None
        self.gap_until = -1.0

    # Driver habits for this trip. [style] runs from 0 (gentle) to 1 (rough); each manoeuvre varies around it.
    def launch_rate(self):
        return (1.0 + 1.8 * self.style) * self.rng.uniform(0.85, 1.2)

    def brake_rate(self):
        return (1.0 + 2.1 * self.style) * self.rng.uniform(0.85, 1.2)

    def step(self, goal_acceleration, jerk=4.0, wheel_rate=200.0):
        limit = jerk * STEP
        self.a += max(-limit, min(limit, goal_acceleration - self.a))
        self.v += self.a * STEP
        if self.v <= 0.0:
            self.v = 0.0
            self.a = max(self.a, 0.0)
        change = wheel_rate * STEP
        self.wheel += max(-change, min(change, self.wheel_goal - self.wheel))
        if abs(self.wheel_goal) < 1.0:  # small corrections on a straight road
            self.wobble += self.rng.gauss(0, 0.6) - self.wobble * 0.08
        else:
            self.wobble *= 0.9
        self.t += STEP
        self._send()

    def _send(self):
        while self.t >= self.next_event:
            at = int(round(self.next_event * 1000))
            if self.next_event < self.gap_until:
                self.speeds.append((at, None))
            else:
                noise = self.rng.gauss(0, 0.012) if self.v > 0.3 else 0.0
                self.speeds.append((at, float32(max(0.0, self.v + noise)) if self.v > 0 else 0.0))
            wheel = round(self.wheel + self.wobble, 1)
            if self.sent_wheel is None or abs(wheel - self.sent_wheel) >= 0.5:
                steer_at = at + self.rng.randint(2, 40)
                if not self.steering or steer_at > self.steering[-1][0]:
                    self.steering.append((steer_at, wheel))
                    self.sent_wheel = wheel
            self.next_event += self.rng.uniform(0.13, 0.18)

    # Manoeuvres -------------------------------------------------------------------------------------------------
    def idle(self, seconds):
        end = self.t + seconds
        while self.t < end:
            self.step(0.0)

    def reach(self, target_kmh, rise=None, fall=None, settle=0.4):
        """Speeds up or slows down to a speed, easing in at the end the way a driver does."""
        target = kmh(target_kmh)
        rise = rise or self.launch_rate()
        fall = fall or self.brake_rate() * 0.7
        for _ in range(int(120 / STEP)):
            gap = target - self.v
            if abs(gap) < kmh(settle) and abs(self.a) < 0.08:
                break
            goal = max(-fall, min(rise, 1.1 * gap))
            self.step(goal, jerk=3.0 + 4.0 * self.style)

    def stop(self, rate=None):
        """Brakes to a standstill; the last few km/h are always soft."""
        rate = rate or self.brake_rate()
        for _ in range(int(90 / STEP)):
            if self.v <= 0.02:
                break
            goal = -rate * min(1.0, (self.v + 0.4) / 2.6)
            self.step(goal, jerk=3.0 + 5.0 * self.style)
        self.v, self.a = 0.0, 0.0
        self.step(0.0)

    def cruise(self, seconds, mean_kmh, spread_kmh=4.0, ease=0.5):
        """Keeps with traffic: the speed drifts toward a new target every few seconds."""
        end = self.t + seconds
        while self.t < end:
            target = kmh(mean_kmh + self.rng.uniform(-spread_kmh, spread_kmh))
            hold = self.t + self.rng.uniform(5, 14)
            while self.t < min(hold, end):
                self.step(max(-ease, min(ease, 0.6 * (target - self.v))), jerk=2.0)

    def congestion(self, seconds):
        """Stop and go; a rough driver closes every gap and brakes for it."""
        end = self.t + seconds
        swing = 0.35 + 1.0 * self.style
        while self.t < end:
            self.reach(self.rng.uniform(12, 28), rise=swing * self.rng.uniform(0.8, 1.2),
                       fall=swing * self.rng.uniform(0.8, 1.25), settle=1.5)
            self.cruise(self.rng.uniform(2, 6), self.v * 3.6, 2.0)
            if self.rng.random() < 0.35:
                self.stop(swing * 1.1)
                self.idle(self.rng.uniform(2, 7))
            else:
                self.reach(self.rng.uniform(3, 10), fall=swing * self.rng.uniform(0.8, 1.25), settle=1.5)

    def steer_for(self, seconds, wheel, rate=200.0, keep_kmh=None):
        """Holds the wheel at an angle, then straightens it."""
        self.wheel_goal = wheel
        end = self.t + seconds
        while self.t < end:
            goal = 0.0 if keep_kmh is None else max(-0.8, min(0.8, 0.8 * (kmh(keep_kmh) - self.v)))
            self.step(goal, wheel_rate=rate)
        self.wheel_goal = 0.0
        while abs(self.wheel) > 0.5:
            self.step(0.0, wheel_rate=rate)

    def turn(self, radius, heading=90.0, from_stop=False):
        """A turn at a junction. The speed through it is the driver's habit."""
        speed = 12 + 9 * self.style + self.rng.uniform(-2, 2)
        if from_stop:
            self.reach(speed * 0.6, rise=self.launch_rate() * 0.8)
        else:
            self.reach(speed, fall=self.brake_rate() * 0.8)
        seconds = math.radians(heading) * radius / max(self.v, kmh(6))
        self.steer_for(seconds, self.rng.choice((-1, 1)) * wheel_for_radius(radius), rate=220.0, keep_kmh=speed)

    def lane_change(self, wheel, seconds):
        side = self.rng.choice((-1, 1))
        self.steer_for(seconds / 2, side * wheel, rate=40.0, keep_kmh=self.v * 3.6)
        self.steer_for(seconds / 2, -side * wheel * 0.9, rate=40.0, keep_kmh=self.v * 3.6)

    def curve(self, radius, seconds, side):
        """A road curve taken at the current speed, or slower for a gentle driver."""
        safe = math.sqrt((1.1 + 1.4 * self.style) * radius) * 3.6
        if self.v * 3.6 > safe:
            self.reach(safe, fall=self.brake_rate() * 0.6)
        self.steer_for(seconds, side * wheel_for_radius(radius), rate=60.0, keep_kmh=self.v * 3.6)

    def signal_gap(self, seconds):
        """The speed signal is briefly unavailable, as it is when the car service restarts."""
        self.gap_until = self.t + seconds


def city(car, minutes, lights=0.55, turns=0.25, crowded=0.12):
    """Blocks of urban road: cruise between junctions, then a red light, a turn or a clear run."""
    end = car.t + minutes * 60
    while car.t < end:
        mean = car.rng.uniform(38, 58)
        if car.v < kmh(15):
            car.reach(mean)
        car.cruise(car.rng.uniform(20, 70), mean, 4.0)
        if car.rng.random() < 0.2:
            car.lane_change(car.rng.uniform(6, 12), car.rng.uniform(3, 4.5))
        roll = car.rng.random()
        if roll < crowded:
            car.congestion(car.rng.uniform(40, 150))
        elif roll < crowded + lights:
            # A yellow light now and then makes even a gentle driver brake hard.
            hard = car.rng.random() < 0.10 + 0.2 * car.style
            car.stop(car.rng.uniform(2.5, 3.4) if hard else None)
            car.idle(car.rng.uniform(15, 80))
            if car.rng.random() < turns:
                car.turn(car.rng.uniform(10, 22), from_stop=True)
        elif roll < crowded + lights + turns:
            car.turn(car.rng.uniform(10, 22))


def expressway(car, minutes, mean_kmh=75):
    """Urban expressway: faster, with waves of slowing traffic."""
    car.reach(mean_kmh)
    end = car.t + minutes * 60
    while car.t < end:
        car.cruise(car.rng.uniform(40, 120), mean_kmh, 7.0, ease=0.6)
        roll = car.rng.random()
        if roll < 0.3:
            car.congestion(car.rng.uniform(60, 180))
            car.reach(mean_kmh)
        elif roll < 0.75:
            car.lane_change(car.rng.uniform(3, 6), car.rng.uniform(3.5, 5))


def highway(car, minutes):
    """On-ramp loop, cruising with lane changes and long bends, then the off-ramp."""
    car.reach(30 + 12 * car.style)
    car.curve(car.rng.uniform(38, 50), car.rng.uniform(9, 13), car.rng.choice((-1, 1)))
    mean = car.rng.uniform(92, 104)
    car.reach(mean, rise=1.0 + 0.8 * car.style)
    end = car.t + minutes * 60
    while car.t < end:
        car.cruise(car.rng.uniform(50, 160), mean, 6.0, ease=0.5)
        roll = car.rng.random()
        if roll < 0.5:
            car.lane_change(car.rng.uniform(1.5, 2.8), car.rng.uniform(4, 6))
        elif roll < 0.75:
            car.curve(car.rng.uniform(700, 1100), car.rng.uniform(15, 30), car.rng.choice((-1, 1)))
        elif roll < 0.83:
            car.congestion(car.rng.uniform(60, 150))
            car.reach(mean)
    car.reach(55 + 10 * car.style, fall=1.0 + 1.0 * car.style)
    car.curve(car.rng.uniform(55, 70), car.rng.uniform(7, 10), car.rng.choice((-1, 1)))


def winding(car, minutes):
    """A hill road: one bend after another, left and right."""
    car.reach(45)
    end = car.t + minutes * 60
    side = car.rng.choice((-1, 1))
    while car.t < end:
        car.curve(car.rng.uniform(45, 80), car.rng.uniform(3.5, 6), side)
        side = -side
        car.cruise(car.rng.uniform(4, 15), 42 + 10 * car.style, 3.0)


def leave_parking(car):
    car.idle(car.rng.uniform(2, 6))
    car.reach(8, rise=0.8)
    car.steer_for(car.rng.uniform(3, 5), car.rng.choice((-1, 1)) * wheel_for_radius(6), rate=150.0, keep_kmh=8)


def park(car):
    car.reach(10, fall=1.0)
    car.steer_for(car.rng.uniform(3, 5), car.rng.choice((-1, 1)) * wheel_for_radius(6), rate=150.0, keep_kmh=7)
    car.stop(0.9)
    car.idle(car.rng.uniform(4, 12))


ROUTES = {
    "school": lambda car, m: city(car, m, lights=0.6, turns=0.3, crowded=0.18),
    "academy": lambda car, m: city(car, m),
    "errand": lambda car, m: city(car, m, lights=0.55, turns=0.3, crowded=0.1),
    "commute": lambda car, m: (city(car, m * 0.25), expressway(car, m * 0.55), city(car, m * 0.2)),
    "outing": lambda car, m: (city(car, m * 0.15), highway(car, m * 0.7), city(car, m * 0.15)),
    "hills": lambda car, m: (city(car, m * 0.15), highway(car, m * 0.6), winding(car, m * 0.1),
                             city(car, m * 0.15)),
}


def drive(rng, route, minutes, style, gap=False):
    car = Car(rng, style)
    leave_parking(car)
    planned = max(1.0, minutes - 1.2)
    if gap:
        mark = car.t
        ROUTES[route](car, planned / 2)
        car.signal_gap(rng.uniform(2.5, 4.0))
        ROUTES[route](car, planned - (car.t - mark) / 60)
    else:
        ROUTES[route](car, planned)
    park(car)
    return car


# ---------------------------------------------------------------------------------------------------------------
# Three weeks of one family's trips
# ---------------------------------------------------------------------------------------------------------------

FAMILY = {"DRIVER": "jiyoung", "FRONT_PASSENGER": "minjun", "REAR_RIGHT": "seoa", "REAR_LEFT": "doyun"}
SCHOOL = {"DRIVER": "jiyoung", "REAR_RIGHT": "seoa", "REAR_LEFT": "doyun"}
ACADEMY = {"DRIVER": "jiyoung", "REAR_RIGHT": "seoa"}
MINJUN_FAMILY = {"DRIVER": "minjun", "FRONT_PASSENGER": "jiyoung", "REAR_RIGHT": "seoa", "REAR_LEFT": "doyun"}
MINJUN_ALONE = {"DRIVER": "minjun"}
EVENING = {"DRIVER": "jiyoung", "FRONT_PASSENGER": "minjun", "REAR_RIGHT": "seoa"}

# (date, time, route, minutes, seats, driving style 0..1, recirculation on, cabin set °C)
# 지영 starts rough and gets gentler, with a hurried morning in between; 민준 drives steadily.
TRIPS = [
    ("2026-10-21", "08:12", "school", 14, SCHOOL, 0.86, True, 24.0),
    ("2026-10-22", "07:48", "commute", 33, MINJUN_ALONE, 0.42, False, 22.0),
    ("2026-10-23", "16:41", "academy", 11, ACADEMY, 0.80, False, 24.0),
    ("2026-10-24", "11:18", "outing", 66, FAMILY, 0.76, True, 23.5),
    ("2026-10-24", "17:52", "outing", 71, MINJUN_FAMILY, 0.45, True, 22.5),
    ("2026-10-27", "08:10", "school", 15, SCHOOL, 0.78, True, 24.0),
    ("2026-10-28", "19:06", "errand", 22, EVENING, 0.70, False, 23.5),
    ("2026-10-29", "08:14", "school", 13, SCHOOL, 0.66, True, 23.5),
    ("2026-10-30", "07:51", "commute", 36, MINJUN_ALONE, 0.50, False, 22.0),
    ("2026-10-31", "10:32", "hills", 52, MINJUN_FAMILY, 0.48, True, 22.5),
    ("2026-11-01", "14:21", "errand", 27, FAMILY, 0.56, False, 23.0),
    ("2026-11-03", "08:11", "school", 14, SCHOOL, 0.50, True, 23.0),
    ("2026-11-04", "08:31", "school", 16, SCHOOL, 0.74, True, 23.0),
    ("2026-11-05", "16:39", "academy", 11, ACADEMY, 0.50, False, 23.0),
    ("2026-11-06", "19:18", "errand", 25, FAMILY, 0.46, False, 23.0),
    ("2026-11-08", "12:57", "hills", 58, FAMILY, 0.40, True, 23.0),
    ("2026-11-08", "18:09", "outing", 49, MINJUN_FAMILY, 0.42, True, 22.5),
    ("2026-11-10", "08:12", "school", 14, SCHOOL, 0.27, True, 23.0),
    ("2026-11-11", "08:13", "school", 13, SCHOOL, 0.24, True, 23.0),
    ("2026-11-11", "18:21", "errand", 21, EVENING, 0.22, False, 23.0),
]
GAP_TRIPS = {6}  # one trip has a few seconds without a speed signal

HOME_SEATS = EVENING  # who sits where for the filming, the same as the last trip


def fetch_hourly(url):
    with urllib.request.urlopen(url, timeout=30) as response:
        hourly = json.load(response)["hourly"]
    return hourly


def weather():
    """Hourly Seoul values for the last 92 days, the most Open-Meteo gives, so the script runs until early February:
    outdoor temperature and the CAMS air quality the app also asks for."""
    base = f"latitude={LATITUDE}&longitude={LONGITUDE}&past_days=92&forecast_days=1&timezone=Asia%2FSeoul"
    temperature = fetch_hourly(f"https://api.open-meteo.com/v1/forecast?{base}&hourly=temperature_2m")
    air = fetch_hourly(f"https://air-quality-api.open-meteo.com/v1/air-quality?{base}&hourly=pm10,pm2_5"
                       "&domains=cams_global")
    by_hour = {}
    for i, stamp in enumerate(temperature["time"]):
        by_hour.setdefault(stamp, {})["temperature"] = temperature["temperature_2m"][i]
    for i, stamp in enumerate(air["time"]):
        by_hour.setdefault(stamp, {}).update(pm10=air["pm10"][i], pm25=air["pm2_5"][i])
    return by_hour


def air_grade(pm10, pm25):
    """AirGrades.airGradeOf: the worse of the PM10 and PM2.5 grades, after rounding to whole µg/m³."""
    def grade(value, bounds):
        rounded = math.floor(value + 0.5)
        for step, bound in enumerate(bounds):
            if rounded <= bound:
                return step
        return len(bounds)
    steps = [grade(v, b) for v, b in ((pm10, (30, 80, 150)), (pm25, (15, 35, 75))) if v is not None]
    return ("GOOD", "MODERATE", "BAD", "VERY_BAD")[max(steps)] if steps else None


def snapshot(trip_id, at_ms, hour, outside, cabin, set_temp, recirculating, cleaner, rng):
    hour_ms = int(dt.datetime.fromisoformat(hour).replace(tzinfo=KST).timestamp() * 1000)
    reading = lambda: at_ms - rng.randint(5, 900)  # noqa: E731 - each value carries its own reading time
    pm10, pm25 = outside.get("pm10"), outside.get("pm25")
    return {
        "trip_id": trip_id, "captured_at": at_ms,
        "outside_value": round(outside["temperature"] * 2) / 2, "outside_state": "AVAILABLE", "outside_reason": None,
        "outside_at": reading(), "outside_source": "VEHICLE",
        "cabin_value": round(cabin, 1), "cabin_state": "AVAILABLE", "cabin_reason": None, "cabin_at": reading(),
        "cabin_source": "VEHICLE",
        "cabin_set_value": set_temp, "cabin_set_state": "AVAILABLE", "cabin_set_reason": None,
        "cabin_set_at": reading(), "cabin_set_source": "VEHICLE",
        "recirculation_value": 1.0 if recirculating else 0.0, "recirculation_state": "AVAILABLE",
        "recirculation_reason": None, "recirculation_at": reading(), "recirculation_source": "VEHICLE",
        "air_cleaner_value": 1.0 if cleaner else 0.0, "air_cleaner_state": "AVAILABLE", "air_cleaner_reason": None,
        "air_cleaner_at": reading(), "air_cleaner_source": "VEHICLE",
        "air_pm10": pm10, "air_pm25": pm25, "air_grade": air_grade(pm10, pm25),
        "air_latitude": LATITUDE, "air_longitude": LONGITUDE,
        "air_state": "AVAILABLE" if pm10 is not None else "UNAVAILABLE", "air_reason": None, "air_at": hour_ms,
        "air_source": "OPEN_METEO_CAMS",
    }


PEOPLE_HABITS = {
    # person: (how much rough starts and stops bother them, how much curves do, how much stop-and-go does)
    "minjun": (1.0, 0.3, 1.0),
    "seoa": (0.5, 1.0, 0.3),
    "doyun": (0.4, 0.4, 0.3),
    "jiyoung": (0.3, 0.3, 0.3),
}


def answers(rng, seats, counts, minutes, cabin_end, set_temp, recirculating):
    """A passenger's comfort follows what happened on the trip and what they are sensitive to."""
    per_ten = {p: n * 10 / max(minutes, 1) for p, n in counts.items()}
    result = {}
    for seat, person in seats.items():
        if seat == "DRIVER" or rng.random() < 0.15:
            continue
        starts_stops, curves, waves = PEOPLE_HABITS[person]
        jolts = per_ten.get("HARD_STOP", 0) + per_ten.get("RAPID_START", 0)
        bends = counts.get("FAST_TURN", 0) + counts.get("REPEATED_SWAY", 0)
        penalty = starts_stops * min(jolts / 2.2, 2.5) + curves * min(bends / 2.0, 2.0) + \
            waves * min(counts.get("REPEATED_SPEED_CHANGE", 0) / 2.0, 1.5)
        hot = cabin_end > 24.0
        stuffy = recirculating and minutes > 40
        penalty += 0.6 * hot + 0.5 * stuffy + (0.5 if person in ("seoa", "doyun") and minutes > 50 else 0)
        rating = max(1, min(5, round(5 - penalty + rng.uniform(-0.5, 0.5))))
        reasons = []
        if rating <= 3:
            if starts_stops * jolts >= 1.0:
                reasons.append("START_STOP")
            if waves * counts.get("REPEATED_SPEED_CHANGE", 0) >= 1:
                reasons.append("REPEATED_SPEED_CHANGE")
            if curves * bends >= 1:
                reasons.append("CURVE")
            if hot:
                reasons.append("FELT_HOT")
            if stuffy:
                reasons.append("STUFFY_AIR")
            if not reasons:
                reasons.append("UNKNOWN")
        result[person] = (rating, ",".join(reasons), "GUARDIAN" if person in ("seoa", "doyun") else "SELF")
    return result


def generate(seed, by_hour):
    rng = random.Random(seed)
    trips = []
    for index, (day, clock, route, minutes, seats, style, recirculating, set_temp) in enumerate(TRIPS):
        started = dt.datetime.fromisoformat(f"{day}T{clock}").replace(tzinfo=KST)
        started_ms = int(started.timestamp() * 1000) + rng.randint(0, 59_999)
        car = drive(rng, route, minutes, style, gap=index in GAP_TRIPS)
        duration_ms = car.speeds[-1][0] + rng.randint(150, 400)
        trip_id = str(uuid.UUID(int=rng.getrandbits(128), version=4))
        hours = [started_ms + k for k in range(0, duration_ms, 3_600_000)]
        weather_at = lambda ms: by_hour[dt.datetime.fromtimestamp(ms / 1000, KST).strftime("%Y-%m-%dT%H:00")]  # noqa
        hour_of = lambda ms: dt.datetime.fromtimestamp(ms / 1000, KST).strftime("%Y-%m-%dT%H:00")  # noqa: E731
        outside = weather_at(started_ms)
        grade = air_grade(outside.get("pm10"), outside.get("pm25"))
        cleaner = grade in ("BAD", "VERY_BAD") and rng.random() < 0.6
        # The car sat outside: the cabin starts near the outdoor temperature and warms toward the set point.
        cabin_start = outside["temperature"] + rng.uniform(2.0, 6.0)
        warm = lambda ms: set_temp - (set_temp - cabin_start) * math.exp(-(ms - started_ms) / 600_000) \
            + rng.uniform(-0.4, 0.6)  # noqa: E731
        snapshots = [snapshot(trip_id, at + rng.randint(80, 140), hour_of(at), weather_at(at), warm(at) if k else
                              cabin_start, set_temp, recirculating, cleaner, rng) for k, at in enumerate(hours)]
        end_ms = started_ms + duration_ms + rng.randint(90, 160)
        cabin_end = warm(end_ms)
        snapshots.append(snapshot(trip_id, end_ms, hour_of(end_ms), weather_at(end_ms), cabin_end, set_temp,
                                  recirculating, cleaner, rng))
        environment = [(rng.randint(1, 120) + k * 300_000, recirculating, air_grade(
            weather_at(started_ms + k * 300_000).get("pm10"), weather_at(started_ms + k * 300_000).get("pm25")))
            for k in range(duration_ms // 300_000 + 1)]
        observations, events = replay(car.speeds, car.steering, environment)
        counts = {}
        for pattern, *_ in observations:
            counts[pattern] = counts.get(pattern, 0) + 1
        last = index == len(TRIPS) - 1
        trips.append({
            "id": trip_id, "started": started_ms, "ended": started_ms + duration_ms, "seats": seats,
            "speeds": car.speeds, "steering": car.steering, "observations": observations, "events": events,
            "snapshots": snapshots, "counts": counts, "route": route, "style": style,
            "answers": {} if last else answers(rng, seats, counts, duration_ms / 60_000, cabin_end, set_temp,
                                               recirculating),
            "answered_at": started_ms + duration_ms + rng.randint(60_000, 900_000),
        })
    return trips


def write(trips, folder):
    """Replaces trips, snapshots and answers in local copies of the three databases."""
    trips_db = sqlite3.connect(folder / "motion-care-trips.db")
    profile_db = sqlite3.connect(folder / "motion-care-profile.db")
    environment_db = sqlite3.connect(folder / "motion-care-environment.db")
    people = {row[0] for row in profile_db.execute("SELECT id FROM people")}
    missing = {p for trip in trips for p in trip["seats"].values()} - people
    if missing:
        sys.exit(f"people missing from the app: {sorted(missing)}; run demo_data.py first")
    for table in ("guidance_events", "observations", "steering_samples", "speed_samples", "trips"):
        trips_db.execute(f"DELETE FROM {table}")
    profile_db.execute("DELETE FROM trip_feedback")
    profile_db.execute("DELETE FROM trip_participants")
    environment_db.execute("DELETE FROM snapshots")
    for trip in trips:
        tid = trip["id"]
        trips_db.execute("INSERT INTO trips (id, started, ended, status, vehicle_id, origin, revision) "
                         "VALUES (?, ?, ?, 'COMPLETED', NULL, 'VEHICLE', ?)",
                         (tid, trip["started"], trip["ended"], len(trip["speeds"]) // 6 + 2))
        trips_db.executemany("INSERT INTO speed_samples (trip_id, elapsed, speed, quality) VALUES (?, ?, ?, ?)",
                             [(tid, at, v, "VALID" if v is not None else "UNAVAILABLE") for at, v in trip["speeds"]])
        trips_db.executemany("INSERT INTO steering_samples (trip_id, elapsed, degrees) VALUES (?, ?, ?)",
                             [(tid, at, d) for at, d in trip["steering"]])
        trips_db.executemany("INSERT INTO observations (trip_id, pattern, start_ms, end_ms, peak_mps2, "
                             "detector_version) VALUES (?, ?, ?, ?, ?, ?)",
                             [(tid, p, s, e, peak, DETECTOR_VERSION) for p, s, e, peak in trip["observations"]])
        trips_db.executemany("INSERT INTO guidance_events (trip_id, elapsed, observed, pattern, outcome) "
                             "VALUES (?, ?, ?, ?, ?)", [(tid, *event) for event in trip["events"]])
        profile_db.executemany("INSERT INTO trip_participants (trip_id, seat, person_id, revision) VALUES (?, ?, ?, 0)",
                               [(tid, seat, person) for seat, person in trip["seats"].items()])
        profile_db.executemany(
            "INSERT INTO trip_feedback (trip_id, target_person_id, rating, scale_version, reasons, respondent, "
            "created_at) VALUES (?, ?, ?, 'COMFORT_5_V1', ?, ?, ?)",
            [(tid, person, rating, reasons, respondent, trip["answered_at"] + k * 1_500)
             for k, (person, (rating, reasons, respondent)) in enumerate(trip["answers"].items())])
        for row in trip["snapshots"]:
            columns = ", ".join(row)
            environment_db.execute(f"INSERT INTO snapshots ({columns}) VALUES ({', '.join('?' * len(row))})",
                                   tuple(row.values()))
    profile_db.execute("DELETE FROM current_seats")
    profile_db.executemany("INSERT INTO current_seats (seat, person_id) VALUES (?, ?)", HOME_SEATS.items())
    for db in (trips_db, profile_db, environment_db):
        db.commit()
        db.execute("VACUUM")
        db.close()


def summary(trips):
    print(f"{'trip':<17} {'route':<8} {'min':>4} {'km':>5} {'max':>4} {'style':>5}  patterns / cards / answers")
    for trip in trips:
        speeds = [(at, v) for at, v in trip["speeds"] if v is not None]
        km = sum((a[1] + b[1]) / 2 * (b[0] - a[0]) / 1000 for a, b in zip(speeds, speeds[1:])
                 if b[0] - a[0] <= 5000) / 1000
        top = max(v for _, v in speeds) * 3.6
        when = dt.datetime.fromtimestamp(trip["started"] / 1000, KST).strftime("%m-%d %a %H:%M")
        shown = [e[2] for e in trip["events"] if e[3] == "SHOWN"]
        answered = " ".join(f"{p}:{r}" for p, (r, *_) in trip["answers"].items())
        print(f"{when:<17} {trip['route']:<8} {(trip['ended'] - trip['started']) / 60000:>4.0f} {km:>5.1f} "
              f"{top:>4.0f} {trip['style']:>5.2f}  {trip['counts']} / {shown} / {answered}")


# ---------------------------------------------------------------------------------------------------------------
# Device
# ---------------------------------------------------------------------------------------------------------------

def device():
    import recording_e2e as e2e  # sets up adb for the chosen emulator on import
    return e2e


def pull(e2e, folder):
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    for db in (TRIPS_DB, PROFILE_DB, ENVIRONMENT_DB):
        data = subprocess.run(e2e.ADB + ["exec-out", f"run-as {e2e.PKG} --user 10 cat {db}"],
                              capture_output=True, timeout=120).stdout
        if not data.startswith(b"SQLite format 3"):
            sys.exit(f"could not read {db} from the app")
        (folder / pathlib.Path(db).name).write_bytes(data)
    files = e2e.shell(f"run-as {e2e.PKG} --user 10 ls databases").split()
    if any(name.endswith("-wal") for name in files):
        sys.exit("a database has a write-ahead log the copy would miss; open and close the app, then run again")


def push(e2e, folder):
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    for db in (TRIPS_DB, PROFILE_DB, ENVIRONMENT_DB):
        name = pathlib.Path(db).name
        subprocess.run(e2e.ADB + ["push", str(folder / name), f"/data/local/tmp/{name}"], capture_output=True,
                       check=True, timeout=120)
        e2e.shell(f"chmod 644 /data/local/tmp/{name}")
        e2e.shell(f"run-as {e2e.PKG} --user 10 sh -c 'rm -f {db}-journal {db}-wal {db}-shm && "
                  f"cp /data/local/tmp/{name} {db}'")
        e2e.shell(f"rm -f /data/local/tmp/{name}")


def check(folder):
    """Runs the copied rules over trips the app recorded and compares with what the app stored."""
    trips_db = sqlite3.connect(folder / "motion-care-trips.db")
    mismatches = 0
    for tid, started in trips_db.execute("SELECT id, started FROM trips WHERE status = 'COMPLETED' ORDER BY started"):
        speeds = [(at, v if q == "VALID" else None) for at, v, q in
                  trips_db.execute("SELECT elapsed, speed, quality FROM speed_samples WHERE trip_id = ? "
                                   "ORDER BY elapsed", (tid,))]
        steering = list(trips_db.execute("SELECT elapsed, degrees FROM steering_samples WHERE trip_id = ? "
                                         "ORDER BY elapsed", (tid,)))
        stored_events = list(trips_db.execute("SELECT elapsed, observed, pattern, outcome FROM guidance_events "
                                              "WHERE trip_id = ? ORDER BY elapsed", (tid,)))
        # What the environment flow emitted is not stored, and it can differ from the saved snapshots (a climate
        # change right at the start reaches the flow later). The stored advice events say what it called for, so
        # the guidance rules are checked with that input.
        environment = sorted(entry for _, observed, pattern, _ in stored_events if pattern in ADVICE for entry in (
            [(observed - 1_800_000, True, "GOOD"), (observed, True, "GOOD")] if pattern == "TRY_OUTSIDE_AIR"
            else [(observed, False, "BAD")]))
        observations, events = replay(speeds, steering, environment)
        stored = sorted((p, s, e, round(peak, 4)) for p, s, e, peak in trips_db.execute(
            "SELECT pattern, start_ms, end_ms, peak_mps2 FROM observations WHERE trip_id = ?", (tid,)))
        mine = sorted((p, s, e, round(peak, 4)) for p, s, e, peak in observations)
        same_events = sorted(events) == sorted(stored_events)
        ok = mine == stored and same_events
        mismatches += not ok
        print(f"{tid[:8]} samples {len(speeds):>5}  patterns {len(stored):>2} {'=' if mine == stored else '≠'} "
              f"{len(mine):>2}  cards {len(stored_events):>2} {'=' if same_events else '≠'} {len(events):>2}")
        if not ok:
            print("  app :", stored, stored_events)
            print("  copy:", mine, events)
    print("all trips match" if not mismatches else f"{mismatches} trips differ")
    return mismatches == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--seed", type=int, default=12)
    parser.add_argument("--dry-run", action="store_true", help="generate and print a summary; touch nothing")
    parser.add_argument("--check", nargs="?", const="device", metavar="FOLDER",
                        help="compare the copied rules with trips the app recorded (on the device, or in FOLDER)")
    args = parser.parse_args()
    work = pathlib.Path(tempfile.mkdtemp(prefix="demo-history-"))

    if args.check:
        if args.check == "device":
            e2e = device()
            pull(e2e, work)
            e2e.app_screen()
            e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
        sys.exit(0 if check(work if args.check == "device" else pathlib.Path(args.check)) else 1)

    trips = generate(args.seed, weather())
    summary(trips)
    if args.dry_run:
        return
    e2e = device()
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "work:", work)
    pull(e2e, work)
    write(trips, work)
    push(e2e, work)
    e2e.app_screen()
    time.sleep(4)
    e2e.shot("demo_history_home")
    print("screenshot:", e2e.OUT / "demo_history_home.png")
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")


if __name__ == "__main__":
    main()
