#!/usr/bin/env python3
"""End-to-end check of cabin air cards on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/environment_card_e2e.py           # bad outdoor air, about 3 minutes
    python3 docs/pleos-emulator/tools/environment_card_e2e.py --long    # also 33 minutes of recirculation

Needs internet access from the emulator for Open-Meteo. The outdoor air is the forecast at the time of the run, so
each scenario reads the grade the app stored and reports a mismatch as not run instead of as a pass or fail.
Set ANDROID_SERIAL when more than one device is attached. This script clears the app's data and changes the car's
position and climate, so it must never run against an emulator that belongs to other work.
"""
import pathlib
import re
import shutil
import sqlite3
import sys
import threading
import time

import recording_e2e as e2e

DELHI = (77.2, 28.6)  # emu geo fix takes longitude first
SEOUL = (126.978, 37.5665)
NAVIGATION = "ai.umos.maps.android.navigation.app/.MainActivity"
REDUCE = "외기 유입을 줄이고 공기청정 기능을 확인해보세요"
TRY = "답답함을 느낀다면 잠시 외기 모드를 사용해보세요"


def hvac(**fields):
    return e2e.http("PUT", "/vehicle/hvac?" + "&".join(f"{k}={v}" for k, v in fields.items()))


def pull(database, query):
    local = e2e.OUT / database
    for suffix in ("", "-wal", "-shm"):
        data = e2e.subprocess.run(
            e2e.ADB + ["exec-out", f"run-as {e2e.PKG} --user 10 cat databases/{database}{suffix}"],
            capture_output=True, timeout=60).stdout
        target = pathlib.Path(str(local) + suffix)
        if data and not data.startswith(b"cat:"):
            target.write_bytes(data)
        elif target.exists():
            target.unlink()
    db = sqlite3.connect(local)
    db.row_factory = sqlite3.Row
    return [dict(row) for row in db.execute(query)]


class CardTexts(threading.Thread):
    """Collects every guidance card text seen in the active notification list."""

    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()
        self.seen = []

    def run(self):
        while not self.stop_event.is_set():
            active = e2e.shell("dumpsys notification --noredact").split("mArchive=")[0]
            for text in re.findall(r"android\.text=String \(([^)]*보세요)\)", active):
                if text not in self.seen:
                    self.seen.append(text)
                    time.sleep(1)
                    e2e.shot(f"card_{len(self.seen)}")
            self.stop_event.wait(1)


def scenario(name, position, recirculating, minutes, expect):
    """Drives at a steady speed with navigation in front. [expect] decides from the stored grade and the cards seen."""
    print(f"\n===== {name} =====", flush=True)
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    for permission in ("android.permission.POST_NOTIFICATIONS", "android.car.permission.CAR_SPEED",
                       "pleos.car.permission.FUSED_LOCATION"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {permission}")
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, position)).strip())
    print("climate on, recirculation", recirculating, ":", hvac(power=1, recirc=int(recirculating)))
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")
    e2e.app_screen()
    print("tap start:", e2e.tap("운행 시작"))
    time.sleep(3)
    print("service:", e2e.service_state())
    watch = CardTexts()
    watch.start()
    e2e.shell(f"am start --user 10 -n {NAVIGATION} >/dev/null 2>&1")
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    for speed in range(0, 31, 5):
        e2e.http("PUT", f"/vehicle/controls?speed={speed}&accelerator=15&brake=0&steering=0")
        time.sleep(1)
    started = time.time()
    while time.time() - started < minutes * 60:
        e2e.http("PUT", "/vehicle/controls?speed=30&accelerator=15&brake=0&steering=0")
        time.sleep(5)
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    time.sleep(3)
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    watch.stop_event.set()
    e2e.app_screen()
    print("tap finish:", e2e.tap("운행 기록 종료"))
    time.sleep(6)
    air = pull("motion-care-environment.db", "SELECT air_grade, air_state, recirculation_value FROM snapshots ORDER BY captured_at")
    events = pull("motion-care-trips.db", "SELECT elapsed, observed, pattern, outcome FROM guidance_events ORDER BY elapsed")
    for name_, path in ((f"{name}_{i + 1}", e2e.OUT / f"card_{i + 1}.png") for i in range(len(watch.seen))):
        if path.exists():
            shutil.move(path, e2e.OUT / f"{name_}.png")
    print("stored air:", air)
    print("guidance events:", events)
    print("cards seen:", watch.seen)
    return expect(air[0] if air else {}, events, watch.seen)


def bad_air_card(air, events, seen):
    if air.get("air_grade") not in ("BAD", "VERY_BAD"):
        return None
    return seen == [REDUCE] and [(e["pattern"], e["outcome"]) for e in events] == [("REDUCE_OUTSIDE_AIR", "SHOWN")]


def no_card(air, events, seen):
    if air.get("air_grade") not in ("BAD", "VERY_BAD"):
        return None
    return not seen and not events


def recirculation_card(air, events, seen):
    if air.get("air_grade") != "GOOD":
        return None
    shown = [e for e in events if e["pattern"] == "TRY_OUTSIDE_AIR" and e["outcome"] == "SHOWN"]
    return seen == [TRY] and len(shown) == 1 and shown[0]["observed"] >= 30 * 60_000


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "console port:", e2e.PORT)
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    results = {
        "bad outdoor air with outside intake shows the card": scenario("bad_outside", DELHI, False, 1, bad_air_card),
        "bad outdoor air while recirculating shows nothing": scenario("bad_recirculating", DELHI, True, 1, no_card),
    }
    if "--long" in sys.argv:
        results["good outdoor air after 30 minutes of recirculation shows the card"] = \
            scenario("good_recirculating", SEOUL, True, 33, recirculation_card)
    print("\n===== summary =====")
    for name, ok in results.items():
        print({True: "PASS", False: "FAIL", None: "NOT RUN (air grade did not match)"}[ok], name)
    print("files:", e2e.OUT)
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if all(ok is not False for ok in results.values()) else 1)


if __name__ == "__main__":
    main()
