import narration from '../data/narration.json';
import story from '../data/story.json';
import {ENDING_SEC, MAIN_END, PLACED, capAt} from './scenes';

/**
 * The narrated version (MotionCareStoryTts): each caption is read aloud as it appears (tools/tts/narrate.py makes the
 * clips). A line waits for the one before it to finish, and the card's line waits for the chime to end, so nothing
 * talks over the app's sound. Times are seconds in the body (after the title), like scenes.ts.
 *
 * The title holds a little longer than in the silent version, and the end of its line runs on while the car screen
 * fades in; holding it for the whole line would take the video past 3:15.
 */
type Clip = {text: string; sec: number};
const CLIPS = narration as Record<string, Clip>;

/** After a caption appears (and after a scene's dissolve) before its line starts. */
const LEAD = 0.2;
/** Between two lines when one has to wait for the other. */
const GAP = 0.25;
/** After the chime ends before the card's line starts. */
const AFTER_CHIME = 0.3;

export type Line = {key: string; at: number; sec: number; text: string; lag: number};

const chime = story.sounds[0];

export const TITLE_LINE_AT = 0.5;
export const TTS_TITLE_SEC = 6.2;
/** The body starts 0.2 s before the title ends (Story.tsx). */
const titleFree = TITLE_LINE_AT + (CLIPS.title?.sec ?? 0) + GAP - (TTS_TITLE_SEC - 0.2);

const place = (): Line[] => {
  const lines: Line[] = [];
  let free = titleFree;
  for (const p of PLACED) {
    p.caps.forEach((c, i) => {
      const key = `${p.id}.${i}`;
      const clip = CLIPS[key];
      if (!clip) return;
      const shown = p.start + capAt(c, true);
      let want = shown + LEAD + (i === 0 ? p.dissolve ?? 0 : 0);
      if (c.bell && chime) want = Math.max(want, p.start + (chime.end - p.from) / p.rate + AFTER_CHIME);
      const at = Math.max(want, free);
      lines.push({key, at, sec: clip.sec, text: clip.text, lag: at - shown});
      free = at + clip.sec + GAP;
    });
  }
  let at = MAIN_END + 0.6;
  for (const key of ['ending.0', 'ending.1']) {
    const clip = CLIPS[key];
    if (!clip) continue;
    lines.push({key, at, sec: clip.sec, text: clip.text, lag: 0});
    at += clip.sec + 0.35;
  }
  return lines;
};

export const LINES = place();

const endings = LINES.filter((l) => l.key.startsWith('ending.'));
const lastEnding = endings[endings.length - 1];
export const TTS_ENDING_SEC = Math.max(ENDING_SEC, lastEnding ? lastEnding.at + lastEnding.sec + 0.9 - MAIN_END : 0);
