# 2026-10-28 · KeyManage DS Builder 피그마 플러그인 제작 및 토큰 자동 드로잉 파이프라인 구축

> 요약: `MusicSearch/Tools/figma-builder` 구조를 계승하여, Figma 빈 파일에서 원클릭으로 shadcn 변수 등록, Tokens 견본, 5대 핵심 컴포넌트, iPhone 실화면 2종을 오토레이아웃으로 자동 빌드하는 자체 개발 플러그인을 구축했다.

## 사용 도구

- 도구: Antigravity IDE (Gemini 3.8 Flash High)
- 플러그인/스킬: `project-docs:ai-usage`

## 요청

- `MusicSearch/Tools/figma-builder`를 레퍼런스로 확인하고, `KeyManage` 전용 Figma DS Builder 플러그인을 제작해달라는 요청.
- 모델 쿼터 관리를 위해 귀한 Gemini 4 Argon 대신 속도와 가성비가 높은 Gemini 3.8 Flash로 구현할 것을 합의.

## AI 산출물

- [`Tools/figma-builder/manifest.json`](../../Tools/figma-builder/manifest.json): 피그마 개발 플러그인 매니페스트.
- [`Tools/figma-builder/code.js`](../../Tools/figma-builder/code.js):
  - `primitive` / `semantic` 로컬 변수 컬렉션 자동 생성 및 14개 shadcn 색상 바인딩.
  - `Inter` 및 `Roboto Mono` 텍스트 스타일 자동 등록.
  - `🎨 Tokens` 페이지: 색상 칩 및 타이포그래피 스케일 시각화.
  - `🧩 Components` 페이지: 5대 핵심 컴포넌트(`KMButton`, `KMCard`, `KMPasswordField`, `KMGaugeBar`, `KMBurnBadge`) 자동 드로잉.
  - `📱 Screens` 페이지: 볼트 대시보드 및 비밀번호 생성기 화면 (iPhone 16 Pro 규격 393x852) 오토레이아웃 조립.
- [`Tools/figma-builder/README.md`](../../Tools/figma-builder/README.md): 피그마 데스크톱 앱 실행 가이드.

## 사람의 판단

- **자체 빌더 방식 확정**: 외부 복잡한 SaaS 플러그인 대신, 로컬에서 직접 소유하고 실행하는 순수 Figma Plugin API 방식을 채택.
- **모델 전략 준수**: 일상 스크립트 작성에 고비용 Argon을 낭비하지 않고 Flash로 민첩하게 제작함.

## 검증

- `bash Scripts/check-docs.sh` 실행 결과: **문서 검사 통과**.

## 근거 링크

- 없음(커밋 전)
