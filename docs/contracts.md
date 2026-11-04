# 공개 연결 계약 v0.4

## 데이터 접근

기능은 data 모듈의 Repository로만 데이터에 접근합니다. 현재 공개된 Repository는 아래 2개입니다.

### 주행 · `data/trip/src/main/java/dev/motioncare/data/trip/TripRepository.kt`

| API | 의미 |
| --- | --- |
| records | 커밋된 주행 원본 목록 StateFlow |
| initialize() | 저장소 로드·미완료 기록 복구. 여러 번 호출해도 한 번 초기화 |
| get(id) | 해당 원본·샘플·관찰된 패턴·조향·안내 결과, 없으면 null. IO 실패와 없음 구분 |
| start(vehicleId) | 주행 생성. 이미 기록 중이면 Rejected |
| captureSample() | 합성 샘플 한 건 추가. 차량 환경에서는 UnsupportedOperationException |
| finish() | 남은 샘플을 저장하고 주행 종료. 기록 중이 아니면 Rejected |

화면은 `TripReader`·SQL·VehicleDataSource에 접근하지 않습니다. 이들은 데이터 모듈 내부 구현입니다.

같은 파일의 `VehicleRecorder`는 `app`의 `TripRecordingService`만 사용합니다. 유효 속도가 1.0m/s 이상으로 3초 이어지면 운행을 자동 생성하고, 0.35m/s 이하가 60초 이어지면 자동 완료합니다. 미지원·누락 속도는 0으로 추정하지 않습니다. 기록 중에는 속도와 조향을 저장하고 카드 조건이 맞으면 `GuidancePresenter`를 호출합니다.

조립부는 자동차 기기에서 `createVehicleTripData(context)`, 그 외 기기에서 `createSampleTripRepository(context)`를 호출합니다. 실제 기록은 `motion-care-trips.db`에 저장해 합성 샘플과 섞이지 않습니다.

### 프로필 · `data/profile/src/main/java/dev/motioncare/data/profile/ProfileRepository.kt`

| API | 의미 |
| --- | --- |
| people | 저장된 사람과 이동 선호 목록 StateFlow. 추가한 순서 |
| seating | 다음 운행에 적용할 현재 좌석 배치 StateFlow |
| participants | 운행 시작 시 고정한 운전자·동승자·좌석 스냅샷 StateFlow |
| feedback | 운행별·동승자별 편안함 평가 StateFlow |
| initialize() | 저장소 로드. 여러 번 호출해도 한 번 초기화 |
| addPerson / updatePerson | 사람 등록·이름·구분·민감도·불편 상황·휴식/온도 선호 저장 |
| placePerson / clearSeat | 좌석 배치 변경. 어린이는 운전석에 배치하지 않음 |
| assignCurrentSeating(tripId) | 현재 좌석을 해당 운행의 참여자 스냅샷으로 저장 |
| saveFeedback | 1~5점 평가·선택 이유·직접/보호자 응답을 동승자별 저장 |

조립부는 `createProfileRepository(context)`만 호출합니다. `TripRecordingService`는 차량 운행이 자동 시작되는 시점에 `assignCurrentSeating`을 호출하므로 화면이 뒤에 있어도 운행 원본과 참여자 스냅샷이 같은 TripId로 연결됩니다. 참여자 저장 실패는 차량 원본 기록을 중단시키지 않습니다.
## 데이터 의미

- 모델 정의: `core/model/src/main/java/dev/motioncare/core/model/`.
- TripId·PersonId·VehicleId 구분, 비어 있는 ID 금지.
- 시작/종료는 epoch ms, 샘플 elapsedMs는 시작 기준 경과시간.
- 속도는 m/s. 0과 null/UNAVAILABLE 구분.
- SYNTHETIC/VEHICLE 출처를 저장하고 화면에 표시.
- 샘플 시각은 엄격히 증가. 역행·중복 입력은 거부.
- 수정 revision은 증가하고 오래된 원본 덮어쓰기를 거부.
- 종료/중단 시각은 마지막 저장된 관측보다 이르지 않음.

## 패턴 관찰

`TripSnapshot.observations`는 주행에서 관찰된 차량의 움직임입니다. 동승자의 증상이나 멀미 여부를 뜻하지 않습니다.

| 패턴 | 화면 문구 | 조건 |
| --- | --- | --- |
| RAPID_START | 빠른 출발 | 정차에서 출발한 뒤 3초 안에 2.0m/s² 이상 가속 |
| HARD_STOP | 정차 직전 큰 감속 | 정차하기 전 5초 안에 2.0m/s² 이상 감속 |
| REPEATED_SPEED_CHANGE | 반복 가감속 | 0.9m/s² 이상의 가속과 감속이 30초 안에 3번 바뀜 |
| FAST_TURN | 빠른 회전 | 추정 횡가속도 2.0m/s² 이상인 회전 |
| REPEATED_SWAY | 반복 좌우 움직임 | 추정 횡가속도 0.9m/s² 이상으로 좌우가 30초 안에 3번 바뀜 |

- 가속도는 약 1초 구간의 속도 차이로 근사합니다. 정차는 0.5m/s 이하, 출발은 1.0m/s 이상입니다.
- **값마다 출처와 한계를 [탐지 기준의 근거](detection-thresholds.md)에 적었습니다.** 멀미 기준이 아니고 실차에서 확인하지 않았습니다. 저장된 기록이 생긴 뒤 값을 바꾸면 `PATTERN_DETECTOR_VERSION`을 올립니다. 각 관찰에 이 버전이 저장됩니다.
- 신호가 1초 넘게 끊기거나 유효하지 않으면 그 구간을 추측하지 않고 판정을 새로 시작합니다.
- 차량 기록에서만 탐지합니다. 합성 샘플 주행의 `observations`는 비어 있습니다.
- `TripSnapshot.steering`은 조향각 원본(도)입니다. 차량 주행에서 비어 있으면 좌우 패턴이 없는 것이 아니라 판정하지 않은 것입니다.
- 좌우 패턴의 값은 조향각과 속도로 추정한 값입니다. 측정값으로 표현하지 않습니다.

## 주행 중 안내

`TripSnapshot.guidance`는 카드마다 정해진 결과입니다. 규칙과 근거는 [안내 카드의 근거](guidance-card.md)에 있습니다.

| 공개 항목 | 위치 | 의미 |
| --- | --- | --- |
| GuidanceEvent | core:model | 결과가 정해진 시각 `elapsedMs`, 카드가 결정된 시각 `observedMs`, 패턴, 결과 |
| GuidanceOutcome | core:model | SHOWN, SKIPPED_INTERVAL, SKIPPED_OFF, SKIPPED_EXPIRED, FAILED |
| GuidancePresenter | data:trip | `show(pattern)`. 표시를 시스템에 넘겼으면 true. 예외와 false는 FAILED로 기록되고 기록 저장을 막지 않음 |
| GuidanceSettings | data:trip | `enabled` StateFlow와 `setEnabled`. 기본은 켜짐, 앱을 다시 켜도 유지 |

- `SHOWN`은 시스템에 알림을 보냈다는 뜻이고 사용자가 봤다는 뜻이 아닙니다.
- 합성 샘플 주행의 `guidance`는 비어 있습니다.
- 표시 구현은 `feature:driving`의 `NotificationGuidancePresenter`이고, ON/OFF 스위치는 `GuidanceSwitch`입니다.

## 상태·오류

Repository의 도메인 거부는 CommandResult.Rejected, 저장/미지원 실패는 예외로 전달합니다. 화면 ViewModel은 사용자 오류 상태로 변환하고 CancellationException은 전파합니다. 상세는 Loading/Content/NotFound/Error를 구분합니다.

DrivingViewModel의 busy는 주행 명령에만 적용합니다. 상세 조회는 별도 Job·request version으로 늦게 끝난 이전 조회를 차단합니다. 서로의 작업을 조용히 무시하는 전역 busy 상태가 없습니다.

## 수명·저장

Application의 AppGraph가 도메인별 Repository를 한 개씩 소유합니다. Activity 재생성은 주행을 끊지 않습니다. 새 프로세스에서 미완료 원본은 INTERRUPTED로 보존합니다. 화면 종료를 자동 운행 종료로 해석하지 않습니다.

주행 원본의 스키마는 v4입니다. 이전 파일은 `observations`, `steering_samples`, `guidance_events` 테이블을 순차 추가하고 기존 주행과 샘플을 유지합니다. 프로필은 별도 파일 `motion-care-profile.db`의 스키마 v2를 사용합니다. v1의 사람 행은 명시적 마이그레이션으로 유지하고, v2에는 이동 선호·현재 좌석·운행 참여자·동승자 평가를 저장합니다. 프로필 수정은 UPDATE로 처리해 연결된 좌석·참여자·평가를 보존합니다. 트랜잭션 저장 후 목록을 갱신하고 읽기·쓰기는 IO dispatcher에서 처리합니다. 차량 샘플·관찰·조향·안내 결과는 약 1초마다 같은 트랜잭션으로 묶어 추가 저장하므로, 프로세스가 강제 종료되면 마지막 약 1초의 값은 잃을 수 있습니다. 보관 기간 정책은 아직 없습니다.

## 분석·표시 계약

거리와 10분당 패턴 빈도는 `Quality.VALID` 속도 샘플만 사용합니다. 인접 표본 간격이 5초를 넘으면 그 구간은 거리와 유효 분석 시간에서 모두 제외하고, 유효 분석 시간이 1분 미만이면 정규화 빈도를 표시하지 않습니다. 조언 비교는 선택된 운전자의 완료 운행만 사용합니다. 이 값은 차량 움직임 관찰치이며 탑승자의 멀미 여부나 원인을 뜻하지 않습니다.
