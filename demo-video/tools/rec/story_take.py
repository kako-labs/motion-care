"""Records the whole story of the demo in one take, in the order it is shown: open the app from the dock, the
departure screens, gear D, three hard stops over the navigation and the card, park, the automatic end after three
minutes in P, the passenger rating, the trip just driven, the driver's trend and the next departure advice.

usage: python3 story_take.py <out_dir>
Only touches the AVD "pleos" on emulator-5600 (gRPC 8590). Start from the filming history (film_takes.py restore)
with the app installed. Writes screen.mp4/screen.json (Pleos display, 1920x1080 at 30 fps), audio.rec, the
Virtual Console screencast and telemetry, logcat.txt and events.json (marks, taps with their screen position,
cards). The take adds one trip and one rating; run film_takes.py restore afterwards.
"""
import json
import os
import re
import signal
import subprocess
import sys
import threading
import time
import urllib.request
import xml.etree.ElementTree as ET

OUT = os.path.abspath(sys.argv[1])
os.makedirs(OUT, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
ADB = [os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"), "-s", "emulator-5600"]
MAIN = "dev.motioncare.scaffold/dev.motioncare.app.MainActivity"
NAV = "ai.umos.maps.android.navigation.app/.MainActivity"
CONSOLE = "http://127.0.0.1:8796"
CTL = "http://127.0.0.1:8797"
# Dock icons on the 2560x1440 display (measured 2026-11-12): navigation and the running Motion Care app.
DOCK_NAV = (1155, 1370)
DOCK_APP = (1521, 1370)
events = []


def now():
    return int(time.time() * 1000)


def log(kind, **data):
    events.append({"t": now(), "kind": kind, **data})
    print(f"[{kind}]", json.dumps(data, ensure_ascii=False), flush=True)


def mark(name):
    log("mark", name=name)


def adb(*args, timeout=60):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=timeout).stdout


def shell(cmd, timeout=60):
    return adb("shell", cmd, timeout=timeout)


def http(method, path):
    try:
        with urllib.request.urlopen(urllib.request.Request(CONSOLE + path, method=method), timeout=10) as r:
            return r.status
    except Exception as e:  # noqa: BLE001
        return str(e)


def drive(**q):
    url = CTL + "/drive?" + "&".join(f"{k}={v}" for k, v in q.items())
    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            out = json.loads(r.read() or b"{}")
    except Exception as e:  # noqa: BLE001
        log("drive-error", error=str(e))
        return {}
    if q:
        log("drive", **q)
    return out


def speed():
    return float(drive().get("speed_kph", 0) or 0)


def wait_speed(cond, limit=40):
    started = time.time()
    while time.time() - started < limit:
        if cond(speed()):
            return True
        time.sleep(0.2)
    return False


def nodes():
    shell("uiautomator dump --user 10 /sdcard/ui.xml >/dev/null 2>&1")
    xml = shell("cat /sdcard/ui.xml")
    try:
        return list(ET.fromstring(xml[xml.index("<"):]).iter("node"))
    except Exception:  # noqa: BLE001
        return []


def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def press(x, y, text, hold):
    # The tap is logged just before it is sent, so the edit can draw where and when it landed.
    log("tap", text=text, x=x, y=y)
    shell(f"input tap {x} {y}")
    time.sleep(hold)


def find(text, contains=False):
    for n in nodes():
        t, d = n.get("text") or "", n.get("content-desc") or ""
        if (text in t or text in d) if contains else (t == text or d == text):
            return n
    return None


def tap(text, hold=2.0, contains=False):
    node = find(text, contains)
    if node is None:
        log("missing", text=text)
        return False
    press(*center(node), text, hold)
    return True


def scroll(hold=3.0):
    # A slow drag so the list moves at a pace the eye can follow.
    log("scroll", x=1700, y=1100)
    shell("input swipe 1700 1100 1700 560 1400")
    time.sleep(hold)


def recording():
    return "android.title=String (운행 기록 중)" in shell("dumpsys notification --noredact").split("mArchive=")[0]


class CardWatch(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()
        self.seen = []
        self.showing = False

    def run(self):
        last = None
        while not self.stop_event.is_set():
            active = shell("dumpsys notification --noredact").split("mArchive=")[0]
            found = re.search(r"android\.text=String \(([^)]*보세요)\)", active)
            text = found.group(1) if found else None
            if text and text != last:
                self.seen.append(text)
                log("card", text=text)
            self.showing = bool(text)
            last = text
            self.stop_event.wait(0.5)


class Heartbeat(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()

    def run(self):
        while not self.stop_event.is_set():
            http("POST", "/browser/session?client=take")
            self.stop_event.wait(1)


def start_recorders():
    procs = {
        "audio": subprocess.Popen(["node", "audio.js", os.path.join(OUT, "audio.rec"), os.path.join(OUT, "audio.json")], cwd=HERE),
        "console": subprocess.Popen(["node", "console.js", os.path.join(OUT, "console"), os.path.join(OUT, "console.json")], cwd=HERE),
        "screen": subprocess.Popen(["node", "screen.js", os.path.join(OUT, "screen.mp4"), os.path.join(OUT, "screen.json")], cwd=HERE),
        "logcat": subprocess.Popen(ADB + ["logcat", "-v", "epoch", "-T", "1", "GuidanceSound:*", "*:S"],
                                   stdout=open(os.path.join(OUT, "logcat.txt"), "w")),
    }
    for _ in range(60):
        try:
            urllib.request.urlopen(CTL + "/drive", timeout=2).read()
            break
        except Exception:  # noqa: BLE001
            time.sleep(0.5)
    time.sleep(2)
    return procs


def stop_recorders(procs):
    for name in ("screen", "audio", "console", "logcat"):
        procs[name].send_signal(signal.SIGTERM)
    for name in ("screen", "audio", "console"):
        procs[name].wait(timeout=90)


def cruise(seconds, kmh=40):
    drive(cruise=kmh, climb=15)
    wait_speed(lambda v: v >= kmh - 0.5)
    time.sleep(seconds)


def hard_stop():
    drive(brake=30)
    wait_speed(lambda v: v < 0.3, 20)
    time.sleep(0.6)
    drive(brake=0)


def story(watch):
    # Opening: the car is parked with the navigation in front.
    mark("open")
    time.sleep(9)
    mark("app")
    press(*DOCK_APP, "모션케어", 8)
    mark("people")
    time.sleep(5)
    mark("seats")
    tap("좌석", hold=7)
    mark("seats-back")
    tap("홈", hold=3) or (shell("input keyevent 4"), time.sleep(3))

    # Driving.
    mark("gear-d")
    drive(gear="Drive")
    for _ in range(40):
        if recording():
            break
        time.sleep(0.5)
    log("recording", on=recording())
    time.sleep(5)
    mark("nav")
    press(*DOCK_NAV, "내비게이션", 3)
    mark("accel")
    cruise(55)
    before = len(watch.seen)
    for stop in range(1, 4):
        mark(f"stop{stop}")
        hard_stop()
        time.sleep(3)
        if stop < 3:
            mark(f"go{stop}")
            cruise(55)
    mark("card-wait")
    log("card-shown", ok=_wait(lambda: len(watch.seen) > before, 20))
    _wait(lambda: not watch.showing, 20)
    mark("card-gone")
    time.sleep(3)
    mark("resume")
    cruise(170, kmh=40)
    mark("slow-stop")
    drive(brake=8)
    wait_speed(lambda v: v < 0.3, 30)
    time.sleep(1)
    drive(brake=0, gear="Park")
    time.sleep(2)
    mark("parked")
    press(*DOCK_APP, "모션케어", 3)

    # Three minutes in P end the trip by themselves.
    mark("wait-end")
    log("auto-end", ok=_wait(lambda: not recording(), 260))
    mark("ended")
    time.sleep(6)

    # Rating.
    mark("rate")
    tap("이동 평가", hold=4)
    tap("서아", hold=2.5)
    tap("보호자가 대신", hold=2.5)
    tap("3점", hold=2.5)
    tap("출발·정차", hold=2.5)
    mark("rate-save")
    tap("응답 남기기", hold=4, contains=True)
    mark("rated")
    time.sleep(2)

    # The trip just driven.
    if find("주행 상세") is None:
        tap("완료", hold=2.5)
    mark("detail")
    if not tap("주행 상세", hold=7):
        tap("기록", hold=4)
        rows = [n for n in nodes() if re.match(r"(오전|오후) \d+:\d+", n.get("text") or "")]
        if rows:
            press(*center(rows[0]), "오늘 운행", 7)
    mark("segment")
    # Segment chips read "3 · 14:05:12"; match the start so a clock time ending in 3 is not taken.
    chips = [n for n in nodes() if re.match(r"3 · \d", n.get("text") or "")]
    if chips:
        press(*center(chips[0]), "3번째 정차 직전 큰 감속", 7)
    else:
        log("missing", text="3 · ")
    mark("detail-lower")
    scroll(hold=7)
    shell("input keyevent 4")
    time.sleep(3)

    # The driver's trend.
    mark("profile")
    tap("프로필", hold=7)
    mark("profile-lower")
    scroll(hold=7)
    tap("홈", hold=3) or (shell("input keyevent 4"), time.sleep(3))

    # Next departure.
    if find("완료") is not None and find("이동 평가") is not None:
        tap("완료", hold=3)
    mark("next")
    time.sleep(6)
    mark("advice")
    tap("왜 이 조언인가요?", hold=7)
    mark("advice-calc")
    tap("실제 횟수와 계산 방법", hold=8)
    mark("end")
    time.sleep(2)


def _wait(cond, limit):
    started = time.time()
    while time.time() - started < limit:
        if cond():
            return True
        time.sleep(0.5)
    return False


def main():
    avd = adb("emu", "avd", "name").split()[0]
    print("avd:", avd)
    if avd != "pleos":
        sys.exit("not the pleos AVD")
    adb("forward", "tcp:8796", "tcp:8088")
    beat = Heartbeat()
    beat.start()
    http("PUT", "/vehicle/gear?gear=Park")
    http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    # Recirculation on: the cabin air card cannot take the slot the pattern card needs.
    http("PUT", "/vehicle/hvac?power=1&recirc=1&driver_temp=23")
    time.sleep(2)
    shell(f"am start --user 10 -S -n {MAIN} >/dev/null 2>&1")
    time.sleep(8)
    shell(f"am start --user 10 -n {NAV} >/dev/null 2>&1")
    time.sleep(4)
    procs = start_recorders()
    drive(steer=0)
    watch = CardWatch()
    watch.start()
    try:
        story(watch)
    finally:
        watch.stop_event.set()
        stop_recorders(procs)
        beat.stop_event.set()
        json.dump(events, open(os.path.join(OUT, "events.json"), "w"), ensure_ascii=False, indent=1)
        print("cards:", watch.seen)


if __name__ == "__main__":
    main()
