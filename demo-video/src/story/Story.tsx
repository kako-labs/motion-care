import React from 'react';
import {AbsoluteFill, Audio, Easing, Img, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {THEME} from '../config/theme';
import detail from '../data/detail.json';
import story from '../data/story.json';
import {CHAPTERS, END_SEC, FPS, MAIN_END, PLACED, captionAt, sceneAt, type Placed, type Rect} from './scenes';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const ease = Easing.inOut(Easing.cubic);
const ramp = (t: number, a: number, b: number) => interpolate(t, [a, b], [0, 1], {...clamp, easing: ease});
const fontFace = `@font-face{font-family:'Noto Sans KR';src:url('${staticFile('fonts/NotoSansKR-VF.ttf')}');font-weight:100 900;}`;

/** The Pleos display (2560x1440) always sits in the same place at the same size; nothing moves the camera. */
const FRAME = {x: 176, y: 20, w: 1568, h: 882};
const K = FRAME.w / 2560;

const toFrame = ([x1, y1, x2, y2]: Rect) => ({left: x1 * K, top: y1 * K, width: (x2 - x1) * K, height: (y2 - y1) * K});

const SOURCES = {story: {video: 'rec/story_pleos.mp4', taps: story.taps}, detail: {video: 'rec/detail_pleos.mp4', taps: detail.taps}};

/**
 * A fixed zoom for small text only: at most 1.5x, eased in and out, held in between. The area's middle moves to the
 * frame's middle, kept so that nothing outside the screen shows. Returns the transform as x' = tx + x * s.
 */
const zoomAt = (scene: Placed, local: number) => {
  const z = scene.zoom;
  if (!z) return {s: 1, tx: 0, ty: 0};
  const p = Math.min(ramp(local, z.at, z.at + 1.0), 1 - ramp(local, z.until - 1.0, z.until));
  const s = 1 + (Math.min(z.scale ?? 1.4, 1.5) - 1) * p;
  const [x1, y1, x2, y2] = z.rect;
  const half = (len: number) => len / (2 * s);
  const cx = Math.min(Math.max(FRAME.w / 2 + (((x1 + x2) / 2) * K - FRAME.w / 2) * p, half(FRAME.w)), FRAME.w - half(FRAME.w));
  const cy = Math.min(Math.max(FRAME.h / 2 + (((y1 + y2) / 2) * K - FRAME.h / 2) * p, half(FRAME.h)), FRAME.h - half(FRAME.h));
  return {s, tx: FRAME.w / 2 - cx * s, ty: FRAME.h / 2 - cy * s};
};

const SceneVideo = ({scene}: {scene: Placed}) => {
  const frame = useCurrentFrame();
  const local = frame / FPS - scene.start;
  const fadeIn = scene.dissolve ? ramp(local, 0, scene.dissolve) : 1;
  const z = zoomAt(scene, local);
  const extra = PLACED[scene.index + 1]?.dissolve ?? 0;
  return (
    <Sequence from={Math.round(scene.start * FPS)} durationInFrames={Math.round((scene.dur + extra) * FPS)} layout="none">
      <AbsoluteFill style={{opacity: fadeIn, transformOrigin: '0 0', transform: `translate(${z.tx}px, ${z.ty}px) scale(${z.s})`}}>
        <OffthreadVideo src={staticFile(SOURCES[scene.src].video)} trimBefore={Math.round(scene.from * FPS)}
          playbackRate={scene.rate} muted style={{width: FRAME.w, height: FRAME.h}} />
      </AbsoluteFill>
    </Sequence>
  );
};

/** Where the take tapped, drawn as a soft ring that fades out. */
const Taps = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  const z = zoomAt(scene, local);
  const src = scene.from + local * scene.rate;
  return (
    <>
      {SOURCES[scene.src].taps.filter((t) => src >= t.t && src < t.t + 0.8 * scene.rate && t.t >= scene.from).map((t) => {
        const age = (src - t.t) / scene.rate;
        const o = 1 - ramp(age, 0.25, 0.8);
        const r = 26 + 14 * ramp(age, 0, 0.8);
        const x = z.tx + t.x * K * z.s;
        const y = z.ty + t.y * K * z.s;
        return <div key={t.t} style={{position: 'absolute', left: x - r, top: y - r, width: r * 2, height: r * 2, borderRadius: '50%',
          background: `rgba(255,255,255,${0.32 * o})`, border: `2px solid rgba(255,255,255,${0.75 * o})`}} />;
      })}
    </>
  );
};

/** A spot dims everything else a little and draws a thin outline; no glow, no blinking. */
const Spots = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  return (
    <>
      {(scene.spots ?? []).map((s, i) => {
        const o = Math.min(ramp(local, s.at, s.at + 0.4), 1 - ramp(local, s.until - 0.4, s.until));
        if (o <= 0) return null;
        const r = toFrame(s.rect);
        return (
          <div key={i} style={{position: 'absolute', ...r, borderRadius: 12, opacity: o, border: `3px solid ${THEME.accent}`,
            boxShadow: s.dim === false ? 'none' : '0 0 0 4000px rgba(2,8,20,0.38)'}} />
        );
      })}
    </>
  );
};

const InsetBody = ({scene}: {scene: Placed}) => {
  const c = scene.console!;
  const t = useCurrentFrame() / FPS;
  const o = Math.min(ramp(t, 0, 0.4), 1 - ramp(t, c.until - c.at - 0.4, c.until - c.at));
  const w = 400, h = Math.round((w * 1080) / 1240);
  return (
    <div style={{position: 'absolute', right: 18, bottom: 18, width: w, opacity: o}}>
      <div style={{fontSize: 17, fontWeight: 700, color: THEME.text, background: 'rgba(5,13,28,0.86)', padding: '6px 12px', borderRadius: '10px 10px 0 0'}}>
        에뮬레이터 차량 입력 · Virtual Console
      </div>
      <div style={{width: w, height: h, overflow: 'hidden', borderRadius: '0 0 10px 10px', border: `1px solid ${THEME.panelBorder}`}}>
        <OffthreadVideo src={staticFile('rec/story_console.mp4')} trimBefore={Math.round((scene.from + c.at * scene.rate) * FPS)}
          playbackRate={scene.rate} muted style={{width: w, height: h}} />
      </div>
    </div>
  );
};

/** The emulator's input page, shown once so it is plain that the car is driven from a console. */
const ConsoleInset = () => (
  <>
    {PLACED.filter((p) => p.console).map((p) => (
      <Sequence key={p.id} from={Math.round((p.start + p.console!.at) * FPS)} durationInFrames={Math.round((p.console!.until - p.console!.at) * FPS)} layout="none">
        <InsetBody scene={p} />
      </Sequence>
    ))}
  </>
);

const SkipLabel = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  if (!scene.skip) return null;
  const o = Math.min(ramp(local, 0.1, 0.4), 1 - ramp(local, 2.2, 2.6));
  return (
    <div style={{position: 'absolute', left: 18, top: 18, opacity: o, fontSize: 22, fontWeight: 700, color: THEME.text,
      background: 'rgba(5,13,28,0.82)', border: `1px solid ${THEME.panelBorder}`, padding: '6px 16px', borderRadius: 18}}>
      {scene.skip}
    </div>
  );
};

const Band = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  const cap = captionAt(scene, local);
  const textIn = ramp(local - cap.at, 0, 0.35);
  const top = FRAME.y + FRAME.h + 18;
  return (
    <div style={{position: 'absolute', left: FRAME.x, width: FRAME.w, top}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 22, fontSize: 21, fontWeight: 600}}>
        {scene.chapter === 0
          ? <span style={{color: THEME.accent, fontWeight: 800}}>모션케어</span>
          : CHAPTERS.map((c, i) => (
            <span key={c} style={{color: i + 1 === scene.chapter ? THEME.accent : THEME.muted, opacity: i + 1 === scene.chapter ? 1 : 0.55,
              fontWeight: i + 1 === scene.chapter ? 800 : 500}}>{c}</span>
          ))}
        <span style={{marginLeft: 'auto', fontSize: 16, color: THEME.muted}}>Pleos Connect 에뮬레이터에서 녹화한 실제 앱 화면</span>
      </div>
      <div style={{opacity: textIn, transform: `translateY(${(1 - textIn) * 6}px)`}}>
        <div style={{fontSize: 40, fontWeight: 800, color: THEME.text, marginTop: 10, display: 'flex', alignItems: 'center', gap: 16}}>
          {cap.title}
          {cap.bell ? <span style={{fontSize: 20, fontWeight: 700, color: '#06203a', background: THEME.accent, borderRadius: 14, padding: '3px 12px'}}>알림음</span> : null}
        </div>
        {cap.sub ? <div style={{fontSize: 25, fontWeight: 500, color: '#c9d6e8', marginTop: 6}}>{cap.sub}</div> : null}
      </div>
    </div>
  );
};

const Main = ({sec}: {sec: number}) => (
  <AbsoluteFill>
    <div style={{position: 'absolute', left: FRAME.x, top: FRAME.y, width: FRAME.w, height: FRAME.h, borderRadius: 12, overflow: 'hidden',
      background: '#000', boxShadow: '0 18px 50px rgba(0,0,0,0.5)', border: `1px solid ${THEME.panelBorder}`}}>
      {PLACED.map((p) => <SceneVideo key={p.id} scene={p} />)}
      <Spots sec={sec} />
      <Taps sec={sec} />
      <ConsoleInset />
      <SkipLabel sec={sec} />
    </div>
    <Band sec={sec} />
  </AbsoluteFill>
);

const LOOP = ['차량 신호로 자동 기록', '주행 중에는 카드 한 장', '도착 후 동승자 평가', '다음 출발 조언'];
const USED = ['차량 속도', '기어', '공조·온도', '위치(바깥 대기질)', '내비 위 알림 카드'];

const Ending = ({sec}: {sec: number}) => {
  const a = ramp(sec, 0.2, 1.0);
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center', opacity: a}}>
      <div style={{display: 'flex', alignItems: 'center', gap: 28}}>
        <Img src={staticFile('story/app_icon.png')} style={{width: 120, height: 120}} />
        <div style={{textAlign: 'left'}}>
          <div style={{fontSize: 84, fontWeight: 900, color: THEME.text, letterSpacing: -1}}>모션케어</div>
          <div style={{fontSize: 34, fontWeight: 500, color: '#c9d6e8'}}>함께 타는 사람까지 편안한 운전</div>
        </div>
      </div>
      <div style={{display: 'flex', alignItems: 'center', gap: 16, marginTop: 60, opacity: ramp(sec, 1.0, 1.8)}}>
        {LOOP.map((f, i) => (
          <React.Fragment key={f}>
            {i > 0 ? <span style={{fontSize: 28, color: THEME.accent}}>→</span> : null}
            <span style={{fontSize: 28, fontWeight: 700, color: THEME.text, padding: '12px 22px', borderRadius: 14, background: THEME.panel,
              border: `1px solid ${THEME.panelBorder}`}}>{f}</span>
          </React.Fragment>
        ))}
      </div>
      <div style={{marginTop: 44, opacity: ramp(sec, 2.2, 3.0)}}>
        <div style={{fontSize: 21, fontWeight: 700, color: THEME.accent}}>영상에서 쓴 Pleos 차량 기능</div>
        <div style={{fontSize: 27, color: THEME.text, marginTop: 10}}>{USED.join('  ·  ')}</div>
      </div>
      <div style={{position: 'absolute', bottom: 46, fontSize: 19, color: THEME.muted, lineHeight: 1.65, opacity: ramp(sec, 2.2, 3.0)}}>
        실제 앱을 Pleos Connect 에뮬레이터(AAOS API 34)에서 녹화했습니다. 차량 신호는 Virtual Console로 입력했고, 실차에서는 확인하지 않았습니다.<br />
        기록 화면의 이전 운행 20건은 시연용으로 만든 기록입니다. 조언은 운전 패턴 빈도의 비교이며, 멀미를 진단하지 않습니다.
      </div>
    </AbsoluteFill>
  );
};

export const Story = () => {
  const frame = useCurrentFrame();
  const sec = frame / FPS;
  const mainIn = ramp(sec, 0, 0.8);
  const mainOut = 1 - ramp(sec, MAIN_END - 0.8, MAIN_END);
  return (
    <AbsoluteFill style={{background: THEME.bg, fontFamily: THEME.font}}>
      <style>{fontFace}</style>
      <AbsoluteFill style={{background: 'radial-gradient(900px 600px at 20% 0%, rgba(30,90,160,0.30), transparent 70%)'}} />
      {sec < MAIN_END ? <AbsoluteFill style={{opacity: Math.min(mainIn, mainOut)}}><Main sec={sec} /></AbsoluteFill> : null}
      {sec >= MAIN_END - 0.2 ? <Ending sec={sec - MAIN_END} /> : null}
      {PLACED.filter((p) => p.audio).map((p) => {
        const frames = Math.round(p.dur * FPS);
        return (
          <Sequence key={p.id} from={Math.round(p.start * FPS)} durationInFrames={frames} layout="none">
            <Audio src={staticFile('rec/story_audio.wav')} trimBefore={Math.round(p.from * FPS)}
              volume={(f) => interpolate(f, [0, 6, frames - 6, frames], [0, 1, 1, 0], clamp)} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};

export const STORY_FRAMES = Math.round(END_SEC * FPS);
