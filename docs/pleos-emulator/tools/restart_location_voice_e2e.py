#!/usr/bin/env python3
"""End-to-end check that location and the card's voice still work after a car service restart on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/restart_location_voice_e2e.py      # about 3 minutes

Chooses the voice, starts a trip in Seoul with recirculation on so no cabin air card takes the shared interval, drives
at 30 km/h with navigation in front and crashes the system car service with `am crash`. Right after the restart it
moves the car to Busan and repeats hard stops until a pattern card speaks. Passes when the car service restarted, the
app survived, the card after the restart was shown and spoken by the speech service, and the outdoor air stored when
the trip ends is for Busan, which only a location read after the restart can give: the air cache is kept per place.
Needs internet access from the emulator for Open-Meteo. Whether the location and speech service processes survive the
restart is printed but not judged. Set ANDROID_SERIAL when more than one device is attached. This script clears the
app's data and restarts the car service, which also restarts other car apps, so it must never run against an emulator
that belongs to other work.
"""
import re
import sys
import threading
import time

import car_service_restart_e2e as restart
import environment_card_e2e as env
import recording_e2e as e2e
import sound_e2e as sound

BUSAN = (129.0756, 35.1796)  # emu geo fix takes longitude first
BEFORE_S = 20
LOCATION_SERVICE = "ai.pleos.playground.service.location.fused"


def user_pid(name):
    """The process of the app's user; the system user runs its own copy of these services."""
    for line in e2e.shell("ps -A -o USER,PID,NAME").splitlines():
        parts = line.split()
        if len(parts) == 3 and parts[0].startswith("u10_") and parts[2] == name:
            return parts[1]
    return None


def processes():
    return {"app": restart.app_pid(), "car service": restart.car_service_pid(),
            "location service": user_pid(LOCATION_SERVICE), "speech service": user_pid(sound.SPEECH_SERVICE)}


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
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, restart.SEOUL)).strip())
    print("climate on, recirculation on:", env.hvac(power=1, recirc=1))
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    restart.control(0, accelerator=0)
    e2e.app_screen()
    print("tap:", e2e.tap("음성"))
    time.sleep(1)
    print("auto record:", e2e.auto_record_on())
    time.sleep(3)
    print("service:", e2e.service_state())
    e2e.shell(f"am start --user 10 -n {env.NAVIGATION} >/dev/null 2>&1")
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    for speed in range(0, 31, 5):
        restart.control(speed)
        time.sleep(1)
    restart.drive(BEFORE_S, steering=0)

    before = processes()
    print("before crash:", before)
    e2e.adb("logcat", "-c")
    print("crash:", e2e.shell(f"am crash {before['car service']}").strip() or "sent")
    time.sleep(5)
    died = re.findall(r"ActivityManager: Process (\S+) \(pid (\d+)\) has died", e2e.adb("logcat", "-d"))
    print("processes that died at the restart:", died)
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, BUSAN)).strip())
    e2e.adb("logcat", "-c")
    watch = sound.PlaybackWatch()
    watch.start()
    sound.drive_until_card()
    watch.stop_event.set()
    after = processes()
    print("after crash:", after)
    log = sound.sound_log()
    requests = sound.speech_requests()
    card = sound.card_text()
    speech_usages = watch.owned_by(sound.SPEECH_SERVICE)
    print("sound log:", log)
    print("speech requests:", requests)
    print("usages from the speech service:", speech_usages)
    print("card text seen in the notification list:", card[:1])

    survived = after["app"] is not None and after["app"] == before["app"]
    if survived:
        e2e.app_screen()
        print("tap finish:", e2e.tap("운행 기록 종료"))
        time.sleep(6)
    trips = env.pull("motion-care-trips.db", "SELECT id, status FROM trips")
    events = env.pull("motion-care-trips.db", "SELECT elapsed, pattern, outcome FROM guidance_events ORDER BY elapsed")
    # Speed is marked unavailable once when the car service goes away, which places the restart in trip time.
    lost = env.pull("motion-care-trips.db", "SELECT elapsed FROM speed_samples WHERE quality != 'VALID' ORDER BY elapsed")
    marker = lost[0]["elapsed"] if lost else None
    shown = [e for e in events if e["outcome"] == "SHOWN"]
    air = env.pull("motion-care-environment.db",
                   "SELECT captured_at, air_state, air_reason, air_latitude, air_longitude FROM snapshots ORDER BY captured_at, id")
    print("trips:", trips)
    print("restart at trip ms:", marker)
    print("guidance events:", [(e["elapsed"], e["pattern"], e["outcome"]) for e in events])
    print("outdoor air snapshots:", [(a["air_state"], a["air_reason"], a["air_latitude"], a["air_longitude"]) for a in air])

    checks = [
        ("the car service restarted", before["car service"] is not None and after["car service"] not in (None, before["car service"])),
        ("the app process survived", survived),
        ("a card was shown after the restart",
         len(shown) == 1 and marker is not None and shown[0]["elapsed"] > marker and bool(card)),
        ("the card's voice started", any("started: VOICE" in line for line in log)),
        ("the speech service spoke for the app", e2e.PKG in requests and "USAGE_NOTIFICATION" in speech_usages),
        ("the trip finished normally", [t["status"] for t in trips] == ["COMPLETED"]),
        ("outdoor air at the start was for Seoul",
         bool(air) and (air[0]["air_latitude"], air[0]["air_longitude"]) == (37.6, 127.0)),
        ("outdoor air at the end was for Busan, read after the restart",
         len(air) >= 2 and air[-1]["air_state"] == "AVAILABLE" and (air[-1]["air_latitude"], air[-1]["air_longitude"]) == (35.2, 129.1)),
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
