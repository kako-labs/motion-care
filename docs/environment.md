# 환경 데이터의 근거

`data:environment`가 읽는 값, 값마다의 출처, 규칙 값의 근거와 확인 수준을 적습니다. 규칙 값을 바꾸면 이 문서를 함께 고칩니다. 공개 API는 [연결 계약](contracts.md)에 있습니다.

## 먼저 알아 둘 점

- **환경 값은 멀미 기준이 아닙니다.** 특정 온도나 대기질에서 멀미가 생긴다는 기준값은 찾지 못했고, 팀 조사도 그런 값을 만들지 않기로 했습니다(Notion 멀미 리서치, 설계 구조). 앱은 값과 출처만 전하고 "멀미 최적 온도" 같은 판단을 하지 않습니다.
- **출처가 다른 값을 섞지 않습니다.** 바깥 대기질로 실내 공기를 대신하지 않고, 값이 없으면 0이나 다른 출처의 값 대신 상태와 이유를 남깁니다(최종 기획 §19.3).
- **운전 패턴과 합쳐 점수를 만들지 않습니다(최종 기획 §19.4).**
- **이번 범위는 데이터까지입니다.** 출발 전·주행 상세 화면은 기록 담당이, 내비 카드와의 연결은 주행 담당의 다음 작업입니다.

## 읽는 값

2026-11-06 Pleos Connect 에뮬레이터 v2.1.0에서 측정했습니다. 실차에서는 확인하지 못했습니다.

| 값 | 경로 | 권한(보호 수준) | 에뮬레이터 결과 |
| --- | --- | --- | --- |
| 바깥 온도 | `CarPropertyManager`의 `ENV_OUTSIDE_TEMPERATURE`(°C, Float) | `android.car.permission.CAR_EXTERIOR_ENVIRONMENT`(normal) | 25.0°C를 1~2초마다 받음. 같은 시각 서울 예보 12.6°C와 무관한 시뮬레이터 고정값으로 보임 |
| 실내 온도 | Vehicle SDK `getHvacTemperatureCurrent` | `pleos.car.permission.CAR_CLIMATE`(normal) | 19.1°C. 공조를 켜고 설정 온도를 바꿔도 변하지 않음 |
| 설정 온도 | Vehicle SDK `getHvacTemperature`, 운전석 | 같음 | 공조가 꺼져 있으면 읽기 실패(`PropertyNotAvailable`), 켜면 콘솔에서 바꾼 24°C·20°C를 그대로 읽음 |
| 내기 순환 | Vehicle SDK `getHvacRecirculationState` | 같음 | 콘솔에서 켜고 끈 값을 그대로 읽음 |
| 공기청정 | Vehicle SDK `getHvacAirCleanerState` | 같음 | 꺼짐 |
| 실내 PM2.5·CO₂ | 없음 | 없음 | Vehicle SDK 2.3.2에서 해당 API를 찾지 못함. 항상 값 없음으로 둡니다 |
| 위치 | Pleos FusedLocation SDK 1.1.3 `getLastKnownLocation` | `pleos.car.permission.FUSED_LOCATION`(dangerous) | `emu geo fix`로 지정한 좌표를 그대로 받음 |
| 바깥 대기질 | Open-Meteo Air Quality API | `android.permission.INTERNET`(normal) | HTTPS 200, 약 1초 |

- **공조 값은 Vehicle SDK로만 읽습니다.** `CarPropertyManager`의 `HVAC_TEMPERATURE_CURRENT`·`HVAC_RECIRC_ON`은 `signature|privileged` 권한이 필요합니다([VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds)). 바깥 온도는 normal 권한이라 `CarPropertyManager`로 읽습니다.
- **SDK 영역:** 실내 온도는 전체 실내 영역을 고릅니다. 에뮬레이터는 운전석, 조수석, 전체 실내를 따로 알려 줬습니다. 설정 온도는 좌석마다 달라 운전석(`ROW_1_LEFT`) 값을 씁니다(제품 판단). 내기 순환과 공기청정은 전체 실내 영역 하나만 지원했습니다. 좌석 하나를 넣으면 `area ID not supported`로 실패했습니다(측정).
- **위치 권한 요청 창:** 권한에 이름표가 없어 시스템이 "Motion Care에서 알 수 없는 작업 실행하도록 허용하시겠습니까?"라고 묻습니다. 시연 화면에 그대로 보입니다.
- **지역 이름:** Android `Geocoder`가 없어(`isPresent=false`, 측정) 좌표를 지역 이름으로 바꾸지 못합니다. 화면에는 "현재 위치 부근"처럼 표시해야 합니다.

## 바깥 대기질 제공처

[Open-Meteo Air Quality API](https://open-meteo.com/en/docs/air-quality-api)의 CAMS 전 지구 예측(`domains=cams_global`)을 씁니다. 2026-11-06에 주행 담당이 골랐습니다.

| 항목 | 내용 |
| --- | --- |
| 값의 성격 | 실측이 아닌 모델 예측. 격자 0.4°(약 45km), 시간 해상도 3시간 자료를 1시간 값으로 제공(`interval: 3600`, 측정) |
| 키·가입 | 비상업 사용은 키가 필요 없음. 저장소와 APK에 키를 넣지 않는다는 [아키텍처 규칙](architecture.md#외부-api차량-sdk)을 서버 없이 지키는 유일한 후보 |
| 이용 조건 | 무료는 하루 1만 회 미만. 구독·광고가 없는 앱은 비상업이고, 구독·광고가 있으면 유료 플랜 필요([약관](https://open-meteo.com/en/terms)). 앱은 주행 시작·종료와 1시간마다만 요청 |
| 출처 표시 | 데이터는 CC BY 4.0. 대기질을 보여 주는 화면은 "대기질: Open-Meteo, CAMS 예측"을 함께 적어야 함 |
| 정확도 | 한국 측정소 값과의 오차 수치는 찾지 못함. 국내 측정소 값과 다를 수 있음 |

비교한 대안은 한국환경공단 에어코리아입니다([대기오염정보](https://www.data.go.kr/data/15073861/openapi.do), [측정소정보](https://www.data.go.kr/data/15073877/openapi.do)). 측정소 실측값과 공식 등급을 주지만 서비스 키가 필요해 서버리스 중계 서버를 두거나 키를 APK에 넣어야 하고, 가장 가까운 측정소를 찾는 호출과 좌표 변환이 더 필요합니다. 제공처 코드는 `OpenMeteoAirSource` 하나라 실측값이 필요해지면 이 클래스만 바꾸면 됩니다.

## 규칙

근거 유형: **공공 기준** / **실측**(우리가 측정) / **제품 판단**(근거 없음, 이유만 있음) / **구현 판단**.

| 값 | 현재 | 유형 | 근거 |
| --- | --- | --- | --- |
| 대기질 등급 | PM10 30·80·150, PM2.5 15·35·75 이하를 좋음·보통·나쁨, 그 위는 매우나쁨. 둘 중 나쁜 쪽 | 공공 기준·제품 판단 | 표와 "등급이 다르면 높은 등급" 규칙은 [서울시 미디어허브](https://mediahub.seoul.go.kr/archives/1214219)에서 확인. 원래 일평균 기준인데 1시간 예측값에 적용하는 것은 제품 판단. 환경부 원문은 확인하지 못함 |
| 등급 전 반올림 | 정수 µg/m³ | 구현 판단 | 표가 정수 구간(0~15, 16~35)이라 15.4는 좋음, 15.5는 보통 |
| `AIR_REFRESH_MS` 같은 곳 재요청 간격 | 1시간 | 제품 판단 | 제공처가 1시간마다 값을 냄(측정). 더 자주 물어도 같은 값 |
| `AIR_STALE_MS` 오래된 값 | 예측 시각에서 3시간 | 제품 판단 | 1시간마다 다시 묻는데 두 번 연속 실패한 경우. 그 뒤에는 값을 남기되 `STALE`로 표시 |
| 좌표 반올림 | 소수 첫째 자리(약 11km) | 구현 판단 | 예측 격자 0.4°보다 작아 결과가 거의 같고, 정확한 위치가 제공처와 DB에 남지 않음. 다른 칸으로 옮기면 1시간 전이라도 다시 요청 |
| 주행 중 저장 | 시작, 1시간마다, 종료 | 제품 판단 | 대기질이 1시간마다 바뀌므로 그보다 자주 저장하지 않음. 실내 온도는 이보다 빨리 변할 수 있음 |
| SDK 응답 대기 | 3초, 연결 전 1초 간격 5번 재시도 | 구현 판단 | 조향 입력과 같은 연결 대기 방식. SDK가 연결 완료를 알려 주지 않음 |
| HTTP 시간 제한 | 연결·읽기 각 10초 | 구현 판단 | 실패하면 마지막 값이나 `NETWORK`로 표시 |

## 상태

값마다 `Availability`와 이유를 남깁니다. 화면은 이 상태로 "확인 중", "미지원", "권한 없음", "네트워크 연결 필요", "오래된 값"을 구분해 보여 줍니다.

| 상태 | 뜻 | 예 |
| --- | --- | --- |
| `LOADING` | 앱을 연 뒤 아직 읽지 않음 | 첫 화면 |
| `AVAILABLE` | 값과 관측 시각이 있음 | |
| `STALE` | 마지막 값이 있으나 예측 시각에서 3시간이 지남 | 계속 네트워크 실패 |
| `UNSUPPORTED` | 이 기기나 차량이 값을 주지 않음 | 일반 기기, 위치 서비스가 없는 차량, SDK가 지원하지 않는 공조 항목 |
| `UNAVAILABLE` + `NO_PERMISSION` | 위치 권한 거부 | 시작 때 위치를 거부 |
| `UNAVAILABLE` + `NO_LOCATION` | 위치를 받지 못함 | |
| `UNAVAILABLE` + `NETWORK` | 대기질 요청 실패, 같은 칸의 마지막 값 없음 | 비행기 모드 |
| `UNAVAILABLE` + `NO_DATA` | 제공처나 차량이 값 없이 답함 | 공조가 꺼진 상태의 설정 온도 |

다른 칸에서 받은 마지막 값은 보여 주지 않습니다. 어느 지역의 값인지 다를 수 있기 때문입니다.

## 확인하지 못한 것

- 실차의 바깥·실내 온도와 공조 값, 차종별 지원 범위
- App Market에서 `FUSED_LOCATION`, `CAR_CLIMATE`, `CAR_EXTERIOR_ENVIRONMENT` 권한 승인 여부
- CAMS 예측값과 국내 측정소 값의 차이
- 주행 중 네트워크가 끊길 때의 실제 빈도
- 바깥 대기질이나 실내 공조 상태를 안내하는 것이 동승자의 편안함에 주는 효과
