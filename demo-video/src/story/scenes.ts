import history from '../data/history.json';
import story from '../data/story.json';

/**
 * Two takes: the drive and the rating are tools/rec/story_take.py; the history and the next advice are
 * history_take.py on the filming history (film_takes.py restore), so they show generated trips, not the test drive. `from` is seconds in the take; every scene plays at 1x. Rectangles are on the 2560x1440 Pleos display,
 * read from uiautomator bounds or measured on the recording.
 */
export const FPS = 30;
export const CHAPTERS = ['출발 전', '주행', '도착', '기록', '다음 출발'];
export const ENDING_SEC = 9;

export type Rect = [number, number, number, number];
/** ttsAt: when the caption shows in the narrated version, if its line needs to start earlier than `at`. */
export type Caption = {at: number; title: string; sub?: string; bell?: boolean; ttsAt?: number};
export type Scene = {
  id: string;
  src: 'story' | 'history';
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
const H = history.marks as Record<string, number>;
const CHIME = story.sounds[0]?.start ?? M['card-wait'];

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
  // The car is parked with the navigation in front; the app is opened from the dock. The title comes before.
  {id: 'open', src: 'story', chapter: 1, from: M.open + 5.8, dur: 6.5, caps: [
    {at: 0, title: '출발하기 전에 하단 바에서 모션케어를 엽니다'},
  ]},
  {id: 'home', src: 'story', chapter: 1, from: M.open + 12.3, dur: 6.5, spots: [{at: 0.6, until: 6.3, rect: HERO}], caps: [
    {at: 0, title: '첫 화면에는 최근 운전 기록에 맞춘 조언이 뜹니다'},
  ]},
  {id: 'people', src: 'story', chapter: 1, from: M.open + 18.8, dur: 4.5, spots: [{at: 0.3, until: 4.3, rect: PEOPLE}], caps: [
    {at: 0, title: '오늘 같이 탈 사람도 여기서 확인합니다'},
  ]},
  {id: 'seats', src: 'story', chapter: 1, from: M.seats + 2.2, dur: 6.5, caps: [
    {at: 0, title: '좌석마다 누가 앉는지 정해 둡니다'},
  ]},

  // Driving: gear D starts the trip, the app steps back behind the navigation, three hard stops bring the card.
  {id: 'gear-d', src: 'story', chapter: 2, from: M['gear-d'] - 1.8, dur: 7.4, dissolve: 0.5, console: {at: 0.3, until: 5.6}, caps: [
    {at: 0, title: '기어를 D로 바꾸면 기록이 바로 시작됩니다'},
  ]},
  {id: 'nav', src: 'story', chapter: 2, from: M.nav - 0.15, dur: 7, caps: [
    {at: 0, title: '주행 중에는 내비만 보이고 모션케어는 백그라운드에서 작동합니다'},
  ]},
  {id: 'stop1', src: 'story', chapter: 2, from: M.stop1 - 2.5, dur: 8, skip: '1분 뒤', dissolve: 0.3,
    spots: [{at: 1.5, until: 7.6, rect: SPEED, dim: false}], caps: [
      {at: 0, title: '정차 직전에 브레이크를 세게 밟으면 ‘정차 직전 큰 감속’으로 기록합니다'},
    ]},
  {id: 'stop2', src: 'story', chapter: 2, from: M.stop2 - 1.0, dur: 7, skip: '1분 뒤', dissolve: 0.3,
    spots: [{at: 0.5, until: 6.6, rect: SPEED, dim: false}], caps: [
      {at: 0, title: '비슷한 급정거가 한 번 더 나오면 10분 안에 반복되는지 계속 지켜봅니다'},
    ]},
  {id: 'card', src: 'story', chapter: 2, from: M.stop3 - 3, dur: cardAt + 12.5, skip: '1분 뒤', dissolve: 0.3, audio: true,
    spots: [{at: 1.5, until: cardAt - 0.5, rect: SPEED, dim: false}, {at: cardAt + 0.7, until: cardAt + 8.8, rect: CARD, dim: false}], caps: [
      {at: 0, title: '그리고 10분이 채 안 돼 세 번째 급정거가 나옵니다'},
      {at: cardAt, title: '이렇게 같은 패턴이 반복되면 내비 위에 짧은 안내 카드가 뜹니다', bell: true},
      // Narrated, the line (about 6 s) starts while the card is still up, so it ends before the scene does.
      {at: cardAt + 9.3, ttsAt: cardAt + 6.0, title: '카드는 누르지 않아도 잠시 뒤 사라지고 운전은 그대로 이어집니다'},
    ]},

  // Arrival: P, the trip ends by itself three minutes later, then the passenger rating.
  {id: 'park', src: 'story', chapter: 3, from: M.parked - 6.5, dur: 6, skip: '3분 뒤', dissolve: 0.5, caps: [
    {at: 0, title: '목적지에 도착하면 기어를 P로 바꿉니다'},
  ]},
  {id: 'ended', src: 'story', chapter: 3, from: M.ended - 1.6, dur: 6, skip: '3분 뒤', dissolve: 0.3, caps: [
    {at: 0, title: 'P 상태로 3분이 지나면 기록이 알아서 끝납니다'},
  ]},
  {id: 'rate-open', src: 'story', chapter: 3, from: M.rate + 1.6, dur: 4.4, caps: [
    {at: 0, title: '운행이 끝나면 동승자가 이번 이동을 평가합니다'},
  ]},
  {id: 'rate-who', src: 'story', chapter: 3, from: M.rate + 8.2, dur: 6.8, dissolve: 0.25, caps: [
    {at: 0, title: '아이가 탔다면 보호자가 대신 답합니다'},
  ]},
  {id: 'rate-stars', src: 'story', chapter: 3, from: M.rate + 17.7, dur: 3.6, dissolve: 0.25, caps: [
    {at: 0, title: '얼마나 편했는지는 별점으로 남깁니다'},
  ]},
  // Ends before the save tap: the form moves on to the next passenger at once.
  {id: 'rate-moment', src: 'story', chapter: 3, from: M.rate + 22.5, dur: 4.9, dissolve: 0.25, caps: [
    {at: 0, title: '어느 순간이 불편했는지도 골라서 남깁니다'},
  ]},

  // Records: the generated history (11/5 16:39, 지영 driving 서아, five hard stops and a card), then the trend.
  // Starts just after the tap on 기록, so the home (with the advice) does not show yet.
  {id: 'records', src: 'history', chapter: 4, from: H.records + 2.6, dur: 4, dissolve: 0.5, caps: [
    {at: 0, title: '운행이 끝날 때마다 기록이 쌓입니다'},
  ]},
  {id: 'filter', src: 'history', chapter: 4, from: H.filter - 0.15, dur: 5.6, caps: [
    {at: 0, title: '함께 탄 사람별로 골라 볼 수 있습니다'},
  ]},
  {id: 'trip', src: 'history', chapter: 4, from: H.trip - 0.4, dur: 7.6, dissolve: 0.3, spots: [{at: 2.4, until: 7.3, rect: TRIP_GRAPH}], caps: [
    {at: 0, title: '운행을 열면 급정거가 언제 있었는지 그래프로 보여줍니다'},
  ]},
  {id: 'segment', src: 'history', chapter: 4, from: H.segment - 0.4, dur: 6.6, caps: [
    {at: 0, title: '그래프에서 급정거 구간을 고르면 그때 속도 변화를 자세히 볼 수 있습니다'},
  ]},
  // The cabin values are kept per trip (start, every hour, end), not per moment.
  {id: 'cabin', src: 'history', chapter: 4, from: H.lower1 - 0.2, dur: 7, caps: [
    {at: 0, title: '이 운행의 실내 온도와 공조 상태도 바깥 대기질과 함께 남습니다'},
  ]},
  {id: 'answer', src: 'history', chapter: 4, from: H.lower2 - 0.2, dur: 6.6, spots: [{at: 2.2, until: 6.4, rect: ANSWER}], caps: [
    {at: 0, title: '동승자의 평가와 주행 중에 뜬 안내도 이 기록에서 확인할 수 있습니다'},
  ]},
  {id: 'profile', src: 'history', chapter: 4, from: H.profile + 2.7, dur: 5.2, dissolve: 0.5, caps: [
    {at: 0, title: '운전자 프로필에서는 최근 운전 경향을 볼 수 있습니다'},
  ]},
  {id: 'trend', src: 'history', chapter: 4, from: H['profile-lower'] - 0.1, dur: 6.5, caps: [
    {at: 0, title: '지난 몇 주 동안 급정거가 늘었는지 줄었는지 보여줍니다'},
  ]},

  // Next departure: the advice from the history, with how it was counted.
  {id: 'next', src: 'history', chapter: 5, from: H.next - 0.2, dur: 6.5, dissolve: 0.5, spots: [{at: 0.8, until: 6.2, rect: HERO}], caps: [
    {at: 0, title: '다음에 출발할 때도 쌓인 기록으로 고른 조언이 뜹니다'},
  ]},
  {id: 'advice', src: 'history', chapter: 5, from: H.advice + 2.5, dur: 7, spots: [{at: 1.0, until: 6.8, rect: COMPARE}], caps: [
    {at: 0, title: '조언을 누르면 최근 3회와 그 이전 3회를 비교한 근거가 나옵니다'},
  ]},
  {id: 'calc', src: 'history', chapter: 5, from: H['advice-calc'] + 1.75, dur: 8, zoom: {at: 1.6, until: 7.7, rect: CALC, scale: 1.5}, caps: [
    {at: 0, title: '조언이 어떤 운행을 보고 나왔는지 계산 과정까지 확인할 수 있습니다'},
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

export const capAt = (c: Caption, narration = false) => (narration ? c.ttsAt ?? c.at : c.at);

export const captionAt = (scene: Placed, local: number, narration = false): Caption =>
  [...scene.caps].reverse().find((c) => local >= capAt(c, narration)) ?? scene.caps[0];
