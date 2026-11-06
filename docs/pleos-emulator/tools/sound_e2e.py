#!/usr/bin/env python3
"""End-to-end check of the guidance card's sound on the Pleos emulator.

Build the debug APK and the sound probe first, then pass the probe APK:

    ./gradlew :app:assembleDebug
    ./gradlew -p docs/pleos-emulator/sound-probe -PprobeId=dev.soundother :app:assembleDebug
    python3 docs/pleos-emulator/tools/sound_e2e.py docs/pleos-emulator/sound-probe/app/build/outputs/apk/debug/app-debug.apk

The probe plays the part of navigation voice and of an app that takes audio focus. Set ANDROID_SERIAL
when more than one device is attached. This script clears the app's data, so it must never run against
an emulator that belongs to other work. It proves that playback started, not how the sound sounds.
"""
import re
import sys
import threading
import time

import recording_e2e as e2e

OTHER = "dev.soundother"
SPEECH_SERVICE = "ai.pleos.playground.caas"
USAGE_NAV = 12


def other(action):
    e2e.shell(f"am start-foreground-service --user 10 -n {OTHER}/dev.soundprobe.ProbeService --es act '{action}'")


class PlaybackWatch(threading.Thread):
    """Collects every player seen started. Polls fast enough to catch the 0.6 s chime."""

    def __init__(self):
        super().__init__(daemon=True)
        self.stop_event = threading.Event()
        self.seen = set()

    def run(self):
        while not self.stop_event.is_set():
            for line in e2e.shell("dumpsys audio").splitlines():
                if "state:started" in line:
                    usage = re.search(r"usage=(\w+)", line)
                    owner = re.search(r"u/pid:\d+/(\d+)", line)
                    self.seen.add((usage.group(1) if usage else "?", owner.group(1) if owner else "?"))
            self.stop_event.wait(0.2)

    def owned_by(self, package):
        pids = set(e2e.shell(f"pidof {package}").split())
        return {usage for usage, pid in self.seen if pid in pids}


def sound_log():
    lines = e2e.adb("logcat", "-d", "-s", "GuidanceSound:I").splitlines()
    return [line.split("GuidanceSound: ", 1)[1] for line in lines if "GuidanceSound: " in line]


def speech_requests():
    """Who asked the speech service to speak since the log was cleared, according to the service itself."""
    return re.findall(r"speak\((\S+?)\) - new request", e2e.adb("logcat", "-d"))


def drive_until_card(on_started=None):
    """Repeats stop and go, which repeats hard stops, then holds a steady speed so the card can appear."""
    e2e.shell("am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity >/dev/null 2>&1")
    time.sleep(3)
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    profile = [v for _ in range(4) for v in (0, 10, 20, 30, 20, 10, 0)]
    profile += list(range(0, 41, 5)) + [40] * 22
    for speed in profile:
        e2e.http("PUT", f"/vehicle/controls?speed={speed}&accelerator={15 if speed else 0}&brake=0&steering=0")
        time.sleep(1)
        if any("started" in line or "skipped" in line for line in sound_log()):
            if on_started:
                on_started()
            break
    # Keep the steady speed for a while so a voice can finish.
    time.sleep(8)
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0")
    e2e.http("PUT", "/vehicle/gear?gear=Park")


def card_text():
    # The card stays in the notification list for about 15 s, so this is read right after the drive.
    active = e2e.shell("dumpsys notification --noredact")
    return re.findall(r"android\.text=String \(([^)]*보세요)\)", active)


def scenario(name, voice, expect_log, expect_speech, expect_owner=None, before=None, on_started=None):
    print(f"\n===== {name} =====", flush=True)
    e2e.shell(f"am force-stop --user 10 {OTHER}")
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    e2e.shell(f"pm grant --user 10 {e2e.PKG} android.permission.POST_NOTIFICATIONS")
    e2e.shell(f"pm grant --user 10 {e2e.PKG} android.car.permission.CAR_SPEED")
    # The start button also asks for location, for outdoor air.
    e2e.shell(f"pm grant --user 10 {e2e.PKG} pleos.car.permission.FUSED_LOCATION")
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.http("PUT", "/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0")
    time.sleep(1)
    e2e.app_screen()
    if voice:
        print("tap:", e2e.tap("음성"))
        time.sleep(1)
    e2e.shot(f"sound_{name}_ready")
    print("tap start:", e2e.tap("운행 기록 시작"))
    time.sleep(3)
    print("service:", e2e.service_state())
    e2e.adb("logcat", "-c")
    if before:
        before()
    print("playing before the drive:", [line.split("attr:")[0].strip()[-40:] for line in e2e.shell("dumpsys audio").splitlines() if "state:started" in line])
    watch = PlaybackWatch()
    watch.start()
    drive_until_card(on_started)
    watch.stop_event.set()
    log = sound_log()
    requests = speech_requests()
    print("sound log:", log)
    print("speech requests:", requests)
    print("players:", sorted(watch.seen))
    owner = watch.owned_by(expect_owner) if expect_owner else None
    if expect_owner:
        print(f"usages from {expect_owner}:", owner)
    card = card_text()
    print("card text seen in the notification list:", card[:1])
    ok = (any(expect_log in line for line in log)
          and (not expect_owner or "USAGE_NOTIFICATION" in owner)
          and (e2e.PKG in requests) == expect_speech
          and bool(card))
    print("result:", "PASS" if ok else "FAIL", flush=True)
    return name, ok


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0])
    probe = sys.argv[1]
    stop = threading.Event()
    threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    time.sleep(2)
    print("install app:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    print("install probe:", e2e.adb("install", "-r", probe).strip().splitlines()[-1])
    e2e.shell(f"pm grant --user 10 {OTHER} android.permission.POST_NOTIFICATIONS")

    results = [
        scenario("chime", False, "started: CHIME", False, e2e.PKG),
        scenario("voice", True, "started: VOICE", True, SPEECH_SERVICE),
        scenario("voice_while_navigation_speaks", True, "skipped: other voice", False,
                 before=lambda: other(f"tone:{USAGE_NAV}:90000")),
        scenario("navigation_starts_during_voice", True, "stopped: other voice", True,
                 on_started=lambda: other(f"tone:{USAGE_NAV}:3000")),
        scenario("exclusive_focus_during_voice", True, "stopped: focus lost", True,
                 on_started=lambda: other("focus:excl:1:4")),
    ]

    print("\n===== choice kept and hidden when guidance is off =====", flush=True)
    e2e.shell(f"am force-stop --user 10 {OTHER}")
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    e2e.app_screen()
    print("ready:", [t for t in e2e.texts() if t in ("주행 중 안내", "안내 소리", "알림음", "음성")])
    print("tap:", e2e.tap("음성"))
    time.sleep(1)
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.app_screen()
    prefs = e2e.shell(f"run-as {e2e.PKG} --user 10 cat shared_prefs/motion-care-guidance.xml")
    print("stored:", re.findall(r'name="sound">(\w+)<', prefs))
    e2e.shot("89_guidance_sound_choice")
    # Compose reports the switch as a plain view, and it is the first checkable thing on the screen.
    switch = next(n for n in e2e.nodes() if n.get("checkable") == "true")
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", switch.get("bounds")))
    e2e.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    time.sleep(1)
    after_off = [t for t in e2e.texts() if t in ("주행 중 안내", "안내 소리", "알림음", "음성")]
    print("after turning guidance off:", after_off)
    results.append(("choice", "VOICE" in prefs and after_off == ["주행 중 안내"]))

    print("\n===== summary =====")
    for name, ok in results:
        print(("PASS" if ok else "FAIL"), name)
    print("screenshots:", e2e.OUT)
    e2e.shell(f"am force-stop --user 10 {OTHER}")
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if all(ok for _, ok in results) else 1)


if __name__ == "__main__":
    main()
