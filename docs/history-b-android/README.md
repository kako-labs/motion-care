# B 화면 Android 연결 · 2026-11-08

## 연결한 범위

기존 기록 담당 PR #10과 B 준비 PR #17을 합친 기준에서 `feature/history` 표시를 연결했다. 원본 작업 트리의 미커밋 화면·얼굴 변경은 복사본의 `315bee2`로 먼저 보존했다. 원본 트리와 공유 main을 수정하거나 병합하지 않았다.

- 출발: 큰 차량과 얼굴·좌석 서랍, 현재 운전자의 최근 완료 운행, 사선 제안 패널. `DepartureViewModel`의 저장·좌석·안내 스위치를 그대로 사용한다. 최근 운행을 누르면 해당 운행으로 이동한다.
- 프로필: 기존 `PersonDetailViewModel`의 운전자 전체 기록과 동승자 선호·평가. 기존 선호 편집 콜백을 유지한다.
- 조언: 기존 선정 정책과 분석 가능한 운행을 사용한다. 이번 운행과 직전 최대 2회 평균을 구별하고, 선택 감속을 동일 운행 상세로 전달한다.
- 운행 상세: 기존 `TripExperienceViewModel`의 원본 `TripSnapshot`을 B 리포트로 표시한다. 완료·중단·기록 중 상태, 탑승 정보·응답·조향 미지원 설명을 유지한다.
- 평가: B 별점·이유·사선 저장 패널을 기존 `saveRating`에 연결했다. 보호자·직접 응답, 여러 동승자, 저장 오류·재진입과 건너뛰기 흐름을 유지한다. 수정 중 건너뛰기는 기존 응답을 삭제하지 않는다.
- 종료: 기존 콜백용 간단 패널도 B 표현을 사용한다. 데이터가 연결된 `FinishedRoute`는 준비했으며, 실제 홈의 종료 분기에 넣는 것은 앱 담당 검토용 패치다.

## 그래프와 집계

실제 Repository의 표본 해상도를 그대로 그린다. 없는 5Hz 측정이나 곡선을 만들어 넣지 않는다. 원본 `Observation`의 시각을 표시하고 선택 구간 앞 24초·뒤 36초를 확대한다. 터치한 시각의 저장 표본과 소수점 km/h를 읽을 수 있다. 없는 값·기록 공백은 선을 끊고 배경으로 표시한다.

표시 집계의 기존 버그도 회귀 검사 후 수정했다. 유효 표본을 먼저 걸러 이웃으로 묶으면 중간의 `UNAVAILABLE`을 건너뛸 수 있었다. 원래 이웃한 두 표본이 모두 유효할 때만 거리·분석시간을 적분하도록 수정했다. 기존 최대 5초 간격과 조언 선정 정책은 유지했다. DB·탐지기·임계값은 변경하지 않았다.

## 지영 시연과 실제 사용자 기록

`feature/history/src/debug`에만 `HistoryDemoActivity`, 합성 JSON, 시연 Repository가 있다. 지영·서아와 j1~j3가 기본이며 앱의 일반 진입이나 실제 DB에는 주입하지 않는다. 0.2초 상세 표본은 승인한 HTML fixture에서 내보낸 합성 데이터다. 다른 패턴을 새 탐지기로 계산하지 않으므로 이 시연 JSON에 없는 관찰은 추가하지 않는다.

시연의 평가 쓰기만 별도 `history_b_debug_only` SharedPreferences에 저장한다. `unanswered` 시나리오는 별도의 시연 저장 공간을 사용한다. 앱의 실제 프로필·주행 DB와 연결하지 않는다. 디버그 Activity를 닫고 정상 앱을 열면 기존 Repository와 기존 사용자 기록을 사용한다.

```sh
adb shell am start -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity
# 빈 기록 / 미응답 / 중단된 최근 운행 검증
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez empty true
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez unanswered true
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez interrupted true
```

에뮬레이터는 일반 Android API 35 x86_64의 `mc_car`, 1682×1210 및 1024×768·160dpi를 사용했다. 1024 가로 창에서도 차량·좌석·최근 기록·사선 제안을 함께 볼 수 있게 배치를 보정했다. 동승자 프로필은 얼굴·편집 버튼을 한 번만 표시한다. Pleos 실기기·실차 SDK·실시간 안내 검증으로 소개하지 않는다. 검사 결과와 실제 캡처는 [검증 기록](VERIFICATION.md)에 모은다.

## 팀원에게 남긴 연결

1. 선행 PR #10의 좌석·평가 계약과 PR #17의 준비 컴포넌트를 먼저 검토한다. 이번 작업은 새로운 공통 계약·DB 변경을 추가하지 않는다. `315bee2` 이후의 변경은 history·검증 도구·문서뿐이다.
2. 일반 앱의 `ReadyContent → HistoryRoutes.PROFILES` 연결은 그대로여서 기존 버튼으로 큰 B 출발 화면을 열 수 있다. 앱 첫 화면의 흰색 개발용 바깥 틀과 주행 시작·권한·서비스 조립은 앱 담당 소유로 유지했다. 큰 B 화면을 홈 자체로 올리는 최종 배치는 앱 담당이 기존 주행 상태 전환과 함께 결정한다.
3. [app-finished-summary.patch](review-patches/app-finished-summary.patch)는 홈의 종료 분기를 기존 데이터가 연결된 `FinishedRoute`로 바꾸는 최소 패치다. 콜백과 주행 종료 정책은 그대로다. **적용하지 않았다.**
4. [app-history-b-ui-test.patch](review-patches/app-history-b-ui-test.patch)는 기존 오래된 placeholder 문자열에 의존하지 않는 B 화면 UI 검사다. 임시 검증에만 적용한 뒤 앱 트리에서 제거한다. 테스트 소유자가 기존 `ScaffoldFlowTest`와 함께 정리해야 한다.
   [실제 저장소 UI 검사 패치](review-patches/app-history-b-storage-ui-test.patch)는 기존 공개 SQLite Repository를 UUID 이름 공간에서 실행한다. UI로 선호와 평가를 저장하고 Repository를 다시 생성해 보존을 확인한다. 정상 사용자 DB의 SHA-256도 대조한다.
5. 운행 시작 시 `profiles.assignCurrentSeating(tripId)` 호출은 기존 PR #10의 미완료 연결이다. 이번에는 기존 상세 진입 시 연결하는 임시 방식을 바꾸지 않았다. 현재 좌석을 과거 기록에 잘못 붙일 수 있는 기존 한계가 있으므로 주행 담당이 실제 시작 시점과 묶어 통합해야 한다.
6. 환경은 기존 `EnvironmentPanel` 준비 상태다. `EnvironmentRepository.current/history` 주입과 모듈 간 허용 변경은 적용하지 않았다. 경로·내비·서비스·탐지·SDK에는 손대지 않았다.

## 저장·이식

별도 `feat/history-b-android` 브랜치다. 선행 PR #10/#17을 함께 검토해야 하므로 main에 바로 병합하지 않는다. 기존 PR 두 개의 변경이 이 브랜치의 이력에도 포함된다. 이번 신규 표시 변경만 보려면 `315bee2..HEAD`를 비교한다. 기존 미커밋 화면의 보존 커밋은 `315bee2`, 최초 B 연결은 `f8ab752`, 중단 시점 저장은 `698d3a2`다. 최신 연결 지점·적용 준비·팀원 확인 순서는 [통합 검토](INTEGRATION-REVIEW.md)를 기준으로 한다.

공유 시 사용할 파일은 APK·기기 캡처·이 문서이며, 로컬 SDK·AVD·키·DB 파일은 Git에 넣지 않는다.

최신 로컬 소스는 `0d8f057`에 저장했다. 원격 push는 자동 승인 검토에서 명시적 승인 부족으로 거절돼 실행되지 않았다. [전달 상태와 승인 대상](DELIVERY.md)을 확인한다. Draft PR 본문과 적용 패치는 준비 완료 상태다.
