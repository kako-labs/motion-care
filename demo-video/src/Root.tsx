import {Composition} from 'remotion';
import {FPS, TOTAL_FRAMES} from './config/timeline';
import {Demo} from './Demo';
import {Short} from './short/Short';
import {Story, STORY_FRAMES, STORY_TTS_FRAMES} from './story/Story';
import {FPS as SHORT_FPS, TOTAL_FRAMES as SHORT_FRAMES} from './short/timeline';

export const Root = () => (
  <>
    <Composition id="MotionCareDemo" component={Demo} durationInFrames={TOTAL_FRAMES} fps={FPS} width={1920} height={1080} />
    <Composition id="MotionCareStory" component={Story} durationInFrames={STORY_FRAMES} fps={30} width={1920} height={1080} />
    <Composition id="MotionCareStoryTts" component={Story} durationInFrames={STORY_TTS_FRAMES} fps={30} width={1920} height={1080}
      defaultProps={{narration: true}} />
    <Composition id="MotionCare3Min" component={Short} durationInFrames={SHORT_FRAMES} fps={SHORT_FPS} width={1920} height={1080} />
  </>
);
