# B Android 연결 이후의 인수인계

기록 담당 화면 연결·기기 검증·인계 준비를 마무리했다. Android 소스는 `0d8f057`, HTML 기록 일관성 수정은 `0298fcf`다. 사용자 명시 승인 후 원격 브랜치 push와 [Draft PR #19](https://github.com/kako-labs/motion-care/pull/19) 생성을 완료했다. [전달 상태](DELIVERY.md)와 [팀 Notion 인계](TEAM-NOTION.md)를 먼저 읽는다. 다음 작업은 팀원의 리뷰·최종 조립이다.

## 현재 작업 위치

- 개인 작업 폴더 안의 `.android-b-integration`, 별도 clone의 `feat/history-b-android` 브랜치, origin `kako-labs/motion-care`.
- 원본 개인 저장소·`motion-care-mine`·`motion-care-polish`·`hyundai-demo-save`는 기존 변경을 보존했다. 어느 트리든 수정 전 status부터 확인한다.
- 선행 PR #10/#17을 로컬 결합한 뒤 원래 미커밋 화면을 `315bee2`로 보존했다. 신규 표시 연결의 리뷰 범위는 `315bee2..HEAD`다. 이전 중단 시점은 `698d3a2`이며, 최신 저장은 이후 커밋이다.
- 공유 main 직접 병합·강제 push 없음. 원격 `feat/history-b-android`와 Draft PR #19로 코드·검증 캡처·인계 문서를 공유했다.

## 먼저 읽기

1. [README](README.md), [최종 검증](VERIFICATION.md), [통합 검토](INTEGRATION-REVIEW.md).
2. `AGENTS.md`, `docs/architecture.md`, `docs/ownership.md`.
3. `docs/design-archive/history-b-2026-11-07/demo-kit/APPROVED-BASELINE-2026-11-08.md`: 확정 B 기준.

## 실제 연결한 부분

출발 차량·좌석 서랍·최근 기록·사선 제안, 운전자 기록과 동승자 선호/프로필 수정, 조언, 감속 상세 그래프, 평가를 기존 history ViewModel·Repository·저장 흐름에 연결했다. 기본 앱 진입 계약도 유지한다. 1024 가로 창과 동승자 헤더 중복을 보정했다.

시연 Activity만 지영·서아와 j1~j3를 기본 제공한다. 정상 사용자 DB에 가짜 운행을 주입하지 않는다. 실제 표본은 원본 해상도를 유지하며, 0.2초 상세 합성 자료는 debug fixture에만 있다.

표준 verify, 전체 단위 검사 274개, 실제 Android UI/저장 검사 6개, release 시연 제외 검사를 확인했다. 실제 SQLite 테스트도 정상 사용자 DB와 파일 이름 공간을 분리한다. 최종 화면은 `device-checks/final/` 기준이다.

## 남은 팀원 통합

1. 선행 PR #10/#17과 최신 main을 확인하고 충돌을 검토한다. 기존 미커밋 작업을 덮어쓰지 않는다.
2. 앱 홈의 바깥 틀과 B 출발 화면의 최종 배치는 주행 담당이 결정한다. 현재 일반 앱의 기존 버튼으로 큰 B 화면에 진입할 수 있다.
3. `review-patches/app-finished-summary.patch`를 검토해 기존 FinishedRoute를 홈의 종료 분기에 연결한다. 기존 콜백과 종료 정책을 유지하는 준비 패치다.
4. 실제 운행 시작 시 `profiles.assignCurrentSeating(tripId)`를 연결한다. 선행 PR #10의 `linkSeatingIfMissing()` 임시 방식은 현재 좌석을 과거 운행에 붙일 수 있는 기존 한계가 있으므로 이 통합과 함께 정리한다.
5. 환경 current/history 주입·허용 의존성과 내비·실제 안내는 해당 담당이 연결한다. 이 브랜치가 SDK·서비스·DB·탐지·내비 정책을 변경한 것은 아니다.
6. 합친 고정 APK에서 실제 탑승→주행→종료→기록→평가 저장→재진입을 확인하고 촬영한다. 일반 Android 검증을 Pleos/실차 결과로 소개하지 않는다.

## 재현 도구

- debug 시연: `adb shell am start -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity`
- 정상 앱: `adb shell am start -n dev.motioncare.scaffold/dev.motioncare.app.MainActivity`
- 실제 UI 검사 패치: `app-history-b-ui-test.patch`, 실제 SQLite 저장 검사: `app-history-b-storage-ui-test.patch`. 적용 전 검사 후 프로젝트 에뮬레이터에서만 실행하고 앱 담당이 기존 테스트와 통합한다.
- `tools/capture_history_b_final.py`는 지정한 크기로 캡처한 뒤 기기 크기를 복구한다. 기본 검토 크기는 1682×1210, 가로 분할 검토는 1024×768이다.
- 로컬 검증용 APK: `artifacts/motion-care-b-debug.apk`. SDK·AVD·키·DB 파일은 Git에 저장하지 않는다.
