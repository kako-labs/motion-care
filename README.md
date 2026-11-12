# 모션케어 (Motion Care)

동승자의 멀미를 줄이도록 운전 습관을 돕는 Pleos Connect 차량용 앱입니다.

- **운행 기록:** 차량 속도·기어·조향 신호로 운행을 자동 기록합니다.
- **주행 중 안내:** 동승자를 불편하게 하는 운전 패턴(예: 정차 직전 큰 감속)이 반복되면, 내비 위에 짧은 안내 카드와 소리로 알립니다.
- **도착 후:** 동승자가 이번 이동을 평가합니다.
- **다음 출발:** 쌓인 기록으로 고른 조언을 보여 줍니다.

운전 패턴의 빈도를 다룰 뿐 멀미를 진단하지 않습니다.

## 심사용 실행 안내

### APK

`apk/TEAM_모션케어_release.apk`

- release 빌드, 서명됨, 버전 1.0.0
- 패키지 `dev.motioncare.scaffold`

### 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect 에뮬레이터 v2.1.0 (Android 14, API 34, arm64, user build) |
| 화면 규격 | 2560×1440, 160dpi. 앱 하나가 앱 영역 전체를 쓰는 창은 1682×1210 |
| 차량 입력 | 에뮬레이터의 Virtual Console(속도·기어·페달·조향·공조) |
| 인터넷 | 바깥 대기질(Open-Meteo 예측)에 필요합니다. 없으면 대기질만 표시되지 않습니다 |
| API 키·서버 | 필요 없음 |
| 실차 | 확인하지 않았습니다. 모든 결과는 에뮬레이터 기준입니다 |

### 설치와 실행

1. 에뮬레이터를 켜고 APK를 설치합니다: `adb install apk/TEAM_모션케어_release.apk`
2. 하단 바의 모션케어 아이콘으로 앱을 엽니다.
3. 홈의 설정 카드에서 **운행 자동 기록**을 켜고 권한을 허용합니다.
   - 차량 속도 권한은 위치 권한 묶음이라 '위치 정보'로 표시됩니다.
   - 같은 카드의 **알림 켜기**로 알림도 허용합니다. 주행 중 안내 카드가 알림으로 뜹니다.
4. 하단 **좌석**에서 자리를 눌러 사람을 추가하고 앉힙니다. 에뮬레이터 키보드로 한글을 칠 수 없으면 영문 이름을 씁니다. 운전석에 앉은 사람이 출발 조언과 운전자 프로필의 대상입니다.
5. Virtual Console에서 기어를 **D**로 바꾸면 기록이 시작됩니다. 내비를 띄워도 기록은 계속됩니다.
6. **주행 중 안내를 보려면:** 약 40km/h로 달리다 세게 브레이크를 밟아 서는 정차를 10분 안에 세 번 합니다. 그러면 세 번째 정차 뒤 내비 위에 "정지 지점보다 조금 일찍 감속해보세요" 카드와 알림음이 뜹니다.
7. 기어를 **P**로 두면 3분 뒤 기록이 끝납니다. 바로 끝내려면 홈의 **운행 기록 종료**를 누릅니다. 홈의 **운행을 마쳤어요**에서 이동 평가와 주행 상세로 갑니다.
8. 운행이 쌓이면 **기록**, **프로필**, 홈의 출발 조언과 "왜 이 조언인가요?"가 채워집니다.

시연 영상의 3주치 기록은 `docs/pleos-emulator/tools/demo_data.py`와 `demo_history.py`로 넣은 시연용 데이터입니다. 이 도구는 앱 데이터에 직접 쓰므로 debug 빌드가 필요합니다. 일반 Android 기기(차량 아님)에서는 홈의 **샘플 시작**으로 합성 주행을 볼 수 있습니다.

### 주요 문서

- [안내 카드의 근거](docs/guidance-card.md): 카드 규칙과 문구의 출처
- [탐지 기준의 근거](docs/detection-thresholds.md): 운전 패턴 기준값의 출처
- [환경 데이터의 근거](docs/environment.md): 온도·공조·대기질
- [Pleos 에뮬레이터 측정](docs/pleos-emulator/README.md): 알림·백그라운드·주행 제한 실측
- [문서 지도](#문서-지도): 나머지 문서

## 개발 환경

기획·화면·작업 보드는 [Notion 팀 대시보드](https://gainful-trilby-a8b.notion.site/HMG-40bae4c07fd6804ba9fed356a13e7a28)에 있습니다. 실제 구조와 공개 API는 이 저장소의 문서와 코드가 기준입니다. 처음 받은 분은 아래 실행 절차 → [두 사람 시작 가이드](docs/quick-start.md) → [개발 규칙](CONTRIBUTING.md) 순서로 읽습니다.

### release APK 만들기

`./gradlew :app:assembleRelease`로 만듭니다. 결과는 `app/build/outputs/apk/release/app-release.apk`입니다.

- **서명 정보:** `local.properties`(Git 제외)의 `release.storeFile`, `release.storePassword`, `release.keyAlias`, `release.keyPassword`에서 읽습니다.
- **서명 정보가 없으면:** 서명되지 않은 APK가 만들어집니다.

## 처음 실행

필수 도구는 **JDK 17, Android SDK, Python 3.9 이상**입니다. Android Studio는 화면 개발에 권장합니다. 시스템 Gradle·별도 Python 패키지·API 키·실제 차량은 필요하지 않습니다.

1. 저장소 초대를 수락하고 `gh repo clone kako-labs/motion-care`로 받은 뒤 프로젝트 폴더로 이동합니다. GitHub CLI 대신 Android Studio의 Git clone 기능을 사용해도 됩니다. `local.properties`, `.gradle`, `build`, 개인 에뮬레이터는 공유하지 않습니다.
2. Android Studio에서 프로젝트를 엽니다. SDK Manager에서 API 35·Build Tools 35.0.0·Command-line Tools·Platform Tools·Emulator를 설치합니다.
3. Gradle JDK를 **17**로 설정합니다. 터미널도 같은 JDK를 사용합니다. `ANDROID_HOME` 또는 Android Studio가 만든 `local.properties`의 `sdk.dir`로 SDK 위치를 지정합니다.
4. 프로젝트 루트에서 실행합니다. Windows에서는 `python3` 대신 `py -3`를 사용할 수 있습니다.

```sh
python3 scripts/dev.py doctor
python3 scripts/dev.py verify
```

`verify`는 모듈 의존성 검사 + APK 빌드 + 모든 모듈 단위 테스트 + Android lint입니다. Android Studio의 `app` 실행 버튼으로 API 28 이상 기기에서 샘플 앱을 열 수 있습니다.

APK: `app/build/outputs/apk/debug/app-debug.apk`

## 프로젝트가 준비하는 테스트 환경

| 항목 | 공유 방식 |
| --- | --- |
| Gradle | Wrapper 8.12 + 배포 checksum |
| 라이브러리 | version catalog + 모듈별 dependency lockfile |
| SDK·테스트 기기 | `config/android-test.properties` |
| 테스트 데이터 | `data/trip` 내부 고정 샘플, 테스트의 FakeRepository |
| 화면 테스트 | `app/src/androidTest` + Gradle Managed Device |
| 실행 명령 | 표준 라이브러리만 사용하는 `scripts/dev.py` |
| CI | Linux에서 동일 verify/UI 명령과 보고서 업로드 |

```sh
# 프로젝트에 정의된 Pixel 2 / API 35 기기를 Gradle이 생성·실행·정리
python3 scripts/dev.py ui

# 이미 켜 둔 별도 테스트 기기를 이용하는 대안
python3 scripts/dev.py ui-connected

# 개인 Gradle 설정·캐시를 배제해 공유 환경 점검
python3 scripts/dev.py verify --clean-home
```

UI 테스트 최초 실행은 시스템 이미지를 다운로드하며 하드웨어 가상화가 필요합니다. [환경 준비·문제 해결](docs/testing.md)을 확인하세요. 이 테스트는 일반 Android 기기에서 돌며, Pleos 환경에서 따로 측정한 결과는 [에뮬레이터 측정 기록](docs/pleos-emulator/README.md)에 있습니다.

## 구조

```text
app/                 앱 조립·내비게이션
core/model/          Android에 의존하지 않는 데이터 모델
core/ui/             재사용 화면 요소
data/profile/       프로필 Repository·저장
data/trip/          주행 Repository·원본 저장·세션·샘플 입력
feature/driving/    주행 UI·DrivingViewModel
feature/history/    목록·상세·프로필·출발/종료 UI·각 ViewModel·기록 쪽 화면 이동
```

두 feature는 서로 참조하지 않습니다. 데이터 접근은 data 모듈의 Repository를 통하고 DB·입력 구현은 `internal`로 숨깁니다. `app`은 주입과 메인 화면을 조립하고, 기록 쪽 화면 이동은 `feature/history`의 `historyGraph`가 맡습니다. 각각 독립 Gradle 모듈입니다.

모듈은 기능과 책임으로 나눴으며 사람별 개인 모듈이 아닙니다. 현재 분담은 주행 담당이 app·data/trip·feature/driving, 기록 담당이 data/profile·feature/history입니다. 공통 모델·UI는 함께 확인하고 주행 담당이 통합합니다.

## 동료에게 소스 전달

Git remote로 공유하거나, 아래 명령으로 개인 설정·캐시·빌드 결과를 제외한 ZIP을 만듭니다.

```sh
python3 scripts/export_source.py
```

`artifacts/motion-care-source.zip`을 전달하면 됩니다. 동료는 압축을 풀고 이 README의 처음 실행 절차를 따라갑니다. 이 ZIP은 현재 Git에 커밋되지 않은 새 소스도 포함합니다. 시안과 측정 자료가 들어 있어 용량은 약 40MB입니다.

## 문서 지도

- [Notion 팀 대시보드](https://gainful-trilby-a8b.notion.site/HMG-40bae4c07fd6804ba9fed356a13e7a28): 기획·화면·작업 보드·일정·제출 준비
- [개발 규칙](CONTRIBUTING.md): 브랜치·PR·제출 전 확인 항목
- [Claude Code 안내](CLAUDE.md): 명령·구조·작업 흐름 요약
- [구조와 공식 근거](docs/architecture.md): 왜 이 정도로 나눴는지
- [소유권](docs/ownership.md): 각자 수정할 위치·공통 변경 절차
- [연결 계약](docs/contracts.md): Repository·단위·상태·오류
- [환경·테스트](docs/testing.md): 로컬·CI·에뮬레이터 재현
- [인계·후속 순서](docs/handoff.md): 구현된 것과 다음 작업
- [검증 기록](docs/verification.md): 실제 확인한 범위
- [기획서 초안](docs/proposal.md): 공모전 접수양식 항목별 초안과 제출 전 확인 항목
- [화면 디자인](docs/design.md): 시안 링크·스타일 기준·앱 아이콘·Pleos 화면 제약
- [Pleos 에뮬레이터 측정](docs/pleos-emulator/README.md): 알림·백그라운드·주행 제한 실측
- [탐지 기준의 근거](docs/detection-thresholds.md): 운전 패턴 기준값의 출처·실측·한계
- [안내 카드의 근거](docs/guidance-card.md): 주행 중 카드 규칙·문구의 출처·실측·한계
- [환경 데이터의 근거](docs/environment.md): 온도·공조·대기질의 출처·규칙·한계
- [운행 자동 기록](docs/auto-record.md): 기어로 운행을 시작·종료하는 규칙·근거·측정·한계
- [운행 기록·분석·탑승자 피드백](docs/history-analysis.md): 좌석 고정부터 패턴 분석·출발 전 조언·완료 평가까지의 데이터 흐름과 한계
- [화면 모음 만들기](docs/screen-gallery.md): 에뮬레이터에서 모든 화면을 찍어 설명 페이지로 묶는 방법과 다른 AI에게 줄 프롬프트
- [시연 영상](demo-video/README.md): Remotion 영상 프로젝트. 만드는 순서와 배운 것은 [demo-video/docs/howto.md](demo-video/docs/howto.md)

프로필 등록·선호 편집·좌석 연결·동승자 평점 저장이 연결돼 있습니다. 환경은 홈과 출발 화면에서 현재 온도·대기질과 상태·출처를 표시하며, 주행 상세에서 과거 `history(tripId)`를 읽는 연결은 남아 있습니다. 홈은 `Home2` 시안 배치이고, 종료 분기는 간단 요약을 사용합니다. AI 요약·녹음·실제 경로 연동은 미구현입니다. 주행 중 안내는 패턴 카드, 대기질과 내기·외기 상태에 따른 환경 카드, 쉬지 않고 2시간을 달렸을 때의 휴식 카드, 소리(알림음·음성 선택)까지이며 앱 안 배너는 구현하지 않았습니다. 케어·경로는 메인 기능 완료 후 시간이 남을 때만 진행합니다.
