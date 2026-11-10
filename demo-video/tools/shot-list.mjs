// timeline.ts 를 읽어 docs/shot-list.md (장면표) 를 만든다. 사용: node tools/shot-list.mjs
import {buildSync} from 'esbuild';
import {mkdirSync, writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

mkdirSync('.cache', {recursive: true});
mkdirSync('docs', {recursive: true});
buildSync({entryPoints: ['src/config/timeline.ts'], bundle: true, format: 'esm', outfile: '.cache/timeline.mjs', logLevel: 'error'});
const {PLACED, CHAPTERS, TOTAL_SEC, BRAND_INTRO_SEC, BRAND_OUTRO_SEC} = await import(pathToFileURL('.cache/timeline.mjs').href + `?t=${Date.now()}`);

// 각 장면에서 실제로 해야 하는 조작 (새로 녹화할 때 따라 할 내용)
const ACTIONS = {
  'intro-console': 'D 기어, 가속 페달 약 15%로 출발. 0→10km/h. 앱은 "운행 기록 중" 화면',
  'intro-pleos': '계속 직진 가속. 12→20km/h',
  'intro-data': '계속 직진 가속. 23→30km/h',
  'intro-caption': '30km/h 유지',
  'intro-stop': '브레이크로 정지. 30→0km/h',
  'intro-reset': 'P 기어, 속도 0. 모션케어 메인 화면 (운행 전)',
  'main-home': '메인 화면을 그대로 보여줌 (조작 없음)',
  'main-seat': "하단 탭 '좌석' 누름 → 좌석 화면",
  'main-history-tab': "하단 탭 '기록' 누름 → 전체 운행 기록",
  'main-profile': "하단 탭 '프로필' 누름 → 운전 경향",
  'main-why': "메인으로 돌아와 '왜 이런 조언을 받았나요?' 누름",
  'set-sound': '안내 소리를 음성 → 알림음으로 바꿈',
  'set-driving-off': '주행 중 안내 토글을 껐다가 다시 켬',
  'set-departure-off': '출발 안내 토글을 껐다가 다시 켬',
  'd1-start': "'운행 시작' 누름 → '운행 기록 중'",
  'd1-depart': 'D 기어, 0→30km/h 가속 (앱 화면 유지)',
  'd1-pattern-a': '내비 화면으로 전환. 브레이크 35%로 급정거(30→0) 후 가속 60%로 급출발(0→30) — 1번째',
  'd1-pattern-b': '급정거·급출발 3번째 (2번째는 편집에서 건너뜀). 끝에 30km/h 유지',
  'd1-card': '30km/h 유지 중 내비 위 카드 "정지 지점보다 조금 일찍 감속해보세요" + 알림음. 카드는 구간 시작 약 1.5초 뒤에 떠야 함',
  'd1-stop': '브레이크 8%로 천천히 정지 → P 기어',
  'd1-end': "모션케어 앱으로 돌아와 '운행 기록 종료' 누름",
  'd1-endcard': '종료 카드(이동 평가 · 주행 상세 · 완료) → 완료 → 바뀐 출발 조언',
  'd2-start': "안내 소리를 음성으로 바꾸고 '운행 시작'",
  'd2-accel': 'D 기어, 가속 15%로 0→40km/h, 내비 화면으로 전환',
  'd2-swing-a': '40km/h 유지, 핸들 +30° ↔ −30° 를 3초 간격으로 반복',
  'd2-swing-b': '핸들 반복 마지막 부분 → 0°로 직진',
  'd2-card': '직진 중 카드 "핸들을 조금 더 부드럽게 돌려보세요" + 음성. 음성은 구간 시작 약 1초 뒤',
  'd2-stop': '천천히 정지 → P 기어 → 앱에서 운행 기록 종료',
  'd2-endcard': '종료 카드 → 완료 → 출발 조언이 "좌우 움직임이 이어지지 않도록 부드럽게 조향해 보세요"로 바뀜',
  'd3-env': '위치를 델리로(대기질 매우 나쁨), 공기 흐름 외기 유입, 운전석 26°C. 앱을 닫았다 다시 열어 환경 카드 갱신',
  'd3-depart': "'운행 시작' → D 기어, 0→30km/h, 내비 화면",
  'd3-card': '환경 카드 "외기 유입을 줄이고 공기청정 기능을 확인해보세요" + 음성. 음성은 구간 시작 약 2.3초 뒤',
  'd3-recirc': 'Virtual Console 에서 내기 순환 켬 → 천천히 정지 → P',
  'd3-end': '앱에서 운행 기록 종료 → 종료 카드 → 완료',
  'rec-list': "하단 탭 '기록' → 방금 세 운행이 맨 위에 있음",
  'rec-detail': '가장 최근 운행을 눌러 운행 상세 → 아래로 스크롤 (속도 그래프, 관찰된 움직임, 동승자 응답, 다음 출발 조언, 동승자 평가 버튼)',
  'rec-back': '뒤로 가서 기록 목록',
  'noti-off': '시스템 설정에서 앱 알림을 끈 뒤 앱을 다시 엶 → "알림 켜기" 안내',
};

const mmss = (s) => `${Math.floor(s / 60)}:${(s % 60).toFixed(1).padStart(4, '0')}`;
const rows = PLACED.map((p) => {
  const speed = ((p.src[1] - p.src[0]) / p.dur).toFixed(1);
  const opts = [p.full ? `전체화면(${p.full})` : '', p.focus ? `확대(${p.focus.pane} ${p.focus.from}~${p.focus.to}초, ${p.focus.zoom}배)` : '', p.audio ? '원본 소리' : '', p.dip ? '암전 전환' : '']
    .filter(Boolean).join(', ');
  return `| ${mmss(p.start)}~${mmss(p.end)} | \`${p.id}\` | ${CHAPTERS[p.chapter]} | ${p.title} | ${ACTIONS[p.id] ?? ''} | ${p.src[0]}~${p.src[1]} | ${p.dur} | ${speed}배 | ${opts} |`;
});

const md = `# 장면표 (timeline.ts 에서 자동 생성)

전체 ${mmss(TOTAL_SEC)} = 브랜드 소개 ${BRAND_INTRO_SEC}초 + 아래 ${PLACED.length}개 장면 + 브랜드 마무리 ${BRAND_OUTRO_SEC}초.
"기존 원본 구간"은 기존 6분 영상(public/source.mp4)의 초입니다. 새로 녹화하면 이 값만 새 녹화본 기준으로 다시 적으면 됩니다.

| 새 영상 시각 | id | 단계 | 맨 위 한 줄 | 이 장면에서 하는 조작 | 기존 원본 구간(초) | 길이(초) | 배속 | 연출 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
${rows.join('\n')}
`;
writeFileSync('docs/shot-list.md', md);
console.log('docs/shot-list.md', PLACED.length, '장면');
