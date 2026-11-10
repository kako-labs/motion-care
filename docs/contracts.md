# 공개 연결 계약 v0.5

## 데이터 접근

기능은 data 모듈의 Repository로만 데이터에 접근합니다. 현재 공개된 Repository는 아래 3개입니다.

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

같은 파일의 `VehicleRecorder`는 `app`의 `TripRecordingService`만 사용합니다. `record(presenter, environment)`는 기록 중인 주행이 끝나거나 호출자가 취소할 때까지 차량 속도를 저장하고, 카드를 띄울 때 `GuidancePresenter`를 호출합니다. `environment`는 `EnvironmentSnapshot` 흐름이며 환경 카드에만 쓰입니다. 앱은 `EnvironmentRepository.current`를 넘기고, 흐름이 실패해도 기록은 계속됩니다. 소리는 `GuidanceAudio.play(문구)`로 냅니다. 재생하지 못해도 예외를 던지지 않으며, 알림음과 음성 중 무엇을 낼지는 `GuidanceSettings.sound`가 정합니다.

조립부는 자동차 기기에서 `createVehicleTripData(context)`, 그 외 기기에서 `createSampleTripRepository(context)`를 호출합니다. 실제 기록은 `motion-care-trips.db`에 저장해 합성 샘플과 섞이지 않습니다.

### 프로필 · `data/profile/src/main/java/dev/motioncare/data/profile/ProfileRepository.kt`

| API | 의미 |
| --- | --- |
| people | 저장된 사람 목록 StateFlow. 추가한 순서 |
| initialize() | 저장소 로드. 여러 번 호출해도 한 번 초기화 |
| addPerson(displayName) | 앞뒤 공백을 제거해 저장하고 ID가 붙은 프로필 반환. 빈 이름은 IllegalArgumentException |

조립부는 `createProfileRepository(context)`만 호출합니다. 차량·주행별 인물 연결·평가·설정은 기능 착수 시 이 모듈에 추가합니다.

### 환경 · `data/environment/src/main/java/dev/motioncare/data/environment/EnvironmentRepository.kt`

| API | 의미 |
| --- | --- |
| current | 가장 최근에 읽은 `EnvironmentSnapshot` StateFlow. 처음 읽기 전에는 모든 값이 `LOADING` |
| refresh() | 차량 값을 다시 읽고, 바깥 대기질은 1시간이 지났거나 위치 칸이 바뀌었을 때만 다시 요청. 값이 없어도 예외를 던지지 않음 |
| capture(tripId) | refresh 후 그 결과를 주행에 저장 |
| history(tripId) | 주행에 저장된 값 목록. 저장 순서 |

조립부는 자동차 기기에서 `createVehicleEnvironmentRepository(context)`, 그 외 기기에서 `createUnsupportedEnvironmentRepository()`를 호출합니다. 후자는 모든 값이 `UNSUPPORTED`이고 아무것도 저장하지 않습니다. `app`의 `TripRecordingService`가 주행 시작, 1시간마다, 종료 때 `capture`를 부르고, `MainHost`가 홈을 열 때와 권한 응답 뒤에 `refresh`를 부릅니다. 이 모듈을 화면에서 읽으려면 해당 feature를 `allowedEdges`에 추가합니다. feature:history는 이 모듈에 의존하지 않고, `MainHost`가 `historyGraph(environment = current, tripEnvironment = history)`로 값과 함수만 넘깁니다.

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
| GuidanceEvent | core:model | 결과가 정해진 시각 `elapsedMs`, 카드가 결정된 시각 `observedMs`, 주제 `topic`, 결과. 패턴을 받는 보조 생성자가 있음 |
| GuidanceTopic | core:model | `Pattern(DrivingPattern)`, `Environment(EnvironmentAdvice)`, `LongDrive`(쉬지 않고 2시간 주행) 중 하나 |
| EnvironmentAdvice | core:model | REDUCE_OUTSIDE_AIR(대기질 나쁨인데 외기 유입), TRY_OUTSIDE_AIR(대기질 좋음인데 내기 순환 30분 이상) |
| GuidanceOutcome | core:model | SHOWN, SKIPPED_INTERVAL, SKIPPED_OFF, SKIPPED_EXPIRED, FAILED |
| GuidancePresenter | data:trip | `show(topic)`. 표시를 시스템에 넘겼으면 true. 예외와 false는 FAILED로 기록되고 기록 저장을 막지 않음 |
| GuidanceSettings | data:trip | `enabled` StateFlow와 `setEnabled`. 기본은 켜짐, 앱을 다시 켜도 유지 |

- `SHOWN`은 시스템에 알림을 보냈다는 뜻이고 사용자가 봤다는 뜻이 아닙니다.
- 합성 샘플 주행의 `guidance`는 비어 있습니다. 일반 기기는 환경 값이 미지원이라 환경 카드도 없습니다. 주행 시간 카드는 차량 속도로만 셉니다.
- 저장 열 `guidance_events.pattern`에는 패턴 이름, 환경 안내 이름, `LONG_DRIVE`가 들어갑니다. 이름이 겹치지 않아 스키마는 v4 그대로입니다.
- 표시 구현은 `feature:driving`의 `NotificationGuidancePresenter`이고, ON/OFF 스위치는 `GuidanceSwitch`입니다. 알림이 꺼져 카드가 뜰 수 없으면(`guidanceCardsAllowed`가 false) 스위치 아래에 알리고 "알림 켜기"를 보여 주며, 소리가 나지 않으므로 "안내 소리" 선택은 숨깁니다. 버튼 동작은 `app`의 `rememberTurnOnNotifications`가 맡습니다.

## 환경 데이터

모델은 `core/model/.../EnvironmentModels.kt`에 있습니다. 출처와 규칙 값의 근거는 [환경 데이터의 근거](environment.md)에 있습니다.

| 항목 | 출처 | 의미 |
| --- | --- | --- |
| outsideTemperature | VEHICLE | 바깥 온도 °C |
| cabinTemperature | VEHICLE | 실내 온도 °C. 공조가 측정한 값 |
| cabinSetTemperature | VEHICLE | 운전석 설정 온도 °C. 실내 온도와 다름 |
| recirculation, airCleaner | VEHICLE | 내기 순환, 공기청정 켜짐 여부 |
| outdoorAir | OPEN_METEO_CAMS | PM10·PM2.5 µg/m³, 등급, 소수 첫째 자리로 반올림한 위경도. 실측이 아닌 예측값 |

- 값마다 `Availability`, 관측 시각, 출처를 갖고, 값이 없을 때만 `MissingReason`이 붙습니다. `AVAILABLE`과 `STALE`만 값을 가집니다.
- 값이 없으면 0이나 다른 출처의 값으로 채우지 않습니다. 실내 공기질 값은 차량 SDK에 없어 모델에 두지 않았습니다.
- 대기질을 보여 주는 화면은 "대기질: Open-Meteo, CAMS 예측"을 함께 적어야 합니다(CC BY 4.0).
- 온도와 대기질로 멀미를 판단하지 않고, 운전 패턴과 합쳐 점수를 만들지 않습니다.
- 합성 샘플 주행과 일반 기기에서는 환경 값을 저장하지 않습니다.

## 상태·오류

Repository의 도메인 거부는 CommandResult.Rejected, 저장/미지원 실패는 예외로 전달합니다. 화면 ViewModel은 사용자 오류 상태로 변환하고 CancellationException은 전파합니다. 상세는 Loading/Content/NotFound/Error를 구분합니다.

DrivingViewModel의 busy는 주행 명령에만 적용합니다. 상세 조회는 별도 Job·request version으로 늦게 끝난 이전 조회를 차단합니다. 서로의 작업을 조용히 무시하는 전역 busy 상태가 없습니다.

## 수명·저장

Application의 AppGraph가 도메인별 Repository를 한 개씩 소유합니다. Activity 재생성은 주행을 끊지 않습니다. 새 프로세스에서 미완료 원본은 INTERRUPTED로 보존합니다. 화면 종료를 자동 운행 종료로 해석하지 않습니다.

주행 원본의 스키마는 v4입니다. 이전 파일은 `observations`, `steering_samples`, `guidance_events` 테이블만 추가하고 기존 주행과 샘플을 유지합니다. 프로필은 별도 파일 `motion-care-profile.db`, 환경은 `motion-care-environment.db`(v1)를 사용하며 스키마 버전을 따로 관리합니다. 트랜잭션 저장 후 목록을 갱신하고 읽기·쓰기는 IO dispatcher에서 처리합니다. 차량 샘플·관찰·조향·안내 결과는 약 1초마다 같은 트랜잭션으로 묶어 추가 저장하므로, 프로세스가 강제 종료되면 마지막 약 1초의 값은 잃을 수 있습니다. 보관 기간 정책은 아직 없습니다.

## 후속 계약

차량·인물 연결·평가·설정 관련 최소 모델은 남겨 두었으나 실행 API는 구현하지 않았습니다. [초안](future-contracts.md)은 설계 참고용입니다. 실제 질문·응답자·삭제·단위·지원 조건을 정한 후 기능과 함께 확장합니다. 현재 앱에 구현되지 않은 기능을 빈 성공 응답으로 꾸미지 않습니다.
