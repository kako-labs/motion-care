"""Build the narrated story video: narration clips placed on the captions, frames held where a line needs more time,
music underneath. The source video's own audio (one app chime) is dropped and the closing screen has no narration.
Usage: assemble.py <source.mp4> <clips_dir> <scan.json> <music.mp3> <out.mp4> [--plan]
<clips_dir> holds lines.json and b0_el.wav .. b6_el.wav (one processed take per batch); plan.json and
narration-only.wav are written there. <scan.json> comes from scan.py."""
import av, wave, json, sys, numpy as np
from fractions import Fraction

VIDEO, E, SCAN, MUSIC, OUT = sys.argv[1:6]; PLAN_ONLY = "--plan" in sys.argv
SR = 48000; FPS = 30
SEGS = [(3.87, 9.9), (10.1, 16.4), (16.6, 20.9), (21.1, 27.4), (27.6, 34.8), (35, 41.8), (42, 49.8), (50, 56.8), (57, 66.8),
        (67, 76.1), (76.3, 79.3), (79.5, 85.3), (85.5, 91.3), (91.5, 95.7), (95.87, 102.5), (102.7, 106.1), (106.3, 111),
        (111.2, 115), (115.2, 120.6), (120.8, 128.2), (128.4, 134.8), (135, 141.77), (142, 148.4), (148.6, 153.6),
        (153.8, 160.1), (160.3, 166.6), (166.8, 173.6), (173.8, 181.2)]
SRC_END = 190.53; CHIME = (66.5, 67.2); ENDING_AT = 181.2
scan = json.load(open(SCAN)); FR = scan["frames"]


def read(p):
    w = wave.open(p, "rb"); sr = w.getframerate()
    return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768, sr


def trim(x, sr):
    v = np.where(np.abs(x) > 0.01)[0]
    return x[max(0, v[0] - int(0.03 * sr)): v[-1] + int(0.12 * sr)]


def split(a, sr, n):
    hop = int(0.01 * sr); e = np.array([np.sqrt((a[i:i + hop] ** 2).mean()) for i in range(0, len(a) - hop, hop)]); sil = e < 0.008
    gaps = []; i = 0
    while i < len(sil):
        if sil[i]:
            j = i
            while j < len(sil) and sil[j]: j += 1
            if i > 0 and j < len(sil): gaps.append((j - i, i, j))
            i = j
        else: i += 1
    cuts = sorted((g[1] + g[2]) // 2 * hop for g in sorted(gaps, reverse=True)[: n - 1])
    return [a[s:t] for s, t in zip([0] + cuts, cuts + [len(a)])]


def up(x, sr, gain):
    return np.interp(np.linspace(0, len(x) - 1, int(len(x) * SR / sr)), np.arange(len(x)), x).astype(np.float32) * gain


def batch_clips(path, n, join=None):
    a, sr = read(path); pieces = [trim(p, sr) for p in split(a, sr, n)]
    gain = 0.09 / np.sqrt((np.concatenate(pieces) ** 2).mean())   # one gain per take keeps its own balance
    if join is not None:   # merge pieces i and i+1 with a short pause (the product name and its tagline)
        i = join; pieces[i:i + 2] = [np.concatenate([pieces[i], np.zeros(int(0.35 * sr), np.float32), pieces[i + 1]])]
    return [up(p, sr, gain) for p in pieces]


# ---- narration clips in order: title, 28 caption lines, 2 ending lines (one voice, speed x1.12, pitch -1.5 st)
lines = json.load(open(E + "/lines.json", encoding="utf-8"))
clips = batch_clips(E + "/b0_el.wav", 4, join=0)
for b in ["b1", "b2", "b3", "b4", "b5"]: clips += batch_clips(E + f"/{b}_el.wav", len(lines[b]))
clips += batch_clips(E + "/b6_el.wav", 4, join=1)
title, body, ending = clips[0], clips[1:29], clips[29:]
assert len(body) == 28 and len(ending) == 2, (len(body), len(ending))
L = lambda x: len(x) / SR


def still_time(lo, hi):
    """Stillest moment in [lo, hi] of the source (least frame-to-frame change), preferring later ones, outside the chime."""
    best = None
    for k in range(len(FR) - 9):
        t = FR[k][0]
        if t < lo or t > hi or (CHIME[0] - 0.2 < t < CHIME[1] + 0.2): continue
        m = max(f[2] for f in FR[k:k + 9])
        if best is None or m <= best[0] + 0.05: best = (min(m, best[0]) if best else m, t)
    return best[1] if best else hi


# ---- plan: where each line goes and how long each hold is (output time = source time + holds so far)
holds = []; plan = []; off = 0.0
t0 = 0.40; n0 = max(0.0, t0 + L(title) + 0.25 - 3.50); n0 = round(n0 * FPS) / FPS
if n0: holds.append((3.40, n0)); off += n0
plan.append(("title", t0, t0 + L(title), 0)); prev_end = t0 + L(title)
for i, (cs, ce) in enumerate(SEGS):
    start = max(cs + off + 0.30, prev_end + 0.25)
    end = start + L(body[i]); limit = ce + off - 0.10; extra = 0.0
    if end > limit:
        extra = np.ceil((end - limit) * FPS) / FPS
        holds.append((still_time(max(cs + 1.0, ce - 2.5), ce - 0.45), float(extra))); off += extra
    plan.append((f"cap{i + 1}", start, end, extra)); prev_end = end
t = max(ENDING_AT + off + 0.8, prev_end + 0.5)
ending = []   # the closing screen plays without narration
for k, x in enumerate(ending):
    plan.append((f"ending{k}", t, t + L(x), 0)); t += L(x) + 0.35
tail = 0.0
if tail: holds.append((SRC_END - 0.1, float(tail))); off += tail
DUR = SRC_END + off
holds.sort()
for name, s, e, x in plan: print(f"{name:9s} {s:7.2f}-{e:7.2f} ({e - s:.2f}s)" + (f"  hold +{x:.2f}s" if x else ""))
print("holds (source time, seconds):", [(round(a, 2), round(b, 2)) for a, b in holds])
print(f"total {DUR:.2f}s (source {SRC_END}s, +{off:.2f}s)")
json.dump({"plan": plan, "holds": holds, "duration": DUR}, open(E + "/plan.json", "w"), indent=1)
if PLAN_ONLY: sys.exit()

# ---- audio: narration + original app sound (silence inserted at holds) + music
nar = np.zeros(int(DUR * SR) + SR, np.float32)
for (name, s, e, x), clip in zip(plan, [title] + body):
    i = int(s * SR); nar[i:i + len(clip)] += clip
c = av.open(VIDEO); rs = av.AudioResampler(format="flt", layout="mono", rate=SR); buf = []
for f in c.decode(c.streams.audio[0]):
    for g in rs.resample(f): buf.append(g.to_ndarray()[0])
orig = np.concatenate(buf); parts = []; last = 0
for ts, d in holds:
    k = int(ts * SR); parts += [orig[last:k], np.zeros(int(d * SR), np.float32)]; last = k
orig = np.concatenate(parts + [orig[last:]]) * 0.0   # the app chime in the source is dropped
c = av.open(MUSIC); rs = av.AudioResampler(format="flt", layout="mono", rate=SR); buf = []; n = 0
for f in c.decode(c.streams.audio[0]):
    for g in rs.resample(f): buf.append(g.to_ndarray()[0]); n += buf[-1].size
    if n > (DUR + 1) * SR: break
bgm = np.concatenate(buf)[: int(DUR * SR)]
win = 8 * SR; p2 = np.cumsum(np.concatenate([[0], bgm.astype(np.float64) ** 2]))   # slow levelling: the track starts quiet and grows
idx = np.arange(0, len(bgm), SR // 2); loc = np.array([np.sqrt((p2[min(len(bgm), i + win // 2)] - p2[max(0, i - win // 2)]) / win) for i in idx])
gain = np.interp(np.arange(len(bgm)), idx, np.clip(0.02 / np.maximum(loc, 1e-4), 0.02, 2.5)); bgm = (bgm * gain).astype(np.float32)
k = SR; bgm[:k] *= np.linspace(0, 1, k); k = 3 * SR; bgm[-k:] *= np.linspace(1, 0, k)
N = int(DUR * SR); mix = np.zeros(N, np.float32)
for x in (nar, orig, bgm): mix[: min(N, len(x))] += x[:N]
mix = np.clip(mix, -1, 1)
ww = wave.open(E + "/narration-only.wav", "wb"); ww.setnchannels(1); ww.setsampwidth(2); ww.setframerate(SR); ww.writeframes((np.clip(nar[:N], -1, 1) * 32767).astype(np.int16).tobytes()); ww.close()

# ---- video
inp = av.open(VIDEO); vin = inp.streams.video[0]; vin.thread_type = "AUTO"; o = av.open(OUT, "w")
vo = o.add_stream("libx264", rate=FPS); vo.width, vo.height, vo.pix_fmt = vin.width, vin.height, "yuv420p"; vo.options = {"crf": "18", "preset": "medium"}
ao = o.add_stream("aac", rate=SR); ao.layout = "stereo"; ao.bit_rate = 192000
idx = 0; pending = list(holds)
for fr in inp.decode(vin):
    tsrc = float(fr.pts * vin.time_base); g = fr.reformat(format="yuv420p"); reps = 1
    while pending and tsrc >= pending[0][0]: reps += int(round(pending.pop(0)[1] * FPS))
    for _ in range(reps):
        h = av.VideoFrame.from_ndarray(g.to_ndarray(), format="yuv420p") if reps > 1 else g
        h.pts = idx; h.time_base = Fraction(1, FPS); idx += 1
        for p in vo.encode(h): o.mux(p)
for p in vo.encode(None): o.mux(p)
st = np.stack([mix, mix])
for i in range(0, N, 1024):
    af = av.AudioFrame.from_ndarray(np.ascontiguousarray(st[:, i:i + 1024]), format="fltp", layout="stereo"); af.sample_rate = SR; af.pts = i
    for p in ao.encode(af): o.mux(p)
for p in ao.encode(None): o.mux(p)
o.close()
print("frames written", idx, "=", round(idx / FPS, 2), "s")
