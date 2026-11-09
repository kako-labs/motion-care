# 통합 전 시연 준비 검증 계획

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 전체 샘플 운행과 예외 상태를 검증하고 실제 구현 현황을 팀 회의 전에 정확하게 전달한다.

**Architecture:** 현재 MainHost·historyGraph와 공개 Repository를 그대로 사용한다. 실제 SQLite 검사는 UUID 이름 공간으로 격리하고 정상 DB 변경 여부를 확인한다. 공통 계약·제품 방향은 바꾸지 않는다.

**Tech Stack:** Kotlin, Compose instrumentation, SQLite public repositories, API35.

**Spec:** 사용자 요청(전체 흐름·예외·현황 정리), docs/proposal.md, docs/handoff.md.

## Global Constraints

- 기존 독립 clone과 feat/history-approved-compose-ui 유지, main 병합 금지.
- 정상 DB에 합성 시연 데이터 주입 금지. 탐지·SDK·서비스·공통 계약 임의 변경 금지.
- 앱 동작 오류는 재현 후 최소 수정, 단순 테스트 기대값은 현재 UI와 정합화.
- 예전 문서의 완료 주장 대신 코드와 실행 증거로 구분.

## Review Focus

- 운행 시작 시 좌석 연결이 상세 진입보다 먼저 저장되는가.
- 평가 건너뛰기가 평점으로 저장되지 않는가.
- 기록·평가가 Repository 재생성 뒤 유지되는가.
- 기록 없음·분석 부족·환경 미지원이 가짜 값으로 보이지 않는가.
- 합성 데모와 실제 앱 흐름이 별개임을 명시하는가.

## Task 1: 전체 흐름과 예외 검증

- [x] 공개 Repository를 UUID DB로 격리하는 MainHost instrumentation 검사를 작성한다. UI 등록·좌석→출발→샘플3회→종료→평가→상세→다음 출발, 별도 건너뛰기·빈 기록 상태를 확인한다.
- [x] 읽기 전용 API35 기기에서 실행한다. 실패는 원인을 조사하고 실제 오류만 최소 수정한다.
- [x] 저장소 재생성으로 기록·좌석·평가 보존, 정상 DB SHA256 불변을 확인한다. 화면 증거를 저장한다.
- [x] 기본 지영3건 debug 시연과 기존 예외 시연을 확인한다.

## Task 2: 현재 상태와 전달

- [x] docs/meeting-readiness/README.md에 완료·시연 전용·미확인·팀 결정 사항과 재현 절차를 정리한다.
- [x] README·handoff·proposal의 오래된 주장과 서로 다른 표현을 최소 수정한다. 과거 검증 문서는 역사 기록으로 보존하고 최신 문서로 안내한다.
- [x] 필수 verify, 전체 UI 검사, HTML 회귀 검사를 실행한다.
- [x] 최종 리뷰, 커밋·푸시, PR·Notion 기록을 갱신한다. main에는 병합하지 않는다.

## 진행 기록

- 시작 d3c1e6a, 작업 트리 clean. 기존 다른 작업 트리는 수정하지 않는다.
- Ruling: 사용자에게 이미 승인받은 순서대로 직접 진행한다. 계획 재승인 질문은 생략한다.
- 기존 검증 코드의 이전 문구·화면 밖 포인터를 진단 후 현재 UI와 가시 영역에 맞췄다. 앱 동작 수정은 필요 없었다. 로컬 UI12개·verify·HTML4개 통과 후 캡처 출력 경로도 검증했다.
- 최종 리뷰: 합성 기본4점을 다시 저장하는 허점을 수정한다. 실제 변경 평점·debug preferences 쓰기·재진입을 확인하고 이전 평점 키를 복원한다. Activity 재생성과 프로세스 재시작은 구분한다.
- Ruling: 같은 구간 번호만 확인하는 리뷰 Minor는 같은 운행 증명이 빠진 Important로 판단한다. 운행 날짜와 전체 구간 시각 일치를 함께 검증한다. 제품 동작은 변경하지 않는다.
