#!/usr/bin/env python3
"""End-to-end check of environment values on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/environment_e2e.py

Needs internet access from the emulator for Open-Meteo. Set ANDROID_SERIAL when more than one device is
attached. This script clears the app's data and changes the car's position and climate, so it must never run
against an emulator that belongs to other work. It checks what the app stores, not whether the values are true.
"""
import pathlib
import re
import sqlite3
import time

import recording_e2e as e2e

SEOUL = (126.978, 37.5665)  # emu geo fix takes longitude first
LOCATION_DIALOG = "알 수 없는 작업"


def hvac(**fields):
    query = "&".join(f"{k}={v}" for k, v in fields.items())
    return e2e.http("PUT", f"/vehicle/hvac?{query}")


def answer_permissions(deny_location):
    """Answers each dialog in turn. The location permission has no label, so the system calls it an unknown task."""
    seen = []
    for _ in range(4):
        time.sleep(2)
        dialog = [t for t in e2e.texts() if "허용하시겠습니까" in t]
        if not dialog:
            break
        seen.append(dialog[0])
        if deny_location and LOCATION_DIALOG in dialog[0]:
            e2e.tap("com.android.permissioncontroller:id/permission_deny_button", "허용 안함", "허용 안 함")
        else:
            e2e.tap("com.android.permissioncontroller:id/permission_allow_button",
                    "com.android.permissioncontroller:id/permission_allow_foreground_only_button", "허용", "앱 사용 중에만 허용")
    return seen


def pull_database():
    local = e2e.OUT / "environment.db"
    for suffix in ("", "-wal", "-shm"):
        data = e2e.subprocess.run(
            e2e.ADB + ["exec-out", f"run-as {e2e.PKG} --user 10 cat databases/motion-care-environment.db{suffix}"],
            capture_output=True, timeout=60).stdout
        target = pathlib.Path(str(local) + suffix)
        if data and not data.startswith(b"cat:"):
            target.write_bytes(data)
        elif target.exists():
            target.unlink()
    db = sqlite3.connect(local)
    db.row_factory = sqlite3.Row
    return [dict(row) for row in db.execute("SELECT * FROM snapshots ORDER BY captured_at, id")]


def describe(row):
    parts = []
    for name in ("outside", "cabin", "cabin_set", "recirculation", "air_cleaner"):
        value = row[f"{name}_value"]
        parts.append(f"{name}={value if value is not None else '-'}({row[f'{name}_state']}{'/' + row[f'{name}_reason'] if row[f'{name}_reason'] else ''})")
    air = f"pm10={row['air_pm10']} pm25={row['air_pm25']} grade={row['air_grade']} at=({row['air_latitude']},{row['air_longitude']})"
    parts.append(f"air={air}({row['air_state']}{'/' + row['air_reason'] if row['air_reason'] else ''}, {row['air_source']})")
    return " ".join(parts)


def drive(deny_location, between=None):
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    e2e.app_screen()
    print("tap start:", e2e.tap("운행 시작"))
    print("dialogs:", answer_permissions(deny_location))
    time.sleep(10)
    print("service:", e2e.service_state())
    if between:
        between()
        time.sleep(4)
    print("tap finish:", e2e.tap("운행 기록 종료"))
    time.sleep(6)
    rows = pull_database()
    for row in rows:
        print("snapshot:", describe(row))
    return rows


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0], "console port:", e2e.PORT)
    stop = e2e.threading.Event()
    e2e.threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    print("position:", e2e.adb("emu", "geo", "fix", *map(str, SEOUL)).strip())
    print("gear park:", e2e.http("PUT", "/vehicle/gear?gear=Park"))
    print("climate on, recirculation on, driver 24 °C:", hvac(power=1, recirc=1, driver_temp=24))
    results = {}

    print("\n===== allowed =====", flush=True)
    rows = drive(False, between=lambda: print("recirculation off, driver 20 °C:", hvac(recirc=0, driver_temp=20)))
    first, last = (rows[0], rows[-1]) if rows else ({}, {})
    results["start and end snapshots stored"] = len(rows) == 2
    results["vehicle values read"] = first.get("outside_state") == "AVAILABLE" and first.get("cabin_state") == "AVAILABLE"
    results["climate changes followed"] = (first.get("recirculation_value"), last.get("recirculation_value")) == (1.0, 0.0) \
        and (first.get("cabin_set_value"), last.get("cabin_set_value")) == (24.0, 20.0)
    results["outdoor air for the rounded position"] = first.get("air_state") == "AVAILABLE" \
        and (first.get("air_latitude"), first.get("air_longitude")) == (37.6, 127.0)

    print("\n===== location denied =====", flush=True)
    rows = drive(True)
    results["denied location leaves only outdoor air out"] = bool(rows) and all(
        r["air_state"] == "UNAVAILABLE" and r["air_reason"] == "NO_PERMISSION" and r["outside_state"] == "AVAILABLE" for r in rows)

    print("\n===== summary =====")
    for name, ok in results.items():
        print(("PASS" if ok else "FAIL"), name)
    print("files:", e2e.OUT)
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")


if __name__ == "__main__":
    main()
