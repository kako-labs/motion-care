#!/usr/bin/env python3
"""End-to-end check of automatic recording on the Pleos emulator.

Turns the switch on, leaves the car parked behind navigation for IDLE_SECONDS (default 600), drives once so the
gear starts a trip, parks until the trip finishes by itself, then reboots the emulator and reinstalls the app to
see the watch come back without opening it. Build the debug APK first. Screenshots go to a temporary folder.
Set ANDROID_SERIAL when more than one device is attached. This script installs an app, drives the vehicle signals
and reboots the device, so it must never run against an emulator that belongs to other work.
"""
import os
import pathlib
import re
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request
import xml.etree.ElementTree as ET

SDK = pathlib.Path(os.environ.get("ANDROID_HOME", pathlib.Path.home() / "Library/Android/sdk"))
ADB_BIN = str(SDK / "platform-tools/adb")
PKG = "dev.motioncare.scaffold"
APK = str(pathlib.Path(__file__).resolve().parents[3] / "app/build/outputs/apk/debug/app-debug.apk")
OUT = pathlib.Path(tempfile.mkdtemp(prefix="auto-record-e2e-"))
IDLE_SECONDS = int(os.environ.get("IDLE_SECONDS", "600"))
# The app finishes a trip after three minutes in park and checks every five seconds.
FINISH_LIMIT_S = 180 + 30


def choose_serial():
    serial = os.environ.get("ANDROID_SERIAL")
    if serial:
        return serial
    listing = subprocess.run([ADB_BIN, "devices"], capture_output=True, text=True, timeout=30).stdout
    devices = [line.split()[0] for line in listing.splitlines()[1:] if line.endswith("device")]
    if len(devices) != 1:
        sys.exit(f"Set ANDROID_SERIAL to choose one of the attached devices: {devices}")
    return devices[0]


ADB = [ADB_BIN, "-s", choose_serial()]
PORT = None


def forward():
    # A free host port keeps this run from taking over another session's forward.
    global PORT
    PORT = subprocess.run(ADB + ["forward", "tcp:0", "tcp:8088"], capture_output=True, text=True, check=True,
                          timeout=30).stdout.strip()


def adb(*args, check=False, timeout=120):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, check=check, timeout=timeout).stdout


def shell(cmd):
    return adb("shell", cmd)


def shot(name):
    (OUT / f"{name}.png").write_bytes(subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def http(method, path):
    req = urllib.request.Request(f"http://127.0.0.1:{PORT}" + path, method=method)
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.status


def heartbeat(stop):
    while not stop.is_set():
        try:
            http("POST", "/browser/session?client=e2e")
        except Exception:
            pass
        stop.wait(1)


def nodes():
    shell("uiautomator dump --user 10 /data/local/tmp/w.xml >/dev/null 2>&1")
    raw = subprocess.run(ADB + ["exec-out", "cat", "/data/local/tmp/w.xml"], capture_output=True, timeout=30).stdout
    try:
        return list(ET.fromstring(raw).iter("node"))
    except ET.ParseError:
        return []


def bounds(node):
    return tuple(map(int, re.findall(r"\d+", node.get("bounds"))))


def texts():
    return [n.get("text") for n in nodes() if n.get("text")]


def tap(*wanted):
    for node in nodes():
        if node.get("text") in wanted or node.get("resource-id") in wanted:
            x1, y1, x2, y2 = bounds(node)
            shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            return node.get("text") or node.get("resource-id")
    return None


def switch_next_to(label):
    """The switch in the same row as the label, as (checked, center)."""
    found = nodes()
    rows = [bounds(n) for n in found if n.get("text") == label]
    if not rows:
        return None
    _, y1, _, y2 = rows[0]
    middle = (y1 + y2) // 2
    for node in found:
        if node.get("checkable") == "true":
            x1, t, x2, b = bounds(node)
            if t <= middle <= b:
                return node.get("checked") == "true", ((x1 + x2) // 2, (t + b) // 2)
    return None


def app_screen():
    shell(f"am start --user 10 -n {PKG}/dev.motioncare.app.MainActivity >/dev/null 2>&1")
    time.sleep(4)


def navigation():
    shell("am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1")
    time.sleep(3)


def service_state():
    out = shell(f"dumpsys activity services {PKG}")
    return {"running": "TripRecordingService" in out, "foreground": "isForeground=true" in out}


def recording_title():
    active = shell("dumpsys notification --noredact").split("mArchive=")[0]
    if "android.title=String (운행 기록 중)" in active:
        return "운행 기록 중"
    if "android.title=String (운행 자동 기록 켜짐)" in active:
        return "운행 자동 기록 켜짐"
    return None


def wait_for(check, limit, step=1.0):
    started = time.time()
    while time.time() - started < limit:
        if check():
            return time.time() - started
        time.sleep(step)
    return None


def trips():
    local = OUT / "trips.db"
    for suffix in ("", "-wal", "-shm"):
        data = subprocess.run(ADB + ["exec-out", f"run-as {PKG} --user 10 cat databases/motion-care-trips.db{suffix}"],
                              capture_output=True, timeout=60).stdout
        target = pathlib.Path(str(local) + suffix)
        if data and not data.startswith(b"cat:") and not data.startswith(b"run-as:"):
            target.write_bytes(data)
        elif target.exists():
            target.unlink()
    if not local.exists():
        return []
    db = sqlite3.connect(local)
    try:
        return db.execute("SELECT id, status, ended - started, "
                          "(SELECT count(*) FROM speed_samples s WHERE s.trip_id = trips.id) FROM trips ORDER BY started").fetchall()
    finally:
        db.close()


def drive(seconds=30):
    http("PUT", "/vehicle/gear?gear=Drive")
    started = time.time()
    for step in range(seconds):
        speed = min(40, step * 4)
        http("PUT", f"/vehicle/controls?speed={speed}&accelerator={15 if speed else 0}&brake=0&steering=0")
        time.sleep(1)
    for speed in (30, 20, 10, 0):
        http("PUT", f"/vehicle/controls?speed={speed}&accelerator=0&brake=30&steering=0")
        time.sleep(1)
    return time.time() - started


def wait_boot():
    adb("wait-for-device", timeout=600)
    wait_for(lambda: shell("getprop sys.boot_completed").strip() == "1", 600, step=3)
    # The driver's user unlocks after the system user, and only then gets the boot broadcast.
    wait_for(lambda: "RUNNING_UNLOCKED" in shell("am get-started-user-state 10"), 300, step=3)


def main():
    print("device:", ADB[-1], "avd:", adb("emu", "avd", "name").split()[0])
    forward()
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", adb("install", "-r", APK).strip().splitlines()[-1])
    shell(f"pm clear --user 10 {PKG}")
    http("PUT", "/vehicle/gear?gear=Park")
    http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")
    time.sleep(2)
    granted = re.findall(r"android\.car\.permission\.CAR_POWERTRAIN: granted=(\w+)", shell(f"dumpsys package {PKG}"))
    print("powertrain granted at install:", granted)

    print("\n--- home before turning it on")
    app_screen()
    shot("120_auto_off")
    print("status:", [t for t in texts() if "자동 기록" in t or "샘플" in t or t == "운행 시작"])
    print("switch:", switch_next_to("운행 자동 기록"))
    print("service:", service_state())

    print("\n--- turn it on")
    _, (x, y) = switch_next_to("운행 자동 기록")
    shell(f"input tap {x} {y}")
    time.sleep(3)
    allow = ("com.android.permissioncontroller:id/permission_allow_button",
             "com.android.permissioncontroller:id/permission_allow_foreground_only_button", "허용", "앱 사용 중에만 허용")
    for _ in range(3):
        hit = tap(*allow)
        print(" allowed via:", hit)
        time.sleep(2)
        if not hit:
            break
    time.sleep(2)
    shot("121_auto_on")
    print("switch:", switch_next_to("운행 자동 기록"), "status:", [t for t in texts() if "자동 기록" in t])
    print("service:", service_state(), "notification:", recording_title())

    print(f"\n--- parked behind navigation for {IDLE_SECONDS}s")
    navigation()
    idle_started = time.time()
    alive = True
    while time.time() - idle_started < IDLE_SECONDS:
        time.sleep(min(60, IDLE_SECONDS))
        state = service_state()
        if not (state["running"] and state["foreground"]):
            alive = False
            print(f" service gone after {time.time() - idle_started:.0f}s:", state)
            break
    print("idle survived:", alive, "after", round(time.time() - idle_started), "s;", service_state(), recording_title())

    print("\n--- leave park")
    before = len(trips())
    gear_at = time.time()
    threading.Thread(target=drive, daemon=True).start()
    started_after = wait_for(lambda: recording_title() == "운행 기록 중", 30)
    print("recording notification after", started_after, "s")
    time.sleep(36)
    shot("122_auto_driving")
    rows = trips()
    print("trips:", rows[before:])

    print("\n--- park")
    http("PUT", "/vehicle/gear?gear=Park")
    parked_at = time.time()
    finished_after = wait_for(lambda: recording_title() == "운행 자동 기록 켜짐", FINISH_LIMIT_S, step=2)
    print("finished after", finished_after, "s in park; trips:", trips()[before:])
    app_screen()
    shot("123_auto_finished")
    print("home:", [t for t in texts() if "주행" in t or "평가" in t or "자동 기록" in t][:6])
    tap("확인", "닫기")

    print("\n--- reboot without opening the app")
    stop.set()
    adb("forward", "--remove", f"tcp:{PORT}")
    reboot_at = time.time()
    adb("reboot", timeout=60)
    time.sleep(10)
    wait_boot()
    print("booted after", round(time.time() - reboot_at), "s")
    back = wait_for(lambda: service_state()["foreground"], 120, step=3)
    print("service in foreground", back, "s after unlock;", service_state(), recording_title())
    forward()
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(3)
    http("PUT", "/vehicle/gear?gear=Park")
    time.sleep(2)
    before = len(trips())
    threading.Thread(target=drive, daemon=True).start()
    print("recording after reboot:", wait_for(lambda: recording_title() == "운행 기록 중", 30), "s")
    time.sleep(36)
    http("PUT", "/vehicle/gear?gear=Park")
    print("finished after reboot:", wait_for(lambda: recording_title() == "운행 자동 기록 켜짐", FINISH_LIMIT_S, step=2),
          "s; trips:", trips()[before:])

    print("\n--- update the app")
    print("install:", adb("install", "-r", APK).strip().splitlines()[-1])
    print("service after update:", wait_for(lambda: service_state()["foreground"], 60, step=2), "s;", service_state())

    print("\n--- turn it off")
    app_screen()
    _, (x, y) = switch_next_to("운행 자동 기록")
    shell(f"input tap {x} {y}")
    time.sleep(3)
    shot("124_auto_off_again")
    print("switch:", switch_next_to("운행 자동 기록"), "service:", service_state(), "notification:", recording_title())
    print("all trips:", trips())
    print("screenshots:", OUT)
    stop.set()
    adb("forward", "--remove", f"tcp:{PORT}")


if __name__ == "__main__":
    main()
