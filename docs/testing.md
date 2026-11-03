# 실행·테스트 환경

## 공유하는 것과 각 PC가 준비할 것

프로젝트는 도구 버전·의존성·샘플·기기 정의·테스트·실행 스크립트를 공유합니다. OS/CPU, JDK 바이너리, Android SDK와 가상화 드라이버는 각 PC의 기본 도구입니다. 특정 사용자의 설치 경로나 기존 AVD에 의존하지 않습니다.

| 도구 | 기준 | 설정 위치 |
| --- | --- | --- |
| JDK | 17 | JAVA_HOME·Android Studio Gradle JDK |
| Python | 3.9 이상, 외부 패키지 없음 | python3 또는 Windows py -3 |
| Gradle | 8.12 | Wrapper·checksum |
| AGP/Kotlin/라이브러리 | 고정 버전 | gradle/libs.versions.toml·lockfiles |
| SDK·Build Tools | 35 / 35.0.0 | config/android-test.properties |
| UI 테스트 기기 | Pixel 2 / API 35 / AOSP | 같은 properties·app Gradle Managed Device |

최초 다운로드에는 인터넷·디스크 여유가 필요합니다. SDK 라이선스는 설치자가 SDK Manager 또는 sdkmanager --licenses로 확인합니다. 이미 설치한 SDK를 사용해도 되고 새 SDK 위치를 지정해도 됩니다.

## 명령

```sh
python3 scripts/dev.py doctor
python3 scripts/dev.py setup-sdk
python3 scripts/dev.py verify
python3 scripts/dev.py ui
```

setup-sdk는 이미 설치된 Command-line Tools를 사용해 프로젝트에 필요한 패키지를 설치합니다. 임의 개인 경로를 추측하거나 OS 전역 도구를 바꾸지 않습니다.

`verify`는 에뮬레이터가 필요 없습니다. 모든 모듈 단위 테스트·Robolectric SQLite 검사·lint·APK 빌드·의존성 그래프 검사를 실행합니다.

`ui`는 프로젝트의 Gradle Managed Device를 생성/실행/정리합니다. Apple Silicon에서는 호스트에 맞는 이미지를 사용하고 Linux CI에서는 x86_64 가상화가 필요합니다. API 35 AOSP 이미지 제공/네트워크/호스트 지원 여부에 따라 최초 준비가 실패하면 아래 대안을 사용합니다.

```sh
# SDK Manager에서 API 35 테스트 기기를 준비하고 켜 둔 경우
python3 scripts/dev.py ui-connected
```

connected 테스트는 연결된 테스트 기기에서 샘플 기록을 생성합니다. 실제 사용 기기를 연결하지 않습니다. 예전 `scripts/smoke_emulator.py`는 강제 종료/재실행 확인용 보조 스크립트이며 주 검증은 저장소의 AndroidTest입니다.

## 개인 Gradle 설정 배제

```sh
python3 scripts/dev.py verify --clean-home
```

프로젝트의 .gradle-user-home을 사용하므로 개인 ~/.gradle/init.d·캐시·개인 Gradle 설정을 사용하지 않습니다. JDK·SDK는 doctor에서 확인한 설치를 사용합니다. 이 디렉터리는 공유 대상이 아닙니다.

## 테스트 위치

| 위치 | 확인 내용 |
| --- | --- |
| core/model/src/test | 단위·없음·원본/시각 불변조건 |
| data/profile/src/test | 프로필 재시작 유지·순서·이름 검증 |
| data/trip/src/test | 세션 중복/복구/실패, SQLite 재연결·버전 충돌 |
| feature/driving/src/test | 초기화 재시도·명령 중복 방지·오류 상태 |
| feature/history/src/test | 늦은 이전 조회·실패/없음/재시도, 프로필 로딩·오류·재시도 |
| app/src/androidTest | 샘플 입력·Activity 재생성·완료·평가 복귀, 프로필 화면 이동 |

## 결과와 CI

각 모듈 build/reports/tests, build/test-results, build/reports/lint-results-debug.html에 결과가 생깁니다. UI는 app/build/reports/androidTests 아래에 있습니다.

.github/workflows/android.yml은 Ubuntu 24.04에서 같은 스크립트로 host/UI 검사를 실행하고 결과를 artifact로 보존합니다. PR과 `main` push에서 실행하며, `.md`와 `docs/`만 바뀌면 건너뜁니다. 같은 PR에 새로 push하면 이전 실행은 취소됩니다. 최근 실행 결과는 [GitHub Actions](https://github.com/kako-labs/motion-care/actions)에서 확인합니다.

SDK 설치 action에는 `packages: platform-tools`를 명시합니다. 기본값에 포함된 구형 `tools` 패키지는 현재 SDK 저장소에서 찾을 수 없어 설치가 실패합니다. API·Build Tools·Emulator는 이후 `scripts/dev.py setup-sdk`가 프로젝트 설정에 맞춰 설치합니다.

## 자주 만나는 문제

- JDK 불일치: 터미널 java -version과 Android Studio Gradle JDK를 모두 17로 맞춥니다.
- SDK 경로 없음: ANDROID_HOME 또는 local.properties의 sdk.dir을 본인 경로로 설정합니다. 타인의 파일을 복사하지 않습니다.
- 시스템 이미지/라이선스: SDK Manager에서 설치·승인을 확인합니다.
- 가상화 없음: BIOS/OS 가상화를 준비하거나 API 35 테스트 기기로 ui-connected 실행.
- dependency lock 오류: 임의로 lockfile을 삭제하지 않고 버전 변경 여부를 확인합니다. 변경 작업일 때만 --write-locks 사용.
- 사내 프록시: 조직의 정상 프록시 설정 사용. 개인 토큰·프록시 비밀번호를 저장소에 넣지 않습니다.

실차 SDK·차량 표시면·자동 시동·권한의 검증은 별도이며 일반 Android UI 테스트로 대체하지 않습니다. Pleos 에뮬레이터 설치와 측정 방법은 [측정 기록](pleos-emulator/README.md)을 따릅니다.
