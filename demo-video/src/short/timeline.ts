/**
 * 3분 데모 타임라인. 장면 하나가 한 줄입니다.
 *
 * - src: 'app' = 앱만 찍은 테이크(public/rec/app_pleos.mp4), 'drive' = 주행 테이크(Pleos·콘솔·소리·차량 값)
 * - from/to: 그 테이크에서 가져올 구간(초). 테이크 시각은 Pleos 녹화의 첫 프레임이 0초입니다(src/data/*.json).
 * - dur: 새 영상에서의 길이(초). to-from 보다 짧으면 그만큼 빨리 재생하고 화면에 배속을 표시합니다.
 * - layout: 'app' = Pleos 화면 하나를 크게, 'quad' = 콘솔·Pleos·차량 데이터·움직임 분석 4분할
 * - cams: 카메라. [장면 안 시각(초), 보여 줄 영역] 목록이고, 사이를 부드럽게 옮겨 갑니다.
 *   영역은 Pleos 화면 좌표(2560x1440, uiautomator bounds와 같음)의 [x1, y1, x2, y2]입니다.
 *   quad 에서는 Pleos 칸 안쪽만 확대합니다.
 * - audio: true 면 주행 테이크의 소리(알림음)를 넣습니다. 1배속 장면에만 씁니다.
 */
export type Rect = [number, number, number, number];
export type Cam = [number, Rect];
export type Scene = {
  id: string;
  chapter: number;
  title: string;
  src: 'app' | 'drive';
  from: number;
  to: number;
  dur: number;
  layout: 'app' | 'quad';
  cams?: Cam[];
  audio?: boolean;
};

export const FPS = 30;
export const BRAND_INTRO_SEC = 8;
export const BRAND_OUTRO_SEC = 10;
/** Pleos 계기판 속도는 콘솔 입력보다 약 0.3초 늦게 바뀝니다(11/12 측정). 콘솔·차량 데이터를 그만큼 늦춰 같은 값을 보입니다. */
export const CONSOLE_DELAY_SEC = 0.3;

export const CHAPTERS = ['', '1 출발 전', '2 주행 중', '3 도착 후', '4 차 안 공기', '5 쌓인 기록', '6 다음 출발'];

// 자주 쓰는 영역 (Pleos 화면 2560x1440 좌표)
const FULL: Rect = [0, 0, 2560, 1440];
const APP: Rect = [853, 90, 2535, 1300];
const HERO: Rect = [890, 150, 1740, 600];
const HOME_CARDS: Rect = [880, 650, 2050, 980];
const SEAT_MAP: Rect = [880, 120, 1700, 1290];
const ADD_PERSON: Rect = [1380, 330, 2000, 1050];
const LIST: Rect = [880, 90, 2510, 940];
const CHIPS: Rect = [880, 190, 1700, 330];
const GRAPH: Rect = [890, 560, 1800, 1300];
const RIGHT_COL: Rect = [1840, 300, 2500, 1300];
const OUTING_TOP: Rect = [880, 300, 2510, 960];
const TREND: Rect = [890, 330, 1820, 720];
/** 프로필을 아래로 넘긴 뒤의 전체 운전 기록 그래프 */
const TREND_CHART: Rect = [890, 280, 2500, 710];
const COMPARE: Rect = [890, 230, 1960, 700];
const CALC: Rect = [890, 560, 2490, 850];
const CARD: Rect = [880, 30, 1700, 230];
const RATE_TOP: Rect = [880, 230, 2480, 720];
const RATE_REASONS: Rect = [880, 560, 2480, 1180];

export const SCENES: Scene[] = [
  // ───────── 1 출발 전 (앱 테이크) ─────────
  {id: 'home-hero', chapter: 1, title: '운전자의 지난 기록으로 고른 출발 조언', src: 'app', from: 3.0, to: 9.6, dur: 6, layout: 'app',
    cams: [[0, FULL], [0.8, FULL], [2.0, APP], [3.0, APP], [4.2, HERO]]},
  {id: 'home-cards', chapter: 1, title: '함께 타는 사람과 지금 차 안팎 환경', src: 'app', from: 9.6, to: 15.9, dur: 5, layout: 'app',
    cams: [[0, HERO], [1.1, HOME_CARDS]]},
  {id: 'seats', chapter: 1, title: '누가 어디에 앉았는지 좌석에 등록', src: 'app', from: 16.1, to: 25.9, dur: 6, layout: 'app',
    cams: [[0, APP], [1.4, APP], [2.6, SEAT_MAP]]},
  {id: 'character', chapter: 1, title: '사람마다 고르는 캐릭터', src: 'app', from: 39.3, to: 44.2, dur: 4, layout: 'app',
    cams: [[0, APP], [0.9, ADD_PERSON]]},

  // ───────── 2 주행 중 (주행 테이크, 4분할) ─────────
  {id: 'gear-d', chapter: 2, title: '기어를 D로 바꾸면 운행 기록이 자동으로 시작', src: 'drive', from: 8.6, to: 15.6, dur: 7, layout: 'quad',
    cams: [[0, FULL], [1.6, FULL], [2.6, HERO], [6.0, HERO], [7.0, FULL]]},
  {id: 'depart', chapter: 2, title: '내비를 띄우고 출발', src: 'drive', from: 15.6, to: 37.3, dur: 5, layout: 'quad'},
  {id: 'stops', chapter: 2, title: '정차 직전에 세게 서기를 세 번 반복', src: 'drive', from: 37.3, to: 94.2, dur: 12, layout: 'quad'},
  {id: 'card', chapter: 2, title: '같은 패턴이 반복되자 내비 위에 안내 카드와 알림음', src: 'drive', from: 94.2, to: 106.2, dur: 12, layout: 'quad', audio: true,
    cams: [[0, FULL], [1.0, FULL], [2.0, CARD], [10.4, CARD], [11.4, FULL]]},

  // ───────── 3 도착 후 ─────────
  {id: 'park', chapter: 3, title: '천천히 서서 P로', src: 'drive', from: 106.2, to: 121.6, dur: 4, layout: 'quad'},
  {id: 'finish', chapter: 3, title: "'운행 기록 종료'를 누르면 종료 카드에서 바로 이동 평가로", src: 'drive', from: 121.6, to: 131.8, dur: 5, layout: 'app',
    cams: [[0, FULL], [0.8, APP], [2.4, APP], [3.4, HERO]]},
  {id: 'rate', chapter: 3, title: '동승자마다 편안함을 남기고, 아이는 보호자가 대신', src: 'drive', from: 131.8, to: 148.0, dur: 8, layout: 'app',
    cams: [[0, HERO], [0.8, APP], [2.6, APP], [3.6, RATE_TOP]]},
  {id: 'rate-reason', chapter: 3, title: '불편했던 순간을 골라 저장', src: 'drive', from: 148.0, to: 154.8, dur: 5.5, layout: 'app',
    cams: [[0, RATE_TOP], [1.0, RATE_REASONS]]},

  // ───────── 4 차 안 공기 (다른 운행, 4분할) ─────────
  {id: 'air-depart', chapter: 4, title: '다른 운행: 외기 유입 상태로 출발, 바깥 대기질 나쁨', src: 'drive', from: 180.4, to: 190.8, dur: 4, layout: 'quad'},
  {id: 'air-card', chapter: 4, title: '대기질이 나쁜데 외기가 들어오면 공기 안내', src: 'drive', from: 190.8, to: 200.6, dur: 9.8, layout: 'quad', audio: true,
    cams: [[0, FULL], [0.6, CARD], [8.6, CARD], [9.6, FULL]]},

  // ───────── 5 쌓인 기록 (앱 테이크) ─────────
  {id: 'records', chapter: 5, title: '3주 동안 20번의 운행 기록', src: 'app', from: 56.0, to: 66.8, dur: 6, layout: 'app',
    cams: [[0, FULL], [0.8, LIST]]},
  {id: 'filter', chapter: 5, title: '함께 탄 사람별로 보기', src: 'app', from: 69.3, to: 76.5, dur: 3, layout: 'app',
    cams: [[0, LIST], [0.8, CHIPS]]},
  {id: 'detail', chapter: 5, title: '운행 상세: 정차 직전 큰 감속 구간을 그래프에서', src: 'app', from: 87.4, to: 96.5, dur: 8, layout: 'app',
    cams: [[0, APP], [1.6, APP], [2.6, GRAPH]]},
  {id: 'detail-env', chapter: 5, title: '그때의 차 안 환경과 다음 출발 조언', src: 'app', from: 97.5, to: 103.0, dur: 5, layout: 'app',
    cams: [[0, APP], [1.0, RIGHT_COL]]},
  {id: 'outing', chapter: 5, title: '고속도로 나들이: 동승자 응답과 그때의 공기 흐름', src: 'app', from: 138.4, to: 143.6, dur: 5, layout: 'app',
    cams: [[0, APP], [0.7, OUTING_TOP]]},
  {id: 'profile', chapter: 5, title: '운전자 프로필: 정차 직전 큰 감속이 줄어든 흐름', src: 'app', from: 155.7, to: 164.6, dur: 8, layout: 'app',
    cams: [[0, APP], [1.0, TREND], [3.4, TREND], [4.6, TREND_CHART]]},

  // ───────── 6 다음 출발 (앱 테이크) ─────────
  {id: 'next-home', chapter: 6, title: '다음 출발 조언: 10분당 1.7회에서 0.5회로', src: 'app', from: 149.0, to: 155.5, dur: 5, layout: 'app',
    cams: [[0, APP], [1.0, HERO]]},
  {id: 'advice', chapter: 6, title: '왜 이 조언인지: 최근 3회 평균과 이전 3회 평균', src: 'app', from: 171.6, to: 179.8, dur: 7, layout: 'app',
    cams: [[0, APP], [1.0, COMPARE]]},
  {id: 'advice-calc', chapter: 6, title: '계산까지 그대로, 멀미 진단이 아닌 운전 패턴 비교', src: 'app', from: 179.9, to: 186.4, dur: 6, layout: 'app',
    cams: [[0, COMPARE], [1.0, CALC]]},
];

// ───────── 계산용 ─────────
export type Placed = Scene & {start: number; end: number; index: number};

export const PLACED: Placed[] = (() => {
  let t = BRAND_INTRO_SEC;
  return SCENES.map((s, index) => {
    const p = {...s, start: t, end: t + s.dur, index};
    t += s.dur;
    return p;
  });
})();

export const MAIN_START = BRAND_INTRO_SEC;
export const MAIN_END = PLACED[PLACED.length - 1].end;
export const TOTAL_SEC = MAIN_END + BRAND_OUTRO_SEC;
export const TOTAL_FRAMES = Math.round(TOTAL_SEC * FPS);

export const sceneAt = (sec: number) => {
  const t = Math.min(Math.max(sec, MAIN_START), MAIN_END - 1e-4);
  const scene = PLACED.find((p) => t < p.end) ?? PLACED[PLACED.length - 1];
  const local = t - scene.start;
  const srcTime = scene.from + (local / scene.dur) * (scene.to - scene.from);
  return {scene, local, srcTime};
};
