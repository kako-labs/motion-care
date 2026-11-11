#!/usr/bin/env python3
"""Drives the Pleos emulator through the filming takes the same way every time.

    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/film_takes.py check     # is the emulator ready?
    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/film_takes.py pattern   # card over the navigation
    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/film_takes.py air       # cabin air card
    ANDROID_SERIAL=<device> python3 docs/pleos-emulator/tools/film_takes.py restore   # filming history back

Start the screen recording first. Each step waits for Enter so the shot can be framed; --no-wait runs straight
through. Close the Virtual Console page in the browser: while it is open it sends its own speed. Every take records
a trip, so run `restore` (demo_history.py) when filming is done.

pattern: recirculation on, so the cabin air card cannot take the 15 minute slot all cards share. The app keeps the
climate it last read and hands it to a new trip at once, so the app is restarted and the home is checked to show
내기 순환 켜짐 before setting off; without that the air card took the slot in the first test. Gear D starts the
trip, the navigation opens, and three stops from 40 km/h at about 2.8 m/s², over PatternDetector's 2.0 m/s² hard stop
line, make the third a repeated pattern (GuidancePolicy: three within ten minutes). The card shows once the car has
been still for three seconds. Speeding up stays at 2 km/h a second (0.56 m/s²), well under the 0.9 m/s² swing line
even when a console request arrives late, so no other pattern competes for the card.
air: recirculation off. When outdoor air is 나쁨 or worse the cabin air card shows a few seconds after the start.
Both takes end parked in P with the app in front, ready for tapping 운행 기록 종료 and the rating on camera.
"""
import argparse
import json
import math
import re
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

import recording_e2e as e2e

NAVIGATION = "ai.umos.maps.android.navigation.app/.MainActivity"
LATITUDE, LONGITUDE = 37.6, 127.0  # where demo_data.py puts the emulator, rounded the way the app asks Open-Meteo
CARD = re.compile(r"android\.title=String \(모션케어\)[\s\S]*?android\.text=String \(([^)]*)\)")


def console(path, attempts=4):
    """The Virtual Console sometimes drops one request; a dropped step is sent again."""
    for attempt in range(attempts):
        try:
            return e2e.http("PUT", path)
        except OSError:
            if attempt == attempts - 1:
                raise
            time.sleep(0.5)


def speed(kmh):
    moving = kmh > 0
    console(f"/vehicle/controls?speed={kmh}&accelerator={12 if moving else 0}&brake={0 if moving else 30}&steering=0")


def ramp(start, end, per_second):
    """Changes speed by whole km/h at a steady rate, as the console takes integers."""
    step = 1 if end > start else -1
    pause = 1 / per_second
    for kmh in range(start + step, end + step, step):
        speed(kmh)
        time.sleep(pause)


def hold(seconds, kmh):
    end = time.time() + seconds
    while time.time() < end:
        speed(kmh)
        time.sleep(1)


def active_notifications():
    return e2e.shell("dumpsys notification --noredact").split("mArchive=")[0]


def recording():
    return "android.title=String (운행 기록 중)" in active_notifications()


def card():
    found = CARD.search(active_notifications())
    return found.group(1) if found else None


def wait_for(check, limit):
    started = time.time()
    while time.time() - started < limit:
        result = check()
        if result:
            return result
        time.sleep(0.5)
    return None


def step(wait, message):
    print(f"\n▶ {message}", flush=True)
    if wait:
        input("  Enter를 누르면 진행합니다. ")


def outdoor_grade():
    """The outdoor air grade the app would show now, from the same Open-Meteo request and the same table."""
    url = (f"https://air-quality-api.open-meteo.com/v1/air-quality?latitude={LATITUDE}&longitude={LONGITUDE}"
           "&current=pm10,pm2_5&domains=cams_global")
    with urllib.request.urlopen(url, timeout=20) as response:
        current = json.load(response)["current"]
    steps = []
    for value, bounds in ((current.get("pm10"), (30, 80, 150)), (current.get("pm2_5"), (15, 35, 75))):
        if value is not None:
            rounded = math.floor(value + 0.5)
            steps.append(next((i for i, bound in enumerate(bounds) if rounded <= bound), len(bounds)))
    grade = ("좋음", "보통", "나쁨", "매우 나쁨")[max(steps)] if steps else None
    return grade, current.get("pm10"), current.get("pm2_5")


def gear(name):
    console(f"/vehicle/gear?gear={name}")


def start_trip():
    gear("Drive")
    if not wait_for(recording, 20):
        sys.exit("기어 D로 운행 기록이 시작되지 않았습니다. 운행 자동 기록 스위치를 확인하세요.")
    print("  운행 기록이 시작됐습니다.", flush=True)


def park():
    speed(0)
    gear("Park")
    time.sleep(2)
    e2e.app_screen()
    print("  P에 두고 앱 홈으로 돌아왔습니다. 화면의 '운행 기록 종료'를 누르면 '운행을 마쳤어요'와 이동 평가로 이어집니다.",
          flush=True)


def cabin_air(recirculating):
    """Sets the intake and restarts the app so the trip starts from the new climate, then waits for the home to show it."""
    console(f"/vehicle/hvac?power=1&recirc={1 if recirculating else 0}&driver_temp=23")
    time.sleep(2)
    e2e.shell(f"am start --user 10 -S -n {e2e.PKG}/dev.motioncare.app.MainActivity >/dev/null 2>&1")
    expected = "켜짐" if recirculating else "꺼짐"
    for _ in range(20):
        time.sleep(2)
        texts = e2e.texts()
        if "내기 순환" in texts and texts[texts.index("내기 순환") + 1:][:1] == [expected]:
            print(f"  홈 환경 카드: 내기 순환 {expected}", flush=True)
            return
    sys.exit(f"홈에 '내기 순환 {expected}'이 보이지 않습니다. 차 공조 상태를 확인하세요.")


def ready():
    """Parked, still, and nothing left from an earlier take."""
    gear("Park")
    speed(0)
    if recording():
        sys.exit("앞 테이크의 운행이 아직 기록 중입니다. 앱 홈에서 '운행 기록 종료'를 누른 뒤 다시 실행하세요.")


def take_pattern(wait):
    ready()
    cabin_air(True)
    step(wait, "홈 화면입니다. 녹화를 시작했으면 기어를 D로 바꿉니다(홈 큰 카드가 '운행 기록 중'으로 바뀜).")
    start_trip()
    step(wait, "내비를 열고 출발합니다. 40km/h에서 세게 서기를 세 번 합니다(약 2분).")
    e2e.shell(f"am start --user 10 -n {NAVIGATION} >/dev/null 2>&1")
    time.sleep(3)
    for stop in range(3):
        ramp(0, 40, 2)
        hold(6, 40)
        ramp(40, 0, 10)
        print(f"  {stop + 1}번째 정차", flush=True)
        hold(5 if stop < 2 else 1, 0)
    shown = wait_for(card, 15)
    print(f"  내비 위 카드: {shown}" if shown else "  카드가 15초 안에 뜨지 않았습니다.", flush=True)
    hold(10, 0)
    step(wait, "조금 더 달린 뒤 천천히 서서 P로 바꿉니다.")
    ramp(0, 30, 3)
    hold(6, 30)
    ramp(30, 0, 3)
    park()


def take_air(wait):
    ready()
    grade, pm10, pm25 = outdoor_grade()
    print(f"지금 바깥 대기질: {grade} (PM10 {pm10}, PM2.5 {pm25})", flush=True)
    if grade not in ("나쁨", "매우 나쁨"):
        print("  '나쁨' 이상이 아니라 공기 카드가 뜨지 않습니다. 대기질이 나쁠 때 다시 찍으세요.", flush=True)
        return
    cabin_air(False)
    e2e.shell(f"am start --user 10 -n {NAVIGATION} >/dev/null 2>&1")
    step(wait, "내비 화면입니다. 녹화를 시작했으면 기어를 D로 바꾸고 천천히 출발합니다(외기 유입 + 나쁜 대기질).")
    start_trip()
    ramp(0, 30, 3)
    shown = wait_for(card, 30)
    print(f"  내비 위 카드: {shown}" if shown else "  카드가 30초 안에 뜨지 않았습니다.", flush=True)
    hold(10, 30)
    step(wait, "천천히 서서 P로 바꿉니다.")
    ramp(30, 0, 3)
    park()


def check():
    print("기기:", e2e.ADB[-1], "AVD:", e2e.adb("emu", "avd", "name").split()[0])
    prefs = {name: e2e.shell(f"run-as {e2e.PKG} --user 10 cat shared_prefs/{name}.xml")
             for name in ("motion-care-auto-record", "motion-care-guidance", "motion-care-profile-settings")}
    on = lambda text: 'value="true"' in text  # noqa: E731
    print("운행 자동 기록:", "켜짐" if on(prefs["motion-care-auto-record"]) else "꺼짐")
    print("주행 중 안내:", "켜짐" if on(prefs["motion-care-guidance"]) else "꺼짐")
    print("출발 안내:", "켜짐" if on(prefs["motion-care-profile-settings"]) else "꺼짐")
    state = json.loads(urllib.request.urlopen(e2e.BASE + "/vehicle/state", timeout=5).read())
    print("기어:", state["gear"]["actual"]["value"])
    print("기록 중인 운행:", "있음" if recording() else "없음")
    grade, pm10, pm25 = outdoor_grade()
    print(f"바깥 대기질(공기 카드 조건 '나쁨' 이상): {grade} (PM10 {pm10}, PM2.5 {pm25})")


def restore():
    tools = Path(__file__).resolve().parent
    subprocess.run([sys.executable, str(tools / "demo_history.py")], check=True, cwd=tools)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("take", choices=("check", "pattern", "air", "restore"))
    parser.add_argument("--no-wait", action="store_true", help="run every step without waiting for Enter")
    args = parser.parse_args()
    if args.take == "restore":
        e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
        return restore()
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    try:
        {"check": check, "pattern": lambda: take_pattern(not args.no_wait),
         "air": lambda: take_air(not args.no_wait)}[args.take]()
    finally:
        stop.set()
        e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")


if __name__ == "__main__":
    main()
