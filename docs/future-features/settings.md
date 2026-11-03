# settings

담당: 기록 담당

후속 구현: 출발 안내와 주행 안내의 개별 ON/OFF 저장. 아직 구현하지 않았습니다.

저장은 `data/profile`의 Repository에 추가하고, 설정 화면은 `feature/history`에 둡니다. `feature/driving`이 설정을 읽으려면 루트 `build.gradle.kts`의 `allowedEdges`에 `:data:profile` 의존을 추가합니다.
