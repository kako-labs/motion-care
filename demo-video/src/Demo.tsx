import React from 'react';
import {AbsoluteFill, Audio, Easing, Sequence, interpolate, staticFile, useCurrentFrame} from 'remotion';
import {Analysis} from './components/Analysis';
import {Source} from './components/Source';
import {COPY} from './config/copy';
import {BIG, CAPTION, CROP, FULL, HOME, INTRO_STAGES, MOTION} from './config/layout';
import {THEME} from './config/theme';
import {
  BRAND_INTRO_SEC, BRAND_OUTRO_SEC, CHAPTERS, FPS, PLACED, QUAD_END_SEC, QUAD_START_SEC, TOTAL_SEC,
  segById, stateAt, type PaneId,
} from './config/timeline';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const ease = Easing.inOut(Easing.cubic);
const ramp = (t: number, a: number, b: number) => interpolate(t, [a, b], [0, 1], {...clamp, easing: ease});
const fontFace = `@font-face{font-family:'Noto Sans KR';src:url('${staticFile('fonts/NotoSansKR-VF.ttf')}');font-weight:100 900;}`;

// ───────── 화면 위치 계산 ─────────
type Pose = {x: number; y: number; scale: number; opacity: number; lift: number; glow: number; zoom: number; origin: [number, number]};

const stageOf = (pane: string) => INTRO_STAGES.find((s) => s.pane === pane)!;

/** full 로 지정된 화면이 지금 얼마나 커져 있는지 (0 = 제자리, 1 = 전체화면) */
const fullAmount = (pane: PaneId, sec: number) => {
  const {seg, local} = stateAt(sec);
  if (seg.full !== pane) return 0;
  const before = PLACED[seg.index - 1]?.full === pane;
  const after = PLACED[seg.index + 1]?.full === pane;
  const grow = before ? 1 : ramp(local, 0, MOTION.full);
  const shrink = after ? 1 : 1 - ramp(local, seg.dur - MOTION.full, seg.dur);
  return Math.min(grow, shrink);
};

/** 지금 전체화면으로 커진 화면이 있으면 그 정도 (다른 화면을 숨기는 데 씀) */
const anyFull = (sec: number) => Math.max(fullAmount('console', sec), fullAmount('pleos', sec), fullAmount('data', sec));

/** 화면 하나의 위치·크기: 소개 때는 크게 → 제자리, 본편에서는 focus 때만 잠깐 확대 */
const poseOf = (pane: PaneId, sec: number): Pose => {
  const home = HOME[pane];
  const seg = segById(stageOf(pane).seg);
  const opacity = ramp(sec, seg.start, seg.start + MOTION.appear);
  const settle = ramp(sec, seg.end - MOTION.shrink, seg.end); // 0 = 크게, 1 = 제자리
  const big = BIG[pane];
  const full = fullAmount(pane, sec);
  const hide = anyFull(sec) - full; // 다른 화면이 전체화면이면 이 화면은 숨김
  const x = interpolate(full, [0, 1], [interpolate(settle, [0, 1], [big.x, home.x]), FULL[pane].x]);
  const y = interpolate(full, [0, 1], [interpolate(settle, [0, 1], [big.y, home.y]), FULL[pane].y]);
  const scale = interpolate(full, [0, 1], [interpolate(settle, [0, 1], [big.scale, 1]), FULL[pane].scale]);
  const lift = Math.max(1 - settle, full);
  let glow = 0;
  let zoom = 1;
  let origin: [number, number] = [50, 50];

  const {seg: cur, local} = stateAt(sec);
  if (cur.focus && cur.focus.pane === pane) {
    const {from, to} = cur.focus;
    const f = Math.min(ramp(local, from, from + MOTION.focus), 1 - ramp(local, to - MOTION.focus, to));
    glow = f;
    zoom = interpolate(f, [0, 1], [1, cur.focus.zoom]);
    origin = cur.focus.origin;
  }
  return {x, y, scale, opacity: opacity * (1 - hide), lift, glow, zoom, origin};
};

const Pane = ({pose, w, h, children}: {pose: Pose; w: number; h: number; children: React.ReactNode}) => (
  <div
    style={{
      position: 'absolute', left: 0, top: 0, width: w, height: h, transformOrigin: '0 0',
      transform: `translate(${pose.x}px, ${pose.y}px) scale(${pose.scale})`,
      opacity: pose.opacity, zIndex: pose.lift > 0.01 ? 10 : 1,
    }}
  >
    <div
      style={{
        position: 'absolute', inset: -1, borderRadius: 15, pointerEvents: 'none',
        boxShadow: `0 ${30 * pose.lift}px ${90 * pose.lift}px rgba(0,0,0,${0.75 * pose.lift}), 0 0 0 ${1 + pose.lift + 2 * pose.glow}px rgba(127,212,255,${0.2 + 0.5 * pose.lift + 0.7 * pose.glow}), 0 0 ${40 * pose.glow}px rgba(127,212,255,${0.45 * pose.glow})`,
      }}
    />
    {children}
  </div>
);

// ───────── 4분할 화면 ─────────
const Quad = ({sec}: {sec: number}) => {
  const {seg, local, srcTime} = stateAt(sec);
  const quadFrames = Math.round((QUAD_END_SEC - QUAD_START_SEC) * FPS);
  const srcFrame = Math.min(quadFrames - 1, Math.max(0, Math.round((sec - QUAD_START_SEC) * FPS))); // public/remap 의 프레임
  const poses = {console: poseOf('console', sec), pleos: poseOf('pleos', sec), data: poseOf('data', sec)};

  // 장면이 끊기는 곳(dip)에서 잠깐 어둡게
  const dipSeg = PLACED.find((p) => p.dip && Math.abs(sec - p.start) < 0.4);
  const dim = dipSeg ? interpolate(Math.abs(sec - dipSeg.start), [0, 0.4], [0.15, 1], clamp) : 1;

  const anaSeg = segById('intro-caption');
  const anaIn = ramp(sec, anaSeg.start, anaSeg.start + 0.9);
  const headerIn = ramp(sec, QUAD_START_SEC + 0.2, QUAD_START_SEC + 1);
  // 맨 위 한 줄: 문장이 바뀔 때만 살짝 떠오르게
  const prev = PLACED[seg.index - 1];
  const titleChanged = !prev || prev.title !== seg.title;
  const textIn = titleChanged ? interpolate(local, [0, 0.35], [0, 1], clamp) : 1;

  const logH = (HOME.analysis.w * CROP.cardlog.h) / CROP.cardlog.w;
  const others = 1 - anyFull(sec); // 한 화면이 전체화면일 때 나머지를 숨김

  return (
    <AbsoluteFill>
      {/* 맨 위 한 줄: 지금 무엇을 하는지 */}
      <div style={{position: 'absolute', left: HOME.console.x, right: 1920 - HOME.pleos.x - HOME.pleos.w, top: CAPTION.top, height: CAPTION.height, display: 'flex', alignItems: 'center', opacity: headerIn}}>
        <span style={{fontSize: CAPTION.chipSize, fontWeight: 800, padding: '8px 20px', borderRadius: 26, color: '#06203a', background: THEME.accent, whiteSpace: 'nowrap'}}>
          {CHAPTERS[seg.chapter]}
        </span>
        <span style={{fontSize: CAPTION.fontSize, fontWeight: 800, color: THEME.text, marginLeft: 22, whiteSpace: 'nowrap', opacity: textIn, transform: `translateY(${(1 - textIn) * 8}px)`}}>
          {seg.title}
        </span>
        <span style={{marginLeft: 'auto', fontSize: 15, color: THEME.muted, whiteSpace: 'nowrap'}}>{COPY.header.note}</span>
      </div>

      {/* 화면 이름표 (제자리에 있을 때만) */}
      {(['console', 'pleos'] as const).map((p) => (
        <div
          key={p}
          style={{
            position: 'absolute', left: HOME[p].x + 4, top: HOME[p].y - 27, fontSize: 16, fontWeight: 600, color: THEME.muted,
            opacity: poses[p].opacity * (1 - Math.min(1, poses[p].lift * 3)) * others,
          }}
        >
          {COPY.paneLabels[p]}
        </div>
      ))}

      <div style={{position: 'absolute', inset: 0, opacity: dim}}>
        <Pane pose={poses.console} w={HOME.console.w} h={HOME.console.h}>
          <Source crop={CROP.console} width={HOME.console.w} srcFrame={srcFrame} radius={14} zoom={poses.console.zoom} origin={poses.console.origin} />
        </Pane>

        <Pane pose={poses.pleos} w={HOME.pleos.w} h={HOME.pleos.h}>
          <Source crop={CROP.pleos} width={HOME.pleos.w} srcFrame={srcFrame} radius={14} zoom={poses.pleos.zoom} origin={poses.pleos.origin} />
        </Pane>

        <Pane pose={poses.data} w={HOME.data.w} h={HOME.data.h}>
          <Source crop={CROP.data} width={HOME.data.w} srcFrame={srcFrame} radius={14} zoom={poses.data.zoom} origin={poses.data.origin} />
        </Pane>
      </div>

      {/* 오른쪽 아래: 움직임 분석 + 내비 위 카드 기록 */}
      <div
        style={{
          position: 'absolute', left: HOME.analysis.x, top: HOME.analysis.y, width: HOME.analysis.w, height: HOME.analysis.h,
          opacity: anaIn * others, transform: `translateY(${(1 - anaIn) * 60}px)`,
        }}
      >
        <Analysis srcSec={srcTime} height={HOME.analysis.h - logH - 10} />
        <div style={{height: 10}} />
        <div style={{opacity: dim}}>
          <Source crop={CROP.cardlog} width={HOME.analysis.w} srcFrame={srcFrame} radius={14} />
        </div>
      </div>

      {/* 화면 소개 때의 설명 글 */}
      {INTRO_STAGES.map((st, i) => {
        if (!st.note) return null;
        const s = segById(st.seg);
        const o = Math.min(ramp(sec, s.start + 0.5, s.start + 1.1), 1 - ramp(sec, s.end - MOTION.shrink - 0.3, s.end - MOTION.shrink + 0.2));
        if (o <= 0) return null;
        return (
          <div key={st.seg} style={{position: 'absolute', left: st.note.x, top: st.note.y, width: st.note.w, opacity: o, zIndex: 20}}>
            <div style={{fontSize: 20, fontWeight: 700, color: THEME.accent}}>
              {COPY.introStepPrefix} {i + 1}/{INTRO_STAGES.length}
            </div>
            <div style={{fontSize: 44, fontWeight: 800, color: THEME.text, lineHeight: 1.25, marginTop: 10, wordBreak: 'keep-all'}}>{s.title}</div>
            <div style={{fontSize: 25, color: '#c9d6e8', lineHeight: 1.5, marginTop: 16, wordBreak: 'keep-all'}}>{s.sub}</div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

// ───────── 브랜드 소개 · 마무리 ─────────
const BrandIntro = ({sec}: {sec: number}) => {
  const c = COPY.brandIntro;
  const b = Math.min(ramp(sec, 0.4, 1.3), 1 - ramp(sec, BRAND_INTRO_SEC - 0.8, BRAND_INTRO_SEC - 0.1));
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center'}}>
      <div style={{position: 'absolute', opacity: b, transform: `translateY(${(1 - b) * 14}px)`}}>
        <div style={{fontSize: 104, fontWeight: 900, color: THEME.text, letterSpacing: -2}}>{c.name}</div>
        <div style={{width: 64, height: 4, borderRadius: 2, background: THEME.accent, margin: '18px auto 30px'}} />
        <div style={{fontSize: 44, fontWeight: 700, color: THEME.accent, marginBottom: 26}}>{c.tag}</div>
        <div style={{fontSize: 36, fontWeight: 500, color: '#c9d6e8', lineHeight: 1.5, whiteSpace: 'pre-line'}}>{c.line2}</div>
      </div>
    </AbsoluteFill>
  );
};

const BrandOutro = ({sec}: {sec: number}) => {
  const c = COPY.brandOutro;
  const a = ramp(sec, 0.3, 1.2);
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'center', textAlign: 'center', opacity: Math.min(a, 1 - ramp(sec, BRAND_OUTRO_SEC - 0.6, BRAND_OUTRO_SEC))}}>
      <div style={{fontSize: 112, fontWeight: 900, color: THEME.text, letterSpacing: -2}}>{c.name}</div>
      <div style={{fontSize: 44, fontWeight: 500, color: '#c9d6e8', marginTop: 8}}>{c.tagline}</div>
      <div style={{display: 'flex', alignItems: 'center', gap: 18, marginTop: 70}}>
        {c.flow.map((f, i) => {
          const o = ramp(sec, 1.6 + i * 0.7, 2.2 + i * 0.7);
          return (
            <React.Fragment key={f}>
              {i > 0 ? <span style={{fontSize: 30, color: THEME.accent, opacity: o}}>→</span> : null}
              <span
                style={{
                  opacity: o, fontSize: 30, fontWeight: 700, color: THEME.text, padding: '14px 26px', borderRadius: 14,
                  background: THEME.panel, border: `1px solid ${THEME.panelBorder}`,
                }}
              >
                {f}
              </span>
            </React.Fragment>
          );
        })}
      </div>
      <div style={{position: 'absolute', bottom: 54, fontSize: 19, color: THEME.muted}}>{c.footnote}</div>
    </AbsoluteFill>
  );
};

// ───────── 전체 ─────────
export const Demo = () => {
  const frame = useCurrentFrame();
  const sec = frame / FPS;
  return (
    <AbsoluteFill style={{background: THEME.bg, fontFamily: THEME.font}}>
      <style>{fontFace}</style>
      <AbsoluteFill
        style={{
          background:
            'radial-gradient(900px 600px at 22% 8%, rgba(30,90,160,0.38), transparent 70%), radial-gradient(1000px 700px at 85% 100%, rgba(20,70,140,0.30), transparent 70%)',
        }}
      />
      {sec < BRAND_INTRO_SEC ? <BrandIntro sec={sec} /> : null}
      {sec >= QUAD_START_SEC && sec < QUAD_END_SEC ? (
        <AbsoluteFill style={{opacity: 1 - ramp(sec, QUAD_END_SEC - 0.5, QUAD_END_SEC)}}>
          <Quad sec={sec} />
        </AbsoluteFill>
      ) : null}
      {sec >= QUAD_END_SEC ? <BrandOutro sec={sec - QUAD_END_SEC} /> : null}

      {/* 원본 소리(알림음·음성)는 audio: true 인 구간에서만 */}
      {PLACED.filter((p) => p.audio).map((p) => {
        const frames = Math.round(p.dur * FPS);
        return (
          <Sequence key={p.id} from={Math.round(p.start * FPS)} durationInFrames={frames} layout="none">
            <Audio
              src={staticFile('source.mp4')}
              trimBefore={Math.round(p.src[0] * FPS)}
              volume={(f) => interpolate(f, [0, 6, frames - 6, frames], [0, 1, 1, 0], clamp)}
            />
          </Sequence>
        );
      })}
    </AbsoluteFill>
  );
};

export const _totalSec = TOTAL_SEC;
