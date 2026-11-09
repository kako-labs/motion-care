# B 화면 정교화 · CI 복구

차량과 네이비·하늘색 컨셉을 유지하고, 과한 링·광택·반복 카드·장식용 라벨을 줄였다. HTML과 실제 Android 기록 담당 화면에 반영했다. 이전 버전은 `save/history-b-before-refinement-20261108` 로컬 브랜치와 기존 커밋/캡처로 보존했다.

## 실제 변경

- 좌석의 큰 원형 플러스와 아바타 이중 링을 작은 좌석 표면·이름·추가 표시로 정리했다. 차량 그림과 얼굴 원본은 바꾸지 않았다. 좌석 조작 영역은 48dp 이상이며 전체 이름을 접근성에 유지한다.
- 출발의 최근 운행을 구분선이 있는 목록으로 바꾸고, 반복되는 운전자/동승자 문구를 줄였다. HTML은 제목에서 현재 운전자를 알려 준다.
- 기록·프로필·조언·평가·종료 화면의 배경, 카드, 버튼, 탭, 글자 위계를 통일했다. 숫자 카드를 다시 카드로 감싸던 구성을 줄였고, 보호자/인물 종류 선택의 장식용 캐릭터를 없앴다.
- 평가 입력·저장·건너뛰기, 보호자/직접 응답, 기존 ViewModel·Repository·상태 처리는 유지했다. 새로운 의학 점수나 탐지 기준을 만들지 않았다.
- HTML 지영 프로필이 다른 운전자의 지표/임의 누적 횟수를 쓰던 부분을 회귀 검사 후 수정했다. 현재 인물의 3건·최신 지표·이전 평균을 사용한다.
- HTML의 큰 제목·여백·카드 경계·하단 메뉴를 차분하게 다듬었다. 모바일 상세 속도 그래프는 축 글자를 찌그러뜨리지 않고 좌우로 살펴볼 수 있다. 로컬 파일의 오래된 자산 캐시를 피하도록 변경 자산 URL에 버전을 붙였다.

## CI 실패 복구

최초 [실행](https://github.com/kako-labs/motion-care/actions/runs/42477028026)은 Gradle 다운로드 10초 제한으로 UI 실행 전에 실패했다. 실패 작업만 재실행했더니 실제 UI 5개 중 기존 scaffold 이동 검사 2개가 옛 화면 문구를 기다리다가 실패했다.

사용자의 수정 요청으로 `app/src/androidTest/.../ScaffoldFlowTest.kt`만 현재 UI 진입·뒤로·상세·평가 왕복에 맞췄다. `샘플 3개`라는 화면 문구 대신 공개 TripRepository에서 샘플 수 3개와 재생성 후 보존, 최종 SYNTHETIC/COMPLETED 상태를 직접 검증한다. 검사를 끄거나 예전 문구를 억지로 되살리지 않았다. 뒤로 버튼의 접근성 이름도 추가했다.

복구 커밋 `1a20003`의 [CI 실행](https://github.com/kako-labs/motion-care/actions/runs/42480565484)은 **verify·UI 모두 성공**했다. 이후 시각 변경의 최신 원격 결과는 [PR #19 Checks](https://github.com/kako-labs/motion-care/pull/19/checks)에서 해당 커밋 기준으로 확인한다.

## 최종 로컬 검증

- 최종 제품 소스의 `verifyAll` 성공: 빌드·아키텍처·lint·단위 검사 274개/32 suite, 실패·오류 0. 임시 앱 검사 파일 제거 뒤에도 표준 verifyAll을 다시 통과했다.
- release 빌드 성공. dex/assets에서 debug 시연 Activity·Repository·합성 JSON 제외를 확인했다.
- 1024×768과 1682×1210에서 격리 B UI/실제 SQLite 저장 검사 **각 6개 모두 성공**. 두 크기 모두 정상 profile/sample DB의 전후 SHA-256이 같았다.
- 넓은 창의 NavHost 전환 중 저장 검사가 버튼을 너무 일찍 찾는 경우를 확인했다. 검토용 검사에 실제 목적 화면과 merged 별점 노드가 준비되는 조건을 넣고, 실제 터치는 유지한 채 양쪽 크기에서 다시 통과했다. 검토 소스와 적용 패치를 동기화했다.
- HTML Node 검사 3개 성공. 1440/1024/768/390 너비의 7개 화면에서 바깥 가로 넘침이 없음을 확인했다. 입력 가능한 화면으로 이동해 별점 선택·저장·종료를 확인했고, 390 너비는 에뮬레이션된 입력 좌표로 저장 버튼을 실제 클릭해 완료 화면 진입을 확인했다.
- 독립 최종 리뷰에서 Critical/Important 코드 문제 없음. 지적된 오래된 화면 캡처와 검사 원장은 최신 결과로 교체했다.

근거: [검증 JSON](refinement/verification.json), [1024 검사](refinement/ui-1024x768.json), [1682 검사](refinement/ui-1682x1210.json).

UI 소스는 `63bba24`로 저장·푸시했다. [팀 Notion 인계](https://app.notion.com/p/412ae4c07fd68016ab62df5763ee0487)에 정교화·CI 복구·실제 적용 범위와 남은 통합을 추가하고, 페이지 재열기 후 저장을 확인했다. 이 문서 후속 커밋은 인계 문서와 미적용 패치의 문맥 정리만 포함한다.

## 실제 Android 화면

| 화면 | 1024×768 | 1682×1210 |
| --- | --- | --- |
| 출발·좌석 | [보기](refinement/1024x768/departure.png) | [보기](refinement/1682x1210/departure.png) |
| 기록 목록 | [보기](refinement/1024x768/records.png) | [보기](refinement/1682x1210/records.png) |
| 운행 상세 | [보기](refinement/1024x768/trip.png) | [보기](refinement/1682x1210/trip.png) |
| 운전자 프로필 | [보기](refinement/1024x768/driver.png) | [보기](refinement/1682x1210/driver.png) |
| 조언 | [보기](refinement/1024x768/advice.png) | [보기](refinement/1682x1210/advice.png) |
| 평가 | [보기](refinement/1024x768/feedback.png) | [보기](refinement/1682x1210/feedback.png) |
| 종료 요약 | [보기](refinement/1024x768/finished.png) | [보기](refinement/1682x1210/finished.png) |
| 좌석 선택 | [보기](refinement/1024x768/seat-drawer.png) | [보기](refinement/1682x1210/seat-drawer.png) |
| 동승자 프로필 | [보기](refinement/1024x768/passenger.png) | [보기](refinement/1682x1210/passenger.png) |

캡처는 API 35 일반 Android의 debug 시연이다. 시각은 테스트 기기의 GMT 설정으로 표시되며, HTML의 설명용 날짜/시각과 구분한다. Pleos·실차·TalkBack·확대 글꼴 검증으로 소개하지 않는다. HTML 화면 모음은 [review.html](../design-archive/history-b-2026-11-07/ui-review/selected-b/review.html)이다.

## 팀원에게 전달할 부분

팀원 app 제품 코드·core·data·DB·SDK·서비스·탐지·내비 흐름은 이번에 변경하지 않았다. 기존 문서의 `app 변경 없음`은 제품 코드 기준이며, 이번 명시적 CI 복구로 앱의 검사 파일은 변경했다.

공통 루트의 밝은 테마와 검은 시스템 바 아이콘을 B에 맞추는 [app-b-theme.patch](review-patches/app-b-theme.patch)는 **미적용 리뷰 패치**다. 적용 전 검사만 통과했다. 팀원과 루트 테마·중복 헤더를 조율하고, 기존 종료/탑승 연결·환경 주입과 함께 통합한다. 최종 고정 APK의 전체 주행 흐름과 Pleos·실차 검증은 여전히 통합 담당 작업이다.
