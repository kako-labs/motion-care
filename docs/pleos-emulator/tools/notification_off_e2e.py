#!/usr/bin/env python3
"""End-to-end check of the notice shown when notifications are off, on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/notification_off_e2e.py     # about 1 minute

Speed and location are granted by adb so that only the notification prompt appears. The run taps "알림 켜기" three
times: the first prompt is denied, the second is answered "허용하지 않고 다시 묻지 않음", and the third must open
Car Settings because the system no longer shows the prompt. It then turns notifications on there and checks that
the notice is gone when the app comes back. Set ANDROID_SERIAL when more than one device is attached. This script
clears the app's data, so it must never run against an emulator that belongs to other work.
"""
import re
import sys
import time

import recording_e2e as e2e

NOTICE = "알림이 꺼져 있어 주행 중 안내 카드가 뜨지 않아요"
TURN_ON = "알림 켜기"
PROMPT = "Motion Care에서 알림을 보내도록 허용하시겠습니까?"
DENY = "허용 안함"
DENY_FOR_GOOD = "허용하지 않고 다시 묻지 않음"
NOTIFICATION = "android.permission.POST_NOTIFICATIONS"


def permission():
    lines = [line.strip() for line in e2e.shell(f"dumpsys package {e2e.PKG}").splitlines()
             if line.strip().startswith(f"{NOTIFICATION}: granted")]
    return lines[-1].split(": ", 1)[1] if lines else "missing"


def focused():
    found = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)", e2e.shell("dumpsys window displays"))
    return found.group(1) if found else "unknown"


def settings_notification_switch():
    """The notification row of the app page. The menu on the left has an item with the same text."""
    rows = [n for n in e2e.nodes() if n.get("text") == "알림"]
    if not rows:
        return None
    row = max(rows, key=lambda n: int(re.findall(r"\d+", n.get("bounds"))[0]))
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", row.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def step(name, seconds=3):
    time.sleep(seconds)
    seen = e2e.texts()
    e2e.shot(name)
    return seen


def main():
    print("device:", e2e.ADB[-1], "avd:", e2e.adb("emu", "avd", "name").split()[0])
    print("install:", e2e.adb("install", "-r", e2e.APK).strip().splitlines()[-1])
    e2e.shell(f"am force-stop --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear --user 10 {e2e.PKG}")
    e2e.shell(f"pm clear-permission-flags --user 10 {e2e.PKG} {NOTIFICATION} user-set user-fixed")
    for granted in ("android.car.permission.CAR_SPEED", "pleos.car.permission.FUSED_LOCATION"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {granted}")
    e2e.app_screen()
    first_open = step("93_notification_notice")
    print("first open:", permission(), "notice:", NOTICE in first_open)

    print("tap 1:", e2e.tap(TURN_ON))
    prompt_1 = step("94_notification_prompt")
    print("prompt 1:", prompt_1)
    print("deny:", e2e.tap(DENY))
    after_1 = step("95_after_first_deny")
    focus_1 = focused()
    print("after first deny:", permission(), "focus:", focus_1)

    print("tap 2:", e2e.tap(TURN_ON))
    prompt_2 = step("96_notification_prompt_again")
    print("prompt 2:", prompt_2)
    print("deny for good:", e2e.tap(DENY_FOR_GOOD))
    after_2 = step("97_after_deny_for_good")
    focus_2 = focused()
    print("after deny for good:", permission(), "focus:", focus_2)

    print("tap 3:", e2e.tap(TURN_ON))
    settings = step("98_notification_settings", 4)
    focus_3 = focused()
    print("after tap 3:", focus_3, settings)
    switch = settings_notification_switch()
    print("notification switch at:", switch)
    if switch:
        e2e.shell(f"input tap {switch[0]} {switch[1]}")
    time.sleep(2)
    granted = permission()
    print("after the settings switch:", granted)
    e2e.app_screen()
    back = step("99_notice_gone")
    print("back in the app:", back)

    checks = [
        ("the notice shows before the first prompt", NOTICE in first_open),
        ("the first tap shows the system prompt", PROMPT in prompt_1 and DENY in prompt_1),
        ("a denied prompt keeps the driver in the app", e2e.PKG in focus_1 and NOTICE in after_1),
        ("the second tap shows the prompt with don't ask again", PROMPT in prompt_2 and DENY_FOR_GOOD in prompt_2),
        ("don't ask again keeps the driver in the app", e2e.PKG in focus_2 and NOTICE in after_2),
        ("the third tap opens the app's page in settings",
         "com.android.car.settings" in focus_3 and "Motion Care" in settings and "앱 정보" in settings),
        ("the settings switch grants the permission", granted.startswith("granted=true")),
        ("the notice is gone back in the app", NOTICE not in back and TURN_ON not in back and "주행 중 안내" in back),
    ]
    print("\n===== summary =====")
    for name, ok in checks:
        print("PASS" if ok else "FAIL", name)
    print("files:", e2e.OUT)
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if all(ok for _, ok in checks) else 1)


if __name__ == "__main__":
    main()
