# Pleos 에뮬레이터 측정 기록

2026-11-03에 시험 앱으로 Pleos Connect 에뮬레이터를 측정한 결과입니다. 화면에 미치는 영향은 [화면 디자인 기록](../design.md)에 정리했습니다.

## 환경과 한계

- Pleos Connect v2.1.0, API 34, arm64, user build에서 측정했습니다.
- 실차와 App Market 심사 결과가 아닙니다.
- 시험 앱의 권한은 adb로 강제 부여했습니다. 앱의 속도 권한은 실제 요청 창으로 확인했습니다. 마켓 승인은 확인하지 못했습니다.
- 소리와 음성 안내는 확인하지 못했습니다. 첫 측정은 소리를 끈 상태(`-no-audio`)였고, 메시지 카드 측정은 소리를 켰지만 재생 여부를 들을 수 없었습니다.
- 시험하지 않은 항목: 자동 시작, 앱 닫기 제스처, 시동 껐다 켜기, 앱 두 개 동시 표시, 3분을 넘는 기록, 공조 제어 호출

## 결과

### 내비 표시 중 알림

D 기어 40km/h, 내비 표시 상태에서 중요도 HIGH 채널로 보냈습니다. 각 캡처는 알림을 보내고 2초 뒤 찍었습니다.

| 방식 | 결과 | 증거 |
| --- | --- | --- |
| 카테고리 없음 | 카드와 종 배지 모두 없음. 알림 센터를 열면 보임 | [14b_plain](shots/14b_plain.png), [14c_center](shots/14c_center.png) |
| 카테고리 없음, 주차 중 | 카드 없음 | [21_park_plain_alert](shots/21_park_plain_alert.png) |
| `CATEGORY_REMINDER` | 카드와 종 배지 모두 없음 | [06_reminder](shots/06_reminder.png) |
| `CATEGORY_STATUS` | 카드와 종 배지 모두 없음 | [07_status](shots/07_status.png) |
| `CATEGORY_MESSAGE` | 앱 영역 상단에 카드 표시, 종 배지 표시 | [08_msg](shots/08_msg.png) |
| `CATEGORY_NAVIGATION` | 앱 영역 상단에 카드 표시, 종 배지 표시 | [09_nav](shots/09_nav.png) |
| `CATEGORY_CALL` | 카드 없음. 운전 영역에 수락/거절 화면이 나타났고 시험 앱 강제 종료 뒤 사라짐. 인과는 확정하지 못함 | [10_call](shots/10_call.png), [14a_before](shots/14a_before.png) |
| 서비스에서 띄운 토스트 | 주행과 주차 모두 안 보임 | [15_toast_drive](shots/15_toast_drive.png), [22_park_toast](shots/22_park_toast.png) |
| `TYPE_APPLICATION_OVERLAY` | `adb install -g`로 설치하면 표시됨 | [12_overlay](shots/12_overlay.png), [13_overlay_default](shots/13_overlay_default.png) |
| 오버레이, `-g` 없이 설치 | `BadTokenException`. `MANAGE_OVERLAY_PERMISSION` 설정 화면도 열리지 않음 | [40_overlay_settings](shots/40_overlay_settings.png) |

리마인더와 상태 알림은 알림 센터 표시를 따로 캡처하지 않았습니다.

### 메시지 카드로 안내 띄우기

주행 중 안내에 쓸 `CATEGORY_MESSAGE` 카드를 실제 안내 문구로 다시 측정했습니다. 별도 표시가 없으면 D 기어 40km/h, 내비 표시 상태입니다.

| 시험 | 결과 | 증거 |
| --- | --- | --- |
| 실제 문구 | 내비 위 앱 창 왼쪽 위에 앱 이름과 문구가 표시됨. 메시지 말풍선 배지가 함께 보임 | [70_msg_real_copy](shots/70_msg_real_copy.png) |
| 표시 시간 | 약 1초 간격 캡처에서 1.3초부터 9.6초까지 보이고 10.7초에는 사라짐 | |
| 카드 크기 | 가로 약 720px, 세로 약 160px. 두 줄이면 약 190px. 상태 표시줄 일부를 덮음 | |
| 1초 간격 3건 | 쌓이지 않고 마지막 안내만 보임. 첫 캡처 뒤 약 10.6초에도 표시됨 | [71_three_alerts](shots/71_three_alerts.png) |
| 긴 문구 | 두 줄(줄당 약 30자)까지만 보이고 말줄임표 없이 잘림 | [72_long_copy](shots/72_long_copy.png) |
| P 기어 | 같은 카드가 표시됨 | [73_msg_park](shots/73_msg_park.png) |

- **소리:** 미확인입니다. `dumpsys notification`의 `mSoundNotificationKey`는 비어 있었고 로그에서도 재생 흔적을 찾지 못했지만, 실제로 소리가 나지 않는다는 뜻은 아닙니다.
- **경로 안내 화면:** 에뮬레이터 내비에 지도와 경로가 표시되지 않아, 회전 안내를 가리는지는 확인하지 못했습니다.
- **내비 앱 내부 표시:** NaviHelper 문서의 API는 경로 요청, 경유지 추가, 내비 화면 호출, 이벤트 수신이며, 내비 화면 안에 문구를 넣는 기능은 찾지 못했습니다.

### 앱의 차량 속도 기록

시험 앱이 아니라 실제 앱을 설치해 시험했습니다. 주차 중 기록을 시작하고, 내비를 앞으로 띄운 뒤 91초 동안 최고 60km/h로 가감속했습니다. 주차 후 기록을 끝낸 총 길이는 약 114초입니다.

| 항목 | 결과 | 증거 |
| --- | --- | --- |
| 권한 요청 | 시작 버튼에서 요청 창이 뜸. `CAR_SPEED`가 위치 권한 그룹이라 "위치 정보" 접근으로 표시됨 | [80_speed_permission](shots/80_speed_permission.png) |
| 권한 거부 | 기록을 시작하지 않고 안내 문구 표시. 다시 시도하면 요청 창이 뜨고, 두 번째부터 "다시 묻지 않음"이 나타남 | |
| 내비 뒤 기록 | 내비가 앞에 있는 동안 포그라운드 서비스 유지 | [81_recording_behind_nav](shots/81_recording_behind_nav.png) |
| 저장 | 샘플 726건, 초당 약 6.4건, 간격 중앙값 199ms, p95 201ms, 최대 204ms | [82_recorded_detail](shots/82_recorded_detail.png) |
| 값 | 0~60km/h, 모두 `VALID`, 시각이 엄격히 증가, 출처 `VEHICLE` | |
| 종료 | 기록 종료 후 서비스 중지 | |

자동차 기기에서 앱은 차량 모드로 동작하고, 저장 시각은 `CarPropertyValue`의 이벤트 시각을 씁니다. 첫 샘플이 시작 후 49ms였고 전체 길이가 주행 시간과 일치해, 이 시각이 기록 기준 시계와 맞는 것을 확인했습니다.

### 패턴 탐지

같은 시험에서 내비를 앞으로 띄운 채 1초마다 속도를 바꿨습니다. 입력은 초당 2km/h로 60km/h까지 가속, 유지, 정차, 그리고 30km/h까지 올렸다 멈추기 4회입니다. 총 길이는 약 117초, 샘플은 751건입니다.

| 항목 | 결과 | 증거 |
| --- | --- | --- |
| 완만한 가속·유지 | 첫 관찰이 약 71초에 나와, 이 구간에서는 패턴 없음 | |
| 저장 | 빠른 출발 4회, 정차 직전 큰 감속 5회, 반복 가감속 2회 | |
| 화면 | 상세 화면에 횟수 표시 | [83_pattern_detail](shots/83_pattern_detail.png) |
| 최고 가속도 | 3.09~3.50m/s². 입력 2.78m/s²(초당 10km/h)보다 큼 | |

저장된 샘플을 보면 에뮬레이터 속도는 입력 순간 계단처럼 바뀝니다. 약 200ms 간격 때문에 1초 구간이 0.8초로 계산되면 10km/h ÷ 0.8초 = 3.47m/s²가 됩니다. 이 시험은 저장과 표시 흐름을 확인한 것이며, 실제 차량 신호에서의 정확도는 말해 주지 않습니다.

### 백그라운드 기록

| 조건 | 결과 | 증거 |
| --- | --- | --- |
| 포그라운드 서비스(`dataSync`), 내비 뒤, D 기어, 최고 62km/h | 171초 동안 1초 타이머 누락 없음. 속도 1169건, 조향 75건, 브레이크 36건 수신 | [soak_fg.log](logs/soak_fg.log), [30_soak_end](shots/30_soak_end.png) |
| 포그라운드 없는 일반 서비스 | 앱 `onStop` 18:43:24, 서비스 `onDestroy` 18:44:24. 60초 뒤 종료 | [soak_bg.log](logs/soak_bg.log) |

로그 시각은 에뮬레이터 시계 기준입니다. 포그라운드 서비스는 adb 명령으로 시작했으므로 앱 버튼에서 시작하는 경로는 구현할 때 확인해야 합니다.

### 차량 신호

- **속도:** Vehicle SDK 2.3.2에는 속도 API가 없습니다. `CarPropertyManager`의 `PERF_VEHICLE_SPEED`는 `android.car.permission.CAR_SPEED` 권한으로 읽힙니다.
- **속도 수치:** 단위는 m/s이고 설정 범위는 10~20Hz입니다. 10Hz로 요청해 초당 6.8건을 받았고, 간격은 중앙값 133.5ms, p95 200ms, 최대 203ms였습니다.
- **정확도:** 에뮬레이터 신호에는 잡음이 없어 실차 정확도는 알 수 없습니다.
- **기어:** `GEAR_SELECTION` 속성과 SDK 리스너 모두 동작했습니다.
- **조향:** SDK 값의 부호가 Virtual Console 입력과 반대입니다(8을 넣으면 -8.0).
- **주행 거리:** `registerTripInstantaneousDrivingDistance`는 `ERROR_NOT_SUPPORTED_PROPERTY`를 반환했습니다.
- **SDK 버전:** 문서에 있는 Vehicle 2.3.1은 저장소에서 404이고, 받을 수 있는 버전은 2.0.3과 2.3.2입니다.

### 주행 제한

| 기어·속도 | 주행 상태 | 제한값 |
| --- | --- | --- |
| P | 0 | DO 필요 없음, UxR 0 |
| D 또는 N, 0km/h | 1 | DO 필요, UxR 80 |
| D 또는 N, 주행 중 | 2 | DO 필요, UxR 80 |

UxR 80은 `NO_SETUP`과 `NO_VIDEO`입니다. DO는 Distraction Optimized이며, Activity의 `distractionOptimized` 메타데이터로 선언합니다.

| 화면 | 40km/h 결과 | 증거 |
| --- | --- | --- |
| 일반 Activity | 시스템 화면으로 가려짐. 5km/h에서도 동일 | [62_plain_40](shots/62_plain_40.png), [64_plain_fresh_40](shots/64_plain_fresh_40.png), [61_plain_5](shots/61_plain_5.png) |
| DO 선언 Activity, task 시작 화면 | 표시됨. 주차 중에 열어도, 주행 중에 열어도 동일. 설치 출처와 무관하게 사이드로드에서 동작 | [60_do_root_40](shots/60_do_root_40.png), [63_do_fresh_40](shots/63_do_fresh_40.png) |
| DO 선언 Activity, 일반 Activity 위 | 가려짐 | [65_plain_then_do_40](shots/65_plain_then_do_40.png), [03_do](shots/03_do.png) |

가려질 때 Activity의 `onPause`는 호출되지 않았고, 주차하면 화면이 자동으로 돌아왔습니다. [23_drive_blocked](shots/23_drive_blocked.png), [24_park_again](shots/24_park_again.png)

### 창 크기

전체 화면은 2560×1440, 160dpi입니다. 앱 하나를 띄운 창은 [853,90]~[2535,1300], 즉 1682×1210입니다.

## 파일

| 위치 | 내용 |
| --- | --- |
| [shots](shots) | 원본 캡처. `sheet1`~`sheet6`은 번호순 모음 |
| [logs](logs) | 측정 로그 `soak_*.log`, 앱 차단 서비스 덤프 `cpm.txt`, Virtual Console 응답 `state.json`·`catalog.json`, AVD 설정 `avd-config.ini` |
| [probe](probe) | 시험 앱 소스. 앱 빌드에 포함되지 않는 독립 Gradle 프로젝트 |
| [tools/soak.py](tools/soak.py) | 측정 로그 집계 |
| [tools/recording_e2e.py](tools/recording_e2e.py) | 앱의 권한 요청부터 저장까지 자동 시험. 켜 둔 에뮬레이터와 디버그 APK 필요 |

저장소에 넣지 않은 것:

- 에뮬레이터 이미지와 AVD: 저장소 규칙으로 금지되어 있고 전체 26GB입니다. 아래 주소에서 다시 받을 수 있습니다.
- Pleos 문서 사본과 Vehicle SDK AAR: 외부 자료입니다.
- 에뮬레이터 실행 로그와 빌드 로그: 개인 경로가 들어 있습니다.

## 재현

### 에뮬레이터

Android Studio SDK Manager의 SDK Update Sites에 아래 주소를 추가하고, API 34의 Pleos Connect 이미지를 설치합니다.

- Mac: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-arm64-sys-img.xml`
- Windows/Linux: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

```sh
emulator -avd <AVD 이름> -no-window -no-snapshot -no-audio -no-boot-anim -gpu host -netdelay none -netspeed full
```

### 시험 앱

JDK 17과 `ANDROID_HOME`이 필요합니다. 현재 사용자가 10번이므로 명령에 `--user 10`을 붙입니다.

```sh
cd docs/pleos-emulator/probe
./gradlew :app:assembleDebug
adb install -g app/build/outputs/apk/debug/app-debug.apk

# Activities
adb shell am start --user 10 -n dev.probe/.PlainActivity
adb shell am start --user 10 -n dev.probe/.DoActivity

# Foreground service. act: hun:plain|msg|nav|call|status|reminder, toast, overlay, overlay_off
adb shell am start-foreground-service --user 10 -n dev.probe/.FgService --es act hun:plain

# Message card with real copy. title and text are optional.
adb shell "am start-foreground-service --user 10 -n dev.probe/.FgService --es act hun:msg --es title '모션케어' --es text '조금 일찍 감속해보세요.'"

# Navigation
adb shell am start --user 10 -n ai.umos.maps.android.navigation.app/.MainActivity
```

### 차량 신호 입력

Virtual Console의 HTTP API를 씁니다. 페이지 소스에서 확인한 값으로, 공식 문서에는 없는 동작입니다. 세션 요청을 매초 보내지 않으면 `emulator_inactive` 오류가 납니다.

```sh
adb forward tcp:8088 tcp:8088
curl -X POST 'http://127.0.0.1:8088/browser/session?client=probe'
curl -X PUT 'http://127.0.0.1:8088/vehicle/gear?gear=Drive'
curl -X PUT 'http://127.0.0.1:8088/vehicle/controls?speed=40&accelerator=15&brake=0&steering=8'
curl 'http://127.0.0.1:8088/vehicle/state'
```

### 로그 집계

```sh
python3 docs/pleos-emulator/tools/soak.py docs/pleos-emulator/logs/soak_fg.log
```
