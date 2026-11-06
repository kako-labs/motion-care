# 검증 기록

실제로 확인한 범위를 작업별로 적습니다.

## 모듈화 및 GitHub 공유

2026-11-02, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| Gradle 모듈 6개 APK 빌드 | 통과 |
| 전체 모듈 host 테스트 | 17개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| 개인 Gradle 홈 배제·clean 빌드 | .gradle-user-home에서 새 Wrapper/의존성 다운로드 후 verifyAll 통과 |
| Gradle Managed Device | 프로젝트 정의로 Pixel 2 / API 35 AOSP ARM64 이미지 설치·생성·검사·종료 |
| Android UI 테스트 | 1개 전체 흐름 통과 |
| 금지된 모듈 의존성 | history → driving을 임시 주입한 검증에서 예상대로 차단 |
| 환경 점검 실패 경로 | 없는 JDK에 명확한 오류·설치 안내, traceback 없이 종료 |
| 문서 링크·개인 경로 검사 | 공유 문서 링크 정상, 공유 소스에 개인 절대 경로 없음 |

## 무엇을 검증했나

- 모델: 값 없음/0, 시각 순서, 종료 원본 조건.
- 데이터: 시작/종료 중복, 입력 순서, 저장 실패, 프로세스 복구, 실제 어댑터 미지원, SQLite 재연결·revision 충돌.
- 주행 상태: 초기화 실패·재시도, 명령 중복 방지·오류 전달.
- 상세 상태: 늦게 끝난 이전 조회가 새 선택을 덮어쓰지 않음, 실패/없음/재시도 구분.
- 실제 UI: 샘플 3건 입력 → Activity 재생성 → 종료 → 상세 → 평가 자리 → 같은 상세 복귀.

주행 원본 DB 이름과 스키마 v1을 유지했습니다. 과거 단일 모듈에서 통과한 강제 종료/재실행 검사와 구분해, 이번 기록은 모듈화 후 테스트입니다.

## 재현

```sh
python3 scripts/dev.py verify
python3 scripts/dev.py ui
python3 scripts/dev.py verify --clean-home
```

처음 도구 준비는 docs/testing.md를 따릅니다. 로컬 진단 로그/검사 보고서 백업은 artifacts 아래이며 공유 소스에는 포함하지 않습니다.

버전 갱신 권고 lint 3종만 고정 도구체인 정책으로 제외했습니다. 코드·리소스 경고는 오류로 처리하며 전체 lint baseline으로 숨기지 않았습니다.

## GitHub Actions 검증

2026-11-02, 비공개 저장소 `kako-labs/motion-care`의 코드 커밋 `4291150`을 GitHub-hosted Ubuntu 24.04에서 검증했습니다. [실행 결과와 보고서](https://github.com/kako-labs/motion-care/actions/runs/41455647976)는 저장소 접근 권한이 있는 계정으로 볼 수 있습니다.

| 검사 | 결과 |
| --- | --- |
| verify | APK 빌드·모든 모듈 단위 테스트·lint·모듈 의존성 검사 통과 |
| ui | Gradle Managed Device의 Android UI 테스트 통과 |
| 환경 재현 | GitHub의 새 실행 환경에서 JDK·SDK 준비 후 프로젝트 스크립트 실행 |

첫 실행은 SDK 설치 action의 기본값에 있던 구형 `tools` 패키지 때문에 실패했습니다. `.github/workflows/android.yml`에 `packages: platform-tools`를 명시한 뒤 두 작업 모두 통과했습니다. 개인 `local.properties`·SDK·Gradle 캐시·에뮬레이터는 저장소에 포함하지 않았습니다.

## 협업 구조 개선 검증

2026-11-03, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다. `data/profile` 모듈 추가, 기록 쪽 화면 이동을 `feature/history`로 이동, 검증 대상 자동화를 다룹니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 24개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과. 기존 전체 흐름과 프로필 화면 이동 |
| 빌드 결과가 없는 사본에서 verify | 공유 대상 파일만 복사해 통과 |
| 새 모듈의 검증 포함 | `verifyAll`에 `:data:profile`의 테스트와 lint가 자동 포함 |
| 미선언 모듈 | 임시 모듈 추가 시 `allowedEdges` 선언 요구로 빌드 실패 |
| 금지된 의존성 | `feature:driving`에서 `:data:profile` 의존을 임시 주입하면 차단 |
| 다른 모듈의 내부 구현 접근 | `feature:history`에서 두 data 모듈의 DB 클래스 참조 시 컴파일 실패 |
| 의존성 버전 | 기존 lockfile의 버전 변경 없음. `feature:history`에 내비게이션 항목만 추가 |
| GitHub Actions | 코드 커밋 `ad5e679`의 [실행](https://github.com/kako-labs/motion-care/actions/runs/41601400955)과 문서 커밋 `85069d5`의 [실행](https://github.com/kako-labs/motion-care/actions/runs/41601931656) 모두 verify·ui 통과 |

## 차량 속도 기록 검증

2026-11-03, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 37개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과. 일반 기기의 합성 샘플 흐름 유지 |
| Pleos 에뮬레이터 종단 시험 | `docs/pleos-emulator/tools/recording_e2e.py` 통과. [결과](pleos-emulator/README.md#앱의-차량-속도-기록) |

실차, App Market 권한 승인, 3분을 넘는 기록은 확인하지 못했습니다.

## 패턴 탐지 검증

2026-11-03, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 73개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과 |
| v1 DB 업그레이드 | Robolectric에서 기존 주행을 유지하고 관찰 저장 가능 |
| Pleos 에뮬레이터 종단 시험 | 관찰 저장과 상세 화면 표시 통과. [결과](pleos-emulator/README.md#패턴-탐지) |
| 분석 도구와 앱의 판정 | 에뮬레이터 주행의 관찰 11건이 시각·값까지 Python 판정과 일치 |
| 공개 주행 데이터 측정 | VED 3,790시간. [결과](detection-thresholds.md#실측) |

실제 차량 신호에서의 탐지 정확도, 소음이 섞인 신호의 기준값, 실기기의 v1 파일 업그레이드는 확인하지 못했습니다.

## 조향 기반 좌우 움직임 검증

2026-11-04, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 100개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과. 일반 기기에서 SDK를 불러오지 않음 |
| v1·v2 DB 업그레이드 | Robolectric에서 기존 기록을 유지하고 조향 저장 가능 |
| 조향 끊김 | 단위 테스트에서 속도 기록이 계속되고, 마지막 조향값으로 판정하지 않음 |
| 권한 경로 | 에뮬레이터에서 `CarPropertyManager`는 불가, SDK는 `CAR_DRIVE` 선언만으로 동작. [기록](pleos-emulator/README.md#차량-신호) |
| Pleos 에뮬레이터 종단 시험 | 조향 저장과 빠른 회전·반복 좌우 움직임 판정, 상세 화면 표시 통과. [결과](pleos-emulator/README.md#패턴-탐지) |
| 분석 도구와 앱의 판정 | 앞뒤 12건은 시각·값까지 일치. 좌우 2건은 패턴이 일치하고 시각 차이 400ms 이내 |
| 의존성 | Vehicle SDK와 하위 의존성(Hilt·Dagger·Gson 등) 추가. 기존 버전 변경은 UI 테스트 런타임의 `jsr305` 2.0.2→3.0.2 한 건 |

실차 조향 신호, 실제 차량의 축거·조향비, App Market의 `CAR_DRIVE` 승인은 확인하지 못했습니다.

## 주행 중 안내 카드 검증

2026-11-04, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 134개, 실패·오류 0 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과 |
| v1·v2·v3 DB 업그레이드 | Robolectric에서 기존 기록을 유지하고 안내 결과 저장 가능. v3 경로는 조건을 빼면 실패하는 것을 확인 |
| 표시 실패 | 단위 테스트에서 `FAILED`로 기록되고 기록 저장은 계속되며, 다음 카드를 막지 않음 |
| Pleos 에뮬레이터 종단 시험 | 내비 위 표시, 대기·생략 저장, 상세 표시, 약 15초 뒤 제거, 스위치 끄기와 유지 통과. [결과](pleos-emulator/README.md#앱의-안내-카드) |
| 권장 문구 5개 표시 | 40km/h 내비 위에서 모두 한 줄. 정차 직전 문구는 앱, 나머지 4개는 시험 앱으로 확인 |
| 분석 도구와 앱의 판정 | 앞뒤 12건은 시각·값까지 일치. 좌우 2건은 패턴 일치. 문구 교체 전 측정이며 탐지 코드는 그 뒤 바뀌지 않음 |
| 공개 주행 데이터 측정 | VED 3,255시간에 규칙 적용. [결과](guidance-card.md#빈도-측정) |
| 의존성 | `feature:driving`에 `core-ktx` 직접 선언. lockfile 변경 없음 |

실차 신호와 시점, App Market의 `CATEGORY_MESSAGE` 승인, 소리, 경로 안내 화면을 가리는지, 권장 문구의 안전성과 효과는 확인하지 못했습니다. 알림 권한을 거부했을 때의 `FAILED`는 단위 테스트로만 확인했습니다. 앱이 실제로 띄운 문구는 5개 중 정차 직전 문구 하나입니다.

## 주행 중 안내 소리 검증

2026-11-05, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 163개, 실패·오류 0. 소리 판단은 테스트를 먼저 작성해 실패를 확인한 뒤 구현 |
| 전체 Android 모듈 lint | 통과 |
| 의존성 | TextToSpeech 3.3.1 추가. 잠금 파일의 기존 버전 변경 없음. `universal-access` 제외, 앱에 `INTERNET` 권한 없음 |
| Pleos 에뮬레이터 소리 시험 | `sound_e2e.py` 6개 시나리오 통과. 알림음, 음성, 내비 음성 중 생략, 음성 중 내비 음성 시작, 독점 포커스, 선택 유지와 끄기. [결과](pleos-emulator/README.md#소리와-음성) |
| 음성 요청 주체 | 알림음과 생략 시나리오에서 음성 서비스에 앱의 요청이 없음을 서비스 로그로 확인 |
| Android UI 테스트 | 2개 통과. 일반 기기에서 TTS SDK를 불러오지 않음 |
| 기존 기록 시험 | `recording_e2e.py` 통과. 카드 표시와 저장, 상세 "주행 중 안내 1회", 기본 알림음 재생 |

음성은 주행 담당이 에뮬레이터 출력(노트북 스피커)을 듣고 잘 나오는 것을 확인했습니다. 앱에서 재생한 알림음은 듣고 확인하지 않았고, 재생 상태와 출력 프레임, 음량 콜백으로 판단했습니다. 실제 통화 중의 동작, 음악이 줄어드는지, 실차 스피커, App Market의 `TTS_SERVICE` 승인은 확인하지 못했습니다. 재생 결과는 저장하지 않아 주행 상세에서 볼 수 없습니다.

## 환경 데이터 검증

2026-11-06, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 199개, 실패·오류 0. 환경 모듈 32개와 공통 모델 4개 추가. 저장소·등급·응답 해석·저장은 테스트를 먼저 작성해 실패를 확인한 뒤 구현 |
| 전체 Android 모듈 lint | 통과 |
| Android UI 테스트 | 2개 통과. 일반 기기에서 환경 SDK를 불러오지 않고 설치됨(`uses-feature` 덮어쓰기 확인) |
| 의존성 | FusedLocation 1.1.3 추가. 잠금 파일에 새로 들어온 항목은 FusedLocation과 하위 2개뿐이고 기존 버전 변경 없음. 앱에 `INTERNET` 권한 추가 |
| Pleos 에뮬레이터 환경 시험 | `environment_e2e.py` 5개 항목 통과. 시작·종료 저장, 차량 값, 공조 변경 반영, 반올림 좌표의 대기질, 위치 거부 시 대기질만 제외. [결과](pleos-emulator/README.md#환경-값) |
| 소리 회귀 | `sound_e2e.py` 6개 시나리오 통과. 시작 버튼이 위치도 묻게 되어 스크립트에 위치 권한 허용을 추가 |
| 기존 기록 시험 | `recording_e2e.py` 통과. 샘플 1,146건, 패턴 14건, 카드 표시. 권한 창 3개(속도, 알림, 위치) |

실차 값, App Market의 위치·공조 권한 승인, CAMS 예측과 국내 측정소 값의 차이, 네트워크가 끊긴 실제 상황은 확인하지 못했습니다. 네트워크 실패와 오래된 값은 단위 테스트로만 확인했습니다. 에뮬레이터의 바깥·실내 온도는 고정값이라 값이 맞는지는 판단할 수 없습니다.

## 환경 안내 카드 검증

2026-11-06, macOS ARM64 / JDK 17 / Android SDK 35에서 확인했습니다.

| 검사 | 결과 |
| --- | --- |
| 전체 모듈 host 테스트 | 224개, 실패·오류 0. 조건 판단 10개, 정책 9개, 저장 2개, 기록 흐름 2개, 공통 모델 2개 추가. 문구 테스트는 환경 문구까지 검사. 모두 테스트를 먼저 작성해 실패를 확인한 뒤 구현 |
| 전체 Android 모듈 lint | 통과 |
| CI | `verify`, `ui` 통과(PR #12) |
| Pleos 에뮬레이터 환경 카드 시험 | `environment_card_e2e.py --long` 3개 통과. 대기질 매우나쁨 + 외기 유입이면 카드, 내기 순환이면 없음, 대기질 좋음 + 내기 순환이면 30분에 카드. [결과](pleos-emulator/README.md#환경-값) |

대기질 등급은 실행 시점의 예측값이라 같은 좌표에서도 결과가 달라질 수 있습니다. 시험은 저장된 등급이 시나리오와 맞지 않으면 통과나 실패 대신 "NOT RUN"으로 보고합니다. 실차의 내기 순환 값, 실제 CO₂와 탑승 인원, 카드가 공조 조작이나 답답함에 주는 효과는 확인하지 못했습니다.

## 아직 별도 검증이 필요한 환경

Windows/Linux 개인 컴퓨터에서의 설치·실행은 별도 확인이 필요합니다. Linux 검증은 위 GitHub-hosted Ubuntu 실행 결과입니다. 실제 Pleos 시스템·Vehicle SDK·차량 주행 제한은 샘플 테스트로 검증하지 않습니다. Pleos 에뮬레이터에서 별도로 측정한 결과는 [측정 기록](pleos-emulator/README.md)에 있고, 실차는 아직 확인하지 못했습니다.
