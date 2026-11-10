// timeline.ts 의 구간대로 원본(public/source.mp4)을 잘라 붙여 public/remap/f_00001.jpg … 프레임 이미지로 뽑는다.
// 새 영상의 4분할 구간과 프레임이 1:1 로 맞아서, Remotion 은 프레임 번호에 맞는 이미지만 읽으면 된다.
// (이 PC에서는 Remotion 이 영상에서 직접 프레임을 뽑는 방식이 너무 느려서 이렇게 한다.)
// timeline.ts 를 고친 뒤에는 이 스크립트를 다시 돌려야 한다 (npm run render / npm run studio 가 자동으로 돌림).
import {buildSync} from 'esbuild';
import {spawnSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import {existsSync, mkdirSync, readFileSync, rmSync, writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

mkdirSync('.cache', {recursive: true});
buildSync({entryPoints: ['src/config/timeline.ts'], bundle: true, format: 'esm', outfile: '.cache/timeline.mjs', logLevel: 'error'});
const {PLACED, FPS, QUAD_START_SEC} = await import(pathToFileURL('.cache/timeline.mjs').href + `?t=${Date.now()}`);

const parts = PLACED.map((p) => {
  const frames = Math.round((p.end - QUAD_START_SEC) * FPS) - Math.round((p.start - QUAD_START_SEC) * FPS);
  return {a: p.src[0], b: p.src[1], frames};
});
const key = createHash('sha1').update(JSON.stringify(parts)).digest('hex');
if (existsSync('public/remap/f_00001.jpg') && existsSync('.cache/remap.key') && readFileSync('.cache/remap.key', 'utf8') === key) {
  console.log('public/remap 최신 상태 — 건너뜀');
  process.exit(0);
}

const chains = parts.map(({a, b, frames}, i) => {
  const k = frames / FPS / (b - a);
  return `[0:v]trim=start=${a}:end=${b},setpts=(PTS-STARTPTS)*${k.toFixed(6)},fps=${FPS},tpad=stop_mode=clone:stop_duration=1,trim=end_frame=${frames},setpts=PTS-STARTPTS[v${i}]`;
});
const filter = `${chains.join(';')};${parts.map((_, i) => `[v${i}]`).join('')}concat=n=${parts.length}:v=1:a=0[out]`;
writeFileSync('.cache/remap.filter', filter);
rmSync('public/remap', {recursive: true, force: true});
mkdirSync('public/remap', {recursive: true});
console.log(`프레임 이미지 만드는 중 (${parts.length}개 구간, ${parts.reduce((s, p) => s + p.frames, 0)} 프레임)…`);
const r = spawnSync(
  'ffmpeg',
  ['-v', 'error', '-y', '-i', 'public/source.mp4', '-/filter_complex', '.cache/remap.filter', '-map', '[out]', '-an',
    '-fps_mode', 'passthrough', '-q:v', '3', 'public/remap/f_%05d.jpg'],
  {stdio: 'inherit'},
);
if (r.status !== 0) process.exit(r.status ?? 1);
writeFileSync('.cache/remap.key', key);
console.log('완료: public/remap/');
