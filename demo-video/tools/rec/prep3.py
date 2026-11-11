"""Lines up the recordings of the 3 minute demo for the Remotion edit.

usage: python3 tools/rec/prep3.py <app_take_dir> <drive_take_dir> <emulator_minus_host_ms>
Both takes come from tools/rec (app_scenes.py, drive_takes.py). Time zero of each take is the first frame of its
Pleos recording (screen.json). Writes public/rec/{app,drive}_pleos.mp4, public/rec/drive_console.mp4,
public/rec/drive_audio.wav and src/data/{app,drive}.json with marks, cards, sounds and the console telemetry in
seconds from time zero.
"""
import json
import os
import re
import statistics
import struct
import subprocess
import sys
import wave

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PUB = os.path.join(ROOT, "public", "rec")
DATA = os.path.join(ROOT, "src", "data")
os.makedirs(PUB, exist_ok=True)
APP, DRIVE = os.path.abspath(sys.argv[1]), os.path.abspath(sys.argv[2])
CLOCK_OFFSET_MS = int(sys.argv[3])
FPS = 30


def run(cmd):
    print("$", " ".join(cmd)[:160], flush=True)
    subprocess.run(cmd, check=True)


def reencode(src, dst):
    # Short keyframe distance so Remotion can seek anywhere quickly.
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-c:v", "libx264", "-crf", "16", "-preset", "medium", "-g", "15",
         "-pix_fmt", "yuv420p", "-movflags", "+faststart", dst])


# ---------- app-only take ----------
meta = json.load(open(os.path.join(APP, "screen.json")))
t0 = meta["startedAtMs"]
marks = json.load(open(os.path.join(APP, "marks.json")))
reencode(os.path.join(APP, "screen.mp4"), os.path.join(PUB, "app_pleos.mp4"))
json.dump({"duration": meta["frames"] / FPS, "marks": {m["name"]: round((m["t"] - t0) / 1000, 3) for m in marks}},
          open(os.path.join(DATA, "app.json"), "w"), ensure_ascii=False, indent=1)

# ---------- drive take ----------
meta = json.load(open(os.path.join(DRIVE, "screen.json")))
t0 = meta["startedAtMs"]
dur_ms = int(meta["frames"] / FPS * 1000)
end = t0 + dur_ms
events = json.load(open(os.path.join(DRIVE, "events.json")))
reencode(os.path.join(DRIVE, "screen.mp4"), os.path.join(PUB, "drive_pleos.mp4"))

# Sound plays from the app log (emulator clock, moved to the host clock).
sounds, current = [], None
for line in open(os.path.join(DRIVE, "logcat.txt")):
    m = re.match(r"\s*(\d+\.\d+)\s+\d+\s+\d+\s+\w\s+GuidanceSound: (started: (\w+)|finished|failed.*|stopped.*)", line)
    if not m:
        continue
    t = int(float(m.group(1)) * 1000) - CLOCK_OFFSET_MS
    if m.group(3):
        current = {"start": t, "kind": m.group(3)}
    elif current:
        current["end"] = t
        sounds.append(current)
        current = None

# Audio packets arrive after they play; calibrate on the loud packet that follows each logged start.
raw = open(os.path.join(DRIVE, "audio.rec"), "rb").read()
packets, i = [], 0
while i + 12 <= len(raw):
    ts, n = struct.unpack_from("<qI", raw, i)
    i += 12
    packets.append((ts // 1000, raw[i:i + n]))
    i += n


def loud(chunk, level=600):
    vals = struct.unpack(f"<{len(chunk) // 2}h", chunk[: len(chunk) // 2 * 2])
    return max((abs(v) for v in vals), default=0) >= level


lags = []
for s in sounds:
    hits = [ts for ts, ch in packets if s["start"] - 200 <= ts <= s["start"] + 5000 and loud(ch)]
    if hits:
        lags.append(hits[0] - s["start"])
lag = int(statistics.median(lags)) if lags else 600
print("sounds", [(s["kind"], round((s["start"] - t0) / 1000, 2)) for s in sounds], "audio lags", lags, "using", lag)
RATE, FRAME = 44100, 4
total = int(dur_ms / 1000 * RATE) * FRAME
pcm = bytearray(total)
for ts, chunk in packets:
    end_frame = int((ts - lag - t0) / 1000 * RATE)
    off = (end_frame - len(chunk) // FRAME) * FRAME
    if 0 <= off and off + len(chunk) <= total:
        pcm[off:off + len(chunk)] = chunk
aligned = os.path.join(DRIVE, "audio_aligned.wav")
with wave.open(aligned, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(RATE)
    w.writeframes(bytes(pcm))
# The emulator plays quietly; lift it and keep peaks below clipping.
run(["ffmpeg", "-y", "-loglevel", "error", "-i", aligned, "-af", "volume=20dB,alimiter=limit=0.9", os.path.join(PUB, "drive_audio.wav")])

# Console: timestamped screencast frames to constant 30 fps on the same time zero.
frames = json.load(open(os.path.join(DRIVE, "console", "frames.json")))
lst = os.path.join(DRIVE, "console_list.txt")
usable = [fr for fr in frames if fr["t"] <= end]
first = max((k for k, fr in enumerate(usable) if fr["t"] <= t0), default=0)
usable = usable[first:]
with open(lst, "w") as f:
    for k, fr in enumerate(usable):
        start = t0 if k == 0 else fr["t"]
        nxt = usable[k + 1]["t"] if k + 1 < len(usable) else end
        f.write(f"file '{fr['file']}'\nduration {max(nxt - start, 1) / 1000:.4f}\n")
    f.write(f"file '{usable[-1]['file']}'\n")
run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-vf", f"fps={FPS},format=yuv420p",
     "-t", f"{dur_ms / 1000:.3f}", "-c:v", "libx264", "-crf", "17", "-preset", "medium", "-g", "15", "-movflags", "+faststart",
     os.path.join(PUB, "drive_console.mp4")])

telemetry = json.load(open(os.path.join(DRIVE, "console", "telemetry.json")))
tele = [{"t": round((x["t"] - t0) / 1000, 3), "v": round(x.get("raw", x["speed_kph"]), 2), "a": x["accelerator_pct"],
         "b": x["brake_pct"], "s": x["steering_deg"], "g": x["gear"][0]} for x in telemetry if t0 <= x["t"] <= end]
cards = [e for e in events if e["kind"] == "card"]
out = {
    "duration": dur_ms / 1000,
    "audioLagMs": lag,
    "marks": {e["name"]: round((e["t"] - t0) / 1000, 3) for e in events if e["kind"] == "mark"},
    # The watcher polls the notification list, so it sees a card late; a card and its sound start together.
    "cards": [{"t": round((next((s["start"] for s in sounds if c["t"] - 4000 <= s["start"] <= c["t"] + 500), c["t"]) - t0) / 1000, 3),
               "text": c["text"]} for c in cards],
    "sounds": [{"start": round((s["start"] - t0) / 1000, 3), "end": round((s.get("end", s["start"] + 1000) - t0) / 1000, 3),
                "kind": s["kind"]} for s in sounds],
    "hvac": [{"t": round((e["t"] - t0) / 1000, 3), "recirc": e["recirc"], "driverTemp": e["driverTemp"]}
             for e in events if e["kind"] == "hvac"],
    "telemetry": tele,
}
json.dump(out, open(os.path.join(DATA, "drive.json"), "w"), ensure_ascii=False)
print("drive", out["duration"], "s, cards", out["cards"], "telemetry", len(tele))
