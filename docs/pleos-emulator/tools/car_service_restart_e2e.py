#!/usr/bin/env python3
"""End-to-end check that recording survives a car service restart on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/car_service_restart_e2e.py                 # about 3 minutes
    python3 docs/pleos-emulator/tools/car_service_restart_e2e.py --apk <apk>     # another build, e.g. before the fix

Starts a trip, drives at 30 km/h with navigation in front and crashes the system car service with `am crash`, the
same restart that carwatchdog caused when the host starved the emulator. Passes when the app process survives, the
speed is marked unavailable at the crash and recorded again afterwards, the trip finishes normally and the outside
temperature is read after the restart. Whether steering keeps arriving after the restart is printed but not judged.
Set ANDROID_SERIAL when more than one device is attached. This script clears the app's data and restarts the car
service, which also restarts other car apps, so it must never run against an emulator that belongs to other work.
"""
import argparse
import sys
import threading
import time

import environment_card_e2e as env
import recording_e2e as e2e

SEOUL = (126.978, 37.5665)  # emu geo fix takes longitude first
BEFORE_S = 40
AFTER_S = 60


def control(speed, steering=0, accelerator=15, brake=0):
    e2e.http("PUT", f"/vehicle/controls?speed={speed}&accelerator={accelerator}&brake={brake}&steering={steering}")


def app_pid():
    return e2e.shell(f"pidof {e2e.PKG}").strip() or None


def car_service_pid():
    # The system user's car service; the per-user process of the same name is not the one apps bind to.
    for line in e2e.shell("ps -A -o USER,PID,NAME").splitlines():
        parts = line.split()
        if len(parts) == 3 and parts[0] == "system" and parts[2] == "com.android.car":
            return parts[1]
    return None


def drive(seconds, steering):
    started = time.time()
    while time.time() - started < seconds:
        control(30, steering)
        time.sleep(2)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", default=e2e.APK)
    apk = parser.parse_args().apk
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "console port:", e2e.PORT)
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", e2e.adb("install", "-r", apk).strip().splitlines()[-1])
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    for permission in ("android.permission.POST_NOTIFICATIONS", "android.car.permission.CAR_SPEED",
                       "pleos.car.permission.FUSED_LOCATION"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {permission}")
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, SEOUL)).strip())
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    control(0, accelerator=0)
    e2e.app_screen()
    print("auto record:", e2e.auto_record_on())
    time.sleep(3)
    print("service:", e2e.service_state())
    e2e.shell(f"am start --user 10 -n {env.NAVIGATION} >/dev/null 2>&1")
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    for speed in range(0, 31, 5):
        control(speed)
        time.sleep(1)
    drive(BEFORE_S, steering=10)

    before_app, before_car = app_pid(), car_service_pid()
    print("before crash: app pid", before_app, "car service pid", before_car)
    print("crash:", e2e.shell(f"am crash {before_car}").strip() or "sent")
    time.sleep(5)
    drive(AFTER_S, steering=20)
    after_app, after_car = app_pid(), car_service_pid()
    print("after crash: app pid", after_app, "car service pid", after_car)
    survived = after_app is not None and after_app == before_app
    service = e2e.service_state()
    print("service before finish:", service)

    control(0, accelerator=0, brake=30)
    time.sleep(3)
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    if survived:
        e2e.app_screen()
        print("tap finish:", e2e.tap("운행 기록 종료"))
        time.sleep(6)
    trips = env.pull("motion-care-trips.db", "SELECT id, status FROM trips")
    samples = env.pull("motion-care-trips.db", "SELECT elapsed, speed, quality FROM speed_samples ORDER BY elapsed")
    steering = env.pull("motion-care-trips.db", "SELECT elapsed, degrees FROM steering_samples ORDER BY elapsed")
    lost = [s for s in samples if s["quality"] != "VALID"]
    marker = lost[0]["elapsed"] if lost else None
    after = [s for s in samples if marker is not None and s["elapsed"] > marker and s["quality"] == "VALID"]
    moving_after = [s for s in after if s["speed"] > 0.5]
    print("trips:", trips)
    print("speed samples:", len(samples), "unavailable:", [(s["elapsed"], s["quality"]) for s in lost])
    print("valid speed after the unavailable mark:", len(after), "moving:", len(moving_after),
          "first after ms:", after[0]["elapsed"] - marker if after else None)
    print("steering samples:", [(s["elapsed"], s["degrees"]) for s in steering])
    print("steering after the unavailable mark:",
          [(s["elapsed"], s["degrees"]) for s in steering if marker is not None and s["elapsed"] > marker])
    outside = []
    # The values at the end are stored after the outdoor air request, which waits out its timeout offline.
    waited = time.time()
    while trips and len(outside) < 2 and time.time() - waited < 30:
        outside = env.pull("motion-care-environment.db",
                           f"SELECT captured_at, outside_value, outside_state, outside_reason FROM snapshots "
                           f"WHERE trip_id = '{trips[0]['id']}' ORDER BY captured_at, id")
        if len(outside) < 2:
            time.sleep(2)
    print("outside temperature snapshots:", [(o["outside_value"], o["outside_state"], o["outside_reason"]) for o in outside])

    checks = [
        ("the car service restarted", before_car is not None and after_car is not None and after_car != before_car),
        ("the app process survived", survived),
        ("recording kept its foreground service", service == "running=True foreground=True"),
        ("speed was marked unavailable once at the restart", len(lost) == 1),
        ("speed was recorded again after the restart", len(moving_after) > 0),
        ("the trip finished normally", [t["status"] for t in trips] == ["COMPLETED"]),
        ("outside temperature was read after the restart",
         len(outside) >= 2 and outside[-1]["outside_state"] == "AVAILABLE"),
    ]
    print("\n===== summary =====")
    for name, ok in checks:
        print("PASS" if ok else "FAIL", name)
    print("files:", e2e.OUT)
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if all(ok for _, ok in checks) else 1)


if __name__ == "__main__":
    main()
