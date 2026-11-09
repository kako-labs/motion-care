# B 차량 자산 제작

- 도구: 내장 image_gen (CLI/API fallback 아님)
- 입력 참고: 사용자가 고른 B 화면 `assets/selected-design-b.png`
- 결과: `assets/cabin-blue.png` (1024×1536 RGBA). 원본을 복사했으며 별도 비트맵 편집 없음.
- 앱에서는 CSS로 크기를 조절하고 클릭 가능한 좌석·인물·이름을 겹침.

## 최종 프롬프트

Use case: product-mockup. Asset generation for an interactive vehicle seat selector. Reference image is design B; recreate ONLY its beautiful blue top-down cutaway vehicle as a standalone TRANSPARENT PNG asset, NO UI or labels. Premium photorealistic 3D render of a generic metallic midnight-blue five-seat family vehicle, perfectly overhead orthographic view, nose FRONT UP, steering wheel FRONT LEFT. Roof cut away to reveal a beautifully rendered realistic deep navy leather interior. Exactly TWO front seats and THREE rear seats (2+3), anatomically believable seats, headrests, central console, dashboard and steering wheel, windshield, body and side mirrors. Cool sky-blue material highlights, subtle glass reflections, elegant ambient light; match the reference car's visual fidelity and color. All five seats must be EMPTY and visually neutral navy, no blue selection highlights. Absolutely NO people, no avatars, no plus symbols, no text, no names, no labels, no circles, no watermarks, no interface surrounding it. We will add clickable seats and text later using code. Vehicle centered, almost fills a 2:3 portrait canvas with slim transparent margins, whole car and mirrors visible. No floor or background. Crisp clean transparent alpha edges. Need attractive realistic car asset rather than SVG/cartoon. Transparent background.

## 2026-11-07 인물 정교화

`avatar-minjun.png`, `avatar-seoa.png`, `avatar-jiyoung.png`, `avatar-doyun.png`은 내장 image_gen으로 생성한 가상 인물 이미지다. 실제 사용자·팀원 사진을 사용하지 않았다. 네이비 배경, 부드러운 청색 조명, 자연스러운 미소의 정면 인물이라는 공통 지시로 성인 남성·어린 여자아이·성인 여성·어린 남자아이를 각각 생성했다. 원본 PNG를 그대로 복사하고 CSS 원형 크롭과 확대만 적용했다. 좌석·프로필·평가 화면에서 같은 이미지를 사용한다.
