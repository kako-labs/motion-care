import {existsSync} from 'node:fs';
import {Config} from '@remotion/cli/config';

Config.setVideoImageFormat('jpeg');
Config.setJpegQuality(92);
Config.setOverwriteOutput(true);

// 회사 PC에서는 Remotion 전용 브라우저가 실행되지 않아, 설치된 Chrome 을 씁니다. 없으면 기본 동작.
const chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
if (existsSync(chrome)) {
  Config.setBrowserExecutable(chrome);
  Config.setChromeMode('chrome-for-testing');
}
