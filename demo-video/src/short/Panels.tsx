import drive from '../data/drive.json';
import {THEME} from '../config/theme';

/**
 * 주행 테이크의 콘솔 값(0.1초 간격). 콘솔 페이지가 앱에 보낸 속도·페달·조향·기어를 그대로 기록한 것입니다.
 * 가감속과 좌우 움직임은 앱 문서의 계산식으로 다시 계산합니다(docs/detection-thresholds.md).
 */
type Sample = {t: number; v: number; a: number; b: number; s: number; g: string};
const TELE = drive.telemetry as Sample[];
const STEERING_RATIO = 14.26;
const WHEELBASE_M = 3.0;
const ACCEL_LIMIT = 2.0;
const LATERAL_LIMIT = 0.9;

const sampleAt = (t: number): Sample => {
  let lo = 0;
  let hi = TELE.length - 1;
  if (t <= TELE[0].t) return TELE[0];
  if (t >= TELE[hi].t) return TELE[hi];
  while (hi - lo > 1) {
    const mid = (lo + hi) >> 1;
    if (TELE[mid].t <= t) lo = mid;
    else hi = mid;
  }
  return TELE[lo];
};

const motionAt = (t: number) => {
  const v = sampleAt(t).v / 3.6;
  const vPrev = sampleAt(t - 1).v / 3.6;
  const steer = sampleAt(t).s;
  return {accel: v - vPrev, lateral: (v * v * Math.tan(((steer / STEERING_RATIO) * Math.PI) / 180)) / WHEELBASE_M};
};

const recircAt = (t: number) => {
  const changes = drive.hvac.filter((h) => h.t <= t);
  return changes.length ? changes[changes.length - 1].recirc : 1;
};

const box = {boxSizing: 'border-box', borderRadius: 14, background: THEME.panel, border: '1px solid ' + THEME.panelBorder} as const;
const label = {fontSize: 15, color: THEME.muted, fontWeight: 600} as const;

const Bar = ({value, color}: {value: number; color: string}) => (
  <div style={{position: 'relative', height: 10, borderRadius: 5, background: 'rgba(140,170,210,0.18)', flex: 1}}>
    <div style={{position: 'absolute', left: 0, top: 0, bottom: 0, width: Math.min(100, value) + '%', borderRadius: 5, background: color}} />
  </div>
);

/** 왼쪽 아래: 지금 차량 상태 (콘솔 값) */
export const DataPanel = ({t, w, h}: {t: number; w: number; h: number}) => {
  const x = sampleAt(t);
  const recirc = recircAt(t);
  return (
    <div style={{...box, width: w, height: h, padding: '16px 22px', display: 'flex', flexDirection: 'column', gap: 12}}>
      <div style={{display: 'flex', alignItems: 'flex-end', gap: 26}}>
        <div>
          <div style={label}>차량 속도</div>
          <div style={{display: 'flex', alignItems: 'baseline', gap: 6}}>
            <span style={{fontSize: 62, fontWeight: 800, color: THEME.text, lineHeight: 1, fontVariantNumeric: 'tabular-nums', minWidth: 88, display: 'inline-block'}}>
              {Math.round(x.v)}
            </span>
            <span style={{fontSize: 20, color: THEME.muted}}>km/h</span>
          </div>
        </div>
        <div>
          <div style={label}>기어</div>
          <div style={{display: 'flex', gap: 6, marginTop: 8}}>
            {['P', 'R', 'N', 'D'].map((g) => (
              <span key={g} style={{
                width: 34, height: 34, borderRadius: 8, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', fontSize: 20, fontWeight: 800,
                color: g === x.g ? '#06203a' : THEME.muted, background: g === x.g ? THEME.accent : 'rgba(140,170,210,0.14)',
              }}>{g}</span>
            ))}
          </div>
        </div>
        <div>
          <div style={label}>핸들</div>
          <div style={{display: 'flex', alignItems: 'center', gap: 8, marginTop: 6}}>
            <svg width={34} height={34} viewBox="0 0 34 34" style={{transform: `rotate(${x.s}deg)`}}>
              <circle cx={17} cy={17} r={14} fill="none" stroke={THEME.text} strokeWidth={3} />
              <path d="M3 17 H31 M17 17 V31" stroke={THEME.text} strokeWidth={3} />
            </svg>
            <span style={{fontSize: 22, fontWeight: 700, color: THEME.text, fontVariantNumeric: 'tabular-nums'}}>{Math.round(x.s)}°</span>
          </div>
        </div>
      </div>
      <div style={{display: 'flex', gap: 22}}>
        <div style={{flex: 1, display: 'flex', alignItems: 'center', gap: 10}}>
          <span style={{...label, width: 64, whiteSpace: 'nowrap'}}>가속 페달</span>
          <Bar value={x.a} color={THEME.good} />
          <span style={{fontSize: 16, color: THEME.text, width: 44, textAlign: 'right', fontVariantNumeric: 'tabular-nums'}}>{Math.round(x.a)}%</span>
        </div>
        <div style={{flex: 1, display: 'flex', alignItems: 'center', gap: 10}}>
          <span style={{...label, width: 64, whiteSpace: 'nowrap'}}>브레이크</span>
          <Bar value={x.b} color={THEME.warn} />
          <span style={{fontSize: 16, color: THEME.text, width: 44, textAlign: 'right', fontVariantNumeric: 'tabular-nums'}}>{Math.round(x.b)}%</span>
        </div>
      </div>
      <div style={{display: 'flex', gap: 24, fontSize: 17, color: THEME.text}}>
        <span><span style={label}>공기 흐름 </span><b style={{color: recirc ? THEME.text : THEME.warn}}>{recirc ? '내기 순환' : '외기 유입'}</b></span>
        <span><span style={label}>운전석 설정 </span>23°C</span>
        <span><span style={label}>바깥 대기질 </span><b style={{color: THEME.warn}}>나쁨</b></span>
      </div>
      <div style={{fontSize: 13, color: THEME.muted}}>Virtual Console 입력값 · 대기질은 앱이 받은 Open-Meteo 예측</div>
    </div>
  );
};

const W = 480;
const H = 70;

const Gauge = ({name, hint, pick, t, limit, range}: {name: string; hint: string; pick: 'accel' | 'lateral'; t: number; limit: number; range: number}) => {
  const value = motionAt(t)[pick];
  const over = Math.abs(value) >= limit;
  const color = over ? THEME.warn : THEME.good;
  const shown = Math.abs(value) < 0.05 ? '0.0' : (value > 0 ? '+' : '−') + Math.abs(value).toFixed(1);
  const y = (v: number) => H / 2 - (Math.max(-range, Math.min(range, v)) / range) * (H / 2 - 3);
  const n = 80;
  const points = Array.from({length: n + 1}, (_, i) => {
    const tt = t - 8 + i / 10;
    return ((i / n) * W).toFixed(1) + ',' + y(motionAt(tt)[pick]).toFixed(1);
  }).join(' ');
  return (
    <div style={{flex: 1, minWidth: 0}}>
      <div style={{display: 'flex', alignItems: 'baseline', gap: 10, lineHeight: 1.2}}>
        <span style={{fontSize: 22, fontWeight: 700, color: THEME.text}}>{name}</span>
        <span style={{fontSize: 14, color: THEME.muted}}>{hint}</span>
        <span style={{marginLeft: 'auto', fontSize: 28, fontWeight: 800, color, fontVariantNumeric: 'tabular-nums'}}>
          {shown}<span style={{fontSize: 14, fontWeight: 500, color: THEME.muted}}> m/s²</span>
        </span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" style={{width: '100%', height: H, marginTop: 8, display: 'block'}}>
        <line x1={0} x2={W} y1={y(0)} y2={y(0)} stroke="rgba(255,255,255,0.25)" strokeWidth={1} />
        {[limit, -limit].map((l) => (
          <line key={l} x1={0} x2={W} y1={y(l)} y2={y(l)} stroke="rgba(255,154,107,0.6)" strokeWidth={1} strokeDasharray="5 5" />
        ))}
        <polyline points={points} fill="none" stroke={THEME.accent} strokeWidth={2.5} strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      </svg>
    </div>
  );
};

/** 운행이 시작된 시각(주행 테이크 기준): 카드 기록은 운행마다 새로 셉니다. */
const TRIP_STARTS = [drive.marks['gear-d'], drive.marks['air-gear']];

/** 오른쪽 아래: 움직임 분석과 이번 운행의 내비 위 카드 */
export const MotionPanel = ({t, w, h}: {t: number; w: number; h: number}) => {
  const tripStart = Math.max(...TRIP_STARTS.filter((s) => s <= t), 0);
  const cards = drive.cards.filter((c) => c.t >= tripStart && c.t <= t);
  const last = cards[cards.length - 1];
  return (
    <div style={{width: w, height: h, display: 'flex', flexDirection: 'column', gap: 10}}>
      <div style={{...box, flex: 1, padding: '12px 22px 0'}}>
        <div style={{display: 'flex', alignItems: 'baseline', gap: 12, lineHeight: 1.2}}>
          <span style={{fontSize: 17, fontWeight: 700, color: THEME.accent}}>움직임 분석</span>
          <span style={{fontSize: 13, color: THEME.muted}}>콘솔 입력값으로 다시 계산 · 주황 점선은 앱이 움직임을 구분하는 기준 (가감속 2.0, 좌우 0.9 m/s²)</span>
        </div>
        <div style={{display: 'flex', gap: 34, marginTop: 8}}>
          <Gauge name="가감속" hint="1초 동안의 속도 변화" pick="accel" t={t} limit={ACCEL_LIMIT} range={5} />
          <Gauge name="좌우 움직임" hint="조향각과 속도로 추정" pick="lateral" t={t} limit={LATERAL_LIMIT} range={3} />
        </div>
      </div>
      <div style={{...box, height: 64, padding: '0 22px', display: 'flex', alignItems: 'center', gap: 16}}>
        <span style={{fontSize: 15, color: THEME.muted, whiteSpace: 'nowrap'}}>내비 위 카드 · 이번 운행 {cards.length}번</span>
        <span style={{fontSize: 21, fontWeight: 700, color: last ? THEME.text : THEME.muted, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis'}}>
          {last ? `“${last.text}”` : '아직 없음'}
        </span>
      </div>
    </div>
  );
};
