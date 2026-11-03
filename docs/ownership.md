# 작업 소유권

모듈 이름은 기능 기준입니다. 담당자가 바뀌어도 구조는 유지합니다.

저장소에는 역할로만 적습니다. 공모전 양식이 공유 링크와 첨부자료에 개발자 실명을 금지하기 때문입니다. 역할별 실제 담당자는 Notion 개발 기획서에 있습니다.

| 담당 | 작업 위치 | 처음 읽을 파일 |
| --- | --- | --- |
| 주행 담당 | app | AppGraph.kt·MainHost.kt |
| 주행 담당 | data/trip | TripRepository.kt·SampleTripRepositoryFactory.kt |
| 주행 담당 | feature/driving | DrivingViewModel.kt·ui/ActiveContent.kt |
| 기록 담당 | data/profile | ProfileRepository.kt·ProfileRepositoryFactory.kt |
| 기록 담당 | feature/history | navigation/HistoryNavigation.kt·HistoryViewModel.kt·TripDetailViewModel.kt·profile/ProfileViewModel.kt |
| 공동 확인·주행 담당 통합 | core/model·core/ui | 공통 모델·화면 요소 |
| 공동 확인·주행 담당 통합 | Gradle·config·CI·스키마 | 변경 소비자·검증 영향 확인 |

## 다음 기능을 붙일 위치

- 차량 SDK·기본 탐지: data/trip.
- 실내 환경·외부 대기질: data/environment 모듈을 추가해 구현.
- 주행 중 안내·소리·배너: feature/driving. 데이터는 Repository로 접근.
- 프로필·차량·인물 연결·평가·안내 설정 저장: data/profile. 화면은 feature/history.
- 기록 집계·출발 조언·상세: feature/history의 관련 패키지에서 출발.
- AI 요약: LLM을 쓰면 data/summary 모듈을 추가해 호출. 표시는 feature/history.
- 메인 화면 조립: app. 기록 쪽 화면 이동: feature/history의 `historyGraph`.

모든 미래 데이터를 하나의 Repository에 넣지 않습니다. 현재 공개 API는 [연결 계약](contracts.md)이 기준입니다.

## 서로 침범하지 않는 방법

기록 담당은 data/trip의 DB/세션 구현을 수정하지 않고 Repository를 사용합니다. 주행 담당도 data/profile의 저장 구현을 수정하지 않습니다. 필요한 필드·조회는 계약 변경으로 요청합니다. 주행 담당은 기록 상세의 로딩·오류 상태를 공통 ViewModel에 추가하지 않고 history 담당에게 전달합니다.

기능 브랜치 → 작은 변경 → 상대 리뷰 → 통합을 기본으로 합니다. 공통 타입·스키마·버전을 변경할 때 관련 샘플과 문서를 같이 갱신합니다. 같은 기능을 둘이 동시에 분할 편집하기보다 한 사람이 책임지고 상대가 연결 검증합니다.
