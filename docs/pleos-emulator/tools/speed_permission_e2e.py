#!/usr/bin/env python3
"""End-to-end check of the start screen after the speed permission is denied, on the Pleos emulator.

Build the debug APK and start the emulator first, then run:

    python3 docs/pleos-emulator/tools/speed_permission_e2e.py     # about 1.5 minutes

Location and notifications are granted by adb so that only the speed prompt appears. The run turns on the
"운행 자동 기록" switch three times: the first prompt is denied, the second is answered "허용하지 않고 다시 묻지 않음",
and the third shows no prompt, so the screen must say where to allow it and offer "설정 열기". The switch stays off
each time. It then opens settings from the app, allows 권한 > 위치 there, checks that the notice is gone back in the
app, turns the switch on, and lets the drive gear start a recording that it then finishes. It also
records where ACTION_APPLICATION_DETAILS_SETTINGS goes, which is why the app does not use it. Set ANDROID_SERIAL
when more than one device is attached. This script clears the app's data, so it must never run against an
emulator that belongs to other work.
"""
import re
import sys
import time

import recording_e2e as e2e

SPEED = "android.car.permission.CAR_SPEED"
FINISH = "운행 기록 종료"
DENIED = "차량 속도 권한이 있어야 운행을 기록할 수 있습니다."
WHERE = "설정 > 권한 > 위치에서 허용하면 기록할 수 있어요"
OPEN = "설정 열기"
PROMPT = "Motion Care에서 이 기기의 위치 정보에 액세스하도록 허용하시겠습니까?"
DENY = "허용 안함"
DENY_FOR_GOOD = "허용하지 않고 다시 묻지 않음"
CAR_SETTINGS = "com.android.car.settings"
VEHICLE_SETTINGS = "ai.umos.vehiclecontrol"


def permission():
    lines = [line.strip() for line in e2e.shell(f"dumpsys package {e2e.PKG}").splitlines()
             if line.strip().startswith(f"{SPEED}: granted")]
    return lines[-1].split(": ", 1)[1] if lines else "missing"


def focused():
    found = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)", e2e.shell("dumpsys window displays"))
    return found.group(1) if found else "unknown"


def tap_rightmost(text):
    """Car Settings keeps a menu on the left whose items share text with the page on the right."""
    rows = [n for n in e2e.nodes() if n.get("text") == text]
    if not rows:
        return None
    row = max(rows, key=lambda n: int(re.findall(r"\d+", n.get("bounds"))[0]))
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", row.get("bounds")))
    e2e.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}")
    return text


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
    e2e.shell(f"pm clear-permission-flags --user 10 {e2e.PKG} {SPEED} user-set user-fixed")
    for granted in ("pleos.car.permission.FUSED_LOCATION", "android.permission.POST_NOTIFICATIONS"):
        e2e.shell(f"pm grant --user 10 {e2e.PKG} {granted}")
    stop = e2e.threading.Event()
    e2e.threading.Thread(target=e2e.heartbeat, args=(stop,), daemon=True).start()
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    e2e.app_screen()
    print("first open:", permission())

    print("tap 1:", e2e.tap_switch())
    prompt_1 = step("speed_prompt")
    print("prompt 1:", prompt_1)
    print("deny:", e2e.tap(DENY))
    after_1 = step("speed_after_first_deny")
    focus_1 = focused()
    print("after first deny:", permission(), "focus:", focus_1, after_1)

    print("tap 2:", e2e.tap_switch())
    prompt_2 = step("speed_prompt_again")
    print("prompt 2:", prompt_2)
    print("deny for good:", e2e.tap(DENY_FOR_GOOD))
    after_2 = step("speed_after_deny_for_good")
    focus_2 = focused()
    fixed = permission()
    print("after deny for good:", fixed, "focus:", focus_2, after_2)

    print("tap 3:", e2e.tap_switch())
    after_3 = step("96_speed_permission_notice")
    still_off = e2e.switch_next_to(e2e.AUTO_RECORD)
    focus_3 = focused()
    print("after tap 3:", permission(), "focus:", focus_3, after_3)

    print("open settings:", e2e.tap(OPEN))
    app_page = step("speed_settings_app_page", 4)
    focus_4 = focused()
    print("settings:", focus_4, app_page)
    print("tap 권한:", tap_rightmost("권한"))
    permissions_page = step("97_speed_settings_permissions")
    print("permissions:", focused(), permissions_page)
    print("tap 위치:", tap_rightmost("위치"))
    location_page = step("speed_settings_location")
    print("location:", focused(), location_page)
    print("allow:", e2e.tap("허용"))
    time.sleep(2)
    allowed = permission()
    print("after allowing in settings:", allowed)

    e2e.app_screen()
    back = step("speed_notice_gone")
    print("back in the app:", back)
    print("tap switch:", e2e.tap_switch())
    time.sleep(3)
    turned_on = e2e.switch_next_to(e2e.AUTO_RECORD)
    e2e.http("PUT", "/vehicle/gear?gear=Drive")
    recording = step("speed_recording", 6)
    print("recording:", e2e.service_state(), turned_on, recording)
    print("finish:", e2e.tap(FINISH))
    finished = step("speed_finished", 4)
    e2e.http("PUT", "/vehicle/gear?gear=Park")
    print("finished:", finished)

    # Why the app does not use the documented app details action on Pleos.
    e2e.shell(f"am start --user 10 -a android.settings.APPLICATION_DETAILS_SETTINGS -d package:{e2e.PKG} -f 0x10000000")
    details = step("98_vehicle_settings_app_page", 4)
    focus_5 = focused()
    print("app details action:", focus_5, details)
    e2e.app_screen()

    checks = [
        ("the first tap shows the speed prompt", PROMPT in prompt_1 and DENY in prompt_1),
        ("a denial in the prompt only explains",
         e2e.PKG in focus_1 and DENIED in after_1 and WHERE not in after_1 and OPEN not in after_1),
        ("the second tap shows the prompt with don't ask again", PROMPT in prompt_2 and DENY_FOR_GOOD in prompt_2),
        ("don't ask again in the prompt only explains",
         "USER_FIXED" in fixed and e2e.PKG in focus_2 and DENIED in after_2 and OPEN not in after_2),
        ("the third tap shows no prompt and says where to allow it",
         e2e.PKG in focus_3 and PROMPT not in after_3 and DENIED in after_3 and WHERE in after_3 and OPEN in after_3),
        ("the switch stays off without the permission", still_off is not None and not still_off[0]),
        ("설정 열기 opens the app's page in Car Settings",
         CAR_SETTINGS in focus_4 and "Motion Care" in app_page and "권한" in app_page),
        ("권한 > 위치 > 허용 grants the speed permission",
         allowed.startswith("granted=true") and "USER_FIXED" not in allowed),
        ("the notice is gone back in the app", DENIED not in back and OPEN not in back and e2e.AUTO_RECORD in back),
        ("the switch turns on and the drive gear starts recording",
         turned_on is not None and turned_on[0] and FINISH in recording and DENIED not in recording),
        ("the recording finishes", FINISH not in finished),
        ("the app details action opens the vehicle settings app without a location entry",
         VEHICLE_SETTINGS in focus_5 and "위치" not in details),
    ]
    print("\n===== summary =====")
    for name, ok in checks:
        print("PASS" if ok else "FAIL", name)
    print("files:", e2e.OUT)
    stop.set()
    e2e.adb("forward", "--remove", f"tcp:{e2e.PORT}")
    sys.exit(0 if all(ok for _, ok in checks) else 1)


if __name__ == "__main__":
    main()
