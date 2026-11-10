"""원본 영상의 왼쪽 아래 데이터 패널에서 속도·핸들 값을 읽어 telemetry.json 을 만든다.
1단계(cluster): 숫자 영역을 10fps로 잘라 같은 모양끼리 묶고, 대표 이미지를 시트로 저장.
2단계(build): 사람이 적은 labels_*.json (클러스터번호 -> 값) 으로 시계열 생성.
사용: python tools/extract_telemetry.py cluster <video> <workdir>
      python tools/extract_telemetry.py build <workdir> <out.json>
"""
import sys, json, subprocess, os
import numpy as np
from PIL import Image

FPS = 10
REGIONS = {  # name: (x, y, w, h) in 1920x1080 source
    "speed": (86, 760, 126, 64),
    "steer": (546, 792, 110, 36),
}

def dump(video, name):
    x, y, w, h = REGIONS[name]
    cmd = ["ffmpeg", "-v", "error", "-i", video, "-vf", f"fps={FPS},crop={w}:{h}:{x}:{y},format=gray",
           "-f", "rawvideo", "-"]
    raw = subprocess.run(cmd, capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.uint8).reshape(-1, h, w)

def cluster(frames, thr):
    reps, ids = [], []
    for f in frames:
        b = (f > 128)
        best, bd = -1, 1e9
        for i, r in enumerate(reps):
            d = np.count_nonzero(b ^ r)
            if d < bd: best, bd = i, d
        if bd <= thr: ids.append(best)
        else:
            reps.append(b); ids.append(len(reps) - 1)
    return reps, ids

def sheet(reps, counts, path, cols=8):
    h, w = reps[0].shape
    rows = (len(reps) + cols - 1) // cols
    img = Image.new("L", (cols * (w + 40), rows * (h + 6)), 40)
    from PIL import ImageDraw
    d = ImageDraw.Draw(img)
    for i, r in enumerate(reps):
        cx, cy = (i % cols) * (w + 40), (i // cols) * (h + 6)
        img.paste(Image.fromarray((r * 255).astype(np.uint8)), (cx + 36, cy))
        d.text((cx + 2, cy + 2), str(i), fill=255)
        d.text((cx + 2, cy + 16), f"n{counts[i]}", fill=160)
    img.save(path)

if sys.argv[1] == "cluster":
    video, work = sys.argv[2], sys.argv[3]
    os.makedirs(work, exist_ok=True)
    for name, thr in (("speed", 60), ("steer", 25)):
        fr = dump(video, name)
        reps, ids = cluster(fr, thr)
        counts = np.bincount(ids, minlength=len(reps))
        sheet(reps, counts, os.path.join(work, f"clusters_{name}.png"))
        json.dump(ids, open(os.path.join(work, f"ids_{name}.json"), "w"))
        print(name, "frames", len(fr), "clusters", len(reps))
else:
    work, out = sys.argv[2], sys.argv[3]
    res = {"fps": FPS}
    for name in ("speed", "steer"):
        ids = json.load(open(os.path.join(work, f"ids_{name}.json")))
        labels = json.load(open(os.path.join(work, f"labels_{name}.json")))
        vals, last = [], 0
        for i in ids:
            v = labels.get(str(i))
            if v is None: v = last
            vals.append(v); last = v
        res[name] = vals
    json.dump(res, open(out, "w"), separators=(",", ":"))
    print("wrote", out, len(res["speed"]))
