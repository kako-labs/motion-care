# 통합 담당 검토 순서

> 이 문서는 초기 B 연결 시점의 기록입니다. 최신 기준과 패치 적용 여부는 [통합 전 준비 현황](../meeting-readiness/README.md)을 우선합니다. 특히 운행 시작 시 좌석 연결과 출발 환경 current 주입은 현재 코드에 이미 있습니다.

## 이번 브랜치의 읽는 기준

PR #10(좌석·평가 저장)과 #17(B 표시 준비)을 로컬에서 결합한 후 기존 기록 담당 미커밋 화면을 `315bee2`로 보존했다. 이번 표시 연결은 **`315bee2..HEAD`** 범위다. main 기준 전체 diff에 보이는 선행 계약·DB 변경을 이번 작업에서 새로 만든 것으로 해석하지 않는다.

일반 앱은 기존 `ReadyContent → HistoryRoutes.PROFILES`로 들어가 실제 Repository를 읽는다. 지영·서아/j1~j3를 보여 주는 기본 시연은 별도의 debug Activity다. 두 진입을 혼동하지 않는다.

## 실제 연결 / 적용 준비

| 영역 | 현재 연결 | 통합 담당 확인 |
| --- | --- | --- |
| 큰 차량·좌석 서랍·최근 운행·출발 조언 | 기존 DepartureViewModel·historyGraph에 연결 | 홈 외곽 프레임 안의 최종 배치 |
| 운전자 기록·동승자 선호·프로필 수정 | 기존 PersonDetailViewModel.update 사용 | 기존 사용자 프로필로 확인 |
| 조언과 선택 감속 상세 | 기존 저장 Observation과 같은 운행 ID/사건 번호 전달 | 실제 장시간 운행 자료로 확인 |
| 운행 상세·평가 | 기존 TripExperienceViewModel과 saveRating 사용 | 실제 탑승 링크가 시작 때 고정되는지 확인 |
| 종료 요약 | feature의 FinishedRoute 준비 및 debug에서 실행 | app-finished-summary.patch 적용 여부 리뷰 |
| 앱 홈·주행 시작/권한/서비스 | 기존 코드 유지 | 주행 담당 소유 |
| 운행 시작 시 좌석 고정 | 선행 PR #10의 임시 연결 유지 | assignCurrentSeating(tripId)를 실제 시작 시 연결 |
| 환경 | 표시 컴포넌트 준비 | current/history 주입·허용 의존성 공동 확인 |
| 경로·내비·차량 SDK·탐지 | 이번 변경 없음 | 기존 담당 구현으로 별도 검증 |

## 패치 적용 전

1. main과 선행 PR들의 최신 상태·충돌을 읽기 전용으로 확인한다. 이미 별도 구현이 있으면 새로 덮어쓰지 않는다.
2. `git apply --check docs/history-b-android/review-patches/app-finished-summary.patch`로 종료 분기 패치를 검토한다. 이 패치는 기존 콜백·종료 정책을 바꾸지 않는다.
3. UI 검사 패치는 `app-history-b-ui-test.patch`, 실제 SQLite 저장 검사는 `app-history-b-storage-ui-test.patch`다. 이 브랜치의 app 소스에는 적용하지 않은 상태로 전달한다.
4. 실제 SQLite 검사는 UUID 접두어를 가진 테스트 DB/설정만 사용한다. 사용자 DB에 합성 운행을 주입하지 않는다. 정상 DB 경로의 SHA-256이 바뀌지 않는지도 검사한다.
5. UI 검사를 실행하면 Gradle이 검증용 앱을 정리할 수 있으므로 사용자 데이터가 있는 기기 대신 프로젝트 에뮬레이터에서 실행한다.

## 조언·그래프의 해석

이번 값은 분석 시간으로 정규화한 관찰 빈도다. 동승자 평가를 임의의 종합 점수로 바꾸거나 멀미 효과로 해석하지 않는다. 실제 표본에 없는 0.2초 측정은 생성하지 않는다. 시연의 0.2초 자료는 승인한 HTML 합성 fixture에만 있다.

## 촬영 전 팀원 확인

- 고정 통합 커밋/APK에서 탑승 연결 → 실제 주행 시작 → 종료 → 목록 → 상세 → 평가 저장 → 재진입.
- 기록 없음·분석 부족·미응답·중단된 운행·안내 OFF·조향 미지원 구별.
- 실제 시작 시점의 탑승 링크와 과거 운행 링크 보존.
- 환경 출처·시각·상태 및 기록 없음 처리.
- Pleos 화면 분할·실제 내비 위 안내는 기존 담당의 에뮬레이터/SDK로 확인.

일반 Android API 35의 UI·저장 검증을 Pleos 실차 검증으로 보고하지 않는다.
