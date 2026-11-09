#!/usr/bin/env python3
"""End-to-end check that the app stays on screen while driving, on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/driving_screen_e2e.py     # about 1 minute

MainActivity declares distractionOptimized, so the system should not cover it with its "주행 중에는 사용할 수
없습니다" screen. The run grants the permissions by adb, starts a recording while parked, drives at 40 km/h with the
app in front, and checks that the app keeps the screen and the recording, then parks and finishes. It also prints the
car's driving restrictions. Set ANDROID_SERIAL when more than one device is attached. This script clears the app's
data, so it must never run against an emulator that belongs to other work.
"""
import re
import sys
import threading
import time

import recording_e2e as e2e

BLOCKED = "주행 중에는 사용할 수 없습니다"
PERMISSIONS = ("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION",
               "android.permission.POST_NOTIFICATIONS")


def focused():
    found = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)", e2e.shell("dumpsys window displays"))
    return found.group(1) if found else "unknown"


def restrictions():
    """The current line looks like "Display id: 0 UXR: DO: true UxR: 80 time: ...". 80 is NO_SETUP and NO_VIDEO."""
    dump = e2e.shell("dumpsys car_service --services CarUxRestrictionsManagerService")
    found = re.search(r"Display id: 0 UXR: (DO: \w+ UxR: \d+)", dump)
    return found.group(1) if found else "unknown"


def step(name, seconds=3):
    time.sleep(seconds)
    seen = e2e.texts()
    e2e.shot(name)
    return seen


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0])
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    for permission in PERMISSIONS:
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {permission}")
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    e2e.app_screen()

    print("tap start:", e2e.tap("운행 시작"))
    parked = step("driving_screen_parked")
    print("parked:", focused(), [t for t in parked if "운행" in t])
    print("restrictions parked:", restrictions())

    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    e2e.http("PUT", "/vehicle/controls?speed=40&accelerator=15&brake=0&steering=0")
    time.sleep(6)
    e2e.app_screen()
    driving = step("driving_screen_40kmh")
    focus_driving = focused()
    service_driving = e2e.service_state()
    print("driving:", focus_driving, service_driving, [t for t in driving if "운행" in t or BLOCKED in t])
    print("restrictions driving:", restrictions())

    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    time.sleep(4)
    print("tap finish:", e2e.tap("운행 기록 종료"))
    finished = step("driving_screen_finished")
    print("finished:", [t for t in finished if "저장" in t])
    stop.set()

    checks = [
        ("recording starts while parked", "운행 기록 중" in parked),
        ("the app keeps the screen at 40 km/h", e2e.PKG in focus_driving and "운행 기록 중" in driving),
        ("no blocking screen at 40 km/h", BLOCKED not in driving),
        ("the recording runs while driving", "running=True" in service_driving),
        ("the recording finishes after parking", "주행 저장 완료" in finished),
    ]
    print("\n===== summary =====")
    for name, ok in checks:
        print("PASS" if ok else "FAIL", name)
    print("files:", e2e.OUT)
    sys.exit(0 if all(ok for _, ok in checks) else 1)


if __name__ == "__main__":
    main()
