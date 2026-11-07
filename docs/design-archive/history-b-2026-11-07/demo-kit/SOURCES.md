# 범위 판단의 근거 · 2026-11-07

## 최신 공유 코드

검토 기준: `kako-labs/motion-care` main `4fd34ebe448144124d29662d4fa9f805f39e4d80`. 원격 브랜치·열린 PR을 GitHub API로 확인하고 해당 커밋의 문서·공개 모델을 읽었다. 실차 재검증은 하지 않았다.

작업 중 main이 `fae2f54cb7ea56c1a2b01b3641318c61eb540b3a`로 갱신됐다(PR #16, 차량 서비스 재시작 뒤 위치·음성 검증 문서와 도구). 차이를 확인했으며 런타임·모델·저장 계약 변경은 없었다. 준비 브랜치는 이 최신 main까지 fast-forward한 뒤 변경을 추가한다. 주행 재현 도구는 최신 `docs/pleos-emulator/`를 따른다.

- [작업 소유권](https://github.com/kako-labs/motion-care/blob/4fd34ebe448144124d29662d4fa9f805f39e4d80/docs/ownership.md): 기록 담당은 feature/history와 data/profile. app·data/environment·data/trip은 주행 담당. 공통 타입·빌드·스키마 변경은 공동 확인.
- [인계](https://github.com/kako-labs/motion-care/blob/4fd34ebe448144124d29662d4fa9f805f39e4d80/docs/handoff.md): 환경 수집·저장과 환경 안내는 구현됨. 출발 전·주행 상세 표시는 남음. 케어·경로는 기본 기능 완료 뒤.
- [환경 근거](https://github.com/kako-labs/motion-care/blob/4fd34ebe448144124d29662d4fa9f805f39e4d80/docs/environment.md): 실내 온도와 설정 온도를 구별, 바깥 대기질은 CAMS 예측, 실내 PM2.5·CO₂ 없음. 값마다 상태·이유·시각·출처를 보존.
- [연결 계약](https://github.com/kako-labs/motion-care/blob/4fd34ebe448144124d29662d4fa9f805f39e4d80/docs/contracts.md): 현재 공개 API를 기준으로 한다. 없는 서비스는 성공 응답으로 꾸미지 않는다.
- 기존 [기록 담당 PR #10](https://github.com/kako-labs/motion-care/pull/10)은 별도 통합 대상이다. 이번 준비가 해당 PR 병합이나 로컬 미커밋 수정의 통합을 대신하지 않는다.

## 팀 노션

사용자가 제공한 ZIP의 Markdown을 읽었다. 내보내기 파일에는 11월 6일 진행 기록도 포함돼 있다. 최신 공유 코드와 상태가 다를 때는 코드의 확인 가능한 구현 상태를 우선했다.

- [팀 대시보드](https://app.notion.com/p/HMG-40bae4c07fd6804ba9fed356a13e7a28): 로그인된 브라우저에서 본문 확인. 메인 흐름은 연구·주행 분석 → 출발 요약 → 주행 중 안내 → 완료 피드백 → 기록·프로필.
- [P08 추천 엔진·환경 안내](https://www.notion.so/40cae4c07fd681948c1ddc087e418e4f): 환경 입력·캐시·출처·현재 안내 후보는 주행 담당, 기록·비교 기반 출발 조언은 기록 담당. 내보내기 기준 검토.
- [E04 편안한 경로 추천 후보](https://www.notion.so/40cae4c07fd681958301d3f3524d0adb): 후순위, 담당 미정, P18 통합 후, COMFORTABLE 실제 지원·처리 확인, 멀미 방지 보장 금지. 내보내기 기준 검토.
- [P19 멀미 앱 시연 영상](https://www.notion.so/40cae4c07fd681f799b0fce7ac18746d): 문제 → 실제 앱 반응 → 상세 → 근거·한계. 실제/합성/목업/미구현을 구분. 내보내기 기준 검토.
- [환경 데이터 근거표](https://www.notion.so/410ae4c07fd681efa4cccc29604d915d): 개별 페이지를 브라우저 직링크로 읽는 시도는 접근 실패. 내보내기와 GitHub 동명 근거를 사용했으며 이 개별 페이지의 최신 본문을 읽었다고 간주하지 않는다.

노션의 담당·기한·완료 상태는 수정하지 않았다. 새 규칙이나 연구 결론도 추가하지 않았다.

## 이번 사용자 결정과 디자인 판단

- 기존 B의 남색·하늘색·다크 글래스 유지, 사선 유리 제안 패널, 차량을 왼쪽에 크게 배치: 사용자 결정.
- 운행 상세의 숫자 요약·짧은 그래프·패턴, 프로필의 인물과 선호, 평가의 2단계 배치: 가독성을 위한 디자인 판단. 임상 근거를 뜻하지 않음.
- 환경 수치·시연 경로 시간·거리·지도 선: 고정 합성 예시. 대기질 API나 실제 내비 응답이 아님.
- 온도·대기질로 멀미 임계값이나 복합 점수를 만들지 않음. `멀미가 적은 길`을 검증된 결과로 표현하지 않음.
