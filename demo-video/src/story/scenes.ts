import detail from '../data/detail.json';
import story from '../data/story.json';

/**
 * Two takes recorded in story order (tools/rec/story_take.py, then detail_take.py on the same data), cut in that
 * order. `from` is seconds in the take; every scene plays at 1x. Rectangles are on the 2560x1440 Pleos display,
 * read from uiautomator bounds or measured on the recording.
 */
export const FPS = 30;
export const CHAPTERS = ['출발 전', '주행', '도착', '기록', '다음 출발'];
export const ENDING_SEC = 10;

export type Rect = [number, number, number, number];
export type Caption = {at: number; title: string; sub?: string; bell?: boolean};
export type Scene = {
  id: string;
  src: 'story' | 'detail';
  /** 0 is the opening; 1.. index CHAPTERS from 1 */
  chapter: number;
  from: number;
  dur: number;
  rate?: number;
  caps: Caption[];
  /** Shown for a moment when the scene skips ahead in time, e.g. "1분 뒤". */
  skip?: string;
  /** Seconds this scene fades in over the previous one. */
  dissolve?: number;
  spots?: {at: number; until: number; rect: Rect; dim?: boolean}[];
  /** Small text only: a fixed zoom of at most 1.5x. */
  zoom?: {at: number; until: number; rect: Rect; scale?: number};
  console?: {at: number; until: number};
  audio?: boolean;
};
export type Placed = Scene & {index: number; start: number; rate: number};

const M = story.marks as Record<string, number>;
const D = detail.marks as Record<string, number>;
const CHIME = story.sounds[0]?.start ?? M['card-wait'];
const CARD_TEXT = story.cards[0]?.text ?? '';

const HERO: Rect = [890, 230, 1640, 595];
const PEOPLE: Rect = [895, 670, 1310, 945];
const SPEED: Rect = [320, 60, 540, 245];
const CARD: Rect = [912, 34, 1648, 204];
const TRIP_GRAPH: Rect = [925, 585, 1785, 1160];
const ANSWER: Rect = [900, 952, 1600, 1066];
const COMPARE: Rect = [905, 385, 1925, 560];
/** The app area only (right of the system's driving view), so 1.5x shows no system strip. */
const CALC: Rect = [853, 225, 2560, 1185];

const cardAt = CHIME - (M.stop3 - 3);

const SCENES: Scene[] = [
  // Opening: the car is parked with the navigation in front, and the app is opened from the dock.
  {id: 'open', src: 'story', chapter: 0, from: M.open + 1.3, dur: 11, caps: [
    {at: 0, title: '운전하는 사람은 괜찮은데, 뒷자리 아이는 멀미를 합니다'},
    {at: 4.5, title: '모션케어는 함께 타는 사람까지 편안한 운전을 돕는 차량용 앱입니다'},
  ]},
  {id: 'home', src: 'story', chapter: 1, from: M.open + 12.3, dur: 6.5, spots: [{at: 0.6, until: 6.3, rect: HERO}], caps: [
    {at: 0, title: '출발 전, 운전자의 지난 기록으로 고른 오늘의 조언', sub: '지영의 최근 운행에서 정차 직전 큰 감속이 줄었습니다'},
  ]},
  {id: 'people', src: 'story', chapter: 1, from: M.open + 18.8, dur: 4.5, spots: [{at: 0.3, until: 4.3, rect: PEOPLE}], caps: [
    {at: 0, title: '오늘 함께 타는 사람', sub: '운전자 지영, 동승자 민준, 뒷자리 아이 서아'},
  ]},
  {id: 'seats', src: 'story', chapter: 1, from: M.seats + 2.2, dur: 6.5, caps: [
    {at: 0, title: '누가 어디에 앉았는지 좌석에 등록해 둡니다', sub: '서아는 뒷좌석 오른쪽'},
  ]},

  // Driving: gear D starts the trip, the app steps back behind the navigation, three hard stops bring the card.
  {id: 'gear-d', src: 'story', chapter: 2, from: M['gear-d'] - 1.8, dur: 7.4, dissolve: 0.5, console: {at: 0.3, until: 5.6}, caps: [
    {at: 0, title: '기어를 D로 바꾸면 운행 기록이 저절로 시작됩니다', sub: '시작 버튼을 누를 필요가 없습니다'},
  ]},
  {id: 'nav', src: 'story', chapter: 2, from: M.nav - 0.15, dur: 7, caps: [
    {at: 0, title: '달리는 동안 화면에는 내비만', sub: '모션케어는 뒤에서 차량 속도를 살핍니다'},
  ]},
  {id: 'stop1', src: 'story', chapter: 2, from: M.stop1 - 2.5, dur: 8, skip: '1분 뒤', dissolve: 0.3,
    spots: [{at: 1.5, until: 7.6, rect: SPEED, dim: false}], caps: [
      {at: 0, title: '정차 직전에 세게 섭니다', sub: '정차 직전 큰 감속 1번째'},
    ]},
  {id: 'stop2', src: 'story', chapter: 2, from: M.stop2 - 1.0, dur: 7, skip: '1분 뒤', dissolve: 0.3,
    spots: [{at: 0.5, until: 6.6, rect: SPEED, dim: false}], caps: [
      {at: 0, title: '같은 감속이 또 반복됩니다', sub: '정차 직전 큰 감속 2번째'},
    ]},
  {id: 'card', src: 'story', chapter: 2, from: M.stop3 - 3, dur: cardAt + 12.5, skip: '1분 뒤', dissolve: 0.3, audio: true,
    spots: [{at: 1.5, until: cardAt - 0.5, rect: SPEED, dim: false}, {at: cardAt + 0.7, until: cardAt + 8.8, rect: CARD, dim: false}], caps: [
      {at: 0, title: '10분 안에 세 번째', sub: '정차 직전 큰 감속 3번째'},
      {at: cardAt, title: '같은 패턴이 반복되자 내비 위에 짧은 카드', sub: `“${CARD_TEXT}”`, bell: true},
      {at: cardAt + 9.3, title: '누르지 않아도 잠시 뒤 사라집니다', sub: '앱을 열지 않아도 되고, 운전은 그대로 이어집니다'},
    ]},

  // Arrival: P, the trip ends by itself three minutes later, then the passenger rating.
  {id: 'park', src: 'story', chapter: 3, from: M.parked - 6.5, dur: 6, skip: '3분 뒤', dissolve: 0.5, caps: [
    {at: 0, title: '도착해서 P로 바꿉니다'},
  ]},
  {id: 'ended', src: 'story', chapter: 3, from: M.ended - 1.6, dur: 6, skip: '3분 뒤', dissolve: 0.3, caps: [
    {at: 0, title: 'P에서 3분이 지나면 기록이 저절로 끝납니다', sub: '운행을 마쳤어요'},
  ]},
  {id: 'rate-open', src: 'story', chapter: 3, from: M.rate + 1.6, dur: 4.4, caps: [
    {at: 0, title: '함께 탄 사람이 이번 이동을 평가합니다'},
  ]},
  {id: 'rate-who', src: 'story', chapter: 3, from: M.rate + 8.2, dur: 6.8, dissolve: 0.25, caps: [
    {at: 0, title: '아이는 보호자가 대신 답합니다', sub: '서아'},
  ]},
  {id: 'rate-stars', src: 'story', chapter: 3, from: M.rate + 17.7, dur: 3.6, dissolve: 0.25, caps: [
    {at: 0, title: '얼마나 편안했는지 별로 남깁니다', sub: '서아: 별 3개'},
  ]},
  // Ends before the save tap: the form moves on to the next passenger at once.
  {id: 'rate-moment', src: 'story', chapter: 3, from: M.rate + 22.5, dur: 4.9, dissolve: 0.25, caps: [
    {at: 0, title: '불편했던 순간도 고를 수 있습니다', sub: '서아: 출발·정차'},
  ]},

  // Records: the trip just driven, then the driver's trend.
  // Starts just after the tap on 기록, so the home (with the next advice) does not show yet.
  {id: 'records', src: 'detail', chapter: 4, from: D.records + 2.4, dur: 4.8, dissolve: 0.5, caps: [
    {at: 0, title: '방금 운행은 기록 맨 위에'},
  ]},
  {id: 'trip', src: 'detail', chapter: 4, from: D.trip - 0.4, dur: 8, spots: [{at: 2.4, until: 7.7, rect: TRIP_GRAPH}], caps: [
    {at: 0, title: '방금 운행의 정차 직전 큰 감속 3번이 그래프에 남았습니다'},
  ]},
  {id: 'segment', src: 'detail', chapter: 4, from: D.segment - 0.4, dur: 6.2, caps: [
    {at: 0, title: '세 번째 감속을 고르면 그때의 속도 변화를 자세히', sub: '이 감속 뒤에 내비 위 카드가 떴습니다'},
  ]},
  {id: 'cabin', src: 'detail', chapter: 4, from: D.lower1 - 0.2, dur: 6.4, caps: [
    {at: 0, title: '그때의 차 안 환경도 함께 남습니다', sub: '온도·공조는 차량 신호, 바깥 대기질은 예보'},
  ]},
  {id: 'answer', src: 'detail', chapter: 4, from: D.lower2 - 0.2, dur: 7.4, spots: [{at: 2.4, until: 7.2, rect: ANSWER}], caps: [
    {at: 0, title: '서아의 응답과 주행 중 안내 1번까지, 한 운행에 함께'},
  ]},
  {id: 'profile', src: 'story', chapter: 4, from: M.profile + 3.4, dur: 5.2, dissolve: 0.5, caps: [
    {at: 0, title: '운전자 프로필에는 최근 운전 경향'},
  ]},
  {id: 'trend', src: 'story', chapter: 4, from: M['profile-lower'] - 0.2, dur: 7, caps: [
    {at: 0, title: '3주 동안의 흐름', sub: '줄어들던 정차 직전 큰 감속이 오늘 운행으로 다시 늘었습니다'},
  ]},

  // Next departure: the advice now reflects today's trip, with how it was counted.
  {id: 'next', src: 'story', chapter: 5, from: M.next - 0.4, dur: 7.4, dissolve: 0.5, spots: [{at: 0.8, until: 7.0, rect: HERO}], caps: [
    {at: 0, title: '다음 출발 조언에 오늘 운행이 반영됐습니다', sub: '정차 직전 큰 감속, 10분당 0.4회에서 1.9회로'},
  ]},
  {id: 'advice', src: 'story', chapter: 5, from: M.advice + 2.1, dur: 6.2, spots: [{at: 1.2, until: 6.0, rect: COMPARE}], caps: [
    {at: 0, title: '왜 이 조언인지, 최근 3회와 그 이전 3회를 비교합니다'},
  ]},
  {id: 'calc', src: 'story', chapter: 5, from: M['advice-calc'] + 1.4, dur: 9, zoom: {at: 1.6, until: 8.6, rect: CALC, scale: 1.5}, caps: [
    {at: 0, title: '어떤 기록을 어떻게 셌는지 그대로 보여 줍니다', sub: '운전 패턴 빈도의 비교일 뿐, 멀미를 진단하지 않습니다'},
  ]},
];

let cursor = 0;
export const PLACED: Placed[] = SCENES.map((s, index) => {
  const placed = {...s, index, start: cursor, rate: s.rate ?? 1};
  cursor += s.dur;
  return placed;
});
export const MAIN_END = cursor;
export const END_SEC = MAIN_END + ENDING_SEC;

export const sceneAt = (sec: number) => {
  const scene = PLACED.find((p) => sec < p.start + p.dur) ?? PLACED[PLACED.length - 1];
  return {scene, local: sec - scene.start};
};

export const captionAt = (scene: Placed, local: number): Caption =>
  [...scene.caps].reverse().find((c) => local >= c.at) ?? scene.caps[0];
