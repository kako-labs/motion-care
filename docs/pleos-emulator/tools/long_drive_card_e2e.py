#!/usr/bin/env python3
"""End-to-end check of the rest card on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/long_drive_card_e2e.py    # about 2 hours 10 minutes

Drives at 30 km/h with navigation in front for 2 hours 5 minutes without a stop and checks that the rest card
appears once, about 2 hours after the car first moved, and is saved as LONG_DRIVE. Breaks and other edge cases are
covered by unit tests. Cabin air cards may also appear because the outdoor air is a live forecast; they do not
affect the result. Keep other heavy work such as Gradle builds off the host while it runs: a starved emulator lets
carwatchdog kill the vehicle HAL, and the car service restart kills the app with it. Set ANDROID_SERIAL when more than one device is attached. This script clears the app's data and
changes the car's position and climate, so it must never run against an emulator that belongs to other work.
"""
import sys
import threading
import time
import urllib.error

import environment_card_e2e as env
import recording_e2e as e2e

SEOUL = (126.978, 37.5665)  # emu geo fix takes longitude first
REST = "2시간 넘게 달렸어요. 안전한 곳에서 쉬어가보세요"
TWO_HOURS_MS = 2 * 60 * 60_000
DRIVE_MINUTES = 125


def control(speed, accelerator=15, brake=0):
    path = f"/vehicle/controls?speed={speed}&accelerator={accelerator}&brake={brake}&steering=0"
    for attempt in range(3):
        try:
            return e2e.http("PUT", path)
        except urllib.error.HTTPError as error:
            # The Virtual Console answers 503 when its session lapsed; a session request brings it back.
            if error.code != 503 or attempt == 2:
                raise
            print("console 503, renewing the session", flush=True)
            e2e.http("POST", "/browser/session?client=e2e")
            time.sleep(1)


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "console port:", e2e.PORT)
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    for permission in ("android.permission.POST_NOTIFICATIONS", "android.car.permission.CAR_SPEED",
                       "pleos.car.permission.FUSED_LOCATION"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {permission}")
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, SEOUL)).strip())
    print("climate on, recirculation off:", env.hvac(power=1, recirc=0))
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")
    e2e.app_screen()
    print("tap start:", e2e.tap("운행 기록 시작"))
    time.sleep(3)
    print("service:", e2e.service_state())
    watch = env.CardTexts()
    watch.start()
    e2e.shell(f"am start --user 10 -n {env.NAVIGATION} >/dev/null 2>&1")
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    for speed in range(0, 31, 5):
        control(speed)
        time.sleep(1)
    started = time.time()
    while time.time() - started < DRIVE_MINUTES * 60:
        control(30)
        time.sleep(5)
    control(0, accelerator=0, brake=30)
    time.sleep(3)
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    watch.stop_event.set()
    print("service before finish:", e2e.service_state())
    e2e.app_screen()
    print("tap finish:", e2e.tap("운행 기록 종료"))
    time.sleep(6)
    first_move = env.pull("motion-care-trips.db", "SELECT MIN(elapsed) AS ms FROM speed_samples WHERE speed > 0.5")
    events = env.pull("motion-care-trips.db", "SELECT elapsed, observed, pattern, outcome FROM guidance_events ORDER BY elapsed")
    moved = first_move[0]["ms"] if first_move else None
    rest = [e for e in events if e["pattern"] == "LONG_DRIVE"]
    print("first movement ms:", moved)
    print("guidance events:", events)
    print("cards seen:", watch.seen)
    ok = (moved is not None and len(rest) == 1 and rest[0]["outcome"] == "SHOWN"
          and TWO_HOURS_MS <= rest[0]["observed"] - moved < TWO_HOURS_MS + 60_000
          and watch.seen.count(REST) == 1)
    print("\n===== summary =====")
    print("PASS" if ok else "FAIL", "two hours of driving without a break shows the rest card once")
    print("files:", e2e.OUT)
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
