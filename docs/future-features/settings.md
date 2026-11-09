# settings

담당: 기록 담당

주행 안내 ON/OFF는 기록 중 판단에 쓰여 `data/trip`의 `GuidanceSettings`로 먼저 구현했고, 스위치는 홈 화면의 "안내" 카드에 있습니다(`feature/driving`의 `GuidanceSwitch`). 후속 구현: 출발 안내 ON/OFF와 통합 설정 화면.

저장은 `data/profile`의 Repository에 추가하고, 설정 화면은 `feature/history`에 둡니다. `feature/driving`이 설정을 읽으려면 루트 `build.gradle.kts`의 `allowedEdges`에 `:data:profile` 의존을 추가합니다.
