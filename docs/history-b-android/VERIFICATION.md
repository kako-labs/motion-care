# 최종 Android 연결 검증 · 2026-11-08

## 빌드·단위 검사

- 최종 소스에서 표준 `python scripts/dev.py verify` 통과: APK·아키텍처 검사·전체 단위 검사·lint, BUILD SUCCESSFUL(1분 29초).
- 전체 단위 검사 **274개, 32개 suite, 실패/오류 0**. 이 중 history 28개. [집계 JSON](device-checks/unit-tests-final.json).
- `:app:assembleRelease` 통과(1분 15초). release APK의 전체 dex와 asset 이름을 검사해 debug Activity·시연 Repository·합성 JSON이 제외된 것을 확인. [release 격리](device-checks/release-isolation.json).
- JDK 17·기존 SDK 35 사용. Gradle·권한·SDK 설정 변경 없음. 한 번에 한 Gradle 작업만 실행했고, 기존 증분 캐시 문제를 피해 실행 환경에 `kotlin.incremental=false`, `kotlin.compiler.execution.strategy=in-process`를 지정했다. 이를 빌드 설정 파일에 커밋하지 않았다.

## 실제 Android UI·저장

기기는 일반 Android API 35 x86_64의 `mc_car`, density 160이다. Pleos나 실차 결과가 아니다.

1024×768에서 최종 UI/저장 검사 **6개 모두 통과**. [검사 결과](device-checks/ui-1024-persistence.json).

1. 조언에서 선택한 감속 3번이 같은 운행 상세로 전달됨.
2. 기존 평가 저장 콜백으로 4점 저장 → 재진입 → Activity 재생성 후 응답 유지.
3. 미응답 운행에서 건너뛰어도 평가를 만들지 않음.
4. 기록 없음과 중단된 운행 상태 구별.
5. 뒷좌석 터치 영역이 겹치지 않고 별점 터치 폭이 56dp 이상임.
6. 실제 공개 Profile/Trip SQLite Repository를 UUID 테스트 파일 이름 공간에서 사용: feature UI로 프로필 민감도와 보호자 평가 5점/커브 저장 → 새 Repository에서 재조회. 운행 완료 상태·샘플 수도 유지. 정상 앱 DB의 SHA-256 불변 확인.

감속 상태 전달 자동 검사는 부분 노출 좌표에 의존하지 않도록 접근성 OnClick으로 선택하고, 선택 요약을 스크롤해 확인한다. 같은 흐름의 실제 터치도 1024에서 직접 실행해 확인했다. 별도 Compose 검사는 실제 좌석·별점 터치 영역을 측정한다. 이 둘을 같은 종류의 검증으로 과장하지 않는다.

앱 소유 테스트는 [검토 패치](review-patches/)로 전달한다. 임시 적용 후 제거했으며 최종 `app` 소스 변경은 없다. 기존 placeholder 기반 `ScaffoldFlowTest` 전체가 통과했다는 뜻은 아니다. 프로필/평가의 재조회와 Activity 재생성은 확인했으며 실차 운행·서비스 종료/재시작 전체 흐름을 검증한 것은 아니다.

## 일반 앱 진입과 시연 격리

일반 MainActivity에서 기존 버튼을 통해 B 출발 화면에 진입했다. 실제 Repository에 기록이 없으면 빈 상태가 나오며 지영 시연 3건으로 채우지 않는다. [일반 앱 진입](device-checks/normal-home-resumed.png), [B 출발 빈 상태](device-checks/normal-departure-resumed.png), [빈 기록 목록](device-checks/normal-records-final.png). 최종 일반 DB 검사에서도 사람·운행·평가 0건이고 시연 ID가 없었다. [정상 DB 대조](device-checks/normal-database-final.json).

지영·서아/j1~j3는 `src/debug`의 메모리 Repository/fixture에만 존재한다. 시연 평가 저장은 별도 debug SharedPreferences를 사용한다. 이전 직접 저장 검사에서 정상 DB SHA-256과 fixture ID 부재도 대조했다. [이전 격리 결과](device-checks/database-isolation.json). 이 해시는 Gradle connected UI 검사 전의 DB에 대한 증거다. Gradle은 검증용 앱을 정리할 수 있으므로 모든 테스트 전후 같은 기기 DB가 계속 보존됐다는 주장으로 쓰지 않는다.

## 최종 화면 캡처

- [1682×1210](device-checks/final/1682x1210/): 출발·기록·운전자 프로필/전체 기록·동승자 프로필·조언·선택 감속·운행 상세/그래프·평가/저장 동작·종료/동작.
- [1024×768](device-checks/final/1024x768/): 같은 화면과 흐름. 짧은 가로 창에서도 좌석 5개·최근 기록 3건·사선 제안을 함께 표시한다.
- 두 크기에서 각 13장, 총 26장. `results.json`은 캡처별 필수 문구와 PNG SHA-256을 보존하며, 26개 PNG의 해시를 다시 대조했다. 접근성 문구 확인만으로 배치·클리핑을 모두 검증했다고 주장하지 않으며, PNG 시각 검토와 별도 터치 검사를 함께 사용했다.
- 동승자 프로필에서 중복 얼굴·편집 버튼을 제거하고 기존 헤더 한 곳으로 통일했다. 선호·응답 표시 공간을 확보했다.
- 루트 device-checks의 이전 PNG에는 리뷰 전 상태도 있으므로 최종 디자인 검토에는 `final/`을 사용한다.

## 변경 보호·리뷰

- 원래 기록 담당 트리의 15개 파일은 보존 기준 `315bee2`와 일치. [파일 대조](preserved-owned-files.json).
- `315bee2..HEAD` 신규 범위에서 app/core/data/Gradle 변경 없음. 선행 PR #10의 계약/DB 변경은 기존 의존성이다.
- 종료 분기 패치와 UI 검사 패치 2개는 모두 적용 전 검사 통과. 앱 소스에는 적용하지 않았다.
- 독립 리뷰의 상태 표시·평균 설명·좁은 좌석 겹침을 수정했고, 이번 재개 변경에도 제품 코드 차단 이슈는 없었다. 오래된 검사 패치 불일치를 동기화했다.

## 통합 담당에게 남는 확인

[통합 검토](INTEGRATION-REVIEW.md)의 홈 바깥 틀·종료 분기·실제 운행 시작 시 탑승 링크·환경 주입은 통합 담당 소유다. 선행 PR #10/#17 및 최신 main을 검토해 합친 고정 APK에서 실제 주행→종료→기록→평가를 다시 확인한다. 공유 main에 직접 병합하지 않았다.
