# 작업 소유권

모듈 이름은 기능 기준입니다. 담당자가 바뀌어도 구조는 유지합니다.

| 담당 | 작업 위치 | 처음 읽을 파일 |
| --- | --- | --- |
| 전경연님 | app | AppGraph.kt·MainHost.kt |
| 전경연님 | data/trip | TripRepository.kt·SampleTripRepositoryFactory.kt |
| 전경연님 | feature/driving | DrivingViewModel.kt·ui/ActiveContent.kt |
| 송호현님 | feature/history | HistoryViewModel.kt·TripDetailViewModel.kt·ui/RecentTrips.kt |
| 공동 확인·전경연님 통합 | core/model·core/ui | 공통 모델·화면 요소 |
| 공동 확인·전경연님 통합 | Gradle·config·CI·스키마 | 변경 소비자·검증 영향 확인 |

## 다음 기능을 붙일 위치

- 차량 SDK·환경 입력·기본 탐지: data/trip 내부에서 출발. 독립 영역이 커지면 data:environment 등으로 분리.
- 주행 중 안내·소리·배너: feature/driving. 데이터는 Repository로 접근.
- 프로필·인물 연결·기록 집계·출발 조언·평가·상세: feature/history의 관련 패키지에서 출발. 지속 저장이 필요하면 해당 도메인의 Repository 계약과 data 구현을 함께 설계.
- 화면 조립·navigation 연결: app.

모든 미래 데이터를 TripRepository에 넣으라는 뜻은 아닙니다. 현재 공개 API는 주행 원본과 샘플 명령만 다룹니다.

## 서로 침범하지 않는 방법

송호현님은 data의 DB/세션 구현을 수정하지 않고 Repository를 사용합니다. 필요한 필드·조회는 계약 변경으로 요청합니다. 전경연님은 기록 상세의 로딩·오류 상태를 공통 ViewModel에 추가하지 않고 history 담당에게 전달합니다.

기능 브랜치 → 작은 변경 → 상대 리뷰 → 통합을 기본으로 합니다. 공통 타입·스키마·버전을 변경할 때 관련 샘플과 문서를 같이 갱신합니다. 같은 기능을 둘이 동시에 분할 편집하기보다 한 사람이 책임지고 상대가 연결 검증합니다.
