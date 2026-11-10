# 모션케어 데모 영상 (약 3분 30초)

실제 녹화본으로 다시 만들려면 `docs/HANDOFF.md`부터 읽으세요. 장면표는 `docs/shot-list.md`, 기준 영상은 `reference/motioncare-demo-reference.mp4`입니다.

기존 6분 시연 영상(`motion-care-driving-demo-share.mp4`)을 기준본으로 삼아 줄인 Remotion 프로젝트입니다.
원본 영상은 `public/source.mp4`로 복사해서 쓰며, 원본 파일과 앱 코드는 건드리지 않습니다.

## 만들기

```bash
# 처음 한 번
set NODE_OPTIONS=--use-system-ca     (PowerShell: $env:NODE_OPTIONS="--use-system-ca")
npm install

npm run studio      # 브라우저에서 미리 보며 고치기
npm run render      # out/motioncare-demo-4min.mp4 로 저장 (이 PC에서 약 40분)
npx remotion still src/index.ts MotionCareDemo out/check.png --frame=3500   # 한 장만 뽑아 확인
```

회사 PC에서는 Remotion 전용 브라우저가 실행되지 않아 설치된 Chrome을 쓰도록 `remotion.config.ts`에 적어 두었습니다.

`studio`와 `render`는 먼저 `tools/build-remap.mjs`를 돌려, 원본을 `timeline.ts`대로 이어 붙인 프레임 이미지(`public/remap/`, 약 1.2GB)를 만듭니다. 이 PC에서 Remotion이 영상에서 직접 프레임을 뽑는 방식이 너무 느려서 이렇게 합니다. 타임라인의 `src`나 `dur`을 바꾸면 자동으로 다시 만들고(약 2분), 자막만 바꾸면 건너뜁니다. ffmpeg가 설치되어 있어야 합니다.

일부만 빨리 보려면: `npx remotion render src/index.ts MotionCareDemo out/part.mp4 --frames=3300-3700 --scale=0.5`

## 어디를 고치면 되나

| 고치고 싶은 것 | 파일 |
| --- | --- |
| 장면 순서, 장면 길이, 자막, 소리, 확대 | `src/config/timeline.ts` |
| 브랜드 소개·마무리 문구, 이름표 | `src/config/copy.ts` |
| 네 화면의 자리와 크기, 소개 때 크게 보이는 위치 | `src/config/layout.ts` |
| 색, 글꼴 | `src/config/theme.ts` |
| 가감속·좌우 움직임 계산과 기준선 | `src/components/Analysis.tsx` |

`timeline.ts`의 한 줄이 장면 하나입니다.

```ts
{
  id: 'd1-card',
  chapter: 3,                  // 위쪽 단계 표시 (CHAPTERS 순서)
  src: [134.5, 144.2],         // 원본 영상에서 가져올 구간(초)
  dur: 9.7,                    // 새 영상에서의 길이(초). 짧게 잡으면 그만큼 빨리 재생
  audio: true,                 // 원본 소리 사용 (1배속 구간에만)
  focus: {pane: 'pleos', from: 2.4, to: 7.6, zoom: 1.5, origin: [10, 0]},  // 화면 안쪽 확대
  title: '내비 위 카드와 알림음',
  sub: '"정지 지점보다 조금 일찍 감속해보세요"',
}
```

- 장면을 빼려면 그 줄을 지우고, 순서를 바꾸려면 줄을 옮깁니다. 전체 길이는 자동으로 다시 계산됩니다.
- 화면 소개 네 구간(`intro-console`, `intro-pleos`, `intro-data`, `intro-caption`)의 `id`는 등장 연출과 연결되어 있어 이름을 바꾸면 안 됩니다. 길이와 문구는 바꿔도 됩니다.
- 원본 영상을 새 녹화본으로 바꾸려면 `public/source.mp4`를 교체합니다. 화면 배치가 같으면 `src` 시각만 다시 맞추면 되고, 배치가 다르면 `layout.ts`의 `CROP`을 고칩니다. 속도·핸들 값은 아래 도구로 다시 뽑습니다.

## 가감속·좌우 움직임 값

왼쪽 아래의 속도·기어·핸들·공조 패널은 원본 영상을 그대로 잘라 쓴 것이고, 오른쪽 아래의 가감속·좌우 움직임 그래프만 새로 그린 것입니다.
원본 패널의 속도와 핸들 숫자를 0.1초 간격으로 읽어 `src/data/telemetry.json`에 넣고, 앱과 같은 식으로 계산합니다(`motion-care/docs/detection-thresholds.md`).

- 가감속 = 1초 동안의 속도 변화
- 좌우 움직임 = 속도² × tan(조향각 ÷ 14.26) ÷ 3.0m (추정값)

화면에는 "입력값 기반 계산"이라고 표시합니다. Virtual Console 화면 자체는 손대지 않았습니다.

```bash
# 원본이 바뀌었을 때 다시 뽑기
python tools/extract_telemetry.py cluster public/source.mp4 work    # 숫자 모양 묶기 → work/clusters_*.png 확인
# work/labels_speed.json, labels_steer.json 에 "묶음번호": 값 을 적은 뒤 (예시는 tools/labels_*.json)
python tools/extract_telemetry.py build work src/data/telemetry.json
```

## 기존 6분 영상에서 뺀 것

- 맨 끝의 별도 시험 두 가지(2시간 주행 휴식 카드, 속도 권한 거부)와 "정리" 화면. 4분할이 아닌 전체화면 장면이라 뺐고, 한계 안내는 마지막 화면 아래 한 줄로 남겼습니다.
- 운행 2에서 핸들 흔들기 사이의 쉬는 구간(원본 213.5~221초).

## 알아 둘 점

- 내레이션 음성은 없습니다. 설명은 맨 위 한 줄(`title`)뿐입니다.
- 본편 시작부터 차가 움직이기 전까지는 `full: 'pleos'`로 Pleos 화면만 전체화면입니다.
- 화면 소개 구간은 원본의 첫 출발 장면(84~97초)을 절반 속도로 늘려 쓴 것이라 도로 움직임이 조금 느립니다.
- 원본이 1920x1080 한 장에 네 화면이 들어간 영상이라, 화면 소개 때 크게 보이는 Virtual Console은 약간 흐립니다. 화면별 원본 녹화가 있으면 더 선명하게 만들 수 있습니다.
