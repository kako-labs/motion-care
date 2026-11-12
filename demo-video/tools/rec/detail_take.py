"""Records the trip just driven in story_take.py: records tab, the trip's detail, its third hard stop, the cabin and
the passenger's answer. Run right after story_take.py, before film_takes.py restore, with the app home in front.

usage: python3 detail_take.py <out_dir>
Writes screen.mp4/screen.json and events.json like story_take.py. Nothing is saved in the app.
"""
import json
import os
import re
import signal
import subprocess
import time

import story_take as st


def main():
    avd = st.adb("emu", "avd", "name").split()[0]
    if avd != "pleos":
        raise SystemExit("not the pleos AVD")
    rec = subprocess.Popen(["node", "screen.js", os.path.join(st.OUT, "screen.mp4"), os.path.join(st.OUT, "screen.json")], cwd=st.HERE)
    time.sleep(3)
    try:
        st.mark("home")
        time.sleep(3)
        st.mark("records")
        st.tap("기록", hold=5)
        rows = [n for n in st.nodes() if re.match(r"(오전|오후) \d+:\d+$", n.get("text") or "")]
        st.mark("trip")
        st.press(*st.center(rows[0]), rows[0].get("text"), 7)
        chips = [n for n in st.nodes() if re.match(r"3 · \d", n.get("text") or "")]
        st.mark("segment")
        if chips:
            st.press(*st.center(chips[0]), chips[0].get("text"), 7)
        else:
            st.log("missing", text="3 · ")
        st.mark("lower1")
        st.scroll(hold=6)
        st.mark("lower2")
        st.scroll(hold=7)
        st.mark("end")
        st.shell("input keyevent 4")
        time.sleep(2)
        st.shell("input keyevent 4")
        time.sleep(2)
    finally:
        rec.send_signal(signal.SIGTERM)
        rec.wait(timeout=60)
        json.dump(st.events, open(os.path.join(st.OUT, "events.json"), "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
