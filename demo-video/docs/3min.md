# 3분 영상 (MotionCare3Min)

Pleos 에뮬레이터와 Virtual Console을 새로 돌려 녹화한 화면으로 만든 2분 44초 영상입니다. 4분 영상(`MotionCareDemo`)처럼 6분 시연 영상을 잘라 쓰지 않습니다.

- 결과: `out/motioncare-demo-3min.mp4` (1920x1080, 30fps, 알림음 포함). 영상과 녹화본(`public/rec/`)은 Git에 넣지 않습니다.
- 장면표: `docs/shot-list-3min.md` (`node tools/shot-list3.mjs`로 다시 만듦)
- 편집 파일: `src/short/timeline.ts` 한 줄이 장면 하나입니다. 화면은 `src/short/Short.tsx`, 아래 두 칸은 `src/short/Panels.tsx`, 확대는 `src/short/camera.ts`.

## 구성

브랜드 소개 8초 → 6단계 23장면 → 브랜드 마무리 10초.

| 단계 | 내용 | 배치 |
| --- | --- | --- |
| 1 출발 전 | 홈의 출발 조언, 함께 타는 사람과 차 안팎 환경, 좌석 등록, 캐릭터 | Pleos 크게 |
| 2 주행 중 | 기어 D로 자동 기록 시작, 정차 직전 큰 감속 3번, 내비 위 카드와 알림음 | 4분할 |
| 3 도착 후 | 천천히 정차 → P (4분할), 운행 기록 종료 → 이동 평가(보호자가 대신, 별 3개, 출발·정차) | 4분할 → Pleos 크게 |
| 4 차 안 공기 | 다른 운행: 외기 유입 + 바깥 대기질 나쁨 → 공기 카드와 알림음 | 4분할 |
| 5 쌓인 기록 | 20번의 운행 기록, 사람별 보기, 운행 상세, 고속도로 나들이, 운전자 프로필 | Pleos 크게 |
| 6 다음 출발 | 홈의 출발 조언, 왜 이 조언인지, 계산 방법 | Pleos 크게 |

- **4분할**은 차량 값이 의미 있는 주행 장면에만 씁니다. 왼쪽 위 Virtual Console, 오른쪽 위 Pleos, 왼쪽 아래 차량 데이터, 오른쪽 아래 움직임 분석과 이번 운행의 카드 기록입니다.
- **Pleos 크게**는 앱 설명 장면입니다. 장면마다 `cams`로 필요한 영역을 부드럽게 확대합니다(영역 좌표는 Pleos 화면 2560x1440 기준, uiautomator bounds와 같음).
- 배치가 바뀌는 장면의 처음 0.9초 동안 Pleos 칸이 옮겨 가고 나머지 칸이 나타나거나 사라집니다.
- 1.3배속 이상인 장면은 맨 위 오른쪽에 배속을 표시합니다. 카드와 소리가 나오는 장면은 1배속입니다.
- 공기 카드 장면은 다른 운행이라 "4 차 안 공기"로 따로 두었습니다. 이 운행은 평가하지 않았습니다.

## 녹화 (tools/rec)

같은 노트북에서 다른 세션이 에뮬레이터를 쓰므로 AVD `pleos`(emulator-5600)만 건드립니다. 끄기 전에는 `adb -s emulator-5600 emu avd name`으로 확인합니다.

```sh
# 소리와 gRPC 화면을 받으려면 오디오를 켜고 gRPC 포트를 엽니다
emulator -avd pleos -port 5600 -no-snapshot -no-boot-anim -gpu host -netdelay none -netspeed full -grpc 8590
# Virtual Console 페이지가 http://127.0.0.1:8796 에 떠 있어야 합니다
cd demo-video/tools/rec && npm install     # @grpc/grpc-js, @grpc/proto-loader, playwright-core. 설치된 Chrome을 씀
```

1. **앱 테이크 먼저.** 촬영용 20회 기록(`docs/pleos-emulator/tools/demo_history.py`)이 그대로인 상태에서 찍습니다. 아무것도 저장하지 않고 화면마다 뒤로 나옵니다.
   `python3 app_scenes.py <앱 테이크 폴더>`
2. **주행 테이크.** 운행 두 번과 평가 하나가 기록에 더해집니다.
   `python3 drive_takes.py <주행 테이크 폴더>`
3. **기록 되돌리기.** 저장소 루트에서 `python3 docs/pleos-emulator/tools/film_takes.py restore`
4. **맞추기.** 에뮬레이터 시계와 호스트 시계 차이(ms)를 재서 넘깁니다(`adb -s emulator-5600 shell date +%s.%N`과 호스트 시각 비교. 11/12 측정 −942~−947).
   `python3 prep3.py <앱 테이크> <주행 테이크> -942`
   `public/rec/{app_pleos,drive_pleos,drive_console}.mp4`, `public/rec/drive_audio.wav`, `src/data/{app,drive}.json`이 생깁니다.

녹화 방식:

- Pleos 화면: 에뮬레이터 gRPC `streamScreenshot`을 1920x1080으로 받아 최신 프레임을 일정한 30fps로 씁니다(`screen.js`). 화면 변화는 초당 약 19~20번입니다. `adb screenrecord`도 같은 수준이었고, 2560 원본으로 받으면 초당 11번으로 떨어졌습니다.
- 소리: gRPC `streamAudio`(`audio.js`).
- Virtual Console: Playwright로 연 Chrome의 CDP 스크린캐스트(1240x1080)와, 콘솔이 차에 보낸 속도·페달·조향·기어를 0.1초마다 적은 기록(`console.js`). 주행 조작도 이 페이지의 컨트롤로 합니다(`/drive?gear=&accel=&brake=&steer=`).

## 시각 맞춤

- Pleos 녹화의 첫 프레임이 0초입니다.
- 소리는 앱 로그 `GuidanceSound: started`의 시각에 맞춥니다(에뮬레이터 시계를 호스트 시계로 옮기고, 소리 패킷이 늦게 오는 만큼(중앙값 567ms)을 뺌).
- Pleos 계기판 속도는 콘솔 입력보다 약 0.3초 늦게 바뀝니다(계기판이 초당 2~3번 갱신). 콘솔과 차량 데이터 칸을 0.3초 늦춰 같은 값을 보이게 합니다(`CONSOLE_DELAY_SEC`).
- **알림음은 카드가 화면에 보이기 약 0.5~0.7초 전에 시작합니다.** 앱이 소리를 낸 시각(로그)과 화면 녹화에 카드가 나타난 시각의 차이이며, 녹화된 그대로 두었습니다. 시스템 UI가 카드를 그리는 시간과 화면 전송 지연이 섞인 값이라 실차에서의 차이는 확인하지 못했습니다.

## 움직임 분석 그래프

- 앱이 계산한 값이 아닙니다. 콘솔이 보낸 속도·조향 입력값(0.1초 간격, 소수점 그대로)을 앱 문서(`docs/detection-thresholds.md`)의 식으로 다시 계산합니다. 화면에 "콘솔 입력값으로 다시 계산"이라고 적었습니다.
  - 가감속 = 1초 동안의 속도 변화, 좌우 움직임 = 속도² × tan(조향각 ÷ 14.26) ÷ 3.0m
- 선을 다듬거나 보간하지 않습니다. 주황 점선은 앱의 구분선 2.0m/s²(정차 직전 큰 감속·빠른 출발)과 0.9m/s²(반복 좌우)입니다. 멀미 기준이 아닙니다.
- 이번 주행에는 핸들 조작이 없어 좌우 움직임은 0입니다.
- 아래 칸의 "내비 위 카드" 기록은 녹화 중 알림 목록에서 읽은 카드 문구입니다. 운행이 바뀌면 다시 셉니다.

## 만들기

```sh
npm run studio3     # 미리 보기 (MotionCare3Min 선택)
npm run render3     # out/motioncare-demo-3min.mp4
npx remotion still src/index.ts MotionCare3Min out/check.png --frame=1600
```

이 PC(macOS, `--concurrency=6`)에서 1080p 전체 렌더는 약 3분 걸렸습니다. Remotion 4.0.538로 렌더했고, `package.json`은 4분 영상과 같은 4.0.380입니다(`trimBefore`는 두 버전 모두에 있음).

## 확인한 것

- 장면 23개의 맨 위 문장이 한 줄에 들어가고, 화면에 보이는 것만 적었습니다. 운행 상세(11/11)에는 동승자 응답이 없어 "차 안 환경과 다음 출발 조언"으로, 응답이 보이는 고속도로 나들이에만 "동승자 응답"을 적었습니다.
- 마무리의 "영상에서 쓴 Pleos 차량 기능"은 영상에 나온 것만 적었습니다(차량 속도, 기어, 공조(내기 순환), 실외 온도, 위치 기반 대기질, 내비 위 알림 카드). 음성 안내와 조향 기능은 영상에 없어 적지 않았습니다.
- 차량 데이터 칸의 속도와 Pleos 계기판 속도, 그래프와 정차 순간, 알림음과 카드 장면을 프레임으로 확인했습니다.

## 확인하지 못한 것

- 실차. 모든 화면은 에뮬레이터입니다.
- 앱 화면의 실제 갱신은 초당 약 20번이라 스크롤이 30fps 원본보다 덜 매끄럽습니다.
- 다른 PC(Windows)에서 이 컴포지션을 렌더해 보지 않았습니다.
