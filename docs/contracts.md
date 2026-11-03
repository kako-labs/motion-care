# 공개 연결 계약 v0.3

## 데이터 접근

기능은 data 모듈의 Repository로만 데이터에 접근합니다. 현재 공개된 Repository는 아래 2개입니다.

### 주행 · `data/trip/src/main/java/dev/motioncare/data/trip/TripRepository.kt`

| API | 의미 |
| --- | --- |
| records | 커밋된 주행 원본 목록 StateFlow |
| initialize() | 저장소 로드·미완료 기록 복구. 여러 번 호출해도 한 번 초기화 |
| get(id) | 해당 원본과 샘플, 없으면 null. IO 실패와 없음 구분 |
| start(vehicleId) | 주행 생성. 이미 기록 중이면 Rejected |
| captureSample() | 합성 샘플 한 건 추가. 차량 환경에서는 UnsupportedOperationException |
| finish() | 남은 샘플을 저장하고 주행 종료. 기록 중이 아니면 Rejected |

화면은 `TripReader`·SQL·VehicleDataSource에 접근하지 않습니다. 이들은 데이터 모듈 내부 구현입니다.

같은 파일의 `VehicleRecorder`는 `app`의 `TripRecordingService`만 사용합니다. `record()`는 기록 중인 주행이 끝나거나 호출자가 취소할 때까지 차량 속도를 저장합니다.

조립부는 자동차 기기에서 `createVehicleTripData(context)`, 그 외 기기에서 `createSampleTripRepository(context)`를 호출합니다. 실제 기록은 `motion-care-trips.db`에 저장해 합성 샘플과 섞이지 않습니다.

### 프로필 · `data/profile/src/main/java/dev/motioncare/data/profile/ProfileRepository.kt`

| API | 의미 |
| --- | --- |
| people | 저장된 사람 목록 StateFlow. 추가한 순서 |
| initialize() | 저장소 로드. 여러 번 호출해도 한 번 초기화 |
| addPerson(displayName) | 앞뒤 공백을 제거해 저장하고 ID가 붙은 프로필 반환. 빈 이름은 IllegalArgumentException |

조립부는 `createProfileRepository(context)`만 호출합니다. 차량·주행별 인물 연결·평가·설정은 기능 착수 시 이 모듈에 추가합니다.

## 데이터 의미

- 모델 정의: `core/model/src/main/java/dev/motioncare/core/model/`.
- TripId·PersonId·VehicleId 구분, 비어 있는 ID 금지.
- 시작/종료는 epoch ms, 샘플 elapsedMs는 시작 기준 경과시간.
- 속도는 m/s. 0과 null/UNAVAILABLE 구분.
- SYNTHETIC/VEHICLE 출처를 저장하고 화면에 표시.
- 샘플 시각은 엄격히 증가. 역행·중복 입력은 거부.
- 수정 revision은 증가하고 오래된 원본 덮어쓰기를 거부.
- 종료/중단 시각은 마지막 저장된 관측보다 이르지 않음.

## 상태·오류

Repository의 도메인 거부는 CommandResult.Rejected, 저장/미지원 실패는 예외로 전달합니다. 화면 ViewModel은 사용자 오류 상태로 변환하고 CancellationException은 전파합니다. 상세는 Loading/Content/NotFound/Error를 구분합니다.

DrivingViewModel의 busy는 주행 명령에만 적용합니다. 상세 조회는 별도 Job·request version으로 늦게 끝난 이전 조회를 차단합니다. 서로의 작업을 조용히 무시하는 전역 busy 상태가 없습니다.

## 수명·저장

Application의 AppGraph가 도메인별 Repository를 한 개씩 소유합니다. Activity 재생성은 주행을 끊지 않습니다. 새 프로세스에서 미완료 원본은 INTERRUPTED로 보존합니다. 화면 종료를 자동 운행 종료로 해석하지 않습니다.

주행 원본의 SQLite 이름·스키마 v1은 유지했습니다. 프로필은 별도 파일 `motion-care-profile.db`를 사용하며 스키마 버전을 따로 관리합니다. 트랜잭션 저장 후 목록을 갱신하고 읽기·쓰기는 IO dispatcher에서 처리합니다. 차량 샘플은 이벤트 시각 기준으로 약 1초마다 묶어 추가 저장하므로, 프로세스가 강제 종료되면 마지막 약 1초의 샘플은 잃을 수 있습니다. 보관 기간 정책은 아직 없습니다.

## 후속 계약

차량·인물 연결·평가·설정 관련 최소 모델은 남겨 두었으나 실행 API는 구현하지 않았습니다. [초안](future-contracts.md)은 설계 참고용입니다. 실제 질문·응답자·삭제·단위·지원 조건을 정한 후 기능과 함께 확장합니다. 현재 앱에 구현되지 않은 기능을 빈 성공 응답으로 꾸미지 않습니다.
