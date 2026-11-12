"""Records the driving scenes of the 3 minute demo: Virtual Console page, Pleos display, emulator audio and events.

usage: python3 drive_takes.py <out_dir>
Only touches the AVD "pleos" on emulator-5600 (gRPC 8590). The console page is driven through console.js so what
it shows is what the car reports. Takes: pattern card (three hard stops with recirculation on), the trip's finish
and a passenger rating, then the cabin air card (recirculation off). Run film_takes.py restore afterwards.
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
events = []
state = {"recirc": 1, "driverTemp": 23.0, "sound": "알림음"}


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


def hvac(recirc):
    state["recirc"] = recirc
    http("PUT", f"/vehicle/hvac?power=1&recirc={recirc}&driver_temp={int(state['driverTemp'])}")
    log("hvac", recirc=recirc, driverTemp=state["driverTemp"])


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


def tap(text, hold=1.5, contains=False):
    for n in nodes():
        t, d = n.get("text") or "", n.get("content-desc") or ""
        if (text in t or text in d) if contains else (t == text or d == text):
            shell("input tap {} {}".format(*center(n)))
            log("tap", text=text)
            time.sleep(hold)
            return True
    log("missing", text=text)
    return False


def app_front(restart=False, hold=3.0):
    shell(f"am start --user 10 {'-S ' if restart else ''}-n {MAIN} >/dev/null 2>&1")
    time.sleep(hold)


def nav_front(hold=2.0):
    shell(f"am start --user 10 -n {NAV} >/dev/null 2>&1")
    time.sleep(hold)


def home_shows_intake(expected):
    for _ in range(20):
        texts = [n.get("text") for n in nodes() if n.get("text")]
        if "내기 순환" in texts and texts[texts.index("내기 순환") + 1:][:1] == [expected]:
            return True
        time.sleep(2)
    return False


def recording():
    return "android.title=String (운행 기록 중)" in shell("dumpsys notification --noredact").split("mArchive=")[0]


class CardWatch(threading.Thread):
    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()
        self.seen = []

    def run(self):
        last = None
        while not self.stop_event.is_set():
            active = shell("dumpsys notification --noredact").split("mArchive=")[0]
            found = re.search(r"android\.text=String \(([^)]*보세요)\)", active)
            text = found.group(1) if found else None
            if text and text != last:
                self.seen.append(text)
                log("card", text=text)
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


def wait_card(watch, before, limit):
    started = time.time()
    while time.time() - started < limit and len(watch.seen) == before:
        time.sleep(0.3)
    return len(watch.seen) > before


def gentle_stop():
    drive(brake=8)
    wait_speed(lambda v: v < 0.3, 30)
    time.sleep(1)
    drive(brake=0, gear="Park")
    time.sleep(1.5)


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


def pattern_take(watch):
    mark("home")
    time.sleep(4)
    mark("gear-d")
    drive(gear="Drive")
    for _ in range(40):
        if recording():
            break
        time.sleep(0.5)
    log("recording", on=recording())
    time.sleep(4)
    mark("nav")
    nav_front(hold=1.5)
    mark("accel")
    drive(cruise=40, climb=15)
    wait_speed(lambda v: v >= 39.5)
    time.sleep(5)
    before = len(watch.seen)
    for stop in range(1, 4):
        mark(f"stop{stop}")
        drive(brake=30)
        wait_speed(lambda v: v < 0.3, 20)
        time.sleep(0.6)
        drive(brake=0)
        time.sleep(2.5)
        if stop < 3:
            mark(f"go{stop}")
            drive(cruise=40, climb=15)
            wait_speed(lambda v: v >= 39.5)
            time.sleep(4)
    mark("card-wait")
    log("card-shown", ok=wait_card(watch, before, 20))
    time.sleep(10)
    mark("resume")
    drive(cruise=25, climb=15)
    time.sleep(8)
    mark("gentle-stop")
    gentle_stop()
    mark("app")
    app_front(hold=2.5)
    mark("finish")
    tap("운행 기록 종료", hold=4)
    mark("rate")
    tap("이동 평가", hold=3.5)
    tap("서아", hold=2)
    tap("보호자가 대신", hold=1.8)
    tap("3점", hold=1.8)
    tap("출발·정차", hold=2)
    mark("rate-save")
    tap("응답 남기기", hold=3.5, contains=True)
    mark("rated")
    time.sleep(2)
    for _ in range(3):
        if tap("완료", hold=2.5):
            break
        shell("input keyevent 4")
        time.sleep(2)
    mark("pattern-end")


def air_take(watch):
    mark("air-setup")
    hvac(0)
    time.sleep(2)
    app_front(restart=True, hold=4)
    log("intake-off", ok=home_shows_intake("꺼짐"))
    mark("air-home")
    time.sleep(3)
    mark("air-nav")
    nav_front(hold=1.5)
    before = len(watch.seen)
    mark("air-gear")
    drive(gear="Drive")
    for _ in range(40):
        if recording():
            break
        time.sleep(0.5)
    drive(cruise=30, climb=15)
    log("card-shown", ok=wait_card(watch, before, 40))
    mark("air-card")
    time.sleep(9)
    mark("air-stop")
    gentle_stop()
    app_front(hold=2.5)
    tap("운행 기록 종료", hold=4)
    tap("완료", hold=2)
    mark("air-end")


def main():
    print("avd:", adb("emu", "avd", "name").split()[0])
    adb("forward", "tcp:8796", "tcp:8088")
    beat = Heartbeat()
    beat.start()
    http("PUT", "/vehicle/gear?gear=Park")
    http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    hvac(1)
    time.sleep(2)
    app_front(restart=True, hold=5)
    log("intake-on", ok=home_shows_intake("켜짐"))
    procs = start_recorders()
    drive(steer=0)
    watch = CardWatch()
    watch.start()
    try:
        pattern_take(watch)
        air_take(watch)
    finally:
        watch.stop_event.set()
        stop_recorders(procs)
        beat.stop_event.set()
        json.dump(events, open(os.path.join(OUT, "events.json"), "w"), ensure_ascii=False, indent=1)
        print("cards:", watch.seen)


if __name__ == "__main__":
    main()
