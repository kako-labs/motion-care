#!/usr/bin/env python3
"""Fills the app on the Pleos emulator with a believable history for filming the demo.

Build the debug APK and start the emulator first, then run:

    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/demo_data.py     # about 25 minutes

Trips are not written into the database. The script drives the emulator's vehicle through the Virtual Console with
automatic recording on, so every trip, pattern, card and environment value is what the app itself recorded from the
emulated car. Only what a person would type is put straight into the profile database, because the emulator keyboard
cannot type Korean: four people with characters, who sits where, and the passengers' answers after each trip except
the last, which is left for answering on camera. The driver's first trips are rough and later ones gentle, so the
departure advice has a trend to compare. The trips all fall on the day of the run.

This script clears the app's data and drives the vehicle signals, so it must never run against an emulator that
belongs to other work. Air quality needs internet in the emulator; without it the cards say so.
"""
import pathlib
import sqlite3
import subprocess
import sys
import tempfile
import threading
import time

import recording_e2e as e2e

PROFILE_DB = "databases/motion-care-profile.db"
TRIPS_DB = "databases/motion-care-trips.db"
WORK = pathlib.Path(tempfile.mkdtemp(prefix="demo-data-"))
SEOUL = ("126.978", "37.5665")  # emu geo fix takes longitude first

# Fictional people. Character keys are the ones in feature/history's Characters.kt.
PEOPLE = [
    ("jiyoung", "지영", "ADULT", "UNSPECIFIED", "", "character_04"),
    ("minjun", "민준", "ADULT", "HIGH", "START_STOP,REPEATED_SPEED_CHANGE", "character_12"),
    ("seoa", "서아", "CHILD", "MEDIUM", "CURVE", "character_19"),
    ("doyun", "도윤", "CHILD", "UNSPECIFIED", "", "character_22"),
]
FAMILY_DRIVES = {"DRIVER": "jiyoung", "FRONT_PASSENGER": "minjun", "REAR_RIGHT": "seoa"}
MINJUN_DRIVES = {"DRIVER": "minjun", "FRONT_PASSENGER": "jiyoung", "REAR_RIGHT": "seoa"}


def rough(cycles=6):
    """Stop and go at 10 km/h per second (about 2.8 m/s²), then left and right swaying."""
    steps = []
    for _ in range(cycles):
        steps += [(v, 0) for v in (0, 10, 20, 30, 40)] + [(40, 0)] * 6 + [(v, 0) for v in (30, 20, 10, 0)] + [(0, 0)] * 3
    steps += [(v, 0) for v in range(0, 41, 8)] + [(40, 0)] * 2
    for _ in range(3):
        steps += [(40, 30)] * 3 + [(40, -30)] * 3
    steps += [(40, 0)] * 3 + [(v, 0) for v in range(40, -1, -4)] + [(0, 0)] * 2
    return steps


def gentle(hard_stops=0):
    """Three legs speeding up and slowing down at 3 km/h per second (about 0.8 m/s²); [hard_stops] legs end hard."""
    steps = []
    for leg in range(3):
        steps += [(v, 0) for v in range(0, 41, 3)] + [(40, 0)] * 16
        steps += [(v, 0) for v in (30, 20, 10, 0)] if leg < hard_stops else [(v, 0) for v in range(40, -1, -3)] + [(0, 0)]
        steps += [(0, 0)] * 3
    return steps


def curves():
    """City driving with two fast turns and gentle stops."""
    steps = [(v, 0) for v in range(0, 37, 3)] + [(36, 0)] * 8
    for turn in (60, -60):
        steps += [(30, turn)] * 5 + [(30, 0)] * 10
    steps += [(v, 0) for v in range(36, -1, -3)] + [(0, 0)] * 2
    steps += [(v, 0) for v in range(0, 37, 3)] + [(36, 0)] * 20 + [(v, 0) for v in range(36, -1, -3)] + [(0, 0)] * 2
    return steps


# (label, seating, profile, hvac): the climate differs so the trip details show different cabins.
TRIPS = [
    ("민준 운전, 회전", MINJUN_DRIVES, curves(), {"power": 1, "recirc": 0, "driver_temp": 23}),
    ("지영 1, 거침", FAMILY_DRIVES, rough(), {"power": 1, "recirc": 1, "driver_temp": 26}),
    ("지영 2, 거침", FAMILY_DRIVES, rough(), {"power": 1, "recirc": 1, "driver_temp": 25}),
    ("지영 3, 거침", FAMILY_DRIVES, rough(5), {"power": 1, "recirc": 1, "driver_temp": 24}),
    ("지영 4, 나아짐", FAMILY_DRIVES, gentle(2), {"power": 1, "recirc": 0, "driver_temp": 23}),
    ("지영 5, 부드러움", FAMILY_DRIVES, gentle(1), {"power": 1, "recirc": 0, "driver_temp": 22}),
    ("지영 6, 부드러움", FAMILY_DRIVES, gentle(0), {"power": 1, "recirc": 0, "driver_temp": 22}),
]

# Passengers' answers per trip in TRIPS order: person -> (rating 1..5, reasons, respondent). The last trip is left open.
ANSWERS = [
    {"jiyoung": (4, "", "SELF"), "seoa": (3, "CURVE", "GUARDIAN")},
    {"minjun": (2, "START_STOP,REPEATED_SPEED_CHANGE", "SELF"), "seoa": (2, "START_STOP", "GUARDIAN")},
    {"minjun": (2, "START_STOP,STUFFY_AIR", "SELF"), "seoa": (3, "FELT_HOT", "GUARDIAN")},
    {"minjun": (3, "REPEATED_SPEED_CHANGE", "SELF"), "seoa": (2, "START_STOP", "GUARDIAN")},
    {"minjun": (4, "", "SELF"), "seoa": (3, "START_STOP", "GUARDIAN")},
    {"minjun": (5, "", "SELF"), "seoa": (4, "", "GUARDIAN")},
    {},
]


def console(path, attempts=4):
    """The Virtual Console sometimes drops one request during a long drive; a dropped step is sent again."""
    for attempt in range(attempts):
        try:
            return e2e.http("PUT", path)
        except OSError:
            if attempt == attempts - 1:
                raise
            time.sleep(0.5)


def run_as(cmd):
    return e2e.shell(f"run-as {e2e.PKG} --user 10 {cmd}")


def pull(db):
    """Copies a database and its WAL to the work folder and returns the local path."""
    local = WORK / pathlib.Path(db).name
    for suffix in ("", "-wal", "-shm"):
        data = subprocess.run(e2e.ADB + ["exec-out", f"run-as {e2e.PKG} --user 10 cat {db}{suffix}"],
                              capture_output=True, timeout=60).stdout
        target = pathlib.Path(str(local) + suffix)
        if data and not data.startswith(b"cat:") and not data.startswith(b"run-as:"):
            target.write_bytes(data)
        elif target.exists():
            target.unlink()
    return local


def edit_profiles(change):
    """Stops the app, applies [change] to the profile database and puts it back."""
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    local = pull(PROFILE_DB)
    con = sqlite3.connect(local)
    change(con)
    con.commit()
    con.close()  # The last connection checkpoints the WAL into the main file.
    for suffix in ("-wal", "-shm", "-journal"):
        run_as(f"rm -f {PROFILE_DB}{suffix}")
    subprocess.run(e2e.ADB + ["push", str(local), "/data/local/tmp/demo-profile.db"], capture_output=True, timeout=60)
    e2e.shell("chmod 644 /data/local/tmp/demo-profile.db")
    run_as(f"cp /data/local/tmp/demo-profile.db {PROFILE_DB}")
    e2e.shell("rm -f /data/local/tmp/demo-profile.db")


def seat(plan):
    def change(con):
        con.execute("DELETE FROM current_seats")
        for position, person in plan.items():
            con.execute("INSERT INTO current_seats (seat, person_id) VALUES (?, ?)", (position, person))
    return change


def add_people(con):
    for person_id, name, kind, sensitivity, triggers, avatar in PEOPLE:
        con.execute("INSERT INTO people (id, display_name, kind, sensitivity, triggers, avatar) VALUES (?, ?, ?, ?, ?, ?)",
                    (person_id, name, kind, sensitivity, triggers, avatar))


def recording():
    active = e2e.shell("dumpsys notification --noredact").split("mArchive=")[0]
    return "android.title=String (운행 기록 중)" in active


def wait_for(check, limit, step=1.0):
    started = time.time()
    while time.time() - started < limit:
        if check():
            return True
        time.sleep(step)
    return False


def drive(label, steps, hvac):
    console("/vehicle/hvac?" + "&".join(f"{k}={v}" for k, v in hvac.items()))
    e2e.shell("am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1")
    time.sleep(3)
    console("/vehicle/gear?gear=Drive")
    if not wait_for(recording, 20):
        sys.exit(f"{label}: the drive gear did not start a trip")
    for speed, steering in steps:
        console(f"/vehicle/controls?speed={speed}&accelerator={15 if speed else 0}&brake={0 if speed else 30}"
                f"&steering={steering}")
        time.sleep(1)
    console("/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    console("/vehicle/gear?gear=Park")
    time.sleep(2)
    e2e.app_screen()
    # The home may still be reading the saved trips when it comes to the front, so the button is looked for again.
    finished = None
    for _ in range(8):
        finished = e2e.tap("운행 기록 종료")
        if finished:
            break
        time.sleep(2)
    if not finished:
        sys.exit(f"{label}: the finish button did not appear")
    time.sleep(4)
    # The finish card stays on the home screen until dismissed; the next trip should start from the advice.
    e2e.tap("완료")
    time.sleep(2)
    print(f"{label}: {len(steps)} s, finished", flush=True)


def finished_trips():
    db = sqlite3.connect(pull(TRIPS_DB))
    try:
        return [row[0] for row in db.execute("SELECT id FROM trips WHERE status = 'COMPLETED' ORDER BY started")]
    finally:
        db.close()


def add_answers(trip_ids):
    def change(con):
        for trip_id, answers in zip(trip_ids, ANSWERS):
            for person, (rating, reasons, respondent) in answers.items():
                con.execute("INSERT OR REPLACE INTO trip_feedback (trip_id, target_person_id, rating, scale_version, "
                            "reasons, respondent, created_at) VALUES (?, ?, ?, 'COMFORT_5_V1', ?, ?, ?)",
                            (trip_id, person, rating, reasons, respondent, int(time.time() * 1000)))
    edit_profiles(change)


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "work:", WORK)
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    if "--keep-install" not in sys.argv:
        print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    for permission in ("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION",
                       "android.permission.POST_NOTIFICATIONS"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {permission}")
    e2e.adb("emu", "geo", "fix", *SEOUL)
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")

    # The first launch creates the databases; then the people go in.
    e2e.app_screen()
    time.sleep(3)
    edit_profiles(lambda con: (add_people(con), seat(MINJUN_DRIVES)(con)))
    e2e.app_screen()
    time.sleep(3)
    print("auto record:", e2e.auto_record_on())

    current = None
    for label, plan, steps, hvac in TRIPS:
        if plan is not current:
            edit_profiles(seat(plan))
            e2e.app_screen()
            time.sleep(4)
            current = plan
        drive(label, steps, hvac)

    trip_ids = finished_trips()
    print("completed trips:", len(trip_ids))
    if len(trip_ids) != len(TRIPS):
        sys.exit("some trips did not finish; answers were not added")
    add_answers(trip_ids)
    e2e.app_screen()
    time.sleep(4)
    e2e.shot("demo_home")

    db = sqlite3.connect(pull(TRIPS_DB))
    for row in db.execute("SELECT t.id, round((t.ended - t.started) / 1000.0), "
                          "(SELECT count(*) FROM observations o WHERE o.trip_id = t.id), "
                          "(SELECT count(*) FROM guidance_events g WHERE g.trip_id = t.id AND g.outcome = 'SHOWN') "
                          "FROM trips t ORDER BY t.started"):
        print("trip:", row[0][:8], "seconds:", row[1], "patterns:", row[2], "cards shown:", row[3])
    db.close()
    print("screenshot:", e2e.OUT / "demo_home.png")
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")


if __name__ == "__main__":
    main()
