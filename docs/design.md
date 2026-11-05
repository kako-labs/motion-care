# 화면 디자인 기록

2026-11-03 기준입니다. 화면을 구현하거나 시안을 고칠 때 이 문서를 먼저 확인합니다.

## 링크

| 자료 | 위치 | 접근 조건 |
| --- | --- | --- |
| 화면 시안 (Claude Design) | [모션케어 앱 화면 시안](https://claude.ai/artifact/Rs6oUxktSkUH1nntnA7jEP) | 비공개입니다. 소유자가 공유 메뉴에서 공유해야 볼 수 있습니다. |
| 팀 보드 (Notion) | [대시보드](https://app.notion.com/p/40bae4c07fd6804ba9fed356a13e7a28), [디자인 시안 페이지](https://app.notion.com/p/40dae4c07fd68180a128ffc333949174) | 팀 워크스페이스 권한이 필요합니다. |
| Pleos 창 정책 | [Window Policy](https://document.pleos.ai/en/docs/connect/guide/docs-design/getting-started/window) | Pleos 개발자 문서 |
| Pleos 화면 모델 | [Screen model](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-planning-guide/app-structure-screen-model) | Pleos 개발자 문서 |
| Pleos 글꼴 기준 | [Typography](https://document.pleos.ai/en/docs/connect/guide/docs-design/foundations/typography) | Pleos 개발자 문서 |
| Pleos 주행 방해 심사 기준 | [Driver distraction](https://document.pleos.ai/en/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements) | Pleos 개발자 문서 |

## 현재 기준

- 시안을 열면 나오는 `2차 · 다크 글래스` 페이지가 기준입니다. `1차 시안` 페이지는 비교용으로만 남겨 두었습니다.
- 시안 작업은 위 링크에서 합니다. 저장소의 [보존 자료](#보존-자료)는 2026-11-03 시점의 사본이며, 시안을 고치면 함께 갱신합니다.
- 시안의 인물, 수치, 조언 문구, 사진은 모두 예시입니다. 실제 측정값, 의학 기준, 실제 사용자 정보가 아닙니다.

## 스타일 결정 과정

| 단계 | 방향 | 결과 |
| --- | --- | --- |
| 1차 | 현대자동차 컬러에 묵직한 느낌 | 다른 팀 시안과 비교해 스타일이 약하다고 판단해 중단 |
| 2차 | 어두운 남색 바탕, 유리 질감 카드, 생성 이미지 | 채택. 같은 스타일로 전체 화면을 제작 |

2차 방향을 잡을 때 다른 팀 시안 4개(WindowTales, 카-케스트라, 여기서 타!, 차마고치)를 비교 기준으로 삼았습니다.

## 화면 목록

크기는 시안 보드 기준이며, 실제 앱 창과의 차이는 [Pleos 제약](#시안에-아직-반영되지-않은-pleos-제약)을 확인합니다. `DrivingNarrow2`를 제외한 화면 보드는 모두 앱 하나가 앱 영역 전체를 쓰는 가로형입니다.

![2차 시안의 주요 화면 4개](design-archive/previews/sheet.png)

보드 이름을 누르면 미리보기 이미지가 열립니다.

| 보드 | 화면 | 크기 | 비고 |
| --- | --- | --- | --- |
| [KeyVisual2](design-archive/previews/KeyVisual2.png) | 키비주얼 | 1280×1280 | |
| [Home2](design-archive/previews/Home2.png) | 홈 · 출발 전 | 1600×1280 | `showApply`: 제안 공조 바로 적용 표시, 기본 꺼짐 |
| [Driving2](design-archive/previews/Driving2.png) | 주행 중 | 1600×1280 | `bannerKind`: 환경, 장시간 주행, 운전 패턴, 없음 |
| [DrivingNarrow2](design-archive/previews/DrivingNarrow2.png) | 주행 중 (1:1 창) | 840×1280 | 세로형. 실제 창 크기 미측정 |
| [Finished2](design-archive/previews/Finished2.png) | 종료 후 | 1600×1280 | |
| [TripDetail2](design-archive/previews/TripDetail2.png) | 주행 상세 | 1600×1540 | |
| [ProfileDetail2](design-archive/previews/ProfileDetail2.png) | 프로필 상세 | 1600×1280 | |
| [Reason2](design-archive/previews/Reason2.png) | 왜 이런 조언을 받았나요? | 1600×1280 | |
| [LinkPeople2](design-archive/previews/LinkPeople2.png) | 함께 탄 사람 연결 | 1600×1280 | |
| [Rating2](design-archive/previews/Rating2.png) | 동승자 평가 | 1600×1280 | |
| [History2](design-archive/previews/History2.png) | 전체 기록 | 1600×1280 | |
| [ProfileEdit2](design-archive/previews/ProfileEdit2.png) | 프로필 등록·수정 | 1600×1280 | |
| [VehicleManage2](design-archive/previews/VehicleManage2.png) | 차량 선택·관리 | 1600×1280 | |
| [FirstRun2](design-archive/previews/FirstRun2.png) | 첫 실행 | 1600×1280 | |
| [States2](design-archive/previews/States2.png) | 데이터 상태 모음 | 1600×1280 | |
| [Style2](design-archive/previews/Style2.png) | 색·글자·컴포넌트 | 1600×1280 | 스타일 기준 보드 |

## 스타일 기준

### 글꼴

`Asta Sans`를 쓰고 `Noto Sans KR`을 대체 글꼴로 둡니다. Asta Sans는 Pleos System UI에 적용된 글꼴이며, Pleos 문서는 실제 픽셀 높이 18px 이상을 권장합니다. 시안의 최소 크기는 20입니다.

| 용도 | 크기 | 굵기 |
| --- | --- | --- |
| 히어로 | 68 | 800 |
| 큰 제목 | 50 | 800 |
| 화면 제목 | 44 | 800 |
| 구역 제목 | 26~34 | 800 |
| 본문 | 26~28 | 500 |
| 보조 | 21~24 | 400 |
| 최소 | 20 | 400 |

### 색

| 이름 | 값 |
| --- | --- |
| 바탕 | `#030E1F` |
| Hyundai Blue | `#002C5F` |
| 주 버튼 | `#2F9BEA`에서 `#0B5FC4`로 이어지는 세로 그라데이션 |
| Active Blue | `#00AAD2` |
| 빛나는 강조 | `#5FE0FB` |
| Light Sand | `#E4DCD3` |
| 주의 | `#FFD3B3` |
| 본문 글자 | `#EAF4FF` |

### 공통 표면

```css
/* Screen background */
background:
  radial-gradient(1100px 620px at 82% 4%, #0B447F 0%, rgba(11, 68, 127, 0) 62%),
  radial-gradient(900px 620px at 4% 58%, #07365F 0%, rgba(7, 54, 95, 0) 60%),
  radial-gradient(1000px 480px at 60% 104%, #06396B 0%, rgba(6, 57, 107, 0) 62%),
  #030E1F;

/* Glass card */
border-radius: 28px;
background: linear-gradient(155deg, rgba(122, 176, 240, 0.16) 0%, rgba(40, 84, 140, 0.10) 55%, rgba(12, 34, 66, 0.30) 100%);
border: 1.5px solid rgba(156, 205, 255, 0.26);
box-shadow: 0 18px 44px rgba(0, 6, 20, 0.5), inset 0 1.5px 0 rgba(255, 255, 255, 0.18);

/* Primary button */
background: linear-gradient(180deg, #2F9BEA 0%, #0B5FC4 100%);
border: 1.5px solid rgba(170, 225, 255, 0.65);
```

### 이미지

Codex CLI로 생성한 이미지입니다. 시안 안에서는 `/_blob/<ID>`로 참조합니다. [design-archive/images](design-archive/images)에는 아래 화면용 파일과 함께 고해상도 PNG 원본, 생성 프롬프트가 있습니다.

| 용도 | 파일 | 자산 ID |
| --- | --- | --- |
| 키비주얼 | `kv.jpg` | `88501f2ab61932bf369412a577418f98` |
| 차량 | `car.jpg` | `764444c7f24c23434c770b43c49a2f0a` |
| 차량 (작은 크기) | `car_small.jpg` | `82662963fa94eafbc3ee1baa566220df` |
| 앱 아이콘 | `appicon.png` | `ae0818b1521fa4b61f165d9cc72fea0d` |
| 도로 | `road.jpg` | `2059ab7c736404042a8ca812601e8704` |
| 도착 | `arrive.jpg` | `53b555997199fb0c449876c15ae67d0a` |
| 예시 프로필 서아 | `avatar_child_s.jpg` | `4cec43282a9fc4fce1e5dae98de2900b` |
| 예시 프로필 민준 | `avatar_adult_s.jpg` | `d037b8f6d888fe055acc1463e9f4069d` |
| 예시 프로필 지영 | `avatar_woman_s.jpg` | `b9b9803b95f883f521b56d7ead990c8f` |
| 예시 프로필 도윤 | `avatar_boy_t.jpg` | `fb413833e6479ba6e3b7d29344a17f81` |

## 시안에 아직 반영되지 않은 Pleos 제약

2026-11-03에 Pleos Connect 에뮬레이터 v2.1.0(API 34)에서 시험 앱으로 측정한 결과입니다. 실차와 App Market 심사에서는 확인하지 못했고, 권한은 adb로 강제로 부여했습니다. 측정 방법과 캡처는 [측정 기록](pleos-emulator/README.md)에 있습니다.

### 창 크기

- 전체 화면은 2560×1440, 160dpi입니다.
- 앱 하나가 앱 영역 전체를 쓰는 가로형 창은 **1682×1210**이고 시작 위치는 x=853, y=90입니다. 시안의 1600×1280은 이 크기에 맞게 고쳐야 합니다.
- 왼쪽 시스템 주행 영역(약 853px), 앱 위 상태 표시줄(90px), 하단 바(140px)는 시스템이 그립니다. 앱이 주행 영역을 가리거나 닫을 수 없습니다.
- 시험에서는 앱을 열면 같은 창의 내비를 대신했습니다.
- Pleos 문서 기준으로 앱은 최대 2개까지 함께 표시됩니다. 두 앱의 배치는 Connect-S가 1:1, Connect-W가 2:1 또는 1:2입니다. 이렇게 나뉜 창의 실제 크기와 동작은 시험하지 않았습니다.
- Pleos 기획 가이드는 세로형과 가로형을 모두 설계하라고 안내합니다. 세로형에는 핵심 기능과 정보를, 가로형에는 추가 정보와 확장 기능을 넣는 방식입니다. 현재 시안의 세로형 보드는 `DrivingNarrow2`뿐입니다.

### 내비 표시 중 알림

40km/h 주행 중 내비를 띄우고 중요도 높음으로 시험했습니다.

| 방식 | 내비 위 표시 | 비고 |
| --- | --- | --- |
| 일반 알림, 리마인더, 상태 | 안 뜸 | 종 배지도 표시되지 않음. 일반 알림은 알림 센터를 직접 열어야 보임 |
| 메시지, 내비 카테고리 | 뜸 | 앱 영역 상단에 작은 카드로 표시됨 |
| 전화 카테고리 | 불명확 | 카드는 없고 운전 영역에 수락/거절 화면이 나타남. 우리 알림 때문인지 확정하지 못함 |
| 토스트 | 안 보임 | 주차 중에도 안 보임 |
| 오버레이 | 일반 사용자는 불가 | adb로 권한을 강제로 줄 때만 표시되고, 사용자가 허용할 설정 화면이 없음 |

**주행 중 안내 방식:** 2차 회의 방향대로 내비 위 상단 카드로 보여 줍니다. `CATEGORY_MESSAGE` 알림으로 보내면 메시지 알림처럼 잠깐 떴다 사라집니다. ([시험 문구 화면](pleos-emulator/shots/70_msg_real_copy.png), [앱의 카드](pleos-emulator/shots/85_guidance_card.png))

- **표시:** 앱 창 왼쪽 위에 약 9초 보입니다. 문구는 두 줄(줄당 약 30자)을 넘으면 잘리므로 짧게 씁니다.
- **연속 안내:** 쌓이지 않고 마지막 안내만 보이므로 앱에서 간격을 조절합니다. 규칙과 문구는 [안내 카드의 근거](guidance-card.md)에 있습니다.
- **끄기:** 시작 화면의 "주행 중 안내" 스위치로 끕니다. 차량 입력이 있을 때만 보입니다. ([스위치 화면](pleos-emulator/shots/86_guidance_switch.png))
- **소리:** 카드가 뜰 때 알림음이나 음성으로 한 번 알립니다. 기본은 알림음이고 스위치 아래 "안내 소리"에서 고릅니다. 통화·내비 음성과 겹치면 소리만 생략합니다. ([선택 화면](pleos-emulator/shots/89_guidance_sound_choice.png), [규칙](guidance-card.md#소리))
- **문구:** 탐지한 패턴에 맞는 행동 하나를 "~해보세요"로 권합니다. 핵심 조언은 출발 전과 종료 후 화면에도 넣습니다.
- **탭 동작:** 넣지 않습니다. `distractionOptimized`를 선언하지 않으면 주행 중 앱 화면이 가려지기 때문입니다.
- **심사 위험:** 이 방식을 직접 금지한 문구는 찾지 못했지만, Pleos 용어집은 이 알림을 통화·메시지·내비처럼 즉시 인지가 필요한 알림으로 정의합니다. App Market 심사 통과 여부를 알 수 없어 운영사무국에 문의합니다.
- **막힐 때 대안:** 우리 앱이 보일 때는 앱 안 배너, 내비가 보일 때는 알림 센터로 보냅니다. 전환하기 쉽도록 안내 판단과 표시 코드를 분리합니다.

내비 앱 화면 안에 직접 문구를 넣는 API는 Pleos 문서에서 찾지 못했습니다. 경로 안내 화면을 가리는지와 실차 스피커의 음량·음질은 확인하지 못했습니다.

### 주행 중 화면 표시

- 일반 화면은 5km/h와 40km/h 모두에서 시스템 화면 "주행 중에는 사용할 수 없습니다"로 가려졌습니다. 주차하면 자동으로 돌아옵니다.
- Activity에 `distractionOptimized`를 선언하면 40km/h에서도 보입니다. 단, 같은 task 아래에 선언하지 않은 화면이 있으면 가려집니다.
- 현재 앱은 `MainActivity` 하나로 구성되어 있으므로, 주행 화면을 보여 주려면 여기에 선언해야 합니다.
- 선언한 앱은 주행 제한을 직접 따라야 합니다. 시험에서 N·D 기어 제한값은 80(설정 화면과 영상 금지)이었습니다.

### 기록과 화면의 관계

- 일반 서비스는 앱이 뒤로 가고 60초 뒤 종료되었습니다. 포그라운드 서비스는 내비 뒤에서 171초 동안 끊기지 않았습니다.
- 따라서 주차 중 "운행 기록 시작" 버튼으로 포그라운드 서비스를 켜는 흐름을 권합니다. 앱 버튼에서 시작하는 경로는 구현할 때 확인해야 합니다.

## 보존 자료

| 위치 | 내용 |
| --- | --- |
| [design-archive/source](design-archive/source) | 1차·2차 보드 소스 31개와 `canvas.json`. 파일 크기가 게시본과 일치함을 확인 |
| [design-archive/previews](design-archive/previews) | 2차 보드 16개 PNG와 주요 화면 모음 `sheet.png` |
| [design-archive/images](design-archive/images) | 화면용 이미지, PNG 원본, 생성 프롬프트 |
| [design-archive/tools](design-archive/tools) | 미리보기 생성 도구 `render.py` |
| [pleos-emulator](pleos-emulator/README.md) | 에뮬레이터 측정 결과, 캡처, 로그, 시험 앱 소스 |

미리보기는 간이 실행기와 Chrome으로 만든 정지 화면이라 실제 캔버스와 다를 수 있습니다. 조정값은 기본값으로 렌더링합니다. 다시 만들려면 Chrome을 설치하고 아래처럼 실행합니다.

```sh
python3 docs/design-archive/tools/render.py Home2 1600 1280
```

## 남은 일

- [ ] 가로형 보드 크기를 1682×1210 기준으로 고치기
- [ ] 세로형 창의 실제 크기를 재고 `DrivingNarrow2`를 고치기
- [ ] 나머지 화면의 세로형 배치 정하기
- [ ] 내비 위 상단 카드를 시안에 반영하기. 현재 `Driving2`의 배너는 앱 안 표시입니다.
- [ ] 운영사무국에 `distractionOptimized` 선언과 `android.car.permission.CAR_SPEED` 권한의 승인 여부 문의하기
- [ ] 알림음과 음성을 실제로 들어 보고 음색 정하기
- [ ] 공조 제어 호출, 자동 시작 확인하기
