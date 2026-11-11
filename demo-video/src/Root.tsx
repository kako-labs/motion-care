import {Composition} from 'remotion';
import {FPS, TOTAL_FRAMES} from './config/timeline';
import {Demo} from './Demo';
import {Short} from './short/Short';
import {FPS as SHORT_FPS, TOTAL_FRAMES as SHORT_FRAMES} from './short/timeline';

export const Root = () => (
  <>
    <Composition id="MotionCareDemo" component={Demo} durationInFrames={TOTAL_FRAMES} fps={FPS} width={1920} height={1080} />
    <Composition id="MotionCare3Min" component={Short} durationInFrames={SHORT_FRAMES} fps={SHORT_FPS} width={1920} height={1080} />
  </>
);
