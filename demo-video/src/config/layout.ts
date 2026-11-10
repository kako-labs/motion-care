/** 화면 배치. 위치·크기·확대 정도를 바꿀 때 이 파일을 고치세요. (단위: 1920x1080 기준 px) */
export type Rect = {x: number; y: number; w: number; h: number};

/** 원본 6분 영상에서 각 화면이 차지하는 영역 */
export const CROP = {
  console: {x: 72, y: 112, w: 685, h: 597},
  pleos: {x: 785, y: 112, w: 1062, h: 597},
  data: {x: 71, y: 728, w: 687, h: 328},
  cardlog: {x: 784, y: 946, w: 1064, h: 110}, // '내비 위 카드' 기록 상자
} satisfies Record<string, Rect>;

const TOP_Y = 100;
const TOP_H = 610;
const LEFT_X = 50;
const GAP = 26;
const CONSOLE_W = Math.round((TOP_H * CROP.console.w) / CROP.console.h); // 700
const PLEOS_W = Math.round((TOP_H * CROP.pleos.w) / CROP.pleos.h); // 1085
const RIGHT_X = LEFT_X + CONSOLE_W + GAP;
const BOTTOM_Y = TOP_Y + TOP_H + 14;
const BOTTOM_H = Math.round((CONSOLE_W * CROP.data.h) / CROP.data.w); // 334

/** 4분할이 완성됐을 때 각 화면의 자리 */
export const HOME = {
  console: {x: LEFT_X, y: TOP_Y, w: CONSOLE_W, h: TOP_H},
  pleos: {x: RIGHT_X, y: TOP_Y, w: PLEOS_W, h: TOP_H},
  data: {x: LEFT_X, y: BOTTOM_Y, w: CONSOLE_W, h: BOTTOM_H},
  analysis: {x: RIGHT_X, y: BOTTOM_Y, w: PLEOS_W, h: BOTTOM_H}, // 움직임 분석 + 안내 기록
} satisfies Record<string, Rect>;

/** 화면 소개 때 크게 보여줄 위치(x, y)와 배율 */
export const BIG = {
  console: {x: 90, y: 118, scale: 1.5},
  pleos: {x: 626, y: 350, scale: 1.12},
  data: {x: 50, y: 548, scale: 1.5},
};

/** timeline.ts 에서 full 로 지정한 구간에 한 화면만 크게 보여줄 위치와 배율 */
export const FULL = {
  console: {x: 395, y: 74, scale: 1.63},
  pleos: {x: 76, y: 74, scale: 1.63},
  data: {x: 50, y: 300, scale: 2.6},
};

/** 화면 소개: 어느 구간에서 어느 화면이 등장하는지, 설명 글 위치 */
export const INTRO_STAGES = [
  {pane: 'console', seg: 'intro-console', note: {x: 1230, y: 380, w: 620}},
  {pane: 'pleos', seg: 'intro-pleos', note: {x: 60, y: 790, w: 520}},
  {pane: 'data', seg: 'intro-data', note: {x: 1170, y: 800, w: 700}},
  {pane: 'analysis', seg: 'intro-caption', note: null},
] as const;

/** 등장·축소·확대에 걸리는 시간(초) */
export const MOTION = {
  appear: 0.6,
  shrink: 1.3,
  focus: 0.55,
  full: 0.8,
};
