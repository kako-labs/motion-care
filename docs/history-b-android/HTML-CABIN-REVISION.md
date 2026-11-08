# 차량 전용 세로 영역 · 좌석 선택 수정

2026-11-08 후속 피드백: 차량은 반드시 커야 하며 차량 열의 위아래에 다른 내용을 놓지 않는다. 환경·경로 메뉴는 유지하고, 사선 조언 카드는 바꾼다. 플러스 선택이 이상한 문제도 실제로 재현해 수정했다.

이번 변경은 `feat/history-b-layout-redesign`의 HTML 후속 작업이다. Android·공통 앱·DB·SDK·서비스·내비는 변경하지 않았다. 이전 HTML은 `f880816`, 기존 Android는 `feat/history-b-android`의 `9996018`로 유지한다.

## 적용

- 왼쪽은 차량만 있는 전용 열이다. 큰 제목·차량 아래 캡션·하단 기록 목록을 제거하고 오른쪽 출발 준비 영역에 모았다. 1600×900에서 차량 박스 높이는788px이며 전용 열 높이와 같다. 1440×760,1024×768,1024×600에서도 전용 열 높이를 채운다. 세로 화면에서는 가용 너비와 높이 안에 차량 전체를 최대 크기로 표시하고 정보 영역은 별도 스크롤 구간으로 둔다.
- 사용자가 좋다고 한 환경·경로 행의 아이콘/구분선 형식은 유지했다. 조언은 사선 외곽 카드 대신 열린 제목·비교 수치·상세 링크로 정리했다. 다른 화면의 B 색감·자산·상세 그래프는 유지한다.
- 좌석 선택은 화면 끝의 긴 패널 대신 차량 옆 작은 대화상자다. 좁은 화면은 작은 하단 대화상자를 사용한다. 실제 렌더 크기를 측정해 화면 안에 배치하고, 위치를 정한 뒤 preventScroll로 초점을 옮긴다. 기존의 슬라이드 이동과 배경 흐림은 제거했다.
- 좌석 선택/비우기/자리 교환과 어린이 운전석 제한, 키보드 순환/Escape/초점 복귀는 유지한다. 실제 사용자 DB와 무관한 기존 HTML 시연 저장만 사용한다.

## 근거

- seat-picker 배치 회귀 검사는 구현 파일이 없을 때 실패한 뒤 구현 후 통과했다. 기존 시나리오·조언·replay 검사까지 Node4개 모두 통과.
- 7개 화면 크기에서5개 좌석, 총35개 선택 상태가 화면 안에 위치한다. 문서/메인의 가로 스크롤0, 초점은 대화상자 안, 배경 흐림none을 확인했다.
- 실제 포인터로 선택·비우기·자리 교환·바깥 클릭 닫기를 통과했다. 키보드 Tab/Escape와 인물 목록으로의 초점 복귀, 어린이 운전석 비활성화를 확인했다. 새 조언에서 동일한j1 운행/감속 상세로 이동도 통과했다.
- 9개 화면×4개 너비,36개 배치에 가로 넘침 없음. 독립 소스 리뷰에서 Critical/Important 없음. 브라우저 증거는 직접 검증했다.
- 원본 루트 파일이f880816과 일치하는지 확인하고 사본을 보존한 뒤 필요한 HTML 파일만 동기화했다. 기존 미커밋 작업은 보존했다. 최신 미리보기를 Chrome에 열었다.

[화면 모음/전후 비교](../design-archive/history-b-2026-11-07/ui-review/selected-b/review.html), [배치 검증 JSON](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/geometry.json).

| 화면 크기 | 첫 화면 | 좌석 선택 |
| --- | --- | --- |
|1600×900|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/1600-900-home.png)|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/1600-900-picker.png)|
|1024×768|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/1024-768-home.png)|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/1024-768-picker.png)|
|390×844|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/390-844-home.png)|[보기](../design-archive/history-b-2026-11-07/ui-review/selected-b/previews/cabin/390-844-picker.png)|
