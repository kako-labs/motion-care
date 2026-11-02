# 함께 개발하는 방법

## 작업 시작

1. `python3 scripts/dev.py doctor`로 환경을 확인합니다.
2. `python3 scripts/dev.py verify`가 통과하는 기준에서 기능 브랜치를 만듭니다.
3. 담당 모듈 안에서 구현합니다. 모듈별 시작 지점은 `docs/ownership.md`를 확인합니다.

## 공통 계약을 바꿀 때

요구하는 필드·단위·오류·소비 화면·샘플을 먼저 정리합니다. 공통 모델/Repository 변경을 작은 변경으로 합친 뒤 각 기능을 구현합니다. 소비자가 없는 인터페이스를 미리 대량 생성하지 않습니다.

## 변경 제출 체크리스트

- [ ] 수정 범위와 모듈 소유권이 일치한다.
- [ ] UI에서 DB·SDK를 직접 호출하지 않는다.
- [ ] 로딩·없음·오류·미지원 상태를 구분한다.
- [ ] 샘플/실제 출처를 보존한다.
- [ ] 의미 있는 로직/저장/상태 변경을 테스트한다.
- [ ] verify 통과. 내비게이션 변경은 UI 테스트도 통과.
- [ ] 공개 계약·스키마·실행 명령 변경을 문서에 반영한다.
- [ ] 개인 경로·키·local.properties·AVD·build 파일을 포함하지 않는다.

## 의존성 변경

버전은 `gradle/libs.versions.toml`에서 관리합니다. 의도적으로 변경할 때만 `./gradlew verifyAll :app:assembleDebugAndroidTest --write-locks`로 lockfile을 갱신하고 차이를 확인합니다. 일반 빌드에서는 `--write-locks`를 사용하지 않습니다.

## 공유

[KAKO Labs / motion-care](https://github.com/kako-labs/motion-care)는 비공개 저장소입니다. 초대를 수락한 GitHub 계정으로 clone합니다. 기본 브랜치는 `main`이며, 각자 기능 브랜치에서 작업한 뒤 Pull Request로 변경을 공유합니다.

소스·문서·Wrapper·lockfile·테스트·CI를 공유하며 `.gitignore` 대상은 제외합니다. 로컬 SDK 경로, 캐시, 빌드 결과, API 키는 커밋하지 않습니다.
