# Windows 개발 환경 인수인계

이 문서는 다른 Windows 노트북에서 `feature/history-ui-v2` 작업을 그대로 이어가기 위한 기준입니다. Android SDK나 개인 경로는 Git에 저장하지 않고, 버전과 설정 절차만 저장합니다.

## 1. 저장소와 브랜치 받기

```powershell
git clone https://github.com/kako-labs/motion-care.git
cd motion-care
git fetch origin
git switch --track origin/feature/history-ui-v2
```

이미 저장소가 있다면 다음 명령을 사용합니다.

```powershell
git fetch origin
git switch feature/history-ui-v2
git pull --ff-only
```

## 2. 이 노트북에서 사용한 버전

| 항목 | 기준 |
| --- | --- |
| JDK | Microsoft OpenJDK 17 (`17.0.21`) |
| Android compile/target SDK | 35 |
| Android min SDK | 28 |
| Android Build Tools | 35.0.0 |
| Android Command-line Tools | latest |
| 테스트 시스템 이미지 | Android 35 / default / x86_64 / Pixel 2 |
| Gradle | Wrapper 8.12 |
| Android Gradle Plugin | 8.10.1 |
| Kotlin | 2.1.20 |
| Python | 3.9 이상, 외부 패키지 불필요 |

정확한 프로젝트 기준값은 `config/android-test.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/gradle-wrapper.properties`가 소유합니다.

## 3. 필수 프로그램 설치

회사 정책이 허용하면 Windows Package Manager를 사용할 수 있습니다.

```powershell
winget install Microsoft.OpenJDK.17
winget install Google.AndroidStudio
```

설치 후 Android Studio의 SDK Manager에서 아래 항목을 준비합니다.

- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0
- Android SDK Platform-Tools
- Android SDK Command-line Tools (latest)
- Android Emulator
- Android 35의 `default x86_64` 시스템 이미지 — 에뮬레이터/UI 테스트를 실행할 때만 필요

Android Studio의 Gradle JDK도 17로 지정합니다. 현재 노트북은 `C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot`을 사용했지만, 다른 노트북에서 이 경로를 그대로 복사하면 안 됩니다.

## 4. 프로젝트 자동 설정

프로젝트 루트에서 다음 스크립트를 실행합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-windows.ps1
```

에뮬레이터 시스템 이미지까지 준비하려면 다음 옵션을 추가합니다.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/setup-windows.ps1 -IncludeEmulatorImage
```

스크립트는 다음 작업만 수행합니다.

1. Java 17과 Python을 확인합니다.
2. Android SDK와 `sdkmanager` 위치를 확인합니다.
3. 프로젝트가 요구하는 SDK 패키지를 설치합니다.
4. 개인 SDK 경로를 `.gitignore` 대상인 `local.properties`에 기록합니다.
5. `scripts/dev.py doctor`로 환경을 검사합니다.

SDK 라이선스 동의는 사용자가 Android Studio SDK Manager 또는 아래 명령으로 직접 확인합니다.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

## 5. 확인 명령

먼저 가벼운 확인부터 실행합니다.

```powershell
py -3 scripts/dev.py doctor
.\gradlew.bat :app:compileDebugKotlin "-Djavax.net.ssl.trustStoreType=Windows-ROOT" --max-workers=1
```

사내 인증서 환경에서는 `Windows-ROOT` 옵션이 Gradle HTTPS 인증에 필요할 수 있습니다. 전체 검증은 시간이 더 걸립니다.

```powershell
py -3 scripts/dev.py verify --clean-home
```

현재 브랜치에서 확인된 항목:

- `:app:compileDebugKotlin` 성공
- `:data:trip:testDebugUnitTest` 성공
- `:data:profile:testDebugUnitTest` 성공
- `:feature:history:testDebugUnitTest` 성공
- `:app:compileDebugAndroidTestKotlin` 성공
- 이 회사 노트북에서는 Gradle 8.12 Windows 캐시 이동 오류 때문에 최종 APK 묶기를 중단했으며, 소스 컴파일 오류는 아니었습니다.

## 6. HTML UI 데모

Android SDK 없이도 아래 파일을 브라우저에서 직접 열 수 있습니다.

```text
demo/mock-data-ui/index.html
```

HTML, CSS, JavaScript가 분리되어 있습니다.

- `index.html`: 화면 구조
- `style.css`: 색상, 5인승 차량 실내, 레이아웃
- `app.js`: 가상 운행·프로필 데이터와 화면 이동

프로필 하단 메뉴는 `프로필 선택 → 개인 상세` 순서로 동작합니다. 홈은 5인승 좌석, 중앙 조언, 오른쪽 최근 운행으로 구성됩니다.

## 7. Android 디버그 가상 데이터

`app/src/debug`에는 실제 앱의 `TripRepository`와 `ProfileRepository` 계약을 사용하는 디버그 전용 `DemoActivity`가 있습니다. 출시 빌드 소스에는 포함되지 않습니다. 앱을 설치할 수 있는 환경에서는 다음 Activity를 실행합니다.

```text
dev.motioncare.scaffold/dev.motioncare.app.DemoActivity
```

가상 데이터는 실제 차량 수신을 대신하는 시연용이며 `DataOrigin.VEHICLE`로 표시됩니다. 실제 Pleos 차량 신호 연결과 Pleos 에뮬레이터 검증은 별도 단계입니다.

## 8. Git에 올리면 안 되는 것

- `local.properties`
- `.gradle/`, `.gradle-user-home/`, 각 모듈의 `build/`
- Android SDK와 AVD 폴더
- `.env`, API 토큰, 서명 키
- 개인 PC의 절대 경로
