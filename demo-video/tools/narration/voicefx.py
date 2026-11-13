"""Speed and pitch change with ffmpeg filters through PyAV. shift(src, speed, semitones) -> float32 mono 48 kHz."""
import av, av.filter, numpy as np, wave
def shift(src, speed=1.0, semitones=0.0, rate=48000):
    c = av.open(src); st = c.streams.audio[0]; r = 2 ** (semitones / 12); g = av.filter.Graph()
    nodes = [g.add_abuffer(template=st), g.add("asetrate", str(int(round(st.rate * r)))), g.add("aresample", str(st.rate)),
             g.add("atempo", f"{speed / r:.5f}"), g.add("aformat", f"sample_fmts=s16:channel_layouts=mono:sample_rates={rate}"), g.add("abuffersink")]
    for a, b in zip(nodes, nodes[1:]): a.link_to(b)
    g.configure(); out = []
    def drain():
        while True:
            try: out.append(nodes[-1].pull().to_ndarray()[0])
            except (av.BlockingIOError, av.EOFError): break
    for fr in c.decode(st): nodes[0].push(fr); drain()
    nodes[0].push(None); drain()
    return np.concatenate(out).astype(np.float32) / 32768
def save(x, path, rate=48000):
    w = wave.open(path, "wb"); w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes()); w.close()
