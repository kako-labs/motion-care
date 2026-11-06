# environment

담당: 주행 담당

구현한 것: `data/environment` 모듈. 바깥 온도·실내 공조(차량), 바깥 대기질(Open-Meteo), 상태·이유, 1시간 캐시, 주행 시작·1시간마다·종료 때 저장. 근거는 [환경 데이터의 근거](../environment.md)에 있습니다.

주행 중 안내 카드와의 연결: 대기질과 내기·외기 상태가 어긋날 때의 공조 카드 두 종([근거](../guidance-card.md#환경-카드)).

후속 구현: 출발 전·주행 상세 화면의 표시(기록 담당).

화면과 ViewModel은 `EnvironmentRepository`로만 읽습니다. 외부 API 호출은 이 모듈 안에서만 합니다.
