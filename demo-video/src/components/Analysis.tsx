import telemetry from '../data/telemetry.json';
import {COPY} from '../config/copy';
import {THEME} from '../config/theme';

/**
 * 앱과 같은 계산식으로 구한 값 (motion-care/docs/detection-thresholds.md)
 * - 가감속 = 1초 동안의 속도 변화
 * - 좌우 움직임 = 속도² × tan(조향각 ÷ 조향비) ÷ 축거  (추정값)
 */
const STEERING_RATIO = 14.26;
const WHEELBASE_M = 3.0;
const ACCEL_LIMIT = 2.0; // 급출발·급정거 구분선 (m/s²)
const LATERAL_LIMIT = 0.9; // 반복 좌우 움직임 구분선 (m/s²)
const HISTORY_SEC = 8; // 그래프에 보여줄 지난 시간(원본 영상 기준 초)

const at = (arr: number[], srcSec: number) => arr[Math.min(arr.length - 1, Math.max(0, Math.round(srcSec * telemetry.fps)))];

export const motionAt = (srcSec: number) => {
  const v = at(telemetry.speed, srcSec) / 3.6;
  const vPrev = at(telemetry.speed, srcSec - 1) / 3.6;
  const steer = at(telemetry.steer, srcSec);
  const accel = v - vPrev;
  const lateral = (v * v * Math.tan((steer / STEERING_RATIO) * (Math.PI / 180))) / WHEELBASE_M;
  return {accel, lateral};
};

const W = 480;
const H = 74;

const Gauge = ({label, hint, pick, srcSec, limit, range}: {
  label: string; hint: string; pick: 'accel' | 'lateral'; srcSec: number; limit: number; range: number;
}) => {
  const value = motionAt(srcSec)[pick];
  const over = Math.abs(value) >= limit;
  const color = over ? THEME.warn : THEME.good;
  const half = (Math.min(Math.abs(value), range) / range) * 50;
  const sign = value > 0 ? '+' : '−';
  const shown = Math.abs(value) < 0.05 ? '0.0' : sign + Math.abs(value).toFixed(1);
  const y = (v: number) => H / 2 - (Math.max(-range, Math.min(range, v)) / range) * (H / 2 - 3);
  const n = HISTORY_SEC * 10;
  const points = Array.from({length: n + 1}, (_, i) => {
    const t = srcSec - HISTORY_SEC + i / 10;
    return ((i / n) * W).toFixed(1) + ',' + y(motionAt(t)[pick]).toFixed(1);
  }).join(' ');
  return (
    <div style={{flex: 1, minWidth: 0}}>
      <div style={{display: 'flex', alignItems: 'baseline', gap: 10, lineHeight: 1.2}}>
        <span style={{fontSize: 23, fontWeight: 700, color: THEME.text}}>{label}</span>
        <span style={{fontSize: 15, color: THEME.muted}}>{hint}</span>
        <span style={{marginLeft: 'auto', fontSize: 30, fontWeight: 800, color, fontVariantNumeric: 'tabular-nums'}}>
          {shown}
          <span style={{fontSize: 15, fontWeight: 500, color: THEME.muted}}> m/s²</span>
        </span>
      </div>
      <div style={{position: 'relative', height: 12, marginTop: 8, borderRadius: 6, background: 'rgba(140,170,210,0.18)'}}>
        <div
          style={{
            position: 'absolute', top: 0, height: 12, borderRadius: 6, background: color,
            left: value >= 0 ? '50%' : 50 - half + '%', width: half + '%',
          }}
        />
        {[-1, 0, 1].map((s) => (
          <div
            key={s}
            style={{
              position: 'absolute', top: -3, height: 18, width: 2, marginLeft: -1, left: 50 + s * (limit / range) * 50 + '%',
              background: s === 0 ? 'rgba(255,255,255,0.55)' : 'rgba(255,154,107,0.8)',
            }}
          />
        ))}
      </div>
      <svg viewBox={'0 0 ' + W + ' ' + H} preserveAspectRatio="none" style={{width: '100%', height: H, marginTop: 10, display: 'block'}}>
        <line x1={0} x2={W} y1={y(0)} y2={y(0)} stroke="rgba(255,255,255,0.25)" strokeWidth={1} />
        {[limit, -limit].map((l) => (
          <line key={l} x1={0} x2={W} y1={y(l)} y2={y(l)} stroke="rgba(255,154,107,0.6)" strokeWidth={1} strokeDasharray="5 5" />
        ))}
        <polyline points={points} fill="none" stroke={THEME.accent} strokeWidth={2.5} strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      </svg>
    </div>
  );
};

export const Analysis = ({srcSec, height}: {srcSec: number; height: number}) => (
  <div style={{height, boxSizing: 'border-box', padding: '14px 22px 0', borderRadius: 14, background: THEME.panel, border: '1px solid ' + THEME.panelBorder}}>
    <div style={{display: 'flex', alignItems: 'baseline', gap: 12, lineHeight: 1.2}}>
      <span style={{fontSize: 18, fontWeight: 700, color: THEME.accent}}>{COPY.analysis.title}</span>
      <span style={{fontSize: 14, color: THEME.muted}}>
        {COPY.analysis.tag} (가감속 {ACCEL_LIMIT.toFixed(1)}, 좌우 {LATERAL_LIMIT.toFixed(1)} m/s²)
      </span>
    </div>
    <div style={{display: 'flex', gap: 34, marginTop: 10}}>
      <Gauge label={COPY.analysis.accel} hint={COPY.analysis.accelHint} pick="accel" srcSec={srcSec} limit={ACCEL_LIMIT} range={5} />
      <Gauge label={COPY.analysis.lateral} hint={COPY.analysis.lateralHint} pick="lateral" srcSec={srcSec} limit={LATERAL_LIMIT} range={3} />
    </div>
  </div>
);
