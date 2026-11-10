/** 브랜드 소개·마무리와 화면 곳곳의 고정 문구. 문구만 바꿀 때는 이 파일을 고치세요. */
export const COPY = {
  brandIntro: {
    name: '모션케어',
    tag: '동승자 멀미를 덜어 주는 운전 도우미',
    line2: '급정거, 급출발, 좌우 흔들림을 차량 데이터로 찾아\n함께 탄 사람이 덜 멀미하는 운전을 안내합니다.',
  },
  brandOutro: {
    name: '모션케어',
    tagline: '함께 타는 사람까지 편안한 운전',
    flow: ['차량 데이터 수집', '움직임 분석', '동승자 평가', '다음 운전 개선'],
    footnote: 'Pleos Connect 에뮬레이터(AAOS API 34)에서 녹화한 시연입니다. 실차에서는 확인하지 않았습니다.',
  },
  header: {
    note: '모션케어 주행 시연 · Pleos Connect 에뮬레이터 녹화',
  },
  paneLabels: {
    console: '차량 제어 · Virtual Console',
    pleos: 'Pleos 화면 · 내비와 모션케어 앱',
  },
  analysis: {
    title: '움직임 분석',
    tag: '입력값 기반 계산 · 주황 선은 앱이 기록을 구분하는 기준',
    accel: '가감속',
    accelHint: '1초 동안의 속도 변화',
    lateral: '좌우 움직임',
    lateralHint: '조향각과 속도로 추정',
  },
  introStepPrefix: '화면 소개',
};
