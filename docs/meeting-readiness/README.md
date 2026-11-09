# 팀 통합 전 시연 준비 · 2026-11-09

기준 브랜치는 `feat/history-approved-compose-ui`, 이번 준비 시작점은 `d3c1e6a`다. 실제 코드·일반 Android 실행 검증·별도 합성 시연을 구분한다. main에 병합하지 않는다.

## 연결 상태

| 기능 | 현재 연결 | 근거·한계 |
| --- | --- | --- |
| 사람 등록·선호 편집·좌석 | 실제 ProfileRepository와 화면 ViewModel | `DepartureViewModel`, `PersonDetailViewModel`, SQLite 저장 |
| 운행 시작 시 탑승 고정 | `MainHost`가 activeId 발생 시 `assignCurrentSeating` 호출 | 상세 진입 때 현재 좌석을 뒤늦게 붙이는 상태가 아님. 이번 준비에서 계약 변경 없음 |
| 샘플 시작·추가·종료 | 실제 공개 SampleTripRepository | 일반 Android는 SYNTHETIC. 차량 입력 정확도 증거가 아님 |
| 평가 | 별점·이유·직접/보호자 응답 저장, 건너뛰기 | 평가는 관찰 빈도의 점수에 합산하지 않음 |
| 기록·그래프·비교·조언 | 실제 TripSnapshot과 운행별 탑승 연결 사용 | 유효 시간60초 미만은 비교 빈도를 만들지 않음. 멀미 감소율 아님 |
| 출발 환경 | `MainHost → historyGraph → DepartureViewModel`에 current 주입 | 온도·대기질·미지원/누락 상태·출처. 미지원 기기에 가짜 값 없음 |
| 상세의 과거 환경 | 표시 컴포넌트 준비, Repository history 조회 미연결 | 현재 환경을 과거 값인 것처럼 표시하지 않음. 통합 후속 |
| 앱 첫 화면 | 개발용 바깥 프레임 안에서 B 출발 화면을 여는 버튼 | 큰 B 화면을 홈 자체로 쓸지는 통합 담당과 결정 |
| 종료 화면 | 일반 앱은 간단 FinishedContent, 전체 FinishedRoute는 debug에서 확인 가능 | 기존 app-finished-summary.patch는 선택 검토안. 이번 준비에서 적용하지 않음 |

과거 [B 인계](../history-b-android/README.md)의 미적용·미푸시·좌석 연결 필요 설명은 해당 시점 기록이다. 현재 브랜치에 이미 반영된 작업을 다시 적용하지 않는다. 최근 UI 실패의 빈 좌석 문구 패치는 `682e8bc`에 적용했고 [원격 verify/UI](https://github.com/kako-labs/motion-care/actions/runs/42613567634)가 성공했다.

## 두 가지 시연

### 일반 앱: 저장 흐름 증명

사람·좌석 선택 → 출발 조언 → 샘플 시작 → 신호3회 → 종료 → 동승자4점·출발/정차 응답 저장 → 같은 운행 상세 → 다음 출발.

짧게 누른 샘플3개는 분석 유효 시간이60초에 못 미친다. 다음 출발에 분석 부족이 나오는 것이 정상이며, 상세 합성 그래프나 좋은 비교 수치를 만들려고 실제 DB에 지영의 기록을 넣지 않는다. 검사에서는 UUID DB를 사용하고 공개 Repository를 다시 생성해 기록·탑승·평가 보존을 확인한다.

### 별도 debug: 지영의3건과 자세한 그래프

`HistoryDemoActivity`는 j1~j3 합성 fixture와 시연용 Repository를 쓴다. 일반 앱의 DB가 아니다. 별점 수정은 별도 debug SharedPreferences에만 저장되며, Activity 재생성과 앱 프로세스 재시작을 구분한다. 실제 차량 입력·실차 멀미 효과의 증명이 아니다.

```sh
adb shell am start -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez empty true
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez unanswered true
adb shell am start -S -n dev.motioncare.scaffold/dev.motioncare.feature.history.demo.HistoryDemoActivity --ez interrupted true
```

## 팀 회의에서 결정할 것

1. 최종 통합 브랜치·고정 커밋·APK와 리뷰/병합 담당. PR #20을 기준으로 확인하고 main에 직접 반영하지 않는다.
2. B 홈을 개발용 바깥 프레임과 어떻게 합칠지, 간단 종료와 전체 종료 중 어떤 화면으로 시연할지.
3. 상세의 과거 환경 이력 주입 책임·상태 처리. 출발 current와 혼동하지 않는다.
4. Android 이전 최대2회와 HTML 이전 최대3회 비교 상한의 최종 정책. 이번 준비에서는 그대로 유지한다.
5. Pleos 최종 APK에서 주행 제한·실제 내비 위 카드·백그라운드 기록·권한을 확인할 담당과 기기.

앱명·대표 타깃·MAU/사업 모델 가정·심사용 링크의 계정정보 노출·합성 표시·시연 영상은 [기획서 제출 전 표](../proposal.md#제출-전-확인)를 함께 검토한다. 이 문서는 제품 방향이나 의료 효과를 새로 확정하지 않는다.

## 검증과 재현

```sh
python scripts/dev.py verify
python scripts/dev.py ui
# 켜 둔 별도 테스트 기기에서는
python scripts/dev.py ui-connected
```

`MeetingReadinessFlowTest`는 실제 MainHost·SQLite의 전체 흐름과 빈 기록/건너뛰기를 확인한다. `HistoryDemoReadinessTest`는 별도 합성 시연의 선택 구간 이동·평가 재진입·빈 기록/중단·좌석 터치 영역을 확인한다. 사용자 데이터가 없는 테스트 기기에서만 실행한다. 테스트 앱 설치/정리와 일반 사용자 기기 사용을 혼동하지 않는다.

### 이번 실행 결과

일반 Android API35 읽기 전용 에뮬레이터,1024×768·160dpi에서 확인했다. Pleos/실차 검증과 최종 통합 APK 검증은 아니다.

| 항목 | 결과 |
| --- | --- |
| 필수 verify | 최종 성공. 빌드·의존성 검사·lint·단위280개/33suite, 실패·오류0 |
| 전체 Android UI | 기존5개 + 일반 앱 실제 저장2개 + debug 시연5개 =12개, 실패·오류·생략0 |
| 실제 저장 | UUID DB로 사람·좌석·기록·평가 저장. Repository 재생성 후 기록/샘플/탑승/별점/이유/응답자 일치. 정상 DB 파일 SHA256 불변 |
| 예외 | 빈 기록, 평가 건너뛰기 후 미응답 유지, 분석 부족, 환경 미지원, 중단 상태, 안내 OFF/알림 미허용 구분 |
| 합성 시연 | 기본 평점과 다른 값 저장·debug preferences 쓰기 확인, 재진입·Activity 재생성 후 확인. 선택 j1 날짜와 세 번째 감속의 전체 시각을 상세에서도 확인 |
| HTML | advice-context/history-scenario/journey-replay/seat-picker 회귀 파일4개 통과 |
| 리뷰 | 독립 리뷰에서 기본4점 재저장의 무효 검증을 발견해 다른 평점 저장으로 강화. 구간 번호뿐 아니라 날짜/전체 시각도 확인하도록 보강 후12개·verify 재통과 |

Activity 재생성은 프로세스 종료가 아니다. 이번 실제 저장 검증은 SQLite Repository 재생성을 통해 디스크 보존을 확인했으며, 임의 강제 종료 타이밍·최종 Pleos 앱 전체 흐름의 증거로 확대하지 않는다. 정상 DB 불변은 격리 검사 구간의 파일 해시 비교다. 일반 앱 기본 UI 검사는 별도의 읽기 전용 기기에서 정상 샘플 저장 경로도 사용한다.

초기 검증 코드의 오래된 문구와 화면 밖 포인터를 현재 UI에 맞춰 수정했다. 세로 페이지 안 가로 감속 목록은 바깥 페이지와 목록을 각각 스크롤해 실제 클릭한다. 앱의 내비/저장 동작을 바꿀 오류는 발견하지 않았다. 캡처는 runner의 추가 출력 디렉터리에서 테스트 앱 정리 전에 수집했다. 지영 홈 캡처는 테스트 후 별도 debug Activity의 안정된 화면으로 확인했다.

### 실제 화면

| 구분 | 화면 |
| --- | --- |
| 일반 앱 · 좌석2명/현재 환경 미지원 | [보기](01-seating-unsupported.png) |
| 일반 앱 · 첫 출발 기록 없음 | [보기](02-no-analysis.png) |
| 일반 앱 · 저장한4점 재진입 | [보기](03-saved-feedback.png) |
| 일반 앱 · 짧은 샘플 후 분석 부족 | [보기](04-next-departure-insufficient.png) |
| 일반 앱 · 빈 기록 | [보기](05-empty-history.png) |
| 일반 앱 · 건너뛰기 후 미응답 | [보기](06-skipped-feedback.png) |
| 별도 debug · 지영3건 기본 출발 | [보기](07-demo-home.png) |
| 별도 debug · 세 번째 감속 선택 | [보기](08-demo-selected-event.png) |
| 별도 debug · 같은 감속 상세 | [보기](09-demo-same-event-detail.png) |

일반 앱 캡처에는 아직 개발용 밝은 바깥 프레임이 있다. 긴 B 화면은 그 안에서 스크롤한다. debug의 전체 B 화면과 혼동하지 않으며 최종 홈 배치 검토 대상이다. 기기 시간대가 UTC여서 fixture의09:20 KST가00:20으로 표시되는 캡처가 있다. 저장된 운행/구간과 원본 시각을 바꾸지는 않았다.

[검증 집계](verification.json). 원격 재검증은 PR #20의 해당 코드 커밋 Checks와 보고서를 확인한다.

첫 원격 `b480d14` 실행42619592987은 verify 성공, UI11/12 성공이었다. 전체 흐름 검사가 평가 제목 직후 동승자 데이터 로딩 전에 별점을 찾았다. 제목 대신 실제 별점 노드 준비를 기다리도록 검증을 보정한다. 제한 시간과 저장·평가 성공 기준은 유지하며 앱 동작은 바꾸지 않는다. 이 실행을 전체 성공으로 소개하지 않는다.

준비 상태 보정 후 로컬 API35의411×731·160dpi(약411dp 폭)에서도 UI12개와 필수 verify가 모두 성공했다.1024 가로 화면의 캡처는 그대로 보존한다. 최종 원격 결과는 이 보정 커밋의 PR Checks와 Notion 후속 기록을 기준으로 확인한다.
