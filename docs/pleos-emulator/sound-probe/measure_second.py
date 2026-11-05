#!/usr/bin/env python3
"""Second round: cross-app focus, whether TTS takes focus itself, no-internet build, plain Android TTS."""
import pathlib
import re
import subprocess
import sys
import time

SERIAL = "emulator-5600"
ADB = [str(pathlib.Path.home() / "Library/Android/sdk/platform-tools/adb"), "-s", SERIAL]
APKS = pathlib.Path(sys.argv[1])
COPY = "정지 지점보다 조금 일찍 감속해보세요"
MAIN, OTHER, NONET = "dev.soundprobe", "dev.soundother", "dev.soundnonet"


def adb(*args, timeout=90):
    return subprocess.run(ADB + list(args), capture_output=True, text=True, timeout=timeout).stdout


if adb("emu", "avd", "name").split()[0] != "pleos":
    sys.exit("unexpected avd")


def act(package, action, text=""):
    print(f">>> {package} {action} {text}", flush=True)
    adb("shell", f"am start-foreground-service --user 10 -n {package}/dev.soundprobe.ProbeService --es act '{action}' --es text '{text}'")


def focus_holders(label):
    car = adb("shell", "dumpsys", "car_service", "--services", "CarAudioService")
    if not car.strip():
        car = adb("shell", "dumpsys", "car_service")
    rows = [line.strip()[:170] for line in car.splitlines() if re.search(r"Current Focus Holders|Transient Focus Losers|Pending|clientId|Client|dev\.sound|ai\.pleos\.playground\.caas", line) and re.search(r"dev\.sound|caas|Holders|Losers", line)]
    print(f"  [{label}] car focus: {rows[:12] or 'none'}", flush=True)


def playing(label):
    audio = adb("shell", "dumpsys", "audio")
    rows = []
    for line in audio.splitlines():
        if "state:started" in line:
            usage = re.search(r"usage=(\w+)", line)
            owner = re.search(r"u/pid:(\S+)", line)
            muted = re.search(r"mutedState:(\S.*?)(?: |$)", line)
            rows.append(f"{usage.group(1) if usage else '?'} {owner.group(1) if owner else '?'}")
    print(f"  [{label}] playing: {rows or 'none'}", flush=True)


def flinger(label):
    dump = adb("shell", "dumpsys", "media.audio_flinger")
    threads = re.split(r"\n(?=Output thread )", dump)
    for thread in threads:
        if not thread.startswith("Output thread"):
            continue
        written = re.search(r"Frames written: (\d+)", thread) or re.search(r"Timestamp stats.*?(\d+)", thread)
        standby = re.search(r"Standby: (\w+)", thread)
        devices = re.search(r"Output devices: (.*)", thread)
        active = re.search(r"(\d+) Tracks? of which (\d+) (?:are )?active", thread)
        if standby and standby.group(1).lower() == "no":
            print(f"  [{label}] flinger active thread devices={devices.group(1)[:70] if devices else '?'} frames={written.group(1) if written else '?'} tracks={active.group(0) if active else '?'}", flush=True)


for package, apk in ((MAIN, "main.apk"), (OTHER, "other.apk"), (NONET, "nonet.apk")):
    adb("shell", f"am force-stop --user 10 {package}")
    print(package, "install:", adb("install", "-r", str(APKS / apk)).strip().splitlines()[-1])
    adb("shell", f"pm grant --user 10 {package} android.permission.POST_NOTIFICATIONS")
print("nonet INTERNET granted lines:", sum("android.permission.INTERNET" in line for line in adb("shell", "dumpsys", "package", NONET).splitlines()))
adb("logcat", "-c")

print("\n===== 1. other app plays music; TTS with no focus request =====")
act(OTHER, "focus:music:1:1")
act(OTHER, "tone:1:30000")
time.sleep(1)
act(MAIN, "tts:ON_DEVICE")
time.sleep(3)
act(MAIN, "attrs:5")
act(MAIN, "speak", COPY)
time.sleep(2.2)
playing("speaking")
focus_holders("speaking")
flinger("speaking")
time.sleep(3)

print("\n===== 2. same, TTS with MAY_DUCK focus =====")
act(MAIN, "focus:cue:5:3")
time.sleep(0.4)
focus_holders("granted")
act(MAIN, "speak", COPY)
time.sleep(2.2)
playing("speaking")
time.sleep(3)
act(MAIN, "abandon:cue")
time.sleep(1)

print("\n===== 3. same, navigation-usage voice holds focus from another app; TTS with MAY_DUCK =====")
act(OTHER, "abandon:music")
act(OTHER, "focus:nav:12:3")
act(OTHER, "tone:12:8000")
time.sleep(0.8)
act(MAIN, "focus:cue:5:3")
act(MAIN, "speak", COPY)
time.sleep(2.2)
playing("both")
time.sleep(3)
act(MAIN, "abandon:cue")
act(OTHER, "abandon:nav")
time.sleep(1)

print("\n===== 4. call focus held by another app =====")
act(OTHER, "focus:call:2:2")
time.sleep(0.5)
act(MAIN, "focus:cue:5:3")
time.sleep(0.5)
act(MAIN, "abandon:cue")
act(OTHER, "abandon:call")
time.sleep(1)

print("\n===== 5. another app takes exclusive focus while TTS holds MAY_DUCK =====")
act(MAIN, "focus:cue:5:3")
act(MAIN, "speak", COPY)
time.sleep(1.2)
act(OTHER, "focus:excl:1:4")
time.sleep(3.5)
playing("after-exclusive")
act(OTHER, "abandon:excl")
act(MAIN, "abandon:cue")
time.sleep(1)
act(MAIN, "release")
time.sleep(1)
adb("shell", f"am force-stop --user 10 {OTHER}")

print("\n===== 6. no-internet build, on-device mode =====")
act(NONET, "tts:ON_DEVICE")
time.sleep(3)
act(NONET, "attrs:5")
act(NONET, "speak", COPY)
time.sleep(6)
act(NONET, "release")
time.sleep(1)

print("\n===== 7. plain Android TextToSpeech API, no SDK call =====")
act(NONET, "ptts:5", COPY)
time.sleep(8)

print("\n===== 8. Bluetooth/call mode =====")
print("audio mode:", re.findall(r"mode: *\S+|Current mode.*|mMode=\S+", adb("shell", "dumpsys", "audio"))[:2])

print("\n===== probe log =====")
for line in adb("logcat", "-d", "-s", "SOUNDPROBE:I").splitlines():
    if "SOUNDPROBE:" in line:
        print(line.split("SOUNDPROBE: ", 1)[1])
print("\n===== crashes =====")
for line in adb("logcat", "-d", "-b", "crash").splitlines()[:20]:
    print(line[:180])
