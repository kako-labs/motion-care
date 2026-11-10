import {Composition} from 'remotion';
import {FPS, TOTAL_FRAMES} from './config/timeline';
import {Demo} from './Demo';

export const Root = () => (
  <Composition id="MotionCareDemo" component={Demo} durationInFrames={TOTAL_FRAMES} fps={FPS} width={1920} height={1080} />
);
