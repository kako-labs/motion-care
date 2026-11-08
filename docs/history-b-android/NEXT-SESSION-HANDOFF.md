# 다음 세션: B Android 연결 마무리

사용자는 2026-11-08 PC 종료 때문에 작업을 여기서 멈추고 기록하라고 요청했다. 추가 구현·배포·병합을 자동으로 이어가지 않는다. 다음 사용자 요청 때 아래에서 재개한다.

## 작업 위치와 보호

- 현재 저장소는 개인 작업 폴더 안의 `.android-b-integration`이다. 별도 로컬 clone, 브랜치 `feat/history-b-android`, origin `kako-labs/motion-care`.
- 원래 개인 저장소·`motion-care-mine`·`motion-care-polish`·`hyundai-demo-save`는 수정하지 않았다. 각 트리 status를 다시 확인하고 기존 미커밋 변경을 보존한다.
- 공유 main은 병합하지 않았다. PR #10/#17은 선행 의존성으로 로컬에서 결합했을 뿐이다.
- 기준 `315bee2`는 기존 기록 담당 미커밋 화면 사본 보존, `f8ab752`는 최초 B 연결. 그 이후 커밋이 리뷰 보정·검증·인계다.
- 원격 push와 새 Draft PR은 아직 하지 않았다. 로컬 코드가 사라진 것으로 오해하고 원본 트리를 reset/pull하지 않는다.

## 먼저 읽기

1. 이 파일, [README](README.md), [VERIFICATION](VERIFICATION.md), [LEDGER](LEDGER.md).
2. 원래 확정 기준: `docs/design-archive/history-b-2026-11-07/demo-kit/APPROVED-BASELINE-2026-11-08.md` 및 `NEXT-SESSION-HANDOFF.md`.
3. 루트 `AGENTS.md`, `docs/architecture.md`, `docs/ownership.md`.

## 이미 한 것

기존 feature/history 화면에 B 차량·얼굴·유리·사선 패널, 동승자 프로필, 평가, 조언, 원본 표본을 쓰는 감속 상세 그래프를 연결했다. 기존 ViewModel·Repository·저장·보호자·다중 동승자 흐름을 유지한다. 실제 앱 홈의 기존 버튼으로 큰 출발 화면에 들어가는 계약도 유지한다.

지영·서아/j1~j3는 `src/debug`의 시연 Repository·JSON에만 존재한다. 사용자 DB에 주입하지 않는다. 실제 자료는 원본 표본 해상도, 시연 상세만 기존 합성 0.2초 자료다. `DemoSession` ViewModel이 시연 Repository를 화면 재생성 동안 유지한다.

표준 verify, 단위 검사 28개, 실제 기기 UI 5개, 360dp 터치 검사 1개 통과. 독립 리뷰의 중단 상태·평균 설명·좌석 겹침과 칩/별점 줄바꿈을 수정했다. 앱 소유 테스트는 패치로 보존하고 임시 적용을 제거했다.

## 다음 순서

1. `git status`, `git log`와 원격 main/#10/#17 상태를 확인한다. 이 브랜치의 신규 범위는 `315bee2..HEAD`다. 이후 feature 코드 수정 없이 검증부터 마무리한다.
2. `release-build.log`와 release APK 생성 여부를 확인한다. PC 종료 시 빌드가 중단될 수 있다. 필요하면 `:app:assembleRelease` 재실행. release ZIP/dex에서 `history-b-synthetic.json`, `HistoryDemoActivity`, `DemoRepositories`가 없는지 검사하고 결과 기록.
3. 에뮬레이터 크기가 360×800으로 바뀌어 있다. `adb shell wm size reset`으로 기본 1682×1210 복구. 앱과 테스트 APK는 connected UI 검사에서 정리될 수 있으므로 설치 상태 확인. 최종 디버그 APK 사본은 `artifacts/motion-care-b-debug.apk`.
4. 일반 `MainActivity`를 열어 실제 Repository 진입과 B 화면을 순회한다. 실제 DB에 가짜 기록을 넣지 않는다. 지영 시연은 별도 `HistoryDemoActivity`로 연다.
5. 최신 출발·프로필·조언·상세·평가·종료 화면 캡처를 갱신하고 1024×768도 확인. 이전 device-checks PNG는 리뷰 전 것도 있어 최종 이미지로 혼용하지 않는다.
6. 검증 결과·인계 문서 갱신 후 `feat/history-b-android`만 push하고 Draft PR 생성. PR 본문에서 선행 #10/#17, 이번 신규 변경, 확인하지 않은 Pleos/실차 범위를 구분한다. main 병합·강제 push 없음.
7. 팀원에게 홈 외곽 프레임·종료 분기 패치·운행 시작 좌석 연결·환경 주입을 인계한다. app/core/data/Gradle/DB/SDK/서비스/탐지/내비를 임의로 바꾸지 않는다.

## 주의할 남은 경계

`TripExperienceViewModel.linkSeatingIfMissing()`의 과거 운행에 현재 좌석을 붙이는 임시 동작은 선행 PR #10에서 온 기존 한계다. 이번 표시 변경에서 바꾸지 않았다. 실제 운행 시작 시점 연결은 팀원 통합 과제로 남긴다.

API 35 일반 에뮬레이터 확인을 Pleos SDK·내비·실차 검증으로 소개하지 않는다. 경로와 환경은 기존 준비 범위를 유지한다.
