#!/usr/bin/env python3
"""Measures card sound, TTS usage, audio focus and in-drive playback on the Pleos emulator."""
import pathlib
import re
import subprocess
import sys
import threading
import time
import urllib.request

SERIAL = "emulator-5600"
ADB = [str(pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb"), "-s", SERIAL]
OUT = pathlib.Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
COPY = "정지 지점보다 조금 일찍 감속해보세요"


def adb(*args, timeout=60):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=timeout).stdout


if adb("emu", "avd", "name").split()[0] != "pleos":
    sys.exit("unexpected avd")


def act(action, text=""):
    print(f"\n>>> {action} {text}", flush=True)
    adb("shell", f"am start-foreground-service --user 10 -n dev.soundprobe/.ProbeService --es act '{action}' --es text '{text}'")


def playing():
    audio = adb("shell", "dumpsys", "audio")
    rows = []
    for line in audio.splitlines():
        if "state:started" in line:
            usage = re.search(r"usage=(\w+)", line)
            owner = re.search(r"u/pid:(\S+)", line)
            kind = re.search(r"type:(\S+)", line)
            rows.append(f"{usage.group(1) if usage else '?'} {kind.group(1) if kind else '?'} {owner.group(1) if owner else '?'}")
    focus = audio.split("Audio Focus stack entries", 1)[-1].split("\n\n", 1)[0].splitlines()[1:]
    print("  playing:", rows or "none")
    print("  focus:", [re.sub(r"\s+", " ", f.strip())[:150] for f in focus if f.strip()] or "none", flush=True)


def wait(seconds):
    time.sleep(seconds)


def section(name):
    print(f"\n===== {name} =====", flush=True)


adb("logcat", "-c")
adb("shell", "am force-stop --user 10 dev.soundprobe")
wait(1)

section("A. card sound while parked")
for kind in ("default", "explicit", "silent"):
    act(f"card:{kind}", COPY)
    for _ in range(3):
        wait(0.8)
        playing()
    wait(8)

section("B. TTS usage")
act("tts:ON_DEVICE")
wait(3)
for usage, name in ((1, "media-default"), (5, "notification"), (10, "notification-event"), (12, "nav-guidance"), (13, "sonification")):
    print(f"--- usage {usage} {name}")
    act(f"attrs:{usage}")
    wait(0.5)
    act("speak", COPY)
    wait(2.5)
    playing()
    wait(4)

section("C. audio focus")
for name, usage, gain in (
    ("music", 1, 1),
    ("nav", 12, 3),
    ("call", 2, 2),
):
    print(f"--- holder {name}")
    act(f"focus:{name}:{usage}:{gain}")
    wait(0.5)
    playing()
    for cue, cue_usage in (("cue_notification", 5), ("cue_nav", 12), ("cue_sonification", 13)):
        act(f"focus:{cue}:{cue_usage}:3")
        wait(0.4)
        act(f"abandon:{cue}")
        wait(0.4)
    act(f"abandon:{name}")
    wait(0.5)

section("D. own tone with focus")
act("focus:cue:5:3")
wait(0.3)
act("tone:5:400")
wait(0.25)
playing()
wait(0.8)
act("abandon:cue")
wait(0.5)

section("E. speech stop and second-speak latency")
act("attrs:5")
act("speak", COPY)
wait(1.0)
act("stop")
wait(1.5)
act("speak", COPY)
wait(6)

port = adb("forward", "tcp:0", "tcp:8088").strip()
base = f"http://127.0.0.1:{port}"


def http(method, path):
    with urllib.request.urlopen(urllib.request.Request(base + path, method=method), timeout=5) as resp:
        return resp.status


stop = threading.Event()


def heartbeat():
    while not stop.is_set():
        try:
            http("POST", "/browser/session?client=sound-matrix")
        except Exception:
            pass
        stop.wait(1)


threading.Thread(target=heartbeat, daemon=True).start()
wait(3)

section("F. driving at 40 km/h with navigation in front")
adb("shell", "am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1")
wait(3)
print("gear:", http("PUT", "/vehicle/gear?gear=Drive"), "speed:", http("PUT", "/vehicle/controls?speed=40&accelerator=15&brake=0&steering=0"))
wait(3)
act("card:default", COPY)
wait(1)
playing()
(OUT / "driving_card.png").write_bytes(subprocess.run(ADB + ["exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout)
act("speak", COPY)
wait(2.5)
playing()
wait(5)
print("speed 0:", http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0"), "park:", http("PUT", "/vehicle/gear?gear=Park"))
wait(2)

section("G. network-dependent modes without registration")
for mode in ("HYBRID", "SERVER"):
    act(f"tts:{mode}")
    wait(3)
    act("speak", COPY)
    wait(7)

act("release")
wait(1)
stop.set()
adb("forward", "--remove", f"tcp:{port}")

section("probe log")
log = adb("logcat", "-d", "-s", "SOUNDPROBE:I")
log = "\n".join(line[33:] for line in log.splitlines() if "SOUNDPROBE:" in line)
(OUT / "probe.log").write_text(log)
print(log)

section("system log around sound")
full = adb("logcat", "-d", timeout=120)
pattern = re.compile(r"Beeper|beep|NotificationPlayer|Ringtone|CarHeadsUp|HeadsUpEntry|HUN|OnDeviceStreamPlayerImpl.*generate|AudioFocus|CarAudioFocus|focus")
hits = [line[:200] for line in full.splitlines() if pattern.search(line) and "SOUNDPROBE" not in line]
(OUT / "system.log").write_text("\n".join(hits))
print(f"{len(hits)} lines in {OUT / 'system.log'}")
print("\n".join(hits[:40]))
print("--- notification effects")
notification = adb("shell", "dumpsys", "notification", "--noredact")
for line in notification.splitlines():
    if re.search(r"mSoundNotificationKey|mVibrateNotificationKey|isAutomotive|mNotificationEffectsEnabledForAutomotive", line):
        print(line.strip()[:160])
