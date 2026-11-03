# Motion Care

차량 앱 공모전의 **협업용 Android 뼈대**입니다. 현재는 합성 샘플만 실행하며 실제 차량 SDK·분석·AI는 후속 구현 영역입니다.

KAKO Labs의 비공개 저장소: [kako-labs/motion-care](https://github.com/kako-labs/motion-care). 초대받은 GitHub 계정으로 접근합니다.

**처음 받은 분:** 아래 실행 절차 → [두 사람 시작 가이드](docs/quick-start.md) → [개발 규칙](CONTRIBUTING.md) 순서로 읽습니다.

## 현재 가능한 것

샘플 시작 → 신호 추가 → 종료 → 저장 목록 → 상세 → 평가 자리 → 같은 상세 복귀. 앱 프로세스 종료 후에도 기록이 유지되고 미완료 기록은 중단됨으로 보존됩니다.

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

UI 테스트 최초 실행은 시스템 이미지를 다운로드하며 하드웨어 가상화가 필요합니다. 실제 Pleos 호환성 검사는 별도입니다. [환경 준비·문제 해결](docs/testing.md)을 확인하세요.

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

모듈은 기능과 책임으로 나눴으며 사람별 개인 모듈이 아닙니다. 현재 분담은 전경연님이 app·data/trip·feature/driving, 송호현님이 data/profile·feature/history입니다. 공통 모델·UI는 함께 확인하고 전경연님이 통합합니다.

## 동료에게 소스 전달

Git remote로 공유하거나, 아래 명령으로 개인 설정·캐시·빌드 결과를 제외한 ZIP을 만듭니다.

```sh
python3 scripts/export_source.py
```

`artifacts/motion-care-source.zip`을 전달하면 됩니다. 동료는 압축을 풀고 이 README의 처음 실행 절차를 따라갑니다. 이 ZIP은 현재 Git에 커밋되지 않은 새 소스도 포함합니다.

## 문서 지도

- [구조와 공식 근거](docs/architecture.md): 왜 이 정도로 나눴는지
- [소유권](docs/ownership.md): 각자 수정할 위치·공통 변경 절차
- [연결 계약](docs/contracts.md): Repository·단위·상태·오류
- [환경·테스트](docs/testing.md): 로컬·CI·에뮬레이터 재현
- [인계·후속 순서](docs/handoff.md): 구현된 것과 다음 작업
- [검증 기록](docs/verification.md): 실제 확인한 범위
- [화면 디자인](docs/design.md): 시안 링크·스타일 기준·Pleos 화면 제약
- [Pleos 에뮬레이터 측정](docs/pleos-emulator/README.md): 알림·백그라운드·주행 제한 실측

프로필은 저장소와 목록 조회까지만 연결했고 등록 화면은 구현하지 않았습니다. 평점은 아직 화면 연결 자리입니다. 환경 API·패턴 분석·AI 요약·녹음·주행 중 배너는 구현하지 않았습니다. 케어·경로는 메인 기능 완료 후 시간이 남을 때만 진행합니다.
