// Reads src/short/timeline.ts and writes docs/shot-list-3min.md (scene table of the 3 minute cut).
// usage: node tools/shot-list3.mjs
import {buildSync} from 'esbuild';
import {mkdirSync, writeFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

mkdirSync('.cache', {recursive: true});
buildSync({entryPoints: ['src/short/timeline.ts'], bundle: true, format: 'esm', outfile: '.cache/timeline3.mjs', logLevel: 'error'});
const {PLACED, CHAPTERS, TOTAL_SEC, BRAND_INTRO_SEC, BRAND_OUTRO_SEC} = await import(pathToFileURL('.cache/timeline3.mjs').href + `?t=${Date.now()}`);

// 테이크에서 그 구간에 실제로 한 조작 (app_scenes.py, drive_takes.py)
const ACTIONS = {
  'home-hero': '홈 화면 그대로. 큰 카드에 지난 기록으로 고른 출발 조언',
  'home-cards': '홈 화면 그대로. 함께 타는 사람, 지금 차 안팎 환경 카드',
  seats: "하단 탭 '좌석' → 출발 전 안내(좌석 배치)",
  character: "빈 좌석 '+' → 사람 추가 창에서 캐릭터를 고름. 저장하지 않고 취소",
  'gear-d': 'Virtual Console에서 기어 P → D. 자동 기록이 켜져 있어 앱이 "운행 기록 중"으로 바뀜',
  depart: '내비 화면으로 전환, 가속 페달 15%로 40km/h까지 가속',
  stops: '40km/h에서 브레이크 30%로 정지, 가속 페달 15%로 다시 출발. 세 번 반복 (내기 순환 켬)',
  card: '세 번째 정지 뒤 서 있을 때 내비 위 카드 "정지 지점보다 조금 일찍 감속해보세요" + 알림음, 다시 출발',
  park: '브레이크 8%로 천천히 정지 → P',
  finish: "앱으로 돌아와 '운행 기록 종료' → 종료 카드 → '이동 평가'",
  rate: "이동 평가: 서아 탭, '보호자가 대신', 별 3개",
  'rate-reason': "불편했던 순간 '출발·정차'를 고르고 저장",
  'air-depart': 'Virtual Console에서 내기 순환 끔(외기 유입) → D → 가속 페달 15%로 출발. 바깥 대기질은 나쁨',
  'air-card': '약 28km/h로 달리는 중 내비 위 카드 "외기 유입을 줄이고 공기청정 기능을 확인해보세요" + 알림음',
  records: "하단 탭 '기록' → 전체 운행 기록 20건, 아래로 스크롤",
  filter: "사람 칩 '서아'를 눌러 서아가 탄 운행만",
  detail: '11월 11일 18:21 운행 상세 → 정차 직전 큰 감속 2번째 구간 선택',
  'detail-env': '아래로 스크롤: 이 운행의 차 안 환경, 다음 출발',
  outing: '10월 24일 고속도로 나들이(1시간 8분 · 77.4km) 상세 → 아래로 스크롤',
  profile: "하단 탭 '프로필' → 지영: 최근 운전 경향, 아래로 스크롤해 전체 운전 기록 그래프",
  'next-home': '홈으로 돌아와 출발 조언',
  advice: "'왜 이 조언인가요?' → 최근 3회 평균과 이전 3회 평균",
  'advice-calc': "'실제 횟수와 계산 방법'을 펼침",
};

const mmss = (s) => `${Math.floor(s / 60)}:${(s % 60).toFixed(1).padStart(4, '0')}`;
const take = {app: '앱', drive: '주행'};
const rows = PLACED.map((p) => {
  const rate = (p.to - p.from) / p.dur;
  const extra = [p.layout === 'quad' ? '4분할' : 'Pleos 크게', p.cams ? '확대' : '', p.audio ? '소리' : ''].filter(Boolean).join(', ');
  return `| ${mmss(p.start)}~${mmss(p.end)} | \`${p.id}\` | ${CHAPTERS[p.chapter]} | ${p.title} | ${ACTIONS[p.id] ?? ''} | ${take[p.src]} ${p.from}~${p.to} | ${p.dur} | ${rate.toFixed(1)}배 | ${extra} |`;
});

const md = `# 3분 영상 장면표 (src/short/timeline.ts 에서 자동 생성)

전체 ${mmss(TOTAL_SEC)} = 브랜드 소개 ${BRAND_INTRO_SEC}초 + 아래 ${PLACED.length}개 장면 + 브랜드 마무리 ${BRAND_OUTRO_SEC}초.
테이크 구간은 각 녹화본(public/rec/app_pleos.mp4, drive_pleos.mp4)의 첫 프레임을 0초로 한 초입니다. 다시 만들려면 \`node tools/shot-list3.mjs\`.

| 새 영상 시각 | id | 단계 | 맨 위 한 줄 | 테이크에서 한 조작 | 테이크 구간(초) | 길이(초) | 배속 | 연출 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
${rows.join('\n')}
`;
writeFileSync('docs/shot-list-3min.md', md);
console.log('docs/shot-list-3min.md', PLACED.length, 'scenes', mmss(TOTAL_SEC));
