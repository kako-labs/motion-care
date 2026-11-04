"""Python port of data/trip/.../analysis/PatternDetector.kt for offline measurement.

The default limits must match PatternLimits in the app. check_port.py compares the two.
"""
import math
from collections import deque
from dataclasses import dataclass


@dataclass(frozen=True)
class Limits:
    stopped_below: float = 0.5
    moving_above: float = 1.0
    max_gap_ms: float = 1000
    window_ms: float = 1000
    min_span_ms: float = 500
    rapid_start: float = 2.0
    rapid_window_ms: float = 3000
    hard_stop: float = 2.0
    hard_window_ms: float = 5000
    swing: float = 0.9
    swing_window_ms: float = 30000
    swing_count: int = 3


class Detector:
    def __init__(self, limits=Limits()):
        self.l = limits
        self.reset()

    def reset(self):
        self.speeds = deque()
        self.accels = deque()
        self.swing_times = deque()
        self.swing_dir = 0
        self.swing_peak = 0.0
        self.stopped = None
        self.started_at = None
        self.start_peak = 0.0

    def finish(self):
        found = self._close_start(self.speeds[-1][0]) if self.speeds else []
        self.reset()
        return found

    def add(self, now, speed):
        found = []
        if self.speeds and now - self.speeds[-1][0] > self.l.max_gap_ms:
            found += self.finish()
        accel = self._acceleration(now, speed)
        if accel is not None:
            self.accels.append((now, accel))
            while now - self.accels[0][0] > self.l.hard_window_ms:
                self.accels.popleft()
            self._track_start(now, accel, found)
            self._track_swing(now, accel, found)
        self._track_stop_state(now, speed, accel, found)
        return found

    def _acceleration(self, now, speed):
        self.speeds.append((now, speed))
        while now - self.speeds[0][0] > self.l.window_ms:
            self.speeds.popleft()
        t0, v0 = self.speeds[0]
        span = now - t0
        return None if span < self.l.min_span_ms else (speed - v0) / (span / 1000.0)

    def _track_start(self, now, accel, found):
        if self.started_at is None:
            return
        self.start_peak = max(self.start_peak, accel)
        if now - self.started_at >= self.l.rapid_window_ms:
            found += self._close_start(now)

    def _close_start(self, end):
        if self.started_at is None:
            return []
        started = self.started_at
        self.started_at = None
        if self.start_peak < self.l.rapid_start:
            return []
        return [("RAPID_START", started, end, self.start_peak)]

    def _track_swing(self, now, accel, found):
        l = self.l
        if accel >= l.swing:
            direction = 1
        elif accel <= -l.swing:
            direction = -1
        else:
            direction = self.swing_dir
        if direction != self.swing_dir:
            if self.swing_dir != 0:
                self.swing_times.append(now)
            self.swing_dir = direction
        while self.swing_times and now - self.swing_times[0] > l.swing_window_ms:
            self.swing_times.popleft()
        self.swing_peak = 0.0 if not self.swing_times else max(self.swing_peak, abs(accel))
        if len(self.swing_times) >= l.swing_count:
            found.append(("REPEATED_SPEED_CHANGE", self.swing_times[0], now, self.swing_peak))
            self.swing_times.clear()
            self.swing_peak = 0.0

    def _track_stop_state(self, now, speed, accel, found):
        l = self.l
        was = self.stopped
        if was is None or was is False:
            is_stopped = speed <= l.stopped_below
        else:
            is_stopped = speed < l.moving_above
        self.stopped = is_stopped
        if was is True and not is_stopped:
            self.started_at = now
            self.start_peak = max(accel if accel is not None else 0.0, 0.0)
        if was is False and is_stopped:
            found += self._close_start(now)
            strong = [(t, a) for t, a in self.accels if -a >= l.hard_stop]
            if strong:
                found.append(("HARD_STOP", strong[0][0], now, max(-a for _, a in strong)))
            self.accels.clear()
            self.speeds.clear()
            self.speeds.append((now, speed))


@dataclass(frozen=True)
class LateralLimits:
    wheelbase_m: float = 3.0
    steering_ratio: float = 14.26
    max_gap_ms: float = 1000
    window_ms: float = 1000
    min_span_ms: float = 500
    fast_turn: float = 2.0
    turn_end_ratio: float = 0.5
    swing: float = 0.9
    swing_window_ms: float = 30000
    swing_count: int = 3


def lateral_acceleration(speed, degrees, limits=LateralLimits()):
    return speed * speed * math.tan(math.radians(degrees / limits.steering_ratio)) / limits.wheelbase_m


class LateralDetector:
    """Port of data/trip/.../analysis/LateralDetector.kt."""

    def __init__(self, limits=LateralLimits()):
        self.l = limits
        self.recent = deque()
        self.swing_times = deque()
        self.swing_dir = 0
        self.swing_peak = 0.0
        self.turn_start = None
        self.turn_dir = 0
        self.turn_peak = 0.0

    def add(self, now, speed, degrees):
        found = []
        if self.recent and now - self.recent[-1][0] > self.l.max_gap_ms:
            found += self.finish()
        self.recent.append((now, lateral_acceleration(speed, degrees, self.l)))
        while now - self.recent[0][0] > self.l.window_ms:
            self.recent.popleft()
        if now - self.recent[0][0] < self.l.min_span_ms:
            return found
        value = sum(v for _, v in self.recent) / len(self.recent)
        self._swing(now, value, found)
        self._turn(now, value, found)
        return found

    def finish(self):
        found = self._close(self.recent[-1][0]) if self.recent else []
        self.recent.clear()
        self.swing_times.clear()
        self.swing_dir = 0
        self.swing_peak = 0.0
        return found

    def _swing(self, now, value, found):
        l = self.l
        direction = 1 if value >= l.swing else -1 if value <= -l.swing else self.swing_dir
        if direction != self.swing_dir:
            if self.swing_dir != 0:
                self.swing_times.append(now)
            self.swing_dir = direction
        while self.swing_times and now - self.swing_times[0] > l.swing_window_ms:
            self.swing_times.popleft()
        self.swing_peak = 0.0 if not self.swing_times else max(self.swing_peak, abs(value))
        if len(self.swing_times) >= l.swing_count:
            found.append(("REPEATED_SWAY", self.swing_times[0], now, self.swing_peak))
            self.swing_times.clear()
            self.swing_peak = 0.0

    def _turn(self, now, value, found):
        l = self.l
        direction = 1 if value >= 0 else -1
        if self.turn_start is not None and (direction != self.turn_dir or abs(value) < l.fast_turn * l.turn_end_ratio):
            found += self._close(now)
        if self.turn_start is None and abs(value) >= l.fast_turn:
            self.turn_start, self.turn_dir, self.turn_peak = now, direction, 0.0
        if self.turn_start is not None:
            self.turn_peak = max(self.turn_peak, abs(value))

    def _close(self, end):
        if self.turn_start is None:
            return []
        start, self.turn_start = self.turn_start, None
        return [("FAST_TURN", start, end, self.turn_peak)]
