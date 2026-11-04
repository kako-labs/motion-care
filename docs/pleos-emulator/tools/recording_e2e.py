#!/usr/bin/env python3
"""End-to-end check of vehicle recording on the Pleos emulator.

Start the emulator and build the debug APK first. Screenshots go to a temporary folder.
Set ANDROID_SERIAL when more than one device is attached. This script installs an app and drives the
vehicle signals, so it must never run against an emulator that belongs to other work.
"""
import os
import pathlib
import re
import sqlite3
import statistics
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
OUT = pathlib.Path(tempfile.mkdtemp(prefix="recording-e2e-"))


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
# A free host port keeps this run from taking over another session's forward.
PORT = subprocess.run(ADB + ["forward", "tcp:0", "tcp:8088"], capture_output=True, text=True, check=True, timeout=30).stdout.strip()
BASE = f"http://127.0.0.1:{PORT}"


def adb(*args, check=False):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, check=check, timeout=120).stdout


def shell(cmd):
    return adb("shell", cmd)


def shot(name):
    (OUT / f"{name}.png").write_bytes(subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)


def http(method, path):
    req = urllib.request.Request(BASE + path, method=method)
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


def texts():
    return [n.get("text") for n in nodes() if n.get("text")]


def tap(*wanted):
    for node in nodes():
        if node.get("text") in wanted or node.get("resource-id") in wanted:
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
            shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
            return node.get("text") or node.get("resource-id")
    return None


def app_screen():
    shell(f"am start --user 10 -n {PKG}/dev.motioncare.app.MainActivity >/dev/null 2>&1")
    time.sleep(4)


class CardWatch(threading.Thread):
    """Notes when the guidance card is posted and takes one screenshot while it is up."""

    def __init__(self, stop):
        super().__init__(daemon=True)
        self.stop_event = stop
        self.first, self.last, self.text = None, None, None

    def run(self):
        while not self.stop_event.is_set():
            # Only the active list counts. Look for the text, not the channel: while the recording notification
            # is up the system keeps a group summary that carries the guidance channel after the card is gone.
            active = shell("dumpsys notification --noredact").split("mArchive=")[0]
            found = re.search(r"android\.text=String \(([^)]*보세요)\)", active)
            if found:
                now = time.time()
                if self.first is None:
                    self.first = now
                    self.text = found.group(1)
                    time.sleep(1)
                    shot("84_guidance_card")
                self.last = now
            self.stop_event.wait(1)


def service_state():
    out = shell(f"dumpsys activity services {PKG}")
    running = "TripRecordingService" in out
    foreground = "isForeground=true" in out
    return f"running={running} foreground={foreground}"


def main():
    print("device:", ADB[-1], "avd:", adb("emu", "avd", "name").split()[0], "console port:", PORT)
    stop = threading.Event()
    threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", adb("install", "-r", APK).strip().splitlines()[-1])
    shell(f"pm clear --user 10 {PKG}")
    print("gear park:", http("PUT", "/vehicle/gear?gear=Park"), "speed 0:", http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0"))
    time.sleep(2)
    app_screen()
    shot("80_ready")
    print("ready screen:", [t for t in texts() if "운행" in t or "개발" in t or "샘플" in t or "안내" in t])

    print("\n--- deny")
    print("tap start:", tap("운행 기록 시작"))
    time.sleep(3)
    shot("81_permission")
    print("dialog:", texts()[:8])
    deny_ids = ("com.android.permissioncontroller:id/permission_deny_button", "거부", "허용 안함", "허용 안 함", "Don't allow")
    for _ in range(3):
        hit = tap(*deny_ids)
        print(" denied via:", hit)
        time.sleep(2)
        if not hit:
            break
    shot("82_denied")
    print("after deny:", [t for t in texts() if "권한" in t or "운행" in t])
    print("trips db exists:", "motion-care-trips.db" in shell(f"run-as {PKG} --user 10 ls databases 2>&1"))

    print("\n--- allow")
    print("tap start:", tap("운행 기록 시작"))
    time.sleep(3)
    print("dialog:", texts()[:8])
    allow_ids = ("com.android.permissioncontroller:id/permission_allow_button",
                 "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
                 "허용", "앱 사용 중에만 허용", "Allow")
    for _ in range(3):
        hit = tap(*allow_ids)
        print(" allowed via:", hit)
        time.sleep(2)
        if not hit:
            break
    time.sleep(2)
    shot("83_recording")
    print("recording screen:", [t for t in texts() if "운행" in t])
    print("service:", service_state())
    print("grants:", re.findall(r"(android\.car\.permission\.CAR_SPEED|android\.permission\.POST_NOTIFICATIONS): granted=(\w+)", shell(f"dumpsys package {PKG}")))

    print("\n--- drive behind navigation")
    shell("am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1")
    time.sleep(3)
    http("PUT", "/vehicle/gear?gear=Drive")
    profile = []
    profile += [(v, 0) for v in range(0, 61, 2)]    # steady acceleration
    profile += [(60, 0)] * 20                       # cruise
    profile += [(v, 0) for v in (60, 55, 50, 45, 40, 30, 20, 10, 0, 0, 0)]  # stop
    for _ in range(4):
        profile += [(v, 0) for v in (0, 10, 20, 30, 20, 10, 0)]  # stop and go
    profile += [(v, 0) for v in range(0, 41, 5)]    # gentle start for the steering part
    profile += [(40, 0)] * 4
    profile += [(40, 60)] * 5 + [(40, 0)] * 5       # a fast turn
    for _ in range(3):
        profile += [(40, 30)] * 3 + [(40, -30)] * 3  # left and right
    profile += [(40, 0)] * 3
    profile += [(v, 0) for v in range(40, -1, -5)]  # gentle stop
    card_stop = threading.Event()
    card = CardWatch(card_stop)
    card.start()
    started = time.time()
    for speed, steering in profile:
        http("PUT", f"/vehicle/controls?speed={speed}&accelerator={15 if speed else 0}&brake=0&steering={steering}")
        time.sleep(1)
    print(f"drove {time.time() - started:.0f}s, top 60 km/h, steps {len(profile)}")
    time.sleep(16)
    card_stop.set()
    if card.first:
        print(f"card first seen {card.first - started:.0f}s after driving began, active for about {card.last - card.first:.0f}s: {card.text}")
    else:
        print("card never seen")
    shot("84_nav_driving")
    print("service mid-drive:", service_state())
    print("top activity:", re.findall(r"topResumedActivity=\S+ \S+ (\S+)", shell("dumpsys activity activities"))[:1])

    print("\n--- finish while parked")
    http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    http("PUT", "/vehicle/gear?gear=Park")
    time.sleep(3)
    app_screen()
    shot("85_back_in_app")
    print("tap finish:", tap("운행 기록 종료"))
    time.sleep(3)
    print("service after finish:", service_state())
    shot("86_finished")
    print("finished:", [t for t in texts() if "저장" in t or "상세" in t])
    print("tap detail:", tap("주행 상세"))
    time.sleep(3)
    shot("87_detail")
    print("detail:", [t for t in texts() if "출처" in t or "샘플" in t or "패턴" in t or "좌우" in t or "안내" in t][:5])
    time.sleep(1)

    print("\n--- saved data")
    local = OUT / "trips.db"
    for suffix in ("", "-wal", "-shm"):
        data = subprocess.run(ADB + ["exec-out", f"run-as {PKG} --user 10 cat databases/motion-care-trips.db{suffix}"], capture_output=True, timeout=60).stdout
        target = pathlib.Path(str(local) + suffix)
        if data and not data.startswith(b"cat:"):
            target.write_bytes(data)
        elif target.exists():
            target.unlink()
    db = sqlite3.connect(local)
    for row in db.execute("SELECT status, origin, revision, ended - started FROM trips"):
        print("trip:", row)
    rows = db.execute("SELECT elapsed, speed, quality FROM speed_samples ORDER BY elapsed").fetchall()
    print("samples:", len(rows))
    if rows:
        gaps = [b[0] - a[0] for a, b in zip(rows, rows[1:])]
        speeds = [r[1] for r in rows if r[1] is not None]
        print("first/last elapsed ms:", rows[0][0], rows[-1][0])
        print("rate per s:", round(len(rows) / (rows[-1][0] / 1000), 1))
        print("gap ms median/p95/max:", statistics.median(gaps), sorted(gaps)[int(len(gaps) * .95)], max(gaps))
        print("speed km/h min/max:", round(min(speeds) * 3.6, 1), round(max(speeds) * 3.6, 1))
        print("quality:", {q: sum(1 for r in rows if r[2] == q) for q in {r[2] for r in rows}})
        print("strictly increasing:", all(g > 0 for g in gaps))
    steering = db.execute("SELECT elapsed, degrees FROM steering_samples ORDER BY elapsed").fetchall()
    print("steering samples:", len(steering))
    if steering:
        print("steering degrees min/max:", min(r[1] for r in steering), max(r[1] for r in steering))
    for row in db.execute("SELECT pattern, start_ms, end_ms, round(peak_mps2, 2), detector_version FROM observations ORDER BY start_ms"):
        print("observation:", row)
    for row in db.execute("SELECT elapsed, observed, pattern, outcome FROM guidance_events ORDER BY elapsed, rowid"):
        print("guidance:", row)
    print("screenshots:", OUT)
    stop.set()
    adb("forward", "--remove", f"tcp:{PORT}")


if __name__ == "__main__":
    main()
