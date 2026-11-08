# 확정 B HTML · 다른 노트북에서 열기

UI 기준: `c29f155`, 브랜치 `feat/history-b-layout-redesign`. 큰 차량 전용 세로 영역과 차량 옆 작은 좌석 선택창이 있는 버전이다.

1. 저장소 권한이 있는 GitHub 계정으로 위 브랜치를 연다.
2. Code → Download ZIP으로 내려받고 압축을 푼다. 또는 새 폴더에 `git clone --depth 1 --branch feat/history-b-layout-redesign https://github.com/kako-labs/motion-care.git`을 실행한다.
3. `docs/design-archive/history-b-2026-11-07/ui-review/selected-b/index.html`을 Chrome으로 연다.
4. 전체 화면 캡처와 전후 비교는 같은 폴더의 [review.html](review.html)을 연다.

`assets`·CSS·JS를 포함한 폴더 전체가 필요하다. HTML 한 파일만 받으면 그림/동작이 빠진다. 서버, Android Studio, 에뮬레이터는 필요하지 않다. GitHub의 HTML 링크는 소스 보기이며, 이번 작업에서 실행 웹사이트를 배포한 것은 아니다.

기본은 지영 운전·서아 동승·운행 3건이다. 좌석·평가 시연 상태는 브라우저 로컬 저장이며 기기 사이에 자동 동기화되지 않는다. 이 HTML의 시연 값은 합성이고 실제 차량·내비·사용자 DB 연결과 구분한다.

최근 확정 HTML 구조는 Android에 아직 옮기지 않았다. 기존 Android 연결/검증은 `feat/history-b-android` / `9996018`의 별도 결과다. main에 병합하지 않았다.

[화면·그래프 읽는 법과 멀미와의 관계](../../../../history-b-android/DESIGN-GUIDE.md) · [노션의 확정 화면 설명](https://app.notion.com/p/412ae4c07fd68016ab62df5763ee0487#412ae4c07fd6801aafa0ea9a868f6c51).
