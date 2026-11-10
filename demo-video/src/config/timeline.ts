/**
 * 영상 타임라인. 이 파일만 고치면 순서·길이·자막이 바뀝니다.
 *
 * - src: 원본 6분 영상(public/source.mp4)에서 가져올 구간 [시작초, 끝초]
 * - dur: 새 영상에서 이 구간이 차지하는 시간(초). src 길이보다 짧으면 그만큼 빨리 재생됩니다.
 * - title: 맨 위 한 줄에 나오는 문장 (지금 무엇을 하는지)
 * - sub: 화면 소개 구간에서만 쓰는 설명 한 줄 (본편에서는 화면에 나오지 않음)
 * - audio: true 면 원본 소리(알림음·음성)를 그대로 넣습니다. 1배속 구간에만 쓰세요.
 * - focus: 특정 화면의 안쪽을 잠깐 확대. from/to 는 이 구간 안에서의 초, zoom 은 배율,
 *          origin 은 확대 기준점 [가로%, 세로%] (0,0 = 왼쪽 위). 화면 자리와 크기는 그대로입니다.
 * - full: 이 구간 동안 한 화면만 전체화면으로 크게 (다른 화면은 숨김). 예: full: 'pleos'
 * - dip: 구간 시작에서 화면을 잠깐 어둡게 했다가 밝힘 (장면이 끊기는 곳에 사용)
 */
export type PaneId = 'console' | 'pleos' | 'data';

export type Segment = {
  id: string;
  chapter: number; // CHAPTERS 의 인덱스
  src: [number, number];
  dur: number;
  title: string;
  sub?: string;
  audio?: boolean;
  dip?: boolean;
  full?: PaneId;
  focus?: {pane: PaneId; from: number; to: number; zoom: number; origin: [number, number]};
};

export const FPS = 30;

/** 맨 앞 브랜드 소개, 맨 뒤 브랜드 마무리 길이(초) */
export const BRAND_INTRO_SEC = 6.5;
export const BRAND_OUTRO_SEC = 9;

export const CHAPTERS = [
  '화면 소개',
  '1 메인 화면',
  '2 안내 설정',
  '3 운행 1 · 알림음',
  '4 운행 2 · 음성',
  '5 운행 3 · 환경',
  '6 운행 기록',
  '7 알림 꺼짐',
];

export const SEGMENTS: Segment[] = [
  // ───────── 화면 소개 (주행 중인 차량으로 네 화면을 하나씩 소개) ─────────
  // 아래 네 구간의 id 는 화면 등장 연출과 연결되어 있으니 바꾸지 마세요 (layout.ts 의 INTRO_STAGES)
  {
    id: 'intro-console',
    chapter: 0,
    src: [84.0, 88.5],
    dur: 9,
    title: 'Pleos Virtual Console',
    sub: '차량 상태를 입력하고 확인하는 공식 도구 화면',
  },
  {
    id: 'intro-pleos',
    chapter: 0,
    src: [88.5, 92.5],
    dur: 8,
    title: 'Pleos 화면',
    sub: '모션케어 앱과 주행 중 안내가 표시되는 화면',
  },
  {
    id: 'intro-data',
    chapter: 0,
    src: [92.5, 97.0],
    dur: 9,
    title: '차량 데이터',
    sub: '차량에서 받은 속도, 기어, 조향각, 공조 값',
  },
  {
    id: 'intro-caption',
    chapter: 0,
    src: [97.0, 100.5],
    dur: 4.5,
    title: '움직임 분석과 안내 기록',
    sub: '받은 값으로 계산한 가감속·좌우 움직임과, 주행 중에 띄운 안내',
  },
  {
    id: 'intro-stop',
    chapter: 0,
    src: [100.5, 104.5],
    dur: 4,
    title: '화면 소개를 마치고 차량을 정지합니다',
  },
  {
    id: 'intro-reset',
    chapter: 0,
    src: [6.0, 8.2],
    dur: 3,
    dip: true,
    title: '이제 실제 모션케어 이용 과정을 시작합니다',
  },

  // ───────── 1 메인 화면 ─────────
  {
    id: 'main-home',
    full: 'pleos',
    chapter: 1,
    src: [8.2, 15.0],
    dur: 5,
    title: '메인 화면',
  },
  {
    id: 'main-seat',
    full: 'pleos',
    chapter: 1,
    src: [15.0, 24.2],
    dur: 5,
    title: "하단 탭 '좌석'",
  },
  {
    id: 'main-history-tab',
    full: 'pleos',
    chapter: 1,
    src: [24.2, 32.4],
    dur: 4.5,
    title: "하단 탭 '기록'",
  },
  {
    id: 'main-profile',
    full: 'pleos',
    chapter: 1,
    src: [32.4, 40.8],
    dur: 4.5,
    title: "하단 탭 '프로필'",
  },
  {
    id: 'main-why',
    full: 'pleos',
    chapter: 1,
    src: [40.8, 49.8],
    dur: 5.5,
    title: "'왜 이런 조언을 받았나요?'",
  },

  // ───────── 2 안내 설정 ─────────
  {
    id: 'set-sound',
    full: 'pleos',
    chapter: 2,
    src: [49.8, 59.2],
    dur: 4.5,
    title: '안내 소리 선택: 음성 → 알림음',
  },
  {
    id: 'set-driving-off',
    full: 'pleos',
    chapter: 2,
    src: [59.2, 68.4],
    dur: 4.5,
    title: '주행 중 안내 끄기',
  },
  {
    id: 'set-departure-off',
    full: 'pleos',
    chapter: 2,
    src: [68.4, 78.4],
    dur: 5,
    title: '출발 안내 끄기',
  },

  // ───────── 3 운행 1 · 알림음 ─────────
  {
    id: 'd1-start',
    full: 'pleos',
    chapter: 3,
    src: [78.4, 83.6],
    dur: 3.5,
    title: "운행 1 · '운행 시작'",
  },
  {
    id: 'd1-depart',
    chapter: 3,
    src: [83.6, 97.6],
    dur: 4,
    title: 'D 기어로 출발',
  },
  {
    id: 'd1-pattern-a',
    chapter: 3,
    src: [97.6, 109.0],
    dur: 4,
    title: '내비를 띄우고 급정거와 급출발을 3번 반복',
  },
  {
    // 가운데 한 번은 건너뛰고 세 번째 급정거·급출발로 넘어감
    id: 'd1-pattern-b',
    chapter: 3,
    src: [124.0, 135.5],
    dur: 3.5,
    title: '내비를 띄우고 급정거와 급출발을 3번 반복',
  },
  {
    id: 'd1-card',
    chapter: 3,
    src: [135.5, 143.5],
    dur: 8,
    audio: true,
    focus: {pane: 'pleos', from: 1.4, to: 6.8, zoom: 1.5, origin: [10, 0]},
    title: '같은 패턴이 3번 반복되자 내비 위에 카드와 알림음',
  },
  {
    id: 'd1-stop',
    chapter: 3,
    src: [143.5, 153.8],
    dur: 3,
    title: '천천히 멈추고 P 기어',
  },
  {
    id: 'd1-end',
    chapter: 3,
    src: [153.8, 162.8],
    dur: 4,
    title: "앱에서 '운행 기록 종료'",
  },
  {
    id: 'd1-endcard',
    chapter: 3,
    src: [162.8, 169.0],
    dur: 5,
    title: '종료 카드: 이동 평가 · 주행 상세 · 완료',
  },

  // ───────── 4 운행 2 · 음성 ─────────
  {
    id: 'd2-start',
    chapter: 4,
    src: [169.0, 179.2],
    dur: 5,
    title: '운행 2 · 안내 소리를 음성으로 바꾸고 출발',
  },
  {
    id: 'd2-accel',
    chapter: 4,
    src: [179.2, 195.2],
    dur: 3,
    title: '40km/h까지 완만하게 가속',
  },
  {
    id: 'd2-swing-a',
    chapter: 4,
    src: [195.2, 207.2],
    dur: 4,
    title: '40km/h를 유지하며 핸들을 좌우 30°로 반복',
  },
  {
    id: 'd2-swing-b',
    chapter: 4,
    src: [230.2, 241.5],
    dur: 3,
    title: '40km/h를 유지하며 핸들을 좌우 30°로 반복',
  },
  {
    id: 'd2-card',
    chapter: 4,
    src: [241.5, 250.5],
    dur: 9,
    audio: true,
    focus: {pane: 'pleos', from: 1.2, to: 7.8, zoom: 1.5, origin: [10, 0]},
    title: '좌우 흔들림이 반복되자 카드와 음성 안내',
  },
  {
    id: 'd2-stop',
    chapter: 4,
    src: [250.5, 266.0],
    dur: 3,
    title: '천천히 멈추고 P 기어',
  },
  {
    id: 'd2-endcard',
    chapter: 4,
    src: [266.0, 279.0],
    dur: 5,
    title: '종료 후 바뀐 출발 조언',
  },

  // ───────── 5 운행 3 · 환경 ─────────
  {
    id: 'd3-env',
    chapter: 5,
    src: [279.0, 291.2],
    dur: 6,
    title: '운행 3 · 차 안팎 환경',
  },
  {
    id: 'd3-depart',
    chapter: 5,
    src: [291.2, 307.5],
    dur: 4,
    title: '대기질 매우 나쁨, 외기 유입 상태로 출발',
  },
  {
    id: 'd3-card',
    chapter: 5,
    src: [307.5, 316.5],
    dur: 9,
    audio: true,
    focus: {pane: 'pleos', from: 1.6, to: 8.0, zoom: 1.5, origin: [10, 0]},
    title: '대기질이 나쁜데 외기 유입이면 환경 카드와 음성 안내',
  },
  {
    id: 'd3-recirc',
    chapter: 5,
    src: [316.5, 331.2],
    dur: 5,
    title: '차량 제어에서 내기 순환을 켜기',
  },
  {
    id: 'd3-end',
    chapter: 5,
    src: [331.2, 344.4],
    dur: 4,
    title: '운행 기록 종료',
  },

  // ───────── 6 운행 기록 ─────────
  {
    id: 'rec-list',
    chapter: 6,
    src: [344.4, 353.8],
    dur: 5,
    title: '운행 기록',
  },
  {
    id: 'rec-detail',
    chapter: 6,
    src: [353.8, 363.8],
    dur: 9,
    focus: {pane: 'pleos', from: 0.8, to: 8.4, zoom: 1.3, origin: [100, 48]},
    title: '운행 상세: 속도 그래프, 움직임 횟수, 동승자 응답, 다음 조언',
  },
  {
    id: 'rec-back',
    chapter: 6,
    src: [363.8, 366.6],
    dur: 1.5,
    title: '운행 기록',
  },

  // ───────── 7 알림 꺼짐 ─────────
  {
    id: 'noti-off',
    chapter: 7,
    src: [366.6, 377.4],
    dur: 7,
    title: '앱 알림을 끈 경우',
  },
];

// ───────── 아래는 계산용 (보통 고칠 필요 없음) ─────────
export type Placed = Segment & {start: number; end: number; index: number};

export const PLACED: Placed[] = (() => {
  let t = BRAND_INTRO_SEC;
  return SEGMENTS.map((s, index) => {
    const p = {...s, start: t, end: t + s.dur, index};
    t += s.dur;
    return p;
  });
})();

export const QUAD_START_SEC = BRAND_INTRO_SEC;
export const QUAD_END_SEC = PLACED[PLACED.length - 1].end;
export const TOTAL_SEC = QUAD_END_SEC + BRAND_OUTRO_SEC;
export const TOTAL_FRAMES = Math.round(TOTAL_SEC * FPS);

export const segById = (id: string): Placed => {
  const s = PLACED.find((p) => p.id === id);
  if (!s) throw new Error(`timeline.ts 에 '${id}' 구간이 없습니다`);
  return s;
};

/** 새 영상의 시각(초) → 지금 구간과 원본 영상 시각 */
export const stateAt = (sec: number) => {
  const t = Math.min(Math.max(sec, QUAD_START_SEC), QUAD_END_SEC - 1e-4);
  const seg = PLACED.find((p) => t < p.end) ?? PLACED[PLACED.length - 1];
  const local = t - seg.start;
  const srcTime = seg.src[0] + (local / seg.dur) * (seg.src[1] - seg.src[0]);
  return {seg, local, srcTime};
};
