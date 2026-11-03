# tripanalysis

담당: 기록 담당

후속 구현: 저장된 기본 탐지 이벤트 집계·최근 비교·개인별 요약. 아직 구현하지 않았습니다.

`feature/history`에 구현하고, 저장된 기록은 data 모듈의 Repository로 읽습니다. 집계 결과를 저장해야 하면 `data/profile`이 아닌 해당 도메인의 Repository를 함께 설계합니다.
