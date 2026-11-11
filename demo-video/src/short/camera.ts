import {Easing, interpolate} from 'remotion';
import type {Cam, Rect} from './timeline';

/** 녹화본은 Pleos 화면(2560x1440)을 1920x1080으로 받았습니다. 영역 좌표는 2560 기준이라 0.75배로 바꿉니다. */
export const SRC_W = 1920;
export const SRC_H = 1080;
const K = SRC_W / 2560;
const ease = Easing.inOut(Easing.cubic);

export type View = {cx: number; cy: number; vw: number};

const clamp = (v: number, lo: number, hi: number) => Math.min(Math.max(v, lo), hi);

/** 영역 하나를 16:9 칸에 담는 시야. 영역보다 조금 넓게 잡고, 화면 밖으로 나가지 않게 합니다. */
export const viewOf = (r: Rect, pad = 0.05): View => {
  const [x1, y1, x2, y2] = r.map((v) => v * K);
  const w = (x2 - x1) * (1 + pad * 2);
  const h = (y2 - y1) * (1 + pad * 2);
  const vw = Math.min(SRC_W, Math.max(w, (h * 16) / 9));
  const vh = (vw * 9) / 16;
  return {cx: clamp((x1 + x2) / 2, vw / 2, SRC_W - vw / 2), cy: clamp((y1 + y2) / 2, vh / 2, SRC_H - vh / 2), vw};
};

const FULL_VIEW: View = {cx: SRC_W / 2, cy: SRC_H / 2, vw: SRC_W};

/** 장면 안 시각 t 의 시야. 키프레임 사이는 부드럽게 옮기고, 확대 정도는 로그 축으로 보간합니다. */
export const viewAt = (cams: Cam[] | undefined, t: number): View => {
  if (!cams || cams.length === 0) return FULL_VIEW;
  if (t <= cams[0][0]) return viewOf(cams[0][1]);
  for (let i = 1; i < cams.length; i++) {
    const [ta, ra] = cams[i - 1];
    const [tb, rb] = cams[i];
    if (t <= tb) {
      const p = ease(interpolate(t, [ta, tb], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}));
      const a = viewOf(ra);
      const b = viewOf(rb);
      const vw = Math.exp(Math.log(a.vw) + (Math.log(b.vw) - Math.log(a.vw)) * p);
      return {cx: a.cx + (b.cx - a.cx) * p, cy: a.cy + (b.cy - a.cy) * p, vw};
    }
  }
  return viewOf(cams[cams.length - 1][1]);
};

/** 칸(pw x ph) 안에 시야를 채우는 영상 위치와 크기 */
export const placeVideo = (v: View, pw: number) => {
  const s = pw / v.vw;
  const vh = (v.vw * 9) / 16;
  return {left: -(v.cx - v.vw / 2) * s, top: -(v.cy - vh / 2) * s, width: SRC_W * s, height: SRC_H * s};
};
