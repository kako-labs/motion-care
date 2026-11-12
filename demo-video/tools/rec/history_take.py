"""Records the history part of the demo on the filming history (film_takes.py restore), not on the trip just driven:
records tab, the passenger filter, one generated trip (11/5 16:39, the driver 지영 with 서아, five hard stops and a
card), its detail, the driver's profile and the next departure advice with its counting.

usage: python3 history_take.py <out_dir>
Run with the app home in front after film_takes.py restore. Writes screen.mp4/screen.json and events.json like
story_take.py. Nothing is saved in the app.
"""
import json
import os
import re
import signal
import subprocess
import time

import story_take as st

TRIP_ROW = "오후 4:39"
SEOA_CHIP = (1448, 272)


def home():
    for _ in range(4):
        if st.find("왜 이 조언인가요?") is not None:
            return
        st.shell("input keyevent 4")
        time.sleep(1.5)


def main():
    avd = st.adb("emu", "avd", "name").split()[0]
    if avd != "pleos":
        raise SystemExit("not the pleos AVD")
    home()
    rec = subprocess.Popen(["node", "screen.js", os.path.join(st.OUT, "screen.mp4"), os.path.join(st.OUT, "screen.json")], cwd=st.HERE)
    time.sleep(3)
    try:
        st.mark("home")
        time.sleep(3)
        st.mark("records")
        st.tap("기록", hold=5)
        st.mark("filter")
        st.press(*SEOA_CHIP, "서아", 4)
        st.mark("list-scroll")
        st.scroll(hold=4)
        rows = [n for n in st.nodes() if (n.get("text") or "") == TRIP_ROW]
        st.mark("trip")
        if rows:
            st.press(*st.center(rows[0]), TRIP_ROW, 8)
        else:
            st.log("missing", text=TRIP_ROW)
        chips = [n for n in st.nodes() if re.match(r"3 · \d", n.get("text") or "")]
        st.mark("segment")
        if chips:
            st.press(*st.center(chips[0]), chips[0].get("text"), 7)
        else:
            st.log("missing", text="3 · ")
        st.mark("lower1")
        st.scroll(hold=7)
        st.mark("lower2")
        st.scroll(hold=7)
        st.mark("detail-end")
        home()
        st.mark("profile")
        st.tap("프로필", hold=7)
        st.mark("profile-lower")
        st.scroll(hold=7)
        home()
        st.mark("next")
        time.sleep(6)
        st.mark("advice")
        st.tap("왜 이 조언인가요?", hold=7)
        st.mark("advice-calc")
        st.tap("실제 횟수와 계산 방법", hold=9)
        st.mark("end")
    finally:
        rec.send_signal(signal.SIGTERM)
        rec.wait(timeout=60)
        json.dump(st.events, open(os.path.join(st.OUT, "events.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
