"""Scan the source video for caption on/off times, per-frame motion and existing sounds. Usage: scan.py <source.mp4> <scan.json>"""
import av, numpy as np, json, sys
p = sys.argv[1]
c = av.open(p); v = c.streams.video[0]; v.thread_type = "AUTO"
rows = []; prev = None
for f in c.decode(v):
    t = float(f.pts * v.time_base)
    g = f.to_ndarray(format="gray")
    cap = g[955:1015:2, 160:1300:2]; bright = int((cap > 150).sum())
    small = g[::8, ::8].astype(np.int16)
    d = float(np.abs(small - prev).mean()) if prev is not None else 0.0
    rows.append((round(t, 3), bright, round(d, 2))); prev = small
c = av.open(p); a = c.streams.audio[0]; rs = av.AudioResampler(format="flt", layout="mono", rate=16000); buf = []
for f in c.decode(a):
    for g in rs.resample(f): buf.append(g.to_ndarray()[0])
x = np.concatenate(buf); hop = 1600
rms = [float(np.sqrt((x[i:i + hop] ** 2).mean())) for i in range(0, len(x) - hop, hop)]
json.dump({"frames": rows, "rms": rms}, open(sys.argv[2], "w"))
# caption segments: bright>1500 = caption on
on = False; segs = []; 
for t, b, d in rows:
    if b > 1500 and not on: on = True; s = t
    if b < 300 and on: on = False; segs.append((s, t))
if on: segs.append((s, rows[-1][0]))
print(len(segs), "caption segments"); print(" ".join(f"{s:.2f}-{e:.2f}" for s, e in segs))
snd = []; i = 0
while i < len(rms):
    if rms[i] > 0.003:
        j = i
        while j < len(rms) and rms[j] > 0.003: j += 1
        snd.append((i / 10, j / 10, max(rms[i:j]))); i = j
    else: i += 1
print("sounds:", " ".join(f"{s:.1f}-{e:.1f}({m:.3f})" for s, e, m in snd))
print("end", rows[-1][0])
