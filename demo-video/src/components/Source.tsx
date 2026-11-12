import {Img, staticFile} from 'remotion';
import type {Rect} from '../config/layout';

/**
 * 영상의 한 영역만 잘라서 보여준다.
 * public/remap/f_00001.jpg … = 원본을 timeline.ts 대로 미리 이어 붙여 뽑은 프레임 (tools/build-remap.mjs 가 만듦).
 * srcFrame = 그 프레임 번호 (= 4분할 구간이 시작된 뒤 지난 프레임 수)
 */
export const Source = ({
  crop, width, srcFrame, radius = 0, zoom = 1, origin = [50, 50],
}: {crop: Rect; width: number; srcFrame: number; radius?: number; zoom?: number; origin?: [number, number]}) => {
  const k = width / crop.w;
  return (
    <div style={{width, height: crop.h * k, overflow: 'hidden', position: 'relative', borderRadius: radius}}>
      <div style={{position: 'absolute', inset: 0, transform: `scale(${zoom})`, transformOrigin: `${origin[0]}% ${origin[1]}%`}}>
        <Img
          src={staticFile(`remap/f_${String(srcFrame + 1).padStart(5, '0')}.jpg`)}
          style={{position: 'absolute', left: -crop.x * k, top: -crop.y * k, width: 1920 * k, height: 1080 * k, maxWidth: 'none'}}
        />
      </div>
    </div>
  );
};
