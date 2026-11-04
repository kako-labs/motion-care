"""Measures how a normal app receives steering on the Pleos emulator, with and without adb grants.

Set ANDROID_HOME, ANDROID_SERIAL and PROBE_APK. Point ANDROID_SERIAL only at an emulator of your own.
"""
import os
import re
import statistics
import subprocess
import threading
import time
import urllib.request

SDK = os.environ["ANDROID_HOME"]
ADB = [SDK + "/platform-tools/adb", "-s", os.environ["ANDROID_SERIAL"]]
APK = os.environ["PROBE_APK"]
PORT = subprocess.run(ADB + ["forward", "tcp:0", "tcp:8088"], capture_output=True, text=True, timeout=30).stdout.strip()
BASE = f"http://127.0.0.1:{PORT}"


def adb(*args):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=120).stdout


def shell(cmd):
    return adb("shell", cmd)


def http(method, path):
    req = urllib.request.Request(BASE + path, method=method)
    with urllib.request.urlopen(req, timeout=5) as resp:
        return resp.read().decode("utf-8", "ignore")


def heartbeat(stop):
    while not stop.is_set():
        try:
            http("POST", "/browser/session?client=steer")
        except Exception:
            pass
        stop.wait(1)


def probe_logs():
    return [l for l in adb("logcat", "-d", "-s", "PROBE").splitlines() if "PROBE" in l]


def start_service():
    shell("am start-foreground-service --user 10 -n dev.probe/.FgService --es act toast")


def show(title, lines, pattern):
    print(f"--- {title}")
    for line in [l for l in lines if re.search(pattern, l)][:14]:
        print("  ", re.sub(r"^.*PROBE\s*:\s*", "", line)[:190])


print("device", ADB[-1], "avd", adb("emu", "avd", "name").split()[0], "port", PORT)
stop = threading.Event()
threading.Thread(target=heartbeat, args=(stop,), daemon=True).start()
time.sleep(2)

print("--- declared permissions on the system")
listing = shell("pm list permissions -f")
blocks = listing.split("+ permission:")
for block in blocks:
    if re.search(r"STEERING|pleos\.car\.permission\.CAR_SPEED|android\.car\.permission\.CAR_SPEED", block.split("\n")[0]):
        name = block.split("\n")[0].strip()
        level = re.search(r"protectionLevel:(\S+)", block)
        print("  ", name, level.group(1) if level else "?")

print("\n--- install WITHOUT grants, as a real install would be")
shell("am force-stop --user 10 dev.probe")
adb("uninstall", "dev.probe")
print(adb("install", "-r", APK).strip().splitlines()[-1])
shell("logcat -c")
start_service()
time.sleep(6)
lines = probe_logs()
show("permission and steering state", lines, r"perm |steering|speed config|speed registered|failed|error")
print("  grants:", re.findall(r"((?:android|pleos)\.car\.permission\.\w*(?:STEERING\w*|SPEED)): granted=(\w+)", shell("dumpsys package dev.probe")))

print("\n--- try runtime grants")
for name in ("android.car.permission.READ_CAR_STEERING", "android.car.permission.READ_STEERING_STATE", "pleos.car.permission.READ_CAR_STEERING", "android.car.permission.CAR_SPEED"):
    r = subprocess.run(ADB + ["shell", f"pm grant --user 10 dev.probe {name}"], capture_output=True, text=True, timeout=30)
    print("  ", name, "=>", (r.stdout + r.stderr).strip()[:120] or "ok")
print("  grants:", re.findall(r"((?:android|pleos)\.car\.permission\.\w*(?:STEERING\w*|SPEED)): granted=(\w+)", shell("dumpsys package dev.probe")))

print("\n--- run with what could be granted")
shell("am force-stop --user 10 dev.probe")
shell("logcat -c")
start_service()
time.sleep(6)
http("PUT", "/vehicle/gear?gear=Drive")
sent = [0, 5, 10, 20, 40, 80, 40, 20, 10, 5, 0, -5, -10, -20, -40, -80, -40, -20, -10, -5, 0]
for value in sent:
    http("PUT", f"/vehicle/controls?speed=40&accelerator=15&brake=0&steering={value}")
    time.sleep(1)
state = http("GET", "/vehicle/state")
print("  console state steering fields:", re.findall(r'"[^"]*steer[^"]*"\s*:\s*[^,}]+', state, flags=re.I)[:6])
for value in [12, 12, 12]:
    http("PUT", f"/vehicle/controls?speed=40&accelerator=15&brake=0&steering={value}")
    time.sleep(1)
http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
http("PUT", "/vehicle/gear?gear=Park")
time.sleep(2)
lines = probe_logs()
show("after grants: permission and registration", lines, r"perm |steering config|steering registered|steering property failed|sdk registered|sdk steering failed")

for tag, pattern in (("car steering", r"car steering (-?[\d.]+) status=(\d+) ts=(\d+) now=(\d+)"), ("sdk steering", r"sdk steering (-?[\d.]+) now=(\d+)")):
    values, times, lags = [], [], []
    for line in lines:
        m = re.search(pattern, line)
        if m:
            values.append(float(m.group(1)))
            if tag == "car steering":
                times.append(int(m.group(3)))
                lags.append((int(m.group(4)) - int(m.group(3))) / 1e6)
            else:
                times.append(int(m.group(2)))
    print(f"--- {tag}: {len(values)} events")
    if values:
        print("   first values:", values[:30])
        print("   min/max:", min(values), max(values))
        gaps = [(b - a) / 1e6 for a, b in zip(times, times[1:]) if b > a]
        if gaps:
            print("   gap ms median/p95/max:", round(statistics.median(gaps)), round(sorted(gaps)[int(len(gaps) * 0.95)]), round(max(gaps)))
        if lags:
            print("   event age ms median/max:", round(statistics.median(lags)), round(max(lags)))
print("--- sent:", sent)
stop.set()
adb("forward", "--remove", f"tcp:{PORT}")
