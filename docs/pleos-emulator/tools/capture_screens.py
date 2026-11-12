"""Captures the app's screens on the Pleos emulator for a screen gallery (docs/screen-gallery.md).

usage: ANDROID_SERIAL=emulator-5600 python3 docs/pleos-emulator/tools/capture_screens.py <out_dir> [static|drive]

static (default) walks home, seats, records, a trip, the rating form, the profile and the advice with the app's own
data, saving nothing: switches are turned back and the rating is left with the back key. drive starts a short trip
with the virtual car, so it adds a trip to the records; run film_takes.py restore afterwards when the filming
history must stay as it was. Each screen is saved as <out_dir>/<name>.jpg, cropped to the app window. Needs ffmpeg.
"""
import os
import re
import subprocess
import sys
import threading
import time

# Checked before recording_e2e is imported: importing it picks the device and opens a port forward.
if len(sys.argv) < 2 or (sys.argv[2:] and sys.argv[2] not in ("static", "drive")):
    sys.exit(__doc__)
OUT = sys.argv[1]
MODE = sys.argv[2] if len(sys.argv) > 2 else "static"

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import recording_e2e as e2e  # noqa: E402

MAIN = f"{e2e.PKG}/dev.motioncare.app.MainActivity"
# The landscape window an app gets when it uses the whole app area, inside the 2560x1440 display (x, y, w, h).
APP_WINDOW = (853, 90, 1682, 1210)


def shot(name):
    png = os.path.join(OUT, f"{name}.png")
    with open(png, "wb") as f:
        f.write(subprocess.run(e2e.ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
    x, y, w, h = APP_WINDOW
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", png, "-vf", f"crop={w}:{h}:{x}:{y}", "-q:v", "3",
                    os.path.join(OUT, f"{name}.jpg")], check=True)
    os.remove(png)
    print("shot", name, flush=True)


def find(text, contains=False):
    for n in e2e.nodes():
        t, d = n.get("text") or "", n.get("content-desc") or ""
        if (text in t or text in d) if contains else (t == text or d == text):
            return n
    return None


def tap_node(n, wait=3.0):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    e2e.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    time.sleep(wait)


def tap(text, contains=False, wait=3.0):
    n = find(text, contains)
    if n is None:
        print("missing", text, flush=True)
        return False
    tap_node(n, wait)
    return True


def back(wait=2.5):
    e2e.shell("input keyevent 4")
    time.sleep(wait)


def swipe():
    e2e.shell("input swipe 1700 1150 1700 420 600")
    time.sleep(2)


def wait_for(text, timeout=20.0):
    end = time.time() + timeout
    while time.time() < end:
        if find(text) is not None:
            return True
        time.sleep(1)
    print("missing", text, flush=True)
    return False


def launch():
    e2e.shell(f"am start --user 10 -S -n {MAIN} >/dev/null 2>&1")
    # Right after a start the home leaves the advice slot empty until the advice is ready.
    time.sleep(6)
    wait_for("왜 이 조언인가요?")
    time.sleep(1)


def static():
    launch()
    shot("01_home")
    # The setting switches are turned back right after each shot.
    e2e.tap_switch("주행 중 안내"); time.sleep(2); shot("02_home_live_off"); e2e.tap_switch("주행 중 안내"); time.sleep(2)
    e2e.tap_switch("출발 안내"); time.sleep(2); shot("03_home_departure_off"); e2e.tap_switch("출발 안내"); time.sleep(2)
    if tap("좌석", wait=4):
        shot("04_seats")
        seat = find("운전석", contains=True)
        if seat is not None:
            tap_node(seat, 2); shot("05_seat_popup"); back()
        back()
    launch()
    if tap("기록", wait=4):
        shot("07_records")
        chip = find("서아")
        if chip is not None:
            tap_node(chip, 2); shot("08_records_filtered"); tap("전체", wait=2)
        rows = [n for n in e2e.nodes() if re.match(r"(오전|오후) \d+:\d+", n.get("text") or "")]
        if rows:
            tap_node(rows[0], 5)
            # Two screens reach the end of a trip's detail.
            shot("09_detail_1"); swipe(); shot("10_detail_2")
            if tap("동승자 평가", wait=4):
                shot("13_feedback")
                star = find("4점")
                if star is not None:
                    tap_node(star, 1)
                tap("출발·정차", wait=1); shot("14_feedback_chosen")
                back()  # leaves the form without saving
    launch()
    if tap("프로필", wait=4):
        shot("15_profile")
        # The tabs are at the top, so the passenger tab comes before scrolling.
        if tap("동승할 때", wait=3):
            shot("18_profile_passenger")
            tap("운전할 때", wait=3)
        swipe(); shot("16_profile_2")
    launch()
    if tap("왜 이 조언인가요?", wait=4):
        shot("19_advice")
        if tap("실제 횟수와 계산 방법", wait=3):
            shot("19b_advice_calc")
        swipe(); shot("20_advice_2")
    launch()


def drive():
    launch()
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    for v in (10, 20, 30, 40):
        e2e.http("PUT", f"/vehicle/controls?speed={v}&accelerator=15&brake=0&steering=0")
        time.sleep(1)
    time.sleep(8)
    e2e.app_screen()
    time.sleep(3)
    shot("21_driving")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0"); time.sleep(2)
    e2e.http("PUT", "/vehicle/gear?gear=Park"); time.sleep(2)
    if tap("운행 기록 종료", wait=6):
        shot("22_finished")
        if tap("이동 평가", wait=4):
            shot("23_finished_feedback")
            back()
    print("drive added a trip; run film_takes.py restore to bring back the filming history", flush=True)


def main():
    os.makedirs(OUT, exist_ok=True)
    print("device", e2e.ADB[-1], "avd", (e2e.adb("emu", "avd", "name").split() or ["?"])[0], flush=True)
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    try:
        static() if MODE == "static" else drive()
    finally:
        stop.set()
        e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")


if __name__ == "__main__":
    main()
