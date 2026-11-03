# 인계 범위와 다음 개발

## 이번 뼈대에 포함

- app/core/data/feature Gradle 모듈과 의존성 검사
- 공개 Repository·숨겨진 저장/입력 구현
- 화면별 ViewModel·StateFlow·Navigation Compose
- 고정 샘플 입력·원본 저장·재실행 복구
- 프로필 저장 모듈과 사람 목록 조회. 등록 화면은 후속 구현
- 모델/저장/상태 테스트·UI 테스트·공유 기기 정의
- 실행 도구·버전/lockfile·환경 점검·CI·문서

## 다음 작업

1. 함께: README 명령과 샘플 전체 흐름 확인.
2. 주행 담당: 승인된 차량 입력·지원/시각/단위. 기록 담당: 프로필·주행 인물 연결.
3. 주행 담당: 환경·기본 탐지. 기록 담당: 기록 집계·비교·상세.
4. 주행 담당: 주행 안내. 기록 담당: 출발 조언·평점·개인 요약.
5. 연결·OFF·누락·삭제 검증 후 AI 요약·선택 녹음.
6. 메인 기능 완료 후 시간이 남을 때만 케어·경로.

## 차량 입력을 붙이기 전에 바꿀 것

[에뮬레이터 측정 기록](pleos-emulator/README.md)에서 확인한 조건입니다.

- `TripRepository.captureSample()`과 `VehicleDataSource.sample()`은 수동 호출 방식입니다. 실제 신호는 콜백으로 초당 약 7건 들어오므로 스트림 입력으로 바꿉니다.
- `SqliteTripStore.save()`는 저장할 때마다 샘플 전체를 지우고 다시 넣습니다. 측정 속도면 30분에 약 12,000건이므로 일괄 추가 저장으로 바꿉니다.
- 기록은 포그라운드 서비스에서 돌립니다. 일반 서비스는 앱이 뒤로 가고 60초 뒤 종료됩니다.
- 주행 중 화면을 보여 주려면 `MainActivity`에 `distractionOptimized`를 선언하고 주행 제한을 직접 따릅니다.
- 인터페이스를 바꾸면 각 테스트의 가짜 `TripRepository`도 함께 고쳐야 합니다. 공용 테스트 구현으로 합치는 것을 이때 함께 처리합니다.

## 운영사무국에 확인할 것

- `distractionOptimized` 선언과 `android.car.permission.CAR_SPEED` 권한의 승인 여부

구현되지 않은 데이터 API를 미리 모두 넣지 않습니다. 기능을 시작할 때 해당 도메인의 Repository·입력·상태·인수 기준을 정합니다. 최초 기획서의 상세 계약은 참고이며 실제 공개 API는 docs/contracts.md와 코드가 기준입니다.
