"""Split each take into sentences at its longest pauses, transcribe them and score against lines.json.
Usage: pick_takes.py <clips_dir>  (needs faster-whisper; writes choice.json)"""
import sys, wave, json, glob, os, re, difflib, io, numpy as np
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
from faster_whisper import WhisperModel
D = sys.argv[1]; batches = json.load(open(D + "/lines.json", encoding="utf-8")); TARGET = 190.0
m = WhisperModel("small", device="cpu", compute_type="int8")
NUM = {"십": "10", "삼": "3"}
def norm(s):
    s = re.sub(r"[\s.,!?'\"‘’]", "", s)
    return s.replace("십분", "10분").replace("삼분", "3분").replace("삼회", "3회").replace("디로", "D로").replace("피로", "P로").replace("피상태", "P상태")
def f0(a, sr):
    n, hop = int(0.04 * sr), int(0.01 * sr); out = []
    for i in range(0, len(a) - n, hop):
        x = a[i:i + n] - a[i:i + n].mean()
        if np.sqrt((x ** 2).mean()) < 0.01: continue
        r = np.correlate(x, x, "full")[n - 1:]; lo, hi = int(sr / 400), int(sr / 70); k = lo + int(np.argmax(r[lo:hi]))
        if r[k] / r[0] > 0.5: out.append(sr / k)
    return np.array(out)
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
    return [a[s:t] for s, t in zip([0] + cuts, cuts + [len(a)])], sorted(g[0] for g in sorted(gaps, reverse=True)[:n])
def stt(x, sr):
    k = int(len(x) * 16000 / sr); y = np.interp(np.linspace(0, len(x) - 1, k), np.arange(len(x)), x).astype(np.float32)
    segs, _ = m.transcribe(y, language="ko", beam_size=5); return " ".join(s.text.strip() for s in segs)
choice = {}
for b, lines in batches.items():
    best = None
    for p in sorted(glob.glob(f"{D}/{b}_*.wav")):
        w = wave.open(p, "rb"); sr = w.getframerate(); a = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768
        pieces, gl = split(a, sr, len(lines))
        if len(pieces) != len(lines): print(os.path.basename(p), "split failed"); continue
        sims, meds, rngs, texts = [], [], [], []
        for x, exp in zip(pieces, lines):
            t = stt(x, sr); texts.append(t); sims.append(difflib.SequenceMatcher(None, norm(t), norm(exp)).ratio())
            f = f0(x, sr); meds.append(float(np.median(f))); st = 12 * np.log2(f / np.median(f)); rngs.append(float(np.percentile(st, 95) - np.percentile(st, 5)))
        score = (min(sims) < 0.85) * 100 + abs(np.mean(meds) - TARGET) / 10 + (max(meds) - min(meds)) / 10 + np.mean(rngs) / 10
        print(f"{os.path.basename(p)} minsim {min(sims):.2f} med {np.mean(meds):.0f}Hz (spread {max(meds)-min(meds):.0f}) range {np.mean(rngs):.1f}st gaps {gl[:len(lines)-1]} score {score:.2f}")
        for t, s in zip(texts, sims):
            if s < 0.9: print("     ", f"{s:.2f}", t)
        if best is None or score < best[0]: best = (score, p, min(sims))
    choice[b] = {"file": best[1], "score": best[0], "minsim": best[2]}; print("=>", b, os.path.basename(best[1]), "\n")
json.dump(choice, open(D + "/choice.json", "w"), indent=1)
