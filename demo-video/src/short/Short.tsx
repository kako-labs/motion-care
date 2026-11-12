import React from 'react';
import {AbsoluteFill, Audio, Easing, OffthreadVideo, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {THEME} from '../config/theme';
import {placeVideo, viewAt} from './camera';
import {DataPanel, MotionPanel} from './Panels';
import {
  BRAND_INTRO_SEC, BRAND_OUTRO_SEC, CHAPTERS, CONSOLE_DELAY_SEC, FPS, MAIN_END, MAIN_START, PLACED, sceneAt, type Placed,
} from './timeline';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const ease = Easing.inOut(Easing.cubic);
const ramp = (t: number, a: number, b: number) => interpolate(t, [a, b], [0, 1], {...clamp, easing: ease});
const lerp = (a: number, b: number, p: number) => a + (b - a) * p;
const fontFace = `@font-face{font-family:'Noto Sans KR';src:url('${staticFile('fonts/NotoSansKR-VF.ttf')}');font-weight:100 900;}`;

type Box = {x: number; y: number; w: number; h: number};
/** 앱 장면: Pleos 화면 하나를 크게 */
const STAGE: Box = {x: 112, y: 112, w: 1696, h: 954};
/** 4분할: 콘솔(1240x1080 비율), Pleos(16:9), 차량 데이터, 움직임 분석 */
const QUAD = {
  console: {x: 80, y: 128, w: 679, h: 592},
  pleos: {x: 785, y: 128, w: 1053, h: 592},
  data: {x: 80, y: 734, w: 679, h: 324},
  motion: {x: 785, y: 734, w: 1053, h: 324},
};
const MORPH_SEC = 0.9;

const SOURCES = {app: 'rec/app_pleos.mp4', drive: 'rec/drive_pleos.mp4'};

/** 4분할 정도 (0 = 앱 장면, 1 = 4분할). 배치가 바뀌는 장면의 처음 MORPH_SEC 동안 옮겨 갑니다. */
const quadAmount = (sec: number) => {
  const {scene, local} = sceneAt(sec);
  const target = scene.layout === 'quad' ? 1 : 0;
  const prev = PLACED[scene.index - 1];
  const from = prev ? (prev.layout === 'quad' ? 1 : 0) : target;
  return lerp(from, target, ramp(local, 0, MORPH_SEC));
};

const rateOf = (s: Placed) => (s.to - s.from) / s.dur;

/** Pleos 화면: 장면마다 녹화본을 이어 붙이고, 카메라로 필요한 영역만 키웁니다. */
const PleosVideo = ({scene, sec, pane}: {scene: Placed; sec: number; pane: Box}) => {
  const view = viewAt(scene.cams, sec - scene.start);
  const pos = placeVideo(view, pane.w);
  return (
    <Sequence from={Math.round(scene.start * FPS)} durationInFrames={Math.round(scene.dur * FPS)} layout="none">
      <OffthreadVideo
        src={staticFile(SOURCES[scene.src])}
        trimBefore={Math.round(scene.from * FPS)}
        playbackRate={rateOf(scene)}
        muted
        style={{position: 'absolute', maxWidth: 'none', ...pos}}
      />
    </Sequence>
  );
};

/** 다음 장면이 앱 테이크면 4분할이 접히는 동안 콘솔이 비지 않게 조금 더 재생합니다. */
const consoleFrames = (scene: Placed) => {
  const next = PLACED[scene.index + 1];
  return Math.round((scene.dur + (next && next.src !== 'drive' ? MORPH_SEC : 0)) * FPS);
};

const ConsoleVideo = ({scene}: {scene: Placed}) => (
  <Sequence from={Math.round(scene.start * FPS)} durationInFrames={consoleFrames(scene)} layout="none">
    <OffthreadVideo
      src={staticFile('rec/drive_console.mp4')}
      trimBefore={Math.max(0, Math.round((scene.from - CONSOLE_DELAY_SEC) * FPS))}
      playbackRate={rateOf(scene)}
      muted
      style={{position: 'absolute', left: 0, top: 0, width: QUAD.console.w, height: QUAD.console.h}}
    />
  </Sequence>
);

const Frame = ({box, children, glow = 0}: {box: Box; children: React.ReactNode; glow?: number}) => (
  <div style={{position: 'absolute', left: box.x, top: box.y, width: box.w, height: box.h, borderRadius: 14, overflow: 'hidden', background: '#000',
    boxShadow: `0 20px 60px rgba(0,0,0,0.55), 0 0 0 1px rgba(127,212,255,${0.25 + 0.5 * glow}), 0 0 ${36 * glow}px rgba(127,212,255,${0.4 * glow})`}}>
    {children}
  </div>
);

const Caption = ({sec}: {sec: number}) => {
  const {scene, local} = sceneAt(sec);
  const prev = PLACED[scene.index - 1];
  const changed = !prev || prev.title !== scene.title;
  const textIn = changed ? interpolate(local, [0, 0.35], [0, 1], clamp) : 1;
  const rate = rateOf(scene);
  return (
    <div style={{position: 'absolute', left: 80, right: 80, top: 24, height: 70, display: 'flex', alignItems: 'center'}}>
      <span style={{fontSize: 23, fontWeight: 800, padding: '8px 20px', borderRadius: 26, color: '#06203a', background: THEME.accent, whiteSpace: 'nowrap'}}>
        {CHAPTERS[scene.chapter]}
      </span>
      <span style={{fontSize: 44, fontWeight: 800, color: THEME.text, marginLeft: 22, whiteSpace: 'nowrap', opacity: textIn, transform: `translateY(${(1 - textIn) * 8}px)`}}>
        {scene.title}
      </span>
      <span style={{marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 14, whiteSpace: 'nowrap'}}>
        {rate >= 1.3 ? (
          <span style={{fontSize: 17, fontWeight: 700, color: THEME.accent, padding: '4px 12px', borderRadius: 14, border: `1px solid ${THEME.panelBorder}`}}>
            {rate.toFixed(1)}배속
          </span>
        ) : null}
        <span style={{fontSize: 15, color: THEME.muted}}>Pleos Connect 에뮬레이터 녹화</span>
      </span>
    </div>
  );
};

const Main = ({sec}: {sec: number}) => {
  const {scene, srcTime} = sceneAt(sec);
  const q = quadAmount(sec);
  const pleos: Box = {
    x: lerp(STAGE.x, QUAD.pleos.x, q), y: lerp(STAGE.y, QUAD.pleos.y, q), w: lerp(STAGE.w, QUAD.pleos.w, q), h: lerp(STAGE.h, QUAD.pleos.h, q),
  };
  const zoomed = scene.cams ? 1 - Math.min(1, viewAt(scene.cams, sec - scene.start).vw / 1920) : 0;
  // 콘솔·차량 데이터 시각 (Pleos 계기판에 맞춤). 앱 테이크로 넘어가며 4분할이 접히는 동안은 직전 주행 장면의 끝 값을 둡니다.
  const lastDrive = [...PLACED.slice(0, scene.index + 1)].reverse().find((p) => p.src === 'drive');
  const t = (scene.src === 'drive' ? srcTime : (lastDrive?.to ?? 0)) - CONSOLE_DELAY_SEC;
  const others = q;
  const slide = (1 - others) * 40;
  return (
    <AbsoluteFill>
      <Caption sec={sec} />
      {others > 0.01 ? (
        <>
          {(['console', 'pleos', 'data', 'motion'] as const).map((k) => (
            <div key={k} style={{position: 'absolute', left: QUAD[k].x + 4, top: QUAD[k].y - 26, fontSize: 16, fontWeight: 600, color: THEME.muted, opacity: others}}>
              {{console: '차량 제어 · Virtual Console', pleos: 'Pleos 화면 · 내비와 모션케어 앱', data: '차량 데이터', motion: '움직임 분석과 안내 기록'}[k]}
            </div>
          ))}
          <div style={{opacity: others, transform: `translateX(${-slide}px)`}}>
            <Frame box={QUAD.console}>
              {PLACED.filter((p) => p.src === 'drive').map((p) => <ConsoleVideo key={p.id} scene={p} />)}
            </Frame>
          </div>
          <div style={{position: 'absolute', left: QUAD.data.x, top: QUAD.data.y, opacity: others, transform: `translateY(${slide}px)`}}>
            <DataPanel t={t} w={QUAD.data.w} h={QUAD.data.h} />
          </div>
          <div style={{position: 'absolute', left: QUAD.motion.x, top: QUAD.motion.y, opacity: others, transform: `translateY(${slide}px)`}}>
            <MotionPanel t={t} w={QUAD.motion.w} h={QUAD.motion.h} />
          </div>
        </>
      ) : null}
      <Frame box={pleos} glow={zoomed}>
        {PLACED.map((p) => <PleosVideo key={p.id} scene={p} sec={sec} pane={pleos} />)}
      </Frame>
    </AbsoluteFill>
  );
};

const BrandIntro = ({sec}: {sec: number}) => {
  const b = Math.min(ramp(sec, 0.3, 1.2), 1 - ramp(sec, BRAND_INTRO_SEC - 0.8, BRAND_INTRO_SEC - 0.1));
  const line = ramp(sec, 1.6, 2.4);
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center', opacity: b}}>
      <div style={{fontSize: 26, fontWeight: 700, color: THEME.muted, letterSpacing: 2, marginBottom: 14}}>차량용 멀미 대응 앱</div>
      <div style={{fontSize: 104, fontWeight: 900, color: THEME.text, letterSpacing: -2}}>모션케어</div>
      <div style={{width: 64, height: 4, borderRadius: 2, background: THEME.accent, margin: '18px auto 30px'}} />
      <div style={{fontSize: 44, fontWeight: 700, color: THEME.accent, marginBottom: 30}}>함께 타는 사람까지 편안한 운전</div>
      <div style={{fontSize: 34, fontWeight: 500, color: '#c9d6e8', lineHeight: 1.55, whiteSpace: 'pre-line', opacity: line}}>
        {'차량 데이터로 운전 패턴과 차 안 공기를 살펴\n주행 중에는 내비 위 카드로, 출발 전에는 지난 기록으로 안내합니다.'}
      </div>
    </AbsoluteFill>
  );
};

const FLOW = ['차량 데이터 기록', '움직임 분석', '동승자 평가', '다음 출발 조언'];
const USED = ['차량 속도', '기어', '공조(내기 순환)', '실외 온도', '위치 기반 대기질', '내비 위 알림 카드'];

const BrandOutro = ({sec}: {sec: number}) => {
  const a = Math.min(ramp(sec, 0.3, 1.2), 1 - ramp(sec, BRAND_OUTRO_SEC - 0.6, BRAND_OUTRO_SEC));
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center', opacity: a}}>
      <div style={{fontSize: 112, fontWeight: 900, color: THEME.text, letterSpacing: -2}}>모션케어</div>
      <div style={{fontSize: 44, fontWeight: 500, color: '#c9d6e8', marginTop: 8}}>함께 타는 사람까지 편안한 운전</div>
      <div style={{display: 'flex', alignItems: 'center', gap: 18, marginTop: 64}}>
        {FLOW.map((f, i) => {
          const o = ramp(sec, 1.4 + i * 0.6, 2.0 + i * 0.6);
          return (
            <React.Fragment key={f}>
              {i > 0 ? <span style={{fontSize: 30, color: THEME.accent, opacity: o}}>→</span> : null}
              <span style={{opacity: o, fontSize: 30, fontWeight: 700, color: THEME.text, padding: '14px 26px', borderRadius: 14, background: THEME.panel, border: `1px solid ${THEME.panelBorder}`}}>{f}</span>
            </React.Fragment>
          );
        })}
      </div>
      <div style={{marginTop: 54, opacity: ramp(sec, 4.2, 5.0)}}>
        <div style={{fontSize: 22, fontWeight: 700, color: THEME.accent}}>영상에서 쓴 Pleos 차량 기능</div>
        <div style={{fontSize: 28, color: THEME.text, marginTop: 12}}>{USED.join('  ·  ')}</div>
      </div>
      <div style={{position: 'absolute', bottom: 50, fontSize: 19, color: THEME.muted, lineHeight: 1.6}}>
        Pleos Connect 에뮬레이터(AAOS API 34)에서 녹화했습니다. 실차에서는 확인하지 않았습니다.<br />
        조언은 운전 패턴 관찰 빈도의 비교이며, 멀미를 진단하지 않습니다.
      </div>
    </AbsoluteFill>
  );
};

export const Short = () => {
  const frame = useCurrentFrame();
  const sec = frame / FPS;
  const fadeIn = ramp(sec, MAIN_START, MAIN_START + 0.6);
  const fadeOut = 1 - ramp(sec, MAIN_END - 0.6, MAIN_END);
  return (
    <AbsoluteFill style={{background: THEME.bg, fontFamily: THEME.font}}>
      <style>{fontFace}</style>
      <AbsoluteFill style={{background: 'radial-gradient(900px 600px at 22% 8%, rgba(30,90,160,0.38), transparent 70%), radial-gradient(1000px 700px at 85% 100%, rgba(20,70,140,0.30), transparent 70%)'}} />
      {sec < BRAND_INTRO_SEC ? <BrandIntro sec={sec} /> : null}
      {sec >= MAIN_START && sec < MAIN_END ? (
        <AbsoluteFill style={{opacity: Math.min(fadeIn, fadeOut)}}>
          <Main sec={sec} />
        </AbsoluteFill>
      ) : null}
      {sec >= MAIN_END ? <BrandOutro sec={sec - MAIN_END} /> : null}
      {PLACED.filter((p) => p.audio).map((p) => {
        const frames = Math.round(p.dur * FPS);
        return (
          <Sequence key={p.id} from={Math.round(p.start * FPS)} durationInFrames={frames} layout="none">
            <Audio src={staticFile('rec/drive_audio.wav')} trimBefore={Math.round(p.from * FPS)}
              volume={(f) => interpolate(f, [0, 6, frames - 6, frames], [0, 1, 1, 0], clamp)} />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};
