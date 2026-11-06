# Pleos 에뮬레이터 측정 기록

2026-11-03에 시험 앱으로 Pleos Connect 에뮬레이터를 측정한 결과입니다. 화면에 미치는 영향은 [화면 디자인 기록](../design.md)에 정리했습니다.

## 환경과 한계

- Pleos Connect v2.1.0, API 34, arm64, user build에서 측정했습니다.
- 실차와 App Market 심사 결과가 아닙니다.
- 시험 앱의 권한은 adb로 강제 부여했습니다. 앱의 속도 권한은 실제 요청 창으로 확인했습니다. 마켓 승인은 확인하지 못했습니다.
- 소리는 재생 상태와 출력 프레임으로 확인했습니다. 음성은 주행 담당이 에뮬레이터 출력을 듣고 잘 나오는 것을 확인했고, 앱에서 재생한 알림음은 듣고 확인하지 않았습니다. 실차의 음량과 음질은 모릅니다. ([소리와 음성](#소리와-음성))
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

- **소리:** 카드 알림만으로는 재생 기록이 없습니다. 측정은 [소리와 음성](#소리와-음성)에 있습니다.
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

같은 시험에서 내비를 앞으로 띄운 채 1초마다 속도를 바꿨습니다. 입력은 초당 2km/h로 60km/h까지 가속, 유지, 정차, 그리고 30km/h까지 올렸다 멈추기 4회입니다. 총 길이는 약 115초, 샘플은 724건입니다.

| 항목 | 결과 | 증거 |
| --- | --- | --- |
| 완만한 가속·유지 | 첫 관찰이 약 70초에 나와, 이 구간에서는 패턴 없음 | |
| 저장 | 빠른 출발 4회, 정차 직전 큰 감속 5회, 반복 가감속 2회 | |
| 화면 | 상세 화면에 횟수 표시 | [83_pattern_detail](shots/83_pattern_detail.png) |
| 최고 가속도 | 3.09~3.47m/s². 입력 2.78m/s²(초당 10km/h)보다 큼 | |
| 수신 간격 | 중앙값 199ms, p95 200ms, 최대 1076ms. 1초를 넘는 간격에서는 판정을 새로 시작 | |

같은 입력을 반복하면 반복 가감속 횟수는 2~3회로 달라집니다. 입력 시점과 수신 간격이 매번 조금씩 다르기 때문입니다.

조향을 포함한 주행(약 167초, 속도 1,075건, 조향 57건)에서는 40km/h에서 핸들 60°를 5초 유지한 구간이 빠른 회전 1회(추정 3.03m/s², 계산값과 일치)로, ±30°를 3초씩 번갈아 바꾼 구간이 반복 좌우 움직임 1회(추정 1.51m/s²)로 저장됐습니다. [84_steering_detail](shots/84_steering_detail.png)

저장된 샘플을 보면 에뮬레이터 속도는 입력 순간 계단처럼 바뀝니다. 약 200ms 간격 때문에 1초 구간이 0.8초로 계산되면 10km/h ÷ 0.8초 = 3.47m/s²가 됩니다. 이 시험은 저장과 표시 흐름을 확인한 것이며, 실제 차량 신호에서의 정확도는 말해 주지 않습니다. 기준값의 근거는 [탐지 기준의 근거](../detection-thresholds.md)에 있습니다.

### 앱의 안내 카드

실제 앱을 설치하고 내비를 앞에 띄운 채 약 145초 주행했습니다. 입력은 위 패턴 탐지 시험에 조향 구간을 더한 것입니다(샘플 1,197건, 조향 62건). 규칙과 근거는 [안내 카드의 근거](../guidance-card.md)에 있습니다.

| 항목 | 결과 | 증거 |
| --- | --- | --- |
| 표시 | 정차 직전 큰 감속이 90.1초에 3번째로 관찰된 뒤, 출발과 정차가 이어지는 동안 기다렸다가 117.5초에 40km/h 주행 중 "정지 지점보다 조금 일찍 감속해보세요" 표시. 제목과 문구 모두 한 줄 | [85_guidance_card](shots/85_guidance_card.png) |
| 생략 | 기다리는 동안 3번째가 된 빠른 출발(95.3초)과 반복 가감속(106.4초)은 표시하지 않고 `SKIPPED_INTERVAL`로 저장 | |
| 제거 | 카드는 표시 후 약 15초에 활성 알림 목록에서 제거됨. 약 56초 뒤 화면에는 카드와 종 배지가 없음 | |
| 상세 | "주행 중 안내 1회" 표시 | [87_guidance_detail](shots/87_guidance_detail.png) |
| 끄기 | 시작 화면의 스위치를 끄면 앱을 다시 켜도 유지됨. 같은 주행에서 카드가 뜨지 않고 `SKIPPED_OFF` 3건 저장, 상세에 "주행 중 안내 꺼짐" 표시 | [86_guidance_switch](shots/86_guidance_switch.png) |

- **수명 설정:** `setTimeoutAfter(15초)`는 시험 앱에서도 P 기어와 D 기어 40km/h 모두 약 15초 뒤 알림을 제거했습니다.
- **그룹 요약:** 기록 알림과 카드가 함께 있으면 시스템이 `ranker_group` 요약 알림을 만듭니다. 이 요약은 카드가 사라져도 `channel=guidance`로 활성 목록에 남고, 기록이 끝나면 없어집니다. 그래서 `recording_e2e.py`는 채널이 아니라 문구로 카드를 찾습니다.
- **본 문구:** 앱이 실제로 띄운 문구는 정차 직전 문구 하나입니다. 나머지 4개는 시험 앱으로 같은 제목과 카테고리의 카드를 40km/h 내비 위에 띄웠고, 5개 모두 한 줄로 표시됐습니다. 시험 앱 카드는 아이콘이 다릅니다. ([가장 긴 문구](shots/88_guidance_longest_copy.png))
- **값의 흔들림:** 같은 입력의 다른 실행에서 최고 가속도 6.17m/s²가 한 번 저장됐습니다. 에뮬레이터 속도가 계단처럼 바뀌어 계산 구간이 짧을 때 값이 커지는 것으로 보이며, 재현해 확인하지는 않았습니다.

### 소리와 음성

2026-11-05에 측정했습니다. 소리를 켠 에뮬레이터(`-no-audio` 없음)에서 [측정 앱](sound-probe)과 실제 앱을 돌렸습니다. 재생 여부는 `dumpsys audio`의 재생 상태, `media.audio_flinger`의 출력 프레임, SDK의 음량 콜백으로 판단했습니다. 소리를 귀로 듣지는 못했습니다.

| 시험 | 결과 | 증거 |
| --- | --- | --- |
| 카드 알림의 소리 | 채널 소리가 기본값이든 직접 지정하든 재생 기록 없음. `mSoundNotificationKey`도 비어 있음. 소리는 앱이 직접 내야 함 | [sound_first](logs/sound_first.log) A |
| 음성 서비스 | `ai.pleos.playground.caas` 4.2.10이 내장됨. `pleos.car.permission.TTS_SERVICE`의 보호 수준은 normal이라 선언만으로 동작 | |
| TTS `ON_DEVICE`, 한국어 | 앱 등록 없이 재생. 20자 문구가 약 3초, 첫 재생은 약 4.5초. `onStart`는 호출 직후 와서 실제 재생 시작을 뜻하지 않음 | [sound_first](logs/sound_first.log) B |
| TTS `HYBRID`, 등록 없음 | 온디바이스로 재생 | [sound_first](logs/sound_first.log) G |
| TTS `SERVER`, 등록 없음 | `onError UNKNOWN` | [sound_first](logs/sound_first.log) G |
| 재생 용도 | SDK 기본값은 `USAGE_MEDIA`. `setAudioAttributes`로 지정한 값이 그대로 적용됨 | [sound_first](logs/sound_first.log) B |
| SDK의 오디오 포커스 | 재생 중 포커스 보유자에 음성 서비스가 없음. 포커스는 앱이 잡아야 함 | [sound_second](logs/sound_second.log) 1 |
| 40km/h, 내비 앞 | TTS 재생됨. 카드와 함께 재생 가능 | [sound_first](logs/sound_first.log) F |
| 다른 앱 음악 + 우리 `MAY_DUCK` | 포커스 허용, 함께 재생. 음악 앱에 포커스 변화가 전달되지 않음. 실제로 줄어드는지는 모름 | [sound_second](logs/sound_second.log) 2 |
| 다른 앱 내비 음성 + 우리 `MAY_DUCK` | 포커스 허용, 내비 음성과 TTS가 함께 재생됨. 앱이 직접 막아야 함 | [sound_second](logs/sound_second.log) 3 |
| 다른 앱 통화 포커스 + 우리 `MAY_DUCK` | `USAGE_NOTIFICATION` 요청이 허용됨. 같은 앱이 통화 포커스를 잡은 시험에서는 내비 용도도 허용되고 시스템 소리 용도(`SONIFICATION`)만 거부됨 | [sound_second](logs/sound_second.log) 4, [sound_first](logs/sound_first.log) C |
| 재생 중 다른 앱이 독점 포커스 | 우리 쪽에 `LOSS_TRANSIENT`가 오지만 TTS는 끝까지 재생됨. 앱이 직접 멈춰야 함 | [sound_second](logs/sound_second.log) 5 |
| `INTERNET` 권한 제거 | `ON_DEVICE` 재생 | [sound_second](logs/sound_second.log) 6 |
| 표준 `android.speech.tts` API, 같은 엔진 지정 | 초기화 실패(status -1) | [sound_second](logs/sound_second.log) 7 |
| 로그인 라이브러리(`universal-access`) 제외 | `ON_DEVICE` 재생, APK 25MB→5.3MB, 네이티브 라이브러리 없음 | [sound_without_login_library](logs/sound_without_login_library.log) |

실제 앱의 결과입니다. 40km/h, 내비 앞에서 패턴을 반복시켜 카드를 띄웠습니다. `tools/sound_e2e.py`로 재현합니다.

| 시나리오 | 소리 기록 | 재생 |
| --- | --- | --- |
| 알림음 | 시작 → 끝남 | 앱이 `USAGE_NOTIFICATION` 재생 |
| 음성 | 시작 → 끝남 | 음성 서비스가 `USAGE_NOTIFICATION` 재생 |
| 내비 음성이 재생 중일 때 카드 | 생략. 카드는 표시 | 내비 음성만 재생 |
| 음성 중 내비 음성 시작 | 멈춤 → 끝남. 카드는 표시 | 앞부분만 재생 |
| 음성 중 다른 앱이 독점 포커스 | 멈춤 → 끝남. 카드는 표시 | 앞부분만 재생 |
| 선택 유지와 끄기 | 앱을 다시 켜도 음성 유지. "주행 중 안내"를 끄면 선택 항목이 사라짐 | [89_guidance_sound_choice](shots/89_guidance_sound_choice.png) |

전체 기록: [sound_e2e](logs/sound_e2e.log). 실제 통화(`AudioManager.mode`)와 음악이 줄어드는지는 재현하지 못했습니다.

- **일찍 멈춘 음성:** `speak` 후 약 0.6초 안에 `stop`하면 서비스의 재생 기록이 `started`로 남습니다. 17초 뒤에도 남아 있었고, 다음 음성을 끝까지 재생한 뒤에는 사라졌습니다. 1.2초 뒤 멈추면 남지 않았습니다. 소리가 나는지는 듣지 못했고, 앱에서 피할 방법은 찾지 못해 그대로 둡니다. ([sound_stop_early](logs/sound_stop_early.log))

### 환경 값

2026-11-06에 [측정 앱](environment-probe)과 실제 앱으로 측정했습니다. 규칙과 근거는 [환경 데이터의 근거](../environment.md)에 있습니다.

| 시험 | 결과 | 증거 |
| --- | --- | --- |
| 권한 보호 수준 | `CAR_EXTERIOR_ENVIRONMENT`·`CAR_CLIMATE`는 normal, `FUSED_LOCATION`은 dangerous | `pm list permissions -f` |
| 일반 앱에 보이는 차량 속성 | `NIGHT_MODE`, `ENV_OUTSIDE_TEMPERATURE` 두 개 | [environment_probe](logs/environment_probe.log) |
| 바깥 온도 | 25.0°C, 1~2초마다 이벤트. 값이 바뀌지 않아 시뮬레이터 고정값으로 보임 | 같음 |
| 실내 온도 | Vehicle SDK로 19.1°C. 운전석·조수석·전체 실내 영역을 지원 | 같음 |
| 내기 순환·공기청정·공조 전원 | 전체 실내 영역만 지원. 좌석 하나로 물으면 `area ID not supported` | 같음 |
| 설정 온도 | 공조가 꺼져 있으면 `PropertyNotAvailable`. 콘솔에서 공조를 켜고 24°C·20°C로 바꾸면 앱이 그대로 저장 | [environment_e2e](logs/environment_e2e.log) |
| 위치 | 권한 없이 부르면 `SecurityException`. 요청 창 문구는 "알 수 없는 작업 실행". 허용 후 `emu geo fix` 좌표를 그대로 받고 등록하면 1초마다 이벤트 | [environment_probe](logs/environment_probe.log) |
| `Geocoder` | `isPresent=false`, 주소 없음 | 같음 |
| 인터넷 | 앱에서 Open-Meteo HTTPS 200, 약 1.1초 | 같음 |
| 실제 앱, 모두 허용 | 주행 시작·종료 때 값 저장. 도중에 바꾼 내기 순환과 설정 온도가 종료 값에 반영. 대기질은 반올림한 (37.6, 127.0) 기준 | [environment_e2e](logs/environment_e2e.log) |
| 실제 앱, 위치만 거부 | 기록은 진행. 대기질만 `UNAVAILABLE`·`NO_PERMISSION`, 차량 값은 저장 | 같음 |
| 인터넷 권한을 되살린 뒤 소리 | `sound_e2e.py` 6개 시나리오 통과 | |

측정 앱의 위치 권한은 앞선 실행에서 요청 창으로 허용한 상태라 로그에는 권한이 있는 경우만 남아 있습니다. 권한이 없을 때의 `SecurityException`은 첫 실행에서 확인했습니다.

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
- **조향 경로:** `PERF_STEERING_ANGLE`을 `CarPropertyManager`로 읽는 `android.car.permission.READ_CAR_STEERING`은 `signature|privileged`라 일반 앱에 부여되지 않습니다(`pm grant`도 거부). Vehicle SDK의 `registerSteeringWheelAngle`은 `pleos.car.permission.CAR_DRIVE`를 선언하면 별도 부여 없이 동작합니다. 튜토리얼의 `pleos.car.permission.READ_CAR_STEERING`은 시스템에 없는 권한이며, SDK의 `@RequiresPermission` 주석은 `CAR_DRIVE`를 요구하고, 문서도 이 주석을 따르라고 안내합니다. [로그](logs/steering_probe.log)
- **조향 값:** 핸들 각도(도)로, Virtual Console 범위는 −470~500입니다. 부호는 입력과 반대입니다(8을 넣으면 -8.0). 크기는 입력과 같습니다.
- **조향 이벤트:** 값이 바뀔 때만 오고 이벤트 시각이 없습니다. 같은 값을 다시 보내면 오지 않습니다. 구독 직후 현재 값이 한 번 옵니다.
- **차량 치수:** `CarInfo`는 VIN과 CRN만 줍니다. 축거·조향비·요레이트·횡가속도 API는 SDK에서 찾지 못했습니다.
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
| [logs](logs) | 측정 로그 `soak_*.log`, `sound_*.log`, 앱 차단 서비스 덤프 `cpm.txt`, Virtual Console 응답 `state.json`·`catalog.json`, AVD 설정 `avd-config.ini` |
| [probe](probe) | 시험 앱 소스. 앱 빌드에 포함되지 않는 독립 Gradle 프로젝트 |
| [sound-probe](sound-probe) | 소리 측정 앱과 측정 스크립트 `measure_first.py`·`measure_second.py`. 앱 빌드에 포함되지 않는 독립 Gradle 프로젝트 |
| [environment-probe](environment-probe) | 바깥 온도·공조·위치·`Geocoder`·인터넷 측정 앱. 앱 빌드에 포함되지 않는 독립 Gradle 프로젝트 |
| [tools/soak.py](tools/soak.py) | 측정 로그 집계 |
| [tools/recording_e2e.py](tools/recording_e2e.py) | 앱의 권한 요청부터 안내 카드와 저장까지 자동 시험. 켜 둔 에뮬레이터와 디버그 APK 필요. `ANDROID_SERIAL`로 기기 지정 |
| [tools/steering_probe.py](tools/steering_probe.py) | 일반 앱이 조향을 받는 경로와 권한을 측정. 시험 앱 필요 |
| [tools/sound_e2e.py](tools/sound_e2e.py) | 실제 앱의 알림음·음성과 겹침 처리 자동 시험. 소리 측정 앱 APK 필요 |
| [tools/environment_e2e.py](tools/environment_e2e.py) | 실제 앱의 환경 값 저장 자동 시험. 위치 허용·거부와 공조 변경. 에뮬레이터 인터넷 필요 |

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
# 소리를 측정할 때는 -no-audio를 뺍니다.
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

### 소리 측정

저장소 루트에서 실행합니다. 측정 앱은 `-PprobeId`로 패키지를 바꿔 다른 앱 역할을 하고, `-PnoNet`은 인터넷 권한을, `-PnoUa`는 로그인 라이브러리를 뺍니다.

```sh
./gradlew -p docs/pleos-emulator/sound-probe -PprobeId=dev.soundother :app:assembleDebug
./gradlew :app:assembleDebug
ANDROID_SERIAL=<기기> python3 docs/pleos-emulator/tools/sound_e2e.py docs/pleos-emulator/sound-probe/app/build/outputs/apk/debug/app-debug.apk
```

`measure_first.py <출력 폴더>`는 `dev.soundprobe`를, `measure_second.py <APK 폴더>`는 `main.apk`(`dev.soundprobe`), `other.apk`(`dev.soundother`), `nonet.apk`(`dev.soundnonet`)를 씁니다.

### 환경 측정

```sh
./gradlew -p docs/pleos-emulator/environment-probe :app:assembleDebug
adb install -r docs/pleos-emulator/environment-probe/app/build/outputs/apk/debug/app-debug.apk
adb emu geo fix 126.978 37.5665
adb shell am start --user 10 -n dev.envprobe/.PermissionActivity   # 위치 권한 요청 창
adb shell am start-foreground-service --user 10 -n dev.envprobe/.ProbeService
adb shell am start-foreground-service --user 10 -n dev.envprobe/.ProbeService --es act again
adb logcat -d -s ENVPROBE:I

./gradlew :app:assembleDebug
ANDROID_SERIAL=<기기> python3 docs/pleos-emulator/tools/environment_e2e.py
```

공조는 Virtual Console의 `PUT /vehicle/hvac?power=1&recirc=1&driver_temp=24`로 바꿉니다. 페이지 소스에서 확인한 값입니다.

### 로그 집계

```sh
python3 docs/pleos-emulator/tools/soak.py docs/pleos-emulator/logs/soak_fg.log
```
