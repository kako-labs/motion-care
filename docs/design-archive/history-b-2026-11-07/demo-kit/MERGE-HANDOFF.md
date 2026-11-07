# 기록 담당 통합 인계

## 이번 변경의 경계

B HTML은 별도 시연 자산이다. 공유 Android main의 app·data/trip·data/environment·core·Gradle·DB·권한·서비스·탐지 규칙·팀원 브랜치를 수정하지 않았다. 기존 기록 담당 로컬 미커밋 변경도 덮어쓰지 않았다.

추가 Compose 파일은 feature/history 안의 `presentation/EnvironmentPanel.kt`, `presentation/BHistoryPanels.kt`와 환경 표시 상태 단위 테스트다. 최신 main의 `EnvironmentSnapshot`을 그대로 읽고 환경 API를 직접 부르지 않는다. B 화면은 호출자의 값과 콜백을 받는 stateless 표시 함수다. 새 공통 계약·기능 간 의존성·내비게이션은 없다.

## 기존 기록 화면을 합칠 때

1. 최신 main과 기존 기록 담당 PR #10의 상태를 확인한다. PR #10의 ViewModel·프로필 저장·평가 흐름을 유지한다.
2. B의 시각적 구성은 HTML과 캡처를 기준으로 옮긴다. `BReportPanel`, `BPassengerProfilePanel`, `BFeedbackPanel`, `BActionRibbon`을 기록 담당 화면 안에서 사용할 수 있게 준비했다. 숫자는 기존 ViewModel의 실제 값만 전달한다. `BReportUi` 등은 feature/history 내부 표시용 타입이며 새 공통 도메인 계약이 아니다.
3. 기존 EnvironmentPanel을 사용할 곳은 출발 전과 운행 상세다. 호출자가 제공한 `EnvironmentSnapshot?`를 표시한다. 화면 조립·Repository 주입·의존성 허용 변경은 공동 확인 후 통합 담당이 연결한다.
4. 출발 전은 `EnvironmentRepository.current`, 과거 운행 상세는 `history(tripId)`를 쓴다. 과거 기록이 없으면 null·기록 없음이며 현재 값이나 다른 운행 값으로 채우지 않는다.
5. 환경 호출은 data/environment에 둔다. 화면이 Open-Meteo·차량 SDK를 직접 호출하지 않는다. 운행 시작·끝 수집 시점을 새로 만들지 않는다.
6. 주행 중 카드와 음성·패턴 재현은 팀원의 기존 시나리오와 실행 도구를 쓴다. 이번 화면 시제품의 합성 그래프로 탐지 정확도를 입증하지 않는다.

### B 컴포넌트 연결 메모

- `BReportUi.speedSegments`: 기존 유효 구간 계산에서 만든 정규화 좌표를 전달한다. 신호 공백을 각각의 segment로 나눠야 한다. 컴포넌트는 분석·거리·패턴 계산을 하지 않는다. 실제 화면에서 축·시간 라벨은 기존 차트 정보로 연결한다.
- `BPassengerProfilePanel.preferences`: 기존 저장 프로필의 값만 넘긴다. 없는 선호는 null 또는 해당 항목 생략. portrait 슬롯에는 기존 인물 자산을 넣는다.
- `BFeedbackPanel`: `rating`, `selectedReasons`, `saving`은 기존 평가 ViewModel 상태와 연결한다. 이유 ID는 기존 enum으로 매핑한다. `onSave`는 기존 저장 기능, `onSkip`은 기존 미응답 흐름이다. 보호자 응답·다중 동승자 이동은 기존 화면이 유지한다.
- 새 표시 컴포넌트만 추가해 두었고 기존 화면 호출부를 교체하지 않았다. 팀원의 실제 앱 흐름과 기존 기록 담당 PR을 함께 확인하는 통합 단계에서 연결한다.

## 경로 콘셉트의 연결 범위

HTML 옵션 선택은 브라우저 메모리만 바꾼다. 실제 경로 API·내비 목적지·APK·기기 권한을 바꾸지 않는다. 실제 COMFORTABLE 지원과 응답을 확인하기 전까지 영상은 별도 콘셉트 컷으로 사용한다.

실제 경로 연결을 시작하려면 내비 담당·지원 SDK·탐색 요청/응답·실패 처리를 먼저 확인한다. 실패 시 기존 경로를 유지한다. 커브 수·방지턱 수·위험도·멀미 감소율을 임의로 채우지 않는다.

## 합친 뒤 촬영 직전 확인

- [ ] 고정 통합 커밋에서 빌드·필수 검사 통과
- [ ] 실제 탑승 연결 → 주행 → 기록 종료 → 목록 → 상세 → 평가 저장 → 재진입
- [ ] 미응답·기록 없음·짧은 기록·조향 미지원·안내 OFF 표시가 기존 규칙과 일치
- [ ] 환경 값의 출처·시각·상태, 실내/설정/바깥 온도 구분
- [ ] 환경 기록 없음일 때 다른 운행 값이 나오지 않음
- [ ] 주행 중 안내는 실제 에뮬레이터 위에서 나타나는 컷으로 촬영
- [ ] 콘셉트 경로 컷에는 `실제 탐색 미연결` 자막
- [ ] 공유 링크·영상·APK에서 개발자 실명·개인 계정·알림·키 노출 없음

이 목록은 아직 실행되지 않은 최종 통합 검사다. 준비 파일만으로 최종 통합 완료를 선언하지 않는다.
