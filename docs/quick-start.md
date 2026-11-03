# 두 사람이 바로 시작하기

## 공통

1. README대로 JDK 17·SDK 준비.
2. `python3 scripts/dev.py doctor`.
3. `python3 scripts/dev.py verify`.
4. Android Studio에서 app 실행 → 샘플 시작/신호 추가/종료/상세 확인.

## 전경연님

- `app/.../AppGraph.kt`: Repository 생성과 주입.
- `data/trip/.../TripRepository.kt`: 상대에게 주는 공개 기능.
- `data/trip/.../session/TripSessionController.kt`: 수명·저장 흐름.
- `feature/driving/.../DrivingViewModel.kt`: 주행 화면 상태.

실제 데이터 입력을 바꿔도 기록 UI는 Repository 계약을 유지하면 동작해야 합니다.

## 송호현님

- `feature/history/.../navigation/HistoryNavigation.kt`: 새 화면 경로를 추가하는 위치.
- `data/profile/.../ProfileRepository.kt`: 프로필·차량·인물 연결·평가·설정 저장을 추가하는 위치.
- `feature/history/.../profile/ProfileViewModel.kt`: Repository에서 화면까지 연결한 예시.
- `feature/history/.../HistoryViewModel.kt`: 목록을 받는 방법.
- `feature/history/.../TripDetailViewModel.kt`: 선택 주행 조회·오류·재시도.
- `feature/history/.../ui/RecentTrips.kt`: 목록 UI.
- `feature/history/.../report/TripDetailContent.kt`: 상세 UI.
- feedback/departure 패키지: 다음 기능을 붙일 자리.

UI 개발에는 차량·API 키가 필요하지 않습니다. ViewModel 단위 테스트에서는 해당 Repository의 fake를 주입합니다.

## 첫 공동 확인

- [ ] 같은 샘플 원본을 조회한다.
- [ ] unit/시간/source 의미를 설명할 수 있다.
- [ ] 상대 구현을 import하지 않는다.
- [ ] 새 화면 상태는 담당 feature 안에 둔다.
- [ ] 공통 계약 변경은 따로 리뷰한다.
- [ ] 기능 완료마다 다른 사람이 실행해 본다.
