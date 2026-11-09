# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## 프로젝트

현대자동차그룹 차량용 앱 공모전 출품작인 멀미 대응 앱(가칭 모션케어)입니다. Pleos Connect(AAOS API 34) 대상 Kotlin·Jetpack Compose 앱이며, 일반 기기에서는 합성 샘플로 동작하고 자동차 기기에서는 차량 속도와 조향을 기록하고 운전 패턴을 찾아 저장하며, 같은 패턴이 반복되면 내비 위 카드로 알립니다. 환경 값(차량 온도·공조, Open-Meteo 대기질)은 주행마다 저장하고, 대기질과 내기·외기 상태가 어긋나면 같은 카드로 알리며, 현재 값은 홈 화면에 보여 줍니다. 쉬지 않고 2시간을 달리면 같은 카드로 휴식을 권합니다. AI 요약은 아직 없습니다. 문서 목록은 `README.md`의 문서 지도를 봅니다.

## 작업 흐름: 브랜치와 PR

코드와 문서를 바꾸는 작업은 브랜치에서 하고 PR로 `main`에 합칩니다. 사용자가 명시적으로 요청한 경우에만 `main`에 직접 push합니다. 브랜치 보호를 쓸 수 없는 요금제라 GitHub가 막아 주지 않으므로 이 흐름을 직접 지킵니다.

1. 최신 `main`에서 브랜치를 만듭니다. 이름은 `type/설명`이고 type은 커밋과 같습니다(`feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `perf`).

   ```sh
   git switch main && git pull --ff-only
   git switch -c feat/profile-edit
   ```

2. 작은 단위로 커밋합니다. 메시지는 `type: 한글 설명`입니다. 예: `feat: 프로필 등록 화면 추가`
3. push 전에 `python3 scripts/dev.py verify`를 실행합니다. 화면 이동이나 수명주기를 바꿨다면 `python3 scripts/dev.py ui`도 실행합니다.
4. PR을 엽니다. 본문에 변경 내용, 영향받는 모듈, 실행한 검증, 확인하지 못한 항목을 적습니다. 제출 전 확인 항목은 `CONTRIBUTING.md`에 있습니다.

   ```sh
   git push -u origin feat/profile-edit
   gh pr create --base main --title "feat: 프로필 등록 화면 추가" --body-file <본문 파일>
   ```

5. `gh pr checks`로 CI의 `verify`와 `ui`를 확인합니다. 문서만 바꾼 PR은 CI가 돌지 않습니다. 병합은 상대 담당이 확인하고 사용자가 요청한 뒤에 합니다. force push와 `--no-verify`는 쓰지 않습니다.

`core/*`, Gradle 설정, CI, 스키마, 공개 Repository를 바꾸는 PR은 다른 담당의 작업에 영향을 주므로 본문에 영향 범위를 적습니다. 담당 구분은 `docs/ownership.md`에 있습니다.

## 명령

JDK 17이 필요합니다. `JAVA_HOME`과, `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`을 설정합니다.

```sh
python3 scripts/dev.py doctor               # JDK, SDK 점검
python3 scripts/dev.py verify               # 모듈 규칙 + APK + 전체 단위 테스트 + lint (에뮬레이터 불필요)
python3 scripts/dev.py ui                   # Gradle Managed Device(Pixel 2, API 35)에서 UI 테스트
python3 scripts/dev.py ui-connected         # 이미 켜 둔 기기에서 UI 테스트

# 단위 테스트 하나. Android 모듈은 testDebugUnitTest, core:model은 test
./gradlew :data:trip:testDebugUnitTest --tests 'dev.motioncare.data.trip.storage.SqliteTripStoreTest'
./gradlew :data:trip:testDebugUnitTest --tests '*SqliteTripStoreTest.stale*'
./gradlew :core:model:test --tests 'dev.motioncare.core.model.TripModelsTest'

./gradlew :feature:history:lintDebug        # 모듈 하나만 lint
./gradlew verifyArchitecture                # 모듈 의존성 규칙만 검사
```

의존성 잠금이 켜져 있습니다. 모듈 의존성을 바꾸면 `not part of the dependency lock state` 오류가 납니다. 이때만 아래 명령으로 잠금을 갱신하고, 기존 버전이 바뀌지 않았는지 diff로 확인합니다.

```sh
./gradlew verifyAll :app:assembleDebugAndroidTest --write-locks
```

## 구조

```text
app ──────────► feature:driving, feature:history, data:*, core:*
feature:* ────► data:*, core:*     (feature끼리는 참조 금지)
data:* ───────► core:model
```

- **모듈 규칙:** 허용 의존성은 루트 `build.gradle.kts`의 `allowedEdges`가 정합니다. `verifyArchitecture`가 빌드 전에 검사하며, 선언하지 않은 모듈이나 의존성은 빌드를 실패시킵니다. `verifyAll`은 플러그인을 보고 모든 모듈의 테스트와 lint를 자동으로 포함합니다. `docs/pleos-emulator/probe`, `sound-probe`, `environment-probe`는 앱 빌드에 포함되지 않는 측정용 프로젝트입니다.
- **데이터 접근:** data 모듈은 Repository 인터페이스와 생성 함수만 공개하고 나머지는 `internal`입니다. 모듈마다 DB 파일과 스키마 버전을 따로 가지며, `onUpgrade`는 명시적 마이그레이션이 없으면 실패합니다. 파일이 나뉘어 있어 모듈을 넘는 트랜잭션은 쓸 수 없습니다.
- **조립:** DI 프레임워크 없이 Application 범위의 `AppGraph`가 Repository를 만들고, `MainActivity`를 거쳐 `MainHost`로 전달합니다.
- **화면 이동:** `MainHost`는 home 경로와 `historyGraph` 호출만 갖습니다. 기록 쪽 경로는 `feature/history/.../navigation/HistoryNavigation.kt`에 추가하며 `MainHost`를 고치지 않습니다.
- **home 화면:** `app`의 `home` 패키지가 `Home2` 시안 배치로 조립합니다. 큰 카드에는 `DrivingViewModel` 상태에 따라 준비 문구, `ActiveContent`(feature:driving), `FinishedContent`(feature:history)가 들어가고, 아래에 사람·환경·안내·최근 기록 카드와 "운행 시작" 버튼이 있습니다. 화면에 넣는 값의 변환은 `HomeModels.kt`에 있습니다. 담당이 다른 영역이 한 화면에 모이는 지점입니다. 앱 전체 색은 `MotionCareTheme`가 정합니다.
- **화면 패턴:** `*Route` 컴포저블이 ViewModel을 만들고 상태를 `*Content`에 넘깁니다. 도메인 거부는 `CommandResult.Rejected`, 저장 실패는 예외로 전달되며 ViewModel이 화면 상태로 바꿉니다.
- **주행 기록:** `TripSessionController`가 기록 중인 주행을 하나로 유지합니다. 새 프로세스에서 미완료 기록은 `INTERRUPTED`가 되고, `SqliteTripStore`는 오래된 revision의 저장을 거부합니다. `UnavailableVehicleDataSource`는 차량 입력이 없을 때 합성값으로 대체되지 않도록 예외를 던집니다.
- **차량 기록:** `AppGraph`는 자동차 기기에서만 `createVehicleTripData`를 조립합니다. 주행이 시작되면 `TripRecordingService`가 `VehicleRecorder.record()`를 돌리고, 속도는 이벤트 시각 기준으로 약 1초마다 묶어 추가 저장합니다. 단위 테스트로 잡히지 않는 이 흐름은 `docs/pleos-emulator/tools/recording_e2e.py`로 확인합니다.
- **패턴 탐지:** `TripSessionController`가 차량 샘플을 `PatternDetector`와 `LateralDetector`에 한 건씩 넘기고, 찾은 `Observation`을 샘플과 같은 트랜잭션으로 저장합니다. 합성 샘플 주행은 탐지하지 않습니다. 기준값의 출처와 한계는 `docs/detection-thresholds.md`에 있으며 멀미 기준이 아닙니다.
- **환경:** `data:environment`의 `EnvironmentRepository`가 바깥 온도·실내 공조(차량)와 바깥 대기질(Open-Meteo 예측)을 값마다 출처·시각·상태와 함께 냅니다. 값이 없으면 채우지 않고 이유를 남깁니다. `TripRecordingService`가 주행 시작·1시간마다·종료 때 저장하고, 그 사이 5분마다 읽어 `current`를 기록기에 넘깁니다. 일반 기기는 모두 미지원입니다. 근거와 규칙 값은 `docs/environment.md`, 에뮬레이터 확인은 `docs/pleos-emulator/tools/environment_e2e.py`입니다.
- **주행 중 안내:** `GuidancePolicy`(data:trip)가 관찰과 샘플, `EnvironmentAdvisor`가 고른 환경 안내를 받고 `LongDriveTracker`로 쉬지 않고 달린 시간을 세어 카드의 표시·대기·생략을 정하고(패턴 우선, 15분 간격 공유), 결과 `GuidanceEvent`를 샘플과 같은 트랜잭션으로 저장합니다. 표시는 `TripRecordingService`가 넘긴 `NotificationGuidancePresenter`(feature:driving)가 맡고, 소리는 `GuidanceSoundPlayer`(data:trip)가 맡습니다. 소리 실패는 카드에 영향을 주지 않습니다. 규칙과 문구의 출처는 `docs/guidance-card.md`에 있습니다.

## Pleos 제약

에뮬레이터 측정값이며 실차와 마켓 심사는 확인하지 못했습니다. 근거는 `docs/pleos-emulator/README.md`, 화면 영향은 `docs/design.md`에 있습니다.

- **백그라운드:** 일반 서비스는 앱이 뒤로 가고 60초 뒤 종료됩니다. 기록은 포그라운드 서비스에서 돌립니다.
- **주행 중 화면:** `distractionOptimized`를 선언하지 않은 Activity는 5km/h부터 시스템 화면으로 가려집니다. 현재 `MainActivity`에는 선언이 없습니다.
- **알림:** 일반 알림은 내비 위에 뜨지 않고 종 배지도 표시되지 않습니다. 메시지·내비 카테고리만 뜹니다. 주행 중 안내는 `CATEGORY_MESSAGE` 카드로 표시합니다(약 9초, 두 줄까지). App Market 심사 통과 여부는 미확인입니다. 알림 설정은 Car Settings 앱 정보의 앱 단위 스위치뿐이고 채널별 화면이 없습니다. 하단 바에 뒤로 버튼이 없어 설정 화면은 `FLAG_ACTIVITY_NEW_TASK`로 엽니다.
- **소리:** 알림 채널의 소리는 재생 기록이 없어 앱이 직접 냅니다. TextToSpeech SDK는 온디바이스 모드만 씁니다. 등록·키가 필요 없고 인터넷 권한 없이도 재생되며(앱은 대기질 때문에 인터넷 권한을 가짐), 서버 모드용 `universal-access`는 의존성에서 뺐습니다. SDK는 오디오 포커스를 잡지 않고 다른 앱에 포커스를 뺏겨도 멈추지 않으며, 포커스만으로는 내비 음성·통화와의 겹침이 막히지 않아 앱이 처리합니다.
- **속도:** Vehicle SDK 2.3.2에는 속도 API가 없습니다. `CarPropertyManager`의 `PERF_VEHICLE_SPEED`를 `android.car.permission.CAR_SPEED` 권한으로 읽으며 단위는 m/s입니다. 이 권한은 위치 권한 그룹에 속해 요청 창에 위치 정보로 표시됩니다. 두 번 거부한 뒤에는 Car Settings 앱 정보의 권한 > 위치에서만 다시 켤 수 있습니다. `ACTION_APPLICATION_DETAILS_SETTINGS`는 위치 항목이 없는 Pleos 차량 설정을 열어, 앱은 `ACTION_AUTO_REVOKE_PERMISSIONS`로 엽니다.
- **차량 서비스 재시작:** `Car.createCar`를 `CarServiceLifecycleListener` 없이 부른 `Car`가 앱에 하나라도 있으면, 차량 서비스가 재시작될 때 앱 프로세스가 종료됩니다. 리스너를 넘기면 같은 `Car`가 다시 연결되지만 관리자와 콜백은 비워지므로 다시 얻고 다시 등록합니다. `CarSpeedSource`와 `CarClimateSource`가 이 방식입니다. 위치·음성 SDK는 `Car`를 쓰지 않고, 그 서비스는 차량 서비스 재시작 때 종료되지 않았습니다.
- **조향:** `CarPropertyManager`의 조향 권한은 `signature|privileged`라 일반 앱이 받을 수 없습니다. Vehicle SDK를 `pleos.car.permission.CAR_DRIVE`로 씁니다. 값은 핸들 각도(도)이고, 바뀔 때만 오며 시각이 없습니다. 축거·조향비 API는 없어 횡가속도는 추정값입니다.
- **환경:** 바깥 온도는 `CarPropertyManager`의 `ENV_OUTSIDE_TEMPERATURE`(`CAR_EXTERIOR_ENVIRONMENT`, normal), 공조는 Vehicle SDK(`CAR_CLIMATE`, normal)로 읽습니다. `CarPropertyManager`의 공조 속성은 `signature|privileged`입니다. 위치는 FusedLocation SDK(`FUSED_LOCATION`, dangerous)이며 요청 창에 "알 수 없는 작업"으로 표시됩니다. 실내 공기질 API는 없고 `Geocoder`도 없습니다. FusedLocation이 자동차 기기를 필수로 선언해 앱 Manifest에서 `tools:replace`로 덮어씁니다.
- **창:** 앱 하나가 앱 영역 전체를 쓰는 가로형 창은 1682×1210입니다. 앱 두 개가 나뉜 세로형 창은 측정하지 못했습니다.
