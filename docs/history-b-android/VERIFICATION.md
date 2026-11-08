# 중단 시점 검증 · 2026-11-08

사용자가 PC 종료를 위해 작업 중단과 다음 세션 기록을 요청했다. 구현 완료·최종 통합 완료로 선언하지 않는다.

## 실제 통과

- 최종 소스의 표준 `python scripts/dev.py verify`: BUILD SUCCESSFUL, 1분 36초. APK·아키텍처·전체 단위 검사·lint 통과. 원본 로그 `delivery-verify.log`는 로컬에 보존.
- 기록 담당 단위 검사 28개 통과: 운전자 필터·빈 기록·짧은/중단된 조언 제외·저장 오류·공백 적분·표본 해상도·감속 상세 범위 포함.
- 실제 `mc_car` Android API 35 x86_64, 1682×1210·160dpi: `HistoryBFlowTest` 5개 모두 통과. [결과](device-checks/ui-large.json).
- 같은 운행·감속 3번 이동, 평가 저장/재진입/Activity 재생성, 미응답 건너뛰기, 빈 기록·중단 상태, 좌석/별점 터치 영역 검증.
- 360×800·160dpi: `rearSeatsHaveSeparateTouchTargetsAndRatingsStayLarge` 실제 기기 재실행 통과, `OK (1 test)`. 로컬 `narrow-ui-test.log`.
- 실제 사용자 DB와 연결하지 않는 시연 저장 검증: 평가 5점·커브 저장 전후 기존 프로필/샘플 DB SHA-256 동일, j1~j3 및 시연 인물 ID 없음. [결과](device-checks/database-isolation.json). 이 검사는 Gradle UI 실행 전의 DB에 대해 확인했다. Gradle connected UI 검사는 종료 시 검증용 앱을 제거하므로 이 해시를 모든 테스트 이후의 데이터 보존 주장으로 쓰지 않는다.
- 기존 기록 담당 트리의 15개 미커밋/자산 파일은 보존 기준 `315bee2`와 여전히 일치. 준비/저장 트리도 깨끗한 상태 유지. [파일 대조](preserved-owned-files.json).
- 앱 소유 테스트는 임시 적용 후 제거했다. 최종 app 작업 트리 변경 없음. 종료 화면 연결 패치는 `git apply --check` 통과, 적용하지 않음.

## 아직 끝내지 않은 것

- release 빌드 및 APK 내부에서 debug Activity·fixture가 제외됐는지 확인. source set 격리는 구현했지만 release 산출물 검사는 미완료.
- 최신 APK의 최종 전체 화면 캡처와 1024×768 중간 크기 검증. 기존 캡처 중에는 리뷰 보정 이전 것이 포함됨.
- 일반 `MainActivity`에서 실제 Repository를 읽는 B 출발·기록·프로필·평가를 최종 순회. 지금까지의 5개 UI 자동 검사는 격리된 시연 Activity에서 같은 feature 화면·ViewModel을 실행한 결과다.
- 팀원 앱 홈 바깥 틀·종료 분기·좌석의 실제 운행 시작 시 연결·환경 주입·Pleos 실제 주행/안내는 통합 담당 작업.
- 원격 브랜치 push 및 Draft PR 생성. 로컬 커밋까지만 저장한 상태로 다음 세션에서 원격 상태 확인 후 진행.

## 환경과 재현

기존 JDK 17과 Android SDK 35를 사용한다. 새 Gradle 설정은 커밋하지 않았다. 초반 병렬 Gradle 실행 후 증분 캐시 문제가 발생해 후속 검증은 한 번씩, 필요할 때 `:feature:history:clean`, `-Pkotlin.incremental=false`, `-Pkotlin.compiler.execution.strategy=in-process`로 실행했다. 정상 표준 verify도 위 환경 속성을 사용해 통과했다.

앱 소유 UI 검사 원본과 적용 패치는 [review-patches](review-patches/)에 있다. 이를 임시로 app/androidTest에 적용한 뒤 `:app:connectedDebugAndroidTest`의 class 인자로 `dev.motioncare.app.HistoryBFlowTest`만 실행했다. 기존 placeholder 기반 `ScaffoldFlowTest` 전체가 통과했다는 뜻은 아니다.
