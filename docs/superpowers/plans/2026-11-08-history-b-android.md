# B Android 화면 연결 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 확정 B 표현을 기존 feature/history ViewModel·저장 흐름에 연결하고 실제 에뮬레이터로 검증한다.
**Architecture:** PR #10과 #17을 합친 선행 기준 위에서 history 표시 코드만 바꾼다. 원본 미커밋 history를 복사해 315bee2로 보존했다. 기존 공통 계약·DB·탐지·서비스·앱 조립은 변경하지 않는다. 시연 Activity와 메모리 Repository는 src/debug에만 둔다.
**Tech Stack:** 기존 Kotlin, Compose, Navigation, JUnit, Android SDK 35 / JDK 17.
**Spec:** docs/design-archive/history-b-2026-11-07/demo-kit/NEXT-SESSION-HANDOFF.md 및 APPROVED-BASELINE-2026-11-08.md.

## Global Constraints

- B 네이비·하늘색·유리·사선 패널·차량 및 얼굴 자산 유지.
- 기존 ViewModel과 실제 Repository를 사용한다. 가짜 기록을 일반 사용자 DB에 쓰지 않는다.
- 실제 속도 자료에 없는 0.2초 측정을 만들지 않는다. 유효하지 않은 표본·공백을 이어 그리지 않는다.
- 공유 main 병합 없음. 선행 PR #10의 계약 변경은 이번 표시 패치와 분리한다.
- 사용자가 질문 없이 실행을 요청했다. 확정 디자인 재선택과 중간 승인 단계는 생략한다.

## Review Focus

- 다른 운전자·짧거나 중단된 기록으로 개인화 수치를 만들지 않는다.
- 감속 선택은 원본 Observation과 같은 시각축을 사용하고 공백을 보간하지 않는다.
- 저장 실패·로딩·미응답을 유지하며 재진입 시 기존 저장된 평가를 읽는다.
- 시연 fixture는 debug 전용이며 release APK와 실제 DB에서 격리된다.
- 좁은 창과 큰 차량 화면에서 주요 조작이 잘리지 않는다.

### Task 1: 원본 표본을 그리는 상세 그래프

**Files:** presentation/SpeedTrace.kt, presentation/SpeedTraceTest.kt, report/TripDetailContent.kt, advice/AdviceRoute.kt (feature/history 아래).
**Interfaces:** speedTrace(snapshot, observation?)는 원본 표본 구간과 관찰 전후 범위를 제공한다. BSpeedTrace(snapshot)는 전체·선택 구간·시각/속도 조회를 표시한다.
- [x] 공백·짧은 기록·원본 해상도·선택 범위 테스트 실패 확인.
- [x] 표시 모델과 Compose 그래프 구현, 기존 상세/조언 화면에 연결.
- [x] history 단위 테스트 통과.

### Task 2: 출발·프로필·평가·종료에 B 연결

**Files:** 기존 departure/ReadyContent.kt, profile/ProfileRoute.kt, feedback/FeedbackSlot.kt, report/FinishedContent.kt, ui/MotionCareComponents.kt 및 res/drawable-nodpi.
**Interfaces:** 기존 화면 함수·ViewModel 콜백과 저장 경로 유지. BActionRibbon, BPassengerProfilePanel, BFeedbackPanel에 실제 상태를 전달한다.
- [x] 기존 자산을 사용하고 큰 차량/좌석 및 사선 조언을 연결.
- [x] 기존 프로필 편집·다중 동승자·보호자 응답·평가 저장/건너뛰기 유지.
- [x] python scripts/dev.py verify 통과.

### Task 3: 격리된 시연·실행 검증·인계

**Files:** feature/history/src/debug의 Activity·Repository·fixture, docs/history-b-android의 검증·팀원 연결 패치.
**Interfaces:** debug Activity에서 기존 historyGraph와 같은 ViewModel을 사용한다. 지영 3건은 src/debug/assets에만 존재한다. 기존 app/계약/Gradle을 수정하지 않는다.
- [x] debug 시연은 지영·서아와 j1~j3. 일반 화면의 빈 상태 유지.
- [x] 에뮬레이터에서 출발→프로필→조언→상세→평가 저장→재진입, 건너뛰기 확인.
- [x] 원래 작업 트리 상태 보존 및 변경 경계 확인, 표준 verify와 release 격리 검사.
- [x] 리뷰 가능한 패치·캡처·검증 기록을 저장하고 별도 브랜치/Draft PR 준비.
