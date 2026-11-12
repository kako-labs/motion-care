"""Records the app-only scenes of the 3 minute demo on the filming history, with a marker at the start of each.

usage: python3 app_scenes.py <out_dir>
Only touches the AVD "pleos" on emulator-5600. Writes screen.mp4 + screen.json (gRPC display at 1920x1080, 30 fps)
and marks.json [{name, t}] in host epoch ms. Nothing is saved in the app; every screen is backed out of.
"""
import json
import os
import re
import signal
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

OUT = os.path.abspath(sys.argv[1])
os.makedirs(OUT, exist_ok=True)
HERE = os.path.dirname(os.path.abspath(__file__))
ADB = [os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"), "-s", "emulator-5600"]
MAIN = "dev.motioncare.scaffold/dev.motioncare.app.MainActivity"
TIME = re.compile(r"(오전|오후) \d+:\d+")
marks = []


def now():
    return int(time.time() * 1000)


def mark(name):
    marks.append({"name": name, "t": now()})
    print("mark", name, flush=True)


def shell(cmd):
    return subprocess.run(ADB + ["shell", cmd], capture_output=True, text=True, timeout=60).stdout


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


def find(match):
    for n in nodes():
        t, d = n.get("text") or "", n.get("content-desc") or ""
        if match(t) or match(d):
            return n
    return None


def tap(text, hold=0.0, contains=False):
    node = find((lambda s: text in s) if contains else (lambda s: s == text))
    if node is None:
        print("missing", text, flush=True)
        return False
    shell("input tap {} {}".format(*center(node)))
    time.sleep(hold)
    return True


def back(hold=1.5):
    shell("input keyevent 4")
    time.sleep(hold)


def scroll(down=True, hold=1.5):
    shell("input swipe 1700 1100 1700 520 700" if down else "input swipe 1700 520 1700 1100 700")
    time.sleep(hold)


def open_row(label):
    for _ in range(10):
        found = [n for n in nodes() if (n.get("text") or "") == label]
        if found:
            shell("input tap {} {}".format(*center(found[0])))
            return True
        scroll(hold=1.0)
    print("missing row", label, flush=True)
    return False


def main():
    print("avd:", subprocess.run(ADB + ["emu", "avd", "name"], capture_output=True, text=True).stdout.split()[0])
    shell(f"am start --user 10 -n {MAIN} >/dev/null 2>&1")
    time.sleep(4)
    rec = subprocess.Popen(["node", os.path.join(HERE, "screen.js"), os.path.join(OUT, "screen.mp4"), os.path.join(OUT, "screen.json")], cwd=HERE)
    time.sleep(3)
    try:
        mark("home"); time.sleep(8)
        mark("seats"); tap("좌석", hold=6)
        seat = find(lambda s: "운전석" in s)
        if seat is not None:
            mark("seat-popup"); shell("input tap {} {}".format(*center(seat))); time.sleep(3.5); back()
        plus = [n for n in nodes() if "뒤 왼쪽" in (n.get("content-desc") or "")]
        if plus:
            shell("input tap {} {}".format(*center(plus[0]))); time.sleep(1.5)
            mark("add-person"); tap("사람 추가", hold=5); back(); back()
        back(hold=2.5)
        mark("records"); tap("전체 기록 보기", hold=4)
        scroll(hold=1.5); scroll(hold=2); scroll(down=False, hold=1); scroll(down=False, hold=1.5)
        mark("filter"); tap("서아", hold=3); tap("전체", hold=2)
        rows = [n for n in nodes() if TIME.match(n.get("text") or "")]
        mark("detail"); shell("input tap {} {}".format(*center(rows[0]))); time.sleep(5)
        mark("detail-segment"); tap("2 · ", hold=3.5, contains=True)
        mark("detail-lower"); scroll(hold=5)
        back(hold=3)
        mark("outing")
        if open_row("오전 11:18"):
            time.sleep(5); mark("outing-lower"); scroll(hold=4); back(hold=2)
        back(hold=2.5)
        mark("profile"); tap("프로필", hold=5)
        mark("profile-lower"); scroll(hold=4); back(hold=2.5)
        mark("advice"); tap("왜 이 조언인가요?", hold=5)
        mark("advice-calc"); tap("실제 횟수와 계산 방법", hold=6)
        back(hold=2.5)
        mark("home-again"); time.sleep(5)
        mark("end")
    finally:
        rec.send_signal(signal.SIGTERM)
        rec.wait(timeout=60)
        json.dump(marks, open(os.path.join(OUT, "marks.json"), "w"), indent=1)


if __name__ == "__main__":
    main()
